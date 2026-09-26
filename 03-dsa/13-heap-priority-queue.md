# 13 — Heap / PriorityQueue

> **Week 12** · NeetCode section: **Heap / Priority Queue** · Target: **8 new problems** + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#13--heap--priorityqueue) · Java APIs: [toolkit §7 PriorityQueue & comparators](./java-dsa-toolkit.md#7-priorityqueue-and-comparators)

A heap gives you the min (or max) element in O(1) and lets you insert/remove in O(log n). Whenever a problem asks for "the k best", "the next most urgent", or "merge many sorted streams", a heap is usually the answer. You'll use the same idea in [P4 PulseWatch](../18-projects/p4-pulsewatch/README.md)'s scheduler (next check due first) and Java's `ScheduledThreadPoolExecutor` uses a heap-based delay queue internally.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Binary heap | Complete binary tree stored in an array; parent `(i-1)/2`, children `2i+1`, `2i+2` |
| Heap property | Min-heap: every parent ≤ its children (only the root is guaranteed minimum; the array is **not** sorted) |
| Sift up / sift down | Insert at end + sift up: O(log n). Remove root: move last to root + sift down: O(log n) |
| Heapify | Build from an array bottom-up in **O(n)** (not O(n log n)) |
| Java `PriorityQueue` | Min-heap by default; `Collections.reverseOrder()` or a comparator for max-heap; `remove(Object)` is O(n) |
| Top-k largest | **Min**-heap of size k: push, and pop when size > k → O(n log k). The root is the k-th largest |
| Top-k smallest / closest | **Max**-heap of size k |
| Two heaps | Max-heap for the lower half, min-heap for the upper half → running median |
| K-way merge | Heap holds the current head of each list: O(N log k) |
| Lazy deletion | Mark as stale and skip when popped (instead of O(n) remove) |
| Heap vs sort vs quickselect | Sort O(n log n); heap O(n log k); quickselect O(n) average, O(n²) worst |
| Heap vs BST | Heap: only min/max fast, O(n) heapify, array-backed. `TreeMap`: any order query, O(log n) all ops |

---

## 2. Prerequisite knowledge

- [`10-trees.md`](./10-trees.md) — complete binary trees, levels.
- [`02-hashing.md`](./02-hashing.md) — counting before top-k (347 revisited).
- [`08-linked-lists.md`](./08-linked-lists.md) — needed for 23.
- [Toolkit §7](./java-dsa-toolkit.md#7-priorityqueue-and-comparators) — comparators and the `a - b` overflow trap.

---

## 3. Java implementation

### 3.1 A min-heap from scratch

```java
import java.util.*;

class MinHeap {
    private int[] a;
    private int size;

    MinHeap(int capacity) { a = new int[Math.max(1, capacity)]; }

    // O(n) bottom-up heapify
    MinHeap(int[] values) {
        a = Arrays.copyOf(values, Math.max(1, values.length));
        size = values.length;
        for (int i = size / 2 - 1; i >= 0; i--) siftDown(i);
    }

    void offer(int x) {
        if (size == a.length) a = Arrays.copyOf(a, size * 2);
        a[size] = x;
        siftUp(size++);
    }

    int peek() {
        if (size == 0) throw new NoSuchElementException();
        return a[0];
    }

    int poll() {
        if (size == 0) throw new NoSuchElementException();
        int top = a[0];
        a[0] = a[--size];
        siftDown(0);
        return top;
    }

    int size() { return size; }

    private void siftUp(int i) {
        while (i > 0) {
            int p = (i - 1) / 2;
            if (a[p] <= a[i]) break;
            swap(i, p);
            i = p;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int l = 2 * i + 1, r = l + 1, smallest = i;
            if (l < size && a[l] < a[smallest]) smallest = l;
            if (r < size && a[r] < a[smallest]) smallest = r;
            if (smallest == i) return;
            swap(i, smallest);
            i = smallest;
        }
    }

    private void swap(int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }

    public static void main(String[] args) {
        MinHeap h = new MinHeap(new int[]{9, 4, 7, 1, 8, 2});
        h.offer(0); h.offer(5);
        StringBuilder sb = new StringBuilder();
        while (h.size() > 0) sb.append(h.poll()).append(' ');
        System.out.println(sb.toString().trim());         // 0 1 2 4 5 7 8 9  (heap sort!)
    }
}
```

### 3.2 PriorityQueue templates

```java
import java.util.*;

class HeapTemplates {
    // Top-k largest: min-heap of size k — O(n log k)
    static int kthLargest(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        for (int x : nums) {
            minHeap.offer(x);
            if (minHeap.size() > k) minHeap.poll();      // drop the smallest → keep the k largest
        }
        return minHeap.peek();
    }

    // k closest points: max-heap by distance of size k (distance squared as long, no sqrt)
    static int[][] kClosest(int[][] points, int k) {
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>(
                (p, q) -> Long.compare(dist(q), dist(p)));  // reversed → max-heap
        for (int[] p : points) {
            maxHeap.offer(p);
            if (maxHeap.size() > k) maxHeap.poll();
        }
        return maxHeap.toArray(new int[0][]);
    }
    private static long dist(int[] p) { return (long) p[0] * p[0] + (long) p[1] * p[1]; }

    // K-way merge of sorted arrays: heap holds {value, arrayIndex, elementIndex}
    static List<Integer> mergeSorted(int[][] arrays) {
        PriorityQueue<int[]> pq = new PriorityQueue<>((x, y) -> Integer.compare(x[0], y[0]));
        for (int i = 0; i < arrays.length; i++) if (arrays[i].length > 0) pq.offer(new int[]{arrays[i][0], i, 0});
        List<Integer> out = new ArrayList<>();
        while (!pq.isEmpty()) {
            int[] cur = pq.poll();
            out.add(cur[0]);
            int i = cur[1], j = cur[2] + 1;
            if (j < arrays[i].length) pq.offer(new int[]{arrays[i][j], i, j});
        }
        return out;
    }

    // Simulation: repeatedly take the two largest (LC 1046 shape)
    static int lastStone(int[] stones) {
        PriorityQueue<Integer> max = new PriorityQueue<>(Collections.reverseOrder());
        for (int s : stones) max.offer(s);
        while (max.size() > 1) {
            int y = max.poll(), x = max.poll();
            if (y != x) max.offer(y - x);
        }
        return max.isEmpty() ? 0 : max.peek();
    }

    public static void main(String[] args) {
        System.out.println(kthLargest(new int[]{3, 2, 1, 5, 6, 4}, 2));                               // 5
        System.out.println(Arrays.deepToString(kClosest(new int[][]{{1, 3}, {-2, 2}}, 1)));           // [[-2, 2]]
        System.out.println(mergeSorted(new int[][]{{1, 4, 5}, {1, 3, 4}, {2, 6}}));                   // [1, 1, 2, 3, 4, 4, 5, 6]
        System.out.println(lastStone(new int[]{2, 7, 4, 1, 8, 1}));                                   // 1
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Heap type | Problems |
|---|---|---|
| Stream: k-th largest so far | min-heap size k | 703 |
| Simulation: repeatedly take extremes | max-heap | 1046 |
| Top-k by a key (distance, frequency) | max-heap size k (or min for largest) | 973, 347 (revisited) |
| k-th element in array | min-heap size k / quickselect | 215 |
| Greedy scheduling by count | max-heap of counts + cooldown queue (or math formula) | 621, 767 |
| Merge k sorted / k-way feed | min-heap of heads | 23, 355 |
| Running median | two heaps | 295 |
| Event simulation by time | heap ordered by (time, index) | 1834 |

---

## 5. How to recognise the pattern

- "k largest / smallest / closest / most frequent", "k-th largest".
- "Continuously", "stream", "add numbers and query the median/max".
- "Merge k sorted lists/arrays", "news feed of the 10 most recent posts from followed users".
- Scheduling: "process the task with the smallest processing time among available ones".
- n up to 10⁵ with k ≪ n → O(n log k) beats sorting.
- Need both ends / arbitrary deletions → `TreeMap` instead.

---

## 6. Beginner problems (Week 12)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 703 | [Kth Largest Element in a Stream](https://leetcode.com/problems/kth-largest-element-in-a-stream/) | Easy | Min-heap capped at size k; `peek()` is the answer after each `add`. |
| 1046 | [Last Stone Weight](https://leetcode.com/problems/last-stone-weight/) | Easy | Max-heap with `Collections.reverseOrder()`; smash the two largest until ≤ 1 stone. |

---

## 7. Interview problems (Week 12)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 973 | [K Closest Points to Origin](https://leetcode.com/problems/k-closest-points-to-origin/) | Medium | 12 | <details><summary>show</summary>Max-heap of size k keyed by x² + y² (no sqrt, compare as `long` or `Integer.compare`).</details> |
| 215 | [Kth Largest Element in an Array](https://leetcode.com/problems/kth-largest-element-in-an-array/) | Medium | 12 | <details><summary>show</summary>Min-heap size k → O(n log k). Follow-up: quickselect O(n) average (randomise the pivot).</details> |
| 621 | [Task Scheduler](https://leetcode.com/problems/task-scheduler/) | Medium | 12 | <details><summary>show</summary>Max-heap of counts + queue of (count, readyTime) for cooling tasks; or formula `max(n_tasks, (maxCount−1)(n+1) + numWithMaxCount)`.</details> |
| 355 | [Design Twitter](https://leetcode.com/problems/design-twitter/) | Medium | 12 | <details><summary>show</summary>Per-user tweet lists with a global timestamp; news feed = k-way merge of followees' most recent tweets with a max-heap, stop at 10.</details> |
| 295 | [Find Median from Data Stream](https://leetcode.com/problems/find-median-from-data-stream/) | Hard | 12 | <details><summary>show</summary>Max-heap `low`, min-heap `high`; keep `low.size() == high.size()` or one more; median from the tops.</details> |
| 23 | [Merge k Sorted Lists](https://leetcode.com/problems/merge-k-sorted-lists/) | Hard | 12 | <details><summary>show</summary>Min-heap of list heads (`Comparator.comparingInt(n -> n.val)`); pop, append, push its `next`. O(N log k).</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 767 | [Reorganize String](https://leetcode.com/problems/reorganize-string/) | Medium | <details><summary>show</summary>Max-heap of (count, char); place the top char, hold it out for one step, re-insert. Impossible if max count > (n+1)/2.</details> |
| 1834 | [Single-Threaded CPU](https://leetcode.com/problems/single-threaded-cpu/) | Medium | <details><summary>show</summary>Sort tasks by enqueue time; heap of available tasks by (processing time, index); jump time forward when idle. Use `long` time.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Heap / Priority Queue**: 703 → 1046 → 973 → 215 → 621 → 355 → 295. (23 is in NeetCode's Linked List section; it's assigned here because it's a heap technique.)
Here: `MinHeap` from memory → 703 → 1046 → 973 → 215 → 23 → 621 → 355 → 295 (W12) → (W21–26) 767, 1834.

---

## 9. Target number of problems

**8 new** in Week 12 + 2 stretch.

---

## 10. Mistakes beginners commonly make

- Forgetting Java's `PriorityQueue` is a **min**-heap.
- Comparator `(a, b) -> b - a` → overflow for large magnitudes. Use `Integer.compare(b, a)` / `Collections.reverseOrder()`.
- Distances as `int` `x*x + y*y` with coordinates up to 10⁴ is fine (2·10⁸) — but know when it isn't; use `long` by habit.
- Iterating a `PriorityQueue` (for-each / `toString`) expecting sorted order. Only `poll()` gives order.
- Using `pq.remove(obj)` in a loop → O(n) each; use lazy deletion.
- Top-k largest with a **max**-heap of all n elements (O(n log n) + O(k log n)) when a size-k min-heap is O(n log k).
- 295: not rebalancing after each add; computing median with integer division (`(a + b) / 2` → use `/ 2.0`).
- 23: pushing `null` lists into the heap → `NullPointerException` in the comparator.
- Mutating an object's priority field while it's inside the heap — the heap doesn't re-sort. Remove and re-insert.

---

## 11. Mastery criteria

- [ ] Implement `MinHeap` with `offer`, `poll`, `siftUp`, `siftDown` and O(n) heapify from memory in ≤ 15 min.
- [ ] Explain why top-k largest uses a **min**-heap.
- [ ] Solve 295 in ≤ 20 min and 23 in ≤ 15 min from blank.
- [ ] Solve 2 unseen heap Mediums in ≤ 25 min each and compare heap vs sort vs quickselect complexity.

---

## 12. Revision schedule

| Review | Week 12 set |
|---|---|
| Day 0 | W12 |
| Day 3 | W12/W13 |
| Day 7 | W13 |
| Day 14 | W14 |
| Day 30 | W16 (Checkpoint 16) |

**Revisit:** Week 17 (intervals: 253 meeting rooms uses a min-heap of end times), Week 20 (Dijkstra = BFS with a heap, [22](./22-advanced-graphs.md)), Weeks 21–26 (767, 1834), OA sims (heap simulations are common).

---

## Worked example — 295. Find Median from Data Stream

**Clarify.** `addNum(int)` up to 5·10⁴ calls; `findMedian()` returns a `double` — middle value, or the mean of the two middle values when the count is even. `findMedian` is only called when at least one number exists. Values in [−10⁵, 10⁵].

**Brute force.** Keep a list; sort on every `findMedian` → O(n log n) per query. Or insertion into a sorted `ArrayList` → O(n) per add (shifting). With 5·10⁴ operations, O(n) per add is ~10⁹ total shifts in the worst case — too slow.

**Optimise.** Split the numbers into a lower half (max-heap `low`) and an upper half (min-heap `high`). Invariants: every element of `low` ≤ every element of `high`; `low.size() == high.size()` or `low.size() == high.size() + 1`. Median = `low.peek()` (odd count) or the mean of both tops. Add: push into `low`, move `low`'s max to `high` (fixes ordering), then if `high` got bigger, move its min back (fixes sizes). O(log n) per add, O(1) per median.

**Code.**

```java
import java.util.*;

class MedianFinder {
    private final PriorityQueue<Integer> low = new PriorityQueue<>(Collections.reverseOrder()); // max-heap
    private final PriorityQueue<Integer> high = new PriorityQueue<>();                          // min-heap

    public void addNum(int num) {
        low.offer(num);
        high.offer(low.poll());                  // largest of the low half moves up → ordering invariant
        if (high.size() > low.size()) {
            low.offer(high.poll());              // size invariant: low has equal or one more
        }
    }

    public double findMedian() {
        if (low.size() > high.size()) return low.peek();
        return (low.peek() + (double) high.peek()) / 2.0;   // double arithmetic avoids int overflow/truncation
    }

    public static void main(String[] args) {
        MedianFinder m = new MedianFinder();
        m.addNum(1); m.addNum(2);
        System.out.println(m.findMedian());   // 1.5
        m.addNum(3);
        System.out.println(m.findMedian());   // 2.0
        MedianFinder n = new MedianFinder();
        for (int x : new int[]{5, -1, 5, 5, -1}) n.addNum(x);
        System.out.println(n.findMedian());   // 5.0
        MedianFinder single = new MedianFinder();
        single.addNum(-7);
        System.out.println(single.findMedian()); // -7.0
    }
}
```

**Test cases.** `add 1, add 2 → 1.5, add 3 → 2.0` · duplicates and negatives `[5,-1,5,5,-1]` → 5.0 · single element → itself · descending inserts `3,2,1` → 2.0 · even count with negatives `[-1,-2]` → −1.5.

**Complexity.** `addNum` O(log n) (three heap operations), `findMedian` O(1). Space O(n).

**Follow-ups interviewers ask.** "All numbers in [0, 100]?" → counting array of 101 buckets, O(100) median. "99% of numbers in [0, 100]?" → buckets plus two overflow counters/heaps.
