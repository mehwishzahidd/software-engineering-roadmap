# Checkpoint 4 — End of FlowGrid M1

> **Gate question:** Can I solve Easies in Python fluently, write tested Java, basic SQL and a
> secured Spring Boot CRUD API (FlowGrid M1) without a tutorial?

| | |
|---|---|
| **When** | Sunday of Week 4 (Part A + C on Saturday afternoon, Parts B/D/E + scoring on Sunday) |
| **Time** | ≈ 3.5–4 h: A 55 min · B 50 min · C 60 min · D 30 min · E 20 min · scoring 10 min |
| **Covers** | Weeks 1–4: Python core + interview toolkit + pytest; Java syntax → OOP → collections → modern Java; Git core + branches; JUnit 5 + Maven; SQL basics + joins/aggregation; HTTP/REST; Spring Boot intro → JPA, validation, ProblemDetail, pagination, Security + JWT; Docker Compose for Postgres; basic GitHub Actions |
| **DSA patterns in scope** | Big-O, Arrays & Strings, Hashing, Two Pointers, Prefix Sums, Sliding Window ([`../03-dsa/00-big-o.md`](../03-dsa/00-big-o.md) … [`05-sliding-window.md`](../03-dsa/05-sliding-window.md)) |
| **Project under review** | [FlowGrid](../18-projects/flowgrid/README.md) M1 — **MVP core** (see [milestones.md](../18-projects/flowgrid/milestones.md)) |
| **Rules** | [Checkpoint rules](./README.md#rules): closed book except language/framework docs, timed, honest. Cheatsheet closed during Part A. |

This is the first gate. Thresholds are deliberately modest: the question is whether the
foundation exists in **both tracks** — Python for coding rounds, Java for the project — not
whether either is polished.

---

## Part A — Timed coding (Track A · Python · 40 min · 2 Easy) + Java rep (15 min)

### A1 — Python, 2 Easy in 40 min

**Selection rules**

1. Open [`../trackers/dsa-tracker.md`](../trackers/dsa-tracker.md). Pick two problems tagged
   **Easy** from the NeetCode/LeetCode lists for *Arrays & Hashing*, *Two Pointers* or
   *Sliding Window* with status `Not Started`. One must be from Hashing, one from Two Pointers or
   Sliding Window.
2. If you recognise a problem, swap it and note the swap in the results log.
3. Python 3.12, plain editor without AI completion. Write a few `assert`s or a tiny `pytest`
   function to check the examples — no online judge until the bell.

**Clock:** 40 minutes total, target ≤ 20 min each. Stop at the bell.

**Method:** [`../16-interview-prep/coding-interview-method.md`](../16-interview-prep/coding-interview-method.md):
restate → examples → brute force + complexity → better approach → code → trace one example →
edge cases. Say it out loud even alone.

**Python quality check (after the bell, 2 min):** did you use `Counter`/`defaultdict`/`set` where
they fit, `enumerate` instead of `range(len(...))`, no `list.pop(0)` in a loop, no accidental
O(n) `in` on a list, correct handling of the empty input?

### A2 — Java rep, 15 min

Pick **one problem already `Solved Independently` in Python** from Hashing (e.g. a frequency-count
or two-sum-style problem). Re-implement it in Java 21 from a blank file using the collections it
needs (`HashMap`, `HashSet`, `ArrayList`, `Arrays.sort`, `StringBuilder`), plus a `main` or JUnit
test running the examples. Time limit 15 min. This checks Java collections fluency, not
algorithmic insight — you already know the algorithm.

**Scoring**

| A1 result (feeds DSA + Python) | Score |
|---|---:|
| Both correct, both ≤ 20 min, complexity stated correctly, idiomatic Python | 4 |
| Both correct within 40 min total, one complexity mistake or non-idiomatic code | 3 |
| One correct in time; other partial or wrong | 2 |
| One correct but over time; or both partial | 1 |
| Neither correct | 0 |

| A2 result (feeds Java) | Points |
|---|---:|
| Compiles and passes examples in 15 min, correct generics, no raw types | 2 |
| Compiles and passes but over time or needed the Javadoc for basic API | 1 |
| Does not compile / wrong | 0 |

Record for each: problem, pattern, language, time, status, whether the trace caught a bug.

---

## Part B — Knowledge questions (50 min · 30 questions)

Write your answers first (1–4 sentences, code where asked). Then open each `<details>`.
Score 1 point for a correct answer, 0.5 for partial. Max 30.

### Python for interviews (Q1–Q6)

**Q1.** What is wrong with `def add(item, bucket=[]): bucket.append(item); return bucket`, and
what is the idiom?
<details><summary>Answer</summary>
Default arguments are evaluated **once** at function definition, so every call without `bucket`
shares the same list — calls accumulate. Idiom: `bucket=None` then `if bucket is None: bucket = []`.
</details>

**Q2.** `grid = [[0] * 3] * 3; grid[0][0] = 1` — what does `grid` look like and why? Give the fix.
<details><summary>Answer</summary>
All three rows become `[1, 0, 0]`: the outer `* 3` copies the *reference* to one inner list.
Fix: `grid = [[0] * 3 for _ in range(3)]`. Same trap for any nested mutable structure; `copy.deepcopy`
for arbitrary nesting.
</details>

**Q3.** Average-case complexity of `x in some_list`, `x in some_set`, `d[key]`, `lst.append`,
`lst.pop(0)`, `lst.insert(0, x)`. Which one should make you reach for `deque`?
<details><summary>Answer</summary>
List `in`: O(n). Set `in` and dict lookup: O(1) average (hash). `append`: amortised O(1).
`pop(0)` and `insert(0, x)`: O(n) (shift every element) — use `collections.deque` with
`popleft()`/`appendleft()` for O(1) at both ends.
</details>

**Q4.** `heapq` is a min-heap. How do you get a max-heap, and how do you push a `(priority, item)`
pair safely when two items can have the same priority and `item` is not comparable?
<details><summary>Answer</summary>
Push negated keys: `heapq.heappush(h, -x)` and negate on pop. For tuples, add a tie-breaker
counter: `heappush(h, (priority, counter, item))` (`itertools.count()`), so comparison never
reaches the non-comparable `item`. Tuples compare element by element.
</details>

**Q5.** Why can a clean recursive DFS on a 20 000-node linked structure crash in Python, and what
are the two remedies?
<details><summary>Answer</summary>
The default recursion limit is ~1000 frames → `RecursionError`. Remedies: `sys.setrecursionlimit(10**6)`
(works for most judge inputs but can still segfault for extreme depth) or rewrite iteratively with
an explicit stack — the safer answer in an interview.
</details>

**Q6.** `s = "abc"; s[0] = "z"` fails. Explain, and give the O(n) idiom for building a string
character by character (and why `s += ch` in a loop is a trap).
<details><summary>Answer</summary>
`str` is immutable — no item assignment. Collect parts in a list and `"".join(parts)`. `s += ch`
creates a new string each time (O(n) per step, O(n²) total in principle; CPython sometimes
optimises it, but never rely on that in an interview). Also note slicing `s[a:b]` copies — O(b−a).
</details>

### Java (Q7–Q13)

**Q7.** What is the difference between `==` and `.equals()` for `String`s, and why does
`"a" == "a"` usually print `true` anyway?
<details><summary>Answer</summary>
`==` compares references; `.equals()` compares content. String literals are interned, so two
identical literals share one object — but `new String("a")` or a computed string is a different
object. Always use `.equals()` (or `Objects.equals` for null-safety).
</details>

**Q8.** You override `equals` in a class used as a `HashMap` key but forget `hashCode`. What
breaks and why?
<details><summary>Answer</summary>
`HashMap` buckets by `hashCode()` first. Two "equal" objects with different identity hash codes
land in different buckets, so `map.get(equalKey)` misses. Contract: equal objects must have equal
hash codes. Use `Objects.hash(...)` over the same fields, or a `record`.
</details>

**Q9.** When would you choose `ArrayList` vs `LinkedList` vs `ArrayDeque`?
<details><summary>Answer</summary>
`ArrayList`: random access O(1), append amortised O(1), middle insertion O(n) — the default.
`LinkedList`: almost never; poor cache locality. `ArrayDeque`: O(1) at both ends — use it for
stacks and queues (`push/pop/offer/poll`), the Java equivalent of Python's `deque`.
</details>

**Q10.** What does this do, and why?
```java
List<Integer> xs = new ArrayList<>(List.of(1, 2, 3));
for (Integer x : xs) if (x == 2) xs.remove(x);
```
<details><summary>Answer</summary>
Throws `ConcurrentModificationException` on the next iteration after the removal (fail-fast
iterator). Fix: `xs.removeIf(x -> x == 2)` or `Iterator.remove()`. Also: `xs.remove(x)` with an
`Integer` calls `remove(Object)`, not `remove(int index)`.
</details>

**Q11.** Checked vs unchecked exceptions: one example of each, and which kind a FlowGrid service
should throw for "SKU not found" and why.
<details><summary>Answer</summary>
Checked (`IOException`, `SQLException`) must be declared or handled; unchecked
(`IllegalArgumentException`, any `RuntimeException`) need not. Use an unchecked domain exception
(`SkuNotFoundException extends RuntimeException`) mapped by `@ControllerAdvice` — checked
exceptions also do not roll back `@Transactional` by default.
</details>

**Q12.** Write a stream pipeline that, given `List<InventoryLevel>` with `warehouseId()` and
`quantityOnHand()`, returns `Map<Long, Integer>` total on-hand per warehouse.
<details><summary>Answer</summary>

```java
Map<Long, Integer> totals = levels.stream()
    .collect(Collectors.groupingBy(InventoryLevel::warehouseId,
             Collectors.summingInt(InventoryLevel::quantityOnHand)));
```
(Python equivalent: `Counter()` updated in a loop, or `defaultdict(int)`.)
</details>

**Q13.** What is a `record`? Two things it gives you for free, one thing it cannot do, and why it
is wrong for a JPA entity.
<details><summary>Answer</summary>
Immutable data carrier: canonical constructor, accessors, `equals`/`hashCode`/`toString` for free.
Cannot have mutable fields or extend a class. JPA needs a no-arg constructor and mutable state
(proxies, dirty checking) → records are for DTOs, not entities.
</details>

### Git (Q14–Q16)

**Q14.** You committed a secret to a feature branch (not pushed). Exact steps to remove it from
history and the working tree.
<details><summary>Answer</summary>
Last commit: add to `.gitignore`, `git rm --cached secret.env`, `git commit --amend`. Older:
`git rebase -i <commit>^`, mark `edit`, remove the file, `git commit --amend`, `git rebase --continue`.
Rotate the secret regardless. If already pushed, rotation is the only real fix.
</details>

**Q15.** `git merge` vs `git rebase` for integrating `main` into your feature branch; when is
rebase inappropriate?
<details><summary>Answer</summary>
`merge` adds a merge commit preserving both histories; `rebase` replays your commits on top of
`main` (linear history, new SHAs). Never rebase commits others have based work on. Your own
unpushed or personal PR branch is fine.
</details>

**Q16.** What does `git stash` do, and how do you recover a stash dropped by mistake?
<details><summary>Answer</summary>
Saves working-tree and index changes to a stack and resets to `HEAD`; `git stash pop` reapplies.
A dropped stash commit survives until GC: `git fsck --unreachable | grep commit` or the SHA printed
at drop time, then `git stash apply <sha>`.
</details>

### SQL (Q17–Q21)

**Q17.** Write the query listing every warehouse with its count of SKUs stocked, including
warehouses with none.
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

**Q18.** `WHERE` vs `HAVING`.
<details><summary>Answer</summary>
`WHERE` filters rows before grouping; `HAVING` filters groups after aggregation.
</details>

**Q19.** Why is `SELECT * FROM sku WHERE code = 'ABC'` slow on a million rows without an index,
and what does the index change?
<details><summary>Answer</summary>
Sequential scan reads every row. A B-tree index on `code` finds matches in O(log n) and fetches
only those rows; a unique index also enforces uniqueness. Cost: slower writes, storage.
</details>

**Q20.** What is a foreign key; `ON DELETE RESTRICT` vs `CASCADE`; which for warehouse →
inventory_level?
<details><summary>Answer</summary>
Values must exist in the referenced key. `RESTRICT` refuses to delete a parent with children;
`CASCADE` deletes children too. Deleting a warehouse with inventory should be restricted (or
soft-deleted), never cascaded.
</details>

**Q21.** What does `WHERE city != 'Paris'` return for rows whose `city` is NULL?
<details><summary>Answer</summary>
Nothing for those rows: `NULL != 'Paris'` is `UNKNOWN`, treated as false. Use
`IS DISTINCT FROM` or add `OR city IS NULL`.
</details>

### HTTP / REST / Spring (Q22–Q27)

**Q22.** Status codes for: successful creation, validation failure, missing resource,
authenticated but not permitted, no credentials.
<details><summary>Answer</summary>
201 Created (+ `Location`), 400 (or 422), 404, 403, 401.
</details>

**Q23.** What is constructor injection and why prefer it over field injection?
<details><summary>Answer</summary>
Dependencies arrive via the constructor; Spring auto-wires a single constructor. `final` fields,
cannot construct without dependencies, unit-testable without Spring, circular dependencies fail
fast.
</details>

**Q24.** What does `@Transactional` on a service method do for a `save()` followed by an
exception?
<details><summary>Answer</summary>
A proxy opens a transaction before and commits after; a `RuntimeException` escaping triggers
rollback so the `save()` is undone. Checked exceptions do not roll back by default. Self-invocation
bypasses the proxy — no transaction.
</details>

**Q25.** How does Bean Validation reach a controller, and what turns a violation into a
`ProblemDetail` response?
<details><summary>Answer</summary>
`@Valid` on a `@RequestBody` DTO triggers constraint checks; violations throw
`MethodArgumentNotValidException`. A `@RestControllerAdvice` (or
`spring.mvc.problemdetails.enabled=true` + `ResponseEntityExceptionHandler`) maps it to a 400
`application/problem+json` body with field errors.
</details>

**Q26.** Describe a JWT: three parts, what "stateless" means for the server, one thing it does
*not* solve.
<details><summary>Answer</summary>
Header.payload.signature (base64url). The signature lets the server verify without a session store.
Does not solve revocation before expiry (short TTL, refresh tokens, deny-list) and does not encrypt
the payload.
</details>

**Q27.** In Spring Security 6, restrict `DELETE /api/warehouses/{id}` to `ADMIN` — two ways.
<details><summary>Answer</summary>
(1) `SecurityFilterChain`: `.requestMatchers(HttpMethod.DELETE, "/api/warehouses/**").hasRole("ADMIN")`.
(2) `@PreAuthorize("hasRole('ADMIN')")` with `@EnableMethodSecurity`. Roles are stored as
`ROLE_ADMIN`; `hasRole` adds the prefix.
</details>

### Testing / build / Docker / CI (Q28–Q30)

**Q28.** What does `mvn verify` run that `mvn test` does not?
<details><summary>Answer</summary>
Everything through `package`, `integration-test` (Failsafe, `*IT` classes) and `verify`; `test`
stops at Surefire unit tests.
</details>

**Q29.** In `compose.yaml`, how does the Spring app reach Postgres, and why does `localhost:5432`
fail *inside* the app container?
<details><summary>Answer</summary>
Services resolve each other by service name on the Compose network
(`jdbc:postgresql://postgres:5432/flowgrid`). Inside the container `localhost` is the container
itself. From the host use the published port.
</details>

**Q30.** In pytest, what is a fixture, and how would you test a function that reads a CSV without
touching the real filesystem?
<details><summary>Answer</summary>
A fixture is a function decorated `@pytest.fixture` whose return value is injected into tests by
parameter name (setup/teardown via `yield`). Use the built-in `tmp_path` fixture to write a
temporary CSV, or pass an `io.StringIO` to the function if it accepts a file-like object.
</details>

**Part B scoring:** ≥ 25/30 → 4 · 20–24 → 3 · 15–19 → 2 · 10–14 → 1 · < 10 → 0. Section scores
feed areas separately (Python Q1–Q6 → Python; Java Q7–Q13 → Java; Git; SQL; Spring/HTTP →
Backend; Q28–Q30 → CS/Build). A section below 50% fails that area regardless of the total.

---

## Part C — Practical task (Track B · 60 min)

**Task:** in your FlowGrid repo, add one **validated, authorized endpoint with a slice test**,
from a fresh branch, in 60 minutes.

**Specification**

`POST /api/warehouses/{warehouseId}/adjustments` — record a stock adjustment for a SKU.

- Request body: `{ "skuId": long, "quantityDelta": int (non-zero, |delta| ≤ 10 000), "reason": "RECOUNT|DAMAGE|CORRECTION", "note": string ≤ 255 }`
- Rules: warehouse and SKU must exist (404 ProblemDetail otherwise); resulting on-hand must not go
  negative (409 with a `type` describing the conflict); writes an `audit_event` row in the same
  transaction; returns **201** with the new inventory level and a `Location` header.
- Authorization: `ADMIN` or `OPS_MANAGER` only; `WAREHOUSE_ASSOCIATE` and `VIEWER` get 403;
  unauthenticated gets 401.
- Validation errors → 400 `application/problem+json` with field list.
- OpenAPI: the endpoint appears with request/response schemas.

**Tests required (slice, not full context)**

- `@WebMvcTest(AdjustmentController.class)` with a mocked service and security applied:
  201 happy path, 400 on `quantityDelta = 0`, 403 for `VIEWER`, 401 without token.
- One `@DataJpaTest` (or a service test on Testcontainers if already set up) asserting the
  negative-stock rule.

**Deliverable:** branch `cp4/adjustment-endpoint`, PR opened against `main`, CI green.

**Acceptance / scoring (feeds Backend, Java, Git)**

| Criteria | Points |
|---|---:|
| Endpoint works end-to-end (`curl` shows 201 + `Location`) | 1 |
| Validation + ProblemDetail correct | 1 |
| Authorization correct for all four roles + anonymous | 1 |
| Audit event written transactionally | 0.5 |
| Slice tests written and green | 1 |
| Done in 60 min, PR + CI green | 0.5 |

5/5 → 4 · 4–4.5 → 3 · 3–3.5 → 2 · 1.5–2.5 → 1 · < 1.5 → 0.

Using the Spring reference for `@WebMvcTest` security setup is allowed — note it. Copying from an
existing controller in your own repo is allowed — it is your code.

---

## Part D — Explain out loud (30 min, recorded)

Record each answer; 2–4 minutes each. Score 0–4 with the rubric in
[`../16-interview-prep/mock-interviews.md`](../16-interview-prep/mock-interviews.md)
(structure, correctness, trade-offs, example from *my* code).

1. "Walk me through what happens when `POST /api/skus` hits FlowGrid, from the TCP connection to
   the row in Postgres." (Filters → security → dispatcher → controller → validation → service →
   repository → JPA → JDBC → commit → response serialisation.)
2. "Why Flyway migrations instead of `ddl-auto=update`?"
3. "How does your JWT authentication work, and what happens when a token expires?"
4. "Explain the inventory-level model: why quantity is split into states, and which state
   changes M1 supports."
5. **Track A prompt:** "Explain your Part A solution to the Hashing problem as if to an
   interviewer: approach, why a dict/set, complexity, one edge case." (2 min, Python vocabulary.)
6. **Mini deep dive (8 min):** "Give me the FlowGrid M1 overview as if I am a senior engineer on
   your new team: purpose, entities, API shape, auth model, what is not built yet."

Listen back once. Note filler, missing trade-offs, any claim you could not defend.

**Scoring (feeds Interview readiness):** average of the six.

---

## Part E — Project review: FlowGrid M1 vs MVP core

Check against [`../18-projects/flowgrid/README.md`](../18-projects/flowgrid/README.md) and the M1
acceptance criteria in [`../18-projects/flowgrid/milestones.md`](../18-projects/flowgrid/milestones.md).

**Scope**
- [ ] Spring Boot 3.x, Java 21, Maven; runs with `docker compose up` (Postgres) + `mvn spring-boot:run`
- [ ] Flyway migrations: warehouse, product, sku, inventory_level, stock_adjustment, audit_event, app_user, role
- [ ] CRUD for warehouses, products, SKUs; inventory levels readable per warehouse and per SKU
- [ ] Stock adjustments change on-hand and write an audit event in the same transaction
- [ ] Bean Validation on all request DTOs; ProblemDetail errors for 400/404/409
- [ ] Pagination + sort + at least one filter on list endpoints (bounded page size)
- [ ] JWT login; roles ADMIN / OPS_MANAGER / WAREHOUSE_ASSOCIATE / VIEWER enforced on at least one endpoint per role boundary
- [ ] OpenAPI UI (springdoc) reachable and accurate

**Quality**
- [ ] Unit tests for domain rules; slice tests for controllers; at least one repository test
- [ ] `mvn verify` green locally and in GitHub Actions on every PR
- [ ] README with run instructions, `.gitignore`, no secrets committed, `.env.example`
- [ ] Commit history: small described commits, at least three merged PRs
- [ ] No `ddl-auto=update` in any profile; no `System.out` logging; no `catch (Exception e) {}`

**Foundation Python (not portfolio, but check it exists)**
- [ ] [`../19-python/exercises.md`](../19-python/exercises.md) W1–W3 sets done with `pytest` passing; at least one script uses `argparse` + `pathlib`

**Hours**
- [ ] Project hours logged in [`../trackers/project-tracker.md`](../trackers/project-tracker.md): target ≈ 26 h this week

**Scoring (feeds Projects):** all scope boxes + ≥ 4 quality boxes → 4 · one scope box missing → 3 ·
two missing → 2 · three or more → 1 · M1 not runnable → 0.

---

## Scoring table

| Area | Evidence | Critical? | PASS threshold | My score |
|---|---|:---:|:---:|:---:|
| Python (coding) | A1 (idiom + speed), B Q1–Q6 | yes | 3 | |
| Java (engineering) | A2 Java rep, B Q7–Q13, C code quality | yes | 3 | |
| DSA | A1 correctness/complexity | yes | 2 | |
| SQL | B Q17–Q21 | yes | 2 | |
| Backend (Spring/REST/JPA/security) | B Q22–Q27, C | yes | 3 | |
| Frontend | — not yet | — | — | — |
| Git | B Q14–Q16, C (branch/PR hygiene), E | no | 2 | |
| Debugging | A trace step, C (debugger / logs used to find failures?) | no | 2 | |
| CS fundamentals | B Q28–Q30 + HTTP questions | no | 2 | |
| Projects | E | yes | 3 | |
| Interview readiness | D | no | 2 | |

## Decision

| Condition | Decision |
|---|---|
| All critical areas ≥ threshold, ≤ 1 non-critical below | **PASS** |
| All critical met, ≥ 2 non-critical below | **PASS WITH REMEDIATION** |
| Any critical area below threshold | **FAIL** — remediate; Week 5 starts on schedule |
| FlowGrid M1 not runnable end-to-end | **FAIL** + finish M1 before starting M2 (ROADMAP §13 rule 3) |

---

## If you fail — remediation (inside Weeks 5–6 review blocks, ≈ 6–8 h total)

| Area | Do this |
|---|---|
| **Python (coding)** | Re-read [`../19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) and [`../19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md); rewrite the [`../PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md) templates from memory (1.5 h). Re-solve 6 Easies from Hashing/Two Pointers with a 15-min clock each, then compare against NeetCode's Python solution for idiom (Week 5 Sat, 2 h). |
| **Java (engineering)** | [`../01-java/exercises.md`](../01-java/exercises.md) collections + streams set from a blank file (2 h). Do the Java rep twice in Week 5 instead of once. Re-read [`../01-java/03-collections-generics.md`](../01-java/03-collections-generics.md), [`../01-java/05-exceptions-io.md`](../01-java/05-exceptions-io.md). |
| **DSA** | Cut Week 5 new problems from 8 to 5; add 6 Easy re-solves marked `Solved With Solution` (Week 5 Sat, 2 h). Time every solve; re-read [`../03-dsa/02-hashing.md`](../03-dsa/02-hashing.md) templates. |
| **SQL** | [`../04-sql-databases/sql-practice.md`](../04-sql-databases/sql-practice.md) joins + aggregation sets against `practice-schema.sql` (3 h over two Sundays). Write every FlowGrid list query by hand in `psql` before trusting JPA. |
| **Backend** | Rebuild one FlowGrid controller + service + slice test from a blank file without looking at the original (Week 5 Sat, 2 h). Re-read [`../05-spring-boot/04-validation-errors.md`](../05-spring-boot/04-validation-errors.md), [`../05-spring-boot/05-security-jwt.md`](../05-spring-boot/05-security-jwt.md). |
| **Git** | [`../02-git/exercises.md`](../02-git/exercises.md) rebase/stash/amend set (1 h). Use the PR workflow in [`../02-git/workflows.md`](../02-git/workflows.md) for every M2 task. |
| **Debugging** | Breakpoint in the FlowGrid request path and step through one request ([`../01-java/09-debugging-java.md`](../01-java/09-debugging-java.md)), 1 h. |
| **CS fundamentals** | [`../06-rest-apis/http-for-apis.md`](../06-rest-apis/http-for-apis.md) + `curl -v` against FlowGrid for every status code (1 h). |
| **Projects** | Finish M1 before M2 features. If M1 completion takes > 1 week, drop FlowGrid Advanced Version now (ROADMAP §13 rule 4). |
| **Interview** | Re-record prompt 6 after reading [`../16-interview-prep/project-deep-dive.md`](../16-interview-prep/project-deep-dive.md) (Week 5 Sat, 45 min). |

Two consecutive failures in the same area (here and CP-8) → freeze new features in that area for
one week.

---

## Results log

```
CP-4 — date: ____________   total time: ____ h

PART A1 problem 1: ____________ pattern: ______ time: __ min status: ________ swap? __
        problem 2: ____________ pattern: ______ time: __ min status: ________ swap? __
        idiom issues: ____________________  score: __/4
PART A2 Java rep problem: ____________ time: __ min compiled/passed? __  points __/2
PART B  Python __/6  Java __/7  Git __/3  SQL __/5  Spring/HTTP __/6  Build/test __/3  total __/30  score __/4
PART C  points __/5  time __ min  CI green? __  docs used: ______________  score __/4
PART D  prompts 1-6: __ __ __ __ __ __  average __  weakest prompt: __  score __/4
PART E  scope __/8  quality __/5  python foundation __/1  hours logged __  score __/4

AREA SCORES  Python __  Java __  DSA __  SQL __  Backend __  Git __  Debug __  CS __  Projects __  Interview __
DECISION     PASS / PASS WITH REMEDIATION / FAIL
REMEDIATION  areas: ____________  hours planned W5: __  W6: __
NOTES        (what surprised me; what I could not explain; what to change next week)
```

Copy area scores to [`./README.md`](./README.md#summary-log) and
[`../trackers/weekly-progress.md`](../trackers/weekly-progress.md).
