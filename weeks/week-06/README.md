# Week 6 — FlowGrid M3: allocation, pick/pack/ship, Redis

[← Week 5](../week-05/) · [Roadmap](../../ROADMAP.md) · [Week 7 →](../week-07/)

**Phase 1 · FlowGrid** · Milestone **M3 — Allocation + fulfillment workflow + Redis**

| Block | Hours | What it means this week |
|---|---:|---|
| Project | 28 | Deterministic multi-warehouse allocation, pick lists, picking → packing → shipment state machine, partial fulfillment, Redis cache-aside for the catalog + low-stock cache, Redis-down degradation |
| Learning | 7 | Schema design & normalization, indexes & `EXPLAIN`, CTEs/window functions; Redis fundamentals; state machines; Spring caching & scheduling; **JavaScript** core + async |
| DSA | 7 | Binary Search — **8 new problems in Python** + Day-3/7/14/30 reviews + 1 Java rep |
| Interview / review | 3 | Think-aloud #2; explain out loud |

---

## 1. Main objective

Turn reserved stock into shipped packages. Three engineering problems:

1. **Deterministic allocation.** For each order line, choose the warehouse(s) by a scoring
   function — availability, capacity, distance proxy (region/zone match), current workload,
   shipping priority — with **tie-break by id**, so the same inputs always yield the same
   allocation (testable, explainable, replayable).
2. **A fulfillment state machine** that survives reality: `ALLOCATED → PICKING → PICKED → PACKED
   → SHIPPED`, with **partial fulfillment** (ship what is ready; the rest stays allocated), pick
   lists per warehouse, packages and shipments.
3. **Redis as a cache, not a database.** Cache-aside for the product/SKU catalog with
   invalidation on write, a low-stock cache refreshed on a schedule — and a *measured* answer to
   "what happens when Redis is down" (degrade to DB, never fail the request).

The SQL side gets serious too: schema review + normalization, indexes chosen from `EXPLAIN
(ANALYZE, BUFFERS)`, CTEs and window functions for the allocation and reporting queries.
JavaScript starts this week so that Week 7's TypeScript + React is not cargo-cult.

Spec: [`18-projects/flowgrid/README.md`](../../18-projects/flowgrid/README.md) · M3 in
[`18-projects/flowgrid/milestones.md`](../../18-projects/flowgrid/milestones.md) ·
[`18-projects/flowgrid/failure-engineering.md`](../../18-projects/flowgrid/failure-engineering.md).

## 2. Prerequisites

- M2 tagged `m2`; concurrency IT green in CI; Compose runs app + Postgres.
- Node.js 20 LTS installed by Wednesday (for the JavaScript block; React tooling is Week 7).
- Read M3 in `milestones.md` in full before Monday.

## 3. Learning topics

| Topic | Subtopics | Folder file |
|---|---|---|
| Schema design & normalization | 1NF–3NF in practice, when to denormalise, surrogate vs natural keys, enums as `CHECK` vs lookup tables, `timestamptz` everywhere, review of your own M1–M2 schema | [`04-sql-databases/04-schema-design.md`](../../04-sql-databases/04-schema-design.md) |
| Indexes & `EXPLAIN` | B-tree basics, composite index column order, covering indexes, partial indexes, `EXPLAIN (ANALYZE, BUFFERS)`, seq scan vs index scan, when Postgres ignores your index, `pg_stat_user_indexes` | [`04-sql-databases/05-indexes-performance.md`](../../04-sql-databases/05-indexes-performance.md) |
| CTEs & window functions | `WITH`, recursive CTE (reading), `ROW_NUMBER()`/`RANK()` `OVER (PARTITION BY … ORDER BY …)`, running totals, "top-N per group" | [`04-sql-databases/03-advanced-queries.md`](../../04-sql-databases/03-advanced-queries.md) |
| Redis fundamentals | data types (string, hash, set, sorted set, list), TTL, `SETNX`/`SET NX EX`, pipelines, keyspace design, eviction policies, Lettuce vs Jedis, `redis-cli`, `MONITOR` | [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md) |
| Caching strategy | cache-aside vs write-through, invalidation on write, TTL as a safety net, stampede basics, what not to cache (locked inventory rows) | [`15-system-design/caching.md`](../../15-system-design/caching.md), [`05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md) |
| Spring scheduling | `@Scheduled` (fixed delay vs cron), `@EnableScheduling`, `ShedLock` awareness for multi-instance, scheduled idempotency-key cleanup + low-stock refresh | [`05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md) |
| State machines | explicit transition tables, guards, idempotent transitions, persisting history, DB `CHECK` on status | [`14-cs-fundamentals/software-engineering-practices.md`](../../14-cs-fundamentals/software-engineering-practices.md) |
| JavaScript core | `let`/`const`, functions & closures, arrays (`map`/`filter`/`reduce`), objects, destructuring, spread, modules, `this` (enough to avoid traps), equality `===` | [`07-javascript-typescript/01-javascript-core.md`](../../07-javascript-typescript/01-javascript-core.md), [`07-javascript-typescript/README.md`](../../07-javascript-typescript/README.md) |
| Async JavaScript | event loop, promises, `async`/`await`, `fetch`, error handling, `Promise.all`, AbortController | [`07-javascript-typescript/02-async-javascript.md`](../../07-javascript-typescript/02-async-javascript.md), [`07-javascript-typescript/exercises.md`](../../07-javascript-typescript/exercises.md) |

## 4. Concepts to learn

### 4.1 Deterministic allocation as a pure function

```java
record Candidate(long warehouseId, int available, int capacityHeadroom, boolean regionMatch, int openPickTasks) {}

static Comparator<Candidate> byScoreThenId(ShippingPriority p) {
    Comparator<Candidate> score = Comparator.comparingDouble((Candidate c) -> score(c, p)).reversed();
    return score.thenComparingLong(Candidate::warehouseId);     // deterministic tie-break
}
static double score(Candidate c, ShippingPriority p) {
    double s = 0;
    s += c.available() > 0 ? 40 : -1_000;          // hard requirement expressed as a huge penalty
    s += c.regionMatch() ? 30 : 0;                 // distance proxy
    s += Math.min(c.capacityHeadroom(), 20);       // capacity
    s -= Math.min(c.openPickTasks(), 20);          // workload
    s += p == ShippingPriority.EXPRESS && c.regionMatch() ? 10 : 0;
    return s;
}
```

The allocator takes a snapshot of candidates and returns a plan; a separate transactional step
applies the plan under the same row locks as M2 (`reserved → allocated`). Pure function =
table-driven unit tests with no database.

- **Interview angle:** "How did you make allocation deterministic and why does it matter?" (reproducible tests, explainable decisions, no flaky ties). "How would you make weights configurable?" (properties + a version stamp on the plan).
- **FlowGrid uses this:** `AllocationService.plan(order)`; the ADVANCED tier (polish weeks) adds split fulfillment across warehouses on the same scorer.

### 4.2 The fulfillment state machine with partial shipments

```
Order:        RESERVED → ALLOCATED → PARTIALLY_SHIPPED → SHIPPED   (CANCELLED from RESERVED/ALLOCATED only)
Allocation:   ALLOCATED → PICKING → PICKED → PACKED → SHIPPED
PickList:     OPEN → IN_PROGRESS → COMPLETED
```

Rules: a transition is a single `@Transactional` method that (1) loads the aggregate with a row
lock, (2) checks `canTransitionTo`, (3) mutates quantities (`allocated → picked → shipped` on
`inventory_level`; `on_hand` decreases at *ship*), (4) writes an audit event. "Ship what is ready"
= a shipment can be created from `PACKED` allocations while others are still `PICKING`; the order
becomes `PARTIALLY_SHIPPED` and `SHIPPED` when every line is shipped.

- **Interview angle:** "How do you handle partial fulfillment?" "Where do quantities move at each step and why does on-hand only drop at ship?" (physical truth; returns/adjustments stay simple).
- **FlowGrid uses this:** the M4 pick/pack screens drive exactly these transitions.

### 4.3 Indexes chosen from `EXPLAIN`, not from guesses

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM inventory_level WHERE warehouse_id = 3 AND sku_id = 1017;
-- Seq Scan … actual time=… rows=1  → add the composite index that also enforces uniqueness:
CREATE UNIQUE INDEX IF NOT EXISTS ux_inventory_level_wh_sku ON inventory_level (warehouse_id, sku_id);
-- partial index for the low-stock query:
CREATE INDEX ix_inventory_level_low ON inventory_level (sku_id)
  WHERE on_hand - reserved - allocated - picked < 10;
-- orders list by status, newest first:
CREATE INDEX ix_orders_status_created ON orders (status, created_at DESC);
```

Column order matters: `(status, created_at)` serves `WHERE status = ? ORDER BY created_at DESC`;
`(created_at, status)` does not. Re-run `EXPLAIN` after each index; record before/after in `PERFORMANCE.md`.

- **Interview angle:** "How do you decide what to index?" (from real query plans and access patterns; every index costs writes). "What is a covering/partial index?" "Why might Postgres ignore an index?" (small table, low selectivity, stale statistics — `ANALYZE`).
- **FlowGrid uses this:** every list/filter endpoint from M1 gets reviewed; the k6 run in M5 shows the difference.

### 4.4 CTEs and window functions for allocation and reporting

```sql
WITH cand AS (
  SELECT il.warehouse_id, il.sku_id,
         il.on_hand - il.reserved - il.allocated - il.picked AS available,
         (w.region = $1) AS region_match,
         COUNT(pl.id) FILTER (WHERE pl.status IN ('OPEN','IN_PROGRESS')) AS open_picks
  FROM inventory_level il
  JOIN warehouse w ON w.id = il.warehouse_id
  LEFT JOIN pick_list pl ON pl.warehouse_id = w.id
  WHERE il.sku_id = $2
  GROUP BY il.warehouse_id, il.sku_id, available, region_match
)
SELECT *, ROW_NUMBER() OVER (ORDER BY available DESC, region_match DESC, open_picks, warehouse_id) AS rnk
FROM cand WHERE available > 0;
```

Window functions answer "top-N per group" and running totals without self-joins:
`SUM(qty) OVER (PARTITION BY sku_id ORDER BY created_at)` for stock movement history.

- **Interview angle:** "Difference between `GROUP BY` and a window function?" (windows keep rows). "What is a CTE and is it an optimisation fence?" (in Postgres ≥ 12, not by default).
- **FlowGrid uses this:** candidate query for the allocator; "inventory by warehouse with rank" on the dashboard.

### 4.5 Redis cache-aside with invalidation and graceful degradation

```java
public Optional<SkuView> findSku(String code) {
    String key = "sku:" + code;
    try {
        String cached = redis.opsForValue().get(key);
        if (cached != null) return Optional.of(json.read(cached, SkuView.class));
    } catch (RedisConnectionFailureException e) { metrics.increment("cache.unavailable"); }   // degrade: fall through
    var view = repo.findByCode(code).map(SkuView::from);
    view.ifPresent(v -> { try { redis.opsForValue().set(key, json.write(v), Duration.ofMinutes(10)); } catch (RedisConnectionFailureException ignored) {} });
    return view;
}
// on SKU update:  redis.delete("sku:" + code);   (after the DB commit — use TransactionSynchronization.afterCommit)
```

Never cache `inventory_level` quantities that are locked/updated transactionally — stale stock is
an oversell in disguise. Cache the *catalog* (rarely changes) and a low-stock *summary* refreshed
by `@Scheduled(fixedDelay = 60_000)` into a Redis set `lowstock:skus`.

- **Interview angle:** "Cache-aside vs write-through?" "How do you invalidate?" (delete on write after commit; TTL as backstop). "What happens when Redis is down?" (degrade to DB, timeouts short, no cascading failure — prove it).
- **FlowGrid uses this:** `SkuCatalogService`; the M5 failure exercise "Redis down under load" measures the latency penalty.

### 4.6 JavaScript essentials before TypeScript

```js
const lines = [{ sku: "BOLT-M6", qty: 2 }, { sku: "NUT-M6", qty: 0 }];
const lowStock = lines.filter(l => l.qty < 1).map(({ sku }) => sku);   // destructuring + arrow fns
const total = lines.reduce((acc, l) => acc + l.qty, 0);

async function loadOrders(page = 0) {
  const res = await fetch(`/api/orders?page=${page}`, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);        // fetch does not reject on 4xx/5xx
  return res.json();
}
const [orders, skus] = await Promise.all([loadOrders(), loadSkus()]);   // parallel, fail-fast
```

Closures, `===`, `undefined` vs `null`, array methods, promises and the event loop are the
80% you need; `this` only enough to avoid it.

- **Interview angle:** "What does the event loop do?" "`Promise.all` vs `allSettled`?" "Why doesn't `fetch` throw on 404?"
- **FlowGrid uses this:** the API client in Week 7's dashboard is this code, typed.

## 5. Resources

- [PostgreSQL 16 — Indexes (ch. 11)](https://www.postgresql.org/docs/16/indexes.html), [Using EXPLAIN](https://www.postgresql.org/docs/16/using-explain.html), [Window functions](https://www.postgresql.org/docs/16/tutorial-window.html), [WITH queries](https://www.postgresql.org/docs/16/queries-with.html); [Use The Index, Luke](https://use-the-index-luke.com/) (B-tree chapters).
- [Redis docs — data types](https://redis.io/docs/latest/develop/data-types/), [key eviction](https://redis.io/docs/latest/develop/reference/eviction/); [Spring Data Redis reference](https://docs.spring.io/spring-data/redis/reference/); [Spring Boot — caching](https://docs.spring.io/spring-boot/reference/io/caching.html), [task execution & scheduling](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html).
- [MDN — JavaScript Guide](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide), [Using promises](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Using_promises), [Event loop](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Event_loop); [javascript.info](https://javascript.info/) parts 1 (chapters 2–6, 11).
- *Designing Data-Intensive Applications* ch. 3 (indexes) for depth if time allows.
- DSA: [`03-dsa/07-binary-search.md`](../../03-dsa/07-binary-search.md), NeetCode "Binary Search".

## 6. Exercises + coding assignments (inside FlowGrid)

| # | Exercise | Acceptance |
|---|---|---|
| 1 | Schema review of M1–M2: list every table's normal form, every enum stored as text + `CHECK`, every FK index; fix ≥ 3 findings in `V4__…` | `docs/DATABASE.md` "review" section + migration |
| 2 | `EXPLAIN` before/after for 5 queries (inventory search, low stock, orders by status, candidate query, audit by entity) | Table in `docs/PERFORMANCE.md` with plan excerpts and timings |
| 3 | Allocation table-driven tests: 10 scenarios (single warehouse, tie, region match beats capacity, workload penalty, express priority, no stock → plan fails) | All pass; changing a weight breaks the expected test |
| 4 | Redis-down test: stop Redis (Testcontainers `stop()` or Toxiproxy), catalog endpoint still `200`; measure p50 latency with/without Redis in a 30 s loop | `PERFORMANCE.md` numbers + `FAILURE_LOG.md` entry |
| 5 | JS: `fetch`-based mini client script (`node client.mjs`) that logs in, creates an order, polls its status until `SHIPPED` or timeout with `AbortController` | Runs against local Compose stack |

### Break it

- Delete the cache invalidation on SKU update; rename a SKU; observe the stale read for up to the TTL. Then move invalidation *before* commit and roll back the transaction — the cache is now empty but the DB unchanged (harmless) vs the opposite ordering bug (cache holds uncommitted data if you cache-on-write). Explain why "delete after commit" is the safe default.
- Cache `available` quantity for 10 s and run the Week 5 concurrency test through the cached path. Oversell. Revert; write the rule down.
- Drop `ux_inventory_level_wh_sku`, insert a duplicate (warehouse, sku) row, and watch the allocator pick an inconsistent candidate. Restore.
- Remove the `thenComparingLong(warehouseId)` tie-break; run the allocation test suite 20× with shuffled candidate order (`Collections.shuffle`). Flaky. Restore.
- Set `spring.data.redis.timeout=30s` and stop Redis. Every catalog request hangs for 30 s — a latent outage. Set it to 200 ms.

### Debug it

- A pick-list transition "succeeds" but `inventory_level.picked` does not change: the update happened on a detached entity. Find it with SQL logging + a breakpoint on `flush`; fix by loading inside the transaction.
- `@Scheduled` low-stock refresh runs twice a minute in tests: two application contexts cached by Spring's test context cache. Diagnose via thread names in logs; fix with `@MockBean` on the scheduler in slices or `spring.task.scheduling.enabled`-style property gating.
- `EXPLAIN` shows a seq scan despite your index: `ANALYZE` the table (fresh Testcontainers DB has no stats), or the predicate uses a function on the column. Fix and document.

## 7. DSA — Binary Search (8 new, in Python)

Guide: [`03-dsa/07-binary-search.md`](../../03-dsa/07-binary-search.md); templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) (`bisect_left` and the "search on answer" template).
Limits: Easy 20 min, Medium 35 min.

| Day | Problem | Difficulty | Limit | Focus |
|---|---|---|---|---|
| Mon | [704. Binary Search](https://leetcode.com/problems/binary-search/) | Easy | 15 min | `lo, hi = 0, n-1`; `mid = (lo + hi) // 2`; loop invariant |
| Mon | [35. Search Insert Position](https://leetcode.com/problems/search-insert-position/) | Easy | 15 min | lower bound = `bisect_left` |
| Tue | [278. First Bad Version](https://leetcode.com/problems/first-bad-version/) | Easy | 15 min | first `True` in a monotone predicate |
| Tue | [34. Find First and Last Position of Element in Sorted Array](https://leetcode.com/problems/find-first-and-last-position-of-element-in-sorted-array/) | Medium | 30 min | two bounds |
| Wed | [74. Search a 2D Matrix](https://leetcode.com/problems/search-a-2d-matrix/) | Medium | 25 min | index mapping `r, c = divmod(mid, cols)` |
| Thu | [875. Koko Eating Bananas](https://leetcode.com/problems/koko-eating-bananas/) | Medium | 35 min | **search on answer**; `math.ceil` |
| Thu | [153. Find Minimum in Rotated Sorted Array](https://leetcode.com/problems/find-minimum-in-rotated-sorted-array/) | Medium | 30 min | which half is sorted |
| Sat | [33. Search in Rotated Sorted Array](https://leetcode.com/problems/search-in-rotated-sorted-array/) | Medium | 35 min | same idea + target check |

**Java rep (Sat, 30 min):** re-do **875. Koko Eating Bananas** in Java (`long` arithmetic, `Math.ceilDiv` in Java 18+).

**Spaced reviews due:** Day-30 of Week 2 — Contains Duplicate, Valid Anagram (Mon), Two Sum, Group
Anagrams (Tue), Top K Frequent Elements (Wed), Product of Array Except Self, Valid Sudoku (Thu),
Longest Consecutive Sequence (Sat) → `Mastered` on a clean timed solve. Day-14 of Week 4 — Maximum
Average Subarray, Longest Substring Without Repeating (Mon), Minimum Size Subarray Sum (Tue), Max
Consecutive Ones III, Fruit Into Baskets (Wed), Longest Repeating Character Replacement, Permutation
in String (Thu), Minimum Window Substring (Sat). Day-7 of Week 5 — Valid Parentheses, Min Stack (Mon),
Queue using Stacks (Tue), Evaluate RPN, Next Greater Element I (Wed), Daily Temperatures, Car Fleet
(Thu), Sliding Window Maximum (Sat). Day-3 of this week's — Binary Search, Search Insert (Thu), First
Bad Version, First and Last Position (Fri), Search 2D Matrix (Sat), Koko, Find Minimum Rotated (Sun).

## 8. Project work — FlowGrid M3

Read M3 in [`milestones.md`](../../18-projects/flowgrid/milestones.md) first. GitHub milestone
**M3 — Allocation + fulfillment workflow + Redis**, issues labelled `m3`.

### Task checklist

- [ ] Mon: schema review + `V4__allocations_picklists_shipments.sql` (allocation, pick_list, pick_task, package, shipment; status `CHECK`s; FK indexes); `EXPLAIN` baseline for the 5 queries.
- [ ] Tue: candidate query (CTE) + `AllocationService.plan` (pure) + `apply` (transactional, ordered row locks, `reserved → allocated`); table-driven tests; `POST /api/orders/{id}/allocate`.
- [ ] Wed: pick lists per warehouse (`POST /api/warehouses/{id}/pick-lists` from allocated lines; `GET` for associates), pick tasks, `PICKING → PICKED`; role checks (`WAREHOUSE_ASSOCIATE` can pick, not allocate).
- [ ] Thu: packing (`PACKED`, packages), shipments (`SHIPPED`, on-hand decrement, partial fulfillment → `PARTIALLY_SHIPPED`), cancellation rules per state; audit on every transition; state-machine tests for every illegal edge.
- [ ] Fri: Redis in Compose + Testcontainers; `SkuCatalogService` cache-aside + after-commit invalidation; low-stock scheduled refresh into Redis; idempotency-key cleanup job; Redis-down test + timeout config.
- [ ] Sat: JS mini client; `EXPLAIN` after; Break-it/Debug-it; failure scenarios; `DESIGN_DECISIONS.md` (allocation scoring, cache scope, Redis-down policy); think-aloud #2; PR; tag `m3`.

### Acceptance criteria (summary)

- Allocation is deterministic (same input → same plan, proven by a shuffled-input test), respects availability, tie-breaks by id, and applies under row locks.
- Full workflow allocate → pick → pack → ship via API with role enforcement; partial shipments supported; every transition audited; illegal transitions → `409` `ProblemDetail`.
- Catalog reads hit Redis after first load; SKU update invalidates; Redis down → requests still succeed (measured latency documented); low-stock set refreshed on schedule.
- Indexes justified by `EXPLAIN` before/after in `PERFORMANCE.md`.

### Verification tests

- Unit: scorer/comparator table tests; state-machine transition table (all edges); partial-shipment order status derivation.
- `@DataJpaTest`: candidate CTE returns correct availability and ranking; partial index used (assert via `EXPLAIN` in a test is optional — record manually).
- IT (Testcontainers Postgres + Redis): allocate-then-ship end to end; cache hit/miss counters; invalidation after update; Redis stopped mid-test → `200` from DB; concurrent allocate of the last unit → exactly one allocation (reuse Week 5's harness).
- Scheduled: low-stock refresh populates the Redis set (use `fixedDelay` override in test properties).

### Failure-engineering scenarios this week

From [`failure-engineering.md`](../../18-projects/flowgrid/failure-engineering.md): **Redis
unavailable** (start-up and mid-flight), **stale cache after SKU update**, **missing index under
load** (compare `EXPLAIN` + timing), **invalid state transition from a stale client** (two
associates pick the same task), **scheduled job overlapping** (long-running refresh). Log each in `docs/FAILURE_LOG.md`.

### PR expectations

≈ 4 PRs (schema+allocation; pick/pack/ship; Redis+scheduling; docs+client). Rebase, self-review, squash, CI green (now with Postgres **and** Redis Testcontainers).

## 9. Git activity

```bash
git switch -c feat/m3-allocation
git commit -m "feat(allocation): deterministic scorer with id tie-break"
git commit -m "perf(db): composite + partial indexes from EXPLAIN review"
git commit -m "feat(cache): sku catalog cache-aside with after-commit invalidation"
git commit -m "test(cache): redis down degrades to db"
git tag -a m3 -m "FlowGrid M3: allocation, fulfillment workflow, Redis" && git push origin m3
```

- Keep `docs/PERFORMANCE.md` changes in the same PR as the index migration so reviewers see the evidence.
- Use `git bisect` once this week on purpose: plant a regression in a scorer weight three commits back, then find it with `git bisect run mvn -q -Dtest=AllocationScorerTest test`.

## 10. Interview preparation

**Think-aloud #2** (Sat, 60 min): one unseen Medium (suggested: [981. Time Based Key-Value Store](https://leetcode.com/problems/time-based-key-value-store/) — binary search on timestamps, in Python), recorded, scored with [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md), logged in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md). Method: [`16-interview-prep/coding-interview-method.md`](../../16-interview-prep/coding-interview-method.md).

Questions to answer out loud:

Track A: 1. State the loop invariant of your binary search; why does `lo <= hi` vs `lo < hi` change the exit condition? 2. What makes a problem a "search on answer" and how do you find the bounds? 3. Why `(lo + hi) // 2` is fine in Python but `lo + (hi - lo) / 2` matters in Java.
Track B: 4. Walk through allocation for a 2-line order across 3 warehouses with your scorer. 5. Why do you not cache inventory quantities? 6. What happens in your system when Redis dies — and how do you know (metrics)? 7. Read this `EXPLAIN` output (bring one) — what would you change? 8. `GROUP BY` vs window function. 9. What does the event loop do when `await` is hit?

Plus M3 questions from [`18-projects/flowgrid/interview-questions.md`](../../18-projects/flowgrid/interview-questions.md), one per day.

## 11. Revision work

- Re-explain Week 5's locking choice, then say how allocation reuses the same row locks (`reserved → allocated`).
- Redo the `HashMap` internals drawing (Week 2), then contrast with a Redis hash and a B-tree index in one paragraph each.
- Redo one Week 4 sliding-window problem you marked `Needs Review`.

## 12. Daily plan

| Day | Blocks |
|---|---|
| **Mon** (8 h) | Learning 2: schema design, indexes, `EXPLAIN` · Project 4.5: schema review, `V4`, `EXPLAIN` baseline, allocation model · DSA 1.5: 704, 35 + Day-30 (Contains Duplicate, Valid Anagram) + Day-14 (Max Avg, Longest Substring) + Day-7 (Valid Parentheses, Min Stack) |
| **Tue** (8 h) | Project 5: candidate CTE, `plan` + `apply`, table tests, allocate endpoint · DSA 2: 278, 34 + Day-30 (Two Sum, Group Anagrams) + Day-14 (Min Size Subarray) + Day-7 (Queue via Stacks) · Interview 1: M3 question; explain the scorer out loud |
| **Wed** (8 h) | Learning 2: CTEs/window functions; JavaScript core · Project 4.5: pick lists, pick tasks, role checks · DSA 1.5: 74 + Day-30 (Top K) + Day-14 (Ones III, Fruit) + Day-7 (RPN, Next Greater) |
| **Thu** (8 h) | Project 5: packing, shipments, partial fulfillment, state-machine tests, audit · DSA 2: 875, 153 + Day-30 (Product Except Self, Valid Sudoku) + Day-14 (Char Replacement, Permutation) + Day-7 (Daily Temps, Car Fleet) + Day-3 (704, 35) · Docs 1: `DESIGN_DECISIONS.md` allocation + state machine |
| **Fri** (5 h) | Learning 2: Redis fundamentals; Spring caching/scheduling; async JS · Project 2: Redis in Compose/Testcontainers, cache-aside, scheduled refresh, Redis-down test · DSA 1: Day-3 (278, 34) · Retro 0.5 |
| **Sat** (6 h) | Project 3: JS client, `EXPLAIN` after, Break-it/Debug-it, failure scenarios, `git bisect`, PR, tag `m3` · DSA 1: 33 + Day-30 (Longest Consecutive) + Day-14 (Min Window) + Day-7 (Sliding Window Max) + Day-3 (74) + **Java rep** (875) · Interview 2: **think-aloud #2** |
| **Sun** (2–3 h) | End-of-week test (§13) · Day-3 (875, 153) · trackers · plan Week 7 (install Vite/React toolchain) · rest |

## 13. End-of-week test (100 min, timed)

**Part A — DSA in Python (40 min).** [153. Find Minimum in Rotated Sorted Array](https://leetcode.com/problems/find-minimum-in-rotated-sorted-array/)
and [875. Koko Eating Bananas](https://leetcode.com/problems/koko-eating-bananas/) from blank; complexities and the invariant out loud.

**Part B — Concepts (25 min).**

<details>
<summary>1. You have an index on <code>(created_at, status)</code>. Does <code>WHERE status = 'OPEN' ORDER BY created_at DESC</code> use it well?</summary>

Poorly: the leading column is `created_at`, so the planner cannot seek on `status`; it would scan
the whole index or the table and filter. `(status, created_at DESC)` serves this query directly.
</details>

<details>
<summary>2. Cache-aside: why delete the key <em>after</em> commit rather than before, and why not write the new value into the cache instead?</summary>

Deleting before commit leaves a window where a reader repopulates the cache with the *old* DB value
before the transaction commits. Writing the new value risks caching data that later rolls back or
races with another writer; delete-after-commit + TTL is simplest and safe.
</details>

<details>
<summary>3. Why is <code>ROW_NUMBER() OVER (PARTITION BY sku_id ORDER BY score DESC)</code> not the same as <code>GROUP BY sku_id</code>?</summary>

Window functions compute per-row values within a partition and keep every row; `GROUP BY` collapses
rows to one per group. "Top-N per group" needs the window and an outer `WHERE rn <= N`.
</details>

<details>
<summary>4. Redis is down and your timeout is 30 s. What does the user see, and what is the fix?</summary>

Every catalog request hangs ~30 s before degrading — effectively an outage, and thread/connection
pools fill. Fix: short connect/command timeouts (~100–300 ms), catch connection failures, fall through
to the DB, emit a metric, consider a circuit breaker.
</details>

<details>
<summary>5. Binary search on answer for Koko: what are <code>lo</code>/<code>hi</code> and what is the monotone predicate?</summary>

`lo = 1`, `hi = max(piles)`; predicate `can_finish(k) = sum(ceil(p / k)) <= h`, which is false→true
monotone in k. Find the first `k` where it is true.
</details>

<details>
<summary>6. JavaScript: <code>const p = fetch(url); console.log("a"); await p; console.log("b")</code> — order, and why doesn't a 500 throw?</summary>

`a` then `b`; `fetch` starts a request and returns a promise immediately; `await` yields to the event
loop until it resolves. `fetch` only rejects on network failure; HTTP errors resolve with `ok === false`.
</details>

**Part C — Practical (30 min).** Write the SQL (CTE + window) that returns, for each SKU, the two
warehouses with the highest `available`, then the JPA/`@Query` or `JdbcTemplate` call and a
`@DataJpaTest` for it with three warehouses seeded. Pass = correct rows, test green.

**Part D — Explain out loud (5 min).** "An order for 3 SKUs arrives. Walk me through allocation
to shipment, naming each state, each lock and each cache touch."

## 14. Mastery checklist

- [ ] I can review a schema for normalization and constraint gaps and fix it with a migration.
- [ ] I choose indexes from `EXPLAIN (ANALYZE, BUFFERS)` and can explain composite order, partial and covering indexes.
- [ ] I write CTEs and window functions for ranking/top-N/running totals.
- [ ] Allocation is a deterministic pure function with table-driven tests; the state machine rejects every illegal edge.
- [ ] I can implement cache-aside with after-commit invalidation, explain what not to cache, and prove Redis-down degradation.
- [ ] I can write modern JavaScript with promises/`async`/`fetch` and explain the event loop.
- [ ] Binary-search problems: 8 re-solvable in Python within limits; Java rep done; think-aloud #2 scored.

## 15. Expected deliverables

- `flowgrid`: milestone M3 closed, ≥ 4 PRs, tag `m3`, Redis in Compose + CI, `PERFORMANCE.md` (`EXPLAIN` before/after; Redis on/off latency), `DESIGN_DECISIONS.md` (allocation, cache scope, Redis-down), `FAILURE_LOG.md` (+5), `docs/API.md` updated with workflow endpoints, `scripts/client.mjs`.
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 46 problems, 6 Java reps, Week 2 set `Mastered` where earned.
- [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md): think-aloud #2.
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md), [`trackers/project-tracker.md`](../../trackers/project-tracker.md) (M3 done), [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md) (SQL advanced, Redis, JavaScript).

## 16. If you're behind / stretch

**Behind:** preserve allocation (single-warehouse, deterministic) + allocate → pick → ship with
audit + catalog cache-aside with Redis-down test. Cut: packages as a separate entity (ship
directly from `PICKED`), low-stock scheduled cache (keep the DB query), the JS client (Week 7 will
build the real one). DSA: drop 33 and 74; keep all reviews.

**Stretch:** split fulfillment across warehouses for one line (ADVANCED tier — only if M3 is
fully green); `SKIP LOCKED` pick-task claiming for associates; Redis `MONITOR` session recorded
in `PERFORMANCE.md`; [981. Time Based Key-Value Store](https://leetcode.com/problems/time-based-key-value-store/) and
[1011. Capacity To Ship Packages Within D Days](https://leetcode.com/problems/capacity-to-ship-packages-within-d-days/) in Python.
