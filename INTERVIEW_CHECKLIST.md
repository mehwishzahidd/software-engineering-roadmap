# Interview Checklist

Concise, tickable. Print it or keep it open. The reasoning behind each item is in
[`16-interview-prep/`](./16-interview-prep/README.md); OAs have their own checklist in [`OA_PREP.md`](./OA_PREP.md#10-real-oa-day--condensed).

**Track A** = coding round in **Python**. **Track B** = project / résumé / behavioral / design round (Java stack).
Also used weekly from Week 5 to score recorded think-aloud sessions (ROADMAP §10).

---

## Day before

- [ ] Confirm time **and time zone**, format (video/phone/onsite), link, interviewer names, and **which rounds are Track A vs Track B**.
- [ ] Test the setup: camera, mic, internet, screen sharing, the coding platform's editor (**Python 3** selected, version checked).
- [ ] Re-read the job posting and your notes on the company (2 reasons you want to work there).
- [ ] Re-read your résumé; every line defensible ([`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md)).
- [ ] Say your 60–75 s intro out loud once; say your gap sentence out loud once.
- [ ] Review the 30 s / 2 min versions of FlowGrid, LedgerX, ForgeCI and FlagForge ([`16-interview-prep/project-deep-dive.md`](./16-interview-prep/project-deep-dive.md)).
- [ ] Skim story bank: pick 1 story per likely LP / theme ([`16-interview-prep/behavioral.md`](./16-interview-prep/behavioral.md)).
- [ ] Track A: 2 warm-up Mediums in Python you've solved before — **no new hard problems**. Skim [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md).
- [ ] Track B: skim the relevant [`17-resume-tech-defense/`](./17-resume-tech-defense/) files for the technologies in the posting.
- [ ] Prepare 3 questions for the interviewers.
- [ ] Clothes, water, pen + paper, charger ready. Sleep.

## 1 hour before

- [ ] Eat something light; water on the desk.
- [ ] Close everything except: interview link, editor, notes. Notifications off, phone silent and away.
- [ ] Track B: project repo open in IDE with bookmarks; app runnable if a demo is possible; `ARCHITECTURE.md` and `PERFORMANCE.md` in a tab.
- [ ] Track A: one easy warm-up problem in Python, spoken aloud (10 min); a blank `.py` with imports typed (`collections`, `heapq`, `bisect`).
- [ ] Re-read the 9-step method headings ([`16-interview-prep/coding-interview-method.md`](./16-interview-prep/coding-interview-method.md)).
- [ ] Join 3–5 minutes early.

## During the coding round (Track A — Python)

- [ ] Restate the problem.
- [ ] Ask 2–4 clarifying questions (size, values, duplicates, empty, output format).
- [ ] Work an example by hand; write edge cases as a comment.
- [ ] State brute force + complexity.
- [ ] Optimize out loud; state plan + complexity; ask "does that sound good?" **before coding**.
- [ ] Narrate intent while coding; no silence > 30 s.
- [ ] Right containers: `deque` not `pop(0)`, set/dict not `in list`, `"".join` not `+=`, no `[[0]*m]*n`, no mutable default args.
- [ ] Good names, helper functions; iterative for deep recursion.
- [ ] Trace an example line by line; then edge cases.
- [ ] Fix bugs calmly and say what you found.
- [ ] Final time and space complexity, justified — including Python's hidden costs (slicing, `in list`, sort).
- [ ] Hint given → stop, repeat it back, connect it, adjust.
- [ ] Stuck → say so, new example, pattern list, brute force.
- [ ] Minute ~38: switch to your questions.

## During the project / résumé / behavioral round (Track B)

- [ ] Headline first, then STARL; 2–3 minutes per answer.
- [ ] "I" for my actions; label sources (past role vs personal project).
- [ ] Only true facts and numbers; "roughly" when unsure; scope tier stated (MVP / v1.0 / Advanced).
- [ ] Project: start at 30 s version, go deeper when asked; draw the diagram; name the Python tooling when it's relevant (generators, verifiers, SDK).
- [ ] Résumé technology question → answer from what I know **now**, with an example from my code.
- [ ] Unfamiliar-code task → build, run tests, read the failing test, reproduce → isolate → fix → re-run all ([`21-debugging-code-reading/method.md`](./21-debugging-code-reading/method.md)).
- [ ] "I don't know" → reason out loud and say how I'd find out.
- [ ] Gap question → one calm true sentence + pivot to evidence. No apology.
- [ ] "Why junior?" → honest answer, focused on growth and current skill level.
- [ ] Ask my prepared questions; listen to answers.

## After

- [ ] Same day (20 min): write down all questions from memory (own practice only), self-score with the right track's rubric ([`16-interview-prep/mock-interviews.md`](./16-interview-prep/mock-interviews.md)).
- [ ] Log in [`trackers/interview-tracker.md`](./trackers/interview-tracker.md): stage, date, track, notes, next step.
- [ ] Thank-you email within 24 h (short, specific).
- [ ] One weakness → one drill scheduled within 3 days.
- [ ] Update the story bank / project scripts if a question exposed a gap.
- [ ] Then stop thinking about it. Next application.
