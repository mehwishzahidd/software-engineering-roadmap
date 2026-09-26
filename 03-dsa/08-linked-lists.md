# 08 — Linked Lists

> **Weeks 7–8** · NeetCode section: **Linked List** · Target: **9 new problems** (W7: 3, W8: 6) + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#08--linked-lists) · Java APIs: [`java-dsa-toolkit.md`](./java-dsa-toolkit.md)

Linked-list problems test **pointer manipulation under pressure**: can you rewire `next` references without losing part of the list? The toolkit is small — dummy head, fast/slow pointers, in-place reversal — but bugs are easy. Draw boxes and arrows every time. Week 8 is also a **review week** — see §12.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Node structure | `class ListNode { int val; ListNode next; }` — each node is a heap object; the variable holds a **reference** (ties to Java memory, Week 6) |
| Singly vs doubly linked | Doubly: `prev` + `next`, O(1) removal given a node — used by `LinkedHashMap` / LRU |
| Complexity | Access by index O(n); insert/delete at a known node O(1); no random access, poor cache locality |
| Dummy (sentinel) head | `ListNode dummy = new ListNode(0, head)` removes all "is it the head?" special cases |
| Fast/slow pointers | Slow moves 1, fast moves 2: middle of list, cycle detection (Floyd), k-th from end (gap of k) |
| In-place reversal | Three pointers `prev`, `cur`, `next` — memorise it |
| Merge | Two sorted lists → dummy + tail pointer |
| Combining techniques | 143 Reorder List = find middle + reverse second half + merge alternately |
| Hash map of nodes | Old node → new node (138 deep copy) |
| Floyd's cycle start | After meeting, reset one pointer to head; move both by 1; they meet at the cycle start (287) |

---

## 2. Prerequisite knowledge

- [`02-hashing.md`](./02-hashing.md) (maps of node → node), [`03-two-pointers.md`](./03-two-pointers.md) (fast/slow is two pointers).
- Java references, `null`, pass-by-value of references — [`01-java/06-memory-jvm.md`](../01-java/06-memory-jvm.md).
- [Toolkit §4 HashMap](./java-dsa-toolkit.md#4-hashmap) for 138 and 146.

---

## 3. Java implementation

### 3.1 A singly linked list from scratch

```java
import java.util.*;

class MyLinkedList {
    private static final class Node {
        int val; Node next;
        Node(int val, Node next) { this.val = val; this.next = next; }
    }

    private final Node dummy = new Node(0, null);   // sentinel: dummy.next is the real head
    private Node tail = dummy;
    private int size;

    void addFirst(int v) {
        dummy.next = new Node(v, dummy.next);
        if (tail == dummy) tail = dummy.next;
        size++;
    }

    void addLast(int v) {                           // O(1) thanks to tail pointer
        tail.next = new Node(v, null);
        tail = tail.next;
        size++;
    }

    boolean removeFirstOccurrence(int v) {          // O(n)
        for (Node prev = dummy; prev.next != null; prev = prev.next) {
            if (prev.next.val == v) {
                if (prev.next == tail) tail = prev;
                prev.next = prev.next.next;         // unlink
                size--;
                return true;
            }
        }
        return false;
    }

    void reverse() {                                // O(n), O(1) space
        Node prev = null, cur = dummy.next;
        tail = cur == null ? dummy : cur;
        while (cur != null) {
            Node next = cur.next;                   // 1. save
            cur.next = prev;                        // 2. rewire
            prev = cur;                             // 3. advance prev
            cur = next;                             // 4. advance cur
        }
        dummy.next = prev;
    }

    @Override public String toString() {
        StringJoiner sj = new StringJoiner(" -> ", "[", "]");
        for (Node n = dummy.next; n != null; n = n.next) sj.add(String.valueOf(n.val));
        return sj + " size=" + size;
    }

    public static void main(String[] args) {
        MyLinkedList l = new MyLinkedList();
        l.addLast(2); l.addLast(3); l.addFirst(1); l.addLast(4);
        l.removeFirstOccurrence(3);
        System.out.println(l);                      // [1 -> 2 -> 4] size=3
        l.reverse();
        l.addLast(0);
        System.out.println(l);                      // [4 -> 2 -> 1 -> 0] size=4
    }
}
```

### 3.2 LeetCode-style templates (`ListNode`)

```java
class ListNode {
    int val; ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

class ListTemplates {
    static ListNode reverse(ListNode head) {
        ListNode prev = null;
        while (head != null) { ListNode next = head.next; head.next = prev; prev = head; head = next; }
        return prev;
    }

    static ListNode middle(ListNode head) {         // second middle for even length (LC 876)
        ListNode slow = head, fast = head;
        while (fast != null && fast.next != null) { slow = slow.next; fast = fast.next.next; }
        return slow;
    }

    static boolean hasCycle(ListNode head) {        // Floyd (LC 141)
        ListNode slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next; fast = fast.next.next;
            if (slow == fast) return true;          // reference equality is what we want here
        }
        return false;
    }

    static ListNode merge(ListNode a, ListNode b) { // LC 21
        ListNode dummy = new ListNode(), tail = dummy;
        while (a != null && b != null) {
            if (a.val <= b.val) { tail.next = a; a = a.next; } else { tail.next = b; b = b.next; }
            tail = tail.next;
        }
        tail.next = (a != null) ? a : b;
        return dummy.next;
    }

    static ListNode removeNthFromEnd(ListNode head, int n) {   // LC 19: gap of n
        ListNode dummy = new ListNode(0, head), fast = dummy, slow = dummy;
        for (int i = 0; i <= n; i++) fast = fast.next;          // fast is n+1 ahead
        while (fast != null) { fast = fast.next; slow = slow.next; }
        slow.next = slow.next.next;                             // slow is just before the target
        return dummy.next;
    }

    static ListNode of(int... vals) {
        ListNode dummy = new ListNode(), t = dummy;
        for (int v : vals) { t.next = new ListNode(v); t = t.next; }
        return dummy.next;
    }

    static String str(ListNode h) {
        StringBuilder sb = new StringBuilder("[");
        for (; h != null; h = h.next) sb.append(h.val).append(h.next != null ? "," : "");
        return sb.append("]").toString();
    }

    public static void main(String[] args) {
        System.out.println(str(reverse(of(1, 2, 3))));                    // [3,2,1]
        System.out.println(middle(of(1, 2, 3, 4)).val);                   // 3
        ListNode c = of(1, 2, 3); c.next.next.next = c.next;
        System.out.println(hasCycle(c) + " " + hasCycle(of(1)));          // true false
        System.out.println(str(merge(of(1, 2, 4), of(1, 3, 4))));        // [1,1,2,3,4,4]
        System.out.println(str(removeNthFromEnd(of(1, 2, 3, 4, 5), 2))); // [1,2,3,5]
        System.out.println(str(removeNthFromEnd(of(1), 1)));             // []
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Problems |
|---|---|
| Reverse (iterative & recursive) | 206, 25 |
| Merge sorted lists | 21, (23 in [13](./13-heap-priority-queue.md)) |
| Fast/slow: middle | 876, 143 |
| Fast/slow: cycle / cycle start | 141, 287 |
| Gap of n: n-th from end | 19 |
| Find middle + reverse + weave | 143 |
| Digit-by-digit arithmetic with carry | 2 |
| Deep copy with node map (or interleaving) | 138 |
| Doubly linked list + hash map | 146 |

---

## 5. How to recognise the pattern

- Input is `ListNode head` — obviously; the question is which technique.
- "Middle", "k-th from end", "cycle", "palindrome list", "O(1) extra space" → fast/slow.
- "Reverse", "reorder", "in groups of k" → in-place reversal with careful reconnection.
- "Design a cache with O(1) get/put and eviction by recency" → hash map + doubly linked list.
- "Array of n+1 integers in [1, n], find duplicate, O(1) space, no modification" → treat as linked list (`i → nums[i]`) + Floyd.

---

## 6. Beginner problems (Week 7)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 206 | [Reverse Linked List](https://leetcode.com/problems/reverse-linked-list/) | Easy | prev/cur/next; also write the recursive version (you'll need it in [09](./09-recursion.md)). |
| 21 | [Merge Two Sorted Lists](https://leetcode.com/problems/merge-two-sorted-lists/) | Easy | Dummy + tail; attach the leftover list at the end. |
| 876 | [Middle of the Linked List](https://leetcode.com/problems/middle-of-the-linked-list/) | Easy | Fast/slow; loop `while (fast != null && fast.next != null)`. |

---

## 7. Interview problems (Week 8)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 141 | [Linked List Cycle](https://leetcode.com/problems/linked-list-cycle/) | Easy | 8 | <details><summary>show</summary>Floyd: fast gains one step per iteration on slow inside the cycle, so they must meet. O(1) space vs a `HashSet<ListNode>`.</details> |
| 143 | [Reorder List](https://leetcode.com/problems/reorder-list/) | Medium | 8 | <details><summary>show</summary>Find middle, cut, reverse second half, merge alternately. Cut the first half (`slow.next = null`) or you create a cycle.</details> |
| 19 | [Remove Nth Node From End of List](https://leetcode.com/problems/remove-nth-node-from-end-of-list/) | Medium | 8 | <details><summary>show</summary>Dummy head; advance fast n+1 steps; move both until fast is null; `slow.next = slow.next.next`.</details> |
| 2 | [Add Two Numbers](https://leetcode.com/problems/add-two-numbers/) | Medium | 8 | <details><summary>show</summary>Digits are reversed, so add from heads with a carry; loop while `l1 != null || l2 != null || carry != 0`.</details> |
| 138 | [Copy List with Random Pointer](https://leetcode.com/problems/copy-list-with-random-pointer/) | Medium | 8 | <details><summary>show</summary>Pass 1: `Map<Node,Node>` old → new copy. Pass 2: wire `next` and `random` via the map (`map.get(null)` = null).</details> |
| 146 | [LRU Cache](https://leetcode.com/problems/lru-cache/) | Medium | 8 | <details><summary>show</summary>`HashMap<key, Node>` + doubly linked list with dummy head/tail; move to front on access; evict `tail.prev`.</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 25 | [Reverse Nodes in k-Group](https://leetcode.com/problems/reverse-nodes-in-k-group/) | Hard | <details><summary>show</summary>Check k nodes exist; reverse that segment; reconnect `groupPrev.next` to the new head and the old head to the next group.</details> |
| 287 | [Find the Duplicate Number](https://leetcode.com/problems/find-the-duplicate-number/) | Medium | <details><summary>show</summary>Treat `i → nums[i]` as a linked list; the duplicate is the cycle entrance (Floyd phase 2).</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Linked List**: 206 → 21 → 143 → 19 → 138 → 2 → 141 → 287 → 146 → 23 → 25.
Here: `MyLinkedList` from memory → 206 → 21 → 876 (W7) → 141 → 19 → 143 → 2 → 138 → 146 (W8) → (W21–26) 287, 25. (23 Merge k Sorted Lists is assigned in [13 Heap](./13-heap-priority-queue.md).)

---

## 9. Target number of problems

**9 new**: 3 in Week 7, 6 in Week 8 (review week — ROADMAP §5 lists 6 new) + 2 stretch.

---

## 10. Mistakes beginners commonly make

- Losing the rest of the list: rewiring `cur.next` before saving it.
- Dereferencing `null`: `fast.next.next` without checking `fast != null && fast.next != null`.
- Not using a dummy head → special cases for deleting/inserting at the head (19 with n = length).
- Forgetting to terminate a list (`tail.next = null`) → accidental cycles (143).
- Returning `head` instead of `dummy.next` after the head changed.
- Java: comparing nodes with `.equals` when you mean identity — `==` is correct for node identity (no `equals` override).
- 146: `LinkedHashMap` with `accessOrder = true` and `removeEldestEntry` solves it in 10 lines — know it, but interviewers usually want the manual version.
- 2: forgetting the final carry (`[5] + [5]` → `[0,1]`).
- Printing a cyclic list in a debug loop → infinite output.

---

## 11. Mastery criteria

- [ ] Reverse a list iteratively **and** recursively in ≤ 3 min each, from memory.
- [ ] Implement 146 LRU Cache (manual doubly linked list) from blank in ≤ 25 min with all operations O(1).
- [ ] Solve 143 in ≤ 20 min combining three techniques.
- [ ] Solve 2 unseen linked-list Mediums in ≤ 25 min each; always draw the pointers first.

---

## 12. Revision schedule

| Review | Week 7 set (206, 21, 876) | Week 8 set (141, 143, 19, 2, 138, 146) |
|---|---|---|
| Day 0 | W7 | W8 |
| Day 3 | W7/W8 | W8/W9 |
| Day 7 | W8 | W9 |
| Day 14 | W9 | W10 |
| Day 30 | W11 | W12 |

**Week 8 review week plan:** new problems are capped at 6 so you can clear the queue: re-solve timed 1, 49, 128 ([02](./02-hashing.md)), 15 ([03](./03-two-pointers.md)), 3, 424 ([05](./05-sliding-window.md)), 739 ([06](./06-stack-queue.md)), 33, 875 ([07](./07-binary-search.md)) — these feed [Checkpoint 8](../checkpoints/checkpoint-08.md).
**Revisit:** Week 9 (recursive 206), Week 12 (23 with a heap), Week 20 (146 again when you build a Redis cache in P4 — explain LRU eviction), Weeks 21–26 (25, 287).

---

## Worked example — 146. LRU Cache

**Clarify.** Capacity ≥ 1. `get(key)` returns value or −1 and marks the key most-recently used. `put(key, value)` inserts/updates and marks it MRU; if size exceeds capacity, evict the **least** recently used. Both **O(1)** average. Keys/values are ints.

**Brute force.** A list of (key, value) ordered by recency: `get` is O(n) search + move; `put` O(n). Fails the O(1) requirement.

**Optimise.** Need O(1) lookup → `HashMap<key, Node>`. Need O(1) "move to front" and "remove LRU" → doubly linked list (removal needs `prev`). Dummy `head` and `tail` sentinels eliminate null checks. MRU lives right after `head`; LRU right before `tail`.

**Code.**

```java
import java.util.*;

class LRUCache {
    private static final class Node {
        int key, val; Node prev, next;
        Node(int key, int val) { this.key = key; this.val = val; }
    }

    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0), tail = new Node(0, 0);   // sentinels

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        Node n = map.get(key);
        if (n == null) return -1;
        unlink(n);
        addFront(n);
        return n.val;
    }

    public void put(int key, int value) {
        Node n = map.get(key);
        if (n != null) {                 // update + refresh
            n.val = value;
            unlink(n);
            addFront(n);
            return;
        }
        if (map.size() == capacity) {    // evict LRU
            Node lru = tail.prev;
            unlink(lru);
            map.remove(lru.key);         // this is why the node stores its key
        }
        n = new Node(key, value);
        map.put(key, n);
        addFront(n);
    }

    private void unlink(Node n) {
        n.prev.next = n.next;
        n.next.prev = n.prev;
    }

    private void addFront(Node n) {
        n.next = head.next;
        n.prev = head;
        head.next.prev = n;
        head.next = n;
    }

    public static void main(String[] args) {
        LRUCache c = new LRUCache(2);
        c.put(1, 1); c.put(2, 2);
        System.out.println(c.get(1));    // 1   (order MRU→LRU: 1, 2)
        c.put(3, 3);                     // evicts 2
        System.out.println(c.get(2));    // -1
        c.put(4, 4);                     // evicts 1
        System.out.println(c.get(1) + " " + c.get(3) + " " + c.get(4)); // -1 3 4
        LRUCache one = new LRUCache(1);
        one.put(2, 1); one.put(2, 2);    // update, no eviction of itself
        System.out.println(one.get(2));  // 2
    }
}
```

**Test cases.** LeetCode example sequence (above) · capacity 1 with repeated `put` on the same key · `get` on a missing key → −1 · updating an existing key must **not** trigger eviction · `get` refreshes recency (put 1, put 2, get 1, put 3 → 2 evicted, not 1).

**Complexity.** `get` and `put` O(1) average (hash lookup + constant pointer changes). Space O(capacity).

**Interview follow-ups.** "How would you make it thread-safe?" (single lock around both structures, or `ConcurrentHashMap` + striped locks — see [`01-java/07-concurrency.md`](../01-java/07-concurrency.md)). "How does Redis evict?" (approximated LRU/LFU by sampling — see [`04-sql-databases/redis.md`](../04-sql-databases/redis.md)).
