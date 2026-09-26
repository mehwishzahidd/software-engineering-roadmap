# 🏗️ 18-projects — How to Run Any Project Through This Roadmap

> The four flagship projects — [FlowGrid](./flowgrid/README.md), [LedgerX](./ledgerx/README.md),
> [ForgeCI](./forgeci/README.md), [FlagForge](./flagforge/README.md) — each have five spec files
> here and **one repository of their own on GitHub that you build**. This page is the shared
> operating procedure: how a milestone becomes issues, branches, PRs, tests, a tag and an
> interview story. Overview and scope tiers live in [`PROJECTS.md`](../PROJECTS.md); order and
> weeks in [`ROADMAP.md`](../ROADMAP.md).

[← README](../README.md) · [Projects overview](../PROJECTS.md) · [Project selection](../PROJECT_SELECTION.md) · [Project tracker](../trackers/project-tracker.md)

---

## Contents

1. [The rule](#1-the-rule)
2. [Starting a project: design doc first](#2-starting-a-project-design-doc-first)
3. [Milestone → GitHub milestone → issues → branch → PR → tag](#3-milestone--github-milestone--issues--branch--pr--tag)
4. [The milestone loop](#4-the-milestone-loop)
5. [Definition-of-done checklist](#5-definition-of-done-checklist)
6. [Repo docs standard](#6-repo-docs-standard)
7. [Benchmark reporting standard](#7-benchmark-reporting-standard)
8. [Failure-engineering protocol](#8-failure-engineering-protocol)
9. [Deep-dive rehearsal protocol](#9-deep-dive-rehearsal-protocol)
10. [Index of the four projects](#10-index-of-the-four-projects)

---

## 1. The rule

**This repository teaches; you build.** No file under `18-projects/` contains a complete
implementation. Each milestone gives you what to know first, requirements, architectural guidance,
acceptance criteria, the instruction "now implement it", verification tests to write, debugging
scenarios and interview questions. Copy-paste code appears only for tiny isolated concepts
(≤ ~25 lines). If you catch yourself pasting a tutorial's service class, stop: the goal is that
"I built this" is simply true in an interview.

Language split: every backend is **Java 21 / Spring Boot 3**. Every project also ships at least one
tested, documented **Python component** in `tools/` (generators, simulators, verifiers, analysis;
FlagForge also `sdk-python/`) — see [`PROJECTS.md` §3a](../PROJECTS.md#3a-python-engineering-component-per-project).

---

## 2. Starting a project: design doc first

Before the first line of code in a new project (and before each milestone that changes the
architecture), write a short design doc from [`templates/design-doc.md`](./templates/design-doc.md):

1. Create the GitHub repository (public, MIT licence, `.gitignore`, README stub with the one-line description).
2. Copy the template to `docs/design/01-<milestone-slug>.md`. Fill context, goals/non-goals,
   requirements, architecture sketch, data model, API, state machines, failure modes, security,
   testing plan, rollout. Leave honest `?`s.
3. Every decision that could be argued the other way becomes an ADR from
   [`templates/adr.md`](./templates/adr.md) in `docs/adr/`, indexed in `docs/DESIGN_DECISIONS.md`.
   The example ADR ("SSE instead of WebSockets") shows the expected depth.
4. Timebox: 1–2 hours for a milestone design doc. It is a thinking tool, not a deliverable to polish.
5. Revisit it when the implementation diverges. Divergence is normal; undocumented divergence is the problem.

Set up the repo skeleton the same day: Maven project (multi-module for ForgeCI), `compose.yaml`
for Postgres (+ Redis when needed), Flyway `V1__init.sql`, `.env.example`, a GitHub Actions
workflow running `mvn verify`, a `tools/` directory with a `pyproject.toml` and an empty `tests/`
folder for the Python component. CI must be green on the first PR.

---

## 3. Milestone → GitHub milestone → issues → branch → PR → tag

| Step | Action | Convention |
|---|---|---|
| 1 | Create a **GitHub milestone** per roadmap milestone | `M1 — Domain, persistence, auth` (names from each `milestones.md`) |
| 2 | Turn each requirement / acceptance criterion into an **issue** under that milestone | Title as a verb phrase; body = acceptance criteria as `- [ ]`; labels: `feature`, `test`, `docs`, `infra`, `bug`, `stretch` |
| 3 | One **branch** per issue | `feat/12-idempotency-key-store`, `test/15-reservation-concurrency`, `fix/22-double-release` |
| 4 | Small commits, imperative messages | `Add idempotency_key table and Flyway V4`, `Reject reuse of Idempotency-Key with different body` |
| 5 | Open a **PR** per issue (or per tightly related pair); the PR body copies the acceptance criteria and ticks them; CI green before merge | Squash-merge if the branch history is noisy; keep `main` linear and readable |
| 6 | Review your own PR like a stranger (read the diff top to bottom, 10 minutes minimum) — then merge | Check: tests for every behaviour claimed; no debug logging; no secrets; docs updated |
| 7 | When all issues in the milestone are closed, **tag** | `v0.1` … `v0.4` per milestone, `v1.0` = STRONG RESUME VERSION, `v1.1+` = ADVANCED |
| 8 | Update [`trackers/project-tracker.md`](../trackers/project-tracker.md) and close the GitHub milestone | Note slippage honestly; it is the input to the retro |

Scope that gets cut is not deleted — it becomes an issue labelled `stretch` in the backlog. Recruiters
reading the repo see an engineer who manages scope, not one who ran out of time.

---

## 4. The milestone loop

Every milestone in every `milestones.md` has exactly these eight sections. Work them in order.

| # | Section | You do |
|---|---|---|
| 1 | **Know first** | Read the linked topic files *this week* (e.g. [`05-spring-boot/07-transactions.md`](../05-spring-boot/07-transactions.md), [`04-sql-databases/06-transactions.md`](../04-sql-databases/06-transactions.md), [`04-sql-databases/redis.md`](../04-sql-databases/redis.md)). Write a 5-line summary in your own words per topic. |
| 2 | **Requirements** | Convert into GitHub issues (§3). Ask "what does the domain say?" — for FlowGrid, your operations experience is a legitimate source. |
| 3 | **Architectural guidance** | Update the design doc; write ADRs for the choices the guidance leaves open (`FOR UPDATE` vs `@Version`, `BLMOVE` vs Streams, polling vs SSE). |
| 4 | **Acceptance criteria** | Paste into the milestone issues as checkboxes. They become your PR checklist and your test names. |
| 5 | **Now implement it** | Blank file. Small steps. Run the app after each step. Commit often. |
| 6 | **Verification tests** | Write the tests described (unit / slice / Testcontainers / concurrency / Python `pytest` for tools). They must pass in CI. A test you cannot make fail on purpose is not testing anything. |
| 7 | **Debugging scenarios** | Do them, every one. Predict → break → observe → explain. Record findings in `docs/failure-log.md` (§8). |
| 8 | **Interview questions** | Answer aloud without notes, recorded. Mark weak answers; they go into the next Sunday review and into [`trackers/interview-tracker.md`](../trackers/interview-tracker.md). |

A milestone that skips 6–8 is not finished, however much code was written. If a milestone slips,
finish it before starting the next (ROADMAP §13).

---

## 5. Definition-of-done checklist

Copy into the final milestone issue of each project. The full version with rationale is in
[`PROJECTS.md` §8](../PROJECTS.md#8-definition-of-done).

**Code and tests**
- [ ] All STRONG RESUME VERSION milestones merged to `main`, tagged `v1.0`
- [ ] The core engineering problem is proven by an automated test (concurrency / invariants / recovery / measured latency)
- [ ] State machines enforced; illegal transitions tested
- [ ] Unit + slice + Testcontainers integration tests; `mvn verify` green in GitHub Actions; badge in README
- [ ] Frontend (where present): builds in CI, at least smoke-level component tests
- [ ] No known data-corrupting bug open; every open bug is a labelled issue

**Python component**
- [ ] The project's Python tool(s) exist in `tools/` (FlagForge: also `sdk-python/`) and do the job the spec names (generator / harness / verifier / simulator / linter / test client)
- [ ] `pytest` suite passes, type hints present, `pyproject.toml` or `requirements.txt` committed
- [ ] `tools/README.md`: purpose, install, run command, how to test, which Java test / benchmark / failure exercise uses it
- [ ] Runs in CI (a `python` job) or the README says why not
- [ ] Referenced from TESTING.md or PERFORMANCE.md where it is used

**Failure engineering**
- [ ] Every exercise in the project's `failure-engineering.md` completed and logged (§8)
- [ ] Each has a regression test or a documented design-around

**Deployment**
- [ ] Deployed on AWS at least once with a reproducible DEPLOYMENT.md (IAM least privilege, CloudWatch logs, alarm)
- [ ] `docker compose up` runs the whole stack locally; `.env.example` complete; no secrets committed
- [ ] Teardown and cost review done ([`12-aws/cost-safety.md`](../12-aws/cost-safety.md))

**Documentation**
- [ ] README passes the [first-impression checklist](../PROJECT_SELECTION.md#8-readme-first-impression-checklist)
- [ ] ARCHITECTURE (diagram), API (+ OpenAPI), DATABASE (ERD), TESTING, DEPLOYMENT, SECURITY, DESIGN_DECISIONS present and true
- [ ] PERFORMANCE.md only if it contains a real benchmark report (§7)
- [ ] GitHub milestones/issues tidy; `stretch` backlog visible; releases with notes

**Explainability**
- [ ] Deep-dive rehearsal done and scored (§9)
- [ ] `interview-questions.md` drilled end to end at least once
- [ ] Résumé bullets drafted in `docs-and-resume.md` with `<placeholder>` where unmeasured

---

## 6. Repo docs standard

| File | Must contain | Rule |
|---|---|---|
| `README.md` | One-line description, badges, diagram or screenshot, stack line, "the hard part" + proof, quick start, status/tier, links, honest context line | Always |
| `docs/ARCHITECTURE.md` | Components, main flows (numbered), key patterns and why, diagrams (Mermaid is fine) | Always |
| `docs/API.md` | Auth, error format (ProblemDetail), idempotency, pagination, endpoint table, link to OpenAPI (`/v3/api-docs` or committed `docs/openapi.yaml`) | Always |
| `docs/DATABASE.md` | ERD, tables, constraints and why, indexes and the queries they serve, migration policy | Always |
| `docs/TESTING.md` | Test layers, how to run each, what the important tests prove, the Python tools and their `pytest` command | Always |
| `docs/DEPLOYMENT.md` | Local Compose, AWS topology, IAM, secrets, CI/CD pipeline, teardown | Always |
| `docs/SECURITY.md` | Auth model, roles, secrets handling, validation, project-specific threats | Always, short |
| `docs/DESIGN_DECISIONS.md` | ADR index with one-line summaries | Always |
| `docs/PERFORMANCE.md` | Index of benchmark reports (§7) | **Only if a real measured result exists** |
| `docs/failure-log.md` | One entry per failure exercise (§8) | Always |
| `tools/README.md` | Python component documentation | Always |

"Only if meaningful": every document must state something true and specific about *this* repo.
Delete generic sections rather than leave them. Short and true beats long and vague.

---

## 7. Benchmark reporting standard

**Unmeasured = not reportable.** Every performance number anywhere (README, PERFORMANCE.md,
résumé, interview answer) traces to a report written from
[`templates/benchmark-report.md`](./templates/benchmark-report.md) and committed at
`docs/perf/<date>-<slug>.md` with its raw output.

A report is complete only when it records: **goal · environment** (hardware, OS, JVM, DB/Redis
versions, container limits, where the load generator ran) **· setup** (data volume, config) **·
load** (tool, VUs, duration, ramp, mix) **· methodology** (warm-up, runs, which run is reported,
percentiles) **· results table · interpretation · threats to validity · reproducibility commands**.

What each project measures for its STRONG RESUME VERSION:

| Project | Measurement | Tool |
|---|---|---|
| FlowGrid | Order-creation latency and throughput under concurrent clients; reservation contention | k6 + the Python load harness |
| LedgerX | Transfer throughput and latency; lock-strategy comparison (pessimistic vs optimistic) | k6 / JUnit timing + Python generator |
| ForgeCI | Queue wait time, jobs/min vs worker count, orphan-recovery time | Python webhook/load simulator + log analysis |
| FlagForge | Server-side evaluation p50/p95/p99 at N rps; cache-hit ratio; propagation delay publish → SDK | k6 / JMH + Python rollout simulator |

Laptop numbers are fine when labelled as laptop numbers. Comparisons need the same environment for
both variants. Never report a cold-start run as steady state.

---

## 8. Failure-engineering protocol

Each project's `failure-engineering.md` lists exercises (kill Redis mid-request; crash between
ledger steps; deliver a webhook twice; kill a worker mid-job; publish a flag during a poll storm).
For every exercise:

```
reproduce → observe → inspect logs → diagnose → fix (or design around) → regression test
```

| Step | Do | Record in `docs/failure-log.md` |
|---|---|---|
| **Reproduce** | Make it deterministic: a test, a script, `docker kill`, a fault-injection hook, a temporary sleep | The exact command / test name |
| **Observe** | What does the *user* see first? Wrong balance, duplicate order, hung request, silent loss? | One sentence |
| **Inspect logs** | Structured app logs, `docker logs`, Postgres logs, `redis-cli MONITOR`, `psql` state queries | Key log lines / state snapshot |
| **Diagnose** | Root cause in one sentence — not the symptom | The sentence |
| **Fix or design around** | Code change, or an explicit policy ("degrade to DB", "at-least-once + idempotent handler", "lease expiry re-queues") | Commit / PR link, ADR if a design change |
| **Regression test** | A test that fails if the bug returns; if impossible to automate, say why | Test name or reason |

Predict the outcome **before** running each exercise; a wrong prediction is the most valuable
result. Count completed exercises in [`trackers/project-tracker.md`](../trackers/project-tracker.md).

---

## 9. Deep-dive rehearsal protocol

After every project's `v1.0` (Weeks 8, 13, 19, 23), and again in Weeks 24–26, run the rehearsal
from [`16-interview-prep/project-deep-dive.md`](../16-interview-prep/project-deep-dive.md):

1. **Set up:** timer at 15 minutes, screen recording on, no notes, the repo closed.
2. **Structure (≈ 12 min):** what it is and for whom (1 min) → architecture on a whiteboard/paper
   (3 min) → the hardest problem and how you proved the solution (4 min) → a failure you caused on
   purpose and what changed (2 min) → what you would do differently / next (2 min).
3. **Probe (≈ 3 min):** pick 5 questions at random from the project's `interview-questions.md`
   (backend, database, concurrency, deployment, Python tooling) and answer them cold.
4. **Score** against [`INTERVIEW_CHECKLIST.md`](../INTERVIEW_CHECKLIST.md): clarity, correctness,
   trade-offs named, numbers only when measured, honesty about scope. Note every stumble.
5. **Remediate:** each stumble becomes a 5-line written answer in `docs-and-resume.md` notes and a
   re-run the following Sunday. Two rehearsals per project minimum before Week 24.
6. **Log** in [`trackers/project-tracker.md`](../trackers/project-tracker.md) and
   [`trackers/interview-tracker.md`](../trackers/interview-tracker.md).

---

## 10. Index of the four projects

| # | Project | Weeks | Spec | Milestones | Failure engineering | Interview questions | Docs & résumé |
|---|---|---|---|---|---|---|---|
| 1 | **FlowGrid** — multi-warehouse fulfillment & inventory platform | 4–8 | [README](./flowgrid/README.md) | [milestones](./flowgrid/milestones.md) | [failure-engineering](./flowgrid/failure-engineering.md) | [interview-questions](./flowgrid/interview-questions.md) | [docs-and-resume](./flowgrid/docs-and-resume.md) |
| 2 | **LedgerX** — digital wallet & double-entry ledger engine | 9–13 | [README](./ledgerx/README.md) | [milestones](./ledgerx/milestones.md) | [failure-engineering](./ledgerx/failure-engineering.md) | [interview-questions](./ledgerx/interview-questions.md) | [docs-and-resume](./ledgerx/docs-and-resume.md) |
| 3 | **ForgeCI** — distributed CI/CD execution platform | 14–19 | [README](./forgeci/README.md) | [milestones](./forgeci/milestones.md) | [failure-engineering](./forgeci/failure-engineering.md) | [interview-questions](./forgeci/interview-questions.md) | [docs-and-resume](./forgeci/docs-and-resume.md) |
| 4 | **FlagForge** — feature-flag & progressive-rollout platform + Java SDK | 20–23 | [README](./flagforge/README.md) | [milestones](./flagforge/milestones.md) | [failure-engineering](./flagforge/failure-engineering.md) | [interview-questions](./flagforge/interview-questions.md) | [docs-and-resume](./flagforge/docs-and-resume.md) |

Templates: [design-doc](./templates/design-doc.md) · [adr](./templates/adr.md) · [benchmark-report](./templates/benchmark-report.md)

Related: [`PROJECTS.md`](../PROJECTS.md) (overview, tiers, timeline) · [`PROJECT_SELECTION.md`](../PROJECT_SELECTION.md) (which project for which role, bullet rules) · [`trackers/project-tracker.md`](../trackers/project-tracker.md) · [`checkpoints/`](../checkpoints/README.md)
