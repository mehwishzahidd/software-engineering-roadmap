# 16 — Interview Prep

Everything for turning competence into offers: coding rounds, behavioral, project
deep-dives, recruiter screens, junior system design, mocks, and the post-Week-26 routine.
Online assessments have their own root file: [`../OA_PREP.md`](../OA_PREP.md).
Day-of checklists: [`../INTERVIEW_CHECKLIST.md`](../INTERVIEW_CHECKLIST.md).

## The two-track model (ROADMAP §10)

Interviews test two different things, and this roadmap prepares them in two different languages. **Never mix them.**

| Track | Language | What it covers | Where it's trained |
|---|---|---|---|
| **A · Coding interview** | **Python** | LeetCode/NeetCode problems, OAs (Amazon-style, HackerRank, CodeSignal), timed problems, explaining solutions, Big-O, debugging algorithm code | [`coding-interview-method.md`](./coding-interview-method.md), [`../PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md), [`../03-dsa/`](../03-dsa/), [`../OA_PREP.md`](../OA_PREP.md) |
| **B · Software-engineering / résumé interview** | Java, Spring Boot, SQL, TS/React, Docker, Redis, AWS, CI/CD | Project deep-dives (FlowGrid, LedgerX, ForgeCI, FlagForge), architecture, concurrency, debugging an unfamiliar codebase, testing, system design, technology trade-offs, résumé probing, behavioral | [`project-deep-dive.md`](./project-deep-dive.md), [`behavioral.md`](./behavioral.md), [`system-design-interview.md`](./system-design-interview.md), [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md), [`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md), [`../17-resume-tech-defense/`](../17-resume-tech-defense/), [`../21-debugging-code-reading/`](../21-debugging-code-reading/) |

The one place they meet: OA simulations whose "existing codebase / failing tests" slot uses the Java [`buggy-library`](../21-debugging-code-reading/exercises/buggy-library/) while the algorithm problems are solved in Python. Each of the four projects also ships a **Python tooling component** (generators, load harnesses, verifiers, a Python SDK) — that's Python evidence for Track B, but you present it as engineering, not as a LeetCode skill.

## Target loops

| Role type | Typical pipeline | Track A parts | Track B parts |
|---|---|---|---|
| SWE internship | Recruiter screen → OA → 1–2 technical → sometimes behavioral | OA, coding rounds | résumé/project questions inside the technical round |
| Junior SWE / entry-level SDE | Recruiter → OA or phone screen → 3–5 round loop | 2–3 coding rounds | 1 project/experience round, 1 behavioral, maybe light design |
| New-grad-style loop (e.g. Amazon SDE I) | OA (coding + work style / work simulation) → loop of ~3–4 rounds, each coding or design **plus** Leadership-Principle questions | OA coding, coding rounds | LP behavioral in every round, design round |
| Startups / smaller companies | Recruiter/founder call → take-home or live coding in a real repo → project deep-dive → team fit | sometimes a short algorithm screen | live coding in a real repo (Java or Python), project deep-dive |
| Résumé-driven technical | "You list Spring Boot / PostgreSQL / Docker — explain…" | — | all of it |

## Files

| File | Track | Use it for | Start week |
|---|---|---|---:|
| [`coding-interview-method.md`](./coding-interview-method.md) | A | The 9-step method, exact phrases, hints, being stuck, 45-min budget, Python-specific tips | 5 |
| [`behavioral.md`](./behavioral.md) | B | STAR/STARL, 12-story bank, Amazon LP mapping, handling the career gap honestly | 8 |
| [`project-deep-dive.md`](./project-deep-dive.md) | B | 30 s / 2 min / 10–15 min explanations of FlowGrid, LedgerX, ForgeCI, FlagForge (incl. their Python tooling) + drill-down questions | 8 (FlowGrid) |
| [`mock-interviews.md`](./mock-interviews.md) | A + B | Mock formats, Track A and Track B rubrics (1–4), schedule, feedback log | 10 |
| [`recruiter-screen.md`](./recruiter-screen.md) | B | The first call: pitch, logistics, salary, gap question | 8 |
| [`system-design-interview.md`](./system-design-interview.md) | B | Junior-level design framework, using the four projects as evidence | 20 (fundamentals), 22 (mock) |
| [`maintenance-plan.md`](./maintenance-plan.md) | A + B | Weekly routine after Week 26 until you sign an offer | 26 |

## Interview-practice ramp (from ROADMAP §10)

| Weeks | Activity | Files |
|---|---|---|
| 1–4 | Explain every DSA solution (Python) out loud after solving it. Draft 60-second "about me". | [`behavioral.md`](./behavioral.md) §3 |
| 5–13 | Think-aloud 1×/week (one Medium in Python, recorded, scored against the checklist). Résumé-defense drills from W7 (3 questions/week). Story bank from W8. **Mock #1 in Week 10.** Project deep-dive rehearsal at the end of each project (W8 FlowGrid, W13 LedgerX). | [`coding-interview-method.md`](./coding-interview-method.md), [`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md), [`project-deep-dive.md`](./project-deep-dive.md) |
| 14–23 | **Weekly mock** alternating Track A / Track B (peer / Pramp-style / AI interviewer with strict rules). Unfamiliar-code drills from W13. OA sims W17, W18, W19, W20, W22, W23. System-design mock W22. Deep-dive rehearsals W19 ForgeCI, W23 FlagForge. | [`mock-interviews.md`](./mock-interviews.md), [`../OA_PREP.md`](../OA_PREP.md), [`system-design-interview.md`](./system-design-interview.md) |
| 24–26 | 2 mocks/week, OA sims #7–8 in W24, full loops (A + B rounds), repository-modification drills, recruiter-screen rehearsal, all-tech résumé grill. | all |

All logs go in [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md). ROADMAP §5 targets **≥ 14 mocks** and **≥ 8 OA simulations**.

## The honesty rule (applies to every file here)

You describe your past roles truthfully, your gap truthfully, and your projects as what
they are: projects you built during this roadmap. You never inflate titles, invent
responsibilities, claim team size or scale you didn't have, or present roadmap projects as
paid work. The whole plan is built so the truth is a strong enough answer.
