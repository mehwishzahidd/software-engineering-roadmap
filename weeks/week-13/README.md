# Week 13 — LedgerX M5: deploy, document, advanced features → v1.0

[← Week 12](../week-12/) · [Roadmap](../../ROADMAP.md) · [Week 14 →](../week-14/)

**Phase 2 · LedgerX (Weeks 9–13)** — final week. Deploy LedgerX to AWS (faster than FlowGrid's deploy — that is the point of repeating it), finish the docs set, tag **v1.0 (Strong Résumé Version)**, then — only then — add the advanced features: scheduled payments and basic risk rules. Also: first **unfamiliar-code drill** and the **LedgerX deep-dive rehearsal**.

| Block | Hours | Notes |
|---|---:|---|
| Project (LedgerX M5) | 26 | AWS deploy, CI/CD, docs set, security checklist, v1.0; then scheduled payments + risk rules |
| Learning | 6 | Scheduled work, basic rule engines, security review checklist, codebase-reading method |
| DSA (Graphs BFS/DFS) | 7 | 8 new + reviews |
| Interview / review | 6 | Think-aloud, drill, **unfamiliar-code drill #1**, **LedgerX deep-dive rehearsal**, applications |

---

## 1. Main objective

- LedgerX runs on AWS (EC2 + Docker Compose, RDS Postgres, Redis on EC2 or ElastiCache only if cheaper, CloudWatch logs + one alarm, IAM least privilege) via a CI/CD pipeline (test → image → GHCR → deploy), in **≤ 60 % of the wall-clock time FlowGrid's deploy took** (check your Week 8 log).
- Docs set complete: README, ARCHITECTURE, API, DATABASE, TESTING, DEPLOYMENT, SECURITY, DESIGN_DECISIONS, PERFORMANCE + ADRs + ERD.
- Tag **`v1.0`** = M1–M4 + deploy + docs.
- Advanced (after v1.0): **scheduled payments** (`SCHEDULED → PENDING → …`, a scheduler that is safe with multiple app instances) and **basic risk rules** (velocity limit, max amount) evaluated before a transfer, configurable without redeploy.
- You can give a **12-minute LedgerX deep dive without notes** and survive 10 minutes of follow-ups.

## 2. Prerequisites

- Tag `m4`; Checkpoint 12 done (remediation items scheduled into this week's review blocks if any).
- FlowGrid deploy notes from [Week 8](../week-08/) at hand: IAM user/role, security groups, RDS parameter group, Compose on EC2, the CI workflow. You will reuse and *improve* them.
- Your AWS cost guard from [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md) is active (budget alarm).
- Graph DSA is in Python: `collections.deque` for BFS, `defaultdict(list)` adjacency, `sys.setrecursionlimit(10**6)` (or iterative DFS) for deep grids — [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md).

## 3. Learning topics

| Topic | Subtopics | File |
|---|---|---|
| AWS deploy repetition | IAM least privilege for the deploy user, EC2 + Compose, RDS Postgres 16 (parameter group, `rds.force_ssl`), Redis placement decision, CloudWatch agent + log group + alarm, S3 for exports | [`12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md), [`12-aws/iam.md`](../../12-aws/iam.md), [`12-aws/rds.md`](../../12-aws/rds.md), [`12-aws/cloudwatch.md`](../../12-aws/cloudwatch.md), [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md) |
| CI/CD pipeline | matrix of jobs, caching Maven, build + push to GHCR, SSH deploy step with secrets, environment protection | [`13-cicd/github-actions.md`](../../13-cicd/github-actions.md), [`13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md) |
| Scheduled work | `@Scheduled` vs `TaskScheduler`, multi-instance safety (`SELECT … FOR UPDATE SKIP LOCKED` claim), missed runs, idempotent execution, clock/timezone | [`05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md) |
| Basic rule engines | rule = predicate + action + priority; config table; evaluation order; deny vs review; testability | [`14-cs-fundamentals/software-engineering-practices.md`](../../14-cs-fundamentals/software-engineering-practices.md) |
| Security review checklist | authn/authz per endpoint, IDOR, mass assignment, secrets, TLS to RDS, dependency scan, rate limiting (Redis token bucket) | [`05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md), [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md) |
| Codebase-reading method | entry points → data model → one request end-to-end → tests as docs → failing test → fix | [`21-debugging-code-reading/method.md`](../../21-debugging-code-reading/method.md), [`21-debugging-code-reading/README.md`](../../21-debugging-code-reading/README.md) |
| Structured logging | JSON logs, MDC correlation, CloudWatch Logs Insights queries | [`05-spring-boot/06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md) |

## 4. Concepts to learn

### 4.1 Multi-instance-safe scheduling

`@Scheduled` fires on **every** instance. Two app containers → two executions of "process due scheduled payments" → double payment unless the work itself is claimed atomically:

```sql
-- claim due scheduled payments; safe with N instances
UPDATE scheduled_payment
SET    state = 'CLAIMED', claimed_by = :instance, claimed_at = now()
WHERE  id IN (
  SELECT id FROM scheduled_payment
  WHERE  state = 'DUE' AND run_at <= now()
  ORDER  BY run_at
  LIMIT  50
  FOR UPDATE SKIP LOCKED)
RETURNING id;
```

Then each claimed row executes a transfer with idempotency key `sp:{id}:{run_at}` (so even a re-claim after crash cannot double-pay), and moves to `EXECUTED`/`FAILED`. Missed runs (app down at `run_at`) execute late, once — document this policy. Use `Clock` injection so tests control time.

- **Interview angle:** "How do you run a cron job in a horizontally scaled service?" → claim rows with `SKIP LOCKED` (or a leader lock/ShedLock); make the job idempotent; never rely on "only one instance".
- **Where LedgerX uses this:** scheduled payments; also fixes the reconciliation job and outbox poller for multi-instance (audit them this week!).

### 4.2 Basic risk rules

Table `risk_rule(id, kind, params JSONB, action, priority, enabled)`, kinds: `MAX_AMOUNT` (`{"max": "1000.00"}`), `VELOCITY` (`{"max_count": 5, "window_seconds": 60}`), later `NEW_ACCOUNT_LIMIT`. Evaluate in priority order before the transfer's transaction begins (read-only); first `DENY` wins → `422` with rule id in ProblemDetail; `REVIEW` puts the txn into a `PENDING_REVIEW` state (new transition — update the machine!). Velocity counts come from Redis `INCR` + `EXPIRE` per wallet-window, with DB fallback if Redis is down (degrade: count from `journal_txn` in the window — slower, still correct).

```java
public interface RiskRule { Optional<RiskDecision> evaluate(TransferCommand cmd, RiskContext ctx); }
// engine: rules.stream().sorted(byPriority).map(r -> r.evaluate(cmd, ctx)).flatMap(Optional::stream).findFirst()
```

- **Interview angle:** "Why a table instead of code?" → change limits without redeploy; audit who changed what (rule changes go through the audit log too).
- **Where LedgerX uses this:** M5 advanced; FlagForge's rule engine (Week 21) generalises it.

### 4.3 Security review before v1.0

Walk every endpoint with a table: method/path → auth required? → role? → ownership check? → input validation → rate limit? Findings become issues. Must-haves: no IDOR on `/wallets/{id}/*` (test with two users), admin endpoints ADMIN-only, secrets from env/SSM not in the image, RDS TLS enforced, `Actuator` endpoints restricted, dependency scan in CI (`mvn dependency-check` or GitHub Dependabot alerts enabled), token bucket on `POST /transfers` (Redis Lua, ≤ 25 lines — see [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md)).

### 4.4 Deployment, second time

Measure yourself: Week 8 took N hours; target ≤ 0.6 N. Improvements that make it faster: parameterised Compose (`.env` per environment), one IAM policy document you keep in-repo (`infra/iam/deploy-policy.json`), a `deploy.sh` (Bash — see [`10-linux/bash-scripting.md`](../../10-linux/bash-scripting.md)) that pulls the GHCR image and restarts with health check, a CloudWatch alarm on `ERROR` log count. Redis: on EC2 in Compose unless ElastiCache is cheaper for your account — record the decision and price in `docs/DEPLOYMENT.md`.

```bash
#!/usr/bin/env bash
set -euo pipefail
IMAGE="ghcr.io/${GH_OWNER}/ledgerx:${TAG:?TAG required}"
docker compose pull app
docker compose up -d --no-deps app
for i in $(seq 1 30); do
  curl -fsS http://localhost:8080/actuator/health >/dev/null && { echo "healthy"; exit 0; }
  sleep 2
done
echo "app did not become healthy" >&2; docker compose logs --tail=100 app; exit 1
```

- **Interview angle:** "Describe your deployment pipeline." → concrete: GitHub Actions → `mvn verify` (with Testcontainers) → build image → push GHCR → SSH → `deploy.sh` → health gate → CloudWatch alarm; rollback = redeploy previous tag.

### 4.5 Codebase-reading method (learning for the drill — Track B, Java)

Read [`21-debugging-code-reading/method.md`](../../21-debugging-code-reading/method.md) *before* the drill: map the repo in 10 minutes (`pom.xml`, entry point, packages, tests), pick one failing test, trace the call path with the IDE debugger, form a hypothesis, fix, run `mvn test`, write a 3-line explanation. Time-boxed; the goal is method, not heroics. This is a **Java** skill (the buggy-library is a Maven project) and belongs to Track B — OA simulations from Week 17 include a task of this shape alongside Python algorithm problems (ROADMAP §10).

## 5. Resources

- AWS docs: *Amazon RDS for PostgreSQL*, *CloudWatch Logs agent*, *IAM policy best practices* — https://docs.aws.amazon.com/
- GitHub docs: *Publishing Docker images* (GHCR), *Using environments for deployment* — https://docs.github.com/en/actions
- Spring docs: *Task Execution and Scheduling* — https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- PostgreSQL 16 docs: `SELECT … FOR UPDATE SKIP LOCKED` — https://www.postgresql.org/docs/16/sql-select.html#SQL-FOR-UPDATE-SHARE
- OWASP API Security Top 10 — https://owasp.org/API-Security/
- Redis docs: *Scripting with Lua*, `INCR` rate-limiting pattern — https://redis.io/docs/latest/
- NeetCode 150 — Graphs; problems in §7

## 6. Exercises and assignments

### 6.1 Warm-up exercises (~1.5 h)

1. **Double-fire demo (30 min).** Run two app instances locally (`docker compose up --scale app=2` behind different ports) with a naive `@Scheduled` job that logs; watch both fire. Then implement the `SKIP LOCKED` claim and watch exactly one process each row.
2. **Token bucket in Lua (30 min).** From [`redis.md`](../../04-sql-databases/redis.md), type the ≤ 25-line script yourself, load with `EVALSHA`, hit it in a loop, observe refill.
3. **IDOR hunt (30 min).** Two JWTs; hit every `/wallets/{id}` route with the other user's id. Any 200 is a bug → issue → fix → test.

### 6.2 Assignment — LedgerX M5

**Acceptance criteria — v1.0 (must):**

- [ ] AWS: EC2 (Compose: app, redis), RDS Postgres 16 with TLS, S3 bucket for statement exports (one endpoint `GET /wallets/{id}/statement.csv` writes to S3 with a presigned URL — small), IAM least privilege, CloudWatch logs + alarm; public URL with `/actuator/health` green.
- [ ] CI/CD: PR → `mvn verify`; `main` → image → GHCR → deploy (environment `production` with required reviewer = you) → health gate.
- [ ] Docs set complete (list in §1) + ERD + 4+ ADRs; README has architecture diagram, how-to-run in 3 commands, and *measured* numbers with methodology links.
- [ ] Security checklist pass with issues closed; rate limit on transfers; Dependabot on.
- [ ] Deploy time recorded and compared with Week 8.
- [ ] Tag `v1.0`.

**Advanced (only after v1.0 — target Thu/Fri):**

- [ ] Scheduled payments: create/cancel; states `SCHEDULED → DUE → CLAIMED → EXECUTED | FAILED`, `CANCELLED`; multi-instance claim; idempotent execution; late-run policy documented; tests with injected `Clock`.
- [ ] Risk rules: `MAX_AMOUNT`, `VELOCITY` from a table; Redis counters with DB fallback; admin CRUD (ADMIN) audited; tests for deny/allow/degrade paths.

### 6.3 Break it

- Deploy with the wrong `SPRING_PROFILES_ACTIVE` so the `chaos` profile is active in prod. Does your context test (Week 12) catch this *before* deploy? It should fail the pipeline. If it does not, add a startup guard that refuses to boot with `chaos` outside test.
- Stop Redis on EC2; send transfers under a velocity rule; confirm the DB fallback path (correct but slower) and that CloudWatch shows the WARN.
- Kill the app during scheduled-payment execution after claim; restart; confirm the row is re-claimed after a lease timeout and the idempotency key prevents a second payment.

### 6.4 Debug it

- App on EC2 cannot reach RDS: work the checklist — security group inbound from the EC2 SG, RDS publicly accessible = no, `rds.force_ssl` → JDBC URL needs `sslmode=require`, parameter group applied? Use `psql` from the EC2 box before touching Java.
- CloudWatch shows no logs: agent config path, IAM role missing `logs:PutLogEvents`, Compose log driver still `json-file` — fix with the `awslogs` driver or the agent.

### 6.5 Unfamiliar-code drill #1 (Sat, 60 min, timed) — Track B, Java

[`21-debugging-code-reading/drills.md`](../../21-debugging-code-reading/drills.md), drill #1 in the **Java `buggy-library`** Maven project at `21-debugging-code-reading/exercises/buggy-library/`: clone, `mvn test`, pick the first two failing tests, apply the method, fix, explain. Record time to first hypothesis and time to green in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md). No AI assistance during the drill. (Python is *not* used here: the drill trains reading and debugging a Java/Maven codebase — the Track B skill.)

## 7. DSA — Graphs: BFS / DFS (8 new problems, in Python)

**Language: Python (Track A).** Guide: [`03-dsa/15-graphs-bfs-dfs.md`](../../03-dsa/15-graphs-bfs-dfs.md) · Templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md). Python: adjacency as `defaultdict(list)`; grid BFS with `DIRS = ((1,0),(-1,0),(0,1),(0,-1))` and `deque`; `visited` as a `set[tuple[int,int]]` or mutate the grid in place; recursive DFS on a 300×300 grid can exceed the default recursion limit — raise it or go iterative with an explicit stack; multi-source BFS = seed the queue with all sources at distance 0.

| # | Problem | Difficulty | Time limit | Day |
|---|---|---|---|---|
| 200 | Number of Islands | Medium | 20 min | Mon |
| 695 | Max Area of Island | Medium | 20 min | Mon |
| 133 | Clone Graph | Medium | 25 min | Tue |
| 994 | Rotting Oranges | Medium | 25 min (multi-source BFS) | Tue |
| 417 | Pacific Atlantic Water Flow | Medium | 35 min | Wed |
| 130 | Surrounded Regions | Medium | 30 min | Thu |
| 286 | Walls and Gates (Premium; alt: 542. 01 Matrix) | Medium | 25 min | Thu |
| 684 | Redundant Connection | Medium | 30 min (DFS now; Union-Find next week) | Fri |

Reviews due: Day-3 Week 12 Thu/Fri; Day-7 Week 12 backtracking; Day-14 Week 11 heaps; Day-30 Week 9 trees (first Day-30 of Phase 2 — aim for `Mastered` on at least 4).

**Java rep (Fri, ≤ 30 min):** #200 Number of Islands in Java (`char[][]`, iterative BFS with `ArrayDeque<int[]>`). ForgeCI's DAG scheduler (Week 19) is Java BFS/DFS in production code — keeping graph traversal fluent in Java pays off directly.

## 8. Project work — LedgerX M5 (Deploy + docs + advanced) → v1.0

Spec: [`18-projects/ledgerx/README.md`](../../18-projects/ledgerx/README.md) · [`milestones.md`](../../18-projects/ledgerx/milestones.md) (M5) · [`failure-engineering.md`](../../18-projects/ledgerx/failure-engineering.md) · Docs & résumé: [`docs-and-resume.md`](../../18-projects/ledgerx/docs-and-resume.md)

### 8.1 Task checklist

- [ ] **Mon:** milestone `M5 — Deploy, docs, advanced` + issues; `infra/` (IAM policy JSON, Compose prod, `.env.example`, `deploy.sh`); RDS + EC2 + SG provisioned (time it); Redis decision recorded.
- [ ] **Tue:** CI/CD workflow (build → GHCR → environment-gated deploy → health gate); CloudWatch logs + alarm; S3 statement export; first production deploy.
- [ ] **Wed:** security checklist pass, IDOR tests, rate limiter, Dependabot; docs: SECURITY, DEPLOYMENT, ARCHITECTURE (+ diagram), README rewrite.
- [ ] **Thu (AM):** DATABASE/TESTING/PERFORMANCE/DESIGN_DECISIONS final pass; ERD; **tag `v1.0`** — then **(PM)** scheduled payments (advanced).
- [ ] **Fri:** scheduled payments tests (Clock, multi-instance, crash-after-claim); risk rules engine + `MAX_AMOUNT`.
- [ ] **Sat:** `VELOCITY` rule with Redis + fallback; admin CRUD; failure scenarios; tag `m5`; PR review.

### 8.2 Acceptance summary

v1.0 = M1–M4 + deployed + documented + secured (§6.2 "must"). M5 advanced features are tagged `m5` afterwards and listed under "Advanced Version" in the README. If the advanced part slips, v1.0 still stands — that is the design of the week.

### 8.3 Verification tests you write

| Test | Type | Proves |
|---|---|---|
| `ChaosProfileRefusedInProdTest` | context/startup | app refuses to boot with `chaos` outside test |
| `IdorProtectionIT` | MockMvc, two users | every wallet route returns 403/404 for a non-owner |
| `TransferRateLimitIT` | Testcontainers Redis | 11th request in window → `429` with `Retry-After` |
| `StatementExportIT` | LocalStack or S3 mock | CSV written, presigned URL returned |
| `ScheduledPaymentClaimIsExclusiveIT` | 2 "instances" (two threads calling the claim) | each row claimed once |
| `ScheduledPaymentIdempotentAfterCrashIT` | chaos | re-claim after crash does not double-pay |
| `ScheduledPaymentLateRunIT` | injected `Clock` | app "down" past `run_at` → executes once on next tick |
| `RiskRuleEngineTest` | unit | priority order, first deny wins, disabled rules skipped |
| `VelocityRuleDegradesWithoutRedisIT` | Testcontainers, Redis stopped | DB fallback path counts correctly, WARN logged |
| Smoke test in CI after deploy | `curl` health + one authenticated GET | production is alive after each deploy |

### 8.4 Failure-engineering scenarios this week

M5 set: Redis down under velocity rule; RDS failover/reboot (reboot the instance) → connection pool recovery time; deploy with a broken image → health gate fails → rollback procedure executed and timed; scheduled-payment crash after claim; CloudWatch alarm fires on injected ERROR burst (verify email/SNS). Write-ups → `docs/FAILURES.md`; timings → `docs/DEPLOYMENT.md`.

### 8.5 GitHub expectations

Milestone `M5`; PRs `infra/m5-aws`, `ci/m5-pipeline`, `sec/m5-review`, `docs/m5-v1`, `feat/m5-scheduled-payments`, `feat/m5-risk-rules`. **Release `v1.0`** on GitHub with notes: features, measured numbers (linked), known limitations (honest), advanced roadmap. Close the LedgerX project board. Tag `m5` after advanced features.

## 9. Git activity

- `git tag -a v1.0`; GitHub Release with notes; protect `main` (required checks, required review — you can self-review via a second account or just enforce the check).
- Practise `git revert` of a merged deploy commit as the rollback story (do it once, for real, on a harmless docs commit).
- Archive the failure write-ups and benchmark JSON in a `docs/evidence/` folder — the deep dive cites them.

## 10. Interview preparation

Two tracks, never mixed: **Track A = coding interview, Python**; **Track B = software-engineering / résumé interview, Java/Spring/SQL/AWS/etc.** This week is Track-B-heavy (drill, deep dive) because a project just finished.

- **Think-aloud (Tue, 45 min) — Track A, Python:** LeetCode 994 (Rotting Oranges), recorded, scored.
- **Résumé-defense drill (Sat) — Track B — this week: AWS, Java, JUnit (second pass).** [`17-resume-tech-defense/aws.md`](../../17-resume-tech-defense/aws.md), [`java.md`](../../17-resume-tech-defense/java.md), [`junit.md`](../../17-resume-tech-defense/junit.md). AWS answers must reference *this week's* deploy.
- **Unfamiliar-code drill #1 (Sat, 60 min) — Track B, Java buggy-library:** §6.5.
- **LedgerX deep-dive rehearsal (Sun, 60 min) — Track B:** [`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md) structure; questions from [`18-projects/ledgerx/interview-questions.md`](../../18-projects/ledgerx/interview-questions.md). Record a 12-minute narrative (problem → architecture → the hardest correctness decision → how it was proven, including the independent Python verifier → measured results → what you would change), then answer 10 random questions from the file. Score; note weak spots into [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Applications (Sun):** you are now **internship-ready** per [`JOB_READINESS.md`](../../JOB_READINESS.md) — 5 applications this week; update résumé project section with LedgerX (only measured claims; see [`docs-and-resume.md`](../../18-projects/ledgerx/docs-and-resume.md)).

## 11. Revision work

- Checkpoint 12 remediation items (if any) get the Tue/Thu docs hour.
- Re-derive from memory: idempotency flow, ordered locking query, outbox claim, reconciliation invariants (and how the Python verifier checks them independently), retry-after-crash cases — this *is* the deep dive.
- Flashcards: `SKIP LOCKED` claim pattern, ShedLock alternative, token bucket parameters, IAM least-privilege actions used, `sslmode=require`.

## 12. Daily plan

| Day | Learning | Project | DSA | Interview / other |
|---|---|---|---|---|
| **Mon** (8h) | AWS repetition + IAM policy (2h) | Milestone, `infra/`, RDS/EC2/SG, Redis decision (4.5h) | Graphs §1–5, #200, #695 (1.5h) | — |
| **Tue** (8h) | — | CI/CD pipeline, CloudWatch, S3 export, first deploy (5h) | #133, #994 + Day-3 reviews (2h) | Think-aloud #994 (1h) |
| **Wed** (8h) | Security checklist; rate limiting (2h) | Security pass, IDOR tests, limiter, docs SECURITY/DEPLOYMENT/ARCHITECTURE/README (4.5h) | #417 (1.5h) | — |
| **Thu** (8h) | — | Docs final pass, ERD, **tag v1.0**; scheduled payments (5h) | #130, #286/#542 + Day-7 reviews (2h) | Release notes (1h) |
| **Fri** (5h) | Scheduling + rule engines (1h) | Scheduled-payment tests; risk engine + MAX_AMOUNT (2h) | Reviews + Java rep #200 (+#684) (1h) | Retro prep (1h) |
| **Sat** (6h) | Codebase-reading method (1h) | VELOCITY rule + fallback, admin CRUD, failure scenarios, tag `m5` (3h) | — | Unfamiliar-code drill #1, Java (1h) + résumé drill (1h) |
| **Sun** (3h) | — | — | Day-14/30 reviews | End-of-week test (§13), LedgerX deep-dive rehearsal, trackers, 5 applications |

## 13. End-of-week test (Sunday, 75 min)

**Part A — DSA (30 min, Python).** LeetCode **1091. Shortest Path in Binary Matrix** (BFS on a grid, 8 directions) in ≤ 25 min.

**Part B — Concepts (20 min).**

1. Two app instances, one `@Scheduled` job. What goes wrong and what is your fix?
   <details><summary>Answer</summary>Both instances run it → duplicate work/payments. Fix: claim rows atomically with `FOR UPDATE SKIP LOCKED` (+ idempotent execution), or a leader lock (ShedLock). Never assume a single instance.</details>
2. Why is the scheduled payment's idempotency key `sp:{id}:{run_at}` rather than `sp:{id}`?
   <details><summary>Answer</summary>Recurring schedules execute many times; the key must identify *one* execution. A crash-and-reclaim of the same execution reuses the same key and is deduplicated.</details>
3. Velocity rule with Redis down: what does "degrade correctly" mean?
   <details><summary>Answer</summary>Fall back to counting from the DB within the window — same decision, higher latency, WARN logged and alarmed. Never fail open silently (allow everything) or fail closed (block all) without deciding and documenting.</details>
4. Name three items on your security checklist that a test enforces.
   <details><summary>Answer</summary>IDOR (two-user test on every wallet route), admin-only endpoints (role test), rate limiting (429 test); also: chaos profile refused at startup.</details>
5. What is the rollback procedure for a bad deploy and how long does it take?
   <details><summary>Answer</summary>Redeploy the previous image tag via the same `deploy.sh` with `TAG=<prev>`; health gate confirms. Quote your measured time from the failure exercise. DB migrations must be backward-compatible for this to be safe (expand/contract).</details>
6. BFS vs DFS for "shortest path in an unweighted grid" and why?
   <details><summary>Answer</summary>BFS: it explores in layers, so the first time you reach the target is the shortest path. DFS finds *a* path, not the shortest.</details>

**Part C — Practical (20 min).** Write the `SKIP LOCKED` claim `UPDATE … RETURNING` from memory and a `deploy.sh` health-gate loop in Bash.

**Part D — Explain (5 min).** Deliver the first 5 minutes of your LedgerX deep dive without notes (the rehearsal's opening).

Pass: A in time · B ≥ 5/6 · C correct · D fluent.

## 14. Mastery checklist

- [ ] LedgerX v1.0 is deployed, documented, secured, and released; deploy time beat Week 8's by ≥ 40 %.
- [ ] I can explain multi-instance scheduling safety and idempotent execution keys.
- [ ] I can describe my risk-rule engine and its Redis-down behaviour.
- [ ] I walked a security checklist and have tests for its key items.
- [ ] I completed unfamiliar-code drill #1 (Java buggy-library) with the method (not by guessing).
- [ ] 12-minute LedgerX deep dive recorded (Track B); 10 follow-ups answered; weak spots listed.
- [ ] 8 graph problems done in Python; Java rep done; Day-30 trees mostly `Mastered`.

## 15. Expected deliverables

- `ledgerx`: tags `v1.0`, `m5`; GitHub Release; full docs set; `infra/`; `docs/evidence/`.
- Trackers: [`project-tracker.md`](../../trackers/project-tracker.md) (LedgerX complete, hours total vs 125–150 budget), [`dsa-tracker.md`](../../trackers/dsa-tracker.md), [`technology-tracker.md`](../../trackers/technology-tracker.md) (AWS, Redis, CI/CD → "can defend with evidence"), [`interview-tracker.md`](../../trackers/interview-tracker.md) (drill #1 timing, deep-dive score), [`weekly-progress.md`](../../trackers/weekly-progress.md) (Phase 2 retro: what LedgerX taught that FlowGrid did not).
- Résumé: LedgerX bullets drafted from measured results only ([`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md)).

## 16. If behind / stretch

**Behind?** Per ROADMAP §13 rule 4: **drop the Advanced Version** (scheduled payments, risk rules) before touching v1.0's deploy/docs/security. If even v1.0 slips: finish deploy + README/ARCHITECTURE/DEPLOYMENT/SECURITY this week, remaining docs in Week 14's Thursday docs hour, and record the slip in the tracker. Do not start ForgeCI before `v1.0` is tagged.

**Ahead?** Add `NEW_ACCOUNT_LIMIT` rule and a `REVIEW` action with an admin approve/decline endpoint; add a tiny React/TS admin view (rules + reconciliation findings) — 4 h cap; read ForgeCI's spec ([`18-projects/forgeci/README.md`](../../18-projects/forgeci/README.md)) and sketch its data model on paper for Week 14.
