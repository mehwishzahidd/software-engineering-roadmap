# Week 17 — ForgeCI M4: timeouts, cancellation, retries, limits, health

[← Week 16](../week-16/) · [Roadmap](../../ROADMAP.md) · [Week 18 →](../week-18/)

**Phase 3 · ForgeCI (Weeks 14–19)** — the milestone that turns a pipeline that works into a platform that *survives*: per-job timeouts that actually kill containers, cancellation of queued and running jobs, a retry policy that knows the difference between "your tests failed" and "the worker died", worker heartbeats, orphaned-job recovery via lease expiry, per-project concurrency limits, and graceful shutdown. Also: **OA simulation #1**.

| Block | Hours | Notes |
|---|---:|---|
| Project (ForgeCI M4) | 32 | Timeouts (kill container), cancel queued + running, retry policy (app vs infra), heartbeats, orphan recovery, per-project limits (Redis), graceful shutdown |
| Learning | 6 | Failure taxonomy, heartbeats & leases, semaphores/limits in Redis, graceful shutdown |
| DSA (Python) | 6 | Greedy + 2-D DP — 8 new + reviews + 1 Java rep |
| Interview / review | 4 | **OA simulation #1** (Track A Python + Java buggy-library task), weekly mock #5, drill |

---

## 1. Main objective

By Sunday, for every job:

- a **timeout** (from config, default 10 min, max 60) is enforced by the worker: on expiry it sends `SIGTERM` to the step process, waits 10 s, then **kills the container**, records `TIMED_OUT` with a final system log chunk, and cleans up;
- **cancel** works for `QUEUED` jobs (removed from the queue *and* marked `CANCELLED` atomically enough — decide the order and cover the race) and for `RUNNING` jobs (cancel flag observed by the worker → kill container → `CANCELLED`);
- a **retry policy** distinguishes **app failure** (exit code ≠ 0: never retried) from **infra failure** (image pull error, container start error, Docker daemon down, worker lost): retried with exponential backoff + jitter, max N attempts, then `INFRA_ERROR`;
- workers send **heartbeats** (`worker:<id>` with TTL) and **renew job leases**; a **reaper** finds jobs whose lease expired (worker died) and requeues them as infra failures (attempt+1), so no job is ever stuck `RUNNING` forever;
- a **per-project concurrency limit** (Redis counter/semaphore with TTL safety) caps running jobs per project; excess jobs wait in the queue without busy-looping;
- **graceful shutdown**: `SIGTERM` to a worker stops claiming, lets running steps finish up to a deadline, then cancels them with a clear status; `SIGTERM` to the API completes SSE streams cleanly.

## 2. Prerequisites

- Tag `m3`; Checkpoint 16 done (remediation items scheduled).
- Week 14 signals knowledge (TERM/KILL, exit 137/143) and Week 15 interruption contract — this week applies both to real containers.
- LedgerX's lease-style claim (`SKIP LOCKED`, scheduled payments) and idempotency keys — retries reuse the pattern.
- 1-D DP fluent in Python; 2-D DP starts this week (table aliasing pitfall: `[[0]*n for _ in range(m)]`).

## 3. Learning topics

| Topic | Subtopics | File |
|---|---|---|
| Failure taxonomy | app vs infra vs timeout vs cancel; retryable vs terminal; error classification at the boundary; idempotent retries; poison jobs | [`14-cs-fundamentals/software-engineering-practices.md`](../../14-cs-fundamentals/software-engineering-practices.md), [`15-system-design/fundamentals.md`](../../15-system-design/fundamentals.md) |
| Heartbeats & leases | liveness vs safety, TTL choice vs renewal interval (renew at ≤ ⅓ TTL), clock skew, fencing tokens (why `attempt` acts as one), split-brain: two workers on one job | [`14-cs-fundamentals/concurrency.md`](../../14-cs-fundamentals/concurrency.md), [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md) |
| Semaphores/limits in Redis | `INCR`/`DECR` with TTL safety, Lua for atomic check-and-increment, sorted-set semaphore with expiry, releasing on crash (reaper), fairness | [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md), [`15-system-design/scalability.md`](../../15-system-design/scalability.md) |
| Timeouts & cancellation in Java | `Future.cancel(true)`, `CompletableFuture.orTimeout`, cooperative cancellation flags, `ScheduledExecutorService` watchdog, kill sequence TERM → KILL | [`01-java/07-concurrency.md`](../../01-java/07-concurrency.md) |
| Graceful shutdown | JVM shutdown hooks, Spring `SmartLifecycle` phases, `server.shutdown=graceful`, Docker `stop_grace_period`, Compose `stop` timeouts, draining SSE | [`05-spring-boot/01-core-di.md`](../../05-spring-boot/01-core-di.md), [`11-docker/compose.md`](../../11-docker/compose.md), [`10-linux/README.md`](../../10-linux/README.md) |
| Retry design | exponential backoff + full jitter, max attempts, retry budgets, retry storms, `Retry-After`, where to store attempt state | [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md), [`15-system-design/fundamentals.md`](../../15-system-design/fundamentals.md) |
| Health | Actuator health groups (liveness/readiness), custom indicators (Redis, Docker daemon), what a worker's "ready" means | [`05-spring-boot/06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md) |

## 4. Concepts to learn

### 4.1 Failure taxonomy — the decision table

| Outcome | Cause | Status | Retry? | Who decides |
|---|---|---|---|---|
| Step exit ≠ 0 | user's code/tests | `FAILED` | **No** | executor |
| Image pull / container create / start error | infra | `INFRA_ERROR` (attempt < N → requeue) | Yes, backoff | executor |
| Docker daemon unreachable mid-job | infra | same | Yes | executor |
| Lease expired (worker died) | infra | requeue attempt+1; after N → `INFRA_ERROR` | Yes | reaper |
| Timeout | job exceeded limit | `TIMED_OUT` | No (by default; configurable) | worker watchdog |
| Cancel | user | `CANCELLED` | No | API + worker |
| Config invalid | user | `CONFIG_ERROR` | No | api (M1) |

Encode this as a pure function `RetryPolicy.decide(Outcome, attempt) → Decision{status, requeueAfter}` with an exhaustive test. Backoff: `min(cap, base * 2^attempt) * random(0.5, 1.0)` (full/half jitter), `base = 5 s`, `cap = 5 min`, `N = 3`.

- **Interview angle (Track B):** "Why never retry an exit code ≠ 0?" → the code is deterministic from the user's perspective; retrying hides flakiness and wastes capacity; users retry explicitly. "Why jitter?" → avoids synchronized retry storms after an outage.
- **Where ForgeCI uses this:** executor result mapping, reaper, `POST /jobs/{id}/retry` (manual, user-initiated, allowed for `FAILED` too — a *new* attempt by choice).

### 4.2 Timeouts that kill for real

A watchdog per running job (`ScheduledExecutorService.schedule(deadline)`) sets `cancelReason=TIMEOUT` and calls `kill(job)`: `docker exec` the step's process gets `SIGTERM` (`docker kill --signal TERM` on the exec's PID is not directly supported; practical approach: run each step via `sh -c` under `timeout --signal=TERM --kill-after=10s <n>s` **inside** the container, *and* have the worker `stopContainerCmd(t=10)` then `killContainerCmd` as the outer guarantee). Two layers: inner `timeout` gives the process a chance to clean up; outer stop/kill guarantees termination even if the inner is bypassed (a step that ignores TERM). Record the final system chunk: `"[forgeci] job timed out after 600s; container killed"`.

```java
// watchdog registration when a step starts (sketch)
ScheduledFuture<?> watchdog = scheduler.schedule(() -> cancel(job, CancelReason.TIMEOUT),
                                                 job.timeout().toSeconds(), TimeUnit.SECONDS);
try { runSteps(job); } finally { watchdog.cancel(false); }
```

- **Interview angle:** "How do you kill a process that ignores SIGTERM?" → SIGKILL after a grace period; "Why not rely on `timeout` alone?" → it runs inside the untrusted job.

### 4.3 Cancellation — queued vs running

**Queued:** `POST /jobs/{id}/cancel` → in one DB transaction set `status=CANCELLED` if currently `QUEUED` (conditional `UPDATE ... WHERE status='QUEUED' RETURNING`), then `LREM` from the Redis queue. Race: a worker may `BLMOVE` it between the UPDATE and the LREM → the worker **re-checks status in Postgres after claim** and acks a `CANCELLED` job without running it. That re-check is the safety net; the LREM is the optimisation.

**Running:** set `cancel_requested=true` (DB) and `PUBLISH forgeci:control:<jobId> cancel` (fast path). The worker's job context subscribes (or polls every 2 s as fallback) → `kill(job)` → `CANCELLED` → cleanup → ack. If the worker is dead, the reaper sees `cancel_requested` on an expired lease and marks `CANCELLED` instead of requeuing.

- **Where ForgeCI uses this:** UI cancel button (M3 UI gets it this week), graceful shutdown reuses `cancel(job, SHUTDOWN)`.

### 4.4 Heartbeats, leases and the reaper

Worker: `SET forgeci:worker:<id> <json> EX 30` every 10 s (heartbeat) and `PEXPIRE forgeci:lease:<jobId> 60000` every 20 s per running job (renewal at ⅓ TTL). Reaper (in `api`, `@Scheduled` every 30 s, multi-instance-safe via a Redis lock `SET forgeci:reaper NX PX 25000` or Postgres advisory lock): for every job `RUNNING` in Postgres whose lease key is **absent** → the worker died: `RetryPolicy.decide(WORKER_LOST, attempt)` → requeue attempt+1 or `INFRA_ERROR`; write a system log chunk explaining it. **Fencing:** a zombie worker that wakes up later and tries to write results for `(job, attempt=1)` is rejected because the job is now on attempt 2 (`UPDATE ... WHERE attempt = :myAttempt`).

- **Interview angle:** "What if the worker is alive but partitioned from Redis?" → its lease expires, the reaper requeues, the old worker keeps running the container → duplicate execution (at-least-once!); the fencing `attempt` check stops it from *reporting*, and the old worker should kill its container when it detects lost heartbeats (self-fencing). Explain both.

### 4.5 Per-project concurrency limit

Atomic acquire in Lua (≤ 25 lines — type it yourself):

```lua
-- KEYS[1]=forgeci:running:<projectId>  ARGV[1]=limit  ARGV[2]=jobId  ARGV[3]=ttlMs
local n = redis.call('ZCARD', KEYS[1])
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[4])   -- ARGV[4]=now-ttl: drop stale members
if n >= tonumber(ARGV[1]) then return 0 end
redis.call('ZADD', KEYS[1], ARGV[5], ARGV[2])               -- ARGV[5]=now (score)
return 1
```

A sorted set with timestamps (refreshed with the lease) lets crashed holders expire; release = `ZREM` on completion. On `0` the worker **does not** hold the job: push it back (`LMOVE` to the tail) and back off 2–5 s, or (better) keep per-project queues and only pull from projects under limit — ADR-worthy; the simple version is acceptable for v1.0 as long as the test proves the limit is never exceeded.

- **Where ForgeCI uses this:** M5's concurrency test "limit 2 never exceeded under 20 jobs"; FlagForge later reuses the Lua-with-TTL idea for stampede protection.

### 4.6 Graceful shutdown

Worker: on `SIGTERM` (Compose `stop_grace_period: 90s`), `SmartLifecycle.stop()`: (1) stop claiming; (2) wait up to `forgeci.worker.drain-timeout` (60 s) for running jobs; (3) remaining → `cancel(job, SHUTDOWN)` → jobs go back to the queue as attempt+1 (infra-style, since the user did nothing wrong); (4) heartbeat key deleted so the reaper does not wait for TTL. API: `server.shutdown=graceful`, complete SSE emitters with a `status: reconnect` event so browsers resume elsewhere.

- **Interview angle:** "What happens to running builds when you deploy a new worker version?" — this section is the answer; measure it (time from `SIGTERM` to exit, jobs requeued vs completed).

## 5. Resources

- Redis docs: *Keyspace/TTL*, `SET NX PX`, Lua scripting (`EVAL`), *Distributed locks* (the Redlock discussion — read critically) — https://redis.io/docs/latest/
- Martin Kleppmann, *How to do distributed locking* (fencing tokens) — https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html
- AWS Architecture Blog, *Exponential Backoff And Jitter* — https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
- Spring Boot docs: *Graceful shutdown*, *Kubernetes probes / health groups* — https://docs.spring.io/spring-boot/reference/
- Docker docs: `docker stop`/`kill`, Compose `stop_grace_period`, *Container exit codes* — https://docs.docker.com/
- GNU coreutils `timeout` manual — https://www.gnu.org/software/coreutils/manual/html_node/timeout-invocation.html
- [`OA_PREP.md`](../../OA_PREP.md) — read fully before Saturday; NeetCode 150 — Greedy, 2-D DP; problems in §7

## 6. Exercises and assignments

### 6.1 Warm-up exercises (~2.5 h)

1. **Kill sequence lab (30 min).** In a container run `sh -c 'trap "" TERM; sleep 600'`; `docker stop -t 5` → takes 5 s then exit 137; without the trap → exit 143 immediately. Then the same via `docker-java`.
2. **Lease expiry lab (20 min).** `SET lease 1 PX 3000`; renew every 1 s from a loop; kill the loop; `TTL lease` counts down to `-2`. Compute: with TTL 60 s and renewal 20 s, what is the worst-case detection delay for a dead worker? (≈ 60 s + reaper period.)
3. **Semaphore Lua (30 min).** Type §4.5's script; `EVALSHA` it from `redis-cli` with limit 2 from three terminals; force one holder to "crash" (no `ZREM`); advance `now` past TTL; the slot frees.
4. **Backoff table (15 min).** For base 5 s, cap 5 min, attempts 1..5, compute min/max delay with full jitter; put it in the ADR.
5. **Retry policy test-first (30 min, Java).** Write `RetryPolicyTest` covering every row of §4.1 before writing `RetryPolicy`.
6. **2-D DP table pitfall (15 min, Python).** `grid = [[0]*3]*3; grid[0][0] = 1; print(grid)` → all rows change. Fix with a comprehension. Then LCS table by hand for `"abcde"`/`"ace"`.

### 6.2 Assignment — ForgeCI M4

**Acceptance criteria:**

- [ ] `timeout` in `.forgeci.yml` (per job, default 600 s, max 3600); inner `timeout --signal=TERM --kill-after=10s` per step + outer watchdog stop/kill; `TIMED_OUT` status + final system chunk; cleanup verified.
- [ ] `POST /jobs/{id}/cancel`: queued → conditional UPDATE + `LREM`, worker re-checks after claim; running → flag + control channel → kill → `CANCELLED`; UI cancel button with optimistic state + reconcile.
- [ ] `RetryPolicy` implementing §4.1 exactly, with backoff + jitter, `max_attempts=3`; `POST /jobs/{id}/retry` for manual new attempts; results fenced by `attempt`.
- [ ] Heartbeats (`EX 30`, every 10 s) and lease renewal (`PX 60000`, every 20 s); `GET /workers` shows live workers with last heartbeat and running jobs.
- [ ] Reaper (every 30 s, single-runner lock): expired lease → requeue/`INFRA_ERROR` per policy, system chunk written, `cancel_requested` honoured; zombie worker's late result rejected (fencing test).
- [ ] Per-project limit (`forgeci.limits.per-project`, default 2) via the Lua sorted-set semaphore with TTL safety; never exceeded under load (test); released on completion, timeout, cancel and crash (via TTL).
- [ ] Graceful shutdown for worker (drain then requeue) and API (SSE `reconnect` event); Compose `stop_grace_period` set; shutdown timings measured and recorded.
- [ ] Health: Actuator liveness/readiness groups; custom indicators for Redis and Docker daemon on the worker; readiness false while draining.

### 6.3 Break it

- Disable the outer watchdog and run a step with `trap "" TERM` and an inner `timeout` — the inner `--kill-after` still works. Now also remove `--kill-after`: the job runs forever. Restore both layers; keep the test.
- Make the reaper requeue without checking `cancel_requested`; cancel a running job, kill its worker; watch the job resurrect. Restore.
- Set lease TTL 60 s but renewal every 70 s: watch healthy jobs get "reaped" and duplicated. Restore ⅓ rule; add a config validation that rejects renewal ≥ TTL/2.
- Remove the `attempt` fence; simulate a zombie worker writing results for attempt 1 after attempt 2 succeeded; the build flips to `FAILED`. Restore.

### 6.4 Debug it

- Cancelled queued jobs still run sometimes: the worker's post-claim status re-check reads a stale value from JPA's first-level cache or runs outside a transaction. Use a direct `SELECT status ... FOR SHARE`/fresh `EntityManager.refresh`, then add the race test with a latch.
- The reaper runs on both API instances and double-requeues: the lock `SET NX PX` TTL (25 s) is shorter than a slow reaper run. Measure the run, set TTL > worst case, and make requeue idempotent (`UPDATE ... WHERE status='RUNNING' AND attempt=:a RETURNING`).
- Project limit "leaks": jobs finish but `ZCARD` stays high — release runs in a `finally` that is skipped when the cancel path throws before it. Restructure: acquire → try → finally release, and add a TTL-expiry test as the backstop.

## 7. DSA — Greedy + 2-D DP (8 new problems, in Python)

**Language: Python (Track A).** Guides: [`03-dsa/20-greedy.md`](../../03-dsa/20-greedy.md) · [`03-dsa/21-dp-2d.md`](../../03-dsa/21-dp-2d.md) · Templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md). Greedy: state the exchange argument out loud ("swapping any non-greedy choice for the greedy one never makes it worse") — if you cannot, it is probably DP. 2-D DP: `dp = [[0]*(n+1) for _ in range(m+1)]`, iterate in dependency order, reduce to two rows when only `i-1` is used.

| # | Problem | Difficulty | Time limit | Day |
|---|---|---|---|---|
| 53 | Maximum Subarray | Medium | 15 min (Kadane; then say the DP view) | Mon |
| 55 | Jump Game | Medium | 20 min | Mon |
| 45 | Jump Game II | Medium | 25 min | Tue |
| 134 | Gas Station | Medium | 25 min | Tue |
| 846 | Hand of Straights | Medium | 25 min | Wed |
| 62 | Unique Paths | Medium | 15 min | Wed |
| 1143 | Longest Common Subsequence | Medium | 25 min | Thu |
| 309 | Best Time to Buy and Sell Stock with Cooldown | Medium | 30 min | Thu |

Reviews due: Day-3 Week 16 Thu/Fri; Day-7 Week 16 DP/intervals; Day-14 Week 15 DP; Day-30 Week 13 graphs.

**Java rep (Fri, ≤ 30 min):** #1143 LCS in Java with `int[][] dp` and `char` comparison via `charAt` — Week 18's rep re-does it space-optimised, so do the plain table now.

## 8. Project work — ForgeCI M4 (Timeouts, cancellation, retries, limits, health)

Spec: [`18-projects/forgeci/README.md`](../../18-projects/forgeci/README.md) · [`milestones.md`](../../18-projects/forgeci/milestones.md) (M4) · [`failure-engineering.md`](../../18-projects/forgeci/failure-engineering.md)

### 8.1 Task checklist

- [ ] **Mon:** milestone `M4 — Timeouts, cancellation, retries, limits, health` + issues; `RetryPolicy` test-first + backoff; ADR `0005-retry-policy.md`; migration `V5__cancel_timeout_attempts.sql` (`cancel_requested`, `timeout_seconds`, `cancel_reason`, `attempt` fences).
- [ ] **Tue:** timeouts: config field, inner `timeout` wrapper, outer watchdog, `TIMED_OUT`, system chunk; tests with real Docker (`sleep 600` + `trap`).
- [ ] **Wed:** cancellation queued + running (control channel + poll fallback), worker post-claim re-check, UI cancel button; race tests.
- [ ] **Thu:** heartbeats, lease renewal, `GET /workers`; reaper with lock, fencing on result writes, `cancel_requested` handling; kill-the-worker test.
- [ ] **Fri:** per-project semaphore (Lua, TTL) + never-exceeded test; release paths; health groups + indicators.
- [ ] **Sat (extended, +2 h):** graceful shutdown worker + API with measured timings; failure scenarios (§8.4); `docs/OPERATIONS.md` (statuses, retry table, timeouts, limits, shutdown behaviour); PR review; tag `m4`.

### 8.2 Acceptance summary

M4 done = §6.2 green + `docs/OPERATIONS.md` + every row of the failure taxonomy has a test that produces exactly that status + tag `m4`. Week 18 (M5) then runs the full failure-engineering suite and measures; v1.0 follows.

### 8.3 Verification tests you write

| Test | Type | Proves |
|---|---|---|
| `RetryPolicyTest` | unit, exhaustive | every (outcome, attempt) → expected decision; jitter within bounds; never retries exit ≠ 0 |
| `TimeoutKillsContainerIT` | real Docker | `sleep 600` with timeout 5 s → `TIMED_OUT` in < 20 s, container gone, system chunk present |
| `TimeoutSurvivesTrapIT` | real Docker | step ignoring TERM still killed by outer watchdog |
| `CancelQueuedIT` | Testcontainers | queued job cancelled → not in Redis, `CANCELLED`, never executed |
| `CancelQueuedRaceIT` | latch: claim between UPDATE and LREM | worker re-check prevents execution |
| `CancelRunningIT` | real Docker | running `sleep 600` cancelled → container gone in < 15 s, `CANCELLED`, final chunk |
| `HeartbeatAndLeaseRenewalIT` | Testcontainers Redis | keys refreshed on schedule; renewal < TTL/2 enforced by config validation |
| `ReaperRequeuesOrphanIT` | kill worker process/thread | expired lease → attempt 2 queued (`INFRA_ERROR` after N), system chunk explains |
| `ReaperHonoursCancelIT` | | expired lease + `cancel_requested` → `CANCELLED`, no requeue |
| `ZombieResultRejectedIT` | | late write for attempt 1 after attempt 2 → rejected, status unchanged |
| `ReaperSingleRunnerIT` | 2 API instances | only one reaper run per period |
| `PerProjectLimitNeverExceededIT` | 20 jobs, limit 2, 3 workers | max concurrent running per project == 2 at all sampled instants; all jobs complete |
| `LimitReleasedOnCrashIT` | holder crashes | slot frees after TTL |
| `GracefulShutdownDrainsIT` | worker `stop()` | short job completes; long job requeued as attempt+1 with `SHUTDOWN` reason; heartbeat key removed |
| `ReadinessFalseWhileDrainingIT` | Actuator | `/actuator/health/readiness` → `OUT_OF_SERVICE` during drain |

### 8.4 Failure-engineering scenarios this week

M4 set: worker `docker kill`ed mid-step (orphan recovery, measured detection delay); worker partitioned from Redis (`docker network disconnect`) but container still running — observe duplicate execution risk, fencing, self-fencing decision; Redis down for 60 s — do heartbeats/leases cause a mass reap on return? (they should not: reaper must skip when Redis was unreachable — design it); cancel storm (cancel 50 queued jobs at once); deploy a new worker version under load (graceful shutdown) — count completed vs requeued. Write-ups in `docs/FAILURES.md`.

### 8.5 GitHub expectations

Milestone `M4`; PRs `feat/m4-retry-policy`, `feat/m4-timeouts`, `feat/m4-cancel`, `feat/m4-heartbeats-reaper`, `feat/m4-project-limits`, `feat/m4-graceful-shutdown-health`, `docs/m4-operations`. Tag `m4`.

## 9. Git activity

- Practise `git bisect run mvn -q -pl worker test -Dtest=RetryPolicyTest` on a planted regression (20 min) — automated bisect is an interview-worthy trick.
- Keep failure write-ups linked from PRs; reference issue numbers in commit trailers (`Refs: #42`).

## 10. Interview preparation

Two tracks, never mixed (ROADMAP §10): **Track A = coding interview in Python**; **Track B = software-engineering / résumé interview in Java, Spring, SQL, Docker, Redis…** OA simulations mix both by design: algorithm problems in Python, the "existing codebase / failing tests" task in the Java buggy-library.

**Track A (Python)**
- **OA simulation #1 (Sat, 90–120 min, strict):** per [`OA_PREP.md`](../../OA_PREP.md) — a HackerRank/CodeSignal-style environment (plain editor, no autocomplete, stdin/stdout parsing as in the cheatsheet §2.1), **two timed problems in Python** (one Medium from Weeks 13–17 patterns, one unseen Medium from a weaker pattern), then **20 minutes on the Java `buggy-library`** ([`21-debugging-code-reading/exercises/buggy-library/`](../../21-debugging-code-reading/exercises/buggy-library/), drill #2 from [`21-debugging-code-reading/drills.md`](../../21-debugging-code-reading/drills.md)): make two failing tests pass. Score each part against [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md) and the OA rubric; log time-to-first-passing-test and bugs found in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Weekly mock #5 (Tue, 45 min, Python):** one unseen greedy/DP Medium under protocol ([`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md)).

**Track B (Java / projects)**
- **Engineering mock slice (Sat, 15 min, after the OA):** "A worker dies mid-build. Walk me through detection, recovery and how you prevent duplicate results." — from §4.4 and your reaper tests.
- **Résumé-defense drill — Redis, Docker, Linux (third pass, now with M4 evidence):** [`17-resume-tech-defense/redis.md`](../../17-resume-tech-defense/redis.md), [`docker.md`](../../17-resume-tech-defense/docker.md), [`linux.md`](../../17-resume-tech-defense/linux.md). Required: Lua atomicity, TTL as crash safety, `docker stop` vs `kill`, exit codes, `SIGTERM` handling in the JVM.
- **Story bank:** add the "cancelled job resurrected by the reaper" debugging story ([`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md)).
- **Applications (Sun):** with OA sim #1 done you are at the **OA-ready** stage ([`JOB_READINESS.md`](../../JOB_READINESS.md)); raise volume where roles fit (internship + new-grad SDE), and treat every real OA as a data point to log.

## 11. Revision work

- Re-derive LedgerX's scheduled-payment claim + idempotent execution and map it to ForgeCI's lease + `attempt` fence — same problem, two systems; be able to say so in a deep dive.
- Flashcards: full/half jitter formulas, ⅓ renewal rule, fencing token, `docker stop -t`, `stop_grace_period`, `SmartLifecycle` phases, `server.shutdown=graceful`, `ZREMRANGEBYSCORE`.
- Python pitfall of the week (OA-relevant): reading input — `sys.stdin.readline()` keeps the trailing `\n`; `input().split()` for tokens; `int(x)` per token; print with `'\n'.join(...)` once.

## 12. Daily plan

| Day | Learning | Project | DSA | Interview / other |
|---|---|---|---|---|
| **Mon** (8h) | Failure taxonomy + retry design; labs 4–5 (2h) | Milestone, `RetryPolicy` + tests, ADR 0005, migration V5 (4.5h) | Greedy §1–5, #53, #55 (1.5h) | — |
| **Tue** (8h) | — | Timeouts: config, inner `timeout`, watchdog, `TIMED_OUT`, Docker tests (5h) | #45, #134 + Day-3 reviews (2h) | Mock #5 (Python) (1h) |
| **Wed** (8h) | Leases/heartbeats/fencing; semaphore Lua lab (2h) | Cancellation queued + running, re-check, UI button, race tests (4.5h) | #846, 2-D DP §1–5, #62 (1.5h) | — |
| **Thu** (8h) | — | Heartbeats, lease renewal, `/workers`, reaper + lock + fencing, kill-worker test (5h) | #1143, #309 + Day-7 reviews (2h) | `docs/OPERATIONS.md` (1h) |
| **Fri** (5h) | Graceful shutdown + health groups (1h) | Per-project semaphore + tests; health indicators (2h) | Reviews + Java rep #1143 (1h) | Retro prep; read `OA_PREP.md` rules (1h) |
| **Sat** (8h) | — | Graceful shutdown (worker + API) with timings, failure scenarios, PR review, tag `m4` (5h) | — | **OA sim #1** (Python ×2 + Java buggy-library) + engineering slice (3h) |
| **Sun** (2–3h) | — | — | Day-14/30 reviews | End-of-week test, trackers, plan Week 18, applications |

## 13. End-of-week test (Sunday, 75 min)

**Part A — DSA (30 min, Python).** LeetCode **1130. Minimum Cost Tree From Leaf Values** (greedy with a stack, or the DP) — or, if that is too far, **518. Coin Change II** (2-D → 1-D DP) in ≤ 25 min.

**Part B — Concepts (20 min).**

1. A step exits 1. A container fails to start. A worker vanishes. Give the status and retry decision for each.
   <details><summary>Answer</summary>`FAILED`, no retry (app failure). `INFRA_ERROR` path: requeue with backoff up to N (infra). Worker lost: reaper requeues attempt+1 with backoff up to N, then `INFRA_ERROR`.</details>
2. Why renew a lease at one-third of its TTL rather than just before expiry?
   <details><summary>Answer</summary>Tolerates missed renewals (GC pause, Redis hiccup, scheduling delay) without a false expiry; two consecutive misses are still safe. Renewal ≥ TTL/2 makes healthy workers look dead.</details>
3. What is a fencing token and what plays that role in ForgeCI?
   <details><summary>Answer</summary>A monotonically increasing number handed out with each lease grant; writers include it and storage rejects stale ones. ForgeCI: `attempt` — result writes are `WHERE attempt = :mine`.</details>
4. Cancel arrives for a queued job; a worker claims it a millisecond later. How is execution prevented?
   <details><summary>Answer</summary>The conditional UPDATE to `CANCELLED` is the source of truth; the worker re-reads status after claim and acks without running. `LREM` is an optimisation that may lose the race.</details>
5. Why keep the semaphore in a sorted set with timestamps instead of a plain counter?
   <details><summary>Answer</summary>A counter cannot expire individual holders; a crashed worker would leak a slot forever. Timestamped members can be dropped after TTL (`ZREMRANGEBYSCORE`) and refreshed with the lease.</details>
6. On `SIGTERM`, what should a worker do with a job that will not finish within the drain timeout?
   <details><summary>Answer</summary>Cancel it with reason `SHUTDOWN`, kill its container, requeue as attempt+1 (infra-style — the user did nothing wrong), delete its heartbeat key, exit. Never leave it `RUNNING` for the reaper to guess.</details>

**Part C — Practical (20 min).** Write from memory: the Lua semaphore acquire, and the `RetryPolicy.decide` decision table as a Java `switch` over a sealed `Outcome`.

**Part D — Explain (5 min, Track B).** "Deploying a new worker version while 10 builds are running — what happens, and what did you measure?"

Pass: A in time · B ≥ 5/6 · C correct · D fluent with numbers.

## 14. Mastery checklist

- [ ] I can recite the failure taxonomy table and point to the test for each row.
- [ ] Timeouts kill for real, in two layers, and I can explain why both.
- [ ] Cancellation is correct for queued and running jobs including the claim race.
- [ ] I can explain leases, heartbeats, the reaper, fencing and self-fencing, with detection-delay numbers.
- [ ] Per-project limits are atomic, crash-safe and proven never exceeded.
- [ ] Graceful shutdown is implemented and measured for worker and API.
- [ ] 8 greedy/2-D DP problems in Python with exchange arguments spoken; Java rep done; reviews done.
- [ ] OA simulation #1 completed under strict conditions and scored; mock #5 done.

## 15. Expected deliverables

- `forgeci`: tag `m4`; ADR 0005; `docs/OPERATIONS.md`; `docs/FAILURES.md` (M4 set with measured detection/shutdown timings); UI cancel button.
- Trackers: [`project-tracker.md`](../../trackers/project-tracker.md), [`dsa-tracker.md`](../../trackers/dsa-tracker.md), [`technology-tracker.md`](../../trackers/technology-tracker.md) (Redis Lua/TTL patterns, Docker lifecycle, Linux signals → "can defend with evidence"), [`interview-tracker.md`](../../trackers/interview-tracker.md) (OA sim #1 score + timings, mock #5), [`weekly-progress.md`](../../trackers/weekly-progress.md).

## 16. If behind / stretch

**Behind?** Cut order: health groups/indicators (basic `/health` is enough for now) → `GET /workers` view (keep the keys) → control-channel fast path (2 s polling only) → #846/#309. **Never cut** the retry policy + tests, timeouts, orphan recovery with fencing, or the per-project-limit test — M5's failure suite and v1.0 depend on them, and ROADMAP §8 says cut features, never quality.

**Ahead?** Implement per-project queues so limited projects do not cause requeue churn (the ADR alternative); add `retry_on_infra_failure: false` as a config option; add a `SIGTERM` self-fencing behaviour (worker kills its own containers when heartbeats fail for > TTL); solve LeetCode 72 (Edit Distance) in Python as a Week 18 preview.
