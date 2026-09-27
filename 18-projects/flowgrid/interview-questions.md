# FlowGrid — Interview Question Bank

> **Track B** (software-engineering / résumé interview: Java, Spring, SQL, React, Docker, Redis, AWS, CI/CD, system design). Coding-interview practice (Track A) is in Python and lives in [`../../03-dsa/`](../../03-dsa/README.md) and [`../../PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) — never mix the two in an answer.
>
> Use: after each milestone answer that milestone's questions out loud ([`milestones.md`](./milestones.md) §8); at the end of week 8 rehearse the deep dive (§7 below) without notes, recorded, scored against [`../../INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md). Method: [`../../16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md).
>
> Answer outlines are in `<details>`. They reference the design in [`README.md`](./README.md) and [`milestones.md`](./milestones.md). **Replace every "your number" with your measured number** from `PERFORMANCE.md` — never invent one.

Contents: [1 Basic](#1-basic) · [2 Intermediate](#2-intermediate) · [3 Advanced](#3-advanced) · [4 Deep-dive drill-downs](#4-project-deep-dive-drill-downs) · [5 System design](#5-system-design-questions) · [6 Troubleshooting](#6-troubleshooting-scenarios) · [7 Python tooling](#7-python-tooling-questions) · [8 Deep-dive talk outline](#8-1015-minute-deep-dive-talk-outline) · [9 Two-minute version](#9-2-minute-version)

---

## 1. Basic

<details><summary><b>Explain the architecture in one minute.</b></summary>
React/TS SPA (Vite, served by nginx) → Spring Boot REST API (JWT, package-by-feature: catalog, inventory, orders, fulfillment, auth, audit) → PostgreSQL as the single source of truth (Flyway migrations, row locks for reservations) + Redis as a cache-aside for the catalog and low-stock view + scheduled jobs (low-stock scan, idempotency purge, S3 export). Deployed on one EC2 with Docker Compose, RDS Postgres, S3, CloudWatch; CI/CD with GitHub Actions to GHCR. A Python `tools/` package generates data and load-tests the API. README §7.</details>

<details><summary><b>What problem does FlowGrid solve?</b></summary>
Multi-warehouse fulfillment back office: know true stock by state per warehouse, promise stock to orders without overselling under concurrency, create orders exactly once despite retries, choose the best warehouse deterministically, run pick→pack→ship with partial shipments, handle returns/transfers, alert on low stock, and audit every mutation. Motivated by real ops failures I have seen (README §2).</details>

<details><summary><b>What are the main entities?</b></summary>
Warehouse, Product, SKU, InventoryLevel (per warehouse×SKU with on_hand/reserved/allocated/picked/shipped_total/quarantined), StockAdjustment, CustomerOrder, OrderLine, Reservation, Allocation, PickList(+Item), Package, Shipment, ReturnRequest, StockTransfer(+Line), LowStockAlert, AppUser, AuditEvent, IdempotencyKey. README §8.3.</details>

<details><summary><b>Why Java and Spring Boot?</b></summary>
Strong typing and a mature concurrency/transaction model for a correctness-critical domain; Spring gives declarative transactions, security, data access, validation, actuator; it is the dominant enterprise backend stack. Honest addition: it is also the stack on my résumé that I am rebuilding depth in.</details>

<details><summary><b>How does authentication work?</b></summary>
`POST /auth/login` checks BCrypt hash, issues a 15-minute HS256 JWT (sub, role, warehouse id) + rotating refresh token (ADR-002). A `OncePerRequestFilter` validates the token per request and sets the `SecurityContext`; stateless sessions; CSRF disabled because no cookies carry auth. README §12.</details>

<details><summary><b>How does authorization work?</b></summary>
Four roles; URL-level `authenticated()` default plus `@PreAuthorize` per method using the role matrix; a custom bean checks "own warehouse" for associates. Every row of the matrix has a `403` test. README §13.</details>

<details><summary><b>What is an idempotency key and where do you use it?</b></summary>
Client-generated header on `POST /orders` (and returns). Server stores (user, endpoint, key, request hash, response); a retry with the same key returns the stored response instead of creating a second order. README §9.1, milestones M2.</details>

<details><summary><b>What is <code>available</code> and why isn't it stored?</b></summary>
`on_hand − reserved`, derived. Two columns that change for different reasons, a `CHECK` constraint enforces the invariant, and a stored value could drift. README §8.2.</details>

<details><summary><b>What does Redis do here?</b></summary>
Cache-aside for product/SKU reads and the low-stock view with TTL and delete-after-commit invalidation. Nothing correctness-critical; if Redis is down everything still works from Postgres.</details>

<details><summary><b>How is it tested?</b></summary>
Unit (state machines, scorer, hashing), slices (`@WebMvcTest`, `@DataJpaTest` on Testcontainers Postgres), integration (`@SpringBootTest` with Postgres+Redis containers), concurrency tests with real threads and commits, an OpenAPI presence test, Vitest for the SPA, pytest for `tools/`, k6 + the Python harness for load. README §14.</details>

<details><summary><b>How is it deployed?</b></summary>
Tag → GitHub Actions: tests → Docker images → GHCR → SSH/SSM to EC2 → `docker compose pull && up -d` → health wait → smoke test. RDS Postgres, Redis container, S3 exports via instance role, CloudWatch logs + one alarm. README §16–17.</details>

<details><summary><b>What is Flyway and why use it?</b></summary>
Versioned SQL migrations applied in order and recorded in `flyway_schema_history`; the app validates the entity mapping against the schema at startup. Reproducible schema across dev/CI/prod; reviewable in PRs.</details>

<details><summary><b>What is ProblemDetail?</b></summary>
RFC 9457 error format (`type/title/status/detail/instance` + extensions); Spring 6 supports it natively. I add `code` from an error catalogue, `requestId`, and `errors[]` for validation. README §19.</details>

---

## 2. Intermediate

<details><summary><b>Why PostgreSQL (and not MySQL, or a NoSQL store)?</b></summary>
Need: transactions, row-level locking (`FOR UPDATE`), `CHECK` constraints, partial unique indexes (one open alert per level), JSONB for explanations/attributes, and MVCC that keeps readers unblocked. All are first-class in Postgres. MySQL/InnoDB would work (differences: `CHECK` only enforced since 8.0.16, no partial indexes, different default isolation — REPEATABLE READ — and gap locking behaviour; see [`../../04-sql-databases/07-postgres-vs-mysql.md`](../../04-sql-databases/07-postgres-vs-mysql.md)). A document store would push the reservation invariant into application code — exactly what I want the database to guarantee.</details>

<details><summary><b>Why Redis here, and why not more of it?</b></summary>
Catalog reads are hot, rarely change, tolerate minutes of staleness → ideal cache-aside. Inventory quantities are never cached because a stale value would cause oversell/false rejections; the row lock is the only valid read for that decision. Idempotency keys stayed in Postgres (ADR-004) because replay must survive a Redis restart. Redis is an optimisation, never a source of truth.</details>

<details><summary><b>Where are the transaction boundaries?</b></summary>
Services only. Order creation: idempotency claim in its own short transaction; then one transaction for order + ordered row locks + reservations + audit; then the idempotency completion after commit. Allocation: candidate computation (non-locking read) then one transaction per candidate attempt (lock, re-check, reserve, allocate). Ship: one transaction for locks, four counter updates, shipment, line/reservation/order updates, audit. Nothing slow (HTTP, cache writes) inside any of them. Milestones M2/M3.</details>

<details><summary><b>Walk through order creation end to end.</b></summary>
Filter: request id → JWT → Idempotency-Key (hash body, claim row: new / replay / in-progress / mismatch). Controller: validate DTO. Service: check SKUs; compute candidate warehouses (one aggregate query); for the best candidate lock its inventory rows ordered by id `FOR UPDATE`, re-check `available ≥ qty` per line, increment `reserved`, insert reservations + allocation with score breakdown, order → `RESERVED/ALLOCATED`, audit; commit. Filter stores the 201 body under the key. Diagram in `ARCHITECTURE.md`.</details>

<details><summary><b>Explain <code>SELECT … FOR UPDATE</code> vs <code>@Version</code>. Which did you choose and what did you measure?</b></summary>
`FOR UPDATE`: pessimistic row lock; contenders queue; each attempt resolves once. `@Version`: optimistic; contenders all proceed and losers fail at commit with `OptimisticLockException` → retry. I implemented both behind a profile and ran the same 10-threads/1-unit and 50-threads/100-units tests. Chosen: pessimistic for the hot-row reservation because conflicts are the expected case, not the exception (your measured attempts/failures/p95 from ADR-003). Optimistic remains the right tool for low-contention edits like product updates.</details>

<details><summary><b>How does the allocation algorithm work?</b></summary>
Weighted score per feasible warehouse: availability ratio 0.35, region match 0.25, inverse workload 0.15, capacity headroom 0.15, priority boost 0.10 (weights in config); sort by score desc then warehouse id asc; store the full breakdown as JSON on the order so ops can see *why*. Then lock and re-check at the winner, fall back to the next candidate, else `BACKORDERED`. Milestones M3.</details>

<details><summary><b>What is cache-aside and how do you keep it consistent?</b></summary>
Read: try cache, on miss read DB and write cache with TTL. Write: update DB, commit, **then** delete the key (after-commit hook). Deleting before commit leaves a window where a reader re-fills the old value. Residual race bounded by TTL; a versioned key namespace would close it if it mattered.</details>

<details><summary><b>What happens when Redis is down?</b></summary>
Lettuce timeout 200 ms; exceptions caught in the cache-aside path; fall through to DB; rate-limited WARN; health shows redis DOWN in a non-fatal group. Test `catalog_whenRedisDown_readsFromDbAndReturns200`. Failure scenario 2 covers the stale-after-recovery edge.</details>

<details><summary><b>How did you avoid N+1?</b></summary>
`open-in-view=false` so lazy loads outside a transaction fail loudly; list endpoints return projections without collections; detail uses `@EntityGraph`/`JOIN FETCH`; a test asserts ≤ 3 statements for a 200-order page using Hibernate statistics. Failure scenario 9 shows the before/after p95.</details>

<details><summary><b>How do state machines work in your code?</b></summary>
Each stateful entity has an `EnumMap<State, Set<State>>` of allowed transitions and a `transitionTo(next)` method throwing `IllegalStateTransitionException` → `409` with from/to. The tables live in `ARCHITECTURE.md`; a parametrised test covers every forbidden cell.</details>

<details><summary><b>Why two-phase transfers?</b></summary>
Goods physically leave before they arrive; `IN_TRANSIT` stock belongs to neither warehouse's `on_hand`. Dispatch decrements the source under lock; receive increments the destination with the received quantity (shorts recorded). Both phases audited; locks ordered to avoid deadlocks (scenario 10).</details>

<details><summary><b>How do you paginate, filter and sort safely?</b></summary>
`Pageable` with size cap 100, per-endpoint sort whitelist (unknown → 400), filters as explicit params combined with `AND` via JPA `Specification`, `ILIKE` with bound parameters. Own `PageResponse` shape. Offset pagination is fine at this size; cursor pagination noted as the fix at scale.</details>

<details><summary><b>How does the scheduled low-stock job work and what would break with two instances?</b></summary>
`@Scheduled(fixedDelay)`: one SQL finds `available < threshold`, upserts one `OPEN` alert per level (partial unique index), resolves recovered levels, invalidates the cached view. Two instances → duplicate work (not duplicate rows, thanks to the index); fix with ShedLock or a DB advisory lock.</details>

<details><summary><b>What do you log and at which level?</b></summary>
JSON with requestId/userId via MDC. ERROR = actionable failures (alarm); WARN = expected-but-notable (Redis down, deadlock retry, idempotency conflicts, slow requests); INFO = business events with ids; DEBUG = SQL, cache hit/miss, score breakdown. No PII, no tokens, no bodies at INFO. README §18.</details>

<details><summary><b>Describe your CI pipeline.</b></summary>
On PR: Maven `verify` (unit, slices, integration + concurrency on Testcontainers), JaCoCo upload, web lint/test/build, `tools/` ruff/mypy/pytest. On tag: rebuild, push images to GHCR, deploy to EC2, health wait, smoke login. Branch protection requires CI green.</details>

<details><summary><b>Explain your Docker setup.</b></summary>
Multi-stage API image (Maven build stage with dependency layer caching → JRE runtime, non-root, healthcheck); nginx image for the SPA proxying `/api`; dev Compose with Postgres/Redis; prod Compose with api/web/redis, env from a 600-perm file, awslogs driver, no Postgres (RDS). README §15.</details>

<details><summary><b>Why is <code>on_hand</code> decremented at ship rather than at pick?</b></summary>
Design choice (README §8.2): the unit physically leaves at ship; `picked` is a staging count so a cancelled pick can return to shelf without an adjustment. The alternative is defensible; what matters is that all four counters move in one transaction and the choice is documented and tested.</details>

<details><summary><b>How do you handle validation errors and unexpected exceptions?</b></summary>
`@RestControllerAdvice` maps `MethodArgumentNotValidException` → 400 with `errors[]`, domain exceptions → catalogue code/status, `AccessDenied` → 403, unknown → 500 with requestId only, logged at ERROR with stack trace. README §19.</details>

---

## 3. Advanced

<details><summary><b>How did you prevent race conditions, and how do you know it works?</b></summary>
Row-level pessimistic locks in a global id order + re-check under lock + DB `CHECK` + unique constraint on idempotency claims. Proof: `reserve_whenOneUnitAndTenConcurrentOrders_exactlyOneSucceeds` and the multi-line no-deadlock test on a real Postgres in CI; the Python harness verifies `successes == min(units, competitors)` against the deployed API (your run: units/competitors/result from PERFORMANCE.md). I also broke it deliberately (scenario 1) to see the oversell and the constraint catching it.</details>

<details><summary><b>What if the database fails?</b></summary>
It is the source of truth, so the API cannot serve writes; goal is to fail fast and cleanly: Hikari connection timeout → `503 DEPENDENCY_UNAVAILABLE` with `Retry-After`; health DOWN; CloudWatch alarm. Reads from cache continue for catalog only. RDS has automated backups; single-AZ in this budget — Multi-AZ is the production answer. Nothing is half-written because every multi-step operation is one transaction.</details>

<details><summary><b>What if the API crashes mid-request?</b></summary>
Open transaction rolled back by Postgres; the client retries with the same idempotency key; a stale `IN_PROGRESS` claim is reclaimable after a lease (scenario 12), so the retry succeeds and creates exactly one order.</details>

<details><summary><b>100× traffic — what breaks first and what would you change?</b></summary>
Order (README §22): (1) hot inventory rows → lock queueing → pool exhaustion (scenario 11): shrink locked section, then virtual stock buckets per SKU, then a promise counter with async reconciliation (accepting rare oversell as a business choice); (2) single EC2 → ECS/ASG behind ALB, stateless already; (3) orders list offset/`ILIKE` → cursor pagination + trigram/search index; (4) audit growth → partition/archival; (5) synchronous allocation → async via outbox; (6) cache shared across instances → ElastiCache. Each with the trade-off it introduces.</details>

<details><summary><b>Multi-region?</b></summary>
Domain partitions naturally by warehouse region: keep each warehouse's inventory rows in the region that operates it; route orders to the region of the allocated warehouse; cross-region transfers are already two-phase. Hard parts: global catalog (replicate, eventually consistent — fine), global order numbers (per-region prefixes), and an order whose lines split across regions (ADVANCED split fulfillment + a saga). I would not do it before it is needed.</details>

<details><summary><b>Where could data become inconsistent?</b></summary>
(1) Cache vs DB for catalog — bounded by TTL and delete-after-commit; (2) idempotency claim vs business tx — crash window handled by lease; (3) counters within the ship transaction — impossible unless someone splits the transaction, guarded by the rollback test; (4) in-transit stock if receive is not idempotent — status check; (5) two instances running the scheduled job — leader lock needed; (6) `shipped_total` is a reporting counter, not an invariant input — drift there would be cosmetic. I can list these because I broke each on purpose.</details>

<details><summary><b>What trade-off are you least comfortable with?</b></summary>
Honest options: pessimistic locking holds DB connections under contention (pool becomes the limit); single-warehouse allocation rejects orders that could ship split; single EC2/single-AZ RDS for cost; HS256 shared secret vs asymmetric keys. Name one, say why you accepted it and what would change your mind.</details>

<details><summary><b>What would you redesign with another month?</b></summary>
Split fulfillment (per-line allocation, multiple shipments); capacity as a hard constraint; move allocation and exports to async jobs with a transactional outbox; cursor pagination; refresh token in HttpOnly cookie; ECS + ALB + Multi-AZ RDS; contract tests generated from the OpenAPI spec; property-based tests for the state machines. Prioritised by what the measurements showed.</details>

<details><summary><b>Why not a distributed lock in Redis for reservations?</b></summary>
The DB row lock already serialises exactly the critical section and is transactional (released on commit/rollback, no lease expiry bugs, no split-brain). A Redis lock adds a second system that can be wrong (lease expires mid-transaction → two holders). Only justified if the critical section spans multiple databases/services. ADVANCED tier says "only if you can justify it — write the ADR first"; I couldn't justify it.</details>

<details><summary><b>Why not SERIALIZABLE isolation?</b></summary>
It would catch the anomaly but via serialization failures that must be retried, with more aborts under hot-row contention; explicit locks make waiting the failure mode instead of retry storms. For a known hot row, targeted locking is simpler and more predictable. I would consider SERIALIZABLE for complex read-modify-write sets that are hard to lock explicitly.</details>

<details><summary><b>How would you make allocation capacity-aware and what changes?</b></summary>
Capacity becomes a hard constraint: candidates with `allocatedToday + lines > daily_pick_capacity` are infeasible; the "allocated today" counter must be updated under the same lock as the reservation (or in a per-warehouse-day row) to avoid over-allocation races — another hot row, same pattern.</details>

<details><summary><b>How do you keep the locked section small and why does it matter?</b></summary>
Only lock, check, update, insert, audit; no HTTP, no cache writes, no allocation scoring (done before). Lock hold time × arrival rate = queue depth = connections held = latency and pool exhaustion (scenario 11). Measured with `log_min_duration_statement` and the harness p95 at increasing concurrency.</details>

<details><summary><b>Security: what would an attacker try, and what stops them?</b></summary>
Token theft → 15-min expiry, rotation, in-memory storage; BOLA → own-warehouse checks in services, tests per matrix row; mass assignment → separate DTOs, role never bound from self-service; injection → bound parameters; secrets → env/instance role, none in repo; excessive exposure → DTOs, no hashes/PII; dependency CVEs → Dependabot; brute-force login → rate limiting is a documented gap for polish week. OWASP API Top 10 walkthrough in `SECURITY.md`.</details>

<details><summary><b>How would you add observability beyond logs?</b></summary>
Micrometer metrics already exposed (`http.server.requests`, Hikari, cache hit/miss counters I added) → CloudWatch or Prometheus; traces with OpenTelemetry (request id → trace id); dashboards for p95 by endpoint, lock wait time (`pg_stat_activity` sampling), 409 rate as a business signal.</details>

---

## 4. Project deep-dive drill-downs

Interviewers pick one thread and pull. Practise each chain until the third question is comfortable.

**Thread A — the reservation**
1. Show me the code path for reserving stock. → 2. What SQL does Hibernate emit for `@Lock(PESSIMISTIC_WRITE)`? (`select … for update`; verify in the Postgres log) → 3. What if the row doesn't exist yet for that warehouse/SKU? (create level idempotently *outside* the locked section; unique constraint handles the race) → 4. What is the lock timeout and what happens on expiry? (`jakarta.persistence.lock.timeout`, `PessimisticLockException` → 503/409 mapping) → 5. How do you test it without flakiness? (start gate latch, real commits, deterministic assertions on counts, run 20× in CI).

**Thread B — idempotency**
1. Why a filter rather than service code? → 2. How do you read the body twice? (`ContentCachingRequestWrapper`) → 3. What is in the hash? (canonical JSON) → 4. Same key, different user? (scoped) → 5. Crash after claim? (lease) → 6. Why Postgres not Redis? (durable replay; ADR-004) → 7. What about `PUT`? (naturally idempotent; no key needed).

**Thread C — allocation**
1. Why those weights? (config, defaults from ops intuition; I would tune with data) → 2. Determinism with floating point? (sort by rounded score then id) → 3. Where do workload/capacity numbers come from? (one aggregate SQL) → 4. What if the winner is drained between scoring and locking? (re-check under lock, next candidate) → 5. How would you A/B two scoring strategies? (store strategy id in the explanation; compare outcomes offline).

**Thread D — the ship transaction**
1. Which rows are locked? → 2. In what order? → 3. What if the package was already shipped? (state machine 409) → 4. What if the DB dies after `UPDATE inventory_level` and before `INSERT shipment`? (rollback; scenario 3) → 5. Partial shipment: which counters move? (only shipped quantities; reservation stays for the remainder).

**Thread E — deployment and operations**
1. How does a tag become a running container? → 2. What if the health check never passes? (job fails; site may be down until rollback — the honest finding from scenario 8; migration-first fix) → 3. How do you roll back? → 4. Where are the secrets? → 5. What alarm exists and have you seen it fire? → 6. What does it cost?

**Thread F — measurements**
1. What is your p95 for order creation and under what load? (your number + environment) → 2. Why is p99 so much higher than p50? (lock queueing on hot SKUs, GC, cold cache) → 3. What did you change after measuring? → 4. What would you measure next? → 5. Why both k6 and the Python harness?

---

## 5. System design questions

<details><summary><b>Design an inventory reservation service for a flash sale (10k orders/s on 1 SKU).</b></summary>
Start from FlowGrid: a single row lock serialises to maybe hundreds/s. Options: (a) pre-split stock into N bucket rows and reserve from a hashed bucket (N× throughput, occasional false "sold out" while other buckets have stock → rebalance); (b) Redis atomic `DECRBY` with Lua as the promise gate, DB as the record with async reconciliation, accept and compensate rare oversell; (c) queue orders and process sequentially per SKU (fair, bounded, adds latency). Discuss consistency vs availability explicitly; mention idempotency keys carry over unchanged.</details>

<details><summary><b>Design the order → shipment pipeline as separate services.</b></summary>
Orders, Inventory, Fulfillment services; the reservation becomes a cross-service call → saga (reserve → allocate → confirm, with compensating release); transactional outbox for events; idempotent consumers; where you lose the single-DB atomicity you gain independent scaling. Say when this is worth it (not at FlowGrid's size).</details>

<details><summary><b>Design low-stock alerting for 1M SKUs across 200 warehouses.</b></summary>
Event-driven: inventory change events → threshold check on the affected level only (not a full scan); scan job as a periodic safety net over partitions; alert dedupe via partial unique index or Redis set; notification fan-out via SQS/SNS; dashboard reads a materialised low-stock table.</details>

<details><summary><b>How would you design the API for external channels (marketplaces) to submit orders?</b></summary>
API keys per channel (separate from user JWT), `Idempotency-Key` mandatory, rate limits per channel (token bucket in Redis), async acceptance (`202` + status endpoint + webhook callback) so allocation latency doesn't block them, versioned schema, bulk endpoint with per-line idempotency (the EDI-style stretch).</details>

<details><summary><b>Cache design: what would you cache at 100×, and how would you invalidate?</b></summary>
Catalog (already), warehouse metadata, allocation candidate snapshot (short TTL, since it's only a hint), orders list first page per common filter (short TTL), never inventory quantities used for decisions. Invalidate by delete-after-commit + versioned namespaces; protect against stampede with single-flight or jittered TTLs ([`../../15-system-design/caching.md`](../../15-system-design/caching.md)).</details>

<details><summary><b>How would you make the system multi-tenant?</b></summary>
`tenant_id` on every table + composite unique keys; tenant from JWT; row-level security in Postgres or a mandatory `Specification` filter; separate caches namespaces; per-tenant rate limits; the idempotency scope gains the tenant. FlagForge (project 4) does this for real.</details>

---

## 6. Troubleshooting scenarios

Each maps to [`failure-engineering.md`](./failure-engineering.md).

<details><summary><b>Ops reports two orders shipped the same last unit. Where do you look?</b></summary>
Audit events for the SKU/warehouse (who reserved, when, request ids); Postgres log for `for update` on those requests; check whether the path bypassed the service (bulk seed? direct SQL? a new endpoint without the lock?); check the `CHECK` constraint exists in prod schema (`\d inventory_level`). Scenario 1.</details>

<details><summary><b>p99 latency spiked at 14:00 with no deploy. How do you investigate?</b></summary>
CloudWatch: ERROR/WARN rate; slow-request WARNs (path, duration); Hikari pending metric; `pg_stat_activity` lock waits; Redis reachable? (rate-limited WARN); which SKU is hot (409 rate by SKU from logs). Likely: a promotion made one SKU hot → lock queueing → pool wait. Scenario 11.</details>

<details><summary><b>Customers see a product name that was changed an hour ago. Why?</b></summary>
Cache: was the key deleted after commit? Was Redis down during the update (stale key survived the outage)? TTL 10 min should bound it — if an hour, invalidation isn't happening: check `MONITOR` for `DEL` on update. Scenario 2 edge.</details>

<details><summary><b>After a deploy, the API restarts in a loop. First three commands?</b></summary>
`docker compose logs --tail 200 api` (Flyway? env? DB SSL?), `docker inspect api | jq .[0].State`, `SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3`. Then rollback to the previous tag; repair the migration. Scenario 8.</details>

<details><summary><b>A client says "I get 409 IN_PROGRESS forever for my order."</b></summary>
Stale `IN_PROGRESS` claim from a crash; check the row's age; the lease reclaim should handle it — if not, the lease isn't implemented/too long. Scenario 12.</details>

<details><summary><b>The orders page went from 30 ms to 900 ms after adding "line count" to the table.</b></summary>
Someone mapped `order.getLines().size()` per row → N+1. Confirm with Hibernate statistics / `pg_stat_statements` calls count; fix with a subquery projection. Scenario 9.</details>

<details><summary><b>Two warehouses both show the transferred units.</b></summary>
Receive applied without checking status or applied twice (non-idempotent receive), or dispatch never decremented (missing lock/transaction). Compare `stock_transfer.status`, audit events, and counters. Scenario 10 / M4.</details>

<details><summary><b>The alarm fired at 3 a.m.: 40 ERRORs in 5 minutes. Walk me through it.</b></summary>
Filter CloudWatch by level=ERROR → group by exception → requestIds → correlate with WARNs before (Redis? DB?) → check RDS metrics (connections, CPU, storage) → if `DEPENDENCY_UNAVAILABLE`: RDS maintenance window/failover; document in the post-mortem template; add a runbook line.</details>

---

## 7. Python tooling questions

<details><summary><b>Why is there Python in a Java project?</b></summary>
`tools/` is an operations/measurement package, not part of the product: a synthetic data generator (realistic warehouses/SKUs/orders, seeded, writes via API or SQL) and a load/simulation harness (async concurrent order creation, latency percentiles, and an invariant check that `successes == min(units, competitors)`). Python is the natural language for that kind of scripting, data generation and reporting, it exercises the API purely as an external client (no shared code, so it cannot "cheat"), and it demonstrates polyglot competence honestly. The backend stays Java where the type system, Spring transactions and concurrency control matter.</details>

<details><summary><b>How is the Python code kept to the same standard as the Java?</b></summary>
`pyproject.toml` with `src/` layout; `mypy --strict`; `ruff`; `pytest` (+`pytest-asyncio`, `respx` for HTTP mocking) in the same CI workflow; README with usage; typed dataclasses/pydantic models; deterministic generation via an injected `random.Random(seed)`.</details>

<details><summary><b>How does the harness measure and verify?</b></summary>
`asyncio` + `httpx.AsyncClient`, a semaphore for concurrency, an `asyncio.Event` barrier so contention competitors fire together, `perf_counter` per request, nearest-rank percentiles (matches k6), a status/error-code histogram, retries only for 429/503 (never 409 — a 409 is a legitimate loser). After the run it reads `GET /inventory` and produces a `PASS/FAIL` verdict with reasons (`oversell`, `undersell`). Measurement (`RunResult`) and verification (`Verdict`) are separate pure-ish functions so they are unit-testable with fake data.</details>

<details><summary><b>Why both k6 and the Python harness?</b></summary>
k6 gives stable, comparable raw load numbers and is the industry-standard tool; the harness drives a *realistic mix* from the generator, injects hot SKUs and duplicate keys, and verifies invariants afterwards. Numbers + proof. I record both in `PERFORMANCE.md` and never quote a harness latency as if it were a k6 number (different clients, different overheads).</details>

<details><summary><b>How do you make a random data generator testable?</b></summary>
Explicit RNG injection; exact assertions for invariants on every dataset (unique codes, `reserved <= on_hand`, regions valid); statistical assertions with tolerances on large samples (EXPRESS ratio ±2 pp); determinism test (same seed → identical output); a small golden snapshot.</details>

<details><summary><b>What did the harness find that JUnit tests didn't?</b></summary>
Your real finding — e.g. the concurrency level at which the connection pool became the bottleneck (503s appearing), or that lock timeouts produced false `INSUFFICIENT_STOCK` (an `undersell` verdict). If you have no finding yet, say what it would catch (scenario 11's threshold) and that you measured X (your number).</details>

<details><summary><b>Python vs Java for this component — what did you notice?</b></summary>
Speed of iteration and stdlib richness (`statistics`, `dataclasses`, `asyncio`) vs static guarantees; `mypy --strict` recovers much of the safety; `asyncio` single-threaded concurrency is simpler than Java executors for I/O-bound load but CPU-bound JSON parsing caps throughput (why k6 is the number source). See [`../../19-python/python-for-java-devs.md`](../../19-python/python-for-java-devs.md).</details>

---

## 7b. Rapid-fire résumé-defense (30-second answers)

Use in the weekly résumé-defense drills from week 7 ([`../../RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md)). One breath each.

<details><summary>What does <code>@Transactional</code> actually do?</summary>Proxy opens a transaction before the method, commits after, rolls back on unchecked exceptions (configurable); only works through the proxy — not on self-invocation or private methods.</details>
<details><summary>What does <code>@Version</code> do?</summary>Adds a version column; `UPDATE … WHERE id=? AND version=?`; zero rows → `OptimisticLockException`.</details>
<details><summary>READ COMMITTED vs REPEATABLE READ in Postgres?</summary>RC: each statement sees data committed before it started; RR: snapshot per transaction, write conflicts abort. Postgres default is RC.</details>
<details><summary>What is MVCC?</summary>Readers see a snapshot; writers create new row versions; readers don't block writers. Explains why `FOR UPDATE` is needed for read-modify-write.</details>
<details><summary>What is a partial unique index and where did you use it?</summary>Unique constraint over rows matching a `WHERE`; one `OPEN` low-stock alert per inventory level.</details>
<details><summary>JSONB vs JSON?</summary>Binary, indexable, supports operators; used for allocation explanation and SKU attributes.</details>
<details><summary>Why `@Enumerated(STRING)`?</summary>Ordinal breaks on reorder; string is readable in SQL and safe with a `CHECK`.</details>
<details><summary>What is Testcontainers and why not H2?</summary>Real Postgres in Docker for tests; H2 differs on `FOR UPDATE`, JSONB, `CHECK`, partial indexes — tests would pass and prod would fail.</details>
<details><summary>Spring Security filter order matters — why?</summary>JWT filter must run before the authorization filter so the `SecurityContext` is populated; entry point must produce ProblemDetail.</details>
<details><summary>What is a `OncePerRequestFilter`?</summary>Servlet filter guaranteed to run once per request dispatch; used for request id, JWT, idempotency.</details>
<details><summary>What is Lettuce?</summary>Netty-based Redis client used by Spring Data Redis; thread-safe shared connection; timeouts configurable.</details>
<details><summary>Cache-aside vs write-through?</summary>Aside: app reads DB on miss and fills cache; through: writes go to cache and DB together. Aside chosen for simplicity and read-heavy catalog.</details>
<details><summary>Multi-stage Docker build — why?</summary>Build tools stay out of the runtime image: smaller, fewer CVEs, non-root JRE image.</details>
<details><summary>What is an IAM instance role?</summary>Credentials delivered via instance metadata to the EC2; no keys on disk; scoped policy.</details>
<details><summary>What is `useEffect` dependency array?</summary>Controls when the effect re-runs; missing deps → stale closures; used in data-fetch hooks (or replaced by React Query).</details>
<details><summary>TypeScript `strict` — what does it buy?</summary>No implicit any, strict null checks; DTO types mirror the API so a renamed field fails at compile time.</details>
<details><summary>Conventional Commits — why?</summary>Machine-readable history, changelogs, scope per feature; reviewers know intent from the title.</details>
<details><summary>What is `mvn verify` vs `mvn test`?</summary>`test` runs Surefire (unit); `verify` also runs Failsafe integration tests (`*IT`) and checks (JaCoCo).</details>

## 8. 10–15 minute deep-dive talk outline

Rehearse without notes at the end of week 8 (record it). Structure from [`../../16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md).

1. **Context (1 min).** Fulfillment ops background → the three failures that hurt: oversell, duplicate orders, lost transfers. FlowGrid is my clean model of that domain. Stack in one sentence.
2. **Architecture (2 min).** Draw: SPA → API → Postgres/Redis/jobs → AWS. Package-by-feature. Postgres is the only source of truth; Redis can vanish.
3. **The core problem: reservations under concurrency (3 min).** `on_hand`/`reserved` invariant; the race (check-then-act under READ COMMITTED); the fix (ordered `FOR UPDATE`, re-check, `CHECK` constraint); the proof (`reserve_whenOneUnitAndTenConcurrentOrders_exactlyOneSucceeds`, harness `PASS` at units/competitors); the alternative measured (`@Version`, your numbers) and why I chose pessimistic.
4. **Idempotent orders (2 min).** Key + hash + stored response; claim-before-execute in its own transaction; unique constraint makes concurrent duplicates safe; the crash window and the lease (scenario 12).
5. **Allocation and fulfillment (2 min).** Deterministic scoring with stored explanation; candidate → lock → fallback → `BACKORDERED`; pick→pack→ship as one transaction each with state machines; partial fulfillment; two-phase transfers.
6. **Operations (2 min).** Redis cache-aside + degradation; Docker, CI/CD to EC2/RDS/S3/CloudWatch; least-privilege instance role; the alarm I saw fire; cost and teardown.
7. **Measurements and failure engineering (2 min).** Environment sentence + p50/p95/p99 at N VUs, contention success ratio, cache hit ratio; what broke first (pool) and what I changed; two failure exercises I ran (Redis down; migration failure) and what I learned about deploy ordering.
8. **What I'd do next (1 min).** Split fulfillment, async allocation via outbox, cursor pagination, ECS/ALB/Multi-AZ. Trade-off I'm least comfortable with.

Timing checkpoints: architecture done by 3:00; reservations done by 6:30; ops by 11:00; end ≤ 14:00. Leave room for interruptions — every section has a drill-down thread (§4).

## 9. 2-minute version

> "FlowGrid is a multi-warehouse fulfillment and inventory platform I built in Java 21 and Spring Boot with PostgreSQL, Redis, a React/TypeScript dashboard, Docker, and AWS. I chose the domain because I've worked in fulfillment operations and I know the failures that matter: overselling the last unit, duplicate orders from client retries, and stock that gets lost between warehouses.
>
> The core engineering problem is reserving stock correctly under concurrency. Inventory is modelled with separate `on_hand` and `reserved` counts and a database-enforced invariant. Reservations take ordered row locks with `SELECT … FOR UPDATE`, re-check availability under the lock, and I have a concurrency test — ten threads, one unit — that proves exactly one succeeds, running against a real Postgres in CI. I also implemented optimistic locking and measured both before choosing.
>
> Order creation is idempotent: a client key, a request hash, and the stored response, claimed in its own transaction so concurrent duplicates can't both proceed. Orders are allocated to a warehouse with a deterministic score whose breakdown is stored for ops, then flow through pick, pack and ship as single transactions with explicit state machines and partial shipments.
>
> Redis caches the catalog with invalidation after commit and degrades to the database when it's down — I tested that. It's deployed with GitHub Actions to EC2 with Docker Compose, RDS, S3 exports and CloudWatch alarms, with least-privilege IAM. A Python tooling package generates realistic data and drives concurrent load; together with k6 I measured order-creation latency of [your p95] at [your load] on [your environment], and found the connection pool becomes the limit under hot-SKU contention before the database does.
>
> Next I'd add split fulfillment across warehouses and move allocation to an async job with an outbox."

Say it in under two minutes with no numbers you did not measure. Adjust the last paragraph to what you actually measured and found.
