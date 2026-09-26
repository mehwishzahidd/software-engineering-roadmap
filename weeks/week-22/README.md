# Week 22 — FlagForge M3: the Java SDK

[← Week 21](../week-21/) · [Roadmap](../../ROADMAP.md) · [Week 23 →](../week-23/)

**Phase 4 · FlagForge** (weeks 20–23) · Milestone **M3** · Target: SDK with polling + local evaluation, then SSE streaming (advanced within M3)

| Block | Hours | Focus |
|---|---:|---|
| Project | 28 | `FlagClient` (builder, polling, local snapshot, local evaluation, defaults, timeouts, stale-if-error, offline), sample app, SDK unit + contract tests, then SSE streaming updates |
| Learning | 6 | Client-library/API design, semantic versioning, polling vs streaming, resilience, publishing a Maven artifact |
| DSA | 7 | Mixed review — **6 new** + reviews |
| Interview / review | 4 | OA simulation #5, **system-design mock #1**, weekly mock |

---

## 1. Main objective

Build the thing other developers will `import`: a Java SDK whose **failure behaviour is the
feature**. The app must never block, never crash and never see an exception from a flag lookup
— it gets a stale snapshot, or its own default. This is API design as a product: the builder,
the defaults, the thread model and the semver promise are all interview material.

## 2. Prerequisites

- FlagForge M2 ([Week 21](../week-21/)): `eval` module with no Spring dependency, `/snapshot` with `ETag`/`304`, conformance vectors.
- Java concurrency deep dive ([Week 15](../week-15/)): executors, `ScheduledExecutorService`, `volatile`/`AtomicReference`, `CompletableFuture`.
- Maven multi-module and packaging ([`01-java/08-maven-build.md`](../../01-java/08-maven-build.md)).

## 3. Learning topics

| Topic | Subtopics | Read |
|---|---|---|
| Client-library design | Builder pattern, immutable config, minimal public surface, no checked exceptions on lookups, no logging framework lock-in (SLF4J API only), thread ownership (daemon threads, `close()`), never throw from `getBool` | [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md), [`01-java/02-oop.md`](../../01-java/02-oop.md) |
| Semantic versioning | MAJOR/MINOR/PATCH, what is a breaking change in a library (public types, constructor args, thrown exceptions, thread behaviour), `@Deprecated` policy, changelog | [`01-java/08-maven-build.md`](../../01-java/08-maven-build.md) |
| Polling vs streaming | Poll with `ETag`/`If-None-Match` + jitter; long-poll; SSE with `Last-Event-ID`; when each is right; cost per client | [`15-system-design/scalability.md`](../../15-system-design/scalability.md), [`14-cs-fundamentals/networking.md`](../../14-cs-fundamentals/networking.md) |
| Resilience | Connect/read timeouts, bounded retries with backoff + jitter, stale-if-error, offline mode, circuit-breaker awareness, initialization timeout (`waitForReady`) | [`15-system-design/fundamentals.md`](../../15-system-design/fundamentals.md) |
| Publishing a Maven artifact | `maven-source-plugin`, `maven-javadoc-plugin`, GitHub Packages (simplest) vs Maven Central (needs GPG + namespace — optional), consuming it from the sample app | [`01-java/08-maven-build.md`](../../01-java/08-maven-build.md), [`13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md) |
| Testing an SDK | Unit tests with a fake transport, contract tests against the real server (Testcontainers or Compose), WireMock/MockWebServer for failure injection (timeouts, 500s, malformed JSON) | [`09-testing/mockito.md`](../../09-testing/mockito.md), [`09-testing/testcontainers.md`](../../09-testing/testcontainers.md) |

## 4. Concepts to learn

### 4.1 The public surface (design this before coding)

```java
FlagClient client = FlagClient.builder("sdk-server-…")
        .baseUrl("https://flags.example.com")
        .pollingInterval(Duration.ofSeconds(30))
        .connectTimeout(Duration.ofSeconds(2))
        .readTimeout(Duration.ofSeconds(5))
        .staleIfError(Duration.ofHours(6))      // keep serving old snapshot this long
        .offline(false)
        .build();

client.waitForReady(Duration.ofSeconds(3));     // returns boolean, never throws
boolean on = client.getBool("new-checkout", ctx, false);
EvaluationDetail<Boolean> d = client.getBoolDetail("new-checkout", ctx, false); // value + reason + version
client.close();                                  // stops threads, idempotent
```

- Everything the app calls returns; nothing throws (except `builder()` argument validation).
- `Context` is an immutable value: `key` + typed attributes (`Map<String, Object>` with `String/Number/Boolean` only, validated).
- **Interview angle:** "What was your hardest API decision?" — e.g. `getBool` taking a default (never `Optional`, never `null`), because the SDK's promise is "you always get *a* value".
- **Where FlagForge uses this:** `flagforge-sdk` module; the sample app; M4 propagation.

### 4.2 Thread model and state

```java
final AtomicReference<Snapshot> snapshot = new AtomicReference<>();   // publish-safe swap
final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "flagforge-poller"); t.setDaemon(true); return t;
});
```

- One daemon poller thread; evaluation happens on the caller's thread against the current snapshot reference (no locks on the hot path).
- The swap is atomic; a snapshot is immutable; readers never see a half-built map.
- `close()` shuts the executor down and waits ≤ 1 s; calling `getBool` after `close()` still returns the last snapshot or default.
- **Interview angle:** "Is your SDK thread-safe? Prove it." — immutable snapshot + `AtomicReference` + single writer.

### 4.3 Polling with ETag, backoff and jitter

```
every interval + jitter(±10%):
  GET /api/v1/snapshot  If-None-Match: "<version>"
  304 → touch lastSuccess
  200 → parse, swap, touch lastSuccess, reset backoff
  error/timeout → backoff = min(backoff*2, 5 min); keep serving old snapshot
  if now - lastSuccess > staleIfError → mark STALE (still serve; expose status)
```

- Jitter prevents a thundering herd when 500 instances restart together.
- Initialization: first poll synchronously on `build()`? No — start async and provide `waitForReady`. Explain why (constructors must not block on the network).

### 4.4 Local evaluation = the same `eval` module

The SDK depends on `flagforge-eval` (from M2) so **server and client evaluate identically** — the conformance vectors prove the bucketing; a contract test proves whole-flag evaluation: for 50 generated contexts, `server.evaluate == sdk.evaluate` for every flag in the environment.

### 4.5 SSE streaming (advanced, second half of the week)

- Server: `GET /api/v1/stream` (SDK key) → `text/event-stream`; events `snapshot-updated {version}` (the client then polls once) or the full snapshot (bigger, simpler — pick and justify; a "version nudge + fetch" keeps SSE payloads tiny and reuses the ETag path).
- Client: `java.net.http.HttpClient` with `BodyHandlers.ofLines()` on a daemon thread; `Last-Event-ID` on reconnect; exponential reconnect backoff; **polling stays on as a fallback** at a longer interval.
- **Interview angle:** "Why SSE and not WebSockets here?" — one direction, plain HTTP, proxy friendly, trivial reconnect semantics; you made the same call in ForgeCI M3 for logs.

### 4.6 Publishing

`mvn -pl sdk deploy` to GitHub Packages with a `distributionManagement` block and a `GITHUB_TOKEN`; the sample app in a **separate** directory consumes `com.<you>.flagforge:flagforge-sdk:0.1.0` from that repository. Version `0.1.0` (pre-1.0: minor bumps may break). Changelog `sdk/CHANGELOG.md` from day one.

## 5. Resources

- "Effective Java" (Bloch): Items 2 (builders), 17 (immutability), 69–71 (exceptions), 78–81 (concurrency) — the SDK is a Bloch exercise.
- Java `java.net.http.HttpClient` javadoc (timeouts, `BodyHandlers.ofLines`).
- Semantic Versioning 2.0.0 spec (semver.org).
- GitHub Docs: "Working with the Apache Maven registry".
- OpenFeature Java SDK docs — for vocabulary and to compare public surface after you designed yours (not before).
- MockWebServer (OkHttp) or WireMock docs for transport failure tests.

## 6. Exercises and assignments

### Exercise A — API review before code (1 h, Mon)

Write `sdk/API.md` with every public type and method, its threading guarantee and its failure behaviour. Acceptance: a reviewer (you, tomorrow) can find no method that can throw at an app.

### Exercise B — Failure matrix (45 min)

Table: {server 500, timeout, malformed JSON, 401 revoked key, DNS failure, server down at startup, server down after 6 h} × {what the app sees, what the SDK logs, SDK status}. Acceptance: every cell filled; each cell becomes a test.

### Break it (inside FlagForge)

- Start the sample app with the server down. Predict: `waitForReady` returns `false` after 3 s; `getBool` returns the default; app keeps running.
- Kill the server after the SDK has a snapshot; wait past `staleIfError`. Predict status `STALE`; values still served.
- Return a 200 with truncated JSON from MockWebServer. Predict: old snapshot kept, error logged once (not every poll — rate-limit your logs).
- Revoke the SDK key. Predict: 401 → SDK stops polling? No — it backs off and keeps trying (key may be rotated back); status `UNAUTHORIZED`.

### Debug it

- The sample app never exits: a non-daemon thread — find it with `jstack` and fix the thread factory.
- Server and SDK disagree on one flag for one user: version mismatch (SDK stale) vs bucketing input mismatch (attribute type coercion: `"42"` vs `42`) — the `reason` and `version` in `EvaluationDetail` tell you which.

## 7. DSA — Mixed review (6 new)

Week 20 selection rule; recompute `weak` scores first. This week, make the unseen timed Medium a **string/hashing** problem (SDKs are full of parsing): candidates 49 (Group Anagrams), 271 (Encode and Decode Strings — premium; use 8 String to Integer (atoi) if unavailable), 5 (Longest Palindromic Substring), 76 (Minimum Window Substring — Hard, 45 min), 208 (Implement Trie) if Tries is weak.

Reviews due: Day-3 of W21, Day-7 of W20, Day-14 of W19 graphs/bits, Day-30 of W17 Greedy/2-D DP.

## 8. Project work — FlagForge M3

Spec: [`18-projects/flagforge/README.md`](../../18-projects/flagforge/README.md) · [`milestones.md`](../../18-projects/flagforge/milestones.md) · [`failure-engineering.md`](../../18-projects/flagforge/failure-engineering.md) · [`docs-and-resume.md`](../../18-projects/flagforge/docs-and-resume.md).

### Weekly task checklist

- [ ] Milestone `M3 – Java SDK`; `sdk/API.md` reviewed before implementation
- [ ] `flagforge-sdk` module: `FlagClient` + builder, `Context`, `EvaluationDetail`, `ClientStatus` (`INITIALIZING/READY/STALE/UNAUTHORIZED/OFFLINE/CLOSED`)
- [ ] Transport: `java.net.http.HttpClient`, ETag polling, timeouts, backoff + jitter, log rate-limiting
- [ ] Local evaluation via `flagforge-eval`; defaults; typed getters (`getBool/getString/getInt/getDouble/getJson`)
- [ ] Offline mode (bootstrap from a JSON file; no network)
- [ ] `close()` semantics; daemon threads; `waitForReady`
- [ ] Sample app (`examples/sample-app`, plain `main` + a small Spring Boot variant optional)
- [ ] Unit tests with fake transport; failure-matrix tests with MockWebServer; contract test vs real server (Compose/Testcontainers) incl. conformance vectors and 50-context equivalence
- [ ] Publish `0.1.0` to GitHub Packages; sample app consumes it; `CHANGELOG.md`
- [ ] Advanced: `/stream` SSE endpoint + SDK streaming mode with polling fallback; `StreamingUpdateIT`
- [ ] Docs: `sdk/README.md` (quick start, config table, failure behaviour table, thread model)

### Acceptance summary

- The sample app runs correctly with the server up, down at start, and down after start — without code changes.
- No public SDK method throws to the application; every failure cell has a test.
- Contract test: SDK and server evaluate identically for the conformance vectors and 50 generated contexts.
- `0.1.0` artifact resolvable from a clean machine with a token.
- Streaming: publish → SDK sees new version in < 1 s (measured), and still works with streaming disabled.

### Verification tests

| Test | Given | When | Then |
|---|---|---|---|
| `BuilderValidationTest` | blank key / negative interval | `build()` | `IllegalArgumentException` (the only allowed throw) |
| `DefaultsWhenNotReadyTest` | server unreachable | `getBool(…, false)` | `false`, status `INITIALIZING` then `OFFLINE`-ish per your matrix |
| `EtagPollingTest` | fake transport returns 304 | poll | snapshot unchanged, `lastSuccess` updated |
| `StaleIfErrorTest` | snapshot v7, server 500 for > threshold (fake clock) | | status `STALE`, values from v7 |
| `MalformedJsonTest` | 200 with bad body | poll | old snapshot kept, one error log |
| `BackoffJitterTest` | 5 consecutive failures | | delays grow ≤ 5 min and differ by jitter |
| `CloseIdempotentTest` | `close()` twice | | no error; no non-daemon threads (assert via `Thread.getAllStackTraces`) |
| `ContractEquivalenceIT` | real server | 50 contexts × all flags | server == SDK for value, variation, reason |
| `StreamingUpdateIT` | streaming on | publish v8 | SDK snapshot version 8 within 1 s; reconnect after server restart |

### Failure scenarios to run

1. Server down at startup / mid-run / after stale threshold.
2. Slow server (2× read timeout) — SDK threads never pile up (single poller, no overlap).
3. Snapshot 5 MB (1 000 flags) — parse time, memory, GC; document limits.
4. SSE connection silently dropped by a proxy — heartbeat comments every 15 s; detect and reconnect.
5. Clock jump on the client — stale computation uses monotonic `System.nanoTime()`, not wall clock (test).

### GitHub expectations

- Milestone M3 closed; package visible under the repo's Packages; sample app README; `sdk/CHANGELOG.md` with `0.1.0`.
- ADR: polling vs streaming (both, with fallback) in `docs/adr/`.

## 9. Git activity

- Branches: `feat/m3-sdk-core`, `feat/m3-sdk-transport`, `feat/m3-contract-tests`, `feat/m3-streaming`, `ops/m3-publish`.
- Tag `sdk-0.1.0` separately from the server tags; the CI publish job triggers on that tag pattern.

## 10. Interview preparation

- **System-design mock #1** (Sat, 60 min + 30 min debrief): [`16-interview-prep/system-design-interview.md`](../../16-interview-prep/system-design-interview.md). Have the interviewer choose between "design a feature-flag service", "design a CI job runner" and "design a wallet ledger" — all three are yours, so the mock tests *transfer*, not memory. Score against the rubric; log in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **OA simulation #5** (Fri, 90 min): [`OA_PREP.md`](../../OA_PREP.md) with a buggy-library task from [`21-debugging-code-reading/exercises/buggy-library/`](../../21-debugging-code-reading/exercises/buggy-library/).
- **Weekly coding mock** (Tue): [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md).
- **Résumé defense:** Maven + JUnit + REST — [`17-resume-tech-defense/maven.md`](../../17-resume-tech-defense/maven.md), [`17-resume-tech-defense/junit.md`](../../17-resume-tech-defense/junit.md), [`17-resume-tech-defense/rest-apis.md`](../../17-resume-tech-defense/rest-apis.md). By next week every technology must have been drilled at least twice — check the readiness matrix in [`RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md) and schedule the gaps.
- **Applications:** junior-ready tier ([`JOB_READINESS.md`](../../JOB_READINESS.md)); recruiter-screen answers refreshed with the SDK story ([`16-interview-prep/recruiter-screen.md`](../../16-interview-prep/recruiter-screen.md)).

## 11. Revision

- Re-read [`01-java/07-concurrency.md`](../../01-java/07-concurrency.md) on `volatile`/`AtomicReference` and executor shutdown — then check your SDK against it.
- Compare ForgeCI's SSE replay (`Last-Event-ID` → sequence) with this week's; write 5 lines on what is common.
- DSA reviews.

## 12. Daily plan

| Day | Plan |
|---|---|
| **Mon (8 h)** | Learning 2: client-library design + semver · Project 4.5: `API.md`, builder, `Context`, status model · DSA 1.5 |
| **Tue (8 h)** | Project 5: transport, ETag polling, backoff, stale-if-error, unit tests · DSA 2 + reviews · Mock 1 h |
| **Wed (8 h)** | Learning 2: polling vs streaming, resilience patterns · Project 4.5: local eval, typed getters, offline mode, failure-matrix tests · DSA 1.5 |
| **Thu (8 h)** | Project 5: contract tests, sample app, publish `0.1.0` · DSA 2 (timed string Medium) · Docs 1: `sdk/README.md` |
| **Fri (5 h)** | Project 3: SSE endpoint + SDK streaming · OA sim #5 (90 min) · DSA reviews |
| **Sat (6 h)** | Project 4: streaming tests, failure scenarios, ADR · System-design mock 1.5 h |
| **Sun (2–3 h)** | End-of-week test · reviews · trackers · plan W23 · rest |

## 13. End-of-week test

1. Sketch the SDK's public API from memory and state each method's failure behaviour.
2. Explain the thread model and why evaluation needs no lock.
3. What is a breaking change for a library? Give three examples from your own SDK's surface.
4. Polling vs SSE: cost per 10 000 clients at a 30 s interval vs one idle connection each — reason it through.
5. Timed: LeetCode 49 (Group Anagrams) in 20 min.

Pass: 4/5.

## 14. Mastery checklist

- [ ] I can design a client library whose failure behaviour is explicit, tested and non-throwing
- [ ] I can implement ETag polling with backoff + jitter and stale-if-error using a monotonic clock
- [ ] I can prove server/SDK evaluation equivalence with contract tests
- [ ] I can publish and consume a Maven artifact and explain semver for it
- [ ] I can implement SSE client + server with reconnect and fallback
- [ ] I can run a junior system-design interview using my own projects as case studies

## 15. Expected deliverables

- `flagforge-sdk 0.1.0` published; sample app; `sdk/README.md`, `sdk/API.md`, `CHANGELOG.md`; ADR.
- Trackers: project (M3), DSA (6 + reviews), interview (OA #5, coding mock, **system-design mock score**), technology (Maven "published artifact", Java "concurrency in a library"), weekly progress.

## 16. If behind / stretch

**Behind:** ship polling + local evaluation + failure tests + published artifact; move SSE streaming to M4 (it is the "propagation" half of M4 anyway). Never ship an SDK without the failure-matrix tests.

**Stretch:** TypeScript SDK skeleton sharing the conformance vectors (ADVANCED tier), `Flux`/reactive wrapper, metrics hook (`onEvaluation` listener) for the M4 dashboard.
