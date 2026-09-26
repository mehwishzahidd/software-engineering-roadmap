# 🎯 PROJECT_SELECTION — Which Project to Feature, and How

> Four projects, one résumé, many kinds of job. This file decides **which project leads for which
> role**, what technical story each one tells, and the **honesty rules** for turning them into
> résumé bullets, GitHub pins and a 30-second recruiter pitch. Final decisions are made in Week 26
> (ROADMAP §5) and recorded in [`trackers/project-tracker.md`](./trackers/project-tracker.md).

[← README](./README.md) · [Projects overview](./PROJECTS.md) · [Résumé tech defense](./RESUME_TECH_DEFENSE.md) · [Job readiness](./JOB_READINESS.md)

---

## Contents

1. [Strongest project per role type](#1-strongest-project-per-role-type)
2. [If only two fit — and if three fit](#2-if-only-two-fit--and-if-three-fit)
3. [The technical story each project tells](#3-the-technical-story-each-project-tells)
4. [Choosing per job description](#4-choosing-per-job-description)
5. [Résumé presentation rules](#5-résumé-presentation-rules)
6. [Bullet-writing rules](#6-bullet-writing-rules)
7. [GitHub profile pinning strategy](#7-github-profile-pinning-strategy)
8. [README first-impression checklist](#8-readme-first-impression-checklist)
9. [Recruiter-screen 30-second pitch](#9-recruiter-screen-30-second-pitch)

---

## 1. Strongest project per role type

### Backend roles (Java / Spring / "backend engineer") → **LedgerX**, with FlowGrid a close second

The argument for **LedgerX**: backend interviewers for junior roles ask about transactions,
isolation, locking, idempotency and data integrity more than anything else, and LedgerX is
*only* about those. Its headline test — $500 balance, two concurrent $400 transfers, exactly one
succeeds, never negative — is the cleanest possible answer to "how do you prevent race conditions?".
Its append-only ledger enforced by the database (trigger / revoked privileges / CHECK constraints)
shows you understand that correctness belongs in the schema, not only in Java. The reconciliation
job and fault-injection tests show engineering maturity most junior candidates do not have.

The argument for **FlowGrid**: it covers *more* of the backend surface (JPA, validation, auth,
pagination, Redis, allocation logic, deploy) and has a domain you can speak about from real
operations experience. If the JD stresses "REST APIs", "Spring Boot", "PostgreSQL", "Redis" as a
list, FlowGrid ticks more boxes. If the JD stresses "payments", "fintech", "data integrity",
"transactions", LedgerX wins outright.

**Decision rule:** lead with LedgerX when the JD mentions correctness/fintech/transactions;
lead with FlowGrid when it lists technologies broadly or mentions logistics/e-commerce/inventory.
Feature both on the résumé for any backend role.

### General SDE / new-grad / internship roles → **FlowGrid**

Full stack (Spring Boot + React/TS), a real-world domain, deployed on AWS, with a concurrency
story (reservations) and a caching story (Redis). It demonstrates the *breadth* generalist
loops look for and gives non-specialist interviewers something concrete to ask about. Your
fulfillment/logistics operations background makes the domain modelling credible and gives you
genuine "why did you model it this way" answers.

### Systems / infrastructure / platform-engineering / DevOps-adjacent roles → **ForgeCI**

Queues, worker pools, leases and heartbeats, Docker Engine API, process isolation, timeouts and
cancellation, failure recovery, SSE streaming, a DAG scheduler and a Docker-socket security
discussion. It is the hardest project and the one that shows you can reason about a distributed
system that breaks. It also exercises Linux and Docker internals directly, which is what these
roles probe.

### Where **FlagForge** shines → platform / developer-tools / SDK / "developer experience" roles

FlagForge's differentiator is the **Java SDK**: designing a client library that other developers
depend on (builder API, polling vs streaming, local evaluation, stale-if-error, offline defaults,
semantic versioning, a published Maven artifact, contract tests). Add deterministic bucketing,
measured p99 evaluation latency and cache propagation, and it is the best answer to "have you
designed an API for other engineers?". It is also the best *system-design* conversation starter
because its trade-offs (consistency vs latency, push vs pull) are textbook.

---

## 2. If only two fit — and if three fit

Résumé space is limited; a one-page résumé for a junior role usually fits two projects with
three bullets each, or three projects with two bullets each.

### Only two fit → **FlowGrid + one of {LedgerX, ForgeCI}**

- **FlowGrid is always one of the two.** It is the broadest (full stack + AWS + Redis + concurrency),
  the most relatable domain, and the one that proves the most résumé technologies at once.
- **Second slot = LedgerX** for backend / fintech / data-heavy JDs (correctness story).
- **Second slot = ForgeCI** for infrastructure / platform / "distributed systems" JDs (recovery story).
- FlagForge is the second slot only for dev-tools / SDK / platform-product JDs.

Why not LedgerX + ForgeCI? Because neither has a substantial frontend or a relatable business
domain; together they read as "two backend engines" and lose the generalist reader.

### Three fit → **FlowGrid + LedgerX + ForgeCI** (default), swap ForgeCI for FlagForge on dev-tools JDs

Three projects tell a progression: *domain & full stack* → *correctness* → *distributed execution*.
That progression is itself an interview talking point ("each project was chosen to be harder in a
different dimension"). FlagForge remains pinned on GitHub and linked from the résumé header even
when it is not a résumé section — nothing is hidden, only prioritised.

### All four

Only when the résumé has room (e.g. a two-page CV for a market that expects it) or the JD is
unusually broad. Otherwise four projects with one bullet each is weaker than two with three.

---

## 3. The technical story each project tells

### FlowGrid

FlowGrid is a multi-warehouse fulfillment and inventory platform: products and SKUs with per-warehouse
inventory tracked by state (on hand → available → reserved → allocated → picked → shipped), orders
created idempotently, reservations that survive concurrent requests, deterministic allocation across
warehouses, a pick/pack/ship workflow with partial fulfillment, returns and two-phase transfers, a
React/TypeScript operations dashboard, Redis cache-aside for the catalog, and an AWS deployment with
CI/CD. The story: **"I modelled a real operational domain I know from experience, made its hot path
safe under concurrency, and shipped it end to end."**

Three strongest talking points:
1. The **N-threads-one-unit reservation test** and the `SELECT … FOR UPDATE` vs `@Version` comparison — why you chose what you chose.
2. **Idempotent order creation** with `Idempotency-Key` (stored key + request hash + response, TTL) — what happens on retry, on a different body, after TTL.
3. **Deterministic multi-warehouse allocation** (scoring by availability, capacity, distance proxy, workload, priority; tie-break by id) and **Redis-down → degrade to DB**.

Python talking point: the `tools/` **order generator and load/simulation harness** (pytest-tested)
that seeds realistic SKUs/warehouses and drives concurrent order creation to surface reservation
contention — the same harness feeds the k6 protocol and the failure exercises.

### LedgerX

LedgerX is a digital wallet built on a double-entry ledger: every journal transaction is a set of
debit/credit entries that sum to zero, entries are append-only and the database itself refuses
updates and deletes, balances are derived from entries and cross-checked against a materialized
balance, transfers are idempotent and use ordered pessimistic locking, refunds and reversals are
compensating transactions, events leave through a transactional outbox, and a reconciliation job
plus fault-injection tests prove the invariants hold even when the process crashes between steps.
The story: **"I built a system where being wrong by one cent is a bug, and I proved it isn't."**

Three strongest talking points:
1. **$500 / two concurrent $400** — exactly one succeeds; how lock ordering prevents deadlock; what optimistic locking did instead and why you compared them.
2. **DB-enforced immutability + invariants** — trigger / revoked privileges / CHECK constraints; the reconciliation job that flags drift.
3. **Crash-between-steps fault injection** and **retry-after-crash idempotency** — what state the system was in, how the retry converged.

Python talking point (often the strongest of all): the **independent reconciliation verifier**
in `tools/` reads PostgreSQL directly with `psycopg` and `decimal` and proves every journal sums
to zero and every balance equals its entries **without trusting the Java code** — "I did not let
the system grade its own homework." Plus the transaction-data generator and the consistency
checker used in the crash/retry exercises.

### ForgeCI

ForgeCI is a distributed CI/CD execution platform: a GitHub webhook (HMAC-verified, deduplicated on
delivery ID) creates a build whose jobs are pushed onto a reliable Redis queue; separate worker
processes claim jobs under a lease, run each in a temporary Docker container via the Engine API,
stream log chunks through Redis pub/sub to an SSE endpoint with replay-on-reconnect, and persist
results; timeouts kill containers, cancellation works on queued and running jobs, retries distinguish
application from infrastructure failures, heartbeats and lease expiry recover orphaned jobs, and
(advanced) a DAG scheduler runs `needs:` dependencies with fan-out/fan-in. The story: **"I built a
system where workers die, containers hang and webhooks repeat — and it recovers."**

Three strongest talking points:
1. **The reliable queue** — `BLMOVE` to a per-worker processing list with a lease (or Streams consumer groups) and why; at-least-once + idempotent job handling; orphan recovery.
2. **Failure taxonomy** — exit code ≠ 0 is not retried, container-start error / worker loss is retried with backoff and max N; graceful shutdown.
3. **SSE over WebSockets** for live logs with replay-from-sequence — the ADR and the reconnect behaviour.

Python talking point: the `tools/` **test-repository generator** (Git repos with passing / failing /
slow / timeout / bad-config `.forgeci.yml` variants), the **signed-webhook load simulator** that
measures queue wait and completion, and the **log/result analysis** tool that reports failure-taxonomy
statistics — the evidence behind the retry policy and the queue numbers.

### FlagForge

FlagForge is a feature-flag and progressive-rollout platform with a Java SDK: organisations,
projects and environments hold flags whose configurations are immutable versions (rollback is a new
version), rules are evaluated by priority with attribute targeting and deterministic percentage
rollout via `hash(flagKey:userKey) mod 10000`, environment snapshots are cached in Redis and
invalidated on publish, a `FlagClient` SDK evaluates locally from a polled (or SSE-streamed)
snapshot with defaults, timeouts, stale-if-error and offline mode, and a React dashboard manages
rules, versions, rollback and audit. The story: **"I designed a low-latency evaluation service and
the client library other developers would build on."**

Three strongest talking points:
1. **SDK design** — builder API, local evaluation, resilience (timeouts, stale-if-error, offline defaults), contract tests, semantic versioning, Maven publishing.
2. **Deterministic bucketing** — why the same user always lands in the same bucket, why rollouts are monotonic, why the hash input is `flagKey:userKey`.
3. **Propagation and caching** — publish → Redis pub/sub → SSE; invalidate-on-publish; stampede protection; the measured p99.

Python talking point: the **rollout-distribution simulator** (proves `pct ± tolerance` and
stickiness over N users against server evaluation), the minimal **Python SDK / test client** that
implements the same evaluation contract (polling + ETag, defaults, offline) and powers cross-SDK
contract tests, and the **configuration linter** (overlapping / unreachable / invalid rules).
"Two SDKs in two languages agreeing on every evaluation" is a compact proof that the contract is real.

### Where Python sits in the story

Backends are Java/Spring; Python is the coding-interview language and each project's tooling
language. Say it that way. Never describe a project as "Python + Java" — describe it as a Java
system with pytest-tested Python tooling, and name the tool. See
[`17-resume-tech-defense/python.md`](./17-resume-tech-defense/python.md) for the defense drill.

---

## 4. Choosing per job description

Scan the JD, match keywords, lead with the first project in the row.

| JD keywords | Lead with | Also feature | Notes |
|---|---|---|---|
| Java, Spring Boot, REST, PostgreSQL, microservices (generic backend) | FlowGrid | LedgerX | Broadest tech coverage first; correctness second |
| Payments, fintech, banking, ledger, transactions, data integrity, ACID, consistency | LedgerX | FlowGrid | Open the pitch with the $500/$400/$400 test |
| Full stack, React, TypeScript, end-to-end, product engineer | FlowGrid | FlagForge | Both have real dashboards; FlagForge's rules editor is the richer form UI |
| Distributed systems, queues, workers, async, reliability, fault tolerance | ForgeCI | LedgerX | ForgeCI for recovery, LedgerX for correctness under failure |
| Infrastructure, platform, DevOps, CI/CD, Docker, containers, Linux | ForgeCI | FlowGrid | Mention the Docker-socket security ADR and the multi-worker Compose stack |
| Developer tools, SDK, client library, API design, developer experience | FlagForge | ForgeCI | Lead with the Java SDK and contract tests |
| Performance, latency, caching, high throughput | FlagForge | ForgeCI | Only with measured p99 / jobs-per-minute numbers |
| E-commerce, logistics, supply chain, inventory, operations, warehouse | FlowGrid | LedgerX | Your operations background is a differentiator here — say so |
| AWS, cloud, EC2, RDS, S3, CloudWatch | FlowGrid | ForgeCI | FlowGrid = first and most complete deploy; ForgeCI = multi-service |
| Testing, quality, reliability engineering, TDD | LedgerX | ForgeCI | Invariant suite + fault injection; chaos-style integration tests |
| Python, scripting, tooling, automation, data pipelines (alongside Java) | LedgerX | FlagForge | Independent Python verifier; Python SDK/test client + rollout simulator. Backends stay Java — say so |
| Internship / new grad, no specific stack | FlowGrid | LedgerX or ForgeCI | Breadth first, then the one hard story |
| System design mentioned in interview process | FlagForge or ForgeCI | — | Both are textbook design problems you have actually built |

When two rows match, prefer the row whose lead project is **further along** in
[`trackers/project-tracker.md`](./trackers/project-tracker.md). A deployed `v1.0` beats a
better-matching MVP.

---

## 5. Résumé presentation rules

1. **A "Projects" section, separate from employment.** Past roles go under "Experience" and are
   described truthfully. The four projects go under "Projects" (or "Selected Projects"). They are
   never presented as client work, employment, or a company.
2. **Never backdate.** Each project is dated as built: `FlowGrid — Oct–Nov 2026` (whatever the
   real weeks were). Never fold project dates into the employment gap to hide it; the gap is
   handled in [`16-interview-prep/behavioral.md`](./16-interview-prep/behavioral.md), not by the résumé layout.
3. **Link every project to its repository** (and the live URL while it is deployed). If a
   deployment has been torn down for cost, say "deployed on AWS (EC2/RDS) — see DEPLOYMENT.md"
   rather than leaving a dead link.
4. **Say what it is in six words** before any technology: "multi-warehouse fulfillment & inventory
   platform", "double-entry wallet & ledger engine", "distributed CI/CD execution platform",
   "feature-flag platform with Java SDK".
5. **Only claim technologies actually used in that project.** The [technology × project matrix](./PROJECTS.md#3-technology--project-matrix) is the authority.
6. **State the honest scope.** "Solo project", "built over five weeks", "deployed for demo on
   AWS". Interviewers respect precision; they punish vagueness.
7. **Never present ADVANCED or STRETCH features you have not built.** A résumé bullet describes
   the tagged `v1.0` (or later tag) at the time of sending. Update the résumé when the tag moves.
8. **Keep the technology list on the résumé in sync with what you can defend** —
   [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md) readiness matrix decides, not optimism.

---

## 6. Bullet-writing rules

**Formula:** `Action verb + what you built + technology + the engineering problem + MEASURED result`
— or, when there is no number, `+ "verified by <test name>"`.

Rules:
- Every quantitative claim traces to a benchmark report ([template](./18-projects/templates/benchmark-report.md)) committed in the repo, with environment, setup, load and methodology.
- Until measured, write `<placeholder>` in angle brackets. A placeholder is a to-do, not a guess. **A résumé is never sent with a placeholder or an invented number.**
- A passing test is a legitimate result. "Verified by `TransferConcurrencyTest` (2 × $400 against $500: exactly one commits)" is stronger than a made-up throughput figure.
- No vague scale words ("high-performance", "scalable", "enterprise-grade", "production-ready") unless a measurement or a specific mechanism follows.
- One bullet = one engineering problem. Do not stack five technologies into one sentence.
- Laptop measurements are fine if labelled: "on a 4-vCPU dev box" or "single EC2 t3.small".

### GOOD bullets (honest, with placeholders until measured)

- Built **FlowGrid**, a multi-warehouse fulfillment platform (Java 21, Spring Boot 3, PostgreSQL, Redis, React/TS, AWS); implemented idempotent order creation with `Idempotency-Key` storage and row-level locking for concurrent reservations, verified by an N-thread test in which exactly one of `<N>` concurrent reservations for the last unit succeeds.
- Measured order-creation latency with k6 at `<VUs>` virtual users on `<instance type>`: p95 `<x> ms`, `<y>` req/s, `<z>%` errors (methodology in `docs/PERFORMANCE.md`).
- Designed **LedgerX**'s append-only double-entry ledger with database-enforced immutability (trigger + revoked `UPDATE/DELETE`) and a reconciliation job that verifies every journal sums to zero and every balance equals the sum of its entries; fault-injection tests confirm invariants hold after a crash between steps.
- Implemented ordered pessimistic locking for wallet transfers; a concurrency test with a $500 balance and two simultaneous $400 transfers commits exactly one and never produces a negative balance.
- Built **ForgeCI**'s worker service on a lease-based Redis queue (`BLMOVE` to per-worker processing lists) with heartbeat-driven orphan recovery; integration tests on Testcontainers show a job claimed by a killed worker is re-queued within `<lease seconds>` s and completes exactly once.
- Streamed live build logs over Server-Sent Events with replay-from-sequence on reconnect; measured median queue wait `<x> ms` and `<y>` jobs/min with `<N>` workers on `<environment>`.
- Published **FlagForge**'s Java SDK (`FlagClient`) as a Maven artifact with local evaluation, polling/SSE updates, stale-if-error and offline defaults; contract tests run against the live server in CI. Server-side evaluation p99 `<x> ms` at `<rps>` on `<environment>`.
- Wrote an independent Python reconciliation verifier (`psycopg`, `decimal`, pytest) for **LedgerX** that reads PostgreSQL directly and confirms every journal sums to zero and every balance equals its entries across `<N>` generated transactions, including after injected crashes.
- Built a Python rollout simulator for **FlagForge** that evaluates `<N>` synthetic users against the server and verifies a `<pct>%` rollout lands within `±<tolerance>` with 100% stickiness across `<runs>` runs.

### BAD bullets (fabricated, vague, or unverifiable — never write these)

- ~~"Built a highly scalable microservices platform handling 1M+ requests per day."~~ — never measured, never deployed at that scale, "microservices" is false.
- ~~"Reduced latency by 70% through Redis caching."~~ — compared to what, measured how? Without a before/after report this is invented.
- ~~"Led development of an enterprise inventory system for a logistics company."~~ — it was a solo portfolio project; "led" and "for a company" are lies.
- ~~"Production-grade CI/CD system used by developers."~~ — no users; say "demo deployment".
- ~~"Achieved 99.99% uptime on AWS."~~ — unmonitored for that duration; unmeasurable.
- ~~"Implemented Kafka, Kubernetes and gRPC."~~ — not in any project (ROADMAP §2.4: no Kafka, no Kubernetes).
- ~~"Optimised database queries for maximum performance."~~ — no problem, no number, no test.

---

## 7. GitHub profile pinning strategy

GitHub allows six pinned repositories. Recruiters look at the top row first.

| Pin slot | Repository | Why |
|:-:|---|---|
| 1 | The project matching the roles you are applying for most (default: **FlowGrid**) | First impression: full stack, deployed, screenshots |
| 2 | **LedgerX** | The correctness story; strong README with the $500/$400/$400 test up front |
| 3 | **ForgeCI** | The hardest system; architecture diagram in README |
| 4 | **FlagForge** | SDK + platform; link to the published artifact |
| 5 | This roadmap repository (optional) | Shows discipline and honesty about the rebuild; keep it if it is tidy |
| 6 | Leave empty or a small, clean utility (e.g. the buggy-library debugging exercises) — never a half-finished repo | An abandoned pin damages the other five |

Re-order pins per application wave (Week 25–26: set the order for the role type you are targeting
that week). Every pinned repo must have: a one-line description, topics/tags (`java`,
`spring-boot`, `postgresql`, …), a green CI badge, and a README that passes §8.

Profile README (optional but cheap): one paragraph, links to the four projects, the technologies
you defend, and nothing about "passionate" or "rockstar".

---

## 8. README first-impression checklist

A recruiter gives a README about 30 seconds; an engineer about 3 minutes. Both must succeed.

**First screen (no scrolling)**
- [ ] One-sentence description of what the system does and for whom
- [ ] CI badge (green), tag/version badge, licence badge
- [ ] Architecture diagram or a screenshot/GIF of the running system
- [ ] Stack line: `Java 21 · Spring Boot 3 · PostgreSQL 16 · Redis 7 · React 18 / TS · Docker · AWS`
- [ ] Links: live demo (if up) · docs folder · OpenAPI · this roadmap (optional)

**Within the first scroll**
- [ ] "The hard part" section: the core engineering problem and the test/measurement that proves it (e.g. the concurrency test, the invariant suite, orphan recovery, p99)
- [ ] Quick start: `docker compose up` (or two commands) that actually works from a fresh clone
- [ ] Status: which tier is tagged (`v1.0 — Strong Résumé Version`), what is in the backlog (issues), what is deliberately out of scope

**Further down**
- [ ] Feature list grouped by milestone
- [ ] Links to ARCHITECTURE, API, DATABASE, TESTING, DEPLOYMENT, SECURITY, DESIGN_DECISIONS (and PERFORMANCE only if real)
- [ ] How to run the tests and what the important ones prove
- [ ] Honest context line: solo project, built in `<weeks>` as part of a structured rebuild

**Never**
- Placeholder text, broken links, "TODO" headings, a wall of badges, screenshots of an empty app, claims not backed by code in the repo.

---

## 9. Recruiter-screen 30-second pitch

Template (one breath, ~75 words): **what it is → the hard problem → how you solved it → how you
proved it → what you'd say next if asked.** Practise each with a timer; full method in
[`16-interview-prep/recruiter-screen.md`](./16-interview-prep/recruiter-screen.md).

**FlowGrid**
> "FlowGrid is a multi-warehouse inventory and fulfillment platform I built in Java and Spring Boot with a React dashboard, deployed on AWS. The hard part was making stock reservations safe under concurrent orders — I used row-level locking and idempotency keys, and there is a test where `<N>` threads fight for the last unit and exactly one wins. I know the domain from working in fulfillment operations, so the model reflects how warehouses actually work."

**LedgerX**
> "LedgerX is a digital wallet built on a double-entry ledger. Every transaction is a set of entries that sum to zero, the database refuses updates or deletes on ledger rows, and balances are reconciled against the entries. The hard part was concurrent transfers — with a $500 balance and two simultaneous $400 transfers, exactly one succeeds, never a negative balance — plus fault-injection tests that crash the process between steps and prove the invariants still hold."

**ForgeCI**
> "ForgeCI is a small CI/CD platform: a GitHub webhook creates a build, jobs go onto a Redis queue, worker processes run each job in a throwaway Docker container and stream logs live over SSE. The hard part was failure — workers dying mid-job, containers hanging, webhooks arriving twice — so I built leases with heartbeats, orphan recovery, timeouts and a retry policy that distinguishes application failures from infrastructure failures, all covered by integration tests."

**FlagForge**
> "FlagForge is a feature-flag and progressive-rollout service with its own Java SDK. Flag configs are immutable versions, rules are evaluated by priority, and percentage rollouts use deterministic hashing so a user never flips back and forth. The SDK evaluates locally from a cached snapshot with polling or SSE updates and safe defaults when the server is unreachable. I measured evaluation p99 at `<x> ms` and wrote contract tests between the SDK and the server."

**Follow-up you should invite:** "Happy to go deeper on the concurrency test / the queue design / the SDK's failure modes." Then stop talking.

---

*Decisions for the current application wave are recorded in the portfolio summary of
[`trackers/project-tracker.md`](./trackers/project-tracker.md). Revisit after every project's `v1.0` tag.*
