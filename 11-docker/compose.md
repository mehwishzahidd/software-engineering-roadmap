# Containers at Runtime, Docker Compose v2, and the Engine API

> Weeks 4–8 (FlowGrid: Postgres → Redis → full stack → EC2), Week 14 (internals + Engine API for ForgeCI's workers),
> Week 18 (ForgeCI multi-worker stack). Covers ports, env vars, volumes, networks, Compose, debugging — the things that
> break in practice — and what's underneath.

---

## 1. Running containers: the flags that matter

```bash
docker run -d --name api \
  -p 8080:8080 \                               # publish host:container
  -e SPRING_PROFILES_ACTIVE=dev \              # env var
  --env-file ./api.env \                       # many env vars from a file
  -v pgdata:/var/lib/postgresql/data \         # named volume
  -v "$PWD/config:/app/config:ro" \            # bind mount, read-only
  --network flowgrid-net \                     # user-defined network
  --restart unless-stopped \                   # restart policy
  --memory 512m --cpus 1 --pids-limit 256 \    # cgroup limits
  flowgrid-api:dev
```

### Ports

`-p HOST:CONTAINER`. `EXPOSE` in a Dockerfile only documents. The app inside **must listen on `0.0.0.0`**, not
`127.0.0.1`, or the published port leads nowhere (Spring Boot binds all interfaces by default; the Vite dev server does
not — `--host`).

`-p 5432:5432` binds on **all host interfaces** — on a cloud VM that can expose Postgres to the internet (if the security
group allows). Prefer `-p 127.0.0.1:5432:5432` for dev-only access, or no published port at all for services only other
containers need.

### Environment variables

Spring Boot maps `SPRING_DATASOURCE_URL` → `spring.datasource.url` (relaxed binding). This is how one image runs in every
environment: config comes from the environment, not the image (12-factor).

---

## 2. Volumes vs bind mounts

| | Named volume (`pgdata:/var/lib/…`) | Bind mount (`./src:/app/src`) | tmpfs |
|---|---|---|---|
| Managed by | Docker (`/var/lib/docker/volumes/`) | you (a host path) | memory |
| Use for | **database data**, persistent app data | dev: live code/config; config files; ForgeCI job workspaces (§6) | scratch, secrets in memory |
| Portability | same on every host | depends on host paths and permissions | — |
| Survives `docker compose down` | ✅ | ✅ (it's your folder) | ❌ |
| Survives `docker compose down -v` | ❌ **deleted** | ✅ | ❌ |

```bash
docker volume ls
docker volume inspect flowgrid_pgdata
docker volume rm flowgrid_pgdata
```

Without any volume, Postgres data lives in the container's writable layer: it survives `stop`/`start` but not `rm`
(and `docker compose down` removes containers).

> Postgres image note: for `postgres:16`, data lives in `/var/lib/postgresql/data`. Newer major versions (18+) changed the
> default layout — check the image docs when bumping a major, and never bump Postgres majors on an existing volume
> without a dump/restore or `pg_upgrade`.

---

## 3. Networks and service-name DNS

- Default `bridge` network: containers get IPs but **no name resolution** between them.
- **User-defined bridge** networks (Compose creates one per project, `<project>_default`): containers resolve each other
  **by service name** via Docker's embedded DNS.
- Inside a container, **`localhost` is the container itself**, not the host and not other containers.

```
┌──────────────────── network: flowgrid_default ──────────────────────┐
│  api  ──── jdbc:postgresql://postgres:5432/flowgrid ───►  postgres   │
│   └──────── redis:6379 ───────────────────────────────►  redis       │
│  web (nginx) ── proxy_pass http://api:8080 ──► api                   │
└──────────────────────────────────────────────────────────────────────┘
      ▲ -p 8088:80
   browser: http://localhost:8088
```

| Where the app runs | Where Postgres runs | JDBC host |
|---|---|---|
| IDE on host | Compose, port published | `localhost:5432` |
| Compose service | Compose service | `postgres:5432` (service name, **container** port) |
| Container | host machine | `host.docker.internal` (Desktop; Linux: `extra_hosts: ["host.docker.internal:host-gateway"]`) |
| Container on EC2 | RDS | the RDS endpoint hostname |

---

## 4. Compose v2 basics

Compose v2 is the `docker compose` CLI plugin. The file is `compose.yaml` (`docker-compose.yml` still works). The
top-level `version:` key is obsolete — omit it.

```bash
docker compose up -d --build        # build images if needed, start in background
docker compose ps
docker compose logs -f api          # follow one service
docker compose exec api sh          # shell in running container
docker compose run --rm api env     # one-off container
docker compose restart api
docker compose stop                 # stop, keep containers
docker compose down                 # remove containers + network (volumes kept)
docker compose down -v              # + remove named volumes (DATA LOSS)
docker compose config               # fully resolved config (after .env interpolation) — great for debugging
docker compose --profile tools up -d
docker compose up -d --scale worker=3   # N replicas of a service (ForgeCI, §6)
```

---

## 5. The pattern: api + postgres + redis with healthchecks (FlowGrid, Weeks 6–8)

This shows the **mechanics** (healthchecks, conditions, interpolation, profiles). Your FlowGrid file will differ —
e.g. it adds the `web` service (nginx + built React app, [dockerfiles.md §7](./dockerfiles.md#7-frontend-image-nginx--static-build)).

```yaml
# compose.yaml
name: flowgrid

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: flowgrid
      POSTGRES_USER: flowgrid
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set in .env}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "127.0.0.1:5432:5432"          # dev convenience (psql/IDE); remove on servers
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]   # $$ = literal $ for the container shell
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 10s
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redisdata:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10
    restart: unless-stopped

  api:
    build: ./api
    image: ghcr.io/${GHCR_OWNER:-local}/flowgrid-api:${IMAGE_TAG:-dev}
    env_file: .env
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/flowgrid
      SPRING_DATASOURCE_USERNAME: flowgrid
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      # alpine-based image: BusyBox wget is available (Ubuntu-based JRE images may need curl installed)
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep -q UP"]
      interval: 10s
      timeout: 3s
      retries: 12
      start_period: 40s
    restart: unless-stopped

  adminer:
    image: adminer:4
    ports:
      - "127.0.0.1:8081:8080"
    profiles: ["tools"]                 # only with: docker compose --profile tools up -d

volumes:
  pgdata:
  redisdata:
```

```bash
# .env  (gitignored! commit a .env.example with dummy values instead)
POSTGRES_PASSWORD=change-me-locally
IMAGE_TAG=dev
GHCR_OWNER=your-github-username
APP_JWT_SECRET=change-me-at-least-32-bytes-long-000000
```

Key points:
- **`.env` in the project dir** is read automatically for `${VAR}` interpolation in `compose.yaml`. `env_file:` passes
  variables **into the container**. Different mechanisms — know both.
- `${VAR:?message}` fails fast if missing; `${VAR:-default}` supplies a default.
- **`depends_on` alone only orders startup** — it doesn't wait for readiness. With `condition: service_healthy` Compose
  waits for the dependency's healthcheck. The app should *still* tolerate the DB briefly disappearing.
- `restart: unless-stopped` restarts on crash and after host reboot (if the Docker daemon starts at boot).
- **Redis-down behaviour** (FlowGrid M3 requirement: degrade to DB): `docker compose stop redis` is your test harness.

### Same file on EC2 with RDS (Week 8)

Use an override file (`compose.prod.yaml`) that points the datasource at the RDS endpoint and removes published DB ports,
run with `docker compose -f compose.yaml -f compose.prod.yaml up -d`, or put the local `postgres` service behind
`profiles: ["local-db"]`. Details: [12-aws/deploy-walkthrough.md](../12-aws/deploy-walkthrough.md).

### The frontend in the stack

The `web` service serves the built dashboard and proxies `/api/` to `api:8080` → one origin → no CORS config in
production ([08-react/04-api-integration-auth.md](../08-react/04-api-integration-auth.md)). Give `api` no published port in
production; only nginx is reachable.

---

## 6. ForgeCI's multi-worker stack (Week 18) — requirements, not a solution

ForgeCI's Compose file is yours to write in M5. What it must handle, and the traps:

| Requirement | Notes / traps |
|---|---|
| Services: `api`, `worker` (N replicas), `postgres`, `redis`, `ui` | `docker compose up -d --scale worker=3`; a scaled service must **not** set `container_name` or a fixed host port |
| Workers talk to the **host's** Docker daemon | mount `/var/run/docker.sock:/var/run/docker.sock` into `worker` only — read §9's security section first |
| Job workspaces | **Bind-mount paths in API calls are resolved by the daemon on the host**, not inside the worker container. If the worker creates `/workspaces/job-42` inside its own filesystem and asks the daemon to bind it, the daemon mounts the *host's* `/workspaces/job-42` (probably empty). Fix: mount a host dir into the worker at the **same path** (`/var/forgeci/workspaces:/var/forgeci/workspaces`) or use named volumes |
| Graceful worker shutdown | `stop_grace_period` long enough to finish or hand back the current job; the worker handles SIGTERM (exec-form entrypoint) |
| Orphans | label job containers (`forgeci.job=<id>`, `forgeci.worker=<id>`) so a restarted worker can find and remove its leftovers |
| Worker identity | each replica needs a unique id for leases/heartbeats (hostname = container id works) |

Test it: kill one worker mid-job (`docker kill <worker-container>`) and prove the job is recovered after lease expiry.

LedgerX (Weeks 9–13) and FlagForge (Weeks 20–23) reuse the §5 pattern (api + postgres + redis, plus a small UI).

---

## 7. Local development flow

For daily development, run **only infrastructure** in Compose and the Spring app from the IDE (fast restarts, debugger).
Use `127.0.0.1:5432:5432` and `jdbc:postgresql://localhost:5432/flowgrid`. Build and run the app image in Compose
regularly (at least before every PR that touches config) to prove the container works. Spring Boot 3.1+ can also start
`compose.yaml` services for you via `spring-boot-docker-compose` — optional.

---

## 8. Debugging containers

```bash
docker compose ps                         # State/Status: running, restarting, exited (code), health
docker compose logs --tail=200 api        # last 200 lines; -f to follow; --timestamps
docker inspect <container> --format '{{.State.Status}} {{.State.ExitCode}} OOM={{.State.OOMKilled}}'
docker inspect <container> --format '{{json .State.Health}}' | jq      # healthcheck history + output
docker inspect <container> --format '{{json .NetworkSettings.Networks}}' | jq
docker compose exec api sh                # look around: env, files, DNS
docker compose exec api env | sort
docker compose exec api nslookup postgres     # BusyBox; or getent hosts postgres
docker compose exec api wget -qO- localhost:8080/actuator/health
docker stats                              # live CPU / memory per container
docker compose top                        # processes inside
docker compose run --rm --entrypoint sh api   # shell in a container that crashes on startup
docker system df; docker system prune     # disk usage; clean stopped containers, dangling images, unused networks, build cache
```

**Crash-loop triage:**
1. `docker compose ps` → exit code. `1` app error; `137` SIGKILL (often **OOMKilled** — check `inspect`); `143` SIGTERM; `127` command not found (bad entrypoint).
2. `docker compose logs api` → the **first** stack trace (scroll up, not just the last line).
3. Config? `docker compose config` + `exec env`.
4. Network/DNS? `exec … getent hosts postgres`, `nc -zv postgres 5432`.
5. Dependency not ready? healthcheck + `depends_on` condition.

---

## 9. Under the hood: internals and the Engine API (Week 14, ForgeCI)

### What a container is

| Mechanism | What it does | See it |
|---|---|---|
| **Namespaces** (pid, net, mnt, uts, ipc, user, cgroup) | what a process can *see*: its own PID 1, network stack, mount table, hostname | `ls -l /proc/<pid>/ns`; `docker inspect -f '{{.State.Pid}}'` |
| **cgroups v2** | what it can *use*: memory, CPU, PIDs, I/O | `--memory`, `--cpus`, `--pids-limit`; `cat /sys/fs/cgroup/memory.max` inside the container |
| **Union filesystem (overlay2)** | image layers stacked read-only (lowerdir) + one writable layer (upperdir) | `docker inspect -f '{{json .GraphDriver.Data}}' <c>` |
| **Runtime chain** | `docker` CLI → `dockerd` (Engine API) → `containerd` → `runc` (creates namespaces/cgroups, execs PID 1) | `ps -ef | grep -E 'dockerd|containerd'` |

### The Engine API

The `docker` CLI is just an HTTP client for `dockerd`'s REST API on a Unix socket. ForgeCI's worker does the same with
the docker-java client. Walk the lifecycle by hand once, with curl:

```bash
S=/var/run/docker.sock
curl -s --unix-socket $S http://localhost/version | jq .ApiVersion          # pin paths as /v<ApiVersion>/… in real code
curl -s --unix-socket $S -X POST "http://localhost/images/create?fromImage=alpine&tag=3.20" > /dev/null   # pull

ID=$(curl -s --unix-socket $S -X POST -H 'Content-Type: application/json' \
  "http://localhost/containers/create?name=job-42" -d '{
    "Image": "alpine:3.20",
    "Cmd": ["sh", "-c", "echo step 1; sleep 2; echo step 2; exit 3"],
    "Labels": {"forgeci.job": "42"},
    "HostConfig": {"Memory": 268435456, "NanoCpus": 1000000000, "PidsLimit": 256}
  }' | jq -r .Id)

curl -s --unix-socket $S -X POST "http://localhost/containers/$ID/start"
curl -s --unix-socket $S -X POST "http://localhost/containers/$ID/wait"        # blocks → {"StatusCode":3,...}
curl -s --unix-socket $S "http://localhost/containers/$ID/logs?stdout=1&stderr=1" | cat -v   # note the 8-byte frame headers
curl -s --unix-socket $S "http://localhost/containers/json?all=1&filters=%7B%22label%22%3A%5B%22forgeci.job%22%5D%7D" | jq '.[].Names'
curl -s --unix-socket $S -X DELETE "http://localhost/containers/$ID?force=1&v=1"   # always clean up
```

Facts to know:
- Without a TTY, the logs endpoint returns a **multiplexed stream**: each frame has an 8-byte header (stream type + length).
  docker-java decodes it for you; that's why raw `curl` output shows odd bytes.
- `POST /containers/{id}/stop?t=10` = SIGTERM then SIGKILL after 10 s; `POST /containers/{id}/kill` = SIGKILL (or `?signal=…`).
  Timeout → kill; cancel → stop with a short grace period ([10-linux/commands.md §14](../10-linux/commands.md#14-processes-inside-containers-week-14-forgeci)).
- `wait` returns the exit code; `GET /containers/{id}/json` gives `State.OOMKilled` — both feed ForgeCI's app-vs-infra failure classification.
- The API is also reachable over TCP (`-H tcp://…`). Unprotected TCP exposure of the daemon is a well-known way servers get compromised — never do it without TLS client auth.

### The Docker-socket security trade-off

Access to `/var/run/docker.sock` is **root on the host**: anyone who can call the API can start a container that mounts
`/` and do anything. ForgeCI's worker needs it; the build containers must never get it.

Mitigations to consider and document in an ADR (Week 19 asks you to explain this for the AWS deploy):
- Workers run on a **dedicated** host/instance with nothing else valuable on it (no DB credentials beyond what the worker needs, minimal IAM role).
- Job containers: non-root user where possible, `--memory`/`--cpus`/`--pids-limit`, no `--privileged`, no socket mount, no host network, time limits, cleanup in `finally`.
- Restrict images (allowlist or pinned digests) and consider a socket proxy that allows only the endpoints the worker uses.
- Awareness: rootless Docker, gVisor/Kata/Firecracker-style sandboxes exist for stronger isolation — out of scope, but name them.

---

## Break it

1. Set `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/flowgrid` in the `api` service. Read the `Connection refused` stack trace; explain why; fix with `postgres`.
2. Remove `condition: service_healthy` and delay Postgres startup (`command: sh -c "sleep 10 && docker-entrypoint.sh postgres"`). Watch the api fail on startup, then get restarted.
3. `docker compose down -v`, then `up`. Where did your warehouses and orders go?
4. Remove `$$` in the Postgres healthcheck. Run `docker compose config` and see Compose interpolated it (to empty) at parse time.
5. Change `8080:8080` to `8080:9090`. The container is "running" but `curl localhost:8080` fails. Why?
6. Give `api` `mem_limit: 256m` with `-XX:MaxRAMPercentage=90`. Load it. Check `OOMKilled`.
7. (Week 14) From inside a container that has the socket mounted, start another container that bind-mounts a directory you created *inside the first container*. Look inside — it's empty (or missing). Explain using §6.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `localhost` to reach another container | service name |
| `depends_on` without healthcheck condition | `condition: service_healthy` |
| Committing `.env` | `.gitignore` + `.env.example` |
| Publishing DB ports on servers | no `ports:` for internal services |
| `down -v` as a habit | `down`; `-v` only when you mean "delete data" |
| Mixing up `HOST:CONTAINER` | host side first |
| No log size limits on long-running hosts | daemon `log-opts` `max-size`/`max-file`, or `logging:` per service |
| Mounting the Docker socket into job containers | only the worker; see §9 |

---

## Interview Q&A

<details><summary>Volume vs bind mount?</summary>

A named volume is storage managed by Docker — ideal for database data, portable across hosts. A bind mount maps a host
path into the container — useful for dev code/config and, in ForgeCI, job workspaces. `docker compose down -v` deletes
named volumes; bind-mounted folders stay.
</details>

<details><summary>How do containers in Compose find each other?</summary>

Compose puts services on a user-defined bridge network where Docker's DNS resolves service names, so the api connects to
`postgres:5432`. `localhost` inside a container is that container.
</details>

<details><summary>Does <code>depends_on</code> wait until Postgres is ready?</summary>

Not by default — it only orders container start. With a healthcheck (`pg_isready`) and `condition: service_healthy`,
Compose waits until it's healthy. The app should still retry connections.
</details>

<details><summary>What is a container, really?</summary>

A normal Linux process tree isolated by namespaces (what it can see) and constrained by cgroups (what it can use), running
on a layered copy-on-write filesystem built from the image. `dockerd` exposes an API; `containerd` and `runc` do the low-level work.
</details>

<details><summary>How does your CI worker run builds in containers, and what are the security risks?</summary>

The worker calls the Docker Engine API (docker-java) to create a container per job with resource limits and labels, runs
the steps, waits for the exit code, streams logs, and always removes the container. Socket access is root-equivalent, so
workers run on a dedicated host, job containers get no socket and no privileges, and images are restricted. (Describe
what *you* actually implemented.)
</details>

---

## Mastery checklist

- [ ] Write FlowGrid's Compose stack from scratch with healthchecks, conditions, volumes, `.env` interpolation and a `tools` profile.
- [ ] Explain the four "where is the DB" rows in §3.
- [ ] Run the full stack behind nginx; deep links work; no CORS config needed.
- [ ] Triage a crash loop in < 5 minutes using ps/logs/inspect/exec.
- [ ] (W14) Drive a container's full lifecycle through the Engine API with curl; explain multiplexed logs.
- [ ] (W14) Explain namespaces, cgroups, overlay2, and the socket security trade-off.
- [ ] (W18) Scale ForgeCI workers; kill one mid-job; explain what happened.
