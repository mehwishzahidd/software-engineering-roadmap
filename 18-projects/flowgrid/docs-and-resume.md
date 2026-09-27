# FlowGrid — Documentation, Benchmarking and Résumé Rules

> What "documented" and "measured" mean for FlowGrid `v1.0`, and how real work becomes honest résumé bullets.
> Rules: [`../../ROADMAP.md`](../../ROADMAP.md#1-learning-philosophy) (honesty rule) · [`../../PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) · templates: [`design-doc.md`](../templates/design-doc.md), [`adr.md`](../templates/adr.md), [`benchmark-report.md`](../templates/benchmark-report.md).

Contents: [1 Required docs](#1-required-repository-docs) · [2 Benchmarking protocol](#2-benchmarking-protocol) · [3 Résumé bullets](#3-résumé-bullet-template) · [4 GitHub hygiene](#4-github-hygiene-checklist)

---

## 1. Required repository docs

All under `docs/` except the root `README.md` and `tools/README.md`. "Meaningful content" means an interviewer could read it and ask a hard question; a template with headings and no substance fails the checklist. Each doc ≤ ~2 pages; link rather than repeat.

### README.md (root)
- [ ] One-paragraph description + technical-identity sentence (README spec §1)
- [ ] Badges: CI, coverage, latest tag
- [ ] Architecture image + link to `docs/ARCHITECTURE.md`
- [ ] Feature list by tier (MVP / v1.0 / Advanced) — only what exists, ticked
- [ ] Quick start (Compose, run API, run web, `tools/` seed, env vars, default credentials from env)
- [ ] Live URL or "stopped on <date> to save cost" + screenshots/GIF
- [ ] API summary, Swagger link, `docs/API.md` link
- [ ] Testing summary naming the concurrency test
- [ ] Measured results table (copied from PERFORMANCE.md, with the environment line)
- [ ] 3–5 design-decision highlights linking ADRs
- [ ] Failure-engineering summary linking `docs/FAILURE_EXERCISES.md`
- [ ] Known limitations (honest), licence

### ARCHITECTURE.md
- [ ] Component diagram (spec in README §7.3) with labelled arrows
- [ ] Order-creation sequence diagram with transaction boundaries marked
- [ ] State-machine tables (order, reservation, pick list, transfer, shipment)
- [ ] Package structure and the cross-feature rules (services only, DTOs out)
- [ ] Where Redis fits and the degradation contract
- [ ] Scheduled jobs list with cadence and multi-instance caveat
- [ ] Python `tools/` shown as an external client (not deployed)

### API.md
- [ ] Conventions: auth header, `Idempotency-Key`, `X-Request-Id`, pagination/sort/filter, error shape
- [ ] Endpoint table (README §9.2) kept in sync with the exported `docs/openapi.json`
- [ ] Error catalogue (README §19) — every code the tests produce
- [ ] Three worked examples with real request/response bodies (create order + replay, ship, transfer dispatch/receive)
- [ ] Versioning policy

### DATABASE.md
- [ ] ERD (Mermaid or image) for every table; grouped by feature
- [ ] Inventory quantity state-flow figure
- [ ] Table-by-table notes: purpose, key constraints, indexes and *why each index exists* (which query)
- [ ] Invariants list (`available = on_hand − reserved`, `reserved ≤ on_hand`, …) and where each is enforced (CHECK / unique / service)
- [ ] Locking strategy: which transactions lock which rows, in what order
- [ ] Migration policy (additive, expand/contract, never edit applied)
- [ ] Two `EXPLAIN ANALYZE` plans with commentary

### TESTING.md
- [ ] Pyramid table (README §14.1) with commands per layer (`mvn test`, `mvn verify`, `npm test`, `cd tools && pytest`)
- [ ] Testcontainers setup and how CI runs it
- [ ] The mandatory named tests (README §14.3–14.4 + Python list) each with one line: what it proves
- [ ] How concurrency tests are made deterministic (start gate, real commits, cleanup)
- [ ] Coverage number and what it excludes
- [ ] How to run one failure scenario locally

### DEPLOYMENT.md
- [ ] Topology diagram (EC2/RDS/S3/CloudWatch/SGs)
- [ ] Exact steps you ran (IAM, SG, RDS, EC2 user-data, `.env`, first migration) — commands, not prose
- [ ] Pipeline description (`ci.yml`, `deploy.yml`) and secrets list (names only)
- [ ] Rollback procedure and the migration-failure finding (scenario 8)
- [ ] Health/alarm evidence: screenshots of health from the public URL and the alarm in ALARM state
- [ ] Cost table and teardown script
- [ ] Environment variables reference

### SECURITY.md
- [ ] Auth model (ADR-002), token lifetimes, storage on client, rotation/revocation
- [ ] Role matrix (README §13) and how "own warehouse" is enforced
- [ ] OWASP API Top 10 walkthrough: for each item, "how FlowGrid addresses it" or "known gap"
- [ ] Secrets handling, IAM least privilege (paste the instance-role policy)
- [ ] Input validation, CORS, headers, SQL-injection posture
- [ ] Known gaps (rate limiting, TLS if skipped) — honest

### DESIGN_DECISIONS.md
- [ ] Index of ADRs with status; each ADR in `docs/adr/` using [`../templates/adr.md`](../templates/adr.md): context, options, decision, consequences
- [ ] Minimum set: ADR-001 Flyway + identity keys · ADR-002 JWT + refresh strategy · ADR-003 pessimistic vs optimistic (with numbers) · ADR-004 idempotency store location · ADR-005 what is cached and the invalidation contract · ADR-006 Redis on EC2 vs ElastiCache · ADR-007 single-warehouse allocation in v1.0 · ADR-008 Python for `tools/`
- [ ] "Decisions I would revisit" section

### PERFORMANCE.md
- [ ] Environment block (template §2.5) for every result
- [ ] Results for all protocol scenarios (k6 + harness), three runs, median reported, raw outputs in `load/results/`
- [ ] Redis hit ratio before/after warm-up
- [ ] `EXPLAIN` before/after for the two indexed queries
- [ ] N+1 before/after (scenario 9)
- [ ] Pool-size experiment (scenario 11)
- [ ] Interpretation: bottleneck found, change made, delta measured
- [ ] Explicit "targets vs measured" table — measured column never edited to match targets

### tools/README.md
- [ ] Purpose and scope (generator, harness, contention verifier, report)
- [ ] Install (`pip install -e .[dev]`), Python version, commands with examples
- [ ] Data model the generator produces and its distributions/knobs
- [ ] Report format (Markdown/JSON) and how the contention verdict is computed
- [ ] How to run tests, mypy, ruff; CI job link
- [ ] Limitations (client-side overhead, not a k6 replacement)

### FAILURE_EXERCISES.md and DEMO.md
- [ ] One entry per executed scenario: date, prediction, observation, root cause, change, regression test
- [ ] `DEMO.md`: 2-minute script + screenshot list + GIF

---

## 2. Benchmarking protocol

Goal: numbers that you can defend sentence-by-sentence. Every number carries its environment. Use [`../templates/benchmark-report.md`](../templates/benchmark-report.md) for the write-up.

### 2.1 Tools
- **k6** (`load/*.js`): raw latency/throughput.
- **`flowgrid-tools`** (Python, `tools/`): realistic dataset (`seed`), realistic mixed load (`load`), contention verification (`contention`), report writer (`report`).
- **Redis**: `redis-cli INFO stats` (`keyspace_hits`, `keyspace_misses`) before/after.
- **Postgres**: `pg_stat_statements`, `EXPLAIN (ANALYZE, BUFFERS)`.
- **App**: `/actuator/metrics/hikaricp.connections.*`, `http.server.requests`; `/actuator/info` for git SHA.

### 2.2 Scenarios and what to measure

| # | Scenario | Tool | Load | Measure |
|---|---|---|---|---|
| B1 | Order creation, mixed realistic orders (generator mix, 20 % hot SKUs) | k6 `order-create.js` + harness `load` | 10 / 20 / 50 VUs, 30 s warm-up, 120 s | p50 / p95 / p99 latency, req/s, status histogram, 409 ratio |
| B2 | Reservation contention: one SKU, `units=100`, `competitors=150` all at once | harness `contention` (+ k6 `contention.js` for latency) | single burst; also 3 bursts | successes (must = 100), p95 of winners vs losers, verdict PASS |
| B3 | Catalog read `GET /skus/{id}` cold vs warm | k6 `catalog.js` | 50 VUs, 60 s, `FLUSHDB` before cold run | p95 cold vs warm; Redis hit ratio |
| B4 | Orders list `page=0` vs `page=500` (50k orders seeded via `seed --sql`) | k6 `orders-list.js` | 20 VUs, 60 s | p95 each; `EXPLAIN` of both |
| B5 | Pool-size experiment (pool 5 vs 10) at 60 competitors | harness `contention --competitors 60` | 3 runs each | 503 count, p95, successes |
| B6 | Login (BCrypt cost) | k6 `login.js` | 5 VUs, 30 s | p95 (shows the hashing cost; justify cost factor) |

Run each scenario in **two environments** and label them: `LOCAL` (laptop, Compose Postgres/Redis, API from `mvn spring-boot:run` or the image) and `AWS` (k6/harness from your laptop → EC2 API → RDS; note the client→server network is part of the number). Optionally a third: harness run *on* the EC2 against `localhost` to remove your ISP from the picture.

### 2.3 Procedure (repeat identically for every scenario)
1. Fresh dataset: `flowgrid-tools seed --sql --seed 42 --orders 50000` (LOCAL) or the demo seed via API (AWS, smaller); record the seed and counts.
2. Restart the API (cold JVM) and Redis; `FLUSHDB` for the cold-cache case only.
3. Warm-up: 30 s at the target load, discard.
4. Measure: 120 s (or the burst for B2). Save raw output: `load/results/<env>/<scenario>-<run>-<date>.json`.
5. Collect: k6 summary, harness report, `INFO stats`, Hikari metrics snapshot, `pg_stat_statements` top 5.
6. Repeat steps 2–5 **three times**; report the median run; note variance if p95 differs > 20 % between runs.
7. Fill the template block (§2.5) and add to `PERFORMANCE.md` with a two-sentence interpretation.

### 2.4 Rules
- Never quote a number without its environment line in the same sentence/table row.
- Never mix LOCAL and AWS numbers in one table without the env column.
- Never edit a target to match a result; record both.
- k6 numbers are the headline latency numbers; harness latencies are reported separately and labelled (Python client overhead).
- The harness contention verdict is the headline **correctness** result.
- If a run finds a bug (e.g. `undersell`), fix, add the regression test, re-run, and keep both entries (before/after) — that is the best material you can have.

### 2.5 Result template (copy per scenario)

```
### B1 — Order creation, 20 VUs — LOCAL — 2026-xx-xx — run 2 of 3 (median)
Environment: MacBook/ThinkPad <model>, <CPU>, <RAM>; Java 21.0.x (Temurin), -XX:MaxRAMPercentage=75, Boot 3.x.y;
  Postgres 16.x in Docker (Compose, default config + log_lock_waits); Redis 7.x in Docker; API run via <image|mvn>;
  git SHA <from /actuator/info>; dataset seed 42: 3 warehouses, 120 SKUs, 50,000 orders; Hikari max 10.
Load: k6 v0.5x, 20 VUs, 30 s warm-up discarded, 120 s measured; mix from tools/fixtures/mix.json (20 % hot SKUs, 15 % EXPRESS).
Method: fresh dataset, cold JVM, 3 runs, median reported; raw in load/results/local/b1-20vu-run2.json.
Result: p50 <ms> · p95 <ms> · p99 <ms> · <req/s> · 201: <n> · 409 INSUFFICIENT_STOCK: <n> · 5xx: <n>
Harness (same load, Python client): p95 <ms> (client overhead noted) · replays: <n> · verdict: PASS
Observations: <one or two sentences: what dominated, what changed since the last entry>
```

---

## 3. Résumé-bullet template

**The rule:** a bullet may contain a number only if that number appears in `PERFORMANCE.md` with an environment block, or is a count you can point to in the repo (tests, endpoints, tables). Until measured, the placeholder stays as `<measured …>` and the bullet is **not** on the résumé. See [`../../PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) and [`../../RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md).

Format: *Built/Designed/Implemented* **what** *using* **stack** *to achieve* **measured outcome or proven property**; one line each; strongest first.

**Templates (fill only with real work):**

- Designed and built **FlowGrid**, a multi-warehouse fulfillment and inventory platform (Java 21, Spring Boot 3, PostgreSQL, Redis, React/TypeScript, Docker, AWS), with race-free stock reservations using ordered row-level locking (`SELECT … FOR UPDATE`) proven by concurrency tests on Testcontainers — `<N>` concurrent orders for `<U>` units yield exactly `<U>` reservations.
- Implemented idempotent order creation (client key + canonical request hash + stored response, claim-before-execute) eliminating duplicate orders under client retries and concurrent submits; `<measured replays>` replays with zero duplicates in load runs.
- Built deterministic multi-warehouse allocation (weighted scoring: availability, region match, workload, capacity, priority; stored explanation) and a pick → pack → ship state machine with partial fulfillment, two-phase stock transfers and returns, all transactional and audited.
- Added Redis cache-aside for the catalog with after-commit invalidation and graceful degradation to PostgreSQL when Redis is unavailable (verified by integration tests); cache hit ratio `<measured %>` under `<load>`.
- Measured with k6 and a custom Python load harness: order-creation p95 `<measured ms>` at `<VUs>` VUs on `<environment>`; identified the connection pool as the first bottleneck under hot-SKU contention and `<what you changed, measured delta>`.
- Deployed to AWS (EC2 + Docker Compose, RDS PostgreSQL, S3, CloudWatch logs and alarms, least-privilege IAM instance role) via GitHub Actions CI/CD (tests → images → GHCR → deploy with health gate); `<N>` automated tests incl. `<N>` concurrency tests run on every PR.
- Wrote a typed, pytest-tested Python tooling package (`tools/`) that generates realistic seed data and drives concurrent load with post-run invariant verification (`successes == min(units, competitors)`), used for all benchmark and failure exercises.
- Executed `<N>` failure-engineering exercises (Redis outage, mid-transaction failure, deadlock, migration failure, crash between idempotency claim and commit), each with a documented root cause and regression test.

**Placeholders and their sources**

| Placeholder | Where it must come from |
|---|---|
| `<N>` concurrent / `<U>` units | The test's parameters (e.g. 10/1, 150/100) — it must exist in the suite |
| `<measured p95>`, `<VUs>`, `<environment>` | `PERFORMANCE.md` block for that scenario; use the AWS or LOCAL label |
| `<measured %>` hit ratio | Redis `INFO stats` recorded in B3 |
| `<measured replays>` | Harness `load` report with duplicate injection |
| `<N>` tests | `mvn verify` summary + `pytest` summary on `v1.0` |
| `<what you changed>` | A commit/PR you can open in the interview |

**Anti-patterns (never):** "handles 10k orders/s" (not measured), "production-grade" (no users), "reduced latency by 60 %" without before/after entries, "led a team", any number rounded up.

**Ordering advice:** lead with the reservation/idempotency bullet (engineering depth), then measurements (evidence), then deploy/CI (professional practice), then Python tooling (polyglot). Cut to 3–4 bullets on a one-page résumé; keep the rest for the deep dive.

---

## 4. GitHub hygiene checklist

Before announcing `v1.0`:

- [ ] Repository name `flowgrid`, description = the one-paragraph pitch, topics: `java`, `spring-boot`, `postgresql`, `redis`, `react`, `typescript`, `docker`, `aws`, `github-actions`, `inventory-management`
- [ ] Pinned on your profile (with LedgerX/ForgeCI/FlagForge later; keep ≤ 6 pins)
- [ ] `main` protected: PR + CI required; no force-push; squash merges
- [ ] Five milestones M1–M5 with all issues closed and linked PRs; ADVANCED issues in a `v1.1` milestone, not left open in `v1.0`
- [ ] Labels used consistently (`type:*`, `area:*`); no orphan issues
- [ ] Tags `v0.1` … `v0.4`, `v1.0` annotated; `v1.0` has a GitHub Release with notes, screenshots, benchmark summary and the deployed URL (or "stopped" note)
- [ ] CI badge green on `main`; coverage badge or number in README
- [ ] `.github/pull_request_template.md`, `CODEOWNERS` (you), Dependabot config for Maven, npm and pip
- [ ] No secrets ever committed (`git log -p | grep -iE 'password|secret|AKIA'` clean; secret scanning enabled)
- [ ] `docs/` complete per §1; `docs/screenshots/` and the GIF present; `docs/openapi.json` exported at `v1.0`
- [ ] `tools/README.md` present; `tools/` tests visible in CI
- [ ] Commit history readable: Conventional Commits, one commit per issue on `main`
- [ ] Issues for known limitations opened (honest backlog), e.g. "rate limiting on /auth/login", "cursor pagination for orders list"
- [ ] `trackers/project-tracker.md` in this roadmap updated with dates, hours and the measured headline numbers ([`../../trackers/project-tracker.md`](../../trackers/project-tracker.md))
- [ ] Deep-dive rehearsal recorded and scored; notes in [`../../trackers/interview-tracker.md`](../../trackers/interview-tracker.md)
