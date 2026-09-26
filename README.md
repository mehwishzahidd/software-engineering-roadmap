# ☕ Software Engineering Roadmap — 26 Weeks to Job-Ready

A self-contained, week-by-week plan to rebuild **deep, current, defensible** software
engineering skill and pass **SWE internship, junior SDE, entry-level and new-grad-style**
interviews — with **Java** as the primary language for building, DSA and interviewing.

> **Why this exists.** My résumé already shows years of real work across junior →
> mid → contract software engineering, followed by a period away from SWE. Passing a
> beginner coding screen isn't enough: I need to be able to discuss Java, Spring Boot,
> PostgreSQL, REST, React, TypeScript, Docker, AWS, testing and Git **naturally and in
> depth**, and back every answer with code I've written recently.
> **Nothing in this repo fabricates experience.** It builds competence and evidence.

```
Learn → Build → Break → Debug → Explain → Review → Build again
```

---

## 📍 Where am I?

| | |
|---|---|
| **Current week** | **[Week 1 — Set up; write Java from a blank file](./weeks/week-01/)** ← *update this link every Monday* |
| **Current phase** | Phase 1 — Programming foundation + Java + Git |
| **Next checkpoint** | [Checkpoint 4](./checkpoints/checkpoint-04.md) |
| **Job-readiness stage** | Too early — see [`JOB_READINESS.md`](./JOB_READINESS.md) |
| **Start date** | `YYYY-MM-DD` |

**Every day:** open the current week's README → find today in its *Daily plan* → do the blocks → update [`trackers/dsa-tracker.md`](./trackers/dsa-tracker.md).
**Every Sunday:** end-of-week test → [`trackers/weekly-progress.md`](./trackers/weekly-progress.md) → move the "Current week" link.

### Overall progress

| Phase | Weeks | Progress |
|---|---|---|
| 1 · Foundation: Java + Git | 1–4 | ⬜⬜⬜⬜ 0/4 |
| 2 · DSA + OOP + SQL | 5–8 | ⬜⬜⬜⬜ 0/4 |
| 3 · Backend: Spring Boot + Postgres | 9–13 | ⬜⬜⬜⬜⬜ 0/5 |
| 4 · Frontend: JS + TS + React | 14–18 | ⬜⬜⬜⬜⬜ 0/5 |
| 5 · Production: Linux, Docker, Redis, AWS, CI/CD | 19–22 | ⬜⬜⬜⬜ 0/4 |
| 6 · Interview-heavy | 23–26 | ⬜⬜⬜⬜ 0/4 |

<sub>Replace ⬜ with ✅ as weeks complete.</sub>

---

## 🎯 The 6-month goal

By Week 24 I can:

- [ ] Code comfortably in Java without tutorials, and read unfamiliar Java codebases
- [ ] Solve LeetCode Easy reliably and common Medium patterns in ≤ 25–30 minutes, explaining the approach and Big-O out loud
- [ ] Pass a 70–120 minute OA (DSA + debugging + existing-codebase tasks)
- [ ] Build, secure, test and containerize a Spring Boot REST API on PostgreSQL
- [ ] Build a typed React client that talks to that API with auth
- [ ] Write SQL — joins, CTEs, window functions — without searching syntax, and read an `EXPLAIN` plan
- [ ] Use Git, Linux, Docker, GitHub Actions and core AWS (IAM, EC2, S3, RDS, CloudWatch) professionally
- [ ] Explain four real projects end-to-end, including trade-offs and what I'd change
- [ ] Answer résumé-driven technical questions on every technology I list
- [ ] Pass an internship/junior-level interview loop ([Checkpoint 24](./checkpoints/checkpoint-24.md))

---

## 🧰 Technology stack & priorities

Not everything gets equal time. Priority reflects **interview weight × daily-job usefulness**.

| Priority | Technologies | Depth target |
|---|---|---|
| 🔴 **Core** | **Java**, **DSA**, **Spring Boot**, **SQL / PostgreSQL**, **REST APIs** | Build, debug, explain trade-offs, interview on it |
| 🟠 **High** | JUnit 5 / Mockito / Testcontainers, Git / GitHub, Maven, JPA/Hibernate, TypeScript, React, JavaScript | Build and debug confidently |
| 🟡 **Working** | Docker, Linux / Bash, Redis, GitHub Actions CI/CD, AWS (IAM, EC2, S3, RDS, CloudWatch), CS fundamentals, basic system design | Use in a real project, answer standard questions |
| 🟢 **Familiar** | Python, MySQL (as a diff vs Postgres), C++ basics, HTML/CSS | Read, write small programs, explain differences |

Full weighting and the structure audit: [`ROADMAP.md` §2–3](./ROADMAP.md#2-structure-review).

---

## 🗺️ Roadmap phases

| Phase | Weeks | Focus | Project | Gate |
|---|---|---|---|---|
| **1** | [1](./weeks/week-01/) · [2](./weeks/week-02/) · [3](./weeks/week-03/) · [4](./weeks/week-04/) | Java syntax → OOP → Collections → modern Java; Git; JUnit; Maven | P1 starts (W3) | [CP-4](./checkpoints/checkpoint-04.md) |
| **2** | [5](./weeks/week-05/) · [6](./weeks/week-06/) · [7](./weeks/week-07/) · [8](./weeks/week-08/) | SOLID & patterns, SQL → schema design → transactions, JDBC, Java memory | P1 ships (W8) | [CP-8](./checkpoints/checkpoint-08.md) |
| **3** | [9](./weeks/week-09/) · [10](./weeks/week-10/) · [11](./weeks/week-11/) · [12](./weeks/week-12/) · [13](./weeks/week-13/) | HTTP/networking, Spring Boot, JPA, Security/JWT, testing, concurrency, Python sprint | P2 (W9–13) | [CP-12](./checkpoints/checkpoint-12.md) |
| **4** | [14](./weeks/week-14/) · [15](./weeks/week-15/) · [16](./weeks/week-16/) · [17](./weeks/week-17/) · [18](./weeks/week-18/) | JavaScript → TypeScript → React, full-stack auth, frontend tests, reading codebases | P3 (W14–18) | [CP-16](./checkpoints/checkpoint-16.md) |
| **5** | [19](./weeks/week-19/) · [20](./weeks/week-20/) · [21](./weeks/week-21/) · [22](./weeks/week-22/) | Linux, Docker, Redis, background jobs, AWS, CI/CD, observability, system design | P4 (W18–22) | [CP-20](./checkpoints/checkpoint-20.md) |
| **6** | [23](./weeks/week-23/) · [24](./weeks/week-24/) · [25](./weeks/week-25/) · [26](./weeks/week-26/) | OA simulations, mock loops, résumé defense, polish, applications | Polish | [CP-24](./checkpoints/checkpoint-24.md) |

**Weekly load:** ~21 hours (18–24). Five working days, one light day, one review day. See [the weekly rhythm](./ROADMAP.md#11-standard-weekly-rhythm).

---

## 🏗️ Project progression

Four substantial projects instead of fifteen toy apps. Each is its own GitHub repository; specs live here.

| # | Project | Weeks | Proves | Status |
|---|---|---|---|---|
| P1 | [**Ledger** — personal-finance engine (CLI)](./18-projects/p1-ledger/) | 3–8 | Java, OOP, collections, streams, JUnit, Maven, JDBC, PostgreSQL | ⬜ |
| P2 | [**TicketHold** — seat-reservation REST API](./18-projects/p2-tickethold/) | 9–13 | Spring Boot, JPA, Flyway, Security/JWT, transactions & locking, Testcontainers, Docker, CI | ⬜ |
| P3 | [**TeamBoard** — full-stack issue tracker with RBAC](./18-projects/p3-teamboard/) | 14–18 | React + TypeScript, auth end-to-end, authorization model, full-stack Compose | ⬜ |
| P4 | [**PulseWatch** — uptime monitoring & alerting](./18-projects/p4-pulsewatch/) | 18–22 | Redis caching, rate limiting, background workers, AWS, CI/CD, observability, query tuning | ⬜ |

Details: [`PROJECTS.md`](./PROJECTS.md) · tracking: [`trackers/project-tracker.md`](./trackers/project-tracker.md)

---

## 🧩 DSA progression

NeetCode-style, pattern-first, **Java only**, continuous from Week 1 to Week 26 (~180 new problems + spaced reviews).

| Weeks | Patterns |
|---|---|
| 1–4 | [Big-O](./03-dsa/00-big-o.md) · [Arrays & Strings](./03-dsa/01-arrays-strings.md) · [Hashing](./03-dsa/02-hashing.md) · [Two Pointers](./03-dsa/03-two-pointers.md) · [Prefix Sums](./03-dsa/04-prefix-sums.md) |
| 5–8 | [Sliding Window](./03-dsa/05-sliding-window.md) · [Stack & Queue](./03-dsa/06-stack-queue.md) · [Binary Search](./03-dsa/07-binary-search.md) · [Linked Lists](./03-dsa/08-linked-lists.md) |
| 9–13 | [Recursion](./03-dsa/09-recursion.md) · [Trees](./03-dsa/10-trees.md) · [BST](./03-dsa/11-bst.md) · [Tries](./03-dsa/12-tries.md) · [Heap](./03-dsa/13-heap-priority-queue.md) · [Backtracking](./03-dsa/14-backtracking.md) |
| 14–18 | [Graphs BFS/DFS](./03-dsa/15-graphs-bfs-dfs.md) · [Topological Sort](./03-dsa/16-topological-sort.md) · [Union-Find](./03-dsa/17-union-find.md) · [1-D DP](./03-dsa/18-dp-1d.md) · [Intervals](./03-dsa/19-intervals.md) · [Greedy](./03-dsa/20-greedy.md) |
| 19–20 | [2-D DP](./03-dsa/21-dp-2d.md) · [Advanced Graphs](./03-dsa/22-advanced-graphs.md) · [Bit Manipulation](./03-dsa/23-bit-manipulation.md) |
| 21–26 | Mixed, timed, weakest-pattern sweeps, [OA simulations](./OA_PREP.md) |

**Statuses:** Not Started → Attempted → Solved With Solution / Hint / Independently → Needs Review → Mastered.
**Spaced repetition:** Day 0 → 3 → 7 → 14 → 30. Java DSA API cheat sheet: [`java-dsa-toolkit.md`](./03-dsa/java-dsa-toolkit.md).

---

## 🏁 Checkpoints

| Week | Checkpoint | Gate question | Result |
|---|---|---|---|
| 4 | [CP-4](./checkpoints/checkpoint-04.md) | Can I write, test and version small Java programs without help? | ⬜ |
| 8 | [CP-8](./checkpoints/checkpoint-08.md) | Easy problems reliably, real SQL, P1 shipped? | ⬜ |
| 12 | [CP-12](./checkpoints/checkpoint-12.md) | Can I build, secure and test a Spring Boot API? | ⬜ |
| 16 | [CP-16](./checkpoints/checkpoint-16.md) | Typed React client + standard Mediums? | ⬜ |
| 20 | [CP-20](./checkpoints/checkpoint-20.md) | Containerize, cache, reason about production? | ⬜ |
| 24 | [CP-24](./checkpoints/checkpoint-24.md) | Would I pass a junior loop today? | ⬜ |

Failing one doesn't stop the calendar — each checkpoint has a remediation plan. See [`checkpoints/`](./checkpoints/).

---

## 💼 Job-readiness milestones

**Don't wait until Week 26 to apply.** Advance a stage only when its objective criteria are met — details in [`JOB_READINESS.md`](./JOB_READINESS.md).

| Stage | Typical week | Unlocks |
|---|---|---|
| ⛔ Too early | 1–7 | Build; set up GitHub + LinkedIn |
| 🌱 Early application | ~8–10 | Long-timeline internships, referrals |
| 🎓 Internship-ready | ~12–14 | Broad internship applications |
| 📝 OA-ready | ~16–19 | Accept OAs with confidence |
| 💼 Junior-role-ready | ~20–22 | Junior / entry-level / new-grad-style roles at volume |
| 🎤 Interview-ready | ~24 | Full loops |

---

## 📚 Repository map

<details open>
<summary><b>Plan & tracking</b></summary>

| File | Use it for |
|---|---|
| [`ROADMAP.md`](./ROADMAP.md) | The master plan: phases, week table, weighting, structure audit |
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
| 03 | [DSA](./03-dsa/) | 14 | [CS fundamentals](./14-cs-fundamentals/) |
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
| [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md) | Method + readiness matrix for defending every résumé technology |
| [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md) | 120+ résumé-probing questions with answer outlines |
| [`INTERVIEW_CHECKLIST.md`](./INTERVIEW_CHECKLIST.md) | Before / during / after every interview |
| [`OA_PREP.md`](./OA_PREP.md) | Online-assessment strategy + 8 timed simulation sets |
| [`JOB_READINESS.md`](./JOB_READINESS.md) | When to apply, to what, and how many |
| [`PROJECTS.md`](./PROJECTS.md) | The four projects and how they map to the résumé |

</details>

---

## ✅ Rules I follow

1. **Java first.** DSA, projects and interviews in Java unless a task requires otherwise.
2. **Blank file, not tutorial.** Read the concept, close the tab, build it.
3. **Every problem gets a status and a review date.** No untracked LeetCode.
4. **Every feature gets a test; every milestone gets a PR.**
5. **Explain it out loud** before calling it learned.
6. **Honesty:** I describe past work truthfully and prove current skill with current code.
7. **Sustainable pace:** ~21 hours/week, one light day, one review day. Missing a day is fine; missing reviews is not.
