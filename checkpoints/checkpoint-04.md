# Checkpoint 4 — End of FlowGrid M1

> **Gate question:** Can I write tested Java, basic SQL and a secured Spring Boot CRUD API
> (FlowGrid M1) without a tutorial?

| | |
|---|---|
| **When** | Sunday of Week 4 (Part A + C on Saturday afternoon, Parts B/D/E + scoring on Sunday) |
| **Time** | ≈ 3.5 h: A 40 min · B 45 min · C 60 min · D 30 min · E 20 min · scoring 10 min |
| **Covers** | Weeks 1–4: Java syntax → OOP → collections → modern Java; Git core + branches; JUnit 5 + Maven; SQL basics + joins/aggregation; HTTP/REST; Spring Boot intro → JPA, validation, ProblemDetail, pagination, Security + JWT; Docker Compose for Postgres; basic GitHub Actions |
| **DSA patterns in scope** | Big-O, Arrays & Strings, Hashing, Two Pointers, Prefix Sums, Sliding Window (`../03-dsa/00-big-o.md` … `05-sliding-window.md`) |
| **Project under review** | [FlowGrid](../18-projects/flowgrid/README.md) M1 — **MVP core** (see [milestones.md](../18-projects/flowgrid/milestones.md)) |
| **Rules** | [Checkpoint rules](./README.md#rules): closed book except language/framework docs, timed, honest |

This is the first gate. Thresholds are deliberately modest: the question is whether the
foundation exists, not whether it is polished.

---

## Part A — Timed coding (40 min · 2 Easy)

**Selection rules**

1. Open [`../trackers/dsa-tracker.md`](../trackers/dsa-tracker.md). Pick two problems tagged
   **Easy** from the NeetCode/LeetCode lists for *Arrays & Hashing*, *Two Pointers* or
   *Sliding Window* that have status `Not Started`. One must be from Hashing, one from Two
   Pointers or Sliding Window.
2. If you recognise a problem, swap it and note the swap in the results log.
3. Java 21, plain editor or IDE without AI completion. No tests provided — write your own
   `main` or a JUnit test to check examples.

**Clock:** 40 minutes total. Target ≤ 20 min each. Stop at the bell.

**Method:** follow [`../16-interview-prep/coding-interview-method.md`](../16-interview-prep/coding-interview-method.md):
restate → examples → brute force + complexity → better approach → code → trace one example →
edge cases. Say it out loud even alone — this is the first checkpoint that builds the habit.

**Scoring (feeds DSA + Java)**

| Result | Score |
|---|---:|
| Both correct, both ≤ 20 min, complexity stated correctly | 4 |
| Both correct within 40 min total, one complexity mistake or messy code | 3 |
| One correct in time; other partial or wrong | 2 |
| One correct but over time; or both partial | 1 |
| Neither correct | 0 |

Record for each: problem, pattern, time, status (`Solved Independently` / `Solved With Hint` /
`Attempted`), whether the trace caught a bug.

---

## Part B — Knowledge questions (45 min · 24 questions)

Write your answers first (short, 1–4 sentences, code where asked). Then open the `<details>`.
Score 1 point for a correct answer, 0.5 for partial. Max 24.

### Java (Q1–Q8)

**Q1.** What is the difference between `==` and `.equals()` for `String`s, and why does
`"a" == "a"` usually print `true` anyway?
<details><summary>Answer</summary>
`==` compares references; `.equals()` compares content. String literals are interned in the string
pool, so two identical literals share one object — but a `new String("a")` or a computed string is
a different object and `==` returns `false`. Always use `.equals()` (or `Objects.equals` for
null-safety).
</details>

**Q2.** You override `equals` in a class used as a `HashMap` key but forget `hashCode`. What
breaks and why?
<details><summary>Answer</summary>
`HashMap` buckets by `hashCode()` first. Two "equal" objects with different default (identity)
hash codes land in different buckets, so `map.get(equalKey)` misses. Contract: equal objects must
have equal hash codes. Use `Objects.hash(...)` over the same fields, or a `record`.
</details>

**Q3.** When would you choose `ArrayList` vs `LinkedList` vs `ArrayDeque`?
<details><summary>Answer</summary>
`ArrayList`: random access O(1), append amortised O(1), insertion in the middle O(n) — the default.
`LinkedList`: almost never; O(1) insertion at a known node but O(n) to find it, poor cache
locality. `ArrayDeque`: stack/queue operations at both ends in O(1) without `LinkedList`'s
overhead — use it for stacks and queues (`push/pop/offer/poll`).
</details>

**Q4.** What does this print, and why? 
```java
List<Integer> xs = new ArrayList<>(List.of(1, 2, 3));
for (Integer x : xs) if (x == 2) xs.remove(x);
```
<details><summary>Answer</summary>
Throws `ConcurrentModificationException` on the next iteration after the removal (fail-fast
iterator detects `modCount` change). Fix: `xs.removeIf(x -> x == 2)`, or use an explicit
`Iterator` and `it.remove()`. Note also `xs.remove(x)` with an `Integer` calls `remove(Object)`,
not `remove(int index)`.
</details>

**Q5.** Checked vs unchecked exceptions: give one example of each and state when you would
create a custom checked exception in FlowGrid.
<details><summary>Answer</summary>
Checked (`IOException`, `SQLException`) must be declared or handled; unchecked
(`IllegalArgumentException`, `NullPointerException`, any `RuntimeException`) need not. In a
Spring service layer, prefer unchecked domain exceptions (`SkuNotFoundException extends
RuntimeException`) mapped by `@ControllerAdvice` — checked exceptions also stop `@Transactional`
from rolling back by default. Rarely, a checked exception is right for a recoverable condition the
caller *must* handle explicitly (e.g. a parser API).
</details>

**Q6.** Write a stream pipeline that, given `List<InventoryLevel>` with `warehouseId()` and
`quantityOnHand()`, returns `Map<Long, Integer>` total on-hand per warehouse.
<details><summary>Answer</summary>

```java
Map<Long, Integer> totals = levels.stream()
    .collect(Collectors.groupingBy(InventoryLevel::warehouseId,
             Collectors.summingInt(InventoryLevel::quantityOnHand)));
```
</details>

**Q7.** What is a `record`? Name two things it gives you for free and one thing it cannot do.
<details><summary>Answer</summary>
An immutable, transparent data carrier: `record Point(int x, int y)`. Free: canonical
constructor, accessors, `equals`/`hashCode`/`toString`. Cannot: have mutable fields, extend a
class (it implicitly extends `Record`), or declare additional instance fields. Great for DTOs
and request/response bodies; not for JPA entities (JPA needs a no-arg constructor and mutable
state).
</details>

**Q8.** `Optional`: name one correct use and two misuses.
<details><summary>Answer</summary>
Correct: a return type for "may be absent" (`Optional<Sku> findBySkuCode(...)`) consumed with
`map/orElseThrow/orElseGet`. Misuses: as a field or method parameter; calling `.get()` without
`isPresent()`; wrapping collections (return an empty list instead).
</details>

### Git (Q9–Q11)

**Q9.** You committed a secret to a feature branch (not pushed). Exact steps to remove it from
history and from the working tree.
<details><summary>Answer</summary>
If it is the last commit: remove the file / add to `.gitignore`, `git rm --cached secret.env`,
`git commit --amend`. If older: `git rebase -i <commit>^`, mark the commit `edit`, remove the file,
`git commit --amend`, `git rebase --continue`. Rotate the secret regardless — assume it leaked.
If already pushed, force-push is not enough; rotate first.
</details>

**Q10.** Difference between `git merge` and `git rebase` for integrating `main` into your feature
branch; when is rebase inappropriate?
<details><summary>Answer</summary>
`merge` creates a merge commit preserving both histories; `rebase` replays your commits on top of
`main` giving a linear history but rewriting your commit SHAs. Never rebase commits that others
have already based work on (shared/pushed branches used by teammates). Rebasing your own unpushed
or personal PR branch is fine.
</details>

**Q11.** What does `git stash` do, and how do you recover a stash you dropped by mistake?
<details><summary>Answer</summary>
Saves working-tree and index changes to a stack and resets to `HEAD`. `git stash pop` reapplies.
A dropped stash's commit still exists until GC: `git fsck --unreachable | grep commit`, or the SHA
printed at drop time, then `git stash apply <sha>`.
</details>

### SQL (Q12–Q16)

**Q12.** Inner join vs left join: for `warehouse` and `inventory_level`, write the query listing
every warehouse with its count of SKUs stocked, including warehouses with none.
<details><summary>Answer</summary>

```sql
SELECT w.id, w.name, COUNT(il.sku_id) AS skus_stocked
FROM warehouse w
LEFT JOIN inventory_level il ON il.warehouse_id = w.id
GROUP BY w.id, w.name
ORDER BY w.name;
```
`COUNT(il.sku_id)` (not `COUNT(*)`) returns 0 for unmatched rows because `COUNT(col)` ignores NULLs.
</details>

**Q13.** `WHERE` vs `HAVING`.
<details><summary>Answer</summary>
`WHERE` filters rows before grouping; `HAVING` filters groups after aggregation. `HAVING
SUM(quantity) < 10` is legal; `WHERE SUM(quantity) < 10` is not.
</details>

**Q14.** Why is `SELECT * FROM sku WHERE code = 'ABC'` slow on a million rows without an index,
and what does the index change?
<details><summary>Answer</summary>
Without an index Postgres does a sequential scan reading every row (O(n) pages). A B-tree index on
`code` lets it find matching entries in O(log n) and fetch only those rows (index scan). Unique
indexes also enforce uniqueness. Trade-off: slower writes and extra storage.
</details>

**Q15.** What is a foreign key and what does `ON DELETE RESTRICT` vs `CASCADE` do?
<details><summary>Answer</summary>
A constraint that a column's values must exist in the referenced table's key. `RESTRICT` refuses
to delete a parent while children exist; `CASCADE` deletes the children too. For FlowGrid: deleting
a warehouse with inventory should be restricted (or soft-deleted), never cascaded.
</details>

**Q16.** Three-valued logic: what does `WHERE city != 'Paris'` return for rows whose `city` is
NULL?
<details><summary>Answer</summary>
Nothing for those rows. `NULL != 'Paris'` evaluates to `UNKNOWN`, which `WHERE` treats as false.
Use `WHERE city IS DISTINCT FROM 'Paris'` or add `OR city IS NULL`.
</details>

### HTTP / REST / Spring (Q17–Q22)

**Q17.** Which status codes for: successful creation, validation failure, missing resource,
authenticated but not permitted, no credentials at all?
<details><summary>Answer</summary>
201 Created (with `Location`), 400 Bad Request (or 422), 404 Not Found, 403 Forbidden,
401 Unauthorized.
</details>

**Q18.** What is constructor injection and why prefer it over field injection?
<details><summary>Answer</summary>
Dependencies arrive through the constructor (Spring auto-wires a single constructor). Benefits:
`final` fields, impossible to construct without dependencies, trivially unit-testable without
Spring, circular dependencies fail fast at startup.
</details>

**Q19.** Explain what `@Transactional` on a service method does for a JPA `save()` followed by an
exception.
<details><summary>Answer</summary>
Spring opens a transaction (via a proxy) before the method and commits after; a `RuntimeException`
escaping the method triggers rollback, so the `save()` is undone. Checked exceptions do *not* roll
back by default (`rollbackFor` to change). Self-invocation inside the same bean bypasses the proxy —
no transaction.
</details>

**Q20.** How does Bean Validation reach a controller, and what turns a violation into a
`ProblemDetail` response?
<details><summary>Answer</summary>
`@Valid` on a `@RequestBody` parameter triggers validation of constraints (`@NotBlank`,
`@Positive`, …) on the DTO; violations throw `MethodArgumentNotValidException`. A
`@RestControllerAdvice` (or `ResponseEntityExceptionHandler` with `spring.mvc.problemdetails.enabled=true`)
maps it to a 400 `application/problem+json` body with a field-error list.
</details>

**Q21.** Describe a JWT: its three parts, what "stateless" means for the server, and one thing
a JWT does *not* solve.
<details><summary>Answer</summary>
Header.payload.signature (base64url); the signature (HMAC or RSA/EC) lets the server verify the
token without storing sessions — each request carries its own proof. Does not solve revocation
before expiry (need short TTLs, refresh tokens or a deny-list) and does not encrypt the payload.
</details>

**Q22.** In Spring Security 6, how do you restrict `DELETE /api/warehouses/{id}` to `ADMIN`?
Two ways.
<details><summary>Answer</summary>
(1) In the `SecurityFilterChain`: `.requestMatchers(HttpMethod.DELETE, "/api/warehouses/**").hasRole("ADMIN")`.
(2) `@PreAuthorize("hasRole('ADMIN')")` on the controller/service method with
`@EnableMethodSecurity`. Roles are stored as `ROLE_ADMIN` authorities; `hasRole` adds the prefix.
</details>

### Testing / build / Docker / CI (Q23–Q24)

**Q23.** What does `mvn verify` run that `mvn test` does not, and where does Testcontainers-style
integration testing normally hook in?
<details><summary>Answer</summary>
`verify` runs the full lifecycle through `package` and `integration-test` (Failsafe plugin,
`*IT` classes) then `verify`; `test` stops at Surefire unit tests. Integration tests bind to
Failsafe so a packaged jar can be tested.
</details>

**Q24.** In `compose.yaml`, how does the Spring app reach Postgres, and why does `localhost:5432`
fail *inside* the app container?
<details><summary>Answer</summary>
Compose creates a network; services resolve each other by service name (`jdbc:postgresql://postgres:5432/flowgrid`).
Inside the app container `localhost` is the container itself, not the host or the DB container.
From the host you use the published port (`ports: "5432:5432"`).
</details>

**Part B scoring (feeds Java, Git, SQL, Backend, CS):** ≥ 20/24 → 4 · 16–19 → 3 · 12–15 → 2 ·
8–11 → 1 · < 8 → 0. Also note per-section scores; a section below 50% fails that area regardless
of the total.

---

## Part C — Practical task (60 min)

**Task:** in your FlowGrid repo, add one **validated, authorized endpoint with a slice test**,
from a fresh branch, in 60 minutes.

**Specification**

`POST /api/warehouses/{warehouseId}/adjustments` — record a stock adjustment for a SKU.

- Request body: `{ "skuId": long, "quantityDelta": int (non-zero, |delta| ≤ 10 000), "reason": "RECOUNT|DAMAGE|CORRECTION", "note": string ≤ 255 }`
- Rules: warehouse and SKU must exist (404 ProblemDetail otherwise); resulting on-hand must not go
  negative (409 with `type` describing the conflict); writes an `audit_event` row in the same
  transaction; returns **201** with the new inventory level and `Location` header.
- Authorization: `ADMIN` or `OPS_MANAGER` only; `WAREHOUSE_ASSOCIATE` and `VIEWER` get 403;
  unauthenticated gets 401.
- Validation errors → 400 `application/problem+json` with field list.
- OpenAPI: the endpoint appears with request/response schemas.

**Tests required (slice, not full context):**

- `@WebMvcTest(AdjustmentController.class)` with `@MockBean` service and security applied:
  201 happy path, 400 on `quantityDelta = 0`, 403 for `VIEWER`, 401 without token.
- One `@DataJpaTest` (or service test with Testcontainers if already set up) asserting the
  negative-stock rule.

**Deliverable:** branch `cp4/adjustment-endpoint`, PR opened against `main`, CI green.

**Acceptance / scoring (feeds Backend, Java, Git)**

| Criteria | Points |
|---|---:|
| Endpoint works end-to-end (curl shows 201 + Location) | 1 |
| Validation + ProblemDetail correct | 1 |
| Authorization correct for all four roles + anonymous | 1 |
| Audit event written transactionally | 0.5 |
| Slice tests written and green | 1 |
| Done in 60 min, PR + CI green | 0.5 |

5/5 → 4 · 4–4.5 → 3 · 3–3.5 → 2 · 1.5–2.5 → 1 · < 1.5 → 0.

If you needed the Spring reference docs for `@WebMvcTest` security setup, that is allowed — note
it. If you needed to copy from an existing controller in your own repo, that is allowed — it is
your code.

---

## Part D — Explain out loud (30 min, recorded)

Record each answer; 2–4 minutes each. Score each 0–4 with the rubric in
[`../16-interview-prep/mock-interviews.md`](../16-interview-prep/mock-interviews.md)
(structure, correctness, trade-offs, example from *my* code).

1. "Walk me through what happens when a `POST /api/skus` request hits FlowGrid, from the TCP
   connection to the row in Postgres." (Filters → security → dispatcher → controller → validation
   → service → repository → JPA → JDBC → transaction commit → response serialisation.)
2. "Why did you choose Flyway migrations instead of `ddl-auto=update`?"
3. "How does your JWT authentication work and what happens when a token expires?"
4. "Explain the inventory-level model: why quantity is split into states, and which state
   changes M1 supports."
5. "Show me a test you are proud of from Week 2–4 and explain what it proves and what it doesn't."
6. **Mini deep dive (8 min):** "Give me the FlowGrid M1 overview as if I am a senior engineer on
   your new team: purpose, entities, API shape, auth model, what is not built yet."

Listen back once. Note filler, missing trade-offs, any claim you could not defend.

**Scoring (feeds Interview readiness):** average of the six.

---

## Part E — Project review: FlowGrid M1 vs MVP core

Check against [`../18-projects/flowgrid/README.md`](../18-projects/flowgrid/README.md) and the M1
acceptance criteria in [`../18-projects/flowgrid/milestones.md`](../18-projects/flowgrid/milestones.md).

**Scope**
- [ ] Spring Boot 3.x, Java 21, Maven, runs with `docker compose up` (Postgres) + `mvn spring-boot:run`
- [ ] Flyway migrations: warehouse, product, sku, inventory_level, stock_adjustment, audit_event, app_user, role
- [ ] CRUD for warehouses, products, SKUs; inventory levels readable per warehouse and per SKU
- [ ] Stock adjustments change on-hand and write an audit event in the same transaction
- [ ] Bean Validation on all request DTOs; ProblemDetail errors for 400/404/409
- [ ] Pagination + sort + at least one filter on list endpoints (`Pageable`, bounded page size)
- [ ] JWT login; roles ADMIN / OPS_MANAGER / WAREHOUSE_ASSOCIATE / VIEWER enforced on at least one endpoint per role boundary
- [ ] OpenAPI UI (springdoc) reachable and accurate

**Quality**
- [ ] Unit tests for domain rules; slice tests for controllers; at least one repository test
- [ ] `mvn verify` green locally and in GitHub Actions on every PR
- [ ] Repository has README (run instructions), `.gitignore`, no secrets committed, `.env.example`
- [ ] Commit history shows small, described commits and at least three merged PRs
- [ ] No `ddl-auto=update` in any profile; no `System.out` logging; no `catch (Exception e) {}`

**Hours**
- [ ] Project hours logged in [`../trackers/project-tracker.md`](../trackers/project-tracker.md): target ≈ 26 h this week

**Scoring (feeds Projects):** all scope boxes + ≥ 4 quality boxes → 4 · one scope box missing → 3 ·
two scope boxes missing → 2 · three or more → 1 · M1 not runnable → 0.

---

## Scoring table

| Area | Evidence | Critical? | PASS threshold | My score |
|---|---|:---:|:---:|:---:|
| Java | A (code quality), B Q1–Q8, C | yes | 3 | |
| DSA | A | yes | 2 | |
| SQL | B Q12–Q16 | yes | 2 | |
| Backend (Spring/REST/JPA/security) | B Q17–Q22, C | yes | 3 | |
| Frontend | — not yet | — | — | — |
| Git | B Q9–Q11, C (branch/PR hygiene), E | no | 2 | |
| Debugging | A trace step, C (did you use the debugger / logs to find failures?) | no | 2 | |
| CS fundamentals | B Q23–Q24 + HTTP questions | no | 2 | |
| Projects | E | yes | 3 | |
| Interview readiness | D | no | 2 | |

## Decision

| Condition | Decision |
|---|---|
| All critical areas ≥ threshold, ≤ 1 non-critical below | **PASS** |
| All critical met, ≥ 2 non-critical below | **PASS WITH REMEDIATION** |
| Any critical area below threshold | **FAIL** — remediate; Week 5 starts on schedule |
| FlowGrid M1 not runnable end-to-end | **FAIL** + finish M1 before starting M2 (ROADMAP §13 rule 3); M2 tasks shift, Week 8 buffer noted |

---

## If you fail — remediation (inside Weeks 5–6 review blocks, ≈ 6–8 h total)

| Area | Do this |
|---|---|
| **Java** | Re-do the exercises in [`../01-java/exercises.md`](../01-java/exercises.md) for collections and streams from a blank file (2 h). Re-read [`../01-java/03-collections-generics.md`](../01-java/03-collections-generics.md) and [`../01-java/05-exceptions-io.md`](../01-java/05-exceptions-io.md). Write 10 flashcards from [`../01-java/interview-questions.md`](../01-java/interview-questions.md) and drill on Fri. |
| **DSA** | Cut Week 5 new problems from 8 to 5; add 6 Easy re-solves from Hashing/Two Pointers/Sliding Window marked `Solved With Solution` (Week 5 Sat, 2 h). Re-read [`../03-dsa/02-hashing.md`](../03-dsa/02-hashing.md) templates; time every solve. |
| **SQL** | [`../04-sql-databases/sql-practice.md`](../04-sql-databases/sql-practice.md) joins + aggregation sets against `practice-schema.sql` (3 h across two Sundays). Write every FlowGrid list query by hand in `psql` before trusting JPA. |
| **Backend** | Rebuild one FlowGrid controller + service + slice test from a blank file without looking at the original (Week 5 Sat, 2 h). Re-read [`../05-spring-boot/04-validation-errors.md`](../05-spring-boot/04-validation-errors.md) and [`../05-spring-boot/05-security-jwt.md`](../05-spring-boot/05-security-jwt.md). |
| **Git** | [`../02-git/exercises.md`](../02-git/exercises.md) rebase/stash/amend set (1 h). Adopt the PR workflow in [`../02-git/workflows.md`](../02-git/workflows.md) for every M2 task. |
| **Debugging** | Set a breakpoint in the FlowGrid request path and step through one request ([`../01-java/09-debugging-java.md`](../01-java/09-debugging-java.md)), 1 h. |
| **CS fundamentals** | [`../06-rest-apis/http-for-apis.md`](../06-rest-apis/http-for-apis.md) + `curl -v` against FlowGrid for every status code (1 h). |
| **Projects** | Finish M1 before M2 features (ROADMAP §13). Use Week 5 project hours; if M1 completion takes > 1 week, drop FlowGrid Advanced Version now. |
| **Interview** | Re-record prompt 6 after reading [`../16-interview-prep/project-deep-dive.md`](../16-interview-prep/project-deep-dive.md) (Week 5 Sat, 45 min). |

Two consecutive failures in the same area (this and CP-8) → freeze new features in that area for
one week.

---

## Results log

```
CP-4 — date: ____________   total time: ____ h

PART A  problem 1: ____________ pattern: ______ time: __ min status: ________ swap? __
        problem 2: ____________ pattern: ______ time: __ min status: ________ swap? __
        score: __/4
PART B  Java __/8  Git __/3  SQL __/5  Spring/HTTP __/6  Build/Docker __/2  total __/24  score __/4
PART C  points __/5  time __ min  CI green? __  docs used: ______________  score __/4
PART D  prompts 1-6: __ __ __ __ __ __  average __  weakest prompt: __  score __/4
PART E  scope __/8  quality __/5  hours logged __  score __/4

AREA SCORES  Java __  DSA __  SQL __  Backend __  Git __  Debug __  CS __  Projects __  Interview __
DECISION     PASS / PASS WITH REMEDIATION / FAIL
REMEDIATION  areas: ____________  hours planned W5: __  W6: __
NOTES        (what surprised me; what I could not explain; what to change next week)
```

Copy area scores to [`./README.md`](./README.md#summary-log) and
[`../trackers/weekly-progress.md`](../trackers/weekly-progress.md).
