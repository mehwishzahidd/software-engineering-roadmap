# 11 — Docker

> **Minimal intro in Week 10** (run Postgres for P2 TicketHold via Compose) → **Dockerfile for TicketHold in Week 12**
> → **full-stack Compose for TeamBoard in Week 18** → **deep treatment in Week 19** (PulseWatch stack: api, worker,
> postgres, redis) → **images pushed by CI and run on EC2 in Weeks 21–22**.

Docker is on your résumé. Interviewers will ask what an image is, why your image is 700 MB, why the app can't reach
the DB at `localhost`, and how data survives a restart. After this folder you answer from things you've built and broken.

---

## Files

| File | Content | Week |
|---|---|---:|
| [dockerfiles.md](./dockerfiles.md) | Images vs containers, layers & cache, instructions, multi-stage Spring Boot build, non-root, layered jars, `.dockerignore`, image size & security, registries (GHCR/ECR) | 12, 19 |
| [compose.md](./compose.md) | Ports, env vars, volumes vs bind mounts, networks & service DNS, Compose v2, healthchecks + `depends_on`, profiles, stacks for TicketHold / TeamBoard / PulseWatch, debugging | 10, 18, 19 |
| [exercises.md](./exercises.md) | 18 build / break / debug tasks | 19 |

---

## Week 10 minimum (≈ 1 hour) — just enough to run Postgres

```bash
docker --version && docker compose version   # Compose v2 is "docker compose" (space), not "docker-compose"
```

```yaml
# tickethold/compose.yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: tickethold
      POSTGRES_USER: tickethold
      POSTGRES_PASSWORD: tickethold      # dev only
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
docker compose exec postgres psql -U tickethold -d tickethold
docker compose down             # stop + remove containers (volume kept)
docker compose down -v          # ALSO deletes the volume → data gone
```

That's all Week 10 needs. Come back for the rest in Week 19.

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

**Container ≠ VM.** Containers share the host kernel and are isolated with namespaces (PID, network, mount, user…) and
limited with cgroups (CPU, memory). They start in milliseconds and add little overhead; a VM virtualises hardware and runs its own kernel.
On macOS/Windows, Docker Desktop runs a small Linux VM to host containers.

---

## Week 19 plan (Docker half)

| Day | Block |
|---|---|
| Mon–Tue | (Linux, see [10-linux](../10-linux/README.md)) |
| Wed | [dockerfiles.md](./dockerfiles.md) — rebuild TicketHold's Dockerfile as multi-stage, non-root, measure size |
| Thu | [compose.md](./compose.md) — PulseWatch Compose stack (api, worker, postgres, redis) with healthchecks |
| Sat | P4 M1 on the stack + [exercises.md](./exercises.md) break/debug drills |
| Sun | Review: explain image vs container, layers, volumes, networks out loud |

---

## Mapping to projects

| Project | Docker deliverable |
|---|---|
| P2 TicketHold (W10, W12) | Postgres via Compose; multi-stage Dockerfile; image built in CI |
| P3 TeamBoard (W18) | Compose full stack: nginx (built React + `/api` proxy) + Spring API + Postgres |
| P4 PulseWatch (W19–22) | Compose: api, worker, postgres, redis; images pushed to GHCR/ECR by GitHub Actions; the same Compose file runs on EC2 with RDS replacing the postgres service |

---

## Interview relevance

- Image vs container; what's a layer; why order Dockerfile instructions carefully?
- Multi-stage builds — why? How big is your image and why?
- Volume vs bind mount; what happens to data on `docker compose down -v`?
- How do containers talk to each other? Why doesn't `localhost` work between containers?
- How do you debug a container that keeps restarting?
- Container vs VM.
- Security: why non-root? Where do secrets go?

Résumé defense: [17-resume-tech-defense/docker.md](../17-resume-tech-defense/docker.md).

---

## Resources

- docs.docker.com — "Get started", Dockerfile reference, Compose file reference, "Building best practices".
- Spring Boot reference — "Container Images" section (efficient images, layered jars, Buildpacks).
- *Docker Deep Dive* (Nigel Poulton) — concise book.
- More in [RESOURCES.md](../RESOURCES.md).

---

## Exit criteria (end of Week 19)

- [ ] TicketHold and PulseWatch images: multi-stage, non-root, pinned base, `.dockerignore`, < 300 MB (JRE-based).
- [ ] PulseWatch `compose.yaml`: api, worker, postgres, redis; healthchecks; `depends_on: condition: service_healthy`; named volume; `.env` not committed.
- [ ] I can explain every line of both files.
- [ ] I've reproduced and fixed "can't reach DB at localhost" and "data lost after `down -v`".
- [ ] I can debug a crashing container with `logs`, `inspect`, `exec`, and exit codes.
- [ ] I've pushed an image to GHCR (or ECR) and pulled it on another machine.
