# Week 4 — FlowGrid M1: domain, persistence, auth

[← Week 3](../week-03/) · [Roadmap](../../ROADMAP.md) · [Week 5 →](../week-05/)

**Phase 1 · FlowGrid** · Milestone **M1 — Domain, persistence, auth** (= MVP core) · ends with **[Checkpoint CP-4](../../checkpoints/checkpoint-04.md)** on Sunday

| Block | Hours | What it means this week |
|---|---:|---|
| Project | 26 | FlowGrid repo: Spring Boot + Flyway + Postgres (Compose), entities, CRUD + validation + ProblemDetail, pagination/sort/filter, JWT + roles, OpenAPI, CI |
| Learning | 8 | Spring Data JPA, Flyway, DTOs, Bean Validation, `@ControllerAdvice`/ProblemDetail, pagination, OpenAPI; SQL joins & aggregation; Docker Compose; GitHub Actions; Spring Security + JWT |
| DSA | 7 | Sliding Window — **8 new problems in Python** + Day-3/7/14 reviews + 1 Java rep |
| Interview / review | 4 | Explain out loud; **CP-4** on Sunday |

---

## 1. Main objective

Ship FlowGrid's **MVP core**: a real, secured, documented CRUD API over the inventory domain,
backed by PostgreSQL with versioned migrations, tested, and built by CI on every push. Not a
tutorial CRUD: warehouses, products, SKUs, per-warehouse inventory levels with quantity *by state*,
stock adjustments that write audit events, users with four roles, JWT auth, pagination/sort/filter
on every list, RFC 9457 `ProblemDetail` on every error.

Everything later stands on this: M2's reservations lock the `inventory_level` rows you design
this week; M3's allocator reads the warehouse/zone model; M4's dashboard consumes these
endpoints; M5 deploys this Compose stack. Get the schema and error contract right now.

Spec: [`18-projects/flowgrid/README.md`](../../18-projects/flowgrid/README.md) ·
Milestone detail: [`18-projects/flowgrid/milestones.md`](../../18-projects/flowgrid/milestones.md) (M1) ·
Failure exercises: [`18-projects/flowgrid/failure-engineering.md`](../../18-projects/flowgrid/failure-engineering.md).

## 2. Prerequisites

- Week 3's `items-api` works and you can explain every Spring piece a request passes through.
- Docker running; `docker run postgres:16` done in Week 3.
- FlowGrid GitHub repo created (Sunday of Week 3) with milestone **M1** and its issues (see §8).
- Read M1 in `milestones.md` in full before Monday's learning block.

## 3. Learning topics

| Topic | Subtopics | Folder file |
|---|---|---|
| Spring Data JPA | entities, `@Id`/generation, `@ManyToOne`/`@OneToMany` (lazy by default), `equals`/`hashCode` policy for entities, repositories, derived queries, `@Query`, `Specification` for filters, N+1 and `JOIN FETCH`, `open-in-view=false` | [`05-spring-boot/03-data-jpa.md`](../../05-spring-boot/03-data-jpa.md), [`04-sql-databases/08-jdbc-orm.md`](../../04-sql-databases/08-jdbc-orm.md) |
| Flyway | `V1__init.sql` naming, migration order, never edit an applied migration, `flyway.clean` disabled in prod | [`05-spring-boot/03-data-jpa.md`](../../05-spring-boot/03-data-jpa.md) |
| SQL joins & aggregation | `INNER`/`LEFT` joins, `GROUP BY`/`HAVING`, `COUNT`/`SUM`, `COALESCE`, aggregation over states | [`04-sql-databases/02-joins-aggregation.md`](../../04-sql-databases/02-joins-aggregation.md) |
| DTOs, validation, errors | request/response records, `@Valid`, constraint annotations, `@ControllerAdvice`, `ProblemDetail` (RFC 9457), mapping domain exceptions → status | [`05-spring-boot/04-validation-errors.md`](../../05-spring-boot/04-validation-errors.md), [`05-spring-boot/02-web-layer.md`](../../05-spring-boot/02-web-layer.md) |
| Pagination, sort, filter | `Pageable`, `Page<T>` → response DTO, whitelisting sort fields, filter params → `Specification` | [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md) |
| OpenAPI | springdoc-openapi, `@Operation`, `@Schema`, security scheme, Swagger UI | [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md) |
| Spring Security + JWT | filter chain, stateless sessions, `BCryptPasswordEncoder`, issuing/validating HS256 JWTs (`jjwt` or Nimbus), `@PreAuthorize`, method security, roles vs authorities | [`05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md) |
| Docker Compose | `compose.yaml` with `postgres:16`, volumes, healthcheck, env, `docker compose up -d`, `logs`, `exec psql` | [`11-docker/compose.md`](../../11-docker/compose.md), [`11-docker/README.md`](../../11-docker/README.md) |
| GitHub Actions (basic) | workflow on push/PR, `actions/setup-java` with cache, `mvn -B verify`, service container for Postgres (or H2-free: Testcontainers from Week 5) | [`13-cicd/github-actions.md`](../../13-cicd/github-actions.md), [`13-cicd/README.md`](../../13-cicd/README.md) |

## 4. Concepts to learn

### 4.1 Migrations own the schema; JPA maps to it

```sql
-- src/main/resources/db/migration/V1__init.sql
CREATE TABLE warehouse (
  id         bigserial PRIMARY KEY,
  code       text NOT NULL UNIQUE,
  name       text NOT NULL,
  region     text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE inventory_level (
  id           bigserial PRIMARY KEY,
  warehouse_id bigint NOT NULL REFERENCES warehouse(id),
  sku_id       bigint NOT NULL REFERENCES sku(id),
  on_hand      integer NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
  reserved     integer NOT NULL DEFAULT 0 CHECK (reserved >= 0),
  allocated    integer NOT NULL DEFAULT 0 CHECK (allocated >= 0),
  picked       integer NOT NULL DEFAULT 0 CHECK (picked >= 0),
  version      bigint NOT NULL DEFAULT 0,
  UNIQUE (warehouse_id, sku_id),
  CHECK (reserved + allocated + picked <= on_hand)
);
```

`spring.jpa.hibernate.ddl-auto=validate`: Hibernate checks the mapping against the migrated schema
and refuses to start on drift. `available` is **derived** (`on_hand − reserved − allocated − picked`),
never stored — the same rule as Week 2's kata.

- **Interview angle:** "Why Flyway instead of `ddl-auto=update`?" (reviewable, ordered, reproducible; `update` never drops or renames safely). "Why is `available` not a column?" (one source of truth; no drift).
- **FlowGrid uses this:** every milestone adds a `V<n>__…sql`; the `CHECK` constraints are the last line of defence in M2's concurrency test.

### 4.2 Entity mapping, lazy relations, and `equals`

```java
@Entity @Table(name = "inventory_level")
public class InventoryLevel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "warehouse_id") private Warehouse warehouse;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sku_id") private Sku sku;
    private int onHand; private int reserved; private int allocated; private int picked;
    @Version private long version;
    protected InventoryLevel() {}                       // JPA needs it
    public int available() { return onHand - reserved - allocated - picked; }
    @Override public boolean equals(Object o) { return o instanceof InventoryLevel other && id != null && id.equals(other.id); }
    @Override public int hashCode() { return getClass().hashCode(); }   // stable before/after persist
}
```

- **Interview angle:** "Why is `@ManyToOne` lazy by default a good idea, and what is `LazyInitializationException`?" "What is the N+1 problem and how do you fix it?" (`JOIN FETCH`/`@EntityGraph`). "Why not use Lombok `@Data` on entities?" (`equals`/`hashCode` over all fields breaks with lazy proxies and mutable state).
- **FlowGrid uses this:** `InventoryLevel` is *the* contended row of the whole project; `@Version` is already here so Week 5 can compare optimistic vs pessimistic locking on the same entity.

### 4.3 Joins and aggregation — the SQL behind "inventory by warehouse"

```sql
SELECT w.code AS warehouse, s.code AS sku,
       il.on_hand, il.reserved, il.on_hand - il.reserved - il.allocated - il.picked AS available
FROM inventory_level il
JOIN warehouse w ON w.id = il.warehouse_id
JOIN sku s       ON s.id = il.sku_id
WHERE w.region = 'EU'
ORDER BY w.code, s.code
LIMIT 20 OFFSET 40;

SELECT s.code, SUM(il.on_hand) AS total_on_hand, COUNT(DISTINCT il.warehouse_id) AS warehouses
FROM sku s LEFT JOIN inventory_level il ON il.sku_id = s.id
GROUP BY s.code
HAVING COALESCE(SUM(il.on_hand), 0) < 10;
```

Write the SQL first, then the JPA/`@Query` that produces it, then compare with `spring.jpa.show-sql`.

- **Interview angle:** "`INNER` vs `LEFT JOIN` — when does the result differ?" "`WHERE` vs `HAVING`?" "Why does `OFFSET` get slow?" (scans and discards; keyset in Week 11).
- **FlowGrid uses this:** the low-stock endpoint and the M4 dashboard's inventory-by-warehouse view are exactly these queries.

### 4.4 Errors as `ProblemDetail`

```java
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://flowgrid.example/problems/not-found"));
        return pd;
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException ex) {
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "validation failed");
        pd.setProperty("errors", ex.getFieldErrors().stream()
            .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage()))).toList());
        return pd;
    }
}
```

One handler per domain exception family: `404` not found, `409` duplicate/conflict, `400` validation,
`403` forbidden, `422` business rule (e.g. negative adjustment beyond on-hand).

- **Interview angle:** "How do you design error responses for an API?" (consistent shape, machine-readable `type`, field-level errors, no stack traces).
- **FlowGrid uses this:** the React client (M4) parses `errors[]` into form messages; k6 (M5) asserts on `status`.

### 4.5 Pagination, sorting, filtering without exposing internals

```java
@GetMapping
PageResponse<InventoryLevelResponse> list(
        @RequestParam Optional<Long> warehouseId, @RequestParam Optional<String> skuCode,
        @RequestParam(defaultValue = "false") boolean lowStockOnly,
        @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    var safe = SortWhitelist.apply(pageable, Set.of("id", "onHand", "sku.code"));   // reject unknown sort fields → 400
    return PageResponse.from(service.search(new InventoryFilter(warehouseId, skuCode, lowStockOnly), safe));
}
```

Return your own `PageResponse` (`items`, `page`, `size`, `totalElements`, `totalPages`) — never
`Page<Entity>` (unstable JSON, leaks entities).

- **Interview angle:** "How do you paginate an API? What breaks with large offsets?" "Why not return entities directly?"
- **FlowGrid uses this:** every list endpoint; the dashboard's table controls (M4) map 1:1 to these params.

### 4.6 JWT auth: stateless, role-checked

```java
@Bean
SecurityFilterChain api(HttpSecurity http, JwtAuthFilter jwt) throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/actuator/health").permitAll()
            .anyRequest().authenticated())
        .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
        .build();
}
// method security:
@PreAuthorize("hasAnyRole('ADMIN','OPS_MANAGER')")
public AdjustmentResponse adjust(AdjustStockRequest req) { ... }
```

Login: verify BCrypt hash → issue HS256 JWT (`sub`, `roles`, `exp` ≤ 1 h) signed with a secret from
env, never from the repo. Filter: parse header, validate signature + expiry, set
`SecurityContextHolder`. Roles: `ADMIN`, `OPS_MANAGER`, `WAREHOUSE_ASSOCIATE`, `VIEWER`.

- **Interview angle:** "How does JWT auth work end to end? Where is the state?" "Why disable CSRF for a stateless bearer-token API?" "What is the risk of long-lived JWTs and how do you mitigate it?" (short expiry; refresh tokens — out of scope, say so).
- **FlowGrid uses this:** every endpoint; role matrix in the spec decides who can adjust stock, create orders, pick/pack.

### 4.7 Compose for the database; CI for every push

```yaml
# compose.yaml
services:
  postgres:
    image: postgres:16
    environment: { POSTGRES_DB: flowgrid, POSTGRES_USER: flowgrid, POSTGRES_PASSWORD: flowgrid }
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U flowgrid -d flowgrid"], interval: 5s, timeout: 3s, retries: 10 }
volumes: { pgdata: {} }
```

```yaml
# .github/workflows/ci.yml
name: ci
on: { push: { branches: [main] }, pull_request: {} }
jobs:
  verify:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env: { POSTGRES_DB: flowgrid, POSTGRES_USER: flowgrid, POSTGRES_PASSWORD: flowgrid }
        ports: ["5432:5432"]
        options: --health-cmd "pg_isready -U flowgrid" --health-interval 5s --health-timeout 3s --health-retries 10
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21", cache: maven }
      - run: mvn -B verify
```

- **Interview angle:** "What does your CI do on a PR?" (build, unit + integration tests against a real Postgres, fail the merge). "Why a health check?"
- **FlowGrid uses this:** the service container is replaced by Testcontainers in Week 5; the same workflow grows into build → image → GHCR → deploy in Week 8.

## 5. Resources

- [Spring Data JPA reference](https://docs.spring.io/spring-data/jpa/reference/); [Hibernate ORM 6 user guide](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html) (associations, fetching); [Flyway docs — migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations).
- [Spring Framework — `ProblemDetail`/RFC 9457 support](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html); [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457).
- [Spring Security reference — Servlet](https://docs.spring.io/spring-security/reference/servlet/index.html) (architecture, authorization, method security); [jjwt](https://github.com/jwtk/jjwt).
- [springdoc-openapi](https://springdoc.org/); [Jakarta Bean Validation](https://beanvalidation.org/).
- [PostgreSQL 16 — joins](https://www.postgresql.org/docs/16/tutorial-join.html), [aggregates](https://www.postgresql.org/docs/16/tutorial-agg.html).
- [Docker Compose reference](https://docs.docker.com/compose/); [GitHub Actions — building and testing Java with Maven](https://docs.github.com/en/actions/use-cases-and-examples/building-and-testing/building-and-testing-java-with-maven).
- *Effective Java* Item 10–11 (revisit for entities); Vlad Mihalcea's Hibernate articles on `equals`/`hashCode` and N+1 are the standard references.
- DSA: [`03-dsa/05-sliding-window.md`](../../03-dsa/05-sliding-window.md), NeetCode "Sliding Window".

## 6. Exercises + coding assignments (inside FlowGrid)

Beyond the milestone itself (§8), these targeted exercises are part of the week:

| # | Exercise | Acceptance |
|---|---|---|
| 1 | Write the SQL for "inventory by warehouse", "low stock (available < threshold)", "total on-hand per SKU" in `psql` first; then implement each as a repository query and diff `show-sql` output against your SQL | Three `sql/*.sql` files + matching repository methods with tests |
| 2 | Build the sort-field whitelist so `?sort=password,desc` is a `400`, not a 500 | Test proves it |
| 3 | Role matrix test: for `POST /api/adjustments`, each of the four roles → expected status (`201`/`403`) | Parameterized `@WebMvcTest` or `@SpringBootTest` |
| 4 | Audit event on every adjustment (`who`, `what`, `before`, `after`, `reason`, `at`) written in the same transaction | Test: adjustment failing validation writes no audit row |

### Break it (predict first, then run)

- Set `spring.jpa.open-in-view=false` (it should already be) and serialise an entity with a lazy `warehouse` from a controller. `LazyInitializationException`. Fix by mapping to a DTO in the service inside the transaction.
- Log SQL, list 20 inventory levels with their warehouse names via naive mapping. Count the queries (21). Fix with `JOIN FETCH`/`@EntityGraph`; count again (1).
- Compare two `Long` entity IDs with `==` for values > 127 in a filter. Watch the silent wrong result. Fix with `equals`/`Objects.equals`.
- Edit an already-applied migration (`V1__init.sql`) and restart. Read Flyway's checksum-mismatch error. Revert; add `V2__…`.
- Change the JWT secret in env and call an endpoint with a token issued before. `401`. Explain why nothing on the server "remembers" the token.
- `docker compose down -v` then `up`. Data gone — because you deleted the volume. Explain volumes vs containers.

### Debug it

- Run a `@SpringBootTest` against Compose Postgres with the wrong password. Read the *full* startup stack trace bottom-up to the `PSQLException`; find the cause in under 2 minutes.
- A `POST /api/skus` returns 500 instead of 409 on duplicate code. Set a breakpoint in the advice, find the actual exception (`DataIntegrityViolationException`), map it properly (or check uniqueness in the service first and explain the race — Week 5 will make it precise).
- CI fails but local passes: the workflow has no `DB_URL` env. Read the Actions log, fix via `env:` on the step. Document "CI parity" in `TESTING.md`.

## 7. DSA — Sliding Window (8 new, in Python)

Guide: [`03-dsa/05-sliding-window.md`](../../03-dsa/05-sliding-window.md); templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md).
Limits: Easy 20 min, Medium 35 min, Hard 45 min (Hard: reading the solution after 45 min is expected).

| Day | Problem | Difficulty | Limit | Focus |
|---|---|---|---|---|
| Mon | [643. Maximum Average Subarray I](https://leetcode.com/problems/maximum-average-subarray-i/) | Easy | 15 min | fixed window: add right, drop left |
| Mon | [3. Longest Substring Without Repeating Characters](https://leetcode.com/problems/longest-substring-without-repeating-characters/) | Medium | 30 min | variable window + last-seen `dict` |
| Tue | [209. Minimum Size Subarray Sum](https://leetcode.com/problems/minimum-size-subarray-sum/) | Medium | 30 min | shrink while condition holds |
| Wed | [1004. Max Consecutive Ones III](https://leetcode.com/problems/max-consecutive-ones-iii/) | Medium | 30 min | budget of flips as the window invariant |
| Wed | [904. Fruit Into Baskets](https://leetcode.com/problems/fruit-into-baskets/) | Medium | 30 min | "at most 2 distinct" with `Counter` |
| Thu | [424. Longest Repeating Character Replacement](https://leetcode.com/problems/longest-repeating-character-replacement/) | Medium | 35 min | `window − maxFreq ≤ k`; why maxFreq needn't decrease |
| Thu | [567. Permutation in String](https://leetcode.com/problems/permutation-in-string/) | Medium | 35 min | fixed window with two count arrays |
| Sat | [76. Minimum Window Substring](https://leetcode.com/problems/minimum-window-substring/) | Hard | 45 min | `need`/`have` counters; expand then contract |

**Java rep (Sat, 30 min):** re-do **3. Longest Substring Without Repeating Characters** in Java with `HashMap<Character,Integer>` (or `int[128]`).

**Spaced reviews due:** Day-14 of Week 2 — Contains Duplicate, Valid Anagram (Mon), Two Sum, Group
Anagrams (Tue), Top K Frequent Elements (Wed), Product of Array Except Self, Valid Sudoku (Thu),
Longest Consecutive Sequence (Sat). Day-7 of Week 3 — Valid Palindrome, Two Sum II (Mon), 3Sum (Tue),
Container With Most Water, Running Sum (Wed), Find Pivot Index, Range Sum Query (Thu), Subarray Sum
Equals K (Sat). Day-3 of this week's — Maximum Average Subarray, Longest Substring Without Repeating
(Thu), Minimum Size Subarray Sum (Fri), Max Consecutive Ones III, Fruit Into Baskets (Sat), Longest
Repeating Character Replacement, Permutation in String (Sun).

## 8. Project work — FlowGrid M1

Read [`18-projects/flowgrid/milestones.md`](../../18-projects/flowgrid/milestones.md) → M1 for
requirements, architecture guidance, acceptance criteria, verification tests, debugging scenarios
and interview questions. This section is the week's *plan* for it — not the implementation.

### GitHub setup (Mon, 45 min)

- Repo `flowgrid`; `README.md` with one-paragraph pitch + "status: M1 in progress"; MIT or similar licence.
- Milestone **M1 — Domain, persistence, auth**; issues (labels `m1`, `backend`, `db`, `security`, `ci`), roughly one per row below.
- Branch protection on `main`: require the `ci / verify` check; PRs only.

### Task checklist

- [ ] Mon: Initializr (Web, Validation, Data JPA, PostgreSQL, Flyway, Security, Actuator, springdoc); `compose.yaml`; `V1__init.sql` for warehouse/product/sku/inventory_level/stock_adjustment/audit_event/app_user/user_role; app boots with `ddl-auto=validate`; CI workflow green on an empty test.
- [ ] Tue: entities + repositories; Warehouse + Product + SKU CRUD (DTOs, validation, `ProblemDetail`, `409` on duplicate codes); `PageResponse` + sort whitelist; tests per endpoint.
- [ ] Wed: InventoryLevel list/search (filters: warehouse, sku, lowStockOnly), "inventory by warehouse" summary via aggregation query; audit event model.
- [ ] Thu: Stock adjustments (`POST /api/adjustments`: warehouse, sku, delta, reason) → updates `on_hand` transactionally, writes audit event; validation (`422` if result < 0); OpenAPI annotations + Swagger UI.
- [ ] Fri: Users/roles + BCrypt + login endpoint + JWT filter + `@PreAuthorize` matrix; seed migration `V2__seed_admin.sql` with a placeholder hash replaced from env at startup (or an `ADMIN` bootstrap on first run — document the choice).
- [ ] Sat: role-matrix tests, Break-it/Debug-it scenarios, `docs/API.md` (endpoint table) + `docs/DATABASE.md` (ERD as a Mermaid diagram + constraints), PR review, merge, tag `m1`.
- [ ] Sun: CP-4.

### Acceptance criteria (summary — full list in `milestones.md`)

- Compose `up` → app boots, Flyway applies `V1`/`V2`, `/actuator/health` is `UP`.
- CRUD for warehouse/product/SKU; inventory levels listable with pagination/sort/filter; adjustments update on-hand and write audit events atomically.
- Every error is a `ProblemDetail`; every list is a `PageResponse`; unknown sort fields → `400`.
- JWT login; 4 roles enforced on adjustments and admin endpoints; unauthenticated → `401`, wrong role → `403`.
- OpenAPI at `/v3/api-docs`, Swagger UI usable with the bearer token.
- `mvn -B verify` green locally and in GitHub Actions on the PR.

### Verification tests to write (describe → then write)

- Web: per endpoint happy path, validation `400` with `errors[]`, `404`, `409` duplicate; sort whitelist `400`.
- Repository/`@DataJpaTest` (against Postgres — see Week 5 for Testcontainers; this week Compose is acceptable): filters combine correctly; low-stock query returns only `available < threshold`.
- Service: adjustment below zero → `422`, no audit row; successful adjustment → exactly one audit row with before/after.
- Security: parameterized role matrix; expired token → `401`; tampered signature → `401`.
- Migration: app fails to start when an entity column is renamed without a migration (`ddl-auto=validate`) — a manual check you record in `TESTING.md`.

### Failure-engineering scenarios this week

From [`failure-engineering.md`](../../18-projects/flowgrid/failure-engineering.md) run the M1 set:
**database unavailable at startup** (stop Postgres, boot, observe; add sane connection-timeout
config), **migration checksum mismatch**, **lazy-loading outside a transaction**, **N+1 on a list
endpoint** (measure query count before/after). Record each as reproduce → observe → logs →
diagnose → fix → regression test in `docs/FAILURE_LOG.md`.

### PR expectations

One PR per issue group (≈ 4–6 PRs), each: description (what/why/how tested), CI green, self-review
comments on at least two lines you are unsure about, squash-merge. Final PR closes milestone M1.

## 9. Git activity

```bash
git switch -c feat/m1-schema-and-compose
git commit -m "feat(db): V1 init schema with inventory_level state columns and CHECKs"
git push -u origin feat/m1-schema-and-compose     # open PR, wait for ci/verify
# after merge:
git switch main && git pull --ff-only
git tag -a m1 -m "FlowGrid M1: domain, persistence, auth" && git push origin m1
```

- Conventional Commits with scopes `db`, `api`, `security`, `ci`, `docs`.
- Branch per issue; PR per group; **CI required before merge** from this week on.
- `.github/PULL_REQUEST_TEMPLATE.md` with sections *What / Why / How tested / Risks*.

## 10. Interview preparation

Weeks 1–4 ramp continues (explain each Python solution out loud). This week also start answering
the M1 questions in [`18-projects/flowgrid/interview-questions.md`](../../18-projects/flowgrid/interview-questions.md) out loud, one per day, from *your* code.

Questions to answer out loud:

Track A (Python): 1. How do you decide whether a window is fixed or variable? 2. In 424, why can the window never shrink below the best answer so far? 3. Explain the `need`/`have` technique for 76.
Track B (Java/Spring/SQL): 4. Walk through what happens when a JWT-authenticated `POST /api/adjustments` hits the app (filter → security context → controller → service → transaction → repository → SQL → audit → response). 5. Why Flyway + `ddl-auto=validate`? 6. What is N+1 and how did you find and fix it? 7. `INNER` vs `LEFT JOIN` in your low-stock query — which and why? 8. How does `ProblemDetail` improve your API for clients?

## 11. Revision work

- Sunday is **CP-4**; the revision is inside it. Before it, re-read Week 2's `HashMap` internals and Week 3's HTTP notes (they appear in CP-4's knowledge questions).
- Redo the Week 3 `MockMvc` test from memory against a FlowGrid endpoint.

## 12. Daily plan

| Day | Blocks |
|---|---|
| **Mon** (8 h) | Learning 2: JPA + Flyway; Compose · Project 4.5: repo + milestone + issues; Initializr; `compose.yaml`; `V1__init.sql`; boots with `validate`; CI workflow · DSA 1.5: 643, 3 + Day-14 (Contains Duplicate, Valid Anagram) + Day-7 (Valid Palindrome, Two Sum II) |
| **Tue** (8 h) | Project 5: entities, repositories, warehouse/product/SKU CRUD, DTOs, `ProblemDetail`, `PageResponse`, tests · DSA 2: 209 + Day-14 (Two Sum, Group Anagrams) + Day-7 (3Sum) · Interview 1: explain the request path out loud; M1 question #1 |
| **Wed** (8 h) | Learning 2: joins & aggregation; pagination/filters · Project 4.5: inventory-level search + summary; audit model; SQL-first exercise · DSA 1.5: 1004, 904 + Day-14 (Top K) + Day-7 (Container, Running Sum) |
| **Thu** (8 h) | Project 5: adjustments + audit in one transaction; OpenAPI; Swagger UI · DSA 2: 424, 567 + Day-14 (Product Except Self, Valid Sudoku) + Day-7 (Pivot Index, Range Sum) + Day-3 (643, 3) · Docs 1: `docs/API.md` skeleton |
| **Fri** (5 h) | Learning 2: Spring Security + JWT · Project 2: users/roles, login, JWT filter, `@PreAuthorize` · DSA 1: Day-3 (209) reviews · Retro (moved to Sun) |
| **Sat** (6 h) | Project 4: role-matrix tests; Break-it/Debug-it; failure scenarios; `DATABASE.md` ERD; final PR; tag `m1` · DSA 1.5: 76 + Day-14 (Longest Consecutive) + Day-7 (Subarray Sum K) + Day-3 (1004, 904) + **Java rep** (3) · Learning 0.5: GitHub Actions log reading |
| **Sun** (3 h) | **[Checkpoint CP-4](../../checkpoints/checkpoint-04.md)** (timed) · Day-3 (424, 567) · trackers · plan Week 5 · rest |

## 13. End-of-week test → Checkpoint CP-4

This week's test *is* [`checkpoints/checkpoint-04.md`](../../checkpoints/checkpoint-04.md)
(timed Python coding, knowledge questions, a practical Spring/SQL task, explain-out-loud prompts, and
an M1 project review with scoring thresholds). Warm-up questions to self-check on Saturday night:

<details>
<summary>1. Your list endpoint returns 20 inventory levels and the log shows 21 SQL statements. What is it and what are two fixes?</summary>

N+1: one query for the page, one per row for the lazy `warehouse`/`sku`. Fix with `JOIN FETCH` in a
`@Query`, `@EntityGraph`, or a DTO projection query that selects exactly the needed columns.
</details>

<details>
<summary>2. Why is <code>available</code> computed rather than stored, and what would you need if you stored it?</summary>

A stored column can drift from `on_hand − reserved − …` under concurrent updates or a forgotten
code path. If stored, you would need a trigger or a `CHECK (available = on_hand - reserved - allocated - picked)` and to update it in every write.
</details>

<details>
<summary>3. A JWT's payload is readable by anyone. Why is that acceptable and what must never be in it?</summary>

JWTs are signed, not encrypted: integrity and authenticity, not confidentiality. Never put
passwords, secrets or sensitive PII in claims; keep `exp` short.
</details>

<details>
<summary>4. <code>SELECT s.code, SUM(il.on_hand) FROM sku s LEFT JOIN inventory_level il ON il.sku_id = s.id GROUP BY s.code HAVING SUM(il.on_hand) < 10</code> — which SKUs with zero levels appear?</summary>

None: for SKUs with no levels, `SUM` is `NULL`, and `NULL < 10` is not true. Use `COALESCE(SUM(il.on_hand), 0) < 10`.
</details>

<details>
<summary>5. Sliding window: when is the window <em>invalid</em> in 1004 and what do you do?</summary>

When the count of zeros in the window exceeds k; advance `left` until it is ≤ k again. The answer
is the max `right − left + 1` seen while valid.
</details>

## 14. Mastery checklist

- [ ] I can design a normalised schema with constraints, write it as a Flyway migration and map it with JPA without `ddl-auto=update`.
- [ ] I can find and fix N+1 and lazy-loading errors, and explain the entity `equals`/`hashCode` policy I chose.
- [ ] I write SQL joins/aggregations by hand and know the JPA that produces them.
- [ ] Every FlowGrid error is a `ProblemDetail`; every list is paged, sortable (whitelisted) and filterable.
- [ ] I can explain the JWT flow end to end and the role matrix is tested.
- [ ] Compose runs Postgres; CI runs `mvn verify` on every PR; `main` is protected.
- [ ] Sliding-window problems: 8 re-solvable in Python within limits; Java rep done.

## 15. Expected deliverables

- `flowgrid` repo: milestone M1 closed, ≥ 4 merged PRs with green CI, tag `m1`, `docs/API.md`, `docs/DATABASE.md`, `docs/FAILURE_LOG.md` (4 scenarios), `TESTING.md` started.
- CP-4 completed and scored in [`checkpoints/checkpoint-04.md`](../../checkpoints/checkpoint-04.md) / [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md).
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 30 problems total, 4 Java reps, reviews logged.
- [`trackers/project-tracker.md`](../../trackers/project-tracker.md): FlowGrid M1 done, hours logged.
- [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): Spring Boot, JPA, SQL, Spring Security, Docker Compose, GitHub Actions updated.

## 16. If you're behind / stretch

**Behind:** the order to preserve is schema + migrations + inventory levels + adjustments with
audit + JWT with roles + CI. Cut: OpenAPI annotations (keep the default doc), filters beyond
`warehouseId`, the ERD diagram (a table in `DATABASE.md` is enough). Drop 76 and 567 from DSA;
keep all reviews. Take CP-4 anyway — it measures, it does not block.

**Stretch:** `Specification`-based filtering with a small DSL; `@EntityGraph` on the summary
endpoint; refresh tokens design note (not implementation) in `docs/DESIGN_DECISIONS.md`;
[239. Sliding Window Maximum](https://leetcode.com/problems/sliding-window-maximum/) in Python with `deque` (previews Week 5).
