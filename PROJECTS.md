# 🛠️ PROJECTS — Four Projects, Built From a Blank File

> Root overview. **Full specs live in [`18-projects/`](./18-projects/README.md).**
> Timing and scope come from [`ROADMAP.md`](./ROADMAP.md) §7 — if anything here disagrees, ROADMAP wins.

| # | Project | Weeks | Spec | Tracker |
|---|---|---|---|---|
| **P1** | **Ledger** — personal-finance engine (CLI) | 3–8 | [`18-projects/p1-ledger/README.md`](./18-projects/p1-ledger/README.md) | [`trackers/project-tracker.md`](./trackers/project-tracker.md#p1--ledger) |
| **P2** | **TicketHold** — event seat-reservation REST API | 9–13 | [`18-projects/p2-tickethold/README.md`](./18-projects/p2-tickethold/README.md) | [`trackers/project-tracker.md`](./trackers/project-tracker.md#p2--tickethold) |
| **P3** | **TeamBoard** — full-stack issue tracker with RBAC | 14–18 | [`18-projects/p3-teamboard/README.md`](./18-projects/p3-teamboard/README.md) | [`trackers/project-tracker.md`](./trackers/project-tracker.md#p3--teamboard) |
| **P4** | **PulseWatch** — uptime monitoring & alerting platform | design 18, build 19–22, polish 23–25 | [`18-projects/p4-pulsewatch/README.md`](./18-projects/p4-pulsewatch/README.md) | [`trackers/project-tracker.md`](./trackers/project-tracker.md#p4--pulsewatch) |

Supporting material: [how to run a project](./18-projects/README.md) · [design-doc template](./18-projects/templates/design-doc.md) · [ADR template](./18-projects/templates/adr.md) · [project deep-dive interview prep](./16-interview-prep/project-deep-dive.md) · [résumé tech defense](./RESUME_TECH_DEFENSE.md)

---

## 1. Why 4 projects and not 15

Most "project lists" produce fifteen half-finished tutorial clones. Interviewers can tell in
about ninety seconds: ask one "why did you do it that way?" and the story collapses.

| 15 small projects | 4 deep projects (this roadmap) |
|---|---|
| Each is a thin slice: CRUD + a screen | Each forces a hard problem: messy input, concurrency, authorization, background work at scale |
| Code mostly copied from a video | Built from a blank file against a written spec |
| No tests, no history, no README | Every feature tested, every milestone a PR, README + diagram + ADRs |
| "I made a to-do app" | "Here's the test that proves two users can't book the same seat" |
| Nothing to say after minute 2 | 20 minutes of defensible trade-offs per project |
| Covers each technology once, shallowly | Each technology appears in 2–4 projects, deeper each time |

Four projects also match the time budget: ≈80 of the ≈550 roadmap hours are pure
project-building (ROADMAP §3), and project work reinforces what the weekly topics teach.
Depth over breadth is also what the **honesty rule** requires: every technology on your
résumé must be backed by something you actually built and can explain now.

---

## 2. Progression

Each project reuses the previous project's skills and adds exactly one or two hard new ideas.

| | P1 Ledger | P2 TicketHold | P3 TeamBoard | P4 PulseWatch |
|---|---|---|---|---|
| **Weeks** | 3–8 | 9–13 | 14–18 | 18 (design), 19–22, polish 23–25 |
| **Shape** | CLI + library | REST API | Full-stack web app | Distributed-ish platform (API + worker + cache + cloud) |
| **Core hard problem** | Messy input, rule engine, persistence behind an interface | Correctness under concurrency (no double-booking) | Authorization model spanning backend + frontend | Background work, caching, rate limiting, deploy + observe |
| **Persistence** | In-memory → JDBC + PostgreSQL | JPA + Flyway + PostgreSQL | JPA + Flyway + PostgreSQL | JPA/JDBC + Flyway + PostgreSQL (RDS) + Redis |
| **Testing** | JUnit 5, integration test vs local Postgres | + Mockito, `@WebMvcTest`, `@DataJpaTest`, Testcontainers, concurrency test | + authz tests, Vitest + React Testing Library | + worker/Redis tests with Testcontainers, k6 load test |
| **Delivery** | Runnable jar, tag `v1.0` | Docker image + Compose, CI (`mvn verify`) | Full Compose (nginx + API + Postgres) | AWS (EC2, RDS, S3, IAM, CloudWatch) via GitHub Actions CI/CD |
| **Git maturity** | Commits → PRs from W5 | PR per milestone, CI gate | Issues → branches → PRs, monorepo | Protected `main`, CD on tag/merge |
| **Interview story** | "Swapped storage behind an interface, with tests." | "Prevented double-booking; here's the proof test." | "Every hop from click to row; I designed the permission model." | "Background checks, Redis cache + rate limit, AWS via pipeline, tuned with EXPLAIN." |

---

## 3. Résumé technology × project matrix

Legend: **●** core (you will be asked about it in that project's deep dive) · **○** used / supporting · blank = not used.

| Résumé technology | P1 Ledger | P2 TicketHold | P3 TeamBoard | P4 PulseWatch | Defense file |
|---|:---:|:---:|:---:|:---:|---|
| Java (21) | ● | ● | ● | ● | [java.md](./17-resume-tech-defense/java.md) |
| Spring Boot (3.x) | | ● | ● | ● | [spring-boot.md](./17-resume-tech-defense/spring-boot.md) |
| PostgreSQL | ● | ● | ● | ● | [postgresql.md](./17-resume-tech-defense/postgresql.md) |
| SQL (general) | ● | ● | ● | ● | [sql.md](./17-resume-tech-defense/sql.md) |
| MySQL | ○ (diff notes / optional run) | | | | [mysql.md](./17-resume-tech-defense/mysql.md) |
| REST APIs | | ● | ● | ● | [rest-apis.md](./17-resume-tech-defense/rest-apis.md) |
| React | | | ● | ○ (status dashboard) | [react.md](./17-resume-tech-defense/react.md) |
| TypeScript | | | ● | ○ | [typescript.md](./17-resume-tech-defense/typescript.md) |
| JavaScript | | | ● | ○ (k6 script) | [javascript.md](./17-resume-tech-defense/javascript.md) |
| Docker | | ● | ● | ● | [docker.md](./17-resume-tech-defense/docker.md) |
| AWS | | | | ● | [aws.md](./17-resume-tech-defense/aws.md) |
| Redis | | | | ● | [redis.md](./17-resume-tech-defense/redis.md) |
| Git | ● | ● | ● | ● | [git.md](./17-resume-tech-defense/git.md) |
| GitHub | ○ | ● | ● | ● | [github.md](./17-resume-tech-defense/github.md) |
| Maven | ● | ● | ● | ● | [maven.md](./17-resume-tech-defense/maven.md) |
| JUnit | ● | ● | ● | ● | [junit.md](./17-resume-tech-defense/junit.md) |
| CI/CD | | ○ (CI) | ○ (CI) | ● (CI + CD) | [cicd.md](./17-resume-tech-defense/cicd.md) |
| Linux | ○ | ○ | ○ | ● (EC2, systemd/Compose, logs) | [linux.md](./17-resume-tech-defense/linux.md) |

Every row has at least one **●**. If an interviewer asks "where did you use Redis?", the
answer is PulseWatch, and you can open the Lua script.

---

## 4. Timeline (weeks)

```
Week:   3   4   5   6   7   8 | 9  10  11  12  13 | 14  15  16  17  18 | 19  20  21  22 | 23  24  25
P1     M1  M2  M3──M3  M4  M5 |                   |                    |                |
P2                            | M1  M2  M3  M4  M5 |                    |                |
P3                            |                   | M1  M2  M3  M4  M5 |                |
P4                            |                   |                 M0 | M1  M2  M3  M4 | polish────
Tags            P1 v1.0 ──▶ W8 · P2 v1.0 ──▶ W12 · P3 v1.0 ──▶ W18 · P4 v1.0 ──▶ W22
```

| Week | Milestone | Headline deliverable |
|---:|---|---|
| 3 | P1 M1 | Maven project, domain model, CSV import with per-row errors, JUnit tests |
| 4 | P1 M2 | Strategy-based categorization rules, stream reports, CLI commands |
| 5–6 | P1 M3 | Repository/Factory/Builder refactor, budgets + alerts, recurring detection; PRs from W5 |
| 7 | P1 M4 | PostgreSQL via JDBC behind `TransactionRepository` |
| 8 | P1 M5 | Atomic import, dedupe via unique constraint, `EXPLAIN` + index, **v1.0** |
| 9 | P2 M1 | Spring Boot skeleton, venues/events CRUD in memory, Postman collection |
| 10 | P2 M2 | JPA + Flyway + Postgres (Compose), DTOs, validation, ProblemDetail, pagination |
| 11 | P2 M3 | Spring Security + JWT, roles, method security, MDC request IDs, Actuator |
| 12 | P2 M4 | Holds with expiry, `@Version`, Idempotency-Key, full test pyramid, CI, Dockerfile, **v1.0** |
| 13 | P2 M5 | N-thread concurrency test, scheduled hold cleanup, README + diagram |
| 14 | P3 M1 | Design doc, ERD, Spring Boot + Flyway skeleton |
| 15 | P3 M2 | Per-org RBAC, issue CRUD, status workflow, filters/pagination/search |
| 16 | P3 M3 | React + TS (Vite) board, forms, typed API client, loading/error states |
| 17 | P3 M4 | Frontend auth, role-aware UI, Vitest + RTL, backend authz tests |
| 18 | P3 M5 + P4 M0 | Comments, audit log, full Compose, demo GIF, **P3 v1.0**; P4 design doc |
| 19 | P4 M1 | Monitors API, checker worker, `check_results`, Compose (api, worker, postgres, redis) |
| 20 | P4 M2 | Redis status cache, token-bucket rate limit, incidents, alerts w/ retry + idempotency, retention |
| 21 | P4 M3 | AWS: EC2 + Compose, RDS, S3 exports, IAM least privilege, CloudWatch |
| 22 | P4 M4 | CI/CD, Micrometer, JSON logs, composite index via `EXPLAIN`, k6, React dashboard, **v1.0** |
| 23–25 | P4 polish | README, architecture diagram, runbook, teardown/cost notes (+ polish of P1–P3) |

Budget: ≈5 project hours/week (Wed 2 h + Sat 3 h per ROADMAP §11). If a milestone slips, follow
[ROADMAP §12](./ROADMAP.md#12-rules-for-falling-behind): cut stretch goals, never the core or the tests.

---

## 5. Definition of "done" (applies to every project)

A project is **done** — and may appear on your résumé — only when **all** of these are true:

- [ ] All milestones in the spec are merged to `main` via PRs, each with its acceptance criteria ticked.
- [ ] `main` builds from a clean clone with one documented command (`mvn verify` / `docker compose up --build`).
- [ ] Tests pass locally **and** in CI (P2 onward); no `@Disabled` tests without a linked issue.
- [ ] Every feature has at least one test; the spec's "must-exist" test cases exist by name.
- [ ] README meets the checklist in [`18-projects/README.md`](./18-projects/README.md#readme-requirements).
- [ ] Architecture diagram (ASCII or image) is in the README.
- [ ] At least 3 ADRs in `docs/adr/` using the [ADR template](./18-projects/templates/adr.md).
- [ ] Design doc in `docs/design.md` using the [design-doc template](./18-projects/templates/design-doc.md), updated to match what was built.
- [ ] Release tag `v1.0.0` (annotated) with release notes.
- [ ] Deployment target met (P1 jar · P2 image + Compose · P3 full Compose · P4 AWS + CI/CD).
- [ ] You have done every "Break it" exercise and written the result in `docs/break-it.md`.
- [ ] You have recorded the 2-minute and 10-minute walkthroughs and can answer the spec's interview questions without notes.
- [ ] No secrets in the repo or Git history (`git log -p | grep -i -E "password|secret|key"` is clean or only placeholders).

---

## 6. The rules

1. **Build from a blank file.** `mvn archetype:generate` / Spring Initializr / `npm create vite@latest` are allowed. Copying a finished tutorial repo is not.
2. **No tutorial clones.** You may read docs and small snippets to learn an API. You may not follow a "build X step by step" video for the project itself. If you used a snippet, you must be able to explain each line.
3. **Every feature has a test.** A feature PR without a test is not mergeable. Bug fixes start with a failing test that reproduces the bug.
4. **Every milestone is a PR.** One milestone → one (or a few) PRs into `main`, with a description, a checklist, and self-review comments. From P2 onward, CI must be green before merge.
5. **Conventional Commits.** `feat:`, `fix:`, `test:`, `refactor:`, `docs:`, `chore:`, `build:`, `ci:`, `perf:`.
6. **README + architecture diagram + ADRs.** Written as you go, not at the end. Every non-obvious decision (e.g. "optimistic vs pessimistic locking") gets an ADR the week you make it.
7. **Break it on purpose.** Each spec has "Break it" exercises. Predict, break, observe, restore, write it down.
8. **Honesty.** These are learning projects built during this roadmap. On a résumé, list them under **Projects**, never as employment. Never claim users, traffic, or production usage you don't have. "Load-tested to 200 req/s with k6 on a t3.small" is honest; "serves thousands of users" is not.
9. **AI assistants.** Allowed for explaining errors and reviewing *your* code after you've tried. Not allowed for generating whole features. If you can't re-implement it without help, it isn't yours yet.

---

## 7. Where each project lives

**Each project is its own GitHub repository that you create.** This repository only holds the
**specs**, templates and trackers.

| Project | Suggested repo name | Visibility | Created in |
|---|---|---|---|
| P1 | `ledger` | Public | Week 3 |
| P2 | `tickethold` | Public | Week 9 |
| P3 | `teamboard` (monorepo: `backend/`, `frontend/`) | Public | Week 14 |
| P4 | `pulsewatch` (monorepo: `api/`, `worker/`, `dashboard/`, `infra/`) | Public | Week 18 |

Setup for every project repo:

- [ ] `README.md`, `LICENSE` (MIT is fine), `.gitignore` (Java/Maven, Node, IDE files, `.env`).
- [ ] `docs/design.md`, `docs/adr/0001-*.md`, `docs/break-it.md`.
- [ ] Branch protection on `main` (P2 onward): require PR + passing checks.
- [ ] GitHub Issues enabled; one issue per task; milestone objects named `M1`…`M5`.
- [ ] Pin the four repos on your GitHub profile once each reaches `v1.0.0`.
- [ ] Record the repo URL and each milestone PR link in [`trackers/project-tracker.md`](./trackers/project-tracker.md).

---

## 8. Links

- How to run any project (design doc → issues → branches → PRs, DoD, README template, demos): [`18-projects/README.md`](./18-projects/README.md)
- P1 spec: [`18-projects/p1-ledger/README.md`](./18-projects/p1-ledger/README.md)
- P2 spec: [`18-projects/p2-tickethold/README.md`](./18-projects/p2-tickethold/README.md)
- P3 spec: [`18-projects/p3-teamboard/README.md`](./18-projects/p3-teamboard/README.md)
- P4 spec: [`18-projects/p4-pulsewatch/README.md`](./18-projects/p4-pulsewatch/README.md)
- Templates: [`design-doc.md`](./18-projects/templates/design-doc.md) · [`adr.md`](./18-projects/templates/adr.md)
- Tracker: [`trackers/project-tracker.md`](./trackers/project-tracker.md)
- Interview use: [`16-interview-prep/project-deep-dive.md`](./16-interview-prep/project-deep-dive.md) · [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md)
