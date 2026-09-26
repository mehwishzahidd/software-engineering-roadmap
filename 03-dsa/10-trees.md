# 10 — Trees (Traversals, DFS / BFS)

> **Week 9** · NeetCode section: **Trees** · Target: **8 new problems** + stretch pool · Java rep: **102** (Week 9)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#10--trees) · Python: [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) (`deque`) · Java: [quick reference](./java-dsa-toolkit.md#6-node-classes-and-traversal-templates)

Binary-tree problems are recursion with two children. Almost all of them are one of two shapes: **DFS that returns something from each subtree and combines it**, or **BFS level by level with a queue**. Master the four traversals (pre/in/post/level) both recursively and iteratively.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Terminology | root, leaf, parent/child, depth (from root), height (to deepest leaf), subtree, balanced, complete, full, perfect |
| `TreeNode` | `val`, `left`, `right`; `None` is an empty tree. LeetCode signatures use `Optional[TreeNode]` |
| Preorder (N L R) | Copy/serialize a tree, root-first processing |
| Inorder (L N R) | Sorted order for a BST ([11](./11-bst.md)) |
| Postorder (L R N) | Children's answers needed before the parent: height, diameter, delete |
| Level order (BFS) | `collections.deque`; process `len(q)` nodes per level (freeze the size first) |
| Iterative DFS | Explicit list-as-stack; inorder needs the "go left as far as possible" loop |
| Top-down vs bottom-up | Top-down: pass info **down** as parameters (max so far — 1448). Bottom-up: **return** info up (height — 104, 543, 110) |
| Global answer + local return | Return one thing (height) while updating another (diameter) via `nonlocal` or `self.best` — 543, 124 |
| Complexity | Every node visited once: O(n) time; space O(h) for DFS (h = log n balanced, n skewed), O(w) for BFS (w = max width ≤ n/2) |
| Python depth risk | Skewed trees of 10⁴–10⁵ nodes exceed the default recursion limit → iterative traversal |
| Reconstruction | Preorder gives roots; inorder splits left/right (105) |
| Serialization | Preorder with null markers (297) |

Practical use: the file system, the React component tree, JSON documents, and PostgreSQL's B-tree indexes ([`04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md)) — which you'll tune for [LedgerX](../18-projects/ledgerx/README.md)'s history queries this phase.

---

## 2. Prerequisite knowledge

- [`09-recursion.md`](./09-recursion.md) — contracts, base cases, leap of faith, `nonlocal`, recursion limit. **Required.**
- [`06-stack-queue.md`](./06-stack-queue.md) — stack for iterative DFS, `deque` for BFS.
- [`08-linked-lists.md`](./08-linked-lists.md) — a linked list is a tree with one child.

---

## 3. Python implementation

```python
from __future__ import annotations

from collections import deque


class TreeNode:
    def __init__(self, val: int = 0, left: TreeNode | None = None, right: TreeNode | None = None) -> None:
        self.val, self.left, self.right = val, left, right


def build(vals: list[int | None]) -> TreeNode | None:
    """Build from LeetCode's level-order list with None gaps, e.g. [1, 2, 3, None, 4]."""
    if not vals or vals[0] is None:
        return None
    root = TreeNode(vals[0])
    q, i = deque([root]), 1
    while q and i < len(vals):
        node = q.popleft()
        if i < len(vals) and vals[i] is not None:
            node.left = TreeNode(vals[i]); q.append(node.left)
        i += 1
        if i < len(vals) and vals[i] is not None:
            node.right = TreeNode(vals[i]); q.append(node.right)
        i += 1
    return root


# ---------- Recursive traversals ----------
def preorder(n: TreeNode | None, out: list[int]) -> None:
    if n:
        out.append(n.val); preorder(n.left, out); preorder(n.right, out)


def inorder(n: TreeNode | None, out: list[int]) -> None:
    if n:
        inorder(n.left, out); out.append(n.val); inorder(n.right, out)


def postorder(n: TreeNode | None, out: list[int]) -> None:
    if n:
        postorder(n.left, out); postorder(n.right, out); out.append(n.val)


# ---------- Iterative inorder (memorise) ----------
def inorder_iterative(root: TreeNode | None) -> list[int]:
    out, stack, cur = [], [], root
    while cur or stack:
        while cur:                       # go left as far as possible
            stack.append(cur)
            cur = cur.left
        cur = stack.pop()
        out.append(cur.val)              # visit
        cur = cur.right                  # then the right subtree
    return out


# ---------- Iterative preorder / postorder ----------
def preorder_iterative(root: TreeNode | None) -> list[int]:
    out, stack = [], [root] if root else []
    while stack:
        n = stack.pop()
        out.append(n.val)
        if n.right: stack.append(n.right)    # push right first → left processed first
        if n.left: stack.append(n.left)
    return out


def postorder_iterative(root: TreeNode | None) -> list[int]:
    out, stack = [], [root] if root else []
    while stack:                             # produce N R L, then reverse → L R N
        n = stack.pop()
        out.append(n.val)
        if n.left: stack.append(n.left)
        if n.right: stack.append(n.right)
    return out[::-1]


# ---------- BFS level order ----------
def level_order(root: TreeNode | None) -> list[list[int]]:
    levels: list[list[int]] = []
    q = deque([root]) if root else deque()
    while q:
        level = []
        for _ in range(len(q)):              # range() evaluates len(q) ONCE → frozen level size
            n = q.popleft()
            level.append(n.val)
            if n.left: q.append(n.left)
            if n.right: q.append(n.right)
        levels.append(level)
    return levels


# ---------- Bottom-up and top-down ----------
def height(n: TreeNode | None) -> int:
    return 0 if n is None else 1 + max(height(n.left), height(n.right))


def good_nodes(n: TreeNode | None, max_so_far: float = float("-inf")) -> int:
    if n is None:
        return 0
    good = 1 if n.val >= max_so_far else 0
    m = max(max_so_far, n.val)
    return good + good_nodes(n.left, m) + good_nodes(n.right, m)


root = build([1, 2, 3, 4, 5, None, 6])
pre, ino, post = [], [], []
preorder(root, pre); inorder(root, ino); postorder(root, post)
assert pre == preorder_iterative(root) == [1, 2, 4, 5, 3, 6]
assert ino == inorder_iterative(root) == [4, 2, 5, 1, 3, 6]
assert post == postorder_iterative(root) == [4, 5, 2, 6, 3, 1]
assert level_order(root) == [[1], [2, 3], [4, 5, 6]] and level_order(None) == []
assert height(root) == 3
assert good_nodes(build([3, 1, 4, 3, None, 1, 5])) == 4
print("tree templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 9): 102 Binary Tree Level Order Traversal.** The Java template is in the [quick reference §6](./java-dsa-toolkit.md#6-node-classes-and-traversal-templates); write it from memory.

| Python | Java |
|---|---|
| `if not root: return []` | `if (root == null) return new ArrayList<>();` |
| `deque([root])`, `popleft()` | `Deque<TreeNode> q = new ArrayDeque<>(); q.offer(root); q.poll()` |
| `for _ in range(len(q))` | `for (int size = q.size(); size > 0; size--)` |
| `nonlocal best` | instance field `private int best;` reset at the start of the public method |

```java
import java.util.*;

class TreeNode {
    int val; TreeNode left, right;
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
}

class LevelOrderRep {
    static List<List<Integer>> levelOrder(TreeNode root) {
        List<List<Integer>> levels = new ArrayList<>();
        if (root == null) return levels;
        Deque<TreeNode> q = new ArrayDeque<>();
        q.offer(root);
        while (!q.isEmpty()) {
            List<Integer> level = new ArrayList<>();
            for (int size = q.size(); size > 0; size--) {
                TreeNode n = q.poll();
                level.add(n.val);
                if (n.left != null) q.offer(n.left);      // ArrayDeque rejects null
                if (n.right != null) q.offer(n.right);
            }
            levels.add(level);
        }
        return levels;
    }

    public static void main(String[] args) {
        TreeNode t = new TreeNode(3, new TreeNode(9), new TreeNode(20, new TreeNode(15), new TreeNode(7)));
        System.out.println(levelOrder(t));                // [[3], [9, 20], [15, 7]]
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Signature idea | Problems |
|---|---|---|
| Traversal itself | recursive + iterative | 94 |
| Bottom-up single value | `return 1 + max(L, R)` | 104 |
| Mutate structure | swap children, recurse | 226 |
| Compare two trees | recurse on pairs `(p.left, q.left)` | 100, 572 |
| Return height, track global | height returned, diameter/imbalance tracked | 543, 110, 124 |
| Top-down parameter | pass max/min/path so far | 1448 |
| BFS by level | frozen `len(q)` loop | 102, 199 |
| Reconstruction from traversals | preorder root + inorder index dict | 105 |
| Serialize / deserialize | preorder with `#` for null | 297 |

---

## 6. How to recognise the pattern

- Input is `root: Optional[TreeNode]` → decide: **DFS** (depth, paths, subtree properties) or **BFS** (levels, "nearest", "right side view", "minimum depth").
- Answer at a node depends on answers of its children → **postorder / bottom-up**.
- Answer at a node depends on ancestors → **top-down parameter**.
- "Level", "row", "zigzag", "view from the side" → BFS with a frozen level size.
- "Any path" (not necessarily through the root) → global max + returned "best downward path".
- Up to 10⁴ nodes possibly skewed → Python recursion may exceed 1000 frames → iterative, or raise the limit consciously.

---

## 7. Beginner problems (Week 9)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 104 | [Maximum Depth of Binary Tree](https://leetcode.com/problems/maximum-depth-of-binary-tree/) | Easy | `1 + max(depth(left), depth(right))`; also try BFS counting levels. |
| 226 | [Invert Binary Tree](https://leetcode.com/problems/invert-binary-tree/) | Easy | `root.left, root.right = invert(root.right), invert(root.left)`. |
| 100 | [Same Tree](https://leetcode.com/problems/same-tree/) | Easy | Both `None` → True; one `None` or values differ → False; recurse on pairs. |

---

## 8. Interview problems (Week 9)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 543 | [Diameter of Binary Tree](https://leetcode.com/problems/diameter-of-binary-tree/) | Easy | 9 | <details><summary>show</summary>Return height; at each node update `best = max(best, hL + hR)` (edges). The diameter need not pass through the root.</details> |
| 110 | [Balanced Binary Tree](https://leetcode.com/problems/balanced-binary-tree/) | Easy | 9 | <details><summary>show</summary>Return height or −1 for "unbalanced"; propagate −1 upward → O(n) instead of O(n²).</details> |
| 102 | [Binary Tree Level Order Traversal](https://leetcode.com/problems/binary-tree-level-order-traversal/) | Medium | 9 | <details><summary>show</summary>`deque`; `for _ in range(len(q))` freezes the level size.</details> |
| 199 | [Binary Tree Right Side View](https://leetcode.com/problems/binary-tree-right-side-view/) | Medium | 9 | <details><summary>show</summary>BFS: last node of each level. DFS alternative: visit right first; append when `depth == len(result)`.</details> |
| 1448 | [Count Good Nodes in Binary Tree](https://leetcode.com/problems/count-good-nodes-in-binary-tree/) | Medium | 9 | <details><summary>show</summary>Top-down: pass the max value on the path so far; a node is good if `val >= max_so_far`.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 94 | [Binary Tree Inorder Traversal](https://leetcode.com/problems/binary-tree-inorder-traversal/) | Easy | <details><summary>show</summary>Timed drill of the iterative version in §3 (≤ 5 min).</details> |
| 572 | [Subtree of Another Tree](https://leetcode.com/problems/subtree-of-another-tree/) | Easy | <details><summary>show</summary>For every node of `root`, run `same_tree(node, sub_root)` → O(m·n). (Serialization + substring search gives O(m+n).)</details> |
| 105 | [Construct Binary Tree from Preorder and Inorder Traversal](https://leetcode.com/problems/construct-binary-tree-from-preorder-and-inorder-traversal/) | Medium | <details><summary>show</summary>Next preorder element is the root; its inorder index (precomputed dict) splits left/right sizes. Pass index bounds, never slices.</details> |
| 124 | [Binary Tree Maximum Path Sum](https://leetcode.com/problems/binary-tree-maximum-path-sum/) | Hard | <details><summary>show</summary>Return the best downward path `val + max(0, L, R)`; update global with `val + max(0, L) + max(0, R)`. Initialise global to `-inf` (all-negative trees).</details> |
| 297 | [Serialize and Deserialize Binary Tree](https://leetcode.com/problems/serialize-and-deserialize-binary-tree/) | Hard | <details><summary>show</summary>Preorder with `#` for null, comma-joined; deserialize by consuming tokens from an iterator (`next(it)`) recursively.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Trees**: 226 → 104 → 543 → 110 → 100 → 572 → 235 → 102 → 199 → 1448 → 98 → 230 → 105 → 124 → 297. (235, 98, 230 are BST problems — see [11](./11-bst.md).)
Here: §3 from memory → 104 → 226 → 100 → 543 → 110 → 102 → 199 → 1448 → Java rep 102 → (W20–26) 94, 572, 105, 124, 297.

---

## 10. Target number of problems

**8 new** in Week 9 + 5 stretch (the tree stretch pool is larger because trees are the most common mock-interview topic).

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Deep recursion on skewed trees → `RecursionError`. Use the iterative traversals from §3.
- `for _ in range(len(q))` is correct (evaluated once) — but a `while q:` inner loop mixes levels.
- `q.pop()` instead of `q.popleft()` → turns BFS into DFS silently.
- Mutable default argument for accumulators: `def dfs(node, path=[])` shares `path` across calls.
- Global/class-level `best` that isn't reset between LeetCode test cases → wrong on the second test. Use `nonlocal` inside the method.
- `max_so_far = 0` sentinel when values can be negative → use `float("-inf")`.
- 105: slicing `preorder[1:k+1]` in each call → O(n²) time and memory. Use indices + a value→index dict.

**General**
- Not handling `root is None` first.
- Depth in **nodes** vs **edges** (543 wants edges; 104 wants nodes).
- 110: calling `height` inside `is_balanced` recursively → O(n²).

**Java-rep traps (102):** `ArrayDeque.offer(null)` throws; freeze `q.size()` before the inner loop.

---

## 12. Mastery criteria

- [ ] Write all four traversals (recursive) and iterative inorder + level order from memory in ≤ 12 min total.
- [ ] Explain top-down vs bottom-up with 1448 and 104 as examples.
- [ ] Solve 543 and 110 in ≤ 12 min each using "return height, track global".
- [ ] Solve 3 unseen tree Mediums in ≤ 25 min each and state O(n) time / O(h) space correctly.
- [ ] Java rep: 102 in Java in ≤ 15 min.

---

## 13. Revision schedule

| Review | Week 9 set |
|---|---|
| Day 0 | W9 |
| Day 3 | W9/W10 |
| Day 7 | W10 (mock #1 — trees are likely) |
| Day 14 | W11 |
| Day 30 | W13 |

**Revisit:** Week 10 (BST builds on inorder), Week 13 (graphs = trees with cycles → `visited`), Weeks 14+ (weekly mocks), Weeks 20–26 (94, 572, 105, 124, 297).

---

## Worked example — 543. Diameter of Binary Tree

**Clarify.** Diameter = number of **edges** on the longest path between any two nodes; the path may or may not pass through the root. 1 ≤ nodes ≤ 10⁴. Single node → 0.

**Brute force.** For every node, compute `height(left) + height(right)` with separate height calls → O(n) per node → O(n²) on a skewed tree.

**Optimise.** One postorder pass. Contract: `height(node)` returns the number of **nodes** on the longest downward path from `node` (0 for `None`). The longest path *bending at* node has `height(left) + height(right)` edges. Track the max of that with `nonlocal` while returning height.

**Code (Python).**

```python
from __future__ import annotations


class TreeNode:
    def __init__(self, val: int = 0, left: TreeNode | None = None, right: TreeNode | None = None) -> None:
        self.val, self.left, self.right = val, left, right


def diameter_of_binary_tree(root: TreeNode | None) -> int:
    best = 0

    def height(n: TreeNode | None) -> int:
        nonlocal best
        if n is None:
            return 0
        l, r = height(n.left), height(n.right)
        best = max(best, l + r)              # edges on the best path bending at n
        return 1 + max(l, r)

    height(root)
    return best


t1 = TreeNode(1, TreeNode(2, TreeNode(4), TreeNode(5)), TreeNode(3))
assert diameter_of_binary_tree(t1) == 3                        # 4-2-1-3
assert diameter_of_binary_tree(TreeNode(1, TreeNode(2))) == 1
assert diameter_of_binary_tree(TreeNode(1)) == 0
wide = TreeNode(2, TreeNode(3, TreeNode(4, TreeNode(5))), TreeNode(6, None, TreeNode(7, None, TreeNode(8))))
assert diameter_of_binary_tree(TreeNode(1, wide)) == 6         # not through the root
print("543 worked example passed")
```

**Test cases.** `[1,2,3,4,5]` → 3 · two nodes → 1 · one node → 0 · path not through the root (above) → 6 · skewed chain of n nodes → n − 1 (and, in Python, recursion depth n — mention the limit for n > ~1000).

**Complexity.** Time O(n) — each node visited once. Space O(h): O(log n) balanced, O(n) skewed (call stack).
