# 10 — Trees (Traversals, DFS / BFS)

> **Weeks 9–10** · NeetCode section: **Trees** · Target: **11 new problems** (W9: 3, W10: 8) + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#10--trees) · Java APIs: [toolkit §6 `ArrayDeque`](./java-dsa-toolkit.md#6-arraydeque-as-stack-and-queue), [§12 recursion depth](./java-dsa-toolkit.md#12-recursion-depth)

Binary-tree problems are recursion with two children. Almost all of them are one of two shapes: **DFS that returns something from each subtree and combines it**, or **BFS level by level with a queue**. Master the four traversals (pre/in/post/level) both recursively and iteratively.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Terminology | root, leaf, parent/child, depth (from root), height (to deepest leaf), subtree, balanced, complete, full, perfect |
| `TreeNode` | `int val; TreeNode left, right;` — `null` is an empty tree |
| Preorder (N L R) | Copy/serialize a tree, root-first processing |
| Inorder (L N R) | Sorted order for a BST ([11](./11-bst.md)) |
| Postorder (L R N) | Children's answers needed before the parent: height, diameter, delete |
| Level order (BFS) | `ArrayDeque` queue; process `size = q.size()` nodes per level |
| Iterative DFS | Explicit stack; inorder needs "go left as far as possible" loop |
| Top-down vs bottom-up | Top-down: pass info **down** as parameters (max so far — 1448). Bottom-up: **return** info up (height — 104, 543, 110) |
| Global answer + local return | Return one thing (height) while updating another (diameter) — 543, 124 |
| Complexity | Every node visited once: O(n) time; space O(h) for DFS (h = log n balanced, n skewed), O(w) for BFS (w = max width ≤ n/2) |
| Reconstruction | Preorder gives roots; inorder splits left/right (105) |
| Serialization | Preorder with null markers (297) |

Practical use: the file system, the DOM in React ([`08-react/`](../08-react/README.md)), JSON documents and B-tree indexes in PostgreSQL ([`04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md)) are all trees.

---

## 2. Prerequisite knowledge

- [`09-recursion.md`](./09-recursion.md) — contracts, base cases, leap of faith. **Required.**
- [`06-stack-queue.md`](./06-stack-queue.md) — stack for iterative DFS, queue for BFS.
- [`08-linked-lists.md`](./08-linked-lists.md) — a linked list is a tree with one child.

---

## 3. Java implementation

```java
import java.util.*;

class TreeNode {
    int val; TreeNode left, right;
    TreeNode() {}
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
}

class TreeTemplates {
    // ---------- Recursive traversals ----------
    static void preorder(TreeNode n, List<Integer> out) {
        if (n == null) return;
        out.add(n.val); preorder(n.left, out); preorder(n.right, out);
    }
    static void inorder(TreeNode n, List<Integer> out) {
        if (n == null) return;
        inorder(n.left, out); out.add(n.val); inorder(n.right, out);
    }
    static void postorder(TreeNode n, List<Integer> out) {
        if (n == null) return;
        postorder(n.left, out); postorder(n.right, out); out.add(n.val);
    }

    // ---------- Iterative inorder (memorise) ----------
    static List<Integer> inorderIterative(TreeNode root) {
        List<Integer> out = new ArrayList<>();
        Deque<TreeNode> st = new ArrayDeque<>();
        TreeNode cur = root;
        while (cur != null || !st.isEmpty()) {
            while (cur != null) { st.push(cur); cur = cur.left; }   // go left as far as possible
            cur = st.pop();
            out.add(cur.val);                                       // visit
            cur = cur.right;                                        // then the right subtree
        }
        return out;
    }

    // ---------- Iterative preorder ----------
    static List<Integer> preorderIterative(TreeNode root) {
        List<Integer> out = new ArrayList<>();
        if (root == null) return out;
        Deque<TreeNode> st = new ArrayDeque<>();
        st.push(root);
        while (!st.isEmpty()) {
            TreeNode n = st.pop();
            out.add(n.val);
            if (n.right != null) st.push(n.right);                  // push right first → left is processed first
            if (n.left != null) st.push(n.left);
        }
        return out;
    }

    // ---------- Iterative postorder (reverse of N R L) ----------
    static List<Integer> postorderIterative(TreeNode root) {
        LinkedList<Integer> out = new LinkedList<>();
        if (root == null) return out;
        Deque<TreeNode> st = new ArrayDeque<>();
        st.push(root);
        while (!st.isEmpty()) {
            TreeNode n = st.pop();
            out.addFirst(n.val);
            if (n.left != null) st.push(n.left);
            if (n.right != null) st.push(n.right);
        }
        return out;
    }

    // ---------- BFS level order ----------
    static List<List<Integer>> levelOrder(TreeNode root) {
        List<List<Integer>> levels = new ArrayList<>();
        if (root == null) return levels;
        Deque<TreeNode> q = new ArrayDeque<>();
        q.offer(root);
        while (!q.isEmpty()) {
            int size = q.size();                                    // freeze the level size
            List<Integer> level = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                TreeNode n = q.poll();
                level.add(n.val);
                if (n.left != null) q.offer(n.left);
                if (n.right != null) q.offer(n.right);
            }
            levels.add(level);
        }
        return levels;
    }

    // ---------- Bottom-up: height ----------
    static int height(TreeNode n) {
        return n == null ? 0 : 1 + Math.max(height(n.left), height(n.right));
    }

    // ---------- Top-down: count nodes >= every ancestor (LC 1448 shape) ----------
    static int goodNodes(TreeNode n, int maxSoFar) {
        if (n == null) return 0;
        int good = n.val >= maxSoFar ? 1 : 0;
        int m = Math.max(maxSoFar, n.val);
        return good + goodNodes(n.left, m) + goodNodes(n.right, m);
    }

    // ---------- Helper: build from LeetCode level-order array with nulls ----------
    static TreeNode build(Integer... vals) {
        if (vals.length == 0 || vals[0] == null) return null;
        TreeNode root = new TreeNode(vals[0]);
        Deque<TreeNode> q = new ArrayDeque<>();
        q.offer(root);
        int i = 1;
        while (!q.isEmpty() && i < vals.length) {
            TreeNode n = q.poll();
            if (i < vals.length && vals[i] != null) { n.left = new TreeNode(vals[i]); q.offer(n.left); }
            i++;
            if (i < vals.length && vals[i] != null) { n.right = new TreeNode(vals[i]); q.offer(n.right); }
            i++;
        }
        return root;
    }

    public static void main(String[] args) {
        TreeNode root = build(1, 2, 3, 4, 5, null, 6);
        List<Integer> pre = new ArrayList<>(), in = new ArrayList<>(), post = new ArrayList<>();
        preorder(root, pre); inorder(root, in); postorder(root, post);
        System.out.println(pre + " " + preorderIterative(root));    // [1, 2, 4, 5, 3, 6] twice
        System.out.println(in + " " + inorderIterative(root));      // [4, 2, 5, 1, 3, 6] twice
        System.out.println(post + " " + postorderIterative(root));  // [4, 5, 2, 6, 3, 1] twice
        System.out.println(levelOrder(root));                       // [[1], [2, 3], [4, 5, 6]]
        System.out.println(height(root));                           // 3
        System.out.println(goodNodes(build(3, 1, 4, 3, null, 1, 5), Integer.MIN_VALUE)); // 4
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Signature idea | Problems |
|---|---|---|
| Traversal itself | recursive + iterative | 94 |
| Bottom-up single value | `return 1 + max(L, R)` | 104 |
| Mutate structure | swap children, recurse | 226 |
| Compare two trees | recurse on pairs `(p.left, q.left)` | 100, 572 |
| Return height, track global | height returned, diameter/imbalance tracked | 543, 110, 124 |
| Top-down parameter | pass max/min/path so far | 1448 |
| BFS by level | `size` loop | 102, 199 |
| Reconstruction from traversals | preorder root + inorder index map | 105 |
| Serialize / deserialize | preorder with `#` for null | 297 |

---

## 5. How to recognise the pattern

- Input is `TreeNode root` → decide: **DFS** (depth, paths, subtree properties) or **BFS** (levels, "nearest", "right side view", "minimum depth").
- Answer at a node depends on answers of its children → **postorder / bottom-up**.
- Answer at a node depends on ancestors → **top-down parameter**.
- "Level", "row", "zigzag", "view from the side" → BFS with a `size` loop.
- "Any path" (not necessarily through root) → global max + returned "best downward path".
- n ≤ 10⁴ nodes → recursion depth fine on LeetCode even for skewed trees; for 10⁵+ consider iteration.

---

## 6. Beginner problems (Weeks 9–10)

| # | Problem | Difficulty | Week | Hint |
|---:|---|---|---:|---|
| 94 | [Binary Tree Inorder Traversal](https://leetcode.com/problems/binary-tree-inorder-traversal/) | Easy | 9 | Do recursive, then the iterative stack version (§3). Submit both. |
| 104 | [Maximum Depth of Binary Tree](https://leetcode.com/problems/maximum-depth-of-binary-tree/) | Easy | 9 | `1 + max(depth(left), depth(right))`; also try BFS counting levels. |
| 226 | [Invert Binary Tree](https://leetcode.com/problems/invert-binary-tree/) | Easy | 9 | Swap children, recurse on both (pre- or postorder both work). |
| 100 | [Same Tree](https://leetcode.com/problems/same-tree/) | Easy | 10 | Both null → true; one null or values differ → false; recurse on pairs. |

---

## 7. Interview problems (Week 10)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 572 | [Subtree of Another Tree](https://leetcode.com/problems/subtree-of-another-tree/) | Easy | 10 | <details><summary>show</summary>For every node of `root`, run `sameTree(node, subRoot)` → O(m·n). (Serialization + string search gives O(m+n).)</details> |
| 543 | [Diameter of Binary Tree](https://leetcode.com/problems/diameter-of-binary-tree/) | Easy | 10 | <details><summary>show</summary>Return height; at each node update `best = max(best, hL + hR)` (edges). The diameter need not pass through the root.</details> |
| 110 | [Balanced Binary Tree](https://leetcode.com/problems/balanced-binary-tree/) | Easy | 10 | <details><summary>show</summary>Return height or −1 for "unbalanced"; propagate −1 upward → O(n) instead of O(n²).</details> |
| 102 | [Binary Tree Level Order Traversal](https://leetcode.com/problems/binary-tree-level-order-traversal/) | Medium | 10 | <details><summary>show</summary>Queue; snapshot `size = q.size()` at the start of each level.</details> |
| 199 | [Binary Tree Right Side View](https://leetcode.com/problems/binary-tree-right-side-view/) | Medium | 10 | <details><summary>show</summary>BFS: last node of each level. DFS alternative: visit right first; add when `depth == result.size()`.</details> |
| 1448 | [Count Good Nodes in Binary Tree](https://leetcode.com/problems/count-good-nodes-in-binary-tree/) | Medium | 10 | <details><summary>show</summary>Top-down: pass the max value on the path so far; node is good if `val >= max`.</details> |
| 105 | [Construct Binary Tree from Preorder and Inorder Traversal](https://leetcode.com/problems/construct-binary-tree-from-preorder-and-inorder-traversal/) | Medium | 10 | <details><summary>show</summary>Next preorder element is the root; its inorder index splits left/right sizes. Precompute a value→index map for O(n).</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 124 | [Binary Tree Maximum Path Sum](https://leetcode.com/problems/binary-tree-maximum-path-sum/) | Hard | <details><summary>show</summary>Return best downward path `val + max(0, L, R)`; update global with `val + max(0,L) + max(0,R)`. Initialise global to `Integer.MIN_VALUE` (all-negative trees).</details> |
| 297 | [Serialize and Deserialize Binary Tree](https://leetcode.com/problems/serialize-and-deserialize-binary-tree/) | Hard | <details><summary>show</summary>Preorder with `#` for null, comma-separated; deserialize by consuming tokens from a queue/iterator recursively.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Trees**: 226 → 104 → 543 → 110 → 100 → 572 → 235 → 102 → 199 → 1448 → 98 → 230 → 105 → 124 → 297. (235, 98, 230 are BST problems — see [11](./11-bst.md).)
Here: §3 from memory → 94 → 104 → 226 (W9) → 100 → 572 → 543 → 110 → 102 → 199 → 1448 → 105 (W10) → (W21–26) 124, 297.

---

## 9. Target number of problems

**11 new**: 3 in Week 9 (with 4 recursion = 7) and 8 in Week 10 + 2 stretch.

---

## 10. Mistakes beginners commonly make

- Not handling `root == null` first.
- Confusing depth in **nodes** vs **edges** (543 wants edges; 104 wants nodes).
- 110: calling `height` inside `isBalanced` recursively → O(n²). Return a sentinel instead.
- 102: using `q.size()` in the loop condition while adding children → levels merge. Freeze `size` first.
- Java: `Queue<TreeNode> q = new ArrayDeque<>()` then `q.offer(null)` → `NullPointerException` (ArrayDeque rejects nulls). Check children before offering.
- Using a `static` field for the global answer (543/124) without resetting it between test cases.
- 105: `Arrays.copyOfRange` for each subtree → O(n²) time and memory; pass index bounds instead.
- 124: initialising the global max to 0 → wrong for all-negative trees.
- Deep recursion on degenerate trees in OA environments → iterative version.

---

## 11. Mastery criteria

- [ ] Write all four traversals (recursive) and iterative inorder + level order from memory in ≤ 12 min total.
- [ ] Explain top-down vs bottom-up with 1448 and 104 as examples.
- [ ] Solve 543 and 110 in ≤ 12 min each using "return height, track global".
- [ ] Solve 105 in ≤ 25 min in O(n).
- [ ] Solve 3 unseen tree Mediums in ≤ 25 min each and state O(n) time / O(h) space correctly.

---

## 12. Revision schedule

| Review | Week 9 set (94, 104, 226) | Week 10 set (100, 572, 543, 110, 102, 199, 1448, 105) |
|---|---|---|
| Day 0 | W9 | W10 |
| Day 3 | W9/W10 | W10/W11 |
| Day 7 | W10 | W11 |
| Day 14 | W11 | W12 (Checkpoint 12) |
| Day 30 | W13 | W14 |

**Revisit:** Week 11 (BST builds on inorder), Week 14 (graphs = trees with cycles → `visited`), Week 17 (weekly mocks — trees are the most common mock topic), Weeks 21–26 (124, 297).

---

## Worked example — 543. Diameter of Binary Tree

**Clarify.** Diameter = number of **edges** on the longest path between any two nodes; the path may or may not pass through the root. 1 ≤ nodes ≤ 10⁴. Single node → 0.

**Brute force.** For every node, compute `height(left) + height(right)` with a separate height call → O(n) per node → O(n²) on a skewed tree (10⁸, borderline and wasteful).

**Optimise.** One postorder pass. Contract: `height(node)` returns the number of **nodes** on the longest downward path from `node` (0 for null). The longest path *through* node has `height(left) + height(right)` edges. Track the max of that in a field while returning height.

**Code.**

```java
class TreeNode {
    int val; TreeNode left, right;
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
}

class DiameterOfBinaryTree {
    private int best;                                   // instance field, reset per call

    public int diameterOfBinaryTree(TreeNode root) {
        best = 0;
        height(root);
        return best;
    }

    // Contract: number of nodes on the longest downward path starting at n
    private int height(TreeNode n) {
        if (n == null) return 0;
        int l = height(n.left), r = height(n.right);
        best = Math.max(best, l + r);                   // edges on the best path bending at n
        return 1 + Math.max(l, r);
    }

    public static void main(String[] args) {
        DiameterOfBinaryTree s = new DiameterOfBinaryTree();
        TreeNode t1 = new TreeNode(1, new TreeNode(2, new TreeNode(4), new TreeNode(5)), new TreeNode(3));
        System.out.println(s.diameterOfBinaryTree(t1));                        // 3  (4-2-1-3 or 5-2-1-3)
        System.out.println(s.diameterOfBinaryTree(new TreeNode(1, new TreeNode(2), null))); // 1
        System.out.println(s.diameterOfBinaryTree(new TreeNode(1)));           // 0
        // Diameter not through root: root has one child whose subtree is wide
        TreeNode wide = new TreeNode(2, new TreeNode(3, new TreeNode(4, new TreeNode(5), null), null),
                                        new TreeNode(6, null, new TreeNode(7, null, new TreeNode(8))));
        System.out.println(s.diameterOfBinaryTree(new TreeNode(1, wide, null))); // 6
    }
}
```

**Test cases.** `[1,2,3,4,5]` → 3 · two nodes → 1 · one node → 0 · path not through root (above) → 6 · skewed chain of n nodes → n − 1.

**Complexity.** Time O(n) — each node visited once. Space O(h): O(log n) balanced, O(n) skewed (call stack).
