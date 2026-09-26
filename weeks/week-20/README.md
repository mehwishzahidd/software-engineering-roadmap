# Week 20 — FlagForge M1: model, versioning, audit

[← Week 19](../week-19/) · [Roadmap](../../ROADMAP.md) · [Week 21 →](../week-21/)

**Phase 4 · FlagForge** (weeks 20–23) · Milestone **M1** · Ends with [**Checkpoint 20**](../../checkpoints/checkpoint-20.md)

| Block | Hours | Focus |
|---|---:|---|
| Project | 26 | Orgs/projects/environments/flags, immutable versioned configs with rollback, rules, audit history, auth/authz, admin API, SDK keys |
| Learning | 8 | Configuration versioning, immutable snapshots, multi-tenant modelling; **system design fundamentals** |
| DSA | 7 | Mixed review (weakest patterns) — **6 new** + reviews |
| Interview / review | 4 | OA simulation #4, weekly mock, **CP-20** |

---

## 1. Main objective

Start the fourth and last project with a **data model that makes rollback trivial and audit
automatic**: flag configs are immutable versions; publishing creates a new version; rollback
is a new version copying an old one. Get authz right per organization. In parallel, begin the
system-design sweep now that three real systems exist to use as case studies. Close the week
with Checkpoint 20.

## 2. Prerequisites

- ForgeCI phase closed ([Week 19](../week-19/)): `v1.0` tagged, deep-dive rehearsed.
- FlowGrid M1 skills ([Week 4](../week-04/)): Flyway, JPA, validation, ProblemDetail, pagination, JWT + roles, OpenAPI — you will rebuild that skeleton in **one day**, not one week.
- LedgerX's append-only thinking ([Week 9](../week-09/)): versions are append-only exactly like ledger entries.

## 3. Learning topics

| Topic | Subtopics | Read |
|---|---|---|
| System design fundamentals | Requirements → estimates → API → data model → high-level design → deep dive → trade-offs; latency numbers; the vocabulary (availability, consistency, replication, sharding, load balancing) | [`15-system-design/fundamentals.md`](../../15-system-design/fundamentals.md), [`15-system-design/README.md`](../../15-system-design/README.md) |
| Junior design problems | URL shortener, rate limiter, notification service, **feature-flag service** (this project!) | [`15-system-design/junior-design-problems.md`](../../15-system-design/junior-design-problems.md) |
| Multi-tenant modelling | `org_id` on every tenant-owned row, composite unique keys `(project_id, key)`, row-level filtering in every query, tenant in the JWT, never in the URL alone | [`04-sql-databases/04-schema-design.md`](../../04-sql-databases/04-schema-design.md) |
| Configuration versioning & immutable snapshots | Version rows with `(env_id, version_no)` unique, JSONB payload vs normalized rules, `published_version_id` pointer, rollback = copy, diff between versions | [`04-sql-databases/03-advanced-queries.md`](../../04-sql-databases/03-advanced-queries.md) (JSONB, window functions) |
| Authz per organization | Roles `OWNER / ADMIN / EDITOR / VIEWER` per org membership; method security `@PreAuthorize`; SDK keys as a separate principal type | [`05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md) |
| Admin API design | Resource naming, `PUT` vs `POST /versions`, `ETag`/`If-Match` for concurrent edits, ProblemDetail errors | [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md) |

## 4. Concepts to learn

### 4.1 The versioned-config model

```
organizations ─< projects ─< environments ─< flag_configs (env_id, flag_id, published_version_id)
                    │                                │
                    └─< flags (project_id, key)      └─< flag_config_versions (id, env_id, flag_id, version_no, payload jsonb, created_by, created_at, comment)
sdk_keys (env_id, key_hash, role, revoked_at)   audit_events (org_id, actor, action, entity, before, after, at)
```

- `flag_config_versions` is **append-only**: no `UPDATE`, no `DELETE` (revoke privileges or trigger, exactly as LedgerX did for `ledger_entry`).
- `version_no` is per `(env_id, flag_id)`; computed as `max + 1` under a row lock on `flag_configs` (or a per-config sequence counter column) — two concurrent publishes must not both get version 7.
- Publishing = insert version + update pointer in one transaction. Rollback = insert a new version whose payload copies version *k*, comment `"rollback to v7"`. History never rewrites.
- **Interview angle:** "How do you roll back a flag?" — "we never mutate; rollback is a forward version. That gives audit for free and makes the SDK's `version` monotonic."
- **Where FlagForge uses this:** M1 admin API; M2 snapshot cache key includes `version`; M4 dashboard "versions" tab.

### 4.2 Rules payload (JSONB, validated in Java)

```json
{
  "enabled": true,
  "defaultVariation": "off",
  "variations": { "on": true, "off": false },
  "rules": [
    { "priority": 1, "type": "USER",       "users": ["u_42"],                       "serve": "on" },
    { "priority": 2, "type": "ATTRIBUTE",  "attr": "country", "op": "IN", "values": ["DE","NL"], "serve": "on" },
    { "priority": 3, "type": "PERCENTAGE", "percentage": 2500, "serve": "on" }
  ]
}
```

- Validate with a sealed Java model (`sealed interface Rule permits UserRule, AttributeRule, PercentageRule`) + Jackson polymorphic deserialization; reject unknown `type`, duplicate priorities, percentage outside `0..10000`.
- Store JSONB, but **do not query inside it** for M1 — JSONB is a payload, not a schema. If you later need "which flags target country DE", that is a reporting concern, not the hot path.
- **Interview angle:** "Why JSONB and not normalized rule tables?" — the version is read whole, written whole, never partially updated; normalized rows would need a version id on every row and make rollback a multi-row copy.

### 4.3 Multi-tenant authz that cannot leak

- JWT carries `sub` and a list of `{orgId, role}` memberships (or look it up per request — cache later).
- Every repository query for tenant data takes `orgId` (or `projectId` resolved *through* org) — write `findByIdAndProjectOrganizationId`, never `findById` for tenant rows.
- `@PreAuthorize("@authz.canEdit(#projectId)")` on mutating endpoints; a `VIEWER` gets 403, a non-member gets **404** (do not reveal existence).
- SDK keys: `sdk_keys.key_hash` (SHA-256 of a random 32-byte key shown once), scoped to one environment, role `SERVER` (M2 eval) or `CLIENT` (later). Authenticated by a separate filter that sets an `SdkPrincipal`.
- **Interview angle:** "How do you prevent tenant A reading tenant B's flags?" — tenant id in every query, tested by a `CrossTenantAccessIT` that expects 404.

### 4.4 Audit history

Every mutation writes an `audit_events` row **in the same transaction** with `before`/`after` JSON. Simplest correct implementation: an `AuditService.record(action, entity, before, after)` called from services, not JPA listeners (listeners lack the actor and cannot see the diff cleanly). Query API: `GET /orgs/{id}/audit?entity=flag:123&cursor=…` — cursor pagination from LedgerX M3.

### 4.5 Concurrency on edits

Two admins edit the same flag in two tabs. Choice for M1: `If-Match: "<published_version_id>"` on publish → `412 Precondition Failed` if the pointer moved. Cheap, RESTful, and the dashboard (M4) will show "someone published v8 while you were editing".

## 5. Resources

- Spring Security 6 reference: method security, custom authentication filters.
- PostgreSQL 16 docs: JSONB, `REVOKE`, triggers for append-only tables.
- Jackson docs: `@JsonTypeInfo` / `@JsonSubTypes` with sealed interfaces.
- Feature-flag design writing from LaunchDarkly / Unleash / OpenFeature docs (public, stable) — read for vocabulary (flag, variation, targeting rule, rollout, environment); do **not** copy their SDK code.
- [`RESOURCES.md`](../../RESOURCES.md).

## 6. Exercises and assignments

### Exercise A — Skeleton in a day (Mon)

Spring Boot 3 + Flyway + Postgres Compose + JWT + ProblemDetail + OpenAPI + CI `mvn verify`. Acceptance: green CI on the first PR by Monday evening. If it takes more than a day, the gap is in [`checkpoints/checkpoint-04.md`](../../checkpoints/checkpoint-04.md) territory — note it.

### Exercise B — Design-problem drill (1 h, Wed)

Do "design a feature-flag service" from [`15-system-design/junior-design-problems.md`](../../15-system-design/junior-design-problems.md) on a whiteboard *before* reading FlagForge's spec again. Then compare with the spec; write down three differences and why the spec's choice is better (or argue yours is).

### Break it (inside FlagForge)

- Publish two versions concurrently (two `curl`s in a shell loop). Predict: do both get `version_no = 7`? Prove the lock works with a test.
- Try to `UPDATE flag_config_versions SET payload = '{}'` as the app user. Predict the Postgres error.
- Call `GET /projects/{otherTenantsProjectId}/flags` with a valid token from another org. Predict 404, not 403 and not 200.

### Debug it

- Version numbers skip (7, 9, 10): a rolled-back transaction consumed a sequence value — decide whether gaps matter (they don't for correctness; they do for "rollback to v8" UX) and document.
- `@PreAuthorize` never fires: `@EnableMethodSecurity` missing, or the call is an internal `this.method()` bypassing the proxy — [`05-spring-boot/01-core-di.md`](../../05-spring-boot/01-core-di.md).

## 7. DSA — Mixed review, weakest patterns (6 new)

From Week 20 the schedule is **selection by evidence**, not by list. The rule:

1. Open [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md). For each pattern compute `weak = (#Attempted + #Solved With Solution + #Needs Review) / #problems in pattern`.
2. Take the **two highest-`weak` patterns** → 2 new problems each (4).
3. Take one pattern you have not touched for ≥ 3 weeks → 1 new problem (5).
4. One **unseen Medium** from the NeetCode 150 you have not attempted, any pattern, **timed 30 min** (6).

Timing: Easy 15 min, Medium 30 min, Hard 45 min. Log the pattern and the reason it was selected ("weak = 0.6, second highest").

Candidate pool if your tracker is roughly average by now (real numbers, choose per rule): Sliding Window 424 (Longest Repeating Character Replacement), 567 (Permutation in String); Stack 84 (Largest Rectangle in Histogram), 853 (Car Fleet); Binary Search 875 (Koko Eating Bananas), 153 (Find Minimum in Rotated Sorted Array); Linked Lists 25 (Reverse Nodes in k-Group), 146 (LRU Cache); Trees 105 (Construct Binary Tree from Preorder and Inorder), 124 (Binary Tree Maximum Path Sum); Heaps 621 (Task Scheduler), 295 (Find Median from Data Stream); Backtracking 40 (Combination Sum II), 51 (N-Queens); Graphs 417 (Pacific Atlantic Water Flow), 684 (Redundant Connection); 1-D DP 139 (Word Break), 300 (Longest Increasing Subsequence); Intervals 435 (Non-overlapping Intervals), 253 (Meeting Rooms II — premium; use 56 Merge Intervals if unavailable).

Reviews due: Day-3 of W19 graphs/bits, Day-7 of W18 2-D DP, Day-14 of W17, Day-30 of W15 1-D DP.

## 8. Project work — FlagForge M1

Spec: [`18-projects/flagforge/README.md`](../../18-projects/flagforge/README.md) · [`milestones.md`](../../18-projects/flagforge/milestones.md) · [`failure-engineering.md`](../../18-projects/flagforge/failure-engineering.md) · [`docs-and-resume.md`](../../18-projects/flagforge/docs-and-resume.md).

### Weekly task checklist

- [ ] New repo `flagforge` (multi-module Maven: `server`, `sdk` (empty for now), `ui` later); milestone `M1 – Model, versioning, audit`
- [ ] Day-1 skeleton: Boot 3, Flyway `V1__init.sql`, Compose Postgres (+ Redis service present but unused), JWT auth, ProblemDetail, springdoc, CI
- [ ] Entities + migrations: organizations, memberships (user, org, role), projects, environments (dev/staging/prod seeded per project), flags, flag_configs, flag_config_versions (append-only enforced in DB), sdk_keys, audit_events
- [ ] Rule model: sealed types, validation, JSONB (de)serialization tests
- [ ] Admin API: orgs, projects, environments, flags CRUD; `POST /environments/{env}/flags/{key}/versions` (publish), `POST …/rollback/{versionNo}`, `GET …/versions`, `GET …/versions/{n}`; `If-Match` on publish
- [ ] SDK key issue/revoke endpoints (hash stored, plaintext shown once)
- [ ] Authz: org roles, method security, 404-for-foreign-tenant
- [ ] Audit: same-transaction event per mutation, cursor-paginated query API
- [ ] Tests: `@WebMvcTest` slices for validation/authz; `@DataJpaTest` + Testcontainers for append-only + version uniqueness; `CrossTenantAccessIT`; `ConcurrentPublishIT`
- [ ] Docs: README (run + auth flow), `docs/DATABASE.md` (ERD sketch), `docs/API.md` (from OpenAPI)

### Acceptance summary

- Publish → new version, pointer moves, audit row exists — all in one transaction (test kills it midway with a failure point and sees nothing persisted).
- Rollback creates version *n+1* with payload identical to version *k*.
- Cross-tenant access returns 404; viewer mutation returns 403.
- Append-only enforced by the database, not only by the code.
- CI green; OpenAPI lists every endpoint with error responses.

### Verification tests

| Test | Given | When | Then |
|---|---|---|---|
| `PublishCreatesVersionIT` | flag with v3 published | publish new payload | v4 exists, pointer = v4, audit `FLAG_PUBLISHED` with before/after |
| `RollbackIsForwardVersionIT` | v1..v4 | rollback to v2 | v5 payload == v2 payload, pointer = v5 |
| `ConcurrentPublishIT` | 10 threads publish | all complete | version_no 4..13 unique, no duplicates, no gaps required |
| `AppendOnlyVersionsIT` | any version | `UPDATE`/`DELETE` via JDBC as app role | SQL exception (permission/trigger) |
| `CrossTenantAccessIT` | token of org B | `GET /projects/{orgA-project}` | 404 |
| `ViewerCannotPublishTest` | VIEWER token | publish | 403 |
| `IfMatchStaleTest` | pointer = v4, `If-Match: "v3-id"` | publish | 412 |
| `RuleValidationTest` | percentage 12000, duplicate priority, unknown type | deserialize | 400 with field-level ProblemDetail |

### Failure scenarios to run

1. Crash between version insert and pointer update (failure point) → nothing visible; no dangling version.
2. Two publishes racing for the same `version_no`.
3. Migration applied out of order on a second machine (Flyway checksum mismatch) — how you fix it without `repair` in prod.
4. Token from a deleted membership still valid until expiry — decide: short TTL + membership check per request, or accept and document.

### GitHub expectations

- Repo with README, `compose.yaml`, CI badge, milestone M1 with ≥ 8 issues linked to PRs.
- ERD sketch in `docs/DATABASE.md` (Mermaid or dbdiagram export).

## 9. Git activity

- Repo initialised with `.gitignore`, `README.md`, LICENSE, branch protection.
- Branches: `feat/m1-skeleton`, `feat/m1-model`, `feat/m1-versioning`, `feat/m1-authz`, `feat/m1-audit`.
- Conventional commits; PR template with "How I tested" and "Failure scenario reproduced".

## 10. Interview preparation

- **OA simulation #4** (Sat, 90 min): [`OA_PREP.md`](../../OA_PREP.md) — two problems + a repo-modification drill from [`21-debugging-code-reading/drills.md`](../../21-debugging-code-reading/drills.md) ("add a feature to an unfamiliar module and keep tests green").
- **Weekly mock** (Tue): [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md).
- **System design warm-up:** 30 min, explain FlowGrid's allocation as a design problem using the fundamentals vocabulary — the W22 system-design mock will use one of your projects.
- **Résumé defense:** PostgreSQL + Spring Boot — [`17-resume-tech-defense/postgresql.md`](../../17-resume-tech-defense/postgresql.md), [`17-resume-tech-defense/spring-boot.md`](../../17-resume-tech-defense/spring-boot.md).
- **Applications:** junior-role-ready tier per [`JOB_READINESS.md`](../../JOB_READINESS.md); keep volume; add FlagForge to the résumé only when M2 (MVP) exists.
- **Behavioral:** one new story from ForgeCI's hardest bug ([`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md)).

## 11. Revision

- Re-derive JWT + method-security flow on paper (filter → authentication → `SecurityContext` → `@PreAuthorize`).
- Re-read [`04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md): what isolation level makes "max(version_no)+1" safe, and why you used a row lock instead.
- DSA reviews.

## 12. Daily plan

| Day | Plan |
|---|---|
| **Mon (8 h)** | Learning 2: system design fundamentals · Project 4.5: skeleton in a day · DSA 1.5: rule step 1–2 (pick 2) |
| **Tue (8 h)** | Project 5: schema + entities + append-only enforcement · DSA 2: 2 problems + reviews · Mock 1 h |
| **Wed (8 h)** | Learning 2: junior design problems (flag service drill) + multi-tenant modelling · Project 4.5: rules model, publish/rollback · DSA 1.5: 1 problem |
| **Thu (8 h)** | Project 5: authz, SDK keys, audit · DSA 2: timed unseen Medium + reviews · Docs 1: DATABASE.md |
| **Fri (5 h)** | Project 3: ITs (concurrent publish, cross-tenant, If-Match) · DSA reviews 1 · Retro 1 |
| **Sat (6 h)** | Project 4: failure scenarios, API.md, close M1 · OA sim #4 (90 min) + review |
| **Sun (3 h)** | **Checkpoint 20** (timed coding + knowledge + explain-out-loud) · trackers · plan W21 · rest |

## 13. End-of-week test — Checkpoint 20

Run [`checkpoints/checkpoint-20.md`](../../checkpoints/checkpoint-20.md) in full. Its gate: *Is ForgeCI shipped with failure recovery? Can I design and explain a system at junior level?* Record the score in [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md). A fail does not stop the calendar (ROADMAP §9): remediation goes into W21–22 review blocks.

Quick self-check before the checkpoint: explain in 2 minutes each — (a) why versions are immutable, (b) how tenant isolation is enforced, (c) how ForgeCI recovers an orphaned job.

## 14. Mastery checklist

- [ ] I can build a secured Spring Boot CRUD skeleton with migrations and CI in one day
- [ ] I can model immutable versions with rollback-as-new-version and explain the trade-off vs mutable rows
- [ ] I can enforce tenant isolation and prove it with a test
- [ ] I can run a junior system-design conversation: requirements → API → data → design → trade-offs
- [ ] I select DSA problems from tracker evidence, not from a fixed list

## 15. Expected deliverables

- FlagForge repo with M1 closed; CI green; `docs/DATABASE.md`, `docs/API.md`.
- CP-20 score recorded; remediation items (if any) scheduled.
- Trackers updated: project (M1), DSA (6 new + reviews + selection reasons), interview (OA #4, mock), technology, weekly progress.

## 16. If behind / stretch

**Behind:** drop `If-Match` and the audit query API (keep audit *writes*); keep append-only enforcement and tenant tests — they are the engineering story.

**Stretch:** version diff endpoint (`GET …/versions/{a}/diff/{b}`), environment cloning ("copy staging → prod as new versions"), OpenAPI-generated TypeScript client for the M4 dashboard.
