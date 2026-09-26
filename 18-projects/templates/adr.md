# ADR Template — Architecture Decision Record

> One ADR per significant, hard-to-reverse decision. Keep each to one page. Number them
> sequentially in `docs/adr/NNNN-<slug>.md` and index them in `docs/DESIGN_DECISIONS.md`.
> Never edit an accepted ADR's decision — write a new one that supersedes it.
> ADRs are the interviewer's favourite document: "why did you choose X over Y?" is answered here.

---

## Template

```markdown
# ADR-NNNN: <Decision in one imperative sentence>

| Field | Value |
|---|---|
| Status | Proposed / Accepted / Deprecated / Superseded by ADR-NNNN |
| Date | YYYY-MM-DD |
| Project / milestone | <ForgeCI M3> |
| Deciders | <you> |
| Related | <design doc, issues, other ADRs> |

## Context
What situation forces a decision? Which requirements, constraints and forces are in play?
State facts, not preferences. Include the numbers you actually have (or say you have none).

## Options considered
### Option A — <name>
- How it would work
- Pros
- Cons
### Option B — <name>
- …
### Option C — <name> (if any)

## Decision
"We will <do X> because <the reasons that outweighed the cons>."
Name the trade-off you are accepting on purpose.

## Consequences
- Positive: …
- Negative / risks: …
- What becomes easier, what becomes harder, what must be revisited and when.

## Verification
How you will know the decision was right: a test, a measurement, a failure exercise.

## Revisit triggers
Conditions under which this ADR should be re-opened (e.g. "if we need bidirectional messaging",
"if evaluation p99 exceeds X", "if a second instance is deployed").
```

---

## Example — filled ADR

# ADR-0003: Use Server-Sent Events instead of WebSockets for live build logs

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-XX-XX |
| Project / milestone | ForgeCI M3 — Live logs + UI |
| Deciders | me |
| Related | `docs/design/03-live-logs.md`, issue #41, ADR-0002 (Redis pub/sub for log fan-out) |

## Context

Workers append log chunks for running jobs. Chunks are persisted in PostgreSQL (`job_log_chunk`
with a monotonically increasing `seq` per job) and published on a Redis pub/sub channel per job.
The React UI must show logs live while a job runs and must show the full log after it finishes.
Browser tabs disconnect frequently (sleep, navigation, flaky Wi-Fi). Traffic is strictly
**server → client**: the UI never sends log data upstream. Cancellation is a separate REST call.
The API runs behind a plain reverse proxy on a single EC2 instance; no sticky sessions or
special upgrade handling are configured.

## Options considered

### Option A — WebSockets
- Full-duplex channel; Spring supports it via `spring-websocket` (raw or STOMP).
- Pros: bidirectional; familiar to many interviewers; binary frames possible.
- Cons: needs an HTTP upgrade path through the proxy; no built-in reconnect or resume, so
  replay-from-sequence must be hand-built on both sides; STOMP adds a protocol layer for no benefit
  here; connection lifecycle harder to test with plain HTTP tooling; auth on the upgrade request
  needs custom handling.

### Option B — Server-Sent Events (`text/event-stream`)
- One long-lived HTTP response; Spring MVC `SseEmitter` (or WebFlux `Flux<ServerSentEvent>`).
- Pros: one-directional fits the use case exactly; `EventSource` in the browser reconnects
  automatically and sends `Last-Event-ID`, which maps directly onto our `seq` for replay; plain
  HTTP — works through proxies, `curl`-testable, ordinary JWT/cookie auth; trivially load-testable.
- Cons: text only (fine for logs); one connection per open job view — browsers limit concurrent
  HTTP/1.1 connections per host (~6), so many simultaneously open log views would need HTTP/2 or
  a multiplexed stream; no client → server messages (not needed).

### Option C — Polling `GET /jobs/{id}/logs?after=seq`
- Pros: simplest; no long-lived connections.
- Cons: latency = poll interval; wasteful under load; makes the "live" UI feel laggy. Kept as the
  fallback path when `EventSource` is unavailable.

## Decision

We will use **SSE** for live build logs. The traffic is one-directional, the browser's built-in
reconnect + `Last-Event-ID` gives us replay-from-sequence almost for free (server reads
`Last-Event-ID`, loads chunks with `seq > id` from PostgreSQL, then subscribes to Redis for new
ones), and plain HTTP keeps proxying, auth and testing simple. We accept the per-host connection
limit as a known constraint and the text-only limitation as irrelevant for logs.

## Consequences

- Positive: `curl -N` shows a live log; integration test is an ordinary HTTP client reading a stream;
  reconnect logic lives in the browser, not in our code; no proxy upgrade configuration.
- Negative / risks: the API must guarantee **no gap** between "load persisted chunks with `seq > id`"
  and "subscribe to pub/sub" — we subscribe first, then load, then de-duplicate by `seq`.
  Long-lived connections consume a servlet thread each with Spring MVC; mitigated by virtual
  threads (`spring.threads.virtual.enabled=true`) or by moving to WebFlux if connection counts grow.
- Cancellation stays a REST `POST /jobs/{id}/cancel`, not an in-stream message.

## Verification

- `LiveLogSseIT`: start a job, open the stream, kill the client after chunk 5, reconnect with
  `Last-Event-ID: 5`, assert chunks 6..n arrive exactly once and in order.
- Failure exercise FE-4: kill Redis during streaming; assert the stream ends cleanly and reconnect replays from PostgreSQL.
- Measure connection count vs memory on the dev box before and after enabling virtual threads (benchmark report).

## Revisit triggers

- A feature needs client → server messages inside the same channel (e.g. interactive terminals).
- More than ~5 concurrent log views per user become normal and HTTP/2 is not available at the proxy.
- Streaming becomes a bottleneck on the API's thread pool that virtual threads do not solve.

---

## Suggested ADRs per project (write these — interviewers ask about exactly these)

| Project | Decisions worth an ADR |
|---|---|
| FlowGrid | `SELECT … FOR UPDATE` vs `@Version` for reservations · Idempotency-Key storage (DB vs Redis, TTL) · Deterministic allocation scoring · Redis cache-aside with degrade-to-DB · EC2 + Compose vs managed services |
| LedgerX | Append-only ledger enforced by trigger/privileges · Derived vs materialized balance · Ordered pessimistic locking vs optimistic · Isolation level for transfers · Transactional outbox vs direct publish · Compensating entries vs mutation for reversals |
| ForgeCI | `BLMOVE` + lease vs Redis Streams · Docker Engine API vs shelling out to `docker` · SSE vs WebSockets (above) · App-vs-infra retry taxonomy · Lease/heartbeat parameters · Docker socket on worker EC2 (security) · Redis vs SQS on AWS |
| FlagForge | Immutable config versions (rollback = new version) · `hash(flagKey:userKey) mod 10000` bucketing · Polling vs SSE for the SDK · Local vs server-side evaluation · Stale-if-error and offline defaults · Redis snapshot invalidation and stampede protection |
