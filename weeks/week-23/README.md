# Week 23 — FlagForge M4: dashboard, propagation, deploy (v1.0)

[← Week 22](../week-22/) · [Roadmap](../../ROADMAP.md) · [Week 24 →](../week-24/)

**Phase 4 · FlagForge** (weeks 20–23) · Milestone **M4** · Target: **Strong Résumé Version, tagged `v1.0`**

| Block | Hours | Focus |
|---|---:|---|
| Project | 28 | React admin dashboard, propagation (publish → Redis pub/sub → SSE → SDKs), stampede protection, Docker/AWS/CI, docs, benchmarks, failure exercises; **Python: `tools/config-validator`, finish `sdk-python/` + `rollout-sim`** |
| Learning | 5 | Pub/sub invalidation, cache-stampede protection, security review, AWS deploy repetition |
| DSA (Python) | 7 | Timed mixed — **6 new** + reviews + 1 Java rep |
| Interview / review | 5 | OA simulation #6 (Track A), **FlagForge deep-dive rehearsal** (Track B), résumé defense complete for all technologies |

---

## 1. Main objective

Finish the fourth project: an admin dashboard humans can use, propagation that makes a publish
visible to every SDK within a second, protection against the stampede that a popular flag
would cause, and a fourth AWS deployment that takes a day, not a week. Tag `v1.0` and
rehearse the FlagForge deep-dive. By the end of this week **all four projects exist, are
deployed and are explainable** — the raw material for weeks 24–26.

## 2. Prerequisites

- M3 SDK published ([Week 22](../week-22/)); SSE streaming at least on the server side.
- React + TS from FlowGrid M4 ([Week 7](../week-07/)) and ForgeCI M3 ([Week 16](../week-16/)); forms/routing in [`08-react/03-forms-routing.md`](../../08-react/03-forms-routing.md).
- AWS deploy done three times; [`12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md) is now a checklist you follow, not a lesson.

## 3. Learning topics

| Topic | Subtopics | Read |
|---|---|---|
| Pub/sub invalidation | Redis `PUBLISH env:{id}` after commit → every api instance drops its in-process snapshot and pushes an SSE nudge; at-most-once nature of pub/sub, why the TTL and polling fallback stay | [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md), [`15-system-design/caching.md`](../../15-system-design/caching.md) |
| Cache-stampede protection | Single-flight (one loader per key, others wait), lock with `SET key NX PX`, early/probabilistic refresh, jittered TTL, serve-stale-while-revalidate | [`15-system-design/caching.md`](../../15-system-design/caching.md) |
| Security review (first pass) | OWASP API Top 10 walk-through for your own endpoints: BOLA (tenant checks), broken auth (SDK keys), excessive data exposure (audit `before/after`), rate limiting, mass assignment | [`05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md), [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md) |
| React forms-heavy admin | Rules editor as controlled forms, optimistic UI vs confirm-on-save, `If-Match` 412 handling, tables with pagination, SSE hook for live "version published" banner | [`08-react/03-forms-routing.md`](../../08-react/03-forms-routing.md), [`08-react/04-api-integration-auth.md`](../../08-react/04-api-integration-auth.md), [`08-react/05-architecture-testing.md`](../../08-react/05-architecture-testing.md) |
| AWS deploy repetition | Same topology as ForgeCI minus workers; CloudWatch alarm on 5xx; budget; teardown script | [`12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md), [`12-aws/cloudwatch.md`](../../12-aws/cloudwatch.md), [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md), [`13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md) |
| Python: config validation tool | Reading a rules payload (`json`), typed model (`dataclasses`/`pydantic` optional), detecting overlaps/unreachable rules/invalid ranges, exit codes for CI, `argparse`, `pytest` parametrized cases | [`19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md), [`19-python/02-interview-toolkit.md`](../../19-python/02-interview-toolkit.md) |

## 4. Concepts to learn

### 4.1 Propagation path, end to end

```
POST publish ──tx commit──▶ afterCommit: DEL env:{id}:snapshot ; PUBLISH env-updates {envId, version}
                                              │
   api-1 subscriber ◀─────────────────────────┴───────────▶ api-2 subscriber
   drop in-process snapshot; SSE "snapshot-updated {version}" to every connected SDK on this instance
                                              │
   SDK: GET /snapshot If-None-Match ──▶ 200 new snapshot (or 304 if it already has it)
```

- Redis pub/sub is fire-and-forget: a subscriber that is reconnecting misses the message. That is fine **because** the SDK still polls (long interval) and the cache still has a TTL — layered guarantees, each cheap.
- Measure **propagation latency**: timestamp at publish response → timestamp when the sample app's listener fires; 100 publishes; p50/p95/p99.
- **Interview angle:** "How fast does a flag change reach clients?" — a number with a method, plus "and if pub/sub is lost, within one polling interval".

### 4.2 Single-flight snapshot loading (stampede protection)

```java
private final ConcurrentHashMap<UUID, CompletableFuture<Snapshot>> inflight = new ConcurrentHashMap<>();

Snapshot load(UUID envId) {
    CompletableFuture<Snapshot> f = inflight.computeIfAbsent(envId, id ->
            CompletableFuture.supplyAsync(() -> loadFromRedisOrDb(id), loaderPool)
                             .whenComplete((s, e) -> inflight.remove(id)));
    return f.join();                    // callers of the same key share one load
}
```

- Per instance this collapses N concurrent misses into one DB query. Across instances, add a Redis lock `SET lock:env:{id} <token> NX PX 5000`; losers wait briefly and re-read the cache (or serve stale if present).
- Jitter the TTL (`600 ± 60 s`) so environments do not expire together.
- **Interview angle:** "What happens when the cache expires for a flag hit 5 000 times/s?" — single-flight + stale-while-revalidate; show the k6 graph before/after.

### 4.3 The dashboard (scope control)

Screens: projects/environments switcher · flag list (search, status, version) · flag detail with **rules editor** (add/reorder/remove rules, percentage slider with bucket preview) · versions tab (diff to previous, **rollback** button with confirmation) · audit tab · SDK keys (issue/revoke). Live banner via SSE when someone else publishes (412 on your own publish → "reload and retry").

- Component tests for the rules editor (adding a rule, reordering changes priority, invalid percentage shows error) — [`09-testing/frontend-testing.md`](../../09-testing/frontend-testing.md).
- Keep styling minimal ([`07-javascript-typescript/04-html-css-minimum.md`](../../07-javascript-typescript/04-html-css-minimum.md)); a dashboard that works beats one that is pretty.

### 4.4 Security pass (this project, this week; all projects in W24)

Checklist to run against FlagForge: every tenant-scoped endpoint has a `CrossTenantAccessIT`; SDK keys hashed, shown once, revocable, scoped to one environment; audit `before/after` never includes secrets; admin API rate-limited per token (Redis `INCR` + `EXPIRE` or a token bucket); CORS restricted to the dashboard origin; security headers; dependency scan in CI (`mvn dependency-check` or GitHub Dependabot alerts enabled).

### 4.5 The configuration validator (`tools/config-validator`)

A Python linter for rule sets, runnable from the dashboard's CI or by hand: `python -m config_validator flag.json` → exit 0 (clean), 1 (warnings), 2 (errors). Checks to implement and test:

| Check | Kind | Example |
|---|---|---|
| Percentage outside `0..10000`, duplicate priorities, unknown variation keys | error | `"serve": "onn"` |
| **Unreachable rule**: a `PERCENTAGE` rule with `10000` (100 %) before other rules, or a `USER` rule whose users are all matched by an earlier `USER` rule | warning | rule 3 never evaluated |
| **Overlap**: two `ATTRIBUTE` rules with identical predicates serving different variations (first wins silently) | warning | `country IN [DE]` twice |
| Flag disabled but rules present | info | maybe intended |

- Pure functions over a typed model; `pytest.mark.parametrize` with 15+ cases; `Counter` for duplicate detection; keep it O(rules²) at most (rule sets are tiny).
- Reuse the same `bucketing-vectors.json` if you add a "preview: which of these sample users hit which rule" mode.
- **Interview angle (Track B):** "How do you stop someone shipping a bad flag config?" — server-side validation (M1) *and* a CI-runnable linter with semantic checks the server does not do (unreachable/overlap).
- **Where FlagForge uses this:** dashboard shows validator warnings before publish (call it via a small endpoint or run it in the ui CI against fixtures); `docs/TESTING.md`.

### 4.6 Deploy in a day

Order: budget alarm → IAM role → RDS → EC2 (Compose: api, redis, ui via nginx) → CloudWatch logs + 5xx alarm → HTTPS (or IP allow-list) → smoke test (publish from the dashboard; the Java sample app **and** the Python test client on your laptop both see the change) → teardown script tested. Write `docs/DEPLOYMENT.md` as you go, and time yourself: it is a résumé-defense answer ("I have deployed four Spring Boot systems to AWS; the fourth took a day").

## 5. Resources

- Redis docs: Pub/Sub, `SET NX PX`, keyspace notifications (awareness only).
- OWASP API Security Top 10 (2023) — the list itself, applied to your endpoints.
- React docs: forms, `useSyncExternalStore` (for SSE-driven state), Testing Library docs.
- AWS docs: CloudWatch metric alarms on ALB/nginx 5xx (or log-metric filters), Budgets.
- [`RESOURCES.md`](../../RESOURCES.md).

## 6. Exercises and assignments

### Exercise A — Stampede before/after (1 h)

k6: 300 VUs on `/evaluate`, force a cache expiry mid-run (`redis-cli DEL`). Capture DB query count and p99 without single-flight, then with. Acceptance: two graphs in `docs/PERFORMANCE.md`, DB queries at expiry drop from ~hundreds to ~1 per instance.

### Exercise B — OWASP walk (45 min)

For each of the 10 items write one line: "N/A because…", "covered by test X", or "TODO issue #n". Acceptance: no item without a line; TODOs become W24 issues.

### Break it (inside FlagForge)

- Kill the Redis subscriber thread (or restart Redis) and publish. Predict: api instances keep serving old in-process snapshot until TTL/poll; SSE clients get nothing until the subscriber reconnects — verify the reconnect logic exists.
- Open two dashboard tabs, publish in one, publish in the other. Predict 412 and the banner.
- Run 2 api instances behind nginx round-robin; connect an SDK via SSE to one; publish through the other. Predict: the SDK still gets the nudge (pub/sub crosses instances).

### Debug it

- Propagation p99 is 30 s instead of < 1 s: SSE nudges are lost because nginx buffers `text/event-stream` — set `proxy_buffering off` / `X-Accel-Buffering: no`; verify with `curl -N`.
- Dashboard rules reorder saves wrong priorities: the client sends array index but the server expects explicit `priority`; write the contract down in `docs/API.md` and a test.

## 7. DSA — Timed mixed (6 new, Python)

Selection rule as in Week 20, but **every problem is timed with the OA clock** in Python: Easy 12 min, Medium 25 min, Hard 40 min, no hints and no [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) open before time is up. Log time-to-first-correct-submit. Two-problem "sets" (50 min for two Mediums) simulate OA pacing — do two such sets plus two singles. **Java rep:** 207 (Course Schedule) in Java — Kahn's with `ArrayDeque` and `int[] indegree`; you shipped this algorithm in ForgeCI M6, so this rep is also Track B rehearsal.

Pool additions (real numbers): 3 (Longest Substring Without Repeating Characters), 128 (Longest Consecutive Sequence), 155 (Min Stack), 33 (Search in Rotated Sorted Array), 143 (Reorder List), 199 (Binary Tree Right Side View), 207 (Course Schedule), 322 (Coin Change), 57 (Insert Interval), 78 (Subsets).

Reviews due: Day-3 of W22, Day-7 of W21, Day-14 of W20, Day-30 of W18 2-D DP.

## 8. Project work — FlagForge M4

Spec: [`18-projects/flagforge/README.md`](../../18-projects/flagforge/README.md) · [`milestones.md`](../../18-projects/flagforge/milestones.md) · [`failure-engineering.md`](../../18-projects/flagforge/failure-engineering.md) · [`docs-and-resume.md`](../../18-projects/flagforge/docs-and-resume.md) · [`interview-questions.md`](../../18-projects/flagforge/interview-questions.md).

### Weekly task checklist

- [ ] Milestone `M4 – Dashboard, propagation, deploy`
- [ ] Propagation: after-commit `PUBLISH`; subscriber per api instance; in-process snapshot drop; SSE nudge to connected SDKs; reconnect logic for the subscriber
- [ ] Stampede protection: single-flight per instance + Redis lock across instances + jittered TTL; before/after measurement
- [ ] Dashboard (Vite + React + TS): auth, project/env switcher, flag list, flag detail + rules editor, versions + rollback, audit, SDK keys, SSE banner, 412 handling; component tests for the rules editor
- [ ] Security pass: OWASP list, rate limiting on admin API, CORS, headers, Dependabot
- [ ] Docker: ui image (nginx), `compose.yaml` full stack; CI publishes 2 images to GHCR
- [ ] AWS: deploy per §4.5; `docs/DEPLOYMENT.md`; teardown script
- [ ] Benchmarks recorded ([`benchmark-report.md`](../../18-projects/templates/benchmark-report.md)): `/evaluate` p50/p95/p99 at 3 load levels, propagation latency, stampede before/after; `docs/PERFORMANCE.md`
- [ ] Failure exercises from `failure-engineering.md` with regression tests
- [ ] Python `tools/config-validator`: checks table above, exit codes, ≥ 15 parametrized `pytest` cases, README; wired into the ui CI against fixture configs
- [ ] Python `sdk-python/` finished (polling, offline, contract test vs deployed server) and `tools/rollout-sim` result table in `docs/TESTING.md`; `pytest` for all three Python components runs in CI
- [ ] Docs: README (what/why/run/demo GIF placeholder), ARCHITECTURE, API, DATABASE, TESTING (incl. "Python components" section), SECURITY, DESIGN_DECISIONS (ADRs), PERFORMANCE
- [ ] Tag `v1.0`; release notes

### Acceptance summary

- Publish in the dashboard → sample app (via SDK streaming) sees the change; measured p95 < 1 s locally (record the real number).
- Stampede test shows collapsed DB load.
- Dashboard covers flags, rules, versions, rollback, audit, keys; rules editor tested.
- Deployed on AWS with alarms and a tested teardown; CI builds + publishes + (manual) deploys.
- All docs present; `v1.0` tagged; README honest about scope (segments, scheduled rollouts, TS SDK are ADVANCED/not done).
- Python components (`sdk-python/`, `tools/rollout-sim`, `tools/config-validator`) each have README, `pyproject.toml`, type hints, green `pytest` in CI, and are referenced from TESTING.md/PERFORMANCE.md where used — **`v1.0` is not tagged without them**.

### Verification tests

| Test | Given | When | Then |
|---|---|---|---|
| `PropagationIT` | 2 api instances (Compose), SDK on instance A | publish via B | SDK version updates; latency recorded |
| `PubSubLostIT` | subscriber paused | publish | SDK still updates within polling interval; cache TTL bounds staleness |
| `SingleFlightTest` | 100 threads miss same env | load | loader invoked once |
| `CrossInstanceLockIT` | 2 instances, cache empty | 200 concurrent misses | ≤ 2 DB loads |
| `RulesEditorTest` (RTL) | 3 rules | drag rule 3 to top | priorities 1,2,3 re-assigned; save payload correct |
| `Publish412Test` (RTL) | stale `If-Match` | save | banner "reload and retry"; no data loss in form |
| `AdminRateLimitIT` | 100 requests/10 s | | 429 with `Retry-After` |
| `DeploySmokeTest` (manual, documented) | AWS stack | dashboard publish | laptop Java sample app and Python client both log the new value |
| `test_unreachable_rule` / `test_overlap` / `test_exit_codes` (pytest) | fixture configs | validator | expected warnings/errors and exit code |
| `test_python_contract_vs_deployed` (pytest, opt-in via env var) | deployed server | 50 contexts | Python SDK == server |

### Failure scenarios to run

1. Redis restart mid-traffic (subscriber reconnect; snapshot rebuild; stampede protection in action).
2. Postgres restart while cache warm — evaluations unaffected; publish fails cleanly with 503 ProblemDetail.
3. SSE connections × 500 from a load script — memory and thread usage; heartbeat keeps proxies open.
4. Publish storm (20 publishes/s) — SDK does not fetch 20 times (coalesce nudges by version).
5. Deployment rollback: redeploy previous image sha; Flyway compatibility (no destructive migration in `v1.0`).

### GitHub expectations

- Milestone M4 closed; release `v1.0`; full docs set; screenshots folder with at least the flag detail and versions screens; `docs/PERFORMANCE.md` with three measured tables.

### Interview questions this milestone generates (Track B)

| Question | Strong answer contains |
|---|---|
| "How fast does a change reach clients, and how do you know?" | measured propagation p95; publish → pub/sub → SSE nudge → ETag fetch; fallback bounds (poll interval, TTL) |
| "Pub/sub is at-most-once — so what?" | layered guarantees; every layer cheap; test `PubSubLostIT` |
| "What is a cache stampede and what did you do?" | single-flight per instance, Redis lock across instances, jittered TTL, stale-while-revalidate; before/after graph |
| "How did you handle concurrent edits in the UI?" | `If-Match` → 412 → banner; no silent overwrite; form state preserved |
| "Walk me through your OWASP review." | the table: BOLA tests, SDK key hashing, rate limits, CORS, headers, Dependabot; what is still TODO |
| "How long did the fourth AWS deploy take and why?" | a day; the runbook; SGs, IAM role, alarms, teardown script; what you would add for HA |
| "What Python is in this project?" | Python SDK/test client (contract), rollout simulator (fairness/stickiness), config validator (unreachable/overlap rules) — all tested, in CI, documented |

### Definition of done for the FlagForge phase

- [ ] `v1.0` tagged with release notes; README honest about tiers
- [ ] Propagation + stampede protection measured; `docs/PERFORMANCE.md` three tables
- [ ] Dashboard with tests; security pass table; deployed with alarms + teardown
- [ ] Full docs set; ADRs; diagrams may be ASCII until W25
- [ ] Python components (`sdk-python/`, `rollout-sim`, `config-validator`) tested, typed, documented, in CI
- [ ] Deep-dive recorded; [`trackers/project-tracker.md`](../../trackers/project-tracker.md) closed for FlagForge with actual hours

## 9. Git activity

- Branches: `feat/m4-propagation`, `feat/m4-stampede`, `feat/m4-dashboard-*` (several small PRs), `sec/m4-review`, `ops/m4-aws`, `docs/m4-docs-set`.
- Tag `v1.0`; the SDK keeps its own `sdk-0.1.x` line.

## 10. Interview preparation

**Track A (Python coding)**
- **OA simulation #6** (Fri, 90–120 min): [`OA_PREP.md`](../../OA_PREP.md) — two Python problems as a timed set + the Java repo-modification drill from [`21-debugging-code-reading/drills.md`](../../21-debugging-code-reading/drills.md) on [`buggy-library`](../../21-debugging-code-reading/exercises/buggy-library/).
- **Weekly coding mock** (Tue, 45 min, Python): [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md).

**Track B (Java / projects / résumé)**
- **FlagForge deep-dive rehearsal** (Sat, 2 h): [`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md) + [`18-projects/flagforge/interview-questions.md`](../../18-projects/flagforge/interview-questions.md). Record; 12 minutes; then 10 cold questions, including "what does the Python side of the project do and why is it not just a script?". Compare with the ForgeCI recording from W19 — is the structure tighter?
- **Résumé defense — completion gate:** by Sunday every technology in [`RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md)'s matrix (Java, Spring Boot, PostgreSQL, MySQL, REST, React, TypeScript, JavaScript, Docker, AWS, Redis, Git, GitHub, Maven, JUnit, CI/CD, Linux, **Python**) has been drilled with [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md) and has a "which project proves it" line. This week's remaining gaps are typically MySQL ([`17-resume-tech-defense/mysql.md`](../../17-resume-tech-defense/mysql.md) — the Postgres diff), Linux ([`17-resume-tech-defense/linux.md`](../../17-resume-tech-defense/linux.md)), TypeScript ([`17-resume-tech-defense/typescript.md`](../../17-resume-tech-defense/typescript.md)), GitHub ([`17-resume-tech-defense/github.md`](../../17-resume-tech-defense/github.md)), Python ([`17-resume-tech-defense/python.md`](../../17-resume-tech-defense/python.md) — proven by the four projects' `tools/` and `sdk-python/`, plus daily DSA).
- **Behavioral:** story bank should have ≥ 8 stories; add "a time I cut scope" from this week ([`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md)).
- **Applications:** with four projects, move to the higher volume band in [`JOB_READINESS.md`](../../JOB_READINESS.md); update the résumé with FlagForge `v1.0` **only with measured numbers**.

## 11. Revision

- Re-explain all three "propagation" mechanisms you have built: FlowGrid cache invalidation, ForgeCI log pub/sub → SSE, FlagForge publish → pub/sub → SSE → poll. One paragraph on what generalises.
- Re-read [`14-cs-fundamentals/networking.md`](../../14-cs-fundamentals/networking.md) on HTTP keep-alive and proxies — the SSE-through-nginx lesson.
- DSA reviews.

## 12. Daily plan

| Day | Plan |
|---|---|
| **Mon (8 h)** | Learning 2: pub/sub invalidation + stampede protection · Project 4.5: propagation path, subscriber, SSE nudge · DSA 1.5 (timed set) |
| **Tue (8 h)** | Project 5: single-flight + Redis lock, before/after measurement · DSA 2 + reviews · Track A mock 45 min + review |
| **Wed (8 h)** | Learning 2: OWASP API Top 10 applied, validator design · Project 4.5: dashboard — flags, detail, rules editor; `tools/config-validator` core + tests · DSA 1.5 |
| **Thu (8 h)** | Project 5: dashboard — versions/rollback/audit/keys, SSE banner, RTL tests; finish `sdk-python/` polling · DSA 2 (timed set) · Docs 1: SECURITY.md |
| **Fri (5 h)** | Project 3: AWS deploy in a day (start); `rollout-sim` table into TESTING.md · OA sim #6 · DSA reviews + Java rep (207) |
| **Sat (7 h)** | Project 4: finish deploy, smoke test (Java + Python clients), benchmarks, docs set, `v1.0` · Track B deep-dive rehearsal 2 h · résumé-defense gaps 1 h |
| **Sun (2–3 h)** | End-of-week test · reviews · trackers · plan W24 · rest |

## 13. End-of-week test

1. Draw the propagation path and name the guarantee of each layer (pub/sub, SSE, poll, TTL).
2. Implement single-flight from memory (≤ 15 lines) and explain the cross-instance extension.
3. Name five OWASP API Top 10 items and how FlagForge addresses each.
4. State the measured p99 evaluation latency and propagation latency, with load and environment.
5. Timed OA set (Python): two Mediums in 50 min from the pool above.
6. Given a rules payload on paper, find the unreachable rule and the overlap by hand, then say how the validator detects each.

Pass: 5/6.

## 14. Mastery checklist

- [ ] I can implement cache invalidation across instances with pub/sub and explain its at-most-once nature
- [ ] I can protect a cache from stampedes and show the measurement
- [ ] I can build a forms-heavy React admin with optimistic-concurrency handling and tests
- [ ] I can run an OWASP API Top 10 review on my own code
- [ ] I can deploy a Spring Boot + React + Redis + Postgres system to AWS in a day, with alarms and teardown
- [ ] I can deliver the FlagForge deep-dive in 12 minutes and defend every résumé technology, including Python via the project tooling
- [ ] I can write a small Python linter with parametrized tests and CI exit codes

## 15. Expected deliverables

- FlagForge `v1.0`; deployed; docs set; `docs/PERFORMANCE.md` with three measured tables; three Python components tested and documented.
- Trackers: project (FlagForge phase closed; hours vs 100–120 target), DSA (6 timed in Python + reviews + Java rep), interview (OA #6 split, Track A mock, Track B deep-dive self-score, résumé-defense matrix complete incl. Python), technology, weekly progress.

## 16. If behind / stretch

**Behind:** dashboard without the audit tab and SDK-key screen (API still works); keep propagation, stampede protection, deploy and docs — they are the engineering story. The validator can shrink to errors + unreachable-rule detection (skip overlap). If AWS slips, deploy in W24 and say so in the README.

**Stretch (ADVANCED tier, only after `v1.0`):** segments (reusable rule groups), scheduled rollouts (percentage ramps over time — reuse LedgerX's scheduling), TypeScript SDK from the conformance vectors.
