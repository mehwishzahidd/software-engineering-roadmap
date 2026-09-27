# 01 · Python core for interviews (Week 1)

> Goal: write correct, idiomatic Python from a blank file with no lookup for anything in this file.
> Time: 8–10 h in Week 1 alongside [`03-dsa/00-big-o.md`](../03-dsa/00-big-o.md) and [`03-dsa/01-arrays-strings.md`](../03-dsa/01-arrays-strings.md).
> Reference while practising: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md). Coming from Java: [`python-for-java-devs.md`](./python-for-java-devs.md).

**Contents:** [Setup](#0-setup) · [Types & numbers](#1-types-and-numbers) · [Strings](#2-strings) · [Lists & tuples](#3-lists-and-tuples) · [Sets & dicts](#4-sets-and-dicts) · [Control flow](#5-control-flow) · [Functions](#6-functions) · [Comprehensions](#7-comprehensions) · [Built-in iteration helpers](#8-built-in-iteration-helpers) · [Recursion](#9-recursion-basics) · [Exceptions](#10-exceptions) · [Modules](#11-modules-and-imports) · [Classes](#12-classes-basics) · [Break it](#13-break-it-experiments) · [Exercises](#14-exercises) · [Interview Q&A](#15-interview-qa) · [Checklist](#16-mastery-checklist)

---

## 0. Setup

```bash
python3 --version            # 3.12.x expected; install from python.org or your package manager
python3 -m venv .venv && source .venv/bin/activate     # (Windows: .venv\Scripts\activate)
python3 -i scratch.py        # run a file then drop into the REPL with its names loaded
python3 -c "print(7 // 2)"   # one-liner
```

Work in a throwaway folder `python-katas/` with one file per topic. Use the REPL constantly: hypothesis → run → observe. Everything in Week 1 is disposable — the projects start in Week 4.

---

## 1. Types and numbers

```python
type(3), type(3.0), type("3"), type(True), type(None)     # int float str bool NoneType
isinstance(True, int)          # True — bool is a subclass of int; True + True == 2
7 / 2, 7 // 2, 7 % 2, 7 ** 2   # 3.5, 3, 1, 49   (/ is ALWAYS float; // floors)
-7 // 2, -7 % 2                # -4, 1          (floor division; remainder takes the divisor's sign)
divmod(17, 5)                  # (3, 2)
int("42"), int("ff", 16), str(42), float("3.5"), int(3.9), round(2.5), round(3.5)   # 42 255 '42' 3.5 3 2 4 (banker's rounding!)
2 ** 200                       # arbitrary precision, no overflow, no Long/BigInteger
abs(-3), max(1, 5, 2), min([4, 2]), sum([1, 2, 3]), pow(2, 10, 1000)   # pow with modulus for modular exponent
float("inf") > 10 ** 100       # True; float('-inf'), math.inf
0.1 + 0.2 == 0.3               # False → never compare floats with ==; use math.isclose or integers
bool(0), bool(""), bool([]), bool(None), bool("0")    # False False False False True (non-empty string is truthy)
```

Interview notes: no `int` overflow means you never worry about `Integer.MAX_VALUE` — but when a problem says "assume 32-bit", mask with `& 0xFFFFFFFF` and handle the sign yourself (see cheatsheet §5.19). `round()` uses banker's rounding; use `decimal` for money (Week 3).

---

## 2. Strings

Immutable sequences of Unicode code points. Every "modifying" method returns a **new** string.

```python
s = "Hello, World"
len(s), s[0], s[-1], s[7:], s[:5], s[::-1], s[::2]        # 12 'H' 'd' 'World' 'Hello' 'dlroW ,olleH' 'Hlo ol'
s.lower(), s.upper(), s.title(), s.swapcase()
s.strip(), s.lstrip("H"), s.rstrip("d ")                   # strip whitespace / specific chars
s.split(", "), "a b  c".split(), "a,b,,c".split(",")       # ['Hello', 'World'] ['a','b','c'] ['a','b','','c']
",".join(["a", "b"]), "".join(reversed(s))
s.find("o"), s.rfind("o"), s.index("o"), s.count("l")      # 4 8 4 3; find returns -1, index raises ValueError
s.startswith("He"), s.endswith(("ld", "LD")), "World" in s
s.replace("l", "L", 1), s.zfill(15), s.center(20, "*")
"abc".isalpha(), "123".isdigit(), "a1".isalnum(), " ".isspace(), "Ab".islower()
ord("a"), chr(97), ord("z") - ord("a")                     # 97 'a' 25
f"{s!r} has {len(s)} chars; pi={3.14159:.2f}; {42:>6}|{42:<6}|{42:^6}|{255:#x}|{1234567:,}"
str(123) + "4", "ab" * 3, "ab" < "b", "Z" < "a"           # '1234' 'ababab' True True (ASCII order)
s == "Hello, World"                                        # value equality; never use `is` for strings (identity is undefined, and 3.8+ warns)
```

**Building strings:** never `s += ch` in a loop for large n. Collect into a list and `"".join`. Character frequency: `Counter(s)` or `[0]*26` with `ord(c) - ord('a')`.

**Palindrome / clean-up idiom:** `t = [c.lower() for c in s if c.isalnum()]; t == t[::-1]`.

Raw strings `r"\d+"` for regexes; multi-line with triple quotes; `"\n"` newline. Bytes vs str: `"é".encode()` → `b'\xc3\xa9'` — irrelevant in DSA, relevant in tooling.

---

## 3. Lists and tuples

### 3.1 `list` — a dynamic array of references

```python
a = [3, 1, 2]
a.append(4); a.extend([5, 6]); a += [7]      # O(1) amortized / O(k) / O(k)
a.insert(0, 0)                               # O(n) — shifts everything
a.pop(), a.pop(0), a.remove(3)               # O(1), O(n), O(n) (removes FIRST occurrence; ValueError if absent)
a.index(2), a.count(2), 2 in a               # O(n) each
a.sort(); a.sort(reverse=True); a.sort(key=abs)   # in place, returns None — `b = a.sort()` makes b None
sorted(a)                                    # new list, works on any iterable
a.reverse(); list(reversed(a)); a[::-1]      # in place / iterator / copy
a.copy(); a[:]; list(a)                      # shallow copies
[0] * 5; [None] * n; list(range(5))          # [0,0,0,0,0]; preallocate; [0,1,2,3,4]
[[0] * cols for _ in range(rows)]            # 2-D grid (see the [[0]*n]*m trap in 03-pitfalls)
a[1:3] = [9, 9, 9]                           # slice assignment can change length
del a[0]; del a[1:3]                         # O(n)
a[-1], a[-2]                                 # last, second-to-last
a.clear(); len(a)                            # [] 0
```

Lists compare lexicographically: `[1, 2] < [1, 3]`. `min([])` raises `ValueError`; use `min(xs, default=...)`. `a * 2` repeats; `a + b` concatenates (new list).

### 3.2 `tuple` — immutable, hashable, cheaper

```python
p = (1, 2); q = 1, 2; one = (1,)            # parentheses optional; trailing comma makes a 1-tuple
r, c = p                                     # unpacking
p[0], p[-1], len(p), p + (3,), p * 2, 2 in p # indexing/slicing like a list
d = {(0, 0): "origin"}; visited = {(r, c)}   # tuples as dict keys / set members — the standard for grid coordinates
t = ([1], 2); t[0].append(9)                 # tuple is immutable, its contents may not be; ([1, 9], 2)
hash((1, 2))                                 # ok; hash(([1], 2)) → TypeError: unhashable list inside
```

Use tuples for fixed-shape records `(dist, node)`, heap entries, dict keys and multiple return values. Use `namedtuple`/`dataclass` (Week 2) when fields need names.

### 3.3 Slicing (lists, tuples, strings)

```python
a = list(range(10))
a[2:5], a[:3], a[7:], a[-3:], a[::3], a[::-1], a[8:2:-2], a[100:]    # clamp, never IndexError → [2,3,4] ... []
b = a[2:5]; b[0] = 99; print(a[2])         # 2 — a slice is a NEW list: O(j-i) time and memory; a[:] is a full shallow copy
```

Passing `a[1:]` into recursion creates O(n) copies per level → O(n²). Pass an index instead.

---

## 4. Sets and dicts

Both are hash tables: O(1) average insert/lookup/delete, O(n) worst case (pathological collisions — mention it, never worry about it). Elements/keys must be **hashable** (immutable built-ins, tuples of hashables, frozenset, objects with `__hash__`).

### 4.1 `set`

```python
s = {1, 2, 3}; empty = set()                # {} is an EMPTY DICT, not a set
s.add(4); s.discard(9); s.remove(1)         # discard never raises; remove raises KeyError
3 in s, len(s)                              # O(1)
a = {1, 2, 3}; b = {3, 4}
a | b, a & b, a - b, a ^ b                  # union, intersection, difference, symmetric difference (new sets)
a |= b; a &= b                              # in-place variants (update / intersection_update)
a <= b, a < b, a.isdisjoint(b)              # subset, proper subset, no common elements
set("hello"), set([1, 1, 2])                # {'h','e','l','o'}, {1, 2} — dedupe
frozenset([1, 2])                           # hashable set → usable as a dict key / set member
s.pop()                                     # removes and returns an arbitrary element
```

Sets have **no order**; do not rely on iteration order. Sorting a set: `sorted(s)`.

### 4.2 `dict` — insertion-ordered hash map (guaranteed since 3.7)

```python
d = {"a": 1, "b": 2}; d = dict(a=1, b=2); d = dict(zip(["a", "b"], [1, 2])); d = dict.fromkeys("ab", 0)
d["c"] = 3; d["a"]                          # set; get — d["zzz"] would raise KeyError
d.get("zzz"), d.get("zzz", 0)               # None / default — no insertion
d.setdefault("list", []).append(1)          # insert default if absent, return the value
d.pop("a"), d.pop("zzz", None), d.popitem() # remove+return; default avoids KeyError; popitem is LIFO
"a" in d                                    # KEY membership, O(1); `1 in d.values()` is O(n)
for k in d: pass                            # keys, insertion order
for k, v in d.items(): pass                 # pairs
for v in d.values(): pass                   # values
d.keys() & other.keys()                     # key views support set operations
d.update({"x": 9}); merged = d | other      # merge (right side wins)
del d["b"]; d.clear(); len(d)
{k: v for k, v in d.items() if v > 1}       # dict comprehension
d[(r, c)] = 1                               # tuple keys for grids/pairs
sorted(d), sorted(d.items(), key=lambda kv: kv[1])   # keys sorted; pairs by value
max(d, key=d.get)                           # key with the largest value
list(d)[0], next(iter(d))                   # first inserted key: O(n) vs O(1)
```

Counting idiom without `Counter`: `freq[x] = freq.get(x, 0) + 1`. Grouping idiom: `groups.setdefault(key, []).append(item)`. Week 2 replaces both with `Counter`/`defaultdict`.

Do not mutate a dict/set while iterating over it (`RuntimeError: dictionary changed size during iteration`); iterate over `list(d)`.

---

## 5. Control flow

```python
if x < 0:   sign = -1
elif x == 0: sign = 0
else:        sign = 1
sign = -1 if x < 0 else (0 if x == 0 else 1)      # conditional expression

for i in range(5): ...              # 0..4
for i in range(2, 10, 3): ...       # 2, 5, 8
for i in range(n - 1, -1, -1): ...  # n-1 down to 0
for i, v in enumerate(a): ...
for a_i, b_i in zip(a, b): ...
while lo < hi: ...
for x in xs:
    if bad(x): break
    if skip(x): continue
else:                               # runs only if the loop did NOT break — handy for "search didn't find"
    print("no bad x")

match command.split():              # structural pattern matching (3.10+) — rarely needed in interviews
    case ["go", direction]: ...
    case ["quit"]: ...
    case _: ...

pass                                # placeholder statement
0 <= r < R and 0 <= c < C           # chained comparison — the grid bounds idiom
```

Truthiness: `0`, `0.0`, `""`, `[]`, `{}`, `set()`, `None` are falsy; everything else is truthy. `if not stack:` is the idiom for "stack is empty". `while q:` for "queue not empty". Beware `if node:` on a node whose `__len__` or `__bool__` you defined.

Short-circuit: `a and b` returns `a` if falsy else `b`; `a or b` returns `a` if truthy else `b`. Hence `tail.next = a or b` in merge-lists, `x = d.get(k) or default` (careful when a legitimate value is `0`/`""`).

---

## 6. Functions

```python
def area(w: float, h: float = 1.0) -> float:
    """Docstring: first line is the summary."""
    return w * h

area(2), area(2, 3), area(h=3, w=2)          # positional, positional, keyword

def f(*args, **kwargs):                      # args: tuple, kwargs: dict
    return len(args), sorted(kwargs)
f(1, 2, x=3)                                 # (2, ['x'])

def g(a, b, /, c, *, d):                     # a,b positional-only; d keyword-only
    ...
nums = [1, 2, 3]; print(*nums); f(**{"x": 1})   # unpack into a call

def outer():
    count = 0
    def inner():
        nonlocal count                       # rebind the enclosing variable; without it `count += 1` is UnboundLocalError
        count += 1
        return count
    return inner
counter = outer(); counter(); counter()      # 2 — a closure keeps `count` alive

square = lambda x: x * x                     # single expression; use def for anything longer
key = lambda p: (-p[1], p[0])                # the main use: sort/min/max keys

def no_return(): pass
print(no_return())                           # None — every function returns something

def multi(): return 1, 2                     # returns a tuple
a, b = multi()
```

Rules that matter in interviews:
- Arguments are passed by **object reference** ("pass by assignment"): mutating a list parameter affects the caller; reassigning the parameter name does not.
- Default values are evaluated **once at definition** → never use a mutable default (`def f(x, acc=[])`). Use `None` and create inside.
- Nested helper functions (`def dfs(...)` inside the solution) are idiomatic; they close over `nums`, `res`, `seen`. Reassigning an outer scalar needs `nonlocal`; appending to an outer list does not.
- Functions are objects: pass them as arguments (`key=len`), store in dicts (`ops = {"+": operator.add}`), return them.
- Recursion depth is limited (~1000) — see §9.

---

## 7. Comprehensions

```python
[x * 2 for x in nums]                                      # map
[x for x in nums if x % 2 == 0]                            # filter
[x if x > 0 else 0 for x in nums]                          # map with conditional expression (if/else goes BEFORE the for)
[(i, j) for i in range(3) for j in range(i)]               # nested loops, outer first: (1,0) (2,0) (2,1)
[c for row in grid for c in row]                           # flatten
{w: len(w) for w in words}                                 # dict
{w[0] for w in words}                                      # set
sum(x * x for x in nums)                                   # generator expression: lazy, no list built
any(x < 0 for x in nums); all(row[0] == 0 for row in grid) # short-circuit on the first True/False
max((len(w), w) for w in words)                            # tuple key trick: longest, tie → lexicographically largest
matrix_t = [list(col) for col in zip(*matrix)]             # transpose
```

Readability rule: one `for`, at most one `if`, no side effects. Anything more → a plain loop. Comprehensions are slightly faster than equivalent loops and avoid `append` calls, but the win is clarity, not speed.

---

## 8. Built-in iteration helpers

```python
enumerate(xs, start=1)              # (index, value) pairs
zip(a, b, c)                        # tuples until the SHORTEST is exhausted; zip(a, b, strict=True) raises on mismatch
zip(*pairs)                         # unzip: xs, ys = zip(*pairs)
sorted(xs, key=..., reverse=...)    # stable Timsort, new list
reversed(xs)                        # iterator (no copy); works on lists, tuples, strings, ranges — not sets/dicts
any(it), all(it)                    # short-circuit; all([]) is True, any([]) is False
min(it, key=..., default=...), max(...)
sum(it, start=0)                    # sum of strings is a TypeError on purpose: use "".join
len(x)                              # O(1) for built-ins
range(n)                            # lazy, O(1) memory; supports `in`, len, indexing, slicing
map(f, xs), filter(pred, xs)        # lazy; comprehensions are usually clearer
iter(xs), next(it, default)         # manual iteration
list(...) / tuple(...) / set(...)   # materialize
```

`enumerate` + `zip` + `sorted` with a key cover 90 % of "loop with index / pair up / order by" needs. Say "I'll enumerate to keep the original indices" — it signals fluency.

---

## 9. Recursion basics

```python
def fact(n: int) -> int:
    if n <= 1:                     # base case FIRST
        return 1
    return n * fact(n - 1)         # progress toward the base case

def sum_list(a: list[int], i: int = 0) -> int:      # index instead of slicing — O(n), not O(n²)
    return 0 if i == len(a) else a[i] + sum_list(a, i + 1)

def reverse_str(s: str) -> str:
    return s if len(s) <= 1 else reverse_str(s[1:]) + s[0]      # O(n²) because of slicing — fine for n ≤ 1000, say so

def fib(n, memo={}):               # DON'T use a mutable default as a cache in interviews — use @cache (Week 2)
    ...
```

Facts to say out loud: each call adds a stack frame; CPython's default recursion limit is 1000 (`sys.getrecursionlimit()`); `sys.setrecursionlimit(10**6)` lifts the guard but very deep recursion can still crash the interpreter, so a 10^5-deep DFS should be iterative. Recursion depth = space complexity O(depth). Tail-call optimization does **not** exist in Python.

Week 8 ([`03-dsa/09-recursion.md`](../03-dsa/09-recursion.md)) goes deep; this week only: base case, recursive case, trust the recursion, trace small inputs.

---

## 10. Exceptions

```python
try:
    v = int(text)
except ValueError:                       # specific first
    v = 0
except (TypeError, KeyError) as e:       # several at once; `e` is the exception object
    print(type(e).__name__, e)
else:                                    # runs if no exception was raised
    print("parsed", v)
finally:                                 # always runs (cleanup)
    print("done")

raise ValueError(f"bad input: {text!r}")
raise                                    # re-raise the current exception inside an except block
raise RuntimeError("wrapped") from e     # chain the cause

class InsufficientStock(Exception):      # custom exception: subclass Exception, that's all
    def __init__(self, sku: str, needed: int):
        super().__init__(f"{sku}: need {needed}")
        self.sku, self.needed = sku, needed
```

Hierarchy to know: `BaseException` → `Exception` → `ValueError`, `TypeError`, `KeyError`, `IndexError`, `ZeroDivisionError`, `RecursionError`, `StopIteration`, `AttributeError`. `KeyboardInterrupt` and `SystemExit` derive from `BaseException` — that is why `except Exception:` does not swallow Ctrl-C.

EAFP ("easier to ask forgiveness than permission") is idiomatic: `try: return d[k] except KeyError: ...` versus Java's check-first style. In DSA code you rarely raise; in tooling (Week 3) you validate input and raise early. Never `except:` bare.

`with open(path) as f:` is a context manager: the file closes even if an exception occurs (try/finally in disguise).

---

## 11. Modules and imports

```python
import math; math.gcd(12, 18)
from collections import Counter, deque
import numpy as np                       # alias (not needed for interviews)
from heapq import heappush as push       # rename
```

A file `utils.py` is a module `utils`; a folder with `__init__.py` is a package. `python -m package.module` runs a module as a script; `if __name__ == "__main__":` guards script-only code so imports do not execute it. Absolute imports (`from tools.generator import make_order`) beat relative ones in project tooling. The interpreter searches `sys.path` (script dir, `PYTHONPATH`, site-packages). Circular imports fail at import time — restructure, don't hack.

Interview-relevant standard modules: `sys` (stdin, recursion limit), `math`, `collections`, `heapq`, `bisect`, `itertools`, `functools`, `random`, `string` (`string.ascii_lowercase`), `re` (rarely), `typing`, `dataclasses`.

---

## 12. Classes basics

```python
class Node:
    def __init__(self, val: int, next: "Node | None" = None):   # constructor; `self` is explicit
        self.val = val
        self.next = next

    def __repr__(self) -> str:                    # shown in the REPL/debugger — write it for every node class
        return f"Node({self.val})"

class Stack:
    def __init__(self):
        self._items: list[int] = []               # single underscore = "private by convention"

    def push(self, x: int) -> None: self._items.append(x)
    def pop(self) -> int: return self._items.pop()
    def peek(self) -> int: return self._items[-1]
    def __len__(self) -> int: return len(self._items)     # enables len(stack) and truthiness
    def __bool__(self) -> bool: return bool(self._items)

class Point:
    __slots__ = ("x", "y")                        # optional: fixed attributes, less memory
    def __init__(self, x, y): self.x, self.y = x, y
    def __eq__(self, o): return isinstance(o, Point) and (self.x, self.y) == (o.x, o.y)
    def __hash__(self): return hash((self.x, self.y))     # define BOTH __eq__ and __hash__ to use as dict key
    def __lt__(self, o): return (self.x, self.y) < (o.x, o.y)   # enables sorting and heapq

class Animal:
    def speak(self): raise NotImplementedError
class Dog(Animal):
    def speak(self): return "woof"                # override; super().method() calls the parent
```

No interfaces are needed: **duck typing** — anything with `.speak()` works. Class attributes (defined in the class body) are shared across instances; instance attributes (set on `self`) are per-object — a mutable class attribute list is the class version of the mutable-default trap. `@staticmethod`/`@classmethod`/`@property` exist; in interviews you need `__init__`, `__repr__`, `__eq__`/`__hash__`, `__lt__`, and `__len__` at most. Week 2 introduces `@dataclass` which writes most of these for you.

---

## 13. Break it experiments

Predict the output **before** running each one. Write your prediction, run, then explain the difference.

```python
# 1  Shared default
def add(x, acc=[]):
    acc.append(x); return acc
print(add(1)); print(add(2))                 # [1] then [1, 2]  — same list object

# 2  Aliasing
a = [1, 2, 3]; b = a; b.append(4); print(a)  # [1, 2, 3, 4]

# 3  Grid trap
g = [[0] * 3] * 2; g[0][0] = 1; print(g)     # [[1, 0, 0], [1, 0, 0]]

# 4  sort returns None
xs = [3, 1]; ys = xs.sort(); print(ys, xs)   # None [1, 3]

# 5  Integer division and modulo with negatives
print(-7 // 2, -7 % 2, int(-7 / 2))          # -4 1 -3

# 6  Truthiness and `or`
print(0 or "default", "" or None, [] or [1]) # default None [1]

# 7  Loop variable leaks (for-loops only; comprehensions don't leak) and late binding
fs = []
for i in range(3): fs.append(lambda: i)
print([f() for f in fs], i)                  # [2, 2, 2] 2 — all closures see the final i; i survives the loop

# 8  Mutating while iterating
s = {1, 2, 3}
try:
    for x in s: s.remove(x)
except RuntimeError as e: print("RuntimeError:", e)

# 9  is vs ==
a = 1000; b = 1000; print(a == b, a is b)    # True, implementation-dependent (often False in a REPL) — never use `is` here
print(None is None, [] == [], [] is [])      # True True False

# 10 String immutability
s = "abc"
try: s[0] = "z"
except TypeError as e: print("TypeError:", e)

# 11 Recursion limit
import sys
def depth(n): return 0 if n == 0 else 1 + depth(n - 1)
try: depth(5000)
except RecursionError: print("RecursionError at default limit", sys.getrecursionlimit())

# 12 Tuple with a mutable inside
t = ([], 0); t[0].append(1); print(t)        # ([1], 0)
try: t[1] = 5
except TypeError as e: print("TypeError:", e)
```

---

## 14. Exercises

Full graded list with acceptance criteria in [`exercises.md`](./exercises.md) (W1 block). Minimum for this file:

- [ ] **Char frequency** without `Counter`: dict + `get`, then `[0]*26` with `ord`; return the top-3 as a sorted list of `(char, count)` by count desc, char asc.
- [ ] **Slicing drill**: given `a = list(range(20))`, produce evens, odds reversed, last 5, middle third, rotate left by k using only slicing (`a[k:] + a[:k]`).
- [ ] **Anagram groups** with a dict keyed by `tuple(sorted(w))` and then by a 26-count tuple; explain why a list can't be a key.
- [ ] **Matrix ops**: transpose with `zip(*m)`, rotate 90° clockwise, spiral order — each with explicit index loops too.
- [ ] **Closure counter** and a `make_adder(n)` factory; explain `nonlocal`.
- [ ] **Recursive** power, sum of digits, reverse a list in place (indices), flatten a nested list, binary representation.
- [ ] **Custom exception** `InsufficientStock` in a tiny `Inventory` class with `reserve(sku, qty)`; test that it raises.
- [ ] **Stack and Queue classes** wrapping `list` and `deque`; write `__len__`, `__repr__`, `__bool__`.
- [ ] Re-do all Week 1 DSA problems (Big-O + Arrays, 6 problems) using only this file's tools; explain each built-in's complexity out loud.

---

## 15. Interview Q&A

<details><summary>What is the difference between a list and a tuple, and when do you use each?</summary>

Both are ordered sequences with O(1) indexing. A list is mutable and resizable (dynamic array); a tuple is immutable, slightly smaller, and hashable when its elements are — so it can be a dict key or set member. Use tuples for fixed-shape records (coordinates `(r, c)`, heap entries `(dist, node)`, multiple return values) and lists for collections that grow or change.
</details>

<details><summary>Why is `x in my_list` slow and what do you use instead?</summary>

`in` on a list is a linear scan, O(n). Inside a loop that becomes O(n²). Convert to a `set` (O(n) once) and membership becomes O(1) average because it is a hash table. If order and duplicates matter you keep the list and add a parallel set.
</details>

<details><summary>How are arguments passed in Python?</summary>

By object reference (call by sharing). The parameter name is bound to the same object the caller passed. Mutating that object (`lst.append`) is visible to the caller; rebinding the name (`lst = []`) is not. There is no copy on call, and no pointer to the caller's variable.
</details>

<details><summary>What does `sorted` guarantee and what is its complexity?</summary>

Timsort: O(n log n) worst case, O(n) on data with existing runs, stable (equal keys keep input order), `key` called once per element. `list.sort()` is in place and returns `None`; `sorted()` returns a new list from any iterable.
</details>

<details><summary>Explain `//` and `%` with negative numbers.</summary>

`//` floors toward negative infinity: `-7 // 2 == -4`. `%` returns a result with the sign of the divisor and satisfies `a == (a // b) * b + a % b`: `-7 % 2 == 1`. Java truncates toward zero and the remainder takes the dividend's sign (`-7 / 2 == -3`, `-7 % 2 == -1`). This matters for circular indexing: `(i - 1) % n` is always in range in Python.
</details>

<details><summary>What is a closure and when does Python need `nonlocal`?</summary>

A closure is an inner function that references variables from an enclosing scope; those variables stay alive after the outer function returns. Reading them needs nothing special; mutating a mutable object (list, dict) needs nothing special; **rebinding** the name (`best = ...`, `count += 1`) makes Python treat it as a new local, so you must declare `nonlocal name` first. `global` does the same for module-level names.
</details>

<details><summary>Why does `[[0] * n] * m` break, and what is the correct way?</summary>

`[row] * m` repeats the same list object m times, so all rows alias one list; writing `g[0][0] = 1` changes column 0 of every row. Use a comprehension `[[0] * n for _ in range(m)]`, which evaluates `[0] * n` m times and creates m distinct lists. `[0] * n` itself is fine because ints are immutable.
</details>

<details><summary>What is the difference between `is` and `==`?</summary>

`==` calls `__eq__` and compares values. `is` compares identity (same object in memory). Use `is` only for singletons: `x is None`, `x is True`. Small ints and interned strings may be identical by accident, which makes `is` on values a bug that hides in tests and appears in production.
</details>

<details><summary>What happens on deep recursion in Python?</summary>

Each call pushes a frame; past `sys.getrecursionlimit()` (default 1000) a `RecursionError` is raised. You can raise the limit with `sys.setrecursionlimit`, but extreme depth can overflow the C stack and crash the process, and there is no tail-call optimization. For depth ~10^5 (long linked lists, grid DFS on 1000×1000) write the iterative version with an explicit stack.
</details>

<details><summary>How do you make a class usable as a dict key or in a set?</summary>

Define `__eq__` and `__hash__` consistently: objects that compare equal must have equal hashes. Hash a tuple of the identifying fields. Defining `__eq__` alone sets `__hash__` to `None` (unhashable). `@dataclass(frozen=True)` generates both.
</details>

<details><summary>Why prefer `"".join(parts)` over `+=` for strings?</summary>

Strings are immutable, so each `+=` allocates a new string and copies both operands: n appends cost O(n²) in the worst case. `join` computes the total length once and copies each part once: O(n). CPython has an optimization for `+=` on a uniquely referenced string, but it is not guaranteed and interviewers expect `join`.
</details>

---

## 16. Mastery checklist

- [ ] I can write a list/dict/set comprehension and a generator expression without thinking about syntax
- [ ] I know the Big-O of every list/dict/set/str operation I use, and say it while coding
- [ ] I never use `in` on a list inside a loop, `pop(0)` on a list, or `+=` on a string in a loop
- [ ] I can explain aliasing vs copying and the `[[0]*n]*m` trap with a demo
- [ ] I use `enumerate`, `zip`, `sorted(key=...)`, `min/max(key=...)` fluently, including multi-key sorts
- [ ] I can write a closure, explain `nonlocal`, and write a nested `dfs` helper inside a solution
- [ ] I can write a recursive function with a correct base case and state its depth/space cost
- [ ] I handle exceptions with specific `except` clauses and can write a custom exception
- [ ] I can write a node class with `__init__`, `__repr__`, and `__eq__`/`__hash__`/`__lt__` when needed
- [ ] I solved the Week 1 DSA set (Big-O + Arrays) in Python and explained each solution out loud
- [ ] All 12 "Break it" experiments predicted correctly (or the mistakes written down in my notes)
