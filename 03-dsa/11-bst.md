# 11 — Binary Search Trees

> **Week 11** · NeetCode section: **Trees** (BST problems) · Target: **5 new problems** + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#11--binary-search-trees) · Java APIs: [toolkit §8 TreeMap/TreeSet](./java-dsa-toolkit.md#8-treemap--treeset)

A BST is a binary tree with an ordering invariant: **everything in the left subtree < node < everything in the right subtree**. That invariant gives O(h) search/insert/delete and sorted order via inorder traversal. Java's `TreeMap`/`TreeSet` are self-balancing BSTs (red-black trees) — this week you learn what's inside them.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| BST invariant | For **every** node: all keys in left subtree < key < all keys in right subtree (not just direct children!) |
| Search / insert | Walk left or right by comparison: O(h) |
| Delete | 0 children → remove; 1 child → replace by child; 2 children → replace value with inorder successor (min of right subtree), then delete successor |
| Inorder = sorted | Inorder traversal visits keys in ascending order — k-th smallest, validation, BST iterator |
| Height | Balanced: O(log n). Degenerate (inserting sorted data): O(n) — a linked list |
| Self-balancing trees (awareness) | AVL, red-black (Java `TreeMap`), B-trees (database indexes — [`04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md)) keep h = O(log n) |
| Bounds (min, max) | Validate by passing an allowed open interval down the recursion |
| LCA in a BST | First node where p and q split (one ≤ node ≤ other) |
| Floor / ceiling | Greatest key ≤ x / smallest ≥ x — O(h) walk remembering the best candidate |
| Sorted array → balanced BST | Middle element as root, recurse on halves (108) |

Interview angle: "Why does `TreeMap` give O(log n) but a plain BST might not?" and "How does a B-tree index differ from a BST?" (fan-out, disk pages).

---

## 2. Prerequisite knowledge

- [`10-trees.md`](./10-trees.md) — traversals, top-down parameters. **Required.**
- [`07-binary-search.md`](./07-binary-search.md) — a BST is binary search made into a data structure.
- [`09-recursion.md`](./09-recursion.md).

---

## 3. Java implementation

```java
import java.util.*;

class BST {
    private static final class Node {
        int key; Node left, right;
        Node(int key) { this.key = key; }
    }

    private Node root;
    private int size;

    boolean contains(int key) {                              // iterative search, O(h)
        Node n = root;
        while (n != null) {
            if (key == n.key) return true;
            n = key < n.key ? n.left : n.right;
        }
        return false;
    }

    void insert(int key) { root = insert(root, key); }
    private Node insert(Node n, int key) {                   // returns the (possibly new) subtree root
        if (n == null) { size++; return new Node(key); }
        if (key < n.key) n.left = insert(n.left, key);
        else if (key > n.key) n.right = insert(n.right, key);
        return n;                                            // duplicates ignored
    }

    void delete(int key) { root = delete(root, key); }
    private Node delete(Node n, int key) {
        if (n == null) return null;
        if (key < n.key) { n.left = delete(n.left, key); return n; }
        if (key > n.key) { n.right = delete(n.right, key); return n; }
        // found
        if (n.left == null) { size--; return n.right; }
        if (n.right == null) { size--; return n.left; }
        Node succ = n.right;                                 // two children: inorder successor
        while (succ.left != null) succ = succ.left;
        n.key = succ.key;
        n.right = delete(n.right, succ.key);                 // size-- happens in that call
        return n;
    }

    Integer floor(int x) {                                   // greatest key <= x, or null
        Integer best = null;
        for (Node n = root; n != null; ) {
            if (n.key == x) return x;
            if (n.key < x) { best = n.key; n = n.right; } else n = n.left;
        }
        return best;
    }

    List<Integer> inorder() {
        List<Integer> out = new ArrayList<>();
        Deque<Node> st = new ArrayDeque<>();
        Node cur = root;
        while (cur != null || !st.isEmpty()) {
            while (cur != null) { st.push(cur); cur = cur.left; }
            cur = st.pop();
            out.add(cur.key);
            cur = cur.right;
        }
        return out;
    }

    int height() { return height(root); }
    private int height(Node n) { return n == null ? 0 : 1 + Math.max(height(n.left), height(n.right)); }

    public static void main(String[] args) {
        BST t = new BST();
        for (int k : new int[]{50, 30, 70, 20, 40, 60, 80}) t.insert(k);
        System.out.println(t.inorder() + " h=" + t.height());      // [20, 30, 40, 50, 60, 70, 80] h=3
        t.delete(50);                                               // two children
        t.delete(20);                                               // leaf
        System.out.println(t.inorder() + " size=" + t.size);       // [30, 40, 60, 70, 80] size=5
        System.out.println(t.contains(60) + " " + t.floor(65) + " " + t.floor(10)); // true 60 null
        BST skewed = new BST();
        for (int k = 1; k <= 100; k++) skewed.insert(k);
        System.out.println("sorted inserts → height " + skewed.height());         // 100
    }
}
```

### Validation and k-th smallest templates

```java
class TreeNode {
    int val; TreeNode left, right;
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
}

class BSTTemplates {
    // Bounds passed down; long bounds so Integer.MIN_VALUE / MAX_VALUE node values are handled
    static boolean isValid(TreeNode n, long lo, long hi) {
        if (n == null) return true;
        if (n.val <= lo || n.val >= hi) return false;
        return isValid(n.left, lo, n.val) && isValid(n.right, n.val, hi);
    }

    // k-th smallest via inorder with early stop (LC 230)
    private int count, answer;
    int kthSmallest(TreeNode root, int k) {
        count = k;
        inorder(root);
        return answer;
    }
    private void inorder(TreeNode n) {
        if (n == null || count == 0) return;
        inorder(n.left);
        if (--count == 0) { answer = n.val; return; }
        inorder(n.right);
    }

    // LCA in a BST (LC 235): iterative, O(h), O(1) space
    static TreeNode lca(TreeNode root, TreeNode p, TreeNode q) {
        TreeNode n = root;
        while (n != null) {
            if (p.val < n.val && q.val < n.val) n = n.left;
            else if (p.val > n.val && q.val > n.val) n = n.right;
            else return n;                                  // split point (or equals p/q)
        }
        return null;
    }

    // Sorted array → height-balanced BST (LC 108)
    static TreeNode fromSorted(int[] a, int lo, int hi) {
        if (lo > hi) return null;
        int mid = lo + (hi - lo) / 2;
        return new TreeNode(a[mid], fromSorted(a, lo, mid - 1), fromSorted(a, mid + 1, hi));
    }

    public static void main(String[] args) {
        TreeNode root = fromSorted(new int[]{1, 2, 3, 4, 5, 6, 7}, 0, 6);
        System.out.println(isValid(root, Long.MIN_VALUE, Long.MAX_VALUE));          // true
        System.out.println(new BSTTemplates().kthSmallest(root, 3));               // 3
        System.out.println(lca(root, new TreeNode(1), new TreeNode(3)).val);       // 2
        TreeNode bad = new TreeNode(5, new TreeNode(4), new TreeNode(6, new TreeNode(3), new TreeNode(7)));
        System.out.println(isValid(bad, Long.MIN_VALUE, Long.MAX_VALUE));           // false
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Problems |
|---|---|
| Search by comparison | 700 |
| Build balanced from sorted | 108 |
| Split-point walk (LCA) | 235 |
| Validate with bounds (or inorder strictly increasing) | 98 |
| Inorder with counter / early stop | 230 |
| Insert (return new subtree root) | 701 |
| Delete with successor | 450 |
| Floor/ceiling in a sorted structure | `TreeMap` in 981 ([07](./07-binary-search.md)) |

---

## 5. How to recognise the pattern

- The statement says **"binary search tree"** — use the ordering; a generic tree solution is usually suboptimal.
- "k-th smallest/largest", "sorted order", "closest value", "range [low, high]" in a BST → inorder or bounded walk.
- "Validate" → bounds.
- "Design a structure with ordered keys and O(log n) ops" → `TreeMap`/`TreeSet` in Java, and be ready to explain balancing.

---

## 6. Beginner problems (Week 11)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 700 | [Search in a Binary Search Tree](https://leetcode.com/problems/search-in-a-binary-search-tree/) | Easy | Iterative walk: left if smaller, right if larger. |
| 108 | [Convert Sorted Array to Binary Search Tree](https://leetcode.com/problems/convert-sorted-array-to-binary-search-tree/) | Easy | Middle element is root; recurse on index ranges (no array copies). |

---

## 7. Interview problems (Week 11)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 235 | [Lowest Common Ancestor of a Binary Search Tree](https://leetcode.com/problems/lowest-common-ancestor-of-a-binary-search-tree/) | Medium | 11 | <details><summary>show</summary>Walk from root: both smaller → go left; both larger → go right; otherwise current node is the LCA.</details> |
| 98 | [Validate Binary Search Tree](https://leetcode.com/problems/validate-binary-search-tree/) | Medium | 11 | <details><summary>show</summary>Pass `(lo, hi)` bounds down as `long` (node values can be `Integer.MIN/MAX_VALUE`); checking only direct children is wrong.</details> |
| 230 | [Kth Smallest Element in a BST](https://leetcode.com/problems/kth-smallest-element-in-a-bst/) | Medium | 11 | <details><summary>show</summary>Iterative inorder; stop at the k-th pop → O(h + k).</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 701 | [Insert into a Binary Search Tree](https://leetcode.com/problems/insert-into-a-binary-search-tree/) | Medium | <details><summary>show</summary>Recurse and reassign `node.left = insert(node.left, val)`; new leaf at the null position.</details> |
| 450 | [Delete Node in a BST](https://leetcode.com/problems/delete-node-in-a-bst/) | Medium | <details><summary>show</summary>Three cases; with two children copy the inorder successor's value and delete it from the right subtree.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Trees** (BST part): 235 → 98 → 230.
Here: implement `BST` (§3) from memory → 700 → 108 → 235 → 98 → 230 (W11), then tries ([12](./12-tries.md)) the same week → (W21–26) 701, 450.

---

## 9. Target number of problems

**5 new** in Week 11 (+ 3 tries = 8) + 2 stretch.

---

## 10. Mistakes beginners commonly make

- 98: checking only `left.val < node.val < right.val` — misses grandchildren violating the ancestor bound.
- 98: using `int` bounds with `Integer.MIN_VALUE`/`MAX_VALUE` sentinels → fails when a node holds those values. Use `long` or `Integer` `null` bounds.
- Forgetting that duplicates' handling must be defined (LeetCode BSTs usually have unique values — ask).
- Insert/delete without reassigning the returned subtree (`insert(n.left, v);` instead of `n.left = insert(n.left, v);`).
- Treating BST operations as O(log n) without saying "if balanced".
- 230: collecting the whole inorder list (O(n) space) when an early stop suffices — fine to start with, but mention the improvement.
- Using recursion on a BST built from sorted input (height n) in an OA — stack overflow risk.

---

## 11. Mastery criteria

- [ ] Implement `insert`, `contains`, `delete` (all three cases) from memory in ≤ 20 min.
- [ ] Solve 98 with bounds in ≤ 10 min and explain the grandchild counterexample.
- [ ] Explain why sorted inserts degrade a BST and how `TreeMap` avoids it (red-black rotations, awareness level).
- [ ] Solve 2 unseen BST Mediums in ≤ 25 min each.

---

## 12. Revision schedule

| Review | Week 11 set (700, 108, 235, 98, 230) |
|---|---|
| Day 0 | W11 |
| Day 3 | W11/W12 |
| Day 7 | W12 (Checkpoint 12) |
| Day 14 | W13 |
| Day 30 | W15 |

**Revisit:** Week 12 (heap vs BST: when to use which), Week 13 (`TreeMap` inside concurrency/scheduling discussions), Week 22 (B-tree indexes on P4 — explain the difference), Weeks 21–26 (701, 450).

---

## Worked example — 98. Validate Binary Search Tree

**Clarify.** Valid iff for every node: all left-subtree keys < node < all right-subtree keys (strict, so duplicates are invalid). 1 ≤ n ≤ 10⁴; values span the full `int` range, including `Integer.MIN_VALUE` and `Integer.MAX_VALUE`.

**Brute force.** For each node, scan its whole left subtree for max and right subtree for min → O(n²) on skewed trees.

**Wrong-but-tempting.** Compare each node only with its children. Counterexample: `5 → (4, 6 → (3, 7))` — 3 is in 5's right subtree but less than 5.

**Optimise (bounds).** Every node inherits an open interval `(lo, hi)` from its ancestors. Root: (−∞, +∞). Going left narrows `hi` to the parent's value; going right narrows `lo`. Use `long` so the infinities are outside the `int` range. Alternative: iterative inorder and check strictly increasing.

**Code.**

```java
import java.util.*;

class TreeNode {
    int val; TreeNode left, right;
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
}

class ValidateBST {
    public boolean isValidBST(TreeNode root) {
        return valid(root, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    // Contract: true iff every key in n's subtree lies strictly inside (lo, hi) and the subtree is a BST
    private boolean valid(TreeNode n, long lo, long hi) {
        if (n == null) return true;
        if (n.val <= lo || n.val >= hi) return false;
        return valid(n.left, lo, n.val) && valid(n.right, n.val, hi);
    }

    // Alternative: iterative inorder must be strictly increasing (no recursion depth risk)
    public boolean isValidInorder(TreeNode root) {
        Deque<TreeNode> st = new ArrayDeque<>();
        TreeNode cur = root;
        Long prev = null;
        while (cur != null || !st.isEmpty()) {
            while (cur != null) { st.push(cur); cur = cur.left; }
            cur = st.pop();
            if (prev != null && cur.val <= prev) return false;
            prev = (long) cur.val;
            cur = cur.right;
        }
        return true;
    }

    public static void main(String[] args) {
        ValidateBST s = new ValidateBST();
        TreeNode ok = new TreeNode(2, new TreeNode(1), new TreeNode(3));
        TreeNode bad = new TreeNode(5, new TreeNode(4), new TreeNode(6, new TreeNode(3), new TreeNode(7)));
        TreeNode dup = new TreeNode(2, new TreeNode(2), null);
        TreeNode extreme = new TreeNode(Integer.MAX_VALUE, new TreeNode(Integer.MIN_VALUE), null);
        System.out.println(s.isValidBST(ok) + " " + s.isValidBST(bad) + " " + s.isValidBST(dup) + " " + s.isValidBST(extreme));
        // true false false true
        System.out.println(s.isValidInorder(ok) + " " + s.isValidInorder(bad) + " " + s.isValidInorder(dup) + " " + s.isValidInorder(extreme));
        // true false false true
    }
}
```

**Test cases.** `[2,1,3]` → true · grandchild violation `[5,4,6,null,null,3,7]` → false · duplicate `[2,2]` → false · extremes `[MAX, MIN]` → true (breaks `int` sentinels) · single node → true.

**Complexity.** Time O(n). Space O(h) — recursion stack (or explicit stack for the inorder version).
