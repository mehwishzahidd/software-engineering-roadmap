# Week 1 — Write Python and Java from a blank file; version it

[Roadmap](../../ROADMAP.md) · [Week 2 →](../week-02/)

**Phase 0 · Foundation** (exercises only — nothing this week is portfolio material)

| Block | Hours | What it means this week |
|---|---:|---|
| Project (foundation coding) | 15 | Python + Java katas from a blank file, committed to a throwaway repo |
| Learning | 15 | **Python core for interviews**; terminal & Linux basics; JDK/IDE, Java syntax, control flow, methods, arrays, Strings; Git core |
| DSA | 7 | Big-O + Arrays — **6 new problems in Python** + Day-3 reviews + 1 Java rep |
| Interview / review | 3 | Explain every DSA solution out loud; draft a 60-second "about me" |

---

## 1. Main objective

Two languages, two jobs, from day one:

- **Python (Track A — coding interviews).** By Sunday you solve Easy array problems in Python from a
  blank file, fluently using slicing, `enumerate`, `zip`, comprehensions and `sorted(key=...)`,
  and you state Big-O unprompted. ~90% of all algorithm practice for 26 weeks happens in Python.
- **Java (Track B — backend / projects / résumé).** By Sunday you can compile and run a Java 21
  single-file program from the terminal, manipulate arrays and `String`s correctly, and know what
  `==` vs `equals` costs you. Java stays deep: Week 2 adds OOP, JUnit and Maven; Week 3 the first
  Spring Boot endpoint; Week 4 starts [FlowGrid](../../18-projects/flowgrid/README.md).

Plus the habits everything later assumes: terminal, Git with Conventional Commits, Big-O on
every piece of code, explaining out loud. You have written Java professionally before; this week
removes rust on Java 21 fast and makes Python your *interview* language deliberately rather than
by accident.

## 2. Prerequisites

- A laptop with ≥ 8 GB RAM, admin rights, a GitHub account.
- No prior checkpoint. If your Java is genuinely fresh, spend Monday and Tuesday on
  [`01-java/01-syntax-basics.md`](../../01-java/01-syntax-basics.md) and skip nothing.
- If you have never written Python: read [`19-python/python-for-java-devs.md`](../../19-python/python-for-java-devs.md) before Monday's learning block.

## 3. Learning topics

| Topic | Subtopics | Folder file |
|---|---|---|
| **Python core for interviews** | `python3.12`, REPL, `int`/`float`/`str`/`bool`/`None`, `list`/`tuple`/`set`/`dict`, slicing, comprehensions, `enumerate`/`zip`/`range`, `sorted(key=lambda)`, f-strings, functions & default args, truthiness, `is` vs `==` | [`19-python/01-python-core.md`](../../19-python/01-python-core.md), [`19-python/python-for-java-devs.md`](../../19-python/python-for-java-devs.md), [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) |
| Terminal & Linux basics | shell, `pwd`/`ls`/`cd`, paths, `cat`/`less`/`grep`, pipes, `chmod`, `PATH`, env vars, `python3 -m venv` | [`10-linux/commands.md`](../../10-linux/commands.md), [`10-linux/README.md`](../../10-linux/README.md) |
| JDK 21 + IDE | install JDK 21 (Temurin), `java -version`, `javac`, single-file `java Foo.java`, IntelliJ IDEA (Community is enough), `JAVA_HOME`; VS Code or PyCharm CE for Python | [`01-java/README.md`](../../01-java/README.md) |
| Java syntax & control flow | primitives vs references, `var`, `if`/`switch` (arrow form), loops, `break`/`continue`, methods, overloading, `static` | [`01-java/01-syntax-basics.md`](../../01-java/01-syntax-basics.md) |
| Java arrays & Strings | 1-D/2-D arrays, `Arrays.*`, `String` immutability, `StringBuilder`, `charAt`, `equals` vs `==`, text blocks | [`01-java/01-syntax-basics.md`](../../01-java/01-syntax-basics.md) |
| Git core | `init`/`clone`/`status`/`add`/`commit`/`log`/`diff`, `.gitignore`, remotes, `push`/`pull`, SSH keys | [`02-git/README.md`](../../02-git/README.md), [`02-git/exercises.md`](../../02-git/exercises.md) |
| Big-O | time/space, dominant term, common classes, amortised cost (informal); Python built-in costs | [`03-dsa/00-big-o.md`](../../03-dsa/00-big-o.md) |

## 4. Concepts to learn

### 4.1 Python: lists, slicing and the costs you must know

```python
nums = [3, 1, 4, 1, 5]
nums[1:3]          # [1, 4]   — new list, O(k)
nums[::-1]         # reversed copy, O(n); nums.reverse() is in place
nums.append(9)     # amortised O(1);  nums.insert(0, x) and nums.pop(0) are O(n)
x in nums          # O(n) for a list;  O(1) average for set/dict
sorted(nums, key=lambda v: -v)   # O(n log n), stable, returns a new list
```

- **Interview angle:** "What is the cost of `list.pop(0)`?" (O(n) — use `collections.deque`, Week 2). "Is `sorted` stable?" (yes — Timsort).
- **FlowGrid uses this:** the Python order generator (Week 7) builds order mixes with comprehensions and `random.choices`; you will size loops knowing which operations are O(n).

### 4.2 Python: `enumerate`, `zip`, comprehensions, `sorted` with keys

```python
skus = ["BOLT-M6", "NUT-M6", "WASHER"]
qty  = [5, 0, 12]
low = [s for s, q in zip(skus, qty) if q < 3]              # ['NUT-M6']
by_qty = sorted(zip(skus, qty), key=lambda p: (p[1], p[0]))  # tuples compare lexicographically
for i, s in enumerate(skus, start=1): print(f"{i}. {s}")
```

- **Interview angle:** interviewers watch whether you reach for `enumerate` instead of `range(len(...))`; it signals fluency.
- **FlowGrid uses this:** the low-stock report in Week 7's simulation harness is literally this comprehension against API output.

### 4.3 Python: `is` vs `==`, immutability of `str`, aliasing

```python
a = [1, 2]; b = a; b.append(3); print(a)     # [1, 2, 3] — same object
s = "grid"; t = s + "!"                       # str is immutable: t is a new object
print(a is b, [1, 2, 3] == a, [1, 2, 3] is a) # True True False
```

- **Interview angle:** "Why did my helper list change?" — aliasing; deep vs shallow copy is Week 2's pitfalls file.
- **FlowGrid uses this:** the same bug shape appears in Java (`b = a` on arrays) — see 4.6; you will see both.

### 4.4 Java: primitives, references and `==`

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

### 4.5 Java: Strings are immutable; build with `StringBuilder`

```java
String csv = "";
for (int i = 0; i < n; i++) csv += i + ",";     // O(n²): each += copies the whole string

StringBuilder sb = new StringBuilder();
for (int i = 0; i < n; i++) sb.append(i).append(',');
String out = sb.toString();                      // O(n)
```

Python equivalent: `",".join(str(i) for i in range(n))` — same reason (`+=` on `str` in a loop is quadratic in principle).

- **Interview angle:** "Complexity of concatenating in a loop, and how do you fix it?" — in either language.
- **FlowGrid uses this:** building CSV report exports for S3 (M5).

### 4.6 Java: modern `switch`, `var`, arrays

```java
static String zone(char aisle) {
    return switch (aisle) {
        case 'A', 'B' -> "north";
        case 'C'      -> "south";
        default       -> throw new IllegalArgumentException("unknown aisle " + aisle);
    };
}
int[] qty = new int[5];                    // zero-initialised; qty[2] = 7;
int[] copy = Arrays.copyOf(qty, qty.length);   // int[] b = qty; would alias, not copy
Arrays.sort(qty);                          // O(n log n)
```

- **Interview angle:** "What changed in `switch` recently?" (arrow form, expression, no fall-through). "Insert at the front of an array — cost?" (O(n)).
- **FlowGrid uses this:** mapping inventory states (`on_hand`, `available`, `reserved`…) in the reservation state machine (M2).

### 4.7 Big-O in one table (both languages)

| Code shape | Time | Python | Java |
|---|---|---|---|
| Index / hash lookup | O(1) | `nums[i]`, `d[k]`, `k in s` | `arr[i]`, `map.get(k)` |
| Membership in a sequence | O(n) | `x in list` | `list.contains(x)` |
| Single loop | O(n) | `max(nums)` | loop for max |
| Sort | O(n log n) | `sorted`, `.sort()` | `Arrays.sort` |
| Nested loop | O(n²) | naive pair search | naive pair search |
| Halving each step | O(log n) | `bisect` (Week 6) | binary search |

Rule: drop constants and lower-order terms; say *what n is* ("n = number of SKUs").

- **Interview angle:** interviewers ask the complexity of *your* code. Say it before they ask.
- **FlowGrid uses this:** the allocation scorer in M3 is O(warehouses × order lines); you will say that out loud in the Week 8 deep-dive.

### 4.8 Git: the three areas

```bash
git init && git add . && git commit -m "feat: initial katas"
git status          # working tree vs index vs HEAD
git diff            # unstaged changes
git diff --staged   # what the next commit contains
git log --oneline --graph
git remote add origin git@github.com:<you>/foundation-katas.git
git push -u origin main
```

- **Interview angle:** "Explain `git add` vs `commit` vs `push`." "What does a commit point to?" (tree + parent(s) + metadata, content-addressed by SHA).
- **FlowGrid uses this:** every milestone is a branch → PR → merge → tag. Habits start now.

## 5. Resources

- Python: [The Python Tutorial (docs.python.org)](https://docs.python.org/3/tutorial/) chapters 3–5; [Built-in Types](https://docs.python.org/3/library/stdtypes.html); [TimeComplexity wiki](https://wiki.python.org/moin/TimeComplexity); [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md).
- Java: [Oracle Java Tutorials — Language Basics](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/index.html); *Effective Java* (3rd ed.) Item 63 (string concatenation), Item 17 (immutability).
- JDK: [Adoptium Temurin 21](https://adoptium.net/); [JEP 330 — single-file source programs](https://openjdk.org/jeps/330).
- Git: [Pro Git, chapters 1–2](https://git-scm.com/book/en/v2); [GitHub docs — connecting with SSH](https://docs.github.com/en/authentication/connecting-to-github-with-ssh).
- Terminal: [`10-linux/commands.md`](../../10-linux/commands.md); `man ls`, `man grep`.
- DSA: [NeetCode roadmap — Arrays & Hashing](https://neetcode.io/roadmap); [`03-dsa/00-big-o.md`](../../03-dsa/00-big-o.md); [`03-dsa/01-arrays-strings.md`](../../03-dsa/01-arrays-strings.md); [`03-dsa/java-dsa-toolkit.md`](../../03-dsa/java-dsa-toolkit.md) (for the Java rep).

## 6. Exercises + coding assignments

One throwaway repo `foundation-katas` with `python/` and `java/` folders. Python katas are plain
scripts with `assert` checks (`python3 kata.py`); Java katas are single-file programs run with
`java -ea Kata.java`. Also do [`19-python/exercises.md`](../../19-python/exercises.md) (core
section), [`01-java/exercises.md`](../../01-java/exercises.md) (syntax section) and
[`10-linux/exercises.md`](../../10-linux/exercises.md).

| # | Kata | Language | Acceptance criteria |
|---|---|---|---|
| 1 | **FizzBuzz** returning a list/array | Python **and** Java | n = 0, 1, 15, 100; Python uses a comprehension or clean loop; Java uses arrow `switch` or clean `if` chain; O(n) |
| 2 | **Reverse & palindrome** ignoring case and non-letters | Python **and** Java | `"A man, a plan, a canal: Panama"` → true; empty → true; two-index solution (no `[::-1]`/`reverse()` inside `is_palindrome`) |
| 3 | **Array stats**: `min`, `max`, `sum`, `mean`, `second_largest` | Java | length 1 and duplicates handled; `secondLargest` in one pass; `IllegalArgumentException` on empty |
| 4 | **Inventory count table** (domain warm-up): repeated SKUs → `sku → count`, sorted by SKU | Python (dict + `sorted`) **and** Java (arrays only: sort, count runs) | Same output from both; note the complexity difference; Java `HashMap` version comes in Week 2 |
| 5 | **Matrix ops**: transpose, rotate 90°, row sums | Python (nested comprehensions / `zip(*m)`) **and** Java (`int[][]`) | non-square transpose works; rotate returns a new structure |
| 6 | **Word frequency** from `words.txt`, top 5 | Python (`dict`, `sorted(key=...)`) | punctuation handled, lowercase; complexity noted in a comment |
| 7 | **CLI temperature converter** `python3 temp.py 37 C` / `java Temp.java 37 C` | Python **and** Java | bad input prints usage and exits with code 2 (`sys.exit(2)` / `System.exit(2)`) |

### Break it

- Kata 2 (Java): change `s1.equals(s2)` to `s1 == s2` for runtime-built strings. Predict, run, explain the string pool in three sentences. Then in Python compare `"gr" + "id" is "grid"` — and explain why the answer is "don't rely on it".
- Kata 4 (Python): build the count with `counts[sku] += 1` on a plain `dict` without initialising. Read the `KeyError`. Fix with `dict.get(sku, 0) + 1` now; `Counter` arrives in Week 2.
- Kata 3 (Java): `secondLargest(new int[]{5, 5, 5})` — what *should* it return? Decide, document, test.
- Kata 6: remove `.lower()`; watch `The` and `the` split. "Normalise at the boundary" — FlowGrid upper-cases SKU codes at the boundary for the same reason.

### Debug it

- Plant an off-by-one in Kata 5's rotate (`n - i` instead of `n - 1 - i`) in both languages. Read the Python `IndexError` traceback bottom-up and the Java `ArrayIndexOutOfBoundsException` stack trace top-down. Write the two trace-reading recipes in your notes.
- Kata 6 with a missing file: read Python's `FileNotFoundError` and Java's `NoSuchFileException`; add a clear message and a non-zero exit code in both.

## 7. DSA — Big-O + Arrays (6 new, in Python)

Pattern guides: [`03-dsa/00-big-o.md`](../../03-dsa/00-big-o.md), [`03-dsa/01-arrays-strings.md`](../../03-dsa/01-arrays-strings.md); templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md); Java rep reference: [`03-dsa/java-dsa-toolkit.md`](../../03-dsa/java-dsa-toolkit.md).

Rules: solve **in Python** in a plain editor first, then submit on LeetCode. Time limit 25 min per
Easy. Stuck at 25 min → read the pattern guide (not the solution), try 10 more minutes, then read
the solution and mark `Solved With Solution`. After every solve, say out loud: pattern,
complexity, one edge case.

| Day | Problem | Difficulty | Limit | Focus |
|---|---|---|---|---|
| Mon | [344. Reverse String](https://leetcode.com/problems/reverse-string/) | Easy | 15 min | in-place two-index swap (`s[i], s[j] = s[j], s[i]`) |
| Mon | [27. Remove Element](https://leetcode.com/problems/remove-element/) | Easy | 20 min | write-pointer overwrite |
| Tue | [26. Remove Duplicates from Sorted Array](https://leetcode.com/problems/remove-duplicates-from-sorted-array/) | Easy | 25 min | same write-pointer idea |
| Wed | [283. Move Zeroes](https://leetcode.com/problems/move-zeroes/) | Easy | 25 min | stable in-place |
| Thu | [485. Max Consecutive Ones](https://leetcode.com/problems/max-consecutive-ones/) | Easy | 20 min | running counter |
| Thu | [121. Best Time to Buy and Sell Stock](https://leetcode.com/problems/best-time-to-buy-and-sell-stock/) | Easy | 25 min | running minimum; contrast with O(n²) brute force |

**Java rep (Sat, 30 min):** re-do **283. Move Zeroes** in Java (`int[]`, in place). Log it in the
tracker's "Java rep" column — it does not count as new.

Big-O drills (Mon/Wed, 20 min each): for every kata in §6, write complexity and justify. For 121,
write both the O(n²) and O(n) Python solutions and time them on n = 100 000 with `time.perf_counter()`.

**Spaced reviews due this week:** Day-3 reviews of Mon's problems on Thu (Reverse String, Remove
Element), Tue's on Fri (Remove Duplicates from Sorted Array), Wed's on Sat (Move Zeroes). Log
everything in [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md) with status, language and next review date.

## 8. Project work — foundation exercises

No product this week. "Project" hours = the katas above, from a blank file, the same day you read
the concept. Keep the repo tidy anyway:

- [ ] `foundation-katas` repo on GitHub, cloned via SSH; `.gitignore` for `*.class`, `.idea/`, `out/`, `__pycache__/`, `.venv/`.
- [ ] `python/` and `java/` folders, one file per kata; root `README.md` lists katas with a one-line "what I learned".
- [ ] Every Python kata passes its `assert`s; every Java kata runs with `java -ea`.
- [ ] At least one "Break it" and one "Debug it" note per kata in the README.

## 9. Git activity

At least one meaningful commit per kata, [Conventional Commits](https://www.conventionalcommits.org/) from day one:

```bash
git commit -m "feat(kata-02): palindrome with two indices (py + java)"
git commit -m "fix(kata-05): off-by-one in rotate90"
git commit -m "docs: add break-it notes for kata 4"
```

Friday: `git log --oneline` should read like a story. Saturday: branch `refactor/kata-04`, change
something, `git merge` (fast-forward), observe the log, delete the branch. Read
[`02-git/workflows.md`](../../02-git/workflows.md) only as far as fast-forward; conflicts are Week 2.

## 10. Interview preparation

Weeks 1–4 ramp: explain every DSA solution out loud after solving it (30 seconds: problem
restated, approach, complexity, edge case) — in the language you solved it in (Python). Record two
and listen back: filler words, and did you *say the complexity unprompted*?

Draft a **60-second "about me"** (Saturday, 45 min): past roles in one sentence each → the gap
stated plainly → what you are doing now ("building four production-style systems in Java/Spring
with Python tooling; currently a multi-warehouse fulfillment platform") → what you want. No
fabrication. Save it; you iterate it in Week 8 with [`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md).

Questions to answer out loud (Track A in Python terms, Track B in Java terms):

1. What is the cost of `x in some_list` vs `x in some_set`, and why?
2. Why is `list.pop(0)` O(n)? What would you use instead?
3. Java: `==` vs `equals` for `String`s; why is `String` immutable?
4. Time complexity of inserting at index 0 of an array of n elements? Why?
5. The three areas of a Git repository and the commands that move changes between them.

## 11. Revision work

- Sunday: re-read your notes for §4.1–4.8, close the file, rewrite each concept in five lines from memory. Compare.
- Redo Kata 2 in Python from blank in under 10 minutes, then in Java in under 15.
- Update [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): Python core, Java syntax, Git core, Linux basics → honest current level.

## 12. Daily plan

Foundation weeks shift ~10 h from project to learning; DSA is daily.

| Day | Blocks |
|---|---|
| **Mon** (8 h) | Learning 3.5: terminal basics; install Python 3.12 + JDK 21 + IDEs; `python3 hello.py`, `java Hello.java`; Git init/commit/push, SSH key; Python core part 1 (types, lists, slicing) · Project 2.5: Kata 1 + 2 (Python first, then Java) · DSA 2: Big-O reading; 344, 27 |
| **Tue** (8 h) | Learning 2.5: Python core part 2 (`enumerate`/`zip`/comprehensions/`sorted`); Java syntax + control flow · Project 3.5: Kata 3 + 4 · DSA 1.5: 26 · Interview 0.5: explain 344/27 out loud |
| **Wed** (8 h) | Learning 3: Java arrays, Strings, `StringBuilder`; Python `is` vs `==`, aliasing · Project 3: Kata 5 (both) · DSA 2: 283 + Big-O drill on katas |
| **Thu** (8 h) | Learning 2.5: `.gitignore`, `git diff`/`log`, reading tracebacks and stack traces · Project 3: Kata 6 + Debug-it tasks · DSA 2: 485, 121 (+ timing drill) + Day-3 (344, 27) · Docs 0.5: kata README notes |
| **Fri** (5 h) | Project 2: Kata 7 (both) + Break-it tasks · DSA 1.5: Day-3 (26) · Retro 1: 5 lines in [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md) · Learning 0.5: Linux exercises |
| **Sat** (6 h) | Project 2.5: branch/merge exercise, polish, README · DSA 1.5: Day-3 (283) + **Java rep** (283 in Java) · Interview 2: "about me" draft + record 2 explanations |
| **Sun** (2–3 h) | End-of-week test (§13) · trackers · plan Week 2 · rest |

## 13. End-of-week test (75 min, timed)

**Part A — DSA in Python (30 min).** From a blank file, no notes:
[1. Two Sum](https://leetcode.com/problems/two-sum/) with the **brute-force O(n²)** approach (the
dict approach is Week 2), then [485. Max Consecutive Ones](https://leetcode.com/problems/max-consecutive-ones/) again. State complexities.

**Part B — Concepts (20 min).**

<details>
<summary>1. Python: <code>a = [[0] * 3] * 3; a[0][0] = 1</code> — what is <code>a</code>?</summary>

`[[1, 0, 0], [1, 0, 0], [1, 0, 0]]`. The outer `* 3` repeats the *same* inner list object three
times (aliasing). Use `[[0] * 3 for _ in range(3)]`.
</details>

<details>
<summary>2. Java: <code>Integer a = 127, b = 127; Integer c = 128, d = 128; System.out.println((a == b) + " " + (c == d));</code></summary>

`true false`. Autoboxing uses `Integer.valueOf`, which caches −128..127; 128 is outside the cache,
so `c` and `d` are distinct objects and `==` compares references.
</details>

<details>
<summary>3. Complexity of building a string of n pieces with <code>+=</code> in a loop versus <code>StringBuilder</code> / <code>"".join</code>?</summary>

`+=` is O(n²) in principle (each concatenation copies the growing string). `StringBuilder.append`
and `"".join(parts)` are O(n) total.
</details>

<details>
<summary>4. A commit's SHA changes if you amend only its message. Why?</summary>

The commit object hashes tree, parent(s), author/committer lines *and* message. Any change → new object → new SHA.
</details>

<details>
<summary>5. Give an O(n) algorithm for "max profit from one buy and one sell" and say why it is correct.</summary>

Track the minimum price so far and the best `price − min` at each step. Any optimal sale on day j
buys at the minimum before j, so the running minimum suffices; one pass, O(1) space.
</details>

<details>
<summary>6. Python: what does <code>sorted(pairs, key=lambda p: (p[1], p[0]))</code> do, and what is its complexity?</summary>

Sorts by second element, ties broken by first (tuples compare lexicographically); O(n log n), stable, returns a new list.
</details>

**Part C — Practical (20 min).** Python: read `sku,qty` lines from a file, print total quantity per
SKU sorted by SKU, commit with a Conventional Commit message, push. Pass = correct output, pushed,
within time.

**Part D — Explain out loud (5 min).** "Walk me through what happens when I run `java Foo.java`"
and "what does `python3 foo.py` actually run?" (source → bytecode → interpreter loop).

## 14. Mastery checklist

- [ ] I solve Easy array problems in Python without looking up syntax for slicing, `enumerate`, `zip`, `sorted(key=...)`.
- [ ] I can write, run and debug a single-file Java 21 program from the terminal.
- [ ] I can explain `is` vs `==` (Python) and `==` vs `equals` (Java), and string immutability in both.
- [ ] I can state the Big-O of any kata I wrote and of Python's list/dict/set operations.
- [ ] I can re-solve the 6 array problems independently within limits, and I did one Java rep.
- [ ] I use `git status`/`diff`/`log` fluently, write Conventional Commits, and my SSH key works.
- [ ] I have a 60-second "about me" that I have said out loud twice.

## 15. Expected deliverables

- `foundation-katas` repo: 7 katas (Python + Java where listed), README, ≥ 12 commits.
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 6 problems (Python) with status + review dates, 1 Java rep, Day-3 reviews logged.
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md): Week 1 entry (hours per block, test score, 3 lessons).
- [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): Python, Java, Git, Linux rows updated.
- [`trackers/project-tracker.md`](../../trackers/project-tracker.md): "Foundation" row started (katas done / total).

## 16. If you're behind / stretch

**Behind:** do Katas 1, 2, 4, 6 (Python) and 1, 2, 3 (Java); keep all 6 DSA problems and the
Sunday test — cut kata time, never DSA. Skip the matrix kata.

**Stretch:** Kata 6 with a `dataclass WordCount` (preview of Week 2) and, in Java, a
`record WordCount(String word, int count)` sorted with a comparator; solve
[189. Rotate Array](https://leetcode.com/problems/rotate-array/) in Python (three-reversal trick);
read the first half of [`01-java/05-exceptions-io.md`](../../01-java/05-exceptions-io.md).
