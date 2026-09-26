# 06 · Transactions, ACID, Isolation, Locking

> **Week 5** (FlowGrid M2: concurrent reservations, `FOR UPDATE` vs `@Version`), deepened in **Weeks 9–12** (LedgerX:
> MVCC, isolation experiments, ordered locking, outbox with `SKIP LOCKED`). Spring's side of this lives in [`../05-spring-boot/07-transactions.md`](../05-spring-boot/07-transactions.md).

## 1. What a transaction is

A **transaction** groups statements into one unit: either all take effect (**COMMIT**) or none do (**ROLLBACK**).

```sql
BEGIN;
INSERT INTO orders (customer_id, status) VALUES (5, 'PENDING') RETURNING id;
-- currval = the id this session just generated (in Java you'd use the RETURNING value)
INSERT INTO order_items (order_id, event_id, quantity, unit_price)
VALUES (currval(pg_get_serial_sequence('orders', 'id')), 8, 2, 80.00);
UPDATE orders SET status = 'PAID' WHERE id = currval(pg_get_serial_sequence('orders', 'id'));
ROLLBACK;    -- COMMIT would make all three permanent; ROLLBACK keeps the practice data intact
```

- **Autocommit:** outside `BEGIN`, every statement is its own transaction. JDBC connections are autocommit by default (`conn.setAutoCommit(false)` to group — see [`08-jdbc-orm.md`](./08-jdbc-orm.md)).
- In Postgres, an error inside a transaction **aborts** it: every following statement fails with `current transaction is aborted, commands ignored until end of transaction block` until you `ROLLBACK`.
- **Savepoints** give partial rollback:

```sql
BEGIN;
INSERT INTO venues (name, city, capacity) VALUES ('Tmp Hall', 'Oslo', 100);
SAVEPOINT before_risky;
INSERT INTO venues (name, city, capacity) VALUES ('Bad', 'Oslo', -5);   -- CHECK violation
ROLLBACK TO SAVEPOINT before_risky;                                    -- first insert survives
SELECT name FROM venues WHERE city = 'Oslo';                           -- Tmp Hall
ROLLBACK;
```

(A bulk stock-adjustment import can use a savepoint per row to report bad rows and keep the good ones. Or validate everything first, then insert in one transaction, which is usually simpler.)

## 2. ACID

| Property | Guarantee | How Postgres provides it |
|---|---|---|
| **Atomicity** | all or nothing | MVCC: uncommitted row versions are invisible and discarded on rollback |
| **Consistency** | constraints hold before and after | `NOT NULL`, `CHECK`, `UNIQUE`, FK checked by the DB (app invariants are *your* job) |
| **Isolation** | concurrent transactions don't see each other's partial work (to a chosen degree) | MVCC snapshots + row locks + SSI |
| **Durability** | committed = survives a crash | **WAL** (write-ahead log) flushed to disk at COMMIT (`synchronous_commit`) |

## 3. MVCC in Postgres (how isolation works)

- `UPDATE` never overwrites a row in place. It writes a **new row version** and marks the old one as expired. Each version has hidden `xmin` (creating txn) and `xmax` (deleting/updating txn).
- Each statement (Read Committed) or transaction (Repeatable Read+) reads from a **snapshot**: the set of transactions committed at snapshot time. You see only versions visible in your snapshot.
- Therefore **readers never block writers and writers never block readers**. Writers block only other writers **on the same row**.
- Dead versions are cleaned by **VACUUM** (autovacuum). Long-running transactions keep old snapshots alive → vacuum can't clean → table **bloat**. Don't leave transactions open (`idle in transaction`).

```sql
SELECT xmin, xmax, id, status FROM orders WHERE id = 1;
```

## 4. Anomalies ⭐

| Anomaly | What happens | Example |
|---|---|---|
| **Dirty read** | read another txn's **uncommitted** change | T2 sees a price T1 later rolls back |
| **Non-repeatable read** | same row read twice gives different values (another txn committed in between) | report reads balance 100, later 50 |
| **Phantom read** | same `WHERE` returns a different **set** of rows (inserts/deletes in between) | `COUNT(*)` of holds changes mid-transaction |
| **Lost update** | two read-modify-write cycles overwrite each other | both read stock=10, both write 9 → one sale lost |
| **Write skew** | two txns read overlapping data, each writes a *different* row, together they break an invariant | two on-call doctors both go off-call because each saw the other still on |

## 5. Isolation levels — standard vs PostgreSQL

| Level | Dirty read | Non-repeatable | Phantom | Lost update | Write skew | Notes (Postgres) |
|---|---|---|---|---|---|---|
| Read Uncommitted | possible (std) | possible | possible | possible | possible | **PG treats it as Read Committed** — never dirty reads |
| **Read Committed** (PG default) | ✗ | possible | possible | possible* | possible | new snapshot **per statement** |
| Repeatable Read | ✗ | ✗ | ✗ in PG (std: possible) | ✗ (error) | **possible** | one snapshot per transaction = snapshot isolation; concurrent update of same row → `ERROR 40001 could not serialize access due to concurrent update` |
| Serializable | ✗ | ✗ | ✗ | ✗ | ✗ | SSI: detects dangerous patterns, aborts one txn with `40001` — **app must retry** |

\* Under Read Committed, `UPDATE t SET x = x + 1` is safe (the second writer waits, then re-reads the latest committed row). The lost update happens when the app **reads into Java, computes, and writes back a constant**.

MySQL/InnoDB defaults to **Repeatable Read** and uses gap locks — see [`07-postgres-vs-mysql.md`](./07-postgres-vs-mysql.md).

```sql
BEGIN ISOLATION LEVEL REPEATABLE READ;
-- …
COMMIT;
SHOW default_transaction_isolation;   -- read committed
```

## 6. Two-terminal labs ⭐

Open **two** `psql` sessions (A and B) on `tixhub`. Type the steps in order.

### Lab 1 — Lost update (Read Committed) and three fixes

```sql
-- setup (either session)
CREATE TABLE IF NOT EXISTS inventory (event_id int PRIMARY KEY, remaining int NOT NULL CHECK (remaining >= 0));
INSERT INTO inventory VALUES (8, 10) ON CONFLICT (event_id) DO UPDATE SET remaining = 10;
```

| Step | Session A | Session B |
|---|---|---|
| 1 | `BEGIN;` | `BEGIN;` |
| 2 | `SELECT remaining FROM inventory WHERE event_id = 8;` → 10 | |
| 3 | | `SELECT remaining FROM inventory WHERE event_id = 8;` → 10 |
| 4 | `UPDATE inventory SET remaining = 9 WHERE event_id = 8;` (app computed 10-1) | |
| 5 | | `UPDATE inventory SET remaining = 9 WHERE event_id = 8;` → **blocks** (row locked by A) |
| 6 | `COMMIT;` | unblocks, `UPDATE 1` |
| 7 | | `COMMIT;` → remaining = **9**, two tickets sold. **Lost update.** |

Fixes:

1. **Atomic update** (best when possible): `UPDATE inventory SET remaining = remaining - 1 WHERE event_id = 8 AND remaining > 0;` → check the affected-row count (0 = sold out).
2. **Pessimistic lock**: `SELECT remaining FROM inventory WHERE event_id = 8 FOR UPDATE;` in step 2/3 → B's `SELECT … FOR UPDATE` waits until A commits, then reads 9.
3. **Optimistic lock** (version column; JPA `@Version`, compared against `FOR UPDATE` in FlowGrid M2):
   ```sql
   -- ALTER TABLE inventory ADD COLUMN version bigint NOT NULL DEFAULT 0;
   -- UPDATE inventory SET remaining = 9, version = version + 1
   -- WHERE event_id = 8 AND version = 0;     -- 0 rows updated => someone else won => retry / 409
   ```
4. Or run both in `REPEATABLE READ`: B's update fails with `could not serialize access due to concurrent update` → retry.

| | Pessimistic (`FOR UPDATE`) | Optimistic (`version`) |
|---|---|---|
| Conflict handling | wait for the lock | detect at write, fail, retry |
| Best when | conflicts frequent, short critical section | conflicts rare, long "think time" (user editing a form) |
| Risk | blocking, deadlocks, holding locks across slow I/O | retry storms under heavy contention |
| Spring | `@Lock(PESSIMISTIC_WRITE)` | `@Version` → `ObjectOptimisticLockingFailureException` |

### Lab 2 — Non-repeatable read vs Repeatable Read

| Step | Session A | Session B |
|---|---|---|
| 1 | `BEGIN;` (Read Committed) | |
| 2 | `SELECT base_price FROM events WHERE id = 8;` → 80.00 | |
| 3 | | `UPDATE events SET base_price = 99 WHERE id = 8;` (autocommit) |
| 4 | `SELECT base_price FROM events WHERE id = 8;` → **99.00** (changed!) | |
| 5 | `COMMIT;` — now repeat with `BEGIN ISOLATION LEVEL REPEATABLE READ;` → step 4 returns 80.00 | reset: `UPDATE events SET base_price = 80 WHERE id = 8;` |

### Lab 3 — Write skew (Repeatable Read allows it; Serializable doesn't)

Rule: *at least one staff member must stay on call.*

```sql
CREATE TABLE IF NOT EXISTS on_call (employee_id int PRIMARY KEY, on_duty boolean NOT NULL);
INSERT INTO on_call VALUES (3, true), (5, true)
ON CONFLICT (employee_id) DO UPDATE SET on_duty = true;
```

| Step | Session A | Session B |
|---|---|---|
| 1 | `BEGIN ISOLATION LEVEL REPEATABLE READ;` | `BEGIN ISOLATION LEVEL REPEATABLE READ;` |
| 2 | `SELECT count(*) FROM on_call WHERE on_duty;` → 2 | `SELECT count(*) FROM on_call WHERE on_duty;` → 2 |
| 3 | `UPDATE on_call SET on_duty = false WHERE employee_id = 3;` | `UPDATE on_call SET on_duty = false WHERE employee_id = 5;` |
| 4 | `COMMIT;` ✅ | `COMMIT;` ✅ → **nobody on call** |

Different rows were written, so no row-level conflict. Redo with `SERIALIZABLE`: B's commit fails with `ERROR: could not serialize access due to read/write dependencies among transactions` (SQLSTATE `40001`). Alternatives: `SELECT … FOR UPDATE` on the rows that the check reads, or a constraint that encodes the rule.

### Lab 4 — Deadlock

| Step | Session A | Session B |
|---|---|---|
| 1 | `BEGIN; UPDATE inventory SET remaining = remaining - 1 WHERE event_id = 8;` | |
| 2 | | `BEGIN; UPDATE events SET base_price = base_price WHERE id = 9;` |
| 3 | `UPDATE events SET base_price = base_price WHERE id = 9;` → waits for B | |
| 4 | | `UPDATE inventory SET remaining = remaining - 1 WHERE event_id = 8;` → waits for A → cycle |
| 5 | after `deadlock_timeout` (1 s) Postgres aborts one: `ERROR: deadlock detected` (`40P01`) | |

Prevention: **acquire locks in a consistent order** (e.g., always lock rows sorted by id: `SELECT … WHERE id IN (…) ORDER BY id FOR UPDATE`), keep transactions short, don't do network calls while holding locks, and **retry** on `40P01`/`40001`.

Clean up after the labs: `DROP TABLE IF EXISTS inventory, on_call;`

## 7. Row locks

| Clause | Blocks | Use |
|---|---|---|
| `FOR UPDATE` | other `FOR UPDATE/SHARE`, `UPDATE`, `DELETE` of those rows | read-then-modify |
| `FOR NO KEY UPDATE` | like FOR UPDATE but lets FK checks (`FOR KEY SHARE`) proceed | updates not touching the key (what plain `UPDATE` takes) |
| `FOR SHARE` | writers, not other sharers | "make sure it doesn't change while I work" |
| `… NOWAIT` | error immediately instead of waiting | UI: "someone else is editing" |
| `… SKIP LOCKED` | silently skip locked rows | **work queues** |

Plain `SELECT` takes no row locks (MVCC).

### Job queue with `SKIP LOCKED` ⭐ (LedgerX M3 outbox relay, FlowGrid scheduled alerts)

```sql
CREATE TABLE IF NOT EXISTS job_queue (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payload     jsonb       NOT NULL,
    status      text        NOT NULL DEFAULT 'READY' CHECK (status IN ('READY', 'RUNNING', 'DONE', 'FAILED')),
    attempts    int         NOT NULL DEFAULT 0,
    run_after   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS job_queue_ready_idx ON job_queue (run_after) WHERE status = 'READY';

INSERT INTO job_queue (payload) SELECT jsonb_build_object('event', 'order.created', 'order_id', g) FROM generate_series(1, 5) g;

-- each worker, in its own transaction:
BEGIN;
WITH next AS (
    SELECT id FROM job_queue
    WHERE status = 'READY' AND run_after <= now()
    ORDER BY run_after
    LIMIT 1
    FOR UPDATE SKIP LOCKED
)
UPDATE job_queue j
SET status = 'RUNNING', attempts = attempts + 1
FROM next
WHERE j.id = next.id
RETURNING j.id, j.payload;
COMMIT;

DROP TABLE job_queue;
```

N workers run this concurrently; each gets a **different** job, nobody waits. On failure, set `status = 'READY', run_after = now() + backoff`. Because a job may run more than once (crash after sending, before marking DONE), the job itself must be **idempotent**.

## 8. Timeouts and hygiene

```sql
SET lock_timeout = '2s';                              -- give up waiting for a lock
SET statement_timeout = '5s';                         -- kill long statements
SET idle_in_transaction_session_timeout = '30s';      -- kill forgotten open transactions
```

Find blockers:

```sql
SELECT pid, state, wait_event_type, wait_event, pg_blocking_pids(pid) AS blocked_by,
       now() - xact_start AS xact_age, left(query, 60) AS query
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY xact_start NULLS LAST;
```

`SELECT pg_cancel_backend(pid);` cancels a query; `pg_terminate_backend(pid)` kills the session.

**Advisory locks** (`pg_advisory_xact_lock(key)`) are app-defined mutexes held by Postgres — handy to ensure only one instance runs a scheduled job (alternative: ShedLock).

## 9. 🔨 Break it

1. In Lab 1, leave Session A in `BEGIN` after step 4 and go to lunch (well, 2 minutes). What does Session B see? Now look at `pg_stat_activity` from a third session.
2. Cause an error inside a transaction, then try another `SELECT`. Read the "current transaction is aborted" message. How does JDBC surface this?
3. Run Lab 3 under Serializable and write down which session fails and at which statement (it may be the UPDATE or the COMMIT).
4. Start two workers on the `SKIP LOCKED` queue without `SKIP LOCKED` — observe the second one waiting.
5. Write a Java program with 20 threads doing Lab 1's read-modify-write through JDBC. Count lost updates. Fix it with each of the three techniques. (This is FlowGrid M2's N-threads-one-unit test in miniature.)

## 10. 🐞 Debugging tips

- "Hangs" are almost always **lock waits**: check `pg_stat_activity.wait_event_type = 'Lock'` and `pg_blocking_pids`.
- A connection pool full of `idle in transaction` sessions = code path that opened a transaction and didn't commit/close (exception swallowed, missing `finally`, `@Transactional` around a slow HTTP call).
- `40001` and `40P01` are **retryable**: retry the *whole* transaction with backoff, not the single statement.
- Log `SQLState` along with the message in Java (`SQLException.getSQLState()`).

## 11. 🎤 Interview Q&A

<details><summary>What does ACID stand for? Give an example of each.</summary>

Atomicity (import of 500 CSV rows commits all or none), Consistency (FK prevents an order for a non-existent customer), Isolation (a report doesn't see half of a concurrent transfer), Durability (after COMMIT returns, a crash doesn't lose it — WAL).
</details>

<details><summary>Explain the isolation levels and the anomalies each prevents.</summary>

Read Uncommitted (allows dirty reads — not in Postgres), Read Committed (no dirty reads; non-repeatable reads and phantoms possible), Repeatable Read (stable snapshot; in PG also no phantoms, but write skew possible), Serializable (equivalent to some serial order; PG uses SSI and aborts one txn on conflict). Postgres default is Read Committed; MySQL InnoDB default is Repeatable Read.
</details>

<details><summary>What is MVCC?</summary>

Multi-Version Concurrency Control: writes create new row versions instead of overwriting, and each transaction reads a consistent snapshot of committed versions. Readers and writers don't block each other; old versions are removed by VACUUM.
</details>

<details><summary>What is a lost update and how do you prevent it?</summary>

Two transactions read the same value, compute, and write back, so one write overwrites the other. Prevent with an atomic `UPDATE … SET x = x - 1`, `SELECT … FOR UPDATE`, optimistic locking with a version column, or a stricter isolation level with retries.
</details>

<details><summary>What is write skew?</summary>

Two transactions read an overlapping set, each updates a different row based on what it read, and together they violate an invariant. Snapshot isolation (PG Repeatable Read) allows it; Serializable prevents it, as does explicitly locking the rows read.
</details>

<details><summary>Optimistic vs pessimistic locking?</summary>

Pessimistic takes a lock before modifying (`SELECT … FOR UPDATE`) so others wait. Optimistic checks a version at write time and fails if it changed, requiring a retry or a 409. Use optimistic when conflicts are rare; pessimistic when frequent and the critical section is short.
</details>

<details><summary>What is a deadlock and how do you avoid it?</summary>

Two transactions each hold a lock the other needs. The DB detects the cycle and aborts one. Avoid by locking in a consistent order, keeping transactions short, and retrying on deadlock errors.
</details>

<details><summary>What does SKIP LOCKED do?</summary>

Makes `SELECT … FOR UPDATE` skip rows that other transactions have locked instead of waiting — so multiple workers can pull different jobs from a table-based queue concurrently.
</details>

<details><summary>How did you stop two orders from reserving the last unit in FlowGrid?</summary>

Answer from what you actually built. The expected shape: the reservation runs in one transaction that locks the inventory row (`SELECT … FOR UPDATE`), or updates it conditionally (`… WHERE available >= :qty`) and checks the affected-row count. A `CHECK (available >= 0)` constraint is the last line of defence. The retried POST is covered by an Idempotency-Key, and the N-thread test proves exactly one success. Then explain why you picked pessimistic over `@Version` (or vice versa) for that path.
</details>

## ✅ Mastery checklist

- [ ] Ran Labs 1–4 in two terminals and can narrate each step
- [ ] Can fill in the isolation/anomaly table from memory, including Postgres differences
- [ ] FlowGrid M2 reservation is atomic and passes the N-threads-one-unit test; LedgerX M2 transfer never goes negative
- [ ] Built the `SKIP LOCKED` queue and ran two workers against it
- [ ] Wrote the Java lost-update experiment and fixed it three ways
