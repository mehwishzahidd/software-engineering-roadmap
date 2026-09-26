# 01 · SQL Basics — SELECT, Filtering, NULL, and Writing Data

> **Week 3** (foundation: SELECT … LIMIT, INSERT/UPDATE/DELETE, PostgreSQL + `psql`). It has to be solid before JPA starts in
> Week 4 with **FlowGrid M1**.
> Database: [`practice-schema.sql`](./practice-schema.sql). Practice: [`sql-practice.md`](./sql-practice.md) Q1–Q20.

## 1. The relational model in five sentences

1. A **table** (relation) is a set of **rows** (tuples) that all have the same **columns** (attributes), each with a type.
2. A table has **no inherent order**. Order exists only when you say `ORDER BY`.
3. Every row should be identifiable by a **primary key**; relationships are expressed by **foreign keys** that hold another table's key.
4. SQL is **declarative**: you describe *what* rows you want; the **planner** decides *how* (which index, which join algorithm) — see [`05-indexes-performance.md`](./05-indexes-performance.md).
5. Every query produces a new (virtual) table, which is why queries compose (subqueries, CTEs, views).

**Interview angle:** "Why is SQL declarative and why does it matter?" → the same query can go from 2 s to 2 ms by adding an index without changing a character of SQL.

## 2. Setup and `psql` survival kit

```bash
# PostgreSQL 16 in Docker (Week 4 moves to Docker Compose for FlowGrid; this is enough for now)
docker run --name pg -e POSTGRES_PASSWORD=pg -p 5432:5432 -d postgres:16
psql -h localhost -U postgres           # password: pg
```

```sql
CREATE DATABASE tixhub;
\c tixhub
\i practice-schema.sql
```

| Meta-command | Does |
|---|---|
| `\l` / `\c db` | list databases / connect |
| `\dt` / `\d orders` | list tables / describe a table (columns, indexes, FKs, checks) |
| `\di` | list indexes |
| `\x auto` | expanded display for wide rows |
| `\timing on` | print execution time of each statement |
| `\e` | open last query in `$EDITOR` |
| `\i file.sql` | run a file |
| `\?` / `\h UPDATE` | help on meta-commands / on SQL syntax |
| `\q` | quit |

## 3. Types you actually need (PostgreSQL)

| Use | Type | Notes / traps |
|---|---|---|
| Surrogate keys | `bigint GENERATED ALWAYS AS IDENTITY` | Standard SQL replacement for `serial`. `integer` tops out at ~2.1 billion. |
| Money | `numeric(12,2)` | **Never `float`/`double`**: `0.1 + 0.2 <> 0.3`. Java side: `BigDecimal` (FlowGrid prices, LedgerX amounts). |
| Text | `text` | `varchar(n)` only when a length limit is a business rule; same performance in Postgres. |
| Flags | `boolean` | Three values: true, false, NULL. |
| Calendar date | `date` | Birthdays, `hired_on`. |
| Point in time | `timestamptz` | Stores UTC, displays in session time zone. Prefer it over `timestamp` (no zone) for events that happened. |
| Duration | `interval` | `now() - created_at`. |
| Public ids | `uuid` | `gen_random_uuid()` (built in since PG 13). |
| Semi-structured | `jsonb` | Queryable, indexable (GIN). Don't use it to avoid designing a schema. |

```sql
SELECT 0.1::float8 + 0.2::float8 = 0.3::float8 AS float_equal,    -- false
       0.1::numeric + 0.2::numeric = 0.3::numeric AS numeric_equal; -- true
```

## 4. Anatomy of `SELECT` — and the order it really runs in

```sql
SELECT   c.country, COUNT(*) AS customers        -- 5. compute output columns
FROM     customers c                              -- 1. take rows from tables/joins
WHERE    c.created_at >= '2026-01-01'             -- 2. filter rows
GROUP BY c.country                                -- 3. form groups
HAVING   COUNT(*) >= 1                            -- 4. filter groups
ORDER BY customers DESC, c.country                -- 6. sort
LIMIT    5;                                       -- 7. cut
```

**Logical order: FROM → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT.**
Consequences you'll be asked about:

- You **can't** use a `SELECT` alias in `WHERE` (it doesn't exist yet). You **can** in `ORDER BY`.
- You **can't** filter on an aggregate in `WHERE` → use `HAVING`.
- You **can't** use a window function in `WHERE` → wrap in a subquery/CTE ([`03-advanced-queries.md`](./03-advanced-queries.md)).

## 5. Filtering

```sql
-- comparison, AND/OR, parentheses matter
SELECT title, category, base_price
FROM events
WHERE (category = 'CONCERT' OR category = 'COMEDY')
  AND base_price < 60;

-- IN / BETWEEN (inclusive) / pattern matching
SELECT title FROM events WHERE category IN ('SPORTS', 'CONFERENCE');
SELECT title FROM events WHERE base_price BETWEEN 25 AND 55;
SELECT email FROM customers WHERE email LIKE '%@example.com';   -- case-sensitive
SELECT email FROM customers WHERE email ILIKE 'ava@%';          -- case-insensitive (Postgres)
```

`%` = any sequence, `_` = exactly one character. A **leading** wildcard (`LIKE '%foo'`) cannot use a B-tree index.

**Timestamps: always use half-open ranges.**

```sql
SELECT id, created_at
FROM orders
WHERE created_at >= '2026-02-01' AND created_at < '2026-03-01';
```

## 6. NULL — three-valued logic (the #1 source of wrong answers)

`NULL` means *unknown / not applicable*. Any comparison with NULL yields **UNKNOWN**, and `WHERE` keeps only **TRUE**.

| a | b | a AND b | a OR b | NOT a |
|---|---|---|---|---|
| TRUE | UNKNOWN | UNKNOWN | TRUE | FALSE |
| FALSE | UNKNOWN | FALSE | UNKNOWN | TRUE |
| UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN |

```sql
SELECT NULL = NULL          AS eq,          -- NULL (not true!)
       NULL IS NULL         AS is_null,     -- true
       1 IS DISTINCT FROM NULL AS distinct_, -- true   (NULL-safe <>)
       COALESCE(NULL, 'x')  AS coalesced,   -- 'x'    (first non-NULL)
       NULLIF(5, 5)         AS nullif_;     -- NULL   (turns a value into NULL)

SELECT full_name FROM customers WHERE city = NULL;        -- 0 rows, always
SELECT full_name FROM customers WHERE city IS NULL;       -- Jonas
SELECT full_name FROM customers WHERE city <> 'Toronto';  -- silently drops Jonas
SELECT full_name FROM customers WHERE city IS DISTINCT FROM 'Toronto';  -- includes Jonas
```

NULL rules to memorise:

- Aggregates (`SUM`, `AVG`, `COUNT(col)`, `MIN`, `MAX`) **ignore** NULLs; `COUNT(*)` counts rows.
- `SUM` of zero rows is **NULL**, not 0 → `COALESCE(SUM(x), 0)`.
- `NOT IN (subquery)` returns nothing if the subquery yields a NULL → use `NOT EXISTS` ([`02-joins-aggregation.md`](./02-joins-aggregation.md)).
- `UNIQUE` allows many NULLs (NULLs are not equal) unless you declare `UNIQUE NULLS NOT DISTINCT` (PG 15+).
- In `ORDER BY`, Postgres puts NULLs **last** for ASC and **first** for DESC. Override: `ORDER BY salary DESC NULLS LAST`.
- `GROUP BY` and `DISTINCT` treat all NULLs as one group.

## 7. Sorting, paging, de-duplicating

```sql
SELECT full_name, salary
FROM employees
ORDER BY salary DESC NULLS LAST, full_name   -- tiebreaker => deterministic
LIMIT 5 OFFSET 0;

SELECT DISTINCT country FROM customers ORDER BY country;

-- Postgres extension: first row per group
SELECT DISTINCT ON (department) department, full_name, salary
FROM employees
ORDER BY department, salary DESC NULLS LAST;
```

`OFFSET n` still reads and discards n rows — deep pages are slow. Keyset pagination is covered in [`05-indexes-performance.md`](./05-indexes-performance.md) and [`../06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md).

## 8. Expressions: CASE, strings, dates

```sql
SELECT title,
       base_price,
       CASE
         WHEN base_price >= 200 THEN 'premium'
         WHEN base_price >= 50  THEN 'standard'
         ELSE 'budget'
       END                                    AS tier,
       upper(category)                        AS cat,
       length(title)                          AS title_len,
       title || ' @ ' || venue_id             AS label,
       starts_at::date                        AS day,
       extract(dow FROM starts_at)            AS weekday_0_sun,
       date_trunc('month', starts_at)         AS month,
       starts_at - now()                      AS time_until
FROM events
ORDER BY starts_at;
```

`CASE` returns NULL when no branch matches and there's no `ELSE`.

## 9. Writing data

### INSERT

```sql
BEGIN;
-- multi-row insert; RETURNING gives generated values back (JDBC: getGeneratedKeys)
INSERT INTO venues (name, city, capacity)
VALUES ('Old Mill Theatre', 'Toronto', 450),
       ('Canal Stage',      'Berlin',  800)
RETURNING id, name;

-- insert from a query
INSERT INTO events (venue_id, title, category, starts_at, base_price)
SELECT id, name || ' Open House', 'CONFERENCE', '2026-11-01 10:00+00', 0
FROM venues
WHERE city = 'Berlin';
ROLLBACK;
```

Always list the columns. `INSERT INTO t VALUES (...)` without a column list breaks the day someone adds a column.

### UPDATE

```sql
BEGIN;
UPDATE events
SET    status = 'CANCELLED'
WHERE  id = 10
RETURNING id, title, status;

-- update using another table (Postgres UPDATE ... FROM)
UPDATE orders o
SET    status = 'CANCELLED'
FROM   customers c
WHERE  c.id = o.customer_id
  AND  c.country = 'CA'
  AND  o.status = 'PENDING';
ROLLBACK;
```

### DELETE and TRUNCATE

```sql
BEGIN;
DELETE FROM payments WHERE status = 'FAILED' RETURNING id;

-- delete using another table
DELETE FROM order_items oi
USING orders o
WHERE o.id = oi.order_id AND o.status = 'CANCELLED';
ROLLBACK;
```

| | `DELETE` | `TRUNCATE` |
|---|---|---|
| `WHERE` | yes | no — whole table |
| Speed on big tables | row by row, writes WAL per row | near-instant (new empty file) |
| Fires row triggers | yes | no |
| Transactional in Postgres | yes | **yes** (unlike MySQL, where it's DDL and implicitly commits) |
| Identity reset | no | `TRUNCATE … RESTART IDENTITY` |

**Safe-write habit:** write the `SELECT` with the exact `WHERE` first, check the count, then turn it into `UPDATE`/`DELETE` inside `BEGIN;` … check … `COMMIT;`.

## 10. 🔨 Break it

Predict first, then run.

1. `SELECT * FROM customers WHERE city <> 'Toronto';` — how many rows? Why is Jonas missing?
2. `SELECT SUM(salary) FROM employees WHERE department = 'Marketing';` — 0 or NULL?
3. `INSERT INTO events (venue_id, title, category, starts_at, base_price) VALUES (99, 'x', 'CONCERT', now(), 10);` — read the FK error message word for word.
4. `INSERT INTO events (venue_id, title, category, starts_at, base_price) VALUES (1, 'x', 'OPERA', now(), 10);` — which constraint name appears?
5. `UPDATE events SET base_price = base_price * 2;` inside `BEGIN`, then `SELECT` and `ROLLBACK`. Feel how easy it is to forget `WHERE`.
6. Insert a customer **without** an id after running the schema *without* the `setval` lines. What error, and why?
7. `SELECT 7 / 2, 7 / 2.0, 7::numeric / 2;` — integer division.

## 11. 🐞 Debugging tips

- Read the whole error: Postgres gives `DETAIL` and `HINT` lines and the **constraint name** — name your constraints meaningfully in real schemas.
- "column does not exist" when it obviously does → you used double quotes (`"Email"` is case-sensitive identifier) or single vs double quotes (`'text'` literal vs `"identifier"`).
- Unexpected row count → check for NULLs (`COUNT(*)` vs `COUNT(col)`) and duplicates (`COUNT(DISTINCT id)`).
- Timestamps "off by hours" → `SHOW TIME ZONE;` and `SET TIME ZONE 'UTC';`.
- Use `\x auto` and `\timing on` permanently in `~/.psqlrc`.

## 12. 🎤 Interview Q&A

<details><summary>What is the logical order of evaluation of a SELECT?</summary>

FROM/JOIN → WHERE → GROUP BY → HAVING → SELECT (incl. window functions) → DISTINCT → ORDER BY → LIMIT/OFFSET. That's why aliases work in ORDER BY but not WHERE, and why aggregates need HAVING. The physical plan may differ; the result must be as if evaluated in this order.
</details>

<details><summary>What does NULL = NULL return?</summary>

NULL (unknown), which WHERE treats as not-true. Use `IS NULL`, or `IS NOT DISTINCT FROM` for NULL-safe equality.
</details>

<details><summary>WHERE vs HAVING?</summary>

WHERE filters rows before grouping and can't reference aggregates; HAVING filters groups after aggregation. Put non-aggregate conditions in WHERE — fewer rows reach the grouping step.
</details>

<details><summary>DELETE vs TRUNCATE vs DROP?</summary>

DELETE removes selected rows (logged, triggers fire, can use WHERE). TRUNCATE empties the whole table fast, no WHERE; transactional in Postgres. DROP removes the table definition itself.
</details>

<details><summary>Why not store money as float/double?</summary>

Binary floating point can't represent most decimal fractions exactly, so sums drift (0.1 + 0.2 ≠ 0.3). Use `numeric(p,s)` in SQL and `BigDecimal` in Java (or integer minor units, e.g., cents as `bigint`).
</details>

<details><summary>timestamp vs timestamptz?</summary>

`timestamptz` normalises to UTC on input and converts to the session zone on output — it represents an instant. `timestamp` is a wall-clock reading with no zone; ambiguous across time zones. Use `timestamptz` for "when did it happen"; map to `Instant`/`OffsetDateTime` in Java.
</details>

<details><summary>What does RETURNING do and why is it useful?</summary>

Returns column values from rows affected by INSERT/UPDATE/DELETE — e.g., generated ids — in the same round-trip, avoiding a race-prone follow-up SELECT.
</details>

## ✅ Mastery checklist

- [ ] Loaded the practice DB and can navigate it with `\d`, `\dt`, `\di`
- [ ] Can recite the logical SELECT order and explain two consequences
- [ ] Can explain three-valued logic and fix a `<>`/NULL bug with `IS DISTINCT FROM`
- [ ] Always write timestamp filters as half-open ranges
- [ ] Wrote INSERT/UPDATE/DELETE with RETURNING inside a transaction and rolled back
- [ ] Solved [`sql-practice.md`](./sql-practice.md) Q1–Q20 without peeking
