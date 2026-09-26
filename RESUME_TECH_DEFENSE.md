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
JavaScript, Docker, AWS, Redis, Git, GitHub, Maven, JUnit, CI/CD, Linux, Python**.
Interviewers for internship / junior / new-grad roles will pick 2–4 of these and probe until
they find the edge of your knowledge. Résumé-driven probing typically looks like:

1. "I see Spring Boot on your résumé — tell me how you used it." (**context**)
2. "How does dependency injection work there?" (**concept**)
3. "What happens if two beans implement the same interface?" (**mechanics**)
4. "You got a `LazyInitializationException` in production — what do you do?" (**debugging**)
5. "Why Spring Boot and not something lighter?" (**trade-offs**)

This system exists so that at every one of those five steps you have a **true, current,
specific** answer, backed by code you can show.

**Two tracks, never mixed** (ROADMAP §10): **Track A** is the coding interview — Python, DSA, OAs,
drilled from [`03-dsa/`](./03-dsa/) and [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md).
**Track B** is the software-engineering / résumé interview — Java, Spring, SQL, TS/React, Docker,
Redis, AWS, CI/CD, the four projects — which is what this file and its drill folder train. Python
appears in *both*: as the language you code in (A) and as a résumé technology backed by each
project's Python component (B).

---

## 2. The honesty principle

**Non-negotiable rules:**

- [ ] I never claim a project, feature, responsibility, team size, metric, or tool I did not actually work on.
- [ ] I never inflate duration ("3 years of Spring Boot" when it was one project for 8 months).
- [ ] I never imply recent daily use of something I last touched years ago.
- [ ] When I don't know, I say so, then reason out loud from what I *do* know.
- [ ] Every technical claim I make about the *present* is backed by one of my roadmap projects (FlowGrid, LedgerX, ForgeCI, FlagForge) or a drill I can repeat live.

**Why this is also the winning strategy:** fabricated experience collapses at question 3 or
4 of a probe, and interviewers compare notes. Truthful past + deep present + visible evidence
survives any depth of questioning, because every answer is something you actually did.

**The three sources you are allowed to draw on:**

| Source | What it proves | How to phrase it |
|---|---|---|
| **Past roles** (Junior Dev → Developer → SE → contract SE) | You have worked in real teams on real systems | Past tense, specific, truthful scope: "In my role at X, I maintained…" |
| **Current knowledge** (this roadmap) | You understand it *now*, at depth | Present tense: "The way it works is…", "I'd debug it by…" |
| **Roadmap projects** FlowGrid (W4–8), LedgerX (W9–13), ForgeCI (W14–19), FlagForge (W20–23) | You can build with it *now* | "In FlowGrid I implemented… here's the test that proves it." |

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
> built FlowGrid, a multi-warehouse inventory and fulfillment API where I handled concurrent
> reservations with `SELECT … FOR UPDATE` (measured against optimistic locking) and proved it with
> an N-threads-one-unit test — then LedgerX, a double-entry ledger with idempotent transfers.
> Happy to walk through that code."

**Template B — used lightly in the past, deeper now:**
> "In my earlier role I used Docker mainly to run a local database someone else had set up —
> I didn't write the Dockerfiles. Since then I've learned it properly: I wrote the multi-stage
> Dockerfile and Compose stack for FlowGrid, and in ForgeCI I wrote the worker that starts and
> cleans up a Docker container per CI job through the Engine API. I can explain the layer caching choices."

**Template C — on the résumé, but honestly shallow:**
> "I'd put MySQL at a working level rather than expert. I used it for [truthful scope]. My
> deeper database experience now is PostgreSQL — in FlowGrid and LedgerX I used transactions,
> row locking, constraints, triggers and `EXPLAIN ANALYZE` — and I know the main MySQL/InnoDB differences."

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

Rehearse all four templates out loud during the Week 7 drills, again when the story bank starts in Week 8, and once more in Week 23.

---

## 4. The defense method (7 layers)

For **every** technology, you must be able to answer these seven layers without notes.
Each drill file in `17-resume-tech-defense/` is organised so you can build each layer.

| # | Layer | The question behind it | Example (PostgreSQL) |
|---|---|---|---|
| 1 | **What** | Define it in two sentences. | "An open-source relational database with MVCC, strong SQL standard support, and rich types like JSONB." |
| 2 | **Why** | What problem does it solve; why was it chosen? | "Relational integrity with constraints + transactions; the data was relational." |
| 3 | **How I used / use it** | Truthful past use + current project use. | "Past: queried reporting tables. Now: FlowGrid schema via Flyway, `FOR UPDATE` reservations; LedgerX triggers and constraints." |
| 4 | **How it interacts with my stack** | Where does it sit relative to Java/Spring/React/Docker/AWS/CI? | "JDBC → HikariCP → Postgres in Docker locally, RDS in prod, Testcontainers in CI." |
| 5 | **Problems & debugging** | A real problem you hit and how you found the root cause. | "Slow report query → `EXPLAIN ANALYZE` showed seq scan → composite index." |
| 6 | **Trade-offs** | When would you *not* use it; what did you give up? | "Vertical scaling limits; ops cost vs DynamoDB; but joins and constraints were needed." |
| 7 | **Evidence in my projects** | File, commit, test, or screenshot you can point to. | "`V7__add_log_chunks_index.sql` in ForgeCI + before/after plans in PERFORMANCE.md." |

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
| Python | **L3** | Primary coding-interview language (Track A) *and* every project's tooling language. Expect language-level probes (dict/set complexity, mutability, generators, GIL) and "walk me through your Python tool". |

---

## 7. Weekly drill routine

### Weeks 7–22: 3 questions per week (≈45 min, inside the Saturday interview-practice block)

1. **Pick 3 questions** — one from each of the last three technologies you studied, rotating
   through [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md) and the relevant
   drill file's *Realistic interview questions* section. Start Week 7 with **Java, Git, SQL** —
   the three that FlowGrid has already proved by then.
2. **Answer out loud, recorded** (phone or screen recorder), no notes, 60–120 s each.
3. **Score** each answer 1–4 using the rubric in [`17-resume-tech-defense/README.md`](./17-resume-tech-defense/README.md#scoring-rubric).
4. **Fix**: for any score < 3, rewrite the answer in 5 sentences, look up the gap in the linked
   learning file, and re-record once.
5. **Log** in [`trackers/technology-tracker.md`](./trackers/technology-tracker.md) and update the
   readiness matrix below.

| Weeks | Techs in rotation (add as learned — a tech enters the week after the project first proves it) |
|---|---|
| 7 | Java, Git, SQL |
| 8 | + Spring Boot, REST APIs, PostgreSQL, JUnit, Maven, MySQL (diff, taught W8), Python (daily since W1; FlowGrid tools W7–8) |
| 9–10 | + GitHub, Docker, JavaScript, TypeScript, React (all proven by FlowGrid v1.0) |
| 11–13 | + AWS, CI/CD, Redis (FlowGrid M5) |
| 14–22 | + Linux (W14 deep dive); full rotation, weighted toward the active project (ForgeCI → FlagForge) |

### Weeks 23–26: all technologies

- **Week 23:** one full pass over **every** technology: 3 realistic questions + 1 debugging
  question each, recorded. Rehearse the four gap templates from §3.
- **Week 24:** mock "résumé grill" — have a peer (or strictly-prompted AI) pick any 4 techs and
  probe 5 levels deep. Fix anything scoring < 3.
- **Week 25:** re-drill only techs whose matrix status is still ☐.
- **Week 26:** 10-minute daily "random tech" drill; carry into the maintenance plan.

---

## 8. Readiness matrix

Change **Status** from ☐ to ☑ (GitHub does not render `- [ ]` inside tables) only when you score ≥ 3 on three consecutive recorded answers for that tech
*and* can point to the project evidence.

| Tech | Target level | Week learned | Project evidence | Status |
|---|---|---|---|---|
| [Java](./17-resume-tech-defense/java.md) | L4 | 1–3, 5, 15 | FlowGrid domain model + allocation `Comparator`; LedgerX `BigDecimal` money + invariants; ForgeCI worker concurrency; FlagForge SDK | ☐ |
| [Spring Boot](./17-resume-tech-defense/spring-boot.md) | L3/L4 | 3–5, 10, 13, 17 | All four: FlowGrid layered API + JWT roles + `@Transactional` locking; LedgerX propagation + outbox; ForgeCI api + worker apps; FlagForge multi-tenant authz | ☐ |
| [Maven](./17-resume-tech-defense/maven.md) | L2/L3 | 2, 14, 22 | Every project's `pom.xml` + `mvn verify` in CI; ForgeCI multi-module reactor; FlagForge SDK as a separate artifact | ☐ |
| [JUnit](./17-resume-tech-defense/junit.md) | L3 | 2, 5, 12, 18 | FlowGrid slice + Testcontainers + N-threads test; LedgerX invariant / concurrency / crash suites; ForgeCI chaos-style integration tests | ☐ |
| [REST APIs](./17-resume-tech-defense/rest-apis.md) | L4 | 3–5, 11, 22 | FlowGrid ProblemDetail, pagination, Idempotency-Key, OpenAPI; LedgerX cursor pagination; ForgeCI webhooks + SSE; FlagForge SDK-facing API | ☐ |
| [Git](./17-resume-tech-defense/git.md) | L3 | 1, 2, 5 | PR-per-milestone across all four, tags `v1.0`; ForgeCI consumes Git (clone, commit SHAs) | ☐ |
| [GitHub](./17-resume-tech-defense/github.md) | L2 | 1, 5, 14 | Actions in every repo from W4; ForgeCI uses GitHub OAuth, webhooks (HMAC, dedupe) and the REST API | ☐ |
| [SQL](./17-resume-tech-defense/sql.md) | L4 | 3–6, 9 | FlowGrid inventory schema + report queries; LedgerX constraints/triggers + reconciliation queries | ☐ |
| [PostgreSQL](./17-resume-tech-defense/postgresql.md) | L3 | 3–6, 8, 9 | FlowGrid `FOR UPDATE` reservations + RDS (W8); LedgerX append-only trigger + isolation experiments; ForgeCI `log_chunks` index | ☐ |
| [MySQL](./17-resume-tech-defense/mysql.md) | L2 | 8 (diff study only) | FlowGrid inventory schema ported to MySQL 8 in a throwaway branch | ☐ |
| [JavaScript](./17-resume-tech-defense/javascript.md) | L3 | 6 | FlowGrid dashboard (W7); ForgeCI live logs (W16); FlagForge admin (W23); k6 scripts | ☐ |
| [TypeScript](./17-resume-tech-defense/typescript.md) | L2 | 7 | FlowGrid typed API client; ForgeCI `JobStatus` unions; FlagForge rule types | ☐ |
| [React](./17-resume-tech-defense/react.md) | L3 | 7, 16, 23 | FlowGrid operations dashboard; ForgeCI live-log SSE view; FlagForge admin dashboard | ☐ |
| [Docker](./17-resume-tech-defense/docker.md) | L3 | 4, 5, 14, 18 | All four Compose stacks + Dockerfiles; ForgeCI executes CI jobs in containers via the Engine API | ☐ |
| [Redis](./17-resume-tech-defense/redis.md) | L2/L3 | 6, 15, 21 | FlowGrid catalog cache; ForgeCI queue / pub-sub / limits; FlagForge snapshot cache + propagation | ☐ |
| [AWS](./17-resume-tech-defense/aws.md) | L2 | 7–8, 13, 19, 23 | FlowGrid first deploy (W8, EC2 + RDS + S3 + CloudWatch); repeated for LedgerX, ForgeCI (api + workers), FlagForge | ☐ |
| [CI/CD](./17-resume-tech-defense/cicd.md) | L2 | 4, 8, 18 | FlowGrid `mvn verify` from W4, full pipeline W8; ForgeCI is itself a CI system | ☐ |
| [Linux](./17-resume-tech-defense/linux.md) | L2 | 1, 14 | EC2 ops for every deploy; ForgeCI workers run processes and signals inside Docker | ☐ |
| [Python](./17-resume-tech-defense/python.md) | L3 | 1–3, then daily | **Track A:** all DSA/OA in Python from W1. **Project tools:** FlowGrid inventory/order generator + load harness (W7–8); LedgerX *independent* reconciliation verifier + data generator (W12); ForgeCI test-repo generator + webhook/load simulator + log analysis (W18); FlagForge rollout simulator + Python SDK/test client + config linter (W22–23) | ☐ |

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
