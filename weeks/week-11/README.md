# Week 11 — LedgerX M3: states, reversals, history, audit, outbox

[← Week 10](../week-10/) · [Roadmap](../../ROADMAP.md) · [Week 12 →](../week-12/)

**Phase 2 · LedgerX (Weeks 9–13)** — MVP exists. Now make it *operable*: explicit transaction states, refunds and reversals that never edit history, payment requests, a history API that scales, an audit log, and an outbox so events leave the system without dual-write bugs.

| Block | Hours | Notes |
|---|---:|---|
| Project (LedgerX M3) | 28 | State machine, compensating txns, payment requests, cursor pagination, audit, outbox |
| Learning | 6 | State machines, compensating entries, cursor pagination, audit design, transactional outbox |
| DSA (Heap / PriorityQueue) | 7 | 8 new + reviews |
| Interview / review | 4 | Think-aloud, drill, retro |

---

## 1. Main objective

By Sunday:

- every `journal_txn` moves through an explicit state machine (`pending → completed | failed`, `completed → reversed | refunded`) with illegal transitions rejected in code **and** guarded in the DB;
- a refund/reversal is a **new** journal txn whose entries mirror the original and which links to it (`reverses_txn_id`) — nothing is edited;
- users can create/accept/decline **payment requests**; `GET /wallets/{id}/history` uses **cursor pagination** and stays fast at 1M entries;
- every state change writes an **audit log** row; every completed txn writes an **outbox** row published by a poller (to a log/Redis stream/fake consumer) with at-least-once semantics.

## 2. Prerequisites

- Tags `m2`/`mvp` exist. If the optimistic comparison slipped, keep it parked — do not let it eat this week.
- You can write the ordered-lock transfer from memory (test yourself Monday, 10 min).
- Trees/BST done in Python: heaps add the `heapq` module (min-heap only; negate for max-heap; tuples compare element-wise) — see [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) §3.9.

## 3. Learning topics

| Topic | Subtopics | File |
|---|---|---|
| State machines in code and DB | enum + transition table, guard in service, `CHECK` + trigger on transitions, idempotent transitions | [`14-cs-fundamentals/software-engineering-practices.md`](../../14-cs-fundamentals/software-engineering-practices.md), [`05-spring-boot/03-data-jpa.md`](../../05-spring-boot/03-data-jpa.md) |
| Compensating entries | reversal vs refund (full vs partial), linking, double-reversal prevention, fee handling on refund | [`18-projects/ledgerx/README.md`](../../18-projects/ledgerx/README.md) |
| Cursor (keyset) pagination | `(created_at, id)` composite cursor, opaque base64 cursor, index design, why OFFSET dies | [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md), [`04-sql-databases/05-indexes-performance.md`](../../04-sql-databases/05-indexes-performance.md) |
| Audit log design | who/what/when/before/after, immutability (again), JSONB diff, correlation id | [`04-sql-databases/04-schema-design.md`](../../04-sql-databases/04-schema-design.md), [`05-spring-boot/06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md) |
| Transactional outbox | dual-write problem, outbox table in same txn, poller, at-least-once, consumer idempotency, ordering | [`15-system-design/fundamentals.md`](../../15-system-design/fundamentals.md), [`05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md) |
| Advanced SQL | window functions for running balance, `LATERAL`, `FOR UPDATE SKIP LOCKED` for the poller | [`04-sql-databases/03-advanced-queries.md`](../../04-sql-databases/03-advanced-queries.md) |

## 4. Concepts to learn

### 4.1 State machine — enum, transition table, DB guard

```java
public enum TxnState {
    PENDING, COMPLETED, FAILED, REVERSED, REFUNDED;

    private static final Map<TxnState, Set<TxnState>> ALLOWED = Map.of(
        PENDING,   EnumSet.of(COMPLETED, FAILED),
        COMPLETED, EnumSet.of(REVERSED, REFUNDED),
        FAILED,    EnumSet.noneOf(TxnState.class),
        REVERSED,  EnumSet.noneOf(TxnState.class),
        REFUNDED,  EnumSet.noneOf(TxnState.class));

    public boolean canTransitionTo(TxnState next) { return ALLOWED.get(this).contains(next); }
}
```

Service: `transition(txn, next)` throws `IllegalTransition` (→ `409`) if not allowed; sets `state`, `state_changed_at`; writes audit. DB guard: a `BEFORE UPDATE` trigger on `journal_txn` that raises when `(OLD.state, NEW.state)` is not in the allowed table — the second layer, same philosophy as Week 9.

- **Interview angle:** "Why keep the transition table in the enum rather than `if` chains?" → single source of truth, testable exhaustively with one parameterised test over all pairs.
- **Where LedgerX uses this:** every service; M4's crash tests inspect `pending` rows; M5's scheduled payments add `SCHEDULED → PENDING`.

### 4.2 Compensating journal transactions

A reversal of txn T (entries `+100 bob`, `−100 alice`) is txn R with entries `−100 bob`, `+100 alice`, `type = REVERSAL`, `reverses_txn_id = T.id`, and T's state → `REVERSED`. A **partial refund** of 30 is txn F with `−30 bob`, `+30 alice`, `refunds_txn_id = T.id`; T → `REFUNDED` (or keep `COMPLETED` and track refunded amount — decide and document). Prevent double reversal with a **partial unique index**:

```sql
CREATE UNIQUE INDEX ux_one_reversal_per_txn
  ON journal_txn (reverses_txn_id) WHERE reverses_txn_id IS NOT NULL;
```

Reversal must use the **same ordered locking** as a transfer (it moves money) and must reject when the receiver no longer has the funds (bob spent it) — design decision: allow negative on system accounts only; user wallets never go negative.

- **Interview angle:** "How do you fix a wrong transaction in an immutable ledger?" → You do not fix; you compensate. History stays true.
- **Where LedgerX uses this:** refunds, declined payment requests that were pre-authorised, M4 reconciliation checks that reversal pairs net to zero.

### 4.3 Payment requests

`payment_request(id, requester_wallet, payer_wallet, amount, currency, state, expires_at, resulting_txn_id)` with states `OPEN → ACCEPTED | DECLINED | EXPIRED`. Accept = a transfer from payer to requester with an idempotency key derived from the request id (`pr:{id}:accept`) — reusing M2 exactly. This is the pattern: *new features compose the primitives, they do not add new money paths*.

### 4.4 Cursor pagination

```sql
-- index that makes this O(page)
CREATE INDEX ix_entry_account_created_id ON ledger_entry (account_id, created_at DESC, id DESC);

SELECT id, created_at, amount, journal_txn_id
FROM ledger_entry
WHERE account_id = :acct
  AND (created_at, id) < (:cursor_created_at, :cursor_id)      -- row-value comparison
ORDER BY created_at DESC, id DESC
LIMIT :page_size + 1;                                            -- +1 to know if there's a next page
```

Cursor = base64 of `created_at|id`, opaque to clients. Response `{ items, next_cursor }`. Compare `EXPLAIN (ANALYZE, BUFFERS)` for this vs `OFFSET 900000 LIMIT 50` on 1M rows — write the numbers into `docs/PERFORMANCE.md`.

- **Interview angle:** "Why is `OFFSET` slow and why can it skip/duplicate rows?" → scans and discards N rows; inserts shift pages between requests. Keyset is stable and O(page).
- **Where LedgerX uses this:** history API; the admin view (optional React) consumes `next_cursor`; ForgeCI's log replay in Week 16 reuses the idea with sequence numbers.

### 4.5 Audit log

`audit_event(id, occurred_at, actor_id, action, entity_type, entity_id, before JSONB, after JSONB, correlation_id, request_id)`. Written in the **same transaction** as the change (so it cannot lie), immutable (trigger + `REVOKE`, same as entries). Correlation id comes from an `X-Request-Id` header or is generated in a filter and put in MDC so logs and audit rows join.

- **Interview angle:** "Difference between logs and an audit log?" → Logs are for operators, best-effort, unstructured, can be lost; audit is for compliance/users, transactional, immutable, queryable.

### 4.6 Transactional outbox

Dual-write problem: "commit DB then publish event" can publish without commit (or commit without publish). Fix: write `outbox_event(id, aggregate_type, aggregate_id, type, payload JSONB, created_at, published_at NULL)` **in the same transaction**; a poller publishes and marks `published_at`.

```sql
-- poller claims a batch without blocking other pollers
SELECT id, type, payload FROM outbox_event
WHERE published_at IS NULL
ORDER BY created_at
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

Semantics: **at-least-once** (crash after publish, before mark → re-publish). Consumers must be idempotent (dedupe on event id). Demonstrate with a fake consumer that stores seen ids in Redis (`SET NX`) or a table.

- **Interview angle:** "Why not publish inside the transaction?" → the broker call is not part of the DB transaction; you get phantom events on rollback and lost events on broker failure. Outbox makes publish *derived from* committed state.
- **Where LedgerX uses this:** `TxnCompleted`, `TxnReversed`, `PaymentRequestAccepted` events; M4 kills the app between publish and mark to prove at-least-once; ForgeCI's status events reuse the pattern.

### 4.7 Running balance with a window function (history view)

```sql
SELECT id, created_at, amount,
       SUM(amount) OVER (PARTITION BY account_id ORDER BY created_at, id) AS running_balance
FROM ledger_entry WHERE account_id = :acct ORDER BY created_at, id;
```

Useful for statements; expensive at scale — compute per page from the cursor's starting balance instead. Know both.

## 5. Resources

- PostgreSQL 16 docs: *Partial Indexes*, *Row-value comparison*, *Window Functions*, `SKIP LOCKED` in *SELECT* — https://www.postgresql.org/docs/16/
- microservices.io, *Transactional outbox* pattern — https://microservices.io/patterns/data/transactional-outbox.html
- Use The Index, Luke — *Paging* chapter (keyset pagination) — https://use-the-index-luke.com/no-offset
- Spring docs: `@Scheduled` and `TaskScheduler` — https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- *Designing Data-Intensive Applications* ch. 11 (event logs, exactly-once discussion) — Kleppmann
- NeetCode 150 — Heap / Priority Queue; LeetCode problems in §7

## 6. Exercises and assignments

### 6.1 Warm-up exercises (~2 h)

1. **Transition-table test (30 min).** Parameterised JUnit test over all 25 `(from, to)` pairs asserting `canTransitionTo` matches a hand-written truth table.
2. **Keyset vs offset (45 min).** Generate 1M entries for one account with `generate_series`; `EXPLAIN (ANALYZE, BUFFERS)` both queries at page 1 and page 18,000. Record buffers and ms.
3. **Outbox race on paper (20 min).** Draw the timeline for: publish → crash → restart. Show where duplicates arise and where the consumer dedupes.
4. **Heap warm-up (25 min, Python).** Implement a min-heap on a plain `list` with `push`/`pop`/`_sift_up`/`_sift_down`; verify against `heapq` on 10k random ops. Then note the `heapq` pitfall: `heapq.heappush(h, (priority, obj))` fails with `TypeError` when priorities tie and `obj` is not comparable — add an index as a tiebreaker.

### 6.2 Assignment — LedgerX M3

**Acceptance criteria:**

- [ ] `TxnState` machine with transition table, service guard (`409 IllegalTransition`), DB trigger guard; exhaustive test.
- [ ] `POST /transactions/{id}/reverse` and `POST /transactions/{id}/refund` (partial allowed) create compensating txns linked to the original; original state updates; double reversal → `409` from the partial unique index; receiver-insufficient-funds → `422`.
- [ ] Payment requests: create/accept/decline/expire (`@Scheduled` expiry); accept composes `TransferService` with a derived idempotency key.
- [ ] `GET /wallets/{id}/history?cursor=&limit=` keyset paginated, ≤ 5 ms at 1M rows for any page (measured), with `next_cursor`; includes running balance per page.
- [ ] Audit rows for every state change and every money movement, immutable, with correlation id joined to logs (MDC).
- [ ] Outbox table + poller (`SKIP LOCKED`, batch 100, every 500 ms) + fake idempotent consumer; test proves at-least-once + dedupe.
- [ ] ProblemDetail for all new errors; OpenAPI updated; CI green.

### 6.3 Break it

- Publish the event directly from the service (before commit) and throw after publishing. Observe the phantom event. Restore the outbox.
- Drop the partial unique index; fire two concurrent reversals of the same txn. Two reversals succeed — money created. Restore and keep the concurrency test.
- Replace the cursor with `OFFSET` and insert rows between page requests — show a duplicated item in the client's output.

### 6.4 Debug it

- The poller "never publishes": `@Scheduled` methods run, logs show `claimed 0` while rows exist with `published_at IS NULL`. Suspects: the poller runs inside a long-lived transaction with a stale snapshot; `@EnableScheduling` missing in tests; timezone mismatch in `created_at` comparison. Find it with `pg_stat_activity` and `SHOW timezone`.
- History returns the same page forever: cursor decoding drops the `id` half and ties on `created_at` (bulk-inserted rows share timestamps). Fix and add a test with identical timestamps.

## 7. DSA — Heap / PriorityQueue (8 new problems, in Python)

**Language: Python (Track A).** Guide: [`03-dsa/13-heap-priority-queue.md`](../../03-dsa/13-heap-priority-queue.md) · Templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md). Python: `heapq` is a min-heap on a list; max-heap = push `-x`; k-largest = `heapq.nlargest(k, xs)` or a size-k min-heap; `heapq.heapify` is O(n); there is no O(log n) arbitrary removal (use lazy deletion with a "dead" set — you will need this for Design Twitter and for ForgeCI's queue later).

| # | Problem | Difficulty | Time limit | Day |
|---|---|---|---|---|
| 703 | Kth Largest Element in a Stream | Easy | 15 min | Mon |
| 1046 | Last Stone Weight | Easy | 15 min | Mon |
| 973 | K Closest Points to Origin | Medium | 20 min | Tue |
| 215 | Kth Largest Element in an Array | Medium | 20 min (heap), then quickselect | Tue |
| 621 | Task Scheduler | Medium | 30 min | Wed |
| 355 | Design Twitter | Medium | 35 min | Thu |
| 295 | Find Median from Data Stream | Hard | 35 min | Thu |
| 1834 | Single-Threaded CPU | Medium | 30 min | Fri (if reviews done) |

Reviews due: Day-3 Week 10 Thu/Fri (Tries); Day-7 Week 10 BST; Day-14 Week 9 Trees; Day-30 Week 7 Linked Lists. Note that #1834 is literally a job scheduler — you will reuse the idea for ForgeCI's queue ordering.

**Java rep (Fri, ≤ 30 min):** #973 K Closest Points in Java with `PriorityQueue<int[]>` and `Comparator.comparingInt` (max-heap of size k via reversed comparator). The Java `PriorityQueue` API — `offer`/`poll`/`peek`, O(n) `remove(Object)` — is a Track B question in disguise ("how would you implement a job queue in Java?").

## 8. Project work — LedgerX M3 (States, reversals, history, audit, outbox)

Spec: [`18-projects/ledgerx/README.md`](../../18-projects/ledgerx/README.md) · [`milestones.md`](../../18-projects/ledgerx/milestones.md) (M3) · [`failure-engineering.md`](../../18-projects/ledgerx/failure-engineering.md)

### 8.1 Task checklist

- [ ] **Mon:** milestone `M3 — States, reversals, history, audit, outbox` + issues; `TxnState` + transition test; migration `V6__txn_states_and_links.sql` (state column constraints, `reverses_txn_id`, `refunds_txn_id`, partial unique index, transition trigger).
- [ ] **Tue:** `ReversalService`, `RefundService` (ordered locks reused), endpoints, tests incl. concurrent double reversal.
- [ ] **Wed:** payment requests (entity, states, accept composes transfers, decline, `@Scheduled` expiry) + tests.
- [ ] **Thu:** history endpoint with keyset pagination + running balance; 1M-row perf test (manual, numbers into `docs/PERFORMANCE.md`); audit log + MDC correlation filter.
- [ ] **Fri:** outbox table, poller with `SKIP LOCKED`, fake consumer with dedupe; at-least-once test.
- [ ] **Sat:** failure scenarios; ADR `0004-outbox-vs-direct-publish.md`; PR review; tag `m3`.

### 8.2 Acceptance summary

M3 done = §6.2 green, `docs/API.md` updated with the new endpoints and cursor contract, `docs/PERFORMANCE.md` has the keyset vs offset numbers, tag `m3`.

### 8.3 Verification tests you write

| Test | Type | Proves |
|---|---|---|
| `TxnStateTransitionTest` | unit, parameterised | full truth table |
| `TxnStateDbGuardIT` | Testcontainers | illegal `UPDATE journal_txn SET state` raises |
| `ReversalCreatesCompensatingTxnIT` | Testcontainers | mirrored entries, link set, original → `REVERSED`, zero-sum |
| `ConcurrentDoubleReversalIT` | Testcontainers, 2 threads | exactly one succeeds (`23505` → `409`) |
| `PartialRefundIT` | Testcontainers | 30 of 100 refunded; a second 80 refund rejected (over-refund) |
| `PaymentRequestAcceptIsIdempotentIT` | Testcontainers | accept twice → one transfer |
| `HistoryKeysetPaginationIT` | Testcontainers | no duplicates/gaps across pages with identical timestamps and concurrent inserts |
| `AuditWrittenInSameTxnIT` | Testcontainers | failed transfer → no audit row for "completed"; success → audit + txn both present |
| `OutboxAtLeastOnceIT` | Testcontainers + fake consumer | crash between publish and mark → re-published; consumer sees one effect |

### 8.4 Failure-engineering scenarios this week

M3 set: (1) phantom event from direct publish, (2) double reversal race, (3) poller crash between publish and mark, (4) consumer down for 10 minutes — does the outbox drain in order when it returns? (5) expiry job and accept race on a payment request at `expires_at` — which wins, and is the result auditable? Write-ups in `docs/FAILURES.md`.

### 8.5 GitHub expectations

Milestone `M3`, 8–10 issues; PRs `feat/m3-states`, `feat/m3-reversals-refunds`, `feat/m3-payment-requests`, `feat/m3-history-keyset`, `feat/m3-audit`, `feat/m3-outbox`. PRs for reversal and outbox must link to their failure write-ups. Tag `m3`.

## 9. Git activity

- Six feature branches this week — practise keeping PRs small (< 400 lines diff). Split the history PR into migration/index → query → endpoint if it grows.
- `git log --oneline --graph --all` daily; keep `main` linear (rebase-merge).
- Write one PR description using a template you add at `.github/pull_request_template.md` (what/why/how-tested/screens).

## 10. Interview preparation

Two tracks, never mixed: **Track A = coding interview, Python**; **Track B = software-engineering / résumé interview, Java/Spring/SQL/etc.**

- **Think-aloud (Tue, 45 min) — Track A, Python:** LeetCode 621 (Task Scheduler), recorded and scored.
- **Résumé-defense drill (Sat) — Track B — this week: REST APIs, Git, GitHub.** [`17-resume-tech-defense/rest-apis.md`](../../17-resume-tech-defense/rest-apis.md), [`git.md`](../../17-resume-tech-defense/git.md), [`github.md`](../../17-resume-tech-defense/github.md). Must cover: pagination styles, PR workflow, branch protection.
- **Project questions (Sat, 30 min) — Track B:** answer 5 from [`18-projects/ledgerx/interview-questions.md`](../../18-projects/ledgerx/interview-questions.md) on M1–M3 out loud; note the two weakest for Week 13's rehearsal.
- **Applications (Sun):** 2–3; update [`JOB_READINESS.md`](../../JOB_READINESS.md) self-assessment — you are approaching "internship-ready".

## 11. Revision work

- Re-derive the idempotency flow (M2) and check payment-request accept follows it exactly.
- Flashcards: outbox vs CDC, at-least-once vs at-most-once, keyset cursor contents, `SKIP LOCKED`, partial unique index.
- Re-solve one Week 9 tree problem timed (Day-14 review already covers this — pick the weakest).

## 12. Daily plan

| Day | Learning | Project | DSA | Interview / other |
|---|---|---|---|---|
| **Mon** (8h) | State machines; compensating entries (2h) | Milestone, `TxnState`, migration V6, transition tests (4.5h) | Heap §1–5, #703, #1046 (1.5h) | — |
| **Tue** (8h) | — | Reversal/refund services + endpoints + double-reversal test (5h) | #973, #215 + Day-3 reviews (2h) | Think-aloud #621 (1h) |
| **Wed** (8h) | Cursor pagination; keyset lab (2h) | Payment requests (4.5h) | #621 (1.5h) | — |
| **Thu** (8h) | — | History keyset + perf numbers; audit + MDC (5h) | #355, #295 + Day-7 reviews (2h) | `docs/API.md`, `docs/PERFORMANCE.md` (1h) |
| **Fri** (5h) | Outbox pattern (1h) | Outbox + poller + consumer + test (3h) | Reviews + Java rep #973 (+#1834 if time) (1h) | Retro prep |
| **Sat** (6h) | — | Failure scenarios, ADR 0004, PR review, tag `m3` (4h) | — | Drill + project questions (2h) |
| **Sun** (2–3h) | — | — | Day-14/30 reviews | End-of-week test, trackers, plan Week 12, applications |

## 13. End-of-week test (Sunday, 75 min)

**Part A — DSA (30 min, Python).** LeetCode **347. Top K Frequent Elements** in ≤ 20 min using `Counter` + `heapq.nlargest` (or a size-k heap), then say how bucket sort makes it O(n).

**Part B — Concepts (20 min).**

1. Why is a reversal a new transaction instead of deleting the original?
   <details><summary>Answer</summary>Ledger immutability: history must reflect what happened, including mistakes and their corrections; auditors and reconciliation depend on it. Deleting would also break derived balances at past points in time.</details>
2. How do you prevent two concurrent reversals of the same txn?
   <details><summary>Answer</summary>Partial unique index on `reverses_txn_id WHERE NOT NULL` (DB guarantee) plus locking the original txn row (`FOR UPDATE`) so the state check is serialized; map `23505` to `409`.</details>
3. Explain the dual-write problem in two sentences.
   <details><summary>Answer</summary>Writing to two systems (DB + broker) without a shared transaction means one can succeed while the other fails, producing events for data that never committed or committed data with no event. The outbox stores the event in the DB transaction and publishes afterwards.</details>
4. What are the exact contents of your history cursor and why two fields?
   <details><summary>Answer</summary>`created_at` and `id`, base64-encoded; timestamps are not unique (bulk inserts), so `id` breaks ties and makes `(created_at, id) < (…)` a total order with the matching composite index.</details>
5. The outbox poller published an event, then the app crashed before `published_at` was set. What happens?
   <details><summary>Answer</summary>The row is republished on restart → duplicate delivery. Guarantee is at-least-once; the consumer dedupes on event id.</details>
6. What goes in an audit event that does not go in a log line?
   <details><summary>Answer</summary>Actor, entity id, before/after state, transactional guarantee (written with the change), immutability. Logs may be sampled or lost; audit may not.</details>

**Part C — Practical (20 min).** Write from memory the keyset query with its index and the `SKIP LOCKED` claim query; then the `EnumSet` transition table for a 4-state machine of your choice.

**Part D — Explain (5 min).** "A customer says a transfer was wrong. Walk me through how your system corrects it and how an auditor could verify it later."

Pass: A in time · B ≥ 5/6 · C correct · D covers compensation, linking, state, audit, reconciliation.

## 14. Mastery checklist

- [ ] I can implement a state machine with code + DB guards and test it exhaustively.
- [ ] I can explain compensating entries and the double-reversal race and its fix.
- [ ] I can write keyset pagination with its index and quote my measured numbers vs offset.
- [ ] I can draw the outbox timeline, name the delivery guarantee, and explain consumer idempotency.
- [ ] Audit is transactional and immutable and joins to logs by correlation id.
- [ ] 8 heap problems done in Python; I can implement a heap by hand and explain `heapq` tie-breaking; reviews done; Java rep (#973) done.

## 15. Expected deliverables

- `ledgerx`: tag `m3`; `docs/API.md`, `docs/PERFORMANCE.md` (keyset numbers), `docs/adr/0004-outbox-vs-direct-publish.md`, `docs/FAILURES.md` (+5).
- Trackers: [`project-tracker.md`](../../trackers/project-tracker.md), [`dsa-tracker.md`](../../trackers/dsa-tracker.md), [`technology-tracker.md`](../../trackers/technology-tracker.md) (REST pagination, Postgres indexes → evidence), [`interview-tracker.md`](../../trackers/interview-tracker.md), [`weekly-progress.md`](../../trackers/weekly-progress.md).

## 16. If behind / stretch

**Behind?** Cuts in order: running balance per page → payment-request expiry job → #1834/#295 → partial refunds (keep full reversal). **Keep**: state machine + DB guard, reversal + double-reversal test, keyset history, outbox with at-least-once test — Checkpoint 12 asks about all four.

**Ahead?** Add `GET /audit?entity=…` with keyset pagination; add a tiny React/TS history view (optional per spec) consuming `next_cursor` — 3 h max; make the fake consumer a Redis Stream (`XADD`/`XREADGROUP`) as a preview of ForgeCI's queue decision.
