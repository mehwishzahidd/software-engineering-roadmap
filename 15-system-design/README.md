# 🏗️ 15 — System Design (junior level)

> **What junior interviews actually ask:** not "design Netflix" at staff depth, but *"design a URL
> shortener / rate limiter / something like your project"* — can you gather requirements, sketch an
> API and schema, draw a sensible box diagram, estimate roughly, and discuss 2–3 trade-offs honestly?
> Your four projects already contain most of the answers. This folder formalizes them.

Timeline (from [ROADMAP](../ROADMAP.md#5-week-by-week-master-table)):

| Week | Activity |
|---|---|
| 20 | Caching + rate limiting built for real in P4 → read [`caching.md`](./caching.md) alongside |
| 22 | **Formal study**: [`fundamentals.md`](./fundamentals.md), [`scalability.md`](./scalability.md); system design mock #1 |
| 23–26 | One problem from [`junior-design-problems.md`](./junior-design-problems.md) per week, timed (35–45 min), out loud; full-loop mocks |

Interview-day method and delivery tips: [`16-interview-prep/system-design-interview.md`](../16-interview-prep/system-design-interview.md).
Foundations this builds on: [`14-cs-fundamentals/networking.md`](../14-cs-fundamentals/networking.md),
[`database-internals.md`](../14-cs-fundamentals/database-internals.md), [`04-sql-databases/redis.md`](../04-sql-databases/redis.md).

---

## Files

| File | Contents |
|---|---|
| [`fundamentals.md`](./fundamentals.md) | The 6-step framework, requirements, API & data model, back-of-envelope estimation with worked numbers |
| [`caching.md`](./caching.md) | Where to cache, cache-aside / write-through / write-back, TTL, invalidation, stampede, P4 status page |
| [`scalability.md`](./scalability.md) | Vertical/horizontal, stateless services, load balancers, replication, sharding, queues, CAP, idempotency, rate limiting, CDN |
| [`junior-design-problems.md`](./junior-design-problems.md) | 8 worked problems: URL shortener, rate limiter, uptime monitor (P4), ticket booking (P2), issue tracker (P3), notification service, pastebin, leaderboard |

---

## What "junior depth" means

| Expected | Nice to have | Not expected |
|---|---|---|
| Clarify functional & non-functional requirements | Capacity estimates that drive a decision | Consensus algorithms (Raft/Paxos) internals |
| REST API with request/response shapes | Discussing consistency trade-offs (CAP) | Designing a database engine |
| Relational schema with keys & indexes | Sharding key choice | Multi-region active-active |
| Box diagram: client → LB → stateless app → DB + cache + queue | Failure modes & monitoring | Exact cloud product SKUs |
| One or two bottlenecks and fixes | Idempotency, retries, backoff | |
| Honest trade-offs; "I'd measure first" | Relating to your own project | |

The strongest junior signal: **"In PulseWatch I actually hit this — here's what I measured and what I changed."**

---

## The universal starting diagram

```
 Clients (browser / mobile / other services)
        │  HTTPS
        ▼
 ┌──────────────┐     static assets
 │ DNS + CDN    │◄──── (React build, images)
 └──────┬───────┘
        ▼
 ┌──────────────┐
 │ Load balancer│  L7, TLS termination, health checks
 └──────┬───────┘
   ┌────┴─────┬───────────┐
   ▼          ▼           ▼
 ┌─────┐   ┌─────┐     ┌─────┐   stateless app servers (Spring Boot), scale horizontally
 │ app │   │ app │ ... │ app │
 └──┬──┘   └──┬──┘     └──┬──┘
    │  ┌──────┴──────┐    │
    ├─►│ Cache(Redis)│◄───┤      hot reads, rate limits, sessions
    │  └─────────────┘    │
    ├─►┌─────────────┐◄───┤      async work: emails, exports, webhooks
    │  │ Queue       │──► workers
    │  └─────────────┘
    ▼
 ┌─────────────────┐   replication   ┌──────────────┐
 │ Primary DB      │────────────────►│ Read replica │
 └─────────────────┘                 └──────────────┘
    + object storage (S3) for files · + metrics/logs/alerts
```

Almost every junior problem is this diagram with 2–3 boxes emphasized. Learn to draw it in 60 seconds.

---

## Checklist

- [ ] Can run the 6-step framework on a blank page in 40 minutes
- [ ] Can do a QPS + storage estimate in under 3 minutes with round numbers
- [ ] Can explain cache-aside and one invalidation strategy with P4's status page
- [ ] Can explain why stateless services scale horizontally and where state goes instead
- [ ] Can explain a token bucket rate limiter in Redis (P4 M2)
- [ ] Can explain idempotency keys (P2 M4) and at-least-once delivery
- [ ] Have done all 8 problems out loud at least once; 3 of them twice
- [ ] Have presented P4's architecture as a system design answer in a mock

---

## Resources

- *System Design Interview – An Insider's Guide* (Alex Xu), Vol. 1 — chapters on rate limiter, URL shortener, notification system, key-value store (read for structure, not to memorize)
- *Designing Data-Intensive Applications* (Kleppmann) — chapters 1, 5, 6, 11 for depth
- The "system-design-primer" GitHub repository (donnemartin) — overview topics
- AWS Architecture Center and Well-Architected Framework (docs.aws.amazon.com) — real reference architectures
- NeetCode's system design course section (neetcode.io) — you have Pro
