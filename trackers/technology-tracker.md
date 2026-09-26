# 🧰 Technology Tracker

Tracks how deeply I know each technology — not whether I've "covered" it.
Update at every checkpoint (Weeks 4, 8, 12, 16, 20, 24).

[← README](../README.md) · [Résumé tech defense](../RESUME_TECH_DEFENSE.md) · [Weekly progress](./weekly-progress.md) · [Project tracker](./project-tracker.md)

---

## Depth levels

| Level | Name | I can… | Evidence required |
|---|---|---|---|
| **L0** | Unfamiliar | …not explain it yet | — |
| **L1** | Define | …explain what it is and why it exists in my own words | Say it out loud in < 60 s without notes |
| **L2** | Use | …build something with it from a blank file, looking up only syntax | A commit in one of my projects |
| **L3** | Debug | …diagnose and fix realistic failures; know the common pitfalls | A bug I found and fixed (issue/PR/notes) |
| **L4** | Design | …choose it (or not), explain trade-offs and alternatives, reason about production concerns | Can answer the "architecture" and "when NOT to use" sections of its defense file |

**Targets for junior interviews:** Very-high priority → **L4**; High → **L3**; Medium-high → **L2–L3**; Low → **L1–L2**.

---

## Tracker

| Technology | Track | Priority | Target | Learned (wk) | CP-4 | CP-8 | CP-12 | CP-16 | CP-20 | CP-24 | Project evidence | Defense / guide |
|---|:-:|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|---|
| Python (interview fluency) | A | 🔴 Very high | L4 | 1–3, daily | | | | | | | DSA tracker; OA sims | [python](../17-resume-tech-defense/python.md) · [cheatsheet](../PYTHON_INTERVIEW_CHEATSHEET.md) |
| Python (tooling) | B | 🔴 Very high | L3 | 3, 7–8, 12, 18, 22–23 | — | | | | | | FlowGrid harness · LedgerX verifier · ForgeCI simulators · FlagForge SDK/simulator | [19-python](../19-python/) |
| DSA / patterns | A | 🔴 Very high | L4 | 1–26 | | | | | | | [dsa-tracker](./dsa-tracker.md) | [03-dsa](../03-dsa/) |
| Java | B | 🔴 Very high | L4 | 1–3, 5, 15 | | | | | | | all four backends | [java](../17-resume-tech-defense/java.md) |
| Spring Boot | B | 🔴 Very high | L4 | 3–5, 10, 13, 17 | | | | | | | all four | [spring-boot](../17-resume-tech-defense/spring-boot.md) |
| SQL | B | 🔴 Very high | L4 | 3–6, 9 | | | | | | | FlowGrid, LedgerX | [sql](../17-resume-tech-defense/sql.md) |
| PostgreSQL | B | 🔴 Very high | L4 | 3–6, 9, 25 | | | | | | | all four; LedgerX constraints/triggers | [postgresql](../17-resume-tech-defense/postgresql.md) |
| REST APIs / HTTP | B | 🔴 Very high | L4 | 3–5, 11, 22 | | | | | | | all four; FlagForge SDK API | [rest-apis](../17-resume-tech-defense/rest-apis.md) |
| JPA / Hibernate | B | 🟠 High | L3 | 4, 10 | | | | | | | FlowGrid, LedgerX, ForgeCI, FlagForge | [spring-boot](../17-resume-tech-defense/spring-boot.md) |
| JUnit 5 / Mockito / Testcontainers | B | 🟠 High | L3 | 2, 5, 12, 18 | | | | | | | LedgerX invariant & concurrency suites | [junit](../17-resume-tech-defense/junit.md) |
| Concurrency (Java) | B | 🟠 High | L3 | 5, 15, 17 | | | | | | | FlowGrid reservations · LedgerX transfers · ForgeCI workers | [01-java/07](../01-java/07-concurrency.md) |
| Redis | B | 🟠 High | L3 | 6, 15, 21 | — | | | | | | FlowGrid cache · ForgeCI queue/pub-sub · FlagForge snapshots | [redis](../17-resume-tech-defense/redis.md) |
| Docker | B | 🟠 High | L3 | 4–5, 14, 18 | | | | | | | all four; ForgeCI runs jobs in containers | [docker](../17-resume-tech-defense/docker.md) |
| Git | B | 🟠 High | L3 | 1, 2, 5 | | | | | | | all repos | [git](../17-resume-tech-defense/git.md) |
| GitHub (incl. OAuth, webhooks, API) | B | 🟠 High | L3 | 1, 5, 14 | | | | | | | ForgeCI integration | [github](../17-resume-tech-defense/github.md) |
| Maven | B | 🟠 High | L3 | 2, 14, 22 | | | | | | | ForgeCI multi-module · FlagForge SDK artifact | [maven](../17-resume-tech-defense/maven.md) |
| JavaScript | B | 🟡 Medium-high | L2–L3 | 6 | — | | | | | | FlowGrid dashboard | [javascript](../17-resume-tech-defense/javascript.md) |
| TypeScript | B | 🟡 Medium-high | L3 | 7 | — | | | | | | FlowGrid, ForgeCI, FlagForge UIs | [typescript](../17-resume-tech-defense/typescript.md) |
| React | B | 🟡 Medium-high | L3 | 7, 16, 23 | — | | | | | | FlowGrid ops dashboard · ForgeCI live logs · FlagForge admin | [react](../17-resume-tech-defense/react.md) |
| CI/CD (GitHub Actions) | B | 🟡 Medium-high | L3 | 4, 8, 18 | | | | | | | all four; ForgeCI *is* a CI system | [cicd](../17-resume-tech-defense/cicd.md) |
| AWS (IAM, EC2, S3, RDS, CloudWatch) | B | 🟡 Medium-high | L2–L3 | 7–8, 13, 19, 23 | — | | | | | | all four deployed | [aws](../17-resume-tech-defense/aws.md) |
| Linux / Bash | B | 🟡 Medium-high | L3 | 1, 14 | | | | | | | ForgeCI workers/Docker hosts | [linux](../17-resume-tech-defense/linux.md) |
| CS fundamentals | B | 🟡 Medium-high | L3 | 5, 9, 14, 15 | | | | | | | — | [14-cs-fundamentals](../14-cs-fundamentals/) |
| System design (junior) | B | 🟡 Medium-high | L2–L3 | 20–23 | — | — | — | — | | | the four projects as case studies | [15-system-design](../15-system-design/) |
| MySQL (diff vs Postgres) | B | 🟢 Low | L2 | 6 | — | | | | | | — | [mysql](../17-resume-tech-defense/mysql.md) |
| C++ basics | B | 🟢 Low | L1 | 21 | — | — | — | — | | | — | [20-cpp-basics](../20-cpp-basics/) |
| HTML / CSS | B | 🟢 Low | L1–L2 | 7 | — | | | | | | project UIs | — |

Write the level (e.g. `L2`) in each checkpoint column. `—` = not yet taught.

---

## Gaps log

When a checkpoint or mock exposes a gap, log it here and link the fix.

| Date | Technology | Track | Gap (specific) | Found by | Fix (file / exercise) | Closed |
|---|---|:-:|---|---|---|:-:|
| | | | | | | ⬜ |
