# Git Workflows, Pull Requests & Code Review (Week 5 onward)

> **Outcome:** work on every project the way a team does: short-lived branches, small PRs
> with clear descriptions, CI checks, self-review, a consistent commit convention, and a
> deliberate merge strategy. Each flagship project (FlowGrid, LedgerX, ForgeCI, FlagForge) lives in
> its **own GitHub repository** with CI from its first PR (Week 4); from **Week 5**, nothing lands on
> `main` without a PR, and each milestone is a GitHub milestone with issues.

Related: [README.md](./README.md) · CI checks on PRs: [../13-cicd/github-actions.md](../13-cicd/github-actions.md) · GitHub docs: docs.github.com (Pull requests, Protected branches)

---

## 1. GitHub flow (use this for all four projects)

```
main ──●────────────●──────────────●────────►   always deployable, protected
        \          / (squash)      \       /
         ●──●──●──●  feat/…          ●──●─●  fix/…
```

1. `git switch main && git pull` — start from the latest `main`.
2. `git switch -c feat/reservation-expiry` — one branch per change; name it `type/short-desc`.
3. Commit small, logical steps with Conventional Commit messages.
4. `git push -u origin feat/reservation-expiry`; open a PR early (draft) if you want feedback.
5. CI runs (`./mvnw -B verify`, later `npm test`, lint).
6. Review (self-review when solo — see §4), address comments with new commits.
7. Update with `main` (`git rebase main` + `--force-with-lease`, or merge `main` in).
8. Merge (squash by default), delete the branch.
9. `main` is deployed / tagged when a milestone is done.

Rules of thumb: branches live **< 2 days**; PRs are **< ~400 lines** of meaningful diff; one concern per PR.

### Alternatives you should be able to describe

| Workflow | Shape | Where it fits |
|---|---|---|
| **GitHub flow** | `main` + short feature branches + PRs | Continuous delivery, web services (this roadmap) |
| **Trunk-based** | Everyone commits to `main` (or branches < 1 day), feature flags hide unfinished work | High-velocity teams with strong CI |
| **Git Flow** | `main`, `develop`, `feature/*`, `release/*`, `hotfix/*` | Versioned software with scheduled releases; heavy for web apps |
| **Forking model** | Contributors push to their fork, PR into upstream | Open source, untrusted contributors |

---

## 2. Feature branches in practice

```bash
git switch main && git pull
git switch -c feat/reservation-expiry

# work...
git add -p && git commit -m "feat(reservations): add expires_at to reservations"
git add -p && git commit -m "feat(reservations): scheduled release of expired reservations"
git add -p && git commit -m "test(reservations): expired reservation frees stock"

git fetch origin
git rebase origin/main              # replay my commits on the latest main
./mvnw -q verify                    # still green after rebase?
git push -u origin feat/reservation-expiry          # first time
git push --force-with-lease         # after later rebases of THIS branch
```

Branch naming: `feat/…`, `fix/…`, `refactor/…`, `test/…`, `docs/…`, `chore/…` — matching commit types.

---

## 3. Conventional Commits

Format (conventionalcommits.org):

```
<type>(<optional scope>): <imperative summary, ≤ 72 chars, no period>

<optional body: WHY, not what; wrap at 72>

<optional footer: BREAKING CHANGE: ..., Refs #12>
```

| Type | Use for |
|---|---|
| `feat` | New user-visible behavior |
| `fix` | Bug fix |
| `refactor` | Code change with no behavior change |
| `test` | Adding/fixing tests |
| `docs` | Documentation only |
| `perf` | Performance improvement |
| `build` | Build system, dependencies (pom.xml) |
| `ci` | CI config (GitHub Actions) |
| `chore` | Maintenance that fits nowhere else |

Examples in the style of the projects:

```
feat(orders): store Idempotency-Key with request hash and response
fix(money): normalize BigDecimal scale so 1.5 equals 1.50
refactor(allocation): extract WarehouseScorer strategy
perf(ledger): add index on ledger_entry(account_id, created_at)
feat(webhooks): verify X-Hub-Signature-256 before parsing payload
test(reservations): exactly one of 20 concurrent reservations wins
build: bump spring-boot-starter-parent to 3.3.x
ci: run mvn verify on pull requests

feat(api)!: require Idempotency-Key on POST /orders

BREAKING CHANGE: clients must send an Idempotency-Key header.
```

Why it matters: readable history, changelog generation, semantic versioning (`feat` → minor,
`fix` → patch, `!`/`BREAKING CHANGE` → major), and it forces one-purpose commits.

**Good vs bad:**

| ❌ | ✅ |
|---|---|
| `fixed stuff` | `fix(csv): accept negative amounts in parentheses` |
| `WIP` | (squash WIP commits before review) |
| `Update Money.java` | `refactor(money): replace double with BigDecimal` |

---

## 4. PR etiquette

### Author checklist (put this in `.github/pull_request_template.md`)

```markdown
## What
One or two sentences: what changes for the user/system.

## Why
Link the issue / milestone (e.g. FlowGrid M2, #14). The problem this solves.

## How
Key design decisions and trade-offs. Anything reviewers should look at first.

## Testing
- [ ] Unit tests added/updated
- [ ] `./mvnw verify` passes locally
- [ ] Manual check: `curl -i -X POST localhost:8080/api/orders -H 'Idempotency-Key: …' ...` → 201, repeat → same response

## Screenshots / output
(For UI or CLI changes)

## Checklist
- [ ] Self-reviewed the diff on GitHub
- [ ] No secrets, no debug prints, no commented-out code
- [ ] Docs/README updated if behavior changed
```

Author rules:
- **Self-review first**: read your own diff in the GitHub UI. You'll catch 30% of issues.
- Small PRs. Split refactors from behavior changes ("prep" PR, then "feature" PR).
- Keep the PR description current if the approach changes.
- Respond to every comment (fix, or explain why not). Don't take comments personally.
- Don't force-push during review without saying so — reviewers lose context. Prefer fixup commits, then squash at merge.

### Solo developer version (this roadmap)

You're mostly alone, so simulate a team: open the PR, wait until **the next day**, then review it
with the reviewer checklist below and leave at least one comment on your own PR. Let CI gate the
merge. This builds the habits interviewers ask about ("walk me through your PR process").

---

## 5. Code review

### Reviewer checklist (in priority order)

1. **Correctness** — does it do what the PR says? Edge cases: null, empty, huge, concurrent, time zones, money rounding.
2. **Tests** — do they test behavior, fail without the change, cover edge cases?
3. **Design** — right layer? Duplicates existing code? Simplest thing that works?
4. **Security** — input validation, authZ checks, SQL injection (string-concatenated SQL), secrets in code/logs.
5. **Readability** — names, method size, comments explaining *why*.
6. **Operational** — logging, error messages, migrations reversible, performance (N+1 queries, missing index).
7. Style — let formatters/linters handle it.

### Writing comments

- Be specific and kind; comment on code, not people. Ask questions: "What happens if `reservations` is empty here?"
- Label severity: **blocking**, `nit:` (optional polish), `question:`, `suggestion:` (use GitHub's suggestion blocks).
- Approve with minor nits rather than blocking on style.
- Praise good things too — it's information.

---

## 6. Merge strategies: merge vs squash vs rebase-merge

GitHub offers three buttons:

| Strategy | Result on `main` | Pros | Cons |
|---|---|---|---|
| **Create a merge commit** | All branch commits + a merge commit (2 parents) | Full history, clear PR boundary, easy to revert the whole PR (`revert -m 1`) | Noisy graph; WIP commits on main |
| **Squash and merge** | One new commit containing the whole PR | Clean linear history, one commit = one PR = easy revert/bisect; WIP commits vanish | Loses individual commit granularity; branch commits ≠ main commit (delete branch after) |
| **Rebase and merge** | Each branch commit replayed onto main, no merge commit | Linear + keeps granular commits | Every commit must be clean and build; new hashes |

This roadmap's default: **squash and merge**, with the PR title in Conventional Commit format
(it becomes the commit message). Disable the other two in repo settings for consistency.

### Cleaning up before review without interactive rebase

```bash
# Squash all commits on the branch into one (branch based on main):
git reset --soft $(git merge-base main HEAD)
git commit -m "feat(alerts): scheduled low-stock alerts"
git push --force-with-lease
```
`reset --soft` moves the branch back to where it forked but keeps all changes staged — one fresh commit. See exercise scenario in [exercises.md](./exercises.md).

---

## 7. Protected branches (set this up on every project repo)

GitHub → Settings → Branches → Branch protection rule (or Rulesets) for `main`:

- [x] Require a pull request before merging (0 approvals when solo; 1+ in teams)
- [x] Require status checks to pass (select the CI job, e.g. `build`) — from **Week 4** (every project has CI from its first PR)
- [x] Require branches to be up to date before merging
- [x] Require conversation resolution
- [x] Block force pushes and deletions
- [ ] Require signed commits (optional)
- [ ] Include administrators (turn on to keep yourself honest)

Also: `CODEOWNERS` file (auto-request reviewers per path), required linear history (pairs with squash/rebase merge).

---

## 8. Forking model (open source)

```bash
gh repo fork someorg/somelib --clone         # fork on GitHub + clone your fork
cd somelib
git remote -v                                # origin = your fork, upstream = original
git switch -c fix/typo-in-readme
# commit...
git push -u origin fix/typo-in-readme
gh pr create --repo someorg/somelib          # PR from your fork's branch to upstream main

# keep your fork current
git fetch upstream
git switch main && git merge --ff-only upstream/main && git push origin main
```
Read `CONTRIBUTING.md` first; sign the CLA if required; keep the PR tiny for a first contribution.

---

## 9. Releases

```bash
git switch main && git pull
git tag -a v1.0 -m "LedgerX v1.0: double-entry core, idempotent transfers, reconciliation"
git push origin v1.0
gh release create v1.0 --generate-notes     # notes from merged PR titles (Conventional Commits pay off)
```

---

## 🎤 Interview questions

<details><summary>1. Describe your Git workflow.</summary>

GitHub flow: protected `main`, short-lived feature branches named by type, Conventional Commits,
PR with template and self-review, CI must pass (`mvn verify`), squash-merge, delete branch, tag
releases. Rebase my own branch on `main` before merging; never force-push shared branches.
</details>

<details><summary>2. Squash vs merge commit vs rebase merge — which do you prefer?</summary>

Squash for feature PRs: one commit per PR on `main`, clean history, easy revert and bisect. Merge
commits when the individual commits are meaningful and you want the PR boundary. Rebase-merge when
every commit is clean and you want linear granular history. Consistency matters more than the choice.
</details>

<details><summary>3. What makes a good pull request?</summary>

Small and single-purpose, descriptive title and body (what/why/how/testing), tests included, CI
green, self-reviewed, no unrelated changes, easy to review in < 30 minutes.
</details>

<details><summary>4. What do you look for when reviewing code?</summary>

Correctness and edge cases, tests, design fit, security, readability, operational concerns
(logging, migrations, performance). Style is for linters. Comments are specific, kind, and labeled by severity.
</details>

<details><summary>5. What are protected branches for?</summary>

To enforce process on important branches: PR required, status checks passing, reviews, no force
pushes or deletion — so `main` is always releasable and history can't be rewritten accidentally.
</details>

<details><summary>6. What is trunk-based development?</summary>

Everyone integrates into `main` at least daily with very short branches (or none), relying on
strong CI and feature flags to hide incomplete features. Minimizes merge pain; requires discipline and fast tests.
</details>

## ✅ Mastery checklist

- [ ] All project repos have protected `main` and a PR template
- [ ] Every change since W5 went through a PR with a Conventional Commit title
- [ ] Can explain squash vs merge vs rebase-merge with trade-offs
- [ ] Reviewed own PRs the next day with the reviewer checklist; left comments
- [ ] Used `reset --soft $(git merge-base main HEAD)` to clean a branch
- [ ] Made one small open-source (or classmate) PR via the forking model
- [ ] Tagged and released `v1.0` for FlowGrid (W8) with generated notes; same for LedgerX (W13), ForgeCI (W18), FlagForge (W23)
