# LedgerX — Docs, Benchmarks and Résumé

> Documentation is part of the Strong Résumé Version, not an afterthought. Docs are written in
> the Thursday docs hour of each week and completed in M5. Benchmarks follow a protocol so
> that every number you quote can be reproduced and defended. Résumé bullets come **only**
> from recorded results. See [`../../PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) and
> [`../../RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md).

Templates: [`../templates/design-doc.md`](../templates/design-doc.md) ·
[`../templates/adr.md`](../templates/adr.md) · [`../templates/benchmark-report.md`](../templates/benchmark-report.md).

---

## 1. Required documents (in your `ledgerx` repo)

| File | Written in | Must contain |
|---|---|---|
| `README.md` | M1 draft, M5 final | Pitch; why double-entry (≤ 10 lines); transfer sequence diagram; quick start with `curl` incl. `Idempotency-Key` and a replay; invariants list; named mandatory tests + CI link; measured numbers + link to `PERFORMANCE.md`; architecture diagram; limitations; links to all docs; Python tools usage |
| `docs/ARCHITECTURE.md` | M2, M5 | Component diagram; transfer sequence diagram (happy, replay, insufficient funds); package rules; transaction boundaries table (which service method, propagation, isolation); outbox flow; where Redis is optional; the `tools/` verifier as an independent component |
| `docs/API.md` | M1–M3 | Endpoint table with auth + idempotency column; idempotency contract; cursor pagination; error catalogue; example requests/responses; link to committed `openapi.json` |
| `docs/DATABASE.md` | M1, M3, M4 | ERD (image + source); every table with constraints; the three triggers (immutability, balanced journal, balance floor) with rationale; roles and grants; indexes with the query each serves; `EXPLAIN` excerpts; money representation (ADR link); retention/cleanup jobs |
| `docs/TESTING.md` | M2, M4 | Test pyramid for LedgerX; how to run (`mvn verify`, Docker needed); the named mandatory tests and what each proves; concurrency-test methodology (latch, loops, no reruns); fault-injection hook and crash-point table; invariant suite description; Python tool tests (`pytest -m unit`, `-m integration`); flakiness policy |
| `docs/FAILURE_ENGINEERING.md` | W10–W12 | One record per drill from [`failure-engineering.md`](./failure-engineering.md) using its template, with real observations and log excerpts |
| `docs/PERFORMANCE.md` | M4 baseline, M5 AWS | Benchmark reports (§2 template) for every run you quote; `EXPLAIN` plans; lock-wait observations; what bounded throughput; what you did not measure |
| `docs/DEPLOYMENT.md` | M5 | AWS topology; roles/secrets (names, never values); step-by-step deploy; release pipeline; rollback; RDS restore test record; cost and teardown |
| `docs/SECURITY.md` | M5 | Authn/authz model; per-wallet ownership; admin actions and audit; idempotency scope; input validation rules; DB roles; OWASP API Top 10 walk-through with findings and fixes; what is *not* covered (no PCI data by design) |
| `docs/DESIGN_DECISIONS.md` | ongoing | Index of ADRs with one-line summaries + scalability notes (sharding, hot accounts, sub-accounts, queues, multi-region) |
| `docs/adr/ADR-001…010.md` | per milestone | Signed amount vs debit/credit columns; `NUMERIC` vs minor units; pessimistic vs optimistic; isolation level; key reservation in separate txn; outbox vs direct publish; refund/reversal policy; no auto-fix on drift; stale-key policy; independent verifier in Python |
| `docs/experiments/isolation-and-locking.md` | M2 | Setup; three configurations; throughput/error tables at 8 and 32 threads; `pg_stat_database.deadlocks`; conclusion |
| `tools/README.md` | M4 | Install (`pip install -e .[dev]`), the three commands with options, DSN/role requirements, JSON report schema, exit codes, how the tools are used in failure drills and the release pipeline, how to run `pytest`, `mypy --strict`, `ruff` |
| `CHANGELOG.md` | tags | `v0.5`, `v1.0`, `v1.1` with what each includes |

Diagrams: commit sources (`.drawio`/`.excalidraw`/Mermaid) beside PNGs under `docs/diagrams/`.

---

## 2. Benchmarking protocol

**Principle:** a number without environment, setup, load, methodology and raw output is not a
result. Record everything in the template; keep raw k6/generator output files under
`docs/perf/raw/`.

### 2.1 What to measure

| Metric | Scenario | Why it matters |
|---|---|---|
| Transfers/s, p50/p95/p99 latency, error counts by code | **Hot**: all transfers debit distinct senders → one destination account, and separately all from one sender | Shows lock-serialization ceiling |
| Same | **Spread**: transfers between random pairs of 1,000 accounts | Shows the non-contended ceiling (CPU/pool/DB-bound) |
| `pg_stat_database.deadlocks`, `40001`/`40P01` retry counts | both | Proves lock ordering |
| Idempotent replay latency | 20 % duplicate keys mixed in | Replay path should be cheaper than execution |
| Reconciliation duration | 100k, 1M entries; full vs incremental | Operability |
| Python verifier duration | same sizes | It runs post-deploy; must fit in the pipeline |
| Locked-section time (Micrometer timer) | hot | The actual bound on hot-account throughput |

### 2.2 Method

1. Warm up 30 s; measure 60 s; repeat 3×; report median run and note variance.
2. One variable at a time: concurrency 1 / 8 / 32 / 64 (k6 VUs or generator threads).
3. Seed data with `tools/ledgerx_gen --seed` so runs are comparable; record the seed and counts.
4. Record DB stats before/after (`pg_stat_database`, `pg_stat_user_tables` for
   `ledger_entry`, Hikari pool metrics from Actuator).
5. Run the Python verifier after every benchmark — a benchmark that produced findings is
   invalid (and a bug report).
6. Load generator on a separate machine/container from the app where possible; note when not.
7. Never quote a local-laptop number as if it were the deployed system's; label it.

### 2.3 Report template (`docs/PERFORMANCE.md`, one block per run)

```
## Run: <name>                                   Date: <YYYY-MM-DD>   Commit: <sha>   Tag: <vX>
Environment
  App host:      <EC2 type / laptop model, vCPU, RAM, OS>   JVM: <21.x, flags>
  DB:            <RDS class or container; Postgres 16.x; storage; max_connections>
  Network:       <same VPC / localhost / laptop→cloud>
  Load generator:<k6 vX on <host> | tools/ledgerx_gen vX on <host>>
Setup
  Accounts: <N users, M wallets>   Seed balance: <amount>   Seed command: <…>
  Config: locking=<PESSIMISTIC|OPTIMISTIC>  isolation=<…>  pool=<n>  fault hooks=off
Load
  Scenario: <hot-destination | hot-source | spread-1000>   Concurrency: <VUs/threads>
  Duration: warm-up 30 s, measure 60 s, repeats 3   Duplicate-key rate: <%>
Methodology
  <how requests were generated, how latency was measured (client-side), what was excluded>
Result (median of 3)
  Throughput: <x> transfers/s   p50 <a> ms   p95 <b> ms   p99 <c> ms
  Errors: 422 INSUFFICIENT_FUNDS <n>  409 <n>  5xx <n>   deadlocks <n>   40001 retries <n>
  Locked section p95: <ms>   DB CPU: <%>   App CPU: <%>   Hikari wait p95: <ms>
  Verifier after run: findings=<0>   duration <s>
Interpretation
  <what bounded it, what you changed between runs, what you would try next>
Raw: docs/perf/raw/<files>
```

### 2.4 Reconciliation benchmark

Generate 100k and 1M entries with `ledgerx_gen --ops` (or a SQL generator for bulk), run the
Java job (full and incremental) and the Python verifier; record durations, the `EXPLAIN` of
the drift query, and memory of the Python process (`/usr/bin/time -v`).

---

## 3. Résumé bullets — from real results only

**Rule:** every number in a bullet must map to a block in `docs/PERFORMANCE.md` or a test in
the repo that CI ran. If you did not measure it, do not write it. Prefer *what you proved* over
*what you used*.

### 3.1 Templates (fill from your own records)

- Built **LedgerX**, a double-entry wallet ledger (Java 21, Spring Boot 3, PostgreSQL 16) with
  DB-enforced immutability (triggers + revoked privileges) and balances derived from
  append-only entries; reconciliation and an independent Python verifier prove every journal
  sums to zero and every balance equals its entry sum.
- Implemented idempotent, concurrency-safe transfers using ordered `SELECT … FOR UPDATE`
  row locking and idempotency keys finalized in the same transaction as ledger writes; verified
  with Testcontainers concurrency tests (e.g. two concurrent $400 transfers on a $500 balance →
  exactly one succeeds) and fault-injection tests at every step of the transfer.
- Measured **[N] transfers/s at p95 [X] ms** on a single hot account and **[M]/s at p95
  [Y] ms** across 1,000 accounts on **[EC2 type + RDS class]** with k6 at [C] VUs
  (methodology in repo); identified lock hold time as the hot-account bound and documented
  sub-account and per-account-queue alternatives.
- Designed compensating reversals/refunds, a transactional outbox with at-least-once
  delivery, cursor-paginated history, and an append-only audit log; ran [K] failure-engineering
  drills (crash-before/after-commit, deadlock, stale idempotency keys, drift) each with a
  regression test.
- Wrote a typed, pytest-tested Python toolkit (psycopg 3, `decimal`) that independently
  re-derives ledger invariants from raw tables and gates releases in CI/CD.

### 3.2 Anti-patterns (never)

- "Handles millions of transactions" (you did not run millions in production).
- "Zero-downtime", "99.99 %", "bank-grade" — unmeasured adjectives.
- Quoting the laptop number without saying "local".
- Bullets that list technologies with no outcome.

### 3.3 Story bank entries (for [`../../16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md))

Write three STAR stories from LedgerX: (1) a bug found by a failure drill or the verifier
(what disagreed, how you traced it); (2) a design decision you reversed (e.g. optimistic →
pessimistic after the experiment, or where the key is finalized); (3) a scope cut you made to
protect quality (what you dropped from M5 and why).

---

## 4. GitHub hygiene

- **Repo:** `ledgerx`, public, description "Double-entry wallet ledger with idempotent,
  concurrency-safe transfers — Java 21 / Spring Boot 3 / PostgreSQL 16 / Python verifier".
  Topics: `spring-boot`, `postgresql`, `double-entry`, `idempotency`, `testcontainers`, `ledger`.
- **README first screen:** pitch, badges (CI, license), one diagram, quick start. No wall of
  text before the first command.
- **Milestones** `M1`…`M5` closed with all issues; issues have labels (`area:*`, `type:*`),
  acceptance criteria copied from [`milestones.md`](./milestones.md), and link to the PR that
  closed them.
- **PRs:** template (what/why/how tested/risk/rollback); CI green; self-review comments; squash
  merge; no direct pushes to `main`.
- **Tags/releases:** `v0.5` (MVP), `v1.0` (Strong Résumé Version), `v1.1` (Advanced), each with
  release notes and the benchmark summary; `CHANGELOG.md` in sync.
- **Commits:** conventional, small, message explains *why*; no "fix", "wip", "final2".
- **Secrets:** `.env.example` only; secret scanning enabled; a test that fails if a
  `application-*.yml` contains a password literal.
- **License:** MIT or Apache-2.0. **`.github/`**: `ci.yml`, `release.yml`, PR template, issue
  templates (bug/feature), `CODEOWNERS` (you), Dependabot for Maven, pip and Actions.
- **Demo:** GIF/MP4 in README (≤ 5 min flow from [`README.md`](./README.md) §23) and a
  `scripts/demo.sh` that reproduces it against Compose.
- **Polish weeks (24–26):** re-run all benchmarks on the deployed system, refresh numbers,
  close stale issues, verify every link in docs, record the final deep-dive.

Tracking: [`../../trackers/project-tracker.md`](../../trackers/project-tracker.md) ·
readiness criteria: [`../../JOB_READINESS.md`](../../JOB_READINESS.md).
