# Containers at Runtime and Docker Compose v2

> Weeks 10 (Postgres only), 18 (TeamBoard full stack), 19 (PulseWatch stack). Covers ports, env vars, volumes,
> networks, Compose, and debugging — the things that break in practice.

---

## 1. Running containers: the flags that matter

```bash
docker run -d --name api \
  -p 8080:8080 \                               # publish host:container
  -e SPRING_PROFILES_ACTIVE=dev \              # env var
  --env-file ./api.env \                       # many env vars from a file
  -v pgdata:/var/lib/postgresql/data \         # named volume
  -v "$PWD/config:/app/config:ro" \            # bind mount, read-only
  --network pulsewatch-net \                   # user-defined network
  --restart unless-stopped \                   # restart policy
  --memory 512m --cpus 1 \                     # cgroup limits
  pulsewatch-api:dev
```

### Ports

`-p HOST:CONTAINER`. `EXPOSE` in a Dockerfile only documents. The app inside **must listen on `0.0.0.0`**, not
`127.0.0.1`, or the published port leads nowhere (Spring Boot binds all interfaces by default; Vite dev server does not — `--host`).

`-p 5432:5432` binds on **all host interfaces** — on a cloud VM that can expose Postgres to the internet (if the
security group allows). Prefer `-p 127.0.0.1:5432:5432` for dev-only access, or no published port at all for
services only other containers need.

### Environment variables

Spring Boot maps `SPRING_DATASOURCE_URL` → `spring.datasource.url` (relaxed binding). This is how one image runs in
every environment: config comes from the environment, not the image (12-factor).

---

## 2. Volumes vs bind mounts

| | Named volume (`pgdata:/var/lib/…`) | Bind mount (`./src:/app/src`) | tmpfs |
|---|---|---|---|
| Managed by | Docker (`/var/lib/docker/volumes/`) | you (a host path) | memory |
| Use for | **database data**, persistent app data | dev: live code/config; mounting config files | scratch, secrets in memory |
| Portability | same on every host | depends on host paths and permissions | — |
| Survives `docker compose down` | ✅ | ✅ (it's your folder) | ❌ |
| Survives `docker compose down -v` | ❌ **deleted** | ✅ | ❌ |

```bash
docker volume ls
docker volume inspect pulsewatch_pgdata
docker volume rm pulsewatch_pgdata
```

Without any volume, Postgres data lives in the container's writable layer: it survives `stop`/`start` but not `rm`
(and `docker compose down` removes containers).

> Postgres image note: for `postgres:16`, data lives in `/var/lib/postgresql/data`. Newer major versions (18+) changed the
> default layout — always check the image docs when bumping a major version, and never bump Postgres majors on an existing volume without a dump/restore or `pg_upgrade`.

---

## 3. Networks and service-name DNS

- Default `bridge` network: containers get IPs but **no name resolution** between them.
- **User-defined bridge** networks (Compose creates one per project, `<project>_default`): containers resolve each
  other **by service name** via Docker's embedded DNS.
- Inside a container, **`localhost` is the container itself**, not the host and not other containers.

```
┌──────────────────── network: pulsewatch_default ────────────────────┐
│  api  ──── jdbc:postgresql://postgres:5432/pulsewatch ───►  postgres │
│   └──────── redis:6379 ───────────────────────────────────►  redis   │
└─────────────────────────────────────────────────────────────────────┘
      ▲ -p 8080:8080
   host / browser: http://localhost:8080
```

| Where the app runs | Where Postgres runs | JDBC host |
|---|---|---|
| IDE on host | Compose, port published | `localhost:5432` |
| Compose service | Compose service | `postgres:5432` (service name, **container** port) |
| Container | host machine | `host.docker.internal` (Desktop; Linux: `extra_hosts: ["host.docker.internal:host-gateway"]`) |
| EC2 container | RDS | the RDS endpoint hostname |

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
```

---

## 5. PulseWatch stack (P4 M1): api, worker, postgres, redis

```yaml
# compose.yaml
name: pulsewatch

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: pulsewatch
      POSTGRES_USER: pulsewatch
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
    image: ghcr.io/${GHCR_OWNER:-local}/pulsewatch-api:${IMAGE_TAG:-dev}
    env_file: .env
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pulsewatch
      SPRING_DATASOURCE_USERNAME: pulsewatch
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

  worker:
    build: ./worker
    image: ghcr.io/${GHCR_OWNER:-local}/pulsewatch-worker:${IMAGE_TAG:-dev}
    env_file: .env
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/pulsewatch
      SPRING_DATASOURCE_USERNAME: pulsewatch
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      api:
        condition: service_healthy      # api runs Flyway migrations first; worker starts after
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
APP_ALERT_WEBHOOK_URL=http://localhost:9999/hook
```

Key points:
- **`.env` in the project dir** is read automatically for `${VAR}` interpolation in `compose.yaml`. `env_file:` passes
  variables **into the container**. Different mechanisms — know both.
- `${VAR:?message}` fails fast if missing; `${VAR:-default}` supplies a default.
- **`depends_on` alone only orders startup** — it doesn't wait for readiness. With `condition: service_healthy` Compose
  waits for the dependency's healthcheck. Your app should *still* tolerate the DB being briefly unavailable (Hikari retries on
  connection acquisition; restart policy covers the rest).
- `restart: unless-stopped` restarts on crash and after host reboot (if the Docker daemon starts at boot).

### Using it on EC2 with RDS (Week 21)

Create `compose.prod.yaml` overriding the datasource URL to the RDS endpoint and removing published DB ports; run
`docker compose -f compose.yaml -f compose.prod.yaml up -d`, or give the local `postgres` service `profiles: ["local-db"]`
so it isn't started in prod. Details: [12-aws/deploy-walkthrough.md](../12-aws/deploy-walkthrough.md).

---

## 6. TeamBoard full stack (P3 M5)

```yaml
# compose.yaml (TeamBoard)
name: teamboard
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: teamboard
      POSTGRES_USER: teamboard
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set in .env}
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U teamboard -d teamboard"]
      interval: 5s
      retries: 10

  api:
    build: ./backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/teamboard
      SPRING_DATASOURCE_USERNAME: teamboard
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      APP_JWT_SECRET: ${JWT_SECRET:?set in .env}
    depends_on:
      postgres: { condition: service_healthy }
    # no ports: only nginx talks to the api

  web:
    build: ./frontend              # multi-stage: node build → nginx (see dockerfiles.md §7)
    ports: ["8088:80"]
    depends_on: [api]

volumes:
  pgdata:
```

Browser → `http://localhost:8088` → nginx serves React; `/api/*` is proxied to `api:8080` on the Compose network.
Same origin → no CORS config needed in this setup ([08-react/04-api-integration-auth.md](../08-react/04-api-integration-auth.md)).

---

## 7. TicketHold dev setup (P2)

For daily development, run **only infrastructure** in Compose and the Spring app from the IDE (fast restarts,
debugger). Use `127.0.0.1:5432:5432` and `jdbc:postgresql://localhost:5432/tickethold`. Build and run the app image in
Compose occasionally to prove the container works. (Spring Boot 3.1+ can also start `compose.yaml` services for you via
`spring-boot-docker-compose` — optional.)

---

## 8. Debugging containers

```bash
docker compose ps                         # State/Status: running, restarting, exited (code), health
docker compose logs --tail=200 api        # last 200 lines; add -f to follow; --timestamps
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
2. `docker compose logs api` → first stack trace (scroll **up**, not just the last line).
3. Config? `docker compose config` + `exec env`.
4. Network/DNS? `exec … getent hosts postgres`, `nc -zv postgres 5432`.
5. Dependency not ready? healthcheck + `depends_on` condition.

---

## Break it

1. Set `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/pulsewatch` in the `api` service. Read the `Connection refused` stack trace; explain why; fix with `postgres`.
2. Remove the `condition: service_healthy` and add `sleep 10` before Postgres starts (e.g. `command: sh -c "sleep 10 && docker-entrypoint.sh postgres"`). Watch the api fail on startup, then be restarted.
3. `docker compose down -v`, then `up`. Where did your monitors go?
4. Remove `$$` in the Postgres healthcheck (`$POSTGRES_USER`). Run `docker compose config` and see Compose interpolated it (to empty) at parse time.
5. Change `-p 8080:8080` to `-p 8080:9090`. The container is "running" but `curl localhost:8080` fails. Why?
6. Give `api` `mem_limit: 256m` with `-XX:MaxRAMPercentage=90`. Load it. Check `OOMKilled`.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `localhost` to reach another container | service name |
| `depends_on` without healthcheck condition | `condition: service_healthy` |
| Committing `.env` | `.gitignore` + `.env.example` |
| Publishing DB ports on servers | no `ports:` for internal services |
| `down -v` as a habit | `down`; `-v` only when you mean "delete data" |
| Using the container port on the host side or vice versa | `HOST:CONTAINER` |
| No log size limits on long-running hosts | daemon `log-opts` `max-size`/`max-file`, or `logging:` per service |

---

## Interview Q&A

<details><summary>Volume vs bind mount?</summary>

A named volume is storage managed by Docker — ideal for database data, portable across hosts. A bind mount maps a host
path into the container — useful in dev for code/config. `docker compose down -v` deletes named volumes; bind-mounted
folders stay.
</details>

<details><summary>How do containers in Compose find each other?</summary>

Compose puts services on a user-defined bridge network where Docker's DNS resolves service names, so the api connects
to `postgres:5432`. `localhost` inside a container is that container.
</details>

<details><summary>Does <code>depends_on</code> wait until Postgres is ready?</summary>

Not by default — it only orders container start. With a healthcheck on Postgres (`pg_isready`) and
`condition: service_healthy`, Compose waits until it's healthy. The app should still retry connections.
</details>

<details><summary>How do you debug a container that keeps restarting?</summary>

`docker compose ps` for the exit code, `logs` for the first error, `inspect` for OOMKilled and health output, `exec`
or `run --entrypoint sh` to check env, files and DNS. 137 usually means OOM or a SIGKILL after a stop timeout.
</details>

<details><summary>How does your local setup differ from production?</summary>

Locally Compose runs Postgres and Redis containers; in production on EC2 the same api/worker images run with Compose
but point at RDS via environment variables, with no published DB ports and secrets from the environment rather than a local `.env`.
</details>

---

## Mastery checklist

- [ ] Write PulseWatch's `compose.yaml` from scratch with healthchecks, conditions, volumes, `.env` interpolation and a `tools` profile.
- [ ] Explain the four "where is the DB" rows in §3.
- [ ] Run TeamBoard's full stack behind nginx; deep links work; no CORS config needed.
- [ ] Triage a crash loop in < 5 minutes using ps/logs/inspect/exec.
- [ ] Explain volumes vs bind mounts and what `down -v` does.
