# LedgerX — Interview Questions

> Use this after each milestone (Sat interview block) and for the Week 13 deep-dive rehearsal.
> Answer **out loud**, from your own code, in ≤ 2 minutes each. Outlines are hidden in
> `<details>` — try first, then compare. Method: [`../../16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md).
> Technology grills that LedgerX defends: [`../../17-resume-tech-defense/postgresql.md`](../../17-resume-tech-defense/postgresql.md),
> [`../../17-resume-tech-defense/spring-boot.md`](../../17-resume-tech-defense/spring-boot.md),
> [`../../17-resume-tech-defense/junit.md`](../../17-resume-tech-defense/junit.md),
> [`../../17-resume-tech-defense/python.md`](../../17-resume-tech-defense/python.md).

Contents: [Basic](#basic) · [Intermediate](#intermediate) · [Advanced](#advanced) ·
[Deep-dive drill-downs](#deep-dive-drill-downs) · [System design](#system-design-questions) ·
[Troubleshooting](#troubleshooting-scenarios) · [Talk outlines](#talk-outlines)

---

## Basic

**B1. What does LedgerX do, in two sentences?**
<details><summary>Outline</summary>
A wallet backend where every money movement is an immutable double-entry journal transaction and balances are derived from the ledger. Its engineering focus is correctness under concurrency and failure: ordered locking, idempotent retries, compensating reversals, reconciliation and fault-injection tests.
</details>

**B2. Why double-entry instead of a `balance` column?**
<details><summary>Outline</summary>
Every movement has a source and destination, so money cannot be created or lost inside the system; the "sum to zero" rule is checkable by a query. A mutable balance loses history, cannot be audited, and is prone to lost updates. Balances are still cached, but the ledger is the truth.
</details>

**B3. What is a journal transaction versus a ledger entry?**
<details><summary>Outline</summary>
Journal = one business event (deposit, transfer, reversal) with type, state, links. Entries = the signed per-account legs belonging to it; ≥ 2 per completed journal; sum = 0. Entries are append-only.
</details>

**B4. Where do deposits come from if money can't be created?**
<details><summary>Outline</summary>
From the `external_clearing` system account, which represents the outside world. A deposit is `+100` on the wallet and `−100` on clearing; clearing's negative balance equals total net inflow — useful, not a bug.
</details>

**B5. Why `BigDecimal` and `NUMERIC(19,4)`? Why not `double`?**
<details><summary>Outline</summary>
Binary floating point cannot represent most decimal fractions exactly (`0.1 + 0.2`); errors accumulate and the ledger stops summing to zero. `NUMERIC` and `BigDecimal` are exact decimals. Scale fixed at 4; compare with `compareTo` because `equals` is scale-sensitive. An ArchUnit rule bans `double/float` in money packages.
</details>

**B6. Where are the transaction boundaries?**
<details><summary>Outline</summary>
On application-service methods (`TransferService.transfer`), not controllers or repositories. One transaction covers lock → checks → journal → entries → balance updates → audit → outbox → idempotency finalization. The idempotency *reservation* is a separate short transaction before it, on purpose.
</details>

**B7. What is an idempotency key and why is it required on every money-moving POST?**
<details><summary>Outline</summary>
A client-chosen unique id per logical operation. Stored with a request fingerprint, status and the response. Retries with the same key replay the stored response instead of re-executing; same key with a different body is a 409. It makes network retries safe.
</details>

**B8. What is the difference between a reversal and a refund in your system?**
<details><summary>Outline</summary>
Both are new compensating journals linked via `reversal_of`. Reversal = full negation, once only (partial unique index), original → `REVERSED`. Refund = partial negation, repeatable up to the original amount, original → `REFUNDED`.
</details>

**B9. What is `SELECT … FOR UPDATE`?**
<details><summary>Outline</summary>
A row lock held until commit/rollback; other writers (and other `FOR UPDATE` readers) block. Under `READ COMMITTED` it re-reads the latest committed version of the row after acquiring the lock, which is what makes the funds check after the lock trustworthy.
</details>

**B10. What does the reconciliation job check?**
<details><summary>Outline</summary>
Every journal sums to zero; every materialized balance equals the sum of its entries; global sum per currency is zero; completed journals have entries and failed ones don't; wallets aren't below their floor; suspense is empty; reversals negate originals; no stale in-progress keys. Findings are recorded and alerted, never auto-fixed.
</details>

---

## Intermediate

**I1. Walk me through a transfer from HTTP request to committed rows.**
<details><summary>Outline</summary>
Validate → authz (owner of `from`) → reserve key in own txn (`INSERT … ON CONFLICT`); branch on existing (replay / in-progress / conflict) → main txn: lock both `account_balance` rows in ascending id order in one `FOR UPDATE` statement → check ACTIVE/currency/funds → insert journal → insert 2 entries → update balances (+version) → audit + outbox rows → finalize key with response → COMMIT → respond 201. Failure at funds check commits a `FAILED` journal and a 422 stored on the key.
</details>

**I2. Pessimistic vs optimistic locking here — which and why?**
<details><summary>Outline</summary>
Pessimistic (`FOR UPDATE` in id order) as default: the check-then-write on two rows must be atomic, contention on hot accounts is expected, and lock waits queue fairly rather than thrash. Optimistic (`@Version`) implemented behind a flag for comparison: no DB lock during the check, but two-row version matching plus a retry loop; under a hot account the retry rate climbs and p99 explodes. Numbers from `docs/experiments/isolation-and-locking.md`.
</details>

**I3. Which isolation level and why not `SERIALIZABLE`?**
<details><summary>Outline</summary>
`READ COMMITTED` (Postgres default) plus explicit row locks. `SERIALIZABLE` (SSI) would also be correct without explicit locks but surfaces conflicts as `40001` errors that must be retried; on a hot account that is a lot of wasted work. `REPEATABLE READ` alone is *not* enough for check-then-write on two rows without locks (write skew). I use `REPEATABLE READ` for the reconciliation snapshot.
</details>

**I4. How do you prevent deadlocks between A→B and B→A?**
<details><summary>Outline</summary>
Acquire both locks in a canonical order (ascending account id) in one statement. Then the wait-for graph can't have a cycle. Postgres still has a detector (`deadlock_timeout` 1 s, kills one with `40P01`); I keep a bounded retry for residual cases (e.g. lock interaction with reversal paths) and measured `pg_stat_database.deadlocks` staying at 0 over 1,000 opposite-direction rounds.
</details>

**I5. Why reserve the idempotency key in a separate transaction, but finalize it inside the main one?**
<details><summary>Outline</summary>
Reserve separately so concurrent duplicates see `IN_PROGRESS` immediately instead of blocking on a unique index for the full transfer. Finalize inside so the key's `COMPLETED` status and the ledger rows are atomic: a crash after commit before response leaves a consistent replayable state; a crash before commit leaves nothing plus a stale `IN_PROGRESS` key handled by takeover/reaper.
</details>

**I6. What if the DB fails after entries are written but before the balance update?**
<details><summary>Outline</summary>
They're in the same transaction, so the failure rolls back the entries too — nothing partial is durable. The key stays `IN_PROGRESS`; the retry recovers via the staleness rule. Proven by `crashAfterEntriesBeforeBalance_retryIsConsistent` using the fault-injection hook at that exact point, plus the invariant "entry count ∈ {0,2}" after every crash point.
</details>

**I7. How does idempotency survive a crash after commit but before the response?**
<details><summary>Outline</summary>
The stored response and `COMPLETED` status committed with the ledger rows. The client's retry (same key) hits the replay path and receives the original 201 with the same `transactionId` and `Idempotent-Replayed: true`. No second execution. Test: `crashAfterCommitBeforeResponse_retryReplaysStoredResponse`.
</details>

**I8. How is immutability of the ledger enforced?**
<details><summary>Outline</summary>
Three layers: a `BEFORE UPDATE OR DELETE` (and `TRUNCATE`) trigger that raises; `REVOKE UPDATE, DELETE` from the app role; Hibernate `@Immutable` entity with no setters. Tests attempt each violation directly.
</details>

**I9. Explain the transactional outbox and its delivery guarantee.**
<details><summary>Outline</summary>
Events are inserted in the same DB transaction as the business change, so an event exists iff the change committed. A poller reads unpublished rows with `FOR UPDATE SKIP LOCKED`, publishes, marks. Crash between publish and mark → re-publish: at-least-once; consumers dedupe on event id. Avoids the "commit then publish" dual-write problem.
</details>

**I10. Why cursor pagination for history?**
<details><summary>Outline</summary>
Offset pagination scans and discards rows (`OFFSET 100000`) and shifts pages when new entries arrive. Keyset on `(account_id, id DESC)` with `WHERE id < :cursor` is an index range scan, stable under inserts. Cursor is opaque (base64). `EXPLAIN` in `PERFORMANCE.md`.
</details>

**I11. What is `@Transactional` self-invocation and did it bite you?**
<details><summary>Outline</summary>
Spring's proxy intercepts calls from *outside* the bean; `this.method()` bypasses it, so `@Transactional` on an internal method does nothing. I reproduced it in a test (two physical transactions visible in TRACE logs) and structured services so the public entry point owns the transaction.
</details>

**I12. Why an independent verifier in Python?**
<details><summary>Outline</summary>
The Java reconciliation and the Java tests share one author, one model and one `Money` class — a systematic bug (sign convention, rounding, `equals` vs `compareTo`) could be in both. The Python tool knows only the table contract, reads via a SELECT-only role, sums with `decimal.Decimal`, and reports findings. It caught/would catch cross-implementation disagreement; it's also the post-deploy gate in the release pipeline.
</details>

**I13. `Decimal` vs `float` in Python — concrete failure?**
<details><summary>Outline</summary>
`sum([0.1]*10) == 1.0` is `False` (0.9999999999999999). `Decimal("0.1")*10 == Decimal("1")` is `True`. psycopg 3 returns `NUMERIC` as `Decimal` by default; never `float()` it; JSON fixtures must be parsed with `parse_float=Decimal`. A test greps the package for `float(`.
</details>

**I14. How do you record a failed transfer without breaking the "journals sum to zero" rule?**
<details><summary>Outline</summary>
A `FAILED` journal has zero entries by definition; the invariant suite checks `COMPLETED ⇒ ≥ 2 entries` and `FAILED ⇒ 0`. It commits in the same transaction that locked the rows and finalized the key with a 422, so the retry replays the 422 rather than re-attempting.
</details>

---

## Advanced

**A1. Where does locking break at 100× load, and what are the alternatives?**
<details><summary>Outline</summary>
Hot rows (merchant wallet, `fees`) serialize: throughput ≈ 1/lock-hold-time, lock queue depth drives p99, deadlock checks add cost. Alternatives: (1) don't lock system accounts (no floor → atomic `UPDATE` suffices); (2) sub-accounts (`fees_00..15`) chosen by hash, aggregate on read; (3) single-writer queue per account (partitioned stream) — lock-free but async and operationally heavier; (4) shorten the locked section (no I/O inside). Measured baseline first, then pick.
</details>

**A2. How would you shard LedgerX?**
<details><summary>Outline</summary>
By `account_id` (owner). Same-shard transfers stay one transaction. Cross-shard transfers become a saga: debit into a per-shard `in_transit` holding account (local txn), credit on the other shard (local txn), with a settlement/outbox step and a reconciliation that checks holding accounts net to zero *eventually*. Idempotency key table sharded the same way. Global sum invariant becomes per-shard + holding accounts.
</details>

**A3. The hot account problem — a merchant receives 5,000 payments/s.**
<details><summary>Outline</summary>
Receiving has no floor check, so the credit leg can skip `FOR UPDATE` and use an atomic `UPDATE balance = balance + ?` — contention drops to row-level write serialization. Further: sub-accounts with periodic sweep; or batch credits (aggregate N incoming into one journal per interval — changes semantics, must be explicit). Never drop the sender-side lock.
</details>

**A4. Multi-region consistency for a ledger?**
<details><summary>Outline</summary>
Home-region per account (writes routed there; cross-region = cross-shard saga with holding accounts); reads from replicas with an `asOfEntryId` staleness marker; no active-active writes on the same account without consensus (Spanner-style) — too expensive for v1. Idempotency keys must be region-scoped or globally unique with home routing.
</details>

**A5. `SERIALIZABLE` in Postgres — how does SSI work and when would you choose it?**
<details><summary>Outline</summary>
Tracks read/write dependencies (SIREAD locks), aborts a transaction when a dangerous cycle *could* form; false positives possible; requires retry loops; no blocking. Good for complex multi-row invariants where explicit locking is error-prone and contention is low. For a two-row transfer with predictable contention, explicit locks are cheaper and more observable.
</details>

**A6. What would you redesign?**
<details><summary>Outline</summary>
Candidates: (1) store amounts as minor-unit `BIGINT` per currency for cheaper arithmetic; (2) partition `ledger_entry` by month for archival; (3) event-sourced projections for history rather than joins; (4) freeze accounts automatically on drift; (5) move the idempotency response cache to Redis with Postgres as source of truth only if latency demanded it; (6) a proper `pending → settled` two-phase model for external withdrawals from day one.
</details>

**A7. How do you guarantee exactly-once for scheduled payments across two instances?**
<details><summary>Outline</summary>
`FOR UPDATE SKIP LOCKED` on due rows; idempotency key derived from `(schedule_id, occurrence_at)`; unique index on the same pair; `next_run_at` advanced in the same transaction as the transfer; DB clock for "now". Two instances or a restart mid-run cannot produce two journals for one occurrence.
</details>

**A8. What does the deferred constraint trigger cost and why keep it?**
<details><summary>Outline</summary>
One aggregate over the journal's entries at commit (2–3 rows via `(txn_id)` index) — microseconds. It converts a class of application bugs into commit failures. Measured in M4; kept.
</details>

**A9. How do you test concurrency deterministically?**
<details><summary>Outline</summary>
Real Postgres via Testcontainers, `CountDownLatch` start barrier so threads hit the DB together, assertions on *outcomes and invariants* (exactly one 201, balance 100, sums zero) rather than on interleavings; loop 3–20× in CI without rerun-on-failure; fault-injection hook for exact crash points instead of timing.
</details>

**A10. Compare your ledger to how a bank core or Stripe-like system models money.**
<details><summary>Outline</summary>
Same fundamentals: double-entry, append-only, derived balances, idempotency keys, reconciliation against external statements. Real systems add: multi-currency with FX legs, pending/settled phases, T+n settlement, chart of accounts with normal balances, regulatory reporting, sharded/queued hot paths. LedgerX deliberately simulates the external side.
</details>

---

## Deep-dive drill-downs

Interviewers pick one thread and pull. Practise each chain until the fourth question is
comfortable.

**Chain 1 — Locking**
1. How do you prevent overdraft under concurrency? → 2. Why in id order? → 3. What does
`FOR UPDATE` do under `READ COMMITTED` exactly — what version of the row do you see? → 4. What
if the two accounts are on different shards? → 5. How did you *prove* it (test name,
assertions, how many runs)?

**Chain 2 — Idempotency**
1. What's stored per key? → 2. Why fingerprint the body? → 3. Same key, in-progress, second
request — what happens? → 4. Process dies after reserving the key — then what? → 5. Why not
put keys in Redis?

**Chain 3 — Crash consistency**
1. List the steps of a transfer. → 2. Crash after step N — what is durable? (for each N) →
3. How does the retry behave for each? → 4. How did you inject the crash *after commit*? →
5. What did the Python checker show?

**Chain 4 — Reconciliation**
1. What checks, in SQL? → 2. Why one `REPEATABLE READ` snapshot? → 3. Drift found — what
happens next, who acts, what's audited? → 4. Why a second verifier in Python? → 5. What if the
two disagree?

**Chain 5 — Reversals**
1. How is a refund represented? → 2. Receiver already spent the money? → 3. Two admins reverse
simultaneously? → 4. Reverse a reversal? → 5. Refund fees?

**Chain 6 — Outbox**
1. Why not publish after commit? → 2. Delivery guarantee? → 3. Two publishers? → 4. Crash mid-
batch? → 5. Ordering guarantees per aggregate?

---

## System-design questions

1. **Design a wallet service for 10M users.** Start from LedgerX; add sharding by account,
   holding accounts for cross-shard, read replicas for history, sub-accounts for hot system
   accounts, async outbox to a stream, idempotency keys partitioned by owner. State the
   invariants first, then the components.
2. **Add multi-currency.** Accounts have a currency; a cross-currency transfer is two legs in
   two currencies plus FX gain/loss legs so each currency sums to zero separately; FX rate
   table with versioning.
3. **Add external bank withdrawals (real, not simulated).** Two-phase: `pending_outbound`
   holding account, an outbox event to the bank adapter, a webhook/poll for settlement,
   compensating journal on failure, reconciliation against the bank statement file.
4. **Design the reconciliation for 1B entries.** Incremental by `last_entry_id`, partition by
   month, per-account checks parallelized by account range, snapshot via replica, findings as
   the only output.
5. **How would you expose this to a mobile client?** Idempotency keys generated client-side
   per tap, retry with backoff on 5xx/timeouts only, show pending state, history via cursor.

Use [`../../16-interview-prep/system-design-interview.md`](../../16-interview-prep/system-design-interview.md)
and [`../../15-system-design/junior-design-problems.md`](../../15-system-design/junior-design-problems.md).

---

## Troubleshooting scenarios (answer with a method, then a hypothesis)

1. **p99 latency on transfers jumped from 40 ms to 1.2 s after a deploy; throughput
   unchanged.** → Look for lock waits ≈ `deadlock_timeout` (1 s): `pg_stat_activity`
   wait events, `pg_stat_database.deadlocks`; suspect a new lock-order path (e.g. reversal
   service locking journal then balances in a different order) or an I/O call inside the
   locked section.
2. **Reconciliation flags drift on 200 accounts every night at 02:00, none during the day.**
   → Checks running in separate snapshots while the nightly scheduled payments run; or a
   nightly script touching `account_balance`. Verify with the Python verifier at 02:05 and
   with the audit log.
3. **A customer was charged twice; both journals have different idempotency keys.** → The
   client generated a new key per retry (client bug) — your system behaved correctly; show
   the audit trail; discuss server-side dedupe heuristics (same from/to/amount within N s →
   warn) and why they are not a substitute for keys.
4. **`UnexpectedRollbackException` in production logs, no user impact visible.** → An inner
   `REQUIRED` method threw and was caught; the outer commit then failed. Find the catch, decide
   whether the inner should be `REQUIRES_NEW` (e.g. writing the `FAILED` journal) or the outer
   should propagate.
5. **Outbox table growing to millions of rows.** → Publisher stuck (`attempts` climbing, a
   poison event) or no archival. Check `SELECT count(*) WHERE published_at IS NULL`, dead-letter
   the poison, add a retention job for published rows.
6. **Python verifier says OK, Java reconciliation says `UNBALANCED_TXN` for one journal.** →
   Compare the SQL: Java likely includes a `FAILED` journal with a stray entry or filters by
   currency differently. One of them is wrong — the disagreement is the finding.

---

## Talk outlines

### 10–15 minute deep dive (no notes)

1. **Problem (1 min):** naive `balance = balance - amount` → lost updates, no audit, no undo.
2. **Model (2 min):** accounts incl. system accounts; journal + entries; sum-to-zero; derived
   balances with verified cache; draw deposit and transfer on the whiteboard.
3. **The transfer path (3 min):** the sequence with the transaction boundary box; lock order;
   idempotency reserve-then-finalize; failed-transfer handling. Name the isolation level and
   why.
4. **Proving it (3 min):** the $500/$400/$400 test; A→B/B→A deadlock test; fault-injection
   crash points and the retry table; invariant suite; reconciliation; the Python verifier as an
   independent witness.
5. **Numbers (1–2 min):** measured transfers/s and p95 on hot vs spread accounts, environment
   and method stated honestly; where it was bounded (lock hold time on the hot row).
6. **Failures I engineered (1–2 min):** pick two from `failure-engineering.md` you actually
   ran — what broke, root cause, fix, regression test.
7. **What I'd change at scale (1 min):** sub-accounts, holding accounts across shards,
   single-writer queues; what I deliberately left out and why.

### 2-minute version

"LedgerX is a wallet backend built as an immutable double-entry ledger: every deposit,
transfer or refund is a journal transaction whose entries sum to zero, balances are derived
and verified, and nothing is ever updated or deleted — the database itself rejects it. The
hard part was transfers under concurrency and retries: I lock both balance rows in id order
inside one transaction so a $500 balance with two concurrent $400 transfers gets exactly one
success, and every money-moving request carries an idempotency key that's finalized in the
same transaction as the ledger rows, so a crash after commit but before the response is
replayed, not re-executed. I proved that with fault-injection tests at every step of the
transfer, an invariant suite over random concurrent workloads, a reconciliation job, and an
independent Python verifier that re-derives every invariant from the raw tables. On [env] it
sustained [N] transfers/s at p95 [X] ms on a single hot account and [M]/s spread across
accounts — the hot-account limit is lock hold time, and I can talk about sub-accounts and
per-account queues as the next step."

Fill the brackets only with numbers from your own `PERFORMANCE.md`.
