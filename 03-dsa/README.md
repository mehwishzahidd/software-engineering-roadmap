# 🧩 03 — Data Structures & Algorithms (Python-first, Java reps)

> DSA runs **every week from Week 1 to Week 26** at **6–8 h/week** (≈180 h total, roughly 60% new problems / 40% spaced reviews),
> in parallel with the four flagship projects (FlowGrid, LedgerX, ForgeCI, FlagForge).
> The order, weeks and weekly counts below are copied from [`ROADMAP.md` §5 and §7](../ROADMAP.md#7-dsa-progression).
> If this file ever disagrees with the roadmap, the roadmap wins.

**Tracking:** every problem assigned in these guides is pre-listed in [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md).
**Python toolkit:** [`PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md) + [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) + [`19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md) — read them in Weeks 1–2 and keep the cheatsheet open.
**Java quick reference:** [`java-dsa-toolkit.md`](./java-dsa-toolkit.md) — only for the weekly Java rep.

### Language rule: Python-first, one Java rep per week

| Track | Language | Used for |
|---|---|---|
| **A · Coding interview** | **Python 3.12** | Every new DSA problem, every spaced review, NeetCode, LeetCode, OAs (HackerRank/CodeSignal/Amazon-style), timed mocks, explaining algorithms. ≈90% of all algorithm practice. |
| **B · Software-engineering / résumé interview** | **Java 21** | FlowGrid, LedgerX, ForgeCI, FlagForge, Spring Boot, concurrency, "walk me through your code". |

**The Java-rep rule.** Once per week, take **one problem you already solved in Python** (the guide's "The same in Java" section names it) and re-implement it in Java from a blank file: `HashMap`/`HashSet`, `ArrayList`, `ArrayDeque`, `PriorityQueue` + `Comparator`, BFS/DFS, trees, recursion. Log it with the tracker's **Java rep ✓** column. A Java rep is **never** counted as a new problem, and you **never** solve everything twice. Why bother: an interviewer on Track B may ask you to write a quick loop over a `Map` or a comparator in Java; your backend code uses the same collections every day.


---

## Contents

1. [What "done" looks like](#1-what-done-looks-like)
2. [Progression table (NeetCode-style)](#2-progression-table)
3. [How to study a pattern — the 7-step loop](#3-how-to-study-a-pattern--the-7-step-loop)
4. [How to attempt a problem — the timebox protocol](#4-how-to-attempt-a-problem--the-timebox-protocol)
5. [How to use NeetCode Pro](#5-how-to-use-neetcode-pro)
6. [Spaced repetition rules](#6-spaced-repetition-rules)
7. [Pattern-recognition cheat table](#7-pattern-recognition-cheat-table)
8. [Constraint → complexity cheat table](#8-constraint--complexity-cheat-table)
9. [Structure of every pattern guide](#9-structure-of-every-pattern-guide)
10. [Weekly DSA time budget](#10-weekly-dsa-time-budget)

---

## 1. What "done" looks like

By the end of Week 26 you should be able to:

- [ ] Recognise which of the 23 patterns a new problem belongs to within ~3 minutes, and say *why* (the signal in the statement).
- [ ] Solve an unseen **Easy** in ≤ 15 min and an unseen **Medium** in ≤ 25–30 min in **Python**, talking while you code.
- [ ] State time and space complexity for every solution without hesitation, including the recursion stack.
- [ ] Write in Python from a blank file, without looking anything up: binary search (and `bisect`), BFS with `collections.deque`, DFS on a grid, union-find, a trie, a `heapq` top-K, a backtracking template, and a memo (`@cache`) + tabulation DP.
- [ ] Re-write any of those in Java 21 on request (≈25 Java reps logged by Week 26 — one per week).
- [ ] Pass a 70–120 min OA with 1–2 problems (see [`OA_PREP.md`](../OA_PREP.md)).
- [ ] Have ≈185 problems logged in the tracker, with the NeetCode 150 core at `Solved Independently` or better and ≥ 60% of all problems `Mastered`.

---

## 2. Progression table

"Target" = **new** problems for that pattern (beginner + interview lists in the guide). Reviews are *in addition*.
Weekly totals match the "DSA (new)" column in [`ROADMAP.md` §5](../ROADMAP.md#5-week-by-week-master-table).

| # | Pattern | Weeks | Target (new) | NeetCode 150 section | Guide |
|---:|---|---|---:|---|---|
| 0 | Big-O & complexity analysis | 1 | 0 LC (10 analysis drills) | — | [`00-big-o.md`](./00-big-o.md) |
| 1 | Arrays & Strings | 1–2 | 7 (W1: 6, W2: 1) | Arrays & Hashing | [`01-arrays-strings.md`](./01-arrays-strings.md) |
| 2 | HashMap / HashSet | 2 | 7 | Arrays & Hashing | [`02-hashing.md`](./02-hashing.md) |
| 3 | Two Pointers | 3 | 5 | Two Pointers | [`03-two-pointers.md`](./03-two-pointers.md) |
| 4 | Prefix Sums | 3 | 3 | Arrays & Hashing (extras) | [`04-prefix-sums.md`](./04-prefix-sums.md) |
| 5 | Sliding Window | 4 | 8 | Sliding Window | [`05-sliding-window.md`](./05-sliding-window.md) |
| 6 | Stack & Queue (monotonic stack) | 5 | 8 | Stack | [`06-stack-queue.md`](./06-stack-queue.md) |
| 7 | Binary Search (+ search on answer) | 6 | 8 | Binary Search | [`07-binary-search.md`](./07-binary-search.md) |
| 8 | Linked Lists | 7 | 8 | Linked List | [`08-linked-lists.md`](./08-linked-lists.md) |
| 9 | Recursion | 8 | 6 | (foundation for Trees / Backtracking / DP) | [`09-recursion.md`](./09-recursion.md) |
| 10 | Trees (traversals, DFS/BFS) | 9 | 8 | Trees | [`10-trees.md`](./10-trees.md) |
| 11 | Binary Search Trees | 10 | 5 | Trees | [`11-bst.md`](./11-bst.md) |
| 12 | Tries | 10 | 3 | Tries | [`12-tries.md`](./12-tries.md) |
| 13 | Heap / PriorityQueue | 11 | 8 | Heap / Priority Queue | [`13-heap-priority-queue.md`](./13-heap-priority-queue.md) |
| 14 | Backtracking | 12 | 8 | Backtracking | [`14-backtracking.md`](./14-backtracking.md) |
| 15 | Graphs: BFS / DFS | 13 | 8 | Graphs | [`15-graphs-bfs-dfs.md`](./15-graphs-bfs-dfs.md) |
| 16 | Topological Sort | 14 | 4 | Graphs | [`16-topological-sort.md`](./16-topological-sort.md) |
| 17 | Union-Find | 14 | 4 | Graphs | [`17-union-find.md`](./17-union-find.md) |
| 18 | Dynamic Programming 1-D | 15–16 | 11 (W15: 8, W16: 3) | 1-D DP | [`18-dp-1d.md`](./18-dp-1d.md) |
| 19 | Intervals | 16 | 5 | Intervals | [`19-intervals.md`](./19-intervals.md) |
| 20 | Greedy | 17 | 5 | Greedy | [`20-greedy.md`](./20-greedy.md) |
| 21 | Dynamic Programming 2-D | 17–18 | 10 (W17: 3, W18: 7) | 2-D DP | [`21-dp-2d.md`](./21-dp-2d.md) |
| 22 | Advanced Graphs (Dijkstra, MST awareness) | 19 | 4 | Advanced Graphs | [`22-advanced-graphs.md`](./22-advanced-graphs.md) |
| 23 | Bit Manipulation basics | 19 | 3 | Bit Manipulation | [`23-bit-manipulation.md`](./23-bit-manipulation.md) |
| — | Mixed / timed / OA | 20–26 | 39 (6+6+6+6+6+5+4) from the **stretch pools** | all | [`OA_PREP.md`](../OA_PREP.md) |

**Totals:** 146 new problems in Weeks 1–19 + 39 in Weeks 20–26 = **≈185** (matches ROADMAP §5).
Every pattern guide except 00, 01 and 09 ends its interview list with a **stretch pool** tagged "W20–26". Those 45 problems form the menu for the mixed/timed weeks: in Weeks 20–22 draw from your 3 weakest patterns (lowest confidence in the tracker summary), in Weeks 23–24 solve them timed alongside OA simulations, in Week 25 target whatever Checkpoint 24 flagged, and in Week 26 switch to the [maintenance plan](../16-interview-prep/maintenance-plan.md).

Topological sort sits in Week 14 on purpose: ForgeCI's DAG scheduling milestone (Week 19) uses it for real — see [`16-topological-sort.md`](./16-topological-sort.md).

### Week-by-week DSA view

| Week | New | Pattern(s) | Project running in parallel | Week | New | Pattern(s) | Project running in parallel |
|---:|---:|---|---|---:|---:|---|---|
| 1 | 6 | Big-O, Arrays & Strings | Foundation | 14 | 8 | Topo sort (4) + Union-Find (4) | ForgeCI M1 |
| 2 | 8 | Arrays (1) + Hashing (7) | Foundation | 15 | 8 | 1-D DP | ForgeCI M2 |
| 3 | 8 | Two Pointers (5) + Prefix Sums (3) | Foundation | 16 | 8 | 1-D DP (3) + Intervals (5) | ForgeCI M3 |
| 4 | 8 | Sliding Window | FlowGrid M1 | 17 | 8 | Greedy (5) + 2-D DP (3) | ForgeCI M4 · OA sim #1 |
| 5 | 8 | Stack & Queue | FlowGrid M2 | 18 | 7 | 2-D DP | ForgeCI M5 |
| 6 | 8 | Binary Search | FlowGrid M3 | 19 | 7 | Advanced Graphs (4) + Bits (3) | ForgeCI M6 |
| 7 | 8 | Linked Lists | FlowGrid M4 | 20 | 6 | Mixed: weakest 3 patterns | FlagForge M1 |
| 8 | 6 | Recursion + review week | FlowGrid M5 | 21 | 6 | Mixed review | FlagForge M2 |
| 9 | 8 | Trees | LedgerX M1 | 22 | 6 | Mixed review | FlagForge M3 |
| 10 | 8 | BST (5) + Tries (3) | LedgerX M2 | 23 | 6 | Timed mixed | FlagForge M4 |
| 11 | 8 | Heap / PriorityQueue | LedgerX M3 | 24 | 6 + OA | Timed mixed | Polish I |
| 12 | 8 | Backtracking | LedgerX M4 | 25 | 5 | Weakest patterns | Polish II |
| 13 | 8 | Graphs BFS/DFS | LedgerX M5 | 26 | 4 | Maintenance | Final loop |

---

## 3. How to study a pattern — the 7-step loop

This is the DSA version of the repo philosophy **Learn → Build → Break → Debug → Explain → Review → Build again**.
Do it once per pattern, spread across the week's DSA blocks (see §10).

| Step | What you do | Time | Output |
|---:|---|---|---|
| **1. Learn the concept** | Read §1 "Concepts" of the guide + watch *one* NeetCode explanation of the pattern's anchor problem. Write 5 lines in your own words: what the pattern is, when it applies, its core invariant. | 30–45 min | Notes in the guide's margin or your notes file |
| **2. Implement from scratch in Python** | Close everything. Type the data structure / template from §3 of the guide from memory (e.g. `class MinHeap`, `class Trie`, the sliding-window loop). Then compare line by line. Fix differences. Repeat until it's clean. | 30–60 min | A `.py` file in your practice repo that runs and ends with 3 `assert`s (or a tiny `pytest` file — you learn pytest in Week 3) |
| **3. Break it** | Change one thing and predict what fails: `<=` → `<` in binary search, remove `visited`, `popleft()` → `pop()`, a mutable default argument, `res.append(path)` without copying. Run it. Was your prediction right? | 15 min | One line per break in your notes |
| **4. Two beginner problems** | The first two problems in §7 of the guide. Use the timebox protocol (§4 below). These prove you can *apply* the template. | 30–60 min | Tracker rows updated |
| **5. Interview problems** | Work through §8 in order. Timebox strictly. Log status honestly. | rest of the week | Tracker rows updated |
| **6. Explain** | For at least one problem per session: explain out loud (or record) the approach, why it's correct, and its complexity — as if to an interviewer. | 5 min / problem | "Explained ✓" in tracker notes |
| **7. Review (+ Java rep)** | Put every problem on the Day 0/3/7/14/30 schedule (§6). Reviews are **never** skipped ([ROADMAP §13](../ROADMAP.md#13-rules-for-falling-behind) rule 1). | ongoing | Review queue in tracker; once a week do the guide's Java rep (§4 of the guide) |

> **Rule:** you are not allowed to start step 5 until step 2 produces a template you typed from memory that runs and passes its asserts.

---

## 4. How to attempt a problem — the timebox protocol

Use a timer. The protocol is identical for Easy and Medium; only the timebox changes.

| Phase | Easy | Medium | Hard | What you do |
|---|---|---|---|---|
| **A. Read & clarify** | 2 min | 3 min | 5 min | Restate the problem in one sentence. Write 2 examples by hand, including an edge case (empty, one element, duplicates, negatives, max constraint). Read the **constraints** — they tell you the target complexity (§8). |
| **B. Brute force** | 2 min | 3 min | 5 min | Say the naive solution and its complexity out loud. Don't code it unless stuck. |
| **C. Optimise** | ≤ 8 min | ≤ 12 min | ≤ 20 min | Find the bottleneck in the brute force. Match against §7 cheat table. Write the plan as 3–6 comment lines *before* coding. |
| **D. Code** | ≤ 8 min | ≤ 12 min | ≤ 20 min | Python from a blank editor, with type hints on the function signature (as LeetCode shows them). No autocomplete crutches in timed practice (LeetCode editor is fine). |
| **E. Test** | 2 min | 3 min | 5 min | Trace one normal example and one edge case **by hand** before pressing Run. |
| **Total** | **15 min** | **25 min** | **45 min** | |

**If the timer runs out:**

1. **Stuck with no idea (at ~15 min Easy / ~20–25 min Medium):** open *one* hint (LeetCode "Hints" tab or the first 2 minutes of the NeetCode video). Set a fresh 10-minute timer. If you solve it now → status `Solved With Hint`.
2. **Still stuck after the hint + 10 min:** read/watch the full solution. Understand it — close it — type it yourself from memory. Status → `Solved With Solution`.
3. **Next day (mandatory for Hint/Solution statuses):** re-solve it **from a blank editor** without looking. That re-solve is the Day-0 → Day-1 bridge; it does not replace the Day-3 review.
4. Write **one line** in the tracker's Notes column: *the insight you were missing* (e.g., "sort first so duplicates are adjacent"). This line is what you read before each review.

**Never:** read the solution before 15 minutes of honest effort; copy-paste code; mark `Solved Independently` if you saw *anything* (discussion titles, a hint, the video thumbnail's approach name).

---

## 5. How to use NeetCode Pro

You have NeetCode Pro. Use it as a **structured syllabus and explanation library**, not as a way to avoid struggle.

| Feature | How to use it here |
|---|---|
| **NeetCode 150 / Roadmap** | The section names in the progression table map 1:1 to the roadmap tree on neetcode.io. Tick problems there *and* in [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md) (the tracker is the source of truth — it has review dates). |
| **Courses (DSA for Beginners / Advanced Algorithms)** | Use the lesson for a data structure in **Step 1** of the loop (e.g., the heap lesson before implementing `MinHeap`). One lesson, not the whole course. |
| **Video solutions** | Only *after* the timebox, or after solving to compare approaches. Watch at 1.25×. Pause before the code section and write the code yourself. |
| **Python solutions tab** | Compare *after* you've submitted. Look for idioms you missed (`Counter`, `defaultdict`, `enumerate`, `zip`, `heapq.nlargest`, `bisect_left`). For the weekly Java rep, compare against the Java tab (`merge`, `computeIfAbsent`, `Integer.compare`). |
| **Premium LeetCode problems** (e.g. 252, 253, 261, 269, 286, 323) | NeetCode hosts free equivalents (e.g. "Meeting Rooms", "Graph Valid Tree", "Islands and Treasure" for Walls and Gates, "Count Connected Components"). Use those — you don't need LeetCode Premium. |
| **Practice / flashcards (spaced review)** | Optional. The tracker's Day 0/3/7/14/30 schedule is the authoritative one; don't run two review systems. |
| **Mock interview / timed mode** | Think-aloud weekly from Week 5, mock #1 in Week 10, weekly mocks from Week 14, OA simulations from Week 17 — see [`OA_PREP.md`](../OA_PREP.md). |

---

## 6. Spaced repetition rules

Full statuses and transition rules live in [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#1-status-legend). Summary:

| Review | When | What counts as a pass |
|---|---|---|
| **Day 0** | Same day you first solve it | You solved it (any status except `Attempted`). Schedule Day 3. |
| **Day 3** | 3 days later | Blank editor, no hints, ≤ original timebox, passes all tests. |
| **Day 7** | 7 days after Day 0 | Same, plus explain complexity out loud. |
| **Day 14** | 14 days after Day 0 | Same, and write the approach as 3 comment lines *before* coding. |
| **Day 30** | 30 days after Day 0 | **Timed** (Easy ≤ 10 min, Medium ≤ 20 min, Hard ≤ 35 min), independent, clean first submit or one fix. Pass → `Mastered`. |

**Rules**

1. **Fail a review → reset to Day 3** (from the failed review date), and set status `Needs Review`.
2. A review "fails" if you needed a hint, looked at old code, exceeded the timebox, or needed > 2 submits.
3. **Reviews come before new problems.** If you only have 30 minutes, do reviews.
4. A review of a Medium should take ≤ 15 min — you're recalling, not re-deriving. If it takes longer, that's a fail.
5. Cap reviews at ~6 per day; if the queue exceeds that, cut new problems that week ([ROADMAP §13](../ROADMAP.md#13-rules-for-falling-behind)).
6. Each pattern gets a **"revisit week"** later in the roadmap (listed in each guide's §13) where you re-solve 2–3 of its problems timed, even if they're not due.

---

## 7. Pattern-recognition cheat table

Read the statement for these signals. More detail in each guide's §6.

| Signal in the problem statement | Likely pattern | Guide |
|---|---|---|
| "Have we seen this before?", duplicates, counts, "anagram", pair that sums to target (unsorted) | HashMap / HashSet | [02](./02-hashing.md) |
| Sorted array + pair/triplet with a target; "in-place", palindrome check, merge two sorted | Two pointers | [03](./03-two-pointers.md) |
| "Sum of subarray/range", many range-sum queries, "subarray sum equals k" (with negatives) | Prefix sums (+ hashmap) | [04](./04-prefix-sums.md) |
| "Longest / shortest **contiguous** subarray/substring such that…", "at most k", window of size k | Sliding window | [05](./05-sliding-window.md) |
| Matching brackets, "next greater/smaller element", undo, evaluate expression, "days until warmer" | Stack / monotonic stack | [06](./06-stack-queue.md) |
| Sorted input + find position; "minimum capacity/speed such that…"; answer is monotonic (if x works, x+1 works) | Binary search / search on answer | [07](./07-binary-search.md) |
| `ListNode`, "in-place", cycle, middle, k-th from end, reverse | Linked list (dummy node, fast/slow) | [08](./08-linked-lists.md) |
| Problem defined in terms of a smaller version of itself | Recursion | [09](./09-recursion.md) |
| `TreeNode`, depth, path, level-by-level, "left/right view" | Tree DFS / BFS | [10](./10-trees.md) |
| BST, "k-th smallest", sorted order from a tree, validate ordering | BST inorder / bounds | [11](./11-bst.md) |
| Prefix matching, autocomplete, "words starting with", many word lookups on a board | Trie | [12](./12-tries.md) |
| "k largest / smallest / closest / most frequent", merge k sorted, running median, schedule by priority | Heap / PriorityQueue | [13](./13-heap-priority-queue.md) |
| "All combinations / permutations / subsets / partitions", "generate all valid…", n ≤ ~20 | Backtracking | [14](./14-backtracking.md) |
| Grid of cells, islands, "connected", shortest path in **unweighted** graph, "minimum steps" | Graph BFS / DFS | [15](./15-graphs-bfs-dfs.md) |
| Prerequisites, dependencies, "order of tasks/courses", detect cycle in directed graph | Topological sort | [16](./16-topological-sort.md) |
| "Number of connected components", "redundant edge", merge groups/accounts, dynamic connectivity | Union-Find | [17](./17-union-find.md) |
| "Number of ways", "min/max cost to reach", choices at each index that depend on earlier results | 1-D DP | [18](./18-dp-1d.md) |
| Pairs `[start, end]`, overlapping meetings, merge/insert ranges, "minimum rooms" | Intervals (sort by start/end) | [19](./19-intervals.md) |
| Local choice that provably never needs undoing; "minimum number of jumps", "can you reach" | Greedy | [20](./20-greedy.md) |
| Two strings/sequences compared, grid paths with counts/costs, knapsack with a target | 2-D DP | [21](./21-dp-2d.md) |
| **Weighted** shortest path, "minimum cost to connect all", "cheapest with ≤ k stops" | Dijkstra / Prim / Bellman-Ford | [22](./22-advanced-graphs.md) |
| "Without extra space", every element appears twice except one, powers of two, count bits, XOR | Bit manipulation | [23](./23-bit-manipulation.md) |

---

## 8. Constraint → complexity cheat table

Rough budget: **~10^7 simple Python operations per second** (Java ≈ 10^8). Python is ~10× slower per operation, so the complexity class matters even more — an O(n²) with n = 10^4 (10^8 steps) will time out in Python where Java might squeak through. Use `n` to pick the target complexity **before** designing.

| Constraint on n | Target complexity | Typical patterns |
|---|---|---|
| n ≤ 10–12 | O(n!) | Permutations backtracking |
| n ≤ 20–25 | O(2^n) / O(2^n · n) | Subsets, bitmask |
| n ≤ 100–500 | O(n^3) | Interval DP, Floyd-Warshall |
| n ≤ 1,000–5,000 | O(n^2) | 2-D DP, double loop |
| n ≤ 10^5 – 2·10^5 | O(n log n) or better | Sort + scan, heap, binary search, BFS/DFS |
| n ≤ 10^6 – 10^7 | O(n) | Hashing, two pointers, sliding window, prefix sums |
| n up to 10^9 / 10^18 (a *value*, not a length) | O(log n) or O(1) | Binary search on answer, math, bits |

Python `int` never overflows (arbitrary precision), so sums of 10^5 values up to 10^9 are safe — but the **same code overflows `int` in your Java rep** → use `long` there (see [Java quick reference](./java-dsa-toolkit.md#5-overflow-integer-caching-and-other-traps)). Also note: `n` up to 10^5 with **recursion** → Python's default recursion limit (1000) fails first; see each guide's mistakes section.

---

## 9. Structure of every pattern guide

Each of `01`–`23` has the same 13 sections plus a worked example, so you always know where to look:

1. Concepts to learn · 2. Prerequisites · 3. **Python implementation** (runnable Python 3.12 templates with asserts) · 4. **The same in Java (occasional reps)** — Java idioms + the week's Java-rep problem · 5. Common sub-patterns · 6. How to recognise it · 7. Beginner problems · 8. Interview problems (insights hidden in `<details>`) + stretch pool · 9. NeetCode / LeetCode practice order · 10. Target count · 11. Common mistakes (Python pitfalls + general + Java-rep traps) · 12. Mastery criteria · 13. Revision schedule · **Worked example in Python** (clarify → brute force → optimise → code → tests → complexity).

`00-big-o.md` uses the same skeleton adapted to analysis drills instead of LeetCode problems.

---

## 10. Weekly DSA time budget

From the standard weekly rhythm in [`ROADMAP.md` §12](../ROADMAP.md#12-standard-weekly-rhythm) — **≈ 8 h DSA/week** (6–8 h; ~60% new, ~40% review). DSA blocks sit next to project blocks, so protect them: a day of FlowGrid/LedgerX/ForgeCI/FlagForge debugging must not eat the DSA slot.

| Day | DSA block | Use it for |
|---|---|---|
| Mon | 1.5 h | Due reviews (30 min) · loop steps 1–2 for the week's pattern: concept + implement from scratch (60 min) |
| Tue | 2 h | New problems (beginner → interview); one explained out loud in the interview block |
| Wed | 1.5 h | Due reviews · re-solve Monday/Tuesday's Hint/Solution problems from a blank editor |
| Thu | 2 h | New problems — the week's hardest problem goes here or Tuesday (fresh brain) |
| Fri | 1 h | **Reviews only** (lighter day) + the week's **Java rep** (~30 min) + tracker update |
| Sat | — | Mock / think-aloud in the interview block uses a problem from this week's pattern |
| Sun | review | Weekly test: 2 random problems from this week, timed; plan next week's review queue |

**Minimum viable DSA day** ([ROADMAP §13](../ROADMAP.md#13-rules-for-falling-behind) rule 5): one due review, nothing else.
