# Redis — Data Types, TTL, Eviction, Persistence, Caching, Rate Limiting

> **Week 6** (fundamentals, cache-aside, invalidation: FlowGrid M3 catalog cache, degrade to DB when Redis is down) →
> **Weeks 15–17** (reliable queues, pub/sub, counters as limits: ForgeCI M2–M4) → **Weeks 21–23** (config snapshot cache,
> pub/sub invalidation, stampede protection: FlagForge M2/M4). This file owns Redis **mechanics**. Caching *strategy* lives in
> [`../15-system-design/caching.md`](../15-system-design/caching.md), and Spring integration in
> [`../05-spring-boot/08-caching-scheduling.md`](../05-spring-boot/08-caching-scheduling.md).
> Résumé defense: [`../17-resume-tech-defense/redis.md`](../17-resume-tech-defense/redis.md).

## 1. What Redis is (and isn't)

- An **in-memory data-structure server**: keys map to strings, hashes, lists, sets, sorted sets, streams, etc.
- Commands execute on **one main thread**, one at a time → every single command is **atomic**. (I/O threads exist for networking; command execution is still serialized.)
- Sub-millisecond latency because data lives in RAM; durability is **optional** (RDB/AOF).
- Not a replacement for Postgres: no joins, no ad-hoc queries, memory-bound, weaker durability. Use it for **caches, counters, rate limits, sessions, leaderboards, short-lived locks, lightweight queues**.
- Licensing note: Redis changed licence in 2024 (7.4+); the Linux Foundation fork **Valkey** is protocol-compatible. Commands here work on both.

```bash
docker run --name redis -p 6379:6379 -d redis:7
docker exec -it redis redis-cli
```

## 2. Keys and TTL

Key naming convention: `app:entity:id[:field]` → `fg:catalog:sku:SKU-1042`, `fci:queue:jobs`, `ff:snapshot:env:42`.

```
SET app:greeting "hello"
GET app:greeting                 -> "hello"
EXPIRE app:greeting 60           -> 1 (seconds)
TTL app:greeting                 -> 59   (-1 = no expiry, -2 = key doesn't exist)
SET app:session:42 "{...}" EX 1800        # set + expire atomically (PX for ms)
SET app:lock:job "owner-1" NX PX 30000    # only if Not eXists, with expiry
PERSIST app:greeting             # remove TTL
DEL app:greeting
EXISTS app:greeting              -> 0
SCAN 0 MATCH app:* COUNT 100     # iterate keys safely
```

- **Never run `KEYS *` in production** — it's O(N) and blocks the single thread. Use `SCAN`.
- A plain `SET` **clears** an existing TTL unless you pass `KEEPTTL`.
- Expiry is lazy (checked on access) + active (background sampling), so expired keys may occupy memory briefly.
- Add **jitter** to TTLs (`300 ± random(30)` s) so thousands of keys don't expire in the same second.

## 3. Data types with real uses

| Type | Commands | Use in this roadmap |
|---|---|---|
| **String** (bytes, ≤512 MB) | `SET GET INCR INCRBY DECR MGET SETNX GETEX` | cached JSON (catalog entry, config snapshot); counters; rate-limit windows |
| **Hash** (field → value) | `HSET HGET HGETALL HINCRBY HDEL` | token bucket state `{tokens, ts}`; small objects |
| **List** (linked, ordered) | `LPUSH RPUSH LPOP RPOP BLPOP BLMOVE LRANGE LTRIM` | job queue (ForgeCI, §10); "last 50 events" with `LPUSH` + `LTRIM 0 49` |
| **Set** (unique, unordered) | `SADD SREM SISMEMBER SMEMBERS SCARD SINTER` | live workers; dedupe of processed event ids |
| **Sorted set** (unique member + score, ordered) | `ZADD ZINCRBY ZRANGE … REV WITHSCORES ZRANGEBYSCORE ZREMRANGEBYSCORE ZCARD` | leaderboards; sliding-window rate limiting; delayed jobs by timestamp |
| **Stream** (append-only log) | `XADD XREAD XREADGROUP XACK XPENDING` | durable event/job stream with consumer groups |
| Bitmap / HyperLogLog | `SETBIT BITCOUNT` / `PFADD PFCOUNT` | daily active flags / approximate unique counts in 12 KB |

```
# counter (atomic even with 100 app instances)
INCR app:requests:total

# hash
HSET app:worker:w1 host "10.0.1.7" slots 4 started "2026-09-26T10:00:00Z"
HGET app:worker:w1 slots
HINCRBY app:worker:w1 jobs_done 1

# capped recent list
LPUSH app:recent:events "2026-09-26T10:00:00Z order 881 created"
LTRIM app:recent:events 0 49
LRANGE app:recent:events 0 9

# sorted set leaderboard: slowest endpoints (ms)
ZADD app:latency 812 /orders 95 /skus 400 /warehouses
ZRANGE app:latency 0 2 REV WITHSCORES
```

## 4. Atomicity: pipelines, MULTI/EXEC, Lua

| Tool | Guarantees |
|---|---|
| Single command | atomic |
| Pipeline | many commands in one round-trip; **not** atomic (others can interleave) |
| `MULTI … EXEC` | queued commands run back-to-back with no interleaving; **no rollback** if one fails at runtime; can't branch on intermediate results |
| `WATCH key` + `MULTI/EXEC` | optimistic check-and-set: EXEC aborts if a watched key changed |
| **Lua script** (`EVAL`, `EVALSHA`) | whole script runs atomically and can branch → the tool for rate limiters and safe lock release. Keep scripts short: they block everything else. |

## 5. Memory limits and eviction policies ⭐

```
CONFIG SET maxmemory 256mb
CONFIG SET maxmemory-policy allkeys-lru
INFO memory
```

| Policy | Evicts | Use when |
|---|---|---|
| `noeviction` (**default**) | nothing — writes fail with an OOM error | Redis is a store you can't lose from (queues, locks) |
| `allkeys-lru` | least-recently-used among **all** keys | pure cache (most common choice) |
| `allkeys-lfu` | least-frequently-used among all keys | cache with stable hot set |
| `volatile-lru` / `volatile-lfu` | LRU/LFU among keys **with a TTL** | mixed: cache keys have TTL, important keys don't |
| `volatile-ttl` | keys with the nearest expiry | — |
| `allkeys-random` / `volatile-random` | random | rarely |

LRU/LFU are **approximated** by sampling (`maxmemory-samples`), not exact. With a `volatile-*` policy and no keys having TTLs, it behaves like `noeviction`.

## 6. Persistence: RDB vs AOF ⭐

| | RDB (snapshot) | AOF (append-only file) |
|---|---|---|
| How | fork + write a point-in-time dump every N seconds/changes (`save 3600 1 300 100 60 10000`) | log every write command; replay on restart |
| Data loss on crash | everything since last snapshot (minutes) | `appendfsync everysec` (default): ≤ ~1 s; `always`: ~none but slow; `no`: OS decides |
| File size / restart speed | compact, fast restart | larger, slower replay; rewritten/compacted in background |
| Use | backups, fast restarts | durability |

Production default: **both** (AOF with RDB preamble). For a pure cache you may disable both — the source of truth is Postgres. Replication (primary → replicas) and Sentinel/Cluster provide availability; replication is **asynchronous**, so a failover can lose acknowledged writes.

## 7. Caching patterns

### Cache-aside (lazy loading) — the default ⭐

```
read(key):
  v = redis.GET(key)
  if v != null: return v                  # hit
  v = db.query(...)                       # miss
  redis.SET(key, v, EX ttl)               # populate
  return v

write(entity):
  db.update(entity)                       # 1. write the source of truth
  redis.DEL(key)                          # 2. invalidate (don't SET: avoids races writing stale data)
```

```java
// Spring Data Redis (Lettuce is Spring Boot's default client)
public ProductView product(String sku) {
    String key = "fg:catalog:sku:" + sku;
    String cached = redis.opsForValue().get(key);
    if (cached != null) return json.readValue(cached, ProductView.class);
    ProductView fresh = catalogQueries.loadFromDatabase(sku);
    Duration ttl = Duration.ofSeconds(30 + ThreadLocalRandom.current().nextInt(10));   // jitter
    redis.opsForValue().set(key, json.writeValueAsString(fresh), ttl);
    return fresh;
}
```

(`json` is a Jackson `ObjectMapper`; handle `JsonProcessingException` in real code.)

**When Redis is down** (FlowGrid M3 requires this): a cache is an optimisation, never a dependency. Wrap cache reads and
writes so that a connection error or timeout is **logged and counted, then treated as a miss**, and the request is served from
Postgres. Set short client timeouts (e.g., `spring.data.redis.timeout=200ms`), so a dead Redis costs milliseconds per request
instead of seconds. Measure what "degraded" means: the DB load and latency with Redis stopped.

### Other patterns

| Pattern | How | Trade-off |
|---|---|---|
| Read-through | cache library loads from DB on miss | same as cache-aside, logic in the cache layer |
| **Write-through** | write to cache and DB synchronously on every write | cache always warm & fresh; write latency higher; caches data nobody reads |
| Write-behind (write-back) | write to cache, flush to DB asynchronously | fast writes, **risk of data loss** — rarely right for business data |
| Refresh-ahead | refresh hot keys before they expire | smooth latency, extra load |

### Cache problems interviewers ask about

| Problem | What | Mitigation |
|---|---|---|
| **Stale data** | DB changed, cache didn't | TTL as a safety net + delete-on-write; accept bounded staleness |
| **Stampede / dogpile** | hot key expires, 1,000 requests hit the DB at once | per-key lock / single-flight, early refresh, TTL jitter, serve stale while revalidating |
| **Penetration** | requests for keys that don't exist bypass cache every time | cache negative results briefly; validate ids; Bloom filter |
| **Avalanche** | many keys expire simultaneously / Redis down | jitter, circuit breaker, fallback to DB with limits |
| **Big keys / hot keys** | one huge value or one super-hot key | split, compress, local in-process cache in front |

## 8. Distributed locks — and their caveats ⭐

```
SET app:lock:nightly-report <random-token> NX PX 30000     # acquire (only one holder, auto-expires)
```

Release **only if you still own it** (atomic compare-and-delete in Lua):

```lua
-- KEYS[1] = lock key, ARGV[1] = my token
if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
end
return 0
```

Caveats you must be able to say out loud:

1. **Expiry vs work time:** if your GC pause/slow I/O outlasts the TTL, the lock expires, someone else acquires it, and now two holders run. Extending (watchdog) reduces but doesn't remove this.
2. **Failover:** replication is async — a lock written to the primary may be lost when a replica is promoted. (Redlock tries to address this across multiple nodes; its safety is disputed — see Martin Kleppmann's critique.)
3. So Redis locks are fine for **efficiency** (avoid duplicate work) but not for **correctness**. For correctness use **fencing tokens** checked by the resource, or the database itself: unique constraints, `SELECT … FOR UPDATE`, advisory locks, `SKIP LOCKED` ([`06-transactions.md`](./06-transactions.md)).

For "only one instance runs this `@Scheduled` job", ShedLock (DB- or Redis-backed) is the pragmatic choice.

## 9. Rate limiting ⭐

### Fixed window: `INCR` + `EXPIRE`

```
INCR   app:rl:abc123:202609261000       # key per client per minute
EXPIRE app:rl:abc123:202609261000 60 NX # set TTL only if none yet (Redis 7+)
# allow if the INCR result <= limit
```

Simple and cheap. Flaws: bursts at window edges (up to 2× limit across a boundary), and if the process dies between `INCR` and `EXPIRE` on older setups the key never expires — do both in `MULTI/EXEC` or a Lua script.

### Sliding window log: sorted set

```
ZREMRANGEBYSCORE app:rl:abc123 0 <now_ms - 60000>   # drop entries older than 60 s
ZADD app:rl:abc123 <now_ms> <now_ms>-<uuid>         # log this request
ZCARD app:rl:abc123                                  # count in window
PEXPIRE app:rl:abc123 60000
```

Accurate, but memory grows with requests per window. Wrap in Lua for atomicity.

### Token bucket (Lua) — the one to build

Bucket holds up to `capacity` tokens and refills at `rate` tokens/second; each request spends one. Allows short bursts, enforces an average rate.

```lua
-- token_bucket.lua
-- KEYS[1] = bucket key; ARGV[1] = capacity; ARGV[2] = refill rate (tokens/sec); ARGV[3] = cost
local capacity = tonumber(ARGV[1])
local rate     = tonumber(ARGV[2])
local cost     = tonumber(ARGV[3])

local t   = redis.call('TIME')                         -- server clock: no client clock skew
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local state  = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1]) or capacity
local ts     = tonumber(state[2]) or now

tokens = math.min(capacity, tokens + (math.max(0, now - ts) / 1000) * rate)

local allowed = 0
if tokens >= cost then
  tokens  = tokens - cost
  allowed = 1
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / rate * 1000) + 1000)   -- idle buckets disappear
return { allowed, math.floor(tokens) }
```

```bash
# 5-token bucket, 1 token/s: first 5 calls allowed, 6th rejected
for i in 1 2 3 4 5 6; do redis-cli --eval token_bucket.lua app:tb:abc123 , 5 1 1; done
```

```java
// Spring Data Redis
private final DefaultRedisScript<List> tokenBucket =
        new DefaultRedisScript<>(Files.readString(Path.of("token_bucket.lua")), List.class);  // load once at startup

boolean tryAcquire(String apiKey) {
    List<?> r = redis.execute(tokenBucket, List.of("app:tb:" + apiKey), "20", "5", "1");
    return ((Long) r.get(0)) == 1L;
}
// In a filter: if !tryAcquire -> 429 Too Many Requests + Retry-After / RateLimit-* headers
```

Header conventions for the response side are in [`../06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md#rate-limiting).

## 10. Queues and messaging: lists, Streams, Pub/Sub

### Reliable queue with lists + `BLMOVE` (ForgeCI M2 concept)

A plain `LPUSH` / `BRPOP` queue **loses the job** if the worker crashes after popping it. The reliable pattern atomically moves
the job into a per-worker "processing" list, so an in-flight job is always somewhere visible:

```
LPUSH app:queue:jobs job:981                                   # producer
BLMOVE app:queue:jobs app:processing:w1 RIGHT LEFT 5           # worker w1: block up to 5 s, move atomically
SET app:lease:job:981 w1 EX 60                                  # lease: "w1 owns this job for 60 s", renewed by heartbeat
# ... run the job ...
LREM app:processing:w1 1 job:981                                # done: remove from processing
DEL app:lease:job:981
```

A **reaper** (scheduled task) scans processing lists. Any job whose lease has expired (the worker died) is moved back to the
main queue with an incremented attempt count. The job may therefore run **twice**, so the job must be idempotent, or the DB
must record which attempt "won". Designing the lease length, heartbeat and retry limits is ForgeCI M2/M4's job.

### Streams (`XADD`/`XREADGROUP`/`XACK`)

```
XADD app:events * type order.created id 881
XGROUP CREATE app:events notifier $ MKSTREAM
XREADGROUP GROUP notifier worker-1 COUNT 10 BLOCK 5000 STREAMS app:events >
XACK app:events notifier <entry-id>
XPENDING app:events notifier                 # delivered but not acked (in-flight / crashed consumers)
XAUTOCLAIM app:events notifier worker-2 60000 0-0   # take over entries idle > 60 s
```

Streams give you consumer groups, acks, a pending list and replay built in. Lists + `BLMOVE` are simpler, and you own the
recovery logic.

### Pub/Sub vs Streams

| | Pub/Sub (`PUBLISH`/`SUBSCRIBE`) | Streams |
|---|---|---|
| Delivery | fire-and-forget to **currently connected** subscribers | persisted log; consumers read at their own pace |
| Offline consumer | misses messages | catches up from its last id |
| Consumer groups / acks | no | yes (`XPENDING`, `XAUTOCLAIM`) |
| Use in this roadmap | ForgeCI live log fan-out to SSE connections (M3, the log itself is persisted in Postgres); FlagForge "config published" invalidation (M4) | job/event processing that must not be lost |

```
SUBSCRIBE app:build:77:logs           # API instance holding the SSE connections for build 77
PUBLISH   app:build:77:logs "chunk 42"  # worker, after persisting chunk 42 in Postgres
```

Because Pub/Sub drops messages when nobody is listening, the durable copy must live elsewhere. A reconnecting client
replays from Postgres by sequence number (ForgeCI M3). When a job must be created **in the same transaction** as other data
(LedgerX's outbox), a Postgres table with `SKIP LOCKED` ([`06-transactions.md`](./06-transactions.md)) beats Redis entirely.
SQS is considered for ForgeCI in Week 19; Kafka/RabbitMQ are awareness only.

## 11. 🔨 Break it

1. `CONFIG SET maxmemory 2mb`, `CONFIG SET maxmemory-policy noeviction`, then write keys in a loop until you get `OOM command not allowed`. Switch to `allkeys-lru` and repeat — which keys survived?
2. `SET k v EX 100`, then `SET k v2`, then `TTL k`. Explain.
3. Implement fixed-window limiting with `INCR` and then `EXPIRE` as two calls; kill the client between them (or just skip the EXPIRE). What's the key's TTL?
4. Acquire a lock with `SET … NX PX 2000`, "work" for 3 s, then `DEL` it without checking the token — while another client acquired it in between. Whose lock did you delete?
5. Run the token-bucket script 30 times quickly with capacity 5, rate 1 — then wait 3 s and try again.
6. `docker restart redis` with persistence off vs `--appendonly yes`. What survives?

## 12. 🐞 Debugging tips

- `redis-cli MONITOR` streams every command (dev only — heavy). `SLOWLOG GET 10` shows slow commands. `INFO stats` → `keyspace_hits`/`keyspace_misses` = hit ratio.
- `MEMORY USAGE key`, `redis-cli --bigkeys` find large keys.
- Serialization bugs in Spring (`\xac\xed` garbage in keys/values) = JDK serializer; configure `StringRedisSerializer` / JSON serializer.
- Cache "not working"? Log hit/miss per request, check the key string you build is identical on read and write, and check TTL units (seconds vs ms).

## 13. 🎤 Interview Q&A

<details><summary>Why is Redis fast?</summary>

Data lives in memory, data structures are purpose-built, the command loop is single-threaded (no lock contention) with efficient I/O multiplexing, and the protocol is simple. Most operations are O(1) or O(log n).
</details>

<details><summary>Redis data types and a use case for each?</summary>

String (cache, counters), Hash (object fields, bucket state), List (recent items, simple queue), Set (unique membership), Sorted set (leaderboards, sliding-window rate limits, scheduled jobs), Stream (durable event log with consumer groups).
</details>

<details><summary>What happens when Redis runs out of memory?</summary>

It depends on `maxmemory-policy`: `noeviction` (default) rejects writes; `allkeys-lru/lfu` evicts least recently/frequently used keys; `volatile-*` evicts only keys with TTLs.
</details>

<details><summary>RDB vs AOF?</summary>

RDB = periodic snapshots: compact, fast restarts, can lose minutes of writes. AOF = log of every write: fsync every second by default (≤1 s loss), larger, slower to replay. Commonly both are enabled.
</details>

<details><summary>Explain cache-aside and how you keep the cache consistent.</summary>

Read: try cache, on miss load from DB and populate with a TTL. Write: update DB, then delete the cache key. TTL bounds staleness if an invalidation is missed; deleting (not setting) avoids racing writers caching stale values.
</details>

<details><summary>What is a cache stampede and how do you prevent it?</summary>

Many concurrent misses on a hot key after it expires overload the DB. Prevent with single-flight locking per key, early/background refresh, TTL jitter, and serving stale data briefly.
</details>

<details><summary>How would you implement rate limiting with Redis?</summary>

Fixed window with INCR + EXPIRE per client per window (simple, bursty at edges); sliding window with a sorted set of timestamps; or a token bucket stored in a hash and updated atomically by a Lua script. Return 429 with Retry-After. Only claim what you built and measured (e.g., if you added a limiter to a LedgerX or FlagForge endpoint).
</details>

<details><summary>Is a Redis lock safe for correctness?</summary>

Not on its own: TTL expiry during long pauses and async replication failover can yield two holders. Use it to avoid duplicate work; for correctness rely on DB constraints/row locks or fencing tokens validated by the protected resource.
</details>

<details><summary>Pub/Sub vs Streams? How do you make a Redis list queue reliable?</summary>

Pub/Sub is fire-and-forget to connected subscribers — messages are lost if nobody's listening. Streams persist entries and support consumer groups, acknowledgements and replay. A list queue becomes reliable with `BLMOVE` into a per-worker processing list plus a lease/heartbeat, and a reaper that re-queues expired jobs. Consumers must be idempotent because a job can run twice.
</details>

## ✅ Mastery checklist

- [ ] Used every data type in `redis-cli` and can name a use case for each
- [ ] Can explain eviction policies and RDB vs AOF without notes
- [ ] FlowGrid M3: cache-aside for the catalog with TTL + jitter + invalidation on write, and the app keeps working (slower) with Redis stopped
- [ ] ForgeCI M2: reliable queue with visible in-flight jobs and lease-based recovery; can justify lists vs Streams
- [ ] FlagForge M2/M4: snapshot cache invalidated on publish via pub/sub; stampede protection in place
- [ ] Ran the token-bucket script and can explain every line
- [ ] Can explain why a Redis lock isn't a correctness guarantee
