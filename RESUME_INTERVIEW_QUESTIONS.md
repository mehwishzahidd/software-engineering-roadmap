# 🎯 Résumé Interview Questions — the probing bank

> Every question here is one an interviewer can ask *because of a line on your résumé*. The answer
> outlines are grounded in the four projects — [**FlowGrid**](./18-projects/flowgrid/README.md)
> (inventory & fulfillment, W4–8), [**LedgerX**](./18-projects/ledgerx/README.md) (double-entry wallet,
> W9–13), [**ForgeCI**](./18-projects/forgeci/README.md) (CI platform, W14–19), [**FlagForge**](./18-projects/flagforge/README.md)
> (feature flags + SDK, W20–23) — and in truthful past work. The method (7 layers, L1–L4, gap templates)
> is in [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md); one deep file per technology is in
> [`17-resume-tech-defense/`](./17-resume-tech-defense/README.md).

---

## Contents

1. [How to use this bank](#1-how-to-use-this-bank)
2. [Experience & career-gap questions](#2-experience--career-gap-questions) `EXP`
3. [Java](#3-java) `JAV`
4. [Spring Boot](#4-spring-boot) `SPR`
5. [REST / HTTP](#5-rest--http) `RST`
6. [Databases — SQL, PostgreSQL, MySQL, Redis](#6-databases--sql-postgresql-mysql-redis) `DB`
7. [Frontend — JavaScript, TypeScript, React](#7-frontend--javascript-typescript-react) `FE`
8. [Testing](#8-testing) `TST`
9. [Git, GitHub, Maven](#9-git-github-maven) `GIT`
10. [Docker & Linux](#10-docker--linux) `DOK`
11. [AWS & deployment](#11-aws--deployment) `AWS`
12. [CI/CD](#12-cicd) `CI`
13. [Debugging & production incidents](#13-debugging--production-incidents) `DBG`
14. [Architecture & design](#14-architecture--design) `ARC`
15. [Project deep-dive questions](#15-project-deep-dive-questions) `PRJ`
16. [Python](#16-python) `PY`
17. [Behavioral-technical hybrids](#17-behavioral-technical-hybrids) `BEH`
18. [Tracking table](#18-tracking-table)

---

## 1. How to use this bank

### Two tracks, never mixed

| Track | What it is | Language | Where you train it |
|---|---|---|---|
| **A · Coding interview** | LeetCode/NeetCode-style problems, OAs, timed rounds | **Python** | [`03-dsa/`](./03-dsa/README.md), [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md), [`OA_PREP.md`](./OA_PREP.md) |
| **B · Résumé / software-engineering interview** | "I see X on your résumé…" probing, project deep-dives, design, debugging | **Java** and the rest of the stack | **This file**, [`17-resume-tech-defense/`](./17-resume-tech-defense/README.md), each project's `interview-questions.md` |

This bank is Track B. Section 16 (Python) is the bridge: Python is both the language you *code in* (A) and a résumé technology backed by each project's Python tool (B). If an interviewer switches tracks mid-conversation ("great — now code it"), switch languages deliberately and say so: "I'll do this in Python, my interview language."

### Drill protocol (from Week 7, 3 questions/week; all techs by Week 23; full grill W24–26)

1. **Pick** 3 questions — rotate technologies as they enter the [readiness matrix](./RESUME_TECH_DEFENSE.md#8-readiness-matrix) (Week 7: Java, Git, SQL). Don't open the `<details>` first.
2. **Answer out loud, recorded**, no notes, 60–120 s. Use the shape *what → how I used it (past truthfully, now specifically) → one concrete decision → one trade-off → offer to go deeper*.
3. **Score 1–4** with the [rubric](./17-resume-tech-defense/README.md#scoring-rubric): **1** vague/wrong · **2** correct but generic, no project example · **3** correct mechanism + a concrete example from a project or truthful past work + one trade-off, survives one follow-up · **4** structured, anticipates the follow-up, admits limits cleanly. −1 for any claim your own code contradicts.
4. **Re-drill** anything < 3: write 5 sentences, read the linked topic file, re-record once. Come back next week; don't re-record three times in a row.
5. **Log** id, date, score in the [tracking table](#18-tracking-table) and [`trackers/technology-tracker.md`](./trackers/technology-tracker.md).

### Honesty rules that apply to every answer

- **"When did you last use X?"** — give the real year and context, then bridge to current evidence: "I last used Redis professionally in [year] for [true scope]. In the last six months I designed the caching in FlowGrid and the job queue in ForgeCI myself — I can show the code."
- **"What have you been doing?"** — one or two true sentences about the gap, then the plan and the evidence: "I stepped away from software between [dates]. When I decided to return I set a six-month plan: daily DSA, four projects of increasing difficulty, all tested and deployed. They're on my GitHub."
- **"Why junior roles with your history?"** — true and unapologetic: "My earlier experience is real, but I've been away and the stack moved — Spring Boot 3, Java 21, containers, cloud. I'd rather join at a level where I can prove current skill fast than claim seniority I need to re-earn. My projects show where I am today; my history shows how I work in a team."
- Never backdate a roadmap project into a past job. Never quote a number you didn't measure with a recorded method. If you don't know, say so and reason out loud from what you do know.

---

## 2. Experience & career-gap questions

<details><summary><b>EXP-1. "Walk me through your résumé."</b></summary>

- 90 seconds, chronological, truthful scope per role (Junior Dev → Developer → Software Engineer → contract SE): what the system was, what *you* owned, one thing you're proud of per role.
- The gap in one sentence. Then: "Since [month] I've rebuilt on the current stack — four projects, deployed, tested; the biggest is ForgeCI, a CI system that runs jobs in Docker."
- End with what you want: a team where you can contribute in the first month and grow.
- *Follow-up they'll ask next:* "Tell me more about [the project you named]" — have PRJ-1 ready.
</details>

<details><summary><b>EXP-2. "What have you been doing since your last role?"</b></summary>

- Two true sentences on the gap. Then the structured plan: DSA daily in Python, Java 21 + Spring Boot 3 rebuilt through FlowGrid, then LedgerX (correctness), ForgeCI (distributed execution), FlagForge (latency + SDK), each with tests, docs and an AWS deploy.
- One measured result you recorded (e.g. FlowGrid order-creation p95 under N concurrent users, methodology in PERFORMANCE.md). No invented numbers.
</details>

<details><summary><b>EXP-3. "When did you last use Spring Boot / Java / SQL professionally?"</b></summary>

- Real year + true scope (template A in [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md#3-handling-when-did-you-last-use-x-and-the-gap)).
- Then what changed and that you know it: `javax` → `jakarta`, `SecurityFilterChain` bean, ProblemDetail, records/sealed types/virtual threads.
- Then evidence: "FlowGrid is Boot 3 on Java 21 — here's the reservation service and its concurrency test."
</details>

<details><summary><b>EXP-4. "Why are you applying for junior / new-grad roles with your history?"</b></summary>

- Honest framing (see honesty rules). Add what your history *gives* the team: you've shipped in real teams, handled tickets, reviews, on-call-style pressure — say only what's true.
- Don't undersell either: "I expect to grow out of the level quickly; I'd rather do that visibly than overclaim."
</details>

<details><summary><b>EXP-5. "Are these projects tutorials?"</b></summary>

- "The ideas are common; the design, code and tests are mine." Give one decision with a reason: FlowGrid chose `SELECT … FOR UPDATE` over `@Version` after measuring retry rates under contention; ForgeCI chose SSE over WebSockets because the client never sends; LedgerX keeps the idempotency row in the same transaction as the journal write.
- Offer the failure-engineering docs: each project has reproduced failures and the fixes.
</details>

<details><summary><b>EXP-6. "What's the most complex thing you've built?"</b></summary>

- ForgeCI: webhook → queue → worker → Docker container → live logs → recovery. Name the hard parts: reliable queue with leases, orphan recovery when a worker dies, retry policy that distinguishes app failure (exit ≠ 0, no retry) from infra failure (retry with backoff), timeouts that kill containers, DAG scheduling.
- One thing you'd do differently (e.g. Streams instead of lists from the start, or SQS if multi-region).
</details>

<details><summary><b>EXP-7. "Which of your past roles is most relevant to this job?"</b></summary>

- Map truthfully: the role closest to the posting's stack or domain. If the domain is logistics/fulfillment, your operations experience is real and relevant to FlowGrid — say what you saw happen with inventory in the real world and how it shaped the state model (on_hand / available / reserved / allocated / picked / shipped).
</details>

<details><summary><b>EXP-8. "What would your last manager say about you?"</b></summary>

- One true strength with an example, one true development area with what you've done about it. Keep it concrete and short.
</details>

<details><summary><b>EXP-9. "How do you keep your skills current now?"</b></summary>

- The rhythm you actually keep: DSA reviews (Day 0/3/7/14/30), weekly mock, failure-engineering exercises, reading official docs for each version bump (Spring Boot release notes, Java 21 JEPs). Point to the [maintenance plan](./16-interview-prep/maintenance-plan.md).
</details>

---

## 3. Java

<details><summary><b>JAV-1. "Tell me about the most recent Java code you wrote."</b></summary>

- "Java 21, daily." Pick: FlowGrid's allocation engine (deterministic scoring as a `Comparator` chain: availability → capacity → zone match → workload → priority → id tie-break), LedgerX's journal (`BigDecimal`, every transaction sums to zero), or ForgeCI's worker loop (`ExecutorService`, per-job timeout via `Future.get(timeout)`, cancellation flag, `finally` cleanup).
- One decision + why. Offer to show the test.
- *Follow-up:* "Why a `Comparator` chain and not a weighted score?" → determinism and explainability; tie-break by id makes allocation reproducible in tests.
</details>

<details><summary><b>JAV-2. "What's new in Java since 8 that you actually use?"</b></summary>

- Records (DTOs everywhere), sealed interfaces + pattern-matching `switch` (ForgeCI's `JobOutcome`: `Succeeded | Failed(exitCode) | InfraError(cause) | Cancelled | TimedOut` — the retry policy switches on it exhaustively), `var`, text blocks (SQL in tests), `HttpClient`, virtual threads (considered for ForgeCI's SSE fan-out; explain pinning caveat in 21).
</details>

<details><summary><b>JAV-3. "Why <code>BigDecimal</code> for money, and what are the gotchas?"</b></summary>

- Binary floats can't represent 0.1; errors accumulate across thousands of entries. Gotchas: construct from `String`/`valueOf`, `equals` compares scale (`2.0` ≠ `2.00`) → `compareTo`, division needs a `RoundingMode`.
- LedgerX: `NUMERIC(19,4)` in Postgres ↔ `BigDecimal`; invariant test sums entries to exactly `ZERO` with `compareTo`.
</details>

<details><summary><b>JAV-4. "How does <code>HashMap</code> work internally?"</b></summary>

- Buckets array, index from spread hash, collisions → list → red-black tree at 8 (table ≥ 64), resize at 0.75 load. Average O(1). Not thread-safe; `ConcurrentHashMap.compute/merge` for atomic updates (ForgeCI's per-worker in-memory counters before they moved to Redis).
- *Follow-up:* "What breaks if `hashCode` is inconsistent with `equals`?" → lookups miss; records generate both correctly.
</details>

<details><summary><b>JAV-5. "How would you make this class thread-safe?" (they show a counter/cache)</b></summary>

- Find the shared mutable state and the compound action. Prefer immutability → confinement → concurrent structures (`AtomicLong`, `ConcurrentHashMap.merge`) → locks. Mention visibility (`volatile`/happens-before).
- How you'd test it: N threads + `CountDownLatch` released together, repeated — the shape of FlowGrid's one-unit reservation test and LedgerX's $500/$400/$400 test.
</details>

<details><summary><b>JAV-6. "Explain the Java memory model in one minute."</b></summary>

- Threads may cache/reorder; `happens-before` edges (lock release→acquire, `volatile` write→read, thread start/join, executor submit→run) make writes visible. Without them, a plain `boolean running` flag may never be seen — ForgeCI's cancellation flag is `volatile`/`AtomicBoolean` for this reason.
</details>

<details><summary><b>JAV-7. "Virtual threads — when do they help and when don't they?"</b></summary>

- Help: many blocking I/O tasks (thread-per-request, SSE connections held open). Don't help: CPU-bound work; and `synchronized` around blocking I/O can pin the carrier in 21 (use `ReentrantLock`). Don't pool them. FlagForge's SDK polling client stays on a single platform thread — no need.
</details>

<details><summary><b>JAV-8. "Checked vs unchecked exceptions — how did you decide in your code?"</b></summary>

- Domain failures the caller must handle become unchecked domain exceptions mapped to ProblemDetail (`InsufficientStockException` → 409). Checked for recoverable I/O at boundaries (docker-java, GitHub client) — wrapped into `InfraError` so the retry policy can act on the category, not the class.
</details>

<details><summary><b>JAV-9. "Streams vs loops — where do you draw the line?"</b></summary>

- Streams for transform/filter/group with no side effects (LedgerX grouping entries by account with `groupingBy` + `reducing(BigDecimal.ZERO, BigDecimal::add)`); loops for early exit, index logic, or when the stream needs a comment to be understood. Never side effects in lambdas.
</details>

<details><summary><b>JAV-10. "Where would you look first for a memory leak in a long-running Java service?"</b></summary>

- Heap dump on OOM (`-XX:+HeapDumpOnOutOfMemoryError`) → MAT dominator tree → usually an unbounded map/list or listeners never removed. ForgeCI's risk spot: log chunk buffers per running job — bounded by flushing to Postgres every N chunks/ms; SSE emitters removed on completion/timeout.
</details>

---

## 4. Spring Boot

<details><summary><b>SPR-1. "Tell me how you used Spring Boot."</b></summary>

- Past: truthful scope (e.g. "added endpoints and fixed JPA bugs in an existing Boot 2 service").
- Now: "All four projects are Boot 3 on Java 21. FlowGrid is the most complete: layered controller/service/repository, JPA + Flyway, Spring Security with JWT and four roles (ADMIN, OPS_MANAGER, WAREHOUSE_ASSOCIATE, VIEWER), Bean Validation + ProblemDetail, Redis catalog cache, `@Scheduled` low-stock alerts, Actuator + Micrometer, OpenAPI. ForgeCI runs as *two* Boot apps — api and worker — from one Maven reactor. FlagForge adds multi-tenant authorization and an SSE stream."
- One hard part: reservations under concurrency — `@Transactional` + `@Lock(PESSIMISTIC_WRITE)` measured against `@Version`.
- *Follow-up:* "What does `@SpringBootApplication` actually do?" → SPR-3.
</details>

<details><summary><b>SPR-2. "What is dependency injection?"</b></summary>

- Objects don't construct their collaborators; the container creates beans and hands them in — constructor injection, so dependencies are explicit, `final`, and a test can pass a fake (`new ReservationService(fakeRepo, fixedClock)`).
- Mechanism: `ApplicationContext` scans stereotypes, builds a graph, resolves by type (then `@Qualifier`/`@Primary`), wraps some beans in proxies (`@Transactional`, `@Cacheable`).
- Example: FlagForge collects every `RuleEvaluator` bean as `List<RuleEvaluator>` ordered by priority — adding a rule type is a new class, not an edit.
- *Follow-up:* "Two beans implement the same interface — what happens?" → `NoUniqueBeanDefinitionException` unless `@Primary`/`@Qualifier`/collection injection.
</details>

<details><summary><b>SPR-3. "What happens at startup — how does auto-configuration decide what to create?"</b></summary>

- `@EnableAutoConfiguration` loads candidates from `AutoConfiguration.imports`; each is conditional (`@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`). Your own `@Bean` makes Boot back off. Debug with `--debug` / `/actuator/conditions`. Example: defining a `SecurityFilterChain` replaces the default "everything authenticated" chain.
</details>

<details><summary><b>SPR-4. "What happens when an HTTP request reaches your backend?"</b></summary>

- Tomcat thread accepts → servlet filter chain: request-ID MDC filter, then Spring Security (`BearerTokenAuthenticationFilter`/JWT filter validates signature + expiry, builds `Authentication`, authorization rules → 401/403) → `DispatcherServlet` → handler mapping → argument resolution + `@Valid` (400 via `@RestControllerAdvice` → ProblemDetail) → controller → service method behind the `@Transactional` proxy (begin) → repository → Hikari connection → SQL (`SELECT … FOR UPDATE` on the inventory row) → commit → entity → DTO record → Jackson → `ResponseEntity` → back through filters → client.
- Say where each failure surfaces: bad token 401, wrong role 403, validation 400, insufficient stock 409, pool exhausted 500/timeout.
- *Follow-up:* "Where exactly does the transaction start and end?" → at the proxy boundary of the *outermost* `@Transactional` bean method; self-invocation bypasses it.
</details>

<details><summary><b>SPR-5. "How does authentication work in one of your applications?"</b></summary>

- FlowGrid: `POST /api/auth/login` → BCrypt check → signed JWT (HS256/RS256, short expiry, `sub` + roles) → client sends `Authorization: Bearer` → stateless `SecurityFilterChain` (`SessionCreationPolicy.STATELESS`, CSRF off for the token API) validates and sets `SecurityContextHolder` → `authorizeHttpRequests` + `@PreAuthorize("hasRole('OPS_MANAGER')")` on stock adjustments.
- FlagForge adds two identities: users with org roles, and SDK keys per environment checked by a separate filter on `/api/eval`. ForgeCI uses GitHub OAuth (Spring Security OAuth2 client) so it can act on the user's repos.
- Trade-off: JWT scales statelessly but revocation is hard → short expiry + refresh; where the React app stores the token and why.
- *Follow-up:* "How do you revoke a JWT?" → you can't; short-lived + refresh token rotation, or a denylist by `jti`.
</details>

<details><summary><b>SPR-6. "How does <code>@Transactional</code> work, and when doesn't it?"</b></summary>

- Proxy around the bean; begin/commit/rollback around the call; rollback on unchecked by default (`rollbackFor` for checked). Doesn't apply on self-invocation, private methods, or when the exception is caught inside.
- LedgerX: the idempotency row and the journal entries are written in the *same* transaction so a crash can't record a response for a transfer that didn't happen; the audit log uses `REQUIRES_NEW` so it survives a rollback.
- *Follow-up:* "What's the difference between `REQUIRED` and `REQUIRES_NEW`?" → join vs suspend + new; and the connection-pool cost of nesting.
</details>

<details><summary><b>SPR-7. "How do you test a Spring Boot service?"</b></summary>

- Pyramid: plain JUnit + Mockito for services (no context) → `@WebMvcTest` + `MockMvc` for controllers (status codes, ProblemDetail shape, 401/403) → `@DataJpaTest` + Testcontainers Postgres 16 (real dialect, Flyway migrations) → a few `@SpringBootTest` end-to-end → concurrency tests (N threads + latch).
- Evidence: FlowGrid's suite in GitHub Actions via `mvn verify` since W4; LedgerX's invariant suite; ForgeCI's Testcontainers Postgres + Redis chaos-style tests.
- *Follow-up:* "Why not H2?" → it hides `ON CONFLICT`, `FOR UPDATE SKIP LOCKED`, JSONB, triggers.
</details>

<details><summary><b>SPR-8. "What is the N+1 problem and how did you fix it?"</b></summary>

- Load N orders, touch `order.lines` lazily → N more queries. Detect with SQL logging or a statement counter in tests. Fix: `JOIN FETCH`/`@EntityGraph` for the endpoint that needs lines, DTO projections for lists, `default_batch_fetch_size` as a safety net. FlowGrid's order list returns a projection; order detail fetch-joins lines.
</details>

<details><summary><b>SPR-9. "How do you run two Spring Boot apps from one codebase?" (ForgeCI)</b></summary>

- Maven reactor: `forgeci-core` (domain, persistence, queue contracts), `forgeci-api` (web, security, SSE, webhooks), `forgeci-worker` (`ApplicationRunner` loop, docker-java, executor beans, graceful shutdown). Core has no web dependency; docker-java lives only in the worker. Same Flyway migrations, one owner (api) runs them. Different profiles/config; two images.
</details>

<details><summary><b>SPR-10. "What does Actuator give you, and what did you expose?"</b></summary>

- `/health` (with DB/Redis indicators — FlowGrid reports DEGRADED when Redis is down), `/metrics` via Micrometer (Hikari pool, HTTP timings, cache hit/miss, ForgeCI queue depth), `/info` with the git SHA the deploy smoke test checks. Everything else off; health unauthenticated, the rest behind auth.
</details>

---

## 5. REST / HTTP

<details><summary><b>RST-1. "How would a React frontend communicate with your Spring Boot backend?"</b></summary>

- JSON over HTTPS. A single typed API client (`request<T>()` over `fetch`) attaches `Authorization: Bearer <jwt>`, checks `res.ok`, parses RFC 7807 ProblemDetail into a typed `ApiError`, and every screen renders loading/error/success from a discriminated union.
- Backend side: `@RestController` returning records, `@Valid` → 400 with field errors, pagination `?page=&size=&sort=`, 401/403 by role.
- Same origin in production (nginx serves the Vite build and proxies `/api` to the API on the Compose network) so CORS never applies; in dev the Vite proxy does the same. If origins differ: `CorsConfigurationSource` in Spring Security, not just MVC.
- Streaming: ForgeCI's live logs and FlagForge's propagation use SSE (`EventSource`, `Last-Event-ID` replay) — one-way push over plain HTTP, no WebSocket infra.
- *Follow-up:* "Where do you store the JWT in the browser?" → memory vs `localStorage` vs httpOnly cookie trade-offs (FE-5).
</details>

<details><summary><b>RST-2. "Design the endpoints for placing an order that reserves stock."</b></summary>

- `POST /orders` (+ `Idempotency-Key`) → 201 + `Location`, body includes reservations; `GET /orders/{id}`; `POST /orders/{id}/cancellation` → 204 releasing reservations; `GET /orders?status=&page=&size=&sort=`; `GET /skus/{id}/inventory?warehouseId=`.
- Errors: 400 validation, 401/403, 404 unknown SKU, 409 insufficient stock (ProblemDetail with `type`), 422 same key + different body. This is FlowGrid M2.
</details>

<details><summary><b>RST-3. "What is idempotency, and how did you implement it?"</b></summary>

- Repeating a request has the same effect as once. FlowGrid: `Idempotency-Key` header → table (key, request hash, response, TTL) with a unique constraint; replay returns the stored response; different body → 422.
- LedgerX does it *properly*: the idempotency row is written in the same transaction as the journal entries and has a status (`IN_PROGRESS`/`DONE`) so a concurrent retry waits or gets the stored result — tested with retry-after-crash.
- *Follow-up:* "What if two identical requests arrive at the same time?" → unique constraint makes the second insert fail → it reads and returns/awaits the first's result.
</details>

<details><summary><b>RST-4. "401 vs 403 vs 404?"</b></summary>

- 401 not authenticated (missing/expired token, include `WWW-Authenticate`); 403 authenticated but not allowed (VIEWER adjusting stock); 404 for resources you don't want to reveal exist (FlagForge returns 404 for a project in another org).
</details>

<details><summary><b>RST-5. "Offset vs cursor pagination — which did you use and why?"</b></summary>

- FlowGrid: offset (`page/size/sort`) — dashboards need page jumping, tables are moderate; always a unique tiebreaker in `ORDER BY`. LedgerX history: cursor (`?after=<opaque cursor>` → `WHERE (created_at, id) < (?, ?)`) — append-only, deep, must be stable under inserts; index on `(account_id, created_at, id)`.
</details>

<details><summary><b>RST-6. "How do you return errors consistently?"</b></summary>

- `@RestControllerAdvice` → `ProblemDetail` (`application/problem+json`) with `type`, `title`, `status`, `detail`, `instance`, plus `errors[]` for validation. Domain exceptions map to 404/409/422; never leak stack traces. The React client and the Python harness both parse the same shape.
</details>

<details><summary><b>RST-7. "What is CORS and why did curl work when the browser didn't?"</b></summary>

- Browser-enforced: cross-origin page needs `Access-Control-Allow-*` headers; non-simple requests preflight with `OPTIONS`. curl doesn't care. Fix in Spring Security's CORS config (security rejects the preflight before MVC sees it) — or avoid it with same-origin nginx. Not a security boundary; auth is.
</details>

<details><summary><b>RST-8. "How did you design an API that a client library consumes?" (FlagForge)</b></summary>

- Snapshot endpoint with `ETag`/`If-None-Match` → 304 on poll; SSE update stream; eval endpoint rate-limited per SDK key (429 + `Retry-After`); versioned immutable configs so the SDK can report which version it evaluated; defaults documented so the SDK behaves when the server is unreachable. Two SDKs (Java, Python) against one contract test suite.
</details>

---

## 6. Databases — SQL, PostgreSQL, MySQL, Redis

<details><summary><b>DB-1. "Why PostgreSQL instead of MySQL?"</b></summary>

- Honest first: "Both are excellent; for FlowGrid either would work." Then the concrete reasons Postgres earned its place: **transactional DDL** (a failed Flyway migration rolls back cleanly — in MySQL DDL auto-commits and can leave a half-applied migration), **triggers + revoked privileges** making LedgerX's `ledger_entry` append-only, `NUMERIC` money, `RETURNING`, partial/expression indexes, `SKIP LOCKED` (MySQL 8 has it too), `timestamptz`, JSONB for audit payloads, and RDS support.
- Show you know MySQL: InnoDB clustered PK (UUID PKs hurt more), REPEATABLE READ default with gap locks, `ON DUPLICATE KEY UPDATE` instead of `ON CONFLICT`, `utf8mb4`. "I ported FlowGrid's inventory schema to MySQL 8 in a throwaway branch to feel the differences."
- *Follow-up:* "When would you pick MySQL?" → team/ops expertise, existing Aurora MySQL, read-heavy simple workloads.
</details>

<details><summary><b>DB-2. "How would you investigate a slow database query?"</b></summary>

- Find it: `pg_stat_statements` (top by total time), slow-query log, or the endpoint's timer in Micrometer. Reproduce with the real parameters.
- `EXPLAIN (ANALYZE, BUFFERS)`: seq scan on a big table? estimated vs actual rows off (stale stats → `ANALYZE`)? sort spilling (`external merge`)? nested loop over many rows? shared read vs hit?
- Fix in order: query shape (no function on the indexed column, no `SELECT *`, sargable predicates) → the right index (composite, equality columns first then range/sort; partial if filtered; `CREATE INDEX CONCURRENTLY`) → pagination → cache. Verify with a before/after plan and timing; record it.
- Story: ForgeCI's log-replay query `WHERE job_id = ? AND seq > ? ORDER BY seq` went from Seq Scan + Sort to an Index Scan with `log_chunks(job_id, seq)`; numbers in PERFORMANCE.md.
- *Follow-up:* "The index exists but isn't used — why?" → function/cast on the column, leftmost-prefix violated, low selectivity, type mismatch, stale stats.
</details>

<details><summary><b>DB-3. "How did you prevent double reservation / negative balance at the database level?"</b></summary>

- FlowGrid: `SELECT … FOR UPDATE` on the `inventory_levels` row, check `available >= qty`, update — all in one transaction; `CHECK (available >= 0)` as the last line of defence; measured against `@Version` (0 rows updated → 409 → retry).
- LedgerX: lock both accounts in ascending id order (no deadlocks), `CHECK (balance >= 0)`, the $500/$400/$400 test.
- *Follow-up:* "Pessimistic vs optimistic — when each?" → contention level and retry cost.
</details>

<details><summary><b>DB-4. "Explain isolation levels with something you actually observed."</b></summary>

- LedgerX M2 experiments in two `psql` sessions: READ COMMITTED (default) shows non-repeatable reads; REPEATABLE READ gives a snapshot and aborts conflicting writers (`40001`); SERIALIZABLE (SSI) catches write skew but needs retries. Chose READ COMMITTED + explicit row locks for transfers: predictable, no retry loop.
</details>

<details><summary><b>DB-5. "What is MVCC and why do long transactions hurt?"</b></summary>

- Updates write new row versions; readers see a snapshot; dead tuples cleaned by VACUUM. A long or idle-in-transaction session pins old versions → bloat, and blocks `CREATE INDEX CONCURRENTLY`. Set `idle_in_transaction_session_timeout`; keep no HTTP calls inside transactions (ForgeCI never holds a DB transaction while talking to Docker).
</details>

<details><summary><b>DB-6. "Write: top 3 SKUs by shipped quantity per warehouse per month."</b></summary>

- CTE aggregating `SUM(quantity)` by `date_trunc('month', shipped_at)`, `warehouse_id`, `sku_id`; then `DENSE_RANK() OVER (PARTITION BY month, warehouse_id ORDER BY qty DESC)`; filter `r <= 3`. Mention `DENSE_RANK` vs `ROW_NUMBER` for ties.
</details>

<details><summary><b>DB-7. "How does LedgerX make the ledger immutable?"</b></summary>

- Append-only `ledger_entry`: a trigger raises on UPDATE/DELETE, and the app's DB role has no UPDATE/DELETE privilege on it. Corrections are new compensating entries (reversal/refund) linked to the original. Balance is derived (`SUM`) and a materialized balance is checked by the reconciliation job and by an independent Python verifier.
</details>

<details><summary><b>DB-8. "What did you use Redis for?"</b></summary>

- A different job per project: FlowGrid — cache-aside catalog + low-stock cache, invalidated after commit, degrade to DB when Redis is down; ForgeCI — reliable job queue (`BLMOVE` to a per-worker processing list + lease), pub/sub for log fan-out, Lua semaphore for per-project concurrency limits, worker heartbeats; FlagForge — environment snapshot cache invalidated on publish, pub/sub → SSE propagation, stampede protection.
- *Follow-up:* "What happens when Redis goes down?" → DB-9.
</details>

<details><summary><b>DB-9. "What happens when Redis goes down?"</b></summary>

- FlowGrid: reads fall back to Postgres with timeouts; health DEGRADED; no request hangs (tested by killing the container). ForgeCI: no new dispatch, running jobs finish, webhooks still land in Postgres, leases re-established on return. FlagForge: eval reads the current version from Postgres (slower, correct); SDKs serve their last snapshot (stale-if-error). LedgerX never depended on Redis for correctness.
</details>

<details><summary><b>DB-10. "Cache invalidation — what went wrong and how did you fix it?"</b></summary>

- Race: reader loads old value → writer commits + deletes key → reader writes stale value → stale until TTL. Fix: delete *after* commit (`@TransactionalEventListener(AFTER_COMMIT)`), short TTL as backstop, versioned keys. Stampede on a hot key: single-flight lock (`SET NX EX`) or jittered TTL — FlagForge's snapshot rebuild.
</details>

<details><summary><b>DB-11. "How do you change a schema safely in production?"</b></summary>

- Flyway, never edit an applied migration; expand → migrate → contract so old and new app versions coexist; add nullable → backfill → `NOT NULL`; `CREATE INDEX CONCURRENTLY` (non-transactional migration); `lock_timeout` so an `ALTER` doesn't queue behind a long lock.
</details>

---

## 7. Frontend — JavaScript, TypeScript, React

<details><summary><b>FE-1. "What did you build with React?"</b></summary>

- Past: truthful scope. Now: FlowGrid's operations dashboard (inventory by warehouse with search/filter/sort/pagination, orders, pick/pack screens, low-stock view, returns/transfer forms, role-aware UI, `AuthContext` + `<ProtectedRoute>`, Vitest + RTL); ForgeCI's build UI with a live log over SSE; FlagForge's admin (rules editor, versions, rollback, audit).
- *Follow-up:* "What would you change about its architecture?" → server state via TanStack Query instead of hand-rolled effects.
</details>

<details><summary><b>FE-2. "Explain the event loop; what's the output of this snippet?"</b></summary>

- Stack runs to completion → all microtasks (promise reactions, `await` continuations) → one macrotask (timer, I/O) → repeat. `console.log(1); setTimeout(()=>log(2)); Promise.resolve().then(()=>log(3)); log(4)` → 1 4 3 2.
</details>

<details><summary><b>FE-3. "What is a closure, with an example from your code?"</b></summary>

- Function + captured lexical scope. FlowGrid: debounced SKU search captures the timer id; `useEffect` cleanup captures the `AbortController`. Bug class: stale closure in `setInterval` → functional state updates or a ref.
</details>

<details><summary><b>FE-4. "How do you fetch data correctly in <code>useEffect</code>?"</b></summary>

- `AbortController` created per effect, aborted in cleanup; ignore results after abort; depend on the id (`warehouseId`); discriminated-union load state. StrictMode double-invokes effects in dev — effects must be idempotent with cleanup.
</details>

<details><summary><b>FE-5. "Where do you store the JWT and why?"</b></summary>

- `localStorage`: simple, readable by any XSS. Memory: safest vs XSS, lost on refresh. httpOnly `Secure` `SameSite` cookie: not readable by JS, needs CSRF thinking. State what FlowGrid does (token in memory, documented strategy) and what you'd do for production (short-lived access token in memory + refresh in an httpOnly cookie).
</details>

<details><summary><b>FE-6. "Why TypeScript, and what does it not do?"</b></summary>

- Compile-time shape checking against the API DTOs, safe refactors (renaming `warehouse` → `warehouseId` produced 6 compile errors instead of runtime `undefined`s), self-documenting props. Types are erased — network data is `unknown`; validate at one boundary (type guard/zod) and keep `as` out of components.
</details>

<details><summary><b>FE-7. "How did you keep a live log view responsive at thousands of lines?" (ForgeCI)</b></summary>

- SSE chunks appended to a ref buffer, flushed to state once per animation frame; virtualised window; stable keys by `seq`; reconnect with `Last-Event-ID` so replay is exact; pause auto-scroll when the user scrolls up.
</details>

<details><summary><b>FE-8. "How do you test React components?"</b></summary>

- Vitest + React Testing Library: render, query by role/label, `userEvent`, assert what the user sees; mock the API module (or MSW). Examples: VIEWER sees no "Adjust stock" button; transfer form shows the server's ProblemDetail error; log view orders chunks after a simulated reconnect. The UI check is UX — the backend re-checks every request.
</details>

---

## 8. Testing

<details><summary><b>TST-1. "How do you decide what to test?"</b></summary>

- Behaviours, not methods: happy path, each validation branch, boundaries, error paths, interactions with collaborators. Fakes for your own interfaces, mocks at boundaries (GitHub API, Docker), a real Postgres via Testcontainers for anything touching SQL. Write the failing test first when fixing a bug.
</details>

<details><summary><b>TST-2. "How did you prove there is no double reservation?"</b></summary>

- FlowGrid M2: `ExecutorService` with N threads + `CountDownLatch` released together, all reserving the last unit → exactly one 201, N−1 409, repeated in a loop; asserts the DB row afterwards. Limits: probabilistic → the row lock/CHECK constraint is the real guarantee; the test is the alarm.
</details>

<details><summary><b>TST-3. "What is an invariant test, and what does LedgerX's suite check?"</b></summary>

- Properties that must hold after *any* sequence of operations: every journal transaction sums to zero; `balance == SUM(entries)`; `ledger_entry` rejects UPDATE/DELETE; a reversal nets the original to zero. Run after randomised transfer bursts and after crash injection.
</details>

<details><summary><b>TST-4. "How do you test a crash between two steps?"</b></summary>

- LedgerX M4 fault-injection hook (a `FaultInjector` bean that throws at a named point in tests): crash after the idempotency row is written but before the journal → transaction rolls back, retry with the same key succeeds exactly once; crash after commit but before the response → retry returns the stored response. Assert with the invariant suite.
</details>

<details><summary><b>TST-5. "A test is flaky in CI — what do you do?"</b></summary>

- Reproduce with `@RepeatedTest(100)`; usual suspects: `now()` (inject `Clock`), sleeps (Awaitility), shared state/test order, unordered collections, container readiness. Fix the root cause; quarantine only briefly; never "re-run until green".
</details>

<details><summary><b>TST-6. "Why Testcontainers rather than H2?"</b></summary>

- Real dialect and features: `ON CONFLICT`, `FOR UPDATE SKIP LOCKED`, triggers (LedgerX immutability), JSONB, `timestamptz`. Cost: Docker in CI (fine on `ubuntu-latest`), slower startup → singleton container per JVM.
</details>

<details><summary><b>TST-7. "What does the ForgeCI failure-engineering suite cover?"</b></summary>

- Worker killed mid-job → lease expires → job re-queued once (infra failure); step exits 1 → no retry; container start fails → retry with backoff, max N; timeout → container killed, exit 137, `TIMED_OUT`; duplicate webhook delivery → one build; Redis restart → dispatch resumes; API restart → SSE clients reconnect and replay from `seq`.
</details>

---

## 9. Git, GitHub, Maven

<details><summary><b>GIT-1. "What Git workflow do you use?"</b></summary>

- Past: what the team actually did. Now: GitHub Flow — one PR per milestone (linked GitHub milestone/issues), CI required, squash-merge, tags `v1.0` (and `sdk-v1.0.0` for FlagForge's SDK). Rebase private branches onto `main`; never rebase shared history.
</details>

<details><summary><b>GIT-2. "Merge vs rebase?"</b></summary>

- Merge preserves history with a merge commit; rebase replays commits onto a new base (new SHAs) for linear history. Rebase your own feature branch; merge/squash into `main`. Cleaned LedgerX's locking-experiments branch with interactive rebase before review.
</details>

<details><summary><b>GIT-3. "You committed a secret. What now?"</b></summary>

- Rotate first — history rewriting doesn't un-leak. Then `git filter-repo`/BFG if policy requires, coordinated force-push, GitHub support for cached views. Prevent: `.gitignore`, env vars, push protection.
</details>

<details><summary><b>GIT-4. "A regression appeared in the last 40 commits — how do you find it?"</b></summary>

- `git bisect run ./mvnw -q -Dtest=RegressionTest test` — ~6 steps; keep the regression test.
</details>

<details><summary><b>GIT-5. "How does ForgeCI use GitHub?"</b></summary>

- OAuth login (PAT for the MVP first), repo registration, webhook receiver (HMAC-SHA256 over the raw body with constant-time compare, `X-GitHub-Delivery` unique constraint for dedupe, fast 2xx then async), clone at the commit SHA with a short-lived token, commit status via the REST API. Know retries/redelivery behaviour.
</details>

<details><summary><b>GIT-6. "Explain Maven's lifecycle and why CI runs <code>mvn verify</code>."</b></summary>

- `validate → compile → test → package → verify → install → deploy`; Surefire runs unit tests in `test`, Failsafe runs `*IT` in `integration-test` and checks in `verify` so cleanup still runs. `install` is unnecessary in CI.
</details>

<details><summary><b>GIT-7. "How is ForgeCI's Maven build structured, and how did you publish the FlagForge SDK?"</b></summary>

- ForgeCI: reactor with `forgeci-core`, `forgeci-api`, `forgeci-worker`; parent `dependencyManagement`; `mvn -pl forgeci-worker -am verify`; two fat JARs → two images. FlagForge: `flagforge-sdk` as a separate artifact with no Spring dependency, semver + tags, installed to a local repo and consumed by the sample app; contract tests in the server module. Conflicts: nearest-wins, `dependency:tree -Dverbose`, pin in `dependencyManagement`.
</details>

---

## 10. Docker & Linux

<details><summary><b>DOK-1. "What did Docker solve for your application?"</b></summary>

- Reproducible dependencies (Postgres 16 + Redis 7 for everyone with one command), dev/CI/prod parity (the image tested in CI is the image deployed), real-DB integration tests via Testcontainers, a simple deploy (`docker compose pull && up -d` on EC2), isolation between api / worker / ui.
- Honest about what it didn't solve: orchestration, HA, secrets management — and, in ForgeCI, a *security boundary* for untrusted build steps (containers isolate; the Docker socket is root on the host).
- ForgeCI goes further: the worker *creates* containers through the Engine API — one temporary container per CI job, workspace volume, steps via `exec`, exit codes captured, killed on timeout, always removed in `finally`.
- *Follow-up:* "Walk me through your Dockerfile" → DOK-2.
</details>

<details><summary><b>DOK-2. "Walk me through your Dockerfile and Compose file."</b></summary>

- Multi-stage: Maven build stage (copy `pom.xml`, `dependency:go-offline`, then `src`, `package -DskipTests` — tests ran in CI), JRE runtime stage, non-root user, exec-form `ENTRYPOINT`, `-XX:MaxRAMPercentage`. Compose: `api`, `postgres` (named volume, `pg_isready` healthcheck), `redis`, `nginx` (React build + `/api` proxy), `depends_on: condition: service_healthy`; ForgeCI adds `worker` with `--scale`.
</details>
