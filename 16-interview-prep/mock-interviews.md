# Mock Interviews

Real interviews are a performance skill. You only get good at performing under
observation by practising under observation. Target: **≥ 15 mocks** by the end of Week 26
(ROADMAP requires ≥ 10), each scored with the same rubric and logged.

---

## 1. Formats

| Format | Best for | Setup | Weakness |
|---|---|---|---|
| **Peer mock** (friend, ex-colleague, study group, community Discord) | Realistic pressure, human follow-ups, behavioral | 60 min: 45 interview + 15 feedback. Swap roles next time. Share this rubric beforehand. | Peer may be too kind; give them the rubric and ask for a score |
| **Pramp-style peer platform** (peer-matching services where you alternate interviewer/candidate) | Strangers = real nerves; practice being the interviewer too | Book a slot; prepare your interviewer problem properly | Variable partner quality |
| **AI interviewer with strict rules** | High frequency, any time, consistent rubric | Paste the prompt below into your AI assistant. Voice mode if available. Shared editor or IDE with no autocomplete AI. | Softer than humans on follow-ups unless forced; can leak answers |
| **Self-recorded** | Communication habits, filler words, silences, pacing | Screen + webcam recording, timer, problem chosen by a random picker | No follow-ups; easy to cheat — be strict |
| **Unfamiliar-code mock** (from W18) | Debugging/codebase rounds | Partner picks a drill from [`../21-debugging-code-reading/drills.md`](../21-debugging-code-reading/drills.md), you share screen | Needs a partner who knows the codebase (or use AI with the drill text) |
| **Full loop** (W24+) | Stamina and context switching | 3–4 back-to-back rounds, 10-min breaks: coding · project deep-dive · behavioral · junior design | Takes half a day |

### AI interviewer — strict-rules prompt

```text
You are a strict technical interviewer for a junior software engineer role. Rules:
1. Give me ONE LeetCode-style Medium problem (not one I name). Don't reveal its name or pattern.
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

Variants: replace rule 1 with "Ask me 3 behavioral questions mapped to Amazon Leadership Principles, with 2 follow-ups each, then score STAR structure, specificity, ownership ('I' vs 'we'), and honesty of results" — or "Ask me to walk through my project <name>, drilling down until I reach the edge of my knowledge".

**Integrity rule:** never use an AI assistant (or any help) during a real interview or OA unless the company explicitly allows it. Mocks are practice for doing it yourself.

---

## 2. Scoring rubric (1–4)

Use for every coding mock. A "pass" at junior level is roughly **average ≥ 3.0 with no 1s**.

| Dimension | 1 — Strong no | 2 — Lean no | 3 — Lean yes | 4 — Strong yes |
|---|---|---|---|---|
| **Problem solving** | No working approach, or brute force not identified | Reached a working approach only with major hints; complexity unclear | Found an optimal or near-optimal approach with ≤ 1 small hint; justified it | Optimal independently; compared alternatives and trade-offs; handled follow-up variation |
| **Coding** | Doesn't compile/run in principle; major logic gaps | Works for the happy path; messy; notable bugs left | Correct, readable, idiomatic Java; minor issues self-corrected | Clean, well-structured (helpers, names), no bugs, fluent with APIs |
| **Communication** | Long silences; interviewer had to extract everything | Explained some steps; plan not stated before coding | Clarified, stated plan + complexity before coding, narrated intent; took hints well | Collaborative and structured throughout; checked in at key points; concise |
| **Testing** | No testing | Ran the given example only, loosely | Traced example + some edge cases; found/fixed own bug | Systematic: examples, edge cases, boundaries; precise complexity analysis |

**Behavioral / project mocks** use the same 1–4 scale on: *Structure (STARL)* · *Specificity & ownership* · *Honesty & precision* · *Depth under follow-ups*.
**System-design mocks:** *Requirements & scoping* · *High-level design* · *Data model & APIs* · *Trade-offs & scaling* (see [`system-design-interview.md`](./system-design-interview.md)).

---

## 3. Schedule (ROADMAP §9)

| Week | Mock | Format | Focus |
|---:|---|---|---|
| 9–16 | Think-aloud (weekly, not scored as mocks) | Self-recorded | One Medium, method from [`coding-interview-method.md`](./coding-interview-method.md), reviewed against [`../INTERVIEW_CHECKLIST.md`](../INTERVIEW_CHECKLIST.md) |
| 13 | **Mock #1** | Peer or self-recorded | Coding (Easy–Medium) + 60 s intro |
| 17 | #2 | AI strict | Coding (Medium) |
| 18 | #3 | Peer | Coding + 15 min unfamiliar-code drill |
| 19 | #4 | Pramp-style | Coding (Medium) |
| 20 | #5 | Peer or AI | Behavioral (3 LP questions) + P2 deep-dive |
| 21 | #6 | Peer | Coding (Medium, 2 problems in 45 min) |
| 22 | #7 | Peer or AI | **System design mock #1** (junior level) |
| 23 | #8, #9 | AI strict + peer | Coding; project deep-dive (P4, 20 min) + behavioral |
| 24 | #10, #11 | Peer / mixed | **Full loops** (coding + project + behavioral + basic design) |
| 25 | #12, #13 | Pramp-style + AI | Coding on weakest pattern; recruiter screen + behavioral |
| 26 | #14, #15 | Peer | Final full loop; coding |
| 27+ | 1/week | Rotate | Per [`maintenance-plan.md`](./maintenance-plan.md) |

Weeks 23–26 also contain 2 OA simulations/week — see [`../OA_PREP.md`](../OA_PREP.md).

---

## 4. Running a mock

**Before (5 min):** pick the format, have the rubric open for the interviewer, timer ready, recording on, IDE/editor with autocomplete-AI off.

**During (45 min):** follow [`coding-interview-method.md`](./coding-interview-method.md). The interviewer notes timestamps: plan agreed, code done, bug found, hint given.

**After (15 min):**
1. Interviewer scores first, with one example per score.
2. You self-score, then compare. A gap of ≥ 2 on any dimension = blind spot.
3. Pick **one** action for the next week (not five).
4. Log it (below and in [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md#mock-interviews)).
5. Within 48 h: re-solve the problem cleanly, untimed; add it to the DSA review queue.

**Watching your recording (20 min, same day):** count silences > 30 s, filler words per minute, time to agreed plan, time to first line of code, whether complexity was stated before coding.

---

## 5. Feedback log

Keep the master copy in the tracker. Template per mock:

```markdown
### Mock #__ — Week __ — <date>
- Format: peer / Pramp-style / AI strict / self-recorded / full loop
- Problem(s): <LeetCode # + name> | behavioral prompts | project
- Timeline: plan agreed @ __ min · code done @ __ · hints: __
- Scores (interviewer / self): PS _/_ · Code _/_ · Comm _/_ · Test _/_   Avg: __
- What went well (1–2):
- What hurt most (1–2, specific: "went silent 90 s choosing between heap and sort"):
- Action for next week (ONE):
- Re-solved cleanly within 48 h? [ ]
```

### Common feedback → targeted fix

| Feedback | Fix drill |
|---|---|
| "Started coding too early" | Force a spoken plan + complexity + "does that sound good?" before typing |
| "Went quiet" | Self-recorded sessions; narrate intent every 30 s |
| "Didn't test" | Write edge cases as a comment in step 3; trace before saying done |
| "Slow to find the pattern" | Pattern-recognition drill: read 10 problem statements, name pattern + complexity only, 2 min each |
| "Buggy Java" | Re-implement toolkit idioms from memory ([`../03-dsa/java-dsa-toolkit.md`](../03-dsa/java-dsa-toolkit.md)) |
| "Rambling behavioral answers" | Rewrite the story's Situation to 2 sentences; time to 2:30 |
| "Didn't handle hint well" | Practise the 5-step hint response in [`coding-interview-method.md`](./coding-interview-method.md) |
