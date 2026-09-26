# System Design Interview — Junior Level

Junior and new-grad loops rarely include a full "design Twitter" round, but many include
**light design**: "design a URL shortener", "how would you build the back end for X",
"how would your project handle 100× the traffic", or an object-oriented design question.
The bar is not distributed-systems expertise. It's: **can you turn vague requirements into
a sensible, working design, explain the data model and APIs, and reason about trade-offs?**

Concepts live in [`../15-system-design/`](../15-system-design/) (fundamentals, caching,
scalability, practice problems). This file is the **interview framework**. Starts Week 22
(system design mock #1), practised through Week 26.

---

## 1. What's evaluated at junior level

| Signal | Looks like |
|---|---|
| Requirements & scoping | Asks clarifying questions; separates functional vs non-functional; picks a sensible MVP |
| Data model | Right entities, keys, relationships, indexes for the main queries |
| API design | Clean REST endpoints with methods, status codes, pagination |
| High-level architecture | Client → load balancer → stateless app servers → database (+ cache, queue when justified) |
| Trade-offs | Can say *why* this choice and what it costs |
| Scaling awareness | Knows the next step when a component becomes the bottleneck — doesn't over-engineer up front |
| Communication | Drives the conversation, draws clearly, checks in |

---

## 2. The framework (45 minutes)

```
1 Requirements (5) → 2 Estimates (3) → 3 API (5) → 4 Data model (7)
→ 5 High-level design (10) → 6 Deep dive (10) → 7 Wrap-up (5)
```

### 1. Requirements — 5 min

- **Functional:** "Users can create a short URL; visiting it redirects; optional custom alias; optional expiry." Write 3–5 bullets. Ask which are in scope.
- **Non-functional:** read-heavy or write-heavy? Latency target? Availability vs consistency? Data retention? Security/abuse?
- **Out of scope:** say it explicitly ("I'll leave analytics dashboards out unless you want them").

### 2. Back-of-the-envelope estimates — 3 min

Only enough to justify decisions. Round aggressively.

| Quantity | Example |
|---|---|
| Writes/day → per second | 1M new URLs/day ÷ ~10⁵ s/day ≈ **10 writes/s** |
| Read:write ratio | 100:1 → **~1 000 reads/s**, peaks ×3–5 |
| Storage | 1M/day × 365 × 500 bytes ≈ **~180 GB/year** |
| Conclusion | "One PostgreSQL instance handles this; reads benefit from a cache." |

Useful numbers: ~10⁵ seconds/day; a single well-indexed Postgres instance handles thousands of simple queries/s; Redis GET ≈ sub-millisecond; network round trip within a region ≈ ~1 ms, cross-continent ≈ ~100 ms.

### 3. API — 5 min

```
POST   /api/urls            {longUrl, customAlias?, expiresAt?} → 201 {code, shortUrl}
GET    /{code}              → 301/302 Location: longUrl  | 404 | 410 if expired
GET    /api/urls?page=0&size=20   (owner's URLs)
DELETE /api/urls/{code}     → 204
```

Mention: auth (who can create/delete), validation, idempotency for POSTs that clients may retry, rate limiting on public endpoints, pagination. See [`../06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md).

### 4. Data model — 7 min

```sql
CREATE TABLE short_url (
  code        VARCHAR(10) PRIMARY KEY,
  long_url    TEXT        NOT NULL,
  owner_id    BIGINT      REFERENCES app_user(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at  TIMESTAMPTZ
);
CREATE INDEX idx_short_url_owner_created ON short_url (owner_id, created_at DESC);
```

Say which queries each index serves. SQL vs NoSQL: default to PostgreSQL unless the access pattern clearly needs something else — and say why.

### 5. High-level design — 10 min

Draw left to right:

```
Client → Load balancer → App servers (stateless, N instances) → PostgreSQL (primary)
                                  │                                 └→ read replica (later)
                                  └→ Redis cache (code → long_url, TTL)
                       (async)  → Queue → Worker (click analytics, emails, cleanup)
```

Walk one write and one read through the diagram. Justify each box; remove any you can't justify.

### 6. Deep dive — 10 min (interviewer usually picks)

Be ready for 2–3 of these:

| Topic | What to say |
|---|---|
| **Key generation** | Base62 of an auto-increment ID (simple, guessable) vs random 7-char + unique constraint + retry on collision (unguessable). Trade-off: predictability vs collision handling. |
| **Caching** | Cache-aside; TTL; invalidation on delete; hot keys; what staleness is acceptable. [`../15-system-design/caching.md`](../15-system-design/caching.md) |
| **Scaling reads** | Cache first, then read replicas (replication lag!), then CDN for static. |
| **Scaling writes** | Batching, queue + workers, partitioning/sharding by key as a last resort. |
| **Consistency** | Where you need strong (booking a seat, payments) vs eventual (view counts). |
| **Reliability** | Health checks, multiple instances behind LB, retries with backoff + idempotency, timeouts, backups. |
| **Rate limiting** | Token bucket in Redis keyed by IP/user; 429 with `Retry-After`. |
| **Background jobs** | Queue, at-least-once delivery → idempotent consumers, dead-letter queue. |
| **Observability** | Metrics (latency p95/p99, error rate), structured logs with request IDs, alarms. |
| **Security** | AuthN/Z, input validation, secrets management, least privilege, abuse (malicious URLs). |

### 7. Wrap-up — 5 min

Summarize the design in 4 sentences, name the **bottleneck at 10× load** and what you'd change, and list what you left out.

---

## 3. Use your own projects as design answers

You've already built systems with real design decisions. Reference them — honestly scoped:

| Concept | Your evidence |
|---|---|
| Concurrency control, transactions, locking | LedgerX: ordered pessimistic locks, isolation experiments, the $500/$400/$400 test; FlowGrid: N-threads-one-unit reservation |
| Idempotency | FlowGrid (key + request hash + TTL) → LedgerX (fingerprint, conflict, crash-safe) → ForgeCI (webhook delivery-id dedupe) |
| Queues, workers, retries, recovery | ForgeCI: reliable Redis queue with leases, heartbeats, orphan recovery, app-vs-infra retry policy |
| Caching + low latency | FlagForge: Redis config snapshots, p99 evaluation latency, stampede protection; FlowGrid catalog cache-aside |
| Real-time delivery | ForgeCI live logs and FlagForge propagation over SSE with replay-from-sequence |
| Indexing for a query pattern | LedgerX history cursor pagination; FlowGrid low-stock queries — with `EXPLAIN` before/after |
| Authorization / multi-tenancy | FlagForge org → project → environment scoping; FlowGrid warehouse roles |
| Deployment & observability | All four: EC2 + RDS + S3, CloudWatch alarm, structured logs, CI/CD; ForgeCI multi-worker Compose |

"In FlagForge I hit exactly this: every evaluation rebuilt the environment config from Postgres, so I cached an immutable snapshot per environment in Redis and invalidated it on publish — and added single-flight rebuilding so a publish didn't cause a stampede."

---

## 4. Practice prompts (junior-appropriate)

| # | Prompt | Key discussion points |
|---:|---|---|
| 1 | URL shortener | key generation, redirect codes, cache, read-heavy |
| 2 | Pastebin | blob storage (S3) vs DB, expiry cleanup job |
| 3 | Rate limiter for a public API | token bucket vs fixed window, Redis atomicity, 429 |
| 4 | Wallet / payments ledger (scale LedgerX) | double-entry, locking hot accounts, idempotency, reconciliation, outbox |
| 5 | CI runner platform (scale ForgeCI to 1 000 workers) | queue partitioning, leases, log volume, autoscaling workers, isolation |
| 6 | Multi-warehouse order fulfillment (scale FlowGrid) | reservation contention, allocation, partial fulfillment, event-driven shipping |
| 7 | Library lending system (OOD) | classes, responsibilities, due dates/fines — compare with [`../21-debugging-code-reading/exercises/buggy-library/`](../21-debugging-code-reading/exercises/buggy-library/) |
| 8 | Parking lot (OOD) | enums, strategy for pricing, concurrency on spot assignment |
| 9 | Feature-flag service (scale FlagForge to 100k SDK connections) | snapshot caching, fan-out, SSE connection limits, multi-tenancy |
| 10 | Notification service (email/SMS/push) | queue, retries, idempotency, templates, user preferences |

More worked problems: [`../15-system-design/junior-design-problems.md`](../15-system-design/junior-design-problems.md).

### Object-oriented design variant

Some junior loops ask "design the classes for X" instead. Framework: clarify use cases → identify nouns (classes) and verbs (methods) → relationships (composition vs inheritance) → key interfaces/patterns (Strategy for pricing, Observer for notifications) → write 2–3 method signatures → walk one use case through the objects → discuss extensibility and concurrency.

---

## 5. Common mistakes

| Mistake | Instead |
|---|---|
| Jumping to microservices, Kafka, sharding | Start with one service + Postgres; add components when a requirement demands it |
| No questions, straight to drawing | 5 minutes of requirements first |
| Buzzwords without mechanics | Explain how it works: "cache-aside means the app checks Redis, on miss reads Postgres and writes back with a TTL" |
| Ignoring the data model | Tables and indexes are where junior candidates can shine |
| Monologue | Check in: "Does this level of detail work, or should I go deeper on storage?" |
| Claiming production experience you don't have | "I haven't run this at scale; in my project at small scale I did X, and at larger scale I'd expect Y" |

---

## 6. Scoring (for mocks)

1–4 on: **Requirements & scoping** · **High-level design** · **Data model & APIs** · **Trade-offs & scaling** (rubric scale as in [`mock-interviews.md`](./mock-interviews.md)). Log in [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md#mock-interviews).
