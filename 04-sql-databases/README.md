# 🗄️ 04 · SQL, PostgreSQL, MySQL, Redis

> **Goal:** SQL becomes a genuine strength. You can write correct queries on unfamiliar schemas under time pressure,
> read a query plan, design a normalized schema with real constraints, reason about transactions and locks, and defend
> PostgreSQL, MySQL and Redis on your résumé with evidence from **FlowGrid, LedgerX, ForgeCI and FlagForge**.
> **When:** just-in-time, per [ROADMAP §6](../ROADMAP.md#6-just-in-time-learning-map). Basics in Week 3; joins in Week 4; transactions in Week 5;
> schema, indexes, `EXPLAIN` and window functions in Week 6; internals and constraints in Week 9 (LedgerX); Redis in Weeks 6, 15–16 and 21–23.

## Why SQL before (and alongside) Spring Data JPA

JPA generates SQL for you, which is exactly why you need to know SQL first. Developers who learn the ORM first ship
N+1 queries, missing indexes and lost updates without noticing. Here, SQL fundamentals come in Week 3. From Week 4 on, every
JPA mapping in FlowGrid is read against the SQL it generates.

## Files in order

| # | File | First needed | You'll be able to… |
|---|---|---|---|
| 1 | [`01-sql-basics.md`](./01-sql-basics.md) | W3 | SELECT/WHERE/ORDER BY/LIMIT, NULL logic, types, INSERT/UPDATE/DELETE with RETURNING |
| 2 | [`02-joins-aggregation.md`](./02-joins-aggregation.md) | W4 | every join type incl. self/semi/anti, GROUP BY/HAVING, subqueries, set ops, fan-out bugs |
| 3 | [`06-transactions.md`](./06-transactions.md) | W5 | ACID, MVCC, isolation levels & anomalies, `FOR UPDATE` vs version columns, SKIP LOCKED, deadlocks |
| 4 | [`04-schema-design.md`](./04-schema-design.md) | W4 (§1–4), W6 (all) | keys, 1:1 / 1:N / M:N, constraints, ON DELETE, 1NF–3NF, deliberate denormalization |
| 5 | [`05-indexes-performance.md`](./05-indexes-performance.md) | W6 | B-tree/composite/covering/partial/GIN indexes, EXPLAIN ANALYZE, join algorithms, optimization |
| 6 | [`03-advanced-queries.md`](./03-advanced-queries.md) | W6 | CTEs, recursive CTEs, window functions & frames, gaps/islands, LATERAL, upserts |
| 7 | [`08-jdbc-orm.md`](./08-jdbc-orm.md) | W3–4 | JDBC done right, SQL injection, JDBC transactions, HikariCP, ORM vs raw SQL |
| 8 | [`07-postgres-vs-mysql.md`](./07-postgres-vs-mysql.md) | W9 (≈2 h) | clustered index, RR + gap locks, AUTO_INCREMENT vs identity, upsert syntax, when to pick which |
| — | [`redis.md`](./redis.md) | W6, W15–16, W21–23 | data types, TTL, eviction, RDB/AOF, cache-aside, reliable queues, locks & caveats, rate limiting |
| — | [`practice-schema.sql`](./practice-schema.sql) | W3+ | the TixHub practice database (8 tables, 200k-row `page_views`) |
| — | [`sql-practice.md`](./sql-practice.md) | W3–6, W24 | 66 graded problems + 10 plan readings + 5 slow-query fixes |

The file numbers follow topic order; the table above is the order you *need* them in.
TixHub (event ticketing) is a **practice database only**, a neutral domain so drills don't turn into project code.

## Setup (Week 3, once)

```bash
# PostgreSQL 16 in Docker (Compose arrives in Week 4 with FlowGrid)
docker run --name pg -e POSTGRES_PASSWORD=pg -p 5432:5432 -v pgdata:/var/lib/postgresql/data -d postgres:16
docker cp practice-schema.sql pg:/tmp/
docker exec -it pg psql -U postgres -c 'CREATE DATABASE tixhub'
docker exec -it pg psql -U postgres -d tixhub -f /tmp/practice-schema.sql
docker exec -it pg psql -U postgres -d tixhub          # start practising
```

Or install PostgreSQL 16 natively and run `createdb tixhub && psql -d tixhub -f practice-schema.sql`.
A GUI (DBeaver, pgAdmin, the IntelliJ Database tool) is fine for browsing, but **write queries in `psql` at least half the time**. Interviews and servers won't have your GUI.

A `~/.psqlrc` worth having:

```
\set QUIET 1
\x auto
\timing on
\pset null '∅'
\set VERBOSITY verbose
\unset QUIET
```

`\pset null '∅'` makes NULLs visible, and you'll immediately see why many "wrong" answers are wrong.

## Week-by-week (learning block ≈ 6–8 h/week, shared with other topics)

| Week | Read | Hands-on | Practice | Where it lands in the project |
|---|---|---|---|---|
| 3 | 01, 08 §1–4 | Load TixHub; explore with `\d`; JDBC query + SQL-injection demo | Q1–Q20 | Foundation Spring Boot API (throwaway) |
| 4 | 02, 04 §1–4, 08 §7–8 | Reproduce the fan-out bug; design FlowGrid's first tables on paper before writing Flyway `V1` | Q21–Q33, LeetCode SQL 50 (first 15) | **FlowGrid M1**: schema, constraints, joins behind list endpoints |
| 5 | 06 | Four two-terminal isolation labs; the `FOR UPDATE` vs version-column comparison | Q34–Q42 | **FlowGrid M2**: reservations under concurrency, idempotency-key table |
| 6 | 03, 04 §5–7, 05, redis §1–7 | EXPLAIN every FlowGrid list/search query; cache-aside + invalidation in `redis-cli` | Q43–Q66, P1–P10, S1–S5, SQL 50 (rest) | **FlowGrid M3**: indexes, allocation queries, Redis catalog cache |
| 8 | 05 §9 (again) | EXPLAIN on the queries k6 shows as slowest | S1–S5 re-done | **FlowGrid M5**: measured baseline |
| 9 | 07, 06 §3 (MVCC) again, 04 §4 (constraints) again | MySQL diff lab; append-only table with a trigger | redo ⭐ problems | **LedgerX M1**: immutable ledger entries, CHECK constraints |
| 10–12 | 06 (Labs 1, 3, 4 again), 03 §6 | Isolation experiments with money; ordered locking; outbox with SKIP LOCKED | Q66 (reconciliation) timed | **LedgerX M2–M4** |
| 14–17 | redis §8–10 | Reliable queue with `BLMOVE`; pub/sub; counters as semaphores | — | **ForgeCI M1–M4**: webhook dedupe (unique constraint), job queue, live logs |
| 21–23 | redis §7 again | Snapshot cache + pub/sub invalidation; stampede protection | — | **FlagForge M2, M4** |
| 24 | — | timed: 3 hard problems in 60 min | Q51–Q66 timed | Polish: DATABASE.md, ERDs |

## How to study SQL (method)

1. **Predict, then run.** Before running any query, write down the row count you expect. The surprises are where you learn.
2. **Build queries incrementally.** Start with FROM/JOIN and check the row count, then add WHERE, then GROUP BY, then windows, then ORDER BY/LIMIT.
3. **Explain out loud** why each join is INNER or LEFT, and what happens to NULLs.
4. **Do the Break-it sections.** They're the Break → Debug steps of the [learning philosophy](../ROADMAP.md#1-learning-philosophy).
5. Log each practice problem in [`../trackers/technology-tracker.md`](../trackers/technology-tracker.md), and re-solve ⭐ problems on Day 3/7/14.

## Where this shows up in projects

| Project milestone | SQL / Redis skill you'll prove (you implement it; these files teach the concept) |
|---|---|
| FlowGrid M1 (W4) | normalized schema via Flyway, FK/CHECK/UNIQUE constraints, joins for list endpoints, pagination |
| FlowGrid M2 (W5) | transactions, `SELECT … FOR UPDATE` vs `@Version`, idempotency-key table with a unique constraint, N-threads-1-unit test |
| FlowGrid M3 (W6) | composite indexes from `EXPLAIN`, deterministic allocation queries, Redis cache-aside, degrade to DB when Redis is down |
| LedgerX M1 (W9) | append-only `ledger_entry` enforced by the DB, "every journal transaction sums to zero", balances from `SUM` |
| LedgerX M2 (W10) | ordered pessimistic locking, isolation experiments, deadlock avoidance |
| LedgerX M3–M4 (W11–12) | cursor pagination, transactional outbox + `SKIP LOCKED`, reconciliation queries |
| ForgeCI M1–M4 (W14–17) | webhook dedupe via `ON CONFLICT DO NOTHING`, Redis reliable queue, pub/sub, Redis counters as limits |
| FlagForge M1–M4 (W20–23) | immutable config versions, Redis snapshot cache, pub/sub invalidation, stampede protection |

## Checkpoint bars

- **[CP-4](../checkpoints/checkpoint-04.md):** basic SQL. SELECT/WHERE/ORDER BY, a two-table join, an aggregate with HAVING, an INSERT/UPDATE with RETURNING, all without notes.
- **[CP-8](../checkpoints/checkpoint-08.md):** a multi-join aggregate, one top-N-per-group window query, one anti-join, and explaining one `EXPLAIN ANALYZE` plan from FlowGrid.
- **[CP-12](../checkpoints/checkpoint-12.md):** narrate every isolation anomaly with a two-terminal demo, and prove LedgerX's transfer can't go negative.

## Resources

- **Official:** PostgreSQL 16 docs, <https://www.postgresql.org/docs/16/>: Tutorial; Ch. 7 Queries; Ch. 11 Indexes; Ch. 13 Concurrency Control; Ch. 14 Performance Tips. MySQL 8 Reference Manual (dev.mysql.com/doc): the *InnoDB Locking and Transaction Model* chapter. Redis docs: redis.io/docs.
- **Practice:** LeetCode SQL 50 study plan (<https://leetcode.com/studyplan/top-sql-50/>), plus [`sql-practice.md`](./sql-practice.md).
- **Books (optional):** *SQL Performance Explained* / use-the-index-luke.com (Markus Winand), the best short text on indexes. *Designing Data-Intensive Applications* (Kleppmann), Ch. 7 (transactions) during LedgerX, and Ch. 3 (storage) in Week 9.
- **Tools:** `psql`, DBeaver, explain.dalibo.com (plan visualiser; practice data only).

## ✅ Module mastery checklist

- [ ] Solved all 66 problems in [`sql-practice.md`](./sql-practice.md); ⭐ problems re-solved timed
- [ ] Finished LeetCode SQL 50
- [ ] Can read any plan in P1–P10 aloud; did S1–S5 with measured before/after
- [ ] Ran all four two-terminal transaction labs, and again with LedgerX's tables
- [ ] Every FlowGrid list/search query has been `EXPLAIN`ed; the indexes are justified in `DATABASE.md`
- [ ] Can explain Postgres vs MySQL differences in two minutes
- [ ] Redis used for real: FlowGrid catalog cache, ForgeCI queue, FlagForge snapshot cache
- [ ] Drills done: [`../17-resume-tech-defense/sql.md`](../17-resume-tech-defense/sql.md), [`postgresql.md`](../17-resume-tech-defense/postgresql.md), [`mysql.md`](../17-resume-tech-defense/mysql.md), [`redis.md`](../17-resume-tech-defense/redis.md)
