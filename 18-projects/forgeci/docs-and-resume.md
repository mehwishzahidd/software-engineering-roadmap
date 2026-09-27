# ForgeCI — Documentation, Benchmarks and Résumé

> Documentation is part of the deliverable, not an afterthought; benchmarks are only real when the
> protocol is recorded; résumé bullets come **only** from measured results. The honesty rule in
> [`ROADMAP.md`](../../ROADMAP.md#1-learning-philosophy) and [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) applies.

Contents: [Required docs](#1-required-documents) · [Benchmarking protocol](#2-benchmarking-protocol) · [Report template](#3-benchmark-report-template) · [Résumé bullets](#4-résumé-bullet-template) · [GitHub hygiene](#5-github-hygiene) · [Timeline](#6-when-to-write-what)

---

## 1. Required documents

All in `docs/` of the ForgeCI repo, linked from the README. Templates: [`../templates/design-doc.md`](../templates/design-doc.md), [`../templates/adr.md`](../templates/adr.md), [`../templates/benchmark-report.md`](../templates/benchmark-report.md).

| Document | Must contain | Done when |
|---|---|---|
| `README.md` | Pitch (3 sentences), architecture diagram, "what is interesting here" (5 bullets), quick start (`docker compose up`, register, push, watch — including smee/ngrok for local webhooks), `.forgeci.yml` reference (minimal + DAG), the **Docker-socket warning**, scope tier reached + tags, measured numbers with link, links to every doc, screenshots, "what I'd do next" | A stranger runs it in 15 minutes from the README alone |
| `ARCHITECTURE.md` | Component responsibilities (api / worker / common / ui / tools), the 13-step flow as a sequence diagram, queue mechanics (`BLMOVE`, processing lists, leases, heartbeats, sweeps) with a diagram, log pipeline with the replay race explained, state machines (job, build), DAG scheduling, trust boundaries | Every arrow in the diagram has a paragraph |
| `API.md` + `api/openapi.json` | Endpoint table (README §15), auth model (cookie JWT, HMAC for webhooks), `ProblemDetail` types, SSE event format and `Last-Event-ID` semantics, example `curl` for webhook (signed), cancel, retry, stream | `openapi.json` regenerated in CI and matches |
| `DATABASE.md` | ERD image, table-by-table rationale, indexes and the queries they serve (with `EXPLAIN` output for build list and log replay), migrations policy, retention for `log_chunk`, what lives in Redis (key table with TTLs) vs Postgres | Someone can add a table without breaking invariants |
| `TESTING.md` | Test pyramid with counts, how to run each layer (`mvn test`, `mvn verify`, `-Pdocker`, `pytest tools/`, `npm test`), Testcontainers setup, the named concurrency tests and what each proves, coverage numbers | `mvn verify` instructions work on a clean clone |
| `DEPLOYMENT.md` | Compose (local and `compose.test.yaml`), AWS topology diagram, security groups table, SSM parameters list, IAM roles, step-by-step bring-up, webhook URL configuration, CloudWatch alarm, teardown checklist and cost estimate | You can redeploy from scratch in < 2 h following it |
| `SECURITY.md` | Threat model (webhook forgery, token theft, malicious `.forgeci.yml`, socket abuse, log injection), controls per threat, the untrusted-code caveat and what real products do, OWASP API Top 10 mapping (Week 24), secrets handling and rotation | Every control points at code or config |
| `DESIGN_DECISIONS.md` | ADR-001 pull-based workers · ADR-002 Redis list vs Streams vs SQS (incl. AWS section) · ADR-003 SSE vs WebSockets · ADR-004 clone on host · ADR-005 one container per job, exec per step · ADR-006 Postgres truth / Redis transport · ADR-007 failure taxonomy and retry policy · ADR-008 API-side DAG scheduling; each with context, options, decision, consequences | ADRs written the week the decision was made, not retrofitted |
| `PERFORMANCE.md` | The benchmark reports below (queue wait, throughput vs workers, log throughput, SSE fan-out), environment, methodology, raw CSVs in `docs/benchmarks/`, interpretation, bottleneck analysis | Numbers reproducible with the commands listed |
| `FAILURE_ENGINEERING.md` | The 16 scenarios with evidence (template in [`failure-engineering.md`](./failure-engineering.md)) | Each has observed output and a regression test link |
| `tools/README.md` | Install (`pip install -e tools/[dev]`), the three commands with every flag, the test-mode config hook the simulator relies on, how each benchmark was run (exact commands), how to run `pytest`/`mypy`/`ruff` | Someone reproduces a benchmark from it |
| `CHANGELOG.md` | Per tag: `v0.1-mvp`, `v1.0`, `v1.1` | Updated at each tag |

Diagrams (`docs/diagrams/`, source + PNG): architecture, webhook-to-log sequence, queue/lease states, ERD, DAG example, AWS topology. Screenshots (`docs/screenshots/`): see README §30.

### 1.1 Section outlines for the two documents interviewers actually open

**`README.md`**
1. Title + one-line pitch + badges (CI, tools CI, coverage, tag).
2. 60-second overview: what happens on push (the 13 steps compressed to 5 lines) + architecture PNG.
3. What is interesting here (5 bullets, each linking to the ADR or doc that proves it).
4. Quick start: prerequisites (Docker, a GitHub repo you admin, smee/ngrok), `docker compose up`, register a repo, push, open the UI, watch — with a GIF.
5. `.forgeci.yml` reference: minimal, DAG, every key with default and limits, "steps run under `sh -c`", timeout semantics.
6. Operating it: workers, limits, cancel/retry, health endpoints, metrics, the Docker-socket warning box.
7. Measured results: a 5-row table copied from `PERFORMANCE.md` with a link and the environment line.
8. Failure engineering: one line per scenario with a link.
9. Docs index; scope tier and tags; what I'd do next; license.

**`ARCHITECTURE.md`**
1. Context and constraints (single-tenant learning deployment; untrusted code; laptop → AWS).
2. Components and responsibilities (api, worker, common, ui, tools) + module dependency rule (api/worker → common, never sideways).
3. Runtime flow: sequence diagram, then per-step notes on transactions and atomicity.
4. Queue design: keys table (`queue:jobs`, `processing:<w>`, `job:<id>:lease`, `queue:delayed`, `limit:<repo>`, `job:<id>:cancel`, `control:<w>`, `logs:<job>`, `builds:<build>`) with type, TTL, writer, reader; the lease/heartbeat/sweep protocol; fencing.
5. Execution model: container lifecycle, limits, cleanup guarantees, what runs on host vs in container.
6. Log pipeline: chunking rules, persist-then-publish, SSE replay algorithm (with the race), fan-out registry.
7. State machines (job, build) — tables from README §13.
8. Failure taxonomy and retry policy (link ADR-007).
9. DAG scheduling (validation, event + reconcile, fail-fast).
10. Deployment views: Compose and AWS.
11. Known limitations and evolution (§28 of the spec).

### 1.2 Documentation review checklist (Week 24)

- [ ] Every claim in README is backed by a test name, an ADR, or a `PERFORMANCE.md` row.
- [ ] Every diagram matches the code (key names, endpoints, states) — grep to check.
- [ ] Every ADR has "consequences" including at least one negative.
- [ ] A reader can find "what happens if Redis dies" in ≤ 2 clicks from the README.
- [ ] No doc says "will" about something that is not built; unbuilt ideas live under "What I'd do next".
- [ ] `tools/README.md` commands were copy-pasted and run once more on a clean checkout.

---

## 2. Benchmarking protocol

Benchmarks are run with the Python `tools/` component (M5) against the Compose stack, then once on AWS. Record **everything** in the template below; discard warm-up runs; run three times; report all three plus the median. Never report an average of latencies — report p50/p95 (and p99 if ≥ 100 samples).

### 2.1 Environment to record

- Machine: CPU model/cores, RAM, disk type; OS; Docker version; `docker info` storage driver.
- Stack: commit SHA, images (`sha-<short>`), worker count and `capacity` per worker, per-repo limit, executor limits (CPU/memory per job), Redis/Postgres versions, JVM flags.
- Workload: `repogen` variant (use `passing` with a no-dependency step like `echo` for pure pipeline overhead, and a real `mvn -q test` variant with a pre-warmed local `.m2` mount for realistic numbers), rate, count, duplicates ratio.
- Clock source for timings: server-side `queued_at`, `started_at`, `finished_at` (Postgres `now()` at write time); client-side only for webhook response latency.

### 2.2 Benchmarks

| # | Benchmark | Command | Metrics | Vary |
|---|---|---|---|---|
| B1 | **Queue wait time** | `forgeci-tools simulate --rate 2/s --count 100 --variant passing` then `forgeci-tools analyze --since 15m` | queue wait (`started_at − queued_at`) p50 / p95 / p99; webhook response p95 | workers = 1, 2, 4 (`--scale worker=N`), limit = 8 |
| B2 | **Throughput (jobs/min)** | `simulate --rate 10/s --count 200 --variant passing` (saturating) | completed jobs per minute in steady state (exclude first and last 10 %), max concurrent RUNNING observed | workers = 1, 2, 4; then `capacity` 2 vs 4 per worker |
| B3 | **Log throughput** | one `chatty` job (`yes | head -n 200000` or a loop printing 1,000 lines/s for 30 s) | chunks/s and bytes/s persisted (from `log_chunk` timestamps), Postgres insert latency, Redis `PUBLISH` count, memory of worker | chunk size 4 KB vs 16 KB; flush interval 200 ms vs 50 ms |
| B4 | **SSE fan-out** | 1 / 10 / 50 `curl -N` clients on the same running chatty job (a small bash loop), measure with `analyze --job ID` + API `forgeci_sse_clients` and CPU | end-to-end latency (chunk `created_at` → client receipt, use `--stamp` in the simulator's SSE client), API CPU/memory, dropped clients | clients = 1, 10, 50 |
| B5 | **Webhook ingestion under duplicates** | `simulate --rate 20/s --count 500 --duplicates 0.3 --concurrency 10` | webhook p50/p95 latency, builds created = distinct ids, errors = 0 | concurrency 1 vs 10 |
| B6 | **Recovery time** (failure-engineering, not load) | scripted `docker kill worker-1` during B1 | time from kill to attempt 2 RUNNING (expect ≈ lease TTL + sweep interval + backoff); jobs lost = 0 | lease TTL 30 s vs 15 s |

Optional on AWS (M6): repeat B1 and B2 once with 2 workers on the worker instance; record instance types; note the difference from your laptop and *why* (CPU, image pulls, RDS round-trip).

### 2.3 Methodology rules

1. Warm-up: run 20 jobs first so images are pulled and JIT is warm; discard.
2. Steady state: for throughput, measure only after the queue has ≥ 20 items and until it drops below 20.
3. Isolation: nothing else running on the machine; note it if you couldn't guarantee it.
4. Repeat 3×; report all runs; the résumé uses the median.
5. Cross-check: the simulator's client-side p95 and the analyzer's server-side p95 must agree within ~10 %; investigate if not.
6. Keep the raw CSV (`docs/benchmarks/<date>-<benchmark>-<workers>w.csv`) — reviewers and future you will ask.
7. Interpret: one paragraph per benchmark answering "what was the bottleneck?" (use `docker stats`, `pg_stat_statements`, `redis-cli --stat`).

### 2.4 Pitfalls that produce fake numbers (and how the protocol avoids them)

| Pitfall | Effect | Guard |
|---|---|---|
| First run pulls images / downloads Maven deps | p95 inflated 10× | Warm-up discarded; `passing` variant with no dependencies for pipeline-overhead numbers; `.m2` mount for realistic ones |
| Per-repo limit binding, not workers | "adding workers did nothing" | Set limit ≥ workers × capacity for B1/B2 and say so |
| Client clock vs server clock | Negative or skewed queue waits | Server-side timestamps only |
| Averages hide tail | Looks better than it is | p50/p95/p99 only |
| Simulator on the same laptop competing for CPU | Throughput under-reported | Note it; on AWS run the simulator from a separate host |
| Reporting the best of 3 | Dishonest | All 3 reported, median used |
| SSE latency measured to `curl` receipt through nginx buffering | Seconds instead of ms | Measure direct to API and through nginx; report both |
| Counting jobs/min over the whole run including ramp-up/drain | Under-reported | Steady-state window rule |

### 2.5 What the Python tools must output for the report

- `simulate`: a CSV with `delivery_id, duplicate(bool), http_status, webhook_ms, build_id, job_id, queued_at, started_at, finished_at, status, failure_kind, attempt` and a summary block (counts, p50/p95/p99 queue wait, jobs/min steady-state, duplicates rejected, errors).
- `analyze`: Markdown tables ready to paste: by-`failure_kind` counts, per-image duration percentiles, queue-wait percentiles, retries histogram, chunks/s and bytes for `--job`, worker utilisation (`running_jobs` samples if you record them) — and a `--json` form for the CSV cross-check script.
- Both print the exact command line and the commit SHA of the stack (`/actuator/info`) at the top so the report header can be pasted, not typed.

---

## 3. Benchmark report template

Copy per benchmark into `PERFORMANCE.md` (from [`../templates/benchmark-report.md`](../templates/benchmark-report.md), specialized):

```
## B1 — Queue wait time vs worker count

Date: 2026-__-__   Commit: abc1234   Images: sha-abc1234
Environment: <CPU, cores, RAM, disk, OS, Docker x.y>, nothing else running
Stack: compose.yaml + compose.test.yaml; postgres:16, redis:7 (AOF on); worker capacity=2; per-job limits 2 CPU / 2 GiB; repo limit 8
Workload: forgeci-tools simulate --rate 2/s --count 100 --variant passing (step: echo ok); warm-up 20 jobs discarded
Measurement: server-side queued_at → started_at; analyzer: forgeci-tools analyze --since 15m --format md
Runs: 3 per configuration

| Workers | Run | Jobs | Queue wait p50 (s) | p95 (s) | p99 (s) | Webhook p95 (ms) | Jobs/min |
|---|---|---|---|---|---|---|---|
| 1 | 1 |100| | | | | |
| 1 | 2 |100| | | | | |
| 1 | 3 |100| | | | | |
| 2 | … | | | | | | |
| 4 | … | | | | | | |

Median summary: 1 worker → p95 __ s; 2 → __ s; 4 → __ s.
Bottleneck: <e.g. "with 1 worker the queue is CPU-bound on the job containers; with 4 the per-repo limit of 8 and container start (~250 ms) dominate; webhook latency unaffected (p95 __ ms) because enqueue is after commit and non-blocking">
Raw data: docs/benchmarks/2026-__-__-B1-{1,2,4}w.csv
Reproduce: <exact commands>
```

---

## 4. Résumé bullet template

Rules (from [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) and [`RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md)):
- Every number comes from a table in `PERFORMANCE.md` or a test name in the repo. If it is not measured, it is not on the résumé.
- Describe what *you* built and *how it behaves*, not what it "can" do.
- One bullet per interesting mechanism; 3–4 bullets total for ForgeCI; each defensible for 5 minutes ([`interview-questions.md`](./interview-questions.md)).
- Environment qualifiers ("on a 4-core laptop", "on t3.medium") are honest and interviewers respect them.

Templates — replace every `<…>` with your measured value or delete the bullet:

- Built **ForgeCI**, a distributed CI/CD platform (Java 21, Spring Boot multi-module, PostgreSQL, Redis, Docker Engine API, React/TS, AWS) that runs GitHub-triggered builds in isolated, resource-limited containers across a pool of pull-based workers; at-least-once job execution with lease/heartbeat recovery and attempt-based fencing, verified by concurrency tests (`<N>` workers, `<M>` jobs, zero duplicate executions).
- Designed a reliable Redis work queue (`BLMOVE` + per-worker processing lists + TTL leases) with idempotent orphan recovery and a failure taxonomy (app / infra / config / timeout) driving bounded retries with backoff; sustained `<X>` jobs/min with `<W>` workers at queue-wait p95 of `<Y>` s (`<environment>`), measured with a Python webhook load simulator I wrote.
- Implemented live build logs as an end-to-end pipeline (batched log chunks → Postgres + Redis pub/sub → Server-Sent Events with `Last-Event-ID` replay), gap-free across client and server restarts, handling `<C>` chunks/s per job and `<K>` concurrent viewers at `<L>` ms end-to-end latency (`<environment>`).
- Hardened the system with a 16-scenario failure-engineering suite (worker crash/pause, Redis outage, Docker daemon loss, duplicate webhooks, timeouts, cancellation, disk exhaustion) with regression tests; HMAC-verified, deduplicated webhook ingestion (`<D>` deliveries at `<R>`/s with `<P>` % duplicates → zero duplicate builds).
- (Advanced) Added DAG pipelines (`needs:` with Kahn's-algorithm validation, event-driven + reconciling scheduler, fan-out/fan-in, fail-fast) and deployed to AWS (EC2 API + isolated Docker worker instance, RDS PostgreSQL, SSM secrets, CloudWatch alarms); evaluated SQS vs Redis for the queue (ADR).
- (Tools) Wrote a typed, pytest-tested Python toolkit (test-repository generator, HMAC-signed webhook load simulator, result/log analyzer) used for all benchmarks and failure exercises; cross-language HMAC contract test against the Java verifier.

Technology-line additions this project justifies (see [`17-resume-tech-defense/`](../../17-resume-tech-defense/README.md)): Redis (queues, Lua, pub/sub), Docker Engine API, SSE, Testcontainers, GitHub webhooks/OAuth, AWS (EC2/RDS/IAM/CloudWatch/SSM), Python tooling.

---

## 5. GitHub hygiene

- [ ] Repo description, topics (`ci-cd`, `spring-boot`, `redis`, `docker`, `sse`, `java-21`), a social preview image (the architecture diagram).
- [ ] README renders correctly on GitHub: relative links, images in `docs/`, no broken anchors (check with a link checker in CI or by clicking).
- [ ] Badges: CI status, coverage (JaCoCo via a reporting action or a static number with a date), Python tools CI, latest tag.
- [ ] Milestones M1–M6 closed with their issues; issues reference PRs; PRs have descriptions and test evidence; no "wip" commits on `main` (squash-merge).
- [ ] Tags `v0.1-mvp`, `v1.0`, `v1.1` with release notes (`CHANGELOG.md`), images in GHCR public and linked.
- [ ] `docs/screenshots/` and a short GIF of live logs in the README.
- [ ] No secrets in history (`git log -p | grep -i -E 'ghp_|secret|password'`; enable GitHub secret scanning and push protection); `.env.example` with every variable documented.
- [ ] `LICENSE` (MIT), `CONTRIBUTING.md` (how to run tests), `SECURITY.md` at root pointing to `docs/SECURITY.md`.
- [ ] Demo repo (`forgeci-demo-maven`) public with its own README explaining the branches (`passing`, `failing`, `bad-image`, `dag`).
- [ ] Profile README / pinned repos: ForgeCI pinned with the one-line pitch.
- [ ] A `docs/INTERVIEW_NOTES.md` (private if you prefer: keep it local) with your 2-minute and 15-minute outlines and the numbers.

---

## 6. When to write what

| Week | Docs work (≈ 1 h Thu + retro Fri) |
|---|---|
| 14 | README skeleton, `API.md` draft, ADR-001, CI badge |
| 15 | `ARCHITECTURE.md` queue section, ADR-002/004/005, `.forgeci.yml` reference, `CHANGELOG` `v0.1-mvp` |
| 16 | `ARCHITECTURE.md` log pipeline, ADR-003, first screenshots (live log), `DATABASE.md` draft + ERD |
| 17 | ADR-007, `SECURITY.md` draft (taxonomy, cancellation, socket warning), `TESTING.md` draft |
| 18 | `FAILURE_ENGINEERING.md` (16 entries), `PERFORMANCE.md` (B1–B6), `tools/README.md`, `DEPLOYMENT.md` (Compose), docs set complete, `v1.0` |
| 19 | ADR-008, `DEPLOYMENT.md` (AWS), `SECURITY.md` final, `CHANGELOG` `v1.1`, `INTERVIEW_NOTES.md`, deep-dive rehearsal |
| 24–25 | Polish: diagrams redrawn, OWASP mapping, load test rerun on the final code, screenshots refreshed, résumé bullets finalized in [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) |

Track progress in [`trackers/project-tracker.md`](../../trackers/project-tracker.md); technologies proven in [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md).
