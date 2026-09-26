# Maintenance Plan — After Week 26

The 26 weeks (≈45 h/week) built the skills. The job search is a separate marathon that can take months.
This plan keeps skills sharp and applications flowing at **~12–15 hours/week**, so you
don't burn out or decay while waiting for results. Run it from Week 27 until you sign an offer
(and scale down, not stop, for the first months of the new job).

---

## 1. Weekly template (~13 h)

| Day | Time | Block |
|---|---:|---|
| Mon | 1.5 h | DSA 45 min (1 new + reviews) · Applications 45 min |
| Tue | 1.5 h | DSA 45 min · Résumé-defense drill: 3 questions out loud 20 min · Follow-ups 25 min |
| Wed | 1.5 h | DSA 45 min · Project upkeep 45 min (small feature/fix on ForgeCI or FlagForge) |
| Thu | 1.5 h | DSA 45 min · Applications + networking 45 min |
| Fri | 1 h | Light: 1 behavioral story out loud · tracker update · plan next week |
| Sat | 3–4 h | **Mock interview** (1/week) or **OA simulation** (alternate weeks) · company-specific prep for scheduled interviews |
| Sun | 1 h | Spaced-review DSA · week review · rest |

**Non-negotiables:** DSA 45 min/day (5–6 days), **1 mock/week**, applications every weekday you're not interviewing, rest one full day.

---

## 2. DSA maintenance

- **45 minutes/day:** 1 new problem (Medium, from weak patterns or company-tagged lists) **or** 2 reviews from the Day 0/3/7/14/30 queue in [`../trackers/dsa-tracker.md`](../trackers/dsa-tracker.md).
- Timed: 25 minutes per Medium, method from [`coding-interview-method.md`](./coding-interview-method.md), spoken aloud at least twice a week.
- **Monthly pattern sweep:** one problem from each of the 23 patterns in [`../03-dsa/`](../03-dsa/) over the month; any failure goes back into the review queue.
- Before a specific company's OA/interview: 5–10 problems from that company's commonly reported topics; re-read [`../OA_PREP.md`](../OA_PREP.md).

## 3. Interview practice

| Frequency | Activity |
|---|---|
| Weekly | 1 mock (rotate: coding · behavioral + project · coding · system design) — [`mock-interviews.md`](./mock-interviews.md) |
| Every 2 weeks | 1 OA simulation (reuse OA-1…OA-8 with alternates, or the platform's practice test) |
| Weekly | 3 résumé-defense questions ([`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md)), rotating technologies |
| Weekly | 1 behavioral story rehearsed; update the bank after every real interview |
| Monthly | 10-minute deep-dive of each of the four projects re-recorded without notes ([`project-deep-dive.md`](./project-deep-dive.md)) |
| Monthly | One debugging drill or backlog feature from [`../21-debugging-code-reading/drills.md`](../21-debugging-code-reading/drills.md) |

## 4. Applications pipeline

| Metric (weekly) | Target |
|---|---|
| Quality applications (tailored résumé bullets, role requirements checked) | 10–15 |
| Referral / networking touches (ex-colleagues, meetups, alumni, thoughtful messages) | 3–5 |
| Follow-ups on applications > 10 days old | all |
| Pipeline review | 15 min Friday |

- Track every application in [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md#applications-pipeline).
- Prioritise: referrals > direct applications with a tailored note > mass-apply.
- Re-read [`../JOB_READINESS.md`](../JOB_READINESS.md) monthly; move up role tiers when the criteria are met.
- Response rate check every 4 weeks: < 5 % responses → rework résumé and targeting; OAs but no interviews → OA practice; interviews but no offers → mock focus on the failing round type.

## 5. Keep GitHub and projects alive

- One meaningful commit per week (feature, test, refactor, docs) on one of the four projects (ForgeCI and FlagForge first — they carry the strongest stories) — not busywork.
- Keep the AWS deployments **off** unless you're demoing them (cost) — ForgeCI's worker EC2 especially. Keep the teardown/redeploy runbook tested so you can bring it up before an interview.
- Pick backlog items that give new interview stories: FlowGrid split fulfillment, LedgerX risk rules, ForgeCI SQS-vs-Redis experiment, FlagForge segments or a TypeScript SDK — or a Testcontainers test you were missing.
- Update READMEs with anything you'd want an interviewer to see.

## 6. After each real interview (same day, 20 min)

- [ ] Write down every question (technical and behavioral) from memory — for your own practice only; respect any confidentiality the company asks for.
- [ ] Score yourself with the rubric.
- [ ] One thing to fix → schedule it in the next 3 days.
- [ ] Thank-you email within 24 h.
- [ ] Update the tracker (stage, date, next step).

## 7. Handling rejection and long searches

- Expect many rejections and silence; it's the base rate, not a verdict. Track **process metrics** (applications, mocks, problems) which you control, not only outcomes.
- If you get feedback, log it verbatim and turn it into one drill.
- Every 4 weeks, a **retro**: what's working, what isn't, one change. Don't change everything at once.
- Protect sleep, exercise and one full rest day. Minimum viable day (ROADMAP §12) still applies: 1 DSA review + 15 min reading.

## 8. Once you have an offer

- Compare offers on role, team, mentorship, growth, and total compensation; ask for time to decide if needed.
- Keep the habit at lower intensity for the first 3 months: 2–3 DSA problems/week, and turn the "read an unfamiliar codebase" method ([`../21-debugging-code-reading/method.md`](../21-debugging-code-reading/method.md)) on your new team's repo in week one.
