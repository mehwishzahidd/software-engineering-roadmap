# 🗺️ ROADMAP — The 26-Week Plan

> This file is the **single source of truth** for order, timing and scope.
> Every week folder, topic folder, project spec and checkpoint in this repository is
> derived from the tables below. If two files ever disagree, this one wins.

**Shape:** 3 foundation weeks → 4 flagship projects built **sequentially** → 3 polish/interview weeks = **26 weeks (~6 months)**.
**Load:** **40–50 focused hours per week** when your schedule permits (≈45 target). Some weeks shift toward the active project — ForgeCI weeks run heavier on project time.
**Two-language strategy:** **Python 3.12 = primary DSA / LeetCode / OA / coding-interview language** (≈90% of algorithm practice). **Java 21 (LTS) = primary backend / Spring Boot / flagship-project / résumé-interview language.** Occasional Java DSA reps keep Java collections fluent; nothing is solved twice by default. See [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md) and [`19-python/`](./19-python/).
**The four projects are fixed:** [**FlowGrid**](./18-projects/flowgrid/) · [**LedgerX**](./18-projects/ledgerx/) · [**ForgeCI**](./18-projects/forgeci/) · [**FlagForge**](./18-projects/flagforge/). They are the evidence that my skills are *current*.

---

## Contents

1. [Learning philosophy](#1-learning-philosophy)
2. [Structure review — what was changed and why](#2-structure-review)
3. [Time allocation](#3-time-allocation)
4. [The six phases](#4-the-six-phases)
5. [Week-by-week master table](#5-week-by-week-master-table)
6. [Just-in-time learning map — what is taught when, and why then](#6-just-in-time-learning-map)
7. [DSA progression](#7-dsa-progression)
8. [Project progression and scope control](#8-project-progression)
9. [Checkpoints](#9-checkpoints)
10. [Interview-practice ramp](#10-interview-practice-ramp)
11. [Job-application timeline](#11-job-application-timeline)
12. [Standard weekly rhythm and daily template](#12-standard-weekly-rhythm)
13. [Rules for falling behind](#13-rules-for-falling-behind)

---

## 1. Learning philosophy

```
Learn → Build → Break → Debug → Explain → Review → Build again
```

| Step | What it means in practice | What it is NOT |
|---|---|---|
| **Learn** | Read docs / a book chapter / one focused video on a concept. Take notes in your own words. | Binge-watching a 12-hour course. |
| **Build** | Write the code yourself, from a blank file, the same day — inside the current flagship project wherever possible. | Copy-pasting a tutorial repo. |
| **Break** | Deliberately change something: remove `@Transactional`, kill Redis, send the webhook twice, run two transfers at once. Predict what happens first. | Hoping nothing goes wrong. |
| **Debug** | Use the debugger, logs, stack traces, `EXPLAIN`, `curl -v`, `docker logs`. Find the *root cause*. | Pasting the error into a chatbot and applying the answer blindly. |
| **Explain** | Say it out loud or write 5 sentences: what, why, how, trade-off, example from *your* code. | Memorising a definition. |
| **Review** | Spaced repetition on DSA (Day 0/3/7/14/30), weekly revision block, checkpoint every 4 weeks. | Moving on forever once "done". |
| **Build again** | Re-implement from scratch later (an idempotency filter, a token bucket, a reliable Redis queue, a Dockerfile). | Assuming "I did it once" means "I can do it". |

**The project rule.** This repository teaches me *how* to build FlowGrid, LedgerX, ForgeCI and
FlagForge. It does not build them for me. Each milestone gives me: what to know first, the
requirements, architectural guidance, acceptance criteria, verification tests, debugging
scenarios and interview questions — and then I implement it. Complete code appears only for
very small, isolated concepts. The goal is that "I built this" is simply true.

**The honesty rule.** Nothing here fabricates experience or metrics. Past roles are described
truthfully; current skill is proven by these four projects; only *measured* results (with
environment, setup, load and methodology recorded) may ever become résumé bullets. See
[`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md) and [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md).

---

## 2. Structure review

The structure was audited for missing prerequisites, duplication, ordering, mis-weighted
technologies and gaps against real interview expectations. Findings and fixes:

### 2.1 Prerequisite ordering (fixed)

| Gap | Why it matters | Fix |
|---|---|---|
| **FlowGrid starts Week 4** but needs Spring Boot, JPA, Postgres, auth, Docker, React, AWS and CI within five weeks | Teaching all of that up front would delay the project by two months | Everything is taught **just-in-time inside the FlowGrid weeks** (§6). Week 3 delivers a running Spring Boot REST endpoint so Week 4 starts on the domain, not on "hello world". |
| **HTTP / networking before Spring Boot** | Controllers, status codes, CORS and TLS make no sense without knowing what a request is | HTTP fundamentals in **Week 3**, before the first controller |
| **Command line before Git/Maven/Java** | All three are driven from a terminal | Shell basics on Day 1 of **Week 1**; Linux gets its deep treatment in Week 14, when ForgeCI needs processes, signals and Docker internals |
| **JUnit from the start** | Untested code in Weeks 1–3 produces bad habits and an untested FlowGrid | JUnit 5 in **Week 2**; Mockito, Spring slices and Testcontainers in **Weeks 4–5** because FlowGrid's concurrency test requires a real Postgres |
| **Maven before Spring Boot** | `pom.xml`, lifecycle and dependency scopes underpin every project | Maven in **Week 2** |
| **SQL before JPA** | JPA first hides SQL and produces N+1 disasters | SQL fundamentals in **Week 3**, joins/transactions/indexes in **Weeks 4–6** alongside JPA, so every entity mapping is seen against the SQL it generates |
| **Java memory model + threads before FlowGrid's concurrency milestone** | Race conditions and locks must be understood before reservations are built | Memory model + thread basics in **Week 5** (first concurrency test); deep Java concurrency in **Week 15** for ForgeCI's worker pool |
| **Recursion before trees/backtracking/DP** | Every tree, DFS, backtracking and DP problem is recursion | Recursion block in **Week 8**, before trees |
| **JavaScript → TypeScript → React** | Skipping JS produces cargo-cult React | **JS in Week 6, TS + React in Week 7**, used for FlowGrid's dashboard in Week 7 |
| **Docker in two doses** | Postgres must run in Week 4; Docker *internals* only matter for ForgeCI | Minimal Compose in **Week 4**; Dockerfiles/multi-stage in **Week 5**; images, layers, networking, Docker Engine API in **Week 14** |
| **AWS before the first deployment** | FlowGrid deploys in Week 8 | IAM/EC2/RDS/S3/CloudWatch in **Weeks 7–8**; deployment repeats for every project so the skill compounds |
| **CI early** | Every project should run `mvn verify` on every push from its first PR | Basic GitHub Actions in **Week 4**; full pipelines (image build, deploy) in Week 8 and Week 18 |

### 2.2 Missing topics (added)

| Missing | Added as |
|---|---|
| Python as a first-class interview language and project tooling language | [`19-python/`](./19-python/) — core (W1), interview toolkit + pitfalls (W2), testing/scripting (W3), used every week for DSA and for each project's Python component; [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md) |
| C++ | [`20-cpp-basics/`](./20-cpp-basics/) (Week 21, ≈4 h) |
| Reading unfamiliar codebases / debugging existing apps | [`21-debugging-code-reading/`](./21-debugging-code-reading/) — a buggy Maven project with failing tests, drills from Week 13, OA simulations from Week 17 |
| Database migrations, pagination, idempotency, optimistic/pessimistic locking, outbox, reliable queues | Inside project milestones where they are needed for real (FlowGrid M2, LedgerX M2–M4, ForgeCI M2/M4) |
| Double-entry accounting | [`18-projects/ledgerx/`](./18-projects/ledgerx/) teaches the accounting model before M1 |
| Client-library (SDK) design | [`18-projects/flagforge/`](./18-projects/flagforge/) M3 |
| Failure engineering as a discipline | Every project has a `failure-engineering.md`: reproduce → observe → inspect logs → diagnose → fix/design around → regression test |
| Real benchmarking methodology | Every project has a `docs-and-resume.md` with a measurement protocol; [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md) explains how measured results become bullets |
| Behavioral interviews and the career-gap question | [`16-interview-prep/behavioral.md`](./16-interview-prep/behavioral.md), story bank from Week 8 |

### 2.3 Duplication (resolved)

| Overlap | Resolution |
|---|---|
| `RESUME_TECH_DEFENSE.md` vs `17-resume-tech-defense/` | Root = method, readiness matrix, drill routine. Folder = one deep file per technology. |
| `PROJECTS.md` vs `18-projects/` | Root = overview, order, scope tiers, timeline. Folder = full specs (5 files per project). |
| HTTP in `06-rest-apis/` vs `14-cs-fundamentals/` | `14-cs-fundamentals/networking.md` owns TCP/IP/DNS/TLS; `06-rest-apis/` owns HTTP semantics for API design. |
| Redis under databases vs caching in system design | `04-sql-databases/redis.md` owns mechanics; `15-system-design/caching.md` owns strategy. |
| Auth in Spring vs React | `05-spring-boot/05-security-jwt.md` owns server side; `08-react/04-api-integration-auth.md` owns client side. |
| Queues in ForgeCI vs system design | ForgeCI spec owns the implementation; `15-system-design/scalability.md` owns the concept and links to it. |

### 2.4 Weighting (fixed)

- **Project engineering is the largest block (25–30 h/week)** because the projects are the evidence.
- **DSA is continuous (6–8 h/week)** from Week 1 to Week 26 — never "done".
- **CSS ≈ 3 h total, C++ ≈ 4 h, MySQL ≈ 2 h as a diff against Postgres, AWS capped at IAM/EC2/S3/RDS/CloudWatch (+ SQS considered for ForgeCI, ElastiCache only if cheaper than Redis-on-EC2).**
- No Kafka, no Kubernetes, no microservices-for-their-own-sake. Each project has one technical identity.

### 2.5 Gaps between learning and interview expectations (closed)

| Interview expectation | Trained by |
|---|---|
| Solve 1–2 problems in 70–120 min on HackerRank/CodeSignal-style platforms | [`OA_PREP.md`](./OA_PREP.md), simulations weekly from Week 17 |
| Talk while coding; clarify; take hints | [`16-interview-prep/coding-interview-method.md`](./16-interview-prep/coding-interview-method.md), weekly from Week 5 |
| 10–15 minute project deep-dive without notes | `interview-questions.md` in each project + [`16-interview-prep/project-deep-dive.md`](./16-interview-prep/project-deep-dive.md), rehearsed after every project |
| Résumé-driven probing | [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md) + [`17-resume-tech-defense/`](./17-resume-tech-defense/) weekly from Week 8 |
| Debug an unfamiliar repository | [`21-debugging-code-reading/`](./21-debugging-code-reading/) from Week 13 |
| Junior-level system design | [`15-system-design/`](./15-system-design/) — the four projects *are* the design problems |

---

## 3. Time allocation

Weekly target ≈ 45 h (40–50). Not rigid: the active project pulls hours.

| Block | Hours / week | Notes |
|---|---:|---|
| **Project engineering** | 25–30 (ForgeCI: 30–35) | Building the current flagship project, including its tests, docs and deployment |
| **DSA / NeetCode (Python)** | 6–8 | New problems + spaced reviews in Python; ≈1 problem/week re-done in Java for collections fluency; never skipped |
| **Technology / CS learning** | 6–8 | Just-in-time for the current milestone (§6) |
| **Interview / review / documentation** | 3–5 | Explaining out loud, mocks, résumé defense, weekly retro, tracker updates |

Approximate 26-week totals: **projects ≈ 700 h · DSA ≈ 180 h · learning ≈ 170 h · interview/review ≈ 100 h ≈ 1,150 h**.

Project hour targets: FlowGrid **125–150 h** · LedgerX **125–150 h** · ForgeCI **170–200 h** · FlagForge **100–120 h** · Polish **≈ 75 h**.

---

## 4. The six phases

| Phase | Weeks | Theme | Project | Exit gate |
|---|---|---|---|---|
| **0 · Foundation** | 1–3 | **Python for interviews** (core + stdlib toolkit) in parallel with Java, Git, OOP, JUnit, Maven, DSA basics, SQL, HTTP, Spring Boot intro | Exercises only (not portfolio) | — |
| **1 · FlowGrid** | 4–8 | Spring Boot properly, JPA + SQL, transactions & locking, idempotency, Redis, React/TS dashboard, Docker, CI, first AWS deploy | **FlowGrid** | [CP-4](./checkpoints/checkpoint-04.md), [CP-8](./checkpoints/checkpoint-08.md) |
| **2 · LedgerX** | 9–13 | Correctness: double-entry ledger, isolation levels, pessimistic vs optimistic locking, idempotency done properly, reconciliation, failure injection, invariant testing | **LedgerX** | [CP-12](./checkpoints/checkpoint-12.md) |
| **3 · ForgeCI** | 14–19 | Linux & Docker internals, queues & workers, Java concurrency, webhooks, container execution, live logs (SSE), timeouts/cancel/retry, failure recovery, DAG scheduling | **ForgeCI** | [CP-16](./checkpoints/checkpoint-16.md) |
| **4 · FlagForge** | 20–23 | Caching & low latency, deterministic bucketing, rule engines, SDK/client-library design, configuration propagation, fallback/resilience, system design | **FlagForge** | [CP-20](./checkpoints/checkpoint-20.md) |
| **5 · Polish & interviews** | 24–26 | Bug fixing, refactoring, load testing, security review, docs, diagrams, demos, résumé bullets, mock loops, OA simulations, applications | All four | [CP-24](./checkpoints/checkpoint-24.md) |

DSA runs through **all phases** in **Python**. Java stays the language for every project and for the résumé/backend interview track. Every project also ships at least one **Python engineering component** (generators, simulators, verifiers, analysis tooling — see §8).

---

## 5. Week-by-week master table

"DSA new" = new problems that week; spaced reviews are in addition. Hours are approximate targets.

| Wk | Phase | Main objective | Learning (just-in-time) | Project milestone | DSA (new) | Interview / other | Hours P / L / D / I |
|---:|---|---|---|---|---|---|---|
| [1](./weeks/week-01/) | 0 | Write Python and Java from a blank file; version it | **Python core for interviews** (types, strings, lists/tuples/sets/dicts, slicing, comprehensions, functions, `sorted`/keys, `enumerate`/`zip`); terminal & Linux basics; JDK/IDE, Java syntax, control flow, methods, arrays, Strings; Git core commands, `.gitignore`, GitHub | Foundation exercises (Python + Java katas) | Big-O + Arrays (Python) — **6** | 60-second "about me" draft | 15 / 15 / 7 / 3 |
| [2](./weeks/week-02/) | 0 | Model problems with classes; test and build them | **Python interview toolkit** (`Counter`, `defaultdict`, `deque`, `heapq`, `bisect`, `itertools`, classes/dataclasses, pitfalls: mutable defaults, aliasing, copies, recursion limit); Java OOP (classes, interfaces, polymorphism, `equals`/`hashCode`), exceptions, Collections & generics, **JUnit 5**, **Maven**, Git branches/merge | Foundation exercises (an inventory/ledger kata with tests — throwaway) | Arrays & Hashing — **8** | — | 18 / 15 / 7 / 3 |
| [3](./weeks/week-03/) | 0 | Modern Java; SQL fundamentals; first HTTP endpoint | Python: `pytest`, type hints, scripting (`argparse`, `pathlib`, `csv`/`json`, `requests`); Lambdas, streams, records, `Optional`, IDE debugger; SQL (`SELECT`…`LIMIT`, `INSERT/UPDATE/DELETE`, PostgreSQL + `psql`); HTTP/REST/JSON; **Spring Boot intro** (DI, `@RestController`, layers) | Foundation: a tiny Spring Boot API with 2 endpoints + tests (throwaway) | Two Pointers + Prefix Sums — **8** | — | 20 / 15 / 7 / 3 |
| [4](./weeks/week-04/) | 1 | **FlowGrid M1** — domain, persistence, auth | Spring Data JPA, entities/relationships, Flyway, DTOs, Bean Validation, `@ControllerAdvice`/ProblemDetail, pagination, OpenAPI; SQL joins & aggregation; Docker Compose for Postgres; basic GitHub Actions; Spring Security + JWT | Warehouses, products, SKUs, inventory levels, adjustments + audit events, users/roles, OpenAPI, CI | Sliding Window — **8** | — | 26 / 8 / 7 / 4 · **CP-4** |
| [5](./weeks/week-05/) | 1 | **FlowGrid M2** — idempotent orders, concurrent reservations | Transactions & ACID, isolation levels, `SELECT … FOR UPDATE` vs `@Version`, deadlocks; Java memory model + threads (basics); Mockito, `@WebMvcTest`/`@DataJpaTest`, Testcontainers; Dockerfile multi-stage; Git rebase/PR workflow | Orders, `Idempotency-Key`, reservation state machine, cancellation, **N-threads-one-unit test** | Stack & Queue — **8** | Think-aloud practice begins (weekly) | 28 / 7 / 7 / 3 |
| [6](./weeks/week-06/) | 1 | **FlowGrid M3** — allocation, pick/pack/ship, Redis | Schema design & normalization, indexes & `EXPLAIN`, CTEs/window functions; Redis fundamentals (types, TTL, cache-aside, invalidation); state machines; **JavaScript** core + async | Deterministic multi-warehouse allocation, picking & packing workflows, shipments, partial fulfillment, Redis catalog cache | Binary Search — **8** | — | 28 / 7 / 7 / 3 |
| [7](./weeks/week-07/) | 1 | **FlowGrid M4** — operations dashboard, returns, transfers | **TypeScript**, **React** (components, hooks, forms, router, API client, auth on the client), minimal HTML/CSS; AWS IAM + EC2 | React/TS dashboard, search/filter/sort/pagination, returns, stock transfers, scheduled low-stock alerts | Linked Lists — **8** | Résumé-defense drills begin (Java, Git, SQL) | 28 / 7 / 7 / 3 |
| [8](./weeks/week-08/) | 1 | **FlowGrid M5** — deploy, document, measure | AWS RDS, S3, CloudWatch, security groups; CI/CD pipeline (build → test → image → deploy); k6 load testing; structured logging | AWS deployment, docs set, k6 baseline, failure-engineering exercises, **v1.0 (Strong Résumé Version)** | Recursion + review — **6** | Story bank starts; FlowGrid deep-dive rehearsal | 28 / 6 / 6 / 5 · **CP-8** |
| [9](./weeks/week-09/) | 2 | **LedgerX M1** — double-entry ledger core | Double-entry accounting for engineers; `BigDecimal`/money; append-only tables, DB constraints & triggers, MVCC; DB internals | Accounts, wallets, immutable ledger entries, journal transactions, simulated deposits/withdrawals, balance derivation, auth | Trees — **8** | Weekly think-aloud continues | 26 / 7 / 7 / 4 |
| [10](./weeks/week-10/) | 2 | **LedgerX M2** — idempotent, concurrent transfers | Idempotency done properly (key + request hash + stored response), lock ordering, retry semantics, `@Transactional` propagation & pitfalls, isolation-level experiments | Transfers, idempotency store, ordered pessimistic locking (+ optimistic comparison), **$500/$400/$400 test** | BST + Tries — **8** | Mock interview #1 | 28 / 6 / 7 / 4 |
| [11](./weeks/week-11/) | 2 | **LedgerX M3** — states, reversals, history, audit | State machines, compensating entries, cursor pagination, audit design, transactional outbox pattern | Transaction states, refunds/reversals as compensating entries, payment requests, history API, audit log, outbox | Heap / PriorityQueue — **8** | — | 28 / 6 / 7 / 4 |
| [12](./weeks/week-12/) | 2 | **LedgerX M4** — reconciliation, failure injection, invariants | Failure injection techniques, invariant/property-style tests, crash-consistency reasoning; Python tooling patterns (`psycopg`, `decimal`, `pytest` fixtures) | Reconciliation job, crash-between-steps tests, retry-after-crash tests, invariant suite, throughput measurement, **Python independent reconciliation verifier + data generator** | Backtracking — **8** | — | 26 / 8 / 7 / 4 · **CP-12** |
| [13](./weeks/week-13/) | 2 | **LedgerX M5** — deploy, document, advanced features | Scheduled work, rule engines (basic), security review checklist; codebase-reading method | AWS deploy, docs, scheduled payments + basic risk rules (advanced), **v1.0** | Graphs BFS/DFS — **8** | Unfamiliar-code drill #1; LedgerX deep-dive rehearsal | 26 / 6 / 7 / 6 |
| [14](./weeks/week-14/) | 3 | **ForgeCI M1** — GitHub integration, idempotent webhooks | **Linux deep dive** (processes, signals, permissions, Bash); **Docker internals** (images, layers, networking, Engine API); OS fundamentals; GitHub OAuth/Apps, webhooks & HMAC | Repositories, GitHub OAuth, webhook receiver with signature verification + delivery-ID dedupe, build creation, minimal pipeline config | Topological Sort + Union-Find — **8** | Mock | 30 / 8 / 6 / 3 |
| [15](./weeks/week-15/) | 3 | **ForgeCI M2** — job queue + Docker-executing workers | **Java concurrency deep** (executors, `CompletableFuture`, locks, atomics, virtual threads); producer/consumer, reliable Redis queues (`BLMOVE`/Streams), leases; networking fundamentals | Redis job queue, worker service, Docker-based step execution via Engine API, workspace + cleanup | 1-D DP — **8** | Mock | 32 / 7 / 6 / 3 |
| [16](./weeks/week-16/) | 3 | **ForgeCI M3** — live logs and UI | SSE vs WebSockets, Redis pub/sub, backpressure basics; React data-heavy views | Log chunk pipeline, SSE streaming endpoint, React build list + live log view, job status | 1-D DP + Intervals — **8** | Mock | 32 / 6 / 6 / 3 · **CP-16** |
| [17](./weeks/week-17/) | 3 | **ForgeCI M4** — timeouts, cancellation, retries, limits, health | Failure taxonomy (app vs infra), heartbeats & leases, semaphores/limits in Redis, graceful shutdown | Timeouts, cancellation (queued + running), retry policy, worker heartbeats, orphan recovery, per-project concurrency limits | Greedy + 2-D DP — **8** | **OA simulation #1**; mock | 32 / 6 / 6 / 4 |
| [18](./weeks/week-18/) | 3 | **ForgeCI M5** — failure recovery, tests, full stack, benchmarks | Testcontainers with Redis + Postgres, chaos-style tests, Compose multi-worker stacks, CI/CD with image publishing | Failure-engineering suite, integration + concurrency tests, Compose (api, N workers, postgres, redis, ui), CI/CD, queue latency/throughput measurement, **v1.0** | 2-D DP — **7** | OA sim #2; mock | 32 / 5 / 6 / 4 |
| [19](./weeks/week-19/) | 3 | **ForgeCI M6** — DAG pipelines + AWS | DAGs & topological scheduling (from DSA to production), Docker-socket security, AWS deployment for multi-service apps (SQS considered) | Job dependencies (DAG), AWS deploy, docs, ForgeCI deep-dive rehearsal (**Advanced Version**) | Advanced Graphs + Bit Manipulation — **7** | OA sim #3; mock | 32 / 5 / 6 / 4 |
| [20](./weeks/week-20/) | 4 | **FlagForge M1** — model, versioning, audit | Configuration versioning, immutable snapshots, multi-tenant modelling; **system design fundamentals** | Orgs/projects/environments/flags, versioned configs with rollback, rules, audit history, auth/authz, admin API | Mixed review (weakest) — **6** | OA sim #4; mock | 26 / 8 / 7 / 4 · **CP-20** |
| [21](./weeks/week-21/) | 4 | **FlagForge M2** — evaluation engine + cache | Hashing for deterministic bucketing, rule-engine design, Redis snapshot caching, hot-path latency; **C++ basics** (≈4 h) | Evaluation engine (priority, attribute targeting, percentage rollout, defaults), server-side eval API, Redis config snapshots, SDK keys, p99 latency measurement | Mixed review — **6** | Mock | 28 / 7 / 7 / 3 |
| [22](./weeks/week-22/) | 4 | **FlagForge M3** — the Java SDK | Client-library/API design, semantic versioning, polling vs streaming, resilience (timeouts, stale-if-error, defaults), publishing a Maven artifact | Java SDK (local cache, polling, local evaluation, fallbacks), sample app, SDK tests, then SSE streaming updates | Mixed review — **6** | OA sim #5; system design mock #1 | 28 / 6 / 7 / 4 |
| [23](./weeks/week-23/) | 4 | **FlagForge M4** — dashboard, propagation, deploy | Pub/sub invalidation, cache stampede protection, security review, AWS deploy repetition | React dashboard, propagation (pub/sub → SSE), Docker/AWS/CI, docs, benchmarks, failure exercises, **v1.0** | Timed mixed — **6** | OA sim #6; FlagForge deep-dive rehearsal | 28 / 5 / 7 / 5 |
| [24](./weeks/week-24/) | 5 | Polish I — bugs, refactors, tests, security | Security review checklist (OWASP API Top 10), refactoring techniques | All four: bug-fix sprint, refactor, test gaps, security pass, README/ARCHITECTURE/API/DATABASE docs | Timed mixed — **6** + OA | 2 mocks; OA sim #7–8 | 22 / 4 / 6 / 13 · **CP-24** |
| [25](./weeks/week-25/) | 5 | Polish II — performance, load tests, diagrams, demos | Load-testing methodology, profiling basics, diagramming | k6/JMH runs recorded with methodology, PERFORMANCE.md, ERDs, architecture diagrams, screenshots/demos, GitHub issues/milestones cleanup | Weakest patterns — **5** | 2 mocks; full loop; applications at volume | 20 / 3 / 6 / 16 |
| [26](./weeks/week-26/) | 5 | Final loop + résumé + maintenance plan | — | Résumé bullets from measured results, [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md) decisions, final deploy check, teardown/cost review | Maintenance — **4** | Final mock loop; hand-off to [maintenance plan](./16-interview-prep/maintenance-plan.md) | 12 / 2 / 5 / 20 |

<sub>Hours column = Project / Learning / DSA / Interview-review. Foundation weeks front-load learning; ForgeCI weeks front-load project time.</sub>

**Totals:** ≈ **185 new DSA problems** + spaced reviews · **4 flagship projects** · **6 checkpoints** · **≥ 14 mock interviews** · **≥ 8 OA simulations**.

---

## 6. Just-in-time learning map

Every topic folder is taught **the week a milestone first needs it** and revisited when a later project stresses it harder. Nothing is taught "for later".

| Topic folder | First taught | Why then | Deepened |
|---|---|---|---|
| [`01-java/`](./01-java/) | W1–3 (syntax → OOP → collections → modern) | Foundation | W5 memory/threads (FlowGrid reservations), W15 concurrency (ForgeCI workers) |
| [`02-git/`](./02-git/) | W1 core, W2 branches | Everything is versioned from Day 1 | W5 rebase/PR review workflow; W14 webhooks & GitHub API |
| [`03-dsa/`](./03-dsa/) | W1 | Continuous | Pattern order in §7 |
| [`04-sql-databases/`](./04-sql-databases/) | W3 basics | Before JPA | W4 joins, W5 transactions/isolation, W6 schema/indexes/`EXPLAIN`, W9 internals & constraints (LedgerX), W6/W15/W21 Redis |
| [`05-spring-boot/`](./05-spring-boot/) | W3 intro | First endpoint before FlowGrid | W4 JPA/validation/errors/security, W5 transactions/testing, W10 propagation pitfalls, W13/W17 scheduling & caching |
| [`06-rest-apis/`](./06-rest-apis/) | W3 | Before designing FlowGrid's API | W4 OpenAPI, W5 idempotency keys, W11 cursor pagination, W22 SDK-facing API design |
| [`07-javascript-typescript/`](./07-javascript-typescript/) | W6 JS, W7 TS | Before FlowGrid's dashboard | W16 (ForgeCI live UI), W23 (FlagForge dashboard) |
| [`08-react/`](./08-react/) | W7 | FlowGrid M4 | W16 streaming views, W23 forms-heavy admin |
| [`09-testing/`](./09-testing/) | W2 JUnit | Foundation | W5 Mockito/slices/Testcontainers, W12 failure injection & invariants, W18 chaos-style integration tests |
| [`10-linux/`](./10-linux/) | W1 basics | Terminal | W14 deep dive (ForgeCI executes processes in containers) |
| [`11-docker/`](./11-docker/) | W4 Compose for Postgres | Run a DB | W5 Dockerfiles, W14 internals + Engine API, W18 multi-worker Compose |
| [`12-aws/`](./12-aws/) | W7–8 | FlowGrid deploy | Repeated W13, W19, W23 — each deploy faster than the last |
| [`13-cicd/`](./13-cicd/) | W4 `mvn verify` | Every PR tested | W8 full pipeline, W18 image publishing + multi-service deploy |
| [`14-cs-fundamentals/`](./14-cs-fundamentals/) | W5 memory, W9 DB internals | As projects need them | W14 OS, W15 networking/concurrency, W20+ interview sweep |
| [`15-system-design/`](./15-system-design/) | W20 | After three real systems exist | W22 mock, W24–26 loops — the projects are the case studies |
| [`16-interview-prep/`](./16-interview-prep/) | W5 think-aloud, W8 story bank | Ramp (§10) | W10 first mock; weekly from W14 |
| [`17-resume-tech-defense/`](./17-resume-tech-defense/) | W7 | After FlowGrid proves Java/SQL/Git | All techs by W23 |
| [`19-python/`](./19-python/) | W1–3 (core, interview toolkit, pytest/scripting) | DSA starts in Python on Day 1 | Daily via DSA; project Python components W7, W12, W18, W23 |
| [`20-cpp-basics/`](./20-cpp-basics/) | W21 (≈4 h) | Lighter learning week | — |
| [`21-debugging-code-reading/`](./21-debugging-code-reading/) | W13 | Before ForgeCI (reading GitHub API/Docker client code) | OA sims W17+ |

---

## 7. DSA progression

Full pattern guides in [`03-dsa/`](./03-dsa/) — **Python templates first**, with a short "the same in Java" section per pattern. Tracking in [`trackers/dsa-tracker.md`](./trackers/dsa-tracker.md). 6–8 h/week: roughly 60% new problems, 40% spaced reviews. **Java DSA reps:** one already-solved problem per week re-implemented in Java (HashMap/ArrayDeque/PriorityQueue/Comparator, BFS/DFS, trees, recursion) — logged in the tracker, never counted as "new".

| Order | Pattern | Weeks | Guide |
|---:|---|---|---|
| 0 | Big-O & complexity analysis | 1 | [`00-big-o.md`](./03-dsa/00-big-o.md) |
| 1 | Arrays & Strings | 1–2 | [`01-arrays-strings.md`](./03-dsa/01-arrays-strings.md) |
| 2 | HashMap / HashSet | 2 | [`02-hashing.md`](./03-dsa/02-hashing.md) |
| 3 | Two Pointers | 3 | [`03-two-pointers.md`](./03-dsa/03-two-pointers.md) |
| 4 | Prefix Sums | 3 | [`04-prefix-sums.md`](./03-dsa/04-prefix-sums.md) |
| 5 | Sliding Window | 4 | [`05-sliding-window.md`](./03-dsa/05-sliding-window.md) |
| 6 | Stack & Queue (incl. monotonic stack) | 5 | [`06-stack-queue.md`](./03-dsa/06-stack-queue.md) |
| 7 | Binary Search (incl. search on answer) | 6 | [`07-binary-search.md`](./03-dsa/07-binary-search.md) |
| 8 | Linked Lists | 7 | [`08-linked-lists.md`](./03-dsa/08-linked-lists.md) |
| 9 | Recursion | 8 | [`09-recursion.md`](./03-dsa/09-recursion.md) |
| 10 | Trees (traversals, DFS/BFS) | 9 | [`10-trees.md`](./03-dsa/10-trees.md) |
| 11 | Binary Search Trees | 10 | [`11-bst.md`](./03-dsa/11-bst.md) |
| 12 | Tries | 10 | [`12-tries.md`](./03-dsa/12-tries.md) |
| 13 | Heap / PriorityQueue | 11 | [`13-heap-priority-queue.md`](./03-dsa/13-heap-priority-queue.md) |
| 14 | Backtracking | 12 | [`14-backtracking.md`](./03-dsa/14-backtracking.md) |
| 15 | Graphs: BFS / DFS | 13 | [`15-graphs-bfs-dfs.md`](./03-dsa/15-graphs-bfs-dfs.md) |
| 16 | Topological Sort | 14 | [`16-topological-sort.md`](./03-dsa/16-topological-sort.md) |
| 17 | Union-Find | 14 | [`17-union-find.md`](./03-dsa/17-union-find.md) |
| 18 | Dynamic Programming 1-D | 15–16 | [`18-dp-1d.md`](./03-dsa/18-dp-1d.md) |
| 19 | Intervals | 16 | [`19-intervals.md`](./03-dsa/19-intervals.md) |
| 20 | Greedy | 17 | [`20-greedy.md`](./03-dsa/20-greedy.md) |
| 21 | Dynamic Programming 2-D | 17–18 | [`21-dp-2d.md`](./03-dsa/21-dp-2d.md) |
| 22 | Advanced Graphs (Dijkstra, MST awareness) | 19 | [`22-advanced-graphs.md`](./03-dsa/22-advanced-graphs.md) |
| 23 | Bit Manipulation basics | 19 | [`23-bit-manipulation.md`](./03-dsa/23-bit-manipulation.md) |
| — | Mixed / timed / OA | 20–26 | [`OA_PREP.md`](./OA_PREP.md) |

Topological sort lands in Week 14 on purpose: ForgeCI's DAG milestone (Week 19) uses it for real.

**Statuses:** `Not Started` → `Attempted` → `Solved With Solution` / `Solved With Hint` / `Solved Independently` → `Needs Review` → `Mastered`.
**Review cycle:** Day 0 → 3 → 7 → 14 → 30. `Mastered` only after an independent, clean, timed solve at the Day-30 review.

---

## 8. Project progression

Full specs in [`18-projects/`](./18-projects/) (each project: `README.md` spec, `milestones.md`, `failure-engineering.md`, `interview-questions.md`, `docs-and-resume.md`). Overview in [`PROJECTS.md`](./PROJECTS.md); résumé strategy in [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md).

| # | Project | Weeks | Hours | Technical identity | Builds on |
|---|---|---|---|---|---|
| 1 | [**FlowGrid**](./18-projects/flowgrid/) — multi-warehouse fulfillment & inventory platform | 4–8 | 125–150 | Domain modelling, inventory state machine, reservation concurrency, idempotent orders, deterministic allocation, Redis caching, full stack, first AWS deploy | Foundation |
| 2 | [**LedgerX**](./18-projects/ledgerx/) — digital wallet & double-entry ledger engine | 9–13 | 125–150 | Correctness: immutable ledger, isolation & locking, idempotency, compensating entries, reconciliation, failure injection, invariants | FlowGrid's Spring/JPA/testing/deploy skills — spends its time on transactions, not controllers |
| 3 | [**ForgeCI**](./18-projects/forgeci/) — distributed CI/CD execution platform | 14–19 | 170–200 | Queues, worker pools, Docker-isolated execution, webhooks, live logs, timeouts/cancel/retry, failure recovery, DAG scheduling | Both previous projects — spends its time on async execution and recovery |
| 4 | [**FlagForge**](./18-projects/flagforge/) — feature-flag & progressive-rollout platform with SDK | 20–23 | 100–120 | Low-latency evaluation, deterministic bucketing, rule engine, SDK design, config propagation, fallback behaviour, versioning | All previous — spends its time on caching, latency and client-library design |

### Python engineering component (every project)

Java/Spring Boot remains every project's backend. Each project also ships **at least one tested, documented Python component** that serves a real purpose — this is where practical Python experience beyond DSA comes from.

| Project | Python component(s) | Milestone |
|---|---|---|
| FlowGrid | `tools/` — synthetic inventory & order generator (realistic SKUs, warehouses, order mix); load/simulation harness that drives the API concurrently and reports latency/contention | M4–M5 (W7–8) |
| LedgerX | `tools/` — **independent** reconciliation verifier (reads Postgres directly, proves every journal sums to zero and balances match entries, without trusting the Java code); transaction-data generator; consistency checker used in failure exercises | M4 (W12) |
| ForgeCI | `tools/` — test-repository generator (creates Git repos with `.forgeci.yml` variants incl. failing/slow/timeout cases); worker/load simulator (fires webhooks, measures queue wait); build-result/log analysis tooling | M5 (W18) |
| FlagForge | `tools/` + `sdk-python/` — rollout-distribution simulator (proves 10% ± tolerance and stickiness over N users); a minimal **Python SDK / test client** exercising the SDK API and contract; configuration validation tool (lints rule sets for overlaps/unreachable rules) | M3–M4 (W22–23) |

Rules: each tool has a README, `pytest` tests, type hints, a `pyproject.toml`/`requirements.txt`, and is listed in the project's TESTING.md or PERFORMANCE.md where it is used. Only add tooling that is actually used by a test, benchmark or failure exercise.

### Scope tiers (every project)

| Tier | Meaning | Rule |
|---|---|---|
| **MVP** | The smallest end-to-end version that exercises the project's core engineering problem | Reached mid-phase |
| **Strong Résumé Version** | **The primary target.** Core architecture, engineering depth, tests, deployment, documentation | Reached by the phase's last week; declared `v1.0` |
| **Advanced Version** | One or two hard features that deepen the story (split fulfillment, scheduled payments/risk rules, DAG pipelines, streaming propagation) | Only after Strong Résumé Version is tagged |
| **Optional Stretch** | Ideas for later; never block completion | Polish weeks or after Week 26 |

**If the schedule slips, preserve — in this order — core architecture, engineering depth, testing, deployment, documentation. Cut features, never quality.** Four well-scoped projects beat four enormous unfinished systems.

### Milestone cadence

Each milestone follows the same loop: **know first → requirements → architecture guidance → acceptance criteria → I implement → verification tests → debugging scenarios → interview questions**. One milestone ≈ one week ≈ one GitHub milestone with issues ≈ one or more PRs.

---

## 9. Checkpoints

Pass/fail gates every four weeks in [`checkpoints/`](./checkpoints/): timed coding, knowledge questions, a practical task, explain-out-loud prompts, a project review, scoring thresholds and a remediation plan.

| Checkpoint | Week | Gate question |
|---|---|---|
| [CP-4](./checkpoints/checkpoint-04.md) | 4 | Can I solve Easies in Python fluently, write tested Java, basic SQL and a secured Spring Boot CRUD API (FlowGrid M1) without a tutorial? |
| [CP-8](./checkpoints/checkpoint-08.md) | 8 | Is FlowGrid's Strong Résumé Version deployed, tested and explainable? Do I solve Easies reliably and first Mediums? |
| [CP-12](./checkpoints/checkpoint-12.md) | 12 | Can I reason about and prove transactional correctness, locking and idempotency (LedgerX M4)? |
| [CP-16](./checkpoints/checkpoint-16.md) | 16 | Do I have a working queue → worker → container → live-log pipeline (ForgeCI M3)? Standard Mediums in ≤ 30 min? |
| [CP-20](./checkpoints/checkpoint-20.md) | 20 | Is ForgeCI shipped with failure recovery? Can I design and explain a system at junior level? |
| [CP-24](./checkpoints/checkpoint-24.md) | 24 | Would I pass an internship/junior loop today, including a 15-minute project deep-dive without notes? |

**Failure rule:** a failed checkpoint does not stop the calendar. Remediation happens inside the next two weeks' review blocks; two consecutive failures in the same area → freeze *new* features in that area for one week and remediate. Weeks 24–26 absorb slippage.

---

## 10. Interview-practice ramp

Two distinct tracks, never mixed:

| Track | Language | Covers | Material |
|---|---|---|---|
| **A · Coding interview** | **Python** | LeetCode/NeetCode, OAs (Amazon-style, HackerRank, CodeSignal), timed problems, explaining solutions, Big-O, debugging algorithm code | [`03-dsa/`](./03-dsa/), [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md), [`OA_PREP.md`](./OA_PREP.md), [`16-interview-prep/coding-interview-method.md`](./16-interview-prep/coding-interview-method.md) |
| **B · Software-engineering / résumé interview** | Java, Spring Boot, SQL, TS/React, Docker, Redis, AWS, CI/CD | Project deep-dives, architecture, concurrency, debugging, testing, system design, technology trade-offs, résumé probing | [`17-resume-tech-defense/`](./17-resume-tech-defense/), [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md), each project's `interview-questions.md`, [`15-system-design/`](./15-system-design/) |

OA simulations that include "existing codebase / failing tests" tasks use the Java [`buggy-library`](./21-debugging-code-reading/exercises/buggy-library/) project (Track B skill inside an OA) while the algorithm problems are solved in Python.

| Weeks | Activity |
|---|---|
| 1–4 | Explain every DSA solution out loud after solving it. Draft "about me". |
| 5–13 | Think-aloud session weekly (one Medium, recorded, scored against [`INTERVIEW_CHECKLIST.md`](./INTERVIEW_CHECKLIST.md)). Résumé-defense drills from Week 7 (3 questions/week). Story bank from Week 8. Mock #1 in Week 10. Project deep-dive rehearsal at the end of each project (W8, W13, W19, W23). |
| 14–23 | **Weekly mock interview** (peer / Pramp-style / AI interviewer with strict rules). Unfamiliar-code drills from Week 13. OA simulations from Week 17 (70–120 min). System design mock Week 22. |
| 24–26 | 2 mocks/week, 2 OA simulations/week, full loops, repository-modification drills, recruiter-screen rehearsal, all-tech résumé grill. |

---

## 11. Job-application timeline

Do **not** wait until Week 26. Objective criteria in [`JOB_READINESS.md`](./JOB_READINESS.md).

| Stage | Earliest typical week | Why then |
|---|---|---|
| Too early | 1–7 | No deployed project yet |
| Early application stage | ~8 | FlowGrid v1.0 is deployed and explainable; long-timeline internships, referrals |
| Internship-ready | ~12–13 | Two projects; transactions/concurrency story; Easies solid, Mediums emerging |
| OA-ready | ~16–17 | Mediums ≤ 30 min; OA simulations passing |
| Junior-role-ready | ~19–20 | Three projects incl. ForgeCI; weekly mocks scoring ≥ 3/4 |
| Interview-ready | ~24 | Full loops; 15-minute deep dives without notes |

---

## 12. Standard weekly rhythm

≈ 45 hours. Adapt days to your life; keep the ratios. One lighter day and one review day are mandatory for sustainability.

| Day | Hours | Blocks |
|---|---:|---|
| **Mon** | 8 | Learning (2) · Project (4.5) · DSA (1.5) |
| **Tue** | 8 | Project (5) · DSA (2) · Interview/explain (1) |
| **Wed** | 8 | Learning (2) · Project (4.5) · DSA (1.5) |
| **Thu** | 8 | Project (5) · DSA (2) · Docs/notes (1) |
| **Fri** | 5 | **Lighter day:** Project (3) · DSA reviews (1) · Retro prep (1) |
| **Sat** | 6 | Project (4) · Interview practice / mock (2) |
| **Sun** | 2–3 | **Review day:** end-of-week test · spaced reviews · trackers · plan next week · rest |

Foundation weeks swap ~10 project hours for learning; ForgeCI weeks add ~5 project hours (mostly Sat).

### Daily template

```
Date:            Week/Day:
Learning:        (topic, source, 3-line summary in my words)
Project:         (milestone task, PR/commit, what broke, how I fixed it)
DSA:             (problem, status, time, pattern, next review date)
Explain:         (one concept said out loud — did I stumble?)
Tomorrow:        (the first thing I'll do)
```

---

## 13. Rules for falling behind

1. **Never skip DSA reviews** — cut new problems before cutting reviews.
2. **Never skip the project's tests or docs to add features** — quality over surface area.
3. If a milestone slips, **finish it before starting the next** — the sequence matters more than the calendar. Weeks 24–26 are the buffer.
4. If a project phase is > 1 week behind at its checkpoint, **drop its Advanced Version**, not its Strong Résumé Version.
5. Sick / burnt out? **Minimum viable day:** 1 DSA review + 15 minutes reading. Streak kept, energy saved.
6. Never start a fifth project. Never restart a project "cleaner". Refactor in place — that is the real skill.
