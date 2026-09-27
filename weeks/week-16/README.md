# Week 16 — ForgeCI M3: live logs and UI · Checkpoint 16

[← Week 15](../week-15/) · [Roadmap](../../ROADMAP.md) · [Week 17 →](../week-17/)

**Phase 3 · ForgeCI (Weeks 14–19)** — builds run; now people can *watch* them. Workers append log chunks (persisted in Postgres, published via Redis pub/sub), the API streams them over **Server-Sent Events** with **replay-from-sequence** on reconnect, and a React/TS UI shows a build list, build detail and a live log with status badges. Ends with **[Checkpoint 16](../../checkpoints/checkpoint-16.md)**: "Do I have a working queue → worker → container → live-log pipeline? Standard Mediums in ≤ 30 min?"

| Block | Hours | Notes |
|---|---:|---|
| Project (ForgeCI M3) | 32 | Log chunk pipeline (worker → Postgres + Redis pub/sub), SSE endpoint with replay, React build list/detail + live log, status badges |
| Learning | 6 | SSE vs WebSockets, Redis pub/sub semantics, backpressure basics, React data-heavy/streaming views |
| DSA (Python) | 6 | 1-D DP (cont.) + Intervals — 8 new + reviews + 1 Java rep |
| Interview / review | 3 | Weekly mock #4 (Track A Python + Track B slice), drill, **Checkpoint 16** (Sun) |

---

## 1. Main objective

By Sunday:

- the worker reads container output (stdout/stderr, demultiplexed) as it happens, buffers into **chunks** (by size or time), assigns each a **monotonic sequence number per job**, persists to `log_chunk`, and publishes `{jobId, seq}` (or the chunk) on Redis pub/sub;
- `GET /jobs/{id}/logs/stream` is an **SSE** endpoint: on connect it replays chunks with `seq > Last-Event-ID` from Postgres, then subscribes to live events; `id:` carries the seq so the browser's automatic reconnect resumes with no gaps or duplicates; the stream closes with a terminal `status` event when the job finishes;
- a React + TypeScript (Vite) UI lists builds (paginated by cursor), shows a build with its jobs/steps and status badges, and renders the live log with auto-scroll, a "follow" toggle and reconnect handling;
- the SSE-over-WebSockets choice is explained in ADR 0002 with your actual reasons.

## 2. Prerequisites

- Tags `m2`/`mvp`; a real push runs to `SUCCESS`/`FAILED` in a container.
- React/TS from [Week 7](../week-07/) (hooks, effects, API client, auth on the client) — refresh [`08-react/02-hooks.md`](../../08-react/02-hooks.md) on Monday if `useEffect` cleanup is not automatic for you.
- LedgerX keyset cursor and outbox at-least-once — log replay is the same idea with sequence numbers.
- 1-D DP method (state → recurrence → memo → table → space) is fluent in Python.

## 3. Learning topics

| Topic | Subtopics | File |
|---|---|---|
| SSE vs WebSockets | one-way vs duplex, HTTP/1.1 connection limits (≈6 per host) vs HTTP/2, auto-reconnect + `Last-Event-ID`, proxies/buffering (`X-Accel-Buffering`, `Cache-Control: no-cache`), text-only, auth (cookie/query token since `EventSource` cannot set headers), when WebSockets are the right call | [`06-rest-apis/http-for-apis.md`](../../06-rest-apis/http-for-apis.md), [`14-cs-fundamentals/networking.md`](../../14-cs-fundamentals/networking.md) |
| Spring SSE | `SseEmitter` (servlet) vs WebFlux `Flux<ServerSentEvent>`; timeouts, completion, error handling; thread model; heartbeats/comments | [`05-spring-boot/02-web-layer.md`](../../05-spring-boot/02-web-layer.md) |
| Redis pub/sub | fire-and-forget (no persistence, no replay), `SUBSCRIBE`/`PSUBSCRIBE`, subscriber connection model in Spring Data Redis (`RedisMessageListenerContainer`), why persistence must come from Postgres | [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md) |
| Backpressure basics | producer faster than consumer; bounded buffers; drop vs block vs coalesce; slow SSE clients; chunk sizing | [`14-cs-fundamentals/concurrency.md`](../../14-cs-fundamentals/concurrency.md), [`15-system-design/scalability.md`](../../15-system-design/scalability.md) |
| Docker log streaming | attach vs `logs --follow`, stdout/stderr multiplexed frames (8-byte header), line vs chunk boundaries, ANSI codes | [`11-docker/README.md`](../../11-docker/README.md) |
| React data-heavy views | `EventSource` in `useEffect` with cleanup, `useReducer` for append-only log state, virtualised lists for large logs, polling vs streaming for the list view, TanStack Query or a hand-rolled fetcher, error/loading states, status badge component | [`08-react/02-hooks.md`](../../08-react/02-hooks.md), [`08-react/04-api-integration-auth.md`](../../08-react/04-api-integration-auth.md), [`08-react/05-architecture-testing.md`](../../08-react/05-architecture-testing.md), [`07-javascript-typescript/02-async-javascript.md`](../../07-javascript-typescript/02-async-javascript.md), [`07-javascript-typescript/03-typescript.md`](../../07-javascript-typescript/03-typescript.md) |
| Frontend testing | Vitest + Testing Library for the log view reducer and the badge; a fake `EventSource` | [`09-testing/frontend-testing.md`](../../09-testing/frontend-testing.md) |

## 4. Concepts to learn

### 4.1 Why SSE (the ADR you will defend)

CI logs are **server → client, append-only text**. SSE gives that over plain HTTP with built-in reconnect and `Last-Event-ID`, works through most proxies, needs no extra protocol or library, and is trivially cacheable/loggable. WebSockets would add duplex you do not need, a handshake/upgrade path, manual reconnect and resume logic. Costs of SSE: HTTP/1.1 per-host connection limit (mitigate: one stream per open job view, HTTP/2 in production), text-only (fine), `EventSource` cannot send headers (auth via cookie or a short-lived token in the query string — document the security trade-off). Decision: **SSE**; revisit if you ever need client → server streaming (you do not: cancel is a `POST`).

- **Interview angle (Track B):** "Why SSE over WebSockets?" — and the follow-up "how do you resume after a disconnect without duplicates?" (§4.3).

### 4.2 Log chunk pipeline

Worker side: a reader thread per running job (a good **virtual thread** use: blocked on I/O) consumes the `exec` stream, demultiplexes stdout/stderr, and a `ChunkBuffer` flushes when `size ≥ 8 KB` **or** `age ≥ 500 ms` or the step ends. Each flush: `INSERT INTO log_chunk(job_id, seq, step_ordinal, stream, content, created_at)` with `seq` from an in-memory `AtomicLong` per job (**unique constraint `(job_id, seq)`**), then `PUBLISH forgeci:logs:<jobId> <seq>`. Persist **before** publish — subscribers fetch by seq from Postgres if they need content, or you include the content in the message to save a round trip (choose; the persisted row is the truth either way).

```sql
CREATE TABLE log_chunk (
  job_id     UUID        NOT NULL REFERENCES job(id),
  seq        BIGINT      NOT NULL,
  step_ordinal INT       NOT NULL,
  stream     TEXT        NOT NULL CHECK (stream IN ('stdout','stderr')),
  content    TEXT        NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (job_id, seq)
);
```

Retention: cap per job (e.g. 10 MB → then `truncated=true` marker) so a `yes | head -c 10G` step cannot fill the DB. Redact tokens with a masking filter before persisting.

- **Where ForgeCI uses this:** SSE replay (this week), M4 cancel/timeout writes a final system chunk ("killed: timeout after 600 s"), M5's Python log-analysis tool reads `log_chunk`.

### 4.3 SSE endpoint with replay-from-sequence

On `GET /jobs/{id}/logs/stream` with header `Last-Event-ID: 41` (browser sends it automatically on reconnect): (1) subscribe to `forgeci:logs:<jobId>` **first** (so nothing is missed between replay and live), buffering incoming seqs; (2) replay rows `seq > 41` from Postgres in order; (3) drain the buffer, skipping seqs ≤ last replayed; (4) continue live. Every event carries `id: <seq>`. If the job is already finished, replay everything and send `event: status` then complete. Heartbeat comment `: ping` every 15 s keeps proxies from closing idle streams.

```java
@GetMapping(path = "/jobs/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@PathVariable UUID id,
                         @RequestHeader(value = "Last-Event-ID", required = false) Long lastId) {
    SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
    logStreamService.attach(id, lastId == null ? 0L : lastId, emitter);   // subscribe → replay → live
    emitter.onTimeout(emitter::complete);
    emitter.onCompletion(() -> logStreamService.detach(id, emitter));
    return emitter;
}
```

Correctness property to test: for any disconnect point, the client's concatenated chunks equal the persisted log exactly once, in order.

- **Interview angle:** subscribe-before-replay ordering is the classic question; so is "what if two API instances?" → pub/sub fans out to all instances, each serves its own SSE clients; state is in Postgres.

### 4.4 Backpressure with slow clients

`SseEmitter.send` blocks the sending thread when the client's TCP window is full. Isolate: one dispatcher thread per API instance pulls from a bounded per-client queue (`ArrayBlockingQueue(256)`); on overflow, **drop the client's queue and resend a "resync from seq N" marker** (the client reconnects with `Last-Event-ID`) rather than blocking the worker or the pub/sub thread. Never let a slow browser slow the worker: the worker only writes to Postgres and Redis.

### 4.5 React: streaming log view

- `useEffect(() => { const es = new EventSource(url); es.onmessage = …; return () => es.close(); }, [jobId])` — cleanup on unmount and on `jobId` change (the classic bug: two streams open).
- State via `useReducer` with actions `append(seq, text)`, `status(s)`, `reset()`; ignore `seq ≤ lastSeq` defensively (idempotent client).
- Large logs: render lines with a virtualised list (or cap visible lines + "load earlier"), `key` = seq; auto-scroll only while the user is at the bottom ("follow" toggle).
- Build list: cursor pagination + `refetchInterval` (polling is fine for a list; streaming is for the log).
- Types: `type JobStatus = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'INFRA_ERROR'` and a `<StatusBadge status={…}/>` with an exhaustive `switch` (`never` check).

- **Interview angle (Track B):** "How do you avoid memory growth in a live view?" (cap + virtualise), "How do you handle reconnect?" (`EventSource` does it; your reducer must be idempotent).

## 5. Resources

- MDN: *Using server-sent events* and `EventSource` — https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events
- WHATWG HTML Standard, §9.2 Server-sent events (the `Last-Event-ID` semantics) — https://html.spec.whatwg.org/multipage/server-sent-events.html
- Spring Framework docs: *Asynchronous Requests* (`SseEmitter`) — https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- Redis docs: *Pub/Sub* — https://redis.io/docs/latest/develop/interact/pubsub/
- Docker Engine API: *Attach to a container* (stream format) — https://docs.docker.com/engine/api/
- React docs: *Synchronizing with Effects*, *Extracting State Logic into a Reducer* — https://react.dev/learn
- Testing Library + Vitest docs — https://testing-library.com/ , https://vitest.dev/
- NeetCode 150 — 1-D DP, Intervals; problems in §7

## 6. Exercises and assignments

### 6.1 Warm-up exercises (~2.5 h)

1. **SSE by hand (30 min).** `curl -N` against a 15-line Spring `SseEmitter` demo; observe the wire format (`id:`, `event:`, `data:`, blank line, `: ping`); kill the server mid-stream; reconnect with `-H "Last-Event-ID: 7"`.
2. **Pub/sub is fire-and-forget (15 min).** `redis-cli SUBSCRIBE` in one terminal; `PUBLISH` before the subscriber connects → nothing delivered. Write the sentence that goes into the ADR.
3. **Docker stream frames (30 min).** Read the 8-byte frame header with `docker-java`'s `Frame`/`ResultCallback`; print stream type + payload for a step that writes to both stdout and stderr.
4. **Reducer TDD (30 min, TS).** Write `logReducer` tests first: out-of-order/duplicate seqs are ignored; `status` terminal stops appends; `reset` on job change.
5. **Intervals warm-up (20 min, Python).** Sort by start; merge with a running `last`; know why sorting is required and the O(n log n) bound.

### 6.2 Assignment — ForgeCI M3

**Acceptance criteria:**

- [ ] `log_chunk` table with `(job_id, seq)` PK; worker chunker (8 KB / 500 ms / step-end flush), stdout/stderr tagged, per-job monotonic seq, persist-then-publish; per-job cap with `truncated` marker; token masking.
- [ ] Redis pub/sub channel per job; API instances subscribe on demand and unsubscribe when no clients remain.
- [ ] SSE endpoint with subscribe-before-replay, `Last-Event-ID` resume, `id:` per event, `event: status` terminal event, heartbeat comments, 30-min emitter timeout, bounded per-client queue with resync on overflow.
- [ ] Auth for the stream (cookie or short-lived token) documented in `docs/SECURITY.md`.
- [ ] `ui/` (Vite + React + TS): build list (cursor pagination, polling), build detail (jobs, steps, exit codes, durations), live log view (`EventSource`, reducer, follow toggle, virtualised or capped), `StatusBadge`; error/loading states; `npm test` (Vitest) with reducer + badge tests; `npm run build` in CI.
- [ ] Non-functional: a 50 MB-output step does not exhaust API memory (cap + streaming), and a slow client does not slow the worker (test with a throttled consumer).
- [ ] ADR 0002 finalised with your real reasons and trade-offs.

### 6.3 Break it

- Replay first, subscribe second; run a chatty step; observe missing chunks between replay end and subscription start. Restore the order and keep the property test.
- Publish before persist; kill the worker between them; the client shows a chunk that does not exist in Postgres — on reconnect it "disappears". Restore persist-then-publish.
- Remove the `useEffect` cleanup; switch between two jobs quickly; watch interleaved logs in the DevTools network tab (two open streams). Restore.
- Send 20,000 lines with no cap and no virtualisation; measure the tab's memory and frame rate; restore the cap.

### 6.4 Debug it

- SSE works locally but in Compose behind nginx the stream arrives in bursts every ~30 s: proxy buffering. Fix with `X-Accel-Buffering: no` / `proxy_buffering off` and `Cache-Control: no-cache`; also confirm heartbeats.
- After reconnect the log shows duplicated lines: `id:` was set to the *chunk count* not the *seq*, or the reducer trusts every message. Fix the id, keep the reducer's `seq ≤ lastSeq` guard as defence in depth.
- Logs from a step that prints without newlines never appear until the step ends: the chunker flushes on newline only. Add the time-based flush.

## 7. DSA — 1-D DP (continued) + Intervals (8 new problems, in Python)

**Language: Python (Track A).** Guides: [`03-dsa/18-dp-1d.md`](../../03-dsa/18-dp-1d.md) · [`03-dsa/19-intervals.md`](../../03-dsa/19-intervals.md) · Templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md). Intervals: `intervals.sort(key=lambda x: x[0])`; merge with `res[-1][1] = max(res[-1][1], e)`; "min rooms / max overlap" = sort starts and ends separately (or a `heapq` of end times); Checkpoint 16's timed Mediums come from Weeks 13–16 patterns.

| # | Problem | Difficulty | Time limit | Day |
|---|---|---|---|---|
| 139 | Word Break | Medium | 25 min | Mon |
| 300 | Longest Increasing Subsequence | Medium | 25 min (O(n²) then `bisect` O(n log n)) | Mon |
| 152 | Maximum Product Subarray | Medium | 25 min | Tue |
| 416 | Partition Equal Subset Sum | Medium | 30 min | Tue |
| 57 | Insert Interval | Medium | 25 min | Wed |
| 56 | Merge Intervals | Medium | 15 min | Wed |
| 435 | Non-overlapping Intervals | Medium | 25 min | Thu |
| 253 | Meeting Rooms II (Premium; alt: 1094. Car Pooling or 452. Minimum Number of Arrows to Burst Balloons) | Medium | 25 min | Thu |

Reviews due: Day-3 Week 15 Thu/Fri; Day-7 Week 15 DP; Day-14 Week 14 topo/Union-Find; Day-30 Week 12 backtracking.

**Java rep (Fri, ≤ 30 min):** #56 Merge Intervals in Java (`Arrays.sort(intervals, Comparator.comparingInt(a -> a[0]))`, `List<int[]>`, `toArray(new int[0][])`) — sorting 2-D arrays with a comparator is a Java-specific fluency check.

## 8. Project work — ForgeCI M3 (Live logs + UI)

Spec: [`18-projects/forgeci/README.md`](../../18-projects/forgeci/README.md) · [`milestones.md`](../../18-projects/forgeci/milestones.md) (M3) · [`failure-engineering.md`](../../18-projects/forgeci/failure-engineering.md)

### 8.1 Task checklist

- [ ] **Mon:** milestone `M3 — Live logs + UI` + issues; `V4__log_chunks.sql`; worker stream reader (frames → stdout/stderr) + `ChunkBuffer` (size/time/step-end) + per-job seq + persist-then-publish + cap + masking; unit tests for the chunker.
- [ ] **Tue:** Redis pub/sub listener container in `api`; `LogStreamService` (subscribe → replay → live; per-client bounded queue; resync marker); SSE controller with `Last-Event-ID`, heartbeats, terminal status event.
- [ ] **Wed:** replay-correctness property test (§8.3); slow-client test; auth for the stream; `docs/SECURITY.md` section; ADR 0002 finalised.
- [ ] **Thu:** `ui/` scaffold (Vite + React + TS), API client with types, build list + detail pages, `StatusBadge`, routing.
- [ ] **Fri:** live log view (`EventSource` + reducer + follow + virtualised/capped), Vitest tests, `npm run build` in CI, Compose `ui` service (static build behind nginx or Vite preview — document).
- [ ] **Sat (extended, +2 h):** end-to-end: push → watch the log live in the browser → kill the API mid-stream → browser reconnects with no gaps; failure scenarios (§8.4); PR review; tag `m3`.

### 8.2 Acceptance summary

M3 done = §6.2 green + a recorded 60-second screen capture of a live build in the UI (goes into `docs/` and later the README) + tag `m3`. Checkpoint 16 on Sunday reviews this pipeline end to end.

### 8.3 Verification tests you write

| Test | Type | Proves |
|---|---|---|
| `ChunkBufferTest` | unit | flush at 8 KB; flush at 500 ms with partial line; flush at step end; stderr/stdout separated; seq strictly increasing |
| `LogChunkPersistThenPublishIT` | Testcontainers Postgres + Redis | for every published seq, the row already exists |
| `LogCapTruncatesIT` | Testcontainers | 10 MB + 1 → `truncated` marker row, no further inserts |
| `TokenMaskingTest` | unit | the repo token never appears in a persisted chunk |
| `SseReplayThenLiveIT` | `@SpringBootTest` + WebTestClient/`RestClient` streaming | connect with `Last-Event-ID: 41` while chunks 40..60 are arriving → receives exactly 42..60 then live, in order, no duplicates |
| `SseReplayPropertyIT` (`@RepeatedTest(20)`) | random disconnect points | concatenation of received chunks == persisted log, once, ordered |
| `SseFinishedJobIT` | | finished job → full replay + `status` event + stream completes |
| `SseSlowClientDoesNotBlockWorkerIT` | throttled consumer | worker chunk latency unaffected; slow client gets a resync marker |
| `SseHeartbeatIT` | | `: ping` at least every 15 s on an idle stream |
| `ui: logReducer.test.ts`, `StatusBadge.test.tsx` | Vitest | duplicates/out-of-order ignored; terminal status; exhaustive badge rendering |

### 8.4 Failure-engineering scenarios this week

M3 set: API instance killed mid-stream (browser reconnects to another instance — run `api` ×2 behind a simple round-robin) → no gaps; Redis restarted → subscriptions must be re-established (Spring's listener container reconnect) and clients resync via `Last-Event-ID`; worker killed mid-step → partial chunks persisted, job state observed (M4 fixes recovery); a step emitting 100 MB → cap engaged, UI stays responsive; proxy buffering scenario (§6.4). Write-ups in `docs/FAILURES.md`.

### 8.5 GitHub expectations

Milestone `M3`; PRs `feat/m3-log-chunks`, `feat/m3-sse-stream`, `feat/m3-ui-scaffold`, `feat/m3-ui-live-log`, `test/m3-replay-property`, `docs/m3-adr-sse`. CI adds `ui` job (`npm ci && npm test && npm run build`). Tag `m3`.

### 8.6 Interview questions built from this milestone (Track B — answer out loud, 2 min each)

1. "Why SSE and not WebSockets for build logs? When would you switch?" (one-way append-only, auto-reconnect + `Last-Event-ID`, plain HTTP; switch for client→server streaming)
2. "A user's browser reconnects after 30 s offline. How do they get exactly the lines they missed?" (`id: <seq>`, replay `seq > lastId` from Postgres, subscribe-before-replay, seq filter)
3. "Why persist the chunk before publishing it?" (pub/sub is fire-and-forget; the row is the truth; a publish-before-persist crash shows phantom lines)
4. "Two API instances, one worker — does the client see all lines regardless of which instance it hits?" (pub/sub fans out; state in Postgres; yes)
5. "A step prints 5 GB. What happens to Postgres, the API and the browser?" (per-job cap + truncated marker; bounded client queues + resync; UI cap/virtualisation)
6. "How do you keep a slow browser from slowing the worker?" (worker only writes DB + Redis; API per-client bounded queue; drop and resync)
7. "How would you test the resume logic?" (property test over random disconnect points: received == persisted, once, ordered)

Full list: [`18-projects/forgeci/interview-questions.md`](../../18-projects/forgeci/interview-questions.md).

## 9. Git activity

- Monorepo with `ui/`: add path filters to CI so backend-only PRs skip `npm` jobs and vice versa (15 min).
- Keep the screen capture small (`.gif`/`.webm` ≤ 2 MB) or link to a release asset — do not bloat the repo history.
- Practise `git rebase --onto` once to move a UI branch off a stale base (15 min).

## 10. Interview preparation

Two tracks, never mixed (ROADMAP §10): **Track A = coding interview in Python**; **Track B = software-engineering / résumé interview in Java, Spring, SQL, TS/React, Redis…**

**Track A (Python)**
- **Weekly mock #4 (Sat, 45 min + 15 min review):** one unseen Medium from Weeks 13–16 patterns (e.g. LeetCode 1584-style Union-Find or 1143 LCS preview) in Python; target ≤ 30 min to a working solution — Checkpoint 16's bar. Score with [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md); log in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Recorded solve (Tue, 30 min):** #435 with the greedy argument spoken.

**Track B (Java / TS / projects)**
- **Engineering mock slice (Sat, 15 min):** "How does your live log survive a browser reconnect without duplicates?" and "Why SSE?" — from ADR 0002 and the replay test.
- **Résumé-defense drill — React, TypeScript, JavaScript:** [`17-resume-tech-defense/react.md`](../../17-resume-tech-defense/react.md), [`typescript.md`](../../17-resume-tech-defense/typescript.md), [`javascript.md`](../../17-resume-tech-defense/javascript.md). Required: `useEffect` cleanup, `useReducer` vs `useState`, discriminated unions + `never` exhaustiveness, event loop and `EventSource`.
- **Checkpoint 16 (Sun, ~3 h):** [`checkpoints/checkpoint-16.md`](../../checkpoints/checkpoint-16.md) — timed coding (Python, Track A) + knowledge + practical (Java/TS, Track B) + explain + ForgeCI M1–M3 review. Score honestly; remediation into Weeks 17–18 review blocks.
- **Applications (Sun):** 5/week; you are approaching **OA-ready** ([`JOB_READINESS.md`](../../JOB_READINESS.md)) — OA simulation #1 is next week.

## 11. Revision work

- Checkpoint 16 covers Weeks 13–16: re-read ADRs 0001–0004, the queue design, the executor lifecycle, the log pipeline; be able to draw the whole flow from webhook to browser in 3 minutes.
- Flashcards: SSE wire format, `Last-Event-ID`, subscribe-before-replay, pub/sub guarantees, `SseEmitter` timeout, `X-Accel-Buffering`, exit codes 137/143 (again), `BLMOVE` (again).
- Python pitfall of the week: `sorted()` returns a new list, `list.sort()` returns `None` — `intervals = intervals.sort()` silently destroys your input.

## 12. Daily plan

| Day | Learning | Project | DSA | Interview / other |
|---|---|---|---|---|
| **Mon** (8h) | SSE vs WebSockets; Docker stream frames; labs 1–3 (2h) | Milestone, `log_chunk`, worker reader + chunker + persist/publish + cap + masking (4.5h) | DP cont. #139, #300 (1.5h) | — |
| **Tue** (8h) | — | Pub/sub listener, `LogStreamService`, SSE controller (5h) | #152, #416 + Day-3 reviews (2h) | Recorded solve #435 (1h) |
| **Wed** (8h) | Backpressure; React streaming views + reducer TDD (2h) | Replay property test, slow-client test, stream auth, ADR 0002 (4.5h) | Intervals §1–5, #57, #56 (1.5h) | — |
| **Thu** (8h) | — | UI scaffold, API client, build list + detail, badges (5h) | #435, #253/#452 + Day-7 reviews (2h) | `docs/SECURITY.md` stream section (1h) |
| **Fri** (5h) | Frontend testing (1h) | Live log view + tests + CI `ui` job (2h) | Reviews + Java rep #56 (1h) | Retro prep (1h) |
| **Sat** (8h) | — | End-to-end live run + reconnect demo, failure scenarios, screen capture, PR review, tag `m3` (6h) | — | Mock #4 (A + B slice) + drill (2h) |
| **Sun** (3h) | — | — | Day-14/30 reviews | **Checkpoint 16**, trackers, plan Week 17, applications |

## 13. End-of-week test = Checkpoint 16

This week's Sunday test **is** [`checkpoints/checkpoint-16.md`](../../checkpoints/checkpoint-16.md). Do it in full. Saturday-evening self-check (20 min) first:

1. Order of operations when an SSE client connects with `Last-Event-ID`, and why.
   <details><summary>Answer</summary>Subscribe to pub/sub first, then replay `seq > lastId` from Postgres, then drain buffered live events skipping already-sent seqs. Replay-then-subscribe leaves a gap; subscribe-then-replay may produce overlap, which the seq filter removes.</details>
2. What does Redis pub/sub *not* give you, and where does that guarantee live instead?
   <details><summary>Answer</summary>No persistence, no replay, no delivery to absent subscribers. Persistence and replay come from `log_chunk` in Postgres with `(job_id, seq)`.</details>
3. A browser tab is on a slow connection. How do you stop it from affecting the worker?
   <details><summary>Answer</summary>The worker never talks to clients — it writes Postgres + publishes. The API keeps a bounded per-client queue; on overflow it drops and tells the client to resync via reconnect + `Last-Event-ID`.</details>
4. Three reasons for SSE over WebSockets here; one reason you might switch.
   <details><summary>Answer</summary>One-way append-only data; built-in reconnect/resume; plain HTTP through proxies/no extra protocol. Switch if you needed client→server streaming (interactive shells) — cancel is a POST, so no.</details>
5. Why is `useEffect` cleanup essential for `EventSource`?
   <details><summary>Answer</summary>Without `es.close()` on unmount/dependency change, streams leak: multiple open connections, interleaved logs, hitting the per-host connection limit.</details>
6. Intervals: why must you sort before merging, and by what?
   <details><summary>Answer</summary>Merging assumes each interval only needs comparing with the last merged one; that holds only when intervals are ordered by start. Sort by start (ties by end are irrelevant for merge).</details>

Pass gate is in the checkpoint file. Record in [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md).

## 14. Mastery checklist

- [ ] I can explain the log pipeline end to end and why persist-then-publish.
- [ ] My SSE endpoint resumes correctly and I have a property test proving it.
- [ ] I can defend SSE over WebSockets with trade-offs, including the auth wrinkle.
- [ ] I isolated slow clients from the worker and can describe the backpressure mechanism.
- [ ] The React UI shows list/detail/live log with idempotent state and cleanup; tests exist.
- [ ] 8 DP/interval problems done in Python; Java rep done; Mediums in ≤ 30 min in the mock.
- [ ] Checkpoint 16 completed and scored.

## 15. Expected deliverables

- `forgeci`: tag `m3`; `ui/` with tests; ADR 0002 final; `docs/SECURITY.md` (stream auth), `docs/ARCHITECTURE.md` (logs + SSE), `docs/FAILURES.md` (M3 set); screen capture.
- [`checkpoints/checkpoint-16.md`](../../checkpoints/checkpoint-16.md) score + remediation in [`weekly-progress.md`](../../trackers/weekly-progress.md).
- Trackers: [`project-tracker.md`](../../trackers/project-tracker.md), [`dsa-tracker.md`](../../trackers/dsa-tracker.md), [`technology-tracker.md`](../../trackers/technology-tracker.md) (React/TS second evidence, SSE, Redis pub/sub), [`interview-tracker.md`](../../trackers/interview-tracker.md) (mock #4).

## 16. If behind / stretch

**Behind?** Cut order: virtualised list (cap at 5,000 lines + "download full log") → build list polling (manual refresh) → multi-API-instance failure scenario → #253/#416. **Never cut** persist-then-publish, subscribe-before-replay + the property test, or `useEffect` cleanup — Checkpoint 16 tests exactly these.

**Ahead?** Add `GET /jobs/{id}/logs?after=<seq>&limit=` (plain JSON, for the Python analysis tool in M5 and for "download"); add ANSI-colour rendering in the log view; post GitHub commit statuses on job completion with a link to the build page; solve LeetCode 1143 (LCS) in Python as a 2-D DP preview for Week 17.
