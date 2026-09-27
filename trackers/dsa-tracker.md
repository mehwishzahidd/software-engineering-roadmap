# 📊 DSA Tracker

> Every problem assigned in the [`03-dsa/`](../03-dsa/README.md) pattern guides is pre-listed below with status **Not Started**.
> **Language:** solve and review in **Python** (Track A — coding interviews). Once a week, re-implement one already-solved problem in **Java** and tick **Java rep ✓** (see [§3](#3-java-reps)).
> Source of truth for week order and weekly counts: [`ROADMAP.md` §5 and §7](../ROADMAP.md#7-dsa-progression). Totals: **146 core problems (Weeks 1–19) + 45-problem stretch pool (choose 39 for Weeks 20–26) ≈ 185 new problems**.

## Contents

1. [Status legend](#1-status-legend)
2. [Spaced repetition schedule](#2-spaced-repetition-schedule)
3. [Java reps](#3-java-reps)
4. [Per-pattern summary](#4-per-pattern-summary)
5. [Problem tables (one per pattern)](#5-problem-tables)
6. [Weeks 20–26: mixed / timed plan](#6-weeks-2026-mixed--timed-plan)
7. [Review queue](#7-review-queue)
8. [Weekly DSA log template](#8-weekly-dsa-log-template)

**How to fill in a row:** `Attempts` = number of sittings (including reviews); `Time` = minutes for the latest attempt (e.g. `18m`); dates as `YYYY-MM-DD`; `Notes` = the **one insight you were missing** + `T: O(..) S: O(..)`. Keep one row per problem forever — update it in place.

---

## 1. Status legend

| Status | Exact definition | Counts as solved? |
|---|---|---|
| **Not Started** | Never opened the problem. | No |
| **Attempted** | Worked on it for the full timebox (Easy 15 / Medium 25 / Hard 45 min) and did **not** reach an accepted solution, even after a hint and the solution. Use also when you stopped early and must return. | No |
| **Solved With Solution** | Accepted only after reading/watching a full solution (then closing it and typing your own version). | Yes (weak) |
| **Solved With Hint** | Accepted after at most **one** hint (LeetCode hint, first 2 minutes of a video, a pattern name from someone), without seeing code. | Yes |
| **Solved Independently** | Accepted with **zero** outside help within the timebox. At most 2 submissions. | Yes |
| **Needs Review** | Any scheduled review failed (needed help, over the timebox, or > 2 submissions), or you can no longer explain the approach. | Yes, but decaying |
| **Mastered** | Passed the **Day-30** review: independent, clean (≤ 2 submissions), **timed** (Easy ≤ 10 min, Medium ≤ 20 min, Hard ≤ 35 min), and complexity explained out loud. | Yes (durable) |

### Transition rules

| From | Event | To |
|---|---|---|
| Not Started | First sitting ends without acceptance | Attempted |
| Not Started / Attempted | Accepted with full solution | Solved With Solution |
| Not Started / Attempted | Accepted with ≤ 1 hint | Solved With Hint |
| Not Started / Attempted | Accepted with no help | Solved Independently |
| Solved With Solution / Solved With Hint | **Mandatory next-day re-solve from a blank editor** passes | Solved Independently (review clock still starts at the original Day 0) |
| Any solved status | A Day 3 / 7 / 14 review passes | unchanged (move `Next Review` forward) |
| Any solved status | A review fails | **Needs Review** — reset the clock: next review = failed date + 3 days |
| Needs Review | Passes a review | Solved Independently; continue the schedule from Day 7 |
| Solved Independently | Passes the **Day-30** timed review | **Mastered** |
| Mastered | Fails a later spot-check (mocks, OA sims, revisit weeks) | Needs Review (restart at Day 3) |

Rules: **never** mark `Solved Independently` if you saw anything (discussion titles, hints, a video thumbnail naming the approach). `Mastered` only comes from a Day-30 timed pass.

---

## 2. Spaced repetition schedule

| Review | Due | Pass criteria |
|---|---|---|
| **Day 0** | Same day as the first solve | Accepted (any solved status). Set `Next Review` = Day 0 + 3. |
| **Day 3** | +3 days | Blank editor, no help, within the original timebox. |
| **Day 7** | +7 days from Day 0 | As Day 3 **and** explain time/space out loud. |
| **Day 14** | +14 days from Day 0 | As Day 7 **and** write the approach as 3 comment lines before coding. |
| **Day 30** | +30 days from Day 0 | **Timed**: Easy ≤ 10 min, Medium ≤ 20 min, Hard ≤ 35 min; ≤ 2 submissions → `Mastered`. |

**Rules**

1. **Fail a review → reset to Day 3**: status `Needs Review`, `Next Review` = today + 3. After passing, continue with Day 7, 14, 30 counted from the pass date.
2. A review fails if you needed any hint, looked at old code, exceeded the timebox, or needed > 2 submissions.
3. **Reviews before new problems** ([ROADMAP §13](../ROADMAP.md#13-rules-for-falling-behind) rule 1). If the queue exceeds ~6/day, cut new problems that week.
4. A Medium review should take ≤ 15 min; longer = fail.
5. `Mastered` problems get a spot-check in their pattern's **revisit week** (listed in each guide's §13) and in mocks/OA sims.
6. Reviews are done in **Python**. A Java rep never replaces a review.

---

## 3. Java reps

One per week, always a problem **already solved in Python**. Never counted as a new problem. Tick the `Java rep ✓` column in the problem's row **and** fill this log. Details and idioms: [`03-dsa/java-dsa-toolkit.md`](../03-dsa/java-dsa-toolkit.md#7-the-java-rep-schedule).

| Week | Planned rep | Done (✓) | Time | Java trap I hit |
|---:|---|:---:|---|---|
| 1 | 189 Rotate Array ([01](../03-dsa/01-arrays-strings.md)) | ☐ | | |
| 2 | 49 Group Anagrams ([02](../03-dsa/02-hashing.md)) | ☐ | | |
| 3 | 560 Subarray Sum Equals K ([04](../03-dsa/04-prefix-sums.md)) | ☐ | | |
| 4 | 3 Longest Substring Without Repeating Characters ([05](../03-dsa/05-sliding-window.md)) | ☐ | | |
| 5 | 739 Daily Temperatures ([06](../03-dsa/06-stack-queue.md)) | ☐ | | |
| 6 | 875 Koko Eating Bananas ([07](../03-dsa/07-binary-search.md)) | ☐ | | |
| 7 | 146 LRU Cache ([08](../03-dsa/08-linked-lists.md)) | ☐ | | |
| 8 | 50 Pow(x, n) ([09](../03-dsa/09-recursion.md)) | ☐ | | |
| 9 | 102 Binary Tree Level Order Traversal ([10](../03-dsa/10-trees.md)) | ☐ | | |
| 10 | 98 Validate Binary Search Tree ([11](../03-dsa/11-bst.md)) | ☐ | | |
| 11 | 973 K Closest Points to Origin ([13](../03-dsa/13-heap-priority-queue.md)) | ☐ | | |
| 12 | 78 Subsets ([14](../03-dsa/14-backtracking.md)) | ☐ | | |
| 13 | 994 Rotting Oranges ([15](../03-dsa/15-graphs-bfs-dfs.md)) | ☐ | | |
| 14 | 207 Course Schedule ([16](../03-dsa/16-topological-sort.md)) | ☐ | | |
| 15 | 198 House Robber ([18](../03-dsa/18-dp-1d.md)) | ☐ | | |
| 16 | 56 Merge Intervals ([19](../03-dsa/19-intervals.md)) | ☐ | | |
| 17 | 763 Partition Labels ([20](../03-dsa/20-greedy.md)) | ☐ | | |
| 18 | 1143 Longest Common Subsequence ([21](../03-dsa/21-dp-2d.md)) | ☐ | | |
| 19 | 743 Network Delay Time ([22](../03-dsa/22-advanced-graphs.md)) | ☐ | | |
| 20 | one weakest-pattern problem (tracker confidence ≤ 2) | ☐ | | |
| 21 | one weakest-pattern problem (tracker confidence ≤ 2) | ☐ | | |
| 22 | one weakest-pattern problem (tracker confidence ≤ 2) | ☐ | | |
| 23 | one weakest-pattern problem (tracker confidence ≤ 2) | ☐ | | |
| 24 | one weakest-pattern problem (tracker confidence ≤ 2) | ☐ | | |
| 25 | one weakest-pattern problem (tracker confidence ≤ 2) | ☐ | | |
| 26 | one weakest-pattern problem (tracker confidence ≤ 2) | ☐ | | |

---

## 4. Per-pattern summary

Update weekly (Sunday review). **Confidence 1–5:** 1 = can't start unseen problems · 2 = need hints on Easies · 3 = Easies alone, Mediums with hints · 4 = unseen Mediums in ≤ 30 min · 5 = unseen Mediums in ≤ 20 min and can teach it. Weeks 20–22 draw from the three lowest-confidence patterns.

| Pattern | Weeks | Target (core) | Stretch pool | Solved | Mastered | Confidence (1–5) | Java rep ✓ |
|---|---|---:|---:|---:|---:|:---:|:---:|
| [00 — Big-O](../03-dsa/00-big-o.md) (10 drills) | 1 | 0 | 0 | 0 | 0 | | — |
| [01 — Arrays & Strings](#01--arrays--strings) | 1–2 | 7 | 0 | 0 | 0 | | ☐ |
| [02 — HashMap / HashSet](#02--hashmap--hashset) | 2 | 7 | 3 | 0 | 0 | | ☐ |
| [03 — Two Pointers](#03--two-pointers) | 3 | 5 | 3 | 0 | 0 | | (opt.) ☐ |
| [04 — Prefix Sums](#04--prefix-sums) | 3 | 3 | 2 | 0 | 0 | | ☐ |
| [05 — Sliding Window](#05--sliding-window) | 4 | 8 | 2 | 0 | 0 | | ☐ |
| [06 — Stack & Queue](#06--stack--queue) | 5 | 8 | 2 | 0 | 0 | | ☐ |
| [07 — Binary Search](#07--binary-search) | 6 | 8 | 2 | 0 | 0 | | ☐ |
| [08 — Linked Lists](#08--linked-lists) | 7 | 8 | 3 | 0 | 0 | | ☐ |
| [09 — Recursion](#09--recursion) | 8 | 6 | 0 | 0 | 0 | | ☐ |
| [10 — Trees](#10--trees) | 9 | 8 | 5 | 0 | 0 | | ☐ |
| [11 — Binary Search Trees](#11--binary-search-trees) | 10 | 5 | 2 | 0 | 0 | | ☐ |
| [12 — Tries](#12--tries) | 10 | 3 | 1 | 0 | 0 | | (opt.) ☐ |
| [13 — Heap / PriorityQueue](#13--heap--priorityqueue) | 11 | 8 | 2 | 0 | 0 | | ☐ |
| [14 — Backtracking](#14--backtracking) | 12 | 8 | 2 | 0 | 0 | | ☐ |
| [15 — Graphs: BFS / DFS](#15--graphs-bfs--dfs) | 13 | 8 | 2 | 0 | 0 | | ☐ |
| [16 — Topological Sort](#16--topological-sort) | 14 | 4 | 1 | 0 | 0 | | ☐ |
| [17 — Union-Find](#17--union-find) | 14 | 4 | 1 | 0 | 0 | | (opt.) ☐ |
| [18 — Dynamic Programming 1-D](#18--dynamic-programming-1-d) | 15–16 | 11 | 2 | 0 | 0 | | ☐ |
| [19 — Intervals](#19--intervals) | 16 | 5 | 1 | 0 | 0 | | ☐ |
| [20 — Greedy](#20--greedy) | 17 | 5 | 3 | 0 | 0 | | ☐ |
| [21 — Dynamic Programming 2-D](#21--dynamic-programming-2-d) | 17–18 | 10 | 2 | 0 | 0 | | ☐ |
| [22 — Advanced Graphs](#22--advanced-graphs) | 19 | 4 | 2 | 0 | 0 | | ☐ |
| [23 — Bit Manipulation](#23--bit-manipulation) | 19 | 3 | 2 | 0 | 0 | | (opt.) ☐ |
| **Total** | 1–26 | **146** | **45** | **0** | **0** | | |

---

## 5. Problem tables

Columns: **#** LeetCode number · **Problem** (link) · **Diff** · **Week** assigned (`20–26` = stretch pool) · **Status** · **Attempts** · **Time** · **Last Review** · **Next Review** · **Java rep ✓** · **Notes** (missing insight + complexity). Premium problems are marked; use NeetCode's free versions.

### 01 — Arrays & Strings

Guide: [`03-dsa/01-arrays-strings.md`](../03-dsa/01-arrays-strings.md) · Weeks 1–2 · core **7** · Java rep: **189** (W1)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 1929 | [Concatenation of Array](https://leetcode.com/problems/concatenation-of-array/) | E | 1 | Not Started | 0 | | | |  | |
| 1768 | [Merge Strings Alternately](https://leetcode.com/problems/merge-strings-alternately/) | E | 1 | Not Started | 0 | | | |  | |
| 58 | [Length of Last Word](https://leetcode.com/problems/length-of-last-word/) | E | 1 | Not Started | 0 | | | |  | |
| 14 | [Longest Common Prefix](https://leetcode.com/problems/longest-common-prefix/) | E | 1 | Not Started | 0 | | | |  | |
| 169 | [Majority Element](https://leetcode.com/problems/majority-element/) | E | 1 | Not Started | 0 | | | |  | |
| 189 | [Rotate Array](https://leetcode.com/problems/rotate-array/) | M | 1 | Not Started | 0 | | | | ☐ | |
| 238 | [Product of Array Except Self](https://leetcode.com/problems/product-of-array-except-self/) | M | 2 | Not Started | 0 | | | |  | |

### 02 — HashMap / HashSet

Guide: [`03-dsa/02-hashing.md`](../03-dsa/02-hashing.md) · Week 2 · core **7** · Java rep: **49** (W2)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 217 | [Contains Duplicate](https://leetcode.com/problems/contains-duplicate/) | E | 2 | Not Started | 0 | | | |  | |
| 242 | [Valid Anagram](https://leetcode.com/problems/valid-anagram/) | E | 2 | Not Started | 0 | | | |  | |
| 1 | [Two Sum](https://leetcode.com/problems/two-sum/) | E | 2 | Not Started | 0 | | | |  | |
| 49 | [Group Anagrams](https://leetcode.com/problems/group-anagrams/) | M | 2 | Not Started | 0 | | | | ☐ | |
| 347 | [Top K Frequent Elements](https://leetcode.com/problems/top-k-frequent-elements/) | M | 2 | Not Started | 0 | | | |  | |
| 36 | [Valid Sudoku](https://leetcode.com/problems/valid-sudoku/) | M | 2 | Not Started | 0 | | | |  | |
| 128 | [Longest Consecutive Sequence](https://leetcode.com/problems/longest-consecutive-sequence/) | M | 2 | Not Started | 0 | | | |  | |
| 383 | [Ransom Note](https://leetcode.com/problems/ransom-note/) *(stretch)* | E | 20–26 | Not Started | 0 | | | |  | |
| 229 | [Majority Element II](https://leetcode.com/problems/majority-element-ii/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 380 | [Insert Delete GetRandom O(1)](https://leetcode.com/problems/insert-delete-getrandom-o1/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |

### 03 — Two Pointers

Guide: [`03-dsa/03-two-pointers.md`](../03-dsa/03-two-pointers.md) · Week 3 · core **5** · Java rep: **15** (W3 (optional — W3 rep is 560))

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 344 | [Reverse String](https://leetcode.com/problems/reverse-string/) | E | 3 | Not Started | 0 | | | |  | |
| 125 | [Valid Palindrome](https://leetcode.com/problems/valid-palindrome/) | E | 3 | Not Started | 0 | | | |  | |
| 167 | [Two Sum II - Input Array Is Sorted](https://leetcode.com/problems/two-sum-ii-input-array-is-sorted/) | M | 3 | Not Started | 0 | | | |  | |
| 15 | [3Sum](https://leetcode.com/problems/3sum/) | M | 3 | Not Started | 0 | | | | ☐ | |
| 11 | [Container With Most Water](https://leetcode.com/problems/container-with-most-water/) | M | 3 | Not Started | 0 | | | |  | |
| 88 | [Merge Sorted Array](https://leetcode.com/problems/merge-sorted-array/) *(stretch)* | E | 20–26 | Not Started | 0 | | | |  | |
| 18 | [4Sum](https://leetcode.com/problems/4sum/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 42 | [Trapping Rain Water](https://leetcode.com/problems/trapping-rain-water/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 04 — Prefix Sums

Guide: [`03-dsa/04-prefix-sums.md`](../03-dsa/04-prefix-sums.md) · Week 3 · core **3** · Java rep: **560** (W3)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 303 | [Range Sum Query - Immutable](https://leetcode.com/problems/range-sum-query-immutable/) | E | 3 | Not Started | 0 | | | |  | |
| 724 | [Find Pivot Index](https://leetcode.com/problems/find-pivot-index/) | E | 3 | Not Started | 0 | | | |  | |
| 560 | [Subarray Sum Equals K](https://leetcode.com/problems/subarray-sum-equals-k/) | M | 3 | Not Started | 0 | | | | ☐ | |
| 1480 | [Running Sum of 1d Array](https://leetcode.com/problems/running-sum-of-1d-array/) *(stretch)* | E | 20–26 | Not Started | 0 | | | |  | |
| 304 | [Range Sum Query 2D - Immutable](https://leetcode.com/problems/range-sum-query-2d-immutable/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |

### 05 — Sliding Window

Guide: [`03-dsa/05-sliding-window.md`](../03-dsa/05-sliding-window.md) · Week 4 · core **8** · Java rep: **3** (W4)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 643 | [Maximum Average Subarray I](https://leetcode.com/problems/maximum-average-subarray-i/) | E | 4 | Not Started | 0 | | | |  | |
| 121 | [Best Time to Buy and Sell Stock](https://leetcode.com/problems/best-time-to-buy-and-sell-stock/) | E | 4 | Not Started | 0 | | | |  | |
| 219 | [Contains Duplicate II](https://leetcode.com/problems/contains-duplicate-ii/) | E | 4 | Not Started | 0 | | | |  | |
| 3 | [Longest Substring Without Repeating Characters](https://leetcode.com/problems/longest-substring-without-repeating-characters/) | M | 4 | Not Started | 0 | | | | ☐ | |
| 424 | [Longest Repeating Character Replacement](https://leetcode.com/problems/longest-repeating-character-replacement/) | M | 4 | Not Started | 0 | | | |  | |
| 567 | [Permutation in String](https://leetcode.com/problems/permutation-in-string/) | M | 4 | Not Started | 0 | | | |  | |
| 1004 | [Max Consecutive Ones III](https://leetcode.com/problems/max-consecutive-ones-iii/) | M | 4 | Not Started | 0 | | | |  | |
| 76 | [Minimum Window Substring](https://leetcode.com/problems/minimum-window-substring/) | H | 4 | Not Started | 0 | | | |  | |
| 209 | [Minimum Size Subarray Sum](https://leetcode.com/problems/minimum-size-subarray-sum/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 239 | [Sliding Window Maximum](https://leetcode.com/problems/sliding-window-maximum/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 06 — Stack & Queue

Guide: [`03-dsa/06-stack-queue.md`](../03-dsa/06-stack-queue.md) · Week 5 · core **8** · Java rep: **739** (W5)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 20 | [Valid Parentheses](https://leetcode.com/problems/valid-parentheses/) | E | 5 | Not Started | 0 | | | |  | |
| 232 | [Implement Queue using Stacks](https://leetcode.com/problems/implement-queue-using-stacks/) | E | 5 | Not Started | 0 | | | |  | |
| 155 | [Min Stack](https://leetcode.com/problems/min-stack/) | M | 5 | Not Started | 0 | | | |  | |
| 150 | [Evaluate Reverse Polish Notation](https://leetcode.com/problems/evaluate-reverse-polish-notation/) | M | 5 | Not Started | 0 | | | |  | |
| 739 | [Daily Temperatures](https://leetcode.com/problems/daily-temperatures/) | M | 5 | Not Started | 0 | | | | ☐ | |
| 853 | [Car Fleet](https://leetcode.com/problems/car-fleet/) | M | 5 | Not Started | 0 | | | |  | |
| 901 | [Online Stock Span](https://leetcode.com/problems/online-stock-span/) | M | 5 | Not Started | 0 | | | |  | |
| 84 | [Largest Rectangle in Histogram](https://leetcode.com/problems/largest-rectangle-in-histogram/) | H | 5 | Not Started | 0 | | | |  | |
| 394 | [Decode String](https://leetcode.com/problems/decode-string/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 71 | [Simplify Path](https://leetcode.com/problems/simplify-path/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |

### 07 — Binary Search

Guide: [`03-dsa/07-binary-search.md`](../03-dsa/07-binary-search.md) · Week 6 · core **8** · Java rep: **875** (W6)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 704 | [Binary Search](https://leetcode.com/problems/binary-search/) | E | 6 | Not Started | 0 | | | |  | |
| 35 | [Search Insert Position](https://leetcode.com/problems/search-insert-position/) | E | 6 | Not Started | 0 | | | |  | |
| 74 | [Search a 2D Matrix](https://leetcode.com/problems/search-a-2d-matrix/) | M | 6 | Not Started | 0 | | | |  | |
| 875 | [Koko Eating Bananas](https://leetcode.com/problems/koko-eating-bananas/) | M | 6 | Not Started | 0 | | | | ☐ | |
| 153 | [Find Minimum in Rotated Sorted Array](https://leetcode.com/problems/find-minimum-in-rotated-sorted-array/) | M | 6 | Not Started | 0 | | | |  | |
| 33 | [Search in Rotated Sorted Array](https://leetcode.com/problems/search-in-rotated-sorted-array/) | M | 6 | Not Started | 0 | | | |  | |
| 981 | [Time Based Key-Value Store](https://leetcode.com/problems/time-based-key-value-store/) | M | 6 | Not Started | 0 | | | |  | |
| 1011 | [Capacity To Ship Packages Within D Days](https://leetcode.com/problems/capacity-to-ship-packages-within-d-days/) | M | 6 | Not Started | 0 | | | |  | |
| 162 | [Find Peak Element](https://leetcode.com/problems/find-peak-element/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 4 | [Median of Two Sorted Arrays](https://leetcode.com/problems/median-of-two-sorted-arrays/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 08 — Linked Lists

Guide: [`03-dsa/08-linked-lists.md`](../03-dsa/08-linked-lists.md) · Week 7 · core **8** · Java rep: **146** (W7)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 206 | [Reverse Linked List](https://leetcode.com/problems/reverse-linked-list/) | E | 7 | Not Started | 0 | | | |  | |
| 21 | [Merge Two Sorted Lists](https://leetcode.com/problems/merge-two-sorted-lists/) | E | 7 | Not Started | 0 | | | |  | |
| 141 | [Linked List Cycle](https://leetcode.com/problems/linked-list-cycle/) | E | 7 | Not Started | 0 | | | |  | |
| 143 | [Reorder List](https://leetcode.com/problems/reorder-list/) | M | 7 | Not Started | 0 | | | |  | |
| 19 | [Remove Nth Node From End of List](https://leetcode.com/problems/remove-nth-node-from-end-of-list/) | M | 7 | Not Started | 0 | | | |  | |
| 2 | [Add Two Numbers](https://leetcode.com/problems/add-two-numbers/) | M | 7 | Not Started | 0 | | | |  | |
| 138 | [Copy List with Random Pointer](https://leetcode.com/problems/copy-list-with-random-pointer/) | M | 7 | Not Started | 0 | | | |  | |
| 146 | [LRU Cache](https://leetcode.com/problems/lru-cache/) | M | 7 | Not Started | 0 | | | | ☐ | |
| 876 | [Middle of the Linked List](https://leetcode.com/problems/middle-of-the-linked-list/) *(stretch)* | E | 20–26 | Not Started | 0 | | | |  | |
| 287 | [Find the Duplicate Number](https://leetcode.com/problems/find-the-duplicate-number/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 25 | [Reverse Nodes in k-Group](https://leetcode.com/problems/reverse-nodes-in-k-group/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 09 — Recursion

Guide: [`03-dsa/09-recursion.md`](../03-dsa/09-recursion.md) · Week 8 · core **6** · Java rep: **50** (W8)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 509 | [Fibonacci Number](https://leetcode.com/problems/fibonacci-number/) | E | 8 | Not Started | 0 | | | |  | |
| 203 | [Remove Linked List Elements](https://leetcode.com/problems/remove-linked-list-elements/) | E | 8 | Not Started | 0 | | | |  | |
| 231 | [Power of Two](https://leetcode.com/problems/power-of-two/) | E | 8 | Not Started | 0 | | | |  | |
| 24 | [Swap Nodes in Pairs](https://leetcode.com/problems/swap-nodes-in-pairs/) | M | 8 | Not Started | 0 | | | |  | |
| 50 | [Pow(x, n)](https://leetcode.com/problems/powx-n/) | M | 8 | Not Started | 0 | | | | ☐ | |
| 779 | [K-th Symbol in Grammar](https://leetcode.com/problems/k-th-symbol-in-grammar/) | M | 8 | Not Started | 0 | | | |  | |

### 10 — Trees

Guide: [`03-dsa/10-trees.md`](../03-dsa/10-trees.md) · Week 9 · core **8** · Java rep: **102** (W9)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 104 | [Maximum Depth of Binary Tree](https://leetcode.com/problems/maximum-depth-of-binary-tree/) | E | 9 | Not Started | 0 | | | |  | |
| 226 | [Invert Binary Tree](https://leetcode.com/problems/invert-binary-tree/) | E | 9 | Not Started | 0 | | | |  | |
| 100 | [Same Tree](https://leetcode.com/problems/same-tree/) | E | 9 | Not Started | 0 | | | |  | |
| 543 | [Diameter of Binary Tree](https://leetcode.com/problems/diameter-of-binary-tree/) | E | 9 | Not Started | 0 | | | |  | |
| 110 | [Balanced Binary Tree](https://leetcode.com/problems/balanced-binary-tree/) | E | 9 | Not Started | 0 | | | |  | |
| 102 | [Binary Tree Level Order Traversal](https://leetcode.com/problems/binary-tree-level-order-traversal/) | M | 9 | Not Started | 0 | | | | ☐ | |
| 199 | [Binary Tree Right Side View](https://leetcode.com/problems/binary-tree-right-side-view/) | M | 9 | Not Started | 0 | | | |  | |
| 1448 | [Count Good Nodes in Binary Tree](https://leetcode.com/problems/count-good-nodes-in-binary-tree/) | M | 9 | Not Started | 0 | | | |  | |
| 94 | [Binary Tree Inorder Traversal](https://leetcode.com/problems/binary-tree-inorder-traversal/) *(stretch)* | E | 20–26 | Not Started | 0 | | | |  | |
| 572 | [Subtree of Another Tree](https://leetcode.com/problems/subtree-of-another-tree/) *(stretch)* | E | 20–26 | Not Started | 0 | | | |  | |
| 105 | [Construct Binary Tree from Preorder and Inorder Traversal](https://leetcode.com/problems/construct-binary-tree-from-preorder-and-inorder-traversal/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 124 | [Binary Tree Maximum Path Sum](https://leetcode.com/problems/binary-tree-maximum-path-sum/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |
| 297 | [Serialize and Deserialize Binary Tree](https://leetcode.com/problems/serialize-and-deserialize-binary-tree/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 11 — Binary Search Trees

Guide: [`03-dsa/11-bst.md`](../03-dsa/11-bst.md) · Week 10 · core **5** · Java rep: **98** (W10)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 700 | [Search in a Binary Search Tree](https://leetcode.com/problems/search-in-a-binary-search-tree/) | E | 10 | Not Started | 0 | | | |  | |
| 108 | [Convert Sorted Array to Binary Search Tree](https://leetcode.com/problems/convert-sorted-array-to-binary-search-tree/) | E | 10 | Not Started | 0 | | | |  | |
| 235 | [Lowest Common Ancestor of a Binary Search Tree](https://leetcode.com/problems/lowest-common-ancestor-of-a-binary-search-tree/) | M | 10 | Not Started | 0 | | | |  | |
| 98 | [Validate Binary Search Tree](https://leetcode.com/problems/validate-binary-search-tree/) | M | 10 | Not Started | 0 | | | | ☐ | |
| 230 | [Kth Smallest Element in a BST](https://leetcode.com/problems/kth-smallest-element-in-a-bst/) | M | 10 | Not Started | 0 | | | |  | |
| 701 | [Insert into a Binary Search Tree](https://leetcode.com/problems/insert-into-a-binary-search-tree/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 450 | [Delete Node in a BST](https://leetcode.com/problems/delete-node-in-a-bst/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |

### 12 — Tries

Guide: [`03-dsa/12-tries.md`](../03-dsa/12-tries.md) · Week 10 · core **3** · Java rep: **208** (W10 (optional — W10 rep is 98))

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 208 | [Implement Trie (Prefix Tree)](https://leetcode.com/problems/implement-trie-prefix-tree/) | M | 10 | Not Started | 0 | | | | ☐ | |
| 648 | [Replace Words](https://leetcode.com/problems/replace-words/) | M | 10 | Not Started | 0 | | | |  | |
| 211 | [Design Add and Search Words Data Structure](https://leetcode.com/problems/design-add-and-search-words-data-structure/) | M | 10 | Not Started | 0 | | | |  | |
| 212 | [Word Search II](https://leetcode.com/problems/word-search-ii/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 13 — Heap / PriorityQueue

Guide: [`03-dsa/13-heap-priority-queue.md`](../03-dsa/13-heap-priority-queue.md) · Week 11 · core **8** · Java rep: **973** (W11)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 703 | [Kth Largest Element in a Stream](https://leetcode.com/problems/kth-largest-element-in-a-stream/) | E | 11 | Not Started | 0 | | | |  | |
| 1046 | [Last Stone Weight](https://leetcode.com/problems/last-stone-weight/) | E | 11 | Not Started | 0 | | | |  | |
| 973 | [K Closest Points to Origin](https://leetcode.com/problems/k-closest-points-to-origin/) | M | 11 | Not Started | 0 | | | | ☐ | |
| 215 | [Kth Largest Element in an Array](https://leetcode.com/problems/kth-largest-element-in-an-array/) | M | 11 | Not Started | 0 | | | |  | |
| 621 | [Task Scheduler](https://leetcode.com/problems/task-scheduler/) | M | 11 | Not Started | 0 | | | |  | |
| 355 | [Design Twitter](https://leetcode.com/problems/design-twitter/) | M | 11 | Not Started | 0 | | | |  | |
| 295 | [Find Median from Data Stream](https://leetcode.com/problems/find-median-from-data-stream/) | H | 11 | Not Started | 0 | | | |  | |
| 23 | [Merge k Sorted Lists](https://leetcode.com/problems/merge-k-sorted-lists/) | H | 11 | Not Started | 0 | | | |  | |
| 767 | [Reorganize String](https://leetcode.com/problems/reorganize-string/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 1834 | [Single-Threaded CPU](https://leetcode.com/problems/single-threaded-cpu/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |

### 14 — Backtracking

Guide: [`03-dsa/14-backtracking.md`](../03-dsa/14-backtracking.md) · Week 12 · core **8** · Java rep: **78** (W12)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 78 | [Subsets](https://leetcode.com/problems/subsets/) | M | 12 | Not Started | 0 | | | | ☐ | |
| 22 | [Generate Parentheses](https://leetcode.com/problems/generate-parentheses/) | M | 12 | Not Started | 0 | | | |  | |
| 39 | [Combination Sum](https://leetcode.com/problems/combination-sum/) | M | 12 | Not Started | 0 | | | |  | |
| 46 | [Permutations](https://leetcode.com/problems/permutations/) | M | 12 | Not Started | 0 | | | |  | |
| 90 | [Subsets II](https://leetcode.com/problems/subsets-ii/) | M | 12 | Not Started | 0 | | | |  | |
| 40 | [Combination Sum II](https://leetcode.com/problems/combination-sum-ii/) | M | 12 | Not Started | 0 | | | |  | |
| 79 | [Word Search](https://leetcode.com/problems/word-search/) | M | 12 | Not Started | 0 | | | |  | |
| 131 | [Palindrome Partitioning](https://leetcode.com/problems/palindrome-partitioning/) | M | 12 | Not Started | 0 | | | |  | |
| 17 | [Letter Combinations of a Phone Number](https://leetcode.com/problems/letter-combinations-of-a-phone-number/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 51 | [N-Queens](https://leetcode.com/problems/n-queens/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 15 — Graphs: BFS / DFS

Guide: [`03-dsa/15-graphs-bfs-dfs.md`](../03-dsa/15-graphs-bfs-dfs.md) · Week 13 · core **8** · Java rep: **994** (W13)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 733 | [Flood Fill](https://leetcode.com/problems/flood-fill/) | E | 13 | Not Started | 0 | | | |  | |
| 1971 | [Find if Path Exists in Graph](https://leetcode.com/problems/find-if-path-exists-in-graph/) | E | 13 | Not Started | 0 | | | |  | |
| 200 | [Number of Islands](https://leetcode.com/problems/number-of-islands/) | M | 13 | Not Started | 0 | | | |  | |
| 695 | [Max Area of Island](https://leetcode.com/problems/max-area-of-island/) | M | 13 | Not Started | 0 | | | |  | |
| 133 | [Clone Graph](https://leetcode.com/problems/clone-graph/) | M | 13 | Not Started | 0 | | | |  | |
| 994 | [Rotting Oranges](https://leetcode.com/problems/rotting-oranges/) | M | 13 | Not Started | 0 | | | | ☐ | |
| 417 | [Pacific Atlantic Water Flow](https://leetcode.com/problems/pacific-atlantic-water-flow/) | M | 13 | Not Started | 0 | | | |  | |
| 130 | [Surrounded Regions](https://leetcode.com/problems/surrounded-regions/) | M | 13 | Not Started | 0 | | | |  | |
| 286 | [Walls and Gates (Premium — NeetCode: Islands and Treasure)](https://leetcode.com/problems/walls-and-gates/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 127 | [Word Ladder](https://leetcode.com/problems/word-ladder/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 16 — Topological Sort

Guide: [`03-dsa/16-topological-sort.md`](../03-dsa/16-topological-sort.md) · Week 14 · core **4** · Java rep: **207** (W14)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 207 | [Course Schedule](https://leetcode.com/problems/course-schedule/) | M | 14 | Not Started | 0 | | | | ☐ | |
| 210 | [Course Schedule II](https://leetcode.com/problems/course-schedule-ii/) | M | 14 | Not Started | 0 | | | |  | |
| 802 | [Find Eventual Safe States](https://leetcode.com/problems/find-eventual-safe-states/) | M | 14 | Not Started | 0 | | | |  | |
| 1462 | [Course Schedule IV](https://leetcode.com/problems/course-schedule-iv/) | M | 14 | Not Started | 0 | | | |  | |
| 269 | [Alien Dictionary (Premium — free on NeetCode)](https://leetcode.com/problems/alien-dictionary/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 17 — Union-Find

Guide: [`03-dsa/17-union-find.md`](../03-dsa/17-union-find.md) · Week 14 · core **4** · Java rep: **684** (W14 (optional — W14 rep is 207))

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 547 | [Number of Provinces](https://leetcode.com/problems/number-of-provinces/) | M | 14 | Not Started | 0 | | | |  | |
| 684 | [Redundant Connection](https://leetcode.com/problems/redundant-connection/) | M | 14 | Not Started | 0 | | | | ☐ | |
| 323 | [Number of Connected Components in an Undirected Graph (Premium — free on NeetCode)](https://leetcode.com/problems/number-of-connected-components-in-an-undirected-graph/) | M | 14 | Not Started | 0 | | | |  | |
| 261 | [Graph Valid Tree (Premium — free on NeetCode)](https://leetcode.com/problems/graph-valid-tree/) | M | 14 | Not Started | 0 | | | |  | |
| 721 | [Accounts Merge](https://leetcode.com/problems/accounts-merge/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |

### 18 — Dynamic Programming 1-D

Guide: [`03-dsa/18-dp-1d.md`](../03-dsa/18-dp-1d.md) · Weeks 15–16 · core **11** · Java rep: **198** (W15)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 70 | [Climbing Stairs](https://leetcode.com/problems/climbing-stairs/) | E | 15 | Not Started | 0 | | | |  | |
| 746 | [Min Cost Climbing Stairs](https://leetcode.com/problems/min-cost-climbing-stairs/) | E | 15 | Not Started | 0 | | | |  | |
| 1137 | [N-th Tribonacci Number](https://leetcode.com/problems/n-th-tribonacci-number/) | E | 15 | Not Started | 0 | | | |  | |
| 198 | [House Robber](https://leetcode.com/problems/house-robber/) | M | 15 | Not Started | 0 | | | | ☐ | |
| 213 | [House Robber II](https://leetcode.com/problems/house-robber-ii/) | M | 15 | Not Started | 0 | | | |  | |
| 5 | [Longest Palindromic Substring](https://leetcode.com/problems/longest-palindromic-substring/) | M | 15 | Not Started | 0 | | | |  | |
| 647 | [Palindromic Substrings](https://leetcode.com/problems/palindromic-substrings/) | M | 15 | Not Started | 0 | | | |  | |
| 91 | [Decode Ways](https://leetcode.com/problems/decode-ways/) | M | 15 | Not Started | 0 | | | |  | |
| 322 | [Coin Change](https://leetcode.com/problems/coin-change/) | M | 16 | Not Started | 0 | | | |  | |
| 139 | [Word Break](https://leetcode.com/problems/word-break/) | M | 16 | Not Started | 0 | | | |  | |
| 300 | [Longest Increasing Subsequence](https://leetcode.com/problems/longest-increasing-subsequence/) | M | 16 | Not Started | 0 | | | |  | |
| 152 | [Maximum Product Subarray](https://leetcode.com/problems/maximum-product-subarray/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 416 | [Partition Equal Subset Sum](https://leetcode.com/problems/partition-equal-subset-sum/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |

### 19 — Intervals

Guide: [`03-dsa/19-intervals.md`](../03-dsa/19-intervals.md) · Week 16 · core **5** · Java rep: **56** (W16)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 252 | [Meeting Rooms (Premium — free on NeetCode)](https://leetcode.com/problems/meeting-rooms/) | E | 16 | Not Started | 0 | | | |  | |
| 56 | [Merge Intervals](https://leetcode.com/problems/merge-intervals/) | M | 16 | Not Started | 0 | | | | ☐ | |
| 57 | [Insert Interval](https://leetcode.com/problems/insert-interval/) | M | 16 | Not Started | 0 | | | |  | |
| 435 | [Non-overlapping Intervals](https://leetcode.com/problems/non-overlapping-intervals/) | M | 16 | Not Started | 0 | | | |  | |
| 253 | [Meeting Rooms II (Premium — free on NeetCode)](https://leetcode.com/problems/meeting-rooms-ii/) | M | 16 | Not Started | 0 | | | |  | |
| 452 | [Minimum Number of Arrows to Burst Balloons](https://leetcode.com/problems/minimum-number-of-arrows-to-burst-balloons/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |

### 20 — Greedy

Guide: [`03-dsa/20-greedy.md`](../03-dsa/20-greedy.md) · Week 17 · core **5** · Java rep: **763** (W17)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 860 | [Lemonade Change](https://leetcode.com/problems/lemonade-change/) | E | 17 | Not Started | 0 | | | |  | |
| 53 | [Maximum Subarray](https://leetcode.com/problems/maximum-subarray/) | M | 17 | Not Started | 0 | | | |  | |
| 55 | [Jump Game](https://leetcode.com/problems/jump-game/) | M | 17 | Not Started | 0 | | | |  | |
| 45 | [Jump Game II](https://leetcode.com/problems/jump-game-ii/) | M | 17 | Not Started | 0 | | | |  | |
| 763 | [Partition Labels](https://leetcode.com/problems/partition-labels/) | M | 17 | Not Started | 0 | | | | ☐ | |
| 134 | [Gas Station](https://leetcode.com/problems/gas-station/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 846 | [Hand of Straights](https://leetcode.com/problems/hand-of-straights/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |
| 678 | [Valid Parenthesis String](https://leetcode.com/problems/valid-parenthesis-string/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |

### 21 — Dynamic Programming 2-D

Guide: [`03-dsa/21-dp-2d.md`](../03-dsa/21-dp-2d.md) · Weeks 17–18 · core **10** · Java rep: **1143** (W18)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 62 | [Unique Paths](https://leetcode.com/problems/unique-paths/) | M | 17 | Not Started | 0 | | | |  | |
| 63 | [Unique Paths II](https://leetcode.com/problems/unique-paths-ii/) | M | 17 | Not Started | 0 | | | |  | |
| 64 | [Minimum Path Sum](https://leetcode.com/problems/minimum-path-sum/) | M | 17 | Not Started | 0 | | | |  | |
| 1143 | [Longest Common Subsequence](https://leetcode.com/problems/longest-common-subsequence/) | M | 18 | Not Started | 0 | | | | ☐ | |
| 518 | [Coin Change II](https://leetcode.com/problems/coin-change-ii/) | M | 18 | Not Started | 0 | | | |  | |
| 494 | [Target Sum](https://leetcode.com/problems/target-sum/) | M | 18 | Not Started | 0 | | | |  | |
| 309 | [Best Time to Buy and Sell Stock with Cooldown](https://leetcode.com/problems/best-time-to-buy-and-sell-stock-with-cooldown/) | M | 18 | Not Started | 0 | | | |  | |
| 97 | [Interleaving String](https://leetcode.com/problems/interleaving-string/) | M | 18 | Not Started | 0 | | | |  | |
| 72 | [Edit Distance](https://leetcode.com/problems/edit-distance/) | M | 18 | Not Started | 0 | | | |  | |
| 221 | [Maximal Square](https://leetcode.com/problems/maximal-square/) | M | 18 | Not Started | 0 | | | |  | |
| 329 | [Longest Increasing Path in a Matrix](https://leetcode.com/problems/longest-increasing-path-in-a-matrix/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |
| 115 | [Distinct Subsequences](https://leetcode.com/problems/distinct-subsequences/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 22 — Advanced Graphs

Guide: [`03-dsa/22-advanced-graphs.md`](../03-dsa/22-advanced-graphs.md) · Week 19 · core **4** · Java rep: **743** (W19)

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 743 | [Network Delay Time](https://leetcode.com/problems/network-delay-time/) | M | 19 | Not Started | 0 | | | | ☐ | |
| 1631 | [Path With Minimum Effort](https://leetcode.com/problems/path-with-minimum-effort/) | M | 19 | Not Started | 0 | | | |  | |
| 1584 | [Min Cost to Connect All Points](https://leetcode.com/problems/min-cost-to-connect-all-points/) | M | 19 | Not Started | 0 | | | |  | |
| 787 | [Cheapest Flights Within K Stops](https://leetcode.com/problems/cheapest-flights-within-k-stops/) | M | 19 | Not Started | 0 | | | |  | |
| 778 | [Swim in Rising Water](https://leetcode.com/problems/swim-in-rising-water/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |
| 332 | [Reconstruct Itinerary](https://leetcode.com/problems/reconstruct-itinerary/) *(stretch)* | H | 20–26 | Not Started | 0 | | | |  | |

### 23 — Bit Manipulation

Guide: [`03-dsa/23-bit-manipulation.md`](../03-dsa/23-bit-manipulation.md) · Week 19 · core **3** · Java rep: **191** (W19 (optional — W19 rep is 743))

| # | Problem | Diff | Week | Status | Attempts | Time | Last Review | Next Review | Java rep ✓ | Notes |
|---:|---|---|---|---|---:|---|---|---|:---:|---|
| 136 | [Single Number](https://leetcode.com/problems/single-number/) | E | 19 | Not Started | 0 | | | |  | |
| 191 | [Number of 1 Bits](https://leetcode.com/problems/number-of-1-bits/) | E | 19 | Not Started | 0 | | | | ☐ | |
| 338 | [Counting Bits](https://leetcode.com/problems/counting-bits/) | E | 19 | Not Started | 0 | | | |  | |
| 268 | [Missing Number](https://leetcode.com/problems/missing-number/) *(stretch)* | E | 20–26 | Not Started | 0 | | | |  | |
| 371 | [Sum of Two Integers](https://leetcode.com/problems/sum-of-two-integers/) *(stretch)* | M | 20–26 | Not Started | 0 | | | |  | |

Diff: E = Easy, M = Medium, H = Hard.

---

## 6. Weeks 20–26: mixed / timed plan

New problems in these weeks come from the **stretch rows** above (45 available, 39 needed), chosen by weakness — not in file order.

| Week | New | Source | Mode |
|---:|---:|---|---|
| 20 | 6 | Stretch rows of your 3 lowest-confidence patterns | Untimed first attempt, then timed Day-3 review |
| 21 | 6 | Stretch rows, next-weakest patterns | Same |
| 22 | 6 | Stretch rows — include the Hards you haven't touched (42, 239, 25, 124, 297, 4, 212, 51, 127, 269, 329, 115, 778, 332) | Same |
| 23 | 6 | Mixed stretch rows | **Timed**: Medium 25 min, Hard 40 min; think aloud |
| 24 | 6 + OA | Mixed stretch rows + OA simulations ([`OA_PREP.md`](../OA_PREP.md)) | Timed, OA conditions (70–120 min) |
| 25 | 5 | Whatever [Checkpoint 24](../checkpoints/checkpoint-24.md) flagged | Timed |
| 26 | 4 | Remaining stretch rows / weakest | Then switch to the [maintenance plan](../16-interview-prep/maintenance-plan.md) |

---

## 7. Review queue

Rebuild every Sunday from the `Next Review` column (sort ascending; everything ≤ next Sunday goes in). Work top-down; overdue first.

| Due date | # | Problem | Pattern | Review (D3 / D7 / D14 / D30) | Result (pass / fail) | Time | New status | Next review |
|---|---:|---|---|---|---|---|---|---|
| | | | | | | | | |
| | | | | | | | | |
| | | | | | | | | |
| | | | | | | | | |
| | | | | | | | | |
| | | | | | | | | |

Queue rules: max ~6 reviews/day; a failed review goes back in at +3 days; Day-30 reviews are always timed.

---

## 8. Weekly DSA log template

Copy this block into [`weekly-progress.md`](./weekly-progress.md) (or your notes) every Sunday.

```
Week:            (1–26)            Pattern(s):
Planned new:     (from ROADMAP §5)  Actually solved new:
Status mix:      Independently __ · With Hint __ · With Solution __ · Attempted __
Reviews due:     __   done: __   passed: __   failed (→ Needs Review): __
Mastered so far: __ / 185
Java rep:        (problem #, done ✓/✗, minutes, Java trap hit — e.g. Integer ==, overflow, forgot new ArrayList<>(path))
Timed check:     (Sunday: 2 random problems from this week — times)
Explained aloud: (which problem, stumbled where?)
Weakest pattern: (lowest confidence in §4)  → next week's extra review focus
Python pitfall hit this week: (mutable default, aliasing, pop(0), recursion limit, heapq tuple compare, ...)
Next week:       (pattern, first problem, reviews already due)
```
