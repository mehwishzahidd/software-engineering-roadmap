# 11 — Binary Search Trees

> **Week 10** · NeetCode section: **Trees** (BST problems) · Target: **5 new problems** + stretch pool · Java rep: **98** (Week 10)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#11--binary-search-trees) · Python: [cheatsheet](../PYTHON_INTERVIEW_CHEATSHEET.md) · Java: [quick reference](./java-dsa-toolkit.md) (`TreeMap`)

A BST is a binary tree with an ordering invariant: **everything in the left subtree < node < everything in the right subtree**. That invariant gives O(h) search/insert/delete and sorted order via inorder traversal. Python's standard library has **no** balanced BST (a sorted list + `bisect` is the usual substitute); Java's `TreeMap`/`TreeSet` are red-black trees — this week you learn what's inside them.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| BST invariant | For **every** node: all keys in the left subtree < key < all keys in the right subtree (not just direct children!) |
| Search / insert | Walk left or right by comparison: O(h) |
| Delete | 0 children → remove; 1 child → replace by child; 2 children → copy the inorder successor (min of right subtree), then delete the successor |
| Inorder = sorted | Inorder traversal visits keys in ascending order — k-th smallest, validation, BST iterator |
| Height | Balanced: O(log n). Degenerate (inserting sorted data): O(n) — a linked list |
| Self-balancing trees (awareness) | AVL, red-black (Java `TreeMap`), B-trees (database indexes — [`04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md)) keep h = O(log n) |
| Python substitutes | Sorted `list` + `bisect` (O(log n) search, O(n) insert); `heapq` for min/max only; third-party `sortedcontainers` (not available on most judges/OAs — don't rely on it) |
| Bounds | Validate by passing an allowed open interval down the recursion (`float("-inf")`, `float("inf")`) |
| LCA in a BST | First node where p and q split (one ≤ node ≤ other) |
| Floor / ceiling | Greatest key ≤ x / smallest ≥ x — O(h) walk remembering the best candidate |
| Sorted array → balanced BST | Middle element as root, recurse on index ranges (108) |

Interview angle: "Why does `TreeMap` give O(log n) but a plain BST might not?" and "How does a B-tree index differ from a BST?" (fan-out, disk pages — relevant to [LedgerX](../18-projects/ledgerx/README.md)'s indexed history queries this week).

---

## 2. Prerequisite knowledge

- [`10-trees.md`](./10-trees.md) — traversals, top-down parameters. **Required.**
- [`07-binary-search.md`](./07-binary-search.md) — a BST is binary search turned into a data structure.
- [`09-recursion.md`](./09-recursion.md).

---

## 3. Python implementation

```python
from __future__ import annotations


class _Node:
    __slots__ = ("key", "left", "right")

    def __init__(self, key: int) -> None:
        self.key, self.left, self.right = key, None, None


class BST:
    def __init__(self) -> None:
        self.root: _Node | None = None
        self.size = 0

    def contains(self, key: int) -> bool:          # iterative, O(h)
        n = self.root
        while n:
            if key == n.key:
                return True
            n = n.left if key < n.key else n.right
        return False

    def insert(self, key: int) -> None:
        def _ins(n: _Node | None) -> _Node:        # returns the (possibly new) subtree root
            if n is None:
                self.size += 1
                return _Node(key)
            if key < n.key:
                n.left = _ins(n.left)
            elif key > n.key:
                n.right = _ins(n.right)
            return n                               # duplicates ignored
        self.root = _ins(self.root)

    def delete(self, key: int) -> None:
        def _del(n: _Node | None, k: int) -> _Node | None:
            if n is None:
                return None
            if k < n.key:
                n.left = _del(n.left, k)
            elif k > n.key:
                n.right = _del(n.right, k)
            elif n.left is None or n.right is None:
                self.size -= 1
                return n.left or n.right           # 0 or 1 child
            else:
                succ = n.right                     # 2 children: inorder successor
                while succ.left:
                    succ = succ.left
                n.key = succ.key
                n.right = _del(n.right, succ.key)
            return n
        self.root = _del(self.root, key)

    def floor(self, x: int) -> int | None:         # greatest key <= x
        best, n = None, self.root
        while n:
            if n.key == x:
                return x
            if n.key < x:
                best, n = n.key, n.right
            else:
                n = n.left
        return best

    def inorder(self) -> list[int]:
        out, stack, cur = [], [], self.root
        while cur or stack:
            while cur:
                stack.append(cur); cur = cur.left
            cur = stack.pop()
            out.append(cur.key)
            cur = cur.right
        return out

    def height(self) -> int:                       # iterative BFS: safe for skewed trees
        if not self.root:
            return 0
        level, h = [self.root], 0
        while level:
            h += 1
            level = [c for n in level for c in (n.left, n.right) if c]
        return h


t = BST()
for k in (50, 30, 70, 20, 40, 60, 80):
    t.insert(k)
assert t.inorder() == [20, 30, 40, 50, 60, 70, 80] and t.height() == 3
t.delete(50)                                       # two children
t.delete(20)                                       # leaf
assert t.inorder() == [30, 40, 60, 70, 80] and t.size == 5
assert t.contains(60) and t.floor(65) == 60 and t.floor(10) is None
skewed = BST()
for k in range(1, 101):
    skewed.insert(k)                               # recursion depth grows with height!
assert skewed.height() == 100                      # sorted inserts → a linked list
print("BST ok")
```

### Validation, k-th smallest, LCA, build-from-sorted

```python
from __future__ import annotations


class TreeNode:
    def __init__(self, val: int = 0, left: TreeNode | None = None, right: TreeNode | None = None) -> None:
        self.val, self.left, self.right = val, left, right


def is_valid(n: TreeNode | None, lo: float = float("-inf"), hi: float = float("inf")) -> bool:
    if n is None:
        return True
    if not lo < n.val < hi:
        return False
    return is_valid(n.left, lo, n.val) and is_valid(n.right, n.val, hi)


def kth_smallest(root: TreeNode | None, k: int) -> int:
    stack, cur = [], root                          # iterative inorder, stop at the k-th pop
    while True:
        while cur:
            stack.append(cur); cur = cur.left
        cur = stack.pop()
        k -= 1
        if k == 0:
            return cur.val
        cur = cur.right


def lca_bst(root: TreeNode, p: TreeNode, q: TreeNode) -> TreeNode:
    n = root
    while n:
        if p.val < n.val and q.val < n.val:
            n = n.left
        elif p.val > n.val and q.val > n.val:
            n = n.right
        else:
            return n                               # split point (or equals p/q)
    raise ValueError("p and q must be in the tree")


def from_sorted(a: list[int]) -> TreeNode | None:
    def build(lo: int, hi: int) -> TreeNode | None:   # indices, not slices
        if lo > hi:
            return None
        mid = (lo + hi) // 2
        return TreeNode(a[mid], build(lo, mid - 1), build(mid + 1, hi))
    return build(0, len(a) - 1)


root = from_sorted([1, 2, 3, 4, 5, 6, 7])
assert is_valid(root) and kth_smallest(root, 3) == 3
assert lca_bst(root, TreeNode(1), TreeNode(3)).val == 2
bad = TreeNode(5, TreeNode(4), TreeNode(6, TreeNode(3), TreeNode(7)))
assert not is_valid(bad)                          # 3 is in 5's RIGHT subtree
print("BST templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 10): 98 Validate Binary Search Tree** — the `int` sentinel trap.

| Python | Java |
|---|---|
| `float("-inf")`, `float("inf")` bounds | `long` bounds `Long.MIN_VALUE` / `Long.MAX_VALUE` (node values can be `Integer.MIN_VALUE`/`MAX_VALUE`) |
| `lo < n.val < hi` chained comparison | `n.val > lo && n.val < hi` |
| sorted `list` + `bisect` | `TreeMap` / `TreeSet` (`floorKey`, `ceilingKey`, `headMap`, `tailMap`) |

```java
class TreeNode {
    int val; TreeNode left, right;
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
}

class ValidateBSTRep {
    static boolean isValidBST(TreeNode root) { return valid(root, Long.MIN_VALUE, Long.MAX_VALUE); }

    private static boolean valid(TreeNode n, long lo, long hi) {
        if (n == null) return true;
        if (n.val <= lo || n.val >= hi) return false;
        return valid(n.left, lo, n.val) && valid(n.right, n.val, hi);
    }

    public static void main(String[] args) {
        TreeNode extreme = new TreeNode(Integer.MAX_VALUE, new TreeNode(Integer.MIN_VALUE), null);
        TreeNode bad = new TreeNode(5, new TreeNode(4), new TreeNode(6, new TreeNode(3), new TreeNode(7)));
        System.out.println(isValidBST(extreme) + " " + isValidBST(bad));   // true false
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Problems |
|---|---|
| Search by comparison | 700 |
| Build balanced from sorted | 108 |
| Split-point walk (LCA) | 235 |
| Validate with bounds (or inorder strictly increasing) | 98 |
| Inorder with counter / early stop | 230 |
| Insert (return new subtree root) | 701 |
| Delete with successor | 450 |
| Floor/ceiling on sorted keys | 981 ([07](./07-binary-search.md), `bisect`) |

---

## 6. How to recognise the pattern

- The statement says **"binary search tree"** — use the ordering; a generic tree solution is usually suboptimal.
- "k-th smallest/largest", "sorted order", "closest value", "range [low, high]" in a BST → inorder or bounded walk.
- "Validate" → bounds.
- "Design a structure with ordered keys and O(log n) ops" → Java `TreeMap`; in Python, discuss sorted list + `bisect` (O(n) insert) vs a heap vs a balanced BST, and pick based on the operation mix.

---

## 7. Beginner problems (Week 10)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 700 | [Search in a Binary Search Tree](https://leetcode.com/problems/search-in-a-binary-search-tree/) | Easy | Iterative walk: left if smaller, right if larger. |
| 108 | [Convert Sorted Array to Binary Search Tree](https://leetcode.com/problems/convert-sorted-array-to-binary-search-tree/) | Easy | Middle element is the root; recurse on index ranges (no slices). |

---

## 8. Interview problems (Week 10)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 235 | [Lowest Common Ancestor of a Binary Search Tree](https://leetcode.com/problems/lowest-common-ancestor-of-a-binary-search-tree/) | Medium | 10 | <details><summary>show</summary>Walk from the root: both smaller → left; both larger → right; otherwise the current node is the LCA.</details> |
| 98 | [Validate Binary Search Tree](https://leetcode.com/problems/validate-binary-search-tree/) | Medium | 10 | <details><summary>show</summary>Pass `(lo, hi)` open bounds down (`±inf`); checking only direct children is wrong.</details> |
| 230 | [Kth Smallest Element in a BST](https://leetcode.com/problems/kth-smallest-element-in-a-bst/) | Medium | 10 | <details><summary>show</summary>Iterative inorder; stop at the k-th pop → O(h + k).</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 701 | [Insert into a Binary Search Tree](https://leetcode.com/problems/insert-into-a-binary-search-tree/) | Medium | <details><summary>show</summary>Recurse and reassign `node.left = insert(node.left, val)`; the new leaf goes where you hit `None`.</details> |
| 450 | [Delete Node in a BST](https://leetcode.com/problems/delete-node-in-a-bst/) | Medium | <details><summary>show</summary>Three cases; with two children copy the inorder successor's value and delete it from the right subtree.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Trees** (BST part): 235 → 98 → 230.
Here: implement `BST` (§3) from memory → 700 → 108 → 235 → 98 → 230 (Week 10), then tries ([12](./12-tries.md)) the same week → Java rep 98 → (W20–26) 701, 450.

---

## 10. Target number of problems

**5 new** in Week 10 (+ 3 tries = 8) + 2 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Assuming Python has a built-in balanced BST — it doesn't. Don't import `sortedcontainers` in an OA unless the platform lists it.
- `bisect.insort` into a list looks like O(log n) but is O(n) (shifting).
- Recursive insert/delete on a BST built from sorted input → height n → `RecursionError` beyond ~1000 keys. Use iterative versions or balanced construction.
- Chained comparison `lo < n.val < hi` is correct Python; don't "fix" it into `lo < n.val and n.val < hi` incorrectly.
- Using `0` / `-1` sentinels instead of `float("-inf")` / `None` when values can be any integer.

**General**
- 98: checking only `left.val < node.val < right.val` — misses grandchildren violating an ancestor bound.
- Forgetting to define duplicate handling (LeetCode BSTs usually have unique values — ask).
- Insert/delete without reassigning the returned subtree (`insert(n.left, v)` instead of `n.left = insert(n.left, v)`).
- Claiming BST operations are O(log n) without "if balanced".

**Java-rep traps (98):** `int` bounds with `Integer.MIN_VALUE`/`MAX_VALUE` sentinels fail on extreme node values — use `long`.

---

## 12. Mastery criteria

- [ ] Implement `insert`, `contains`, `delete` (all three cases) from memory in ≤ 20 min.
- [ ] Solve 98 with bounds in ≤ 10 min and explain the grandchild counterexample.
- [ ] Explain why sorted inserts degrade a BST, how `TreeMap` avoids it (rotations, awareness level), and what Python uses instead.
- [ ] Solve 2 unseen BST Mediums in ≤ 25 min each.
- [ ] Java rep: 98 in Java with `long` bounds.

---

## 13. Revision schedule

| Review | Week 10 set (700, 108, 235, 98, 230) |
|---|---|
| Day 0 | W10 |
| Day 3 | W10/W11 |
| Day 7 | W11 |
| Day 14 | W12 (Checkpoint 12) |
| Day 30 | W14 |

**Revisit:** Week 11 (heap vs BST: when to use which), Week 21 (FlagForge rule priority ordering — sorted structures in practice), Weeks 20–26 (701, 450).

---

## Worked example — 98. Validate Binary Search Tree

**Clarify.** Valid iff for every node: all left-subtree keys < node < all right-subtree keys (strict → duplicates are invalid). 1 ≤ n ≤ 10⁴; values span the full 32-bit range.

**Brute force.** For each node, scan its whole left subtree for the max and right subtree for the min → O(n²) on skewed trees.

**Wrong-but-tempting.** Compare each node only with its children. Counterexample: `5 → (4, 6 → (3, 7))` — 3 is in 5's right subtree but less than 5.

**Optimise (bounds).** Every node inherits an open interval `(lo, hi)` from its ancestors. Root: (−∞, +∞). Going left narrows `hi` to the parent's value; going right narrows `lo`. Alternative: iterative inorder must be strictly increasing — no recursion-depth risk on a 10⁴-node skewed tree (which *would* exceed Python's default limit).

**Code (Python).**

```python
from __future__ import annotations


class TreeNode:
    def __init__(self, val: int = 0, left: TreeNode | None = None, right: TreeNode | None = None) -> None:
        self.val, self.left, self.right = val, left, right


def is_valid_bst(root: TreeNode | None) -> bool:
    """Bounds version (recursive)."""
    def valid(n: TreeNode | None, lo: float, hi: float) -> bool:
        if n is None:
            return True
        if not lo < n.val < hi:
            return False
        return valid(n.left, lo, n.val) and valid(n.right, n.val, hi)
    return valid(root, float("-inf"), float("inf"))


def is_valid_bst_inorder(root: TreeNode | None) -> bool:
    """Iterative inorder: strictly increasing, safe for deep trees."""
    stack, cur, prev = [], root, None
    while cur or stack:
        while cur:
            stack.append(cur); cur = cur.left
        cur = stack.pop()
        if prev is not None and cur.val <= prev:
            return False
        prev = cur.val
        cur = cur.right
    return True


ok = TreeNode(2, TreeNode(1), TreeNode(3))
bad = TreeNode(5, TreeNode(4), TreeNode(6, TreeNode(3), TreeNode(7)))
dup = TreeNode(2, TreeNode(2))
extreme = TreeNode(2**31 - 1, TreeNode(-2**31))
for f in (is_valid_bst, is_valid_bst_inorder):
    assert f(ok) and not f(bad) and not f(dup) and f(extreme) and f(TreeNode(0))
chain = None
for v in range(1, 5001):                            # 5000-deep left chain (root 5000), valid BST
    chain = TreeNode(v, chain)
assert is_valid_bst_inorder(chain)                  # the recursive version would hit RecursionError
print("98 worked example passed")
```

**Test cases.** `[2,1,3]` → True · grandchild violation → False · duplicate `[2,2]` → False · extremes `[2³¹−1, −2³¹]` → True (breaks `int` sentinels in Java) · single node → True · 5000-deep chain → iterative version handles it.

**Complexity.** Time O(n). Space O(h) — recursion stack or explicit stack.
