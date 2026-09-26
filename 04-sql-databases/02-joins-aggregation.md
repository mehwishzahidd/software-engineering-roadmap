# 02 · Joins, Aggregation, Subqueries

> **Week 4**, alongside **FlowGrid M1**: list endpoints join warehouses, SKUs and inventory levels. Think in sets, not loops. Practice: [`sql-practice.md`](./sql-practice.md) Q18, Q21–Q33, Q42–Q46.

## 1. Mental model: a join is "for every pair of rows, keep those where ON is true"

Conceptually a join takes the **Cartesian product** of the two inputs and keeps rows where the `ON` predicate is TRUE. The engine never literally does that (it uses nested loop / hash / merge joins — see [`05-indexes-performance.md`](./05-indexes-performance.md)), but the mental model predicts results correctly, including duplicates.

**Cardinality rule:** joining a row to *N* matching rows produces *N* rows. That's how `SUM` gets silently inflated ("fan-out").

## 2. Join types

```
customers (c)             orders (o)
id | name                 id | customer_id
 1 | Ava                   1 | 1
 9 | Isla   (no orders)    2 | 1
```

| Join | Keeps | Typical use |
|---|---|---|
| `INNER JOIN` | only matched pairs | "orders with their customer" |
| `LEFT [OUTER] JOIN` | all left rows; right columns NULL if no match | "all customers, with orders if any" |
| `RIGHT JOIN` | all right rows | rarely used — rewrite as LEFT with tables swapped |
| `FULL [OUTER] JOIN` | everything from both sides | reconciliation of two sources |
| `CROSS JOIN` | every combination | generating grids (dates × events) |
| Self join | same table twice under two aliases | hierarchies, comparing rows |
| Semi-join (`EXISTS`) | left rows that have ≥1 match, no duplication | "customers who ordered" |
| Anti-join (`NOT EXISTS`) | left rows with no match | "customers who never ordered" |

```sql
-- INNER: 18 rows (every order has a customer)
SELECT o.id, c.full_name
FROM orders o
JOIN customers c ON c.id = o.customer_id;

-- LEFT: 21 rows (18 orders + 3 customers without orders, o.* NULL)
SELECT c.id, c.full_name, o.id AS order_id
FROM customers c
LEFT JOIN orders o ON o.customer_id = c.id
ORDER BY c.id, o.id;

-- FULL: compare "events that were viewed" vs "events that were sold"
SELECT COALESCE(v.event_id, s.event_id) AS event_id, v.views, s.sold
FROM (SELECT event_id, COUNT(*) AS views FROM page_views GROUP BY event_id) v
FULL JOIN (SELECT event_id, SUM(quantity) AS sold FROM order_items GROUP BY event_id) s
       ON s.event_id = v.event_id
ORDER BY event_id;

-- CROSS: every venue × every category (a grid to LEFT JOIN facts onto)
SELECT v.name, c.category
FROM venues v
CROSS JOIN (VALUES ('CONCERT'), ('COMEDY'), ('SPORTS'), ('CONFERENCE')) AS c(category);

-- SELF: employee with manager
SELECT e.full_name, m.full_name AS manager
FROM employees e
LEFT JOIN employees m ON m.id = e.manager_id;
```

### The `ON` vs `WHERE` trap for outer joins ⭐

```sql
-- all customers, with their PAID orders if any  (filter in ON)
SELECT c.id, o.id AS paid_order
FROM customers c
LEFT JOIN orders o ON o.customer_id = c.id AND o.status = 'PAID';

-- WRONG for that goal: WHERE runs after the join and removes the NULL-extended rows,
-- turning the LEFT JOIN into an INNER JOIN.
SELECT c.id, o.id AS paid_order
FROM customers c
LEFT JOIN orders o ON o.customer_id = c.id
WHERE o.status = 'PAID';
```

Rule: conditions on the **optional (right) side** of a LEFT JOIN go in `ON`; conditions on the **preserved (left) side** go in `WHERE`.

### Multi-table joins

```sql
SELECT c.full_name, o.id AS order_id, e.title, oi.quantity, v.name AS venue
FROM customers c
JOIN orders o       ON o.customer_id = c.id
JOIN order_items oi ON oi.order_id   = o.id
JOIN events e       ON e.id          = oi.event_id
JOIN venues v       ON v.id          = e.venue_id
WHERE o.status = 'PAID'
ORDER BY o.id;
```

Follow the foreign keys. When lost, draw the path: `customers → orders → order_items → events → venues`.

## 3. Aggregation

```sql
SELECT e.category,
       COUNT(*)                          AS line_items,
       COUNT(DISTINCT oi.order_id)       AS orders,
       SUM(oi.quantity)                  AS tickets,
       SUM(oi.quantity * oi.unit_price)  AS revenue,
       ROUND(AVG(oi.unit_price), 2)      AS avg_price,
       MIN(oi.unit_price), MAX(oi.unit_price),
       string_agg(DISTINCT e.title, ', ' ORDER BY e.title) AS titles
FROM order_items oi
JOIN events e ON e.id = oi.event_id
GROUP BY e.category
HAVING SUM(oi.quantity) >= 5
ORDER BY revenue DESC;
```

Rules:

- Every non-aggregated column in `SELECT` must be in `GROUP BY` (or functionally dependent on a grouped primary key — Postgres allows `GROUP BY c.id` then `SELECT c.full_name`).
- `COUNT(*)` counts rows; `COUNT(col)` counts non-NULL values; `COUNT(DISTINCT col)` counts distinct non-NULL values.
- Conditional aggregation: `COUNT(*) FILTER (WHERE status = 'PAID')` or `SUM(CASE WHEN … THEN 1 ELSE 0 END)`.
- `GROUPING SETS / ROLLUP / CUBE` produce subtotals in one query — know they exist:

```sql
SELECT e.category, e.venue_id, SUM(oi.quantity) AS tickets
FROM order_items oi JOIN events e ON e.id = oi.event_id
GROUP BY ROLLUP (e.category, e.venue_id)
ORDER BY e.category NULLS LAST, e.venue_id NULLS LAST;   -- NULL rows = subtotals / grand total
```

### Fan-out: the silent aggregation bug ⭐

```sql
-- WRONG: joining two child tables of orders multiplies rows.
-- Order 7 has 2 items and 2 payments -> 4 joined rows -> both sums doubled.
SELECT o.id,
       SUM(oi.quantity * oi.unit_price) AS items_total,
       SUM(p.amount)                    AS paid_total
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN payments p     ON p.order_id  = o.id
WHERE o.id = 7
GROUP BY o.id;

-- RIGHT: aggregate each child separately, then join the aggregates.
SELECT o.id, i.items_total, p.paid_total
FROM orders o
JOIN (SELECT order_id, SUM(quantity * unit_price) AS items_total
      FROM order_items GROUP BY order_id) i ON i.order_id = o.id
LEFT JOIN (SELECT order_id, SUM(amount) AS paid_total
           FROM payments WHERE status = 'SUCCEEDED' GROUP BY order_id) p ON p.order_id = o.id
WHERE o.id = 7;
```

Debug habit: before aggregating, run the join **without** `GROUP BY` and look at how many rows each key has.

## 4. Subqueries

| Kind | Returns | Example |
|---|---|---|
| Scalar | one value | `WHERE salary > (SELECT AVG(salary) FROM employees)` |
| Row/list | a column | `WHERE id IN (SELECT customer_id FROM orders)` |
| Table (derived) | a table in FROM | `FROM (SELECT …) AS t` |
| Correlated | re-evaluated per outer row | `WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id)` |

```sql
-- scalar: employees above the company average
SELECT full_name, salary
FROM employees
WHERE salary > (SELECT AVG(salary) FROM employees);

-- correlated scalar: above their own department's average
SELECT e.full_name, e.department, e.salary
FROM employees e
WHERE e.salary > (SELECT AVG(e2.salary) FROM employees e2 WHERE e2.department = e.department);

-- semi-join: customers with at least one PAID order (no duplicates, unlike a JOIN)
SELECT c.id, c.full_name
FROM customers c
WHERE EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id AND o.status = 'PAID');

-- same with IN
SELECT id, full_name FROM customers
WHERE id IN (SELECT customer_id FROM orders WHERE status = 'PAID');
```

### EXISTS vs IN vs JOIN

- `IN (subquery)` and `EXISTS` are both semi-joins; Postgres usually plans them identically. `EXISTS` reads better for correlated conditions and stops at the first match.
- A plain `JOIN` for "has at least one" **duplicates** the left row per match — you'd need `DISTINCT`.
- **`NOT IN` is dangerous**: if the subquery returns any NULL, `NOT IN` returns no rows. `NOT EXISTS` doesn't have this problem and is planned as an efficient Hash Anti Join.

```sql
-- anti-join, three ways (all return customers 9, 11, 12)
SELECT c.id FROM customers c
WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);

SELECT c.id FROM customers c
LEFT JOIN orders o ON o.customer_id = c.id
WHERE o.id IS NULL;

SELECT id FROM customers
EXCEPT
SELECT customer_id FROM orders;
```

## 5. Set operations

```sql
SELECT customer_id FROM orders WHERE created_at < '2026-02-01'
UNION                                    -- de-duplicates (sorts/hashes: slower)
SELECT customer_id FROM orders WHERE created_at >= '2026-05-01';

SELECT customer_id FROM orders WHERE created_at < '2026-02-01'
UNION ALL                                -- keeps duplicates, no extra work: prefer when you can
SELECT customer_id FROM orders WHERE created_at >= '2026-05-01';

-- ordered in January AND in February
SELECT customer_id FROM orders WHERE created_at >= '2026-01-01' AND created_at < '2026-02-01'
INTERSECT
SELECT customer_id FROM orders WHERE created_at >= '2026-02-01' AND created_at < '2026-03-01';

-- ordered in January but NOT in February
SELECT customer_id FROM orders WHERE created_at >= '2026-01-01' AND created_at < '2026-02-01'
EXCEPT
SELECT customer_id FROM orders WHERE created_at >= '2026-02-01' AND created_at < '2026-03-01';
```

Both sides need the same number of columns with compatible types. `UNION`/`INTERSECT`/`EXCEPT` treat NULLs as equal (unlike `=`).

## 6. 🔨 Break it

1. Remove the `ON` clause from a join and use a comma: `SELECT count(*) FROM customers, orders;` — 12 × 18 = ? This is what a forgotten join condition does.
2. Run the fan-out query above for all orders; find every order whose `paid_total` is wrong.
3. `SELECT c.id FROM customers c WHERE c.id NOT IN (SELECT referred_by FROM customers);` → 0 rows. Add `WHERE referred_by IS NOT NULL` inside and compare.
4. Move `o.status = 'PAID'` from `ON` to `WHERE` in a LEFT JOIN and count rows before/after.
5. `SELECT full_name, department, MAX(salary) FROM employees GROUP BY department;` — read the error. How would you get the name of the top earner per department? (→ [`03-advanced-queries.md`](./03-advanced-queries.md))

## 7. 🐞 Debugging tips

- Count at every step: `SELECT count(*)` after each added join. Unexpected growth = fan-out or missing join condition; unexpected shrinkage = inner join where you needed LEFT, or a WHERE on the nullable side.
- Check uniqueness assumptions: `SELECT key, count(*) FROM t GROUP BY key HAVING count(*) > 1;`
- Qualify every column with a table alias in multi-table queries — avoids ambiguity errors and silent wrong-column picks after a schema change.

## 8. 🎤 Interview Q&A

<details><summary>Difference between INNER and LEFT JOIN?</summary>

INNER keeps only rows with a match on both sides. LEFT keeps every row from the left table and fills the right side's columns with NULL where there's no match. Use LEFT when absence is meaningful ("customers with zero orders").
</details>

<details><summary>How do you find rows in A with no match in B?</summary>

Anti-join: `WHERE NOT EXISTS (SELECT 1 FROM B WHERE B.a_id = A.id)`, or `LEFT JOIN B … WHERE B.pk IS NULL`. Avoid `NOT IN` when the subquery column can be NULL — one NULL makes the result empty.
</details>

<details><summary>What's a self join? Give an example.</summary>

Joining a table to itself with two aliases. Employee → manager (`employees e JOIN employees m ON m.id = e.manager_id`), or comparing consecutive rows, or finding duplicate pairs (`a.id < b.id AND a.email = b.email`).
</details>

<details><summary>UNION vs UNION ALL?</summary>

UNION removes duplicates (extra sort/hash step); UNION ALL concatenates. Use UNION ALL unless you actually need de-duplication.
</details>

<details><summary>Why did my SUM double after adding a join?</summary>

Fan-out: the new join matched multiple rows per key, duplicating the rows being summed. Aggregate each child table in a subquery first, then join the aggregates; or verify join cardinality before aggregating.
</details>

<details><summary>EXISTS vs IN — which is faster?</summary>

In modern Postgres they're usually planned the same (semi-join). Prefer EXISTS for correlated conditions and NOT EXISTS over NOT IN for correctness. Check with EXPLAIN rather than folklore.
</details>

<details><summary>Can you use an aggregate in WHERE?</summary>

No — WHERE runs before grouping. Use HAVING, or compute the aggregate in a subquery/CTE and filter outside.
</details>

## ✅ Mastery checklist

- [ ] Can draw the output of INNER / LEFT / FULL on a 3-row example by hand
- [ ] Wrote an anti-join three ways and explained the NOT IN trap
- [ ] Reproduced and fixed a fan-out bug
- [ ] Used `FILTER`, `COUNT(DISTINCT)`, `HAVING` correctly
- [ ] Solved Q21–Q33 and Q42–Q46 in [`sql-practice.md`](./sql-practice.md)
