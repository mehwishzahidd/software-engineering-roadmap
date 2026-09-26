# 05 · Indexes, EXPLAIN, and Query Optimization

> **Week 6** (FlowGrid M3: every list/search query gets `EXPLAIN`ed), **Week 8** (tuning what the k6 baseline shows is slow),
> **Week 9** (DB internals, LedgerX), **Week 25** (performance polish). Practice: [`sql-practice.md`](./sql-practice.md) P1–P10 and S1–S5.

All plans below are real output from [`practice-schema.sql`](./practice-schema.sql) (200k `page_views` rows) with
`SET max_parallel_workers_per_gather = 0;` so plans stay single-threaded and readable.

## 1. What an index is

A **B-tree index** is a separate, sorted, balanced tree of `(key → row location)` entries. Postgres stores table rows in an unordered **heap**; an index lets it find rows by key in `O(log n)` page reads instead of scanning every page.

```
                 [ root:  50k | 100k | 150k ]
                /        |          |        \
      [leaf 1..50k] [leaf ..100k] [leaf ..150k] [leaf ..200k]   <- leaves are linked, sorted
            |
     (event_id=3, viewed_at=…) -> heap page 812, item 14
```

Because leaves are **sorted and linked**, a B-tree serves: equality (`=`), ranges (`<, >, BETWEEN`), prefix `LIKE 'abc%'`, `ORDER BY` (both directions), `MIN/MAX`, and `IS NULL`.

**Cost of an index:** disk space, slower `INSERT/UPDATE/DELETE` (every index must be updated), more vacuum work, and memory competition. Indexes are a read/write trade-off, not free speed.

## 2. Index types

| Type | Good for | Example |
|---|---|---|
| **B-tree** (default) | `=`, ranges, sorting — 95% of cases | `CREATE INDEX ON orders (customer_id);` |
| **GIN** | "contains" queries on multi-valued data: `jsonb`, arrays, full-text, trigram `ILIKE '%x%'` | `CREATE INDEX ON webhooks USING gin (payload jsonb_path_ops);` |
| **GiST** | geometric, ranges, exclusion constraints, nearest-neighbour | overlapping time ranges for seat holds |
| **BRIN** | huge, naturally ordered append-only tables (time series), tiny size | `CREATE INDEX ON check_results USING brin (checked_at);` |
| Hash | equality only; rarely better than B-tree | — |

GIN awareness for interviews: it's an **inverted index** (element → list of rows), so `WHERE tags @> ARRAY['vip']` or `payload @> '{"status":"down"}'` can be indexed; writes are more expensive than B-tree. `pg_trgm` + GIN makes `ILIKE '%term%'` searchable (FlowGrid product search by name).

## 3. Composite indexes and column order ⭐

`CREATE INDEX ON page_views (event_id, viewed_at);` sorts by `event_id`, then by `viewed_at` within each `event_id` — like a phone book sorted by (last name, first name).

| Query | Uses `(event_id, viewed_at)` efficiently? |
|---|---|
| `WHERE event_id = 3` | ✅ leftmost prefix |
| `WHERE event_id = 3 AND viewed_at >= X AND viewed_at < Y` | ✅ both columns in `Index Cond` |
| `WHERE event_id = 3 ORDER BY viewed_at DESC LIMIT 10` | ✅ no sort needed |
| `WHERE viewed_at >= X` (no event_id) | ❌ not in PG 16 → Seq Scan (PG 18 adds "skip scan" for some cases) |
| `WHERE event_id IN (3, 4) AND viewed_at >= X` | ✅ |

**Rules of thumb**

1. **Equality columns first, then the range/sort column.** A range on the first column stops the second from narrowing the scan.
2. Among equality columns, order by what *other queries* also need as a prefix.
3. One well-chosen composite index often replaces two single-column ones.
4. Postgres can combine separate indexes with a **BitmapAnd**, but a matching composite index is usually much faster.

Verified on this DB — "one event, one month":

```
-- no index:              Seq Scan … Rows Removed by Filter: 197943     ~12.8 ms
-- (event_id, viewed_at): Index Only Scan … Index Cond: ((event_id = 3) AND (viewed_at >= …) AND (viewed_at < …))   ~1.2 ms
```

## 4. Covering indexes (`INCLUDE`) and index-only scans

If every column a query needs is in the index, Postgres can skip the heap: **Index Only Scan**.

```sql
CREATE INDEX pv_event_viewed_incl ON page_views (event_id, viewed_at) INCLUDE (device);
VACUUM ANALYZE page_views;

EXPLAIN (ANALYZE, BUFFERS)
SELECT device, count(*)
FROM page_views
WHERE event_id = 3 AND viewed_at >= '2026-03-01' AND viewed_at < '2026-04-01'
GROUP BY device;
```

```
HashAggregate  (actual time=0.720..0.721 rows=3 loops=1)
  Group Key: device
  ->  Index Only Scan using pv_event_viewed_incl on page_views  (cost=0.42..91.56 rows=2095 width=5) (actual time=0.029..0.354 rows=2057 loops=1)
        Index Cond: ((event_id = 3) AND (viewed_at >= …) AND (viewed_at < …))
        Heap Fetches: 0
        Buffers: shared hit=1 read=13
```

- `INCLUDE` columns are stored in leaf pages only — they don't affect ordering and can't be searched on, but make the index "cover" the query.
- `Heap Fetches: 0` depends on the **visibility map**: pages recently modified must still be checked in the heap. Autovacuum keeps this healthy.
- `Buffers: … read=13` = 13 × 8 kB pages touched. **Buffers are a better cost signal than milliseconds** (timings vary with cache warmth).

## 5. Partial and expression indexes

```sql
-- partial: index only the rows queries care about (70% of views are anonymous)
CREATE INDEX pv_logged_in ON page_views (customer_id, viewed_at) WHERE customer_id IS NOT NULL;
-- size: 1.9 MB vs ~7.9 MB for a full two-column index here

-- partial unique: "one active booking per seat" (see 04-schema-design.md)
-- CREATE UNIQUE INDEX … ON booking (event_id, seat_id) WHERE status = 'CONFIRMED';

-- expression: make lower(email) lookups indexable (and enforce case-insensitive uniqueness)
CREATE INDEX customers_lower_email_idx ON customers (lower(email));
SELECT * FROM customers WHERE lower(email) = 'ava@example.com';

DROP INDEX pv_event_viewed_incl, pv_logged_in, customers_lower_email_idx;
```

A partial index is used only when the query's `WHERE` **implies** the index predicate. An expression index is used only when the query uses the **same expression**. Expression functions must be `IMMUTABLE` (that's why `date(timestamptz)` can't be indexed — it depends on the session time zone).

## 6. When indexes don't help — or hurt

| Situation | Why |
|---|---|
| Low selectivity (`WHERE event_id = 9`, 46% of rows) | Reading most of the table via the index = random I/O + index overhead; a Seq Scan is correct |
| Tiny tables (`venues`, 4 rows) | Whole table is one page; index costs more |
| Function on the column (`WHERE date(viewed_at) = …`, `WHERE lower(email) = …` without expression index) | not *sargable* |
| Leading wildcard `LIKE '%summit'` | B-tree can't seek; use `pg_trgm` GIN |
| Type mismatch / implicit cast | e.g., comparing a `text` column to a numeric parameter |
| Wrong column order in composite index | range column first blocks the second |
| Write-heavy tables with many indexes | every insert updates every index; also blocks HOT updates |
| Unused indexes | pure cost. Find with `SELECT relname, indexrelname, idx_scan FROM pg_stat_user_indexes ORDER BY idx_scan;` |
| Stale statistics | planner misjudges selectivity → `ANALYZE` |

## 7. Reading `EXPLAIN` ⭐

```sql
EXPLAIN                      SELECT …;   -- plan + estimates only (does NOT run the query)
EXPLAIN ANALYZE              SELECT …;   -- runs it, adds actual rows/time  (careful with UPDATE/DELETE: wrap in BEGIN/ROLLBACK)
EXPLAIN (ANALYZE, BUFFERS)   SELECT …;   -- + pages read/hit — use this by default
```

Node line anatomy:

```
Index Scan using page_views_pkey on page_views  (cost=0.42..8.44 rows=1 width=29) (actual time=0.020..0.021 rows=1 loops=1)
                                                  │     │    │       │              │     │               │        │
                                     startup cost ┘     │    │       │   time to 1st row  └ time to last  │        └ executions
                                          total cost ───┘    │       └ avg row bytes         (ms, PER LOOP)└ actual rows (per loop)
                                            estimated rows ──┘
```

**How to read a plan**

1. Read **inside-out / bottom-up**: the most indented nodes run first and feed their parent.
2. Find where the time goes: compare each node's `actual time` (× `loops`) with its children's.
3. **Compare estimated `rows` vs actual `rows`** at each node. A 10×+ mismatch means bad statistics or an un-estimable predicate → bad join/scan choices above it.
4. Look for `Rows Removed by Filter` ≫ rows returned (missing index), `loops=` large on an inner scan (nested loop over many rows, or a correlated subquery), `Sort Method: external merge  Disk:` (work_mem too small / missing index for ORDER BY), `Batches: >1` on hash nodes (spilled).

### Scan nodes

| Node | Meaning | Typical when |
|---|---|---|
| **Seq Scan** | read every page of the table | no usable index, low selectivity, tiny table |
| **Index Scan** | walk index, fetch each matching heap row | few rows, or ordered output needed |
| **Index Only Scan** | answer from the index alone | covering index + visible pages |
| **Bitmap Index Scan → Bitmap Heap Scan** | collect row locations, sort by page, read each page once | medium selectivity; combining indexes (BitmapAnd/BitmapOr) |

### Join nodes

| Node | How | Best when | Cost |
|---|---|---|---|
| **Nested Loop** | for each outer row, look up inner rows (ideally via index) | outer side small, inner indexed | O(n × lookup) — disastrous if outer is big and unindexed |
| **Hash Join** | build hash table on smaller input, probe with the other | equality joins, large unsorted inputs | O(n + m), needs memory (`work_mem`) |
| **Merge Join** | walk two sorted inputs in lockstep | both inputs already sorted (indexes) on the join key; large inputs | O(n + m) + sorts if not pre-sorted |

Other nodes: `Sort` (+ `Sort Method: quicksort | top-N heapsort | external merge`), `HashAggregate` / `GroupAggregate`, `Limit`, `Materialize`, `SubPlan` (correlated subquery — look at its `loops`), `Gather` (parallel workers).

A real example with a bad estimate:

```
Aggregate  (cost=4385.50..4385.51 rows=1 width=8) (actual time=42.931..42.933 rows=1 loops=1)
  ->  Seq Scan on page_views  (cost=0.00..4383.00 rows=1000 width=0) (actual time=0.053..39.931 rows=66665 loops=1)
        Filter: (lower(device) = 'web'::text)
        Rows Removed by Filter: 133335
```

Estimated 1,000 vs actual 66,665: Postgres has statistics for `device` but not `lower(device)` and guesses 0.5%. Fix: query `device = 'web'` directly, or add an expression index (which also gets statistics after `ANALYZE`).

## 8. Statistics and the planner

- The planner is **cost-based**: it estimates the cost of alternative plans using table statistics and picks the cheapest.
- Statistics come from `ANALYZE` (run automatically by **autovacuum**): row counts, null fraction, distinct counts, most-common values, histograms. Inspect: `SELECT * FROM pg_stats WHERE tablename = 'page_views' AND attname = 'event_id';`
- After bulk loads, run `ANALYZE table;` manually. Correlated columns (city + country) can be taught with `CREATE STATISTICS`.
- Settings to know: `work_mem` (memory per sort/hash node), `shared_buffers`, `random_page_cost` (lower it on SSDs, e.g. 1.1), `effective_cache_size`.

## 9. Query optimization checklist

1. **Measure first**: find the slow query (`pg_stat_statements`, slow-query log `log_min_duration_statement`, APM). Optimise what's actually slow and frequent.
2. `EXPLAIN (ANALYZE, BUFFERS)` it. Save the plan to compare.
3. Is a big table **Seq Scanned** to return few rows? → index on the filter columns (equality first, then range/sort).
4. Is a column **wrapped in a function/cast**? → rewrite as a range or add an expression index.
5. **Estimates way off?** → `ANALYZE`; simplify predicates; extended statistics.
6. **Correlated subquery / N+1** (`loops=N`)? → rewrite as a join + `GROUP BY` or window function (or fix the ORM — [`../05-spring-boot/03-data-jpa.md`](../05-spring-boot/03-data-jpa.md)).
7. **Sort spilling** / `ORDER BY … LIMIT` slow? → index matching the `ORDER BY`.
8. **Deep `OFFSET`?** → keyset pagination: `WHERE (created_at, id) < ($1, $2) ORDER BY created_at DESC, id DESC LIMIT 20`.
9. `SELECT *` → select only needed columns (enables index-only scans, less network/JSON work).
10. Fetching too much and filtering in Java → push the filter/aggregate into SQL.
11. Re-measure. Keep the index **only if** it helps a real query more than it costs writes.

## 10. Connection pooling (concept — details in [`08-jdbc-orm.md`](./08-jdbc-orm.md))

Each Postgres connection is a separate server **process** (~several MB, expensive to create: TCP + TLS + auth). Opening one per request would dominate latency and exhaust `max_connections` (default 100). A **pool** (HikariCP in Spring Boot) keeps a small set of open connections and lends them out. Rule of thumb: pool size ≈ a small multiple of DB CPU cores, *not* one per user; `connections = ((core_count × 2) + effective_spindle_count)` is HikariCP's starting formula.

## 11. 🔨 Break it

1. Create `(viewed_at, event_id)` instead of `(event_id, viewed_at)` and run S1 from [`sql-practice.md`](./sql-practice.md). Compare `Buffers`.
2. `UPDATE page_views SET device = device WHERE id <= 50000;` then re-run the index-only scan: watch `Heap Fetches` jump; `VACUUM page_views;` and re-run.
3. Create 6 indexes on `page_views`, then time `INSERT INTO page_views (event_id, device, viewed_at) SELECT 1, 'web', now() FROM generate_series(1, 100000);` with and without them.
4. `SET enable_seqscan = off;` and query `event_id = 9` — the forced index plan is *slower*. (Experiment only; `RESET enable_seqscan;`.)
5. `SET work_mem = '64kB';` and run `SELECT * FROM page_views ORDER BY viewed_at;` with `EXPLAIN ANALYZE` — find `external merge  Disk:`.

## 12. 🐞 Debugging tips

- Plan looks fine but production is slow? Check with **production-like data volume** (this is why `page_views` has 200k rows) and a cold vs warm cache.
- Parameterised queries from Java may use a *generic plan* after 5 executions; if one parameter value is very skewed (event 9), test with `EXPLAIN` on `PREPARE`d statements.
- `EXPLAIN ANALYZE` on `UPDATE/DELETE` **executes** it — wrap in `BEGIN; … ROLLBACK;`.
- Paste big plans into a visualiser (e.g., explain.dalibo.com) — fine for learning data; never paste production data into third-party tools.

## 13. 🎤 Interview Q&A

<details><summary>How does a B-tree index speed up a query?</summary>

It keeps keys sorted in a balanced tree, so finding a key takes O(log n) page reads instead of scanning all pages; sorted, linked leaves also serve range scans and ORDER BY without sorting.
</details>

<details><summary>Why might the database not use my index?</summary>

Low selectivity (most rows match), tiny table, function/cast on the column, leading wildcard, composite index whose leading column isn't constrained, stale statistics, or a type mismatch. The planner chose what it estimated cheaper — check EXPLAIN and estimates.
</details>

<details><summary>How do you choose column order in a composite index?</summary>

Equality-filtered columns first, then the range or ORDER BY column. Consider which prefixes other queries need (leftmost-prefix rule).
</details>

<details><summary>What's a covering index / index-only scan?</summary>

An index containing every column a query needs (key columns + INCLUDE columns), letting Postgres answer from the index without visiting the table, provided the visibility map marks pages all-visible.
</details>

<details><summary>Downsides of indexes?</summary>

Storage, slower writes (each index maintained on every insert/update/delete), vacuum overhead, planner complexity, and memory pressure. Unused indexes are pure cost.
</details>

<details><summary>Explain Nested Loop vs Hash Join vs Merge Join.</summary>

Nested loop: per outer row, look up inner rows — great for small outer + indexed inner. Hash join: build hash table on the smaller input, probe with the larger — best for large equality joins. Merge join: merge two inputs sorted on the join key — good for large pre-sorted inputs.
</details>

<details><summary>EXPLAIN vs EXPLAIN ANALYZE?</summary>

EXPLAIN shows the chosen plan with estimates without executing. EXPLAIN ANALYZE executes the query and reports actual rows, time and loops per node, so you can compare estimates vs reality. Add BUFFERS to see I/O.
</details>

<details><summary>Why is OFFSET pagination slow, and what's the alternative?</summary>

OFFSET N must produce and discard N rows, so cost grows with page depth. Keyset (cursor) pagination filters on the last seen sort key (`WHERE id > :last ORDER BY id LIMIT 20`), which is an index seek with constant cost.
</details>

<details><summary>Tell me about a query you optimized.</summary>

Answer only from real work in this roadmap: e.g., a FlowGrid order-list query filtered by warehouse and status and sorted by date. Show the before/after EXPLAIN, the index you added (column order and why), and the measured change with its setup. LedgerX's `ledger_entry(account_id, id)` index behind cursor-paginated history is a second story. Never quote a number you didn't measure.
</details>

## ✅ Mastery checklist

- [ ] Can draw a B-tree and explain leftmost-prefix and column order
- [ ] Created and verified composite, covering (`INCLUDE`), partial, and expression indexes
- [ ] Can read every node in P1–P10 of [`sql-practice.md`](./sql-practice.md) aloud
- [ ] Did S1–S5 with before/after timings and `Buffers` in my notes
- [ ] FlowGrid: every list/search query `EXPLAIN`ed; indexes and their reasons recorded in `DATABASE.md`
