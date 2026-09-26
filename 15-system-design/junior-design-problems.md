# Junior Design Problems — 8 worked at interview depth

> **How to use:** from Week 20, one problem per week. Set a 40-minute timer, do it **out loud** on paper
> using the [six-step framework](./fundamentals.md#1-the-six-step-framework), *then* read the worked
> version and note what you missed. Problems 1–4 are the interview versions of your four projects —
> practise presenting what you **actually built** in this format, and use the worked version only to
> find gaps. Problems 5–8 are classic junior prompts.

← [Scalability](./scalability.md) · [System design README](./README.md) · Interview delivery: [`16-interview-prep/system-design-interview.md`](../16-interview-prep/system-design-interview.md)

| # | Problem | Core idea being tested | Your evidence |
|---|---|---|---|
| 1 | [Inventory reservation system](#1-inventory-reservation-system) | Concurrency on shared stock, idempotent orders, state machines | [FlowGrid](../18-projects/flowgrid/README.md) |
| 2 | [Wallet / ledger](#2-wallet--double-entry-ledger) | Correctness, double-entry, locking order, idempotency, reconciliation | [LedgerX](../18-projects/ledgerx/README.md) |
| 3 | [CI job runner](#3-ci-job-runner) | Queues, workers, leases, isolation, log streaming | [ForgeCI](../18-projects/forgeci/README.md) |
| 4 | [Feature-flag service](#4-feature-flag-service) | Read-heavy low latency, caching, deterministic bucketing, propagation | [FlagForge](../18-projects/flagforge/README.md) |
| 5 | [URL shortener](#5-url-shortener) | Key generation, read-heavy caching, redirects | — |
| 6 | [Rate limiter](#6-rate-limiter) | Algorithms, shared counters, atomicity | Token bucket in [scalability.md](./scalability.md#8-rate-limiting) |
| 7 | [Notification service](#7-notification-service) | Async queues, retries, idempotency, fan-out | Outbox (LedgerX), low-stock alerts (FlowGrid) |
| 8 | [Leaderboard](#8-leaderboard) | Sorted sets, top-K, write-heavy counters | — |

Honesty rule for problems 1–4: when you say "in my project I…", it must be true of your repository,
and any number must come from a measurement you documented. "I didn't build that part — here's how I'd
approach it" is a strong answer.

---

## 1. Inventory reservation system

*"Design the backend for an e-commerce fulfillment company with several warehouses: orders reserve
stock, then get picked, packed and shipped."*

**Requirements**
- Functional: manage SKUs and per-warehouse stock; create order → reserve stock; cancel → release; allocate order lines to warehouse(s); pick → pack → ship; returns restock.
- Non-functional: **never oversell** (strong consistency on stock); order creation safe to retry; ~50 orders/s peak; dashboard reads can be seconds stale.

**Estimates:** 50 orders/s × 3 lines = 150 stock updates/s peak — a single Postgres handles this if
transactions are short. Hot SKUs (a viral product) are the real risk: contention on the same rows.

**API**
```
POST /api/v1/orders            Idempotency-Key: <uuid>   {lines:[{skuId, qty}], shipTo, priority}
                               → 201 {orderId, status: RESERVED | PARTIALLY_RESERVED | BACKORDERED}
POST /api/v1/orders/{id}/cancel                          → 200 (releases reservations)
GET  /api/v1/inventory?skuId=&warehouseId=&page=         → stock by state
POST /api/v1/pick-lists  /  POST /api/v1/shipments       → fulfillment workflow
```

**Schema (core)**
```sql
inventory_levels(id PK, sku_id FK, warehouse_id FK, on_hand, available, reserved, version,
                 UNIQUE(sku_id, warehouse_id), CHECK (available >= 0 AND reserved >= 0))
orders(id PK, customer_ref, status, priority, created_at)
order_lines(id PK, order_id FK, sku_id FK, qty)
reservations(id PK, order_line_id FK, warehouse_id FK, qty, status, created_at)
idempotency_keys(key, user_id, request_hash, response_json, created_at, PRIMARY KEY(key, user_id))
```

**Design**
```
client ─► LB ─► API (stateless) ──┬──► Postgres (stock, orders, reservations: source of truth)
                                   ├──► Redis (catalog cache, low-stock view — never reservation decisions)
                                   └──► scheduler: low-stock alerts, expired-reservation cleanup
```
Write path: begin tx → idempotency check → choose warehouse(s) (deterministic scoring) → lock the
`inventory_levels` rows (`SELECT … FOR UPDATE`, in a consistent order) or conditional update
(`… WHERE available >= ?`) → insert reservations → store idempotent response → commit.

**Bottlenecks & trade-offs**
- Hot SKU rows serialize — keep transactions tiny; pessimistic locking wins under high contention, optimistic (`@Version`) under low.
- Multi-line orders lock several rows → lock in a stable order (by id) to avoid deadlocks; retry on `40P01`.
- Allocation quality vs speed: scoring across all warehouses per order is fine at this scale.

**Follow-ups:** reservation expiry for unpaid orders · split shipments across warehouses · a flash
sale with 10,000 buyers for 100 units (queue buyers / pre-decrement in Redis with DB reconciliation — trade-off) ·
multi-region warehouses.

---

## 2. Wallet / double-entry ledger

*"Design a digital wallet: deposits, withdrawals, transfers between users, history, refunds."*

**Requirements**
- Functional: accounts per user (+ system accounts: external clearing, fees); deposit/withdraw; P2P transfer; history; refunds/reversals.
- Non-functional: **no money created or lost**, balances never negative, every operation idempotent, full audit trail; latency < 300 ms; correctness ≫ availability (CP).

**Model:** double-entry — every journal transaction has ≥ 2 **immutable** entries whose signed amounts
**sum to zero**. Balances are derived from entries (optionally materialised and checked).

**API**
```
POST /api/v1/transfers   Idempotency-Key   {fromAccountId, toAccountId, amount, currency}
                         → 201 {journalId, status: COMPLETED}  | 409 insufficient funds | 422 key reused
GET  /api/v1/accounts/{id}/entries?cursor=&limit=50            → cursor-paginated history
POST /api/v1/journals/{id}/reversal                            → compensating journal, links original
```

**Schema (core)**
```sql
accounts(id PK, owner_id, type, currency, balance NUMERIC(19,4), version)
journal_txns(id PK, type, status, idempotency_key, reverses_id FK NULL, created_at)
ledger_entries(id PK, journal_id FK, account_id FK, amount NUMERIC(19,4) CHECK (amount <> 0), created_at)
  -- append-only: no UPDATE/DELETE (revoked privileges or trigger)
outbox(id PK, aggregate_id, event_type, payload JSONB, published_at NULL)
```

**Design**
```
client ─► API ─► Postgres: one transaction = lock both accounts (ascending id) → check balance →
                 insert journal + 2 entries → update materialised balances → idempotency row → outbox row
          outbox relay ─► notifications / analytics (at-least-once, consumers idempotent)
          reconciliation job ─► verify Σentries = 0 per journal, balance = Σentries per account → alert on drift
```

**Bottlenecks & trade-offs**
- Hot system accounts (fees) touched by every transfer → contention; mitigate with batched postings or sub-accounts.
- Lock ordering prevents A→B / B→A deadlocks.
- Why not `double`? Why not update balances in place without entries? (Auditability, reconciliation.)
- Isolation: Read Committed + explicit row locks vs Serializable with retries — know both.

**Follow-ups:** external payment provider callbacks (webhook idempotency) · multi-currency · crash
between debiting and crediting (can't happen inside one DB transaction — explain why; it *can* across
services → sagas) · scheduled payments · fraud/velocity rules.

---

## 3. CI job runner

*"Design a simplified GitHub Actions: on push, run the repository's pipeline in isolated containers and show live logs."*

**Requirements**
- Functional: register repos; receive push webhooks; parse pipeline config; queue jobs; run steps in containers; stream logs; show status; cancel; retry infra failures.
- Non-functional: isolation between builds; no lost jobs if a worker dies; webhook processed exactly once in effect; queue wait p95 < 30 s at normal load.

**Estimates:** see [fundamentals.md worked example 2](./fundamentals.md#worked-example-2--forgeci-a-system-you-build) — ~12 concurrent jobs at peak → 3 worker hosts.

**API**
```
POST /webhooks/github        (HMAC-SHA256 verified, deduped on X-GitHub-Delivery) → 202
GET  /api/v1/builds?repo=&page=                     GET /api/v1/builds/{id}
POST /api/v1/jobs/{id}/cancel                        POST /api/v1/builds/{id}/retry
GET  /api/v1/jobs/{id}/logs/stream   (SSE, resume with Last-Event-ID = last log sequence)
```

**Schema (core)**
```sql
webhook_deliveries(delivery_id PK, received_at)          -- dedupe
builds(id PK, repo_id FK, commit_sha, status, created_at)
jobs(id PK, build_id FK, name, status, attempt, worker_id, lease_expires_at, needs TEXT[])
log_chunks(job_id FK, seq, content, PRIMARY KEY(job_id, seq))
```

**Design**
```
GitHub ─► API ─► Postgres (builds/jobs)            Redis: queue ──BLMOVE──► worker 1..N
            │         ▲                                  processing list + lease       │
            │         └──── status, log chunks ◄─────────────────────────────────────┤
            ├─ SSE ◄── Redis pub/sub (log lines) ◄───────────────────────────────────┘
            └─ reaper: leases expired → re-queue (infra failure) or fail (max attempts)
worker: pull job → start container (image, limits, workspace) → clone → run steps → stream logs →
        exit code → persist result → ALWAYS remove container
```

**Bottlenecks & trade-offs**
- Worker capacity, not the API, limits throughput → autoscale workers on queue depth.
- Redis queue + your own leases vs SQS visibility timeout ([12-aws deploy walkthrough §14](../12-aws/deploy-walkthrough.md#14-the-multi-service-case-forgeci-week-19)).
- App failure (exit ≠ 0 → no retry) vs infra failure (container start error, worker loss → retry with backoff).
- Security: Docker socket = root on the worker host → dedicated hosts; ephemeral VMs at scale.

**Follow-ups:** DAG pipelines (`needs:` → topological scheduling, fan-out/fan-in) · per-project
concurrency limits · caching dependencies between builds · secrets for builds · log retention.

---

## 4. Feature-flag service

*"Design a feature-flag system: teams toggle features and roll out gradually to a percentage of users."*

**Requirements**
- Functional: orgs/projects/environments; flags with rules (attribute match, user list, percentage rollout, default); versioned configs with rollback; audit log; SDKs evaluate flags.
- Non-functional: evaluation **p99 in single-digit ms or better** (it sits on every request of the customer's app); flags must still evaluate if the flag service is down; changes propagate within seconds; the same user always gets the same variant.

**Key idea — deterministic bucketing:**
```
bucket = hash(flagKey + ":" + userKey) mod 10000        // 0..9999
enabled for a 10% rollout  ⇔  bucket < 1000
```
Stable across servers and restarts; increasing 10% → 20% keeps the first 10% enabled.

**API**
```
PUT  /api/v1/envs/{envId}/flags/{key}      admin: new config version (immutable)
POST /api/v1/envs/{envId}/flags/{key}/rollback?toVersion=7   → creates version n+1 copying v7
GET  /api/v1/sdk/snapshot     (SDK key auth; ETag / version)  → whole env config
GET  /api/v1/sdk/stream       (SSE: "snapshot version changed")
POST /api/v1/evaluate         server-side evaluation for thin clients
```

**Schema (core)**
```sql
flags(id PK, env_id FK, key, UNIQUE(env_id, key))
flag_versions(id PK, flag_id FK, version, rules JSONB, created_by, created_at, UNIQUE(flag_id, version))
env_snapshots(env_id, version, document JSONB, PRIMARY KEY(env_id, version))
audit_events(id PK, org_id, actor, action, target, before JSONB, after JSONB, at)
```

**Design**
```
admin UI ─► API ─► Postgres (versions, audit) ─commit─► build snapshot ─► Redis (snapshot + pub/sub)
                                                                            │
customer app + SDK ◄── SSE "changed" / polling ◄── API nodes ◄──────────────┘
SDK: in-memory snapshot → local evaluation (no network per flag check) → stale-if-error → defaults
```

**Bottlenecks & trade-offs**
- Server-side evaluation for every request doesn't scale and adds latency → **local evaluation in SDKs**.
- Propagation delay vs load (polling interval vs streaming connections).
- Snapshot rebuild stampede after publish → single-flight.
- Consistency: eventual across SDKs, but each SDK evaluates against one whole snapshot (no half-applied changes).

**Follow-ups:** segments reused across flags · scheduled rollouts · experiment metrics · kill switch
latency guarantees · SDKs in other languages (contract tests).

---

## 5. URL shortener

**Requirements:** shorten a long URL (optional alias, expiry); redirect; basic click count. 100 M new
URLs/month, 100:1 read:write; redirect p99 < 50 ms; links must not be lost.

**Estimates:** writes ~40/s, reads ~4,000/s avg, ~20k/s peak; ~360 GB/year ([fundamentals.md](./fundamentals.md#worked-example-1--url-shortener)).

**API**
```
POST /api/v1/urls {longUrl, customAlias?, expiresAt?} → 201 {code, shortUrl}
GET  /{code} → 302 Location   (404 unknown, 410 expired)
```

**Schema:** `urls(code PK, long_url, owner_id, created_at, expires_at)`; clicks aggregated separately.

**Key generation options**

| Option | How | Trade-off |
|---|---|---|
| Counter + base62 | DB sequence → base62 encode | Short, unique; predictable/enumerable; sequence is a central point |
| Random + check | 7 random base62 chars, insert, retry on unique violation | Unpredictable; collisions rare at 3.5 T keyspace |
| Hash of URL | First 7 chars of base62(SHA-256) | Same URL → same code (dedupe); still need collision handling |

**Design**
```
client ─► CDN/LB ─► app ──► Redis (code → long_url, TTL) ──miss──► Postgres (or KV store)
                      └──► click events ─► queue ─► aggregator (async, never slows redirects)
```

**Bottlenecks:** read path → cache; hot links → CDN caching the 302 briefly; analytics writes → async.
**Follow-ups:** 301 vs 302 (browser caching vs analytics) · abuse/malware checks · custom domains · deleting links (cache invalidation).

---

## 6. Rate limiter

**Requirements:** limit each API key to N requests/minute across a fleet of API servers; return 429
with `Retry-After`; add < 5 ms latency; decide behaviour when the limiter's store is down.

**Design**
```
client ─► LB ─► API instance ─► limiter middleware ─► Redis (shared counters/buckets, atomic via Lua)
                                    │ allowed → handler     │ denied → 429 + Retry-After + X-RateLimit-* headers
```

**Algorithms:** fixed window, sliding log, sliding window counter, **token bucket**, leaky bucket — comparison and a
token-bucket Lua script in [scalability.md §8](./scalability.md#8-rate-limiting).

**Data:** key `rl:{apiKey}` → hash `{tokens, ts}` with expiry; limits config in Postgres, cached in memory.

**Trade-offs**
- Atomicity: read-modify-write must be one Lua script (or `INCR` + `EXPIRE` for fixed window) — two app instances otherwise both allow the last request.
- Clock: use Redis `TIME`, not each server's clock.
- Fail-open (availability) vs fail-closed (protection) when Redis is down — per endpoint.
- Local in-memory pre-check to shave Redis calls for very hot keys (approximate).

**Follow-ups:** per-user *and* per-IP limits · different tiers · distributed limits across regions
(accept approximation) · concurrency limits (in-flight requests) vs rate limits — ForgeCI's per-project
limit is a concurrency limit.

---

## 7. Notification service

**Requirements:** other services send "notify user X about event Y" by email/SMS/push/webhook; user
preferences; retries; no duplicate sends; 1 M notifications/day, bursts of 10× during incidents.

**API**
```
POST /api/v1/notifications  Idempotency-Key  {userId, template, data, channels?} → 202 {id}
GET  /api/v1/notifications/{id}                                              → status per channel
PUT  /api/v1/users/{id}/preferences
```

**Schema:** `notifications(id, user_id, template, payload, created_at)`,
`deliveries(id, notification_id, channel, status, attempts, next_attempt_at, provider_msg_id)`,
`preferences(user_id, channel, enabled, quiet_hours)`.

**Design**
```
producers ─► API ─► Postgres (notification + deliveries, one tx) ─► queue per channel
                                                                     │
               email worker ─► provider A   sms worker ─► provider B   webhook worker ─► customer URL
               retries: exponential backoff + jitter · max attempts → DLQ · status back to Postgres
```

**Key points**
- Accept fast (202), deliver async; queues absorb bursts; per-channel workers scale independently.
- **At-least-once** delivery → dedupe with the idempotency key and provider message IDs; many providers accept an idempotency key too.
- Producers publish reliably via a **transactional outbox** (LedgerX M3 pattern) so "order committed but notification lost" can't happen.
- Rate limits per provider and per user (don't send 50 SMS in a minute).

**Follow-ups:** templates & localisation · digesting/batching · priority lanes (security alerts vs
marketing) · tracking opens/clicks · webhook signing (HMAC — the same thing ForgeCI verifies from GitHub).

---

## 8. Leaderboard

**Requirements:** game with 10 M players; submit score; show global top 100, a player's rank, and
players around them; near-real-time; 5k score updates/s peak.

**API**
```
POST /api/v1/scores {playerId, score}          → 204
GET  /api/v1/leaderboard/top?limit=100         → [{rank, playerId, score}]
GET  /api/v1/leaderboard/players/{id}          → {rank, score, neighbours:[…]}
```

**Design — Redis sorted set as the serving structure, Postgres as the durable record**
```
client ─► API ─┬─► Redis ZSET "lb:global"  (ZADD with GT to keep the best score)
               │     top-K:   ZREVRANGE lb:global 0 99 WITHSCORES      O(log n + k)
               │     rank:    ZREVRANK lb:global <player>                O(log n)
               └─► Postgres scores (async/batched write for durability & history)
```

**Trade-offs**
- Sorted set = skip list: O(log n) updates and rank queries; 10 M members fits in memory (~1 GB order of magnitude — estimate it).
- Ties: encode a tie-breaker (earlier timestamp wins) into the score, e.g. `score * 1e10 + (MAX_TS - ts)` — mind double precision.
- Rebuild the ZSET from Postgres if Redis is lost; periodic snapshots.
- Time-windowed boards (daily/weekly) = separate keys with expiry.
- SQL alternative for smaller scale: index on `score DESC` + `ORDER BY … LIMIT 100`; rank via window function is expensive at 10 M rows per request.

**Follow-ups:** sharding by region/league · anti-cheat validation · friends-only leaderboards (per-user sets or compute on read).

---

## Self-review checklist (after each attempt)

- [ ] Stated functional + non-functional requirements and what's out of scope
- [ ] Did an estimate *and* said which decision it changed
- [ ] API with status codes, pagination, idempotency where needed
- [ ] Schema with keys, constraints and the indexes my queries need
- [ ] Walked one write and one read through the diagram
- [ ] Named the bottleneck and a concrete fix; named one failure mode and its mitigation
- [ ] Stated at least two trade-offs with both sides
- [ ] For problems 1–4: clearly separated what I built from what I'd add
- [ ] Finished within 40 minutes; logged gaps in [`trackers/interview-tracker.md`](../trackers/interview-tracker.md)
