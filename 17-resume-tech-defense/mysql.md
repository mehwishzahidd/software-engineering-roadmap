# MySQL — Résumé Defense

**Target level:** L2 · **Learned:** Week 8 (taught as a *diff* against PostgreSQL, ≈2 h) · **Version:** MySQL 8.0/8.4 LTS, InnoDB engine
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md) · General SQL: [sql.md](./sql.md) · Primary DB: [postgresql.md](./postgresql.md)

> **Honesty note:** MySQL is L2 on purpose. If your past role used MySQL, describe that truthfully.
> Your current depth is PostgreSQL; say so, and show you know the differences that matter.

---

## 1. Beginner questions

<details><summary><b>Q1. What is MySQL and what is InnoDB?</b></summary>

MySQL is an open-source relational database (Oracle). Storage engines are pluggable; InnoDB is the
default since 5.5 — transactional (ACID), row-level locking, foreign keys, crash recovery, MVCC.
MyISAM is legacy: no transactions, table-level locks, no FKs.
</details>

<details><summary><b>Q2. How do auto-increment keys work?</b></summary>

`id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY`. Get the generated value via
`LAST_INSERT_ID()` or JDBC `getGeneratedKeys()` (no `RETURNING` in MySQL). Gaps are normal (rollbacks, failed inserts).
</details>

<details><summary><b>Q3. What character set should you use?</b></summary>

`utf8mb4` (real 4-byte UTF-8, supports emoji) — default in MySQL 8 with collation `utf8mb4_0900_ai_ci`.
Old `utf8` (= `utf8mb3`) can't store 4-byte characters.
</details>

<details><summary><b>Q4. Basic CLI usage?</b></summary>

`mysql -h host -u user -p dbname`; `SHOW DATABASES; USE db; SHOW TABLES; DESCRIBE t; SHOW CREATE TABLE t; SHOW INDEX FROM t;`.
</details>

<details><summary><b>Q5. How is quoting different from PostgreSQL?</b></summary>

MySQL quotes identifiers with backticks (`` `order` ``); Postgres uses double quotes. With default
`sql_mode`, MySQL treats double quotes as string delimiters (unless `ANSI_QUOTES`).
</details>

## 2. Intermediate questions

<details><summary><b>Q6. What is a clustered index in InnoDB and why does it matter?</b></summary>

The table *is* a B+tree ordered by the primary key; rows are stored in the PK leaf pages. Secondary
indexes store the PK value, so a secondary lookup = index lookup + PK lookup. Consequences: keep PKs
short; random UUID v4 PKs cause page splits and fragmentation (prefer auto-increment or time-ordered IDs, `BINARY(16)` if UUIDs).
Postgres, by contrast, uses heap tables with separate indexes.
</details>

<details><summary><b>Q7. Default isolation level and how it behaves?</b></summary>

REPEATABLE READ (Postgres: READ COMMITTED). Consistent snapshot reads for plain `SELECT`; locking
reads (`SELECT ... FOR UPDATE`) and writes use next-key/gap locks that prevent phantoms in the locked range —
which also increases lock contention and deadlock likelihood.
</details>

<details><summary><b>Q8. Upsert syntax?</b></summary>

`INSERT ... ON DUPLICATE KEY UPDATE amount = VALUES(amount)` (8.0.20+ prefers row alias:
`INSERT ... AS new ON DUPLICATE KEY UPDATE amount = new.amount`). `INSERT IGNORE` skips errors (dangerous: also ignores other errors). Postgres: `ON CONFLICT`.
</details>

<details><summary><b>Q9. What features did MySQL 8 add that close the gap with Postgres?</b></summary>

Window functions, CTEs (including recursive), `SKIP LOCKED`/`NOWAIT`, CHECK constraints enforced (8.0.16+),
atomic DDL (single statements, but DDL still implicitly commits the transaction), descending indexes, invisible indexes, functional indexes (8.0.13+), JSON improvements.
</details>

<details><summary><b>Q10. What is <code>sql_mode</code> and why care?</b></summary>

Controls strictness. MySQL 8 defaults include `STRICT_TRANS_TABLES` and `ONLY_FULL_GROUP_BY`. Without
strict mode MySQL silently truncates/coerces bad data (e.g. `'abc'` → 0). Never disable strict mode to "fix" errors.
</details>

<details><summary><b>Q11. How does replication work at a high level?</b></summary>

Primary writes changes to the binary log; replicas pull and apply them (asynchronous by default,
semi-sync optional). Row-based binlog format is the default. Replica lag means reads from replicas can be stale.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "You have MySQL on your résumé — how did you use it?"</b></summary>

- Truthful past scope only (e.g. "the application at my previous role ran on MySQL; I wrote queries and simple migrations").
- "My deeper, current work is PostgreSQL. I've studied the MySQL/InnoDB differences — clustered PK, REPEATABLE READ default,
  gap locks, upsert syntax — and ported Ledger's schema to MySQL 8 to check them."
</details>

<details><summary><b>R2. "What would you watch out for migrating a Postgres app to MySQL?"</b></summary>

- No `RETURNING` (use generated keys), `ON CONFLICT` → `ON DUPLICATE KEY UPDATE`, no transactional DDL,
  `timestamptz` → `TIMESTAMP` (UTC-converted, range to 2038) or `DATETIME` (no zone), JSONB → JSON (no GIN; use generated columns + indexes),
  no partial indexes, identifier quoting, `BOOLEAN` is `TINYINT(1)`, case-insensitive default collation affects uniqueness.
- Re-run the full test suite against MySQL via Testcontainers.
</details>

<details><summary><b>R3. "Why might UUID primary keys hurt in MySQL more than in Postgres?"</b></summary>

- InnoDB clusters rows by PK → random inserts scatter across pages → page splits, poor cache locality, larger secondary indexes (each stores the PK).
- Mitigate: auto-increment internal PK + UUID as unique secondary column, or time-ordered UUIDs (v7) in `BINARY(16)`.
</details>

<details><summary><b>R4. "Why did you choose Postgres over MySQL for your projects?"</b></summary>

- Features I used: `timestamptz`, partial indexes, `RETURNING`, transactional DDL for Flyway migrations, JSONB.
- Balanced: MySQL is excellent, fast for simple read-heavy workloads, huge ecosystem; either would work for TicketHold.
</details>

## 4. Practical tasks (live)

- [ ] Run `mysql:8.4` in Docker and connect with the `mysql` client.
- [ ] Port Ledger's `schema.sql` to MySQL (types, identity → `AUTO_INCREMENT`, quoting) and load sample data.
- [ ] Rewrite a Postgres upsert as `ON DUPLICATE KEY UPDATE`.
- [ ] Run `EXPLAIN` / `EXPLAIN ANALYZE` (8.0.18+) on a report query and add an index.
- [ ] Show a gap-lock wait with two sessions under REPEATABLE READ.

## 5. Debugging questions

<details><summary><b>D1. "Incorrect string value: '\xF0\x9F...'" on insert.</b></summary>

Column/table/connection uses `utf8mb3`. Convert to `utf8mb4` and ensure the JDBC connection uses it
(MySQL Connector/J 8 negotiates utf8mb4 with an 8.0 server by default; check `characterEncoding`).
</details>

<details><summary><b>D2. "Lock wait timeout exceeded; try restarting transaction".</b></summary>

A transaction waited longer than `innodb_lock_wait_timeout` (50 s default). Find blockers with
`performance_schema.data_lock_waits` / `sys.innodb_lock_waits`, `SHOW ENGINE INNODB STATUS`; shorten
transactions, add indexes so locking reads lock fewer rows (unindexed `UPDATE ... WHERE` can lock far more rows).
</details>

<details><summary><b>D3. "Expression #1 of SELECT list is not in GROUP BY clause…" (ONLY_FULL_GROUP_BY).</b></summary>

The query selects a non-aggregated column not functionally dependent on the GROUP BY. Fix the query
(add to GROUP BY or aggregate it, or `ANY_VALUE()` if truly arbitrary) — don't remove the mode.
</details>

<details><summary><b>D4. A unique constraint rejects 'Alice' because 'alice' exists.</b></summary>

Default collation `utf8mb4_0900_ai_ci` is case- and accent-insensitive. Use a `_bin`/`_as_cs` collation on that column if case-sensitivity is required.
</details>

## 6. Architecture questions

- When would a team pick MySQL over PostgreSQL (existing expertise/ops tooling, read-heavy web workload, managed Aurora MySQL)?
- How does InnoDB's clustered PK influence schema design choices you'd make differently than in Postgres?
- Read replicas for scaling reads: how to handle read-your-writes consistency.
- Keeping an app portable across both (JPA + Flyway per-vendor migrations, Testcontainers matrix) — worth the cost?

## 7. Common mistakes

- Using MyISAM or `utf8` (utf8mb3).
- Disabling strict `sql_mode`.
- Random UUID PKs in `CHAR(36)`.
- Assuming DDL can be rolled back inside a transaction.
- `INSERT IGNORE` hiding real errors.
- Assuming Postgres isolation behaviour (READ COMMITTED) in MySQL.

## 8. Terminology I must know

| Term | One-line meaning |
|---|---|
| InnoDB | Default transactional storage engine |
| Clustered index | Table stored ordered by the PK |
| Secondary index | Index whose leaves point to PK values |
| Gap lock / next-key lock | Locks on ranges between index records to prevent phantoms |
| Binlog | Binary log of changes; replication and point-in-time recovery |
| `sql_mode` | Server strictness and SQL-behaviour flags |
| `utf8mb4` | Full UTF-8 character set |
| Collation | Rules for comparing/sorting strings |
| Buffer pool | InnoDB's main memory cache for pages |
| `AUTO_INCREMENT` | Auto-generated integer column |
| Aurora MySQL | AWS's MySQL-compatible managed engine |

## 9. When to use it

When the team/company already runs MySQL, read-heavy web apps, managed MySQL/Aurora is the standard,
or existing tooling/replication expertise is MySQL-centric.

## 10. When NOT to use it

When you need Postgres-specific features (partial indexes, rich types, transactional DDL, advanced
extensions) or strict standards behaviour; not as a cache or search engine.

## 11. Trade-offs (vs PostgreSQL)

| MySQL / InnoDB | PostgreSQL |
|---|---|
| Clustered PK → fast PK range scans | Heap + indexes → PK choice less critical |
| REPEATABLE READ default + gap locks | READ COMMITTED default, SSI for SERIALIZABLE |
| Undo log for MVCC (purge thread) | Tuple versions in table (VACUUM) |
| DDL implicitly commits | Transactional DDL |
| `ON DUPLICATE KEY UPDATE`, no `RETURNING` | `ON CONFLICT`, `RETURNING` |
| Simpler, very popular replication | Richer types/indexes/extensions |

## 12. How it interacts with the rest of my stack

- **Java/JDBC**: MySQL Connector/J driver (`com.mysql:mysql-connector-j`); `getGeneratedKeys()` for IDs.
- **Spring Boot**: change datasource URL/driver; Hibernate picks MySQL dialect; Flyway supports MySQL (non-transactional DDL — a failed migration can leave partial changes).
- **Docker**: `mysql:8.4` image with `MYSQL_ROOT_PASSWORD`/`MYSQL_DATABASE`.
- **AWS**: RDS MySQL or Aurora MySQL.
- **CI**: Testcontainers `MySQLContainer` to validate portability.
- **React**: no direct interaction.

## 13. Hands-on exercise

**Port Ledger's persistence to MySQL 8 and record the differences.**

Acceptance criteria:
- [ ] Ledger's `schema.sql` rewritten for MySQL (`utf8mb4`, `AUTO_INCREMENT`, `DECIMAL(12,2)`, `DATETIME`/`TIMESTAMP` choice justified).
- [ ] The JDBC repository integration test passes against a MySQL Testcontainer (dedupe via `ON DUPLICATE KEY` or `INSERT IGNORE` with a justified choice).
- [ ] A `DIFFERENCES.md` note (in your own practice folder) with ≥ 8 concrete differences hit during the port.
- [ ] You can explain InnoDB's clustered index in 60 s.

## 14. Mastery checklist

- [ ] Explain InnoDB vs MyISAM.
- [ ] Explain clustered vs secondary indexes and the UUID PK issue.
- [ ] State default isolation levels for MySQL and Postgres and the practical difference.
- [ ] Write MySQL upserts and retrieve generated keys.
- [ ] Explain `sql_mode`, `utf8mb4`, and collation effects.
- [ ] List 8 Postgres ↔ MySQL differences from memory.
- [ ] Describe my real MySQL experience honestly in 30 s.

## Evidence in my projects

| Project | What it demonstrates | Fill in: file / commit |
|---|---|---|
| P1 Ledger (exercise) | Schema port + integration test on MySQL 8 via Testcontainers | |
| P2 TicketHold (optional) | Swap datasource to MySQL in a branch; note which features break (partial index, `RETURNING`) | |

Main projects use PostgreSQL — state that plainly if asked.

## Where to learn it in this repo

[`../04-sql-databases/07-postgres-vs-mysql.md`](../04-sql-databases/07-postgres-vs-mysql.md) · [`../04-sql-databases/06-transactions.md`](../04-sql-databases/06-transactions.md) ·
[`../04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md) · [`../04-sql-databases/08-jdbc-orm.md`](../04-sql-databases/08-jdbc-orm.md) ·
[`../14-cs-fundamentals/database-internals.md`](../14-cs-fundamentals/database-internals.md)
