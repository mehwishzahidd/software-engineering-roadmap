# Docker — Résumé Tech Defense

> **Goal:** defend "Docker" with truthful past context and current competence with images, layers,
> multi-stage builds, volumes, networks and Docker Compose v2 — shown by TicketHold's Dockerfile,
> TeamBoard's full-stack Compose, and PulseWatch's 4-service stack running on EC2.
>
> **Honesty rule:** "I ran `docker compose up` on a project someone else containerised" is a
> valid, honest past description. Your authoring skill is demonstrated by the Dockerfiles and
> Compose files you wrote here.

---

## Evidence in my projects

| Project | Docker evidence |
|---|---|
| **P2 TicketHold** M2 (W10) | `compose.yaml` running Postgres 16 with a named volume + healthcheck for local dev |
| **P2** M4 (W12) | Multi-stage `Dockerfile` (Maven build → JRE runtime, non-root user); Testcontainers in tests |
| **P3 TeamBoard** M5 (W18) | Full-stack Compose: `nginx` (serving React build + reverse proxy `/api`), `api`, `postgres`; `depends_on: condition: service_healthy` |
| **P4 PulseWatch** M1/M3 (W19–21) | `api`, `worker`, `postgres`, `redis` Compose stack locally; on EC2 with RDS replacing the Postgres container; `awslogs` driver |
| **P4** M4 (W22) | CI builds and pushes tagged images (`:sha`), deploy pulls by tag |

## Where to learn it in this repo

- [`../11-docker/README.md`](../11-docker/README.md)
- [`../11-docker/dockerfiles.md`](../11-docker/dockerfiles.md) — layers, caching, multi-stage, non-root
- [`../11-docker/compose.md`](../11-docker/compose.md) — services, networks, volumes, healthchecks
- [`../11-docker/exercises.md`](../11-docker/exercises.md)
- [`../09-testing/testcontainers.md`](../09-testing/testcontainers.md)
- Related: [`linux.md`](./linux.md), [`aws.md`](./aws.md)

---

## 1. Beginner questions

<details><summary><b>B1. Image vs container?</b></summary>

An image is an immutable, layered filesystem + metadata (entrypoint, env, exposed ports). A container is a running (or stopped) instance of an image with its own writable layer, process namespace and network namespace.
</details>

<details><summary><b>B2. Container vs virtual machine?</b></summary>

Containers share the host kernel, isolated with namespaces and limited with cgroups — start in milliseconds, MBs in size. VMs virtualise hardware and run a full guest OS — stronger isolation, heavier. On macOS/Windows Docker Desktop runs a Linux VM underneath.
</details>

<details><summary><b>B3. What are layers and how does build caching work?</b></summary>

Each filesystem-changing instruction (`RUN`, `COPY`, `ADD`) creates a layer. Cache reuses a layer if the instruction and its inputs are unchanged; once one layer misses, all later layers rebuild. So copy dependency manifests (`pom.xml`) and download deps **before** copying source.
</details>

<details><summary><b>B4. <code>CMD</code> vs <code>ENTRYPOINT</code>?</b></summary>

`ENTRYPOINT` is the executable; `CMD` provides default args (or the default command if no entrypoint). `docker run img args` replaces `CMD`. Use exec form (`["java","-jar","app.jar"]`) so the process is PID 1 and receives SIGTERM.
</details>

<details><summary><b>B5. Volumes vs bind mounts?</b></summary>

Named volume: Docker-managed storage, survives container removal (Postgres data). Bind mount: a host path mounted in (dev source code, config). tmpfs: memory only.
</details>

<details><summary><b>B6. What is Docker Compose?</b></summary>

Declarative multi-container definition (`compose.yaml`) run with `docker compose up` (v2, plugin, no hyphen). Creates a default network where services resolve each other by service name (`jdbc:postgresql://postgres:5432/app`).
</details>

## 2. Intermediate questions

<details><summary><b>I1. Walk through your multi-stage Spring Boot Dockerfile.</b></summary>

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
```
Why: small runtime image (no Maven/JDK), cached deps layer, non-root, container-aware heap. Tests run in CI before this, hence `-DskipTests` here.
</details>

<details><summary><b>I2. How does container networking work in Compose?</b></summary>

Compose creates a user-defined bridge network; embedded DNS resolves service names. `ports: "8080:8080"` publishes to the host; containers talk on internal ports without publishing. `localhost` inside a container is the container itself — classic bug.
</details>

<details><summary><b>I3. <code>depends_on</code> — does it wait for the DB to be ready?</b></summary>

Only for start order by default. Use a `healthcheck` on Postgres (`pg_isready`) and `depends_on: { postgres: { condition: service_healthy } }`. The app should still retry connections (Hikari does on startup within limits).
</details>

<details><summary><b>I4. How do you make images smaller and more secure?</b></summary>

Multi-stage, slim/distroless base, `.dockerignore` (exclude `target/`, `node_modules/`, `.git`), combine `RUN` steps and clean caches, non-root `USER`, pin versions/digests, scan (`docker scout`/Trivy), no secrets in layers (they persist in history even if deleted later — use BuildKit `--mount=type=secret`).
</details>

<details><summary><b>I5. How are config and secrets passed?</b></summary>

Environment variables (`SPRING_DATASOURCE_URL`), `env_file`, Compose `secrets`, or fetched at runtime from SSM/Secrets Manager. Spring's relaxed binding maps `SPRING_DATASOURCE_URL` → `spring.datasource.url`.
</details>

<details><summary><b>I6. What happens on <code>docker stop</code>?</b></summary>

SIGTERM to PID 1, wait 10 s (configurable), then SIGKILL. Spring Boot graceful shutdown (`server.shutdown=graceful`) finishes in-flight requests. Shell-form `CMD` makes `/bin/sh` PID 1, which may not forward signals.
</details>

<details><summary><b>I7. What's the JVM-in-container memory issue?</b></summary>

Modern JDKs are container-aware (cgroup limits). Default max heap is 25% of container memory; set `-XX:MaxRAMPercentage`. Exceeding the limit → OOM-killed (exit 137), not a Java `OutOfMemoryError`.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "What did Docker solve for your application?"</b></summary>

Reproducible environments (same Postgres 16 / Redis 7 for everyone, one command), parity between dev/CI/prod (same image tested then deployed), Testcontainers for real-DB integration tests, simple deploy on EC2 (pull + `compose up`), isolation of dependencies. Be honest about what it didn't solve: orchestration, HA, secrets management.
</details>

<details><summary><b>R2. "Your résumé says Docker — what did you do with it?"</b></summary>

Truthful past scope → current: wrote TicketHold's multi-stage Dockerfile, TeamBoard's 3-service Compose, PulseWatch's stack on EC2, CI image build.
</details>

<details><summary><b>R3. "How do your containers talk to each other?"</b></summary>

Compose network + service-name DNS; nginx `proxy_pass http://api:8080`; api → `postgres:5432`, `redis:6379`. Only nginx publishes ports.
</details>

<details><summary><b>R4. "Docker vs Kubernetes?"</b></summary>

Docker builds/runs containers; Compose runs multi-container apps on one host. Kubernetes orchestrates across many hosts: scheduling, self-healing, rolling updates, service discovery, autoscaling. PulseWatch doesn't need K8s; say when you'd move (multiple hosts, HA SLAs, many services).
</details>

<details><summary><b>R5. "How do you persist Postgres data in Docker?"</b></summary>

Named volume at `/var/lib/postgresql/data`; `docker compose down` keeps it, `down -v` deletes it. In prod, RDS instead of a container DB.
</details>

## 4. Practical tasks (doable live)

1. Write a Dockerfile for a Spring Boot jar from scratch; explain every line.
2. Write a Compose file: api + postgres + redis with healthchecks and a named volume.
3. Reorder a slow Dockerfile to maximise cache hits.
4. `docker exec` into the DB container and run `psql`.
5. Inspect an image's layers (`docker history`) and size.

## 5. Debugging questions

<details><summary><b>D1. App container: "Connection refused localhost:5432".</b></summary>

Inside the container `localhost` is itself. Use `postgres:5432` (service name). Also check healthcheck/readiness.
</details>

<details><summary><b>D2. Container exits immediately with code 137.</b></summary>

SIGKILL — usually OOM-killed (`docker inspect` → `OOMKilled: true`) or stop timeout. Raise memory limit or tune heap via `MaxRAMPercentage`.
</details>

<details><summary><b>D3. Every build re-downloads all Maven dependencies.</b></summary>

`COPY . .` before `mvn dependency:go-offline` invalidates cache on any source change. Copy `pom.xml` first; or BuildKit cache mount `--mount=type=cache,target=/root/.m2`.
</details>

<details><summary><b>D4. Code changes don't show up after <code>compose up</code>.</b></summary>

Old image reused; use `docker compose up --build` or `docker compose build --no-cache api`.
</details>

<details><summary><b>D5. Postgres data vanished after restart.</b></summary>

No volume (data in container writable layer) or `down -v` was run. Add named volume.
</details>

## 6. Architecture questions

<details><summary><b>A1. One container per process — why?</b></summary>

Independent scaling, restarts, logs, and images. PulseWatch splits `api` and `worker` from the same codebase (different Spring profiles) so the worker can be scaled/paused independently.
</details>

<details><summary><b>A2. How do you version and promote images?</b></summary>

Tag with git SHA (immutable) + optionally semver; CI builds once, tests, pushes; deploy references the SHA. Never deploy `:latest` blindly — you can't tell what's running or roll back cleanly.
</details>

## 7. Common mistakes

- Secrets in `ENV` or copied files baked into the image.
- Running as root.
- Missing `.dockerignore` (huge context, leaked `.env`).
- Shell-form entrypoint → no graceful shutdown.
- Using `localhost` between containers.
- Treating containers as pets (SSH-ing in to patch).
- `:latest` tags in production.

## 8. Terminology I must know

| Term | Meaning |
|---|---|
| Image / layer | Immutable layered filesystem |
| Container | Running instance + writable layer |
| Registry | Image store (Docker Hub, GHCR, ECR) |
| Tag / digest | Mutable name / immutable content hash |
| Multi-stage build | Several `FROM`s; copy artefacts forward |
| Build context | Files sent to the builder |
| BuildKit | Modern builder: cache mounts, secrets |
| Namespaces / cgroups | Isolation / resource limits |
| Bridge network | Default container network with DNS in user-defined ones |
| Named volume | Docker-managed persistent storage |
| Healthcheck | Command determining container health |
| PID 1 | Process that receives signals |

## 9. When to use it

- Local dependencies (Postgres, Redis) and integration tests.
- Shipping a service with its runtime to any host/CI.
- Standardising dev environments.

## 10. When NOT to use it

- Managed platforms already handling runtime (e.g. Lambda zip deploys) where images add nothing.
- Stateful production databases when a managed service (RDS) is available.
- As a security boundary for untrusted code (use VMs/sandboxes).

## 11. Trade-offs

| Gain | Cost |
|---|---|
| Reproducibility, parity | Image build time, registry management |
| Isolation of deps | Another layer to debug (networking, volumes) |
| Fast startup vs VMs | Weaker isolation than VMs |
| Compose simplicity | Single host, no self-healing |

## 12. How it interacts with the rest of my stack

- **Java/Spring Boot:** jar in JRE image; env-var config; graceful shutdown; Actuator health for container healthchecks.
- **Postgres/Redis:** official images for dev + Testcontainers; RDS in prod.
- **React:** Node build stage → nginx image.
- **Linux:** containers are Linux processes; `ps`, signals, permissions all apply.
- **AWS:** EC2 runs Compose; logs via `awslogs`; images from a registry.
- **CI:** `docker build`/`push` in GitHub Actions with `docker/build-push-action` + layer cache.

## 13. One small hands-on exercise

**Containerise TicketHold end to end.**

- [ ] Multi-stage Dockerfile, final image < 250 MB, non-root.
- [ ] `compose.yaml` with api + postgres (healthcheck, named volume), api waits for healthy DB.
- [ ] `docker compose up --build` from a clean clone gives a working API (`curl /actuator/health` → UP).
- [ ] `docker stop api` logs graceful shutdown.
- [ ] Rebuild after a one-line Java change reuses the dependency layer (show build output).

## 14. Mastery checklist

- [ ] Write a Spring Boot Dockerfile from memory and explain layers
- [ ] Write a Compose file with healthchecks, volumes, env
- [ ] Explain container vs VM, namespaces, cgroups
- [ ] Diagnose exit 137, "connection refused", cache misses
- [ ] Explain image tagging/promotion in my CI
- [ ] Truthful 60-second answer on past Docker use + project bridge
