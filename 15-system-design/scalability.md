# Scalability, Reliability & Consistency Basics

> **Practical use:** explain how each of your projects would grow beyond one EC2 instance — and which
> parts (stateless JWT auth, idempotency keys, row locking, Redis queues with leases, cached snapshots)
> were already built with that in mind.
> **Interview use:** vertical vs horizontal, stateless, load balancing, replicas, sharding, queues, CAP, idempotency, rate limiting, CDN.

← [Caching](./caching.md) · Next: [Junior design problems](./junior-design-problems.md)

---

## 1. Vertical vs horizontal scaling

| | Vertical (scale up) | Horizontal (scale out) |
|---|---|---|
| How | Bigger machine: more CPU/RAM/IOPS | More machines behind a load balancer |
| Pros | Zero code change; no distributed-systems problems | Near-linear capacity; redundancy (no single point of failure); scale in/out with load |
| Cons | Hard ceiling; expensive at the top; still one point of failure; resize may need downtime | Requires stateless design, LB, coordination; more moving parts |
| Typical for | Databases (first), early-stage apps | Stateless app/web tiers, workers |

Honest default: **scale vertically until it hurts, while designing so you *can* scale horizontally.**
Postgres on a large instance handles far more than most junior-interview scenarios need.

---

## 2. Stateless services

A service is **stateless** if any instance can handle any request because no request-relevant state
lives in instance memory between requests.

| State | Where it lives instead |
|---|---|
| User session / login | **JWT** in the request (all four projects) or a shared session store (Redis) |
| Uploaded files | Object storage (S3), not local disk |
| Caches | Redis (shared) — in-process caches are fine only as disposable optimizations |
| Rate-limit / concurrency counters | Redis (ForgeCI per-project limits) — per-instance counters would multiply the real limit by N |
| Scheduled job "who runs it" | Leader election / DB locks (ShedLock) / a single worker deployment |
| Business data | Database |

Stateless → you can add instances, kill instances, deploy with rolling restarts, and the LB can send
any request anywhere. **Sticky sessions** are a workaround for stateful apps; avoid when possible.

---

## 3. Load balancers

- Distribute requests across healthy instances; remove unhealthy ones via **health checks** (`/actuator/health`, liveness vs readiness).
- Algorithms: round robin, least connections, weighted, consistent hashing (for cache affinity).
- L4 vs L7 and reverse proxies: [`14-cs-fundamentals/networking.md` §10](../14-cs-fundamentals/networking.md#10-load-balancers-l4-vs-l7).
- The LB itself must not be a single point of failure — managed LBs (ALB) are redundant across AZs.
- **Graceful shutdown**: LB stops routing (deregistration delay), app finishes in-flight requests (`server.shutdown=graceful`), then exits.

---

## 4. Database scaling path

Most systems' bottleneck is the database. Escalate in this order:

```
1. Fix queries & indexes (EXPLAIN)             ← cheapest, biggest wins
2. Connection pooling, batch writes
3. Cache hot reads (Redis)                     ← caching.md
4. Vertical scale the DB
5. Read replicas for read-heavy traffic
6. Partition big tables (by time) / archive old data
7. Split by function (separate DB per service/domain)
8. Shard (horizontal partitioning across DB servers)   ← last resort
```

### Replication & read replicas

- Primary takes all **writes**, streams WAL to replicas; replicas serve **reads**.
- Asynchronous → **replication lag** → read-your-own-writes issues. Route a user's reads to the primary for a short time after they write, or read critical data from the primary always.
- Replicas also serve analytics/reporting so heavy queries don't hurt OLTP.
- Failover: promote a replica; with async replication, the last few transactions may be lost.
- Details: [`14-cs-fundamentals/database-internals.md` §7](../14-cs-fundamentals/database-internals.md#7-replication-basics).

### Sharding (awareness)

Split rows across multiple independent databases by a **shard key**.

| Approach | How | Issue |
|---|---|---|
| Range | `user_id` 1–1M → shard A, … | Hot ranges (newest users are most active) |
| Hash | `hash(user_id) % N` | Resharding moves almost everything when N changes → **consistent hashing** reduces movement |
| Directory/lookup | A table maps key → shard | Extra lookup; flexible |

Costs: cross-shard queries and joins become application logic; cross-shard transactions are hard;
rebalancing is operationally heavy; unique constraints across shards need care. Choose a shard key
that matches the dominant access pattern (e.g. `organization_id` for FlagForge — every query is
already scoped by org, which is also what makes multi-tenant apps shard-friendly).

---

## 5. Queues and asynchronous processing

Move work that doesn't need to finish inside the HTTP request to background workers.

```
API ── enqueue {type: SEND_ALERT, incidentId} ──► Queue ──► Worker(s) ──► email/webhook provider
 └── 202 Accepted / 201 Created immediately                   │ fail → retry w/ backoff → DLQ
```

| Benefit | Example |
|---|---|
| Lower request latency | Respond before sending emails |
| Absorb spikes (load leveling) | 50 pushes in a minute become queued builds processed at worker capacity |
| Isolation from slow/failed dependencies | Webhook endpoint down doesn't break the API |
| Independent scaling | More workers without more API instances |

Delivery semantics — say these precisely:
- **At-most-once:** may lose messages, never duplicates.
- **At-least-once:** never lose (with retries), **may duplicate** → consumers must be **idempotent**. The realistic default.
- **Exactly-once:** in practice achieved as at-least-once delivery + idempotent processing / dedupe ("exactly-once *effects*").

Tools: SQS, RabbitMQ, Kafka (a log, not just a queue — replayable, ordered per partition), Redis
Streams/lists (ForgeCI M2: `BLMOVE` to a per-worker processing list + lease, or Streams consumer
groups — see the [ForgeCI spec](../18-projects/forgeci/README.md)), or a **Postgres table as a job queue**
(`SELECT … FOR UPDATE SKIP LOCKED`) — a good fit at small scale with no new infrastructure:

```sql
-- Worker claims up to 10 due jobs without blocking other workers
UPDATE jobs SET status = 'RUNNING', locked_at = now()
WHERE id IN (
  SELECT id FROM jobs
  WHERE status = 'PENDING' AND run_after <= now()
  ORDER BY run_after
  LIMIT 10
  FOR UPDATE SKIP LOCKED
)
RETURNING id, payload;
```

Also know: **dead-letter queue** (messages that failed N times, for inspection), **backoff with
jitter**, **poison messages**, **ordering** (usually only per key/partition), and the **transactional
outbox** pattern (write the event to an outbox table in the same DB transaction as the business
change; a relay publishes it) to avoid "DB committed but message lost".

---

## 6. CAP and consistency basics

**CAP:** in a distributed data store, during a **network partition (P)**, you must choose between
**Consistency** (every read sees the latest write or errors) and **Availability** (every request gets
a non-error response, possibly stale). Partitions aren't optional, so the real choice is **C or A when
the network splits**.

| System leaning | Behaviour during partition | Fits |
|---|---|---|
| CP | Refuse/timeout some requests to stay correct | Seat booking, payments, inventory, unique usernames |
| AP | Serve possibly stale data, reconcile later | Likes/view counters, feeds, dashboards, feature-flag propagation, DNS |

**PACELC** extension: *else* (no partition), trade **Latency vs Consistency** — e.g. synchronous
replication (consistent, slower) vs async (fast, possibly stale).

Consistency vocabulary:
- **Strong / linearizable:** reads reflect the latest committed write.
- **Eventual:** replicas converge if writes stop; reads may be stale meanwhile.
- **Read-your-writes:** a user always sees their own updates.
- **Monotonic reads:** a user never sees data go "back in time".

Junior-level move: *for each piece of data, say which consistency it needs*. LedgerX balances and
FlowGrid reservations: strong (single primary, transactions, row locks). FlagForge flag changes reaching
SDKs: eventual is fine (seconds), as long as each SDK evaluates against one consistent snapshot.

---

## 7. Idempotency

An operation is **idempotent** if doing it N times has the same effect as once. HTTP: `GET`, `PUT`,
`DELETE` are idempotent by definition; `POST` is not → retries after a timeout can double-book or
double-charge.

**Idempotency-Key pattern** (FlowGrid M2 order POST, LedgerX M2 transfer POST):

```
POST /api/v1/transfers
Idempotency-Key: 5f1c7e0a-...          (client-generated UUID per logical attempt)

Server:
 1. INSERT INTO idempotency_keys(key, user_id, request_hash, status) VALUES (...)
    ON CONFLICT (key, user_id) DO NOTHING
 2. If the key already existed:
      same request_hash + completed → return the stored response (same status + body)
      in progress                   → 409 Conflict (or wait)
      different request_hash        → 422 (key reused for a different request)
 3. Else perform the transfer in the same transaction, store the response, commit.
 4. Expire keys after e.g. 24 h.
```

Other idempotency techniques: natural unique constraints (one webhook per `X-GitHub-Delivery`), conditional
updates (`WHERE status = 'HELD'`), dedupe tables keyed by message ID in consumers, upserts.

---

## 8. Rate limiting

Protects services from abuse and overload, and enforces fairness/quotas. Respond **429 Too Many
Requests** with `Retry-After`.

| Algorithm | Idea | Pros | Cons |
|---|---|---|---|
| **Fixed window counter** | `INCR key:{minute}`; reject over N | Trivial | Burst of 2N across a window boundary |
| **Sliding window log** | Store each request timestamp (sorted set), count last 60 s | Accurate | Memory per request |
| **Sliding window counter** | Weighted current + previous window counts | Good accuracy, cheap | Approximation |
| **Token bucket** | Bucket of capacity C refills at R tokens/s; each request takes 1 | Allows bursts up to C, enforces average R | Slightly more logic |
| **Leaky bucket** | Queue drained at constant rate | Smooth output | Adds latency; drops when full |

Where: API gateway / nginx (`limit_req`) for coarse per-IP limits; application (Redis) for per-user /
per-API-key business limits. Key by user/API key when authenticated, by IP otherwise.

**Token bucket in Redis (atomic via Lua)** — must be atomic because many app instances share the bucket:

```lua
-- KEYS[1] = bucket key; ARGV[1] = capacity; ARGV[2] = refill tokens/sec; ARGV[3] = cost
local capacity = tonumber(ARGV[1])
local rate     = tonumber(ARGV[2])
local cost     = tonumber(ARGV[3])
local t        = redis.call('TIME')
local now      = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)   -- ms, Redis clock

local state  = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1]) or capacity
local ts     = tonumber(state[2]) or now

tokens = math.min(capacity, tokens + (math.max(0, now - ts) / 1000) * rate)
local allowed = 0
if tokens >= cost then
  tokens = tokens - cost
  allowed = 1
end
redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / rate * 1000) + 1000)
return allowed
```

```java
private static final DefaultRedisScript<Long> TOKEN_BUCKET =
        new DefaultRedisScript<>(loadLua("token_bucket.lua"), Long.class);

boolean tryConsume(String clientKey) {
    Long allowed = redis.execute(TOKEN_BUCKET, List.of("rl:" + clientKey), "20", "5", "1"); // burst 20, 5 req/s
    return allowed != null && allowed == 1L;
}
```

Using Redis's `TIME` avoids clock skew between app instances. Decide the **fail-open vs fail-closed**
policy if Redis is down (public status API: fail open with a local fallback limiter; login endpoint: fail closed).

---

## 9. CDN

A **Content Delivery Network** caches content at edge locations close to users.

- Static assets (React build, images) → CDN with long TTLs and content-hashed filenames.
- Cacheable public pages/API responses (e.g. a public status/badge endpoint like ForgeCI build badges) → short TTL (`Cache-Control: public, max-age=30`), absorbing traffic spikes before they reach your servers.
- Benefits: lower latency (fewer long round trips), offloads origin, DDoS absorption, TLS at the edge.
- Invalidation: versioned URLs (best) or purge API (slow/limited).
- Not for private per-user data unless carefully keyed.

---

## 10. Reliability patterns (name them, know one sentence each)

| Pattern | One sentence |
|---|---|
| **Timeouts** | Every network call has connect + read timeouts; no timeout = eventually stuck threads |
| **Retries with exponential backoff + jitter** | Retry transient failures, spreading retries so they don't synchronize |
| **Circuit breaker** | After repeated failures, stop calling a dependency for a while and fail fast (Resilience4j) |
| **Bulkhead** | Separate pools per dependency so one slow dependency can't exhaust all threads |
| **Backpressure** | Bounded queues/pools push back instead of accepting unbounded work |
| **Graceful degradation** | Serve stale cache / partial page when a dependency is down |
| **Health checks** | Liveness (restart me) vs readiness (don't send traffic yet) |
| **Redundancy across AZs** | Survive a data-center failure |
| **Observability** | Metrics, logs, traces, alerts on symptoms (latency, errors, saturation) |

---

## 11. Scaling stories for your projects (practise each in 2 minutes)

| Project | First bottleneck to expect | Next step | After that |
|---|---|---|---|
| **FlowGrid** | Lock contention on hot SKUs' inventory rows; dashboard queries | Short transactions, correct indexes, cache catalog reads, read replica for dashboards | Partition by warehouse/region; per-warehouse services only if teams demand it |
| **LedgerX** | Hot accounts (system/fee accounts touched by every transfer) serialise on row locks | Keep lock scope tiny; batch system-account postings; materialised balances with invariant checks | Shard by account id — cross-shard transfers need sagas/2-phase flows (say "hard", don't hand-wave) |
| **ForgeCI** | Worker capacity (containers per host); queue wait time | More worker hosts (stateless, leases recover crashes); per-project limits for fairness | Managed queue (SQS) + autoscaling workers on queue depth; ephemeral VMs per job for isolation |
| **FlagForge** | Evaluation QPS on the server; propagation fan-out | SDK local evaluation (server off the hot path), Redis snapshots, CDN for snapshot fetches | Regional read-only replicas of snapshots; streaming fan-out tier |

"Who watches the system?" — external health checks from outside AWS + CloudWatch alarms, for every project.

---

## 12. Interview questions

<details><summary>Why do stateless services scale better?</summary>

Any instance can serve any request, so you can add/remove instances freely behind a load balancer,
survive instance loss, and do rolling deploys. State moves to shared stores (DB, Redis, S3) or into
the request (JWT).
</details>

<details><summary>Read replicas vs sharding?</summary>

Replicas copy all data to scale reads and improve availability; writes still hit one primary.
Sharding splits data across primaries to scale writes and storage, at the cost of cross-shard
queries/transactions and operational complexity.
</details>

<details><summary>Explain CAP in one minute.</summary>

When a network partition happens, a distributed store must either reject some requests to stay
consistent (CP) or answer with possibly stale data to stay available (AP). Choose per data type:
bookings CP, counters/feeds AP.
</details>

<details><summary>How does a token bucket work?</summary>

A bucket holds up to C tokens and refills at R per second; each request consumes a token or is
rejected with 429. It allows bursts up to C while enforcing an average rate R. Implemented atomically
in Redis (Lua) so all instances share the limit.
</details>

<details><summary>Why do message consumers need to be idempotent?</summary>

Queues typically deliver at-least-once: after a crash or timeout, a message may be redelivered.
Idempotent processing (dedupe by message ID, unique constraints, conditional updates) makes
duplicates harmless.
</details>

<details><summary>When would you add a queue?</summary>

When work doesn't need to complete within the request (emails, exports, webhooks), to absorb spikes,
to isolate slow or unreliable dependencies, or to scale processing independently.
</details>
