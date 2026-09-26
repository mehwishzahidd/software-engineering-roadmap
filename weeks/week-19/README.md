# Week 19 — Linux and Docker in depth; C++ essentials

[← Week 18](../week-18/) · [Roadmap](../../ROADMAP.md) · [Week 20 →](../week-20/)

**Phase 5 — Production engineering (Weeks 19–22)** · **Estimated time: ≈21 h**

| Category | Hours |
|---|---:|
| Core learning (Linux, Docker, C++) | 6.5 |
| Hands-on coding / labs (Break it, Debug it) | 3.5 |
| DSA (7 new 2-D DP + spaced reviews) | 5 |
| Project P4 PulseWatch M1 | 4 |
| Interview practice (OA sim #1, mock, résumé defense) | 2 |
| **Total** | **21** |

---

## 1. Main objective

By Sunday you can operate a Linux box from the terminal without a GUI (files, permissions, processes, signals, services, SSH, text processing), write a defensive Bash script, and ship a Spring Boot service as a small, non-root, multi-stage Docker image orchestrated with Compose. You start **P4 PulseWatch** (monitors API + checker worker + Postgres + Redis in Compose), spend ≈3 hours on C++ so pointer/reference/memory questions stop being scary, and sit your **first OA simulation (70 min)**.

## 2. Prerequisites

- [Checkpoint 16](../../checkpoints/checkpoint-16.md) passed (or its remediation scheduled).
- P3 TeamBoard v1.0 tagged and running with Docker Compose (Week 18); P4 design doc (M0) merged.
- You already ran Postgres in Compose (Week 10) and wrote a Dockerfile for P2 (Week 12). This week makes that knowledge deliberate.
- DSA: 1-D DP and the first 2-D DP problems from Week 18 are in the tracker.

## 3. Topics & subtopics

| Topic | Subtopics | Read |
|---|---|---|
| Linux command line | Filesystem hierarchy, `ls -l` output, permissions (rwx, octal, umask, `chown`), links, `find`, `du`/`df` | [`10-linux/commands.md`](../../10-linux/commands.md) |
| Processes & services | `ps`, `top`/`htop`, `kill` + signals (SIGTERM, SIGKILL, SIGINT, SIGHUP), exit codes, `systemd` units, `journalctl` | [`10-linux/commands.md`](../../10-linux/commands.md), [`14-cs-fundamentals/operating-systems.md`](../../14-cs-fundamentals/operating-systems.md) |
| Text processing | `grep -E`, `sed`, `awk`, `sort`, `uniq -c`, `cut`, `xargs`, pipes, redirection, `tee` | [`10-linux/commands.md`](../../10-linux/commands.md) |
| Networking tools | `ssh`, keys, `scp`, `curl -v`, `ss -tlnp`, `dig` | [`10-linux/commands.md`](../../10-linux/commands.md), [`14-cs-fundamentals/networking.md`](../../14-cs-fundamentals/networking.md) |
| Bash scripting | Shebang, `set -euo pipefail`, variables/quoting, `if`/`for`/functions, `$?`, `trap`, args | [`10-linux/bash-scripting.md`](../../10-linux/bash-scripting.md), [`10-linux/exercises.md`](../../10-linux/exercises.md) |
| Docker images | Images vs containers, layers + cache, `.dockerignore`, multi-stage builds, non-root user, `ENTRYPOINT` vs `CMD`, exec form & PID 1 | [`11-docker/README.md`](../../11-docker/README.md), [`11-docker/dockerfiles.md`](../../11-docker/dockerfiles.md) |
| Docker runtime | Volumes vs bind mounts, bridge networks + DNS by service name, `logs`, `exec`, `inspect`, restart policies, resource limits | [`11-docker/compose.md`](../../11-docker/compose.md), [`11-docker/exercises.md`](../../11-docker/exercises.md) |
| Compose | Services, `depends_on` + `healthcheck`, env files, profiles, named volumes | [`11-docker/compose.md`](../../11-docker/compose.md) |
| C++ essentials (≈3h) | Compile/link, stack vs heap, pointers vs references, `new`/`delete`, RAII, `std::unique_ptr`, `std::vector` | [`20-cpp-basics/README.md`](../../20-cpp-basics/README.md) |
| Résumé defense | Linux, Docker | [`17-resume-tech-defense/linux.md`](../../17-resume-tech-defense/linux.md), [`17-resume-tech-defense/docker.md`](../../17-resume-tech-defense/docker.md) |

## 4. Concepts to learn

### 4.1 Permissions and ownership

```bash
ls -l deploy.sh
# -rw-r--r-- 1 alice dev 412 Sep 1 10:00 deploy.sh
#  u=rw  g=r  o=r   → octal 644
chmod 750 deploy.sh          # u=rwx g=rx o=---
chmod u+x,g-w deploy.sh      # symbolic form
sudo chown app:app /var/lib/pulsewatch
umask                        # 0022 → new files 644, dirs 755
```

- `x` on a **directory** means "may enter / traverse"; `r` means "may list names".
- Containers run processes as a UID; a file owned by root with mode 600 is unreadable by a non-root app user → classic "works as root, breaks as `USER app`".

**Interview angle:** "What does `chmod 755` mean?" / "Why shouldn't a container run as root?" (container escape blast radius, file writes to mounted host paths).

### 4.2 Processes, signals, exit codes

```bash
ps aux | grep java                # find the JVM
pgrep -f pulsewatch-worker        # PID only
kill -TERM 4312                   # polite: app runs shutdown hooks
kill -KILL 4312                   # cannot be caught; no cleanup
echo $?                           # exit code of last command: 0 ok, 1–255 error
                                   # 128+N = killed by signal N (137 = SIGKILL, 143 = SIGTERM)
```

- `docker stop` sends SIGTERM, waits 10 s, then SIGKILL. Spring Boot with `server.shutdown=graceful` finishes in-flight requests on SIGTERM.
- If your `ENTRYPOINT` is shell form (`ENTRYPOINT java -jar app.jar`), `/bin/sh` is PID 1 and **does not forward SIGTERM** → every stop takes 10 s and ends with exit 137.

**Interview angle:** "Difference between SIGTERM and SIGKILL?" "Your container exits with 137 — what happened?" (OOM-killed or SIGKILL after stop timeout; check `docker inspect --format '{{.State.OOMKilled}}'`).

### 4.3 systemd and journald

```ini
# /etc/systemd/system/pulsewatch.service
[Unit]
Description=PulseWatch compose stack
After=docker.service network-online.target
Requires=docker.service

[Service]
WorkingDirectory=/opt/pulsewatch
ExecStart=/usr/bin/docker compose up
ExecStop=/usr/bin/docker compose down
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now pulsewatch
systemctl status pulsewatch
journalctl -u pulsewatch -f --since "10 min ago"
```

You will use exactly this on EC2 in Week 21.

**Interview angle:** "How would you make a service start on boot and restart if it crashes?"

### 4.4 Text processing on real logs

```bash
# Top 5 endpoints returning 5xx in an access log (space-separated, status in field 9)
awk '$9 ~ /^5/ {print $7}' access.log | sort | uniq -c | sort -rn | head -5

# All ERROR lines from the worker in the last run, with 2 lines of context
docker compose logs worker | grep -E -A2 'ERROR|Exception'

# Replace a config value in place (GNU sed)
sed -i 's/^CHECK_INTERVAL=.*/CHECK_INTERVAL=30/' .env

# Which process holds port 8080?
sudo ss -tlnp | grep ':8080'
```

**Interview angle:** "How would you find which endpoint produces the most errors from a log file?" — say the pipeline out loud.

### 4.5 Defensive Bash

```bash
#!/usr/bin/env bash
set -euo pipefail            # exit on error, unset var, failed pipe stage
IFS=$'\n\t'

readonly BACKUP_DIR="${1:?usage: backup.sh <dir>}"
readonly TS="$(date +%Y%m%dT%H%M%S)"
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT     # cleanup even on failure

docker compose exec -T postgres pg_dump -U pulsewatch pulsewatch > "$tmp"
mkdir -p "$BACKUP_DIR"
gzip -c "$tmp" > "$BACKUP_DIR/pulsewatch-$TS.sql.gz"
# keep last 7
ls -1t "$BACKUP_DIR"/pulsewatch-*.sql.gz | tail -n +8 | xargs -r rm --
echo "backup ok: $BACKUP_DIR/pulsewatch-$TS.sql.gz"
```

Quote every variable (`"$x"`); unquoted variables split on spaces and glob.

**Interview angle:** "What does `set -euo pipefail` do and why?"

### 4.6 Images, layers, multi-stage builds

```dockerfile
# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q dependency:go-offline          # cached unless pom.xml changes
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]   # exec form → java is PID 1
```

- Each instruction is a layer; order from least- to most-frequently changing so `COPY src` doesn't invalidate the dependency layer.
- Final image contains a JRE + jar only — no Maven, no sources. Compare `docker image ls` sizes of single-stage vs multi-stage and record both numbers in the P4 README.
- `.dockerignore`: `target/`, `.git/`, `node_modules/`, `.env`.

**Interview angle:** "Image vs container?" "Why multi-stage?" "Why did my build re-download all dependencies on every code change?"

### 4.7 Volumes, networks, Compose healthchecks

```yaml
# compose.yaml (P4 PulseWatch M1)
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: pulsewatch
      POSTGRES_USER: pulsewatch
      POSTGRES_PASSWORD: ${DB_PASSWORD:?set DB_PASSWORD in .env}
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pulsewatch"]
      interval: 5s
      retries: 10
  redis:
    image: redis:7
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
  api:
    build: ./api
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pulsewatch   # service name = DNS name
      SPRING_DATA_REDIS_HOST: redis
    depends_on:
      postgres: { condition: service_healthy }
      redis: { condition: service_healthy }
  worker:
    build: ./worker
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pulsewatch
    depends_on:
      postgres: { condition: service_healthy }
    restart: unless-stopped
volumes:
  pgdata:
```

- Named volume `pgdata` survives `docker compose down`; `down -v` deletes it.
- Inside the Compose network, `localhost` is the container itself — use the service name.
- Only `api` publishes a port; Postgres and Redis are reachable only inside the network.

**Interview angle:** "How do two containers talk to each other?" "Where does Postgres data live when the container is deleted?" "Does `depends_on` wait for the DB to be ready?" (only with `condition: service_healthy`).

### 4.8 C++ essentials (≈3 h, awareness level)

```cpp
#include <iostream>
#include <memory>
#include <vector>

struct Monitor { std::string url; int intervalSec; };

void bump(int x)      { x++; }        // copy: caller unchanged
void bumpRef(int& x)  { x++; }        // reference: caller changed
void bumpPtr(int* x)  { (*x)++; }     // pointer: caller changed, may be nullptr

int main() {
    int a = 1; bump(a); bumpRef(a); bumpPtr(&a);          // a == 3
    Monitor* raw = new Monitor{"https://x.io", 30};       // heap, manual
    delete raw;                                            // forget → leak; twice → UB
    auto m = std::make_unique<Monitor>(Monitor{"https://y.io", 60}); // RAII: freed at scope end
    std::vector<int> v{3, 1, 2};                           // like ArrayList<Integer>, but values not refs
    std::cout << a << " " << m->url << " " << v.size() << "\n";
}
```

Compile: `g++ -std=c++20 -Wall -Wextra -g main.cpp -o main && ./main`. Run once with `-fsanitize=address` after adding a double `delete` to see the error.

**Interview angle:** "Java has no pointers — true?" (Java has references, no pointer arithmetic, GC instead of `delete`). "Pass-by-value in Java vs pass-by-reference in C++."

## 5. Resources

- Linux: `man` pages (`man 1 chmod`, `man 7 signal`), *The Linux Command Line* (William Shotts, free online edition), systemd docs (`man systemd.service`).
- Bash: GNU Bash Reference Manual (gnu.org/software/bash/manual); ShellCheck (shellcheck.net) — run it on every script.
- Docker: docs.docker.com — "Dockerfile reference", "Multi-stage builds", "Compose file reference", "Networking overview".
- Java in containers: docs.spring.io — Spring Boot reference, "Container Images" and "Graceful Shutdown" sections.
- C++: cppreference.com (`std::unique_ptr`, `std::vector`), learncpp.com chapters on pointers/references.
- DSA: neetcode.io 2-D DP section; [`03-dsa/21-dp-2d.md`](../../03-dsa/21-dp-2d.md).

## 6. Exercises and coding assignments

| # | Task | Acceptance criteria |
|---|---|---|
| E1 | Complete the permissions + process sections of [`10-linux/exercises.md`](../../10-linux/exercises.md) | You can predict `ls -l` output for `chmod 640`; you killed a process with SIGTERM and SIGKILL and recorded both exit codes |
| E2 | Log analysis one-liners: given P3's nginx access log, produce top 10 paths, count of 4xx vs 5xx, and requests per minute | Three commands saved in `scripts/log-report.sh`; passes `shellcheck` |
| E3 | `backup.sh` from §4.5 for P4's Postgres + `restore.sh` | Restore into a fresh volume and the monitor rows are back; script fails loudly on a missing arg |
| E4 | Dockerfile for P4 `api` and `worker`, multi-stage, non-root, exec-form entrypoint | `docker image ls` shows < 300 MB; `docker exec api id` shows uid 10001; `docker stop api` takes < 3 s (graceful) |
| E5 | C++: write the snippet in §4.8 plus a `Stack<int>` class wrapping `std::vector` | Compiles with `-Wall -Wextra` and no warnings; ASan run shows the double-free when you plant one |

### Break it

1. **Kill a container.** With the stack up, `docker kill pulsewatch-postgres-1`. Predict: what does the API return on `GET /api/monitors`? What does the worker log? Does it recover on `docker compose start postgres` without restarting Java? (HikariCP should reconnect; write down how long it took.)
2. **Fill the disk.** In a throwaway container: `docker run --rm -it --tmpfs /data:size=50m ubuntu bash` then `fallocate -l 60M /data/big` or `dd if=/dev/zero of=/data/big bs=1M`. Observe `No space left on device`. Then on your host run `docker system df` and reclaim with `docker system prune` (read what it deletes first).
3. **Shell-form entrypoint.** Change `ENTRYPOINT` to shell form, rebuild, `time docker stop api`. Record 10 s + exit 137. Revert.
4. **Wrong permissions.** `chmod 600` a config file owned by root and mount it into the non-root container. Read the stack trace; fix with ownership, not by running as root.

### Debug it

- Worker can't reach Postgres: set `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/...` in the worker. Diagnose with `docker compose exec worker getent hosts postgres` and `docker network inspect`. Write the root cause in one sentence.
- Build cache busting: move `COPY src src` above the dependency step; time two builds after a one-line code change. Explain the difference from the layer list (`docker history`).

## 7. DSA — 2-D Dynamic Programming (7 new)

Guide: [`03-dsa/21-dp-2d.md`](../../03-dsa/21-dp-2d.md) · Toolkit: [`03-dsa/java-dsa-toolkit.md`](../../03-dsa/java-dsa-toolkit.md) · Tracker: [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md)

Method for every problem: define `dp[i][j]` in one English sentence → recurrence → base cases → iteration order → space optimize only after a correct 2-D version.

| # | Problem | Difficulty | Time limit | Key idea |
|---|---|---|---:|---|
| 1 | [518. Coin Change II](https://leetcode.com/problems/coin-change-ii/) | Medium | 30 min | Unbounded knapsack, coins outer loop to count combinations not permutations |
| 2 | [494. Target Sum](https://leetcode.com/problems/target-sum/) | Medium | 30 min | Reduce to subset-sum count: `(total + target) / 2` |
| 3 | [97. Interleaving String](https://leetcode.com/problems/interleaving-string/) | Medium | 35 min | `dp[i][j]` = first i of s1 + first j of s2 form first i+j of s3 |
| 4 | [72. Edit Distance](https://leetcode.com/problems/edit-distance/) | Medium | 35 min | min of insert/delete/replace from three neighbours |
| 5 | [309. Best Time to Buy and Sell Stock with Cooldown](https://leetcode.com/problems/best-time-to-buy-and-sell-stock-with-cooldown/) | Medium | 30 min | State machine: hold / sold / rest |
| 6 | [329. Longest Increasing Path in a Matrix](https://leetcode.com/problems/longest-increasing-path-in-a-matrix/) | Hard | 40 min | DFS + memo on grid |
| 7 | [115. Distinct Subsequences](https://leetcode.com/problems/distinct-subsequences/) | Hard | 40 min | Take-or-skip matching char |

If any of these was already solved in Week 18, swap in: [312. Burst Balloons](https://leetcode.com/problems/burst-balloons/) or [10. Regular Expression Matching](https://leetcode.com/problems/regular-expression-matching/).

**Spaced reviews due this week** (from the tracker): Day-3/7 reviews of Week 18's greedy + 2-D DP (e.g. 62 Unique Paths, 1143 Longest Common Subsequence, 55 Jump Game), Day-14 reviews of Week 17's 1-D DP/intervals, Day-30 reviews of Week 15's topological sort / union-find. Reviews are re-solves from blank, timed at 20 min for Mediums.

## 8. Project work — P4 PulseWatch, Milestone 1

Spec: [`18-projects/p4-pulsewatch/README.md`](../../18-projects/p4-pulsewatch/README.md) · Tracker: [`trackers/project-tracker.md`](../../trackers/project-tracker.md)

**M1 (W19): monitors CRUD API, scheduled checker worker (HTTP checks with timeouts, executor/virtual threads), `check_results` table, Compose stack (api, worker, postgres, redis).**

- [ ] Maven multi-module or two modules: `api` (Spring Boot web + JPA + Flyway) and `worker` (Spring Boot, no web).
- [ ] Flyway `V1__monitors.sql`: `monitors(id, name, url, method, interval_seconds, timeout_ms, expected_status, enabled, created_at)`; `V2__check_results.sql`: `check_results(id bigserial, monitor_id, checked_at timestamptz, status_code, latency_ms, success boolean, error text)`.
- [ ] Monitors CRUD with DTOs, Bean Validation (URL format, `interval_seconds >= 30`, `timeout_ms <= 10000`), ProblemDetail errors, pagination.
- [ ] Worker: `@Scheduled(fixedDelay = 5000)` loop selects due monitors and submits checks to `Executors.newVirtualThreadPerTaskExecutor()`; `java.net.http.HttpClient` with connect + request timeouts; each result inserted into `check_results`.

```java
HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
HttpRequest req = HttpRequest.newBuilder(URI.create(m.url()))
        .timeout(Duration.ofMillis(m.timeoutMs())).GET().build();
long start = System.nanoTime();
try {
    HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
    save(m, res.statusCode(), elapsedMs(start), res.statusCode() == m.expectedStatus(), null);
} catch (HttpTimeoutException e) {
    save(m, null, elapsedMs(start), false, "timeout");
} catch (IOException e) {
    save(m, null, elapsedMs(start), false, e.getClass().getSimpleName());
}
```

- [ ] Compose stack from §4.7 starts with one command; README "Run locally" section.
- [ ] Tests: `@WebMvcTest` for the controller, unit test for "is monitor due?" logic, Testcontainers test for the repository; worker test against a local stub (WireMock or a `HttpServer` in the test) returning 200, 500 and a delayed response that trips the timeout.

**Definition of done:** create 3 monitors via curl (one to a URL that 500s, one that times out) → within a minute `SELECT monitor_id, success, count(*) FROM check_results GROUP BY 1,2;` shows both outcomes.

## 9. Git activity

- Branch per feature: `feat/monitors-crud`, `feat/checker-worker`, `chore/compose-stack`; PR into `main` with a description + checklist; squash-merge.
- Conventional commits (`feat(worker): add http check with timeout`).
- Add `scripts/` (backup/restore, log-report) with a `README` line each.
- Target: ≥ 10 meaningful commits across ≥ 4 days; at least 3 PRs merged.

## 10. Interview preparation

- **OA simulation #1 (70 min)** — follow [`OA_PREP.md`](../../OA_PREP.md): 2 problems (1 Easy/Medium array-hash, 1 Medium DP/graph), HackerRank/CodeSignal-style editor, no IDE autocomplete, hidden tests. Log score, time per problem and failure causes in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Weekly mock** (per [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md)): 45 min, one Medium from this week's DP + 10 min "walk me through TeamBoard" ([`16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md)).
- **Résumé defense:** Linux + Docker — answer 5 questions each out loud from [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md) using [`RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md); record one answer, cite PulseWatch Compose + P3 Dockerfile as current evidence.
- **Behavioral:** add one story ("a time you debugged a production-like failure") to your bank per [`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md) — use this week's shell-form entrypoint or disk-full lab honestly as a learning story, not as fake work experience.
- **Applications:** OA-ready stage per [`JOB_READINESS.md`](../../JOB_READINESS.md) → **10 applications** this week (internships + junior roles with OAs). Log them.

## 11. Revision work

- Re-read your Week 10 Compose file and Week 12 Dockerfile; list three things you'd now do differently and apply one to P2.
- Explain out loud: HTTP request path from `curl` → Docker port mapping → container → Spring `DispatcherServlet`.
- CS: processes vs threads, virtual memory, context switches — re-read [`14-cs-fundamentals/operating-systems.md`](../../14-cs-fundamentals/operating-systems.md) sections tied to signals.

## 12. Daily plan

| Day | Hours | Blocks |
|---|---:|---|
| **Mon** | 3 | 1.5 h Linux filesystem, permissions, processes, signals ([`10-linux/commands.md`](../../10-linux/commands.md)) · 1 h E1 in a Docker `ubuntu` container · 0.5 h DSA reviews |
| **Tue** | 3 | 1.5 h DSA: 518, 494 · 1.5 h Bash scripting + E2 log one-liners + ShellCheck |
| **Wed** | 3 | 2 h P4: Flyway schema + monitors CRUD API + controller tests · 1 h DSA reviews |
| **Thu** | 3 | 1 h Dockerfiles/Compose deep read · 1 h E4 multi-stage images + Break it #1, #3 · 1 h DSA: 97 |
| **Fri** | 2 | Light: C++ essentials (§4.8, E5) 1.5 h · tracker update + explain SIGTERM vs SIGKILL out loud 0.5 h |
| **Sat** | 5 | 3 h P4: checker worker (virtual threads, timeouts), Compose stack, E3 backup/restore · 1 h DSA: 72, 309 · 1 h **OA simulation #1 (70 min)** |
| **Sun** | 2 | End-of-week test (below) 1.5 h · plan Week 20 + DSA: 329/115 carry-over if not done Thu 0.5 h. Rest. |

C++ total this week: Fri 1.5 h + 1.5 h spread (Mon/Thu evenings reading [`20-cpp-basics/README.md`](../../20-cpp-basics/README.md)) ≈ 3 h. Mock: schedule in Sat interview hour on alternate weeks, or add 45 min Thu if your partner's only free then.

## 13. End-of-week test (≈90 min, timed)

**Part A — DSA (45 min):** solve [1143. Longest Common Subsequence](https://leetcode.com/problems/longest-common-subsequence/) (20 min) and [518. Coin Change II](https://leetcode.com/problems/coin-change-ii/) from blank (25 min). Pass = both accepted, complexity stated.

**Part B — Concepts (20 min):** answer, then check.

<details><summary>1. A container exits with code 137. Two possible causes?</summary>

137 = 128 + 9 (SIGKILL). Either the kernel OOM-killer killed it (`State.OOMKilled=true`), or `docker stop` escalated to SIGKILL after the grace period because the process ignored SIGTERM (often shell-form entrypoint).
</details>

<details><summary>2. Why order Dockerfile instructions from least to most frequently changing?</summary>

Layers are cached; a change invalidates that layer and every layer after it. Copying `pom.xml` and resolving dependencies before copying `src` keeps the dependency layer cached across code changes.
</details>

<details><summary>3. `chmod 640 secrets.env` owned by root:root. Can a process running as uid 10001 read it?</summary>

No. Owner root has rw, group root has r, others nothing. uid 10001 is "other" (unless in group root). Fix with `chown 10001` or a group the app belongs to.
</details>

<details><summary>4. What does `set -o pipefail` change?</summary>

A pipeline's exit status becomes the last non-zero status of any stage, instead of only the last command's. Without it, `false | true` succeeds, hiding failures.
</details>

<details><summary>5. Volume vs bind mount?</summary>

Named volumes are managed by Docker (`/var/lib/docker/volumes`), portable, right for DB data. Bind mounts map a specific host path, right for dev source code/config; they carry host permissions and paths.
</details>

<details><summary>6. Why does `depends_on` alone not prevent "connection refused" on API startup?</summary>

Without a condition it only waits for the container to start, not for Postgres to accept connections. Use `condition: service_healthy` with a `pg_isready` healthcheck (and still have retry logic in the app).
</details>

<details><summary>7. C++ reference vs pointer in one sentence each.</summary>

A reference is an alias that must be bound at initialization and can't be null or reseated; a pointer is an address value that can be null, reassigned and does arithmetic.
</details>

<details><summary>8. Why use virtual threads for the checker?</summary>

HTTP checks are blocking I/O; virtual threads let you write simple blocking code while thousands of checks wait concurrently without a platform thread each. CPU-bound work gains nothing.
</details>

**Part C — Practical (15 min):** on a fresh clone, `docker compose up -d`, add a monitor via curl, then write a one-liner that prints the failure count per monitor from `docker compose exec postgres psql`. Then `docker compose down` and prove data survives `up` again.

**Part D — Explain out loud (10 min, recorded):** "Walk me through what happens from `docker compose up` to the first check result being written." Cover image build, network, DNS, healthchecks, scheduler, virtual threads, JDBC insert.

## 14. Mastery checklist

- [ ] I can read `ls -l` and set permissions in octal and symbolic form.
- [ ] I can find and stop a process, and explain SIGTERM/SIGKILL/exit codes 137/143.
- [ ] I can write a systemd unit and read its logs with `journalctl`.
- [ ] I can answer a log question with a `grep`/`awk`/`sort`/`uniq` pipeline.
- [ ] My Bash scripts use `set -euo pipefail`, quoting, `trap`, and pass ShellCheck.
- [ ] I can write a multi-stage, non-root Dockerfile from memory and explain each layer.
- [ ] I can explain volumes vs bind mounts and Compose DNS by service name.
- [ ] I can explain pointer vs reference vs Java reference, and RAII.
- [ ] P4 M1 is merged and runs with one command.
- [ ] 7 new 2-D DP problems logged with status + next review date.
- [ ] OA sim #1 done and logged with failure causes.

## 15. Expected deliverables

- P4 repo with M1 merged (PRs linked) — update [`trackers/project-tracker.md`](../../trackers/project-tracker.md).
- 7 new problems + reviews in [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md).
- Linux, Docker, C++ rows updated in [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md).
- OA sim #1, mock, 10 applications in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- Weekly entry in [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md) with test score.

## 16. If you're behind / stretch goals

**Behind (priority order):** keep DSA reviews → keep P4 M1 core (CRUD + worker + Compose) → keep OA sim #1 → cut C++ to 1.5 h (pointers/references only) → cut E2/E3 to one script each → drop problems 6–7 (move to Week 21's mixed review).

**Stretch:**
- Add a Compose `profiles: [debug]` service with `adminer` or `redis-commander`.
- Build the image with `docker buildx` for `linux/amd64` and `linux/arm64` (useful if EC2 is Graviton in Week 21).
- Add `HEALTHCHECK` using Actuator in the Dockerfile and resource limits (`mem_limit`, `cpus`) in Compose; watch the JVM respect them with `-XX:+PrintFlagsFinal | grep MaxHeapSize`.
- DSA: 312 Burst Balloons, 10 Regular Expression Matching.
