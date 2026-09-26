# Checkpoint 8 — FlowGrid v1.0 (Strong Résumé Version)

> **Gate question:** Is FlowGrid's Strong Résumé Version deployed, tested and explainable?
> Do I solve Easies reliably and first Mediums?

| | |
|---|---|
| **When** | Sunday of Week 8 (Part A + C on Saturday, Parts B/D/E + scoring on Sunday) |
| **Time** | ≈ 4 h: A 65 min · B 50 min · C 60 min · D 40 min · E 25 min · scoring 10 min |
| **Covers** | Weeks 5–8: transactions/isolation/locking, idempotency keys, Java memory model + threads basics, Mockito/slices/Testcontainers, Dockerfiles, Git rebase/PR workflow; schema design, indexes/`EXPLAIN`, CTEs/window functions, Redis cache-aside, state machines; JavaScript → TypeScript → React; AWS IAM/EC2/RDS/S3/CloudWatch; CI/CD pipeline; k6; structured logging; FlowGrid Python tools |
| **DSA patterns in scope** | Everything through Recursion: Stack & Queue, Binary Search, Linked Lists, Recursion ([`../03-dsa/06-stack-queue.md`](../03-dsa/06-stack-queue.md) … [`09-recursion.md`](../03-dsa/09-recursion.md)) plus CP-4 patterns |
| **Project under review** | [FlowGrid](../18-projects/flowgrid/README.md) **Strong Résumé Version, tagged `v1.0`** ([milestones.md](../18-projects/flowgrid/milestones.md) M1–M5) |
| **Rules** | [Checkpoint rules](./README.md#rules) |

This is the first checkpoint that decides whether you enter the **Early application stage** in
[`../JOB_READINESS.md`](../JOB_READINESS.md). Passing here means FlowGrid is real, running on AWS
and you can talk about it.

---

## Part A — Timed coding (Track A · Python · 45 min + 20 min) + Java rep (15 min)

### A1 — 3 Easy in 45 min

**Selection rules:** three `Not Started` Easies, one each from *Stack/Queue*, *Binary Search*
and *Linked Lists* (NeetCode 150 or the LeetCode topic lists). Swap anything you recognise.
Python 3.12, no cheatsheet, assertions or `pytest` for the examples.

**Clock:** 45 min total (≤ 15 each). Stop at the bell.

### A2 — 1 Medium attempt in 20 min

One `Not Started` **Medium** from *Sliding Window*, *Two Pointers* or *Stack (monotonic)*. The
goal is not necessarily to finish: the goal is to reach a correct approach with complexity and
have working code for the main case. Record how far you got at the bell (approach only / code
compiles / examples pass / edge cases pass).

### A3 — Java rep, 15 min

One already-solved Python problem from *Stack & Queue* or *Linked Lists* re-implemented in Java:
`ArrayDeque` as a stack/queue, or a hand-written `ListNode` class with pointer manipulation.
`main` or JUnit test on the examples.

**Scoring**

| A1 (feeds DSA, Python) | Score |
|---|---:|
| 3/3 correct, each ≤ 15 min, complexity stated, idiomatic | 4 |
| 3/3 correct within 45 min; or 2/3 with the third's approach right | 3 |
| 2/3 correct | 2 |
| 1/3 | 1 |
| 0 | 0 |

| A2 (feeds DSA) | Bonus |
|---|---:|
| Examples pass within 20 min | +1 to DSA (cap 4) |
| Correct approach and complexity, code partial | +0.5 |
| No viable approach | note in log — this is the Week 9–12 target |

| A3 (feeds Java) | Points |
|---|---:|
| Compiles, passes, ≤ 15 min, idiomatic collections use | 2 |
| Passes but over time / needed Javadoc for basic API | 1 |
| Fails | 0 |

---

## Part B — Knowledge questions (50 min · 28 questions)

Write first, then open. 1 point each, 0.5 partial. Max 28.

### Python for interviews (Q1–Q5)

**Q1.** `sorted(items, key=lambda x: (-x[1], x[0]))` — what order does this produce, what is the
complexity, and is Python's sort stable?
<details><summary>Answer</summary>
Descending by the second element, ties broken ascending by the first. O(n log n) (Timsort).
Stable — equal keys keep their original relative order, which is why chaining sorts works.
</details>

**Q2.** Shallow vs deep copy: `b = a[:]`, `b = list(a)`, `b = a.copy()`, `b = copy.deepcopy(a)`.
Which are shallow, and when does shallow bite you?
<details><summary>Answer</summary>
The first three are shallow (new outer list, same inner objects). Bites you when `a` holds mutable
elements (lists, dicts, objects): mutating `b[0].append(...)` changes `a[0]`. `deepcopy` copies
recursively.
</details>

**Q3.** Implement a stack and a queue in Python with O(1) operations. Name the class for each.
<details><summary>Answer</summary>
Stack: plain `list` with `append`/`pop`. Queue: `collections.deque` with `append`/`popleft`.
(A `list` used as a queue makes `pop(0)` O(n).)
</details>

**Q4.** In a recursive helper you write `def dfs(node, path=[])` and pass `path` down. Two bugs
lurking?
<details><summary>Answer</summary>
Mutable default (shared across calls); and appending to `path` without popping after recursion
(no backtracking), so sibling branches see each other's elements. Use `path=None` guard and
`path.append(x); dfs(...); path.pop()`, or pass `path + [x]` (O(n) copy per call).
</details>

**Q5.** What does `bisect.bisect_left(a, x)` return, and how do you use it to implement
"first index with `a[i] >= x`" and "count of elements `< x`"?
<details><summary>Answer</summary>
The insertion index for `x` in sorted `a` keeping order, placing `x` before equal elements — which
is exactly the first index with `a[i] >= x`, and also the count of elements strictly less than `x`.
`bisect_right` gives the count `<= x`.
</details>

### Java, memory and threads (Q6–Q10)

**Q6.** Two threads increment a shared `int counter` 10 000 times each without synchronisation.
Why is the result often < 20 000, and name three fixes with their trade-offs.
<details><summary>Answer</summary>
`counter++` is read–modify–write (three steps); interleavings lose updates. Fixes:
`synchronized` (simple, coarse), `AtomicInteger` (lock-free CAS, best here), `ReentrantLock`
(explicit, supports tryLock/fairness). `volatile` alone does *not* fix it — it guarantees
visibility, not atomicity.
</details>

**Q7.** What does the `volatile` keyword guarantee, and give one legitimate use.
<details><summary>Answer</summary>
Visibility and ordering (happens-before) of writes to that field across threads; no caching in
registers/CPU cache without publishing. Use: a `volatile boolean running` shutdown flag checked by
a worker loop.
</details>

**Q8.** Mockito: difference between `@Mock` + `@InjectMocks` in a plain unit test and `@MockBean`
in a `@WebMvcTest`.
<details><summary>Answer</summary>
`@Mock/@InjectMocks` (MockitoExtension) build the object graph without Spring — fast, for
services. `@MockBean` replaces a bean *inside the Spring test context* so the controller under
test gets the mock via DI; needed in slice tests where Spring wires the controller.
</details>

**Q9.** Why does the N-threads-one-unit reservation test need Testcontainers rather than H2?
<details><summary>Answer</summary>
The test proves a *database* behaviour: row locks (`SELECT … FOR UPDATE`) and isolation semantics
are engine-specific. H2 does not reproduce Postgres locking/MVCC exactly, so a green H2 test proves
nothing about production.
</details>

**Q10.** Explain a multi-stage Dockerfile for a Spring Boot app and why the final image is
smaller.
<details><summary>Answer</summary>
Stage 1 (`maven`/JDK image) builds the jar; stage 2 (`eclipse-temurin:21-jre` or similar) copies
only the jar. Build tools, sources and the `.m2` cache stay in the discarded stage → smaller
attack surface and image. Add layer ordering (copy `pom.xml` + resolve deps before sources) for
cache hits.
</details>

### SQL and Postgres (Q11–Q16)

**Q11.** Two transactions each `SELECT quantity_available FROM inventory_level WHERE id = 1`,
see 1, then both `UPDATE … SET quantity_available = 0`. Under READ COMMITTED, what happens and
what is the fix?
<details><summary>Answer</summary>
Both see 1, both update — one unit sold twice (lost update / check-then-act race). Fixes:
`SELECT … FOR UPDATE` (pessimistic row lock, second waits then re-reads 0), an atomic conditional
`UPDATE … SET qty = qty - 1 WHERE id = 1 AND qty >= 1` checking the row count, or optimistic
locking with a version column (`@Version`) and retry.
</details>

**Q12.** What does `EXPLAIN (ANALYZE, BUFFERS)` show that `EXPLAIN` alone does not?
<details><summary>Answer</summary>
Actual run: real row counts vs estimates, actual time per node, loops, and buffer hits/reads
(cache vs disk). It executes the query — wrap writes in a transaction and roll back.
</details>

**Q13.** Write a query using a window function: for each warehouse, the top 3 SKUs by on-hand
quantity.
<details><summary>Answer</summary>

```sql
SELECT * FROM (
  SELECT warehouse_id, sku_id, quantity_on_hand,
         ROW_NUMBER() OVER (PARTITION BY warehouse_id ORDER BY quantity_on_hand DESC, sku_id) AS rn
  FROM inventory_level
) t
WHERE rn <= 3;
```
</details>

**Q14.** What is a deadlock, how does Postgres handle it, and how does lock ordering prevent it?
<details><summary>Answer</summary>
Two transactions each hold a lock the other needs. Postgres detects the cycle after
`deadlock_timeout` and aborts one (error 40P01). If every transaction acquires locks in the same
global order (e.g. by ascending id), no cycle can form.
</details>

**Q15.** Redis cache-aside for the SKU catalog: describe read path, write path, and what
happens if Redis is down.
<details><summary>Answer</summary>
Read: `GET sku:{id}` → hit returns; miss → DB → `SET` with TTL. Write: update DB, then `DEL` the
key (invalidate, don't update-in-cache; avoids stale writes racing). Redis down: catch the
connection error, log, serve from DB (degrade), maybe a circuit breaker — never fail the request
because the cache is unavailable.
</details>

**Q16.** Composite index `(warehouse_id, sku_id)`: which of these can use it — `WHERE warehouse_id = 1`,
`WHERE sku_id = 5`, `WHERE warehouse_id = 1 AND sku_id = 5`?
<details><summary>Answer</summary>
First and third (leftmost-prefix rule). `WHERE sku_id = 5` alone cannot use it efficiently — it
needs its own index (or the column order swapped, depending on the dominant query).
</details>

### Backend, idempotency and state (Q17–Q20)

**Q17.** Design an `Idempotency-Key` mechanism for `POST /orders`: what do you store, what do you
return on replay, and what if the same key arrives with a different body?
<details><summary>Answer</summary>
Store key (+ user scope), request-body hash, response status + body, TTL. On replay with the
same hash → return the stored response (same status). Same key, different hash → 422/409 with a
ProblemDetail. Insert the key row *first* (unique constraint) inside the transaction so concurrent
duplicates collide at the DB, not in memory.
</details>

**Q18.** Which `@Transactional` mistake makes a self-invoked method run without a transaction,
and how do you prove it?
<details><summary>Answer</summary>
Calling `this.otherMethod()` bypasses the Spring proxy, so `@Transactional` on `otherMethod` is
ignored. Prove: `TransactionSynchronizationManager.isActualTransactionActive()` logging, or a test
that throws inside the inner method and observes the write was *not* rolled back.
</details>

**Q19.** Reservation state machine: list the states and one illegal transition, and where the
guard lives.
<details><summary>Answer</summary>
e.g. `PENDING → CONFIRMED → ALLOCATED → PICKED → SHIPPED`, with `CANCELLED` from
PENDING/CONFIRMED only. Illegal: `SHIPPED → CANCELLED`. The guard lives in the domain entity/service
(a `transitionTo(next)` that throws `IllegalStateTransitionException`, mapped to 409), never only
in the UI.
</details>

**Q20.** Why is the allocation algorithm deterministic, and why does that matter for tests and
for ops?
<details><summary>Answer</summary>
Same inputs → same warehouse choice (scores plus a total tie-break by id). Tests can assert exact
outcomes; ops can explain "why warehouse B"; replays after failure produce the same decision.
</details>

### Frontend (Q21–Q24)

**Q21.** In JavaScript, what does `await` do inside an `async` function, and what is wrong with
`items.forEach(async x => await save(x))`?
<details><summary>Answer</summary>
Pauses the function until the promise settles, yielding to the event loop. `forEach` ignores
returned promises — the saves run concurrently and the caller cannot await completion. Use
`for … of` with `await` (sequential) or `await Promise.all(items.map(save))` (parallel).
</details>

**Q22.** Why does a React `useEffect` that fetches data need a cleanup / cancellation, and how do
you do it?
<details><summary>Answer</summary>
Component may unmount or deps may change before the fetch resolves → state update on a stale
render (out-of-order results). Use an `AbortController` in the effect and `abort()` in the cleanup,
or an `ignore` flag set in cleanup.
</details>

**Q23.** TypeScript: what is the difference between `interface`/`type` unions and `any`; why
avoid `any` for API responses?
<details><summary>Answer</summary>
Types/interfaces describe shape; discriminated unions model variants safely. `any` disables
checking — a renamed backend field silently becomes `undefined` at runtime. Type the API client
from the OpenAPI schema (generated or hand-written) and narrow at the boundary.
</details>

**Q24.** Where does the JWT live in your dashboard, and what are the trade-offs of
`localStorage` vs an `HttpOnly` cookie?
<details><summary>Answer</summary>
`localStorage`: simple, readable by JS → XSS steals it. `HttpOnly` cookie: JS cannot read it, but
needs CSRF protection (SameSite, tokens) and CORS credentials config. Either way: short-lived
access token, refresh flow, no token in URLs.
</details>

### AWS, CI/CD, ops (Q25–Q28)

**Q25.** IAM: what is the difference between a user, a role and a policy; why does the EC2 host
get a role rather than access keys?
<details><summary>Answer</summary>
User = long-term identity with credentials; role = assumable identity with temporary credentials;
policy = JSON permissions attached to either. An instance profile role gives the app temporary,
auto-rotated credentials — no keys on disk to leak.
</details>

**Q26.** Your security group for RDS: what inbound rule, and why not `0.0.0.0/0`?
<details><summary>Answer</summary>
Allow 5432 only from the app's security group (source = sg-id), no public access. `0.0.0.0/0`
exposes the DB to brute force and scanners; the DB should be unreachable from the internet.
</details>

**Q27.** In your GitHub Actions pipeline, what runs on PR vs on push to `main`, and how are
secrets handled?
<details><summary>Answer</summary>
PR: `mvn verify` (tests, Testcontainers with services or Docker). `main`: build image → push to
GHCR → deploy (SSH/SSM to EC2, `docker compose pull && up -d`). Secrets in repo/environment
secrets, never echoed; least-privilege deploy key; `GITHUB_TOKEN` for GHCR.
</details>

**Q28.** Your k6 baseline: what did you measure, at what concurrency, and what would make the
number meaningless?
<details><summary>Answer</summary>
(Your own numbers.) e.g. order-creation p50/p95 latency and throughput at N VUs for M minutes
against the deployed stack, with warm-up, fixed dataset, machine specs recorded. Meaningless if:
run on a laptop against localhost, no warm-up, mixed with other load, or only p50 quoted.
Methodology → [`../18-projects/templates/benchmark-report.md`](../18-projects/templates/benchmark-report.md).
</details>

**Part B scoring:** ≥ 23/28 → 4 · 19–22 → 3 · 14–18 → 2 · 9–13 → 1 · < 9 → 0. Sections feed
areas: Q1–5 Python; Q6–10 Java; Q11–16 SQL; Q17–20 Backend; Q21–24 Frontend; Q25–28 CS/ops.

---

## Part C — Practical task (Track B · 60 min)

### C1 — 8 SQL queries against the practice schema (35 min)

Load [`../04-sql-databases/practice-schema.sql`](../04-sql-databases/practice-schema.sql) into
a fresh database (`tixhub`). Write and run each query; paste result row counts in the log.

1. Revenue per event (sum of `quantity * unit_price` over `order_items`) for `PAID` orders only,
   highest first.
2. Customers who have never placed an order (two ways: `LEFT JOIN … IS NULL` and `NOT EXISTS`).
3. Orders that are `PAID` but have **no** `SUCCEEDED` payment (the schema plants one).
4. Orders with **more than one** `SUCCEEDED` payment (the double charge).
5. For each venue, the number of events and the average `base_price`, including venues with no
   events.
6. Each employee with their manager's name (self join), and the CEO shown with `NULL` manager.
7. Using a recursive CTE, everyone who reports (directly or indirectly) to the CTO.
8. Per customer, their orders ranked by `created_at` (window function), returning only each
   customer's most recent order.

Bonus (if time remains): find the two customers whose e-mails collide case-insensitively and
propose the constraint that prevents it.

### C2 — Read an EXPLAIN plan from FlowGrid (25 min)

In your FlowGrid database (seeded by your Python generator in `tools/`, or by the M4 fixtures):

1. Run `EXPLAIN (ANALYZE, BUFFERS)` on the query behind your inventory list endpoint with a
   warehouse filter and sort (copy the SQL Hibernate logs, or write the equivalent).
2. Write down: the plan's top node, whether the filter uses an index scan or a seq scan, the
   estimated vs actual row counts, and buffers read.
3. If it is a seq scan on a filterable column, create the index, re-run, and record the
   before/after execution time. If it is already indexed, explain why the planner chose that
   plan and what would make it flip to a seq scan.
4. Run the same on `page_views` in the practice DB: `SELECT count(*) FROM page_views WHERE event_id = 9`
   before and after `CREATE INDEX ON page_views(event_id)`.

**Scoring (feeds SQL, Debugging)**

| Criteria | Points |
|---|---:|
| C1: queries 1–4 correct | 2 |
| C1: queries 5–8 correct | 2 |
| C1 done within 35 min | 0.5 |
| C2: plan read correctly (node, scan type, estimate vs actual, buffers) | 1 |
| C2: before/after index measured and explained | 1 |

6.5/6.5 → 4 · 5–6 → 3 · 3.5–4.5 → 2 · 2–3 → 1 · < 2 → 0.

---

## Part D — Explain out loud (40 min, recorded)

**FlowGrid deep-dive rehearsal (15 min, no notes).** Follow the structure in
[`../16-interview-prep/project-deep-dive.md`](../16-interview-prep/project-deep-dive.md): problem →
architecture → hardest part → a decision you would change → numbers you measured. Then answer
five probes (pick at random from [`../18-projects/flowgrid/interview-questions.md`](../18-projects/flowgrid/interview-questions.md)),
for example:

1. "How exactly did you prove only one reservation succeeds under concurrency? Show me the test
   in your head."
2. "What does your Idempotency-Key filter store, and what happens if the DB commits but the
   response is never stored?"
3. "Walk me through the allocation scoring. What happens on a tie?"
4. "What happens to the dashboard when Redis is down? To order creation?"
5. "You measured X req/s at Y VUs — what was the bottleneck and how do you know?"

**Résumé-defense drill (10 min):** three questions from
[`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md) on Java, SQL and Git
answered with examples from FlowGrid.

**Track A explain (5 min):** narrate your A2 Medium approach as an interviewer would hear it —
including where you got stuck and what hint you would have wanted.

Score each block 0–4 with [`../16-interview-prep/mock-interviews.md`](../16-interview-prep/mock-interviews.md).
The deep dive must be ≥ 3 to count as a passed "deep-dive rehearsal" in
[`../trackers/interview-tracker.md`](../trackers/interview-tracker.md).

---

## Part E — Project review: FlowGrid vs Strong Résumé Version

Check [`../18-projects/flowgrid/README.md`](../18-projects/flowgrid/README.md) scope tiers and
every acceptance criterion in [`../18-projects/flowgrid/milestones.md`](../18-projects/flowgrid/milestones.md).

**M2 — idempotent orders, concurrent reservations**
- [ ] `Idempotency-Key` stored with request hash + response, TTL; replay returns stored response; conflict on different body
- [ ] Reservation state machine with illegal transitions rejected (409)
- [ ] Cancellation releases reservations
- [ ] Concurrency test: N threads, 1 unit → exactly one success — on Testcontainers Postgres, in CI
- [ ] Documented comparison: `SELECT … FOR UPDATE` vs `@Version` (what you measured/observed)

**M3 — allocation, fulfillment, Redis**
- [ ] Deterministic multi-warehouse allocation with documented score and tie-break
- [ ] Pick list → packing → shipment state machine; partial fulfillment works
- [ ] Redis cache-aside for catalog with invalidation; low-stock cache
- [ ] Redis-down test: app degrades to DB, no 5xx

**M4 — dashboard, returns, transfers**
- [ ] React + TS dashboard: inventory by warehouse, orders, pick/pack, low-stock; search/filter/sort/pagination
- [ ] Client-side auth (login, token handling, role-aware UI)
- [ ] Returns (restock / quarantine); two-phase stock transfers (`IN_TRANSIT`); scheduled low-stock alerts
- [ ] At least a few component tests ([`../09-testing/frontend-testing.md`](../09-testing/frontend-testing.md))

**M5 — deploy, document, measure**
- [ ] Running on AWS: EC2 + Compose, RDS Postgres, Redis, S3 export, IAM least privilege, CloudWatch logs + one alarm
- [ ] CI/CD: PR → `mvn verify`; `main` → image → GHCR → deploy
- [ ] k6 baseline recorded with methodology (environment, dataset, VUs, duration, p50/p95, throughput)
- [ ] Docs set: README, ARCHITECTURE, API, DATABASE (ERD), TESTING, DEPLOYMENT, SECURITY, DESIGN_DECISIONS (ADRs), PERFORMANCE
- [ ] Failure-engineering exercises from [`../18-projects/flowgrid/failure-engineering.md`](../18-projects/flowgrid/failure-engineering.md) done and written up
- [ ] `v1.0` tag exists; GitHub milestones/issues closed

**Python component (`tools/`)**
- [ ] Synthetic inventory & order generator with README, type hints, `pytest` tests
- [ ] Load/simulation harness driving concurrent order creation, reporting latency + reservation contention; referenced from PERFORMANCE.md / TESTING.md

**Hours:** logged ≈ 125–150 h in [`../trackers/project-tracker.md`](../trackers/project-tracker.md).

**Scoring (feeds Projects):** all M1–M5 boxes + Python component → 4 · ≤ 2 boxes missing, none
in M2/M5 deploy → 3 · deployed but ≤ 4 boxes missing → 2 · not deployed or concurrency test
missing → 1 · M3 not finished → 0.

---

## Scoring table

| Area | Evidence | Critical? | PASS threshold | My score |
|---|---|:---:|:---:|:---:|
| Python (coding) | A1 idiom/speed, B Q1–Q5 | yes | 3 | |
| Java (engineering) | A3 Java rep, B Q6–Q10, code quality seen in E | yes | 3 | |
| DSA | A1 (+ A2 bonus) | yes | 3 | |
| SQL | B Q11–Q16, C1, C2 | yes | 3 | |
| Backend | B Q17–Q20, E (M2/M3) | yes | 3 | |
| Frontend | B Q21–Q24, E (M4) | no | 2 | |
| Git | PR history in E, branch hygiene | no | 3 | |
| Debugging | C2 plan reading, failure-engineering write-ups in E | no | 2 | |
| CS fundamentals | B Q6–Q7, Q25–Q28 | no | 2 | |
| Projects | E | yes | 3 | |
| Interview readiness | D (deep dive weighted double) | yes | 3 | |

## Decision

| Condition | Decision |
|---|---|
| All critical ≥ threshold, ≤ 1 non-critical below | **PASS** → enter *Early application stage* ([`../JOB_READINESS.md`](../JOB_READINESS.md)) |
| All critical met, ≥ 2 non-critical below | **PASS WITH REMEDIATION** → still enter the stage; fix in W9–10 |
| Any critical below | **FAIL** → do not apply yet; remediate in W9–10 |
| FlowGrid not deployed / no `v1.0` | **FAIL**; finish M5 in Week 9 review + Sat blocks; FlowGrid Advanced Version dropped |
| Phase > 1 week behind | Drop FlowGrid Advanced Version (ROADMAP §13 rule 4) |

---

## If you fail — remediation (Weeks 9–10 review blocks, ≈ 8 h)

| Area | Do this |
|---|---|
| **Python (coding)** | Timed re-solves: 8 Easies in 2 h with a 15-min clock, compare each against a NeetCode Python solution and note one idiom per problem (Week 9 Sat). Re-do [`../19-python/exercises.md`](../19-python/exercises.md) toolkit set. |
| **Java (engineering)** | Two Java reps/week in W9–10 (stack/queue/linked-list problems). Re-read [`../01-java/06-memory-jvm.md`](../01-java/06-memory-jvm.md), [`../01-java/07-concurrency.md`](../01-java/07-concurrency.md) basics section; write the counter race + three fixes as a JUnit test (1 h). |
| **DSA** | Week 9 new problems 8 → 5; add Day-30 reviews for all `Needs Review` in Stack/Binary Search/Linked Lists (3 h). Medium attempt every Sat with 25-min clock. |
| **SQL** | Finish all of [`../04-sql-databases/sql-practice.md`](../04-sql-databases/sql-practice.md); re-read [`../04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md); `EXPLAIN` every LedgerX query in W9 (3 h). |
| **Backend** | Re-implement the idempotency filter from a blank file in a scratch project (1.5 h); re-read [`../05-spring-boot/07-transactions.md`](../05-spring-boot/07-transactions.md). |
| **Frontend** | [`../08-react/02-hooks.md`](../08-react/02-hooks.md) + [`../07-javascript-typescript/02-async-javascript.md`](../07-javascript-typescript/02-async-javascript.md); rebuild one dashboard table with fetch + abort + pagination from scratch (2 h). |
| **Git** | Adopt [`../02-git/workflows.md`](../02-git/workflows.md) PR template + squash strategy for LedgerX from day one. |
| **Debugging** | Repeat two FlowGrid failure exercises with the debugger attached and write root cause in 5 lines each (1.5 h). |
| **CS fundamentals** | [`../14-cs-fundamentals/memory-and-architecture.md`](../14-cs-fundamentals/memory-and-architecture.md) + [`../12-aws/iam.md`](../12-aws/iam.md) re-read; 10 flashcards. |
| **Projects** | Finish missing M5 items before any LedgerX Advanced work; Advanced Version dropped if > 1 week behind. |
| **Interview** | Re-record the deep dive after writing the one-page outline from [`../16-interview-prep/project-deep-dive.md`](../16-interview-prep/project-deep-dive.md); repeat until ≥ 3 (Week 9 + 10 Sat). |

Second consecutive failure in an area → one-week feature freeze in that area (ROADMAP §9).

---

## Results log

```
CP-8 — date: ____________   total time: ____ h

PART A1 easies: 1 ______ __min __  2 ______ __min __  3 ______ __min __   score __/4
PART A2 medium: ________ reached: approach / compiles / examples / edge   bonus __
PART A3 java rep: ________ __min  points __/2
PART B  Py __/5 Java __/5 SQL __/6 Backend __/4 Frontend __/4 AWS/ops __/4  total __/28  score __/4
PART C  C1 __/4.5 (time __ min)  C2 __/2   seq→index before/after: ____ ms → ____ ms   score __/4
PART D  deep dive __/4  probes avg __  résumé drill __/4  track A explain __/4   score __/4
PART E  M2 __/5 M3 __/4 M4 __/4 M5 __/6 Python __/2  hours __  v1.0 tag? __  URL: __________  score __/4

AREA SCORES  Python __ Java __ DSA __ SQL __ Backend __ Frontend __ Git __ Debug __ CS __ Projects __ Interview __
DECISION     PASS / PASS WITH REMEDIATION / FAIL       Early application stage entered? __
REMEDIATION  areas: ____________  hours W9: __  W10: __
NOTES
```

Copy to [`./README.md`](./README.md#summary-log), [`../trackers/weekly-progress.md`](../trackers/weekly-progress.md)
and the readiness scorecard in [`../JOB_READINESS.md`](../JOB_READINESS.md).
