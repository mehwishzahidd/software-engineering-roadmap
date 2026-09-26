# 🌿 02 — Git

> **Outcome:** a correct mental model (working tree → index → commits → refs → HEAD) so that
> every command is predictable, not magic; the ability to undo anything safely; and a
> professional PR-based workflow you use on every project from Week 5.

| Week ([ROADMAP §5](../ROADMAP.md#5-week-by-week-master-table)) | Git topics |
|---|---|
| **W1** | Mental model, `init`, `clone`, `add`, `commit`, `push`, `pull`, `fetch`, `.gitignore`, `log`, `diff`, `status` |
| **W2** | Branches, `switch`, `merge` (fast-forward vs merge commit), simple conflicts |
| **W5** | `rebase`, PRs, conflict resolution, `revert`, `reset`, `reflog`, `stash`, `cherry-pick`, tags; PR-based workflow on P1 begins — see [workflows.md](./workflows.md) |
| W8 → | Tag releases (`v1.0` of each project); résumé defense [../17-resume-tech-defense/git.md](../17-resume-tech-defense/git.md), [../17-resume-tech-defense/github.md](../17-resume-tech-defense/github.md) |

Files: this README (concepts + commands) · [workflows.md](./workflows.md) (team workflows, PRs, commits) · [exercises.md](./exercises.md) (16 broken-repo scenarios with setup scripts).

---

## 1. The mental model

```
 working tree  ──git add──►  index (staging)  ──git commit──►  repository (.git/objects)
 (your files)  ◄─restore───                   ◄──restore --staged / reset──
```

Git stores **snapshots**, not diffs. Four object types live in `.git/objects`, addressed by the
hash of their content:

| Object | Contains |
|---|---|
| **blob** | File contents (no name) |
| **tree** | A directory listing: names → blobs/trees + modes |
| **commit** | A tree (the snapshot), parent commit(s), author, committer, message |
| **tag** (annotated) | A pointer to a commit + tagger + message (+ optional signature) |

```
          ┌──────────── refs (movable names) ─────────────┐
HEAD ──► refs/heads/main ──► C3 ──► C2 ──► C1        (commits point to parents)
         refs/heads/feature ──► F1 ──┘
         refs/remotes/origin/main ──► C2              (your last-known view of the remote)
         refs/tags/v1.0 ──► C2
```

- **Branch** = a file containing one commit hash. Creating a branch is instant; committing moves the branch you're on forward.
- **HEAD** = "where you are": normally a pointer to a branch (`ref: refs/heads/main`). If it points directly to a commit, you're in **detached HEAD**.
- **Remote-tracking branches** (`origin/main`) only move when you `fetch`/`pull`/`push`.
- Commits are **immutable**. "Rewriting history" (rebase, amend, reset) makes *new* commits and moves refs; the old ones stay in the object store until garbage-collected (≈ 30–90 days), reachable via **reflog**. That's why almost nothing is truly lost.

Try it:

```bash
git init demo && cd demo
echo hi > a.txt && git add a.txt && git commit -m "first"
cat .git/HEAD                       # ref: refs/heads/main  (or master, depending on init.defaultBranch)
cat .git/refs/heads/main            # a commit hash
git cat-file -p HEAD                # tree, author, message
git cat-file -p HEAD^{tree}         # the tree → blob for a.txt
```

---

## 2. First-time setup

```bash
git config --global user.name  "Your Name"
git config --global user.email "you@example.com"      # must match a verified GitHub email
git config --global init.defaultBranch main
git config --global pull.rebase false                 # explicit: pull = fetch + merge (change later if you prefer rebase)
git config --global core.editor "code --wait"         # or nano/vim
git config --global alias.lg "log --oneline --graph --decorate --all"
ssh-keygen -t ed25519 -C "you@example.com"            # then add ~/.ssh/id_ed25519.pub to GitHub
ssh -T git@github.com
```

---

## 3. Everyday commands

### Start

```bash
git init                             # new repo in the current dir
git clone git@github.com:you/ledger.git          # copy a remote repo (+ sets up origin)
git clone --depth 1 <url>                        # shallow clone (CI)
```

### Record

```bash
git status                           # ALWAYS before add/commit
git add Money.java                   # stage one file
git add -p                           # stage hunk by hunk (review what you commit)
git add .                            # stage everything under here (check status first!)
git commit -m "feat(money): normalize scale to 2"
git commit --amend                   # replace the LAST commit (only if not pushed)
git restore Money.java               # discard working-tree changes to a file (irreversible!)
git restore --staged Money.java      # unstage, keep changes
git rm --cached secrets.env          # stop tracking a file but keep it on disk
git mv old.java new.java
```

### Inspect: `log`, `diff`, `show`, `blame`

```bash
git log --oneline --graph --decorate --all   # the picture of your repo (alias: git lg)
git log -p -- src/main/java/.../Money.java   # history of a file with patches
git log --author="Your Name" --since="2 weeks ago"
git log -S "removeEldestEntry"               # "pickaxe": commits that added/removed this string
git log main..feature                        # commits on feature not on main

git diff                                     # working tree vs index (unstaged changes)
git diff --staged                            # index vs HEAD (what will be committed)
git diff main...feature                      # changes on feature since it branched from main (PR view)
git diff HEAD~3 -- pom.xml

git show HEAD                                # last commit: message + patch
git show v1.0:pom.xml                        # a file as of a tag

git blame -L 40,60 src/.../CsvImporter.java  # who last changed each line, in which commit
git blame -w -C ...                          # ignore whitespace, detect moved lines
```
Use `blame` to find the **commit**, then `git show <hash>` to read *why* (the message and PR) — not to find someone to blame.

### Sync with a remote

```bash
git remote -v
git fetch                            # download new commits; updates origin/*; your branches untouched
git pull                             # fetch + merge origin/<branch> into current branch
git pull --rebase                    # fetch + rebase your local commits on top (linear history)
git push -u origin feature/csv-import   # first push; -u sets upstream so later `git push` just works
git push                             # push current branch to its upstream
git push --force-with-lease          # after rebasing YOUR OWN branch; refuses if someone else pushed
```
**`fetch` vs `pull`:** fetch is always safe (only updates `origin/*`). Pull also changes your branch. When unsure: `git fetch` then `git lg` then decide.

---

## 4. Branches and merging

```bash
git switch -c feature/rules-engine          # create + switch (older: git checkout -b)
git switch main
git branch                                   # list local; -a includes remotes; -vv shows upstreams
git branch -d feature/rules-engine           # delete if merged; -D forces
git merge feature/rules-engine               # merge INTO the current branch
```

**Fast-forward vs merge commit:**

```
Before:   main: A─B            feature: A─B─C─D
FF merge: main: A─B─C─D        (main pointer just moves; no new commit)

Before:   main: A─B─E          feature: A─B─C─D
Merge:    main: A─B─E───M      (M has two parents: E and D)
                  └─C─D─┘
```
`git merge --no-ff` forces a merge commit even when FF is possible (keeps the "this was a feature" grouping).

### Rebase

```
Before:  main: A─B─E        feature: A─B─C─D
git switch feature && git rebase main
After:   main: A─B─E        feature: A─B─E─C'─D'    (C', D' are NEW commits)
```
- Replays your commits on top of the target → linear history, easy review.
- **Golden rule: never rebase commits that others have based work on** (shared branches like `main`). Rebasing your own unmerged feature branch is fine; push with `--force-with-lease`.
- During a rebase: resolve conflict → `git add` → `git rebase --continue`; or `git rebase --abort` to go back.

### Conflicts

A conflict happens when both sides changed the same lines (or one deleted a file the other modified).

```
<<<<<<< HEAD
    return amount.setScale(2, RoundingMode.HALF_EVEN);
=======
    return amount.setScale(2, RoundingMode.HALF_UP);
>>>>>>> feature/rounding
```
1. `git status` — lists "both modified" files.
2. Open each, decide the correct **combined** result (not just "mine" or "theirs"), delete markers.
3. Compile and run tests.
4. `git add <file>` then `git commit` (merge) or `git rebase --continue` (rebase).
5. Bail out: `git merge --abort` / `git rebase --abort`.

Helpers: `git config --global merge.conflictstyle zdiff3` shows the **common ancestor** between
markers (the "3-way" view) — makes intent obvious. `git checkout --ours/--theirs <file>` takes one side wholesale.
Note: during a **rebase**, "ours" is the branch you're rebasing onto and "theirs" is your commit being replayed — reversed from merge.

---

## 5. Undoing things — pick the right tool

| Situation | Command | Rewrites history? |
|---|---|---|
| Discard unstaged edits to a file | `git restore <file>` | No (but edits are gone) |
| Unstage | `git restore --staged <file>` | No |
| Fix last commit's message/content (not pushed) | `git commit --amend` | Yes (local) |
| Undo a commit **already pushed** | `git revert <hash>` | **No** — adds an inverse commit |
| Undo commits locally, keep changes staged | `git reset --soft HEAD~2` | Yes |
| Undo commits locally, keep changes unstaged | `git reset --mixed HEAD~2` (default) | Yes |
| Throw away commits **and** changes | `git reset --hard HEAD~2` | Yes — dangerous |
| "I lost a commit / branch / bad reset" | `git reflog` then `git branch rescue <hash>` or `git reset --hard <hash>` | — |
| Temporarily shelve work | `git stash` / `git stash pop` | No |
| Copy one commit to another branch | `git cherry-pick <hash>` | No (new commit) |

### `reset` — the three modes

`git reset <commit>` moves the **current branch** to `<commit>`. The mode decides what happens to the index and working tree:

| Mode | Branch ref | Index | Working tree | Typical use |
|---|:-:|:-:|:-:|---|
| `--soft` | moved | unchanged (keeps old changes staged) | unchanged | Squash last N commits into one: `git reset --soft HEAD~3 && git commit` |
| `--mixed` (default) | moved | reset to match commit | unchanged | Re-split commits; unstage everything |
| `--hard` | moved | reset | **reset** (uncommitted work lost) | Throw away local experiments; match remote: `git reset --hard origin/main` |

Uncommitted changes destroyed by `--hard` are **not** in the reflog. Commit or stash first.

### `revert` — the safe undo for shared history

```bash
git revert a1b2c3d                 # new commit that applies the inverse patch
git revert -m 1 <merge-hash>       # revert a merge commit, keeping parent 1 (mainline)
git revert HEAD~2..HEAD            # revert a range (newest first)
```

### `reflog` — your safety net

```bash
git reflog                          # every position HEAD has had, with messages
# a1b2c3d HEAD@{0}: reset: moving to HEAD~3
# f00ba12 HEAD@{1}: commit: feat: budgets
git reset --hard HEAD@{1}           # go back to before the bad reset
git branch recovered f00ba12        # or resurrect a deleted branch
git reflog show feature/x           # per-branch reflog
```
Reflog is **local only** (not pushed) and entries expire (default 90 days; 30 for unreachable).

### `stash`

```bash
git stash push -m "wip: budget alerts"     # -u to include untracked files
git stash list
git stash show -p stash@{0}
git stash pop                               # apply + drop (conflicts possible)
git stash apply stash@{1}                   # apply, keep in list
git stash drop stash@{0}
```

### `cherry-pick`

```bash
git switch release/1.0
git cherry-pick 9f8e7d6                     # copy a hotfix commit from main
git cherry-pick -x 9f8e7d6                  # append "(cherry picked from commit ...)" to message
```
Creates a new commit with a different hash; if later merged, Git usually recognizes identical changes, but avoid cherry-pick as a regular workflow.

---

## 6. Tags

```bash
git tag -a v1.0 -m "Ledger v1.0: Postgres persistence, atomic import"   # annotated (use these for releases)
git tag v1.0-lw                              # lightweight (just a ref)
git tag --list 'v*'
git show v1.0
git push origin v1.0                         # tags are NOT pushed by default
git push origin --tags
git switch --detach v1.0                     # inspect the release (detached HEAD)
git tag -d v1.0 && git push origin :refs/tags/v1.0   # delete locally and remotely (avoid for published releases)
```
Every project in this roadmap ends with an annotated `v1.0` tag and a GitHub Release.

---

## 7. `.gitignore`

```gitignore
# Java / Maven
target/
*.class
*.log
hs_err_pid*
*.hprof

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
Thumbs.db

# Node (P3/P4 frontends)
node_modules/
dist/

# Secrets — NEVER commit
.env
*.pem
application-local.yml
```
- Patterns: `dir/` directory, `*.ext` glob, `/file` root-only, `!keep.me` negate, `**/logs`.
- `.gitignore` only affects **untracked** files. Already committed? `git rm --cached <file>` then commit.
- Committed a secret? Removing it in a new commit is **not enough** — it's in history and possibly cloned. **Rotate the secret first**, then clean history (`git filter-repo`) if needed. GitHub secret scanning may alert you.
- Templates: github.com/github/gitignore.
- Commit `mvnw` and `.mvn/wrapper/`; ignore `target/`.

---

## 8. Pull requests (overview)

A PR is a GitHub feature, not a Git one: "please merge branch X into Y", with diff, discussion, CI
checks and approvals.

```bash
git switch -c feat/budget-alerts
# ...commits...
git push -u origin feat/budget-alerts
gh pr create --fill --base main                # GitHub CLI; or use the web UI
gh pr checks                                   # CI status
gh pr merge --squash --delete-branch
```
Full workflow, etiquette, review and merge strategies: [workflows.md](./workflows.md).

---

## 9. `git bisect` (find the commit that broke it)

```bash
git bisect start
git bisect bad                    # current commit is broken
git bisect good v0.3              # this one was fine
# Git checks out the midpoint; test, then:
git bisect good   # or: git bisect bad
# ...repeat (log2(n) steps)...
git bisect reset
# Automated:
git bisect run ./mvnw -q -Dtest=ReportServiceTest test
```

---

## Command reference (the list you must know)

| Command | One-liner |
|---|---|
| `init` / `clone` | Create repo / copy remote repo |
| `add` / `commit` | Stage / snapshot staged changes |
| `status` / `log` / `diff` / `show` / `blame` | Inspect state, history, changes, a commit, line authorship |
| `push` / `fetch` / `pull` | Upload / download / download + integrate |
| `branch` / `switch` | List-create-delete branches / move HEAD |
| `merge` / `rebase` | Integrate by merge commit or FF / replay commits |
| `revert` | Inverse commit (safe on shared history) |
| `reset --soft/--mixed/--hard` | Move branch; keep staged / keep unstaged / discard |
| `reflog` | History of HEAD positions — recovery |
| `stash` | Shelve uncommitted work |
| `cherry-pick` | Copy a commit onto current branch |
| `tag` | Name a commit (releases) |
| `restore` | Discard/unstage file changes |
| `bisect` | Binary-search for a bad commit |

---

## ⚠️ Common mistakes

- `git add .` without `git status` → committing `target/`, `.env`, `.idea/`.
- `git push --force` on `main` or a shared branch. Use `--force-with-lease`, only on your own branches.
- Using `reset --hard` to "undo" a pushed commit → diverged history for everyone. Use `revert`.
- Committing directly on `main` once the PR workflow starts (W5).
- Giant commits "various fixes". One logical change per commit.
- Resolving conflicts by picking one side blindly, without compiling/testing.
- Never fetching → surprises at push time.

## 🎤 Interview questions

<details><summary>1. What's the difference between git fetch and git pull?</summary>

`fetch` downloads commits and updates remote-tracking refs (`origin/main`) without touching your
branches or files. `pull` = `fetch` + integrate (merge by default, or rebase with `--rebase`) into the current branch.
</details>

<details><summary>2. Merge vs rebase?</summary>

Merge preserves history as it happened, adding a merge commit with two parents; non-destructive.
Rebase replays commits on a new base, creating new commits and a linear history; rewrites history, so
never rebase shared commits. I rebase my own feature branch onto `main` before a PR and let the PR merge strategy decide the rest.
</details>

<details><summary>3. How do you undo a commit that's already been pushed?</summary>

`git revert <hash>` — creates a new commit that inverts it, keeping shared history intact. Not
`reset`, which would require force-pushing and break teammates' clones.
</details>

<details><summary>4. Explain reset --soft, --mixed and --hard.</summary>

All move the current branch to a commit. Soft keeps index and working tree (changes staged); mixed
resets the index (changes unstaged); hard resets both (uncommitted changes lost).
</details>

<details><summary>5. You accidentally deleted a branch with unmerged work. Recover it.</summary>

`git reflog` (or the hash printed by `git branch -D`), find the tip commit, `git branch <name> <hash>`.
Works as long as the commits haven't been garbage-collected.
</details>

<details><summary>6. What is HEAD? What is detached HEAD?</summary>

HEAD is the reference to the current checkout — normally a symbolic ref to a branch. Detached HEAD
means it points directly to a commit (checked out a tag/hash); new commits there aren't on any
branch — create one with `git switch -c <name>` to keep them.
</details>

<details><summary>7. How do you resolve a merge conflict?</summary>

`git status` to list files, edit each to the correct combined result using the 3-way context
(zdiff3), remove markers, build and test, `git add`, then `git commit` / `git rebase --continue`. Abort if needed.
</details>

<details><summary>8. What is cherry-pick and when would you use it?</summary>

Applies the changes from a specific commit as a new commit on the current branch. Use for
back-porting a hotfix to a release branch; avoid as a substitute for merging.
</details>

<details><summary>9. What does git stash do?</summary>

Saves uncommitted changes (tracked; `-u` for untracked) onto a stack and cleans the working tree,
so you can switch context; restore with `pop`/`apply`.
</details>

<details><summary>10. How does Git store data?</summary>

Content-addressed objects: blobs (file content), trees (directories), commits (tree + parents +
metadata), annotated tags. Branches and tags are refs pointing to commits. Snapshots, not diffs (packfiles delta-compress on disk).
</details>

## ✅ Mastery checklist

- [ ] Draw working tree / index / commits / refs / HEAD and explain `add`, `commit`, `switch` on it
- [ ] Explain a branch as "a file with a hash"; inspect `.git/HEAD` and `.git/refs`
- [ ] Use `log --graph`, `diff --staged`, `show`, `blame`, `log -S` fluently
- [ ] Merge with FF and with a merge commit; rebase a feature branch; resolve a 3-way conflict
- [ ] Choose correctly between `restore`, `revert`, `reset --soft/--mixed/--hard`
- [ ] Recover a deleted branch and undo a bad `reset --hard` with `reflog`
- [ ] Use `stash`, `cherry-pick`, annotated tags, `bisect`
- [ ] Write a `.gitignore` for Java + Node + secrets; untrack a committed file
- [ ] Complete ≥ 12 scenarios in [exercises.md](./exercises.md)
