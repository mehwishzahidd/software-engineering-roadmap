# 07 · PostgreSQL vs MySQL (InnoDB) — The Diff You Need

> **Week 9, ≈2 hours** (with DB internals). MySQL is on the résumé, so you must defend it — but it's taught as a **diff against PostgreSQL**,
> not a second course (see [ROADMAP §2.4](../ROADMAP.md#24-weighting-fixed)). Versions: PostgreSQL 16, MySQL 8.x with InnoDB.
> Résumé-defense drill: [`../17-resume-tech-defense/mysql.md`](../17-resume-tech-defense/mysql.md).

## 1. Try it (15 minutes)

```bash
docker run --name my -e MYSQL_ROOT_PASSWORD=my -p 3306:3306 -d mysql:8.4
docker exec -it my mysql -uroot -pmy
```

```sql
-- MySQL
CREATE DATABASE tix; USE tix;
SELECT VERSION(), @@transaction_isolation;      -- 8.4.x, REPEATABLE-READ
```

## 2. Big-picture differences

| Topic | PostgreSQL 16 | MySQL 8 (InnoDB) |
|---|---|---|
| Table storage | **Heap** (unordered); every index points to a physical row location (TID) | **Clustered index**: rows stored *inside* the PK B-tree, in PK order |
| Secondary indexes | point to heap location | store the **PK value** → lookup = secondary index + PK index ("double lookup") |
| MVCC | new row version per update; old versions removed by **VACUUM** | update in place + **undo log**; old versions reconstructed from undo; purge thread cleans |
| Default isolation | **Read Committed** | **Repeatable Read** |
| Phantom protection at RR | snapshot (no phantoms for plain reads); write conflicts → serialization error | consistent snapshot for plain reads; **next-key (gap) locks** for locking reads/writes |
| DDL in transactions | **transactional** (`BEGIN; ALTER …; ROLLBACK;` works) | DDL **implicitly commits**; a failed multi-step migration can leave a half-applied schema |
| FK columns | not auto-indexed | auto-indexed |
| Auto ids | `GENERATED … AS IDENTITY` (sequence-backed), sequences are objects | `AUTO_INCREMENT` column attribute; no standalone sequences |
| Returning generated values | `RETURNING` on INSERT/UPDATE/DELETE | no `RETURNING`; use `LAST_INSERT_ID()` / JDBC `getGeneratedKeys()` |
| Upsert | `INSERT … ON CONFLICT (…) DO UPDATE/NOTHING`, `MERGE` (15+) | `INSERT … ON DUPLICATE KEY UPDATE`, `INSERT IGNORE`, `REPLACE` |
| Time zones | `timestamptz` (instant) and `timestamp` | `TIMESTAMP` (converted to UTC, range ends 2038-01-19) and `DATETIME` (no zone) |
| Booleans | real `boolean` | `BOOLEAN` = `TINYINT(1)` |
| JSON | `json` and binary **`jsonb`** with GIN indexes | binary `JSON`; index via generated columns / multi-valued indexes |
| Index types | B-tree, GIN, GiST, BRIN, hash; **partial**, **expression**, **INCLUDE** | B-tree, full-text, spatial; functional indexes (8.0.13+); no partial, no INCLUDE |
| `FULL OUTER JOIN` | yes | **no** (emulate with `LEFT JOIN … UNION … RIGHT JOIN`) |
| CHECK constraints | always enforced | enforced since **8.0.16** (silently ignored before!) |
| Window functions / CTEs | yes (long-standing) | since 8.0 |
| String comparison | case-**sensitive** by default | default collation `utf8mb4_0900_ai_ci` is case- and accent-**insensitive** |
| Identifier quoting | `"double quotes"` | `` `backticks` `` (double quotes only in ANSI_QUOTES mode) |
| `||` | string concatenation | logical OR (unless `PIPES_AS_CONCAT`); use `CONCAT()` |
| Extensions | rich (`pg_trgm`, PostGIS, `citext`, …) | plugins, fewer |

## 3. Clustered index consequences ⭐

- A range scan on the PK (`WHERE id BETWEEN …`) reads rows that are **physically adjacent** — very fast in InnoDB.
- **Random PKs (UUIDv4) hurt InnoDB more**: inserts land in random leaf pages → page splits, fragmentation, cache misses. Prefer `BIGINT AUTO_INCREMENT` or time-ordered ids (UUIDv7 stored as `BINARY(16)`).
- **Wide PKs bloat every secondary index** (each entry carries the PK).
- A secondary index on `(customer_id)` implicitly contains `id`, so `SELECT id FROM orders WHERE customer_id = 5` is covered.
- In Postgres, table order is insertion-ish; `CLUSTER` reorders once but isn't maintained.

## 4. Repeatable Read and gap locks ⭐

InnoDB at REPEATABLE READ:

- **Plain `SELECT`** = consistent (snapshot) read, no locks — similar to Postgres RR.
- **Locking reads** (`SELECT … FOR UPDATE / FOR SHARE`), `UPDATE`, `DELETE` lock the index records they scan **plus the gaps between them** (*next-key locks*), to stop other transactions from inserting phantoms into the range.

```sql
-- MySQL, session A
START TRANSACTION;
SELECT * FROM bookings WHERE event_id = 8 FOR UPDATE;   -- locks existing rows AND the gap(s) around event_id = 8

-- MySQL, session B
INSERT INTO bookings (event_id, seat_id) VALUES (8, 42);  -- BLOCKS until A finishes (insert into a locked gap)
```

Surprises this causes: inserts blocked by a transaction that "only read", and **deadlocks between two inserters** that both hold gap locks. With no index on the filtered column, InnoDB scans (and locks) far more — **indexes affect locking in MySQL**, not just speed.
Teams often switch MySQL to `READ COMMITTED` to reduce gap locking (and match Postgres/Oracle behaviour).

Postgres never takes gap locks; it prevents anomalies with snapshots and (at SERIALIZABLE) SSI predicate tracking that *aborts* rather than blocks.

## 5. Syntax diffs you will hit

### Auto-increment / identity

```sql
-- PostgreSQL
CREATE TABLE venue (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, name text NOT NULL);
INSERT INTO venue (name) VALUES ('Maple Hall') RETURNING id;
```

```sql
-- MySQL
CREATE TABLE venue (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200) NOT NULL);
INSERT INTO venue (name) VALUES ('Maple Hall');
SELECT LAST_INSERT_ID();          -- per-connection, safe under concurrency
```

Both leave gaps on rollback. Postgres: `GENERATED ALWAYS` rejects explicit ids unless `OVERRIDING SYSTEM VALUE`; `BY DEFAULT` allows them (then fix the sequence with `setval`, as in [`practice-schema.sql`](./practice-schema.sql)).

### Upsert

```sql
-- PostgreSQL
INSERT INTO event_stats (event_id, views) VALUES (1, 5)
ON CONFLICT (event_id) DO UPDATE SET views = event_stats.views + EXCLUDED.views;

INSERT INTO event_stats (event_id, views) VALUES (1, 5)
ON CONFLICT DO NOTHING;
```

```sql
-- MySQL 8.0.19+ (row alias; the older VALUES(col) form is deprecated)
INSERT INTO event_stats (event_id, views) VALUES (1, 5) AS new
ON DUPLICATE KEY UPDATE views = event_stats.views + new.views;

INSERT IGNORE INTO event_stats (event_id, views) VALUES (1, 5);   -- also ignores OTHER errors -> dangerous
REPLACE INTO event_stats (event_id, views) VALUES (1, 5);         -- DELETE + INSERT: fires delete, new auto-id, cascades!
```

MySQL's `ON DUPLICATE KEY` fires on **any** unique key conflict (you can't name the target); Postgres requires you to name it.

### Misc

| Task | PostgreSQL | MySQL |
|---|---|---|
| Concatenate | `a || b`, `concat(a, b)` | `CONCAT(a, b)` |
| Case-insensitive match | `ILIKE`, `lower(x) = …`, `citext` | default collation already case-insensitive |
| Current time | `now()` (txn start), `clock_timestamp()` | `NOW()`, `SYSDATE()` |
| Date truncation | `date_trunc('month', ts)` | `DATE_FORMAT(ts, '%Y-%m-01')` |
| Interval math | `ts + INTERVAL '7 days'` | `ts + INTERVAL 7 DAY`, `DATE_ADD` |
| Limit/offset | `LIMIT 20 OFFSET 40` | same, or `LIMIT 40, 20` |
| Top-1 per group | `DISTINCT ON` | `ROW_NUMBER()` subquery |
| Explain | `EXPLAIN (ANALYZE, BUFFERS)` | `EXPLAIN FORMAT=TREE`, `EXPLAIN ANALYZE` (8.0.18+) |
| Describe table | `\d t` | `DESCRIBE t;` / `SHOW CREATE TABLE t;` |
| Skip locked | `FOR UPDATE SKIP LOCKED` | same (8.0+) |
| Charset | UTF-8 database | use **`utf8mb4`** (MySQL's old `utf8` is 3-byte and can't store emoji) |

## 6. JDBC / Spring differences

- URLs: `jdbc:postgresql://localhost:5432/tixhub` vs `jdbc:mysql://localhost:3306/tix?serverTimezone=UTC`.
- Hibernate: `GenerationType.IDENTITY` disables JDBC **insert batching** on both; with Postgres you can use `SEQUENCE` + `allocationSize` to batch. MySQL has no sequences, so `IDENTITY` is the norm.
- Flyway works for both, but on MySQL a failed migration may be **partially applied** (DDL auto-commit) → keep one DDL statement per migration where possible.
- Case-insensitive collation: a `UNIQUE(email)` in MySQL already rejects `Ava@x.com` vs `ava@x.com`; in Postgres it doesn't.

## 7. When to pick which

| Pick PostgreSQL when | Pick MySQL when |
|---|---|
| Complex queries, analytics, window functions, CTEs, rich types (jsonb, arrays, ranges) | Simple, high-volume OLTP reads/writes with a well-known access pattern |
| You want transactional DDL (safer migrations) | The team/org already runs MySQL at scale (ops expertise, tooling, managed services) |
| Extensions (PostGIS, trigram search, `citext`) | Ecosystem expects it (many PHP/WordPress-era stacks) |
| Strict standards compliance and constraints | Replication topologies your ops team knows well |

Honest answer in interviews: *both are excellent, mature, ACID relational databases; the biggest practical factors are team expertise and the features you need. For this roadmap's projects I chose Postgres for transactional Flyway migrations, partial indexes, jsonb, and `SKIP LOCKED`.*

## 8. 🔨 Break it

1. In MySQL, create `customers(email VARCHAR(100) UNIQUE)` and insert `a@x.com` and `A@x.com`. Compare with Postgres.
2. In MySQL: `START TRANSACTION; CREATE TABLE t1 (id INT); ROLLBACK;` — does `t1` exist? Do the same in Postgres.
3. Reproduce the gap-lock block from §4 (create `bookings` with an index on `event_id`). Then drop the index and repeat — how much gets locked now?
4. `SELECT 'a' || 'b';` in both.
5. Use `REPLACE INTO` on a row that has child rows with `ON DELETE CASCADE`. What happened to the children?

## 9. 🎤 Interview Q&A

<details><summary>What are the main differences between PostgreSQL and MySQL?</summary>

Storage (PG heap vs InnoDB clustered PK index), MVCC implementation (PG row versions + VACUUM vs InnoDB undo logs), default isolation (Read Committed vs Repeatable Read with gap locks), transactional DDL in PG, richer index types and data types in PG (partial/expression/GIN, jsonb, arrays), upsert syntax, and RETURNING support.
</details>

<details><summary>What is a clustered index? Does Postgres have one?</summary>

A clustered index stores the table rows themselves in index order — InnoDB does this for the primary key, so each table *is* a B-tree. Postgres stores rows in an unordered heap; all indexes are secondary. `CLUSTER` physically reorders once but isn't maintained.
</details>

<details><summary>What are gap locks?</summary>

InnoDB locks on the gap between index records (part of next-key locking) taken by locking reads and writes at Repeatable Read, to prevent other transactions inserting phantom rows into a scanned range. They can block inserts and cause deadlocks unexpectedly.
</details>

<details><summary>Why are random UUID primary keys worse in MySQL?</summary>

Because the PK is the clustered index, random keys scatter inserts across the tree, causing page splits and poor cache locality, and each secondary index stores the (wide) PK. Use auto-increment or time-ordered ids.
</details>

<details><summary>How do you do an upsert in each?</summary>

Postgres: `INSERT … ON CONFLICT (cols) DO UPDATE SET col = EXCLUDED.col`. MySQL: `INSERT … ON DUPLICATE KEY UPDATE col = new.col` (row alias, 8.0.19+). Avoid `REPLACE` (delete + insert) and `INSERT IGNORE` (swallows other errors).
</details>

<details><summary>Which would you choose for a new project?</summary>

Depends on requirements and team expertise; default to Postgres for feature richness and safety (transactional DDL, constraints, jsonb, partial indexes), MySQL if the organisation already operates it well and the workload is straightforward OLTP.
</details>

## ✅ Mastery checklist

- [ ] Ran MySQL 8 in Docker and executed §8 experiments 1, 2, 4
- [ ] Can explain clustered vs heap storage with a diagram
- [ ] Can explain RR + gap locks vs Postgres snapshots in two minutes
- [ ] Can write upserts in both dialects from memory
- [ ] Have a 30-second honest "Postgres vs MySQL" answer tied to my projects
