# GitHub — Résumé Defense

**Target level:** L2 · **Learned:** Week 1 (profile, remotes), Week 5 (PRs, reviews), Week 12 (Actions CI), Week 22 (CI/CD, environments, secrets)
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md)

---

## 1. Beginner questions

<details><summary><b>Q1. What does GitHub add on top of Git?</b></summary>

Hosted remotes, pull requests with code review, issues/projects, Actions (CI/CD), branch
protection/rulesets, releases, security features (Dependabot, secret scanning, code scanning), Pages, packages/container registry (GHCR).
</details>

<details><summary><b>Q2. What is a pull request?</b></summary>

A request to merge one branch into another, with a diff, discussion, reviews, and status checks.
It's the unit of review and the gate before code reaches `main`.
</details>

<details><summary><b>Q3. Fork vs clone vs branch?</b></summary>

Clone = local copy of a repo. Branch = line of work inside a repo. Fork = your own server-side copy
of someone else's repo (open-source contribution: fork → branch → PR to upstream).
</details>

<details><summary><b>Q4. HTTPS vs SSH authentication?</b></summary>

HTTPS uses a personal access token (fine-grained PATs preferred) or credential manager; SSH uses
a key pair registered on your account (`ssh-keygen -t ed25519`). Passwords aren't accepted for Git operations.
</details>

<details><summary><b>Q5. What are GitHub Issues and labels for?</b></summary>

Tracking bugs/features/tasks; labels categorise; `Fixes #12` in a PR description closes the issue on merge.
</details>

## 2. Intermediate questions

<details><summary><b>Q6. What are branch protection rules / rulesets?</b></summary>

Rules on branches like `main`: require PR, N approving reviews, passing status checks, up-to-date
branch, linear history, no force-push, signed commits. Enforces the workflow instead of relying on discipline.
</details>

<details><summary><b>Q7. Merge options on a PR?</b></summary>

Create a merge commit (keeps all commits + merge commit), squash and merge (one commit on main),
rebase and merge (replays commits linearly, no merge commit). Repos can restrict which are allowed.
</details>

<details><summary><b>Q8. Anatomy of a GitHub Actions workflow?</b></summary>

YAML in `.github/workflows/`. `on:` triggers (push, pull_request, schedule, workflow_dispatch) →
`jobs:` (run in parallel by default, `needs:` for ordering) → each job `runs-on:` a runner → `steps:`
with `uses:` (actions) or `run:` (shell).
```yaml
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: ./mvnw -B verify
```
</details>

<details><summary><b>Q9. How do secrets work in Actions?</b></summary>

Stored encrypted at repo/org/environment level, exposed as `${{ secrets.NAME }}`, masked in logs.
Not passed to workflows triggered by PRs from forks. Better for AWS: OIDC (`id-token: write` +
`aws-actions/configure-aws-credentials` assuming an IAM role) — no long-lived keys.
</details>

<details><summary><b>Q10. What are environments?</b></summary>

Named deploy targets (e.g. `production`) with their own secrets, required reviewers, and wait
timers — gate deploys behind approval.
</details>

<details><summary><b>Q11. What does <code>CODEOWNERS</code> do?</b></summary>

Maps paths to owners who are auto-requested for review; with branch protection, their approval can be required.
</details>

<details><summary><b>Q12. Dependabot and secret scanning?</b></summary>

Dependabot opens PRs to update vulnerable/outdated dependencies (Maven, npm, Docker, Actions).
Secret scanning detects committed credentials; push protection blocks the push.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "Walk me through how code got from your laptop to production in your last team."</b></summary>

- Truthful past: describe what you remember; it's fine if it was Bitbucket/GitLab/Jenkins — say so.
- Current, concrete: PulseWatch — branch → PR → Actions runs tests → review (self-review checklist
  as solo dev) → squash-merge → pipeline builds image, pushes to registry, deploys to EC2.
</details>

<details><summary><b>R2. "How do you review a pull request?"</b></summary>

- Understand intent (description/issue) → run it or read tests → correctness, edge cases, error handling,
  security (authz, injection, secrets), naming/readability, test quality → comment kindly and specifically;
  distinguish blocking vs nit.
- Evidence: review checklist you used on your own PRs; any open-source PR comments (only if real).
</details>

<details><summary><b>R3. "What makes a good PR?"</b></summary>

- Small and focused (< ~400 lines), clear title, description with what/why/how-to-test, screenshots for UI,
  linked issue, green CI, self-reviewed first.
</details>

<details><summary><b>R4. "Your CI passes locally but fails on GitHub Actions. How do you debug?"</b></summary>

- Read the failing step log; compare environment (JDK version, OS, env vars, timezone, Docker availability for Testcontainers).
- Reproduce: same Java version, clean `~/.m2`, `./mvnw -B verify`; re-run job with debug logging (`ACTIONS_STEP_DEBUG` secret).
</details>

<details><summary><b>R5. "Can I look at your GitHub?"</b></summary>

- Yes — pinned repos: TicketHold, TeamBoard, PulseWatch with READMEs, diagrams, CI badges, tagged releases.
- Be honest that they're personal learning projects; point to one PR that shows a design decision.
</details>

## 4. Practical tasks (live)

- [ ] Open a PR from a feature branch with a proper template; link and auto-close an issue.
- [ ] Add branch protection on `main` requiring PR + passing `build` check.
- [ ] Write a CI workflow for a Maven project with caching.
- [ ] Add a repository secret and use it in a workflow without echoing it.
- [ ] Configure Dependabot for `maven` and `github-actions`.
- [ ] Use `gh` CLI: `gh pr create`, `gh pr checks`, `gh run view --log-failed`.

## 5. Debugging questions

<details><summary><b>D1. "Permission denied (publickey)" when pushing.</b></summary>

SSH key not loaded/registered. `ssh -T git@github.com` to test; `ssh-add`; confirm the remote URL
is SSH (`git remote -v`); or switch to HTTPS with a token.
</details>

<details><summary><b>D2. Workflow doesn't run on a PR.</b></summary>

Wrong trigger (`on: push` only, branch filters), YAML error (check Actions tab), workflow file not on
the default branch for some triggers, or Actions disabled for the repo.
</details>

<details><summary><b>D3. Testcontainers tests fail only in CI with "Could not find a valid Docker environment".</b></summary>

Job runs in a container or runner without Docker. `ubuntu-latest` hosted runners have Docker; don't run the job inside a `container:` without Docker socket access.
</details>

<details><summary><b>D4. Deploy job fails: "Resource not accessible by integration".</b></summary>

`GITHUB_TOKEN` permissions too narrow. Add a `permissions:` block (e.g. `packages: write` to push to GHCR, `id-token: write` for OIDC).
</details>

## 6. Architecture questions

- Single workflow vs separate CI (every PR) and CD (on `main`/tag) workflows — why split?
- Secrets vs OIDC for deploying to AWS; how to scope the IAM role trust policy to one repo/branch.
- Reusable workflows / composite actions when you have 3 Java services.
- How would you design repo settings for a 5-person team (protection, CODEOWNERS, required checks)?

## 7. Common mistakes

- Pushing directly to `main`; no branch protection.
- Huge PRs; PRs without description or tests.
- Long-lived AWS access keys as secrets instead of OIDC.
- Unpinned third-party actions (pin to a tag or, better, a commit SHA).
- Printing secrets in logs via `set -x` or `echo`.
- Empty profile/READMEs on pinned repos.

## 8. Terminology I must know

| Term | One-line meaning |
|---|---|
| Pull request | Proposed merge with review and checks |
| Review / approval | Formal sign-off (approve / request changes) |
| Status check | CI result attached to a commit/PR |
| Branch protection / ruleset | Enforced rules on branches |
| CODEOWNERS | Auto-assigned reviewers by path |
| Workflow / job / step | Actions YAML file / unit on one runner / single command |
| Runner | Machine executing jobs (hosted or self-hosted) |
| `GITHUB_TOKEN` | Auto-generated, scoped token per workflow run |
| OIDC | Short-lived federated credentials to clouds |
| Environment | Deploy target with protection rules and secrets |
| GHCR | GitHub Container Registry |
| Dependabot | Automated dependency update PRs |

## 9. When to use it

Hosting code, collaboration via PRs, CI/CD with Actions for most small/medium projects, public portfolio.

## 10. When NOT to use it

When an organisation standardises on GitLab/Bitbucket/Azure DevOps; air-gapped environments; very large
build farms where self-hosted CI is cheaper (you can still use self-hosted runners).

## 11. Trade-offs

| Choice | Gain | Cost |
|---|---|---|
| Actions (hosted runners) | Zero infra, tight PR integration | Minutes cost, less control, vendor lock-in |
| Strict branch protection | Quality gate | Slower for tiny fixes |
| Squash-merge | Clean main | Lost commit granularity |

## 12. How it interacts with the rest of my stack

- **Git**: GitHub is the `origin` remote; PRs sit on top of branches.
- **Maven/JUnit**: CI runs `./mvnw -B verify`; failures block merge.
- **Docker**: workflows build and push images (GHCR or ECR) tagged with the commit SHA.
- **AWS**: CD authenticates via OIDC role, deploys to EC2.
- **React**: separate job runs `npm ci && npm test && npm run build`.
- **PostgreSQL**: Testcontainers or a `services: postgres` container in CI jobs.

## 13. Hands-on exercise

**Harden the TicketHold repository.**

Acceptance criteria:
- [ ] `main` protected: PR required, `build` check required, no force-push.
- [ ] CI workflow on `pull_request` and `push` to `main`, Maven cache, runs Testcontainers tests.
- [ ] PR template with What/Why/How to test/Checklist.
- [ ] Dependabot config for Maven + Actions; one Dependabot PR reviewed and merged.
- [ ] README has CI badge, run instructions, and architecture diagram.

## 14. Mastery checklist

- [ ] Explain PR workflow and merge options.
- [ ] Configure branch protection and required checks.
- [ ] Write a CI workflow from memory.
- [ ] Explain secrets vs OIDC and `GITHUB_TOKEN` permissions.
- [ ] Debug a failing Actions run from logs.
- [ ] Review a PR with a clear checklist.
- [ ] Present my GitHub profile in 2 minutes, honestly.

## Evidence in my projects

| Project | What it demonstrates | Fill in: link |
|---|---|---|
| P1 Ledger | PR workflow from M3, issues, tag `v1.0` release | |
| P2 TicketHold | Actions CI (`mvn verify`) with Testcontainers, badge, protected `main` | |
| P3 TeamBoard | Separate backend/frontend jobs in one workflow | |
| P4 PulseWatch | Full CI/CD: test → build image → push → deploy to AWS | |

## Where to learn it in this repo

[`../02-git/workflows.md`](../02-git/workflows.md) · [`../02-git/README.md`](../02-git/README.md) ·
[`../13-cicd/github-actions.md`](../13-cicd/github-actions.md) · [`../13-cicd/pipeline-examples.md`](../13-cicd/pipeline-examples.md) ·
[`../12-aws/iam.md`](../12-aws/iam.md)
