# Week 23 — FlagForge M4: dashboard, propagation, deploy (v1.0)

[← Week 22](../week-22/) · [Roadmap](../../ROADMAP.md) · [Week 24 →](../week-24/)

**Phase 4 · FlagForge** (weeks 20–23) · Milestone **M4** · Target: **Strong Résumé Version, tagged `v1.0`**

| Block | Hours | Focus |
|---|---:|---|
| Project | 28 | React admin dashboard, propagation (publish → Redis pub/sub → SSE → SDKs), stampede protection, Docker/AWS/CI, docs, benchmarks, failure exercises |
| Learning | 5 | Pub/sub invalidation, cache-stampede protection, security review, AWS deploy repetition |
| DSA | 7 | Timed mixed — **6 new** + reviews |
| Interview / review | 5 | OA simulation #6, **FlagForge deep-dive rehearsal**, résumé defense complete for all technologies |

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

### 4.5 Deploy in a day

Order: budget alarm → IAM role → RDS → EC2 (Compose: api, redis, ui via nginx) → CloudWatch logs + 5xx alarm → HTTPS (or IP allow-list) → smoke test (publish from the dashboard, sample app on your laptop sees the change) → teardown script tested. Write `docs/DEPLOYMENT.md` as you go, and time yourself: it is a résumé-defense answer ("I have deployed four Spring Boot systems to AWS; the fourth took a day").

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

## 7. DSA — Timed mixed (6 new)

Selection rule as in Week 20, but **every problem is timed with the OA clock**: Easy 12 min, Medium 25 min, Hard 40 min, no hints before time is up. Log time-to-first-correct-submit. Two-problem "sets" (50 min for two Mediums) simulate OA pacing — do two such sets plus two singles.

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
- [ ] Docs: README (what/why/run/demo GIF placeholder), ARCHITECTURE, API, DATABASE, TESTING, SECURITY, DESIGN_DECISIONS (ADRs), PERFORMANCE
- [ ] Tag `v1.0`; release notes

### Acceptance summary

- Publish in the dashboard → sample app (via SDK streaming) sees the change; measured p95 < 1 s locally (record the real number).
- Stampede test shows collapsed DB load.
- Dashboard covers flags, rules, versions, rollback, audit, keys; rules editor tested.
- Deployed on AWS with alarms and a tested teardown; CI builds + publishes + (manual) deploys.
- All docs present; `v1.0` tagged; README honest about scope (segments, scheduled rollouts, TS SDK are ADVANCED/not done).

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
| `DeploySmokeTest` (manual, documented) | AWS stack | dashboard publish | laptop sample app logs new value |

### Failure scenarios to run

1. Redis restart mid-traffic (subscriber reconnect; snapshot rebuild; stampede protection in action).
2. Postgres restart while cache warm — evaluations unaffected; publish fails cleanly with 503 ProblemDetail.
3. SSE connections × 500 from a load script — memory and thread usage; heartbeat keeps proxies open.
4. Publish storm (20 publishes/s) — SDK does not fetch 20 times (coalesce nudges by version).
5. Deployment rollback: redeploy previous image sha; Flyway compatibility (no destructive migration in `v1.0`).

### GitHub expectations

- Milestone M4 closed; release `v1.0`; full docs set; screenshots folder with at least the flag detail and versions screens; `docs/PERFORMANCE.md` with three measured tables.

## 9. Git activity

- Branches: `feat/m4-propagation`, `feat/m4-stampede`, `feat/m4-dashboard-*` (several small PRs), `sec/m4-review`, `ops/m4-aws`, `docs/m4-docs-set`.
- Tag `v1.0`; the SDK keeps its own `sdk-0.1.x` line.

## 10. Interview preparation

- **FlagForge deep-dive rehearsal** (Sat, 2 h): [`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md) + [`18-projects/flagforge/interview-questions.md`](../../18-projects/flagforge/interview-questions.md). Record; 12 minutes; then 10 cold questions. Compare with the ForgeCI recording from W19 — is the structure tighter?
- **OA simulation #6** (Fri, 90–120 min): [`OA_PREP.md`](../../OA_PREP.md) with a repo-modification drill from [`21-debugging-code-reading/drills.md`](../../21-debugging-code-reading/drills.md).
- **Weekly coding mock** (Tue): [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md).
- **Résumé defense — completion gate:** by Sunday every technology in [`RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md)'s matrix (Java, Spring Boot, PostgreSQL, MySQL, REST, React, TypeScript, JavaScript, Docker, AWS, Redis, Git, GitHub, Maven, JUnit, CI/CD, Linux) has been drilled with [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md) and has a "which project proves it" line. This week's remaining gaps are typically MySQL ([`17-resume-tech-defense/mysql.md`](../../17-resume-tech-defense/mysql.md) — the Postgres diff), Linux ([`17-resume-tech-defense/linux.md`](../../17-resume-tech-defense/linux.md)), TypeScript ([`17-resume-tech-defense/typescript.md`](../../17-resume-tech-defense/typescript.md)), GitHub ([`17-resume-tech-defense/github.md`](../../17-resume-tech-defense/github.md)).
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
| **Tue (8 h)** | Project 5: single-flight + Redis lock, before/after measurement · DSA 2 + reviews · Mock 1 h |
| **Wed (8 h)** | Learning 2: OWASP API Top 10 applied · Project 4.5: dashboard — flags, detail, rules editor · DSA 1.5 |
| **Thu (8 h)** | Project 5: dashboard — versions/rollback/audit/keys, SSE banner, RTL tests · DSA 2 (timed set) · Docs 1: SECURITY.md |
| **Fri (5 h)** | Project 3: AWS deploy in a day (start) · OA sim #6 · DSA reviews |
| **Sat (7 h)** | Project 4: finish deploy, smoke test, benchmarks, docs set, `v1.0` · Deep-dive rehearsal 2 h · résumé-defense gaps 1 h |
| **Sun (2–3 h)** | End-of-week test · reviews · trackers · plan W24 · rest |

## 13. End-of-week test

1. Draw the propagation path and name the guarantee of each layer (pub/sub, SSE, poll, TTL).
2. Implement single-flight from memory (≤ 15 lines) and explain the cross-instance extension.
3. Name five OWASP API Top 10 items and how FlagForge addresses each.
4. State the measured p99 evaluation latency and propagation latency, with load and environment.
5. Timed OA set: two Mediums in 50 min from the pool above.

Pass: 4/5.

## 14. Mastery checklist

- [ ] I can implement cache invalidation across instances with pub/sub and explain its at-most-once nature
- [ ] I can protect a cache from stampedes and show the measurement
- [ ] I can build a forms-heavy React admin with optimistic-concurrency handling and tests
- [ ] I can run an OWASP API Top 10 review on my own code
- [ ] I can deploy a Spring Boot + React + Redis + Postgres system to AWS in a day, with alarms and teardown
- [ ] I can deliver the FlagForge deep-dive in 12 minutes and defend every résumé technology

## 15. Expected deliverables

- FlagForge `v1.0`; deployed; docs set; `docs/PERFORMANCE.md` with three measured tables.
- Trackers: project (FlagForge phase closed; hours vs 100–120 target), DSA (6 timed + reviews), interview (OA #6, mock, deep-dive self-score, résumé-defense matrix complete), technology, weekly progress.

## 16. If behind / stretch

**Behind:** dashboard without the audit tab and SDK-key screen (API still works); keep propagation, stampede protection, deploy and docs — they are the engineering story. If AWS slips, deploy in W24 and say so in the README.

**Stretch (ADVANCED tier, only after `v1.0`):** segments (reusable rule groups), scheduled rollouts (percentage ramps over time — reuse LedgerX's scheduling), TypeScript SDK from the conformance vectors.
