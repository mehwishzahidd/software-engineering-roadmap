# 🗺️ ROADMAP — The 26-Week Plan

> This file is the **single source of truth** for order, timing and scope.
> Every week folder, topic folder, project spec and checkpoint in this repository is
> derived from the tables below. If two files ever disagree, this one wins.

**Shape:** 24 core weeks + 2 interview-sprint/buffer weeks = **26 weeks (~6 months)**.
**Load:** **18–24 focused hours per week** (target ≈ 21). No 12-hour days.
**Primary language everywhere:** **Java 21 (LTS)**. DSA, projects, and interviews all happen in Java.

---

## Contents

1. [Learning philosophy](#1-learning-philosophy)
2. [Structure review — what was changed from the original outline and why](#2-structure-review)
3. [Priority weighting — how time is distributed](#3-priority-weighting)
4. [The six phases](#4-the-six-phases)
5. [Week-by-week master table](#5-week-by-week-master-table)
6. [DSA progression across all 26 weeks](#6-dsa-progression)
7. [Project progression](#7-project-progression)
8. [Checkpoints](#8-checkpoints)
9. [Interview-practice ramp](#9-interview-practice-ramp)
10. [Job-application timeline](#10-job-application-timeline)
11. [Standard weekly rhythm and daily template](#11-standard-weekly-rhythm)
12. [Rules for falling behind](#12-rules-for-falling-behind)

---

## 1. Learning philosophy

```
Learn → Build → Break → Debug → Explain → Review → Build again
```

| Step | What it means in practice | What it is NOT |
|---|---|---|
| **Learn** | Read docs / a book chapter / one focused video on a concept. Take notes in your own words. | Binge-watching a 12-hour course. |
| **Build** | Write the code yourself, from a blank file, the same day. | Copy-pasting a tutorial repo. |
| **Break** | Deliberately change something: remove an annotation, pass `null`, drop an index, kill the DB. Predict what happens first. | Hoping nothing goes wrong. |
| **Debug** | Use the debugger, logs, stack traces, `EXPLAIN`, `curl -v`. Find the *root cause*. | Pasting the error into a chatbot and applying the answer blindly. |
| **Explain** | Say it out loud or write 5 sentences: what, why, how, trade-off, example from *your* code. | Memorising a definition. |
| **Review** | Spaced repetition on DSA (Day 0/3/7/14/30), weekly revision block, checkpoint every 4 weeks. | Moving on forever once "done". |
| **Build again** | Re-implement from scratch later (LRU cache, JWT filter, a React form, a Dockerfile). | Assuming "I did it once" means "I can do it". |

**The honesty rule.** This repository will never help you invent experience. It exists so
that every technology already on your résumé is backed by **current, demonstrable
competence** and by **projects you built during these 26 weeks**. When an interviewer
asks about your past work, you describe it truthfully; when they probe technical depth,
you answer from what you actually know *now*, and you can point at code you wrote to
prove it. See [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md).

---

## 2. Structure review

Before building, the originally proposed structure was audited for missing
prerequisites, duplication, bad ordering, mis-weighted technologies and gaps against real
interview expectations. These are the findings and the fixes applied.

### 2.1 Missing prerequisites (fixed)

| Gap | Why it matters | Fix |
|---|---|---|
| **HTTP / networking before Spring Boot** | You can't reason about controllers, status codes, CORS or TLS without knowing what a request is. | Networking + HTTP fundamentals are taught in **Week 9**, the same week Spring Boot starts, *before* the first controller. |
| **Command line before Git and Java builds** | Git, Maven and Java are all driven from a terminal. | Shell basics are Day 1 of **Week 1**; Linux gets its deep treatment in Week 19. |
| **Testing too late** | Outline put testing in folder 09 — if you only learn JUnit in month 3, months 1–2 produce untested code and bad habits. | **JUnit 5 starts in Week 2**, is required for Project 1, and deepens (Mockito, slice tests, Testcontainers) in Week 12. |
| **Maven was listed under tools, but Spring Boot depends on it** | Dependency management, lifecycle and `pom.xml` must be understood before Spring. | Maven is introduced in **Week 3** (Project 1 is a Maven project from day one). |
| **Java memory model before concurrency** | Stack vs heap, references and GC are prerequisites for threads and for "pass-by-value" interview questions. | Memory model in **Week 6**, concurrency in **Week 13**. |
| **Recursion before trees/backtracking/DP** | Every tree, graph DFS, backtracking and DP problem is recursion. | Dedicated recursion block in **Week 9**, before trees. |
| **JavaScript before TypeScript before React** | TypeScript is JavaScript plus types; React is JavaScript plus components. Skipping JS produces cargo-cult React. | Strict order: **JS (Week 14) → TS (Week 15) → React (Weeks 15–17)**. |
| **Docker needed earlier than Phase 5** | Running PostgreSQL for Project 2 is easiest in a container. | A **minimal Docker/Compose intro in Week 10** (just enough to run Postgres); full Docker in **Week 19**. |
| **CI before Phase 5** | Project 2 should already run `mvn test` on every push. | **Basic GitHub Actions in Week 12**; full CI/CD pipelines in **Week 22**. |

### 2.2 Missing topics (added)

| Missing from the outline | Added as |
|---|---|
| Python and C++ had no home folder | [`19-python/`](./19-python/) (Week 13 sprint + ongoing scripting) and [`20-cpp-basics/`](./20-cpp-basics/) (≈6 hours total, Week 19) |
| "Reading unfamiliar codebases" and "debugging existing applications" were goals but had no material | [`21-debugging-code-reading/`](./21-debugging-code-reading/) — a real, buggy Maven project with failing tests, used for drills from Week 18 onward |
| Database migrations | Flyway in Week 10 (production teams never hand-edit schemas) |
| Pagination, idempotency, optimistic locking | Weeks 10–12 inside Project 2 (these are the questions that separate "did a tutorial" from "built something") |
| Behavioral interviews | [`16-interview-prep/behavioral.md`](./16-interview-prep/behavioral.md), story bank starts Week 8 |

### 2.3 Duplication (resolved)

| Overlap | Resolution |
|---|---|
| `RESUME_TECH_DEFENSE.md` (root) vs `17-resume-tech-defense/` | Root file = **the method + index + how to use it weekly**. Folder = **one deep file per technology**. |
| `PROJECTS.md` (root) vs `18-projects/` | Root file = **overview, progression, timeline**. Folder = **full specs** per project. |
| HTTP in `06-rest-apis/` and `14-cs-fundamentals/` | `14-cs-fundamentals/networking.md` owns TCP/IP/DNS/TLS; `06-rest-apis/` owns HTTP *semantics for API design* and links back. |
| Redis under databases vs caching under system design | `04-sql-databases/redis.md` owns Redis mechanics; `15-system-design/` owns caching *strategy* and links to it. |
| Auth in Spring Boot and in React | `05-spring-boot/` owns server-side auth (Spring Security, JWT, BCrypt); `08-react/` owns client-side (token storage, protected routes) and links back. |

### 2.4 Ordering problems (fixed)

- **SQL before Spring Data JPA.** Learning JPA first hides SQL and produces N+1 disasters. SQL runs Weeks 5–8; JPA starts Week 10.
- **OOP design (SOLID, patterns) after OOP mechanics.** Mechanics Week 2, design Week 5, after you've written enough code to feel the problems SOLID solves.
- **System design last, but not only at the end.** Real concepts (caching, load balancing, queues) appear first where Project 4 needs them (Weeks 20–22), then are formalised in Week 22 and practised in Weeks 23–26.

### 2.5 Weighting problems (fixed)

- **CSS** is deliberately minimal (≈3 hours total). Enough to lay out a form and a table.
- **C++** is capped at ≈6 hours. It exists so pointers/references/manual memory make sense in CS questions — not to become a second DSA language.
- **MySQL** is taught as a *diff* against PostgreSQL (≈2 hours), not as a second database course.
- **AWS** is capped at five core services (IAM, EC2, S3, RDS, CloudWatch) + just enough VPC/security groups to deploy. No certification chasing.
- **Java and DSA** get the most time by a wide margin (see §3).

### 2.6 Gaps between "learning" and "interview expectations" (closed)

| Real interview expectation | Where it's trained |
|---|---|
| Solve 1–2 problems in 70–120 minutes on HackerRank/CodeSignal-style platforms | [`OA_PREP.md`](./OA_PREP.md), OA simulations weekly from Week 19 |
| Talk while coding; clarify; handle hints | [`16-interview-prep/coding-interview-method.md`](./16-interview-prep/coding-interview-method.md), practised from Week 9 |
| "Walk me through your project" for 20 minutes | Project deep-dive scripts in every project spec + [`16-interview-prep/project-deep-dive.md`](./16-interview-prep/project-deep-dive.md) |
| Résumé-driven technical probing | [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md) + [`17-resume-tech-defense/`](./17-resume-tech-defense/) drills weekly from Week 8 |
| Debugging an unfamiliar repository | [`21-debugging-code-reading/`](./21-debugging-code-reading/) from Week 18 |
| Basic system design for junior roles | [`15-system-design/`](./15-system-design/) Week 22 onward |

---

## 3. Priority weighting

Approximate share of the ~550 total study hours:

| Area | Share | ≈ Hours | Notes |
|---|---:|---:|---|
| **DSA / LeetCode (Java)** | 27% | 150 | Every week from Week 1 to 26 |
| **Java (core → advanced → concurrency)** | 14% | 75 | Deepest single language |
| **Spring Boot + REST + JPA + Security** | 13% | 70 | The backend core |
| **Projects (building, not learning)** | 14% | 80 | Overlaps with everything above |
| **SQL + PostgreSQL + Redis** | 8% | 45 | Must become genuinely strong |
| **JavaScript + TypeScript + React** | 8% | 45 | Enough to build and defend a full-stack app |
| **Testing (JUnit, Mockito, Testcontainers, Vitest)** | 3% | 17 | Plus continuous use inside projects |
| **Git / Linux / Docker / AWS / CI/CD** | 6% | 35 | Practical, project-driven |
| **CS fundamentals + system design** | 4% | 22 | Always tied to interview questions |
| **Interview practice (mocks, OA, behavioral, résumé defense)** | 3% → heavy at end | 11 (+ Phase 6) | Ramps from Week 9; dominant in Weeks 23–26 |
| Python / C++ | ≈1% | 8 | Python ≈5h, C++ ≈3h, plus scripting reps later |

---

## 4. The six phases

| Phase | Weeks | Theme | Exit gate |
|---|---|---|---|
| **1** | 1–4 | Programming foundation + Java + Git + JUnit | [Checkpoint 4](./checkpoints/checkpoint-04.md) |
| **2** | 5–8 | DSA fundamentals + OOP design + SQL/PostgreSQL | [Checkpoint 8](./checkpoints/checkpoint-08.md) |
| **3** | 9–13 | Backend engineering: HTTP, Spring Boot, JPA, Security, testing, concurrency | [Checkpoint 12](./checkpoints/checkpoint-12.md) |
| **4** | 14–18 | JavaScript → TypeScript → React; full-stack integration | [Checkpoint 16](./checkpoints/checkpoint-16.md) |
| **5** | 19–22 | Linux, Docker, Redis, background jobs, AWS, CI/CD, observability, system design | [Checkpoint 20](./checkpoints/checkpoint-20.md) |
| **6** | 23–26 | Interview-heavy: OA simulations, mocks, résumé defense, project polish, applications | [Checkpoint 24](./checkpoints/checkpoint-24.md) |

DSA runs through **all six phases**. Java stays the language for DSA and backend throughout.

---

## 5. Week-by-week master table

"New" = new DSA problems that week. Reviews from the spaced-repetition queue are **in addition**
(see [`trackers/dsa-tracker.md`](./trackers/dsa-tracker.md)).

| Wk | Main objective | Core topics | DSA (new) | Project | Interview / other |
|---:|---|---|---|---|---|
| [1](./weeks/week-01/) | Set up; write Java from a blank file; version everything with Git | Terminal basics, JDK/IDE, Java syntax, types, control flow, methods, arrays, `String`/`StringBuilder`; Git init/add/commit/push, `.gitignore` | Big-O intro; arrays basics — **4** | Create GitHub profile + this repo's workflow | Start 60-second "about me" draft |
| [2](./weeks/week-02/) | Model problems with classes; test them | Classes, constructors, encapsulation, `static`, inheritance, interfaces, abstract classes, polymorphism, `equals`/`hashCode`/`toString`, exceptions; **JUnit 5 basics**; Git branches + merge | Arrays & Hashing — **7** | P1 design sketch | — |
| [3](./weeks/week-03/) | Master the Collections Framework and Maven | `List`/`ArrayList`/`LinkedList`, `Map`/`HashMap` internals, `Set`, `Deque`, `PriorityQueue`, iteration, `Comparable`/`Comparator`, generics; **Maven** (pom, lifecycle, deps) | Hashing + Two Pointers — **7** | **P1 start** (Ledger: domain model + CSV import) | — |
| [4](./weeks/week-04/) | Write modern, idiomatic Java; debug with an IDE | Lambdas, functional interfaces, streams, `Optional`, records, enums, immutability, file I/O (`java.nio.file`), IDE debugger (breakpoints, watches, step into) | Two Pointers + Prefix Sums — **7** | P1 milestone 2 (rules + reports) | **🏁 Checkpoint 4** |
| [5](./weeks/week-05/) | Design classes well; start SQL | SOLID, composition vs inheritance, patterns (Strategy, Factory, Builder, Observer, Singleton), clean code; **Git rebase, PRs, conflicts**; SQL: relational model, `SELECT`/`WHERE`/`ORDER BY`/`LIMIT`, PostgreSQL + `psql` | Sliding Window — **7** | P1 refactor with patterns; PR-based workflow begins | — |
| [6](./weeks/week-06/) | Think in sets; understand Java memory | SQL joins (inner/left/right/full/self), `GROUP BY`/`HAVING`, aggregates, subqueries, `INSERT`/`UPDATE`/`DELETE`; **Java memory**: stack vs heap, references, pass-by-value, GC, String pool; sorting algorithms (merge/quick) | Stack + Binary Search — **8** | P1 milestone 3 (budgets, recurring detection) | CS: memory & computer architecture basics |
| [7](./weeks/week-07/) | Design schemas; connect Java to a database | Schema design, keys, constraints, normalization (1NF–3NF), CTEs, window functions, indexes (B-tree), **JDBC** (`DriverManager`, `PreparedStatement`, SQL injection) | Binary Search + Linked Lists — **8** | P1 milestone 4 (Postgres persistence via JDBC) | — |
| [8](./weeks/week-08/) | Make SQL production-grade; finish Project 1 | Transactions, ACID, isolation levels & anomalies, row locks, `EXPLAIN ANALYZE`, query optimization, connection pooling concept, ORM vs raw SQL, **MySQL diffs** | Linked Lists + review week — **6** | **P1 complete** | **🏁 Checkpoint 8** · behavioral story bank starts · résumé-defense drills begin (Java, Git, SQL) |
| [9](./weeks/week-09/) | Understand the web; first Spring Boot app | **Networking**: TCP/IP, DNS, TLS, HTTP methods/status/headers, REST, JSON; **Spring Boot**: IoC/DI, beans, auto-config, starters, `@RestController`, layered architecture | Recursion + Trees intro — **7** | **P2 start** (TicketHold API: skeleton, events CRUD in memory) | Think-aloud practice begins (1×/week) |
| [10](./weeks/week-10/) | Persist data properly with JPA | Spring Data JPA, Hibernate, entities, relationships, fetch types, **N+1**, **Flyway**, DTOs + mapping, **Bean Validation**, `@ControllerAdvice` error handling, pagination & sorting; **Docker minimal**: Postgres via Compose | Trees — **8** | P2 milestone 2 (Postgres + JPA + validation + errors) | — |
| [11](./weeks/week-11/) | Secure an API | Spring Security filter chain, authentication vs authorization, BCrypt, **JWT** (structure, signing, expiry), roles, method security, CORS; logging (SLF4J/Logback, MDC), Actuator | Trees (BST) + Tries — **8** | P2 milestone 3 (auth + roles) | — |
| [12](./weeks/week-12/) | Test like a professional; transactions in Spring | Test pyramid, JUnit 5 deep, **Mockito**, `@WebMvcTest`, `@DataJpaTest`, **Testcontainers**, `@Transactional` + propagation, **optimistic locking** (`@Version`), idempotency keys; **GitHub Actions: `mvn verify` on every push** | Heap / PriorityQueue — **8** | **P2 v1 complete** (tested, CI green, Dockerized) | **🏁 Checkpoint 12** |
| [13](./weeks/week-13/) | Concurrency and OS fundamentals; Python sprint | Processes vs threads, scheduling, virtual memory; Java threads, `ExecutorService`, `synchronized`, locks, atomics, `ConcurrentHashMap`, race conditions, deadlocks, `CompletableFuture`; **Python for Java devs** (≈5h) | Backtracking — **8** | P2 hardening: concurrent seat-hold test, race fix | Mock #1 (peer or self-recorded) |
| [14](./weeks/week-14/) | JavaScript properly | Types, `let`/`const`, scope, closures, `this`, objects/arrays, destructuring, modules, **event loop**, promises, `async`/`await`, `fetch`, minimal HTML/DOM/CSS | Graphs: BFS/DFS on grids — **8** | **P3 start** (TeamBoard: design doc, schema, backend skeleton) | — |
| [15](./weeks/week-15/) | TypeScript + React fundamentals | TS: types, interfaces, unions, narrowing, generics, `tsconfig`; React: Vite, components, JSX, props, state, events, lists/keys, lifting state | Graphs: Topological sort + Union-Find — **8** | P3 backend: RBAC, projects, issues API | — |
| [16](./weeks/week-16/) | Build real React features against a real API | Hooks (`useEffect`, `useRef`, `useMemo`, `useContext`, custom hooks), controlled forms + validation, React Router, API client layer, loading/error states, TanStack Query (optional) | 1-D Dynamic Programming — **8** | P3 frontend: board, issue CRUD | **🏁 Checkpoint 16** |
| [17](./weeks/week-17/) | Auth and architecture on the frontend; frontend testing | Token storage trade-offs (memory vs `localStorage` vs httpOnly cookie), protected routes, CORS revisited, role-aware UI, **Vitest + React Testing Library**, folder architecture | 1-D DP + Intervals — **8** | P3: auth, role-based UI, tests | **Weekly mock interviews begin** |
| [18](./weeks/week-18/) | Ship a complete full-stack app; read other people's code | Full-stack Docker Compose, end-to-end validation, audit logging, search + pagination UI; **codebase reading method**; debugging drills | Greedy + 2-D DP — **8** | **P3 complete** · P4 design doc | Unfamiliar-code drill #1 |
| [19](./weeks/week-19/) | Linux and Docker in depth; C++ essentials | Filesystem, permissions, processes, signals, `systemd`, `ssh`, `grep`/`sed`/`awk`, Bash scripting; Docker images, layers, multi-stage builds, volumes, networks, Compose; **C++ basics** (≈3h) | 2-D DP — **7** | **P4 start** (PulseWatch: monitors, checker worker, Compose stack) | **OA simulation #1** (70 min) |
| [20](./weeks/week-20/) | Caching, rate limiting, background work | **Redis** (data types, TTL, eviction), cache-aside, invalidation, Spring Cache, **rate limiting** (fixed window, token bucket), `@Scheduled`, job queues, idempotent workers, retries/backoff | Advanced graphs (Dijkstra) + Bit manipulation — **7** | P4 milestone 2 (Redis cache, rate limiter, alerting jobs) | **🏁 Checkpoint 20** · OA sim #2 |
| [21](./weeks/week-21/) | Deploy to the cloud | **AWS**: IAM (users, roles, policies, least privilege), EC2, security groups, VPC basics, S3, RDS (Postgres), CloudWatch logs/metrics/alarms; secrets & config | Mixed review (weakest 3 patterns) — **6** | P4 milestone 3 (running on AWS) | OA sim #3 · mock |
| [22](./weeks/week-22/) | Automate delivery; observe and optimize | **CI/CD**: pipeline stages, build → test → image → deploy, secrets, environments; Actuator + Micrometer metrics, structured logs, health checks; `EXPLAIN` on P4, indexes, load test (k6); **System design basics** | Mixed review — **6** | **P4 complete** (CI/CD deploy, dashboards) | System design mock #1 · OA sim #4 |
| [23](./weeks/week-23/) | Interview sprint I | OA simulations (2×), timed mixed DSA, résumé tech defense (all techs), project deep-dive scripts recorded, behavioral STAR polish | Timed mixed — **6** + OA | Project polish (READMEs, diagrams, demo) | 2 mocks · repo-modification drill |
| [24](./weeks/week-24/) | Interview sprint II — full loops | Full mock loops (coding + project + behavioral + basic design), unfamiliar-code debugging under time, weak-spot remediation | Timed mixed — **6** + OA | Final polish | **🏁 Checkpoint 24** |
| [25](./weeks/week-25/) | Remediation + applications at volume | Whatever Checkpoint 24 flagged; pattern sweep on weakest topics; company-specific OA prep | Weakest patterns — **5** | Small feature on P3/P4 (keeps GitHub active) | 2 mocks |
| [26](./weeks/week-26/) | Final loop + sustainable maintenance plan | Final mock loop, maintenance schedule for the job search (DSA 45 min/day, 1 mock/week) | Maintenance — **4** | — | Transition to [`16-interview-prep/maintenance-plan.md`](./16-interview-prep/maintenance-plan.md) |

**Totals:** ≈ **180 new DSA problems** (NeetCode 150 core + selected extras) + spaced reviews · **4 substantial projects** · **6 checkpoints** · **≥ 10 mock interviews** · **≥ 8 OA simulations**.

---

## 6. DSA progression

Full pattern guides live in [`03-dsa/`](./03-dsa/). Tracking lives in [`trackers/dsa-tracker.md`](./trackers/dsa-tracker.md).

| Order | Pattern | Weeks | Guide |
|---:|---|---|---|
| 0 | Big-O & complexity analysis | 1 | [`00-big-o.md`](./03-dsa/00-big-o.md) |
| 1 | Arrays & Strings | 1–2 | [`01-arrays-strings.md`](./03-dsa/01-arrays-strings.md) |
| 2 | HashMap / HashSet | 2–3 | [`02-hashing.md`](./03-dsa/02-hashing.md) |
| 3 | Two Pointers | 3–4 | [`03-two-pointers.md`](./03-dsa/03-two-pointers.md) |
| 4 | Prefix Sums | 4 | [`04-prefix-sums.md`](./03-dsa/04-prefix-sums.md) |
| 5 | Sliding Window | 5 | [`05-sliding-window.md`](./03-dsa/05-sliding-window.md) |
| 6 | Stack & Queue (incl. monotonic stack) | 6 | [`06-stack-queue.md`](./03-dsa/06-stack-queue.md) |
| 7 | Binary Search (incl. search on answer) | 6–7 | [`07-binary-search.md`](./03-dsa/07-binary-search.md) |
| 8 | Linked Lists | 7–8 | [`08-linked-lists.md`](./03-dsa/08-linked-lists.md) |
| 9 | Recursion | 9 | [`09-recursion.md`](./03-dsa/09-recursion.md) |
| 10 | Trees (traversals, DFS/BFS) | 9–10 | [`10-trees.md`](./03-dsa/10-trees.md) |
| 11 | Binary Search Trees | 11 | [`11-bst.md`](./03-dsa/11-bst.md) |
| 12 | Tries | 11 | [`12-tries.md`](./03-dsa/12-tries.md) |
| 13 | Heap / PriorityQueue | 12 | [`13-heap-priority-queue.md`](./03-dsa/13-heap-priority-queue.md) |
| 14 | Backtracking | 13 | [`14-backtracking.md`](./03-dsa/14-backtracking.md) |
| 15 | Graphs: BFS / DFS | 14 | [`15-graphs-bfs-dfs.md`](./03-dsa/15-graphs-bfs-dfs.md) |
| 16 | Topological Sort | 15 | [`16-topological-sort.md`](./03-dsa/16-topological-sort.md) |
| 17 | Union-Find | 15 | [`17-union-find.md`](./03-dsa/17-union-find.md) |
| 18 | Dynamic Programming 1-D | 16–17 | [`18-dp-1d.md`](./03-dsa/18-dp-1d.md) |
| 19 | Intervals | 17 | [`19-intervals.md`](./03-dsa/19-intervals.md) |
| 20 | Greedy | 18 | [`20-greedy.md`](./03-dsa/20-greedy.md) |
| 21 | Dynamic Programming 2-D | 18–19 | [`21-dp-2d.md`](./03-dsa/21-dp-2d.md) |
| 22 | Advanced Graphs (Dijkstra, MST awareness) | 20 | [`22-advanced-graphs.md`](./03-dsa/22-advanced-graphs.md) |
| 23 | Bit Manipulation basics | 20 | [`23-bit-manipulation.md`](./03-dsa/23-bit-manipulation.md) |
| — | Mixed / timed / OA | 21–26 | [`OA_PREP.md`](./OA_PREP.md) |

**Problem statuses:** `Not Started` → `Attempted` → `Solved With Solution` / `Solved With Hint` / `Solved Independently` → `Needs Review` → `Mastered`.
**Review cycle:** Day 0 → Day 3 → Day 7 → Day 14 → Day 30. A problem is `Mastered` only after an independent, clean, timed solve at the Day-30 review.

---

## 7. Project progression

Four projects, each larger than the last. Full specs in [`18-projects/`](./18-projects/); overview in [`PROJECTS.md`](./PROJECTS.md).

| # | Project | Weeks | Stack | The interview story it gives you |
|---|---|---|---|---|
| **P1** | [**Ledger** — personal-finance engine (CLI)](./18-projects/p1-ledger/) | 3–8 | Java 21, Collections, Streams, JUnit 5, Maven, JDBC, PostgreSQL, Git | "I designed a domain model, parsed messy real-world input, wrote rule-based logic, and swapped in-memory storage for Postgres behind an interface — with tests." |
| **P2** | [**TicketHold** — event seat-reservation REST API](./18-projects/p2-tickethold/) | 9–13 | Spring Boot 3, Spring Data JPA, PostgreSQL, Flyway, Spring Security + JWT, JUnit/Mockito/Testcontainers, Docker, GitHub Actions | "I prevented double-booking under concurrency with transactions and optimistic locking, and I can show the test that proves it." |
| **P3** | [**TeamBoard** — full-stack issue tracker with RBAC](./18-projects/p3-teamboard/) | 14–18 | Spring Boot, PostgreSQL, React 18 + TypeScript, Vite, JWT, Vitest/RTL, Docker Compose | "I built both halves, designed the permission model, and can explain every hop from a button click to a database row." |
| **P4** | [**PulseWatch** — uptime monitoring & alerting platform](./18-projects/p4-pulsewatch/) | 18–22 (+polish 23–25) | Spring Boot, PostgreSQL, Redis, scheduled workers, React/TS, Docker, AWS (EC2, RDS, S3, CloudWatch, IAM), GitHub Actions CI/CD | "I run background checks at scale, cache hot reads in Redis, rate-limit a public API, deploy to AWS through a pipeline, and I tuned the slowest query with `EXPLAIN`." |

---

## 8. Checkpoints

Pass/fail gates every four weeks. Each lives in [`checkpoints/`](./checkpoints/) with a
timed test, pass criteria across Java, DSA, SQL, backend, frontend, Git, debugging, CS
fundamentals, projects and interview readiness, and a **remediation plan** if you fail.

| Checkpoint | Week | Gate question |
|---|---|---|
| [CP-4](./checkpoints/checkpoint-04.md) | 4 | Can I write, test and version small Java programs without a tutorial? |
| [CP-8](./checkpoints/checkpoint-08.md) | 8 | Can I solve Easy problems reliably, write real SQL, and have I shipped P1? |
| [CP-12](./checkpoints/checkpoint-12.md) | 12 | Can I build, secure and test a Spring Boot API against Postgres? |
| [CP-16](./checkpoints/checkpoint-16.md) | 16 | Can I connect a typed React client to my API and solve standard Mediums? |
| [CP-20](./checkpoints/checkpoint-20.md) | 20 | Can I containerize, cache, and reason about production concerns? |
| [CP-24](./checkpoints/checkpoint-24.md) | 24 | Would I pass an internship/junior loop today? |

**Failure rule:** a failed checkpoint does **not** stop the calendar. You do the listed
remediation inside the next two weeks' revision blocks (and Weeks 25–26 are buffer).
Two consecutive failures in the same area → pause new material in that area for one week.

---

## 9. Interview-practice ramp

| Weeks | Interview activity |
|---|---|
| 1–8 | Explain each DSA solution out loud after solving it. Draft "about me". Story bank starts Week 8. |
| 8–12 | Résumé-defense drill: 3 questions/week from [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md), answered out loud and recorded. |
| 9–16 | Think-aloud session 1×/week: one Medium, recorded, reviewed against [`INTERVIEW_CHECKLIST.md`](./INTERVIEW_CHECKLIST.md). Mock #1 in Week 13. |
| 17–22 | **Weekly mock interview** (peer, Pramp-style, or AI interviewer with strict rules). Unfamiliar-code drills from Week 18. OA simulations from Week 19. |
| 23–26 | 2 mocks/week, 2 OA simulations/week, full loops, repository-modification drills. |

---

## 10. Job-application timeline

Do **not** wait until Week 26. Full criteria in [`JOB_READINESS.md`](./JOB_READINESS.md).

| Stage | Earliest typical week | Action |
|---|---|---|
| Too early | 1–7 | Build. Don't apply. Set up LinkedIn/GitHub. |
| Early application stage | ~8–10 | Apply to internships with long timelines; referrals; 3–5 apps/week. |
| Internship-ready | ~12–14 | Apply broadly to internships. 8–10 apps/week. |
| OA-ready | ~16–19 | Accept OAs confidently; apply to junior roles with OAs. |
| Junior-role-ready | ~20–22 | Apply to junior / entry-level / new-grad-style roles at volume. |
| Interview-ready | ~24 | Full loops. Keep the maintenance plan running until offer. |

Weeks are *typical*, not automatic — you advance a stage only when you meet its objective criteria.

---

## 11. Standard weekly rhythm

Every week folder follows this rhythm (≈21 hours). Adjust days to your life; keep the ratios.

| Day | Hours | Blocks |
|---|---:|---|
| **Mon** | 3 | Core learning (1.5) · Hands-on coding (1) · DSA (0.5 review) |
| **Tue** | 3 | DSA new problems (1.5) · Core learning/coding (1.5) |
| **Wed** | 3 | Project development (2) · DSA review (1) |
| **Thu** | 3 | Core learning (1) · Hands-on coding (1) · DSA new (1) |
| **Fri** | 2 | **Light day**: revision + tracker update + one explanation out loud |
| **Sat** | 5 | Project development (3) · DSA new (1) · Interview practice (1) |
| **Sun** | 2 | **Review day**: weekly test, spaced reviews, plan next week. Rest the remainder. |

### Daily template (copy into your notes)

```
Date:            Week/Day:
Core learning:   (topic, source, 3-line summary in my words)
Hands-on coding: (what I built, what broke, how I fixed it)
DSA:             (problem, status, time, pattern, next review date)
Project:         (commit links)
Explain:         (one concept, said out loud — did I stumble?)
Tomorrow:        (the first thing I'll do)
```

---

## 12. Rules for falling behind

1. **Never skip DSA reviews** — cut new problems before cutting reviews.
2. **Never skip the project** — projects are the evidence; cut "extra reading" first.
3. If you lose a week, **don't compress** two weeks into one. Slide the calendar and use Weeks 25–26 as buffer.
4. If you're >2 weeks behind at a checkpoint, drop that phase's *stretch goals*, not its core.
5. Sick / burnt out? Do the **minimum viable day**: 1 DSA review + 15 minutes of reading. Streak kept, energy saved.
