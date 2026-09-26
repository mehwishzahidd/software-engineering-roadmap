# 🏗️ Project Tracker

Tracks the four flagship projects milestone by milestone: status, PRs, tags, scope tier, docs,
deployment, benchmarks, failure exercises, Python component and interview readiness.
Update every Sunday review and at every tag. Order and weeks come from [`ROADMAP.md`](../ROADMAP.md).

[← README](../README.md) · [Projects overview](../PROJECTS.md) · [Project selection](../PROJECT_SELECTION.md) · [How to run a project](../18-projects/README.md) · [Weekly progress](./weekly-progress.md)

**Status legend:** ⬜ not started · 🟡 in progress · ✅ done (merged + tagged) · ⏸️ deferred to polish weeks · ❌ cut (issue labelled `stretch`)

---

## 1. FlowGrid — multi-warehouse fulfillment & inventory platform (Weeks 4–8)

Spec: [README](../18-projects/flowgrid/README.md) · [milestones](../18-projects/flowgrid/milestones.md) · [failure-engineering](../18-projects/flowgrid/failure-engineering.md) · [interview-questions](../18-projects/flowgrid/interview-questions.md) · [docs-and-resume](../18-projects/flowgrid/docs-and-resume.md)
Repo: `<github.com/<you>/flowgrid>` · Live URL: `<…>` · Hours target 125–150 · Hours logged: `<…>`

### Milestones

| Milestone | Week | Status | PR link(s) | Tag | Notes |
|---|:-:|:-:|---|---|---|
| M1 Domain, persistence, auth | 4 | ⬜ | | `v0.1` | |
| M2 Idempotent orders + concurrent reservations (**MVP**) | 5 | ⬜ | | `v0.2` | N-threads-one-unit test: |
| M3 Allocation + fulfillment workflow + Redis | 6 | ⬜ | | `v0.3` | |
| M4 Operations dashboard + returns/transfers | 7 | ⬜ | | `v0.4` | Python generator/harness starts |
| M5 Deploy, document, measure (**STRONG RESUME VERSION**) | 8 | ⬜ | | `v1.0` | |
| ADVANCED: split fulfillment / capacity-aware allocation | 24–26 | ⬜ | | `v1.1` | Only after v1.0 |

### Scope tier

| Tier | Status | Date reached |
|---|:-:|---|
| MVP | ⬜ | |
| Strong Résumé Version (`v1.0`) | ⬜ | |
| Advanced | ⬜ | |
| Stretch (carrier rate stub, EDI-style import) | ⬜ | never blocks |

### Python component (`tools/`)

| Item | Status | Notes |
|---|:-:|---|
| Synthetic inventory & order generator (SKUs, warehouses, order mix; API or SQL) | ⬜ | |
| Load/simulation harness (concurrent order creation; latency + reservation contention report) | ⬜ | |
| `pytest` suite passing, type hints, `pyproject.toml` | ⬜ | |
| `tools/README.md` (purpose, run, test, used-by) | ⬜ | |
| Runs in CI / referenced from TESTING.md or PERFORMANCE.md | ⬜ | |

### Docs checklist

- [ ] README (first-impression checklist passed)
- [ ] ARCHITECTURE.md + diagram
- [ ] API.md + OpenAPI
- [ ] DATABASE.md + ERD
- [ ] TESTING.md
- [ ] DEPLOYMENT.md
- [ ] SECURITY.md
- [ ] DESIGN_DECISIONS.md (ADRs: locking strategy, idempotency storage, allocation scoring, cache degrade, EC2+Compose)
- [ ] PERFORMANCE.md (only with a real report)
- [ ] failure-log.md
- [ ] Screenshots / demo GIF of the dashboard

### Deployment

| Item | Value |
|---|---|
| AWS topology | `<EC2 + Compose, RDS Postgres, Redis on EC2 / ElastiCache, S3, CloudWatch>` |
| Deployed URL | `<…>` |
| First deployed | `<date>` |
| CI/CD pipeline green (test → image → GHCR → deploy) | ⬜ |
| Teardown date / monthly cost observed | `<date> / <$>` |

### Benchmarks recorded

| Report | Date | Link | Headline (only if Final) |
|---|---|---|---|
| Order-creation latency/throughput (k6) | | `docs/perf/…` | |
| Reservation contention (Python harness) | | | |

### Failure exercises, rehearsal, interview bank

| Item | Status |
|---|---|
| Failure exercises completed | `<n>` / `<total from failure-engineering.md>` |
| Deep-dive rehearsal #1 (W8) | ⬜ score: |
| Deep-dive rehearsal #2 (W24–26) | ⬜ score: |
| Interview bank drilled end to end | ⬜ date: |
| Résumé bullets drafted (placeholders allowed) | ⬜ |

---

## 2. LedgerX — digital wallet & double-entry ledger engine (Weeks 9–13)

Spec: [README](../18-projects/ledgerx/README.md) · [milestones](../18-projects/ledgerx/milestones.md) · [failure-engineering](../18-projects/ledgerx/failure-engineering.md) · [interview-questions](../18-projects/ledgerx/interview-questions.md) · [docs-and-resume](../18-projects/ledgerx/docs-and-resume.md)
Repo: `<github.com/<you>/ledgerx>` · Live URL: `<…>` · Hours target 125–150 · Hours logged: `<…>`

### Milestones

| Milestone | Week | Status | PR link(s) | Tag | Notes |
|---|:-:|:-:|---|---|---|
| M1 Ledger core (double-entry, immutability, balances, auth) | 9 | ⬜ | | `v0.1` | |
| M2 Idempotent concurrent transfers (**MVP**) | 10 | ⬜ | | `v0.2` | $500 / 2×$400 test: |
| M3 States, reversals, history, audit, outbox | 11 | ⬜ | | `v0.3` | |
| M4 Reconciliation + failure injection + invariants | 12 | ⬜ | | `v0.4` | Python verifier here |
| M5 Deploy + docs (**STRONG RESUME VERSION**) → scheduled payments + risk rules (ADVANCED) | 13 | ⬜ | | `v1.0` / `v1.1` | |

### Scope tier

| Tier | Status | Date reached |
|---|:-:|---|
| MVP | ⬜ | |
| Strong Résumé Version (`v1.0`) | ⬜ | |
| Advanced (scheduled payments, velocity limit, max amount) | ⬜ | |
| Stretch | ⬜ | never blocks |

### Python component (`tools/`)

| Item | Status | Notes |
|---|:-:|---|
| Independent reconciliation verifier (`psycopg`, `decimal`; journals sum to zero; balance == Σ entries; no trust in Java code) | ⬜ | |
| Transaction-data generator | ⬜ | |
| Consistency checker used in crash/retry failure exercises | ⬜ | |
| `pytest` suite passing, type hints, `pyproject.toml` | ⬜ | |
| `tools/README.md` (purpose, run, test, used-by) | ⬜ | |
| Runs in CI / referenced from TESTING.md or PERFORMANCE.md | ⬜ | |

### Docs checklist

- [ ] README
- [ ] ARCHITECTURE.md + diagram
- [ ] API.md + OpenAPI
- [ ] DATABASE.md + ERD (incl. immutability trigger / privileges, CHECK constraints)
- [ ] TESTING.md (invariant suite, fault-injection hook, verifier)
- [ ] DEPLOYMENT.md
- [ ] SECURITY.md
- [ ] DESIGN_DECISIONS.md (ADRs: append-only enforcement, derived vs materialized balance, lock ordering vs optimistic, isolation level, outbox, compensating entries)
- [ ] PERFORMANCE.md (only with a real report)
- [ ] failure-log.md
- [ ] Screenshots (admin/history view if built)

### Deployment

| Item | Value |
|---|---|
| AWS topology | `<…>` |
| Deployed URL | `<…>` |
| First deployed | `<date>` |
| CI/CD pipeline green | ⬜ |
| Teardown date / monthly cost observed | `<date> / <$>` |

### Benchmarks recorded

| Report | Date | Link | Headline (only if Final) |
|---|---|---|---|
| Transfer throughput / latency | | | |
| Pessimistic vs optimistic comparison | | | |

### Failure exercises, rehearsal, interview bank

| Item | Status |
|---|---|
| Failure exercises completed | `<n>` / `<total>` |
| Deep-dive rehearsal #1 (W13) | ⬜ score: |
| Deep-dive rehearsal #2 (W24–26) | ⬜ score: |
| Interview bank drilled end to end | ⬜ date: |
| Résumé bullets drafted | ⬜ |

---

## 3. ForgeCI — distributed CI/CD execution platform (Weeks 14–19)

Spec: [README](../18-projects/forgeci/README.md) · [milestones](../18-projects/forgeci/milestones.md) · [failure-engineering](../18-projects/forgeci/failure-engineering.md) · [interview-questions](../18-projects/forgeci/interview-questions.md) · [docs-and-resume](../18-projects/forgeci/docs-and-resume.md)
Repo: `<github.com/<you>/forgeci>` · Live URL: `<…>` · Hours target 170–200 · Hours logged: `<…>`

### Milestones

| Milestone | Week | Status | PR link(s) | Tag | Notes |
|---|:-:|:-:|---|---|---|
| M1 GitHub integration + idempotent webhooks | 14 | ⬜ | | `v0.1` | |
| M2 Queue + workers + Docker execution (**MVP**) | 15 | ⬜ | | `v0.2` | Queue choice ADR: |
| M3 Live logs + UI (SSE) | 16 | ⬜ | | `v0.3` | |
| M4 Timeouts, cancellation, retries, limits, health | 17 | ⬜ | | `v0.4` | |
| M5 Failure recovery + tests + full stack + benchmarks (**STRONG RESUME VERSION**) | 18 | ⬜ | | `v1.0` | Python tools here |
| M6 DAG pipelines + AWS (**ADVANCED**) | 19 | ⬜ | | `v1.1` | |

### Scope tier

| Tier | Status | Date reached |
|---|:-:|---|
| MVP | ⬜ | |
| Strong Résumé Version (`v1.0`) | ⬜ | |
| Advanced (DAG, AWS multi-service) | ⬜ | |
| Stretch | ⬜ | never blocks |

### Python component (`tools/`)

| Item | Status | Notes |
|---|:-:|---|
| Test-repository generator (`.forgeci.yml` variants: passing, failing, slow, timeout, bad config) | ⬜ | |
| Worker/load simulator (HMAC-signed webhooks at rate; queue wait + completion measured) | ⬜ | |
| Build-result / log analysis tooling (failure-taxonomy stats) | ⬜ | |
| `pytest` suite passing, type hints, `pyproject.toml` | ⬜ | |
| `tools/README.md` (purpose, run, test, used-by) | ⬜ | |
| Runs in CI / referenced from TESTING.md or PERFORMANCE.md | ⬜ | |

### Docs checklist

- [ ] README (architecture diagram up top)
- [ ] ARCHITECTURE.md (api / worker / queue / container / SSE flow)
- [ ] API.md + OpenAPI
- [ ] DATABASE.md + ERD
- [ ] TESTING.md (Testcontainers Postgres + Redis, chaos-style tests, Python tools)
- [ ] DEPLOYMENT.md (multi-service Compose; worker EC2 with Docker socket)
- [ ] SECURITY.md (HMAC, Docker-socket exposure, secrets in jobs)
- [ ] DESIGN_DECISIONS.md (ADRs: BLMOVE vs Streams, Engine API vs CLI, SSE vs WebSockets, retry taxonomy, lease parameters, Redis vs SQS)
- [ ] PERFORMANCE.md (only with a real report)
- [ ] failure-log.md
- [ ] Screenshots / GIF of live logs

### Deployment

| Item | Value |
|---|---|
| AWS topology | `<api EC2, worker EC2 (Docker socket), RDS, Redis, S3?>` |
| Deployed URL | `<…>` |
| First deployed | `<date>` |
| CI/CD pipeline green (image publishing, multi-service) | ⬜ |
| Teardown date / monthly cost observed | `<date> / <$>` |

### Benchmarks recorded

| Report | Date | Link | Headline (only if Final) |
|---|---|---|---|
| Queue wait time / jobs per minute vs worker count | | | |
| Orphan-recovery time (lease expiry) | | | |

### Failure exercises, rehearsal, interview bank

| Item | Status |
|---|---|
| Failure exercises completed | `<n>` / `<total>` |
| Deep-dive rehearsal #1 (W19) | ⬜ score: |
| Deep-dive rehearsal #2 (W24–26) | ⬜ score: |
| Interview bank drilled end to end | ⬜ date: |
| Résumé bullets drafted | ⬜ |

---

## 4. FlagForge — feature-flag & progressive-rollout platform + SDK (Weeks 20–23)

Spec: [README](../18-projects/flagforge/README.md) · [milestones](../18-projects/flagforge/milestones.md) · [failure-engineering](../18-projects/flagforge/failure-engineering.md) · [interview-questions](../18-projects/flagforge/interview-questions.md) · [docs-and-resume](../18-projects/flagforge/docs-and-resume.md)
Repo: `<github.com/<you>/flagforge>` · Live URL: `<…>` · SDK artifact: `<coordinates>` · Hours target 100–120 · Hours logged: `<…>`

### Milestones

| Milestone | Week | Status | PR link(s) | Tag | Notes |
|---|:-:|:-:|---|---|---|
| M1 Model + versioning + audit | 20 | ⬜ | | `v0.1` | |
| M2 Evaluation engine + cache (**MVP**) | 21 | ⬜ | | `v0.2` | p99 measured: |
| M3 Java SDK (+ SSE streaming = advanced within M3) | 22 | ⬜ | | `v0.3` | Python SDK/test client starts |
| M4 Dashboard + propagation + deploy (**STRONG RESUME VERSION**) | 23 | ⬜ | | `v1.0` | |
| ADVANCED: segments / scheduled rollouts / TS SDK | 24–26 | ⬜ | | `v1.1` | Only after v1.0 |

### Scope tier

| Tier | Status | Date reached |
|---|:-:|---|
| MVP | ⬜ | |
| Strong Résumé Version (`v1.0`) | ⬜ | |
| Advanced | ⬜ | |
| Stretch (experimentation hooks) | ⬜ | never blocks |

### Python component (`tools/` + `sdk-python/`)

| Item | Status | Notes |
|---|:-:|---|
| Rollout-distribution simulator (pct ± tolerance, stickiness over N users, compared with server evaluation) | ⬜ | |
| Minimal Python SDK / test client (polling + ETag, defaults, offline) for cross-SDK contract tests | ⬜ | |
| Configuration validation tool (overlaps / unreachable / invalid rules) | ⬜ | |
| `pytest` suite passing, type hints, `pyproject.toml` | ⬜ | |
| `tools/README.md` + `sdk-python/README.md` | ⬜ | |
| Runs in CI / referenced from TESTING.md or PERFORMANCE.md | ⬜ | |

### Docs checklist

- [ ] README
- [ ] ARCHITECTURE.md (eval path, cache, propagation)
- [ ] API.md + OpenAPI (admin API + SDK-facing API)
- [ ] DATABASE.md + ERD (versions, rules, audit)
- [ ] TESTING.md (SDK unit + contract tests, Python tools)
- [ ] DEPLOYMENT.md
- [ ] SECURITY.md (SDK keys, org roles)
- [ ] DESIGN_DECISIONS.md (ADRs: immutable versions, bucketing hash, polling vs SSE, local vs server eval, stale-if-error, stampede protection)
- [ ] PERFORMANCE.md (only with a real report)
- [ ] failure-log.md
- [ ] SDK README + sample app; screenshots of rules editor
 
### Deployment

| Item | Value |
|---|---|
| AWS topology | `<…>` |
| Deployed URL | `<…>` |
| SDK published (GitHub Packages / Maven coordinates) | ⬜ |
| First deployed | `<date>` |
| CI/CD pipeline green | ⬜ |
| Teardown date / monthly cost observed | `<date> / <$>` |

### Benchmarks recorded

| Report | Date | Link | Headline (only if Final) |
|---|---|---|---|
| Evaluation p50/p95/p99 at N rps; cache-hit ratio | | | |
| Propagation delay publish → SDK | | | |
| Rollout distribution accuracy (Python simulator) | | | |

### Failure exercises, rehearsal, interview bank

| Item | Status |
|---|---|
| Failure exercises completed | `<n>` / `<total>` |
| Deep-dive rehearsal #1 (W23) | ⬜ score: |
| Deep-dive rehearsal #2 (W24–26) | ⬜ score: |
| Interview bank drilled end to end | ⬜ date: |
| Résumé bullets drafted | ⬜ |

---

## 5. Portfolio summary

Update at every tag and at CP-8 / CP-12 / CP-16 / CP-20 / CP-24.

| Project | Tier reached | Tag | Deployed (URL / torn down) | Docs complete | Python component | Benchmarks (Final) | Failure exercises | Rehearsals | Bullets measured | Lead for role type |
|---|---|---|---|:-:|:-:|:-:|:-:|:-:|:-:|---|
| FlowGrid | ⬜ | | | ⬜ | ⬜ | 0 | 0/`<t>` | 0/2 | ⬜ | General SDE / backend / logistics |
| LedgerX | ⬜ | | | ⬜ | ⬜ | 0 | 0/`<t>` | 0/2 | ⬜ | Backend / fintech / testing |
| ForgeCI | ⬜ | | | ⬜ | ⬜ | 0 | 0/`<t>` | 0/2 | ⬜ | Infra / platform / distributed |
| FlagForge | ⬜ | | | ⬜ | ⬜ | 0 | 0/`<t>` | 0/2 | ⬜ | Dev tools / SDK / platform |

**Current PROJECT_SELECTION decision** (from [`PROJECT_SELECTION.md`](../PROJECT_SELECTION.md)):

| Application wave | Role type targeted | Two-project résumé | Three-project résumé | GitHub pin order |
|---|---|---|---|---|
| `<W8 early>` | | FlowGrid + `<…>` | | |
| `<W13>` | | | | |
| `<W20>` | | | | |
| `<W25–26>` | | | | |

---

## 6. Polish weeks 24–26 checklist

Run through all four projects. Preserve, in order: core architecture → engineering depth → testing → deployment → documentation; features last.

**Week 24 — bugs, refactors, tests, security**
- [ ] Bug-fix sprint: every open `bug` issue closed or explicitly deferred with a reason
- [ ] Refactor pass: dead code, duplicated logic, naming; no behaviour changes without tests
- [ ] Test gaps: each core engineering problem has its proving test; illegal state transitions covered
- [ ] Python tools: `pytest` green in CI for all four; READMEs accurate
- [ ] Security pass per project (OWASP API Top 10): auth on every endpoint, input limits, secrets out of repo/logs, HMAC constant-time compare (ForgeCI), SDK key handling (FlagForge), Docker-socket notes (ForgeCI)
- [ ] README / ARCHITECTURE / API / DATABASE docs true and current for all four
- [ ] CP-24 taken; remediation plan written

**Week 25 — performance, load tests, diagrams, demos**
- [ ] k6 / JMH / Python-harness runs recorded with the benchmark template (Final status) — FlowGrid, LedgerX, ForgeCI, FlagForge
- [ ] PERFORMANCE.md written only where a Final report exists
- [ ] ERDs and architecture diagrams committed (Mermaid or exported)
- [ ] Screenshots / demo GIFs in every README
- [ ] GitHub milestones and issues cleaned; `stretch` backlog visible; releases with notes for every tag
- [ ] Deep-dive rehearsal #2 for all four projects, scored
- [ ] 2 mocks; OA sims; applications at volume

**Week 26 — résumé, decisions, teardown**
- [ ] Résumé bullets written **only** from Final benchmark reports or named tests; no `<placeholder>` left in the sent version
- [ ] Technology list on résumé matches the readiness matrix in [`RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md)
- [ ] PROJECT_SELECTION decisions recorded in §5 for the target role types; GitHub pins ordered
- [ ] Final deploy check: each live URL works or the README says "torn down; see DEPLOYMENT.md"
- [ ] Teardown / cost review: no idle EC2/RDS/ElastiCache; monthly cost recorded
- [ ] All four repos pass the [README first-impression checklist](../PROJECT_SELECTION.md#8-readme-first-impression-checklist)
- [ ] Hand-off to [`16-interview-prep/maintenance-plan.md`](../16-interview-prep/maintenance-plan.md)
