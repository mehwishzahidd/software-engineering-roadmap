# Database Internals (Weeks 7–8, revisited Week 22)

> **Practical use:** predict whether a query will use an index before running `EXPLAIN`, understand
> why P2's optimistic locking works, why Postgres tables bloat, and what a read replica can and can't do for P4.
> **Interview use:** "How does an index work?", "What is MVCC?", "What's a WAL?", "Why isn't my index used?"

SQL usage lives in [`04-sql-databases/`](../04-sql-databases/README.md) — especially
[`05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md) and
[`06-transactions.md`](../04-sql-databases/06-transactions.md). This file is what happens *underneath*.
Examples are PostgreSQL 16.

---

## 1. How rows are stored

- Data files are divided into fixed-size **pages** (8 KB in Postgres; 16 KB in MySQL InnoDB).
- A Postgres table is a **heap**: rows (*tuples*) placed wherever there's space, in no particular order. Each tuple has a physical address, the **ctid** `(page, slot)`.
- InnoDB instead stores rows **inside the primary-key B-tree** (a *clustered index*) — rows are physically ordered by PK. Secondary indexes store the PK, not a physical address. (One of the interesting [Postgres vs MySQL](../04-sql-databases/07-postgres-vs-mysql.md) differences.)
- The **buffer pool / shared buffers** caches pages in RAM; the OS page cache sits below it. "Is it in memory?" dominates query speed (see latency numbers in [memory-and-architecture.md](./memory-and-architecture.md#2-latency-numbers-every-engineer-should-know-orders-of-magnitude)).
- Large values (long text/JSON) are moved out of line (**TOAST**) automatically.

```sql
SELECT ctid, id, name FROM monitors LIMIT 3;   -- see physical row addresses
```

---

## 2. B-tree indexes

A **B+tree** is a balanced tree with a **high fan-out** (hundreds of keys per 8 KB page):

```
                        [ 40 | 80 ]                         ← root (1 page)
              ┌────────────┼─────────────┐
        [10|20|30]     [50|60|70]     [90|100|110]           ← internal pages
         │ │ │ │        …               …
  leaf: [1..9]→[10..19]→[20..29]→ …                          ← leaves: sorted keys + ctids,
                                                               linked for range scans
```

- Height grows logarithmically with a huge base: ~3–4 levels for **hundreds of millions** of rows → a lookup is ~4 page reads, most already cached.
- Leaves are **sorted and linked** → supports `=`, `<`, `>`, `BETWEEN`, `ORDER BY`, `LIKE 'abc%'` (prefix), `MIN/MAX`.
- Inserts may **split** pages; deletes leave space. Every index is updated on every insert → **write cost** per index.
- Why not a hash table? Hash indexes only do equality; no ranges, no ordering.
- Why not a binary tree? Fan-out 2 → height ~27 for 100 M rows → 27 random page reads.

### Composite indexes — leftmost prefix rule

Index on `check_results(monitor_id, checked_at)` is sorted by `monitor_id`, then by `checked_at` within each monitor:

| Query | Uses index well? | Why |
|---|---|---|
| `WHERE monitor_id = 42` | ✅ | Leftmost column |
| `WHERE monitor_id = 42 AND checked_at > now() - interval '1 day'` | ✅ | Equality then range — ideal |
| `WHERE monitor_id = 42 ORDER BY checked_at DESC LIMIT 50` | ✅✅ | Reads 50 entries backwards, **no sort** — P4's status-page query |
| `WHERE checked_at > now() - interval '1 day'` | ❌ (mostly) | Not a leftmost prefix |
| `WHERE monitor_id IN (1,2,3) ORDER BY checked_at` | Partly | Needs a merge/sort across monitors |

Rule of thumb for column order: **equality columns first, then range/sort column**.

### Other index kinds (awareness)

| Kind | Use |
|---|---|
| **Unique index** | Enforces uniqueness (P1 dedupe, P2 one active hold per seat) |
| **Partial index** | `CREATE INDEX … WHERE status = 'OPEN'` — smaller, targets hot subset |
| **Covering index** (`INCLUDE`) | Adds columns so the query is answered from the index alone (**index-only scan**) |
| **Expression index** | `CREATE INDEX ON users (lower(email))` for `WHERE lower(email) = ?` |
| **GIN** | Full-text search, JSONB containment, arrays (P3 issue search) |
| **BRIN** | Huge append-only tables ordered by time — tiny index |
| **Hash** | Equality only |

### Why isn't my index used?

1. Function on the column: `WHERE lower(email) = ?` / `WHERE date(created_at) = ?` → use expression index or rewrite as range.
2. Leading wildcard: `LIKE '%foo'`.
3. Type mismatch / implicit cast.
4. Not a leftmost prefix of a composite index.
5. **Low selectivity:** the planner estimates most rows match → a sequential scan is cheaper than thousands of random reads. This is correct behaviour, not a bug.
6. Stale statistics → `ANALYZE table`.
7. Tiny table — seq scan of 1 page beats any index.

---

## 3. Write-Ahead Log (WAL)

**Rule:** before a change to a data page is written to disk, a **log record describing it** must be
durably written (fsync) to the WAL.

```
UPDATE … ─► modify page in shared buffers (dirty, in RAM)
         ─► append WAL record ─► on COMMIT: fsync WAL  ◄── durability point
                                                      (data pages written later by checkpointer)
crash? ─► on restart, replay WAL from last checkpoint ─► consistent state
```

Why:
- **Durability (the D in ACID)** with sequential writes (fast) instead of random page writes at every commit.
- **Crash recovery:** replay the log.
- **Replication:** stream WAL to replicas (§7).
- **Point-in-time recovery:** base backup + archived WAL → restore to any second (what RDS automated backups do — see [`12-aws/rds.md`](../12-aws/rds.md#7-backups-and-restore)).

Same idea elsewhere: MySQL's redo log, Kafka's log, event sourcing. "Append-only log as source of
truth" is a recurring system-design theme.

---

## 4. MVCC and locking

**Multi-Version Concurrency Control:** instead of overwriting a row in place, an `UPDATE` creates a
**new version** of the row; each transaction sees a **snapshot** of versions committed before it
started (or before each statement, depending on isolation level).

```
 tuple versions for seat 17          xmin (created by)   xmax (deleted by)
 v1: status=FREE,  version=3          tx 100               tx 205
 v2: status=HELD,  version=4          tx 205               –
 Tx 204 (snapshot before 205 committed) still sees v1. Tx 206 sees v2.
```

Consequences:
- **Readers don't block writers and writers don't block readers.** Only writer-vs-writer on the same row conflicts (row lock).
- Old versions become **dead tuples** → cleaned by **VACUUM** (autovacuum). Long-running transactions prevent cleanup → **bloat**. Don't leave transactions open (e.g. `idle in transaction` sessions).
- Isolation levels in Postgres:

| Level | Snapshot | Prevents | Postgres note |
|---|---|---|---|
| Read Committed (default) | New snapshot **per statement** | Dirty reads | Non-repeatable reads and lost updates possible |
| Repeatable Read | One snapshot **per transaction** | + non-repeatable reads, phantoms (in PG) | Concurrent update of same row → serialization error, retry |
| Serializable | + dependency tracking (SSI) | All anomalies incl. write skew | Retries required on `40001` errors |

### Locking strategies for "two users, one seat" (P2)

| Strategy | SQL | Behaviour |
|---|---|---|
| **Optimistic** (`@Version`) | `UPDATE seats SET status='HELD', version=version+1 WHERE id=? AND version=?` | No lock held while thinking; 0 rows updated ⇒ conflict ⇒ `OptimisticLockException` → 409 |
| **Pessimistic** | `SELECT … FROM seats WHERE id=? FOR UPDATE` | Second transaction **waits** for the first to commit |
| **Constraint** | Unique partial index on active holds per seat | Second `INSERT` fails with unique violation |
| **Atomic conditional update** | `UPDATE seats SET status='HELD' WHERE id=? AND status='FREE'` | Check-and-act in one statement; row count tells you who won |

Optimistic fits low contention and short user flows; pessimistic fits high contention on hot rows
(but hold locks briefly). Constraints are your last line of defence regardless.

---

## 5. The query planner

SQL is declarative; the **planner/optimizer** chooses *how*: scan types, join algorithms, join order,
using **statistics** (row counts, value distributions, `n_distinct`, histograms) and a **cost model**.

| Node | When |
|---|---|
| Seq Scan | Reading much of the table, or no usable index |
| Index Scan | Few rows, fetch heap tuples via index |
| Index Only Scan | All needed columns in the index (and visibility map says pages are all-visible) |
| Bitmap Heap Scan | Medium selectivity; collect ctids, read pages in order |
| Nested Loop | Small outer input, indexed inner lookup |
| Hash Join | Larger unsorted inputs, equality join |
| Merge Join | Both inputs sorted on join key |
| Sort / Top-N heapsort | `ORDER BY` without a matching index (`LIMIT` → top-N) |

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT checked_at, status, latency_ms
FROM check_results
WHERE monitor_id = 42
ORDER BY checked_at DESC
LIMIT 50;
```

Read it bottom-up / inside-out. Compare **estimated rows vs actual rows** — big mismatches mean bad
statistics or correlated columns. `BUFFERS` shows `shared hit` (cache) vs `read` (disk). P4 Week 22
evidence: before/after plans for the composite index go into your README.

---

## 6. Connection handling

Each Postgres connection is a **separate OS process** (a few MB of RAM). Hundreds of connections
hurt → keep app pools small (Hikari default 10) and use a pooler (PgBouncer, RDS Proxy) at scale.
More connections than cores × small factor rarely makes a database faster.

---

## 7. Replication basics

```
           writes                        WAL stream
App ───────────────► Primary ─────────────────────────► Replica 1 (read-only)
    ◄─── reads ──────┘   │                              Replica 2 (read-only)
    ◄─── reads (may be stale) ─────────────────────────────┘
```

| Concept | Meaning |
|---|---|
| **Streaming replication** | Replicas apply the primary's WAL continuously |
| **Asynchronous** | Primary commits without waiting → replicas **lag** (ms–s); failover can lose the last few transactions |
| **Synchronous** | Primary waits for replica ack → no loss, higher write latency (RDS Multi-AZ standby) |
| **Read replica** | Serve reads (status page, reports) to offload the primary |
| **Replication lag** | Read-your-own-writes problem: user creates a monitor, next page reads from replica, monitor "missing" → route that user's reads to the primary for a while |
| **Failover** | Promote a replica to primary; clients reconnect via DNS/endpoint |
| **Logical replication** | Row-change level; for upgrades, CDC, selective tables |

Replication gives **availability and read scaling, not write scaling**. Writes scale via
**sharding/partitioning** (see [`15-system-design/scalability.md`](../15-system-design/scalability.md)).
Postgres **table partitioning** (e.g. `check_results` by month) is a single-node technique that makes
retention cleanup a cheap `DROP TABLE` of an old partition — a nice P4 follow-up.

---

## 8. Other storage engines (awareness)

- **LSM trees** (Cassandra, RocksDB, many time-series DBs): writes go to an in-memory table + append-only sorted files, merged in background (compaction). Very fast writes, reads may check several files (bloom filters help). B-tree: balanced reads/writes, in-place updates.
- **Column stores** (Redshift, ClickHouse, BigQuery): store each column contiguously → fast aggregations over few columns of billions of rows (analytics, OLAP) vs row stores for OLTP.
- **In-memory stores** (Redis): data in RAM, optional persistence (RDB snapshots, AOF log) — see [`04-sql-databases/redis.md`](../04-sql-databases/redis.md).

---

## 9. Break → Debug drills

- [ ] Insert 1 M rows into `check_results`; `EXPLAIN ANALYZE` the status query before/after the composite index; record timings.
- [ ] Create the index as `(checked_at, monitor_id)` instead; explain why the plan got worse.
- [ ] Open two `psql` sessions: `BEGIN; SELECT … FOR UPDATE` in one, try to update the same row in the other — observe the wait.
- [ ] Two sessions updating two rows in opposite order → `ERROR: deadlock detected`.
- [ ] Leave a transaction open, update rows in a loop from another session, check dead tuples in `pg_stat_user_tables.n_dead_tup`.

---

## 10. Interview questions (quick)

<details><summary>How does a database index work?</summary>

Usually a B+tree: a balanced, high-fan-out tree of sorted keys pointing to row locations, with linked
leaves for range scans. Lookups cost O(log n) page reads — 3–4 for huge tables. Trade-off: extra
storage and slower writes.
</details>

<details><summary>What is MVCC?</summary>

Updates create new row versions; each transaction reads a consistent snapshot of committed versions.
Readers and writers don't block each other; old versions are removed by VACUUM.
</details>

<details><summary>What is the WAL for?</summary>

Durability and crash recovery: changes are logged sequentially and fsynced at commit before data
pages are written, so a crash can be recovered by replaying the log. It also powers replication and
point-in-time recovery.
</details>

<details><summary>Optimistic vs pessimistic locking?</summary>

Optimistic: no locks; detect conflicts at write time via a version column and retry/fail — good for
low contention. Pessimistic: lock the row (`SELECT … FOR UPDATE`) so others wait — good for high
contention on hot rows, risks waits and deadlocks.
</details>

<details><summary>Does adding a read replica help a write-heavy workload?</summary>

No — every write still goes to the primary and is replayed on every replica. It helps read-heavy
workloads. For writes: batch, optimize, partition, or shard.
</details>
