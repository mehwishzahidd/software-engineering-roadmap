# 09 — Recursion

> **Week 9** · NeetCode section: — (foundation for **Trees**, **Backtracking**, **1-D DP**) · Target: **4 new problems**
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#09--recursion) · Java APIs: [toolkit §12 recursion depth](./java-dsa-toolkit.md#12-recursion-depth)

Every tree, graph DFS, backtracking and DP solution in the rest of this roadmap is recursion. This week you learn to **trust the recursive leap of faith**: define what the function returns, handle the base case, assume the recursive call works on the smaller input, and combine.

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| The contract | Write one sentence: "`f(x)` returns ___ for input ___." Everything follows from it |
| Base case(s) | Smallest inputs answered directly; must be reachable from every input |
| Progress | Each call must move toward a base case (n − 1, n / 2, `node.next`, smaller range) |
| Leap of faith | Assume `f(smaller)` is correct; only reason about one level |
| Call stack | Each call is a stack frame holding locals + return address; depth = space cost; too deep → `StackOverflowError` |
| Recursion tree | Draw calls as a tree: #nodes × work per node = time. Fibonacci: ~2ⁿ nodes |
| Head vs tail recursion | Work before vs after the recursive call. Java does **not** optimise tail calls |
| Helper with extra parameters | `helper(node, depth)`, `helper(arr, index, acc)` — carry state downward |
| Return values vs global/field accumulators | Returning is cleaner; a field is fine for "max over all nodes" (diameter, max path sum) |
| Divide and conquer | Split, solve halves, combine: merge sort (Week 6), fast power |
| Memoisation | Cache results of repeated subcalls → the bridge to DP ([18](./18-dp-1d.md)) |
| Recursion → iteration | Replace the call stack with an explicit `ArrayDeque` when depth may exceed ~10⁴ |

Interview angle: "What's the space complexity of your recursive solution?" — always includes O(depth) stack.

---

## 2. Prerequisite knowledge

- [`06-stack-queue.md`](./06-stack-queue.md) — the call stack *is* a stack.
- [`08-linked-lists.md`](./08-linked-lists.md) — lists are recursive structures (`head` + smaller list).
- Merge sort from Week 6 (divide and conquer), JVM stack vs heap — [`01-java/06-memory-jvm.md`](../01-java/06-memory-jvm.md).

---

## 3. Java implementation

```java
import java.util.*;

class RecursionTemplates {
    // Contract: returns n! for n >= 0. Base case n <= 1. Progress: n - 1.
    static long factorial(int n) {
        if (n <= 1) return 1;
        return n * factorial(n - 1);
    }

    // Naive Fibonacci: O(2^n) time — draw the recursion tree to see repeated fib(n-2)
    static int fibNaive(int n) {
        return n < 2 ? n : fibNaive(n - 1) + fibNaive(n - 2);
    }

    // Memoised Fibonacci: O(n) time, O(n) space
    static int fibMemo(int n, int[] memo) {
        if (n < 2) return n;
        if (memo[n] != 0) return memo[n];
        return memo[n] = fibMemo(n - 1, memo) + fibMemo(n - 2, memo);
    }

    // Divide and conquer: sum of a[lo..hi] — depth O(log n) instead of O(n)
    static long rangeSum(int[] a, int lo, int hi) {
        if (lo > hi) return 0;
        if (lo == hi) return a[lo];
        int mid = lo + (hi - lo) / 2;
        return rangeSum(a, lo, mid) + rangeSum(a, mid + 1, hi);
    }

    // Helper with accumulator: collect all binary strings of length n
    static List<String> binaryStrings(int n) {
        List<String> out = new ArrayList<>();
        build(n, new StringBuilder(), out);
        return out;
    }
    private static void build(int n, StringBuilder sb, List<String> out) {
        if (sb.length() == n) { out.add(sb.toString()); return; }
        for (char c : new char[]{'0', '1'}) {
            sb.append(c);
            build(n, sb, out);
            sb.setLength(sb.length() - 1);     // undo — the seed of backtracking (file 14)
        }
    }

    // Recursive string reverse (head recursion)
    static String reverse(String s) {
        if (s.length() <= 1) return s;
        return reverse(s.substring(1)) + s.charAt(0);   // O(n^2) because of substring + concat — say so
    }

    // Merge sort (Week 6) — the classic divide and conquer
    static void mergeSort(int[] a, int lo, int hi, int[] tmp) {   // sorts a[lo..hi]
        if (lo >= hi) return;
        int mid = lo + (hi - lo) / 2;
        mergeSort(a, lo, mid, tmp);
        mergeSort(a, mid + 1, hi, tmp);
        int i = lo, j = mid + 1, k = lo;
        while (i <= mid && j <= hi) tmp[k++] = (a[i] <= a[j]) ? a[i++] : a[j++];  // <= keeps it stable
        while (i <= mid) tmp[k++] = a[i++];
        while (j <= hi) tmp[k++] = a[j++];
        System.arraycopy(tmp, lo, a, lo, hi - lo + 1);
    }

    // Recursion → iteration with an explicit stack (same order as recursive preorder on a range)
    static List<Integer> iterativeCountdown(int n) {
        List<Integer> out = new ArrayList<>();
        Deque<Integer> st = new ArrayDeque<>();
        st.push(n);
        while (!st.isEmpty()) {
            int x = st.pop();
            out.add(x);
            if (x > 0) st.push(x - 1);         // the "recursive call"
        }
        return out;
    }

    public static void main(String[] args) {
        System.out.println(factorial(20));                           // 2432902008176640000
        System.out.println(fibNaive(20) + " " + fibMemo(45, new int[46])); // 6765 1134903170
        System.out.println(rangeSum(new int[]{1, 2, 3, 4, 5}, 0, 4)); // 15
        System.out.println(binaryStrings(2));                       // [00, 01, 10, 11]
        System.out.println(reverse("recursion"));                    // noisrucer
        int[] a = {5, 2, 9, 1, 5, 6};
        mergeSort(a, 0, a.length - 1, new int[a.length]);
        System.out.println(Arrays.toString(a));                     // [1, 2, 5, 5, 6, 9]
        System.out.println(iterativeCountdown(3));                  // [3, 2, 1, 0]
    }
}
```

### Linked lists recursively (re-solve 206 this way — it's already in your tracker from [08](./08-linked-lists.md))

```java
class ListNode {
    int val; ListNode next;
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

class RecursiveLists {
    // Contract: reverses the list starting at head, returns the new head.
    static ListNode reverse(ListNode head) {
        if (head == null || head.next == null) return head;
        ListNode newHead = reverse(head.next);  // leap of faith: rest is reversed, head.next is its TAIL
        head.next.next = head;                  // hook head after that tail
        head.next = null;
        return newHead;
    }

    // Contract: returns the list with every node of value val removed (LC 203)
    static ListNode removeElements(ListNode head, int val) {
        if (head == null) return null;
        head.next = removeElements(head.next, val);
        return head.val == val ? head.next : head;
    }

    public static void main(String[] args) {
        ListNode h = reverse(new ListNode(1, new ListNode(2, new ListNode(3))));
        System.out.println(h.val + " " + h.next.val + " " + h.next.next.val);   // 3 2 1
        ListNode r = removeElements(new ListNode(6, new ListNode(1, new ListNode(6))), 6);
        System.out.println(r.val + " " + r.next);                                  // 1 null
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Shape | Problems |
|---|---|---|
| Linear recursion | `f(n)` calls `f(n-1)` once | 509 (naive), 203 |
| Multiple recursion | Two+ calls → exponential unless memoised | 509, 70 (in [18](./18-dp-1d.md)) |
| Divide and conquer | Halve the input | 50, merge sort |
| Structural recursion on lists/trees | Solve for `next` / children, combine | 203, 24, all of [10](./10-trees.md) |
| Generate-all with undo | Choose → recurse → unchoose | leads into [14](./14-backtracking.md) |
| Memoisation | Cache by argument | leads into [18](./18-dp-1d.md) |

---

## 5. How to recognise the pattern

- The problem is defined in terms of a smaller instance: "a list is a node plus a list", "a tree is a root plus two trees", "ways(n) = ways(n−1) + ways(n−2)".
- Nested structure of unknown depth (nested lists, expressions, directories).
- Exponent/power with n up to 2³¹ → divide and conquer, O(log n).
- If the recursion tree has **repeated** subproblems → add memoisation.
- If depth could exceed ~10⁴ (long lists, big grids) → consider iteration.

---

## 6. Beginner problems (Week 9)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 509 | [Fibonacci Number](https://leetcode.com/problems/fibonacci-number/) | Easy | Write it naively, draw the tree for n = 5, then memoise with `int[]`. Submit both. |
| 203 | [Remove Linked List Elements](https://leetcode.com/problems/remove-linked-list-elements/) | Easy | Recursive: fix `head.next` first, then decide whether to keep `head`. |

---

## 7. Interview problems (Week 9)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 24 | [Swap Nodes in Pairs](https://leetcode.com/problems/swap-nodes-in-pairs/) | Medium | 9 | <details><summary>show</summary>`second = head.next; head.next = swapPairs(second.next); second.next = head; return second;` Base: fewer than 2 nodes.</details> |
| 50 | [Pow(x, n)](https://leetcode.com/problems/powx-n/) | Medium | 9 | <details><summary>show</summary>`x^n = (x^(n/2))²` (× x if n odd) → O(log n). Negative n: use `long` because `-Integer.MIN_VALUE` overflows.</details> |

No stretch pool: recursion is exercised by every problem in files 10–22.

---

## 8. Recommended NeetCode / LeetCode practice order

No dedicated NeetCode 150 section — NeetCode's **"Recursion"** lessons in the DSA for Beginners course cover this. Order: §3 templates from memory → re-solve 206 recursively (tracked under [08](./08-linked-lists.md)) → 509 → 203 → 24 → 50 → then start [10 Trees](./10-trees.md) the same week.

---

## 9. Target number of problems

**4 new** in Week 9 (+ 3 tree problems from [10](./10-trees.md) = the week's 7).

---

## 10. Mistakes beginners commonly make

- Missing or unreachable base case → `StackOverflowError`.
- Base case too late (`if (node.next == null)` when `node` itself can be `null`).
- Not using the return value (`removeElements(head.next, val);` without assigning to `head.next`).
- Tracing every level mentally instead of trusting the contract → confusion. Trace only 2 levels.
- Forgetting the stack in space complexity.
- Java: `-n` when `n == Integer.MIN_VALUE` stays negative → cast to `long` first (50).
- Sharing a mutable `StringBuilder`/`List` across branches without undoing the change.
- Using `static` fields as accumulators on LeetCode without resetting them — the judge reuses the class across test cases.

---

## 11. Mastery criteria

- [ ] For any recursive function you write, state its contract in one sentence before coding.
- [ ] Draw the recursion tree for naive `fib(5)` and count calls (15).
- [ ] Reverse a linked list recursively and explain `head.next.next = head` in ≤ 2 sentences.
- [ ] Solve 50 in ≤ 15 min with O(log n) and the `MIN_VALUE` edge case.
- [ ] Convert one recursive solution into an explicit-stack iterative one.

---

## 12. Revision schedule

| Review | Week 9 set (509, 203, 24, 50) |
|---|---|
| Day 0 | W9 |
| Day 3 | W9/W10 |
| Day 7 | W10 |
| Day 14 | W11 |
| Day 30 | W13 (backtracking week — recursion under load) |

**Revisit:** Week 10 (every tree problem), Week 13 (backtracking), Week 16 (memoisation → DP: 509 becomes 1137 and 70).

---

## Worked example — 50. Pow(x, n)

**Clarify.** `double x` (−100 < x < 100), `int n` in [−2³¹, 2³¹ − 1]. Return xⁿ. `x = 0` with n < 0 won't be tested (guaranteed). Answer fits a double within ±10⁴.

**Brute force.** Multiply n times → O(n) = 2·10⁹ multiplications. Too slow, and n can be negative.

**Optimise.** Contract: `pow(x, n)` returns xⁿ for n ≥ 0.
- Base: n = 0 → 1.
- Recurse on n / 2: `half = pow(x, n / 2)`; result `half * half`, times x if n is odd.
- Negative n: xⁿ = (1/x)⁻ⁿ. But `-(-2147483648)` overflows `int` → convert n to `long` first.
Depth log₂(2³¹) = 31 — no stack risk.

**Code.**

```java
class Pow {
    public double myPow(double x, int n) {
        long N = n;                    // avoid overflow of -Integer.MIN_VALUE
        if (N < 0) {
            x = 1 / x;
            N = -N;
        }
        return fastPow(x, N);
    }

    // Contract: returns x^n for n >= 0
    private double fastPow(double x, long n) {
        if (n == 0) return 1.0;
        double half = fastPow(x, n / 2);   // compute ONCE — calling it twice makes it O(n) again
        return (n % 2 == 0) ? half * half : half * half * x;
    }

    public static void main(String[] args) {
        Pow p = new Pow();
        System.out.println(p.myPow(2.0, 10));                 // 1024.0
        System.out.println(p.myPow(2.1, 3));                  // ≈ 9.261
        System.out.println(p.myPow(2.0, -2));                 // 0.25
        System.out.println(p.myPow(1.0, Integer.MIN_VALUE));  // 1.0
        System.out.println(p.myPow(-2.0, 3));                 // -8.0
    }
}
```

**Test cases.** `2, 10` → 1024 · `2.1, 3` → 9.261 (floating-point: ≈ 9.261000000000001) · `2, -2` → 0.25 · `1, MIN_VALUE` → 1 (overflow trap) · negative base with odd exponent → negative result · `x, 0` → 1.

**Complexity.** Time O(log n). Space O(log n) recursion stack (an iterative bit-by-bit version gets O(1) space — mention it; you'll see bits in [23](./23-bit-manipulation.md)).
