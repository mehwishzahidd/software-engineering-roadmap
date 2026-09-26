# CI/CD — Résumé Tech Defense

> **Goal:** defend "CI/CD" with truthful past context and current competence in GitHub Actions:
> workflows, jobs, caching, services, secrets, environments, OIDC, and a real build → test →
> image → deploy pipeline (TicketHold CI in W12, PulseWatch CD in W22).
>
> **Honesty rule:** many engineers "used" CI/CD (pushed code, watched Jenkins go green) without
> writing pipelines. If that's your past, say it. Your pipeline-authoring claim rests on the
> workflows in TicketHold and PulseWatch.

---

## Evidence in my projects

| Project | CI/CD evidence |
|---|---|
| **P2 TicketHold** M4 (W12) | `.github/workflows/ci.yml`: on push/PR → `actions/setup-java` (Temurin 21, Maven cache) → `mvn -B verify` (unit + `@WebMvcTest` + `@DataJpaTest` + Testcontainers); branch protection requires it |
| **P3 TeamBoard** (W17–18) | Two jobs: backend `mvn verify`, frontend `npm ci && npm run lint && tsc --noEmit && vitest run && npm run build` |
| **P4 PulseWatch** M4 (W22) | Pipeline: test → build image → push (tag = git SHA) → deploy job (`environment: production`, manual approval) assuming AWS role via **OIDC** → SSM command on EC2: `docker compose pull && up -d` → smoke test `/actuator/health`; rollback = redeploy previous SHA |

## Where to learn it in this repo

- [`../13-cicd/README.md`](../13-cicd/README.md)
- [`../13-cicd/github-actions.md`](../13-cicd/github-actions.md)
- [`../13-cicd/pipeline-examples.md`](../13-cicd/pipeline-examples.md)
- [`../12-aws/deploy-walkthrough.md`](../12-aws/deploy-walkthrough.md)
- Related: [`docker.md`](./docker.md), [`aws.md`](./aws.md)

---

## 1. Beginner questions

<details><summary><b>B1. What is CI/CD?</b></summary>

**CI**: every change is merged frequently and automatically built + tested, so integration problems surface within minutes. **Continuous Delivery**: every green build produces a deployable artefact and deploy is one (possibly manual) step. **Continuous Deployment**: green builds deploy to production automatically.
</details>

<details><summary><b>B2. GitHub Actions building blocks?</b></summary>

Workflow (YAML in `.github/workflows/`) → triggered by events (`push`, `pull_request`, `workflow_dispatch`, `schedule`) → jobs (run on runners, parallel by default, `needs:` for order) → steps (`uses:` an action or `run:` a shell command).
</details>

<details><summary><b>B3. Why run tests on pull requests?</b></summary>

Catch failures before merge; with branch protection "required status checks", `main` stays green. Reviewers review code that already builds.
</details>

<details><summary><b>B4. What is a build artefact?</b></summary>

The versioned output of a build: a jar, a Docker image, a static bundle. Build once, promote the same artefact through environments.
</details>

<details><summary><b>B5. Where do secrets go?</b></summary>

Repository/environment secrets (`${{ secrets.X }}`), masked in logs, not available to workflows from forks on `pull_request`. Better: OIDC to cloud roles so there's no long-lived secret at all.
</details>

## 2. Intermediate questions

<details><summary><b>I1. Show your TicketHold CI workflow.</b></summary>

```yaml
name: ci
on: { push: { branches: [main] }, pull_request: {} }
permissions: { contents: read }
jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: mvn -B -ntp verify
      - if: failure()
        uses: actions/upload-artifact@v4
        with: { name: surefire-reports, path: target/surefire-reports }
```
Testcontainers works because `ubuntu-latest` runners have Docker.
</details>

<details><summary><b>I2. How do you speed up pipelines?</b></summary>

Dependency caching (`setup-java cache: maven`, `setup-node cache: npm`), Docker layer cache (`cache-from: type=gha`), parallel jobs, run fast tests first, path filters, `concurrency:` to cancel superseded runs, avoid rebuilding the same artefact.
</details>

<details><summary><b>I3. Explain environments and approvals.</b></summary>

`environment: production` on a job enables environment-scoped secrets, required reviewers, and deployment history. PulseWatch's deploy job waits for manual approval.
</details>

<details><summary><b>I4. How does OIDC to AWS work in Actions?</b></summary>

Job requests an OIDC token (`permissions: id-token: write`); `aws-actions/configure-aws-credentials` exchanges it via STS `AssumeRoleWithWebIdentity`; the IAM role's trust policy checks `aud = sts.amazonaws.com` and `sub = repo:owner/repo:environment:production`. Credentials last ~1 h, nothing stored.
</details>

<details><summary><b>I5. How do you tag images and roll back?</b></summary>

Tag with `${{ github.sha }}` (immutable), optionally `vX.Y.Z` on release tags. Deploy sets `IMAGE_TAG=<sha>`. Rollback = redeploy the previous known-good SHA. Database migrations must be backward-compatible (expand → migrate → contract) or rollback breaks.
</details>

<details><summary><b>I6. Deployment strategies?</b></summary>

Recreate (downtime, PulseWatch single host — brief), rolling (replace instances gradually), blue/green (switch traffic between two stacks), canary (small % first). Choose by risk tolerance and infra cost.
</details>

<details><summary><b>I7. What makes a CI suite flaky and how do you fix it?</b></summary>

Time dependence, test order dependence, shared state, real network calls, race conditions, fixed sleeps. Fix root causes (injectable `Clock`, isolated data, Awaitility instead of `sleep`); quarantine only temporarily; never just "re-run until green".
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "What is CI/CD?" (and "have you set one up?")</b></summary>

Definition (B1) + concrete: "In PulseWatch, a PR runs `mvn verify` and the frontend tests; merge to main builds an image tagged with the SHA, pushes it, and after approval deploys to EC2 via an OIDC-assumed role, then a smoke test hits `/actuator/health`." Past: truthful about what you wrote vs used.
Follow-up: "How do you roll back?"
</details>

<details><summary><b>R2. "What runs in your pipeline and in what order? Why?"</b></summary>

Lint/compile → unit → slice/integration (Testcontainers) → build image → push → deploy → smoke test. Fast feedback first; build once; deploy only from `main`.
</details>

<details><summary><b>R3. "How do you handle database migrations in CD?"</b></summary>

Flyway runs on app startup (simple, one instance) or as a separate step before rollout. Migrations are additive/backward-compatible so old and new app versions coexist; destructive changes happen in a later release.
</details>

<details><summary><b>R4. "A deploy broke production. What does your pipeline give you?"</b></summary>

Which SHA is deployed (deployment history), fast rollback to previous SHA, smoke test that should have caught it — then add a test that would have caught it.
</details>

<details><summary><b>R5. "Jenkins vs GitHub Actions?"</b></summary>

Jenkins: self-hosted, plugin ecosystem, Groovy pipelines, you run it. Actions: hosted runners, YAML, integrated with PRs/marketplace. Concepts (stages, agents/runners, artefacts, secrets) transfer. Only claim Jenkins experience you actually had.
</details>

## 4. Practical tasks (doable live)

1. Write a workflow that runs `mvn verify` on PRs with Maven caching.
2. Add a matrix over Java 21 and 25 (or two OSes).
3. Add a job that builds and pushes a Docker image only on `main`.
4. Add `concurrency: { group: ${{ github.ref }}, cancel-in-progress: true }` and explain it.
5. Read a failed run's log and identify the failing test.

## 5. Debugging questions

<details><summary><b>D1. Tests pass locally, fail in CI.</b></summary>

Differences: timezone/locale, Java version, OS file paths/case sensitivity, missing env vars/secrets, test order, Docker availability, resource limits/timeouts. Reproduce with the same command (`mvn -B verify`), same JDK, clean `~/.m2`, or `act`/a container.
</details>

<details><summary><b>D2. Secret is empty in a PR from a fork.</b></summary>

By design, secrets aren't passed to fork PR workflows. Use `pull_request` without secrets for tests; don't switch to `pull_request_target` with checkout of untrusted code (security risk).
</details>

<details><summary><b>D3. Deploy "succeeded" but the old version is running.</b></summary>

Deployed `:latest` and host used a cached image; or compose didn't recreate. Use SHA tags, `docker compose pull`, verify with `/actuator/info` showing git SHA.
</details>

<details><summary><b>D4. Pipeline takes 15 minutes.</b></summary>

Check step timings: no dependency cache, Docker layers rebuilt, sequential jobs that could be parallel, integration tests starting a container per class (use reusable/singleton containers).
</details>

## 6. Architecture questions

<details><summary><b>A1. Design the pipeline for TeamBoard (monorepo, backend + frontend).</b></summary>

Path-filtered jobs (`backend/**`, `frontend/**`), both on PR; on main: build two images (api, web/nginx), push, deploy together with Compose; e2e smoke after deploy.
</details>

<details><summary><b>A2. How do you promote across dev → staging → prod?</b></summary>

Same image SHA deployed to each environment; config via environment-specific secrets/vars; approvals gate prod; tests at each gate.
</details>

## 7. Common mistakes

- Long-lived cloud keys in secrets; broad `GITHUB_TOKEN` permissions.
- Rebuilding artefacts per environment.
- Deploying `:latest`.
- No rollback plan / irreversible migrations.
- Ignoring flaky tests.
- Unpinned third-party actions (pin to a version or SHA).

## 8. Terminology I must know

| Term | Meaning |
|---|---|
| Workflow / job / step | Pipeline file / unit on a runner / command or action |
| Runner | Machine executing jobs (hosted or self-hosted) |
| Trigger | Event starting a workflow |
| Matrix | Run job over parameter combinations |
| Artefact | Build output saved/passed between jobs |
| Cache | Reused dependencies across runs |
| Environment | Deploy target with secrets/protection rules |
| OIDC | Keyless auth from CI to cloud |
| Branch protection | Rules requiring checks/reviews before merge |
| Smoke test | Minimal post-deploy health check |
| Rollback | Revert to previous version |
| Blue/green, canary | Low-risk release strategies |

## 9. When to use it

- Every repository with more than a day's life — CI from the first commit (TicketHold from W12).
- CD when deploys are frequent enough that manual steps cause errors.

## 10. When NOT to use it

- Fully automatic prod deploys without tests/monitoring/rollback — that's just automated breaking.
- Over-engineered multi-stage pipelines for a one-person prototype.

## 11. Trade-offs

| Choice | Gain | Cost |
|---|---|---|
| Continuous deployment | Fast delivery | Needs excellent tests + monitoring |
| Manual approval | Safety | Slower, human bottleneck |
| Hosted runners | Zero maintenance | Minutes cost, limited resources |
| Self-hosted runners | Power, network access | Security + upkeep |

## 12. How it interacts with the rest of my stack

- **Maven/JUnit/Testcontainers:** `mvn verify` runs Surefire (unit) + Failsafe (IT) phases.
- **Spring Boot:** Actuator health for smoke tests; `info` endpoint exposes build SHA.
- **Docker:** `docker/build-push-action` builds the multi-stage image.
- **AWS:** OIDC role → SSM/SSH deploy to EC2; RDS migrations via Flyway on startup.
- **React/TS:** lint + type-check + Vitest + build job.
- **Git/GitHub:** PRs, branch protection, required checks.

## 13. One small hands-on exercise

**CI for TicketHold + image publish.**

- [ ] PR workflow runs `mvn -B verify`, uploads test reports on failure.
- [ ] Branch protection requires it; a PR with a failing test cannot merge.
- [ ] On `main`, image built with layer cache and pushed to GHCR as `:sha`.
- [ ] `concurrency` cancels superseded runs.
- [ ] Total time < 6 min; you can explain every line.

## 14. Mastery checklist

- [ ] Define CI vs continuous delivery vs continuous deployment
- [ ] Write a Maven CI workflow from memory
- [ ] Explain OIDC deploy and trust policy conditions
- [ ] Explain rollback + migration compatibility
- [ ] Diagnose "passes locally, fails in CI"
- [ ] Truthful 60-second answer on past CI/CD involvement + PulseWatch bridge
