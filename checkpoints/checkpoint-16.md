# Checkpoint 16 — ForgeCI M3: the pipeline runs end to end

> **Gate question:** Do I have a working queue → worker → container → live-log pipeline
> (ForgeCI M3)? Do I solve standard Mediums in ≤ 30 min?

| | |
|---|---|
| **When** | Sunday of Week 16 (Part A + C on Saturday, Parts B/D/E + scoring on Sunday) |
| **Time** | ≈ 4 h: A 65 min · B 50 min · C 70 min · D 35 min · E 25 min · scoring 10 min |
| **Covers** | Weeks 13–16: codebase-reading method, Linux deep dive (processes, signals, permissions, Bash), Docker internals (images, layers, networking, Engine API), OS fundamentals, GitHub OAuth/webhooks/HMAC; Java concurrency deep (executors, `CompletableFuture`, locks, atomics, virtual threads), reliable Redis queues (`BLMOVE`/Streams), leases; SSE vs WebSockets, Redis pub/sub, backpressure; React data-heavy views; Graphs BFS/DFS, Topological Sort, Union-Find, 1-D DP, Intervals |
| **DSA patterns in scope** | Everything through Intervals ([`../03-dsa/15-graphs-bfs-dfs.md`](../03-dsa/15-graphs-bfs-dfs.md) … [`19-intervals.md`](../03-dsa/19-intervals.md)) plus all earlier |
| **Project under review** | [ForgeCI](../18-projects/forgeci/README.md) through **M3** ([milestones.md](../18-projects/forgeci/milestones.md)); MVP was M2 |
| **Rules** | [Checkpoint rules](./README.md#rules) |

Passing here is the entry condition for **OA-ready** in [`../JOB_READINESS.md`](../JOB_READINESS.md):
Mediums in ≤ 30 min and the hardest project's core loop working.

---

## Part A — Timed coding (Track A · Python · 50 min · 2 Medium) + Java rep (15 min)

### A1 — 2 Medium in 50 min

**Selection rules:** two `Not Started` Mediums: one *Graph* (BFS/DFS/topological sort/union-find),
one *1-D DP* or *Intervals*. NeetCode 150 first. Swap anything recognised. Python 3.12, no
cheatsheet. The 25-min-per-problem pace is the standard you need from here on.

**Clock:** 50 min total, target ≤ 25 min each. Stop at the bell.

**Python checks after the bell:** adjacency list via `defaultdict(list)`; BFS with `deque`;
visited as `set` (not list); DP table sized correctly with base cases; intervals sorted by start
with a key function; no recursion on 10⁵-node graphs without `setrecursionlimit`.

### A2 — Java rep, 15 min

One already-solved Python *Graph* problem in Java: `Map<Integer, List<Integer>>` adjacency,
`ArrayDeque` BFS or recursive DFS with `boolean[] visited`, in-degree array for Kahn's algorithm.
This is the rep that matters most — ForgeCI M6 needs topological sort in Java for real.

**Scoring**

| A1 (feeds DSA, Python) | Score |
|---|---:|
| 2/2 correct, each ≤ 25 min, complexity stated, idiomatic | 4 |
| 2/2 correct within 50 min; or 1 correct ≤ 25 + other's approach + partial code | 3 |
| 1/2 correct in time | 2 |
| 1/2 over time, or both partial | 1 |
| 0 | 0 |

| A2 (feeds Java) | Points |
|---|---:|
| Compiles, passes, ≤ 15 min, idiomatic collections | 2 |
| Passes late / with Javadoc | 1 |
| Fails | 0 |

---

## Part B — Knowledge questions (50 min · 30 questions)

Write first, then open. 1 point each, 0.5 partial. Max 30.

### Python for interviews (Q1–Q5)

**Q1.** Write the BFS skeleton for shortest path in an unweighted graph given `adj: dict[int, list[int]]`.
<details><summary>Answer</summary>

```python
from collections import deque
def bfs(adj, src):
    dist = {src: 0}
    q = deque([src])
    while q:
        u = q.popleft()
        for v in adj[u]:
            if v not in dist:
                dist[v] = dist[u] + 1
                q.append(v)
    return dist
```
Using `dist` as the visited set avoids a second structure.
</details>

**Q2.** Why is `visited = []` with `if v not in visited` a bug for performance, and what is the
complexity of the fixed version?
<details><summary>Answer</summary>
List membership is O(n) → BFS/DFS becomes O(V·(V+E)). Use a `set` (O(1) average) → O(V + E).
</details>

**Q3.** `functools.lru_cache` / `@cache` on a recursive DP: what does it change, what is the
trap with mutable arguments, and how do you convert to bottom-up?
<details><summary>Answer</summary>
Memoises by argument tuple → exponential becomes O(states). Arguments must be hashable (no
lists — use tuples or indices). Bottom-up: iterate states in dependency order with an explicit
table; avoids recursion depth and is usually faster.
</details>

**Q4.** `sorted(intervals)` vs `intervals.sort(key=lambda x: x[0])`: difference in behaviour and
memory; which do you use inside a function that must not mutate its input?
<details><summary>Answer</summary>
`sorted` returns a new list (O(n) extra memory), `list.sort` sorts in place and returns `None`
(assigning `a = a.sort()` is a classic bug). Plain `sorted(intervals)` sorts tuples
lexicographically (start, then end) — fine for intervals. Use `sorted` when input must stay
untouched.
</details>

**Q5.** In Python, integers are arbitrary precision — so why does an `O(n)` solution using
`2 ** n` still get TLE for large `n`, and what does that say about "bit manipulation" problems?
<details><summary>Answer</summary>
Big-int arithmetic costs O(digits), so operations on huge ints are not O(1). Bit tricks
(`x & (x-1)`, `x & -x`) are still fast for word-sized values; mask with `& 0xFFFFFFFF` when a
problem expects 32-bit wraparound.
</details>

### Linux, processes, Docker (Q6–Q12)

**Q6.** Difference between `SIGTERM` and `SIGKILL`; what does `docker stop` send, and in what
order?
<details><summary>Answer</summary>
`SIGTERM` can be caught for graceful shutdown; `SIGKILL` cannot be caught and terminates
immediately. `docker stop` sends `SIGTERM` to PID 1, waits (default 10 s), then `SIGKILL`. If
PID 1 is a shell that ignores signals, the child never sees `SIGTERM` — use `exec` or a proper
init.
</details>

**Q7.** A build step runs `npm test` inside a container and hangs forever. Name three ways to
find out what it is doing from the host.
<details><summary>Answer</summary>
`docker exec <c> ps -ef` / `top`; `docker logs -f <c>`; `docker exec <c> cat /proc/<pid>/status`
or `strace -p`; `docker stats` for CPU/memory; `docker inspect` for state. From the host with
root: the container processes appear in `ps` with different PIDs.
</details>

**Q8.** What is a Docker image layer, why does `COPY . .` before `RUN mvn dependency:go-offline`
ruin caching, and what is the fix?
<details><summary>Answer</summary>
Each instruction produces a read-only layer; a changed layer invalidates every layer after it.
Copying all sources first means any source edit invalidates the dependency download. Fix: copy
`pom.xml` first, resolve dependencies, then copy sources.
</details>

**Q9.** Bind mount vs named volume; which does the ForgeCI worker use for the job workspace and
why must cleanup run in `finally`?
<details><summary>Answer</summary>
Bind mount = host path mapped in; named volume = Docker-managed. The worker uses a per-job
workspace (temp dir bind-mounted or an ephemeral volume) so clone + steps share files; cleanup in
`finally` because a failed step, timeout or exception must still remove the container and
workspace or the host fills with garbage and leaks secrets.
</details>

**Q10.** Explain what mounting `/var/run/docker.sock` into a container grants and why it is
effectively root on the host.
<details><summary>Answer</summary>
Full Docker Engine API access: anyone in that container can start a privileged container with
`/` bind-mounted, i.e. root on the host. Mitigations: run the worker on the host (not in a
container) or on a dedicated VM, socket proxies with allow-lists, rootless Docker, or
sysbox/gVisor — and never expose the worker to untrusted pipeline configs without isolation.
</details>

**Q11.** File permissions `-rwxr-x---` as octal; what does the sticky bit on `/tmp` do?
<details><summary>Answer</summary>
`750`. Sticky bit on a world-writable dir: only the file owner (or root) can delete or rename a
file there, so users cannot remove each other's temp files.
</details>

**Q12.** What is the difference between a process and a thread at OS level, and what does the
JVM do with virtual threads?
<details><summary>Answer</summary>
Process: own address space, file table, PID; threads share the process memory and are scheduled
by the kernel. Virtual threads (Java 21) are JVM-managed, cheap, mounted on a small pool of
carrier platform threads; blocking I/O unmounts them — ideal for many concurrent blocking calls
(webhook fan-out, log streaming), not for CPU-bound work.
</details>

### Java concurrency (Q13–Q18)

**Q13.** `ExecutorService` shutdown done right: the sequence and why `shutdownNow()` alone is
insufficient.
<details><summary>Answer</summary>
`shutdown()` (stop accepting), `awaitTermination(timeout)`, then `shutdownNow()` (interrupts),
`awaitTermination` again. `shutdownNow` only *interrupts* — tasks must check
`Thread.interrupted()` / handle `InterruptedException` or they keep running.
</details>

**Q14.** What does `CompletableFuture.supplyAsync(...).orTimeout(30, SECONDS)` do to the
underlying task when it times out?
<details><summary>Answer</summary>
Nothing to the task: it completes the *future* exceptionally with `TimeoutException`; the
supplier keeps running. To actually stop work you must cancel cooperatively (interrupt, kill the
container). This is exactly why ForgeCI's timeout kills the container rather than just abandoning
the future.
</details>

**Q15.** `synchronized` vs `ReentrantLock`: two things the lock can do that the keyword cannot.
<details><summary>Answer</summary>
`tryLock(timeout)` (avoid indefinite blocking), fairness, multiple `Condition`s, lock across
method boundaries / non-block-structured acquire-release, and interruptible acquisition.
</details>

**Q16.** Explain the producer/consumer pattern with a `BlockingQueue` and how backpressure
arises.
<details><summary>Answer</summary>
Producers `put()` into a bounded queue and block when it is full; consumers `take()` and block
when empty. The bound is the backpressure: a slow consumer slows the producer instead of growing
memory without limit. Unbounded queues hide the problem until OOM.
</details>

**Q17.** What is a "lease" on a queued job and why is it better than a simple `LPOP`?
<details><summary>Answer</summary>
`LPOP` deletes the job; if the worker dies, the job is lost. `BLMOVE queue processing:worker-1`
moves it atomically to a per-worker list with a lease expiry stored alongside; a reaper re-queues
jobs whose lease expired (worker died) and a live worker renews its lease (heartbeat). Redis
Streams consumer groups give the same with `XPENDING`/`XCLAIM`.
</details>

**Q18.** Why is the log-chunk sequence number per job important for SSE replay?
<details><summary>Answer</summary>
The client reconnects with `Last-Event-ID`; the server replays chunks with `seq > lastId` from
Postgres then continues from pub/sub. Without a monotonic per-job sequence you cannot know what
the client missed, and pub/sub alone loses messages during the disconnect.
</details>

### Webhooks, SSE, backend (Q19–Q24)

**Q19.** How do you verify a GitHub webhook signature, and why compare with a constant-time
function?
<details><summary>Answer</summary>
Compute HMAC-SHA256 over the *raw* request body with the webhook secret, hex-encode, compare to
`X-Hub-Signature-256` (`sha256=…`) using `MessageDigest.isEqual`. Constant-time comparison
prevents timing attacks leaking the signature byte by byte. Read the raw bytes before any JSON
parsing.
</details>

**Q20.** GitHub redelivers a webhook. What makes your receiver idempotent, at the DB level?
<details><summary>Answer</summary>
Unique constraint on `X-GitHub-Delivery` (`webhook_delivery.delivery_id`); insert first, on
unique violation return 200 without creating a second build. Also respond fast (< 10 s) and do
the work asynchronously.
</details>

**Q21.** SSE vs WebSockets: three reasons SSE fits live logs and one case you would switch.
<details><summary>Answer</summary>
Server→client only (logs are one-way), plain HTTP (works through proxies/load balancers, simple
auth headers), built-in reconnect with `Last-Event-ID`, `text/event-stream` trivially testable
with `curl`. Switch to WebSockets when the client must send frequent messages (interactive
terminal).
</details>

**Q22.** What happens to an SSE endpoint under a Servlet container when 1 000 clients connect,
and how do you avoid exhausting threads?
<details><summary>Answer</summary>
Each open `SseEmitter` holds a connection but, being async, releases the request thread;
`spring.mvc.async.request-timeout` matters. With virtual threads or async emitters this scales;
with blocking writes per client on a fixed pool it would not. Set heartbeats and clean up on
completion/timeout/error callbacks.
</details>

**Q23.** In React, why should a component subscribing to an `EventSource` close it in the
effect cleanup, and how do you append 10 000 log lines without re-rendering everything?
<details><summary>Answer</summary>
Otherwise connections leak on unmount/re-render (and StrictMode double-invokes effects in dev).
Keep lines in a ref or state with chunked appends, render with keys by `seq`, virtualise the
list (windowing) or cap the DOM at N lines with "load earlier".
</details>

**Q24.** Redis pub/sub delivery guarantee, and why Postgres is still the log source of truth.
<details><summary>Answer</summary>
Fire-and-forget: subscribers absent at publish time miss the message; no persistence, no ack.
Postgres stores every chunk durably with a sequence; pub/sub only accelerates delivery to
connected clients.
</details>

### OS, networking, Git, debugging (Q25–Q30)

**Q25.** What does `ulimit -n` control, and how would it show up as a ForgeCI bug?
<details><summary>Answer</summary>
Max open file descriptors per process. Leaked container log streams / sockets → "Too many open
files" after N builds. Fix the leak (close streams in `finally`), then raise the limit as a
safety margin.
</details>

**Q26.** TCP three-way handshake; what does `curl -v` show you that a browser hides?
<details><summary>Answer</summary>
SYN, SYN-ACK, ACK before data. `curl -v` shows DNS resolution, connection, TLS handshake
details, exact request/response headers, redirects and chunked/SSE framing.
</details>

**Q27.** `git worktree` or a second clone: how did you work on api and worker modules of the
multi-module Maven repo concurrently without stashing?
<details><summary>Answer</summary>
`git worktree add ../forgeci-worker feature/worker-lease` gives a second working directory on
another branch sharing the same `.git`. Alternatively small PRs merged fast. Multi-module Maven
builds from the root (`mvn -pl worker -am verify`) for one module and its deps.
</details>

**Q28.** In an unfamiliar repository, what are your first three actions before reading any
business logic? ([`../21-debugging-code-reading/method.md`](../21-debugging-code-reading/method.md))
<details><summary>Answer</summary>
Build and run the tests (see what is red); find the entry points (main, controllers, handlers)
and the config; trace one request/path end-to-end with the debugger or logs. Then read the
failing test and its target.
</details>

**Q29.** What is the difference between `docker compose down` and `down -v`, and which do you
run before a failure exercise that needs clean state?
<details><parameter><summary>Answer</summary>
`down` removes containers and networks; `-v` also removes named volumes (the database data).
Clean-state exercises use `-v`; never on a stack whose data you need.
</details>

**Q30.** Explain `Content-Type: text/event-stream` framing: what does one event look like and
how is an id attached?
<details><summary>Answer</summary>

```
id: 42
event: log
data: {"seq":42,"line":"Compiling..."}

```
Fields separated by newlines, events by a blank line; `id` is what the browser sends back as
`Last-Event-ID` on reconnect; `retry:` sets the reconnect delay.
</details>

**Part B scoring:** ≥ 25/30 → 4 · 20–24 → 3 · 15–19 → 2 · 10–14 → 1 · < 10 → 0. Q1–5 Python;
Q6–12 Linux/Docker (CS); Q13–18 Java concurrency; Q19–24 Backend/Frontend; Q25–30 CS/Git/Debugging.

---

## Part C — Practical task (Track B · 70 min)

Branch `cp16/cancel-edge-case`. One clock.

### C1 — Add a job-cancel edge case to ForgeCI, with a test (45 min)

M4 (Week 17) will implement cancellation fully; this checkpoint asks for **one** well-defined edge
case built end-to-end now, on top of your M3 code:

> **Cancel a job that is queued but not yet leased.** `POST /api/jobs/{id}/cancel` on a `QUEUED`
> job must: remove it from the Redis queue (so no worker ever picks it up), set status
> `CANCELLED` with `cancelled_at` and `cancelled_by`, write a final log chunk
> `"Cancelled before start"`, publish the status change so an open SSE client sees it, and be
> idempotent (a second cancel returns 200 with the same state). If a worker leases the job at the
> same instant, exactly one of "worker runs it" / "cancel removes it" wins — never both.

Guidance: the race is the interesting part. Decide where the source of truth is (DB row status
with a conditional `UPDATE … WHERE status = 'QUEUED'` returning the row count, then `LREM` from
the queue), and what the worker checks *after* `BLMOVE` (re-read status; if `CANCELLED`, drop the
job and ack). Document the decision in the PR description.

**Tests required**

- Integration test (Testcontainers Postgres + Redis): enqueue → cancel → assert queue length 0,
  status `CANCELLED`, final log chunk present, second cancel idempotent.
- Race test: 20 iterations of "enqueue; concurrently cancel and start a worker loop"; assert the
  job is either `CANCELLED` with no container started or runs exactly once — never a container
  started *and* `CANCELLED` without the run being recorded.

### C2 — Verify the live-log path from outside (15 min)

With the stack running (`docker compose up`): push a commit to a registered test repository (or
fire a signed webhook with `curl`), then `curl -N -H "Accept: text/event-stream" …/builds/{id}/logs`
and paste: the first three events, the event ids, and what happens when you kill `curl` and
reconnect with `Last-Event-ID`. Then `docker kill` the running job container and record what the
UI shows and what state the job ends in (this is your M4 preview).

### C3 — Python tooling check (10 min)

The ForgeCI Python component lands in M5 (W18). At this checkpoint, only verify that
`tools/` exists with a `pyproject.toml`/`requirements.txt`, a README stub, and a first
`pytest`-tested helper (e.g. the signed-webhook sender you used in C2 is a good candidate).
`pytest tools/` green.

**Scoring (feeds Backend, Java, Debugging, Python)**

| Criteria | Points |
|---|---:|
| C1 endpoint + state + log chunk + SSE publish working | 1.5 |
| C1 race handled with a documented, DB-anchored decision | 1 |
| C1 integration test green; race test green | 1.5 |
| C2 SSE replay demonstrated; container-kill behaviour recorded honestly | 1 |
| C3 tooling stub with one tested helper | 0.5 |
| Within 70 min | 0.5 |

6/6 → 4 · 4.5–5.5 → 3 · 3–4 → 2 · 1.5–2.5 → 1 · < 1.5 → 0.

---

## Part D — Explain out loud (35 min, recorded)

**ForgeCI walkthrough (12 min, no notes):** GitHub event → webhook → build → job queued → worker
`BLMOVE` → container → steps → log chunks → Postgres + pub/sub → SSE → UI → cleanup. Draw it on
paper while talking; photograph the drawing for the log.

Probes from [`../18-projects/forgeci/interview-questions.md`](../18-projects/forgeci/interview-questions.md), e.g.:

1. "Why `BLMOVE` with a per-worker processing list rather than `BRPOP`? What if the worker dies
   after the move?"
2. "How do you make sure a duplicated webhook delivery doesn't create two builds — and what if
   two deliveries arrive in the same millisecond?"
3. "Why SSE and not WebSockets? What breaks with a load balancer in front?"
4. "A job's container is running and the worker JVM is OOM-killed. What happens to the container,
   the job row, the lease, the logs?"
5. "How would you stop a malicious `.forgeci.yml` from reading your host's files?"

**Résumé-defense (8 min):** Docker, Redis, Linux questions from
[`../RESUME_INTERVIEW_QUESTIONS.md`](../RESUME_INTERVIEW_QUESTIONS.md) with ForgeCI examples.

**Track A explain (5 min):** narrate the graph Medium: modelling as a graph, choice of BFS/DFS/
topo, complexity, one edge case (disconnected components / cycle).

Score 0–4 each; walkthrough counts double.

---

## Part E — Project review: ForgeCI vs MVP (M2) + M3

Check [`../18-projects/forgeci/README.md`](../18-projects/forgeci/README.md) and
[`../18-projects/forgeci/milestones.md`](../18-projects/forgeci/milestones.md).

**M1 — GitHub integration, idempotent webhooks**
- [ ] Repo registration; GitHub OAuth (or PAT for MVP, with OAuth planned/ADR)
- [ ] Webhook receiver: HMAC-SHA256 verification on raw body, constant-time compare, tested with a bad signature
- [ ] Dedupe on `X-GitHub-Delivery` via unique constraint, tested with a redelivery
- [ ] Build/job/step records; `.forgeci.yml` parsed (list of commands), invalid config → failed build with a readable message

**M2 — queue + workers + Docker execution (MVP)**
- [ ] Reliable Redis queue (`BLMOVE` + processing list + lease, or Streams) with the choice justified in an ADR
- [ ] Separate worker app in the multi-module Maven repo; N workers runnable via Compose
- [ ] Execution in a temporary container: image from config, workspace, clone, steps, exit codes captured
- [ ] Cleanup always (`finally`), verified by a test that fails a step and asserts no leftover containers
- [ ] Integration test with Testcontainers Postgres + Redis exercising enqueue → execute → persist

**M3 — live logs + UI**
- [ ] Worker appends log chunks with per-job sequence; persisted in Postgres; published via pub/sub
- [ ] SSE endpoint with replay-from-sequence on reconnect (`Last-Event-ID`), heartbeat, cleanup on disconnect
- [ ] React build list + build detail with live log and status badges; `EventSource` closed on unmount
- [ ] Manual reconnect test recorded (C2)

**Hygiene**
- [ ] CI green on every PR (both modules); ADRs for queue choice and SSE ([`../18-projects/templates/adr.md`](../18-projects/templates/adr.md))
- [ ] Docker-socket security noted in SECURITY.md draft
- [ ] Hours logged ≈ 90–100 h so far (target 170–200 by end of W19)

**Scoring (feeds Projects):** all M1–M3 → 4 · ≤ 2 missing, none in M2 → 3 · M2 complete but M3
partial → 2 · M2 incomplete → 1 · M1 incomplete → 0.

---

## Scoring table

| Area | Evidence | Critical? | PASS threshold | My score |
|---|---|:---:|:---:|:---:|
| Python (coding) | A1 idiom/speed, B Q1–Q5 | yes | 3 | |
| Java (engineering) | A2, B Q13–Q18, C1 code quality | yes | 3 | |
| DSA | A1 (≤ 25 min/Medium is the bar) | yes | 3 | |
| SQL | conditional-update race handling in C1, E | no | 3 | |
| Backend | B Q19–Q22, Q24; C1; E | yes | 3 | |
| Frontend | B Q23, E M3 UI | no | 2 | |
| Git | multi-module PR hygiene, ADRs, B Q27 | no | 3 | |
| Debugging | B Q7, Q28; C2 container-kill investigation | yes | 3 | |
| CS fundamentals | B Q6–Q12, Q25–Q26, Q29–Q30 | yes | 3 | |
| Projects | E | yes | 3 | |
| Interview readiness | D + last two weekly mock scores from [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md) | yes | 3 | |

## Decision

| Condition | Decision |
|---|---|
| All critical ≥ threshold, ≤ 1 non-critical below | **PASS** → *OA-ready* once OA sim #1 (W17) scores ≥ pass line in [`../OA_PREP.md`](../OA_PREP.md) |
| All critical met, ≥ 2 non-critical below | **PASS WITH REMEDIATION** |
| Any critical below | **FAIL** — remediate W17–18 |
| M2 pipeline not executing real containers | **FAIL**; M4 features frozen until M2/M3 proven |
| Phase > 1 week behind | Drop ForgeCI Advanced Version (M6 DAG) now — protect v1.0 in W18 |

---

## If you fail — remediation (Weeks 17–18 review blocks, ≈ 8–9 h; ForgeCI weeks are heavy, so keep it surgical)

| Area | Do this |
|---|---|
| **Python (coding)** | Graph + DP template drill: write BFS, DFS, topo sort (Kahn + DFS), union-find, 1-D DP skeletons from memory in 30 min, twice (W17, W18). Re-solve 4 graph Mediums with 25-min clocks. |
| **Java (engineering)** | Re-read [`../01-java/07-concurrency.md`](../01-java/07-concurrency.md); implement a bounded producer/consumer with graceful shutdown from a blank file + test (1.5 h). Java rep: topo sort (needed for M6 anyway). |
| **DSA** | W17 new problems 8 → 5; Day-14/30 reviews for graphs/DP; OA sim #1 becomes the remediation proof. |
| **SQL** | Conditional-update / `RETURNING` / `LREM` race patterns: write the job-state transitions as SQL with row-count checks ([`../04-sql-databases/06-transactions.md`](../04-sql-databases/06-transactions.md)), 1 h. |
| **Backend** | Rebuild the SSE endpoint with replay in a scratch app from a blank file (2 h); [`../05-spring-boot/02-web-layer.md`](../05-spring-boot/02-web-layer.md) async section. |
| **Frontend** | [`../08-react/05-architecture-testing.md`](../08-react/05-architecture-testing.md); virtualise the log view; one component test for the status badge (1.5 h). |
| **Git** | Split any PR > 400 lines going forward; ADR backlog cleared in W17 docs hour. |
| **Debugging** | [`../21-debugging-code-reading/drills.md`](../21-debugging-code-reading/drills.md) two drills (1.5 h); reproduce the container-kill scenario with the debugger attached to the worker. |
| **CS fundamentals** | [`../10-linux/exercises.md`](../10-linux/exercises.md) signals/permissions set; [`../11-docker/exercises.md`](../11-docker/exercises.md) layers/networking set; [`../14-cs-fundamentals/operating-systems.md`](../14-cs-fundamentals/operating-systems.md) (2 h). |
| **Projects** | Finish M3 in W17 Sat blocks before M4 timeouts; drop M6 if > 1 week behind. |
| **Interview** | Two extra recorded walkthroughs (W17, W18) using the diagram; weekly mock focus on ForgeCI probes. |

Second consecutive failure in an area → one-week feature freeze in that area.

---

## Results log

```
CP-16 — date: ____________   total time: ____ h

PART A1 medium 1 (graph): ______ __min reached: ______   medium 2 (dp/intervals): ______ __min reached: ______   score __/4
PART A2 java rep (graph): ______ __min  points __/2
PART B  Py __/5 Linux/Docker __/7 Java conc __/6 Backend/FE __/6 CS/Git/Debug __/6  total __/30  score __/4
PART C  C1 __/4 (race decision: __________)  C2 __/1 (reconnect ok? __ kill → state: ____)  C3 __/0.5  time __  score __/4
PART D  walkthrough __/4  probes avg __  résumé drill __/4  track A __/4  score __/4
PART E  M1 __/4 M2 __/5 M3 __/4 hygiene __/3  hours __  score __/4
MOCKS   last two weekly mock scores: __/4  __/4

AREA SCORES  Python __ Java __ DSA __ SQL __ Backend __ Frontend __ Git __ Debug __ CS __ Projects __ Interview __
DECISION     PASS / PASS WITH REMEDIATION / FAIL     OA-ready pending OA sim #1? __
REMEDIATION  areas: ____________  hours W17: __  W18: __
NOTES
```

Copy to [`./README.md`](./README.md#summary-log), [`../trackers/weekly-progress.md`](../trackers/weekly-progress.md),
[`../JOB_READINESS.md`](../JOB_READINESS.md) scorecard.
