# Behavioral Interviews

Behavioral rounds decide more junior offers than people expect. At Amazon-style companies
**every** loop round includes behavioral questions mapped to Leadership Principles; elsewhere
there's usually one dedicated round plus questions woven into technical ones.

Your situation has one extra dimension: **prior professional experience followed by a gap**.
Handled honestly and briefly, that's a strength — you've shipped software before, you know
what a team is, and you've just proven you can rebuild skills on your own. Handled evasively,
it becomes the thing they remember.

**Timeline:** story bank starts **Week 8** (ROADMAP §9). One new story per week until all 12
exist; each rehearsed out loud at least 3 times before Week 23.

---

## 1. The rules

1. **Never fabricate.** Every story is something that really happened, told at the scope it really had. No invented incidents, metrics, team sizes, titles, or responsibilities. If you don't know the exact number, say "roughly" or describe it qualitatively.
2. **"I", not "we".** The interviewer is hiring you. Say what *you* did; credit the team for the rest.
3. **Label the source.** Past-job story → say the company/role. Roadmap project → say "in a personal project I built — LedgerX, a double-entry ledger engine". Never let a personal project sound like paid work.
4. **Short.** 2–3 minutes per answer. Let them pull more with follow-ups.
5. **Specific.** One situation, one moment in time. "I always write tests" is not a story.

---

## 2. Format: STAR and STARL

| Part | Share | Content |
|---|---:|---|
| **S — Situation** | 10–15 % | Where, when, what was at stake. 1–2 sentences. |
| **T — Task** | 10 % | What *you* were responsible for. The problem or goal. |
| **A — Action** | 50–60 % | 3–5 concrete steps *you* took, with the reasoning for each. Technical detail welcome. |
| **R — Result** | 15 % | Outcome — measured honestly (numbers only if true and you can back them up). Include partial or negative results. |
| **L — Learning** | 10 % | What you took away and **how you've applied it since**. |

STARL is STAR plus Learning. Use STARL by default: the "L" shows growth and gives a natural bridge ("…which is why in ForgeCI I wrote the worker-crash test first").

**Example (roadmap project, clearly labelled).** Illustrative only — your version must describe what *actually* happened when you built it (if your first concurrency test passed, say so and tell a different story).

> **S:** In LedgerX, a wallet and double-entry ledger engine I built as a personal project, transfers move money between accounts inside one database transaction.
> **T:** I needed to guarantee a balance could never go negative under concurrent transfers, and I wanted proof, not hope.
> **A:** First I wrote a test: a $500 account, two concurrent $400 transfers started through a `CountDownLatch`. It failed — both succeeded and the balance was −$300. I read the SQL Hibernate generated and saw a read-then-write race under READ COMMITTED. I added `SELECT … FOR UPDATE` on the accounts, locked them in id order to rule out deadlocks, added a `CHECK (balance >= 0)` constraint as a last line of defence, and compared it with an optimistic `@Version` approach to justify the choice.
> **R:** The test now passes consistently — exactly one transfer succeeds, the other gets a clear insufficient-funds response. It runs in CI on every push.
> **L:** I learned to write the concurrency test *before* the fix. I used the same approach in ForgeCI, killing a worker mid-job to prove the lease-expiry recovery works.

---

## 3. "Tell me about yourself" (60–90 seconds)

Structure: **present → past → why here**. Write yours, time it, cut to 75 seconds.

```
Present:  I'm a software engineer focused on Java backend development. Over the past six months
          I've built [ForgeCI or LedgerX one-liner] and [FlowGrid or FlagForge one-liner] — [one concrete technical detail].
Past:     Before that I worked [N years] as [real titles, in order], most recently [true one-liner
          of what you did]. Then I took time away from software development [brief true reason, or
          simply "for personal reasons"], and I've spent this year rebuilding my skills deliberately.
Why here: I'm looking for a [junior / entry-level] role where I can contribute to [team/domain]
          and keep growing with strong code review — which is why [company-specific reason].
```

**Don't** open with the gap. **Don't** list every technology. **Do** name one thing you built and one detail that invites a follow-up.

---

## 4. Handling the career gap honestly

### Principles

| Do | Don't |
|---|---|
| State it plainly, in one or two sentences | Hide it, blur dates, or stretch a previous job to cover it |
| Give the reason at the level of detail *you* choose ("personal reasons", "family responsibilities", "health, now resolved", "I changed direction for a while") — all are acceptable if true | Over-explain private matters, or invent a more "acceptable" reason |
| Pivot within 15 seconds to what you did to come back | Apologise or sound defensive |
| Offer evidence: GitHub, deployed project, the CI pipeline, tests | Claim you "kept fully up to date" if you didn't |
| Acknowledge what changed while you were away and how you caught up | Pretend nothing changed |

You are not obliged to disclose health, caregiving or other personal details. "I stepped away for personal reasons, and that's fully behind me" is complete and honest.

### Script template — fill with your true details

> "I worked in software from **[year]** to **[year]** — as a junior developer, then developer, then software engineer, and most recently on contract projects where I **[one true, specific thing]**. In **[year]** I stepped away from software development **[true reason at your chosen level of detail]**.
>
> When I decided to come back, I was honest with myself that some of my knowledge was out of date, so I followed a structured plan: I rebuilt my Java fundamentals, solved **[actual number]** algorithm problems, and built **[number actually finished]** projects. The largest, ForgeCI, is a CI/CD execution platform: a queue, Docker-executing workers, live logs over SSE, and failure recovery, deployed to AWS through a GitHub Actions pipeline. I'm happy to walk through any of it."

Keep it under 45 seconds. Then stop talking and let them choose the follow-up.

### Follow-ups and honest answers

| Question | Answer shape |
|---|---|
| "What did you do during the gap?" | The true answer, briefly. If part of it wasn't tech, say so: "Mostly [true thing]. The last [N] months have been a full-time return to engineering — here's what I built." |
| "Why should we believe your skills are current?" | Evidence, not adjectives: "The code is on GitHub with CI. I'm happy to walk through LedgerX's concurrent-transfer test or ForgeCI's worker-recovery suite right now." |
| "What changed in the industry while you were away?" | Show you noticed: e.g. Java 17/21 features (records, virtual threads), Spring Boot 3 / Jakarta namespace, containers as the default dev environment, CI/CD expectations, AI-assisted tooling — and what you did about each. Only mention what's actually true for your timeline. |
| "Is anything likely to take you away again?" | You don't have to discuss personal plans. "I'm fully committed to this return — that's why I've spent six months on it." |
| "Your résumé says X years with Spring Boot — can you still do it?" | Answer technically from what you know **now** and point to where you rebuilt it. See [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md). |

---

## 5. "Why are you applying to junior roles?"

This is fair to ask, and interviewers are checking three things: *are you realistic, will you be satisfied, will you leave in six months?*

**Honest answer template:**

> "My earlier experience was real, but after **[time]** away I'd rather be assessed on what I can do today than on my title history. A junior or entry-level role lets me meet and exceed expectations from day one, get strong code review on a modern stack, and grow quickly. I'd rather over-deliver at the right level than overclaim and struggle. I expect to grow into more responsibility, but through performance here — not by moving on."

Variants depending on what's true for you:
- "I want to move from [previous domain/stack] into [this domain], and a junior role is the honest entry point for that."
- "My previous roles were at small companies with little review culture. I want to work somewhere with strong engineering practices, and I'm happy to start at the level that gets me there."

**Related questions:**

| Question | Answer shape |
|---|---|
| "Aren't you overqualified?" | "My experience helps — I know what working on a team, in a real codebase, with deadlines looks like. But I'm applying at the level of my *current* skills, which I've rebuilt recently. I'd expect to be evaluated for promotion like anyone else." |
| "Will you be bored / leave?" | "I'm looking for a place to stay and grow. What I care about is the team, mentorship and the work — [specific thing about this team]." |
| "Why should we pick you over a new grad?" | Don't disparage anyone. "I bring professional habits — PRs, code review, working with stakeholders, owning production issues — plus a recent, deep refresh of fundamentals." |
| "What are your salary expectations?" | See [`recruiter-screen.md`](./recruiter-screen.md). Research the band for the *role level*, not your old title. |

**Eligibility note:** some "new grad" or "university" roles require a recent graduation date. Read the posting; if unclear, ask the recruiter directly. Never alter dates to fit.

---

## 6. Where your stories come from

| Source | Good for | How to label it |
|---|---|---|
| **Past roles (real events)** | Teamwork, conflict, deadlines, customers, production incidents, feedback | "At [company], as a [title]…" |
| **Roadmap projects (FlowGrid, LedgerX, ForgeCI, FlagForge)** | Technical depth, debugging, design trade-offs, testing, learning fast | "In [project], a personal project I built…" |
| **Debugging drills** ([`../21-debugging-code-reading/`](../21-debugging-code-reading/)) | Methodical debugging (use as supporting detail, not your headline "hardest bug") | "While practising on an unfamiliar codebase…" |
| **The return itself** | Ownership, learning, discipline, planning | "When I decided to return to engineering…" |
| **Non-tech parts of your life** | Only if relevant and you're comfortable — responsibility, organisation | Keep brief and professional |

Aim for a mix: at least **5 stories from past roles** (interviewers value real team context) and **at least 4 from the four projects**. If a prompt has no true past-job story, use a project story — don't invent a workplace one.

---

## 7. The story bank — 12 prompts

Write one story per prompt using the template in §9. Several prompts can share a story, but have **at least 8 distinct stories** so you never reuse the same one twice in a loop.

| # | Prompt ("Tell me about a time…") | Likely sources | Primary LPs |
|---:|---|---|---|
| 1 | …you debugged a difficult problem. | Past production issue; LedgerX concurrent-transfer race; ForgeCI orphaned job that ran twice; FlowGrid reservation deadlock fixed by ordered locking | Dive Deep, Ownership |
| 2 | …you failed or missed a deadline. | Past role; a milestone you slipped in the roadmap and how you re-planned | Ownership, Earn Trust, Learn and Be Curious |
| 3 | …you disagreed with a teammate or lead. | Past role (code review, design, estimate) | Have Backbone; Disagree and Commit, Earn Trust |
| 4 | …you learned a new technology quickly. | Docker Engine API + SSE for ForgeCI; TypeScript/React for the FlowGrid dashboard; AWS for the first deploy; a stack switch at a past job | Learn and Be Curious, Deliver Results |
| 5 | …you did something for a user/customer that wasn't asked for. | Past client work; FlagForge SDK stale-if-error so apps keep working when the server is down; clear ProblemDetail error bodies in FlowGrid | Customer Obsession |
| 6 | …you simplified something complex. | FlagForge immutable config versions (rollback = publish old version); LedgerX compensating entries instead of edits; a past-job process | Invent and Simplify |
| 7 | …you made a decision with incomplete information or under time pressure. | Past incident; choosing `BLMOVE` + lease vs Streams for the ForgeCI queue; pessimistic vs optimistic locking in LedgerX | Bias for Action, Are Right A Lot |
| 8 | …you raised the quality bar. | Adding tests/CI at a past job; LedgerX invariant suite + fault-injection tests; ForgeCI chaos tests; code-review standards | Insist on the Highest Standards |
| 9 | …you delivered with limited time or resources. | Contract project; four projects on AWS with a strict cost cap (teardown/cost notes); ForgeCI Redis-on-EC2 instead of managed services | Frugality, Deliver Results |
| 10 | …you received critical feedback. | Past code review or manager feedback; mock-interview feedback you acted on | Earn Trust, Learn and Be Curious |
| 11 | …you helped a teammate or someone learn. | Onboarding someone at a past job; explaining code in reviews | Hire and Develop the Best, Strive to be Earth's Best Employer |
| 12 | …you took ownership of your own growth / a long self-driven effort. | The return to engineering: the plan, the projects, what you cut and why | Ownership, Learn and Be Curious, Deliver Results |

---

## 8. Amazon Leadership Principles mapping

Amazon publishes 16 Leadership Principles (read the current wording on amazon.jobs). In the loop, each interviewer is typically assigned a few LPs and asks 1–2 behavioral questions per round, then probes with follow-ups. Many other companies ask the same kinds of questions without the labels.

| Leadership Principle | Typical question | Story # | What they listen for |
|---|---|---|---|
| Customer Obsession | "Tell me about a time you went above and beyond for a customer." | 5 | Started from the user's need; measured the impact on them |
| Ownership | "Tell me about a time you took on something outside your responsibility." | 1, 2, 12 | No blaming; long-term thinking; followed through |
| Invent and Simplify | "Tell me about a time you found a simpler way to do something." | 6 | Removed complexity, not added cleverness |
| Are Right, A Lot | "Tell me about a decision you made without all the data." | 7 | Sought other views; judgment; admitted when wrong |
| Learn and Be Curious | "Tell me about something you taught yourself recently." | 4, 12 | Self-directed; applied it |
| Hire and Develop the Best | "Tell me about a time you helped someone grow." | 11 | Specific help, their outcome |
| Insist on the Highest Standards | "Tell me about a time you refused to compromise on quality." | 8 | Concrete standard, raised for others too |
| Think Big | "Tell me about a time you proposed a bigger idea." | 6, 9 (ForgeCI design: N workers and DAG scheduling beyond what you run) | Vision proportionate to a junior role; realistic |
| Bias for Action | "Tell me about a time you acted quickly without complete information." | 7 | Calculated risk; reversible decisions |
| Frugality | "Tell me about accomplishing more with less." | 9 | Constraints as a design input (AWS cost cap) |
| Earn Trust | "Tell me about receiving tough feedback." / "admitting a mistake" | 2, 10 | Candour, self-critique, listening |
| Dive Deep | "Tell me about a problem you solved by digging into details." | 1 | Data, logs, root cause, not guesses |
| Have Backbone; Disagree and Commit | "Tell me about disagreeing with your manager." | 3 | Disagreed respectfully with data; committed once decided |
| Deliver Results | "Tell me about a time you delivered under a tight deadline." | 4, 9, 12 | Outcome, obstacles overcome, trade-offs made |
| Strive to be Earth's Best Employer | "Tell me about making your team's environment better." | 11 | Care for teammates' growth and wellbeing |
| Success and Scale Bring Broad Responsibility | "Tell me about considering the wider impact of your work." | 9 (least-privilege IAM, Docker-socket security notes in ForgeCI, cost), 8 | Security, privacy, sustainability, second-order effects |

### Amazon-style follow-ups — prepare answers for each story

- "What exactly did **you** do?" / "What was your role vs the team's?"
- "What data did you use to decide?"
- "What would you do differently?"
- "What was the result — how did you measure it?"
- "What did the other person say? How did you convince them?"
- "What happened next / a month later?"
- "If you had half the time, what would you cut?"

---

## 9. Story-bank template

Copy one block per story into your notes (or a private file). Track status in [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md#behavioral-story-bank).

```markdown
### Story: <short memorable title>
- Prompt(s): #__, #__           LPs: ______________________
- Source: [ ] Past role: <company, title, year>   [ ] Project: FlowGrid / LedgerX / ForgeCI / FlagForge (personal)   [ ] Other: ___
- Evidence I can point to: <PR/commit link, README section, or "none — past employer">
- Verified facts only? [ ] yes (every number is real or marked "roughly")

S — Situation (1–2 sentences):
T — Task (my responsibility):
A — Actions (3–5, each with "because…"):
  1.
  2.
  3.
R — Result (honest; numbers only if true):
L — Learning + where I applied it since:

Follow-ups I expect → my answers:
- What exactly did you do?
- What would you do differently?
- What data did you have?
- <story-specific>:

Length when spoken: __:__  (target 2:00–2:45)
Rehearsals: [ ] 1  [ ] 2  [ ] 3  [ ] recorded  [ ] used in a mock
```

---

## 10. Other questions to prepare

- "Why this company?" — two specific reasons (product, engineering practice, team) + one tie to your experience.
- "Why software engineering?" — your real reason, including why you came back.
- "What's your greatest strength / weakness?" — weakness must be real and include what you're doing about it (e.g., "I used to under-communicate progress on long tasks; now I post short written updates at fixed points").
- "Where do you see yourself in 3 years?" — growing into a strong mid-level engineer on this team; owning a component.
- "Tell me about a project you're proud of." → [`project-deep-dive.md`](./project-deep-dive.md).
- "What questions do you have for us?" — always have 3:
  - "What does a new engineer's first month look like? What did the last junior hire ship first?"
  - "How does code review work on the team?"
  - "What's the biggest technical challenge the team is facing this year?"

---

## 11. Delivery checklist

- [ ] Answer the question asked (a "conflict" question needs a conflict).
- [ ] Headline first: "The hardest bug I've fixed was a race condition in seat booking."
- [ ] "I" for actions; credit others by role.
- [ ] 2–3 minutes; stop and let them probe.
- [ ] Every number true; source labelled.
- [ ] End with result + learning, not trailing off.
- [ ] Record yourself weekly from Week 14; watch for filler words, rambling Situation, vague Action.
