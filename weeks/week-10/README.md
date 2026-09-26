# Week 10 — LedgerX M2: idempotent, concurrent transfers

[← Week 9](../week-09/) · [Roadmap](../../ROADMAP.md) · [Week 11 →](../week-11/)

**Phase 2 · LedgerX (Weeks 9–13)** — this is the week LedgerX reaches **MVP**: money moves between wallets, retries are safe, and two concurrent $400 withdrawals from a $500 wallet leave exactly one winner and never a negative balance.

| Block | Hours | Notes |
|---|---:|---|
| Project (LedgerX M2) | 28 | Idempotency store, transfers, ordered pessimistic locking, optimistic comparison, isolation experiments, concurrency test |
| Learning | 6 | Idempotency done properly, lock ordering, retry semantics, `@Transactional` propagation pitfalls, isolation levels |
| DSA (BST + Tries) | 7 | 8 new problems + reviews |
| Interview / review | 4 | **Mock interview #1**, think-aloud, 3 résumé-defense questions |

---

## 1. Main objective

Ship `POST /transfers` such that:

- a client that retries with the same `Idempotency-Key` gets the **same response** and **no second movement of money**; the same key with a **different body** is rejected (`422`/`409` — you decide and document);
- concurrent transfers touching the same accounts **serialize correctly** via pessimistic locks taken **in a deterministic order** (no deadlocks), and you can show the measured difference against an optimistic `@Version` approach;
- a JUnit test proves: balance 500, two concurrent 400 transfers → exactly one `completed`, one `failed` with `InsufficientFunds`, final balance 100, zero-sum invariant intact.

## 2. Prerequisites

- Week 9 acceptance criteria green; tag `m1` exists. If withdrawal slipped, finish it **Monday morning** before anything else.
- You can explain FlowGrid's N-threads-one-unit test ([Week 5](../week-05/)) without looking. This week generalises it.
- Trees are comfortable in Python (BST problems assume you can traverse); you know the `TreeNode`/DFS templates in [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md).

## 3. Learning topics

| Topic | Subtopics | File |
|---|---|---|
| Idempotency done properly | key scope (per user), request fingerprint (SHA-256 of canonical body), stored response + status, in-progress state, TTL, key reuse with different body | [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md), [`06-rest-apis/http-for-apis.md`](../../06-rest-apis/http-for-apis.md) |
| Locking | `SELECT … FOR UPDATE`, `NOWAIT`/`SKIP LOCKED`, lock ordering to avoid deadlocks, deadlock detection in Postgres, `@Version` optimistic locking + retry | [`04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md), [`05-spring-boot/03-data-jpa.md`](../../05-spring-boot/03-data-jpa.md) |
| Isolation levels | READ COMMITTED vs REPEATABLE READ vs SERIALIZABLE in Postgres; serialization failures (`40001`), write skew | [`04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md), [`14-cs-fundamentals/database-internals.md`](../../14-cs-fundamentals/database-internals.md) |
| Spring transactions deep | propagation (`REQUIRED`, `REQUIRES_NEW`, `NESTED`), self-invocation, checked exceptions and rollback, `TransactionTemplate` | [`05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md) |
| Retry semantics | what is safe to retry, exponential backoff + jitter, retry budgets, `@Retryable` vs hand-written loop | [`05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md), [`15-system-design/fundamentals.md`](../../15-system-design/fundamentals.md) |
| Redis for idempotency (optional) | `SET NX PX` as a fast in-progress marker; DB stays the source of truth | [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md) |
| Concurrency testing | `ExecutorService` + `CountDownLatch` barriers, Testcontainers, flaky-test hygiene | [`09-testing/testcontainers.md`](../../09-testing/testcontainers.md), [`01-java/07-concurrency.md`](../../01-java/07-concurrency.md) |

## 4. Concepts to learn

### 4.1 Idempotency store — the real design

FlowGrid's M2 stored key + hash + response. LedgerX adds the **in-progress** state and the **conflict** rule:

```sql
CREATE TABLE idempotency_record (
  owner_id      UUID        NOT NULL,
  idem_key      TEXT        NOT NULL,
  fingerprint   CHAR(64)    NOT NULL,            -- SHA-256 hex of canonical request
  status        TEXT        NOT NULL CHECK (status IN ('IN_PROGRESS','COMPLETED')),
  http_status   INT,
  response_body JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at    TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (owner_id, idem_key)
);
```

Flow: `INSERT ... ON CONFLICT DO NOTHING` in its own short transaction (`REQUIRES_NEW`) → if inserted, you own the key: run the transfer, then store the response. If not inserted: load the row; fingerprint differs → `422 Unprocessable` (same key, different request); status `IN_PROGRESS` → `409 Conflict` with `Retry-After`; `COMPLETED` → replay stored status + body.

Canonical fingerprint: serialize the DTO with sorted keys, no whitespace, then SHA-256. Do **not** hash the raw request bytes (whitespace changes would defeat replay).

- **Interview angle:** "Why not just make the endpoint naturally idempotent?" → Transfers are not: "move 50" twice is 100. The key turns an unsafe POST into an exactly-once *effect* under retry.
- **Where LedgerX uses this:** transfers (M2), payment requests (M3), retry-after-crash tests (M4) rely on the `IN_PROGRESS` row surviving a crash.

### 4.2 Ordered pessimistic locking

Deadlock recipe: T1 locks A then B, T2 locks B then A. Cure: **always lock in a canonical order** (by primary key). Lock the `account_balance` rows, not `account` (the balance row is what you mutate), and lock them *before* reading balances.

```java
// Lock both balance rows in id order — deadlock-free by construction.
@Query("select b from AccountBalance b where b.accountId in :ids order by b.accountId")
@Lock(LockModeType.PESSIMISTIC_WRITE)
List<AccountBalance> lockAllByIdOrdered(@Param("ids") Collection<UUID> ids);
```

Note: `ORDER BY` with `FOR UPDATE` in Postgres locks rows in the sorted order for a simple single-table query. Verify with `EXPLAIN` and by reading the generated SQL in the logs (`spring.jpa.show-sql` is fine in tests; use `logging.level.org.hibernate.SQL=DEBUG` in dev).

Alternative escape hatch: `FOR UPDATE NOWAIT` (fail fast, retry at the app) vs waiting (default). Postgres's deadlock detector (`deadlock_timeout`, default 1 s) will abort one transaction with SQLSTATE `40P01` — your test should provoke this once with *unordered* locks so you have seen it.

- **Interview angle:** "How do you prevent deadlocks?" → Ordered acquisition; short transactions; timeouts; retry on `40P01`/`40001`.
- **Where LedgerX uses this:** `TransferService`; M3 reversals lock the same way; M4's fault hook sits between the lock and the write.

### 4.3 Optimistic comparison (`@Version`)

Implement the same transfer with `@Version` on `AccountBalance` and a retry loop on `OptimisticLockException` (max 5 attempts, jittered backoff). Measure under the same concurrency test: attempts, retries, wall time, failures. Write the result in `docs/DESIGN_DECISIONS.md` — this is the *measured* comparison you will cite in interviews.

- **Interview angle:** "When is optimistic locking better?" → Low contention, short critical sections, no long-held DB locks; worse under hot accounts (retry storms).

### 4.4 Isolation-level experiments

Run the transfer test under `READ COMMITTED` (default), `REPEATABLE READ`, `SERIALIZABLE` with **no explicit locks**. Record what happens: RC → lost update (both succeed, negative balance — the bug); RR → one transaction fails with `40001` on the balance update *only if you use UPDATE ... WHERE*, but a read-then-insert can still write skew; SERIALIZABLE → aborts one with `40001` (needs retry). Then re-run with `FOR UPDATE` under RC: correct with no retries. Fill the table:

| Isolation | Explicit lock | Outcome | Retries | Notes |
|---|---|---|---|---|
| RC | none | ? | ? | |
| RR | none | ? | ? | |
| SERIALIZABLE | none | ? | ? | |
| RC | `FOR UPDATE` ordered | ? | ? | |

- **Where LedgerX uses this:** the table goes in `docs/DESIGN_DECISIONS.md` and is a Checkpoint 12 question.

### 4.5 `@Transactional` propagation pitfalls

```java
@Service
public class TransferService {
    @Transactional                                  // REQUIRED: joins the caller's txn if any
    public TransferView transfer(TransferCommand cmd) { ... }

    @Transactional(propagation = Propagation.REQUIRES_NEW)   // own txn: commits even if the outer rolls back
    public void recordFailure(UUID txnId, String reason) { ... }
}
```

Pitfalls to reproduce this week: (1) calling `recordFailure` from inside `transfer` in the same class → proxy bypassed → *not* a new transaction → your failure record rolls back with the transfer; fix by moving to another bean or injecting `TransactionTemplate`. (2) Catching an exception inside a `@Transactional` method and returning normally → the transaction still commits (or is marked rollback-only by an inner `@Transactional`, giving `UnexpectedRollbackException`). (3) Checked exceptions do not roll back by default (`rollbackFor`).

- **Interview angle:** all three above are standard Spring questions; answer with the LedgerX case you reproduced.

### 4.6 Writing a trustworthy concurrency test

```java
@Test
void twoConcurrentTransfersOfFourHundredFromFiveHundred_exactlyOneSucceeds() throws Exception {
    seedWallet(alice, "500.0000");
    var start = new CountDownLatch(1);
    var pool  = Executors.newFixedThreadPool(2);
    List<Future<Result>> futures = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
        String key = "k-" + i;
        futures.add(pool.submit(() -> { start.await(); return attemptTransfer(alice, bob, "400.0000", key); }));
    }
    start.countDown();                                   // release both at once
    var results = futures.stream().map(this::get).toList();
    assertThat(results).filteredOn(Result::succeeded).hasSize(1);
    assertThat(balance(alice)).isEqualByComparingTo("100.0000");
    assertThat(brokenJournalTxns()).isEmpty();           // zero-sum still holds
}
```

Run it **20 times in a loop** (`@RepeatedTest(20)`) before trusting it. Use a real Postgres (Testcontainers) — H2 will lie to you about locking.

## 5. Resources

- PostgreSQL 16 docs: *Transaction Isolation* (13.2), *Explicit Locking* (13.3), *Deadlocks* — https://www.postgresql.org/docs/16/mvcc.html
- Spring Framework docs: *Transaction propagation*, *Understanding the Spring Framework's declarative transaction implementation* — https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html
- Stripe API docs, *Idempotent requests* (design reference for the header semantics) — https://docs.stripe.com/api/idempotent_requests
- IETF draft *The Idempotency-Key HTTP Header Field* (search the IETF datatracker by that title)
- *Designing Data-Intensive Applications*, ch. 7 (Transactions) — Kleppmann
- NeetCode 150 — Trees (BST) and Tries; LeetCode problems in §7

## 6. Exercises and assignments

### 6.1 Warm-up exercises (~2.5 h)

1. **Deadlock on purpose (30 min, psql).** Two sessions, two `account_balance` rows, opposite lock order. Observe `40P01`, note which session was killed and why (`pg_stat_activity`, server log).
2. **Lost update demo (30 min, psql).** Both sessions `SELECT balance` (500), both `UPDATE ... SET balance = 500 - 400`. Result: 100 with 800 moved. Then repeat with `SELECT ... FOR UPDATE` and with `UPDATE ... SET balance = balance - 400 WHERE balance >= 400` — explain why the last one is also safe.
3. **Propagation lab (45 min, Java).** A tiny `@SpringBootTest` with two beans reproducing pitfalls (1)–(3) in §4.5; assert the surprising behaviour, then the fix.
4. **Fingerprint kata (20 min).** Canonical JSON → SHA-256 hex; prove `{"a":1,"b":2}` and `{ "b":2, "a":1 }` produce the same hash.

### 6.2 Assignment — LedgerX M2

**Acceptance criteria:**

- [ ] `POST /transfers` with `Idempotency-Key` header (required; `400` if missing): moves money alice→bob as one journal txn (2 entries, or 3 with a fee if you model one).
- [ ] Replay: same key + same body → identical status and body, no new `journal_txn`. Same key + different body → `422`. In-progress → `409` + `Retry-After`.
- [ ] Both balance rows locked in id order; no deadlock under a 16-thread bidirectional test (alice↔bob) — proven by test and by a run with `log_lock_waits = on`.
- [ ] $500/$400/$400 test passes 20/20 repeats.
- [ ] Optimistic variant behind a profile/flag, same test passes, comparison table written.
- [ ] Isolation-level table (§4.4) completed with actual observations.
- [ ] Insufficient funds → `422` ProblemDetail; journal txn recorded as `failed` (via `REQUIRES_NEW` in a separate bean) so failures are auditable.
- [ ] Expired idempotency records cleaned by a scheduled job (`@Scheduled`, TTL 24 h) — small, but real.

### 6.3 Break it

- Reverse the lock order for one direction of transfer (alice→bob locks alice first, bob→alice locks bob first). Run the bidirectional test. Collect the deadlock error. Restore.
- Delete the `ON CONFLICT DO NOTHING` and rely on catching the unique-violation exception inside the *same* transaction. Watch `current transaction is aborted` errors. Understand why the idempotency insert needs its own transaction.
- Make the fingerprint from raw bytes; send the same request with different whitespace → false conflict.

### 6.4 Debug it

- The concurrency test passes alone, fails when the suite runs. Suspects: shared Testcontainers DB with leftover rows; test ordering; connection pool size 2 with 2 threads + 1 idempotency `REQUIRES_NEW` = **pool exhaustion deadlock** (classic!). Prove it with HikariCP's `leakDetectionThreshold` and by raising `maximumPoolSize`.
- Transfer succeeds but replay returns `409 IN_PROGRESS` forever: the completion write happened in the outer transaction that rolled back for an unrelated reason. Fix the ordering/propagation and add a test.

## 7. DSA — Binary Search Trees + Tries (8 new problems, in Python)

**Language: Python (Track A).** Guides: [`03-dsa/11-bst.md`](../../03-dsa/11-bst.md) · [`03-dsa/12-tries.md`](../../03-dsa/12-tries.md) · Templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) · finish Week 9 leftovers first if any. Trie node in Python: a class with `children: dict[str, TrieNode]` and `end: bool` (or a nested `dict` with a sentinel key — know both; the class version reads better in interviews). Use `float('-inf')`/`float('inf')` for BST bounds.

| # | Problem | Difficulty | Time limit | Day |
|---|---|---|---|---|
| 700 | Search in a Binary Search Tree | Easy | 10 min | Mon |
| 235 | Lowest Common Ancestor of a Binary Search Tree | Medium | 15 min | Mon |
| 98 | Validate Binary Search Tree | Medium | 25 min | Tue |
| 230 | Kth Smallest Element in a BST | Medium | 20 min | Tue |
| 105 | Construct Binary Tree from Preorder and Inorder Traversal | Medium | 30 min | Wed |
| 208 | Implement Trie (Prefix Tree) | Medium | 25 min | Thu |
| 211 | Design Add and Search Words Data Structure | Medium | 30 min | Thu |
| 1448 | Count Good Nodes in Binary Tree | Medium | 20 min | Fri (if reviews done) |

Reviews due: Day-3 of Week 9 Thu/Fri; Day-7 of Week 9 Mon–Wed; Day-14 of Week 8 recursion; Day-30 of Week 6 binary search. Explain #98's min/max-bound approach vs inorder approach out loud — both are interview-standard.

**Java rep (Fri, ≤ 30 min):** #208 Implement Trie in Java (`Map<Character, Node>` or `Node[26]`) — a data-structure design problem is the best kind of Java rep because it exercises classes, not just collections. Tick the tracker's "Java rep" column.

## 8. Project work — LedgerX M2 (Idempotent concurrent transfers) = MVP

Spec: [`18-projects/ledgerx/README.md`](../../18-projects/ledgerx/README.md) · [`milestones.md`](../../18-projects/ledgerx/milestones.md) (M2) · [`failure-engineering.md`](../../18-projects/ledgerx/failure-engineering.md)

### 8.1 Task checklist

- [ ] **Mon:** milestone `M2 — Idempotent concurrent transfers` + issues; migration `V5__idempotency_record.sql`; `IdempotencyService` (claim/complete/lookup in `REQUIRES_NEW`), fingerprint util + tests.
- [ ] **Tue:** `TransferService` with ordered `PESSIMISTIC_WRITE` lock, `InsufficientFunds` path with `failed` journal txn; `POST /transfers` + `IdempotencyFilter`/interceptor (or explicit service call — document the choice).
- [ ] **Wed:** concurrency test harness (Testcontainers, latch, repeated); $500/$400/$400 test; bidirectional 16-thread deadlock-freedom test.
- [ ] **Thu:** optimistic variant (`@Version` + retry) behind `ledgerx.locking=optimistic`; run both under the same harness; write comparison.
- [ ] **Fri:** isolation-level experiments and table; TTL cleanup job; `docs/DESIGN_DECISIONS.md` + ADR `0003-locking-strategy.md`.
- [ ] **Sat:** failure scenarios; PR self-review; tag `m2` and **`mvp`**.

### 8.2 Acceptance summary

MVP = §6.2 all green + docs written + CI green + tags. Money can be deposited, withdrawn, transferred; retries are safe; concurrency is proven, not assumed.

### 8.3 Verification tests you write

| Test | Type | Proves |
|---|---|---|
| `IdempotencyReplayIT` | Testcontainers + MockMvc | same key/body → same response, one journal txn |
| `IdempotencyConflictIT` | same | same key/different body → `422`; in-progress → `409` |
| `ConcurrentTransfersIT` (`@RepeatedTest(20)`) | Testcontainers | $500/$400/$400 exactly-one; balance 100; zero-sum |
| `BidirectionalTransfersNoDeadlockIT` | Testcontainers | 16 threads alice↔bob, no `40P01`, sum of balances constant |
| `OptimisticTransfersIT` | profile `optimistic` | same properties; records retry count |
| `IsolationLevelExperimentIT` | `@Disabled` by default, run manually | documents RC/RR/SER behaviour with no locks |
| `TransactionPropagationTest` | `@SpringBootTest` | failure record survives outer rollback |
| `IdempotencyExpiryTest` | unit + `@Scheduled` invoked directly | expired rows removed, live ones kept |

### 8.4 Failure-engineering scenarios this week

M2 set from `failure-engineering.md`: (1) pool exhaustion under `REQUIRES_NEW`, (2) deadlock with unordered locks, (3) app killed after idempotency claim but before transfer (leave `IN_PROGRESS`; what should the client see? decide: TTL-based unlock vs manual), (4) Postgres restarted during the 16-thread test — do any journals end up `pending`? (This sets up M3's state machine and M4's crash tests.) Write-ups in `docs/FAILURES.md`.

### 8.5 GitHub expectations

- Milestone `M2`, 7–10 issues; PRs: `feat/m2-idempotency`, `feat/m2-transfer-locking`, `test/m2-concurrency`, `feat/m2-optimistic-comparison`, `docs/m2-decisions`.
- PR descriptions include the *measured* numbers (runs, retries, timing) — copy them into `docs/DESIGN_DECISIONS.md`.
- Tags `m2`, `mvp`.

## 9. Git activity

- Keep the optimistic variant on a branch first; merge only after the comparison is written (real-world "spike then decide").
- Use `git stash` and `git worktree add ../ledgerx-opt` to run both variants side by side — 15 min practice.
- Rebase feature branches onto `main` daily; resolve at least one real conflict this week (there will be one in `TransferService`).

## 10. Interview preparation

- **Mock interview #1 (Sat, 60 min + 30 min review):** follow [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md) — one Medium (suggest LeetCode 98 or 230 family, not one you solved this week), a 10-minute "tell me about a project" (FlowGrid), score against [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md). Log score and 3 fixes in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Think-aloud (Tue, 45 min):** LeetCode 208 (Trie), recorded.
- **Résumé-defense drill — this week: Spring Boot, SQL, Redis.** [`17-resume-tech-defense/spring-boot.md`](../../17-resume-tech-defense/spring-boot.md), [`sql.md`](../../17-resume-tech-defense/sql.md), [`redis.md`](../../17-resume-tech-defense/redis.md). Must include: propagation, `FOR UPDATE`, Redis `SET NX`.
- **Applications (Sun):** 2–3 more early-stage applications; note any OA invitations — they set the priority for [`OA_PREP.md`](../../OA_PREP.md) reading in Week 16–17.

## 11. Revision work

- Re-implement FlowGrid's idempotency filter *from memory* on paper, then compare with LedgerX's — list 3 differences and why.
- Flashcards: propagation types, SQLSTATE `40001`/`40P01`/`23505`, HikariCP pool-exhaustion symptom, `compareTo` vs `equals`.
- Re-read Week 9 sign-convention ADR; confirm transfers obey it.

## 12. Daily plan

| Day | Learning | Project | DSA | Interview / other |
|---|---|---|---|---|
| **Mon** (8h) | Idempotency design; fingerprinting (2h) | Milestone/issues, idempotency table + service + tests (4.5h) | BST §1–5, #700, #235 (1.5h) | — |
| **Tue** (8h) | — | TransferService with ordered locks, endpoint, failure path (5h) | #98, #230 + Day-3 reviews (2h) | Think-aloud #208 (1h) |
| **Wed** (8h) | Locking + deadlocks; psql labs (2h) | Concurrency harness, $500/$400/$400, bidirectional test (4.5h) | #105 (1.5h) | — |
| **Thu** (8h) | — | Optimistic variant + comparison (5h) | Tries §1–5, #208, #211 + Day-7 reviews (2h) | `docs/DESIGN_DECISIONS.md` (1h) |
| **Fri** (5h) | Propagation lab (1h, counts as learning) | Isolation experiments, TTL job, ADR (3h) | Reviews + #1448 if time (1h) | Retro prep |
| **Sat** (6h) | — | Failure scenarios, PR review, tags `m2`/`mvp` (4h) | — | **Mock #1** + review (2h) |
| **Sun** (2–3h) | — | — | Day-14/30 reviews | End-of-week test, trackers, plan Week 11, applications |

## 13. End-of-week test (Sunday, 75 min)

**Part A — DSA (30 min).** LeetCode **1448. Count Good Nodes** (if not yet done) or **173. Binary Search Tree Iterator** in ≤ 25 min.

**Part B — Concepts (20 min).**

1. Same idempotency key, different body — which status and why?
   <details><summary>Answer</summary>`422` (or `409` — but be consistent): the key identifies *one* logical request; a different fingerprint means the client is misusing the key. Never execute; never replay the old response silently.</details>
2. Why must the idempotency claim run in `REQUIRES_NEW`?
   <details><summary>Answer</summary>So the claim commits (and becomes visible to concurrent duplicates) before/independently of the long transfer transaction, and so a unique-violation does not abort the main transaction.</details>
3. T1 holds lock on A and waits for B; T2 holds B and waits for A. What does Postgres do?
   <details><summary>Answer</summary>After `deadlock_timeout` it detects the cycle and aborts one transaction with SQLSTATE `40P01`; the other proceeds. Prevention: ordered locking.</details>
4. Under REPEATABLE READ with no explicit locks, two transactions each read balance 500 then insert a −400 entry and update the balance row. Outcome?
   <details><summary>Answer</summary>The second `UPDATE` of the same balance row fails with `40001` (could not serialize) — RR blocks lost updates on the *same row*. But if you only *insert entries* and never update a shared row, both succeed (write skew) — which is why the balance row lock matters.</details>
5. `@Transactional` method catches `RuntimeException` from an inner `@Transactional` bean call and returns 200. What happens at commit?
   <details><summary>Answer</summary>Inner proxy marked the transaction rollback-only; the outer commit throws `UnexpectedRollbackException` → client gets 500, not 200.</details>
6. Pessimistic vs optimistic for a hot "house" account receiving every fee?
   <details><summary>Answer</summary>Pessimistic (or batching fees asynchronously): optimistic would retry-storm on the hot row. Cite your measured retry counts.</details>

**Part C — Practical (20 min).** Write from memory: the JPQL/`@Lock` ordered-lock query, and the SQL of the idempotency claim (`INSERT ... ON CONFLICT DO NOTHING RETURNING ...`).

**Part D — Explain (5 min).** "Two users hit *Transfer* at the same moment on the same wallet. Walk me through what happens in your system, from HTTP to commit."

Pass: A in time · B ≥ 5/6 · C correct · D covers idempotency claim, ordered locks, balance check, commit, second request's fate.

## 14. Mastery checklist

- [ ] I can design an idempotency store on a whiteboard including in-progress, conflict and TTL.
- [ ] I can explain why ordered locking prevents deadlocks and show the query.
- [ ] I have *measured* pessimistic vs optimistic and can quote the numbers with methodology.
- [ ] I can list Postgres isolation levels and which anomaly each prevents, with my experiment table.
- [ ] I reproduced all three `@Transactional` pitfalls and can explain the proxy mechanism.
- [ ] Concurrency test is repeatable 20/20 and I know the pool-exhaustion trap.
- [ ] Mock #1 done, scored, with 3 concrete fixes.
- [ ] 8 BST/Trie problems done; reviews done.

## 15. Expected deliverables

- `ledgerx`: tags `m2`, `mvp`; `docs/DESIGN_DECISIONS.md` (locking comparison + isolation table), `docs/adr/0003-locking-strategy.md`, `docs/FAILURES.md` (+4 scenarios).
- Trackers updated: [`project-tracker.md`](../../trackers/project-tracker.md) (M2, MVP reached), [`dsa-tracker.md`](../../trackers/dsa-tracker.md), [`technology-tracker.md`](../../trackers/technology-tracker.md) (Spring transactions, Postgres locking → "can defend"), [`interview-tracker.md`](../../trackers/interview-tracker.md) (mock #1), [`weekly-progress.md`](../../trackers/weekly-progress.md).

## 16. If behind / stretch

**Behind?** Order of cuts: TTL cleanup job → isolation table (do the RC vs `FOR UPDATE` rows only) → optimistic comparison (move to Week 12's lighter learning slot) → #1448/#211. **Never cut** the $500/$400/$400 test or the idempotency replay/conflict tests — they *are* the MVP.

**Ahead?** Add a Redis `SET NX PX` fast-path in front of the DB claim and measure its effect on p50 latency under the 16-thread test (document; keep the DB as source of truth). Solve LeetCode 212 (Word Search II) as a Trie + backtracking preview.
