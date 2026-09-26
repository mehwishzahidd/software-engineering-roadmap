# Week 21 — FlagForge M2: evaluation engine + cache (MVP)

[← Week 20](../week-20/) · [Roadmap](../../ROADMAP.md) · [Week 22 →](../week-22/)

**Phase 4 · FlagForge** (weeks 20–23) · Milestone **M2** · Target: **MVP**

| Block | Hours | Focus |
|---|---:|---|
| Project | 28 | Deterministic evaluation engine, server-side eval endpoint, Redis environment snapshots with invalidation, p99 latency measurement |
| Learning | 7 | Hashing for deterministic bucketing, rule-engine design, Redis snapshot caching, hot-path latency; **C++ basics (≈4 h)** |
| DSA (Python) | 7 | Mixed review — **6 new** + reviews + 1 Java rep |
| Interview / review | 3 | Weekly mocks (Track A + B), résumé defense, retro |

---

## 1. Main objective

Build the **hot path**: given an SDK key, a flag key and a user context, return the variation
in well under a millisecond of server CPU, deterministically (same user → same bucket → same
answer, on every server, forever), from a cached environment snapshot that is invalidated the
instant a version is published. Then **measure p99**, not guess it.

## 2. Prerequisites

- FlagForge M1 merged ([Week 20](../week-20/)): versioned configs, rules payload, SDK keys.
- Redis cache-aside + invalidation from FlowGrid M3 ([Week 6](../week-06/)); Redis mechanics in [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md).
- Benchmarking discipline from ForgeCI M5 ([Week 18](../week-18/)).

## 3. Learning topics

| Topic | Subtopics | Read |
|---|---|---|
| Deterministic bucketing | Hash of `flagKey:userKey` → bucket `0..9999`; hash choice (MurmurHash3 vs SHA-1 vs `String.hashCode` — why not the last), stickiness across rollout increases, salt per flag | [`15-system-design/caching.md`](../../15-system-design/caching.md) (consistent hashing section), [`03-dsa/02-hashing.md`](../../03-dsa/02-hashing.md) |
| Rule-engine design | Ordered rules, first match wins, typed operators (`EQ, IN, STARTS_WITH, GT, SEMVER_GT`), attribute types, evaluation reasons, no reflection on the hot path | [`18-projects/flagforge/README.md`](../../18-projects/flagforge/README.md) |
| Redis snapshot caching | Whole-environment snapshot as one value (JSON or compact binary) keyed by `env:{id}:snapshot`, `version` field, invalidate on publish, cache-aside with single-flight, TTL as safety net | [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md), [`05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md) |
| Hot-path latency | Where the time goes (JSON parse, DB roundtrip, Redis roundtrip, GC), percentiles, JMH for pure evaluation, k6 for the endpoint, warm-up | [`01-java/06-memory-jvm.md`](../../01-java/06-memory-jvm.md), [`18-projects/templates/benchmark-report.md`](../../18-projects/templates/benchmark-report.md) |
| C++ basics (≈4 h, spread Mon/Wed) | Compilation model, values vs references vs pointers, RAII, `std::vector`/`std::string`/`std::unordered_map`, a two-sum and a linked-list reversal in C++ | [`20-cpp-basics/README.md`](../../20-cpp-basics/README.md) |

## 4. Concepts to learn

### 4.1 The bucketing function (≤ 25 lines, copy this one)

```java
public final class Bucketing {
    private static final int BUCKETS = 10_000;

    /** Deterministic bucket in [0, 10000). Same inputs -> same bucket on every JVM. */
    public static int bucket(String flagKey, String salt, String userKey) {
        byte[] bytes = (flagKey + ":" + salt + ":" + userKey).getBytes(StandardCharsets.UTF_8);
        int h = murmur3_32(bytes);                 // or a vetted library implementation
        return Math.floorMod(h, BUCKETS);
    }

    static int murmur3_32(byte[] data) { /* standard MurmurHash3 x86_32, seed 0 */ return 0; }
}
```

- Use a real MurmurHash3 (Guava `Hashing.murmur3_32_fixed()` or ~30 lines you write and test against known vectors). `String.hashCode()` is stable in practice but is not a specified contract for cross-language SDKs, and its distribution is poor for short keys.
- A rollout of 25 % serves bucket `< 2500`. Raising it to 50 % keeps every user who was "in" still in — **stickiness** without storing anything.
- **Interview angle:** "How do you guarantee a user sees the same variation across servers?" — pure function of inputs, no state. "Why 10 000 buckets?" — 0.01 % granularity, and it fits an `int` test table.
- **Where FlagForge uses this:** `PercentageRule` evaluation; the Java SDK (M3) **and the Python SDK/test client (M3–M4)** must produce the **same** bucket — the shared conformance vectors assert it in JUnit and in `pytest`. Pick a hash with a well-known Python implementation (`mmh3` package, or your own ~30-line pure-Python MurmurHash3 tested against the same vectors).

### 4.2 The evaluation algorithm

```
evaluate(snapshot, flagKey, context):
  cfg = snapshot.flags[flagKey]          // missing → reason FLAG_NOT_FOUND, serve caller default
  if !cfg.enabled → serve cfg.defaultVariation, reason DISABLED
  for rule in cfg.rules ordered by priority:
     if rule.matches(context) → serve rule.serve, reason RULE_MATCH(rule.id)
  serve cfg.defaultVariation, reason DEFAULT
```

- Return `EvaluationResult(variationKey, value, reason, version)` — the **reason** is what makes debugging possible in the dashboard and in support tickets.
- Attribute comparisons are typed at parse time: build `Predicate<Context>` objects once per snapshot load, not per evaluation.
- No exceptions on the hot path for expected cases (missing attribute → rule does not match).
- **Interview angle:** "What is the complexity of an evaluation?" — O(rules) with tiny constants; the snapshot lookup is O(1); no I/O.

### 4.3 Environment snapshot in Redis (cache-aside + invalidation)

```
GET env:{envId}:snapshot           → hit: deserialize, evaluate
                                   → miss: load all published versions for env from Postgres,
                                            build snapshot {version: max(version_no), flags: {...}},
                                            SET env:{envId}:snapshot <bytes> EX 600
on publish (same transaction commits) → DEL env:{envId}:snapshot   (after commit!)
```

- Invalidate **after** commit (`TransactionSynchronization.afterCommit`) — otherwise a concurrent reader can repopulate the cache with the old data between your `DEL` and your commit (the classic cache-aside race; see [`15-system-design/caching.md`](../../15-system-design/caching.md)).
- Keep an **in-process** copy too (`ConcurrentHashMap<envId, Snapshot>` with the snapshot version) so a Redis roundtrip is not on every evaluation: check Redis only every N seconds or on a pub/sub nudge (M4). For M2: Redis per request is acceptable; measure both.
- Redis down → degrade: serve in-process copy if present, else load from Postgres directly and log a warning. Never fail an evaluation because a *cache* is down.
- **Interview angle:** "What happens if two publishes and one read interleave?" — draw the race; explain after-commit invalidation and the TTL safety net.

### 4.4 The eval endpoint

`POST /api/v1/evaluate` with `Authorization: Bearer <sdkKey>`:

```json
{ "flagKey": "new-checkout", "context": { "key": "u_42", "attributes": { "country": "DE", "plan": "pro" } } }
```

→ `200 { "flagKey": "new-checkout", "variation": "on", "value": true, "reason": "RULE_MATCH:r2", "version": 7 }`.

Also `POST /api/v1/evaluate/all` (all flags for a context — what SDKs bootstrap with) and `GET /api/v1/snapshot` (the raw snapshot + `ETag: "<version>"`, `304` on `If-None-Match` — the SDK's polling primitive in M3).

### 4.5 Measuring p99 honestly

- **JMH** for `evaluate()` alone: ops/µs with a 20-rule flag; this is CPU only.
- **k6** for `POST /evaluate`: 50/200/500 VUs, 60 s each after 30 s warm-up, record p50/p95/p99 and error rate, with Redis-hit and Redis-cold variants.
- Record hardware, JVM flags, Boot version, Redis location (same host vs container), payload size.
- **Interview angle:** "Your p99 was 4 ms — where did it go?" — you need the breakdown (network + JSON + Redis + eval), and you will only have it if you measured each.

## 5. Resources

- MurmurHash3 reference (Austin Appleby's public domain description); Guava `Hashing` javadoc.
- Redis docs: `SET … EX`, `DEL`, pub/sub (preview for M4), latency monitoring (`redis-cli --latency`).
- Spring Framework docs: `TransactionSynchronizationManager`.
- JMH samples (OpenJDK) and Grafana k6 docs.
- OpenFeature specification (evaluation context, reasons vocabulary) — for vocabulary only.
- C++: cppreference.com; "A Tour of C++" (Stroustrup) chapters 1–3.

## 6. Exercises and assignments

### Exercise A — Bucketing conformance table (45 min)

Generate 20 `(flagKey, salt, userKey) → bucket` vectors, commit them as `bucketing-vectors.json`. Acceptance: server test and (in M3) SDK test both pass against the same file; distribution over 100 000 random keys has each of the 10 deciles within ±1 % of 10 %.

### Exercise B — Cache race drill (30 min)

On paper, interleave: reader miss → publisher `DEL` → publisher commit → reader `SET old`. Now move the `DEL` after commit and show the window that remains (reader loaded before commit, sets after `DEL`). Explain why the TTL and the `version` compare-on-write (`SET` only if `snapshot.version >= cached.version`, via Lua) close it.

### Exercise C — C++ (4 h total)

From [`20-cpp-basics/README.md`](../../20-cpp-basics/README.md): compile hello world with `g++ -std=c++20`, write two-sum with `std::unordered_map`, reverse a singly linked list with raw pointers and once with `std::unique_ptr`. Acceptance: you can explain what RAII means using your own linked-list code.

### Break it (inside FlagForge)

- Stop Redis, evaluate. Predict: latency and log line. Then start Redis again — does the snapshot come back without a restart?
- Publish while a k6 run is hammering `/evaluate`. Predict: any request sees the old version *after* one has seen the new? (It must not, for one server. Across two servers it may — document.)
- Change the salt of a flag. Predict what happens to every user's bucket (they all move — that is why salt is immutable per flag).

### Debug it

- p99 spikes every ~10 minutes: TTL expiry causes a stampede on the DB — count `SELECT` statements at the spike; single-flight (M4) or jittered TTL fixes it.
- Two servers disagree on a variation for the same user: compare snapshot `version`; if equal, compare the bucketing input string byte-for-byte (trailing whitespace, Unicode normalization of the user key).

## 7. DSA — Mixed review (6 new, Python)

Apply the Week 20 selection rule (two weakest patterns × 2, one stale pattern × 1, one unseen timed Medium, plus 1 Java rep). All in Python; record `weak` scores in the tracker before choosing. This week's cheatsheet focus ([`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md)): `bisect` for binary-search-on-answer, `heapq` with tuples, `sorted(key=lambda ...)` cost O(n log n). **Java rep:** re-do LeetCode 146 (LRU Cache) in Java with `LinkedHashMap(accessOrder=true)` vs a hand-rolled doubly linked list — it doubles as Track B material (caching). Suggested pool additions for this week (real numbers): Two Pointers 42 (Trapping Rain Water), 11 (Container With Most Water); Prefix Sums 560 (Subarray Sum Equals K), 238 (Product of Array Except Self); Tries 208 (Implement Trie), 211 (Design Add and Search Words); BST 230 (Kth Smallest Element in a BST), 98 (Validate Binary Search Tree); Greedy 55 (Jump Game), 134 (Gas Station); Union-Find 323 (Number of Connected Components — premium; use 547 Number of Provinces if unavailable), 721 (Accounts Merge).

Reviews due: Day-3 of W20 set, Day-7 of W19 graphs/bits, Day-14 of W18 2-D DP, Day-30 of W16 1-D DP + Intervals.

## 8. Project work — FlagForge M2

Spec: [`18-projects/flagforge/README.md`](../../18-projects/flagforge/README.md) · [`milestones.md`](../../18-projects/flagforge/milestones.md) · [`failure-engineering.md`](../../18-projects/flagforge/failure-engineering.md) · [`docs-and-resume.md`](../../18-projects/flagforge/docs-and-resume.md).

### Weekly task checklist

- [ ] Milestone `M2 – Evaluation engine + cache`
- [ ] `flagforge-eval` package (pure Java, no Spring): `Snapshot`, `FlagConfig`, `Rule` predicates, `Bucketing`, `Evaluator`, `EvaluationResult` with reasons — designed to be shared with the SDK module in M3
- [ ] Conformance vectors file + tests; distribution test
- [ ] Snapshot builder from Postgres (published versions per environment) + serialization (JSON first; measure; consider a compact format later)
- [ ] Redis cache-aside with after-commit invalidation, TTL, degrade-to-DB when Redis is down
- [ ] In-process snapshot holder with version check
- [ ] SDK-key authentication filter; `/evaluate`, `/evaluate/all`, `/snapshot` with `ETag`/`304`
- [ ] JMH benchmark module for `Evaluator`; k6 script for `/evaluate`
- [ ] Benchmark runs recorded per `benchmark-report.md`; `docs/PERFORMANCE.md` first section
- [ ] Tests: evaluator unit tests (rule types, priorities, disabled, missing flag, missing attribute), `SnapshotCacheIT` (Testcontainers Redis + Postgres), `InvalidationAfterCommitIT`, `RedisDownDegradesIT`, `SdkKeyAuthIT`
- [ ] Tag `v0.5-mvp`

### Acceptance summary

- Evaluation is deterministic and matches the conformance vectors.
- Publishing invalidates the cache after commit; the next evaluation reflects the new version (test).
- Redis down → evaluations still succeed (test), with a warning metric/log.
- p99 for `/evaluate` measured under a defined load with warm hit rate, recorded with methodology.
- `/snapshot` supports `ETag` and `304`.

### Verification tests

| Test | Given | When | Then |
|---|---|---|---|
| `BucketingVectorsTest` | vectors file | `bucket(...)` | equals expected for all 20 |
| `BucketDistributionTest` | 100k random keys | bucket | each decile 10 % ± 1 % |
| `EvaluatorRulePriorityTest` | user rule p1, attr rule p2 | user matches both | serves p1's variation, reason `RULE_MATCH:p1` |
| `EvaluatorMissingAttributeTest` | attr rule on `country` | context lacks `country` | rule skipped, default served |
| `InvalidationAfterCommitIT` | cached snapshot v7 | publish v8 | first `/evaluate` after response returns `version: 8` |
| `CacheRaceIT` | failure point before commit | reader repopulates | after commit, cache holds v8 (Lua compare-version SET or TTL) |
| `RedisDownDegradesIT` | Redis container paused | `/evaluate` | 200 with correct value; log `cache unavailable` |
| `SnapshotEtagTest` | `If-None-Match: "7"` | version still 7 | 304 |

### Failure scenarios to run

1. Redis down at startup / mid-run (degrade and recover).
2. Stampede on TTL expiry (observe; fix in M4 with single-flight, or now if time allows).
3. Publish/read race (after-commit invalidation).
4. Malformed snapshot in Redis (someone `SET` garbage) → treat as miss, rebuild, log.
5. SDK key revoked mid-session → 401 on next call; how quickly (cache of key hashes?).

### GitHub expectations

- Milestone M2 closed; `docs/PERFORMANCE.md` with the first table; `bucketing-vectors.json` in `eval/src/test/resources`.
- `v0.5-mvp` tag; README's "How evaluation works" section with the algorithm and reasons.

## 9. Git activity

- Branches: `feat/m2-eval-core`, `feat/m2-snapshot-cache`, `feat/m2-eval-api`, `perf/m2-benchmarks`.
- Keep the `eval` module free of Spring dependencies — enforce with Maven (no `spring-*` in its `pom.xml`) so M3's SDK can depend on it.

## 10. Interview preparation

**Track A (Python coding)**
- **Weekly coding mock** (Tue, 45 min, Python): [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md); ask for a hashing/design-a-data-structure question (e.g. LRU cache, 146 — `OrderedDict` in Python, then the Java rep) to connect with this week.

**Track B (Java / projects / system design)**
- **Weekly engineering mock** (Sat, 30 min): "walk me through what happens on `/evaluate` when Redis is down" and "how did you measure p99?"
- **Résumé defense:** Redis and Java — [`17-resume-tech-defense/redis.md`](../../17-resume-tech-defense/redis.md), [`17-resume-tech-defense/java.md`](../../17-resume-tech-defense/java.md); 3 questions each from [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md).
- **System-design prep for W22 mock:** 45 min — practise "design a feature-flag service" with the latency numbers you measured this week; read [`16-interview-prep/system-design-interview.md`](../../16-interview-prep/system-design-interview.md).
- **Applications:** continue at the junior-ready tier ([`JOB_READINESS.md`](../../JOB_READINESS.md)); FlagForge MVP can now appear on the résumé as "in progress" with honest scope.
- No OA sim this week — the hours go to C++ and the benchmark.

## 11. Revision

- Re-explain cache-aside vs write-through vs write-behind and where each project used which ([`15-system-design/caching.md`](../../15-system-design/caching.md)).
- Re-do the FlowGrid Redis-down degrade path in your head; compare with this week's — is the code shape the same? Should it be a shared pattern in your notes?
- DSA reviews.

## 12. Daily plan

| Day | Plan |
|---|---|
| **Mon (8 h)** | Learning 2: bucketing + rule-engine design (1) · C++ (1) · Project 4.5: `eval` module, bucketing + vectors · DSA 1.5 |
| **Tue (8 h)** | Project 5: evaluator + unit tests, snapshot builder · DSA 2 + reviews · Track A mock 45 min + review |
| **Wed (8 h)** | Learning 2: Redis snapshot caching + after-commit invalidation (1) · C++ (1) · Project 4.5: cache-aside, invalidation, degrade · DSA 1.5 |
| **Thu (8 h)** | Project 5: SDK-key auth, `/evaluate`, `/snapshot` ETag, ITs · DSA 2 (timed unseen Medium) · Docs 1: "How evaluation works" |
| **Fri (5 h)** | Project 3: JMH + k6 runs · C++ (1) · DSA reviews 1 + Java rep (146) |
| **Sat (6 h)** | Project 4: failure scenarios, PERFORMANCE.md, tag MVP · C++ (1) · Track B mock 30 min + system-design practice 30 min |
| **Sun (2–3 h)** | End-of-week test · reviews · trackers · plan W22 · rest |

## 13. End-of-week test

1. Write the bucketing function and explain why `String.hashCode()` was rejected.
2. Draw the cache-aside race and explain after-commit invalidation plus the remaining window.
3. State your measured p50/p95/p99 for `/evaluate` at 200 VUs and the breakdown of where the time goes.
4. In C++: what is RAII; what does `std::unique_ptr` guarantee that a raw pointer does not?
5. Timed, Python: LeetCode 146 (LRU Cache) in 30 min with `OrderedDict` **and** with a hand-written doubly linked list, explaining the invariants out loud.

Pass: 4/5.

## 14. Mastery checklist

- [ ] I can implement deterministic percentage bucketing and prove distribution + stickiness
- [ ] I can design a first-match rule engine with reasons, without reflection or exceptions on the hot path
- [ ] I can implement cache-aside with correct invalidation timing and degrade behaviour
- [ ] I can measure and explain p99 latency with a breakdown
- [ ] I can read and write basic C++ (values/pointers/RAII/STL containers)

## 15. Expected deliverables

- FlagForge `v0.5-mvp`; `docs/PERFORMANCE.md` section 1 with methodology.
- Trackers: project (M2 + numbers), DSA (6 new in Python with selection reasons + reviews + Java rep), interview (Track A + B mocks), technology (Redis "cache design + measurement"; C++ "basics"), weekly progress.

## 16. If behind / stretch

**Behind:** skip `/evaluate/all` and the in-process holder (Redis per request is fine for MVP); do not skip the conformance vectors — M3 depends on them. C++ can shrink to 2 h (hello world + two-sum) and finish in W25.

**Stretch:** semver operators (`SEMVER_GT`), compact binary snapshot format with a size/latency comparison, per-flag evaluation counters in Redis (`INCR` with pipelining) for the M4 dashboard.
