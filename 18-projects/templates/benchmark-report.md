# Benchmark Report — `<Project> · <What was measured>`

> Copy into your repo as `docs/perf/<YYYY-MM-DD>-<slug>.md` and index it from `docs/PERFORMANCE.md`.
> **Rule: unmeasured = not reportable.** No number goes into a README, résumé bullet or interview
> answer unless it comes from a report like this one, with every section filled in.
> A laptop result is a valid result *if the report says it is a laptop result*.

| Field | Value |
|---|---|
| Date | `<YYYY-MM-DD>` |
| Project / tag | `<FlowGrid v1.0 (commit abc1234)>` |
| Author | `<you>` |
| Raw output | `<link to k6 summary JSON / JMH output committed under docs/perf/raw/>` |
| Status | Draft / Final |

---

## 1. Goal

*One sentence: what question does this measurement answer, and what decision or claim depends on it?*

> Example: "Establish a baseline for order-creation latency and throughput under concurrent
> clients on a single small instance, so the README can state measured p95 and req/s."

Hypothesis (optional, written **before** running): `<e.g. p95 < 200 ms at 50 VUs>`

## 2. Environment

| Item | Value |
|---|---|
| Hardware | `<laptop model / EC2 instance type>`, `<vCPU>` vCPU, `<RAM>` GB RAM, `<disk type>` |
| OS | `<Ubuntu 24.04 / macOS 15>` kernel `<…>` |
| JVM | `<Temurin 21.0.x>`, flags `<-Xmx…, -XX:…>` |
| Application build | `<commit / tag>`, profile `<prod>`, virtual threads `<on/off>` |
| PostgreSQL | `<16.x>`, `<container / RDS db.t3.micro>`, `shared_buffers=<…>`, `max_connections=<…>` |
| Redis | `<7.x>`, `<container / EC2 / ElastiCache>`, `maxmemory=<…>` |
| Container limits | `<api: 1 CPU / 1 GB; postgres: 1 CPU / 1 GB; …>` or "none" |
| Network | `<same host (Compose) / same VPC / load generator on separate machine>` |
| Load generator location | `<same laptop / separate EC2 t3.small>` — note: same-host generators steal CPU from the system under test |
| Other processes running | `<IDE closed? browser? — be honest>` |

## 3. Setup

| Item | Value |
|---|---|
| Data volume | `<warehouses: 5, SKUs: 10,000, inventory rows: 50,000, orders pre-existing: 0>` |
| How data was seeded | `<script path>` |
| Relevant config | `<HikariCP pool size, cache TTLs, idempotency TTL, worker count, lease seconds>` |
| Caches state at start | `<cold / warmed by warm-up phase>` |
| Auth | `<JWT obtained once and reused / per-VU login>` |
| Anything disabled for the run | `<e.g. CloudWatch agent off>` — and why |

## 4. Load

| Item | Value |
|---|---|
| Tool | `<k6 v0.x / JMH 1.37 / hey / ab>` |
| Script | `<path in repo, e.g. perf/k6/create-order.js>` |
| Scenario | `<constant VUs / ramping arrival rate / closed model>` |
| Virtual users / threads | `<50>` |
| Duration | `<5 min>` |
| Ramp profile | `<0→50 VUs over 30 s, hold 4 min, ramp down 30 s>` |
| Request mix | `<100% POST /orders with unique Idempotency-Key; or 80% eval / 20% publish>` |
| Think time | `<none / 100 ms>` |
| Payload | `<size, representative example>` |

## 5. Methodology

- Warm-up: `<60 s at 10 VUs, discarded>` — JIT and connection pools need it; never report cold numbers as steady-state.
- Runs: `<3>` independent runs; reported figure = `<median run>`; spread across runs shown below.
- Percentiles: `<p50 / p95 / p99>` from the tool's own summary (not eyeballed from a graph).
- Error definition: `<HTTP ≥ 400 that is not an expected 409 / timeouts > 5 s>`.
- What was **not** controlled: `<laptop thermal throttling, background processes, shared cloud tenancy>`.
- Clock / monitoring: `<CPU % of api container sampled with docker stats every 5 s>`.

## 6. Results

### Summary (median run)

| Metric | Value |
|---|---|
| Throughput | `<x> req/s` (or `<y> jobs/min`, `<z> evaluations/s`) |
| p50 latency | `<ms>` |
| p95 latency | `<ms>` |
| p99 latency | `<ms>` |
| Max latency | `<ms>` |
| Error rate | `<%>` (`<count>` / `<total>`) |
| CPU (app) | `<% avg / peak>` |
| Memory (app) | `<MB peak>` |
| DB / Redis notable stats | `<active connections, slow queries, evictions>` |

### Run-to-run spread

| Run | Throughput | p95 | p99 | Errors |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

### Comparison (if this run compares alternatives — e.g. `FOR UPDATE` vs `@Version`, polling vs SSE)

| Variant | Throughput | p95 | p99 | Errors | Notes |
|---|---|---|---|---|---|
| A | | | | | |
| B | | | | | |

Raw output: `<link>`. Charts (optional): `<link to PNG>`.

## 7. Interpretation

- What the numbers mean for the goal in §1: `<…>`
- What limited throughput / latency (evidence, not guesses): `<e.g. CPU at 95% on api; pool exhausted (Hikari wait time); lock waits in pg_stat_activity>`
- Surprises: `<…>`
- What this does **not** show: `<e.g. behaviour on multiple instances; behaviour with 10× data; production hardware>`
- Next experiment (if any): `<…>`

## 8. Threats to validity

- [ ] Load generator on the same host as the system under test (steals CPU)
- [ ] Small data set — indexes may hide costs that appear at scale
- [ ] Single run / short duration
- [ ] Caches warm vs cold not representative of real traffic
- [ ] Laptop thermal throttling / shared cloud tenancy
- [ ] Test data less varied than real data (e.g. all orders hit the same SKU)
- [ ] Measured through Compose networking rather than a real reverse proxy
- [ ] `<other>`

## 9. Reproducibility

Exact commands, from a fresh clone, that reproduce this report:

```bash
git checkout <tag-or-commit>
docker compose -f compose.yaml -f compose.perf.yaml up -d --build
./scripts/seed-perf-data.sh            # <what it seeds>
k6 run --vus 50 --duration 5m --summary-export=docs/perf/raw/<date>-summary.json perf/k6/create-order.js
docker compose down -v
```

Any manual step (obtaining a token, editing a config) is listed here too, or the report is not reproducible.

---

## How this report becomes a résumé bullet

Only after Status = **Final** and the raw output is committed:

> "Measured `<endpoint/operation>` at `<load>` on `<environment in five words>`: p95 `<x> ms`, `<y>` req/s, `<z>%` errors (methodology in `docs/PERFORMANCE.md`)."

If any field above is still `<…>`, the bullet keeps its `<placeholder>`. See
[`PROJECT_SELECTION.md` §6](../../PROJECT_SELECTION.md#6-bullet-writing-rules).
