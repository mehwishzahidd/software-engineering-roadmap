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

1. **Pick** 3 questions — rotate technologies as they enter the [readiness matrix](./RESUME_TECH_DEFENSE.md#8-readiness-matrix) (Week 7: Java, Git, SQL). Don't expand the answer outline first.
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

<details><summary><b>DOK-3. "How do containers talk to each other, and what's the classic mistake?"</b></summary>

- Compose creates a user-defined bridge network with DNS by service name: `jdbc:postgresql://postgres:5432/flowgrid`, `redis:6379`, nginx `proxy_pass http://api:8080`. Only nginx publishes a port. `localhost` inside a container is the container itself — the classic "connection refused".
</details>

<details><summary><b>DOK-4. "A container exits with code 137 — what happened?"</b></summary>

- SIGKILL: OOM-killed (`docker inspect` → `OOMKilled: true`) or stop timeout. JVM: default heap is 25 % of container memory → set `-XX:MaxRAMPercentage`; check `dmesg` on the host. In ForgeCI, 137 on a *job* container means the per-job timeout fired (`TIMED_OUT`, not `FAILED`).
</details>

<details><summary><b>DOK-5. "Explain SIGTERM vs SIGKILL and how your service shuts down gracefully."</b></summary>

- `docker stop` sends SIGTERM to PID 1, waits, then SIGKILL. Exec-form entrypoint so the JVM is PID 1; `server.shutdown=graceful` finishes in-flight requests; ForgeCI's worker stops taking new jobs, finishes or releases the current lease, then exits (exit 143 = SIGTERM).
</details>

<details><summary><b>DOK-6. "The API is slow on the server — what commands do you run first?"</b></summary>

- `uptime`/`top` (load vs cores), `free -h` (available, not free), `df -h`, `docker stats`, `ss -tlnp`, app logs filtered by `requestId`, `jcmd <pid> Thread.print` for stuck threads, `pg_isready`/`pg_stat_activity` for lock waits. Narrow to app vs DB vs network before touching anything.
</details>

<details><summary><b>DOK-7. "Why is giving a worker the Docker socket dangerous, and what did you do about it?"</b></summary>

- Socket access = root on the host (mount `/`, escape). ForgeCI: workers on their own EC2 instance with their own SG, no privileged flag for job containers, resource limits, non-root user in job images, documented as isolation-not-security — and the honest next step (rootless Docker, gVisor/Firecracker, or a managed runner) if untrusted repos were allowed.
</details>

---

## 11. AWS & deployment

<details><summary><b>AWS-1. "How would you deploy this architecture on AWS?"</b></summary>

- What I did (FlowGrid W8, repeated for LedgerX, ForgeCI, FlagForge): Route 53/DNS → EC2 (SG: 443 from the world, 22 closed — SSM Session Manager) running nginx + api + redis in Compose → **RDS PostgreSQL** in private subnets (SG allows 5432 only from the EC2 SG, automated backups) → **S3** for report exports via an instance role (`PutObject/GetObject` on one prefix, pre-signed URLs) → **CloudWatch** logs via the `awslogs` driver + alarms on 5xx/CPU/RDS storage → GitHub Actions builds the image, pushes to GHCR, assumes a deploy role via **OIDC** (no stored keys), `docker compose pull && up -d` through SSM, smoke test on `/actuator/health`.
- ForgeCI variant: api instance + a separate worker instance (Docker socket), `--scale worker=N`; SQS weighed against Redis for the queue.
- Next steps if it had to scale: ALB + ASG or ECS/Fargate for stateless api, ElastiCache Redis, RDS Multi-AZ; the scheduled low-stock job guarded by a distributed lock. Cost controls: budget alarm, smallest instance, teardown script.
- *Follow-up:* "What breaks if the AZ goes down?" → everything on one instance; that's the documented trade-off for a single-box deploy and the reason for the "next steps".
</details>

<details><summary><b>AWS-2. "Explain least privilege with a policy you wrote."</b></summary>

- Instance role with exactly `s3:PutObject/GetObject` on `arn:aws:s3:::flowgrid-reports-*/*` and `logs:CreateLogStream/PutLogEvents` on the app's log group; default credential chain in the SDK — no keys in env files. Prove it by showing `ListBucket` and writes outside the prefix are denied.
</details>

<details><summary><b>AWS-3. "How does CI deploy without storing AWS keys?"</b></summary>

- GitHub OIDC: `permissions: id-token: write`, `aws-actions/configure-aws-credentials` with `role-to-assume`; the role's trust policy pins `aud` and `sub` to `repo:<owner>/<repo>:environment:production`. ~1 h credentials per run.
</details>

<details><summary><b>AWS-4. "The app on EC2 can't reach RDS — walk me through it."</b></summary>

- Endpoint resolves? RDS SG allows 5432 from the app SG (SG reference, not CIDR)? Same VPC/routing? RDS `available`? Credentials/DB name? `sslmode`? `nc -zv host 5432` from the instance; `max_connections` vs Hikari pool × instances.
</details>

<details><summary><b>AWS-5. "Multi-AZ vs read replica vs backup?"</b></summary>

- Multi-AZ: synchronous standby for availability (failover, same endpoint), not read scaling. Read replica: async, separate endpoint, replica lag → read-your-writes issues. Backups/PITR: recovery from mistakes.
</details>

<details><summary><b>AWS-6. "How do you know the app is down, and how do you keep costs sane?"</b></summary>

- Alarm on EC2 status checks + a health probe from *outside* the instance (Route 53 health check), 5xx metric filter on logs, RDS CPU/storage alarms → SNS. Costs: budget alarm, no NAT gateway, single-AZ for dev, S3 lifecycle rules, delete unattached EBS/EIPs, teardown script, weekly Cost Explorer check.
</details>

<details><summary><b>AWS-7. "EC2 + Compose vs ECS/Fargate vs Lambda for your projects?"</b></summary>

- EC2 + Compose: simplest, cheapest for one box, manual patching, no self-healing. ECS/Fargate: managed orchestration, rolling deploys, per-task IAM — the natural next step for api + worker. Lambda: poor fit for long-running workers (ForgeCI) or Redis-connected APIs with scheduled jobs without a redesign.
</details>

---

## 12. CI/CD

<details><summary><b>CI-1. "What is CI/CD?"</b></summary>

- **CI**: every change merged frequently and automatically built + tested, so integration problems surface within minutes. **Continuous delivery**: every green build yields a deployable artefact; deploy is one (possibly manual) step. **Continuous deployment**: green builds go to production automatically.
- Mine: "In FlowGrid a PR runs `mvn verify` (unit + slice + Testcontainers) and the frontend lint/type-check/tests; merge to `main` builds an image tagged with the git SHA, pushes it to GHCR, and after a manual approval on the `production` environment deploys to EC2 via an OIDC-assumed role, then a smoke test hits `/actuator/health`; rollback = redeploy the previous SHA."
- Differentiator: "I also built ForgeCI — webhook receiver, Redis job queue, workers running steps in Docker containers, live logs — so I can explain what happens *behind* a pipeline, not just write the YAML."
- Past: truthful about what you wrote vs used.
- *Follow-up:* "How do you roll back?" → CI-3.
</details>

<details><summary><b>CI-2. "What runs in your pipeline, in what order, and why?"</b></summary>

- Compile/lint → unit → slice/integration (Testcontainers) → build image once → push (SHA tag) → deploy (approval) → smoke test. Fast feedback first; build once, promote the same artefact; deploy only from `main`. ForgeCI: path-filtered jobs per module, `mvn -pl <module> -am verify`, three images.
</details>

<details><summary><b>CI-3. "A deploy broke production. What does your pipeline give you?"</b></summary>

- Deployment history says which SHA is live; rollback = redeploy the previous SHA (images are immutable, never `:latest`); migrations are expand/contract so the old version still runs; then add the test that would have caught it. `/actuator/info` shows the SHA so you can verify.
</details>

<details><summary><b>CI-4. "Tests pass locally, fail in CI."</b></summary>

- Differences: JDK, timezone/locale, env vars/secrets (none on fork PRs), Docker availability, test order, resources. Reproduce with the same command (`./mvnw -B verify`), clean `~/.m2`, read the failing step's log first.
</details>

<details><summary><b>CI-5. "How does ForgeCI decide whether to retry a failed job?"</b></summary>

- Failure taxonomy: app failure (a step exits ≠ 0) → never retry; infra failure (container start error, worker lost → lease expired, Docker daemon error) → retry with exponential backoff up to N; timeout → `TIMED_OUT`, no retry by default; cancellation → terminal. Encoded as a sealed `JobOutcome` so the policy is exhaustive.
</details>

---

## 13. Debugging & production incidents

<details><summary><b>DBG-1. "How would you debug a slow API?"</b></summary>

- **Measure first**: which endpoint, p50 vs p99, since when, what changed (deploy, data growth, traffic). Micrometer HTTP timers / logs with `requestId` and durations; correlate with DB (`pg_stat_statements`, lock waits in `pg_stat_activity`), Redis (`SLOWLOG`, hit ratio), Hikari pool (`active/pending` — pool exhaustion looks like a slow API), CPU/GC (`jcmd GC.heap_info`, thread dump if threads are `BLOCKED`).
- **Narrow**: is time spent in the DB (slow query → DB-2), waiting for a connection (pool too small / long transactions / leak), in a downstream call inside a transaction, in serialization of a huge payload (missing pagination), or in N+1 queries (SQL logging)?
- **Fix and verify** with the same measurement; add an alert on the metric that would have caught it.
- Story: FlowGrid's low-stock endpoint slowed as inventory grew — the query needed a partial index on `available < reorder_point`; the Redis low-stock cache came second, not first.
- *Follow-up:* "How do you tell 'slow DB' from 'slow app'?" → DB time from `pg_stat_statements` vs endpoint time; if pool wait dominates, the DB isn't slow — the app holds connections too long.
</details>

<details><summary><b>DBG-2. "Describe a production bug you might encounter in this architecture and how you would diagnose it."</b></summary>

- Pick one with a real mechanism: **duplicate reservations after a client retry** (FlowGrid). Symptom: an order shows two reservations for one line; support ticket. Diagnose: pull both rows by `requestId`/timestamps → two `POST /orders` within 200 ms from the same client → the mobile client retried on timeout → the first request had succeeded after the client gave up. Root cause: the endpoint accepted a retry as a new order (missing/ignored `Idempotency-Key`), and no unique constraint on the business key. Fix: enforce `Idempotency-Key` (stored key + request hash + response), unique constraint, return the stored 201 on replay; regression test that replays the same request twice; alert on duplicate-line count.
- Alternatives ready: stale catalog after Redis invalidation raced the commit (DB-10); LedgerX drift found by the reconciliation job (a transfer partially committed because a method was self-invoked and bypassed `@Transactional`); ForgeCI orphaned jobs after a worker OOM (lease sweep wasn't running because the scheduler thread died silently); FlagForge SDK serving stale flags because `ETag` handling returned 304 for a changed snapshot.
- *Follow-up:* "How would you have caught it before production?" → the concurrency test for the reservation path, and a failure exercise that kills the client mid-request.
</details>

<details><summary><b>DBG-3. "Requests hang; nothing errors."</b></summary>

- Lock waits: `pg_stat_activity` where `wait_event_type = 'Lock'`, `pg_blocking_pids(pid)`; usually an idle-in-transaction session. Or pool exhaustion: Hikari `pending` climbing with "Connection is not available". Thread dump shows where the app threads wait. Fix the long transaction; set `idle_in_transaction_session_timeout`; keep no I/O inside transactions.
</details>

<details><summary><b>DBG-4. "<code>LazyInitializationException</code> in production — what do you do?"</b></summary>

- A lazy association touched after the persistence context closed (usually Jackson serialising an entity). Fix properly: map to DTOs inside the service, `JOIN FETCH`/`@EntityGraph` for what the endpoint needs. Don't enable open-in-view or make everything EAGER.
</details>

<details><summary><b>DBG-5. "The service hangs with near-zero CPU."</b></summary>

- Deadlock or everything blocked on a lock/pool. `jcmd <pid> Thread.print` → "Found one Java-level deadlock" or many `BLOCKED`/`WAITING` on the same monitor. LedgerX avoids DB deadlocks by locking accounts in id order; in Java, consistent lock ordering + timeouts (`tryLock`).
</details>

<details><summary><b>DBG-6. "The reconciliation job flags drift on three accounts. Walk me through it."</b></summary>

- Confirm with the independent Python verifier (same finding → data, not the job). Diff `SUM(entries)` vs materialized balance per account, find the first entry after which they diverge, correlate with the audit log and deploy history. Typical causes: a code path updating the balance outside the journal write, a reversal applied twice, a crash between steps before the outbox pattern existed. Fix forward with compensating entries — never edit the ledger.
</details>

<details><summary><b>DBG-7. "A worker died mid-job. What happens, and how do you know?"</b></summary>

- Heartbeat stops → lease expires → sweeper moves the job back to the queue with an infra-failure retry count; the container it started is orphaned until the reaper removes containers labelled with dead job ids. Alert on "jobs with expired lease" and "orphan containers > 0". Check `dmesg` for the OOM kill, worker logs for the last `seq` persisted.
</details>

---

## 14. Architecture & design

<details><summary><b>ARC-1. "Why layered architecture, and what belongs where?"</b></summary>

- Controller: HTTP concerns only (validation, mapping, status codes). Service: use cases and transaction boundaries. Repository: persistence. Domain: entities/value objects with invariants (FlowGrid's inventory state machine lives in the domain, not the controller). Cross-cutting via filters/aspects. Why: testability per layer and one place per rule.
</details>

<details><summary><b>ARC-2. "Optimistic vs pessimistic locking — how did you choose?"</b></summary>

- Measured in FlowGrid M2: under a burst on one SKU, `@Version` produced N−1 retries per burst and jittery latency; `SELECT … FOR UPDATE` serialised cleanly with no retry loop. Chose pessimistic for hot rows with short transactions; optimistic where contention is rare (FlagForge config edits). LedgerX: pessimistic with ordered locks.
</details>

<details><summary><b>ARC-3. "Why a queue between the API and the workers?" (ForgeCI)</b></summary>

- Decouples burst intake from execution capacity; workers pull at their own pace; per-project limits; a dead worker's job survives (lease); horizontal scaling by adding workers. Redis lists + `BLMOVE` chosen for latency and simplicity; Streams/SQS as alternatives with acks/visibility timeouts — trade-offs documented in an ADR.
</details>

<details><summary><b>ARC-4. "SSE vs WebSockets vs polling — justify your choice."</b></summary>

- ForgeCI logs and FlagForge propagation are one-way server → client: SSE gives auto-reconnect, `Last-Event-ID` replay, plain HTTP through proxies, no extra infra. WebSockets when the client must send frequently. Polling when freshness of 15–30 s is fine and the endpoint is cacheable (FlowGrid low-stock view).
</details>

<details><summary><b>ARC-5. "Design a feature-flag evaluation path that stays fast under load."</b></summary>

- Evaluate locally in the SDK from an in-memory snapshot (polling with `ETag` + SSE invalidation) so most evaluations never hit the server; server-side eval reads a Redis snapshot per environment, invalidated on publish with single-flight rebuild; deterministic bucketing `hash(flagKey:userKey) mod 10000` so results are sticky without storage; measured p99.
</details>

<details><summary><b>ARC-6. "How would you split FlowGrid into services — and should you?"</b></summary>

- Not at this scale: one deployable, clear module boundaries (inventory, orders/fulfillment, catalog). If forced: split along the allocation boundary with an outbox for events, accept eventual consistency in the dashboard, keep reservations transactional inside one service. Say what you'd lose: cross-module transactions, simple joins.
</details>

<details><summary><b>ARC-7. "What is the transactional outbox and why did LedgerX need it?"</b></summary>

- Writing an event to a broker/webhook *after* commit can lose it (crash) or publish for a rolled-back transaction. Outbox: write the event row in the same transaction as the journal; a relay (`FOR UPDATE SKIP LOCKED`) publishes and marks it. At-least-once → consumers idempotent.
</details>

---

## 15. Project deep-dive questions

Rehearse the 10–15 minute deep-dive without notes (W8, W13, W19, W23) using [`16-interview-prep/project-deep-dive.md`](./16-interview-prep/project-deep-dive.md); each project's own `interview-questions.md` has the long list.

### FlowGrid

<details><summary><b>PRJ-1. "Tell me about FlowGrid — what problem, what design, what was hard?"</b></summary>

- Problem: multi-warehouse inventory and order fulfillment — quantities by state (on_hand / available / reserved / allocated / picked / shipped), idempotent orders, concurrent reservations, deterministic allocation across warehouses, pick/pack/ship workflow, returns and transfers.
- Design: Spring Boot 3 + Postgres + Redis + React; row-level locking for reservations; allocation as a scored `Comparator` with an id tie-break; cache-aside catalog; JWT with four roles.
- Hard: the one-unit concurrency test; partial fulfillment (ship what's ready); two-phase transfers (`in_transit`). Deployed to AWS in W8 with a measured k6 baseline.
- *Follow-up:* "How does allocation pick a warehouse?" → PRJ-2.
</details>

<details><summary><b>PRJ-2. "How does allocation decide which warehouse ships an order line?"</b></summary>

- Score candidates on availability, capacity, distance proxy (region/zone match), current workload, shipping priority; ties broken by warehouse id → deterministic, testable, explainable. Partial fulfillment when no single warehouse can cover a line (ADVANCED: split across warehouses). Allocation runs after reservation, inside its own transaction, and is re-runnable.
</details>

<details><summary><b>PRJ-3. "What did the k6 baseline show, and what would you not claim from it?"</b></summary>

- Quote only what PERFORMANCE.md records: setup (instance type, RDS class, data size), load shape, p50/p95/p99 for order creation and the contention mix (201 vs 409). Don't extrapolate to production hardware; say the single-box deploy is the bottleneck you'd remove first.
</details>

### LedgerX

<details><summary><b>PRJ-4. "Explain double-entry to me like I'm an engineer, and how the schema enforces it."</b></summary>

- Every journal transaction has ≥ 2 entries whose amounts sum to zero (debit/credit); system accounts (`external_clearing`, `fees`) absorb the other side of deposits/withdrawals; balances are derived. Schema: `account`, `journal_txn` (state machine), `ledger_entry` append-only (trigger + revoked privileges), CHECKs, materialized balance verified by reconciliation and an independent Python verifier.
</details>

<details><summary><b>PRJ-5. "Two concurrent $400 transfers from a $500 account — what happens, exactly?"</b></summary>

- Both hit `transfer()`; each locks accounts in ascending id order with `SELECT … FOR UPDATE`; the second waits; the first commits (balance 100); the second re-reads inside its transaction, fails `available >= amount`, rolls back → 409; `CHECK (balance >= 0)` would reject it anyway. Idempotency rows written in the same transactions. Test asserts exactly one success and the invariant suite passes.
</details>

<details><summary><b>PRJ-6. "What did failure injection teach you?"</b></summary>

- Crash after the idempotency row but before the journal → must be one transaction. Crash after commit but before the response → retry must return the stored response, not re-execute. Outbox relay crash → at-least-once, consumers idempotent. Reconciliation must be independent of the code it checks → the Python verifier.
</details>

### ForgeCI

<details><summary><b>PRJ-7. "Walk me through a push to a registered repo, end to end."</b></summary>

- GitHub → webhook (HMAC verified, delivery id unique) → build + jobs from `.forgeci.yml` → enqueue → worker `BLMOVE` + lease → container from the configured image, workspace volume, clone at SHA → steps via `exec`, exit codes → log chunks to Postgres + Redis pub/sub → API SSE to the UI → result, status to GitHub → container removed. Name where each failure is handled.
</details>

<details><summary><b>PRJ-8. "How do you make the queue reliable and recover orphaned jobs?"</b></summary>

- `BLMOVE` to a per-worker processing list (atomic hand-off), lease key with TTL refreshed by heartbeats, `LREM` on completion; sweeper re-queues jobs with expired leases as infra failures with bounded retries; job rows in Postgres are the record. Streams consumer groups as the alternative; SQS visibility timeout as the managed equivalent.
</details>

<details><summary><b>PRJ-9. "How do DAG pipelines schedule, and what happens when one job fails?"</b></summary>

- `needs:` edges → topological order (Kahn's), fan-out of independent jobs, fan-in waits for all parents; fail-fast cancels descendants and queued siblings (configurable). Cycle → config rejected at parse time. Direct line from the W14 DSA pattern to the W19 feature.
</details>

### FlagForge

<details><summary><b>PRJ-10. "How does percentage rollout stay consistent for a user?"</b></summary>

- `bucket = hash(flagKey + ":" + userKey) mod 10000`; on if `bucket < pct × 100`. Same input → same bucket, on server and in both SDKs; different flags hash differently so users aren't always in the same cohort. The Python simulator proves distribution within tolerance and stickiness over N users.
</details>

<details><summary><b>PRJ-11. "How do config changes reach running applications?"</b></summary>

- Publish creates an immutable version → Redis snapshot invalidated → pub/sub → SSE to connected SDKs, which refetch with `ETag`; polling as the fallback; rollback is just publishing an older version's copy. Stampede protection on rebuild; SDK serves stale on error.
</details>

<details><summary><b>PRJ-12. "What did designing an SDK teach you that building APIs didn't?"</b></summary>

- You own the client's failure modes: timeouts, defaults, offline mode, stale-if-error, no Spring dependency, semantic versioning, a builder that can't be misconfigured. Two implementations (Java, Python) against one contract test suite exposed ambiguities in the spec.
</details>

---

## 16. Python

Python is on the résumé as both the coding-interview language (Track A) and a tooling language used in every project (Track B). Drill file: [`17-resume-tech-defense/python.md`](./17-resume-tech-defense/python.md); templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md).

<details><summary><b>PY-1. "How much Python have you used, and for what?"</b></summary>

- Truthful past. Now: daily for every DSA/OA problem, and the tooling language in all four projects — FlowGrid's data generator + load harness, LedgerX's independent reconciliation verifier, ForgeCI's repo generator + webhook/load simulator + log analysis, FlagForge's rollout simulator + Python SDK + config linter — each with `pytest`, type hints and a README. Backends are Java; no Django/FastAPI production claims.
</details>

<details><summary><b>PY-2. "Complexity of list, dict and set operations?"</b></summary>

- List: index/append/pop-end O(1); insert/pop-front/`in` O(n); slice O(k). Dict/set: get/set/`in`/delete O(1) average, O(n) worst; iteration O(n). `deque` O(1) both ends; `heapq` push/pop O(log n), `heapify` O(n); `sorted` O(n log n). Always say "average" for hash-based ops.
</details>

<details><summary><b>PY-3. "What's wrong with <code>def f(x, acc=[])</code>?"</b></summary>

- Default evaluated once at definition; shared across calls. Use `None` + create inside; `field(default_factory=list)` in dataclasses. Same family: `[[0] * n] * m` aliases one row.
</details>

<details><summary><b>PY-4. "Explain the GIL. Did it affect your load harness?"</b></summary>

- One thread executes bytecode at a time in CPython. I/O-bound threads still overlap (the GIL is released on socket waits) — FlowGrid's harness with a `ThreadPoolExecutor` saturated the API fine and was cross-checked with k6. CPU-bound (rollout simulation) → `ProcessPoolExecutor`. 3.13 has an experimental free-threaded build.
</details>

<details><summary><b>PY-5. "What are generators and where did you use one?"</b></summary>

- `yield` produces a lazy iterator with constant memory. LedgerX verifier streams journal transactions through a server-side cursor in batches; ForgeCI log analysis streams large files line by line.
</details>

<details><summary><b>PY-6. "Dataclasses vs Java records?"</b></summary>

- `@dataclass(frozen=True, slots=True)` generates `__init__/__repr__/__eq__/__hash__`; validation in `__post_init__` (≈ compact constructor); `frozen` makes it hashable like a record. Differences: no runtime type enforcement; `field(default_factory=…)` for mutable defaults.
</details>

<details><summary><b>PY-7. "When would you pick Python vs Java?"</b></summary>

- Python: interviews, scripts, verification/simulation/analysis tooling, glue, data. Java: long-lived services, type safety at scale, real threads and JVM performance, Spring ecosystem. In my stack: Java services, Python tooling — tested like production code.
</details>

<details><summary><b>PY-8. "Explain the LedgerX independent verifier — why does it exist and what can't it catch?"</b></summary>

- Reads Postgres directly with `psycopg` + `Decimal`, shares no code with the Java service; proves every journal sums to zero and `balance == SUM(entries)`; exits non-zero with a report; pytest against a DB with a planted unbalanced transaction; runs in CI after the Java suite and inside crash/retry exercises. Can't catch: a shared misunderstanding of the accounting model; anything not expressible as a row-level invariant; it's point-in-time.
</details>

<details><summary><b>PY-9. "How does the FlowGrid load harness work and how do you keep its numbers honest?"</b></summary>

- Generator seeds realistic warehouses/SKUs/orders (via API or SQL); harness fires concurrent order creations (`concurrent.futures`, fresh `Idempotency-Key` per request), records latency and 201/409 per request, prints percentiles; run parameters and environment recorded in PERFORMANCE.md; warm-up excluded; cross-checked with k6. Say what a single-client harness can't distinguish (client scheduling vs server queueing).
</details>

<details><summary><b>PY-10. "What do the ForgeCI simulators do?"</b></summary>

- Repo generator creates Git repos with `.forgeci.yml` variants (passing, failing, slow, timeout, bad config) for the failure suite; webhook simulator signs payloads with HMAC-SHA256 over the exact bytes sent and fires them at a rate, measuring queue wait and completion; log analysis parses persisted results into a failure-taxonomy report. Bug you hit: signing a re-serialised body → 401 → sign the raw bytes.
</details>

<details><summary><b>PY-11. "Why a Python SDK for FlagForge, and how is it kept in sync with the Java one?"</b></summary>

- Second implementation of the evaluation contract (bucketing, rule priority, defaults, stale-if-error, polling + `ETag`, offline) used for cross-SDK contract tests against recorded server evaluations; disagreements mean the spec is ambiguous. Deliberately minimal (no SSE); the config linter reuses its rule parser to flag overlapping/unreachable rules.
</details>

<details><summary><b>PY-12. "What's the output?" (late-binding closure / aliasing / <code>is</code> snippet)</b></summary>

- `[lambda: i for i in range(3)]` → all return 2 (fix `i=i`); `b = a; b.append(1)` mutates `a`; `a is b` for equal ints may be True or False (interning) — never rely on it. Narrate the object graph, not the syntax.
</details>

---

## 17. Behavioral-technical hybrids

Story bank and STAR shaping: [`16-interview-prep/behavioral.md`](./16-interview-prep/behavioral.md). Use true stories from past roles when you remember them clearly; otherwise the roadmap projects — labelled as personal projects.

<details><summary><b>BEH-1. "Tell me about a hard bug you fixed."</b></summary>

- STAR with mechanism: symptom → hypothesis → tool (thread dump, `EXPLAIN`, logs by `requestId`, the failing test) → root cause → fix → regression test → what you changed in process. Candidates: FlowGrid's double reservation (concurrency test caught it), ForgeCI's orphaned jobs (lease sweep), LedgerX drift (self-invocation bypassing `@Transactional`).
</details>

<details><summary><b>BEH-2. "Tell me about a decision you'd make differently now."</b></summary>

- ForgeCI: starting with Redis lists instead of Streams/SQS meant building lease and sweep logic by hand — good learning, more code to own. Or FlowGrid: hand-rolled fetch hooks in React where a query library would have removed a class of race bugs. Say what you learned and when the original choice was still right.
</details>

<details><summary><b>BEH-3. "Tell me about a time you disagreed with a technical decision."</b></summary>

- True past story if you have one; frame the mechanism (what each option cost), how you argued with data, how you committed once decided. If using a project: the `@Version` vs `FOR UPDATE` measurement — "I had an opinion; I measured instead."
</details>

<details><summary><b>BEH-4. "How do you approach a codebase you've never seen?"</b></summary>

- Run it and its tests first; read the entry points and the tests for the area you must change; trace one request end to end with the debugger; write a failing test before changing anything; small PR. Practised weekly since W13 on the [buggy-library drills](./21-debugging-code-reading/README.md) and in OA simulations.
</details>

<details><summary><b>BEH-5. "How do you handle being stuck?"</b></summary>

- Timebox; reproduce smaller; read the actual error and the docs for the exact version; form a hypothesis and test it; then ask with a precise question (what I tried, what I expected, what happened). Example: Testcontainers "no Docker environment" in CI — resolved by reading the runner docs rather than guessing.
</details>

<details><summary><b>BEH-6. "What would you do in your first 30 days here?"</b></summary>

- Ship something small in week one through the real pipeline; learn the on-call/incident path; read the last five postmortems; pair with whoever owns the scariest service; write down what confused me so the docs improve.
</details>

---

## 18. Tracking table

Log every recorded drill here (or in [`trackers/technology-tracker.md`](./trackers/technology-tracker.md)). Target ≥ 3 on three consecutive attempts per id before you stop drilling it. Add rows as you go; keep the last three scores per id.

| id | last drilled | score (1–4) | note (gap found / follow-up missed) |
|---|---|---|---|
| EXP-1 | | | |
| EXP-2 | | | |
| EXP-3 | | | |
| EXP-4 | | | |
| JAV-1 | | | |
| JAV-3 | | | |
| JAV-5 | | | |
| SPR-1 | | | |
| SPR-2 | | | |
| SPR-4 | | | |
| SPR-5 | | | |
| SPR-6 | | | |
| SPR-7 | | | |
| RST-1 | | | |
| RST-3 | | | |
| DB-1 | | | |
| DB-2 | | | |
| DB-3 | | | |
| DB-8 | | | |
| DB-9 | | | |
| FE-1 | | | |
| FE-5 | | | |
| TST-2 | | | |
| TST-3 | | | |
| GIT-1 | | | |
| GIT-5 | | | |
| DOK-1 | | | |
| DOK-2 | | | |
| AWS-1 | | | |
| AWS-3 | | | |
| CI-1 | | | |
| CI-3 | | | |
| DBG-1 | | | |
| DBG-2 | | | |
| ARC-2 | | | |
| ARC-3 | | | |
| PRJ-1 | | | |
| PRJ-5 | | | |
| PRJ-7 | | | |
| PRJ-10 | | | |
| PY-1 | | | |
| PY-4 | | | |
| PY-8 | | | |
| BEH-1 | | | |
| BEH-2 | | | |

Related: [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md) · [`17-resume-tech-defense/README.md`](./17-resume-tech-defense/README.md) · [`16-interview-prep/project-deep-dive.md`](./16-interview-prep/project-deep-dive.md) · [`INTERVIEW_CHECKLIST.md`](./INTERVIEW_CHECKLIST.md) · [`PROJECTS.md`](./PROJECTS.md)
