# 🧪 SQL Practice — 66 Problems + 10 Plan Readings + 5 Slow-Query Fixes

All problems run against [`practice-schema.sql`](./practice-schema.sql) (the **TixHub** event-ticketing
database) on **PostgreSQL 16**. Every solution below was executed against that file.

```bash
createdb tixhub && psql -d tixhub -f practice-schema.sql
psql -d tixhub
```

Run these once per `psql` session so your output matches the text:

```sql
SET TIME ZONE 'UTC';                       -- date_trunc / ::date depend on the session time zone
SET max_parallel_workers_per_gather = 0;   -- keeps EXPLAIN plans simple (no Gather nodes)
```

## How to use this file

1. Read the prompt. **Write your query in a scratch file before opening the solution.**
2. Run it. Check the row count and a couple of rows by hand.
3. Open the solution. If yours differs, decide whether it is *wrong* or just *different* (NULLs? ties? duplicates?).
4. Log it in [`../trackers/technology-tracker.md`](../trackers/technology-tracker.md). Redo every ⭐ problem on Day 3/7/14.
5. Any write (`INSERT/UPDATE/DELETE`) is wrapped in `BEGIN … ROLLBACK` so the data stays intact.

| Level | Problems | When (see [ROADMAP](../ROADMAP.md)) | Target time |
|---|---|---|---|
| 🟢 Easy | Q1–Q20 | Week 3 | ≤ 5 min each |
| 🟡 Medium | Q21–Q50 | Weeks 4–5 | ≤ 12 min each |
| 🔴 Hard | Q51–Q66 | Week 6, re-done timed Week 24 | ≤ 20 min each |
| 🔍 Plan reading | P1–P10 | Week 6 | — |
| 🐢 Slow-query fixes | S1–S5 | Week 6, again Week 25 (performance polish) | — |

Schema cheat-sheet:

```
employees(id, full_name, title, department, manager_id→employees, salary NULL?, hired_on)
customers(id, email UNIQUE, full_name, city NULL?, country, referred_by→customers, created_at)
venues(id, name, city, capacity)
events(id, venue_id→venues, title, category, starts_at, base_price, status)
orders(id, customer_id→customers, status, promo_code NULL?, created_at)
order_items(order_id→orders, event_id→events, quantity, unit_price)   PK(order_id, event_id)
payments(id, order_id→orders, amount, method, status, created_at)
page_views(id, event_id→events, customer_id→customers NULL?, device, viewed_at)   200,000 rows
```

"Revenue" in this file means `SUM(quantity * unit_price)` over order_items of **PAID** orders, unless a problem says otherwise.

---

## 🟢 Easy (Q1–Q20)

### Q1 · Event calendar
List every event's `title`, `starts_at` and `base_price`, earliest first.

<details><summary>Solution</summary>

```sql
SELECT title, starts_at, base_price
FROM events
ORDER BY starts_at;
```
Without `ORDER BY`, row order is **undefined** — never rely on insertion order.
</details>

### Q2 · Canadian customers
Customers whose `country` is `CA`.

<details><summary>Solution</summary>

```sql
SELECT id, full_name, email
FROM customers
WHERE country = 'CA';
```
3 rows (ids 1, 6, 10).
</details>

### Q3 · Unknown city ⭐
Customers whose city is not recorded.

<details><summary>Solution</summary>

```sql
SELECT id, full_name
FROM customers
WHERE city IS NULL;
```
`WHERE city = NULL` returns **zero rows**: any comparison with NULL yields `UNKNOWN`, and `WHERE` keeps only `TRUE`. Answer: Jonas Berg (11).
</details>

### Q4 · Mid-priced events
Events with `base_price` between 30 and 100 inclusive, cheapest first; ties by title.

<details><summary>Solution</summary>

```sql
SELECT title, base_price
FROM events
WHERE base_price BETWEEN 30 AND 100
ORDER BY base_price, title;
```
`BETWEEN` is inclusive on both ends. 5 rows.
</details>

### Q5 · Three most expensive events

<details><summary>Solution</summary>

```sql
SELECT title, base_price
FROM events
ORDER BY base_price DESC
LIMIT 3;
```
If there could be ties at 3rd place, `LIMIT` cuts arbitrarily; use `FETCH FIRST 3 ROWS WITH TIES` (needs `ORDER BY`) to include them.
</details>

### Q6 · Distinct categories

<details><summary>Solution</summary>

```sql
SELECT DISTINCT category
FROM events
ORDER BY category;
```
</details>

### Q7 · Promo orders
Orders that used a promo code.

<details><summary>Solution</summary>

```sql
SELECT id, customer_id, promo_code
FROM orders
WHERE promo_code IS NOT NULL;
```
</details>

### Q8 · Not in Toronto (including unknown) ⭐
Customers whose city is not Toronto. A customer with no recorded city should be **included**.

<details><summary>Solution</summary>

```sql
SELECT id, full_name, city
FROM customers
WHERE city IS DISTINCT FROM 'Toronto';
```
`city <> 'Toronto'` silently drops Jonas (NULL city). `IS DISTINCT FROM` is the NULL-safe `<>`. Equivalent: `WHERE city <> 'Toronto' OR city IS NULL`. 9 rows.
</details>

### Q9 · Recent hires
Employees hired on or after 2023-01-01, newest first.

<details><summary>Solution</summary>

```sql
SELECT full_name, hired_on
FROM employees
WHERE hired_on >= DATE '2023-01-01'
ORDER BY hired_on DESC;
```
</details>

### Q10 · Orders per status

<details><summary>Solution</summary>

```sql
SELECT status, COUNT(*) AS orders
FROM orders
GROUP BY status
ORDER BY orders DESC;
```
PAID 15, then CANCELLED/PENDING/REFUNDED 1 each.
</details>

### Q11 · Money collected
Total amount of `SUCCEEDED` payments.

<details><summary>Solution</summary>

```sql
SELECT SUM(amount) AS collected
FROM payments
WHERE status = 'SUCCEEDED';
```
Answer: 4148.00 — which includes a **double charge** on order 7 (see Q31). Aggregates are only as correct as the data.
</details>

### Q12 · COUNT(*) vs COUNT(col) ⭐
In one query: number of employees, number with a salary, and the average salary.

<details><summary>Solution</summary>

```sql
SELECT COUNT(*)       AS employees,
       COUNT(salary)  AS with_salary,
       ROUND(AVG(salary), 2) AS avg_salary
FROM employees;
```
11, 10, 154500.00. `COUNT(col)`, `AVG`, `SUM`, `MIN`, `MAX` **skip NULLs**. If you wanted the intern counted as 0 you'd write `AVG(COALESCE(salary, 0))` — a business decision, not a SQL one.
</details>

### Q13 · Average salary per department
Rounded to whole units, highest first.

<details><summary>Solution</summary>

```sql
SELECT department, ROUND(AVG(salary)) AS avg_salary, COUNT(*) AS headcount
FROM employees
GROUP BY department
ORDER BY avg_salary DESC;
```
</details>

### Q14 · Line totals
For each order item show `order_id`, `event_id`, and `line_total = quantity * unit_price`, largest first.

<details><summary>Solution</summary>

```sql
SELECT order_id, event_id, quantity * unit_price AS line_total
FROM order_items
ORDER BY line_total DESC;
```
You can `ORDER BY` a select-list alias, but you **cannot** use it in `WHERE` (WHERE is evaluated before SELECT).
</details>

### Q15 · Big orders
Order ids whose item total exceeds 300.

<details><summary>Solution</summary>

```sql
SELECT order_id, SUM(quantity * unit_price) AS total
FROM order_items
GROUP BY order_id
HAVING SUM(quantity * unit_price) > 300
ORDER BY total DESC;
```
`WHERE` filters rows before grouping; `HAVING` filters groups after.
</details>

### Q16 · February orders ⭐
Orders created in February 2026.

<details><summary>Solution</summary>

```sql
SELECT id, created_at
FROM orders
WHERE created_at >= TIMESTAMPTZ '2026-02-01 00:00+00'
  AND created_at <  TIMESTAMPTZ '2026-03-01 00:00+00'
ORDER BY created_at;
```
Use a **half-open range** `[start, next_start)`. `BETWEEN '2026-02-01' AND '2026-02-28'` misses everything after midnight on the 28th, and `WHERE date_trunc('month', created_at) = …` can't use an index on `created_at`. 6 rows.
</details>

### Q17 · Views by device

<details><summary>Solution</summary>

```sql
SELECT device, COUNT(*) AS views
FROM page_views
GROUP BY device
ORDER BY views DESC;
```
</details>

### Q18 · Events with their venue

<details><summary>Solution</summary>

```sql
SELECT e.title, v.name AS venue, v.city
FROM events e
JOIN venues v ON v.id = e.venue_id
ORDER BY e.starts_at;
```
</details>

### Q19 · Insert and get the id back
Add venue "Old Mill Theatre" in Toronto, capacity 450, and return the generated id.

<details><summary>Solution</summary>

```sql
BEGIN;
INSERT INTO venues (name, city, capacity)
VALUES ('Old Mill Theatre', 'Toronto', 450)
RETURNING id;
ROLLBACK;
```
Returns 5 the first time (the script `setval`'d the identity sequence) and **6 the next time** — sequences are not rolled back with the transaction, which is why ids have gaps (Q44). `RETURNING` saves a second round-trip — JDBC exposes it via `getGeneratedKeys()`.
</details>

### Q20 · Price increase
Raise `base_price` by 10% for every **scheduled concert**; return title, old and new price.

<details><summary>Solution</summary>

```sql
BEGIN;
UPDATE events AS e
SET    base_price = ROUND(e.base_price * 1.10, 2)
FROM   events AS old
WHERE  old.id = e.id
  AND  e.category = 'CONCERT'
  AND  e.status = 'SCHEDULED'
RETURNING e.title, old.base_price AS old_price, e.base_price AS new_price;
ROLLBACK;
```
`RETURNING` sees the new values; the self-join on `old` exposes the pre-update row. Always run an `UPDATE` as a `SELECT` with the same `WHERE` first. An `UPDATE` without `WHERE` updates every row.
</details>

---

## 🟡 Medium (Q21–Q50)

### Q21 · Second highest salary ⭐
Return the second highest **distinct** salary (NULL if it doesn't exist). Classic: LeetCode 176.

<details><summary>Solution</summary>

```sql
SELECT MAX(salary) AS second_highest
FROM employees
WHERE salary < (SELECT MAX(salary) FROM employees);

-- alternative
SELECT (SELECT DISTINCT salary
        FROM employees
        WHERE salary IS NOT NULL
        ORDER BY salary DESC
        OFFSET 1 LIMIT 1) AS second_highest;
```
210000. The outer `SELECT (subquery)` guarantees one row with NULL when nothing exists, instead of zero rows.
</details>

### Q22 · N-th highest salary (N = 3) ⭐

<details><summary>Solution</summary>

```sql
SELECT DISTINCT salary
FROM (
    SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS rnk
    FROM employees
    WHERE salary IS NOT NULL
) ranked
WHERE rnk = 3;
```
180000 (Carmen and Dev tie at 3rd). With `RANK()` the next value after the tie is rank 5; with `ROW_NUMBER()` the tie is broken arbitrarily. Know all three.
</details>

### Q23 · Employee and manager
Every employee with their manager's name; the CEO must still appear.

<details><summary>Solution</summary>

```sql
SELECT e.full_name AS employee, m.full_name AS manager
FROM employees e
LEFT JOIN employees m ON m.id = e.manager_id
ORDER BY e.id;
```
Self-join = the same table under two aliases. `JOIN` would drop Amara (no manager).
</details>

### Q24 · Earns more than their manager ⭐
(LeetCode 181 pattern.)

<details><summary>Solution</summary>

```sql
SELECT e.full_name, e.salary, m.full_name AS manager, m.salary AS manager_salary
FROM employees e
JOIN employees m ON m.id = e.manager_id
WHERE e.salary > m.salary;
```
Ines Costa (155000 > 150000).
</details>

### Q25 · Customers who never ordered ⭐ (anti-join)

<details><summary>Solution</summary>

```sql
-- NOT EXISTS (preferred)
SELECT c.id, c.full_name
FROM customers c
WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.customer_id = c.id);

-- LEFT JOIN … IS NULL
SELECT c.id, c.full_name
FROM customers c
LEFT JOIN orders o ON o.customer_id = c.id
WHERE o.id IS NULL;
```
Ids 9, 11, 12. Postgres plans both as a *Hash Anti Join*. Test the right-side **PK** (`o.id`) for NULL, not a nullable column.
</details>

### Q26 · The NOT IN trap ⭐
Find customers who have never referred anyone. First try `NOT IN (SELECT referred_by FROM customers)`. Explain the result, then fix it.

<details><summary>Solution</summary>

```sql
-- returns ZERO rows:
SELECT id, full_name FROM customers
WHERE id NOT IN (SELECT referred_by FROM customers);

-- correct:
SELECT c.id, c.full_name
FROM customers c
WHERE NOT EXISTS (SELECT 1 FROM customers r WHERE r.referred_by = c.id);
```
`x NOT IN (1, 2, NULL)` = `x<>1 AND x<>2 AND x<>NULL` → the last term is UNKNOWN, so the whole thing is never TRUE. **Never use `NOT IN` with a subquery on a nullable column.** 8 rows.
</details>

### Q27 · Events with zero sales

<details><summary>Solution</summary>

```sql
SELECT e.id, e.title
FROM events e
WHERE NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.event_id = e.id);
```
Stand-up Gala (10).
</details>

### Q28 · Revenue per event, including zero ⭐
Every event with its PAID revenue; events with no paid sales show 0.

<details><summary>Solution</summary>

```sql
SELECT e.id, e.title,
       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS revenue
FROM events e
LEFT JOIN order_items oi ON oi.event_id = e.id
LEFT JOIN orders o       ON o.id = oi.order_id AND o.status = 'PAID'
GROUP BY e.id, e.title
ORDER BY revenue DESC;
```
**Bug to avoid:** the version above still sums items of unpaid orders, because `oi` rows survive even when the `o` join fails. Correct it by joining items and orders first:

```sql
SELECT e.id, e.title,
       COALESCE(SUM(p.quantity * p.unit_price), 0) AS revenue
FROM events e
LEFT JOIN (
    SELECT oi.event_id, oi.quantity, oi.unit_price
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id
    WHERE o.status = 'PAID'
) p ON p.event_id = e.id
GROUP BY e.id, e.title
ORDER BY revenue DESC;
```
And putting `WHERE o.status = 'PAID'` on the outer query would turn the LEFT JOIN back into an inner join (events with no sales vanish). Where a predicate lives — `ON` vs `WHERE` — changes the answer for outer joins.
</details>

### Q29 · Frequent buyers
Customers with more than 2 PAID orders.

<details><summary>Solution</summary>

```sql
SELECT c.id, c.full_name, COUNT(*) AS paid_orders
FROM customers c
JOIN orders o ON o.customer_id = c.id
WHERE o.status = 'PAID'
GROUP BY c.id, c.full_name
HAVING COUNT(*) > 2;
```
Ava (id 1) with 5. Grouping by `c.id` lets Postgres accept `c.full_name` (functional dependency on the PK), but listing both is clearer.
</details>

### Q30 · Case-insensitive duplicate e-mails ⭐

<details><summary>Solution</summary>

```sql
SELECT lower(email) AS email_key, COUNT(*) AS n, array_agg(id ORDER BY id) AS ids
FROM customers
GROUP BY lower(email)
HAVING COUNT(*) > 1;
```
`ava@example.com` → {1,10}. The `UNIQUE` constraint on `email` is case-sensitive. Real fix: `CREATE UNIQUE INDEX ON customers (lower(email))` (after cleaning — Q62) or the `citext` extension.
</details>

### Q31 · Double charges ⭐
Pairs of SUCCEEDED payments for the same order and amount created within 5 minutes of each other.

<details><summary>Solution</summary>

```sql
SELECT a.order_id, a.id AS first_payment, b.id AS duplicate_payment,
       b.created_at - a.created_at AS gap
FROM payments a
JOIN payments b
  ON  b.order_id = a.order_id
  AND b.amount   = a.amount
  AND b.id       > a.id                    -- each pair once, no self-match
  AND b.created_at - a.created_at <= INTERVAL '5 minutes'
WHERE a.status = 'SUCCEEDED' AND b.status = 'SUCCEEDED';
```
Order 7: payments 7 and 8, 30 seconds apart. This is exactly why FlowGrid's order POST and LedgerX's transfer POST take an **Idempotency-Key** (see [`../06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md)).
</details>

### Q32 · Delete the duplicate, keep the first

<details><summary>Solution</summary>

```sql
BEGIN;
DELETE FROM payments p
USING payments keep
WHERE keep.order_id = p.order_id
  AND keep.amount   = p.amount
  AND keep.status   = 'SUCCEEDED'
  AND p.status      = 'SUCCEEDED'
  AND keep.id       < p.id
RETURNING p.*;
ROLLBACK;
```
Returns payment 8 only. In production you'd refund, not delete — but this pattern is the standard "delete duplicates keeping lowest id" interview answer. A `ROW_NUMBER()` variant: `DELETE … WHERE id IN (SELECT id FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY order_id, amount ORDER BY id) rn FROM payments WHERE status='SUCCEEDED') t WHERE rn > 1)`.
</details>

### Q33 · Paid but not paid
Orders with status PAID that have no SUCCEEDED payment.

<details><summary>Solution</summary>

```sql
SELECT o.id, o.customer_id, o.created_at
FROM orders o
WHERE o.status = 'PAID'
  AND NOT EXISTS (
      SELECT 1 FROM payments p
      WHERE p.order_id = o.id AND p.status = 'SUCCEEDED'
  );
```
Order 20. Data-integrity checks like this are good candidates for a nightly job.
</details>

### Q34 · Latest order per customer ⭐ (top-1 per group)

<details><summary>Solution</summary>

```sql
-- Postgres-specific, concise
SELECT DISTINCT ON (customer_id) customer_id, id AS order_id, created_at
FROM orders
ORDER BY customer_id, created_at DESC;

-- portable
SELECT customer_id, order_id, created_at
FROM (
    SELECT customer_id, id AS order_id, created_at,
           ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY created_at DESC) AS rn
    FROM orders
) t
WHERE rn = 1
ORDER BY customer_id;
```
`DISTINCT ON` keeps the first row per key **according to ORDER BY**, whose leading columns must match the `DISTINCT ON` list.
</details>

### Q35 · Top-2 events per category by tickets sold ⭐ (top-N per group)

<details><summary>Solution</summary>

```sql
WITH sold AS (
    SELECT e.category, e.title, SUM(oi.quantity) AS tickets
    FROM order_items oi
    JOIN orders o ON o.id = oi.order_id AND o.status = 'PAID'
    JOIN events e ON e.id = oi.event_id
    GROUP BY e.category, e.id, e.title
),
ranked AS (
    SELECT *, DENSE_RANK() OVER (PARTITION BY category ORDER BY tickets DESC) AS rnk
    FROM sold
)
SELECT category, title, tickets, rnk
FROM ranked
WHERE rnk <= 2
ORDER BY category, rnk, title;
```
You cannot put a window function in `WHERE` (windows are computed after WHERE) — hence the CTE/subquery.
</details>

### Q36 · Monthly revenue

<details><summary>Solution</summary>

```sql
SELECT date_trunc('month', o.created_at)::date AS month,
       SUM(oi.quantity * oi.unit_price)         AS revenue
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
WHERE o.status = 'PAID'
GROUP BY 1
ORDER BY 1;
```
</details>

### Q37 · Month-over-month change ⭐ (LAG)

<details><summary>Solution</summary>

```sql
WITH monthly AS (
    SELECT date_trunc('month', o.created_at)::date AS month,
           SUM(oi.quantity * oi.unit_price)         AS revenue
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.id
    WHERE o.status = 'PAID'
    GROUP BY 1
)
SELECT month, revenue,
       LAG(revenue) OVER (ORDER BY month)                          AS prev_revenue,
       revenue - LAG(revenue) OVER (ORDER BY month)                AS delta,
       ROUND(100.0 * (revenue - LAG(revenue) OVER (ORDER BY month))
             / NULLIF(LAG(revenue) OVER (ORDER BY month), 0), 1)   AS pct_change
FROM monthly
ORDER BY month;
```
`NULLIF(x, 0)` avoids division-by-zero errors. First month's `prev_revenue` is NULL. Months with no sales don't appear at all — Q57 shows how to fill gaps with `generate_series`.
</details>

### Q38 · Running total of revenue by day ⭐

<details><summary>Solution</summary>

```sql
WITH daily AS (
    SELECT o.created_at::date AS day, SUM(oi.quantity * oi.unit_price) AS revenue
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.id
    WHERE o.status = 'PAID'
    GROUP BY 1
)
SELECT day, revenue,
       SUM(revenue) OVER (ORDER BY day) AS running_total
FROM daily
ORDER BY day;
```
With `ORDER BY` inside `OVER`, the default frame is `RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW` → running total. Peers (same `day`) are summed together, which is why we aggregate per day first.
</details>

### Q39 · Share of lifetime spend
For each PAID order: its total and what % of that customer's lifetime PAID spend it represents.

<details><summary>Solution</summary>

```sql
WITH order_totals AS (
    SELECT o.id, o.customer_id, SUM(oi.quantity * oi.unit_price) AS total
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.id
    WHERE o.status = 'PAID'
    GROUP BY o.id, o.customer_id
)
SELECT id, customer_id, total,
       ROUND(100 * total / SUM(total) OVER (PARTITION BY customer_id), 1) AS pct_of_customer
FROM order_totals
ORDER BY customer_id, id;
```
A window function with `PARTITION BY` and no `ORDER BY` aggregates the whole partition **without collapsing rows** — the key difference from `GROUP BY`.
</details>

### Q40 · ROW_NUMBER vs RANK vs DENSE_RANK
Rank employees by salary (highest first, NULL salaries excluded) with all three functions side by side.

<details><summary>Solution</summary>

```sql
SELECT full_name, salary,
       ROW_NUMBER() OVER w AS row_num,
       RANK()       OVER w AS rnk,
       DENSE_RANK() OVER w AS dense_rnk
FROM employees
WHERE salary IS NOT NULL
WINDOW w AS (ORDER BY salary DESC)
ORDER BY salary DESC, full_name;
```
Carmen/Dev: row_num 3/4, rank 3/3, dense 3/3; Felix next: row 5, rank 5, dense 4.
</details>

### Q41 · Venue utilization
For each event with PAID sales: tickets sold, venue capacity, and % of capacity sold.

<details><summary>Solution</summary>

```sql
SELECT e.title, v.name AS venue,
       SUM(oi.quantity) AS sold,
       v.capacity,
       ROUND(100.0 * SUM(oi.quantity) / v.capacity, 3) AS pct_sold
FROM events e
JOIN venues v       ON v.id = e.venue_id
JOIN order_items oi ON oi.event_id = e.id
JOIN orders o       ON o.id = oi.order_id AND o.status = 'PAID'
GROUP BY e.id, e.title, v.name, v.capacity
ORDER BY pct_sold DESC;
```
`100.0 *` forces numeric division; `integer / integer` truncates (`3 / 300 = 0`).
</details>

### Q42 · Both concerts and comedy
Customers who (in any order status) bought tickets for at least one CONCERT **and** at least one COMEDY event.

<details><summary>Solution</summary>

```sql
SELECT o.customer_id
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN events e       ON e.id = oi.event_id
WHERE e.category IN ('CONCERT', 'COMEDY')
GROUP BY o.customer_id
HAVING COUNT(DISTINCT e.category) = 2;
```
Customers 1 and 3. Alternative: `INTERSECT` of two single-category queries.
</details>

### Q43 · Above category average (correlated subquery)
Events whose `base_price` is above the average `base_price` of their own category.

<details><summary>Solution</summary>

```sql
SELECT e.title, e.category, e.base_price
FROM events e
WHERE e.base_price > (
    SELECT AVG(e2.base_price) FROM events e2 WHERE e2.category = e.category
);

-- window version (one pass)
SELECT title, category, base_price
FROM (
    SELECT title, category, base_price,
           AVG(base_price) OVER (PARTITION BY category) AS cat_avg
    FROM events
) t
WHERE base_price > cat_avg;
```
A **correlated** subquery references the outer row (`e.category`), so conceptually it runs once per outer row.
</details>

### Q44 · Missing order ids ⭐ (gaps)

<details><summary>Solution</summary>

```sql
SELECT s.id AS missing_id
FROM generate_series(1, (SELECT MAX(id) FROM orders)) AS s(id)
WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.id = s.id);

-- as ranges, without generate_series
SELECT id + 1 AS gap_start, next_id - 1 AS gap_end
FROM (SELECT id, LEAD(id) OVER (ORDER BY id) AS next_id FROM orders) t
WHERE next_id - id > 1;
```
12 and 17. Interview follow-up: gaps in identity/sequence values are **normal** (rolled-back transactions and cached sequence values never get reused). Never use ids to count rows.
</details>

### Q45 · Pivot: tickets per category per month

<details><summary>Solution</summary>

```sql
SELECT date_trunc('month', o.created_at)::date AS month,
       COALESCE(SUM(oi.quantity) FILTER (WHERE e.category = 'CONCERT'),    0) AS concert,
       COALESCE(SUM(oi.quantity) FILTER (WHERE e.category = 'COMEDY'),     0) AS comedy,
       COALESCE(SUM(oi.quantity) FILTER (WHERE e.category = 'SPORTS'),     0) AS sports,
       COALESCE(SUM(oi.quantity) FILTER (WHERE e.category = 'CONFERENCE'), 0) AS conference
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN events e       ON e.id = oi.event_id
WHERE o.status = 'PAID'
GROUP BY 1
ORDER BY 1;
```
`FILTER (WHERE …)` is the Postgres-standard way; the portable form is `SUM(CASE WHEN … THEN oi.quantity ELSE 0 END)`.
</details>

### Q46 · Referral leaderboard

<details><summary>Solution</summary>

```sql
SELECT r.id, r.full_name, COUNT(c.id) AS referrals
FROM customers r
LEFT JOIN customers c ON c.referred_by = r.id
GROUP BY r.id, r.full_name
ORDER BY referrals DESC, r.id;
```
Ava and Ben have 2 each. `COUNT(c.id)` (not `COUNT(*)`) so non-referrers show 0, not 1.
</details>

### Q47 · Device mix for the hot event
Views per device for event 9 in March 2026.

<details><summary>Solution</summary>

```sql
SELECT device, COUNT(*) AS views
FROM page_views
WHERE event_id = 9
  AND viewed_at >= '2026-03-01' AND viewed_at < '2026-04-01'
GROUP BY device
ORDER BY views DESC;
```
Run it with `EXPLAIN ANALYZE` now and again after S1 — note the plan change.
</details>

### Q48 · Average gap between a customer's orders

<details><summary>Solution</summary>

```sql
WITH gaps AS (
    SELECT customer_id,
           created_at - LAG(created_at) OVER (PARTITION BY customer_id ORDER BY created_at) AS gap
    FROM orders
)
SELECT customer_id, AVG(gap) AS avg_gap, COUNT(gap) AS gaps_measured
FROM gaps
WHERE gap IS NOT NULL
GROUP BY customer_id
ORDER BY customer_id;
```
Subtracting timestamps gives an `interval`; `AVG(interval)` works but prints oddities like `16 days 29:12:00` — wrap in `justify_interval(...)` or use `EXTRACT(EPOCH FROM gap) / 86400` for days as a number.
</details>

### Q49 · Median PAID order value

<details><summary>Solution</summary>

```sql
WITH order_totals AS (
    SELECT o.id, SUM(oi.quantity * oi.unit_price) AS total
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.id
    WHERE o.status = 'PAID'
    GROUP BY o.id
)
SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY total) AS median,
       ROUND(AVG(total), 2)                                AS mean
FROM order_totals;
```
`percentile_cont` interpolates; `percentile_disc` returns an actual value from the set.
</details>

### Q50 · First order used a promo

<details><summary>Solution</summary>

```sql
SELECT customer_id, order_id, promo_code
FROM (
    SELECT DISTINCT ON (customer_id) customer_id, id AS order_id, promo_code
    FROM orders
    ORDER BY customer_id, created_at
) firsts
WHERE promo_code IS NOT NULL;
```
Customers 2 and 7. Filtering **before** picking the first order (`WHERE promo_code IS NOT NULL` inside) would answer a different question.
</details>

---

## 🔴 Hard (Q51–Q66)

### Q51 · Org chart under the CTO ⭐ (recursive CTE)
Everyone who reports (directly or indirectly) to Bruno Silva (id 2), with depth and a readable path.

<details><summary>Solution</summary>

```sql
WITH RECURSIVE org AS (
    SELECT id, full_name, manager_id, 0 AS depth, full_name AS path
    FROM employees
    WHERE id = 2                                   -- anchor
    UNION ALL
    SELECT e.id, e.full_name, e.manager_id, org.depth + 1,
           org.path || ' > ' || e.full_name        -- recursive step
    FROM employees e
    JOIN org ON e.manager_id = org.id
)
SELECT repeat('    ', depth) || full_name AS tree, depth, path
FROM org
ORDER BY path;
```
6 rows (Bruno + 5 reports). A cycle in the data would recurse forever; guard with a depth limit (`WHERE org.depth < 20`) or Postgres 14+'s `CYCLE id SET is_cycle USING visited` clause.
</details>

### Q52 · Chain of command upward
From Jamal Wright (id 10) up to the CEO.

<details><summary>Solution</summary>

```sql
WITH RECURSIVE chain AS (
    SELECT id, full_name, manager_id, 0 AS level
    FROM employees WHERE id = 10
    UNION ALL
    SELECT m.id, m.full_name, m.manager_id, chain.level + 1
    FROM employees m
    JOIN chain ON m.id = chain.manager_id
)
SELECT level, full_name FROM chain ORDER BY level;
```
Jamal → Elena → Dev → Bruno → Amara.
</details>

### Q53 · Gaps and islands: consecutive order days ⭐
For each customer, find runs of **consecutive calendar days** with at least one order (runs of length ≥ 2).

<details><summary>Solution</summary>

```sql
WITH days AS (
    SELECT DISTINCT customer_id, created_at::date AS d
    FROM orders
),
grp AS (
    SELECT customer_id, d,
           d - (ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY d))::int AS island
    FROM days
)
SELECT customer_id, MIN(d) AS start_day, MAX(d) AS end_day, COUNT(*) AS length
FROM grp
GROUP BY customer_id, island
HAVING COUNT(*) >= 2
ORDER BY customer_id, start_day;
```
Trick: in a run of consecutive dates, `date − row_number` is constant, so it labels the island. Customer 1: Jan 5–7 (3) and Feb 10–11 (2). `DISTINCT` matters — two orders on the same day would break the arithmetic.
</details>

### Q54 · Longest streak per customer

<details><summary>Solution</summary>

```sql
WITH days AS (
    SELECT DISTINCT customer_id, created_at::date AS d FROM orders
),
grp AS (
    SELECT customer_id, d,
           d - (ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY d))::int AS island
    FROM days
),
islands AS (
    SELECT customer_id, MIN(d) AS start_day, COUNT(*) AS length
    FROM grp GROUP BY customer_id, island
)
SELECT DISTINCT ON (customer_id) customer_id, length AS longest_streak, start_day
FROM islands
ORDER BY customer_id, length DESC, start_day;
```
</details>

### Q55 · Next-month retention by cohort ⭐
Cohort = month of a customer's first PAID order. For each cohort: size, how many placed a PAID order in the following month, and the %.

<details><summary>Solution</summary>

```sql
WITH firsts AS (
    SELECT customer_id, date_trunc('month', MIN(created_at)) AS cohort
    FROM orders WHERE status = 'PAID'
    GROUP BY customer_id
),
activity AS (
    SELECT DISTINCT customer_id, date_trunc('month', created_at) AS month
    FROM orders WHERE status = 'PAID'
)
SELECT f.cohort::date                                   AS cohort,
       COUNT(*)                                         AS cohort_size,
       COUNT(a.customer_id)                             AS retained_next_month,
       ROUND(100.0 * COUNT(a.customer_id) / COUNT(*), 1) AS retention_pct
FROM firsts f
LEFT JOIN activity a
       ON a.customer_id = f.customer_id
      AND a.month = f.cohort + INTERVAL '1 month'
GROUP BY f.cohort
ORDER BY f.cohort;
```
January cohort (customers 1, 2): both ordered in February → 100%. `activity` must be `DISTINCT` or a customer with two orders next month is counted twice.
</details>

### Q56 · Relational division: every Toronto venue
Customers who bought tickets (any order status) for events at **every** venue in Toronto.

<details><summary>Solution</summary>

```sql
-- counting
SELECT o.customer_id
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
JOIN events e       ON e.id = oi.event_id
JOIN venues v       ON v.id = e.venue_id
WHERE v.city = 'Toronto'
GROUP BY o.customer_id
HAVING COUNT(DISTINCT v.id) = (SELECT COUNT(*) FROM venues WHERE city = 'Toronto');

-- double NOT EXISTS: "there is no Toronto venue this customer hasn't bought at"
SELECT c.id
FROM customers c
WHERE NOT EXISTS (
    SELECT 1 FROM venues v
    WHERE v.city = 'Toronto'
      AND NOT EXISTS (
          SELECT 1
          FROM orders o
          JOIN order_items oi ON oi.order_id = o.id
          JOIN events e       ON e.id = oi.event_id
          WHERE o.customer_id = c.id AND e.venue_id = v.id
      )
);
```
Customer 1 only. Edge case: if there were **no** Toronto venues, the NOT EXISTS version returns every customer (vacuous truth), the counting version returns none.
</details>

### Q57 · 7-day rolling average with no missing days ⭐
Daily views for event 9 across Jan 1 – Jun 29 2026 (**days with 0 views must appear**) plus a 7-day trailing average.

<details><summary>Solution</summary>

```sql
WITH counts AS (
    SELECT viewed_at::date AS day, COUNT(*) AS views
    FROM page_views
    WHERE event_id = 9
    GROUP BY 1
),
days AS (
    SELECT generate_series(DATE '2026-01-01', DATE '2026-06-29', INTERVAL '1 day')::date AS day
)
SELECT d.day,
       COALESCE(c.views, 0) AS views,
       ROUND(AVG(COALESCE(c.views, 0)) OVER (
             ORDER BY d.day ROWS BETWEEN 6 PRECEDING AND CURRENT ROW), 1) AS avg_7d
FROM days d
LEFT JOIN counts c USING (day)
ORDER BY d.day;
```
`ROWS BETWEEN 6 PRECEDING AND CURRENT ROW` counts physical rows — correct **only because** the calendar has no gaps. On gappy data use `RANGE BETWEEN INTERVAL '6 days' PRECEDING AND CURRENT ROW`.
</details>

### Q58 · Anonymous share per event

<details><summary>Solution</summary>

```sql
SELECT e.title,
       COUNT(*) AS views,
       ROUND(100.0 * COUNT(*) FILTER (WHERE pv.customer_id IS NULL) / COUNT(*), 1) AS anon_pct
FROM page_views pv
JOIN events e ON e.id = pv.event_id
GROUP BY e.id, e.title
ORDER BY views DESC;
```
</details>

### Q59 · FIRST_VALUE / LAST_VALUE frame trap ⭐
For each customer, their first and last order id in one row.

<details><summary>Solution</summary>

```sql
SELECT DISTINCT customer_id,
       FIRST_VALUE(id) OVER w AS first_order,
       LAST_VALUE(id)  OVER w AS last_order
FROM orders
WINDOW w AS (PARTITION BY customer_id ORDER BY created_at
             ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)
ORDER BY customer_id;
```
Without the explicit frame, the default ends at `CURRENT ROW`, so `LAST_VALUE` returns **the current row** — a classic bug. (Simpler here: `GROUP BY` with `MIN/MAX` if ids increase with time — but they're not guaranteed to.)
</details>

### Q60 · Best event per venue, ties included

<details><summary>Solution</summary>

```sql
WITH rev AS (
    SELECT e.venue_id, e.title, SUM(oi.quantity * oi.unit_price) AS revenue
    FROM events e
    JOIN order_items oi ON oi.event_id = e.id
    JOIN orders o       ON o.id = oi.order_id AND o.status = 'PAID'
    GROUP BY e.venue_id, e.id, e.title
)
SELECT v.name, r.title, r.revenue
FROM (
    SELECT rev.*, RANK() OVER (PARTITION BY venue_id ORDER BY revenue DESC) AS rnk
    FROM rev
) r
JOIN venues v ON v.id = r.venue_id
WHERE r.rnk = 1
ORDER BY v.name;
```
`RANK() = 1` keeps ties; `ROW_NUMBER() = 1` would silently drop one.
</details>

### Q61 · Idempotent upsert
Create a table `event_daily_views(event_id, day, views)` and load March 1st counts so that re-running the load **updates** instead of duplicating.

<details><summary>Solution</summary>

```sql
BEGIN;
CREATE TEMP TABLE event_daily_views (
    event_id integer NOT NULL,
    day      date    NOT NULL,
    views    integer NOT NULL,
    PRIMARY KEY (event_id, day)
) ON COMMIT DROP;

INSERT INTO event_daily_views (event_id, day, views)
SELECT event_id, viewed_at::date, COUNT(*)
FROM page_views
WHERE viewed_at >= '2026-03-01' AND viewed_at < '2026-03-02'
GROUP BY 1, 2
ON CONFLICT (event_id, day) DO UPDATE SET views = EXCLUDED.views;

-- run the same load again: 10 rows updated, still 10 rows
INSERT INTO event_daily_views (event_id, day, views)
SELECT event_id, viewed_at::date, COUNT(*)
FROM page_views
WHERE viewed_at >= '2026-03-01' AND viewed_at < '2026-03-02'
GROUP BY 1, 2
ON CONFLICT (event_id, day) DO UPDATE SET views = EXCLUDED.views;

SELECT COUNT(*) FROM event_daily_views;
ROLLBACK;
```
`ON CONFLICT` needs a unique constraint/index on the conflict target. `EXCLUDED` is the row you tried to insert. MySQL's equivalent is `INSERT … ON DUPLICATE KEY UPDATE` (see [`07-postgres-vs-mysql.md`](./07-postgres-vs-mysql.md)). Postgres 15+ also has `MERGE`.
</details>

### Q62 · Merge duplicate customers safely ⭐
Merge customers that share an e-mail ignoring case into the lowest id, re-pointing every reference, then prevent recurrence. All-or-nothing.

<details><summary>Solution</summary>

```sql
BEGIN;
CREATE TEMP TABLE dupes ON COMMIT DROP AS
SELECT id AS dupe_id, MIN(id) OVER (PARTITION BY lower(email)) AS keeper_id
FROM customers;
DELETE FROM dupes WHERE dupe_id = keeper_id;

UPDATE orders     o SET customer_id = d.keeper_id FROM dupes d WHERE o.customer_id = d.dupe_id;
UPDATE page_views p SET customer_id = d.keeper_id FROM dupes d WHERE p.customer_id = d.dupe_id;
UPDATE customers  c SET referred_by = d.keeper_id FROM dupes d WHERE c.referred_by = d.dupe_id;
DELETE FROM customers c USING dupes d WHERE c.id = d.dupe_id;

CREATE UNIQUE INDEX customers_email_lower_uq ON customers (lower(email));
SELECT id, email FROM customers ORDER BY id;
ROLLBACK;   -- COMMIT for real
```
Forget the `page_views` update and the `DELETE` fails with `violates foreign key constraint` — the FK saved you. The transaction guarantees no half-merged state.
</details>

### Q63 · Peak day per event

<details><summary>Solution</summary>

```sql
WITH daily AS (
    SELECT event_id, viewed_at::date AS day, COUNT(*) AS views
    FROM page_views
    GROUP BY 1, 2
)
SELECT DISTINCT ON (event_id) event_id, day, views
FROM daily
ORDER BY event_id, views DESC, day;
```
Top-1 per group over an aggregate. The `day` tiebreaker makes the result deterministic.
</details>

### Q64 · Revenue from repeat vs first purchases
Label each PAID order as `first` or `repeat` for its customer, then show revenue and % per label.

<details><summary>Solution</summary>

```sql
WITH order_totals AS (
    SELECT o.id, o.customer_id, o.created_at, SUM(oi.quantity * oi.unit_price) AS total
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.id
    WHERE o.status = 'PAID'
    GROUP BY o.id, o.customer_id, o.created_at
),
labelled AS (
    SELECT *,
           CASE WHEN ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY created_at) = 1
                THEN 'first' ELSE 'repeat' END AS kind
    FROM order_totals
)
SELECT kind, COUNT(*) AS orders, SUM(total) AS revenue,
       ROUND(100 * SUM(total) / SUM(SUM(total)) OVER (), 1) AS pct
FROM labelled
GROUP BY kind;
```
`SUM(SUM(total)) OVER ()` — a window over the grouped result: the inner `SUM` is the aggregate, the outer is the window across all groups.
</details>

### Q65 · Next two shows per venue (LATERAL)
For every venue, the next two SCHEDULED events on or after 2026-06-01; venues with none still appear.

<details><summary>Solution</summary>

```sql
SELECT v.name, nxt.title, nxt.starts_at
FROM venues v
LEFT JOIN LATERAL (
    SELECT e.title, e.starts_at
    FROM events e
    WHERE e.venue_id = v.id
      AND e.status = 'SCHEDULED'
      AND e.starts_at >= '2026-06-01'
    ORDER BY e.starts_at
    LIMIT 2
) nxt ON true
ORDER BY v.name, nxt.starts_at;
```
`LATERAL` lets the subquery reference `v` — "for each row, run this top-N query". With an index on `events(venue_id, starts_at)` each lookup is a tiny index scan.
</details>

### Q66 · Payment reconciliation ⭐
For every non-cancelled order: order total, net collected (SUCCEEDED minus REFUNDED), and a verdict: `OK`, `UNDERPAID`, `OVERPAID`, `REFUNDED_OK`.

<details><summary>Solution</summary>

```sql
WITH totals AS (
    SELECT order_id, SUM(quantity * unit_price) AS order_total
    FROM order_items GROUP BY order_id
),
paid AS (
    SELECT order_id,
           COALESCE(SUM(amount) FILTER (WHERE status = 'SUCCEEDED'), 0)
         - COALESCE(SUM(amount) FILTER (WHERE status = 'REFUNDED'),  0) AS net
    FROM payments GROUP BY order_id
)
SELECT o.id, o.status, t.order_total, COALESCE(p.net, 0) AS net_collected,
       CASE
         WHEN o.status = 'REFUNDED' AND COALESCE(p.net, 0) = 0 THEN 'REFUNDED_OK'
         WHEN COALESCE(p.net, 0) = t.order_total              THEN 'OK'
         WHEN COALESCE(p.net, 0) < t.order_total              THEN 'UNDERPAID'
         ELSE 'OVERPAID'
       END AS verdict
FROM orders o
JOIN totals t    ON t.order_id = o.id
LEFT JOIN paid p ON p.order_id = o.id
WHERE o.status <> 'CANCELLED'
ORDER BY verdict DESC, o.id;
```
Finds order 7 (OVERPAID — double charge), order 20 (UNDERPAID — PAID with no payment) and order 11 (UNDERPAID, but it is PENDING, so expected). Pre-aggregate each child table **before** joining; joining `order_items` and `payments` directly multiplies rows (fan-out) and inflates both sums.
</details>

---

## 🔍 Reading query plans (P1–P10)

Real output from this database (`SET max_parallel_workers_per_gather = 0`). Your timings will differ; shapes and row counts won't. Each node: `(cost=startup..total rows=estimate width=bytes) (actual time=first..last rows=real loops=n)`. Costs are in arbitrary planner units; **actual time is per loop in ms**. Read plans **inside-out**: the most indented node runs first.

Setup for P3–P10: `CREATE INDEX idx_pv_event ON page_views (event_id); ANALYZE page_views;` (drop it afterwards).

### P1
```
Seq Scan on page_views  (cost=0.00..3883.00 rows=12220 width=29) (actual time=0.009..10.449 rows=12078 loops=1)
  Filter: (event_id = 3)
  Rows Removed by Filter: 187922
```
<details><summary>What is happening? What would you do?</summary>

Full table scan: read all 200,000 rows, keep 12,078 (6%). No usable index on `event_id`. Estimate (12,220) ≈ actual — statistics are fine. Adding an index on `event_id` helps because 6% is selective enough (see P3). "Rows Removed by Filter" much larger than rows returned is the classic sign of a missing index.
</details>

### P2
```
Index Scan using page_views_pkey on page_views  (cost=0.42..8.44 rows=1 width=29) (actual time=0.020..0.021 rows=1 loops=1)
  Index Cond: (id = 12345)
```
<details><summary>Explain</summary>

B-tree descent on the PK (≈3 page reads), then one heap fetch. `Index Cond` = used to navigate the index (good); `Filter` = checked after fetching (less good).
</details>

### P3
```
Bitmap Heap Scan on page_views  (cost=130.61..1659.27 rows=11653 width=29) (actual time=0.442..3.519 rows=12078 loops=1)
  Recheck Cond: (event_id = 3)
  Heap Blocks: exact=1383
  ->  Bitmap Index Scan on idx_pv_event  (cost=0.00..127.69 rows=11653 width=0) (actual time=0.299..0.300 rows=12078 loops=1)
        Index Cond: (event_id = 3)
```
<details><summary>Explain</summary>

Two phases: the Bitmap Index Scan collects matching row locations into a bitmap sorted by physical page; the Bitmap Heap Scan then visits each page once, in order. Chosen for "medium" selectivity — too many rows for one-by-one index lookups, too few for a seq scan. 10.4 ms → 3.5 ms vs P1. `Recheck Cond` applies if the bitmap becomes lossy (per-page instead of per-row) under memory pressure. `exact=1383` = all 1,383 heap pages were touched because event 3's rows are spread throughout the table.
</details>

### P4
```
Bitmap Heap Scan on page_views  (cost=1029.40..3562.56 rows=92013 width=29) (actual time=1.933..10.742 rows=91657 loops=1)
  Recheck Cond: (event_id = 9)
  Heap Blocks: exact=1383
  ->  Bitmap Index Scan on idx_pv_event  (cost=0.00..1006.39 rows=92013 width=0) (actual time=1.790..1.791 rows=91657 loops=1)
        Index Cond: (event_id = 9)
```
<details><summary>Same index, event 9. Did the index help?</summary>

Barely. Event 9 matches 46% of rows; every heap page is read anyway (`exact=1383`), so this is basically a seq scan plus index overhead. On a colder cache or slightly higher percentage the planner would pick a Seq Scan — and that would be *correct*. **Indexes help selective predicates**; for low-selectivity values they don't. Postgres knows the skew because `ANALYZE` stores "most common values" per column (`pg_stats.most_common_vals`).
</details>

### P5
```
HashAggregate  (cost=5131.73..5131.83 rows=10 width=22) (actual time=60.960..60.966 rows=10 loops=1)
  Group Key: e.title
  Batches: 1  Memory Usage: 24kB
  ->  Hash Join  (cost=1.23..4131.73 rows=200000 width=14) (actual time=0.028..38.278 rows=200000 loops=1)
        Hash Cond: (pv.event_id = e.id)
        ->  Seq Scan on page_views pv  (cost=0.00..3383.00 rows=200000 width=4) (actual time=0.006..12.745 rows=200000 loops=1)
        ->  Hash  (cost=1.10..1.10 rows=10 width=18) (actual time=0.016..0.019 rows=10 loops=1)
              Buckets: 1024  Batches: 1  Memory Usage: 9kB
              ->  Seq Scan on events e  (cost=0.00..1.10 rows=10 width=18) (actual time=0.004..0.006 rows=10 loops=1)
```
Query: `SELECT e.title, count(*) FROM page_views pv JOIN events e ON e.id = pv.event_id GROUP BY e.title;`
<details><summary>Explain</summary>

1. Seq-scan the small table (`events`, 10 rows) and build an in-memory hash table on `id`.
2. Seq-scan `page_views`, probe the hash for each row → Hash Join (equality joins, no index needed, O(n+m)).
3. HashAggregate groups by title in a hash table (24 kB). `Batches: 1` = fit in `work_mem`; more batches = spilled to disk.
The index on `event_id` is useless here — we need every row. The planner always builds the hash on the **smaller** input.
</details>

### P6
```
Nested Loop  (cost=130.61..1776.95 rows=11653 width=19) (actual time=1.336..6.444 rows=12078 loops=1)
  ->  Seq Scan on customers c  (cost=0.00..1.15 rows=1 width=15) (actual time=0.008..0.014 rows=1 loops=1)
        Filter: (id = 3)
        Rows Removed by Filter: 11
  ->  Bitmap Heap Scan on page_views pv  (cost=130.61..1659.27 rows=11653 width=12) (actual time=1.322..5.182 rows=12078 loops=1)
        Recheck Cond: (event_id = 3)
        ...
```
<details><summary>Why a Nested Loop? Why a Seq Scan on customers even though it has a PK?</summary>

Outer side returns 1 row, so the inner side runs once (`loops=1`) — nested loop is ideal when the outer input is tiny and the inner side is indexed. Nested loops become disasters when the outer side is large *and* the estimate was wrong (inner `loops=50000`). The PK is ignored on `customers` because the whole table is one 8 kB page — reading it is cheaper than touching an index too. **Seq scans on tiny tables are not a problem.**
(The join condition `pv.event_id = c.id` is nonsense on purpose — a plan is valid even for a meaningless query. Plans tell you *how*, never *whether it's right*.)
</details>

### P7
```
Aggregate  (cost=273.36..273.37 rows=1 width=8) (actual time=1.382..1.383 rows=1 loops=1)
  ->  Index Only Scan using idx_pv_event on page_views  (cost=0.29..244.22 rows=11653 width=0) (actual time=0.023..0.786 rows=12078 loops=1)
        Index Cond: (event_id = 3)
        Heap Fetches: 0
```
Query: `SELECT count(*) FROM page_views WHERE event_id = 3;` after `VACUUM page_views;`
<details><summary>Explain</summary>

Every column needed is in the index, so the heap is skipped. `Heap Fetches: 0` because `VACUUM` updated the **visibility map** (pages marked all-visible). Right after heavy writes, Heap Fetches climbs and index-only scans lose their advantage — this is one reason autovacuum matters. Same query without VACUUM: a bitmap scan.
</details>

### P8
```
Merge Join  (cost=0.84..13408.84 rows=200000 width=0) (actual time=0.036..60.797 rows=200000 loops=1)
  Merge Cond: (a.id = b.id)
  ->  Index Only Scan using page_views_pkey on page_views a  (...) (actual ... rows=200000 loops=1)
  ->  Index Only Scan using page_views_pkey on page_views b  (...) (actual ... rows=200000 loops=1)
```
(`SET enable_hashjoin = off;` then a self-join on `id`.)
<details><summary>Explain</summary>

Merge Join walks two **already sorted** inputs in lockstep (like merging in merge sort). Both sides come pre-sorted from the PK index, so there's no Sort node. Typical when both inputs are large and sorted on the join key. `enable_*` settings are for **experiments only** — never in production.
</details>

### P9
```
Aggregate  (cost=4385.50..4385.51 rows=1 width=8) (actual time=42.931..42.933 rows=1 loops=1)
  ->  Seq Scan on page_views  (cost=0.00..4383.00 rows=1000 width=0) (actual time=0.053..39.931 rows=66665 loops=1)
        Filter: (lower(device) = 'web'::text)
        Rows Removed by Filter: 133335
```
<details><summary>Find the problem</summary>

Estimate 1,000 vs actual 66,665 — off by 66×. Postgres has statistics for `device`, not for `lower(device)`, so it falls back to a default selectivity (0.5%). Bad estimates cascade: in a bigger query this would make the planner choose a nested loop where a hash join belonged. Fixes: don't wrap the column (`device = 'web'` — values are already lowercase via the CHECK), or create an expression index `ON page_views (lower(device))` (Postgres then gathers stats on the expression at the next ANALYZE). **Rule: compare estimated `rows` with actual `rows` at every node; a 10× gap is where to look.**
</details>

### P10
```
Limit  (cost=7704.93..7704.95 rows=10 width=29) (actual time=27.673..27.676 rows=10 loops=1)
  ->  Sort  (cost=7704.93..8204.93 rows=200000 width=29) (actual time=27.671..27.672 rows=10 loops=1)
        Sort Key: viewed_at DESC
        Sort Method: top-N heapsort  Memory: 26kB
        ->  Seq Scan on page_views  (...) (actual time=0.004..12.904 rows=200000 loops=1)
```
Compare with:
```
Limit  (cost=0.42..0.58 rows=5 width=29) (actual time=0.008..0.010 rows=5 loops=1)
  ->  Index Scan Backward using page_views_pkey on page_views  (...) (actual time=0.008..0.008 rows=5 loops=1)
```
<details><summary>Explain both</summary>

First: `ORDER BY viewed_at DESC LIMIT 10` with no index on `viewed_at` → read everything, keep the top 10 in a heap (top-N heapsort: O(n log k), tiny memory). Second: `ORDER BY id DESC LIMIT 5` → the PK index is already ordered; walk it backward and stop after 5 rows (0.01 ms). An index on the `ORDER BY` column turns "sort everything" into "read the first k". Note `Limit` total cost is small even though the child's is large — execution stops early.
</details>

---

## 🐢 Fix the slow query (S1–S5)

Start each from a clean schema (re-run `practice-schema.sql`). Measure **before and after** with `EXPLAIN (ANALYZE, BUFFERS)`; write the numbers in your notes.

### S1 · Dashboard: one event, one month
```sql
SELECT count(*) FROM page_views
WHERE event_id = 3 AND viewed_at >= '2026-03-01' AND viewed_at < '2026-04-01';
```
Before: `Seq Scan … Rows Removed by Filter: 197943`, ~12.8 ms.

<details><summary>Fix</summary>

```sql
CREATE INDEX idx_pv_event_viewed ON page_views (event_id, viewed_at);
```
After: `Index Only Scan using idx_pv_event_viewed … Index Cond: ((event_id = 3) AND (viewed_at >= …) AND (viewed_at < …))`, ~1.2 ms.
**Column order:** equality column first, range column second. `(viewed_at, event_id)` would have to scan all of March for all events and filter. This index also serves `WHERE event_id = 3` alone (leftmost prefix) but **not** `WHERE viewed_at >= …` alone. It's the same shape as a FlowGrid index on orders `(warehouse_id, created_at)` or a LedgerX index on `(account_id, created_at)`.
</details>

### S2 · The function-on-column trap
(Keep the S1 index.)
```sql
SELECT count(*) FROM page_views
WHERE event_id = 3 AND date(viewed_at) = '2026-03-01';
```
Plan: `Index Cond: (event_id = 3)` then `Filter: (date(viewed_at) = '2026-03-01'::date)  Rows Removed by Filter: 12022`.

<details><summary>Fix</summary>

```sql
SELECT count(*) FROM page_views
WHERE event_id = 3
  AND viewed_at >= '2026-03-01' AND viewed_at < '2026-03-02';
```
Now both columns are in `Index Cond`; 1.4 ms → 0.03 ms. Wrapping an indexed column in a function (`date()`, `lower()`, `::date`, arithmetic) makes the index unusable for that column (not **sargable**). You can't simply index `date(viewed_at)` either: for `timestamptz` it depends on the session time zone, so it isn't IMMUTABLE. Rewrite as a range.
</details>

### S3 · "Latest 10 views for this customer"
```sql
SELECT * FROM page_views WHERE customer_id = 5 ORDER BY viewed_at DESC LIMIT 10;
```
Before: `Seq Scan … Filter: (customer_id = 5)` + `top-N heapsort`, ~9 ms.

<details><summary>Fix</summary>

```sql
CREATE INDEX idx_pv_customer_viewed ON page_views (customer_id, viewed_at DESC);
```
After: `Limit -> Index Scan using idx_pv_customer_viewed … Index Cond: (customer_id = 5)` — no Sort node, 0.08 ms. The index delivers rows already in the requested order and the scan stops after 10. (A plain `(customer_id, viewed_at)` also works — B-trees can be scanned backward; `DESC` matters for mixed orders like `ORDER BY a ASC, b DESC`.) Also a reminder: **foreign-key columns are not indexed automatically in Postgres** — `customer_id` had no index at all.
</details>

### S4 · Correlated subquery per row
```sql
SELECT e.id, e.title,
       (SELECT count(*) FROM page_views pv
        WHERE pv.event_id = e.id AND pv.device = 'ios') AS ios_views
FROM events e;
```
Before: `SubPlan 1 -> Aggregate … loops=10 -> Seq Scan on page_views … loops=10`, ~101 ms.

<details><summary>Fix</summary>

```sql
SELECT e.id, e.title, count(pv.id) AS ios_views
FROM events e
LEFT JOIN page_views pv ON pv.event_id = e.id AND pv.device = 'ios'
GROUP BY e.id, e.title
ORDER BY e.id;
```
One pass + Hash Right Join + HashAggregate, ~29 ms. `loops=10` on a Seq Scan means you scanned 200k rows ten times. With 10,000 events it would be 10,000 scans. (Postgres sometimes de-correlates for you; don't count on it.) Same shape as the **N+1 problem** in JPA — see [`../05-spring-boot/03-data-jpa.md`](../05-spring-boot/03-data-jpa.md).
</details>

### S5 · Deep pagination
```sql
SELECT * FROM page_views ORDER BY id LIMIT 20 OFFSET 190000;
```
Before: `Index Scan using page_views_pkey … rows=190020`, ~24 ms — it reads and throws away 190,000 rows.

<details><summary>Fix</summary>

```sql
-- keyset / cursor pagination: client sends the last id it saw
SELECT * FROM page_views WHERE id > 190000 ORDER BY id LIMIT 20;
```
After: `Index Cond: (id > 190000)`, 0.05 ms, **constant** regardless of page depth. Trade-off: no "jump to page 9,500", and the sort key must be unique (use `(created_at, id)` tuples: `WHERE (created_at, id) > ($1, $2)`). Covered in [`../06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md#pagination).
</details>

---

## 🌐 External practice

- **LeetCode "SQL 50"** study plan: <https://leetcode.com/studyplan/top-sql-50/> — do all 50 across Weeks 4–6 (≈ 15–20/week in the learning block). Pick PostgreSQL as the dialect.
- Specific classics that mirror this file: [176. Second Highest Salary](https://leetcode.com/problems/second-highest-salary/), [177. Nth Highest Salary](https://leetcode.com/problems/nth-highest-salary/), [181. Employees Earning More Than Their Managers](https://leetcode.com/problems/employees-earning-more-than-their-managers/), [183. Customers Who Never Order](https://leetcode.com/problems/customers-who-never-order/), [184. Department Highest Salary](https://leetcode.com/problems/department-highest-salary/), [185. Department Top Three Salaries](https://leetcode.com/problems/department-top-three-salaries/), [180. Consecutive Numbers](https://leetcode.com/problems/consecutive-numbers/), [196. Delete Duplicate Emails](https://leetcode.com/problems/delete-duplicate-emails/), [197. Rising Temperature](https://leetcode.com/problems/rising-temperature/), [550. Game Play Analysis IV](https://leetcode.com/problems/game-play-analysis-iv/).
- PostgreSQL docs: *Tutorial* (Ch. 2–3), *Queries* (Ch. 7), *Window Functions* (§3.5 and §9.22), *Using EXPLAIN* (§14.1) — <https://www.postgresql.org/docs/16/>.

## ✅ Mastery checklist

- [ ] Solved Q1–Q20 without opening a solution
- [ ] Can explain the NOT IN / NULL trap (Q26) and the LEFT JOIN `ON` vs `WHERE` trap (Q28) from memory
- [ ] Can write top-N-per-group three ways: `ROW_NUMBER`, `DISTINCT ON`, `LATERAL`
- [ ] Solved Q53 (gaps & islands) and Q55 (retention) timed, ≤ 20 min each
- [ ] Can read P1–P10 aloud: node type, why chosen, estimate vs actual
- [ ] Did S1–S5 myself and recorded before/after timings
- [ ] Finished LeetCode SQL 50
