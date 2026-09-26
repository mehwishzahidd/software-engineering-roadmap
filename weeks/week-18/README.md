# Week 18 — ForgeCI M5: failure recovery, tests, full stack, benchmarks (v1.0)

[← Week 17](../week-17/) · [Roadmap](../../ROADMAP.md) · [Week 19 →](../week-19/)

**Phase 3 · ForgeCI** (weeks 14–19) · Milestone **M5** · Target: **Strong Résumé Version, tagged `v1.0`**

| Block | Hours | Focus |
|---|---:|---|
| Project | 32 | Failure-engineering suite, integration + concurrency tests, multi-worker Compose stack, CI/CD with image publishing, queue benchmarks, **Python tools** (test-repo generator, webhook load simulator, log analysis) |
| Learning | 5 | Testcontainers (Postgres + Redis), chaos-style tests, Compose multi-worker stacks, GHCR image publishing |
| DSA (Python) | 6 | 2-D DP — **7 new** + spaced reviews + 1 Java rep |
| Interview / review | 4 | OA simulation #2, weekly mock (Track A + Track B), retro, trackers |

---

## 1. Main objective

Turn the ForgeCI that *works when nothing goes wrong* (M1–M4) into the ForgeCI that
**proves it recovers when things go wrong** — and tag it `v1.0`. By Sunday:

- every failure scenario in [`failure-engineering.md`](../../18-projects/forgeci/failure-engineering.md) has been reproduced, diagnosed, fixed or designed around, and covered by a regression test;
- integration tests run against **real Postgres and Redis in Testcontainers** and pass in CI;
- `docker compose up` starts api + N workers + postgres + redis + ui from a clean machine;
- CI builds, tests, publishes images to GHCR and (optionally) deploys;
- **queue wait time and jobs/min are measured** with the methodology recorded, not guessed — driven by ForgeCI's **Python tools** (`tools/`: test-repository generator, webhook load simulator, log/result analysis), each with `pytest` tests and a README.

## 2. Prerequisites

- [Checkpoint 16](../../checkpoints/checkpoint-16.md) passed (or its remediation plan is in your weekly tracker).
- ForgeCI M4 merged: timeouts, cancellation, retry policy, heartbeats, orphan recovery, per-project limits, graceful shutdown ([Week 17](../week-17/)).
- You can already run one api + one worker + postgres + redis locally and stream logs to the UI (M3).
- Testcontainers was first used in FlowGrid M2 ([Week 5](../week-05/)) — this week adds Redis and multi-container wiring.

If M4 is not merged: finish it first (ROADMAP §13 rule 3). Cut M5's benchmark section before cutting its tests.

## 3. Learning topics

| Topic | Subtopics | Read |
|---|---|---|
| Testcontainers with Postgres **and** Redis | `@Testcontainers`/`@Container`, `@ServiceConnection` (Boot 3.1+), `@DynamicPropertySource`, container reuse, one static container per test class vs per suite, `GenericContainer` for Redis | [`09-testing/testcontainers.md`](../../09-testing/testcontainers.md) |
| Chaos-style / failure tests | Killing a worker mid-job, Redis restart, Postgres connection drop, clock skew on leases; deterministic vs probabilistic tests; fault-injection hooks (from LedgerX M4) | [`09-testing/spring-testing.md`](../../09-testing/spring-testing.md), [`18-projects/forgeci/failure-engineering.md`](../../18-projects/forgeci/failure-engineering.md) |
| Compose multi-worker stacks | `deploy.replicas` vs `--scale`, `depends_on` with `condition: service_healthy`, healthchecks, named volumes, Docker socket mount for workers, `profiles` | [`11-docker/compose.md`](../../11-docker/compose.md) |
| CI/CD with image publishing | Job matrix, service containers vs Testcontainers in Actions, `docker/build-push-action`, GHCR login with `GITHUB_TOKEN`, tags (`sha`, `v1.0`), caching layers | [`13-cicd/github-actions.md`](../../13-cicd/github-actions.md), [`13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md) |
| Redis for reliable queues (revisit) | `BLMOVE`, `XPENDING`/`XCLAIM` if using Streams, `INFO` and `SLOWLOG` for measurements | [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md) |
| Benchmark methodology | What to record (env, load, warm-up, duration, percentiles), why one number is not a result | [`18-projects/templates/benchmark-report.md`](../../18-projects/templates/benchmark-report.md), [`18-projects/forgeci/docs-and-resume.md`](../../18-projects/forgeci/docs-and-resume.md) |

## 4. Concepts to learn

### 4.1 Testcontainers: one real Postgres + one real Redis per test class

```java
@SpringBootTest
@Testcontainers
class QueueRecoveryIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

- `static` containers start once per class; a per-method container would add ~5 s each.
- `@ServiceConnection` replaces `@DynamicPropertySource` for datasources Boot knows about; Redis needs the explicit registry (or `@ServiceConnection` on a `RedisContainer` from the `testcontainers-redis` module if you add it — pick one and document why).
- **Interview angle:** "Why Testcontainers over H2?" — because H2 does not have Postgres' locking, `SKIP LOCKED`, JSONB or the exact error codes your retry logic branches on. "Why not mocks?" — the bug you are hunting is in the interaction with the real thing.
- **Where ForgeCI uses this:** every M5 integration test (webhook → build → queue → worker → result) and the orphan-recovery test that needs a real lease expiry in Redis.

### 4.2 Chaos-style tests must be deterministic

A test that "kills a worker mid-job" must not depend on timing luck. Two techniques:

1. **Fault-injection hook** (the LedgerX M4 pattern): a `FailurePoint` bean that tests can arm — `failurePoints.arm("worker.afterLeaseAcquired")` — and the worker checks at that point and throws/exits. In production the bean is a no-op.
2. **Control the clock:** inject `java.time.Clock` into the lease/heartbeat code so the test can advance time instead of sleeping.

```java
// production: Clock.systemUTC(); test: a MutableClock you advance
public boolean leaseExpired(Lease lease) {
    return lease.expiresAt().isBefore(Instant.now(clock));
}
```

- **Interview angle:** "How did you test orphan recovery?" — answer with the hook + clock, not with `Thread.sleep(65000)`.
- **Where ForgeCI uses this:** `OrphanRecoveryIT`, `WorkerCrashMidJobIT`, `RedisRestartIT`.

### 4.3 Compose stack with N workers and health-gated startup

```yaml
services:
  api:
    build: { context: ., dockerfile: api/Dockerfile }
    depends_on:
      postgres: { condition: service_healthy }
      redis:    { condition: service_healthy }
  worker:
    build: { context: ., dockerfile: worker/Dockerfile }
    deploy: { replicas: 3 }
    volumes: [ "/var/run/docker.sock:/var/run/docker.sock" ]
    depends_on: { api: { condition: service_started } }
  postgres:
    image: postgres:16-alpine
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U forgeci"], interval: 5s, retries: 10 }
  redis:
    image: redis:7-alpine
    healthcheck: { test: ["CMD", "redis-cli", "ping"], interval: 5s, retries: 10 }
```

- `docker compose up --scale worker=5` overrides replicas at run time — use it for the benchmark matrix.
- Each worker needs a **unique worker id** (hostname is fine inside Compose) for leases and heartbeats.
- **Interview angle:** "What does mounting the Docker socket give the worker, and why is it dangerous?" — root-equivalent access to the host. You will write the security note in M6; this week just make sure no untrusted config can reach the socket path.
- **Where ForgeCI uses this:** `compose.yaml` at repo root; the benchmark runs use `--scale worker=1|3|5`.

### 4.4 Publishing images from CI

```yaml
- uses: docker/login-action@v3
  with: { registry: ghcr.io, username: ${{ github.actor }}, password: ${{ secrets.GITHUB_TOKEN }} }
- uses: docker/build-push-action@v6
  with:
    context: .
    file: api/Dockerfile
    push: ${{ github.ref == 'refs/heads/main' }}
    tags: ghcr.io/${{ github.repository }}/api:${{ github.sha }}
```

- The workflow needs `permissions: { contents: read, packages: write }`.
- Tag with `sha` always; add `v1.0` on the release tag; never rely on `latest` for deploys.
- **Interview angle:** "Walk me through your pipeline" — test → build image → push → (deploy). Say what fails the build and what the rollback is (redeploy previous sha).
- **Where ForgeCI uses this:** `.github/workflows/ci.yml` builds `api`, `worker`, `ui` images.

### 4.5 Measuring queue wait time and throughput

Define the metrics precisely before measuring:

- **Queue wait** = `startedAt − enqueuedAt` per job (both timestamps set by the server clock, not the worker's).
- **Jobs/min** = completed jobs in a window ÷ minutes, at steady state (after warm-up).
- Record p50/p95/p99, not the mean; record worker count, job duration profile (e.g. `sleep 2`), machine, Redis version, Postgres version.

A tiny SQL for p95 queue wait from your own tables:

```sql
SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM started_at - enqueued_at))
FROM jobs WHERE started_at IS NOT NULL AND enqueued_at > now() - interval '15 minutes';
```

- **Interview angle:** "What was your throughput?" — "X jobs/min with 3 workers on a 2-vCPU laptop, 2-second jobs, p95 queue wait Y ms; bottleneck was container start time, not the queue." That sentence is only allowed if you ran it.

## 5. Resources

- Testcontainers Java docs (modules: PostgreSQL, GenericContainer) and Spring Boot 3 "Testcontainers support" reference section.
- Docker Compose specification: `depends_on` conditions, `deploy.replicas`, healthchecks.
- GitHub Docs: "Publishing Docker images", `docker/build-push-action` README.
- Redis docs: `BLMOVE`, `XAUTOCLAIM`, `INFO`, `SLOWLOG`.
- Grafana k6 docs (only if you drive webhook load with k6; a Java load generator is fine too).
- [`RESOURCES.md`](../../RESOURCES.md) for the curated list.

## 6. Exercises and assignments

### Exercise A — Testcontainers wiring (1.5 h)

Write `AbstractIntegrationTest` (Postgres + Redis, static, reused by all ITs). Acceptance: `mvn verify -pl api` runs the IT profile in < 90 s locally; no test uses `localhost:5432`.

### Exercise B — Compose from zero (1 h)

On a machine (or a fresh clone) with only Docker: `docker compose up --build` → open the UI → register a repo → push → see a green build. Acceptance: no manual step besides `.env` with the GitHub secret.

### Break it (inside ForgeCI)

- Start the stack with `--scale worker=3`, enqueue 20 jobs, `docker kill` one worker mid-job. Predict: which job status, when does the lease expire, who picks it up, does the retry counter increase (it should — infra failure)?
- `docker compose restart redis` while jobs are queued. Predict: what is lost if you use a plain list vs Streams vs AOF persistence?
- Remove the healthcheck `condition` from `api.depends_on`. Predict the first log line.

### Debug it

- A job shows `RUNNING` forever after a worker crash. Use `redis-cli` (`LRANGE processing:<worker>`, `TTL lease:<jobId>`) and the `jobs` table to find whether the lease was never written, never expired, or expired but the reaper didn't run.
- Integration tests pass locally but fail in CI with `Connection refused`. Check whether CI uses Testcontainers (needs Docker) or you accidentally left a `localhost` property in `application-test.yml`.

## 7. DSA — 2-D Dynamic Programming (7 new)

Guide: [`03-dsa/21-dp-2d.md`](../../03-dsa/21-dp-2d.md). Toolkit: [`03-dsa/java-dsa-toolkit.md`](../../03-dsa/java-dsa-toolkit.md). Week 17 opened 2-D DP; this week finishes the core set.

| # | Problem | Pattern note | Time limit |
|---|---|---|---|
| 494 | Target Sum | Subset-sum on a 2-D table (or 1-D map by sum) | 30 min |
| 97 | Interleaving String | `dp[i][j]` = prefix i of s1 and j of s2 form prefix i+j of s3 | 30 min |
| 64 | Minimum Path Sum | Grid DP, in-place | 20 min |
| 221 | Maximal Square | `min(top, left, diag) + 1` | 25 min |
| 72 | Edit Distance | Classic 3-way transition; explain the table out loud | 35 min |
| 329 | Longest Increasing Path in a Matrix | DFS + memo — DP without a table order | 35 min |
| 115 | Distinct Subsequences | Skip vs match; watch `long` overflow | 35 min |

Rules: 25–35 min then read the editorial and log `Solved With Solution`; re-implement blank-file the same day. Reviews due this week: Day-3 of Week 17's Greedy/2-D DP set, Day-7 of Week 16's Intervals, Day-14 of Week 15's 1-D DP, Day-30 of Week 13's Graphs. Log everything in [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md).

## 8. Project work — ForgeCI M5

Spec: [`18-projects/forgeci/README.md`](../../18-projects/forgeci/README.md) · Milestone detail: [`milestones.md`](../../18-projects/forgeci/milestones.md) · Failure suite: [`failure-engineering.md`](../../18-projects/forgeci/failure-engineering.md) · Docs & measurement protocol: [`docs-and-resume.md`](../../18-projects/forgeci/docs-and-resume.md).

### Weekly task checklist

- [ ] Create GitHub milestone `M5 – Failure recovery, tests, full stack, benchmarks`; one issue per row below
- [ ] `AbstractIntegrationTest` with Postgres + Redis Testcontainers
- [ ] End-to-end IT: signed webhook → build → job queued → worker (in-process or test worker) → container step → status `SUCCESS` → log chunks persisted
- [ ] Concurrency ITs: (a) 50 jobs, 3 workers, each job executed **exactly once**; (b) per-project limit 2 never exceeded under 20 concurrent jobs; (c) cancel-while-running kills the container and frees the slot
- [ ] Failure-engineering suite: every scenario in `failure-engineering.md` reproduced → observed → diagnosed → fixed/designed around → regression test
- [ ] Fault-injection hook + injectable `Clock` (if not done in M4)
- [ ] `compose.yaml`: api, worker (replicas), postgres, redis, ui, healthchecks, `.env.example`
- [ ] CI: `mvn verify` (unit + IT) → build 3 images → push to GHCR on `main` → optional deploy job (manual `workflow_dispatch` is fine this week)
- [ ] Benchmark: 3 runs × worker count {1, 3, 5} × 100 jobs; record with `benchmark-report.md`; write `docs/PERFORMANCE.md` draft
- [ ] Docs: README (run in 3 commands), ARCHITECTURE (queue + worker + execution diagram), TESTING (how ITs run, how to arm failure points)
- [ ] Tag `v1.0`, GitHub release notes listing what is measured and what is not

### Acceptance summary

- `mvn verify` green locally and in CI, including ITs on real Postgres + Redis.
- Compose stack up from a clean clone; `--scale worker=5` works without config change.
- Every failure scenario has a linked regression test (issue → PR → test name).
- Benchmark report has environment, load profile, warm-up, duration, p50/p95/p99, worker count, and a one-paragraph bottleneck analysis.
- `v1.0` tag exists; README states the tier honestly ("Strong Résumé Version; DAG pipelines are M6").

### Verification tests you write (describe, then implement)

| Test | Given | When | Then |
|---|---|---|---|
| `WebhookToResultIT` | registered repo, `.forgeci.yml` with 2 steps | signed push event | build `SUCCESS`, 2 step records, log sequence contiguous from 1 |
| `ExactlyOnceExecutionIT` | 50 queued jobs, 3 workers | all drain | `executions` count == 50, each `jobId` once (unique constraint helps) |
| `WorkerCrashMidJobIT` | job running, failure point `worker.afterStepStart` armed | worker throws | job returns to queue after lease TTL; attempt = 2; final `SUCCESS` |
| `AppFailureNoRetryIT` | step exits 1 | job completes | `FAILED`, attempts = 1 |
| `RedisRestartIT` | 10 queued jobs | Redis container restarted | zero lost jobs (Streams/AOF) **or** documented loss + DB-backed requeue |
| `ProjectLimitIT` | limit 2, 20 jobs | workers drain | max concurrent per project observed == 2 |
| `GracefulShutdownIT` | job running | SIGTERM to worker | job finishes or is released within `shutdownTimeout`; no orphan |

### Failure scenarios to run this week (from `failure-engineering.md`)

1. Worker killed mid-job (`docker kill`) — lease expiry recovery.
2. Redis restarted with queued jobs — persistence decision.
3. Postgres unavailable for 30 s during log persistence — chunk buffering/backpressure behaviour.
4. Container image pull fails — infra failure → retry with backoff, max N, then `FAILED_INFRA`.
5. Duplicate webhook delivery under load — dedupe still holds with 3 api replicas? (unique constraint, not an in-memory set).
6. Clock skew between api and worker — leases must use one authority (Redis `TIME` or DB `now()`).

### GitHub expectations

- Milestone M5 closed with all issues linked to PRs; PR descriptions include "how I tested" and the failure scenario reproduced.
- Release `v1.0` with notes; images `ghcr.io/<you>/forgeci/{api,worker,ui}:<sha>` visible.
- `docs/PERFORMANCE.md` links the raw benchmark CSV/notes in the repo.

Do not write implementations from this file — the spec and milestone files give architecture guidance; you write the code.

## 9. Git activity

- Branches: `feat/m5-testcontainers`, `feat/m5-compose-stack`, `feat/m5-ci-images`, `test/m5-failure-suite`, `docs/m5-benchmarks`.
- Conventional commits; squash-merge PRs; protect `main` with the CI check required.
- Tag: `git tag -a v1.0 -m "ForgeCI Strong Résumé Version"` after CI is green on `main`.
- Review your own PR diff before merging — leave two comments on your own code as a reviewer would ([`02-git/workflows.md`](../../02-git/workflows.md)).

## 10. Interview preparation

- **OA simulation #2** (Sat, 90 min): per [`OA_PREP.md`](../../OA_PREP.md) — two timed problems (one Medium from this week's DP set, one unseen Medium from a weak pattern) plus a 20-minute debugging task from [`21-debugging-code-reading/exercises/buggy-library/`](../../21-debugging-code-reading/exercises/buggy-library/). Score against [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md); log in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Weekly mock** (Tue, 1 h): coding mock per [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md). Ask the interviewer to probe "how would you test this?" — M5 is a testing week.
- **Résumé defense drill** (3 questions): Docker and CI/CD this week — [`17-resume-tech-defense/docker.md`](../../17-resume-tech-defense/docker.md), [`17-resume-tech-defense/cicd.md`](../../17-resume-tech-defense/cicd.md), [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md).
- **Story bank:** add one STAR story from this week's failure debugging ([`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md)).
- **Applications:** you crossed OA-ready around W16–17 ([`JOB_READINESS.md`](../../JOB_READINESS.md)); keep the weekly volume target there (internship + new-grad roles), and log every OA invitation as a real data point.

## 11. Revision

- 30 min: re-derive the retry policy decision table (app vs infra failure) from memory; compare with M4 code.
- 30 min: re-read [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md) sections on persistence (RDB vs AOF) — you need them for the Redis-restart scenario.
- DSA reviews as scheduled in the tracker (never skipped).

## 12. Daily plan

| Day | Plan |
|---|---|
| **Mon (8 h)** | Learning 2: Testcontainers + Spring Boot 3 service connections · Project 4.5: `AbstractIntegrationTest`, `WebhookToResultIT` · DSA 1.5: 494, 64 |
| **Tue (8 h)** | Project 5: concurrency ITs (exactly-once, project limit) · DSA 2: 97, 221 + Day-3 reviews · Mock 1 h |
| **Wed (8 h)** | Learning 2: Compose healthchecks/replicas, GHCR publishing · Project 4.5: `compose.yaml`, `.env.example`, `--scale` test · DSA 1.5: 72 |
| **Thu (8 h)** | Project 5: failure suite scenarios 1–3 with regression tests · DSA 2: 329 + Day-7 reviews · Docs 1: TESTING.md |
| **Fri (5 h)** | Project 3: scenarios 4–6, CI image publishing · DSA reviews 1 · Retro 1 |
| **Sat (7 h)** | Project 5: benchmark matrix, `PERFORMANCE.md` draft, tag `v1.0` · OA sim #2 (90 min) + review (30 min) |
| **Sun (2–3 h)** | End-of-week test · Day-14/30 reviews · trackers · plan W19 · rest |

## 13. End-of-week test (45 min, closed notes)

1. Write from memory the Testcontainers setup for Postgres + Redis in a Spring Boot test (10 min).
2. Explain the exactly-once guarantee of your queue: what makes it *at-least-once*, and which constraint turns duplicate execution into a no-op?
3. Draw your Compose stack and label every healthcheck and dependency.
4. Given a benchmark that shows 40 jobs/min with 5 workers and 38 jobs/min with 3, what do you conclude and what would you measure next?
5. Solve LeetCode 62 (Unique Paths) in 10 min as a warm-up, then 1143 (LCS) in 20 min — both from Week 17, now timed.

Pass: 4/5 with confident explanations. Fail → remediation block in W19 Sunday.

## 14. Mastery checklist

- [ ] I can write a Postgres + Redis Testcontainers base class from memory
- [ ] I can make a "worker crashes mid-job" test deterministic (failure point + injectable clock)
- [ ] I can explain the difference between at-least-once delivery and exactly-once *effect*
- [ ] I can bring the whole stack up from a clean clone and scale workers
- [ ] My CI publishes sha-tagged images and I can say what a rollback is
- [ ] My benchmark numbers have environment, load, warm-up, percentiles and a bottleneck analysis
- [ ] I can solve Edit Distance and Interleaving String from a blank file, explaining the transition

## 15. Expected deliverables

- ForgeCI `v1.0` tagged; milestone M5 closed; `docs/PERFORMANCE.md`, `docs/TESTING.md` committed.
- [`trackers/project-tracker.md`](../../trackers/project-tracker.md): M5 row complete, measured numbers pasted with link to report.
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 7 new + reviews logged.
- [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md): OA sim #2 score, mock feedback.
- [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): Testcontainers, Docker Compose, GitHub Actions moved to "used in production-like setup".
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md): retro (what broke, what I would do differently).

## 16. If behind / stretch

**Behind:** keep tests and the Compose stack; drop the deploy job (M6 covers AWS) and reduce the benchmark to one worker count with one honest paragraph. Do not tag `v1.0` without ITs and the failure suite.

**Stretch:** GitHub Actions job matrix running ITs against Postgres 15 and 16; k6 script that fires signed webhooks at 5 rps; per-step timing histogram in the UI.
