# System Design Fundamentals — the framework and the numbers

> **Practical use:** write every project's design doc ([template](../18-projects/templates/design-doc.md)) with the same
> structure; estimate whether one Postgres instance can hold a year of check results.
> **Interview use:** a repeatable 40-minute structure so you never stare at a blank whiteboard.

← [System design README](./README.md) · Next: [Caching](./caching.md)

---

## 1. The six-step framework

| # | Step | Time (45-min interview) | Output on the whiteboard |
|---|---|---:|---|
| 1 | **Requirements** — functional + non-functional, scope | 5–7 min | Bullet lists; "out of scope" list |
| 2 | **Estimates** — traffic, storage (only if it'll drive decisions) | 3–5 min | 4–6 numbers |
| 3 | **API** — endpoints / main operations | 5 min | 3–6 endpoints with request/response |
| 4 | **Data model** — entities, keys, indexes, SQL vs NoSQL | 5 min | Tables with columns |
| 5 | **High-level design** — boxes and arrows, walk a request through it | 10 min | Diagram |
| 6 | **Deep dive + trade-offs** — bottlenecks, failure modes, scaling, alternatives | 10–15 min | Annotations, "if X then Y" |

Keep talking throughout; check in with the interviewer at the end of each step ("Does this scope
sound right before I go on?"). They will often steer the deep dive — let them.

---

## 2. Step 1 — Requirements

### Functional ("what does it do?")

Phrase as user actions. Pick the **3–5 core** ones and explicitly park the rest.

> URL shortener: *create short URL for a long URL; redirect short → long; optional custom alias;
> optional expiry.* Out of scope: analytics dashboard, user accounts (mention as follow-up).

### Non-functional ("how well?")

| Quality | Question to ask | Typical junior answer |
|---|---|---|
| Scale | How many users/requests? Read:write ratio? | "Let's assume 10 M redirects/day, 100:1 read-heavy" |
| Latency | What's acceptable? | Redirect p99 < 100 ms |
| Availability | Can it be down? | Redirects highly available; creation can degrade |
| Consistency | Is stale data OK? | Newly created link readable within seconds is fine |
| Durability | Can we lose data? | Links must not be lost |
| Security / abuse | Spam, auth, rate limits? | Rate limit creation per IP/user |
| Cost / team | Small team? | Prefer managed services, simple architecture |

Non-functional requirements **drive the design**. "Read-heavy" → cache + replicas. "Must not
double-book" → strong consistency + transactions. "Spiky" → queue.

---

## 3. Step 2 — Back-of-envelope estimation

### Numbers to memorize

| Quantity | Value |
|---|---|
| Seconds per day | 86,400 ≈ **10⁵** (use 100k) |
| Seconds per month | ≈ 2.5 × 10⁶ |
| Seconds per year | ≈ 3 × 10⁷ |
| 1 M requests/day | ≈ **12 req/s** average |
| Peak factor | 2–10× average (use 3–5× unless told) |
| KB / MB / GB / TB / PB | 10³ / 10⁶ / 10⁹ / 10¹² / 10¹⁵ bytes |
| `char` in UTF-8 (ASCII) | 1 byte; UUID 16 bytes binary / 36 as text; `bigint` 8; timestamp 8 |
| Postgres row overhead | ~24-byte header + alignment; indexes often add 50–100% |

Latency numbers: [`14-cs-fundamentals/memory-and-architecture.md`](../14-cs-fundamentals/memory-and-architecture.md#2-latency-numbers-every-engineer-should-know-orders-of-magnitude).

### Rough capacity of single components (order of magnitude — **say "roughly" and "I'd benchmark"**)

| Component | Ballpark |
|---|---|
| Spring Boot instance, simple JSON endpoint with a DB query | hundreds to a few thousand req/s |
| PostgreSQL, simple indexed reads on a decent instance | thousands to tens of thousands QPS |
| PostgreSQL writes (single row inserts, with fsync) | thousands/s; much more with batching |
| Redis single instance | ~100k simple ops/s |
| One SSD disk | tens of thousands of random IOPS |
| 1 Gbps network | ~100 MB/s |

These are for deciding "one box vs many" — not for exact sizing.

### The formula pattern

```
average QPS  = daily requests / 100,000
peak QPS     = average × peak factor
storage/year = writes per day × bytes per record × 365 (× ~2 for indexes/overhead)
bandwidth    = QPS × response size
```

### Worked example 1 — URL shortener

- 100 M new URLs/month, reads 100× writes.
- Writes: 100 M / 2.5 M s ≈ **40/s** avg, ~200/s peak.
- Reads: 4,000/s avg, ~20,000/s peak → **cache is essential**, a single DB would struggle at peak.
- Record: short code 8 B + long URL ~100 B + timestamps/ids ~30 B ≈ ~150 B, ×2 overhead ≈ 300 B.
- Storage: 100 M × 300 B = 30 GB/month ≈ **360 GB/year**; 5 years ≈ 1.8 TB → fits on one large DB instance, or shard later.
- Keyspace: base62, 7 chars = 62⁷ ≈ 3.5 × 10¹² codes → plenty for 6 B URLs over 5 years.

### Worked example 2 — ForgeCI (a system you build)

Assume 50 repositories, 200 pushes/day total, each build = 3 jobs × ~4 min, log output ~200 KB per job.

- Jobs: 200 × 3 = 600 jobs/day. Peak hour (say 30% of pushes): 60 builds → **180 jobs/hour ≈ 3 jobs/min**.
- Concurrency needed at peak: 3 jobs/min × 4 min ≈ **12 jobs running at once** (Little's law: arrivals × duration).
  With 4 concurrent containers per worker host → **3 workers**, or accept queueing (measure queue wait time!).
- Log storage: 600 × 200 KB = **120 MB/day** ≈ 44 GB/year → retention policy (e.g. 30 days) + partition or move old logs to S3.
- Log streaming: 12 running jobs × a few lines/s is trivial for Redis pub/sub; the SSE connection count
  (viewers) matters more than throughput.

Decisions it drives: worker count and per-host concurrency cap; log retention; that Postgres + Redis
on small instances are plenty — no Kafka needed. Measure the real numbers in M5 and put them in
`PERFORMANCE.md` with the methodology.

Estimation is only worth doing if it changes a decision. Say what it changed.

---

## 4. Step 3 — API

- Use REST (or name the operations) with **resources, methods, status codes, and main fields**.
- Include pagination for lists, idempotency for creates that may be retried, auth where needed.
- API design guidance: [`06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md).

```
POST /api/v1/urls                     {longUrl, customAlias?, expiresAt?}  → 201 {code, shortUrl}
GET  /{code}                          → 301/302 Location: longUrl  | 404 | 410 (expired)
GET  /api/v1/urls/{code}/stats        → 200 {clicks, createdAt}
```

Mention: 301 (permanent, browsers cache — fewer hits, lost analytics) vs 302 (temporary, every hit reaches you).

---

## 5. Step 4 — Data model

Default to **PostgreSQL** unless a requirement clearly says otherwise. Justify alternatives when you use them:

| Choose | When |
|---|---|
| Relational (Postgres/MySQL) | Relationships, transactions, constraints, ad-hoc queries — most business apps |
| Key-value (Redis, DynamoDB) | Lookup by key at very high scale, simple access patterns, caches, counters |
| Document (MongoDB, Postgres JSONB) | Flexible nested data read as a whole |
| Wide-column / time-series (Cassandra, Timescale) | Massive append-heavy writes by key + time |
| Search (OpenSearch/Elasticsearch) | Full-text search, relevance, facets |
| Object storage (S3) | Files, blobs, exports, backups |

Write tables with PK, important columns, **foreign keys, unique constraints and indexes that serve
your API's queries**:

```sql
CREATE TABLE urls (
  code        VARCHAR(10) PRIMARY KEY,
  long_url    TEXT        NOT NULL,
  owner_id    BIGINT      REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at  TIMESTAMPTZ
);
CREATE INDEX idx_urls_owner ON urls (owner_id, created_at DESC);
```

---

## 6. Step 5 — High-level design

1. Draw client → (CDN) → LB → app → DB. Add cache/queue/storage only when a requirement needs them.
2. **Walk one write and one read request through the diagram out loud.** This is where you show understanding.
3. Label arrows with protocol/operation ("GET /{code}", "INSERT", "enqueue SendEmail").
4. Keep app servers **stateless** so you can add more ([scalability.md](./scalability.md#2-stateless-services)).

---

## 7. Step 6 — Deep dive and trade-offs

Pick the most interesting or risky part (or follow the interviewer):

| Probe | Where to go |
|---|---|
| "What if traffic grows 10×?" | Cache, read replicas, horizontal app scaling, then sharding |
| "What's the bottleneck?" | Usually the DB — show which query and which index |
| "What if X fails?" | Health checks, retries with backoff, replicas, queues buffer, graceful degradation |
| "How do you avoid duplicates?" | Unique constraints, idempotency keys, exactly-once *effects* via dedupe |
| "How would you monitor it?" | Metrics (rate, errors, latency p95/p99), logs with request IDs, alerts on symptoms |
| "Consistency?" | Which data must be strongly consistent (balances, stock reservations) vs eventually (counters, dashboards, flag propagation) |

**Trade-off vocabulary** (state both sides): consistency vs availability, latency vs freshness
(cache TTL), cost vs redundancy, simplicity vs scalability, write amplification vs read speed
(indexes, denormalization), push vs pull, sync vs async.

---

## 8. Common junior mistakes

- Jumping to microservices, Kafka and Kubernetes for 10 req/s. **Start simple, scale on evidence.**
- Skipping requirements → designing the wrong thing.
- Diagram without data model, or data model without the indexes the queries need.
- Saying "use a cache" without saying *what key, what TTL, how it's invalidated*.
- Saying "use NoSQL because it scales" without an access-pattern argument.
- Silence. Think out loud; it's the evaluation.
- Not relating to experience — you *have* built FlowGrid, LedgerX, ForgeCI and FlagForge; use them.

---

## 9. Practice template (copy per problem)

```
Problem:
1. Functional reqs (core 3–5):            Out of scope:
2. Non-functional: scale, latency, availability, consistency, durability
3. Estimates: writes/s, reads/s, storage/yr → decision it drives:
4. API:
5. Schema (+ indexes):
6. Diagram + walk a read and a write:
7. Deep dive: bottleneck → fix; failure → mitigation; 10× growth → plan
8. Trade-offs I stated:
9. Time used:        What I'd do better:
```

---

## 10. Interview questions

<details><summary>How do you start a system design interview?</summary>

Clarify functional scope and non-functional requirements (scale, latency, consistency, availability),
state assumptions and what's out of scope, then estimate if numbers will drive decisions.
</details>

<details><summary>Estimate the QPS for 50 M requests/day.</summary>

50 M / 100k s ≈ 500 req/s average; with a 3–5× peak factor, ~1,500–2,500 req/s peak.
</details>

<details><summary>How do you choose between SQL and NoSQL?</summary>

By access patterns and consistency needs. Relational for relationships, transactions and flexible
queries; key-value/wide-column for massive scale on simple key-based access. Postgres covers most
junior-level problems; JSONB covers semi-structured data.
</details>
