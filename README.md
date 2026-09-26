# 🛠️ Software Engineering Roadmap — 26 Weeks to Job-Ready

A self-contained, week-by-week plan to rebuild **deep, current, defensible** software
engineering skill and pass **SWE internship, junior SDE, entry-level and new-grad-style**
interviews — built around four flagship projects: **FlowGrid**, **LedgerX**, **ForgeCI**, **FlagForge**.

> **Why this exists.** My résumé already shows years of real work across junior → mid →
> contract software engineering, followed by a period away from SWE. Passing a beginner
> coding screen isn't enough: I need to discuss Java, Spring Boot, PostgreSQL, REST, React,
> TypeScript, Docker, AWS, Redis, testing and Git **naturally and in depth**, and back every
> answer with serious software I've built recently.
> **Nothing in this repo fabricates experience, projects or metrics.** It builds competence and evidence.

```
Learn → Build → Break → Debug → Explain → Review → Build again
```

### The two-language strategy

| | Language | Used for |
|---|---|---|
| **Track A — coding interviews** | 🐍 **Python** | LeetCode, NeetCode, HackerRank, Amazon-style OAs, timed problems, mock coding interviews (~90% of DSA) |
| **Track B — engineering & résumé interviews** | ☕ **Java** + the stack | Spring Boot, the four flagship projects, architecture, concurrency, testing, project deep-dives, résumé probing |

One "Java rep" per week keeps Java collections fluent; nothing is solved twice by default. Every project keeps a Java/Spring backend **and** ships a real, tested Python tooling component.

---

## 📍 Where am I?

| | |
|---|---|
| **Current week** | **[Week 1 — Write Python and Java from a blank file; version it](./weeks/week-01/)** ← *update this link every Monday* |
| **Current phase** | Phase 0 — Foundation |
| **Current project** | Foundation exercises (FlowGrid starts [Week 4](./weeks/week-04/)) |
| **Next checkpoint** | [Checkpoint 4](./checkpoints/checkpoint-04.md) |
| **Job-readiness stage** | Too early — see [`JOB_READINESS.md`](./JOB_READINESS.md) |
| **Start date** | `YYYY-MM-DD` |

**Every day:** open the current week's README → find today in its *Daily plan* → do the blocks → update [`trackers/dsa-tracker.md`](./trackers/dsa-tracker.md).
**Every Sunday:** end-of-week test → [`trackers/weekly-progress.md`](./trackers/weekly-progress.md) → move the "Current week" link.

### Overall progress

| Phase | Weeks | Project | Progress |
|---|---|---|---|
| 0 · Foundation: Python + Java + Git + SQL + Spring intro | 1–3 | exercises | ⬜⬜⬜ 0/3 |
| 1 · FlowGrid | 4–8 | [FlowGrid](./18-projects/flowgrid/) | ⬜⬜⬜⬜⬜ 0/5 |
| 2 · LedgerX | 9–13 | [LedgerX](./18-projects/ledgerx/) | ⬜⬜⬜⬜⬜ 0/5 |
| 3 · ForgeCI | 14–19 | [ForgeCI](./18-projects/forgeci/) | ⬜⬜⬜⬜⬜⬜ 0/6 |
| 4 · FlagForge | 20–23 | [FlagForge](./18-projects/flagforge/) | ⬜⬜⬜⬜ 0/4 |
| 5 · Polish & interviews | 24–26 | all four | ⬜⬜⬜ 0/3 |

<sub>Replace ⬜ with ✅ as weeks complete.</sub>

---

## 🎯 The 6-month goal

By Week 24 I can:

- [ ] Solve LeetCode Easies fluently and common Mediums in ≤ 25–30 min **in Python**, explaining approach and Big-O out loud
- [ ] Pass a 70–120 minute OA (Python algorithm problems + debugging/existing-codebase tasks)
- [ ] Write production-style **Java** and build, secure, test and containerize Spring Boot services on PostgreSQL
- [ ] Solve real concurrency problems (inventory reservations, double spending, job leasing) and prove it with tests
- [ ] Use Redis meaningfully (caching, queues, pub/sub, rate limiting) and explain when not to
- [ ] Write SQL — joins, CTEs, window functions — without searching syntax, and read an `EXPLAIN` plan
- [ ] Build typed React clients that talk to my APIs with auth
- [ ] Use Git, Linux, Docker, GitHub Actions and core AWS (IAM, EC2, S3, RDS, CloudWatch) professionally
- [ ] Give a 10–15 minute deep technical explanation of each flagship project without notes
- [ ] Answer résumé-driven technical questions on every technology I list
- [ ] Pass an internship/junior-level interview loop ([Checkpoint 24](./checkpoints/checkpoint-24.md))

---

## 🧰 Technology stack & priorities

| Priority | Technologies | Depth target |
|---|---|---|
| 🔴 **Very high** | **Python** (interviews/DSA/tooling) · **Java** (backend/projects) · **SQL / PostgreSQL** · **Spring Boot** · **REST APIs** · **DSA** | Build, debug, explain trade-offs, interview on it |
| 🟠 **High** | JUnit 5 / Mockito / Testcontainers, Git / GitHub, Maven, JPA/Hibernate, Redis, Docker, concurrency | Build and debug confidently |
| 🟡 **Medium-high** | TypeScript, React, JavaScript, GitHub Actions CI/CD, AWS (IAM, EC2, S3, RDS, CloudWatch), Linux / Bash, CS fundamentals, basic system design | Use in a real project, answer standard questions |
| 🟢 **Low** | MySQL (as a diff vs Postgres), C++ basics, HTML/CSS (only enough for usable React UIs) | Read, write small programs, explain differences |

Full weighting and the structure audit: [`ROADMAP.md` §2–3](./ROADMAP.md#2-structure-review).

---

## 🗺️ Roadmap phases

| Phase | Weeks | Focus | Gate |
|---|---|---|---|
| **0** | [1](./weeks/week-01/) · [2](./weeks/week-02/) · [3](./weeks/week-03/) | Python for interviews · Java syntax → OOP → collections → modern Java · Git · JUnit · Maven · SQL basics · HTTP · Spring Boot intro | — |
| **1** | [4](./weeks/week-04/) · [5](./weeks/week-05/) · [6](./weeks/week-06/) · [7](./weeks/week-07/) · [8](./weeks/week-08/) | **FlowGrid**: JPA, transactions & locking, idempotency, allocation, Redis, React/TS dashboard, Docker, CI, first AWS deploy | [CP-4](./checkpoints/checkpoint-04.md) · [CP-8](./checkpoints/checkpoint-08.md) |
| **2** | [9](./weeks/week-09/) · [10](./weeks/week-10/) · [11](./weeks/week-11/) · [12](./weeks/week-12/) · [13](./weeks/week-13/) | **LedgerX**: double-entry ledger, isolation & locking, idempotency, reversals, reconciliation, failure injection, invariants | [CP-12](./checkpoints/checkpoint-12.md) |
| **3** | [14](./weeks/week-14/) · [15](./weeks/week-15/) · [16](./weeks/week-16/) · [17](./weeks/week-17/) · [18](./weeks/week-18/) · [19](./weeks/week-19/) | **ForgeCI**: Linux/Docker internals, queues & workers, Java concurrency, webhooks, container execution, live logs, timeouts/cancel/retry, recovery, DAGs | [CP-16](./checkpoints/checkpoint-16.md) |
| **4** | [20](./weeks/week-20/) · [21](./weeks/week-21/) · [22](./weeks/week-22/) · [23](./weeks/week-23/) | **FlagForge**: versioned config, evaluation engine, deterministic rollouts, Redis snapshots, Java + Python SDKs, propagation, system design | [CP-20](./checkpoints/checkpoint-20.md) |
| **5** | [24](./weeks/week-24/) · [25](./weeks/week-25/) · [26](./weeks/week-26/) | Polish all four · load tests · security review · docs & diagrams · OA simulations · mock loops · applications | [CP-24](./checkpoints/checkpoint-24.md) |

**Weekly load:** 40–50 focused hours (project 25–30 · DSA 6–8 · learning 6–8 · interview/review 3–5). See [the weekly rhythm](./ROADMAP.md#12-standard-weekly-rhythm).

---

## 🏗️ The four flagship projects

Built **sequentially** so knowledge compounds. Each is its own GitHub repository; specs live here.
Each has scope tiers **MVP → Strong Résumé Version (the target) → Advanced → Stretch**.

| # | Project | Weeks | Technical identity | Python component | Status |
|---|---|---|---|---|---|
| 1 | [**FlowGrid**](./18-projects/flowgrid/) — multi-warehouse fulfillment & inventory platform | 4–8 | Inventory state machine, reservation concurrency, idempotent orders, deterministic allocation, pick/pack/ship, Redis cache, React dashboard, AWS | Order generator + load/simulation harness | ⬜ |
| 2 | [**LedgerX**](./18-projects/ledgerx/) — digital wallet & double-entry ledger engine | 9–13 | Immutable ledger, isolation & locking, idempotency, compensating entries, reconciliation, failure injection, invariants | Independent reconciliation verifier + data generator | ⬜ |
| 3 | [**ForgeCI**](./18-projects/forgeci/) — distributed CI/CD execution platform | 14–19 | Redis job queue, worker pool, Docker-isolated execution, GitHub webhooks, SSE live logs, timeouts/cancel/retry, recovery, DAG pipelines | Test-repo generator, webhook load simulator, log analysis | ⬜ |
| 4 | [**FlagForge**](./18-projects/flagforge/) — feature-flag & progressive-rollout platform + SDK | 20–23 | Rule engine, deterministic percentage bucketing, Redis snapshots, Java SDK design, propagation, fallback behaviour, versioning | Rollout simulator, config validator, Python SDK/test client | ⬜ |

Overview: [`PROJECTS.md`](./PROJECTS.md) · which to feature on a résumé: [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md) · tracking: [`trackers/project-tracker.md`](./trackers/project-tracker.md)

---

## 🧩 DSA progression (Python)

NeetCode-style, pattern-first, **Python** templates with a short "same in Java" section per pattern. Continuous from Week 1 to Week 26 (~185 new problems + spaced reviews). Keep [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md) open.

| Weeks | Patterns |
|---|---|
| 1–3 | [Big-O](./03-dsa/00-big-o.md) · [Arrays & Strings](./03-dsa/01-arrays-strings.md) · [Hashing](./03-dsa/02-hashing.md) · [Two Pointers](./03-dsa/03-two-pointers.md) · [Prefix Sums](./03-dsa/04-prefix-sums.md) |
| 4–8 | [Sliding Window](./03-dsa/05-sliding-window.md) · [Stack & Queue](./03-dsa/06-stack-queue.md) · [Binary Search](./03-dsa/07-binary-search.md) · [Linked Lists](./03-dsa/08-linked-lists.md) · [Recursion](./03-dsa/09-recursion.md) |
| 9–13 | [Trees](./03-dsa/10-trees.md) · [BST](./03-dsa/11-bst.md) · [Tries](./03-dsa/12-tries.md) · [Heap](./03-dsa/13-heap-priority-queue.md) · [Backtracking](./03-dsa/14-backtracking.md) · [Graphs BFS/DFS](./03-dsa/15-graphs-bfs-dfs.md) |
| 14–19 | [Topological Sort](./03-dsa/16-topological-sort.md) · [Union-Find](./03-dsa/17-union-find.md) · [1-D DP](./03-dsa/18-dp-1d.md) · [Intervals](./03-dsa/19-intervals.md) · [Greedy](./03-dsa/20-greedy.md) · [2-D DP](./03-dsa/21-dp-2d.md) · [Advanced Graphs](./03-dsa/22-advanced-graphs.md) · [Bit Manipulation](./03-dsa/23-bit-manipulation.md) |
| 20–26 | Mixed, timed, weakest-pattern sweeps, [OA simulations](./OA_PREP.md) |

**Statuses:** Not Started → Attempted → Solved With Solution / Hint / Independently → Needs Review → Mastered.
**Spaced repetition:** Day 0 → 3 → 7 → 14 → 30. **Java rep:** one solved problem per week re-done in Java ([quick reference](./03-dsa/java-dsa-toolkit.md)).

---

## 🏁 Checkpoints

| Week | Checkpoint | Gate question | Result |
|---|---|---|---|
| 4 | [CP-4](./checkpoints/checkpoint-04.md) | Python Easies fluently; tested Java; basic SQL; FlowGrid M1 secured CRUD API? | ⬜ |
| 8 | [CP-8](./checkpoints/checkpoint-08.md) | FlowGrid v1.0 deployed, tested, explainable? Easies reliable, first Mediums? | ⬜ |
| 12 | [CP-12](./checkpoints/checkpoint-12.md) | Transactional correctness, locking and idempotency proven (LedgerX M4)? | ⬜ |
| 16 | [CP-16](./checkpoints/checkpoint-16.md) | Queue → worker → container → live logs working (ForgeCI M3)? Mediums ≤ 30 min? | ⬜ |
| 20 | [CP-20](./checkpoints/checkpoint-20.md) | ForgeCI shipped with failure recovery? Junior-level system design? | ⬜ |
| 24 | [CP-24](./checkpoints/checkpoint-24.md) | Would I pass a junior loop today, incl. a 15-min project deep-dive without notes? | ⬜ |

Failing one doesn't stop the calendar — each checkpoint has a remediation plan. See [`checkpoints/`](./checkpoints/).

---

## 💼 Job-readiness milestones

**Don't wait until Week 26 to apply.** Advance a stage only when its objective criteria are met — details in [`JOB_READINESS.md`](./JOB_READINESS.md).

| Stage | Typical week | Unlocks |
|---|---|---|
| ⛔ Too early | 1–7 | Build; set up GitHub + LinkedIn |
| 🌱 Early application | ~8 | FlowGrid v1.0 deployed → long-timeline internships, referrals |
| 🎓 Internship-ready | ~12–13 | Two projects; transactions/concurrency story |
| 📝 OA-ready | ~16–17 | Mediums ≤ 30 min in Python; OA sims passing |
| 💼 Junior-role-ready | ~19–20 | Three projects incl. ForgeCI; weekly mocks ≥ 3/4 |
| 🎤 Interview-ready | ~24 | Full loops; deep dives without notes |

---

## 📚 Repository map

<details open>
<summary><b>Plan & tracking</b></summary>

| File | Use it for |
|---|---|
| [`ROADMAP.md`](./ROADMAP.md) | The master plan: phases, week table, just-in-time learning map, weighting, structure audit |
| [`weeks/`](./weeks/) | 26 weekly plans with daily schedules, assignments, tests, checklists |
| [`checkpoints/`](./checkpoints/) | Pass/fail gates every 4 weeks, with remediation |
| [`trackers/`](./trackers/) | [DSA](./trackers/dsa-tracker.md) · [Technology](./trackers/technology-tracker.md) · [Projects](./trackers/project-tracker.md) · [Interviews](./trackers/interview-tracker.md) · [Weekly progress](./trackers/weekly-progress.md) |
| [`RESOURCES.md`](./RESOURCES.md) | Curated books, docs, courses, practice sites |

</details>

<details open>
<summary><b>Topic guides</b></summary>

| # | Folder | # | Folder |
|---|---|---|---|
| 01 | [Java](./01-java/) | 12 | [AWS](./12-aws/) |
| 02 | [Git](./02-git/) | 13 | [CI/CD](./13-cicd/) |
| 03 | [DSA (Python)](./03-dsa/) | 14 | [CS fundamentals](./14-cs-fundamentals/) |
| 04 | [SQL & databases (incl. Redis)](./04-sql-databases/) | 15 | [System design](./15-system-design/) |
| 05 | [Spring Boot](./05-spring-boot/) | 16 | [Interview prep](./16-interview-prep/) |
| 06 | [REST APIs](./06-rest-apis/) | 17 | [Résumé tech defense](./17-resume-tech-defense/) |
| 07 | [JavaScript & TypeScript](./07-javascript-typescript/) | 18 | [Projects](./18-projects/) |
| 08 | [React](./08-react/) | 19 | [Python](./19-python/) |
| 09 | [Testing](./09-testing/) | 20 | [C++ basics](./20-cpp-basics/) |
| 10 | [Linux](./10-linux/) | 21 | [Debugging & code reading](./21-debugging-code-reading/) |
| 11 | [Docker](./11-docker/) | | |

</details>

<details open>
<summary><b>Interview & career</b></summary>

| File | Use it for |
|---|---|
| [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md) | Track A: Python syntax, stdlib, Big-O and pattern templates for coding interviews |
| [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md) | Track B: method + readiness matrix for defending every résumé technology |
| [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md) | 120+ résumé-probing questions with answer outlines |
| [`INTERVIEW_CHECKLIST.md`](./INTERVIEW_CHECKLIST.md) | Before / during / after every interview |
| [`OA_PREP.md`](./OA_PREP.md) | Online-assessment strategy + 8 timed simulation sets |
| [`JOB_READINESS.md`](./JOB_READINESS.md) | When to apply, to what, and how many |
| [`PROJECTS.md`](./PROJECTS.md) · [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md) | The four projects, scope tiers, and which to feature per role |

</details>

---

## ✅ Rules I follow

1. **Python for coding interviews, Java for engineering.** One Java rep per week; never everything twice.
2. **The repo teaches; I build.** Every milestone: know first → requirements → guidance → acceptance → implement → verify → debug → explain.
3. **One project at a time.** Finish the milestone before starting the next; never start a fifth project.
4. **Every problem gets a status and a review date.** No untracked LeetCode.
5. **Every feature gets a test; every milestone gets a PR; every claim gets a measurement.**
6. **Explain it out loud** before calling it learned.
7. **Honesty:** truthful past work, current skill proven by current code, no invented metrics.
8. **Sustainable pace:** one lighter day and one review day every week. Missing a day is fine; missing reviews is not.
