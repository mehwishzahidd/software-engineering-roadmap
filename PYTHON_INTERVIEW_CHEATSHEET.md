# 🐍 Python Interview Cheatsheet

> Keep this file open during every DSA session, NeetCode problem, OA simulation and mock.
> Python 3.12 is the primary coding-interview language in this roadmap (see [`ROADMAP.md`](./ROADMAP.md) §4 and §10).
> Deep explanations live in [`19-python/`](./19-python/); this file is the compressed reference: syntax → data structures → templates → pitfalls → complexity → start ritual.

**Contents:** [Imports](#1-imports-to-type-first) · [Syntax](#2-syntax-you-need-in-interviews) · [Data structures](#3-data-structures-with-big-o) · [Sorting](#4-sorting) · [Templates](#5-templates) · [Pitfalls](#6-python-interview-pitfalls) · [Complexity of Python ops](#7-how-to-explain-complexity-of-python-ops) · [Start ritual](#8-interview-start-ritual)

---

## 1. Imports to type first

```python
import sys, math, heapq, bisect
from collections import Counter, defaultdict, deque
from functools import lru_cache, cache, cmp_to_key, reduce
from itertools import permutations, combinations, product, accumulate, groupby, chain, pairwise
from dataclasses import dataclass, field
from typing import Optional
```

LeetCode already has `List`, `Optional`, `collections`, `math`, `heapq` etc. imported; HackerRank/CodeSignal often do **not** — type the block anyway, it costs 5 seconds.

---

## 2. Syntax you need in interviews

### 2.1 Input / output for OAs (HackerRank, CodeSignal, custom judges)

```python
import sys

n = int(input())                          # one integer line
a, b = map(int, input().split())          # two integers on one line
nums = list(map(int, input().split()))    # a whole line of integers
s = input().strip()                       # a string (strip the newline)

# Fast reading for big inputs (10^5+ tokens): read everything once
data = sys.stdin.read().split()           # list of tokens (str)
it = iter(data)
n = int(next(it)); nums = [int(next(it)) for _ in range(n)]

# Fast output: build then write once
out = []
for x in nums:
    out.append(str(x))
sys.stdout.write("\n".join(out) + "\n")
print(*nums)                              # space-separated, unpack the list
print(*nums, sep="\n")                    # one per line
```

### 2.2 Everyday syntax

```python
name, n = "sku", 42
print(f"{name}={n:05d} {n/7:.2f} {n:>8} {n:,} {name!r} {n:b} {n:x}")   # f-strings + format specs
x = 5 if n > 3 else 0                     # ternary
a, b = b, a                               # swap
first, *rest = [1, 2, 3]                  # star unpacking -> 1, [2, 3]
*init, last = [1, 2, 3]                   # [1, 2], 3
(x, y), z = (1, 2), 3                     # nested unpacking
for i, v in enumerate(nums, start=1): ...  # index + value
for a, b in zip(xs, ys): ...              # stops at the shortest; zip(strict=True) raises on mismatch
for a, b in pairwise(nums): ...           # consecutive pairs (3.10+)
q, r = divmod(17, 5)                      # (3, 2)
7 // 2, -7 // 2                           # 3, -4  (floor division rounds toward -inf)
7 % 3, -7 % 3                             # 1, 2   (result has the sign of the divisor)
INF, NINF = float("inf"), float("-inf")   # sentinels; math.inf works too
2 ** 100                                  # ints are arbitrary precision: no overflow, no Long
ord("a"), chr(98), ord(c) - ord("a")      # 97, "b", 0..25 index for lowercase
"abc" * 2, "-".join(["a", "b"])           # "abcabc", "a-b"
s[::-1], s[1:4], s[-3:], s[::2]           # reverse, slice [1,4), last three, every 2nd
nums[i:j] = []                            # delete a slice (O(n))
```

**Slicing rules:** `a[start:stop:step]`, stop is exclusive, negatives count from the end, out-of-range bounds are clamped (no exception), a slice is a **new list** (O(k) time + memory).

### 2.3 Comprehensions

```python
squares = [x * x for x in range(10) if x % 2 == 0]
grid = [[0] * cols for _ in range(rows)]          # correct 2-D init (NOT [[0]*cols]*rows)
index = {v: i for i, v in enumerate(nums)}        # dict comprehension
seen = {x % 10 for x in nums}                     # set comprehension
total = sum(x * x for x in nums)                  # generator: no intermediate list
flat = [v for row in grid for v in row]           # nested: outer loop first
```

### 2.4 `sorted` / `min` / `max` with keys

```python
sorted(words, key=len)                                    # by length, stable
sorted(words, key=lambda w: (-len(w), w))                 # multi-key: length desc, then alpha asc
sorted(pairs, key=lambda p: p[1], reverse=True)           # reverse keeps stability (equal keys keep order)
nums.sort()                                               # in place, returns None
max(nums, key=abs); min(d, key=d.get)                     # element with max |x|; dict key with min value
max(d.items(), key=lambda kv: kv[1])                      # (key, value) of the largest value
sorted(d)                                                 # sorts the KEYS of a dict
sorted(d.items(), key=lambda kv: (-kv[1], kv[0]))         # freq desc, key asc (classic top-k tiebreak)
```

### 2.5 Functions, closures, lambdas

```python
def f(a, b=2, *args, key=None, **kwargs): ...   # positional, default, varargs, kw-only, kwargs
def solve():
    best = 0
    def dfs(node):
        nonlocal best            # needed to REASSIGN an enclosing variable; not needed to mutate a list/dict
        best = max(best, node.val)
    ...
add = lambda a, b: a + b         # one expression only
```

---

## 3. Data structures with Big-O

### 3.1 `list` (dynamic array)

| Op | Code | Complexity |
|---|---|---|
| index / assign | `a[i]`, `a[i] = v` | O(1) |
| append / pop end | `a.append(v)`, `a.pop()` | O(1) amortized |
| insert / pop front | `a.insert(0, v)`, `a.pop(0)` | **O(n)** — use `deque` |
| `len`, `a[-1]` | | O(1) |
| membership | `v in a` | **O(n)** — use `set` |
| slice | `a[i:j]` | O(j−i) copy |
| `a.index(v)`, `a.count(v)`, `a.remove(v)` | | O(n) |
| `a.sort()`, `sorted(a)` | Timsort | O(n log n) |
| `a.reverse()`, `reversed(a)` | in place / iterator | O(n) / O(1) |
| `a.extend(b)`, `a + b` | | O(len b) / O(len a + len b) |
| `min(a)`, `max(a)`, `sum(a)` | | O(n) |
| `a.copy()`, `a[:]`, `list(a)` | shallow copy | O(n) |

### 3.2 `tuple` — immutable list; hashable (dict key, set member) if its elements are hashable. `(x,)` is a one-element tuple.

### 3.3 `str` (immutable)

| Method | Note |
|---|---|
| `s.split()`, `s.split(",")`, `s.strip()`, `s.lower()`, `s.upper()` | new strings; O(n) |
| `s.startswith(p)`, `s.endswith(p)`, `s.find(t)` (−1 if absent), `s.index(t)` (raises), `s.count(t)` | O(n·m) worst |
| `s.isalpha()`, `s.isdigit()`, `s.isalnum()`, `s.isspace()` | per-char checks |
| `s.replace(a, b)`, `s.zfill(w)`, `s.center(w)` | |
| `"".join(parts)` | **the** way to build strings; O(total length) |
| `s == t` | O(min len); `s < t` lexicographic |
| `s[i]`, `len(s)` | O(1) |
| `s += "x"` in a loop | O(n²) worst — collect into a list, join at the end |

### 3.4 `set` / `frozenset`

| Op | Code | Complexity (average) |
|---|---|---|
| add / remove / discard | `s.add(v)`, `s.remove(v)` (KeyError), `s.discard(v)` (silent) | O(1) |
| membership | `v in s` | O(1) |
| union / intersection / difference / symmetric | `a \| b`, `a & b`, `a - b`, `a ^ b` | O(len a + len b) |
| subset | `a <= b`, `a.issubset(b)` | O(len a) |
| `s.pop()` | arbitrary element | O(1) |

Elements must be hashable: numbers, strings, tuples of hashables, `frozenset`. **Not** lists/dicts/sets.

### 3.5 `dict` (insertion-ordered since 3.7)

| Op | Code | Complexity (average) |
|---|---|---|
| get / set / delete | `d[k]`, `d[k] = v`, `del d[k]` | O(1) |
| safe get | `d.get(k, default)` | O(1), no insertion |
| get-or-insert | `d.setdefault(k, []).append(v)` | O(1) |
| pop | `d.pop(k, default)`, `d.popitem()` (LIFO) | O(1) |
| iterate | `d.keys()`, `d.values()`, `d.items()` | O(n), insertion order |
| membership | `k in d` (keys only!) | O(1) |
| merge | `d \| e`, `d.update(e)` | O(len e) |
| `dict(zip(keys, vals))`, `dict.fromkeys(keys, 0)` | build | O(n) |

Keys must be hashable. Use a `tuple` for composite keys: `d[(r, c)]`.

### 3.6 `collections.Counter`

```python
c = Counter("mississippi")             # Counter({'i': 4, 's': 4, 'p': 2, 'm': 1})
c["z"]                                 # 0 — missing keys return 0, never KeyError
c.most_common(2)                       # [('i', 4), ('s', 4)]  (O(n log k)); ties keep first-seen order
c.total()                              # 11 (3.10+)
Counter(a) - Counter(b)                # multiset difference, drops counts <= 0
Counter(a) & Counter(b)                # min per key (intersection); | is max
c.elements()                           # iterator repeating each key count times
sum(c.values()); list(c)               # total / distinct keys
Counter(a) == Counter(b)               # anagram test in O(n)
```

### 3.7 `collections.defaultdict`

```python
graph = defaultdict(list)     # graph[u].append(v) — no KeyError, creates [] on first access
freq = defaultdict(int)       # freq[x] += 1
nested = defaultdict(lambda: defaultdict(int))
# WARNING: reading defaultdict[k] INSERTS k. Use `k in dd` for pure membership tests.
```

### 3.8 `collections.deque` (doubly linked block list)

| Op | Complexity |
|---|---|
| `append`, `appendleft`, `pop`, `popleft` | O(1) |
| `dq[0]`, `dq[-1]` | O(1); middle index O(n) |
| `rotate(k)` | O(k) |
| `deque(maxlen=k)` | auto-evicts from the opposite end |
| `extend`, `extendleft` (reverses order) | O(len) |

Use for BFS queues, sliding-window maximum (monotonic deque), and "last k items".

### 3.9 `heapq` (binary min-heap on a plain list)

```python
h = []
heapq.heappush(h, (dist, node))        # tuples compare element by element: first by dist, then node
d, u = heapq.heappop(h)                # smallest
h[0]                                   # peek, O(1)
heapq.heapify(nums)                    # O(n) in place
heapq.heappush(h, -x); -heapq.heappop(h)          # max-heap by negation
heapq.heappushpop(h, x)                # push then pop smallest (faster than push+pop)
heapq.heapreplace(h, x)                # pop smallest then push
heapq.nlargest(k, nums, key=...)       # O(n log k); nsmallest likewise
heapq.merge(*sorted_iterables)         # lazy k-way merge
```

Push/pop O(log n); no `decrease-key` → push duplicates and skip stale entries when popping. Tuple ties: if the second element is unorderable (a custom object), add a counter as tiebreak: `(priority, count, obj)`, or define `__lt__`.

### 3.10 `bisect` (binary search on a sorted list)

```python
i = bisect.bisect_left(a, x)    # first index with a[i] >= x  (insert position for leftmost)
j = bisect.bisect_right(a, x)   # first index with a[i] > x   (== bisect.bisect)
j - i                           # count of x in a
bisect.insort(a, x)             # insert keeping sorted: O(log n) search + O(n) shift
bisect.bisect_left(a, x, lo, hi, key=...)   # key= supported since 3.10
```

`a[i-1] < x <= a[i]` for `bisect_left`; `a[j-1] <= x < a[j]` for `bisect_right`.

### 3.11 `itertools`

```python
permutations("abc", 2)         # ordered, no repeats: ab ac ba bc ca cb
combinations([1,2,3], 2)       # unordered: (1,2) (1,3) (2,3)
combinations_with_replacement([1,2], 2)
product("ab", repeat=2)        # cartesian: aa ab ba bb
list(accumulate([1,2,3]))      # prefix sums [1, 3, 6]; accumulate(xs, max) for running max
[(k, len(list(g))) for k, g in groupby("aaabbc")]   # run-length encoding (consecutive only!)
chain(a, b); chain.from_iterable(list_of_lists)      # flatten one level lazily
islice(iterable, 5); zip_longest(a, b, fillvalue=0)
```

### 3.12 `functools`

```python
@lru_cache(maxsize=None)   # or @cache (3.9+): memoize; args must be hashable (tuple, not list)
def fib(n): return n if n < 2 else fib(n-1) + fib(n-2)
fib.cache_clear()          # between test cases if the judge reuses the process

sorted(items, key=cmp_to_key(lambda a, b: -1 if a + b < b + a else 1))   # comparator → key
reduce(lambda acc, x: acc * x, nums, 1)                                    # fold
```

### 3.13 `math`

`math.inf`, `math.gcd(a, b)`, `math.lcm(a, b)`, `math.isqrt(n)` (exact integer sqrt), `math.log2(n)`, `math.comb(n, k)`, `math.perm(n, k)`, `math.factorial(n)`, `math.ceil`, `math.floor`, `math.hypot(dx, dy)`, `math.isclose(a, b)`. Ceil division without floats: `-(-a // b)` or `(a + b - 1) // b`.

### 3.14 `dataclasses` and type hints

```python
@dataclass
class Node:
    val: int
    next: "Node | None" = None            # forward reference as a string

@dataclass(order=True)                    # generates __lt__ etc. field by field → usable in heapq
class Task:
    priority: int
    name: str = field(compare=False)     # exclude from ordering

@dataclass(frozen=True)                   # immutable + hashable → dict key / set member
class Point:
    r: int
    c: int

def two_sum(nums: list[int], target: int) -> list[int]: ...
def find(root: Optional["TreeNode"]) -> "TreeNode | None": ...
```

---

## 4. Sorting

- Python uses **Timsort**: O(n log n) worst, O(n) on already-sorted or reverse-sorted runs, **stable** (equal keys keep original order).
- `list.sort()` sorts **in place** and returns `None`; `sorted(iterable)` returns a **new list** and works on any iterable (dict, set, generator, string → list of chars).
- `key=` is called **once per element** (O(n) key calls); `reverse=True` still keeps stability.
- Multi-key: return a tuple `key=lambda x: (x.a, -x.b, x.c)`; for a string you cannot negate, sort twice (stable) or use `cmp_to_key`.
- Sorting a list of tuples with no key sorts lexicographically by all elements.
- Descending numbers: `sorted(nums, reverse=True)`; descending by one key while ascending by another: negate the numeric key.
- `cmp_to_key(cmp)` when the order is only defined pairwise (LeetCode 179 *Largest Number*): `cmp(a, b)` returns negative if `a` should come first.

---

## 5. Templates

Every template is Python 3.12 and runnable as-is. Adapt names, do not memorize blindly — say what each line does out loud.

### 5.1 Binary search (three variants)

```python
def bsearch_exact(a: list[int], target: int) -> int:
    lo, hi = 0, len(a) - 1
    while lo <= hi:
        mid = (lo + hi) // 2                 # no overflow in Python
        if a[mid] == target:
            return mid
        if a[mid] < target:
            lo = mid + 1
        else:
            hi = mid - 1
    return -1

def lower_bound(a: list[int], target: int) -> int:
    """First index i with a[i] >= target (== bisect_left). Returns len(a) if none."""
    lo, hi = 0, len(a)
    while lo < hi:
        mid = (lo + hi) // 2
        if a[mid] < target:
            lo = mid + 1
        else:
            hi = mid
    return lo

def search_on_answer(lo: int, hi: int, feasible) -> int:
    """Smallest x in [lo, hi] with feasible(x) True; feasible is monotone False...False True...True."""
    while lo < hi:
        mid = (lo + hi) // 2
        if feasible(mid):
            hi = mid
        else:
            lo = mid + 1
    return lo            # e.g. Koko Eating Bananas (875), Capacity To Ship Packages (1011)
```

### 5.2 Two pointers

```python
def two_sum_sorted(a: list[int], target: int) -> tuple[int, int] | None:
    i, j = 0, len(a) - 1
    while i < j:
        s = a[i] + a[j]
        if s == target:
            return i, j
        if s < target:
            i += 1
        else:
            j -= 1
    return None

def remove_duplicates_in_place(a: list[int]) -> int:      # slow/fast writer pattern
    w = 0
    for r in range(len(a)):
        if r == 0 or a[r] != a[r - 1]:
            a[w] = a[r]
            w += 1
    return w
```

### 5.3 Sliding window (fixed and variable)

```python
def max_sum_window_k(a: list[int], k: int) -> int:        # fixed size
    cur = sum(a[:k]); best = cur
    for r in range(k, len(a)):
        cur += a[r] - a[r - k]
        best = max(best, cur)
    return best

def longest_substring_no_repeat(s: str) -> int:           # variable size, shrink while invalid
    last = {}
    l = best = 0
    for r, ch in enumerate(s):
        if ch in last and last[ch] >= l:
            l = last[ch] + 1
        last[ch] = r
        best = max(best, r - l + 1)
    return best

def min_window_len_at_least(a: list[int], target: int) -> int:   # generic shrink loop
    l = cur = 0; best = float("inf")
    for r in range(len(a)):
        cur += a[r]
        while cur >= target:                 # window valid → try to shrink
            best = min(best, r - l + 1)
            cur -= a[l]; l += 1
    return 0 if best == float("inf") else best
```

### 5.4 Prefix sums

```python
def prefix_sums(a: list[int]) -> list[int]:
    p = [0] * (len(a) + 1)
    for i, v in enumerate(a):
        p[i + 1] = p[i] + v
    return p                                 # sum(a[i:j]) == p[j] - p[i]

def count_subarrays_with_sum(a: list[int], k: int) -> int:   # hashmap of prefix counts
    seen = {0: 1}; run = ans = 0
    for v in a:
        run += v
        ans += seen.get(run - k, 0)
        seen[run] = seen.get(run, 0) + 1
    return ans
```

### 5.5 Monotonic stack

```python
def next_greater(a: list[int]) -> list[int]:
    res = [-1] * len(a)
    st = []                                  # indices with decreasing values
    for i, v in enumerate(a):
        while st and a[st[-1]] < v:
            res[st.pop()] = v
        st.append(i)
    return res

def largest_rectangle(heights: list[int]) -> int:
    st, best = [], 0
    for i, h in enumerate(heights + [0]):    # sentinel flushes the stack
        while st and heights[st[-1]] >= h:
            top = st.pop()
            left = st[-1] if st else -1
            best = max(best, heights[top] * (i - left - 1))
        st.append(i)
    return best
```

### 5.6 Linked list: node, reversal, fast/slow

```python
class ListNode:
    def __init__(self, val: int = 0, next: "ListNode | None" = None):
        self.val, self.next = val, next

def reverse_list(head):
    prev, cur = None, head
    while cur:
        cur.next, prev, cur = prev, cur, cur.next    # RHS evaluated fully before assignment
    return prev

def middle(head):                                     # fast/slow; also detects cycles
    slow = fast = head
    while fast and fast.next:
        slow, fast = slow.next, fast.next.next
    return slow

def has_cycle(head) -> bool:
    slow = fast = head
    while fast and fast.next:
        slow, fast = slow.next, fast.next.next
        if slow is fast:
            return True
    return False

def merge_two(a, b):
    dummy = tail = ListNode()                         # dummy head avoids edge cases
    while a and b:
        if a.val <= b.val: tail.next, a = a, a.next
        else:              tail.next, b = b, b.next
        tail = tail.next
    tail.next = a or b
    return dummy.next
```

### 5.7 BFS (grid and graph)

```python
DIRS = ((1, 0), (-1, 0), (0, 1), (0, -1))

def bfs_grid(grid: list[list[int]], sr: int, sc: int) -> dict[tuple[int, int], int]:
    R, C = len(grid), len(grid[0])
    dist = {(sr, sc): 0}
    q = deque([(sr, sc)])
    while q:
        r, c = q.popleft()
        for dr, dc in DIRS:
            nr, nc = r + dr, c + dc
            if 0 <= nr < R and 0 <= nc < C and grid[nr][nc] == 0 and (nr, nc) not in dist:
                dist[(nr, nc)] = dist[(r, c)] + 1       # mark when ENQUEUING, not when popping
                q.append((nr, nc))
    return dist

def bfs_graph(adj: dict[int, list[int]], src: int) -> dict[int, int]:
    dist = {src: 0}
    q = deque([src])
    while q:
        u = q.popleft()
        for v in adj[u]:
            if v not in dist:
                dist[v] = dist[u] + 1
                q.append(v)
    return dist

def bfs_levels(adj, src):                                  # level-by-level (len(q) snapshot)
    q, seen, level = deque([src]), {src}, 0
    while q:
        for _ in range(len(q)):
            u = q.popleft()
            for v in adj[u]:
                if v not in seen:
                    seen.add(v); q.append(v)
        level += 1
```

### 5.8 DFS (recursive and iterative)

```python
def dfs_rec(adj, u, seen):
    seen.add(u)
    for v in adj[u]:
        if v not in seen:
            dfs_rec(adj, v, seen)

def dfs_iter(adj, src):
    seen, st = {src}, [src]
    while st:
        u = st.pop()
        for v in adj[u]:
            if v not in seen:
                seen.add(v); st.append(v)
    return seen

def count_islands(grid: list[list[str]]) -> int:           # flood fill, mutates grid
    R, C = len(grid), len(grid[0])
    def sink(r, c):
        if not (0 <= r < R and 0 <= c < C) or grid[r][c] != "1":
            return
        grid[r][c] = "0"
        for dr, dc in DIRS:
            sink(r + dr, c + dc)
    return sum(1 for r in range(R) for c in range(C) if grid[r][c] == "1" and (sink(r, c) or True))
```

If the grid can be 1000×1000, recursion may exceed the limit — use the iterative stack version or `sys.setrecursionlimit(10**6)` (and know why: see §6).

### 5.9 Tree traversals

```python
class TreeNode:
    def __init__(self, val=0, left=None, right=None):
        self.val, self.left, self.right = val, left, right

def preorder(n, out):  # root, left, right
    if n: out.append(n.val); preorder(n.left, out); preorder(n.right, out)
def inorder(n, out):   # left, root, right  → sorted for a BST
    if n: inorder(n.left, out); out.append(n.val); inorder(n.right, out)
def postorder(n, out): # left, right, root  → children before parent (heights, subtree sums)
    if n: postorder(n.left, out); postorder(n.right, out); out.append(n.val)

def inorder_iter(root):
    out, st, cur = [], [], root
    while cur or st:
        while cur:                 # go left as far as possible
            st.append(cur); cur = cur.left
        cur = st.pop()
        out.append(cur.val)
        cur = cur.right
    return out

def level_order(root):
    if not root: return []
    out, q = [], deque([root])
    while q:
        level = []
        for _ in range(len(q)):
            n = q.popleft(); level.append(n.val)
            if n.left: q.append(n.left)
            if n.right: q.append(n.right)
        out.append(level)
    return out

def height(n):  return 0 if not n else 1 + max(height(n.left), height(n.right))
```

### 5.10 BST operations

```python
def bst_search(n, key):
    while n and n.val != key:
        n = n.left if key < n.val else n.right
    return n

def bst_insert(n, key):
    if not n: return TreeNode(key)
    if key < n.val: n.left = bst_insert(n.left, key)
    else:           n.right = bst_insert(n.right, key)
    return n

def is_valid_bst(root):
    def ok(n, lo, hi):
        if not n: return True
        if not (lo < n.val < hi): return False
        return ok(n.left, lo, n.val) and ok(n.right, n.val, hi)
    return ok(root, float("-inf"), float("inf"))

def bst_delete(n, key):
    if not n: return None
    if key < n.val:   n.left = bst_delete(n.left, key)
    elif key > n.val: n.right = bst_delete(n.right, key)
    else:
        if not n.left:  return n.right
        if not n.right: return n.left
        succ = n.right
        while succ.left: succ = succ.left
        n.val = succ.val
        n.right = bst_delete(n.right, succ.val)
    return n
```

### 5.11 Trie

```python
class Trie:
    def __init__(self):
        self.root = {}                    # char -> child dict; "$" marks end of word

    def insert(self, word: str) -> None:
        node = self.root
        for ch in word:
            node = node.setdefault(ch, {})
        node["$"] = True

    def _walk(self, s: str):
        node = self.root
        for ch in s:
            node = node.get(ch)
            if node is None: return None
        return node

    def search(self, word: str) -> bool:
        node = self._walk(word)
        return node is not None and "$" in node

    def starts_with(self, prefix: str) -> bool:
        return self._walk(prefix) is not None
```

### 5.12 Heap: top-k and k-way merge

```python
def top_k_frequent(nums: list[int], k: int) -> list[int]:
    cnt = Counter(nums)
    return [x for x, _ in heapq.nlargest(k, cnt.items(), key=lambda kv: kv[1])]   # O(n log k)

def k_smallest_stream(nums, k):        # keep a max-heap of size k (negate), O(n log k)
    h = []
    for x in nums:
        heapq.heappush(h, -x)
        if len(h) > k: heapq.heappop(h)
    return sorted(-x for x in h)

def merge_k_sorted(lists: list[list[int]]) -> list[int]:
    h = [(lst[0], i, 0) for i, lst in enumerate(lists) if lst]     # (value, list idx, elem idx)
    heapq.heapify(h)
    out = []
    while h:
        v, i, j = heapq.heappop(h)
        out.append(v)
        if j + 1 < len(lists[i]):
            heapq.heappush(h, (lists[i][j + 1], i, j + 1))
    return out
```

### 5.13 Backtracking: subsets, permutations, combinations

```python
def subsets(nums):
    res, path = [], []
    def bt(i):
        if i == len(nums):
            res.append(path[:])          # COPY the path
            return
        path.append(nums[i]); bt(i + 1); path.pop()     # take
        bt(i + 1)                                        # skip
    bt(0)
    return res

def permutations_(nums):
    res, path, used = [], [], [False] * len(nums)
    def bt():
        if len(path) == len(nums):
            res.append(path[:]); return
        for i, v in enumerate(nums):
            if used[i]: continue
            used[i] = True; path.append(v)
            bt()
            path.pop(); used[i] = False
    bt()
    return res

def combinations_(n, k):                # k-subsets of 1..n
    res, path = [], []
    def bt(start):
        if len(path) == k:
            res.append(path[:]); return
        for v in range(start, n + 1):
            if k - len(path) > n - v + 1: break         # prune: not enough numbers left
            path.append(v); bt(v + 1); path.pop()
    bt(1)
    return res

def combination_sum(cands, target):     # reuse allowed; sort + break to prune
    cands.sort(); res, path = [], []
    def bt(start, remaining):
        if remaining == 0: res.append(path[:]); return
        for i in range(start, len(cands)):
            if cands[i] > remaining: break
            path.append(cands[i]); bt(i, remaining - cands[i]); path.pop()
    bt(0, target)
    return res
```

Skip duplicates in sorted input: `if i > start and nums[i] == nums[i-1]: continue`.

### 5.14 Topological sort (Kahn + DFS)

```python
def topo_kahn(n: int, edges: list[tuple[int, int]]) -> list[int]:
    adj = [[] for _ in range(n)]; indeg = [0] * n
    for u, v in edges:
        adj[u].append(v); indeg[v] += 1
    q = deque(i for i in range(n) if indeg[i] == 0)
    order = []
    while q:
        u = q.popleft(); order.append(u)
        for v in adj[u]:
            indeg[v] -= 1
            if indeg[v] == 0: q.append(v)
    return order if len(order) == n else []             # [] → cycle

def topo_dfs(n, edges):
    adj = [[] for _ in range(n)]
    for u, v in edges: adj[u].append(v)
    WHITE, GRAY, BLACK = 0, 1, 2
    color, order = [WHITE] * n, []
    def dfs(u):
        color[u] = GRAY
        for v in adj[u]:
            if color[v] == GRAY: raise ValueError("cycle")
            if color[v] == WHITE: dfs(v)
        color[u] = BLACK; order.append(u)
    for u in range(n):
        if color[u] == WHITE: dfs(u)
    return order[::-1]
```

### 5.15 Union-Find (path compression + union by rank)

```python
class DSU:
    def __init__(self, n: int):
        self.parent = list(range(n)); self.rank = [0] * n; self.components = n

    def find(self, x: int) -> int:
        while self.parent[x] != x:
            self.parent[x] = self.parent[self.parent[x]]    # path halving
            x = self.parent[x]
        return x

    def union(self, a: int, b: int) -> bool:
        ra, rb = self.find(a), self.find(b)
        if ra == rb: return False
        if self.rank[ra] < self.rank[rb]: ra, rb = rb, ra
        self.parent[rb] = ra
        if self.rank[ra] == self.rank[rb]: self.rank[ra] += 1
        self.components -= 1
        return True
```

Near-O(1) amortized per op (inverse Ackermann).

### 5.16 Dijkstra

```python
def dijkstra(adj: dict[int, list[tuple[int, int]]], src: int, n: int) -> list[float]:
    dist = [float("inf")] * n; dist[src] = 0
    h = [(0, src)]
    while h:
        d, u = heapq.heappop(h)
        if d > dist[u]: continue                 # stale entry
        for v, w in adj[u]:
            nd = d + w
            if nd < dist[v]:
                dist[v] = nd; heapq.heappush(h, (nd, v))
    return dist                                  # O((V + E) log V); non-negative weights only
```

### 5.17 Intervals (sort + merge) and greedy skeleton

```python
def merge_intervals(iv: list[list[int]]) -> list[list[int]]:
    iv.sort(key=lambda x: x[0])
    out = []
    for s, e in iv:
        if out and s <= out[-1][1]:
            out[-1][1] = max(out[-1][1], e)
        else:
            out.append([s, e])
    return out

def max_non_overlapping(iv):            # greedy: sort by END, take if compatible
    iv.sort(key=lambda x: x[1])
    count, last_end = 0, float("-inf")
    for s, e in iv:
        if s >= last_end:
            count += 1; last_end = e
    return count
# Greedy skeleton: (1) sort by the right key, (2) iterate keeping a small state,
# (3) prove the exchange argument out loud ("swapping any optimal choice for mine never hurts").
```

### 5.18 Dynamic programming

```python
# Memoization (top-down)
@cache
def climb(n: int) -> int:
    return 1 if n <= 1 else climb(n - 1) + climb(n - 2)

# 1-D tabulation: House Robber
def rob(nums):
    prev2 = prev1 = 0
    for v in nums:
        prev2, prev1 = prev1, max(prev1, prev2 + v)     # O(1) space
    return prev1

# 2-D tabulation: unique paths in an m×n grid
def unique_paths(m, n):
    dp = [[1] * n for _ in range(m)]
    for r in range(1, m):
        for c in range(1, n):
            dp[r][c] = dp[r - 1][c] + dp[r][c - 1]
    return dp[-1][-1]

# 0/1 knapsack, space-optimized (iterate capacity DOWNWARD to use each item once)
def knapsack(weights, values, cap):
    dp = [0] * (cap + 1)
    for w, v in zip(weights, values):
        for c in range(cap, w - 1, -1):
            dp[c] = max(dp[c], dp[c - w] + v)
    return dp[cap]
# Unbounded knapsack / coin change: iterate capacity UPWARD instead.

# LCS with 2 rows (space optimization)
def lcs(a: str, b: str) -> int:
    prev = [0] * (len(b) + 1)
    for i in range(1, len(a) + 1):
        cur = [0] * (len(b) + 1)
        for j in range(1, len(b) + 1):
            cur[j] = prev[j - 1] + 1 if a[i - 1] == b[j - 1] else max(prev[j], cur[j - 1])
        prev = cur
    return prev[-1]
```

Memo → tabulate when: recursion depth may exceed ~1000, or the problem asks for the full table. Say the state, transition, base case and answer location out loud before coding.

### 5.19 Bit tricks

```python
x & 1                      # odd?
x >> 1, x << 1             # //2, *2
x & (x - 1)                # clear lowest set bit  (== 0 → power of two, for x > 0)
x & -x                     # isolate lowest set bit
bin(x).count("1"); x.bit_count()   # popcount (3.10+)
x ^ x == 0; x ^ 0 == x     # XOR: find the single number (LC 136)
1 << k                     # bitmask with bit k; mask | (1 << k), mask & ~(1 << k), (mask >> k) & 1
for mask in range(1 << n): ...          # enumerate subsets; sub = (sub - 1) & mask to iterate submasks
x & 0xFFFFFFFF             # emulate 32-bit unsigned when a problem says "32-bit"
```

---

## 6. Python interview pitfalls

Full runnable demos and fixes in [`19-python/03-pitfalls-and-complexity.md`](./19-python/03-pitfalls-and-complexity.md).

| # | Pitfall | Wrong | Right |
|---|---|---|---|
| 1 | Mutable default argument is created once per function definition | `def f(x, acc=[]): acc.append(x)` — the list persists between calls | `def f(x, acc=None): acc = [] if acc is None else acc` |
| 2 | `a = b` aliases; both names point to the same list | `b = a; b.append(1)` changes `a` | `b = a[:]` / `a.copy()` / `list(a)` |
| 3 | `[[0]*n]*m` repeats the **same** inner list m times | `g[0][0] = 1` sets column 0 in every row | `[[0]*n for _ in range(m)]` |
| 4 | Shallow copy copies the outer container only | `copy.copy(grid)` shares rows | `copy.deepcopy(grid)` or rebuild |
| 5 | Strings are immutable; `s += c` rebuilds the string each time | O(n²) in a loop | collect in a list, `"".join(parts)` |
| 6 | `list.pop(0)` / `insert(0, x)` shift every element | O(n) per op → O(n²) BFS | `deque.popleft()` / `appendleft()` |
| 7 | `x in list` is a linear scan | O(n) inside a loop → O(n²) | `x in set_` O(1) |
| 8 | Default recursion limit ≈ 1000 frames | deep DFS raises `RecursionError` | iterate with an explicit stack, or `sys.setrecursionlimit(10**6)` (CPython can still segfault at extreme depth — prefer iteration for 10^5+) |
| 9 | heapq compares tuples element by element | `(prio, node)` fails when `node` objects are not orderable and priorities tie | add a tiebreak counter `(prio, i, node)` or define `__lt__` |
| 10 | `is` checks identity, `==` checks equality | `x is 1000` may be False even when `x == 1000` | use `==` for values; `is` only for `None`, `True`, `False` |
| 11 | `//` floors toward −inf; `%` takes the sign of the divisor | `-7 // 2 == -4`, `-7 % 3 == 2` | `int(a / b)` truncates; `math.fmod` for C-style remainder |
| 12 | Float equality | `0.1 + 0.2 == 0.3` is False | integers where possible; `math.isclose`; `decimal.Decimal` for money |
| 13 | `sorted(d)` sorts keys only | you wanted values or pairs | `sorted(d.items(), key=...)` |
| 14 | Mutating a container while iterating it | `for x in s: s.remove(x)` → `RuntimeError`/skips | iterate over a copy `for x in list(s)` or build a new container |
| 15 | Assigning to an outer variable inside a nested function makes it local | `count += 1` inside `dfs` → `UnboundLocalError` | `nonlocal count` (or mutate a list `count[0] += 1`); `global` for module level |
| 16 | Late-binding closures in loops capture the variable, not its value | `[lambda: i for i in range(3)]` all return 2 | `lambda i=i: i` default-arg trick |
| 17 | Slicing copies | `a[1:]` in each recursive call → O(n²) | pass indices `(a, i)` |
| 18 | `defaultdict` lookup inserts | `if dd[k]:` silently creates a key | `if k in dd:` |
| 19 | `dict`/`set` ordering assumptions | sets are unordered; dicts keep insertion order | sort when order matters |
| 20 | Integer division on Python 2 habits | `/` is always float in Python 3 | use `//` for integer results |
| 21 | Returning inside a loop that should collect | early `return` from a `for` inside a nested helper | build results, return once |
| 22 | Forgetting `path[:]` when collecting backtracking results | all results alias the same list → all empty | append a copy |
| 23 | Using `list.remove(x)` inside a loop | O(n) each and skips elements | mark visited / build a new list |
| 24 | Chained comparison surprise | `a < b == c` means `a < b and b == c` | fine, but say it intentionally |
| 25 | `max()` on an empty sequence raises | `max([])` → `ValueError` | `max(xs, default=0)` |

---

## 7. How to explain complexity of Python ops

Say the data structure, then the operation, then the cost, then *why* — every time.

| Operation | Cost | Why (one sentence to say out loud) |
|---|---|---|
| `lst[i]`, `lst.append`, `lst.pop()` | O(1) | list is a dynamic array; append is amortized over occasional resizes |
| `lst.insert(0, x)`, `lst.pop(0)`, `del lst[i]` | O(n) | every later element shifts |
| `x in lst`, `lst.index(x)` | O(n) | linear scan |
| `lst[a:b]` | O(b−a) | copies the slice |
| `sorted(lst)` / `lst.sort()` | O(n log n) | Timsort; O(n) if already sorted |
| `d[k]`, `k in d`, `s.add(x)`, `x in s` | O(1) avg, O(n) worst | hash table; worst case only with adversarial collisions |
| `dict`/`set` iteration | O(n) | walks the table |
| `deque.popleft/appendleft` | O(1) | block-linked structure |
| `heapq.heappush/heappop` | O(log n) | sift up / sift down a binary heap |
| `heapq.heapify` | O(n) | bottom-up sift-down |
| `heapq.nlargest(k, xs)` | O(n log k) | heap of size k |
| `bisect_left` | O(log n) | binary search; `insort` is O(n) because of the shift |
| `"".join(parts)` | O(total chars) | one allocation |
| `s + t` (strings) | O(len s + len t) | new string every time |
| `s[i]`, `len(s)`, `len(anything built-in)` | O(1) | length is stored |
| `min(xs)`, `max(xs)`, `sum(xs)` | O(n) | one pass |
| `Counter(xs)` | O(n) | one pass of dict increments |
| `set(a) & set(b)` | O(min(len a, len b)) | iterates the smaller, probes the larger |
| `lru_cache` hit | O(1) avg | dict keyed by args (hashing a tuple is O(len tuple)) |
| `x ** y` on big ints | not O(1) | arbitrary precision; usually ignored unless numbers are huge |
| String comparison `s == t` | O(min len) | char by char; hashing a string once is O(len) then cached |

Memory: a list of n ints ≈ 8n bytes for pointers plus the int objects (small ints −5..256 are cached singletons). A dict/set uses roughly 2–3× the memory of a list of the same size. Recursion uses one frame per call.

---

## 8. Interview start ritual

One page. Do this every time, including on OA problems you think are easy.

```
1. READ (1–2 min)      Restate the problem in one sentence. Identify input types, sizes (n ≤ 10^5 → O(n log n) or better),
                       value ranges (negatives? duplicates? empty?), and the exact output format.
2. EXAMPLES (2 min)    Walk the given example by hand. Write 2 more: an edge case (empty / one element /
                       all equal) and a tricky case (duplicates, negatives, already sorted, cycle).
3. BRUTE FORCE (2 min) Say the naive solution and its complexity out loud. "Brute force is O(n²) by checking
                       every pair. Let me see if a hash map / sort / two pointers gets that down."
4. OPTIMIZE (3–5 min)  Name the pattern (hash map, two pointers, sliding window, heap, BFS, DP...).
                       State the invariant. Confirm the plan with the interviewer BEFORE coding.
5. CODE (10–15 min)    Top-down: helper names first, then bodies. Narrate what each block does.
                       Use the templates above. Descriptive names (lo/hi, left/right, seen, dist, dp).
6. TRACE (3–5 min)     Run your tricky example through the code line by line. Check off-by-one at
                       boundaries, empty input, the last iteration, and what the function returns.
7. COMPLEXITY (1 min)  Time and space, with one-sentence justifications from the table in §7.
                       Mention what would change for a follow-up (streaming input, k queries, memory limit).
```

**If stuck for > 3 minutes:** say what you know, what you have ruled out, and ask for a hint. Silence is the worst outcome.
**Before submitting an OA:** re-read the constraints, test the empty/single-element case, check `//` vs `/`, check you return (not print) in LeetCode-style, and print exactly the requested format in stdin/stdout-style judges.

Related: [`03-dsa/README.md`](./03-dsa/README.md) · [`16-interview-prep/coding-interview-method.md`](./16-interview-prep/coding-interview-method.md) · [`OA_PREP.md`](./OA_PREP.md) · [`INTERVIEW_CHECKLIST.md`](./INTERVIEW_CHECKLIST.md) · [`19-python/README.md`](./19-python/README.md)
