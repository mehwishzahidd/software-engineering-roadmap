# 03 · Pitfalls, object model and complexity (Week 2)

> Goal: never lose an interview to a Python trap, and state the cost of any built-in with a one-sentence reason.
> Time: 4–5 h in Week 2 (Day 4). Re-read before every checkpoint and OA simulation.
> Every demo below is runnable: paste into `python3 -i` and predict first. Table version: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md) §6–§7.

**Contents:** [Object model](#1-object-model-names-references-mutability-identity) · [Copy semantics](#2-copy-semantics) · [Pitfalls with demos](#3-pitfalls-with-runnable-demos-and-fixes) · [Complexity table](#4-complexity-table-of-built-ins) · [CPython notes](#5-cpython-notes) · [Memory/time trade-offs](#6-memory--time-trade-offs) · [Self-test](#7-25-question-self-test)

---

## 1. Object model: names, references, mutability, identity

- Everything is an object with an **identity** (`id(x)`), a **type** and a **value**.
- A variable is a **name bound to an object**, not a box holding a value. `a = b` binds `a` to the object `b` refers to; no copy happens.
- **Immutable** objects (int, float, str, tuple, frozenset, bool, None) cannot change; "modifying" them creates a new object and rebinds the name. **Mutable** objects (list, dict, set, most user classes) change in place, and every name bound to them sees the change.
- `==` compares values (`__eq__`); `is` compares identity. Small ints (−5..256) and short strings may be cached, so `is` sometimes "works" by accident — never rely on it for values.
- Function arguments are passed by assignment: the parameter is a new name bound to the same object.

```python
a = [1, 2]; b = a
print(a is b, id(a) == id(b))            # True True — one list, two names
b.append(3); print(a)                    # [1, 2, 3]
b = b + [4]; print(a, b, a is b)         # [1, 2, 3] [1, 2, 3, 4] False — `+` created a new list and rebound b
b = a; b += [9]; print(a)                # [1, 2, 3, 9] — `+=` on a list MUTATES in place (extend)
s = "ab"; t = s; t += "c"; print(s, t)   # ab abc — strings are immutable: += created a new string
x = 5; y = x; y += 1; print(x, y)        # 5 6
```

---

## 2. Copy semantics

| Method | What you get | Cost |
|---|---|---|
| `b = a` | alias (same object) | O(1) |
| `a[:]`, `a.copy()`, `list(a)`, `copy.copy(a)` | **shallow** copy: new outer list, same inner objects | O(n) |
| `copy.deepcopy(a)` | recursive copy of everything reachable | O(size of graph) |
| `[row[:] for row in grid]` | idiomatic 2-D copy (one level deep) | O(rows·cols) |
| `dict(d)`, `d.copy()`, `set(s)` | shallow copies | O(n) |
| `tuple(a)`, `frozenset(s)` | immutable shallow snapshot | O(n) |

```python
import copy
grid = [[0, 0], [0, 0]]
shallow = grid[:]            # or grid.copy()
shallow[0][0] = 1
print(grid)                  # [[1, 0], [0, 0]] — inner lists are shared!
shallow[1] = [9, 9]
print(grid)                  # [[1, 0], [0, 0]] — rebinding an element of the OUTER copy does not affect grid
deep = copy.deepcopy(grid); deep[0][0] = 7; print(grid[0][0])     # 1 — independent
```

In backtracking, `res.append(path[:])` is a shallow copy of a list of ints — sufficient because ints are immutable. If `path` held lists, you'd need a deeper copy.

---

## 3. Pitfalls with runnable demos and fixes

### 3.1 Mutable default arguments

```python
def append_to(x, acc=[]):        # default evaluated ONCE at def time
    acc.append(x); return acc
print(append_to(1), append_to(2))            # [1, 2] [1, 2]  (same list, printed after both calls)

def append_to(x, acc=None):                  # FIX
    if acc is None: acc = []
    acc.append(x); return acc
```
Same trap: `memo={}` as a cache parameter, class attributes `items = []` shared by all instances, `dataclass` fields without `default_factory`.

### 3.2 Aliasing: `a = b`

```python
matrix = [[1, 2], [3, 4]]
row = matrix[0]; row.append(99)
print(matrix)                                # [[1, 2, 99], [3, 4]]
```
Fix: copy deliberately (`row = matrix[0][:]`) or accept the alias on purpose and say so.

### 3.3 `[[0] * n] * m`

```python
g = [[0] * 3] * 2; g[0][0] = 1
print(g, g[0] is g[1])                       # [[1, 0, 0], [1, 0, 0]] True
g = [[0] * 3 for _ in range(2)]              # FIX: fresh inner list per row
```

### 3.4 Shallow vs deep copy — see §2.

### 3.5 String immutability and concat cost

```python
import time
n = 200_000
t = time.perf_counter(); s = ""
for i in range(n): s += "x"
t1 = time.perf_counter() - t
t = time.perf_counter(); parts = []
for i in range(n): parts.append("x")
s2 = "".join(parts); t2 = time.perf_counter() - t
print(f"+=: {t1:.3f}s  join: {t2:.3f}s")     # CPython's in-place += optimization can hide the O(n²); don't rely on it
```
Also: `s[0] = "z"` → `TypeError`. Convert to `list(s)`, mutate, `"".join`.

### 3.6 `list.pop(0)` / `insert(0, x)` are O(n)

```python
from collections import deque
import time
n = 100_000
q = list(range(n)); t = time.perf_counter()
while q: q.pop(0)
print(f"list.pop(0): {time.perf_counter() - t:.2f}s")     # seconds
q = deque(range(n)); t = time.perf_counter()
while q: q.popleft()
print(f"deque.popleft: {time.perf_counter() - t:.3f}s")   # milliseconds
```

### 3.7 `in` on a list vs a set

```python
import time
n = 20_000
lst = list(range(n)); st = set(lst)
t = time.perf_counter(); hits = sum(1 for x in range(n) if x in lst); print(f"list: {time.perf_counter() - t:.2f}s")
t = time.perf_counter(); hits = sum(1 for x in range(n) if x in st);  print(f"set:  {time.perf_counter() - t:.4f}s")
```
Rule: any membership test inside a loop → set (or dict) unless n is tiny.

### 3.8 Recursion limit

```python
import sys
def depth(n): return 0 if n == 0 else 1 + depth(n - 1)
print(sys.getrecursionlimit())               # 1000
try: depth(2000)
except RecursionError as e: print("RecursionError")
sys.setrecursionlimit(10_000); print(depth(2000))   # works; 10**6 may segfault the C stack on big inputs

def depth_iter(n):                           # FIX for large depth: explicit stack
    stack, count = [n], 0
    while stack:
        k = stack.pop()
        if k > 0: count += 1; stack.append(k - 1)
    return count
```
Linked list of 10^5 nodes, DFS on a 1000×1000 grid, deep backtracking → iterative or raise the limit *and* mention the trade-off. In LeetCode, `sys.setrecursionlimit(10**6)` at the top of `class Solution` is common and acceptable.

### 3.9 heapq tuple comparison and ties

```python
import heapq
class Node:
    def __init__(self, v): self.v = v
h = []
heapq.heappush(h, (1, Node(1)))
try: heapq.heappush(h, (1, Node(2)))         # priorities tie → compares Node < Node
except TypeError as e: print("TypeError:", e)
# FIX 1: tiebreak counter        heapq.heappush(h, (1, i, Node(2)))
# FIX 2: __lt__ on Node           def __lt__(self, o): return self.v < o.v
# FIX 3: @dataclass(order=True) with payload field(compare=False)
```
Also: `heapq` is a **min**-heap; for max, negate. Tuples sort by first element then second — `(dist, node_id)` orders ties by id, which is usually fine but is a hidden rule; mention it.

### 3.10 `is` vs `==`

```python
a = 256; b = 256; print(a is b)              # True (cached small int)
a = 257; b = 257; print(a == b, a is b)      # True, and `is` is implementation-defined (often False when created separately)
print([] == [], [] is [])                    # True False
x = None; print(x is None, x == None)        # True True — but `is None` is the idiom (== can be overridden)
```

### 3.11 Integer division and negative modulo

```python
print(7 // 2, -7 // 2, 7 % 3, -7 % 3, 7 % -3)      # 3 -4 1 2 -2
import math
print(int(-7 / 2), math.trunc(-3.5))                # -3 -3 truncation toward zero (Java semantics)
print((-1) % 5)                                     # 4 — circular indexing works without special-casing
```
Ceil division: `-(-a // b)`. Java-style truncation: `int(a / b)` (float precision) or `abs(a) // abs(b)` with sign fix.

### 3.12 Floating point equality

```python
print(0.1 + 0.2 == 0.3, 0.1 + 0.2)           # False 0.30000000000000004
import math; print(math.isclose(0.1 + 0.2, 0.3))     # True
from decimal import Decimal; print(Decimal("0.1") + Decimal("0.2") == Decimal("0.3"))   # True — money (LedgerX tooling)
print(int(10 ** 16 / 3) == 10 ** 16 // 3)    # False — float loses integer precision above 2**53
```
Fix in DSA: keep everything in integers (compare `a*d` vs `b*c` instead of `a/b` vs `c/d`); use `math.isqrt`, `//`.

### 3.13 `sorted(d)` sorts keys

```python
d = {"b": 2, "a": 3}
print(sorted(d))                              # ['a', 'b']
print(sorted(d.values()), sorted(d.items(), key=lambda kv: kv[1]))   # [2, 3] [('b', 2), ('a', 3)]
```

### 3.14 Iterating while mutating

```python
nums = [1, 2, 3, 4]
for x in nums:
    if x % 2 == 0: nums.remove(x)             # skips elements: index shifts under the iterator
print(nums)                                   # [1, 3] here, but the pattern is wrong in general — try [2, 2, 3]
s = {1, 2, 3}
try:
    for x in s: s.discard(x)
except RuntimeError as e: print("RuntimeError:", e)
nums = [x for x in nums if x % 2]             # FIX: build a new container
for k in list(d):                             # FIX: iterate a snapshot when deleting keys
    if d[k] == 0: del d[k]
```

### 3.15 `global` / `nonlocal`

```python
total = 0
def add(x):
    total += x                                # UnboundLocalError: assignment makes `total` local
def add(x):
    global total; total += x                  # FIX for module-level (avoid in real code; fine in a kata)

def solve(nums):
    best = 0
    def dfs(i):
        nonlocal best                         # FIX for enclosing-function scope
        best = max(best, nums[i])
    dfs(0); return best
```
Mutating a list/dict from an inner function needs no declaration: `res.append(...)`, `seen.add(...)`, `best[0] = ...`.

### 3.16 Late-binding closures in loops

```python
fs = [lambda: i for i in range(3)]
print([f() for f in fs])                      # [2, 2, 2] — all closures see the final i
fs = [lambda i=i: i for i in range(3)]        # FIX: bind at definition via default arg
print([f() for f in fs])                      # [0, 1, 2]
```
Same trap with `functools.partial`-less callbacks in tooling and with lambdas in dict comprehensions.

### 3.17 Slicing copies (O(k))

```python
def sum_rec(a): return 0 if not a else a[0] + sum_rec(a[1:])      # O(n²) time and memory
def sum_rec(a, i=0): return 0 if i == len(a) else a[i] + sum_rec(a, i + 1)   # FIX: O(n)
```
`s[i:]` in string recursion, `nums[1:]` in merge sort (fine: O(n log n) total), `path[:]` in backtracking (necessary).

### 3.18 `defaultdict` insertion on read

```python
from collections import defaultdict
adj = defaultdict(list)
if adj[5]: pass
print(dict(adj))                              # {5: []} — a read inserted a key; breaks len(adj) and iteration
if 5 in adj: pass                             # FIX
```

### 3.19 `max`/`min` of empty, `list.index` on missing, `dict[k]` on missing

`max([])` → `ValueError` (use `default=`); `[1].index(9)` → `ValueError` (use `in` first); `d[k]` → `KeyError` (use `.get`). Interviewers watch edge cases: empty input is the first one.

### 3.20 Boolean/`or` defaults hiding zero

```python
def f(limit=None): limit = limit or 10       # limit=0 becomes 10 — bug
def f(limit=None): limit = 10 if limit is None else limit   # FIX
```

### 3.21 Comparing chained / unintended tuple

```python
a = 3
x = 1, 2                                     # x is the tuple (1, 2), not 1
def pair(a, b):
    return a, b                              # returns a tuple — fine
if a == 1 or 2: ...                          # always True — `2` is truthy; write `a in (1, 2)`
```

### 3.22 Class attribute vs instance attribute

```python
class Bag:
    items = []                               # shared by ALL instances
    def add(self, x): self.items.append(x)
a, b = Bag(), Bag(); a.add(1); print(b.items)      # [1]
class Bag:
    def __init__(self): self.items = []      # FIX
```

---

## 4. Complexity table of built-ins

Average case unless noted. Source: python.org TimeComplexity wiki + CPython implementation.

| Structure | Operation | Complexity | Note |
|---|---|---|---|
| **list** | `a[i]`, `a[i] = v`, `len`, `append`, `pop()` | O(1) | append amortized (over-allocation ~12.5%) |
| | `insert(i)`, `pop(i)`, `del a[i]`, `remove` | O(n) | shift |
| | `x in a`, `index`, `count`, `min`, `max`, `sum` | O(n) | |
| | `a[i:j]`, `copy`, `a + b`, `extend` | O(k) | k = size involved |
| | `sort`, `sorted` | O(n log n) | stable Timsort; O(n) presorted |
| | `reverse` | O(n) | `reversed()` iterator is O(1) to create |
| | `a * k` | O(n·k) | shares element references |
| **deque** | `append`, `appendleft`, `pop`, `popleft`, `dq[0]`, `dq[-1]` | O(1) | |
| | `dq[i]` middle, `remove`, `rotate(k)` | O(n) / O(k) | |
| **dict** | `d[k]`, `d[k]=v`, `del`, `k in d`, `get`, `setdefault`, `pop` | O(1) avg, O(n) worst | hash collisions; keys must be hashable |
| | iteration, `copy`, `list(d)` | O(n) | insertion order |
| | `next(iter(d))` | O(1) | `list(d)[0]` is O(n) |
| **set** | `add`, `remove`, `discard`, `x in s`, `pop` | O(1) avg | |
| | `a \| b`, `a - b`, `a ^ b` | O(len a + len b) | |
| | `a & b` | O(min(len a, len b)) | |
| | `a <= b` | O(len a) | |
| **str** | `s[i]`, `len` | O(1) | |
| | `s + t`, `s * k`, slicing, `lower`, `strip`, `split`, `replace` | O(n) | new string |
| | `x in s`, `find`, `count` | O(n·m) worst | practical fast paths |
| | `"".join(parts)` | O(total) | |
| | `s == t` | O(min len) | early exit on first mismatch |
| **heapq** | `heappush`, `heappop`, `heappushpop`, `heapreplace` | O(log n) | |
| | `heapify` | O(n) | |
| | `h[0]` | O(1) | |
| | `nlargest(k)`, `nsmallest(k)` | O(n log k) | |
| | `merge` | O(total log k) | lazy |
| **bisect** | `bisect_left`, `bisect_right` | O(log n) | |
| | `insort` | O(n) | shift dominates |
| **Counter** | build | O(n) | |
| | `most_common(k)` | O(n log k) | O(n log n) without k |
| **tuple** | index, len | O(1) | hashing O(len) |
| **misc** | `range` create / `in` / index | O(1) | |
| | `enumerate`, `zip`, `map`, `filter` create | O(1) | lazy |
| | `lru_cache` lookup | O(1) avg | plus hashing the args |
| | `int` arithmetic on n-digit numbers | O(n) add, ~O(n^1.6) mul | matters only for huge ints |

---

## 5. CPython notes (interview-level)

- **Lists are dynamic arrays** of pointers to objects (`PyObject*`), over-allocated on growth so `append` is amortized O(1). Not linked lists; there is no O(1) middle insert.
- **dict and set are open-addressing hash tables.** `dict` stores a compact entries array (insertion order) plus a sparse index table; load factor kept ≤ 2/3 → resize. Keys need `__hash__` and `__eq__`; equal objects must hash equal. Hash randomization for `str` is per process (`PYTHONHASHSEED`), so set iteration order of strings changes between runs.
- **Small ints (−5..256) and short interned strings are cached** → `is` may accidentally return True.
- **The GIL** (Global Interpreter Lock): only one thread executes Python bytecode at a time in the default CPython build. Threads still help for I/O-bound work (network, disk — the GIL is released while waiting), not for CPU-bound work; use `multiprocessing` or `concurrent.futures.ProcessPoolExecutor` for CPU parallelism. (Python 3.13 ships an experimental free-threaded build; the default build in 3.12 has the GIL.) Interview answer: "I know why my load harness uses threads for HTTP calls and processes for CPU-heavy parsing."
- **Recursion:** each Python call creates a frame object; the limit (default 1000) protects the C stack. No tail-call optimization.
- **Everything is a reference; memory is reference-counted plus a cycle collector.** `del x` removes a name, not necessarily the object.
- **Timsort** is the sort for `list.sort`/`sorted`: hybrid merge/insertion sort, stable, adaptive.
- **Integers are arbitrary precision** (no overflow); floats are IEEE-754 doubles.
- **Function calls are relatively expensive** (~50–100 ns): in tight loops, inlining a tiny helper or using a comprehension can matter for OAs with strict time limits; algorithmic complexity matters far more.
- Python ≈ 10–100× slower than Java for CPU-bound loops: an O(n²) with n = 10^4 is 10^8 ops ≈ 10+ s in Python — reduce complexity rather than micro-optimize. Rule of thumb: ~10^7 simple operations per second.

---

## 6. Memory / time trade-offs

| Choice | Time | Memory | When |
|---|---|---|---|
| `set(nums)` for membership | O(1) lookups | +O(n) | any repeated `in` |
| `Counter` vs `[0]*26` | same | dict overhead vs 26 ints | fixed small alphabet → array |
| Memoization (`@cache`) | avoids recomputation | O(#states) | overlapping subproblems |
| Tabulation with rolling rows | same | O(cols) instead of O(rows·cols) | only previous row needed |
| Generator vs list | lazy, same total | O(1) vs O(n) | stream once |
| `deque(maxlen=k)` | O(1) push/evict | O(k) | last-k windows |
| Sorting a copy vs in place | same | +O(n) | need the original order |
| Prefix sums | O(1) range query after O(n) build | O(n) | many range-sum queries |
| Heap of size k vs full sort | O(n log k) vs O(n log n) | O(k) vs O(n) | k ≪ n, streaming |
| String `join` vs `+=` | O(n) vs O(n²) worst | O(n) | always join |
| Iterative DFS vs recursive | same | explicit stack (heap) vs frames (limited) | depth > ~1000 |
| Tuple vs list for records | same | tuple smaller, hashable | fixed shape |
| `__slots__` on node classes | slightly faster attribute access | ~40 % less per object | 10^5+ nodes |

---

## 7. 25-question self-test

Answer out loud or in writing before opening each answer.

<details><summary>1. What prints? <code>a = [1, 2]; b = a; b += [3]; print(a)</code></summary>

`[1, 2, 3]`. `+=` on a list calls `__iadd__` (extend in place); `a` and `b` are the same object.
</details>

<details><summary>2. What prints? <code>a = (1, 2); b = a; b += (3,); print(a)</code></summary>

`(1, 2)`. Tuples are immutable; `+=` created a new tuple and rebound `b`.
</details>

<details><summary>3. Why does <code>def f(x, seen=set())</code> misbehave and how do you fix it?</summary>

The set is created once at definition and shared across calls. Use `seen=None` and `if seen is None: seen = set()`.
</details>

<details><summary>4. What does <code>[[0] * 3] * 2</code> produce and why?</summary>

Two references to the same inner list; mutating one row mutates "both". Use a comprehension to create distinct rows.
</details>

<details><summary>5. Complexity of <code>x in lst</code> vs <code>x in st</code>?</summary>

O(n) linear scan vs O(1) average hash lookup (O(n) worst case with pathological collisions).
</details>

<details><summary>6. Why is BFS with <code>queue.pop(0)</code> wrong for large inputs?</summary>

`pop(0)` shifts all remaining elements, O(n) per pop → O(n²) BFS. `deque.popleft()` is O(1).
</details>

<details><summary>7. What is the result of <code>-7 // 2</code> and <code>-7 % 2</code>? Same in Java?</summary>

`-4` and `1` (floor division; remainder has the divisor's sign). Java gives `-3` and `-1` (truncation).
</details>

<details><summary>8. What is <code>heapq</code>'s heap type and how do you get the other one?</summary>

Min-heap. Negate numeric keys, or define `__lt__` inverted, or wrap in a tuple `(-key, ...)`.
</details>

<details><summary>9. Why might <code>heapq.heappush(h, (prio, obj))</code> raise <code>TypeError</code>?</summary>

If two priorities tie, Python compares the second elements; objects without `__lt__` are not orderable. Add a unique counter as the second element or implement `__lt__`.
</details>

<details><summary>10. Difference between shallow and deep copy in one sentence each.</summary>

Shallow: new outer container, same inner objects. Deep: recursively copies every nested object.
</details>

<details><summary>11. Why does <code>count += 1</code> inside a nested function raise <code>UnboundLocalError</code>?</summary>

Assignment makes `count` local to the inner function, and it is read before being assigned. Declare `nonlocal count`.
</details>

<details><summary>12. What do the lambdas return? <code>fs = [lambda: i for i in range(3)]</code></summary>

All return `2`: closures capture the variable `i`, evaluated when called, after the loop finished. Fix: `lambda i=i: i`.
</details>

<details><summary>13. Complexity of <code>s += ch</code> in a loop of n iterations, and the fix?</summary>

O(n²) worst case (new string each time). Append to a list and `"".join`.
</details>

<details><summary>14. Complexity of <code>sorted(xs)</code>; is it stable; is <code>xs.sort()</code> different?</summary>

O(n log n) Timsort, stable, adaptive. `xs.sort()` sorts in place and returns `None`; `sorted` returns a new list.
</details>

<details><summary>15. What does <code>sorted(d)</code> give for a dict?</summary>

A sorted list of the keys. For pairs use `sorted(d.items(), key=...)`.
</details>

<details><summary>16. What happens if you add to a set while iterating over it?</summary>

`RuntimeError: Set changed size during iteration`. Iterate over a copy or collect changes first.
</details>

<details><summary>17. Why is <code>a[1:]</code> in each recursive call a problem?</summary>

Each slice copies O(n) → O(n²) total time and memory. Pass an index.
</details>

<details><summary>18. Is <code>0.1 + 0.2 == 0.3</code>? What do you use for money?</summary>

False (binary floating point). `decimal.Decimal` with string inputs for money; integers (cents) also work.
</details>

<details><summary>19. What is the default recursion limit and two ways to handle deep recursion?</summary>

1000. `sys.setrecursionlimit(n)` (with risk of C-stack overflow at extreme depth) or rewrite iteratively with an explicit stack.
</details>

<details><summary>20. Why does <code>if adj[v]:</code> on a <code>defaultdict</code> change the dict?</summary>

`__getitem__` on a missing key calls the factory and inserts the result. Use `v in adj`.
</details>

<details><summary>21. <code>a = 1000; b = 1000; a is b</code> — what and why?</summary>

Implementation-defined (may be True if the compiler folded both constants into one object, often False in a REPL). Only small ints −5..256 are guaranteed cached. Use `==`.
</details>

<details><summary>22. What is the GIL and when do threads still help?</summary>

A mutex letting one thread run Python bytecode at a time in CPython. Threads help for I/O-bound work (the GIL is released during blocking I/O); use processes for CPU-bound parallelism.
</details>

<details><summary>23. Complexity of <code>heapq.heapify</code>, <code>heappush</code>, <code>nlargest(k)</code>?</summary>

O(n), O(log n), O(n log k).
</details>

<details><summary>24. Why must objects that are equal have equal hashes?</summary>

dict/set find the bucket by hash, then confirm with `__eq__`. If equal objects hash differently, lookups miss. Defining `__eq__` without `__hash__` makes the class unhashable on purpose.
</details>

<details><summary>25. A Python solution is O(n²) with n = 10^5. Roughly how long, and what do you do?</summary>

~10^10 simple operations ≈ minutes; time limit exceeded. Reduce complexity (hash map, sort + two pointers, heap, prefix sums) — never micro-optimize an O(n²).
</details>

Score: 22+ → move on. Below 18 → redo §3 demos and retake in two days (Sunday review block).
