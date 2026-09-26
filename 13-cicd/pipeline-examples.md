# Pipeline Examples — P1/P2, P3, P4

> Complete workflows you can drop into `.github/workflows/`. Read them line by line; every line is
> explained in [github-actions.md](./github-actions.md). Adjust paths to your actual repo layout.

← [GitHub Actions](./github-actions.md) · [CI/CD README](./README.md)

| # | Project | Week | File |
|---|---|---|---|
| 1a | P1 Ledger | 8 (optional) | `.github/workflows/ci.yml` — `mvn verify` with a Postgres service container |
| 1b | P2 TicketHold | 12 | `.github/workflows/ci.yml` — `mvn verify` with Testcontainers |
| 2 | P3 TeamBoard | 17–18 | `.github/workflows/ci.yml` — backend + frontend jobs |
| 3 | P4 FlowGrid | 22 | `.github/workflows/cicd.yml` — test → image → push → deploy → verify, plus `rollback.yml` |

Action versions below are the current majors at the time of writing; check each action's README
(and let Dependabot bump them).

---

## 1. P1 / P2 — `mvn verify`

### 1a. P1 Ledger (JDBC integration test against a Postgres service)

P1's integration test reads `DB_URL`, `DB_USER`, `DB_PASSWORD` from the environment (M4).

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
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: ledger_test
          POSTGRES_USER: ledger
          POSTGRES_PASSWORD: ledger
        ports: ['5432:5432']
        options: >-
          --health-cmd "pg_isready -U ledger -d ledger_test"
          --health-interval 5s
          --health-timeout 5s
          --health-retries 10
    env:
      DB_URL: jdbc:postgresql://localhost:5432/ledger_test
      DB_USER: ledger
      DB_PASSWORD: ledger
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Apply schema
        run: psql "postgresql://ledger:ledger@localhost:5432/ledger_test" -v ON_ERROR_STOP=1 -f schema.sql

      - name: Build and test
        run: mvn -B -ntp verify

      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: target/surefire-reports/
          retention-days: 7
```

### 1b. P2 TicketHold (Testcontainers — no `services:` needed)

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
  verify:
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
          name: test-reports
          path: |
            target/surefire-reports/
            target/failsafe-reports/
          retention-days: 7

      - name: Build Docker image (smoke — not pushed)
        run: docker build -t tickethold:${{ github.sha }} .
```

Then: Settings → Branches → add a rule (or ruleset) for `main`: require PR + require status check
`verify`. Add the badge to your README:

```markdown
![CI](https://github.com/<owner>/tickethold/actions/workflows/ci.yml/badge.svg)
```

If you use the Maven Wrapper (`./mvnw`), make sure it's executable in Git:
`git update-index --chmod=+x mvnw` — otherwise CI fails with `Permission denied`.

---

## 2. P3 TeamBoard — backend + frontend jobs

Assumed layout: `backend/` (Maven, Spring Boot) and `frontend/` (Vite + React + TS) with these
`package.json` scripts:

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
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
          cache-dependency-path: backend/pom.xml
      - run: mvn -B -ntp verify
      - if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: backend-test-reports
          path: backend/target/surefire-reports/

  frontend:
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
      - name: Install (exactly what the lockfile says)
        run: npm ci
      - name: Lint
        run: npm run lint
      - name: Unit tests (Vitest + React Testing Library)
        run: npm test -- --run
      - name: Type-check and build
        run: npm run build
      - uses: actions/upload-artifact@v4
        with:
          name: frontend-dist
          path: frontend/dist/
          retention-days: 3
```

Notes:
- `defaults.run.working-directory` applies to `run:` steps only — `uses:` steps need explicit paths
  (hence `cache-dependency-path` and the artifact `path`).
- `npm ci` fails if `package.json` and `package-lock.json` disagree — that's a feature. Commit the lockfile.
- `vitest` without `--run` starts watch mode locally; in CI it detects `CI=true` and runs once anyway, but being explicit is clearer.
- Both jobs run in parallel; require **both** status checks on `main`.
- Optional third job: `needs: [backend, frontend]` → `docker compose build` to prove the full stack builds.

---

## 3. P4 FlowGrid — build → test → image → deploy

Assumed layout:

```
pom.xml                  (parent: modules common, api, worker)
api/Dockerfile           (multi-stage; build context = repo root)
worker/Dockerfile
frontend/                (status dashboard) + frontend/Dockerfile (node build → nginx with dist + nginx.conf)
deploy/compose.prod.yml
deploy/deploy.sh         (copied to /opt/flowgrid on the instance once)
```

The deploy walkthrough mounted `frontend-dist` into nginx; in the pipeline the frontend becomes its
own image `flowgrid-web`, so the `nginx` service in `compose.prod.yml` becomes
`image: ghcr.io/<owner>/flowgrid-web:${IMAGE_TAG}`.

### 3a. Server-side `deploy/deploy.sh` (health gate + automatic rollback)

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
$COMPOSE pull api worker nginx
$COMPOSE up -d --remove-orphans

if healthy; then
  echo "$PREV_TAG" > .previous_tag
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

One-time on the instance: `docker login ghcr.io` with a `read:packages` token (or make packages
public), put `IMAGE_TAG=` in `.env`, `chmod +x deploy.sh`.

Rollback caveat: this restores **code**, not the **database**. If the new version ran a Flyway
migration, the old version must still work on the new schema → expand/contract migrations
([README §7](./README.md#7-rollbacks)).

### 3b. `.github/workflows/cicd.yml`

```yaml
name: FlowGrid CI/CD

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

permissions:
  contents: read

env:
  REGISTRY: ghcr.io

jobs:
  backend-test:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Unit + integration tests (Testcontainers Postgres + Redis)
        run: mvn -B -ntp verify
      - if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: backend-test-reports
          path: '**/target/surefire-reports/'

  frontend-test:
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
      - run: npm ci
      - run: npm run lint
      - run: npm test -- --run
      - run: npm run build

  images:
    needs: [backend-test, frontend-test]
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    timeout-minutes: 20
    permissions:
      contents: read
      packages: write
    strategy:
      matrix:
        include:
          - component: api
            dockerfile: api/Dockerfile
            context: .
          - component: worker
            dockerfile: worker/Dockerfile
            context: .
          - component: web
            dockerfile: frontend/Dockerfile
            context: frontend
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
            ${{ env.REGISTRY }}/${{ env.OWNER }}/flowgrid-${{ matrix.component }}:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.OWNER }}/flowgrid-${{ matrix.component }}:main
          labels: |
            org.opencontainers.image.source=${{ github.server_url }}/${{ github.repository }}
            org.opencontainers.image.revision=${{ github.sha }}
          cache-from: type=gha,scope=${{ matrix.component }}
          cache-to: type=gha,mode=max,scope=${{ matrix.component }}

  deploy:
    needs: images
    runs-on: ubuntu-latest
    timeout-minutes: 15
    environment:
      name: production
      url: http://${{ vars.PUBLIC_HOST }}
    concurrency:
      group: deploy-production
      cancel-in-progress: false
    permissions:
      contents: read
      id-token: write            # OIDC → AWS
    env:
      TAG: ${{ github.sha }}
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

Setup for this workflow:

| Where | Name | Value |
|---|---|---|
| Environment `production` → protection | Required reviewers | You |
| Environment `production` → secrets | `AWS_DEPLOY_ROLE_ARN` | Role from [github-actions.md §10](./github-actions.md#oidc-to-aws) |
| Environment `production` → variables | `EC2_INSTANCE_ID`, `PUBLIC_HOST` | `i-0abc…`, public IP/DNS |
| AWS deploy role policy | `ssm:SendCommand` | on the instance ARN **and** `arn:aws:ssm:eu-west-1::document/AWS-RunShellScript` |
| AWS deploy role policy | `ssm:GetCommandInvocation` | `*` (no resource-level support) |
| EC2 instance role | `AmazonSSMManagedInstanceCore` | So the SSM agent can receive commands ([iam.md](../12-aws/iam.md#43-create-it-with-the-cli)) |

### 3c. SSH alternative to the SSM step

Simpler to understand, but port 22 must be reachable from GitHub-hosted runners, whose IP ranges
are large and change — in practice that means opening 22 widely (key-only auth, but still a larger
attack surface). SSM + OIDC avoids both the open port and the stored key; that trade-off is a good
interview answer.

```yaml
      - name: Deploy over SSH
        env:
          SSH_KEY: ${{ secrets.EC2_SSH_KEY }}           # private key of a dedicated deploy key pair
          KNOWN_HOSTS: ${{ secrets.EC2_KNOWN_HOSTS }}   # from: ssh-keyscan -t ed25519 <host> (verify fingerprint!)
          HOST: ${{ vars.PUBLIC_HOST }}
          TAG: ${{ github.sha }}
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
        description: 'Image tag (git SHA) to deploy'
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

The tag validation matters: `inputs.tag` ends up inside a shell command on your server, so it
must not be able to contain `;` or `$(...)`.

---

## 4. Common failures and how to debug them

| Symptom in the log | Likely cause | Fix |
|---|---|---|
| `./mvnw: Permission denied` | Wrapper not executable in Git | `git update-index --chmod=+x mvnw` |
| `release version 21 not supported` | Wrong JDK on runner | `setup-java` with `java-version: '21'` before Maven |
| Tests pass locally, fail in CI with `Connection refused localhost:5432` | Test expects a local Postgres | Service container or Testcontainers |
| `Could not find a valid Docker environment` (Testcontainers) | Runner without Docker (e.g. macOS runner, or job in a container) | Use `ubuntu-latest` |
| `npm ci` → `package-lock.json ... not in sync` | Lockfile not updated/committed | `npm install` locally, commit lockfile |
| `Cannot find module` only in CI | Case-sensitive filesystem (`./Button` vs `button.tsx`) | Fix the import casing |
| Some cache "Cache not found" | First run or key changed | Normal; second run is faster |
| `denied: permission_denied: write_package` | Missing `packages: write` or package not linked to repo | Add permission; package settings → manage Actions access |
| `invalid reference format: repository name must be lowercase` | Owner has uppercase letters | `${GITHUB_REPOSITORY_OWNER,,}` |
| `Not authorized to perform sts:AssumeRoleWithWebIdentity` | Trust policy `sub` doesn't match (branch vs environment), missing `id-token: write` | Print the claim format; match `repo:owner/repo:environment:production` |
| `Credentials could not be loaded` | Forgot `id-token: write` at job level when `permissions` is set per job | Add it |
| SSM status `Failed`, stdout shows `docker: permission denied` | Ran as root/ssm-user without group or login | `sudo -iu ec2-user` |
| SSM command stuck `Pending` | Instance's SSM agent offline / missing role policy | Check Fleet Manager; attach `AmazonSSMManagedInstanceCore` |
| Deploy "succeeds" but site serves old version | Tag not changed / `pull` skipped / browser cache | Check `docker compose ps` image digests; `.env` IMAGE_TAG |
| Health check times out, rolls back | Missing env var, DB SG, Flyway failure | `docker compose logs api` / CloudWatch `/flowgrid/api` |
| `exec format error` on EC2 | Image built for arm64, instance is x86_64 (or vice versa) | `platforms: linux/amd64` |
| Workflow doesn't trigger | YAML in wrong folder, branch filter, or `workflow_dispatch` not on default branch | `.github/workflows/`, check `on:` |
| Deploy job skipped | `if:` false on PRs (intended), or a `needs` job failed/skipped | Check the graph view |

Debugging order: **read the first error, not the last** (later errors cascade) → reproduce the exact
command locally in a clean container → enable debug logging → add a temporary diagnostic step
(`java -version`, `ls -la`, `env | sort` — never print secrets).

---

## 5. Break → Debug drills (Week 22)

- [ ] Commit a failing unit test on a branch → PR blocked → fix
- [ ] Remove `packages: write` → push to `main` fails at image push → restore
- [ ] Set a wrong `SPRING_DATASOURCE_URL` on the server → deploy rolls back automatically → read logs → fix
- [ ] Change the OIDC trust `sub` to another environment name → `AssumeRoleWithWebIdentity` denied → fix
- [ ] Run `rollback.yml` with the previous SHA and confirm via `docker compose ps`
