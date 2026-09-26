# Week 1 — Write Java from a blank file; version it

[Roadmap](../../ROADMAP.md) · [Week 2 →](../week-02/)

**Phase 0 · Foundation** (exercises only — nothing this week is portfolio material)

| Block | Hours | What it means this week |
|---|---:|---|
| Project (foundation coding) | 15 | Java katas from a blank file, committed to a throwaway repo |
| Learning | 15 | Terminal & Linux basics, JDK/IDE, Java syntax, control flow, methods, arrays, Strings, Git core |
| DSA | 7 | Big-O + Arrays — **6 new problems** + Day-3 reviews |
| Interview / review | 3 | Explain every DSA solution out loud; draft a 60-second "about me" |

---

## 1. Main objective

By Sunday you can open a terminal, create a Maven-free Java 21 file, compile and run it, write a
method that manipulates an array or a `String` correctly, state its Big-O, and commit the result to
GitHub with a sensible message. That is the floor everything else stands on: Week 2 adds classes,
tests and Maven; Week 3 adds streams, SQL and the first HTTP endpoint; Week 4 starts
[FlowGrid](../../18-projects/flowgrid/README.md).

You have written Java professionally before. This week is not about "learning Java"; it is about
removing the rust *fast*, on Java 21 specifically, and installing the habits (terminal, Git,
Big-O, explaining out loud) that every later week assumes.

## 2. Prerequisites

- A laptop with ≥ 8 GB RAM, admin rights, and a GitHub account.
- No prior checkpoint. If your Java is genuinely fresh, spend Monday and Tuesday on
  [`01-java/01-syntax-basics.md`](../../01-java/01-syntax-basics.md) and skip nothing.

## 3. Learning topics

| Topic | Subtopics | Folder file |
|---|---|---|
| Terminal & Linux basics | shell, `pwd`/`ls`/`cd`, paths, `cat`/`less`/`grep`, pipes, `chmod`, `PATH`, env vars | [`10-linux/commands.md`](../../10-linux/commands.md), [`10-linux/README.md`](../../10-linux/README.md) |
| JDK 21 + IDE | install JDK 21 (Temurin), `java -version`, `javac`, single-file `java Foo.java`, IntelliJ IDEA (Community is enough), `JAVA_HOME` | [`01-java/README.md`](../../01-java/README.md) |
| Java syntax & control flow | primitives vs references, `var`, `if`/`switch` (arrow form), loops, `break`/`continue`, methods, overloading, `static` | [`01-java/01-syntax-basics.md`](../../01-java/01-syntax-basics.md) |
| Arrays & Strings | 1-D/2-D arrays, `Arrays.*`, `String` immutability, `StringBuilder`, `charAt`, `equals` vs `==`, text blocks | [`01-java/01-syntax-basics.md`](../../01-java/01-syntax-basics.md) |
| Git core | `init`/`clone`/`status`/`add`/`commit`/`log`/`diff`, `.gitignore`, remotes, `push`/`pull`, SSH keys | [`02-git/README.md`](../../02-git/README.md), [`02-git/exercises.md`](../../02-git/exercises.md) |
| Big-O | time/space, dominant term, common classes, amortised cost (informal) | [`03-dsa/00-big-o.md`](../../03-dsa/00-big-o.md) |

## 4. Concepts to learn

### 4.1 Primitives, references and `==`

```java
int a = 1_000, b = 1_000;
Integer x = 1_000, y = 1_000;
System.out.println(a == b);        // true  — primitives compare values
System.out.println(x == y);        // false — boxed references outside the -128..127 cache
System.out.println(x.equals(y));   // true
String s1 = "grid", s2 = new String("grid");
System.out.println(s1 == s2);      // false — different objects
System.out.println(s1.equals(s2)); // true
```

- **Interview angle:** "Why does `==` on two `Integer`s sometimes return true?" (the small-value cache).
- **FlowGrid uses this:** every `equals(...)` on IDs and SKU codes; comparing `Long` entity IDs with `==` is a classic bug you will plant on purpose in Week 4's "Break it".

### 4.2 Strings are immutable; build with `StringBuilder`

```java
String csv = "";
for (int i = 0; i < n; i++) csv += i + ",";     // O(n²): each += copies the whole string

StringBuilder sb = new StringBuilder();
for (int i = 0; i < n; i++) sb.append(i).append(',');
String out = sb.toString();                      // O(n)
```

- **Interview angle:** "What is the complexity of concatenating in a loop, and how do you fix it?"
- **FlowGrid uses this:** building pick-list export lines and CSV report exports (M5, S3 uploads).

### 4.3 Modern `switch` and `var`

```java
static String zone(char aisle) {
    return switch (aisle) {
        case 'A', 'B' -> "north";
        case 'C'      -> "south";
        default       -> throw new IllegalArgumentException("unknown aisle " + aisle);
    };
}
var counts = new int[26];   // var infers int[]; the variable is still statically typed
```

- **Interview angle:** "What changed in `switch` in recent Java versions?" (arrow form, expression, no fall-through, exhaustiveness with sealed types later).
- **FlowGrid uses this:** mapping inventory states (`on_hand`, `available`, `reserved`…) to behaviour in the reservation state machine (M2).

### 4.4 Arrays: fixed size, O(1) index, O(n) shift

```java
int[] qty = new int[5];             // zero-initialised
qty[2] = 7;
int[][] grid = new int[3][4];       // 3 rows, 4 columns
int[] copy = Arrays.copyOf(qty, qty.length);
Arrays.sort(qty);                   // O(n log n), dual-pivot quicksort for primitives
```

- **Interview angle:** "Insert at the front of an array — cost?" (O(n): everything shifts). "Why is `ArrayList.add` amortised O(1)?" (growth by ~1.5×).
- **FlowGrid uses this:** almost never directly, and that is the point — you will pick `List`/`Map` on purpose in Week 2 knowing what they cost.

### 4.5 Big-O in one table

| Code shape | Time | Example |
|---|---|---|
| Index into array / hash lookup | O(1) | `qty[i]`, `map.get(k)` |
| Single loop over n | O(n) | max of an array |
| Sort | O(n log n) | `Arrays.sort` |
| Nested loop over n | O(n²) | naive pair search |
| Halving each step | O(log n) | binary search (Week 6) |

Rule: drop constants and lower-order terms; state *what n is* ("n = number of SKUs").

- **Interview angle:** interviewers ask complexity of *your* code, not definitions. Say it before they ask.
- **FlowGrid uses this:** the allocation scorer in M3 is O(warehouses × order lines); you will say that out loud in the Week 8 deep-dive.

### 4.6 Git: the three areas

```bash
git init && git add . && git commit -m "feat: initial katas"
git status          # working tree vs index vs HEAD
git diff            # unstaged changes
git diff --staged   # what the next commit contains
git log --oneline --graph
git remote add origin git@github.com:<you>/java-foundation-katas.git
git push -u origin main
```

- **Interview angle:** "Explain the difference between `git add`, `git commit` and `git push`." "What does a commit actually point to?" (a tree + parent(s) + metadata; a SHA-1 of that content).
- **FlowGrid uses this:** every milestone is a branch → PR → squash/merge → tag. Habits start now.

## 5. Resources

- Java: [Oracle Java Tutorials — Language Basics](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/index.html); *Effective Java* (3rd ed.) Item 63 (string concatenation), Item 17 (immutability — read for Strings, apply in Week 2).
- JDK: [Adoptium Temurin 21](https://adoptium.net/); [JEP 330 — launch single-file source programs](https://openjdk.org/jeps/330).
- Git: [Pro Git, chapters 1–2](https://git-scm.com/book/en/v2); [GitHub docs — connecting with SSH](https://docs.github.com/en/authentication/connecting-to-github-with-ssh).
- Terminal: [`10-linux/commands.md`](../../10-linux/commands.md); `man` pages for `ls`, `grep`, `find`.
- DSA: [NeetCode roadmap — Arrays & Hashing](https://neetcode.io/roadmap); [`03-dsa/00-big-o.md`](../../03-dsa/00-big-o.md); [`03-dsa/java-dsa-toolkit.md`](../../03-dsa/java-dsa-toolkit.md).

## 6. Exercises + coding assignments

All in one throwaway repo `java-foundation-katas`, one folder per kata, single-file programs
run with `java Kata.java` (no Maven yet). Each kata has a `main` that demonstrates it plus
`assert`-style checks (run with `java -ea Kata.java`). Also do the drills in
[`01-java/exercises.md`](../../01-java/exercises.md) and [`10-linux/exercises.md`](../../10-linux/exercises.md).

| # | Kata | Acceptance criteria |
|---|---|---|
| 1 | **FizzBuzz with a twist**: `fizzbuzz(int n)` returns a `String[]`; divisible-by-3 → `Fizz`, 5 → `Buzz`, both → `FizzBuzz` | Works for n = 0, 1, 15, 100; uses arrow `switch` or a clean `if` chain; O(n) |
| 2 | **Reverse & palindrome**: `reverse(String)`, `isPalindrome(String)` ignoring case and non-letters | `"A man, a plan, a canal: Panama"` → true; empty string → true; no `StringBuilder.reverse()` in `isPalindrome` (two indices) |
| 3 | **Array stats**: `min`, `max`, `sum`, `mean` for `int[]`; `secondLargest` | Handles length 1 and duplicates; `secondLargest` in one pass; throws `IllegalArgumentException` on empty input |
| 4 | **Inventory count table** (domain warm-up): given `String[] skus` with repeats, print a `sku → count` table using **arrays only** (sort first, then count runs) | Output sorted by SKU; O(n log n); no `HashMap` yet (that is Week 2) |
| 5 | **Matrix ops**: transpose a `int[][]`, rotate 90°, sum of each row | Non-square matrices handled for transpose; rotate returns a new array |
| 6 | **Word frequency from a file**: read `words.txt` via `Files.readAllLines`, lowercase, split on non-letters, print top 5 by count with arrays + sort | Correct on a file with punctuation; explain complexity in a comment |
| 7 | **Command-line temperature converter**: `java Temp.java 37 C` prints Fahrenheit; validates args | Bad input prints a usage line and exits with code 2 (`System.exit(2)`) |

### Break it

- In Kata 2, change `s1.equals(s2)` to `s1 == s2` for two strings built at runtime. Predict, run, observe. Explain the string pool in three sentences.
- In Kata 3, call `secondLargest(new int[]{5, 5, 5})`. What *should* it return? Decide, document, test.
- In Kata 6, remove the `toLowerCase()`. Watch `The` and `the` split. That is your first "normalise input at the boundary" lesson — FlowGrid SKU codes will be upper-cased at the boundary for the same reason.

### Debug it

- Introduce an off-by-one in Kata 5's rotate (`n - i` instead of `n - 1 - i`). Run with `-ea`, read the `AssertionError` stack trace, find the line, fix it. Write down the trace-reading steps in your notes; you will reuse them for Spring stack traces in Week 4.
- Run `java -ea Kata.java` with a deliberately missing file in Kata 6. Read the `NoSuchFileException`. Add a clear error message and a non-zero exit code.

## 7. DSA — Big-O + Arrays (6 new)

Pattern guides: [`03-dsa/00-big-o.md`](../../03-dsa/00-big-o.md), [`03-dsa/01-arrays-strings.md`](../../03-dsa/01-arrays-strings.md), toolkit: [`03-dsa/java-dsa-toolkit.md`](../../03-dsa/java-dsa-toolkit.md).

Rules this week: solve in Java in a plain editor first, then submit on LeetCode. Time limit
25 min per Easy. If stuck at 25 min, read the pattern guide (not the solution), try 10 more
minutes, then read the solution and mark `Solved With Solution`. After every solve, say out
loud: pattern, complexity, one edge case.

| Day | Problem | Difficulty | Limit | Focus |
|---|---|---|---|---|
| Mon | [344. Reverse String](https://leetcode.com/problems/reverse-string/) | Easy | 15 min | in-place two-index swap |
| Mon | [27. Remove Element](https://leetcode.com/problems/remove-element/) | Easy | 20 min | write-pointer overwrite |
| Tue | [26. Remove Duplicates from Sorted Array](https://leetcode.com/problems/remove-duplicates-from-sorted-array/) | Easy | 25 min | same write-pointer idea |
| Wed | [283. Move Zeroes](https://leetcode.com/problems/move-zeroes/) | Easy | 25 min | stable in-place |
| Thu | [485. Max Consecutive Ones](https://leetcode.com/problems/max-consecutive-ones/) | Easy | 20 min | running counter |
| Thu | [121. Best Time to Buy and Sell Stock](https://leetcode.com/problems/best-time-to-buy-and-sell-stock/) | Easy | 25 min | running minimum; contrast with O(n²) brute force |

Big-O drills (Mon/Wed, 20 min each): for each kata in §6, write the complexity and justify it. For
problem 121 write *both* the O(n²) and the O(n) solution and time them on n = 100 000 with
`System.nanoTime()`.

**Spaced reviews due this week:** Day-3 reviews of Mon's problems on Thu (344, 27), of Tue's on
Fri (26), of Wed's on Sat (283). Log everything in [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md)
with status and next review date.

## 8. Project work — foundation exercises

There is no product this week. "Project" hours = the katas above, done from a blank file, the same
day you read the concept. Keep the repo tidy anyway:

- [ ] `java-foundation-katas` repo created on GitHub, cloned via SSH, `.gitignore` for `*.class`, `.idea/`, `out/`.
- [ ] One folder per kata; `README.md` at root lists katas with a one-line "what I learned".
- [ ] Every kata runs with `java -ea <File>.java` and its checks pass.
- [ ] At least one "Break it" and one "Debug it" note per kata in the README.

## 9. Git activity

Daily: at least one meaningful commit per kata, [Conventional Commits](https://www.conventionalcommits.org/) from day one:

```bash
git commit -m "feat(kata-02): reverse and palindrome with two indices"
git commit -m "fix(kata-05): off-by-one in rotate90"
git commit -m "docs: add break-it notes for kata 3"
```

Friday: `git log --oneline` should read like a story. Saturday: create a branch `refactor/kata-04`,
change something, merge it back with `git merge` (fast-forward), observe the log. Delete the branch.
Read [`02-git/workflows.md`](../../02-git/workflows.md) sections on branching only as far as
fast-forward; branches and merge conflicts are Week 2.

## 10. Interview preparation

Weeks 1–4 ramp: explain every DSA solution out loud after solving it (30 seconds: problem restated,
approach, complexity, edge case). Record two of them on your phone and listen back — you are
checking for filler words and for whether you *said the complexity unprompted*.

Draft a **60-second "about me"** (Saturday, 45 min). Structure: past roles in one sentence each →
the gap stated plainly and briefly → what you are doing now ("building four production-style
systems in Java/Spring; currently a multi-warehouse fulfillment platform") → what you want. No
fabrication; the gap is a fact, not a confession. Save it to your notes — you will iterate it in
Week 8 with [`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md).

Questions to answer out loud this week:

1. What is the difference between `==` and `equals` for `String`s?
2. Why is `String` immutable in Java and what does that buy us?
3. What is the time complexity of inserting at index 0 of an array of n elements? Why?
4. What are the three areas of a Git repository and which commands move changes between them?
5. What does `git push -u origin main` do exactly?

## 11. Revision work

- Sunday: re-read your own notes for §4.1–4.6, close the file, rewrite the six concepts in
  five lines each from memory. Compare.
- Redo Kata 2 from a blank file in under 15 minutes.
- Update [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): Java syntax, Git core, Linux basics → current level (honest).

## 12. Daily plan

Foundation weeks shift ~10 h from project to learning; the daily shape below keeps DSA daily.

| Day | Blocks |
|---|---|
| **Mon** (8 h) | Learning 3.5: terminal basics, install JDK 21 + IntelliJ, `java Hello.java`, Git init/commit/push, SSH key · Project 2.5: Kata 1 + 2 · DSA 2: Big-O reading, 344, 27 |
| **Tue** (8 h) | Learning 2.5: syntax, control flow, methods · Project 3.5: Kata 3 + 4 · DSA 1.5: 26 · Interview 0.5: explain 344/27 out loud |
| **Wed** (8 h) | Learning 3: arrays, Strings, `StringBuilder`, text blocks · Project 3: Kata 5 · DSA 2: 283 + Big-O drill (time 121 both ways after Thu if needed) |
| **Thu** (8 h) | Learning 2.5: `.gitignore`, `git diff`, `git log`, reading stack traces · Project 3: Kata 6 + Debug-it tasks · DSA 2: 485, 121 · Docs 0.5: kata README notes |
| **Fri** (5 h) | Project 2: Kata 7 + Break-it tasks · DSA 1.5: Day-3 reviews (344, 27, 26) · Retro 1: what slowed you down; write 5 lines in [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md) · Learning 0.5: Linux exercises |
| **Sat** (6 h) | Project 3: branch/merge exercise, polish katas, README · Learning 1: re-read Big-O guide, explain complexity of every kata · Interview 2: 60-second "about me" draft + record 2 DSA explanations |
| **Sun** (2–3 h) | End-of-week test (§13) · Day-3 review of 283 · trackers · plan Week 2 · rest |

## 13. End-of-week test (75 min, timed)

**Part A — DSA (30 min).** Solve from a blank file, no notes:
[1. Two Sum](https://leetcode.com/problems/two-sum/) with the **brute-force O(n²)** approach (the
hash approach is Week 2), then [485. Max Consecutive Ones](https://leetcode.com/problems/max-consecutive-ones/)
again. State complexities.

**Part B — Concepts (20 min).** Write answers, then check.

<details>
<summary>1. What does this print and why? <code>Integer a = 127, b = 127; Integer c = 128, d = 128; System.out.println((a == b) + " " + (c == d));</code></summary>

`true false`. Autoboxing uses `Integer.valueOf`, which caches −128..127, so `a` and `b` are the
same object; 128 is outside the cache so `c` and `d` are distinct objects and `==` compares references.
</details>

<details>
<summary>2. Complexity of building a string of n pieces with <code>+=</code> in a loop versus <code>StringBuilder</code>?</summary>

`+=` is O(n²) total because each concatenation copies the growing string (1 + 2 + … + n characters).
`StringBuilder.append` is amortised O(1) per append (backing array grows geometrically), O(n) total.
</details>

<details>
<summary>3. A commit's SHA changes if you amend its message but not its content. Why?</summary>

The commit object hashes its full content: tree, parent(s), author/committer lines *and* message.
Changing any of them produces a different object and therefore a different SHA.
</details>

<details>
<summary>4. Give an O(n) algorithm for "max profit from one buy and one sell" and explain why it is correct.</summary>

Track the minimum price seen so far and the best `price − min` at each step. Any optimal sale on
day j buys at the minimum price before j, so the running minimum suffices; one pass, O(1) space.
</details>

<details>
<summary>5. <code>int[] a = {1,2,3}; int[] b = a; b[0] = 9;</code> — what is <code>a[0]</code>?</summary>

9. Arrays are objects; `b = a` copies the reference, not the contents. Use `a.clone()` or
`Arrays.copyOf` for a copy.
</details>

**Part C — Practical (20 min).** From a blank file: read a text file of `sku,qty` lines, print
total quantity per SKU using only arrays and sorting, commit it with a Conventional Commit message
and push. Pass = compiles first or second try, correct output, pushed.

**Part D — Explain out loud (5 min).** "Walk me through what happens when I run `java Foo.java`."
(source launcher compiles in memory → bytecode → JVM loads class → `main`.)

## 14. Mastery checklist

- [ ] I can write, run and debug a single-file Java 21 program from the terminal without an IDE.
- [ ] I can explain `==` vs `equals`, `String` immutability, and `StringBuilder`.
- [ ] I can state the Big-O of any kata I wrote this week and justify it.
- [ ] I can solve the 6 array problems again, independently, in the time limits.
- [ ] I use `git status`/`diff`/`log` fluently and write Conventional Commit messages.
- [ ] My SSH key works; `git push` needs no password.
- [ ] I have a 60-second "about me" that I have said out loud twice.

## 15. Expected deliverables

- `java-foundation-katas` repo on GitHub with 7 katas, README, ≥ 12 commits.
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 6 problems with status + review dates; Day-3 reviews logged.
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md): Week 1 entry (hours per block, test score, 3 lessons).
- [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): Java, Git, Linux rows updated.
- [`trackers/project-tracker.md`](../../trackers/project-tracker.md): "Foundation" row started (katas done / total).

## 16. If you're behind / stretch

**Behind:** do Katas 1–4 and 6 only; keep all 6 DSA problems (cut kata time, not DSA). Skip the
matrix kata. Never skip the Sunday test — it is the measurement.

**Stretch:** Kata 6 with a `record WordCount(String word, int count)` and `Arrays.sort` with a
comparator (a preview of Week 2/3); read [`01-java/05-exceptions-io.md`](../../01-java/05-exceptions-io.md)
first half; solve [189. Rotate Array](https://leetcode.com/problems/rotate-array/) (the three-reversal trick).
