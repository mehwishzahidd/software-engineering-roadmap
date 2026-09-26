# 07 — Binary Search (incl. Search on the Answer)

> **Week 6** · NeetCode section: **Binary Search** · Target: **8 new problems** + stretch pool · Java rep: **875** (Week 6)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#07--binary-search) · Python: [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) (`bisect`) · Java: [quick reference](./java-dsa-toolkit.md)

Binary search is not "find x in a sorted array". It is: **find the boundary where a monotonic predicate flips from false to true** in O(log n) evaluations. Once you see it that way, "minimum speed", "minimum capacity" and "rotated array" problems are the same template.

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| Invariant-based search | Decide what `lo` and `hi` *mean* (e.g. "answer is in `[lo, hi]`") and keep it true every iteration |
| Closed interval `[lo, hi]` | `while lo <= hi`, `hi = mid - 1` / `lo = mid + 1` — for "find exact target" |
| Half-open / boundary search | `while lo < hi`, `hi = mid` / `lo = mid + 1` — for "first index where predicate is true" (lower bound) |
| Monotonic predicate | `P(x)`: False…False True…True. Binary search finds the first True |
| Search on the answer | Answer space `[min_possible, max_possible]`; `P(x)` = "is x feasible?"; O(n) check → O(n log range) |
| `bisect` module | `bisect_left(a, x)` = first index with `a[i] >= x`; `bisect_right(a, x)` = first index with `a[i] > x`; `key=` parameter (3.10+) |
| Midpoint | `mid = (lo + hi) // 2` — no overflow in Python; in Java use `lo + (hi - lo) / 2` |
| Rotated sorted array | At least one half `[lo, mid]` or `[mid, hi]` is sorted — decide which, then whether the target is in it |
| 2-D matrix as 1-D | Index `k` ↔ `divmod(k, cols)` when rows are globally sorted |
| Floor lookup | "largest timestamp ≤ t": `bisect_right(times, t) - 1` (981) |
| Integer ceil | `-(-p // k)` or `(p + k - 1) // k` — avoid `math.ceil(p / k)` float rounding on huge ints |

---

## 2. Prerequisite knowledge

- [`00-big-o.md`](./00-big-o.md) — why O(log n) matters (log₂ 10⁹ ≈ 30).
- [`03-two-pointers.md`](./03-two-pointers.md) — pointer invariants on sorted data.
- `bisect` — [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md).

---

## 3. Python implementation

```python
from bisect import bisect_left, bisect_right
from typing import Callable


def index_of(a: list[int], target: int) -> int:
    """1) Exact match, closed interval."""
    lo, hi = 0, len(a) - 1
    while lo <= hi:
        mid = (lo + hi) // 2
        if a[mid] == target:
            return mid
        if a[mid] < target:
            lo = mid + 1
        else:
            hi = mid - 1
    return -1


def lower_bound(a: list[int], target: int) -> int:
    """2) First index i with a[i] >= target (len(a) if none) — same as bisect_left."""
    lo, hi = 0, len(a)                  # answer in [lo, hi]
    while lo < hi:
        mid = (lo + hi) // 2
        if a[mid] >= target:
            hi = mid                    # mid could be the answer
        else:
            lo = mid + 1                # mid is definitely not
    return lo


def first_true(lo: int, hi: int, ok: Callable[[int], bool]) -> int:
    """3) Smallest x in [lo, hi] with ok(x) True; ok must be monotonic and ok(hi) True."""
    while lo < hi:
        mid = (lo + hi) // 2
        if ok(mid):
            hi = mid
        else:
            lo = mid + 1
    return lo


def min_eating_speed(piles: list[int], h: int) -> int:
    """4) Search on the answer (LC 875) using first_true."""
    return first_true(1, max(piles), lambda k: sum((p + k - 1) // k for p in piles) <= h)


def find_min_rotated(a: list[int]) -> int:
    """5) Rotated sorted array, distinct values (LC 153)."""
    lo, hi = 0, len(a) - 1
    while lo < hi:
        mid = (lo + hi) // 2
        if a[mid] > a[hi]:
            lo = mid + 1                # min is strictly right of mid
        else:
            hi = mid                    # a[mid..hi] sorted → min at mid or left
    return a[lo]


a = [1, 3, 3, 3, 5, 8]
assert index_of(a, 5) == 4 and index_of(a, 4) == -1
assert lower_bound(a, 3) == bisect_left(a, 3) == 1
assert bisect_right(a, 3) == 4                      # count of 3s = 4 - 1 = 3
assert lower_bound(a, 9) == 6
assert min_eating_speed([3, 6, 7, 11], 8) == 4
assert find_min_rotated([4, 5, 6, 7, 0, 1, 2]) == 0
times = [1, 4, 10]                                  # floor lookup: largest time <= t
assert times[bisect_right(times, 5) - 1] == 4 and bisect_right(times, 0) - 1 == -1
assert bisect_left([("a", 1), ("c", 3)], "c", key=lambda p: p[0]) == 1   # key= needs Python 3.10+
print("binary-search templates ok")
```

**How to pick the template:** "Is it there?" → (1). "Where does it go / first ≥ / first that works" → (2)/(3) or `bisect`. If you can phrase the problem as "smallest x such that ___", use (3) and only write the predicate.

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 6): 875 Koko Eating Bananas** — the overflow lesson Python never teaches you.

| Python | Java |
|---|---|
| `(lo + hi) // 2` | `lo + (hi - lo) / 2` (plain `lo + hi` can overflow `int`) |
| `sum(...)` of big values | `long hours = 0;` |
| `(p + k - 1) // k` | `(p + (long) k - 1) / k` — `p + k` can overflow for values near 10⁹ |
| `bisect_left` | hand-written lower bound; `Arrays.binarySearch` returns `-(insertion) - 1` and any duplicate |
| `bisect_right(times, t) - 1` | `TreeMap.floorKey(t)` (returns `null` if none) |

```java
class KokoRep {
    static int minEatingSpeed(int[] piles, int h) {
        int lo = 1, hi = 0;
        for (int p : piles) hi = Math.max(hi, p);
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            long hours = 0;
            for (int p : piles) hours += (p + (long) mid - 1) / mid;
            if (hours <= h) hi = mid; else lo = mid + 1;
        }
        return lo;
    }

    public static void main(String[] args) {
        System.out.println(minEatingSpeed(new int[]{3, 6, 7, 11}, 8) + " "
                + minEatingSpeed(new int[]{1_000_000_000}, 2));   // 4 500000000
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Problems |
|---|---|
| Exact search in sorted array | 704 |
| Insert position / lower bound | 35 |
| 2-D matrix flattened | 74 |
| Search on the answer (min feasible value) | 875, 1011 |
| Rotated array — find min | 153 |
| Rotated array — find target | 33 |
| Floor lookup on timestamps | 981 |
| Binary search on an unsorted-but-structured array | 162 |
| Partition two sorted arrays | 4 |

---

## 6. How to recognise the pattern

- Input is **sorted** (or rotated sorted) and you need O(log n).
- "Find the minimum X such that…", "maximum X such that…", "at least", "within h hours / d days" → search on the answer.
- Constraint: values/answers up to 10⁹ but n ≤ 10⁵ → O(n log 10⁹) ≈ 3·10⁶ steps — fine even in Python.
- Feasibility is **monotonic**: if speed k works, any speed > k works.
- "Timestamp ≤ t" lookups → floor search.
- "Must run in O(log n)" in the statement is a giveaway.

---

## 7. Beginner problems (Week 6)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 704 | [Binary Search](https://leetcode.com/problems/binary-search/) | Easy | Closed-interval template; write it by hand (no `bisect`) the first time. |
| 35 | [Search Insert Position](https://leetcode.com/problems/search-insert-position/) | Easy | Lower bound with `hi = len(nums)`; return `lo`. Then confirm with `bisect_left`. |

---

## 8. Interview problems (Week 6)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 74 | [Search a 2D Matrix](https://leetcode.com/problems/search-a-2d-matrix/) | Medium | 6 | <details><summary>show</summary>Treat as one sorted array of length `R*C`; `r, c = divmod(mid, C)`.</details> |
| 875 | [Koko Eating Bananas](https://leetcode.com/problems/koko-eating-bananas/) | Medium | 6 | <details><summary>show</summary>Search k in `[1, max(piles)]`; feasible if Σ ceil(p/k) ≤ h.</details> |
| 153 | [Find Minimum in Rotated Sorted Array](https://leetcode.com/problems/find-minimum-in-rotated-sorted-array/) | Medium | 6 | <details><summary>show</summary>Compare `a[mid]` with `a[hi]`: greater → min is right of mid; else min is at mid or left.</details> |
| 33 | [Search in Rotated Sorted Array](https://leetcode.com/problems/search-in-rotated-sorted-array/) | Medium | 6 | <details><summary>show</summary>One half is sorted (`a[lo] <= a[mid]` → left half); if target lies within the sorted half's range go there, else go to the other half.</details> |
| 981 | [Time Based Key-Value Store](https://leetcode.com/problems/time-based-key-value-store/) | Medium | 6 | <details><summary>show</summary>`defaultdict(list)` of `(timestamp, value)`; timestamps arrive increasing, so `bisect_right(times, t) - 1` gives the floor.</details> |
| 1011 | [Capacity To Ship Packages Within D Days](https://leetcode.com/problems/capacity-to-ship-packages-within-d-days/) | Medium | 6 | <details><summary>show</summary>Search capacity in `[max(w), sum(w)]`; predicate = greedy day count ≤ days.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 162 | [Find Peak Element](https://leetcode.com/problems/find-peak-element/) | Medium | <details><summary>show</summary>If `a[mid] < a[mid + 1]` a peak exists to the right, else at mid or left — binary search without sortedness.</details> |
| 4 | [Median of Two Sorted Arrays](https://leetcode.com/problems/median-of-two-sorted-arrays/) | Hard | <details><summary>show</summary>Binary search the partition in the shorter array so left halves hold (m+n+1)//2 elements and `max_left ≤ min_right` on both sides; use `±inf` sentinels.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Binary Search**: 704 → 74 → 875 → 153 → 33 → 981 → 4.
Here: templates (1)(2)(3) from memory → 704 → 35 → 74 → 875 → 1011 → 153 → 33 → 981 → Java rep 875 → (W20–26) 162, 4.

---

## 10. Target number of problems

**8 new** in Week 6 + 2 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- `mid = (lo + hi) / 2` → a **float**; indexing with it raises `TypeError`. Use `//`.
- `math.ceil(p / k)` → float division; exact for LeetCode ranges but can round wrongly for huge ints. Prefer `(p + k - 1) // k`.
- `bisect` on a list that isn't sorted by the same key → silent wrong answers. `bisect.insort` is O(n) (the insertion shifts).
- `bisect_left(pairs, t)` on a list of tuples compares whole tuples; use `key=` (3.10+) or a parallel list of keys.
- `list.index(x)` is O(n) — not a binary search.

**General**
- Infinite loop: `while lo < hi` with `lo = mid` (when `hi = lo + 1`, mid = lo forever). With `lo < hi` use `lo = mid + 1` / `hi = mid`.
- Mixing templates: `while lo <= hi` with `hi = mid` → infinite loop.
- Wrong search bounds for search-on-answer (starting k at 0 → `ZeroDivisionError`).
- 33: `<` vs `<=` when deciding which half is sorted (`a[lo] <= a[mid]` — equality when lo == mid).

**Java-rep traps (875):** `(lo + hi) / 2` overflow, `int` hour sums, `p + k - 1` overflow.

---

## 12. Mastery criteria

- [ ] Write lower bound and `first_true` from memory, and prove termination (the interval strictly shrinks).
- [ ] Solve 875 and 153 in ≤ 15 min each from blank.
- [ ] Solve 33 in ≤ 20 min, handling the `lo == mid` case.
- [ ] Given an unseen "minimum X such that" problem, write the predicate and bounds in ≤ 5 min; solve 2 unseen Mediums in ≤ 25 min.
- [ ] Java rep: 875 in Java with overflow-safe arithmetic.

---

## 13. Revision schedule

| Review | Week 6 set |
|---|---|
| Day 0 | W6 |
| Day 3 | W6/W7 |
| Day 7 | W7 |
| Day 14 | W8 (review week — 33, 875 timed; Checkpoint 8) |
| Day 30 | W10 |

**Revisit:** Week 10 (BST search is the tree version of this), Week 17+ OA simulations (search-on-answer is common), Weeks 20–26 (162, 4).

---

## Worked example — 875. Koko Eating Bananas

**Clarify.** `piles[i]` bananas (1 ≤ n ≤ 10⁴, piles up to 10⁹), `h` hours with n ≤ h ≤ 10⁹. Each hour Koko eats up to k bananas from one pile (leftover capacity is wasted). Find the **minimum integer k** to finish within h hours.

**Brute force.** Try k = 1, 2, 3, … computing hours each time: O(max · n) = 10⁹ · 10⁴. Impossible.

**Optimise.** `hours(k) = Σ ceil(p / k)` is **non-increasing** in k → predicate `hours(k) <= h` is False…False True…True. Binary search k in `[1, max(piles)]` (k = max always works because h ≥ n). Cost: O(n · log max) ≈ 10⁴ · 30 = 3·10⁵ steps.

**Code (Python).**

```python
def min_eating_speed(piles: list[int], h: int) -> int:
    def can_finish(k: int) -> bool:
        hours = 0
        for p in piles:
            hours += (p + k - 1) // k      # integer ceil
            if hours > h:
                return False               # early exit
        return True

    lo, hi = 1, max(piles)
    while lo < hi:
        mid = (lo + hi) // 2
        if can_finish(mid):
            hi = mid                       # mid works → answer ≤ mid
        else:
            lo = mid + 1                   # mid too slow → answer > mid
    return lo


assert min_eating_speed([3, 6, 7, 11], 8) == 4
assert min_eating_speed([30, 11, 23, 4, 20], 5) == 30
assert min_eating_speed([30, 11, 23, 4, 20], 6) == 23
assert min_eating_speed([1_000_000_000], 2) == 500_000_000
assert min_eating_speed([312884470], 312884469) == 2
print("875 worked example passed")
```

**Test cases.** `[3,6,7,11], h=8` → 4 · `h == n` → answer is the max pile (30) · `[30,11,23,4,20], h=6` → 23 · single huge pile → 5·10⁸ · `[312884470], h=312884469` → 2 (off-by-one trap at the boundary).

**Complexity.** Time O(n log M), M = max pile. Space O(1).
