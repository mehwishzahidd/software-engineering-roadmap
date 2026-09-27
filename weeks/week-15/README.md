# Week 15 — ForgeCI M2: job queue and Docker-executing workers → MVP

[← Week 14](../week-14/) · [Roadmap](../../ROADMAP.md) · [Week 16 →](../week-16/)

**Phase 3 · ForgeCI (Weeks 14–19)** — builds start to *run*. A reliable Redis queue, a separate worker application, and step execution inside temporary Docker containers with guaranteed cleanup. This is the **MVP**. Learning goes deep on **Java concurrency** (the worker is a thread-pool and lifecycle problem) plus Redis queue semantics and networking fundamentals.

| Block | Hours | Notes |
|---|---:|---|
| Project (ForgeCI M2) | 32 | Redis queue (BLMOVE + lease, or Streams — decide), worker app, Docker executor via Engine API, workspace volume, clone, steps, exit codes, cleanup-always |
| Learning | 7 | Java concurrency deep (executors, `CompletableFuture`, locks, atomics, virtual threads), producer/consumer, reliable Redis queues, leases, networking fundamentals |
| DSA (Python) | 6 | 1-D DP — 8 new + reviews + 1 Java rep |
| Interview / review | 3 | Weekly mock #3 (Track A Python + Track B slice), drill, retro |

---

## 1. Main objective

By Sunday:

- `api` enqueues each job of a `QUEUED` build onto a **Redis queue**; `worker` (a separate Spring Boot app, N instances) pulls jobs with **at-least-once** semantics and a **lease** so a crashed worker's job can be recovered later (recovery itself is M4);
- for each job the worker creates a **temporary container** from the config's image, mounts a **workspace volume**, **clones** the repo at the commit, runs each step with `sh -c`, captures **exit codes** (stop at first failure), persists results, and **always** removes container + volume — including when the worker thread is interrupted or the step throws;
- a push to a real repo with a passing `.forgeci.yml` ends `SUCCESS`; a failing step ends `FAILED` with the right exit code; 20 jobs across 3 workers are each executed **exactly once** (test).

## 2. Prerequisites

- Tag `m1`; real push → `QUEUED` build works. If the config parser slipped, finish it Monday morning (2 h cap).
- Week 14 labs done: you can explain exit codes, `SIGTERM`/`SIGKILL`, layers, volumes, and you ran the `docker-java` spike.
- Java memory model + threads basics ([Week 5](../week-05/)) — this week goes deep.
- Recursion and memoisation in Python are comfortable: 1-D DP is recursion with a cache, then a table.

## 3. Learning topics

| Topic | Subtopics | File |
|---|---|---|
| Java concurrency deep | `ExecutorService` types, bounded queues + rejection policies, `Future`/`CompletableFuture` composition, `synchronized` vs `ReentrantLock`, `Condition`, `AtomicInteger`/`LongAdder`, `ConcurrentHashMap`, `volatile`, thread interruption (the contract), `ThreadLocal`, **virtual threads (Java 21)** and when they help, `ScheduledExecutorService`, graceful `shutdown`/`awaitTermination` | [`01-java/07-concurrency.md`](../../01-java/07-concurrency.md), [`01-java/06-memory-jvm.md`](../../01-java/06-memory-jvm.md) |
| Concurrency theory | producer/consumer, bounded buffers, liveness (deadlock, livelock, starvation), happens-before, backpressure | [`14-cs-fundamentals/concurrency.md`](../../14-cs-fundamentals/concurrency.md) |
| Reliable Redis queues | `LPUSH`/`BLMOVE` to a per-worker processing list, leases via `SET key PX`, ack = `LREM`, vs **Streams** (`XADD`/`XREADGROUP`/`XACK`/`XPENDING`/`XAUTOCLAIM`); at-least-once vs at-most-once; visibility timeout; poison messages; Redis persistence (RDB/AOF) | [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md), [`15-system-design/scalability.md`](../../15-system-design/scalability.md) |
| Networking fundamentals | TCP handshake, sockets, ports, DNS in Docker networks, timeouts (connect/read), keep-alive, Unix domain sockets (Docker socket), TLS basics | [`14-cs-fundamentals/networking.md`](../../14-cs-fundamentals/networking.md) |
| Docker execution from Java | `docker-java`: pull, create (cmd, env, binds, resources), start, `wait`, logs (M3), remove; `HostConfig` memory/CPU limits; `--init`; workspace volume lifecycle | [`11-docker/README.md`](../../11-docker/README.md), [`11-docker/exercises.md`](../../11-docker/exercises.md) |
| Spring Boot for a worker app | `ApplicationRunner`/`SmartLifecycle`, `@PreDestroy`, `spring.lifecycle.timeout-per-shutdown-phase`, Actuator health with custom indicators | [`05-spring-boot/01-core-di.md`](../../05-spring-boot/01-core-di.md), [`05-spring-boot/06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md) |

## 4. Concepts to learn

### 4.1 The reliable-queue decision (ADR 0003)

Two valid designs — **pick one and justify**:

**A. Lists with `BLMOVE` + lease.** Producer `LPUSH forgeci:jobs <jobId>`. Worker `BLMOVE forgeci:jobs forgeci:processing:<workerId> RIGHT LEFT 5` (atomic move, blocks up to 5 s). On claim, `SET forgeci:lease:<jobId> <workerId> PX 60000`, renew every 20 s while running (heartbeat = M4). On completion `LREM forgeci:processing:<workerId> 1 <jobId>` + `DEL lease`. Recovery (M4): a reaper scans processing lists for ids with no lease → requeue. Simple, explicit, every step is a primitive you can explain.

**B. Streams with consumer groups.** `XADD forgeci:jobs * jobId <id>`; `XGROUP CREATE`; workers `XREADGROUP GROUP workers <workerId> COUNT 1 BLOCK 5000 STREAMS forgeci:jobs >`; `XACK` on completion; `XPENDING`/`XAUTOCLAIM` with `min-idle-time` recovers stale entries. Built-in pending tracking, per-message delivery count (poison-message detection for free), but more concepts.

The MVP needs at-least-once + a lease + a recoverable claim; both give it. Choose A if you want maximum explainability in interviews ("I built it from four commands"); choose B if you want delivery counts and idle-claim built in. Whichever you choose, write the **other's** sketch in the ADR as the rejected alternative, with the concrete reason.

```java
// Design A: claim loop (worker thread) — only the queue part; execution is §4.3
String jobId = redis.execute((RedisCallback<String>) c ->
    c.listCommands().bLMove(QUEUE, processingKey, Direction.RIGHT, Direction.LEFT, 5.0));
if (jobId == null) return;                                    // timed out, loop again
Boolean leased = redis.opsForValue().setIfAbsent(leaseKey(jobId), workerId, Duration.ofSeconds(60));
```

- **Interview angle (Track B):** "Why not just `RPOP`?" → a crash after pop loses the job; `BLMOVE` keeps it in a processing list until acked. "Exactly-once?" → no such thing end-to-end; at-least-once + idempotent execution (a job re-run overwrites its own results, attempt number increments).
- **Where ForgeCI uses this:** M2 claim/ack; M4 heartbeats, lease expiry and orphan recovery; M5 measures queue wait time; M6 queues DAG-ready jobs.

### 4.2 Worker concurrency model

Each worker instance runs `forgeci.worker.slots` (e.g. 2) executor threads, each looping: claim → execute → ack. A **bounded** model: a worker never claims more than it can run. Use a fixed `ExecutorService` for the slots, an extra `ScheduledExecutorService` for lease renewal (M4) and heartbeats, and **virtual threads** only for the I/O-bound log-streaming reader (M3) — document why not everywhere (a slot is a long-running loop; platform threads are fine and simpler to reason about for N ≤ 8).

Interruption contract: `shutdown()` sets a `volatile boolean running=false` and interrupts slot threads; a blocking `BLMOVE` returns on timeout; a running step must **finish or be cancelled** (M4) before the JVM exits — Spring's `SmartLifecycle.stop()` with `timeout-per-shutdown-phase=60s` gives the window.

```java
// slot loop skeleton — the shape, not the implementation
while (running && !Thread.currentThread().isInterrupted()) {
    Optional<Claim> claim = queue.claim(Duration.ofSeconds(5));   // BLMOVE / XREADGROUP
    if (claim.isEmpty()) continue;
    try { executor.execute(claim.get()); queue.ack(claim.get()); }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); }  // preserve the flag; M4 handles the job
    catch (Exception e) { results.markInfraFailure(claim.get(), e); }       // leave unacked or requeue (ADR)
}
```

- **Interview angle:** "What happens when you catch `InterruptedException`?" → restore the flag or propagate; swallowing it breaks shutdown. "`synchronized` vs `ReentrantLock`?" → tryLock/timeouts/fairness/conditions. "When are virtual threads useful?" → many blocked I/O tasks, not CPU-bound or long loops.

### 4.3 Container execution — cleanup always

Sequence per job: ensure image (`pullImageCmd` if missing; cache) → create volume `forgeci-ws-<jobId>` → create container (`image`, `Cmd: ["sh","-c","sleep infinity"]` or run one container per step — decide: **one container per job** with `docker exec` per step keeps the workspace warm and matches real CI; document) → start → `git clone --depth 1` at SHA inside → for each step `execCreate`/`execStart` with `sh -c`, wait, read exit code → stop at first non-zero → persist → **finally**: `stop` (t=10 s) → `removeContainerCmd(force)` → `removeVolumeCmd`.

```java
String containerId = null; String volume = null;
try {
    volume = docker.createVolumeCmd().withName("forgeci-ws-" + jobId).exec().getName();
    containerId = docker.createContainerCmd(image)
        .withHostConfig(HostConfig.newHostConfig()
            .withBinds(new Bind(volume, new Volume("/workspace")))
            .withMemory(2L * 1024 * 1024 * 1024).withNanoCPUs(1_000_000_000L).withInit(true))
        .withWorkingDir("/workspace").withCmd("sh", "-c", "sleep infinity").exec().getId();
    docker.startContainerCmd(containerId).exec();
    // clone + steps via execCreate/execStart …
} finally {
    if (containerId != null) try { docker.removeContainerCmd(containerId).withForce(true).exec(); } catch (Exception e) { log.warn(...); }
    if (volume != null)      try { docker.removeVolumeCmd(volume).exec(); } catch (Exception e) { log.warn(...); }
}
```

Failure taxonomy starts here (fully in M4): **app failure** = step exit ≠ 0 → `FAILED`, never retried; **infra failure** = image pull error, container start error, Docker daemon unreachable → `INFRA_ERROR` (retry policy in M4).

- **Interview angle:** "How do you guarantee cleanup?" → `finally` + force remove + a periodic sweeper for `forgeci-*` leftovers (label your containers: `forgeci.job=<id>`) because `finally` does not run on `kill -9`.
- **Where ForgeCI uses this:** every job; M4 timeouts kill the same container; M5's Compose runs N workers each with the Docker socket mounted (security notes in M6).

### 4.4 Networking you must be able to draw

Worker → Docker daemon over the Unix socket (`/var/run/docker.sock`, bind-mounted into the worker container in Compose — note the security implication). Worker → Redis over TCP (`redis:6379` by Compose DNS). Job container → GitHub over HTTPS (needs outbound network; consider `--network none` + a clone done by the *worker* into the volume as a hardening option — record in ADR). Timeouts everywhere: Docker client connect/read, Redis command timeout, `git clone` timeout.

### 4.5 Idempotent execution and `attempt`

A job may be executed twice (worker crash after execute, before ack). Make execution **idempotent at the record level**: results are written by `(job_id, attempt)`; a re-execution increments `attempt`, and the build's view shows the latest attempt. Steps' results from a superseded attempt remain (history) but are marked. This is the same lesson as LedgerX: at-least-once delivery + idempotent effect.

### 4.6 Redis persistence and what "reliable" means

Redis is in-memory; with AOF `everysec` you can lose ≤ 1 s of enqueues on a crash, with RDB much more. Decide: Postgres is the source of truth for jobs (`status = QUEUED`); Redis is the *dispatch* mechanism. A **re-enqueue reconciler** (M4/M5) requeues `QUEUED` jobs missing from Redis. Say this in interviews — it is the difference between "I used Redis as a queue" and "I know what Redis as a queue can lose".

## 5. Resources

- Redis docs: `BLMOVE`, `LREM`, `SET` (NX/PX), *Redis Streams* intro, `XREADGROUP`, `XAUTOCLAIM`, *Persistence* — https://redis.io/docs/latest/
- Java 21 docs: `java.util.concurrent` package, JEP 444 *Virtual Threads* — https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html , https://openjdk.org/jeps/444
- *Java Concurrency in Practice* (Goetz) — ch. 5–8, 13–14 (reference; read the parts you use)
- docker-java wiki/examples (`ExecCreateCmd`, `HostConfig`) — https://github.com/docker-java/docker-java
- Docker docs: *Runtime options with memory, CPUs* — https://docs.docker.com/engine/containers/resource_constraints/
- Spring Boot docs: *Graceful shutdown*, *Task Execution* — https://docs.spring.io/spring-boot/reference/
- NeetCode 150 — 1-D DP; problems in §7

## 6. Exercises and assignments

### 6.1 Warm-up exercises (~3 h)

1. **Producer/consumer from scratch (45 min, Java).** `ArrayBlockingQueue<Job>(10)`, 2 producers, 3 consumers, poison-pill shutdown; then swap to a `ReentrantLock` + two `Condition`s implementation; then an `ExecutorService` version. Note the differences in shutdown behaviour.
2. **Interruption lab (20 min).** A task that swallows `InterruptedException`; call `shutdownNow()`; observe the pool never terminates. Fix by restoring the flag.
3. **Redis queue in `redis-cli` (30 min).** Two terminals as workers running `BLMOVE`; one producer `LPUSH`; kill a "worker" mid-processing; observe its processing list still holds the id; requeue by hand with `LMOVE`. Repeat with Streams: `XADD`, `XREADGROUP`, `XPENDING`, `XAUTOCLAIM`.
4. **Exec lab (30 min).** From a scratch Java test with `docker-java`: create container `alpine`, `exec` `sh -c "exit 3"`, read exit code 3, remove. Then `exec` `sh -c "sleep 100"` and force-remove while running — confirm no leftover with `docker ps -a`.
5. **Virtual threads micro-bench (25 min).** 10,000 tasks each sleeping 50 ms on a fixed pool of 8 vs `Executors.newVirtualThreadPerTaskExecutor()`; time both; write one paragraph on when the difference matters (I/O-bound blocking, not CPU).
6. **1-D DP warm-up (20 min, Python).** Fibonacci three ways: naive, `@lru_cache`, bottom-up with two variables — and explain why the middle one can hit the recursion limit at n = 5,000.

### 6.2 Assignment — ForgeCI M2

**Acceptance criteria:**

- [ ] ADR `0003-queue-design.md`: chosen design (BLMOVE+lease or Streams) with rejected alternative and reasons; Postgres as source of truth, Redis as dispatch.
- [ ] `api`: on build creation, jobs → `QUEUED` in Postgres **and** enqueued in Redis (order: commit DB first, then enqueue; a reconciler covers the gap — document).
- [ ] `worker` app: configurable slots; claim loop; lease set on claim (renewal is M4); ack on completion; interruption handled correctly; graceful shutdown finishes the running step (up to the timeout) before exit.
- [ ] Executor: image pull (cached), workspace volume, one container per job with resource limits and `--init`, clone at SHA (`--depth 1`), steps via `exec` with `sh -c`, exit codes captured, stop at first failure, `SUCCESS`/`FAILED`/`INFRA_ERROR`, `attempt` tracking; **cleanup in `finally`** plus a sweeper for labelled leftovers.
- [ ] Results persisted per `(job, attempt)`; build status derived from its jobs (all `SUCCESS` → `SUCCESS`; any `FAILED` → `FAILED`; any running → `RUNNING`).
- [ ] Compose: `api`, `worker` (×2 via `--scale`), `postgres`, `redis`; worker has the Docker socket mounted (documented as a known risk until M6).
- [ ] Tests (§8.3) green in CI, including exactly-once execution across 3 workers.
- [ ] A real push with a passing config → `SUCCESS`; with a failing step → `FAILED` with exit code shown in `GET /builds/{id}`.

### 6.3 Break it

- Replace `BLMOVE` with `RPOP`; kill the worker after claim; the job is gone forever. Restore; watch it sit in the processing list (recoverable — M4).
- Remove `finally`; throw in the clone step; `docker ps -a` fills with `forgeci-*` containers. Restore; add the leftover-sweeper test.
- Set worker memory limit to 64 MB and run `mvn verify` in the job: exit code 137. Record how the executor reports OOM (it must say so, via `docker inspect` `OOMKilled`, not just "failed").
- Swallow `InterruptedException` in the slot loop; `docker compose stop worker` hangs until Compose's kill timeout. Restore.

### 6.4 Debug it

- Jobs are claimed but never start: the worker logs `connect ENOENT /var/run/docker.sock` — socket not mounted or wrong group. Check `ls -l /var/run/docker.sock` inside the container, and the worker image's user.
- Two workers both executed the same job (attempt 1 twice): your ack ran before execute, or the lease `setIfAbsent` result was ignored. Add the exactly-once test first, then fix.
- Clone fails only in the job container, not on the host: DNS inside the job container (Compose network vs default bridge), or a private repo needing the token in the clone URL — never bake the token into the image or logs (mask it in step output).

## 7. DSA — 1-D Dynamic Programming (8 new problems, in Python)

**Language: Python (Track A).** Guide: [`03-dsa/18-dp-1d.md`](../../03-dsa/18-dp-1d.md) · Templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md). Method for every problem: (1) define the state in one sentence, (2) write the recurrence, (3) top-down with `functools.lru_cache(maxsize=None)`, (4) convert to bottom-up table, (5) reduce space if only the last k states matter. Say all five out loud.

| # | Problem | Difficulty | Time limit | Day |
|---|---|---|---|---|
| 70 | Climbing Stairs | Easy | 10 min | Mon |
| 746 | Min Cost Climbing Stairs | Easy | 15 min | Mon |
| 198 | House Robber | Medium | 20 min | Tue |
| 213 | House Robber II | Medium | 20 min | Tue |
| 5 | Longest Palindromic Substring | Medium | 30 min (expand-around-centre, then note the DP) | Wed |
| 647 | Palindromic Substrings | Medium | 20 min | Wed |
| 91 | Decode Ways | Medium | 30 min | Thu |
| 322 | Coin Change | Medium | 25 min | Thu |

Reviews due: Day-3 Week 14 Thu/Fri; Day-7 Week 14 topo/Union-Find; Day-14 Week 13 graphs; Day-30 Week 11 heaps.

**Java rep (Fri, ≤ 30 min):** #322 Coin Change in Java with `int[] dp` and `Arrays.fill(dp, amount + 1)` sentinel — note `Integer.MAX_VALUE` overflow if you add to it, which Python never warned you about.

## 8. Project work — ForgeCI M2 (Queue + workers + Docker execution) = MVP

Spec: [`18-projects/forgeci/README.md`](../../18-projects/forgeci/README.md) · [`milestones.md`](../../18-projects/forgeci/milestones.md) (M2) · [`failure-engineering.md`](../../18-projects/forgeci/failure-engineering.md)

### 8.1 Task checklist

- [ ] **Mon:** milestone `M2 — Queue + workers + Docker execution` + issues; ADR 0003; `core` queue abstraction (`JobQueue.claim/ack/requeue`) + Redis implementation + Testcontainers Redis tests.
- [ ] **Tue:** `worker` app: slot loop, lease on claim, interruption + graceful shutdown; `api` enqueue after commit; `attempt` column + per-attempt results migration `V3__attempts.sql`.
- [ ] **Wed:** executor: image ensure, volume, container with limits, clone at SHA, exec steps, exit codes, status derivation; cleanup `finally` + labels.
- [ ] **Thu:** leftover sweeper; build status roll-up; `GET /builds/{id}` shows steps + exit codes; Compose with 2 workers + socket mount.
- [ ] **Fri:** exactly-once test (3 workers, 20 jobs) and cleanup tests; `docs/ARCHITECTURE.md` (queue + worker sections).
- [ ] **Sat (extended, +2 h):** real-repo pass/fail runs; failure scenarios (§8.4); ADR `0004-one-container-per-job.md`; PR review; tags `m2`, **`mvp`**.

### 8.2 Acceptance summary

MVP = a push runs in a container and the result is correct, with at-least-once dispatch, idempotent execution, cleanup guaranteed, and the exactly-once-execution test green across multiple workers.

### 8.3 Verification tests you write

| Test | Type | Proves |
|---|---|---|
| `RedisJobQueueIT` | Testcontainers Redis | claim moves to processing list (or pending entry); ack removes; unacked survives a client disconnect |
| `ClaimIsExclusiveIT` | 3 in-process "workers", 20 jobs | each job claimed exactly once (set of claimed ids has no duplicates, size 20) |
| `ExactlyOnceExecutionIT` | Testcontainers Redis + Postgres, fake executor | 20 jobs, 3 worker loops → 20 executions, attempt = 1 each |
| `ExecutorRunsStepsAndCapturesExitCodesIT` | real Docker (tagged `docker`, runs in CI on a runner with Docker) | `exit 0; exit 3` → step 2 exit 3, job `FAILED`, step 3 not run |
| `ExecutorCleansUpOnFailureIT` | real Docker | exception during clone → no container/volume with the job label remains |
| `ExecutorCleansUpOnInterruptIT` | real Docker | interrupt during a `sleep 60` step → container removed within 15 s |
| `OomIsReportedIT` | real Docker | 64 MB limit + memory hog → exit 137, `OOMKilled=true` in the result |
| `InfraFailureClassificationTest` | unit | pull error / start error / daemon down → `INFRA_ERROR`; exit ≠ 0 → `FAILED` |
| `GracefulShutdownIT` | worker `@SpringBootTest` | `stop()` during a running fake step waits for it, then exits; no job left claimed-without-result |
| `BuildStatusRollupTest` | unit | job status combinations → build status |

### 8.4 Failure-engineering scenarios this week

M2 set: worker killed (`docker kill`) mid-step → job stays in processing list with a lease (recovery is M4 — record what you observe now); Redis restarted with and without AOF → which enqueues vanish, and are `QUEUED` rows in Postgres still there?; Docker daemon restarted mid-job; image pull fails (typo image) → `INFRA_ERROR`, not `FAILED`; disk full on the volume path (simulate with a tiny tmpfs) → what error surfaces? Write-ups in `docs/FAILURES.md`.

### 8.5 GitHub expectations

Milestone `M2`; PRs `feat/m2-queue`, `feat/m2-worker-app`, `feat/m2-executor`, `feat/m2-cleanup-sweeper`, `test/m2-exactly-once`, `docs/m2-adrs`. CI: a `docker`-tagged test job that runs on a runner with Docker available (GitHub-hosted Ubuntu runners have it). Tags `m2`, `mvp`.

## 9. Git activity

- Cloning as a machine: practise `git clone --depth 1 --single-branch --branch <ref>` and `git fetch --depth 1 origin <sha> && git checkout FETCH_HEAD` — the second form is what a CI worker actually needs (a SHA, not a branch).
- Token hygiene: use `GIT_ASKPASS`/credential helper or an `https://x-access-token:<token>@` URL that is **never logged**; add a log-masking test.
- Tag `mvp` with a message listing what "MVP" means for ForgeCI.

## 10. Interview preparation

Two tracks, never mixed (ROADMAP §10): **Track A = coding interview in Python**; **Track B = software-engineering / résumé interview in Java, Spring, SQL, Redis, Docker…**

**Track A (Python)**
- **Weekly mock #3 (Sat, 45 min + 15 min review):** one unseen 1-D DP or graph Medium (e.g. LeetCode 139 Word Break or 300 LIS) in Python under protocol; cheatsheet closed; score against [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md); log in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Recorded solve (Tue, 30 min):** #198 House Robber, state/recurrence/top-down/bottom-up/space said out loud.

**Track B (Java / projects)**
- **Engineering mock slice (Sat, 15 min):** "Design a job queue on Redis. What happens when a worker dies?" — from your ADR 0003.
- **Résumé-defense drill — Java (concurrency), Redis, Docker (second pass):** [`17-resume-tech-defense/java.md`](../../17-resume-tech-defense/java.md), [`redis.md`](../../17-resume-tech-defense/redis.md), [`docker.md`](../../17-resume-tech-defense/docker.md). Required: interruption contract, `synchronized` vs `ReentrantLock`, virtual threads, `BLMOVE` vs Streams, `finally`-cleanup.
- **Applications (Sun):** 5/week; note that "OA-ready" is targeted ~W16–17 ([`JOB_READINESS.md`](../../JOB_READINESS.md)) — start reading [`OA_PREP.md`](../../OA_PREP.md) fully this Sunday (30 min).

## 11. Revision work

- Re-explain the LedgerX outbox and map it onto "commit job rows, then enqueue to Redis" — where is the gap and what closes it (reconciler)?
- Flashcards: `BLMOVE` args, `XREADGROUP` `>` vs `0`, `XAUTOCLAIM`, `LREM count`, `shutdown` vs `shutdownNow`, `awaitTermination`, happens-before rules, `volatile` guarantees.
- Python pitfall of the week: `@lru_cache` on a method with a `list` argument → `TypeError: unhashable`; convert to `tuple` or index-based state.

## 12. Daily plan

| Day | Learning | Project | DSA | Interview / other |
|---|---|---|---|---|
| **Mon** (8h) | Java concurrency deep I: executors, interruption; labs 1–2 (2h) | Milestone, ADR 0003, queue abstraction + Redis impl + tests (4.5h) | DP §1–5, #70, #746 (1.5h) | — |
| **Tue** (8h) | — | Worker app: slot loop, lease, shutdown; api enqueue; attempts migration (5h) | #198, #213 + Day-3 reviews (2h) | Recorded solve #198 (1h) |
| **Wed** (8h) | Redis queues (lab 3) + networking fundamentals (2h) | Executor: image, volume, container, clone, exec steps, exit codes, cleanup (4.5h) | #5, #647 (1.5h) | — |
| **Thu** (8h) | — | Sweeper, status roll-up, API view, Compose ×2 workers (5h) | #91, #322 + Day-7 reviews (2h) | `docs/ARCHITECTURE.md` queue/worker sections (1h) |
| **Fri** (5h) | Java concurrency deep II: locks, atomics, virtual threads (lab 5) (1h) | Exactly-once + cleanup tests (2h) | Reviews + Java rep #322 (1h) | Retro prep (1h) |
| **Sat** (8h) | — | Real-repo runs, failure scenarios, ADR 0004, PR review, tags `m2`/`mvp` (6h) | — | Mock #3 (A + B slice) + drill (2h) |
| **Sun** (2–3h) | — | — | Day-14/30 reviews | End-of-week test, trackers, plan Week 16, applications, `OA_PREP.md` read |

## 13. End-of-week test (Sunday, 75 min)

**Part A — DSA (30 min, Python).** LeetCode **139. Word Break** in ≤ 25 min (state = "prefix of length i is breakable"); then say the top-down version with `lru_cache`.

**Part B — Concepts (20 min).**

1. Worker crashes after `BLMOVE` and before ack. Where is the job and how is it recovered?
   <details><summary>Answer</summary>In that worker's processing list (or the stream's pending entries). A reaper finds ids whose lease expired (or `XAUTOCLAIM` with min-idle) and requeues them; execution is idempotent via `attempt`.</details>
2. Why is "exactly-once delivery" the wrong goal, and what do you aim for instead?
   <details><summary>Answer</summary>Any acknowledgement can be lost between effect and ack, so delivery is at-least-once (or at-most-once). Aim for at-least-once delivery + idempotent processing = exactly-once *effect*.</details>
3. What does a correct `catch (InterruptedException e)` block do in a worker loop?
   <details><summary>Answer</summary>Restores the interrupt flag (`Thread.currentThread().interrupt()`) or rethrows, and exits the loop so shutdown completes; it never silently continues.</details>
4. Why one container per job rather than one per step?
   <details><summary>Answer</summary>The workspace and environment persist between steps (like real CI); fewer create/remove cycles; a step's exit code is still isolated via `exec`. Trade-off: a hung step blocks the container — handled by timeouts in M4.</details>
5. What can Redis lose on a crash, and how does ForgeCI tolerate it?
   <details><summary>Answer</summary>Up to the last fsync window (AOF `everysec` ≈ 1 s; RDB minutes). Postgres holds `QUEUED` truth; a reconciler re-enqueues jobs missing from Redis.</details>
6. `Executors.newFixedThreadPool(4)` uses which queue and what happens when 10,000 tasks are submitted?
   <details><summary>Answer</summary>An unbounded `LinkedBlockingQueue`; all 10,000 are accepted and buffered in memory — no backpressure. Use a bounded queue with a rejection policy (or `CallerRunsPolicy`) when producers can outrun consumers.</details>

**Part C — Practical (20 min).** Write from memory: the `BLMOVE` + lease claim sequence (Redis commands), and a Java `try/finally` that guarantees container + volume removal with logging on failure.

**Part D — Explain (5 min, Track B).** "A job is enqueued. Walk me through how it becomes a running container and how you know it ran exactly once."

Pass: A in time · B ≥ 5/6 · C correct · D fluent.

## 14. Mastery checklist

- [ ] I can explain my queue design and its rejected alternative with concrete Redis commands.
- [ ] I can describe the worker's threading model, interruption handling and graceful shutdown.
- [ ] I can list the container lifecycle per job and prove cleanup with tests.
- [ ] I distinguish app failure vs infra failure in code and words.
- [ ] I know what Redis can lose and how Postgres-as-truth + reconciler handles it.
- [ ] I ran the virtual-threads benchmark and can say when they help.
- [ ] 8 1-D DP problems done in Python with the five-step method; Java rep done; reviews done.
- [ ] Mock #3 done and scored.

## 15. Expected deliverables

- `forgeci`: tags `m2`, `mvp`; ADRs 0003, 0004; `docs/ARCHITECTURE.md` (queue + worker + executor), `docs/FAILURES.md` (M2 set); Compose with scalable workers.
- Trackers: [`project-tracker.md`](../../trackers/project-tracker.md) (MVP reached), [`dsa-tracker.md`](../../trackers/dsa-tracker.md), [`technology-tracker.md`](../../trackers/technology-tracker.md) (Java concurrency, Redis queues → "can defend with evidence"), [`interview-tracker.md`](../../trackers/interview-tracker.md), [`weekly-progress.md`](../../trackers/weekly-progress.md).

## 16. If behind / stretch

**Behind?** Cut order: leftover sweeper (keep `finally`) → resource limits (keep `--init`) → Compose scaling to 2 workers (1 is fine for MVP) → #5/#647 (do #647 only). **Never cut** the exactly-once test, cleanup tests, or the interruption handling — Week 16's live logs and Week 17's timeouts build directly on them.

**Ahead?** Add the re-enqueue reconciler now (`QUEUED` in Postgres, absent in Redis → enqueue), with a test that flushes Redis mid-run; implement image-pull progress logging; post commit statuses (`pending` → `success`/`failure`) to GitHub; solve LeetCode 300 (LIS) and 152 (Maximum Product Subarray) in Python.
