# OA Prep — Online Assessments

Online assessments (OAs) are the first filter for most internship, new-grad and junior SDE
roles. They're timed, auto-graded, and unforgiving of slow starts and untested edge cases.
This file covers the platforms, the tactics, and **eight fully specified simulations
(OA-1 … OA-8)** run in Weeks 17–24 per [`ROADMAP.md`](./ROADMAP.md) §10.

**Languages (ROADMAP §10, two tracks):**
- **Algorithm problems → Python** (Track A). Templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md).
- **Existing-codebase / failing-test / small-feature tasks → Java** (Track B skill inside an OA), using
  [`21-debugging-code-reading/exercises/buggy-library/`](./21-debugging-code-reading/exercises/buggy-library/).

Related: [`16-interview-prep/coding-interview-method.md`](./16-interview-prep/coding-interview-method.md) (the method you still use, silently) ·
[`21-debugging-code-reading/`](./21-debugging-code-reading/) (debug/codebase tasks) ·
[`19-python/03-pitfalls-and-complexity.md`](./19-python/03-pitfalls-and-complexity.md) (what silently costs you tests) ·
[`trackers/interview-tracker.md`](./trackers/interview-tracker.md) (log every sim).

---

## 1. Platform types

Formats change every hiring season. **The invite email is the source of truth** — read it for sections, durations, language options and whether you can pause between sections. The descriptions below are the commonly reported shapes, not guarantees.

| Platform / style | Typical shape | Scoring | What matters most |
|---|---|---|---|
| **HackerRank** | 1–3 coding problems, 60–120 min. Either *function completion* ("complete the function `foo`") or full *stdin/stdout* programs. Sometimes SQL, MCQ, or a debugging question. | Per hidden test case passed. Partial credit is normal. | Read the input format exactly. Pass sample tests, then think about hidden edge cases and large inputs. Python is available on virtually every HackerRank test. |
| **CodeSignal** (e.g. General Coding Assessment) | Has commonly used 4 problems in ~70 min, rising difficulty: Q1 trivial, Q2 simple implementation, Q3 array/string manipulation, Q4 harder algorithm/data structure. | Composite score from tests passed + time. Some companies reuse one score across applications. | Speed on Q1–Q2 (≤ 10 min combined), careful implementation on Q3, partial credit on Q4. Python's slicing/comprehensions shine on Q1–Q3. |
| **Amazon SDE / intern OA** | Commonly reported: **2 coding problems** (~70–90 min total) plus a **Work Style Assessment** (survey statements mapped to Leadership Principles) and sometimes a **work simulation** (scenario-based SDE decisions: prioritization, code-review choices, customer impact). Some versions have included short debugging questions. | Coding by hidden tests (+ possibly code quality); the other sections are evaluated separately. | Coding: correct, efficient Python that passes hidden tests within the time limit. Work style: answer **honestly and consistently** — it's not a trick test, but know the LPs ([`16-interview-prep/behavioral.md`](./16-interview-prep/behavioral.md)). |
| **Project / codebase style** (CodeSignal project-style tasks, take-home-in-browser) | An existing small repo: fix failing tests, implement a feature, sometimes multi-level (each level unlocks the next). Language is the repo's — often Java or Python. | Tests passed per level. | Reading unfamiliar code fast — exactly what [`21-debugging-code-reading/`](./21-debugging-code-reading/) trains. |
| **Debugging MCQ / fix-the-function** | Short snippets with a bug (off-by-one, wrong operator, wrong condition); fix one or two lines. Any language. | All-or-nothing per snippet. | The "usual suspects" table in [`21-debugging-code-reading/method.md`](./21-debugging-code-reading/method.md). |

### Work-style / work-simulation sections (awareness)

- Answer as the engineer you actually are and want to be. Don't try to game it; inconsistent answers across similar statements are easy to detect.
- Know what the company values. For Amazon, read the 16 Leadership Principles on amazon.jobs and map your real stories (see [`16-interview-prep/behavioral.md`](./16-interview-prep/behavioral.md)).
- Work-simulation scenarios typically reward: customer impact first, raising risks early, data over opinion, owning follow-through, not shipping untested code under pressure, and asking for help at the right time.
- Practise by reading each scenario prompt aloud and stating *why* you'd pick an option in one sentence referencing a principle.

---

## 2. Before the OA

- [ ] Language set to **Python 3** (check the version: 3.8+ gives walrus and `math.comb`; 3.9+ gives `list[int]` hints and `dict | dict`; 3.10+ gives `match`). Avoid features newer than the platform's interpreter.
- [ ] Take the platform's **practice/sample test** to learn the editor, how "Run" vs "Submit" work, whether you have a custom-input box, and whether it's function-completion or stdin.
- [ ] Quiet room, charger, stable internet, water. Close notifications.
- [ ] Scratch paper and pen for examples and edge cases.
- [ ] Know the rules: many OAs are proctored or forbid external help/AI tools. Follow them exactly — this repository only prepares you to do it yourself.
- [ ] Deadline in your calendar with a 48-hour buffer. Don't start at 11 pm tired.
- [ ] If the invite mentions a codebase/debugging section, run `mvn -q test` on buggy-library the day before to warm up your Java reading reflexes.

---

## 3. Time management

### Budget per problem

| Session | Problem 1 | Problem 2 | Buffer |
|---|---|---|---|
| 70 min, 2 problems | 30 min | 30 min | 10 min: re-read constraints, add edge cases, final submit |
| 90 min, 2 problems | 35 min | 45 min | 10 min |
| 70 min, 4 problems (CodeSignal-style) | Q1 5 · Q2 8 · Q3 20 | Q4 30 | 7 min |

### Per-problem clock (30-min problem)

| Minute | Activity |
|---:|---|
| 0–4 | Read **twice**. Circle constraints (n up to? values negative? duplicates?). Work sample by hand. |
| 4–7 | Choose approach from constraints (table below). Write 3–5 edge cases on paper. |
| 7–20 | Code. Helper functions for anything > 5 lines. |
| 20–25 | Run samples. Then your own edge cases in custom input. |
| 25–30 | Fix, submit. If partial, decide: improve or move on (§5). |

### Constraints → target complexity (Python)

Python executes roughly 10⁷ simple operations per second on judge hardware (C++ ≈ 10⁸–10⁹). Budget accordingly.

| n ≤ | Acceptable complexity | Typical approach |
|---|---|---|
| 10–12 | O(n!) / O(2ⁿ · n) | permutations, backtracking |
| 18–20 | O(2ⁿ) | subsets, bitmask |
| 300–500 | O(n³) | triple loop, Floyd–Warshall |
| 3 000–5 000 | O(n²) | double loop, 2-D DP |
| 10⁵ – 10⁶ | O(n log n) or O(n) | sort, heap, two pointers, sliding window, hashing, BFS/DFS, binary search |
| 10⁹ + | O(log n) or O(1) | binary search on the answer, math |

**Rule:** if the brute force is clearly too slow for n, don't code it "to get partial credit" unless you have no better idea within 8 minutes. If you do, code it cleanly so you can swap the core later.

### Stuck rules

- Stuck **8 minutes** with no approach → code the brute force for partial credit.
- A problem at **1.5× its budget** → submit what passes, move to the next. Come back if time remains.
- **Never** leave a problem blank. A brute force passing 40 % of tests beats nothing.

---

## 4. Reading input in Python

Function-completion problems give you parsed arguments. **Stdin/stdout problems** make you parse. Read the "Input Format" section like a contract.

### `input()` — simple, fine for small input

OK up to ~10⁵ tokens. Each call strips the trailing newline. Beware: `input()` raises `EOFError` at end of input.

```python
n = int(input())
a = list(map(int, input().split()))
name = input().strip()
print(sum(a), name)
```

### `sys.stdin` — fast, the safe default for large input

Use when input can be ~10⁶ numbers. Reading everything at once is 5–10× faster than `input()` in a loop.

```python
import sys

def main() -> None:
    data = sys.stdin.buffer.read().split()          # all tokens as bytes; int(b"42") works
    pos = 0
    n = int(data[pos]); pos += 1
    a = [int(x) for x in data[pos:pos + n]]; pos += n   # values may wrap lines — tokens don't care
    out = []
    for x in a:
        out.append(str(x * 2))
    sys.stdout.write("\n".join(out) + "\n")         # one write — print() in a loop is slow

main()
```

Line-oriented variant (when lines matter, e.g. strings with spaces or a grid):

```python
import sys
lines = sys.stdin.read().splitlines()
r, c = map(int, lines[0].split())
grid = [list(lines[i + 1]) for i in range(r)]
```

Token iterator (clean for mixed-type input):

```python
import sys
it = iter(sys.stdin.read().split())
n = int(next(it)); q = int(next(it))
queries = [(next(it), int(next(it))) for _ in range(q)]
```

### Input-format patterns

| Format | Parse with |
|---|---|
| `n` then `n` numbers on one line | `n = int(next(it))`, then `[int(next(it)) for _ in range(n)]` |
| `n` then `n` lines of `a b` | `pairs = [tuple(map(int, next_line().split())) for _ in range(n)]` |
| Unknown number of lines until EOF | `for line in sys.stdin:` (stop on blank with `if not line.strip(): break`) |
| Grid of chars | `grid = [list(line) for line in lines[1:1 + r]]` |
| Comma-separated | `[s.strip() for s in line.split(",")]` |
| `q` queries of different types | `match parts[0]:` (3.10+) or `if/elif` on `parts[0]` |
| Very deep recursion needed (DFS on 10⁵ nodes) | `sys.setrecursionlimit(1 << 25)` at the top **and** prefer iterative; on some judges also run inside a `threading.Thread` with a large stack |

### Output

- Match the expected format **exactly**: spaces vs newlines, decimals as specified (`f"{x:.2f}"`), `"YES"` vs `"Yes"`.
- Build a list of strings and write once for large output; `print(*row)` is fine for small.
- Remove debug prints before submit (stray stdout fails tests). Debug to `sys.stderr` — judges ignore it.
- `print` adds a newline; trailing whitespace is usually tolerated, trailing extra lines sometimes not.

---

## 5. Hidden tests and partial credit

Sample tests prove almost nothing. Hidden tests check **edge cases** and **scale**.

### What hidden tests usually include

1. Minimum size (n = 0 or 1, empty string).
2. All equal elements; all distinct.
3. Negative numbers, zero, very large values (Python ints don't overflow — but the *answer format* may expect modulo 10⁹+7).
4. Already sorted, reverse sorted.
5. Maximum n → timeouts (TLE) if your complexity — or your constant factor — is wrong.
6. Recursion depth at maximum n (a linked list / path of 10⁵ nodes).
7. Disconnected graphs, cycles, self-loops.
8. Duplicates where you assumed uniqueness.

### Partial-credit strategy

| Situation | Action |
|---|---|
| Passing samples, some hidden fail with **Wrong Answer** | Walk the edge-case checklist below, one by one. Check the modulo requirement, negative `//` and `%`, and off-by-one `range` bounds first. |
| Hidden fail with **Time Limit Exceeded** | Complexity or constant factor too high for max n. Look for `in list`, `list.pop(0)`, string `+=`, slicing in a loop, `input()` for huge input, or recomputing `len()`/`sum()` inside loops. Then: dict/set, sort + two pointers, prefix sums, heap. |
| **Runtime Error** | `IndexError` on empty input, `RecursionError`, `KeyError` on a missing dict key (use `.get`/`defaultdict`), `ValueError` from `int()` on whitespace, `TypeError` comparing `None`. |
| **Memory Limit Exceeded** | 2-D DP of 10⁴ × 10⁴ ints; roll the DP to two rows; use `array`/generators. |
| No idea for the optimal solution | Submit brute force now (banks points), then optimize in the remaining time. |
| Two problems, one solved | Spend remaining time on the unsolved one's partial credit before polishing the solved one. |

### Edge-case checklist (run through before every submit)

- [ ] Empty input / n = 0 / n = 1
- [ ] All elements equal
- [ ] Negative numbers and zero; `-7 // 2` is `-4`, `-7 % 3` is `2`
- [ ] Modulo 10⁹+7 applied at every step if required; no `float` on big ints (`x ** 0.5` → use `math.isqrt`)
- [ ] Duplicates
- [ ] Sorted / reverse-sorted input
- [ ] Max constraints: would ~10⁷ Python operations finish in the limit?
- [ ] Recursion depth at max n → iterative or `setrecursionlimit`
- [ ] Strings: case, spaces, non-letters, single character; strings are immutable (build lists)
- [ ] Graphs: disconnected, cycles, single node, no edges
- [ ] Off-by-one: `range(n)` vs `range(n + 1)`, inclusive vs exclusive slices, last element processed?
- [ ] Return type/format exactly as specified (`-1` when not found? `[]` vs `None`? list vs tuple?)
- [ ] No aliased rows (`[[0] * m] * n`), no mutable default args
- [ ] Debug prints removed; `sys.stdout.write` output ends with a newline

---

## 6. OA simulation protocol (70–120 minutes)

The simulations combine what real OAs and first-week-on-the-job tasks look like:

| Slot | Content | Language | Source |
|---|---|---|---|
| **DSA** | 1–2 LeetCode problems, unseen or not reviewed in ≥ 30 days | **Python** | NeetCode 150 + extras |
| **Debugging task** | Find and fix a specific bug from a symptom | Java | [`21-debugging-code-reading/drills.md`](./21-debugging-code-reading/drills.md) D03–D10 |
| **Existing-codebase task** | Navigate/answer/modify code you didn't write | Java | buggy-library, D01/D02/D16 |
| **Failing tests** | Make named red tests green without editing tests | Java | D05, D07, D09, D11 |
| **Small feature** | Add behaviour with tests | Java | D12–D14, backlog F1–F8 |

### Setup (not timed)

```bash
# fresh copy of the practice project for this sim
rm -rf ~/oa/oa-N && mkdir -p ~/oa/oa-N
cp -r 21-debugging-code-reading/exercises/buggy-library ~/oa/oa-N/
cd ~/oa/oa-N/buggy-library && git init -q && git add -A && git commit -qm baseline
mvn -q test   # warm the Maven cache; expect 40 run, 11 failures, 1 error
```

If a set lists **pre-applied fixes**, apply them now from the drill solutions and commit (`git commit -am "pre-applied"`). Setup time is not counted. Have a blank `solution.py` with the fast-input template from §4 ready for the DSA slots.

### Rules

1. One continuous block with a visible countdown. Phone in another room.
2. DSA problems in Python in the **LeetCode editor with the "Run" button only for samples you'd have**; submit once at the end of the slot. (Simulates hidden tests: count the first-submit result.)
3. Codebase tasks in your Java IDE; `mvn test` allowed, debugger allowed.
4. No solutions, no hints, no AI. If you open a hint from `drills.md`, log it and deduct points.
5. Slots have target times but **you manage the clock** — you may reorder slots, exactly like a real OA.
6. Stop at the time limit. Unfinished = unfinished.
7. Immediately after: fill the scoring sheet (§8), then log it in [`trackers/interview-tracker.md`](./trackers/interview-tracker.md). Only then look at solutions.
8. Within 48 h: re-solve anything you failed, untimed, and add DSA misses to the spaced-review queue in [`trackers/dsa-tracker.md`](./trackers/dsa-tracker.md).

---

## 7. The eight simulations

Schedule (ROADMAP §5/§10): **W17 → OA-1 · W18 → OA-2 · W19 → OA-3 · W20 → OA-4 · W22 → OA-5 · W23 → OA-6 · W24 → OA-7, OA-8**.
Week 21 has no simulation (FlagForge M2 + mock). Weeks 25–26: repeat the two lowest-scoring sets with the listed alternates, plus any real company OA practice tests.

If you've already solved a listed problem in the last 30 days, use its **alternate**. All DSA slots are in Python; all codebase slots are in Java.

### OA-1 — Week 17 · 70 min · "Warm start"

| Slot | Task | Target |
|---|---|---:|
| DSA (Python) | [238. Product of Array Except Self](https://leetcode.com/problems/product-of-array-except-self/) (alt: [49. Group Anagrams](https://leetcode.com/problems/group-anagrams/)) | 25 |
| Codebase (Java) | Build the project, list the 12 failing tests and group them into suspected root causes (D01, compressed) | 5 |
| Debugging (Java) | **D04** — students never pay fines (seen in W14 — target ≤ 10 min this time) | 15 |
| Failing tests (Java) | **D05** — make the three `Book` identity tests pass | 20 |
| Small feature (Java) | Add the test from D17 item 2 (student fine capped before discount) and make it pass | 5 |

Pre-applied fixes: none.

### OA-2 — Week 18 · 75 min

| Slot | Task | Target |
|---|---|---:|
| DSA (Python) | [3. Longest Substring Without Repeating Characters](https://leetcode.com/problems/longest-substring-without-repeating-characters/) (alt: [424. Longest Repeating Character Replacement](https://leetcode.com/problems/longest-repeating-character-replacement/)) | 25 |
| Debugging (Java) | **D06** — "Java" finds nothing | 15 |
| Failing tests (Java) | **D07** — a caller emptied the index | 20 |
| Small feature (Java) | **F8** (simplified): `List<Loan> dueWithin(int days, LocalDate today)` — active loans due in `[today, today+days]`, members with an email only, sorted by due date. Two tests. | 15 |

Pre-applied fixes: none.

### OA-3 — Week 19 · 90 min · two DSA problems

| Slot | Task | Target |
|---|---|---:|
| DSA 1 (Python) | [200. Number of Islands](https://leetcode.com/problems/number-of-islands/) (alt: [695. Max Area of Island](https://leetcode.com/problems/max-area-of-island/)) | 25 |
| DSA 2 (Python) | [56. Merge Intervals](https://leetcode.com/problems/merge-intervals/) (alt: [57. Insert Interval](https://leetcode.com/problems/insert-interval/)) | 20 |
| Debugging (Java) | **D08** — `NullPointerException` on email lookup (speed rep) | 10 |
| Debugging (Java) | **D10** — overdue report upside down (speed rep) | 10 |
| Existing codebase + tests (Java) | **D17** — write the missing tests (all four) | 25 |

Pre-applied fixes: D04 (needed for D17 item 2).

### OA-4 — Week 20 · 90 min

| Slot | Task | Target |
|---|---|---:|
| DSA 1 (Python) | [322. Coin Change](https://leetcode.com/problems/coin-change/) (alt: [198. House Robber](https://leetcode.com/problems/house-robber/)) | 25 |
| DSA 2 (Python) | [207. Course Schedule](https://leetcode.com/problems/course-schedule/) (alt: [210. Course Schedule II](https://leetcode.com/problems/course-schedule-ii/)) | 20 |
| Failing tests (Java) | **D09** — `returnAll` (both tests: the CME and the silent skip) | 20 |
| Small feature (Java) | **D13** — overdue fines report | 25 |

Pre-applied fixes: D03, D04, D10.

### OA-5 — Week 22 · 100 min

| Slot | Task | Target |
|---|---|---:|
| DSA 1 (Python) | [347. Top K Frequent Elements](https://leetcode.com/problems/top-k-frequent-elements/) (alt: [692. Top K Frequent Words](https://leetcode.com/problems/top-k-frequent-words/)) | 20 |
| DSA 2 (Python) | [1143. Longest Common Subsequence](https://leetcode.com/problems/longest-common-subsequence/) (alt: [72. Edit Distance](https://leetcode.com/problems/edit-distance/)) | 30 |
| Debugging (Java) | **D07** from a fresh copy (speed rep — target ≤ 10 min) + **D06** | 15 |
| Small feature (Java) | **D12** — renew a loan (tests first) | 35 |

Pre-applied fixes: D03.

### OA-6 — Week 23 · 100 min · "Codebase-heavy"

| Slot | Task | Target |
|---|---|---:|
| DSA (Python) | [994. Rotting Oranges](https://leetcode.com/problems/rotting-oranges/) (alt: [542. 01 Matrix](https://leetcode.com/problems/01-matrix/)) | 25 |
| Failing tests (Java) | **D11** — all 40 tests green, one commit per root cause | 45 |
| Small feature (Java) | **F7** — `history(memberId, page, size)` newest first; reject `page < 0`, `size < 1`, `size > 50` with `IllegalArgumentException`; three tests | 30 |

Pre-applied fixes: none (D11 includes everything).

### OA-7 — Week 24 (first) · 120 min · "Full loop day"

| Slot | Task | Target |
|---|---|---:|
| DSA 1 (Python) | [146. LRU Cache](https://leetcode.com/problems/lru-cache/) — implement with your own doubly linked list + dict (no `OrderedDict`) | 30 |
| DSA 2 (Python) | [743. Network Delay Time](https://leetcode.com/problems/network-delay-time/) (alt: [1631. Path With Minimum Effort](https://leetcode.com/problems/path-with-minimum-effort/)) | 25 |
| Failing tests (Java) | **D11** cold, fresh copy, target ≤ 40 min | 40 |
| Small feature (Java) | **D14** — multi-word search | 25 |

Pre-applied fixes: none.

### OA-8 — Week 24 (second) · 120 min · "First week on the job"

| Slot | Task | Target |
|---|---|---:|
| DSA 1 (Python) | [621. Task Scheduler](https://leetcode.com/problems/task-scheduler/) (alt: [1094. Car Pooling](https://leetcode.com/problems/car-pooling/)) | 30 |
| DSA 2 (Python) | [435. Non-overlapping Intervals](https://leetcode.com/problems/non-overlapping-intervals/) (alt: [452. Minimum Number of Arrows to Burst Balloons](https://leetcode.com/problems/minimum-number-of-arrows-to-burst-balloons/)) | 20 |
| Existing codebase (Java) | **D16** — single source of truth for "active" + `Clock` injection; all tests stay green | 40 |
| Small feature (Java) | **F3** — member suspension: unpaid fines ≥ 500¢ block checkout; `payFine`; at least 4 tests. Partial credit for a clean, tested subset. | 30 |

Pre-applied fixes: all eight (apply D11's full fix set during setup).

---

## 8. Scoring sheet

Copy per simulation. Score each slot, then the overall categories (1–4, same scale as [`16-interview-prep/mock-interviews.md`](./16-interview-prep/mock-interviews.md)).

```
OA-__  Date: ____  Total time used: ___ / ___ min

Slot results
| Slot          | Task         | Lang | Target min | Actual min | Result (pass / partial x/y / fail) | Hints used |
|---------------|--------------|------|-----------:|-----------:|-----------------------------------|-----------:|
| DSA 1         |              | py   |            |            |                                   |            |
| DSA 2         |              | py   |            |            |                                   |            |
| Debugging     |              | java |            |            |                                   |            |
| Failing tests |              | java |            |            |  tests green: __ / __             |            |
| Feature       |              | java |            |            |  tests written: __                |            |

Points (max 100)
- DSA: 35 per problem fully passing first submit; 25 if passing after one resubmit; 15 partial; 0 blank
  (one-DSA sets: DSA max 40, redistribute)
- Debugging / failing tests: 20 × (fraction of target tests green), −5 per hint
- Feature: 15 for spec met + tests, 8 for partial with tests, 3 without tests
- Regressions: −5 per previously green test left red
Score: ___ / 100

Category ratings (1–4)
- Problem solving: _   - Code quality: _   - Testing: _   - Time management: _

Biggest time sink:
Edge case I missed:
Python API / Java API I had to look up:
Next action (specific):
```

**Targets:** ≥ 60 by OA-2, ≥ 70 by OA-4, ≥ 80 on OA-7/OA-8. Two sims below target in a row → spend the next week's Saturday interview block on the weakest slot type only.

---

## 9. Simulation log

Keep the running log in [`trackers/interview-tracker.md`](./trackers/interview-tracker.md#oa-simulations). Quick view:

| OA | Week | Date | Score | DSA result (Python) | Codebase result (Java) | Biggest issue | Re-do date |
|---|---:|---|---:|---|---|---|---|
| OA-1 | 17 | | | | | | |
| OA-2 | 18 | | | | | | |
| OA-3 | 19 | | | | | | |
| OA-4 | 20 | | | | | | |
| OA-5 | 22 | | | | | | |
| OA-6 | 23 | | | | | | |
| OA-7 | 24 | | | | | | |
| OA-8 | 24 | | | | | | |

---

## 10. Real OA day — condensed

- [ ] Python 3 selected; fast-input template typed from memory if it's a stdin problem.
- [ ] Read all problems first (2 min) if the platform allows it; do the easiest first.
- [ ] Per problem: constraints → complexity target (Python budget ≈ 10⁷ ops) → edge cases on paper → code → samples → own edge cases → submit.
- [ ] `deque` not `pop(0)`, set/dict not `in list`, join not `+=`, iterative not deep recursion. No debug prints.
- [ ] Brute force at 8 minutes stuck. Move on at 1.5× budget.
- [ ] Last 5 minutes: make sure every problem has *something* submitted.
- [ ] Codebase section: build first, run tests, read the failing test before the code.
- [ ] Afterwards: write down the problems from memory (for your own practice only — never share OA content if the company prohibits it), and log it in the tracker.
