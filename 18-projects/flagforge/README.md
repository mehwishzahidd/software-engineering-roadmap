# FlagForge — feature-flag & progressive-rollout platform + Java SDK

> **Weeks 20–23 · 100–120 h · Project 4 of 4.** Technical identity: low-latency evaluation,
> deterministic bucketing, a small rule engine, client-library (SDK) design, configuration
> propagation, fallback behaviour and immutable versioning.
>
> This folder is the **spec**. The code lives in *your* GitHub repository (`flagforge`). This
> document tells you what to build, why, in what order and how to prove it works — it does not
> build it for you. See the [project rule](../../ROADMAP.md#1-learning-philosophy).

| File | Purpose |
|---|---|
| [README.md](./README.md) (this file) | Product, requirements, architecture, data model, evaluation semantics, API, SDK design, propagation, testing, deployment, performance, workflow |
| [milestones.md](./milestones.md) | M1–M4 week by week: know first → requirements → guidance → acceptance → implement → verify → debug → interview |
| [failure-engineering.md](./failure-engineering.md) | Break it on purpose: Redis down, server down, stale SDK, stampede, SSE overload, corrupted snapshot … |
| [interview-questions.md](./interview-questions.md) | Basic → advanced questions, system-design version, 2-min and 12-min talk outlines |
| [docs-and-resume.md](./docs-and-resume.md) | Required documentation set, benchmarking protocol (k6, JMH), honest résumé bullets |

Related roadmap material: [`../../PROJECTS.md`](../../PROJECTS.md) · [`../../PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) · [`../README.md`](../README.md) · templates: [design doc](../templates/design-doc.md), [ADR](../templates/adr.md), [benchmark report](../templates/benchmark-report.md) · weeks: [20](../../weeks/week-20/README.md) · [21](../../weeks/week-21/README.md) · [22](../../weeks/week-22/README.md) · [23](../../weeks/week-23/README.md) · gate: [CP-20](../../checkpoints/checkpoint-20.md), [CP-24](../../checkpoints/checkpoint-24.md).

---

## Contents

1. [Product overview](#1-product-overview)
2. [Problem statement](#2-problem-statement)
3. [Functional requirements](#3-functional-requirements)
4. [Non-functional requirements](#4-non-functional-requirements)
5. [Scope tiers](#5-scope-tiers)
6. [Milestone summary](#6-milestone-summary)
7. [Architecture](#7-architecture)
8. [Database design](#8-database-design)
9. [Evaluation semantics](#9-evaluation-semantics)
10. [API design](#10-api-design)
11. [Package / module structure](#11-package--module-structure)
12. [Authentication and authorization](#12-authentication-and-authorization)
13. [SDK design](#13-sdk-design)
14. [Propagation design](#14-propagation-design)
15. [Testing strategy](#15-testing-strategy)
16. [Docker and Compose](#16-docker-and-compose)
17. [CI/CD](#17-cicd)
18. [AWS deployment plan](#18-aws-deployment-plan)
19. [Logging and error handling](#19-logging-and-error-handling)
20. [Security](#20-security)
21. [Performance](#21-performance)
22. [Scalability](#22-scalability)
23. [Git strategy, issues, PR workflow](#23-git-strategy-issues-pr-workflow)
24. [README and demo requirements](#24-readme-and-demo-requirements)
25. [Learning links](#25-learning-links)

---

## 1. Product overview

FlagForge is a **feature-flag management service** in the spirit of LaunchDarkly / Unleash /
Flagsmith, reduced to the parts that teach the most:

- A **control plane** (Spring Boot admin API + React dashboard) where teams create flags, write
  targeting rules, roll features out to a percentage of users, and roll back.
- A **data plane** (SDK API + Java SDK) that answers "is flag X on for user U?" in microseconds,
  keeps working when the server is unreachable, and picks up changes within seconds of publish.

Concepts borrowed (as *concepts*, not code) from LaunchDarkly's public documentation:
organizations/projects/environments, flag keys, variations, targeting rules with attribute
operators, percentage rollouts with deterministic bucketing, per-environment SDK keys
(server-side vs client-side), streaming vs polling delivery, evaluation reasons, audit log,
flag versions and rollback.

**One-sentence pitch:** *"Deploy code dark, release it by flipping a flag, roll it out to 1 % →
10 % → 100 % of users, and kill it instantly — with a Java SDK that evaluates locally and never
blocks your request path."*

---

## 2. Problem statement

| Problem | Without flags | With FlagForge |
|---|---|---|
| **Deploy ≠ release** | Shipping code means exposing it; rollback means redeploying | Code ships disabled; release is a config change; rollback is a config change (seconds, no build) |
| **Progressive rollout** | All-or-nothing releases; a bad release hits 100 % of users | 1 % → 10 % → 50 % → 100 %, same users stay in the same bucket, so a user never flickers on/off |
| **Kill switch** | Incident → hotfix → CI → deploy (30–60 min) | Flip flag off → propagated to every SDK in ≤ 2 s (SSE) or ≤ polling interval |
| **Targeting** | Hard-coded `if (user.email.endsWith("@corp.com"))` | Rules: attribute operators, explicit user targeting, priority order, default |
| **Auditability** | "Who turned this on?" — nobody knows | Every publish is an immutable version with author, timestamp and comment |
| **Resilience** | A config service outage takes the app down | SDK evaluates from an in-memory snapshot; server down ⇒ stale snapshot or coded defaults, never an exception on the hot path |

The engineering problems worth studying here are *not* CRUD. They are: **deterministic hashing**,
**immutable versioned configuration**, a **hot path that never touches the database**,
**cache invalidation + propagation**, and **client-library design under failure**.

---

## 3. Functional requirements

Feature tags: **[MVP]** must exist by M2, **[SRV]** by v1.0 (Strong Résumé Version), **[ADV]** advanced, **[STR]** stretch.

### 3.1 Tenancy and access
- FR-01 [MVP] Organizations; users belong to an organization with a role (`OWNER`, `ADMIN`, `EDITOR`, `VIEWER`).
- FR-02 [MVP] Projects inside an organization; environments inside a project (`dev`, `staging`, `prod` seeded by default; more can be added).
- FR-03 [MVP] Dashboard users authenticate with email + password → JWT. SDKs authenticate with an **SDK key per environment**.
- FR-04 [SRV] Environment-level permission: `prod` publishes require `ADMIN`+ (configurable "protected environment" flag).

### 3.2 Flags and configuration
- FR-05 [MVP] Create a flag in a project: `key` (immutable, `[a-z0-9-]`, unique per project), name, description, type (`BOOLEAN` first; `STRING`/`NUMBER`/`JSON` variations later [ADV]), tags.
- FR-06 [MVP] Each flag has one **config per environment**: enabled/disabled, ordered rules, individual user targets, default variation when no rule matches, "off variation" when disabled.
- FR-07 [MVP] Rule = `priority` + list of conditions (`attribute`, `operator`, `value(s)`) + outcome (fixed variation **or** percentage rollout). Operators: `EQUALS`, `NOT_EQUALS`, `IN`, `NOT_IN`, `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `GT`, `GTE`, `LT`, `LTE`, `SEMVER_GT`/`SEMVER_LT` [ADV], `REGEX` [ADV].
- FR-08 [MVP] Percentage rollout: per rule (or as the default), `rolloutPercentage` 0–100 with two decimals, plus a bucket-by attribute (default `key` = user key).
- FR-09 [MVP] **Immutable versions**: editing a config creates a *draft*; **publish** creates `flag_config_version` N+1 with a comment. Versions are never updated or deleted.
- FR-10 [MVP] **Rollback** = publish a new version whose content is a copy of an older version (never "un-publish").
- FR-11 [MVP] Version history and diff between two versions (dashboard [SRV], API returns both bodies [MVP]).
- FR-12 [ADV] Segments (named reusable user groups referenced by rules). [ADV] Scheduled rollouts (publish version at time T; increase percentage on a schedule).
- FR-13 [STR] Experimentation hooks: emit evaluation events (flag, variation, user, reason) to a sink for A/B analysis.

### 3.3 Evaluation
- FR-14 [MVP] Server-side evaluation endpoint: given SDK key + evaluation context, return the variation and a **reason**.
- FR-15 [MVP] Evaluation is **deterministic**: same flag version + same context ⇒ same result on every node and in every SDK instance.
- FR-16 [MVP] Environment **config snapshot** endpoint returning all published configs for one environment, with `ETag` and `304 Not Modified`.
- FR-17 [SRV] SSE stream endpoint pushing "snapshot changed" events to connected SDKs.

### 3.4 Java SDK (separate Maven artifact)
- FR-18 [SRV] `FlagClient` builder: `sdkKey`, `baseUrl`, `pollingInterval`, `connectTimeout`, `readTimeout`, `initTimeout`, `offline`, `streaming`, `logger`.
- FR-19 [SRV] `isEnabled(key, context)`, `variation(key, context, default)`, `variationDetail(...)` (value + reason), `close()`.
- FR-20 [SRV] Local in-memory snapshot, local evaluation (no network per call), polling refresh with `If-None-Match`, stale-if-error, coded defaults when no snapshot, offline mode with programmatic flag values, sample app.
- FR-21 [ADV within M3] SSE streaming with keepalive, exponential backoff reconnect, fallback to polling.
- FR-22 [ADV] TypeScript SDK (browser, client-side key, server-evaluated).

### 3.5 Audit and operations
- FR-23 [MVP] Audit event for every mutating admin action (who, what, before/after, when, request id).
- FR-24 [SRV] SDK key lifecycle: create, list (prefix only), rotate (grace period), revoke.
- FR-25 [SRV] Health/readiness endpoints; metrics (evaluations/s, snapshot age, SSE connections).

### 3.6 Python component (`tools/` + `sdk-python/`, M3–M4)
Every flagship project ships at least one tested, documented Python component (BRIEF language
strategy: Java stays the backend/SDK language; Python is the tooling and verification language).
- FR-26 [SRV] `tools/rollout_sim.py` — **rollout-distribution simulator**: given a snapshot (file or `/sdk/v1/config`), a flag key and N synthetic users, computes the share in rollout and proves `pct ± tolerance` and **stickiness** (raising the percentage keeps every previously-in user in); optionally cross-checks a sample of users against `POST /sdk/v1/evaluate` and reports mismatches.
- FR-27 [SRV] `tools/flagconf_lint.py` — **configuration validation tool**: lints a draft/version/snapshot JSON for invalid operators, percentages out of range, unknown variations, duplicate priorities, **overlapping rules** (a higher-priority rule whose conditions are a superset of a later one's) and **unreachable rules** (rule after a catch-all; conditions on attributes never used elsewhere with impossible value sets); exit code ≠ 0 on findings so it can run in CI against `config/` fixtures.
- FR-28 [SRV] `sdk-python/` — **minimal Python SDK / test client** (`flagforge` package): `FlagClient(sdk_key, base_url, polling_interval, init_timeout, offline=…)`, `is_enabled(key, context)`, `variation(key, context, default)`, `variation_detail(...)`, `close()`; polling with `If-None-Match`, stale-if-error, coded defaults, offline mode; implements **exactly** the evaluation contract of §9 from the spec (not by porting Java code line by line); typed (`mypy --strict` clean), `pytest`-tested, documented README.
- FR-29 [SRV] **Cross-SDK contract tests**: a shared `contract/` directory (evaluation vectors JSON + bucket vectors JSON) consumed by JUnit (Java) and pytest (Python); same inputs ⇒ same variation and reason in both SDKs and on the server.

---

## 4. Non-functional requirements

| NFR | Target | How it is measured / enforced |
|---|---|---|
| **Evaluation latency (server-side)** | p99 ≤ 20 ms at 500 req/s on a laptop, snapshot served from Redis/in-process cache | k6, protocol in [docs-and-resume.md](./docs-and-resume.md) |
| **Evaluation latency (local SDK)** | p99 ≤ 50 µs for a flag with 5 rules (JMH) | JMH benchmark module |
| **Propagation delay** | publish → SDK sees new value: ≤ 2 s (SSE), ≤ polling interval + 1 s (polling) | timestamped publish vs SDK log line, 20 trials |
| **Availability of evaluation** | Evaluation never fails because Postgres or Redis is down: server falls back Redis → in-process → Postgres; SDK falls back to stale snapshot → coded defaults | failure-engineering scenarios 1–3 |
| **Consistency of bucketing** | Identical results for the same (flagKey, userKey, version) on every server node and SDK version | property test + contract test with fixed hash vectors |
| **Auditability** | Every published version records author, timestamp, comment, previous version; audit table is append-only | DB privileges (no UPDATE/DELETE grant on `audit_event`, `flag_config_version`) |
| **Versioned config** | Versions immutable; rollback creates a new version; snapshot carries a monotonically increasing `environment version` | DB constraints + tests |
| **Security** | Server SDK keys never sent to browsers; keys stored hashed; rate limit on SDK API; JWT expiry ≤ 1 h | security review checklist §20 |
| **Observability** | Structured JSON logs with `requestId`, `env`, `flagKey`; metrics via Actuator/Micrometer | [05-spring-boot/06-logging-actuator.md](../../05-spring-boot/06-logging-actuator.md) |

---

## 5. Scope tiers

| Tier | Contents | When |
|---|---|---|
| **MVP** | M1 + M2: model, versioning, audit, admin API, evaluation engine, server-side eval endpoint, Redis snapshot cache, p99 measured | end of Week 21 |
| **STRONG RESUME VERSION (v1.0)** — *primary target* | MVP + Java SDK (polling, local eval, fallbacks, offline, sample app, contract tests) + Python component (`sdk-python/` minimal SDK, `tools/` rollout simulator + config linter, cross-SDK contract vectors) + React dashboard + SSE propagation via Redis pub/sub + stampede protection + Docker/AWS/CI + docs set + benchmarks + failure exercises | end of Week 23, tag `v1.0` |
| **ADVANCED** | Segments · scheduled rollouts · non-boolean variations with typed `variation()` · TypeScript client SDK (client-side key, server evaluation) · SEMVER/REGEX operators | only after `v1.0`; polish weeks |
| **OPTIONAL STRETCH** | Experimentation hooks (evaluation events, exposure logging) · approval workflow for prod publishes · flag lifecycle (stale-flag detection) | after Week 26 |

If the schedule slips: keep versioning, evaluation determinism, the SDK core (polling + fallbacks) and tests. Cut SSE, then the dashboard's rules editor (use JSON editing), then segments. **Cut features, never quality.**

---

## 6. Milestone summary

| Milestone | Week | Deliverable | Tier reached |
|---|---|---|---|
| **M1 — Model, versioning, audit** | 20 | Orgs, memberships/roles, projects, environments, flags, per-environment immutable config versions, rules, rollback, audit history, JWT auth + org roles, admin API, SDK keys per environment, OpenAPI, CI | — |
| **M2 — Evaluation engine + cache** | 21 | Deterministic evaluator (priority → first match; bucket = hash mod 10000), server-side eval endpoint, environment snapshot in Redis with ETag + invalidation on publish, p99 measured with k6 | **MVP** |
| **M3 — Java SDK (+ Python SDK & simulator)** | 22 | `flagforge-sdk` artifact: builder, polling with ETag, local snapshot (AtomicReference), local evaluation (shared evaluator module), defaults, timeouts, stale-if-error, offline mode, sample app, unit + contract tests; `contract/` vectors JSON; `sdk-python/` minimal SDK implementing the same contract + cross-SDK agreement test; `tools/rollout_sim.py`; then SSE streaming (advanced) | — |
| **M4 — Dashboard, propagation, deploy** | 23 | React admin (flags, rules editor, versions, rollback, audit), publish → Redis pub/sub → SSE fan-out, stampede protection, `tools/flagconf_lint.py` (also wired into the dashboard's publish warnings), Docker Compose, AWS, CI/CD (image + SDK artifact publish + pytest job), docs, benchmarks, failure exercises | **v1.0** |

Full detail per milestone: [milestones.md](./milestones.md).

---

## 7. Architecture

### 7.1 Overview

```
                 ┌──────────────────────────────────────────────────────────────┐
                 │                        CONTROL PLANE                          │
                 │                                                               │
  Browser ──────►│  React 18 + TS admin  ──JWT──►  Admin API (Spring Boot)       │
  (dashboard)    │  (Vite, served by nginx)        /api/v1/orgs|projects|envs|   │
                 │                                 flags|versions|audit|sdk-keys │
                 │                                        │                      │
                 │                                        ▼                      │
                 │                                 PostgreSQL 16                 │
                 │                                 (source of truth,             │
                 │                                  immutable versions, JSONB)   │
                 │                                        │                      │
                 │                    publish(env, flag) ─┘                      │
                 │                        │                                      │
                 │                        ▼                                      │
                 │            SnapshotBuilder: load all published configs of env │
                 │            → EnvironmentSnapshot{envVersion, etag, flags[]}   │
                 │                        │                                      │
                 │      Redis SET snapshot:{envId}  +  PUBLISH flagforge:changes │
                 └────────────────────────┼──────────────────────────────────────┘
                                          │
                 ┌────────────────────────┼──────────────────────────────────────┐
                 │                        ▼            DATA PLANE (hot path)      │
                 │   Redis 7 ◄──────► SDK API nodes (same Spring Boot app or      │
                 │   snapshot:{env}    separate profile)                          │
                 │   pub/sub            in-process cache (Caffeine) ← Redis ← DB  │
                 │                                                               │
                 │   GET  /sdk/config?env=..  (ETag / If-None-Match → 304)        │
                 │   POST /sdk/evaluate       (server-side evaluation)            │
                 │   GET  /sdk/stream         (SSE: "changed" events + keepalive) │
                 └────────▲─────────────────────▲─────────────────────▲──────────┘
                          │ poll (ETag)          │ evaluate             │ SSE
                 ┌────────┴─────────┐   ┌────────┴─────────┐   ┌────────┴─────────┐
                 │ Java SDK         │   │ Thin client      │   │ Java SDK         │
                 │ local snapshot   │   │ (TS, browser,    │   │ streaming mode   │
                 │ local evaluator  │   │ client-side key) │   │ + polling fallbk │
                 └──────────────────┘   └──────────────────┘   └──────────────────┘
                 ┌──────────────────┐   ┌──────────────────────────────────────────┐
                 │ Python SDK       │   │ tools/ (Python)                          │
                 │ sdk-python/      │   │ rollout_sim.py  → snapshot + N users →   │
                 │ polling + ETag,  │   │   pct ± tol, stickiness, vs /evaluate    │
                 │ local evaluator  │   │ flagconf_lint.py → overlaps/unreachable  │
                 └────────▲─────────┘   └──────────────────────────────────────────┘
                          │
                 contract/ (evaluation-vectors.json, bucket-vectors.json)
                 = the SHARED CONTRACT both SDKs and the server are tested against
```

**The rule that shapes everything: the hot path never touches Postgres.** `GET /sdk/config`,
`POST /sdk/evaluate` and `GET /sdk/stream` read only from the in-process cache or Redis. Postgres
is read only (a) on publish to build the snapshot and (b) as a *last-resort* cold fill when both
caches are empty (guarded by single-flight so one node rebuilds and everyone else waits). Java
SDKs go one step further: after startup they evaluate **locally** from a snapshot in memory and
make zero network calls per evaluation.

### 7.2 Two evaluation modes and why both exist

| Mode | Who | Latency | Trust | Use |
|---|---|---|---|---|
| **Local (SDK)** | Server-side Java services with a server SDK key | ~µs, no network | SDK holds the full ruleset (including targeting lists) — acceptable on trusted servers | default for backend services |
| **Server-side (`/sdk/evaluate`)** | Browsers/mobile with a client-side key; scripts; anything that must not see rules | ~ms, one HTTP call | rules stay on the server; only the result leaks | TS SDK, curl demos, debugging "why did user X get variation Y" |

### 7.3 Architecture diagram spec (for `docs/ARCHITECTURE.md`)

Produce three diagrams (draw.io, Excalidraw or Mermaid — check them into `docs/diagrams/`):

1. **Component diagram** — the ASCII above, with technology labels, ports, and arrows annotated with protocol (HTTPS+JWT, HTTPS+SDK key, RESP, SSE).
2. **Publish sequence** — dashboard → `POST /versions:publish` → transaction (insert version, insert audit) → `SnapshotBuilder` → Redis `SET`+`PUBLISH` → each node's subscriber evicts in-process cache → SSE emitters send `changed{envVersion}` → SDK fetches with `If-None-Match` → `200` → atomic swap.
3. **Evaluation sequence (SDK, local)** — `isEnabled()` → `snapshotRef.get()` → evaluator → reason; show that no I/O happens; and the polling loop as a separate lifeline.

---

## 8. Database design

### 8.1 Entity overview

```
organization 1──* membership *──1 app_user
organization 1──* project 1──* environment 1──* sdk_key
project      1──* flag
flag × environment ──* flag_config_version   (immutable; one "current" per (env, flag))
flag_config_version 1──* rule 1──* rule_condition           (normalized)  ── OR ──
flag_config_version.rules JSONB                              (document)   ← decision below
environment  1──1 environment_config_snapshot (env_version, json, etag) [materialized, also in Redis]
organization 1──* audit_event
```

### 8.2 Tables (PostgreSQL 16)

| Table | Key columns | Notes |
|---|---|---|
| `organization` | `id uuid PK`, `slug unique`, `name`, `created_at` | tenant root |
| `app_user` | `id`, `email unique`, `password_hash` (BCrypt), `created_at` | |
| `membership` | `org_id FK`, `user_id FK`, `role` (`OWNER/ADMIN/EDITOR/VIEWER`), PK `(org_id, user_id)` | authorization source |
| `project` | `id`, `org_id FK`, `key` (unique per org), `name` | |
| `environment` | `id`, `project_id FK`, `key` (`dev/staging/prod`, unique per project), `name`, `protected boolean` (prod = true), `current_env_version bigint default 0` | `current_env_version` increments on every publish in that environment → the snapshot's version and ETag source |
| `flag` | `id`, `project_id FK`, `key` (unique per project, immutable), `name`, `description`, `type` (`BOOLEAN` …), `tags text[]`, `archived boolean`, `created_at` | flag identity is project-wide; per-environment state is in versions |
| `flag_config_version` | `id`, `environment_id FK`, `flag_id FK`, `version int`, `enabled boolean`, `off_variation`, `default_rollout jsonb` (fixed variation or percentage), `rules jsonb`, `targets jsonb`, `published_at`, `published_by FK app_user`, `comment`, `rolled_back_from_version int null`, `UNIQUE (environment_id, flag_id, version)` | **immutable** |
| `flag_config_draft` | `environment_id`, `flag_id`, same body columns, `updated_at`, `updated_by`, PK `(environment_id, flag_id)` | mutable working copy; publish copies it into a new version |
| `environment_config_snapshot` | `environment_id PK`, `env_version bigint`, `json jsonb`, `etag text`, `built_at` | materialized so a cold Redis can be refilled without recomputation; optional but simplifies M4 |
| `sdk_key` | `id`, `environment_id FK`, `kind` (`SERVER/CLIENT`), `prefix` (first 8 chars, for display), `key_hash` (SHA-256), `created_at`, `expires_at null`, `revoked_at null`, `created_by` | never store plaintext |
| `audit_event` | `id bigserial`, `org_id`, `actor_id`, `action` (`FLAG_CREATED`, `VERSION_PUBLISHED`, `ROLLBACK`, `SDK_KEY_ROTATED` …), `entity_type`, `entity_id`, `before jsonb`, `after jsonb`, `request_id`, `created_at` | append-only |

### 8.3 Rules: JSONB vs normalized tables — decide and justify

| Option | Pros | Cons |
|---|---|---|
| **A. `rules jsonb` in `flag_config_version`** | A version is one row = one document = exactly what the snapshot and SDK consume; immutability trivial; publish = single insert; diff = compare two documents; no join explosion when building a snapshot for 500 flags | You can't query "all rules using attribute `country`" without `jsonb_path` indexes; schema validation must live in code (Bean Validation on DTOs + a JSON Schema check) |
| **B. `rule` + `rule_condition` tables** | Relational validation (FK to variation, CHECK on operator), analytics queries | Versions become dozens of rows; immutability needs triggers on three tables; snapshot build joins 3 tables × N flags; diff logic is harder |

**Recommended: A (JSONB), with a normalized *read model* only if you need it later.** Justify it in
an ADR ([template](../templates/adr.md)): the document *is* the unit of publish, evaluate, cache
and diff. Keep a GIN index `ON flag_config_version USING gin (rules jsonb_path_ops)` only if you
add "find flags targeting attribute X" in ADVANCED. Study
[04-sql-databases/04-schema-design.md](../../04-sql-databases/04-schema-design.md) and
[04-sql-databases/03-advanced-queries.md](../../04-sql-databases/03-advanced-queries.md) (JSONB
operators) before deciding. Whichever you choose, write the alternative down.

Shape of `rules` (this is also the SDK wire format — keep them identical):

```json
[
  { "id": "r1", "priority": 0, "description": "internal users",
    "conditions": [ { "attribute": "email", "operator": "ENDS_WITH", "values": ["@corp.com"] } ],
    "serve": { "variation": "on" } },
  { "id": "r2", "priority": 1, "description": "10% of EU",
    "conditions": [ { "attribute": "country", "operator": "IN", "values": ["DE","FR","NL"] } ],
    "serve": { "rollout": { "percentage": 10.0, "bucketBy": "key", "variation": "on", "fallthrough": "off" } } }
]
```

### 8.4 Indexes

| Index | Why |
|---|---|
| `flag (project_id, key)` unique | flag lookup by key, uniqueness |
| `flag_config_version (environment_id, flag_id, version DESC)` unique | "current version" = first row; history listing |
| `flag_config_version (environment_id, published_at DESC)` | environment activity feed |
| `sdk_key (key_hash)` unique | SDK auth lookup (hash the presented key, one index probe) |
| `audit_event (org_id, created_at DESC)`; `audit_event (entity_type, entity_id, created_at DESC)` | audit views |
| `membership (user_id)` | "my organizations" |

Verify each with `EXPLAIN (ANALYZE, BUFFERS)` — see [04-sql-databases/05-indexes-performance.md](../../04-sql-databases/05-indexes-performance.md).

### 8.5 Immutability of versions

- Application layer: no `update`/`delete` methods in the repository for `flag_config_version` / `audit_event`.
- Database layer (this is what you can defend in an interview): the app's DB role gets `SELECT, INSERT` only on those tables — `REVOKE UPDATE, DELETE ON flag_config_version, audit_event FROM flagforge_app;` in a Flyway migration run by the migration role. LedgerX did this with a trigger; do the privilege variant here and note the difference.
- `version` assigned inside the publish transaction: `SELECT coalesce(max(version),0)+1 FROM flag_config_version WHERE environment_id=? AND flag_id=? FOR UPDATE` on the flag/env row (or a `SELECT … FOR UPDATE` on the `flag_config_draft` row) so concurrent publishes cannot produce duplicate versions — the unique constraint is the last line of defence.
- **Rollback** = `POST /versions/{n}:rollback` → new version N+1 with the body of version n and `rolled_back_from_version = n`, comment auto-prefixed `"Rollback to v{n}: …"`. Nothing is ever un-published.

### 8.6 ERD spec (for `docs/DATABASE.md`)

Draw the ERD with all tables above, crow's-foot cardinality, PK/FK marks, the unique constraints,
and a callout box: "immutable: flag_config_version, audit_event". Add a second small diagram
showing the JSONB document shape. Include the Flyway migration list (`V1__baseline.sql`,
`V2__sdk_keys.sql`, `V3__snapshots.sql`, `V4__revoke_updates.sql` …) with one line each.

---

## 9. Evaluation semantics

This is the contract the server evaluator and the SDK evaluator **both** implement (same code —
see §11). Write it down in `docs/EVALUATION.md` before writing code; it is your spec and your test
oracle.

### 9.1 Evaluation context

```
EvaluationContext { key: String (required, the user/entity key),
                    attributes: Map<String, Object> }   // e.g. email, country, plan, appVersion, anonymous
```

Attributes are typed at evaluation time: numbers compare numerically, strings lexically, lists
with `IN`. A missing attribute never matches (a condition on `country` fails for a context
without `country`; `NOT_IN` on a missing attribute also fails — document it, it is a classic
ambiguity).

### 9.2 Order of evaluation

```
1. Flag missing from snapshot          → default supplied by caller     reason = FLAG_NOT_FOUND
2. flag.enabled == false               → offVariation                   reason = OFF
3. context.key in targets[variation]   → that variation                 reason = TARGET_MATCH
4. rules sorted by priority ascending; first rule whose ALL conditions match:
      serve.variation set              → variation                      reason = RULE_MATCH(ruleId)
      serve.rollout set                → bucket(context, flag, rule) < pct*100 ? rollout.variation : rollout.fallthrough
                                                                        reason = RULE_MATCH(ruleId) + inRollout=true/false
5. no rule matched → defaultRollout: fixed variation or percentage → same as 4
                                                                        reason = FALLTHROUGH
6. any exception inside evaluation (malformed rule, bad type) → caller default
                                                                        reason = ERROR(kind)   — never throw to the caller
```

**Why "first match by priority" and not "most specific"?** Predictable, explainable, cheap
(O(rules × conditions)), and the dashboard shows exactly that order. Overlapping rules are a
*configuration* concern surfaced by the UI (see failure scenario 5), not resolved by clever logic.

### 9.3 Deterministic percentage bucketing

```java
// Copy/paste allowed (≤25 lines): deterministic bucket in [0, 10000)
static int bucket(String flagKey, String salt, String userKey) {
    byte[] digest = sha256((flagKey + "." + salt + "." + userKey).getBytes(UTF_8));
    long head = ((digest[0] & 0xFFL) << 24) | ((digest[1] & 0xFFL) << 16)
              | ((digest[2] & 0xFFL) << 8)  |  (digest[3] & 0xFFL);
    return (int) (head % 10_000);
}
static boolean inRollout(int bucket, double percentage) {   // percentage 0..100, 2 decimals
    return bucket < Math.round(percentage * 100);            // 10.00 % → bucket < 1000
}
```

- **Deterministic**: no randomness, no state. The same user always lands in the same bucket for
  the same flag, so raising 10 % → 20 % *adds* users and never removes anyone (the first 10 %
  stay in). Every server node and every SDK instance agrees without coordination.
- **Salt per flag** (`flag.salt`, generated at creation, part of the snapshot): without it, the
  user in bucket 42 for flag A is in bucket 42 for *every* flag — the same 10 % of users would get
  every experimental feature. The salt decorrelates flags. LaunchDarkly does the same with a
  per-flag salt.
- **Why SHA-256 and not `hashCode()`**: `String.hashCode()` is stable in Java but not
  cross-language (TS SDK later), and clusters badly for similar keys. MurmurHash3 (32-bit, as
  in Guava `Hashing.murmur3_32_fixed()`) is the common industry choice because it is fast and
  uniform; SHA-256 is in the JDK with no dependency (important for the SDK — §13) and is fast
  enough (~µs). Pick one, **freeze it in the contract test vectors** (`"flag-a.s3cr3t.user-1" →
  bucket 7321`) and never change it after v1.0 — changing the hash re-shuffles every rollout.
  A practical reason to prefer SHA-256 here: Python's `hashlib.sha256` and the JDK's
  `MessageDigest` produce identical bytes with zero dependencies, so `sdk-python/` reproduces
  the bucket from the *spec* ("UTF-8 bytes of `flagKey.salt.userKey`, first 4 bytes big-endian
  unsigned, mod 10000") — MurmurHash3 has several variants (seed, `_fixed`, signedness) that
  cross-language ports get wrong.
- `mod 10000` gives two-decimal percentages (0.01 % granularity). `bucketBy` lets a rule bucket by
  `accountId` instead of user key so everyone in a company sees the same variation.

### 9.4 Evaluation reasons

Return a `reason` with every evaluation (`OFF`, `TARGET_MATCH`, `RULE_MATCH`, `FALLTHROUGH`,
`FLAG_NOT_FOUND`, `ERROR`) plus `ruleId`, `inRollout`, `bucket` (debug only) and the
`envVersion`/`flagVersion` that produced it. This is what makes "why did user X see Y?"
answerable and is the single most useful debugging feature you will build.

---

## 10. API design

Follow [06-rest-apis/api-design-guide.md](../../06-rest-apis/api-design-guide.md). Base path
`/api/v1` (admin, JWT) and `/sdk/v1` (SDK key). Errors as RFC 9457 `application/problem+json`
via Spring's `ProblemDetail`.

### 10.1 Admin API (JWT)

| Method | Path | Purpose | Role |
|---|---|---|---|
| `POST` | `/auth/register`, `/auth/login` | JWT issue | — |
| `POST/GET` | `/orgs`, `/orgs/{orgId}` | organizations | OWNER for mutate |
| `POST/GET/DELETE` | `/orgs/{orgId}/members` | memberships & roles | ADMIN+ |
| `POST/GET` | `/orgs/{orgId}/projects` | projects | EDITOR+ |
| `POST/GET/PATCH` | `/projects/{projectId}/environments` | environments (`protected`) | ADMIN+ |
| `POST/GET/PATCH` | `/projects/{projectId}/flags`, `/flags/{flagId}` | flag identity (archive instead of delete) | EDITOR+ |
| `GET/PUT` | `/environments/{envId}/flags/{flagKey}/draft` | mutable draft config | EDITOR+ |
| `POST` | `/environments/{envId}/flags/{flagKey}/versions:publish` `{comment}` | publish draft → version N+1 → snapshot rebuild | EDITOR+ (ADMIN+ if env protected) |
| `GET` | `/environments/{envId}/flags/{flagKey}/versions?page=` | version history | VIEWER+ |
| `GET` | `/environments/{envId}/flags/{flagKey}/versions/{n}` | one version (full body) | VIEWER+ |
| `POST` | `/environments/{envId}/flags/{flagKey}/versions/{n}:rollback` | new version copying n | EDITOR+/ADMIN+ |
| `POST` | `/environments/{envId}/flags/{flagKey}:toggle` `{enabled:false, comment}` | **kill switch** — publishes a version with only `enabled` changed | EDITOR+/ADMIN+ |
| `POST/GET/DELETE` | `/environments/{envId}/sdk-keys`, `/sdk-keys/{id}:rotate` | key lifecycle; plaintext returned once | ADMIN+ |
| `GET` | `/orgs/{orgId}/audit?entityType=&entityId=&cursor=` | audit feed, cursor pagination | VIEWER+ |
| `POST` | `/environments/{envId}/evaluate-debug` `{flagKey, context}` | admin-side evaluation with full reason (dashboard "test rules") | VIEWER+ |

Pagination: cursor-based on `audit` and `versions` (you did this in LedgerX M3); page/size elsewhere.

### 10.2 SDK API (SDK key in `Authorization: Bearer <sdk-key>` or `X-SDK-Key`)

| Method | Path | Purpose | Notes |
|---|---|---|---|
| `GET` | `/sdk/v1/config` | full environment snapshot (env resolved from the key) | `ETag: "<envVersion>-<hash>"`; honours `If-None-Match` → `304`; `Cache-Control: no-cache` (SDK must revalidate); `Content-Encoding: gzip` |
| `POST` | `/sdk/v1/evaluate` `{flagKey, context}` → `{value, variation, reason}` | server-side evaluation, one flag | client-side keys may only use this and `/evaluate-all` |
| `POST` | `/sdk/v1/evaluate-all` `{context}` → `{flags:{key:{value,variation,reason}}}` | bootstrap for thin clients | |
| `GET` | `/sdk/v1/stream` | `text/event-stream`: `event: changed` `data: {"envVersion":57}`; `event: ping` every 20 s; `retry: 2000` | client fetches `/config` on `changed` (thin events, fat fetch — §14) |

The `/config` response body (this is the SDK's snapshot type — version it: `"schemaVersion": 1`):

```json
{ "schemaVersion": 1, "environment": "prod", "envVersion": 57, "generatedAt": "2026-09-26T10:00:00Z",
  "flags": { "new-checkout": { "key": "new-checkout", "version": 12, "salt": "9f1c…", "type": "BOOLEAN",
             "enabled": true, "variations": {"on": true, "off": false}, "offVariation": "off",
             "targets": {"on": ["user-42"]}, "rules": [ … ], "defaultRollout": {"variation": "off"} } } }
```

### 10.3 Error catalogue

| HTTP | `type` (problem+json) | When |
|---|---|---|
| 400 | `validation-error` | body/params invalid (`errors[]` with field + message) |
| 401 | `unauthenticated` | missing/expired JWT; unknown/revoked SDK key |
| 403 | `forbidden` / `protected-environment` | role insufficient; publishing to prod without ADMIN |
| 404 | `not-found` | org/project/env/flag/version |
| 409 | `conflict` | duplicate flag key; draft changed since read (`If-Match` on draft `PUT`, optional); publish with empty diff |
| 412 | `precondition-failed` | `If-Match` mismatch on draft |
| 422 | `invalid-rule` | unknown operator, percentage out of range, unknown variation referenced |
| 429 | `rate-limited` | SDK API rate limit exceeded; `Retry-After` |
| 503 | `snapshot-unavailable` | cold start: no cache and DB unreachable; SDK keeps stale copy |

### 10.4 OpenAPI expectations

- springdoc-openapi at `/v3/api-docs` + Swagger UI; two groups: `admin` and `sdk`.
- Every endpoint: summary, security requirement, request/response schema, at least one example, all error responses from the catalogue.
- The `/sdk/v1/config` schema is the **published SDK contract** — export it to `docs/api/sdk-openapi.yaml` in CI and diff it in PRs (a change to it requires a `schemaVersion` bump or backward-compatibility note).

---

## 11. Package / module structure

One Maven multi-module repository (you built one for ForgeCI):

```
flagforge/
├── pom.xml                       (parent: Java 21, dependency management, plugins)
├── flagforge-common/             (NO Spring, NO Jackson annotations beyond what the SDK needs)
│   └── com.flagforge.common
│       ├── model/                Snapshot, FlagConfig, Rule, Condition, Rollout, Variation (records)
│       ├── evaluation/           Evaluator, EvaluationContext, EvaluationResult, Reason, Bucketing, Operators
│       └── json/                 SnapshotCodec (Jackson core only; see SDK note)
├── flagforge-server/             (Spring Boot 3.x)
│   └── com.flagforge.server
│       ├── auth/                 JWT filter, SDK-key filter, password hashing, current-user resolution
│       ├── org/                  organization, membership, roles
│       ├── project/              project, environment
│       ├── flags/                flag identity, drafts, versions, publish/rollback/toggle services
│       ├── evaluation/           server-side eval endpoint → common Evaluator; debug endpoint
│       ├── snapshots/            SnapshotBuilder, SnapshotCache (Caffeine + Redis), single-flight, ETag
│       ├── sdkapi/               /sdk/v1 controllers, SSE registry, Redis pub/sub subscriber, rate limit
│       ├── audit/                AuditRecorder (AOP or explicit service calls), audit queries
│       ├── config/               Security config, Redis config, OpenAPI groups, Jackson
│       └── common/               ProblemDetail advice, request-id filter, pagination helpers
├── flagforge-sdk/                (plain Java 21 library; depends ONLY on flagforge-common + Jackson)
│   └── com.flagforge.sdk
│       ├── FlagClient, FlagClientBuilder, FlagClientConfig
│       ├── cache/                SnapshotHolder (AtomicReference<Snapshot>), StaleTracker
│       ├── poller/               Poller (ScheduledExecutorService, ETag, backoff)
│       ├── streaming/            SseConnection (HttpClient, line parser, keepalive watchdog, reconnect)
│       ├── transport/            SdkHttp (java.net.http.HttpClient wrapper, timeouts, headers)
│       └── log/                  SdkLogger (System.Logger or SLF4J-optional)
├── flagforge-sdk-sample/         (tiny app: prints flag values every second; used in demos)
├── flagforge-benchmarks/         (JMH: local evaluation micro-benchmarks)
├── flagforge-ui/                 (React 18 + TS + Vite)
├── contract/                     (language-neutral test vectors — the shared evaluation contract)
│   ├── bucket-vectors.json       [{flagKey, salt, userKey, bucket}] ≥ 20 entries, frozen at v1.0
│   ├── evaluation-vectors.json   [{name, snapshot, flagKey, context, expected:{variation, reason, ruleId}}] ≥ 40 cases
│   └── snapshot-schema.json      JSON Schema for /sdk/v1/config (schemaVersion 1)
├── sdk-python/                   (Python ≥ 3.12, no third-party runtime deps; pytest, mypy)
│   ├── pyproject.toml
│   ├── src/flagforge/            client.py (FlagClient), evaluator.py, bucketing.py, snapshot.py,
│   │                             poller.py (threading.Timer / thread + ETag), context.py, py.typed
│   ├── tests/                    test_evaluator.py, test_bucketing_vectors.py, test_client_polling.py
│   │                             (pytest-httpserver or a stdlib http.server fixture), test_offline.py,
│   │                             test_contract_vectors.py (reads ../contract/*.json)
│   └── README.md                 install, quick start, contract statement, limitations
└── tools/                        (Python scripts + package `flagforge_tools/`, pytest-tested, README)
    ├── rollout_sim.py            distribution + stickiness proof; optional cross-check vs server
    ├── flagconf_lint.py          rule-set linter: invalid / overlapping / unreachable / duplicate priority
    ├── tests/                    test_rollout_sim.py, test_flagconf_lint.py
    └── README.md
```

**Why the contract is JSON vectors, not shared code:** the Java SDK and server share the evaluator
*module*; the Python SDK cannot. The only thing that can keep three implementations (server,
Java SDK, Python SDK — later a TS SDK) in agreement is a **written specification plus
machine-checked vectors**. The spec is §9; the vectors are the executable version of it. The
Python evaluator is written from the spec, and the moment it passes the same vectors as Java,
you have evidence the spec is complete; any case where they disagree is a spec bug, not a code
bug — fix the spec, add a vector, fix both. The `tools/` are deliberately Python: analysis and
simulation scripts (argparse, `json`, `statistics`, `collections.Counter`) are shorter and faster
to iterate in Python, and they exercise the Python you use in the coding-interview track
([19-python/04-testing-and-scripting.md](../../19-python/04-testing-and-scripting.md)).

**Why the evaluator lives in `common` and is shared:** the server (`/sdk/v1/evaluate`, the
dashboard's "test rules") and the SDK (local evaluation) must return *identical* results for the
same snapshot and context; two implementations would drift the moment someone fixes an operator
bug in one. One module, one test suite, one set of hash vectors. The price: `common` must stay
dependency-light so the SDK does not drag Spring into customer applications (see §13.7).

---

## 12. Authentication and authorization

### 12.1 Dashboard users — JWT

Spring Security 6, stateless, `Authorization: Bearer <jwt>`; HS256 with a 256-bit secret from
the environment (RS256 if you want to show asymmetric keys); expiry 60 min; refresh token
optional [ADV]. Same pattern as FlowGrid M1 — reuse your filter, don't re-learn it. See
[05-spring-boot/05-security-jwt.md](../../05-spring-boot/05-security-jwt.md).

### 12.2 SDK keys — per environment, two kinds

| Kind | Format | Given to | Allowed endpoints | Contains rules? |
|---|---|---|---|---|
| **Server key** | `ffs_<env>_<32 random base62>` | backend services (trusted) | `/config`, `/stream`, `/evaluate*` | yes — `/config` returns full rules incl. targeting lists |
| **Client key** | `ffc_<env>_<…>` | browsers / mobile (untrusted, visible in devtools) | `/evaluate`, `/evaluate-all` only | never |

Stored as SHA-256 hash + display prefix. Auth filter: constant-time compare of hashes, resolve
`environment_id` + `kind`, put a `SdkPrincipal` into the security context. Rotation: create new
key, old key gets `expires_at = now + grace` (e.g. 24 h), revoke after (§20).

### 12.3 Organization roles and environment protection

| Action | VIEWER | EDITOR | ADMIN | OWNER |
|---|---|---|---|---|
| read flags/versions/audit | ✔ | ✔ | ✔ | ✔ |
| edit drafts, publish to non-protected env | | ✔ | ✔ | ✔ |
| publish/rollback/toggle in **protected** env (prod) | | | ✔ | ✔ |
| manage environments, SDK keys | | | ✔ | ✔ |
| manage members, delete org | | | | ✔ |

Implement with method security (`@PreAuthorize("@authz.canPublish(#envId)")`) backed by a small
`Authz` bean that loads the membership for (current user, env → project → org) — one query,
cached per request. Every 403 is audited too.

---

## 13. SDK design

The SDK is the part interviewers will ask most about, because "design a client library" is a
different skill from "build an API". Read `docs/SDK.md` requirements in
[docs-and-resume.md](./docs-and-resume.md).

### 13.1 Public API surface (keep it *small*)

```java
FlagClient client = FlagClient.builder()
        .sdkKey(System.getenv("FLAGFORGE_SDK_KEY"))
        .baseUrl("https://flags.example.com")
        .pollingInterval(Duration.ofSeconds(30))
        .streaming(true)                        // SSE with polling fallback (M3 advanced)
        .initTimeout(Duration.ofSeconds(5))     // block build() until first snapshot or timeout
        .build();

EvaluationContext ctx = EvaluationContext.builder("user-42")
        .attribute("email", "a@corp.com").attribute("country", "DE").build();

boolean on   = client.isEnabled("new-checkout", ctx);              // false if unknown/error
String  tier = client.variation("pricing-tier", ctx, "standard");  // typed default
EvaluationDetail<Boolean> d = client.isEnabledDetail("new-checkout", ctx); // value + reason
client.close();                                                     // stops threads, closes SSE
```

Also: `client.status()` (`INITIALIZING`, `READY`, `STALE`, `OFFLINE`), `client.snapshotVersion()`,
`client.addListener(FlagChangeListener)` [ADV], `FlagClient.offline(Map<String,Object> values)`.

### 13.2 Thread-safety and the immutable snapshot swap

Evaluation is called from every request thread of the host application; refresh happens on a
background thread. The design that makes this trivially safe: **snapshots are immutable objects**
(records, unmodifiable maps) held in an `AtomicReference`; a refresh builds a whole new snapshot
and swaps the reference once. Readers never lock; they read one reference and evaluate against a
consistent object even if a swap happens mid-evaluation (§ failure scenario 6).

```java
final class SnapshotHolder {
    private final AtomicReference<Snapshot> ref = new AtomicReference<>(Snapshot.EMPTY);
    Snapshot current() { return ref.get(); }
    boolean swapIfNewer(Snapshot next) {         // ignore out-of-order responses
        for (;;) {
            Snapshot cur = ref.get();
            if (next.envVersion() <= cur.envVersion()) return false;
            if (ref.compareAndSet(cur, next)) return true;
        }
    }
}
```

### 13.3 Initialization semantics (make this a builder choice, document both)

| Mode | Behaviour | When |
|---|---|---|
| **Blocking init** (`initTimeout(5s)`) | `build()` fetches the first snapshot; returns when loaded or after timeout (then status `INITIALIZING`, evaluations return defaults) | web services that prefer correct flags on the first request |
| **Non-blocking** (`initTimeout(ZERO)`) | `build()` returns immediately; first poll runs in background | CLIs, workers, anything that must not delay startup |

Never throw from `build()` because the server is down — log at WARN and continue with defaults.
Do throw from `build()` for *programmer* errors (blank SDK key, malformed URL).

### 13.4 Fallback rules (the contract, in order)

1. Snapshot present (fresh or stale) → evaluate locally; if stale beyond `staleAfter` (e.g. 3 × polling interval) set status `STALE` and log once per transition.
2. No snapshot ever loaded → return the **caller's default** with reason `CLIENT_NOT_READY`.
3. Flag missing from snapshot → caller's default, reason `FLAG_NOT_FOUND`.
4. Type mismatch (`variation("x", ctx, "str")` on a boolean flag) → caller's default, reason `WRONG_TYPE`.
5. Any exception inside evaluation → caller's default, reason `ERROR`; never propagate.

"Stale-if-error": a 5xx/timeout on refresh **keeps** the last good snapshot (it is never cleared)
and schedules a retry with exponential backoff + jitter (1 s → 2 s → … cap 60 s); a 401 (revoked
key) stops polling and sets status `UNAUTHORIZED` (keeps stale data, logs at ERROR once).

### 13.5 Offline mode

`FlagClient.offline(Map.of("new-checkout", true))` (or `.offline(true).bootstrap(snapshotJson)`):
no network, no threads, evaluates from the given values/snapshot. Purpose: unit tests in the host
application, local development without a server, air-gapped runs. `close()` is a no-op.

### 13.6 Logging, versioning, publishing

- Logging: `System.Logger` (JDK) by default so there is **no logging-framework dependency**; SLF4J
  bridge optional. Levels: startup summary at INFO; refresh at DEBUG; transitions (READY↔STALE,
  UNAUTHORIZED) at WARN/ERROR exactly once. Never log the SDK key (log the prefix).
- **Semantic versioning**: `0.x` during M3, `1.0.0` at project v1.0. Breaking change = major.
  The snapshot `schemaVersion` is separate from the SDK version; the SDK declares which schema
  versions it accepts and rejects (keeps stale) unknown ones.
- Publish to **GitHub Packages** (Maven) from CI on tag `sdk-v*`: `maven-deploy-plugin` with
  `<distributionManagement>` pointing to `https://maven.pkg.github.com/<you>/flagforge` and
  `GITHUB_TOKEN`. Attach sources + javadoc jars. Document the consumer `settings.xml` snippet.
- Sample app (`flagforge-sdk-sample`): a `main` that builds a client, prints
  `new-checkout=true (RULE_MATCH r2, v57)` every second; used for the propagation demo GIF.

### 13.7 Why the SDK has no Spring dependency

The SDK is added to *other people's* applications: a Spring Boot 2 app, a Quarkus app, a plain
`main`, a Kafka consumer. If `flagforge-sdk` depended on Spring, it would (1) force a Spring
version on the host and cause dependency conflicts, (2) add ~10 MB of jars for a library whose
job is to hold a map in memory, (3) fail to start in non-Spring apps. Use only the JDK:
`java.net.http.HttpClient` (HTTP/1.1 + HTTP/2, async, timeouts, SSE via `BodyHandlers.ofLines()`),
`java.util.concurrent` (`ScheduledExecutorService`, `AtomicReference`), `System.Logger`.
Jackson (core + databind) is the one allowed dependency for JSON — and even that is a
trade-off worth discussing (shade it, or use a tiny hand-written parser [ADV]). This is also why
the evaluator lives in `flagforge-common` with the same constraint.

### 13.8 Resource discipline

One daemon `ScheduledExecutorService` (single thread) per client; one `HttpClient` per client;
`close()` is idempotent and shuts both down; use `try-with-resources` friendliness
(`implements AutoCloseable`). Creating a second client with the same key logs a warning (a common
misuse: one client per request).

---

## 14. Propagation design

How a publish reaches every SDK. Build it in this order — each step is useful alone.

### 14.1 Polling with ETag (M3 core)

```
SDK every 30 s:  GET /sdk/v1/config   If-None-Match: "57-9f1c"
Server:          etag == current?  → 304 (no body, ~200 B)   else → 200 + full snapshot + new ETag
SDK:             200 → parse → swapIfNewer; 304 → touch lastSuccess; error → keep, backoff
```

Cheap, stateless, load-balancer friendly, works through any proxy. Worst-case propagation = interval.
ETag is derived from `envVersion` + content hash so a rebuild with identical content is still a 304.

### 14.2 Server-Sent Events with keepalive and reconnect (M3 advanced → M4)

- `GET /sdk/v1/stream` → `SseEmitter` (timeout `Long.MAX_VALUE` or 1 h with client-side reconnect),
  registered in a `ConcurrentHashMap<envId, Set<SseEmitter>>`.
- Server sends `event: ping` every 20 s (keeps NATs/LBs from cutting idle connections; lets the
  SDK detect a dead connection: no bytes for 45 s ⇒ reconnect).
- On change: send `event: changed` `data: {"envVersion":58}` — **thin event, fat fetch**: the SDK
  then does a normal `GET /config` (with ETag). Why not push the snapshot in the event? Snapshots
  can be hundreds of KB; sending them over N connections at once is the fan-out overload scenario;
  and a fetch after the event keeps one code path for loading.
- SDK: `HttpClient.sendAsync(..., BodyHandlers.ofLines())`, parse `event:`/`data:`/blank-line
  frames, reconnect with backoff + jitter on any termination, **keep polling at a long interval
  (e.g. 5 min) as a safety net** even when streaming, and revert to normal polling when streaming
  fails 3 × in a row. You chose SSE over WebSockets in ForgeCI M3 — reuse the reasoning.

### 14.3 Redis pub/sub between API nodes (M4)

With two or more API nodes, a publish on node A must reach SSE clients connected to node B:
`PUBLISH flagforge:changes {"envId":..,"envVersion":58}` after `SET snapshot:{envId}`. Every node
subscribes (Spring Data Redis `MessageListenerContainer`), evicts its in-process cache for that
env and notifies its local emitters. Pub/sub is fire-and-forget — a node that was disconnected
misses the message; that is why polling remains the backstop and why the in-process cache has a
short TTL (e.g. 60 s) as well. Mechanics: [04-sql-databases/redis.md](../../04-sql-databases/redis.md).

### 14.4 Cache stampede protection (M4)

After a publish, N SDKs receive `changed` at the same millisecond and all call `/config`. If the
in-process cache was just evicted and Redis is slow/cold, every request could try to rebuild the
snapshot from Postgres — the stampede. Defences (do all three, measure the difference):

1. **Write-through on publish**: the publisher writes the new snapshot to Redis *before* publishing the change event, so followers find it hot.
2. **Single-flight** per env in each node: the first miss computes, concurrent misses wait on the same `CompletableFuture` (Caffeine `LoadingCache` gives this for free; or a `ConcurrentHashMap.computeIfAbsent` over futures). Optionally a Redis lock (`SET lock:snapshot:{env} NX PX 5000`) so only one *node* rebuilds from Postgres.
3. **Jitter** in the SDK: on `changed`, wait `random(0..2 s)` before fetching; on poll, ±10 % interval jitter.
Theory: [15-system-design/caching.md](../../15-system-design/caching.md).

---

## 15. Testing strategy

| Layer | Tool | What | Where |
|---|---|---|---|
| **Evaluator unit tests** | JUnit 5, AssertJ, jqwik (property-based) | operators, missing attributes, priority ordering, off/targets/rules/fallthrough, reasons; **property**: same (flag, salt, user) ⇒ same bucket across 10k random inputs; **distribution**: 100k sequential users at 10 % ⇒ 10 000 ± 100 in rollout (± 1 pt); monotonic rollout: users in at 10 % are in at 20 % | `flagforge-common` |
| **Hash vectors** | JUnit parameterized | 20 fixed `(flagKey, salt, userKey) → bucket` triples checked into `common/src/test/resources/bucket-vectors.json`; the TS SDK will reuse them | `flagforge-common` |
| **Server slices** | `@WebMvcTest`, `@DataJpaTest` | validation → 400/422, ProblemDetail shape, authz 403, JSONB mapping | `flagforge-server` |
| **Server integration** | `@SpringBootTest` + Testcontainers (Postgres 16, Redis 7) | publish → version N+1 → snapshot in Redis → ETag changes → 304 on match; rollback copies body; immutability (UPDATE rejected by DB privileges); pub/sub delivers to a second app context | `flagforge-server` |
| **SDK unit** | JUnit 5 + WireMock or `MockWebServer` (OkHttp, test scope only) | snapshot parse, swapIfNewer ignores older, ETag round trip, backoff schedule, 401 stops polling, SSE frame parser, reconnect on stream close, init timeout | `flagforge-sdk` |
| **SDK contract tests** | Testcontainers running the **server image** (or `@SpringBootTest` + SDK in the same JVM) | SDK against a real server: evaluations equal server-side `/evaluate` for 200 random contexts; update visible within 2 s via SSE; within interval via polling | `flagforge-sdk` (profile `contract`) |
| **Resilience** | WireMock fault injection | server 500/timeout/connection-reset → defaults before first load, stale after; malformed JSON → keep old snapshot; slow response respects `readTimeout` | `flagforge-sdk` |
| **Cross-SDK contract (Python)** | pytest reading `contract/*.json` | `test_bucket_vectors_match_contract`, `test_evaluation_vectors_match_contract`, `test_python_and_java_sdk_agree_on_vectors` (runs both SDKs over the same vector file — Java via a small `contract-runner` main invoked by subprocess, or by comparing both outputs to the same expected file); polling + ETag with a local HTTP fixture; offline mode; defaults on server down | `sdk-python/tests` |
| **Python tools** | pytest, `hypothesis` (optional) | `rollout_sim`: 100k users at 10 % ⇒ 9.9–10.1 %; stickiness 10 → 20 %; mismatch count vs server = 0 on a live stack; `flagconf_lint`: detects superset overlap, catch-all shadowing, unknown operator, bad percentage; clean config ⇒ exit 0 | `tools/tests` |
| **Load / latency** | k6, JMH | §21 | `flagforge-benchmarks`, `perf/` |
| **UI** | Vitest + React Testing Library | rules editor validation, versions list, rollback dialog | `flagforge-ui` |

Testing references: [09-testing/junit5.md](../../09-testing/junit5.md), [09-testing/mockito.md](../../09-testing/mockito.md), [09-testing/spring-testing.md](../../09-testing/spring-testing.md), [09-testing/testcontainers.md](../../09-testing/testcontainers.md), [09-testing/frontend-testing.md](../../09-testing/frontend-testing.md). Named tests per milestone in [milestones.md](./milestones.md).

---

## 16. Docker and Compose

`docker-compose.yml` services: `postgres:16-alpine` (healthcheck `pg_isready`), `redis:7-alpine`
(`--maxmemory 128mb --maxmemory-policy noeviction` — evicting snapshots would silently push load
to Postgres; discuss), `server` (multi-stage Dockerfile: `maven:3.9-eclipse-temurin-21` build →
`eclipse-temurin:21-jre` runtime, non-root, `SPRING_PROFILES_ACTIVE=docker`), `server-2` (same
image, second node to demonstrate pub/sub + SSE across nodes), `ui` (nginx serving Vite build,
proxying `/api` and `/sdk`), `sample-app` (SDK sample pointed at `server`). Compose profile
`perf` adds a `k6` container. See [11-docker/compose.md](../../11-docker/compose.md) and
[11-docker/dockerfiles.md](../../11-docker/dockerfiles.md).

---

## 17. CI/CD

GitHub Actions ([13-cicd/github-actions.md](../../13-cicd/github-actions.md), [13-cicd/pipeline-examples.md](../../13-cicd/pipeline-examples.md)):

| Workflow | Trigger | Steps |
|---|---|---|
| `ci.yml` | PR, push to `main` | `mvn -B verify` (unit + Testcontainers integration via Docker service), `npm ci && npm test && npm run build` in `flagforge-ui`, **`python` job**: `pip install -e sdk-python[dev] -e tools[dev] && ruff check && mypy --strict && pytest sdk-python tools` (contract vectors from `contract/`), `flagconf_lint.py` over `config/fixtures/*.json`, upload OpenAPI export as artifact, diff `docs/api/sdk-openapi.yaml` |
| `release-server.yml` | tag `v*` | build server + ui images → push to GHCR `ghcr.io/<you>/flagforge-server:<tag>`; deploy job (SSH to EC2, `docker compose pull && up -d`) |
| `release-sdk.yml` | tag `sdk-v*` | `mvn -pl flagforge-common,flagforge-sdk -am deploy` to GitHub Packages with sources/javadoc; create GitHub Release with changelog |
| `perf.yml` (manual) | `workflow_dispatch` | JMH run, upload results JSON; k6 against Compose stack |

Cache Maven (`~/.m2`) and npm; fail on `-Dmaven.test.failure.ignore=false`; run `mvn -T 1C`.

---

## 18. AWS deployment plan

Fourth deploy — do it faster than ForgeCI's: [12-aws/deploy-walkthrough.md](../../12-aws/deploy-walkthrough.md).

| Component | Choice | Notes |
|---|---|---|
| Compute | 1 × EC2 `t3.small` running Compose (`server`, `server-2`, `ui`, `redis`) | two server containers behind nginx demonstrate pub/sub fan-out; ALB optional |
| Database | RDS PostgreSQL 16 `db.t3.micro`, private subnet, SG allows only EC2 | automated backups on |
| Redis | Redis on the EC2 (Compose) — ElastiCache only if cheaper for you ([12-aws/cost-safety.md](../../12-aws/cost-safety.md)) | `noeviction`, AOF off (cache only) |
| Storage | S3 for benchmark reports / exported snapshots (optional) | |
| IAM | instance role with CloudWatch Logs write only; no long-lived keys on the box | [12-aws/iam.md](../../12-aws/iam.md) |
| Logs/alarms | CloudWatch Logs agent (Compose `awslogs` driver); alarm on 5xx rate and on `snapshot_age_seconds > 300` | [12-aws/cloudwatch.md](../../12-aws/cloudwatch.md) |
| TLS | nginx + Let's Encrypt (or ALB + ACM); SDKs must talk HTTPS — keys travel in headers | |
| Secrets | JWT secret, DB password via SSM Parameter Store (SecureString) read in the deploy script | |

Write `docs/DEPLOYMENT.md` with the exact commands, security groups, and a teardown checklist.

---

## 19. Logging and error handling

- Structured JSON logs (Logback JSON encoder) with `requestId` (filter, echoed as `X-Request-Id`),
  `userId`/`sdkKeyPrefix`, `envId`, `flagKey`, `envVersion`. Never log SDK keys, JWTs, or full
  targeting lists (PII) — log counts.
- Log lines you *must* have (they are what the failure scenarios inspect): `snapshot.rebuilt
  env=… version=… flags=… bytes=… ms=…`, `snapshot.cache miss level=local|redis|db`,
  `sse.connected/disconnected env=… total=…`, `publish env=… flag=… v=… by=…`,
  `redis.unavailable falling back to …`.
- Errors: one `@RestControllerAdvice` producing `ProblemDetail` for the catalogue in §10.3;
  domain exceptions (`FlagNotFound`, `ProtectedEnvironment`, `InvalidRule`, `StaleDraft`) map 1:1.
  Unexpected exceptions → 500 with `requestId` only. See
  [05-spring-boot/04-validation-errors.md](../../05-spring-boot/04-validation-errors.md).
- Actuator: `/actuator/health` (readiness includes Redis *but* liveness does not — Redis down must
  not restart the app), `/actuator/prometheus` with `flagforge_evaluations_total{env,flag,reason}`,
  `flagforge_snapshot_age_seconds{env}`, `flagforge_sse_connections{env}`, `flagforge_cache_hits_total{level}`.

---

## 20. Security

| Concern | Control |
|---|---|
| Server keys in browsers | Client keys can't fetch `/config`; the dashboard shows a warning when generating a server key; docs state it; the TS SDK refuses `ffs_` keys at construction |
| Key storage | SHA-256 hash only; display prefix; constant-time compare |
| Key rotation | rotate endpoint: new key + old key `expires_at = now + grace`; audit event; SDK handles 401 by stopping polling (keeps stale) and logging; scenario 11 in [failure-engineering.md](./failure-engineering.md) |
| Rate limiting SDK API | per key: token bucket in Redis (you wrote the Lua script in FlowGrid/ForgeCI) e.g. 60 `/config` per minute, 600 `/evaluate` per minute; `429` + `Retry-After`; SSE connections per key capped (e.g. 100) |
| Audit everything | every mutating admin call → `audit_event` in the same transaction; 403s audited; SDK key create/rotate/revoke audited |
| JWT | short expiry, secret ≥ 256 bit from env, no JWT in URLs, CORS restricted to the dashboard origin |
| Input | Bean Validation on all DTOs; rules validated semantically (operator vs value type, variations exist, percentage 0–100); size limits (rules ≤ 50, conditions ≤ 20, targets ≤ 10 000) to bound snapshot size |
| Multi-tenancy | every query scoped by org via the membership check; IDs are UUIDs; integration test: user in org A gets 404 (not 403) for org B's flag |
| Dependencies | `mvn dependency-check` or GitHub Dependabot; OWASP API Top 10 pass in Week 24 |

---

## 21. Performance

What you measure, with methodology, becomes your only legitimate résumé numbers ([docs-and-resume.md](./docs-and-resume.md)).

| Question | Method | Discussion points |
|---|---|---|
| Server-side eval p50/p95/p99 | k6, 200 → 500 → 1000 VUs, 60 s, snapshot in in-process cache vs Redis only vs cold (DB) | difference between the three tiers is the whole argument for the cache hierarchy |
| Local SDK eval latency | JMH: 1 rule / 5 rules / 20 rules, boolean flag, warm JVM; `@BenchmarkMode(SampleTime)` for percentiles | typically sub-µs to few µs; allocation per evaluation (context map, result object) is the cost — measure `-prof gc` |
| Snapshot size / parse cost | 50, 500, 2 000 flags; bytes gzipped vs raw; Jackson parse time | why targeting lists dominate; why segments [ADV] shrink it; when a per-flag delta protocol would be worth it |
| Propagation delay | publish timestamp vs sample-app log timestamp, 20 trials, SSE vs polling(30 s) | includes jitter deliberately added |
| Redis vs in-process cache | k6 `/config` with Caffeine on/off | Caffeine hit: no network; Redis hit: ~0.3–1 ms RTT; DB: ms–tens of ms |

---

## 22. Scalability

Read-heavy by nature: thousands of SDK instances polling, a handful of humans publishing.

| Dimension | Approach | Limit / discussion |
|---|---|---|
| Snapshot reads | in-process cache → Redis → (never DB on hot path); ETag makes 99 % of polls 304s; put `/config` behind a CDN/edge cache keyed by env + ETag [ADV] | one node serves tens of thousands of 304/s |
| Many SDK instances | stateless API nodes behind LB; each SDK is self-sufficient after load | scale nodes horizontally; SDK count doesn't touch DB |
| SSE fan-out | each node holds its emitters; a publish is one Redis message → each node writes ~50 B to each connection | ~10k connections per node (file descriptors, servlet threads if not async; use Spring's async SSE or WebFlux [ADV]); cap per key; beyond that: dedicated stream nodes |
| Multi-region | snapshots replicated by Redis replica per region or simply regional API nodes reading from a primary + per-region Redis fed by pub/sub; SDKs point at nearest | eventual consistency measured in seconds; the versioned snapshot + `swapIfNewer` makes reordering safe |
| Write path | one Postgres; publishes are rare | fine well beyond this project's scale |
| 100× users | nothing changes on the write path; add API nodes and Redis replicas; reconsider SSE vs polling ratio | see [interview-questions.md](./interview-questions.md) |

Theory: [15-system-design/scalability.md](../../15-system-design/scalability.md), [15-system-design/fundamentals.md](../../15-system-design/fundamentals.md).

---

## 23. Git strategy, issues, PR workflow

- `main` protected; feature branches `m1/versioning`, `m2/evaluator`, `m3/sdk-poller` …; squash-merge; conventional commits (`feat(sdk): …`, `fix(snapshots): …`). [02-git/workflows.md](../../02-git/workflows.md).
- **GitHub milestones** `M1 Model & versioning`, `M2 Evaluation & cache`, `M3 Java SDK`, `M4 Dashboard, propagation & deploy`; issues from the task lists in [milestones.md](./milestones.md) with labels `server`, `sdk`, `ui`, `infra`, `docs`, `test`.
- PR template: what/why, how tested (paste test names), screenshots for UI, checklist (tests, docs, OpenAPI updated, audit event added, no secrets). Self-review every PR line by line before merging; leave at least one comment to yourself — this is the review habit interviewers ask about.
- Tags: `v0.1` (MVP, end M2), `sdk-v0.1.0` (M3), `v1.0` + `sdk-v1.0.0` (end M4).

---

## 24. README and demo requirements

**Repo README must contain:** one-paragraph pitch; architecture diagram; "run it in 2 minutes"
(`docker compose up` → URL → seeded org/flags → `curl` evaluate); SDK quick start (5 lines,
Maven coordinates from GitHub Packages); feature table with tier tags; measured numbers with
links to `docs/PERFORMANCE.md` (never numbers without methodology); docs index; screenshots
(dashboard, rules editor, version history/rollback, audit); status badges (CI, SDK version).

**Demo (≤ 3 min, GIF or video):** open dashboard → flag `new-checkout` off → sample app prints
`false` → set rule "10 % rollout" and publish → sample app prints `true` for user-7 and `false`
for user-3 within 2 s → raise to 50 % → user-7 still `true` (monotonic) → hit the kill switch →
all `false` → roll back to the 50 % version → audit trail shows every step with the author.
Then `docker stop redis` → sample app keeps answering.

---

## 25. Learning links

| Topic | File | Used in |
|---|---|---|
| Caching strategy, stampedes, TTLs | [15-system-design/caching.md](../../15-system-design/caching.md) | M2, M4 |
| Redis mechanics, pub/sub, `SET NX PX` | [04-sql-databases/redis.md](../../04-sql-databases/redis.md) | M2, M4 |
| Spring caching & scheduling | [05-spring-boot/08-caching-scheduling.md](../../05-spring-boot/08-caching-scheduling.md) | M2 |
| API design, ETags, problem+json | [06-rest-apis/api-design-guide.md](../../06-rest-apis/api-design-guide.md), [06-rest-apis/http-for-apis.md](../../06-rest-apis/http-for-apis.md) | M1–M3 |
| Schema design, JSONB, indexes | [04-sql-databases/04-schema-design.md](../../04-sql-databases/04-schema-design.md), [04-sql-databases/03-advanced-queries.md](../../04-sql-databases/03-advanced-queries.md), [04-sql-databases/05-indexes-performance.md](../../04-sql-databases/05-indexes-performance.md) | M1 |
| Security & JWT | [05-spring-boot/05-security-jwt.md](../../05-spring-boot/05-security-jwt.md) | M1 |
| Java concurrency (AtomicReference, executors, HttpClient) | [01-java/07-concurrency.md](../../01-java/07-concurrency.md), [14-cs-fundamentals/concurrency.md](../../14-cs-fundamentals/concurrency.md) | M3 |
| Modern Java (records, sealed types for rules) | [01-java/04-modern-java.md](../../01-java/04-modern-java.md) | M2, M3 |
| Maven multi-module, publishing | [01-java/08-maven-build.md](../../01-java/08-maven-build.md) | M3 |
| Testing | [09-testing/README.md](../../09-testing/README.md) | all |
| Python scripting, pytest, type hints (tools/, sdk-python/) | [19-python/04-testing-and-scripting.md](../../19-python/04-testing-and-scripting.md), [19-python/python-for-java-devs.md](../../19-python/python-for-java-devs.md), [19-python/03-pitfalls-and-complexity.md](../../19-python/03-pitfalls-and-complexity.md) | M3, M4 |
| React forms & architecture | [08-react/03-forms-routing.md](../../08-react/03-forms-routing.md), [08-react/05-architecture-testing.md](../../08-react/05-architecture-testing.md) | M4 |
| System design (the interview version) | [15-system-design/junior-design-problems.md](../../15-system-design/junior-design-problems.md), [16-interview-prep/system-design-interview.md](../../16-interview-prep/system-design-interview.md) | W22 mock |
| Deploy | [12-aws/deploy-walkthrough.md](../../12-aws/deploy-walkthrough.md), [13-cicd/pipeline-examples.md](../../13-cicd/pipeline-examples.md) | M4 |
| Deep-dive rehearsal | [16-interview-prep/project-deep-dive.md](../../16-interview-prep/project-deep-dive.md) | W23 |

Next: open [milestones.md](./milestones.md) and start M1.
