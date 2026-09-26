# Git — Résumé Defense

**Target level:** L3 · **Learned:** Week 1 (init/add/commit/push), Week 2 (branches, merge), Week 5 (rebase, PRs, conflicts); used daily through Week 26
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md)

---

## 1. Beginner questions

<details><summary><b>Q1. What is Git, and how is it different from GitHub?</b></summary>

Git is a distributed version-control system: every clone has the full history. GitHub is a
hosting platform around Git (remotes, pull requests, reviews, Actions, issues).
</details>

<details><summary><b>Q2. Working tree vs staging area vs repository?</b></summary>

Working tree = files on disk. Staging area (index) = what the next commit will contain (`git add`).
Repository = committed history in `.git`. `git status` shows differences between them.
</details>

<details><summary><b>Q3. What is a commit?</b></summary>

An immutable snapshot of the tree plus metadata (author, message, parent commit(s)), identified by
a hash of its content. Branches are just movable pointers to commits.
</details>

<details><summary><b>Q4. <code>git fetch</code> vs <code>git pull</code>?</b></summary>

`fetch` downloads remote commits and updates remote-tracking branches (`origin/main`) without
touching your branch. `pull` = `fetch` + merge (or rebase with `--rebase` / `pull.rebase=true`).
</details>

<details><summary><b>Q5. What goes in <code>.gitignore</code>?</b></summary>

Build output (`target/`, `node_modules/`, `dist/`), IDE files, OS files, logs, and **secrets**
(`.env`). It only affects untracked files — already-tracked files need `git rm --cached`.
</details>

<details><summary><b>Q6. What is <code>HEAD</code>?</b></summary>

A pointer to what you have checked out — usually a branch ref. "Detached HEAD" means it points
directly at a commit; new commits there are easy to lose unless you create a branch.
</details>

## 2. Intermediate questions

<details><summary><b>Q7. Merge vs rebase?</b></summary>

Merge creates a merge commit joining histories; preserves exact history; non-destructive.
Rebase replays your commits on top of another base, creating new commits with new hashes → linear history.
Rule: don't rebase commits others have already pulled (shared branches). Rebase your own feature branch onto `main` before a PR.
</details>

<details><summary><b>Q8. Fast-forward merge?</b></summary>

If the target branch has no new commits since you branched, Git just moves the pointer forward —
no merge commit. `--no-ff` forces a merge commit; `--ff-only` refuses if not possible.
</details>

<details><summary><b>Q9. How do you resolve a merge conflict?</b></summary>

`git status` lists conflicted files → edit between `<<<<<<<`, `=======`, `>>>>>>>` markers to the
correct combined result → run tests → `git add` → `git commit` (merge) or `git rebase --continue`.
Abort with `git merge --abort` / `git rebase --abort`.
</details>

<details><summary><b>Q10. <code>reset</code> vs <code>revert</code> vs <code>restore</code>?</b></summary>

`git revert <sha>` creates a new commit undoing a commit — safe on shared branches. `git reset`
moves the branch pointer (`--soft` keep staged, `--mixed` keep working tree, `--hard` discard) —
rewrites history; local only. `git restore <file>` discards working-tree changes; `--staged` unstages.
</details>

<details><summary><b>Q11. How do you recover a "lost" commit?</b></summary>

`git reflog` lists where `HEAD` has been; find the SHA, then `git branch rescue <sha>` or
`git reset --hard <sha>`. Reflog entries expire (default 90 days reachable / 30 unreachable).
</details>

<details><summary><b>Q12. What does interactive rebase do?</b></summary>

`git rebase -i HEAD~5` lets you reorder, squash, fixup, reword, or drop commits to clean up a
branch before review. (`git commit --fixup <sha>` + `rebase -i --autosquash` is a neat workflow.)
</details>

<details><summary><b>Q13. What are <code>git stash</code>, <code>cherry-pick</code>, <code>bisect</code>?</b></summary>

`stash` shelves uncommitted changes. `cherry-pick <sha>` applies one commit onto the current branch
(e.g. a hotfix to a release branch). `bisect` binary-searches history for the commit that introduced
a bug; `git bisect run mvn -q test` automates it.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "What Git workflow did your team use?"</b></summary>

- Truthful past: describe what you actually remember (feature branches + PRs? trunk? release branches?).
  If you don't remember details, say so.
- Current: "In my projects since Week 5 I use GitHub Flow — short-lived feature branches, PR with a
  description and CI check, squash-merge to `main`, tags for releases (`v1.0`)."
</details>

<details><summary><b>R2. "You pushed a commit with a secret to a shared branch. What now?"</b></summary>

- **Rotate the secret first** — assume it's compromised; history rewriting doesn't un-leak it.
- Then remove it from history if policy requires (`git filter-repo`, BFG), force-push with coordination, and ask GitHub support to purge cached views if needed.
- Prevent: `.gitignore`, env vars, secret scanning / push protection.
</details>

<details><summary><b>R3. "When would you rebase instead of merge?"</b></summary>

- Rebase my private feature branch onto fresh `main` to resolve conflicts once and keep a linear, reviewable history.
- Merge (or squash-merge via PR) to integrate into `main`; never rebase `main` itself.
</details>

<details><summary><b>R4. "A bug appeared sometime in the last 40 commits. How do you find it?"</b></summary>

- `git bisect start; git bisect bad; git bisect good v1.0` then `git bisect run ./mvnw -q -Dtest=RegressionTest test`.
- ~log2(40) ≈ 6 steps. Then write the regression test (keep it).
</details>

<details><summary><b>R5. "What makes a good commit / commit message?"</b></summary>

- One logical change, builds and passes tests. Imperative subject ≤ ~50 chars ("Add hold expiry job"),
  blank line, body explaining *why*. Link issue. Evidence: the FlowGrid and LedgerX commit history — one PR per milestone, each linked to a GitHub milestone/issue.
</details>

## 4. Practical tasks (live)

- [ ] Create a branch, make 3 commits, squash them to 1 with interactive rebase.
- [ ] Create and resolve a conflict in both merge and rebase flows.
- [ ] Undo the last commit but keep the changes (`git reset --soft HEAD~1`).
- [ ] Revert a commit that's already on `main`.
- [ ] Recover a branch deleted with `git branch -D` via reflog.
- [ ] Use `git log --oneline --graph --all` and `git blame -L` to explain a line's history.
- [ ] Tag a release: `git tag -a v1.0 -m "..."; git push origin v1.0`.

## 5. Debugging questions

<details><summary><b>D1. "Updates were rejected because the remote contains work that you do not have locally."</b></summary>

Someone pushed first. `git pull --rebase` (or fetch + merge), resolve conflicts, test, push. Don't `--force` a shared branch; if you truly must after your own rebase, use `--force-with-lease`.
</details>

<details><summary><b>D2. You committed to <code>main</code> locally instead of a feature branch.</b></summary>

`git branch feature/x` (keeps the commits), then `git reset --hard origin/main` on main, `git switch feature/x`.
</details>

<details><summary><b>D3. <code>.gitignore</code> isn't ignoring <code>target/</code>.</b></summary>

It was already tracked. `git rm -r --cached target && git commit`. Also check the pattern and file location.
</details>

<details><summary><b>D4. You're in "detached HEAD" and made commits.</b></summary>

`git switch -c save-work` to keep them on a new branch before switching away.
</details>

<details><summary><b>D5. A rebase went badly mid-way.</b></summary>

`git rebase --abort` returns to the pre-rebase state. If already finished: `git reflog` → `git reset --hard <pre-rebase sha>` (or `ORIG_HEAD`).
</details>

## 6. Architecture questions

- GitHub Flow vs Git Flow vs trunk-based development — which suits a small team with CI/CD (each of my projects)? Why?
- Monorepo (api + worker + React ui in one repo, as in ForgeCI's multi-module build) vs polyrepo — effects on CI, versioning, PRs.
- ForgeCI *consumes* Git: cloning at a commit SHA with a short-lived token, shallow clones (`--depth 1`) for speed, why a build must pin the SHA and not the branch name.
- Squash-merge vs merge commits vs rebase-merge in PR policy — what does each do to `git bisect` and history readability?
- Release tagging and semantic versioning for the projects.

## 7. Common mistakes

- `git add .` committing secrets, build output, or `.env`.
- Force-pushing shared branches; rebasing public history.
- Giant commits mixing refactor + feature + formatting.
- Messages like "fix", "wip", "changes".
- Resolving conflicts by blindly taking "ours"/"theirs" without running tests.
- Long-lived feature branches that drift far from `main`.

## 8. Terminology I must know

| Term | One-line meaning |
|---|---|
| Repository | Project history database (`.git`) |
| Index / staging | Snapshot being prepared for the next commit |
| Commit SHA | Content hash identifying a commit |
| Branch | Movable pointer to a commit |
| HEAD | What's currently checked out |
| Remote / origin | A named other copy of the repo |
| Remote-tracking branch | Local read-only copy of a remote branch (`origin/main`) |
| Fast-forward | Moving a pointer ahead without a merge commit |
| Rebase | Replaying commits on a new base |
| Reflog | Log of where refs have pointed locally |
| Cherry-pick | Apply a single commit elsewhere |
| Tag | Named, fixed pointer (releases) |
| Bisect | Binary search through history for a regression |

## 9. When to use it

Always, for any code, config, infrastructure-as-code, SQL migrations, and docs (like this roadmap).

## 10. When NOT to use it

Large binary assets (use Git LFS or object storage like S3), secrets (use env vars/secret managers),
generated build artifacts, database dumps with personal data.

## 11. Trade-offs

| Choice | Gain | Cost |
|---|---|---|
| Rebase | Linear, readable history | Rewrites SHAs; dangerous on shared branches |
| Merge | True history, safe | Noisy graph |
| Squash-merge PRs | One commit per feature on main | Loses granular commits |
| Trunk-based | Fast integration, fewer conflicts | Needs strong CI + feature flags |

## 12. How it interacts with the rest of my stack

- **GitHub**: remote, PRs, branch protection, Actions triggered by pushes/PRs.
- **Maven/Java**: `target/` ignored; `mvnw` committed; `git bisect run ./mvnw test`.
- **PostgreSQL**: Flyway migrations are versioned in Git; never edit an applied migration.
- **React**: `node_modules/` and `dist/` ignored; lockfile committed.
- **Docker**: `.dockerignore` mirrors `.gitignore` to keep `.git` out of image context; image tags can include the commit SHA.
- **AWS/CI**: deploy pipeline triggered by merges to `main`; commit SHA traces a running build back to source.

## 13. Hands-on exercise

**Conflict + bisect lab on a copy of FlowGrid.**

Acceptance criteria:
- [ ] Two branches edit the same method differently; you resolve via rebase, tests green, history linear.
- [ ] You plant a bug 10 commits back, then find it with `git bisect run` using a JUnit test.
- [ ] You delete a branch and restore it via `reflog`.
- [ ] You explain every command used, out loud, in < 3 minutes (recorded).

## 14. Mastery checklist

- [ ] Explain the three areas (working tree, index, repo) and commits as snapshots.
- [ ] Merge vs rebase with when-to-use rules.
- [ ] Resolve conflicts confidently in merge and rebase.
- [ ] reset (soft/mixed/hard) vs revert vs restore.
- [ ] Recover work with reflog.
- [ ] Use bisect, cherry-pick, stash, interactive rebase.
- [ ] Handle a leaked secret correctly (rotate first).
- [ ] Describe my branching workflow with evidence.

## Evidence in my projects

| Project | What it demonstrates | Fill in: link |
|---|---|---|
| FlowGrid | PR-per-milestone workflow from M1 (W4): feature branches, CI check, squash-merge, GitHub milestone per M1–M5, tag `v1.0` in W8 | |
| LedgerX | Same workflow; a rebase-heavy branch (locking experiments) cleaned with interactive rebase before review; tag `v1.0` in W13 | |
| ForgeCI | Multi-module repo (api, worker, ui): conflicts across modules; ForgeCI *consumes* Git — clones repos at the webhook's commit SHA, reports status per commit | |
| FlagForge | Tags drive the SDK release (`sdk-v1.0.0`) separately from the server `v1.0`; merges to `main` trigger CI/CD; SHA-tagged images | |

## Where to learn it in this repo

[`../02-git/README.md`](../02-git/README.md) · [`../02-git/workflows.md`](../02-git/workflows.md) ·
[`../02-git/exercises.md`](../02-git/exercises.md) · [`../14-cs-fundamentals/software-engineering-practices.md`](../14-cs-fundamentals/software-engineering-practices.md)
