# Week 19 — ForgeCI M6: DAG pipelines + AWS (Advanced Version)

[← Week 18](../week-18/) · [Roadmap](../../ROADMAP.md) · [Week 20 →](../week-20/)

**Phase 3 · ForgeCI** (weeks 14–19) · Milestone **M6** · Target: **Advanced Version** on top of `v1.0`

| Block | Hours | Focus |
|---|---:|---|
| Project | 32 | `needs:` job dependencies, topological scheduling, fan-out/fan-in, fail-fast; AWS deployment of a multi-service app; docs |
| Learning | 5 | DAGs from DSA to production, Docker-socket security, AWS multi-service deployment, SQS considered vs Redis |
| DSA | 6 | Advanced Graphs + Bit Manipulation — **7 new** + reviews |
| Interview / review | 4 | OA simulation #3, weekly mock, **ForgeCI deep-dive rehearsal** |

---

## 1. Main objective

Ship ForgeCI's one hard feature — **pipelines as a DAG** — and put the whole system on AWS.
The topological sort you learned in Week 14 becomes the scheduler; the Docker-socket
discussion becomes a written security note; the "why Redis and not SQS" question gets an ADR.
End the week with a 12-minute ForgeCI deep-dive you can deliver without notes.

## 2. Prerequisites

- ForgeCI `v1.0` tagged ([Week 18](../week-18/)) with ITs, failure suite and Compose stack. If not tagged, **finish M5 first** — M6 is Advanced and is the first thing to cut (ROADMAP §8).
- Topological sort + Union-Find from [Week 14](../week-14/) (`03-dsa/16-topological-sort.md`).
- AWS deploys done twice already (FlowGrid W8, LedgerX W13): IAM user/roles, EC2 + Compose, RDS, CloudWatch.

## 3. Learning topics

| Topic | Subtopics | Read |
|---|---|---|
| DAGs in production | Kahn's algorithm as an event-driven scheduler, in-degree tables in Postgres, cycle detection at config-parse time, fan-out/fan-in, fail-fast vs continue-on-error | [`03-dsa/16-topological-sort.md`](../../03-dsa/16-topological-sort.md), [`18-projects/forgeci/milestones.md`](../../18-projects/forgeci/milestones.md) |
| Docker-socket security | What `/var/run/docker.sock` grants, rootless Docker, socket proxies, running untrusted steps: no privileged, no host mounts, resource limits, network isolation | [`11-docker/README.md`](../../11-docker/README.md), [`10-linux/README.md`](../../10-linux/README.md) |
| AWS for multi-service apps | One EC2 (api + workers via Compose) vs separate worker EC2; RDS Postgres; Redis on EC2 vs ElastiCache (cost); security groups per role; IAM instance roles; CloudWatch log groups per service; budgets | [`12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md), [`12-aws/ec2.md`](../../12-aws/ec2.md), [`12-aws/rds.md`](../../12-aws/rds.md), [`12-aws/iam.md`](../../12-aws/iam.md), [`12-aws/cloudwatch.md`](../../12-aws/cloudwatch.md), [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md) |
| SQS considered | Visibility timeout ≈ lease, DLQ ≈ max attempts, FIFO vs standard, why you still chose Redis (pub/sub + counters + one dependency) | [`15-system-design/scalability.md`](../../15-system-design/scalability.md), [`12-aws/README.md`](../../12-aws/README.md) |
| CI/CD deploy stage | `workflow_dispatch` + environment protection, SSH/SSM deploy, pulling sha-tagged images | [`13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md) |

## 4. Concepts to learn

### 4.1 Kahn's algorithm, but event-driven

In an interview you run Kahn's in a loop. In ForgeCI, jobs finish at unpredictable times, so the
"queue" step happens **when a dependency completes**, not in a loop:

```java
// on job completion, inside one transaction
void onJobFinished(UUID buildId, String finishedJob, JobStatus status) {
    for (String dependent : dag.dependentsOf(finishedJob)) {
        int remaining = jobRepo.decrementPendingDeps(buildId, dependent); // UPDATE ... RETURNING pending_deps
        if (remaining == 0 && status == SUCCESS) queue.enqueue(buildId, dependent);
    }
}
```

- `pending_deps` lives in the `jobs` table so a crashed api does not lose the in-degree; the decrement is a single atomic `UPDATE … SET pending_deps = pending_deps - 1 … RETURNING pending_deps`.
- Jobs with in-degree 0 are enqueued when the build is created — that is the "initial queue" in Kahn's.
- **Cycle detection** happens at parse time: run full Kahn's over the config; if processed < job count, reject the pipeline with a `ProblemDetail` listing the jobs in the cycle.
- **Interview angle:** "How would you schedule a pipeline with dependencies?" — in-degrees, enqueue zeros, decrement on completion; then say where the in-degree is stored and what happens on crash.
- **Where ForgeCI uses this:** `.forgeci.yml` `jobs.<name>.needs: [a, b]`; `DagScheduler`; `PipelineConfigParser`.

### 4.2 Fan-out / fan-in and fail-fast

- Fan-out: `test-unit`, `test-it`, `lint` all `needs: [build]` → three jobs become runnable at once → per-project concurrency limit (M4) throttles them.
- Fan-in: `deploy` `needs: [test-unit, test-it, lint]` → runs only when `pending_deps` hits 0.
- Fail-fast: when any job fails, mark every **not-yet-started** transitive dependent `SKIPPED`; running siblings either continue (`fail_fast: false`) or are cancelled via M4's cancel path (`fail_fast: true`). Build status = `FAILED` either way.
- **Interview angle:** "What is the difference between a skipped job and a cancelled job in your data model?" — skipped never got a queue entry; cancelled had one and may have had a container.

### 4.3 Minimal `.forgeci.yml` with dependencies

```yaml
image: maven:3.9-eclipse-temurin-21
jobs:
  build:   { steps: ["mvn -q -DskipTests package"] }
  unit:    { needs: [build], steps: ["mvn -q test"] }
  lint:    { needs: [build], steps: ["mvn -q checkstyle:check"] }
  package: { needs: [unit, lint], steps: ["docker build -t app ."] }
fail_fast: true
```

Parser rules: unknown `needs` target → 400; self-dependency → 400; cycle → 400 with the cycle path; job names `[a-z0-9-]{1,40}`.

### 4.4 Docker socket: the security note you must write

The worker talks to Docker through the mounted socket. Anyone who controls a step command controls
the container, but **not** the host — unless you let them. The note in `docs/SECURITY.md` must state:

- steps run with `--privileged=false`, no host bind mounts except the workspace volume, `--memory`/`--cpus` limits, `--network` restricted or none for untrusted repos, non-root user where the image allows it;
- the socket is never mounted into *step* containers (only the worker has it);
- the honest limitation: a step can still consume host resources; production systems use rootless Docker, a socket proxy, or a VM/firecracker boundary — you did not, and you say why (scope).
- **Interview angle:** "Is it safe to run user code in your CI?" — the right answer starts with "no, and here is the boundary I chose and the one I would add next."

### 4.5 SQS vs Redis: write the ADR

| Concern | Redis (chosen) | SQS |
|---|---|---|
| Lease / visibility | `BLMOVE` + TTL key you manage | visibility timeout built in |
| Retry limit / DLQ | your counter | redrive policy + DLQ built in |
| Pub/sub for logs, per-project counters | same server | need extra services |
| Local dev / Testcontainers | trivial | LocalStack or mocks |
| Durability | AOF/RDB you configure | managed, multi-AZ |

Use [`18-projects/templates/adr.md`](../../18-projects/templates/adr.md). Decision: Redis for one-dependency simplicity and local testability; migration path to SQS documented (queue port interface already isolates it).

### 4.6 Deploying a multi-service stack on AWS

- **Topology (cheap):** one `t3.small` (or similar) EC2 running Compose with api + 2 workers + Redis; RDS Postgres (smallest class, no multi-AZ); S3 for artifacts if you export logs; CloudWatch agent shipping `docker logs`.
- **Security groups:** `sg-api` allows 443/80 from the internet (or only your IP), `sg-rds` allows 5432 from `sg-api` only; Redis not exposed.
- **Secrets:** `.env` on the instance via SSM Parameter Store or written manually — never in the image or the repo.
- **Budget alarm first**, deploy second ([`12-aws/cost-safety.md`](../../12-aws/cost-safety.md)).
- **Interview angle:** "How is it deployed?" — say the topology, the SG rules, where secrets live, and what you would change for HA (separate worker instances, ElastiCache, ALB).

## 5. Resources

- Docker docs: "Docker daemon attack surface", rootless mode.
- AWS docs: EC2 instance roles, RDS security groups, CloudWatch agent for Docker logs, SQS visibility timeout & DLQ (for the ADR).
- Redis docs: persistence (AOF `appendfsync everysec`).
- Kahn (1962) as summarised in [`03-dsa/16-topological-sort.md`](../../03-dsa/16-topological-sort.md); NeetCode "Advanced Graphs" list.
- [`RESOURCES.md`](../../RESOURCES.md).

## 6. Exercises and assignments

### Exercise A — DAG parser (1.5 h)

Parse the YAML above into a `PipelineGraph`; unit-test: valid graph → topological order; cycle `a→b→a` → error naming both; unknown target → error. Acceptance: pure Java, no Spring, 100 % branch coverage of the parser's error paths.

### Exercise B — AWS dry run on paper (45 min)

Draw the topology with SG arrows and the IAM instance-role permissions (CloudWatch logs write, S3 put to one bucket, SSM read of one path). Acceptance: no `*` in any policy.

### Break it (inside ForgeCI)

- Submit a config with a 3-job cycle. Predict the error message and HTTP status before you push.
- Fan-out of 6 jobs with per-project limit 2: predict the order and the queue-wait numbers; compare with the M5 benchmark.
- Kill the api **between** `decrementPendingDeps` and `enqueue`. Predict: is the job lost? (It should not be — either the enqueue happens in the same transaction via an outbox/DB-backed queue entry, or a reconciler re-enqueues `pending_deps = 0 AND status = PENDING`.)
- On EC2: stop Redis. Predict what the api returns and what CloudWatch shows.

### Debug it

- A fan-in job never starts although all dependencies succeeded. Query `SELECT name, pending_deps, status FROM jobs WHERE build_id = …` — is `pending_deps` stuck at 1 (a double-decrement guard bug?) or 0 with status `PENDING` (enqueue lost)?
- Deploy works but the UI shows no live logs: SSE through a reverse proxy needs buffering off (`X-Accel-Buffering: no` for nginx) and the security group must allow long-lived connections (it does; check the proxy first).

## 7. DSA — Advanced Graphs + Bit Manipulation (7 new)

Guides: [`03-dsa/22-advanced-graphs.md`](../../03-dsa/22-advanced-graphs.md), [`03-dsa/23-bit-manipulation.md`](../../03-dsa/23-bit-manipulation.md).

| # | Problem | Pattern note | Time limit |
|---|---|---|---|
| 743 | Network Delay Time | Dijkstra with `PriorityQueue<int[]>`; adjacency list | 30 min |
| 1584 | Min Cost to Connect All Points | Prim's (O(n²) is fine) — MST awareness | 30 min |
| 787 | Cheapest Flights Within K Stops | Bellman-Ford with k+1 relaxations (Dijkstra breaks with the stop limit — explain why) | 35 min |
| 136 | Single Number | XOR identity | 10 min |
| 191 | Number of 1 Bits | `n & (n - 1)` trick; `Integer.bitCount` for comparison | 10 min |
| 338 | Counting Bits | `dp[i] = dp[i >> 1] + (i & 1)` | 15 min |
| 268 | Missing Number | XOR or sum; explain overflow-safety | 10 min |

Reviews due: Day-3 of W18 2-D DP, Day-7 of W17 Greedy, Day-14 of W16 Intervals/1-D DP, Day-30 of W14 Topological Sort + Union-Find (which you are also using in the project this week — note in the tracker how the production use changed your understanding).

## 8. Project work — ForgeCI M6

Spec: [`18-projects/forgeci/README.md`](../../18-projects/forgeci/README.md) · [`milestones.md`](../../18-projects/forgeci/milestones.md) · [`failure-engineering.md`](../../18-projects/forgeci/failure-engineering.md) · [`docs-and-resume.md`](../../18-projects/forgeci/docs-and-resume.md) · [`interview-questions.md`](../../18-projects/forgeci/interview-questions.md).

### Weekly task checklist

- [ ] Milestone `M6 – DAG pipelines + AWS`; issues per item
- [ ] Config parser: `jobs.<name>.needs`, validation, cycle detection with cycle path in the error
- [ ] Schema: `jobs.pending_deps`, `job_dependencies(build_id, job, needs)`, `builds.fail_fast`
- [ ] `DagScheduler`: enqueue in-degree-0 jobs at build creation; decrement-and-enqueue on completion (transactional, crash-safe)
- [ ] Fail-fast: transitive `SKIPPED`, optional cancel of running siblings, build status roll-up
- [ ] UI: DAG view (a simple layered list is acceptable; a graph drawing is stretch), `SKIPPED` badge
- [ ] Tests: parser unit tests; `DagSchedulingIT` (diamond graph), `FailFastIT`, `CrashBetweenDecrementAndEnqueueIT`
- [ ] ADR: Redis vs SQS; ADR: SSE vs WebSockets (if not written in M3)
- [ ] `docs/SECURITY.md`: Docker-socket note, step-container hardening, secrets handling
- [ ] AWS: budget alarm → IAM role → RDS → EC2 with Compose → CloudWatch logs → HTTPS (or documented HTTP + IP allow-list) → smoke test via a real GitHub push
- [ ] CI: deploy job (`workflow_dispatch`, environment `production` with required reviewer = you)
- [ ] `docs/DEPLOYMENT.md`; README updated with the live URL (or "deployed on demand; see DEPLOYMENT.md" if you tear down for cost)
- [ ] Tag `v1.1-dag` (Advanced) — `v1.0` remains the résumé anchor

### Acceptance summary

- Diamond pipeline (build → {unit, lint} → package) runs in the correct order with correct fan-in; cycle configs rejected with a clear error.
- Fail-fast behaviour matches the config flag and is tested.
- A crash between dependency decrement and enqueue does not lose a job (test proves it).
- Stack reachable on AWS; a real push to a registered repo produces a green build with live logs.
- SECURITY.md and both ADRs merged.

### Verification tests

| Test | Given | When | Then |
|---|---|---|---|
| `PipelineConfigParserTest` | cycle `a→b→c→a` | parse | error lists `[a, b, c]` |
| `DagSchedulingIT` | diamond graph | build runs with 2 workers | `package` starts only after both `unit` and `lint` finish; order recorded |
| `FailFastIT` | `lint` exits 1, `fail_fast: true` | | `package` = `SKIPPED`, `unit` cancelled if running, build `FAILED` |
| `ContinueOnFailureIT` | same, `fail_fast: false` | | `unit` completes; `package` `SKIPPED`; build `FAILED` |
| `CrashBetweenDecrementAndEnqueueIT` | failure point `scheduler.afterDecrement` | | reconciler enqueues `pending_deps = 0` job; runs exactly once |
| `DeploySmokeTest` (manual, documented) | AWS stack | push to demo repo | green build, logs visible, CloudWatch has worker logs |

### Failure scenarios to run

1. Cycle in config (parse-time).
2. Crash between decrement and enqueue.
3. Two workers finish the two parents of a fan-in at the same instant — double decrement race (atomic `UPDATE` protects; test with the failure point + latch).
4. Redis down on AWS while a DAG is mid-flight — build stays consistent in Postgres; jobs resume after Redis returns (or documented behaviour).
5. EC2 reboot — Compose `restart: unless-stopped`, workers re-register, orphan recovery kicks in.

### GitHub expectations

- Milestone M6 closed; ADRs under `docs/adr/`; `docs/SECURITY.md`, `docs/DEPLOYMENT.md`.
- Release `v1.1-dag` notes: what is new, what remains out of scope (matrix jobs, artifacts between jobs, secrets management UI).

## 9. Git activity

- Branches: `feat/m6-dag-parser`, `feat/m6-scheduler`, `feat/m6-fail-fast`, `docs/m6-adr-security`, `ops/m6-aws-deploy`.
- Never commit `.env`, keys, or `terraform.tfstate`-like files; add a pre-commit secret scan if you have not.
- Tag `v1.1-dag`; keep `v1.0` intact — your résumé links the release, not `main`.

## 10. Interview preparation

- **ForgeCI deep-dive rehearsal** (Sat, 2 h): record yourself doing the 12-minute walkthrough per [`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md), then answer 10 questions from [`18-projects/forgeci/interview-questions.md`](../../18-projects/forgeci/interview-questions.md) cold. Watch the recording; note every "um, I think". Redo the weakest 3 answers.
- **OA simulation #3** (Fri or Sat, 90–120 min): [`OA_PREP.md`](../../OA_PREP.md) — include one graph problem and one buggy-library task ([`21-debugging-code-reading/drills.md`](../../21-debugging-code-reading/drills.md)).
- **Weekly mock** (Tue): [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md). Target score ≥ 3/4 — the junior-ready criterion.
- **Résumé defense:** AWS and Redis this week — [`17-resume-tech-defense/aws.md`](../../17-resume-tech-defense/aws.md), [`17-resume-tech-defense/redis.md`](../../17-resume-tech-defense/redis.md).
- **Applications:** with three projects (two deployed, ForgeCI deploying this week) you are at the **junior-role-ready** threshold of [`JOB_READINESS.md`](../../JOB_READINESS.md) — raise weekly application volume to that tier's target and start tracking response rates in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Recruiter screen** prep: 20 min reading [`16-interview-prep/recruiter-screen.md`](../../16-interview-prep/recruiter-screen.md); update your 60-second "about me" to mention ForgeCI.

## 11. Revision

- Re-explain out loud: lease-based queue, retry taxonomy, SSE replay — the three ForgeCI pillars the deep-dive will hit.
- Re-read [`14-cs-fundamentals/operating-systems.md`](../../14-cs-fundamentals/operating-systems.md) sections on processes/signals (why SIGTERM → graceful shutdown works).
- DSA reviews as scheduled.

## 12. Daily plan

| Day | Plan |
|---|---|
| **Mon (8 h)** | Learning 2: DAG scheduling + parser design · Project 4.5: parser + schema + unit tests · DSA 1.5: 743 |
| **Tue (8 h)** | Project 5: `DagScheduler` transactional decrement/enqueue, `DagSchedulingIT` · DSA 2: 1584, 136 + reviews · Mock 1 h |
| **Wed (8 h)** | Learning 2: Docker-socket security, SQS vs Redis · Project 4.5: fail-fast + tests, ADRs · DSA 1.5: 787 |
| **Thu (8 h)** | Project 5: AWS — budget alarm, IAM, RDS, EC2, Compose, CloudWatch · DSA 2: 191, 338 · Docs 1: SECURITY.md |
| **Fri (5 h)** | Project 3: deploy job in CI, smoke test via real push, DEPLOYMENT.md · OA sim #3 (counts toward interview hours) · Retro |
| **Sat (7 h)** | Project 5: UI DAG view, crash test, release `v1.1-dag` · Deep-dive rehearsal 2 h |
| **Sun (2–3 h)** | End-of-week test · reviews (268 as warm-up) · trackers · plan W20 · rest |

## 13. End-of-week test

1. Implement Kahn's algorithm on paper for a 6-node graph, then explain how your scheduler differs (event-driven, persisted in-degree).
2. Given `fail_fast: true` and a failing job with two running siblings and three pending dependents, list every job's final status.
3. Write the SG rules for api / worker / RDS / Redis from memory.
4. Why does Dijkstra fail for LeetCode 787, and what did you use instead?
5. Explain what the Docker socket mount grants and the three hardening measures you applied to step containers.

Pass: 4/5.

## 14. Mastery checklist

- [ ] I can explain DAG scheduling with persisted in-degrees and crash-safety
- [ ] I can state the fail-fast semantics of my system precisely (skipped vs cancelled)
- [ ] I can deploy a Compose-based multi-service app to AWS with least-privilege IAM and correct SGs
- [ ] I can argue Redis vs SQS with concrete trade-offs and a migration path
- [ ] I can give the ForgeCI deep-dive in 12 minutes without notes and answer "how did you test X?" for every pillar
- [ ] Dijkstra, Prim, Bellman-Ford(k), XOR tricks — solved independently

## 15. Expected deliverables

- ForgeCI `v1.1-dag` released; live (or on-demand) AWS deployment documented.
- [`trackers/project-tracker.md`](../../trackers/project-tracker.md): ForgeCI phase closed — hours actually spent vs 170–200 target.
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md), [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md) (OA #3, mock, deep-dive self-score), [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md), [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md).

## 16. If behind / stretch

**Behind:** DAG scheduling without fail-fast cancellation (skip only) is acceptable; deploy with HTTP + IP allow-list instead of TLS; but the deep-dive rehearsal is not optional — CP-20 asks "is ForgeCI shipped and explainable?".

**Stretch:** artifacts between jobs via a shared S3 prefix; matrix jobs; `needs` with `if: failure()` semantics; GitHub Checks API status back to the PR.
