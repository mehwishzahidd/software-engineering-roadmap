# 04 — Prefix Sums

> **Week 3** · NeetCode section: **Arrays & Hashing** (prefix-sum extras) · Target: **3 new problems** + stretch pool · Java rep: **560** (Week 3)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#04--prefix-sums) · Python: [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) (`itertools.accumulate`) · Java: [quick reference](./java-dsa-toolkit.md)

Precompute cumulative sums once (O(n)), then answer "sum of range [i, j]" in O(1). Combined with a hash map, prefix sums count subarrays with a target sum **even with negative numbers** — where sliding window fails.

---

## 1. Concepts to learn

| Concept | Formula / idea |
|---|---|
| Prefix list (padded) | `pre[0] = 0`, `pre[i + 1] = pre[i] + a[i]` → `pre` has length n + 1. Python: `pre = [0, *itertools.accumulate(a)]` |
| Range sum | `sum(a[l..r]) = pre[r + 1] - pre[l]` (inclusive l, r) |
| Running prefix (no list) | Keep `total` while scanning; enough when you only need "sum so far" |
| Prefix + hash map | `sum(a[j+1..i]) == k` ⇔ `pre[i+1] - pre[j+1] == k` ⇔ look up how many earlier prefixes equal `pre - k` |
| Seed the map | `{0: 1}` — the empty prefix, so subarrays starting at index 0 are counted |
| 2-D prefix | `P[r+1][c+1] = g[r][c] + P[r][c+1] + P[r+1][c] - P[r][c]`; rectangle by inclusion–exclusion |
| Difference array | Range-add `v` on `[l, r]`: `d[l] += v; d[r+1] -= v`; prefix-sum `d` at the end → O(1) per update |
| Prefix of other operations | Prefix XOR, prefix product ([238](./01-arrays-strings.md)), prefix counts of a condition |
| Overflow | None in Python (unbounded `int`); in the Java rep, sums of 10⁵ values up to 10⁹ need `long` |

Practical use: running inventory balances in [FlowGrid](../18-projects/flowgrid/README.md) and account balances in [LedgerX](../18-projects/ledgerx/README.md) (balance = sum of ledger entries up to a point) are prefix sums; in SQL you'll write the same idea as `SUM(amount) OVER (ORDER BY created_at)`.

---

## 2. Prerequisite knowledge

- [`01-arrays-strings.md`](./01-arrays-strings.md) — indexing and off-by-one discipline.
- [`02-hashing.md`](./02-hashing.md) — `dict.get(k, 0)` / `Counter` counting for the prefix + map variant.

---

## 3. Python implementation

```python
from collections import defaultdict
from itertools import accumulate


def build(a: list[int]) -> list[int]:
    return [0, *accumulate(a)]                  # padded: len(a) + 1 entries


def range_sum(pre: list[int], l: int, r: int) -> int:
    return pre[r + 1] - pre[l]                  # inclusive l..r


def count_subarrays(a: list[int], k: int) -> int:
    """Number of subarrays summing to k (negatives allowed): O(n)."""
    seen: defaultdict[int, int] = defaultdict(int)
    seen[0] = 1                                 # empty prefix
    total = count = 0
    for x in a:
        total += x
        count += seen[total - k]                # earlier prefixes p with total - p == k
        seen[total] += 1
    return count


def longest_with_sum(a: list[int], k: int) -> int:
    """Longest subarray with sum k: store the FIRST index of each prefix sum."""
    first_idx = {0: -1}
    total = best = 0
    for i, x in enumerate(a):
        total += x
        if total - k in first_idx:
            best = max(best, i - first_idx[total - k])
        first_idx.setdefault(total, i)          # keep earliest → longest window
    return best


def build_2d(g: list[list[int]]) -> list[list[int]]:
    R, C = len(g), len(g[0])
    P = [[0] * (C + 1) for _ in range(R + 1)]
    for r in range(R):
        for c in range(C):
            P[r + 1][c + 1] = g[r][c] + P[r][c + 1] + P[r + 1][c] - P[r][c]
    return P


def rect(P: list[list[int]], r1: int, c1: int, r2: int, c2: int) -> int:   # inclusive corners
    return P[r2 + 1][c2 + 1] - P[r1][c2 + 1] - P[r2 + 1][c1] + P[r1][c1]


def apply_range_adds(n: int, updates: list[tuple[int, int, int]]) -> list[int]:
    d = [0] * (n + 1)
    for l, r, v in updates:
        d[l] += v
        d[r + 1] -= v
    return list(accumulate(d[:n]))


pre = build([1, 2, 3, 4])
assert pre == [0, 1, 3, 6, 10] and range_sum(pre, 1, 2) == 5
assert count_subarrays([1, 1, 1], 2) == 2 and count_subarrays([1, -1, 0], 0) == 3
assert longest_with_sum([1, -1, 5, -2, 3], 3) == 4
P = build_2d([[1, 2], [3, 4]])
assert rect(P, 0, 0, 1, 1) == 10 and rect(P, 1, 0, 1, 1) == 7
assert apply_range_adds(5, [(1, 3, 2), (2, 4, 3)]) == [0, 2, 5, 5, 3]
print("prefix-sum templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 3): 560 Subarray Sum Equals K.**

| Python | Java |
|---|---|
| `[0, *accumulate(a)]` | `long[] pre = new long[n + 1]; for (...) pre[i + 1] = pre[i] + a[i];` |
| `seen[total - k]` on a `defaultdict(int)` | `seen.getOrDefault(total - k, 0)` |
| `seen[total] += 1` | `seen.merge(total, 1, Integer::sum)` |
| unbounded `int` | `long` when sums can exceed ~2.1·10⁹ |

```java
import java.util.*;

class SubarraySumKRep {
    static int subarraySum(int[] nums, int k) {
        Map<Integer, Integer> seen = new HashMap<>();
        seen.put(0, 1);
        int total = 0, count = 0;               // |total| ≤ 2·10^4 · 1000 → int is enough here
        for (int x : nums) {
            total += x;
            count += seen.getOrDefault(total - k, 0);
            seen.merge(total, 1, Integer::sum);
        }
        return count;
    }

    public static void main(String[] args) {
        System.out.println(subarraySum(new int[]{1, 1, 1}, 2) + " " + subarraySum(new int[]{1, -1, 0}, 0)); // 2 3
    }
}
```

Java trap: `%` on negatives (divisible-by-k variants) needs `Math.floorMod`; Python's `%` already floors.

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Problems |
|---|---|
| Running sum output | 1480 |
| Immutable range-sum queries | 303, 304 (2-D) |
| Balance point: left sum == right sum (`total - left - a[i]`) | 724 |
| Count subarrays with sum k (prefix + dict of counts) | 560 |
| Longest subarray with sum k (prefix + dict of first index) | `longest_with_sum` in §3 |
| Divisible sums (prefix `% k` as the key — Python's `%` is already non-negative for positive k) | variant of 560 |
| Range updates via difference array | `apply_range_adds` in §3 |

---

## 6. How to recognise the pattern

- "Sum of elements between i and j", **many queries** on a static array → prefix list.
- "Number of subarrays whose sum equals k" and values may be **negative** → prefix + dict (sliding window requires non-negative values).
- "Pivot / equilibrium index", "left sum equals right sum".
- Many range **updates** then one read → difference array.
- Matrix region sums → 2-D prefix.

---

## 7. Beginner problems (Week 3)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 303 | [Range Sum Query - Immutable](https://leetcode.com/problems/range-sum-query-immutable/) | Easy | Build a padded `pre` in `__init__`; `sumRange = pre[r + 1] - pre[l]`. |
| 724 | [Find Pivot Index](https://leetcode.com/problems/find-pivot-index/) | Easy | `total = sum(nums)`; scanning left to right, pivot when `left == total - left - x`; update `left` **after** the check. |

---

## 8. Interview problems (Week 3)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 560 | [Subarray Sum Equals K](https://leetcode.com/problems/subarray-sum-equals-k/) | Medium | 3 | <details><summary>show</summary>Count of earlier prefixes equal to `total - k`; seed `{0: 1}`. Sliding window fails because values can be negative.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 1480 | [Running Sum of 1d Array](https://leetcode.com/problems/running-sum-of-1d-array/) | Easy | <details><summary>show</summary>`list(accumulate(nums))`, or in place `nums[i] += nums[i - 1]`. Timed warm-up: ≤ 3 min.</details> |
| 304 | [Range Sum Query 2D - Immutable](https://leetcode.com/problems/range-sum-query-2d-immutable/) | Medium | <details><summary>show</summary>(R+1)×(C+1) padded prefix; inclusion–exclusion with four corners.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 has no dedicated section; 238 (in [01](./01-arrays-strings.md)) and 560 are the prefix ideas that appear in NeetCode lists. Order: implement §3 → 303 → 724 → 560 → Java rep 560 → (W20–26) 1480, 304.

---

## 10. Target number of problems

**3 new** in Week 3 (with 5 from [03](./03-two-pointers.md) = the week's 8) + 2 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- `sum(a[l:r + 1])` per query → O(n) per query (slice copy + sum). That's the brute force, not a prefix sum.
- `accumulate(a)` returns an **iterator** — wrap in `list(...)` if you need indexing (and pad with `0`).
- Using a plain `dict` and `seen[total - k]` → `KeyError`; use `.get(key, 0)` or `defaultdict(int)` (and remember `defaultdict` inserts on read).
- Mixing `/` and `//` when computing averages/indices.

**General**
- Unpadded prefix lists → special-casing `l == 0` and off-by-one bugs. Always use length n + 1.
- 560: forgetting the `{0: 1}` seed → misses subarrays starting at index 0.
- 560: recording the current prefix **before** looking up `total - k` → counts empty subarrays when k = 0.
- Trying sliding window on arrays with negatives.
- 724: returning the last pivot instead of the leftmost; updating `left` before the comparison.
- 2-D: sign errors in inclusion–exclusion — draw the four rectangles.

**Java-rep traps (560):** `int` overflow on large sums, `Math.floorMod` for negative remainders.

---

## 12. Mastery criteria

- [ ] Write `build`, `range_sum`, and the prefix + dict counter from memory, no off-by-one, in ≤ 10 min total.
- [ ] Solve 560 from blank in ≤ 15 min and explain why sliding window doesn't work.
- [ ] Derive the 2-D formula on paper in ≤ 3 min.
- [ ] Solve 1 unseen prefix-sum Medium in ≤ 25 min.
- [ ] Java rep: 560 in Java in ≤ 15 min.

---

## 13. Revision schedule

| Review | Week 3 set (303, 724, 560) |
|---|---|
| Day 0 | W3 |
| Day 3 | W3/W4 |
| Day 7 | W4 (contrast with sliding window) |
| Day 14 | W5 |
| Day 30 | W7 |

**Revisit:** Week 4 (sliding window — explain why 560 isn't a window problem), Week 9 (LedgerX balances = prefix sums of entries), Week 15 (DP with prefix-sum states), Weeks 20–26 (1480, 304).

---

## Worked example — 560. Subarray Sum Equals K

**Clarify.** `nums: list[int]`, 1 ≤ n ≤ 2·10⁴, values in [−1000, 1000], k in [−10⁷, 10⁷]. Count **contiguous, non-empty** subarrays with sum exactly k. Negative numbers allowed.

**Brute force.** All (i, j) pairs with a running sum → O(n²) = 4·10⁸ steps. In Python that's minutes — TLE. We need O(n).

**Why not sliding window?** Windows need "adding an element never decreases the sum" to know which pointer to move. With negatives, that monotonicity is gone.

**Optimise.** Let `P` be the prefix sum up to index i. A subarray ending at i with sum k exists for every earlier prefix equal to `P - k`. Maintain `prefix → how many times seen`, seeded with `{0: 1}`. For each element: update P, add `count[P - k]` to the answer, then record P. O(n).

**Code (Python).**

```python
from collections import defaultdict


def subarray_sum(nums: list[int], k: int) -> int:
    prefix_count: defaultdict[int, int] = defaultdict(int)
    prefix_count[0] = 1
    prefix = count = 0
    for x in nums:
        prefix += x
        count += prefix_count[prefix - k]   # look up BEFORE recording the current prefix
        prefix_count[prefix] += 1
    return count


assert subarray_sum([1, 1, 1], 2) == 2
assert subarray_sum([1, 2, 3], 3) == 2
assert subarray_sum([1, -1, 0], 0) == 3
assert subarray_sum([5], 5) == 1
assert subarray_sum([1, 2], 10) == 0
print("560 worked example passed")
```

**Test cases.** `[1,1,1], k=2` → 2 · `[1,2,3], k=3` → 2 (`[1,2]`, `[3]`) · `[1,-1,0], k=0` → 3 · single element `[5], k=5` → 1 · no match → 0.

**Complexity.** Time O(n) average. Space O(n) for the dict. (Note: reading `prefix_count[prefix - k]` on a `defaultdict` inserts zero-count keys — harmless here, but it grows the dict; `.get(prefix - k, 0)` avoids that.)
