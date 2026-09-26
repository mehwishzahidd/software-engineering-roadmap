# 11 — Docker

> **Week 4:** minimal Compose to run Postgres for FlowGrid M1 → **Week 5:** Dockerfiles and multi-stage builds (FlowGrid's
> API image) → **Week 8:** images built by CI, pushed to GHCR and run on EC2 → **Week 14:** images, layers, networking and
> the **Docker Engine API** in depth, because ForgeCI's workers *start containers programmatically* → **Week 18:**
> multi-worker Compose stack for ForgeCI. See [ROADMAP §6](../ROADMAP.md#6-just-in-time-learning-map).

Docker is on your résumé. Interviewers will ask what an image is, why your image is 700 MB, why the app can't reach
the DB at `localhost`, how data survives a restart — and, once you've built ForgeCI, what it means to mount the Docker
socket into a container. After this folder you answer from things you've built and broken.

---

## Files

| File | Content | Weeks |
|---|---|---:|
| [dockerfiles.md](./dockerfiles.md) | Images vs containers, layers & cache, instructions, multi-stage Spring Boot build, non-root, layered jars, `.dockerignore`, frontend image, image size & security, registries (GHCR/ECR), CPU architecture | 5, 8, 14 |
| [compose.md](./compose.md) | Ports, env vars, volumes vs bind mounts, networks & service DNS, Compose v2, healthchecks + `depends_on`, profiles, stacks per project, debugging, **internals + Engine API** | 4, 5, 14, 18 |
| [exercises.md](./exercises.md) | 18 build / break / debug tasks | 4–5, 14, 18 |

---

## Week 4 minimum (≈ 1 hour) — just enough to run Postgres for FlowGrid

```bash
docker --version && docker compose version   # Compose v2 is "docker compose" (space), not "docker-compose"
```

```yaml
# flowgrid/compose.yaml — infrastructure only; the API runs from your IDE for now
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: flowgrid
      POSTGRES_USER: flowgrid
      POSTGRES_PASSWORD: flowgrid        # dev only
    ports:
      - "127.0.0.1:5432:5432"            # reachable from your IDE-run Spring app at localhost:5432
    volumes:
      - pgdata:/var/lib/postgresql/data
volumes:
  pgdata:
```

```bash
docker compose up -d            # start in background
docker compose ps               # status
docker compose logs -f postgres # logs
docker compose exec postgres psql -U flowgrid -d flowgrid
docker compose down             # stop + remove containers (volume kept)
docker compose down -v          # ALSO deletes the volume → data gone
```

That's all Week 4 needs. Week 5 adds the Dockerfile; Week 6 adds a `redis` service.

---

## Core vocabulary

| Term | Meaning | Java analogy |
|---|---|---|
| **Image** | read-only template: filesystem layers + metadata (cmd, env, ports) | a class / a built JAR |
| **Container** | a running (or stopped) instance of an image with its own writable layer, process space, network | an object / a running JVM |
| **Layer** | one filesystem diff produced by a build instruction; cached and shared between images | — |
| **Registry** | stores images (Docker Hub, GHCR, ECR) | Maven Central |
| **Tag** | human label for an image version (`16-alpine`, `1.2.0`, git SHA) | version |
| **Digest** | immutable content hash (`sha256:…`) | checksum |
| **Volume** | Docker-managed persistent storage | — |
| **Network** | virtual network; containers on the same user-defined network resolve each other by service name | — |
| **Docker daemon / Engine API** | `dockerd` does the work; the CLI (and ForgeCI's worker) talk to it over a REST API on a Unix socket | — |

**Container ≠ VM.** Containers share the host kernel and are isolated with namespaces (PID, network, mount, user…) and
limited with cgroups (CPU, memory). They start in milliseconds and add little overhead; a VM virtualises hardware and runs its own kernel.
On macOS/Windows, Docker Desktop runs a small Linux VM to host containers.

---

## Where each part is learned

| Week | Learning block | Project use |
|---|---|---|
| 4 | README (this page) | FlowGrid M1: Postgres in Compose; CI runs Testcontainers |
| 5 | [dockerfiles.md](./dockerfiles.md) §1–§6 | FlowGrid M2: multi-stage, non-root API image |
| 6–8 | [compose.md](./compose.md) §1–§7, [dockerfiles.md](./dockerfiles.md) §7–§9 | Redis service; full stack (api, web/nginx, postgres, redis); push to GHCR; run on EC2 |
| 14 | [compose.md](./compose.md) §9 (internals + Engine API), [exercises.md](./exercises.md) Part C | ForgeCI M1–M2: worker creates containers via the Engine API |
| 18 | [compose.md](./compose.md) §6 (multi-worker) | ForgeCI M5: api + N workers + postgres + redis + ui |

---

## Interview relevance

- Image vs container; what's a layer; why order Dockerfile instructions carefully?
- Multi-stage builds — why? How big is your image and why?
- Volume vs bind mount; what happens to data on `docker compose down -v`?
- How do containers talk to each other? Why doesn't `localhost` work between containers?
- How do you debug a container that keeps restarting?
- Container vs VM; namespaces and cgroups.
- Security: why non-root? Where do secrets go? Why is mounting `/var/run/docker.sock` equivalent to root on the host?

Résumé defense: [17-resume-tech-defense/docker.md](../17-resume-tech-defense/docker.md).

---

## Resources

- docs.docker.com — "Get started", Dockerfile reference, Compose file reference, "Building best practices", Engine API reference.
- Spring Boot reference — "Container Images" section (efficient images, layered jars, Buildpacks).
- docker-java (github.com/docker-java/docker-java) — the Java client ForgeCI uses.
- *Docker Deep Dive* (Nigel Poulton) — concise book.
- More in [RESOURCES.md](../RESOURCES.md).

---

## Exit criteria

By Week 8 (FlowGrid v1.0):
- [ ] FlowGrid API image: multi-stage, non-root, pinned base, `.dockerignore`, JRE runtime, exec-form entrypoint.
- [ ] Compose stack with healthchecks, `depends_on: condition: service_healthy`, named volumes, `.env` not committed.
- [ ] I've reproduced and fixed "can't reach DB at localhost" and "data lost after `down -v`".
- [ ] Image pushed to GHCR by CI and pulled on EC2.

By Week 14/18 (ForgeCI):
- [ ] I can explain namespaces, cgroups, layers/overlay filesystem and the Engine API at interview depth.
- [ ] I've created, started, waited for, logged, killed and removed a container through the Engine API (curl, then docker-java).
- [ ] I can explain the Docker-socket security trade-off and what I did about it.
- [ ] Multi-worker Compose stack runs; I can scale workers and explain what happens to in-flight jobs when one dies.
