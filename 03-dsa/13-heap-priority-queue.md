# 13 — Heap / Priority Queue

> **Week 11** · NeetCode section: **Heap / Priority Queue** · Target: **8 new problems** + stretch pool · Java rep: **973** (Week 11)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#13--heap--priorityqueue) · Python: [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) (`heapq`) · Java: [quick reference §4](./java-dsa-toolkit.md#4-comparators-and-the-a---b-trap)

A heap gives you the min (or max) element in O(1) and lets you insert/remove in O(log n). Whenever a problem asks for "the k best", "the next most urgent", or "merge many sorted streams", a heap is usually the answer. Schedulers use the same idea: [ForgeCI](../18-projects/forgeci/README.md)'s job queue and Java's `ScheduledThreadPoolExecutor` (a heap-based delay queue) both pick "the next job due".

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Binary heap | Complete binary tree stored in an array; parent `(i - 1) // 2`, children `2i + 1`, `2i + 2` |
| Heap property | Min-heap: every parent ≤ its children (only the root is guaranteed minimum; the list is **not** sorted) |
| Sift up / sift down | Push at end + sift up: O(log n). Pop root: move last to root + sift down: O(log n) |
| Heapify | Build from a list bottom-up in **O(n)** (not O(n log n)) — `heapq.heapify(lst)` |
| `heapq` API | Functions on a plain `list`: `heappush`, `heappop`, `heapify`, `heappushpop`, `heapreplace`, `nlargest`, `nsmallest`, `merge`. `h[0]` is the min |
| Min-heap only | Max-heap: push `-x` and negate on pop. For tuples: `(-priority, ...)` |
| Tuple ordering | Heaps compare tuples lexicographically: `(dist, node)`. If the first fields tie, the second is compared → must be comparable, or add a tie-breaker counter `(dist, seq, obj)` |
| Top-k largest | **Min**-heap of size k: push, pop when size > k → O(n log k). Root = k-th largest |
| Top-k smallest / closest | Max-heap (negated keys) of size k |
| Two heaps | Max-heap for the lower half, min-heap for the upper half → running median |
| K-way merge | Heap holds the current head of each list: O(N log k) |
| Lazy deletion | Mark as stale and skip when popped (heapq has no decrease-key / arbitrary delete) |
| Heap vs sort vs quickselect | Sort O(n log n); heap O(n log k); quickselect O(n) average, O(n²) worst |

---

## 2. Prerequisite knowledge

- [`10-trees.md`](./10-trees.md) — complete binary trees, levels.
- [`02-hashing.md`](./02-hashing.md) — counting before top-k (347 revisited).
- [`08-linked-lists.md`](./08-linked-lists.md) — needed for 23.

---

## 3. Python implementation

### 3.1 A min-heap from scratch

```python
class MinHeap:
    def __init__(self, values: list[int] | None = None) -> None:
        self.a: list[int] = list(values) if values else []   # copy: don't alias the caller's list
        for i in range(len(self.a) // 2 - 1, -1, -1):        # O(n) bottom-up heapify
            self._sift_down(i)

    def push(self, x: int) -> None:
        self.a.append(x)
        self._sift_up(len(self.a) - 1)

    def peek(self) -> int:
        return self.a[0]

    def pop(self) -> int:
        a = self.a
        top = a[0]
        last = a.pop()
        if a:
            a[0] = last
            self._sift_down(0)
        return top

    def __len__(self) -> int:
        return len(self.a)

    def _sift_up(self, i: int) -> None:
        a = self.a
        while i > 0:
            p = (i - 1) // 2
            if a[p] <= a[i]:
                break
            a[p], a[i] = a[i], a[p]
            i = p

    def _sift_down(self, i: int) -> None:
        a, n = self.a, len(self.a)
        while True:
            l, r, smallest = 2 * i + 1, 2 * i + 2, i
            if l < n and a[l] < a[smallest]:
                smallest = l
            if r < n and a[r] < a[smallest]:
                smallest = r
            if smallest == i:
                return
            a[i], a[smallest] = a[smallest], a[i]
            i = smallest


h = MinHeap([9, 4, 7, 1, 8, 2])
h.push(0)
h.push(5)
assert [h.pop() for _ in range(len(h))] == [0, 1, 2, 4, 5, 7, 8, 9]   # heap sort!
print("MinHeap ok")
```

### 3.2 `heapq` templates

```python
import heapq
from itertools import count


def kth_largest(nums: list[int], k: int) -> int:
    """Top-k largest: min-heap of size k — O(n log k)."""
    heap: list[int] = []
    for x in nums:
        heapq.heappush(heap, x)
        if len(heap) > k:
            heapq.heappop(heap)              # drop the smallest → keep the k largest
    return heap[0]


def k_closest(points: list[list[int]], k: int) -> list[list[int]]:
    """Max-heap by distance via NEGATED keys; keep size k."""
    heap: list[tuple[int, int, int]] = []
    for x, y in points:
        heapq.heappush(heap, (-(x * x + y * y), x, y))
        if len(heap) > k:
            heapq.heappop(heap)              # removes the FARTHEST (most negative key)
    return [[x, y] for _, x, y in heap]


def merge_sorted(arrays: list[list[int]]) -> list[int]:
    """K-way merge: heap of (value, array index, element index)."""
    heap = [(arr[0], i, 0) for i, arr in enumerate(arrays) if arr]
    heapq.heapify(heap)
    out = []
    while heap:
        val, i, j = heapq.heappop(heap)
        out.append(val)
        if j + 1 < len(arrays[i]):
            heapq.heappush(heap, (arrays[i][j + 1], i, j + 1))
    return out


def last_stone(stones: list[int]) -> int:
    """Max-heap simulation (LC 1046 shape)."""
    heap = [-s for s in stones]
    heapq.heapify(heap)
    while len(heap) > 1:
        y, x = -heapq.heappop(heap), -heapq.heappop(heap)
        if y != x:
            heapq.heappush(heap, -(y - x))
    return -heap[0] if heap else 0


class Task:                                   # not comparable: no __lt__
    def __init__(self, name: str) -> None:
        self.name = name


def schedule(tasks: list[tuple[int, Task]]) -> list[str]:
    """Tie-breaker counter so equal priorities never compare Task objects."""
    seq = count()
    heap = [(prio, next(seq), task) for prio, task in tasks]
    heapq.heapify(heap)
    return [heapq.heappop(heap)[2].name for _ in range(len(heap))]


assert kth_largest([3, 2, 1, 5, 6, 4], 2) == 5
assert k_closest([[1, 3], [-2, 2]], 1) == [[-2, 2]]
assert merge_sorted([[1, 4, 5], [1, 3, 4], [2, 6]]) == [1, 1, 2, 3, 4, 4, 5, 6]
assert last_stone([2, 7, 4, 1, 8, 1]) == 1
assert schedule([(2, Task("b")), (1, Task("a")), (2, Task("c"))]) == ["a", "b", "c"]
assert heapq.nlargest(2, [5, 1, 9, 3]) == [9, 5] and heapq.nsmallest(1, [5, 1, 9]) == [1]
print("heapq templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 11): 973 K Closest Points to Origin.**

| Python | Java |
|---|---|
| `heapq` on a list (min-heap) | `PriorityQueue<T>` (min-heap) |
| max-heap via `-x` | `new PriorityQueue<>(Collections.reverseOrder())` or a reversed comparator |
| tuple `(dist, x, y)` ordering | `PriorityQueue<int[]>((a, b) -> Integer.compare(b[0], a[0]))` |
| tie-breaker `count()` | `thenComparingLong(...)` on a sequence field |
| `heap[0]` | `pq.peek()` |

```java
import java.util.*;

class KClosestRep {
    static int[][] kClosest(int[][] points, int k) {
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>(
                (p, q) -> Long.compare(dist(q), dist(p)));   // farthest on top; never (a, b) -> b - a
        for (int[] p : points) {
            maxHeap.offer(p);
            if (maxHeap.size() > k) maxHeap.poll();
        }
        return maxHeap.toArray(new int[0][]);
    }

    private static long dist(int[] p) { return (long) p[0] * p[0] + (long) p[1] * p[1]; }

    public static void main(String[] args) {
        System.out.println(Arrays.deepToString(kClosest(new int[][]{{1, 3}, {-2, 2}}, 1)));  // [[-2, 2]]
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Heap type | Problems |
|---|---|---|
| Stream: k-th largest so far | min-heap size k | 703 |
| Simulation: repeatedly take extremes | max-heap (negated) | 1046 |
| Top-k by a key (distance, frequency) | max-heap size k (or `nsmallest`) | 973, 347 (revisited) |
| k-th element in an array | min-heap size k / quickselect | 215 |
| Greedy scheduling by count | max-heap of counts + cooldown queue (or math formula) | 621, 767 |
| Merge k sorted / k-way feed | min-heap of heads | 23, 355 |
| Running median | two heaps | 295 |
| Event simulation by time | heap ordered by (time, index) | 1834 |

---

## 6. How to recognise the pattern

- "k largest / smallest / closest / most frequent", "k-th largest".
- "Continuously", "stream", "add numbers and query the median/max".
- "Merge k sorted lists/arrays", "news feed of the 10 most recent posts from followed users".
- Scheduling: "process the task with the smallest processing time among available ones".
- n up to 10⁵ with k ≪ n → O(n log k) beats sorting.
- Need arbitrary deletions or ordered iteration → a sorted structure (Java `TreeMap`), or a heap + lazy deletion in Python.

---

## 7. Beginner problems (Week 11)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 703 | [Kth Largest Element in a Stream](https://leetcode.com/problems/kth-largest-element-in-a-stream/) | Easy | Min-heap capped at size k (`heapify`, then pop down to k); `heap[0]` after each `add`. |
| 1046 | [Last Stone Weight](https://leetcode.com/problems/last-stone-weight/) | Easy | Negate values for a max-heap; smash the two largest until ≤ 1 stone. |

---

## 8. Interview problems (Week 11)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 973 | [K Closest Points to Origin](https://leetcode.com/problems/k-closest-points-to-origin/) | Medium | 11 | <details><summary>show</summary>Max-heap of size k keyed by `-(x² + y²)` (no sqrt). `heapq.nsmallest(k, points, key=...)` is the one-liner.</details> |
| 215 | [Kth Largest Element in an Array](https://leetcode.com/problems/kth-largest-element-in-an-array/) | Medium | 11 | <details><summary>show</summary>Min-heap of size k → O(n log k). Follow-up: quickselect O(n) average (random pivot; three-way partition for duplicates).</details> |
| 621 | [Task Scheduler](https://leetcode.com/problems/task-scheduler/) | Medium | 11 | <details><summary>show</summary>Max-heap of counts + `deque` of `(count, ready_time)`; or formula `max(len(tasks), (max_count - 1) * (n + 1) + num_with_max_count)`.</details> |
| 355 | [Design Twitter](https://leetcode.com/problems/design-twitter/) | Medium | 11 | <details><summary>show</summary>Per-user tweet lists with a global decreasing timestamp; the news feed is a k-way merge of followees' most recent tweets, stop at 10.</details> |
| 295 | [Find Median from Data Stream](https://leetcode.com/problems/find-median-from-data-stream/) | Hard | 11 | <details><summary>show</summary>Max-heap `low` (negated), min-heap `high`; keep `len(low) == len(high)` or one more; median from the tops.</details> |
| 23 | [Merge k Sorted Lists](https://leetcode.com/problems/merge-k-sorted-lists/) | Hard | 11 | <details><summary>show</summary>Heap of `(node.val, i, node)` — the index `i` breaks ties so `ListNode`s are never compared. O(N log k).</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 767 | [Reorganize String](https://leetcode.com/problems/reorganize-string/) | Medium | <details><summary>show</summary>Max-heap of `(-count, char)`; place the top char, hold it out for one step, re-insert. Impossible if max count > (n + 1) // 2.</details> |
| 1834 | [Single-Threaded CPU](https://leetcode.com/problems/single-threaded-cpu/) | Medium | <details><summary>show</summary>Sort tasks by enqueue time (keep indices); heap of available `(processing_time, index)`; jump time forward when idle.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Heap / Priority Queue**: 703 → 1046 → 973 → 215 → 621 → 355 → 295. (23 is in NeetCode's Linked List section; it's assigned here because it's a heap technique.)
Here: `MinHeap` from memory → 703 → 1046 → 973 → 215 → 23 → 621 → 355 → 295 → Java rep 973 → (W20–26) 767, 1834.

---

## 10. Target number of problems

**8 new** in Week 11 + 2 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Forgetting `heapq` is a **min**-heap → negate for max (`-x`), and negate back on pop.
- **Tuple comparison crashes:** `(dist, node)` where dists tie → Python compares the `ListNode`s → `TypeError: '<' not supported`. Add a unique tie-breaker `(dist, i, node)` or `itertools.count()`.
- Negating the wrong field in a tuple (`(-x, y)` vs `(x, -y)`) — decide which field drives the order.
- Treating the heap list as sorted (`heap[1]` is **not** the second smallest; `heap[-1]` is not the max).
- `heapq.heappush(h, x)` on a list you haven't `heapify`'d → invariants broken; always start from `[]` or `heapify`.
- `heapify` returns `None` (in place): `h = heapq.heapify(xs)` is a bug.
- Removing an arbitrary element with `h.remove(x)` → O(n) + must `heapify` again; prefer lazy deletion.
- 295: median with `//` on negatives or ints → use `/ 2` for a float result.

**General**
- Top-k largest with a max-heap of all n elements when a size-k min-heap is O(n log k).
- Mutating an item's priority while it's inside the heap — the heap doesn't re-order. Push a new entry and skip stale ones.

**Java-rep traps (973):** comparator `(a, b) -> b - a` overflows — use `Integer.compare`/`Long.compare`; distances in `long`.

---

## 12. Mastery criteria

- [ ] Implement `MinHeap` with `push`, `pop`, `_sift_up`, `_sift_down` and O(n) heapify from memory in ≤ 15 min.
- [ ] Explain why top-k largest uses a **min**-heap, and how to get a max-heap from `heapq`.
- [ ] Solve 295 in ≤ 20 min and 23 in ≤ 15 min from blank (including the tie-breaker).
- [ ] Solve 2 unseen heap Mediums in ≤ 25 min each and compare heap vs sort vs quickselect.
- [ ] Java rep: 973 in Java with a safe comparator.

---

## 13. Revision schedule

| Review | Week 11 set |
|---|---|
| Day 0 | W11 |
| Day 3 | W11/W12 |
| Day 7 | W12 |
| Day 14 | W13 |
| Day 30 | W15 |

**Revisit:** Week 15 (ForgeCI job queue — explain FIFO vs priority scheduling), Week 16 (intervals: 253 uses a min-heap of end times), Week 19 (Dijkstra = BFS with a heap, [22](./22-advanced-graphs.md)), Weeks 20–26 (767, 1834), OA sims (heap simulations are common).

---

## Worked example — 295. Find Median from Data Stream

**Clarify.** `addNum(num)` up to 5·10⁴ calls; `findMedian()` returns a float — the middle value, or the mean of the two middle values when the count is even. `findMedian` is only called when at least one number exists. Values in [−10⁵, 10⁵].

**Brute force.** Keep a list; sort on every `findMedian` → O(n log n) per query. Or `bisect.insort` into a sorted list → O(n) per add (shifting) → up to ~10⁹ element moves in the worst case. Too slow.

**Optimise.** Split the numbers into a lower half (max-heap `low`, stored negated) and an upper half (min-heap `high`). Invariants: every element of `low` ≤ every element of `high`; `len(low) == len(high)` or `len(low) == len(high) + 1`. Median = `-low[0]` (odd count) or the mean of both tops. Add: push into `low`, move `low`'s max to `high` (fixes ordering), then if `high` got bigger, move its min back (fixes sizes). O(log n) per add, O(1) per median.

**Code (Python).**

```python
import heapq


class MedianFinder:
    def __init__(self) -> None:
        self.low: list[int] = []    # max-heap via negation: -low[0] is the largest of the low half
        self.high: list[int] = []   # min-heap: high[0] is the smallest of the high half

    def addNum(self, num: int) -> None:
        heapq.heappush(self.low, -num)
        heapq.heappush(self.high, -heapq.heappop(self.low))   # ordering invariant
        if len(self.high) > len(self.low):
            heapq.heappush(self.low, -heapq.heappop(self.high))  # size invariant

    def findMedian(self) -> float:
        if len(self.low) > len(self.high):
            return float(-self.low[0])
        return (-self.low[0] + self.high[0]) / 2


m = MedianFinder()
m.addNum(1); m.addNum(2)
assert m.findMedian() == 1.5
m.addNum(3)
assert m.findMedian() == 2.0
n = MedianFinder()
for x in (5, -1, 5, 5, -1):
    n.addNum(x)
assert n.findMedian() == 5.0
s = MedianFinder()
s.addNum(-7)
assert s.findMedian() == -7.0
d = MedianFinder()
for x in (3, 2, 1):
    d.addNum(x)
assert d.findMedian() == 2.0
e = MedianFinder()
e.addNum(-1); e.addNum(-2)
assert e.findMedian() == -1.5
print("295 worked example passed")
```

**Test cases.** `add 1, add 2 → 1.5, add 3 → 2.0` · duplicates and negatives `[5,-1,5,5,-1]` → 5.0 · single element → itself · descending inserts `3,2,1` → 2.0 · even count with negatives `[-1,-2]` → −1.5 (true division, not `//`).

**Complexity.** `addNum` O(log n) (three heap operations), `findMedian` O(1). Space O(n).

**Follow-ups interviewers ask.** "All numbers in [0, 100]?" → 101 counting buckets, O(100) median. "99% of numbers in [0, 100]?" → buckets plus two overflow heaps.
