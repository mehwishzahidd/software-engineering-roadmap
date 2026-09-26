# Checkpoints — pass/fail gates every four weeks

> Six honest exams of *me*, not of the repository. Each one answers one gate question from
> [`../ROADMAP.md` §9](../ROADMAP.md#9-checkpoints). Pass → keep going. Fail → keep going **and**
> remediate inside the next two weeks' review blocks. The calendar never stops.

## Why checkpoints exist

A roadmap without gates turns into "I read about it" instead of "I can do it". Every four weeks
I stop building and measure, under exam conditions, whether the last phase actually stuck:

- **Can I produce the artefact from a blank file** (code, query, endpoint, test) — not recognise it?
- **Can I do it in the time an interviewer or an OA gives me?** (Track A: Python coding.)
- **Can I build and defend the Java/Spring work?** (Track B: engineering and résumé interview.)
- **Can I explain what I built, out loud, without notes?**
- **Is the project at the scope tier the phase promised?**

The result goes into [`../trackers/weekly-progress.md`](../trackers/weekly-progress.md) and the
checkpoint's own results log. Trend across checkpoints matters more than any single score.

## The six checkpoints

| Checkpoint | Sunday of week | Gate question (ROADMAP §9) | Project under review | Timed coding |
|---|---:|---|---|---|
| [CP-4](./checkpoint-04.md) | 4 | Can I write tested Java, basic SQL and a secured Spring Boot CRUD API (FlowGrid M1) without a tutorial? | FlowGrid M1 | 2 Easy in 40 min |
| [CP-8](./checkpoint-08.md) | 8 | Is FlowGrid's Strong Résumé Version deployed, tested and explainable? Easies reliable, first Mediums? | FlowGrid v1.0 | 3 Easy in 45 min + 1 Medium attempt |
| [CP-12](./checkpoint-12.md) | 12 | Can I reason about and *prove* transactional correctness, locking and idempotency (LedgerX M4)? | LedgerX M4 | 2 Medium in 60 min |
| [CP-16](./checkpoint-16.md) | 16 | Do I have a working queue → worker → container → live-log pipeline (ForgeCI M3)? Mediums ≤ 30 min? | ForgeCI M3 | 2 Medium in 50 min |
| [CP-20](./checkpoint-20.md) | 20 | Is ForgeCI shipped with failure recovery? Can I design and explain a system at junior level? | ForgeCI v1.0 (+M6) / FlagForge M1 | OA-style, 90 min |
| [CP-24](./checkpoint-24.md) | 24 | Would I pass an internship/junior loop today, including a 15-minute deep dive without notes? | All four | Full mock loop |

## Structure of every checkpoint file

| Part | What | Typical time |
|---|---|---|
| **A · Timed coding (Track A — Python)** | Unseen problems chosen by the selection rules in the file, from patterns covered so far, solved **in Python**. Strict clock. Plus a short **Java rep**: one already-solved problem re-implemented in Java with the required collections (a few points, feeds "Java (engineering)"). | 40–90 min |
| **B · Knowledge questions** | 20–30 short-answer questions covering that phase's learning: Java/Spring/SQL/CS **and** Python interview pitfalls (mutable defaults, aliasing, copies, `heapq`, dict/set complexity, recursion limit). Answers hidden in `<details>` — write yours first. | 40–60 min |
| **C · Practical task (Track B — Java/Spring/SQL)** | Build/fix/query something real in the current project or the practice repo, under a time limit. From CP-12 it also verifies the project's **Python component** (e.g. run the LedgerX verifier against a seeded database). | 60–90 min |
| **D · Explain out loud** | Recorded prompts; a project deep dive of the current project. Scored against [`../INTERVIEW_CHECKLIST.md`](../INTERVIEW_CHECKLIST.md). | 30–45 min |
| **E · Project review** | Checklist versus the project's scope tier and `milestones.md` acceptance criteria. | 20–30 min |
| Scoring + decision | Per-area scores, PASS thresholds, the pass/fail table. | 10 min |
| Remediation | What to do in the next two weeks if an area failed. | — |
| Results log | Fill it in. Keep it. Compare next time. | 5 min |

Total ≈ 3–4 h, split **Saturday (Parts A + C)** and **Sunday (Parts B, D, E, scoring)** so the
review day stays a review day.

## Rules

1. **Closed book** — except official language/framework docs (Python docs, Java API docs, Spring
   reference, PostgreSQL manual, MDN). [`../PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md)
   is **not** allowed during Part A — the point is to know it. No search engines, no chat assistants, no old solutions, no notes,
   no repo files from this roadmap except the checkpoint file itself. Interviews are closed book;
   the checkpoint must be too.
2. **Timed** — start a visible timer, stop when it rings. Partial work at the bell is scored as
   partial. Do not "just finish this one thing".
3. **Unseen problems only** in Part A. If you recognise a problem, swap it and note the swap.
4. **Honest scoring.** A 2/4 recorded truthfully is worth more than a 4/4 that hides a gap; the
   remediation plan only works on true data. Nobody else reads this.
5. **Record Part D.** Phone voice memo is enough. Listen back once. Score the recording, not the
   memory of it.
6. **Write results before reading the answers** in Part B. Then grade against the `<details>`.
7. **Never skip a checkpoint** because the project is behind. A checkpoint on a behind-schedule
   project is exactly the information the schedule needs.
8. **The project is reviewed at the tier the phase promised**, not the tier it reached. If the
   tier was not reached, that is the finding.

## Scoring model (shared by all checkpoints)

Every area gets a score **0–4**:

| Score | Meaning |
|---:|---|
| 0 | Could not do it / did not attempt |
| 1 | Attempted; mostly wrong, incomplete or far over time |
| 2 | Partly correct or correct but well over time / needed the docs heavily |
| 3 | Correct, in time, minor gaps in explanation or polish |
| 4 | Correct, in time, clean, explained clearly with trade-offs |

Areas evaluated at every checkpoint: **Python (coding) · Java (engineering) · DSA · SQL · Backend ·
Frontend (from CP-8) · Git · Debugging · CS fundamentals · Projects · Interview readiness.**
"Python (coding)" is Track A fluency: idiomatic, correct, fast Python under the clock. "Java
(engineering)" is Track B: OOP, collections, testing, concurrency and the Java rep. DSA is the
algorithmic result regardless of language. Each file says which Parts feed
which area and what the PASS threshold is for that area at that point in the roadmap. Thresholds
rise over time: a 2 in DSA passes CP-4; it fails CP-16.

**Checkpoint decision:**

- **PASS** — every *critical* area (marked in the file) meets its threshold and at most one
  non-critical area is below threshold.
- **PASS WITH REMEDIATION** — all critical areas met; two or more non-critical areas below.
- **FAIL** — any critical area below threshold. Remediation for that area is mandatory.

## Failure rule (ROADMAP §9 and §13)

- A failed checkpoint **does not stop the calendar.** The next week starts on schedule.
- Remediation lives in the **next two weeks' review blocks** (Fri retro hour, Sat interview block,
  Sun review) plus the weakest-area DSA hours. Each checkpoint file lists specific files, problems
  and hours per area.
- **Two consecutive failures in the same area** → freeze *new* features in that area for one week
  and remediate full-time on it (e.g. two backend failures → one week of tests/refactors on the
  current milestone before any new endpoint).
- If the current project phase is **more than one week behind at its checkpoint → drop that
  project's Advanced Version**, never its Strong Résumé Version.
- Weeks 24–26 absorb slippage. They are the only buffer; do not spend it early.

## Summary log

Fill in after each checkpoint. Copy the per-area scores from the file's results log.

| Checkpoint | Date | Python | Java | DSA | SQL | Backend | Frontend | Git | Debug | CS | Projects | Interview | Decision |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| CP-4 | | | | | | | — | | | | | | |
| CP-8 | | | | | | | | | | | | | |
| CP-12 | | | | | | | | | | | | | |
| CP-16 | | | | | | | | | | | | | |
| CP-20 | | | | | | | | | | | | | |
| CP-24 | | | | | | | | | | | | | |

## Related

- Readiness stages that these checkpoints feed: [`../JOB_READINESS.md`](../JOB_READINESS.md)
- Coding-round method used in Part A: [`../16-interview-prep/coding-interview-method.md`](../16-interview-prep/coding-interview-method.md) · Python templates: [`../PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md) · pitfalls: [`../19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md)
- Deep-dive method used in Part D: [`../16-interview-prep/project-deep-dive.md`](../16-interview-prep/project-deep-dive.md)
- Mock scoring rubric: [`../16-interview-prep/mock-interviews.md`](../16-interview-prep/mock-interviews.md)
- OA protocol used from CP-20: [`../OA_PREP.md`](../OA_PREP.md)
- Trackers: [`../trackers/dsa-tracker.md`](../trackers/dsa-tracker.md) · [`../trackers/project-tracker.md`](../trackers/project-tracker.md) · [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md) · [`../trackers/weekly-progress.md`](../trackers/weekly-progress.md)
