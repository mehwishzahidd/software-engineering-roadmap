# Checkpoint 12 — LedgerX M4: correctness proven

> **Gate question:** Can I reason about and *prove* transactional correctness, locking and
> idempotency (LedgerX M4)?

| | |
|---|---|
| **When** | Sunday of Week 12 (Part A + C on Saturday, Parts B/D/E + scoring on Sunday) |
| **Time** | ≈ 4 h: A 75 min · B 50 min · C 60 min · D 35 min · E 25 min · scoring 10 min |
| **Covers** | Weeks 9–12: double-entry accounting, `BigDecimal`/money, append-only tables, constraints/triggers, MVCC and DB internals; idempotency done properly, lock ordering, `@Transactional` propagation, isolation experiments; state machines, compensating entries, cursor pagination, audit, transactional outbox; failure injection, invariant tests, crash consistency; Python `psycopg` + `decimal` tooling; Trees, BST, Tries, Heap, Backtracking |
| **DSA patterns in scope** | Everything through Backtracking ([`../03-dsa/10-trees.md`](../03-dsa/10-trees.md) … [`14-backtracking.md`](../03-dsa/14-backtracking.md)) plus all earlier |
| **Project under review** | [LedgerX](../18-projects/ledgerx/README.md) through **M4** ([milestones.md](../18-projects/ledgerx/milestones.md)); v1.0 is due end of Week 13 |
| **Rules** | [Checkpoint rules](./README.md#rules) |

Passing here is the entry condition for **Internship-ready** in
[`../JOB_READINESS.md`](../JOB_READINESS.md): two projects, a transactions/concurrency story you
can prove, Easies solid, Mediums emerging.

---

## Part A — Timed coding (Track A · Python · 60 min · 2 Medium) + Java rep (15 min)

### A1 — 2 Medium in 60 min

**Selection rules:** two `Not Started` Mediums: one from *Trees/BST* (DFS/BFS, recursion), one
from *Heap*, *Backtracking* or *Binary Search on answer*. NeetCode 150 first, LeetCode topic
list otherwise. Swap anything recognised. Python 3.12, no cheatsheet.

**Clock:** 60 min total, target ≤ 30 min each. Say the approach out loud before coding. At the
bell, record how far each got.

**Python checks after the bell:** recursion depth risk handled (iterative or `setrecursionlimit`)?
`heapq` tuples with tie-breakers? Backtracking `append`/`pop` symmetric? No `list.copy()` inside
a hot loop?

### A2 — Java rep, 15 min

One already-solved Python *Tree* or *Heap* problem in Java: define `TreeNode`, recursive DFS with
correct base cases, or `PriorityQueue<int[]>` with a `Comparator.comparingInt`. `main`/JUnit on the
examples.

**Scoring**

| A1 (feeds DSA, Python) | Score |
|---|---:|
| 2/2 correct, each ≤ 30 min, complexity stated, idiomatic | 4 |
| 2/2 correct within 60 min total; or 1 correct + other's approach right with partial code | 3 |
| 1/2 correct in time | 2 |
| 1/2 correct over time, or both partial | 1 |
| 0 | 0 |

| A2 (feeds Java) | Points |
|---|---:|
| Compiles, passes, ≤ 15 min, correct generics/comparator | 2 |
| Passes late or with Javadoc for basic API | 1 |
| Fails | 0 |

---

## Part B — Knowledge questions (50 min · 28 questions)

Write first, then open. 1 point each, 0.5 partial. Max 28.

### Python for interviews (Q1–Q5)

**Q1.** Why must you never use `float` for money in the LedgerX verifier, and what does
`decimal.Decimal("0.10") + Decimal("0.20")` give versus `0.1 + 0.2`?
<details><summary>Answer</summary>
Binary floats cannot represent most decimal fractions: `0.1 + 0.2 == 0.30000000000000004`.
`Decimal` from *strings* is exact: `Decimal("0.30")`. Construct from strings (or ints), never from
floats; match the DB's `numeric(19,4)` scale; `quantize` when rounding. Java equivalent:
`BigDecimal` with explicit scale and `RoundingMode`.
</details>

**Q2.** `heapq.heappush(h, (dist, node))` — when do you get `TypeError: '<' not supported`,
and what is the fix?
<details><summary>Answer</summary>
When two entries share the same `dist` and `node` is an object without `__lt__`; tuple comparison
falls through to the second element. Fix: insert a unique counter or use a `@dataclass(order=True)`
with the non-comparable field marked `field(compare=False)`.
</details>

**Q3.** What is the difference between `a is b` and `a == b`, and why does `x is None` matter?
<details><summary>Answer</summary>
`is` compares identity (same object); `==` calls `__eq__`. `None` is a singleton, so `is None` is
the correct, fast, unambiguous check; `== None` can be overridden and misbehave (e.g. numpy).
Small ints and short strings may be cached, so `is` on them "works" by accident — never rely on it.
</details>

**Q4.** Give the average and worst-case complexity of dict lookup, and explain what makes an
object usable as a dict key.
<details><summary>Answer</summary>
Average O(1), worst O(n) under pathological collisions. Keys must be hashable: `__hash__` and
`__eq__` consistent, and effectively immutable (`str`, `int`, `tuple` of hashables, `frozenset`).
Lists/dicts/sets are unhashable; use `tuple(list)` or `frozenset`.
</details>

**Q5.** In backtracking with `path` as a list, compare `result.append(path)` vs
`result.append(path[:])`. Which is the bug?
<details><summary>Answer</summary>
`result.append(path)` stores a reference that keeps mutating as you backtrack — every result ends
up as the same (possibly empty) list. `path[:]` (or `list(path)`) snapshots it. Cost O(k) per
snapshot, unavoidable.
</details>

### Transactions, isolation, locking (Q6–Q12)

**Q6.** Define the four ANSI isolation levels and, for Postgres, which anomalies each actually
allows.
<details><summary>Answer</summary>
READ UNCOMMITTED (Postgres treats as READ COMMITTED), READ COMMITTED (default: non-repeatable
reads and phantoms possible; each statement sees a fresh snapshot), REPEATABLE READ (snapshot per
transaction; no non-repeatable reads or phantoms in Postgres, but serialization failures on
write–write conflict), SERIALIZABLE (SSI; detects dangerous read–write dependency cycles, aborts
with 40001; retry required).
</details>

**Q7.** Write the SQL that locks two wallet rows in a deterministic order for a transfer from
account 42 to account 7.
<details><summary>Answer</summary>

```sql
SELECT id, balance FROM account
WHERE id IN (7, 42)
ORDER BY id
FOR UPDATE;
```
Ordering by id inside one statement acquires locks smallest-first, so a concurrent 7→42 transfer
cannot deadlock with 42→7.
</details>

**Q8.** Optimistic locking with `@Version`: what does Hibernate emit, what exception do you get,
and what must the caller do?
<details><summary>Answer</summary>
`UPDATE … SET …, version = ? WHERE id = ? AND version = ?`; zero rows updated →
`ObjectOptimisticLockingFailureException` (`OptimisticLockException`). The caller retries the
whole unit of work in a *new* transaction (with a bound), or surfaces 409. Good for low contention;
under hot-account contention pessimistic wins because retries thrash.
</details>

**Q9.** MVCC: why does a reader never block a writer in Postgres, and what is the cost?
<details><summary>Answer</summary>
Each row version carries `xmin`/`xmax`; readers see the version visible to their snapshot while
writers create new versions. Cost: dead tuples, bloat and the need for `VACUUM`; long transactions
prevent cleanup; write–write still blocks.
</details>

**Q10.** `@Transactional(propagation = REQUIRES_NEW)` inside a `REQUIRED` method: what happens on
inner commit + outer rollback, and where is this legitimately used in LedgerX?
<details><summary>Answer</summary>
The inner transaction commits independently (suspends the outer); outer rollback does not undo
it. Legitimate: persisting the idempotency record's "in progress" marker or an audit/failure log
that must survive the business rollback. Dangerous when the inner writes depend on outer data
not yet committed (the inner cannot see it).
</details>

**Q11.** What is a lost update, and give the three-line invariant test that would catch it in
LedgerX.
<details><summary>Answer</summary>
Two transactions read the same balance and both write derived values; one write overwrites the
other. Test: start balance 500, two concurrent 400 transfers, assert exactly one succeeded, final
balance == 100, and `sum(entries) == balance` — plus never negative at any point.
</details>

**Q12.** Why does the ledger table forbid `UPDATE`/`DELETE`, and how did you enforce that in the
database (not the app)?
<details><summary>Answer</summary>
Append-only is the audit guarantee: history is corrections (compensating entries), never edits.
Enforce with a trigger `BEFORE UPDATE OR DELETE ON ledger_entry` that raises, and/or by revoking
UPDATE/DELETE privileges from the application role — so a bug in Java cannot violate it.
</details>

### Double-entry, idempotency, outbox (Q13–Q19)

**Q13.** In double-entry, what does "every journal transaction sums to zero" mean concretely,
and how is it a CHECK you can run?
<details><summary>Answer</summary>
Each journal txn has ≥ 2 entries; debits and credits (signed amounts) sum to zero across the
txn. Check: `SELECT journal_txn_id FROM ledger_entry GROUP BY 1 HAVING SUM(amount) <> 0` returns
no rows. A deposit debits `external_clearing` and credits the user wallet; money is never
created.
</details>

**Q14.** Materialised balance vs derived balance: why keep both, and what is the invariant?
<details><summary>Answer</summary>
Derived (`SUM(entries)`) is the truth but O(n) per read; materialised (`account.balance`) is O(1)
for reads and locking. Invariant: `account.balance == SUM(ledger_entry.amount WHERE account_id)`
for every account — checked by the reconciliation job and the independent Python verifier.
</details>

**Q15.** Idempotency store: what exactly is the "request fingerprint", and why store the
response status and body?
<details><summary>Answer</summary>
A hash (SHA-256) of the canonicalised body + method + path (+ user). Same key + same fingerprint
→ replay the stored status/body so the client gets an identical answer even for errors; same key
+ different fingerprint → 422. Storing the response means a retry after a network drop still gets
the *original* outcome.
</details>

**Q16.** A transfer commits but the process crashes before the idempotency record is marked
`COMPLETED`. What does the retry see and what must the design guarantee?
<details><summary>Answer</summary>
Either the marker and the transfer are in the *same* transaction (then a crash rolls back both and
the retry re-executes cleanly), or the marker is written first as `IN_PROGRESS` and the retry
finds it and must resolve: check whether the journal txn with that key exists (unique constraint
on `journal_txn.idempotency_key`) and finish the marker. Never double-post.
</details>

**Q17.** Why a reversal is a *new* compensating journal transaction rather than deleting the
original.
<details><summary>Answer</summary>
Immutability and audit: the original happened; the reversal records that it was undone, when, by
whom, linking `reverses_txn_id`. Balances derive correctly from both; reports for the original
period stay stable.
</details>

**Q18.** Transactional outbox: the problem it solves, the table, and the delivery guarantee.
<details><summary>Answer</summary>
Dual-write problem: committing to the DB and publishing an event are not atomic. Write the event
row into `outbox` in the same transaction as the business change; a relay polls (or uses logical
decoding), publishes, marks sent. Guarantee: at-least-once delivery → consumers must be
idempotent (event id).
</details>

**Q19.** Cursor pagination vs offset for transaction history: why cursor, and what is the
cursor made of?
<details><summary>Answer</summary>
Offset is O(offset) per page and shifts when rows are inserted. Cursor: `WHERE (created_at, id) <
(:ts, :id) ORDER BY created_at DESC, id DESC LIMIT n` on an index over the same columns —
stable and O(log n + n). Cursor = opaque base64 of `(created_at, id)`.
</details>

### Testing, failure injection, DB internals (Q20–Q25)

**Q20.** What is a "fault injection hook" in your codebase, and how do you make sure it can
never fire in production?
<details><summary>Answer</summary>
An interface (`FaultInjector.maybeFail("after-debit")`) with a no-op production implementation
and a test implementation that throws at named points; wired only under a `test`/`chaos` profile
(`@Profile`), and the chaos bean's presence asserted absent in a prod-config test.
</details>

**Q21.** Property-style invariant test: describe one for LedgerX in pseudo-code.
<details><summary>Answer</summary>
Generate a random sequence of deposits/withdrawals/transfers across K accounts (seeded RNG),
execute (some concurrently), then assert: every journal sums to zero, `balance == sum(entries)`
for all accounts, no account negative, total system money == deposits − withdrawals. Log the seed
on failure for reproduction.
</details>

**Q22.** What is a write-ahead log, and why is it the reason a committed transaction survives a
crash?
<details><summary>Answer</summary>
Changes are appended and fsynced to the WAL before the commit is acknowledged; data pages can be
written lazily. On restart, redo replays WAL records past the last checkpoint. Durability comes
from the fsync of the WAL, not from the data files.
</details>

**Q23.** How do you make the Python verifier *independent* — what would make it "trust the
Java code" by accident?
<details><summary>Answer</summary>
It reads raw tables via `psycopg` and recomputes sums with `Decimal`, using no Java-produced
aggregates, caches or API endpoints. It would be compromised by calling the `/balances` API,
reading the materialised `balance` column *as truth*, or reusing the Java query for sums.
</details>

**Q24.** In Testcontainers, why is one shared static container per test class (or JVM) preferred
over one per test, and what is the risk?
<details><summary>Answer</summary>
Start-up cost (seconds each). Risk: state leaking between tests — mitigate with
`@Transactional` rollback per test, truncation, or unique keys per test. Use `@ServiceConnection`
(Boot 3.1+) to wire the datasource.
</details>

**Q25.** What does `throughput` mean for LedgerX's measurement and what is the trap when
measuring transfers between few accounts?
<details><summary>Answer</summary>
Committed transfers per second at a given concurrency and account distribution, p50/p95 latency
recorded. Trap: hot accounts serialise on row locks, so "N threads" does not mean N× throughput —
report the account distribution (e.g. Zipf vs uniform) with the number.
</details>

### Git, Java, ops (Q26–Q28)

**Q26.** `git bisect`: what it does and when it beat reading the diff for you.
<details><summary>Answer</summary>
Binary search over commits between a known-good and known-bad commit, running a test each step
(`git bisect run mvn -q test -Dtest=X`). Beats reading diffs when the regression is far back or
the diff is large.
</details>

**Q27.** `BigDecimal`: why `new BigDecimal(0.1)` is wrong, what `equals` does with scale, and
how to compare amounts.
<details><summary>Answer</summary>
`new BigDecimal(0.1)` captures the binary float exactly (0.1000000000000000055…); use
`new BigDecimal("0.1")` or `valueOf`. `equals` compares scale too (`2.0 != 2.00`); use
`compareTo(...) == 0`. Set scale and `RoundingMode` explicitly for every division.
</details>

**Q28.** Explain what a Postgres `CHECK` constraint can and cannot enforce; give one LedgerX
example of each.
<details><summary>Answer</summary>
Can: per-row predicates (`amount <> 0`, `status IN (...)`, `balance >= 0`). Cannot: cross-row or
cross-table rules (journal sums to zero) — those need triggers, deferred constraints, or
application logic plus the reconciliation job.
</details>

**Part B scoring:** ≥ 23/28 → 4 · 19–22 → 3 · 14–18 → 2 · 9–13 → 1 · < 9 → 0. Q1–5 Python;
Q6–12 SQL/transactions; Q13–19 Backend; Q20–25 Testing/Debugging/CS; Q26–28 Git/Java.

---

## Part C — Practical task (Track B · 60 min)

Three parts, one clock. Work in a fresh branch `cp12/invariant-and-fault`.

### C1 — Write a concurrency test proving a LedgerX invariant (25 min)

Choose one invariant that your existing suite does **not** yet prove directly, for example:

- Concurrent *deposits* and *withdrawals* on the same wallet (mixed, 16 threads, 200 ops) never
  produce a negative balance and end with `balance == sum(entries)`.
- Two concurrent *reversals* of the same transaction → exactly one succeeds; the second gets 409
  and no entries are written.
- A transfer A→B and B→A running concurrently 50 times never deadlock (no 40P01 in logs) and
  total money is conserved.

Write it with Testcontainers Postgres, an `ExecutorService` + `CountDownLatch` start barrier,
and assertions on both the API/service outcome and the raw tables. It must be green and must
**fail** if you comment out the lock/ordering (verify this quickly — it is the point).

### C2 — Fix an injected fault (25 min)

Before starting the clock (Saturday morning), have a script or a friend inject **one** of these
into a copy of your LedgerX repo, without telling you which:

1. Remove `ORDER BY id` from the lock statement in the transfer service.
2. Change `REQUIRED` to `REQUIRES_NEW` on the ledger-entry write.
3. Make the idempotency conflict check compare only the key, not the fingerprint.
4. Change `compareTo` to `equals` in the "sufficient funds" check.
5. Swap debit/credit sign on the fee entry so journals no longer sum to zero.

Start the clock. Run the suite, read the failures (or notice none — some faults need your new
test or the verifier), locate the root cause, fix it, and write a regression test if none caught
it. Use the debugger and logs, not guesswork.

### C3 — Run the Python verifier (10 min)

Seed a database with your `tools/` transaction-data generator (≥ 1 000 txns), run the independent
reconciliation verifier, and paste its summary: journals checked, accounts checked, drift found
(must be 0). Then plant one bad row by hand (`INSERT` a lone entry via `psql` as a superuser) and
confirm the verifier flags it. `pytest tools/` green.

**Scoring (feeds Backend, Debugging, Java, Python)**

| Criteria | Points |
|---|---:|
| C1: test written, green, and proven to fail without the protection | 2 |
| C1: assertions include raw-table invariant, not only API result | 0.5 |
| C2: fault found and root cause explained in one sentence | 1 |
| C2: fixed + regression test within 25 min | 1 |
| C3: verifier runs clean and catches the planted drift; tests green | 1 |
| Whole task within 60 min | 0.5 |

6/6 → 4 · 4.5–5.5 → 3 · 3–4 → 2 · 1.5–2.5 → 1 · < 1.5 → 0.

---

## Part D — Explain out loud (35 min, recorded)

**LedgerX deep dive (12 min, no notes)** per [`../16-interview-prep/project-deep-dive.md`](../16-interview-prep/project-deep-dive.md).
Then probes from [`../18-projects/ledgerx/interview-questions.md`](../18-projects/ledgerx/interview-questions.md), e.g.:

1. "Draw (in words) the tables and explain how a $50 transfer becomes rows."
2. "You said $500 with two $400 transfers → one succeeds. Show me *how* the second one fails and
   at which line."
3. "Pessimistic vs optimistic — what did you measure, and when would you flip the choice?"
4. "Your service crashes right after the DB commit and before the HTTP response. Walk me through
   what the client, the idempotency store and the reconciliation job each see."
5. "What can your reconciliation job detect that the constraints can't? What can neither detect?"

**Résumé-defense (8 min):** three questions on PostgreSQL, JUnit and Spring Boot from
[`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md), answered with LedgerX
examples.

**Track A explain (5 min):** narrate one of the Part A Mediums — approach, complexity, the bug
your trace caught.

Score 0–4 each block. Deep dive counts double.

---

## Part E — Project review: LedgerX vs MVP (+ M3/M4 depth)

Check [`../18-projects/ledgerx/README.md`](../18-projects/ledgerx/README.md) and M1–M4 criteria in
[`../18-projects/ledgerx/milestones.md`](../18-projects/ledgerx/milestones.md).

**M1 — ledger core**
- [ ] `account`, `ledger_entry`, `journal_txn` with CHECK constraints; system accounts (`external_clearing`, `fees`)
- [ ] `ledger_entry` immutability enforced in the DB (trigger and/or revoked privileges) and tested
- [ ] Balances derived from entries; materialised balance with invariant check
- [ ] Deposits/withdrawals (simulated); auth

**M2 — idempotent concurrent transfers (MVP)**
- [ ] Idempotency store: key + fingerprint + status + response; conflict on same key/different body
- [ ] Transfers with ordered pessimistic locking; optimistic comparison documented with numbers
- [ ] Isolation-level experiments written up (what you saw at RC / RR / SERIALIZABLE)
- [ ] $500 / $400 / $400 concurrency test on Testcontainers, in CI

**M3 — states, reversals, history, audit, outbox**
- [ ] Journal state machine (pending/completed/failed/reversed/refunded) with illegal transitions rejected
- [ ] Refunds/reversals as compensating journals linked to the original
- [ ] Payment requests (create/accept/decline); cursor-paginated history; audit log
- [ ] Transactional outbox with relay and at-least-once semantics documented

**M4 — reconciliation, failure injection, invariants**
- [ ] Reconciliation job: every journal sums to zero; `balance == sum(entries)`; drift flagged and alertable
- [ ] Fault-injection hook (no-op in prod) with crash-between-steps tests
- [ ] Retry-after-crash idempotency tests
- [ ] Invariant suite (property-style, seeded)
- [ ] Throughput measurement recorded with account distribution and methodology

**Python component (`tools/`, M4)**
- [ ] Independent reconciliation verifier (`psycopg`, `Decimal`), README, type hints, `pytest`
- [ ] Transaction-data generator; consistency checker used in a failure exercise; referenced in TESTING.md

**Hygiene**
- [ ] CI green on every PR; small PRs; ADRs for locking strategy and outbox ([`../18-projects/templates/adr.md`](../18-projects/templates/adr.md))
- [ ] Hours logged ≈ 100–120 h so far (target 125–150 by end of W13)

**Scoring (feeds Projects):** all M1–M4 + Python → 4 · ≤ 2 missing, none in M2 → 3 · M2 complete
but ≥ 3 missing → 2 · M2 incomplete → 1 · M1 incomplete → 0.

---

## Scoring table

| Area | Evidence | Critical? | PASS threshold | My score |
|---|---|:---:|:---:|:---:|
| Python (coding) | A1 idiom/speed, B Q1–Q5, C3 tooling quality | yes | 3 | |
| Java (engineering) | A2, B Q27, C1/C2 code quality | yes | 3 | |
| DSA | A1 | yes | 3 | |
| SQL | B Q6–Q12, Q28; E (constraints/triggers) | yes | 3 | |
| Backend | B Q13–Q19, C1, E M2–M4 | yes | 3 | |
| Frontend | (optional admin view) — not scored unless built | — | — | — |
| Git | B Q26, PR/ADR hygiene in E | no | 3 | |
| Debugging | C2 | yes | 3 | |
| CS fundamentals | B Q9, Q22, Q24–25 | no | 3 | |
| Projects | E | yes | 3 | |
| Interview readiness | D | yes | 3 | |

## Decision

| Condition | Decision |
|---|---|
| All critical ≥ threshold, ≤ 1 non-critical below | **PASS** → *Internship-ready* once LedgerX v1.0 tags in W13 |
| All critical met, ≥ 2 non-critical below | **PASS WITH REMEDIATION** |
| Any critical below | **FAIL** — remediate in W13–14 review blocks |
| M2 concurrency test absent or not proven | **FAIL**; M4 work pauses until M2 is proven |
| Phase > 1 week behind | Drop LedgerX Advanced Version (scheduled payments / risk rules) |

---

## If you fail — remediation (Weeks 13–14 review blocks, ≈ 8 h)

| Area | Do this |
|---|---|
| **Python (coding)** | 6 Tree/Heap/Backtracking Mediums re-solved with 30-min clocks over two Saturdays; annotate each with the pitfall hit. Re-read [`../19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md) recursion/heap sections. |
| **Java (engineering)** | Java reps: one tree + one heap problem in W13; `BigDecimal` drill from [`../01-java/interview-questions.md`](../01-java/interview-questions.md) (1 h). |
| **DSA** | W13 new problems 8 → 5; Day-30 reviews for Trees/BST/Heap; one recorded think-aloud Medium per week ([`../INTERVIEW_CHECKLIST.md`](../INTERVIEW_CHECKLIST.md)). |
| **SQL** | Re-run the isolation experiments by hand in two `psql` sessions ([`../04-sql-databases/06-transactions.md`](../04-sql-databases/06-transactions.md)), 1.5 h; [`../14-cs-fundamentals/database-internals.md`](../14-cs-fundamentals/database-internals.md) MVCC/WAL sections. |
| **Backend** | Rebuild the idempotency store + transfer lock path in a scratch module from a blank file (2 h); [`../05-spring-boot/07-transactions.md`](../05-spring-boot/07-transactions.md) propagation table as flashcards. |
| **Debugging** | Two more injected faults (different ones) on Sat of W13 and W14, 30 min each, with root-cause notes; [`../21-debugging-code-reading/method.md`](../21-debugging-code-reading/method.md) — which starts in W13 anyway. |
| **Git** | Adopt ADRs for every ForgeCI decision; `git bisect` drill on a planted regression (30 min). |
| **CS fundamentals** | [`../14-cs-fundamentals/database-internals.md`](../14-cs-fundamentals/database-internals.md) + [`../14-cs-fundamentals/interview-questions.md`](../14-cs-fundamentals/interview-questions.md) DB section, 1.5 h. |
| **Projects** | Complete missing M2–M4 items in W13 before deploy polish; drop Advanced Version if > 1 week behind. |
| **Interview** | Re-record the LedgerX deep dive with the one-page outline; W13 deep-dive rehearsal counts only at ≥ 3. |

Second consecutive failure in an area (CP-8 + CP-12) → one-week feature freeze in that area.

---

## Results log

```
CP-12 — date: ____________   total time: ____ h

PART A1 medium 1: ______ __min reached: ______   medium 2: ______ __min reached: ______   score __/4
PART A2 java rep: ______ __min  points __/2
PART B  Py __/5 SQL/txn __/7 Backend __/7 Test/CS __/6 Git/Java __/3  total __/28  score __/4
PART C  C1 __/2.5 (fails without protection? __)  C2 fault #__ found in __ min __/2  C3 __/1  time __  score __/4
PART D  deep dive __/4  probes avg __  résumé drill __/4  track A __/4  score __/4
PART E  M1 __/4 M2 __/4 M3 __/4 M4 __/5 Python __/2 hygiene __/2  hours __  score __/4

AREA SCORES  Python __ Java __ DSA __ SQL __ Backend __ Git __ Debug __ CS __ Projects __ Interview __
DECISION     PASS / PASS WITH REMEDIATION / FAIL     Internship-ready criteria met? __
REMEDIATION  areas: ____________  hours W13: __  W14: __
NOTES
```

Copy to [`./README.md`](./README.md#summary-log), [`../trackers/weekly-progress.md`](../trackers/weekly-progress.md),
[`../JOB_READINESS.md`](../JOB_READINESS.md) scorecard.
