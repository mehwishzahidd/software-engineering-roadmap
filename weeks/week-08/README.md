# Week 8 — FlowGrid M5: deploy, document, measure

[← Week 7](../week-07/) · [Roadmap](../../ROADMAP.md) · [Week 9 →](../week-09/)

**Phase 1 · FlowGrid** · Milestone **M5 — Deploy, document, measure** (= **Strong Résumé Version, `v1.0`**) · ends with **[Checkpoint CP-8](../../checkpoints/checkpoint-08.md)** on Sunday

| Block | Hours | What it means this week |
|---|---:|---|
| Project | 28 | AWS deployment (EC2 + Compose, RDS Postgres, Redis, S3 exports, IAM least privilege, CloudWatch logs + alarm), CI/CD pipeline (test → image → GHCR → deploy), k6 baseline, **Python load/simulation harness**, docs set, failure exercises, `v1.0` |
| Learning | 6 | AWS RDS, S3, CloudWatch, security groups; CI/CD pipelines; k6 load testing; structured logging |
| DSA | 6 | Recursion + review — **6 new problems in Python** + Day-3/7/14/30 reviews + 1 Java rep |
| Interview / review | 5 | **Story bank starts**; **FlowGrid deep-dive rehearsal**; résumé drill #2; **CP-8** |

---

## 1. Main objective

Finish FlowGrid as something you can put a URL next to and defend for 15 minutes without notes.
Four outcomes:

1. **Deployed on AWS** with a sane, cheap, least-privilege topology: EC2 running Docker Compose
   (app + dashboard + Redis), **RDS PostgreSQL**, **S3** for report exports, **CloudWatch** logs
   and one alarm, security groups that only open what is needed.
2. **A CI/CD pipeline**: PR → `mvn verify` + frontend tests → on `main`: build image → push to
   **GHCR** → deploy to EC2 (SSH/SSM) → smoke test.
3. **Measured behaviour**: a **k6** baseline for order creation latency/throughput under
   concurrency (with retries to prove idempotency), plus the **Python load/simulation harness**
   in `tools/` that drives concurrent order creation against the API and reports latency
   percentiles and reservation contention — the numbers, with methodology, that may become
   résumé bullets (and only those).
4. **A documentation set** (README, ARCHITECTURE, API, DATABASE, TESTING, DEPLOYMENT, SECURITY,
   DESIGN_DECISIONS, PERFORMANCE) and the **failure-engineering exercises** run against the
   deployed system.

Then tag **`v1.0`** — the Strong Résumé Version. ADVANCED features (split fulfillment,
capacity-aware allocation) wait for the polish weeks.

Spec: [`18-projects/flowgrid/README.md`](../../18-projects/flowgrid/README.md) · M5 in
[`18-projects/flowgrid/milestones.md`](../../18-projects/flowgrid/milestones.md) ·
[`18-projects/flowgrid/failure-engineering.md`](../../18-projects/flowgrid/failure-engineering.md) ·
[`18-projects/flowgrid/docs-and-resume.md`](../../18-projects/flowgrid/docs-and-resume.md).

## 2. Prerequisites

- M4 tagged `m4`; Compose runs app + dashboard + Postgres + Redis locally; CI green (Maven + Node).
- AWS account hardened (Week 7); EC2 instance exists with Docker; billing alarm set.
- `tools/` generator works against Compose. Install **k6** locally.
- Read [`12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md) and [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md) *before* Monday.

## 3. Learning topics

| Topic | Subtopics | Folder file |
|---|---|---|
| AWS RDS | PostgreSQL 16 on `db.t4g.micro`/`db.t3.micro`, private subnet, SG allowing 5432 only from the EC2 SG, parameter groups (basics), backups, connection string via SSM Parameter Store/Secrets Manager, `psql` from EC2 | [`12-aws/rds.md`](../../12-aws/rds.md) |
| AWS S3 | bucket (block public access), IAM policy for one bucket, `PutObject` from the app via SDK v2, presigned GET for downloads, lifecycle rule | [`12-aws/s3.md`](../../12-aws/s3.md) |
| CloudWatch | `awslogs` Docker log driver or CloudWatch agent, log groups/retention, metric filter (e.g. `ERROR` count, `5xx`), one alarm → SNS email; basic EC2 metrics | [`12-aws/cloudwatch.md`](../../12-aws/cloudwatch.md) |
| Deploy walkthrough & cost safety | end-to-end order of operations, security groups, instance profile, elastic IP (or not), stopping resources, budget alarm, teardown checklist | [`12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md), [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md), [`12-aws/iam.md`](../../12-aws/iam.md), [`12-aws/ec2.md`](../../12-aws/ec2.md) |
| CI/CD pipelines | workflow jobs & dependencies, `docker/build-push-action` to GHCR with `GITHUB_TOKEN`, tags by SHA + `latest`, environments & secrets, deploy over SSH (`appleboy/ssh-action` or plain `ssh` with a key secret) or SSM `send-command`, smoke test step, concurrency groups | [`13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md), [`13-cicd/github-actions.md`](../../13-cicd/github-actions.md) |
| k6 load testing | scenarios, VUs vs arrival rate, thresholds, `check`s, `Idempotency-Key` per iteration, summary export JSON, warm-up, repeat runs | [`18-projects/flowgrid/docs-and-resume.md`](../../18-projects/flowgrid/docs-and-resume.md), [`18-projects/templates/benchmark-report.md`](../../18-projects/templates/benchmark-report.md) |
| Structured logging & Actuator | JSON logs (Logback + `logstash-logback-encoder` or Spring Boot 3.4 structured logging), correlation id via MDC filter, log levels per package, Actuator `health`/`info`/`metrics`, Micrometer counters you already emit | [`05-spring-boot/06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md) |
| Python for load tooling | `concurrent.futures.ThreadPoolExecutor`, `time.perf_counter`, percentiles (`statistics.quantiles`), `argparse` subcommands, JSON report, pytest with a fake server/session | [`19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md) |

## 4. Concepts to learn

### 4.1 The deployment topology (draw it before building it)

```
Internet ──443/80──▶ [EC2 t3.small: Nginx → dashboard (static) + app:8080, redis:6379 (internal only)]
                          │ instance profile: s3:PutObject/GetObject on flowgrid-exports-*, logs:PutLogEvents, ssm:GetParameter
                          │ SG-app: 22 from my IP, 80/443 from 0.0.0.0/0
                          ▼ 5432 (SG-db allows only SG-app)
                     [RDS PostgreSQL 16, private, automated backups 7d]
                     [S3 flowgrid-exports-<acct>]  [CloudWatch log group /flowgrid/app, alarm 5xx>5/5min → SNS email]
```

Redis on EC2 (in Compose) is chosen over ElastiCache for cost; write that decision down with the
trade-off (no HA, shares the instance). Secrets (DB URL/password, JWT secret) live in SSM Parameter
Store (SecureString) and are read at deploy time into the Compose `.env` — never committed.

- **Interview angle:** "Draw your deployment." "Why is the DB not publicly reachable?" "Where are secrets?" "What would you change for HA?" (multi-AZ RDS, ALB + 2 instances, ElastiCache, ECS/Fargate — say why you did not).
- **FlowGrid uses this:** `docs/DEPLOYMENT.md` + `docs/ARCHITECTURE.md` diagram; the same topology is reused by LedgerX in Week 13, faster.

### 4.2 S3 exports the right way

```java
@Bean S3Client s3(@Value("${aws.region}") String region) { return S3Client.builder().region(Region.of(region)).build(); } // credentials from instance profile

public URL exportLowStockCsv() {
    byte[] csv = reportService.lowStockCsv();                                   // StringBuilder from Week 1
    String key = "reports/low-stock/%s.csv".formatted(Instant.now());
    s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("text/csv").build(), RequestBody.fromBytes(csv));
    return presigner.presignGetObject(b -> b.signatureDuration(Duration.ofMinutes(15))
            .getObjectRequest(g -> g.bucket(bucket).key(key))).url();          // client downloads directly, bucket stays private
}
```

- **Interview angle:** "How do you let a user download a private S3 object?" (presigned URL). "What IAM permissions does the app need?" (exactly `PutObject`/`GetObject` on one bucket ARN).
- **FlowGrid uses this:** `GET /api/reports/low-stock/export` → `202`/`200` with a presigned URL; the dashboard's "Export" button.

### 4.3 The pipeline: test → image → GHCR → deploy → smoke

```yaml
jobs:
  verify:   { runs-on: ubuntu-latest, steps: [checkout, setup-java(cache: maven), run: mvn -B verify] }
  frontend: { runs-on: ubuntu-latest, steps: [checkout, setup-node(cache: npm), run: npm ci && npm test -- --run && npm run build] }
  image:
    needs: [verify, frontend]
    if: github.ref == 'refs/heads/main'
    permissions: { contents: read, packages: write }
    steps:
      - uses: docker/login-action@v3
        with: { registry: ghcr.io, username: "${{ github.actor }}", password: "${{ secrets.GITHUB_TOKEN }}" }
      - uses: docker/build-push-action@v6
        with: { push: true, tags: "ghcr.io/${{ github.repository }}/app:${{ github.sha }},ghcr.io/${{ github.repository }}/app:latest" }
  deploy:
    needs: image
    environment: production
    concurrency: { group: deploy-prod, cancel-in-progress: false }
    steps:
      - run: ssh -o StrictHostKeyChecking=accept-new ubuntu@${{ secrets.EC2_HOST }} "cd /opt/flowgrid && IMAGE_TAG=${{ github.sha }} docker compose pull && docker compose up -d && sleep 10 && curl -fsS localhost/actuator/health"
```

Deploy by **immutable SHA tag**, not `latest`; a failed smoke check fails the job (and you roll
back by redeploying the previous SHA — practise it once).

- **Interview angle:** "Describe your CI/CD pipeline and what stops a bad build from reaching production." "How do you roll back?" "Why SHA tags?"
- **FlowGrid uses this:** `.github/workflows/ci.yml` + `cd.yml`; ForgeCI (Weeks 14–19) is literally a system that runs pipelines like this one — you will understand it from both sides.

### 4.4 Measuring honestly: k6 + the Python harness

```js
// k6/order-create.js
import http from "k6/http"; import { check } from "k6";
export const options = { scenarios: { orders: { executor: "constant-arrival-rate", rate: 20, timeUnit: "1s", duration: "2m", preAllocatedVUs: 50 } },
                         thresholds: { http_req_failed: ["rate<0.01"], http_req_duration: ["p(95)<400"] } };
export default function () {
  const key = crypto.randomUUID();
  const body = JSON.stringify({ customerRef: `k6-${__VU}-${__ITER}`, lines: [{ skuCode: pick(), qty: 1 }], priority: "STANDARD" });
  const params = { headers: { "Content-Type": "application/json", Authorization: `Bearer ${__ENV.TOKEN}`, "Idempotency-Key": key } };
  const r1 = http.post(`${__ENV.BASE}/api/orders`, body, params);
  if (__ITER % 10 === 0) { const r2 = http.post(`${__ENV.BASE}/api/orders`, body, params); check(r2, { "replayed": r => r.headers["Idempotent-Replayed"] === "true" }); }
  check(r1, { "201": r => r.status === 201 });
}
```

Python harness (`tools/flowgrid_tools/simulate.py`): `ThreadPoolExecutor(max_workers=N)` fires
`make_orders(...)` concurrently, records per-request latency with `perf_counter`, counts
`201`/`409`/`422`/`5xx`, computes p50/p95/p99 with `statistics.quantiles`, and — the part k6
cannot do — a **contention scenario**: M orders all targeting the last K units of one SKU, asserting
exactly K reservations succeed. Outputs JSON + a Markdown table for `PERFORMANCE.md`.

Methodology before numbers: machine/instance types, JVM flags, Postgres location (local Compose vs
RDS), data volume (seeded via the generator with a seed), warm-up, repetitions, what "latency"
means (client-observed, including network). Use [`18-projects/templates/benchmark-report.md`](../../18-projects/templates/benchmark-report.md).

- **Interview angle:** "What throughput did you measure and how?" — answer with setup, load shape, numbers, and what limited it (DB row lock on the hot SKU; pool size). Never quote a number without its setup.
- **FlowGrid uses this:** `docs/PERFORMANCE.md`; résumé bullet candidates go through [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md)'s rules.

### 4.5 Structured logs with a correlation id

```java
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String id = Optional.ofNullable(req.getHeader("X-Request-Id")).orElse(UUID.randomUUID().toString());
        MDC.put("requestId", id); res.setHeader("X-Request-Id", id);
        try { chain.doFilter(req, res); } finally { MDC.remove("requestId"); }
    }
}
```

JSON log lines (`timestamp, level, logger, message, requestId, userId, orderId`) go to CloudWatch;
a metric filter on `level = "ERROR"` feeds the alarm. `WARN` for expected business failures
(`InsufficientStock`), `ERROR` only for things a human must look at.

- **Interview angle:** "How do you trace one request through logs?" "What do you alert on?" (symptoms users feel: 5xx rate, latency, health — not every exception).
- **FlowGrid uses this:** every log line; the failure exercises below are observed through CloudWatch, not `docker logs`.

### 4.6 Recursion: the mental model before trees (Week 9)

```python
def pow_(x: float, n: int) -> float:                 # 50. Pow(x, n): halve the problem
    if n == 0: return 1.0
    if n < 0: return 1.0 / pow_(x, -n)
    half = pow_(x, n // 2)
    return half * half * (x if n % 2 else 1.0)

def reverse(head):                                   # 206 recursively: trust the call on the smaller list
    if head is None or head.next is None: return head
    new_head = reverse(head.next)
    head.next.next = head; head.next = None
    return new_head
```

Base case → recursive case on a strictly smaller input → combine. Draw the call stack for n = 5.
Know the recursion limit (~1000 frames; `sys.setrecursionlimit`) and when to go iterative.

- **Interview angle:** "What is the complexity of your recursive solution?" (count calls × work per call; memoisation turns exponential into linear). "When does recursion overflow and what do you do?"
- **FlowGrid uses this:** not directly — but the allocator's ADVANCED split-fulfillment (polish weeks) is a recursive search over warehouse subsets, and Week 9's trees need this cold.

## 5. Resources

- AWS: [RDS for PostgreSQL user guide](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html); [S3 — presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html); [AWS SDK for Java 2.x developer guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/home.html); [CloudWatch Logs — Docker `awslogs` driver](https://docs.docker.com/engine/logging/drivers/awslogs/); [CloudWatch alarms](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/AlarmThatSendsEmail.html); [SSM Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html).
- CI/CD: [GitHub Actions — publishing Docker images](https://docs.github.com/en/actions/use-cases-and-examples/publishing-packages/publishing-docker-images); [GHCR docs](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry); [environments and secrets](https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-deployments/managing-environments-for-deployment).
- k6: [k6 docs](https://grafana.com/docs/k6/latest/), [scenarios](https://grafana.com/docs/k6/latest/using-k6/scenarios/), [thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/).
- Logging: [Spring Boot — logging](https://docs.spring.io/spring-boot/reference/features/logging.html) (structured logging section), [Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html).
- Python: [`concurrent.futures`](https://docs.python.org/3/library/concurrent.futures.html), [`statistics.quantiles`](https://docs.python.org/3/library/statistics.html#statistics.quantiles).
- Interview: [`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md), [`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md), [`18-projects/flowgrid/interview-questions.md`](../../18-projects/flowgrid/interview-questions.md), [`17-resume-tech-defense/aws.md`](../../17-resume-tech-defense/aws.md), [`17-resume-tech-defense/docker.md`](../../17-resume-tech-defense/docker.md), [`17-resume-tech-defense/cicd.md`](../../17-resume-tech-defense/cicd.md).
- DSA: [`03-dsa/09-recursion.md`](../../03-dsa/09-recursion.md), NeetCode "Recursion" / Blind-75 review.

## 6. Exercises + coding assignments (inside FlowGrid)

| # | Exercise | Acceptance |
|---|---|---|
| 1 | Least-privilege pass: write the app's IAM policy by hand (bucket ARN + log group ARN + parameter ARNs); prove a `PutObject` to another bucket is denied | Policy JSON in `docs/SECURITY.md`; denied call recorded |
| 2 | Rollback drill: deploy SHA n, then redeploy SHA n−1 via the workflow (`workflow_dispatch` with an input), smoke passes | Documented in `DEPLOYMENT.md` with timings |
| 3 | k6 baseline × 3 runs against the deployed stack + 3 runs against local Compose; table with p50/p95/p99, error rate, and the bottleneck hypothesis | `PERFORMANCE.md` per benchmark template |
| 4 | Python harness: contention scenario (M = 50 orders, K = 5 units) → exactly 5 `201`s with reservations, 45 clean `422`/`409`; JSON + Markdown report; pytest with a fake session | `tools/README.md` documents both commands; tests green in CI (Python job) |
| 5 | Docs set complete: README (pitch, URL, screenshots, how to run), ARCHITECTURE (diagram + flows), API, DATABASE (ERD), TESTING (pyramid + how to run + CI), DEPLOYMENT, SECURITY (auth, roles, secrets, IAM, threat notes), DESIGN_DECISIONS (ADRs from [`18-projects/templates/adr.md`](../../18-projects/templates/adr.md)), PERFORMANCE | Every doc reviewed against [`docs-and-resume.md`](../../18-projects/flowgrid/docs-and-resume.md) |

### Break it (on the deployed system, observed through CloudWatch)

- Stop the Redis container on EC2 during a k6 run. Latency rises, errors stay ~0 (Week 6's degradation). Restart; note recovery time.
- Reboot the RDS instance (or set the SG to block 5432) mid-run. Observe HikariCP errors, the 5xx alarm firing, health going `DOWN`. Restore; note how long until healthy. Decide whether the app should retry or fail fast on startup.
- Deploy an image whose smoke check fails (break `/actuator/health` on purpose). The pipeline must stop; the old container must still serve. If it doesn't, fix the Compose/health ordering.
- Fill the EC2 disk with logs by setting log level to `DEBUG` under load for 5 minutes. See what breaks first. Set log rotation/retention; revert.
- Revoke the S3 permission from the instance role; trigger an export. Expect a clean `503` `ProblemDetail`, not a stack trace to the client.

### Debug it

- The app on EC2 cannot reach RDS: work the chain — SG rules (source = SG-app?), subnet/route, RDS "publicly accessible" flag, `nc -zv host 5432`, `psql` from the instance, the connection string in SSM. Write the checklist into `DEPLOYMENT.md`.
- The pipeline's deploy step hangs: `ssh` waiting on host-key confirmation; or `docker compose pull` needs GHCR auth on the instance (`docker login ghcr.io` with a read-only PAT stored in SSM). Read the job log; fix; document.
- k6 shows p99 spikes every ~60 s: the low-stock scheduled job's query without its index on RDS (fresh DB, no `ANALYZE`). Correlate timestamps in CloudWatch; run `ANALYZE`; confirm.
- Python harness reports impossible numbers (p99 < p50): percentiles computed on an unsorted list with manual indexing. Use `statistics.quantiles` and add a test with a known distribution.

## 7. DSA — Recursion + review (6 new, in Python)

Guide: [`03-dsa/09-recursion.md`](../../03-dsa/09-recursion.md); templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) (recursion, memo with `lru_cache`).
Limits: Easy 20 min, Medium 30 min. A lighter week by design — the review load is heavy (Day-30 of Week 4 + CP-8).

| Day | Problem | Difficulty | Limit | Focus |
|---|---|---|---|---|
| Mon | [509. Fibonacci Number](https://leetcode.com/problems/fibonacci-number/) | Easy | 15 min | naive → `@lru_cache` → iterative; count calls |
| Mon | [70. Climbing Stairs](https://leetcode.com/problems/climbing-stairs/) | Easy | 15 min | same recurrence in disguise |
| Tue | [50. Pow(x, n)](https://leetcode.com/problems/powx-n/) | Medium | 25 min | halve the problem; negative n |
| Wed | [24. Swap Nodes in Pairs](https://leetcode.com/problems/swap-nodes-in-pairs/) | Medium | 25 min | recursion on lists (Week 7 bridge) |
| Thu | [779. K-th Symbol in Grammar](https://leetcode.com/problems/k-th-symbol-in-grammar/) | Medium | 30 min | relate row n to row n−1; no building |
| Sat | [226. Invert Binary Tree](https://leetcode.com/problems/invert-binary-tree/) | Easy | 15 min | first tree recursion — the door to Week 9 |

**Java rep (Sat, 30 min):** re-do **206. Reverse Linked List** recursively in Java, then **50. Pow(x, n)** with `long` for `n` (why: `-Integer.MIN_VALUE` overflows).

**Spaced reviews due:** Day-30 of Week 4 — Maximum Average Subarray, Longest Substring Without
Repeating (Mon), Minimum Size Subarray Sum (Tue), Max Consecutive Ones III, Fruit Into Baskets (Wed),
Longest Repeating Character Replacement, Permutation in String (Thu), Minimum Window Substring (Sat)
→ `Mastered` on a clean timed solve. Day-14 of Week 6 — Binary Search, Search Insert (Mon), First Bad
Version, First and Last Position (Tue), Search 2D Matrix (Wed), Koko, Find Minimum Rotated (Thu),
Search Rotated (Sat). Day-7 of Week 7 — Reverse Linked List, Middle (Mon), Merge Two Sorted, Cycle
(Tue), Remove Nth (Wed), Reorder, Add Two Numbers (Thu), LRU Cache (Sat). Day-3 of this week's —
Fibonacci, Climbing Stairs (Thu), Pow (Fri), Swap Nodes (Sat), K-th Symbol (Sun).

**Phase-1 review (Sat, 45 min):** re-solve the three problems with the most `Needs Review` marks
across Weeks 1–7 (from the tracker). These are the ones CP-8 will probe.

## 8. Project work — FlowGrid M5

Read M5 in [`milestones.md`](../../18-projects/flowgrid/milestones.md) and
[`docs-and-resume.md`](../../18-projects/flowgrid/docs-and-resume.md) first. GitHub milestone
**M5 — Deploy, document, measure**, issues labelled `m5`, `aws`, `cicd`, `perf`, `docs`, `tools`.

### Task checklist

- [ ] Mon: RDS instance (private) + SG chain; SSM parameters; app config profile `prod`; structured JSON logging + correlation id + Actuator exposure; `awslogs` driver in the prod Compose file; first manual deploy (`docker compose -f compose.prod.yaml up -d`) with the image from GHCR (push manually once).
- [ ] Tue: S3 bucket + policy + export endpoint + presigned URL + dashboard button; CloudWatch log group, metric filter, alarm → SNS email (confirm the subscription); least-privilege pass.
- [ ] Wed: `cd.yml` pipeline (image → GHCR → deploy → smoke) with environment `production`; rollback drill; Nginx/static dashboard served on 80 (443 with a self-signed cert or a free DNS + Let's Encrypt is optional — note the choice).
- [ ] Thu: k6 script + 3 + 3 runs; Python `simulate.py` (concurrency + contention scenario) + pytest + README; `PERFORMANCE.md` with methodology, tables, bottleneck analysis; Python job in CI.
- [ ] Fri: failure exercises on the deployed stack (§6 Break it) logged in `FAILURE_LOG.md`; `SECURITY.md`; `DEPLOYMENT.md` finalised with the teardown/cost checklist.
- [ ] Sat: docs set review against `docs-and-resume.md`; README with screenshots + URL; ADRs; GitHub milestone/issues cleanup; PR; tag **`v1.0`**; deep-dive rehearsal; story bank.
- [ ] Sun: **CP-8**; then **stop the EC2 instance** (or leave it up only if the budget alarm and the cost sheet say so).

### Acceptance criteria (summary)

- Deployed URL serves the dashboard and API over EC2 + RDS + Redis; DB unreachable from the internet; secrets in SSM; instance role least-privilege (policy in `SECURITY.md`).
- CI on PRs (Maven + Node + Python); CD on `main` builds, pushes by SHA to GHCR, deploys, smoke-checks; rollback documented and rehearsed.
- CloudWatch: app logs with `requestId`; alarm on 5xx/ERROR to email — tested by firing it.
- S3 export works via presigned URL; bucket private.
- `PERFORMANCE.md`: k6 baseline (local + AWS, 3 runs each) and Python contention results with methodology; bottleneck explained.
- Docs set complete; failure exercises logged; issues closed; **`v1.0` tagged and released** on GitHub with notes.

### Verification tests

- Smoke test script (`scripts/smoke.sh`): health `UP`, login works, `GET /api/warehouses` returns data, export returns a presigned URL that downloads — run by the pipeline after deploy.
- IT (local): export service writes to a LocalStack/Testcontainers S3 or a fake `S3Client` bean — choose and document; missing permission → `503` `ProblemDetail`.
- Logging test: `MockMvc` request with `X-Request-Id` → same id in the response header and in a captured log event (Logback `ListAppender`).
- Python: percentile function against a known distribution; contention scenario logic with a fake session that simulates K successes; CLI parsing.

### Failure-engineering scenarios this week

From [`failure-engineering.md`](../../18-projects/flowgrid/failure-engineering.md) — the
deployment set: **Redis down under load**, **database outage / reboot**, **bad deploy blocked by
smoke test + rollback**, **disk full from logs**, **lost S3 permission**, plus a re-run of
**oversell under concurrency** *against RDS* with the Python harness (network latency changes lock
hold times — measure it). Each with reproduce → observe (CloudWatch) → diagnose → fix → regression test/check → log entry.

### PR expectations

≈ 4 PRs (prod config + logging + RDS; S3 + CloudWatch + IAM; CD pipeline; perf + tools + docs).
The final PR's description is the draft of the GitHub **release notes for `v1.0`**. Close milestone M5; open a milestone **Polish (W24–26)** and move any leftover ADVANCED ideas there — do not start them.

## 9. Git activity

```bash
git switch -c feat/m5-deploy
git commit -m "feat(ops): structured json logging with request correlation id"
git commit -m "feat(reports): low-stock csv export to s3 with presigned download"
git commit -m "ci: cd pipeline — build, push ghcr by sha, deploy over ssh, smoke test"
git commit -m "perf: k6 baseline and python contention harness with methodology"
git commit -m "docs: complete docs set (architecture, api, database, testing, deployment, security, decisions, performance)"
git tag -a v1.0 -m "FlowGrid v1.0 — Strong Résumé Version: deployed, tested, measured, documented"
git push origin v1.0
gh release create v1.0 --title "FlowGrid v1.0" --notes-file docs/RELEASE_NOTES_v1.0.md
```

- Protect tags `v*` (no deletion); pipeline may also run on tag pushes — keep deploy on `main` only.
- `git log --oneline m1..v1.0 | wc -l` — put the number and the PR count in your notes (it is *your* evidence of the work, not a résumé bullet).

## 10. Interview preparation

Five hours this week, four activities:

1. **FlowGrid deep-dive rehearsal** (Sat, 60 min): using [`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md), deliver a 10–15 minute walkthrough *without notes* (problem → architecture → the hard parts: reservations under concurrency, idempotency, deterministic allocation, Redis degradation, deploy → numbers → what you would do differently), then answer 10 questions from [`18-projects/flowgrid/interview-questions.md`](../../18-projects/flowgrid/interview-questions.md) chosen at random. Record it. Score in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
2. **Story bank starts** (Sat, 45 min): [`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md) — write 4 STAR stories from Weeks 4–8 with *real* details: the deadlock you caused and fixed; the oversell test that failed first; the stale-cache bug; the deploy that the smoke test blocked. Plus the **career-gap answer**, honest and short, and a refreshed 60-second "about me" (Week 1's draft, now with a deployed project).
3. **Résumé-defense drill #2** (Tue, 30 min): Java, Git, SQL again ([`17-resume-tech-defense/java.md`](../../17-resume-tech-defense/java.md), [`git.md`](../../17-resume-tech-defense/git.md), [`sql.md`](../../17-resume-tech-defense/sql.md)) + a first look at [`aws.md`](../../17-resume-tech-defense/aws.md), [`docker.md`](../../17-resume-tech-defense/docker.md), [`cicd.md`](../../17-resume-tech-defense/cicd.md) now that you have used them. Cross-check [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md).
4. **Think-aloud #4** (Thu, 45 min, Python): one unseen recursion/list Medium (suggested: [92. Reverse Linked List II](https://leetcode.com/problems/reverse-linked-list-ii/)); method from [`16-interview-prep/coding-interview-method.md`](../../16-interview-prep/coding-interview-method.md); score against [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md).

Questions to answer out loud:

Track A: 1. Naive Fibonacci is O(2ⁿ) — show the recursion tree and why memoisation makes it O(n). 2. What is the recursion depth of `pow_` and of naive linked-list reversal? 3. When do you convert recursion to iteration?
Track B: 4. Draw your AWS deployment and justify each security group rule. 5. Describe the pipeline from PR to production and the rollback. 6. What did k6 measure, under what setup, and what was the bottleneck? 7. How does a request get a correlation id and how would you find its logs? 8. What is in the instance role and why nothing more? 9. "Tell me about a bug you caused" (story bank). 10. The career gap, in 30 seconds.

## 11. Revision work

- CP-8 covers Weeks 5–8: redo one explanation each of locking (W5), allocation + cache invalidation (W6), two-phase transfers + client auth (W7), deployment + pipeline (W8) — five sentences each, out loud.
- Re-read your `DESIGN_DECISIONS.md` ADRs: can you defend every one in 60 seconds? Rewrite any you cannot.
- Phase-1 DSA review (§7): the three weakest problems.

## 12. Daily plan

| Day | Blocks |
|---|---|
| **Mon** (8 h) | Learning 2: RDS, security groups, SSM; structured logging · Project 4.5: RDS + SG chain, SSM params, prod profile, JSON logs + correlation id, `awslogs`, first manual deploy from GHCR · DSA 1.5: 509, 70 + Day-30 (Max Avg, Longest Substring) + Day-14 (Binary Search, Search Insert) + Day-7 (Reverse List, Middle) |
| **Tue** (8 h) | Project 5: S3 export + presigned URL + button; CloudWatch group, metric filter, alarm → SNS; least-privilege pass · DSA 2: 50 + Day-30 (Min Size Subarray) + Day-14 (First Bad, First/Last) + Day-7 (Merge Two, Cycle) · Interview 1: **résumé drill #2** (Java/Git/SQL + AWS/Docker/CI first look) |
| **Wed** (8 h) | Learning 2: CI/CD pipeline examples; k6 basics · Project 4.5: `cd.yml` (image → GHCR → deploy → smoke), environment + secrets, rollback drill, Nginx/static dashboard · DSA 1.5: 24 + Day-30 (Ones III, Fruit) + Day-14 (Search 2D) + Day-7 (Remove Nth) |
| **Thu** (8 h) | Project 5: k6 script + 6 runs; Python `simulate.py` + contention scenario + pytest + README; `PERFORMANCE.md`; Python job in CI · DSA 1.5: 779 + Day-30 (Char Replacement, Permutation) + Day-14 (Koko, Find Min) + Day-7 (Reorder, Add Two) + Day-3 (509, 70) · Interview 1: **think-aloud #4** · Docs 0.5: `PERFORMANCE.md` methodology |
| **Fri** (5 h) | Learning 1: CloudWatch alarms; cost safety re-read · Project 2.5: failure exercises on the deployed stack; `SECURITY.md`; `DEPLOYMENT.md` + teardown checklist · DSA 1: Day-3 (50) · Retro 0.5 |
| **Sat** (6 h) | Project 2.5: docs review, README + screenshots, ADRs, milestone cleanup, PR, **tag `v1.0` + release** · DSA 1.5: 226 + Day-30 (Min Window) + Day-14 (Search Rotated) + Day-7 (LRU) + Day-3 (24) + **Java rep** + Phase-1 weakest-3 · Interview 2: **deep-dive rehearsal** (60) + **story bank** (45) + "about me" v3 (15) |
| **Sun** (3 h) | **[Checkpoint CP-8](../../checkpoints/checkpoint-08.md)** (timed) · Day-3 (779) · trackers · stop EC2 if not needed · plan Week 9 (read [`18-projects/ledgerx/README.md`](../../18-projects/ledgerx/README.md)) · rest |

## 13. End-of-week test → Checkpoint CP-8

This week's test *is* [`checkpoints/checkpoint-08.md`](../../checkpoints/checkpoint-08.md)
(timed Python coding — Easies reliably, first Mediums; knowledge questions across Weeks 5–8; a
practical task; explain-out-loud prompts; the FlowGrid v1.0 review with scoring thresholds).
Saturday-night self-check:

<details>
<summary>1. Your alarm fires on "any ERROR log line". Why is that a bad alarm and what should it be?</summary>

It pages on noise and cannot distinguish one flaky export from an outage. Alarm on user-visible
symptoms with thresholds and durations: 5xx rate > x% over 5 min, p95 latency, health check
failures, and keep ERROR counts as a dashboard metric.
</details>

<details>
<summary>2. The pipeline deploys <code>app:latest</code>. What can go wrong that a SHA tag prevents?</summary>

`latest` is mutable: a redeploy may pull a different image than the one tested; rollback is
ambiguous; two instances may run different code. SHA tags are immutable and traceable to a commit.
</details>

<details>
<summary>3. k6 reports 20 req/s with p95 = 350 ms locally but 900 ms on AWS with the same rate. Name three candidate causes and how you would separate them.</summary>

Network RTT client→EC2 (measure with a trivial `GET /actuator/health` baseline), EC2→RDS latency
inflating lock hold time on the hot SKU (compare a no-lock read endpoint vs order creation), and
instance size/JVM heap (CPU steal, GC logs). Change one variable at a time and record each run.
</details>

<details>
<summary>4. Python: <code>@lru_cache</code> on <code>fib(n)</code> — what is the time and space complexity, and what is the risk for n = 5000?</summary>

O(n) time, O(n) space for the cache; but recursion depth n hits the default recursion limit (~1000)
→ `RecursionError`. Use an iterative loop or raise the limit deliberately.
</details>

<details>
<summary>5. Why does the DB security group reference the app's security group rather than the instance's IP?</summary>

SG-to-SG rules follow the instances that carry the group: replacing/adding an EC2 instance needs
no rule change, and no public/elastic IP is required for the DB path.
</details>

<details>
<summary>6. What must be in <code>PERFORMANCE.md</code> before any number can become a résumé bullet?</summary>

Environment (instance/DB types, JVM), data volume and seeding, load shape (rate/VUs/duration),
warm-up and repetitions, exact metric definitions, the bottleneck analysis, and the date/commit —
per [`PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) and [`docs-and-resume.md`](../../18-projects/flowgrid/docs-and-resume.md).
</details>

## 14. Mastery checklist

- [ ] FlowGrid v1.0 is deployed on AWS with EC2 + RDS + Redis + S3 + CloudWatch, least-privilege IAM, secrets in SSM, DB private.
- [ ] A PR cannot reach production untested; a bad deploy is blocked by the smoke test; I have rolled back once.
- [ ] I can trace a request via its correlation id in CloudWatch and explain what the alarm watches and why.
- [ ] I have k6 and Python-harness numbers with full methodology and can name the bottleneck.
- [ ] The docs set is complete and every ADR is defensible in 60 seconds.
- [ ] I can deliver a 10–15 minute FlowGrid deep-dive without notes and have 4 STAR stories + a career-gap answer.
- [ ] Recursion problems: 6 re-solvable in Python; Java rep done; Week 4 set `Mastered` where earned; CP-8 taken and scored.

## 15. Expected deliverables

- `flowgrid`: milestone M5 closed, ≥ 4 PRs, tag **`v1.0`** + GitHub release with notes, `cd.yml` green, deployed URL in README with screenshots, `tools/` (generator + harness) with tests/README, complete `docs/` set, `FAILURE_LOG.md` (+6 deployment scenarios), Polish milestone opened with leftovers.
- AWS: resources tagged `project=flowgrid`; cost sheet in `DEPLOYMENT.md`; EC2 stopped or budget-justified.
- CP-8 completed and scored in [`checkpoints/checkpoint-08.md`](../../checkpoints/checkpoint-08.md) / [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md).
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 60 problems, 8 Java reps, Week 4 set `Mastered` where earned, weakest-3 re-solved.
- [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md): think-aloud #4, résumé drill #2, deep-dive rehearsal score, story bank v1 (4 stories + gap answer).
- [`trackers/project-tracker.md`](../../trackers/project-tracker.md): FlowGrid **v1.0 shipped**, total hours (target 125–150), MVP/Strong tiers ticked, ADVANCED parked.
- [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): AWS (IAM/EC2/RDS/S3/CloudWatch), CI/CD, Docker, logging, k6, Python tooling updated; every FlowGrid tech now has "evidence: FlowGrid v1.0".

## 16. If you're behind / stretch

**Behind:** the order to preserve (ROADMAP §8): core architecture → engineering depth → testing →
deployment → documentation. Concretely: deploy with a manual `docker compose up` on EC2 + RDS
first; the CD pipeline can stop at "push image to GHCR" with a documented manual deploy step;
S3 export can be a stretch; k6 gets 1 + 1 runs instead of 3 + 3 but *with* methodology; the Python
harness keeps only the contention scenario. Docs: README, ARCHITECTURE, TESTING, DEPLOYMENT are
mandatory; the rest are stubs with TODOs tracked in the Polish milestone. Still tag `v1.0` only if
the deployed system passes the smoke test and the concurrency IT is green — otherwise tag `v0.9`
and finish v1.0 in Week 9's Monday block before LedgerX starts. Take CP-8 regardless.

**Stretch:** HTTPS with a real domain + Let's Encrypt; `ShedLock` for the scheduled job with two
app replicas behind Nginx (then re-run the concurrency harness across both — the DB lock still
wins, which is the point); JFR recording during a k6 run attached to `PERFORMANCE.md`; a
Grafana/Prometheus Compose profile; [92. Reverse Linked List II](https://leetcode.com/problems/reverse-linked-list-ii/)
and [21. Merge Two Sorted Lists](https://leetcode.com/problems/merge-two-sorted-lists/) recursively in Python.
