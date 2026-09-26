# Job Readiness — when to apply, for what, and how to know

> Objective criteria for each application stage in [`ROADMAP.md` §11](./ROADMAP.md#11-job-application-timeline).
> The rule is simple: **apply when the evidence exists, not when the calendar says so — and do
> not wait for Week 26.** Every criterion below is measurable from the checkpoints in
> [`checkpoints/`](./checkpoints/README.md) and the trackers in [`trackers/`](./trackers/).

## Contents

1. [Why the stages are ordered this way](#1-why-the-stages-are-ordered-this-way)
2. [Language priorities and the two interview tracks](#2-language-priorities-and-the-two-interview-tracks)
3. [The six stages](#3-the-six-stages)
4. [Self-assessment scorecard](#4-self-assessment-scorecard)
5. [Application strategy](#5-application-strategy)
6. [Presenting prior experience and the gap honestly](#6-presenting-prior-experience-and-the-gap-honestly)
7. [Handling OAs and interviews that arrive early](#7-handling-oas-and-interviews-that-arrive-early)
8. [Pipeline tracking](#8-pipeline-tracking)
9. [Warning signs](#9-warning-signs)

---

## 1. Why the stages are ordered this way

| Stage | Earliest typical week | Unlocked by | Why this order |
|---|---:|---|---|
| **Too early** | 1–7 | — | Nothing deployed. A résumé that lists Java/Spring/AWS with no current proof is exactly the résumé that gets grilled and fails. Applying now burns companies you will want later (many block re-applications for 6–12 months). |
| **Early application stage** | ~8 | [CP-8](./checkpoints/checkpoint-08.md) PASS: FlowGrid v1.0 deployed + explainable | One real, deployed, tested full-stack project is the minimum evidence that skills are current. Long-timeline internships and referral-driven roles take weeks to move, so starting the clock now means interviews land around Week 12–16, when you are stronger. |
| **Internship-ready** | ~12–13 | [CP-12](./checkpoints/checkpoint-12.md) PASS + LedgerX v1.0 | Two projects and a transactions/concurrency story you can *prove* (the $500/$400/$400 test). Internship loops are typically 1–2 coding rounds (Easies + first Mediums) and a project talk — which is what you can pass now. |
| **OA-ready** | ~16–17 | [CP-16](./checkpoints/checkpoint-16.md) PASS + OA sim #1 passed | Most junior/new-grad pipelines start with an automated OA (2–3 problems in 70–120 min). Applying before Mediums are reliably ≤ 30 min in Python means failing OAs that you cannot retake for a year. |
| **Junior-role-ready** | ~19–20 | [CP-20](./checkpoints/checkpoint-20.md) PASS: ForgeCI v1.0, weekly mocks ≥ 3/4 | Junior SWE / SDE I loops add system design (junior level) and deeper project probing. ForgeCI is the project that survives "tell me about the hardest thing you built". |
| **Interview-ready** | ~24 | [CP-24](./checkpoints/checkpoint-24.md) PASS: full loop, 15-min deep dive without notes | Full onsite: 2 coding rounds, deep dive, design, behavioral. Applications go out at volume now because the conversion rate is finally worth the companies. |

The ordering follows the evidence: **deployed project → proven correctness → timed coding →
hardest project + design → full loop.** Each stage adds one more thing an interviewer can
verify.

---

## 2. Language priorities and the two interview tracks

Interviews come in two shapes and this roadmap trains them separately (ROADMAP §10). Never mix
them in preparation and never let one's weakness hide behind the other's strength.

| Track | Language | Interview type | Readiness evidence |
|---|---|---|---|
| **A · Coding interview** | **Python** | LeetCode-style rounds, OAs (HackerRank, CodeSignal, Amazon-style), timed problems, explaining solutions | Solve rates and times in [`trackers/dsa-tracker.md`](./trackers/dsa-tracker.md); OA sim scores ([`OA_PREP.md`](./OA_PREP.md)); think-aloud scores ([`INTERVIEW_CHECKLIST.md`](./INTERVIEW_CHECKLIST.md)) |
| **B · Software-engineering / résumé interview** | **Java**, Spring Boot, SQL, TS/React, Docker, Redis, AWS, CI/CD | Project deep dives, architecture, concurrency, testing, debugging, technology trade-offs, résumé probing, junior system design | Deep-dive rehearsal scores; résumé-defense drill scores ([`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md)); mock scores ([`trackers/interview-tracker.md`](./trackers/interview-tracker.md)); checkpoint Backend/Projects scores |

**Language priorities** (from the roadmap's strategy — this is what to be strong in, in order):

| Language / tech | Priority | Where it must be strong | Where it is allowed to be thin |
|---|---|---|---|
| **Python** | VERY HIGH | Every coding round and OA: idiomatic, fast, pitfalls known cold; project tooling (`tools/`) | Frameworks (Django/Flask) — not needed |
| **Java** | VERY HIGH | Every project, every backend/résumé question, OOP, concurrency, testing, one Java rep/week | Java-only DSA speed — Python carries that |
| **SQL** | VERY HIGH | Schema design, joins/windows/CTEs, transactions/locking, `EXPLAIN`; asked in almost every backend loop | Vendor-specific extras beyond Postgres/MySQL diff |
| **TypeScript / JavaScript / React** | MEDIUM-HIGH | Explain the dashboards you built, hooks, async, typing the API client | Advanced patterns, CSS frameworks |
| **C++** | LOW | Recognise syntax, memory model basics (W21, ≈4 h) | Everything else |
| **HTML/CSS** | VERY LOW | Enough to build the dashboards | Layout mastery |

If a job posting says "Java for the coding interview", tell the recruiter you will code in
Python unless they require Java; if they require Java, run the Java-rep drill daily for the
week before (see [`03-dsa/java-dsa-toolkit.md`](./03-dsa/java-dsa-toolkit.md)).

---

## 3. The six stages

Each stage lists **objective criteria** (tick every box before declaring the stage), **what to
apply to**, **weekly application volume**, and **what to do in parallel**. Scores are the 0–4
checkpoint scale; "solve rate" means `Solved Independently` on first attempt for `Not Started`
problems of that difficulty, measured over the last two weeks in the DSA tracker.

### Stage 0 — Too early (Weeks 1–7)

**Do not apply.** Exceptions: (a) a referral from someone who knows your prior work and will
vouch for it *now*; (b) an application deadline for a programme that starts > 4 months out
(some internship/new-grad cycles) — apply with the honest résumé, expect the OA to arrive
early, and follow [§7](#7-handling-oas-and-interviews-that-arrive-early).

**In parallel:** build the honest résumé draft (real roles, no projects section yet), set up
LinkedIn/GitHub per [§5.4](#54-linkedin--github-checklist), start the "about me" (ROADMAP §10),
collect target companies in [`trackers/interview-tracker.md`](./trackers/interview-tracker.md).

### Stage 1 — Early application stage (~Week 8)

**Criteria (all required)**

Track A (coding)
- [ ] Patterns understood through Recursion (ROADMAP §7 order 0–9), each with the template written from memory once
- [ ] Easy solve rate ≥ 80%, median time ≤ 15 min, in Python
- [ ] At least 5 Mediums attempted; ≥ 2 solved independently (any time)
- [ ] CP-8 Part A ≥ 3; Python (coding) area ≥ 3

Track B (engineering)
- [ ] FlowGrid **v1.0** tagged, deployed on AWS, CI/CD green — [CP-8](./checkpoints/checkpoint-08.md) Projects ≥ 3
- [ ] FlowGrid deep-dive rehearsal recorded ≥ 3/4 (W8)
- [ ] Backend competence: can add a validated, authorized endpoint with slice tests in ≤ 60 min (CP-4 Part C ≥ 3), explain transactions/locking/idempotency in FlowGrid terms
- [ ] SQL comfort: joins, aggregation, window functions, one `EXPLAIN` read (CP-8 SQL ≥ 3)
- [ ] Git comfort: branches, PRs, rebase, resolving conflicts, ≥ 15 merged PRs across repos
- [ ] Résumé-defense drills started (Java, SQL, Git) with honest self-scores
- [ ] Résumé updated with a Projects section containing FlowGrid (dated Weeks 4–8, i.e. the actual months)

**Apply to:** long-timeline internships (programmes starting in 3+ months), referral-driven
junior roles, "returnship"/re-entry programmes, small companies where the hiring manager reads
the GitHub. Not: high-volume OA-first pipelines (Amazon-style) — those come at Stage 3.

**Volume:** 2–4 targeted applications/week, each with a referral attempt or a tailored note.
Quality over count; every application logged.

**In parallel:** LedgerX M1–M2, résumé-defense drills weekly, story bank (W8), mock #1 in W10.

### Stage 2 — Internship-ready (~Weeks 12–13)

**Criteria (all required)**

Track A
- [ ] Patterns through Backtracking (0–14) understood; Trees/BST/Heap templates from memory
- [ ] Easy solve rate ≥ 90%, median ≤ 12 min; Medium solve rate ≥ 40%, median ≤ 40 min
- [ ] Weekly think-aloud Medium recorded and scored ≥ 3 twice in a row
- [ ] CP-12 Part A ≥ 3

Track B
- [ ] LedgerX **v1.0** tagged and deployed (W13); [CP-12](./checkpoints/checkpoint-12.md) PASS
- [ ] Can prove transactional correctness: $500/$400/$400 test, isolation experiments written up, idempotency-with-crash test — and explain them without notes
- [ ] Two project deep-dive rehearsals ≥ 3 (FlowGrid W8, LedgerX W13)
- [ ] Mock #1 (W10) done and scored; feedback actioned
- [ ] Unfamiliar-code drill #1 done (W13)
- [ ] Résumé: Projects section has FlowGrid + LedgerX with measured numbers only
- [ ] Python engineering evidence: FlowGrid load harness + LedgerX verifier in `tools/`, tested and documented

**Apply to:** SWE internships (any start), junior SWE at small/mid companies, associate SE
programmes, roles explicitly open to career re-entry. Start a few OA-first pipelines *only* if
the OA can be scheduled ≥ 4 weeks out.

**Volume:** 5–8/week. Half through referrals/warm intros, half direct.

**In parallel:** ForgeCI M1–M2 (the heavy weeks — protect project hours), weekly mocks from W14,
résumé defense for Spring Boot/PostgreSQL/JUnit/Docker.

### Stage 3 — OA-ready (~Weeks 16–17)

**Criteria (all required)**

Track A
- [ ] All patterns through Intervals (0–19); graph and DP templates from memory
- [ ] Medium solve rate ≥ 60%, median ≤ 30 min (this is the gate); Easy ≥ 95%
- [ ] OA simulation #1 (W17) passed at the [`OA_PREP.md`](./OA_PREP.md) pass line; CP-16 Part A ≥ 3
- [ ] Stdin/stdout OA template in Python automatic; edge-case checklist habitual
- [ ] Java rep weekly without fail (tracker column filled)

Track B
- [ ] ForgeCI M3 working end to end (queue → worker → container → live logs); [CP-16](./checkpoints/checkpoint-16.md) PASS
- [ ] Debugging: buggy-library drills done twice; can explain a root cause in one sentence
- [ ] Weekly mocks (W14–16) averaging ≥ 2.5, trending up
- [ ] Résumé-defense: Java, Spring, SQL, Postgres, Git, GitHub, Maven, JUnit, Docker, Redis all at "can defend" in [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md)'s matrix

**Apply to:** everything in Stage 2 plus OA-first pipelines (large tech, fintech, e-commerce
new-grad and junior SDE roles), SDE I where eligible, new-grad-style roles where eligibility
allows (some require graduation within N years — read the posting; do not misrepresent).

**Volume:** 8–12/week. Expect OAs within days; schedule them for the latest allowed date and
run an OA sim two days before each.

**In parallel:** ForgeCI M4–M5 (v1.0 in W18), OA sims #2–3, weekly mocks.

### Stage 4 — Junior-role-ready (~Weeks 19–20)

**Criteria (all required)**

Track A
- [ ] Medium solve rate ≥ 70%, median ≤ 25 min; one Hard attempted per week with a correct approach
- [ ] OA sims #1–4 passed (at most one fail, retaken and passed)
- [ ] CP-20 Part A (OA-style 90 min) ≥ 3

Track B
- [ ] ForgeCI **v1.0** tagged (M5, with failure recovery, tests, benchmarks, Python tools); [CP-20](./checkpoints/checkpoint-20.md) PASS
- [ ] Three deep-dive rehearsals ≥ 3 (FlowGrid, LedgerX, ForgeCI — ForgeCI from W19)
- [ ] Weekly mocks scoring ≥ 3/4 for three consecutive weeks
- [ ] Junior system design: one unseen problem on paper ≥ 3 (CP-20 D2)
- [ ] Failure-engineering write-ups exist for all three projects and you can tell two stories from them
- [ ] All résumé technologies at "can defend" except AWS/CI-CD advanced items scheduled for W23

**Apply to:** junior SWE, SDE I, associate SE, backend engineer (junior), full-stack junior;
new-grad roles where eligible. Include companies whose stack matches (Java/Spring/Postgres/AWS)
first — the projects speak directly to them.

**Volume:** 10–15/week, with 2–3 referral asks per week. Track response rates per channel.

**In parallel:** FlagForge M1–M4, system-design mock (W22), OA sims #5–6, weekly mocks.

### Stage 5 — Interview-ready (~Week 24)

**Criteria (all required)**

Track A
- [ ] Medium solve rate ≥ 75%, median ≤ 25 min; Hards: approach right ≥ 50%
- [ ] OA sims #7–8 passed; mixed timed sets weekly
- [ ] CP-24 coding rounds (A2, A3) ≥ 3 with an external interviewer; Java rep inside the loop passed

Track B
- [ ] All four projects at **v1.0**, deployed or demo-recorded, docs complete; [CP-24](./checkpoints/checkpoint-24.md) PASS
- [ ] 15-minute deep dive without notes ≥ 3 for **every** project, recorded
- [ ] Full mock loop passed (CP-24 Part A weighted ≥ 3; A4 ≥ 3)
- [ ] Buggy-library run ≥ 70% fixed in 40 min; repository-modification task done in 35 min
- [ ] Every résumé technology at "can defend"; story bank of ≥ 8 STAR stories incl. the gap story
- [ ] Résumé bullets are measured results only ([`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md))

**Apply to:** everything above at volume, including stretch companies. Re-apply where the
blocking period has passed.

**Volume:** 15–25/week in W25–26, then per [`16-interview-prep/maintenance-plan.md`](./16-interview-prep/maintenance-plan.md).

**In parallel:** Polish II (performance, diagrams, demos), final loop W26, maintenance plan.

---

## 4. Self-assessment scorecard

Fill in at each checkpoint (copy the numbers from the checkpoint results log) and whenever you
consider moving up a stage. Be honest — this table decides where your applications go.

| Criterion | Source | Stage 1 (W8) | Stage 2 (W12–13) | Stage 3 (W16–17) | Stage 4 (W19–20) | Stage 5 (W24) |
|---|---|---|---|---|---|---|
| **Track A — Python** | | | | | | |
| Patterns understood (of 24) | dsa-tracker | ≥ 10 | ≥ 15 | ≥ 20 | 24 | 24 |
| Easy solve rate / median time | dsa-tracker | ≥ 80% / ≤ 15 | ≥ 90% / ≤ 12 | ≥ 95% / ≤ 10 | ≥ 95% / ≤ 10 | ≥ 95% / ≤ 10 |
| Medium solve rate / median time | dsa-tracker | — / — | ≥ 40% / ≤ 40 | ≥ 60% / ≤ 30 | ≥ 70% / ≤ 25 | ≥ 75% / ≤ 25 |
| OA sims passed | OA_PREP log | — | — | #1 | #1–4 (≤ 1 fail) | #1–8 (≤ 2 fails) |
| Think-aloud / coding-round score | interview-tracker | — | ≥ 3 ×2 | ≥ 3 | ≥ 3 | ≥ 3 (external) |
| Java rep streak (weeks) | dsa-tracker | ≥ 4 | ≥ 8 | ≥ 12 | ≥ 16 | ≥ 20 |
| **Track B — Java / engineering** | | | | | | |
| Projects at v1.0 | project-tracker | FlowGrid | + LedgerX | (ForgeCI M3) | + ForgeCI | + FlagForge |
| Python components shipped | project-tracker | FlowGrid tools | + LedgerX verifier | — | + ForgeCI tools | + FlagForge tools/SDK |
| Deep-dive rehearsals ≥ 3 | interview-tracker | 1 | 2 | 2 | 3 | 4 |
| Weekly mock average (last 3) | interview-tracker | — | mock #1 done | ≥ 2.5 | ≥ 3 | ≥ 3 |
| System-design score | interview-tracker | — | — | — | ≥ 3 (paper) | ≥ 3 (mock) |
| Checkpoint Backend / Projects | checkpoints | ≥ 3 / ≥ 3 | ≥ 3 / ≥ 3 | ≥ 3 / ≥ 3 | ≥ 3 / ≥ 3 | ≥ 3 / ≥ 3 |
| Checkpoint SQL | checkpoints | ≥ 3 | ≥ 3 | ≥ 3 | ≥ 3 | ≥ 3 |
| Checkpoint Debugging | checkpoints | ≥ 2 | ≥ 3 | ≥ 3 | ≥ 3 | ≥ 3 |
| Résumé techs "can defend" | RESUME_TECH_DEFENSE | 3 | 7 | 10 | 14 | all |
| Git: merged PRs / conflicts resolved | GitHub | ≥ 15 / ≥ 3 | ≥ 30 | ≥ 50 | ≥ 70 | ≥ 90 |
| **Decision** | | apply? | apply? | apply? | apply? | apply? |

Rule: move to a stage only when **every** row for that column is met. Being ahead on Track A
and behind on Track B (or vice versa) means you are *not* at the stage — fix the lagging track
first; that is exactly the imbalance interviews expose.

---

## 5. Application strategy

### 5.1 Role targets

| Role type | Typical loop | Fit | From stage |
|---|---|---|---|
| SWE intern | OA or 1 coding round + project chat | Very good fit: judged on fundamentals and one project | 1 (long-timeline), 2 |
| Junior SWE / associate SE | 1–2 coding + project deep dive + behavioral | Best fit for prior experience + fresh projects | 2–3 |
| SDE I | OA → 2–3 coding + design-lite + behavioral (leadership principles style) | Good once Mediums ≤ 30 min | 3–4 |
| New-grad-style roles | OA → loop | Only where **eligible** (some require recent graduation — read carefully; never claim a status you do not have) | 3–4 |
| Backend engineer (junior), full-stack junior | Take-home or coding + system talk | Projects map directly | 3–5 |
| Contract / project SE (short-term) | Portfolio + technical chat | Your prior history helps; use to fund the search if needed | 2+ |

### 5.2 Channels, in order of conversion

1. **Referrals** — former colleagues, managers, clients from your real roles; alumni; people
   who starred/forked/commented on your repos. Ask with a one-paragraph note: what you are
   building, what you are looking for, a link to one deployed project. Track asks in the
   tracker; follow up once after 7 days.
2. **Direct to hiring manager / small companies** — a short message referencing something
   specific about their stack and one relevant project decision.
3. **Company career pages** — with a tailored résumé (reorder Projects to match their stack).
4. **Job boards** — lowest conversion; still worth 30 min/week at Stage 3+.

### 5.3 The honest résumé

- **Real prior roles**, with real dates, real titles (Junior Dev → Developer → Software Engineer
  → contract/project SE), real responsibilities. Bullets from memory of what you actually did;
  no invented metrics. If you cannot remember a number, describe the outcome qualitatively.
- **The gap** appears as what it is (see [§6](#6-presenting-prior-experience-and-the-gap-honestly)).
  Non-SWE work (fulfillment/logistics operations) is listed truthfully — it is the domain
  knowledge behind FlowGrid, and interviewers like that story.
- **Projects section** for FlowGrid / LedgerX / ForgeCI / FlagForge, each dated **as built**
  (the actual months of Weeks 4–8, 9–13, 14–19, 20–23). **Never backdated**, never presented as
  employment, never described as "production" unless it genuinely serves users. Label them
  "Independent projects" with repo + live URL.
- **Bullets only from measured results** with methodology in the repo's PERFORMANCE.md — see
  [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md) for which project goes first for which role
  and how a benchmark becomes a bullet. "Handled 10k req/s" without a benchmark report is a
  fabrication; "Measured p95 of X ms at Y VUs on a t3.medium against RDS, methodology in repo"
  is a bullet.
- **Skills line** lists only technologies at "can defend" in
  [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md). Add a technology to the résumé the week it
  reaches that status, not before. Python is listed once the project tools exist (W8).
- One page. Projects above older roles once you have two projects; older roles compressed but
  present.

### 5.4 LinkedIn / GitHub checklist

**GitHub**
- [ ] Profile README: one paragraph, links to the four projects, current focus, honest status
- [ ] **Pinned repos:** FlowGrid, LedgerX, ForgeCI, FlagForge (order per [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md) for the target role) — pin only projects at v1.0; foundation exercises stay unpinned
- [ ] Every pinned repo: description + topics, README with screenshots/demo GIF, architecture diagram, "how to run" that works from a fresh clone, CI badge green, `v1.0` release with notes
- [ ] Commit history is yours, readable, spread over the real weeks; no giant "initial commit" dumps
- [ ] No secrets, ever (run a secret scanner before pinning)
- [ ] Issues/milestones show how you worked (closed milestones per M1–M5)
- [ ] Contribution graph reflects the actual schedule — it is evidence of consistency

**LinkedIn**
- [ ] Headline: what you do + what you are looking for (e.g. "Software Engineer (Java/Spring, Python) — building FlowGrid, LedgerX, ForgeCI, FlagForge — open to junior/SDE I roles")
- [ ] About: 5 lines — prior experience, the gap in one honest sentence, current rebuild, links
- [ ] Experience: real roles with real dates; the non-SWE period listed truthfully
- [ ] Projects section mirrors the résumé (dates as built, links)
- [ ] Featured: one deployed project demo + the GitHub profile
- [ ] Skills: only "can defend" technologies; ask former colleagues for honest endorsements
- [ ] Open-to-work set for recruiters from Stage 2

### 5.5 Per-application tailoring (10 minutes, no more)

1. Reorder Projects so the most relevant is first ([`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md)).
2. Match 3–5 keywords from the posting to bullets that are *already true*; never add a keyword
   you cannot defend.
3. One-line note or cover paragraph: why this company, one specific thing about their product
   or stack, one link.
4. Log it in [`trackers/interview-tracker.md`](./trackers/interview-tracker.md) with the date and
   the résumé version used.

---

## 6. Presenting prior experience and the gap honestly

**The frame:** "I was a professional developer for several years (junior → software engineer →
contract work), stepped away from software for [reason, one clause], worked in fulfillment/
logistics operations, and over the past N months I have rebuilt current skills by building four
production-style systems — one of them models the exact domain I worked in."

Rules:
- Say the gap in **one sentence**, without apology and without over-explaining; then pivot to
  what you built. Practise it in [`16-interview-prep/behavioral.md`](./16-interview-prep/behavioral.md)
  and [`16-interview-prep/recruiter-screen.md`](./16-interview-prep/recruiter-screen.md).
- Prior roles: describe what you *actually* did at the level you did it. If a technology from a
  prior role is rusty, say "I used it professionally in [year]; I have refreshed it in
  [project]". Never claim depth you cannot demonstrate in the deep dive.
- The operations experience is an asset: domain knowledge for FlowGrid, comfort with on-call
  style pressure, understanding of real users. Use it in stories, not as filler.
- If asked "why should we hire you over a new grad": current projects that go deeper than
  coursework (concurrency proofs, failure recovery, measured benchmarks) + prior professional
  habits (PRs, reviews, working with stakeholders). Say it with evidence, not adjectives.
- Never let a recruiter's summary drift into inaccuracy ("so you were a senior engineer at…") —
  correct it politely on the spot.

---

## 7. Handling OAs and interviews that arrive early

An application at Stage 1 can produce an OA in Week 9. Rules:

1. **Schedule as late as allowed** (most OAs have a 5–14 day window). Use the days for an OA
   sim at the same length ([`OA_PREP.md`](./OA_PREP.md)).
2. **Sit it anyway if you cannot delay.** A failed OA at a company you would have wanted later
   is a cost — but a skipped OA is a guaranteed zero, and OA exposure is training. Log it as
   an OA attempt in the tracker with problems remembered and time per problem.
3. **Decline gracefully** only when the role requires eligibility you lack or a start date you
   cannot make.
4. **Interviews arriving early (Stage 1–2):** treat every one as a mock with stakes. Prepare
   the deep dive for the project they will ask about, rehearse the gap sentence, and do the
   post-interview write-up from [`INTERVIEW_CHECKLIST.md`](./INTERVIEW_CHECKLIST.md) the same day.
5. **A take-home** at any stage: time-box to what they state (or 6–8 h if unstated), use the
   same standards as the projects (tests, README, CI), and never over-deliver in a way you
   cannot explain live.
6. **Rejections early are data, not verdicts.** Record the round you failed and the reason if
   given; map it to a remediation row in the checkpoint file for that area.

---

## 8. Pipeline tracking

Everything goes in [`trackers/interview-tracker.md`](./trackers/interview-tracker.md): company,
role, channel (referral/direct/board), date applied, résumé version, stage (applied → OA →
phone → onsite → offer/reject), each round's date + self-score + notes, follow-up dates.

Weekly (Sunday review):
- Count applications sent, responses, OAs, interviews, offers; compute response rate per channel.
- Move stale applications (> 3 weeks silent after one follow-up) to "closed — no response".
- Pick the two weakest rounds from the week and map them to a remediation action.
- Adjust next week's volume to the stage's range — not above it.

---

## 9. Warning signs

**Applying too early**
- Response rate is fine but you fail every OA or first coding round → Track A below the stage's
  solve-rate criteria; drop volume to the previous stage and fix DSA first.
- Interviewers probe a résumé technology and you cannot go two levels deep → a technology is on
  the résumé before "can defend"; remove it until it is.
- You are explaining a project from memory of the spec rather than from the code you wrote →
  the deep-dive rehearsal was skipped; do not apply again until it is ≥ 3.
- More than two "we will not be moving forward" from companies you care about within a month at
  Stage 1–2 → pause those companies until Stage 4 (their re-apply window matters).
- Study time collapses below 35 h/week because of interviews → cap interviews at 2/week until
  Stage 4.

**Applying too late**
- Week 20+ with zero applications sent → you are waiting for perfection. Send five this week at
  Stage 4 targets.
- All checkpoints PASS, mocks ≥ 3, but "one more polish week" keeps recurring → the projects are
  done; the résumé is the blocker. Finish [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md)
  decisions today.
- No OA sat by Week 18 → you have no real-platform experience; apply to three OA-first pipelines
  now, regardless of fit.
- Referral asks: zero sent → the highest-conversion channel is unused; send three this week.

**Either way**
- Every rejection without a lesson recorded is waste. Every offer conversation without a
  written-down number is a mistake. Log both.

---

Related: [`ROADMAP.md` §11](./ROADMAP.md#11-job-application-timeline) ·
[`checkpoints/README.md`](./checkpoints/README.md) · [`PROJECT_SELECTION.md`](./PROJECT_SELECTION.md) ·
[`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md) · [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md) ·
[`OA_PREP.md`](./OA_PREP.md) · [`INTERVIEW_CHECKLIST.md`](./INTERVIEW_CHECKLIST.md) ·
[`16-interview-prep/README.md`](./16-interview-prep/README.md) · [`trackers/interview-tracker.md`](./trackers/interview-tracker.md)
