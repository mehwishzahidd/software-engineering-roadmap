# FlagForge — Milestones M1–M4 (Weeks 20–23)

> Every milestone follows the same loop: **know first → requirements → architectural guidance →
> acceptance criteria → now implement it → verification tests → debugging scenarios → interview
> questions.** Read [README.md](./README.md) first; section numbers below (§9, §13 …) refer to it.
> Hour estimates are the ~25–30 h/week project budget from [ROADMAP §3](../../ROADMAP.md#3-time-allocation).
> One milestone = one GitHub milestone = several PRs.

| Milestone | Week | Tier | GitHub milestone name |
|---|---|---|---|
| [M1 — Model, versioning, audit](#m1--model-versioning-audit-week-20) | 20 | — | `M1 Model & versioning` |
| [M2 — Evaluation engine + cache](#m2--evaluation-engine--cache-week-21) | 21 | **MVP** | `M2 Evaluation & cache` |
| [M3 — Java SDK (+ Python SDK, simulator)](#m3--java-sdk--python-sdk-simulator-week-22) | 22 | — | `M3 SDKs` |
| [M4 — Dashboard, propagation, deploy](#m4--dashboard-propagation-deploy-week-23) | 23 | **v1.0** | `M4 Dashboard, propagation & deploy` |

Before M1: create the repo `flagforge`, the multi-module Maven skeleton (`flagforge-common`,
`flagforge-server`, `flagforge-sdk` empty), `docker-compose.yml` with Postgres 16 + Redis 7, a
`ci.yml` running `mvn -B verify`, and the four GitHub milestones. You have done this three times;
budget 2 h, not a day.

---

## M1 — Model, versioning, audit (Week 20)

### M1.1 What to know first

| Topic | Read | Why now |
|---|---|---|
| Multi-tenant schema, uniqueness scopes, UUID keys | [04-sql-databases/04-schema-design.md](../../04-sql-databases/04-schema-design.md) | org → project → environment → flag scoping |
| JSONB columns, operators, GIN indexes | [04-sql-databases/03-advanced-queries.md](../../04-sql-databases/03-advanced-queries.md) | `rules jsonb` decision (README §8.3) |
| Privileges, append-only tables | [04-sql-databases/06-transactions.md](../../04-sql-databases/06-transactions.md), [14-cs-fundamentals/database-internals.md](../../14-cs-fundamentals/database-internals.md) | immutability of versions/audit via `REVOKE` |
| JPA mapping of JSONB (`@JdbcTypeCode(SqlTypes.JSON)` in Hibernate 6) | [05-spring-boot/03-data-jpa.md](../../05-spring-boot/03-data-jpa.md) | version body persistence |
| Spring Security 6 JWT + method security | [05-spring-boot/05-security-jwt.md](../../05-spring-boot/05-security-jwt.md) | dashboard auth + org roles |
| ProblemDetail, validation | [05-spring-boot/04-validation-errors.md](../../05-spring-boot/04-validation-errors.md) | error catalogue README §10.3 |
| API design, cursor pagination, action endpoints (`:publish`) | [06-rest-apis/api-design-guide.md](../../06-rest-apis/api-design-guide.md) | admin API |
| System design fundamentals (first week of the topic) | [15-system-design/fundamentals.md](../../15-system-design/fundamentals.md) | vocabulary for the design of M2–M4 |

Concept to internalise this week: **configuration as immutable versions**. A "flag config" is
never edited; a *draft* is edited, and *publishing* freezes it as version N+1. Everything else
(rollback, audit, snapshot, ETag) falls out of that decision.

### M1.2 Requirements

- [ ] Flyway migrations for all tables in README §8.2 (drafts, versions, snapshots table optional now).
- [ ] Registration/login → JWT; organizations; memberships with roles; projects; environments seeded `dev/staging/prod` (`prod.protected = true`).
- [ ] Flags: create/list/get/patch/archive; key immutable and unique per project; `salt` generated at creation (16 random bytes, hex).
- [ ] Draft config per (env, flag): `GET`/`PUT` with full body (enabled, offVariation, variations, targets, rules[], defaultRollout). Semantic validation → `422 invalid-rule`.
- [ ] Publish: draft → new immutable `flag_config_version` (version N+1), comment required, `published_by`, `published_at`; increments `environment.current_env_version`.
- [ ] Rollback: `versions/{n}:rollback` → version N+1 copying n; `rolled_back_from_version = n`.
- [ ] Kill switch: `:toggle` publishes a version with only `enabled` changed (no draft needed).
- [ ] Version history (cursor pagination) and single version fetch.
- [ ] Audit event for every mutating call, written in the same transaction; audit feed endpoint.
- [ ] Authorization matrix README §12.3 incl. protected environments → `403 protected-environment`.
- [ ] SDK keys per environment: create (plaintext returned once), list (prefix only), revoke. Rotation in M4.
- [ ] OpenAPI (`admin` group) with examples; CI green.

### M1.3 Architectural guidance

**Publish sequence (transactional):**

```
PUT draft ... (many times)
POST /environments/{env}/flags/{key}/versions:publish {comment}
  ├─ authz: canPublish(env)  (role ≥ EDITOR; ADMIN if env.protected)
  ├─ BEGIN
  │   SELECT ... FROM flag_config_draft WHERE env_id=? AND flag_id=? FOR UPDATE   ← serialises publishes
  │   validate(draft)                                                              ← 422 if invalid
  │   next = SELECT coalesce(max(version),0)+1 FROM flag_config_version WHERE env_id=? AND flag_id=?
  │   INSERT flag_config_version (…, version=next, body=draft, published_by, comment)
  │   UPDATE environment SET current_env_version = current_env_version + 1 WHERE id=? RETURNING current_env_version
  │   INSERT audit_event (VERSION_PUBLISHED, before=previous version body, after=new body)
  │   [M2+] rebuild snapshot → Redis SET → PUBLISH   (after commit — TransactionSynchronization.afterCommit)
  └─ COMMIT → 201 {version:next, envVersion}
```

Why `FOR UPDATE` on the draft row: two admins clicking publish at once must produce v7 and v8,
not two v7s — the unique constraint would reject one, but a lock gives a clean sequential result
instead of a 409. Same technique as FlowGrid reservations. See
[04-sql-databases/06-transactions.md](../../04-sql-databases/06-transactions.md).

**Rules body as a value object.** Map the JSONB into Java records in `flagforge-common`
(`FlagConfig`, `Rule`, `Condition`, `Rollout`) — the *same* records the evaluator (M2) and SDK
(M3) use. The entity holds the JSON; the service converts. Do not put JPA annotations on the
common records.

**Validation layers:** Bean Validation for shape (`@NotBlank key`, `@Size(max=50) rules`),
a `ConfigValidator` for semantics (operators known, referenced variations exist, percentage
`0 ≤ p ≤ 100` with ≤ 2 decimals, `priority` unique, `bucketBy` non-blank). Return all problems at
once in `errors[]`.

**Audit:** an explicit `AuditRecorder.record(action, entityType, entityId, before, after)` called
from services inside the transaction beats AOP magic here — you want `before`/`after` bodies, and
AOP does not know them. Add the `request_id` from a filter (`X-Request-Id`, generated if absent).

**Authorization:** one `Authz` bean: `role(userId, orgId)`, `canEdit(envId)`, `canPublish(envId)`;
resolve env → project → org with one join query; use `@PreAuthorize("@authz.canPublish(#envId)")`.

### M1.4 Acceptance criteria

- [ ] `mvn verify` green in CI with Testcontainers Postgres.
- [ ] Publishing twice creates versions 1 and 2; `GET versions` lists both newest-first with author, comment, timestamp.
- [ ] `UPDATE flag_config_version SET comment='x'` as the app DB role fails with `permission denied` (prove it in a test).
- [ ] Rollback to v1 creates v3 whose body equals v1 and whose `rolled_back_from_version = 1`.
- [ ] EDITOR can publish to `dev`, gets `403 protected-environment` on `prod`; ADMIN succeeds.
- [ ] A user of org A requesting org B's flag gets `404` (not `403`) — no existence leak.
- [ ] Every mutating endpoint produces exactly one audit event; audit feed paginates by cursor.
- [ ] SDK key plaintext appears exactly once in any response; DB holds only hash + prefix.
- [ ] Swagger UI shows the admin group; every endpoint has examples and error responses.
- [ ] `docs/DATABASE.md` has the ERD and the JSONB-vs-normalized ADR.

### M1.5 Now implement it (≈ 27 h)

| # | Task | h |
|---|---|---|
| 1 | Skeleton, Compose, CI, Flyway `V1__baseline.sql` (all tables, indexes, constraints) | 2.5 |
| 2 | Auth: register/login, JWT filter, `CurrentUser`; orgs + memberships + roles; `Authz` bean | 4 |
| 3 | Projects + environments (seed dev/staging/prod, `protected`), flags (key validation, salt, archive) | 3 |
| 4 | Common records for config body + Jackson mapping + JSONB entity attribute + `ConfigValidator` | 3.5 |
| 5 | Draft `GET/PUT`; publish (transaction, `FOR UPDATE`, version numbering, env version bump) | 3.5 |
| 6 | Rollback, toggle, version history (cursor), single version, diff endpoint (returns both bodies) | 2.5 |
| 7 | `AuditRecorder` + audit feed; request-id filter; `ProblemDetail` advice for the whole catalogue | 3 |
| 8 | SDK keys: generation (`ffs_`/`ffc_` + 32 base62), hash, create/list/revoke | 1.5 |
| 9 | `V4__revoke_updates.sql` (separate migration role vs app role in Compose), immutability test | 1.5 |
| 10 | OpenAPI groups + examples; `docs/DATABASE.md` (ERD + ADR); milestone issues closed | 2 |

### M1.6 Verification tests you write

| Test | Asserts |
|---|---|
| `publish_createsVersionOnePlusOne` | two publishes → versions `[2,1]`; `environment.current_env_version == 2` |
| `publish_concurrent_producesSequentialVersions` | 10 threads publish the same draft → versions 1..10, no exception, no duplicates |
| `publish_invalidRule_returns422WithAllErrors` | unknown operator + percentage 120 → one `422` with two entries in `errors[]` |
| `rollback_createsNewVersionCopyingOld` | v3 body deep-equals v1; `rolledBackFromVersion == 1`; audit action `ROLLBACK` |
| `version_updateRejectedByDatabase` | `jdbcTemplate.update("update flag_config_version …")` as app role throws `permission denied` |
| `toggle_publishesVersionWithOnlyEnabledChanged` | body equal except `enabled`; comment starts with `"Kill switch"` |
| `authz_editorCannotPublishToProtectedEnv` | 403 `protected-environment`, audit event `ACCESS_DENIED` |
| `tenancy_crossOrgFlagIs404` | user in org A → org B flag id → 404 |
| `audit_everyMutationWritesExactlyOneEvent` | parameterized over create flag / put draft / publish / rollback / revoke key |
| `sdkKey_plaintextNeverPersisted` | DB `key_hash == sha256(plaintext)`, `prefix == plaintext.substring(0,8)` |
| `flagKey_immutable_patchIgnoresOrRejects` | PATCH with new key → 400 |

### M1.7 Debugging scenarios

1. **Two publishes → `duplicate key value violates unique constraint`.** You forgot `FOR UPDATE` or computed `next` outside the transaction. Reproduce with the concurrent test; look at `pg_stat_activity` while it runs.
2. **JSONB comes back as a `String` or fails to map.** Hibernate 6 needs `@JdbcTypeCode(SqlTypes.JSON)`; check the column type is `jsonb`, not `json`; check Jackson is configured for records.
3. **Audit event missing after a 403.** Method security throws before the service; audit denials in an `AccessDeniedHandler` or a `@ControllerAdvice`, not in the service.
4. **`REVOKE` migration fails locally but not in CI.** You run migrations as the superuser locally; create the two roles in Compose `init.sql` and run the app as `flagforge_app` everywhere.
5. **Rollback of v1 when the current is v1 → identical body publish.** Decide: allow (audit says so) or 409 `conflict` (empty diff)? Write the ADR.

### M1.8 Interview questions (M1)

- Why are flag configurations stored as immutable versions instead of updated in place? What does rollback mean under that model?
- JSONB document vs normalized rule tables — what did you choose and what does it cost you?
- How do you guarantee version numbers are sequential under concurrent publishes?
- How is immutability enforced at the database level? Why is that stronger than "we just don't call update"?
- How do org roles and protected environments combine in your authorization check?
- Why return 404 rather than 403 for another tenant's resource?
- Why store SDK keys hashed when they are random anyway?

---

## M2 — Evaluation engine + cache (Week 21)

### M2.1 What to know first

| Topic | Read | Why now |
|---|---|---|
| Hashing for bucketing; uniformity; salts | [03-dsa/02-hashing.md](../../03-dsa/02-hashing.md) (concept), README §9.3 | deterministic rollout |
| Caching strategy: cache-aside, TTL, invalidation, hierarchy | [15-system-design/caching.md](../../15-system-design/caching.md) | Redis snapshot + in-process cache |
| Redis strings, `SET`, TTL, `PUBLISH`, Spring Data Redis | [04-sql-databases/redis.md](../../04-sql-databases/redis.md) | snapshot store |
| Spring caching abstraction vs manual (Caffeine) | [05-spring-boot/08-caching-scheduling.md](../../05-spring-boot/08-caching-scheduling.md) | decide: manual cache for control over ETag |
| HTTP caching: `ETag`, `If-None-Match`, `304` | [06-rest-apis/http-for-apis.md](../../06-rest-apis/http-for-apis.md) | `/sdk/v1/config` |
| Records, sealed interfaces, pattern matching (`switch` on operator) | [01-java/04-modern-java.md](../../01-java/04-modern-java.md) | evaluator implementation |
| Property-based testing (jqwik) | [09-testing/junit5.md](../../09-testing/junit5.md) | determinism/distribution tests |
| k6 basics (from FlowGrid M5) | [18-projects/templates/benchmark-report.md](../templates/benchmark-report.md) | p99 measurement |

Also this week: C++ basics (≈4 h) per [ROADMAP W21](../../ROADMAP.md#5-week-by-week-master-table) — keep it out of project hours.

### M2.2 Requirements

- [ ] `flagforge-common` evaluator implementing README §9.2 exactly: off → targets → rules by priority (first match, all conditions) → default; fixed variation or rollout; reasons.
- [ ] Operators: `EQUALS, NOT_EQUALS, IN, NOT_IN, CONTAINS, STARTS_WITH, ENDS_WITH, GT, GTE, LT, LTE`; typed comparison; missing attribute never matches.
- [ ] Bucketing: `bucket(flagKey, salt, userKey) ∈ [0,10000)`; `inRollout = bucket < round(pct×100)`; `bucketBy` attribute (falls back to `key`).
- [ ] `SnapshotBuilder`: all current versions of an environment → `EnvironmentSnapshot` (README §10.2 shape) with `envVersion` and `etag = "<envVersion>-<sha256(json)[0:12]>"`.
- [ ] `SnapshotCache`: in-process (Caffeine, TTL 60 s) → Redis `snapshot:{envId}` (no TTL or long TTL) → DB rebuild (single-flight). Publish writes through and evicts.
- [ ] SDK-key auth filter (server vs client kind); `GET /sdk/v1/config` with `ETag`/`If-None-Match`/`304`, gzip; `POST /sdk/v1/evaluate`, `POST /sdk/v1/evaluate-all`; admin `evaluate-debug`.
- [ ] Rate limit per key on SDK endpoints (Redis token bucket, reuse your Lua script) → `429` + `Retry-After`.
- [ ] Metrics: `flagforge_evaluations_total{reason}`, `flagforge_cache_hits_total{level}`, `flagforge_snapshot_age_seconds`.
- [ ] Redis unavailable → evaluation still works from in-process cache or DB; logged once; readiness reflects it, liveness does not.
- [ ] **p99 measured** for `/sdk/v1/evaluate` with k6 (three cache states) and recorded in `docs/PERFORMANCE.md`.
- [ ] `contract/bucket-vectors.json` (≥ 20 entries) and first `contract/evaluation-vectors.json` cases generated *from* the Java evaluator and reviewed by hand.

### M2.3 Architectural guidance

**Evaluation sequence (server-side):**

```
POST /sdk/v1/evaluate {flagKey, context}
  SdkKeyFilter: hash → sdk_key row (cache 60 s) → SdkPrincipal{envId, kind}
  RateLimiter.check(keyId)                                   → 429?
  snapshot = SnapshotCache.get(envId)                        (Caffeine → Redis → single-flight DB rebuild)
  result   = Evaluator.evaluate(snapshot.flag(flagKey), context)   ← pure function, no I/O
  200 {value, variation, reason:{kind, ruleId, inRollout, bucket?}, envVersion, flagVersion}
```

**Evaluator design.** A pure function `EvaluationResult evaluate(FlagConfig flag, EvaluationContext ctx)`.
Model `Operator` as an enum with a `boolean test(Object attr, List<Object> values)` per constant
(a `switch` with pattern matching is also fine). Comparison rules: numbers via `BigDecimal`
(or `double` — pick, document, put it in vectors); strings exact case; `IN` over `values`.
`serve` is a sealed interface `Serve permits FixedVariation, Rollout`. The evaluator must
**never throw** — wrap and return `Reason.ERROR`.

```java
// Snippet (≤25 lines): rule matching skeleton — the shape, not the whole evaluator
Optional<Rule> firstMatch(List<Rule> rules, EvaluationContext ctx) {
    return rules.stream()
        .sorted(Comparator.comparingInt(Rule::priority))       // or pre-sorted at snapshot build
        .filter(r -> r.conditions().stream().allMatch(c -> matches(c, ctx)))
        .findFirst();
}
boolean matches(Condition c, EvaluationContext ctx) {
    Object attr = ctx.attribute(c.attribute());               // null when absent
    return attr != null && c.operator().test(attr, c.values());   // absent ⇒ never matches
}
```

**Snapshot build.** One query: current version per flag of the env (`DISTINCT ON (flag_id) …
ORDER BY flag_id, version DESC` or a window function — a good `EXPLAIN` exercise). Serialise once
to bytes; store the bytes and the etag; keep the parsed object in the in-process cache for
server-side evaluation so `/evaluate` never parses JSON.

**Cache hierarchy and single-flight.** `SnapshotCache.get(envId)`:

```
local (Caffeine, expireAfterWrite 60 s)      hit → return
Redis GET snapshot:{envId}                   hit → parse, put local, return   (log miss level=local)
single-flight: inflight.computeIfAbsent(envId, id -> CompletableFuture.supplyAsync(rebuildFromDb))
   → rebuild, Redis SET, put local, remove inflight                          (log miss level=redis)
```

Publish path (M1 transaction, after commit): `rebuild → Redis SET → local put` **before** the
response returns, so the next `/config` sees it. `PUBLISH` (M4) comes after `SET`.

**ETag.** Compute from `envVersion` + content hash. Controller: if `If-None-Match` equals the
current etag → `304` with the same `ETag` header and no body. Use Spring's
`ResponseEntity.ok().eTag(etag)…` and `ServletWebRequest.checkNotModified(etag)` — it does the
comparison including weak/strong quoting rules for you.

**Why measure p99 in three states:** local hit, Redis-only (disable Caffeine via property), cold
(flush Redis before each request with a k6 setup hook, or a `?nocache=1` test-only property).
The gap between them is your caching story in interviews.

### M2.4 Acceptance criteria

- [ ] `mvn verify` includes ≥ 60 evaluator unit tests + property tests + vectors test; all green.
- [ ] `curl -H "Authorization: Bearer ffs_…" /sdk/v1/config` returns snapshot with `ETag`; second call with `If-None-Match` → `304`.
- [ ] After publish, `/config` returns the new `envVersion` and a different `ETag` within 100 ms on the same node.
- [ ] `docker stop redis` → `/evaluate` still 200 (local cache), then after 60 s still 200 (DB rebuild, single-flight, one log line `snapshot.cache miss level=redis`); `docker start redis` → Redis repopulated on next miss.
- [ ] Client-side key on `/config` → `403`; revoked key → `401`; 100 rapid calls → some `429` with `Retry-After`.
- [ ] `docs/PERFORMANCE.md` has p50/p95/p99 for `/evaluate` at ≥ 2 load levels × 3 cache states, with the [benchmark template](../templates/benchmark-report.md) filled in.
- [ ] `contract/bucket-vectors.json` committed; `docs/EVALUATION.md` written (the spec, README §9, with your decisions on numbers/casing/missing attributes).
- [ ] Tag `v0.1` (MVP).

### M2.5 Now implement it (≈ 28 h)

| # | Task | h |
|---|---|---|
| 1 | `docs/EVALUATION.md` — write the spec first, including edge decisions | 1.5 |
| 2 | `Bucketing` + hash vectors; `Operator` enum with typed comparisons | 2.5 |
| 3 | `Evaluator` (off/targets/rules/rollout/default/reasons/never-throw) + unit tests | 5 |
| 4 | Property test (determinism), distribution test (100k), monotonic rollout test | 2 |
| 5 | `SnapshotBuilder` + query (`DISTINCT ON`), etag, serialisation; `environment_config_snapshot` table (optional) | 3 |
| 6 | `SnapshotCache` hierarchy: Caffeine + Redis + single-flight + metrics + Redis-down fallback | 4 |
| 7 | SDK-key filter (kind, cache), `/sdk/v1/config` (ETag/304/gzip), `/evaluate`, `/evaluate-all`, `evaluate-debug` | 4 |
| 8 | Rate limiter (Redis Lua token bucket) + `429`; OpenAPI `sdk` group; export `sdk-openapi.yaml` in CI | 2 |
| 9 | Integration tests (Testcontainers Postgres + Redis): publish → snapshot → etag → 304; Redis down | 2.5 |
| 10 | k6 script `perf/evaluate.js`, run 3 cache states × 2 loads, `docs/PERFORMANCE.md` | 2.5 |

### M2.6 Verification tests you write

| Test | Asserts |
|---|---|
| `evaluate_flagDisabled_returnsOffVariationWithReasonOff` | `enabled=false` ignores targets and rules |
| `evaluate_targetMatch_beatsRules` | user in `targets.on` gets `on` even if a rule says `off`; reason `TARGET_MATCH` |
| `rules_higherPriorityWins` | rule p0 → `off`, rule p1 → `on`, context matches both → `off`, `ruleId == "r0"` |
| `rules_allConditionsMustMatch` | two conditions, one fails → rule skipped |
| `rules_missingAttributeNeverMatches` | `NOT_IN` on an absent attribute → no match (documented decision) |
| `operators_numericComparisonIsNumeric` | `"10" GT "9"` as numbers true; as strings would be false — attribute typed as number |
| `evaluate_sameUserSameFlag_alwaysSameBucket` (jqwik `@Property`, 10 000 tries) | `bucket(f, s, u) == bucket(f, s, u)` and equals a second evaluator instance's result |
| `bucket_differentSaltDecorrelatesFlags` | over 10k users, share of users in-rollout for both flag A and flag B at 10 % ≈ 1 % (± 0.3), not 10 % |
| `rollout_10Percent_over100kUsers_within1Point` | users `u0..u99999` at 10.00 % → count in `[9 000, 11 000]`; tighten to ± 0.5 if it passes |
| `rollout_isMonotonic_raisingPercentageKeepsUsers` | set of users in at 10 % ⊆ set in at 25 % |
| `rollout_bucketBy_accountId_groupsUsers` | two contexts with same `accountId`, different keys → same result |
| `bucket_matchesContractVectors` | parameterized over `contract/bucket-vectors.json` |
| `evaluate_matchesContractVectors` | parameterized over `contract/evaluation-vectors.json` |
| `evaluate_malformedRule_returnsDefaultWithReasonError` | operator throwing → caller default, reason `ERROR`, no exception |
| `snapshot_etagUnchanged_returns304` | `@SpringBootTest`: `GET /config` → etag E; `GET` with `If-None-Match: E` → 304 empty body; after publish → 200 with etag ≠ E |
| `snapshot_publishInvalidatesLocalAndRedis` | Redis key content changes; Caffeine entry replaced; `envVersion` +1 |
| `snapshot_redisDown_fallsBackToDbOnce` | stop Redis container (Testcontainers `stop()`) → 20 concurrent `/config` → exactly one `snapshot.rebuilt` log/metric increment |
| `sdkApi_clientKeyCannotFetchConfig` | 403 |
| `sdkApi_rateLimitReturns429WithRetryAfter` | burst > limit → 429 + header |

### M2.7 Debugging scenarios

1. **Distribution test gives 8.7 %.** Your "first 4 bytes" are being sign-extended (`digest[0]` is a signed byte). Mask with `& 0xFF`. Or you hashed `flagKey + userKey` without a separator: `"ab"+"c"` = `"a"+"bc"`.
2. **Same user gets different results on two nodes.** Salt not in the snapshot (one node used `""`); or rules pre-sorted on one node only — sort at build time and assert sorted in the evaluator.
3. **`304` never returned.** Etag quoting: header must be `"57-9f1c…"` with quotes; compare using Spring's `checkNotModified`. Check a proxy isn't stripping `If-None-Match`.
4. **After publish, `/config` on node B is stale for 60 s.** Expected until M4's pub/sub — that is the TTL. Write it down as the reason M4 exists.
5. **p99 jumps every 60 s.** Caffeine expiry causes a synchronous Redis fetch on the request thread. Use `refreshAfterWrite` (async refresh, serve stale meanwhile) instead of `expireAfterWrite`.
6. **Redis down → every request slow (2 s).** Your Redis client timeout is too long and the fallback waits for it on each call. Lower the command timeout (e.g. 200 ms) and add a circuit breaker flag: after a failure, skip Redis for 5 s.

### M2.8 Interview questions (M2)

- Walk me through evaluation order. Why "first match by priority" rather than "most specific"?
- How does percentage rollout stay consistent for a user across time, nodes and SDK versions? What is the salt for?
- Why SHA-256 vs MurmurHash3 vs `hashCode()`? Why must the hash be frozen after release?
- What does `bucket < pct × 100` give you that `random() < pct` doesn't?
- Describe your cache hierarchy. What happens when Redis dies? When Postgres dies?
- How does `ETag`/`304` reduce load? What is in your ETag and why not just the version number?
- What is single-flight and where did you need it?
- What did you measure and what were the numbers in the three cache states? What dominated latency?

---

## M3 — Java SDK (+ Python SDK, simulator) (Week 22)

### M3.1 What to know first

| Topic | Read | Why now |
|---|---|---|
| Java concurrency: `AtomicReference`, `ScheduledExecutorService`, daemon threads, `CompletableFuture` | [01-java/07-concurrency.md](../../01-java/07-concurrency.md), [14-cs-fundamentals/concurrency.md](../../14-cs-fundamentals/concurrency.md) | snapshot swap, poller, SSE thread |
| `java.net.http.HttpClient` (timeouts, `BodyHandlers.ofLines`, async) | JDK docs; [14-cs-fundamentals/networking.md](../../14-cs-fundamentals/networking.md) | transport without Spring |
| Library/API design, semantic versioning, Maven publishing | [01-java/08-maven-build.md](../../01-java/08-maven-build.md), [06-rest-apis/api-design-guide.md](../../06-rest-apis/api-design-guide.md) (SDK-facing API) | public surface, GitHub Packages |
| SSE protocol (`text/event-stream`, `retry`, `id`) and reconnect | your ForgeCI M3 notes; [14-cs-fundamentals/networking.md](../../14-cs-fundamentals/networking.md) | streaming (advanced) |
| WireMock / MockWebServer fault injection | [09-testing/mockito.md](../../09-testing/mockito.md), [09-testing/testcontainers.md](../../09-testing/testcontainers.md) | resilience tests |
| Python: packaging (`pyproject.toml`), type hints, `pytest` fixtures, `threading`, `urllib.request`/`http.client`, `hashlib` | [19-python/04-testing-and-scripting.md](../../19-python/04-testing-and-scripting.md), [19-python/python-for-java-devs.md](../../19-python/python-for-java-devs.md), [19-python/03-pitfalls-and-complexity.md](../../19-python/03-pitfalls-and-complexity.md) | `sdk-python/`, `tools/rollout_sim.py` |
| System-design interview method (mock #1 this week) | [16-interview-prep/system-design-interview.md](../../16-interview-prep/system-design-interview.md) | "design a feature-flag service" |

### M3.2 Requirements

**Java SDK (`flagforge-sdk`, no Spring — README §13.7):**
- [ ] `FlagClient.builder()` with `sdkKey`, `baseUrl`, `pollingInterval` (default 30 s, min 5 s), `connectTimeout`, `readTimeout`, `initTimeout`, `staleAfter`, `streaming(false)`, `offline(...)`, `logger`; `build()` validates and starts.
- [ ] `isEnabled`, `variation` (String/Boolean overloads; typed default), `*Detail` variants with reason, `status()`, `snapshotVersion()`, `close()` (`AutoCloseable`).
- [ ] Local snapshot in `AtomicReference`, `swapIfNewer`; local evaluation via `flagforge-common`.
- [ ] Poller: initial fetch (blocking up to `initTimeout`), periodic with `If-None-Match`, jitter ±10 %, exponential backoff with cap on errors, `401` → stop + status `UNAUTHORIZED`.
- [ ] Fallbacks per README §13.4; stale-if-error; status transitions logged once.
- [ ] Offline mode (`offline(Map)` / bootstrap JSON).
- [ ] `flagforge-sdk-sample` app; `docs/SDK.md`; javadoc on public types; `sdk-v0.1.0` published to GitHub Packages from CI.
- [ ] Unit tests (WireMock) + contract tests against the real server (Testcontainers image or same-JVM `@SpringBootTest`).
- [ ] **Advanced (do after everything above is green):** SSE streaming — `GET /sdk/v1/stream` on the server (`SseEmitter`, registry, `ping` every 20 s) and `SseConnection` in the SDK (parse frames, `changed` → fetch with ETag, 45 s silence watchdog, reconnect with backoff, fall back to polling after 3 consecutive failures).

**Shared contract + Python SDK + simulator:**
- [ ] `contract/evaluation-vectors.json` grown to ≥ 40 cases covering every reason and operator; `contract/snapshot-schema.json`.
- [ ] `sdk-python/` package `flagforge`: `FlagClient(sdk_key, base_url, polling_interval=30, init_timeout=5, offline=None)`, `is_enabled`, `variation`, `variation_detail`, `close()`; evaluator + bucketing **written from `docs/EVALUATION.md`**, not translated from the Java source; polling thread with `If-None-Match`; stale-if-error; defaults; offline mode; `mypy --strict` clean; README.
- [ ] `test_python_and_java_sdk_agree_on_vectors` and bucket-vector tests in pytest.
- [ ] `tools/rollout_sim.py`: `--snapshot file|--url … --sdk-key …`, `--flag`, `--users N`, `--percent P [--then-percent Q]`, `--check-server K` → prints share, tolerance verdict, stickiness verdict, mismatch count; exit code; pytest-tested.

### M3.3 Architectural guidance

**SDK poll with ETag (sequence):**

```
build():   holder=EMPTY; http=HttpClient(connectTimeout); exec=single daemon thread
           fetchOnce() [blocks ≤ initTimeout] → schedule(poll, interval±jitter)
poll():    req = GET {baseUrl}/sdk/v1/config  Authorization: Bearer <key>  If-None-Match: <lastEtag>
           200 → parse (schemaVersion check) → holder.swapIfNewer(snap) → lastEtag=ETag → status READY, backoff=0
           304 → lastSuccess=now → status READY
           401 → status UNAUTHORIZED; stop scheduling; log ERROR once
           5xx/timeout/IOException → keep snapshot; backoff=min(cap, backoff*2)+jitter; if now-lastSuccess>staleAfter → STALE (log WARN once)
```

```java
// Snippet (≤25 lines): the swap — readers never block, out-of-order responses are ignored
private final AtomicReference<Snapshot> ref = new AtomicReference<>(Snapshot.EMPTY);

Snapshot current() { return ref.get(); }   // called on every evaluation, no lock

boolean swapIfNewer(Snapshot next) {
    while (true) {
        Snapshot cur = ref.get();
        if (next.envVersion() <= cur.envVersion()) return false;   // stale or duplicate
        if (ref.compareAndSet(cur, next)) return true;
    }
}
```

```java
// Snippet (≤25 lines): ETag handling in the poller
HttpRequest.Builder b = HttpRequest.newBuilder(cfgUri).timeout(readTimeout)
        .header("Authorization", "Bearer " + sdkKey).header("Accept", "application/json");
String etag = lastEtag.get();
if (etag != null) b.header("If-None-Match", etag);
HttpResponse<byte[]> res = http.send(b.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
switch (res.statusCode()) {
    case 200 -> { Snapshot s = codec.parse(res.body());
                  if (holder.swapIfNewer(s)) res.headers().firstValue("ETag").ifPresent(lastEtag::set);
                  markSuccess(); }
    case 304 -> markSuccess();
    case 401 -> stopUnauthorized();
    default  -> markFailure(new IOException("HTTP " + res.statusCode()));
}
```

**Evaluation on the hot path.** `isEnabled(key, ctx)` = `holder.current()` → `Evaluator.evaluate`
→ unwrap with default on any non-value reason. No logging on the hot path except at DEBUG,
guarded by `isLoggable`. Allocation matters: the `EvaluationContext` is built by the caller, the
evaluator returns a small record — measure with JMH in M4.

**Initialization.** `initTimeout > 0`: `build()` calls `fetchOnce()` on the executor and waits
`future.get(timeout)`; on timeout, log WARN and return a client in `INITIALIZING`. Never throw
for network problems. Do throw `IllegalArgumentException` for blank key/URL.

**SSE reconnect (advanced):**

```
connect(): GET /sdk/v1/stream  Accept: text/event-stream   (HttpClient.sendAsync, BodyHandlers.ofLines)
           lines → frame parser: "event: changed" + "data: {...}" + blank line → onChanged(envVersion)
           onChanged: if envVersion > holder.current().envVersion → poll() (fetch with ETag; jitter 0–2 s)
           "event: ping" → touch lastByte
           watchdog (every 15 s): now-lastByte > 45 s → close stream → reconnect
           completion/exception → reconnect after backoff (1 s → 60 s, jitter); failures ≥ 3 → streaming=false, polling at normal interval; retry streaming every 5 min
           polling continues at 5 min while streaming is healthy (safety net)
```

**Python SDK — same contract, independent implementation.** Structure it like the Java one but
idiomatically: `dataclasses(frozen=True)` for snapshot types; `threading.Thread(daemon=True)` +
`threading.Event` for the poller (so `close()` wakes it immediately); `urllib.request` with
`timeout`; `hashlib.sha256` for bucketing; the snapshot held in a single attribute — assignment
of a reference is atomic in CPython, which is the Python analogue of `AtomicReference.set`
(document that; note it is *not* a substitute for `compareAndSet`, so guard `swap_if_newer` with
a `threading.Lock`). Keep the public API to the same six methods. The point of this component
is **cross-language agreement**, not feature parity: no SSE in Python.

**Rollout simulator (`tools/rollout_sim.py`).** Load a snapshot (file, or `GET /sdk/v1/config`
with a server key), generate `user-0..N-1` (or read keys from a file), evaluate each with the
Python evaluator, report `in/N` vs `pct`, `|in/N − pct| ≤ tol`; with `--then-percent Q` mutate
the rollout in memory and verify the in-set at `P` is a subset of the in-set at `Q`; with
`--check-server K` sample K users and compare against `POST /sdk/v1/evaluate` → mismatch list.
Output a table and a JSON report (used in `docs/PERFORMANCE.md` and the failure exercises).

### M3.4 Acceptance criteria

- [ ] `flagforge-sdk` jar has **no** `org.springframework` classes on its dependency tree (`mvn dependency:tree` proves it; a test asserts `Class.forName("org.springframework.context.ApplicationContext")` throws).
- [ ] Sample app starts with the server down: prints defaults, no stack trace; when the server comes up, values appear within one polling interval.
- [ ] Kill the server mid-run: sample app keeps printing the last known values; status `STALE` after `staleAfter`; recovers automatically.
- [ ] Revoke the key: SDK logs one ERROR, keeps stale values, stops polling.
- [ ] Contract test: for 200 random contexts × all seeded flags, SDK local result == server `/evaluate` result (variation and reason kind).
- [ ] `sdk-v0.1.0` resolvable from GitHub Packages in a fresh project using the documented `settings.xml`.
- [ ] `pytest sdk-python tools` green; `mypy --strict` clean; both SDKs pass the same vectors (`test_python_and_java_sdk_agree_on_vectors`).
- [ ] `python tools/rollout_sim.py --url http://localhost:8080 --sdk-key … --flag new-checkout --users 100000 --percent 10 --then-percent 25 --check-server 500` prints `share=10.0x% OK`, `sticky OK`, `mismatches=0`.
- [ ] (Advanced) SSE: publish → sample app prints new value within 2 s; `docker restart server` → SDK reconnects, logs one WARN; ping every 20 s visible with `curl -N`.
- [ ] `docs/SDK.md` covers builder options, fallback table, thread-safety, offline mode, versioning, Python SDK section.

### M3.5 Now implement it (≈ 29 h)

| # | Task | h |
|---|---|---|
| 1 | Module setup, dependency hygiene test, `SnapshotCodec` in common (Jackson, `schemaVersion` check) | 1.5 |
| 2 | `FlagClientConfig` + builder + validation; `SnapshotHolder`; evaluation API (`isEnabled`, `variation`, `*Detail`, `status`) | 3 |
| 3 | `SdkHttp` (HttpClient, headers, timeouts) + `Poller` (init fetch, schedule, ETag, backoff, jitter, 401) | 4 |
| 4 | Fallbacks, stale tracking, status transitions + once-only logging, `close()` idempotent, offline mode | 2 |
| 5 | Unit tests with WireMock (happy path, 304, 500, timeout, malformed JSON, 401, out-of-order versions, init timeout) | 3.5 |
| 6 | Contract test harness (Testcontainers server image or same-JVM) + random-context agreement test | 2 |
| 7 | Sample app; `docs/SDK.md`; javadoc; `release-sdk.yml` → GitHub Packages; tag `sdk-v0.1.0` | 2.5 |
| 8 | `contract/` vectors ≥ 40 cases + JSON schema; Java tests read them | 1.5 |
| 9 | `sdk-python/`: package, evaluator + bucketing from spec, client (poll/ETag/defaults/offline), pytest (HTTP fixture), mypy, README | 4.5 |
| 10 | `tools/rollout_sim.py` + tests + README section; run it against the live stack, save report | 2 |
| 11 | **Advanced:** server `/sdk/v1/stream` (`SseEmitter` registry, ping scheduler) + SDK `SseConnection` (parser, watchdog, reconnect, fallback) + tests | 4 |

If the week is slipping: cut task 11 to M4 (it is needed there anyway), never tasks 5–6 or 9.

### M3.6 Verification tests you write

| Test | Asserts |
|---|---|
| `sdk_serverDown_returnsDefaultAndUsesStaleCache` | WireMock: 200 once → `isEnabled` true; then 500 → still true, status `STALE` after `staleAfter`; new client with server down → default false, reason `CLIENT_NOT_READY` |
| `sdk_etagRoundTrip_sendsIfNoneMatchAndHandles304` | second request carries `If-None-Match: "<etag>"`; 304 keeps snapshot and marks success |
| `sdk_outOfOrderResponse_isIgnored` | version 7 after version 8 → `snapshotVersion()==8` |
| `sdk_401_stopsPollingAndKeepsSnapshot` | one ERROR log, no further requests, values still served |
| `sdk_malformedJson_keepsPreviousSnapshot` | body `{"flags":` → previous snapshot, failure counted |
| `sdk_readTimeout_respected` | WireMock fixed delay 2 s, `readTimeout` 500 ms → failure within ~600 ms |
| `sdk_initTimeout_returnsClientNotThrowing` | slow server → `build()` returns ≤ `initTimeout`+100 ms, status `INITIALIZING` |
| `sdk_backoff_growsAndCaps` | inject a fake clock/scheduler: delays 1,2,4,…,60,60 |
| `sdk_close_isIdempotentAndStopsThreads` | thread count back to baseline; second `close()` no-op |
| `sdk_offline_neverTouchesNetwork` | WireMock receives 0 requests |
| `sdk_wrongType_returnsDefaultWithReason` | `variation("bool-flag", ctx, "str")` → `"str"`, reason `WRONG_TYPE` |
| `sdk_noSpringOnClasspath` | `assertThrows(ClassNotFoundException.class, () -> Class.forName("org.springframework.core.SpringVersion"))` |
| `contract_localEvaluationEqualsServerEvaluation` | 200 random contexts × seeded flags → equal variation + reason kind |
| `contract_bucketVectors` / `contract_evaluationVectors` | vectors from `contract/` |
| `sdk_updateViaSse_reflectedWithin2s` (advanced) | real server: publish at t0 → `Awaitility.await().atMost(2, SECONDS)` until `snapshotVersion()` bumps |
| `sse_serverRestart_reconnectsWithBackoff` | WireMock scenario: stream closes → reconnect count ≥ 1, snapshot intact |
| `sse_silence45s_triggersReconnect` | fake clock → watchdog closes and reconnects |
| **Python** `test_bucket_vectors_match_contract` | every vector reproduces |
| **Python** `test_evaluation_vectors_match_contract` | every vector reproduces (variation, reason, rule id) |
| **Python** `test_python_and_java_sdk_agree_on_vectors` | run Java `contract-runner` (subprocess, `java -jar`) and Python evaluator over the same generated 1 000 random contexts → identical outputs; skipped if no JDK |
| **Python** `test_client_polls_with_if_none_match_and_handles_304` | local `http.server` fixture records headers |
| **Python** `test_client_server_down_returns_default_then_stale` | |
| **Python** `test_client_offline_never_calls_network` | |
| **Python** `test_rollout_sim_10_percent_100k_within_tolerance` | `9 900 ≤ in ≤ 10 100` |
| **Python** `test_rollout_sim_stickiness_raise_percent_is_superset` | |
| **Python** `test_rollout_sim_reports_server_mismatch` | fake server returning wrong variation for one user → `mismatches == 1` |

### M3.7 Debugging scenarios

1. **The host app hangs on shutdown.** Your executor thread isn't a daemon and `close()` was never called. Use a `ThreadFactory` that sets `daemon=true` and name the thread `flagforge-poller`.
2. **`isEnabled` sometimes returns the old value right after a publish, then the new one.** Expected — polling interval; check `snapshotVersion()` and the reason's `envVersion` to confirm.
3. **Two clients created per request → thousands of threads.** Add the "second client with same key" warning and document one-client-per-process.
4. **SSE stream connects and dies every 60 s.** A proxy/LB idle timeout shorter than your ping; lower ping to 20 s; check `Connection` headers; make sure the server flushes.
5. **SSE `ofLines()` never delivers.** Server not flushing (buffered response) or gzip applied to `text/event-stream` — exclude the stream endpoint from compression.
6. **Python bucket differs from Java for some users.** UTF-8 vs default encoding on one side (`str.encode()` is UTF-8; Java `getBytes()` without charset is platform-dependent — use `UTF_8` explicitly); or Python `%` on a negative number (you shouldn't have negatives if you read bytes as unsigned).
7. **Python evaluator disagrees on `GT` for `"10"` vs `9`.** Your spec didn't say what happens when an attribute is a numeric string. Decide, add a vector, fix both. This is the spec-is-the-contract lesson.
8. **Python poller keeps running after `close()`.** You used `time.sleep(interval)`; use `Event.wait(interval)` and set the event in `close()`.

### M3.8 Interview questions (M3)

- Why is there no Spring in the SDK? What is the cost of your one Jackson dependency?
- How is thread-safety achieved without locks on the evaluation path?
- What happens on the first `isEnabled()` if the server is down? After it was up and then died? If the key is revoked?
- Blocking init vs non-blocking init — who wants which?
- Why "thin event, fat fetch" for SSE instead of pushing the snapshot?
- How does the SDK detect a dead SSE connection? Why keep polling at all when streaming?
- Why is bucketing specified as bytes-and-modulo in a document rather than by sharing code? What broke when you wrote the Python version?
- What does your rollout simulator prove that a unit test cannot?

---

## M4 — Dashboard, propagation, deploy (Week 23)

### M4.1 What to know first

| Topic | Read | Why now |
|---|---|---|
| Redis pub/sub semantics (fire-and-forget), keyspace vs channels | [04-sql-databases/redis.md](../../04-sql-databases/redis.md) | cross-node invalidation |
| Cache stampede, single-flight, jitter, write-through | [15-system-design/caching.md](../../15-system-design/caching.md) | protection after publish |
| Scalability, fan-out, read replicas | [15-system-design/scalability.md](../../15-system-design/scalability.md) | SSE limits, multi-region talk |
| React forms, routing, API client with JWT | [08-react/03-forms-routing.md](../../08-react/03-forms-routing.md), [08-react/04-api-integration-auth.md](../../08-react/04-api-integration-auth.md), [08-react/05-architecture-testing.md](../../08-react/05-architecture-testing.md) | rules editor |
| Docker Compose multi-node, CI image publishing | [11-docker/compose.md](../../11-docker/compose.md), [13-cicd/pipeline-examples.md](../../13-cicd/pipeline-examples.md) | two server nodes |
| AWS deploy walkthrough, cost safety | [12-aws/deploy-walkthrough.md](../../12-aws/deploy-walkthrough.md), [12-aws/cost-safety.md](../../12-aws/cost-safety.md) | 4th deploy |
| Security checklist | [05-spring-boot/05-security-jwt.md](../../05-spring-boot/05-security-jwt.md), README §20 | key rotation, rate limits |
| Project deep-dive method (rehearsal this week) | [16-interview-prep/project-deep-dive.md](../../16-interview-prep/project-deep-dive.md) | W23 rehearsal |

### M4.2 Requirements

- [ ] React 18 + TS + Vite dashboard: login; org/project/env switcher; flag list (search, tags, enabled badge per env); flag detail with **rules editor** (add/reorder rules, conditions with operator/value inputs, rollout slider with 2 decimals, targets, default); draft save; publish dialog with comment and diff vs current; versions list with diff and **rollback**; kill switch; audit feed; SDK keys page; "test rules" panel (`evaluate-debug`).
- [ ] Propagation: publish → Redis `SET` + `PUBLISH flagforge:changes` → every node evicts local cache and pushes `changed` to its SSE emitters → SDKs fetch with ETag. Two server nodes in Compose.
- [ ] Stampede protection: write-through before publish, single-flight per node, optional Redis `SET NX` lock across nodes, SDK jitter; **measured** (README §14.4).
- [ ] SDK key rotation with grace period; audit; `docs/SECURITY.md`.
- [ ] `tools/flagconf_lint.py`: overlaps, unreachable rules, invalid operators/percentages/variations, duplicate priorities; CLI + JSON output; the dashboard's publish dialog shows the same warnings (implement the checks server-side in `ConfigValidator` as *warnings* and keep the Python tool as the offline/CI version — document that both implement the lint spec in `docs/EVALUATION.md`).
- [ ] Docker: multi-stage images for server + ui; Compose (postgres, redis, server, server-2, nginx/ui, sample-app, `perf` profile with k6).
- [ ] CI/CD: `ci.yml` (Java + UI + Python jobs), `release-server.yml` (GHCR + deploy), `release-sdk.yml`; AWS deploy per README §18; CloudWatch alarm on 5xx and snapshot age.
- [ ] Benchmarks: JMH local evaluation (1/5/20 rules), k6 `/config` and `/evaluate`, propagation delay 20 trials SSE vs polling, snapshot size/parse at 50/500/2000 flags → `docs/PERFORMANCE.md`.
- [ ] Failure exercises 1–11 from [failure-engineering.md](./failure-engineering.md) executed and written up.
- [ ] Docs set complete ([docs-and-resume.md](./docs-and-resume.md)); demo GIF; tag `v1.0` + `sdk-v1.0.0`.

### M4.3 Architectural guidance

**Publish → propagation (full sequence, two nodes):**

```
Node A: POST …:publish
  tx: insert version, bump env version, audit  → COMMIT
  afterCommit:
    snap = SnapshotBuilder.build(env)              (DB read, once)
    Redis SET snapshot:{env} <bytes>  EX 86400     (write-through FIRST)
    local.put(env, snap)
    Redis PUBLISH flagforge:changes {"envId":…,"envVersion":58,"etag":"58-…"}
Node A & Node B (subscriber thread):
    onMessage: local.invalidate(env) (or local.put if the message carries enough — it doesn't; refetch from Redis lazily)
               SseRegistry.broadcast(env, "changed", {"envVersion":58})
SDKs: on "changed" → sleep(random 0–2 s) → GET /config If-None-Match → 200 (from A or B; both read Redis) → swap
```

Why invalidate-then-lazy-refetch instead of pushing the snapshot through pub/sub: Redis pub/sub
messages are not persisted and have size costs; the snapshot is already in Redis; a node that
missed the message self-heals via the 60 s TTL (`refreshAfterWrite`) and SDK polling backstop.

**Redis lock for cross-node single-flight (optional):** `SET lock:snapshot:{env} <nodeId> NX PX 5000`
→ holder rebuilds from DB and writes Redis; others poll Redis for up to 5 s then fall through to
their own DB rebuild (never block forever). Measure with k6: 500 SDK-like VUs hitting `/config`
right after a `FLUSHALL` — compare DB query count with and without single-flight/lock.

**SSE registry:** `ConcurrentHashMap<UUID, Set<SseEmitter>>` with `onCompletion`/`onTimeout`/
`onError` removing the emitter; broadcast iterates and catches `IOException` per emitter (a dead
client must not stop the loop); a scheduled `ping` every 20 s; cap emitters per key (429 on
excess); expose `flagforge_sse_connections`. Servlet async request timeout set higher than the
emitter timeout. Use a dedicated executor for broadcasts so publish latency doesn't include N
socket writes.

**Rules editor.** Keep the editor's state as the exact JSON body of the draft (one `useReducer`),
validate on the client with the same rules as the server (shape only), rely on the server's `422`
for semantics, show lint warnings from the publish preview. Reordering = changing `priority` =
array index. Rollback dialog shows the diff (reuse the diff endpoint). Do not over-design: table
of rules, a form per rule, JSON view toggle for power users.

**Config linter (`tools/flagconf_lint.py`) spec:**

| Check | Rule | Severity |
|---|---|---|
| `invalid-operator` | operator not in the spec list | error |
| `invalid-percentage` | not `0 ≤ p ≤ 100` or > 2 decimals | error |
| `unknown-variation` | `serve`/`fallthrough`/`offVariation`/`targets` reference a variation not in `variations` | error |
| `duplicate-priority` | two rules same priority | error |
| `unreachable-after-catch-all` | a rule with zero conditions (catch-all) is followed by any rule | warning |
| `shadowed-rule` | rule i's condition set is a superset-match of a later rule j's (every context matching j matches i) — implement for `EQUALS`/`IN` subsets and identical condition lists; document what you don't detect | warning |
| `contradictory-conditions` | same attribute `EQUALS a` and `EQUALS b` in one rule; `IN []` | warning |
| `rollout-zero-or-hundred` | 0 % or 100 % rollout — probably meant a fixed variation | info |

### M4.4 Acceptance criteria

- [ ] Demo flow from README §24 works end to end on the AWS deployment and is recorded.
- [ ] Publish on node A → sample app connected to node B updates within 2 s (SSE); with `streaming=false`, within polling interval.
- [ ] `docker stop redis` on the deployed stack: dashboard publish still succeeds (snapshot to local cache + DB; pub/sub skipped, warning logged); SDKs keep evaluating; on Redis return, next publish repopulates.
- [ ] Stampede test: 500 VUs after cache flush → DB rebuild count = 1 per node (single-flight) or 1 total (lock); documented numbers.
- [ ] `flagconf_lint.py` catches all seeded fixture problems in `config/fixtures/bad-*.json`, passes `good-*.json`; CI runs it.
- [ ] `docs/PERFORMANCE.md` complete with the four measurements and methodology; `docs/` set complete; README with screenshots and measured numbers.
- [ ] Security checklist in `docs/SECURITY.md` all ticked (rotation tested, client key cannot fetch config, rate limits, CORS, no secrets in repo).
- [ ] `v1.0` and `sdk-v1.0.0` tagged; CI green; GitHub milestones closed.

### M4.5 Now implement it (≈ 30 h)

| # | Task | h |
|---|---|---|
| 1 | Redis pub/sub subscriber, local eviction, SSE broadcast on message; Compose with `server-2` + nginx round-robin | 3 |
| 2 | Stampede protection (write-through order, single-flight audit, optional Redis lock, SDK jitter) + k6 stampede scenario | 2.5 |
| 3 | SDK key rotation + grace + audit; `docs/SECURITY.md` | 1.5 |
| 4 | UI: auth, layout, org/project/env switcher, flag list/create | 3 |
| 5 | UI: rules editor (draft state, conditions, rollout, targets), save draft, test-rules panel | 5 |
| 6 | UI: publish dialog with diff + lint warnings, versions list, rollback, kill switch, audit feed, SDK keys page; Vitest tests | 4 |
| 7 | `tools/flagconf_lint.py` + fixtures + tests; server-side warning checks in `ConfigValidator` | 2.5 |
| 8 | Dockerfiles (server, ui), Compose profiles, `release-server.yml` (GHCR + deploy), Python job in `ci.yml` | 2 |
| 9 | AWS: EC2 + RDS + Compose + nginx/TLS + CloudWatch alarms; `docs/DEPLOYMENT.md` | 3 |
| 10 | Benchmarks: JMH module, k6 runs, propagation trials, snapshot size table → `docs/PERFORMANCE.md` | 2.5 |
| 11 | Failure exercises write-up; docs set; README; demo GIF; tags | 1 |

### M4.6 Verification tests you write

| Test | Asserts |
|---|---|
| `propagation_publishOnNodeA_invalidatesNodeB` | two `SpringApplication` contexts on random ports sharing Testcontainers Redis: publish via A → `GET /config` on B returns new `envVersion` within 500 ms |
| `sdk_updateViaSse_reflectedWithin2s` | now against the two-node stack via nginx |
| `sse_broadcast_survivesDeadEmitter` | one emitter throws on send → the other still receives |
| `sse_pingEvery20s` | with a fast test clock, `event: ping` observed |
| `sse_perKeyConnectionCap_returns429` | 101st stream for one key → 429 |
| `stampede_500ConcurrentMisses_oneDbRebuild` | flush Redis + local; 500 threads `GET /config` → `snapshot.rebuilt` counter == 1 |
| `redisDown_publishStillSucceedsAndLogsWarning` | Testcontainers Redis stopped → publish 201, local cache updated, warning `redis.unavailable` |
| `keyRotation_oldKeyValidUntilGraceThen401` | rotate with 1 s grace (test property) → old key 200, then 401 |
| `lint_detectsShadowedRule` / `lint_detectsUnreachableAfterCatchAll` / `lint_cleanConfigNoFindings` | Java `ConfigValidator` warnings |
| **Python** `test_lint_flags_unknown_operator_and_bad_percentage` | two errors, exit code 1 |
| **Python** `test_lint_shadowed_rule_superset_equals_in` | warning with both rule ids |
| **Python** `test_lint_unreachable_after_catch_all` | |
| **Python** `test_lint_clean_fixture_exit_zero` | |
| **Python** `test_lint_output_matches_java_validator_on_fixtures` | run both on `config/fixtures/*`, compare finding codes (order-insensitive) |
| **UI** `rulesEditor_reorderChangesPriority`, `publishDialog_showsDiffAndWarnings`, `rollback_requiresConfirmation` | Vitest + RTL |
| `perf_localEvaluation_jmh` (not a test — a benchmark) | `EvaluatorBenchmark.oneRule/fiveRules/twentyRules` sample-time percentiles recorded |

### M4.7 Debugging scenarios

1. **Node B never receives pub/sub messages.** Subscriber bean not started (`MessageListenerContainer` needs a topic registered before start); or Redis `notify-keyspace-events` confusion — you use channels, not keyspace events. `redis-cli MONITOR` shows the `PUBLISH`.
2. **Publish takes 800 ms.** The SSE broadcast runs on the request thread and one client is slow. Move broadcasts to an executor and set write timeouts.
3. **Every SDK fetches at the exact same millisecond after publish; Redis CPU spikes.** Jitter missing or too small; verify with the k6 stampede scenario and the `snapshot.cache miss` logs.
4. **Rules editor loses edits when switching environments.** Draft state keyed by flag only; key it by (env, flag) and warn on unsaved changes.
5. **Rollback shows "no changes" diff.** You diffed against the draft instead of the current version.
6. **CloudWatch alarm never fires on Redis down.** `snapshot_age_seconds` still fine because the local cache rebuilds from DB; alarm on `redis.unavailable` log metric filter or on the readiness probe instead. Discuss what "healthy" means for this service.
7. **`flagconf_lint.py` and the Java `ConfigValidator` disagree on a fixture.** Same lesson as the SDK: the lint rules need a written spec table (M4.3); fix the spec, then both.

### M4.8 Interview questions (M4)

- How does a publish reach an SDK connected to a different API node? What if the pub/sub message is lost?
- Why write the snapshot to Redis before publishing the change event?
- Define cache stampede in your system; which three protections did you add and which one mattered most in your measurements?
- What limits SSE fan-out per node? How would you scale to 100k connections?
- How did you make the dashboard's rules editor and the server agree on validation? Where does the linter live and why also in Python?
- Rotating an SDK key: what does the SDK experience, second by second?
- Which of your measured numbers would you put on a résumé and how do you describe the setup?
- If you had one more week, what would you redesign?

---

## After M4

- Rehearse the [deep dive](../../16-interview-prep/project-deep-dive.md) using [interview-questions.md](./interview-questions.md) §talk outline.
- Run the [failure-engineering](./failure-engineering.md) scenarios you skipped; each is a story for interviews.
- Fill [`trackers/project-tracker.md`](../../trackers/project-tracker.md) and prepare résumé bullets per [docs-and-resume.md](./docs-and-resume.md) — measured results only.
- Polish weeks 24–26: ADVANCED items only after everything above is truly done ([ROADMAP §13](../../ROADMAP.md#13-rules-for-falling-behind)).
