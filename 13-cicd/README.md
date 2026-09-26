# 🔁 13 — CI/CD (Weeks 4, 8 and 18)

> **Three passes.** In **[Week 4](../weeks/week-04/README.md)** FlowGrid M1 gets a GitHub Actions workflow
> that runs `mvn verify` on every push and PR. In **[Week 8](../weeks/week-08/README.md)** FlowGrid M5 gets the
> full pipeline: **test → build image → push to GHCR → deploy to EC2 → verify**. In **[Week 18](../weeks/week-18/README.md)**
> ForgeCI M5 adds **multi-module, multi-image** builds (api + worker). LedgerX and FlagForge reuse these
> workflows. (Irony worth mentioning in interviews: ForgeCI *is* a CI system — building it teaches you what
> GitHub Actions does under the hood: webhooks, queues, runners, containers, log streaming.)
> The concepts on this page apply to any CI system (GitHub Actions, GitLab CI, Jenkins, CircleCI).

| File | Contents | When |
|---|---|---|
| This README | Concepts: CI vs CD vs CD, stages, artifacts, environments, secrets, rollbacks, deployment strategies | W4 skim, W8 deep |
| [`github-actions.md`](./github-actions.md) | GitHub Actions syntax and features, OIDC, debugging | W4 §1–7, W8 all, W18 §6 + §13 |
| [`pipeline-examples.md`](./pipeline-examples.md) | Complete YAML: FlowGrid CI (backend + frontend), ForgeCI multi-module image build/push, deploy job with rollback + common failures | W4, W8, W18 |

---

## 1. Three terms interviewers mix up

| Term | Definition | Trigger | Human gate? |
|---|---|---|---|
| **Continuous Integration (CI)** | Every change is merged frequently to the main branch and **automatically built and tested** | Every push / PR | No — fails fast, blocks merge |
| **Continuous Delivery** | Every change that passes CI produces a **release candidate that *could* be deployed** at any time; deploy to production is a button | Merge to `main` | **Yes**, a manual approval before prod |
| **Continuous Deployment** | Every change that passes the pipeline is **deployed to production automatically** | Merge to `main` | No |

One-liner: *"CI keeps `main` always green. Continuous delivery keeps `main` always deployable.
Continuous deployment actually deploys it every time."* FlowGrid implements continuous delivery with an
approval-gated `production` environment (you can flip it to continuous deployment by removing the
reviewer requirement).

---

## 2. Anatomy of a pipeline

```
 push/PR
   │
   ▼
┌────────┐   ┌────────┐   ┌──────────┐   ┌─────────────┐   ┌──────────┐   ┌──────────┐
│ build  │──►│  test  │──►│ package  │──►│ publish     │──►│ deploy   │──►│ verify   │
│compile │   │unit +  │   │jar/image │   │artifact to  │   │staging → │   │health    │
│lint    │   │integr. │   │          │   │registry     │   │prod      │   │smoke test│
└────────┘   └────────┘   └──────────┘   └─────────────┘   └──────────┘   └──────────┘
   fast ◄──────────── fail fast: cheapest checks first ────────────► slow/expensive
```

| Stage | FlowGrid concrete step | Fails when |
|---|---|---|
| Build / static checks | `mvn -B verify` compiles; ESLint; `tsc --noEmit` | Compile error, lint error |
| Test | JUnit + Mockito + `@WebMvcTest` + Testcontainers; Vitest | Any test fails |
| Package | Multi-stage Docker build | Dockerfile error |
| Publish | Push `ghcr.io/<owner>/flowgrid-api:<git-sha>` | Auth/permissions |
| Deploy | Pull new tag on EC2, `docker compose up -d` | Instance unreachable, bad config |
| Verify | `curl /actuator/health` until UP, else roll back | App can't start (DB creds, migration) |

Principles:
- **Build once, deploy many.** The *same* image (same digest) goes to staging and prod; only config differs.
- **Immutable, traceable artifacts.** Tag images with the **git SHA**; `latest` alone is not traceable.
- **Fail fast.** Order stages cheapest → most expensive; run independent jobs in parallel.
- **Pipeline as code.** The YAML lives in the repo and is reviewed in PRs.
- **Reproducible.** Pin versions (JDK 21, Node 20, action major versions), use lockfiles (`npm ci`).

---

## 3. Artifacts

An **artifact** is a build output passed between jobs or kept after the run: a JAR, `dist/`
folder, test reports, a Docker image. In GitHub Actions, files move between jobs with
`actions/upload-artifact` / `actions/download-artifact` (jobs run on **different machines**, so the
filesystem isn't shared). Docker images go to a **registry** (GHCR, Docker Hub, ECR) instead.

---

## 4. Environments

| Environment | Purpose | FlowGrid |
|---|---|---|
| local | Development, `docker compose up` | Laptop |
| CI | Ephemeral, per run; Testcontainers Postgres | GitHub runner |
| staging | Production-like, for final checks | Optional (cost) |
| production | Real users | EC2 + RDS |

GitHub **environments** attach protection rules (required reviewers, wait timers, branch
restrictions) and **environment-scoped secrets** to a job. See [github-actions.md §8](./github-actions.md#8-environments-and-approvals).

---

## 5. Secrets

- Never in code, YAML, Dockerfiles, images or logs.
- Store in the CI secret store (GitHub: repository / environment / organization secrets); they are
  masked in logs (`***`) but a step can still leak them by transforming them (e.g. base64) — so be careful what you echo.
- **Prefer short-lived credentials**: OIDC federation to AWS instead of stored access keys.
- `GITHUB_TOKEN` is generated per run; scope it with `permissions:`.
- Secrets are **not** passed to workflows triggered by pull requests from forks (security feature).
- Runtime app secrets (DB password) live on the server / Parameter Store, not in the pipeline, when possible.

---

## 6. Deployment strategies (awareness level)

| Strategy | How | Downtime | Rollback | Cost |
|---|---|---|---|---|
| **Recreate** | Stop old, start new | Yes (seconds–minutes) | Redeploy old | Cheapest — **FlowGrid on one EC2** |
| **Rolling** | Replace instances a few at a time behind a LB | No | Roll forward/back gradually | Needs ≥ 2 instances |
| **Blue/green** | Run full new stack (green) beside old (blue); switch LB/DNS | No | Switch back instantly | 2× capacity during deploy |
| **Canary** | Send a small % of traffic to new version, watch metrics, ramp up | No | Route 100% back | Needs traffic-splitting + good metrics |
| **Feature flags** | Deploy code dark; enable at runtime | No | Toggle off | App complexity |

Interview framing: *"On a single EC2 I used recreate with a health-check gate and automatic
rollback to the previous image tag. With an ALB and two instances or ECS I'd use rolling; blue/green
if I needed instant rollback."*

---

## 7. Rollbacks

- **Keep the previous artifact addressable**: image tags by SHA → rollback = redeploy previous SHA.
- **Automate the decision**: health check after deploy; if not healthy within N seconds, redeploy previous.
- **Database migrations are the hard part.** Code rolls back; a dropped column doesn't. Use
  **expand/contract** (a.k.a. parallel change):
  1. *Expand:* add new column/table (backward compatible), deploy code writing both.
  2. Backfill.
  3. Deploy code reading the new one.
  4. *Contract:* drop the old column in a **later** release.
  Flyway migrations should always be backward compatible with the previous app version.
- **Roll forward** (a quick fix) is sometimes safer than rolling back — decide by blast radius.

---

## 8. Checklist

Week 4 (FlowGrid M1):
- [ ] `.github/workflows/ci.yml` runs `mvn -B verify` on push and PR
- [ ] Integration tests pass in CI (Testcontainers from M2 / Week 5 onward)
- [ ] Frontend job added when the dashboard starts (Week 7)
- [ ] Branch protection on `main`: PR required, CI must pass
- [ ] CI badge in README

Week 8 (FlowGrid M5) and Week 18 (ForgeCI M5):
- [ ] Separate jobs: backend test, frontend test, image build/push, deploy
- [ ] Images tagged with git SHA in GHCR
- [ ] `production` environment with required reviewer (you) and its own secrets
- [ ] Deploy step with health-check gate + automatic rollback
- [ ] `concurrency` prevents two deploys at once
- [ ] Either OIDC to AWS (SSM deploy) or SSH with a dedicated deploy key — and you can explain the trade-off
- [ ] ForgeCI: `api` and `worker` images built from one multi-module repo, both tagged with the same SHA, deployed API-first
- [ ] Deliberately broke the pipeline 3 ways and fixed it (see [pipeline-examples.md §4](./pipeline-examples.md#4-common-failures-and-how-to-debug-them))

---

## 9. Interview questions

<details><summary>CI vs continuous delivery vs continuous deployment?</summary>

CI: integrate and automatically build/test every change. Delivery: every green build is releasable,
prod deploy is a manual decision. Deployment: every green build is deployed automatically.
</details>

<details><summary>Walk me through your pipeline.</summary>

On PR: backend `mvn verify` with Testcontainers and frontend lint/typecheck/Vitest/build in
parallel; merge blocked unless green. On merge to main: build Docker images once, tag with the git
SHA, push to GHCR; a deploy job gated by the `production` environment approval pulls that SHA on EC2,
restarts via Compose, polls `/actuator/health`, and rolls back to the previous SHA if it doesn't
come up. A concurrency group ensures one deploy at a time.
</details>

<details><summary>How do you handle secrets in CI?</summary>

Encrypted CI secrets scoped to the environment that needs them, minimal `GITHUB_TOKEN`
permissions, OIDC instead of long-lived cloud keys, never echo them, and no secrets for fork PRs.
</details>

<details><summary>How do you roll back?</summary>

Redeploy the previous immutable image tag (automated on failed health check). Keep DB migrations
backward compatible via expand/contract so the old code still works on the new schema.
</details>

<details><summary>Your tests pass locally but fail in CI. Why might that be?</summary>

Different JDK/Node version, missing env var/secret, timezone/locale, test order dependence, reliance
on local state (a running Postgres), network access, flaky timing, case-sensitive filesystem on
Linux, uncommitted files, lockfile drift (`npm install` vs `npm ci`).
</details>

Deeper résumé drilling: [`17-resume-tech-defense/cicd.md`](../17-resume-tech-defense/cicd.md) and
[`17-resume-tech-defense/github.md`](../17-resume-tech-defense/github.md).

Resources: docs.github.com (Actions documentation: workflow syntax, events, security hardening,
OIDC), Martin Fowler's articles on Continuous Integration and Blue/Green Deployment, *Continuous
Delivery* (Humble & Farley) — skim the first chapters.
