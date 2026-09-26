# Redis — Résumé Tech Defense

> **Goal:** defend "Redis" with truthful past context and current Redis 7 competence: data types,
> TTL and eviction, cache-aside and invalidation, Spring Cache, rate limiting (token bucket),
> atomicity (Lua / `MULTI`), reliable queues (`BLMOVE` / Streams), pub/sub, persistence trade-offs —
> used for a different job in each project: FlowGrid's catalog cache, ForgeCI's job queue + pub/sub +
> limits, FlagForge's config-snapshot cache and propagation.
>
> **Honesty rule:** most people's past Redis use is "Spring `@Cacheable` backed by a Redis
> someone else ran". If that's yours, say so. FlowGrid, ForgeCI and FlagForge are where you designed
> keys, TTLs, a queue, a semaphore and invalidation yourself.

---

## Evidence in my projects

| Project | Redis evidence |
|---|---|
| **FlowGrid** M3 (W6) | `redis:7` in Compose; **cache-aside** for the product/SKU catalog (`catalog:sku:{id}`, TTL + explicit invalidation after commit on update); low-stock cache; **Redis-down behaviour = degrade to DB** (timeouts, health indicator, no request hangs); k6 p95 of catalog reads with/without cache (W8) |
| **LedgerX** M2 (W10) | Redis considered for the idempotency store; **Postgres chosen** (durable, same transaction as the transfer) — Redis only as an optional fast pre-check / rate limit |
| **ForgeCI** M2 (W15) | **Reliable job queue**: `LPUSH` + `BLMOVE queue processing:{worker}` with a lease key (or Streams consumer groups — decision documented); orphaned-job recovery via lease expiry |
| **ForgeCI** M3–M4 (W16–17) | **Pub/sub** fan-out of log chunks to API instances for SSE; per-project **concurrency limit** as an atomic counter/semaphore (Lua); worker heartbeats (`SET worker:{id} … EX 15`) |
| **FlagForge** M2–M4 (W21–23) | **Environment config snapshot cache** (`env:{envId}:snapshot`, invalidated on publish), p99 evaluation latency measured with/without cache; **publish → pub/sub → SSE** propagation to SDKs; **stampede protection** on snapshot rebuild (single-flight lock) |

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

Configurable: RDB snapshots (point-in-time, may lose minutes), AOF (append-only log, `appendfsync everysec` loses ≤ ~1 s), both, or none. Treat as a cache unless you configure and test durability; Postgres remains the source of truth in every project — including ForgeCI's queue, where the job row in Postgres is the record and Redis is the dispatch mechanism.
</details>

<details><summary><b>B6. What happens when memory is full?</b></summary>

Depends on `maxmemory-policy`: `noeviction` (writes error), `allkeys-lru`, `allkeys-lfu`, `volatile-lru/lfu/ttl` (only keys with TTL), random variants. For a pure cache, `allkeys-lru`/`lfu`.
</details>

## 2. Intermediate questions

<details><summary><b>I1. Explain cache-aside with your FlowGrid catalog cache.</b></summary>

Read: `GET catalog:sku:{id}` → hit: return; miss: query Postgres, `SET … EX 300`, return. Write path: when a SKU/product is updated, `DEL catalog:sku:{id}` after the DB commit. TTL bounds staleness even if an invalidation is lost. FlagForge does the same for a whole environment snapshot, invalidated on every publish.
</details>

<details><summary><b>I2. Cache invalidation pitfalls?</b></summary>

Race: reader loads old DB value, writer updates DB + deletes key, reader then writes stale value → stale until TTL. Mitigate: short TTL, delete after commit (`TransactionSynchronization.afterCommit` / `@TransactionalEventListener`), versioned keys. Never update cache *before* the DB commit.
</details>

<details><summary><b>I3. Stampede / thundering herd — what and how to prevent?</b></summary>

Hot key expires, many requests miss simultaneously and hammer the DB. Mitigate: jittered TTLs, request coalescing / single-flight lock (`SET lock NX EX 5`), early refresh, serve stale while revalidating.
</details>

<details><summary><b>I4. How does ForgeCI's per-project concurrency limit work, and why Lua?</b></summary>

Key `limit:{projectId}` holds the number of running jobs; before starting one, a Lua script does `if GET < max then INCR and return 1 else return 0` atomically across N workers; on job end (in `finally`) `DECR`; a TTL/heartbeat sweep guards against a crashed worker leaving the counter high. Read-modify-write must be atomic across workers → `EVAL`/`EVALSHA` runs it on the server. FlagForge's token-bucket rate limiter on the eval endpoint is the same pattern with a `{tokens, ts}` hash: refill `tokens = min(cap, tokens + (now - ts) * rate)`, allow if `tokens >= 1`, else `429` + `Retry-After`; TTL so idle buckets expire.
</details>

<details><summary><b>I5. Fixed window vs sliding window vs token bucket?</b></summary>

Fixed window (`INCR` + `EXPIRE` per minute): simple, allows 2× burst at boundaries. Sliding log (sorted set of timestamps): precise, more memory. Token bucket: smooth rate with controlled bursts — the right choice for FlagForge's SDK-facing eval endpoint.
</details>

<details><summary><b>I6. <code>MULTI/EXEC</code> vs Lua vs pipelining?</b></summary>

Pipelining: batch commands to save RTTs, not atomic. `MULTI/EXEC`: queued commands executed atomically, but you can't branch on intermediate results (use `WATCH` for optimistic CAS). Lua: atomic with logic.
</details>

<details><summary><b>I7. How do you implement idempotency with Redis?</b></summary>

`SET idem:{key} <fingerprint> NX EX 86400` — only the first request with that key proceeds. For LedgerX I chose the durable version instead: the idempotency row lives in Postgres in the same transaction as the transfer (key, request fingerprint, status, stored response; unique constraint), so a crash between steps can't lose it. Redis is only ever a fast pre-check.
</details>

<details><summary><b>I8b. How is ForgeCI's queue made reliable?</b></summary>

Producer `LPUSH queue jobId`. Worker `BLMOVE queue processing:{workerId} RIGHT LEFT 5` — the job moves atomically to a per-worker processing list, so a crash can't lose it in flight; the worker also sets `lease:{jobId} … EX 60` and refreshes it while running. On success it `LREM`s the id from its processing list. A sweeper finds ids whose lease expired (worker died) and moves them back to `queue` (bounded by a retry count for infra failures). Alternative: Streams + consumer groups (`XREADGROUP`, `XPENDING`, `XCLAIM`) give the same semantics with acks built in — decision and trade-offs documented in ForgeCI's ADR.
</details>

<details><summary><b>I8. How does Spring Cache map to Redis?</b></summary>

`spring-boot-starter-data-redis` + `@EnableCaching`; `@Cacheable("catalog")` stores values with a `RedisCacheManager` (configure TTL per cache and JSON serializer, not JDK serialization). `@CacheEvict` on writes. Know that self-invocation bypasses the proxy.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "How did you use Redis?"</b></summary>

Past truthfully; then one line per project: FlowGrid — cache-aside catalog + low-stock cache with invalidation after commit and degrade-to-DB when Redis is down; ForgeCI — reliable job queue (`BLMOVE` + lease), pub/sub for live logs, Lua semaphore for per-project limits; FlagForge — environment snapshot cache invalidated on publish, pub/sub → SSE propagation, stampede protection; show measured numbers (k6 p95 with/without cache, queue wait time, eval p99).
Follow-up: "What if Redis goes down?"
</details>

<details><summary><b>R2. "What if Redis goes down?"</b></summary>

FlowGrid: catalog reads degrade to Postgres (with timeouts so requests don't hang), health shows DEGRADED — tested by killing the container in the failure-engineering exercise. ForgeCI: no new jobs are dispatched; running jobs finish; the API keeps accepting webhooks into Postgres and re-enqueues when Redis returns (leases re-established). FlagForge: eval falls back to a Postgres read of the current version (slower, correct); SDKs keep serving their last snapshot (stale-if-error); the rate limiter fails open and logs. LedgerX's idempotency is unaffected — it never depended on Redis.
</details>

<details><summary><b>R3. "Why not just cache in the JVM (Caffeine)?"</b></summary>

In-process cache is faster and simpler but per-instance (inconsistent across replicas, invalidation hard) and dies on restart. Redis is shared and survives app restarts. Rate limiting across instances *requires* shared state. Two-tier caching is possible.
</details>

<details><summary><b>R4. "How do you choose a TTL?"</b></summary>

From staleness tolerance and read/write ratio: FlowGrid's catalog tolerates minutes; a FlagForge snapshot tolerates seconds (so invalidate on publish and use TTL only as a backstop); add jitter; measure hit ratio; shorter if invalidation is unreliable.
</details>

<details><summary><b>R5. "Is Redis a database?"</b></summary>

It can be (persistence, replication, Sentinel/Cluster) but in my projects it's a cache + coordination store; Postgres is the system of record. Explain what you'd lose with RDB-only persistence.
</details>

## 4. Practical tasks (doable live)

1. In `redis-cli`: set a key with TTL, check TTL, overwrite and observe TTL cleared.
2. Implement fixed-window rate limit with `INCR` + `EXPIRE` (handle the "first request" case atomically with `SET NX EX` or Lua).
3. Write the atomic semaphore (per-project limit) Lua script and the token-bucket script.
4. Add `@Cacheable` + `@CacheEvict` to a service and prove hits in logs/metrics.
5. Use `SCAN` (not `KEYS`) to list keys with a prefix.
6. In two terminals: `LPUSH`/`BLMOVE` queue with a processing list, then simulate a dead worker and recover its job.

## 5. Debugging questions

<details><summary><b>D1. Ops sees a stale SKU name for minutes after a product update.</b></summary>

Eviction ran before DB commit (race) or eviction missing on one code path; TTL too long. Move eviction to after-commit; add a test; check keys' TTL.
</details>

<details><summary><b>D2. Redis memory keeps growing.</b></summary>

Keys without TTL (worker heartbeats or limit counters never expire), processing lists left by crashed workers, unbounded lists. `INFO memory`, `redis-cli --bigkeys`, `MEMORY USAGE`, `SCAN` sample `TTL`s. Add TTLs, set `maxmemory` + policy.
</details>

<details><summary><b>D3. Latency spikes every few seconds.</b></summary>

`KEYS *` or big `SMEMBERS`/`HGETALL` blocking the single thread; `SLOWLOG GET`; fork for RDB snapshot on a large dataset. Replace with `SCAN`, smaller structures.
</details>

<details><summary><b>D4. <code>SerializationException</code> after a deploy.</b></summary>

Cached values in an old class format (JDK serialization or changed JSON shape). Version cache names/keys on schema change, use JSON serializer, or flush the cache on deploy.
</details>

## 6. Architecture questions

<details><summary><b>A1. How would you run Redis in production for ForgeCI?</b></summary>

Currently one container on the EC2 host (acceptable: Postgres holds the job rows; a Redis restart loses only in-flight dispatch, recovered by the lease sweep). Production: ElastiCache with replica + automatic failover, private subnet, SG from app/worker only, AUTH/TLS; AOF `everysec` so queued entries survive a restart — or SQS if at-least-once + visibility timeout is all you need (weighed in W19).
</details>

<details><summary><b>A2. Redis as a job queue vs Postgres table vs a real broker?</b></summary>

ForgeCI uses Redis (`BLMOVE` per-worker processing lists + leases) for low-latency dispatch, with the job row in Postgres as the record. LedgerX's outbox relay uses a Postgres table with `FOR UPDATE SKIP LOCKED` (transactional with the journal write). Redis Streams give consumer groups and acks; Kafka/SQS for high volume/durability across services.
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
- When a DB index fixes the slow query (fix the query first — ForgeCI's `log_chunks(job_id, seq)` index did more than caching would have).
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
- **React:** ForgeCI's live log and FlagForge's dashboard receive pub/sub-driven SSE events; FlowGrid's dashboard reads the cached catalog.

## 13. One small hands-on exercise

**Rate-limited eval endpoint (FlagForge-style).**

- [ ] `POST /api/eval` limited to 100 req / 10 s per SDK key with a Redis token bucket (Lua).
- [ ] Returns `429` with `Retry-After` when exhausted.
- [ ] Works correctly with 2 API instances (prove via Compose `--scale api=2` + k6 or a bash loop).
- [ ] Buckets expire after inactivity (check `TTL`).
- [ ] Testcontainers test: 101st request in a burst gets 429; kill Redis → requests still succeed (fail-open) and a WARN is logged.

## 14. Mastery checklist

- [ ] Explain cache-aside + invalidation race and my fix
- [ ] Write the token-bucket Lua script from memory
- [ ] Explain eviction policies and persistence modes
- [ ] Explain Redis-down behaviour for each use in FlowGrid, ForgeCI and FlagForge
- [ ] Explain the reliable-queue pattern (`BLMOVE` + lease) and its failure modes
- [ ] Use `redis-cli` fluently: `TTL`, `SCAN`, `INFO`, `SLOWLOG`, `MONITOR` (dev only)
- [ ] Truthful 60-second answer on past Redis use + FlowGrid / ForgeCI / FlagForge bridge
