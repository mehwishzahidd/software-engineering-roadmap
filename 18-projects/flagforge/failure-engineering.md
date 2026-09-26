# FlagForge — Failure engineering

> **Break → Debug → Explain.** Every scenario follows the same discipline:
> **reproduce → observe → inspect logs → diagnose → fix / design around → regression test.**
> Do each one on your running stack (Compose, two server nodes, sample app, Python SDK client),
> write the outcome in `docs/FAILURE_ENGINEERING.md` in your repo (date, command, what you saw,
> what you changed), and turn the good ones into interview stories. Scenarios 1–3 and 7 are
> mandatory for v1.0 ([milestones.md M4.2](./milestones.md#m42-requirements)); do the rest in M4 or
> polish weeks.

Prerequisites: [README §7](./README.md#7-architecture) (architecture), [§13.4](./README.md#13-sdk-design)
(SDK fallback rules), [§14](./README.md#14-propagation-design) (propagation), the log lines in
[§19](./README.md#19-logging-and-error-handling). Theory: [15-system-design/caching.md](../../15-system-design/caching.md),
[04-sql-databases/redis.md](../../04-sql-databases/redis.md), [14-cs-fundamentals/networking.md](../../14-cs-fundamentals/networking.md).

| # | Scenario | Component | Milestone |
|---|---|---|---|
| 1 | [Redis unavailable (server)](#1-redis-unavailable-server) | server cache | M2 |
| 2 | [SDK cannot contact server at startup](#2-sdk-cannot-contact-server-at-startup) | SDK | M3 |
| 3 | [SDK loses connectivity mid-run](#3-sdk-loses-connectivity-mid-run) | SDK | M3 |
| 4 | [Stale configuration — SDK behind by N versions](#4-stale-configuration--sdk-behind-by-n-versions) | SDK + server | M3/M4 |
| 5 | [Conflicting rollout rules (overlapping priorities)](#5-conflicting-rollout-rules-overlapping-priorities) | evaluator + linter | M2/M4 |
| 6 | [Configuration update during evaluation (snapshot swap atomicity)](#6-configuration-update-during-evaluation-snapshot-swap-atomicity) | SDK | M3 |
| 7 | [Cache stampede after publish](#7-cache-stampede-after-publish) | server | M4 |
| 8 | [SSE fan-out overload](#8-sse-fan-out-overload) | server | M4 |
| 9 | [Corrupted snapshot JSON](#9-corrupted-snapshot-json) | server + SDK | M2/M3 |
| 10 | [Clock skew and TTLs](#10-clock-skew-and-ttls) | SDK + server | M3/M4 |
| 11 | [SDK key leaked — rotation](#11-sdk-key-leaked--rotation) | server + SDK | M4 |
| 12 | [Postgres unavailable](#12-postgres-unavailable) | server | M2 |
| 13 | [Cross-SDK disagreement (Java vs Python)](#13-cross-sdk-disagreement-java-vs-python) | contract | M3 |
| 14 | [Publish partially failed (version written, snapshot not)](#14-publish-partially-failed-version-written-snapshot-not) | server | M2/M4 |

---

## 1. Redis unavailable (server)

**Reproduce.** Stack up, sample app running, k6 or a loop of `curl /sdk/v1/evaluate` at ~50 req/s.
`docker compose stop redis`. Wait 90 s (past the local-cache TTL). Publish a flag from the
dashboard. Then `docker compose start redis`.

**Observe.** Expected: `/evaluate` never fails. Latency: unchanged while the local cache is warm;
after TTL, one request pays a DB rebuild (tens of ms) while others wait on single-flight; then
normal again. Publish returns 201 (slower — the write-through is skipped) and node B does *not*
learn about it until its local TTL expires (pub/sub is down). What you might actually see the
first time: every request takes 2 s (Redis command timeout on the request path), or 500s
(`RedisConnectionFailureException` escaping), or readiness *and* liveness failing so the
orchestrator restarts a perfectly capable node.

**Inspect logs.** `docker compose logs -f server server-2 | grep -E "redis|snapshot"`. You want exactly:
`redis.unavailable falling back to local/db op=GET key=snapshot:…` once per circuit-open period
(not per request), `snapshot.cache miss level=redis env=… ms=…` once per TTL per node,
`publish … pubsub=skipped`. Actuator: `/actuator/health/readiness` shows `redis: DOWN`;
`/actuator/health/liveness` stays `UP`.

**Diagnose.** Trace the request path: does `SnapshotCache.get` catch `RedisConnectionFailureException`
and fall through? Is the Lettuce command timeout ≤ 200 ms? Is there a short-circuit so that once
Redis has failed you stop trying for a few seconds? Is `RedisHealthIndicator` wired into liveness
(default groups may include it — configure `management.endpoint.health.group.liveness.include`).
Does the publish path treat `SET`/`PUBLISH` failure as non-fatal (`try/catch` + warning) after the
transaction committed?

**Fix / design around.**
- Command timeout 200 ms; circuit-breaker flag (`redisDownUntil` timestamp) so the hot path skips Redis for 5 s after a failure.
- Cache hierarchy with single-flight DB rebuild; local cache uses `refreshAfterWrite` so expiry doesn't block readers.
- Publish: write-through and pub/sub are best-effort *after commit*; the DB version is the source of truth; node B self-heals via TTL and SDKs via polling.
- Liveness excludes Redis; readiness includes it only if you want the LB to route around a Redis-less node (debatable — write the reasoning in `docs/DESIGN_DECISIONS.md`).

**Regression test.** `snapshot_redisDown_fallsBackToDbOnce` and `redisDown_publishStillSucceedsAndLogsWarning`
([milestones M2.6, M4.6](./milestones.md)). Add a latency assertion: with Redis stopped, p99 of 100
sequential `/evaluate` calls < 50 ms after the first rebuild.

---

## 2. SDK cannot contact server at startup

**Reproduce.** `docker compose stop server server-2` (or point the sample app at a wrong port /
a black-hole IP such as `10.255.255.1` to get a *connect timeout* rather than a refusal — both
matter). Start the sample app and the Python client.

**Observe.** Expected: `build()` returns after `initTimeout` (5 s) with status `INITIALIZING`; the
app prints `new-checkout=false (CLIENT_NOT_READY)`; no stack trace; poller keeps retrying with
backoff 1, 2, 4 … 60 s; when the server returns, values appear within one backoff step, status
`READY`. Compare refused (fast failure) vs black-hole (waits the full connect timeout each try).

**Inspect logs.** SDK: `flagforge: initial snapshot fetch failed (ConnectException …) — continuing
with defaults, retry in 1s` at WARN once; DEBUG lines per retry; `flagforge: snapshot loaded
envVersion=57 flags=12` on recovery. Python: same shape via `logging`.

**Diagnose.** Does `build()` throw? (It must not for network errors.) Does the sample app's
`main` block longer than `initTimeout`? (Connect timeout > init timeout ⇒ yes — the future's
`get(timeout)` is what bounds it, not the HTTP timeout.) Is the backoff really exponential and
capped, with jitter? Is the retry thread a daemon?

**Fix / design around.** Bound `build()` by `initTimeout` regardless of HTTP timeouts; document
non-blocking init (`initTimeout(ZERO)`) for workers; log once per state transition, not per
retry; provide `bootstrap(json)` so a deployment can ship a last-known snapshot file and start
`READY` even with the server down [ADV].

**Regression test.** `sdk_serverDown_returnsDefaultAndUsesStaleCache` (first half),
`sdk_initTimeout_returnsClientNotThrowing`, `sdk_backoff_growsAndCaps`; Python
`test_client_server_down_returns_default_then_stale`.

---

## 3. SDK loses connectivity mid-run

**Reproduce.** Sample app running with values loaded. Option A: `docker compose stop server server-2`.
Option B (more realistic — packets dropped, not refused): `docker network disconnect flagforge_default flagforge-sample-app-1`.
Option C: `docker compose pause server` (TCP connections hang). Run each for 3 minutes, then restore.

**Observe.** Expected: evaluations continue with the last snapshot (`reason` unchanged); after
`staleAfter` (3 × interval) status `STALE` and exactly one WARN; SSE mode: watchdog notices 45 s of
silence, closes, reconnects with backoff; polling mode: failures with backoff. On restore: one
INFO `recovered`, status `READY`, and any missed publish is fetched (ETag mismatch → 200).

**Inspect logs.** Count WARN lines — if there is one per retry you are spamming the host app's
logs (a real complaint about real SDKs). Check the paused case: does a read hang forever? The
`readTimeout` on `HttpRequest` must bound it; for SSE, the watchdog must bound it because the
stream legitimately has no body for 20 s at a time.

**Diagnose.** No `readTimeout` → thread stuck → no polling at all → `STALE` forever even after the
server returns. SSE without watchdog → the same. Backoff without jitter → thundering herd on
restore (all SDKs reconnect in the same second — combine with scenario 7).

**Fix / design around.** Timeouts on every request; SSE silence watchdog; jittered backoff;
"stale is a state, not an error" — expose `status()` and a `flagforge_sdk_status` gauge if the
host uses Micrometer [ADV]; never clear the snapshot on error.

**Regression test.** `sdk_serverDown_returnsDefaultAndUsesStaleCache` (second half),
`sdk_readTimeout_respected`, `sse_silence45s_triggersReconnect`, `sse_serverRestart_reconnectsWithBackoff`.

---

## 4. Stale configuration — SDK behind by N versions

**Reproduce.** Set the sample app's polling interval to 5 min, streaming off. Publish five
versions in a row (10 % → 20 % → kill switch → rollback → 50 %). Watch the app for 5 minutes.
Variant: SSE on, but `docker compose pause server-2` (the node the app is connected to) while
publishing on `server`.

**Observe.** The SDK jumps from v57 directly to v62 — it never sees v58–61. That is by design
(**snapshots are whole states, not deltas**; only the latest matters). But the kill switch (v59)
was invisible for up to 5 minutes: quantify "propagation delay" honestly as *interval + fetch*
worst case. In the SSE variant, the app connected to the paused node sees nothing until its
safety-net poll (5 min) or the watchdog reconnect (45 s) lands it on the healthy node.

**Inspect logs.** Server: `publish env=prod flag=new-checkout v=59 by=…` timestamps. SDK: the
`snapshot loaded envVersion=62` timestamp. Difference = your measured propagation delay for this
configuration. Record it in `docs/PERFORMANCE.md` next to the SSE number.

**Diagnose.** Is `swapIfNewer` comparing `envVersion` (monotonic per environment) and not
`flagVersion`? Can a slow response deliver v58 *after* v62 arrived via SSE (out-of-order) and
regress you? (Only if the guard is missing.) Does the reason include `envVersion` so support can
tell which version a decision was made on?

**Fix / design around.** Monotonic `envVersion` guard; `envVersion` in every evaluation reason;
document the operational rule: "kill switches propagate within the polling interval — use SSE or
a ≤ 30 s interval for prod services"; expose `snapshotVersion()` and a `snapshot age` gauge; the
dashboard could show "SDKs on old versions" if the SDK reported its version on each poll
(`X-FlagForge-Snapshot-Version` header — cheap telemetry [ADV]).

**Regression test.** `sdk_outOfOrderResponse_isIgnored`; a propagation measurement script
(Python, `tools/`) that publishes, then polls the sample app's `/status` and records delta —
20 trials → table.

---

## 5. Conflicting rollout rules (overlapping priorities)

**Reproduce.** In `prod`, create rules: p0 `country IN [DE]` → 10 % rollout; p1 `plan EQUALS pro`
→ fixed `on`. Evaluate `{"key":"u1","country":"DE","plan":"pro"}`. The product owner expected
"all pro users get it"; 90 % of German pro users get `off`. Second variant: import a draft JSON
with two rules at priority 3 (bypassing the UI).

**Observe.** `evaluate-debug` returns `RULE_MATCH r0, inRollout=false`, `bucket=7321`. This is
*correct per spec* and *wrong per intent*. The duplicate-priority variant: the server must reject
it with `422 invalid-rule` (duplicate priority); if it doesn't, results depend on JSON array
order — which happens to be stable, but nothing guarantees it after a UI reorder.

**Inspect logs.** Nothing is wrong in the logs — that is the point. The only signal is the
reason payload. Make the dashboard's "test rules" panel show the *whole evaluation trace*
(each rule: matched / not matched / which condition failed), not just the final reason.

**Diagnose.** First-match semantics + an overlapping, more general rule earlier in the list.
Determinism means the same "wrong" answer forever, which is better than random — but the
configuration needs linting.

**Fix / design around.** Enforce unique priorities (`422`). Implement the shadowed/overlap
warnings in `ConfigValidator` and `tools/flagconf_lint.py` ([milestones M4.3](./milestones.md#m43-architectural-guidance))
and surface them in the publish dialog. Document in `docs/EVALUATION.md` that specific rules
belong *above* general ones. Do **not** invent "most specific wins" — explain in an ADR why
predictability beats cleverness.

**Regression test.** `rules_higherPriorityWins`, `publish_invalidRule_returns422WithAllErrors`
(duplicate priority case), `lint_detectsShadowedRule`, Python `test_lint_shadowed_rule_superset_equals_in`,
and an evaluator trace test asserting the per-rule outcome list.

---

## 6. Configuration update during evaluation (snapshot swap atomicity)

**Reproduce.** A JMH-style or plain stress harness in `flagforge-sdk` tests: 8 threads call
`isEnabledDetail("f", ctx)` in a loop for 10 s; a 9th thread calls `swapIfNewer` with alternating
snapshots every millisecond, where snapshot A has flag `f` with 1 rule and salt `s1`, snapshot B
has 3 rules and salt `s2`. Each result must be *entirely* from A or *entirely* from B (check
`reason.envVersion` against `reason.ruleId`/bucket consistency).

**Observe.** With `AtomicReference` + immutable snapshot: zero mixed results, zero exceptions, no
lock contention (JFR shows no monitor waits). If your snapshot is *mutable* (e.g. a
`HashMap<String, FlagConfig>` updated in place, or a `List<Rule>` sorted in place by the
evaluator!), you will see `ConcurrentModificationException`, or results that use A's salt with
B's rules — silent wrong answers, which are worse.

**Inspect logs.** None — this class of bug produces no logs. The harness's mixed-result counter is
the only observation. Also run with `-XX:+UseSerialGC`/small heap to confirm you aren't leaking
old snapshots (an old snapshot must be collectable as soon as no evaluation references it).

**Diagnose.** Search the evaluator for any mutation: `sort()`, `put()`, lazy caches inside
`FlagConfig`. Records with `List.copyOf`/`Map.copyOf` at construction eliminate the class.
The evaluator must read `holder.current()` **once** per evaluation and pass that object down —
reading the reference twice inside one evaluation reintroduces the race.

**Fix / design around.** Immutable snapshot types (records + unmodifiable collections, sorted
at build time); single read of the reference per evaluation; `swapIfNewer` CAS loop; no
synchronized on the hot path. Python: assignment is atomic, but `swap_if_newer` needs a lock for
compare-then-set; evaluation reads `self._snapshot` once into a local.

**Regression test.** `sdk_concurrentSwap_neverMixesSnapshots` (the harness above, 5 s in CI),
`snapshot_typesAreImmutable` (reflection: every field of the records is final and collections
are unmodifiable — `assertThrows(UnsupportedOperationException.class, () -> snap.flags().put(...))`).

---

## 7. Cache stampede after publish

**Reproduce.** k6 scenario `perf/stampede.js`: 500 VUs each behaving like an SDK on `changed`:
wait `random(0, 2 s)` (or 0 for the bad case) then `GET /sdk/v1/config`. Setup hook:
`redis-cli FLUSHALL` and restart both server nodes (cold local cache) right before. Compare
four runs: (a) no protection, (b) single-flight only, (c) + write-through-on-publish (so Redis is
already hot — needs the publish to trigger the run instead of `FLUSHALL`), (d) + SDK jitter.

**Observe.** Count `snapshot.rebuilt` log lines / metric per node and Postgres `pg_stat_statements`
calls for the snapshot query; p99 of `/config` during the burst; Redis `INFO commandstats`.
(a) can show hundreds of DB rebuilds and p99 in seconds; (b) ≤ 1 per node; (c) 0 (Redis hit);
(d) flatter Redis/network profile.

**Inspect logs.** `snapshot.cache miss level=redis` vs `level=local` counts; `single-flight
waiters=N` if you log it; Postgres `log_min_duration_statement=50ms` to see the duplicate heavy
queries in (a).

**Diagnose.** The burst is inherent (the event *tells* everyone to fetch at once); the damage is
only in how many of them miss all caches simultaneously. Order matters: if `PUBLISH` precedes
`SET`, followers arrive before the value exists.

**Fix / design around.** Write-through *before* `PUBLISH`; single-flight per node (Caffeine
`LoadingCache` or `computeIfAbsent` over a `CompletableFuture`); optional cross-node lock
`SET lock:snapshot:{env} NX PX 5000`; SDK jitter 0–2 s on `changed`, ±10 % on polls; `ETag`
so most fetches are 304s after the first. Bonus: serve the previous snapshot while rebuilding
(stale-while-revalidate) — measure whether it matters.

**Regression test.** `stampede_500ConcurrentMisses_oneDbRebuild`; the k6 report (a)–(d) in
`docs/PERFORMANCE.md` with the counts table.

---

## 8. SSE fan-out overload

**Reproduce.** A Python script in `tools/` (or k6 with a streaming client) opens N SSE connections
to one node with one server key: N = 500, 2 000, 10 000 (raise `ulimit -n` in the container).
Then publish 10 versions in 10 s. Also test with *slow consumers*: `tc qdisc add dev eth0 root
netem delay 500ms loss 30%` inside the client container, or clients that connect and never read.

**Observe.** Connection count metric; memory per connection (`jcmd <pid> GC.heap_info`); publish
latency (does it include N socket writes?); `Too many open files`; servlet thread pool exhaustion
if SSE holds a thread per connection (it should not with `SseEmitter`, which uses async
servlet support — verify); whether one slow consumer delays the broadcast for everyone.

**Inspect logs.** `sse.connected total=N`, `sse.send failed emitter=… reason=Broken pipe` (must be
per-emitter and non-fatal), Tomcat `maxConnections`/`accept-count` warnings, `429` for the
per-key cap.

**Diagnose.** Sequential broadcast on the publish thread; no per-emitter timeout; no cap; Tomcat
limits (`server.tomcat.max-connections` default 8192); OS fd limits.

**Fix / design around.** Broadcast on a dedicated executor with per-emitter `send` wrapped in
try/catch and a bounded queue; per-key connection cap; `server.tomcat.max-connections` and
container `ulimit` sized on purpose; keep events thin (~50 B); document the ceiling you measured
(connections per node at which p99 publish→delivery exceeded 2 s) and the design beyond it:
dedicated stream nodes, or WebFlux/Netty for the stream endpoint, or plain polling with CDN
for very large fleets.

**Regression test.** `sse_broadcast_survivesDeadEmitter`, `sse_perKeyConnectionCap_returns429`,
and a documented load run: connections vs delivery p99 table.

---

## 9. Corrupted snapshot JSON

**Reproduce.** Three injection points. (1) Redis: `redis-cli SET snapshot:<envId> '{"flags":'`
then hit `/config`. (2) The wire: WireMock returning truncated JSON, or valid JSON with
`schemaVersion: 2`, or with an unknown operator `"FUZZY"`, or `rules` as an object instead of an
array. (3) The DB: a version row whose JSONB is valid JSON but references a variation that no
longer exists (possible only if validation was bypassed — insert it with `psql`).

**Observe.** Server (1): must treat the Redis value as a miss (log `snapshot.corrupt level=redis`),
rebuild from DB, overwrite Redis. SDK (2): must keep the previous snapshot, count a failure, and
*not* enter a tight retry loop; unknown operator inside an otherwise valid snapshot: keep the
snapshot, evaluate that rule as non-matching (or that flag as `ERROR` → default) — decide and
write it into `docs/EVALUATION.md`. Server (3): `SnapshotBuilder` must not throw for the whole
environment because one flag is broken — skip the flag, log ERROR with the flag key, expose a
metric.

**Inspect logs.** Exactly one ERROR per corrupt artifact per occurrence, with `envVersion`, byte
length, and the parse error position; never the full body (it may contain targeting PII).

**Diagnose.** Jackson `FAIL_ON_UNKNOWN_PROPERTIES` (should be false for forward compatibility);
enums deserialised strictly (should map unknown to `UNKNOWN` and be handled); `schemaVersion`
check missing.

**Fix / design around.** Validate on parse (schema version, required fields); per-flag isolation
in the builder; treat cache corruption as a miss; unknown enum values map to safe behaviour;
SDK never replaces a good snapshot with a bad one; JSON Schema `contract/snapshot-schema.json`
used in a server test to validate every generated snapshot.

**Regression test.** `sdk_malformedJson_keepsPreviousSnapshot`, `sdk_unknownSchemaVersion_isRejected`,
`snapshot_corruptRedisValue_treatedAsMiss`, `snapshotBuilder_brokenFlagIsSkippedNotFatal`,
`snapshot_matchesJsonSchema`; Python `test_client_malformed_json_keeps_snapshot`.

---

## 10. Clock skew and TTLs

**Reproduce.** Where does time matter? (a) SDK `staleAfter` and backoff; (b) SDK key `expires_at`
during rotation; (c) JWT `exp`; (d) Redis TTLs; (e) `generatedAt` in the snapshot; (f) the
propagation-delay measurement itself. Skew the sample app container: `docker run --cap-add
SYS_TIME` and `date -s "+10 minutes"` (or use `libfaketime`), then run rotation and staleness
checks. Skew the server against RDS.

**Observe.** (a) If the SDK computes staleness from the server's `generatedAt`, a skewed client
thinks it is 10 min stale immediately — and a client *behind* thinks it is never stale. (b) A
server node with a fast clock rejects the old key before the grace period ends. (c) JWTs issued
by node A are "not yet valid" on node B if you set `nbf`. (f) Your measured "propagation delay"
is negative or inflated if you subtract timestamps from different machines.

**Inspect logs.** SDK `STALE` transition immediately after load; server `401 sdk key expired`
earlier than expected; benchmark table with impossible values.

**Diagnose.** Mixing clocks: any comparison of a timestamp from machine X with `now()` on
machine Y. Wall-clock vs monotonic: backoff and staleness should use `System.nanoTime()`
(Java) / `time.monotonic()` (Python), which are immune to NTP jumps.

**Fix / design around.** SDK staleness = monotonic time since *last successful fetch on this
client*, never `generatedAt`; `generatedAt` is informational only. Server-side expiries compare
DB time (`now()` in the query) against DB-stored timestamps, so all nodes agree. Grace periods
generous (hours) relative to plausible skew (seconds). Propagation delay measured on one machine
(the publish `curl` and the SDK log on the same host) or with a round-trip design. NTP on EC2
(chrony is on by default — check it). Redis TTLs are relative and server-local — fine.

**Regression test.** `sdk_staleness_usesMonotonicClock` (inject a `Clock`/`TimeSource`, jump
wall-clock, assert no transition), `keyRotation_expiryComparedInDatabaseTime`, plus a note in the
benchmark methodology.

---

## 11. SDK key leaked — rotation

**Reproduce.** Pretend `ffs_prod_…` was committed to a public repo. Runbook: dashboard → SDK keys
→ **rotate** (grace 1 h) → new key appears once → update the sample app's env var and restart it
→ observe both keys working → after grace, old key `401`. Also test the *bad* path: revoke
immediately (no grace) while the sample app is still on the old key.

**Observe.** Old key: `200` until `expires_at`, then `401 unauthenticated`; SDK on the old key
logs one ERROR `sdk key rejected (401) — polling stopped, serving last snapshot`, status
`UNAUTHORIZED`, keeps serving stale values (a leak is not a reason to break production — argue
this both ways). Audit feed shows `SDK_KEY_ROTATED` and `SDK_KEY_REVOKED` with actor and prefix.
Client-side keys: verify `ffc_` still cannot fetch `/config` — the leak of a client key exposes
only evaluation results.

**Inspect logs.** Server: `sdk.auth failed prefix=ffs_prod_ reason=expired` (prefix only, never
the key); rate limiting on failed auth attempts (a leaked key being brute-forced is a different
scenario — but repeated 401s from one IP should be throttled). Grep your own logs for the
plaintext: the test that matters is that it appears **nowhere**.

**Diagnose.** Is the key hash lookup constant-time? Does the auth filter cache key rows for 60 s
(then revoke has up to 60 s latency — acceptable? document it, or publish an eviction via
pub/sub)? Does the dashboard show the plaintext more than once?

**Fix / design around.** Rotation with grace; revoke evicts the auth cache on all nodes via
pub/sub; secret scanning in CI (`gitleaks` action) as a pre-merge check; SDK behaviour on 401
documented; `docs/SECURITY.md` runbook "key leaked".

**Regression test.** `keyRotation_oldKeyValidUntilGraceThen401`, `sdk_401_stopsPollingAndKeepsSnapshot`,
`sdkKey_plaintextNeverPersisted`, `sdkKey_plaintextNeverLogged` (log capture appender asserts),
`sdkApi_clientKeyCannotFetchConfig`.

---

## 12. Postgres unavailable

**Reproduce.** `docker compose stop postgres` with Redis and both nodes up and warm. Keep SDKs
polling; try a publish; wait past the local TTL; then `FLUSHALL` Redis too (worst case).

**Observe.** Expected: `/config`, `/evaluate`, `/stream` keep working from Redis (the hot path
never touches Postgres). Publish fails with `503`/`500` and a clean ProblemDetail — dashboard
shows an error, nothing half-written. Auth for SDK keys: if the key cache is cold, SDK auth
needs the DB → `401`? No — it must be `503 snapshot-unavailable`-style (a temporary failure must
not look like a revoked key, or SDKs will stop polling!). Worst case (Redis flushed too): `503`
for `/config`; SDKs keep stale snapshots (scenario 3).

**Inspect logs.** Hikari `Connection is not available, request timed out after 30000ms` — 30 s
per request is far too long for the SDK auth path; `snapshot.cache miss level=db` failing.

**Diagnose.** Which endpoints touched the DB during the outage? `pg_stat_activity` won't help
(DB is down) — use a request-scoped counter or a Hikari metric, or put a `DataSource` proxy that
throws in a test and see which endpoints break.

**Fix / design around.** SDK key auth cache with a long TTL (10 min) and *negative caching only
for real 401s*; DB errors during SDK auth → `503` + `Retry-After`, never `401`; Hikari
`connectionTimeout` 2–5 s; `/sdk/*` endpoints proven DB-free by a test using a failing DataSource.

**Regression test.** `sdkApi_worksWithDatabaseDown_whenRedisWarm` (Testcontainers: stop Postgres,
assert 200s), `sdkAuth_databaseDown_returns503Not401`.

---

## 13. Cross-SDK disagreement (Java vs Python)

**Reproduce.** Run `tools/rollout_sim.py --check-server 2000` and the pytest
`test_python_and_java_sdk_agree_on_vectors` with a *deliberately* extended vector set: contexts
with unicode user keys (`"user-ä"`, emoji), numeric strings (`"10"` vs `10`), booleans, floats
with many decimals (`99.995 %`), attributes that are lists, missing attributes with `NOT_IN`,
case differences (`"DE"` vs `"de"`), 0 % and 100 % rollouts, priority ties.

**Observe.** Typical first-run mismatches: unicode (Java `getBytes()` default charset on some
JDK/OS combos vs Python UTF-8), rounding (`Math.round(99.995 * 100)` = 10000 in Java double
arithmetic? check — vs Python `round()` banker's rounding: `round(0.5) == 0`!), `GT` on numeric
strings, `CONTAINS` on lists, `NOT_IN` on missing attributes.

**Inspect logs.** The simulator's mismatch table: user key, Java result, Python result, server
result, reason from each. Three-way comparison localises the bug: if server == Java ≠ Python
it's the Python port; if Python == spec ≠ Java, the Java code drifted from `docs/EVALUATION.md`.

**Diagnose.** Every mismatch is a **spec gap** first: the document didn't say. Only after the
spec says do you have a code bug.

**Fix / design around.** Make the spec explicit about: encoding (UTF-8), the exact hash input
string, integer conversion of the percentage (`int(pct * 100 + 0.5)`? or require ≤ 2 decimals at
validation and compute in integer hundredths from the start — the better fix), numeric comparison
rules (attribute must be a JSON number; numeric strings are strings), case sensitivity (exact),
list attributes (only `IN`-style semantics if you support them at all), missing attributes. Add a
vector for every decision. Freeze `contract/` at v1.0 and treat changes as breaking.

**Regression test.** The extended vector set in `contract/evaluation-vectors.json` ("edge" group),
run by JUnit and pytest; `test_python_and_java_sdk_agree_on_vectors` over 10 000 generated
contexts nightly (`perf.yml`/`workflow_dispatch`).

---

## 14. Publish partially failed (version written, snapshot not)

**Reproduce.** Insert a fault hook (test profile property `flagforge.faults.afterCommit=throw`)
that throws inside the after-commit snapshot rebuild; or kill the server (`docker kill`) right
after the transaction commits — a `Thread.sleep(2000)` behind a test property makes the window
hittable. Also the opposite: Redis `SET` succeeds but `PUBLISH` fails.

**Observe.** DB: version v58 exists, `current_env_version = 58`. Redis: still v57. Node A local:
maybe v58 (if put before the failure) or v57. Node B: v57. SDKs: v57. The dashboard shows v58 as
current. Nobody notices until the local TTL and a cache miss rebuild from DB — *if* the miss
happens (with Redis warm at v57, it never does!). This is the important finding: **a warm but
stale Redis hides the failure indefinitely.**

**Inspect logs.** `publish … v=58` followed by `snapshot.rebuild failed env=… cause=…` — must be
ERROR, must include env and version; a metric `flagforge_snapshot_lag{env} = current_env_version
− redis snapshot envVersion`.

**Diagnose.** The snapshot is a derived artifact of the DB; the publish transaction cannot span
Redis. Classic dual-write problem (you met it with the outbox in LedgerX M3).

**Fix / design around.** Options, from simplest: (1) a reconciler `@Scheduled` every 30 s
compares `environment.current_env_version` with the Redis snapshot's `envVersion` per env and
rebuilds on lag (self-healing, cheap — do this); (2) a transactional outbox row "snapshot
rebuild needed" processed by a worker (exactly-once-ish, heavier); (3) make readers compare
versions: `/config` reads `current_env_version` from… the DB? No — that puts the DB on the hot
path. Choose (1), expose the lag metric, alarm on it. Publish response should report
`snapshotStatus: "published" | "pending"` so the UI can show a spinner instead of lying.

**Regression test.** `publish_afterCommitFailure_reconcilerRepairsSnapshotWithin30s`,
`metrics_snapshotLag_isZeroAfterPublish`; a fault-injection toggle documented in `docs/TESTING.md`
(same technique as LedgerX crash-between-steps tests).

---

## How to write each exercise up

```
## <n>. <scenario>          Date · commit · stack (compose / AWS)
Reproduce:   exact commands
Expected:    what the design says should happen
Observed:    what actually happened (numbers, log excerpts ≤ 5 lines)
Root cause:  one paragraph
Change:      PR link; what changed in code / config / docs
Regression:  test names added
Interview:   the 3-sentence version of this story
```

Keep them in `docs/FAILURE_ENGINEERING.md`; link the best three from the README. These
write-ups are the raw material for [interview-questions.md](./interview-questions.md) §Troubleshooting.
