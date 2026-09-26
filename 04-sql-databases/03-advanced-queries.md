# 03 · Advanced Queries — CTEs, Recursion, Window Functions, LATERAL, Upserts

> **Week 6** (FlowGrid M3: allocation scoring and reports). Revisited in **Week 11** (LedgerX history) and **Week 19**
> (ForgeCI DAG reachability). This is the material that separates "knows SQL" from "is strong at SQL" in interviews.
> Practice: [`sql-practice.md`](./sql-practice.md) Q34–Q40, Q51–Q66.

## 1. CTEs (`WITH`)

A Common Table Expression names a subquery so the main query reads top-to-bottom.

```sql
WITH order_totals AS (
    SELECT o.id, o.customer_id, SUM(oi.quantity * oi.unit_price) AS total
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.id
    WHERE o.status = 'PAID'
    GROUP BY o.id, o.customer_id
),
customer_totals AS (
    SELECT customer_id, COUNT(*) AS orders, SUM(total) AS lifetime
    FROM order_totals
    GROUP BY customer_id
)
SELECT c.full_name, ct.orders, ct.lifetime
FROM customer_totals ct
JOIN customers c ON c.id = ct.customer_id
ORDER BY ct.lifetime DESC;
```

- Since **PostgreSQL 12**, a CTE referenced once and without side effects is **inlined** (optimised like a subquery). Force the old "compute once" behaviour with `WITH x AS MATERIALIZED (…)`; prevent it with `NOT MATERIALIZED`.
- CTEs can contain `INSERT/UPDATE/DELETE … RETURNING` ("data-modifying CTEs") — e.g., archive-and-delete in one statement:

```sql
BEGIN;
CREATE TEMP TABLE payments_archive (LIKE payments) ON COMMIT DROP;
WITH moved AS (
    DELETE FROM payments WHERE status = 'FAILED' RETURNING *
)
INSERT INTO payments_archive SELECT * FROM moved;
SELECT count(*) FROM payments_archive;   -- 1
ROLLBACK;
```

## 2. Recursive CTEs

Shape: **anchor** `UNION ALL` **recursive step** that references the CTE itself. Runs until the step returns no new rows.

```sql
-- the whole org chart with depth and path
WITH RECURSIVE org AS (
    SELECT id, full_name, manager_id, 1 AS depth, ARRAY[id] AS path
    FROM employees
    WHERE manager_id IS NULL                         -- anchor: the CEO
    UNION ALL
    SELECT e.id, e.full_name, e.manager_id, org.depth + 1, org.path || e.id
    FROM employees e
    JOIN org ON e.manager_id = org.id                -- step: direct reports of the previous level
    WHERE org.depth < 20                             -- safety net against cycles
)
SELECT repeat('  ', depth - 1) || full_name AS org_chart, depth
FROM org
ORDER BY path;
```

```sql
-- number series / calendar without generate_series (a classic interview ask)
WITH RECURSIVE d(day) AS (
    SELECT DATE '2026-03-01'
    UNION ALL
    SELECT day + 1 FROM d WHERE day < DATE '2026-03-07'
)
SELECT day FROM d;
```

In Postgres you'd normally use `generate_series(DATE '2026-03-01', DATE '2026-03-07', INTERVAL '1 day')`.

Uses: org charts, category trees, bill of materials, graph reachability, dependency graphs (ForgeCI's `needs:` DAG in Week 19: "all jobs downstream of this one"). PG 14+ adds `SEARCH DEPTH FIRST BY …` and `CYCLE … SET …` clauses.

## 3. Window functions ⭐

A window function computes a value **across a set of rows related to the current row, without collapsing them** (unlike `GROUP BY`).

```
function(...) OVER (
    PARTITION BY ...          -- restart per group (optional)
    ORDER BY ...              -- order within the partition (needed for ranking / running values)
    ROWS|RANGE|GROUPS BETWEEN ... AND ...   -- the frame (optional)
)
```

### 3.1 Ranking

```sql
SELECT full_name, department, salary,
       ROW_NUMBER() OVER (PARTITION BY department ORDER BY salary DESC NULLS LAST) AS rn,
       RANK()       OVER (PARTITION BY department ORDER BY salary DESC NULLS LAST) AS rnk,
       DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC NULLS LAST) AS drnk,
       NTILE(2)     OVER (ORDER BY salary DESC NULLS LAST)                          AS half
FROM employees
ORDER BY department, rn;
```

| Salaries | ROW_NUMBER | RANK | DENSE_RANK |
|---|---|---|---|
| 180, 180, 150 | 1, 2, 3 | 1, 1, 3 | 1, 1, 2 |

**Top-N per group** — the most common window-function interview problem:

```sql
SELECT department, full_name, salary
FROM (
    SELECT department, full_name, salary,
           DENSE_RANK() OVER (PARTITION BY department ORDER BY salary DESC) AS r
    FROM employees
    WHERE salary IS NOT NULL
) t
WHERE r <= 2
ORDER BY department, r;
```

### 3.2 Offsets: `LAG` / `LEAD` / `FIRST_VALUE` / `LAST_VALUE` / `NTH_VALUE`

```sql
SELECT customer_id, id, created_at,
       LAG(created_at)  OVER w                 AS prev_order_at,
       created_at - LAG(created_at) OVER w     AS since_prev,
       LEAD(id)         OVER w                 AS next_order_id,
       LAG(id, 1, -1)   OVER w                 AS prev_id_or_minus1   -- offset, default
FROM orders
WINDOW w AS (PARTITION BY customer_id ORDER BY created_at)
ORDER BY customer_id, created_at;
```

### 3.3 Aggregates as windows: running totals, shares, moving averages

```sql
WITH daily AS (
    SELECT o.created_at::date AS day, SUM(oi.quantity * oi.unit_price) AS revenue
    FROM orders o JOIN order_items oi ON oi.order_id = o.id
    WHERE o.status = 'PAID'
    GROUP BY 1
)
SELECT day, revenue,
       SUM(revenue) OVER (ORDER BY day)                                   AS running_total,
       ROUND(AVG(revenue) OVER (ORDER BY day ROWS BETWEEN 2 PRECEDING AND CURRENT ROW), 2)
                                                                          AS moving_avg_3,
       ROUND(100 * revenue / SUM(revenue) OVER (), 1)                     AS pct_of_all
FROM daily
ORDER BY day;
```

### 3.4 Frames — where people get it wrong

| Frame | Meaning |
|---|---|
| *(no ORDER BY)* | whole partition |
| *(ORDER BY, no frame)* | **`RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`** — includes **peers** (rows with equal ORDER BY value) |
| `ROWS BETWEEN 6 PRECEDING AND CURRENT ROW` | the current row + 6 physical rows before it |
| `RANGE BETWEEN INTERVAL '6 days' PRECEDING AND CURRENT ROW` | all rows whose ORDER BY value is within 6 days (gap-safe) |
| `ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING` | whole partition — needed for `LAST_VALUE` |

Two classic bugs:

```sql
-- 1. LAST_VALUE returns the current row with the default frame
SELECT id, customer_id,
       LAST_VALUE(id) OVER (PARTITION BY customer_id ORDER BY created_at)            AS wrong,
       LAST_VALUE(id) OVER (PARTITION BY customer_id ORDER BY created_at
                            ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS right_
FROM orders
WHERE customer_id = 1;

-- 2. Running total with ties: default RANGE frame adds all peers at once
SELECT x, SUM(x) OVER (ORDER BY x)                                          AS range_default,
          SUM(x) OVER (ORDER BY x ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS rows_frame
FROM (VALUES (1), (2), (2), (3)) t(x);
-- range_default: 1, 5, 5, 8     rows_frame: 1, 3, 5, 8
```

### 3.5 Window functions can't be filtered in WHERE

They run after WHERE/GROUP BY/HAVING. Filter in an outer query (or CTE). Window functions **can** wrap aggregates: `SUM(SUM(x)) OVER ()` = grand total over grouped rows.

## 4. Gaps and islands ⭐

Find runs of consecutive values. Trick: `value − ROW_NUMBER()` is constant within a run.

```sql
WITH days AS (SELECT DISTINCT customer_id, created_at::date AS d FROM orders),
tagged AS (
    SELECT customer_id, d,
           d - (ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY d))::int AS grp
    FROM days
)
SELECT customer_id, MIN(d) AS from_day, MAX(d) AS to_day, COUNT(*) AS len
FROM tagged
GROUP BY customer_id, grp
ORDER BY customer_id, from_day;
```

Alternative: flag a new island when `d - LAG(d) > 1`, then running-sum the flags to get the group id. Know both.

## 5. `DISTINCT ON` and `LATERAL`

```sql
-- latest order per customer (Postgres-only, very handy)
SELECT DISTINCT ON (customer_id) customer_id, id, created_at
FROM orders
ORDER BY customer_id, created_at DESC;

-- top-2 most expensive line items per order: LATERAL = "correlated subquery in FROM"
SELECT o.id, li.event_id, li.line_total
FROM orders o
CROSS JOIN LATERAL (
    SELECT oi.event_id, oi.quantity * oi.unit_price AS line_total
    FROM order_items oi
    WHERE oi.order_id = o.id
    ORDER BY line_total DESC
    LIMIT 2
) li
ORDER BY o.id, li.line_total DESC;
```

Use `LEFT JOIN LATERAL (…) ON true` to keep outer rows with no matches. With an index on the inner filter + sort columns, LATERAL + LIMIT is often the fastest top-N-per-group.

## 6. Upsert: `INSERT … ON CONFLICT`

```sql
BEGIN;
CREATE TEMP TABLE event_stats (
    event_id integer PRIMARY KEY,
    views    bigint  NOT NULL
) ON COMMIT DROP;

INSERT INTO event_stats (event_id, views)
SELECT event_id, COUNT(*) FROM page_views GROUP BY event_id
ON CONFLICT (event_id) DO UPDATE
   SET views = EXCLUDED.views;        -- EXCLUDED = the row that failed to insert

INSERT INTO event_stats (event_id, views) VALUES (1, 0)
ON CONFLICT (event_id) DO NOTHING;   -- ignore duplicates

INSERT INTO event_stats AS s (event_id, views) VALUES (1, 5)
ON CONFLICT (event_id) DO UPDATE
   SET views = s.views + EXCLUDED.views      -- increment counter atomically
RETURNING *;
ROLLBACK;
```

`ON CONFLICT` requires a unique index/constraint matching the target. It is **atomic under concurrency** — unlike "SELECT then INSERT if missing", which races. ForgeCI M1's webhook dedupe (unique `delivery_id` + `ON CONFLICT DO NOTHING`) is this pattern. `MERGE` (PG 15+) exists for more complex sync logic.

## 7. Views and materialized views

```sql
CREATE VIEW paid_order_totals AS
SELECT o.id, o.customer_id, o.created_at, SUM(oi.quantity * oi.unit_price) AS total
FROM orders o JOIN order_items oi ON oi.order_id = o.id
WHERE o.status = 'PAID'
GROUP BY o.id, o.customer_id, o.created_at;

CREATE MATERIALIZED VIEW monthly_revenue AS
SELECT date_trunc('month', created_at) AS month, SUM(total) AS revenue
FROM paid_order_totals GROUP BY 1;

REFRESH MATERIALIZED VIEW monthly_revenue;   -- recompute; CONCURRENTLY needs a unique index

DROP MATERIALIZED VIEW monthly_revenue;
DROP VIEW paid_order_totals;
```

A **view** is a stored query (no data, always fresh). A **materialized view** stores the result (fast reads, stale until refreshed) — a database-side cache.

## 8. JSONB in one minute

```sql
SELECT '{"seat": "A12", "tags": ["vip", "aisle"]}'::jsonb ->> 'seat'          AS seat,
       '{"seat": "A12", "tags": ["vip", "aisle"]}'::jsonb -> 'tags' ->> 0      AS first_tag,
       '{"seat": "A12", "tags": ["vip", "aisle"]}'::jsonb @> '{"tags": ["vip"]}' AS is_vip;
```

`->` returns jsonb, `->>` returns text, `@>` is containment (GIN-indexable). Use jsonb for truly variable attributes (GitHub webhook payloads in ForgeCI, rule definitions in FlagForge), not for core relational data.

## 9. 🔨 Break it

1. Remove `WHERE org.depth < 20` and make employee 1 report to employee 10 (`UPDATE … ` in a transaction). Run the recursive CTE. What happens? Now add the `CYCLE id SET is_cycle USING visited` clause (PG 14+).
2. Replace `DENSE_RANK` with `ROW_NUMBER` in top-2-per-department: who disappears?
3. Compute a running total without a window function (correlated subquery) and compare `EXPLAIN ANALYZE` on `page_views` aggregated per day.
4. Put `WHERE ROW_NUMBER() OVER (...) = 1` directly in a query — read the error.
5. Run two sessions doing "SELECT; if absent INSERT" on the same key concurrently (use `BEGIN` + pauses). Then do it with `ON CONFLICT`.

## 10. 🐞 Debugging tips

- Debug a CTE chain by selecting from each CTE in turn (`… SELECT * FROM step2`).
- Debug window functions by showing the `PARTITION BY` and `ORDER BY` columns next to the result, sorted by them.
- If a recursive CTE "hangs", it's a cycle: add a depth column and a limit first, then investigate.

## 11. 🎤 Interview Q&A

<details><summary>Difference between GROUP BY and a window function?</summary>

GROUP BY collapses each group into one row. A window function computes over a set of rows but returns one value per input row, keeping row detail (e.g., each order plus the customer's total).
</details>

<details><summary>ROW_NUMBER vs RANK vs DENSE_RANK?</summary>

On ties: ROW_NUMBER assigns unique sequential numbers (arbitrary among ties), RANK gives ties the same rank and skips (1,1,3), DENSE_RANK gives ties the same rank without gaps (1,1,2). "Nth highest distinct salary" → DENSE_RANK.
</details>

<details><summary>How do you get the top 3 per category?</summary>

Rank inside a subquery/CTE with `ROW_NUMBER()/DENSE_RANK() OVER (PARTITION BY category ORDER BY metric DESC)` and filter `rank <= 3` outside. Postgres alternatives: LATERAL with LIMIT, or DISTINCT ON for top-1.
</details>

<details><summary>What's a recursive CTE? When would you use one?</summary>

A CTE with an anchor query UNION ALL a step that references the CTE, iterated until no new rows. Hierarchies (org charts, category trees, comment threads), graph traversal, generating series.
</details>

<details><summary>Why does LAST_VALUE return the current row?</summary>

Because with ORDER BY, the default frame ends at CURRENT ROW (plus peers). Specify `ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING`.
</details>

<details><summary>How would you compute a running total / moving average?</summary>

`SUM(x) OVER (ORDER BY t)` for running total; `AVG(x) OVER (ORDER BY t ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)` for a 7-row moving average (fill missing dates first, or use a RANGE interval frame).
</details>

<details><summary>How do you do an upsert in PostgreSQL and why not SELECT-then-INSERT?</summary>

`INSERT … ON CONFLICT (key) DO UPDATE SET col = EXCLUDED.col` (or DO NOTHING). It's atomic; SELECT-then-INSERT has a race window where two transactions both see "absent" and one fails (or duplicates without a constraint).
</details>

<details><summary>Are CTEs optimization fences in Postgres?</summary>

They were before PG 12. Since 12, non-recursive, side-effect-free CTEs referenced once are inlined; you can force materialization with `AS MATERIALIZED`.
</details>

## ✅ Mastery checklist

- [ ] Wrote a recursive CTE for the org chart from memory
- [ ] Can explain default frame behaviour and fix the LAST_VALUE bug
- [ ] Top-N-per-group three ways (ROW_NUMBER, DISTINCT ON, LATERAL)
- [ ] Gaps & islands with the `value − row_number` trick
- [ ] Used `ON CONFLICT` for idempotent inserts (FlowGrid idempotency keys, ForgeCI webhook dedupe)
- [ ] Solved Q51–Q66 in [`sql-practice.md`](./sql-practice.md)
