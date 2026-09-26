# FlowGrid — Milestones M1–M5

> Companion to [`README.md`](./README.md). One milestone per week (weeks 4–8). Each milestone follows the loop:
> **know first → requirements → architecture → acceptance → implement → verify → debug → interview**.
> Time estimates per milestone sum to ~25–30 h of project time (the ROADMAP's project block). Learning hours are separate and listed under "know first".
>
> Rule: you write the code. Snippets here are ≤ 25 lines and only for isolated concepts.

Contents: [M1](#m1--domain-persistence-auth-week-4) · [M2](#m2--idempotent-orders--concurrent-reservations-week-5) · [M3](#m3--allocation-fulfillment-workflow-redis-week-6) · [M4](#m4--operations-dashboard-returns-transfers-week-7) · [M5](#m5--deploy-document-measure-week-8)

---

## M1 — Domain, persistence, auth (Week 4)

**Goal:** a secured, documented, tested CRUD API over the core domain, running against Postgres in Docker, with migrations and CI. Tag `v0.1`. Tier: **MVP core**.

### 1. What to know first (~8 h learning this week)

| Topic | File | Why now |
|---|---|---|
| Spring DI, configuration, profiles | [`../../05-spring-boot/01-core-di.md`](../../05-spring-boot/01-core-di.md) | Every bean you write this week |
| Controllers, DTOs, `ResponseEntity`, `@Valid` | [`../../05-spring-boot/02-web-layer.md`](../../05-spring-boot/02-web-layer.md) | CRUD endpoints |
| JPA entities, relationships, repositories, `open-in-view=false`, projections | [`../../05-spring-boot/03-data-jpa.md`](../../05-spring-boot/03-data-jpa.md) | Warehouse/Product/SKU/InventoryLevel mapping |
| Bean Validation, `@RestControllerAdvice`, `ProblemDetail` | [`../../05-spring-boot/04-validation-errors.md`](../../05-spring-boot/04-validation-errors.md) | Error catalogue |
| Spring Security 6 `SecurityFilterChain`, JWT, BCrypt, method security | [`../../05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md) | Auth + role matrix |
| SQL joins/aggregation | [`../../04-sql-databases/02-joins-aggregation.md`](../../04-sql-databases/02-joins-aggregation.md) | Inventory-by-warehouse queries |
| Schema design, constraints | [`../../04-sql-databases/04-schema-design.md`](../../04-sql-databases/04-schema-design.md) | The tables in README §8 |
| JDBC/ORM mapping pitfalls | [`../../04-sql-databases/08-jdbc-orm.md`](../../04-sql-databases/08-jdbc-orm.md) | Why Flyway owns the schema, not `ddl-auto` |
| REST conventions, pagination, OpenAPI | [`../../06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md) | Endpoint table |
| Compose for Postgres | [`../../11-docker/compose.md`](../../11-docker/compose.md) | Dev stack |
| GitHub Actions basics | [`../../13-cicd/github-actions.md`](../../13-cicd/github-actions.md) | `mvn verify` on every PR |
| JUnit 5 / Spring test slices / Testcontainers | [`../../09-testing/junit5.md`](../../09-testing/junit5.md), [`spring-testing.md`](../../09-testing/spring-testing.md), [`testcontainers.md`](../../09-testing/testcontainers.md) | Slice tests on a real Postgres |

Concepts you must be able to explain before Friday: entity vs DTO; `@Transactional` at the service layer; why `ddl-auto=validate` + Flyway; how a `SecurityFilterChain` orders filters; what `ProblemDetail` is; offset pagination.

### 2. Requirements (testable)

1. `docker compose up -d` starts Postgres 16 (+ Redis 7, unused yet) with health checks; the app starts with `spring.jpa.hibernate.ddl-auto=validate` and Flyway `V1__baseline.sql` (+ later `V2…`) creates: `warehouse`, `product`, `sku`, `inventory_level`, `stock_adjustment`, `app_user`, `audit_event` (columns/constraints per README §8.3).
2. CRUD endpoints for warehouses (ADMIN), products and SKUs (OPS_MANAGER+), read for VIEWER+; all per README §9.2.
3. `GET /inventory` with filters `warehouseId`, `skuId`, `skuCode`, `belowThreshold`, pagination and a sort whitelist; response includes computed `available`.
4. `POST /inventory/adjustments` changes `on_hand` by `delta` with a reason; result below zero → `409 NEGATIVE_STOCK`; each success writes exactly one `audit_event`.
5. Bean Validation on every request DTO; failures → `400 VALIDATION_FAILED` with `errors[]`.
6. All errors are `application/problem+json` with `code`, `requestId`, `timestamp`.
7. `POST /auth/login` returns a JWT; every other endpoint requires `Authorization: Bearer`; missing/invalid → `401`, wrong role → `403`.
8. Role matrix rows for M1 endpoints enforced with `@PreAuthorize`.
9. `X-Request-Id` accepted/generated, returned, and present in every log line (MDC).
10. springdoc UI at `/swagger-ui.html` with bearer auth working; every endpoint annotated.
11. CI workflow runs `mvn -B verify` on PR and push; a JaCoCo report is produced.
12. An `admin` user is seeded from env vars (`FLOWGRID_ADMIN_PASSWORD`), never hard-coded.

### 3. Architectural guidance

**Layering per feature package.** `Controller → Service → Repository`. Entities stay inside the feature; DTOs cross the boundary. Services are the only `@Transactional` layer.

**Inventory level as the concurrency hotspot — design it now, lock it in M2.** One row per (warehouse, sku), unique. `available` is a method on the entity (`onHand - reserved`) and a computed field on the DTO. Add the `CHECK` constraints in `V1` — you want the DB to refuse bad states even before you write the locking code.

**Audit as a service, not an aspect (for now).** `AuditService.record(action, entityType, entityId, summaryMap)` called explicitly from services inside the same transaction. An AOP version is a possible refactor later; explicit calls are easier to test and reason about at this stage.

**Error flow.** Domain exception (`InsufficientStockException extends DomainException(ErrorCode)`) → `GlobalExceptionHandler` → `ProblemDetail`. Framework errors (`MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `AccessDeniedException`, `AuthenticationException`) mapped explicitly. Unknown → `500 INTERNAL_ERROR` logged at ERROR with the request id.

**Pagination.** Accept `Pageable` via `@PageableDefault(size=20)`; validate `size ≤ 100`; validate sort properties against a per-endpoint `Set<String>`; map `Page<T>` to your own `PageResponse<T>`.

**Security chain (Spring Security 6 style):** `SecurityFilterChain` bean: `csrf.disable()`, `sessionManagement(STATELESS)`, `authorizeHttpRequests` permit `/auth/**`, `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`, everything else `authenticated()`, `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)`, `exceptionHandling` with an `AuthenticationEntryPoint` and `AccessDeniedHandler` that both emit ProblemDetail (otherwise you get empty 401/403 bodies). `@EnableMethodSecurity` for `@PreAuthorize`.

**Request-id filter.** `OncePerRequestFilter` registered at highest precedence: read/generate id → `MDC.put("requestId", id)` → set response header → `try { chain } finally { MDC.clear() }`.

**Sequence — stock adjustment:**
```
Client → InventoryController.adjust(dto)  [@Valid, @PreAuthorize(M+ or associate-own-warehouse)]
  → InventoryService.adjust(...)  [@Transactional]
      → InventoryLevelRepository.findByWarehouseIdAndSkuId (M1: plain read; M2: FOR UPDATE)
      → level.applyDelta(delta)   → throws NegativeStockException if < 0
      → StockAdjustmentRepository.save
      → AuditService.record("STOCK_ADJUSTED", ...)
  ← InventoryLevelDto (201)
```

### 4. Acceptance criteria

- [ ] Fresh clone: `docker compose up -d && mvn spring-boot:run` starts cleanly; Flyway applies migrations; `/actuator/health` is `UP`
- [ ] Swagger UI: log in as admin via `/auth/login`, click Authorize, create a warehouse, product, SKU, adjust stock, list inventory filtered by warehouse — all from the UI
- [ ] `curl` without a token → `401` ProblemDetail; VIEWER creating a warehouse → `403` ProblemDetail
- [ ] Duplicate warehouse code → `409 DUPLICATE_CODE`; adjustment below zero → `409 NEGATIVE_STOCK` and **no** audit event written
- [ ] Invalid sort field → `400 INVALID_SORT`; `size=500` → `400`
- [ ] Every log line in JSON with `requestId`
- [ ] CI green on the PR; JaCoCo artefact present
- [ ] `docs/DATABASE.md` has the ERD (Mermaid ok) for the M1 tables; `docs/adr/ADR-001-flyway-and-identity-keys.md` written
- [ ] Tag `v0.1`

### 5. Now implement it (~27 h)

| # | Task | Est. |
|---|---|---:|
| 1 | Repo init, `pom.xml` (Boot 3.x parent, web, validation, data-jpa, security, flyway, postgres, actuator, springdoc, testcontainers, jjwt/nimbus, logstash encoder, jacoco plugin), `.gitignore`, `.editorconfig`, PR template, issue labels + milestones M1–M5 | 1.5 |
| 2 | `docker-compose.yml` (postgres 16, redis 7, healthchecks, volumes), `application-dev.yml`, `.env.example` | 1 |
| 3 | `V1__baseline.sql` for all M1 tables with constraints + indexes; `ddl-auto=validate` | 2 |
| 4 | Entities + repositories for warehouse/product/sku/inventory_level/stock_adjustment/app_user/audit_event; `@Enumerated(STRING)`; JSONB mapping for `sku.attributes` (Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)`) | 2.5 |
| 5 | Common: `RequestIdFilter` + MDC + logback JSON; `PageResponse`, sort whitelist; `ErrorCode`, `DomainException`, `GlobalExceptionHandler` with ProblemDetail | 2.5 |
| 6 | Catalog feature: DTOs, mapper, service, controller (products with nested SKUs; SKUs) | 2.5 |
| 7 | Inventory feature: warehouse CRUD, inventory list with filters (`Specification`), adjustments with audit | 3 |
| 8 | Auth: `AppUser`, `UserDetailsService`, BCrypt, `JwtService` (issue/verify), `JwtAuthFilter`, `SecurityFilterChain`, entry point/denied handler, `/auth/login`, `/me`, admin seed from env | 3.5 |
| 9 | Method security + role matrix on all M1 endpoints; `@warehouseAccess` bean for the own-warehouse rule | 1.5 |
| 10 | springdoc config + annotations + examples | 1.5 |
| 11 | Testcontainers base class (`@ServiceConnection`), slice tests (`@DataJpaTest` constraints, `@WebMvcTest` controllers + security), one `@SpringBootTest` smoke | 3.5 |
| 12 | `ci.yml` with Maven cache + JaCoCo upload; branch protection | 1 |
| 13 | `docs/DATABASE.md` ERD v1, ADR-001, README skeleton, tag `v0.1` | 1 |

### 6. Verification

**Tests you write (names are the contract):**
- `warehouseController_createWithDuplicateCode_returns409ProblemDetail` — POST twice, second → 409, body has `code=DUPLICATE_CODE`, `requestId` non-empty
- `warehouseController_asViewer_returns403ProblemDetail` — `@WithMockUser(roles="VIEWER")` → 403
- `inventoryLevel_reservedGreaterThanOnHand_violatesCheckConstraint` — `@DataJpaTest`, save `on_hand=1, reserved=2`, `flush()` → `DataIntegrityViolationException`
- `inventoryLevel_duplicateWarehouseSku_violatesUniqueConstraint`
- `stockAdjustment_negativeResult_rejectedWith409AndNoAuditEvent` — service test; assert `auditEventRepository.count()` unchanged
- `stockAdjustment_success_writesExactlyOneAuditEventWithActorAndRequestId`
- `inventoryList_filterByWarehouseAndBelowThreshold_returnsOnlyMatching`
- `inventoryList_unknownSortField_returns400InvalidSort`
- `jwtAuthFilter_missingHeader_returns401ProblemDetail` / `_expiredToken_returns401WithCodeTokenExpired` / `_tamperedSignature_returns401TokenInvalid`
- `login_wrongPassword_returns401BadCredentials`
- `openApi_docsListAllM1Paths`
- `requestIdFilter_echoesProvidedIdAndGeneratesWhenAbsent`

**curl checks (save as `docs/curl/m1.sh`):**
```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"'"$FLOWGRID_ADMIN_PASSWORD"'"}' | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" 'localhost:8080/api/v1/inventory?warehouseId=1&sort=available,asc' | jq .
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/v1/warehouses      # expect 401
```

### 7. Debugging scenarios

| Symptom | Investigate | Likely cause |
|---|---|---|
| App fails to start: `Schema-validation: missing column` | Compare entity field names vs `V1` columns; check `spring.jpa.properties.hibernate.physical_naming_strategy` | Naming strategy mismatch (camelCase vs snake_case) or a column you forgot in the migration |
| Every request returns `403` even with a valid token | Debug log for `org.springframework.security`; check filter order and whether your filter sets `SecurityContextHolder`; check `ROLE_` prefix | `hasRole('ADMIN')` expects authority `ROLE_ADMIN`; or the JWT filter is registered after the authorization filter |
| `401` responses have an empty body | Look at what handles `AuthenticationException` | Default entry point; you need a custom `AuthenticationEntryPoint` writing ProblemDetail |
| `LazyInitializationException` when serialising `ProductDto` | Where does the SKU list get read? | Entity leaked to the controller or mapping outside the transaction; map inside the service, keep `open-in-view=false` |
| `POST /inventory/adjustments` returns 201 but `audit_event` empty after a test | Is the test `@Transactional`? Is `AuditService` called after the exception path? | Test rollback or the audit call placed after the exception throw |
| Flyway "checksum mismatch" | `git diff` on `db/migration` | You edited an applied migration — add a new one, and `flyway repair` locally only |
| Testcontainers hangs on CI | Runner logs: Docker daemon available? Ryuk disabled? | Use `ubuntu-latest`; set `TESTCONTAINERS_RYUK_DISABLED` only if required by the runner |
| Swagger "Authorize" sends no header | Inspect the request in browser devtools | `@SecurityRequirement` missing or scheme name mismatch |

### 8. Interview questions (M1)

<details><summary>Why Flyway instead of <code>ddl-auto=update</code>?</summary>
Migrations are versioned, reviewable, reproducible in CI and prod; `update` never drops/renames safely and hides schema from code review. `validate` catches drift at startup.</details>

<details><summary>Why is <code>available</code> not a column?</summary>
It is derived from `on_hand − reserved`; storing it creates a second source of truth that can drift. The `CHECK (reserved <= on_hand)` constraint guarantees `available ≥ 0` for every write path (README §8.2).</details>

<details><summary>Walk me through what happens when a request with a JWT hits your API.</summary>
RequestIdFilter (MDC) → JwtAuthFilter parses the bearer header, verifies signature/expiry, builds an `Authentication` with `ROLE_x` authorities and sets the `SecurityContext` → authorization filter checks `authenticated()` → controller → `@PreAuthorize` evaluated by method security proxy → service. Failures short-circuit to the entry point / denied handler which write ProblemDetail.</details>

<details><summary>Why BCrypt and what does the cost factor mean?</summary>
Adaptive, salted, slow by design; cost 10–12 ≈ 50–250 ms per hash so brute force is expensive. Salt is stored inside the hash string. Measure your login latency.</details>

<details><summary>What is ProblemDetail and why use it?</summary>
RFC 9457 standard error body (`type`, `title`, `status`, `detail`, `instance` + extensions). Clients get a consistent, documented shape; Spring 6 has first-class support.</details>

<details><summary>Why package-by-feature?</summary>
Cohesion: everything about orders is in one place; clearer ownership; easier to extract a module later; avoids giant `service` packages. Cross-feature calls go through services, not repositories.</details>

<details><summary>What does <code>open-in-view=false</code> change?</summary>
No session held open during view rendering/serialisation; lazy loads outside the transaction fail fast, exposing N+1 and leaking-entity mistakes instead of silently issuing queries from the controller.</details>

---

## M2 — Idempotent orders + concurrent reservations (Week 5)

**Goal:** orders can be created exactly once per idempotency key and stock is reserved race-free, proven by a concurrency test on a real Postgres. Tag `v0.2`. Tier: **MVP**.

### 1. What to know first (~7 h)

| Topic | File | Why now |
|---|---|---|
| ACID, isolation levels, MVCC, row locks, deadlocks | [`../../04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md) | `FOR UPDATE` semantics; what READ COMMITTED does and does not give you |
| `@Transactional` propagation, rollback rules, self-invocation pitfall | [`../../05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md) | Boundaries of the create-order transaction |
| Java memory model, threads, `ExecutorService`, `CountDownLatch`, atomics | [`../../01-java/07-concurrency.md`](../../01-java/07-concurrency.md) | Writing the contention test |
| OS/CS view of races and locks | [`../../14-cs-fundamentals/concurrency.md`](../../14-cs-fundamentals/concurrency.md) | Explaining *why* the race exists |
| Idempotency keys, HTTP semantics of retries | [`../../06-rest-apis/http-for-apis.md`](../../06-rest-apis/http-for-apis.md) | FR-09 |
| Mockito, slices, Testcontainers | [`../../09-testing/mockito.md`](../../09-testing/mockito.md), [`testcontainers.md`](../../09-testing/testcontainers.md) | Service tests + real-DB concurrency tests |
| Multi-stage Dockerfile | [`../../11-docker/dockerfiles.md`](../../11-docker/dockerfiles.md) | API image (task 12) |
| Rebase/PR workflow | [`../../02-git/workflows.md`](../../02-git/workflows.md) | Clean history from now on |

Explain before Friday: lost update; why `SELECT` then `UPDATE` without a lock is racy under READ COMMITTED; pessimistic vs optimistic locking; what a unique constraint gives you that application code cannot; why an idempotency key must be scoped per user/endpoint.

### 2. Requirements

1. `POST /orders` (README §9.2) creates `customer_order` + `order_line`s and a `reservation` per line at one warehouse (M2: the warehouse that has enough `available` for **all** lines, lowest id wins; if none → order `REJECTED` and nothing reserved).
2. `Idempotency-Key` required; missing → `400`. First request stores `(user, endpoint, key, request_hash, IN_PROGRESS)` **before** executing; on completion stores status + body and marks `COMPLETED`; expiry 24 h; purge job hourly.
3. Same key + same hash + `COMPLETED` → replay stored status/body, no side effects. Same key + different hash → `422 IDEMPOTENCY_KEY_REUSED`. Same key while `IN_PROGRESS` → `409 IDEMPOTENCY_IN_PROGRESS` + `Retry-After: 1`.
4. Two concurrent requests with the same key → exactly one order (enforced by the unique constraint, not by luck).
5. Reservation happens under `SELECT … FOR UPDATE` on `inventory_level` rows, locked in a **deterministic order** (by `inventory_level.id`) to avoid deadlocks between multi-line orders.
6. `reserved` never exceeds `on_hand` (DB `CHECK` + service check); the loser gets `409 INSUFFICIENT_STOCK` with sku/warehouse/shortfall.
7. Order state machine: `RECEIVED → RESERVED | REJECTED`; `RESERVED → CANCELLED` (M3 adds the rest). Reservation state machine: `ACTIVE → RELEASED` (M3: `→ ALLOCATED → CONSUMED`). Illegal transitions → `409 ILLEGAL_STATE_TRANSITION`.
8. `POST /orders/{id}/cancel` releases all `ACTIVE` reservations and decrements `reserved` atomically; audited.
9. An **experiment**, not a feature: implement reservation also with `@Version` optimistic locking behind a flag/profile, run the same contention test, record retries/failures, and write ADR-003 choosing pessimistic locking (or justify the opposite).
10. `GET /orders`, `GET /orders/{id}` with lines and reservations; list uses a summary projection (no N+1 — asserted by a test).
11. Multi-stage Dockerfile builds the API image; `docker compose --profile app up` runs it against the Compose Postgres.

### 3. Architectural guidance

**Order creation sequence with idempotency (draw this for ARCHITECTURE.md):**
```
Client ──POST /orders, Idempotency-Key: K, body B──▶ IdempotencyFilter/Interceptor
  1. h = sha256(canonical(B)); look up (user, "POST /api/v1/orders", K)
  2. not found → INSERT (…, h, IN_PROGRESS)  [own short tx; unique violation ⇒ someone else inserted ⇒ goto 3]
  3. found: hash ≠ h ⇒ 422 · status IN_PROGRESS ⇒ 409 + Retry-After · COMPLETED ⇒ replay stored response
  4. proceed → OrderController → OrderService.create(B)  [@Transactional]
        a. validate SKUs exist/active (catalog service)
        b. choose warehouse: query inventory levels for all skus; pick lowest-id warehouse where every line fits (M2 rule)
        c. lock: SELECT … FROM inventory_level WHERE id IN (…) ORDER BY id FOR UPDATE
        d. re-check available ≥ qty for each (the read before the lock was only a hint!)
        e. reserved += qty per level; insert reservations (ACTIVE); order status RESERVED
        f. AuditService.record(ORDER_CREATED, ORDER_RESERVED)
        g. commit ⇒ locks released
  5. wrap response: UPDATE idempotency_key SET status=COMPLETED, response_status, response_body
  ◀── 201 OrderDto
```
Key decisions to write down: the idempotency row is committed in its **own** transaction *before* the business transaction (so a crash mid-way leaves `IN_PROGRESS`; the purge job or a `stale after 60 s → treat as new` rule handles it — pick one and test it in failure scenario 4); `canonical(B)` = JSON re-serialised with sorted keys, no whitespace; the stored body is what the client gets on replay, byte for byte.

**`SELECT … FOR UPDATE` in Spring Data JPA (isolated snippet, ≤ 25 lines):**
```java
public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("select l from InventoryLevel l where l.id in :ids order by l.id")
  List<InventoryLevel> lockAllByIdInOrder(@Param("ids") Collection<Long> ids);

  // native alternative, useful to show the actual SQL in interviews:
  @Query(value = "select * from inventory_level where warehouse_id = :w and sku_id = :s for update",
         nativeQuery = true)
  Optional<InventoryLevel> lockOne(@Param("w") long warehouseId, @Param("s") long skuId);
}
```
Must be called inside an active transaction or JPA throws `TransactionRequiredException`. Verify in the Postgres log (`log_statement=all` in dev) that `for update` is really emitted — Hibernate 6 appends it for Postgres.

**Idempotency-Key filter sketch (≤ 25 lines, shape only):**
```java
@Component @Order(Ordered.LOWEST_PRECEDENCE - 10)  // after security, before controller
class IdempotencyFilter extends OncePerRequestFilter {
  protected boolean shouldNotFilter(HttpServletRequest r) {
    return !(r.getMethod().equals("POST") && IDEMPOTENT_PATHS.matches(r.getRequestURI()));
  }
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
    String key = req.getHeader("Idempotency-Key");
    if (key == null) { problems.write(res, ErrorCode.IDEMPOTENCY_KEY_MISSING); return; }
    var cached = new ContentCachingRequestWrapper(req);   // body must be readable twice
    var hash = Hashing.sha256Canonical(cached);           // reads + caches the body
    switch (store.begin(currentUserId(), endpoint(req), key, hash)) {   // own tx: INSERT … or classify existing
      case REPLAY r -> { problems.replay(res, r); return; }
      case IN_PROGRESS -> { problems.write(res, ErrorCode.IDEMPOTENCY_IN_PROGRESS, "Retry-After", "1"); return; }
      case MISMATCH -> { problems.write(res, ErrorCode.IDEMPOTENCY_KEY_REUSED); return; }
      case NEW -> { /* fall through */ }
    }
    var wrapped = new ContentCachingResponseWrapper(res);
    chain.doFilter(cached, wrapped);
    store.complete(currentUserId(), endpoint(req), key, wrapped.getStatus(), wrapped.getContentAsByteArray());
    wrapped.copyBodyToResponse();
  }
}
```
Design questions to answer yourself: is a `HandlerInterceptor` cleaner than a filter (it runs after Spring MVC routing and has the principal)? What if the business call throws — do you store the 4xx/5xx response too (store 4xx yes; 5xx no — delete the row so a retry can succeed)? Where does the row live: Postgres (durable replay) or Redis (fast, TTL native, lost on restart)? Default: Postgres; ADR-004.

**State machines as tables** (put in `docs/ARCHITECTURE.md`; implement as an `EnumMap<State, Set<State>>` checked by a `transitionTo` method on the entity):

Order (M2 subset):
| From \ To | RESERVED | REJECTED | CANCELLED |
|---|:-:|:-:|:-:|
| RECEIVED | ✔ | ✔ | – |
| RESERVED | – | – | ✔ |

Reservation (M2): `ACTIVE → RELEASED` only.

**Optimistic alternative** (`@Version` on `inventory_level`): read → check → `reserved += qty` → flush → `OptimisticLockException` for losers → retry N times with jitter. Under 10 threads/1 unit you will see 9 retries per round; under 50 threads/100 units you will see many failures despite stock existing. That is the point of the experiment: measure and record `attempts`, `failures`, `p95` for both in `PERFORMANCE.md`.

**Transaction boundary rules:** the create-order transaction contains: lock, check, write reservations, write order, write audit. It does **not** contain: HTTP calls, cache writes, the idempotency bookkeeping. Keep it under ~10 ms of DB time — measure with `log_min_duration_statement`.

### 4. Acceptance criteria

- [ ] `reserve_whenOneUnitAndTenConcurrentOrders_exactlyOneSucceeds` passes 20 times in a row locally and in CI (`mvn verify -Dsurefire.rerunFailingTestsCount=0`)
- [ ] Postgres log shows `for update` on reservation; a manual test with two `psql` sessions shows the second blocks until the first commits
- [ ] Replaying a `POST /orders` with the same key returns the same `id` and creates no new rows; different body → `422`
- [ ] Cancel releases reservations: `reserved` returns to the previous value; cancelling twice → `409`
- [ ] Orders list of 200 orders issues ≤ 3 SQL statements (asserted)
- [ ] ADR-003 (pessimistic vs optimistic, with your measured numbers) and ADR-004 (idempotency store location) committed
- [ ] `docker build` produces an image < 300 MB running as non-root; `docker compose --profile app up` serves `/actuator/health`
- [ ] Tag `v0.2`

### 5. Now implement it (~28 h)

| # | Task | Est. |
|---|---|---:|
| 1 | `V3__orders.sql`: `customer_order`, `order_line`, `reservation`, `idempotency_key` with constraints/indexes | 1.5 |
| 2 | Order/OrderLine/Reservation entities, state enums, `StateMachine` helper + `IllegalStateTransitionException` | 2 |
| 3 | `IdempotencyKey` entity/repo/service (`begin`, `complete`, `purgeExpired`) + canonical JSON hashing + unit tests | 3 |
| 4 | `IdempotencyFilter`/interceptor + ProblemDetail responses + `Retry-After` | 2.5 |
| 5 | `ReservationService.reserve(orderId, warehouseId, lines)` with ordered `FOR UPDATE`, re-check, audit; `release(orderId)` | 3 |
| 6 | `OrderService.create` (warehouse choice M2 rule, reservation, states), `cancel`, DTOs, controller, OpenAPI | 3 |
| 7 | Orders list projection + detail with `@EntityGraph`; statement-count test | 2 |
| 8 | Concurrency test harness (`ExecutorService`, `CountDownLatch` start gate, collect results) + the named tests; non-transactional test with cleanup | 3.5 |
| 9 | `@Version` variant behind a profile + same tests + record numbers; ADR-003, ADR-004 | 3 |
| 10 | Idempotency purge `@Scheduled` job (+ `Clock` injection) + test | 1 |
| 11 | Postgres `log_statement`/`log_min_duration_statement` in dev compose; verify SQL | 0.5 |
| 12 | Multi-stage Dockerfile, non-root, healthcheck, compose `app` profile | 2 |
| 13 | Docs: ARCHITECTURE sequence diagram for order creation; API.md orders section; tag | 1 |

### 6. Verification

**Tests (mandatory names):**
- `orderCreate_missingIdempotencyKey_returns400`
- `orderCreate_sameKeySameBody_returnsSameOrderIdAndCreatesOneOrder` — assert `customerOrderRepository.count()==1`, both responses equal byte-for-byte
- `orderCreate_sameKeyDifferentBody_returns422`
- `orderCreate_sameKeyWhileInProgress_returns409WithRetryAfter` — simulate by inserting an `IN_PROGRESS` row first
- `orderCreate_concurrentSameKey_createsExactlyOneOrder` — 10 threads, same key/body; expect 1 order; responses are 201 or 409 (in-progress), never 500
- `reserve_whenOneUnitAndTenConcurrentOrders_exactlyOneSucceeds` — `on_hand=1`, 10 threads each creating a 1-unit order via the service (or HTTP); assert exactly one `RESERVED`, nine `REJECTED`/409, `reserved==1`, `available==0`, DB constraint never violated (no `DataIntegrityViolationException` in results)
- `reserve_whenFiveUnitsAndTenConcurrentSingleUnitOrders_exactlyFiveSucceedAndReservedEqualsFive`
- `reserve_multiLineOrdersLockingInDifferentOrder_noDeadlock` — two orders with lines (A,B) and (B,A) concurrently, 50 iterations; no `CannotAcquireLockException`/deadlock detected (because you lock by id order)
- `reserve_insufficientForOneLine_reservesNothingAndOrderRejected`
- `cancelOrder_releasesAllReservationsAtomically` — inject a failure on the second reservation release (e.g. a spy throwing) → nothing released, order still `RESERVED`
- `cancelOrder_afterPicked_returns409IllegalTransition` (state prepared directly in DB)
- `ordersList_200Orders_executesAtMost3Statements` (Hibernate `Statistics` or datasource-proxy)
- `idempotencyPurgeJob_removesOnlyExpiredKeys`
- Optimistic variant: `reserveOptimistic_tenThreadsOneUnit_exactlyOneSucceeds_andRecordsRetries`

**Manual race with psql (do this once, it makes the concept concrete):**
```
-- session 1                                      -- session 2
BEGIN;                                            BEGIN;
SELECT * FROM inventory_level WHERE id=1 FOR UPDATE;
                                                  SELECT * FROM inventory_level WHERE id=1 FOR UPDATE;  -- blocks
UPDATE inventory_level SET reserved=reserved+1 WHERE id=1;
COMMIT;                                           -- unblocks, sees reserved=1
```

**curl:** `for i in 1 2; do curl -s -X POST …/orders -H "Idempotency-Key: $K" -d @order.json | jq .id; done` → same id.

### 7. Debugging scenarios

| Symptom | Investigate | Likely cause |
|---|---|---|
| Contention test: two orders succeed for one unit | Postgres log — is `for update` present? Is the reserve method called through the Spring proxy (`@Transactional` on a `public` method called from *another* bean)? | Self-invocation bypasses the proxy → no transaction → lock ignored; or you checked `available` before locking and never re-checked |
| `TransactionRequiredException: no transaction is in progress` | Where is `@Transactional`? | Locking query called outside a transaction (e.g. from the filter or a test without tx) |
| `CannotAcquireLockException: deadlock detected` | Postgres log shows the two processes and their lock waits | Multi-line orders lock rows in different orders — sort ids |
| Test passes alone, fails in the suite | Shared static container + leftover rows; `@Transactional` on the concurrency test | Data from a previous test; concurrency tests must commit for real and clean up in `@AfterEach` |
| Replay returns 201 but a *new* order is created | Which transaction commits the idempotency row? Does the filter run before security (no principal → user_id null → key scope wrong)? | Filter order or the row written only after success |
| `IN_PROGRESS` rows pile up | Query the table after a failed request | Exception path never completes/deletes the row |
| Optimistic variant: `StaleObjectStateException` bubbles as 500 | Handler mapping | Map `ObjectOptimisticLockingFailureException` → retry then `409 CONCURRENT_MODIFICATION` |
| Test hangs | Thread dump (`jcmd <pid> Thread.print`) | Latch never counted down because an exception escaped the task before `countDown()` — use `finally` |

### 8. Interview questions (M2)

<details><summary>How did you prevent two orders reserving the same last unit?</summary>
The reservation runs in one transaction that locks the `inventory_level` row(s) with `SELECT … FOR UPDATE` (ordered by id), re-checks `on_hand − reserved ≥ qty` under the lock, then increments `reserved`. The second transaction blocks until the first commits and then sees the updated value and fails the check. A DB `CHECK (reserved <= on_hand)` is the last line of defence. Proven by `reserve_whenOneUnitAndTenConcurrentOrders_exactlyOneSucceeds`.</details>

<details><summary>Why not just check <code>available</code> in Java and update?</summary>
Under READ COMMITTED both transactions read the same snapshot, both pass the check, both write → oversell (lost update). Locks or a conditional `UPDATE … WHERE reserved + :q <= on_hand` (check rows affected) are needed. The conditional update is a valid, lighter alternative — mention it.</details>

<details><summary>Pessimistic vs optimistic locking — what did you measure?</summary>
Your ADR-003 numbers. Expected shape: with high contention on one row, pessimistic queues writers and each attempt succeeds or fails once; optimistic causes many retries and wasted work. Optimistic wins when conflicts are rare and you can't hold a DB connection. Say which you chose and the trade-off (lock wait time, pool exhaustion risk).</details>

<details><summary>What exactly does the idempotency key protect against, and what doesn't it?</summary>
Protects against client/network retries producing duplicate side effects and against concurrent duplicate submits (unique constraint). Doesn't protect against a client generating a new key for a logically duplicate order, or against replay after expiry. Scoped per user+endpoint so keys can't collide across tenants.</details>

<details><summary>Why store the response, not just "done"?</summary>
The client that retried never saw the first response; replaying the identical body (same order id, 201) makes the retry indistinguishable from the original — that is the definition of idempotent from the client's view.</details>

<details><summary>Where are the transaction boundaries and why?</summary>
Idempotency `begin` in its own tx (must be visible to concurrent duplicates immediately); order creation + reservation + audit in one tx (atomic); idempotency `complete` after the tx commits. Nothing slow inside the locked section.</details>

<details><summary>How do you avoid deadlocks with multi-line orders?</summary>
Always acquire row locks in a global order (by `inventory_level.id`). Deadlocks need a cycle; consistent ordering makes cycles impossible. Postgres still detects deadlocks after `deadlock_timeout` (1 s) and aborts one — map to a retry.</details>

<details><summary>What isolation level do you run at and why not SERIALIZABLE?</summary>
READ COMMITTED (Postgres default) + explicit row locks: predictable and cheap. SERIALIZABLE would detect the anomaly but abort with serialization failures you must retry; for a known hot row, explicit locking is simpler and the failure mode (waiting) is gentler than retry storms.</details>

---

## M3 — Allocation, fulfillment workflow, Redis (Week 6)

**Goal:** an order is allocated to the best warehouse deterministically, flows pick → pack → ship (partially if needed), and the catalog is cached in Redis with correct invalidation and graceful degradation. Tag `v0.3`.

### 1. What to know first (~7 h)

| Topic | File | Why now |
|---|---|---|
| Normalisation, indexes, `EXPLAIN`, CTEs/window functions | [`../../04-sql-databases/04-schema-design.md`](../../04-sql-databases/04-schema-design.md), [`05-indexes-performance.md`](../../04-sql-databases/05-indexes-performance.md), [`03-advanced-queries.md`](../../04-sql-databases/03-advanced-queries.md) | Allocation candidate query, workload counts, pick list queries |
| Redis types, TTL, cache-aside, invalidation, Lettuce | [`../../04-sql-databases/redis.md`](../../04-sql-databases/redis.md) | FR-19/20 |
| Caching strategy (what to cache, stampede, consistency) | [`../../15-system-design/caching.md`](../../15-system-design/caching.md) | Deciding what *not* to cache |
| Spring Cache abstraction, `@Scheduled`, health indicators | [`../../05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md), [`06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md) | `@Cacheable` vs manual `RedisTemplate`; Redis health |
| State machines (design) | [`../../14-cs-fundamentals/software-engineering-practices.md`](../../14-cs-fundamentals/software-engineering-practices.md) | Order/pick/shipment machines |
| JavaScript core + async (prep for M4) | [`../../07-javascript-typescript/01-javascript-core.md`](../../07-javascript-typescript/01-javascript-core.md), [`02-async-javascript.md`](../../07-javascript-typescript/02-async-javascript.md) | Next week's dashboard |

### 2. Requirements

1. **Allocation** (`AllocationService.allocate(orderId)`, called automatically after reservation and via `POST /orders/{id}/allocate`): choose one warehouse for the order (v1.0) using the score below; store `allocation` rows, `allocated_warehouse_id`, and `allocation_explanation` (JSON breakdown per candidate). Deterministic: same DB state → same choice; ties → lowest warehouse id.
2. Only warehouses that can cover **every** line are candidates (v1.0). If none → order stays `RESERVED`? No: in M3, reservation is made at the allocated warehouse — so allocation and reservation merge: candidates computed first, then lock + reserve at the winner; if the winner fails the locked re-check, try the next candidate; if all fail → `BACKORDERED` (new state) with nothing reserved (`allocation_everyWarehouseShort_orderBackorderedAndNothingReserved`).
3. Score formula (weights configurable in `application.yml`, defaults documented):
   ```
   score(w) = 0.35 * availabilityRatio      // min over lines of available/qty, capped at 1 → prefers comfortably stocked
            + 0.25 * regionMatch            // 1 if w.region == order.shipRegion, 0.5 same zone, else 0
            + 0.15 * (1 - workloadRatio)    // open pick-list items today / daily_pick_capacity, capped at 1
            + 0.15 * capacityHeadroom       // (capacity - allocatedToday) / capacity, capped [0,1]
            + 0.10 * priorityBoost          // EXPRESS: regionMatch again (distance matters more), STANDARD: 0
   candidates sorted by (score DESC, warehouse.id ASC)
   ```
   `workloadRatio` and `allocatedToday` come from one aggregate query per allocation (not per candidate).
4. **Pick lists:** `POST /pick-lists {warehouseId, orderIds?}` gathers `ALLOCATED` orders' lines at that warehouse into a pick list (`OPEN`); `start` → `IN_PROGRESS`; `pick` records `quantity_picked ≤ requested`, moves `allocated → picked` on the inventory level; `complete` marks short items `SHORT` and lines `PICKED`/`SHORT`.
5. **Packing:** `POST /orders/{id}/packages` from picked lines only (`409 NOT_PICKED`); a line may be split across packages.
6. **Shipping:** `POST /orders/{id}/ship {packageIds, carrier}` creates a shipment, decrements `on_hand`, `reserved`, `allocated`, `picked` by shipped quantities, increments `shipped_total`, sets `order_line.quantity_shipped`, reservation `CONSUMED` (fully) and order → `SHIPPED` or `PARTIALLY_SHIPPED`.
7. **Partial fulfillment:** short lines keep the order `PARTIALLY_SHIPPED`; a later pick list can include the remainder; cancelling the remainder releases only unshipped quantity.
8. **Redis cache-aside** for `GET /products/{id}`, `GET /skus/{id}`, `GET /skus?code=` (TTL 10 min, key `fg:v1:sku:{id}`), invalidated on update/deactivate; **low-stock view** cached 60 s, invalidated by the low-stock job (M4) and by adjustments crossing a threshold (or just TTL — decide).
9. **Redis down:** reads fall through to DB, writes skip invalidation but log; one WARN per 30 s; `/actuator/health` shows `redis: DOWN` while overall status stays `UP` (custom health group) — decide and document.
10. State machines complete (below); every illegal transition tested.

### 3. Architectural guidance

**Allocation + reservation flow (M3 replaces M2's "lowest id" rule):**
```
OrderService.create → (tx A) persist order RECEIVED + lines; commit
  → AllocationService.allocateAndReserve(orderId)   [tx B]
      1. candidates = query: for each active warehouse, per-line available, region, workload, capacity  (one SQL, GROUP BY)
      2. feasible = candidates where every line fits; scored + sorted (score DESC, id ASC); store explanation for all
      3. for w in feasible:  lock levels (w, skus) ORDER BY id FOR UPDATE; re-check; if ok → reserve, allocate, break
      4. none → order BACKORDERED, explanation stored, nothing reserved
```
Why two transactions? So a `BACKORDERED` order still exists (ops can see it). Document the alternative (single tx, order rejected) and why you chose this.

**Allocation score (≤ 25 lines, the isolated concept):**
```java
record Candidate(long warehouseId, double availabilityRatio, double regionMatch,
                 double workloadRatio, double capacityHeadroom, boolean express) {
  double score(Weights w) {
    return w.availability() * clamp(availabilityRatio)
         + w.region()       * regionMatch
         + w.workload()     * (1 - clamp(workloadRatio))
         + w.capacity()     * clamp(capacityHeadroom)
         + w.priority()     * (express ? regionMatch : 0);
  }
  static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
// sorting: Comparator.comparingDouble((Candidate c) -> c.score(w)).reversed().thenComparingLong(Candidate::warehouseId)
```
Unit-test the scorer exhaustively without Spring. Store the breakdown as `{"warehouseId":2,"score":0.81,"availabilityRatio":1.0,...}` per candidate — this is what ops (and interviewers) read.

**State machines (tables → `EnumMap`):**

Order:
| From \ To | RESERVED | BACKORDERED | REJECTED | ALLOCATED | PICKING | PACKED | PARTIALLY_SHIPPED | SHIPPED | CANCELLED |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| RECEIVED | ✔ | ✔ | ✔ | – | – | – | – | – | ✔ |
| RESERVED | – | – | – | ✔ | – | – | – | – | ✔ |
| BACKORDERED | ✔ (retry) | – | – | – | – | – | – | – | ✔ |
| ALLOCATED | – | – | – | – | ✔ | – | – | – | ✔ |
| PICKING | – | – | – | – | – | ✔ | – | – | ✔ (releases unpicked) |
| PACKED | – | – | – | – | – | – | ✔ | ✔ | – |
| PARTIALLY_SHIPPED | – | – | – | – | ✔ | ✔ | – | ✔ | ✔ (remainder only) |

(Decide whether `RESERVED → ALLOCATED` collapses into one step since M3 reserves at allocation time; keep both states if you want the explanation to be inspectable before allocation. Write it down.)

Pick list: `OPEN → IN_PROGRESS → COMPLETED`; `OPEN → CANCELLED`. Reservation: `ACTIVE → ALLOCATED → CONSUMED`; `ACTIVE|ALLOCATED → RELEASED`. Shipment: `CREATED → SHIPPED`.

**Ship transaction (the one that touches the most rows):** lock the inventory levels (ordered), verify picked ≥ ship qty per line, apply the four decrements, insert shipment + join rows, update lines/reservations/order, audit. If *anything* throws, everything rolls back — that is failure scenario 3.

**Cache-aside sketch (≤ 25 lines):**
```java
public SkuDto getSku(long id) {
  String key = "fg:v1:sku:" + id;
  try {
    String hit = redis.opsForValue().get(key);
    if (hit != null) { metrics.hit(); return json.read(hit, SkuDto.class); }
  } catch (RedisConnectionFailureException | RedisSystemException e) {
    degradation.warnRateLimited("redis read failed", e);       // once per 30 s
  }
  SkuDto dto = skuRepository.findById(id).map(mapper::toDto).orElseThrow(NotFound::new);
  try { redis.opsForValue().set(key, json.write(dto), Duration.ofMinutes(10)); }
  catch (RuntimeException e) { degradation.warnRateLimited("redis write failed", e); }
  return dto;
}
// on update: save to DB, commit, THEN redis.delete(key)  — never delete before commit (stale re-fill window)
```
Configure Lettuce with a short command timeout (e.g. 200 ms) so a dead Redis costs 200 ms, not 60 s — this is the difference between "degrade" and "outage". Consider `@Cacheable` with a custom `CacheErrorHandler` as the declarative alternative; either is fine, but you must be able to explain what happens when Redis is down.

**Invalidation order:** DB commit → delete key (use `TransactionSynchronization.afterCommit` or do it in the service after the transactional method returns). Deleting inside the transaction leaves a window where a reader re-fills the cache with the *old* row.

### 4. Acceptance criteria

- [ ] Same seed data, run allocation twice → identical warehouse and identical explanation JSON
- [ ] An order shipped end-to-end via curl: create → (auto-allocated) → pick list → start → pick → complete → package → ship; inventory row shows `on_hand` and `reserved` reduced by the shipped qty, `shipped_total` increased, `allocated`/`picked` back to 0
- [ ] Partial: order of 2 lines, one short at pick → ship the other → `PARTIALLY_SHIPPED`; remaining line still `ALLOCATED` with reservation intact
- [ ] `docker compose stop redis` → catalog reads still 200 (slower), logs show one WARN per 30 s, health shows redis DOWN; `start redis` → cache resumes with no restart
- [ ] `redis-cli MONITOR` shows `GET` then `SET` on first read, `GET` only on second, `DEL` after update
- [ ] Every state-machine cell marked "–" has at least one test producing `409 ILLEGAL_STATE_TRANSITION` (parametrised test over the table is fine)
- [ ] `EXPLAIN ANALYZE` of the candidate query and of the orders list pasted into `PERFORMANCE.md` with index usage confirmed
- [ ] Tag `v0.3`

### 5. Now implement it (~29 h)

| # | Task | Est. |
|---|---|---:|
| 1 | `V4__fulfillment.sql`: allocation, pick_list(+item), package(+item), shipment(+join); order states extended; indexes | 1.5 |
| 2 | Candidate query (one SQL with aggregates), `Candidate` record, `AllocationScorer` + unit tests (ties, weights, clamps) | 3 |
| 3 | `AllocationService.allocateAndReserve` (loop over candidates, ordered locks, explanation JSON, BACKORDERED) + tests | 3.5 |
| 4 | Full state machines (`EnumMap` tables) for order/reservation/pick list/shipment + parametrised illegal-transition test | 2 |
| 5 | Pick lists: create/start/pick/complete, inventory `allocated → picked`, short handling | 3.5 |
| 6 | Packing + shipping transaction with the four decrements, partial fulfillment, reservation consumption | 3.5 |
| 7 | Cancellation extended (release unpicked, keep shipped) | 1 |
| 8 | Redis config (Lettuce timeouts), cache-aside catalog service, invalidation after commit, degradation logger | 3 |
| 9 | Low-stock cached view (query + cache + TTL) | 1 |
| 10 | Redis health indicator + health group; Testcontainers Redis in the base class; Redis-down test (stop container via Testcontainers API or use a wrong port profile) | 2 |
| 11 | End-to-end flow integration test; cache hit/miss test using Hibernate `Statistics` | 2.5 |
| 12 | `EXPLAIN` sessions; index tweaks; `PERFORMANCE.md` first entries; ADR-005 (what is cached and why) | 1.5 |
| 13 | OpenAPI + API.md for fulfillment; tag | 1 |

### 6. Verification

- `allocationScorer_regionMatchOutweighsSmallAvailabilityDifference` (weights sanity)
- `allocation_sameInputsTwice_producesIdenticalWarehouseAndScore`
- `allocation_tie_brokenByLowestWarehouseId`
- `allocation_everyWarehouseShort_orderBackorderedAndNothingReserved`
- `allocation_winnerLosesRaceAtLock_fallsBackToNextCandidate` — pre-lock the winner's row in another thread/tx and reduce its stock before releasing; assert the second candidate was chosen
- `pickList_pickMoreThanRequested_returns409OverPick`
- `pickList_complete_marksShortItemsAndKeepsReservationForShortQty`
- `pickPackShip_fullFlow_movesQuantitiesAndOrderReachesShipped` — assert all six quantity columns
- `ship_failureAfterPackageCreated_rollsBackInventoryAndShipment` — spy on `ShipmentRepository.save` to throw; assert quantities unchanged, no package/shipment rows
- `partialShip_oneLineShort_orderPartiallyShippedAndRemainderOpen`
- `cancel_partiallyShippedOrder_releasesOnlyUnshippedReservation`
- `catalogCache_secondReadHitsRedisNotDb`, `catalogCache_updateInvalidatesKey`, `catalogCache_invalidationHappensAfterCommit` (reader between DB write and commit must not re-cache stale data — hard to test; at least assert the ordering with a spy)
- `catalog_whenRedisDown_readsFromDbAndReturns200`, `catalog_whenRedisDown_logsWarnAtMostOncePer30s`
- `illegalTransitions_everyForbiddenCell_returns409` (parametrised from the table)

**redis-cli checks:** `MONITOR` during a read/read/update; `TTL fg:v1:sku:1`; `INFO stats | grep keyspace` for hit/miss counters (record for the benchmark).

### 7. Debugging scenarios

| Symptom | Investigate | Likely cause |
|---|---|---|
| Allocation picks different warehouses on repeated runs with identical data | Print candidates + scores; check sort comparator; check `HashMap` iteration order in the breakdown | Non-deterministic tie-break or floating-point equality; use `thenComparingLong(id)` and round scores to 3 dp before comparing |
| Stale SKU after update | `MONITOR` shows `DEL` before the SQL `COMMIT` | Invalidation inside the transaction; move to after-commit |
| Redis stop makes every request take 60 s | Lettuce default timeout | Set `spring.data.redis.timeout=200ms` and connect timeout; verify with the test |
| Health goes DOWN (503) when Redis stops, load balancer would kill the instance | Actuator group config | Make redis a non-fatal indicator or use a separate `readiness` group |
| Ship succeeds but `on_hand` unchanged | Which entity instance did you decrement — the locked one or a detached copy loaded earlier? | Two managed instances in the same persistence context are impossible, but a DTO-to-entity re-load outside the tx is; decrement the entity returned by the locking query |
| `409 NOT_PICKED` although the pick list is completed | Line status vs item status | Completing the pick list did not propagate to `order_line.status` |
| Candidate query slow | `EXPLAIN (ANALYZE, BUFFERS)` | Missing index on `pick_list_item (pick_list_id)` or `pick_list (warehouse_id, status)`; or correlated subquery per warehouse — rewrite with `GROUP BY` |
| Tests pass locally, Redis tests fail in CI | Container startup / port mapping | Use `@ServiceConnection` for Redis too; don't hard-code 6379 |

### 8. Interview questions (M3)

<details><summary>How does allocation work and why is it deterministic?</summary>
Weighted score from availability ratio, region match, workload, capacity headroom, priority; sorted by score then warehouse id; explanation stored. Determinism matters for reproducibility (support tickets, tests) and for explaining decisions to ops. Weights are config, not code.</details>

<details><summary>Why cache the catalog but not inventory quantities?</summary>
Catalog is read-heavy, changes rarely, staleness of minutes is harmless. Inventory quantities decide reservations; a stale value would cause oversell or false rejections. The DB row lock is the only valid read for that decision.</details>

<details><summary>What happens when Redis goes down?</summary>
Reads fall through to Postgres (cache-aside + short Lettuce timeout), writes skip invalidation, a rate-limited WARN fires, health reflects degraded. Nothing incorrect happens; latency rises. Tested with `catalog_whenRedisDown_readsFromDbAndReturns200`.</details>

<details><summary>Why invalidate after commit rather than write-through?</summary>
Delete-after-commit avoids the stale-refill window; write-through pushes uncommitted or soon-to-be-rolled-back data. Mention the remaining race (reader loads old row before commit, writes cache after the delete) and that a short TTL bounds it; a versioned key or a lease would close it if it mattered.</details>

<details><summary>How do you handle partial fulfillment?</summary>
Line-level `quantity_shipped`; ship what is picked; order becomes `PARTIALLY_SHIPPED`; remaining reservation stays; a later pick list finishes it; cancelling releases only the unshipped remainder.</details>

<details><summary>Where could data become inconsistent in the pick/pack/ship flow?</summary>
Between the four counters if they were updated in separate transactions or if a shipment row was inserted after the counters without a tx. That is why ship is one transaction with ordered locks, and scenario 3 in failure engineering proves rollback.</details>

---

## M4 — Operations dashboard, returns, transfers (Week 7)

**Goal:** a React/TS dashboard for ops and associates; returns and two-phase transfers; scheduled low-stock alerts; **Python `tools/` package with the data generator**. Tag `v0.4`.

### 1. What to know first (~7 h)

| Topic | File | Why now |
|---|---|---|
| TypeScript | [`../../07-javascript-typescript/03-typescript.md`](../../07-javascript-typescript/03-typescript.md) | Typed API client and DTO types |
| React fundamentals, hooks, forms, routing | [`../../08-react/01-fundamentals.md`](../../08-react/01-fundamentals.md), [`02-hooks.md`](../../08-react/02-hooks.md), [`03-forms-routing.md`](../../08-react/03-forms-routing.md) | Pages and forms |
| API integration + auth on the client | [`../../08-react/04-api-integration-auth.md`](../../08-react/04-api-integration-auth.md) | Token handling, 401 refresh, role-aware nav |
| Architecture + testing on the frontend | [`../../08-react/05-architecture-testing.md`](../../08-react/05-architecture-testing.md), [`../../09-testing/frontend-testing.md`](../../09-testing/frontend-testing.md) | Vitest + RTL |
| Minimal HTML/CSS | [`../../07-javascript-typescript/04-html-css-minimum.md`](../../07-javascript-typescript/04-html-css-minimum.md) | Tables, forms, layout (≈3 h total, not more) |
| Scheduling in Spring | [`../../05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md) | Low-stock job |
| Python core, testing & scripting, for Java devs | [`../../19-python/01-python-core.md`](../../19-python/01-python-core.md), [`04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md), [`python-for-java-devs.md`](../../19-python/python-for-java-devs.md) | `tools/` generator with pytest, type hints, dataclasses |
| AWS IAM + EC2 (read ahead) | [`../../12-aws/iam.md`](../../12-aws/iam.md), [`ec2.md`](../../12-aws/ec2.md) | Create the account, budget alarm and IAM user this week so M5 starts fast |

### 2. Requirements

1. **SPA (Vite + React 18 + TS 5):** login page; layout with role-aware nav; pages: Inventory (table by warehouse: sku, on_hand, reserved, available, allocated, picked, threshold flag; search by SKU code, filter warehouse/below-threshold, sortable columns, server-side pagination), Orders (list with status/region/date filters + free text; detail with lines, reservations, allocation explanation table, shipments, actions cancel/allocate), Pick lists (list for my warehouse; pick screen with quantity inputs and short marking; complete), Pack & Ship (package builder from picked lines; ship with carrier select), Low-stock (cached view + alerts with ack), Transfers (create, dispatch, receive forms), Returns (form: order line, qty, reason, disposition).
2. **API client:** typed functions per endpoint; access token in memory; refresh on 401 (if you chose refresh tokens); ProblemDetail rendered as field errors/toasts; `X-Request-Id` shown in error toasts (support-friendly).
3. **Returns:** `POST /returns` with `Idempotency-Key`; `RESTOCK` → `on_hand += qty` + adjustment (`RETURN_RESTOCK`); `QUARANTINE` → `quarantined += qty`; cannot exceed `quantity_shipped − already returned`.
4. **Transfers (two-phase):** `REQUESTED` (no stock effect) → `dispatch` (source: lock, check `available ≥ qty`, `on_hand −= qty`; status `IN_TRANSIT`) → `receive` (destination: `on_hand += received`; short receipts recorded; status `RECEIVED`); `cancel` only from `REQUESTED`. Both phases audited. Two transfers in opposite directions must not deadlock (ordered locks).
5. **Scheduled low-stock alerts:** `@Scheduled(fixedDelay)` (e.g. every 5 min; 10 s in dev) scans `available < threshold`; creates one `OPEN` alert per level (partial unique index), resolves alerts whose level recovered, invalidates the low-stock cache, logs a summary line. `POST /alerts/low-stock/{id}/ack`.
6. **`tools/` Python package (FR-26):** `flowgrid-tools generate --seed 42 --warehouses 3 --skus 120 --orders 200 --out fixtures/` writes JSON/CSV; `flowgrid-tools seed --api http://localhost:8080 --profile demo` loads via the API (admin login, idempotency keys, retries with backoff on 5xx/429); `--sql` mode bulk-inserts ≥ 50k orders via `psycopg` `COPY` for index experiments. Typed (`mypy --strict` clean), `ruff` clean, `pytest` green, README with usage and the data model it produces. Distributions configurable (regions, line counts, priority ratio, low-stock ratio, duplicate-order ratio).
7. `scripts/seed-demo-data.sh` wraps the generator; demo users created.
8. Web Dockerfile (nginx) with `/api` proxy; `docker compose --profile app up` serves the SPA on :80.

### 3. Architectural guidance

**Frontend structure:** `api/` (client + types generated by hand from OpenAPI, or via `openapi-typescript` — say which), `auth/` (context/provider, `useAuth`, `RequireRole`), `pages/<feature>/`, `components/` (DataTable with server-side paging/sorting props, ProblemDetail-aware form errors), `hooks/` (`usePagedQuery`). Keep state simple: React Query (TanStack) or plain `useEffect` + reducers — pick one and justify in an ADR; React Query gives you caching/invalidations that match the server semantics nicely.

**Own-warehouse UX:** associates see only their warehouse; managers get a warehouse selector. Enforce on the server (M1) — the UI only hides.

**Transfer sequence:**
```
dispatch(transferId)  [tx]
  lock source levels for the transfer's skus ORDER BY id FOR UPDATE
  for each line: available >= qty else 409 INSUFFICIENT_STOCK (whole dispatch fails)
  on_hand -= qty; status IN_TRANSIT; dispatched_at; audit
receive(transferId, receivedQtys)  [tx]
  lock destination levels (create missing levels first, outside the lock, idempotently)
  on_hand += received; quantity_received; status RECEIVED; audit (short receipt noted)
```
Stock in transit exists in neither warehouse's `on_hand` — it exists only as the transfer row. Ops can see "in transit" by summing `IN_TRANSIT` lines; put that on the Inventory page.

**Low-stock job:** one SQL (`available < coalesce(level.threshold, sku default)`) → upsert alerts → resolve recovered → `DEL fg:v1:lowstock:*`. If you ever run two API instances this needs a leader guard (ShedLock) — note it, don't build it.

**Generator design (Python):** pure functions from `(config, seed) → dataclasses`; `random.Random(seed)` instance passed explicitly (never the module-level `random`); sinks are adapters (`JsonSink`, `CsvSink`, `ApiSink`, `SqlSink`) with one interface `write(dataset)`; `ApiSink` uses `httpx.Client`, logs in once, sets `Idempotency-Key: gen-{seed}-{order_index}` so re-running the seed is idempotent against the API; `SqlSink` uses `psycopg` `COPY … FROM STDIN` and disables nothing (constraints stay on — if generated data violates a `CHECK`, that is a generator bug the tests must catch). Realism: SKU codes like `TS-NAV-M`, category-driven weights, regions matching the warehouses, ~70 % single-line orders, EXPRESS ~15 %, hot SKUs (Zipf-ish: 20 % of SKUs get 80 % of lines) so contention is realistic.

### 4. Acceptance criteria

- [ ] Login → inventory → filter → paginate → sort → order detail → pick → ship, all in the browser, no console errors
- [ ] Associate account cannot see manager actions and gets a friendly 403 if they craft a request
- [ ] Return with `RESTOCK` increases `on_hand` and shows an adjustment + audit; `QUARANTINE` increases `quarantined` only
- [ ] Transfer dispatched then received: source `on_hand` down, destination up, exactly once; receive with a short quantity records the difference
- [ ] Low-stock job creates exactly one open alert for a level below threshold across many runs; resolves after restock; dashboard shows it
- [ ] `cd tools && pytest && mypy --strict src && ruff check .` all green; `flowgrid-tools generate --seed 42` twice → identical files; `seed --api` against the dev stack completes and `GET /inventory` shows the data; `seed --sql` inserts 50k orders in a few minutes
- [ ] `npm test` green; ≥ 5 component tests + API-client tests (mock fetch)
- [ ] GIF recorded for the README
- [ ] Tag `v0.4`

### 5. Now implement it (~30 h)

| # | Task | Est. |
|---|---|---:|
| 1 | `V5__returns_transfers_alerts.sql`; entities | 1 |
| 2 | Returns service/controller + idempotency + tests | 2 |
| 3 | Transfers service (two-phase, ordered locks) + controller + tests incl. opposite-direction concurrency | 3.5 |
| 4 | Low-stock job + alert entity/endpoints + cache invalidation + tests with `Clock` | 2 |
| 5 | Vite scaffold, TS strict, ESLint, router, auth provider, API client with ProblemDetail handling, `RequireRole` | 3 |
| 6 | DataTable component (server paging/sorting), Inventory page | 3 |
| 7 | Orders list + detail (allocation explanation table, actions) | 3 |
| 8 | Pick list + pick screen; pack & ship screen | 3.5 |
| 9 | Low-stock, transfers, returns pages | 2.5 |
| 10 | `tools/` scaffold (pyproject, src layout, ruff, mypy, pytest, CI job), models + generator + JSON/CSV sinks + tests | 3 |
| 11 | `ApiSink` (login, idempotency keys, backoff) + `SqlSink` (COPY) + tests with mocked HTTP (`respx`) and a throwaway Postgres (Testcontainers-python or Compose) | 2.5 |
| 12 | Web Dockerfile + nginx proxy; compose profile; `seed-demo-data.sh`; frontend tests; GIF; tag | 1 |

### 6. Verification

**Backend:**
- `return_restockIncrementsOnHand_quarantineIncrementsQuarantined`
- `return_exceedingShippedQuantity_returns409`
- `transfer_dispatchThenReceive_sourceDecrementsDestinationIncrementsExactlyOnce`
- `transfer_dispatchWithInsufficientAvailable_returns409AndNothingMoves`
- `transfer_receiveShort_recordsQuantityReceivedAndKeepsAudit`
- `transfer_twoOppositeTransfersConcurrently_noDeadlockOrDeterministicRetry` — A→B and B→A on the same SKUs, 50 iterations, both succeed or one fails cleanly with 409/retry; no `deadlock detected` in logs
- `transfer_cancelAfterDispatch_returns409`
- `lowStockJob_levelBelowThreshold_createsOneOpenAlertNotTwo`
- `lowStockJob_levelRecovered_resolvesAlertAndInvalidatesCache`

**Frontend (Vitest + RTL):** `InventoryPage renders rows and calls API with sort param on header click`; `LoginPage shows ProblemDetail detail on 401`; `RequireRole hides manager nav for associate`; `PickScreen prevents quantity > requested`; `apiClient attaches bearer and retries once after refresh on 401`.

**Python (`tools/tests`):**
- `test_generator_produces_valid_skus` — codes unique, match `^[A-Z]{2,4}-[A-Z0-9]{2,4}-[A-Z0-9]{1,4}$`, each references an existing product, attributes JSON-serialisable, weight > 0
- `test_generator_is_deterministic_for_same_seed` — two runs → equal datasets; different seed → different
- `test_generator_order_mix_matches_configured_distributions` — with 5 000 orders, EXPRESS ratio within ±2 pp, single-line ratio within ±3 pp, every `ship_region` is a warehouse region
- `test_generator_inventory_respects_invariants` — `0 <= reserved <= on_hand`, low-stock ratio within tolerance
- `test_generator_injects_duplicate_orders_with_same_idempotency_key` — duplicate ratio honoured; duplicates share key **and** body
- `test_api_sink_sends_idempotency_key_and_retries_on_5xx` — `respx` mock: first 503 then 201; assert header present and one retry
- `test_sql_sink_copy_produces_expected_row_counts` (integration, marked `@pytest.mark.integration`)
- `test_cli_generate_writes_files_and_exit_code_zero`

**Manual:** `flowgrid-tools generate --seed 1 --out /tmp/a && flowgrid-tools generate --seed 1 --out /tmp/b && diff -r /tmp/a /tmp/b` → no output.

### 7. Debugging scenarios

| Symptom | Investigate | Likely cause |
|---|---|---|
| Browser: CORS error on login | Network tab: preflight `OPTIONS` status; server CORS config | Missing `CorsConfigurationSource` with the Vite origin (5173) or nginx proxy path mismatch |
| After refresh page, user logged out | Where is the token? | In memory by design → need refresh-token flow or accept re-login; document |
| Table sorts client-side only for the current page | Where is sort applied? | Sorting must be a server param; DataTable must call the API |
| Transfer receive creates a negative-looking "in transit" | Query for in-transit sums | Received quantity applied twice (receive not idempotent, or status not checked) |
| Deadlock between opposite transfers | Postgres log | Locks not ordered by `inventory_level.id` across *both* warehouses |
| Low-stock job creates duplicate alerts | Unique partial index present? Job overlapping (`fixedRate` vs `fixedDelay`)? | Missing partial unique index or overlapping runs; use `fixedDelay` and the index |
| `seed --api` gets 409 IN_PROGRESS on every order | Keys | Generator reuses a key across different orders (key not including order index) or the harness fires the same order concurrently |
| `mypy --strict` complains about `random.Random` typing / `dict[str, Any]` attributes | Type the config with `TypedDict`/dataclass | Untyped JSON blobs; model them explicitly |
| `pytest` hangs in `ApiSink` test | `respx` not mocking the route → real network | Route pattern mismatch; assert `respx` route `called` |

### 8. Interview questions (M4)

<details><summary>Why two phases for transfers?</summary>
Physical reality: goods leave one site before arriving at the other. A single-step transfer would show stock in the destination before it exists there (oversell) or in neither (undercount). `IN_TRANSIT` is a real state ops needs to see and reconcile.</details>

<details><summary>How does the client handle auth and errors?</summary>
Access token in memory (XSS posture), refresh on 401, ProblemDetail `code` mapped to messages, request id surfaced for support. Authorization is enforced server-side; the UI only hides.</details>

<details><summary>Why does a scheduled job need care when you scale to two instances?</summary>
Both run it → duplicate alerts / duplicate exports. Options: leader election (ShedLock table), DB advisory lock, or move jobs to one dedicated instance. Partial unique index already prevents duplicate *rows*.</details>

<details><summary>Why Python for the tooling instead of more Java?</summary>
It is the language ops/SRE/data people reach for; scripting, data generation and reporting are faster to write and read; it demonstrates polyglot competence honestly; it exercises the API purely as a client (no shared code with the backend, so it cannot accidentally "cheat" the invariants). The backend stays Java where type safety, concurrency control and the Spring ecosystem matter.</details>

<details><summary>How do you make a random generator testable?</summary>
Inject `random.Random(seed)`; pure functions; assert statistical properties with tolerances on large samples and exact properties (uniqueness, invariants) on every sample; snapshot one small dataset for a golden test.</details>

<details><summary>Where does the SQL bulk-seed bypass your API, and is that dangerous?</summary>
`COPY` writes rows directly; constraints stay on so invalid data fails loudly; it never touches `reserved`/reservation logic (orders seeded as `RECEIVED` or historical `SHIPPED` with consistent counters). It exists for index experiments, not for production.</details>

---

## M5 — Deploy, document, measure (Week 8)

**Goal:** FlowGrid is running on AWS, deployed by CI/CD, measured with k6 **and** the Python harness, documented, and failure-exercised. Tag **`v1.0` — Strong Résumé Version**.

### 1. What to know first (~6 h)

| Topic | File | Why now |
|---|---|---|
| RDS, S3, CloudWatch, security groups, deploy walkthrough, cost safety | [`../../12-aws/rds.md`](../../12-aws/rds.md), [`s3.md`](../../12-aws/s3.md), [`cloudwatch.md`](../../12-aws/cloudwatch.md), [`deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md), [`cost-safety.md`](../../12-aws/cost-safety.md) | The deployment |
| Full CI/CD pipeline examples | [`../../13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md) | `deploy.yml` |
| Structured logging + Actuator | [`../../05-spring-boot/06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md) | CloudWatch-ready logs |
| Linux ops basics for the EC2 box | [`../../10-linux/commands.md`](../../10-linux/commands.md), [`bash-scripting.md`](../../10-linux/bash-scripting.md) | `systemd`, `journalctl`, disk, `docker compose` on the host |
| Benchmark methodology | [`../templates/benchmark-report.md`](../templates/benchmark-report.md), [`docs-and-resume.md`](./docs-and-resume.md) | k6 + harness protocol |
| Python async HTTP for the harness | [`../../19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md) | `asyncio` + `httpx.AsyncClient`, `pytest-asyncio` |
| Project deep-dive method | [`../../16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md) | Week-8 rehearsal |

### 2. Requirements

1. **AWS:** IAM user (MFA) for you; IAM instance role for EC2 (S3 prefix + CloudWatch logs only); EC2 `t3.small` with Docker + Compose v2 via user-data; security groups (22 from your IP, 80/443 public, 5432 only from the EC2 SG); RDS Postgres 16 micro with backups; Redis as a container on the EC2 (ADR-006 documents the ElastiCache alternative); S3 private bucket with lifecycle; CloudWatch log group with 7-day retention + metric filter + alarm → SNS email; budget alarm.
2. **Prod Compose:** `api`, `web`, `redis`; env from `/opt/flowgrid/.env` (`chmod 600`); `awslogs` driver; restart policies; healthchecks.
3. **CI/CD:** `ci.yml` (Java + web + `tools/` pytest) on PRs; `deploy.yml` on tag → build images → push GHCR → SSH/SSM → `compose pull && up -d` → health wait → smoke login. Rollback = re-run with the previous tag.
4. **S3 export:** `POST /reports/inventory-export` → `202` + job id; job writes `inventory-<ts>.csv` to S3 with the instance role; `GET /reports/{jobId}` returns status + presigned URL (15 min).
5. **Structured logs** in CloudWatch with `requestId`; alarm fires on ERROR spike (test it by triggering a 500 deliberately — and record that you did).
6. **k6 baseline** (`load/`): scripts for order creation (10/20/50 VUs, 2 min each, warm-up 30 s), contention (50 VUs, one SKU, 100 units), catalog cold/warm, orders list p1/p500. Results + environment + methodology in `docs/PERFORMANCE.md` per the protocol.
7. **Python harness (FR-27):** `flowgrid-tools load --api URL --concurrency 20 --duration 60s --mix fixtures/mix.json` prints p50/p95/p99, throughput, status histogram, error codes, and writes `reports/load-<ts>.{md,json}`; `flowgrid-tools contention --sku TS-NAV-M --warehouse 1 --units 100 --competitors 150` sets stock via the API, fires 150 concurrent 1-unit orders, then reads inventory and asserts `successes == min(units, competitors)`, `reserved == successes`, `reserved <= on_hand` → prints `PASS`/`FAIL` with details, exit code 0/1. Reports include an environment block (host, API URL, git SHA from `/actuator/info`, dataset size, run time). Tested with pytest (mocked API); `mypy --strict`; README updated.
8. **Docs set** (meaningful content, per [`docs-and-resume.md`](./docs-and-resume.md)): README, ARCHITECTURE, API, DATABASE, TESTING, DEPLOYMENT, SECURITY, DESIGN_DECISIONS (ADRs 001–006), PERFORMANCE, plus `tools/README.md` and `DEMO.md`.
9. **Failure exercises:** at least scenarios 1–6 and 9 from [`failure-engineering.md`](./failure-engineering.md) executed, each with its regression test in the suite and a short write-up in `docs/FAILURE_EXERCISES.md`.
10. Tag `v1.0` with a GitHub Release (notes, screenshots, benchmark summary).

### 3. Architectural guidance

**Deployment topology:** one EC2 running `web` (nginx :80 → `/api` proxied to `api:8080`), `api`, `redis`; RDS in the same VPC reachable only from the EC2 SG; S3 via instance role (no keys on disk); logs shipped by Docker's `awslogs` driver (no agent to manage). Draw it in `DEPLOYMENT.md`.

**Configuration:** `application-prod.yml` reads everything from env (`SPRING_DATASOURCE_URL` with `sslmode=require`, `JWT_SECRET`, `SPRING_DATA_REDIS_HOST=redis`, `AWS_REGION`, `FLOWGRID_EXPORT_BUCKET`); `spring.flyway.enabled=true` runs migrations on startup (single instance → safe; document the multi-instance caveat).

**Deploy job design:** tag-triggered; images tagged with the git tag and `latest`; the EC2 pulls by tag; `compose up -d` replaces containers; health wait loop (`for i in $(seq 1 30); do curl -fs http://localhost/api/actuator/health && break; sleep 5; done`) fails the job if never healthy; migration failure (scenario 8) keeps the *old* container from being replaced only if you deploy `api` with a healthcheck + `--wait` — verify what actually happens and write it down.

**Harness design (Python):** `asyncio` with an `httpx.AsyncClient` (connection pool sized to concurrency), a semaphore for concurrency, a start barrier (`asyncio.Event`) so all competitors fire together in the contention scenario, per-request timing with `time.perf_counter()`, percentiles computed with `statistics.quantiles` (or a sorted list and nearest-rank — say which; nearest-rank matches k6 more closely), exponential backoff only for 429/503 and **never** for 409 (a 409 is a legitimate loser and must be counted). Separate the *measurement* from the *verification*: `harness.run(...) → RunResult`; `contention.verify(result, inventory_after) → Verdict`. Both pure enough to unit-test with fake data.

**Benchmark discipline:** same dataset (generated with a fixed seed), same warm-up, same duration, three runs, report the median run; record everything the protocol asks. Numbers from a laptop and from EC2→RDS are different experiments — label them.

### 4. Acceptance criteria

- [ ] Public URL serves the dashboard; `GET /api/actuator/health` is `UP` from the internet; login works with a seeded (non-default) password
- [ ] `git tag v1.0 && git push --tags` deploys without manual steps; the deploy job fails if health never comes up (tested by deploying a deliberately broken tag on a branch)
- [ ] CloudWatch shows JSON logs with `requestId`; the ERROR alarm fired once during your test and you have the email/screenshot
- [ ] Inventory export appears in S3; presigned URL downloads it; bucket is private
- [ ] `docs/PERFORMANCE.md` contains: environment block, dataset, k6 summaries for all four scenarios, harness reports (load + contention `PASS`), Redis hit ratio, `EXPLAIN` plans, and a short interpretation — **no target numbers copied as results**
- [ ] `flowgrid-tools contention … --units 100 --competitors 150` against the deployed API → `PASS` (successes=100)
- [ ] All nine docs + `tools/README.md` + `DEMO.md` contain real content (checklists in `docs-and-resume.md`)
- [ ] Failure exercises 1–6, 9 written up; regression tests in the suite
- [ ] IAM policy for the instance role is ≤ 2 statements scoped to the bucket prefix and log group; no `*`
- [ ] Teardown script tested (stop EC2, snapshot RDS) and cost dashboard checked
- [ ] Release `v1.0` published; README badges green; deep-dive rehearsal recorded (10–15 min, no notes)

### 5. Now implement it (~30 h)

| # | Task | Est. |
|---|---|---:|
| 1 | AWS account hygiene: IAM user + MFA, budget alarm, region choice; VPC/SG design | 1.5 |
| 2 | RDS instance, parameter group, SG; connect from laptop via SSH tunnel; run migrations by starting the API once locally against RDS (or from EC2) | 2 |
| 3 | EC2 + user-data (Docker, Compose, awslogs), Elastic IP, `/opt/flowgrid/.env`; instance role + policy | 2.5 |
| 4 | `docker-compose.prod.yml`, `application-prod.yml`, `logback-spring.xml` prod profile | 2 |
| 5 | S3 bucket + lifecycle; export job + endpoints with AWS SDK v2 (`S3Client`, presigner) + tests (LocalStack optional; else mock `S3Client`) | 3 |
| 6 | CloudWatch log group, metric filter, alarm, SNS; verify by triggering an ERROR | 1.5 |
| 7 | `deploy.yml` (build/push GHCR, SSH/SSM deploy, health wait, smoke); protect tags | 3 |
| 8 | k6 scripts + three runs each + Redis stats + `EXPLAIN` plans → `PERFORMANCE.md` | 3.5 |
| 9 | `tools/` harness: `harness.py` (async load, percentiles, histogram), `contention.py` (setup, barrier, verify), `report.py`, CLI; pytest with mocked API; mypy; README | 4.5 |
| 10 | Run harness against local and AWS; add to `PERFORMANCE.md`; failure exercises 1–6, 9 with write-ups | 3 |
| 11 | Docs set completion, ADR-006, DEMO.md, screenshots, release notes, tag `v1.0`, teardown script | 3.5 |

### 6. Verification

**Backend/infra checks:**
- `inventoryExportJob_writesCsvToS3AndMarksJobDone` (mock `S3Client`, assert key + content header row)
- `reportEndpoint_beforeJobFinishes_returnsInProgress`
- `openApi_docsListAllV1Paths` updated
- From a shell: `curl -fs https://<host>/api/actuator/health`; `aws s3 ls s3://<bucket>/exports/`; `aws logs tail /flowgrid/api --follow | grep requestId`; `aws cloudwatch describe-alarms --alarm-names flowgrid-api-errors`
- Deploy failure drill: push tag `v1.0-rc-broken` from a branch with a failing migration → job fails, previous version still serving (scenario 8)

**Python (`tools/tests`):**
- `test_harness_computes_percentiles_correctly` — feed `[10,20,…,1000]` ms → p50=500 (nearest-rank), p95=950, p99=990
- `test_harness_counts_status_codes_and_error_codes` — mocked responses 201/409/503 → histogram matches; 409 not retried; 503 retried once
- `test_harness_respects_concurrency_limit` — mock server records max in-flight ≤ configured
- `test_harness_reports_contention` — mock API returns 100×201 then 50×409 `INSUFFICIENT_STOCK`; mocked `GET /inventory` returns `reserved=100,on_hand=100` → verdict `PASS`, `successes == min(100,150)`
- `test_contention_flags_oversell` — mocked inventory `reserved=101` → `FAIL` with reason `oversell`
- `test_contention_flags_undersell` — 99 successes with stock left → `FAIL` with reason `undersell` (a lock too coarse or a bug returning 409 wrongly)
- `test_report_contains_environment_and_methodology_blocks` — Markdown has `## Environment`, `## Methodology`, `## Results`, git SHA, dataset seed
- `test_cli_contention_exit_code_1_on_fail`

**Manual protocol run:** documented step list in `docs-and-resume.md` executed three times; median recorded.

### 7. Debugging scenarios

| Symptom | Investigate | Likely cause |
|---|---|---|
| API container restarts in a loop on EC2 | `docker logs flowgrid-api`; `docker inspect` exit code | Missing env var (`JWT_SECRET`), RDS SG blocks 5432, or `sslmode` mismatch |
| `PSQLException: FATAL: no pg_hba.conf entry … no encryption` | JDBC URL | Add `?sslmode=require` |
| `S3Exception 403` from the export job | `aws sts get-caller-identity` from inside the container (needs SDK to pick up instance metadata; IMDSv2 hop limit for containers) | Instance role missing/misscoped, or IMDSv2 hop limit = 1 (set to 2 for containers) |
| CloudWatch shows nothing | `docker info | grep -i log`; `journalctl -u docker` | awslogs driver lacks permissions or region; instance role needs `logs:CreateLogStream/PutLogEvents` |
| Deploy job "succeeds" but old version still running | Image tag pulled | `latest` cached; deploy by explicit tag and `compose pull` before `up` |
| k6 p95 wildly different between runs | JIT warm-up, DB cold cache, laptop thermal | Warm-up phase, three runs, report median, record environment |
| Harness shows many 503s at 50 concurrency | Hikari pool (10) + lock waits → `DEPENDENCY_UNAVAILABLE` | Expected at some point: record where it breaks, it is a *finding*, not a failure; consider pool size vs RDS limits |
| Harness contention says `undersell` | Inspect 409 bodies | API returned `INSUFFICIENT_STOCK` while stock existed → lock timeout (`jakarta.persistence.lock.timeout`) too short → map to retry or raise |
| `asyncio` harness slower than k6 at the same concurrency | Client CPU-bound (JSON parsing), single event loop | Fine — the harness is for realism + verification; k6 is for raw numbers; note it |
| Cost jumps | Billing console → RDS storage/IOPS or forgotten NAT | Stop instances; teardown script |

### 8. Interview questions (M5)

<details><summary>Walk me through your deployment.</summary>
Tag → Actions builds/tests → images to GHCR → SSH/SSM to EC2 → compose pull/up → health wait → smoke. EC2 hosts api/web/redis; RDS Postgres; S3 exports via instance role; CloudWatch via awslogs; rollback = previous tag.</details>

<details><summary>Why Redis on EC2 rather than ElastiCache?</summary>
ADR-006: cost (~$0 vs ~$12+/month), and correctness never depends on Redis — it is a cache with degradation; ElastiCache buys durability/replication we don't need at this scale. Would switch when running multiple API instances that must share a cache.</details>

<details><summary>What did you measure and what did you learn?</summary>
Your real numbers from PERFORMANCE.md: order-creation p50/p95/p99 at 10/20/50 VUs, throughput, contention success ratio, cache hit ratio, where it saturated (pool/locks) and what you changed. Say the environment in the same sentence as the number.</details>

<details><summary>Why both k6 and a Python harness?</summary>
k6 gives stable, comparable load numbers; the harness drives a realistic order mix from the generator and *verifies invariants afterwards* (`successes == min(units, competitors)`), which k6 doesn't do. Together: numbers + proof.</details>

<details><summary>What is least privilege in your setup?</summary>
Instance role: `s3:PutObject/GetObject` on one prefix, `logs:*` on one group; no access keys on the box; SG 5432 only from the EC2 SG; IAM user with MFA for the console; GitHub secrets scoped to deploy.</details>

<details><summary>What would break first at 100× traffic?</summary>
Hot inventory rows (lock queueing) and the single EC2; then offset pagination and audit growth. Mitigations in README §22 — say them in priority order with the trade-off of each.</details>

<details><summary>How do you know your alarm works?</summary>
Triggered an ERROR deliberately, watched the metric filter count it and the alarm transition to ALARM, received the SNS email; screenshot in DEPLOYMENT.md.</details>
