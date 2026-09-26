# Checkpoint 24 — Would I pass a junior loop today?

> **Gate question:** Would I pass an internship/junior loop today, including a 15-minute
> project deep-dive without notes?

| | |
|---|---|
| **When** | Sunday of Week 24 — but Part A (the full mock loop) is a **whole Saturday** run like a real onsite |
| **Time** | ≈ 6 h across the weekend: A 3 h (loop) · B 40 min · C 75 min · D inside A · E 30 min · scoring 15 min |
| **Covers** | Everything. Weeks 21–24 specifically: hashing for bucketing, rule engines, Redis snapshot caching, hot-path latency; client-library/SDK design, semver, polling vs streaming, resilience; pub/sub invalidation, stampede protection; security review (OWASP API Top 10), refactoring; mixed/timed DSA; FlagForge Python tools + Python SDK |
| **DSA patterns in scope** | All; timed mixed ([`../OA_PREP.md`](../OA_PREP.md)) |
| **Projects under review** | All four at their final tiers: FlowGrid v1.0 (+Advanced?), LedgerX v1.0 (+Advanced?), ForgeCI v1.0 (+M6?), [FlagForge](../18-projects/flagforge/README.md) v1.0 |
| **Rules** | [Checkpoint rules](./README.md#rules). Part A is run by another person or an AI interviewer with strict rules ([`../16-interview-prep/mock-interviews.md`](../16-interview-prep/mock-interviews.md)) — not self-administered if at all avoidable |

This checkpoint is the **Interview-ready** gate in [`../JOB_READINESS.md`](../JOB_READINESS.md).
Its output is not only a score: it is the list of what Weeks 25–26 must fix before applications
go out at volume.

---

## Part A — Full mock loop (Track A + Track B · ≈ 3 h, Saturday)

Run as a real onsite. Interviewer(s): a peer, a Pramp-style partner, or an AI interviewer with
the strict-rules prompt in [`../16-interview-prep/mock-interviews.md`](../16-interview-prep/mock-interviews.md).
Breaks of 10 min between rounds. Camera on, shared editor, no notes, no cheatsheet.

| Round | Track | Length | Content | Scored with |
|---|---|---:|---|---|
| **A1 · Recruiter screen** | B | 20 min | "Tell me about yourself", the gap, why this role, availability, salary posture — [`../16-interview-prep/recruiter-screen.md`](../16-interview-prep/recruiter-screen.md) | 0–4 |
| **A2 · Coding I** | A (Python) | 45 min | One Medium, unseen, interviewer-chosen from any pattern; think aloud; follow-up variation in the last 10 min | [`../INTERVIEW_CHECKLIST.md`](../INTERVIEW_CHECKLIST.md) rubric 0–4 |
| **A3 · Coding II** | A (Python) | 45 min | Two problems: one Easy (≤ 12 min) then one Medium; or one Medium/Hard | 0–4 |
| **A4 · Project deep dive** | B | 30 min | **15 min uninterrupted** on the project the interviewer picks (they choose, you do not), then 15 min of probes from that project's `interview-questions.md` | [`../16-interview-prep/project-deep-dive.md`](../16-interview-prep/project-deep-dive.md) rubric 0–4 |
| **A5 · Junior system design** | B | 30 min | Unseen prompt from [`../15-system-design/junior-design-problems.md`](../15-system-design/junior-design-problems.md) | [`../16-interview-prep/system-design-interview.md`](../16-interview-prep/system-design-interview.md) 0–4 |
| **A6 · Behavioral + résumé grill** | B | 30 min | Four STAR stories from the story bank + 6 rapid résumé-tech probes (Java, Spring, SQL, React, Docker, AWS) — [`../16-interview-prep/behavioral.md`](../16-interview-prep/behavioral.md), [`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md) | 0–4 |

**Java rep inside the loop:** in A3, after the Easy, the interviewer asks you to **re-implement
the Easy in Java** in ≤ 10 min (collections, generics, a `record` where sensible). Feeds the
Java (engineering) score. This mirrors real loops where a Java-shop interviewer asks "can you do
that in Java?" after a Python solution.

**Selection rules for the interviewer:** problems must be `Not Started` in your tracker (hand
them the list of solved problem numbers, not the tracker itself). Design prompt must not be one
of the four projects. Deep-dive project is chosen by the interviewer at the start of A4.

**Scoring (feeds DSA, Python, Java, Interview readiness, Projects)**

| Round | Score | Weight |
|---|:---:|:---:|
| A1 recruiter | | 1 |
| A2 coding I | | 2 |
| A3 coding II (incl. Java rep noted separately) | | 2 |
| A4 deep dive | | 2 |
| A5 design | | 1 |
| A6 behavioral/résumé | | 1 |

Weighted average → Interview readiness. A2/A3 average → DSA and Python. Java rep → Java points
(2 = clean and in time, 1 = late, 0 = fail). A4 ≥ 3 is a hard requirement for the "15-minute
deep dive without notes" gate.

---

## Part B — Knowledge questions (40 min · 24 questions, rapid)

Write first, then open. 1 point each, 0.5 partial. Max 24. Answers should be short — this is
the "rapid-fire phone screen" register.

### Python for interviews (Q1–Q4)

**Q1.** Explain why `hash("flag:user")` is *not* usable for FlagForge's bucketing across
processes and languages, and what you used instead.
<details><summary>Answer</summary>
Python's `hash()` for `str` is salted per process (`PYTHONHASHSEED`) → different buckets per
run and not reproducible in Java. Use a stable hash (e.g. MurmurHash3 or SHA-1/MD5 truncated)
over `flagKey:userKey`, identical in the Java SDK, Python SDK and server; contract-tested across
SDKs.
</details>

**Q2.** What is a generator; when did it save memory in your Python tools?
<details><summary>Answer</summary>
A lazily evaluated iterator (`yield`), O(1) memory per element. E.g. streaming 200 000 ledger
rows from `psycopg` cursor to the verifier, or generating synthetic orders on the fly for the load
harness instead of building a list first.
</details>

**Q3.** `dataclass(frozen=True)` vs a plain class vs `NamedTuple` for an evaluation `Context`.
<details><summary>Answer</summary>
Frozen dataclass: immutable, hashable, `__eq__`/`__repr__` free, keyword construction, type
hints. `NamedTuple`: also immutable + indexable, lighter. Plain class: only if you need custom
behaviour. Choose frozen dataclass for clarity.
</details>

**Q4.** Complexity of `sorted` on nearly-sorted input, of `heapq.heapify`, and of `heappush`
n times — and which builds a heap faster?
<details><summary>Answer</summary>
Timsort is O(n) on nearly-sorted data (adaptive). `heapify` is O(n); n × `heappush` is
O(n log n) — `heapify` wins for a bulk build.
</details>

### FlagForge, caching, SDK (Q5–Q11)

**Q5.** Deterministic percentage rollout: the formula, why the flag key is part of the hash, and
how you proved 10% ± tolerance and stickiness.
<details><summary>Answer</summary>
`bucket = stableHash(flagKey + ":" + userKey) mod 10000`; in rollout if `bucket < pct × 100`.
Including the flag key decorrelates rollouts across flags (the same 10% of users are not always
the guinea pigs). Proof: the Python simulator over N users checks the proportion within a tolerance
band and that re-evaluation yields the same result; contract test compares with server evaluation.
</details>

**Q6.** Cache stampede: the scenario in FlagForge and two protections.
<details><summary>Answer</summary>
Snapshot key expires/invalidated while thousands of SDK polls hit → all miss → DB overloaded.
Protections: single-flight (one loader per key, others wait — lock or `computeIfAbsent`),
stale-while-revalidate (serve old snapshot, refresh in background), jittered TTL, publish-time
warm (write-through on publish so misses are rare).
</details>

**Q7.** Polling with ETag vs SSE streaming for SDK updates: trade-offs and what you shipped.
<details><summary>Answer</summary>
Polling + `If-None-Match`: simple, proxy-friendly, bounded load, staleness ≤ interval. SSE:
near-instant propagation, long-lived connections, reconnect logic, needs fan-out via pub/sub.
Shipped polling first (M3), SSE as advanced; the SDK falls back to polling when the stream fails.
</details>

**Q8.** SDK resilience: define stale-if-error, offline mode, and default values, and the order
the SDK consults them.
<details><summary>Answer</summary>
Evaluate from the in-memory snapshot; if refresh fails, keep serving the last good snapshot
(stale-if-error) and log; offline mode never calls the network and uses bootstrap data or
defaults; if no snapshot exists, return the caller-supplied default. Never throw into the host
application's hot path; timeouts bounded.
</details>

**Q9.** What does semantic versioning promise for your SDK, and which change is a major bump:
adding a builder option, renaming `FlagClient.evaluate` → `eval`, changing the default polling
interval?
<details><summary>Answer</summary>
MAJOR = breaking API, MINOR = backwards-compatible features, PATCH = fixes. Rename = major;
new option = minor; changing a default = arguably minor but behaviourally visible — document it,
many teams treat it as major for SDKs.
</details>

**Q10.** p99 evaluation latency: what you measured, how (tool, warm-up, iterations, machine),
and why p99 not average.
<details><summary>Answer</summary>
(Yours) e.g. JMH microbenchmark for local evaluation and k6 for the server endpoint, warm-up
iterations, fixed rule set size, machine recorded. p99 exposes tail behaviour (GC, cache miss)
that averages hide; SDK callers care about worst-case on their request path.
</details>

**Q11.** OWASP API Top 10: name three you found in your own projects during the security pass
and the fixes.
<details><summary>Answer</summary>
Typical: Broken Object Level Authorization (org-scoped ids not checked → ownership checks +
tests), Unrestricted Resource Consumption (no page-size cap / no rate limit → bounds + Redis
token bucket), Security Misconfiguration (verbose errors, CORS `*`, actuator exposed → hardened
config), Broken Authentication (long-lived JWT, no rotation → short TTL + refresh).
</details>

### Cross-project engineering (Q12–Q18)

**Q12.** Compare the three idempotency implementations you built: what differs and which is the
most robust?
<details><summary>Answer</summary>
FlowGrid: key + hash + stored response, TTL. LedgerX: same plus explicit status (in-progress /
completed) and a unique constraint on the journal to survive crashes between steps. ForgeCI:
natural key (`X-GitHub-Delivery`) unique constraint, insert-first. LedgerX's is the most robust
(handles crash-mid-request); ForgeCI's is the simplest because the key is given by the caller.
</details>

**Q13.** Pessimistic locking (LedgerX) vs conditional update with row count (ForgeCI job
states) vs `@Version` (FlowGrid comparison): when each.
<details><summary>Answer</summary>
Pessimistic: multi-row invariants under contention (two accounts). Conditional update: single-row
state transitions (`WHERE status = 'RUNNING'`) — cheapest, no lock wait. Optimistic: low
contention, read-mostly entities, user-facing edits with retry/409.
</details>

**Q14.** Where did you use Redis in each project, and where did you decide *not* to?
<details><summary>Answer</summary>
FlowGrid: catalog cache-aside, low-stock cache. LedgerX: idempotency/rate limit only where
useful — not for balances (correctness lives in Postgres). ForgeCI: queue, pub/sub, concurrency
limits. FlagForge: snapshot cache, pub/sub propagation. Not used: as a source of truth anywhere.
</details>

**Q15.** Transactional outbox (LedgerX) vs Redis pub/sub (ForgeCI/FlagForge): why the
different delivery guarantees are acceptable in each.
<details><summary>Answer</summary>
Ledger events must not be lost (financial audit/consumers) → outbox, at-least-once. Log lines and
flag-change notifications are accelerators over a durable store (Postgres chunks; versioned
snapshots) — a missed message is recovered by replay/poll, so fire-and-forget is acceptable.
</details>

**Q16.** Describe your standard failure-engineering loop and one bug it found that tests had
not.
<details><summary>Answer</summary>
Reproduce → observe → inspect logs → diagnose → fix/design around → regression test
([`../18-projects/README.md`](../18-projects/README.md)). (Yours) e.g. Redis restart wiped the
in-flight processing list because persistence was off → enabled AOF and added lease reconciliation
against Postgres.
</details>

**Q17.** Refactoring you did in Week 24: what smell, what technique, how did tests protect you?
<details><summary>Answer</summary>
(Yours) e.g. a 400-line service split by extracting the allocation scorer (strategy) with
characterisation tests written first; behaviour pinned by existing integration tests.
</details>

**Q18.** The one design decision across the four projects you would reverse, and why.
<details><summary>Answer</summary>
(Yours — must be concrete, with the consequence you observed.) An honest answer here is worth
more than any correct-sounding one in interviews.
</details>

### CS fundamentals, rapid (Q19–Q24)

**Q19.** TCP vs UDP in one line each; which does HTTP/3 use?
<details><summary>Answer</summary>
TCP: ordered, reliable, connection-oriented. UDP: datagrams, no ordering/reliability. HTTP/3
runs over QUIC on UDP.
</details>

**Q20.** What is a deadlock's four conditions; which one does lock ordering remove?
<details><summary>Answer</summary>
Mutual exclusion, hold-and-wait, no preemption, circular wait. Ordering removes circular wait.
</details>

**Q21.** Index on `(status, created_at)` — does `WHERE created_at > now() - interval '1 day'`
use it?
<details><summary>Answer</summary>
Not efficiently (leftmost prefix is `status`); it may scan the index or table. Add
`(created_at)` or reorder if that query dominates.
</details>

**Q22.** JVM: what is the difference between heap and stack memory, and what error do you get
when each is exhausted?
<details><summary>Answer</summary>
Heap: objects, shared, GC-managed → `OutOfMemoryError`. Stack: per-thread frames/locals →
`StackOverflowError`.
</details>

**Q23.** Explain `git rebase --onto` in one sentence and when you used it.
<details><summary>Answer</summary>
Re-parent a range of commits onto a different base (`git rebase --onto main old-base feature`);
used to move a branch built on a stale/abandoned branch onto `main`.
</details>

**Q24.** What is the difference between authentication and authorization, and where does each
live in your Spring apps?
<details><summary>Answer</summary>
AuthN = who you are (JWT filter → `SecurityContext`); AuthZ = what you may do
(`SecurityFilterChain` matchers, `@PreAuthorize`, ownership checks in services).
</details>

**Part B scoring:** ≥ 20/24 → 4 · 16–19 → 3 · 12–15 → 2 · 8–11 → 1 · < 8 → 0. Q1–4 Python;
Q5–11 Backend/FlagForge; Q12–18 cross-project (Backend/Debugging/Interview); Q19–24 CS.

---

## Part C — Practical task (Track B · 75 min)

Two tasks, one clock. This mirrors the "existing codebase" section of OAs and the repository
task some companies send before an onsite.

### C1 — Debug the buggy library under time (40 min)

Fresh clone of [`../21-debugging-code-reading/exercises/buggy-library/`](../21-debugging-code-reading/exercises/buggy-library/)
(reset any earlier fixes: `git checkout -- .` or re-copy). Instructions in
[`../21-debugging-code-reading/README.md`](../21-debugging-code-reading/README.md).

- `mvn test` → record the failing test count.
- Fix as many planted bugs as possible in 40 min using the method in
  [`../21-debugging-code-reading/method.md`](../21-debugging-code-reading/method.md): read the
  failing test → locate → hypothesis → debugger/log → minimal fix → rerun.
- For each bug fixed, one line: symptom → root cause → fix. **Never** change a test to make it
  pass unless the test itself is provably wrong (state why).
- At the bell: failing count before/after, bugs fixed, and the one you could not find.

### C2 — Repository-modification task (35 min)

Pick **one** of the four projects by dice roll. Implement the change below from a ticket-style
description, as a PR with tests, in 35 min:

| Project | Ticket |
|---|---|
| FlowGrid | "Ops managers need to export the low-stock view as CSV: `GET /api/reports/low-stock.csv?warehouseId=` with the same filters as the JSON endpoint, streamed, `Content-Disposition` attachment, VIEWER+ allowed." |
| LedgerX | "Add `GET /api/accounts/{id}/statement?from=&to=` returning entries in the range with an opening and closing balance; must be consistent (single snapshot) and cursor-paginated." |
| ForgeCI | "Add a `retry` button semantics: `POST /api/jobs/{id}/retry` allowed only for terminal jobs, creates a new job attempt linked to the original, keeps logs of both; UI shows attempt number." |
| FlagForge | "Add per-environment `GET /api/flags/{key}/versions/{n}/diff/{m}` returning a rule-level diff between two versions; audited as a read; admin API only." |

Rules: start from `main`, branch, tests first if you can, CI green, PR description with a
one-paragraph design note. Partial at the bell is fine — the PR must honestly say what is
missing.

### C3 — Python components regression (inside the 75 min, ≤ 5 min)

`pytest` in each project's `tools/` (and FlagForge `sdk-python/`) — four commands, all green.
Any red is a finding for Week 25.

**Scoring (feeds Debugging, Java, Backend, Git, Python)**

| Criteria | Points |
|---|---:|
| C1: ≥ 70% of planted bugs fixed with correct root causes | 2 |
| C1: 40–69% fixed | 1 (instead of 2) |
| C1: no test tampering; method followed (notes show hypotheses) | 0.5 |
| C2: change works with tests, PR + CI green, honest description | 2 |
| C2: partial but correct direction, tests written | 1 (instead of 2) |
| C2: done within 35 min | 0.5 |
| C3: all Python components green | 1 |

6/6 → 4 · 4.5–5.5 → 3 · 3–4 → 2 · 1.5–2.5 → 1 · < 1.5 → 0.

---

## Part D — Explain out loud

Covered by Part A (A4 deep dive, A5 design, A6 behavioral). Additionally, **on Sunday**, record
without an interviewer:

- The **15-minute deep dive of a *different* project** than the one the interviewer chose on
  Saturday. Both must reach ≥ 3. Over Weeks 24–26 every one of the four projects must have a
  recorded ≥ 3 deep dive (log in [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md)).
- **Track A explain (5 min):** narrate A2's problem and the follow-up variation as you would in
  a debrief email to yourself — approach, complexity, what the interviewer's hint unlocked.

---

## Part E — Project review: all four at final tier

For each project, check the tier actually reached against its README scope tiers and the
Week 24 polish list in [`../ROADMAP.md`](../ROADMAP.md#5-week-by-week-master-table)
(Polish I: bugs, refactor, tests, security, docs).

**Per project** ([FlowGrid](../18-projects/flowgrid/README.md) · [LedgerX](../18-projects/ledgerx/README.md) · [ForgeCI](../18-projects/forgeci/README.md) · [FlagForge](../18-projects/flagforge/README.md))
- [ ] `v1.0` tagged; Strong Résumé Version criteria in its `milestones.md` all met
- [ ] Advanced Version: reached / explicitly dropped (recorded in the README, no half-built feature on `main`)
- [ ] Deployed and reachable (or intentionally torn down with a recorded demo + cost note per [`../12-aws/cost-safety.md`](../12-aws/cost-safety.md))
- [ ] CI green on `main`; no failing or skipped tests; test count and coverage noted
- [ ] Security pass done (OWASP API Top 10 checklist, secrets scan, dependency updates); SECURITY.md updated
- [ ] Docs: README, ARCHITECTURE, API, DATABASE (ERD), TESTING, DEPLOYMENT, SECURITY, DESIGN_DECISIONS, PERFORMANCE — draft complete; diagrams/screenshots scheduled for W25
- [ ] Failure-engineering write-ups in the repo
- [ ] Python component(s): tested, documented, referenced from TESTING.md/PERFORMANCE.md
- [ ] Benchmarks recorded with methodology ([`../18-projects/templates/benchmark-report.md`](../18-projects/templates/benchmark-report.md)); only these may become résumé bullets ([`../PROJECT_SELECTION.md`](../PROJECT_SELECTION.md))
- [ ] GitHub: issues/milestones closed, topics set, description, pinned

**FlagForge specifically (v1.0 = M4)**
- [ ] Evaluation engine + Redis snapshots + p99 measured; Java SDK with tests + contract tests; Python SDK/test client; rollout simulator; config linter; React dashboard with rules editor, versions, rollback, audit; propagation via pub/sub → SSE; stampede protection; deploy; docs

**Scoring (feeds Projects):** four `v1.0` tags + all boxes → 4 · four tags, ≤ 3 boxes missing
in total → 3 · four tags, more missing → 2 · one project not at v1.0 → 1 · two or more → 0.

---

## Scoring table

| Area | Evidence | Critical? | PASS threshold | My score |
|---|---|:---:|:---:|:---:|
| Python (coding) | A2/A3 idiom and speed; B Q1–Q4; C3 | yes | 3 | |
| Java (engineering) | A3 Java rep; C1/C2 code quality; B Q22 | yes | 3 | |
| DSA | A2/A3 average | yes | 3 | |
| SQL | C2 query/migration quality; B Q21 | no | 3 | |
| Backend | B Q5–Q15; C2; E | yes | 3 | |
| Frontend | FlagForge dashboard in E; ForgeCI UI | no | 3 | |
| Git | C2 PR; release tags; repo hygiene in E | no | 3 | |
| Debugging | C1 | yes | 3 | |
| CS fundamentals | B Q19–Q24; A5 | no | 3 | |
| Projects | E | yes | 3 | |
| Interview readiness | A weighted average; A4 ≥ 3 mandatory; Sunday deep dive ≥ 3 | yes | 3 | |

## Decision

| Condition | Decision |
|---|---|
| All critical ≥ 3, ≤ 1 non-critical below, A4 ≥ 3 | **PASS** → *Interview-ready*: applications at volume from Week 25 ([`../JOB_READINESS.md`](../JOB_READINESS.md)) |
| All critical met, ≥ 2 non-critical below | **PASS WITH REMEDIATION** → apply, remediate in W25–26 |
| Any critical below, or A4 < 3 | **FAIL** → keep applying at the current stage's volume; W25–26 focus on the failed area; re-run the failed rounds in the Week 26 final loop |
| Any project not at v1.0 | Week 25 project hours go to that project first; Advanced Versions everywhere are frozen |

---

## If you fail — remediation (Weeks 25–26, these are the buffer weeks; ≈ 10–15 h available)

| Area | Do this |
|---|---|
| **Python (coding)** | Daily 25-min Medium with think-aloud for 10 days; OA sims #7–8 (W24) post-mortems rewritten; [`../PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md) rewritten from memory once more. |
| **Java (engineering)** | Java reps daily for a week (10 min each: hashing, stack, tree, graph, heap); [`../17-resume-tech-defense/java.md`](../17-resume-tech-defense/java.md) drill. |
| **DSA** | Weakest two patterns from A2/A3: 6 problems each with 25-min clocks across W25; Day-30 reviews cleared before the W26 final loop. |
| **SQL** | [`../17-resume-tech-defense/sql.md`](../17-resume-tech-defense/sql.md) + [`../17-resume-tech-defense/postgresql.md`](../17-resume-tech-defense/postgresql.md) drills; `EXPLAIN` one query per project. |
| **Backend** | Re-read each project's `interview-questions.md`; answer every question in writing (2 × 2 h). |
| **Frontend** | [`../17-resume-tech-defense/react.md`](../17-resume-tech-defense/react.md), [`../17-resume-tech-defense/typescript.md`](../17-resume-tech-defense/typescript.md); one component test per dashboard. |
| **Git** | [`../17-resume-tech-defense/git.md`](../17-resume-tech-defense/git.md) + [`../17-resume-tech-defense/github.md`](../17-resume-tech-defense/github.md); clean every repo's history/README. |
| **Debugging** | Two more full buggy-library runs (reset each time) + [`../21-debugging-code-reading/drills.md`](../21-debugging-code-reading/drills.md) in W25; target ≥ 80% fixed in 40 min. |
| **CS fundamentals** | [`../14-cs-fundamentals/interview-questions.md`](../14-cs-fundamentals/interview-questions.md) full pass; 20 flashcards daily. |
| **Projects** | W25 hours to the weakest project first; docs/diagrams second; never start new features. |
| **Interview** | Two mocks/week (ROADMAP §10); every project deep dive recorded until ≥ 3; recruiter-screen rehearsal; final loop W26 must pass A2–A4. |

---

## Results log

```
CP-24 — date: ____________   total time: ____ h   interviewer(s): ____________

PART A  A1 recruiter __/4 | A2 coding I (____) __/4 | A3 coding II (____ / ____) __/4  java rep __/2
        A4 deep dive (project: ________) __/4 | A5 design (prompt: ________) __/4 | A6 behav/résumé __/4
        weighted avg __/4   A4 ≥ 3? __
PART B  Py __/4 FlagForge/backend __/7 cross-project __/7 CS __/6  total __/24  score __/4
PART C  C1 failing before __ after __ fixed __/__ (tampered? no)  C2 project ______ done? __ CI? __  C3 4/4 green? __  score __/4
PART D  Sunday deep dive (project: ________) __/4   track A explain __/4
PART E  FlowGrid tier ______ __/10 | LedgerX ______ __/10 | ForgeCI ______ __/10 | FlagForge ______ __/11   score __/4
MOCKS   W21–24 mock scores: __ __ __ __ __   OA sims #5–8: __ __ __ __

AREA SCORES  Python __ Java __ DSA __ SQL __ Backend __ Frontend __ Git __ Debug __ CS __ Projects __ Interview __
DECISION     PASS / PASS WITH REMEDIATION / FAIL     Interview-ready? __   Applications at volume from: W__
REMEDIATION  areas: ____________  hours W25: __  W26: __
NOTES        (what the interviewer said; what I would say differently; the deep dive that needs work)
```

Copy to [`./README.md`](./README.md#summary-log), [`../trackers/weekly-progress.md`](../trackers/weekly-progress.md),
[`../trackers/interview-tracker.md`](../trackers/interview-tracker.md) and the scorecard in
[`../JOB_READINESS.md`](../JOB_READINESS.md). Hand off to
[`../16-interview-prep/maintenance-plan.md`](../16-interview-prep/maintenance-plan.md) after Week 26.
