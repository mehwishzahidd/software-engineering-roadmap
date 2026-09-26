# 🛡️ Résumé Tech Defense

> Every technology on your résumé is a promise that you can talk about it under pressure.
> This file is the **method, the schedule, and the index** for keeping that promise honestly.
> The deep, per-technology drill files live in [`17-resume-tech-defense/`](./17-resume-tech-defense/README.md).
> The cross-technology question bank lives in [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md).

---

## Contents

1. [Purpose](#1-purpose)
2. [The honesty principle](#2-the-honesty-principle)
3. [Handling "When did you last use X?" and the gap](#3-handling-when-did-you-last-use-x-and-the-gap)
4. [The defense method (7 layers)](#4-the-defense-method-7-layers)
5. [Depth levels L1–L4](#5-depth-levels-l1l4)
6. [Target level per technology](#6-target-level-per-technology)
7. [Weekly drill routine](#7-weekly-drill-routine)
8. [Readiness matrix](#8-readiness-matrix)
9. [Index of drill files](#9-index-of-drill-files)

---

## 1. Purpose

Your résumé lists: **Java, Spring Boot, PostgreSQL, MySQL, REST, React, TypeScript,
JavaScript, Docker, AWS, Redis, Git, GitHub, Maven, JUnit, CI/CD, Linux** (plus Python).
Interviewers for internship / junior / new-grad roles will pick 2–4 of these and probe until
they find the edge of your knowledge. Résumé-driven probing typically looks like:

1. "I see Spring Boot on your résumé — tell me how you used it." (**context**)
2. "How does dependency injection work there?" (**concept**)
3. "What happens if two beans implement the same interface?" (**mechanics**)
4. "You got a `LazyInitializationException` in production — what do you do?" (**debugging**)
5. "Why Spring Boot and not something lighter?" (**trade-offs**)

This system exists so that at every one of those five steps you have a **true, current,
specific** answer, backed by code you can show.

---

## 2. The honesty principle

**Non-negotiable rules:**

- [ ] I never claim a project, feature, responsibility, team size, metric, or tool I did not actually work on.
- [ ] I never inflate duration ("3 years of Spring Boot" when it was one project for 8 months).
- [ ] I never imply recent daily use of something I last touched years ago.
- [ ] When I don't know, I say so, then reason out loud from what I *do* know.
- [ ] Every technical claim I make about the *present* is backed by one of my roadmap projects (P1–P4) or a drill I can repeat live.

**Why this is also the winning strategy:** fabricated experience collapses at question 3 or
4 of a probe, and interviewers compare notes. Truthful past + deep present + visible evidence
survives any depth of questioning, because every answer is something you actually did.

**The three sources you are allowed to draw on:**

| Source | What it proves | How to phrase it |
|---|---|---|
| **Past roles** (Junior Dev → Developer → SE → contract SE) | You have worked in real teams on real systems | Past tense, specific, truthful scope: "In my role at X, I maintained…" |
| **Current knowledge** (this roadmap) | You understand it *now*, at depth | Present tense: "The way it works is…", "I'd debug it by…" |
| **Roadmap projects** P1 Ledger, P2 TicketHold, P3 TeamBoard, P4 PulseWatch | You can build with it *now* | "In TicketHold I implemented… here's the test that proves it." |

If you can't remember a detail from a past role, say so. "I don't remember the exact
connection-pool size we used, but here's how I'd size one today and why" is a strong answer.

---

## 3. Handling "When did you last use X?" and the gap

Answer **directly, with dates, then pivot to evidence.** Do not dodge; dodging is what
makes interviewers suspicious.

### Answer templates

**Template A — used professionally, then a gap, now rebuilt:**
> "I last used Spring Boot professionally in [year] in my contract role at [company], where I
> [truthful scope: e.g. added REST endpoints to an existing service and fixed bugs in its JPA
> layer]. After that I had a break from software work. Over the last six months I've
> deliberately rebuilt my skills on the current version — Spring Boot 3 on Java 21 — and
> built TicketHold, a seat-reservation API where I handled double-booking with optimistic
> locking and proved it with a concurrency test. Happy to walk through that code."

**Template B — used lightly in the past, deeper now:**
> "In my earlier role I used Docker mainly to run a local database someone else had set up —
> I didn't write the Dockerfiles. Since then I've learned it properly: I wrote the multi-stage
> Dockerfile and Compose stack for PulseWatch, and I can explain the layer caching choices."

**Template C — on the résumé, but honestly shallow:**
> "I'd put MySQL at a working level rather than expert. I used it for [truthful scope]. My
> deeper database experience now is PostgreSQL — in Ledger and TicketHold I used transactions,
> constraints, and `EXPLAIN ANALYZE` — and I know the main MySQL/InnoDB differences."

**Template D — asked directly about the gap:**
> "I stepped away from software work between [dates] for [brief, true reason — or 'personal
> reasons' if you prefer not to elaborate]. When I decided to return, I set a structured plan:
> DSA daily, four projects of increasing size, all tested and deployed. The code is on my GitHub."

### Rules for gap questions

- Keep the gap explanation to **one or two sentences**. You owe a truthful answer, not a biography.
- Always end on **current evidence** (a project, a test, a deployed URL, a commit history).
- Never backdate roadmap projects into past jobs. They are clearly labeled as personal projects.
- If asked "Is this a tutorial project?" — answer truthfully: "The idea is common; the design,
  code and tests are mine. For example, [one decision you made and why]."

Rehearse all four templates out loud during the Week 8 drill and again in Week 23.

---

## 4. The defense method (7 layers)

For **every** technology, you must be able to answer these seven layers without notes.
Each drill file in `17-resume-tech-defense/` is organised so you can build each layer.

| # | Layer | The question behind it | Example (PostgreSQL) |
|---|---|---|---|
| 1 | **What** | Define it in two sentences. | "An open-source relational database with MVCC, strong SQL standard support, and rich types like JSONB." |
| 2 | **Why** | What problem does it solve; why was it chosen? | "Relational integrity with constraints + transactions; the data was relational." |
| 3 | **How I used / use it** | Truthful past use + current project use. | "Past: queried reporting tables. Now: TicketHold schema via Flyway, `@Version` locking." |
| 4 | **How it interacts with my stack** | Where does it sit relative to Java/Spring/React/Docker/AWS/CI? | "JDBC → HikariCP → Postgres in Docker locally, RDS in prod, Testcontainers in CI." |
| 5 | **Problems & debugging** | A real problem you hit and how you found the root cause. | "Slow report query → `EXPLAIN ANALYZE` showed seq scan → composite index." |
| 6 | **Trade-offs** | When would you *not* use it; what did you give up? | "Vertical scaling limits; ops cost vs DynamoDB; but joins and constraints were needed." |
| 7 | **Evidence in my projects** | File, commit, test, or screenshot you can point to. | "`V3__add_check_results_index.sql` in PulseWatch + before/after plans in README." |

**Answer shape for a spoken answer (≈60–90 s):** one-line definition → how I used it (past
truthfully, present specifically) → one concrete detail/decision → one trade-off → offer to go deeper.

---

## 5. Depth levels L1–L4

| Level | Name | You can… | Interview signal |
|---|---|---|---|
| **L1** | Define | Define the tool and its key terms correctly. | "Knows what it is." |
| **L2** | Use | Use it from a blank project without a tutorial for standard tasks. | "Has actually used it." |
| **L3** | Debug | Diagnose common failures from logs/errors/tools and fix the root cause. | "Could be productive on our team." |
| **L4** | Design / trade-offs | Choose between alternatives, explain internals that drive behaviour, justify decisions. | "Strong junior / ready to grow." |

Each drill file marks questions implicitly by section: Beginner ≈ L1, Intermediate ≈ L2,
Debugging ≈ L3, Architecture + Trade-offs ≈ L4.

---

## 6. Target level per technology

What junior / new-grad backend-leaning interviews actually expect:

| Technology | Target | Why this level |
|---|---|---|
| Java | **L4** | Primary language; used in every coding round. Expect internals (HashMap, GC, concurrency). |
| Spring Boot | **L3** (L4 on DI, transactions) | Main backend framework on résumé; probed hard. |
| SQL | **L4** | Live SQL questions are common; joins/aggregation/indexes must be solid. |
| PostgreSQL | **L3** | Primary DB in all projects; transactions, MVCC, `EXPLAIN`. |
| MySQL | **L2** | Taught as a diff; know InnoDB and the key differences. |
| REST APIs | **L4** | Design questions ("design an endpoint for…") are common. |
| JUnit | **L3** | Expect "how do you test this?" in every backend round. |
| Maven | **L2** (L3 on dependency conflicts) | Build tool questions are short but trip people up. |
| Git | **L3** | Merge/rebase/conflict questions; sometimes live. |
| GitHub | **L2** | PR flow, reviews, Actions basics. |
| JavaScript | **L3** | Closures, event loop, async. |
| TypeScript | **L2** | Types, unions, narrowing, generics basics. |
| React | **L3** | Hooks, state, effects, data fetching. |
| Docker | **L3** | Dockerfile, Compose, networking between containers. |
| AWS | **L2** | Five core services; IAM least privilege. |
| Redis | **L2** (L3 on caching) | Cache-aside, TTL, invalidation. |
| CI/CD | **L2** | Pipeline stages, secrets, what fails where. |
| Linux | **L2** | Everyday commands, permissions, processes, logs. |
| Python | **L1–L2** | Read/write scripts; not an interview language here. |

---

## 7. Weekly drill routine

### Weeks 8–22: 3 questions per week (≈45 min, inside the Saturday interview-practice block)

1. **Pick 3 questions** — one from each of the last three technologies you studied, rotating
   through [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md) and the relevant
   drill file's *Realistic interview questions* section. Start Week 8 with **Java, Git, SQL**.
2. **Answer out loud, recorded** (phone or screen recorder), no notes, 60–120 s each.
3. **Score** each answer 1–4 using the rubric in [`17-resume-tech-defense/README.md`](./17-resume-tech-defense/README.md#scoring-rubric).
4. **Fix**: for any score < 3, rewrite the answer in 5 sentences, look up the gap in the linked
   learning file, and re-record once.
5. **Log** in [`trackers/technology-tracker.md`](./trackers/technology-tracker.md) and update the
   readiness matrix below.

| Weeks | Techs in rotation (add as learned) |
|---|---|
| 8 | Java, Git, SQL |
| 9–10 | + Maven, JUnit, PostgreSQL, MySQL |
| 11–13 | + Spring Boot, REST APIs, GitHub, Python |
| 14–18 | + JavaScript, TypeScript, React, Docker |
| 19–22 | + Linux, Redis, AWS, CI/CD |

### Weeks 23–26: all technologies

- **Week 23:** one full pass over **every** technology: 3 realistic questions + 1 debugging
  question each, recorded. Rehearse the four gap templates from §3.
- **Week 24:** mock "résumé grill" — have a peer (or strictly-prompted AI) pick any 4 techs and
  probe 5 levels deep. Fix anything scoring < 3.
- **Week 25:** re-drill only techs whose matrix status is not ✅.
- **Week 26:** 10-minute daily "random tech" drill; carry into the maintenance plan.

---

## 8. Readiness matrix

Tick **Status** only when you score ≥ 3 on three consecutive recorded answers for that tech
*and* can point to the project evidence.

| Tech | Target level | Week learned | Project evidence | Status |
|---|---|---|---|---|
| [Java](./17-resume-tech-defense/java.md) | L4 | 1–4, 6, 13 | P1 domain model + streams; P2 concurrency test; P4 virtual-thread checker | - [ ] |
| [Spring Boot](./17-resume-tech-defense/spring-boot.md) | L3/L4 | 9–12 | P2 layered API, security, `@Transactional`; P3 RBAC; P4 scheduled worker | - [ ] |
| [Maven](./17-resume-tech-defense/maven.md) | L2/L3 | 3 | P1 `pom.xml`; P2 Surefire/Failsafe + `mvn verify` in CI | - [ ] |
| [JUnit](./17-resume-tech-defense/junit.md) | L3 | 2, 12 | P1 CSV import tests; P2 slice + Testcontainers tests | - [ ] |
| [REST APIs](./17-resume-tech-defense/rest-apis.md) | L4 | 9–10 | P2 ProblemDetail errors, pagination, Idempotency-Key; P4 rate-limited public API | - [ ] |
| [Git](./17-resume-tech-defense/git.md) | L3 | 1, 2, 5 | PR workflow from P1 M3; tagged releases v1.0 | - [ ] |
| [GitHub](./17-resume-tech-defense/github.md) | L2 | 1, 5, 12 | PR history, Actions workflows in P2/P4 | - [ ] |
| [SQL](./17-resume-tech-defense/sql.md) | L4 | 5–8 | P1 report queries; P3 filters/search; P4 check_results queries | - [ ] |
| [PostgreSQL](./17-resume-tech-defense/postgresql.md) | L3 | 5–8 | P1 schema + `EXPLAIN`; P2 Flyway; P4 composite index on RDS | - [ ] |
| [MySQL](./17-resume-tech-defense/mysql.md) | L2 | 8 | Ledger schema ported to MySQL 8 exercise | - [ ] |
| [JavaScript](./17-resume-tech-defense/javascript.md) | L3 | 14 | P3 frontend | - [ ] |
| [TypeScript](./17-resume-tech-defense/typescript.md) | L2 | 15 | P3 typed API client | - [ ] |
| [React](./17-resume-tech-defense/react.md) | L3 | 15–17 | P3 board + auth; P4 dashboard | - [ ] |
| [Docker](./17-resume-tech-defense/docker.md) | L3 | 10, 19 | P2 Dockerfile; P3/P4 Compose stacks | - [ ] |
| [Redis](./17-resume-tech-defense/redis.md) | L2/L3 | 20 | P4 status-page cache + token-bucket rate limiter | - [ ] |
| [AWS](./17-resume-tech-defense/aws.md) | L2 | 21 | P4 on EC2 + RDS + S3 + CloudWatch | - [ ] |
| [CI/CD](./17-resume-tech-defense/cicd.md) | L2 | 12, 22 | P2 `mvn verify` CI; P4 build → push → deploy | - [ ] |
| [Linux](./17-resume-tech-defense/linux.md) | L2 | 1, 19 | P4 EC2 ops, logs, systemd/Compose | - [ ] |
| [Python](./17-resume-tech-defense/python.md) | L1/L2 | 13 | Utility scripts (e.g. CSV generation for P1 tests) | - [ ] |

---

## 9. Index of drill files

Start at [`17-resume-tech-defense/README.md`](./17-resume-tech-defense/README.md) (how to use, scoring rubric, recording).

| Backend & language | Data | Tooling & delivery | Frontend | Other |
|---|---|---|---|---|
| [java.md](./17-resume-tech-defense/java.md) | [sql.md](./17-resume-tech-defense/sql.md) | [git.md](./17-resume-tech-defense/git.md) | [javascript.md](./17-resume-tech-defense/javascript.md) | [python.md](./17-resume-tech-defense/python.md) |
| [spring-boot.md](./17-resume-tech-defense/spring-boot.md) | [postgresql.md](./17-resume-tech-defense/postgresql.md) | [github.md](./17-resume-tech-defense/github.md) | [typescript.md](./17-resume-tech-defense/typescript.md) | [linux.md](./17-resume-tech-defense/linux.md) |
| [rest-apis.md](./17-resume-tech-defense/rest-apis.md) | [mysql.md](./17-resume-tech-defense/mysql.md) | [maven.md](./17-resume-tech-defense/maven.md) | [react.md](./17-resume-tech-defense/react.md) | |
| [junit.md](./17-resume-tech-defense/junit.md) | [redis.md](./17-resume-tech-defense/redis.md) | [docker.md](./17-resume-tech-defense/docker.md) | | |
| | | [aws.md](./17-resume-tech-defense/aws.md) | | |
| | | [cicd.md](./17-resume-tech-defense/cicd.md) | | |

Related: [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md) ·
[`16-interview-prep/project-deep-dive.md`](./16-interview-prep/project-deep-dive.md) ·
[`16-interview-prep/behavioral.md`](./16-interview-prep/behavioral.md) ·
[`PROJECTS.md`](./PROJECTS.md) · [`trackers/technology-tracker.md`](./trackers/technology-tracker.md)
