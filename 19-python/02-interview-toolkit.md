# 02 · The interview toolkit — stdlib in depth (Week 2)

> Goal: reach for the right standard-library tool instantly, know its complexity, and know its traps.
> Time: 6–8 h in Week 2 alongside [`03-dsa/02-hashing.md`](../03-dsa/02-hashing.md). Pitfalls get their own file: [`03-pitfalls-and-complexity.md`](./03-pitfalls-and-complexity.md).
> Compressed version: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md) §3.

**Contents:** [collections](#1-collections) · [heapq](#2-heapq) · [bisect](#3-bisect) · [itertools](#4-itertools) · [functools](#5-functools) · [math](#6-math) · [dataclasses](#7-dataclasses) · [typing](#8-typing) · [Node classes](#9-classes-for-linked-lists-trees-and-graphs) · [`__lt__` for heaps](#10-__lt__-and-heap-items) · [Generators](#11-generators-and-iterators) · [String building](#12-string-building) · [Custom sorting](#13-custom-sorting-deep-dive) · [Mini problems](#14-mini-problem-set) · [Checklist](#15-mastery-checklist)

Each section: **when to use → complexity → idioms → mini problems → common misuse.**

---

## 1. `collections`

### 1.1 `Counter`

**When:** frequencies of anything hashable (chars, numbers, tuples), anagram checks, top-k, multiset arithmetic.
**Complexity:** build O(n); lookup O(1); `most_common(k)` O(n log k) (heap) — `most_common()` with no k sorts all: O(n log n).

```python
from collections import Counter
c = Counter("banana")                       # {'a': 3, 'n': 2, 'b': 1}
c["x"]                                      # 0, and does NOT insert (unlike defaultdict)
c.most_common(1)                            # [('a', 3)]; ties ordered by first insertion
c.update("aa"); c.subtract("b")             # add/subtract counts (subtract may go negative)
c.total()                                   # sum of counts
Counter(a) == Counter(b)                    # anagram check, O(n)
(Counter(a) - Counter(b))                   # keeps only positive counts → "what's missing from b"
Counter(a) & Counter(b)                     # min of counts → common letters (e.g. LC 1002)
Counter(a) | Counter(b)                     # max of counts
+c                                          # drops zero/negative counts
list(c.elements())                          # expand back: ['a','a','a','n','n','b']
Counter(nums).most_common()[-1]             # least common — cheaper: min(c.items(), key=lambda kv: kv[1])
```

Mini problems: Valid Anagram (242); Top K Frequent Elements (347) via `most_common(k)` then via a heap then via bucket sort — compare complexities; Find All Anagrams in a String (438) with a sliding `Counter` (or a 26-array).

Misuse: `c.most_common(k)` in a loop (O(n log k) each time); using `Counter` when a `[0]*26` array is asked for O(1) space over lowercase letters; forgetting `c[x] -= 1` can leave zero-count keys that still count toward `len(c)`.

### 1.2 `defaultdict`

**When:** grouping, adjacency lists, nested counting — any "if key not in d: d[key] = <empty>" pattern.
**Complexity:** same as dict.

```python
from collections import defaultdict
adj = defaultdict(list)                     # graph
for u, v in edges: adj[u].append(v); adj[v].append(u)
groups = defaultdict(list)
for w in words: groups["".join(sorted(w))].append(w)      # Group Anagrams (49)
cnt = defaultdict(int); cnt[x] += 1
memo = defaultdict(lambda: -1)              # any zero-arg callable
grid = defaultdict(lambda: defaultdict(int))
dict(adj)                                   # convert back when returning/printing
```

Misuse: `if adj[v]:` **inserts** `v` with `[]` — use `if v in adj:`; passing `defaultdict(list())` (calls list now → TypeError); iterating `adj` while `adj[nbr]` inserts new keys inside the loop (RuntimeError).

### 1.3 `deque`

**When:** queues (BFS), sliding-window maximum (monotonic deque), "last k items", rotating.
**Complexity:** `append/appendleft/pop/popleft` O(1); index in the middle O(n); `rotate(k)` O(k).

```python
from collections import deque
q = deque([start]); q.append(x); q.popleft()          # BFS queue
dq = deque(maxlen=3); dq.extend([1, 2, 3, 4])         # deque([2, 3, 4]) — evicts from the left
d = deque("abcde"); d.rotate(2)                        # 'deabc'; rotate(-2) → 'cdeab'
d[0], d[-1]                                            # O(1) ends
```

Mini problems: Number of Recent Calls (933) with `maxlen`-free popleft; Sliding Window Maximum (239) monotonic deque of indices; Rotting Oranges (994) multi-source BFS.

Misuse: using a `list` as a queue (`pop(0)` O(n)); using `deque` when you only push/pop one end (a list is fine and faster); `len(q)` snapshot mistakes in level-order BFS (take `for _ in range(len(q))` *before* popping).

### 1.4 `OrderedDict` — awareness only

Since 3.7 a plain `dict` keeps insertion order. `OrderedDict` still has `move_to_end(key, last=True)` and `popitem(last=False)` (FIFO) — the classic LRU Cache (146) building block. Know it exists; in interviews either use `OrderedDict` for LRU or build the doubly-linked-list + dict version to show the mechanics.

```python
from collections import OrderedDict
class LRUCache:
    def __init__(self, cap): self.cap, self.d = cap, OrderedDict()
    def get(self, k):
        if k not in self.d: return -1
        self.d.move_to_end(k); return self.d[k]
    def put(self, k, v):
        self.d[k] = v; self.d.move_to_end(k)
        if len(self.d) > self.cap: self.d.popitem(last=False)
```

### 1.5 `namedtuple`

Lightweight immutable records with field names; hashable; unpackable; `_replace`, `_asdict`. Prefer `@dataclass` for anything with behaviour; use `namedtuple` for tuple-compatible records (heap entries that should compare field by field).

```python
from collections import namedtuple
Edge = namedtuple("Edge", "w u v")
e = Edge(3, 0, 1); e.w, e[0], sorted([Edge(2,1,2), e])        # 3 3 [Edge(w=2,...), Edge(w=3,...)]
```

---

## 2. `heapq`

**When:** "k smallest/largest", "next smallest repeatedly", merge k sorted things, Dijkstra, scheduling by priority, median of a stream (two heaps).
**Complexity:** `heappush`/`heappop` O(log n); `heapify` O(n); `h[0]` O(1); `nlargest/nsmallest(k)` O(n log k); no decrease-key.

```python
import heapq
h = []
heapq.heappush(h, 5); heapq.heappush(h, 1); heapq.heappop(h)     # 1 — MIN-heap only
heapq.heapify(nums)                                              # in place, O(n)
heapq.heappush(h, (priority, payload))                           # tuple: compares priority, then payload
heapq.heappush(h, -x); largest = -heapq.heappop(h)               # max-heap by negation
heapq.heappushpop(h, x)                                          # push x, pop min — one sift; keeps size
heapq.heapreplace(h, x)                                          # pop min, push x
heapq.nlargest(3, words, key=len); heapq.nsmallest(3, items, key=lambda t: t[1])
list(heapq.merge([1, 4], [2, 3], [0, 9]))                        # lazy k-way merge of SORTED inputs

# Median of a stream: max-heap for the low half (negated), min-heap for the high half
class MedianFinder:
    def __init__(self): self.low, self.high = [], []
    def add(self, x):
        heapq.heappush(self.low, -x)
        heapq.heappush(self.high, -heapq.heappop(self.low))
        if len(self.high) > len(self.low): heapq.heappush(self.low, -heapq.heappop(self.high))
    def median(self):
        return -self.low[0] if len(self.low) > len(self.high) else (-self.low[0] + self.high[0]) / 2
```

Mini problems: Kth Largest Element in an Array (215) — size-k min-heap; K Closest Points to Origin (973) — push `(-dist, x, y)`, cap at k; Merge k Sorted Lists (23) — `(node.val, i, node)` with index tiebreak; Task Scheduler (621); Find Median from Data Stream (295).

Misuse: assuming a max-heap exists (it doesn't — negate or `__lt__`); `heapq.heappop(sorted_list)` on a list that was never heapified; pushing tuples where the second element may need comparing and is unorderable (see §10); calling `sorted(h)` to "read the heap" every iteration (O(n log n)); mutating an element's priority while it is inside the heap (invalidates the invariant — push a new entry and skip stale ones, as in Dijkstra).

---

## 3. `bisect`

**When:** insert position or count in a sorted list; "first element ≥ x"; longest increasing subsequence in O(n log n); maintaining a small sorted list; any hand-written binary search you might get wrong.
**Complexity:** `bisect_left/right` O(log n); `insort` O(n) because of the list shift.

```python
import bisect
a = [1, 3, 3, 5]
bisect.bisect_left(a, 3)      # 1  — first index with a[i] >= 3
bisect.bisect_right(a, 3)     # 3  — first index with a[i] > 3
bisect.bisect_right(a, 3) - bisect.bisect_left(a, 3)    # 2 — count of 3
bisect.bisect_left(a, 4)      # 3 — insertion point for a missing value
bisect.insort(a, 4)           # [1, 3, 3, 4, 5]
i = bisect.bisect_left(a, x); found = i < len(a) and a[i] == x
bisect.bisect_left(rows, target, key=lambda r: r[0])     # key= since 3.10

# LIS in O(n log n) — tails[i] = smallest tail of an increasing subsequence of length i+1
def lis(nums):
    tails = []
    for x in nums:
        i = bisect.bisect_left(tails, x)
        if i == len(tails): tails.append(x)
        else: tails[i] = x
    return len(tails)
```

Mini problems: Search Insert Position (35); Longest Increasing Subsequence (300); Time Based Key-Value Store (981) — `bisect_right` on timestamps, index − 1; Find First and Last Position (34) with `bisect_left`/`bisect_right`.

Misuse: calling `bisect` on an unsorted list (silently wrong); confusing left/right for duplicates; using `insort` in a loop as a "sorted container" for 10^5 inserts (O(n²) — use a heap or sort once at the end); forgetting to check `i < len(a)` before indexing.

---

## 4. `itertools`

**When:** enumerate combinatorial candidates in brute-force/backtracking-lite problems; prefix sums; run-length grouping; flattening.

```python
from itertools import permutations, combinations, combinations_with_replacement, product
from itertools import accumulate, groupby, chain, islice, pairwise, zip_longest, count, cycle, repeat
list(permutations([1, 2, 3], 2))           # 6 ordered pairs; n!/(n-r)!
list(combinations("abcd", 2))              # 6 unordered pairs; C(n, r)
list(product([0, 1], repeat=3))            # 8 bit-tuples — all masks
list(product(range(2), range(3)))          # grid cells
list(accumulate([1, 2, 3, 4]))             # [1, 3, 6, 10]; accumulate(xs, max) running max; initial=0 for a leading 0
[(k, len(list(g))) for k, g in groupby("aaabbbcca")]   # [('a',3),('b',3),('c',2),('a',1)] — CONSECUTIVE groups only
[(k, list(g)) for k, g in groupby(sorted(words, key=len), key=len)]    # sort first for true grouping
list(chain([1, 2], [3]))                   # [1, 2, 3]; chain.from_iterable(list_of_lists) flattens one level
list(islice(count(10, 5), 3))              # [10, 15, 20] — count is infinite; islice slices any iterator
list(pairwise([1, 2, 3]))                  # [(1, 2), (2, 3)]
list(zip_longest([1, 2], [9], fillvalue=0))   # [(1, 9), (2, 0)] — Add Two Numbers on digit lists
```

Everything is lazy — wrap in `list()` to see it, or iterate directly (memory-safe for huge products).

Mini problems: Subsets (78) via `chain.from_iterable(combinations(nums, r) for r in range(len(nums)+1))` then hand-written backtracking (interviewers want to see the recursion); Letter Combinations of a Phone Number (17) via `product`; Summary Ranges (228) via `groupby` on `x - i`; Range Sum Query - Immutable (303) via `accumulate(nums, initial=0)`.

Misuse: relying on `itertools` when the interviewer asked for the algorithm ("implement permutations") — use it to verify, then write the backtracking; `groupby` without sorting when groups are non-consecutive; materializing `product` of huge sizes.

---

## 5. `functools`

```python
from functools import lru_cache, cache, cmp_to_key, reduce, partial

@cache                                      # == lru_cache(maxsize=None); 3.9+
def ways(i: int, remaining: int) -> int:    # args must be hashable: ints, strs, tuples — not lists
    if remaining == 0: return 1
    if i == len(coins) or remaining < 0: return 0
    return ways(i, remaining - coins[i]) + ways(i + 1, remaining)
ways.cache_info(); ways.cache_clear()       # hits/misses; clear between test cases

@lru_cache(maxsize=1024)                    # bounded: evicts least-recently-used when full
def expensive(key): ...

sorted(nums, key=cmp_to_key(lambda a, b: (a > b) - (a < b)))        # comparator → key function
def largest_number(nums):                                             # LC 179
    s = sorted(map(str, nums), key=cmp_to_key(lambda a, b: -1 if a + b > b + a else 1))
    return "0" if s[0] == "0" else "".join(s)

reduce(lambda acc, x: acc ^ x, nums, 0)     # XOR fold — Single Number (136); usually a loop reads better
square = partial(pow, exp=2)                # freeze arguments
```

Memoization inside a method: `@cache` on a method caches on `(self, args)` and keeps `self` alive — for LeetCode `class Solution` it is fine; for long-running services prefer an inner function or an explicit dict. Memo keys must be hashable → convert `list` states to `tuple`, or memoize on indices.

Mini problems: Climbing Stairs (70) memo → tabulation; Longest Common Subsequence (1143) memo on `(i, j)` then 2-row DP; Word Break (139) memo on index; Largest Number (179) via `cmp_to_key`.

Misuse: `@cache` on a function taking a list (TypeError: unhashable); unbounded caches in long-lived processes (memory); forgetting `cache_clear()` when a judge reuses global state; using `reduce` for readability-critical logic.

---

## 6. `math`

```python
import math
math.inf, -math.inf                     # same as float('inf')
math.gcd(12, 18), math.lcm(4, 6)        # 6, 12; gcd(*list) accepts many args
math.isqrt(17)                          # 4 — exact integer sqrt; int(17 ** 0.5) can be off by one for huge n
math.log2(1024), math.log(8, 2), math.log10(1000)
math.comb(5, 2), math.perm(5, 2), math.factorial(5)     # 10 20 120 — exact integers
math.ceil(7 / 2), math.floor(-3.5)      # 4, -4; prefer -(-7 // 2) for exact integer ceil
math.hypot(3, 4)                        # 5.0; for distance comparisons use dx*dx + dy*dy (no sqrt)
math.isclose(0.1 + 0.2, 0.3)            # True
math.prod([2, 3, 4])                    # 24
```

Mini problems: Count Primes (204) with a sieve up to `isqrt(n)`; Pow(x, n) (50) — write fast exponentiation by hand, then note `pow(x, n, mod)` exists; Number of 1 Bits (191) via `bin(n).count("1")` and `n.bit_count()` and the `n & (n-1)` loop.

Misuse: float sqrt for perfect-square checks (use `isqrt(n) ** 2 == n`); `math.pow` (returns float) where `**` or `pow` is wanted; `math.ceil(a / b)` on huge ints (float rounding).

---

## 7. `dataclasses`

**When:** node/record classes without boilerplate; anything with several fields you'd otherwise write `__init__`/`__repr__`/`__eq__` for.

```python
from dataclasses import dataclass, field

@dataclass
class Order:
    id: int
    sku: str
    qty: int = 1
    tags: list[str] = field(default_factory=list)      # NEVER `tags: list = []` — dataclass rejects mutable defaults

o = Order(1, "SKU-1"); o.qty += 1; print(o)             # Order(id=1, sku='SKU-1', qty=2, tags=[])
Order(1, "a") == Order(1, "a")                          # True — __eq__ compares fields in order

@dataclass(order=True)
class Job:                                              # __lt__ compares (priority, seq); usable in heapq
    priority: int
    seq: int
    payload: str = field(compare=False)                 # excluded from ordering and equality

@dataclass(frozen=True)
class Cell:                                             # immutable → __hash__ generated → set/dict key
    r: int
    c: int

@dataclass(slots=True)                                  # 3.10+: less memory, faster attribute access
class ListNode:
    val: int
    next: "ListNode | None" = None
```

`field(default_factory=list)`, `field(compare=False)`, `field(repr=False)`; `dataclasses.asdict(o)`, `dataclasses.replace(o, qty=5)`; `__post_init__` for validation. In interviews a dataclass is a fast, readable node class; in tooling (Week 3) it is the record type for CSV/JSON rows.

Misuse: mutable default without `default_factory`; forgetting `frozen=True` when using instances as keys; `order=True` comparing a field you didn't intend (exclude with `compare=False`).

---

## 8. `typing`

Python 3.12 style: use built-in generics and `X | None`; import from `typing` only what has no built-in form.

```python
from typing import Optional, Iterable, Iterator, Callable, Protocol, TypeAlias, Any

def two_sum(nums: list[int], target: int) -> list[int]: ...
def bfs(adj: dict[int, list[int]], src: int) -> dict[int, int]: ...
def find(root: "TreeNode | None") -> "TreeNode | None": ...      # Optional[TreeNode] is the same thing
def apply(f: Callable[[int], int], xs: Iterable[int]) -> Iterator[int]: ...
Grid: TypeAlias = list[list[int]]                                  # or `type Grid = list[list[int]]` in 3.12
Point = tuple[int, int]

class Comparable(Protocol):                     # structural typing (duck typing checked statically)
    def __lt__(self, other: Any) -> bool: ...
```

LeetCode signatures use `List[int]`, `Optional[TreeNode]` from `typing` — both spellings are accepted. Type hints are **not enforced at runtime**; they document intent and let `mypy`/`pyright` (Week 3) catch mistakes. In interviews: annotate the function signature (cheap credibility), skip annotating every local.

---

## 9. Classes for linked lists, trees and graphs

```python
class ListNode:
    __slots__ = ("val", "next")
    def __init__(self, val=0, next=None): self.val, self.next = val, next
    def __repr__(self): return f"{self.val} -> {self.next!r}"     # prints the whole chain: handy for debugging

def build_list(values: list[int]) -> ListNode | None:            # test helper
    dummy = tail = ListNode()
    for v in values: tail.next = ListNode(v); tail = tail.next
    return dummy.next

def to_list(head) -> list[int]:
    out = []
    while head: out.append(head.val); head = head.next
    return out

class TreeNode:
    def __init__(self, val=0, left=None, right=None): self.val, self.left, self.right = val, left, right

def build_tree(level: list[int | None]) -> TreeNode | None:     # LeetCode level-order array → tree
    if not level or level[0] is None: return None
    root = TreeNode(level[0]); q = deque([root]); i = 1
    while q and i < len(level):
        node = q.popleft()
        if i < len(level) and level[i] is not None: node.left = TreeNode(level[i]); q.append(node.left)
        i += 1
        if i < len(level) and level[i] is not None: node.right = TreeNode(level[i]); q.append(node.right)
        i += 1
    return root

# Graphs: usually NOT a class. Adjacency list as dict/list:
adj: dict[int, list[int]] = defaultdict(list)          # sparse, arbitrary labels
adj2: list[list[int]] = [[] for _ in range(n)]         # dense 0..n-1 labels, faster
weighted: dict[int, list[tuple[int, int]]]             # (neighbor, weight)
grid: list[list[int]]                                  # implicit graph; neighbors via DIRS
```

Write `build_list`/`to_list`/`build_tree` once in your katas repo and use them in pytest tests (Week 3) — they are also what you type first in an OA with custom judges.

---

## 10. `__lt__` and heap items

`heapq` compares whole items with `<`. Tuples compare element by element, so `(3, node)` breaks with `TypeError: '<' not supported` when two priorities tie and `node` has no `__lt__`. Three fixes:

```python
# 1. Tiebreak counter (simplest, guarantees FIFO among equals)
import itertools
counter = itertools.count()
heapq.heappush(h, (priority, next(counter), node))

# 2. Define __lt__ on the item
class Item:
    def __init__(self, prio, name): self.prio, self.name = prio, name
    def __lt__(self, other): return self.prio < other.prio
heapq.heappush(h, Item(2, "b"))

# 3. dataclass(order=True) with excluded payload
@dataclass(order=True)
class Entry:
    prio: int
    node: object = field(compare=False)
```

Max-heap for objects: negate a numeric key in the tuple `(-score, i, obj)`, or invert `__lt__` (`return self.prio > other.prio`) — document it in a comment so the reader isn't surprised.

---

## 11. Generators and iterators

```python
def gen_evens(n):                 # a function with `yield` is a generator: lazy, resumable, O(1) memory
    for i in range(0, n, 2):
        yield i
g = gen_evens(10); next(g); list(g)         # 0 then [2, 4, 6, 8]; exhausted afterwards

def inorder(node):                          # tree traversal as a generator → BST iterator (LC 173)
    if node:
        yield from inorder(node.left)
        yield node.val
        yield from inorder(node.right)

class Countdown:                            # iterator protocol: __iter__ returns self, __next__ raises StopIteration
    def __init__(self, n): self.n = n
    def __iter__(self): return self
    def __next__(self):
        if self.n <= 0: raise StopIteration
        self.n -= 1; return self.n + 1

sum(x for x in range(10 ** 7) if x % 3 == 0)   # generator expression: no 10^7-element list
```

Generators shine for streaming input (Week 3: reading huge CSVs line by line), iterators over trees, and `any(...)`/`all(...)` short-circuits. Iterating a generator twice yields nothing the second time — materialize with `list()` if needed.

---

## 12. String building

```python
parts = []
for ch in s:
    if ch.isalnum(): parts.append(ch.lower())
cleaned = "".join(parts)                                   # O(n)

"".join(c for c in s if c != " ")                          # generator into join is fine (join materializes internally)
"".join(map(str, nums)); ",".join(f"{k}={v}" for k, v in d.items())
chars = list(s); chars[i], chars[j] = chars[j], chars[i]; s = "".join(chars)   # "mutate" a string
s.split(); " ".join(reversed(s.split()))                   # Reverse Words in a String (151)
f"{count}{ch}"                                             # run-length token
str(n)[::-1]; int(str(n)[::-1])                            # digit tricks (check sign/overflow rules separately)
```

`io.StringIO` is the heavy-duty builder for tooling (Week 3). In interviews: list + join, always.

---

## 13. Custom sorting deep dive

```python
people = [("bob", 25), ("alice", 30), ("carol", 25)]
sorted(people, key=lambda p: p[1])                         # by age; stable → bob before carol
sorted(people, key=lambda p: (p[1], p[0]))                 # age asc, then name asc
sorted(people, key=lambda p: (-p[1], p[0]))                # age DESC, name asc — negate the numeric key
sorted(people, key=lambda p: p[0], reverse=True)           # reverse preserves stability of equal keys

# Descending on a STRING key + ascending on another: sort twice (stable), least-significant key first
by_name_desc = sorted(people, key=lambda p: p[0], reverse=True)
final = sorted(by_name_desc, key=lambda p: p[1])           # age asc, ties: name desc

from operator import itemgetter, attrgetter
sorted(people, key=itemgetter(1, 0)); sorted(objs, key=attrgetter("priority", "name"))

# Comparator when no key exists (pairwise-defined order)
from functools import cmp_to_key
sorted(strs, key=cmp_to_key(lambda a, b: -1 if a + b > b + a else (1 if a + b < b + a else 0)))

# Sort indices by value (argsort)
idx = sorted(range(len(nums)), key=nums.__getitem__)
# Sort a dict by value desc, key asc
sorted(freq.items(), key=lambda kv: (-kv[1], kv[0]))
# Sort by frequency then value (LC 1636)
sorted(nums, key=lambda x: (cnt[x], -x))
# Sort strings case-insensitively but deterministically
sorted(words, key=lambda w: (w.lower(), w))
# In-place vs copy
nums.sort(key=abs)           # None returned; nums modified
by_abs = sorted(nums, key=abs)
```

Why `key` beats `cmp`: the key is computed once per element (n calls) and comparisons are tuple comparisons in C; a `cmp` function is called O(n log n) times. Timsort is stable, so multi-pass sorts compose. Complexity: O(n log n) comparisons, O(n) extra memory (Timsort merges), plus O(n·k) for keys of size k.

---

## 14. Mini problem set

Do these in Python this week (some are re-dos from the DSA schedule; that is intended):

- [ ] Valid Anagram (242) — `Counter` and `[0]*26`, both
- [ ] Group Anagrams (49) — `defaultdict(list)` with tuple key
- [ ] Top K Frequent Elements (347) — `most_common`, heap, bucket sort; state each complexity
- [ ] Kth Largest Element in an Array (215) — size-k heap, then `nlargest`, then note quickselect
- [ ] K Closest Points to Origin (973) — max-heap by negation with tiebreak
- [ ] Merge k Sorted Lists (23) — heap of `(val, i, node)`
- [ ] Sliding Window Maximum (239) — monotonic `deque`
- [ ] Search Insert Position (35) and Find First and Last Position (34) — `bisect`, then by hand
- [ ] Longest Increasing Subsequence (300) — `bisect` O(n log n) and the O(n²) DP
- [ ] Time Based Key-Value Store (981) — `bisect_right`
- [ ] Letter Combinations of a Phone Number (17) — `product`, then backtracking
- [ ] Largest Number (179) — `cmp_to_key`
- [ ] Climbing Stairs (70) and Word Break (139) — `@cache` then tabulation
- [ ] LRU Cache (146) — `OrderedDict` version and dict + doubly linked list version
- [ ] Find Median from Data Stream (295) — two heaps
- [ ] Binary Search Tree Iterator (173) — generator version and explicit-stack version

---

## 15. Mastery checklist

- [ ] I choose `Counter` / `defaultdict` / plain `dict` deliberately and can say why
- [ ] I never write a BFS with `list.pop(0)`
- [ ] I can write a max-heap, a size-k heap, a k-way merge and a two-heap median without looking
- [ ] I know the difference between `bisect_left` and `bisect_right` on duplicates and can derive count/insert/LIS from them
- [ ] I can enumerate subsets/permutations/products with `itertools` *and* by hand
- [ ] I memoize with `@cache` on hashable arguments and can convert memo → tabulation
- [ ] I write `@dataclass` node/record classes with `field(default_factory=...)`, `frozen`, `order` correctly
- [ ] I fix heap tie-breaking three ways (`counter`, `__lt__`, `dataclass(order=True)`)
- [ ] I write multi-key sorts including mixed asc/desc, and know why `key` beats `cmp`
- [ ] I can write a generator-based inorder traversal and explain lazy evaluation
- [ ] All 16 mini problems solved in Python and explained out loud
