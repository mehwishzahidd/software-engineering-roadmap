# 09 — Recursion

> **Week 8** · NeetCode section: — (foundation for **Trees**, **Backtracking**, **1-D DP**) · Target: **6 new problems** (review week) · Java rep: **50** (Week 8)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#09--recursion) · Python: [`19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md) (recursion limit, `functools.cache`) · Java: [quick reference](./java-dsa-toolkit.md)

Every tree, graph DFS, backtracking and DP solution in the rest of this roadmap is recursion. This week you learn to **trust the recursive leap of faith**: define what the function returns, handle the base case, assume the recursive call works on the smaller input, and combine. Week 8 is also a review week (FlowGrid v1.0 ships, Checkpoint 8), so new problems are capped at 6.

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| The contract | Write one sentence: "`f(x)` returns ___ for input ___." Everything follows from it |
| Base case(s) | Smallest inputs answered directly; must be reachable from every input |
| Progress | Each call must move toward a base case (n − 1, n // 2, `node.next`, smaller index range) |
| Leap of faith | Assume `f(smaller)` is correct; only reason about one level |
| Call stack | Each call is a frame holding locals; depth = space cost. Python's default limit is **1000** frames → `RecursionError` |
| `sys.setrecursionlimit` | Raises the limit (e.g. 10⁵) but the C stack can still overflow and crash; on Python 3.12+ `@cache`-decorated recursion is also capped by a separate C recursion limit (a few thousand frames). Iteration/tabulation is the safer fix |
| Recursion tree | Draw calls as a tree: #nodes × work per node = time. Naive Fibonacci: ~2ⁿ nodes |
| Tail recursion | Python (and Java) do **not** optimise tail calls |
| Helper with extra parameters | Nested `def dfs(node, depth)` closures that read outer variables; use `nonlocal` to rebind an outer counter |
| Return values vs accumulators | Returning is cleaner; a `nonlocal best` / `self.best` is fine for "max over all nodes" |
| Divide and conquer | Split, solve halves, combine: merge sort, fast power |
| Memoisation | `@functools.cache` (3.9+) or `@lru_cache(maxsize=None)` → the bridge to DP ([18](./18-dp-1d.md)); arguments must be hashable |
| Recursion → iteration | Replace the call stack with an explicit list-as-stack when depth may exceed ~10³–10⁴ |

Interview angle: "What's the space complexity of your recursive solution?" — always includes O(depth) stack. "What happens with 10⁵ nodes in Python?" — `RecursionError` unless you iterate.

---

## 2. Prerequisite knowledge

- [`06-stack-queue.md`](./06-stack-queue.md) — the call stack *is* a stack.
- [`08-linked-lists.md`](./08-linked-lists.md) — lists are recursive structures (`head` + smaller list).
- Python functions, closures, `nonlocal`, decorators — [`19-python/01-python-core.md`](../19-python/01-python-core.md).

---

## 3. Python implementation

```python
import sys
from functools import cache


def factorial(n: int) -> int:
    """Contract: n! for n >= 0. Base: n <= 1. Progress: n - 1."""
    return 1 if n <= 1 else n * factorial(n - 1)


def fib_naive(n: int) -> int:                 # O(2^n): draw the tree for n = 5 (15 calls)
    return n if n < 2 else fib_naive(n - 1) + fib_naive(n - 2)


@cache                                        # memoised: O(n) time and space
def fib_memo(n: int) -> int:
    return n if n < 2 else fib_memo(n - 1) + fib_memo(n - 2)


def range_sum(a: list[int], lo: int, hi: int) -> int:
    """Divide and conquer on INDICES (no slicing): depth O(log n)."""
    if lo > hi:
        return 0
    if lo == hi:
        return a[lo]
    mid = (lo + hi) // 2
    return range_sum(a, lo, mid) + range_sum(a, mid + 1, hi)


def binary_strings(n: int) -> list[str]:
    out: list[str] = []
    path: list[str] = []

    def build() -> None:                      # closure reads n, path, out
        if len(path) == n:
            out.append("".join(path))
            return
        for ch in "01":
            path.append(ch)
            build()
            path.pop()                        # undo — the seed of backtracking (file 14)

    build()
    return out


def merge_sort(a: list[int]) -> list[int]:
    if len(a) <= 1:
        return a
    mid = len(a) // 2
    left, right = merge_sort(a[:mid]), merge_sort(a[mid:])   # slices copy: O(n log n) total, fine here
    out, i, j = [], 0, 0
    while i < len(left) and j < len(right):
        if left[i] <= right[j]:               # <= keeps it stable
            out.append(left[i]); i += 1
        else:
            out.append(right[j]); j += 1
    out.extend(left[i:]); out.extend(right[j:])
    return out


def count_nodes_with_counter(values: list[int]) -> int:
    """nonlocal: rebind an outer variable from a nested function."""
    count = 0

    def visit(i: int) -> None:
        nonlocal count                        # without this: UnboundLocalError
        if i == len(values):
            return
        if values[i] > 0:
            count += 1
        visit(i + 1)

    visit(0)
    return count


def countdown_iterative(n: int) -> list[int]:
    """Recursion → iteration with an explicit stack."""
    out, stack = [], [n]
    while stack:
        x = stack.pop()
        out.append(x)
        if x > 0:
            stack.append(x - 1)               # the "recursive call"
    return out


assert factorial(20) == 2432902008176640000
assert fib_naive(20) == 6765 and fib_memo(90) == 2880067194370816120
assert range_sum([1, 2, 3, 4, 5], 0, 4) == 15
assert binary_strings(2) == ["00", "01", "10", "11"]
assert merge_sort([5, 2, 9, 1, 5, 6]) == [1, 2, 5, 5, 6, 9]
assert count_nodes_with_counter([1, -2, 3]) == 2
assert countdown_iterative(3) == [3, 2, 1, 0]
try:
    factorial(5000)                           # deeper than the default limit of 1000
except RecursionError:
    print("RecursionError at depth 5000 (limit", sys.getrecursionlimit(), ")")
print("recursion templates ok")
```

### Linked lists recursively (re-solve 206 this way — it's already in your tracker from [08](./08-linked-lists.md))

```python
from __future__ import annotations


class ListNode:
    def __init__(self, val: int = 0, next: ListNode | None = None) -> None:
        self.val, self.next = val, next


def reverse(head: ListNode | None) -> ListNode | None:
    """Contract: reverses the list starting at head, returns the new head."""
    if head is None or head.next is None:
        return head
    new_head = reverse(head.next)   # leap of faith: rest reversed; head.next is now its TAIL
    head.next.next = head           # hook head after that tail
    head.next = None
    return new_head


def remove_elements(head: ListNode | None, val: int) -> ListNode | None:
    """Contract: returns the list with every node of value val removed (LC 203)."""
    if head is None:
        return None
    head.next = remove_elements(head.next, val)
    return head.next if head.val == val else head


h = reverse(ListNode(1, ListNode(2, ListNode(3))))
assert (h.val, h.next.val, h.next.next.val) == (3, 2, 1)
r = remove_elements(ListNode(6, ListNode(1, ListNode(6))), 6)
assert r.val == 1 and r.next is None
print("recursive list helpers ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 8): 50 Pow(x, n)** — `-Integer.MIN_VALUE` overflows in Java; in Python it doesn't.

| Python | Java |
|---|---|
| `@cache` | `long[] memo` / `HashMap<Integer, Long>` |
| `nonlocal best` | an instance field, or return a small record/array |
| `RecursionError` at ~1000 | `StackOverflowError` at ~10⁴ frames |
| `n = -n` (safe) | `long N = n; N = -N;` (int negation of `MIN_VALUE` stays negative) |

```java
class PowRep {
    static double myPow(double x, int n) {
        long N = n;                          // widen BEFORE negating
        if (N < 0) { x = 1 / x; N = -N; }
        return fastPow(x, N);
    }

    private static double fastPow(double x, long n) {
        if (n == 0) return 1.0;
        double half = fastPow(x, n / 2);
        return (n % 2 == 0) ? half * half : half * half * x;
    }

    public static void main(String[] args) {
        System.out.println(myPow(2.0, 10) + " " + myPow(2.0, -2) + " " + myPow(1.0, Integer.MIN_VALUE)); // 1024.0 0.25 1.0
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Shape | Problems |
|---|---|---|
| Linear recursion | `f(n)` calls `f(n-1)` once | 203, 231 |
| Multiple recursion | Two+ calls → exponential unless memoised | 509, 70 (in [18](./18-dp-1d.md)) |
| Divide and conquer | Halve the input | 50, merge sort |
| Structural recursion on lists/trees | Solve for `next` / children, combine | 203, 24, all of [10](./10-trees.md) |
| Recursion on the problem index with parity | Parent position determines child value | 779 |
| Generate-all with undo | Choose → recurse → unchoose | leads into [14](./14-backtracking.md) |
| Memoisation | Cache by (hashable) arguments | leads into [18](./18-dp-1d.md) |

---

## 6. How to recognise the pattern

- The problem is defined in terms of a smaller instance: "a list is a node plus a list", "a tree is a root plus two trees", "ways(n) = ways(n−1) + ways(n−2)".
- Nested structure of unknown depth (nested lists, expressions, directories).
- Exponent/power with n up to 2³¹ → divide and conquer, O(log n).
- The recursion tree has **repeated** subproblems → add `@cache`.
- Depth could exceed ~1000 in Python (long lists, big grids) → iterate.

---

## 7. Beginner problems (Week 8)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 509 | [Fibonacci Number](https://leetcode.com/problems/fibonacci-number/) | Easy | Write it naively, draw the tree for n = 5, then add `@cache`. Submit both. |
| 203 | [Remove Linked List Elements](https://leetcode.com/problems/remove-linked-list-elements/) | Easy | Recursive: fix `head.next` first, then decide whether to keep `head`. |
| 231 | [Power of Two](https://leetcode.com/problems/power-of-two/) | Easy | Recursive: `n == 1` → True; odd or ≤ 0 → False; else recurse on `n // 2`. (Bit trick `n > 0 and n & (n - 1) == 0` comes in [23](./23-bit-manipulation.md).) |

---

## 8. Interview problems (Week 8)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 24 | [Swap Nodes in Pairs](https://leetcode.com/problems/swap-nodes-in-pairs/) | Medium | 8 | <details><summary>show</summary>`second = head.next; head.next = swap(second.next); second.next = head; return second`. Base: fewer than 2 nodes.</details> |
| 50 | [Pow(x, n)](https://leetcode.com/problems/powx-n/) | Medium | 8 | <details><summary>show</summary>`x^n = (x^(n//2))²` (× x if n is odd) → O(log n). Negative n: `1 / x` and `-n`. Compute the half **once**.</details> |
| 779 | [K-th Symbol in Grammar](https://leetcode.com/problems/k-th-symbol-in-grammar/) | Medium | 8 | <details><summary>show</summary>Row n's k-th symbol comes from its parent at row n−1, position `(k + 1) // 2`; odd k copies the parent, even k flips it. O(n) — never build the rows (2ⁿ⁻¹ chars).</details> |

No stretch pool: recursion is exercised by every problem in files 10–22.

---

## 9. Recommended NeetCode / LeetCode practice order

No dedicated NeetCode 150 section — the **Recursion** lessons in NeetCode's DSA for Beginners course cover this. Order: §3 templates from memory → re-solve 206 recursively (tracked under [08](./08-linked-lists.md)) → 509 → 203 → 231 → 24 → 50 → 779 → Java rep 50 → then the Week 8 review plan (see [08 §13](./08-linked-lists.md#13-revision-schedule)).

---

## 10. Target number of problems

**6 new** in Week 8 (review week, ROADMAP §5: "Recursion + review — 6").

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Hitting the **recursion limit** (1000): `RecursionError: maximum recursion depth exceeded`. Options: iterate; or `sys.setrecursionlimit(10**5)` for moderate depths (risk: hard crash if the C stack overflows) — for ≥ 10⁵ depth, iterate.
- **Mutable default arguments** as memo: `def f(n, memo={})` shares the same dict across calls *and across LeetCode test cases*. Use `None` + create inside, or `@cache`.
- `@cache` on a method (`self` becomes part of the key; the cache keeps instances alive) — prefer a nested function or a module-level helper; call `f.cache_clear()` between independent inputs if needed.
- `@cache` with a `list` argument → `TypeError: unhashable type: 'list'`. Pass indices or tuples.
- Rebinding an outer variable in a nested function without `nonlocal` → `UnboundLocalError`. Mutating (`best[0] += 1`, `out.append`) doesn't need it.
- Recursing on slices (`f(a[1:])`) → O(n) copy per call → O(n²) time and memory. Pass indices.

**General**
- Missing or unreachable base case.
- Not using the return value (`remove_elements(head.next, val)` without assigning `head.next = ...`).
- Tracing every level mentally instead of trusting the contract — trace only 2 levels.
- Forgetting the stack in space complexity.
- 50: calling `fast_pow(x, n // 2)` **twice** → back to O(n).

**Java-rep traps (50):** `int` negation overflow; `StackOverflowError` instead of `RecursionError`.

---

## 12. Mastery criteria

- [ ] For any recursive function you write, state its contract in one sentence before coding.
- [ ] Draw the recursion tree for naive `fib(5)` and count calls (15).
- [ ] Reverse a linked list recursively and explain `head.next.next = head` in ≤ 2 sentences.
- [ ] Explain Python's recursion limit and two ways around it.
- [ ] Solve 50 in ≤ 15 min with O(log n); solve 779 in ≤ 20 min without building rows.
- [ ] Java rep: 50 in Java with the `long` widening.

---

## 13. Revision schedule

| Review | Week 8 set (509, 203, 231, 24, 50, 779) |
|---|---|
| Day 0 | W8 |
| Day 3 | W8/W9 |
| Day 7 | W9 |
| Day 14 | W10 |
| Day 30 | W12 (backtracking week — recursion under load) |

**Revisit:** Week 9 (every tree problem), Week 12 (backtracking), Week 15 (memoisation → DP: 509 becomes 1137 and 70), Week 19 (231's bit trick in [23](./23-bit-manipulation.md)).

---

## Worked example — 50. Pow(x, n)

**Clarify.** `x: float` (−100 < x < 100), `n: int` in [−2³¹, 2³¹ − 1]. Return xⁿ. `x = 0` with n < 0 won't be tested. Result within ±10⁴.

**Brute force.** Multiply n times → O(n) = 2·10⁹ multiplications. Far too slow (and `x ** n` is "cheating" in an interview — mention it exists, then implement).

**Optimise.** Contract: `fast_pow(x, n)` returns xⁿ for n ≥ 0.
- Base: n = 0 → 1.
- Recurse on n // 2: `half = fast_pow(x, n // 2)`; result `half * half`, times x if n is odd.
- Negative n: xⁿ = (1/x)⁻ⁿ. Python ints don't overflow, so `-n` is safe (the Java rep must widen to `long`).
Depth log₂(2³¹) = 31 — no recursion-limit risk.

**Code (Python).**

```python
import math


def my_pow(x: float, n: int) -> float:
    if n < 0:
        x, n = 1 / x, -n                 # safe in Python even for n = -2**31

    def fast_pow(base: float, e: int) -> float:
        """Contract: base ** e for e >= 0."""
        if e == 0:
            return 1.0
        half = fast_pow(base, e // 2)    # compute ONCE — two calls would make it O(n)
        return half * half if e % 2 == 0 else half * half * base

    return fast_pow(x, n)


assert my_pow(2.0, 10) == 1024.0
assert math.isclose(my_pow(2.1, 3), 9.261)
assert my_pow(2.0, -2) == 0.25
assert my_pow(1.0, -2**31) == 1.0
assert my_pow(-2.0, 3) == -8.0
assert my_pow(5.0, 0) == 1.0
print("50 worked example passed")
```

**Test cases.** `2, 10` → 1024 · `2.1, 3` → ≈ 9.261 (compare floats with `math.isclose`, not `==`) · `2, -2` → 0.25 · `1, -2³¹` → 1 · negative base with odd exponent → negative · `x, 0` → 1.

**Complexity.** Time O(log n). Space O(log n) recursion stack (an iterative bit-by-bit version gets O(1) space — you'll see bits in [23](./23-bit-manipulation.md)).
