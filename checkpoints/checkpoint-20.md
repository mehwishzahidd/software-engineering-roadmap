# Checkpoint 20 — ForgeCI shipped; junior system design

> **Gate question:** Is ForgeCI shipped with failure recovery? Can I design and explain a
> system at junior level?

| | |
|---|---|
| **When** | Sunday of Week 20 (Part A on Saturday morning as a real OA sitting, Part C Saturday afternoon, Parts B/D/E + scoring on Sunday) |
| **Time** | ≈ 4.5 h: A 90 min + 15 min · B 45 min · C 75 min · D 40 min · E 25 min · scoring 10 min |
| **Covers** | Weeks 17–20: failure taxonomy (app vs infra), heartbeats/leases, Redis semaphores, graceful shutdown; Testcontainers Redis+Postgres, chaos-style tests, multi-worker Compose, CI/CD image publishing; DAGs/topological scheduling, Docker-socket security, multi-service AWS deploy (SQS considered); configuration versioning, immutable snapshots, multi-tenant modelling; **system design fundamentals**; Greedy, 2-D DP, Advanced Graphs, Bit Manipulation; ForgeCI Python tooling |
| **DSA patterns in scope** | All 23 patterns ([`../03-dsa/README.md`](../03-dsa/README.md)); mixed/timed from here on |
| **Projects under review** | [ForgeCI](../18-projects/forgeci/README.md) **v1.0 (Strong Résumé Version, M5) + M6 Advanced if reached**; [FlagForge](../18-projects/flagforge/README.md) M1 |
| **Rules** | [Checkpoint rules](./README.md#rules) |

Passing here is the entry condition for **Junior-role-ready** in
[`../JOB_READINESS.md`](../JOB_READINESS.md): three shipped projects including ForgeCI, weekly
mocks ≥ 3/4, OA sims passing.

---

## Part A — OA-style sitting (Track A · Python · 90 min) + Java rep (15 min)

### A1 — OA simulation (90 min)

Run this as **OA simulation #4** per the protocol in [`../OA_PREP.md`](../OA_PREP.md#6-oa-simulation-protocol-70120-minutes):
fresh directory, `BufferedReader`-style stdin discipline translated to Python (`sys.stdin`), no
IDE autocomplete, no cheatsheet, visible timer, phone away.

**Selection rules** (pick the night before, do not look at them):

- Problem 1 — **Easy/Medium warm-up** (≤ 20 min): Arrays/Hashing/Two Pointers, `Not Started`.
- Problem 2 — **Medium** (≤ 30 min): Graph, Heap or Intervals.
- Problem 3 — **Medium/Hard** (≤ 40 min): 2-D DP, Greedy or Advanced Graph (Dijkstra).
- Choose from LeetCode "problems by tag" sorted by frequency, or NeetCode 150/250 leftovers. Log
  every swap.

**Format:** each solution reads input from stdin and prints output (OA style), plus your own
`assert`-based examples. Score with hidden-test thinking: after the bell, write down three edge
cases per problem and test them; count a problem as passed only if they pass.

**Scoring (feeds DSA, Python)**

| Result | Score |
|---|---:|
| 3/3 pass incl. own edge cases, within budget | 4 |
| 2/3 pass + third has correct approach/partial; or 3/3 with one over budget | 3 |
| 2/3 pass | 2 |
| 1/3 pass | 1 |
| 0 | 0 |

Also record: minutes per problem, where time was lost (reading / approach / bugs / I/O),
Python idiom misses.

### A2 — Java rep (15 min)

One already-solved *Heap* or *Advanced Graph* problem in Java: `PriorityQueue<long[]>` with a
comparator for Dijkstra, or a `TreeMap` for an interval/ordered-set problem. Compiles, passes.

---

## Part B — Knowledge questions (45 min · 26 questions)

Write first, then open. 1 point each, 0.5 partial. Max 26.

### Python for interviews (Q1–Q4)

**Q1.** Dijkstra in Python: which structure for the frontier, why do you skip stale entries, and
what is the complexity?
<details><summary>Answer</summary>
`heapq` of `(dist, node)`; when popping, `if d > dist[node]: continue` skips outdated entries
(lazy deletion, since `heapq` has no decrease-key). O((V + E) log V) with an adjacency list.
</details>

**Q2.** Read all of stdin fast in Python for an OA with 10⁵ lines; what is the trap with
`input()` in a loop?
<details><summary>Answer</summary>
`data = sys.stdin.read().split()` then index through it (or `sys.stdin.buffer.read()` for
bytes). `input()` per line is slow and strips only the newline; also `print` in a loop should be
replaced by `sys.stdout.write("\n".join(out))`.
</details>

**Q3.** 2-D DP grid `dp = [[0] * (n + 1) for _ in range(m + 1)]`: memory in terms of m, n; how do
you reduce to O(n) and what is the subtle ordering bug when you do?
<details><summary>Answer</summary>
O(m·n). Keep one (or two) rows; when a state depends on `dp[i-1][j-1]` you must iterate `j`
in the direction that does not overwrite a value you still need (or keep `prev` explicitly).
</details>

**Q4.** Why is `str.join` with a generator fine but `sum(list_of_lists, [])` O(n²)?
<details><summary>Answer</summary>
`sum` with lists creates a new list at every addition (copying). Use
`list(itertools.chain.from_iterable(lists))` or a comprehension. `join` computes the total length
first, then builds once.
</details>

### Failure taxonomy, recovery, limits (Q5–Q11)

**Q5.** Define "application failure" vs "infrastructure failure" in ForgeCI and state the retry
rule for each with justification.
<details><summary>Answer</summary>
App failure: the user's steps ran and exited non-zero — deterministic, retrying wastes resources
and hides the red build → no retry. Infra failure: container failed to start, image pull error,
worker lost, Docker daemon error → transient → retry with exponential backoff, max N, then mark
`INFRA_FAILED` with the reason.
</details>

**Q6.** Heartbeat + lease: what values did you choose (heartbeat interval, lease TTL, reaper
period) and what is the relationship that must hold?
<details><summary>Answer</summary>
(Your numbers) e.g. heartbeat 5 s, lease 30 s, reaper every 10 s. Lease TTL ≫ heartbeat interval
× (1 + tolerated missed beats); reaper period ≤ lease TTL so orphaned jobs are found within one
lease. Too-short leases cause false orphaning under GC pauses; too-long delays recovery.
</details>

**Q7.** What is the "duplicate execution" risk with lease-based recovery, and how does the
worker prevent it?
<details><summary>Answer</summary>
A worker paused (GC/network) past its lease is still running the job when the reaper re-queues
it → two executions. Prevent: fence with a lease token/epoch persisted on the job; the old worker's
writes (status, logs) are rejected if its token is stale; the old worker checks the token before
publishing results and kills its container if it lost the lease.
</details>

**Q8.** Per-project concurrency limit in Redis: sketch the acquire/release and the failure
mode you must handle.
<details><summary>Answer</summary>
`INCR project:{id}:running` → if > limit, `DECR` and back off (or Lua script for atomic
check-and-increment). Release with `DECR` in `finally`. Failure mode: worker dies without
releasing → counter stuck; fix with lease keys per slot (`SET slot:{job} EX ttl`) counted via a
key pattern, or periodic reconciliation against DB `RUNNING` jobs.
</details>

**Q9.** Graceful shutdown of a worker: signal, steps, and what happens to a job mid-run.
<details><summary>Answer</summary>
`SIGTERM` → stop taking new jobs → either finish the current job within a grace period or mark
it for re-queue (release lease) and kill its container → flush logs → exit. Docker/Compose
`stop_grace_period` and Spring's `server.shutdown=graceful` + `@PreDestroy` hooks implement it.
</details>

**Q10.** Cancel a *running* job: the sequence from HTTP request to container gone, and where a
race hides.
<details><summary>Answer</summary>
API sets `cancel_requested` (DB) and publishes `cancel:{jobId}`; the worker (subscribed, or
polling the flag between steps/on heartbeat) kills the container (`docker kill`), records
`CANCELLED`, releases the lease. Race: cancel arrives as the job finishes — the worker's
conditional status update `WHERE status = 'RUNNING'` decides which final state wins.
</details>

**Q11.** Timeouts: per-job timeout vs per-step, and why the container is killed rather than the
thread interrupted.
<details><summary>Answer</summary>
Per-job bounds total cost; per-step gives better diagnostics. The step runs in a separate OS
process inside the container; interrupting the Java thread only stops *reading* its output. Kill
the container (or the exec process) and then record `TIMED_OUT`.
</details>

### DAG, deployment, CI/CD (Q12–Q16)

**Q12.** Job dependencies via `needs:` — how you detect a cycle and how you schedule
fan-out/fan-in.
<details><summary>Answer</summary>
Kahn's algorithm: compute in-degrees; if the processed count < node count, a cycle exists →
reject the config. Schedule: enqueue all in-degree-0 jobs; when a job completes, decrement
dependents and enqueue those reaching 0 (fan-in waits for all). Fail-fast: on failure, mark
dependents `SKIPPED`.
</details>

**Q13.** Redis vs SQS for the job queue on AWS: three concrete trade-offs you weighed.
<details><summary>Answer</summary>
SQS: managed, visibility timeout ≈ lease built in, at-least-once, DLQ; but no pub/sub, per-request
latency, cost per million, harder local dev. Redis: sub-ms, pub/sub for logs already needed, one
dependency; but self-managed persistence/HA. For a single-host deploy Redis wins; at scale SQS +
a separate pub/sub would be the migration.
</details>

**Q14.** Your CI pipeline builds two images (api, worker). What is cached, what is tagged, and
how does the deploy know which version to pull?
<details><summary>Answer</summary>
Maven deps cached by `pom.xml` hash; Docker layers cached (buildx cache). Tags: `sha-<git sha>`
and `latest` (or semver on tags). Deploy writes the sha tag into the Compose `.env`/image
reference and runs `pull && up -d`; rollback = redeploy previous sha.
</details>

**Q15.** Docker socket on the worker EC2 host: what did you write in SECURITY.md about it?
<details><summary>Answer</summary>
Worker runs on a dedicated instance; socket not exposed over TCP; only registered repos can
trigger builds; images restricted to an allow-list or pinned digests; containers run with
`--cap-drop ALL`, no `--privileged`, memory/CPU limits, read-only root where possible, network
isolation; secrets never mounted; residual risk stated honestly.
</details>

**Q16.** CloudWatch: which metric/alarm did you set for ForgeCI and why that one?
<details><summary>Answer</summary>
(Yours) e.g. an alarm on queue depth or on `INFRA_FAILED` count per 5 min via a custom metric,
plus EC2 disk usage (containers/workspaces fill disks) and a log-based alarm on `ERROR`. Chosen
because they correspond to real failure exercises you ran.
</details>

### System design fundamentals (Q17–Q22)

**Q17.** Define latency vs throughput, and give ForgeCI's measured numbers for queue wait time
and jobs/min with the setup.
<details><summary>Answer</summary>
Latency: time per operation (p50/p95/p99); throughput: operations per unit time. Yours, with
worker count, instance type, job type and duration recorded — from
[`../18-projects/forgeci/docs-and-resume.md`](../18-projects/forgeci/docs-and-resume.md).
</details>

**Q18.** Vertical vs horizontal scaling for ForgeCI workers; what is the shared bottleneck when
you add workers?
<details><summary>Answer</summary>
Vertical: bigger host (more concurrent containers); horizontal: more worker hosts. Shared
bottlenecks: Redis (single-threaded but fine), Postgres log-chunk writes (batch them), the
per-project concurrency limit (by design), and the Docker daemon on each host.
</details>

**Q19.** Cache-aside vs write-through vs write-behind: which does FlowGrid use, which will
FlagForge use for environment snapshots, and why?
<details><summary>Answer</summary>
FlowGrid: cache-aside with invalidation on write. FlagForge: publish-time write-through of the
immutable snapshot (write DB, then set the cache/pub-sub) because the snapshot is versioned and
readers must never see a stale-but-plausible mix; stampede protection on miss.
</details>

**Q20.** What is idempotency at the system-design level and name the three places you have
implemented it.
<details><summary>Answer</summary>
Same request applied N times has the effect of once. FlowGrid orders (`Idempotency-Key`),
LedgerX transfers (key + fingerprint + stored response), ForgeCI webhooks (`X-GitHub-Delivery`
unique). Bonus: outbox consumers by event id.
</details>

**Q21.** How do you estimate storage for ForgeCI logs at 1 000 builds/day, 5 jobs each, 2 MB
logs per job, retained 30 days?
<details><summary>Answer</summary>
1 000 × 5 × 2 MB = 10 GB/day → 300 GB/30 days. Conclusion: chunk logs in Postgres short-term
(recent builds) and archive to S3 with lifecycle rules; or store all in S3 and keep only an
index. State assumptions out loud — the method matters more than the number.
</details>

**Q22.** In a junior design interview, what are the first five minutes for?
<details><summary>Answer</summary>
Clarify requirements (functional, then non-functional: scale, latency, consistency), define the
API and core entities, state assumptions and scope; only then draw components. See
[`../16-interview-prep/system-design-interview.md`](../16-interview-prep/system-design-interview.md).
</details>

### FlagForge M1, Java, SQL (Q23–Q26)

**Q23.** Flag configs as immutable versions: the table shape, how rollback works, and why
rollback creates a new version.
<details><summary>Answer</summary>
`flag_config_version(id, flag_id, environment_id, version_no, rules_json, created_by, created_at,
published_at)`, unique `(flag_id, environment_id, version_no)`. Rollback copies version k's rules
into version n+1 — history stays linear and auditable; "current" is a pointer, never edited data.
</details>

**Q24.** Multi-tenant modelling: how do you guarantee a user in org A can never read org B's
flags — at which layers?
<details><summary>Answer</summary>
Every tenant-owned row carries `org_id`; the auth principal carries the org; repositories filter
by it (or Postgres RLS); authorization checks resource ownership, not only role; tests assert
cross-tenant 404/403. Never trust ids from the client alone.
</details>

**Q25.** Java 21 `sealed` interfaces + `switch` pattern matching: how would you model rule
types (attribute, user, percentage, default) and why is it better than an enum + `if` chain?
<details><summary>Answer</summary>
`sealed interface Rule permits AttributeRule, UserRule, PercentageRule, DefaultRule` (records),
evaluated with an exhaustive `switch`; the compiler forces handling every case when a rule type
is added, and each record carries only its own fields.
</details>

**Q26.** Postgres `jsonb` for rules: one advantage, one cost, and the index you would add.
<details><summary>Answer</summary>
Advantage: flexible rule shapes without migrations per rule type. Cost: weaker constraints and
typing — validate in the app and with CHECK where possible. Index: GIN on the jsonb column only if
you query inside it; otherwise B-tree on the relational keys around it.
</details>

**Part B scoring:** ≥ 22/26 → 4 · 18–21 → 3 · 13–17 → 2 · 8–12 → 1 · < 8 → 0. Q1–4 Python;
Q5–11 Backend/Debugging; Q12–16 CS/ops; Q17–22 system design (Interview); Q23–26 Backend/Java/SQL.

---

## Part C — Practical task (Track B · 75 min)

Branch `cp20/rule-change`. Design, implement, evaluate and containerise **one FlagForge rule
change end to end**, on top of your M1 model — a preview of M2's evaluation engine at
checkpoint scale.

### C1 — Design (15 min, written)

Rule change: **add a `priority` field to rules and make evaluation "first match by ascending
priority, else default"**, where today rules have no explicit order. Write a half-page design
note using [`../18-projects/templates/design-doc.md`](../18-projects/templates/design-doc.md):
schema change (migration + backfill of existing versions), API change (admin API validates
unique priorities per config), evaluation contract (deterministic; ties impossible), versioning
impact (a new version is created — never edit a published one), audit entry, rollback path,
tests.

### C2 — Implement + evaluate (40 min)

- Flyway migration adding `priority` (backfill by current array order), validation on the admin
  API, and a minimal evaluation function `evaluate(config, context) -> Variation` that applies
  rules by priority with a deterministic percentage bucket
  (`hash(flagKey + ":" + userKey) mod 10000` — the small copy-paste snippet in
  [`../18-projects/flagforge/milestones.md`](../18-projects/flagforge/milestones.md) is allowed).
- Tests: unit tests for priority ordering, tie rejection at the API, default fallback, and one
  bucketing test proving that 10 000 synthetic users at 10% land within a tolerance band and
  that the same user is sticky across calls.
- One integration test through the admin API creating v2 from v1 with the new priority and
  asserting v1 is unchanged.

### C3 — Containerise and verify (20 min)

- `docker compose up --build` the FlagForge stack (api, postgres, redis); run the migration;
  create a flag with two rules via `curl`; hit the evaluation endpoint (or a temporary
  `/internal/evaluate` if the public endpoint is M2 work) for two users and show the
  deterministic result.
- Run the ForgeCI Python tools once more as a regression: `pytest tools/` in the ForgeCI repo,
  and generate one test repository with the generator to prove it still works after M6 changes
  (5 min).

**Scoring (feeds Backend, Java, SQL, Python, Interview/design)**

| Criteria | Points |
|---|---:|
| C1 design note complete: schema, API, contract, versioning, rollback, tests | 1.5 |
| C2 migration + validation correct; published versions never mutated | 1 |
| C2 evaluation deterministic; bucketing test with tolerance + stickiness | 1.5 |
| C2 integration test green | 0.5 |
| C3 containerised run demonstrated with `curl` | 1 |
| C3 ForgeCI Python tools regression green | 0.5 |
| Within 75 min | — (note overrun; > 90 min caps at 3) |

6/6 → 4 · 4.5–5.5 → 3 · 3–4 → 2 · 1.5–2.5 → 1 · < 1.5 → 0.

---

## Part D — Explain out loud (40 min, recorded)

**D1 — ForgeCI deep dive (15 min, no notes).** The Week 19 rehearsal, repeated for the record.
Structure per [`../16-interview-prep/project-deep-dive.md`](../16-interview-prep/project-deep-dive.md).
Must cover: why this project, the queue decision, execution isolation, live logs, failure
taxonomy and recovery, what you measured, what you would change. Then three probes from
[`../18-projects/forgeci/interview-questions.md`](../18-projects/forgeci/interview-questions.md).

**D2 — Junior system design (20 min, whiteboard/paper, recorded).** Pick one **unseen** prompt
from [`../15-system-design/junior-design-problems.md`](../15-system-design/junior-design-problems.md)
(not a project you built). Run the method from
[`../16-interview-prep/system-design-interview.md`](../16-interview-prep/system-design-interview.md):
requirements (5 min) → API + data model (5 min) → components + data flow (5 min) → one deep
dive: failure mode or scaling step (5 min). Score with that file's rubric; ≥ 3/4 is the bar.

**D3 — Résumé defense (5 min):** CI/CD, AWS and Docker from
[`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md) with ForgeCI examples.

Score each 0–4. D1 and D2 count double.

---

## Part E — Project review

### E1 — ForgeCI vs Strong Résumé Version (v1.0) [+ Advanced M6]

Check [`../18-projects/forgeci/README.md`](../18-projects/forgeci/README.md) and M4–M6 in
[`../18-projects/forgeci/milestones.md`](../18-projects/forgeci/milestones.md).

**M4 — timeouts, cancellation, retries, limits, health**
- [ ] Per-job timeout kills the container; `TIMED_OUT` recorded with logs intact
- [ ] Cancel queued (removed) and running (flag + kill); idempotent; race documented and tested
- [ ] Retry policy: app failure → no retry; infra failure → backoff, max N; reasons persisted
- [ ] Worker heartbeats; orphaned-job recovery via lease expiry; fencing against duplicate execution
- [ ] Per-project concurrency limit; graceful shutdown on `SIGTERM`

**M5 — failure recovery, tests, full stack, benchmarks (v1.0)**
- [ ] Failure-engineering suite from [`../18-projects/forgeci/failure-engineering.md`](../18-projects/forgeci/failure-engineering.md) done and written up (worker killed mid-job, Redis restart, Postgres restart, Docker daemon down, duplicate delivery, malicious config)
- [ ] Integration tests (Testcontainers Postgres + Redis); concurrency tests (N workers, M jobs, each job executed exactly once)
- [ ] Compose: api, N workers, postgres, redis, ui; CI/CD publishing both images
- [ ] Queue wait time and jobs/min measured with methodology ([`../18-projects/templates/benchmark-report.md`](../18-projects/templates/benchmark-report.md))
- [ ] Docs set complete; `v1.0` tagged

**Python component (`tools/`, M5)**
- [ ] Test-repository generator (`.forgeci.yml` variants: passing, failing, slow, timeout, bad config), README, types, `pytest`
- [ ] Worker/load simulator (signed webhooks at rate; queue wait + completion measured) used by the benchmark
- [ ] Build-result/log analysis tool reporting failure-taxonomy stats; referenced in PERFORMANCE.md/TESTING.md

**M6 — Advanced (only if v1.0 was tagged first)**
- [ ] `needs:` DAG with cycle detection, topological scheduling, fan-out/fan-in, fail-fast
- [ ] AWS deploy (api + worker EC2 with Docker, RDS, Redis), SECURITY.md on the Docker socket, SQS considered in an ADR
- [ ] If not reached: explicitly recorded as dropped per ROADMAP §13 rule 4 — not "in progress forever"

**Hours:** logged ≈ 170–200 h.

### E2 — FlagForge M1 vs milestones

- [ ] Orgs, projects, environments, flags; per-environment configs as immutable versions; rollback = new version
- [ ] Rules model (attribute, user, percentage, priority, default) persisted; audit history
- [ ] Auth/authz with org roles; cross-tenant access tested
- [ ] Admin API with OpenAPI; SDK keys per environment (hashed at rest)
- [ ] CI green; ADR for versioning model

**Scoring (feeds Projects):** ForgeCI v1.0 complete + Python tools + FlagForge M1 → 4 · v1.0
complete, ≤ 2 boxes missing across E → 3 · v1.0 tagged but ≥ 3 missing → 2 · v1.0 not tagged
→ 1 · M4 incomplete → 0.

---

## Scoring table

| Area | Evidence | Critical? | PASS threshold | My score |
|---|---|:---:|:---:|:---:|
| Python (coding) | A1 idiom/speed, B Q1–Q4, C3 tools regression | yes | 3 | |
| Java (engineering) | A2, B Q25, C2 code quality, E | yes | 3 | |
| DSA | A1 (OA sim #4 result) | yes | 3 | |
| SQL | B Q26, C2 migration/backfill | no | 3 | |
| Backend | B Q5–Q11, Q23–Q24; C; E | yes | 3 | |
| Frontend | E1 UI status/cancel controls; ForgeCI UI polish | no | 2 | |
| Git | PR/ADR hygiene across two repos; release tagging | no | 3 | |
| Debugging | B Q7, Q10; failure-engineering write-ups in E1 | yes | 3 | |
| CS fundamentals | B Q12–Q16; system-design fundamentals Q17–Q21 | yes | 3 | |
| Projects | E1 + E2 | yes | 3 | |
| Interview readiness | D1, D2, D3 + last 3 weekly mocks ≥ 3/4 ([`../trackers/interview-tracker.md`](../trackers/interview-tracker.md)) | yes | 3 | |

## Decision

| Condition | Decision |
|---|---|
| All critical ≥ threshold, ≤ 1 non-critical below | **PASS** → *Junior-role-ready* ([`../JOB_READINESS.md`](../JOB_READINESS.md)) |
| All critical met, ≥ 2 non-critical below | **PASS WITH REMEDIATION** |
| Any critical below | **FAIL** — remediate W21–22 |
| ForgeCI v1.0 not tagged | **FAIL**; M6 dropped; finish v1.0 in W21 Sat blocks before FlagForge M2 stretch items |
| Phase > 1 week behind | ForgeCI Advanced Version (M6) dropped; FlagForge Advanced not attempted |

---

## If you fail — remediation (Weeks 21–22 review blocks, ≈ 8 h)

| Area | Do this |
|---|---|
| **Python (coding)** | OA post-mortem: rewrite the failed problem cleanly from a blank file the next day and again at Day 7; stdin/stdout template drilled until automatic ([`../OA_PREP.md`](../OA_PREP.md) §4 in Python). Two extra timed Mediums per week (25 min each). |
| **Java (engineering)** | Java reps in W21–22: Dijkstra with `PriorityQueue`, topo sort; sealed-interface rule model refactor in FlagForge (that is M2 work anyway). |
| **DSA** | W21 mixed review focused on the weakest pattern from A1; Day-30 reviews cleared; OA sim #5 (W22) is the retest. |
| **SQL** | Migration + backfill drill: write two more versioned-config migrations with rollback scripts (1 h); [`../04-sql-databases/04-schema-design.md`](../04-sql-databases/04-schema-design.md). |
| **Backend** | Re-implement lease + heartbeat + reaper in a scratch module with a test that kills a fake worker (2 h); [`../15-system-design/scalability.md`](../15-system-design/scalability.md) queue section. |
| **Frontend** | Add cancel/retry controls with optimistic UI to ForgeCI build detail (1.5 h); [`../08-react/04-api-integration-auth.md`](../08-react/04-api-integration-auth.md). |
| **Git** | Release-tag discipline: `v1.0` with release notes from closed issues; `git log --oneline v0.9..v1.0` as the changelog source. |
| **Debugging** | Repeat two failure exercises with the debugger attached to the *worker*; [`../21-debugging-code-reading/drills.md`](../21-debugging-code-reading/drills.md) one drill. |
| **CS fundamentals** | [`../15-system-design/fundamentals.md`](../15-system-design/fundamentals.md) + [`../15-system-design/caching.md`](../15-system-design/caching.md); 15 flashcards from [`../14-cs-fundamentals/interview-questions.md`](../14-cs-fundamentals/interview-questions.md). |
| **Projects** | Finish v1.0 boxes before any FlagForge polish; Python tools are part of v1.0, not optional. |
| **Interview** | One extra junior design problem per week on paper, recorded, until ≥ 3 ([`../15-system-design/junior-design-problems.md`](../15-system-design/junior-design-problems.md)); the W22 system-design mock is the retest. |

Second consecutive failure in an area → one-week feature freeze in that area.

---

## Results log

```
CP-20 — date: ____________   total time: ____ h

PART A1 (OA sim #4)  p1 ______ __min pass? __   p2 ______ __min pass? __   p3 ______ __min pass? __
        time lost to: reading __ approach __ bugs __ I/O __     score __/4
PART A2 java rep: ______ __min  points __/2
PART B  Py __/4 Recovery __/7 Ops/DAG __/5 SysDesign __/6 FlagForge/Java/SQL __/4  total __/26  score __/4
PART C  C1 __/1.5  C2 __/3  C3 __/1.5  time __ min   score __/4
PART D  D1 deep dive __/4  probes avg __  D2 design (problem: ________) __/4  D3 __/4   score __/4
PART E  M4 __/5 M5 __/5 Python __/3 M6 __/3 (reached? __ / dropped? __)  FlagForge M1 __/5  hours __  score __/4
MOCKS   last three weekly mock scores: __ __ __     OA sims so far: #1 __ #2 __ #3 __ #4 __

AREA SCORES  Python __ Java __ DSA __ SQL __ Backend __ Frontend __ Git __ Debug __ CS __ Projects __ Interview __
DECISION     PASS / PASS WITH REMEDIATION / FAIL     Junior-role-ready? __
REMEDIATION  areas: ____________  hours W21: __  W22: __
NOTES
```

Copy to [`./README.md`](./README.md#summary-log), [`../trackers/weekly-progress.md`](../trackers/weekly-progress.md),
[`../JOB_READINESS.md`](../JOB_READINESS.md) scorecard.
