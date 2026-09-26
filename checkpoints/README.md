# 🏁 Checkpoints — How the Gates Work

Every four weeks you stop learning new material for one weekend and **prove** what you
know under test conditions. A checkpoint is not a quiz you can "study for" the night before;
it measures whether the last four weeks actually turned into skill.

The schedule, gate questions and failure rule come from [`ROADMAP.md` §8](../ROADMAP.md#8-checkpoints).
If this file and the roadmap ever disagree, the roadmap wins.

---

## Summary table

| Checkpoint | Taken | Gate question | Part A (timed coding) | Part C (practical) | Headline pass bar |
|---|---|---|---|---|---|
| [CP-4](./checkpoint-04.md) | Sat–Sun, Week 4 | Can I write, test and version small Java programs without a tutorial? | 2 unseen Easy in 40 min | Tested Java class from a spec in 45 min + Git task | 2/2 Easy correct; class passes your own ≥ 8 tests |
| [CP-8](./checkpoint-08.md) | Sat–Sun, Week 8 | Can I solve Easy problems reliably, write real SQL, and have I shipped P1? | 3 unseen Easy in 45 min + 1 Medium attempt (25 min) | 8 SQL queries against the practice schema in 60 min | ≥ 3/3 Easy (2/3 = borderline); ≥ 6/8 queries correct; P1 tagged `v1.0` |
| [CP-12](./checkpoint-12.md) | Sat–Sun, Week 12 | Can I build, secure and test a Spring Boot API against Postgres? | 2 unseen Medium in 60 min | Authenticated endpoint + tests in P2 in 60 min | ≥ 1.5/2 Medium; endpoint works with 401/403 tests green |
| [CP-16](./checkpoint-16.md) | Sat–Sun, Week 16 | Can I connect a typed React client to my API and solve standard Mediums? | 2 unseen Medium in 50 min | Typed React form hitting the P3 API in 75 min | ≥ 1.5/2 Medium; form handles loading/error/400 field errors |
| [CP-20](./checkpoint-20.md) | Sat–Sun, Week 20 | Can I containerize, cache, and reason about production concerns? | OA-style: 2 problems in 90 min | Containerize a service + Redis cache-aside in 90 min | OA score ≥ 70%; image builds, cache hit/evict proven |
| [CP-24](./checkpoint-24.md) | Sat–Sun, Week 24 | Would I pass an internship/junior loop today? | Full mock loop: 2 coding rounds × 45 min | Debug `buggy-library` under time (60 min) | "Hire" or "lean hire" on ≥ 4/5 rounds |

---

## When and how long

- **When:** the Saturday and Sunday of the checkpoint week. Saturday's 5-hour block becomes
  ≈ 2–2.5 h of checkpoint (the practical and the recorded explanations) + the rest of your
  normal project work. Sunday's 2-hour review block, extended to ≈ 2–2.5 h, holds the timed
  coding, knowledge questions and project review.
- **How long:** ≈ 3.5–4.5 hours in total, split into parts **A–E**. Each part is timed
  separately; take a real break between parts.
- **No new learning that weekend.** The week's normal DSA new-problem quota still applies
  on weekdays; the checkpoint replaces the weekend's "new" work, not your spaced reviews.

| Part | What | Typical time |
|---|---|---|
| A | Timed coding — unseen problems, Java, plain editor or LeetCode editor with no AI | 40–90 min |
| B | Knowledge questions — answer in writing *before* opening the `<details>` answer | 30–45 min |
| C | Practical task — build/fix something real under a timer | 45–90 min |
| D | Explain-out-loud prompts — recorded (phone or screen recorder) | 20–30 min |
| E | Project review checklist — against your actual repo | 20–30 min |

---

## Rules (non-negotiable)

1. **Closed book.** Allowed: official language/library docs for **syntax lookups only**
   (e.g. `docs.oracle.com/javase` Javadoc for a method signature, MDN for an API name,
   `postgresql.org/docs` for function syntax). Not allowed: your notes, NeetCode videos,
   solutions, Stack Overflow, blog posts, AI assistants, autocomplete that writes whole
   functions (turn off AI completion in the IDE).
2. **Timed.** Start a visible timer. When time is up, stop typing. Code written after the
   bell does not count — write it down separately as "post-time notes" if you like.
3. **Unseen problems only in Part A.** Each checkpoint gives a pool. Pick problems whose
   tracker status is `Not Started` in [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md).
   If you have already seen one, skip it and take the next. If you run out, use the stated
   selection rule ("unseen problem from pattern X") on LeetCode's problem list filtered by tag
   and difficulty, choosing the first one you have never opened.
4. **Honest scoring.** Score against the rubric, not against how close you "almost" were.
   An answer that needed the `<details>` reveal is wrong. A solution that passes the examples
   but fails on submit is wrong unless you find and fix the bug *within the time limit*.
5. **Record Part D.** You will not notice your own "umm, so basically, like…" without a recording.
   Listen back once at 1.25× and score it using the rubric in each file.
6. **Log the result** in the results-log template at the bottom of each checkpoint and in
   [`trackers/weekly-progress.md`](../trackers/weekly-progress.md). Add every Part A problem to
   the DSA tracker with its true status (`Attempted`, `Solved Independently`, …) so it enters
   the Day 0/3/7/14/30 review cycle.

---

## Scoring model (common to all checkpoints)

Each checkpoint scores **areas** (Java, DSA, SQL, Backend, Frontend, Git, Debugging,
CS fundamentals, Projects, Interview readiness — only those relevant at that point).
Each area has a **pass threshold**. Areas are either **core** (must pass) or **supporting**.

| Result | Rule |
|---|---|
| **PASS** | Every core area passes and at most one supporting area is below threshold. |
| **CONDITIONAL PASS** | Every core area passes, two supporting areas are below threshold. Remediate those two in the next two weeks. |
| **FAIL** | Any core area below threshold, **or** three or more supporting areas below threshold. |

Core areas differ per checkpoint (e.g. at CP-8 the core areas are DSA, SQL, Projects; at
CP-12 they are DSA, Backend, Projects). Each file lists them.

---

## Failure rule (from ROADMAP §8)

- **A failed checkpoint does not stop the calendar.** You start the next week as planned.
- You do the **remediation plan** listed in that checkpoint file **inside the next two weeks'
  revision blocks**: the Friday light day (2 h) and the Sunday review day (2 h), plus the
  DSA-review slots on Mon/Wed. That is ≈ 6–8 hours of remediation capacity across two
  weeks without touching project time or new DSA problems.
- At the end of those two weeks, **re-test only the failed areas** using the "re-test" line in
  each remediation row (a fresh set of unseen problems/questions, same time limits).
- **Two consecutive failures in the same area** (e.g. DSA at CP-8 and again at CP-12) →
  **pause new material in that area for one week** and spend that area's hours on
  remediation. Weeks 25–26 are the buffer that absorbs the slip — see
  [`ROADMAP.md` §12](../ROADMAP.md#12-rules-for-falling-behind).
- Never "fix" a failure by compressing two future weeks into one.

---

## What a checkpoint is not

- It is **not** a reason to stop applying or to restart the roadmap. The job-application
  stages in [`JOB_READINESS.md`](../JOB_READINESS.md) use checkpoint results as *inputs*,
  alongside your tracker data.
- It is **not** a competition with your past self on speed alone. A slower, correct,
  well-explained solution scores better than a fast, unexplained one.
- It is **not** optional because "the week went badly". Take it anyway; a failed checkpoint
  with a clear remediation plan is more useful than a skipped one.

---

## Files

- [Checkpoint 4 — Java foundation](./checkpoint-04.md)
- [Checkpoint 8 — DSA fundamentals, SQL, P1 shipped](./checkpoint-08.md)
- [Checkpoint 12 — Spring Boot API, secured and tested](./checkpoint-12.md)
- [Checkpoint 16 — Typed React client + standard Mediums](./checkpoint-16.md)
- [Checkpoint 20 — Containers, caching, production thinking](./checkpoint-20.md)
- [Checkpoint 24 — Full mock loop](./checkpoint-24.md)
