# SQL — Résumé Defense

**Target level:** L4 · **Learned:** Week 3 (basics, `psql`), Week 4 (joins, aggregation), Week 5 (transactions, isolation), Week 6 (schema design, CTEs, window functions, indexes, `EXPLAIN`), Week 9 (constraints, triggers, internals for LedgerX) · Dialect used: PostgreSQL 16 (MySQL 8 differences in [mysql.md](./mysql.md))
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md)

---

## 1. Beginner questions

<details><summary><b>Q1. Logical order of evaluation of a SELECT?</b></summary>

`FROM/JOIN → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT/OFFSET`. That's why
you can't use a `SELECT` alias in `WHERE` (but can in `ORDER BY`).
</details>

<details><summary><b>Q2. <code>WHERE</code> vs <code>HAVING</code>?</b></summary>

`WHERE` filters rows before grouping; `HAVING` filters groups after aggregation
(`HAVING SUM(amount) > 500`).
</details>

<details><summary><b>Q3. Primary key vs foreign key vs unique constraint?</b></summary>

PK: unique, not null, identifies a row (one per table). FK: references a key in another table, enforces
referential integrity. UNIQUE: no duplicates among non-null values (multiple allowed per table).
</details>

<details><summary><b>Q4. Types of joins?</b></summary>

INNER (matches only), LEFT (all left rows, NULLs where no match), RIGHT, FULL OUTER (all from both),
CROSS (Cartesian product), self-join (table joined to itself, e.g. employee → manager).
</details>

<details><summary><b>Q5. How does NULL behave?</b></summary>

Unknown: `NULL = NULL` is not true; use `IS NULL`. Aggregates ignore NULLs except `COUNT(*)`.
`NOT IN (subquery containing NULL)` returns no rows — prefer `NOT EXISTS`. Use `COALESCE` for defaults.
</details>

<details><summary><b>Q6. <code>DELETE</code> vs <code>TRUNCATE</code> vs <code>DROP</code>?</b></summary>

`DELETE` removes rows (filterable, row-by-row, fires triggers). `TRUNCATE` empties the table fast
(in Postgres it's transactional; in MySQL it's DDL with implicit commit). `DROP` removes the table itself.
</details>

## 2. Intermediate questions

<details><summary><b>Q7. What are window functions? Example.</b></summary>

Compute over a set of related rows without collapsing them:
```sql
SELECT account_id, created_at, amount,
       SUM(amount) OVER (PARTITION BY account_id ORDER BY created_at, id) AS running_balance,
       RANK() OVER (PARTITION BY account_id ORDER BY abs(amount) DESC) AS size_rank
FROM ledger_entry;
```
`ROW_NUMBER`, `RANK`, `DENSE_RANK`, `LAG`/`LEAD`, running sums.
</details>

<details><summary><b>Q8. CTE vs subquery?</b></summary>

`WITH x AS (...)` names a subquery for readability and reuse within the statement; recursive CTEs
walk hierarchies. In PostgreSQL 12+ non-recursive CTEs are inlined by default (optimisable) unless `MATERIALIZED`.
</details>

<details><summary><b>Q9. Explain normalization (1NF–3NF).</b></summary>

1NF: atomic values, no repeating groups. 2NF: no partial dependency on part of a composite key.
3NF: no transitive dependency (non-key → non-key). Goal: avoid update/insert/delete anomalies.
Denormalize deliberately for read performance with a clear reason.
</details>

<details><summary><b>Q10. How does a B-tree index help, and what's a composite index's column order rule?</b></summary>

Sorted tree → O(log n) lookup, range scans, and sorted output. Composite `(account_id, created_at)`
serves `WHERE account_id = ?` and `WHERE account_id = ? AND created_at > ? ORDER BY created_at`,
but not `WHERE created_at > ?` alone (leftmost-prefix rule). Put equality columns first, then range/sort.
</details>

<details><summary><b>Q11. What are ACID and isolation anomalies?</b></summary>

Atomicity, Consistency, Isolation, Durability. Anomalies: dirty read, non-repeatable read, phantom
read, lost update, write skew. Levels: READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ,
SERIALIZABLE. Defaults: Postgres READ COMMITTED; MySQL InnoDB REPEATABLE READ.
</details>

<details><summary><b>Q12. <code>UNION</code> vs <code>UNION ALL</code>; <code>EXISTS</code> vs <code>IN</code>?</b></summary>

`UNION` removes duplicates (sort/hash cost); `UNION ALL` doesn't. `EXISTS` stops at first match and is
NULL-safe for negation; modern optimizers often plan `IN`/`EXISTS` similarly as semi-joins.
</details>

<details><summary><b>Q13. What is SQL injection and how do you prevent it?</b></summary>

User input concatenated into SQL changes the query. Prevent with parameterized queries
(`PreparedStatement` with `?`, JPA parameters), never string concatenation; whitelist dynamic column names for sorting.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "Write a query: top 3 SKUs by shipped quantity per warehouse per month."</b></summary>

```sql
WITH t AS (
  SELECT date_trunc('month', s.shipped_at) AS month, a.warehouse_id, a.sku_id, SUM(a.quantity) AS qty
  FROM shipments s JOIN allocations a ON a.shipment_id = s.id
  WHERE s.status = 'SHIPPED'
  GROUP BY 1, 2, 3)
SELECT * FROM (
  SELECT t.*, DENSE_RANK() OVER (PARTITION BY month, warehouse_id ORDER BY qty DESC) AS r FROM t) x
WHERE r <= 3 ORDER BY month, warehouse_id, r;
```
Mention tie handling (`DENSE_RANK` vs `ROW_NUMBER`). Evidence: FlowGrid's fulfillment reports (exported to S3 in M5).
</details>

<details><summary><b>R2. "How much SQL did you write in your previous roles?"</b></summary>

- Honest: e.g. "mostly simple queries through an ORM; occasionally debugging reports". Don't inflate.
- Now: "I wrote every migration by hand in Flyway for FlowGrid and LedgerX — constraints, triggers, indexes —
  plus report queries with CTEs and window functions (running balances, top SKUs per warehouse), a reconciliation
  query proving every journal transaction sums to zero, and I tuned the slowest queries with `EXPLAIN ANALYZE`
  and composite indexes."
</details>

<details><summary><b>R3. "This query is slow. What do you do?"</b></summary>

- Measure: `EXPLAIN (ANALYZE, BUFFERS)`; look for seq scans on large tables, row-estimate mismatches, sorts spilling to disk, nested loops over many rows.
- Fix in order: query shape (avoid functions on indexed columns, `SELECT *`), right index (composite/order), statistics (`ANALYZE`), pagination, caching.
- Verify with a before/after plan and timing.
</details>

<details><summary><b>R4. "Find duplicates / find customers with no orders / second-highest salary."</b></summary>

- Duplicates: `GROUP BY email HAVING COUNT(*) > 1`.
- No orders: `LEFT JOIN orders o ON ... WHERE o.id IS NULL` or `NOT EXISTS`.
- Second highest: `SELECT DISTINCT salary FROM emp ORDER BY salary DESC OFFSET 1 LIMIT 1` or `DENSE_RANK() = 2`.
</details>

<details><summary><b>R5. "Design the schema for a multi-warehouse inventory system with reservations."</b></summary>

- `warehouses`, `products`, `skus(product_id)`, `inventory_levels(warehouse_id, sku_id, on_hand, available, reserved, …, UNIQUE(warehouse_id, sku_id), CHECK(available >= 0))`,
  `orders(idempotency_key UNIQUE)`, `order_lines`, `reservations(order_line_id, warehouse_id, sku_id, quantity, status)`, `stock_adjustments`, `audit_events`.
- Constraints enforce invariants (no negative available, one level row per warehouse/SKU) even if app code has a bug. Indexes on FK columns used in joins.
- Follow-up they'll ask: the LedgerX version — `account`, `ledger_entry` (append-only, trigger), `journal_txn`, with CHECKs and a sum-to-zero invariant checked by a reconciliation job.
</details>

## 4. Practical tasks (live)

- [ ] Write joins across 3 tables with a LEFT JOIN and explain row counts.
- [ ] Monthly totals with `GROUP BY` + `HAVING`.
- [ ] Running total and rank with window functions.
- [ ] Recursive CTE over a category hierarchy.
- [ ] Create a table with PK, FK (`ON DELETE` choice justified), UNIQUE, CHECK constraints.
- [ ] Read an `EXPLAIN ANALYZE` and propose an index; show the plan change.
- [ ] Upsert (`INSERT ... ON CONFLICT DO NOTHING/UPDATE`) for deduped imports.

## 5. Debugging questions

<details><summary><b>D1. A report shows totals that are too high after adding a join.</b></summary>

Join fan-out: joining a one-to-many table multiplies rows before `SUM`. Aggregate in a subquery/CTE
first, then join; or use `COUNT(DISTINCT ...)` where appropriate.
</details>

<details><summary><b>D2. <code>NOT IN</code> query returns zero rows unexpectedly.</b></summary>

The subquery contains a NULL. Use `NOT EXISTS` or filter NULLs.
</details>

<details><summary><b>D3. Index exists but isn't used.</b></summary>

Function/cast on the column (`WHERE lower(email) = ...` needs an expression index), leading column not
in predicate, low selectivity (planner prefers seq scan), stale statistics, type mismatch, or `LIKE '%x'`.
</details>

<details><summary><b>D4. Two users update the same balance and one update is lost.</b></summary>

Lost update from read-modify-write in app code. Fix: atomic `UPDATE accounts SET balance = balance - ? WHERE id = ?`,
`SELECT ... FOR UPDATE`, optimistic version column, or a stricter isolation level with retry. (LedgerX never updates a balance in place — it appends entries; the materialized balance is derived and checked.)
</details>

<details><summary><b>D5. A migration adding a NOT NULL column fails on production data.</b></summary>

Existing rows would violate it. Add nullable → backfill → add `NOT NULL` (or add with a `DEFAULT`).
</details>

## 6. Architecture questions

- When would you denormalize (e.g. store `issue_count` on project) and how do you keep it consistent?
- Soft delete vs hard delete; how does it affect unique constraints and queries?
- Enforcing invariants in DB constraints vs application code — why both for FlowGrid (`CHECK(available >= 0)`) and LedgerX (trigger making `ledger_entry` append-only)?
- Append-only, ever-growing tables (LedgerX `ledger_entry`, ForgeCI `log_chunks`): indexing, retention/archival, partitioning awareness.
- Surrogate (identity/UUID) vs natural keys.

## 7. Common mistakes

- `SELECT *` in application queries.
- Missing indexes on foreign keys used in joins.
- `= NULL` instead of `IS NULL`.
- Aggregating after a fan-out join.
- String-concatenated SQL.
- Offset pagination on huge tables; `ORDER BY` without a unique tiebreaker.
- Using `FLOAT` for money (use `NUMERIC(12,2)`).

## 8. Terminology I must know

| Term | One-line meaning |
|---|---|
| Relation | A table (set of tuples) |
| Primary / foreign key | Row identifier / reference to another table's key |
| Cardinality | Number of distinct values, or 1:1/1:N/N:M relationship type |
| Normalization | Structuring to remove redundancy/anomalies |
| Index | Auxiliary structure speeding lookups |
| Selectivity | Fraction of rows a predicate matches |
| CTE | Named subquery via `WITH` |
| Window function | Calculation across related rows without grouping them away |
| Transaction | Unit of work that commits or rolls back entirely |
| Isolation level | How much concurrent transactions see of each other |
| Execution plan | How the DB will run a query (`EXPLAIN`) |
| Upsert | Insert or update on conflict |
| Deadlock | Transactions waiting on each other's locks |

## 9. When to use it

Relational data with integrity requirements, ad-hoc querying and reporting, transactions across multiple rows/tables.

## 10. When NOT to use it

Pure key-value/caching (Redis), full-text search at scale (search engine), massive append-only
analytics (columnar warehouse), unstructured blobs (S3).

## 11. Trade-offs

| Choice | Gain | Cost |
|---|---|---|
| Normalized schema | Integrity, no anomalies | More joins |
| Denormalized | Faster reads | Update complexity |
| More indexes | Faster reads | Slower writes, storage |
| Stricter isolation | Fewer anomalies | Contention, retries |
| Raw SQL vs ORM | Control, clarity | More code, mapping by hand |

## 12. How it interacts with the rest of my stack

- **Java**: JPA/JPQL and native queries via Spring Data generate/run SQL; `JdbcTemplate` for reconciliation and report queries; `@Transactional` wraps statements.
- **Python**: LedgerX's *independent* reconciliation verifier runs the same invariant queries through `psycopg` — a second implementation that must agree with the Java one.
- **Spring Boot**: Flyway migrations (`V1__init.sql`) define schema; Spring Data derives queries.
- **PostgreSQL/MySQL**: dialect differences (upsert, types, `RETURNING`).
- **React**: filters/pagination in the UI become `WHERE`/`LIMIT` in SQL — keep them index-friendly.
- **Docker/AWS**: Postgres in Compose locally, RDS in prod; same migrations.
- **CI**: Testcontainers runs migrations and repository tests against real Postgres.

## 13. Hands-on exercise

**LedgerX + FlowGrid analytics pack.**

Acceptance criteria:
- [ ] 6 queries: running balance per account (window), journal transactions that don't sum to zero (`HAVING SUM(amount) <> 0`), month-over-month deposit volume (`LAG`), accounts whose materialized balance ≠ sum of entries (join + `HAVING`), SKUs with reservations but no allocation (`LEFT JOIN … IS NULL`), top-3 SKUs per warehouse per month (`DENSE_RANK`).
- [ ] Each query has a comment with its expected result on the seed data.
- [ ] One query tuned: before/after `EXPLAIN ANALYZE` saved in the README.
- [ ] Explain each query aloud in ≤ 60 s.

## 14. Mastery checklist

- [ ] Write joins, aggregates, subqueries, CTEs, window functions fluently.
- [ ] Explain NULL semantics and the `NOT IN` trap.
- [ ] Design a normalized schema with constraints for a new domain.
- [ ] Explain B-tree and composite index rules.
- [ ] Read `EXPLAIN ANALYZE` and fix a slow query.
- [ ] Explain ACID, isolation levels and anomalies with examples.
- [ ] Prevent SQL injection and lost updates.

## Evidence in my projects

| Project | What it demonstrates | Fill in: file / commit |
|---|---|---|
| FlowGrid | Flyway migrations for the inventory domain, `UNIQUE(warehouse_id, sku_id)`, `CHECK(available >= 0)`, indexes on FKs and the low-stock query, offset pagination with a unique tiebreaker, `EXPLAIN` before/after | |
| LedgerX | `ledger_entry` append-only via trigger + revoked privileges, `CHECK` constraints, sum-to-zero reconciliation query, running-balance window query, keyset (cursor) pagination for history, `SELECT … FOR UPDATE` in id order | |
| ForgeCI | Unique constraint on delivery id, `log_chunks(job_id, seq)` index + replay query, lease-expiry sweep query, batched retention deletes | |
| FlagForge | Immutable versions (insert-only), rules ordered by priority, audit history query per flag | |

## Where to learn it in this repo

[`../04-sql-databases/README.md`](../04-sql-databases/README.md) · [`../04-sql-databases/01-sql-basics.md`](../04-sql-databases/01-sql-basics.md) ·
[`../04-sql-databases/02-joins-aggregation.md`](../04-sql-databases/02-joins-aggregation.md) · [`../04-sql-databases/03-advanced-queries.md`](../04-sql-databases/03-advanced-queries.md) ·
[`../04-sql-databases/04-schema-design.md`](../04-sql-databases/04-schema-design.md) · [`../04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md) ·
[`../04-sql-databases/06-transactions.md`](../04-sql-databases/06-transactions.md) · [`../04-sql-databases/sql-practice.md`](../04-sql-databases/sql-practice.md) ·
[`../04-sql-databases/practice-schema.sql`](../04-sql-databases/practice-schema.sql)
