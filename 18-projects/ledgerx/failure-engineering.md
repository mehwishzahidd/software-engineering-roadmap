# LedgerX — Failure Engineering

> **Break it on purpose.** For each scenario: predict first, then **reproduce → observe →
> inspect logs → diagnose → fix / design around → regression test**. Do at least one scenario
> per week from Week 10 (Friday retro block), all of them by the end of Week 12 (CP-12), and
> re-run the regression tests before tagging `v1.0`. Record each drill in
> `docs/FAILURE_ENGINEERING.md` in your repo using the template at the bottom.
>
> Tools you will use: `docker compose`, `docker kill`, `psql` as superuser, `pg_stat_activity`,
> `pg_locks`, `pg_stat_database`, the `FaultInjector` hook, `kill -9`, the Python verifier
> (`tools/ledgerx_verify`) and checker (`tools/ledgerx_check`) as an independent witness, and
> your structured logs (`jq` over the JSON lines).

Spec: [`README.md`](./README.md) · Milestones: [`milestones.md`](./milestones.md) · Testing
background: [`../../09-testing/testcontainers.md`](../../09-testing/testcontainers.md) ·
Transactions: [`../../04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md),
[`../../05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md).

| # | Scenario | Milestone | Invariant under attack |
|---|---|---|---|
| 1 | Duplicate transfer request | M2 | at most one execution per key |
| 2 | Concurrent transfers from one account | M2 | wallet never below floor |
| 3 | App dies **before commit** | M4 | no partial ledger state |
| 4 | App dies **after commit, before response** | M4 | retry replays, no double charge |
| 5 | Retry occurs after crash (stale `IN_PROGRESS`) | M4 | exactly-once per key |
| 6 | Deadlock: A→B and B→A | M2 | no client-visible deadlocks |
| 7 | Idempotency store unavailable | M2/M4 | fail safe, never execute unrecorded |
| 8 | Balance drift detected by reconciliation | M4 | ledger wins; no auto-fix |
| 9 | Reversal of an already-reversed transaction | M3 | reversal at most once |
| 10 | Clock skew on scheduled payments | M5 | occurrence executes once |
| 11 | Outbox publisher crashes mid-batch | M3/M4 | no event lost |
| 12 | Database connection lost mid-transaction | M4 | rollback; pool recovers |

---

## Scenario 1 — Duplicate transfer request

**Predict.** Client sends the same `POST /transfers` twice (network retry, double-click, a
retrying SDK). Without idempotency: two journals, double charge. With it: identical response,
one journal.

**Reproduce.**
```bash
KEY=$(uuidgen); BODY='{"from":1001,"to":1002,"amount":"25.0000","currency":"USD"}'
for i in 1 2; do curl -s -i -X POST localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' -d "$BODY" | head -20; done
```
Then send the two requests *concurrently* (`&` + `wait`), and then a third with the same key
but `"amount":"26.0000"`.

**Observe.** Second sequential call: same status, same `transactionId`, header
`Idempotent-Replayed: true`. Concurrent: one executes, the other either replays (if it
arrived after finalization) or gets `409 IDEMPOTENCY_IN_PROGRESS` with `Retry-After`. Mutated
body: `409 IDEMPOTENCY_CONFLICT`. `SELECT count(*) FROM journal_transaction WHERE
idempotency_key_id = …` → 1. Run `python -m ledgerx_verify` → 0 findings.

**Inspect logs.** Filter by key: `jq 'select(.idempotencyKey=="…")' app.log`. Expect
`idempotency.reserved` once, `transfer.completed` once, `idempotency.replayed` once,
`idempotency.conflict` once.

**Diagnose (if it fails).** Two journals → the unique index on `(owner_id, key)` is missing or
the reservation happens after execution. Different `transactionId` on replay → response
stored before the id was assigned, or key finalized outside the main transaction.

**Fix / design around.** Reserve first in its own transaction; finalize inside the main
transaction; store the response JSON verbatim; fingerprint canonical JSON.

**Regression tests.** `transfer_sameKeySameBody_returnsSameResponseNoDoubleCharge`,
`transfer_sameKeyDifferentBody_returns409`, `transfer_sameKeyConcurrent_exactlyOneExecutes`,
plus the Python `test_generator_duplicate_keys_never_double_charge`.

---

## Scenario 2 — Concurrent transfers from one account

**Predict.** Balance 500; two concurrent 400s. Correct: one 201, one 422, balance 100. Wrong
implementations: both succeed (−300) or both fail (a bad lock).

**Reproduce.** Use `tools/ledgerx_gen --hot-source 1001 --concurrency 32 --ops 500` after
seeding 500, or the two-`curl`-in-background variant. Then run the M2 test with the funds
check deliberately moved *before* the `FOR UPDATE` to see the failure.

**Observe.** With the broken order: `account_balance.balance` negative? No — the floor trigger
raises `check_violation` on the second `UPDATE` and the transaction rolls back. Note this: the
DB constraint saved you even though the Java check was wrong, but the client got a 500
instead of a 422 (unmapped exception). Now break the trigger too (drop it in a scratch DB) to
see the −300.

**Inspect logs.** Two `transfer.fundsCheck.ok` lines with `available=500` a few ms apart =
the check ran on stale data. `pg_stat_activity` during the run shows one session
`wait_event_type = Lock` on `account_balance` — that is the queue working.

**Diagnose.** Funds check on an entity loaded before the lock (JPA first-level cache) or no
lock at all.

**Fix.** Read the balance from the ordered `FOR UPDATE` query result; map `check_violation`
from the floor trigger to `422 INSUFFICIENT_FUNDS` so even the last-line-of-defence path
returns the right error.

**Regression.** `transfer_whenTwoConcurrent400sOn500Balance_exactlyOneSucceeds`,
`transfer_whenNThreadsHammerOneAccount_neverNegativeAndSumZero`,
`balanceFloorTrigger_firesWhenJavaCheckBypassed`.

---

## Scenario 3 — Application dies during the transaction, before commit

**Predict.** Nothing is durable. The key remains `IN_PROGRESS` (reserved in its own committed
transaction). The client gets a connection reset.

**Reproduce (in-process).** `MapFaultInjector.failAt("transfer.afterEntries", () -> new
OutOfMemoryError("simulated"))` — an `Error` to mimic abrupt death (Spring rolls back on
`Error`). **Reproduce (real).** Run the app in Compose, add a `Thread.sleep(5000)` behind a
`ledgerx.debug.slowTransfer=true` property at `afterEntries`, fire a transfer, `docker kill
--signal=KILL ledgerx-app` during the sleep.

**Observe.** `psql`: no journal for the key; `ledger_entry` has no rows for it (they were
written in an uncommitted transaction whose backend died → Postgres rolled it back);
`idempotency_key.status = 'IN_PROGRESS'`. `tools/ledgerx_check snapshot` before and after:
balances identical. In Postgres logs: `unexpected EOF on client connection` and the
implicit rollback.

**Inspect logs.** Last app line is `transfer.entriesWritten` then nothing; no
`transfer.completed`. The retry (Scenario 5) is where the interesting behaviour lives.

**Diagnose.** If you *do* find entries without a journal or a balance moved without entries,
your steps ran in more than one transaction — self-invocation, `REQUIRES_NEW`, or a
`JdbcTemplate` on a different `DataSource`/connection than JPA. Check with
`logging.level.org.springframework.orm.jpa=DEBUG` and `…transaction=TRACE`.

**Fix.** One transactional entry point; all writers share the JPA-managed connection
(`JdbcClient` from the same `DataSource` participates via `DataSourceUtils`).

**Regression.** `crashAtEveryPoint_neverLeavesPartialLedgerState`,
`crashAfterEntriesBeforeBalance_retryIsConsistent`.

---

## Scenario 4 — Application dies after commit, before the HTTP response

**Predict.** Everything is durable *including* the finalized key. The client saw a failure
and will retry with the same key → replay of the stored 201, same `transactionId`.

**Reproduce.** `failAt("transfer.afterCommit", …)` — the hook must fire in
`TransactionSynchronization.afterCommit` or in the controller after the service returns.
Real variant: `docker kill` during a sleep placed in the controller after the service call.

**Observe.** Journal exists, entries exist, balances moved, key `COMPLETED` with
`http_status = 201` and `response_body`. Retry → 201, same body, `Idempotent-Replayed: true`.
Verifier: 0 findings. Checker: delta equals exactly one transfer for that key.

**Inspect logs.** `transfer.completed` present, no HTTP access log line for the first
request (died before writing), then `idempotency.replayed` on the retry.

**Diagnose (if double charge).** The key was finalized *after* commit in a separate
transaction — the crash left it `IN_PROGRESS`; the retry then executed again. Or the key TTL
was so short it expired between crash and retry.

**Fix.** Finalize inside the main transaction (README §8.3 step 4g). TTL ≥ 24 h.

**Regression.** `crashAfterCommitBeforeResponse_retryReplaysStoredResponse`.

---

## Scenario 5 — Retry after crash: the stuck `IN_PROGRESS` key

**Predict.** After Scenario 3, the key is `IN_PROGRESS` forever. A naive implementation
returns `409 IDEMPOTENCY_IN_PROGRESS` to every retry until the TTL — the customer's transfer
is blocked for 24 hours. The system needs a staleness rule.

**Reproduce.** Do Scenario 3, then retry immediately, then after your staleness timeout
(set it to 5 s in the `test` profile). Then retry *twice concurrently* after the timeout.

**Observe.** Immediate retry: 409 + `Retry-After`. After timeout: exactly one of the two
concurrent retries executes (takeover `UPDATE … WHERE status='IN_PROGRESS' AND updated_at <
…` affected 1 row for one of them, 0 for the other → that one sees `IN_PROGRESS` again with a
fresh `updated_at` → 409). Or, with the reaper design: the reaper flipped the key to `FAILED`,
and the first retry re-reserves it (key row reused: `UPDATE … SET status='IN_PROGRESS',
fingerprint=…` under the same unique row).

**Inspect logs.** `idempotency.staleTakeover {keyId, previousUpdatedAt}` exactly once.

**Diagnose.** Both retries executed → the takeover was a `SELECT` then `UPDATE` (check-then-
act) instead of a single conditional `UPDATE`; or `updated_at` was not bumped during
execution so a long-running legitimate request looked stale and was taken over while still
running (double execution!). Mitigation for the latter: takeover timeout ≫ max request time,
and `statement_timeout` on the main transaction shorter than the takeover timeout.

**Fix.** Atomic conditional update; heartbeat `updated_at` is unnecessary if the timeout is
comfortably larger than the transaction timeout (document the two numbers next to each other).

**Regression.** `idempotency_staleInProgressKey_isRecoverable`,
`crashAfterKeyReserveBeforeMainTxn_retryRecovers`, reconciliation finding
`STALE_IDEMPOTENCY_KEY` in `reconciliation_detects…` tests.

---

## Scenario 6 — Deadlock between A→B and B→A

**Predict.** Locking rows in call order (from, then to) makes T1 hold A wait for B while T2
holds B waits for A. Postgres detects it after `deadlock_timeout` (1 s) and kills one with
`40P01`. Symptoms: p99 ≥ 1 s, sporadic 500s.

**Reproduce.** Temporarily replace the ordered single query with two `findById(...)` +
`@Lock(PESSIMISTIC_WRITE)` calls in call order. Run 100 rounds of parallel A→B / B→A. Watch
`SELECT deadlocks FROM pg_stat_database WHERE datname='ledgerx'` climb. Capture a deadlock
in flight: `SELECT pid, wait_event_type, wait_event, query FROM pg_stat_activity WHERE state
<> 'idle'` and `SELECT * FROM pg_locks WHERE NOT granted`.

**Observe.** Postgres log: `ERROR: deadlock detected … Process 123 waits for ShareLock on
transaction 456; blocked by process 789 …` with both queries printed. App: `40P01` mapped to
500 (unmapped) or to `409 CONCURRENT_MODIFICATION` after retries.

**Diagnose.** Lock acquisition order differs between the two transactions.

**Fix.** Single `SELECT … WHERE account_id IN (lo,hi) ORDER BY account_id FOR UPDATE`. Keep
the `40P01` retry anyway (other deadlocks are possible, e.g. with the reversal path locking
`journal_transaction` then balances — make *that* path use the same account ordering too).
Verify with `pg_stat_database.deadlocks` unchanged over 1,000 rounds.

**Regression.** `transfer_aToBAndBToAConcurrently_noDeadlockBothSucceed`,
`retrier_onDeadlockSqlState_retriesUpToThreeTimes`.

---

## Scenario 7 — Idempotency store unavailable

**Predict.** In the v1.0 design the idempotency table is in the same Postgres as the ledger,
so "store unavailable" = "database unavailable" → nothing executes, `503
DEPENDENCY_UNAVAILABLE`. The interesting variant is the *optional Redis fast-path cache*
(if you added one) or a future split store: the system must **never execute a transfer it
could not record a key for**, because a retry would then double-charge.

**Reproduce.** (a) Postgres: `docker compose pause ledgerx-postgres` mid-load; observe the pool
time out (`HikariPool … request timed out`) and the API return 503 quickly (not hang for
30 s — tune `connectionTimeout`). Resume; confirm recovery without restart. (b) Redis (if
used): `docker compose stop redis`; observe the app fall back to Postgres for key lookup with
a WARN and continue correctly; rate limiting either fails open or closed per your ADR.

**Observe.** No journal without a finalized key; no key `COMPLETED` without a journal
(verifier check `idempotency keys with 201 ↔ exactly one journal`). Health endpoint reports
`DOWN` for the DB component during the pause.

**Inspect logs.** `dependency.unavailable {component: postgres}` at ERROR once per N seconds
(rate-limited), not once per request.

**Diagnose.** If any transfer executed while the key could not be written, your key write is
best-effort (try/catch around it) — that is a correctness bug, not resilience.

**Fix.** Key write is mandatory and inside the same transaction as the ledger; a
`DataAccessResourceFailureException` → 503 with `Retry-After`. Redis is only ever a cache in
front of the table.

**Regression.** `transfer_whenDatabaseUnavailable_returns503AndExecutesNothing` (Testcontainers
`pause()`), `risk_redisDown_fallsBackToSqlCheckFailClosed` (M5).

---

## Scenario 8 — Balance drift detected by reconciliation

**Predict.** Someone (a migration, an ops script, a bug) changes `account_balance` directly.
The ledger is untouched, so the ledger is right. Reconciliation must flag, not fix.

**Reproduce.** As superuser: `UPDATE account_balance SET balance = balance + 0.01 WHERE
account_id = 1001;`. Trigger `POST /admin/reconciliation/runs`. Also run
`python -m ledgerx_verify --json`.

**Observe.** One `BALANCE_DRIFT` finding (`expected = SUM(entries)`, `actual = 100.01`); the
Java job and the Python verifier agree to the cent — if they disagree, you have found a bug in
one of them, which is exactly why the verifier exists. Balance is *not* changed. Audit row
`RECON_FINDING`, outbox `ReconciliationFindings`, gauge `ledgerx.recon.findings = 1`,
CloudWatch alarm (on AWS). Subsequent transfers on 1001 still work (they use the drifted
materialized balance — decide whether a drifted account should be auto-frozen: a strong
argument says yes, since the funds check is now unreliable; write the ADR).

**Inspect logs.** `recon.run.finished {findings: 1, kinds: [BALANCE_DRIFT]}`.

**Diagnose.** Who changed it: `audit_event` has nothing (direct SQL bypasses the app) — which
is the lesson: the app role has no `UPDATE` on `account_balance` except through the service…
but it *must* update balances. So the protection is procedural: only `ledgerx_app` can
update, and only the ledger service uses that role. Consider `pgaudit` or a trigger writing
`account_balance_history` for forensic purposes.

**Fix / design around.** Resolution path: ADMIN reviews, posts a `RECON_ADJUSTMENT` journal
if money truly moved outside the ledger (rare), or **rebuilds the materialized row from the
ledger** via an explicit admin action (`POST /admin/accounts/{id}/rebuild-balance`, audited,
locks the row, sets `balance = SUM(entries)`). Never inside the job.

**Regression.** `reconciliation_detectsInjectedDrift_andDoesNotAutoFix`,
`test_verifier_detects_injected_drift`, `reconciliation_runsInSingleSnapshot_noFalseDriftUnderLoad`.

---

## Scenario 9 — Reversal of an already-reversed transaction

**Predict.** A second reversal would push money back the other way, effectively re-executing
the original transfer without the original's authorization. Must be `409 ALREADY_REVERSED`,
including under concurrency.

**Reproduce.** Reverse T once; call again with a *new* idempotency key (same key would just
replay). Then 8 concurrent reversals of a fresh transaction with 8 different keys.

**Observe.** Exactly one REVERSAL journal for T (`SELECT count(*) FROM journal_transaction
WHERE reversal_of = T AND type='REVERSAL'` → 1); the partial unique index rejects a second
insert even if the state check were skipped; balances restored exactly once; `state =
REVERSED`. Verifier check `REVERSAL_MISMATCH` clean.

**Inspect logs.** One `reversal.completed`, seven `reversal.rejected {code: ALREADY_REVERSED}`.

**Diagnose.** Two reversals succeeded → state read without locking the original journal row
and unique index missing.

**Fix.** `SELECT … FROM journal_transaction WHERE id = ? FOR UPDATE` before the state check;
keep the partial unique index; map `unique_violation` on it to `409 ALREADY_REVERSED`.

**Regression.** `reversal_ofReversedTxn_returns409`, `reversal_concurrentSameTxn_exactlyOneSucceeds`.

---

## Scenario 10 — Clock skew on scheduled payments

**Predict.** The app server's clock is 2 minutes ahead of the DB / another instance's clock;
or a container starts with a non-UTC timezone. Risks: an occurrence executes early, executes
twice (two instances with different "now"), or is skipped.

**Reproduce.** Run two app instances; use `faketime` (or a `Clock` bean overridden in a
profile with `+2m`) in one. Schedule a payment 1 minute ahead. Also set `TZ=America/New_York`
on one container and observe `next_run_at` handling.

**Observe.** The skewed instance picks the row early (`next_run_at <= now()` with its now).
Because the idempotency key is `sched:<id>:<occurrence_at>` and the unique index is on
`(schedule_id, occurrence_at)`, the second instance at the true time finds the occurrence
already executed → no duplicate. The payment ran 2 minutes early — acceptable within a
documented tolerance? Decide (e.g. compute "now" from the DB: `SELECT now()` inside the
scheduler transaction, so all instances share one clock).

**Inspect logs.** `scheduler.executed {scheduleId, occurrenceAt, executedAt}` — the
difference reveals the skew.

**Diagnose.** Comparisons in local time; JVM timezone ≠ UTC; multiple clocks.

**Fix.** Use the database clock for scheduling decisions; store `TIMESTAMPTZ`; `-Duser.timezone=UTC`;
occurrence-based keys. Catch-up policy after downtime documented.

**Regression.** `scheduler_twoInstancesSameTick_executeOccurrenceOnce`,
`scheduler_lateRun_executesAllMissedOrSkipsPerPolicy`, a unit test with a fixed `Clock`.

---

## Scenario 11 — Outbox publisher crashes mid-batch

**Predict.** Publisher takes 5 events, publishes 3, dies. At-least-once: after restart the
unpublished ones go out; depending on your design, the 3 may go out again. Nothing is lost.

**Reproduce.** `failAt("outbox.afterPublish:3", …)` (fault fires on the third event), or
`docker kill` the app while the publisher runs with a slow fake sink.

**Observe.** Design A (mark per event in its own transaction): rows 1–3 `published_at` set,
4–5 `NULL`; next run publishes 4–5 → total sent 5. Design B (one transaction per batch): all
five `NULL`; next run sends all five again → total sent 8, consumer dedupes by `event id`.
Either way, the consumer-side count of *distinct* ids is 5. Verify with a test sink that
records ids.

**Inspect logs.** `outbox.published {id}` ×3, crash, `outbox.published` ×2 (A) or ×5 (B).

**Diagnose.** If an event is *lost*: `published_at` was set before the publish call, or
publishing errors were swallowed. If a row is *stuck* forever: `FOR UPDATE SKIP LOCKED` lease
never released because the transaction is held open by a long publish — add a `statement_timeout`
and an `attempts` counter with a dead-letter threshold.

**Fix.** Publish → mark, per event, in one short transaction; `attempts++` on failure;
alert when `attempts > 5`.

**Regression.** `outbox_publisherCrashMidBatch_republishesUnpublishedOnly`,
`outbox_eventPublishedOnlyIfTransferCommitted`, `outbox_twoPublishersConcurrently_noDoublePublishWithinBatch`.

---

## Scenario 12 — Database connection lost mid-transaction

**Predict.** Postgres restarts (RDS failover, `docker restart`) while transfers are in
flight. In-flight transactions roll back on the server; the app sees `PSQLException: This
connection has been closed` → 503; Hikari evicts dead connections and recovers.

**Reproduce.** Run `ledgerx_gen --concurrency 16 --ops 2000`; halfway, `docker compose restart
ledgerx-postgres`. Afterwards run the verifier and the checker against the generator manifest.

**Observe.** A burst of 503s; then normal operation without an app restart. Verifier: 0
findings. Checker: every key with a 201 in the manifest has exactly one journal; keys with
503 have 0 journals *or* 1 (if the commit succeeded but the response failed — Scenario 4);
the generator's retry of 503s with the same key resolves each to exactly one journal.

**Inspect logs.** `HikariPool-1 - Failed to validate connection` then `Connection is not
available`; `dependency.unavailable`; recovery lines. No `transfer.completed` for keys that
ended `FAILED`.

**Diagnose.** If recovery required a restart: `maxLifetime`/`keepaliveTime` misconfigured, or
a connection cached outside the pool. If a key ended `COMPLETED` without a journal: your
finalization is outside the ledger transaction.

**Fix.** Sensible Hikari settings (`connectionTimeout 3s`, `validationTimeout 1s`,
`keepaliveTime 30s`), one transaction, 503 with `Retry-After`, client retries with the same key.

**Regression.** `transfer_whenDatabaseRestartsMidLoad_invariantsHoldAfterRecovery`
(Testcontainers `restart` — note this is slow; mark it `@Tag("slow")` and run nightly).

---

## Drill record template (`docs/FAILURE_ENGINEERING.md` in your repo)

```
### <n>. <scenario name>            Date:            Milestone:
Prediction (before running):
Reproduction steps / command:
Observed (DB state, HTTP responses, verifier output):
Log excerpts (redacted):
Root cause:
Fix or design decision (link ADR/PR):
Regression test(s):
Time spent:            Would I have caught this in review? Why not?
```

Keep the record honest — a drill that found nothing is still a drill; a drill that found a
bug is a story for [`interview-questions.md`](./interview-questions.md) and for the behavioral
bank in [`../../16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md).
