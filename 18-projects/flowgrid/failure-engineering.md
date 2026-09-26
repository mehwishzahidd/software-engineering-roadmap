# FlowGrid — Failure Engineering

> **Break it on purpose, predict what happens first, then prove the fix with a test.**
> Each scenario follows the same loop: **reproduce → observe → inspect logs → diagnose → fix / design around → regression test**.
> Run scenarios 1–6 and 9 before tagging `v1.0` (M5 requirement); the rest during polish weeks. Write a short entry per scenario in your repo's `docs/FAILURE_EXERCISES.md`: date, what you predicted, what actually happened, what you changed.
>
> Tooling: dev Compose stack, `psql`, `redis-cli`, `docker compose stop/start`, `jq`, the Python harness in `tools/` (M5), Postgres logging (`log_statement=all`, `log_lock_waits=on`, `deadlock_timeout=1s` in the dev container command).

| # | Scenario | Milestone | Category |
|---|---|---|---|
| [1](#1-two-customers-reserve-the-final-unit-simultaneously) | Two customers reserve the final unit simultaneously | M2 | Concurrency |
| [2](#2-redis-unavailable) | Redis unavailable | M3 | Dependency failure |
| [3](#3-db-operation-fails-halfway-through-pick--pack--ship) | DB operation fails halfway through pick → pack → ship | M3 | Atomicity |
| [4](#4-duplicate-order-request-with-the-same-idempotency-key) | Duplicate order request with the same idempotency key | M2 | Idempotency |
| [5](#5-same-idempotency-key-different-payload) | Same key, different payload | M2 | Idempotency |
| [6](#6-allocation-when-every-warehouse-is-short) | Allocation when every warehouse is short | M3 | Business edge |
| [7](#7-jwt-expires-mid-session) | JWT expires mid-session | M1/M4 | Auth |
| [8](#8-migration-fails-on-deploy) | Migration fails on deploy | M5 | Deployment |
| [9](#9-n1-explosion-on-the-orders-list) | N+1 explosion on the orders list | M2 | Performance |
| [10](#10-deadlock-between-two-transfers) | Deadlock between two transfers | M4 | Concurrency |
| [11](#11-connection-pool-exhaustion-under-lock-contention) | Connection pool exhaustion under lock contention | M5 | Capacity |
| [12](#12-crash-between-idempotency-begin-and-order-commit) | Crash between idempotency `begin` and order commit | M2/M5 | Crash consistency |

---

## 1. Two customers reserve the final unit simultaneously

**Reproduce**
1. Seed: warehouse 1, SKU `TS-NAV-M`, `on_hand=1, reserved=0`.
2. *Bug variant first* (do this once so you see the failure): temporarily replace `lockAllByIdInOrder` with a plain `findAllById` (or comment out `@Lock`), keep the Java-side `available >= qty` check.
3. Fire concurrent orders. Three ways, use all three over the project:
   - JUnit: `reserve_whenOneUnitAndTenConcurrentOrders_exactlyOneSucceeds` (M2).
   - Shell: `for i in $(seq 1 10); do curl -s -o /dev/null -w '%{http_code}\n' -X POST $API/orders -H "Authorization: Bearer $T" -H "Idempotency-Key: $(uuidgen)" -H 'Content-Type: application/json' -d @one-unit-order.json & done; wait`
   - Python harness (M5): `flowgrid-tools contention --sku TS-NAV-M --warehouse 1 --units 1 --competitors 10`
4. Then restore the lock and repeat.

**Observe**
- Bug variant: two (or more) `201`s. `SELECT on_hand, reserved FROM inventory_level WHERE id=…` shows `reserved=2` — unless the `CHECK (reserved <= on_hand)` constraint exists, in which case the second commit fails with a `DataIntegrityViolationException` → `500` (better than oversell, still wrong).
- Fixed: exactly one `201`, nine `409 INSUFFICIENT_STOCK`; `reserved=1`; harness prints `PASS successes=1 expected=1`.

**Inspect logs**
- `grep -E '"code":"INSUFFICIENT_STOCK"|ORDER_RESERVED' api.log | wc -l` — count winners/losers.
- Postgres: `docker compose logs postgres | grep -i 'for update'` — is the lock emitted at all? With `log_lock_waits=on`: `process 123 still waiting for ShareLock on transaction 456` lines show the queueing that *should* happen.
- Bug variant: no `for update`, no lock waits, two `UPDATE inventory_level SET reserved=…` interleaved.

**Diagnose**
Check-then-act on a shared row without a lock: both transactions read `reserved=0` under READ COMMITTED, both pass the check, both write. Classic lost update. If `@Lock` *is* present but you still see it: the method was called via self-invocation (no proxy → no transaction → lock silently ignored), or the check happened *before* the locking query and was not repeated after it.

**Fix / design around**
- `SELECT … FOR UPDATE` on the inventory rows, ordered by id, inside the service transaction; re-check *after* locking; increment; commit.
- Keep the DB `CHECK` as the last line of defence.
- Alternative worth knowing: conditional update `UPDATE inventory_level SET reserved = reserved + :q WHERE id = :id AND reserved + :q <= on_hand` and check the affected-row count — no explicit lock, single statement, also correct.

**Regression test**
`reserve_whenOneUnitAndTenConcurrentOrders_exactlyOneSucceeds` — asserts exactly one `RESERVED` order, `reserved == 1`, no exception other than `InsufficientStockException` in the collected results. Plus the harness run recorded in `PERFORMANCE.md` (`units=100, competitors=150 → PASS`).

---

## 2. Redis unavailable

**Reproduce**
1. Warm the cache: `curl $API/skus/1` twice; `redis-cli GET fg:v1:sku:1` shows the JSON.
2. `docker compose stop redis`.
3. `curl -w '%{time_total}\n' $API/skus/1`; `curl $API/inventory/low-stock`; update a SKU with `PUT /skus/1`; `curl $API/actuator/health`.
4. `docker compose start redis`; `curl $API/skus/1` twice; `redis-cli MONITOR` in another terminal.

**Observe**
- *Before the fix (default Lettuce timeout 60 s, no error handling):* each catalog request hangs for up to 60 s then `500 INTERNAL_ERROR`; the SPA freezes; Hikari fine but Tomcat threads pile up.
- *After:* each request takes ~200 ms extra (the command timeout) and returns `200` from the DB; one `WARN` every 30 s; health shows `redis: DOWN` inside a non-fatal group; after restart the first read `SET`s the key again; the SKU updated during the outage is *not* stale because the key was never re-filled with the old value (verify).

**Inspect logs**
- `grep -c 'redis read failed' api.log` — must be small (rate-limited), not one per request.
- `grep RedisConnectionFailureException api.log | head` — stack traces only at DEBUG; WARN has a one-line message.
- Actuator: `curl $API/actuator/health | jq .components.redis`.

**Diagnose**
Cache treated as a dependency instead of an optimisation: exceptions propagate; timeouts default to a minute; health marks the whole app down and a load balancer would remove a perfectly capable instance.

**Fix / design around**
- Lettuce `spring.data.redis.timeout=200ms`, `connect-timeout=200ms`.
- Cache-aside code catches `RedisConnectionFailureException`/`RedisSystemException` on both read and write; falls through to DB; rate-limited WARN (`DegradationLogger` with `AtomicLong lastLoggedAt`).
- If using Spring Cache: a `CacheErrorHandler` that logs and swallows.
- Health: custom group or `management.health.redis.enabled=false` for liveness plus a separate detail; document the choice.
- A stale-during-outage edge: an update while Redis is down cannot delete the key. When Redis returns, the *old* cached value may still be there if it was cached before the outage and the TTL has not elapsed. Fix options: version prefix bumped on reconnect, or short TTL, or `FLUSHDB` on reconnect for the catalog namespace (`SCAN`+`DEL`). Pick one, write it in ADR-005.

**Regression test**
`catalog_whenRedisDown_readsFromDbAndReturns200` (stop the Testcontainers Redis via `redis.stop()` or point the profile at a closed port; assert `200`, body from DB, elapsed < 1 s) and `catalog_whenRedisDown_logsWarnAtMostOncePer30s` (capture logs with a Logback `ListAppender`, fire 50 requests, assert ≤ 2 WARN lines). ADVANCED: `catalog_staleKeyAfterOutage_isNotServedBeyondTtl`.

---

## 3. DB operation fails halfway through pick → pack → ship

**Reproduce**
1. Order in `PACKED` with one package; inventory `on_hand=10, reserved=2, allocated=2, picked=2`.
2. Inject a failure *after* the counters are decremented but *before* the shipment row is written. Options: a test spy on `ShipmentRepository.save` throwing `RuntimeException`; or a temporary `if (order.id % 2 == 0) throw new IllegalStateException("chaos")` behind a `chaos` profile; or — most realistic — add a `NOT NULL` column to `shipment` in a throwaway migration without updating the entity so the insert fails at flush.
3. `POST /orders/{id}/ship`.

**Observe**
- With `@Transactional` on the service method: `500` (or your mapped error), and `SELECT on_hand, reserved, allocated, picked FROM inventory_level …` is **unchanged** (10/2/2/2); no `package.status=SHIPPED`, no `shipment`, order still `PACKED`.
- *Bug variants to try:* (a) catch the exception inside the method and return "success" → Spring never sees it → counters decremented, no shipment: inconsistent; (b) `@Transactional` on a `private` or self-invoked method → no transaction → partial writes persist; (c) throwing a **checked** exception without `rollbackFor` → commit happens anyway.

**Inspect logs**
- `grep -B2 -A10 'ship' api.log | grep -E 'Rolling back|Committing'` with `logging.level.org.springframework.transaction=DEBUG` — you want `Initiating transaction rollback`.
- Postgres with `log_statement=all`: `BEGIN … UPDATE inventory_level … INSERT INTO shipment (fails) … ROLLBACK`.

**Diagnose**
Atomicity depends on (1) one transaction spanning all writes, (2) the exception reaching the proxy, (3) rollback rules covering the exception type. Each bug variant breaks one of the three.

**Fix / design around**
- One `@Transactional` service method for ship; no catching of exceptions that would hide failure; if you must catch, rethrow or `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`.
- `@Transactional(rollbackFor = Exception.class)` if any checked exceptions exist in the path.
- Ordered locks first, then writes, then audit, all inside.
- Do not send anything external (email, webhook) inside; if you ever need to, that is the outbox pattern (LedgerX M3).

**Regression test**
`ship_failureAfterPackageCreated_rollsBackInventoryAndShipment` — `@SpyBean ShipmentRepository` throwing on `save`; assert `assertThrows`, then reload the inventory level and assert all four counters unchanged and `shipmentRepository.count()==0`, order status `PACKED`. Also `ship_checkedExceptionInPath_stillRollsBack` if you have any.

---

## 4. Duplicate order request with the same idempotency key

**Reproduce**
1. `K=$(uuidgen)`; `curl -X POST $API/orders -H "Idempotency-Key: $K" … -d @order.json` → `201`, note `id`.
2. Same command again (simulating a client retry after a lost response).
3. Concurrent variant: `for i in 1 2 3 4 5; do curl … -H "Idempotency-Key: $K" -d @order.json & done; wait`.
4. Crash variant → scenario 12.

**Observe**
- Second call returns the **same** status and body (same `id`); `SELECT count(*) FROM customer_order WHERE external_ref=…` = 1; `reserved` incremented once.
- Concurrent variant: one `201`, others `409 IDEMPOTENCY_IN_PROGRESS` with `Retry-After: 1` (if they arrived while the first was running) or `201` replays (if after). Never two orders. Never `500`.
- *Bug variants:* key row written only after success → two concurrent requests both proceed → two orders; key not scoped by user → user B replays user A's order and gets A's data (an authz leak!).

**Inspect logs**
- `grep "$K" api.log` — the first request logs `idempotency=NEW`, the second `idempotency=REPLAY`, concurrent ones `IN_PROGRESS`.
- `SELECT status, response_status, request_hash, created_at FROM idempotency_key WHERE key='$K';`
- Postgres: the concurrent variant shows one `INSERT INTO idempotency_key` succeeding and others failing with `duplicate key value violates unique constraint` — that is the mechanism working, not an error to hide (handle it, don't log it at ERROR).

**Diagnose**
Idempotency is a *reservation of the right to execute*: it must be claimed atomically **before** executing (unique insert), and the outcome stored **after**. Any ordering that executes first and records later has a window.

**Fix / design around**
- `begin()` in its own transaction (`REQUIRES_NEW` or a separate `TransactionTemplate`): `INSERT … IN_PROGRESS`; on unique violation → read the row and classify (replay / in-progress / mismatch).
- Scope: `(user_id, endpoint, key)` unique.
- Store 2xx and 4xx outcomes; on 5xx delete the row so a retry can succeed (design decision — document).
- TTL 24 h + purge job.

**Regression test**
`orderCreate_sameKeySameBody_returnsSameOrderIdAndCreatesOneOrder`, `orderCreate_concurrentSameKey_createsExactlyOneOrder`, `orderCreate_sameKeyDifferentUser_isTreatedAsNewKey` (user B with A's key gets their *own* new order, not A's). Python: `test_api_sink_sends_idempotency_key_and_retries_on_5xx` (client side), and the generator's duplicate injection (`test_generator_injects_duplicate_orders_with_same_idempotency_key`) drives this scenario at scale in the harness: `flowgrid-tools load --mix fixtures/mix-dupes.json` → report shows `replays` count equals injected duplicates and `orders_created == unique_orders`.

---

## 5. Same idempotency key, different payload

**Reproduce**
1. `POST /orders` with `Idempotency-Key: K`, body A (`quantity: 1`) → `201`.
2. Same `K`, body B (`quantity: 2`).
3. Subtle variant: same logical body but different key order / whitespace (`{"a":1,"b":2}` vs `{"b":2,"a":1}`).

**Observe**
- Step 2 → `422 IDEMPOTENCY_KEY_REUSED` with `detail: "Idempotency-Key was used with a different request"`; no new order; `reserved` unchanged.
- Step 3 → must be treated as the **same** request (replay), if you canonicalise; if you hash raw bytes it is a `422` — a client that re-serialises would be rejected on retry. Decide and document (canonicalise is recommended).

**Inspect logs**
- `grep IDEMPOTENCY_KEY_REUSED api.log` — log at WARN with the key and both hashes (never the bodies).

**Diagnose**
Without the hash, a key collision silently returns a *different* order than requested — the client believes it created B and got A. Hash comparison turns a silent wrong answer into a loud error.

**Fix / design around**
- `request_hash = sha256(canonicalJson(body))` where canonical = Jackson tree → sorted keys → no whitespace.
- Compare on every hit; mismatch → 422 (not 409: the request is semantically unprocessable, not a state conflict — your call, be consistent).

**Regression test**
`orderCreate_sameKeyDifferentBody_returns422`, `idempotencyHash_keyOrderAndWhitespaceDoNotChangeHash`, `idempotencyHash_quantityChangeChangesHash`.

---

## 6. Allocation when every warehouse is short

**Reproduce**
1. Three warehouses; SKU `X` has `available` 3, 4, 2; order for 5 units of `X` (single warehouse allocation in v1.0).
2. `POST /orders`.
3. Variant: two lines, each fits somewhere but no single warehouse fits both.
4. Variant: the *winner* is drained between candidate computation and lock (use a second session to reserve 3 units at the winning warehouse while the first request is paused in the debugger at the lock line).

**Observe**
- Order exists with status `BACKORDERED`, `allocation_explanation` lists all candidates with `feasible:false` and per-line shortfalls; `reservation` table has **no** `ACTIVE` rows for the order; every `inventory_level.reserved` unchanged.
- Variant 4: explanation shows candidate 1 `feasible:true` at scoring time but `lockCheckFailed:true`; candidate 2 chosen (or `BACKORDERED` if none remain).
- *Bug variants:* partial reservation left behind (line 1 reserved at W1, line 2 fails → nothing rolled back); order `REJECTED` and *deleted* so ops cannot see demand; a `500` because the candidate list is empty and `.get(0)` throws.

**Inspect logs**
- `grep -E 'ORDER_BACKORDERED|allocation' api.log | jq .` — one INFO line per order with the chosen warehouse or `none`, plus DEBUG lines per candidate.
- `SELECT status, allocation_explanation FROM customer_order WHERE id=…;`

**Diagnose**
The candidate computation is a *hint* from a non-locked read; only the locked re-check is authoritative. Feasibility is per-order in v1.0 (all lines at one site), so partial feasibility must not leave partial reservations.

**Fix / design around**
- Candidate loop with ordered locks + re-check per candidate; reservation writes only after all lines pass; exceptions inside the loop iteration roll back that attempt (use a `TransactionTemplate` per candidate attempt, or one transaction with a savepoint — simpler: one tx per attempt).
- `BACKORDERED` is a first-class state with a retry endpoint/job (`POST /orders/{id}/allocate`), and a low-stock alert is a natural consequence.
- ADVANCED: split fulfillment (per-line allocation) turns this into "ship 3 from W2, 2 from W3".

**Regression test**
`allocation_everyWarehouseShort_orderBackorderedAndNothingReserved`, `allocation_twoLinesNoSingleWarehouseFits_backorderedNoPartialReservation`, `allocation_winnerLosesRaceAtLock_fallsBackToNextCandidate`.

---

## 7. JWT expires mid-session

**Reproduce**
1. Set `flowgrid.jwt.access-ttl=30s` in a `chaos` profile.
2. Log in on the dashboard, open the orders page, wait 35 s, click "next page".
3. API-only: `sleep 35; curl -i $API/orders -H "Authorization: Bearer $OLD"`.

**Observe**
- API: `401` ProblemDetail with `code: TOKEN_EXPIRED` (distinct from `TOKEN_INVALID`), `WWW-Authenticate: Bearer error="invalid_token"` header.
- Dashboard *without* refresh handling: blank table, console error, user confused. *With* refresh (Option B, ADR-002): client sees `401 TOKEN_EXPIRED` → calls `/auth/refresh` → retries the original request once → page loads; if refresh fails → redirect to login preserving the intended route.
- *Bug variants:* expired token treated as `403` (client shows "not allowed"); the refresh loop retries forever on a `401` from `/auth/refresh` itself; two concurrent requests both trigger refresh and the second refresh invalidates the first's rotated token (rotation race) → user logged out randomly.

**Inspect logs**
- `grep TOKEN_EXPIRED api.log | jq '.requestId, .userId'` — must **not** contain the token.
- Browser network tab: `401` → `POST /auth/refresh` → original request replayed once.
- `SELECT revoked, expires_at FROM refresh_token WHERE user_id=… ORDER BY id DESC LIMIT 3;` — rotation trail.

**Diagnose**
Expiry is expected behaviour; the failure is in *classification* (expired vs invalid), *client orchestration* (single-flight refresh), and *rotation semantics* (grace period for the previous refresh token, or a single-flight promise on the client so only one refresh happens).

**Fix / design around**
- `JwtAuthFilter` catches `ExpiredJwtException` separately → `TOKEN_EXPIRED`.
- Client: one shared in-flight refresh promise; queue requests behind it; give up after one retry.
- Server: rotated refresh tokens with a short grace window (e.g. previous token valid 10 s) **or** accept that a race logs the user out and document it.
- Never put roles-only decisions in the client; a `403` after refresh means the role changed — re-login.

**Regression test**
Backend: `jwtAuthFilter_expiredToken_returns401WithCodeTokenExpired`, `refresh_rotatedTokenReuse_isRejectedAndRevokesFamily` (ADVANCED). Frontend: `apiClient attaches bearer and retries once after refresh on 401`, `apiClient concurrent 401s trigger a single refresh`.

---

## 8. Migration fails on deploy

**Reproduce**
1. On a branch, add `V9__bad.sql` containing `ALTER TABLE customer_order ADD COLUMN priority VARCHAR(16) NOT NULL;` (no default on a populated table → fails) — or a syntax error.
2. Tag `v1.0-rc-broken`, push the tag; watch `deploy.yml`.
3. Locally first: `docker compose --profile app up` with the bad migration.

**Observe**
- API container exits during startup: `FlywayException: Migration V9__bad.sql failed … ERROR: column "priority" of relation "customer_order" contains null values`. Flyway records the failed migration in `flyway_schema_history` with `success=false`.
- Deploy job: `compose up -d` returns 0 (containers "created"), health wait loops, times out → job **fails**. Whether the *old* container is still serving depends on how you deployed: `compose up -d` **replaces** the container immediately, so the site is down until rollback. That is the important finding.
- Subsequent restarts fail with `Detected failed migration to version 9` until repaired.

**Inspect logs**
- `docker compose logs api | grep -A5 Flyway`.
- `SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;`
- Actions job log: which step failed and how long the health wait took.

**Diagnose**
Migrations run at app start; a failing migration = failed start = outage if the previous container was already replaced. Also: destructive/non-backfilled migrations are not backwards compatible with the running version.

**Fix / design around**
- Migration hygiene: additive first (`ADD COLUMN … NULL`), backfill, then `SET NOT NULL` in a later migration (expand/contract). Test migrations against a copy of prod-like data (the generator's `--sql` dataset) in CI: a job that runs Flyway against a Testcontainers DB pre-populated from the previous tag.
- Deploy: pull image → run `flyway migrate` as a one-off container (`docker compose run --rm api java -jar app.jar --spring.main.web-application-type=none --flowgrid.migrate-only=true`, or Flyway CLI) **before** `up -d`; only replace the running container if migration succeeded.
- Rollback: `flyway repair` to clear the failed row (after fixing the SQL), redeploy previous tag; document in `DEPLOYMENT.md`.
- Keep `spring.flyway.enabled=true` at startup as a safety net (idempotent when already migrated).

**Regression test**
CI job `migrations-against-previous-schema` (Testcontainers Postgres, apply migrations up to the last tag, load a generated dataset, apply HEAD migrations → must succeed). Unit-level: `flyway_allMigrationsApplyOnEmptyDatabase` (`@SpringBootTest` already covers it) and a checklist item in the PR template for `NOT NULL` additions.

---

## 9. N+1 explosion on the orders list

**Reproduce**
1. Seed 2 000 orders with 1–5 lines each (`flowgrid-tools seed --sql --orders 2000`).
2. Bug variant: return `OrderDto` (with lines) from `GET /orders?size=100` mapped straight from `Page<CustomerOrder>` with `lines` lazy.
3. Enable `spring.jpa.properties.hibernate.generate_statistics=true` and `logging.level.org.hibernate.SQL=DEBUG`; call the endpoint; count statements. Then `spring.jpa.open-in-view=true` vs `false` (with `false` you get `LazyInitializationException` instead — better!).

**Observe**
- Bug: 1 query for the page + 100 for lines (+100 for skus if `OrderLine.sku` is eager-ish) → ~201–301 statements; p95 for the page jumps from ~20 ms to hundreds of ms; k6 on `/orders?size=100` shows it clearly.
- Fixed: 2–3 statements (page query, count query, one `JOIN FETCH`/`IN` batch for lines if the list needs them at all).

**Inspect logs**
- `grep -c 'select .* from order_line' api.log` during one request.
- Hibernate stats line: `Session Metrics { … executed N JDBC statements }` — N is the number you assert.
- Postgres `pg_stat_statements` (enable in dev): `SELECT calls, mean_exec_time, query FROM pg_stat_statements ORDER BY calls DESC LIMIT 5;`

**Diagnose**
Lazy association accessed per row during DTO mapping; pagination + `JOIN FETCH` on a collection is a second trap (Hibernate warns `HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory`).

**Fix / design around**
- List endpoint returns a **summary projection** (order number, status, region, line count via subquery, created_at) — no lines.
- Detail endpoint uses `@EntityGraph(attributePaths={"lines","lines.sku"})` or `JOIN FETCH` (one entity, no pagination problem).
- If the list truly needs lines: two queries — page of ids, then `where o.id in (:ids) join fetch lines` — or `@BatchSize`. Never collection fetch + pagination.
- Keep `open-in-view=false` so this can never silently regress.

**Regression test**
`ordersList_200Orders_executesAtMost3Statements` (Hibernate `Statistics.getPrepareStatementCount()` or `datasource-proxy` `QueryCountHolder`), `orderDetail_loadsLinesAndSkusInOneQuery`. Record before/after p95 in `PERFORMANCE.md` (k6 `orders-list.js`).

---

## 10. Deadlock between two transfers

**Reproduce**
1. Warehouses W1, W2; SKUs A and B with stock in both.
2. Transfer T1: W1→W2 lines [A, B]; Transfer T2: W2→W1 lines [B, A]. Both `REQUESTED`.
3. Bug variant: dispatch locks source levels *in line order* (A then B for T1; B then A for T2) — but the deadlock needs the same rows, so make it real: T1 dispatch locks (W1,A),(W1,B); T2 *receive* (creating/locking destination rows at W1) locks (W1,B),(W1,A). Or simpler: two **dispatches from the same warehouse** with reversed line order: T1 W1→W2 [A,B], T2 W1→W3 [B,A].
4. Add a `Thread.sleep(500)` between the two locks (chaos profile) and fire both dispatches concurrently: `curl … /transfers/1/dispatch & curl … /transfers/2/dispatch & wait`.
5. `psql`-only reproduction (always works): two sessions, `BEGIN; SELECT … WHERE id=1 FOR UPDATE;` / `BEGIN; SELECT … WHERE id=2 FOR UPDATE;` then cross: session 1 locks id=2, session 2 locks id=1.

**Observe**
- After `deadlock_timeout` (1 s) Postgres aborts one: `ERROR: deadlock detected DETAIL: Process 51 waits for ShareLock on transaction 731; blocked by process 52 …`. Spring maps it to `CannotAcquireLockException` (a `PessimisticLockingFailureException`) → without handling, `500`.
- The survivor commits; the victim's transaction is rolled back entirely (no partial decrement).
- Fixed variant: no deadlock; the second dispatch waits for the first and then proceeds (or fails `409 INSUFFICIENT_STOCK` if stock ran out).

**Inspect logs**
- Postgres: `grep -A4 'deadlock detected' postgres.log` — shows both processes and the exact statements.
- API: `grep CannotAcquireLockException api.log`.
- `log_lock_waits=on` shows the waiting statements before the abort.

**Diagnose**
Two transactions acquire the same set of row locks in different orders → cycle → deadlock. Postgres detects and breaks it; your code must (a) not create cycles and (b) handle the residual case.

**Fix / design around**
- Global lock ordering: collect all `inventory_level.id`s the transaction will touch (source and destination, all lines), sort, lock in one `… WHERE id IN (:ids) ORDER BY id FOR UPDATE`. Note `ORDER BY` in a `FOR UPDATE` query is honoured for the lock acquisition order in Postgres.
- Residual handling: catch `PessimisticLockingFailureException` at the service boundary and retry the whole transaction up to 3 times with jitter (Spring Retry or a small loop around a `TransactionTemplate`); after that → `409 CONCURRENT_MODIFICATION`.
- Lock timeout (`jakarta.persistence.lock.timeout`) so a stuck transaction doesn't hold the pool.

**Regression test**
`transfer_twoOppositeTransfersConcurrently_noDeadlockOrDeterministicRetry` — 50 iterations of the concurrent pair; assert both end in a terminal state (`IN_TRANSIT` or a clean 409), inventory totals across warehouses unchanged (sum of `on_hand` + in-transit = constant), and the Postgres log (Testcontainers `getLogs()`) contains no `deadlock detected`. Also `lockOrdering_idsAlwaysSorted` unit test on the helper that builds the id list.

---

## 11. Connection pool exhaustion under lock contention

**Reproduce**
1. `HikariCP maximumPoolSize=5` (chaos profile), `connection-timeout=2000`.
2. `flowgrid-tools contention --units 1000 --competitors 60` (one hot SKU) or k6 with 60 VUs on one SKU.
3. Watch `/actuator/metrics/hikaricp.connections.pending` and `.active`.

**Observe**
- Throughput plateaus; after ~2 s, requests fail with `SQLTransientConnectionException: … request timed out after 2000ms` → your handler maps to `503 DEPENDENCY_UNAVAILABLE` (or `500` if unmapped). Latency p99 ≈ connection timeout. The harness histogram shows a 503 cluster; the contention verdict still `PASS` (no oversell — just failures), which is the correct behaviour under overload.
- Postgres `pg_stat_activity` shows 5 sessions, 4 `wait_event_type=Lock`.

**Inspect logs**
- `grep 'request timed out' api.log | wc -l`; Hikari pool stats at DEBUG (`com.zaxxer.hikari=DEBUG`): `Pool stats (total=5, active=5, idle=0, waiting=37)`.
- `SELECT pid, state, wait_event_type, left(query,60) FROM pg_stat_activity WHERE datname='flowgrid';`

**Diagnose**
Each waiting reservation holds a connection while blocked on the row lock; the pool, not the DB, is the first bottleneck. Lock hold time × arrival rate > pool size.

**Fix / design around**
- Shrink the locked section (nothing but the lock, the check, the update, the inserts).
- Reasonable pool size (10 on `t3.small`; more does not help when everyone waits on one row).
- Fail fast: lock timeout ≈ 1–3 s, `connection-timeout` ≈ 2–5 s, map to `503` with `Retry-After`; the client (and the harness) retries 503 with backoff — never 409.
- Design levers for real scale: virtual stock buckets per SKU (README §22), or a queue in front of hot SKUs. Explain, don't build.

**Regression test**
Not a unit test — a **recorded measurement**: `PERFORMANCE.md` entry "pool=5 vs pool=10 at 60 competitors: p95, 503 count, successes" from the harness JSON reports, plus `reservation_lockTimeout_mapsTo503WithRetryAfter` (set lock timeout 100 ms, hold the row from another thread, assert `503` + header).

---

## 12. Crash between idempotency `begin` and order commit

**Reproduce**
1. Chaos hook: in `OrderService.create`, after reservations are written but before return, `if (chaos.isEnabled("crash-after-reserve")) Runtime.getRuntime().halt(1);` (halt, not `System.exit`, to skip shutdown hooks) — behind a profile that is never active in prod.
2. `curl -X POST $API/orders -H "Idempotency-Key: K" …` → connection drops; container exits; Compose restarts it.
3. Retry the same request with `K`.

**Observe**
- The business transaction never committed → no order, no reservation (Postgres rolled back the open transaction when the connection died).
- `idempotency_key` row for `K` exists with `status=IN_PROGRESS` (it was committed in its own transaction).
- Retry: *bug variant* → `409 IDEMPOTENCY_IN_PROGRESS` **forever** — the client can never place the order. Fixed → retry succeeds (`201`) because a stale `IN_PROGRESS` row (older than N seconds, or with a `lock_owner` instance id that is no longer alive) is treated as reclaimable.

**Inspect logs**
- `grep '"key":"K"' api.log` across the restart — `NEW` before the crash, then `STALE_IN_PROGRESS_RECLAIMED` (your log event) after.
- `SELECT status, created_at, now()-created_at AS age FROM idempotency_key WHERE key='K';`

**Diagnose**
The two-transaction design (begin in its own tx, then business tx) is deliberate — it makes concurrent duplicates safe — but it introduces a crash window where the claim outlives the work. Every "claim then do" design needs a lease/expiry on the claim.

**Fix / design around**
- Treat `IN_PROGRESS` older than a lease (e.g. 60 s, generous vs the max request time) as reclaimable: `UPDATE idempotency_key SET created_at=now() WHERE … AND status='IN_PROGRESS' AND created_at < now()-interval '60 s'` → if 1 row updated, proceed as `NEW`.
- Alternatively store the claim **inside** the business transaction with `REQUIRES_NEW` only for the conflict check — analyse why that reopens the concurrent-duplicate window before choosing it.
- The purge job also deletes stale `IN_PROGRESS` rows.
- LedgerX M4 formalises this as "retry-after-crash" tests with a fault-injection hook; do the simple version here.

**Regression test**
`orderCreate_staleInProgressKey_isReclaimedAndOrderCreated` — insert an `IN_PROGRESS` row aged 5 minutes (via the injected `Clock` or a direct SQL update), POST with that key → `201`, one order. `orderCreate_freshInProgressKey_returns409` (age 1 s) to prove the lease boundary. Harness: `flowgrid-tools load … --kill-api-after 10s` is a stretch idea; manual exercise is enough for v1.0.

---

## Cross-scenario checklist (run before `v1.0`)

- [ ] Each executed scenario has an entry in `docs/FAILURE_EXERCISES.md`: prediction, observation, root cause, change, test name
- [ ] Every regression test above that belongs to M1–M5 exists and runs in CI
- [ ] Postgres dev container runs with `log_lock_waits=on` and `log_min_duration_statement=200` so future problems are visible
- [ ] Error catalogue in `API.md` covers every code these scenarios produce (`INSUFFICIENT_STOCK`, `IDEMPOTENCY_*`, `TOKEN_EXPIRED`, `CONCURRENT_MODIFICATION`, `DEPENDENCY_UNAVAILABLE`, `ILLEGAL_STATE_TRANSITION`)
- [ ] `interview-questions.md` "troubleshooting" answers reference these scenarios by number
