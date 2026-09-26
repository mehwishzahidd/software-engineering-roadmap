# Git Exercises — 17 Broken-Repo Scenarios

> Each scenario has a **setup script** that builds a small repository in a specific state
> (often broken, conflicted, or "oh no"), a **task**, **acceptance checks** you can run, and a
> hidden **solution**. Try for 10–15 minutes before opening the solution. Always start with
> `git status` and `git log --oneline --graph --all`.

| # | Scenario | Week | Skills |
|---:|---|---|---|
| 01 | Ignore build output and secrets before the first commit | W1 | `.gitignore`, `status`, `add` |
| 02 | A secret got committed (not pushed yet) | W1 | `rm --cached`, `reset --soft` |
| 03 | Fast-forward vs merge commit | W2 | `merge`, `--no-ff`, `ORIG_HEAD` |
| 04 | First merge conflict | W2 | conflict markers |
| 05 | 3-way conflict: both sides were right | W2 | `zdiff3`, combining changes |
| 06 | Rebase a feature branch with a conflict | W5 | `rebase`, `--continue` |
| 07 | Recover a deleted branch | W5 | `reflog` |
| 08 | Undo a bad `reset --hard` | W5 | `reflog`, `reset` |
| 09 | Undo a pushed commit safely | W5 | `revert`, bare remote |
| 10 | Clean up WIP commits without interactive rebase | W5 | `reset --soft` |
| 11 | Committed to `main` by mistake | W5 | `branch`, `reset --hard origin/main` |
| 12 | Forgot a file and typo'd the message | W1 | `commit --amend` |
| 13 | Urgent hotfix in the middle of work | W5 | `stash -u`, `stash pop` |
| 14 | Back-port one fix to a release branch | W5 | `cherry-pick -x`, tags |
| 15 | Push rejected: histories diverged | W5 | `fetch`, `pull --rebase`, `push` |
| 16 | Find the commit that broke it | W5+ | `bisect run` |
| 17 | Code archaeology: who wrote this and why? | W5+ | `log -S`, `blame`, `show` |

---

## One-time lab setup

Create `~/git-lab/common.sh` (every setup script sources it):

```bash
mkdir -p ~/git-lab
cat > ~/git-lab/common.sh <<'EOF'
# Usage: source ~/git-lab/common.sh; lab 07
lab() {
  local dir="$HOME/git-lab/$1"
  rm -rf "$dir" "$dir-remote.git" "$dir-teammate"
  mkdir -p "$dir" && cd "$dir" || return 1
  git init -q -b main
  git config user.name "Lab User"
  git config user.email "lab@example.com"
  git config commit.gpgsign false
  git config merge.conflictstyle zdiff3
}
# commit <file> <content> <message>
commit() { mkdir -p "$(dirname "$1")"; printf '%s\n' "$2" > "$1"; git add "$1"; git commit -q -m "$3"; }
# make_remote: create a bare "GitHub" next to the repo and push main to it
make_remote() {
  local remote="$(pwd)-remote.git"
  git init -q --bare -b main "$remote"
  git remote add origin "$remote"
  git push -q -u origin main
}
EOF
```

Run each setup **as a script in your current shell** so you end up inside the repo:
`source setup-07.sh` (or paste the block into the terminal). Requires Git ≥ 2.28 (`git --version`).

Tip: commands that would open an editor are shown with `--no-edit` or `GIT_EDITOR=true` so you
can copy them; in real life, write proper messages.

---

## 01 — Ignore build output and secrets before the first commit (W1)

```bash
source ~/git-lab/common.sh; lab 01
mkdir -p src/main/java/app target/classes .idea
echo 'public class App {}' > src/main/java/app/App.java
echo 'CAFEBABE' > target/classes/App.class
echo 'DB_PASSWORD=hunter2' > .env
echo '<project/>' > pom.xml
echo '<xml/>' > .idea/workspace.xml
```

**Task:** make the first commit contain only source, `pom.xml`, and a `.gitignore`.

**Acceptance:**
- [ ] `git ls-files` prints exactly `.gitignore`, `pom.xml`, `src/main/java/app/App.java`
- [ ] `git status --short` prints nothing (ignored files don't show)

<details><summary>Solution</summary>

```bash
printf 'target/\n.idea/\n.env\n' > .gitignore
git status --short              # only .gitignore, pom.xml, src/ remain untracked
git add .
git commit -q -m "chore: initial project skeleton"
git ls-files
```
</details>

---

## 02 — A secret got committed (not pushed yet) (W1)

```bash
source ~/git-lab/common.sh; lab 02
commit App.java 'public class App {}' "feat: app skeleton"
echo 'DB_PASSWORD=hunter2' > .env
echo 'class Db {}' > Db.java
git add . && git commit -q -m "feat: database config"
commit README.md '# Katas' "docs: readme"
```

**Task:** (a) remove `.env` from **all** history (it was never pushed) while keeping the file on
disk and the other changes intact; (b) make sure it can't be committed again.

**Acceptance:**
- [ ] `git log -p | grep hunter2` prints nothing
- [ ] `.env` still exists on disk, and `git status --short` doesn't list it
- [ ] `Db.java` and `README.md` are still committed

<details><summary>Solution</summary>

```bash
git reset --soft HEAD~2         # undo the last 2 commits, keep their changes staged
git rm -q --cached .env         # unstage .env, keep it on disk
echo '.env' > .gitignore
git add .gitignore
git commit -q -m "feat: database config and readme"
git log -p | grep hunter2 || echo "clean"
```
If it **had** been pushed: rotate the password first (assume it's compromised), then rewrite
history with `git filter-repo` and force-push in coordination with the team. Deleting it in a new
commit does nothing for the copies already out there.
</details>

---

## 03 — Fast-forward vs merge commit (W2)

```bash
source ~/git-lab/common.sh; lab 03
commit Money.java 'class Money {}' "feat: money"
git switch -q -c feature/currency
commit Currency.java 'enum Currency { EUR, USD }' "feat: currency enum"
commit Money.java 'class Money { Currency c; }' "feat: money has currency"
git switch -q main
```

**Task:** (1) merge `feature/currency` into `main` and observe it's a fast-forward; (2) undo that
merge; (3) merge again forcing a merge commit. Compare `git log --oneline --graph` both times.

**Acceptance:**
- [ ] After step 3, `git log --merges --oneline` shows exactly one merge commit
- [ ] You can explain when Git can fast-forward

<details><summary>Solution</summary>

```bash
git merge feature/currency                 # "Fast-forward"
git log --oneline --graph
git reset -q --hard ORIG_HEAD              # ORIG_HEAD = where main was before the merge
git merge --no-ff --no-edit feature/currency
git log --oneline --graph
git log --merges --oneline
```
FF is possible when the current branch is an ancestor of the branch being merged — the pointer just moves.
</details>

---

## 04 — First merge conflict (W2)

```bash
source ~/git-lab/common.sh; lab 04
commit Greeting.java 'String greet() { return "Hello"; }' "feat: greeting"
git switch -q -c feature/greeting
commit Greeting.java 'String greet() { return "Hello, world"; }' "feat: greet the world"
git switch -q main
commit Greeting.java 'String greet() { return "Hi"; }' "style: shorter greeting"
git merge feature/greeting || true         # CONFLICT
```

**Task:** finish the merge. Product decided the final text is `"Hi, world"`.

**Acceptance:**
- [ ] `cat Greeting.java` → `String greet() { return "Hi, world"; }` with no markers
- [ ] `git log --merges --oneline` shows the merge commit; `git status` clean

<details><summary>Solution</summary>

```bash
git status                                  # both modified: Greeting.java
cat Greeting.java                           # <<<<<<< HEAD ... ||||||| base ... ======= ... >>>>>>>
printf '%s\n' 'String greet() { return "Hi, world"; }' > Greeting.java
git add Greeting.java
git commit -q --no-edit
```
</details>

---

## 05 — 3-way conflict: both sides were right (W2)

```bash
source ~/git-lab/common.sh; lab 05
cat > Money.java <<'EOF'
public BigDecimal round(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_UP);
}
EOF
git add . && git commit -q -m "feat: rounding"
git switch -q -c fix/bankers-rounding
sed -i.bak 's/HALF_UP/HALF_EVEN/' Money.java && rm -f Money.java.bak
git commit -q -am "fix: use banker's rounding"
git switch -q main
sed -i.bak 's/round(/normalize(/' Money.java && rm -f Money.java.bak
git commit -q -am "refactor: rename round to normalize"
git merge fix/bankers-rounding || true
```

**Task:** resolve so that **both** intentions survive. Read the `|||||||` (base) section that
`zdiff3` shows to see what each side changed relative to the ancestor.

**Acceptance:**
- [ ] `grep -c 'normalize(' Money.java` → 1 and `grep -c HALF_EVEN Money.java` → 1
- [ ] No `round(` and no `HALF_UP` remain; merge committed

<details><summary>Solution</summary>

```bash
cat Money.java
# <<<<<<< HEAD
# public BigDecimal normalize(BigDecimal amount) {
#     return amount.setScale(2, RoundingMode.HALF_UP);
# ||||||| <base>
# public BigDecimal round(BigDecimal amount) {
#     return amount.setScale(2, RoundingMode.HALF_UP);
# =======
# public BigDecimal round(BigDecimal amount) {
#     return amount.setScale(2, RoundingMode.HALF_EVEN);
# >>>>>>> fix/bankers-rounding
# }
cat > Money.java <<'EOF'
public BigDecimal normalize(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_EVEN);
}
EOF
git add Money.java && git commit -q --no-edit
```
Lesson: the base shows *ours changed line 1, theirs changed line 2* — the correct answer takes both,
which "accept ours" / "accept theirs" buttons would never give you. In a real project: compile and run tests before committing.
</details>

---

## 06 — Rebase a feature branch with a conflict (W5)

```bash
source ~/git-lab/common.sh; lab 06
commit app.properties 'server.port=8080' "chore: config"
git switch -q -c feature/timeouts
commit app.properties 'server.port=8080
http.timeout=5s' "feat: http timeout"
commit Checker.java 'class Checker {}' "feat: checker"
git switch -q main
commit app.properties 'server.port=9090' "chore: move to port 9090"
git switch -q feature/timeouts
```

**Task:** rebase `feature/timeouts` onto `main` so history is linear. Final config must have port
9090 **and** the timeout.

**Acceptance:**
- [ ] `git log --oneline --graph` shows a straight line: config → port 9090 → timeout → checker
- [ ] `git merge-base --is-ancestor main feature/timeouts && echo ok` prints `ok`
- [ ] `app.properties` = `server.port=9090` + `http.timeout=5s`

<details><summary>Solution</summary>

```bash
git rebase main || true                       # conflict while replaying "feat: http timeout"
git status                                     # "interactive rebase in progress"... both modified
printf 'server.port=9090\nhttp.timeout=5s\n' > app.properties
git add app.properties
GIT_EDITOR=true git rebase --continue          # replays the remaining commit
git log --oneline --graph
```
If the branch was already pushed: `git push --force-with-lease` (only because it's *your* branch).
</details>

---

## 07 — Recover a deleted branch (W5)

```bash
source ~/git-lab/common.sh; lab 07
commit README.md '# Katas' "docs: readme"
git switch -q -c feature/budgets
commit Budget.java 'record Budget(String category, long limitCents) {}' "feat: budget record"
commit BudgetTracker.java 'class BudgetTracker {}' "feat: budget tracker"
git switch -q main
git branch -D feature/budgets > /dev/null
clear 2>/dev/null || true; echo "Oops. feature/budgets is gone (and it was never merged or pushed)."
```

**Task:** bring the branch back with both commits.

**Acceptance:**
- [ ] `git log --oneline feature/budgets` shows both budget commits

<details><summary>Solution</summary>

```bash
git reflog                                 # find "commit: feat: budget tracker" — the old tip
git branch feature/budgets HEAD@{1}        # HEAD@{1} = before "checkout: moving from feature/budgets to main"
# or, more robustly: git branch feature/budgets <hash-from-reflog>
git log --oneline feature/budgets
```
</details>

---

## 08 — Undo a bad `reset --hard` (W5)

```bash
source ~/git-lab/common.sh; lab 08
commit Money.java 'v1' "feat: money v1"
commit Money.java 'v2' "feat: money v2"
commit Money.java 'v3' "feat: money v3"
commit Money.java 'v4' "feat: money v4"
git reset -q --hard HEAD~3
echo "You meant HEAD~1 ... now 3 commits are 'gone'."; git log --oneline
```

**Task:** restore `main` to where it was before the reset.

**Acceptance:**
- [ ] `git log --oneline | wc -l` → 4 and `cat Money.java` → `v4`

<details><summary>Solution</summary>

```bash
git reflog              # HEAD@{0}: reset: moving to HEAD~3 ; HEAD@{1}: commit: feat: money v4
git reset --hard HEAD@{1}
```
Only **committed** work is recoverable this way. Uncommitted changes wiped by `reset --hard` are gone.
</details>

---

## 09 — Undo a pushed commit safely (W5)

```bash
source ~/git-lab/common.sh; lab 09
commit rules.txt 'coffee -> Food
rent -> Housing
salary -> Income' "feat: default rules"
make_remote
commit rules.txt 'coffee -> Food
salary -> Income' "refactor: tidy rules"          # silently deleted the rent rule!
commit README.md '# Rules' "docs: readme"
git push -q
git clone -q "$(pwd)-remote.git" "$(pwd)-teammate"   # a teammate already has these commits
echo "The rent rule vanished in 'refactor: tidy rules', which is already on origin/main."
```

**Task:** undo *only* the bad commit, without rewriting published history, and push.

**Acceptance:**
- [ ] `grep rent rules.txt` finds the rule; `README.md` still exists
- [ ] `git log --oneline origin/main | wc -l` → 4 (3 original + 1 revert)
- [ ] The teammate can `git -C ../09-teammate pull` without conflicts or force

<details><summary>Solution</summary>

```bash
git log --oneline                          # find the hash of "refactor: tidy rules"
git revert --no-edit HEAD~1                # or: git revert <hash>
git push -q
git -C "$(pwd)-teammate" pull -q && git -C "$(pwd)-teammate" log --oneline
```
Why not `reset --hard HEAD~2` + `push --force`? The teammate's clone would diverge, and it would also throw away the good README commit.
</details>

---

## 10 — Clean up WIP commits without interactive rebase (W5)

```bash
source ~/git-lab/common.sh; lab 10
commit README.md '# Katas' "docs: readme"
git switch -q -c feature/alerts
commit Alert.java 'class Alert {' "wip"
commit Alert.java 'class Alert { String msg; }' "wip 2"
commit AlertTest.java 'class AlertTest {}' "tests??"
commit Alert.java 'final class Alert { String msg; }' "fix typo"
commit Alert.java 'final class Alert { final String msg; Alert(String m) { msg = m; } }' "done i think"
git tag before-squash
```

**Task:** turn the 5 commits on `feature/alerts` into **one** commit
`feat(alerts): add Alert value class` with exactly the same final content. No `rebase -i`.

**Acceptance:**
- [ ] `git rev-list --count main..feature/alerts` → 1
- [ ] `git diff before-squash` prints nothing (same tree)

<details><summary>Solution</summary>

```bash
git reset --soft "$(git merge-base main HEAD)"   # back to the fork point, all changes staged
git status --short                                # A Alert.java, A AlertTest.java
git commit -q -m "feat(alerts): add Alert value class"
git rev-list --count main..feature/alerts
git diff before-squash && echo "identical"
```
If the branch was pushed: `git push --force-with-lease`. (GitHub's "Squash and merge" does the same at merge time.)
</details>

---

## 11 — Committed to `main` by mistake (W5)

```bash
source ~/git-lab/common.sh; lab 11
commit README.md '# Katas' "docs: readme"
make_remote
commit Report.java 'class Report {}' "feat: monthly report"
commit ReportTest.java 'class ReportTest {}' "test: monthly report"
echo "Two commits on local main that should have been on feature/reports (not pushed yet)."
```

**Task:** move the two commits onto a new branch `feature/reports`; make `main` match `origin/main` again.

**Acceptance:**
- [ ] `git rev-parse main` equals `git rev-parse origin/main`
- [ ] `git log --oneline main..feature/reports` shows both commits; you're on `feature/reports`

<details><summary>Solution</summary>

```bash
git branch feature/reports          # new branch points at the current commit (keeps the work)
git reset -q --hard origin/main     # move main back
git switch feature/reports
git log --oneline --graph --all
```
</details>

---

## 12 — Forgot a file and typo'd the message (W1)

```bash
source ~/git-lab/common.sh; lab 12
commit README.md '# Katas' "docs: readme"
echo 'class Report {}' > Report.java
echo 'class ReportTest {}' > ReportTest.java
git add Report.java && git commit -q -m "feat: add reprot"
```

**Task:** one commit containing both files, message `feat(reports): add monthly report`. (Not pushed.)

**Acceptance:**
- [ ] `git show --stat HEAD` lists both files; `git log --oneline | wc -l` → 2

<details><summary>Solution</summary>

```bash
git add ReportTest.java
git commit -q --amend -m "feat(reports): add monthly report"
git show --stat HEAD
```
</details>

---

## 13 — Urgent hotfix in the middle of work (W5)

```bash
source ~/git-lab/common.sh; lab 13
commit App.java 'class App { int port = 8008; }' "feat: app"
git switch -q -c feature/budgets
commit App.java 'class App { int port = 8008; Budget b; }' "feat: wire budget"
echo 'class App { int port = 8008; Budget b; Alerts a; }' > App.java      # uncommitted edit
echo 'class Alerts {}' > Alerts.java                                        # untracked file
git switch main 2>&1 | head -3 || true
echo "Can't switch: local changes would be overwritten. Prod needs port 8080 NOW."
```

**Task:** on `main`, fix the port to `8080` and commit `fix: correct default port`. Then return to
`feature/budgets` with your uncommitted edit and `Alerts.java` exactly as they were.

**Acceptance:**
- [ ] `git show main:App.java` contains `8080`
- [ ] On `feature/budgets`: `App.java` has `Alerts a;` (uncommitted), `Alerts.java` exists (untracked)
- [ ] `git stash list` is empty

<details><summary>Solution</summary>

```bash
git stash push -u -m "wip: alerts"     # -u includes the untracked Alerts.java
git switch main
sed -i.bak 's/8008/8080/' App.java && rm -f App.java.bak
git commit -q -am "fix: correct default port"
git switch feature/budgets
git stash pop
git status --short                      # M App.java, ?? Alerts.java
```
Follow-up: `git rebase main` on the feature branch (after committing) to pick up the hotfix.
</details>

---

## 14 — Back-port one fix to a release branch (W5)

```bash
source ~/git-lab/common.sh; lab 14
commit CsvImporter.java 'class CsvImporter { /* v1 */ }' "feat: csv importer"
git tag -a v1.0 -m "v1.0"
git branch release/1.0 v1.0
commit Budget.java 'class Budget {}' "feat: budgets"
commit EmptyFile.java 'class EmptyFile { /* handle empty csv */ }' "fix(csv): handle empty file"
commit Alerts.java 'class Alerts {}' "feat: alerts"
```

**Task:** users on 1.0 need *only* the empty-file fix. Put it on `release/1.0` (recording where it
came from) and tag `v1.0.1`.

**Acceptance:**
- [ ] `git log --oneline v1.0..release/1.0` shows exactly one commit, and its full message contains `cherry picked from commit`
- [ ] `release/1.0` has no `Budget.java`/`Alerts.java`; `git tag` lists `v1.0.1`

<details><summary>Solution</summary>

```bash
git log --oneline main                          # find "fix(csv): handle empty file"
fix=$(git log --format=%H --grep='fix(csv)' -n 1 main)
git switch release/1.0
git cherry-pick -x "$fix"
git tag -a v1.0.1 -m "v1.0.1: handle empty CSV files"
git log --oneline --graph --all --decorate
```
</details>

---

## 15 — Push rejected: histories diverged (W5)

```bash
source ~/git-lab/common.sh; lab 15
commit README.md '# ForgeCI playground' "docs: readme"
make_remote
me="$(pwd)"
git clone -q "$me-remote.git" "$me-teammate"
( cd "$me-teammate" && git config user.name "Teammate" && git config user.email "tm@example.com" \
  && printf 'pipelines: []\n' > config.yml && git add . && git commit -q -m "chore: config" && git push -q )
commit Worker.java 'class Worker {}' "feat: job worker"
git push 2>&1 | tail -3 || true
echo "Rejected: the remote has a commit you don't have."
```

**Task:** publish your commit on top of the teammate's, keeping history linear. No force push.

**Acceptance:**
- [ ] `git log --oneline origin/main` shows readme → config → job worker, with no merge commit
- [ ] `git status` says up to date with `origin/main`

<details><summary>Solution</summary>

```bash
git fetch
git log --oneline --graph --all         # see the fork: your commit vs teammate's
git rebase origin/main                  # or: git pull --rebase
git push
git log --oneline --graph origin/main
```
With `git pull` (merge mode) you'd get a merge commit instead — also correct, just not linear.
</details>

---

## 16 — Find the commit that broke it (W5+)

```bash
source ~/git-lab/common.sh; lab 16
printf '#!/bin/sh\n[ "$(cat rate.txt)" = "0.20" ]\n' > test.sh && chmod +x test.sh
echo '0.20' > rate.txt
git add . && git commit -q -m "feat: tax rate + test"
for i in $(seq 1 30); do
  echo "note $i" >> notes.txt
  [ "$i" -eq 19 ] && echo '0.02' > rate.txt          # the silent regression
  git add . && git commit -q -m "chore: update notes $i"
done
./test.sh && echo PASS || echo "FAIL: rate is $(cat rate.txt)"
```

**Task:** find the first bad commit in ≤ 6 steps using `git bisect` (automate it with `bisect run`).

**Acceptance:**
- [ ] You name the commit (`chore: update notes 19`) and show its diff with `git show`
- [ ] `git bisect reset` done; you're back on `main`

<details><summary>Solution</summary>

```bash
git bisect start
git bisect bad HEAD
git bisect good "$(git rev-list --max-parents=0 HEAD)"   # the first commit
git bisect run ./test.sh                                  # exit 0 = good, 1..124 = bad
git show --stat refs/bisect/bad
git bisect reset
```
In Java projects: `git bisect run ./mvnw -q -Dtest=SomeTest test`.
</details>

---

## 17 — Code archaeology: who wrote this and why? (W5+)

```bash
source ~/git-lab/common.sh; lab 17
commit Checker.java 'class Checker {
  static final int MAX_RETRIES = 3;
}' "feat: checker with retries"
git -c user.name="Ana" -c user.email="ana@example.com" commit -q --allow-empty -m "chore: noop"
printf 'class Checker {\n  static final int MAX_RETRIES = 7;\n}\n' > Checker.java
git add . && git -c user.name="Bo" -c user.email="bo@example.com" commit -q \
  -m "fix(checker): raise retries to 7" \
  -m "Provider X returns 503 for up to ~20s during deploys; 3 retries with backoff produced false DOWN alerts. See incident 2025-03-02."
commit README.md '# Checker' "docs: readme"
```

**Task:** without reading the whole log, find who changed `MAX_RETRIES` to 7, when, and **why**.

**Acceptance:**
- [ ] You can state author (Bo), the commit hash, and the reason (503s during provider deploys)

<details><summary>Solution</summary>

```bash
git blame -L 2,2 Checker.java              # hash + author for that line
git log -S 'MAX_RETRIES = 7' --oneline     # the commit that introduced the string
git show <hash>                            # full message (the WHY) + diff
```
This is why commit bodies explain *why*: the code shows what, blame finds the commit, the message gives the reason.
</details>

---

## ✅ Completion tracker

- [ ] 01 · [ ] 02 · [ ] 03 · [ ] 04 · [ ] 05 · [ ] 06 · [ ] 07 · [ ] 08 · [ ] 09
- [ ] 10 · [ ] 11 · [ ] 12 · [ ] 13 · [ ] 14 · [ ] 15 · [ ] 16 · [ ] 17
- [ ] Re-did 05, 07, 09 and 10 from memory one week later (the ones interviews ask about)
