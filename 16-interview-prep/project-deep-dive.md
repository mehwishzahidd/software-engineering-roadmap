# Project Deep-Dive

"Walk me through a project you've built" can last 5 minutes or an entire 45-minute round.
The interviewer keeps drilling until they find the edge of your understanding. The goal:
**that edge should be far away**, and when you reach it, you say so honestly and reason
from what you know. CP-24's gate is a **15-minute deep-dive without notes**.

Each project has its own `interview-questions.md` and `docs-and-resume.md` — this file gives
the common framework, the layered versions, and the drill-down questions to rehearse:
[FlowGrid](../18-projects/flowgrid/README.md) ·
[LedgerX](../18-projects/ledgerx/README.md) ·
[ForgeCI](../18-projects/forgeci/README.md) ·
[FlagForge](../18-projects/flagforge/README.md)

**Rehearsal schedule (ROADMAP §10):** end of each project — **W8 FlowGrid · W13 LedgerX · W19 ForgeCI · W23 FlagForge** — then all four in Weeks 24–26.

---

## 1. Framing rules

- **Say what it is:** "a personal project I built over five weeks as part of a structured return to engineering." Never imply it was paid work, had real customers, or ran at a scale it didn't.
- **Numbers only from your own measurements**, with the setup stated: "My k6 run against a single t3.small with Postgres on RDS gave p95 of X ms at Y orders/s." No invented user counts. `PERFORMANCE.md` in each project is the source.
- **Own every line.** If you used a library (docker-java, springdoc, Testcontainers), know what it does for you. If you followed a guide for part of it, you must still be able to explain and modify it.
- **Scope tiers are your friend.** Say which tier you shipped: "The Strong Résumé Version is tagged v1.0; the DAG scheduler was the Advanced feature I added after."
- **Two languages, stated plainly.** "The backend is Java/Spring Boot; the load harness and verifier are Python." Never present the Python tooling as the product, and never let a Track B interviewer think Python is your backend language.
- **"I don't know" is allowed** — follow it with reasoning: "I haven't measured that. I'd expect X because Y, and I'd verify it by Z."

---

## 2. The layered explanation

Prepare every project at three zoom levels. Start at 30 seconds; go deeper only when asked.

| Layer | When | Content |
|---|---|---|
| **30 seconds** | Intro, recruiter screen, "what's on your GitHub?" | What it is · who it's for · stack · the one hard problem you solved |
| **2 minutes** | "Tell me about a project" | Problem → architecture in one breath → 1–2 key decisions with trade-offs → how you proved it works → what you'd do next |
| **10–15 minutes** | Dedicated deep-dive round (Track B) | Diagram → data model → one request end-to-end → hardest problem in detail → testing/failure engineering → deployment → trade-offs & limitations → what you learned |

### 10–15-minute skeleton (draw while talking)

```
1. Goal & scope (tier shipped) .............................. 1 min
2. Architecture diagram (boxes + arrows, left → right) ...... 2 min
3. Data model: 3–5 core tables, key constraints ............. 1 min
4. One request end-to-end (client → DB → response) .......... 2 min
5. The hardest problem: symptom → options → choice → proof .. 2 min
6. Testing + failure engineering + CI + deploy .............. 1 min
7. Python tooling: what it proves, how it's tested ........... 1 min
8. Limitations, what I'd change, what I learned ............. 1 min
```

The extra 5 minutes in a 15-minute round go to steps 5 and 7 — interviewers push hardest on "how do you *know* it works".

### Code-tour readiness

Interviewers may say "share your screen and show me". Before any interview:
- [ ] Repo open in IDE; bookmarks on: entry point, the hardest class, the most important test, the migrations, the CI workflow.
- [ ] App runnable locally in one command (`docker compose up`).
- [ ] `ARCHITECTURE.md` diagram and `PERFORMANCE.md` numbers open in a tab.
- [ ] You can find any file in < 10 seconds.

---

## 3. FlowGrid — multi-warehouse fulfillment & inventory platform (Weeks 4–8)

**30 seconds**
> "FlowGrid is a multi-warehouse inventory and order-fulfillment platform: Spring Boot, PostgreSQL, Redis, a React/TypeScript operations dashboard, deployed on AWS. Inventory moves through states — available, reserved, allocated, picked, shipped — and the hard part was making order creation idempotent and reservations safe under concurrency: I proved with a multi-threaded test that N simultaneous orders for one remaining unit produce exactly one reservation."

**2 minutes — outline**
1. Domain: warehouses, products/SKUs, per-warehouse inventory levels by state, orders, reservations, allocations, pick lists, shipments, returns, transfers, audit events; roles ADMIN / OPS_MANAGER / WAREHOUSE_ASSOCIATE / VIEWER.
2. Layers: controller → service → Spring Data JPA → PostgreSQL (Flyway); Redis cache-aside for the catalog; scheduled low-stock alerts.
3. Decision 1: `Idempotency-Key` stored with a request hash and the response, with TTL — a retry returns the same order; the same key with a different body is a 409/422.
4. Decision 2: `SELECT … FOR UPDATE` on the inventory row for reservations, compared with `@Version` — and why you chose one.
5. Decision 3: deterministic allocation scoring (availability, capacity, region match, workload, priority; tie-break by id) so the same input always allocates the same way — testable.
6. Proof: N-threads-one-unit test on Testcontainers; k6 baseline for order creation; CI/CD to EC2 + RDS.
7. **Python tooling (`tools/`, M4–M5):** a synthetic inventory & order generator (realistic SKUs, warehouses, order mix; writes via API or SQL) and a load/simulation harness that drives concurrent order creation and reports latency and reservation contention — pytest-tested, used by the benchmark protocol and the failure exercises.

**10 minutes — emphasize:** the inventory state machine and which transitions are allowed; the exact SQL of the reservation transaction; the idempotency table and its lookup path; the allocation algorithm on a 3-warehouse example; what happens when Redis is down (degrade to DB); the two-phase stock transfer (`in_transit`); the AWS topology and IAM.

**Drill-down questions**

| Question | Key points |
|---|---|
| Walk me through `POST /orders` from HTTP to the database. | JWT filter → controller + validation → idempotency lookup → service `@Transactional` → lock inventory rows → reservation rows → response stored under the key |
| `FOR UPDATE` vs `@Version` — why here, when would you switch? | Contended hot rows (popular SKU) favour pessimistic; short transactions; optimistic wastes work under contention and is better for rare conflicts |
| Two orders lock SKU A then B and B then A — what happens? | Deadlock; Postgres detects and aborts one; you lock in a stable order (by id) to prevent it |
| What does your idempotency implementation do if the same key arrives twice *concurrently*? | Unique constraint on key; second insert fails → wait/return stored response; or an "in progress" status |
| How does the allocation algorithm handle a tie? Prove it's deterministic. | Explicit ordering + id tie-break; unit test with permuted input order |
| How do you keep `available = on_hand − reserved` consistent? | Single transaction updating both; DB `CHECK (available >= 0)`; reconciliation query in tests |
| Redis dies — what does the user see? | Cache-aside falls through to Postgres; latency rises; a metric/log shows it; no functional failure |
| Cache invalidation for the catalog — when and how? | On product/SKU write: delete key (or write-through); TTL as a backstop; stale window stated |
| What's in the audit event and is it written in the same transaction? | Actor, action, entity, before/after; same transaction for consistency (trade-off: coupling) |
| What did k6 show, and what was the bottleneck? | Your real numbers + methodology; typically DB connections or the lock hot spot |
| Why a Python load harness when you already had k6? | k6 measures HTTP latency; the Python harness generates *realistic* data and reproduces contention scenarios (many orders for one SKU) and asserts on the resulting inventory state — different question; both are documented in `PERFORMANCE.md` |
| How is the generator tested? | pytest: schema of generated records, determinism from a seed, referential integrity (every order line references a generated SKU) |
| What would you change? | Honest list: e.g., outbox for alerts, partial index on low-stock, better allocation weights |

---

## 4. LedgerX — digital wallet & double-entry ledger engine (Weeks 9–13)

**30 seconds**
> "LedgerX is a wallet and double-entry ledger engine: every money movement is a journal transaction whose entries sum to zero, entries are append-only and the database itself forbids updating or deleting them. The hard problem was correctness under concurrency: transfers are idempotent with a key plus request fingerprint, accounts are locked in id order to prevent deadlocks, and the test I'm proudest of starts with a $500 balance, fires two concurrent $400 transfers, and asserts exactly one succeeds and the balance never goes negative. There's a reconciliation job and a fault-injection suite that crashes the process between steps."

**2 minutes — outline**
1. Model: accounts (incl. system accounts like `external_clearing`, `fees`), `ledger_entry` (debit/credit, immutable), `journal_txn` with states pending / completed / failed / reversed / refunded.
2. Immutability enforced in the DB: trigger raising on UPDATE/DELETE + revoked privileges; `CHECK` constraints; balances derived from entries with a materialized balance and an invariant check.
3. Decision 1: pessimistic locking with ordered account locks vs optimistic — measured both.
4. Decision 2: idempotency done properly — key + request fingerprint + status + stored response; same key/different body → conflict; retry-after-crash returns the original result.
5. Decision 3: refunds and reversals as **compensating entries** (never edits); transactional outbox for events.
6. Proof: the $500/$400/$400 test, isolation-level experiments, crash-between-steps tests, reconciliation job (every txn sums to zero; balance == sum(entries)); throughput measured.
7. **Python tooling (`tools/`, M4):** an **independent** reconciliation verifier that reads Postgres directly with `psycopg` and `decimal` and proves every journal sums to zero and every balance equals the sum of its entries — *without trusting the Java code*; a transaction-data generator; a consistency checker run inside the crash/retry failure exercises. pytest-tested.

**10 minutes — emphasize:** the SQL of one transfer (lock A and B in id order → check balance → insert journal + two entries → update materialized balances → store idempotent response), with the transaction boundary drawn; what each isolation level would have allowed; where the outbox is polled; the reconciliation query; one specific crash-injection test and what it proved.

**Drill-down questions**

| Question | Key points |
|---|---|
| Why double-entry instead of a `balance` column you update? | Auditability, invariants (sum to zero), history is the truth; balance is a derived cache |
| How exactly do you prevent a negative balance under concurrency? | Row lock on the source account inside the transaction; check after lock; DB `CHECK` as last line |
| What does `READ COMMITTED` allow that could break you? Did you test `REPEATABLE READ` / `SERIALIZABLE`? | Lost update / write skew without locks; serialization failures need retry; your experiment results |
| Two transfers A→B and B→A at once — deadlock? | Ordered locking by account id makes it impossible; test that proves it |
| Same idempotency key, different amount? | 409/422 conflict with the fingerprint mismatch; never silently return the old response |
| Process crashes after inserting entries but before storing the idempotent response — then the client retries. | Same DB transaction → nothing committed → retry executes normally; or if committed, the key row is inside the same txn, so the retry finds it |
| Why an outbox instead of publishing events inside the service method? | Dual-write problem; outbox row commits atomically with the change; a poller publishes at-least-once → consumers idempotent |
| How does reconciliation detect drift, and what do you do when it finds it? | Sum entries per account vs materialized balance; flag, alert, never auto-"fix" money |
| `BigDecimal` vs `long` cents? Rounding? | Your choice + rounding mode + scale; never `double` |
| How do refunds work when the original txn was partially reversed already? | Compensating txn links the original; sum of reversals ≤ original enforced in code + test |
| What was the throughput, and what limited it? | Your measurement; lock contention on hot accounts; connection pool size |
| Why is the reconciliation verifier in Python and separate from the Java job? | Independence: a bug in the Java ledger code can't hide in a verifier written against the same code; it reads the tables directly and uses `Decimal`, never floats |
| What did the verifier catch? | Your real answer (drift after a crash-injection run, or "nothing — and that's the evidence"), and how it's wired into the failure-exercise runbook |

---

## 5. ForgeCI — distributed CI/CD execution platform (Weeks 14–19)

**30 seconds**
> "ForgeCI is a CI/CD execution platform: a GitHub webhook creates a build, jobs go onto a reliable Redis queue, worker processes pull them and run each step in a temporary Docker container, and logs stream live to a React UI over Server-Sent Events. The hard parts were reliability — leases, heartbeats, orphaned-job recovery, timeouts, cancellation of running containers, and a retry policy that distinguishes app failures from infrastructure failures — proven by a failure-engineering suite that kills workers mid-job. The advanced version adds DAG pipelines with topological scheduling."

**2 minutes — outline**
1. Flow: GitHub event → HMAC-verified webhook (dedupe on `X-GitHub-Delivery`) → build/jobs/steps in Postgres → job queued in Redis → worker `BLMOVE`s it to its processing list with a lease → container via Docker Engine API → steps run, exit codes captured → log chunks persisted + published via pub/sub → SSE to UI → cleanup in `finally`.
2. Decision 1: Redis list + `BLMOVE` with lease vs Streams consumer groups — what you picked and why.
3. Decision 2: SSE over WebSockets (one-directional, HTTP-native, auto-reconnect with `Last-Event-ID` replay-from-sequence).
4. Decision 3: retry policy — exit code ≠ 0 is an app failure (no retry); container start error / worker loss is an infra failure (retry with backoff, max N).
5. Proof: Testcontainers (Postgres + Redis) integration tests; chaos tests (kill worker, kill Redis, kill container); queue wait time and jobs/min measured with N workers.
6. Deploy: Compose with N workers; AWS with the Docker socket security notes.
7. **Python tooling (`tools/`, M5):** a test-repository generator (creates git repos with `.forgeci.yml` variants: passing, failing, slow, timeout, bad config); a worker/load simulator that fires signed webhooks at a rate and measures queue wait and completion; build-result/log analysis tooling that parses persisted logs and reports failure-taxonomy stats. pytest-tested.

**10 minutes — emphasize:** the life of one job with every state transition and the exact Redis operations; how a lease expiry recovers an orphaned job without double-running it; how cancellation reaches a running container (cancel flag + kill); per-project concurrency limits as a Redis counter and its race; the log pipeline's ordering and replay; the DAG scheduler (Kahn's algorithm, fan-out/fan-in, fail-fast).

**Drill-down questions**

| Question | Key points |
|---|---|
| A worker dies halfway through a job. Walk me through recovery. | Heartbeat stops → lease expires → reaper moves the job back to the queue → retry counted as infra failure; the old container is cleaned up by label |
| Can the same job run twice? How do you bound the damage? | At-least-once by design; job attempt ids; steps are re-run from scratch in a fresh container; results keyed by attempt |
| Why `BLMOVE` to a per-worker list instead of `BLPOP`? | `BLPOP` loses the job if the worker crashes after popping; the processing list is the lease record |
| Why not Streams / SQS / Kafka? | Streams consumer groups are a valid alternative (pending entries, `XAUTOCLAIM`); SQS considered for AWS (visibility timeout = lease); Kafka is overkill for this scale |
| How do you verify the webhook is from GitHub? What about replays? | HMAC-SHA256 over the raw body with the shared secret, constant-time compare; unique constraint on delivery id |
| SSE reconnects after a network blip — does the user lose log lines? | `Last-Event-ID` → replay from that sequence out of Postgres, then live from pub/sub |
| What stops one project from consuming all workers? | Per-project Redis counter/semaphore checked at dequeue; released in `finally`; expiry guards a crashed decrement |
| How do you enforce a job timeout on a running container? | Scheduled deadline → `docker kill`; mark TIMED_OUT; no retry |
| Running user code in Docker on a host with the Docker socket — what are the risks? | Socket access = root on the host; mitigations: dedicated worker host, no bind-mounting the socket into build containers, resource limits, network policy; you documented the residual risk |
| Topological scheduling: how do you detect a cycle in `needs:`? | Kahn's algorithm; leftover nodes = cycle → reject config at parse time |
| What did you measure? | Queue wait p50/p95, jobs/min for 1/2/4 workers, and where it stopped scaling (Docker start time, host CPU) |
| How did you generate the load and the test repositories? | The Python generator builds repos with known outcomes (pass/fail/slow/timeout/bad config), so the simulator can assert the platform classified each build correctly, not just that it finished |
| How does the simulator sign webhooks? | Same HMAC-SHA256 over the raw body with the shared secret; a test proves a tampered body is rejected |

---

## 6. FlagForge — feature-flag & progressive-rollout platform with SDK (Weeks 20–23)

**30 seconds**
> "FlagForge is a feature-flag and progressive-rollout platform with a Java SDK. Flag configs are immutable versions per environment, so rollback is just publishing an old version. Evaluation is deterministic — rules by priority, first match wins, and percentage rollouts bucket users with a hash of flag key and user key — and it's fast: environment snapshots are cached in Redis, and I measured p99 evaluation latency. The SDK keeps a local snapshot, evaluates locally, polls or receives SSE updates, and always falls back to defaults if the server is unreachable."

**2 minutes — outline**
1. Model: organizations → projects → environments → flags → **versioned** per-environment configs (rules: attribute targeting, user targeting, percentage rollout, priority, default); audit history; org roles; SDK keys per environment.
2. Decision 1: immutable config versions (rollback = new version copying an old one) — auditability and safe propagation.
3. Decision 2: deterministic bucketing `hash(flagKey:userKey) mod 10000` — the same user always sees the same variant, and increasing the percentage only adds users.
4. Decision 3: SDK design — builder, local cache, local evaluation, timeouts, stale-if-error, offline mode; polling first, then SSE streaming.
5. Propagation: publish → Redis pub/sub → SSE to connected SDKs; stampede protection on snapshot rebuild.
6. Proof: engine unit tests (rule priority, bucket distribution), SDK contract tests against the server, p99 measurements, failure exercises (server down → SDK serves last snapshot/defaults).
7. **Python tooling (`tools/` + `sdk-python/`, M3–M4):** a rollout-distribution simulator that proves a 10 % rollout lands within tolerance and stays sticky over N users, comparing local evaluation with the server; a minimal **Python SDK / test client** implementing the same evaluation contract (polling + ETag, defaults, offline) used for cross-SDK contract tests; a configuration validation tool that lints rule sets for overlaps, unreachable rules and invalid values. pytest-tested.

**10 minutes — emphasize:** the evaluation algorithm on a concrete flag with three rules; why a stable hash (not `String.hashCode()` across JVMs — pick MurmurHash3 or SHA-based) and how you tested bucket uniformity; the Redis snapshot's shape and invalidation; the SDK's state machine (initializing → ready → stale → offline); how the server avoids a thundering herd on publish; what happens to an in-flight evaluation during a version switch (snapshot swap is atomic — one reference).

**Drill-down questions**

| Question | Key points |
|---|---|
| Two rules match a user — which wins? How do you make that obvious in the UI? | Priority order, first match; UI shows priority explicitly; tests for overlapping rules |
| Raising a rollout from 10 % to 20 % — does any user in the 10 % lose the feature? | No: bucket < 1000 ⊂ bucket < 2000; that's the point of deterministic bucketing |
| Why hash `flagKey:userKey` and not just `userKey`? | Avoids the same users always being "first" across all flags (correlated rollouts) |
| Is your hash consistent across SDK languages / JVM versions? | Must be a specified algorithm (e.g., MurmurHash3 32-bit, seed fixed) — `String.hashCode()` is stable in Java but not portable to other SDKs |
| What does the SDK return if the server is down at startup? Mid-run? | Startup: defaults (or a bootstrap file) with `ready=false`; mid-run: stale snapshot ("stale-if-error"), metrics flag it |
| Server-side vs SDK-side evaluation — trade-offs? | Local: latency ~µs, works offline, but config (incl. targeting data) leaves the server; server-side: central, private, +RTT |
| Publish → 1 000 SDKs reconnect and fetch at once. | Pub/sub fan-out + SSE push; snapshot pre-built once (single-flight/lock) and served from Redis; jittered polling as a fallback |
| How is a version rolled back, and what does the audit log show? | New version copying the old content; audit: actor, from-version, to-version, diff |
| Multi-tenancy: how do you stop org A reading org B's flags? | SDK key → environment → project → org resolved server-side; every query scoped; tests for cross-tenant access |
| What was p99 evaluation latency and how did you measure it? | Your numbers, tool (k6 / JMH), warm-up, snapshot cache hit vs miss |
| Semantic versioning of the SDK — what's a breaking change? | Public API changes (builder options removed, return types); document it; you published to a local/GitHub Maven repo |
| You have a Java SDK and a Python SDK — how do you know they evaluate identically? | Shared contract test suite: same config + user attributes → same variant across both, driven from the server's canonical cases; the hash algorithm is specified, not language-default |
| What does the config linter catch that the UI doesn't? | Rules shadowed by a higher-priority catch-all, percentage rollouts summing over 100 %, attributes referenced that no rule can match |

---

## 7. Cross-project questions

- "Which project are you proudest of and why?" — pick one, give a technical reason (most people: LedgerX's correctness proofs or ForgeCI's recovery).
- "What was the hardest bug?" — tie to [`behavioral.md`](./behavioral.md) story #1; the failure-engineering logs in each project are your source.
- "What would you do differently if you started over?" — have 2 real answers per project (`DESIGN_DECISIONS.md`).
- "How did your approach change from FlowGrid to FlagForge?" — tests and failure injection earlier, design docs first, measuring before optimizing, smaller PRs.
- "Why is your tooling in Python when the backend is Java?" — right tool for scripting/data generation/verification; independence for the LedgerX verifier; the same reason the interview coding track is Python. Keep it to two sentences.
- "Idempotency appears in three of your projects — how did your implementation evolve?" — FlowGrid (key + hash + TTL) → LedgerX (fingerprint, conflict semantics, crash-safe) → ForgeCI (delivery-id dedupe, at-least-once workers).
- "Did you use AI tools / tutorials?" — answer truthfully about how you used them and show you understand and can modify the result.
- "How long did it take?" — truthful hours from [`../trackers/project-tracker.md`](../trackers/project-tracker.md).

---

## 8. Practice protocol

| Week | Drill |
|---:|---|
| 8 | FlowGrid: 30 s + 2 min recorded; answer 10 drill-down questions out loud |
| 13 | LedgerX: all three layers; draw the transfer transaction from memory |
| 19 | ForgeCI: all three layers; draw the job lifecycle + recovery from memory |
| 23 | FlagForge: all three layers; evaluate a flag by hand for three users |
| 24 | Record the 10-minute version of each project **without notes** (CP-24 gate) |
| 25 | Full loop: a mock interviewer picks a project and drills for 20 minutes |
| 26 | Weakest project again; update scripts after each real interview |

Scoring (1–4 each): clarity of the 30 s version · diagram accuracy · depth on the hardest problem · honesty/precision on numbers · handling "I don't know". Log in [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md#project-deep-dive-practice).
