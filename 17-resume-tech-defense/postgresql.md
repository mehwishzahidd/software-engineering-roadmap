# PostgreSQL — Résumé Defense

**Target level:** L3 · **Learned:** Weeks 5–8 (psql, schema, JDBC, transactions, `EXPLAIN`), Week 10 (Flyway, Docker Compose), Week 21 (RDS), Week 22 (index tuning) · **Version:** PostgreSQL 16
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md) · General SQL: [sql.md](./sql.md)

---

## 1. Beginner questions

<details><summary><b>Q1. What is PostgreSQL and why pick it?</b></summary>

Open-source object-relational database: strong SQL standards compliance, MVCC, transactional DDL,
rich types (JSONB, arrays, `uuid`, ranges, `timestamptz`), extensible (extensions like `pg_stat_statements`),
and widely available managed (RDS, Cloud SQL).
</details>

<details><summary><b>Q2. Common <code>psql</code> commands?</b></summary>

`\l` databases, `\c db` connect, `\dt` tables, `\d table` describe, `\di` indexes, `\x` expanded
output, `\timing` query time, `\i file.sql` run a file, `\q` quit.
</details>

<details><summary><b>Q3. How do you generate IDs?</b></summary>

`id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY` (SQL-standard, preferred over `serial`), or
`uuid` (`gen_random_uuid()` built in since 13). UUIDs for public/unguessable IDs; bigint for compact, index-friendly keys.
</details>

<details><summary><b>Q4. <code>timestamp</code> vs <code>timestamptz</code>?</b></summary>

`timestamptz` stores an absolute instant (normalised to UTC) and converts on display per session
time zone; `timestamp` has no zone. Use `timestamptz` for events (`checked_at`); map to `Instant`/`OffsetDateTime` in Java.
</details>

<details><summary><b>Q5. <code>json</code> vs <code>jsonb</code>?</b></summary>

`json` stores text as-is; `jsonb` stores a parsed binary form — slower insert, faster queries,
supports GIN indexes and containment (`@>`). Use `jsonb`, and only for genuinely flexible data.
</details>

## 2. Intermediate questions

<details><summary><b>Q6. What is MVCC?</b></summary>

Multi-Version Concurrency Control: an `UPDATE` writes a new row version; readers see a snapshot
based on transaction IDs, so readers don't block writers and vice versa. Old versions (dead tuples)
are cleaned by VACUUM.
</details>

<details><summary><b>Q7. What does VACUUM / autovacuum do?</b></summary>

Reclaims space from dead tuples for reuse, updates the visibility map (enables index-only scans),
and prevents transaction-ID wraparound. `ANALYZE` updates planner statistics. Autovacuum does both
automatically; heavy update/delete tables may need tuning. `VACUUM FULL` rewrites the table with an exclusive lock.
</details>

<details><summary><b>Q8. Isolation levels in Postgres?</b></summary>

Default READ COMMITTED (each statement sees a fresh snapshot). REPEATABLE READ = snapshot for the
whole transaction (no phantoms in Postgres; conflicting updates fail with serialization error).
SERIALIZABLE uses SSI — may abort with `40001`; the app must retry. READ UNCOMMITTED behaves as READ COMMITTED.
</details>

<details><summary><b>Q9. Row locking options?</b></summary>

`SELECT ... FOR UPDATE` locks rows for modification; `FOR SHARE` weaker; `NOWAIT` errors instead of
waiting; `SKIP LOCKED` skips locked rows — ideal for job-queue workers (PulseWatch alert jobs).
</details>

<details><summary><b>Q10. Index types?</b></summary>

B-tree (default; equality/range/sort), Hash (equality), GIN (JSONB, arrays, full-text), GiST
(geometric, ranges, exclusion constraints), BRIN (huge, naturally ordered tables like time-series).
Also partial (`WHERE status = 'ACTIVE'`), expression (`lower(email)`), covering (`INCLUDE`).
</details>

<details><summary><b>Q11. How do you read <code>EXPLAIN (ANALYZE, BUFFERS)</code>?</b></summary>

Tree of nodes, read inside-out. Compare estimated `rows` vs actual; check node types (Seq Scan,
Index Scan, Index Only Scan, Bitmap Heap Scan, Nested Loop/Hash Join/Merge Join, Sort with
`external merge` = spilled to disk), `actual time`, and buffers (shared hit vs read). Note: ANALYZE actually runs the query.
</details>

<details><summary><b>Q12. What is <code>ON CONFLICT</code> and <code>RETURNING</code>?</b></summary>

`INSERT ... ON CONFLICT (bank_ref) DO NOTHING` / `DO UPDATE SET ... = EXCLUDED....` is an atomic upsert
relying on a unique index. `RETURNING id` returns values from inserted/updated rows in one round trip.
</details>

<details><summary><b>Q13. Why use a connection pool? How many connections?</b></summary>

Each Postgres connection is a backend process (memory + fork cost). Pools (HikariCP in-app, PgBouncer
externally) reuse connections. Size small: HikariCP's starting formula is `(db_cores × 2) + effective_spindle_count`
(Hikari's default max is 10), then measure — more connections often reduce throughput. Postgres `max_connections` default is 100, shared by all app instances.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "Tell me about your PostgreSQL experience."</b></summary>

- Truthful past (e.g. "queried and occasionally modified tables in an existing Postgres DB; DBAs owned the schema").
- Current: Ledger (JDBC, atomic import, unique-constraint dedupe, `EXPLAIN`), TicketHold (Flyway, JPA,
  optimistic locking), PulseWatch on RDS with an `EXPLAIN`-driven composite index on `check_results`.
</details>

<details><summary><b>R2. "How did you prevent double booking at the database level?"</b></summary>

- `UNIQUE (seat_id)` on bookings (active), `@Version` column → `UPDATE ... WHERE version = ?` affects 0 rows on conflict → 409.
- Alternative: `SELECT ... FOR UPDATE` (pessimistic) when contention is high; trade-off: waiting/deadlock risk vs retries.
</details>

<details><summary><b>R3. "Your query got slow as the table grew. Walk me through fixing it."</b></summary>

- PulseWatch story: dashboard query `WHERE monitor_id = ? AND checked_at > now() - interval '24 hours' ORDER BY checked_at DESC`
  → `EXPLAIN ANALYZE` showed Seq Scan + Sort → `CREATE INDEX CONCURRENTLY ON check_results (monitor_id, checked_at DESC)`
  → Index Scan, no sort; numbers in README. Plus retention cleanup to bound table size.
</details>

<details><summary><b>R4. "How do you change a schema in production safely?"</b></summary>

- Versioned migrations (Flyway), never edit applied ones. Backward-compatible steps (expand → migrate → contract).
- `CREATE INDEX CONCURRENTLY` (not inside a transaction; Flyway needs that migration non-transactional), add NOT NULL after backfill, beware locks from `ALTER TABLE`; `lock_timeout` to avoid queueing behind long locks.
</details>

<details><summary><b>R5. "PostgreSQL vs MySQL — why Postgres for your projects?"</b></summary>

- Transactional DDL, richer types (JSONB, arrays, `timestamptz`), partial/expression indexes, `RETURNING`, `SKIP LOCKED`
  (MySQL 8 has it too), strong standards compliance. MySQL: simpler replication story, widespread in LAMP, InnoDB clustered PK.
- "Either would work; I chose Postgres for features and because RDS supports it well." See [mysql.md](./mysql.md).
</details>

## 4. Practical tasks (live)

- [ ] Start Postgres 16 in Docker (`docker run -e POSTGRES_PASSWORD=... -p 5432:5432 postgres:16`) and connect with `psql`.
- [ ] Create a schema with identity PK, FK, `timestamptz`, `NUMERIC(12,2)`, CHECK and UNIQUE constraints.
- [ ] Demonstrate a lost update in two `psql` sessions, then fix with `FOR UPDATE`.
- [ ] Use `EXPLAIN (ANALYZE, BUFFERS)` before/after adding a composite index on 1M generated rows (`generate_series`).
- [ ] Write an upsert with `ON CONFLICT` and `RETURNING`.
- [ ] Implement a job-claim query with `FOR UPDATE SKIP LOCKED`.
- [ ] Take a dump and restore it (`pg_dump -Fc`, `pg_restore`).

## 5. Debugging questions

<details><summary><b>D1. "FATAL: sorry, too many clients already".</b></summary>

Connections exhausted: pools too large across instances, leaked connections, or tools left open. Check
`SELECT count(*), state FROM pg_stat_activity GROUP BY state;`. Fix pool sizing / leaks; consider PgBouncer.
</details>

<details><summary><b>D2. Queries hang; nothing errors.</b></summary>

Lock waits. Query `pg_stat_activity` (`wait_event_type = 'Lock'`) and `pg_locks`, or `pg_blocking_pids(pid)`.
Often an idle-in-transaction session holding locks — set `idle_in_transaction_session_timeout`, fix the app.
</details>

<details><summary><b>D3. "ERROR: deadlock detected".</b></summary>

Two transactions lock rows in opposite order. Postgres aborts one. Fix: consistent lock ordering
(e.g. sort seat IDs before locking), shorter transactions, retry on `40P01`.
</details>

<details><summary><b>D4. Table is huge on disk but has few rows.</b></summary>

Bloat from dead tuples — autovacuum can't keep up or is blocked by a long-running transaction. Check
`pg_stat_user_tables` (`n_dead_tup`, `last_autovacuum`); end long transactions; tune autovacuum; `VACUUM FULL`/`pg_repack` in maintenance window.
</details>

<details><summary><b>D5. App can't connect to Postgres in Docker Compose ("Connection refused").</b></summary>

Using `localhost` from inside the app container (should be the service name, e.g. `postgres:5432`), DB not ready yet
(use `depends_on` with `condition: service_healthy` + `pg_isready` healthcheck), or wrong port mapping.
</details>

## 6. Architecture questions

- Optimistic vs pessimistic locking vs SERIALIZABLE + retry for seat booking — which, and why?
- Using Postgres as a job queue (`SKIP LOCKED`) vs Redis/SQS for PulseWatch alerts.
- Retention for `check_results`: batched deletes vs time-based partitioning (drop old partitions).
- Read replicas on RDS — what consistency issue appears (replication lag) and where would you route reads?
- When is JSONB appropriate in TeamBoard (e.g. audit event payload) and when is it a design smell?

## 7. Common mistakes

- `timestamp` without time zone for events.
- Forgetting indexes on FK columns (Postgres doesn't create them automatically).
- `CREATE INDEX` (blocking writes) on a big production table instead of `CONCURRENTLY`.
- Long transactions / idle-in-transaction sessions blocking vacuum.
- Huge connection pools per instance.
- Using H2 in tests and missing Postgres-specific behaviour.

## 8. Terminology I must know

| Term | One-line meaning |
|---|---|
| MVCC | Row versioning so readers and writers don't block each other |
| Tuple | A row version |
| VACUUM | Cleans dead tuples, updates visibility map |
| WAL | Write-ahead log — durability and replication |
| Autovacuum | Background vacuum/analyze daemon |
| `pg_stat_activity` | View of current sessions and queries |
| Identity column | Standard auto-generated key |
| JSONB | Binary JSON type with indexing |
| GIN / BRIN | Index types for composite values / large ordered tables |
| Partial index | Index over a filtered subset of rows |
| `SKIP LOCKED` | Skip rows locked by others (queue pattern) |
| SSI | Serializable Snapshot Isolation |
| Schema (namespace) | Container for tables inside a database |

## 9. When to use it

Default choice for relational data in new backend services: transactions, constraints, complex
queries, moderate JSON needs, managed options everywhere.

## 10. When NOT to use it

Pure caching/ephemeral counters (Redis), massive write-heavy key-value at global scale
(DynamoDB/Cassandra), large-scale analytics (columnar warehouse), large file storage (S3).

## 11. Trade-offs

| Gain | Cost |
|---|---|
| Rich features, correctness, extensions | Vertical scaling ceiling; sharding is non-trivial |
| MVCC concurrency | VACUUM/bloat management |
| Transactional DDL | Some DDL still takes heavy locks |
| Process-per-connection model | Needs pooling |

## 12. How it interacts with the rest of my stack

- **Java**: PostgreSQL JDBC driver; `Instant` ↔ `timestamptz`; `BigDecimal` ↔ `numeric`.
- **Spring Boot**: HikariCP pool, Hibernate dialect, Flyway migrations at startup, `@Transactional`.
- **React**: indirectly — UI filters/pagination drive query shapes.
- **Docker**: `postgres:16` in Compose with a named volume and healthcheck; Testcontainers in tests.
- **AWS**: RDS PostgreSQL in a private subnet; security group allows 5432 only from the EC2 SG; automated backups.
- **Redis**: caches hot reads so Postgres handles writes and cold reads.
- **CI**: Testcontainers spins up real Postgres in GitHub Actions.

## 13. Hands-on exercise

**Concurrency and performance lab (PostgreSQL 16 in Docker).**

Acceptance criteria:
- [ ] Load 1M rows into `check_results` via `generate_series`.
- [ ] Capture `EXPLAIN (ANALYZE, BUFFERS)` of the dashboard query before and after a composite index; record timings.
- [ ] Reproduce a deadlock with two `psql` sessions, then prevent it with consistent lock ordering.
- [ ] Implement a worker claim query using `FOR UPDATE SKIP LOCKED` and show two sessions claiming different rows.
- [ ] Write up findings (≤ 1 page) and explain them aloud in 3 minutes.

## 14. Mastery checklist

- [ ] Use `psql` fluently.
- [ ] Explain MVCC, VACUUM, and why long transactions hurt.
- [ ] Explain isolation levels as Postgres implements them.
- [ ] Choose index types (B-tree, partial, expression, GIN, BRIN) correctly.
- [ ] Read `EXPLAIN ANALYZE` and justify an index.
- [ ] Use `ON CONFLICT`, `RETURNING`, `FOR UPDATE`, `SKIP LOCKED`.
- [ ] Diagnose locks, connection exhaustion, and deadlocks with system views.
- [ ] Run safe production migrations.

## Evidence in my projects

| Project | What it demonstrates | Fill in: file / commit |
|---|---|---|
| P1 Ledger | `schema.sql`, JDBC, atomic import in one transaction, unique-constraint dedupe, `EXPLAIN` + index | |
| P2 TicketHold | Flyway, JPA on Postgres, `@Version` optimistic locking, Postgres in Compose, Testcontainers | |
| P3 TeamBoard | Schema from ERD, search/filter queries, audit table | |
| P4 PulseWatch | RDS PostgreSQL, composite index `check_results(monitor_id, checked_at)`, retention cleanup | |

## Where to learn it in this repo

[`../04-sql-databases/README.md`](../04-sql-databases/README.md) · [`../04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md) ·
[`../04-sql-databases/06-transactions.md`](../04-sql-databases/06-transactions.md) · [`../04-sql-databases/07-postgres-vs-mysql.md`](../04-sql-databases/07-postgres-vs-mysql.md) ·
[`../04-sql-databases/08-jdbc-orm.md`](../04-sql-databases/08-jdbc-orm.md) · [`../14-cs-fundamentals/database-internals.md`](../14-cs-fundamentals/database-internals.md) ·
[`../12-aws/rds.md`](../12-aws/rds.md) · [`../09-testing/testcontainers.md`](../09-testing/testcontainers.md)
