# Docker Exercises

> Part A in Weeks 4–5 (FlowGrid M1–M2), Part B in Weeks 6–8 (FlowGrid full stack + deploy), Part C in Week 14
> (ForgeCI internals + Engine API), Part D in Week 18 (ForgeCI multi-worker stack). 18 tasks.
> For each: **predict first**, run, then write one sentence on what you learned. Break/debug tasks: state the hypothesis,
> the evidence (command output), the fix, and how you'd prevent it — out loud, as in an interview.

Legend: 🛠️ build · 💥 break/debug · 🔍 inspect/explain

---

## Part A — Images and containers (Weeks 4–5)

### 1. 🛠️ Postgres in Compose (Week 4)
Write the minimal `compose.yaml` from the [README](./README.md#week-4-minimum--1-hour--just-enough-to-run-postgres-for-flowgrid).
Start it, connect with `psql` via `docker compose exec`, create a table, insert a row. `docker compose down`, `up` again — is the row there? Why?

### 2. 💥 "Data lost after `down -v`"
Continue from 1: run `docker compose down -v`, then `up`. The row is gone.
Explain precisely what `-v` removed, where that data lived (`docker volume inspect`), and two ways to protect yourself
(habit: never `-v` unless deleting data; `pg_dump` backups — [10-linux/bash-scripting.md §8](../10-linux/bash-scripting.md#8-real-script-1--postgres-backup)).
Bonus: remove the `volumes:` line entirely, `docker compose stop` / `start` (data survives), then `down` / `up` (data gone). Why the difference?

### 3. 🛠️ Naive vs multi-stage (Week 5)
Build FlowGrid's API with the naive single-stage Dockerfile ([dockerfiles.md §3](./dockerfiles.md#3-the-naive-dockerfile-and-why-its-bad)),
then with your own multi-stage one. Record for both: `docker image ls` size, `docker history` layer count, and whether
`ls /workspace/src` works inside. Keep the numbers in your project notes — these are *measured* facts you can quote.

### 4. 💥 Cache busting
With the multi-stage Dockerfile, change one line of Java and rebuild: time it. Now move `COPY src ./src` above
`COPY pom.xml .` + `dependency:go-offline`, change one line again and rebuild: time it. Explain using layer invalidation.

### 5. 🔍 Non-root and signals
Run your image and check `docker exec <c> id`. Then:
1. `docker stop` it and read `docker inspect -f '{{.State.ExitCode}}'` and the Spring shutdown lines in the logs.
2. Change the `ENTRYPOINT` to shell form, rebuild, `time docker stop <c>`. Why ≈ 10 s and exit 137?

### 6. 💥 "Secrets in the image"
In a scratch Dockerfile, add `ENV DB_PASSWORD=supersecret` and `COPY .env .` (with `.env` not in `.dockerignore`). Build, then find the
secret with `docker history --no-trunc` and `docker run --rm <img> cat .env`. Fix both properly and explain why deleting
the file in a later layer doesn't help.

---

## Part B — Compose, networks, debugging (Weeks 6–8)

### 7. 💥 "App can't reach db at localhost"
Put the API in the Compose file with `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/flowgrid`. Start it.
Task: find the root cause from the logs, prove it from inside the container, and fix it.
<details><summary>Reference path</summary>

```bash
docker compose logs api | grep -m1 -A3 "Connection refused"
docker compose exec api sh -c 'getent hosts postgres; nc -zv localhost 5432; nc -zv postgres 5432'
# localhost inside the api container is the api container itself; nothing listens on 5432 there.
# Fix: jdbc:postgresql://postgres:5432/flowgrid (service name + container port).
```
Follow-up: why does the *same* URL with `localhost` work when you run the API from your IDE? (Published port on the host.)
</details>

### 8. 💥 Startup race
Remove `condition: service_healthy` and delay Postgres start (`command: sh -c "sleep 15 && docker-entrypoint.sh postgres"`).
Watch the API crash and restart (`docker compose ps`, `logs`). Restore the healthcheck condition and show the clean startup order.
Then answer: is the healthcheck enough, or must the app also survive the DB restarting later? Test it with `docker compose restart postgres`.

### 9. 🛠️ Add Redis and prove graceful degradation (Week 6, FlowGrid M3)
Add a `redis` service with a healthcheck. With the stack running, `docker compose stop redis` and call the catalog
endpoint: it must still answer (from the DB), with a warning in the logs, not a 500. `start` Redis and confirm caching resumes.

### 10. 🛠️ Full stack behind nginx (Week 7–8)
Add a `web` service built from the dashboard ([dockerfiles.md §7](./dockerfiles.md#7-frontend-image-nginx--static-build)). Only `web`
publishes a port. Verify: the dashboard loads, API calls go to `/api/...` on the same origin (DevTools → Network), a deep
link survives refresh, and `curl localhost:8080` from the host fails (API not published). Explain why no CORS config is needed.

### 11. 💥 Crash-loop triage drill
Ask a friend (or future you) to break one thing: wrong env var name, bad image tag, `mem_limit: 128m`, a typo in the
entrypoint, or a missing `.env` value. Diagnose in ≤ 5 minutes using only `ps`, `logs`, `inspect`, `config`, `exec`.
Record which exit code each failure produced (1, 127, 137, or Compose refusing to start because of `${VAR:?}`).

### 12. 🛠️ Push to GHCR and run elsewhere (Week 8)
Tag your API image with the short git SHA, push to GHCR, then pull and run it on another machine (or your EC2 instance) using
only the Compose file and an `.env`. If you're on Apple silicon deploying to x86 EC2, reproduce `exec format error` first,
then fix it with `docker buildx build --platform linux/amd64`.

### 13. 💥 "Why is the EC2 disk full?"
After several deploys, run `df -h` and `docker system df`. Identify old images, stopped containers and build cache;
reclaim space safely (`docker image prune`, not `docker system prune -a --volumes` on a box with data!). Add pruning to your
deploy script and log rotation to the daemon config (`/etc/docker/daemon.json` → `log-opts` `max-size`/`max-file`).

---

## Part C — Internals and the Engine API (Week 14, ForgeCI M1–M2)

### 14. 🔍 Namespaces and cgroups
Run `docker run -d --name ns-demo --memory 128m --pids-limit 50 alpine sleep 600`. From the host: find its host PID
(`docker inspect -f '{{.State.Pid}}'`), list its namespaces (`sudo ls -l /proc/<pid>/ns`), compare with your shell's.
Inside: `ps` (PID 1 is `sleep`), `cat /sys/fs/cgroup/memory.max`, `cat /sys/fs/cgroup/pids.max`. Explain what each proves.
(On Docker Desktop the host is a VM — use `docker run --rm -it --privileged --pid=host alpine nsenter -t 1 -m -u -n -i sh` to look, or do this on a Linux VM/EC2.)

### 15. 🛠️ Full lifecycle via the Engine API
Using only `curl --unix-socket` ([compose.md §9](./compose.md#9-under-the-hood-internals-and-the-engine-api-week-14-forgeci)):
pull `alpine:3.20`, create a labelled container that prints three lines and exits 3, start it, wait for it, fetch logs,
list containers by label, delete it. Then do the same in a 30-line Java `main` with docker-java. Note every place where
cleanup must happen even if a step fails.

### 16. 💥 Timeout vs cancel vs OOM
Via the API (or CLI): run (a) `sleep 600` and kill it; (b) a shell with a `trap` on TERM and stop it with `t=5`;
(c) a process that allocates more than its `Memory` limit. Record exit code, `OOMKilled`, and elapsed time for each.
Write the classification table ForgeCI's retry policy will use (app failure / timeout / cancelled / infra failure) and justify each row.
(Companion drill: [10-linux/exercises.md #25](../10-linux/exercises.md#part-d--forgeci-drill-week-14).)

---

## Part D — Multi-worker stack (Week 18, ForgeCI M5)

### 17. 💥 The workspace bind-mount trap
Run a worker container with the Docker socket mounted. From inside it, create `/workspaces/job-1/hello.txt` and ask the
daemon to start a job container that bind-mounts `/workspaces/job-1`. The file isn't there. Explain (daemon resolves host
paths) and fix it with a same-path host mount; confirm file ownership afterwards (`ls -ln`) and make cleanup work as the worker's user.

### 18. 💥 Kill a worker mid-job
Scale to 3 workers (`--scale worker=3`), start a long job, then `docker kill` the worker running it. Show — with logs and DB
rows — that the job is recovered after lease expiry and that its orphaned container is found (by label) and removed.
Then `docker stop` (not kill) a worker mid-job: does your graceful-shutdown path hand the job back sooner? Measure both recovery times.

---

## Explain it (out loud, ≤ 60 s each)

- [ ] Image vs container vs layer.
- [ ] Why my Dockerfile copies `pom.xml` before `src`.
- [ ] Volume vs bind mount, and what `down -v` does.
- [ ] Why `localhost` fails between containers.
- [ ] Namespaces vs cgroups.
- [ ] Why mounting the Docker socket is root-equivalent, and what I did about it.

---

## Tracker

| Part | Tasks | Done | Notes |
|---|---|---|---|
| A | 1–6 | ☐ | |
| B | 7–13 | ☐ | |
| C | 14–16 | ☐ | |
| D | 17–18 | ☐ | |

Update [trackers/technology-tracker.md](../trackers/technology-tracker.md) (Docker row).
