# Caching Strategy

> **Practical use:** FlowGrid M3 caches the product/SKU catalog and low-stock view in Redis (and must
> keep working when Redis is down); FlagForge M2 caches each environment's config snapshot in Redis and
> invalidates it on publish; FlagForge's SDK keeps an in-memory snapshot (a client-side cache).
> **Interview use:** "Where would you add a cache?", "How do you keep it consistent?", "What's a cache stampede?"

Redis *mechanics* (data types, TTL commands, eviction policies, persistence) live in
[`04-sql-databases/redis.md`](../04-sql-databases/redis.md); Spring's `@Cacheable` in
[`05-spring-boot/08-caching-scheduling.md`](../05-spring-boot/08-caching-scheduling.md). This file is
about **strategy**: what to cache, where, and how to keep it correct.

← [Fundamentals](./fundamentals.md) · Next: [Scalability](./scalability.md)

---

## 1. Why and when to cache

A cache trades **freshness** (and complexity) for **latency and load reduction**. Worth it when:

- Reads greatly outnumber writes (product catalogs, flag configs, URL redirects).
- The same data is requested repeatedly (skewed popularity — a few keys are hot).
- Computing/fetching it is expensive (aggregations, remote API calls).
- Slightly stale data is acceptable (define *how* stale: TTL).

**Don't** cache first — measure first. A missing index is cheaper to fix than a cache is to maintain.
Caches hide problems until the cache is cold (after a deploy or Redis restart).

---

## 2. Where to cache (from client to database)

| Layer | Example | Scope | Invalidation control |
|---|---|---|---|
| Browser | `Cache-Control: max-age=60`, `ETag` | One user | Weak (you can't purge a browser) |
| CDN | CloudFront caching React assets, public pages | Global, per edge | Purge API, versioned filenames |
| Reverse proxy | nginx `proxy_cache` | Per proxy | Config / TTL |
| **In-process** (app memory) | Caffeine, `ConcurrentHashMap` | One JVM instance | Easy locally; **inconsistent across instances** |
| **Distributed** | **Redis**, Memcached | All app instances | Explicit delete/TTL |
| Database | Postgres shared buffers, OS page cache | DB | Automatic |

Rules of thumb:
- Static assets with content-hashed names (`app.3f9a1c.js`, what Vite produces) → cache "forever" (`max-age=31536000, immutable`); `index.html` → `no-cache`.
- Per-user private data → don't put in shared caches (CDN) without care; `Cache-Control: private`.
- In-process + distributed together (two-level) is common at scale: tiny TTL locally, longer in Redis.

---

## 3. Caching patterns

### Cache-aside (lazy loading) — the default (FlowGrid catalog)

```
read:   app ──get──► cache ──hit──► return
                      │ miss
                      ▼
               app ──query──► DB ──► app ──set(key, value, TTL)──► cache ──► return
write:  app ──update──► DB ──then──► delete(key) in cache
```

The core of cache-aside is four lines — the design work is choosing keys, TTLs and invalidation:

```java
String cached = redis.opsForValue().get(key);                    // 1. try cache
if (cached != null) return json.readValue(cached, SkuDto.class);
SkuDto fresh = loadFromDb(skuId);                                 // 2. miss → source of truth
redis.opsForValue().set(key, json.writeValueAsString(fresh), ttl); // 3. populate with TTL
return fresh;                                                      // (write path: update DB, then DEL key after commit)
```

Spring shortcut: `@Cacheable(cacheNames = "statusPage", key = "#slug")` + `@CacheEvict` with a Redis
`CacheManager` configured with a TTL.

| Pros | Cons |
|---|---|
| Only requested data is cached; cache failure → still works (slower) | First request after miss/expiry is slow |
| Simple, app controls it | Stale window between DB write and eviction/TTL; race conditions (§5) |

### Read-through

Like cache-aside, but the **cache library** loads from the DB on miss (e.g. Caffeine `LoadingCache`,
some managed caches). Same semantics, cleaner code.

### Write-through

Every write goes to the cache **and** DB synchronously (via the cache layer or the app).

| Pros | Cons |
|---|---|
| Cache always fresh for written keys | Write latency includes both; caches data that may never be read |

### Write-back (write-behind)

Writes go to the cache; the cache/app flushes to the DB **asynchronously** later (batched).

| Pros | Cons |
|---|---|
| Very fast writes; absorbs write bursts; batches DB writes | **Data loss** if the cache dies before flush; complex; DB lags |

Use for counters/metrics where losing a few increments is acceptable (view counts, leaderboards).
Not for bookings or money.

### Write-around

Writes go only to the DB; the cache is filled on later reads (cache-aside's write path). Good when
recently written data isn't read soon.

---

## 4. TTL and eviction

- **TTL** bounds staleness and cleans up unused keys. Choose from the business requirement: "catalog data may be 60 s stale; stock levels may not be cached for reservation decisions at all". Add **jitter** (e.g. 30 s ± 5 s) so keys created together don't expire together.
- **Eviction** when memory is full: Redis `maxmemory-policy` — `allkeys-lru` (typical for pure caches), `allkeys-lfu`, `volatile-ttl`, `noeviction` (errors on write — right for Redis used as a **store**, e.g. rate-limit counters you can't lose silently).
- Watch **hit ratio** = hits / (hits + misses). Low hit ratio → wrong keys, TTL too short, or data too unique to cache.

---

## 5. Invalidation — "one of the two hard things"

| Strategy | How | Staleness | Use |
|---|---|---|---|
| TTL only | Let it expire | Up to TTL | Data where bounded staleness is fine |
| Delete on write | Update DB, then `DEL key` | Tiny window | Most cache-aside cases (FlowGrid, FlagForge on publish) |
| Update on write | Update DB, then `SET key newValue` | Tiny window, but racy | Rarely — prefer delete |
| Versioned keys | `status:v2:slug` or include `updated_at` in key | None for new key | Schema changes, bulk invalidation |
| Event-driven | DB change → event → consumers evict | Seconds | Many services caching the same data |

**Why delete, not update?** Two concurrent writers can apply cache updates in a different order than
DB updates, leaving the cache permanently wrong. Deleting converges on the next read.

**The classic race with cache-aside:**

```
T1 (reader): cache miss → reads OLD value from DB ...............→ SET cache OLD  (stale until TTL!)
T2 (writer):                     UPDATE DB NEW → DEL cache
```

Mitigations: short TTL as a safety net (always have one), delete *after* commit
(`@TransactionalEventListener(phase = AFTER_COMMIT)`), optional delayed double-delete. For
junior interviews: name the race and say "TTL bounds the damage".

**Order matters:** update the DB **then** invalidate the cache — and do it **after the transaction commits**,
otherwise a reader can repopulate the cache with pre-commit data.

---

## 6. Failure modes

| Problem | What happens | Mitigations |
|---|---|---|
| **Cache stampede** (thundering herd, dogpile) | Hot key expires; hundreds of requests miss simultaneously and all hit the DB | Per-key lock / single-flight (only one request recomputes, others wait or serve stale), early probabilistic refresh, background refresh before expiry, jittered TTL |
| **Cache penetration** | Requests for keys that don't exist always miss (attack or bug) | Cache "not found" (negative caching) with a short TTL; validate input; Bloom filter |
| **Cache avalanche** | Many keys expire together or Redis restarts → DB flooded | TTL jitter, warm-up, rate limit/circuit-break DB access, Redis replication |
| **Hot key** | One key gets a huge share of traffic, overloading one Redis node | Local in-process cache in front, key replication |
| **Cache down** | Redis unreachable | Treat cache as optional: catch exceptions, fall through to DB with timeouts (short Redis timeouts!) |
| **Big values** | Serializing MBs per request | Cache smaller projections; compress |
| **Stale after deploy** | DTO shape changed, old JSON in cache fails to deserialize | Version the key prefix (`v1` → `v2`) |

Single-flight in Redis (simplified):

```java
Boolean gotLock = redis.opsForValue().setIfAbsent("lock:" + key, "1", Duration.ofSeconds(5)); // SET NX EX
if (Boolean.TRUE.equals(gotLock)) {
    try { value = loadFromDb(); redis.opsForValue().set(key, toJson(value), ttlWithJitter()); }
    finally { redis.delete("lock:" + key); }
} else {
    Thread.sleep(50);            // brief wait, then re-read cache (or serve a stale copy)
    value = readCacheOrFallback(key);
}
```

---

## 7. Your two caching stories (and the questions to answer before building them)

### FlowGrid M3 — catalog cache with Redis-down degradation

- [ ] **What is cached?** Product/SKU catalog reads (read-heavy, rarely changed) and a low-stock summary. **Not** the `available` quantity used to decide a reservation — that decision must read the locked DB row.
- [ ] **Key design:** e.g. `catalog:v1:sku:{id}`; version prefix for DTO changes.
- [ ] **TTL:** from a stated staleness requirement; add jitter.
- [ ] **Invalidation:** delete keys after the transaction that changes a product commits.
- [ ] **Redis down:** short Redis timeouts; catch and fall back to Postgres; log WARN; a test that proves it (stop the Redis container in an integration test).
- [ ] **Evidence:** hit ratio and latency with/without cache from your k6 baseline — only numbers you measured, with the environment written down.

### FlagForge M2–M4 — config snapshot cache, publish invalidation, stampede protection

- [ ] Snapshot = the whole environment's flag config as one versioned document (`flags:{envId}:v{n}` or a pointer key to the latest version) — evaluation needs one read, not N.
- [ ] Publish → write new version to Postgres → commit → update/invalidate Redis → Redis pub/sub notifies API nodes → SSE pushes to SDKs.
- [ ] Stampede: after an invalidation, many evaluation requests miss at once → single-flight rebuild (§6).
- [ ] SDK side: in-memory snapshot, polling or streaming refresh, **stale-if-error** (keep serving the last good snapshot when the server is unreachable), defaults when no snapshot exists yet.
- [ ] Trade-off to state: propagation delay (seconds) vs load; which flags can tolerate it (almost all) and what you'd do if one couldn't.

---

## 8. Interview questions

<details><summary>Explain cache-aside. What happens on a write?</summary>

Read: check cache, on miss load from DB and populate with a TTL. Write: update the DB, then delete
the cache key (after commit) so the next read reloads fresh data. The TTL bounds any staleness from races.
</details>

<details><summary>Write-through vs write-back?</summary>

Write-through writes cache and DB synchronously — consistent, slower writes. Write-back writes the
cache and flushes to the DB later — fast, batched, but risks data loss; suitable for counters, not money.
</details>

<details><summary>What is a cache stampede and how do you prevent it?</summary>

Many requests miss the same expired hot key simultaneously and all hit the DB. Prevent with a per-key
lock/single-flight so one request recomputes, serving stale data meanwhile, refreshing before expiry,
and jittered TTLs.
</details>

<details><summary>Why delete the cache key rather than update it on writes?</summary>

Concurrent writers can update the cache out of order relative to the DB, leaving a permanently wrong
value. Deletion makes the next reader fetch the committed state.
</details>

<details><summary>In-process cache vs Redis?</summary>

In-process: nanoseconds, no network, but per-instance (inconsistent across instances) and lost on
restart. Redis: shared across instances, survives app restarts, ~sub-millisecond network hop. Often combined.
</details>
