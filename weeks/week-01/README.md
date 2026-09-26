# Week 1 — Set up; write Java from a blank file; version everything with Git

[Roadmap](../../ROADMAP.md) · [Week 2 →](../week-02/)

**Phase 1 — Programming foundation + Java + Git + JUnit** (Weeks 1–4, exit gate: [Checkpoint 4](../../checkpoints/checkpoint-04.md))
**Estimated time: ≈ 21 hours**

| Category | Hours | What it covers this week |
|---|---:|---|
| Core learning | 5 | Terminal, JDK, Java syntax/types/control flow, `String`, arrays, Git model |
| Hands-on coding | 4.5 | Small programs from a blank file, compiled and run from the terminal |
| DSA | 5 | Big-O intro, 4 array problems, first Day-3 reviews |
| Project (setup) | 3 | GitHub profile, repo layout, tracker copies, `dsa-java` repo |
| Revision | 2 | Friday recap, Sunday weekly test |
| Interview | 1.5 | Explain each solution out loud; 60-second "about me" draft |
| **Total** | **21** | |

---

## 1. Main objective

Get a working, reproducible development environment and prove you can write, compile, run and
commit a Java program **without a tutorial open**. Everything in the next 25 weeks — Maven,
Spring Boot, Docker, CI — is driven from a terminal and versioned in Git, so these habits must
be automatic from day one. This week also starts the DSA habit that runs for all 26 weeks: every
problem gets a complexity analysis and an out-loud explanation.

## 2. Prerequisites

- A computer you can install software on (macOS, Linux, or Windows with WSL2 recommended).
- A GitHub account (create one on Monday if you don't have it; use a professional username).
- Nothing else. If you remember Java from previous work, treat this week as a **verification**
  week: do every exercise anyway — speed is the signal, not skipping.

## 3. Topics & subtopics

| Topic | Subtopics | Where to study |
|---|---|---|
| Terminal basics | `pwd`, `ls -la`, `cd`, `mkdir -p`, `touch`, `cp`, `mv`, `rm`, `cat`, `less`, `echo $PATH`, `which` | [10-linux/commands.md](../../10-linux/commands.md) |
| JDK & IDE | Temurin/OpenJDK 21, `JAVA_HOME`, `javac`, `java`, single-file launch, IntelliJ IDEA Community setup | [01-java/README.md](../../01-java/README.md) |
| Java syntax & types | primitives vs references, `int`/`long`/`double`/`char`/`boolean`, casting, overflow, `var`, operators, integer division | [01-java/01-syntax-basics.md](../../01-java/01-syntax-basics.md) |
| Control flow & methods | `if`, `switch` expressions, `for`, enhanced `for`, `while`, `break`/`continue`, `static` methods, parameters, return values | [01-java/01-syntax-basics.md](../../01-java/01-syntax-basics.md) |
| Arrays & strings | `int[]`, default values, `Arrays.toString/sort/fill`, 2-D arrays, `String` immutability, `equals` vs `==`, `StringBuilder` | [01-java/01-syntax-basics.md](../../01-java/01-syntax-basics.md) |
| Git fundamentals | working tree / index / repository, `init`, `status`, `add`, `commit`, `log`, `diff`, `restore`, `.gitignore`, `remote`, `push` | [02-git/README.md](../../02-git/README.md) |
| Big-O | time vs space, constant factors, O(1)/O(n)/O(n²)/O(log n), best/worst/average | [03-dsa/00-big-o.md](../../03-dsa/00-big-o.md) |
| Arrays for DSA | traversal, in-place writes, index arithmetic | [03-dsa/01-arrays-strings.md](../../03-dsa/01-arrays-strings.md), [03-dsa/java-dsa-toolkit.md](../../03-dsa/java-dsa-toolkit.md) |

## 4. Concepts to learn

### 4.1 The terminal is your primary interface
You will run `git`, `mvn`, `java`, `docker`, `psql` and `curl` from a shell for six months.
Learn to navigate without a mouse.

```bash
pwd                         # where am I?
mkdir -p ~/code/dsa-java/src && cd ~/code/dsa-java
ls -la                      # -a shows dotfiles like .git and .gitignore
echo $PATH                  # where the shell looks for programs
which java && java -version # which JDK is actually on PATH?
```
**Interview angle:** "What is `PATH` and why does `java -version` show a different version than your IDE?" — the shell and the IDE can point at different JDKs.

### 4.2 JDK vs JRE vs JVM, compile vs run
`javac` compiles `.java` source to `.class` bytecode; `java` starts a JVM that loads and runs
bytecode (JIT-compiling hot code to native). Since Java 11 you can run a single source file directly.

```bash
javac Hello.java && java Hello     # two steps: compile, then run class Hello
java Hello.java                    # single-file source launch (no .class written)
```
**Interview angle:** "Why is Java called platform-independent?" — bytecode runs on any JVM; the JVM itself is platform-specific.

### 4.3 Primitive types and their traps
```java
int big = Integer.MAX_VALUE;
System.out.println(big + 1);          // -2147483648: silent overflow
System.out.println(7 / 2);            // 3: integer division truncates
System.out.println(7 / 2.0);          // 3.5
long safe = (long) big + 1;           // widen BEFORE adding
char c = 'a'; System.out.println((char)(c + 1)); // 'b'
System.out.println(0.1 + 0.2);        // 0.30000000000000004 -> money needs BigDecimal (Week 3)
```
**Interview angle:** "How do you compute the midpoint of two ints safely?" — `lo + (hi - lo) / 2`, not `(lo + hi) / 2` (overflow).

### 4.4 Control flow, including modern `switch`
```java
String label = switch (day) {          // switch expression (Java 14+), no fall-through
    case 6, 7 -> "weekend";
    case 1, 2, 3, 4, 5 -> "weekday";
    default -> throw new IllegalArgumentException("bad day: " + day);
};
```
**Interview angle:** "Difference between `switch` statement and expression?" — expression yields a value, arrows don't fall through, and it must be exhaustive.

### 4.5 Methods and `static`
A `static` method belongs to the class, not an instance. This week all your code is
`static` helpers called from `main`; objects come in Week 2.

```java
static int maxOf(int[] a) {
    if (a.length == 0) throw new IllegalArgumentException("empty");
    int best = a[0];
    for (int x : a) best = Math.max(best, x);
    return best;
}
```
**Interview angle:** "Why is `main` static?" — the JVM calls it before any object exists.

### 4.6 Arrays
Fixed length, zero-indexed, default-initialised (`0`, `false`, `null`). Length is a field (`a.length`), not a method.

```java
int[] counts = new int[26];                 // all zeros
for (char ch : "banana".toCharArray()) counts[ch - 'a']++;
System.out.println(Arrays.toString(counts)); // printing `counts` directly shows [I@1b6d3586
int[][] grid = new int[3][4];               // 3 rows, 4 cols
```
**Interview angle:** "What happens on `a[a.length]`?" — `ArrayIndexOutOfBoundsException` at runtime; Java checks bounds.

### 4.7 `String` is immutable; `StringBuilder` is not
```java
String a = "hi", b = new String("hi");
System.out.println(a == b);        // false: different objects
System.out.println(a.equals(b));   // true: same characters

StringBuilder sb = new StringBuilder();
for (int i = 0; i < 5; i++) sb.append(i).append(',');
sb.setLength(sb.length() - 1);     // drop trailing comma
System.out.println(sb.reverse());  // 4,3,2,1,0
```
Concatenating with `+` in a loop creates a new `String` each iteration → O(n²) characters copied.
**Interview angle:** "Why use `StringBuilder` in a loop?" — avoids re-copying the whole string every append.

### 4.8 Git's three areas
Working tree → (`git add`) → index/staging → (`git commit`) → repository. A commit is a snapshot plus
metadata and a pointer to its parent.

```bash
git init
git status                 # read this before every command
git add Hello.java
git commit -m "feat: add hello world"
git log --oneline --graph
git diff                   # working tree vs index
git diff --staged          # index vs last commit
git restore --staged X     # unstage; git restore X discards working-tree changes (careful)
git remote add origin git@github.com:<you>/dsa-java.git
git push -u origin main
```
**Interview angle:** "What's the difference between `git add` and `git commit`?" — `add` stages a change in the index; `commit` records the staged snapshot in history.

### 4.9 `.gitignore`
```gitignore
*.class
out/
target/
.idea/
*.iml
.DS_Store
```
**Interview angle:** "You committed a secret — does adding it to `.gitignore` fix it?" — No; it's still in history. Rotate the secret; rewriting history is secondary.

### 4.10 Big-O
Count how work grows with input size `n`, drop constants and lower-order terms. One loop over the
array → O(n). Nested loop over the same array → O(n²). Halving each step → O(log n). Extra array of size n → O(n) space.
**Interview angle:** "What's the time and space complexity?" — you will be asked this after **every** problem. Answer it for every problem this week, out loud.

## 5. Resources

| Category | Source |
|---|---|
| Official docs | dev.java → "Learn" track: *Getting Started*, *Language Basics* · docs.oracle.com/javase/21 (API: `String`, `StringBuilder`, `Arrays`, `Math`) · docs.github.com → "Get started" · git-scm.com/book (Pro Git) ch. 1–2 |
| Books | *Head First Java* (3rd ed.) ch. 1–5 if you want a gentle refresher · *Pro Git* ch. 2 "Git Basics" |
| NeetCode | neetcode.io → Courses → *Data Structures & Algorithms for Beginners*: "Static Arrays", "Dynamic Arrays"; Big-O lesson |
| Practice | leetcode.com (problems below) · the Learn-to-program exercises in [01-java/exercises.md](../../01-java/exercises.md) · [02-git/exercises.md](../../02-git/exercises.md) |

## 6. Exercises & coding assignments

### Exercises (small; each in its own file; compile and run from the terminal)
1. `Temps.java`: convert a hard-coded array of Celsius values to Fahrenheit; print with `"%.1f".formatted(...)`.
2. `Vowels.java`: count vowels in a `String` using `switch` on `char`.
3. `ArrayStats.java`: min, max, sum (as `long`), and average (as `double`) of an `int[]` in **one pass**.
4. `ReverseWords.java`: `"the sky is blue"` → `"blue is sky the"` using `split(" ")` and `StringBuilder`.
5. `Fizz.java`: FizzBuzz 1–100 using a `switch` expression on `i % 15`... then rewrite with `if`. Which is clearer?
6. `Grid.java`: fill a 5×5 `int[][]` with multiplication values and print it aligned.
7. Terminal drill: create `~/code/sandbox/a/b/c`, create 3 files, move one, copy one, delete the tree — without the GUI.

### Coding assignments (from a blank file, no IDE autocomplete for the first one)
**A1 — `WordFreq.java` (in the `dsa-java` repo under `warmups/`)**
Read words from `args`, print each distinct lowercase word and its count, sorted alphabetically.
Acceptance criteria:
- [ ] `java WordFreq.java The cat the Dog` prints `cat 1`, `dog 1`, `the 2` (one per line).
- [ ] Uses only arrays, `String`, `StringBuilder`, `Arrays.sort` (no `HashMap` yet — you'll redo it with one in Week 2).
- [ ] Handles zero arguments by printing `usage: java WordFreq.java <words...>` and exiting with `System.exit(1)`.
- [ ] Committed with a Conventional Commit message.

**A2 — `Matrix.java`**
Static methods `transpose(int[][])`, `rotate90(int[][])` (square only), `print(int[][])`.
Acceptance criteria:
- [ ] Rotating a 3×3 four times returns the original (checked in `main` with `Arrays.deepEquals`).
- [ ] Non-square input to `rotate90` throws `IllegalArgumentException` with a helpful message.
- [ ] Comment at top states time and space complexity of each method.

### Break it (predict first, then run, then write one line on what happened)
1. Delete a semicolon. Read the **first** compiler error only — where does `javac` point?
2. `System.out.println(Integer.MAX_VALUE + 1);` and `Math.abs(Integer.MIN_VALUE)`.
3. Compare two strings built at runtime with `==` (e.g. `new StringBuilder("ab").toString() == "ab"`).
4. Access `args[0]` when running with no arguments. Read the full stack trace: exception type, message, line.
5. Build a 100,000-character string with `+=` in a loop vs `StringBuilder`; time both with `System.nanoTime()`.
6. Add `*.java` to `.gitignore` after files are already committed. Does `git status` stop tracking them? (No — `.gitignore` only affects untracked files; look up `git rm --cached`.)

### Debug it
This method should return the index of the largest element. It has two bugs. Find them by reasoning, then confirm by adding `System.out.println` traces.
```java
static int argMax(int[] a) {
    int best = 0;
    for (int i = 1; i <= a.length; i++) {
        if (a[i] > best) best = i;
    }
    return best;
}
```
<details><summary>Answer</summary>

`i <= a.length` goes out of bounds (should be `<`), and it compares `a[i]` with the **index** `best` instead of `a[best]`.
</details>

## 7. DSA

**Topics:** Big-O notation; array traversal; string building.
**Guides:** [03-dsa/00-big-o.md](../../03-dsa/00-big-o.md) · [03-dsa/01-arrays-strings.md](../../03-dsa/01-arrays-strings.md) · [03-dsa/java-dsa-toolkit.md](../../03-dsa/java-dsa-toolkit.md)

**Method for every problem (start the habit now):** read → restate → 2 examples by hand incl. an edge case →
brute force + its Big-O → code → test by hand → state final time/space → log in the tracker.

| # | Problem | Level | Time limit | Focus |
|---|---|---|---:|---|
| 1929 | [Concatenation of Array](https://leetcode.com/problems/concatenation-of-array/) | Beginner | 15 min | array allocation, index arithmetic `ans[i + n]` |
| 1672 | [Richest Customer Wealth](https://leetcode.com/problems/richest-customer-wealth/) | Beginner | 15 min | 2-D array traversal |
| 27 | [Remove Element](https://leetcode.com/problems/remove-element/) | Beginner | 20 min | in-place write pointer (preview of two pointers) |
| 14 | [Longest Common Prefix](https://leetcode.com/problems/longest-common-prefix/) | Beginner → interview | 25 min | `charAt`, `substring`, early exit, O(n·m) |

**Stretch (only if all four are done):** 1768 Merge Strings Alternately (`StringBuilder`).

**Spaced-repetition reviews due this week:** Day 3 reviews of Monday/Tuesday problems (1929, 1672, 27) on Thursday–Sunday. Re-solve from a blank editor; do not look at your old code first.

**When stuck:** at the time limit, read only the problem's hints / the guide's pattern section, try 10 more minutes, then study a solution, close it, and re-type from memory. Log status honestly (`Solved With Hint`, `Solved With Solution`).

## 8. Project work — setup (no project build this week)

Per the roadmap, Week 1's "project" is your professional workspace. P1 Ledger starts in Week 3
([18-projects/p1-ledger/README.md](../../18-projects/p1-ledger/README.md)).

- [ ] GitHub profile: real name (or professional handle), photo optional, short bio stating what you're building now (no invented claims).
- [ ] Enable 2FA on GitHub; add an SSH key (`ssh-keygen -t ed25519 -C "<your email>"`) and test `ssh -T git@github.com`.
- [ ] Global Git config: `git config --global user.name`, `user.email`, `init.defaultBranch main`, `pull.rebase false` (you'll revisit in Week 5).
- [ ] Create repo **`dsa-java`**: `README.md`, `.gitignore`, folders `warmups/`, `arrays/`. One file per problem, named like `P0027RemoveElement.java`, with a header comment: link, pattern, complexity, date.
- [ ] Put your own copy of the trackers somewhere you commit weekly (fork/clone this roadmap repository and edit [trackers/](../../trackers/dsa-tracker.md) there, or keep a `learning-log` repo).
- [ ] Skim [PROJECTS.md](../../PROJECTS.md) and [18-projects/README.md](../../18-projects/README.md) so you know where Weeks 3–22 are heading.

## 9. Git activity

| Practice | Commands |
|---|---|
| First repo end-to-end | `git init` → `git add` → `git commit` → `git remote add origin` → `git push -u origin main` |
| Inspect before acting | `git status`, `git diff`, `git diff --staged`, `git log --oneline -n 10` |
| Undo safely (uncommitted) | `git restore <file>`, `git restore --staged <file>` |
| Amend the last local commit | `git commit --amend` (only before pushing) |
| Ignore build output | `.gitignore` with `*.class`, `out/`, `.idea/` |

**Commit convention — Conventional Commits** (use from the first commit):
`<type>(optional scope): <imperative summary>` with types `feat`, `fix`, `test`, `refactor`, `docs`, `chore`.
Examples: `feat(arrays): solve 27 remove element`, `docs: add repo conventions to README`, `chore: ignore IntelliJ files`.

**Branches/PRs:** not yet — commit directly to `main` this week. Target: **≥ 10 small commits** on at least 5 different days.

## 10. Interview preparation

- After each DSA problem, explain it out loud in ≤ 2 minutes: approach, why it works, time, space, one edge case.
- Draft your **60-second "about me"** (see [16-interview-prep/recruiter-screen.md](../../16-interview-prep/recruiter-screen.md)). Structure: past roles (truthfully, as they were) → time away (one neutral sentence) → what you're doing now (this roadmap, concrete) → what you want. Write it, then say it out loud 3 times. You'll revise it every few weeks.
- Answer out loud: "What is the difference between JDK, JRE and JVM?", "Why is `String` immutable in Java?", "What does `git commit` actually store?" (cross-check with [17-resume-tech-defense/java.md](../../17-resume-tech-defense/java.md) and [17-resume-tech-defense/git.md](../../17-resume-tech-defense/git.md) — read only their "basics" parts this week).

## 11. Revision work

First week — revision is **consolidation**:
- Friday: rewrite your notes on types, `String` vs `StringBuilder`, Git's three areas in your own words (≤ 1 page).
- Sunday: Day-3 reviews + weekly test. Mark anything you hesitated on as a revision item for Week 2 Monday.

## 12. Daily plan

| Day | Hrs | Blocks |
|---|---:|---|
| **Mon** | 3 | **Core (1.5):** install JDK 21 (Temurin), IntelliJ, Git; terminal navigation drill; `java -version`, `javac`. **Coding (1):** exercises 1–2, run from terminal. **DSA (0.5):** read Big-O guide, analyse your two exercises. |
| **Tue** | 3 | **DSA (1.5):** 1929, 1672 with the full method; log both. **Core/coding (1.5):** primitives, overflow, casting, `switch` expressions; exercises 3 and 5; Break-it 2. |
| **Wed** | 3 | **Setup/project (2):** GitHub profile, SSH key, global config, create and push `dsa-java` with README + `.gitignore`; commit Monday/Tuesday work. **DSA review (1):** re-read your solutions, write complexity comments; Big-O guide exercises. |
| **Thu** | 3 | **Core (1):** arrays, 2-D arrays, `String`/`StringBuilder`. **Coding (1):** exercises 4, 6; Break-it 3–5; Debug-it. **DSA (1):** 27 Remove Element. |
| **Fri** | 2 | **Light:** revision notes (types, strings, Git areas); update [dsa-tracker](../../trackers/dsa-tracker.md) and [weekly-progress](../../trackers/weekly-progress.md); explain 27 out loud. Day-3 review of 1929/1672. |
| **Sat** | 5 | **Project/assignments (3):** A1 WordFreq, A2 Matrix, Break-it 6, clean commits. **DSA (1):** 14 Longest Common Prefix. **Interview (1):** "about me" draft + three out-loud questions. |
| **Sun** | 2 | **Review:** Day-3 review of 27; end-of-week test (below); plan Week 2; rest. |

## 13. End-of-week test (75 min, closed notes, timer on)

**Part A — DSA (35 min):**
1. Unseen: [1480 Running Sum of 1d Array](https://leetcode.com/problems/running-sum-of-1d-array/) — 10 min.
2. Re-solve from blank: 14 Longest Common Prefix — 20 min. State complexity for both.

**Part B — Concepts (15 min, write answers, then check):**
1. What does `7 / 2 * 2.0` evaluate to?
2. Why does `"ab" == new String("ab")` print `false`?
3. What is the default value of each element in `new boolean[3]` and `new String[3]`?
4. Complexity of building an n-character string with `+=` in a loop?
5. Which command shows changes that are staged but not yet committed?
6. You ran `git add secret.txt` but haven't committed. How do you unstage it?
7. Is `for (int i = 0; i < n; i++) for (int j = i; j < n; j++)` O(n²)? Why?
8. What does `java Hello.java` do differently from `javac Hello.java && java Hello`?

<details><summary>Answers</summary>

1. `6.0` — `7 / 2` is integer division = 3, then `3 * 2.0 = 6.0`.
2. `==` compares references; `new String` always creates a new object distinct from the pooled literal.
3. `false, false, false` and `null, null, null`.
4. O(n²) total character copies; `StringBuilder` makes it O(n) amortised.
5. `git diff --staged` (alias `--cached`).
6. `git restore --staged secret.txt`.
7. Yes: n + (n-1) + … + 1 = n(n+1)/2 → O(n²).
8. It compiles in memory and runs a single source file without writing `.class` files.
</details>

**Part C — Small coding task (15 min):** `isPalindrome(String s)` ignoring case, without creating a reversed copy. State complexity.

**Part D — Explain out loud (10 min, record on phone):** "Walk me through what happens from typing `java Hello.java` to seeing output, and how that file gets to GitHub." Listen back: where did you hesitate?

## 14. Mastery checklist

- [ ] `java -version` and `javac -version` both report 21 in a fresh terminal.
- [ ] I can write, compile and run a program with `main`, a static helper, a loop and an array **from a blank file** without looking anything up.
- [ ] I can explain `==` vs `equals` for `String`, and why `StringBuilder` exists, in under 60 seconds each.
- [ ] I can predict integer overflow and integer division results in the Break-it tasks.
- [ ] `dsa-java` exists on GitHub with ≥ 10 Conventional Commits, a README and a working `.gitignore` (no `.class` or `.idea/` files committed).
- [ ] 4 problems logged in the DSA tracker with status, time taken, pattern and next review date.
- [ ] I can state time and space complexity for every problem I solved this week.
- [ ] My "about me" draft exists in writing and I've said it out loud at least 3 times.

## 15. Expected deliverables

- `dsa-java` repo: `warmups/` (exercises + A1 + A2), `arrays/` (4 problems), README with conventions.
- ≥ 10 commits across ≥ 5 days.
- [trackers/dsa-tracker.md](../../trackers/dsa-tracker.md): 4 rows (1929, 1672, 27, 14) with Day-3 review dates.
- [trackers/technology-tracker.md](../../trackers/technology-tracker.md): Java basics, Git basics, terminal rated honestly (1–5).
- [trackers/weekly-progress.md](../../trackers/weekly-progress.md): Week 1 entry — hours, test score, what to carry forward.
- [trackers/interview-tracker.md](../../trackers/interview-tracker.md): "about me" v1 logged.

## 16. If you're behind / stretch goals

**Behind?** Minimum core for this week: JDK working, `dsa-java` pushed, problems 1929 + 27 solved and logged,
A1 done. Move 1672/14 to Week 2 Monday (but do not skip Week 2's own problems — see [ROADMAP §12](../../ROADMAP.md#12-rules-for-falling-behind)).

**Ahead? Stretch:**
- 1768 Merge Strings Alternately; 58 Length of Last Word.
- Read *Pro Git* ch. 10.2 "Git Objects" and run `git cat-file -p HEAD` to see a real commit object.
- Configure a shell alias `gs='git status -sb'` and `gl='git log --oneline --graph -n 20'`.
- Write a tiny Bash script `new-problem.sh 27 RemoveElement` that creates a stub file with the header comment.
