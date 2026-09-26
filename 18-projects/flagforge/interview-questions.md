# FlagForge — Interview questions

> Practice out loud. Answer outlines are in `<details>` — write your own answer first, from your
> own code, then compare. Rehearsal method: [16-interview-prep/project-deep-dive.md](../../16-interview-prep/project-deep-dive.md);
> system-design method: [16-interview-prep/system-design-interview.md](../../16-interview-prep/system-design-interview.md);
> scoring: [INTERVIEW_CHECKLIST.md](../../INTERVIEW_CHECKLIST.md). Deep-dive rehearsal: Week 23;
> system-design mock #1: Week 22 — use "design a feature-flag service" from §5.
>
> These belong to **Track B** (software-engineering / résumé interview, Java + system design) —
> see the language strategy in [ROADMAP.md](../../ROADMAP.md). Your answers must match your code:
> numbers only from `docs/PERFORMANCE.md`.

Contents: [1 Basic](#1-basic) · [2 Intermediate](#2-intermediate) · [3 Advanced](#3-advanced) ·
[4 Deep-dive drill-downs](#4-deep-dive-drill-downs) · [5 System design](#5-system-design-design-a-feature-flag-service) ·
[6 Troubleshooting](#6-troubleshooting) · [7 Talk outlines](#7-talk-outlines)

---

## 1. Basic

**1.1 What is a feature flag and what problem does it solve?**
<details><summary>Outline</summary>
Runtime switch that decouples *deploy* (code is on the server) from *release* (users see it).
Enables dark launches, progressive rollouts, kill switches, targeting (betas, internal users),
and instant rollback without a build. Cost: flag debt and combinatorial test surface — mention
lifecycle/cleanup.
</details>

**1.2 Walk me through what happens when a developer flips a flag in your dashboard.**
<details><summary>Outline</summary>
Draft edited → `POST …:publish` with comment → transaction: new immutable `flag_config_version`
(N+1), environment version bump, audit event → after commit: snapshot rebuilt for the
environment, written to Redis, change event published → other API nodes evict local cache and
push `changed` over SSE → SDKs fetch `/sdk/v1/config` with `If-None-Match` → new snapshot
swapped atomically → next `isEnabled()` uses it. No DB on the SDK path.
</details>

**1.3 What is in an evaluation context?**
<details><summary>Outline</summary>
A required `key` (the entity being bucketed: user id, account id, device) plus typed attributes
(email, country, plan, app version). Rules match on attributes; rollouts bucket on `key` or a
`bucketBy` attribute. The SDK never sends it anywhere in local mode — privacy point.
</details>

**1.4 Why does the SDK evaluate locally instead of calling the server for each check?**
<details><summary>Outline</summary>
Latency (µs vs ms), availability (works when the server is down), cost (no request per check),
scale (fleet size doesn't add server load per evaluation). Trade-off: SDK holds the full rule set
(so server keys only in trusted services) and is eventually consistent (polling interval / SSE
delay).
</details>

**1.5 What is the difference between a server-side and a client-side SDK key?**
<details><summary>Outline</summary>
Server keys may fetch the whole config (rules, targeting lists) — trusted backends only. Client
keys (browser/mobile, visible) may only ask the server to evaluate for one context; they never
receive rules. Stored hashed; rotate with grace. The linter/dashboard warns when a server key is
about to be used in a client.
</details>

**1.6 What does "immutable versions" mean and why did you choose it?**
<details><summary>Outline</summary>
A published config is never modified; changes create N+1. Rollback = new version copying an old
one. Gives audit trail, reproducibility ("which config did user X see at 10:03?"), safe caching
(a version's content never changes → ETags are trivial), and concurrency safety. Enforced by DB
privileges (`REVOKE UPDATE, DELETE`), not just convention.
</details>

**1.7 Why Redis in this project?**
<details><summary>Outline</summary>
Shared cache of the environment snapshot across API nodes (so a publish on A is visible on B
without touching Postgres), plus pub/sub as the cross-node change signal. Not the source of
truth — Postgres is; Redis loss degrades to DB rebuild with single-flight.
</details>

**1.8 What does the SDK return if the flag does not exist?**
<details><summary>Outline</summary>
The caller's default, with reason `FLAG_NOT_FOUND`. Never throws. Same for wrong type,
not-ready client, and internal errors — the host application's request path must not depend on
the flag system being healthy.
</details>

---

## 2. Intermediate

**2.1 Why must bucketing be deterministic, and how did you implement it?**
<details><summary>Outline</summary>
Same user must see the same variation on every request, every node, every SDK — otherwise a 10 %
rollout flickers per request and raising to 20 % could drop users who were in. Implementation:
`bucket = first 4 bytes of SHA-256("flagKey.salt.userKey") as unsigned int mod 10000`; in rollout
if `bucket < pct×100`. Deterministic, stateless, uniform, monotonic under percentage increases,
identical in Java and Python. Per-flag salt decorrelates flags so the same 10 % don't get every
experiment. Frozen by contract vectors; changing the hash after release would reshuffle every
rollout in production.
</details>

**2.2 Why SHA-256 rather than `hashCode()` or MurmurHash3?**
<details><summary>Outline</summary>
`hashCode()`: JVM-specific semantics, poor distribution for similar keys, not portable to other
SDK languages. Murmur3: industry standard, fast, but multiple variants (seed, signedness,
`_fixed`) make cross-language ports error-prone and it is not in the JDK (SDK dependency
hygiene). SHA-256: in the JDK and Python stdlib, byte-identical everywhere, cryptographic
uniformity, ~1 µs — cost irrelevant at this call rate. Mention you'd pick Murmur if the SDK had
to evaluate millions/s per instance and you measured hashing as the bottleneck.
</details>

**2.3 Why polling first, then SSE?**
<details><summary>Outline</summary>
Polling + ETag is simple, stateless, proxy-friendly, and already gives bounded staleness; 304s
cost ~nothing. SSE adds seconds-level propagation (kill switches) at the cost of long-lived
connections, keepalives, reconnect logic, fan-out limits and pub/sub between nodes. Build the
simple one, measure, add streaming as an optimisation while keeping polling as the safety net.
SSE over WebSockets: one-directional, plain HTTP, auto-reconnect semantics, simpler infra — the
same reasoning as ForgeCI's log streaming.
</details>

**2.4 How does the SDK behave if the server dies?**
<details><summary>Outline</summary>
Timeline: evaluations keep using the in-memory snapshot (no change visible to callers); refresh
fails → exponential backoff with jitter, snapshot never cleared; after `staleAfter`, status
`STALE` with one WARN; on recovery, one fetch → ETag mismatch → new snapshot, status `READY`. If
the SDK was *started* while the server was down: `build()` returns after `initTimeout`, defaults
served with reason `CLIENT_NOT_READY`, background retries. 401 is different: stop polling, keep
stale, log ERROR once.
</details>

**2.5 Are all SDK instances consistent during a rollout?**
<details><summary>Outline</summary>
Per-user decision: yes (deterministic bucketing on the same version). Across time: eventually —
instance A may be on v57 and B on v58 for up to the polling interval (or ~2 s with SSE). So for a
few seconds the same user can get `on` from A and `off` from B if the request is load-balanced.
Mitigations: SSE, short intervals, sticky sessions, or accept it (most do). Never "fixed" by
coordination — that would put a network call on the hot path.
</details>

**2.6 Where could data be inconsistent in your system?**
<details><summary>Outline</summary>
(1) DB vs Redis snapshot after a failed after-commit rebuild → reconciler + lag metric.
(2) Redis vs node-local caches → TTL + pub/sub invalidation. (3) Server vs SDKs → polling/SSE
delay. (4) Between SDK instances → above. (5) Draft vs published → by design. (6) Auth cache vs
revoked key → up to 60 s, evicted via pub/sub. All are bounded, monotonic (env version) and
self-healing — say which ones are acceptable and why.
</details>

**2.7 Explain your cache hierarchy and what each level costs.**
<details><summary>Outline</summary>
In-process (Caffeine, µs, per node, 60 s refresh) → Redis (~0.3–1 ms RTT, shared) → Postgres
(ms–tens of ms, source of truth, single-flight so one rebuild per miss). Publish writes through
top-down. Give your measured p99 in each state.
</details>

**2.8 What is an ETag doing in a config API?**
<details><summary>Outline</summary>
Conditional GET: the SDK sends `If-None-Match: "<etag>"`; unchanged → `304` with no body. Turns
a 30 s poll from a 200 KB transfer into a ~200 B round trip. ETag = env version + content hash so
identical rebuilds still 304. Spring's `checkNotModified` handles the comparison.
</details>

**2.9 What is thread-safe about the SDK and how?**
<details><summary>Outline</summary>
Immutable snapshot records held in an `AtomicReference`; readers `get()` once per evaluation,
no locks; the poller builds a new snapshot and CAS-swaps if newer. Old snapshots are garbage
collected when no evaluation references them. One daemon scheduler thread; `close()` idempotent.
</details>

**2.10 Why is the evaluator a shared module and why is the Python SDK not sharing it?**
<details><summary>Outline</summary>
Server and Java SDK share `flagforge-common` so they cannot drift. Python cannot share Java
code, so it implements the *written spec* (`docs/EVALUATION.md`) and is checked against the same
JSON vectors. The spec + vectors are the real contract; sharing code is just one way to satisfy
it. Writing the Python version found spec gaps (encoding, rounding, numeric strings).
</details>

**2.11 What does your rollout simulator prove?**
<details><summary>Outline</summary>
Over N synthetic users: the share in rollout is within tolerance of the configured percentage
(uniformity of the hash+mod), raising the percentage keeps every previous user (stickiness /
monotonicity), and a sample cross-checked against the server's `/evaluate` matches (contract).
Unit tests do the same on small fixed inputs; the simulator does it on the *real* snapshot of
the real environment and produces a report for docs.
</details>

---

## 3. Advanced

**3.1 What happens at 100× the load?**
<details><summary>Outline</summary>
Read path: SDK count doesn't touch DB; add stateless API nodes; ETag keeps most polls as 304s;
put `/config` behind a CDN keyed by env + ETag; Redis read replicas. SSE: fan-out per node is the
limit — dedicated stream nodes, or WebFlux for the stream endpoint, cap per key, or push more
fleets to polling. Write path unchanged (publishes are rare). Postgres untouched on the hot path.
Cite your measured single-node numbers and where they broke.
</details>

**3.2 How would you make it multi-region?**
<details><summary>Outline</summary>
Control plane in one region (single Postgres primary). Data plane per region: API nodes + regional
Redis, fed by pub/sub/replication from the primary region; SDKs point at the nearest. Snapshots
are versioned and swaps are monotonic, so out-of-order or delayed propagation is safe.
Consistency = seconds; document the SLO. Kill switch latency across regions is the number to
measure.
</details>

**3.3 Cache stampede — define it in your system and how you protected against it.**
<details><summary>Outline</summary>
After a publish, every SDK is told to fetch at once; if node caches are cold and Redis is cold,
hundreds of requests can each rebuild from Postgres. Protections: write-through to Redis before
publishing the event; single-flight per node (one rebuild, others wait on the future); optional
Redis `SET NX` lock across nodes; jitter in SDKs; ETag so repeats are 304s. Give the measured
DB-rebuild counts with/without.
</details>

**3.4 The snapshot rebuild fails after the DB commit. What now?**
<details><summary>Outline</summary>
Dual-write problem: version exists, cache stale, and a *warm* stale Redis hides it forever.
Fix: a reconciler comparing `environment.current_env_version` with the Redis snapshot version
(cheap, self-healing), lag metric + alarm, publish response reporting snapshot status. Outbox is
the heavier alternative (did it in LedgerX).
</details>

**3.5 Two admins publish the same flag at the same moment.**
<details><summary>Outline</summary>
`SELECT … FOR UPDATE` on the draft row serialises them: v7 then v8, both audited; unique
constraint on (env, flag, version) as the last line. Optionally `If-Match` on the draft so the
second admin gets `412` because they edited a stale draft — UX decision, discuss.
</details>

**3.6 How do you prevent the SDK from harming the host application?**
<details><summary>Outline</summary>
No exceptions on the hot path, no blocking I/O per evaluation, bounded init, daemon threads,
one scheduler thread, bounded memory (snapshot size limits enforced server-side), no logging
storms (once per transition), no Spring/logging framework dependencies to avoid conflicts, small
API surface, semantic versioning.
</details>

**3.7 What is the security model, end to end?**
<details><summary>Outline</summary>
Dashboard: JWT, org roles, protected environments, audit on everything including denials,
tenant scoping returns 404. SDKs: per-env keys, server vs client kinds, hashed storage,
constant-time compare, rotation with grace, rate limits, TLS only, keys never logged. Data:
targeting lists are PII-ish — server keys only, never in client responses, snapshot size limits.
</details>

**3.8 What would you redesign with hindsight?**
<details><summary>Outline</summary>
Pick 2–3 real ones from your `docs/DESIGN_DECISIONS.md`: e.g. percentage stored as integer
hundredths from the start (rounding pain in the Python port); snapshot deltas or segments to cut
size if targeting lists grow; the stream endpoint on a non-servlet stack; SDK telemetry header
(version reporting) from day one; an approval step for protected environments. Show that each is
backed by a measurement or a failure exercise.
</details>

**3.9 Why is bucketing specified as a byte-level algorithm in a document instead of sharing code?**
<details><summary>Outline</summary>
Multiple languages will implement it (Java, Python, later TS). A shared library cannot cross
languages; what can is a precise spec (UTF-8 bytes of `flagKey.salt.userKey`, SHA-256, first 4
bytes big-endian unsigned, mod 10000, compare to integer hundredths) plus machine-checked
vectors. Anything ambiguous in the spec *will* be implemented differently — you found
encoding/rounding/`NOT_IN`-on-missing exactly that way.
</details>

**3.10 Compare SSE, WebSockets and long polling for this use case.**
<details><summary>Outline</summary>
Server→client only, low frequency, small events: SSE fits (HTTP, auto reconnect via `retry`,
proxies fine, text only). WebSockets: bidirectional, binary, more infra (upgrade, LB config) —
unnecessary. Long polling: simplest server, but holds a request per client and needs careful
timeouts; effectively what SSE does with less structure. HTTP/2 makes SSE connections cheap on
the client.
</details>

---

## 4. Deep-dive drill-downs

Interviewers pick one thread and pull. Prepare 3 levels for each.

| Thread | Level 1 | Level 2 | Level 3 |
|---|---|---|---|
| Bucketing | What is the formula? | Why salt per flag? Why mod 10000? | Prove monotonicity; what breaks if you change the hash; how the Python port is verified |
| Versioning | What is a version? | How is immutability enforced in Postgres? | Concurrent publish, rollback semantics, JSONB vs normalized ADR |
| Cache | Where is the snapshot cached? | What happens on Redis/Postgres loss? | Single-flight, `refreshAfterWrite` vs `expireAfterWrite`, measured p99 per level |
| Propagation | How do SDKs learn about changes? | Why thin event + ETag fetch? Why pub/sub between nodes? | Lost pub/sub message, fan-out ceiling measured, jitter, reconciler |
| SDK resilience | What if the server is down? | Init modes; stale vs default; 401 | Thread model, AtomicReference swap, monotonic clock for staleness, no-Spring policy |
| Security | How do SDKs authenticate? | Server vs client keys; hashing; rotation | Auth cache invalidation, rate limiting, PII in targeting lists, tenancy 404 |
| Testing | What kinds of tests? | Property/distribution tests; contract tests; WireMock faults | Cross-language vectors, stampede k6 scenario, fault-injection hook for after-commit failure |
| Measurement | What did you measure? | Setup, load, three cache states | What dominated; what you changed because of it; what you would not put on a résumé |

Practice: pick a row, set a 4-minute timer, go to Level 3 without notes.

---

## 5. System design: "Design a feature-flag service"

Use the method in [16-interview-prep/system-design-interview.md](../../16-interview-prep/system-design-interview.md);
this is the Week 22 mock. You have an unfair advantage — you built one — but *present it as a
design*, not a tour of your repo.

1. **Clarify (3 min):** users (engineers via dashboard; services via SDK); scale (flags per env,
   SDK instances, evaluations/s, publish rate — rare); latency goal for evaluation; propagation
   goal (kill switch < N s); consistency expectations; targeting needs (attributes, percentages,
   segments); multi-tenant?; languages of SDKs.
2. **Core entities & API (5 min):** org/project/env/flag; versioned config; rules; `/config`
   with ETag; `/evaluate`; `/stream`; admin publish/rollback. Draw the two planes.
3. **Evaluation (5 min):** order of evaluation; deterministic bucketing with salt; reasons.
4. **Data path (8 min):** Postgres as truth; snapshot per env; Redis + local cache; hot path
   never hits DB; SDK local evaluation; polling + ETag; SSE thin events; pub/sub between nodes;
   stampede protections.
5. **Failure modes (5 min):** Redis down, DB down, server down for SDKs, partial publish
   (reconciler), stale SDKs, key leak.
6. **Scale & extensions (4 min):** CDN for snapshots, stream nodes, multi-region, segments,
   scheduled rollouts, experimentation events, approvals.
7. **Trade-offs to volunteer:** eventual consistency between instances; full snapshot vs
   deltas; SDK holds rules (trust) vs server evaluation (latency); SSE fan-out cost.

Junior-level variant (15 min): entities, `/evaluate` endpoint, cache in front of DB, deterministic
percentage — then be pushed on "what if the cache dies" and "how do clients learn about changes".
See also [15-system-design/junior-design-problems.md](../../15-system-design/junior-design-problems.md).

---

## 6. Troubleshooting

Answer with **reproduce → observe → logs → diagnose → fix → regression test**
([failure-engineering.md](./failure-engineering.md)).

**6.1 "A user says the feature flickers on and off between page loads."**
<details><summary>Outline</summary>
Suspects: two SDK instances on different versions (propagation lag) → check `envVersion` in
reasons from both nodes; bucketing on a key that changes per request (anonymous id regenerated,
or `bucketBy` attribute missing → fallback differs); non-deterministic attribute (time-based).
Use `evaluate-debug` with the exact context from both requests.
</details>

**6.2 "We published the kill switch 40 seconds ago and service X still has it on."**
<details><summary>Outline</summary>
Check service X's SDK: polling interval and streaming flag; SSE connected to a node that missed
the pub/sub message (node logs `sse.broadcast env=… emitters=N`); SDK `STALE`/`UNAUTHORIZED`
(rotated key?); the snapshot lag metric (publish failed after commit → reconciler). Timeline
from `publish` log to SDK `snapshot loaded` log.
</details>

**6.3 "p99 of `/evaluate` spikes every minute."**
<details><summary>Outline</summary>
Local cache expiry with `expireAfterWrite` causing synchronous Redis/DB reload on the request
thread; fix with `refreshAfterWrite`; confirm with the `snapshot.cache miss` timestamps aligning
with spikes; k6 before/after.
</details>

**6.4 "After the deploy, 10 % rollouts are exactly the same users as the old 10 % of another flag."**
<details><summary>Outline</summary>
Salt missing or identical (default `""`, or salt not included in the snapshot → SDK evaluates
without it). Check `bucket_differentSaltDecorrelatesFlags`, snapshot JSON for `salt`, and the
contract vectors (they'd fail if the hash input changed).
</details>

**6.5 "Redis memory is climbing and snapshots are being evicted."**
<details><summary>Outline</summary>
`maxmemory-policy` should be `noeviction` for this workload (or a dedicated instance); look for
per-request keys without TTL (rate-limit buckets, auth cache) or snapshot keys per *version*
instead of per env. `redis-cli --bigkeys`, `INFO memory`.
</details>

**6.6 "The Python client and the Java client disagree for user `müller-42`."**
<details><summary>Outline</summary>
Encoding: `getBytes()` without charset vs UTF-8; confirm with the bucket vectors including
non-ASCII keys; fix Java to `UTF_8`, add the vector. Spec gap → spec fix → both SDKs.
</details>

**6.7 "The dashboard shows v58 as current but SDKs report v57 and never move."**
<details><summary>Outline</summary>
Partial publish (after-commit rebuild failed) with a warm stale Redis. Check `snapshot_lag`
metric and `snapshot.rebuild failed` log; reconciler should repair within 30 s — if it doesn't
exist yet, that is the fix.
</details>

**6.8 "Publishing takes 3 seconds since we added streaming."**
<details><summary>Outline</summary>
SSE broadcast on the request thread with slow consumers; move to an executor with per-send
timeout; measure publish latency vs emitter count.
</details>

---

## 7. Talk outlines

### 7.1 The 10–15 minute deep dive (no notes)

1. **Context (1 min):** "FlagForge is a feature-flag and progressive-rollout platform with a
   Java SDK; I built it in four weeks as the last of four projects, to go deep on caching, low
   latency and client-library design."
2. **Problem & shape (2 min):** deploy ≠ release; control plane vs data plane; the rule "hot
   path never touches Postgres". Draw the two-plane diagram.
3. **Versioning & audit (2 min):** immutable versions, DB-enforced; rollback = copy; publish
   transaction with `FOR UPDATE`.
4. **Evaluation (3 min):** order; deterministic bucketing with per-flag salt; reasons; the
   shared evaluator module; the written spec + JSON vectors that the Python SDK must also pass —
   and the spec gaps that surfaced.
5. **Caching & propagation (3 min):** Caffeine → Redis → DB with single-flight; ETag/304;
   polling then SSE with thin events; Redis pub/sub between nodes; stampede protection —
   measured numbers.
6. **SDK (2 min):** builder, AtomicReference swap, init modes, fallback table, no Spring,
   published to GitHub Packages; sample app demo story (server down → keeps serving).
7. **What broke & what I learned (1–2 min):** two failure exercises (e.g. partial publish hidden
   by warm Redis → reconciler; Python/Java disagreement on encoding → spec).
8. **What I'd do next (30 s):** segments, stream nodes, telemetry header.

### 7.2 The 2-minute version

"FlagForge is a feature-flag service plus a Java SDK. Teams define flags with targeting rules
and percentage rollouts per environment; every publish is an immutable version, so rollback is
a copy and everything is audited. The SDK downloads a snapshot of the environment's config,
evaluates flags locally in microseconds, and refreshes by polling with ETags or over SSE — a
publish reaches SDKs in about two seconds in my measurements. Percentage rollouts use a
deterministic SHA-256 bucket with a per-flag salt so a user's result is stable across every
node and SDK, and raising a percentage never removes anyone. The hot path never touches
Postgres: snapshots live in an in-process cache and Redis, with single-flight rebuilds and
stampede protection after a publish. The SDK has no Spring dependency, swaps snapshots
atomically, and keeps serving the last known config if the server dies. I also wrote a minimal
Python SDK against the same written evaluation spec and JSON test vectors, which is how I found
and fixed ambiguities in my own spec. It's deployed on AWS with CI publishing the server image
and the SDK artifact."

### 7.3 Résumé-defense pairings

| Résumé technology | Questions from this project | Reference |
|---|---|---|
| Java | `AtomicReference` CAS loop, records/sealed types, `HttpClient`, daemon executors, JMH | [17-resume-tech-defense/java.md](../../17-resume-tech-defense/java.md) |
| Spring Boot | after-commit hooks, `SseEmitter`, method security, ProblemDetail, Actuator groups | [17-resume-tech-defense/spring-boot.md](../../17-resume-tech-defense/spring-boot.md) |
| PostgreSQL | JSONB, `REVOKE` for immutability, `DISTINCT ON`, `FOR UPDATE` | [17-resume-tech-defense/postgresql.md](../../17-resume-tech-defense/postgresql.md) |
| Redis | snapshot cache, pub/sub, `SET NX PX` lock, token bucket, `noeviction` | [17-resume-tech-defense/redis.md](../../17-resume-tech-defense/redis.md) |
| REST | ETag/304, problem+json, action endpoints, OpenAPI as SDK contract | [17-resume-tech-defense/rest-apis.md](../../17-resume-tech-defense/rest-apis.md) |
| React/TS | rules editor state, diff view, JWT client | [17-resume-tech-defense/react.md](../../17-resume-tech-defense/react.md) |
| Python | typed SDK from a spec, pytest fixtures, simulator/linter CLIs | [17-resume-tech-defense/python.md](../../17-resume-tech-defense/python.md) |
| Docker / AWS / CI/CD | two-node Compose, GHCR + GitHub Packages, EC2/RDS, CloudWatch alarm on snapshot lag | [17-resume-tech-defense/docker.md](../../17-resume-tech-defense/docker.md), [17-resume-tech-defense/aws.md](../../17-resume-tech-defense/aws.md), [17-resume-tech-defense/cicd.md](../../17-resume-tech-defense/cicd.md) |
| JUnit | property/distribution tests, Testcontainers, WireMock faults, contract vectors | [17-resume-tech-defense/junit.md](../../17-resume-tech-defense/junit.md) |
