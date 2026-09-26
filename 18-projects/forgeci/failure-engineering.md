# ForgeCI — Failure Engineering

> **Break it on purpose, watch it, understand it, fix it, prove the fix.**
> Sixteen scenarios, run against the Compose stack in Week 18 (M5) — some earlier, the week the
> feature lands. Each follows the same loop: **reproduce → observe → inspect logs → diagnose →
> fix / design around → regression test.** Record every run in `docs/FAILURE_ENGINEERING.md` in your
> repo with pasted evidence (log excerpts, `redis-cli` output, `psql` rows). A scenario you did not
> run is a story you cannot tell in an interview.

## Setup for every scenario

```
docker compose -f docker/compose.yaml -f docker/compose.test.yaml up -d      # api, worker-1, worker-2, postgres, redis, ui
alias rc='docker compose exec redis redis-cli -a "$REDIS_PASSWORD"'
alias pq='docker compose exec postgres psql -U forgeci -d forgeci -c'
alias lg='docker compose logs -f --since 1m'
```

Useful one-liners you will use constantly:

| Question | Command |
|---|---|
| Queue depth / processing / leases | `rc LLEN forgeci:queue:jobs` · `rc LRANGE forgeci:processing:<worker> 0 -1` · `rc KEYS 'forgeci:job:*:lease'` (dev only) |
| Job states | `pq "SELECT id, status, attempt, worker_id, failure_kind, lease_expires_at FROM job ORDER BY queued_at DESC LIMIT 10"` |
| Workers | `pq "SELECT id, hostname, status, running_jobs, now()-last_heartbeat_at AS age FROM worker"` |
| Live containers | `docker ps --filter label=forgeci.job` |
| Watch a job's chunks | `curl -N -H "Cookie: jwt=$JWT" http://localhost:8080/api/jobs/<id>/logs/stream` |
| Follow one job across api + worker logs | `docker compose logs api worker-1 worker-2 \| grep <job_id>` |
| Load | `forgeci-tools simulate --api http://localhost:8080 --repo-id $REPO --secret-env FORGECI_WEBHOOK_SECRET --rate 2/s --count 50` |
| Aftermath | `forgeci-tools analyze --api http://localhost:8080 --since 15m` |

Wrap the fault injections in `scripts/chaos/*.sh` so runs are repeatable and the commands are in the repo.

Scenario index: [1 duplicate webhook](#1-duplicate-webhook-delivery) · [2 worker crash](#2-worker-crashes-mid-job) · [3 container fails to start](#3-container-fails-to-start) · [4 timeout](#4-job-timeout) · [5 cancel](#5-job-cancellation-queued-and-running) · [6 Redis down — API](#6-redis-unavailable-api-side) · [7 Redis down — worker](#7-redis-unavailable-worker-side) · [8 API restart during SSE](#8-api-restarts-during-an-sse-stream) · [9 Docker daemon unreachable](#9-docker-daemon-unreachable) · [10 unregistered repo](#10-github-sends-an-event-for-an-unregistered-repo) · [11 network drop during clone](#11-network-drops-during-clone) · [12 log stream disconnects](#12-log-stream-disconnects) · [13 backlog](#13-queue-backlog) · [14 double lease](#14-two-workers-lease-the-same-job-the-slow-worker-bug) · [15 image pull rate limit](#15-image-pull-rate-limit) · [16 disk full](#16-disk-filling-with-workspaces)

---

## 1. Duplicate webhook delivery

**Why it happens:** GitHub redelivers on timeout or 5xx; an operator clicks "Redeliver"; your reverse proxy retries a POST. Dedupe is M1's core guarantee.

**Reproduce**
```
# a) exact duplicate, sequential
forgeci-tools simulate --count 1 --duplicates 1.0 --repeat 2      # or: curl the same signed body twice
# b) duplicates under concurrency
forgeci-tools simulate --rate 20/s --count 200 --duplicates 0.25 --concurrency 10
# c) real: GitHub → repo settings → Webhooks → Recent deliveries → Redeliver
```

**Observe:** the second response is `200` with the *same* `buildId`. `pq "SELECT count(*) FROM build"` equals the number of distinct delivery ids the simulator printed. `webhook_delivery` has exactly one row per id.

**Inspect logs:** API logs one `INFO webhook accepted delivery_id=… build_id=…` and then `INFO webhook duplicate delivery_id=… existing_build_id=…`. Under concurrency you should see `DataIntegrityViolationException` handled — never a stack trace at `ERROR`.

**Diagnose:** if two builds appear, you checked existence before inserting (TOCTOU) or the delivery insert and the build insert are in different transactions with the check outside. If you see `500 current transaction is aborted`, the unique violation happened inside the outer transaction (Postgres aborts it) — see M1 debugging #3.

**Fix / design:** the unique constraint is the mechanism; the code only *reacts* to it. Do the delivery insert in its own transaction boundary (or the whole ingest in one, catching the violation outside). Return the existing build id.

**Regression test:** `webhook_duplicateDeliveryId_createsOneBuild` (M1) and `WebhookLoadIT.duplicatesUnderLoad_createNoExtraBuilds` (M5).

---

## 2. Worker crashes mid-job

**Why it matters:** the central at-least-once promise. A worker dying must not lose the job, and must not double-run it if it was only slow (scenario 14).

**Reproduce**
```
# start a 90-second job (repogen --variant slow), wait until RUNNING on worker-1, then:
docker kill -s KILL forgeci-worker-1          # kill -9 the JVM; the job container keeps running!
rc LRANGE forgeci:processing:<worker-1-id> 0 -1
rc TTL forgeci:job:<jobId>:lease              # counts down from 30
```

**Observe:** for ≤ 30 s nothing changes. Then the lease TTL hits −2 (gone). Within the next sweep interval (15 s) the API logs `WARN orphan recovered job=… dead_worker=… attempt=2`; the job is `QUEUED`, `attempt = 2`, a `SYSTEM` chunk appears in the log ("worker … lost; retrying 2/3"); worker-2 picks it up. `docker ps` still shows the **orphaned container** from attempt 1 (the JVM died, Docker did not). `/api/workers` shows worker-1 `DEAD` after 60 s.

**Inspect logs:** `grep <job_id>` across api and worker-2: you should be able to read a full timeline: leased by w1 → running → (silence) → recovered by api → leased by w2 → succeeded.

**Diagnose:** three findings are typical: (a) the orphaned container keeps burning CPU until someone removes it; (b) the concurrency-limit counter for the repo is still incremented; (c) if worker-1 comes back (`docker compose start worker-1`) it must not touch attempt 1's state.

**Fix / design:** (a) startup sweep on *every* worker removes containers labelled `forgeci.job` whose job is not `RUNNING` on that worker; the API sweep can also `docker`-less mark them — but only workers have Docker, so add a periodic worker-side sweep too. (b) The recovery path releases the slot (`DECR`), and the counter has a TTL refreshed by heartbeat as a backstop. (c) Terminal writes are CAS on `(worker_id, attempt)`.

**Regression test:** `worker_diesMidJob_leaseExpires_jobRetriedAsInfraFailure`, `WorkspaceSweepDockerTest.startup_removesOrphanContainersAndDirs`.

---

## 3. Container fails to start

**Why:** wrong image, image without `/bin/sh`, Docker out of disk, invalid `HostConfig` (e.g. memory below the minimum 6 MB), port conflicts (you should not publish ports at all).

**Reproduce**
```
# a) unknown image           → repogen --variant invalid-config (image: nope/none:1)
# b) shell-less image         → .forgeci.yml image: gcr.io/distroless/static-debian12
# c) invalid limits           → temporarily set forgeci.executor.memory=1m on worker-1 and restart it
# d) daemon storage exhausted → see scenario 16
```

**Observe:** (a) job `FAILED`, `failure_kind = CONFIG`, `SYSTEM` chunk "image not found: nope/none:1", `attempt = 1`, no retry, no container. (b) exec fails with `OCI runtime exec failed: exec: "sh": executable file not found` → must also be `CONFIG` with a helpful message ("image has no /bin/sh"). (c) `INFRA` (it is *our* misconfiguration): attempt 2 on the other worker succeeds — a nice demonstration that retry-elsewhere works.

**Inspect logs:** worker logs the docker-java exception class; make sure the classification branch is logged (`classified=CONFIG reason=manifest unknown`).

**Diagnose:** the classifier is a `switch` over exception types and Docker status codes (404 manifest → `CONFIG`; 500/connection errors → `INFRA`; exec 126/127 → `CONFIG`). Unknown → `INFRA` + `ERROR` log so you notice and add a branch.

**Fix / design:** `FailureClassifier` as a pure class with a table-driven test; never retry `CONFIG`.

**Regression test:** `DockerJobExecutorDockerTest.job_unknownImage_configFailure`; add `job_imageWithoutShell_configFailure`; `FailureClassifierTest` table.

---

## 4. Job timeout

**Reproduce**
```
repogen --variant timeout   # timeout: 5s, step: sleep 60
# push / simulate; watch:
docker ps --filter label=forgeci.job
watch -n1 'pq "SELECT status, failure_kind, exit_code FROM job ORDER BY queued_at DESC LIMIT 1"'
```

**Observe:** at ~5 s the container disappears; job `TIMED_OUT`; `SYSTEM` chunk "timed out after 5s; container killed"; `attempt = 1` (no retry); build `FAILED`; step `exit_code` is 137 (or null — decide and document); the SSE stream sends `status` then `end`.

**Inspect logs:** worker: `WARN job timeout job=… after=5s → kill`. There must be exactly one terminal transition; if you see both `TIMED_OUT` and a later `FAILED (APP)`, the step reader raced the watchdog.

**Diagnose:** ordering between watchdog and step completion (M4 debugging #5); watchdog thread not daemon (worker won't exit); `ScheduledFuture` not cancelled on normal completion (leaks and kills a *reused* container id — you don't reuse ids, but the log noise hides real problems).

**Fix / design:** `AtomicBoolean timedOut` set before `kill`; classifier checks it first; cancel the watchdog in `finally`.

**Regression test:** `job_exceedsTimeout_containerKilledAndStateTimedOut`.

---

## 5. Job cancellation (queued and running)

**Reproduce**
```
# queued: set repo limit to 1, push two slow builds, cancel the second
curl -X POST -H "Cookie: jwt=$JWT" localhost:8080/api/jobs/<queuedJob>/cancel
rc LLEN forgeci:queue:jobs                       # decremented
# running:
curl -X POST -H "Cookie: jwt=$JWT" localhost:8080/api/jobs/<runningJob>/cancel
docker ps --filter label=forgeci.job             # gone within ~10 s (stop grace)
# race: cancel a queued job at the exact moment a worker leases it
for i in $(seq 1 20); do forgeci-tools simulate --count 1 & sleep 0.2; curl -X POST .../cancel; done
```

**Observe:** queued → `CANCELLED` immediately, never runs; running → `SYSTEM` chunk "cancelled by <login>", container stopped, `CANCELLED`, limit slot released, `audit_event` row with the actor. In the race, either the cancel wins (worker's CAS `QUEUED→LEASED` fails; worker acks and drops) or the lease wins (job runs, then the running-cancel path stops it) — both are correct; a job that is `CANCELLED` in the DB yet still running in Docker is the bug.

**Inspect logs:** worker: `INFO cancel observed job=… via=pubsub|poll`. If you only ever see `via=poll`, the control-channel subscription is broken (still works, just slower).

**Diagnose:** the `LREM` after `BLMOVE` returns 0 → fall through to the running path; cancel flag TTL too short; `CancelWatcher` not started until the container is running (cancel during clone/pull ignored for minutes — fix: check the flag between phases).

**Fix / design:** cancellation is *cooperative* with a *forced* backstop: flag + message + poll, checked between phases and enforced by container stop.

**Regression test:** `job_cancelWhileRunning_containerRemoved`, `cancel_queuedJob_removedFromRedisList`, plus a `cancel_racesWithLease_neverRunsAfterCancelled` interleaving test using a latch inside a fake queue.

---

## 6. Redis unavailable (API side)

**Reproduce**
```
rc DEBUG SLEEP 20                          # Redis blocks all clients for 20 s (safer than stopping it)
# meanwhile:
forgeci-tools simulate --count 5           # webhooks arrive during the outage
curl -N .../api/jobs/<id>/logs/stream      # an SSE client during the outage
docker compose pause redis; sleep 30; docker compose unpause redis     # harder variant
```

**Observe:** webhook `POST`s must still return `200` (Postgres is up) — builds are `QUEUED` in the DB but **not** in Redis. After Redis returns, the "QUEUED but missing from Redis" sweep enqueues them (≤ 60 s + interval). SSE clients see `: ping` comments stop; on reconnect they replay from Postgres. `/actuator/health` shows `redis: DOWN` during the outage.

**Inspect logs:** `WARN enqueue failed, will be recovered by sweep job=…` — not `ERROR` with a 500 to GitHub (that would cause a redelivery, which dedupe handles anyway, but it is noise). `RedisMessageListenerContainer` logs reconnect attempts.

**Diagnose:** common bugs: the enqueue happens *inside* the transaction and its failure rolls back the build (then GitHub retries → fine, but wrong design); Lettuce's default command timeout (60 s) makes the webhook hang for a minute — set `spring.data.redis.timeout=2s` for the API's non-blocking template; the subscriber never resubscribes after the pause (check Lettuce auto-reconnect and the container's error handler).

**Fix / design:** Postgres is the source of truth; Redis is transport. Enqueue after commit, tolerate failure, sweep repairs. Document "Redis down = builds queue up, nothing lost, live logs pause, replay covers the gap".

**Regression test:** `RedisOutageIT.api_enqueueWhenRedisDown_buildStillCreatedAndReenqueuedLater`.

---

## 7. Redis unavailable (worker side)

**Reproduce**
```
# worker-1 running a 60-s job; worker-2 idle in BLMOVE
rc DEBUG SLEEP 25          # longer than the lease TTL (30 s)? try 25 first, then 40
```

**Observe (25 s):** worker-2's `BLMOVE` throws or returns null; loop logs `WARN queue poll failed, retrying in 2s`; heartbeats fail for worker-1 (`WARN heartbeat failed`) but the lease still had TTL — after Redis wakes, heartbeats resume, the job completes normally. **Observe (40 s):** the lease *expires inside Redis* while Redis is asleep? No — Redis expiry is lazy/active but time keeps passing: when it wakes the key is gone. The API sweep then treats worker-1 as dead and requeues attempt 2 while attempt 1 is still running → **scenario 14**. Worker-1 must detect it lost its lease and abandon (kill its container, do not write terminal state for attempt 1).

**Inspect logs:** look for the sequence `heartbeat failed ×3 → lease lost → aborting attempt 1` on worker-1 and `orphan recovered` on the API.

**Diagnose:** does the worker *know* it lost the lease? If the heartbeat only `EXPIRE`s blindly, `EXPIRE` returns 0 for a missing key — check the return value and treat 0 as "lease lost". Does the log pump crash the job when `PUBLISH` fails? It must not: persist to Postgres, log a `WARN`, continue.

**Fix / design:** heartbeat = `SET job:<id>:lease <me> XX EX 30` (only if exists, owned by me — use a Lua compare-and-refresh) and abort the attempt when it fails N times in a row. Log pump: DB first, publish best-effort.

**Regression test:** `RedisOutageIT.worker_pollDuringRedisDown_recoversWithoutCrash`; `LeaseManagerTest.heartbeat_leaseMissing_signalsLost`.

---

## 8. API restarts during an SSE stream

**Reproduce**
```
curl -N -H "Cookie: jwt=$JWT" localhost:8080/api/jobs/<id>/logs/stream > /tmp/stream.txt &
docker compose restart api          # ~10 s
# browser variant: open the job page, restart api, watch the reconnect indicator
```

**Observe:** `curl` exits (it does not reconnect — `EventSource` does). The browser shows "reconnecting", then resumes with no gap: compare the line count in the UI against `GET /api/jobs/<id>/logs`. Server side, after restart the `SseRegistry` is empty and resubscribes on the first client.

**Inspect logs:** on shutdown, `server.shutdown=graceful` should complete emitters (`INFO completing 3 SSE emitters`); on startup, `INFO subscribed logs:<id> clients=1`.

**Diagnose:** if lines are missing, the client's `Last-Event-ID` was not sent (happens when the server never set `id:` on events, or when the reconnect goes through a proxy that strips it). If duplicated, replay used `>=`. If the stream never resumes, the UI created the `EventSource` once and does not recreate on `error` after `readyState === CLOSED` (the browser only auto-reconnects on network errors, not after a server-sent completion).

**Fix / design:** ids on every `log` event; `>` in replay; UI handles `CLOSED` by recreating with `?after=<lastSeq>`; graceful shutdown completes emitters.

**Regression test:** `SseApiRestartIT.client_reconnectsAndReplays`; `sse_reconnectWithLastEventId_replaysMissedChunks`.

---

## 9. Docker daemon unreachable

**Reproduce**
```
# option A (VM or spare host): sudo systemctl stop docker      → the Compose stack dies too; use a VM running only the worker
# option B (Compose): point worker-1 at a bad socket
docker compose stop worker-1
DOCKER_HOST_OVERRIDE=unix:///var/run/nope.sock docker compose up -d worker-1
# option C: chmod the socket so the worker gets EACCES (revert after!)
```

**Observe:** worker-1 `/actuator/health` → `DOWN` (`docker: DOWN`); it should **stop polling** (why lease jobs you cannot run?) — a `DockerHealthGate` that pauses the loop while Docker is unhealthy and marks the worker `DRAINING`. If it does poll, each job fails `INFRA`, is retried with backoff, and lands on worker-2 — acceptable but wasteful; measure how many attempts a job burned.

**Inspect logs:** `ERROR docker ping failed` once per health interval, not per job; `WARN worker draining: docker unhealthy`.

**Diagnose:** three attempts on the same broken worker exhaust `max_attempts` and fail the job *permanently* because of one bad host. Mitigation: prefer another worker (the requeue delay gives worker-2 a chance) and stop polling when unhealthy.

**Fix / design:** health gate on the poll loop; `INFRA` classification; backoff.

**Regression test:** `WorkerHealthTest.dockerUnreachable_healthDown`; `WorkerLoopTest.dockerUnhealthy_loopDoesNotPoll`.

---

## 10. GitHub sends an event for an unregistered repo

**Reproduce**
```
forgeci-tools simulate --repo-id 999999999 --secret-env ANY --count 1
# real: add your webhook URL to a repo you did NOT register in ForgeCI; push
```

**Observe:** `404` with a `ProblemDetail` (`type: /errors/unknown-repository`); `webhook_delivery` row with `status = REJECTED`, `repository_id NULL`, the delivery id, event and body hash — **no body stored**; no build. GitHub shows the delivery as failed (red) — that is correct; the owner sees it in the repo settings.

**Inspect logs:** one `WARN webhook for unknown repository github_repo_id=… delivery_id=…`. Not `ERROR`. No payload in the log.

**Diagnose:** subtle: you cannot verify the signature without a secret, so the `404` response is sent to an *unauthenticated* caller. Make sure the response leaks nothing (no "registered repos are …"), the endpoint is rate-limited per IP, and body size is capped before parsing (a 50 MB JSON from anyone would otherwise be parsed).

**Fix / design:** parse only `repository.id` with a streaming parser or a size-capped read; rate limit; record and reject.

**Regression test:** `webhook_unregisteredRepo_returns404AndRecordsRejected`; `webhook_bodyOver1MB_returns413`.

---

## 11. Network drops during clone

**Reproduce**
```
# in the worker container, black-hole GitHub for one job
docker compose exec worker-1 sh -c 'ip route add blackhole 140.82.112.0/20'   # needs NET_ADMIN cap in compose.test.yaml
# or: set forgeci.git.timeout=2s and clone a huge repo; or point the clone URL at 10.255.255.1
```

**Observe:** `git clone` hangs until the 2-min timeout (or fails fast with "Could not resolve host"), the worker classifies `INFRA`, `SYSTEM` chunk "clone failed: timeout after 120s; retrying 2/3 in 5s", attempt 2 (possibly on worker-2) succeeds. The workspace of attempt 1 is deleted.

**Inspect logs:** `WARN clone failed job=… attempt=1 cause=timeout`; git's stderr in the `SYSTEM` chunk — **verify the token is masked** (`grep -r ghp_ /var/forgeci` and in `log_chunk` — must be empty).

**Diagnose:** `ProcessBuilder` without `redirectErrorStream` leaves stderr unread → git blocks on a full pipe and never finishes; `GIT_TERMINAL_PROMPT=0` missing → git waits for a username forever; the timeout kills `git` but not its child `git-remote-https` (use `destroyForcibly` on the process *and* `ProcessHandle.descendants()`).

**Fix / design:** read both streams concurrently (or merge), hard timeout, kill the tree, mask the token everywhere.

**Regression test:** `GitClonerTest.clone_timeout_throwsInfra`; `GitClonerTest.clone_errorOutput_tokenMasked`.

---

## 12. Log stream disconnects

**Reproduce**
```
# browser: DevTools → Network → Offline for 5 s while a chatty job runs (repogen --variant slow prints a line per 100 ms)
# CLI: curl -N ... | head -c 2000        # closes the connection after 2 KB
# proxy: docker compose restart ui        # nginx in front drops all streams
```

**Observe:** browser resumes with no gap/duplicates (count lines). Server: after `curl` closes, the emitter's `onError`/`onCompletion` fires, `forgeci_sse_clients` decrements, and when it was the last client the Redis subscription for that job is dropped (`INFO unsubscribed logs:<id>`). Publishing continues on the worker regardless.

**Inspect logs:** an `IOException: Broken pipe` at `DEBUG` (not `ERROR`) when writing to the closed client.

**Diagnose:** emitters that are never removed (registry map grows; `jcmd <pid> GC.class_histogram | grep SseEmitter`); a `send` that throws `IllegalStateException` after completion; nginx `proxy_read_timeout` (default 60 s) closing idle streams — the `: ping` every 15 s prevents it.

**Fix / design:** registry with removal on all three callbacks; guarded sends; heartbeat comments; unsubscribe on last client.

**Regression test:** `SseRegistryTest.lastClientRemoved_unsubscribesChannel`; UI `appendsInOrderAndDedupesBySeq`.

---

## 13. Queue backlog

**Reproduce**
```
docker compose up -d --scale worker=1
forgeci-tools simulate --rate 5/s --count 100 --variant slow   # 100 jobs of 20 s, 1 worker × 2 slots ≈ 17 min
watch -n2 'rc LLEN forgeci:queue:jobs'
curl localhost:8080/api/stats/queue
# then scale out mid-backlog:
docker compose up -d --scale worker=4 --no-recreate
```

**Observe:** queue depth climbs to ~100, `forgeci_queue_depth` gauge follows, queue wait p95 grows linearly (`analyze --since 20m` afterwards shows it); after scaling to 4 workers, depth drains ~4× faster with **no restart of anything** — new workers just start pulling. Per-repo limit (2) may be what's binding, not workers: with one repo and limit 2 the backlog drains at 2 jobs at a time regardless of workers — a great thing to notice and explain. Set limit 8 for the test.

**Inspect logs:** nothing alarming should happen; the API stays responsive (webhook p99 latency unaffected — check with `curl -w '%{time_total}'`). Memory of the API flat (no per-job state held).

**Diagnose:** if the API slows down, it is holding state per queued job (e.g. an `SseEmitter` per build page open) or the `stats` endpoint runs an unindexed count.

**Fix / design:** queue depth alarm; document the scale-out procedure; note SQS/ASG autoscaling as the AWS evolution.

**Regression test:** not a unit test — a recorded benchmark run in `PERFORMANCE.md` (queue wait vs workers) is the evidence.

---

## 14. Two workers lease the same job (the slow-worker bug)

**Why it is the most important scenario:** at-least-once + lease expiry means a *slow* worker looks exactly like a *dead* one. If your design assumes "recovered ⇒ the old worker is gone", you will double-run.

**Reproduce**
```
# worker-1 running a 90-s job (slow variant)
docker pause forgeci-worker-1        # freezes the JVM: no heartbeats; container keeps running!
sleep 50                             # lease (30 s) expires; sweep (15 s) requeues → worker-2 starts attempt 2
docker unpause forgeci-worker-1      # worker-1 resumes, finishes attempt 1, tries to write SUCCEEDED and ack
```

**Observe (buggy):** two containers for the same job; `log_chunk` seq collisions (PK violation!) or interleaved logs; the job flips `SUCCEEDED` (attempt 1) while attempt 2 is running, then `SUCCEEDED` again; the limit counter goes negative or drifts; `processing:worker-1` still contains the id after worker-2 acked.

**Observe (fixed):** worker-1 resumes, its next heartbeat's compare-and-refresh returns 0 (lease now owned by worker-2 or absent) → it aborts: kills *its* container, writes nothing terminal, `LREM`s from its own processing list, logs `WARN lost lease for job=… attempt=1; abandoning`. Attempt 2 completes cleanly. Logs of attempt 1 that were already persisted remain (harmless; a `SYSTEM` chunk marks the retry).

**Inspect logs:** the exact interleaving: `w1 heartbeat ok … (pause) … api orphan recovered attempt=2 … w2 leased … w1 lost lease abandoning`.

**Diagnose:** ownership must be checked at every write: heartbeat (Lua: refresh only if value == me), terminal state (`UPDATE job SET … WHERE id=? AND worker_id=? AND attempt=?`), log chunk seq (allocate per attempt from a DB `max(seq)` under the same ownership check or include `attempt` in the PK and let the UI order by `(attempt, seq)` — pick one and document). This is a **fencing token** — the attempt number is your fence.

**Fix / design:** fencing by `(worker_id, attempt)` on every write; abandon on lost lease; accept that attempt 1's *side effects* (its container) may briefly run in parallel — that is what at-least-once means, and why jobs must be idempotent per attempt (fresh workspace, fresh container).

**Regression test:** `LeaseManagerTest.heartbeat_ownedByOther_returnsLost`; `JobStateServiceIT.complete_staleAttempt_rejected` (attempt 1 write after attempt 2 leased → 0 rows updated); `worker_diesMidJob_leaseExpires_jobRetriedAsInfraFailure` extended with a "worker returns" phase.

---

## 15. Image pull rate limit

**Why:** Docker Hub limits anonymous pulls (per IP, per 6 h). On AWS, a NAT'd IP shared by many hosts hits it fast. Symptom: `toomanyrequests: You have reached your pull rate limit`.

**Reproduce**
```
# Simulate without burning your quota: run a local registry that returns 429
docker run -d -p 5001:5000 --name fake-registry <a tiny http server returning 429 on /v2/*>   # or use a mock in the docker-tagged test
# .forgeci.yml image: localhost:5001/maven:3.9
# Alternatively: point the worker's pull at an image name with a typo in a registry that answers 401
```

**Observe:** worker classifies `INFRA` (transient, retryable), backoff 5 s / 10 s, then `FAILED (INFRA)` with a `SYSTEM` chunk that quotes the registry message so the user knows *why*. With the image already present on worker-2 (pre-pulled), attempt 2 there succeeds — pull policy `if-not-present` pays off.

**Inspect logs:** `WARN image pull failed image=… status=429`; one line per attempt, not a stack trace per layer.

**Diagnose:** if classified `CONFIG` (no retry) the user is punished for our infra; if retried immediately with no backoff we make the limit worse. Long-term: authenticate pulls (a Hub account raises the limit), pre-pull a list of common images at worker start, or run a pull-through cache registry (`registry:2` with `proxy.remoteurl`).

**Fix / design:** classifier maps 429/5xx to `INFRA`, 401/404 to `CONFIG`; pre-pull list in worker config; document the mirror option in `DEPLOYMENT.md`.

**Regression test:** `FailureClassifierTest.pull429_isInfra`, `pull404_isConfig`; docker-tagged `imagePullPolicy_ifNotPresent_skipsPullWhenLocal`.

---

## 16. Disk filling with workspaces

**Reproduce**
```
# fill the workspace volume: a job whose step writes garbage
# .forgeci.yml step: dd if=/dev/zero of=/workspace/big bs=1M count=8000
# or simulate cleanup failure: chattr +i a workspace dir so rm fails; or kill -9 workers repeatedly between jobs
df -h /var/forgeci/workspaces; du -sh /var/forgeci/workspaces/*
docker system df
```

**Observe:** with cleanup working, `du` returns to baseline after each job. With `kill -9` between clone and cleanup, stale dirs accumulate until the startup sweep runs (`WorkspaceManager` deletes dirs whose job is not running here). The `dd` job either hits the container's disk quota (if you configured `--storage-opt`, overlay2 on xfs only) or fills the host: the next job fails to clone (`INFRA`, "no space left on device") — and *every* job on that worker now fails.

**Inspect logs:** `ERROR workspace create failed: No space left on device`; health indicator should report `disk: DOWN` below a threshold (Spring's `DiskSpaceHealthIndicator` configured on the workspace path).

**Diagnose:** three layers: (1) cleanup in `finally` + startup sweep + periodic sweep of dirs older than 2 × max timeout; (2) a pre-flight check: refuse to lease when free space < 2 GB (health gate, like Docker health) so jobs go to healthy workers; (3) a per-job size cap is hard with bind mounts — document it and point to volume quotas / tmpfs with `size=` for `/tmp` inside the container; also prune dangling images weekly (`docker image prune`) since pulled images are the real disk hog.

**Fix / design:** disk health gate on the poll loop; sweeps; `DEPLOYMENT.md` sizing note (EBS 30 GB, prune cron).

**Regression test:** `WorkspaceSweepDockerTest.startup_removesOrphanContainersAndDirs`; `WorkerLoopTest.diskLow_loopDoesNotPoll`.

---

## Recording template (copy into `docs/FAILURE_ENGINEERING.md` per scenario)

```
### <n>. <title>
Date / commit: …            Stack: compose.test.yaml, workers=2, limit=8
Reproduce:   <exact commands>
Expected:    <what the design says should happen>
Observed:    <what actually happened — paste log lines, redis-cli / psql output>
Diagnosis:   <root cause in 2–4 sentences>
Fix:         <PR link / ADR / "by design, documented in …">
Regression:  <test name(s)>
Time spent:  …
```

## What the suite proves (say this in interviews)

| Property | Scenarios |
|---|---|
| No lost jobs | 2, 6, 7, 9, 11 |
| No duplicate builds | 1, 10 |
| No double execution *effects* (fenced at-least-once) | 2, 7, 14 |
| Bounded resources | 4, 16, 15 (backoff) |
| Correct failure classification and retry | 3, 9, 11, 15 |
| Live logs are durable and gap-free | 8, 12 |
| Graceful degradation without Redis | 6, 7 |
| Horizontal scaling without coordination | 13 |

See also the failure taxonomy in [`README.md`](./README.md#25-error-handling-strategy-and-failure-taxonomy) and the M4/M5 tests in [`milestones.md`](./milestones.md).
