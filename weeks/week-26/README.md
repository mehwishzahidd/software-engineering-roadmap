# Week 26 — Final loop, résumé from measured results, maintenance plan

[← Week 25](../week-25/) · [Roadmap](../../ROADMAP.md) · [Maintenance plan →](../../16-interview-prep/maintenance-plan.md)

**Phase 5 · Polish & interviews** (weeks 24–26) · Final week → hand-off to [`16-interview-prep/maintenance-plan.md`](../../16-interview-prep/maintenance-plan.md)

| Block | Hours | Focus |
|---|---:|---|
| Project | 12 | Résumé bullets from measured results, [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) decisions, final deploy check, teardown/cost review, Python components final check |
| Learning | 2 | Cost safety, retrospective method |
| DSA (Python) | 5 | Maintenance — **4 new** + reviews + 1 Java rep |
| Interview / review | 20 | **Final mock loop (Track A + Track B)**, 2 OA sims, recruiter screen, applications, retrospective, maintenance plan set up |

---

## 1. Main objective

Close the 26-week plan honestly: write résumé bullets **only** from numbers that exist in a
`PERFORMANCE.md` with a method; decide which projects lead; verify every deployment once more
and then decide, per project, whether it stays up (cost) or is torn down with a documented
"deploy on demand" path; run the final full loop; and set up the maintenance rhythm so the
skills do not decay while applications run. The calendar ends; the practice does not.

## 2. Prerequisites

- [Week 25](../week-25/): four `PERFORMANCE.md` files with methodology, diagrams, demos; full-loop stage scores.
- [Checkpoint 24](../../checkpoints/checkpoint-24.md) passed or remediated.
- Application pipeline live in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).

## 3. Learning topics

| Topic | Subtopics | Read |
|---|---|---|
| Cost safety and teardown | Budgets/alarms check, what costs money while idle (EC2, RDS, EBS snapshots, NAT, Elastic IPs), stop vs terminate, RDS snapshot then delete, S3 lifecycle, "deploy on demand" runbook, IAM key rotation/deletion | [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md), [`12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md), [`12-aws/iam.md`](../../12-aws/iam.md) |
| Résumé from evidence | Bullet = action + system + measured result + method reference; what is *not* a bullet; project selection and ordering; honest tiers (v1.0 vs advanced) | [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md), [`RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md), [`18-projects/README.md`](../../18-projects/README.md) |
| Retrospective and maintenance | What compounding looks like after W26: weekly DSA reviews, one Java rep, one project touch, mock cadence, tracker hygiene | [`16-interview-prep/maintenance-plan.md`](../../16-interview-prep/maintenance-plan.md), [`16-interview-prep/README.md`](../../16-interview-prep/README.md) |

## 4. Concepts to learn

### 4.1 The bullet rule (from `PROJECT_SELECTION.md`)

A bullet is allowed only if every claim in it is traceable to a file in the repo:

| Allowed | Not allowed |
|---|---|
| "Built a distributed CI platform (Java 21/Spring Boot, Redis, Docker) executing pipeline jobs in isolated containers; measured **X jobs/min with 3 workers and p95 queue wait Y ms** on a 2-vCPU host (method in `docs/PERFORMANCE.md`)" | "Handled thousands of builds per day" (never measured), "used by N developers" (no users), "improved performance by 40 %" without a before/after run |
| "Implemented idempotent transfers with ordered pessimistic locking; **concurrency test proves exactly one of two concurrent $400 withdrawals from a $500 balance succeeds**; Python reconciliation verifier independently confirms zero ledger drift" | "Bank-grade", "production-ready", "highly scalable" |
| "Feature-flag SDK (Java, published to GitHub Packages) with local evaluation, ETag polling and stale-if-error; **cross-SDK contract tests (Java + Python) against shared conformance vectors**" | "Used by teams to ship faster" |

Write 3–4 bullets per project; then delete the weakest until each project has 2–3. Each bullet gets a footnote in your private notes: file path + line/section that backs it.

### 4.2 Choosing what leads

Follow [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md): the project that best matches the role's stack leads; ForgeCI or LedgerX usually leads for backend roles (hardest engineering), FlowGrid for logistics/operations-adjacent companies (your domain history is real), FlagForge for platform/developer-tooling roles. Two projects on the résumé in full, two as one-liners with links; all four pinned on GitHub in that order.

### 4.3 Final deploy check, then decide

For each deployed project, run the smoke test from its `DEPLOYMENT.md` and record the result. Then choose:

- **Keep up** (only if it is the leading project for current applications and the monthly cost is known and acceptable): budget alarm verified, IAM keys rotated, security groups re-checked, CloudWatch alarm tested, a weekly check in the maintenance plan.
- **Tear down** (default for the rest): RDS final snapshot → delete; EC2 terminate; release Elastic IPs; delete unused EBS volumes/snapshots; S3 lifecycle or delete; keep the IAM role but delete access keys; verify **zero** resources in the Cost Explorer next day. README states "deployed on demand — see `docs/DEPLOYMENT.md` (≈ 1 h)"; the demo GIF shows it running.

```bash
# quick inventory before you claim "torn down" (adjust region)
aws ec2 describe-instances --query 'Reservations[].Instances[].[InstanceId,State.Name]' --output table
aws rds describe-db-instances --query 'DBInstances[].[DBInstanceIdentifier,DBInstanceStatus]' --output table
aws ec2 describe-addresses --query 'Addresses[].PublicIp' --output table
```

- **Interview angle (Track B):** "Is it live?" — "FlowGrid is; the others deploy on demand in about an hour from the documented runbook — I tore them down to control cost, which is also a real operations decision."

### 4.4 The maintenance rhythm

Set it up **this week** in [`16-interview-prep/maintenance-plan.md`](../../16-interview-prep/maintenance-plan.md): weekly DSA reviews + 2–3 new Python problems + 1 Java rep; one project touch per week (a bug, a dependency bump, a doc fix — keeps the repos alive and your context warm); one mock per two weeks per track; tracker updates on Sundays; a monthly re-read of one project's ADRs. Put the recurring blocks in your calendar before the week ends.

## 5. Resources

- AWS docs: Cost Explorer, Budgets, RDS snapshots/deletion, EC2 termination checklist.
- [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md), [`JOB_READINESS.md`](../../JOB_READINESS.md), [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md).
- [`16-interview-prep/maintenance-plan.md`](../../16-interview-prep/maintenance-plan.md).

## 6. Exercises and assignments

### Exercise A — Bullet audit (Mon, 2 h)

Draft bullets per §4.1; for each claim write the backing file path. Acceptance: no claim without a path; no adjective without a number.

### Exercise B — Cost inventory (Tue, 1 h)

List every AWS resource per project with monthly cost estimate; decide keep/tear down; write the decision in each `docs/DEPLOYMENT.md`. Acceptance: total expected monthly cost written down and below your limit.

### Break it (final)

- Try the "deploy on demand" runbook for one torn-down project on a clean machine, timing it. If it takes > 2 h or fails, fix the runbook — that is the last project task of the plan.

### Debug it

- Cost Explorer still shows charges after teardown: EBS snapshots, an Elastic IP not released, a NAT gateway, or RDS automated backups retained — find it, delete it, note it in [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md)'s checklist for next time.

## 7. DSA — Maintenance (4 new, Python)

Four problems, all timed, from the two weakest patterns after W25's recomputation (2 + 2), in Python with the OA clock. Then run the **Day-30 reviews for W23–W24 sets** and mark `Mastered` only after an independent, clean, timed solve. Fallbacks (real numbers): 20 (Valid Parentheses — re-timed Easy for confidence), 226 (Invert Binary Tree — re-timed), 121 (Best Time to Buy and Sell Stock — re-timed), 70 (Climbing Stairs — re-timed), 236 (Lowest Common Ancestor of a Binary Tree), 424, 875, 994 (Rotting Oranges), 1046 (Last Stone Weight).

**Java rep:** 994 (Rotting Oranges) in Java — multi-source BFS with `ArrayDeque<int[]>`.

From next week the cadence in the maintenance plan applies: reviews first, then 2–3 new Python problems, then 1 Java rep. Cheatsheet: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) stays your pre-interview 20-minute read.

## 8. Project work — Final (all four)

### Weekly task checklist

- [ ] Résumé bullets drafted from `PERFORMANCE.md`/tests per §4.1 (Exercise A); reviewed against [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) and [`RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md)
- [ ] `PROJECT_SELECTION.md` decisions recorded for two target role types (which project leads, ordering, one-liners)
- [ ] Final deploy check per project (smoke test from `DEPLOYMENT.md`), results logged in [`trackers/project-tracker.md`](../../trackers/project-tracker.md)
- [ ] Keep/tear-down decision per project; teardown executed per [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md); next-day Cost Explorer check; IAM keys rotated/deleted
- [ ] "Deploy on demand" runbook tested on one torn-down project (timed)
- [ ] README of each project: final status line (live / on demand), demo, measured results, tier (v1.0 / advanced) — honest
- [ ] Python components: `pytest` green on `main` in all four, README accurate, versions pinned
- [ ] GitHub profile: pinned repos in selection order, profile README with one line per project + tech list
- [ ] All four repos: final tag (`v1.1` or `v1.0.x`), releases updated, no open milestone except `Backlog`

### Acceptance summary

- Every résumé claim traceable; nothing unmeasured; tiers stated honestly.
- Deployments verified; costs known; teardown clean or keep justified.
- Runbook proven.
- Repos and profile presentable to a recruiter in 2 minutes and a hiring manager in 10.

### Verification checks

| Check | How |
|---|---|
| Bullet traceability | For each bullet, open the backing file in < 30 s |
| Cost | Cost Explorer daily view shows only the kept project (or ≈ $0) |
| Runbook | Timed deploy-on-demand run recorded in `DEPLOYMENT.md` |
| CI | Four repos green on `main` (Java + Python jobs) |
| Deep-dive readiness | 15-minute deep-dive of each project delivered this week at least once (loop + rehearsal) |

### GitHub expectations

- Final releases; pinned order; profile README; `Backlog` milestones; no secrets, no stale branches.

## 9. Git activity

- Final tags and releases; `git remote prune`; archive nothing — these repos stay active under the maintenance plan.
- Weekly "project touch" commits start next week (dependency bump, doc fix, small issue).

## 10. Interview preparation (20 h)

**Track A (Python coding)**
- **OA simulations #11 and #12** (Mon 90 min, Thu 120 min): [`OA_PREP.md`](../../OA_PREP.md) — Python problems + a Java [`buggy-library`](../../21-debugging-code-reading/exercises/buggy-library/) or [`drills.md`](../../21-debugging-code-reading/drills.md) task; run them at the time of day real OAs arrive for you.
- **Coding mock** (Tue, 45 min, Python): [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md) — request the pattern from your weakest list.
- **Python quick-fire** (Wed, 30 min): 20 questions from [`19-python/interview-questions.md`](../../19-python/interview-questions.md) out loud (complexities, pitfalls, stdlib).

**Track B (Java / projects / system design / behavioral)**
- **Final full loop** (Sat, ≈4 h with breaks): 45 min Python coding → 45 min system design ([`16-interview-prep/system-design-interview.md`](../../16-interview-prep/system-design-interview.md)) → 45 min deep-dive of the project you *did not* lead with in W25 + Java/Spring/SQL probing ([`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md), [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md)) → 30 min behavioral ([`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md)) → 15 min "questions for us". Compare stage scores with W25; the weakest stage defines the first maintenance-plan focus.
- **Engineering mock** (Wed, 45 min): résumé grill on the **new bullets** — every number challenged ("how did you measure", "what was the environment", "what would change in production").
- **Recruiter screen** (Mon, 20 min): final "about me" with the two leading projects and the Python + Java positioning in one sentence ([`16-interview-prep/recruiter-screen.md`](../../16-interview-prep/recruiter-screen.md)).
- **Deep-dive rehearsals** (Fri, 1.5 h): the two non-loop projects, 12 minutes each, cold questions from their `interview-questions.md` ([flowgrid](../../18-projects/flowgrid/interview-questions.md), [ledgerx](../../18-projects/ledgerx/interview-questions.md), [forgeci](../../18-projects/forgeci/interview-questions.md), [flagforge](../../18-projects/flagforge/interview-questions.md)).

**Applications (≈5 h across the week):** interview-ready tier volume per [`JOB_READINESS.md`](../../JOB_READINESS.md); résumé updated with the audited bullets before sending anything; follow-ups; pipeline table current. Read [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md) the night before any real interview.

## 11. Revision and retrospective

- **26-week retrospective** (Sun, 1 h) in [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md): hours actually spent vs plan (projects ≈ 700 / DSA ≈ 180 / learning ≈ 170 / interview ≈ 100), DSA status distribution, checkpoint scores over time, mock score trend per track, what you would tell yourself in Week 1.
- Re-read [`RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md)'s matrix and mark the final confidence per technology; anything below "confident" is the first maintenance topic.
- DSA reviews as scheduled — the Day-30 items from W24–25 continue into the maintenance plan.

## 12. Daily plan

| Day | Plan |
|---|---|
| **Mon (8 h)** | Learning 1: cost safety · Project 3: Exercise A bullets, selection decisions · **OA sim #11** 1.5 h + review · Interview 2.5: recruiter final, applications with new bullets |
| **Tue (7 h)** | Project 3: Exercise B cost inventory, final deploy checks · DSA 1.5 (2 problems) · **Track A mock** 1 h · Interview 1.5: applications |
| **Wed (8 h)** | Project 3: teardown execution, runbook test start · DSA 1 (reviews) · **Track B engineering mock** 1 h · Python quick-fire 0.5 · Interview 2.5: applications, follow-ups |
| **Thu (7 h)** | Project 2: runbook finish, READMEs final status, profile README · **OA sim #12** 2 h + review 0.5 · DSA 1 (2 problems) · Interview 1.5: prep for loop |
| **Fri (5 h)** | Project 1: Cost Explorer check, final tags · Deep-dive rehearsals 1.5 · DSA reviews 1 + Java rep · Retro prep 0.5 · rest |
| **Sat (6 h)** | **Final full loop** 4 h (with breaks) · debrief 1 · light DSA review 0.5 · rest |
| **Sun (3 h)** | 26-week retrospective · maintenance plan set up in calendar · trackers final · rest |

Rest is scheduled on purpose: Fri afternoon and Sat evening are off. Total ≈ 44 h.

## 13. End-of-week test (the last one)

1. Deliver the two leading projects' deep-dives back-to-back (12 min each) without notes.
2. Defend three résumé bullets: for each, name the file that backs it and the method behind the number.
3. Timed, Python: one Medium (25 min) from your weakest pattern; then explain its complexity and one Python pitfall you avoided.
4. In Java, from memory: sketch the reliable-queue lease loop or the ordered-lock transfer (interviewer's choice) in ≤ 20 lines.
5. State every AWS resource still running and its monthly cost.

Pass: 5/5 — this is the bar for the loops you are now entering; a miss becomes the first maintenance-plan item, not a reason to delay applying.

## 14. Mastery checklist (26-week exit)

- [ ] Four projects at Strong Résumé Version (`v1.0`+), tested (Java + Python), documented, measured, deployed or deployable on demand
- [ ] Every résumé bullet is traceable to a measured result or a test
- [ ] Track A: Mediums in Python in ≤ 25–30 min consistently; OA sims passing; Java reps keep collections fluent
- [ ] Track B: 15-minute deep-dive of any project without notes; every résumé technology defensible; junior system design with my own systems as case studies
- [ ] AWS costs known and controlled; teardown/deploy-on-demand proven
- [ ] Maintenance plan scheduled: reviews, new problems, Java rep, project touch, mocks, trackers

## 15. Expected deliverables

- Audited résumé bullets + `PROJECT_SELECTION.md` decisions; profile README; final tags/releases.
- Deployment decisions and cost evidence in each `DEPLOYMENT.md`; [`trackers/project-tracker.md`](../../trackers/project-tracker.md) final rows.
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 4 new + Day-30 reviews + Java rep; `weak` list carried into the maintenance plan.
- [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md): OA #11/#12, both mocks, final-loop stage scores, pipeline.
- [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): final confidence per technology.
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md): 26-week retrospective.
- Hand-off: [`16-interview-prep/maintenance-plan.md`](../../16-interview-prep/maintenance-plan.md) filled in with your weakest stage/pattern/technology and calendar blocks created.

## 16. If behind / stretch

**Behind:** bullets and the final loop are non-negotiable; teardown can be "stop instances + snapshot" this week and full cleanup next week under the maintenance plan; profile README can be five lines. Do not extend the plan by "one more polish week" — apply now and keep improving under the maintenance rhythm (ROADMAP §11).

**Stretch (after W26, never before):** the ADVANCED tiers you skipped (split fulfillment, scheduled rollouts, TypeScript SDK), a blog-style write-up of one failure-engineering story per project, contributing a small fix to a library you used (docker-java, Testcontainers, k6 docs).
