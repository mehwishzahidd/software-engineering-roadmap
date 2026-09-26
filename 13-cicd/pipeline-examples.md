# Pipeline Examples — FlowGrid, ForgeCI, deploy

> Complete workflows you can drop into `.github/workflows/`. **Pipelines are infrastructure, not the
> project's engineering problem**, so full YAML is given here — but read every line; each is explained
> in [github-actions.md](./github-actions.md). Adjust paths to your actual repo layout.

← [GitHub Actions](./github-actions.md) · [CI/CD README](./README.md)

| # | Workflow | Week | File |
|---|---|---|---|
| 1 | FlowGrid CI — backend `mvn verify` + frontend lint/test/build | 4 (backend), 7 (frontend job added) | `.github/workflows/ci.yml` |
| 2 | ForgeCI — multi-module (api + worker) test, then build & push both images | 18 | `.github/workflows/ci.yml` |
| 3 | Deploy job — FlowGrid to EC2 with health gate and rollback (+ SSH variant, manual rollback, ForgeCI ordering) | 8, 18–19 | `.github/workflows/deploy.yml`, `rollback.yml` |

LedgerX (W9–13) and FlagForge (W20–23) reuse #1 and #3 almost unchanged — FlagForge adds its SDK module to the Maven build.
Action versions below are current majors at the time of writing; check each action's README and let Dependabot bump them.

---

## 1. FlowGrid CI — backend + frontend

Assumed layout: Maven project at the repo root (Spring Boot), React + TS (Vite) in `frontend/` with these scripts:

```json
{
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "lint": "eslint .",
    "test": "vitest"
  }
}
```

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  backend:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Build, unit + integration tests (Testcontainers uses the runner's Docker)
        run: mvn -B -ntp verify

      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: backend-test-reports
          path: |
            target/surefire-reports/
            target/failsafe-reports/
          retention-days: 7

  frontend:                                   # added in Week 7 with the dashboard
    runs-on: ubuntu-latest
    timeout-minutes: 15
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json
      - name: Install exactly what the lockfile says
        run: npm ci
      - name: Lint
        run: npm run lint
      - name: Unit tests (Vitest + React Testing Library)
        run: npm test -- --run
      - name: Type-check and build
        run: npm run build
```

Notes:
- Week 4 starts with only the `backend` job; M1 has no Testcontainers yet, so `mvn verify` runs unit + slice tests. From M2 (Week 5) the N-threads-one-unit reservation test runs against a real Postgres via Testcontainers — no `services:` block needed on `ubuntu-latest`.
- `defaults.run.working-directory` applies to `run:` steps only — `uses:` steps need explicit paths (hence `cache-dependency-path`).
- `npm ci` fails if `package.json` and `package-lock.json` disagree — that's a feature. Commit the lockfile.
- `vitest` without `--run` watches locally; in CI it detects `CI=true` and runs once anyway, but explicit is clearer.
- Require **both** status checks (`backend`, `frontend`) on `main` via branch protection or a ruleset. Add a badge:
  `![CI](https://github.com/<owner>/flowgrid/actions/workflows/ci.yml/badge.svg)`
- Maven Wrapper? Make it executable in Git: `git update-index --chmod=+x mvnw`.
- A **flaky concurrency test** in CI is a bug report, not bad luck: see [`14-cs-fundamentals/concurrency.md` §9](../14-cs-fundamentals/concurrency.md#9-testing-concurrent-code).

---

## 2. ForgeCI — multi-module test, then build & push api + worker images

Assumed layout (one repo, one parent POM):

```
pom.xml                      <modules>common, api, worker</modules>
common/                      shared DTOs, job/step model, queue key names
api/     Dockerfile          webhooks, REST, SSE
worker/  Dockerfile          queue consumer, Docker Engine API client
ui/      Dockerfile          React build → nginx
```

Module-aware Dockerfile build stage (the only interesting line for multi-module is `-pl … -am`):

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -B -ntp -pl worker -am package -DskipTests   # build worker + the modules it depends on

FROM eclipse-temurin:21-jre
COPY --from=build /src/worker/target/worker-*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

(Copy the POMs first and run `mvn dependency:go-offline` in a separate layer if build time matters — [`11-docker/dockerfiles.md`](../11-docker/dockerfiles.md).)

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

env:
  REGISTRY: ghcr.io

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: All modules — unit, integration (Testcontainers Postgres + Redis), worker Docker tests
        run: mvn -B -ntp verify
      - if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: '**/target/*-reports/'
          retention-days: 7

  ui:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    defaults:
      run:
        working-directory: ui
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: ui/package-lock.json
      - run: npm ci
      - run: npm run lint
      - run: npm test -- --run
      - run: npm run build

  images:
    needs: [test, ui]
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    timeout-minutes: 25
    permissions:
      contents: read
      packages: write
    strategy:
      fail-fast: true
      matrix:
        include:
          - component: api
            context: .
            dockerfile: api/Dockerfile
          - component: worker
            context: .
            dockerfile: worker/Dockerfile
          - component: ui
            context: ui
            dockerfile: ui/Dockerfile
    steps:
      - uses: actions/checkout@v4

      - name: Lowercase owner (GHCR requires lowercase names)
        run: echo "OWNER=${GITHUB_REPOSITORY_OWNER,,}" >> "$GITHUB_ENV"

      - uses: docker/setup-buildx-action@v3

      - uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - uses: docker/build-push-action@v6
        with:
          context: ${{ matrix.context }}
          file: ${{ matrix.dockerfile }}
          platforms: linux/amd64
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.OWNER }}/forgeci-${{ matrix.component }}:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.OWNER }}/forgeci-${{ matrix.component }}:main
          labels: |
            org.opencontainers.image.source=${{ github.server_url }}/${{ github.repository }}
            org.opencontainers.image.revision=${{ github.sha }}
          cache-from: type=gha,scope=${{ matrix.component }}
          cache-to: type=gha,mode=max,scope=${{ matrix.component }}
```

Why this shape:
- **Test once at the root** (`mvn verify` builds modules in dependency order), then build images — never publish an image from an untested commit.
- **Same SHA tag for every component** → a deploy is "run version `abc123` of everything"; mixing api `abc123` with worker `def456` is how queue-message format mismatches happen.
- **Matrix = parallel image builds**, each with its own BuildKit cache scope.
- Worker tests that start real containers work on `ubuntu-latest` because the runner has a Docker daemon. They'd fail on a runner without one — note it in `TESTING.md`.
- Path filters (`paths: ['worker/**', 'common/**']`) can skip unaffected images later; start without them — correctness first.
- FlowGrid's image job (Week 8) is the same matrix with two entries: `api` (context `.`) and `ui` (context `frontend`).

---

## 3. Deploy job — FlowGrid to EC2 with health gate and rollback

### 3a. Server-side `deploy.sh` (health gate + automatic rollback)

Lives in the repo (`deploy/deploy.sh`), copied once to `/opt/flowgrid/` on the instance (see
[`12-aws/deploy-walkthrough.md`](../12-aws/deploy-walkthrough.md)). Deploying a different tag = editing `IMAGE_TAG` in `.env`.

```bash
#!/usr/bin/env bash
# Usage: deploy.sh <image-tag>     Runs on the EC2 instance as ec2-user.
set -euo pipefail

NEW_TAG="$1"
cd /opt/flowgrid
COMPOSE="docker compose -f compose.prod.yml --env-file .env"
PREV_TAG=$(grep '^IMAGE_TAG=' .env | cut -d= -f2)

set_tag() { sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$1/" .env; }

healthy() {
  for _ in $(seq 1 30); do                       # up to ~150 s (Spring + Flyway startup)
    if curl -fsS http://localhost/actuator/health | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep 5
  done
  return 1
}

echo "Deploying $NEW_TAG (previous: $PREV_TAG)"
set_tag "$NEW_TAG"
$COMPOSE pull api ui
$COMPOSE up -d --remove-orphans

if healthy; then
  docker image prune -f >/dev/null
  echo "Deploy OK: $NEW_TAG"
  exit 0
fi

echo "Health check failed — rolling back to $PREV_TAG" >&2
$COMPOSE logs --tail=100 api >&2 || true
set_tag "$PREV_TAG"
$COMPOSE up -d --remove-orphans
healthy && echo "Rollback OK" >&2 || echo "ROLLBACK ALSO UNHEALTHY — manual action needed" >&2
exit 1
```

It assumes nginx (the `ui` container) proxies `/actuator/health` to the API — or change the URL to
`http://localhost:8080/...` if you publish the API port on the host's loopback only.

One-time on the instance: `docker login ghcr.io` with a `read:packages` token (or make packages
public), `IMAGE_TAG=` line in `.env`, `chmod +x deploy.sh`.

Rollback caveat: this restores **code**, not the **database**. If the new version ran a Flyway
migration, the old version must still work on the new schema → expand/contract migrations
([README §7](./README.md#7-rollbacks)).

### 3b. `.github/workflows/deploy.yml` (runs after CI succeeds on `main`)

```yaml
name: Deploy

on:
  workflow_run:
    workflows: [CI]
    types: [completed]
    branches: [main]

permissions:
  contents: read
  id-token: write              # OIDC → AWS

jobs:
  deploy:
    if: github.event.workflow_run.conclusion == 'success'
    runs-on: ubuntu-latest
    timeout-minutes: 15
    environment:
      name: production
      url: http://${{ vars.PUBLIC_HOST }}
    concurrency:
      group: deploy-production
      cancel-in-progress: false
    env:
      TAG: ${{ github.event.workflow_run.head_sha }}   # the commit CI tested and the images were tagged with
      INSTANCE_ID: ${{ vars.EC2_INSTANCE_ID }}
    steps:
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_DEPLOY_ROLE_ARN }}
          aws-region: eu-west-1

      - name: Run deploy.sh on the instance via SSM (no SSH port needed)
        run: |
          CMD_ID=$(aws ssm send-command \
            --instance-ids "$INSTANCE_ID" \
            --document-name AWS-RunShellScript \
            --comment "deploy $TAG" \
            --parameters "commands=[\"sudo -iu ec2-user /opt/flowgrid/deploy.sh $TAG\"]" \
            --query Command.CommandId --output text)
          echo "SSM command: $CMD_ID"

          STATUS=Pending
          for _ in $(seq 1 60); do
            sleep 5
            STATUS=$(aws ssm get-command-invocation --command-id "$CMD_ID" \
              --instance-id "$INSTANCE_ID" --query Status --output text 2>/dev/null || echo Pending)
            case "$STATUS" in
              Pending|InProgress|Delayed) continue ;;
              *) break ;;
            esac
          done

          aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
            --query '[StandardOutputContent, StandardErrorContent]' --output text
          echo "Final status: $STATUS"
          [ "$STATUS" = "Success" ]

      - name: External smoke test
        run: |
          curl -fsS --retry 10 --retry-delay 5 --retry-all-errors \
            "http://${{ vars.PUBLIC_HOST }}/actuator/health"

      - name: Summary
        if: always()
        run: echo "Deployed \`$TAG\` → ${{ vars.PUBLIC_HOST }} (${{ job.status }})" >> "$GITHUB_STEP_SUMMARY"
```

Why `workflow_run`: the deploy workflow starts only after the `CI` workflow (which pushed the images)
finished successfully on `main`, and it deploys exactly the SHA CI tested. (Alternative: put the
`deploy` job at the end of `ci.yml` with `needs: images` and `TAG: ${{ github.sha }}` — simpler, one file.
Either is fine; be able to explain your choice.) `workflow_run` only fires for workflow files on the
default branch.

Setup:

| Where | Name | Value |
|---|---|---|
| Environment `production` → protection | Required reviewers | You (continuous delivery); remove for continuous deployment |
| Environment `production` → secrets | `AWS_DEPLOY_ROLE_ARN` | Role from [github-actions.md §10](./github-actions.md#oidc-to-aws) |
| Environment `production` → variables | `EC2_INSTANCE_ID`, `PUBLIC_HOST` | `i-0abc…`, public IP/DNS |
| Deploy role policy | `ssm:SendCommand` | On the instance ARN **and** `arn:aws:ssm:eu-west-1::document/AWS-RunShellScript` |
| Deploy role policy | `ssm:GetCommandInvocation` | `*` (no resource-level support) |
| EC2 instance role | `AmazonSSMManagedInstanceCore` | So the SSM agent can receive commands ([iam.md](../12-aws/iam.md#43-create-it-with-the-cli)) |

### 3c. SSH alternative to the SSM step

Simpler to understand, but port 22 must be reachable from GitHub-hosted runners, whose IP ranges are
large and change — in practice that means opening 22 widely (key-only auth, but a bigger attack
surface). SSM + OIDC avoids both the open port and the stored key — a good interview trade-off answer.

```yaml
      - name: Deploy over SSH
        env:
          SSH_KEY: ${{ secrets.EC2_SSH_KEY }}           # private key of a dedicated deploy key pair
          KNOWN_HOSTS: ${{ secrets.EC2_KNOWN_HOSTS }}   # from: ssh-keyscan -t ed25519 <host> (verify the fingerprint!)
          HOST: ${{ vars.PUBLIC_HOST }}
          TAG: ${{ github.event.workflow_run.head_sha }}
        run: |
          install -m 700 -d ~/.ssh
          printf '%s\n' "$SSH_KEY" > ~/.ssh/deploy_key && chmod 600 ~/.ssh/deploy_key
          printf '%s\n' "$KNOWN_HOSTS" > ~/.ssh/known_hosts
          ssh -i ~/.ssh/deploy_key -o BatchMode=yes ec2-user@"$HOST" "/opt/flowgrid/deploy.sh $TAG"
```

Never use `StrictHostKeyChecking=no` in a deploy — it disables protection against a spoofed host.

### 3d. Manual rollback workflow — `.github/workflows/rollback.yml`

```yaml
name: Rollback / redeploy a tag

on:
  workflow_dispatch:
    inputs:
      tag:
        description: 'Image tag (full git SHA) to deploy'
        required: true
        type: string

permissions:
  contents: read
  id-token: write

jobs:
  redeploy:
    runs-on: ubuntu-latest
    environment: production
    concurrency:
      group: deploy-production
      cancel-in-progress: false
    env:
      TAG: ${{ inputs.tag }}
      INSTANCE_ID: ${{ vars.EC2_INSTANCE_ID }}
    steps:
      - name: Validate tag format
        run: '[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo "Tag must be a full git SHA"; exit 1; }'
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_DEPLOY_ROLE_ARN }}
          aws-region: eu-west-1
      - name: Redeploy
        run: |
          CMD_ID=$(aws ssm send-command --instance-ids "$INSTANCE_ID" \
            --document-name AWS-RunShellScript \
            --parameters "commands=[\"sudo -iu ec2-user /opt/flowgrid/deploy.sh $TAG\"]" \
            --query Command.CommandId --output text)
          sleep 10
          until S=$(aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "$INSTANCE_ID" \
                    --query Status --output text 2>/dev/null) && [[ "$S" != Pending && "$S" != InProgress && "$S" != Delayed ]]; do
            sleep 5
          done
          echo "Status: $S"; [ "$S" = Success ]
```

The tag validation matters: `inputs.tag` ends up inside a shell command on your server, so it must
not be able to contain `;` or `$(...)`.

### 3e. ForgeCI deploy ordering (Week 18–19)

Same building blocks, two targets:

1. **API instance first** (runs Flyway migrations; new schema must be backward compatible with the running workers).
2. **Workers second**, one at a time: send the worker a graceful-shutdown signal (SIGTERM → stop taking jobs, finish or release leases), wait, pull the new tag, start. In the workflow this is a second job `deploy-workers: needs: deploy-api` that loops over worker instance IDs (an SSM `--targets Key=tag:role,Values=forgeci-worker` call with `--max-concurrency 1` does the same in one command).
3. **Verify end to end**: push a commit to a test repo → webhook → job runs on the new worker → logs stream in the UI. That's your post-deploy smoke test.

- [ ] Document in `DEPLOYMENT.md` what happens to a build that is running when its worker is redeployed (it should finish, or be re-queued via lease expiry — never silently lost).

---

## 4. Common failures and how to debug them

| Symptom in the log | Likely cause | Fix |
|---|---|---|
| `./mvnw: Permission denied` | Wrapper not executable in Git | `git update-index --chmod=+x mvnw` |
| `release version 21 not supported` | Wrong JDK on runner | `setup-java` with `java-version: '21'` before Maven |
| Tests pass locally, fail in CI with `Connection refused localhost:5432` | Test expects a locally running Postgres | Testcontainers (or a `services:` container) |
| `Could not find a valid Docker environment` (Testcontainers / ForgeCI worker tests) | Runner without Docker (macOS runner, or job inside a `container:`) | Use `ubuntu-latest` |
| Multi-module: `Could not resolve dependencies ... forgeci-common` | Building one module without its siblings | `mvn -pl worker -am ...` or build from the root |
| `npm ci` → `package-lock.json ... not in sync` | Lockfile not updated/committed | `npm install` locally, commit the lockfile |
| `Cannot find module` only in CI | Case-sensitive filesystem (`./Button` vs `button.tsx`) | Fix the import casing |
| `denied: permission_denied: write_package` | Missing `packages: write`, or package not linked to the repo | Add the permission; package settings → manage Actions access |
| `invalid reference format: repository name must be lowercase` | Owner has uppercase letters | `${GITHUB_REPOSITORY_OWNER,,}` |
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | Trust policy `sub` doesn't match (branch vs environment), or missing `id-token: write` | Match `repo:<owner>/<repo>:environment:production` |
| Deploy workflow never starts | `workflow_run` file not on the default branch, or `workflows:` name ≠ the CI workflow's `name:` | Check both |
| SSM status `Failed`, stdout shows `docker: permission denied` | Ran as root/ssm-user without the docker group | `sudo -iu ec2-user` |
| SSM command stuck `Pending` | Instance's SSM agent offline / missing role policy | Fleet Manager; attach `AmazonSSMManagedInstanceCore` |
| Deploy "succeeds" but the old version is served | Tag unchanged / `pull` skipped / browser cached `index.html` | `docker compose ps` image digests; `.env`; `Cache-Control: no-cache` on `index.html` |
| Health check times out, rolls back | Missing env var, DB security group, Flyway failure | `docker compose logs api` / CloudWatch `/flowgrid/api` |
| `exec format error` on EC2 | Image built for arm64, instance is x86_64 (or vice versa) | `platforms: linux/amd64` |
| Flaky concurrency test | Real race in code or test (shared state, missing latch, timing assumptions) | Reproduce with `@RepeatedTest`; never "retry until green" |

Debugging order: **read the first error, not the last** (later errors cascade) → reproduce the exact
command locally in a clean container (`docker run --rm -it -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-21 mvn -B verify`)
→ enable debug logging → add a temporary diagnostic step (`java -version`, `ls -la`, `env | sort` — never print secrets).

---

## 5. Break → Debug drills

- [ ] Commit a failing unit test on a branch → PR blocked → fix (Week 4)
- [ ] Remove `packages: write` → image push fails on `main` → restore (Week 8)
- [ ] Put a wrong `DB_PASSWORD` in the server `.env` → deploy rolls back automatically → read the logs → fix (Week 8)
- [ ] Change the OIDC trust `sub` to another environment name → `AssumeRoleWithWebIdentity` denied → fix (Week 8)
- [ ] Run `rollback.yml` with the previous SHA and confirm via `docker compose ps` (Week 8)
- [ ] ForgeCI: change a queue message field in `common` without updating the worker → integration test catches the mismatch before images are built (Week 18)
