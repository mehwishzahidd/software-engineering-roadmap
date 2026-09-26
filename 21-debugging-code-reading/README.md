# 21 — Debugging and Reading Unfamiliar Code

> Your first ticket at a new job will not be "build a to-do app from scratch".
> It will be "this test fails on `main`, figure out why" in a codebase you've never seen.

This is a **Track B** skill (software-engineering interview, Java stack — ROADMAP §10). It also
shows up inside OAs, where the algorithm problems are Track A (Python) but the
"existing codebase / failing tests" slot is this Java project.

## Why this matters

| Where it shows up | What they're checking |
|---|---|
| **Online assessments** | Several OA formats (for example some Amazon SDE OAs and CodeSignal/HackerRank "project"-style tasks) include debugging a provided function, fixing failing tests, or making a change inside existing code. Exact formats change — always read the invite. |
| **Technical interviews** | "Here's a class with a bug, walk me through finding it." "What does this code do?" "How would you add X to this?" |
| **Project deep-dives** | "Tell me about a hard bug you fixed." You want real answers from FlowGrid, LedgerX, ForgeCI and FlagForge **and** from these drills. |
| **The job itself** | Most engineering time is spent reading and modifying existing code, not writing new files. |
| **Your résumé defense** | Claiming Java, Maven and JUnit means you can build a stranger's Maven project, read a Surefire report, and use a debugger without hesitation. |

This folder turns those into a trainable skill:

| File | Purpose |
|---|---|
| [`method.md`](./method.md) | The step-by-step method for reading an unfamiliar codebase and for debugging. Read first (Week 13, alongside LedgerX M5). |
| [`drills.md`](./drills.md) | 17 timed drills (find-the-bug, make-tests-pass, add-a-feature, refactor, test-writing) with hints and solutions, plus a feature-request backlog. |
| [`exercises/buggy-library/`](./exercises/buggy-library/) | A real Maven + JUnit 5 project (Java 21) — a library lending service with planted bugs and failing tests. |

## Quick start

```bash
cd 21-debugging-code-reading/exercises/buggy-library
mvn -q test          # expect: Tests run: 40, Failures: 11, Errors: 1
```

Twelve red tests, eight root causes. **Don't fix them in the repository copy** — copy the folder first (see the top of [`drills.md`](./drills.md)) so the drills stay reusable for OA simulations.

## How the drills are used in Weeks 13–26

| Week | Session | Drills | Time |
|---:|---|---|---|
| 13 | Read [`method.md`](./method.md). **Unfamiliar-code drill #1** (LedgerX M5 week) | D01, D02, D03 | 2 × 45 min |
| 14 | Standalone drill (Sat interview block) | D04 | 15 min |
| 15 | Inside mock #3's unfamiliar-code slot | D08 | 15 min |
| 16 | Standalone drill | D10 | 15 min |
| 17 | OA simulation #1 debugging slots ([`../OA_PREP.md`](../OA_PREP.md) OA-1) | D04 (speed rep), D05, D17 item 2 | inside OA |
| 18 | OA sim #2 | D06, D07, F8 (simplified) | inside OA |
| 19 | OA sim #3 | D08, D10 (speed reps), D17 | inside OA |
| 20 | OA sim #4 | D09, D13 | inside OA |
| 21 | Inside mock #9's unfamiliar-code slot | D15 | 25 min |
| 22 | OA sim #5 | D07, D06 (speed reps), D12 | inside OA |
| 23 | OA sim #6 | D11 timed @ 45 min, F7 | inside OA |
| 24 | OA sims #7–8, unfamiliar-code debugging under time | D11 cold (OA-7), D14; D16, F3 (OA-8) | inside OAs |
| 25 | Remediation: redo any drill that took > 1.5× its timebox or needed Hint 2; repository-modification drill | as flagged; one of F1–F8 | 1–2 h |
| 26 | One feature from the backlog end-to-end, explained as if in an interview | F1 or F3 | 90 min |

Later drills assume earlier bugs are fixed — each drill lists its prerequisite. For a cold OA, start from a **fresh** copy and fix what the task needs.

## What "done" looks like by Week 24

- [ ] D11 (all 40 tests green from cold) in **≤ 45 minutes** with no hints.
- [ ] You can explain each of the 8 root causes in ≤ 60 seconds: symptom → cause → fix → prevention.
- [ ] You used the IDE debugger (breakpoint, step into, watch, evaluate expression) on at least 3 drills.
- [ ] You've implemented at least two backlog features (F1–F8) test-first.
- [ ] You can name, from memory, the Java pitfalls planted here: off-by-one boundaries, integer division, `equals`/`hashCode` contract, asymmetric normalization, leaking mutable internals, nullable dereference, modifying a collection while iterating, comparator direction.
- [ ] You've applied the reading method to at least one repo you didn't write besides this one (an open-source library you depend on in ForgeCI or FlagForge, for example).

## Connections

- Java debugging tools: [`../01-java/09-debugging-java.md`](../01-java/09-debugging-java.md)
- Collections pitfalls (CME, `equals`/`hashCode`): [`../01-java/03-collections-generics.md`](../01-java/03-collections-generics.md)
- JUnit 5: [`../09-testing/junit5.md`](../09-testing/junit5.md)
- Git archaeology (`log`, `blame`, `bisect`): [`../02-git/workflows.md`](../02-git/workflows.md)
- OA timing and simulation sets: [`../OA_PREP.md`](../OA_PREP.md)
- Behavioral "hardest bug" story: [`../16-interview-prep/behavioral.md`](../16-interview-prep/behavioral.md)
- The same Python pitfalls for Track A: [`../19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md)
