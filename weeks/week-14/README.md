# Week 14 — ForgeCI M1: GitHub integration and idempotent webhooks

[← Week 13](../week-13/) · [Roadmap](../../ROADMAP.md) · [Week 15 →](../week-15/)

**Phase 3 · ForgeCI (Weeks 14–19)** — the hardest project starts. ForgeCI is a distributed CI/CD execution platform: GitHub event → webhook → build → jobs queued → worker → temporary Docker container → logs streamed → result persisted. This week builds the front door (GitHub, webhooks, records, config) and front-loads the **Linux, Docker-internals and OS** knowledge the executor needs from Week 15. ForgeCI weeks run heavier on project time (+~5 h, mostly Saturday).

| Block | Hours | Notes |
|---|---:|---|
| Project (ForgeCI M1) | 30 | Multi-module repo, repo registration, GitHub OAuth (or PAT for MVP), webhook receiver with HMAC-SHA256 + delivery-ID dedupe, build/job/step records, `.forgeci.yml` parser |
| Learning | 8 | Linux deep dive (processes, signals, permissions, Bash), Docker internals (images, layers, networking, Engine API), OS fundamentals, GitHub OAuth/Apps + webhooks + HMAC |
| DSA (Python) | 6 | Topological Sort + Union-Find — 8 new + reviews + 1 Java rep |
| Interview / review | 3 | Weekly mock begins (Track A Python + short Track B), drill, retro |

---

## 1. Main objective

By Sunday a **new multi-module Maven repo `forgeci`** (`api`, `worker`, `core`, `ui` later) where:

- a user registers a GitHub repository (OAuth flow, or a PAT for the MVP — decide and document, OAuth can land in M5/M6 polish);
- ForgeCI installs a webhook (or you install it manually for MVP) and the receiver **verifies `X-Hub-Signature-256`** with HMAC-SHA256 before parsing anything;
- duplicate deliveries (GitHub retries, replayed events) are **rejected idempotently** by a unique constraint on `X-GitHub-Delivery`;
- a `push`/`pull_request` event creates a **build** with **jobs** and **steps** parsed from a minimal `.forgeci.yml` (a list of commands under one job; no DAG yet), fetched from the repo at the pushed commit via the GitHub API;
- everything is recorded in Postgres, exposed via `GET /builds`, tested (Testcontainers) and green in CI.

No execution yet: builds sit in `QUEUED`. Week 15 makes them run.

## 2. Prerequisites

- LedgerX `v1.0` tagged and deployed; deep-dive rehearsal done ([Week 13](../week-13/)). Do not carry LedgerX advanced work into this week.
- You have read [`18-projects/forgeci/README.md`](../../18-projects/forgeci/README.md) end-to-end and sketched the data model on paper (Week 13 stretch — if not, do it Monday first thing, 45 min).
- Outbox + idempotency patterns (LedgerX M2/M3) are fresh — the webhook receiver is the same idea with an HTTP signature in front.
- Graph BFS/DFS in Python is solid: topological sort is BFS with in-degrees, or DFS with post-order.

## 3. Learning topics

| Topic | Subtopics | File |
|---|---|---|
| Linux deep dive | processes (`fork`/`exec`, PIDs, parent/child, zombies), signals (`SIGTERM` vs `SIGKILL`, `SIGINT`, `trap`), exit codes, permissions/`umask`/`chown`, users in containers, `ps`/`top`/`strace`/`lsof`, `/proc` | [`10-linux/README.md`](../../10-linux/README.md), [`10-linux/commands.md`](../../10-linux/commands.md), [`10-linux/exercises.md`](../../10-linux/exercises.md) |
| Bash scripting | `set -euo pipefail`, quoting, `trap ... EXIT`, functions, `$?`, pipes and `PIPESTATUS`, here-docs | [`10-linux/bash-scripting.md`](../../10-linux/bash-scripting.md) |
| OS fundamentals | process vs thread, scheduling, virtual memory, file descriptors, namespaces + cgroups (what containers really are) | [`14-cs-fundamentals/operating-systems.md`](../../14-cs-fundamentals/operating-systems.md) |
| Docker internals | image layers + content addressing, `docker history`, union filesystems, container = process with namespaces/cgroups, networks (bridge, DNS), volumes vs bind mounts, **Docker Engine API** and the `docker-java` client, Docker socket security | [`11-docker/README.md`](../../11-docker/README.md), [`11-docker/dockerfiles.md`](../../11-docker/dockerfiles.md), [`11-docker/compose.md`](../../11-docker/compose.md), [`11-docker/exercises.md`](../../11-docker/exercises.md) |
| GitHub integration | OAuth web flow vs GitHub Apps vs PAT, scopes, webhooks (events, `X-GitHub-Delivery`, `X-Hub-Signature-256`, redelivery), Contents API to fetch `.forgeci.yml`, Commit Status / Checks API (later) | [`02-git/workflows.md`](../../02-git/workflows.md), [`06-rest-apis/http-for-apis.md`](../../06-rest-apis/http-for-apis.md) |
| HMAC and constant-time comparison | `HmacSHA256`, hex encoding, `MessageDigest.isEqual`, why raw body bytes matter | [`05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md) |
| Multi-module Maven | parent POM, module dependency scopes, shared `core` module, running two Spring Boot apps | [`01-java/08-maven-build.md`](../../01-java/08-maven-build.md) |

## 4. Concepts to learn

### 4.1 Processes, signals and exit codes — what a CI runner is made of

A CI step is `sh -c "<command>"` run as a child process; its **exit code** is the result (`0` success, non-zero failure, `128 + N` when killed by signal N — `137` = `SIGKILL`, `143` = `SIGTERM`). Timeouts (Week 17) send `SIGTERM`, wait, then `SIGKILL`. Zombies appear when a parent never `wait()`s; in containers, PID 1 must reap children (`tini` / `--init`).

```bash
#!/usr/bin/env bash
set -euo pipefail
trap 'echo "cleanup (exit=$?)"; rm -rf "$WORKDIR"' EXIT     # runs on success, failure and SIGTERM
WORKDIR=$(mktemp -d)
timeout --signal=TERM --kill-after=5s 30s ./run-step.sh | tee "$WORKDIR/step.log"
echo "step exit: ${PIPESTATUS[0]}"                            # not $?, which is tee's exit
```

- **Interview angle (Track B):** "Difference between `SIGTERM` and `SIGKILL`?" "Why did my container exit 137?" "What does `set -e` not catch?" (commands in pipelines, `if` conditions).
- **Where ForgeCI uses this:** the worker's step runner (M2) captures exit codes; timeout/cancel (M4) is the TERM-then-KILL sequence; graceful shutdown handles `SIGTERM` from Docker/Compose.

### 4.2 A container is a process

`docker run` = create namespaces (pid, net, mnt, uts, ipc, user) + cgroups (CPU/memory limits) + mount a union filesystem of image layers + exec the entrypoint. Prove it: `docker run -d alpine sleep 300`, then `ps aux | grep sleep` on the host — the process is visible; `docker top`, `cat /proc/<pid>/cgroup`. Layers: `docker history <image>`; every `RUN` line is a layer; the writable container layer is discarded on `rm` — which is why ForgeCI's workspace must be a **volume**.

- **Interview angle:** "What is the difference between a VM and a container?" → shared kernel, namespaces/cgroups vs hypervisor; "Why is the Docker socket dangerous?" → root-equivalent on the host (Week 19 has the mitigations).
- **Where ForgeCI uses this:** the worker creates a container per job with an image from config, a workspace volume, memory/CPU limits (cgroups), and removes it in `finally`.

### 4.3 Docker Engine API via `docker-java`

The worker will not shell out to the `docker` CLI; it talks to the Engine API (Unix socket `/var/run/docker.sock` or TCP) via the `docker-java` client. This week: read the API shape only (create container → start → attach/logs → wait → remove) and run a 20-line spike from a scratch test — the real executor is Week 15.

```java
// spike only (Week 14): prove the client can talk to the daemon
DockerClientConfig cfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
DockerHttpClient http = new ApacheDockerHttpClient.Builder().dockerHost(cfg.getDockerHost()).build();
DockerClient docker = DockerClientImpl.getInstance(cfg, http);
System.out.println(docker.infoCmd().exec().getServerVersion());
```

### 4.4 Webhook signature verification — the right way

GitHub sends `X-Hub-Signature-256: sha256=<hex>` = HMAC-SHA256 over the **raw request body bytes** with your secret. Verify **before** JSON parsing, using the raw bytes (Spring: read the body as `byte[]` in the controller; do not let Jackson deserialize first and re-serialize — whitespace/ordering changes break the MAC). Compare in **constant time**.

```java
static boolean validSignature(byte[] body, String header, String secret) throws Exception {
    if (header == null || !header.startsWith("sha256=")) return false;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] expected = mac.doFinal(body);
    byte[] provided = HexFormat.of().parseHex(header.substring("sha256=".length()));
    return MessageDigest.isEqual(expected, provided);            // constant-time
}
```

- **Interview angle:** "How do you know a webhook is really from GitHub?" → shared-secret HMAC over raw body, constant-time compare; also "what if the secret leaks?" → rotate, per-repo secrets.
- **Where ForgeCI uses this:** `POST /webhooks/github`; the Python load simulator in M5 signs its fake webhooks the same way.

### 4.5 Idempotent webhook processing

GitHub may redeliver (timeouts, manual redelivery). Store `webhook_delivery(delivery_id TEXT PRIMARY KEY, event TEXT, received_at, processed_at, build_id)`; insert first (`ON CONFLICT DO NOTHING`), and only if inserted create the build — in the **same transaction**. Duplicate → `200 OK` with `{"status":"duplicate"}` (a `4xx` would make GitHub mark deliveries failed and your dashboard red). Respond fast (< 10 s GitHub timeout): create records, return; fetching `.forgeci.yml` from the API can happen synchronously for MVP but wrap it with a timeout and record `CONFIG_ERROR` builds rather than failing the webhook.

- **Where ForgeCI uses this:** the same delivery-ID rule protects the queue from double-enqueue in M2; the failure suite (M5) replays deliveries.

### 4.6 Data model for builds

`repository(id, owner, name, default_branch, webhook_secret_enc, installation/pat ref, created_at)` · `build(id, repository_id, number, commit_sha, ref, event, status, created_at, started_at, finished_at, config_error TEXT)` · `job(id, build_id, name, image, status, exit_code, queued_at, started_at, finished_at, attempt)` · `step(id, job_id, ordinal, command, status, exit_code, started_at, finished_at)`. Status enums with a transition table (LedgerX Week 11 — reuse the pattern verbatim). `build.number` is per-repo sequential: `UNIQUE (repository_id, number)`; allocate with `SELECT COALESCE(MAX(number),0)+1 ... FOR UPDATE` on the repository row (contention is per-repo, acceptable).

### 4.7 Minimal pipeline config

```yaml
# .forgeci.yml (M1 subset)
image: maven:3.9-eclipse-temurin-21
steps:
  - mvn -q -B verify
  - echo "done"
```

Parser: Jackson YAML (`jackson-dataformat-yaml`) into a record; validate (image non-empty, ≥ 1 step, each a non-empty string, max 50 steps, forbid `\0`); unknown keys → `CONFIG_ERROR` with a message that names the key. No `needs:`/DAG until Week 19 — but keep `jobs:` as a map even with one job so M6 does not need a migration.

## 5. Resources

- GitHub Docs: *Webhooks* (events, payloads, validating deliveries) — https://docs.github.com/en/webhooks
- GitHub Docs: *Authorizing OAuth apps* and *About creating GitHub Apps* — https://docs.github.com/en/apps
- GitHub REST API: *Repository contents* — https://docs.github.com/en/rest/repos/contents
- Docker Docs: *Docker Engine API* reference — https://docs.docker.com/engine/api/
- docker-java on GitHub (README, examples) — https://github.com/docker-java/docker-java
- *The Linux Programming Interface* (Kerrisk), ch. 6 (processes), 20–22 (signals) — reference, read selectively
- Bash manual: *Signals*, *Pipelines*, `set` — https://www.gnu.org/software/bash/manual/
- NeetCode 150 — Graphs (Course Schedule family, Union-Find); problems in §7

## 6. Exercises and assignments

### 6.1 Warm-up exercises (~3 h, spread Mon–Wed)

1. **Signals lab (30 min).** Run `sleep 1000 &`; send `SIGTERM`, then run a script that `trap`s TERM and ignores it; observe that only `SIGKILL` ends it; record exit codes (`echo $?` → 143 / 137).
2. **Zombie lab (20 min).** In a container without `--init`, spawn a child that exits while the parent sleeps; `ps` shows `<defunct>`; add `--init` and repeat.
3. **Layers lab (30 min).** Build a 3-`RUN` Dockerfile; `docker history`; change the last `RUN`; rebuild and watch cache hits; `docker run` + write a file + `docker rm` → file gone; repeat with `-v` → file persists.
4. **Namespace lab (20 min).** `docker run -d alpine sleep 300`; find its host PID; `nsenter -t <pid> -n ip addr` (or `docker exec`) to see the container's network namespace.
5. **HMAC kata (30 min).** Compute `sha256=` for a fixed body and secret in Java and with `openssl dgst -sha256 -hmac`; they match; change one byte of whitespace → mismatch.
6. **Bash: `run-step.sh` (30 min).** Script that runs a command with a timeout, tees output to a log, returns the *command's* exit code (not `tee`'s) — you will port this logic to Java next week.
7. **Topological sort by hand (20 min, Python).** Kahn's algorithm on a 6-node DAG on paper; then detect a cycle by "not all nodes emitted".

### 6.2 Assignment — ForgeCI M1

**Acceptance criteria:**

- [ ] Multi-module Maven repo `forgeci` (`core`, `api`, `worker` (empty shell), parent POM); Compose: postgres 16, redis 7; Flyway; CI `mvn -B verify` on PR.
- [ ] Repository registration: `POST /repositories` with owner/name + credentials (PAT for MVP; OAuth optional: `GET /oauth/github/login` → callback → store token encrypted at rest with a documented key source). Webhook secret generated per repository.
- [ ] `POST /webhooks/github`: raw-body HMAC verification (constant-time), `400` on bad/missing signature, `200` on valid; unsupported events → `202` "ignored".
- [ ] Delivery dedupe: `webhook_delivery` PK on `X-GitHub-Delivery`; duplicate → `200 duplicate`, no second build; insert + build creation in one transaction.
- [ ] `push` (and `pull_request` `opened`/`synchronize`) → build with per-repo sequential number, commit SHA, ref; config fetched via Contents API at that SHA; parsed into one job with N steps; invalid/missing config → build `CONFIG_ERROR` with message.
- [ ] `GET /builds?repositoryId=&cursor=` (keyset, from LedgerX), `GET /builds/{id}` with jobs + steps.
- [ ] Status enums + transition tables + DB guards for build/job/step.
- [ ] Testcontainers ITs green; webhook signature and dedupe tests included.

### 6.3 Break it

- Verify the signature over the re-serialized JSON instead of raw bytes; send a payload with different key order/whitespace → false `400`. Restore raw-bytes verification.
- Use `Arrays.equals` for the MAC compare; note (do not build) the timing-attack argument; restore `MessageDigest.isEqual`.
- Remove the transaction around insert-delivery + create-build; kill the app between them; replay the delivery → `duplicate`, but no build exists. Restore and add the test.
- Point the config fetch at a repo where `.forgeci.yml` is 5 MB; watch the webhook time out at GitHub's 10 s. Add a size cap and an HTTP client timeout.

### 6.4 Debug it

- All webhooks return `400` in production but pass in tests: Spring's `HiddenHttpMethodFilter`/`ContentCachingRequestWrapper` or a proxy is altering the body, or the secret has a trailing newline from `echo` into the env file. Compare `sha256sum` of the body as received vs GitHub's redelivery view.
- Build numbers collide under concurrent pushes to one repo (`23505` on `(repository_id, number)`): you allocated with a plain `MAX()+1`. Lock the repository row first (`FOR UPDATE`), or use a per-repo sequence table.

## 7. DSA — Topological Sort + Union-Find (8 new problems, in Python)

**Language: Python (Track A).** Guides: [`03-dsa/16-topological-sort.md`](../../03-dsa/16-topological-sort.md) · [`03-dsa/17-union-find.md`](../../03-dsa/17-union-find.md) · Templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md). Kahn's: `indeg = [0]*n`, `deque` of zero-in-degree nodes, count emitted (cycle if `< n`). DFS variant: colours `0/1/2` (white/grey/black), grey hit = cycle, post-order reversed = order. Union-Find: `parent` list, path compression (iterative), union by size/rank; `find` returns root.

| # | Problem | Difficulty | Time limit | Day |
|---|---|---|---|---|
| 207 | Course Schedule | Medium | 25 min | Mon |
| 210 | Course Schedule II | Medium | 20 min | Mon |
| 802 | Find Eventual Safe States | Medium | 30 min | Tue |
| 269 | Alien Dictionary (Premium; alt: 1203. Sort Items by Groups and Dependencies — Hard, optional) | Hard | 35 min | Tue |
| 684 | Redundant Connection (now with Union-Find) | Medium | 20 min | Wed |
| 323 | Number of Connected Components in an Undirected Graph (Premium; alt: 547. Number of Provinces) | Medium | 20 min | Wed |
| 261 | Graph Valid Tree (Premium; alt: 1971. Find if Path Exists in Graph + the `edges == n-1` argument) | Medium | 25 min | Thu |
| 721 | Accounts Merge | Medium | 35 min | Thu |

Reviews due: Day-3 Week 13 Thu/Fri; Day-7 Week 13 graphs; Day-14 Week 12 backtracking; Day-30 Week 10 BST/Tries. If a Premium problem is unavailable, use the listed alternative and note it in the tracker.

**Java rep (Fri, ≤ 30 min):** #207/#210 Kahn's algorithm in Java (`int[] indeg`, `List<List<Integer>> adj`, `ArrayDeque<Integer>`). This one is not optional: ForgeCI M6 (Week 19) implements exactly this in production Java for `needs:` DAG scheduling, and ROADMAP §7 schedules topological sort now for that reason.

## 8. Project work — ForgeCI M1 (GitHub integration + idempotent webhooks)

Spec: [`18-projects/forgeci/README.md`](../../18-projects/forgeci/README.md) · [`milestones.md`](../../18-projects/forgeci/milestones.md) (M1) · [`failure-engineering.md`](../../18-projects/forgeci/failure-engineering.md)

### 8.1 Task checklist

- [ ] **Mon:** repo `forgeci`, parent POM + `core`/`api`/`worker` modules, Compose, Flyway `V1__repositories.sql`, CI; GitHub milestone **M1 — GitHub integration + idempotent webhooks** with issues; ADR `0001-oauth-vs-pat-for-mvp.md`, ADR `0002-sse-over-websockets.md` (decide now, implement W16 — the spec asks you to explain the choice).
- [ ] **Tue:** repository registration + secret generation + token encryption; GitHub client (`RestClient`) with timeouts for Contents API; PAT validation on registration (`GET /user`).
- [ ] **Wed:** webhook endpoint with raw-body HMAC verification; `webhook_delivery` dedupe; event routing (push / pull_request / ignored).
- [ ] **Thu:** `V2__builds_jobs_steps.sql`; status enums + transition tables + triggers; build creation with per-repo numbering; `.forgeci.yml` fetch + parser + `CONFIG_ERROR` path.
- [ ] **Fri:** `GET /builds` keyset + `GET /builds/{id}`; docs `ARCHITECTURE.md` first draft (flow diagram from the spec), `docs/API.md`.
- [ ] **Sat (extended, +2 h):** Testcontainers suite (§8.3); end-to-end manual test with a real GitHub repo + `smee.io`/`ngrok`-style tunnel or a self-hosted tunnel of your choice (document the choice); failure scenarios (§8.4); tag `m1`.

### 8.2 Acceptance summary

M1 done = §6.2 green + real push to a real repo produces a `QUEUED` build with parsed steps visible at `GET /builds/{id}` + docs drafted + tag `m1`.

### 8.3 Verification tests you write

| Test | Type | Proves |
|---|---|---|
| `WebhookSignatureTest` | unit | valid MAC accepted; wrong secret, wrong prefix, missing header, one-byte body change → rejected |
| `WebhookSignatureUsesRawBodyIT` | MockMvc | body with unusual whitespace/key order still verifies |
| `WebhookDedupeIT` | Testcontainers | same `X-GitHub-Delivery` twice → one `webhook_delivery`, one build, second response `duplicate` |
| `WebhookDedupeConcurrentIT` | 2 threads | simultaneous identical deliveries → exactly one build |
| `DeliveryAndBuildAtomicIT` | fault hook | failure after delivery insert → neither row persists |
| `BuildNumberingIT` | 8 threads, one repo | numbers 1..8, no gaps, no duplicates |
| `ConfigParserTest` | unit, parameterised | valid configs parse; each invalid case names the problem; unknown key rejected |
| `ConfigFetchFailureIT` | WireMock/`MockRestServiceServer` | 404 / timeout / oversized → build `CONFIG_ERROR`, webhook still `200` |
| `BuildStatusTransitionTest` | unit | full transition truth table |
| `UnsupportedEventIT` | MockMvc | `ping`, `issues` → `202 ignored`, nothing persisted except delivery |

### 8.4 Failure-engineering scenarios this week

From `failure-engineering.md`, M1 set: (1) GitHub redelivers after your 10 s timeout, (2) secret rotated on GitHub but not in ForgeCI, (3) GitHub API rate limit (403 + `X-RateLimit-Remaining: 0`) during config fetch, (4) app crash between delivery insert and build insert, (5) two pushes to the same branch within 1 s. Reproduce → observe → logs → diagnose → fix/design → regression test → write-up in `docs/FAILURES.md`.

### 8.5 GitHub expectations

Milestone `M1`; issues per acceptance bullet; PRs `chore/m1-skeleton`, `feat/m1-repositories`, `feat/m1-webhook-receiver`, `feat/m1-builds-config`, `test/m1-its`, `docs/m1-adrs`. Branch protection on `main` with required `verify` check. Tag `m1`.

## 9. Git activity

- Multi-module repo: practise `git sparse-checkout` once to see how a monorepo can be partially checked out (15 min) — ForgeCI's worker will clone repos and you should know the clone options (`--depth 1`, `--single-branch`, `--filter=blob:none`).
- Webhook debugging: use `git commit --allow-empty -m "trigger"` to fire test pushes.
- Keep secrets out: add a pre-commit `gitleaks`-style check or at least `.gitignore` for `.env`; verify with `git log -p | grep -i secret` on your own history.

## 10. Interview preparation

Two tracks, never mixed (ROADMAP §10): **Track A = coding interview in Python**; **Track B = software-engineering / résumé interview in Java, Spring, SQL, Linux, Docker, AWS…** Weekly mocks start this week and continue through Week 23.

**Track A (Python)**
- **Weekly mock #2 (Sat, 45 min + 15 min review):** [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md) — one unseen Medium (graph or tree family, e.g. LeetCode 133 variant or 417) in Python under interview protocol: clarify, brute force, optimise, code, test, complexity. Cheatsheet closed. Score with [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md); log in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Think-aloud (Tue, 30 min):** #210 recorded; from this week the weekly mock replaces the standalone think-aloud when time is short — keep at least one recorded Python solve per week.

**Track B (Java / projects)**
- **Engineering mock slice (Sat, 15 min, inside the mock):** "How would you verify a webhook came from GitHub, and how do you avoid processing it twice?" — answer from this week's code.
- **Résumé-defense drill — Linux, Docker, Git:** [`17-resume-tech-defense/linux.md`](../../17-resume-tech-defense/linux.md), [`docker.md`](../../17-resume-tech-defense/docker.md), [`git.md`](../../17-resume-tech-defense/git.md). Answers must use this week's labs (signals, layers, namespaces).
- **Applications (Sun):** 5/week continues ([`JOB_READINESS.md`](../../JOB_READINESS.md), internship-ready stage); any OA invitation → read [`OA_PREP.md`](../../OA_PREP.md) §1 now rather than in Week 17.

## 11. Revision work

- Re-derive LedgerX's idempotency store and map each field to `webhook_delivery` — what did you drop and why (no fingerprint: GitHub's delivery id is already unique per payload)?
- Flashcards: exit code 137/143, `set -euo pipefail` semantics, `PIPESTATUS`, namespaces list, cgroups, `docker history`, `X-Hub-Signature-256`, constant-time compare.
- Python pitfall of the week: `[[0]*n]*m` aliasing (rows are the same list) — you will hit it in DP next week.

## 12. Daily plan

| Day | Learning | Project | DSA | Interview / other |
|---|---|---|---|---|
| **Mon** (8h) | Linux processes/signals + labs 1–2 (2h) | Repo skeleton, modules, Compose, Flyway, CI, milestone, ADRs (4.5h) | Topo sort §1–5, #207, #210 (1.5h) | — |
| **Tue** (8h) | — | Repository registration, GitHub client, token storage (5h) | #802, #269/#1203 + Day-3 reviews (2h) | Think-aloud #210 (1h) |
| **Wed** (8h) | Docker internals + labs 3–4; HMAC kata (2h) | Webhook receiver, signature, dedupe, routing (4.5h) | Union-Find §1–5, #684, #323/#547 (1.5h) | — |
| **Thu** (8h) | — | Builds/jobs/steps schema, transitions, numbering, config fetch + parser (5h) | #261/#1971, #721 + Day-7 reviews (2h) | `docs/ARCHITECTURE.md` draft (1h) |
| **Fri** (5h) | OS fundamentals reading (1h) | `GET /builds` endpoints, `docs/API.md` (2h) | Reviews + Java rep Kahn's (1h) | Retro prep (1h) |
| **Sat** (8h) | Bash `run-step.sh` (0.5h) | Testcontainers suite, real-repo end-to-end, failure scenarios, tag `m1` (5.5h) | — | Mock #2 (Track A + B slice) + drill (2h) |
| **Sun** (2–3h) | — | — | Day-14/30 reviews | End-of-week test, trackers, plan Week 15, applications |

## 13. End-of-week test (Sunday, 75 min)

**Part A — DSA (30 min, Python).** LeetCode **1462. Course Schedule IV** (topological order + reachability) or, if time is short, **547. Number of Provinces** with Union-Find, in ≤ 25 min.

**Part B — Concepts (20 min).**

1. A container exits with code 137. What happened and who did it?
   <details><summary>Answer</summary>128 + 9 → killed by `SIGKILL`: either the OOM killer (cgroup memory limit hit — check `docker inspect` `OOMKilled`) or an explicit `docker kill`/timeout escalation.</details>
2. Why verify the HMAC over raw bytes and compare in constant time?
   <details><summary>Answer</summary>The MAC was computed by GitHub over the exact bytes; any re-serialization changes them. Constant-time comparison prevents leaking how many leading bytes matched via timing.</details>
3. GitHub redelivers a webhook you already processed. What is the correct response code and why?
   <details><summary>Answer</summary>`200` with a "duplicate" body: the delivery is acknowledged; `4xx`/`5xx` would trigger further retries and show as failures in GitHub's UI. Dedupe via unique `X-GitHub-Delivery`.</details>
4. Name the Linux namespaces a container uses and what cgroups add.
   <details><summary>Answer</summary>pid, net, mnt, uts, ipc, user (and cgroup ns) isolate *visibility*; cgroups limit *resources* (CPU, memory, pids). Isolation ≠ limits.</details>
5. Why must delivery insert and build creation share a transaction?
   <details><summary>Answer</summary>Otherwise a crash between them leaves a delivery marked seen with no build — the replay is then "duplicate" and the push is lost forever. Same dual-write reasoning as the outbox.</details>
6. `set -e` is on. `false | true; echo $?` prints?
   <details><summary>Answer</summary>`0` and the script continues: a pipeline's status is the last command's unless `set -o pipefail`. Hence `set -euo pipefail` and `PIPESTATUS` when you need an inner command's code.</details>

**Part C — Practical (20 min).** Write from memory: the Java HMAC verification function, and a Bash snippet that runs a command with `timeout`, tees the log, and exits with the command's status.

**Part D — Explain (5 min, Track B).** "Walk me through what happens from `git push` to a `QUEUED` build in ForgeCI." Cover: webhook, signature, dedupe, transaction, config fetch, records, response time.

Pass: A in time · B ≥ 5/6 · C correct · D fluent.

## 14. Mastery checklist

- [ ] I can explain processes, signals, exit codes and PID 1 reaping with examples I ran.
- [ ] I can explain images/layers/namespaces/cgroups and why a container's writable layer is ephemeral.
- [ ] I can verify a GitHub webhook signature correctly and explain the two classic mistakes.
- [ ] Webhook processing is idempotent and atomic, proven by tests including a concurrent one.
- [ ] `.forgeci.yml` is parsed with clear validation errors; bad config never breaks the webhook.
- [ ] ADRs for OAuth-vs-PAT and SSE-vs-WebSockets are written (implementation later).
- [ ] 8 topo-sort/Union-Find problems done in Python; Kahn's algorithm also written in Java; reviews done.
- [ ] Weekly mock #2 done and scored (Track A), engineering slice answered (Track B).

## 15. Expected deliverables

- `forgeci`: tag `m1`; `docs/adr/0001`, `0002`; `docs/ARCHITECTURE.md` draft, `docs/API.md`, `docs/FAILURES.md` (5 scenarios); CI green.
- Trackers: [`project-tracker.md`](../../trackers/project-tracker.md) (ForgeCI M1, hours), [`dsa-tracker.md`](../../trackers/dsa-tracker.md) (Python + Java rep), [`technology-tracker.md`](../../trackers/technology-tracker.md) (Linux, Docker internals → "can defend with labs"), [`interview-tracker.md`](../../trackers/interview-tracker.md) (mock #2), [`weekly-progress.md`](../../trackers/weekly-progress.md).

## 16. If behind / stretch

**Behind?** Cut order: OAuth (PAT is the documented MVP path anyway) → `pull_request` events (keep `push`) → keyset on `GET /builds` (offset for now, note the debt) → #721/#269. **Never cut** signature verification, dedupe + atomicity tests, or the Linux/Docker labs — Week 15's executor depends on them.

**Ahead?** Implement the GitHub OAuth web flow properly (state parameter, CSRF, token encryption with a KMS-style key in env); post a **commit status** (`pending`) to GitHub when a build is created via the Statuses API — the M3 UI and M4 results will update it; solve LeetCode 1203 if you skipped it.
