# 20 — Greedy

> **Week 17** · NeetCode section: **Greedy** · Target: **5 new problems** + stretch pool · Java rep: **763** (Week 17)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#20--greedy) · Python: [cheatsheet](../PYTHON_INTERVIEW_CHEATSHEET.md) · Java: [quick reference](./java-dsa-toolkit.md)

A greedy algorithm makes the locally best choice at each step and **never revisits it**. When it works it's the simplest and fastest solution (usually O(n) or O(n log n)); when it doesn't, it's confidently wrong. The interview skill is twofold: spotting the greedy choice, and **justifying** it (exchange argument or "stays ahead"), or producing a counterexample that sends you to DP instead.

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| Greedy choice property | Some optimal solution starts with the greedy choice |
| Exchange argument | Take any optimal solution; swap its first choice for the greedy one; show it's no worse → greedy is optimal |
| "Stays ahead" | After each step, greedy's partial solution is at least as good as any other's (e.g. reachable index in 55) |
| Counterexample hunting | Before coding a greedy, try to break it with 2–3 tiny inputs. Coin change `[1, 3, 4]`, amount 6 breaks "largest coin first" → DP ([18](./18-dp-1d.md)) |
| Kadane's algorithm | Max subarray: extend the current run or restart at x → `cur = max(x, cur + x)` |
| Farthest reach | Jump games: track the farthest index reachable so far |
| BFS-by-levels greedy | 45: each "level" = the range reachable with k jumps |
| Last occurrence | 763: a partition can close only after the last occurrence of every char inside it |
| Balance / slack tracking | 134 (total gas ≥ total cost; restart after a deficit), 678 (range of possible open counts) |
| Sorting first | Many greedies are "sort by X, then scan" (intervals by end — [19](./19-intervals.md); 846 by value) |

---

## 2. Prerequisite knowledge

- [`18-dp-1d.md`](./18-dp-1d.md) — greedy is DP where only one sub-choice ever matters; know the DP to compare.
- [`19-intervals.md`](./19-intervals.md) — "earliest end first" is the classic proven greedy.
- [`02-hashing.md`](./02-hashing.md) — `Counter` for 846; last-index dict for 763.

---

## 3. Python implementation

```python
from collections import Counter


def max_subarray(nums: list[int]) -> int:
    """Kadane: best subarray ending here is either x alone or x + best ending at the previous index."""
    best = cur = nums[0]
    for x in nums[1:]:
        cur = max(x, cur + x)
        best = max(best, cur)
    return best


def can_jump(nums: list[int]) -> bool:
    reach = 0                                   # farthest index reachable so far ("stays ahead")
    for i, step in enumerate(nums):
        if i > reach:
            return False                        # stuck before i
        reach = max(reach, i + step)
    return True


def min_jumps(nums: list[int]) -> int:
    jumps = cur_end = farthest = 0              # BFS levels without a queue
    for i in range(len(nums) - 1):
        farthest = max(farthest, i + nums[i])
        if i == cur_end:                        # finished scanning this level
            jumps += 1
            cur_end = farthest
    return jumps


def partition_labels(s: str) -> list[int]:
    last = {ch: i for i, ch in enumerate(s)}    # later indices overwrite → last occurrence
    sizes, start, end = [], 0, 0
    for i, ch in enumerate(s):
        end = max(end, last[ch])
        if i == end:                            # every char seen so far ends inside [start, end]
            sizes.append(end - start + 1)
            start = i + 1
    return sizes


def can_complete_circuit(gas: list[int], cost: list[int]) -> int:
    if sum(gas) < sum(cost):
        return -1
    start = tank = 0
    for i, (g, c) in enumerate(zip(gas, cost)):
        tank += g - c
        if tank < 0:                            # can't reach i + 1 from any start in [start, i]
            start, tank = i + 1, 0
    return start


def is_n_straight_hand(hand: list[int], k: int) -> bool:
    if len(hand) % k:
        return False
    count = Counter(hand)
    for x in sorted(count):                     # smallest remaining card must start a group
        c = count[x]
        if c:
            for v in range(x, x + k):
                if count[v] < c:
                    return False
                count[v] -= c
    return True


assert max_subarray([-2, 1, -3, 4, -1, 2, 1, -5, 4]) == 6 and max_subarray([-3, -1]) == -1
assert can_jump([2, 3, 1, 1, 4]) and not can_jump([3, 2, 1, 0, 4])
assert min_jumps([2, 3, 1, 1, 4]) == 2 and min_jumps([0]) == 0
assert partition_labels("ababcbacadefegdehijhklij") == [9, 7, 8]
assert can_complete_circuit([1, 2, 3, 4, 5], [3, 4, 5, 1, 2]) == 3 and can_complete_circuit([2, 3, 4], [3, 4, 3]) == -1
assert is_n_straight_hand([1, 2, 3, 6, 2, 3, 4, 7, 8], 3) and not is_n_straight_hand([1, 2, 3, 4, 5], 4)
print("greedy templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 17): 763 Partition Labels.**

| Python | Java |
|---|---|
| `{ch: i for i, ch in enumerate(s)}` | `int[] last = new int[26]; for (int i = 0; i < s.length(); i++) last[s.charAt(i) - 'a'] = i;` |
| `sizes.append(...)` | `List<Integer> sizes = new ArrayList<>(); sizes.add(...)` |
| `Counter` + `sorted(count)` (846) | `TreeMap<Integer, Integer>` with `firstKey()` |
| `max(x, cur + x)` | `Math.max(x, cur + x)` (use `long` if sums can exceed `int`) |

```java
import java.util.*;

class PartitionLabelsRep {
    static List<Integer> partitionLabels(String s) {
        int[] last = new int[26];
        for (int i = 0; i < s.length(); i++) last[s.charAt(i) - 'a'] = i;
        List<Integer> sizes = new ArrayList<>();
        int start = 0, end = 0;
        for (int i = 0; i < s.length(); i++) {
            end = Math.max(end, last[s.charAt(i) - 'a']);
            if (i == end) { sizes.add(end - start + 1); start = i + 1; }
        }
        return sizes;
    }

    public static void main(String[] args) {
        System.out.println(partitionLabels("ababcbacadefegdehijhklij"));   // [9, 7, 8]
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Greedy choice | Problems |
|---|---|---|
| Local transaction rules | give change with the largest bills first ($10 before $5) | 860 |
| Kadane | restart when the running sum hurts | 53 |
| Farthest reach | extend the reachable frontier | 55 |
| Level-by-level reach | jump when the current level is exhausted | 45 |
| Close a segment at the last occurrence | extend `end` to the last index of every char seen | 763 |
| Deficit restart | start after the point where the tank went negative | 134 |
| Smallest-first grouping | smallest remaining card must start a group | 846 |
| Range of possibilities | track `[lo, hi]` of open-bracket counts with `*` | 678 |
| Sort by end | [19](./19-intervals.md): 435, 452 | — |

---

## 6. How to recognise the pattern

- "Minimum number of jumps/arrows/steps", "can you reach", "maximum profit with unlimited transactions", "partition into as many parts as possible".
- A natural ordering exists (by end time, by value, by position) and processing in that order lets you decide each item once.
- The DP recurrence you'd write always picks the same branch (e.g. `max` is always the "extend" option) → greedy.
- n up to 10⁵ and the expected complexity is O(n) or O(n log n) with no obvious DP table.
- **Warning signs it's not greedy:** small counterexamples break it; "number of ways" (counting needs DP); weights on items with a capacity (knapsack).

---

## 7. Beginner problems (Week 17)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 860 | [Lemonade Change](https://leetcode.com/problems/lemonade-change/) | Easy | Count $5 and $10 bills; for $20 prefer giving $10 + $5 over three $5s (keeps flexibility). |
| 53 | [Maximum Subarray](https://leetcode.com/problems/maximum-subarray/) | Medium | Kadane: `cur = max(x, cur + x)`. Initialise with `nums[0]` (all-negative arrays). |

---

## 8. Interview problems (Week 17)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 55 | [Jump Game](https://leetcode.com/problems/jump-game/) | Medium | 17 | <details><summary>show</summary>Track the farthest reachable index; if `i > reach` you're stuck. (Or walk backwards moving the "goal" left.)</details> |
| 45 | [Jump Game II](https://leetcode.com/problems/jump-game-ii/) | Medium | 17 | <details><summary>show</summary>Implicit BFS: `cur_end` = end of the current jump's range; when `i` reaches it, jump (count += 1) and set `cur_end = farthest`. Loop to `n - 2`.</details> |
| 763 | [Partition Labels](https://leetcode.com/problems/partition-labels/) | Medium | 17 | <details><summary>show</summary>Last index per char; extend `end` to the max last index seen; cut when `i == end`.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 134 | [Gas Station](https://leetcode.com/problems/gas-station/) | Medium | <details><summary>show</summary>If total gas ≥ total cost a solution exists; whenever the running tank goes negative at i, no start in `[start, i]` works → start = i + 1.</details> |
| 846 | [Hand of Straights](https://leetcode.com/problems/hand-of-straights/) | Medium | <details><summary>show</summary>`Counter`; process values in sorted order; the smallest remaining card must begin a run of k consecutive values.</details> |
| 678 | [Valid Parenthesis String](https://leetcode.com/problems/valid-parenthesis-string/) | Medium | <details><summary>show</summary>Track `lo`/`hi` = min/max possible open count; `*` widens the range; if `hi < 0` fail; clamp `lo` at 0; valid iff `lo == 0` at the end.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Greedy**: 53 → 55 → 45 → 134 → 846 → (1899 Merge Triplets to Form Target Triplet — optional, not tracked) → 763 → 678.
Here: §3 from memory → 860 → 53 → 55 → 45 → 763 (Week 17, before the 2-D DP problems) → Java rep 763 → (W20–26) 134, 846, 678.

---

## 10. Target number of problems

**5 new** in Week 17 (+ 3 2-D DP = 8) + 3 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Kadane initialised with `best = 0` → wrong for all-negative input; use `nums[0]` (or `float("-inf")`).
- `for x in nums[1:]` copies the list (O(n) extra) — fine, but `for i in range(1, len(nums))` avoids it if memory matters.
- `sorted(count)` on a `Counter` iterates keys only (good); `count.most_common()` sorts by frequency (not what 846 needs).
- `max(...)` over an empty sequence → `ValueError`; guard or pass `default=`.
- Building `last = {ch: i for ...}` twice in a loop (O(n²)); build once.

**General**
- Coding a greedy without a proof or at least a counterexample search.
- 45: looping to `n - 1` inclusive → counts an extra jump when you land exactly on the end.
- 55/45: confusing "can reach" (55) with "minimum jumps" (45).
- 134: trying every start (O(n²)).
- 860: giving three $5s when a $10 + $5 is available.

**Java-rep traps (763):** `int[26]` indexing with `charAt(i) - 'a'`; `List<Integer>` return type.

---

## 12. Mastery criteria

- [ ] For each of 53, 55, 45, 763: state the greedy choice and a one-paragraph justification (exchange or stays-ahead).
- [ ] Produce a counterexample showing why "largest coin first" fails for coin change.
- [ ] Solve 45 in ≤ 15 min and 763 in ≤ 12 min from blank.
- [ ] Solve 2 unseen greedy Mediums in ≤ 25 min each — or correctly decide within 5 min that one needs DP.
- [ ] Java rep: 763 in Java in ≤ 12 min.

---

## 13. Revision schedule

| Review | Week 17 set (860, 53, 55, 45, 763) |
|---|---|
| Day 0 | W17 |
| Day 3 | W17/W18 |
| Day 7 | W18 |
| Day 14 | W19 |
| Day 30 | W21 |

**Revisit:** Week 17 OA simulation #1 (greedy + sorting is a frequent OA shape), Week 19 (Prim/Kruskal are greedy with proofs — [22](./22-advanced-graphs.md)), Weeks 20–26 (134, 846, 678).

---

## Worked example — 45. Jump Game II

**Clarify.** `nums[i]` = max jump length from i (0 ≤ nums[i] ≤ 1000), 1 ≤ n ≤ 10⁴. You start at index 0; the last index is **guaranteed** reachable. Return the minimum number of jumps. n = 1 → 0.

**Brute force.** Recursion over every jump length from each index → exponential. DP: `dp[i]` = min jumps to reach i, `dp[j] = min(dp[j], dp[i] + 1)` for all j in `i+1..i+nums[i]` → O(n · max jump) = 10⁷ — slow in Python.

**Optimise (greedy BFS).** Think of BFS levels: level k = the set of indices reachable with exactly k jumps. Those sets are **contiguous ranges** `[level_start, cur_end]`. While scanning a level, track `farthest` = the max `i + nums[i]`; when `i` hits `cur_end`, we must take another jump, and the next level extends to `farthest`. Stays-ahead argument: no strategy with k jumps can reach beyond the k-th level's `farthest`. O(n), O(1).

**Code (Python).**

```python
def jump(nums: list[int]) -> int:
    jumps = cur_end = farthest = 0
    for i in range(len(nums) - 1):        # never "jump" from the last index
        farthest = max(farthest, i + nums[i])
        if i == cur_end:                  # current level exhausted → one more jump
            jumps += 1
            cur_end = farthest
    return jumps


assert jump([2, 3, 1, 1, 4]) == 2         # 0 → 1 → 4
assert jump([2, 3, 0, 1, 4]) == 2
assert jump([0]) == 0
assert jump([1, 1, 1, 1]) == 3
assert jump([10, 1, 1, 1]) == 1
print("45 worked example passed")
```

**Test cases.** `[2,3,1,1,4]` → 2 · with a zero `[2,3,0,1,4]` → 2 · single element → 0 · all ones → n − 1 · one big first jump → 1 · looping to `len(nums)` instead of `len(nums) - 1` would return 3 for `[2,3,1,1,4]`.

**Complexity.** Time O(n). Space O(1).
