# 19 — Intervals

> **Week 16** · NeetCode section: **Intervals** · Target: **5 new problems** + stretch pool · Java rep: **56** (Week 16)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#19--intervals) · Python: [cheatsheet](../PYTHON_INTERVIEW_CHEATSHEET.md) (`sort` with `key=`, `heapq`) · Java: [quick reference §4](./java-dsa-toolkit.md#4-comparators-and-the-a---b-trap)

Interval problems give you pairs `[start, end]` and ask about overlaps: merge them, insert one, count the minimum rooms, remove the fewest to make them disjoint. Nearly every solution starts with **sorting** (by start or by end) and then a single linear scan — sometimes with a heap of end times.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Overlap test | `[a, b]` and `[c, d]` overlap iff `a <= d and c <= b` (closed intervals). Whether touching endpoints (`b == c`) count as overlap is **problem-specific** — ask |
| Sort by start | Merging and inserting: after sorting by start, an interval overlaps the previous merged one iff `start <= last_end` |
| Sort by end | Greedy selection (max non-overlapping, min arrows): always keep the interval that **ends first** |
| Sweep line | Turn intervals into events `(time, +1)` / `(time, -1)`; the running sum = active intervals; process ends before starts at equal times if touching doesn't overlap |
| Min-heap of end times | "Minimum meeting rooms": sort by start; pop rooms whose end ≤ current start; push the current end; heap size = rooms in use |
| Complexity | Dominated by the sort: O(n log n); scans are O(n) |
| Python sorting | `intervals.sort(key=lambda x: x[0])` (or just `sort()` — lists compare lexicographically); `sorted` returns a copy |
| Output building | Merge into `merged[-1][1] = max(...)` — you're mutating the list inside `merged` (fine), but don't mutate the **input** intervals unless allowed |

Practical use: [ForgeCI](../18-projects/forgeci/README.md) (this week: live logs) and [FlagForge](../18-projects/flagforge/README.md) (scheduled rollouts, Advanced) both reason about time windows — "how many jobs run concurrently?" is 253 with build start/end times; per-project concurrency limits are the same counting problem.

---

## 2. Prerequisite knowledge

- [`03-two-pointers.md`](./03-two-pointers.md) — sorted scans.
- [`13-heap-priority-queue.md`](./13-heap-priority-queue.md) — min-heap of end times (253).
- [`18-dp-1d.md`](./18-dp-1d.md) — some interval problems are DP (weighted scheduling); these five are sort + scan / greedy.

---

## 3. Python implementation

```python
import heapq


def overlaps(a: list[int], b: list[int]) -> bool:
    return a[0] <= b[1] and b[0] <= a[1]             # closed intervals: touching counts


def merge(intervals: list[list[int]]) -> list[list[int]]:
    merged: list[list[int]] = []
    for start, end in sorted(intervals):             # copy + sort by start (then end)
        if merged and start <= merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], end)  # extend the current block
        else:
            merged.append([start, end])              # new list, not the input's inner list
    return merged


def insert(intervals: list[list[int]], new: list[int]) -> list[list[int]]:
    """intervals are sorted and disjoint. Three phases: before, overlapping, after."""
    out, i, n = [], 0, len(intervals)
    while i < n and intervals[i][1] < new[0]:
        out.append(intervals[i]); i += 1
    start, end = new
    while i < n and intervals[i][0] <= end:
        start, end = min(start, intervals[i][0]), max(end, intervals[i][1]); i += 1
    out.append([start, end])
    out.extend(intervals[i:])
    return out


def max_non_overlapping(intervals: list[list[int]]) -> int:
    """Greedy by earliest end; touching endpoints allowed ([1,2] and [2,3] don't overlap)."""
    count, last_end = 0, float("-inf")
    for start, end in sorted(intervals, key=lambda x: x[1]):
        if start >= last_end:
            count += 1
            last_end = end
    return count


def min_rooms_heap(intervals: list[list[int]]) -> int:
    ends: list[int] = []                              # min-heap of end times of rooms in use
    for start, end in sorted(intervals):
        if ends and ends[0] <= start:
            heapq.heapreplace(ends, end)              # reuse the room that frees up first
        else:
            heapq.heappush(ends, end)
    return len(ends)


def min_rooms_sweep(intervals: list[list[int]]) -> int:
    events = []
    for s, e in intervals:
        events.append((s, 1))
        events.append((e, -1))
    events.sort()                                     # at equal times (t, -1) sorts before (t, 1): end frees first
    best = cur = 0
    for _, delta in events:
        cur += delta
        best = max(best, cur)
    return best


assert overlaps([1, 3], [3, 5]) and not overlaps([1, 2], [3, 4])
assert merge([[1, 3], [2, 6], [8, 10], [15, 18]]) == [[1, 6], [8, 10], [15, 18]]
assert merge([[1, 4], [4, 5]]) == [[1, 5]] and merge([[4, 7], [1, 4]]) == [[1, 7]]
assert insert([[1, 2], [3, 5], [6, 7], [8, 10], [12, 16]], [4, 8]) == [[1, 2], [3, 10], [12, 16]]
assert max_non_overlapping([[1, 2], [2, 3], [3, 4], [1, 3]]) == 3
meetings = [[0, 30], [5, 10], [15, 20]]
assert min_rooms_heap(meetings) == min_rooms_sweep(meetings) == 2
assert min_rooms_heap([[1, 5], [5, 10]]) == min_rooms_sweep([[1, 5], [5, 10]]) == 1
print("interval templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 16): 56 Merge Intervals.**

| Python | Java |
|---|---|
| `sorted(intervals)` | `Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]))` — never `a[0] - b[0]` |
| `merged: list[list[int]]`, `merged[-1][1] = ...` | `List<int[]> merged`; `merged.get(merged.size() - 1)[1] = ...` |
| return `merged` | `merged.toArray(new int[0][])` |
| `heapq` of ends | `PriorityQueue<Integer>` |

```java
import java.util.*;

class MergeIntervalsRep {
    static int[][] merge(int[][] intervals) {
        int[][] a = intervals.clone();
        Arrays.sort(a, (x, y) -> Integer.compare(x[0], y[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] cur : a) {
            if (!merged.isEmpty() && cur[0] <= merged.get(merged.size() - 1)[1]) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], cur[1]);
            } else {
                merged.add(new int[]{cur[0], cur[1]});     // copy: don't alias the input row
            }
        }
        return merged.toArray(new int[0][]);
    }

    public static void main(String[] args) {
        System.out.println(Arrays.deepToString(merge(new int[][]{{1, 3}, {2, 6}, {8, 10}, {15, 18}})));
        // [[1, 6], [8, 10], [15, 18]]
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Technique | Problems |
|---|---|---|
| Any overlap at all? | sort by start; check neighbours | 252 |
| Merge overlapping | sort by start; extend or append | 56 |
| Insert into sorted disjoint list | three phases (before / overlap / after) — O(n), no sort needed | 57 |
| Remove fewest to make disjoint | sort by end; greedy keep; answer = n − kept | 435 |
| Min resources at once | min-heap of ends, or sweep line | 253 |
| Min points hitting all intervals | sort by end; shoot at the end of the first unhit interval | 452 |

---

## 6. How to recognise the pattern

- Input is a list of `[start, end]` pairs (meetings, bookings, ranges, balloons, jobs with times).
- Words: "overlap", "merge", "conflict", "free time", "minimum number of rooms/arrows/platforms", "remove to make non-overlapping".
- n up to 10⁴–10⁵ → O(n log n) sort + O(n) scan.
- "Maximum number of non-overlapping" → greedy by end (proof in [20](./20-greedy.md)).
- Weighted intervals (each has a value, maximise total) → DP + binary search, not plain greedy.

---

## 7. Beginner problems (Week 16)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 252 | [Meeting Rooms](https://leetcode.com/problems/meeting-rooms/) *(LeetCode Premium — free on NeetCode)* | Easy | Sort by start; any `intervals[i][0] < intervals[i-1][1]` → False. |
| 56 | [Merge Intervals](https://leetcode.com/problems/merge-intervals/) | Medium | Sort by start; extend `merged[-1]` or append a new list. |

---

## 8. Interview problems (Week 16)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 57 | [Insert Interval](https://leetcode.com/problems/insert-interval/) | Medium | 16 | <details><summary>show</summary>Input is already sorted and disjoint → three phases in one pass: copy those ending before `new`, absorb overlaps into `new`, copy the rest. O(n).</details> |
| 435 | [Non-overlapping Intervals](https://leetcode.com/problems/non-overlapping-intervals/) | Medium | 16 | <details><summary>show</summary>Sort by end; keep an interval if `start >= last_end`; removals = n − kept. (Touching endpoints don't overlap here.)</details> |
| 253 | [Meeting Rooms II](https://leetcode.com/problems/meeting-rooms-ii/) *(LeetCode Premium — free on NeetCode)* | Medium | 16 | <details><summary>show</summary>Sort by start; min-heap of end times; reuse a room if `heap[0] <= start`; answer = heap size. Or sweep line over +1/−1 events.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 452 | [Minimum Number of Arrows to Burst Balloons](https://leetcode.com/problems/minimum-number-of-arrows-to-burst-balloons/) | Medium | <details><summary>show</summary>Sort by end; shoot at the first end; skip every balloon with `start <= arrow`; touching counts as hit here.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Intervals**: 57 → 56 → 435 → 252 → 253 → (1851 Minimum Interval to Include Each Query — Hard, optional, not tracked).
Here: §3 from memory → 252 → 56 → 57 → 435 → 253 (Week 16, after the 1-D DP problems) → Java rep 56 → (W20–26) 452.

---

## 10. Target number of problems

**5 new** in Week 16 (+ 3 1-D DP = 8) + 1 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- `intervals.sort()` mutates the caller's list — fine on LeetCode, but say so; `sorted(...)` if the input must stay untouched.
- `merged.append(interval)` then `merged[-1][1] = ...` **mutates the input's inner list** (aliasing). Append a copy `[start, end]`.
- `sort(key=lambda x: x[1])` vs default tuple/list ordering — know which key the algorithm needs (start for merge, end for greedy).
- `float("-inf")` initial `last_end` is fine; `0` breaks with negative coordinates (452 allows −2³¹).
- Sweep line: event tuples `(t, +1)` vs `(t, -1)` — the default sort puts `-1` first at equal `t`; if touching intervals **should** overlap, you need the opposite tie-break.

**General**
- Not asking whether touching endpoints overlap.
- 56: comparing with the previous **input** interval instead of the last **merged** one.
- 435: sorting by start and greedily keeping → wrong; sort by end.
- 253: counting overlaps pairwise → O(n²).

**Java-rep traps (56):** comparator overflow with `a[0] - b[0]` on large coordinates; `List<int[]>` → `toArray(new int[0][])`.

---

## 12. Mastery criteria

- [ ] Solve 56 and 57 in ≤ 15 min each from blank, without mutating the input.
- [ ] Prove in ≤ 4 sentences why "earliest end first" maximises the number of non-overlapping intervals (exchange argument).
- [ ] Solve 253 two ways (heap and sweep line) in ≤ 20 min.
- [ ] Solve 1 unseen interval Medium in ≤ 25 min.
- [ ] Java rep: 56 in Java in ≤ 15 min.

---

## 13. Revision schedule

| Review | Week 16 set (252, 56, 57, 435, 253) |
|---|---|
| Day 0 | W16 |
| Day 3 | W16/W17 |
| Day 7 | W17 (greedy week — pair 435 with [20](./20-greedy.md)) |
| Day 14 | W18 |
| Day 30 | W20 |

**Revisit:** Week 17 (ForgeCI per-project concurrency limits — explain with 253), Week 17+ OA sims (merging intervals is a classic OA task), Weeks 20–26 (452).

---

## Worked example — 56. Merge Intervals

**Clarify.** `intervals`: 1 ≤ n ≤ 10⁴, each `[start, end]` with 0 ≤ start ≤ end ≤ 10⁴. Merge all overlapping intervals; **touching** intervals (`[1,4]` and `[4,5]`) count as overlapping. Output order: any, but sorted by start is natural. Input is **not** sorted.

**Brute force.** Repeatedly scan all pairs and merge any overlapping pair until nothing changes → O(n²) per pass, up to O(n³). Too slow for 10⁴ in Python.

**Optimise.** Sort by start. Now any interval that overlaps the current merged block must start at or before the block's end (all later ones start even later). One pass: if `start <= merged[-1][1]`, extend the block's end with `max` (a contained interval must not shrink it); otherwise start a new block. O(n log n).

**Code (Python).**

```python
def merge(intervals: list[list[int]]) -> list[list[int]]:
    merged: list[list[int]] = []
    for start, end in sorted(intervals, key=lambda iv: iv[0]):
        if merged and start <= merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], end)   # max: [1,10] then [2,3] must stay [1,10]
        else:
            merged.append([start, end])               # fresh list → no aliasing with the input
    return merged


data = [[1, 3], [2, 6], [8, 10], [15, 18]]
assert merge(data) == [[1, 6], [8, 10], [15, 18]]
assert data == [[1, 3], [2, 6], [8, 10], [15, 18]]           # input untouched
assert merge([[1, 4], [4, 5]]) == [[1, 5]]                    # touching merges
assert merge([[1, 10], [2, 3]]) == [[1, 10]]                  # containment
assert merge([[4, 7], [1, 4]]) == [[1, 7]]                    # unsorted input
assert merge([[5, 5]]) == [[5, 5]]
print("56 worked example passed")
```

**Test cases.** Example → `[[1,6],[8,10],[15,18]]` · touching `[[1,4],[4,5]]` → `[[1,5]]` · containment `[[1,10],[2,3]]` → `[[1,10]]` (the `max`) · unsorted input · single interval · input not mutated.

**Complexity.** Time O(n log n) for the sort + O(n) scan. Space O(n) for the sorted copy and the output (O(log n)–O(n) auxiliary for Timsort).
