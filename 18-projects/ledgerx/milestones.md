# LedgerX — Milestones M1–M5

> One milestone ≈ one week ≈ one GitHub milestone with issues ≈ one or more PRs.
> Each milestone: **what to know first → requirements → architectural guidance → acceptance
> criteria → now implement it → verification tests → debugging scenarios → interview questions.**
> Project hours per week ≈ 26–28 (see [`../../ROADMAP.md`](../../ROADMAP.md) §5). Spec in
> [`README.md`](./README.md); failure drills in [`failure-engineering.md`](./failure-engineering.md).

| Milestone | Week | Tag | Theme |
|---|---|---|---|
| [M1](#m1--ledger-core-week-9) | 9 | — | Ledger core: accounts, immutable entries, journals, balances, deposits/withdrawals, auth |
| [M2](#m2--idempotent-concurrent-transfers-week-10) | 10 | `v0.5` MVP | Idempotency store, ordered locking transfers, isolation experiments, the $500/$400/$400 test |
| [M3](#m3--states-reversals-history-audit-outbox-week-11) | 11 | — | State machine, compensating reversals/refunds, payment requests, cursor history, audit, outbox |
| [M4](#m4--reconciliation-failure-injection-invariants-week-12) | 12 | — | Reconciliation, fault-injection hook, crash/retry tests, invariant suite, Python independent verifier, throughput |
| [M5](#m5--deploy-docs-advanced-week-13) | 13 | `v1.0` / `v1.1` | AWS deploy, docs, scheduled payments, risk rules |

Conventions used below: **FR-n / NFR-n** refer to [`README.md`](./README.md) §4–5; hour
estimates are for the project block only; `- [ ]` lists are meant to be copied into GitHub issues.

---

## M1 — Ledger core (Week 9)

### 1. What to know first (~7 h learning block this week)

- Double-entry model: [`README.md`](./README.md) §3 — read it twice, do the worked examples on
  paper for your own account names before writing any code.
- Transactions, MVCC, `READ COMMITTED` semantics, row locks:
  [`../../04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md).
- Constraints, triggers, `DEFERRABLE INITIALLY DEFERRED`, roles and `REVOKE`:
  [`../../04-sql-databases/04-schema-design.md`](../../04-sql-databases/04-schema-design.md),
  [`../../04-sql-databases/03-advanced-queries.md`](../../04-sql-databases/03-advanced-queries.md).
- WAL, MVCC and why an append-only table is cheap for Postgres:
  [`../../14-cs-fundamentals/database-internals.md`](../../14-cs-fundamentals/database-internals.md).
- `BigDecimal` scale/rounding/`compareTo`; records for value types:
  [`../../01-java/04-modern-java.md`](../../01-java/04-modern-java.md).
- JPA mapping of `NUMERIC`, native queries, when to bypass JPA:
  [`../../05-spring-boot/03-data-jpa.md`](../../05-spring-boot/03-data-jpa.md),
  [`../../04-sql-databases/08-jdbc-orm.md`](../../04-sql-databases/08-jdbc-orm.md).
- Reuse from FlowGrid (do not re-learn): Flyway, ProblemDetail handler, JWT, springdoc,
  Testcontainers base class, GitHub Actions `ci.yml`.

### 2. Requirements

FR-1 … FR-10 (users, accounts, system accounts, statuses, journal + entries, immutability,
derived + materialized balances, no overdraft, deposits, withdrawals with optional fee),
NFR-2, NFR-6, NFR-9. Idempotency-Key is **accepted and stored** already in M1 but the full
conflict/replay semantics arrive in M2 — for M1 it is enough that the header is required and a
duplicate key on the same endpoint returns the original response (unique constraint + replay).

### 3. Architectural guidance

**Schema first, code second.** Write `V1__users_accounts.sql`, `V2__ledger.sql`,
`V3__balances.sql`, `V4__roles.sql` by hand. JPA entities are then written *to match* the
schema (`spring.jpa.hibernate.ddl-auto=validate`, never `update`).

**The `ledger` package is the only writer.** A `LedgerService.post(JournalDraft)` method takes
a draft (type, initiator, list of `(accountId, Money)` legs), validates that legs sum to zero
and there are ≥ 2 legs, inserts the journal, inserts entries, and updates `account_balance`
rows. Deposits and withdrawals are thin services that build a draft with `PostingRules` and
call `post`. Transfers (M2) do the same after taking locks. No other package touches
`LedgerEntryRepository`.

**Where is the transaction boundary?** On the *application service* method
(`DepositService.deposit(...)`), not on the controller and not on the repository. Everything
that must be atomic — journal, entries, balance update, audit row — happens inside. Read
[`../../05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md) on
self-invocation: a `@Transactional` method called from another method of the same bean is
**not** proxied. Structure services so that the public entry point is the transactional one.

**Balance update in M1.** Deposits only add money and withdrawals only affect one wallet, so
in M1 a plain `UPDATE account_balance SET balance = balance + ?, version = version + 1 WHERE
account_id = ?` is enough for deposits. For withdrawals you must already prevent overdraft:
`SELECT ... FOR UPDATE` the single wallet row, check funds, then update. (M2 generalizes to two
rows in id order.) The DB trigger `account_balance_floor` (README §9.2) is the last line of
defence — write the test that proves it fires even if the Java check is bypassed.

**System accounts.** Seeded in `V1` with fixed ids (e.g. 1 = `external_clearing`, 2 = `fees`,
3 = `suspense`) and `min_balance = -999999999999999.9999` (or `NULL` meaning unbounded — decide
and make the trigger handle it). Wallets get `min_balance = 0`.

**Immutability in three layers.** (1) trigger raising on `UPDATE`/`DELETE`/`TRUNCATE`;
(2) `REVOKE` from `ledgerx_app`; (3) JPA entity for `LedgerEntry` has no setters and is marked
`@Immutable` (Hibernate) so accidental dirty-checking never issues an `UPDATE`.

**Money.** A `Money` record (`BigDecimal amount`, `Currency currency`) that normalizes scale
to 4 on construction, rejects > 4 decimals, and exposes `plus`, `minus`, `negate`, `isPositive`,
`compareTo`. Jackson serializes it as a string. No `double` anywhere (ArchUnit rule in M1).

**Derived vs materialized.** Expose `GET /accounts/{id}?verify=true` (ADMIN) returning both
`balance` (materialized) and `derivedBalance` (`SUM(amount)` native query) plus `consistent:
true|false`. This is your first invariant check; M4 turns it into a job.

Tiny reference snippets (≤ 25 lines each):

```sql
-- V2__ledger.sql (excerpt): deferred balanced-journal constraint trigger
CREATE OR REPLACE FUNCTION journal_must_balance() RETURNS trigger AS $$
DECLARE s NUMERIC(19,4);
BEGIN
  SELECT COALESCE(SUM(amount),0) INTO s FROM ledger_entry WHERE txn_id = NEW.txn_id;
  IF s <> 0 THEN
    RAISE EXCEPTION 'journal % does not balance (sum=%)', NEW.txn_id, s
      USING ERRCODE = 'integrity_constraint_violation';
  END IF;
  RETURN NULL;
END $$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_journal_balance
  AFTER INSERT ON ledger_entry DEFERRABLE INITIALLY DEFERRED
  FOR EACH ROW EXECUTE FUNCTION journal_must_balance();
```

```java
// Money normalization — the one place scale is decided
public record Money(BigDecimal amount, Currency currency) {
    public static final int SCALE = 4;
    public Money {
        Objects.requireNonNull(amount); Objects.requireNonNull(currency);
        if (amount.scale() > SCALE) throw new IllegalArgumentException("max 4 decimals");
        amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }
    public boolean sameCurrency(Money o) { return currency.equals(o.currency); }
    public int compareTo(Money o) { return amount.compareTo(o.amount); } // never equals()
}
```

### 4. Acceptance criteria

- [ ] `docker compose up` starts Postgres with three roles; app starts, Flyway migrates as
      `ledgerx_migrate`, app connects as `ledgerx_app`.
- [ ] Register/login work; every `/accounts` and money endpoint returns 401 without JWT.
- [ ] `POST /accounts` creates a wallet with `balance = 0`; `GET /accounts` lists mine only.
- [ ] Deposit 100 → two entries (+100 wallet, −100 clearing), journal `COMPLETED`, balance 100.
- [ ] Withdraw 50 with fee 1 → three entries; wallet 49; fees 1; clearing −50.
- [ ] Withdraw 60 from 49 → `422 INSUFFICIENT_FUNDS`, no entries; balance unchanged.
- [ ] Direct SQL `UPDATE ledger_entry SET amount = 0` as `ledgerx_app` → permission denied;
      as superuser → trigger exception. `DELETE` likewise.
- [ ] Inserting a journal with entries summing to 5 fails at `COMMIT`.
- [ ] `GET /accounts/{id}?verify=true` (ADMIN) shows `consistent: true` after 200 random ops.
- [ ] Amount `"1.23456"` → 400; `"0"` → 400; `"-5"` → 400; `"1e3"` → 400.
- [ ] OpenAPI at `/v3/api-docs` lists all endpoints with error responses; `openapi.json` committed.
- [ ] CI green on `mvn verify` with Testcontainers.

### 5. Now implement it (~27 h)

- [ ] Repo bootstrap from FlowGrid skeleton: Maven, Boot 3.x, Testcontainers base, CI, ProblemDetail, JWT — **2 h**
- [ ] `compose.yaml` + `db/init/roles.sql` (three roles, grants) — **1 h**
- [ ] Flyway `V1` users/accounts (+ system account seed, `min_balance`) — **1.5 h**
- [ ] Flyway `V2` journal_transaction, ledger_entry, immutability trigger, deferred balance trigger, `REVOKE` — **2.5 h**
- [ ] Flyway `V3` account_balance + floor trigger; `V4` idempotency_key (schema only, used in M2) — **1.5 h**
- [ ] `Money` record + Jackson (de)serializers + validation annotations — **2 h**
- [ ] Entities (`@Immutable` LedgerEntry), repositories, native `SUM` query — **2.5 h**
- [ ] `PostingRules` (pure functions: deposit/withdrawal → balanced legs) + unit tests — **2 h**
- [ ] `LedgerService.post` (validate legs, insert, update balances) — **3 h**
- [ ] `DepositService`, `WithdrawalService` (FOR UPDATE on single wallet), controllers, DTOs — **3 h**
- [ ] Account endpoints, ownership checks, `?verify=true` — **2 h**
- [ ] ArchUnit tests (package rules, no float/double) — **1 h**
- [ ] Integration tests (§6) — **3 h**
- [ ] README quick start + `docs/DATABASE.md` first version + ADR-001 (signed amount) + ADR-002 (NUMERIC) — **1.5 h**

### 6. Verification tests (you write them)

| Test | Asserts |
|---|---|
| `money_constructor_rejectsMoreThanFourDecimals` | `IllegalArgumentException` for `1.23456` |
| `money_compare_isScaleInsensitive` | `Money(1.0)` compares equal to `Money(1.0000)` |
| `postingRules_deposit_producesTwoBalancedLegs` | legs sum to zero, wallet leg positive, clearing leg negative |
| `postingRules_withdrawalWithFee_producesThreeBalancedLegs` | wallet `−(amount+fee)`, clearing `+amount`, fees `+fee` |
| `ledgerService_post_rejectsUnbalancedDraft` | domain exception before any SQL |
| `ledgerService_post_rejectsSingleLeg` | ≥ 2 legs required |
| `deposit_thenBalanceAndDerivedAgree` | materialized == `SUM(entries)` |
| `withdrawal_insufficientFunds_returns422AndWritesNothing` | 0 entries; balance unchanged; optional `FAILED` journal present |
| `ledger_updateOrDeleteEntry_rejectedByDatabase` | `restrict_violation` via native SQL as superuser; permission denied as app role |
| `ledger_unbalancedJournal_rejectedAtCommit` | exception at commit, not at insert (prove deferral by asserting insert succeeded inside txn) |
| `balanceFloorTrigger_firesWhenJavaCheckBypassed` | native `UPDATE account_balance SET balance = -1` for a wallet → `check_violation`; for `external_clearing` → allowed |
| `accounts_getOtherUsersAccount_returns403` | ownership |
| `archunit_noFloatingPointInMoneyPackages` | rule passes |
| `archunit_onlyLedgerPackageWritesLedgerEntry` | rule passes |
| `migrations_applyCleanlyOnEmptyDatabase` | Flyway head reached; `ddl-auto=validate` passes |

### 7. Debugging scenarios

1. **`Schema-validation: wrong column type encountered in column [amount]`** — Hibernate expects
   `numeric(19,4)` but the migration wrote `NUMERIC(19,2)` or the entity lacks
   `precision/scale`. Fix the entity to match the schema, never the other way round.
2. **Deposit returns 201 but `derivedBalance` ≠ `balance`** — the balance `UPDATE` ran in a
   different transaction (self-invocation, or `@Transactional` on a private method). Enable
   `logging.level.org.springframework.transaction.interceptor=TRACE` and look for two
   "Getting transaction" lines.
3. **The deferred trigger never fires in tests** — `@Transactional` test method with rollback at
   the end means `COMMIT` never happens, so deferred constraints are never checked. Use
   `TransactionTemplate` in the test and commit explicitly, or `@Commit`.
4. **`permission denied for table ledger_entry`** on INSERT — you revoked too much. The app
   role needs `INSERT` + `SELECT` on `ledger_entry` and `USAGE` on its sequence.
5. **`BigDecimal` `equals` assertion fails with `100.00` vs `100.0000`** — use
   `assertThat(a).isEqualByComparingTo(b)` (AssertJ) or `compareTo`.
6. **Jackson serializes `amount` as a number** — your `Money` serializer is not registered;
   confirm with an `@JsonTest`.

### 8. Interview questions (M1)

- Why derive balances from entries instead of storing them? What is the materialized row for?
- How do you make a table append-only in PostgreSQL, and why in three layers?
- What does `DEFERRABLE INITIALLY DEFERRED` change about when a constraint trigger runs? Why
  does the balanced-journal check need it?
- Why `NUMERIC(19,4)` and `BigDecimal`? What goes wrong with `double`? Why `compareTo` not `equals`?
- Where exactly is your transaction boundary and why not on the controller?
- What is `external_clearing` and why does its balance go negative?

---

## M2 — Idempotent, concurrent transfers (Week 10) → MVP `v0.5`

### 1. What to know first (~6 h)

- Idempotency done properly: [`../../06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md)
  (keys, fingerprints, stored responses); revisit your FlowGrid M2 implementation and list what
  it did *not* handle (crash mid-request, concurrent same-key, expiry).
- `SELECT … FOR UPDATE`, lock ordering, deadlock detection (`40P01`), serialization failures
  (`40001`), `REPEATABLE READ` vs `SERIALIZABLE`:
  [`../../04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md).
- `@Transactional` propagation (`REQUIRED`, `REQUIRES_NEW`), rollback rules, self-invocation,
  `TransactionTemplate`: [`../../05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md).
- Writing concurrency tests: `ExecutorService`, `CountDownLatch` start barrier,
  `CompletableFuture.allOf`: [`../../01-java/07-concurrency.md`](../../01-java/07-concurrency.md),
  [`../../14-cs-fundamentals/concurrency.md`](../../14-cs-fundamentals/concurrency.md).
- Optimistic locking with `@Version` for the comparison branch:
  [`../../05-spring-boot/03-data-jpa.md`](../../05-spring-boot/03-data-jpa.md).

### 2. Requirements

FR-11 (transfer), FR-12 (full idempotency contract), FR-8 (never negative under concurrency),
NFR-1, NFR-4. Plus an **experiment report** (`docs/experiments/isolation-and-locking.md`)
comparing pessimistic ordered locking vs optimistic `@Version` retry vs `SERIALIZABLE`, with
measured numbers from your machine.

### 3. Architectural guidance

**The transfer sequence** (README §8.3), restated as rules:

1. Validate → 2. AuthZ (owner of `from`) → 3. **Reserve the idempotency key in its own short
   transaction** (`INSERT … ON CONFLICT DO NOTHING RETURNING id`; if no row returned, load the
   existing row and branch: same fingerprint + `COMPLETED` → replay; same fingerprint +
   `IN_PROGRESS` → 409 in-progress; different fingerprint → 409 conflict) → 4. **Main
   transaction**: lock both `account_balance` rows in ascending id order, check statuses and
   funds, post the journal via `LedgerService`, update balances, write audit + outbox, finalize
   the idempotency row (`COMPLETED`, response JSON, http status) → COMMIT → 5. Respond.

**Why the key reservation is a separate transaction.** If it were inside the main transaction,
a concurrent duplicate would block on the unique index until the first commits and then get a
unique violation — which works, but ties the duplicate up for the whole transfer and makes
"in-progress" invisible. Reserving first makes duplicates cheap and observable. The cost: a
process crash between reservation and main transaction leaves a stuck `IN_PROGRESS` row; M4
adds staleness recovery (`updated_at < now() - interval '30 seconds'` → take over by
`UPDATE … WHERE status='IN_PROGRESS' AND updated_at < ?` and re-execute, or return 409 with
`Retry-After`). Decide now, write the ADR, implement recovery in M4.

**Why finalize the key inside the main transaction.** If the key were finalized *after* commit
in another transaction, a crash in between would leave a completed transfer with an
`IN_PROGRESS` key; the retry would re-execute and double-charge. Finalizing inside means the
key and the ledger agree atomically. This is the single most important design decision in
LedgerX — be able to draw it.

**Lock ordering.** The A→B/B→A deadlock: T1 locks A then wants B; T2 locks B then wants A.
Locking both in ascending id order in a *single statement* eliminates it:

```sql
-- both rows locked atomically in id order; other writers wait here
SELECT account_id, balance, version
FROM account_balance
WHERE account_id IN (:lo, :hi)
ORDER BY account_id
FOR UPDATE;
```

In JPA: `@Query(nativeQuery = true)` + `@Lock` is awkward for two rows; use `JdbcClient`
(Spring 6.1+) or `EntityManager.createNativeQuery` inside the transactional method. Postgres
locks rows in the order they are returned when `ORDER BY` is on the indexed key; document that
you verified this with `pg_locks` during the deadlock experiment. Note the funds check happens
*after* the lock: under `READ COMMITTED`, `FOR UPDATE` re-reads the latest committed version
of the row, so the number you check is current.

**Isolation choice.** `READ COMMITTED` + explicit `FOR UPDATE`. Reasons: no retry loop needed
in the common case; locks are explicit and observable; the funds check reads the freshest row.
Alternative evaluated: `SERIALIZABLE` without explicit locks — correct, but every conflicting
pair produces a `40001` that must be retried, and under a hot account the retry rate climbs
fast. Alternative 2: optimistic `@Version` on `account_balance` — no DB lock held during
the check, but with two rows you need both versions to match at update time and a retry loop
on `OptimisticLockException`; under contention it thrashes. Implement the optimistic path
behind a flag (`ledgerx.transfers.locking=PESSIMISTIC|OPTIMISTIC`) so the comparison is real.

**How retries interact.** Client retries carry the same `Idempotency-Key` and hit the replay
path. *Internal* retries (deadlock `40P01`, serialization `40001`, optimistic conflict) must
wrap **only the main transaction**, run at most 3 times with jittered backoff, and must not
re-reserve the key. Put the retry in a small `Retrier` helper called from outside the
transactional method (a retry inside the transactional method retries within a dead transaction).

**Fingerprint.** SHA-256 over `method + path template + canonical JSON` (sorted keys, no
insignificant whitespace, amounts normalized to scale 4 *before* hashing so `"100"` and
`"100.0000"` are the same request — decide and document).

**Failed transfers.** On `INSUFFICIENT_FUNDS`, the main transaction has done nothing yet except
lock rows — you can *commit* a `FAILED` journal (0 entries) and the finalized idempotency
row (`COMPLETED`, 422 response) in that same transaction rather than rolling back. That way the
attempt is recorded and the retry replays the 422. Alternative: throw, roll back, write the
`FAILED` row in a `REQUIRES_NEW` transaction. The first is simpler; pick it unless you have a
reason.

### 4. Acceptance criteria

- [ ] Transfer 30 A→B: 2 entries, both balances correct, `201` with `transactionId`.
- [ ] Missing header → `400 IDEMPOTENCY_KEY_REQUIRED`.
- [ ] Replay: same key+body → identical body, same status, `Idempotent-Replayed: true`, one journal.
- [ ] Conflict: same key, different amount → `409 IDEMPOTENCY_CONFLICT`, no journal.
- [ ] Two concurrent 400s on 500 → exactly one 201, one 422; balance 100; invariants hold.
- [ ] A→B and B→A in parallel × 100 → no deadlock errors surface to clients; all succeed.
- [ ] Frozen source → `422 ACCOUNT_FROZEN` (and a `FAILED` journal, by policy).
- [ ] Same-currency check: USD→EUR wallet → `422 CURRENCY_MISMATCH`.
- [ ] Optimistic mode passes the same test suite (with expected `CONCURRENT_MODIFICATION` after
      3 retries under extreme contention documented as acceptable behaviour for that mode).
- [ ] `docs/experiments/isolation-and-locking.md` contains: setup, the three configurations,
      throughput and error counts at 8/32 threads on one hot pair, and a conclusion.
- [ ] Tag `v0.5`.

### 5. Now implement it (~28 h)

- [ ] `IdempotencyKey` entity + `IdempotencyStore` (reserve / load / finalize; `ON CONFLICT`) — **3 h**
- [ ] Fingerprint (canonical JSON + SHA-256) + unit tests — **1.5 h**
- [ ] `IdempotencyInterceptor`/filter for money endpoints: header validation, reserve, replay, 409s; cache the request body once — **3 h**
- [ ] `TransferService` pessimistic path (ordered `FOR UPDATE`, status/currency/funds checks, post journal, finalize key) — **4 h**
- [ ] `Retrier` for `40P01`/`40001`/optimistic conflicts, outside the transaction — **1.5 h**
- [ ] Optimistic path behind a property (`@Version`, retry loop) — **2.5 h**
- [ ] `FAILED` journal recording policy + error mapping (`check_violation` → `INSUFFICIENT_FUNDS`) — **1.5 h**
- [ ] Concurrency test harness (latch-started thread pool, collects statuses, asserts invariants) — **2.5 h**
- [ ] The mandatory tests (§6) — **4 h**
- [ ] Isolation experiment: run the hot-pair hammer in three configurations, capture `pg_stat_database.deadlocks`, `pg_locks` snapshot, write the report — **3 h**
- [ ] ADR-003 (pessimistic vs optimistic), ADR-004 (isolation), ADR-005 (key reservation in separate txn) — **1.5 h**

### 6. Verification tests

| Test | Asserts |
|---|---|
| `transfer_whenTwoConcurrent400sOn500Balance_exactlyOneSucceeds` | one 201, one 422 `INSUFFICIENT_FUNDS`; source 100, dest 400; one COMPLETED journal (2 entries), one FAILED (0 entries); global sum 0 |
| `transfer_whenNThreadsHammerOneAccount_neverNegativeAndSumZero` | 32×50 random amounts; final ≥ 0; `SUM(entries)` = 0; materialized = derived for all accounts |
| `transfer_sameKeySameBody_returnsSameResponseNoDoubleCharge` | second response byte-identical body, `Idempotent-Replayed: true`; one journal; balance moved once |
| `transfer_sameKeyDifferentBody_returns409` | `IDEMPOTENCY_CONFLICT`; no new journal |
| `transfer_sameKeyConcurrent_exactlyOneExecutes` | 10 threads: 1× executes; others replay (201 replayed) or 409 in-progress; one journal |
| `transfer_aToBAndBToAConcurrently_noDeadlockBothSucceed` | 100 rounds; `pg_stat_database.deadlocks` unchanged; balances sum preserved |
| `transfer_withoutIdempotencyKey_returns400` | `IDEMPOTENCY_KEY_REQUIRED` |
| `transfer_fromFrozenAccount_returns422` | `ACCOUNT_FROZEN` |
| `transfer_toSelf_returns400` | validation |
| `transfer_keyScopedPerUser_sameKeyDifferentUsersBothExecute` | two journals |
| `transfer_insufficientFunds_isReplayedAs422UnderSameKey` | retry of a failed attempt does not re-execute even after a deposit |
| `transfer_optimisticMode_passesConcurrencyTests` | same assertions, parameterized test class |
| `transactional_selfInvocation_pitfallReproduced` | a deliberately mis-structured bean shows two transactions; fixed structure shows one (keeps you honest) |
| `retrier_onDeadlockSqlState_retriesUpToThreeTimes` | uses a fake throwing `40P01` twice then succeeding |

### 7. Debugging scenarios

1. **Both 400s succeed; balance −300.** The funds check ran on a JPA-managed entity loaded
   *before* the lock (first-level cache returned the stale object). Fix: read the balance from
   the `FOR UPDATE` result set, not from an entity loaded earlier; or `entityManager.refresh`.
2. **`ERROR: deadlock detected` in the A→B/B→A test.** Your two-row lock is actually two
   statements (`findById` × 2) in call order, not id order. Replace with the single ordered query.
3. **Replay returns 201 but a *different* `transactionId`.** The key was finalized after commit
   in a separate transaction, and the test's second call raced the finalization — or the
   response was stored from a DTO built before the id was generated. Move finalization inside.
4. **`UnexpectedRollbackException: Transaction silently rolled back`** — you caught
   `InsufficientFundsException` inside the transactional method and continued; the inner
   `@Transactional` (propagation REQUIRED, same physical txn) already marked it rollback-only.
5. **Tests pass alone, fail together.** Testcontainers DB shared across classes with leftover
   `IN_PROGRESS` keys or accounts. Use unique ids per test and a `TRUNCATE … CASCADE` (as
   superuser; the trigger blocks `TRUNCATE` on `ledger_entry` unless you `ALTER TABLE …
   DISABLE TRIGGER` in test cleanup — and that itself is a good test of your immutability).
6. **`HikariPool … Connection is not available, request timed out`** in the 32-thread test:
   pool size 10, 32 threads each holding a connection while waiting on a row lock → starvation.
   Lower thread count to pool size or raise pool size *for the test* and note that in
   production the DB, not the pool, is the bottleneck.

### 8. Interview questions (M2)

- Walk me through a transfer from HTTP to COMMIT. Where are the locks taken and released?
- Why lock in id order? What happens without it? How does Postgres detect deadlocks?
- Why `READ COMMITTED` + `FOR UPDATE` rather than `SERIALIZABLE`? What did your experiment show?
- Pessimistic vs optimistic here — why did you choose pessimistic, and when would you flip it?
- Why is the idempotency key reserved in one transaction and finalized in another?
- Same key, different body — what do you return and why not just execute it?
- What did your $500/$400/$400 test actually prove, and what would it miss?

---

## M3 — States, reversals, history, audit, outbox (Week 11)

### 1. What to know first (~6 h)

- State machines as explicit transition tables; compensating transactions (sagas, lite):
  [`../../15-system-design/fundamentals.md`](../../15-system-design/fundamentals.md).
- Cursor pagination, keyset queries, composite index use:
  [`../../06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md),
  [`../../04-sql-databases/05-indexes-performance.md`](../../04-sql-databases/05-indexes-performance.md).
- Transactional outbox pattern; at-least-once delivery; consumer idempotency:
  [`../../15-system-design/scalability.md`](../../15-system-design/scalability.md).
- `@Scheduled` with `@SchedulerLock`-style single-runner concerns, `FOR UPDATE SKIP LOCKED`:
  [`../../05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md).
- Audit design: what to store (before/after JSON, actor, reason), immutability, PII minimization.

### 2. Requirements

FR-13 (states + transitions), FR-14 (reversal/refund), FR-15 (payment requests), FR-16
(history), FR-17 (transaction detail with links), FR-18 (audit), FR-19 (outbox), NFR-3.

### 3. Architectural guidance

**State machine.** An enum `TxnState` with a static transition table:
`PENDING→{COMPLETED, FAILED}`, `COMPLETED→{REVERSED, REFUNDED}`, `REFUNDED→{REFUNDED,
REVERSED?}` (decide whether a partially refunded txn can be fully reversed for the remainder —
simplest: `REFUNDED` may receive further refunds until the cumulative amount equals the
original, at which point state becomes `REVERSED`). Transition is a method that throws
`InvalidStateTransitionException`; it is the only way `state` changes, and every transition
writes an audit row.

**Reversal = compensating journal.** `ReversalService.reverse(txnId, key, actor)`:
lock the original journal row (`SELECT … FOR UPDATE` on `journal_transaction` — this
serializes competing reversals of the same txn), assert state `COMPLETED`, load its entries,
build a draft with every amount negated, lock the affected `account_balance` rows in id order,
check the *receiver* has funds (policy: user-initiated refunds must not overdraw; ADMIN
reversals may overdraw a wallet into `suspense` — document), post via `LedgerService` with
`type=REVERSAL, reversal_of=original`, transition original to `REVERSED`. Partial refunds
negate a *scaled* amount for the two wallet legs only; fees are not refunded (policy).
Partial unique index `UNIQUE (reversal_of) WHERE type='REVERSAL'` backs the "once only" rule.

**Payment requests.** A row with its own state machine (`OPEN→ACCEPTED|DECLINED|EXPIRED|
CANCELLED`). `accept` runs a transfer payer→requester **through the same `TransferService`**
with an idempotency key derived from the request (`pr:<id>:accept`) *plus* the client's header
key — the request's state transition and the transfer must commit together, so `accept` is
the transactional entry point that calls the transfer's internal method (same physical
transaction, propagation `REQUIRED`). Expiry via a scheduled sweep (`UPDATE … WHERE status =
'OPEN' AND expires_at < now()`), audited.

**History with cursor pagination.** Query `ledger_entry` (not `journal_transaction`) for the
account — each row is one line in the statement — joined to the journal for type/state/memo/
counterparty. Keyset on `(created_at, id)`; index `(account_id, created_at DESC, id DESC)` or
simply `(account_id, id DESC)` if you cursor on `id` alone (ids are monotonic — simpler and
fully stable; do that unless you need date-range filters to use the index). Return
`nextCursor` opaque. Verify with `EXPLAIN (ANALYZE, BUFFERS)` that it is an index scan with
`LIMIT`, and paste the plan into `docs/PERFORMANCE.md`.

**Audit.** `AuditWriter.record(actor, entityType, entityId, action, before, after, reason)`
called inside the same transaction as the change (so the audit row cannot exist without the
change or vice versa). Append-only trigger like `ledger_entry`. Do not store request bodies —
store the *domain* before/after.

**Transactional outbox.** `OutboxWriter.append(eventType, aggregateId, payload)` inserts
`outbox_event` inside the business transaction. A `@Scheduled(fixedDelay = 500)` publisher
runs `SELECT … WHERE published_at IS NULL ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED`,
publishes each (MVP: a log line / in-memory listener; optional: SNS/SQS in M5), sets
`published_at`, commits per batch. At-least-once: a crash after publishing but before
`published_at` update re-publishes — consumers dedupe on `event id`. Sequence = `id`.
Multiple app instances are safe because of `SKIP LOCKED`.

```java
// FaultInjector — introduced in M3 so M4 can use it everywhere; no-op in prod
public interface FaultInjector {
    /** Called at named points, e.g. "transfer.afterEntries", "outbox.afterPublish". */
    void maybeFail(String point);
    FaultInjector NONE = point -> {};
}
```

### 4. Acceptance criteria

- [ ] `POST /transactions/{id}/reversals` on a COMPLETED transfer → new journal (type REVERSAL,
      `reversal_of` set), original state `REVERSED`, both balances restored, 4 entries total.
- [ ] Second reversal → `409 ALREADY_REVERSED`. Reversal of a `FAILED` txn → `409 INVALID_STATE_TRANSITION`.
- [ ] Partial refund 10 of 30 → journal type REFUND; cumulative refunds capped at 30 (`422 REFUND_EXCEEDS_ORIGINAL`).
- [ ] Payment request create → accept moves money payer→requester in one commit with state `ACCEPTED`
      and `settlement_txn_id`; decline and expiry work; accept twice with same key replays.
- [ ] History: 200 entries, `limit=50`, walk 4 pages with no gaps/duplicates while another
      thread inserts; `EXPLAIN` shows index scan.
- [ ] Every state change has an audit row with actor and before/after; audit rows immutable.
- [ ] Outbox: transfer → exactly one `TransactionCompleted` row; publisher marks it published;
      a rolled-back transfer leaves no row.
- [ ] `GET /transactions/{id}` shows `reversedBy`/`reversalOf`/`paymentRequestId` links.

### 5. Now implement it (~28 h)

- [ ] `TxnState` transition table + `transition()` + unit tests — **1.5 h**
- [ ] `ReversalService` (lock original, negate, post, transition) + `RefundService` (partial, cap) — **4 h**
- [ ] Reversal/refund endpoints with idempotency + authz policy — **1.5 h**
- [ ] `payment_request` migration, entity, state machine, create/accept/decline, expiry sweep — **4 h**
- [ ] History query (keyset), cursor codec, controller, filters — **3 h**
- [ ] `EXPLAIN` the history and per-account sum; add/adjust indexes; record plans — **1 h**
- [ ] `audit_event` migration + trigger, `AuditWriter`, wire into every transition/admin action, `GET /audit` — **3 h**
- [ ] `outbox_event` migration, `OutboxWriter`, `OutboxPublisher` with `SKIP LOCKED`, event payload schema — **3.5 h**
- [ ] `FaultInjector` interface + no-op bean + test implementation (map of point → exception) — **1 h**
- [ ] Tests (§6) — **4 h**
- [ ] `docs/API.md` update, ADR-006 (outbox vs direct publish), ADR-007 (refund policy) — **1.5 h**

### 6. Verification tests

| Test | Asserts |
|---|---|
| `txnState_transitionTable_allowsOnlyDocumentedEdges` | parameterized over every `(from,to)` pair |
| `reversal_fullReversal_restoresBothBalancesAndLinks` | balances, `reversal_of`, state, entry count |
| `reversal_ofReversedTxn_returns409` | `ALREADY_REVERSED` |
| `reversal_concurrentSameTxn_exactlyOneSucceeds` | 8 threads reverse the same txn: one 201, rest 409 |
| `refund_cumulativeExceedsOriginal_returns422` | 20 + 15 on a 30 transfer |
| `refund_receiverSpentFunds_policyApplied` | user refund → 422 INSUFFICIENT_FUNDS; admin reversal → suspense leg (if that is your policy) |
| `paymentRequest_accept_transfersAndTransitionsAtomically` | inject fault after transfer, before state update → both rolled back |
| `paymentRequest_acceptTwiceSameKey_replays` | one settlement txn |
| `paymentRequest_expirySweep_marksExpiredAndAudits` | |
| `history_cursorPagination_stableUnderConcurrentInserts` | no dupes/gaps |
| `history_otherUsersAccount_returns403` | |
| `audit_rowWrittenInSameTransactionAsChange` | fault after change before audit → neither persists |
| `audit_updateRejectedByDatabase` | trigger |
| `outbox_eventPublishedOnlyIfTransferCommitted` | rollback → 0 rows |
| `outbox_publisherCrashMidBatch_republishesUnpublishedOnly` | fault after 3 of 5 published → those 3 have `published_at`; next run publishes 2 (with `published_at` set inside the per-event transaction) or re-publishes all 5 (batch commit) — assert whichever you designed and that consumers can dedupe |
| `outbox_twoPublishersConcurrently_noDoublePublishWithinBatch` | `SKIP LOCKED` |

### 7. Debugging scenarios

1. **Reversal succeeds twice under concurrency.** You checked `state == COMPLETED` on an entity
   loaded without a lock. Lock the journal row (`FOR UPDATE`) before checking; confirm the
   partial unique index also rejected the second insert in the log.
2. **Page 2 repeats the last item of page 1.** Cursor comparison uses `<=` instead of `<`, or
   the sort is `created_at` only and two entries share a timestamp. Cursor on `(created_at, id)`
   or on `id` alone.
3. **Outbox publisher publishes every event twice.** Two app instances (or the test and the
   app) polling without `SKIP LOCKED`, or `published_at` set in a transaction that rolled back
   because publishing threw *after* the update. Order: publish → mark, inside one transaction
   per event, and accept at-least-once.
4. **Audit `before` JSON is identical to `after`.** Both snapshots taken after mutation — the
   entity is a managed object; snapshot `before` by mapping to a DTO first.
5. **`LazyInitializationException` while serializing history.** Building response DTOs
   outside the transaction from entities with lazy joins. Project directly into DTOs in the query.

### 8. Interview questions (M3)

- How is a refund represented? Why never update the original entries?
- What stops two admins reversing the same transaction at the same time?
- Explain the transactional outbox. What delivery guarantee does it give and what must
  consumers do?
- Why cursor pagination instead of offset? What is the cursor made of?
- What is in an audit row and why is it written in the same transaction?
- If a payment request accept crashed after the transfer committed, what state would the
  request be in? (Answer depends on your boundary — know it.)

---

## M4 — Reconciliation, failure injection, invariants (Week 12) → CP-12

### 1. What to know first (~8 h; lighter learning week includes the Python sprint)

- Failure-injection techniques (hooks, `kill -9`, DB connection kill, Toxiproxy-style
  latency): [`../../09-testing/testcontainers.md`](../../09-testing/testcontainers.md) and
  [`../../14-cs-fundamentals/operating-systems.md`](../../14-cs-fundamentals/operating-systems.md)
  (signals, what "crash" means to a process).
- Crash-consistency reasoning: what is durable at each step; WAL and `COMMIT` in
  [`../../14-cs-fundamentals/database-internals.md`](../../14-cs-fundamentals/database-internals.md).
- Property-style testing: random workloads + invariant assertions (plain JUnit loops are
  enough; jqwik optional): [`../../09-testing/junit5.md`](../../09-testing/junit5.md).
- Python for the tools: psycopg 3 basics, `decimal.Decimal`, `dataclasses`, type hints,
  pytest fixtures/markers, `argparse`, exit codes:
  [`../../19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md),
  [`../../19-python/03-pitfalls-and-complexity.md`](../../19-python/03-pitfalls-and-complexity.md),
  [`../../19-python/python-for-java-devs.md`](../../19-python/python-for-java-devs.md).
- Micrometer timers/counters and reading `pg_stat_*`:
  [`../../05-spring-boot/06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md),
  [`../../04-sql-databases/05-indexes-performance.md`](../../04-sql-databases/05-indexes-performance.md).
- Checkpoint: [`../../checkpoints/checkpoint-12.md`](../../checkpoints/checkpoint-12.md).

### 2. Requirements

FR-20 (reconciliation job + findings, never auto-fix), FR-21 (admin endpoints), FR-21b
(Python independent verifier, generator, checker), NFR-1, NFR-2, NFR-4, NFR-5, NFR-7. Deliver
the **invariant suite**, the **fault-injection tests**, the **retry-after-crash tests**, a first
**throughput measurement** with methodology, and the **Python `tools/` package** with pytest.

### 3. Architectural guidance

**Reconciliation job (Java).** `ReconciliationService.run(trigger)` → inserts a
`reconciliation_run`, then executes checks as set-based SQL (never row-by-row in Java):

| Check | SQL shape | Finding kind |
|---|---|---|
| Journals balance | `SELECT txn_id, SUM(amount) FROM ledger_entry GROUP BY txn_id HAVING SUM(amount) <> 0` | `UNBALANCED_TXN` |
| Balance drift | `SELECT b.account_id, b.balance, COALESCE(SUM(e.amount),0) FROM account_balance b LEFT JOIN ledger_entry e ON … GROUP BY … HAVING b.balance <> COALESCE(SUM(e.amount),0)` | `BALANCE_DRIFT` |
| Global zero (per currency) | `SELECT currency, SUM(amount) FROM ledger_entry GROUP BY currency HAVING SUM(amount) <> 0` | `GLOBAL_SUM_NONZERO` |
| Completed without entries | journal `COMPLETED` with 0 entries; `FAILED` with > 0 | `COMPLETED_WITHOUT_ENTRIES` / `FAILED_WITH_ENTRIES` |
| Wallet below floor | `balance < min_balance` | `BELOW_FLOOR` |
| Suspense non-empty | `balance <> 0` for `suspense` | `SUSPENSE_NONEMPTY` |
| Reversal mismatch | reversal entries ≠ negated original | `REVERSAL_MISMATCH` |
| Stuck in-progress keys | `IN_PROGRESS` older than timeout | `STALE_IDEMPOTENCY_KEY` |

Snapshot consistency: run the checks inside one `REPEATABLE READ` transaction so all queries
see the same snapshot (otherwise a transfer committing between the entries query and the
balance query shows as false drift). Findings are persisted, an audit row and an outbox
event (`ReconciliationFindings`) are emitted, a Micrometer gauge `ledgerx.recon.findings` is
set. **Nothing is corrected automatically**; an ADMIN resolves a finding with a note, and any
money correction is a `RECON_ADJUSTMENT` journal via `suspense` (audited, reason required).
Incremental mode (optional): use `account_balance.last_entry_id` to bound the scan.

**Fault injection.** The `FaultInjector` from M3 is a Spring bean; the `test` profile provides
`MapFaultInjector` where a test registers `failAt("transfer.afterEntries", () -> new
RuntimeException("boom"))` or a `CrashSignal` that throws an `Error` subclass to simulate an
abrupt death (Spring will still roll back on `Error`). Name the points explicitly in
`TransferService`: `transfer.afterLock`, `transfer.afterJournal`, `transfer.afterEntries`,
`transfer.afterBalances`, `transfer.afterOutbox`, `transfer.afterKeyFinalize`, and — crucially
— `transfer.afterCommit` which is triggered *after* the transactional method returns but
before the controller writes the response. For a true process crash, add a second flavour:
a Testcontainers test that starts the app in a container and `docker kill`s it mid-request
(optional; the in-process hook covers the transactional reasoning).

**What each crash point must leave behind** (this table is your acceptance criteria and your
interview answer):

| Crash at | Durable state after | Retry with same key must |
|---|---|---|
| after key reserve, before main txn | key `IN_PROGRESS`, nothing else | detect staleness → take over and execute once (or 409 + Retry-After until reaper resets it) |
| after lock / after journal / after entries / after balances (before commit) | nothing (rollback) — key still `IN_PROGRESS` | same as above |
| after commit, before response | everything incl. key `COMPLETED` | replay stored response, same `transactionId` |
| outbox publisher after publish before mark | event published once, `published_at NULL` | re-publish (at-least-once); consumer dedupes |

**Stale-key recovery.** Choose one: (a) *takeover*: `UPDATE idempotency_key SET
updated_at = now(), owner_token = :me WHERE id = :id AND status = 'IN_PROGRESS' AND
updated_at < now() - :timeout` — if 1 row updated, this request proceeds to execute; (b)
*reaper*: a scheduled job flips stale `IN_PROGRESS` to `FAILED` so the next retry starts
clean. (a) gives faster recovery; (b) is simpler. Either way the invariant "at most one
execution per key" must hold — the takeover `UPDATE … WHERE` is atomic, so two simultaneous
retries cannot both win.

**Python tools (`tools/`).** Three small packages, one `pyproject.toml`, typed (`mypy
--strict`), linted (`ruff`), pytest with `unit` and `integration` markers.

- `ledgerx_verify` — connects with `psycopg.connect(dsn)` as `ledgerx_verify` (SELECT-only
  role), opens one `REPEATABLE READ` read-only transaction, runs the same eight checks as
  the Java job **but implemented independently** (own SQL, own Python aggregation for at least
  the balance-drift check so a SQL-level bug is also caught: fetch entries, sum with
  `Decimal` in Python, compare to `account_balance`). Emits JSON report; exit 0/1/2. Options:
  `--dsn`, `--json`, `--since-entry-id` (incremental), `--fail-fast`.
- `ledgerx_gen` — drives the REST API with `httpx`: creates N users/accounts, seeds deposits,
  runs a mixed workload (transfers 70 %, deposits 15 %, withdrawals 10 %, reversals 5 %) with
  a configurable concurrency (`ThreadPoolExecutor`), a configurable **duplicate-key rate**
  (re-sends a previous request with the same `Idempotency-Key` — sometimes with a mutated
  body to provoke 409s), records every response, and writes a run manifest (`keys.jsonl`) that
  the checker consumes. Amounts are `Decimal` quantized to 4 places, serialized as strings.
- `ledgerx_check` — `snapshot` (dump per-account balances + derived sums + key statuses to
  JSON) and `compare before.json after.json --expect-delta` for crash/retry drills: proves
  that after a crash + retry the ledger moved exactly once per key, and that every key in
  the generator manifest with a `201` maps to exactly one journal.

```python
# tools/ledgerx_verify/checks.py — the shape of one check (no float anywhere)
from decimal import Decimal
from dataclasses import dataclass

@dataclass(frozen=True)
class Finding:
    kind: str; entity_id: str; expected: Decimal; actual: Decimal

def balance_drift(balances: dict[int, Decimal], entries: list[tuple[int, Decimal]]) -> list[Finding]:
    sums: dict[int, Decimal] = {}
    for account_id, amount in entries:
        sums[account_id] = sums.get(account_id, Decimal("0")) + amount
    return [Finding("BALANCE_DRIFT", str(a), sums.get(a, Decimal("0")), bal)
            for a, bal in balances.items() if bal != sums.get(a, Decimal("0"))]
```

**Throughput measurement.** k6 (or the Python generator at fixed concurrency) against the
Compose stack; scenarios: (a) all transfers touch one hot destination, (b) 1,000 random
pairs; concurrency 1/8/32/64; 60 s each; record transfers/s, p50/p95/p99, error counts by
code, `pg_stat_database.deadlocks`, CPU. Protocol and template in
[`docs-and-resume.md`](./docs-and-resume.md). This is a *baseline*; the M5/polish runs on AWS
are the ones you may quote.

### 4. Acceptance criteria

- [ ] `POST /admin/reconciliation/runs` → 202; run completes; zero findings on a healthy ledger
      after a 10k-operation generator workload.
- [ ] Injected drift (superuser `UPDATE account_balance`) → exactly one `BALANCE_DRIFT` finding;
      balance not modified; audit + outbox event emitted; gauge > 0.
- [ ] Every crash point in the table above has a test; each passes 20× in a loop.
- [ ] Stale `IN_PROGRESS` recovery implemented and tested (takeover or reaper).
- [ ] Invariant suite runs a random multi-threaded workload (≥ 2,000 ops, ≥ 16 threads) and
      asserts all invariants; runs in CI.
- [ ] `tools/`: `ruff`, `mypy --strict`, `pytest` green in CI; `README.md` explains usage, exit
      codes, report format; verifier detects every injected violation class.
- [ ] `python -m ledgerx_verify` reports 0 findings on the same DB the Java job cleared, and
      the same 1 finding when drift is injected.
- [ ] `docs/PERFORMANCE.md` baseline section filled with the measurement template.
- [ ] CP-12 passed.

### 5. Now implement it (~26 h)

- [ ] `reconciliation_run`/`finding` migrations, entities, `ReconciliationService` with set-based checks in one `REPEATABLE READ` txn — **4 h**
- [ ] Admin endpoints (trigger, get run, resolve finding), `@Scheduled` nightly run, Micrometer gauge, outbox event — **2 h**
- [ ] `RECON_ADJUSTMENT` journal type (admin, suspense only, reason) — **1.5 h**
- [ ] Fault points in `TransferService`/`OutboxPublisher`; `MapFaultInjector` for tests; `afterCommit` hook via `TransactionSynchronization` or controller-level call — **2.5 h**
- [ ] Stale-key takeover/reaper + tests — **2 h**
- [ ] Crash-point tests (§6) — **3.5 h**
- [ ] Invariant suite (random workload harness reusing M2 harness; assertions as native SQL) — **2.5 h**
- [ ] `tools/` scaffold: `pyproject.toml`, `ruff`, `mypy`, pytest markers, DB fixture, `README.md` — **1 h**
- [ ] `ledgerx_verify` (checks + CLI + JSON report + exit codes) + unit tests on fixtures — **3 h**
- [ ] `ledgerx_gen` (users/accounts/seed, mixed workload, duplicate keys, manifest) — **2 h**
- [ ] `ledgerx_check` (snapshot/compare) + integration tests against Compose Postgres — **1.5 h**
- [ ] Throughput baseline run + `docs/PERFORMANCE.md` — **2 h**
- [ ] ADR-008 (no auto-fix), ADR-009 (stale key policy), ADR-010 (independent verifier) — **1 h**

### 6. Verification tests

Java:

| Test | Asserts |
|---|---|
| `crashAfterEntriesBeforeBalance_retryIsConsistent` | fault at `transfer.afterEntries`: 500 returned; DB has no journal/entries for that key; key `IN_PROGRESS`; retry after stale timeout (or immediately, if takeover on same node) → 201 once; balances moved once |
| `crashAfterCommitBeforeResponse_retryReplaysStoredResponse` | fault at `transfer.afterCommit`: client saw error; retry returns 201 with the original `transactionId`, `Idempotent-Replayed: true`; one journal |
| `crashAfterKeyReserveBeforeMainTxn_retryRecovers` | key `IN_PROGRESS`; second call within timeout → 409 `IDEMPOTENCY_IN_PROGRESS`; after timeout → executes exactly once |
| `crashAtEveryPoint_neverLeavesPartialLedgerState` | parameterized over all fault points: after each, invariants hold and entry count ∈ {0, 2} |
| `idempotency_staleInProgressKey_isRecoverable` | two concurrent retries on a stale key: exactly one executes |
| `reconciliation_cleanLedger_zeroFindings` | after generator workload |
| `reconciliation_detectsInjectedDrift_andDoesNotAutoFix` | one finding; balance unchanged; audit + outbox present |
| `reconciliation_detectsUnbalancedJournal_whenTriggerDisabled` | disable trigger as superuser in test, insert bad journal, run → `UNBALANCED_TXN` |
| `reconciliation_detectsCompletedWithoutEntries` | |
| `reconciliation_runsInSingleSnapshot_noFalseDriftUnderLoad` | run recon while 16 threads transfer: zero findings |
| `invariants_randomWorkload_allHold` | ≥ 2,000 ops; journals sum 0; global 0; balance == derived; no wallet < floor; COMPLETED ⇒ ≥ 2 entries; FAILED ⇒ 0 |
| `ledger_everyJournalSumsToZero_property` | the HAVING query returns 0 rows after workload |
| `balance_materializedEqualsDerived_afterRandomWorkload` | |
| `outbox_publisherCrashMidBatch_noEventLost` | fault after 3 of 5; after restart all 5 published (some twice) |

Python (`tools/tests`):

| Test | Asserts |
|---|---|
| `test_balance_drift_on_fixture_rows` (unit) | given lists of rows, exactly the drifted account is reported with exact `Decimal` expected/actual |
| `test_unbalanced_journal_on_fixture_rows` (unit) | |
| `test_no_float_used_anywhere` (unit) | scans package source for `float(` / `: float` |
| `test_verifier_clean_ledger_reports_ok` (integration) | exit 0, `findings == []` |
| `test_verifier_detects_injected_drift` (integration) | superuser alters one balance → one `BALANCE_DRIFT`, exit 1, expected/actual strings equal the SQL values |
| `test_verifier_detects_unbalanced_journal` (integration) | trigger disabled → `UNBALANCED_TXN` |
| `test_verifier_role_cannot_write` (integration) | `INSERT` as `ledgerx_verify` raises `InsufficientPrivilege` |
| `test_generator_duplicate_keys_never_double_charge` (integration, needs app) | every key in manifest with 201 ↔ exactly one journal |
| `test_checker_compare_detects_double_execution` (unit) | before/after fixtures where one key moved money twice → non-zero exit |

### 7. Debugging scenarios

1. **Reconciliation reports drift on every busy account.** Checks ran in separate
   transactions (or `READ COMMITTED`): the entries query and the balance query saw different
   snapshots. One `REPEATABLE READ` transaction for the whole run.
2. **`afterCommit` fault test passes vacuously.** The hook fired *inside* the transaction
   (before commit) because it was placed at the end of the `@Transactional` method. Use
   `TransactionSynchronization.afterCommit` or call the hook in the controller after the
   service returns; assert in the test that the journal *exists* when the fault fires.
3. **Python verifier disagrees with Java: finds drift of `0E-4`.** You compared `Decimal("0")`
   with `Decimal("0.0000")` using string equality or compared to a Python `float` somewhere
   upstream (`psycopg` numeric adapter overridden, or `json.loads` producing floats from a
   fixture). `Decimal("0") == Decimal("0.0000")` is `True` — so the bug is a float leak; grep for
   it and add the no-float test.
4. **Invariant suite flakes: `FAILED_WITH_ENTRIES`.** A transfer that failed at `afterEntries`
   inside a test that did *not* roll back because the fault threw a checked exception not in
   `rollbackFor`. Use runtime exceptions or `rollbackFor = Exception.class`.
5. **Throughput test shows 1 transfer/s on the hot account.** `deadlock_timeout` firing: lock
   waits ≥ 1 s trigger deadlock *checks*, not deadlocks — check `pg_stat_activity.wait_event`;
   the real cause is the locked section doing an HTTP call/log flush/lazy load. Time the
   locked section with a Micrometer timer.
6. **`psycopg.errors.InsufficientPrivilege` on `SET TRANSACTION READ ONLY`?** No — that is
   allowed; the real error is that `ledgerx_verify` lacks `USAGE` on the schema. Grant
   `USAGE ON SCHEMA public` + `SELECT ON ALL TABLES` + default privileges for future tables.

### 8. Interview questions (M4)

- What does your reconciliation check, in SQL terms? Why in one `REPEATABLE READ` snapshot?
- Why never auto-fix drift? What is the process when drift is found?
- Enumerate the crash points in a transfer and the durable state after each. How does a retry
  behave at each?
- How does idempotency survive a crash after commit but before the HTTP response?
- Why write an independent verifier in a different language? What class of bug does it catch
  that the Java tests cannot?
- `decimal.Decimal` vs `float` — give a concrete failing example. Why does psycopg return
  `Decimal` for `NUMERIC`?
- How did you measure throughput, and what limits throughput on a hot account?

---

## M5 — Deploy, docs, advanced (Week 13) → `v1.0`, then `v1.1`

### 1. What to know first (~6 h)

- AWS repetition (faster than FlowGrid): [`../../12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md),
  [`../../12-aws/rds.md`](../../12-aws/rds.md), [`../../12-aws/cloudwatch.md`](../../12-aws/cloudwatch.md),
  [`../../12-aws/cost-safety.md`](../../12-aws/cost-safety.md).
- CI/CD with image publish + deploy: [`../../13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md).
- Scheduling reliably (single runner, missed runs, idempotent execution):
  [`../../05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md).
- Basic rule engines (ordered predicates, first-match/all-match) and Redis counters for velocity:
  [`../../04-sql-databases/redis.md`](../../04-sql-databases/redis.md).
- Security review checklist: [`../../14-cs-fundamentals/software-engineering-practices.md`](../../14-cs-fundamentals/software-engineering-practices.md).
- Deep-dive rehearsal: [`../../16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md).

### 2. Requirements

Deploy (README §16) + CI/CD (§15) + full docs set ([`docs-and-resume.md`](./docs-and-resume.md))
+ benchmarks on AWS → **`v1.0`**. Then FR-22 (scheduled payments), FR-23 (risk rules) →
**`v1.1` Advanced**. Optional: React/TS admin/history view; two-phase pending withdrawals.

### 3. Architectural guidance

**Deploy first, features second.** Days 1–2: RDS + EC2 + Compose + SSM secrets + CloudWatch +
release pipeline + post-deploy smoke + verifier run. Tag `v1.0` only when the docs set is
complete and the AWS benchmark is recorded.

**Scheduled payments.** Table `scheduled_payment` (`id`, `owner`, `from_account`,
`to_account`, `amount`, `currency`, `next_run_at`, `recurrence` (`NONE`/`DAILY`/`WEEKLY`/
`MONTHLY`), `status`, `on_insufficient_funds` (`SKIP`/`RETRY_ONCE`), `last_run_at`,
`failures`). Scheduler every minute: `SELECT … WHERE status='ACTIVE' AND next_run_at <= now()
FOR UPDATE SKIP LOCKED LIMIT 100`; for each, execute a transfer through `TransferService`
with idempotency key `sched:<id>:<next_run_at as ISO instant>` (and `initiated_by = NULL`,
`type = SCHEDULED_TRANSFER`, `schedule_id`, `occurrence_at` set — the unique index on
`(schedule_id, occurrence_at)` is the second guard). Advance `next_run_at` in the same
transaction as the transfer. Clock skew: store and compare in UTC `TIMESTAMPTZ`, tolerate
runs that are late, never run an occurrence twice (the key does that), and never *skip* one
silently (if the box was down for 2 h, decide: catch up or skip-and-audit).

**Risk rules.** `risk_rule` rows per account (or global default): `MAX_SINGLE_AMOUNT`,
`MAX_DAILY_TOTAL`, `MAX_TXN_PER_HOUR`. `RiskEvaluator.evaluate(transferDraft)` runs
*before* the lock (cheap rejection) and the velocity counters are checked again *inside*
the transaction from Postgres (`SUM(amount) … WHERE created_at > now() - interval '1 hour'`
on `journal_transaction` for the source account — indexed) so a concurrent burst cannot
exceed the limit. Redis `INCR` + `EXPIRE` counters are an optional fast path: Redis down →
fall back to the SQL check (fail *closed*, it is money). Violations → `422
RISK_RULE_VIOLATION` with a `FAILED` journal and an audit row.

**Security review pass.** Walk the OWASP API Top 10 against your endpoints; fix findings;
record in `docs/SECURITY.md`.

### 4. Acceptance criteria

- [ ] Public URL (HTTPS) serves `/actuator/health`; the demo script runs end-to-end against it.
- [ ] Release pipeline: tag → image → deploy → smoke → recon → Python verifier; all green.
- [ ] CloudWatch alarm fires on an ERROR log (tested once on purpose) and on recon findings.
- [ ] Docs set complete (see `docs-and-resume.md`); README has diagram, quick start, tests, numbers.
- [ ] AWS benchmark recorded with the template; `v1.0` tagged and linked in `PROJECTS.md` tracker.
- [ ] Scheduled payment executes once per occurrence even if the scheduler runs twice or the
      app restarts mid-run; insufficient funds handled per policy and audited.
- [ ] Risk rule `MAX_SINGLE_AMOUNT` and `MAX_TXN_PER_HOUR` enforced under 16-thread bursts.
- [ ] `v1.1` tagged. LedgerX deep-dive rehearsal done (record yourself, 12 minutes).

### 5. Now implement it (~26 h)

- [ ] RDS (private, roles created), EC2 + Compose, SSM, security groups, TLS proxy — **4 h**
- [ ] `release.yml` (GHCR push, SSH deploy, smoke, recon, verifier) — **2.5 h**
- [ ] CloudWatch logs + 2 alarms + budget alarm — **1.5 h**
- [ ] AWS benchmark runs (hot vs spread) + `docs/PERFORMANCE.md` — **2.5 h**
- [ ] Docs set + diagrams + demo recording — **5 h**
- [ ] Tag `v1.0` — **0.5 h**
- [ ] `scheduled_payment` migration, service, scheduler with `SKIP LOCKED`, occurrence keys, endpoints — **4 h**
- [ ] Risk rules: table, evaluator, SQL velocity check, optional Redis fast path, admin endpoint — **3.5 h**
- [ ] Tests (§6) — **2.5 h**
- [ ] Security review pass + `docs/SECURITY.md`; tag `v1.1` — **1 h**

### 6. Verification tests

| Test | Asserts |
|---|---|
| `scheduler_twoInstancesSameTick_executeOccurrenceOnce` | run scheduler method twice concurrently; one journal per occurrence |
| `scheduler_restartMidRun_doesNotDuplicate` | fault after transfer before `next_run_at` advance → retry executes replay (key) and advances |
| `scheduler_insufficientFunds_appliesPolicyAndAudits` | SKIP → occurrence advanced with audit; RETRY_ONCE → one retry then skip |
| `scheduler_lateRun_executesAllMissedOrSkipsPerPolicy` | `next_run_at` 3 h in the past |
| `risk_maxSingleAmount_rejectsBeforeLocking` | no lock acquired (assert via timing/no `FOR UPDATE` in captured SQL) |
| `risk_velocityLimit_holdsUnder16ThreadBurst` | limit 5/h; 16 concurrent → exactly 5 succeed |
| `risk_redisDown_fallsBackToSqlCheckFailClosed` | stop Redis container → still enforced |
| `deploy_smoke_healthAndIdempotentTransfer` | run in pipeline against the live URL |

### 7. Debugging scenarios

1. **Scheduled payment ran twice at 02:00.** Two app containers (blue/green overlap during
   deploy) both picked the row; you forgot `SKIP LOCKED` or ran the occurrence with a key
   containing `now()` instead of `next_run_at`.
2. **Every scheduled payment fires an hour early after a deploy.** JVM default timezone on the
   EC2 image vs UTC in the DB; set `-Duser.timezone=UTC` and compare `TIMESTAMPTZ` only.
3. **Verifier step in the release pipeline fails with connection refused.** RDS security group
   allows the EC2 SG but the GitHub runner is not in it; run the verifier *on the EC2 host* via
   the SSH step, or through an SSM `RunCommand`.
4. **Velocity limit lets 7 through with limit 5.** The SQL count ran before the lock; move the
   count inside the transaction after locking the source `account_balance` row (which
   serializes the account's outgoing transfers).
5. **CloudWatch shows no logs.** Container logs go to `json-file`; the agent watches a path
   that Compose does not write. Use the `awslogs` Docker log driver or mount the log dir.

### 8. Interview questions (M5)

- How do you guarantee a scheduled payment executes exactly once across restarts and two
  instances?
- How are risk limits enforced under concurrency? What happens when Redis is down, and why
  fail closed?
- Describe the release pipeline. What runs after deploy and what would roll it back?
- What did the AWS benchmark measure and what were the numbers? What bounded them?
- What would you change first if LedgerX had to handle 100× the load?

---

## Weekly rhythm reminders for Weeks 9–13

- Mon/Wed learning blocks = "What to know first" reading, notes in your own words.
- Tue/Thu project blocks = the ordered task list; Thu docs hour = ADRs and `docs/`.
- Fri retro: which acceptance criteria are red, which test flaked, one failure exercise from
  [`failure-engineering.md`](./failure-engineering.md).
- Sat interview block: the milestone's interview questions out loud, 2 minutes each, recorded.
- Sun: trackers ([`../../trackers/project-tracker.md`](../../trackers/project-tracker.md)), plan.
