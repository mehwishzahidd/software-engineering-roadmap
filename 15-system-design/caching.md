# Caching Strategy

> **Practical use:** P4 M2 caches the public status page in Redis so an incident (when everyone
> refreshes at once) doesn't melt Postgres.
> **Interview use:** "Where would you add a cache?", "How do you keep it consistent?", "What's a cache stampede?"

Redis *mechanics* (data types, TTL commands, eviction policies, persistence) live in
[`04-sql-databases/redis.md`](../04-sql-databases/redis.md); Spring's `@Cacheable` in
[`05-spring-boot/08-caching-scheduling.md`](../05-spring-boot/08-caching-scheduling.md). This file is
about **strategy**: what to cache, where, and how to keep it correct.

← [Fundamentals](./fundamentals.md) · Next: [Scalability](./scalability.md)

---

## 1. Why and when to cache

A cache trades **freshness** (and complexity) for **latency and load reduction**. Worth it when:

- Reads greatly outnumber writes (status pages, product pages, URL redirects).
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

### Cache-aside (lazy loading) — the default, used in P4

```
read:   app ──get──► cache ──hit──► return
                      │ miss
                      ▼
               app ──query──► DB ──► app ──set(key, value, TTL)──► cache ──► return
write:  app ──update──► DB ──then──► delete(key) in cache
```

```java
@Service
public class StatusPageService {
    private static final Duration TTL = Duration.ofSeconds(30);
    private final StringRedisTemplate redis;
    private final StatusPageQuery query;          // hits Postgres
    private final ObjectMapper json;

    public StatusPageDto get(String slug) throws JsonProcessingException {
        String key = "status:v1:" + slug;
        String cached = redis.opsForValue().get(key);
        if (cached != null) return json.readValue(cached, StatusPageDto.class);

        StatusPageDto fresh = query.load(slug);   // several SQL queries + aggregation
        redis.opsForValue().set(key, json.writeValueAsString(fresh), TTL);
        return fresh;
    }

    public void evict(String slug) { redis.delete("status:v1:" + slug); }   // after incident open/resolve
}
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

- **TTL** bounds staleness and cleans up unused keys. Choose from the business requirement: "status page may be 30 s stale". Add **jitter** (e.g. 30 s ± 5 s) so keys created together don't expire together.
- **Eviction** when memory is full: Redis `maxmemory-policy` — `allkeys-lru` (typical for pure caches), `allkeys-lfu`, `volatile-ttl`, `noeviction` (errors on write — right for Redis used as a **store**, e.g. rate-limit counters you can't lose silently).
- Watch **hit ratio** = hits / (hits + misses). Low hit ratio → wrong keys, TTL too short, or data too unique to cache.

---

## 5. Invalidation — "one of the two hard things"

| Strategy | How | Staleness | Use |
|---|---|---|---|
| TTL only | Let it expire | Up to TTL | Data where bounded staleness is fine |
| Delete on write | Update DB, then `DEL key` | Tiny window | Most cache-aside cases (P4) |
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

## 7. P4 status page — the full story (use it in interviews)

- **Problem:** the public status page aggregates the last 90 days of uptime per monitor plus current incidents — several queries over `check_results`. During an incident, traffic spikes exactly when the system is under stress.
- **Decision:** cache-aside in Redis, key `status:v1:{slug}`, TTL 30 s ± jitter; evict on incident open/resolve (after commit); rate limit the public API per IP.
- **Evidence:** k6 load test before/after: p95 latency and Postgres CPU (Week 22) — put the numbers in the README.
- **Trade-off:** up to 30 s staleness on uptime percentages; incidents appear immediately thanks to eviction.
- **Failure mode handled:** Redis down → fall back to DB with a short timeout, logged as WARN.
- **What I'd do at scale:** precompute daily uptime rollups in a table (so even a miss is cheap), put the page behind a CDN with `Cache-Control: public, max-age=30`.

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
