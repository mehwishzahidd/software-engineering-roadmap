# Dockerfiles and Images

> Week 5 (FlowGrid API image), Week 8 (frontend image, registry push), Week 14 (layers and internals for ForgeCI).
> Goal: a **small, secure, cache-friendly** image for a Spring Boot 3 / Java 21 service (FlowGrid, LedgerX, FlagForge API;
> ForgeCI api + worker) and for a React frontend, and the ability to explain every line. The Dockerfiles here are the
> *pattern*; write your own per project.

---

## 1. Images, containers, layers

```bash
docker pull postgres:16-alpine
docker image ls
docker history postgres:16-alpine          # the layers and the instruction that made each
docker run -d --name pg -e POSTGRES_PASSWORD=x postgres:16-alpine   # container from image
docker ps -a                               # running + stopped containers
docker rm -f pg                            # container gone; image stays
```

- An image is a stack of **read-only layers**. Each `RUN`, `COPY`, `ADD` creates a layer (other instructions add metadata).
- A container adds a thin **writable layer** on top. Delete the container → its writable layer is gone. Persistent data goes in **volumes** ([compose.md](./compose.md)).
- Layers are content-addressed and **shared**: ten images on `eclipse-temurin:21-jre-alpine` store the base once.

### Build cache

Docker reuses a cached layer if the instruction **and** its inputs (for `COPY`, the file contents) are unchanged,
and every layer before it was cached. **The first changed layer invalidates everything after it.**

Therefore: order instructions from **least** to **most** frequently changing. Dependencies (`pom.xml`,
`package-lock.json`) before source code.

---

## 2. Dockerfile instructions you need

| Instruction | Purpose | Notes |
|---|---|---|
| `FROM image:tag [AS name]` | base image / start a stage | pin tags; multi-stage uses several `FROM` |
| `WORKDIR /app` | set (and create) working dir | don't `RUN cd` |
| `COPY src dst` | copy from build context (or `--from=stage`) | prefer over `ADD` |
| `ADD` | like COPY + URL fetch + tar auto-extract | only when you need those features |
| `RUN cmd` | execute at **build** time, creates a layer | chain + clean in one `RUN` |
| `ENV K=V` | env var at build and run time | visible in `docker inspect` — **no secrets** |
| `ARG K=V` | build-time variable | also visible in history — no secrets |
| `EXPOSE 8080` | documentation of the listening port | does **not** publish; `-p` does |
| `USER app` | run subsequent steps and the container as this user | non-root |
| `ENTRYPOINT ["java","-jar","app.jar"]` | the executable | exec form (JSON array) |
| `CMD ["--spring.profiles.active=prod"]` | default args (or default command if no ENTRYPOINT) | overridden by `docker run image <args>` |
| `HEALTHCHECK CMD …` | container health | Compose can define it instead |
| `LABEL org.opencontainers.image.source=…` | metadata | GHCR uses this to link the repo |

### Exec form vs shell form

```dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]   # exec form: java is PID 1, receives SIGTERM directly ✅
ENTRYPOINT java -jar app.jar             # shell form: /bin/sh -c is PID 1; SIGTERM may not reach java ❌
```

With shell form, `docker stop` sends SIGTERM to `sh`, which typically doesn't forward it → 10 s later SIGKILL → no
graceful shutdown (in-flight requests dropped). See [10-linux/commands.md §6](../10-linux/commands.md#6-processes-and-signals).

---

## 3. The naive Dockerfile (and why it's bad)

```dockerfile
FROM maven:3.9-eclipse-temurin-21
WORKDIR /app
COPY . .
RUN mvn package -DskipTests
CMD mvn spring-boot:run
```

Problems: a large image (often several hundred MB) containing Maven, the JDK, sources and `~/.m2`; any source change re-downloads all
dependencies; runs as root; shell-form CMD; `COPY . .` drags in `target/`, `.git`, `.env`.

---

## 4. Multi-stage build for Spring Boot (the one to learn by heart)

```dockerfile
# syntax=docker/dockerfile:1

############ Stage 1: build ############
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1) Dependencies first → cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 2) Sources → only this and later layers rebuild on code changes
COPY src ./src
RUN mvn -B -q package -DskipTests      # tests run in CI before the image build (mvn verify)

############ Stage 2: runtime ############
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user (Alpine/BusyBox syntax)
RUN addgroup -S app && adduser -S -G app app

COPY --from=build /workspace/target/*.jar /app/app.jar

USER app
EXPOSE 8080

# Container-aware heap sizing: use 75% of the container memory limit
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

```bash
docker build -t flowgrid-api:dev .
docker image ls flowgrid-api            # compare with the naive build
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/flowgrid \
  -e SPRING_DATASOURCE_USERNAME=flowgrid -e SPRING_DATASOURCE_PASSWORD=flowgrid \
  flowgrid-api:dev
```

(`host.docker.internal` resolves to the host on Docker Desktop; on Linux add `--add-host=host.docker.internal:host-gateway`.
Usually you'll run both in Compose instead.)

Why each choice:
- **Build stage** has Maven + JDK; **runtime stage** has only a JRE + the jar → typically well under half the size. Measure both with `docker image ls` and record the numbers.
- `dependency:go-offline` layer caches most dependencies. (It misses a few plugin deps; the BuildKit alternative is
  `RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests`, which keeps `~/.m2` between builds.)
- `USER app` → a container escape or RCE doesn't start as root.
- `MaxRAMPercentage` → the JVM respects the container's memory limit (JDK 10+ is container-aware; the default max heap is only 25%).
- Base image choice: `-alpine` is smallest (musl libc); if a native library needs glibc, use `eclipse-temurin:21-jre` (Ubuntu)
  and create the user with `groupadd --system app && useradd --system --gid app --no-create-home app`.
- Tests are **not** skipped overall — CI runs `mvn verify` first ([13-cicd/pipeline-examples.md](../13-cicd/pipeline-examples.md)).

---

## 5. Layered jars (awareness)

A fat jar changes entirely on every build, so the whole jar layer (tens of MB) is re-pushed. Spring Boot can split it into layers
that change at different rates: `dependencies`, `spring-boot-loader`, `snapshot-dependencies`, `application`.
Only the small `application` layer changes on a typical commit → faster pushes/pulls.

```dockerfile
# Spring Boot 3.3+ (jarmode "tools")
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /builder
COPY --from=build /workspace/target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /application
COPY --from=extract /builder/extracted/dependencies/ ./
COPY --from=extract /builder/extracted/spring-boot-loader/ ./
COPY --from=extract /builder/extracted/snapshot-dependencies/ ./
COPY --from=extract /builder/extracted/application/ ./
USER app
ENTRYPOINT ["java", "-jar", "application.jar"]
```

Before Boot 3.3 the equivalent was `java -Djarmode=layertools -jar app.jar extract` with
`ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]` (class name for Boot 3.2+).
Alternative without a Dockerfile: **Cloud Native Buildpacks** — `mvn spring-boot:build-image`. Know it exists; the
Dockerfile teaches you more.

---

## 6. `.dockerignore`

The build context is sent to the builder. Exclude what isn't needed — smaller, faster, and no leaked secrets.

```gitignore
# .dockerignore (Spring Boot service)
target/
.git/
.idea/
*.iml
.vscode/
.env
*.env
docker-compose*.yml
compose*.yaml
**/*.log
node_modules/
```

> **Break it:** remove `.env` from `.dockerignore`, `COPY . .` in a scratch Dockerfile, build, then
> `docker run --rm image cat .env` — your secrets are inside the image forever (and in every layer pushed to a registry).

---

## 7. Frontend image (nginx + static build)

```dockerfile
# syntax=docker/dockerfile:1
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci                                   # reproducible install from the lockfile
COPY . .
ARG VITE_API_URL=""                          # build-time: inlined into the bundle (public!)
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build                            # → /app/dist

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

```nginx
# nginx.conf — SPA + API reverse proxy on one origin (no CORS needed in prod)
server {
  listen 80;
  root /usr/share/nginx/html;

  location /api/ {
    proxy_pass http://api:8080;              # "api" = Compose service name
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  location / {
    try_files $uri $uri/ /index.html;        # client-side routes → index.html (see 08-react/03)
  }
}
```

Final image: nginx + static files — small (tens of MB), no Node at runtime. `.dockerignore` for the web app must exclude `node_modules/` and `dist/`.

---

## 8. Image size and security checklist

| Practice | Why |
|---|---|
| Multi-stage; runtime = JRE / nginx only | fewer MB, fewer CVEs, no build tools for attackers |
| Pin base tags (`21-jre-alpine`, `16-alpine`); digests for strict reproducibility | builds don't change under you |
| Non-root `USER` | limits blast radius |
| No secrets in `ENV`/`ARG`/files | anyone with the image can read them (`docker history`, `inspect`) — pass at runtime |
| `.dockerignore` | no `.git`, `.env`, `target/` |
| One `RUN` for install + cleanup (`apt-get update && apt-get install -y --no-install-recommends … && rm -rf /var/lib/apt/lists/*`) | deleting in a later layer doesn't shrink the image |
| Scan images (`docker scout cves`, Trivy) in CI | catch known CVEs |
| Rebuild regularly | pick up patched base images |
| Minimal extras (`curl`, shells) in production images | smaller attack surface (trade-off: debuggability) |

---

## 9. Tagging and pushing to a registry

Tag with **immutable** identifiers (git SHA, semver) — never deploy `latest`.

```bash
# GitHub Container Registry (GHCR)
echo "$GHCR_TOKEN" | docker login ghcr.io -u <github-username> --password-stdin   # PAT with write:packages; in Actions use GITHUB_TOKEN
docker tag flowgrid-api:dev ghcr.io/<github-username>/flowgrid-api:1.0.0
docker tag flowgrid-api:dev ghcr.io/<github-username>/flowgrid-api:$(git rev-parse --short HEAD)
docker push ghcr.io/<github-username>/flowgrid-api:1.0.0
docker push ghcr.io/<github-username>/flowgrid-api:$(git rev-parse --short HEAD)

# Amazon ECR
aws ecr create-repository --repository-name flowgrid-api --region eu-central-1
aws ecr get-login-password --region eu-central-1 \
  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.eu-central-1.amazonaws.com
docker tag flowgrid-api:dev <account-id>.dkr.ecr.eu-central-1.amazonaws.com/flowgrid-api:1.0.0
docker push <account-id>.dkr.ecr.eu-central-1.amazonaws.com/flowgrid-api:1.0.0
```

On EC2, pulling from ECR uses the instance's **IAM role** (no keys on the box) — [12-aws/iam.md](../12-aws/iam.md).
CI automation of build → push: [13-cicd/pipeline-examples.md](../13-cicd/pipeline-examples.md).

### CPU architecture

An image built on an Apple-silicon Mac is `linux/arm64`; an x86 EC2 instance needs `linux/amd64` → `exec format error`.

```bash
docker buildx build --platform linux/amd64 -t ghcr.io/<you>/flowgrid-api:1.0.0 --push .
# or build in CI on an amd64 runner; or choose a Graviton (arm64) instance deliberately
```

---

## Break it

1. Put `COPY src ./src` **before** `COPY pom.xml` + `dependency:go-offline`. Change one Java file, rebuild, and time it. Restore the order and compare.
2. Switch `ENTRYPOINT` to shell form, `docker stop` the container, and time it (≈10 s, then exit code 137). Exec form: fast, exit 143 (or 0 if the app handles it gracefully).
3. Remove `USER app`, run `docker exec <c> id` → `uid=0(root)`. Put it back and try to write to `/` inside the container.
4. Run the container with `--memory=256m` and no `MaxRAMPercentage`, load it, and check `docker inspect --format '{{.State.OOMKilled}}'`.
5. `docker history --no-trunc` an image built with `ENV DB_PASSWORD=secret` — find the password.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| Single-stage JDK+Maven image | multi-stage, JRE runtime |
| `COPY . .` first | deps manifest first, then sources |
| Shell-form `ENTRYPOINT` | exec form |
| Running as root | `USER` |
| `latest` tags | pinned base; SHA/semver app tags |
| Secrets baked into images | runtime env vars / secret stores |
| `apt-get install` in one `RUN`, cleanup in another | same `RUN` |

---

## Interview Q&A

<details><summary>Image vs container?</summary>

An image is an immutable, layered filesystem plus metadata; a container is a running instance of it with its own
writable layer, process namespace and network. Many containers can run from one image; deleting a container loses its writable layer.
</details>

<details><summary>Why a multi-stage build?</summary>

To build with heavy tools (Maven, JDK, Node) and ship only what runs (JRE + jar, or nginx + static files). My
FlowGrid image dropped from the naive build's size to a JRE-only image and no longer contains sources or build tools — quote your own measured `docker image ls` numbers, never estimates.
</details>

<details><summary>How does layer caching affect how you write a Dockerfile?</summary>

A changed layer invalidates all later layers, so I copy dependency manifests and resolve dependencies before copying
source code. Code changes then rebuild only the last layers.
</details>

<details><summary>Why exec-form <code>ENTRYPOINT</code>?</summary>

So the JVM is PID 1 and receives SIGTERM from `docker stop`, letting Spring Boot shut down gracefully. With shell
form, `sh` is PID 1 and usually doesn't forward the signal, so the container is SIGKILLed after the timeout.
</details>

<details><summary>How do you handle secrets with Docker?</summary>

Never in the image. Inject at runtime: env vars from an untracked `.env` for local dev, and on AWS from SSM Parameter
Store/Secrets Manager or the platform's secret mechanism; in CI, repository secrets.
</details>

<details><summary>Container vs VM?</summary>

Containers share the host kernel and isolate processes with namespaces and cgroups — lightweight, fast to start.
VMs run a full guest OS on virtualised hardware — stronger isolation, more overhead.
</details>

---

## Mastery checklist

- [ ] Write the multi-stage Spring Boot Dockerfile from memory; explain every line.
- [ ] Measure image size before/after and the rebuild time after a one-line code change.
- [ ] Non-root user, exec-form entrypoint, `.dockerignore`, pinned base.
- [ ] Build the FlowGrid dashboard nginx image with SPA fallback and `/api` proxy.
- [ ] Push an image to GHCR (and/or ECR) with a SHA tag.
- [ ] Explain layered jars and when they help.
