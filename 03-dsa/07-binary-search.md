# 07 — Binary Search (incl. Search on the Answer)

> **Weeks 6–7** · NeetCode section: **Binary Search** · Target: **7 new problems** (W6: 2, W7: 5) + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#07--binary-search) · Java APIs: [toolkit §8 TreeMap](./java-dsa-toolkit.md#8-treemap--treeset), [§9 overflow](./java-dsa-toolkit.md#9-integer-overflow-and-long)

Binary search is not "find x in a sorted array". It is: **find the boundary where a monotonic predicate flips from false to true** in O(log n) evaluations. Once you see it that way, "minimum speed", "minimum capacity" and "rotated array" problems are the same template. Week 6's merge/quick sort study ([ROADMAP W6](../ROADMAP.md#5-week-by-week-master-table)) pairs naturally with this.

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| Invariant-based search | Decide what `lo` and `hi` *mean* (e.g. "answer is in `[lo, hi]`") and keep it true every iteration |
| Closed interval `[lo, hi]` | `while (lo <= hi)`, `hi = mid - 1` / `lo = mid + 1` — for "find exact target" |
| Half-open / boundary search | `while (lo < hi)`, `hi = mid` / `lo = mid + 1` — for "first index where predicate is true" (lower bound) |
| Monotonic predicate | `P(x)` false…false true…true. Binary search finds the first true |
| Search on the answer | Answer space `[minPossible, maxPossible]`; `P(x)` = "is x feasible?"; check in O(n) → O(n log range) |
| Overflow-safe mid | `mid = lo + (hi - lo) / 2` (or `(lo + hi) >>> 1`) |
| Rotated sorted array | At least one half `[lo, mid]` or `[mid, hi]` is sorted — decide which, then whether the target is in it |
| 2-D matrix as 1-D | Index `k` ↔ `(k / cols, k % cols)` when rows are globally sorted |
| `TreeMap.floorKey` | Library binary search over keys — O(log n) (981) |
| `Arrays.binarySearch` | Returns `-(insertionPoint) - 1` when missing; no guarantee which duplicate is found |

---

## 2. Prerequisite knowledge

- [`00-big-o.md`](./00-big-o.md) — why O(log n) matters (log₂ 10⁹ ≈ 30).
- [`03-two-pointers.md`](./03-two-pointers.md) — pointer invariants on sorted data.
- [Toolkit §9](./java-dsa-toolkit.md#9-integer-overflow-and-long) — `int` overflow in `lo + hi` and in feasibility sums.

---

## 3. Java implementation

```java
import java.util.*;
import java.util.function.IntPredicate;

class BinarySearchTemplates {
    // 1) Exact match, closed interval
    static int indexOf(int[] a, int target) {
        int lo = 0, hi = a.length - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] == target) return mid;
            if (a[mid] < target) lo = mid + 1; else hi = mid - 1;
        }
        return -1;
    }

    // 2) Lower bound: first index i with a[i] >= target (a.length if none) — also "search insert position"
    static int lowerBound(int[] a, int target) {
        int lo = 0, hi = a.length;                     // answer in [lo, hi]
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] >= target) hi = mid;            // mid could be the answer
            else lo = mid + 1;                         // mid is definitely not
        }
        return lo;
    }

    // 3) Upper bound: first index with a[i] > target. count(target) = upper - lower
    static int upperBound(int[] a, int target) {
        int lo = 0, hi = a.length;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] > target) hi = mid; else lo = mid + 1;
        }
        return lo;
    }

    // 4) Generic: smallest x in [lo, hi] with ok(x) true, assuming ok is monotonic (false...true)
    static int firstTrue(int lo, int hi, IntPredicate ok) {
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (ok.test(mid)) hi = mid; else lo = mid + 1;
        }
        return lo;                                     // caller guarantees ok(hi) is true
    }

    // 5) Search on the answer: min eating speed (LC 875) using firstTrue
    static int minEatingSpeed(int[] piles, int h) {
        int max = Arrays.stream(piles).max().getAsInt();
        return firstTrue(1, max, k -> {
            long hours = 0;
            for (int p : piles) hours += (p + k - 1) / k;   // ceil(p / k) without floating point
            return hours <= h;
        });
    }

    // 6) Rotated sorted array: find minimum (LC 153)
    static int findMin(int[] a) {
        int lo = 0, hi = a.length - 1;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] > a[hi]) lo = mid + 1;          // min is strictly right of mid
            else hi = mid;                             // mid..hi sorted → min at mid or left
        }
        return a[lo];
    }

    public static void main(String[] args) {
        int[] a = {1, 3, 3, 3, 5, 8};
        System.out.println(indexOf(a, 5) + " " + indexOf(a, 4));                    // 4 -1
        System.out.println(lowerBound(a, 3) + " " + upperBound(a, 3));              // 1 4
        System.out.println(lowerBound(a, 9));                                        // 6
        System.out.println(minEatingSpeed(new int[]{3, 6, 7, 11}, 8));              // 4
        System.out.println(findMin(new int[]{4, 5, 6, 7, 0, 1, 2}));                // 0
        // Library helpers
        System.out.println(Arrays.binarySearch(a, 4));                              // -5 → insertion point 4
        TreeMap<Integer, String> tm = new TreeMap<>(Map.of(1, "a", 4, "b"));
        System.out.println(tm.floorKey(3));                                          // 1
    }
}
```

**How to pick the template:** Need "is it there?" → (1). Need "where does it go / first ≥ / first that works" → (2)/(4). If you can phrase the problem as "smallest x such that ___", use (4) and only write the predicate.

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Problems |
|---|---|
| Exact search in sorted array | 704 |
| Insert position / lower bound | 35 |
| 2-D matrix flattened | 74 |
| Search on the answer (min feasible value) | 875, 1011 |
| Rotated array — find pivot/min | 153 |
| Rotated array — find target | 33 |
| Floor lookup on timestamps (`TreeMap` or binary search on a list) | 981 |
| Partition two sorted arrays | 4 |

---

## 5. How to recognise the pattern

- Input is **sorted** (or rotated sorted) and you need O(log n).
- "Find the minimum X such that…", "maximum X such that…", "at least", "within h hours / d days" → search on the answer.
- Constraint: values/answers up to 10⁹ but n ≤ 10⁵ → O(n log 10⁹) ≈ 3·10⁶ is perfect.
- Feasibility is **monotonic**: if speed k works, any speed > k works.
- "Timestamp ≤ t" lookups → floor search.
- "Must run in O(log n)" in the statement is a giveaway.

---

## 6. Beginner problems (Week 6)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 704 | [Binary Search](https://leetcode.com/problems/binary-search/) | Easy | Closed interval template; `mid = lo + (hi - lo) / 2`. |
| 35 | [Search Insert Position](https://leetcode.com/problems/search-insert-position/) | Easy | Lower bound with `hi = a.length`; return `lo`. |

---

## 7. Interview problems (Week 7)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 74 | [Search a 2D Matrix](https://leetcode.com/problems/search-a-2d-matrix/) | Medium | 7 | <details><summary>show</summary>Treat as one sorted array of length `R*C`; `mid → (mid / C, mid % C)`.</details> |
| 875 | [Koko Eating Bananas](https://leetcode.com/problems/koko-eating-bananas/) | Medium | 7 | <details><summary>show</summary>Search k in `[1, max(piles)]`; feasible if Σ ceil(p/k) ≤ h. Sum hours in `long`.</details> |
| 153 | [Find Minimum in Rotated Sorted Array](https://leetcode.com/problems/find-minimum-in-rotated-sorted-array/) | Medium | 7 | <details><summary>show</summary>Compare `a[mid]` with `a[hi]`: greater → min is right of mid; else min is at mid or left.</details> |
| 33 | [Search in Rotated Sorted Array](https://leetcode.com/problems/search-in-rotated-sorted-array/) | Medium | 7 | <details><summary>show</summary>One half is sorted; if target lies within the sorted half's range go there, else go to the other half.</details> |
| 981 | [Time Based Key-Value Store](https://leetcode.com/problems/time-based-key-value-store/) | Medium | 7 | <details><summary>show</summary>`Map<String, TreeMap<Integer, String>>` with `floorEntry(timestamp)`, or lists (timestamps strictly increase) + upper-bound − 1.</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 4 | [Median of Two Sorted Arrays](https://leetcode.com/problems/median-of-two-sorted-arrays/) | Hard | <details><summary>show</summary>Binary search the partition in the shorter array so left halves hold (m+n+1)/2 elements and `maxLeft ≤ minRight` on both sides.</details> |
| 1011 | [Capacity To Ship Packages Within D Days](https://leetcode.com/problems/capacity-to-ship-packages-within-d-days/) | Medium | <details><summary>show</summary>Search capacity in `[max(w), sum(w)]`; greedy day count as the predicate.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Binary Search**: 704 → 74 → 875 → 153 → 33 → 981 → 4.
Here: templates (1)(2)(4) from memory → 704 → 35 (W6) → 74 → 875 → 153 → 33 → 981 (W7) → (W21–26) 1011, 4.

---

## 9. Target number of problems

**7 new**: 2 in Week 6, 5 in Week 7 (+ 3 linked-list problems in W7 = 8) + 2 stretch.

---

## 10. Mistakes beginners commonly make

- Infinite loop: `while (lo < hi)` with `lo = mid` (when `hi = lo + 1`, mid = lo forever). With `lo < hi` use `lo = mid + 1` / `hi = mid`.
- Mixing templates: `while (lo <= hi)` with `hi = mid` → infinite loop.
- `(lo + hi) / 2` overflow when both near `Integer.MAX_VALUE` (search on answer ranges up to 2·10⁹).
- Feasibility sums in `int` (875: Σ ceil(p/k) with k = 1 and piles up to 10⁹ → overflow) → `long`.
- `Math.ceil(p / k)` with ints → integer division happens first. Use `(p + k - 1) / k`.
- Wrong search bounds for search-on-answer (e.g. starting k at 0 → division by zero).
- 33: using `<` vs `<=` wrongly when checking which half is sorted (`a[lo] <= a[mid]` — equality when lo == mid).
- 981: assigning `floorKey(...)` (possibly `null`) to `int` → `NullPointerException`.
- Assuming `Arrays.binarySearch` finds the first duplicate.

---

## 11. Mastery criteria

- [ ] Write lower bound and `firstTrue` from memory, and prove termination (the interval strictly shrinks).
- [ ] Solve 875 and 153 in ≤ 15 min each from blank.
- [ ] Solve 33 in ≤ 20 min, handling the `lo == mid` case.
- [ ] Given an unseen "minimum X such that" problem, write the predicate and bounds in ≤ 5 min; solve 2 unseen Mediums in ≤ 25 min.

---

## 12. Revision schedule

| Review | Week 6 set (704, 35) | Week 7 set (74, 875, 153, 33, 981) |
|---|---|---|
| Day 0 | W6 | W7 |
| Day 3 | W6/W7 | W7/W8 |
| Day 7 | W7 | W8 (review week) |
| Day 14 | W8 | W9 |
| Day 30 | W10 | W11 |

**Revisit:** Week 8 review week (33, 875 timed), Week 11 (BST search is the tree version of this), Weeks 21–26 (4, 1011) — search-on-answer is common in OAs.

---

## Worked example — 875. Koko Eating Bananas

**Clarify.** `piles[i]` bananas (1 ≤ n ≤ 10⁴, piles up to 10⁹), `h` hours with n ≤ h ≤ 10⁹. Each hour Koko eats up to k bananas from one pile (leftover capacity is wasted). Find the **minimum integer k** to finish within h hours.

**Brute force.** Try k = 1, 2, 3, … and compute hours each time: O(max · n) = 10⁹ · 10⁴. Impossible.

**Optimise.** `hours(k) = Σ ceil(pile / k)` is **non-increasing** in k → predicate `hours(k) ≤ h` is false…false true…true. Binary search k in `[1, max(piles)]` (k = max always works because h ≥ n). Cost: O(n · log(max)) ≈ 10⁴ · 30.

**Code.**

```java
class KokoBananas {
    public int minEatingSpeed(int[] piles, int h) {
        int lo = 1, hi = 0;
        for (int p : piles) hi = Math.max(hi, p);
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (canFinish(piles, h, mid)) hi = mid;   // mid works → answer ≤ mid
            else lo = mid + 1;                        // mid too slow → answer > mid
        }
        return lo;
    }

    private boolean canFinish(int[] piles, int h, int k) {
        long hours = 0;                               // up to 10^4 * 10^9 → needs long
        for (int p : piles) {
            hours += (p + (long) k - 1) / k;          // ceil without overflow of p + k - 1
            if (hours > h) return false;              // early exit
        }
        return true;
    }

    public static void main(String[] args) {
        KokoBananas s = new KokoBananas();
        System.out.println(s.minEatingSpeed(new int[]{3, 6, 7, 11}, 8));           // 4
        System.out.println(s.minEatingSpeed(new int[]{30, 11, 23, 4, 20}, 5));     // 30
        System.out.println(s.minEatingSpeed(new int[]{30, 11, 23, 4, 20}, 6));     // 23
        System.out.println(s.minEatingSpeed(new int[]{1000000000}, 2));            // 500000000
        System.out.println(s.minEatingSpeed(new int[]{312884470}, 312884469));     // 2
    }
}
```

**Test cases.** `[3,6,7,11], h=8` → 4 · `h == n` → answer is max pile (30) · `[30,11,23,4,20], h=6` → 23 · single huge pile `[10⁹], h=2` → 5·10⁸ · `p + k - 1` would overflow `int` for p, k near 10⁹ → the `long` cast matters.

**Complexity.** Time O(n log M), M = max pile. Space O(1).
