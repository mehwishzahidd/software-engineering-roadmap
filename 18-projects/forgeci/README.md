# ForgeCI — Distributed CI/CD Execution Platform

> **Weeks 14–19 · 170–200 h · the hardest of the four projects.**
> Technical identity: queues, worker pools, Docker-isolated execution, webhooks, live logs over SSE,
> timeouts / cancellation / retries, failure recovery, DAG scheduling.
>
> This folder is the **spec and the teaching plan**. You build ForgeCI in its own GitHub repository.
> Nothing here is a copy/paste implementation — see the project rule in [`ROADMAP.md`](../../ROADMAP.md#1-learning-philosophy).

| File | Purpose |
|---|---|
| [`README.md`](./README.md) (this file) | Product, requirements, scope tiers, architecture, every design decision, data model, API, execution model, testing / deployment / security / performance strategy |
| [`milestones.md`](./milestones.md) | M1–M6: know first → requirements → guidance → acceptance → implement → tests → debugging → interview questions |
| [`failure-engineering.md`](./failure-engineering.md) | 16 break-it scenarios with reproduce / observe / diagnose / fix / regression test |
| [`interview-questions.md`](./interview-questions.md) | Basic → advanced, system-design and troubleshooting questions, deep-dive talk outline |
| [`docs-and-resume.md`](./docs-and-resume.md) | Required docs, benchmarking protocol, résumé-bullet template from measured results |

---

## Contents

1. [Product overview](#1-product-overview)
2. [Problem statement](#2-problem-statement)
3. [High-level flow](#3-high-level-flow)
4. [Functional requirements](#4-functional-requirements)
5. [Non-functional requirements](#5-non-functional-requirements)
6. [Scope tiers](#6-scope-tiers)
7. [Milestone summary](#7-milestone-summary)
8. [Architecture](#8-architecture)
9. [Decision: queue technology](#9-decision-queue-technology)
10. [Decision: live logs — SSE vs WebSockets](#10-decision-live-logs--sse-vs-websockets)
11. [Architecture diagram spec](#11-architecture-diagram-spec)
12. [Database design](#12-database-design)
13. [State machines](#13-state-machines)
14. [Pipeline configuration format](#14-pipeline-configuration-format)
15. [API design](#15-api-design)
16. [Package structure](#16-package-structure)
17. [Authentication model](#17-authentication-model)
18. [Authorization model](#18-authorization-model)
19. [Docker execution model](#19-docker-execution-model)
20. [Testing strategy](#20-testing-strategy)
21. [Docker / Compose strategy](#21-docker--compose-strategy)
22. [CI/CD strategy](#22-cicd-strategy)
23. [AWS deployment plan](#23-aws-deployment-plan)
24. [Logging strategy](#24-logging-strategy)
25. [Error-handling strategy and failure taxonomy](#25-error-handling-strategy-and-failure-taxonomy)
26. [Security considerations](#26-security-considerations)
27. [Performance considerations](#27-performance-considerations)
28. [Scalability](#28-scalability)
29. [Git strategy, issues, PR workflow](#29-git-strategy-issues-pr-workflow)
30. [README and demo requirements](#30-readme-and-demo-requirements)
31. [Prerequisite topic map](#31-prerequisite-topic-map)

---

## 1. Product overview

ForgeCI is a small, self-hosted continuous-integration service. A developer registers a GitHub
repository, adds a `.forgeci.yml` to it, and from then on every push triggers a **build** that runs
the configured steps inside a **fresh Docker container**, streams **live logs** to a browser, and
records the result. It borrows familiar *concepts* from GitHub Actions and CircleCI — pipelines,
jobs, steps, a YAML config, a job-detail page with a live log — but it is **not a clone**: no
marketplace, no reusable actions, no hosted runners fleet. Its purpose is to make you build and
defend the hard parts that every CI vendor has to solve:

- Accepting events reliably (webhooks are retried by GitHub; duplicates must not create duplicate builds).
- Moving work from an HTTP-facing API to a pool of workers **without losing or double-running a job**.
- Running **untrusted user code** in isolation, with bounded CPU/memory/time, and always cleaning up.
- Streaming output from a container on machine B to a browser watching through machine A,
  surviving reconnects.
- Telling the difference between "your test failed" and "our infrastructure failed", and reacting differently.
- Recovering when a worker dies mid-job, Redis is unreachable, or the Docker daemon is gone.
- Scheduling a graph of dependent jobs (fan-out / fan-in) with fail-fast.

**Users:** a developer who owns one or more GitHub repos and wants builds to run on push.
**Operator:** you — running the API, a few workers, Postgres and Redis on a laptop (Compose) and later on AWS.

## 2. Problem statement

> Build a service that turns a GitHub push into an isolated, observable, bounded execution of the
> repository's own build script, at least once and never concurrently for the same job, with
> results and logs durable and visible live — and that keeps working (or degrades predictably)
> when any single component fails.

Every word in that sentence maps to a milestone:

| Phrase | Why it is hard | Milestone |
|---|---|---|
| "turns a GitHub push into" | Webhooks are unauthenticated HTTP from the internet; GitHub retries; ordering is not guaranteed | M1 |
| "isolated ... bounded execution" | Containers, resource limits, timeouts, cleanup on every path | M2, M4 |
| "at least once and never concurrently" | Reliable queue semantics, leases, heartbeats, orphan recovery | M2, M4 |
| "observable ... visible live" | Log pipeline across processes; SSE replay after reconnect | M3 |
| "degrades predictably" | Failure taxonomy, retries with backoff, graceful shutdown, failure-engineering suite | M4, M5 |
| dependent jobs | DAG validation and topological scheduling | M6 |

## 3. High-level flow

The BRIEF's one-line flow, expanded into the numbered sequence you will implement:

1. **GitHub event.** A developer pushes to a registered repository. GitHub POSTs a `push` event to `POST /api/webhooks/github` with headers `X-GitHub-Event`, `X-GitHub-Delivery` (a UUID unique per delivery) and `X-Hub-Signature-256` (`sha256=<HMAC-SHA256 of the raw body with the webhook secret>`).
2. **Verify.** The API reads the **raw** request body, computes the HMAC with the secret stored for that repository, and compares in constant time. Mismatch → `401`, nothing recorded.
3. **Dedupe.** The API inserts a `webhook_delivery` row keyed by `delivery_id` (unique). A duplicate delivery (GitHub redelivery, operator "Redeliver" button, a flaky network) hits the unique constraint and returns `200` with the existing build — **idempotent**.
4. **Create build.** In the same transaction: look up the repository, fetch `.forgeci.yml` at the pushed commit through the GitHub Contents API, parse and validate it, create `pipeline` (snapshot of the parsed config), `build` (`QUEUED`), one `job` per configured job (M1–M5: exactly one job; M6: many), one `step` per command.
5. **Enqueue.** After commit, push each *runnable* job id onto the Redis queue (`LPUSH forgeci:queue:jobs`). Because the DB commit happens first, a crash between commit and push is recovered by a periodic "QUEUED but not in Redis" sweep (M4).
6. **Worker pulls.** Each worker loops on `BLMOVE forgeci:queue:jobs forgeci:processing:<workerId> RIGHT LEFT 5` — atomically moving the job id into its own processing list. It writes a **lease** (`job:<id>:lease = workerId`, TTL 30 s) and transitions the job `QUEUED → LEASED → RUNNING` in Postgres.
7. **Temporary container.** The worker creates a workspace directory, pulls the image named in the config (if missing), creates a container with CPU/memory limits, no privileges, the workspace mounted at `/workspace`, and starts it.
8. **Clone.** Inside the container (or on the host before mounting — see §19), `git clone --depth 1` of the commit SHA using a short-lived token.
9. **Run steps.** For each step, `docker exec` the command with `sh -c`, capture stdout/stderr, record exit code; stop at the first non-zero exit.
10. **Logs streamed.** Output is read in chunks; each chunk is assigned a per-job sequence number, persisted to `log_chunk`, and published to Redis channel `logs:<jobId>`. The API's SSE endpoint relays it to browsers; on reconnect the browser sends `Last-Event-ID` and the API replays missed chunks from Postgres.
11. **Result persisted.** Job → `SUCCEEDED` / `FAILED` / `TIMED_OUT` / `CANCELLED`; build status derived from its jobs; `ack` = `LREM` the id from the processing list and delete the lease.
12. **UI updated.** Status change published on `builds:<buildId>` channel → SSE → React updates the badge without polling.
13. **Cleanup.** In a `finally`: kill + remove the container, remove the workspace, release the concurrency-limit slot. Always, on every code path.

## 4. Functional requirements

All BRIEF core features, as testable statements:

**Repositories and GitHub integration**
- FR-1 A user can log in with GitHub OAuth (MVP: paste a Personal Access Token) and register a repository they own or administer.
- FR-2 Registering a repository creates a GitHub webhook (or shows the URL + secret to add manually in MVP) and stores the webhook secret and an encrypted access token.
- FR-3 Webhook deliveries are verified with HMAC-SHA256 over the raw body; invalid signatures are rejected with `401` and logged with the delivery id (never the body).
- FR-4 Duplicate deliveries (same `X-GitHub-Delivery`) never create a second build.
- FR-5 Only `push` events (and `ping`) are handled; other events return `202` and are ignored (recorded as `IGNORED`).

**Pipelines, builds, jobs, steps**
- FR-6 `.forgeci.yml` is fetched at the pushed commit, parsed and validated; validation errors create a build in `FAILED` with `failure_kind = CONFIG` and a readable message.
- FR-7 A build has one or more jobs; each job has an ordered list of steps (shell commands), an image, a timeout, and (M6) a `needs` list.
- FR-8 Builds and jobs are listable per repository with pagination and filterable by status and branch.
- FR-9 A build can be re-run ("retry") manually; the retry is a **new build** referencing the original.

**Execution**
- FR-10 Each job runs in a **new container** from the configured image with the repository cloned at the exact commit.
- FR-11 Steps run sequentially; the first non-zero exit code fails the job; remaining steps are `SKIPPED`.
- FR-12 A job is executed by **exactly one worker at a time**; if the worker dies, another worker retries it (bounded, with backoff).
- FR-13 Each job has a timeout (default 10 min, max 60 min); on expiry the container is killed and the job is `TIMED_OUT`.
- FR-14 A queued job can be cancelled (removed from the queue); a running job can be cancelled (container killed) — both end in `CANCELLED`.
- FR-15 Per-repository concurrency limit (default 2 running jobs); excess jobs stay `QUEUED` and start when a slot frees.

**Logs and UI**
- FR-16 Log output is visible in the browser **while the job runs** with < 1 s latency on a local stack.
- FR-17 A browser that disconnects and reconnects sees no gap and no duplicates.
- FR-18 Completed job logs are viewable later (persisted).
- FR-19 The React UI shows: repository list, build list (status badge, branch, commit, duration, trigger), build detail with jobs, job detail with live log, cancel and retry buttons, worker list with health.

**Operations**
- FR-20 Workers send heartbeats; the UI/API shows each worker's last heartbeat and current jobs; workers absent > N seconds are marked `DEAD` and their leased jobs are recovered.
- FR-21 Graceful shutdown: `SIGTERM` stops taking new jobs, finishes or requeues the current one, then exits.
- FR-22 (M6) Jobs with `needs:` run only after all dependencies `SUCCEEDED`; if a dependency fails, dependents are `SKIPPED` and the build fails fast.

## 5. Non-functional requirements

| NFR | Requirement | How you will prove it |
|---|---|---|
| **Isolation** | User code never runs on the worker host process; each job gets a fresh container, no `--privileged`, no host network, resource-limited | `docker inspect` in tests; a job that runs `cat /etc/hostname` shows the container id; a fork-bomb step is contained by `pids-limit` |
| **At-least-once execution, idempotent effects** | A job is never lost (worker crash → retried); a job is never running on two workers at once; re-execution is safe because every job runs in a fresh container and results are written by `attempt` | `queue_twoWorkersOneJob_executesOnce`, `worker_diesMidJob_leaseExpires_jobRetriedAsInfraFailure` |
| **Bounded resource use** | CPU, memory, pids and wall-clock per job are capped; workspaces are deleted; processing lists cannot grow without bound | Compose stack running 50 jobs leaves `docker ps -a` and `du -sh workspaces/` back at baseline |
| **Observability** | Every log line from API and worker carries `build_id`, `job_id`, `worker_id` where known; Actuator health shows Redis/DB/Docker; metrics for queue depth, jobs per state, queue wait time | Grep a build id across API + worker logs and reconstruct its timeline |
| **Measured queue latency** | Time from `QUEUED` to `RUNNING` is recorded per job and reported as p50/p95 | `docs/PERFORMANCE.md` with the protocol in [`docs-and-resume.md`](./docs-and-resume.md) |
| **Durability** | Build/job/log state survives API, worker and Redis restarts (Postgres is the source of truth; Redis is a transport) | Failure scenarios 6, 7, 8 in [`failure-engineering.md`](./failure-engineering.md) |
| **Security** | Secrets encrypted at rest; webhook verified; tokens never logged; Docker socket exposure documented and minimized | `docs/SECURITY.md` and §26 |

## 6. Scope tiers

| Tier | Contents | When |
|---|---|---|
| **MVP** | PAT-based repo registration, verified + deduped webhook, `.forgeci.yml` (image + steps), Redis queue, one worker app, execution in a temporary container, exit codes, persisted results, `curl`-able API. **No UI, no live logs.** | End of M2 (Week 15) |
| **STRONG RESUME VERSION (v1.0)** | Everything in MVP + live logs over SSE with replay, React UI, timeouts, cancellation (queued + running), retry policy by failure kind, heartbeats + orphan recovery, per-project concurrency limits, graceful shutdown, Testcontainers integration + concurrency tests, failure-engineering suite, Compose with N workers, CI/CD to GHCR, the Python `tools/` component (repo generator, signed-webhook load simulator, result/log analysis — pytest-tested), measured queue latency and throughput, full docs set. | End of M5 (Week 18), tag `v1.0` |
| **ADVANCED** | DAG pipelines (`needs:`, topological scheduling, fan-out/fan-in, fail-fast), AWS deployment (API EC2, worker EC2 with Docker, RDS, Redis), SQS evaluated vs Redis, GitHub OAuth login (if PAT was used until now). | M6 (Week 19), tag `v1.1` |
| **OPTIONAL STRETCH** | Dependency caching between builds (keyed by hash of `pom.xml`, restored into the workspace), build artifacts (upload to S3 with presigned download), matrix builds (`matrix: {java: [17, 21]}` expands to N jobs), a minimal secrets manager (per-repo encrypted key/values injected as env vars and masked in logs). | Polish weeks or never — never blocks v1.0 |

**Slip rule** (from ROADMAP §8): if you are > 1 week behind at CP-16, drop M6, not M4/M5. A CI system without timeouts and orphan recovery is not a résumé project; one without DAGs still is.

## 7. Milestone summary

| Milestone | Week | Theme | Ends in | Hours |
|---|---|---|---|---|
| [M1](./milestones.md#m1--github-integration--idempotent-webhooks-week-14) | 14 | GitHub integration + idempotent webhooks: repo registration, OAuth/PAT, HMAC verification, `X-GitHub-Delivery` dedupe, build/job/step records, minimal `.forgeci.yml` parser | Builds are created from pushes and visible via API | 30 |
| [M2](./milestones.md#m2--queue--workers--docker-execution-week-15) | 15 | Reliable Redis queue (BLMOVE + processing list + lease), separate worker app, Docker execution, workspace, cleanup in `finally` | **MVP**: a push runs `mvn -q test` in a container and records the exit code | 32 |
| [M3](./milestones.md#m3--live-logs--ui-week-16) | 16 | Log chunks → Postgres + Redis pub/sub → SSE with `Last-Event-ID` replay; React build list/detail + live log | Live logs in the browser; **CP-16** | 32 |
| [M4](./milestones.md#m4--timeouts-cancellation-retries-limits-health-week-17) | 17 | Timeouts, cancellation, retry policy (app vs infra), heartbeats, orphan recovery via lease expiry, per-project limits (Lua), graceful shutdown | Failure-tolerant execution | 32 |
| [M5](./milestones.md#m5--failure-recovery--tests--full-stack--benchmarks-week-18) | 18 | Failure-engineering suite, Testcontainers + concurrency tests, Compose (api, 2 workers, postgres, redis, ui), CI/CD to GHCR, **Python `tools/`** (repo generator, webhook load simulator, result analysis), queue-latency and jobs/min benchmarks, docs | **v1.0 STRONG RESUME VERSION** | 32 |
| [M6](./milestones.md#m6--dag-pipelines--aws-week-19) | 19 | `needs:` DAG, Kahn scheduling, fan-out/fan-in, fail-fast; AWS deploy; SQS vs Redis note | **ADVANCED v1.1**; deep-dive rehearsal | 32 |

Total ≈ 190 h project time across six weeks (30–35 h/week, mostly Saturdays adding the extra 5 h).

## 8. Architecture

```
                         ┌──────────────────────────────────────────────────────────────┐
                         │                          GitHub                              │
                         │   push event ──► webhook POST      Contents API / OAuth      │
                         └──────┬───────────────────────────────▲───────────────────────┘
                                │ X-Hub-Signature-256            │ token (encrypted at rest)
                                ▼                                │
┌───────────────┐   HTTPS   ┌──────────────────────────────────┴─────┐      ┌──────────────┐
│  React UI     │◄─────────►│  forgeci-api (Spring Boot)             │◄────►│  PostgreSQL  │
│  (Vite, TS)   │  REST +   │  webhooks · builds · jobs · SSE · auth │ JPA  │  source of   │
│  EventSource  │  SSE      │  limits · workers · scheduler (M6)     │      │  truth       │
└───────────────┘           └───────┬──────────────────▲─────────────┘      └──────▲───────┘
                                    │ LPUSH jobs        │ SUBSCRIBE logs:*, builds:*      │
                                    ▼                   │                                │
                            ┌──────────────────────────────────┐                         │
                            │  Redis 7                         │                         │
                            │  queue:jobs (list)               │                         │
                            │  processing:<worker> (lists)     │                         │
                            │  job:<id>:lease (TTL keys)       │                         │
                            │  limit:<repo> (counters)         │                         │
                            │  pub/sub: logs:<job>, builds:<b> │                         │
                            └──────▲───────────────┬───────────┘                         │
                    BLMOVE / heartbeat / PUBLISH    │                                     │
                                   │               │                     INSERT log_chunk,│
        ┌──────────────────────────┴───────────────▼──────────────┐      UPDATE job       │
        │  forgeci-worker × N (Spring Boot, no web server)        ├───────────────────────┘
        │  pull loop · lease · executor · log pump · heartbeat    │
        └──────────────────────────┬──────────────────────────────┘
                                   │ docker-java (unix socket / TCP)
                                   ▼
                       ┌────────────────────────┐
                       │  Docker Engine         │  one temporary container per job,
                       │  (on the worker host)  │  workspace bind-mounted, limits applied
                       └────────────────────────┘

   Maven multi-module:  forgeci-common (entities, enums, DTOs, config parser, Redis key names)
                        forgeci-api    (web, security, SSE, scheduler)   depends on common
                        forgeci-worker (queue consumer, Docker executor)  depends on common

   Python tools/ (outside the JVM, M5):
        repogen  ──creates──►  git repos (+ optional GitHub push → real webhook)
        simulator ──signed POST /api/webhooks/github at a rate──►  forgeci-api ; polls builds → timings CSV
        analysis  ──reads REST API or Postgres──►  failure-taxonomy stats, p50/p95 → PERFORMANCE.md
```

**Why workers pull (producer/consumer).** The API could *push* work to workers over HTTP, but then the API would have to know every worker's address, health and free capacity, and it would have to retry when a worker is busy or down — it would become a scheduler. With a pull model the API only appends to a queue and forgets. Workers take work **when they have capacity**, which gives natural backpressure (a slow worker simply pulls less), trivial horizontal scaling (start another worker process — nothing to register), and simple failure handling (a dead worker just stops pulling; its leased jobs are reclaimed by lease expiry). This is the classic producer/consumer pattern from [`01-java/07-concurrency.md`](../../01-java/07-concurrency.md), with Redis as the shared `BlockingQueue`.

**Why not Kafka.** Kafka is a partitioned, replicated *log* optimized for high-throughput ordered streams consumed by many independent consumer groups. ForgeCI needs a *work queue*: each job consumed by exactly one worker, with per-message acknowledgement, retry and visibility timeout. Kafka's unit of parallelism is the partition, so a slow job blocks every job behind it in that partition (head-of-line blocking) unless you build a second-level queue; per-message ack/redelivery needs extra machinery. Operationally it means a ZooKeeper/KRaft cluster for a system with a few jobs per minute. Redis is already in the stack for pub/sub and limits, and one `BLMOVE` gives exactly the semantics required. At 100× scale the honest answer is SQS (managed, visibility timeouts, DLQ) — not Kafka. The ROADMAP explicitly excludes Kafka for this reason (§2.4).

**Why a separate worker application.** Workers hold the Docker socket; the API faces the internet. Separating them means the internet-facing process never has Docker access (blast radius), workers can be scaled and placed on beefier hosts independently, and a worker crash (an OOM inside the JVM while pumping logs) never takes the API down. They share `forgeci-common` so entity mappings and Redis key names exist exactly once.

## 9. Decision: queue technology

| Criterion | **Redis reliable queue** (`BLMOVE` + processing list + lease key) | Redis Streams + consumer groups (`XADD`/`XREADGROUP`/`XACK`/`XAUTOCLAIM`) | AWS SQS |
|---|---|---|---|
| Delivery semantics | At-least-once if you re-queue from processing lists on lease expiry | At-least-once built in (pending entries list, `XAUTOCLAIM`) | At-least-once (visibility timeout), FIFO variant available |
| Exactly-one-active-consumer | Yes — `BLMOVE` is atomic; a job id exists in exactly one list | Yes — an entry is delivered to one consumer of the group | Yes, during visibility timeout |
| Crash recovery | **You implement it**: scan `processing:*` lists for ids whose lease key expired, move back | Built in: `XPENDING` + `XAUTOCLAIM min-idle-time` | Built in: message reappears after visibility timeout |
| Blocking pop | Yes (`BLMOVE ... 5`) | Yes (`XREADGROUP BLOCK 5000`) | Long polling (≤ 20 s) |
| Ordering | FIFO per list | Ordered by stream id | Best-effort (standard) / strict (FIFO) |
| Cancellation of queued job | `LREM queue:jobs 0 <id>` | Not removable; must skip on read (tombstone) | Not removable; skip on receive |
| Priority | Multiple lists + `BLMOVE` on the highest first (or `BLMPOP` in Redis 7) | Multiple streams | Multiple queues |
| Observability | `LLEN`, `LRANGE` — trivial to inspect in `redis-cli` | `XINFO GROUPS/CONSUMERS`, `XPENDING` — richer | CloudWatch metrics |
| Spring Data Redis support | `ListOperations.move(...)` / Lettuce `blmove` | `StreamOperations` + `StreamMessageListenerContainer` | AWS SDK v2 / Spring Cloud AWS |
| Learning value | **Highest** — you build leases, heartbeats and recovery yourself and can explain every guarantee | Medium — the interesting parts are hidden behind `XAUTOCLAIM` | Low locally; high for AWS story |
| Local dev | `docker run redis:7` | same | LocalStack or a Redis stand-in |

**Default: the Redis reliable queue (`BLMOVE` + per-worker processing list + lease key with TTL).**
Justification: (1) the whole point of Weeks 15–17 is to *understand* leases, heartbeats and orphan
recovery — Streams would do that for you and leave you unable to explain it; (2) queued jobs must
be cancellable, which lists support with `LREM`; (3) the Redis instance is already there for
pub/sub and limits; (4) the recovery scan you write is the same design as SQS's visibility timeout,
so migrating to SQS later is a change of transport, not of model.

**Keep Streams as the documented alternative** in `docs/DESIGN_DECISIONS.md` (ADR-002) — and if you
finish M4 early, implementing the queue interface a second time on Streams is an excellent exercise.
**SQS is the AWS alternative** considered in M6: on AWS, SQS with a 60-second visibility timeout and
a dead-letter queue after 3 receives replaces the list + lease + recovery scan, and the worker calls
`ChangeMessageVisibility` as its heartbeat. Program against a `JobQueue` interface
(`Optional<Lease> poll(Duration)`, `void heartbeat(Lease)`, `void ack(Lease)`, `void nack(Lease)`)
so the implementation is swappable.

Redis mechanics: [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md). Concept:
[`15-system-design/scalability.md`](../../15-system-design/scalability.md).

## 10. Decision: live logs — SSE vs WebSockets

| Criterion | **Server-Sent Events** | WebSockets |
|---|---|---|
| Direction | Server → client only. Logs only flow one way; cancel is a normal `POST`. | Bidirectional (not needed here) |
| Transport | Plain HTTP/1.1 (or HTTP/2) response with `Content-Type: text/event-stream`; works through any proxy / load balancer that supports streaming responses | Protocol upgrade; some proxies / corporate firewalls / older ALB configs need explicit support |
| Reconnect | **Built into the browser `EventSource`**: automatic reconnect with the `Last-Event-ID` header — exactly the replay hook needed | You write reconnect + resume logic yourself |
| Auth | Same cookies / same-origin as the REST API; `EventSource` cannot set headers, so use a cookie or a short-lived token query param | Headers on the upgrade request; also awkward from browsers |
| Server-side in Spring | `SseEmitter` (Spring MVC) — synchronous style, one thread parked per client unless you use async servlet support; fine for tens of viewers, use WebFlux `Flux<ServerSentEvent>` for thousands | Spring WebSocket + STOMP, more moving parts |
| Message format | Text only, `id:` / `event:` / `data:` lines | Text or binary frames |
| Browser connection limit | 6 per host on HTTP/1.1 (one tab watching one job is fine; HTTP/2 lifts it) | Not subject to that limit |
| Backpressure | Slow client → emitter's `send` blocks / fails; you drop the client, it reconnects and replays | Same problem, more control over framing |

**Pick SSE.** The data is unidirectional, text, and the browser's `EventSource` gives us reconnect +
`Last-Event-ID` for free, which turns "replay from sequence" into a small, testable server-side
feature instead of a custom protocol. Event ids are the per-job chunk `seq`; on reconnect the API
reads `log_chunk WHERE job_id = ? AND seq > ?` from Postgres, sends those, then subscribes to
`logs:<jobId>` for new ones (mind the race — see M3 guidance). Write ADR-003 with this table.

## 11. Architecture diagram spec

Produce `docs/diagrams/architecture.png` (and the source, e.g. draw.io / Excalidraw / Mermaid) in Week 25. Required elements:

- Boxes: GitHub, React UI, forgeci-api, forgeci-worker (×N, drawn as a stack), PostgreSQL, Redis, Docker Engine, one "job container" inside the worker host boundary.
- Arrows labelled with protocol and direction: `webhook (HTTPS, HMAC)`, `REST/JSON`, `SSE text/event-stream`, `LPUSH / BLMOVE`, `PUBLISH / SUBSCRIBE`, `JDBC`, `docker-java (unix socket)`, `git clone (HTTPS + token)`.
- Trust boundaries as dashed rectangles: *internet*, *ForgeCI control plane* (api + db + redis), *execution host* (worker + docker), *untrusted* (job container).
- A second diagram, `sequence-webhook-to-log.png`: the 13 steps of §3 as a sequence diagram (GitHub, API, Postgres, Redis, Worker, Docker, Browser lanes).
- A third, `queue-states.png`: the job state machine (§13) with which component performs each transition.

## 12. Database design

Postgres 16, managed by Flyway. Postgres is the **source of truth**; Redis holds only transient
coordination state (queue, leases, counters, pub/sub). Everything below is a spec — you write the
migrations and the JPA entities.

### Tables

| Table | Columns (type, constraints) | Notes |
|---|---|---|
| `app_user` | `id uuid PK`, `github_id bigint UNIQUE`, `login text`, `avatar_url text`, `created_at timestamptz` | From OAuth; MVP: one row created on PAT registration |
| `installation` | `id uuid PK`, `user_id FK`, `token_ciphertext bytea`, `token_iv bytea`, `token_kind text CHECK IN ('PAT','OAUTH')`, `scopes text`, `created_at`, `rotated_at` | Token encrypted with AES-256-GCM; key from env, never in DB |
| `repository` | `id uuid PK`, `owner_user_id FK app_user`, `installation_id FK`, `github_repo_id bigint UNIQUE`, `full_name text UNIQUE` (`owner/name`), `default_branch text`, `webhook_secret_ciphertext bytea`, `webhook_secret_iv bytea`, `github_hook_id bigint`, `concurrency_limit int NOT NULL DEFAULT 2`, `active bool`, `created_at` | `concurrency_limit` is copied into Redis on change |
| `webhook_delivery` | `id uuid PK`, `delivery_id text NOT NULL UNIQUE`, `repository_id FK NULL`, `event text`, `status text CHECK IN ('ACCEPTED','IGNORED','REJECTED','FAILED')`, `build_id uuid NULL`, `received_at timestamptz`, `error text NULL` | **The dedupe table.** Insert first; unique violation ⇒ duplicate. Never stores the payload beyond a hash |
| `pipeline` | `id uuid PK`, `repository_id FK`, `commit_sha char(40)`, `config_yaml text`, `config_json jsonb`, `config_hash char(64)`, `created_at` | Snapshot of what was parsed; builds reference it |
| `build` | `id uuid PK`, `repository_id FK`, `pipeline_id FK NULL`, `number int NOT NULL`, `status text` (§13), `trigger text CHECK IN ('PUSH','MANUAL','RETRY')`, `branch text`, `commit_sha char(40)`, `commit_message text`, `pusher text`, `retry_of_build_id uuid NULL`, `failure_kind text NULL`, `error_message text NULL`, `created_at`, `started_at`, `finished_at`; `UNIQUE (repository_id, number)` | `number` from a per-repo counter (`UPDATE repository SET next_build_number = next_build_number + 1 RETURNING` or a sequence per repo) |
| `job` | `id uuid PK`, `build_id FK`, `name text`, `image text`, `timeout_seconds int`, `status text`, `attempt int NOT NULL DEFAULT 1`, `max_attempts int NOT NULL DEFAULT 3`, `worker_id uuid NULL`, `lease_expires_at timestamptz NULL`, `exit_code int NULL`, `failure_kind text NULL CHECK IN ('APP','INFRA','CONFIG','TIMEOUT','CANCELLED')`, `error_message text`, `needs jsonb DEFAULT '[]'`, `cancel_requested bool DEFAULT false`, `queued_at`, `leased_at`, `started_at`, `finished_at`; `UNIQUE (build_id, name)` | `attempt` increments on infra retry; `queued_at`→`started_at` is queue wait time |
| `step` | `id uuid PK`, `job_id FK`, `ordinal int`, `name text`, `command text`, `status text`, `exit_code int NULL`, `started_at`, `finished_at`; `UNIQUE (job_id, ordinal)` | |
| `log_chunk` | `job_id uuid FK`, `seq bigint`, `attempt int`, `stream text CHECK IN ('STDOUT','STDERR','SYSTEM')`, `content text`, `created_at`; `PRIMARY KEY (job_id, seq)` | `seq` assigned by the worker, monotonically per job (across attempts, so replay is simple). Rows are **data**, not application logs |
| `worker` | `id uuid PK`, `hostname text`, `capacity int`, `running_jobs int`, `version text`, `status text CHECK IN ('ALIVE','DRAINING','DEAD')`, `started_at`, `last_heartbeat_at` | Workers upsert on start and every heartbeat |
| `concurrency_limit` | `repository_id PK/FK`, `max_running int`, `updated_at` | Optional table if you want limits editable without touching `repository`; otherwise use the column |
| `audit_event` | `id bigserial PK`, `actor text` (user id / `system` / `worker:<id>`), `action text` (`BUILD_CANCELLED`, `JOB_RETRIED`, `REPO_REGISTERED`, ...), `entity_type text`, `entity_id uuid`, `details jsonb`, `created_at` | Append-only |

### Indexes

| Index | Why |
|---|---|
| `webhook_delivery (delivery_id) UNIQUE` | The dedupe guarantee |
| `build (repository_id, created_at DESC)` | Build list per repo, newest first |
| `build (repository_id, status)` | Filter by status; count running per repo |
| `job (status, lease_expires_at)` partial `WHERE status IN ('LEASED','RUNNING')` | Orphan-recovery scan |
| `job (build_id)` | Build detail |
| `log_chunk (job_id, seq)` = PK | Replay `WHERE job_id = ? AND seq > ? ORDER BY seq` |
| `worker (last_heartbeat_at)` | Dead-worker sweep |
| `audit_event (entity_type, entity_id, created_at)` | Audit view |

### ERD spec

Draw `docs/diagrams/erd.png`: `app_user 1—n installation 1—n repository 1—n build 1—n job 1—n step`;
`job 1—n log_chunk`; `repository 1—n webhook_delivery` (nullable FK — unregistered repos still get a
row with `status = REJECTED`); `build 0..1—n build` (retry_of); `worker 1—n job` (nullable);
`repository 1—0..1 concurrency_limit`; `audit_event` polymorphic by `(entity_type, entity_id)`.
Mark PK/FK/UNIQUE and the partial index. Schema design guidance:
[`04-sql-databases/04-schema-design.md`](../../04-sql-databases/04-schema-design.md).

## 13. State machines

### Job

| From | To | Trigger | Who | Side effects |
|---|---|---|---|---|
| — | `QUEUED` | Build created, deps satisfied (M6) | API | `LPUSH queue:jobs`, set `queued_at` |
| `QUEUED` | `LEASED` | `BLMOVE` returned the id; lease key written | Worker | `worker_id`, `lease_expires_at`, `leased_at` |
| `QUEUED` | `CANCELLED` | User cancel | API | `LREM queue:jobs 0 id`; steps `SKIPPED` |
| `QUEUED` | `SKIPPED` | A dependency failed (M6) | API scheduler | — |
| `LEASED` | `RUNNING` | Container started | Worker | `started_at`, `attempt` unchanged; record queue wait = `started_at − queued_at` |
| `LEASED` | `QUEUED` | Concurrency slot unavailable | Worker | `LPUSH` back (to the tail), remove lease; **do not** increment attempt |
| `LEASED` / `RUNNING` | `QUEUED` (attempt+1) | Lease expired (worker dead) and `attempt < max_attempts` | API recovery sweep | `failure_kind=INFRA` noted on the attempt; `SYSTEM` log chunk "worker X lost, retrying (2/3)"; `LPUSH` |
| `LEASED` / `RUNNING` | `FAILED` (`INFRA`) | Lease expired and attempts exhausted | API recovery sweep | notify |
| `RUNNING` | `SUCCEEDED` | All steps exit 0 | Worker | `exit_code=0`, ack, cleanup |
| `RUNNING` | `FAILED` (`APP`) | A step exits ≠ 0 | Worker | `exit_code`, ack, cleanup, **no retry** |
| `RUNNING` | `FAILED` (`INFRA`) → requeue if attempts left | Image pull failed, container create/start failed, Docker daemon unreachable | Worker | backoff `min(2^attempt × 5 s, 60 s)` via delayed requeue (sorted set) |
| `RUNNING` | `TIMED_OUT` | `timeout_seconds` elapsed | Worker | `docker kill`, cleanup, **no retry** |
| `RUNNING` | `CANCELLED` | Cancel flag observed | Worker | `docker kill`, cleanup |
| `LEASED` | `CANCELLED` | Cancel flag observed before container start | Worker | ack, no container |
| `FAILED` (`CONFIG`) | — | Config invalid | API at build creation | terminal; never queued |

Terminal states: `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `CANCELLED`, `SKIPPED`. Everything else is
non-terminal and must have a lease or be in a Redis list; the recovery sweep asserts this invariant.

### Build

| From | To | Rule |
|---|---|---|
| — | `QUEUED` | Created with ≥ 1 job |
| — | `FAILED` (`CONFIG`) | `.forgeci.yml` missing/invalid |
| `QUEUED` | `RUNNING` | First job reaches `RUNNING` |
| `QUEUED` / `RUNNING` | `CANCELLED` | User cancel: every non-terminal job cancelled |
| `RUNNING` | `SUCCEEDED` | All jobs `SUCCEEDED` |
| `RUNNING` | `FAILED` | Any job `FAILED`/`TIMED_OUT` and no job non-terminal (or fail-fast in M6: as soon as one fails, remaining queued jobs `SKIPPED`) |

Build status is **derived** from job statuses in one place (`BuildStatusResolver`) and recomputed
inside the same transaction that changes a job. Never let two code paths compute it differently.

## 14. Pipeline configuration format

Minimal (M1–M5), one implicit job:

```yaml
# .forgeci.yml
image: maven:3.9-eclipse-temurin-21
timeout: 15m            # optional, default 10m, max 60m
steps:
  - name: unit tests    # optional; defaults to "step 1", "step 2", ...
    run: mvn -q -B test
  - run: mvn -q -B package -DskipTests
```

DAG form (M6) — `jobs` map; `needs` lists job names:

```yaml
image: maven:3.9-eclipse-temurin-21   # default for all jobs
jobs:
  build:
    steps: [{ run: mvn -q -B package -DskipTests }]
  test-unit:
    needs: [build]
    steps: [{ run: mvn -q -B test }]
  test-it:
    needs: [build]
    image: maven:3.9-eclipse-temurin-21
    timeout: 20m
    steps: [{ run: mvn -q -B verify -Pintegration }]
  report:
    needs: [test-unit, test-it]
    steps: [{ run: echo all green }]
```

Rules:
- Parse with **SnakeYAML** (`org.yaml.snakeyaml:snakeyaml`, already a transitive Spring Boot dependency) using `SafeConstructor`/`LoaderOptions` — never the default constructor with untrusted input (arbitrary-type instantiation). Load into `Map`, then map into records `PipelineConfig(String image, Duration timeout, Map<String, JobConfig> jobs)` yourself, so validation messages are yours.
- Validation (all errors collected, not just the first): file present; `image` matches `^[a-z0-9._/-]+(:[a-zA-Z0-9._-]+)?(@sha256:[a-f0-9]{64})?$`; ≥ 1 step; `run` non-empty; `timeout` ≤ 60m; job names `^[a-z0-9-]{1,40}$`; `needs` reference existing jobs; no cycles (Kahn — M6); at most 20 jobs, 50 steps per job.
- A config error creates a build with `status = FAILED`, `failure_kind = CONFIG` and `error_message` containing every error, so the user sees *why* in the UI. It is never queued.
- Store both `config_yaml` (as fetched) and `config_json` (normalized) on `pipeline`.
- `run` is executed as `sh -c "<run>"` inside the container. Document that.

## 15. API design

Base path `/api`. JSON everywhere except SSE. Errors as RFC 9457 `ProblemDetail` (you did this in
FlowGrid M1). Full guidance: [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md).

| Method | Path | Auth | Purpose | Status codes |
|---|---|---|---|---|
| `POST` | `/api/webhooks/github` | HMAC only | Receive GitHub events | `200` build created / duplicate; `202` ignored event; `401` bad signature; `404` unknown repo (log, still `webhook_delivery` row) |
| `GET` | `/api/auth/github/login` | — | Redirect to GitHub OAuth authorize URL with `state` | `302` |
| `GET` | `/api/auth/github/callback` | — | Exchange `code` for token, upsert user, issue app JWT (httpOnly cookie) | `302` to UI |
| `POST` | `/api/auth/pat` | — (MVP) | Register with a PAT; validates via `GET /user` | `200` JWT |
| `GET` | `/api/me` | JWT | Current user | `200` |
| `GET` | `/api/repositories` | JWT | Registered repos of the caller | `200` |
| `POST` | `/api/repositories` | JWT | Register `{fullName}`; creates webhook (or returns secret for manual setup) | `201`, `409` already registered, `403` not admin on GitHub |
| `PATCH` | `/api/repositories/{id}` | JWT owner | Update `concurrencyLimit`, `active` | `200` |
| `DELETE` | `/api/repositories/{id}` | JWT owner | Deactivate, delete GitHub hook | `204` |
| `GET` | `/api/repositories/{id}/builds?status=&branch=&page=&size=` | JWT member | Build list, newest first | `200` page |
| `POST` | `/api/repositories/{id}/builds` | JWT member | Manual build `{branch|sha}` | `201` |
| `GET` | `/api/builds/{id}` | JWT member | Build with jobs and steps | `200` |
| `POST` | `/api/builds/{id}/cancel` | JWT member | Cancel all non-terminal jobs | `202` (async), `409` already terminal |
| `POST` | `/api/builds/{id}/retry` | JWT member | New build with `trigger=RETRY`, `retry_of_build_id` | `201` |
| `GET` | `/api/jobs/{id}` | JWT member | Job with steps, attempt, worker, timings | `200` |
| `POST` | `/api/jobs/{id}/cancel` | JWT member | Cancel one job | `202`, `409` |
| `GET` | `/api/jobs/{id}/logs?after=<seq>` | JWT member | Persisted chunks (paged) | `200` |
| `GET` | `/api/jobs/{id}/logs/stream` | JWT member (cookie or `?token=`) | **SSE** `text/event-stream`; honours `Last-Event-ID` | `200` streaming; events `log`, `status`, `end` |
| `GET` | `/api/builds/{id}/events` | JWT member | SSE of job status changes for the build page | `200` |
| `GET` | `/api/workers` | JWT | Workers with status, capacity, running jobs, last heartbeat | `200` |
| `GET` | `/api/repositories/{id}/limits` | JWT owner | Current limit + running count (from Redis) | `200` |
| `PUT` | `/api/repositories/{id}/limits` | JWT owner | `{maxRunning}` | `200` |
| `GET` | `/api/stats/queue` | JWT | Queue depth, per-state counts, p50/p95 queue wait over last hour | `200` |
| `GET` | `/actuator/health`, `/actuator/prometheus` | none / internal | Health incl. DB, Redis, (worker: Docker) | `200`/`503` |

SSE event format (each event id is the chunk `seq`):

```
id: 1042
event: log
data: {"seq":1042,"stream":"STDOUT","content":"[INFO] Tests run: 42, Failures: 0\n"}

event: status
data: {"jobId":"...","status":"SUCCEEDED","exitCode":0}

event: end
data: {}
```

**OpenAPI expectations:** springdoc-openapi generates `/v3/api-docs`; every endpoint has `@Operation`
summary, every DTO field a description, every error response documented with the `ProblemDetail`
schema; the SSE endpoint is documented with `produces = text/event-stream` and a description of the
three event types and `Last-Event-ID`. Export the JSON to `docs/api/openapi.json` in CI so the
repository always has a reviewable copy.

## 16. Package structure

```
forgeci/                          (Maven parent: <packaging>pom</packaging>, Java 21, Spring Boot 3.x BOM)
├── forgeci-common/               plain library (no @SpringBootApplication)
│   └── dev.forgeci.common
│       ├── domain/               JPA entities + enums (JobStatus, FailureKind, ...) + BuildStatusResolver
│       ├── repo/                 Spring Data repositories (used by api and worker)
│       ├── config/               PipelineConfig records, YamlPipelineParser, PipelineValidator
│       ├── queue/                JobQueue interface, RedisKeys (single place for key names), Lease record
│       ├── logs/                 LogChunk DTO, LogChannelNames
│       └── crypto/               AesGcmEncryptor (token/secret at rest)
├── forgeci-api/                  Spring Boot web app
│   └── dev.forgeci.api
│       ├── ForgeCiApiApplication
│       ├── web/                  controllers (WebhookController, BuildController, JobController, LogStreamController, WorkerController, LimitController)
│       ├── webhook/              SignatureVerifier, WebhookIngestService, GitHubEventMapper
│       ├── github/               GitHubClient (RestClient), OAuth handlers
│       ├── security/             JWT filter, GitHub OAuth config, RepoAccessEvaluator
│       ├── build/                BuildService, BuildNumberAllocator, RetryService, CancelService
│       ├── scheduler/            DependencyScheduler (M6), OrphanRecoveryJob, DeadWorkerSweeper, RequeueSweeper
│       ├── stream/               SseRegistry, LogReplayService, RedisSubscriber
│       └── stats/                QueueStatsService
├── forgeci-worker/               Spring Boot app, spring.main.web-application-type=none (+ actuator on a port)
│   └── dev.forgeci.worker
│       ├── ForgeCiWorkerApplication
│       ├── loop/                 WorkerLoop (poll → lease → execute → ack), ShutdownHook
│       ├── lease/                LeaseManager (heartbeat scheduler)
│       ├── limits/               ConcurrencySlotService (Lua)
│       ├── exec/                 DockerJobExecutor, ContainerFactory, StepRunner, TimeoutWatchdog, CancelWatcher
│       ├── workspace/            WorkspaceManager (create/clean), GitCloner
│       ├── logs/                 LogPump (chunking, seq, batch insert, PUBLISH)
│       └── health/               DockerHealthIndicator, WorkerRegistrar
├── forgeci-ui/                   Vite + React 18 + TS (not a Maven module; built by its own Dockerfile)
├── tools/                        Python component (M5) — see §16.1; its own pyproject.toml, pytest suite, README
│   └── forgeci_tools/
│       ├── repogen/              test-repository generator (.forgeci.yml variants, local git repos, optional GitHub push)
│       ├── simulator/            webhook load simulator (HMAC-signed payloads at a rate; queue-wait + completion metrics)
│       ├── analysis/             build-result / log analysis (failure-taxonomy stats, p50/p95 durations, reports)
│       └── cli.py                `forgeci-tools repogen|simulate|analyze`
├── docker/                       Dockerfiles per module, compose.yaml, compose.aws.yaml
├── docs/                         README, ARCHITECTURE, API, DATABASE, TESTING, DEPLOYMENT, SECURITY, DESIGN_DECISIONS (ADRs), PERFORMANCE, diagrams/
└── .github/workflows/            ci.yml, release.yml
```

Maven notes ([`01-java/08-maven-build.md`](../../01-java/08-maven-build.md)): the parent declares
`<modules>`; `forgeci-common` uses `spring-boot-starter-data-jpa` + `data-redis` as dependencies but
the `spring-boot-maven-plugin` runs only in `api` and `worker` (`<skip>true</skip>` in common, or
don't inherit the plugin). `mvn -pl forgeci-worker -am verify` builds the worker and its dependencies.

### 16.1 Python component — `tools/` (M5, Week 18)

The BRIEF's language strategy: Java owns the backend; every flagship project also ships **at least
one tested, typed, documented Python component**. ForgeCI's lives in `tools/` and exists because a
CI system cannot be benchmarked or failure-tested by hand — you need repositories to build, load to
apply and results to analyse. Three sub-packages, one CLI (`forgeci-tools`), Python 3.12,
`pyproject.toml` with `pytest`, `mypy --strict`, `ruff`. Prerequisites:
[`19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md),
[`19-python/python-for-java-devs.md`](../../19-python/python-for-java-devs.md).

| Sub-package | Purpose | Inputs | Outputs | Used by |
|---|---|---|---|---|
| `repogen` | Generate local git repositories containing a small Maven project and a `.forgeci.yml` **variant**: `passing`, `failing` (a test asserts false), `slow` (a step sleeps N s), `timeout` (step outruns `timeout:`), `invalid-config` (bad YAML / unknown key / bad image), and (M6) `dag` (4 jobs with `needs`). Optionally creates the repo on GitHub via the REST API (`POST /user/repos`) and pushes, so a real webhook fires | variant, output dir, optional GitHub token | a git repo path; commit SHA; optional GitHub URL | Failure-engineering scenarios, worker tests, benchmark setup |
| `simulator` | Fire **HMAC-SHA256-signed** synthetic `push` webhook payloads at the API at a configurable rate (`--rate 5/s --count 200`, `--duplicates 10%` to replay delivery ids), then poll `/api/builds/{id}` (or subscribe to SSE) until terminal; record `created_at → queued_at → started_at → finished_at` per job | API URL, repo id, webhook secret, rate, count, duplicate ratio | CSV/JSON of per-build timings; summary with **queue wait p50/p95**, jobs/min, duplicate-rejection count | Benchmarks (`docs-and-resume.md`), scenarios 1 and 13 in `failure-engineering.md` |
| `analysis` | Read persisted results — from the REST API or directly from Postgres (`psycopg`) — and report: counts by `failure_kind` (APP / INFRA / CONFIG / TIMEOUT / CANCELLED), retry counts, p50/p95/p99 of queue wait and job duration by image, log volume (chunks, bytes) per job, worker utilisation; optionally parse a job's log chunks for Maven `Tests run:` summaries | API URL or DSN, time window | Markdown/JSON report pasted into `PERFORMANCE.md` | Benchmarks, weekly retro, interview numbers |

Design rules: pure functions for everything measurable (signing, percentile, taxonomy aggregation)
so they are unit-testable without a server; I/O behind small adapters (`ApiClient`, `PgReader`)
that tests replace with fakes; `dataclasses` + type hints everywhere; `argparse`/`typer` CLI;
`README.md` in `tools/` with usage, and the exact commands used for each recorded benchmark.
The simulator's signature function **must** match the Java verifier byte for byte — a contract test
sends one signed payload to a running API and expects `200` (`test_signed_payload_accepted_by_api`).

## 17. Authentication model

**MVP (M1): Personal Access Token.** The user pastes a fine-grained PAT with `Contents: read`,
`Metadata: read` and `Webhooks: read/write` on the chosen repos. The API validates it (`GET
https://api.github.com/user`), stores it **encrypted** (AES-256-GCM, 12-byte random IV per record,
key from `FORGECI_ENCRYPTION_KEY` env var, base64 32 bytes; store IV alongside ciphertext) and
issues an app JWT. Rationale: no OAuth app registration or callback URL needed to start executing
builds on Day 2.

**Strong/Advanced: GitHub OAuth App** (Authorization Code flow):
1. `GET /api/auth/github/login` → redirect to `https://github.com/login/oauth/authorize?client_id&redirect_uri&scope=repo,read:user&state=<random, stored server-side or signed>`.
2. Callback receives `code` + `state`; verify `state` (CSRF); `POST https://github.com/login/oauth/access_token` with `client_id`, `client_secret`, `code` (header `Accept: application/json`).
3. `GET /user` → upsert `app_user`; store the OAuth token encrypted on `installation` (`token_kind = OAUTH`).
4. Issue **app JWT** (HS256 or RS256, 1 h, claims `sub = user id`, `login`), set as `httpOnly; Secure; SameSite=Lax` cookie so `EventSource` sends it automatically. Spring Security 6 filter chain from [`05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md).
5. You may use `spring-boot-starter-oauth2-client` with the built-in `github` provider — but understand the flow first (do steps 1–3 by hand once with `curl`, then let Spring do it).

**Webhook secret:** 32 random bytes (hex) generated per repository at registration, sent to GitHub
when the hook is created, stored encrypted. Signature check is `HMAC-SHA256(secret, rawBody)` compared
with `MessageDigest.isEqual` against the hex after `sha256=`. **Read the raw body** — Spring's default
JSON deserialization would re-serialize and change bytes; take `byte[]`/`String` as the parameter, or
use a `ContentCachingRequestWrapper`.

**Worker → API/Redis/DB:** workers are internal; they authenticate to Redis with a password
(`requirepass`) and to Postgres with their own DB role limited to the tables they write (`job`,
`step`, `log_chunk`, `worker`, `audit_event`). No worker endpoint is exposed.

**Clone token:** the worker needs a token to `git clone` private repos. Use the repository's stored
token, decrypted by the worker, passed to git via `GIT_ASKPASS`/credential helper or as
`https://x-access-token:<token>@github.com/owner/repo.git` — and **never** printed: mask it in the log
pump (`content.replace(token, "***")`) and in the `SYSTEM` chunk that records the clone command.

## 18. Authorization model

| Resource | Rule |
|---|---|
| Register repository | Caller must be `admin` on GitHub for that repo (`GET /repos/{owner}/{repo}` → `permissions.admin`) |
| Read builds/jobs/logs | Caller is the registering owner **or** has GitHub `push`/`pull` permission on the repo, checked on first access and cached 5 min in Redis (`authz:<userId>:<repoId>`) |
| Cancel / retry / manual build | Same as read (any member) — record `actor` in `audit_event` |
| Change limits / deactivate | Owner only |
| Workers / queue stats | Any logged-in user (single-tenant deployment); document that a multi-tenant product would scope these |
| Webhook endpoint | No user; authenticated by HMAC per repository — the repo is identified from the payload (`repository.id`), the secret looked up, then verified. An unknown repo → `404` after logging the delivery |

Implement as a `RepoAccessEvaluator` bean used from `@PreAuthorize("@repoAccess.canRead(#buildId)")`
on controllers; unit-test it with Mockito, slice-test one controller with `@WebMvcTest`.

## 19. Docker execution model

Read [`11-docker/README.md`](../../11-docker/README.md) and
[`14-cs-fundamentals/operating-systems.md`](../../14-cs-fundamentals/operating-systems.md)
(processes, signals, namespaces, cgroups) before M2.

| Concern | Decision | Notes |
|---|---|---|
| Client | **docker-java** (`com.github.docker-java:docker-java-core` + `docker-java-transport-httpclient5`) | Talks to `unix:///var/run/docker.sock` locally or `tcp://` with TLS on AWS. One `DockerClient` bean per worker, connection pool sized to capacity + 2 |
| Image pull policy | `if-not-present` by default; `always` if the tag is `latest` or config says `pull: always` | Pull with a 5-min timeout and a per-worker mutex per image name (two jobs pulling the same image simultaneously wastes bandwidth). Pull failures = `INFRA` (retryable) unless `404 manifest unknown` = `CONFIG` |
| Workspace | Host directory `${forgeci.workspaces}/<jobId>-<attempt>` **bind-mounted** at `/workspace` | Simpler than a named volume to clean and inspect; on AWS the worker EC2 has a dedicated EBS volume. Set `WorkingDir=/workspace` |
| Clone | **On the host, before starting the container**, using the `git` CLI via `ProcessBuilder` with a 2-min timeout, `--depth 1`, then `git checkout <sha>` | Avoids requiring git in every user image and keeps the token out of the container. Document the alternative (clone inside container) and its trade-off (token exposed to user code) |
| Env vars | `CI=true`, `FORGECI=true`, `FORGECI_BUILD_ID`, `FORGECI_JOB_ID`, `FORGECI_COMMIT_SHA`, `FORGECI_BRANCH`, `FORGECI_BUILD_NUMBER` | Never the GitHub token. Stretch: per-repo secrets |
| Resource limits | `HostConfig`: `withMemory(2 GiB)`, `withMemorySwap(same)` (no swap), `withNanoCPUs(2_000_000_000L)` (2 CPUs) or `withCpuQuota/Period`, `withPidsLimit(512L)`, `withUlimits(nofile 4096)` | Values configurable per worker; per-job requests capped by worker maximum |
| Network policy | Default bridge network (user code needs to download dependencies). Stretch: a dedicated `forgeci-jobs` network with no access to the control-plane containers; on AWS a security group that blocks the metadata endpoint `169.254.169.254` | Never `--network host` |
| Security options | `withPrivileged(false)` (explicit), `withCapDrop(ALL)` + add back only `CHOWN, SETUID, SETGID, DAC_OVERRIDE` if the image needs them, `withSecurityOpts(no-new-privileges:true)`, `withReadonlyRootfs(false)` (Maven writes to `~/.m2`), run as the image's user | Document each flag in `docs/SECURITY.md` |
| Running steps | One container per job (started with `sleep infinity` or `tail -f /dev/null` as the main process); each step = `docker exec` (`ExecCreateCmd` with `sh -c`, `attachStdout/Stderr`), wait, read exit code via `inspectExec` | One container per step would lose files between steps; `exec` keeps the workspace warm |
| Log capture | `ExecStartCmd.exec(ResultCallback)` with `Frame` → bytes → chunked by the `LogPump` (see §27) | Frames tag `STDOUT`/`STDERR`; keep them separate |
| Timeout | `TimeoutWatchdog` (`ScheduledExecutorService`) fires at deadline: `killContainer` (SIGKILL) and marks `TIMED_OUT`; the step reader unblocks with an EOF | Use `docker stop` (SIGTERM then SIGKILL after 10 s) for cancel, `kill` for timeout |
| Cleanup | `finally { stop/kill; removeContainer(force=true, removeVolumes=true); deleteWorkspace(); releaseSlot(); }` — each guarded so one failure does not skip the next | Also a startup sweep: remove containers labelled `forgeci.job=*` that no live job owns (leftovers from a `kill -9`) |
| Labels | `forgeci.job=<jobId>`, `forgeci.attempt`, `forgeci.worker=<workerId>` | Makes `docker ps --filter label=forgeci.job` and sweeps possible |

**Security caveat — untrusted code.** Any registered repo can run arbitrary commands. A container is
a *process isolation* boundary (namespaces + cgroups + seccomp), not a *virtualization* boundary: a
kernel exploit escapes it. Worse, if the Docker socket were mounted into the *job* container (never do
this) the job would own the host. In ForgeCI the socket is mounted only into the **worker** container,
which means the worker process is root-equivalent on its host — accept this for a single-tenant learning
deployment, put the worker on its own EC2 instance with nothing else on it, and write it down in
`docs/SECURITY.md`. What a real product does: gVisor (`runsc` runtime — user-space kernel), Kata
Containers or Firecracker microVMs per job (what GitHub-hosted runners and AWS Lambda use), rootless
Docker, and network egress allow-lists. **Awareness only** — don't implement.

## 20. Testing strategy

Guides: [`09-testing/README.md`](../../09-testing/README.md), [`09-testing/testcontainers.md`](../../09-testing/testcontainers.md).

| Layer | Tool | What | Where it runs |
|---|---|---|---|
| Unit | JUnit 5 + Mockito + AssertJ | `SignatureVerifier`, `YamlPipelineParser`/`PipelineValidator` (every rule, every error message), `BuildStatusResolver`, `RetryPolicy` (failure kind → retry?), `LogChunker` (boundaries, UTF-8 split safety), Kahn `DagScheduler` (M6), `AesGcmEncryptor` round-trip | Every `mvn test`, < 30 s |
| Web slice | `@WebMvcTest` + `MockMvc` | Webhook endpoint (raw body, 401/200/202/404), `ProblemDetail` shape, `@PreAuthorize` rules, SSE endpoint content type + `Last-Event-ID` handling with a mocked replay service | Every `mvn test` |
| Integration | `@SpringBootTest` + **Testcontainers** `PostgreSQLContainer` + `GenericContainer("redis:7")` | Webhook → build → job rows; dedupe under concurrency (10 threads same delivery id → 1 build); enqueue after commit; recovery sweep moves expired leases; concurrency-limit Lua; SSE replay contract (persist 100 chunks, connect with `Last-Event-ID: 40`, receive 41..100 then live) | `mvn verify` (failsafe, `*IT`), CI |
| Worker + real Docker | JUnit tag `@Tag("docker")`, enabled by `-Pdocker` profile or `FORGECI_DOCKER_TESTS=true`; skipped when `/var/run/docker.sock` is absent (`assumeTrue`) | Run `alpine` job with `echo hi` → `SUCCEEDED`, chunk content; `exit 3` → `FAILED`, `exit_code=3`, no retry; `sleep 60` with 2-s timeout → `TIMED_OUT`, container gone; cancel mid-run → `CANCELLED`, container gone; image `does-not-exist:1` → `CONFIG`; workspace deleted in all cases | Locally and in CI (GitHub-hosted runners have Docker) |
| Concurrency | Integration tests with `ExecutorService` + `CountDownLatch` | `queue_twoWorkersOneJob_executesOnce` (two `WorkerLoop` instances with a fake executor counting executions); `limit_thirdConcurrentBuild_staysQueued`; orphan recovery with a worker whose heartbeat is stopped | `mvn verify` |
| Contract | HTTP-level tests with `WebTestClient`/`RestClient` against the running app | SSE replay contract, OpenAPI snapshot diff (fail CI if `openapi.json` changed without being committed) | CI |
| Failure engineering | Manual + scripted (`scripts/chaos/*.sh`) | The 16 scenarios in [`failure-engineering.md`](./failure-engineering.md), each with a regression test where automatable | M5 |
| Frontend | Vitest + React Testing Library | Log viewer appends chunks in order and dedupes by seq; status badge mapping; `EventSource` mocked | `npm test` in CI |
| Python `tools/` | **pytest** + `mypy --strict` + `ruff`; `responses`/`httpx.MockTransport` for HTTP fakes | `repogen`: every variant produces a repo with a valid/invalid `.forgeci.yml` as intended and a clean `git log`; `simulator`: `sign()` matches a known HMAC vector, rate limiter honours `--rate`, duplicate ids are reused at the requested ratio, percentile math against known arrays; `analysis`: taxonomy aggregation and p50/p95 from fixture JSON; **contract**: `test_signed_payload_accepted_by_api` against a live API (skipped unless `FORGECI_API_URL` set) | `pytest tools/` in CI (separate job with `actions/setup-python`) |

Coverage target: `forgeci-common` ≥ 85 % lines, api/worker ≥ 70 % — measured with JaCoCo, reported,
not gamed. Named test list with assertions is in each milestone of [`milestones.md`](./milestones.md).

## 21. Docker / Compose strategy

`docker/compose.yaml` services:

| Service | Image | Notes |
|---|---|---|
| `postgres` | `postgres:16` | Volume `pgdata`; healthcheck `pg_isready` |
| `redis` | `redis:7` | `--requirepass`, `--appendonly yes` (so queue survives restart), healthcheck `redis-cli ping` |
| `api` | built from `docker/api.Dockerfile` (multi-stage: `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`) | `depends_on` with `condition: service_healthy`; env for DB, Redis, encryption key, GitHub client id/secret, public URL |
| `worker` | `docker/worker.Dockerfile` | `deploy.replicas: 2` (or `worker-1`, `worker-2`); mounts **`/var/run/docker.sock:/var/run/docker.sock`** and a host `./workspaces:/var/forgeci/workspaces` (the *same host path* must be used inside the worker and in the bind-mount it asks Docker to create — Docker resolves bind paths on the host, not in the worker container; set `FORGECI_WORKSPACES_HOST_PATH`) |
| `ui` | `docker/ui.Dockerfile` (`node:20` build → `nginx:alpine`) | nginx proxies `/api` to `api:8080` with `proxy_buffering off;` for SSE |

> **Warning (repeat it in the README):** mounting the Docker socket into the worker container gives
> that container root-equivalent control of the host. Do it only on a machine you own and for this
> learning deployment; on AWS put workers on a dedicated instance. See §19 and §26.

`docker compose up --scale worker=4` demonstrates horizontal scaling. `compose.test.yaml` overrides
for the failure-engineering runs (named workers so `docker kill forgeci-worker-1` is deterministic).
Guide: [`11-docker/compose.md`](../../11-docker/compose.md).

## 22. CI/CD strategy

`.github/workflows/ci.yml` on every push/PR ([`13-cicd/github-actions.md`](../../13-cicd/github-actions.md)):

1. `actions/checkout`, `actions/setup-java` (Temurin 21, Maven cache).
2. `mvn -B -ntp verify` — unit + Testcontainers integration tests (`ubuntu-latest` has Docker, so Testcontainers and the `docker`-tagged worker tests both run) with JaCoCo report as artifact.
3. `npm ci && npm test && npm run build` in `forgeci-ui`.
3b. Python job: `actions/setup-python` (3.12), `pip install -e tools/[dev]`, `ruff check`, `mypy --strict`, `pytest tools/` (the live-API contract test is skipped here; it runs in the Compose-based failure-engineering workflow).
4. OpenAPI export + diff check.
5. On `main`: build **three images** (`forgeci-api`, `forgeci-worker`, `forgeci-ui`) with `docker/build-push-action`, tag `sha-<short>` and `latest`, push to **GHCR** (`ghcr.io/<you>/forgeci-api`), `permissions: packages: write`.
6. `release.yml` on tag `v*`: same images tagged with the version; a job that SSHes to the API EC2 and runs `docker compose pull && up -d` (M6).

Job matrix not needed. Cache Maven (`~/.m2`) and Docker layers (`cache-from: type=gha`).
Examples: [`13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md).

## 23. AWS deployment plan

Keep cost near zero ([`12-aws/cost-safety.md`](../../12-aws/cost-safety.md)); tear down after screenshots.

| Component | Choice | Notes |
|---|---|---|
| API + UI | one `t3.small` EC2, Docker Compose (`api`, `ui`, `redis`) | Public IP / Elastic IP; security group: 80/443 from anywhere, 22 from your IP; Redis port **not** public |
| Workers | one `t3.medium` EC2 with Docker, Compose running `worker` ×2 | Security group: no inbound; outbound to GitHub, RDS, Redis on the API host (private IP / same VPC); attach a 30 GB EBS for workspaces |
| Database | RDS PostgreSQL 16 `db.t3.micro`, private subnet, SG allows 5432 from API + worker SGs only | [`12-aws/rds.md`](../../12-aws/rds.md) |
| Redis | Redis on the API EC2 (Compose) with `requirepass` and `bind` to the private IP; ElastiCache only if cheaper for your account | Note the single point of failure in `docs/DEPLOYMENT.md` |
| Secrets | SSM Parameter Store (SecureString) for encryption key, GitHub secret, DB password, Redis password; an IAM instance role with `ssm:GetParameter` on `/forgeci/*` only | [`12-aws/iam.md`](../../12-aws/iam.md) |
| Logs / metrics | CloudWatch agent shipping `docker logs` of api and worker; alarm on "no worker heartbeat in 2 min" (custom metric) and on API 5xx | [`12-aws/cloudwatch.md`](../../12-aws/cloudwatch.md) |
| Artifacts (stretch) | S3 bucket with presigned URLs | |
| Queue on AWS | **Option evaluated in M6:** replace the Redis list with SQS (standard queue, visibility timeout 60 s, `ChangeMessageVisibility` heartbeat, DLQ after `maxReceiveCount = 3`). Implement `SqsJobQueue` behind the `JobQueue` interface if time allows; otherwise write the ADR with the comparison table of §9 | pub/sub and limits stay on Redis |
| Webhook URL | `https://<domain-or-ip>/api/webhooks/github` — GitHub requires a public endpoint; for local dev use `smee.io` or `ngrok` to forward | |

Walkthrough: [`12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md).

## 24. Logging strategy

- **Application logs** (API, worker): Logback JSON (`logstash-logback-encoder`) in containers, plain in dev. `MDC` keys `build_id`, `job_id`, `worker_id`, `delivery_id`, `request_id` set at the entry point of each unit of work (webhook filter, worker loop iteration, SSE subscription) and **cleared in `finally`** — MDC is thread-local and worker threads are reused. Structured logging guidance: [`05-spring-boot/06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md).
- **Job output is data, not logs.** It goes to `log_chunk` and Redis pub/sub — never to the worker's logger (it would be huge, unstructured, and include user secrets). The worker logs *about* it: "job X: 1,204 chunks, 3.1 MB, 41 s".
- Levels: `INFO` for state transitions (one line per transition with from/to/reason), `WARN` for retries and recoveries, `ERROR` only for infra failures needing attention. No `ERROR` for a user's failing test.
- Never log: tokens, webhook secrets, raw webhook bodies (log `delivery_id`, `event`, `repository.full_name`, body hash).
- Redaction: a Logback `MaskingPatternLayout` or a `LogPump` filter that masks the clone token in job output.
- Metrics (Micrometer → `/actuator/prometheus`): `forgeci_queue_depth` (gauge from `LLEN`), `forgeci_jobs_total{status}`, `forgeci_queue_wait_seconds` (timer/histogram), `forgeci_job_duration_seconds`, `forgeci_log_chunks_total`, `forgeci_sse_clients` (gauge), `forgeci_workers_alive`.

## 25. Error-handling strategy and failure taxonomy

| Failure kind | Examples | Job state | Retry? | Build effect | Notification / UI |
|---|---|---|---|---|---|
| **APP** (user's code) | A step exits ≠ 0; tests fail; compile error | `FAILED`, `exit_code` recorded, remaining steps `SKIPPED` | **No** — rerunning the same code gives the same result | `FAILED` | Red badge, "Step 2 failed with exit code 1", link to log |
| **INFRA** (ours) | Image pull network error, container create/start error, Docker daemon unreachable, worker died (lease expired), Redis connection lost mid-job, workspace disk full | Attempt marked `INFRA`; job back to `QUEUED` with `attempt+1` after backoff; after `max_attempts` → `FAILED` (`INFRA`) | **Yes**, bounded (3), backoff 5 s / 10 s / 20 s | `FAILED` only after exhaustion | Yellow "retrying (2/3): worker lost" `SYSTEM` chunk; `WARN` log; alarm if rate spikes |
| **CONFIG** (user's config) | `.forgeci.yml` missing/invalid, unknown image (`manifest unknown`), `needs` cycle | `FAILED` (`CONFIG`) at creation, never queued (or on first attempt for image 404) | **No** | `FAILED` | Show all validation errors verbatim |
| **TIMED_OUT** | Wall-clock exceeded | `TIMED_OUT` | **No** (a hung build will hang again) | `FAILED` | "Exceeded 10m timeout; container killed" |
| **CANCELLED** | User action | `CANCELLED` | No | `CANCELLED` | Grey badge, actor in audit |
| **SKIPPED** (M6) | Dependency failed | `SKIPPED` | n/a | part of `FAILED` build | Grey, "skipped: needs build" |

Rules: (1) the worker decides `APP` vs `INFRA` at the point of failure and records it — the recovery
sweep only ever produces `INFRA`; (2) every exception in the executor is caught and classified — an
unclassified `RuntimeException` is `INFRA` (fail safe: retry) but logged at `ERROR` so you find the
missing branch; (3) `finally` cleanup must never throw (log and continue); (4) API errors are
`ProblemDetail` with a `type` URI per class (`/errors/duplicate-delivery`, `/errors/invalid-signature`,
`/errors/build-terminal`).

## 26. Security considerations

- **Webhook**: HMAC over raw body, constant-time compare, reject missing header, limit body to 1 MB, rate-limit per IP (Bucket4j or a Redis token bucket you already wrote for FlowGrid/LedgerX), never trust `repository.full_name` for authorization — look up by `repository.id` (numeric) and verify with *that* repo's secret.
- **Tokens at rest**: AES-256-GCM, key outside DB (env/SSM), IV per record, key rotation procedure documented (`rotated_at`). Decrypt only in the process that needs it; the API never sends tokens to the UI.
- **JWT**: short-lived, httpOnly cookie, `SameSite=Lax`, CSRF token for state-changing requests (or use `SameSite=Strict` + bearer header for non-SSE calls).
- **OAuth**: verify `state`; only request scopes you use.
- **Docker socket**: only in workers; workers on a dedicated host; documented warning; never in job containers; `no-new-privileges`, `cap-drop ALL`, no `--privileged`, memory/CPU/pids limits, no host network, 60-min hard max timeout.
- **Untrusted code**: assume every job is hostile; it cannot reach Redis/Postgres because those are bound to private IPs and firewalled from the job network; it cannot see the GitHub token (clone on host); it cannot fill the disk forever (workspace quota via cleanup + a `df` check before start).
- **Log injection**: log viewer renders text, never HTML (React escapes by default; do not `dangerouslySetInnerHTML`); ANSI sequences stripped or rendered by a safe library.
- **Dependencies**: Dependabot on Maven and npm; `mvn dependency-check` optional.
- **Redis**: `requirepass`, not exposed publicly, `rename-command FLUSHALL ""` in prod config.
- **Least privilege DB roles**: api role vs worker role.
- **Mass assignment / IDOR**: every `/builds/{id}` checks repo membership; never trust ids in bodies.
- Checklist for the Week 24 review: OWASP API Security Top 10 — map each item to ForgeCI in `docs/SECURITY.md`.

## 27. Performance considerations

- **How it is measured**: with the Python simulator (§16.1) — `forgeci-tools simulate --rate 2/s --count 100` against the Compose stack with 1, 2 and 4 workers, then `forgeci-tools analyze --since 30m` for p50/p95 queue wait, jobs/min and chunks/s. Protocol and report template in [`docs-and-resume.md`](./docs-and-resume.md).
- **Queue latency** (`QUEUED → RUNNING`): dominated by `BLMOVE` block timeout (≤ 5 s wake-up when idle — actually immediate, since `BLMOVE` returns as soon as an element arrives), image presence (pre-pull common images on worker start), clone time (`--depth 1`), and container start (~100–300 ms). Measure p50/p95 and put the numbers in `PERFORMANCE.md`.
- **Log chunk batching**: the worker's `LogPump` buffers frames and flushes a chunk when either 4 KB accumulate or 200 ms elapse — never per line. Each flush = one row + one `PUBLISH`. This caps DB write rate at ~5 rows/s per job even for very chatty output. Publish the chunk *after* commit (or accept the tiny window and rely on replay — document which).
- **DB write amplification for logs**: 1,000 jobs/day × 500 chunks = 500k rows/day; fine for Postgres with the `(job_id, seq)` PK. Use JDBC batch inserts (`spring.jpa.properties.hibernate.jdbc.batch_size=50`) or plain `JdbcTemplate.batchUpdate` for chunks (JPA adds no value there). Retention: delete chunks older than 30 days (scheduled) — or move to S3 (§28).
- **Indexes**: §12 — verify with `EXPLAIN (ANALYZE, BUFFERS)` that the build list and replay queries use them ([`04-sql-databases/05-indexes-performance.md`](../../04-sql-databases/05-indexes-performance.md)).
- **SSE fan-out**: one Redis subscription per *job* (not per client): `SseRegistry` maps `jobId → Set<SseEmitter>`; subscribe on first client, unsubscribe on last. Sending to a slow client blocks the publishing thread — send from a dedicated executor with a per-client bounded queue; drop the client on overflow (it will reconnect and replay).
- **Worker capacity**: default 2 concurrent jobs per worker (each job pins ~2 CPUs); executor thread pool = capacity; the `BLMOVE` loop runs only when a slot is free (semaphore) so a worker never leases more than it can run.
- **JVM**: worker heap small (`-Xmx512m`) — the heavy lifting is in containers; use `-XX:+UseContainerSupport` (default) so cgroup limits are respected.
- **Virtual threads** (Java 21): reasonable for the API's SSE handling and the worker's per-job blocking I/O (`spring.threads.virtual.enabled=true`); note that `BLMOVE` via Lettuce is non-blocking underneath anyway. Try it and measure; don't claim gains you didn't measure.

## 28. Scalability

| Dimension | Now | Next step | At scale |
|---|---|---|---|
| Throughput | 2 workers × 2 jobs | Add workers (`--scale worker=N`); bigger hosts | Autoscale workers on queue depth (CloudWatch alarm → ASG); SQS instead of Redis list |
| Per-project fairness | Per-repo concurrency limit | Priority queues (PR builds before scheduled) | Weighted fair queuing per tenant |
| Logs | Postgres `log_chunk` | Partition `log_chunk` by month (`PARTITION BY RANGE (created_at)`) so retention = `DROP PARTITION` | Stream chunks to S3 objects per job after completion; keep only the last N KB hot in Postgres; serve archives via presigned URL |
| Redis | Single instance (`appendonly`) | Redis Sentinel for failover; `min-replicas-to-write` | Redis Cluster or managed (ElastiCache) — note pub/sub is not cluster-sharded the same way (`SSUBSCRIBE` in 7.0) |
| API | Single instance | N instances behind ALB; SSE works because each instance subscribes to Redis and replay reads Postgres (no sticky sessions needed) | Separate the SSE gateway from the REST API |
| Postgres | Single RDS | Read replica for build lists; connection pooling (HikariCP sized per instance; PgBouncer) | Archive old builds |
| Docker hosts | Socket-mounted workers | Dedicated hosts, image pre-warming, registry mirror (Docker Hub rate limits!) | Firecracker/gVisor per job; Kubernetes Jobs |

## 29. Git strategy, issues, PR workflow

- Repo `forgeci` (single repo, multi-module). `main` protected: PR + green CI required. Branches `feat/m1-webhook-verify`, `fix/...`, `docs/...`. Conventional commit messages.
- One **GitHub Milestone per M1–M6**; issues created *before* each week from the "Now implement it" task lists in [`milestones.md`](./milestones.md) (one issue per task, labels `area:api`, `area:worker`, `area:ui`, `area:infra`, `type:test`, `type:docs`).
- PR template: what/why, how tested (paste test names), screenshots for UI, checklist (tests, docs, ADR if a decision was made). Self-review every PR line by line before merging; squash-merge.
- Tags: `v0.1-mvp` (end M2), `v1.0` (end M5), `v1.1` (end M6).
- ADRs in `docs/DESIGN_DECISIONS.md` using [`../templates/adr.md`](../templates/adr.md): ADR-001 pull-based workers, ADR-002 Redis list queue vs Streams vs SQS, ADR-003 SSE vs WebSockets, ADR-004 clone on host vs in container, ADR-005 one container per job with `exec` per step, ADR-006 Postgres as source of truth / Redis as transport, ADR-007 failure taxonomy and retry policy, ADR-008 DAG scheduling in API vs worker.
- Workflow reference: [`02-git/workflows.md`](../../02-git/workflows.md).

## 30. README and demo requirements

**Project README** (in the ForgeCI repo) must contain: one-paragraph pitch; architecture diagram;
"what is interesting here" list (5 bullets: reliable queue, isolation, SSE replay, failure taxonomy,
DAG); quick start (`docker compose up`, register a repo, push, watch); `.forgeci.yml` reference; the
Docker-socket warning; link to every doc in `docs/`; measured numbers with a link to `PERFORMANCE.md`;
scope tier reached and tags; "what I would do next".

**Demo**:
- A public **demo repository** (e.g. `forgeci-demo-maven`) with a small Maven project (a couple of unit tests, one intentionally slow test behind a profile) and a `.forgeci.yml`. Pushes to it are what your screenshots and benchmark show. A second branch with a failing test demonstrates `APP` failure; a branch with a bad image demonstrates `CONFIG`; M6 uses a `needs:` graph with 4 jobs. Generate it with `forgeci-tools repogen --variant passing --github` so the demo repo and the test fixtures come from the same code.
- Screenshots in `docs/screenshots/`: build list with mixed statuses; **job detail with live log mid-run** (visible partial Maven output and a spinning status); cancelled job; retried-after-worker-loss job showing the `SYSTEM` chunk; workers page with one `DEAD` worker; DAG build view.
- A 60–90 s screen recording (GIF or link) of push → queued → running → live log → green.

## 31. Prerequisite topic map

| Topic | File | Needed by |
|---|---|---|
| Processes, signals, exit codes, permissions | [`10-linux/README.md`](../../10-linux/README.md), [`10-linux/commands.md`](../../10-linux/commands.md), [`10-linux/bash-scripting.md`](../../10-linux/bash-scripting.md) | M2 (why `exit 3` matters, SIGTERM vs SIGKILL, `sh -c`) |
| OS: namespaces, cgroups, scheduling | [`14-cs-fundamentals/operating-systems.md`](../../14-cs-fundamentals/operating-systems.md) | M2 (what a container is), M4 (limits) |
| Docker images, layers, networking, Engine API | [`11-docker/README.md`](../../11-docker/README.md), [`11-docker/dockerfiles.md`](../../11-docker/dockerfiles.md), [`11-docker/compose.md`](../../11-docker/compose.md), [`11-docker/exercises.md`](../../11-docker/exercises.md) | M2, M5 |
| Redis lists, Lua, pub/sub, Streams, TTL | [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md) | M2, M3, M4 |
| Java concurrency: executors, `CompletableFuture`, locks, atomics, virtual threads | [`01-java/07-concurrency.md`](../../01-java/07-concurrency.md), [`14-cs-fundamentals/concurrency.md`](../../14-cs-fundamentals/concurrency.md) | M2, M4 |
| Networking: TCP, HTTP streaming, proxies | [`14-cs-fundamentals/networking.md`](../../14-cs-fundamentals/networking.md), [`06-rest-apis/http-for-apis.md`](../../06-rest-apis/http-for-apis.md) | M1, M3 |
| Spring Security, JWT, OAuth | [`05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md) | M1 |
| Scheduling, caching in Spring | [`05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md) | M4 (sweeps) |
| Testcontainers, Spring testing | [`09-testing/testcontainers.md`](../../09-testing/testcontainers.md), [`09-testing/spring-testing.md`](../../09-testing/spring-testing.md) | M2, M5 |
| React data-heavy views, `EventSource` | [`08-react/02-hooks.md`](../../08-react/02-hooks.md), [`08-react/04-api-integration-auth.md`](../../08-react/04-api-integration-auth.md), [`07-javascript-typescript/02-async-javascript.md`](../../07-javascript-typescript/02-async-javascript.md) | M3 |
| GitHub Actions, images to GHCR | [`13-cicd/github-actions.md`](../../13-cicd/github-actions.md), [`13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md) | M1 (CI), M5 |
| AWS IAM/EC2/RDS/CloudWatch | [`12-aws/README.md`](../../12-aws/README.md), [`12-aws/ec2.md`](../../12-aws/ec2.md), [`12-aws/rds.md`](../../12-aws/rds.md), [`12-aws/iam.md`](../../12-aws/iam.md), [`12-aws/cloudwatch.md`](../../12-aws/cloudwatch.md) | M6 |
| Topological sort | [`03-dsa/16-topological-sort.md`](../../03-dsa/16-topological-sort.md) | M6 |
| Python scripting, pytest, type hints, `subprocess`, `hmac`, `statistics` | [`19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md), [`19-python/python-for-java-devs.md`](../../19-python/python-for-java-devs.md), [`19-python/01-python-core.md`](../../19-python/01-python-core.md) | M5 `tools/` |
| Queues and scalability concepts | [`15-system-design/scalability.md`](../../15-system-design/scalability.md), [`15-system-design/fundamentals.md`](../../15-system-design/fundamentals.md) | M2, interviews |
| Reading unfamiliar library code (docker-java, GitHub API) | [`21-debugging-code-reading/method.md`](../../21-debugging-code-reading/method.md) | M2 |
| Deep-dive rehearsal | [`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md) | Week 19 |

Weekly plans: [`weeks/week-14`](../../weeks/week-14/README.md) · [`week-15`](../../weeks/week-15/README.md) · [`week-16`](../../weeks/week-16/README.md) · [`week-17`](../../weeks/week-17/README.md) · [`week-18`](../../weeks/week-18/README.md) · [`week-19`](../../weeks/week-19/README.md). Checkpoints: [CP-16](../../checkpoints/checkpoint-16.md), [CP-20](../../checkpoints/checkpoint-20.md). Overview: [`PROJECTS.md`](../../PROJECTS.md); résumé use: [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md).
