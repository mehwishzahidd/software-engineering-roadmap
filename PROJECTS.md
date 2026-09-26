# 🏗️ PROJECTS — The Four Flagship Projects

> Root overview of **FlowGrid**, **LedgerX**, **ForgeCI** and **FlagForge**: why there are four,
> why they are built one after another, what each one proves, how scope is controlled, and what
> "done" means. The full specs (five files per project) live in [`18-projects/`](./18-projects/README.md).
> Order, weeks and hours come from [`ROADMAP.md`](./ROADMAP.md) — if this file ever disagrees, the roadmap wins.

[← README](./README.md) · [Roadmap](./ROADMAP.md) · [Project selection](./PROJECT_SELECTION.md) · [Project tracker](./trackers/project-tracker.md)

---

## Contents

1. [Why four sequential projects](#1-why-four-sequential-projects)
2. [The four projects at a glance](#2-the-four-projects-at-a-glance)
3. [Technology × project matrix](#3-technology--project-matrix) · [3a. Python engineering component per project](#3a-python-engineering-component-per-project)
4. [The compounding plan](#4-the-compounding-plan)
5. [Scope tiers and the scope-control rule](#5-scope-tiers-and-the-scope-control-rule)
6. [Timeline and milestones](#6-timeline-and-milestones)
7. [The project development rule](#7-the-project-development-rule)
8. [Definition of done](#8-definition-of-done)
9. [GitHub quality standard](#9-github-quality-standard)
10. [Performance and benchmarking rule](#10-performance-and-benchmarking-rule)
11. [Failure engineering as a discipline](#11-failure-engineering-as-a-discipline)
12. [Links](#12-links)

---

## 1. Why four sequential projects

The résumé lists Java, Spring Boot, PostgreSQL, REST, React/TypeScript, Docker, AWS, Redis,
JUnit and CI/CD. Past roles prove those skills were *once* real; after time away, the four
projects are what prove they are **current**. That only works if the projects are:

- **Few enough to finish.** Four well-scoped, deployed, documented systems beat ten half-built ones.
  A recruiter opens one repo; an interviewer probes one system for 15 minutes.
- **Different enough to tell four stories.** Each project has exactly one technical identity
  (domain + concurrency; correctness + ledger; async execution + recovery; latency + SDK). No two
  projects prove the same thing.
- **Sequential, never parallel.** Knowledge compounds only when each project *reuses* what the last
  one taught and spends its hours on something new. Building all four at once would mean learning
  controllers, JPA, auth and deployment four times badly instead of once well.
- **Hard enough to be defensible.** Every project contains at least one problem that cannot be
  faked in an interview: a concurrency test that must pass, an invariant that must hold, a crash
  that must be recovered from, a p99 that must be measured.

**Never start a fifth project. Never restart one "cleaner". Refactor in place.** (ROADMAP §13.)

---

## 2. The four projects at a glance

| # | Project | Weeks | Hours | Technical identity | What it proves is CURRENT skill |
|---|---|---|---:|---|---|
| 1 | [**FlowGrid**](./18-projects/flowgrid/README.md) — multi-warehouse fulfillment & inventory platform | 4–8 | 125–150 | Domain modelling, inventory state machine, reservation concurrency, idempotent orders, deterministic multi-warehouse allocation, Redis cache-aside, full stack, first AWS deploy | Spring Boot 3 / JPA / Flyway / Spring Security + JWT, PostgreSQL transactions and `SELECT … FOR UPDATE`, React + TypeScript, Docker Compose, GitHub Actions, EC2 + RDS + S3 + CloudWatch, k6 baseline. Plus real domain knowledge from fulfillment/logistics operations. |
| 2 | [**LedgerX**](./18-projects/ledgerx/README.md) — digital wallet & double-entry ledger engine | 9–13 | 125–150 | Correctness: append-only immutable ledger, isolation levels, pessimistic vs optimistic locking, idempotency done properly, compensating entries, transactional outbox, reconciliation, failure injection, invariant suite | PostgreSQL constraints/triggers/MVCC, `@Transactional` propagation and pitfalls, `BigDecimal` money, lock ordering, crash-consistency testing with Testcontainers |
| 3 | [**ForgeCI**](./18-projects/forgeci/README.md) — distributed CI/CD execution platform | 14–19 | 170–200 | Queues and worker pools, Docker-isolated execution via Engine API, GitHub webhooks (HMAC + dedupe), live logs over SSE, timeouts/cancellation/retries, heartbeats and lease-based orphan recovery, DAG scheduling | Java concurrency (executors, `CompletableFuture`, virtual threads), Redis reliable queues (`BLMOVE`/Streams) and pub/sub, Docker internals, Linux processes/signals, multi-module Maven, multi-service Compose and AWS deploy |
| 4 | [**FlagForge**](./18-projects/flagforge/README.md) — feature-flag & progressive-rollout platform + Java SDK | 20–23 | 100–120 | Low-latency evaluation, deterministic bucketing, priority rule engine, immutable versioned configs with rollback, Redis snapshot caching, SDK/client-library design, propagation via pub/sub → SSE, stampede protection | Caching strategy and measured p99 latency, API design for SDK consumers, publishing a Maven artifact, resilience (timeouts, stale-if-error, offline defaults), system design vocabulary backed by three earlier systems |

Foundation weeks 1–3 produce throwaway exercises only (not portfolio). Polish weeks 24–26
touch all four projects: bugs, refactors, tests, security review, load tests, docs, diagrams,
demos, issues/milestones and résumé bullets from measured results.

---

## 3. Technology × project matrix

`●` = core to the project's identity · `○` = used, but reused from an earlier project · `—` = not used

| Technology | FlowGrid | LedgerX | ForgeCI | FlagForge | First taught (week) |
|---|:-:|:-:|:-:|:-:|:-:|
| Java 21 | ● | ● | ● | ● | 1–3 |
| Spring Boot 3 (web, data, security) | ● | ○ | ○ (api + worker apps) | ○ | 3–4 |
| PostgreSQL 16 (schema, indexes, transactions) | ● | ● (constraints, triggers, isolation) | ○ (builds/jobs/logs) | ○ (versions, audit) | 3–6, 9 |
| Redis 7 | ● cache-aside, low-stock cache | ○ idempotency cache / rate limit | ● queue, pub/sub, limits | ● snapshot cache, pub/sub | 6, 15, 21 |
| REST API design (ProblemDetail, pagination, OpenAPI) | ● | ● (cursor pagination, idempotency) | ○ | ● (SDK-facing API) | 3–4 |
| React + TypeScript (Vite) | ● dashboard | ○ optional admin/history view | ● build list + live log | ● admin dashboard, rules editor | 6–7 |
| Docker / Compose | ● Compose for Postgres, multi-stage Dockerfile | ○ | ● Engine API, temp containers, N-worker Compose | ○ | 4–5, 14 |
| AWS (IAM, EC2, RDS, S3, CloudWatch) | ● first deploy | ○ repeat | ● multi-service (Docker socket on worker EC2; SQS considered) | ○ repeat | 7–8 |
| JUnit 5 / Mockito / Testcontainers | ● N-threads-one-unit test | ● $500/$400/$400 test, invariant suite, fault injection | ● Postgres+Redis integration, chaos-style | ● SDK unit + contract tests | 2, 5, 12, 18 |
| GitHub Actions (CI/CD) | ● `mvn verify` → image → GHCR → deploy | ○ | ● image publishing, multi-service | ○ | 4, 8, 18 |
| GitHub API / OAuth / webhooks | — | — | ● HMAC-SHA256, `X-GitHub-Delivery` dedupe | — | 14 |
| SSE (Server-Sent Events) | — | — | ● live logs, replay-from-sequence | ● streaming updates to SDKs | 16, 22 |
| SDK / client-library design | — | — | — | ● `FlagClient`, polling, local eval, Maven artifact | 22 |
| Load / benchmark tooling (k6, JMH) | ● k6 order-creation baseline | ● throughput measurement | ● queue wait time, jobs/min | ● p99 evaluation latency | 8, 12, 18, 21 |
| Python 3.12 (`tools/`, pytest, type hints) | ● data generator + load/simulation harness | ● independent reconciliation verifier, data generator, consistency checker | ● test-repo generator, webhook/load simulator, log analysis | ● rollout simulator, Python SDK/test client, config linter | 1–3, then W7, W12, W18, W22–23 |

Every résumé technology appears as `●` in at least one project. Nothing is on the résumé
that no project can back up — see [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md).

**Language split (ROADMAP §1, §8):** every backend stays **Java 21 / Spring Boot** — the projects
and the résumé/software-engineering interview track (Track B) are Java. **Python** is the
coding-interview and DSA language (Track A, ≈90% of algorithm practice) *and* the tooling language
inside each project. The two are never mixed in an interview: algorithms in Python, systems in Java.

---

## 3a. Python engineering component per project

Each project ships **at least one tested, documented Python component** that serves a real purpose
inside the project — that is where practical Python experience beyond DSA comes from, and it is the
only Python that may appear on the résumé (see [`17-resume-tech-defense/python.md`](./17-resume-tech-defense/python.md)).

| Project | Python component(s) | Where | Milestone / week | Used by |
|---|---|---|---|---|
| **FlowGrid** | Synthetic inventory & order generator (realistic SKUs, warehouses, order mix; writes via API or SQL); load/simulation harness that drives concurrent order creation and reports latency + reservation contention | `tools/` | M4–M5 · W7–8 | k6/benchmark protocol, failure exercises, seeding the demo |
| **LedgerX** | **Independent** reconciliation verifier (reads Postgres directly with `psycopg` + `decimal`; proves every journal sums to zero and balance == Σ entries **without trusting the Java code**); transaction-data generator; consistency checker for crash/retry exercises | `tools/` | M4 · W12 | Invariant suite, failure exercises, CP-12 |
| **ForgeCI** | Test-repository generator (creates Git repos with `.forgeci.yml` variants: passing, failing, slow, timeout, bad config); worker/load simulator (fires HMAC-signed webhooks at a rate, measures queue wait & completion); build-result/log analysis tooling (parses persisted logs/results, reports failure-taxonomy stats) | `tools/` | M5 · W18 | Integration tests, queue benchmarks, failure suite |
| **FlagForge** | Rollout-distribution simulator (proves `pct ± tolerance` and stickiness over N users, compares against server evaluation); minimal **Python SDK / test client** implementing the same evaluation contract (polling + ETag, defaults, offline) for cross-SDK contract tests; configuration validation tool (lints rules for overlaps / unreachable / invalid) | `tools/` + `sdk-python/` | M3–M4 · W22–23 | SDK contract tests, p99/bucketing benchmarks, dashboard validation |

**Rules for every Python component**
- It has its own `README.md`, `pytest` tests, type hints, and a `pyproject.toml` (or `requirements.txt`); `ruff`/`mypy` optional but encouraged.
- It runs in CI (a `python` job alongside `mvn verify`) or its README says exactly why it does not.
- It is listed in the project's `TESTING.md` or `PERFORMANCE.md` at the place it is used.
- **Only add tooling that is actually used** by a test, a benchmark or a failure exercise. A script nobody runs is clutter, not evidence.
- It never replaces Java where Java is the point: the backend, the SDK's primary implementation (Java), the concurrency tests inside the Spring app stay Java.
- The Python component is part of the [definition of done](#8-definition-of-done) for the STRONG RESUME VERSION.

---

## 4. The compounding plan

Each project **reuses** what earlier ones taught and **spends its hours** on something new.
This is why order matters and why nothing is built in parallel.

| Project | Reuses (already learned — don't re-learn, just apply faster) | Newly teaches (where the hours go) |
|---|---|---|
| **FlowGrid** | Foundation Java, JUnit, Maven, Git, SQL basics, the Week-3 Spring Boot intro; Python core + `pytest` + scripting from Weeks 1–3 | Everything about a production Spring Boot service: JPA + Flyway, validation and ProblemDetail errors, JWT auth and roles, pagination/filtering, OpenAPI, transactions and `FOR UPDATE` vs `@Version`, idempotency keys, Redis cache-aside, state machines, deterministic allocation, React/TS dashboard, Docker, CI, first AWS deploy, first k6 run; Python: first real tool (data generator + load/simulation harness) |
| **LedgerX** | Controllers, DTOs, validation, error handling, security, JPA mappings, Testcontainers, Docker, CI, AWS deploy — all from FlowGrid. **LedgerX does not re-teach controllers.** Python scripting/pytest habits from FlowGrid's generator and harness. | Correctness under concurrency: double-entry model, DB-enforced immutability (triggers / revoked privileges, CHECK constraints), balances derived from entries + materialized balance with invariant check, idempotency store with request fingerprint and conflict detection, ordered pessimistic locking vs optimistic, isolation-level experiments, compensating transactions, cursor pagination, audit log, transactional outbox, reconciliation job, crash-between-steps fault injection, retry-after-crash tests, invariant suite; Python: an **independent verifier** that reads the database directly (`psycopg`, `decimal`) |
| **ForgeCI** | Spring Boot, Postgres, auth, testing, Docker/Compose, CI/CD, AWS — all routine by now. Idempotency (from FlowGrid M2 / LedgerX M2) is reapplied to webhook delivery IDs. State machines reapplied to builds/jobs. Python load-harness patterns from FlowGrid. | **Queues, workers, containers and recovery**: reliable Redis queue (`BLMOVE` with per-worker processing list + lease, or Streams consumer groups — chosen and justified), separate worker app in a multi-module Maven repo, Docker Engine API execution with guaranteed cleanup, log chunk pipeline → Redis pub/sub → SSE with replay, per-job timeouts, cancellation of queued and running jobs, app-vs-infra failure retry policy with backoff, heartbeats, lease-expiry orphan recovery, per-project concurrency limits, graceful shutdown, DAG scheduling with topological sort, Docker-socket security; Python: test-repo generator, signed-webhook load simulator, log/result analysis |
| **FlagForge** | Everything above. Versioned/immutable records (from LedgerX's append-only thinking). Pub/sub + SSE (from ForgeCI's live logs). Multi-tenant auth (from FlowGrid roles). Python HTTP-client and simulation patterns from all three. | **Caching, latency, SDK and propagation**: immutable config versions with rollback-as-new-version, priority rule engine, deterministic percentage bucketing (`hash(flagKey:userKey) mod 10000`), Redis environment snapshots with invalidate-on-publish, measured p99 evaluation latency, `FlagClient` SDK (builder, polling, local snapshot, local evaluation, defaults, timeouts, stale-if-error, offline mode), contract tests against the server, Maven artifact publishing, publish → pub/sub → SSE propagation, stampede protection; Python: rollout-distribution simulator, a second (Python) SDK/test client for cross-SDK contract tests, config linter |

**Practical consequence:** in LedgerX Week 9 you should scaffold entities, controllers, auth and CI
in a day or two, not a week. If that scaffolding still takes a week, that is a FlowGrid remediation
signal, not a LedgerX problem.

---

## 5. Scope tiers and the scope-control rule

Every project has four tiers. The tier names are fixed across all specs, milestones and trackers.

| Tier | Meaning | When | Tag |
|---|---|---|---|
| **MVP** | Smallest end-to-end version that exercises the project's *core engineering problem* (the concurrency test, the ledger invariant, queue → container → result, evaluation + cache) | Reached mid-phase | `v0.x` |
| **STRONG RESUME VERSION** | **The primary target.** Core architecture, engineering depth, tests, deployment, documentation. This is what goes on the résumé. | Phase's last week | `v1.0` |
| **ADVANCED** | One or two hard features that deepen the interview story (split fulfillment / capacity-aware allocation; scheduled payments + risk rules; DAG pipelines; segments / scheduled rollouts / TS SDK) | Only after `v1.0` is tagged; ForgeCI's M6 is scheduled, others land in polish weeks | `v1.x` |
| **OPTIONAL STRETCH** | Ideas for later (carrier rate stub, EDI import, experimentation hooks) — never block completion | Polish weeks or after Week 26 | — |

### The scope-control rule

When the schedule slips — and it will — preserve, **in this order**:

1. **Core architecture** — the layering, the data model, the state machines, the queue design.
2. **Engineering depth** — the concurrency test, the invariants, the recovery path, the measured latency.
3. **Testing** — unit, slice, integration (Testcontainers), concurrency, failure exercises.
4. **Deployment** — a reachable URL (or a documented, reproducible teardown).
5. **Documentation** — README, ARCHITECTURE, and the docs that are meaningful for the project.

…and only **then** features. Cut features, never quality. If a phase is more than one week behind
at its checkpoint, drop its **ADVANCED** tier, never its **STRONG RESUME VERSION** (ROADMAP §13).

A feature that does not survive this ordering goes into the repo's issues with a `stretch` label —
it is a visible, honest backlog, not a silent omission.

---

## 6. Timeline and milestones

One milestone ≈ one week ≈ one GitHub milestone with issues ≈ one or more PRs ≈ one tag.
Detailed requirements per milestone live in each project's `milestones.md`.

### FlowGrid — Weeks 4–8 (125–150 h) · [milestones](./18-projects/flowgrid/milestones.md)

| Milestone | Week | Delivers | Tier reached |
|---|:-:|---|---|
| M1 Domain, persistence, auth | 4 | Spring Boot + Flyway + Postgres (Compose); warehouse/product/SKU/inventory-level/adjustment/audit/user entities; CRUD + validation + ProblemDetail; pagination/sort/filter; JWT + roles; OpenAPI; CI `mvn verify` | MVP core |
| M2 Idempotent orders, concurrent reservations | 5 | `Idempotency-Key` store (key + request hash + response, TTL); reservation state machine; cancellation releases stock; `SELECT … FOR UPDATE` vs `@Version`; N-threads-one-unit test on Testcontainers | **MVP** |
| M3 Allocation, fulfillment, Redis | 6 | Deterministic multi-warehouse allocation; pick lists; picking → packing → shipment; partial fulfillment; Redis cache-aside catalog + invalidation; low-stock cache; Redis-down → degrade to DB | — |
| M4 Dashboard, returns, transfers | 7 | React + TS (Vite) dashboard; search/filter/sort/pagination; returns (restock / quarantine); two-phase stock transfers (`in_transit`); scheduled low-stock alerts | — |
| M5 Deploy, document, measure | 8 | EC2 + Compose, RDS, Redis (EC2 or ElastiCache), S3 exports, IAM least privilege, CloudWatch + alarm; CI/CD (test → image → GHCR → deploy); k6 baseline; docs set; failure exercises | **STRONG RESUME VERSION v1.0** |
| ADVANCED (polish) | 24–26 | Split fulfillment across warehouses; capacity-aware allocation; distributed lock only if justified | ADVANCED |

### LedgerX — Weeks 9–13 (125–150 h) · [milestones](./18-projects/ledgerx/milestones.md)

| Milestone | Week | Delivers | Tier reached |
|---|:-:|---|---|
| M1 Ledger core | 9 | Double-entry model (account, ledger_entry, journal_txn); DB-enforced immutability; CHECK constraints; derived + materialized balances with invariant check; deposits/withdrawals; auth | — |
| M2 Idempotent concurrent transfers | 10 | Idempotency store (fingerprint, status, response; conflict on same key / different body); ordered pessimistic locking vs optimistic; isolation experiments; $500 / two × $400 → exactly one succeeds, never negative | **MVP** |
| M3 States, reversals, history, audit, outbox | 11 | Txn state machine; refunds/reversals as compensating journals linking original; payment requests; cursor-paginated history; audit log; transactional outbox | — |
| M4 Reconciliation, failure injection, invariants | 12 | Reconciliation job (sum-to-zero, balance == Σ entries, drift flags); crash-between-steps tests via fault-injection hook; retry-after-crash idempotency tests; invariant suite; throughput measurement | — |
| M5 Deploy, docs, advanced | 13 | AWS deploy; docs set; then scheduled payments + basic risk rules (velocity limit, max amount) | **STRONG RESUME VERSION v1.0** after deploy/docs; risk rules = ADVANCED |

### ForgeCI — Weeks 14–19 (170–200 h; hardest) · [milestones](./18-projects/forgeci/milestones.md)

| Milestone | Week | Delivers | Tier reached |
|---|:-:|---|---|
| M1 GitHub integration, idempotent webhooks | 14 | Repo registration; GitHub OAuth (PAT acceptable first); HMAC-SHA256 verification; dedupe on `X-GitHub-Delivery` (unique constraint); build/job/step records; minimal `.forgeci.yml` parser (command list, no DAG) | — |
| M2 Queue, workers, Docker execution | 15 | Reliable Redis queue (`BLMOVE` + lease or Streams — justified); separate worker app; temp container per job (image, workspace volume, clone, steps, exit codes); cleanup always | **MVP** |
| M3 Live logs and UI | 16 | Log chunks persisted in Postgres + published via Redis pub/sub; SSE endpoint with replay-from-sequence; React build list/detail with live log and status badges | — |
| M4 Timeouts, cancellation, retries, limits, health | 17 | Per-job timeout (kill container); cancel queued and running; app-vs-infra retry policy with backoff and max N; heartbeats; lease-expiry orphan recovery; per-project concurrency limit; graceful shutdown | — |
| M5 Failure recovery, tests, full stack, benchmarks | 18 | Failure-engineering suite; Testcontainers (Postgres + Redis) integration + concurrency tests; Compose (api, N workers, postgres, redis, ui); CI/CD; queue wait time and jobs/min measured | **STRONG RESUME VERSION v1.0** |
| M6 DAG pipelines, AWS | 19 | `needs:` dependencies, topological scheduling, fan-out/fan-in, fail-fast; AWS deploy (worker EC2 with Docker socket — security notes; SQS considered vs Redis) | **ADVANCED** |

### FlagForge — Weeks 20–23 (100–120 h) · [milestones](./18-projects/flagforge/milestones.md)

| Milestone | Week | Delivers | Tier reached |
|---|:-:|---|---|
| M1 Model, versioning, audit | 20 | Orgs / projects / environments / flags; per-environment configs as immutable versions (rollback = new version); rules (attribute, user, percentage, priority, default); audit history; org roles; admin API; SDK keys per environment | — |
| M2 Evaluation engine, cache | 21 | Deterministic evaluation (priority → first match; bucket = `hash(flagKey:userKey) mod 10000`); server-side eval endpoint; Redis environment snapshot with invalidate-on-publish; p99 evaluation latency measured | **MVP** |
| M3 Java SDK | 22 | `FlagClient` (builder, sdkKey, polling interval, local snapshot, local evaluation, defaults, timeouts, stale-if-error, offline mode); sample app; SDK unit + contract tests; then SSE streaming updates | — (SSE = advanced within M3) |
| M4 Dashboard, propagation, deploy | 23 | React admin dashboard (flags, rules editor, versions, rollback, audit); publish → Redis pub/sub → SSE to SDKs; stampede protection; Docker/AWS/CI; docs; benchmarks; failure exercises | **STRONG RESUME VERSION v1.0** |
| ADVANCED (polish) | 24–26 | Segments; scheduled rollouts; TypeScript SDK. STRETCH: experimentation hooks | ADVANCED |

### Polish — Weeks 24–26 (≈75 h, all four projects)

| Week | Focus |
|:-:|---|
| 24 | Bug-fix sprint, refactors, test gaps, security pass (OWASP API Top 10), README / ARCHITECTURE / API / DATABASE docs |
| 25 | k6 / JMH runs recorded with methodology, PERFORMANCE.md, ERDs, architecture diagrams, screenshots/demos, GitHub issues/milestones cleanup |
| 26 | Résumé bullets from measured results, [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md) decisions, final deploy check, teardown/cost review |

---

## 7. The project development rule

**This repository teaches; the learner builds.** No spec in `18-projects/` contains a complete
implementation. Complete, copy-paste code appears only for very small isolated concepts
(≤ ~25 lines: a token-bucket Lua script, a bucketing hash function, a `SELECT … FOR UPDATE` snippet).
Everything else is yours, from a blank file, so that "I built this" is simply true.

### The milestone loop

Every milestone in every `milestones.md` follows the same eight steps:

| Step | What the spec gives you | What you do |
|---|---|---|
| 1. **Know first** | Links to the topic files needed (`05-spring-boot/07-transactions.md`, `04-sql-databases/redis.md`, …) | Read them *this week*, take notes in your own words |
| 2. **Requirements** | Functional and non-functional requirements, domain rules | Turn them into GitHub issues under the milestone |
| 3. **Architectural guidance** | Layering, data model hints, which pattern and why, what to avoid | Write or update the design doc ([template](./18-projects/templates/design-doc.md)) and any ADRs ([template](./18-projects/templates/adr.md)) |
| 4. **Acceptance criteria** | Concrete, checkable statements ("two concurrent $400 transfers against $500: exactly one succeeds") | Copy into the milestone; they become the PR checklist |
| 5. **Now implement it** | — | Branch per issue, small commits, PR per issue or feature |
| 6. **Verification** | Described test cases (occasionally a short JUnit skeleton) | Write the tests; they must pass in CI, not just locally |
| 7. **Debugging scenarios** | "Remove `@Transactional` and run the test", "kill Redis mid-request", "deliver the webhook twice" | Reproduce, observe, diagnose, fix, add a regression test (see §11) |
| 8. **Interview questions** | Questions about what you just built | Answer out loud, without notes; record weak answers in `interview-questions.md` notes |

Then tag the milestone (`v0.2`, `v0.3`, … `v1.0`), update
[`trackers/project-tracker.md`](./trackers/project-tracker.md), and start the next one.
If a milestone slips, **finish it before starting the next**.

---

## 8. Definition of done

A project is **done** (STRONG RESUME VERSION, `v1.0`) only when every box is ticked. Copy this
list into each repo's final milestone issue.

**Core**
- [ ] All milestones through the STRONG RESUME VERSION are merged to `main` and tagged `v1.0`
- [ ] The core engineering problem has an automated test that proves it (concurrency test, invariant suite, recovery test, latency measurement)
- [ ] The state machines in the spec are enforced in code and covered by tests for illegal transitions
- [ ] No known data-corrupting bug is open; every open bug is a GitHub issue with a label

**Testing**
- [ ] Unit tests for domain logic; slice tests (`@WebMvcTest`, `@DataJpaTest`) where useful; integration tests on Testcontainers
- [ ] `mvn verify` is green in GitHub Actions on `main`; the badge is in the README
- [ ] Failure-engineering exercises from `failure-engineering.md` are completed and each has a regression test or a documented design-around
- [ ] The project's **Python component** (§3a) exists in `tools/` (and `sdk-python/` for FlagForge), has `pytest` tests passing, type hints, a README, and is referenced from TESTING.md or PERFORMANCE.md where it is used

**Deployment**
- [ ] Deployed to AWS at least once with a documented, reproducible procedure (DEPLOYMENT.md), including IAM least privilege and CloudWatch logs
- [ ] Docker Compose brings up the whole stack locally with one command; `.env.example` lists every variable
- [ ] Teardown procedure and cost review recorded (never leave an EC2/RDS instance running by accident — see [`12-aws/cost-safety.md`](./12-aws/cost-safety.md))

**Documentation**
- [ ] README passes the [README first-impression checklist](./PROJECT_SELECTION.md#8-readme-first-impression-checklist)
- [ ] ARCHITECTURE.md with a diagram; DATABASE.md with an ERD; OpenAPI spec committed or generated at a documented URL
- [ ] DESIGN_DECISIONS.md (ADRs) records the trade-offs the interview will ask about
- [ ] PERFORMANCE.md exists **only if** there is a measured result following §10 — otherwise it does not exist

**Explainability**
- [ ] Deep-dive rehearsal done: 10–15 minutes, no notes, recorded, scored against [`16-interview-prep/project-deep-dive.md`](./16-interview-prep/project-deep-dive.md)
- [ ] `interview-questions.md` bank drilled at least once end to end
- [ ] Résumé bullets drafted in `docs-and-resume.md` with `<placeholder>` where a number is not yet measured

---

## 9. GitHub quality standard

Each project is its **own public repository**. The repo — not this roadmap — is what a recruiter
or interviewer will open. Standard for every project:

### Documents

| Document | Purpose | Required? |
|---|---|---|
| `README.md` | What it is, why, stack, architecture diagram, quick start, screenshots/demo, status, links to the rest | **Always** |
| `docs/ARCHITECTURE.md` | Components, request/job flow, key patterns, diagram(s), why this shape | **Always** |
| `docs/API.md` | Endpoint overview, auth, error format (ProblemDetail), idempotency, pagination; link to OpenAPI | Always for API projects (all four) |
| `docs/DATABASE.md` | ERD, tables, constraints, indexes and why, migrations policy | **Always** |
| `docs/TESTING.md` | Test pyramid for this repo, how to run each layer, what the concurrency/invariant/recovery tests prove | **Always** |
| `docs/DEPLOYMENT.md` | Local Compose, AWS topology, IAM, secrets handling, CI/CD pipeline, teardown | **Always** |
| `docs/SECURITY.md` | Auth model, roles, secrets, input validation, threat notes (e.g. Docker socket in ForgeCI, SDK keys in FlagForge) | **Always** (short is fine) |
| `docs/DESIGN_DECISIONS.md` | Index of ADRs ([template](./18-projects/templates/adr.md)) | **Always** |
| `docs/PERFORMANCE.md` | Benchmark reports ([template](./18-projects/templates/benchmark-report.md)) | **Only if meaningful** — only with a real measured result. An empty or speculative PERFORMANCE.md is worse than none. |
| `tools/README.md` (and `sdk-python/README.md`) | What each Python tool does, how to install/run it (`python -m …`), how to run its `pytest` suite, which test/benchmark/exercise uses it | **Always** (§3a) |

**"Only if meaningful" rule:** every document must say something that is true about *this* repo.
A generic SECURITY.md copied between projects, or a PERFORMANCE.md with "should handle thousands
of requests", is a red flag to an experienced reader. Shorter and true beats longer and vague.

### Repository contents

- [ ] Architecture diagram (Mermaid in Markdown is fine; export PNG for README if needed)
- [ ] ERD (Mermaid `erDiagram` or an exported image) in DATABASE.md
- [ ] OpenAPI spec (springdoc-generated; commit a snapshot at `docs/openapi.yaml` or document the `/v3/api-docs` URL)
- [ ] `Dockerfile` (multi-stage) and `compose.yaml` that run the full stack
- [ ] Tests visible and runnable: `mvn verify` (backend), `npm test` (frontend where present), `pytest` (Python tools)
- [ ] GitHub Actions workflow(s) in `.github/workflows/`; green badge on README
- [ ] `.env.example` with every environment variable, no real secrets ever committed
- [ ] Screenshots and/or a short demo GIF/video in README (dashboard, live logs, rules editor)
- [ ] Clean commit history: imperative messages, small commits, no `wip`/`fix fix` on `main`; squash-merge PRs if needed
- [ ] GitHub milestones (M1…M5/M6) with issues; closed issues stay as history; `stretch` label for cut scope
- [ ] Tags `v0.x` per milestone, `v1.0` for the STRONG RESUME VERSION, releases with short notes
- [ ] LICENSE (MIT is fine) and a one-line "built as part of a structured 26-week rebuild" note in README — honest about context

---

## 10. Performance and benchmarking rule

**No invented metrics. Ever.** A number appears in a README, PERFORMANCE.md, résumé bullet or
interview answer only if it was measured and the measurement is recorded well enough that someone
else could repeat it.

Every recorded benchmark uses the [benchmark report template](./18-projects/templates/benchmark-report.md) and states:

| Field | Examples |
|---|---|
| **Environment** | Laptop model / EC2 instance type, vCPU, RAM, OS, JDK version and flags, Postgres/Redis version, container CPU/memory limits |
| **Setup** | Data volume (rows per table), config (pool sizes, cache TTLs), whether services were co-located |
| **Load** | Tool (k6, JMH, `ab`), virtual users, duration, ramp profile, request mix |
| **Methodology** | Warm-up period, number of runs, which run is reported (median of N), percentiles used |
| **Result** | Throughput, p50/p95/p99, error rate — as a table, with the raw output linked |
| **Interpretation** | What it means, what it does not mean, what limited it, what you would try next |

Until a number is measured, the résumé bullet says `<p99 latency>` or "verified by
`ReservationConcurrencyTest`" — see the bullet rules in [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md#6-bullet-writing-rules).
A laptop benchmark is a legitimate result *as long as it says it is a laptop benchmark*.

---

## 11. Failure engineering as a discipline

Every project has a `failure-engineering.md` with a fixed set of exercises (kill Redis mid-request,
crash between ledger steps, deliver a webhook twice, lose a worker mid-job, publish a flag while
1,000 SDKs are polling…). Each exercise follows the same protocol:

```
reproduce → observe → inspect logs → diagnose → fix (or design around) → regression test
```

1. **Reproduce** deterministically — a test, a script, `docker kill`, a fault-injection hook, a `Thread.sleep` you will remove.
2. **Observe** the *user-visible* outcome first: wrong balance? duplicate order? hung request? silent loss?
3. **Inspect logs** (structured logs, `docker logs`, Postgres logs, Redis `MONITOR`) and state (`psql`, `redis-cli`).
4. **Diagnose** the root cause in one sentence — not the symptom.
5. **Fix or design around** — sometimes the fix is a code change, sometimes it is "degrade to DB" or "at-least-once + idempotent".
6. **Regression test** — the exercise ends with a test that would fail if the bug came back, or a documented reason it cannot be automated.

Record each exercise in the repo (a `docs/failure-log.md` or an issue per exercise) and count them in
[`trackers/project-tracker.md`](./trackers/project-tracker.md). In interviews, "here is a failure I
caused on purpose, what I saw, and what I changed" is the strongest story a junior candidate can tell.

---

## 12. Links

### Project specs (5 files each)

| Project | Spec | Milestones | Failure engineering | Interview questions | Docs & résumé |
|---|---|---|---|---|---|
| FlowGrid | [README](./18-projects/flowgrid/README.md) | [milestones](./18-projects/flowgrid/milestones.md) | [failure-engineering](./18-projects/flowgrid/failure-engineering.md) | [interview-questions](./18-projects/flowgrid/interview-questions.md) | [docs-and-resume](./18-projects/flowgrid/docs-and-resume.md) |
| LedgerX | [README](./18-projects/ledgerx/README.md) | [milestones](./18-projects/ledgerx/milestones.md) | [failure-engineering](./18-projects/ledgerx/failure-engineering.md) | [interview-questions](./18-projects/ledgerx/interview-questions.md) | [docs-and-resume](./18-projects/ledgerx/docs-and-resume.md) |
| ForgeCI | [README](./18-projects/forgeci/README.md) | [milestones](./18-projects/forgeci/milestones.md) | [failure-engineering](./18-projects/forgeci/failure-engineering.md) | [interview-questions](./18-projects/forgeci/interview-questions.md) | [docs-and-resume](./18-projects/forgeci/docs-and-resume.md) |
| FlagForge | [README](./18-projects/flagforge/README.md) | [milestones](./18-projects/flagforge/milestones.md) | [failure-engineering](./18-projects/flagforge/failure-engineering.md) | [interview-questions](./18-projects/flagforge/interview-questions.md) | [docs-and-resume](./18-projects/flagforge/docs-and-resume.md) |

### Process and templates

- [`18-projects/README.md`](./18-projects/README.md) — how to run any project through this roadmap
- [`18-projects/templates/design-doc.md`](./18-projects/templates/design-doc.md) · [`adr.md`](./18-projects/templates/adr.md) · [`benchmark-report.md`](./18-projects/templates/benchmark-report.md)
- [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md) — which project to feature for which role; résumé and bullet rules
- [`trackers/project-tracker.md`](./trackers/project-tracker.md) — milestone, tier, docs, deployment, benchmark and failure-exercise status
- [`16-interview-prep/project-deep-dive.md`](./16-interview-prep/project-deep-dive.md) — the rehearsal after each project
- [`checkpoints/`](./checkpoints/README.md) — CP-8, CP-12, CP-16/20, CP-24 review each project
