# 08 — Linked Lists

> **Week 7** · NeetCode section: **Linked List** · Target: **8 new problems** + stretch pool · Java rep: **146** (Week 7)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#08--linked-lists) · Python: [`19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md) (references & aliasing) · Java: [quick reference](./java-dsa-toolkit.md)

Linked-list problems test **pointer manipulation under pressure**: can you rewire `next` references without losing part of the list? The toolkit is small — dummy head, fast/slow pointers, in-place reversal — but bugs are easy. Draw boxes and arrows every time.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Node structure | `class ListNode: val, next` — every variable holding a node is a **reference** (Python names are references to objects, just like Java object variables) |
| Singly vs doubly linked | Doubly: `prev` + `next`, O(1) removal given a node — used by LRU caches (`collections.OrderedDict` is built on one) |
| Complexity | Access by index O(n); insert/delete at a known node O(1); no random access |
| Dummy (sentinel) head | `dummy = ListNode(0, head)` removes all "is it the head?" special cases |
| Fast/slow pointers | Slow moves 1, fast moves 2: middle of list, cycle detection (Floyd), k-th from end (gap of k) |
| In-place reversal | `prev, cur` loop — memorise it (Python's tuple assignment makes it one line, but write it in steps first) |
| Merge | Two sorted lists → dummy + tail pointer |
| Combining techniques | 143 Reorder List = find middle + reverse second half + merge alternately |
| Hash map of nodes | Old node → new node (138 deep copy); nodes are hashable by identity by default |
| Identity vs equality | `a is b` checks the same node; `==` falls back to identity unless `__eq__` is defined |
| Floyd's cycle start | After meeting, reset one pointer to head; move both by 1; they meet at the cycle start (287) |

---

## 2. Prerequisite knowledge

- [`02-hashing.md`](./02-hashing.md) (dict of node → node), [`03-two-pointers.md`](./03-two-pointers.md) (fast/slow is two pointers).
- Python references and aliasing — [`19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md).
- Java references (for the rep): [`01-java/06-memory-jvm.md`](../01-java/06-memory-jvm.md).

---

## 3. Python implementation

### 3.1 A singly linked list from scratch

```python
from __future__ import annotations


class _Node:
    __slots__ = ("val", "next")               # smaller objects, faster attribute access

    def __init__(self, val: int, next: _Node | None = None) -> None:
        self.val = val
        self.next = next


class LinkedList:
    def __init__(self) -> None:
        self._dummy = _Node(0)                # sentinel: _dummy.next is the real head
        self._tail = self._dummy
        self._size = 0

    def add_first(self, v: int) -> None:
        self._dummy.next = _Node(v, self._dummy.next)
        if self._tail is self._dummy:
            self._tail = self._dummy.next
        self._size += 1

    def add_last(self, v: int) -> None:       # O(1) thanks to the tail pointer
        self._tail.next = _Node(v)
        self._tail = self._tail.next
        self._size += 1

    def remove_first(self, v: int) -> bool:   # O(n)
        prev = self._dummy
        while prev.next:
            if prev.next.val == v:
                if prev.next is self._tail:
                    self._tail = prev
                prev.next = prev.next.next    # unlink
                self._size -= 1
                return True
            prev = prev.next
        return False

    def reverse(self) -> None:                # O(n) time, O(1) space
        prev, cur = None, self._dummy.next
        self._tail = cur or self._dummy
        while cur:
            nxt = cur.next                    # 1. save
            cur.next = prev                   # 2. rewire
            prev = cur                        # 3. advance prev
            cur = nxt                         # 4. advance cur
        self._dummy.next = prev

    def to_list(self) -> list[int]:
        out, n = [], self._dummy.next
        while n:
            out.append(n.val)
            n = n.next
        return out


ll = LinkedList()
ll.add_last(2); ll.add_last(3); ll.add_first(1); ll.add_last(4)
assert ll.remove_first(3) and not ll.remove_first(99)
assert ll.to_list() == [1, 2, 4]
ll.reverse()
ll.add_last(0)                                # tail was fixed up by reverse()
assert ll.to_list() == [4, 2, 1, 0]
print("LinkedList ok")
```

### 3.2 LeetCode-style templates (`ListNode`)

```python
from __future__ import annotations


class ListNode:
    def __init__(self, val: int = 0, next: ListNode | None = None) -> None:
        self.val = val
        self.next = next


def from_list(vals: list[int]) -> ListNode | None:
    dummy = tail = ListNode()
    for v in vals:
        tail.next = ListNode(v)
        tail = tail.next
    return dummy.next


def to_list(h: ListNode | None) -> list[int]:
    out = []
    while h:
        out.append(h.val)
        h = h.next
    return out


def reverse(head: ListNode | None) -> ListNode | None:
    prev = None
    while head:
        head.next, prev, head = prev, head, head.next   # RHS evaluated first, then assigned left→right
    return prev


def middle(head: ListNode) -> ListNode:          # second middle for even length
    slow = fast = head
    while fast and fast.next:
        slow, fast = slow.next, fast.next.next
    return slow


def has_cycle(head: ListNode | None) -> bool:    # Floyd, O(1) space
    slow = fast = head
    while fast and fast.next:
        slow, fast = slow.next, fast.next.next
        if slow is fast:                         # identity check
            return True
    return False


def merge(a: ListNode | None, b: ListNode | None) -> ListNode | None:
    dummy = tail = ListNode()
    while a and b:
        if a.val <= b.val:
            tail.next, a = a, a.next
        else:
            tail.next, b = b, b.next
        tail = tail.next
    tail.next = a or b
    return dummy.next


def remove_nth_from_end(head: ListNode | None, n: int) -> ListNode | None:
    dummy = ListNode(0, head)
    fast = slow = dummy
    for _ in range(n + 1):                       # fast is n+1 ahead
        fast = fast.next
    while fast:
        fast, slow = fast.next, slow.next
    slow.next = slow.next.next                   # slow is just before the target
    return dummy.next


assert to_list(reverse(from_list([1, 2, 3]))) == [3, 2, 1] and reverse(None) is None
assert middle(from_list([1, 2, 3, 4])).val == 3
c = from_list([1, 2, 3])
c.next.next.next = c.next
assert has_cycle(c) and not has_cycle(from_list([1]))
assert to_list(merge(from_list([1, 2, 4]), from_list([1, 3, 4]))) == [1, 1, 2, 3, 4, 4]
assert to_list(remove_nth_from_end(from_list([1, 2, 3, 4, 5]), 2)) == [1, 2, 3, 5]
assert to_list(remove_nth_from_end(from_list([1]), 1)) == []
print("linked-list templates ok")
```

> The one-line reversal `head.next, prev, head = prev, head, head.next` works because Python evaluates the whole right side first. **Order on the left matters**: `prev, head, head.next = ...` would assign `head` before `head.next` and corrupt the list. In interviews, the 4-line version is safer.

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 7): 146 LRU Cache** — the version interviewers ask Java backend candidates.

| Python | Java |
|---|---|
| `while node:` | `while (node != null)` |
| `a is b` | `a == b` (reference equality for nodes) |
| `dict[int, Node]` | `HashMap<Integer, Node>` |
| `OrderedDict` + `move_to_end` / `popitem(last=False)` | `LinkedHashMap` with `accessOrder = true` + `removeEldestEntry` |

```java
import java.util.*;

class LRUCacheRep {
    private static final class Node { int key, val; Node prev, next; Node(int k, int v) { key = k; val = v; } }
    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0), tail = new Node(0, 0);

    LRUCacheRep(int capacity) { this.capacity = capacity; head.next = tail; tail.prev = head; }

    int get(int key) {
        Node n = map.get(key);
        if (n == null) return -1;
        unlink(n); addFront(n);
        return n.val;
    }

    void put(int key, int value) {
        Node n = map.get(key);
        if (n != null) { n.val = value; unlink(n); addFront(n); return; }
        if (map.size() == capacity) { Node lru = tail.prev; unlink(lru); map.remove(lru.key); }
        n = new Node(key, value);
        map.put(key, n);
        addFront(n);
    }

    private void unlink(Node n) { n.prev.next = n.next; n.next.prev = n.prev; }
    private void addFront(Node n) { n.next = head.next; n.prev = head; head.next.prev = n; head.next = n; }

    public static void main(String[] args) {
        LRUCacheRep c = new LRUCacheRep(2);
        c.put(1, 1); c.put(2, 2); c.get(1); c.put(3, 3);
        System.out.println(c.get(2) + " " + c.get(1) + " " + c.get(3));   // -1 1 3
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Problems |
|---|---|
| Reverse (iterative & recursive) | 206, 25 |
| Merge sorted lists | 21, (23 in [13](./13-heap-priority-queue.md)) |
| Fast/slow: middle | 876, 143 |
| Fast/slow: cycle / cycle start | 141, 287 |
| Gap of n: n-th from end | 19 |
| Find middle + reverse + weave | 143 |
| Digit-by-digit arithmetic with carry (`divmod`) | 2 |
| Deep copy with node map (or interleaving) | 138 |
| Doubly linked list + hash map | 146 |

---

## 6. How to recognise the pattern

- Input is `head: Optional[ListNode]` — obviously; the question is which technique.
- "Middle", "k-th from end", "cycle", "palindrome list", "O(1) extra space" → fast/slow.
- "Reverse", "reorder", "in groups of k" → in-place reversal with careful reconnection.
- "Design a cache with O(1) get/put and eviction by recency" → hash map + doubly linked list.
- "Array of n+1 integers in [1, n], find the duplicate, O(1) space, don't modify" → treat as a linked list (`i → nums[i]`) + Floyd.

---

## 7. Beginner problems (Week 7)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 206 | [Reverse Linked List](https://leetcode.com/problems/reverse-linked-list/) | Easy | `prev`/`cur`/`nxt`; also write the recursive version (you'll need it in [09](./09-recursion.md)). |
| 21 | [Merge Two Sorted Lists](https://leetcode.com/problems/merge-two-sorted-lists/) | Easy | Dummy + tail; `tail.next = a or b` at the end. |
| 141 | [Linked List Cycle](https://leetcode.com/problems/linked-list-cycle/) | Easy | Floyd: `while fast and fast.next`; compare with `is`. A `set` of seen nodes also works (O(n) space). |

---

## 8. Interview problems (Week 7)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 143 | [Reorder List](https://leetcode.com/problems/reorder-list/) | Medium | 7 | <details><summary>show</summary>Find middle, cut (`slow.next = None`), reverse the second half, merge alternately. Forgetting the cut creates a cycle.</details> |
| 19 | [Remove Nth Node From End of List](https://leetcode.com/problems/remove-nth-node-from-end-of-list/) | Medium | 7 | <details><summary>show</summary>Dummy head; advance fast n+1 steps; move both until fast is `None`; `slow.next = slow.next.next`.</details> |
| 2 | [Add Two Numbers](https://leetcode.com/problems/add-two-numbers/) | Medium | 7 | <details><summary>show</summary>Digits are reversed, so add from the heads with a carry: `carry, digit = divmod(a + b + carry, 10)`; loop while `l1 or l2 or carry`.</details> |
| 138 | [Copy List with Random Pointer](https://leetcode.com/problems/copy-list-with-random-pointer/) | Medium | 7 | <details><summary>show</summary>Pass 1: `{old: Node(old.val)}` (seed `{None: None}`). Pass 2: wire `next` and `random` through the dict.</details> |
| 146 | [LRU Cache](https://leetcode.com/problems/lru-cache/) | Medium | 7 | <details><summary>show</summary>Dict key → node + doubly linked list with dummy head/tail; move to front on access; evict `tail.prev`. (`OrderedDict` is the 10-line version — know both.)</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 876 | [Middle of the Linked List](https://leetcode.com/problems/middle-of-the-linked-list/) | Easy | <details><summary>show</summary>Fast/slow; returns the second middle for even lengths. Timed warm-up.</details> |
| 287 | [Find the Duplicate Number](https://leetcode.com/problems/find-the-duplicate-number/) | Medium | <details><summary>show</summary>Treat `i → nums[i]` as a linked list; the duplicate is the cycle entrance (Floyd phase 2).</details> |
| 25 | [Reverse Nodes in k-Group](https://leetcode.com/problems/reverse-nodes-in-k-group/) | Hard | <details><summary>show</summary>Check k nodes exist; reverse that segment; reconnect `group_prev.next` to the new head and the old head to the next group.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Linked List**: 206 → 21 → 143 → 19 → 138 → 2 → 141 → 287 → 146 → 23 → 25.
Here: `LinkedList` from memory → 206 → 21 → 141 → 19 → 143 → 2 → 138 → 146 → Java rep 146 → (W20–26) 876, 287, 25. (23 Merge k Sorted Lists is assigned in [13 Heap](./13-heap-priority-queue.md).)

---

## 10. Target number of problems

**8 new** in Week 7 + 3 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Tuple-assignment order bugs in one-line pointer swaps (see the note under §3.2).
- `if node.next:` when `node` itself may be `None` → `AttributeError: 'NoneType' object has no attribute 'next'`.
- Using `==` to compare nodes after someone added `__eq__` comparing values — use `is` for identity.
- Recursive list solutions on 5·10³–10⁵ nodes → `RecursionError` (default limit 1000). Iterate, or `sys.setrecursionlimit` with care.
- `copy.copy(head)` copies one node, not the list; `copy.deepcopy` on a long list can itself hit the recursion limit.
- 146: using a plain `dict` + `list` for recency → `list.remove(key)` is O(n).

**General**
- Losing the rest of the list: rewiring `cur.next` before saving it.
- No dummy head → special cases when deleting/inserting at the head (19 with n = length).
- Forgetting to terminate a list (`tail.next = None`) → accidental cycles (143).
- Returning `head` instead of `dummy.next` after the head changed.
- 2: forgetting the final carry (`[5] + [5]` → `[0, 1]`).

**Java-rep traps (146):** store the key inside the node (needed to remove it from the map on eviction); `LinkedHashMap` shortcut exists but interviewers usually want the manual version.

---

## 12. Mastery criteria

- [ ] Reverse a list iteratively **and** recursively in ≤ 3 min each, from memory.
- [ ] Implement 146 LRU Cache (manual doubly linked list) from blank in ≤ 25 min with all operations O(1), then state the `OrderedDict` alternative.
- [ ] Solve 143 in ≤ 20 min combining three techniques.
- [ ] Solve 2 unseen linked-list Mediums in ≤ 25 min each; always draw the pointers first.
- [ ] Java rep: 146 in Java in ≤ 25 min.

---

## 13. Revision schedule

| Review | Week 7 set |
|---|---|
| Day 0 | W7 |
| Day 3 | W7/W8 |
| Day 7 | W8 (review week) |
| Day 14 | W9 |
| Day 30 | W11 |

**Week 8 review-week plan** (only 6 new problems that week — recursion): re-solve timed 1, 49, 128 ([02](./02-hashing.md)), 15 ([03](./03-two-pointers.md)), 3, 424 ([05](./05-sliding-window.md)), 739 ([06](./06-stack-queue.md)), 33, 875 ([07](./07-binary-search.md)) — these feed [Checkpoint 8](../checkpoints/checkpoint-08.md).
**Revisit:** Week 8 (recursive 206), Week 11 (23 with a heap), Weeks 20–23 (146 again when FlagForge caches config snapshots — explain LRU vs TTL eviction), Weeks 20–26 stretch pool.

---

## Worked example — 146. LRU Cache

**Clarify.** Capacity ≥ 1. `get(key)` returns the value or −1 and marks the key most-recently used. `put(key, value)` inserts/updates and marks it MRU; if size exceeds capacity, evict the **least** recently used. Both **O(1)** average. Keys/values are ints.

**Brute force.** A list of `(key, value)` ordered by recency: `get` is O(n) search + move; `put` O(n). Fails the O(1) requirement.

**Optimise.** O(1) lookup → dict key → node. O(1) "move to front" and "remove LRU" → doubly linked list (removal needs `prev`). Dummy `head` and `tail` sentinels eliminate `None` checks. MRU right after `head`; LRU right before `tail`.

**Code (Python).**

```python
from collections import OrderedDict


class _Node:
    __slots__ = ("key", "val", "prev", "next")

    def __init__(self, key: int = 0, val: int = 0) -> None:
        self.key, self.val = key, val
        self.prev = self.next = None


class LRUCache:
    def __init__(self, capacity: int) -> None:
        self.capacity = capacity
        self.map: dict[int, _Node] = {}
        self.head, self.tail = _Node(), _Node()      # sentinels
        self.head.next, self.tail.prev = self.tail, self.head

    def _unlink(self, n: _Node) -> None:
        n.prev.next, n.next.prev = n.next, n.prev

    def _add_front(self, n: _Node) -> None:
        n.prev, n.next = self.head, self.head.next
        self.head.next.prev = n
        self.head.next = n

    def get(self, key: int) -> int:
        n = self.map.get(key)
        if n is None:
            return -1
        self._unlink(n)
        self._add_front(n)
        return n.val

    def put(self, key: int, value: int) -> None:
        n = self.map.get(key)
        if n is not None:                            # update + refresh; no eviction
            n.val = value
            self._unlink(n)
            self._add_front(n)
            return
        if len(self.map) == self.capacity:
            lru = self.tail.prev
            self._unlink(lru)
            del self.map[lru.key]                    # why the node stores its key
        n = _Node(key, value)
        self.map[key] = n
        self._add_front(n)


class LRUCacheOrderedDict:
    """The 10-line version: know it, but expect to be asked for the manual one."""

    def __init__(self, capacity: int) -> None:
        self.capacity, self.od = capacity, OrderedDict()

    def get(self, key: int) -> int:
        if key not in self.od:
            return -1
        self.od.move_to_end(key)
        return self.od[key]

    def put(self, key: int, value: int) -> None:
        self.od[key] = value
        self.od.move_to_end(key)
        if len(self.od) > self.capacity:
            self.od.popitem(last=False)              # evict least recently used


for cls in (LRUCache, LRUCacheOrderedDict):
    c = cls(2)
    c.put(1, 1); c.put(2, 2)
    assert c.get(1) == 1                             # order MRU→LRU: 1, 2
    c.put(3, 3)                                      # evicts 2
    assert c.get(2) == -1
    c.put(4, 4)                                      # evicts 1
    assert (c.get(1), c.get(3), c.get(4)) == (-1, 3, 4)
    one = cls(1)
    one.put(2, 1); one.put(2, 2)                     # update must not evict itself
    assert one.get(2) == 2
print("146 worked example passed")
```

**Test cases.** LeetCode example sequence (above) · capacity 1 with repeated `put` on the same key · `get` on a missing key → −1 · updating an existing key must **not** trigger eviction · `get` refreshes recency (put 1, put 2, get 1, put 3 → 2 evicted, not 1).

**Complexity.** `get` and `put` O(1) average. Space O(capacity).

**Interview follow-ups.** "Thread safety?" (one lock around both structures; in Java `ConcurrentHashMap` alone is not enough — see [`01-java/07-concurrency.md`](../01-java/07-concurrency.md)). "How does Redis evict?" (approximated LRU/LFU by sampling — [`04-sql-databases/redis.md`](../04-sql-databases/redis.md)).
