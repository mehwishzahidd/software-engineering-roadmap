# Redis — Résumé Tech Defense

> **Goal:** defend "Redis" with truthful past context and current Redis 7 competence: data types,
> TTL and eviction, cache-aside and invalidation, Spring Cache, rate limiting (token bucket),
> atomicity (Lua / `MULTI`), persistence trade-offs — all used in PulseWatch.
>
> **Honesty rule:** most people's past Redis use is "Spring `@Cacheable` backed by a Redis
> someone else ran". If that's yours, say so. PulseWatch is where you designed keys, TTLs and
> a rate limiter yourself.

---

## Evidence in my projects

| Project | Redis evidence |
|---|---|
| **P4 PulseWatch** M1 (W19) | `redis:7` service in Compose |
| **P4** M2 (W20) | **Cache-aside** for public status page (`status:page:{slug}`, TTL 30 s, evicted when an incident opens/closes); **token-bucket rate limiter** on public API (`rl:{apiKey or ip}` hash with `tokens`/`ts`, atomic Lua script); idempotency keys for alert jobs (`SET alert:{incidentId}:{channel} 1 NX EX 86400`) |
| **P4** M4 (W22) | k6 load test shows p95 of status endpoint before/after cache; Micrometer cache hit/miss metrics |

## Where to learn it in this repo

- [`../04-sql-databases/redis.md`](../04-sql-databases/redis.md) — mechanics, data types, commands
- [`../05-spring-boot/08-caching-scheduling.md`](../05-spring-boot/08-caching-scheduling.md) — Spring Cache, `@Scheduled`
- [`../15-system-design/caching.md`](../15-system-design/caching.md) — caching strategies
- Related: [`docker.md`](./docker.md)

---

## 1. Beginner questions

<details><summary><b>B1. What is Redis?</b></summary>

An in-memory key-value data-structure server. Commands execute on a single main thread (I/O threads optional in 6+), so each command is atomic. Used for caching, rate limiting, sessions, locks, counters, leaderboards, queues/streams, pub/sub.
</details>

<details><summary><b>B2. Main data types and one use each.</b></summary>

String (cached JSON, counters with `INCR`), Hash (object fields — rate-limit bucket), List (simple queue), Set (unique members), Sorted Set (leaderboard, sliding-window rate limit by timestamp), Stream (append-only log with consumer groups), plus bitmaps/HyperLogLog/geo.
</details>

<details><summary><b>B3. How does TTL work?</b></summary>

`SET k v EX 30` or `EXPIRE k 30`; `TTL k` shows remaining seconds (-1 no expiry, -2 missing). Expiry is lazy on access plus periodic sampling. Overwriting with plain `SET` clears the TTL unless you use `KEEPTTL`.
</details>

<details><summary><b>B4. Why is Redis fast?</b></summary>

Data in RAM, efficient data structures, single-threaded execution (no lock contention), event-loop network I/O, simple protocol (RESP). Typical sub-millisecond latency; network RTT usually dominates.
</details>

<details><summary><b>B5. Is Redis durable?</b></summary>

Configurable: RDB snapshots (point-in-time, may lose minutes), AOF (append-only log, `appendfsync everysec` loses ≤ ~1 s), both, or none. Treat as a cache unless you configure and test durability; Postgres remains the source of truth in PulseWatch.
</details>

<details><summary><b>B6. What happens when memory is full?</b></summary>

Depends on `maxmemory-policy`: `noeviction` (writes error), `allkeys-lru`, `allkeys-lfu`, `volatile-lru/lfu/ttl` (only keys with TTL), random variants. For a pure cache, `allkeys-lru`/`lfu`.
</details>

## 2. Intermediate questions

<details><summary><b>I1. Explain cache-aside with your status page.</b></summary>

Read: `GET status:page:{slug}` → hit: return; miss: query Postgres, `SET … EX 30`, return. Write path: when an incident opens/closes, `DEL status:page:{slug}` after the DB commit. TTL bounds staleness even if an invalidation is lost.
</details>

<details><summary><b>I2. Cache invalidation pitfalls?</b></summary>

Race: reader loads old DB value, writer updates DB + deletes key, reader then writes stale value → stale until TTL. Mitigate: short TTL, delete after commit (`TransactionSynchronization.afterCommit` / `@TransactionalEventListener`), versioned keys. Never update cache *before* the DB commit.
</details>

<details><summary><b>I3. Stampede / thundering herd — what and how to prevent?</b></summary>

Hot key expires, many requests miss simultaneously and hammer the DB. Mitigate: jittered TTLs, request coalescing / single-flight lock (`SET lock NX EX 5`), early refresh, serve stale while revalidating.
</details>

<details><summary><b>I4. How does your token-bucket rate limiter work, and why Lua?</b></summary>

Per client hash `{tokens, ts}`; on request: refill `tokens = min(cap, tokens + (now - ts) * rate)`, if `tokens >= 1` decrement and allow else deny with `429` + `Retry-After`. Read-modify-write must be atomic across API instances → a Lua script via `EVAL`/`EVALSHA` runs atomically on the server. Set a TTL so idle buckets expire.
</details>

<details><summary><b>I5. Fixed window vs sliding window vs token bucket?</b></summary>

Fixed window (`INCR` + `EXPIRE` per minute): simple, allows 2× burst at boundaries. Sliding log (sorted set of timestamps): precise, more memory. Token bucket: smooth rate with controlled bursts — chosen for PulseWatch's public API.
</details>

<details><summary><b>I6. <code>MULTI/EXEC</code> vs Lua vs pipelining?</b></summary>

Pipelining: batch commands to save RTTs, not atomic. `MULTI/EXEC`: queued commands executed atomically, but you can't branch on intermediate results (use `WATCH` for optimistic CAS). Lua: atomic with logic.
</details>

<details><summary><b>I7. How do you implement idempotency with Redis?</b></summary>

`SET alert:{incidentId}:{channel} 1 NX EX 86400` — only the first worker to set it sends the alert. For stronger guarantees, a unique constraint in Postgres (`alert_deliveries(incident_id, channel)`) is the durable record; Redis is the fast pre-check.
</details>

<details><summary><b>I8. How does Spring Cache map to Redis?</b></summary>

`spring-boot-starter-data-redis` + `@EnableCaching`; `@Cacheable("statusPage")` stores values with a `RedisCacheManager` (configure TTL per cache and JSON serializer, not JDK serialization). `@CacheEvict` on writes. Know that self-invocation bypasses the proxy.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "How did you use Redis?"</b></summary>

Past truthfully; then PulseWatch: cache-aside with TTL + explicit eviction, token-bucket rate limiting with Lua, idempotency keys for alert jobs; show k6 numbers before/after.
Follow-up: "What if Redis goes down?"
</details>

<details><summary><b>R2. "What if Redis goes down?"</b></summary>

Cache: fall back to Postgres (with timeouts + circuit breaker so requests don't hang). Rate limiter: fail-open (availability) vs fail-closed (protection) — PulseWatch fails open for authenticated users and logs/alerts. Idempotency: DB unique constraint still prevents duplicate alerts.
</details>

<details><summary><b>R3. "Why not just cache in the JVM (Caffeine)?"</b></summary>

In-process cache is faster and simpler but per-instance (inconsistent across replicas, invalidation hard) and dies on restart. Redis is shared and survives app restarts. Rate limiting across instances *requires* shared state. Two-tier caching is possible.
</details>

<details><summary><b>R4. "How do you choose a TTL?"</b></summary>

From staleness tolerance and read/write ratio: status page tolerates 30 s; add jitter; measure hit ratio; shorter if invalidation is unreliable.
</details>

<details><summary><b>R5. "Is Redis a database?"</b></summary>

It can be (persistence, replication, Sentinel/Cluster) but in my projects it's a cache + coordination store; Postgres is the system of record. Explain what you'd lose with RDB-only persistence.
</details>

## 4. Practical tasks (doable live)

1. In `redis-cli`: set a key with TTL, check TTL, overwrite and observe TTL cleared.
2. Implement fixed-window rate limit with `INCR` + `EXPIRE` (handle the "first request" case atomically with `SET NX EX` or Lua).
3. Write the token-bucket Lua script.
4. Add `@Cacheable` + `@CacheEvict` to a service and prove hits in logs/metrics.
5. Use `SCAN` (not `KEYS`) to list keys with a prefix.

## 5. Debugging questions

<details><summary><b>D1. Users see stale status for minutes after an incident resolves.</b></summary>

Eviction ran before DB commit (race) or eviction missing on one code path; TTL too long. Move eviction to after-commit; add a test; check keys' TTL.
</details>

<details><summary><b>D2. Redis memory keeps growing.</b></summary>

Keys without TTL (rate-limit buckets never expire), unbounded lists. `INFO memory`, `redis-cli --bigkeys`, `MEMORY USAGE`, `SCAN` sample `TTL`s. Add TTLs, set `maxmemory` + policy.
</details>

<details><summary><b>D3. Latency spikes every few seconds.</b></summary>

`KEYS *` or big `SMEMBERS`/`HGETALL` blocking the single thread; `SLOWLOG GET`; fork for RDB snapshot on a large dataset. Replace with `SCAN`, smaller structures.
</details>

<details><summary><b>D4. <code>SerializationException</code> after a deploy.</b></summary>

Cached values in an old class format (JDK serialization or changed JSON shape). Version cache names/keys on schema change, use JSON serializer, or flush the cache on deploy.
</details>

## 6. Architecture questions

<details><summary><b>A1. How would you run Redis in production for PulseWatch?</b></summary>

Currently one container on the EC2 host (acceptable: cache + limiter, Postgres is source of truth). Production: ElastiCache with replica + automatic failover, in a private subnet, SG from app only, AUTH/TLS.
</details>

<details><summary><b>A2. Redis as a job queue vs Postgres table vs a real broker?</b></summary>

PulseWatch alerts use a Postgres `alert_jobs` table with `FOR UPDATE SKIP LOCKED` (transactional with incident creation). Redis Streams give consumer groups and speed but less transactional coupling. Kafka/SQS for high volume/durability across services.
</details>

## 7. Common mistakes

- Using `KEYS` in production.
- No TTLs; no `maxmemory`.
- Caching before DB commit; missing invalidation paths.
- Non-atomic read-modify-write for counters/limiters.
- Treating Redis as durable without configuring persistence.
- JDK serialization of cached objects.

## 8. Terminology I must know

| Term | Meaning |
|---|---|
| Cache-aside | App reads cache, loads from DB on miss |
| Write-through / write-behind | Write cache+DB together / cache first, DB later |
| TTL | Time to live |
| Eviction policy | What to drop when `maxmemory` is hit |
| Hit ratio | hits / (hits + misses) |
| Stampede | Many simultaneous misses on a hot key |
| Token bucket | Rate limit with refill rate + capacity |
| Lua script | Server-side atomic logic |
| RDB / AOF | Snapshot / append-only persistence |
| Sentinel / Cluster | HA failover / sharding |
| Pipelining | Batching commands to save RTT |
| `SET NX EX` | Set if absent with expiry (locks, idempotency) |

## 9. When to use it

- Hot reads tolerant of short staleness.
- Shared counters, rate limiting, short-lived locks, idempotency pre-checks, sessions.

## 10. When NOT to use it

- As the only copy of important data.
- When a DB index fixes the slow query (fix the query first — PulseWatch's composite index did more than caching for the dashboard).
- Single-instance app where Caffeine suffices.

## 11. Trade-offs

| Gain | Cost |
|---|---|
| Sub-ms reads, less DB load | Staleness, invalidation complexity |
| Shared state across instances | Another service to run/monitor |
| Atomic primitives | Memory cost; data-loss risk |

## 12. How it interacts with the rest of my stack

- **Spring Boot:** Spring Data Redis (Lettuce), `RedisTemplate`/`StringRedisTemplate`, Spring Cache, Actuator Redis health.
- **Postgres:** source of truth; eviction after commit.
- **Docker:** `redis:7-alpine` in Compose, Testcontainers `GenericContainer("redis:7")` in tests.
- **AWS:** container on EC2 now; ElastiCache later.
- **CI:** integration tests use Testcontainers Redis.
- **React:** status dashboard benefits indirectly (fast cached endpoint).

## 13. One small hands-on exercise

**Rate-limited endpoint.**

- [ ] `GET /api/public/status/{slug}` limited to 10 req / 10 s per IP with a Redis token bucket (Lua).
- [ ] Returns `429` with `Retry-After` when exhausted.
- [ ] Works correctly with 2 API instances (prove via Compose `--scale api=2` + k6 or a bash loop).
- [ ] Buckets expire after inactivity (check `TTL`).
- [ ] Testcontainers test: 11th request in a burst gets 429.

## 14. Mastery checklist

- [ ] Explain cache-aside + invalidation race and my fix
- [ ] Write the token-bucket Lua script from memory
- [ ] Explain eviction policies and persistence modes
- [ ] Explain Redis-down behaviour for each PulseWatch use
- [ ] Use `redis-cli` fluently: `TTL`, `SCAN`, `INFO`, `SLOWLOG`, `MONITOR` (dev only)
- [ ] Truthful 60-second answer on past Redis use + PulseWatch bridge
