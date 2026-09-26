# GitHub Actions — Syntax and Features

> **Practical use:** write and debug the workflows for FlowGrid (Weeks 4 and 8) and ForgeCI (Week 18) without copying
> YAML you don't understand.
> **Interview use:** "What's a runner?", "How do you cache dependencies?", "How do you deploy without storing AWS keys?"

← [CI/CD README](./README.md) · Next: [Pipeline examples](./pipeline-examples.md)

---

## 1. Vocabulary

| Term | Meaning |
|---|---|
| **Workflow** | A YAML file in `.github/workflows/`. A repo can have many |
| **Event / trigger** | What starts a workflow: `push`, `pull_request`, `workflow_dispatch`, `schedule`, `release`… |
| **Job** | A set of steps that runs on **one runner**. Jobs run **in parallel** unless linked with `needs` |
| **Step** | One shell command (`run:`) or one action (`uses:`), executed in order inside a job |
| **Action** | Reusable step: `owner/repo@ref`, e.g. `actions/checkout@v4` |
| **Runner** | The machine: GitHub-hosted (`ubuntu-latest`, `windows-latest`, `macos-latest`) or self-hosted. Fresh VM per job |
| **Context / expression** | `${{ github.sha }}`, `${{ secrets.X }}`, `${{ matrix.java }}`, `${{ needs.build.outputs.tag }}` |
| **Artifact** | Files uploaded from a job, downloadable by later jobs or humans |
| **Environment** | Named deploy target with protection rules and its own secrets |

---

## 2. Minimal workflow, annotated

```yaml
name: CI                                 # shown in the Actions tab

on:                                      # triggers
  push:
    branches: [main]
  pull_request:                          # PRs targeting any branch (restrict with branches:)

permissions:
  contents: read                         # least privilege for GITHUB_TOKEN

jobs:
  test:                                  # job id
    runs-on: ubuntu-latest
    timeout-minutes: 15                  # default is 360 — always set something sane
    steps:
      - uses: actions/checkout@v4        # clone the repo at the triggering commit
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven                   # caches ~/.m2/repository keyed on pom.xml hashes
      - name: Build and test
        run: mvn -B -ntp verify          # -B batch mode, -ntp no transfer progress spam
```

Notes:
- Quote versions (`'21'`, `'20'`) — unquoted `20.10` would be parsed by YAML as a number and could lose digits.
- Pin actions to a **major version** (`@v4`) at minimum; security-sensitive repos pin to a full commit SHA. Check each action's README for its current major.
- `ubuntu-latest` has Docker, Git, Maven, Node, `jq`, the AWS CLI and more preinstalled.

---

## 3. Triggers

```yaml
on:
  push:
    branches: [main]
    paths-ignore: ['**.md', 'docs/**']      # skip CI for docs-only changes
    tags: ['v*']                            # also run on version tags
  pull_request:
    branches: [main]
  workflow_dispatch:                         # "Run workflow" button
    inputs:
      image_tag:
        description: 'Image tag to deploy'
        required: true
  schedule:
    - cron: '17 3 * * 1'                     # UTC; Mondays 03:17 (e.g. dependency audit)
```

| Event | Typical use | Gotcha |
|---|---|---|
| `push` | CI on branches, deploy on `main` | `paths` filters + required checks can leave a PR "waiting" forever |
| `pull_request` | CI on PRs; runs on the **merge commit** of PR + base | Fork PRs get **no secrets** and a read-only token |
| `workflow_dispatch` | Manual deploys/rollbacks | Only available once the file is on the default branch |
| `schedule` | Nightly jobs | Can be delayed; disabled after 60 days of repo inactivity (public repos) |
| `workflow_run` | Chain workflows | Runs in the default branch context — be careful with untrusted code |

---

## 4. Jobs, dependencies, outputs, conditions

```yaml
jobs:
  backend:
    runs-on: ubuntu-latest
    outputs:
      version: ${{ steps.ver.outputs.version }}
    steps:
      - uses: actions/checkout@v4
      - id: ver
        run: echo "version=$(git rev-parse --short HEAD)" >> "$GITHUB_OUTPUT"

  frontend:
    runs-on: ubuntu-latest               # runs in parallel with backend
    steps:
      - run: echo "parallel"

  image:
    needs: [backend, frontend]           # waits for both; skipped if either fails
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - run: echo "Building ${{ needs.backend.outputs.version }}"
```

Useful status functions in `if:`: `success()` (default), `failure()`, `always()`, `cancelled()`.
Example: upload test reports only when tests fail — `if: failure()`.

Special files:
- `$GITHUB_OUTPUT` — step outputs (`name=value`)
- `$GITHUB_ENV` — env vars for later steps in the same job
- `$GITHUB_STEP_SUMMARY` — Markdown shown on the run page

`set-output` / `save-state` workflow commands are deprecated — use the files above.

---

## 5. Caching

| Ecosystem | Easiest | What's cached | Key |
|---|---|---|---|
| Maven | `actions/setup-java` with `cache: maven` | `~/.m2/repository` | hash of `**/pom.xml` |
| npm | `actions/setup-node` with `cache: npm` (+ `cache-dependency-path`) | npm's download cache (not `node_modules`) | hash of `package-lock.json` |
| Docker layers | `docker/build-push-action` with `cache-from/to: type=gha` | BuildKit layers | per build |
| Anything | `actions/cache@v4` with `path` + `key` + `restore-keys` | You choose | You choose |

Cache ≠ artifact: caches speed up *future runs*, may be evicted, and must never hold secrets or
outputs you rely on. Artifacts are outputs of *this* run.

---

## 6. Matrix builds

```yaml
jobs:
  test:
    strategy:
      fail-fast: false                   # let other combos finish when one fails
      matrix:
        java: ['21', '25']
        os: [ubuntu-latest, windows-latest]
        exclude:
          - os: windows-latest
            java: '25'
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '${{ matrix.java }}', cache: maven }
      - run: mvn -B -ntp verify
```

For application projects one JDK is enough; matrices matter for libraries. Knowing the syntax is enough.

---

## 7. Service containers (Postgres in CI)

When you **don't** use Testcontainers, run Postgres as a service next to the job:

```yaml
jobs:
  integration:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: ledger_test
          POSTGRES_USER: ledger
          POSTGRES_PASSWORD: ledger
        ports: ['5432:5432']
        options: >-
          --health-cmd "pg_isready -U ledger"
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
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: psql "postgresql://ledger:ledger@localhost:5432/ledger_test" -f schema.sql
      - run: mvn -B -ntp verify
```

- Job runs directly on the VM → reach the service at `localhost:<mapped port>`.
- If the job itself runs in a `container:`, use the service **name** (`postgres:5432`) instead.
- The health options make the job wait until Postgres accepts connections.
- **Testcontainers** also works on `ubuntu-latest` (Docker is available) and needs no `services:` block — that's the approach from FlowGrid M2 onward.

---

## 8. Environments and approvals

Settings → Environments → `production`:
- **Required reviewers** (you) → the job pauses until approved.
- **Deployment branches**: only `main`.
- **Environment secrets**: `EC2_HOST`, `EC2_SSH_KEY`, `AWS_DEPLOY_ROLE_ARN`… only jobs that declare this environment can read them.

```yaml
  deploy:
    needs: image
    runs-on: ubuntu-latest
    environment:
      name: production
      url: http://${{ vars.PUBLIC_HOST }}       # shown as a link on the run page
    steps:
      - run: echo "deploying"
```

`vars.X` = non-secret configuration variables (Settings → Variables); `secrets.X` = masked secrets.

---

## 9. Secrets and the `GITHUB_TOKEN`

```yaml
permissions:            # workflow-level default; override per job
  contents: read
  packages: write       # push to GHCR
  id-token: write       # request an OIDC token (for AWS)

steps:
  - run: ./deploy.sh
    env:
      SSH_KEY: ${{ secrets.EC2_SSH_KEY }}      # pass via env, not inline in the script text
```

- Repo secrets: Settings → Secrets and variables → Actions. Or `gh secret set EC2_HOST --env production`.
- Secrets are masked in logs, but **multi-line** values and transformed values may not be — never `echo` them.
- Referencing `${{ secrets.X }}` directly inside `run:` text works but passing through `env:` avoids
  shell-injection issues and is the documented best practice. The same applies to untrusted inputs like
  `${{ github.event.pull_request.title }}` — never interpolate those into `run:` directly.

---

## 10. Cloud credentials

### OIDC to AWS

Instead of storing `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` in GitHub, the workflow requests a
signed OIDC token from GitHub and exchanges it with AWS STS for **temporary credentials** of a role
whose trust policy only accepts *your repo* and *your environment*.

One-time AWS setup:

```bash
# Current AWS docs note the thumbprint is no longer used for GitHub's provider; the CLI may still require a value.
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

Trust policy for `flowgrid-github-deploy` role:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Federated": "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
      },
      "Action": "sts:AssumeRoleWithWebIdentity",
      "Condition": {
        "StringEquals": {
          "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
          "token.actions.githubusercontent.com:sub": "repo:<owner>/flowgrid:environment:production"
        }
      }
    }
  ]
}
```

Its permissions: only what deploy needs (e.g. `ssm:SendCommand` on the one instance +
`AWS-RunShellScript` document, `ssm:GetCommandInvocation`).

Workflow:

```yaml
permissions:
  id-token: write
  contents: read

jobs:
  deploy:
    environment: production
    runs-on: ubuntu-latest
    steps:
      - uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: ${{ secrets.AWS_DEPLOY_ROLE_ARN }}
          aws-region: eu-west-1
      - run: aws sts get-caller-identity
```

Why it matters: no long-lived key to leak or rotate; access limited to one repo + environment; each
run's credentials expire in about an hour. The `sub` condition is critical — without it, **any**
GitHub repo could assume your role.

---

## 11. Concurrency

```yaml
concurrency:
  group: deploy-production
  cancel-in-progress: false      # queue deploys; never kill one halfway

# For CI on PRs, cancel superseded runs to save minutes:
concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

(Choose one per workflow or job — a YAML key can't appear twice at the same level.)

---

## 12. Artifacts

```yaml
      - name: Upload test reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: |
            target/surefire-reports/
            target/failsafe-reports/
          retention-days: 7

  # in a later job:
      - uses: actions/download-artifact@v4
        with:
          name: frontend-dist
          path: frontend/dist
```

Artifact names must be unique within a run (v4 does not merge uploads with the same name).

---

## 13. Reusable pieces (awareness)

- **Composite actions** — bundle steps in `.github/actions/<name>/action.yml`.
- **Reusable workflows** — `on: workflow_call`, invoked with `uses: ./.github/workflows/build.yml`.
- **Dependabot** — `.github/dependabot.yml` opens PRs for Maven, npm, Docker and Actions updates.

---

## 14. Debugging workflows

| Technique | How |
|---|---|
| Read the failing step's log from the bottom up | Expand the red step; search "ERROR" / "FAILED" |
| Re-run with debug logging | "Re-run jobs" → **Enable debug logging** (or secret `ACTIONS_STEP_DEBUG=true`) |
| Print context | `run: echo '${{ toJSON(github.event) }}'` (careful with secrets — `secrets` context is never printed by this) |
| Reproduce locally | Same commands in a clean container: `docker run --rm -it -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-21 mvn -B verify` |
| Validate YAML | `actionlint` (linter for workflows) catches typos in keys and expressions |
| Download reports | Upload surefire / Vitest / Playwright reports as artifacts on failure |
| Look at the "Set up job" step | Shows runner image version and token permissions |

---

## 15. Interview questions

<details><summary>What is a runner?</summary>

The machine that executes a job. GitHub-hosted runners are fresh VMs per job (clean state, preinstalled
tools); self-hosted runners are your own machines (persist state, need patching, risky for public repos).
</details>

<details><summary>How do jobs share files?</summary>

They don't share a filesystem. Use artifacts (upload/download) for files, a registry for images,
or `outputs` for small strings.
</details>

<details><summary>How do you speed up a slow pipeline?</summary>

Cache dependencies (Maven/npm/Docker layers), run independent jobs in parallel, fail fast with
cheap checks first, skip irrelevant paths, cancel superseded PR runs, split slow test suites.
</details>

<details><summary>How does your pipeline authenticate to AWS?</summary>

OIDC: the job gets a GitHub-signed token, AWS STS validates it against a trust policy restricted to
my repo's `production` environment, and returns temporary credentials for a narrowly scoped role.
No stored AWS keys.
</details>

<details><summary>Why is `pull_request_target` dangerous?</summary>

It runs with the base repo's secrets and write token. If it checks out and runs the PR's (untrusted)
code, a malicious fork can exfiltrate secrets. Use plain `pull_request` for running PR code.
</details>
