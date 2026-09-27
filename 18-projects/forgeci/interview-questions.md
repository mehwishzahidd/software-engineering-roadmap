# ForgeCI — Interview Questions

> Everything here is answerable **from what you built**. Answer out loud, without notes, with
> numbers from your own `PERFORMANCE.md` and stories from your own `FAILURE_ENGINEERING.md`.
> Outlines are in `<details>` — write your own answer first, then compare.
> Method: [`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md);
> scoring: [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md). This is **Track B** (Java /
> software-engineering interview), not the Python coding track.

Contents: [Basic](#basic) · [Intermediate](#intermediate) · [Advanced](#advanced) · [Deep-dive drill-downs](#deep-dive-drill-downs) · [System design](#system-design-design-a-ci-system) · [Troubleshooting](#troubleshooting-scenarios) · [Python tools](#python-tools-questions) · [Talk outlines](#talk-outlines)

---

## Basic

**1. What is ForgeCI, in two sentences?**
<details><summary>Outline</summary>
A self-hosted CI service: a GitHub push triggers a build that runs the repo's `.forgeci.yml` steps in a fresh Docker container on a pool of workers, streams logs live to a browser, and records results. Built to learn and defend queues, isolation, live streaming and failure recovery — not to compete with GitHub Actions.
</details>

**2. Walk me through what happens when I push a commit.**
<details><summary>Outline</summary>
Webhook → HMAC verify over raw body → dedupe on `X-GitHub-Delivery` (unique constraint) → fetch and validate `.forgeci.yml` → build/job/step rows in one transaction → `LPUSH` after commit → worker `BLMOVE` to its processing list + lease key → clone on host → container with limits → `exec` per step → log chunks to Postgres + Redis pub/sub → API SSE → browser → terminal state, ack, cleanup in `finally`. Thirteen steps; be able to say them in 60 seconds.
</details>

**3. Why is the worker a separate application?**
<details><summary>Outline</summary>
Blast radius (Docker socket only in workers; API faces the internet), independent scaling and placement, crash isolation. Shared `forgeci-common` module keeps entities and Redis key names single-sourced.
</details>

**4. What is a webhook and how do you know it really came from GitHub?**
<details><summary>Outline</summary>
HTTP POST from GitHub to my URL. `X-Hub-Signature-256` = HMAC-SHA256 of the raw body with a shared secret; I recompute and compare in constant time (`MessageDigest.isEqual`). No signature ⇒ 401. Proves integrity and that the sender knows the secret — not confidentiality (that's TLS).
</details>

**5. What is a container, in your own words? What isolates the job?**
<details><summary>Outline</summary>
A process tree with its own namespaces (pid, mount, net, …) and cgroup limits on the shared host kernel. Isolation: fresh filesystem from the image, bind-mounted workspace only, CPU/memory/pids limits, `cap-drop ALL`, `no-new-privileges`, no host network. Not a VM — kernel exploits escape it; real products use gVisor/Firecracker.
</details>

**6. Why Postgres *and* Redis?**
<details><summary>Outline</summary>
Postgres = durable source of truth (builds, jobs, logs). Redis = fast transient coordination: the queue (list), leases (TTL keys), counters (limits), pub/sub (live logs). If Redis is wiped, sweeps rebuild the queue from Postgres; nothing is lost.
</details>

**7. What is `.forgeci.yml` and how is it validated?**
<details><summary>Outline</summary>
`image`, `timeout`, `steps[].run`; later `jobs` with `needs`. SnakeYAML with `SafeConstructor` (untrusted input), mapped to records, a list of validation rules collecting all errors; invalid ⇒ build `FAILED (CONFIG)` with the messages shown in the UI, never queued.
</details>

**8. What do SUCCEEDED / FAILED / TIMED_OUT / CANCELLED / SKIPPED mean for a job?**
<details><summary>Outline</summary>
Terminal states: all steps exit 0; a step exit ≠ 0 (APP) or infra exhausted; wall-clock exceeded and container killed; user cancel; dependency failed (DAG). Non-terminal: QUEUED, LEASED, RUNNING (+ WAITING in DAG). Build status is derived from job statuses in one resolver.
</details>

**9. What is idempotency and where does ForgeCI need it?**
<details><summary>Outline</summary>
Same input applied twice has the effect of once. Webhook delivery (dedupe table), enqueue-after-commit + sweep (re-enqueue is harmless because CAS on lease), orphan recovery (CAS updates, safe if two API instances run it), DAG scheduling on duplicate events.
</details>

**10. Why SSE instead of polling for logs?**
<details><summary>Outline</summary>
Polling every second = N requests per viewer, latency ≥ interval, DB load for "anything new?". SSE pushes chunks as they arrive over one HTTP response, with browser-native reconnect and `Last-Event-ID`.
</details>

**10a. What is `BLMOVE` and why not `LPOP`?**
<details><summary>Outline</summary>
`BLMOVE src dst RIGHT LEFT timeout` atomically pops from one list and pushes to another, blocking until an element exists. `LPOP` removes the element with no trace: a crash after `LPOP` loses the job. With `BLMOVE` into `processing:<worker>` the job is always in exactly one list, so recovery has evidence to work from.
</details>

**10b. What is a lease, in one sentence, and what are the two numbers you chose?**
<details><summary>Outline</summary>
A time-limited claim on a job that the holder must keep renewing: TTL 30 s, heartbeat every 10 s (≥ 3 missed heartbeats before expiry, so a GC pause doesn't trigger recovery). Sweep every 15 s ⇒ worst-case detection ≈ 45 s.
</details>

**10c. What is the `finally` block in the executor responsible for?**
<details><summary>Outline</summary>
Stop/kill the container, remove it (force, with volumes), delete the workspace, release the concurrency slot, cancel the timeout watchdog, clear MDC — each in its own `try` so one failure doesn't skip the rest; never throws. Plus a startup sweep for anything a `kill -9` left behind.
</details>

**10d. How is the GitHub token stored and used?**
<details><summary>Outline</summary>
AES-256-GCM with a per-row random IV; key from env/SSM, never in the DB; decrypted only in the process that needs it (API for the Contents API and hook creation, worker for the clone); never sent to the UI, never inside the job container, masked in log chunks.
</details>

**10e. What is `ProblemDetail` and where does ForgeCI use it?**
<details><summary>Outline</summary>
RFC 9457 error body (`type`, `title`, `status`, `detail`, `instance`) supported natively by Spring 6; every 4xx/5xx from the API uses it with a `type` URI per error class (`/errors/invalid-signature`, `/errors/build-terminal`, `/errors/unknown-repository`).
</details>

---

## Intermediate

**11. Why do workers pull instead of the API pushing jobs to them?**
<details><summary>Outline</summary>
Pull = natural backpressure (a worker takes work only when it has a free slot), zero registration/discovery (start a process, it joins), no scheduler in the API, dead workers just stop pulling and leases expire. Push would require the API to track worker health/capacity and retry. It is the producer/consumer pattern with Redis as the shared blocking queue.
</details>

**12. Why Redis lists and not Kafka?**
<details><summary>Outline</summary>
Need a *work queue* (each job to one consumer, per-message ack/retry, cancel a queued item, blocking pop) at a few jobs/minute. Kafka is a partitioned log: partition = unit of parallelism, head-of-line blocking within a partition, no per-message ack semantics, heavy ops. Redis was already required for pub/sub and limits; one `BLMOVE` gives the semantics. At real scale the answer is SQS (managed visibility timeout + DLQ), not Kafka. The ROADMAP deliberately has no Kafka.
</details>

**13. Redis list vs Redis Streams — you chose the list. Defend it, and say when Streams wins.**
<details><summary>Outline</summary>
List + processing list + lease: I implement leases, heartbeats and recovery, so I can explain them; `LREM` cancels queued jobs; trivially inspectable. Streams: built-in pending-entries list and `XAUTOCLAIM`, better observability (`XPENDING`), consumer groups; wins when you want less custom recovery code or multiple consumer groups over the same events. Behind a `JobQueue` interface either is swappable.
</details>

**14. SSE vs WebSockets — the table.**
<details><summary>Outline</summary>
Unidirectional (logs only flow down; cancel is a POST), plain HTTP/1.1 (proxies, ALBs, cookies), auto-reconnect + `Last-Event-ID` for free, `SseEmitter` in Spring MVC. WebSockets: bidirectional, binary, no browser connection cap, but custom reconnect/resume and upgrade handling. Switch if I needed interactive terminals into containers.
</details>

**15. At-least-once vs exactly-once — which does ForgeCI provide and why?**
<details><summary>Outline</summary>
At-least-once *execution* (a job may start twice if a worker is slow/dead) with exactly-once *effect* on recorded state via fencing (`worker_id, attempt` CAS on every write; heartbeat refresh only if I still own the lease). Exactly-once execution across a network is impossible without coordination that would defeat the purpose; a CI job in a fresh container is safe to rerun.
</details>

**16. How do you distinguish an application failure from an infrastructure failure, and why does it matter?**
<details><summary>Outline</summary>
APP: a step's exit code ≠ 0 — deterministic, retry is pointless, no retry, red badge. INFRA: pull/create/start errors, daemon unreachable, worker lost, Redis lost — transient and ours, retry with backoff up to 3, then fail with INFRA. CONFIG: user's YAML/image — no retry. TIMED_OUT: no retry. Classifier is table-driven; unknown exceptions default to INFRA (retry) but log ERROR so I add a branch.
</details>

**17. What happens if Redis dies?**
<details><summary>Outline</summary>
API: webhooks still 200 (Postgres up), builds sit QUEUED; enqueue failures are recovered by the "QUEUED but not in Redis" sweep; SSE clients stall then replay from Postgres. Workers: `BLMOVE` fails → backoff and retry; heartbeats fail → if the outage outlasts the lease TTL the sweep requeues and the slow worker detects lost lease and abandons. Redis with AOF restores queue and leases on restart. Nothing durable lives only in Redis.
</details>

**18. What happens if a worker dies mid-job?**
<details><summary>Outline</summary>
Its heartbeats stop → lease key expires (30 s) → API sweep (15 s) finds the id in `processing:<worker>` without a lease → INFRA retry: `attempt+1`, SYSTEM chunk, `LPUSH` → another worker runs it; concurrency slot released; the orphaned container is cleaned by the worker sweep when that host is back. `docker kill` to reproduce; `docker pause` for the slow-worker variant.
</details>

**19. How do per-project concurrency limits work?**
<details><summary>Outline</summary>
Redis counter per repo; Lua check-and-increment (atomic; a GET+INCR pair races); if full the worker returns the job to the queue tail with a small delay and releases the lease; `DECR` in `finally` and in orphan recovery; TTL refreshed by heartbeat as a backstop for counter drift. Test: 50 threads, limit 5 → exactly 5 succeed.
</details>

**20. How does live-log replay work after a disconnect?**
<details><summary>Outline</summary>
Every chunk has a per-job `seq` persisted before publish; SSE `id:` = seq; browser reconnects with `Last-Event-ID`; server subscribes to the channel first (buffering), reads `seq > id` from Postgres, sends, drains the buffer skipping ≤ last, goes live; client also dedupes by seq. Contract test: 100 persisted, connect at 40, 2 live → receive 41..102 exactly once.
</details>

**21. Why chunk logs at 4 KB / 200 ms rather than per line?**
<details><summary>Outline</summary>
Write amplification: a chatty job can print 1,000 lines/s; per-line inserts + publishes would hammer Postgres and Redis. Chunking caps it at ~5 rows/s per job while keeping latency ≤ 200 ms. Must cut on UTF-8 boundaries. Measured chunks/s in `PERFORMANCE.md`.
</details>

**22. Why clone on the host rather than inside the container?**
<details><summary>Outline</summary>
The GitHub token never enters the untrusted container; user images don't need git; clone output is a SYSTEM chunk with the token masked. Trade-off: host needs git and network; the workspace is bind-mounted (path-mapping trap in Compose). Alternative documented in ADR-004.
</details>

**23. How is a running job cancelled on a different machine?**
<details><summary>Outline</summary>
API sets a Redis flag + DB `cancel_requested`, publishes on the worker's control channel, returns 202. Worker's CancelWatcher (pub/sub + 2-s poll fallback) stops the container (SIGTERM, 10 s, SIGKILL), executor throws `CancelledException` → CANCELLED, audit with actor. If the worker is dead, the recovery sweep sees `cancel_requested` and marks CANCELLED instead of retrying. Race with leasing a queued job resolved by CAS on status.
</details>

**24. What does graceful shutdown look like for a worker?**
<details><summary>Outline</summary>
SIGTERM → stop polling → wait up to grace for running jobs → leftovers: kill container, nack as INFRA "worker shutdown" → exit 0. Needs exec-form ENTRYPOINT so the JVM gets the signal and Compose `stop_grace_period: 90s`. Tested with a context close during a fake job.
</details>

**25. How did you test concurrency claims?**
<details><summary>Outline</summary>
Integration tests on Testcontainers: two `WorkerLoop`s, one job → executed once; 50 jobs / 4 slots → all once, max concurrency ≤ 4; limit atomicity under 50 threads; orphan sweep run twice concurrently acts once; webhook dedupe with 10 threads. Plus docker-tagged tests with real containers for timeout/cancel.
</details>

---

## Advanced

**26. 100× traffic tomorrow. What breaks first and what do you change?**
<details><summary>Outline</summary>
Order of pain: (1) worker capacity → add workers / autoscale on queue depth (pull model makes this free); (2) Docker Hub pulls → registry mirror, pre-pull; (3) `log_chunk` write volume → batch inserts already; partition by month, then stream finished logs to S3 and keep a hot tail; (4) SSE fan-out → one Redis subscription per job per API instance, N API instances behind an ALB, no sticky sessions needed; then a dedicated SSE gateway; (5) Redis single instance → Sentinel/managed; SQS for the queue; (6) Postgres → read replica for lists, PgBouncer. Quote your measured p95 and jobs/min as the baseline.
</details>

**27. Multi-region?**
<details><summary>Outline</summary>
Don't, until needed. If needed: region-local worker pools pulling from a region-local queue; control plane (Postgres) in one region with async replica; route webhooks to the primary; logs written regionally to S3, index in Postgres; accept cross-region latency for the UI. Redis pub/sub does not cross regions — SSE gateways per region subscribing locally. Honest answer: this is beyond what I built; here is how I'd reason.
</details>

**28. Security of running untrusted code — what did you do, what didn't you do?**
<details><summary>Outline</summary>
Did: fresh container per job, no privileged, cap-drop ALL, no-new-privileges, CPU/memory/pids limits, no host network, token never in container, socket only in worker, worker on its own instance, HMAC + rate limit on webhooks, encrypted tokens, least-privilege DB roles, body caps. Didn't: kernel-level isolation (gVisor/Firecracker), egress allow-lists, per-job disk quotas, secrets masking beyond the clone token. A malicious `.forgeci.yml` on my deployment can burn CPU for ≤ 60 min, hit the network, and attempt kernel exploits — it cannot reach Postgres/Redis (firewalled), cannot see tokens, cannot escape cgroups.
</details>

**29. Two API instances both run the orphan-recovery sweep. Problem?**
<details><summary>Outline</summary>
No — the sweep is idempotent: every action is a CAS update (`WHERE status IN (...) AND worker_id = ?`) plus `LREM`; the second runner updates 0 rows and skips. Tested (`recovery_runTwiceConcurrently_actsOnce`). A distributed lock (ShedLock) would be simpler to reason about but adds a dependency; I chose idempotency and proved it.
</details>

**30. The slow-worker problem (fencing). Explain it and your fix.**
<details><summary>Outline</summary>
`docker pause` a worker: heartbeats stop, lease expires, job requeued, worker-2 runs attempt 2, worker-1 wakes and finishes attempt 1. Without fencing: double terminal writes, seq collisions, counter drift. Fix: the attempt number is a fencing token — heartbeat refreshes only if the lease value is mine; terminal and chunk writes are CAS on `(worker_id, attempt)`; on lost lease the worker kills its container and abandons. Side effects of attempt 1 may briefly overlap — that is at-least-once, and jobs run in fresh containers so it is safe.
</details>

**31. How does DAG scheduling work and where does it live?**
<details><summary>Outline</summary>
Validator: Kahn's algorithm → levels, cycle detection naming members. Scheduler in the API: on a job's terminal event (Redis message) *and* a 30-s reconcile, lock the build's jobs, enqueue dependents whose deps are all SUCCEEDED, SKIP dependents of failures transitively, fail-fast cancels queued siblings. CAS updates make duplicate events harmless; reconcile makes lost events harmless. Workers stay graph-agnostic.
</details>

**32. Why persist a chunk before publishing it? What if you did it the other way?**
<details><summary>Outline</summary>
Pub/sub is at-most-once. Publish-then-persist: a client could see a chunk that then fails to persist; a reconnect would replay without it — inconsistent history. Persist-then-publish: a client may miss a live chunk if the API is down, but replay from Postgres is complete. Small latency cost (one insert) acceptable; batching amortizes it.
</details>

**33. What would you redesign?**
<details><summary>Outline</summary>
Pick 2–3 honest ones: (1) log storage — go to S3 + hot tail from day one instead of only Postgres; (2) `SseEmitter` on servlet threads → WebFlux or virtual threads measured, for fan-out; (3) the queue behind an interface was right, but I'd start on Streams for the pending-entries observability; (4) API-side scheduling for DAGs is fine, but a dedicated scheduler component would separate concerns; (5) per-step containers with a cached layer for dependency install. Also what I'd keep: pull model, fencing, taxonomy.
</details>

**34. Where is the single point of failure in your AWS deployment?**
<details><summary>Outline</summary>
Redis on the API instance (and the API instance itself). Mitigations documented: AOF, restart policy, sweeps rebuild queue from Postgres; next step Sentinel/ElastiCache and 2 API instances behind an ALB. RDS is managed. Workers are disposable.
</details>

**35. How do you know your numbers are real?**
<details><summary>Outline</summary>
Protocol in `PERFORMANCE.md`: environment (machine, Docker version, worker count, limit), workload (variant, count, rate), warm-up discarded, 3 runs, p50/p95 computed by the Python analyzer from persisted timestamps (`started_at − queued_at`), cross-checked with the simulator's own client-side measurement. Every résumé bullet links to the table.
</details>

---

## Deep-dive drill-downs

Interviewers pull one thread until it snaps. Practise each chain until you can go five levels deep.

**Chain A — the queue**
1. How does a worker get a job? → `BLMOVE`.
2. What if it crashes right after? → processing list + lease TTL.
3. What if it is only slow? → fencing on `(worker_id, attempt)`; lost-lease abandon.
4. What if Redis loses the lease keys (restart without AOF)? → sweep grace window after Redis/API start; DB `lease_expires_at` as second source.
5. What if two API instances sweep? → idempotent CAS.
6. Why not SQS from the start? → local dev + learning; interface makes it a transport swap.

**Chain B — logs**
1. How do logs get to the browser? → chunk → Postgres → PUBLISH → SSE.
2. Reconnect? → `Last-Event-ID` replay.
3. Race between replay and live? → subscribe-before-read + client dedupe.
4. Slow client? → per-client bounded queue, drop and let it reconnect.
5. 10,000 viewers? → one subscription per job per instance, SSE gateway tier, HTTP/2.
6. Multi-byte characters? → UTF-8 boundary in the chunker; test with `é`.

**Chain C — isolation**
1. Where does user code run? → temp container on the worker host.
2. What can it do? → CPU/mem/pids bounded, network out, no tokens, no socket.
3. Can it escape? → kernel exploit; namespaces are not VMs.
4. What would a real product do? → gVisor/Firecracker/Kata, rootless, egress policies.
5. Why does the worker have the socket at all? → it must create containers; hence a dedicated instance.

**Chain D — failure taxonomy**
1. Test fails — retried? → No, APP.
2. Image pull 429 — retried? → Yes, INFRA with backoff.
3. Image 404? → CONFIG, no retry.
4. Worker killed? → INFRA via sweep.
5. Timeout? → No retry; why (hangs repeat).
6. Unknown exception? → INFRA by default + ERROR log; explain the fail-safe choice.

**Chain E — DAG**
1. How is order decided? → Kahn levels at validation, event-driven scheduling at runtime.
2. Lost completion message? → reconcile.
3. Duplicate message? → CAS.
4. One of two parallel jobs fails? → skip dependents, fail-fast cancels queued siblings, running finish.
5. Dependency retried (INFRA)? → dependents wait for the final attempt.

---

## System design: "Design a CI system"

Answer it *with ForgeCI* — you have built the reference solution. Structure
([`16-interview-prep/system-design-interview.md`](../../16-interview-prep/system-design-interview.md), [`15-system-design/junior-design-problems.md`](../../15-system-design/junior-design-problems.md)):

1. **Clarify** (2 min): triggers (push/PR/manual), config in repo, isolation requirement, live logs, scale (jobs/day, concurrent), retention, multi-tenant?
2. **Core flow** (3 min): draw README §8. Webhook → API → DB → queue → workers → containers → logs → UI.
3. **Data model** (2 min): repository, build, job, step, log_chunk, worker; state machine.
4. **Key decisions** (5 min): pull-based workers; queue semantics (at-least-once, leases, fencing); failure taxonomy; SSE with replay; isolation model.
5. **Failure modes** (3 min): worker death, slow worker, Redis loss, API restart, duplicate webhooks — each with the mechanism.
6. **Scale** (3 min): the §28 table — workers, logs to S3, SSE gateway, SQS, Redis HA.
7. **Security** (1 min): untrusted code, sockets, tokens.
8. **What I'd cut / add** (1 min).

Variants to practise: "design a job scheduler", "design a webhook ingestion service", "design a live log viewer", "design a rate-limited worker pool". Each is a subset of ForgeCI.

---

## Troubleshooting scenarios

Interviewers describe a symptom; you diagnose out loud. Tie each to `failure-engineering.md`.

| Symptom | Where you look first | Likely cause (from your own runs) |
|---|---|---|
| A build shows two jobs RUNNING for the same job name | `processing:*` lists, `job.worker_id/attempt`, heartbeat logs | Slow worker after lease expiry without fencing (scenario 14) |
| Logs appear only when the job finishes | `curl -N` direct vs via nginx; compression config | Proxy/compression buffering (scenario 12, M3 debug #1) |
| Builds stuck QUEUED, workers idle | `LLEN queue:jobs` = 0 but DB says QUEUED | Enqueue failed after commit (Redis blip); missing-enqueue sweep (scenario 6) |
| Every job on worker-1 fails INFRA, worker-2 fine | worker-1 `/actuator/health` | Docker unreachable or disk full; health gate should have drained it (9, 16) |
| GitHub shows red deliveries but builds exist | delivery response times | Endpoint > 10 s (GitHub fetch inside), redeliveries deduped (1) |
| Job CANCELLED in DB, container still running | worker logs for "cancel observed" | Cancel during clone/pull ignored; watcher started too late (5) |
| Limit counter at max, nothing runs | `GET limit:<repo>`; recovery logs | Release skipped on a path; TTL absent (M4 debug #2) |
| SSE works locally, dies after 60 s on AWS | ALB/nginx idle timeouts | No `: ping` heartbeat (12) |
| Duplicate lines after reconnect | replay query | `>=` instead of `>` (8) |
| DAG build never finishes, all WAITING | scheduler logs after API restart | Lost completion event, no reconcile (M6 debug #1) |
| Worker exits instantly on `compose stop`, jobs lost | Dockerfile ENTRYPOINT form | `sh -c` swallows SIGTERM (M4 debug #4) |
| `�` in logs | chunker | Cut mid-UTF-8 (M3 debug #6) |

---

## Behavioral questions with ForgeCI stories

Prepare each as a STAR story ([`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md)); the *result* must be a real outcome from your repo.

| Prompt | Story candidate from ForgeCI |
|---|---|
| "Tell me about a hard bug." | The `docker pause` double-execution (scenario 14): symptoms, how you found it (`processing:*` lists showed the id twice-owned), the fencing fix, the regression test. |
| "A time you had to make a trade-off." | Redis list vs Streams vs SQS — chose the list for learning and cancelability, wrote the ADR, kept the interface swappable. |
| "A time you cut scope." | Deferring OAuth to M6 behind PAT, or dropping the stretch tier to protect tests and docs (ROADMAP rule: cut features, never quality). |
| "Something you learned from a failure." | Recovery sweep marking live jobs orphaned after a Redis restart without AOF → grace window + AOF. |
| "How do you make sure your work is correct?" | Named concurrency tests, Testcontainers, docker-tagged suite, the 16-scenario chaos loop, cross-language HMAC vector. |
| "Explain something technical to a non-technical person." | The lease: a library book due date you have to renew; if you stop renewing, someone else can take the book. |

## Questions you should ask back

- "How do you run untrusted or third-party code in your CI/build systems — containers, microVMs, something else?"
- "What does your on-call see most often from your job/queue systems — lost work, duplicates, or backlog?"
- "Do you use SQS/Kafka/Redis for internal queues, and how did you land there?"
- "How do you measure and review performance claims internally?"

## Red-flag answers to avoid

| Don't say | Say instead |
|---|---|
| "It's exactly-once." | "At-least-once execution, exactly-once *effect* via fencing on the attempt number." |
| "Containers are secure sandboxes." | "Process isolation, not a VM; here's what I limited, here's what escapes it, here's what real products add." |
| "Kafka would be overkill" (and stop) | Explain the semantic mismatch (partition = parallelism, no per-message ack, head-of-line blocking), then the ops cost. |
| "I got 500 jobs/min." | "On a 4-core laptop with 4 workers × 2 slots running a no-op step, median of three runs was N jobs/min; the bottleneck was container start." |
| "Redis is the database." | "Postgres is the source of truth; Redis holds transient coordination state and can be rebuilt." |
| "WebSockets are better but SSE was easier." | The table: unidirectional data, HTTP/1.1 friendliness, built-in reconnect with `Last-Event-ID`. |

## Python tools questions

**36. What is the Python component and why Python?**
<details><summary>Outline</summary>
`tools/`: repo generator (variants of `.forgeci.yml`, local git repos, optional GitHub push), signed-webhook load simulator (rate, duplicates, polls to completion, p50/p95), result/log analyzer (failure-taxonomy stats, percentiles, Maven test summaries). Python because it is the right tool for glue and measurement: `hmac`, `subprocess`, `statistics`, `psycopg`, pytest — fast to iterate, easy to read. Typed (`mypy --strict`), tested, in CI.
</details>

**37. How do you guarantee the simulator's signature matches the Java verifier?**
<details><summary>Outline</summary>
Same test vector (secret, body, expected hex) asserted in `SignatureVerifierTest` and `test_sign_matches_known_vector`; the simulator signs the exact bytes it sends; a live contract test `test_signed_payload_accepted_by_api` posts one payload and expects 200, resends for dedupe 200, corrupts for 401.
</details>

**38. How does the simulator measure queue wait, and why cross-check with the analyzer?**
<details><summary>Outline</summary>
Server-side timestamps `queued_at`/`started_at` from the job API (not client clocks). The analyzer recomputes from Postgres over the same window; agreement within rounding proves neither tool is lying (different code paths, same data).
</details>

---

## Talk outlines

### 10–15 minute deep dive (no notes)

| Min | Section | Say |
|---|---|---|
| 0–1 | Hook | "ForgeCI is a CI system I built to learn how CI systems actually fail: queues, isolation, live logs, recovery." Stack in one breath. |
| 1–3 | Flow | The 13 steps as a story of one push, drawing the §8 diagram. |
| 3–5 | Decision 1: pull-based workers + Redis list queue | Why pull; `BLMOVE` + processing list + lease; why not Kafka; Streams/SQS alternatives. |
| 5–7 | Decision 2: at-least-once with fencing | Worker death vs slow worker; `docker pause` story; attempt as fencing token; the test that proves it. |
| 7–9 | Decision 3: failure taxonomy | APP/INFRA/CONFIG/TIMEOUT/CANCELLED table; what each does to retry; the classifier default. |
| 9–11 | Decision 4: SSE with replay | Chunking, persist-then-publish, `Last-Event-ID`, the subscribe-before-read race, contract test. |
| 11–12 | Isolation and security | What a job can and cannot do; socket warning; what real products do. |
| 12–13 | Numbers | Queue wait p50/p95 with 1/2/4 workers, jobs/min, chunks/s — from `PERFORMANCE.md`. |
| 13–14 | DAG + AWS | Kahn, event + reconcile, fail-fast; two instances, RDS, SQS considered. |
| 14–15 | What I'd change | Two honest items; invite questions. |

### 2-minute version

> "ForgeCI is a distributed CI service: a GitHub push hits a webhook I verify with HMAC and dedupe on the delivery id, becomes a build in Postgres, and is queued in Redis. Workers pull jobs with `BLMOVE` into a per-worker processing list and hold a lease they heartbeat; each job runs in a fresh Docker container with CPU, memory and time limits, and the output streams as chunks through Postgres and Redis pub/sub to the browser over SSE, with replay on reconnect. The interesting engineering is in the failure paths: a dead worker's job is recovered by lease expiry and retried as an infra failure, while a test failure is never retried; a slow worker can't corrupt state because every write is fenced by the attempt number; Redis going away pauses live logs and queueing but loses nothing because Postgres is the source of truth. I load-tested it with a Python simulator that fires signed webhooks: with 4 workers on my laptop the queue wait p95 was <your number> and throughput <your number> jobs/min. The advanced version schedules DAGs of jobs with Kahn's algorithm and runs on AWS with workers on a separate instance because they hold the Docker socket."

Fill the two blanks from your own measurements — never from memory of what "should" be true.

Cross-references: [`README.md`](./README.md) (decisions §9–§10, taxonomy §25, scale §28), [`failure-engineering.md`](./failure-engineering.md), [`docs-and-resume.md`](./docs-and-resume.md), [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md), [`17-resume-tech-defense/redis.md`](../../17-resume-tech-defense/redis.md), [`17-resume-tech-defense/docker.md`](../../17-resume-tech-defense/docker.md), [`17-resume-tech-defense/python.md`](../../17-resume-tech-defense/python.md).
