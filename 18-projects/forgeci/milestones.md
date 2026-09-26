# ForgeCI — Milestones M1–M6

> Six milestones, six weeks (14–19), 30–35 project hours each. Every milestone follows the loop
> from [`ROADMAP.md`](../../ROADMAP.md#8-project-progression): **know first → requirements →
> architectural guidance → acceptance criteria → now implement it → verification tests →
> debugging scenarios → interview questions.** The spec is in [`README.md`](./README.md); this
> file tells you what to build each week and how to prove it works. It never builds it for you.
>
> Hour estimates assume you already built FlowGrid and LedgerX (Spring, JPA, Flyway, JWT,
> Testcontainers, Compose and AWS are not new). ForgeCI weeks add ~5 h of project time,
> mostly on Saturday.

| Milestone | Week | Result | Tag |
|---|---|---|---|
| [M1](#m1--github-integration--idempotent-webhooks-week-14) | 14 | A push creates a build, idempotently | — |
| [M2](#m2--queue--workers--docker-execution-week-15) | 15 | A worker runs it in a container | `v0.1-mvp` |
| [M3](#m3--live-logs--ui-week-16) | 16 | You watch it live in the browser | CP-16 |
| [M4](#m4--timeouts-cancellation-retries-limits-health-week-17) | 17 | It survives timeouts, cancels, dead workers, floods | — |
| [M5](#m5--failure-recovery--tests--full-stack--benchmarks-week-18) | 18 | It is tested, measured, shipped, documented | `v1.0` |
| [M6](#m6--dag-pipelines--aws-week-19) | 19 | Jobs form a DAG; it runs on AWS | `v1.1` |

Before M1, create the repo (`forgeci`), the Maven parent with three modules, `docker/compose.yaml`
with Postgres + Redis, CI running `mvn verify`, and six GitHub Milestones with the issues from each
"Now implement it" list. (≈ 3 h, counted in M1.)

---

## M1 — GitHub integration + idempotent webhooks (Week 14)

### 1. What to know first

- GitHub webhooks: payload shape of `push`, the `ping` event, headers `X-GitHub-Event`, `X-GitHub-Delivery`, `X-Hub-Signature-256`, redelivery from the repo settings page, why GitHub retries. Official docs: *Webhooks → Validating webhook deliveries*.
- HMAC: what it proves (integrity + shared-secret possession), why compare in constant time, why the **raw body** matters.
- GitHub REST: `GET /user`, `GET /repos/{owner}/{repo}`, `GET /repos/{owner}/{repo}/contents/{path}?ref=<sha>` (base64 content), `POST /repos/{owner}/{repo}/hooks`. Fine-grained PATs and their permissions.
- OAuth 2.0 Authorization Code flow with GitHub OAuth Apps (`state`, scopes, token exchange). [`05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md).
- Unique constraints as idempotency primitives (you did this with `Idempotency-Key` in FlowGrid M2 and LedgerX M2) — [`04-sql-databases/04-schema-design.md`](../../04-sql-databases/04-schema-design.md), [`04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md).
- AES-GCM basics for encrypting tokens at rest (`javax.crypto.Cipher` "AES/GCM/NoPadding", 12-byte IV, 128-bit tag).
- SnakeYAML with `SafeConstructor`; why YAML loaders are a deserialization risk.
- Linux week: [`10-linux/README.md`](../../10-linux/README.md), [`10-linux/commands.md`](../../10-linux/commands.md), [`10-linux/bash-scripting.md`](../../10-linux/bash-scripting.md); Docker internals [`11-docker/README.md`](../../11-docker/README.md); OS [`14-cs-fundamentals/operating-systems.md`](../../14-cs-fundamentals/operating-systems.md) — read this week, used next week.
- Exposing localhost to GitHub for testing: `smee.io` client or `ngrok`.

### 2. Requirements

- [ ] Multi-module Maven build (`forgeci-common`, `forgeci-api`, `forgeci-worker` — worker can be an empty Spring Boot app for now); Flyway migrations `V1__init.sql` for `app_user`, `installation`, `repository`, `webhook_delivery`, `pipeline`, `build`, `job`, `step`, `audit_event` (see README §12).
- [ ] `POST /api/auth/pat` registers a user from a PAT (validated with `GET /user`), stores the token encrypted, returns an app JWT. GitHub OAuth login may be deferred to M6 (document the choice).
- [ ] `POST /api/repositories` registers a repo the user administers; generates a webhook secret; creates the GitHub webhook via API (content type `json`, events `push`) **or** returns the URL + secret for manual creation if the PAT lacks the permission.
- [ ] `POST /api/webhooks/github`: raw-body HMAC verification; `ping` → `200`; non-`push` → `202 IGNORED`; unknown repo → `404` (delivery still recorded with `REJECTED`); duplicate `X-GitHub-Delivery` → `200` with the existing build id; branch deletions (`deleted: true`) ignored.
- [ ] Build creation: fetch `.forgeci.yml` at `after` SHA; parse (`image`, `timeout`, `steps[].run/name`); validation errors → build `FAILED` (`CONFIG`) with every message; valid → `pipeline` + `build` (`QUEUED`, sequential `number` per repo) + one `job` + N `step` rows in **one transaction**. No queue yet — `QUEUED` rows just sit there.
- [ ] `GET /api/repositories/{id}/builds`, `GET /api/builds/{id}`, `GET /api/jobs/{id}` with `ProblemDetail` errors, pagination, OpenAPI annotations.
- [ ] MDC `delivery_id` / `build_id` on every log line of the webhook path.
- [ ] CI green on every PR (`mvn verify` with Testcontainers Postgres).

### 3. Architectural guidance

**Webhook receive sequence**

1. Filter/controller reads the body as `byte[]` (or `String`) — no `@RequestBody` DTO on this endpoint.
2. Extract `X-GitHub-Delivery`, `X-GitHub-Event`, `X-Hub-Signature-256`. Missing signature → `401` immediately.
3. Parse the JSON only far enough to read `repository.id` (Jackson `JsonNode`). Look up `repository` by `github_repo_id`. Unknown → record `webhook_delivery(REJECTED)` and `404`.
4. Decrypt that repo's secret, compute HMAC over the raw bytes, compare in constant time. Mismatch → `401`, record `REJECTED`, `WARN` log with delivery id.
5. **Dedupe:** `INSERT INTO webhook_delivery (delivery_id, ...)` inside a new transaction. Catch `DataIntegrityViolationException` → look up the existing row → return `200 {buildId}`. Do this *before* any GitHub API call so a redelivery costs nothing.
6. Create the build in the same transaction as the delivery row (`ACCEPTED`, `build_id` set), fetching config from GitHub first (the external call is outside the transaction: fetch → then open the transaction that inserts everything). If the fetch itself fails (GitHub 5xx), record `FAILED` delivery with the error and return `500` so GitHub retries.
7. Return `200` fast — GitHub times out deliveries at 10 s. Nothing slow belongs here.

HMAC verification (this is the whole thing — one of the few complete snippets in this project):

```java
static boolean verify(byte[] rawBody, String header, byte[] secret) throws GeneralSecurityException {
    if (header == null || !header.startsWith("sha256=")) return false;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    byte[] expected = mac.doFinal(rawBody);
    byte[] provided = HexFormat.of().parseHex(header.substring("sha256=".length()));
    return MessageDigest.isEqual(expected, provided);   // constant-time
}
```

**Transactions:** the dedupe insert and the build/job/step inserts must commit together, or a crash
between them leaves a delivery marked accepted with no build. If you want the unique-violation path
to not poison the outer transaction, do the delivery insert in `REQUIRES_NEW` and let the caller
decide — [`05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md).

**Build number:** `UPDATE repository SET next_build_number = next_build_number + 1 WHERE id = ? RETURNING next_build_number` — atomic; concurrent webhooks for the same repo serialize on that row for a microsecond. Test it with 20 threads.

**Config parser:** `YamlPipelineParser.parse(String yaml) → Result<PipelineConfig, List<String>>` in
`forgeci-common`. Validation as a list of small rule objects each returning `Optional<String>`;
the parser never throws for user errors, only for programmer errors.

**Token encryption:** an `AesGcmEncryptor` bean with `encrypt(byte[]) → (iv, ciphertext)`; the key
comes from `FORGECI_ENCRYPTION_KEY`; app refuses to start without it.

### 4. Acceptance criteria

- [ ] `curl` a signed `push` payload → `200 {"buildId": ...}`; rows in `webhook_delivery`, `pipeline`, `build`, `job`, `step`.
- [ ] Same payload again → `200` with the **same** build id; exactly one build row.
- [ ] Tampered body or wrong secret → `401`; no build.
- [ ] Repo not registered → `404`; `webhook_delivery.status = REJECTED`.
- [ ] `.forgeci.yml` with `image:` missing → build `FAILED`, `failure_kind = CONFIG`, `error_message` lists the problem; `GET /api/builds/{id}` shows it.
- [ ] A real push to a real GitHub repo (via smee/ngrok) creates a build.
- [ ] Tokens and secrets in Postgres are ciphertext; nothing secret in logs (grep for the token).
- [ ] OpenAPI at `/v3/api-docs` documents every endpoint; CI green.

### 5. Now implement it

| # | Task | Hours |
|---|---|---|
| 1 | Repo, parent POM, three modules, Compose (postgres, redis), CI workflow, GitHub milestones/issues | 3 |
| 2 | Flyway `V1__init.sql` + JPA entities + enums in `forgeci-common`; repositories | 4 |
| 3 | `AesGcmEncryptor` + tests; PAT registration endpoint + `GitHubClient` (`RestClient`) | 3 |
| 4 | JWT issue/verify filter (reuse FlowGrid design), `RepoAccessEvaluator` skeleton | 2.5 |
| 5 | Repository registration incl. webhook creation via API + manual fallback | 3 |
| 6 | `SignatureVerifier` + `WebhookController` reading raw body + `WebhookIngestService` with dedupe | 4 |
| 7 | `YamlPipelineParser` + `PipelineValidator` (minimal format) + exhaustive unit tests | 3.5 |
| 8 | `BuildService.createFromPush` (fetch config, transaction, build number allocator) | 3 |
| 9 | Read endpoints (builds list/detail, job) + `ProblemDetail` + OpenAPI annotations | 2.5 |
| 10 | smee/ngrok end-to-end with a real repo; MDC; `docs/API.md` first draft; ADR-001 | 2.5 |
| | **Total** | **31** |

### 6. Verification tests you write

| Test | Assertions |
|---|---|
| `SignatureVerifierTest.verify_knownVector_true` | Use a secret/body/signature triple you computed with `openssl dgst -sha256 -hmac` → `true` |
| `SignatureVerifierTest.verify_tamperedBody_false` | Flip one byte → `false`; missing header → `false`; wrong prefix → `false` |
| `WebhookControllerTest.webhook_badSignature_returns401` | `@WebMvcTest`, valid JSON, bad signature → `401`, service never called |
| `WebhookControllerTest.webhook_pingEvent_returns200` | `X-GitHub-Event: ping` → `200`, no build |
| `WebhookControllerTest.webhook_unknownEvent_returns202Ignored` | `issues` event → `202` |
| `WebhookIngestIT.webhook_duplicateDeliveryId_createsOneBuild` | Testcontainers Postgres; post the same delivery id 10× from 10 threads via the service → `build` count = 1, `webhook_delivery` count = 1, all callers get the same id |
| `WebhookIngestIT.webhook_unregisteredRepo_returns404AndRecordsRejected` | Delivery row exists with `REJECTED`, no build |
| `WebhookIngestIT.webhook_configInvalid_createsFailedBuildWithMessages` | Missing `image` and empty `steps` → both messages present, `status = FAILED`, `failure_kind = CONFIG`, no job rows |
| `WebhookIngestIT.webhook_validPush_createsBuildJobSteps` | 3 steps in YAML → 3 `step` rows ordered by `ordinal`, job `QUEUED`, build `number = 1` then `2` |
| `BuildNumberAllocatorIT.allocate_20Threads_uniqueSequential` | 20 concurrent allocations → numbers 1..20, no duplicates |
| `YamlPipelineParserTest.*` | One test per rule (bad image regex, timeout > 60m, empty run, unknown top-level key, YAML syntax error, non-map root); `SafeConstructor` blocks `!!java.lang.ProcessBuilder` tags |
| `AesGcmEncryptorTest.roundTrip_and_differentIvPerCall` | decrypt(encrypt(x)) = x; two encryptions of x differ; tampered ciphertext throws `AEADBadTagException` |

Plus a manual checklist in the PR: smee → real push → build visible.

### 7. Debugging scenarios

1. **Signature never matches in Spring but matches in `openssl`.** You are hashing the re-serialized DTO, not the raw bytes; or a filter consumed the `InputStream`. Compare `Content-Length` with your byte count.
2. **Duplicate deliveries create two builds under load.** Dedupe insert and build insert are in different transactions and you check-then-insert. Rely on the unique constraint, not a `SELECT`.
3. **Second delivery returns 500 "current transaction is aborted".** Unique violation inside the outer transaction; Postgres aborts the whole transaction. Use `REQUIRES_NEW` for the delivery insert or check the violation in its own boundary.
4. **`GET /contents` returns 404 for a private repo** although the PAT works for `GET /user`. Fine-grained PAT lacks `Contents: read` on that repo — inspect scopes; return a `CONFIG`-style error that names the missing permission.
5. **Webhook created but GitHub shows red deliveries.** Your endpoint takes > 10 s (GitHub fetch inside) or returns 500 on `ping`. Check the delivery response tab in GitHub.
6. **JWT works for REST but the future SSE endpoint won't see it.** `EventSource` cannot set headers — move the JWT to an httpOnly cookie now (see README §17).

### 8. Interview questions

- Why verify the webhook signature over the raw body, and what breaks if you don't?
- Why is a unique constraint a better idempotency mechanism than checking for existence first?
- What is the difference between an OAuth token, a PAT and a JWT in ForgeCI? Which one leaves the server?
- Why is `MessageDigest.isEqual` used instead of `Arrays.equals`?
- What happens if GitHub delivers the webhook but your DB commit fails after you returned 200? (You didn't — you return after commit. Say why.)
- Why does SnakeYAML need `SafeConstructor` for user-supplied YAML?

---

## M2 — Queue + workers + Docker execution (Week 15)

### 1. What to know first

- **Java concurrency deep**: `ExecutorService`, `CompletableFuture`, `ReentrantLock`, `Semaphore`, atomics, `ScheduledExecutorService`, thread interruption, virtual threads — [`01-java/07-concurrency.md`](../../01-java/07-concurrency.md), [`14-cs-fundamentals/concurrency.md`](../../14-cs-fundamentals/concurrency.md). Producer/consumer with `BlockingQueue` first, in memory, then map every concept to Redis.
- Redis lists and blocking ops: `LPUSH`, `BLMOVE src dst RIGHT LEFT timeout` (Redis ≥ 6.2), `LREM`, `LLEN`, `LRANGE`; key TTLs (`SET k v EX 30`, `EXPIRE`); Lettuce and Spring Data Redis `ListOperations` — [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md). Read the README §9 decision table and be able to argue it.
- Docker Engine API and docker-java: `createContainerCmd`, `HostConfig`, `startContainerCmd`, `execCreateCmd`/`execStartCmd` with `ResultCallback`, `inspectExecCmd` (exit code), `killContainerCmd`, `removeContainerCmd`, `pullImageCmd`. Read the docker-java README and one integration test in its repo ([`21-debugging-code-reading/method.md`](../../21-debugging-code-reading/method.md) — this is a "read a library" drill).
- What a container is: namespaces, cgroups, the difference between `docker run` and `docker exec`, why PID 1 matters, SIGTERM vs SIGKILL, exit codes 0/1/137/143 — [`14-cs-fundamentals/operating-systems.md`](../../14-cs-fundamentals/operating-systems.md), [`10-linux/README.md`](../../10-linux/README.md).
- Bind mounts vs volumes; why a path inside the worker container differs from the host path Docker sees — [`11-docker/README.md`](../../11-docker/README.md), [`11-docker/compose.md`](../../11-docker/compose.md).
- `ProcessBuilder` for running `git clone` with a timeout and captured output.
- Networking basics for "worker talks to Redis/Postgres, container talks to Maven Central" — [`14-cs-fundamentals/networking.md`](../../14-cs-fundamentals/networking.md).

### 2. Requirements

- [ ] `JobQueue` interface in `forgeci-common`; `RedisListJobQueue` implementation: `enqueue(jobId)`, `Optional<Lease> poll(Duration)`, `ack(lease)`, `nack(lease)`; key names centralized in `RedisKeys`.
- [ ] API enqueues after the build transaction commits (`TransactionSynchronization.afterCommit` or an application event handled `AFTER_COMMIT`).
- [ ] Worker app (`forgeci-worker`, no web server; Actuator on 8081): registers itself in `worker`, runs `capacity` loop threads, each: `BLMOVE` → write lease key (TTL 30 s) → `job: QUEUED→LEASED` (only if still `QUEUED`; a compare-and-set `UPDATE ... WHERE status='QUEUED'`) → execute → terminal state → `ack`.
- [ ] `DockerJobExecutor`: workspace dir → clone on host (`--depth 1`, checkout SHA, 2-min timeout) → pull image if absent → create container (limits, labels, bind mount, `sleep infinity`) → start → for each step `exec sh -c` capturing output and exit code → stop at first non-zero → **`finally`**: kill, remove (force), delete workspace.
- [ ] Steps recorded with `status`, `exit_code`, timings; job `SUCCEEDED`/`FAILED` (`APP`) with `exit_code`; build status derived.
- [ ] Output captured to `log_chunk` rows (no streaming yet — a `LogPump` that batches and inserts is fine; pub/sub comes in M3).
- [ ] Infra errors (pull failure, create/start failure, daemon unreachable) classified `INFRA` and — for now — the job is `nack`ed back to the queue with `attempt+1` up to 3 (the backoff and lease-expiry recovery are M4).
- [ ] Startup sweep: remove containers labelled `forgeci.job` not owned by a running job on this worker.
- [ ] Compose: `worker` service with the Docker socket and workspace mount; `docker compose up --scale worker=2` works.
- [ ] **MVP reached**: push to the demo repo → job runs `mvn -q -B test` inside `maven:3.9-eclipse-temurin-21` → `SUCCEEDED` with exit code 0 visible via `GET /api/jobs/{id}`. Tag `v0.1-mvp`.

### 3. Architectural guidance

**Worker loop (per slot thread)**

```
while (running) {
    Optional<Lease> lease = queue.poll(Duration.ofSeconds(5));   // BLMOVE queue:jobs processing:<workerId> RIGHT LEFT 5
    if (lease.isEmpty()) continue;                                 // timeout → loop (lets shutdown flag be checked)
    MDC.put("job_id", lease.jobId()); MDC.put("worker_id", workerId);
    try {
        if (!jobs.tryLease(lease.jobId(), workerId)) { queue.ack(lease); continue; } // job cancelled/not QUEUED → drop
        heartbeats.start(lease);                                   // M4: refresh lease TTL every 10 s
        JobResult r = executor.execute(lease.jobId());             // container lifecycle inside, finally-cleanup inside
        jobs.complete(lease.jobId(), r);                           // terminal state + build status, one transaction
        queue.ack(lease);                                          // LREM processing:<workerId> 0 jobId ; DEL lease key
    } catch (InfraException e) {
        jobs.recordInfraFailure(lease.jobId(), e);                 // attempt+1 or FAILED(INFRA) if exhausted
        queue.nack(lease);                                         // move back to queue:jobs (M4: delayed via ZSET)
    } finally { heartbeats.stop(lease); MDC.clear(); }
}
```

The `BLMOVE` call with Spring Data Redis (Lettuce):

```java
// RedisTemplate<String, String> redis; RedisKeys keys
String jobId = redis.opsForList().move(
        keys.queue(), ListOperations.Direction.RIGHT,
        keys.processing(workerId), ListOperations.Direction.LEFT,
        Duration.ofSeconds(5));                     // null on timeout
if (jobId != null) {
    redis.opsForValue().set(keys.lease(jobId), workerId, Duration.ofSeconds(30));
}
```

Why a **per-worker processing list**: the job id is never "in flight" without being in some list, so
a crash between `BLMOVE` and `ack` leaves evidence (`processing:<deadWorker>`) that M4's recovery
scans. Why a **lease key with TTL**: the processing list says *who* has it; the lease says *whether
they are still alive*. Two separate facts.

**Compare-and-set on lease:** `UPDATE job SET status='LEASED', worker_id=?, leased_at=now(), lease_expires_at=now()+interval '30 s' WHERE id=? AND status='QUEUED'` — check the updated row count. This is your second guard against double execution (the first is `BLMOVE` atomicity) and it handles a job cancelled while queued (M4).

**Executor structure:** `execute(jobId)` is a straight-line method with one big `try/finally`; each
phase throws a typed exception (`CloneException`, `ImagePullException`, `ContainerStartException`
extend `InfraException`; `StepFailedException` carries exit code and is `APP`). Cleanup helpers each
swallow and log their own exceptions. Pass a `Consumer<LogChunk>` sink into the executor so M3 can
swap the sink without touching the executor.

**Bind-mount path trap:** the worker runs in a container and creates `/var/forgeci/workspaces/<job>`;
but Docker (on the host) must be told the *host* path `/home/you/forgeci/workspaces/<job>`. Configure
both (`forgeci.workspaces.local` and `forgeci.workspaces.host`) and map between them. This will cost
you an hour if you don't read this paragraph.

**Executor threads:** `Executors.newFixedThreadPool(capacity)` (or virtual threads — one per slot) —
the loop above *is* the thread body; graceful shutdown (M4) sets `running=false` and waits.

### 4. Acceptance criteria

- [ ] `docker compose up --scale worker=2`; push → exactly one worker logs "leased job"; `GET /api/jobs/{id}` → `SUCCEEDED`, `exit_code = 0`, steps timed; `docker ps -a` shows no `forgeci.job` containers; workspace dir gone.
- [ ] A step `exit 3` → job `FAILED`, `failure_kind = APP`, `exit_code = 3`, later steps `SKIPPED`, **no retry** (`attempt = 1`).
- [ ] Image `nope/nothing:1` → `FAILED (CONFIG)` (manifest unknown) — not retried.
- [ ] `docker pause` the Docker daemon? No — `systemctl stop docker` on a spare VM or point the worker at a wrong socket → job classified `INFRA`, requeued, `attempt = 2`, picked up by the other worker.
- [ ] Log chunks for the job are in `log_chunk` in order with correct stream tags.
- [ ] Redis after a run: `LLEN queue:jobs = 0`, `LLEN processing:<w> = 0`, no lease keys.
- [ ] Tag `v0.1-mvp`.

### 5. Now implement it

| # | Task | Hours |
|---|---|---|
| 1 | In-memory `BlockingQueue` producer/consumer spike (30 min) then `JobQueue` interface + `RedisListJobQueue` + Testcontainers Redis tests | 4 |
| 2 | Enqueue after commit in API; `RedisKeys`; `LLEN` gauge | 1.5 |
| 3 | Worker app skeleton: config, `DockerClient` bean, `WorkerRegistrar`, `WorkerLoop` with slot threads | 4 |
| 4 | `JobStateService.tryLease/complete/recordInfraFailure` with CAS updates + `BuildStatusResolver` | 3 |
| 5 | `WorkspaceManager` + `GitCloner` (`ProcessBuilder`, timeout, token masking) | 3 |
| 6 | `ContainerFactory` (limits, labels, mount, `sleep infinity`), image pull with per-image mutex | 3.5 |
| 7 | `StepRunner` (`exec`, `ResultCallback` → sink, exit code), `DockerJobExecutor` with `finally` cleanup, exception taxonomy | 5 |
| 8 | `LogPump` v1 (batch insert to `log_chunk`) | 2 |
| 9 | Startup container sweep; Compose `worker` service, socket + workspace mounts, host-path mapping | 2.5 |
| 10 | Docker-tagged worker tests (`alpine` echo / exit 3 / bad image); end-to-end on demo repo; ADR-002, ADR-004, ADR-005; tag | 3.5 |
| | **Total** | **32** |

### 6. Verification tests you write

| Test | Assertions |
|---|---|
| `RedisListJobQueueIT.poll_emptyQueue_returnsEmptyAfterTimeout` | ~1 s timeout → empty, ≥ 1 s elapsed |
| `RedisListJobQueueIT.poll_movesIdToProcessingListAndWritesLease` | `LRANGE processing:<w>` contains id; lease key TTL between 25 and 30 s |
| `RedisListJobQueueIT.ack_removesFromProcessingAndDeletesLease` | both gone |
| `RedisListJobQueueIT.nack_returnsToQueueTail` | id back in `queue:jobs`, not in processing |
| `WorkerLoopIT.queue_twoWorkersOneJob_executesOnce` | Two `WorkerLoop` instances (distinct worker ids) share one Redis; enqueue 1 job; fake executor increments an `AtomicInteger` and sleeps 500 ms; after 3 s → count = 1, job `SUCCEEDED`, both processing lists empty |
| `WorkerLoopIT.queue_50Jobs4Slots_allExecutedExactlyOnce` | Set of executed ids has size 50, no duplicates, max concurrent (tracked with an atomic high-water mark) ≤ 4 |
| `JobStateServiceIT.tryLease_alreadyLeased_returnsFalse` | Second CAS returns false, `worker_id` unchanged |
| `DockerJobExecutorDockerTest.job_echo_succeedsWithChunk` (`@Tag("docker")`) | `alpine:3`, step `echo hello` → `SUCCEEDED`, chunk content contains `hello\n` on `STDOUT` |
| `DockerJobExecutorDockerTest.job_nonZeroExit_markedFailedNotRetried` | step `exit 3` → `FAILED`, `APP`, `exit_code = 3`, `attempt = 1`, second step `SKIPPED` |
| `DockerJobExecutorDockerTest.job_stderr_taggedSeparately` | `echo err 1>&2` → chunk on `STDERR` |
| `DockerJobExecutorDockerTest.job_unknownImage_configFailure` | `does-not-exist-xyz:1` → `CONFIG`, no container created |
| `DockerJobExecutorDockerTest.cleanup_runsWhenStepThrows` | Inject a sink that throws → container removed (`inspect` → `NotFoundException`), workspace deleted |
| `DockerJobExecutorDockerTest.workspace_isClonedAtSha` | Local bare repo fixture; step `git rev-parse HEAD` output equals the requested SHA |
| `GitClonerTest.clone_timeout_throwsInfra` | Point at a non-routable IP (`10.255.255.1`) with 2-s timeout → `CloneException` within ~2 s |

### 7. Debugging scenarios

1. **`BLMOVE` returns immediately with null forever.** Lettuce command timeout (default 60 s) is *shorter* than … no — it is longer; but if you set `spring.data.redis.timeout=1s`, a 5-s block throws `RedisCommandTimeoutException`. Blocking commands need a timeout longer than the block. Use a dedicated connection/`RedisTemplate` for blocking ops.
2. **Both workers execute the job.** You used `LPOP` + `LPUSH` instead of `BLMOVE`, or your CAS `UPDATE` has no `WHERE status='QUEUED'`. Check `processing:*` lists.
3. **Container starts but `exec` says "no such file: sh".** Distroless image without a shell. Document: images must contain `/bin/sh`; classify as `CONFIG`.
4. **"invalid mount config: bind source path does not exist".** The host-path mapping (see guidance). `docker inspect` the worker to see the real host path.
5. **Steps hang after the command finished.** You wait on the `ResultCallback` but the container's main process (`sleep infinity`) keeps the stream open — you attached to the *container* instead of the *exec*. Attach to the exec instance.
6. **Exit code always 0.** You read it before `exec` finished; call `inspectExecCmd` only after `awaitCompletion`.
7. **`docker ps -a` fills with dead containers.** Cleanup skipped on an exception path — put each cleanup in its own `try`; add the label sweep.
8. **Clone works locally, fails in Compose with "could not read Username".** Token not passed through env inside the worker container; check that `GIT_TERMINAL_PROMPT=0` produces a fast failure instead of a hang.

### 8. Interview questions

- Walk me through what happens between `LPUSH` in the API and `SUCCEEDED` in the DB. Which steps are atomic?
- What guarantees that one job is executed by one worker? What are the two independent guards?
- What does at-least-once mean here, and why is it acceptable for a CI job?
- Why one container per job and `exec` per step instead of one container per step?
- Why clone on the host and not in the container? What would you lose/gain?
- What does `finally` cleanup have to survive? Give three ways it could still leak.
- What is the difference between Docker's `stop` and `kill`, and which one should a timeout use?
- Why does a `BlockingQueue` in one JVM not solve this problem?

---

## M3 — Live logs + UI (Week 16)

### 1. What to know first

- SSE protocol: `text/event-stream`, `id:`/`event:`/`data:`/`retry:` fields, blank-line delimiter, browser `EventSource` reconnect with `Last-Event-ID`. Spring MVC `SseEmitter` (`send(SseEmitter.event().id(..).name(..).data(..))`, `onCompletion`, `onTimeout`, `onError`) and `spring.mvc.async.request-timeout`. Read README §10 and be able to defend SSE.
- Redis pub/sub: `PUBLISH`, `SUBSCRIBE`, at-most-once, no persistence, `RedisMessageListenerContainer` in Spring Data Redis — [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md).
- Backpressure basics: what happens when a consumer is slower than a producer, bounded queues, drop policies.
- HTTP streaming through proxies: `nginx proxy_buffering off`, `X-Accel-Buffering: no`, `Cache-Control: no-cache` — [`06-rest-apis/http-for-apis.md`](../../06-rest-apis/http-for-apis.md).
- React for data-heavy live views: `useEffect` cleanup, `useRef` for an append-only buffer, virtualization for long logs, `EventSource` in TS — [`08-react/02-hooks.md`](../../08-react/02-hooks.md), [`08-react/04-api-integration-auth.md`](../../08-react/04-api-integration-auth.md), [`07-javascript-typescript/02-async-javascript.md`](../../07-javascript-typescript/02-async-javascript.md).
- UTF-8: why you cannot cut a byte stream anywhere — multi-byte characters straddle chunk boundaries.

### 2. Requirements

- [ ] Worker `LogPump` v2: chunk boundaries at 4 KB or 200 ms, monotonically increasing `seq` per job (starts at the max existing seq + 1 on retry attempts), insert batch → then `PUBLISH logs:<jobId> <json>`; `SYSTEM` chunks for phases ("Pulling image…", "Cloning…", "Step 1/3: mvn test") and results.
- [ ] Worker publishes job status changes on `builds:<buildId>`.
- [ ] API `GET /api/jobs/{id}/logs/stream` (SSE): replay from `Last-Event-ID` (or `?after=`) out of Postgres, then live from Redis; emits `log`, `status`, `end`; ends when the job is terminal and all chunks are sent; heartbeat comment `: ping` every 15 s to keep proxies open.
- [ ] API `GET /api/builds/{id}/events` (SSE) for status changes.
- [ ] `SseRegistry`: one Redis subscription per job, N emitters; removal on completion/timeout/error; gauge of connected clients.
- [ ] React UI (Vite + TS): login (PAT / OAuth), repository list + register form, build list with status badges and auto-refresh via SSE, build detail (jobs, steps, timings), job detail with **live log** (auto-scroll with a "pause" toggle, stderr tinted, ANSI stripped), reconnect indicator.
- [ ] nginx config in `ui` container with SSE-safe proxying.
- [ ] `docs/ARCHITECTURE.md` first full draft with the log pipeline sequence; ADR-003.
- [ ] **CP-16** target: push → queued → running → live log → green, all in the browser.

### 3. Architectural guidance

**Log pipeline sequence**

1. `StepRunner` receives a Docker `Frame` (bytes + stream type) → `LogPump.accept(frame)`.
2. `LogPump` appends to a per-stream `ByteArrayOutputStream`; a flush is triggered by size ≥ 4 KB or by a 200-ms scheduled tick or by end-of-step. Cut only at a UTF-8 boundary (walk back from the cut point while the byte is a continuation byte `10xxxxxx`; keep the remainder for the next chunk).
3. Flush: `seq = ++counter`; `INSERT log_chunk (job_id, seq, attempt, stream, content, created_at)` (JDBC batch when multiple chunks are ready); then `PUBLISH logs:<jobId> {"seq":..,"stream":..,"content":..}`.
4. API `RedisSubscriber` receives the message and hands it to `SseRegistry.dispatch(jobId, event)` which sends to every emitter for that job on a dedicated executor.
5. Browser `EventSource` gets `id: <seq>` with each event; on network loss it reconnects automatically with `Last-Event-ID: <seq>`.
6. On (re)connect: `LogReplayService` — subscribe to the channel **first** (buffer incoming messages), then read `SELECT ... WHERE job_id=? AND seq>? ORDER BY seq`, send them, then drain the buffer skipping `seq ≤ last sent`, then switch to live. Subscribe-before-read closes the race where a chunk is published between the read and the subscribe. Dedupe by `seq` on the client too — cheap insurance.
7. When the job is terminal (check the DB *after* replay) send `status` then `end` and complete the emitter.

An emitter sketch (the shape, not the implementation):

```java
@GetMapping(value = "/api/jobs/{id}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@PathVariable UUID id,
                         @RequestHeader(value = "Last-Event-ID", required = false) Long lastId,
                         @RequestParam(required = false) Long after) {
    long from = lastId != null ? lastId : (after != null ? after : 0L);
    SseEmitter emitter = new SseEmitter(0L);              // no timeout; registry manages lifetime
    emitter.onCompletion(() -> registry.remove(id, emitter));
    emitter.onTimeout(emitter::complete);
    emitter.onError(e -> registry.remove(id, emitter));
    registry.add(id, emitter);                             // subscribes to logs:<id> if first client
    replay.replayThenLive(id, from, emitter);              // runs on an executor, never on the request thread
    return emitter;
}
```

**Why persist before publish:** pub/sub is fire-and-forget. If the API is down when a chunk is
published, nobody hears it — but it is in Postgres, and the next reconnect replays it. The DB is the
truth; pub/sub is a latency optimisation.

**Retry attempts and seq:** on an infra retry, the new attempt continues the sequence (`seq` starts
at `max(seq)+1`) and the log shows a `SYSTEM` chunk explaining the retry. The viewer never needs to
know about attempts to replay correctly.

**UI:** `useJobLogStream(jobId)` hook owning the `EventSource`; append chunks into a `useRef` array and
batch state updates with `requestAnimationFrame` (rendering per chunk at 50 chunks/s is janky);
`react-window` or manual windowing when > 5,000 lines. Cookie auth means `new EventSource(url, {withCredentials: true})`.

### 4. Acceptance criteria

- [ ] Push to the demo repo → open the job page while it runs → Maven output appears within 1 s of the worker seeing it (compare timestamps of chunk `created_at` and browser receipt in the console).
- [ ] Kill the browser's network (DevTools offline) for 5 s during a run → on reconnect, no missing lines and no duplicated lines (verify against `GET /api/jobs/{id}/logs`).
- [ ] Two tabs on the same job both receive all chunks; closing one does not affect the other; `forgeci_sse_clients` gauge decrements.
- [ ] Restart the API mid-job → browser reconnects, replay fills the gap.
- [ ] A finished job's page shows the complete log immediately and then `end`.
- [ ] Multi-byte output (`echo "héllo ✓"` in a 5-byte chunk test) renders correctly.
- [ ] Build list updates status badges without refresh.
- [ ] Through nginx in Compose the stream is not buffered (chunks arrive live, not at job end).

### 5. Now implement it

| # | Task | Hours |
|---|---|---|
| 1 | `LogPump` v2: chunker (size/time/UTF-8 boundary) as a pure class + tests; seq continuity across attempts | 4 |
| 2 | Batch insert + `PUBLISH`; `SYSTEM` chunks; status publish on `builds:<id>` | 2.5 |
| 3 | API `RedisSubscriber` (`RedisMessageListenerContainer`), `SseRegistry` with per-job subscription refcount, executor for sends, gauges | 4 |
| 4 | `LogReplayService` (subscribe-before-read, buffer, dedupe, terminal detection, `end`) + SSE controller + `: ping` heartbeat | 4 |
| 5 | Build events SSE endpoint | 1.5 |
| 6 | Vite + React + TS scaffold, router, API client with cookie auth, login page | 3 |
| 7 | Repository list/register, build list with badges + SSE refresh, build detail | 4 |
| 8 | Job page: `useJobLogStream`, virtualized log view, auto-scroll/pause, stderr tint, ANSI strip, reconnect indicator | 5 |
| 9 | nginx SSE config, `ui` Dockerfile, Compose wiring | 1.5 |
| 10 | Contract test for replay; `ARCHITECTURE.md` draft; ADR-003; CP-16 self-check | 3 |
| | **Total** | **32.5** |

### 6. Verification tests you write

| Test | Assertions |
|---|---|
| `LogChunkerTest.flush_atSizeThreshold` | 4,097 bytes in → first chunk ≤ 4,096, remainder buffered |
| `LogChunkerTest.flush_neverSplitsUtf8Sequence` | `"é".repeat(3000)` bytes with a 4,096 limit → every chunk decodes without `�`; concatenation equals input |
| `LogChunkerTest.flush_onTimeTick` | 10 bytes, tick after 200 ms → one chunk |
| `LogPumpIT.seq_continuesAcrossAttempts` | Attempt 1 writes seq 1..5; attempt 2 starts at 6 |
| `LogPumpIT.chunk_persistedBeforePublished` | A subscriber that immediately reads the DB on message finds the row |
| `SseControllerTest.stream_contentTypeAndLastEventIdParsed` | `@WebMvcTest`: header `Last-Event-ID: 40` → replay service called with 40; `Content-Type: text/event-stream` |
| `SseReplayContractIT.sse_reconnectWithLastEventId_replaysMissedChunks` | Persist chunks 1..100 for a `RUNNING` job; connect with `Last-Event-ID: 40`; publish 101 and 102 live; mark job `SUCCEEDED`; client receives exactly ids 41..102 in order, then `status`, then `end`; no duplicates |
| `SseReplayContractIT.sse_publishDuringReplay_notLost` | While replay of 1..1000 is in progress, publish 1001 → received exactly once after 1000 |
| `SseRegistryTest.lastClientRemoved_unsubscribesChannel` | Refcount 2 → 0 → `unsubscribe` called once |
| `SseRegistryTest.slowClient_droppedNotBlocking` | Emitter whose `send` blocks → other clients still receive; blocked one removed after the bounded queue overflows |
| UI `LogView.test.tsx: appendsInOrderAndDedupesBySeq` | Feed seq 1,2,2,3 → 3 lines |
| UI `useJobLogStream.test.tsx: reconnectSetsIndicator` | Mock `EventSource` error → indicator visible, `readyState` reflected |

### 7. Debugging scenarios

1. **Logs arrive all at once when the job ends.** Buffering: nginx (`proxy_buffering off`), Spring compression filter (`server.compression.enabled` — exclude `text/event-stream`), or your own `BufferedWriter`. Test with `curl -N` directly against the API first, then through nginx.
2. **Lines missing after reconnect.** Subscribe-after-read race, or the client's `Last-Event-ID` is the *build* event id, not the chunk seq (two SSE endpoints, two id spaces).
3. **Duplicate lines after reconnect.** Replay sends `seq ≥ last` instead of `>`; or you send buffered live messages without filtering.
4. **`IllegalStateException: ResponseBodyEmitter has already completed`.** A send after `complete()`; guard sends in the registry and remove emitters on the first failure.
5. **API thread pool exhausted with 20 tabs open.** Replay runs on the request thread; move it to an executor (or enable virtual threads and measure).
6. **`�` characters in the browser.** Chunk cut mid-UTF-8 — see the chunker rule.
7. **Redis `SUBSCRIBE` silently stops delivering after a Redis restart.** `RedisMessageListenerContainer` re-subscribes, but check `spring.data.redis.lettuce` reconnect settings; add a test in M5 (failure scenario 6).
8. **Memory grows on the API.** Emitters never removed (missing `onCompletion`); `SseRegistry` map leaks jobs; check with `jcmd GC.class_histogram`.

### 8. Interview questions

- Why SSE and not WebSockets for logs? What would make you switch?
- What does `Last-Event-ID` give you and what did you have to build on top of it?
- Why persist a chunk before publishing it? What if you did the opposite?
- Describe the race between replay and live delivery and how you closed it.
- Why chunk at 4 KB / 200 ms rather than per line? What does it do to DB write rate and latency?
- What happens to a slow browser client? How do you avoid it slowing the worker?
- Where would this design break at 10,000 concurrent viewers?

---

## M4 — Timeouts, cancellation, retries, limits, health (Week 17)

### 1. What to know first

- Failure taxonomy (README §25): app vs infra vs config vs timeout vs cancelled — and why "retry" is a per-kind decision.
- Leases and heartbeats as a liveness protocol; why a lease needs a TTL *and* a renewer; clock assumptions (all TTLs enforced by Redis's clock — no cross-host time comparison needed if you use Redis TTLs).
- Redis Lua scripting for atomic check-and-act (`EVAL`, `EVALSHA`, `DefaultRedisScript`); `INCR`/`DECR`, `ZADD`/`ZRANGEBYSCORE` for delayed requeue — [`04-sql-databases/redis.md`](../../04-sql-databases/redis.md).
- Spring `@Scheduled` with `ShedLock`-style single-runner concerns (two API instances must not both run the recovery sweep — or make it idempotent so it doesn't matter) — [`05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md).
- Graceful shutdown: `SIGTERM`, `SmartLifecycle`/`@PreDestroy`, `server.shutdown=graceful`, Compose `stop_grace_period`, Docker's 10-s default before `SIGKILL` — [`10-linux/README.md`](../../10-linux/README.md).
- Java: `ScheduledExecutorService` for watchdogs, `Thread.interrupt()` semantics, `Future.cancel(true)`, `CompletableFuture.orTimeout` — [`01-java/07-concurrency.md`](../../01-java/07-concurrency.md).
- Exponential backoff with jitter; why bounded retries and a max attempts count.

### 2. Requirements

- [ ] **Timeout**: `TimeoutWatchdog` per job; at `timeout_seconds` → `killContainer`, job `TIMED_OUT`, `SYSTEM` chunk, cleanup; no retry.
- [ ] **Cancel queued**: `POST /jobs/{id}/cancel` → CAS `QUEUED→CANCELLED` + `LREM queue:jobs 0 id`; if CAS fails because it is already `LEASED`/`RUNNING`, fall through to the running path.
- [ ] **Cancel running**: API sets `SET job:<id>:cancel 1 EX 3600` and `PUBLISH control:<workerId> cancel:<jobId>`; worker `CancelWatcher` (subscription + a 2-s poll fallback) → `stopContainer` (SIGTERM, 10 s grace, then kill) → `CANCELLED` → cleanup. Also observed by the worker *before* starting the container (`LEASED` path).
- [ ] **Build cancel** cancels all non-terminal jobs; audit event with actor.
- [ ] **Retry policy**: `APP` never; `TIMED_OUT` never; `CONFIG` never; `INFRA` up to `max_attempts` (3) with backoff `5 s × 2^(attempt−1)` ± 20 % jitter via a delayed queue (`ZADD queue:delayed <readyAtEpochMs> jobId`; a worker-side or API-side mover pushes ready ids to `queue:jobs`); exhausted → `FAILED (INFRA)`.
- [ ] **Heartbeats**: worker refreshes every lease it holds every 10 s (`EXPIRE job:<id>:lease 30`) and upserts `worker.last_heartbeat_at`; `DEAD` if no heartbeat for 60 s.
- [ ] **Orphan recovery** (API `@Scheduled` every 15 s): for each `processing:<workerId>` list, for each id whose lease key is **absent** → treat as worker loss: `INFRA` retry (or fail if exhausted), `LREM` from that list, requeue; also DB sweep: jobs `LEASED/RUNNING` with `lease_expires_at < now() − 60 s` and no lease key → same path. Plus "`QUEUED` in DB but in no Redis list for > 60 s" → re-enqueue (covers the crash between commit and `LPUSH`).
- [ ] **Per-project concurrency limit**: Lua check-and-increment on `limit:<repoId>` before starting a job; if at limit → release lease, push job back (to the *tail*, with a small delay to avoid hot-looping) and record nothing (attempt unchanged); decrement in `finally`; recovery sweep also decrements for orphaned jobs; limit editable via API.
- [ ] **Graceful shutdown** (worker): on `SIGTERM` stop polling, wait up to `stop_grace_period − 5 s` for running jobs, then for still-running jobs: kill container, `nack` with `INFRA` retry ("worker shutdown"), exit 0.
- [ ] **Health**: worker Actuator health includes Docker (`ping`), Redis, DB; API health includes Redis, DB; `GET /api/workers` shows status derived from heartbeat age.

### 3. Architectural guidance

**Lua check-and-increment** (atomic; a plain `GET` + `INCR` races with another worker):

```lua
-- KEYS[1] = limit:<repoId>:running   ARGV[1] = max   ARGV[2] = ttl seconds
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
if current >= tonumber(ARGV[1]) then
  return 0
end
redis.call('INCR', KEYS[1])
redis.call('EXPIRE', KEYS[1], ARGV[2])   -- safety: never stuck forever
return 1
```

Release is a guarded `DECR` (never below 0 — another tiny Lua). The TTL is a safety net for a crashed
worker that never released; the recovery sweep releases explicitly and the TTL catches the rest.
Trade-off: if the TTL is shorter than a job, the counter drifts low — set TTL ≥ max job timeout
(60 min + margin) and *refresh it in the heartbeat*.

**Cancellation sequence (running job)**

1. API: CAS `RUNNING → CANCELLING`? Keep it simpler: set `cancel_requested = true` in DB, set the Redis flag, publish control message, return `202`.
2. Worker `CancelWatcher` (one per running job): on message *or* on poll seeing the flag → `container.stop(10s)`; the step reader gets EOF; executor sees `cancelled` flag and throws `CancelledException` (not `APP`, not `INFRA`).
3. Worker: job `CANCELLED`, `SYSTEM` chunk "cancelled by <actor>", ack, cleanup, `DECR` limit.
4. If the worker never sees it (dead), the recovery sweep sees `cancel_requested = true` and marks `CANCELLED` instead of retrying.

**Orphan recovery sequence** — the sweep must be **idempotent** (two API instances may run it; the
same orphan may be seen twice): every action is a CAS `UPDATE ... WHERE status IN ('LEASED','RUNNING') AND worker_id = ?` plus an `LREM`; if the CAS updates 0 rows, skip. Log every recovery at `WARN` with job id, dead worker, attempt.

**Timeout vs cancel vs kill:** timeout → `kill` (SIGKILL — the job is misbehaving); cancel → `stop`
(SIGTERM then SIGKILL after 10 s — let Maven flush). Both routes end in the same `finally`.

**Job timeout is the whole job**, including clone and image pull? Decide: clone/pull have their own
infra timeouts (2 min / 5 min); `timeout_seconds` covers step execution. Document it in the YAML reference.

**Graceful shutdown ordering** (worker): `SmartLifecycle.stop()` with high phase: (1) `running=false`
so loops exit after the current `BLMOVE` returns (≤ 5 s); (2) `await` executor termination up to
grace; (3) for leftovers: kill + nack; (4) close Docker client, Redis. Set Compose `stop_grace_period: 90s`
for workers.

### 4. Acceptance criteria

- [ ] Job with `timeout: 5s` and step `sleep 60` → `TIMED_OUT` within ~6 s; container gone; `attempt = 1`.
- [ ] Cancel a queued job → `CANCELLED`, `LLEN queue:jobs` decremented, never runs.
- [ ] Cancel a running job (`sleep 60`) → container stops within ~10 s, job `CANCELLED`, `SYSTEM` chunk names the actor, cleanup done.
- [ ] `docker kill forgeci-worker-1` while it runs a job → within ~45 s the job is back in `QUEUED` with `attempt = 2`, a `SYSTEM` chunk explains, worker-2 runs it, it `SUCCEEDED`; worker-1 shows `DEAD` in `/api/workers`.
- [ ] Three builds pushed to a repo with limit 2 → third stays `QUEUED` until one finishes (never `LEASED` for more than a moment).
- [ ] `docker compose stop worker` (SIGTERM) during a 20-s job → job finishes, worker exits 0; during a 5-min job → job requeued as `INFRA` "worker shutdown", exits within grace.
- [ ] Infra failure (stop Docker daemon on the worker host / point at a bad socket for one worker) → attempt 2 after ~5 s, attempt 3 after ~10 s, then `FAILED (INFRA)` — visible in the job's `SYSTEM` chunks and `attempt` field.
- [ ] `/actuator/health` on the worker turns `DOWN` when Docker is unreachable.

### 5. Now implement it

| # | Task | Hours |
|---|---|---|
| 1 | `TimeoutWatchdog` + `TIMED_OUT` path + tests | 3 |
| 2 | Cancel API (queued + running + build), audit events, Redis flag + control channel | 3.5 |
| 3 | Worker `CancelWatcher` (subscribe + poll), `CancelledException`, stop-vs-kill | 3 |
| 4 | `RetryPolicy` (pure, unit-tested), delayed queue (`ZSET` + mover), backoff/jitter, `max_attempts` | 3.5 |
| 5 | `LeaseManager` heartbeats (lease TTL refresh, `worker` upsert), `DEAD` derivation, `/api/workers` | 3 |
| 6 | `OrphanRecoveryJob` (processing-list scan + DB sweep + missing-enqueue sweep), idempotent CAS, tests | 5 |
| 7 | `ConcurrencySlotService` (Lua acquire/release, TTL refresh in heartbeat), limit API, requeue-with-delay when full | 4 |
| 8 | Graceful shutdown (`SmartLifecycle`), Compose grace period, manual SIGTERM tests | 3 |
| 9 | Health indicators (Docker/Redis/DB), Micrometer metrics for retries/recoveries/limits | 2 |
| 10 | Failure taxonomy doc (`docs/DESIGN_DECISIONS.md` ADR-007), YAML reference update, UI: cancel/retry buttons, attempt badge, `SYSTEM` chunk styling | 2.5 |
| | **Total** | **32.5** |

### 6. Verification tests you write

| Test | Assertions |
|---|---|
| `RetryPolicyTest.app_neverRetried` / `timeout_neverRetried` / `config_neverRetried` | `decide(kind, attempt)` → `FAIL` |
| `RetryPolicyTest.infra_retriedWithBackoffUntilMax` | attempts 1,2 → `RETRY` with delays ≈ 5 s, 10 s (± jitter); attempt 3 → `FAIL` |
| `DockerJobExecutorDockerTest.job_exceedsTimeout_containerKilledAndStateTimedOut` | `timeout 2s`, step `sleep 30` → `TIMED_OUT` in < 5 s; `inspect` → not found; `SYSTEM` chunk "timed out after 2s"; `attempt = 1` |
| `DockerJobExecutorDockerTest.job_cancelWhileRunning_containerRemoved` | Start `sleep 30`; set cancel flag after 1 s → `CANCELLED` within ~12 s; container gone; workspace gone |
| `CancelServiceIT.cancel_queuedJob_removedFromRedisList` | After cancel: `LLEN queue:jobs` = 0, status `CANCELLED`, audit row with actor |
| `CancelServiceIT.cancel_terminalJob_returns409` | `ProblemDetail` type `/errors/build-terminal` |
| `OrphanRecoveryIT.worker_diesMidJob_leaseExpires_jobRetriedAsInfraFailure` | Simulate: lease job with worker A (processing list + lease key + DB `RUNNING`); delete the lease key (as TTL expiry would); run the sweep → job `QUEUED`, `attempt = 2`, `SYSTEM` chunk mentions worker A, id back in `queue:jobs`, `processing:A` empty, limit counter decremented |
| `OrphanRecoveryIT.recovery_attemptsExhausted_failedInfra` | Same with `attempt = 3` → `FAILED`, `failure_kind = INFRA` |
| `OrphanRecoveryIT.recovery_cancelRequestedOrphan_markedCancelled` | `cancel_requested = true` → `CANCELLED`, not retried |
| `OrphanRecoveryIT.recovery_runTwiceConcurrently_actsOnce` | Two threads run the sweep → `attempt = 2` (not 3), one `SYSTEM` chunk |
| `OrphanRecoveryIT.queuedInDbButMissingFromRedis_reenqueued` | Job `QUEUED` for 2 min, not in any list → after sweep it is in `queue:jobs` exactly once |
| `ConcurrencyLimitIT.limit_thirdConcurrentBuild_staysQueued` | Limit 2; 3 jobs; 3 fake-executor slots that block on a latch → after 2 s exactly 2 `RUNNING`, 1 `QUEUED` (never `RUNNING`); release latch → third runs; counter returns to 0 |
| `ConcurrencyLimitIT.acquire_isAtomicUnder50Threads` | Limit 5, 50 threads call acquire → exactly 5 succeed |
| `LeaseManagerIT.heartbeat_refreshesTtl` | TTL after 12 s still > 20 s |
| `WorkerShutdownIT.sigterm_finishesShortJobAndExitsZero` | Spring context `close()` while a 2-s fake job runs → job `SUCCEEDED`, `running = false` |
| `WorkerShutdownIT.sigterm_longJob_requeuedAsInfra` | Fake job 60 s, grace 3 s → job `QUEUED`, `attempt = 2`, reason "worker shutdown" |
| `WorkerHealthTest.dockerUnreachable_healthDown` | Mock `DockerClient.pingCmd` throws → status `DOWN` |

### 7. Debugging scenarios

1. **Recovered job runs twice.** Worker A was *slow*, not dead (GC pause / paused container — `docker pause forgeci-worker-1` reproduces it). Its lease expired, the sweep requeued, then A resumed and finished attempt 1 while B ran attempt 2. Mitigations: heartbeat interval ≪ TTL (10 s vs 30 s); worker checks "do I still hold the lease" before writing terminal state (`GET job:<id>:lease == myId`, or CAS `UPDATE ... WHERE worker_id = me AND attempt = myAttempt`); accept at-least-once and make results idempotent per attempt. **This is the most important scenario in the project — write it up in `failure-engineering.md` scenario 14.**
2. **Limit counter stuck at max; nothing runs.** Release skipped on an exception path or on orphan recovery; TTL not set. Add a "reconcile counters from DB" admin task and log the Lua return.
3. **Cancel of a queued job races with a worker leasing it.** `LREM` ran after `BLMOVE` moved it. The worker's CAS `QUEUED→LEASED` fails (status is `CANCELLED`) → it acks and drops. Verify with a test that interleaves them.
4. **`SIGTERM` kills the worker instantly.** JVM runs as PID 1 via `sh -c` in the Dockerfile so the signal goes to `sh`; use exec-form `ENTRYPOINT ["java", ...]`. Also Compose `stop_grace_period` default is 10 s.
5. **Timed-out job shows `FAILED (APP)` with exit 137.** The step reader saw exit code 137 (SIGKILL) and classified it before the watchdog set the flag. Order: watchdog sets `timedOut=true` *then* kills; classification checks the flag first.
6. **Sweep marks live jobs as orphaned after a Redis restart.** Lease keys vanished with Redis (no AOF). Enable `appendonly` and, on API startup, give workers one heartbeat interval before sweeping (grace window since API start).
7. **Backoff never fires; retries are immediate.** Mover pushes everything in the ZSET regardless of score — `ZRANGEBYSCORE key -inf <now>`, not `ZRANGE`.
8. **Two API instances double-recover.** Not a bug if the sweep is idempotent (test 10) — prove it rather than adding a distributed lock.

### 8. Interview questions

- How do you tell a worker crash from a slow worker? What do you do about the ambiguity?
- Why is the retry decision a function of failure kind, not of "did it fail"?
- Walk through cancellation of a running job on another machine. What if that machine is dead?
- Why does the per-project limit need a Lua script? What is the race without it?
- What does the recovery sweep assume about clocks? (Nothing — Redis TTLs are Redis-local; DB comparisons use `now()` on the DB.)
- What is graceful shutdown for a worker and how did you test it?
- Where is exactly-once impossible here, and what did you do instead?

---

## M5 — Failure recovery + tests + full stack + benchmarks (Week 18)

### 1. What to know first

- Chaos-style testing: fault injection points (`docker kill/pause`, `redis-cli DEBUG SLEEP`, `kill -9`, `iptables`/`tc` optional), the reproduce → observe → diagnose → fix → regression loop — [`09-testing/README.md`](../../09-testing/README.md), [`09-testing/testcontainers.md`](../../09-testing/testcontainers.md), [`09-testing/spring-testing.md`](../../09-testing/spring-testing.md).
- Multi-worker Compose: named services, `--scale`, `stop_grace_period`, healthchecks, profiles — [`11-docker/compose.md`](../../11-docker/compose.md), [`11-docker/dockerfiles.md`](../../11-docker/dockerfiles.md).
- CI/CD with image publishing to GHCR, `docker/build-push-action`, layer cache, tagging — [`13-cicd/github-actions.md`](../../13-cicd/github-actions.md), [`13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md).
- Benchmark methodology: warm-up, steady state, repeat runs, record environment; p50/p95 and why not averages — [`docs-and-resume.md`](./docs-and-resume.md), [`../templates/benchmark-report.md`](../templates/benchmark-report.md).
- **Python for the `tools/` component**: `hmac`/`hashlib`, `subprocess` (git), `argparse`/`typer`, `dataclasses`, type hints, `statistics.quantiles`, `httpx`/`requests`, `psycopg` 3, `pytest` fixtures/`tmp_path`/`monkeypatch`, `mypy --strict`, `ruff` — [`19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md), [`19-python/python-for-java-devs.md`](../../19-python/python-for-java-devs.md), [`19-python/01-python-core.md`](../../19-python/01-python-core.md).
- Documentation set expectations — [`../templates/design-doc.md`](../templates/design-doc.md), [`18-projects/README.md`](../README.md).

### 2. Requirements

- [ ] All 16 scenarios in [`failure-engineering.md`](./failure-engineering.md) executed against the Compose stack, documented in `docs/FAILURE_ENGINEERING.md` with observations and fixes; each automatable one has a regression test.
- [ ] Integration test suite on Testcontainers (Postgres + Redis) covering the named tests from M1–M4; `docker`-tagged suite runs in CI; JaCoCo report; coverage targets from README §20.
- [ ] Compose: `api`, `worker-1`, `worker-2` (or `--scale`), `postgres`, `redis` (AOF, password), `ui`; `compose.test.yaml` overlay with named workers and short grace periods for chaos runs; `scripts/chaos/*.sh` wrappers.
- [ ] CI/CD: `ci.yml` (Maven verify + UI tests + Python tools job + OpenAPI diff), `release.yml` publishing three images to GHCR on tags.
- [ ] **Python `tools/` component** (typed, `pytest`-tested, documented in `tools/README.md`):
  - `repogen`: `forgeci-tools repogen --variant {passing,failing,slow,timeout,invalid-config} --out DIR [--github OWNER/NAME --token-env GITHUB_TOKEN]` creates a local git repo with a minimal Maven project + the matching `.forgeci.yml`, commits, optionally creates the GitHub repo and pushes.
  - `simulator`: `forgeci-tools simulate --api URL --repo-id ID --secret-env FORGECI_WEBHOOK_SECRET --rate 2/s --count 100 [--duplicates 0.1] [--out runs.csv]` sends HMAC-signed synthetic `push` payloads (unique `X-GitHub-Delivery` unless a duplicate is chosen), collects build ids, polls until terminal, writes per-job timings and prints queue wait p50/p95, jobs/min, duplicates rejected, failure counts.
  - `analysis`: `forgeci-tools analyze --api URL | --dsn postgres://... --since 30m [--format md|json]` reports counts by `failure_kind`, retries per job, p50/p95/p99 queue wait and duration (overall and per image), log chunks and bytes per job, worker utilisation; `--job ID` parses that job's log for Maven `Tests run:` totals.
- [ ] Benchmarks recorded with the simulator: queue wait p50/p95 and jobs/min with 1, 2 and 4 workers; log throughput (chunks/s) for a chatty job; SSE fan-out (1, 10, 50 `curl -N` clients on one job) — results in `docs/PERFORMANCE.md` with the protocol from `docs-and-resume.md`.
- [ ] Docs set complete: README, ARCHITECTURE, API (+ `openapi.json`), DATABASE (+ ERD), TESTING, DEPLOYMENT (Compose), SECURITY, DESIGN_DECISIONS (ADR-001…007), PERFORMANCE, FAILURE_ENGINEERING; screenshots.
- [ ] Tag **`v1.0`** — Strong Résumé Version.

### 3. Architectural guidance

**Chaos harness:** a `scripts/chaos/` folder with one script per scenario (`kill-worker.sh`,
`pause-worker.sh`, `redis-sleep.sh 20`, `stop-docker.sh`, `fill-disk.sh`), each printing the commands
to observe with (`docker compose logs -f api`, `redis-cli LRANGE`, `psql -c`). The Python simulator
provides the load while a script injects the fault; `analyze` shows what happened afterwards
(e.g. how many jobs went `INFRA` → retry → `SUCCEEDED`).

**Simulator design:** generate payloads that look like GitHub's `push` (`repository.id`, `after`
SHA, `ref`, `head_commit`, `pusher`); sign `sha256=` + `hmac.new(secret, body, sha256).hexdigest()`
over the *exact bytes sent* (serialize once, sign those bytes, send those bytes); use
`X-GitHub-Delivery: uuid4()`; a token-bucket rate limiter (you wrote one in Java for FlowGrid —
reimplement it in Python, 15 lines); collect `buildId`; poll `/api/builds/{id}` every 500 ms with
a total timeout; compute `queue_wait = started_at − queued_at` from the job fields. Keep the
signing, payload building, percentile math and rate limiting as pure functions in their own module.

Because the simulator's synthetic SHAs do not exist on GitHub, the API's config fetch would fail.
Give the API a **test-mode hook**: when `forgeci.webhook.config-source=inline` and the payload contains
`head_commit.message` starting with `forgeci-config:` followed by base64 YAML, use that instead of
fetching — only enabled in the `chaos` profile, never in prod. Alternative: point `repogen` at a
local Gitea/bare repo the API can read from disk. Choose one, document it in `tools/README.md`.

**Analysis design:** a `Reader` protocol with `ApiReader` and `PgReader` implementations returning
the same `JobRecord` dataclass; aggregation functions take `list[JobRecord]` and return a `Report`
dataclass; a renderer prints Markdown tables. Tests use fixture lists, no I/O.

**Testcontainers layout:** one abstract `AbstractIntegrationTest` with `@ServiceConnection`
`PostgreSQLContainer` and `GenericContainer` Redis (`@Container static`, reused across tests
with `testcontainers.reuse.enable=true` locally). Worker tests that need Docker use the *host*
Docker (Testcontainers already talks to it), tagged `docker`, and use `alpine:3` to stay fast.

**CI images:** build each module with a Dockerfile that runs `mvn -pl <module> -am package -DskipTests`
in a `maven:3.9-eclipse-temurin-21` stage then copies the jar into `eclipse-temurin:21-jre`; label
images with `org.opencontainers.image.revision=$GITHUB_SHA`.

### 4. Acceptance criteria

- [ ] `mvn verify` green locally and in CI including `docker`-tagged tests; `pytest tools/` green with `mypy --strict` and `ruff` clean.
- [ ] `docs/FAILURE_ENGINEERING.md` has 16 entries each with the command run, observed logs (pasted excerpts), diagnosis, fix or design note, and a link to the regression test.
- [ ] `forgeci-tools simulate --rate 2/s --count 100` against the Compose stack completes; the printed p50/p95 match `analyze` output for the same window (two independent computations agree).
- [ ] `forgeci-tools repogen --variant failing --github …` creates a real GitHub repo whose push produces a `FAILED (APP)` build.
- [ ] `docs/PERFORMANCE.md` has the 1/2/4-worker table, log throughput and SSE fan-out numbers with environment and methodology.
- [ ] Three images in GHCR tagged `v1.0`; `docker compose pull` on a clean machine brings up the whole stack.
- [ ] Every doc in the set exists and is linked from the README; ERD and architecture diagrams present.
- [ ] Tag `v1.0`.

### 5. Now implement it

| # | Task | Hours |
|---|---|---|
| 1 | `AbstractIntegrationTest`, consolidate M1–M4 IT tests, JaCoCo, `docker` tag profile in CI | 3 |
| 2 | Compose overlay for chaos, `scripts/chaos/*.sh`, named workers | 2 |
| 3 | Run scenarios 1–8 of `failure-engineering.md`, document, fix bugs found | 4.5 |
| 4 | Run scenarios 9–16, document, fix; regression tests for the automatable ones | 4.5 |
| 5 | Python `tools/` scaffold (`pyproject.toml`, `ruff`, `mypy`, `pytest`, CLI skeleton) | 1.5 |
| 6 | `repogen` (variants, Maven skeleton, git init/commit, optional GitHub create+push) + tests | 3 |
| 7 | `simulator` (payload builder, signer, rate limiter, sender, poller, CSV, summary) + tests + API test-mode hook | 4.5 |
| 8 | `analysis` (readers, aggregations, percentiles, Markdown renderer, log parser) + tests | 3 |
| 9 | Benchmarks: 1/2/4 workers, log throughput, SSE fan-out; `PERFORMANCE.md` | 3 |
| 10 | `release.yml` to GHCR; docs set completion; screenshots; `v1.0` | 3 |
| | **Total** | **32** |

### 6. Verification tests you write

Java (in addition to consolidating all M1–M4 named tests):

| Test | Assertions |
|---|---|
| `RedisOutageIT.api_enqueueWhenRedisDown_buildStillCreatedAndReenqueuedLater` | Pause the Redis container (`container.getDockerClient().pauseContainerCmd`), post webhook → `200`, build `QUEUED`; unpause; sweep → id in `queue:jobs` |
| `RedisOutageIT.worker_pollDuringRedisDown_recoversWithoutCrash` | Pause 20 s during `BLMOVE` → loop logs a `WARN`, continues after unpause, executes the job |
| `SseApiRestartIT.client_reconnectsAndReplays` | Complete the emitter server-side (simulating restart) → client reconnects with `Last-Event-ID`, receives the rest |
| `WebhookLoadIT.duplicatesUnderLoad_createNoExtraBuilds` | 200 posts with 20 % duplicate ids from 10 threads → distinct builds = distinct delivery ids |
| `WorkspaceSweepDockerTest.startup_removesOrphanContainersAndDirs` | Pre-create a labelled container and a stale dir → both gone after `WorkerRegistrar` start |

Python (`tools/tests/`):

| Test | Assertions |
|---|---|
| `test_sign_matches_known_vector` | `sign(b'{"a":1}', b'secret')` equals the hex you got from `openssl dgst -sha256 -hmac secret` and from the Java `SignatureVerifierTest` vector — the same vector in both languages |
| `test_signed_payload_accepted_by_api` | (skipped unless `FORGECI_API_URL`) one signed payload → `200` with `buildId`; same delivery id again → `200` same id; corrupted signature → `401` |
| `test_payload_has_required_github_fields` | `repository.id`, `after`, `ref`, `head_commit.id`, `pusher.name` present; `after` is 40 hex |
| `test_rate_limiter_honours_rate` | 20 tokens at 10/s take ≥ 1.8 s, ≤ 2.5 s (use a fake clock for determinism) |
| `test_duplicate_ratio_applied` | 1,000 deliveries at `duplicates=0.1` → 90–110 reuse an earlier id |
| `test_percentiles_known_array` | p50/p95 of `[1..100]` = 50.5 / 95.05 (state the method: `statistics.quantiles(..., method='inclusive')`) |
| `test_repogen_variants_produce_expected_config` | Each variant → `.forgeci.yml` exists; `invalid-config` fails your Python mini-validator; `timeout` has `timeout: 5s` and a `sleep 60` step; `git log` has one commit |
| `test_repogen_github_push_uses_token_from_env_not_argv` | Token never appears in the constructed command line |
| `test_analysis_taxonomy_counts` | Fixture of 10 `JobRecord`s → counts per `failure_kind`, retries summed, p95 duration |
| `test_analysis_log_parser_maven_summary` | Chunks containing `Tests run: 42, Failures: 1` → parsed totals |
| `test_report_markdown_renders_tables` | Golden-file comparison |

### 7. Debugging scenarios

1. **Simulator's signature is rejected but the Java test vector passes.** JSON serialized twice (`json.dumps` with different separators/ordering) — sign the bytes you send. Or the secret was UTF-8 vs hex-decoded differently in the two languages.
2. **Benchmarks vary 3× between runs.** Image pulls in the first run, Maven downloading dependencies each job (no cache), laptop thermal throttling. Pre-pull, use a local Maven repo mount or the `passing` variant that needs no dependencies, discard warm-up, run 3× and report all three.
3. **CI `docker`-tagged tests fail: "permission denied /var/run/docker.sock".** Runner user vs socket group; on GitHub-hosted runners it works — locally add your user to the `docker` group or use rootless Docker.
4. **`pytest` passes locally, fails in CI: "No module named forgeci_tools".** Install in editable mode (`pip install -e tools/`) in the workflow; or `pythonpath` in `pyproject.toml`.
5. **`analyze` and `simulate` disagree on p95.** Different windows (`--since`) or different definitions (queue wait from `queued_at` vs `created_at`). Define once, document in `PERFORMANCE.md`.
6. **Compose `pull` on a clean machine fails: images private.** GHCR packages default to private; make them public or document `docker login ghcr.io`.

### 8. Interview questions

- Which failure scenario surprised you most and what changed in the design because of it?
- How did you measure queue wait time, and what was p95 with 1 vs 4 workers? What limited throughput?
- Why did you write the load simulator in Python and not in Java/JMeter/k6? (Fast to iterate, `hmac` and `subprocess` in the stdlib, easy to reuse the analysis in a notebook; the trade-off: a second toolchain in CI.)
- How do you know your simulator's HMAC matches the server's? (Shared test vector in both languages + live contract test.)
- What did the failure-taxonomy stats look like under a 200-job run with a worker killed in the middle?
- What does your CI pipeline publish and how would you roll back?

---

## M6 — DAG pipelines + AWS (Week 19)

### 1. What to know first

- Topological sort (Kahn's algorithm), cycle detection, in-degree maps — you solved these in Week 14 ([`03-dsa/16-topological-sort.md`](../../03-dsa/16-topological-sort.md)); now the graph is a build's jobs.
- Fan-out / fan-in; fail-fast vs run-everything; what "skipped" means vs "cancelled".
- Where scheduling decisions belong (API, on job completion events) and why the worker must stay ignorant of the graph.
- AWS for a multi-service app: EC2 with Docker, RDS, security groups between instances, SSM Parameter Store, IAM instance roles, CloudWatch agent — [`12-aws/README.md`](../../12-aws/README.md), [`12-aws/ec2.md`](../../12-aws/ec2.md), [`12-aws/rds.md`](../../12-aws/rds.md), [`12-aws/iam.md`](../../12-aws/iam.md), [`12-aws/cloudwatch.md`](../../12-aws/cloudwatch.md), [`12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md), [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md).
- SQS semantics: visibility timeout, `ChangeMessageVisibility`, DLQ, `maxReceiveCount`, long polling, standard vs FIFO — enough to write ADR-002's AWS section and (optionally) a second `JobQueue` implementation.
- Docker-socket security on a shared host; why the worker instance is separate; gVisor/Firecracker at awareness level (README §19).
- Deep-dive rehearsal method — [`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md).

### 2. Requirements

- [ ] `.forgeci.yml` `jobs:` map with per-job `image`, `timeout`, `steps`, `needs: [..]`; validation: names, existing references, **no cycles** (report the cycle), ≤ 20 jobs; the single-job minimal format still works (implicit job `default`).
- [ ] Build creation stores `needs` per job; only jobs with in-degree 0 are enqueued initially; others are `QUEUED`-but-blocked (add status `WAITING` — or keep `QUEUED` with `blocked = true`; pick, document).
- [ ] `DependencyScheduler` in the API: on every job terminal transition, evaluate dependents: all deps `SUCCEEDED` → enqueue; any dep `FAILED`/`TIMED_OUT`/`CANCELLED`/`SKIPPED` → mark `SKIPPED` (transitively). **Fail-fast**: when a job fails, also cancel any other *queued* jobs in the build (running ones finish); optional per-build `fail_fast: false` to disable.
- [ ] Build status derived with the new states; build detail API returns the graph (`needs`) for the UI; UI renders jobs grouped by level (a simple layered layout) with status colours; `SKIPPED` shows why.
- [ ] Retry of a DAG build re-runs everything (simple) — document "retry failed jobs only" as future work.
- [ ] Concurrency limits and the recovery sweep unchanged (they operate on jobs).
- [ ] **AWS**: API EC2 (api + ui + redis via Compose), worker EC2 (worker ×2, Docker, EBS for workspaces), RDS Postgres, SGs, SSM parameters, IAM roles, CloudWatch logs + "no heartbeat" alarm; GitHub webhook pointed at the public URL; a real push runs end-to-end on AWS; screenshots; teardown checklist.
- [ ] ADR-002 AWS section (Redis list vs SQS) written from the deployment experience; optional `SqsJobQueue` if ≥ 4 h remain.
- [ ] `docs/DEPLOYMENT.md` (AWS), `docs/SECURITY.md` finalized with the socket warning and instance isolation; tag **`v1.1`**; 15-minute deep-dive rehearsal recorded.

### 3. Architectural guidance

**Validation with Kahn** (in `PipelineValidator`, pure):

```
inDegree = {job: len(needs)}; adj = {dep: [dependents]}
queue = [j for j with inDegree 0]; order = []
while queue: j = pop; order.add(j); for d in adj[j]: if --inDegree[d] == 0: queue.add(d)
if len(order) < len(jobs): cycle among {j : inDegree[j] > 0}  → CONFIG error naming them
```

Store `order` (a topological level per job) on `job.level` for the UI layout.

**Scheduling sequence (event-driven, in the API):**

1. Worker marks job J terminal (as today) and publishes `builds:<buildId>` `{"jobId":J,"status":..}`.
2. API `DependencyScheduler` listens (Redis message → `@Transactional` handler; *also* runs as a periodic reconcile every 30 s so a lost message cannot stall a build).
3. Load the build's jobs `FOR UPDATE` (serialize per build); for each non-started job with `needs` containing J: if all deps `SUCCEEDED` → `QUEUED` + enqueue after commit; if any dep in a failure state → `SKIPPED`, recurse to its dependents.
4. Fail-fast: if J failed and `fail_fast`, cancel other queued jobs (`LREM`) and mark them `CANCELLED` (reason "fail-fast"); running jobs are left to finish (document; killing them is a one-line change).
5. Recompute build status.

**Why the API schedules, not the worker:** the worker has one job and no view of the build; putting
graph logic there would need every worker to load the build under a lock. The API already owns
build state. Idempotency: the handler's CAS updates (`WHERE status = 'WAITING'`) make duplicate
messages harmless. The periodic reconcile makes lost messages harmless. Together: exactly-once
*effect* on top of at-most-once pub/sub.

**Graph in the response:** `jobs: [{id, name, status, needs: [names], level}]` — the UI computes
columns by level and draws simple SVG lines between boxes (or just lists per level; lines are optional).

**AWS layout:** two instances in one VPC; API SG: 80/443 public, 6379 from worker SG only; worker SG:
no inbound; RDS SG: 5432 from both. Worker instance role: SSM `GetParameter` on `/forgeci/*` and
CloudWatch `PutLogEvents`. Use `compose.aws.yaml` with images from GHCR. Cost: two small instances +
db.t3.micro for a weekend of screenshots and a benchmark run; then stop instances.

### 4. Acceptance criteria

- [ ] Demo repo branch with a 4-job DAG (build → test-unit, test-it → report): push → `build` runs first, then two in parallel on two workers, then `report`; UI shows levels.
- [ ] Make `test-it` fail → `report` is `SKIPPED` with reason; build `FAILED`; `test-unit` (running) finishes normally.
- [ ] Cycle in config (`a needs b, b needs a`) → build `FAILED (CONFIG)` naming the cycle; nothing queued.
- [ ] Kill the API while a DAG build is mid-way → after restart, the reconcile schedules the waiting jobs (no stuck build).
- [ ] Fan-in with a dependency that was retried (INFRA) → dependents wait for the *final* attempt's result.
- [ ] AWS: `https://<host>/api/webhooks/github` receives a real push; job runs on the worker instance; live log in the UI served from AWS; CloudWatch shows API and worker logs; alarm fires when you stop the worker instance.
- [ ] `docs/DEPLOYMENT.md` reproducible by you on a fresh account in < 2 h.
- [ ] Tag `v1.1`; deep-dive rehearsal done.

### 5. Now implement it

| # | Task | Hours |
|---|---|---|
| 1 | Config format v2 (`jobs`, `needs`, defaults inheritance), validator with Kahn + cycle reporting + tests | 4 |
| 2 | Migration (`needs jsonb`, `level`, `WAITING`/`SKIPPED` states, `fail_fast`), build creation enqueues only roots | 2.5 |
| 3 | `DependencyScheduler` (event handler + reconcile, per-build lock, CAS, transitive skip, fail-fast) | 5 |
| 4 | Build API returns graph; UI layered job view, `SKIPPED` reasons, DAG on build list badge | 4 |
| 5 | Integration tests for scheduling (fan-out/fan-in/fail-fast/lost message/retry) | 3.5 |
| 6 | AWS: VPC/SGs, RDS, API instance, worker instance (Docker, EBS), SSM, IAM roles, CloudWatch agent, alarm | 6 |
| 7 | `compose.aws.yaml`, `release.yml` deploy step (SSH → `compose pull && up -d`), webhook to public URL, end-to-end run, screenshots | 3 |
| 8 | ADR-002 AWS/SQS section (+ optional `SqsJobQueue` spike), `DEPLOYMENT.md`, `SECURITY.md` final, `v1.1`, teardown | 3 |
| 9 | 15-min deep-dive rehearsal (record, review against [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md)) | 1.5 |
| | **Total** | **32.5** |

### 6. Verification tests you write

| Test | Assertions |
|---|---|
| `DagValidatorTest.needs_unknownJob_error` | Message names the job and the missing dependency |
| `DagValidatorTest.cycle_detected_namesCycleMembers` | `a→b→c→a` → error lists `a, b, c`; valid DAG → levels `build=0, test-*=1, report=2` |
| `DagValidatorTest.selfDependency_error` | `a needs a` |
| `DependencySchedulerIT.roots_enqueuedOnCreate_othersWaiting` | 4-job DAG → 1 id in `queue:jobs`, 3 `WAITING` |
| `DependencySchedulerIT.fanOut_afterRootSucceeds_twoEnqueued` | Mark `build` `SUCCEEDED` → `test-unit`, `test-it` queued |
| `DependencySchedulerIT.fanIn_waitsForAllDeps` | `test-unit` succeeds → `report` still `WAITING`; `test-it` succeeds → queued |
| `DependencySchedulerIT.dag_failingJob_skipsDependents` | `test-it` `FAILED` → `report` `SKIPPED` (reason mentions `test-it`), build `FAILED` after `test-unit` finishes; transitive: a chain of 3 dependents all `SKIPPED` |
| `DependencySchedulerIT.failFast_cancelsQueuedSiblings` | Two queued siblings when one fails → both `CANCELLED` reason `fail-fast`, removed from Redis list; with `fail_fast: false` they run |
| `DependencySchedulerIT.duplicateEvent_schedulesOnce` | Deliver the same completion message twice → dependent enqueued once (`LLEN` = 1) |
| `DependencySchedulerIT.lostEvent_reconcileSchedules` | Mark job `SUCCEEDED` in DB without publishing → reconcile pass enqueues dependents |
| `DependencySchedulerIT.dependencyRetried_dependentsWaitForFinalAttempt` | Dep attempt 1 `INFRA` requeued → dependent still `WAITING`; attempt 2 `SUCCEEDED` → queued |
| `BuildStatusResolverTest.skippedAndSucceeded_mix` | Any `SKIPPED` due to failure → `FAILED`; all `SUCCEEDED` → `SUCCEEDED` |
| Manual AWS checklist | Push → build on AWS; alarm; teardown verified (`aws ec2 describe-instances` shows stopped) |

### 7. Debugging scenarios

1. **Build stuck with all jobs `WAITING` after an API restart.** The completion message was published while the API was down; the reconcile job is missing or its query excludes `WAITING`. Prove with the lost-event test.
2. **`report` ran although `test-it` failed.** The scheduler evaluated deps by "not FAILED" instead of "all SUCCEEDED" — `TIMED_OUT`/`CANCELLED` slipped through. Enumerate states explicitly.
3. **Dependent enqueued twice under two API instances.** Missing `FOR UPDATE` per build or missing CAS; `LLEN` reveals it. Fix with the CAS `WHERE status='WAITING'`.
4. **Worker instance cannot reach Redis on the API instance.** Redis bound to `127.0.0.1` inside Compose or SG missing the worker SG rule; `redis-cli -h <private-ip> ping` from the worker host.
5. **Webhook deliveries fail on AWS with SSL errors.** No TLS on the API; put Caddy/nginx with Let's Encrypt in front or use a plain `http://` hook for the demo (document the risk: HMAC still protects integrity, not confidentiality).
6. **Jobs on AWS hit Docker Hub rate limits** (`toomanyrequests`). Pre-pull images on the worker instance; authenticate pulls; classify as `INFRA` with backoff (failure scenario 15).
7. **RDS connections exhausted** after adding the scheduler's reconcile with `FOR UPDATE` and a long transaction. Keep the lock scope to one build and commit fast; check `pg_stat_activity`.

### 8. Interview questions

- How do you schedule a DAG of jobs? Where does Kahn's algorithm run, and what does the worker know about the graph?
- Why does the scheduler need both an event handler and a periodic reconcile?
- What does fail-fast mean in ForgeCI and what happens to a job already running?
- How does a DAG interact with per-project concurrency limits and retries?
- What changed when you deployed to AWS? What would you use SQS for and what would stay on Redis?
- Why is the worker on a separate instance? What can a malicious `.forgeci.yml` do on your deployment, and what can't it do?
- If you had two more weeks, what would you build first: caching, artifacts, matrix builds, or gVisor? Why?

---

## Cross-milestone checklist (Week 19, before CP-20)

- [ ] Every named test in this file exists and passes (grep the names).
- [ ] All 16 failure scenarios documented with evidence.
- [ ] `PERFORMANCE.md` numbers reproduced once from a clean stack using `tools/`.
- [ ] Docs set complete; ADR-001…008 written.
- [ ] `v1.0` and `v1.1` tagged; images in GHCR; AWS torn down (or stopped) with a cost check.
- [ ] Deep-dive rehearsal recorded and scored; answers to every question in [`interview-questions.md`](./interview-questions.md) attempted out loud.
- [ ] [`trackers/project-tracker.md`](../../trackers/project-tracker.md) and [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md) updated.
