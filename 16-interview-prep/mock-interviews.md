# Mock Interviews

Real interviews are a performance skill. You only get good at performing under
observation by practising under observation. Target: **≥ 15 mocks** by the end of Week 26
(ROADMAP §5 requires ≥ 14), each scored with the same rubric and logged.

## Two tracks, never mixed

| Track | Language | What a mock covers | Method file |
|---|---|---|---|
| **A · Coding interview** | **Python** | One or two LeetCode-style problems, hints, follow-up variations, complexity, testing | [`coding-interview-method.md`](./coding-interview-method.md), [`../PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md) |
| **B · Software-engineering / résumé interview** | Java, Spring Boot, SQL, TS/React, Docker, Redis, AWS, CI/CD | Project deep-dives (FlowGrid, LedgerX, ForgeCI, FlagForge), résumé-technology probing, debugging an unfamiliar Java codebase, behavioral, junior system design | [`project-deep-dive.md`](./project-deep-dive.md), [`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md), [`behavioral.md`](./behavioral.md), [`system-design-interview.md`](./system-design-interview.md), [`../21-debugging-code-reading/`](../21-debugging-code-reading/) |

A Track A mock is never "in Java to practise Java". A Track B mock never turns into a LeetCode problem. Full loops (W24+) contain both — as separate rounds.

---

## 1. Formats

| Format | Track | Best for | Setup | Weakness |
|---|---|---|---|---|
| **Peer mock** (friend, ex-colleague, study group, community Discord) | A or B | Realistic pressure, human follow-ups, behavioral | 60 min: 45 interview + 15 feedback. Swap roles next time. Share this rubric beforehand. | Peer may be too kind; give them the rubric and ask for a score |
| **Pramp-style peer platform** (peer-matching services where you alternate interviewer/candidate) | A | Strangers = real nerves; practice being the interviewer too | Book a slot; prepare your interviewer problem properly | Variable partner quality |
| **AI interviewer with strict rules** | A or B | High frequency, any time, consistent rubric | Paste the prompt below into your AI assistant. Voice mode if available. Editor with no AI autocomplete. | Softer than humans on follow-ups unless forced; can leak answers |
| **Self-recorded** | A | Communication habits, filler words, silences, pacing | Screen + webcam recording, timer, problem chosen by a random picker | No follow-ups; easy to cheat — be strict |
| **Unfamiliar-code mock** (from W13) | B | Debugging/codebase rounds | Partner picks a drill from [`../21-debugging-code-reading/drills.md`](../21-debugging-code-reading/drills.md), you share screen | Needs a partner who knows the codebase (or use AI with the drill text) |
| **Résumé grill** | B | Technology probing: "You list Redis — explain…" | Partner/AI picks 5 technologies from your résumé and asks 3 questions each from [`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md) | Needs honest scoring of depth |
| **Full loop** (W24+) | A + B | Stamina and context switching | 3–4 back-to-back rounds, 10-min breaks: coding (Python) · project deep-dive · behavioral · junior design | Takes half a day |

### AI interviewer — strict-rules prompt (Track A)

```text
You are a strict technical interviewer for a junior software engineer role. Rules:
1. Give me ONE LeetCode-style Medium problem (not one I name). Don't reveal its name or pattern.
   I will solve it in Python.
2. Answer clarifying questions briefly and realistically, as an interviewer would.
3. NEVER provide code, the algorithm, or the pattern name. If I'm stuck for 3+ minutes after
   asking, give ONE small hint, then note it for scoring.
4. Interrupt me if I start coding without explaining my approach. Ask "What's the complexity?"
   and "How would you test this?" if I don't cover them.
5. At minute 35, tell me "5 minutes left".
6. After I say "done", ask 1–2 follow-ups (e.g., a variation or a constraint change).
7. Then score me 1–4 on: Problem solving, Coding, Communication, Testing — using the rubric
   I'll paste — with one concrete example from the session per score. Be critical;
   do not round up.
Rubric: <paste section 2>
```

### AI interviewer — Track B variants

Replace rule 1 with one of:
- "Ask me 3 behavioral questions mapped to Amazon Leadership Principles, with 2 follow-ups each, then score STAR structure, specificity, ownership ('I' vs 'we'), and honesty of results."
- "Ask me to walk through my project <FlowGrid | LedgerX | ForgeCI | FlagForge> (Java/Spring Boot, PostgreSQL, Redis, AWS), drilling down until I reach the edge of my knowledge. Then score me on the four Track B dimensions."
- "You are reviewing my résumé, which lists <technologies>. Ask increasingly deep questions about <technology> until I can't answer; note where that happened."
- "Give me a junior-level system design prompt (e.g., a rate limiter, a URL shortener) and drive a 35-minute design discussion; score the four design dimensions."

**Integrity rule:** never use an AI assistant (or any help) during a real interview or OA unless the company explicitly allows it. Mocks are practice for doing it yourself.

---

## 2. Scoring rubric (1–4)

### Track A — coding (Python)

A "pass" at junior level is roughly **average ≥ 3.0 with no 1s**.

| Dimension | 1 — Strong no | 2 — Lean no | 3 — Lean yes | 4 — Strong yes |
|---|---|---|---|---|
| **Problem solving** | No working approach, or brute force not identified | Reached a working approach only with major hints; complexity unclear | Found an optimal or near-optimal approach with ≤ 1 small hint; justified it | Optimal independently; compared alternatives and trade-offs; handled follow-up variation |
| **Coding** | Doesn't run in principle; major logic gaps | Works for the happy path; messy; notable bugs left | Correct, readable, idiomatic Python (right containers, no O(n) `in list`, no aliased rows); minor issues self-corrected | Clean, well-structured (helpers, names), no bugs, fluent with stdlib (`Counter`, `deque`, `heapq`, `bisect`) |
| **Communication** | Long silences; interviewer had to extract everything | Explained some steps; plan not stated before coding | Clarified, stated plan + complexity before coding, narrated intent; took hints well | Collaborative and structured throughout; checked in at key points; concise |
| **Testing** | No testing | Ran the given example only, loosely | Traced example + some edge cases; found/fixed own bug | Systematic: examples, edge cases, boundaries; precise complexity analysis incl. Python's hidden costs |

### Track B — project / résumé / behavioral

| Dimension | 1 | 2 | 3 | 4 |
|---|---|---|---|---|
| **Structure** (STARL; 30 s → 2 min → 10 min layers) | Rambling, no headline | Some structure, overlong Situation | Headline first, clear layers, 2–3 min answers | Crisp, layered, lets the interviewer steer |
| **Specificity & ownership** | Generic, "we" everywhere | Some concrete detail | Concrete actions in first person, real numbers with method | Precise mechanisms (SQL, locks, Redis ops) and measured results |
| **Honesty & precision** | Overclaims, invented numbers | Vague about scope | Labels personal projects and gap plainly; "I don't know" + reasoning | Scope, tier and limits stated unprompted; every number sourced |
| **Depth under follow-ups** | Breaks at the first "why" | Two levels deep | Reaches implementation detail and one trade-off | Reaches the edge, names it, reasons past it |

**System-design mocks:** *Requirements & scoping* · *High-level design* · *Data model & APIs* · *Trade-offs & scaling* (see [`system-design-interview.md`](./system-design-interview.md)).

---

## 3. Schedule (ROADMAP §10)

| Week | Mock | Track | Format | Focus |
|---:|---|---|---|---|
| 5–13 | Think-aloud (weekly, not scored as mocks) | A | Self-recorded | One Medium in Python, method from [`coding-interview-method.md`](./coding-interview-method.md), scored against [`../INTERVIEW_CHECKLIST.md`](../INTERVIEW_CHECKLIST.md) |
| 7–13 | Résumé-defense drill (weekly, 3 questions) | B | Self-recorded | Java, Git, SQL first; then Spring, Postgres, Docker |
| 8 | Deep-dive rehearsal | B | Self-recorded | FlowGrid 30 s + 2 min |
| 10 | **Mock #1** | A | Peer or self-recorded | Coding (Easy–Medium, Python) + 60 s intro |
| 13 | Deep-dive rehearsal | B | Self-recorded | LedgerX all layers |
| 14 | #2 | A | AI strict | Coding (Medium) |
| 15 | #3 | B | Peer or AI | Unfamiliar-code drill (15 min) + FlowGrid deep-dive + 2 behavioral |
| 16 | #4 | A | Pramp-style | Coding (Medium) |
| 17 | #5 | B | Peer or AI | Résumé grill (5 technologies) + LedgerX deep-dive |
| 18 | #6 | A | Peer | Coding (Medium, 2 problems in 45 min) |
| 19 | #7 | B | Peer or AI | ForgeCI deep-dive (20 min) + behavioral (3 LPs) |
| 20 | #8 | A | AI strict | Coding (Medium, weakest pattern) |
| 21 | #9 | B | Peer or AI | Behavioral (3 LP questions) + unfamiliar-code drill |
| 22 | #10 | B | Peer or AI | **System design mock #1** (junior level) |
| 23 | #11 | A | Pramp-style | Coding (Medium–Hard) · FlagForge deep-dive rehearsal (self) |
| 24 | #12, #13 | A + B | Peer / mixed | **Full loops** (coding · project · behavioral · basic design) |
| 25 | #14, #15 | A, B | Pramp-style + AI | Coding on weakest pattern; recruiter screen + all-tech résumé grill |
| 26 | #16, #17 | A + B | Peer | Final full loop; coding |
| 27+ | 1/week | alternate A / B | Rotate | Per [`maintenance-plan.md`](./maintenance-plan.md) |

Weeks 17–24 also contain OA simulations (W17, 18, 19, 20, 22, 23, 24 ×2) — see [`../OA_PREP.md`](../OA_PREP.md).

---

## 4. Running a mock

**Before (5 min):** pick the track and format, have the right rubric open for the interviewer, timer ready, recording on, editor with AI autocomplete off (Track A: Python file; Track B: project repo open with bookmarks).

**During (45 min):** Track A — follow [`coding-interview-method.md`](./coding-interview-method.md); the interviewer notes timestamps: plan agreed, code done, bug found, hint given. Track B — start at the 30-second layer and let the interviewer pull; the interviewer notes where depth ran out.

**After (15 min):**
1. Interviewer scores first, with one example per score.
2. You self-score, then compare. A gap of ≥ 2 on any dimension = blind spot.
3. Pick **one** action for the next week (not five).
4. Log it (below and in [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md#mock-interviews)).
5. Within 48 h: Track A — re-solve the problem cleanly, untimed; add it to the DSA review queue. Track B — rewrite the answer that ran out of depth; add the question to the project's `interview-questions.md` notes.

**Watching your recording (20 min, same day):** count silences > 30 s, filler words per minute, time to agreed plan, time to first line of code, whether complexity was stated before coding (A); whether you led with the headline and labelled the project as personal (B).

---

## 5. Feedback log

Keep the master copy in the tracker. Template per mock:

```markdown
### Mock #__ — Week __ — <date> — Track A / B
- Format: peer / Pramp-style / AI strict / self-recorded / full loop
- Problem(s) (A): <LeetCode # + name>   |   Prompts (B): project / résumé tech / behavioral / design
- Timeline (A): plan agreed @ __ min · code done @ __ · hints: __
- Depth ran out at (B): <question>
- Scores (interviewer / self): _/_ · _/_ · _/_ · _/_   Avg: __
- What went well (1–2):
- What hurt most (1–2, specific: "went silent 90 s choosing between heap and sort"):
- Action for next week (ONE):
- Re-solved / rewritten within 48 h? [ ]
```

### Common feedback → targeted fix

| Feedback | Track | Fix drill |
|---|---|---|
| "Started coding too early" | A | Force a spoken plan + complexity + "does that sound good?" before typing |
| "Went quiet" | A | Self-recorded sessions; narrate intent every 30 s |
| "Didn't test" | A | Write edge cases as a comment in step 3; trace before saying done |
| "Slow to find the pattern" | A | Pattern-recognition drill: read 10 problem statements, name pattern + complexity only, 2 min each |
| "Un-Pythonic / slow code" (`in list`, `pop(0)`, string `+=`) | A | Re-type the cheatsheet idioms from memory; [`../19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md) |
| "Didn't handle hint well" | A | Practise the 5-step hint response in [`coding-interview-method.md`](./coding-interview-method.md) |
| "Rambling behavioral answers" | B | Rewrite the story's Situation to 2 sentences; time to 2:30 |
| "Couldn't go deeper than the README" | B | Re-open the code: trace one request end-to-end and draw it; redo the project's `interview-questions.md` out loud |
| "Vague on a résumé technology" | B | One [`../17-resume-tech-defense/`](../17-resume-tech-defense/) file end-to-end, then re-test in 3 days |
| "Sounded like the project was a job" | B | Fix the framing sentence in every layer ("a personal project I built…") |
