# 17 — Résumé Tech Defense (drill files)

One file per technology on your résumé. The **method** (7 layers, L1–L4 depth levels, gap
templates, readiness matrix) is in [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md).
Cross-technology questions are in [`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md).

> **Honesty rule:** these files train current, real competence. Every "how I used it" answer
> is either a truthful description of past work or a reference to P1–P4 built in this roadmap.
> Never rehearse an answer that describes work you didn't do.

---

## How to use this folder

1. **Only drill a tech after you've learned and used it** in the roadmap (see the "Week learned"
   column in the [readiness matrix](../RESUME_TECH_DEFENSE.md#8-readiness-matrix)). Drilling
   before building produces memorised answers that collapse under follow-ups.
2. **Answer before you open `<details>`.** Say the answer out loud first, then compare.
   Model answers are *minimum* answers — yours should add a detail from your own project.
3. **Personalise the "Realistic interview questions".** Each outline has a slot for *your*
   truthful past context and *your* project evidence. Write your version in your notes.
4. **Do the Practical tasks live**, timed, in a blank project. If you can't, that's the gap.
5. **Tick the Mastery checklist** only when you can do the item without notes today.
6. **Fill in "Evidence in my projects"** with real file paths / commit hashes as you build.

### Every file has the same structure

| Section | Depth | Use it for |
|---|---|---|
| Beginner questions | L1 | Warm-up, recruiter screens |
| Intermediate questions | L2 | Technical screens |
| Realistic interview questions | L2–L4 | Résumé-probing, recorded drills |
| Practical tasks | L2 | Live-coding / "share your screen" rounds |
| Debugging questions | L3 | "What would you do if…" |
| Architecture questions | L4 | Design and trade-off discussion |
| Common mistakes · Terminology · When (not) to use · Trade-offs · Stack interactions | L1–L4 | Fast review before an interview |
| Hands-on exercise · Mastery checklist | — | Proving it to yourself |
| Evidence in my projects · Where to learn it | — | Linking claims to code and study material |

---

## Scoring rubric

Score every recorded answer. Target: **≥ 3** on three consecutive attempts per tech.

| Score | Label | What it sounds like |
|---:|---|---|
| **1** | Vague | Buzzwords, no mechanism, no example. "Spring does dependency injection so it's loosely coupled." Hesitation > 5 s, or incorrect facts. |
| **2** | Correct but generic | Accurate definition, but no example from your work or projects; can't handle the first follow-up. |
| **3** | Specific | Correct mechanism + a concrete example from P1–P4 (or truthful past work) + one trade-off. Survives one follow-up. |
| **4** | Interview-strong | Everything in 3, plus: structured (what → how → example → trade-off), 60–120 s, anticipates the next follow-up, admits limits cleanly ("I haven't used X in production, but here's how I'd reason about it"). |

**Automatic deductions (−1):** claiming experience you don't have; rambling > 3 minutes;
an answer that contradicts your own project code.

---

## Recording yourself

- **Tooling:** phone voice memo is enough; screen recording (OBS / built-in) when you're
  explaining code. Keep recordings in a private folder named `YYYY-MM-DD_tech_question`.
- **Protocol:** read the question → 5 s pause → answer without notes → stop.
- **Review (same day):** listen at 1.25×. Write down (a) filler words count, (b) the first
  inaccurate or vague sentence, (c) the missing project example. Score 1–4.
- **Re-record** anything below 3 once, after fixing the gap. Don't re-record more than twice
  in a session — come back next week instead (spaced repetition beats cramming).
- **Log** the score in [`../trackers/technology-tracker.md`](../trackers/technology-tracker.md).

---

## Link index

| Tech | File | Target | Primary study material |
|---|---|---|---|
| Java | [java.md](./java.md) | L4 | [`../01-java/`](../01-java/README.md) |
| Spring Boot | [spring-boot.md](./spring-boot.md) | L3/L4 | [`../05-spring-boot/`](../05-spring-boot/README.md) |
| Maven | [maven.md](./maven.md) | L2/L3 | [`../01-java/08-maven-build.md`](../01-java/08-maven-build.md) |
| JUnit | [junit.md](./junit.md) | L3 | [`../09-testing/`](../09-testing/README.md) |
| REST APIs | [rest-apis.md](./rest-apis.md) | L4 | [`../06-rest-apis/`](../06-rest-apis/README.md) |
| Git | [git.md](./git.md) | L3 | [`../02-git/`](../02-git/README.md) |
| GitHub | [github.md](./github.md) | L2 | [`../02-git/workflows.md`](../02-git/workflows.md), [`../13-cicd/`](../13-cicd/README.md) |
| SQL | [sql.md](./sql.md) | L4 | [`../04-sql-databases/`](../04-sql-databases/README.md) |
| PostgreSQL | [postgresql.md](./postgresql.md) | L3 | [`../04-sql-databases/`](../04-sql-databases/README.md) |
| MySQL | [mysql.md](./mysql.md) | L2 | [`../04-sql-databases/07-postgres-vs-mysql.md`](../04-sql-databases/07-postgres-vs-mysql.md) |
| JavaScript | [javascript.md](./javascript.md) | L3 | [`../07-javascript-typescript/`](../07-javascript-typescript/README.md) |
| TypeScript | [typescript.md](./typescript.md) | L2 | [`../07-javascript-typescript/03-typescript.md`](../07-javascript-typescript/03-typescript.md) |
| React | [react.md](./react.md) | L3 | [`../08-react/`](../08-react/README.md) |
| Docker | [docker.md](./docker.md) | L3 | [`../11-docker/`](../11-docker/README.md) |
| Redis | [redis.md](./redis.md) | L2/L3 | [`../04-sql-databases/redis.md`](../04-sql-databases/redis.md) |
| AWS | [aws.md](./aws.md) | L2 | [`../12-aws/`](../12-aws/README.md) |
| CI/CD | [cicd.md](./cicd.md) | L2 | [`../13-cicd/`](../13-cicd/README.md) |
| Linux | [linux.md](./linux.md) | L2 | [`../10-linux/`](../10-linux/README.md) |
| Python | [python.md](./python.md) | L1/L2 | [`../19-python/`](../19-python/README.md) |

Back to [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · [`../README.md`](../README.md)
