# FlagForge — Documentation, benchmarking and résumé

> Documentation is part of the Strong Résumé Version, not an afterthought. Benchmarks are only
> real if the methodology is written down. Résumé bullets come **only** from measured results —
> [ROADMAP honesty rule](../../ROADMAP.md#1-learning-philosophy), [PROJECT_SELECTION.md](../../PROJECT_SELECTION.md),
> [RESUME_TECH_DEFENSE.md](../../RESUME_TECH_DEFENSE.md). Templates: [design doc](../templates/design-doc.md),
> [ADR](../templates/adr.md), [benchmark report](../templates/benchmark-report.md).

Contents: [1 Required docs](#1-required-documentation-set) · [2 Benchmarking protocol](#2-benchmarking-protocol) ·
[3 Résumé bullets](#3-résumé-bullet-template) · [4 GitHub hygiene](#4-github-hygiene) · [5 Checklist](#5-v10-documentation-checklist)

---

## 1. Required documentation set

All under `docs/` in your `flagforge` repo, linked from the README. Aim for dense and specific;
each file ≤ 300 lines; diagrams in `docs/diagrams/` (source + PNG/SVG).

| File | Contents | Written in |
|---|---|---|
| `README.md` | pitch, architecture diagram, 2-minute run, SDK quick start (Maven coordinates), Python quick start, feature table with tiers, measured numbers linked to PERFORMANCE, screenshots, demo GIF, docs index, badges | M1 skeleton, final M4 |
| `docs/ARCHITECTURE.md` | control/data planes, component diagram, publish sequence, evaluation sequence, cache hierarchy, propagation (polling/SSE/pub-sub), "hot path never touches Postgres", module split incl. `contract/`, `sdk-python/`, `tools/` | M2, M4 |
| `docs/EVALUATION.md` | **the evaluation contract**: context, order, operators with type rules, missing attributes, bucketing byte-level algorithm, rounding, reasons, lint rules table, links to `contract/*.json`; change policy (breaking after v1.0) | M2, refined M3/M4 |
| `docs/API.md` | admin + SDK endpoints, auth, error catalogue, ETag semantics, SSE event format, rate limits, link to exported OpenAPI (`docs/api/*.yaml`) | M1–M3 |
| `docs/DATABASE.md` | ERD, tables, indexes with `EXPLAIN` evidence, immutability enforcement, JSONB-vs-normalized ADR link, migration list | M1 |
| `docs/SDK.md` | Java: builder options table, fallback table, init modes, thread model, offline, logging, versioning policy, GitHub Packages install; Python: install, API, limitations (no SSE), contract statement | M3 |
| `docs/TESTING.md` | test pyramid, how to run each layer, Testcontainers requirements, WireMock fault tests, contract vectors (how to add a case), fault-injection hooks, Python test run | M2–M4 |
| `docs/DEPLOYMENT.md` | Compose (dev, two-node, perf profile), AWS topology, security groups, secrets (SSM), TLS, CloudWatch alarms, runbooks (rotate key, restart node, teardown) | M4 |
| `docs/SECURITY.md` | threat model (server vs client keys, PII in targeting), controls, rotation runbook, rate limits, OWASP API Top 10 pass results | M4 |
| `docs/DESIGN_DECISIONS.md` | index of ADRs: JSONB rules; SHA-256 bucketing; first-match priority; polling-then-SSE; thin events; no-Spring SDK; Caffeine `refreshAfterWrite`; reconciler vs outbox; noeviction Redis | throughout |
| `docs/PERFORMANCE.md` | every benchmark below with the full template | M2 (p99 eval), M4 (rest) |
| `docs/FAILURE_ENGINEERING.md` | write-ups of the [scenarios](./failure-engineering.md) executed | M2–M4 |
| `sdk-python/README.md`, `tools/README.md` | install (`pip install -e .[dev]`), usage examples with real output, how tests run, what each tool proves | M3, M4 |
| `CHANGELOG.md` | server and SDK entries per tag; SDK follows semver | tags |

---

## 2. Benchmarking protocol

Never report a number without the block below. Copy it for every run
([benchmark template](../templates/benchmark-report.md)).

```
### <Benchmark name>                                   date · commit <sha> · run id
Environment:  machine/instance type, CPU, RAM, OS, JDK (java -version), Docker version,
              network (localhost / same VPC / Internet), Postgres & Redis versions, container limits
Setup:        stack composition (nodes), config (cache TTLs, polling interval, streaming on/off),
              dataset (flags per env, rules per flag, targets size, snapshot bytes raw/gzip),
              warm-up (duration / requests discarded)
Load:         tool + version, VUs/threads, duration, arrival rate, request mix, think time/jitter
Methodology:  what was measured where (client-side latency incl. network vs server-side timer),
              how many repetitions, which percentile definition (k6 / JMH SampleTime), what varied
Result:       table (p50 / p95 / p99 / max / throughput / error rate), with the three cache states side by side
Observations: what dominated, anomalies, what changed as a result (PR link)
Not claimed:  what this does NOT show (e.g. "single laptop, loopback network, no TLS")
```

### 2.1 Server-side evaluation latency (k6) — M2, repeated in M4

- Script `perf/evaluate.js`: `POST /sdk/v1/evaluate` with a random user key from a pool of 100k and 3 attributes; server key; `http_req_duration` thresholds `p(99)<20`.
- Stages: 100 → 300 → 600 VUs, 60 s each, ramp 10 s; report each stage separately; discard the first 15 s.
- Three cache states: **local warm** (default), **Redis only** (`flagforge.cache.local.enabled=false`), **cold-ish** (local disabled + Redis `FLUSHALL` every 5 s by a sidecar loop — state clearly that this is synthetic).
- Also `perf/config.js`: `GET /sdk/v1/config` with and without `If-None-Match` — report bytes transferred and p99 for 200 vs 304.
- Record server-side timings too (Micrometer `http.server.requests` percentiles) to separate network from compute.

### 2.2 Local SDK evaluation latency (JMH) — M4

- Module `flagforge-benchmarks`; `@BenchmarkMode({Throughput, SampleTime})`, `@OutputTimeUnit(MICROSECONDS)`, 3 forks, 5 warm-up × 5 measurement iterations of 2 s; `-prof gc` for allocation/op.
- Cases: boolean flag with 1 / 5 / 20 rules (each 2 conditions), rollout rule (hash path), flag disabled (short-circuit), flag not found; context with 5 attributes reused (use `@State(Scope.Thread)` and a `Blackhole`).
- Report p50/p99/p99.9 µs and bytes allocated per evaluation; compare a version before and after an optimisation (e.g. pre-sorted rules, pre-computed condition matchers, pre-encoded hash prefix).
- Interpretation rule: JMH numbers are per-call CPU cost in a warm JVM on your machine, not production latency.

### 2.3 Propagation delay: publish → SDK — M4

- Same host for the publisher (`curl` with `date +%s%3N`) and the SDK observer (sample app logging `snapshot loaded envVersion=N at <epochMillis>`) to avoid clock skew (see [failure scenario 10](./failure-engineering.md#10-clock-skew-and-ttls)); or a Python script in `tools/` that publishes and polls the sample app's `/status` endpoint, all on one machine.
- 20 trials each for: SSE (two-node stack via nginx, publish alternately on each node), polling 30 s, polling 5 s. Report min/median/p90/max and the theoretical bound (interval + fetch).
- Include SDK-side jitter (0–2 s on `changed`) in the numbers and say so.

### 2.4 Snapshot size and fetch/parse time — M4

- Generate environments with 50 / 500 / 2 000 flags (Python generator in `tools/`, 3 rules per flag, targets 0 / 100 / 10 000 users) — a seed script, not fabricated results.
- Measure: raw bytes, gzip bytes, `GET /config` p99 (k6, 200 not 304), Jackson parse time in the SDK (JMH `SnapshotCodecBenchmark`), memory of the parsed snapshot (JOL or heap diff).
- Conclude when a delta protocol or segments would be justified; state the size limits you enforce server-side.

### 2.5 Stampede and fan-out (M4, from [failure-engineering](./failure-engineering.md) 7 and 8)

- Stampede: DB rebuild count and `/config` p99 during a 500-VU burst, with protections (a) none, (b) single-flight, (c) write-through, (d) + jitter.
- SSE fan-out: connections per node vs publish→delivery p99 and memory; the point at which 2 s is exceeded.

### 2.6 Rollout distribution (M3, `tools/rollout_sim.py`)

- 100 000 synthetic users at 1 %, 10 %, 50 %; report share, absolute deviation, stickiness verdict when raising each to +15 pts, and server cross-check mismatches (must be 0). Include the JSON report in `docs/PERFORMANCE.md`. This is a correctness measurement, not performance — it belongs in the same document because it is reproducible evidence.

---

## 3. Résumé-bullet template

Rules: one bullet = one measured or verifiable fact; include the setup in the bullet or in the
linked doc; never round up; never claim production traffic; never claim "reduced latency by X %"
unless you measured before and after the same way. Fill only from `docs/PERFORMANCE.md` and the
merged code. Wording defense drills: [17-resume-tech-defense/README.md](../../17-resume-tech-defense/README.md).

**Project line:** `FlagForge — feature-flag & progressive-rollout platform with Java SDK · Java 21, Spring Boot 3, PostgreSQL 16, Redis 7, React/TS, Python, Docker, AWS, GitHub Actions · github.com/<you>/flagforge`

**Bullet skeletons (replace every `<…>` with your measured value or delete the bullet):**

- Built a feature-flag service with immutable per-environment config versions (rollback = new version; immutability enforced by PostgreSQL privileges), attribute/user targeting rules and deterministic percentage rollouts (SHA-256 bucketing with per-flag salt), audited end to end.
- Designed the evaluation hot path to never touch the database: in-process + Redis snapshot cache with ETag/304 conditional fetches and single-flight rebuilds; measured `<p99 ms>` p99 server-side evaluation at `<N>` req/s (`<cache state>`, `<instance/laptop>`, k6, `<date>`).
- Published a dependency-light Java SDK (`java.net.http`, no Spring) with local evaluation from an atomically swapped immutable snapshot, polling with ETags and SSE streaming, stale-if-error and offline modes; local evaluation `<p99 µs>` per call (JMH, `<rules>` rules, `<machine>`).
- Implemented change propagation via Redis pub/sub and Server-Sent Events across `<n>` API nodes: publish-to-SDK delay median `<x s>` / p90 `<y s>` over `<20>` trials (SSE) vs `<interval>` polling; added write-through, single-flight and jitter, reducing post-publish DB rebuilds from `<a>` to `<b>` in a `<500>`-client burst test.
- Wrote a minimal typed Python SDK and a rollout-distribution simulator/config linter against a written evaluation spec and shared JSON test vectors; cross-SDK contract tests (`<N>` vectors, `<M>` generated contexts) verify Java and Python agree — surfaced and fixed `<k>` specification ambiguities.
- Deployed on AWS (EC2 + Docker Compose, RDS PostgreSQL, CloudWatch alarm on snapshot lag) with CI/CD publishing container images to GHCR and the SDK to GitHub Packages; `<n>` unit/integration/contract tests incl. Testcontainers and property-based tests.

**What may NOT appear:** "production", "customers", "millions of evaluations" (unless you
actually ran and documented that many in a load test — then say "in load testing"), "99.99 %
availability", any number without a `docs/PERFORMANCE.md` section behind it.

**Defense check:** for each bullet, be able to answer in 60 s: how measured, on what, what
would change in production, and what the biggest weakness of the measurement is.

---

## 4. GitHub hygiene

- [ ] Repo name `flagforge`; description one line; topics: `feature-flags`, `java`, `spring-boot`, `redis`, `sdk`, `python`.
- [ ] README per [README §24](./README.md#24-readme-and-demo-requirements); screenshots in `docs/img/`; demo GIF ≤ 10 MB.
- [ ] Milestones `M1`–`M4` closed with their issues; labels used consistently; each PR references an issue; squash-merged; conventional commit titles.
- [ ] Tags/releases: `v0.1` (MVP), `sdk-v0.1.0`, `v1.0`, `sdk-v1.0.0`; GitHub Releases with changelog; SDK visible under Packages.
- [ ] CI badge green on `main`; `ci.yml` runs Java, UI and Python jobs; `release-*.yml` visible; no secrets in history (`gitleaks` clean — if a key was ever committed, rotate it and say so in `docs/SECURITY.md`).
- [ ] `.github/PULL_REQUEST_TEMPLATE.md`, `CONTRIBUTING.md` (how to run tests, add a contract vector), `LICENSE` (MIT/Apache-2.0), `.editorconfig`, `CODEOWNERS` (you).
- [ ] `contract/` documented as frozen after v1.0; `docs/EVALUATION.md` has a change-policy section.
- [ ] `sdk-python/` and `tools/` have `pyproject.toml`, `README.md`, tests, type hints, `ruff` + `mypy` config; `pip install -e` works from a clean venv.
- [ ] No generated files, no `.env` with values, no IDE folders; `docker compose up` works from a clean clone in ≤ 2 minutes (test it on another machine or a fresh VM).
- [ ] Issues left open are labelled `advanced`/`stretch` and referenced from README "Roadmap".

---

## 5. v1.0 documentation checklist

- [ ] All files in §1 exist and are linked from the README.
- [ ] `docs/PERFORMANCE.md` contains §2.1–§2.6 with the full protocol block each.
- [ ] `docs/FAILURE_ENGINEERING.md` has at least scenarios 1, 2, 3, 7 (+ ideally 13, 14).
- [ ] `docs/EVALUATION.md` and `contract/` are consistent with both SDKs' tests (CI proves it).
- [ ] Diagrams: component, publish sequence, evaluation sequence, ERD.
- [ ] Résumé bullets drafted from real numbers in [`trackers/project-tracker.md`](../../trackers/project-tracker.md) and [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) updated.
- [ ] Deep-dive rehearsed twice with [interview-questions.md](./interview-questions.md) §7 — recorded, scored against [INTERVIEW_CHECKLIST.md](../../INTERVIEW_CHECKLIST.md).
- [ ] [CP-24](../../checkpoints/checkpoint-24.md) project-review items pre-checked.
