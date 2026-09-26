# 04 — Prefix Sums

> **Week 4** · NeetCode section: **Arrays & Hashing** (prefix-sum extras; NeetCode "Range Sum Query" practice) · Target: **4 new problems** + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#04--prefix-sums) · Java APIs: [`java-dsa-toolkit.md`](./java-dsa-toolkit.md)

Precompute cumulative sums once (O(n)), then answer "sum of range [i, j]" in O(1). Combined with a hash map, prefix sums count subarrays with a target sum **even with negative numbers** — where sliding window fails.

---

## 1. Concepts to learn

| Concept | Formula / idea |
|---|---|
| Prefix array (1-indexed, padded) | `pre[0] = 0`, `pre[i + 1] = pre[i] + a[i]` → `pre` has length n + 1 |
| Range sum | `sum(a[l..r]) = pre[r + 1] - pre[l]` (inclusive l, r) |
| Running prefix (no array) | Keep `sum` while scanning; enough when you only need "sum so far" |
| Prefix + hash map | `sum(a[j+1..i]) = k` ⇔ `pre[i+1] - pre[j+1] = k` ⇔ look up count of `pre - k` seen so far |
| Seed the map | `count.put(0, 1)` — the empty prefix, so subarrays starting at index 0 are counted |
| 2-D prefix | `P[r+1][c+1] = g[r][c] + P[r][c+1] + P[r+1][c] - P[r][c]`; rectangle via inclusion–exclusion |
| Difference array | Range-add `v` on `[l, r]`: `d[l] += v; d[r+1] -= v`; prefix-sum `d` at the end → O(1) per update |
| Prefix of other operations | Prefix XOR, prefix product (see [238](./01-arrays-strings.md)), prefix counts of a condition |
| Overflow | Sums of 10⁵ values up to 10⁹ need `long` |

Practical use: monthly running totals and "balance at date X" in [P1 Ledger](../18-projects/p1-ledger/README.md) reports are prefix sums; in SQL you'll meet the same idea as `SUM(...) OVER (ORDER BY ...)` window functions in Week 7.

---

## 2. Prerequisite knowledge

- [`01-arrays-strings.md`](./01-arrays-strings.md) — indexing and off-by-one discipline.
- [`02-hashing.md`](./02-hashing.md) — `merge`/`getOrDefault` counting for the prefix + map variant.
- [Toolkit §9 overflow](./java-dsa-toolkit.md#9-integer-overflow-and-long).

---

## 3. Java implementation

```java
import java.util.*;

class PrefixTemplates {
    // 1-D prefix sums with padding: O(n) build, O(1) query
    static long[] build(int[] a) {
        long[] pre = new long[a.length + 1];
        for (int i = 0; i < a.length; i++) pre[i + 1] = pre[i] + a[i];
        return pre;
    }

    static long rangeSum(long[] pre, int l, int r) {   // inclusive l..r
        return pre[r + 1] - pre[l];
    }

    // Count subarrays summing to k (works with negatives): O(n)
    static int countSubarrays(int[] a, int k) {
        Map<Integer, Integer> seen = new HashMap<>();
        seen.put(0, 1);                                // empty prefix
        int sum = 0, count = 0;
        for (int x : a) {
            sum += x;
            count += seen.getOrDefault(sum - k, 0);   // earlier prefixes p with sum - p = k
            seen.merge(sum, 1, Integer::sum);
        }
        return count;
    }

    // Longest subarray with sum k: store FIRST index of each prefix sum
    static int longestWithSum(int[] a, int k) {
        Map<Integer, Integer> firstIdx = new HashMap<>();
        firstIdx.put(0, -1);
        int sum = 0, best = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i];
            Integer j = firstIdx.get(sum - k);
            if (j != null) best = Math.max(best, i - j);
            firstIdx.putIfAbsent(sum, i);              // keep earliest → longest window
        }
        return best;
    }

    // 2-D prefix sums (LC 304 core)
    static int[][] build2D(int[][] g) {
        int R = g.length, C = g[0].length;
        int[][] P = new int[R + 1][C + 1];
        for (int r = 0; r < R; r++)
            for (int c = 0; c < C; c++)
                P[r + 1][c + 1] = g[r][c] + P[r][c + 1] + P[r + 1][c] - P[r][c];
        return P;
    }

    static int rect(int[][] P, int r1, int c1, int r2, int c2) {   // inclusive corners
        return P[r2 + 1][c2 + 1] - P[r1][c2 + 1] - P[r2 + 1][c1] + P[r1][c1];
    }

    // Difference array: apply many range increments, then materialise
    static int[] applyRangeAdds(int n, int[][] updates) {  // each update = {l, r, v}
        int[] d = new int[n + 1];
        for (int[] u : updates) { d[u[0]] += u[2]; d[u[1] + 1] -= u[2]; }
        int[] out = new int[n];
        int run = 0;
        for (int i = 0; i < n; i++) { run += d[i]; out[i] = run; }
        return out;
    }

    public static void main(String[] args) {
        long[] pre = build(new int[]{1, 2, 3, 4});
        System.out.println(rangeSum(pre, 1, 2));                                  // 5
        System.out.println(countSubarrays(new int[]{1, 1, 1}, 2));                // 2
        System.out.println(countSubarrays(new int[]{1, -1, 0}, 0));               // 3
        System.out.println(longestWithSum(new int[]{1, -1, 5, -2, 3}, 3));        // 4
        int[][] P = build2D(new int[][]{{1, 2}, {3, 4}});
        System.out.println(rect(P, 0, 0, 1, 1) + " " + rect(P, 1, 0, 1, 1));    // 10 7
        System.out.println(Arrays.toString(applyRangeAdds(5, new int[][]{{1, 3, 2}, {2, 4, 3}}))); // [0, 2, 5, 5, 3]
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Problems |
|---|---|
| Running sum output | 1480 |
| Immutable range-sum queries | 303, 304 (2-D) |
| Balance point: left sum == right sum (`total - left - a[i]`) | 724 |
| Count subarrays with sum k (prefix + map of counts) | 560 |
| Longest subarray with sum k (prefix + map of first index) | 525 Contiguous Array *(reference only, not assigned)* |
| Divisible sums (prefix mod k, careful with negatives: `Math.floorMod`) | 974 Subarray Sums Divisible by K *(reference only)* |
| Range updates via difference array | 1109 Corporate Flight Bookings *(reference only)* |

---

## 5. How to recognise the pattern

- "Sum of elements between i and j", **many queries** on a static array → prefix array.
- "Number of subarrays whose sum equals k" and values may be **negative** → prefix + hash map (sliding window requires non-negative values to be monotonic).
- "Pivot / equilibrium index", "left sum equals right sum".
- Many range **updates** then one read → difference array.
- Matrix region sums → 2-D prefix.

---

## 6. Beginner problems (Week 4)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 1480 | [Running Sum of 1d Array](https://leetcode.com/problems/running-sum-of-1d-array/) | Easy | `a[i] += a[i - 1]` in place. |
| 303 | [Range Sum Query - Immutable](https://leetcode.com/problems/range-sum-query-immutable/) | Easy | Build a padded `pre` array in the constructor; `sumRange = pre[r+1] - pre[l]`. |

---

## 7. Interview problems (Week 4)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 724 | [Find Pivot Index](https://leetcode.com/problems/find-pivot-index/) | Easy | 4 | <details><summary>show</summary>Compute total once; scanning left to right, pivot when `left == total - left - a[i]`; update `left` after the check.</details> |
| 560 | [Subarray Sum Equals K](https://leetcode.com/problems/subarray-sum-equals-k/) | Medium | 4 | <details><summary>show</summary>Count of earlier prefixes equal to `sum - k`; seed `{0: 1}`. Sliding window fails because values can be negative.</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 304 | [Range Sum Query 2D - Immutable](https://leetcode.com/problems/range-sum-query-2d-immutable/) | Medium | <details><summary>show</summary>(R+1)×(C+1) padded prefix; inclusion–exclusion with four corners.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 has no dedicated section; 238 (in [01](./01-arrays-strings.md)) and 560 are the prefix ideas that appear in NeetCode lists. Order: implement §3 → 1480 → 303 → 724 → 560 → (W21–26) 304.

---

## 9. Target number of problems

**4 new** in Week 4 (with 3 from [03](./03-two-pointers.md) = the week's 7) + 1 stretch.

---

## 10. Mistakes beginners commonly make

- Unpadded prefix arrays → special-casing `l == 0` and off-by-one bugs. Always use length n + 1.
- 560: forgetting `seen.put(0, 1)` → misses subarrays starting at index 0.
- 560: inserting the current prefix **before** looking up `sum - k` → counts empty subarrays when k = 0.
- Trying sliding window on arrays with negatives.
- `int` overflow on large sums → `long[] pre`.
- 724: returning the last pivot instead of the leftmost; or updating `left` before the comparison.
- Java: `%` of a negative number is negative → use `Math.floorMod` for "divisible by k" variants.
- 2-D: sign errors in inclusion–exclusion — draw the four rectangles.

---

## 11. Mastery criteria

- [ ] Write `build`, `rangeSum`, and the prefix + map counter from memory, no off-by-one, in ≤ 10 min total.
- [ ] Solve 560 from blank in ≤ 15 min and explain why sliding window doesn't work.
- [ ] Derive the 2-D formula on paper in ≤ 3 min.
- [ ] Solve 1 unseen prefix-sum Medium in ≤ 25 min.

---

## 12. Revision schedule

| Review | Week 4 set (1480, 303, 724, 560) |
|---|---|
| Day 0 | W4 |
| Day 3 | W4/W5 |
| Day 7 | W5 (contrast with sliding window) |
| Day 14 | W6 |
| Day 30 | W8 (review week) |

**Revisit:** Week 5 (sliding window — explain why 560 isn't a window problem), Week 16 (DP: prefix-sum states), Weeks 21–26 (304).

---

## Worked example — 560. Subarray Sum Equals K

**Clarify.** `int[] nums`, 1 ≤ n ≤ 2·10⁴, values in [−1000, 1000], k in [−10⁷, 10⁷]. Count **contiguous, non-empty** subarrays with sum exactly k. Negative numbers allowed.

**Brute force.** All (i, j) pairs with a running sum → O(n²) = 4·10⁸. Borderline in Java; interviewer wants better.

**Why not sliding window?** Windows need "adding an element never decreases the sum" to know which pointer to move. With negatives, that monotonicity is gone.

**Optimise.** Let `P` be the prefix sum up to index i. A subarray ending at i with sum k exists for every earlier prefix equal to `P - k`. Maintain `Map<prefixSum, howManyTimesSeen>`, seeded with `{0: 1}`. For each element: update P, add `count(P - k)` to the answer, then record P. O(n).

**Code.**

```java
import java.util.*;

class SubarraySumK {
    public int subarraySum(int[] nums, int k) {
        Map<Integer, Integer> prefixCount = new HashMap<>();
        prefixCount.put(0, 1);
        int prefix = 0, count = 0;          // |prefix| ≤ 2·10^4 · 1000 = 2·10^7 → int is fine
        for (int x : nums) {
            prefix += x;
            count += prefixCount.getOrDefault(prefix - k, 0);
            prefixCount.merge(prefix, 1, Integer::sum);
        }
        return count;
    }

    public static void main(String[] args) {
        SubarraySumK s = new SubarraySumK();
        System.out.println(s.subarraySum(new int[]{1, 1, 1}, 2));     // 2
        System.out.println(s.subarraySum(new int[]{1, 2, 3}, 3));     // 2
        System.out.println(s.subarraySum(new int[]{1, -1, 0}, 0));    // 3
        System.out.println(s.subarraySum(new int[]{5}, 5));           // 1
    }
}
```

**Test cases.** `[1,1,1], k=2` → 2 · `[1,2,3], k=3` → 2 (`[1,2]`, `[3]`) · `[1,-1,0], k=0` → 3 (`[1,-1]`, `[0]`, `[1,-1,0]`) · single element `[5], k=5` → 1 · no match `[1,2], k=10` → 0.

**Complexity.** Time O(n) average (hash operations). Space O(n) for the map.
