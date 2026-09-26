# Python for Java Developers

> Everything here is phrased as "you know X in Java; in Python it's Y". Type every example
> into `python3` (the REPL) or a file. Python 3.11+.

Related: [README.md](./README.md) · [exercises.md](./exercises.md) · Java reference: [../01-java/README.md](../01-java/README.md)

---

## 1. The big differences first

| Topic | Java | Python |
|---|---|---|
| Typing | Static, compiled; types checked by `javac` | **Dynamic**, types checked at runtime; optional hints checked by tools (mypy/pyright) |
| Blocks | `{ }` | **Indentation** (4 spaces) is syntax |
| Execution | Compile to bytecode, JIT on JVM | CPython compiles to bytecode and **interprets** (no JIT by default) → ~10–50× slower on tight loops |
| Entry point | `public static void main` | Top-level code runs on import; guard with `if __name__ == "__main__":` |
| Everything is… | Primitives + objects | **Objects** (ints too), arbitrary-precision `int` (no overflow!) |
| Null | `null` | `None` |
| Booleans | `true`/`false`, `&&` `\|\|` `!` | `True`/`False`, `and` `or` `not` |
| Interfaces | `interface`, explicit `implements` | **Duck typing** ("if it has `.read()`, it's a reader"); `typing.Protocol` / `abc.ABC` when you want structure |
| Access control | `private`/`protected`/`public` | Convention only: `_internal`, `__mangled` |
| Concurrency | Real parallel threads | **GIL**: one thread runs Python bytecode at a time (CPython) → threads for I/O, `multiprocessing` for CPU |
| Packaging | Maven, `pom.xml`, `~/.m2` | `pip` + **venv** per project, `requirements.txt` / `pyproject.toml` |
| Tests | JUnit 5 | pytest |

---

## 2. Syntax mapping table

| Java | Python |
|---|---|
| `int x = 5;` | `x = 5` |
| `final double RATE = 0.2;` | `RATE = 0.2` (convention: UPPER_CASE means constant) |
| `String s = "hi";` | `s = "hi"` or `'hi'` |
| `"Total: " + total` / `String.format` | `f"Total: {total}"`, `f"{price:.2f}"`, `f"{n:>5}"` |
| `s.length()` | `len(s)` |
| `s.charAt(i)` | `s[i]`; last char `s[-1]` |
| `s.substring(1, 4)` | `s[1:4]`; reverse `s[::-1]` |
| `s.equals(t)` | `s == t` (`==` is value equality; `is` is identity) |
| `s.toLowerCase()`, `s.strip()`, `s.split(",")` | `s.lower()`, `s.strip()`, `s.split(",")` (keeps trailing empties) |
| `String.join(",", list)` | `",".join(items)` |
| `sb.append(x)` in a loop | `parts.append(x)` then `"".join(parts)` |
| `7 / 2` → 3 | `7 // 2` → 3 (floor division); `7 / 2` → 3.5 |
| `-7 / 2` → -3 | `-7 // 2` → **-4** (floors, not truncates); `int(-7 / 2)` → -3 |
| `-7 % 3` → -1 | `-7 % 3` → **2** (sign follows divisor) |
| `Math.pow(2, 10)` | `2 ** 10` |
| `Integer.MAX_VALUE` | no limit; use `float("inf")` / `math.inf` for sentinels |
| `if (a && !b) {} else if (c) {} else {}` | `if a and not b: ... elif c: ... else: ...` |
| `cond ? a : b` | `a if cond else b` |
| `for (int i = 0; i < n; i++)` | `for i in range(n):` |
| `for (int i = n - 1; i >= 0; i--)` | `for i in range(n - 1, -1, -1):` or `reversed(range(n))` |
| `for (String s : list)` | `for s in items:` |
| index + value | `for i, s in enumerate(items):` |
| parallel iteration | `for a, b in zip(xs, ys):` |
| `while (x > 0) { x--; }` | `while x > 0: x -= 1` (no `++`/`--`) |
| `switch` | `match value: case 1: ... case _: ...` (3.10+) or `if/elif` |
| `void f(int a, int b)` | `def f(a: int, b: int) -> None:` |
| overloading | default & keyword args: `def f(a, b=0, *, verbose=False)` |
| varargs `int... xs` | `*args`, and `**kwargs` for named |
| lambda `x -> x * 2` | `lambda x: x * 2` (single expression only) |
| `throw new IllegalArgumentException("x")` | `raise ValueError("x")` |
| `try {} catch (E e) {} finally {}` | `try: ... except E as e: ... else: ... finally: ...` |
| try-with-resources | `with open(path) as f:` |
| `System.out.println(x)` | `print(x)` |
| `// comment`, `/** doc */` | `# comment`, `"""docstring"""` |
| `import java.util.*;` | `import collections` / `from collections import Counter` |

Truthiness: `0`, `0.0`, `""`, `[]`, `{}`, `set()`, `None` are falsy. `if items:` means "non-empty".
Chained comparisons: `if 0 <= i < n:`.

---

## 3. Collections

| Java | Python | Notes |
|---|---|---|
| `ArrayList<T>` | `list` — `[1, 2, 3]` | dynamic array; `append` amortized O(1), `pop()` O(1), `pop(0)`/`insert(0, x)` **O(n)** |
| `HashMap<K,V>` | `dict` — `{"a": 1}` | insertion-ordered (3.7+) — like `LinkedHashMap` |
| `HashSet<T>` | `set` — `{1, 2}`; empty set is `set()` (`{}` is a dict!) | |
| immutable tuple / record key | `tuple` — `(r, c)` | hashable if elements are; use as dict/set keys |
| `ArrayDeque` | `collections.deque` | O(1) both ends: `append`, `appendleft`, `pop`, `popleft` |
| `PriorityQueue` | `heapq` on a list | **min-heap** only |
| `TreeMap` | no built-in; `sorted(d)` or `bisect` on a sorted list | (third-party `sortedcontainers`) |
| `map.merge(k, 1, Integer::sum)` | `Counter` or `d[k] = d.get(k, 0) + 1` | |
| `computeIfAbsent(k, x -> new ArrayList<>())` | `defaultdict(list)` | |

```python
nums = [5, 3, 8]
nums.append(1); nums.extend([9, 9]); nums.pop(); nums.sort(); nums.sort(reverse=True)
sorted_copy = sorted(nums)                    # new list; nums.sort() sorts in place and returns None
nums[0], nums[-1], nums[1:3], nums[::-1]      # indexing, slicing (copies)
3 in nums                                     # O(n) for lists, O(1) for sets/dicts
grid = [[0] * cols for _ in range(rows)]      # ✅ NOT [[0] * cols] * rows (same row object repeated!)

ages = {"ana": 31, "bo": 27}
ages["cy"] = 40
ages.get("dee", 0)                            # default instead of KeyError
for name, age in ages.items(): ...
del ages["bo"]
"ana" in ages                                 # key membership

seen = set(); seen.add(3); seen |= {4, 5}; a & b; a - b   # union/intersection/difference

point = (3, 4); x, y = point                  # tuple unpacking
a, b = b, a                                    # swap (no temp)
first, *rest = [1, 2, 3]                      # first=1, rest=[2, 3]
```

### Comprehensions (Python's streams)

```python
squares = [x * x for x in range(10)]
evens = [x for x in nums if x % 2 == 0]
by_id = {u["id"]: u for u in users}                       # dict comprehension (toMap)
merchants = {tx["merchant"] for tx in txs}                 # set comprehension
total = sum(tx["amount"] for tx in txs if tx["amount"] < 0)   # generator expression: lazy, no list built
any(x < 0 for x in nums); all(...); max(txs, key=lambda t: t["amount"]); min(..., default=None)
```

### `collections` and `heapq`

```python
from collections import Counter, defaultdict, deque
import heapq

Counter("mississippi").most_common(2)          # [('i', 4), ('s', 4)]
Counter(a) == Counter(b)                        # anagram check

groups = defaultdict(list)
for w in words:
    groups["".join(sorted(w))].append(w)       # group anagrams

q = deque([(0, 0)]); q.append((0, 1)); r, c = q.popleft()   # BFS queue

heap = []
heapq.heappush(heap, (dist, node))             # tuples compare element by element
d, node = heapq.heappop(heap)
heapq.heapify(nums)                             # O(n) in place
heapq.nlargest(3, nums)                         # top-k
heapq.heappush(max_heap, -x)                    # max-heap trick: negate
```

---

## 4. Functions

```python
def summarize(txs: list[dict], *, month: str | None = None, currency: str = "EUR") -> dict[str, float]:
    """Sum amounts per category. Keyword-only args after `*`."""
    totals: dict[str, float] = {}
    for tx in txs:
        if month and not tx["date"].startswith(month):
            continue
        totals[tx["category"]] = totals.get(tx["category"], 0.0) + tx["amount"]
    return totals

summarize(txs, month="2025-03")
```

- **Mutable default argument trap:** `def f(items=[])` — the list is created **once** at definition and shared across calls. Use `items=None` then `items = items or []` (or `if items is None: items = []`).
- Functions are objects: pass them, return them, store them in dicts (`handlers = {"add": add_cmd}`).
- Closures capture variables; to rebind an outer variable use `nonlocal` (useful in recursive DFS helpers).
- Parameter passing = "pass by object reference" — same semantics as Java's pass-by-value-of-reference: mutating a passed list is visible; rebinding isn't.

---

## 5. Classes and dataclasses

```python
class Account:
    interest_rate = 0.01                              # class attribute (like static)

    def __init__(self, owner: str, balance: int = 0) -> None:   # constructor
        if balance < 0:
            raise ValueError("negative opening balance")
        self.owner = owner                            # instance attributes created by assignment
        self._balance = balance                       # "_" = internal by convention

    @property
    def balance(self) -> int:                         # read-only property: acct.balance
        return self._balance

    def deposit(self, cents: int) -> None:            # explicit `self` (Java's implicit `this`)
        if cents <= 0:
            raise ValueError("deposit must be positive")
        self._balance += cents

    def __repr__(self) -> str:                        # toString (debug form)
        return f"Account(owner={self.owner!r}, balance={self._balance})"

class SavingsAccount(Account):                        # inheritance
    def deposit(self, cents: int) -> None:
        super().deposit(cents)
```

Dunder methods ↔ Java: `__eq__`/`__hash__` ↔ `equals`/`hashCode`; `__lt__` ↔ `compareTo`; `__str__`/`__repr__` ↔ `toString`; `__len__`, `__iter__`, `__contains__`, `__enter__`/`__exit__` ↔ `AutoCloseable`.

### Dataclasses ≈ records

```python
from dataclasses import dataclass, field
from datetime import date
from decimal import Decimal

@dataclass(frozen=True)                   # frozen → immutable + hashable, like a record
class Transaction:
    date: date
    merchant: str
    amount: Decimal                        # Decimal, not float, for money (same reason as BigDecimal)
    tags: tuple[str, ...] = ()

    def __post_init__(self):               # like a compact constructor
        if not self.merchant:
            raise ValueError("merchant is empty")

@dataclass
class Budget:
    category: str
    limit: Decimal
    alerts: list[str] = field(default_factory=list)   # never `= []`

tx = Transaction(date(2025, 3, 1), "Coffee", Decimal("-3.50"))
tx == Transaction(date(2025, 3, 1), "Coffee", Decimal("-3.50"))   # True — generated __eq__
```

---

## 6. Exceptions

```python
class CsvFormatError(ValueError):
    def __init__(self, line: int, reason: str):
        super().__init__(f"line {line}: {reason}")
        self.line = line

try:
    amount = Decimal(raw)
except InvalidOperation as e:               # from decimal import InvalidOperation
    raise CsvFormatError(line_no, f"bad amount {raw!r}") from e   # `from e` = Java's cause
else:
    ok.append(amount)                        # runs only if no exception
finally:
    ...
```
All exceptions are unchecked. Common built-ins: `ValueError` (≈ IllegalArgumentException),
`TypeError`, `KeyError` (missing dict key), `IndexError`, `FileNotFoundError`, `RuntimeError`.
Catch specific ones; never a bare `except:`.

---

## 7. Files, JSON, CSV, pathlib

```python
from pathlib import Path
import csv, json

data_dir = Path("data")
data_dir.mkdir(parents=True, exist_ok=True)
path = data_dir / "transactions.csv"            # `/` joins paths

path.write_text("date,merchant,amount\n2025-03-01,Coffee,-3.50\n", encoding="utf-8")
text = path.read_text(encoding="utf-8")

with path.open(newline="", encoding="utf-8") as f:     # `with` closes the file (try-with-resources)
    for row in csv.DictReader(f):                        # handles quoted fields correctly
        print(row["merchant"], row["amount"])

with (data_dir / "clean.csv").open("w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=["date", "merchant", "amount"])
    w.writeheader()
    w.writerow({"date": "2025-03-01", "merchant": "Coffee", "amount": "-3.50"})

cfg = json.loads('{"port": 8080, "debug": false}')      # str -> dict
print(json.dumps(cfg, indent=2))                         # dict -> str
with open("cfg.json", "w", encoding="utf-8") as f:
    json.dump(cfg, f, indent=2)

for p in data_dir.glob("*.csv"): print(p.name, p.stat().st_size)
for p in Path(".").rglob("*.java"): ...                  # recursive
```

---

## 8. Typing (type hints)

```python
from typing import Iterable, Protocol

def top_merchants(txs: Iterable[Transaction], n: int = 3) -> list[tuple[str, int]]: ...
maybe: str | None = None                  # Optional[str]
Grid = list[list[int]]                    # type alias

class Categorizer(Protocol):              # structural interface: anything with this method matches
    def categorize(self, tx: Transaction) -> str | None: ...
```
Hints are **not enforced at runtime**. Run `pip install mypy && mypy src/` to check them
statically — the closest you'll get to `javac`'s safety net.

---

## 9. pytest

```python
# test_money.py        (files test_*.py, functions test_*)
import pytest
from decimal import Decimal
from ledger import parse_amount

def test_parses_negative():
    assert parse_amount("-3.50") == Decimal("-3.50")          # plain assert, rich diff on failure

@pytest.mark.parametrize("raw", ["", "abc", "1,2,3"])
def test_rejects_garbage(raw):
    with pytest.raises(ValueError):
        parse_amount(raw)

@pytest.fixture
def sample_csv(tmp_path):                                      # tmp_path ≈ JUnit @TempDir
    p = tmp_path / "t.csv"
    p.write_text("date,merchant,amount\n2025-03-01,Coffee,-3.50\n")
    return p

def test_import(sample_csv):
    assert len(import_file(sample_csv)) == 1
```
Run: `pytest -q`, `pytest -k rejects`, `pytest -x` (stop at first failure).
Mapping: `@Test` → `test_` function; `assertEquals` → `assert a == b`; `assertThrows` → `pytest.raises`; `@ParameterizedTest` → `parametrize`; `@BeforeEach` → fixtures.

---

## 10. Scripting toolkit

```python
#!/usr/bin/env python3
"""Usage: python smoke.py --base-url http://localhost:8080 --timeout 5"""
import argparse, subprocess, sys
from pathlib import Path
import requests

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Smoke-test an API")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--timeout", type=float, default=5.0)
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)

    # requests: ALWAYS set a timeout (default is wait forever)
    r = requests.get(f"{args.base_url}/actuator/health", timeout=args.timeout)
    if r.status_code != 200 or r.json().get("status") != "UP":
        print(f"health check failed: {r.status_code} {r.text[:200]}", file=sys.stderr)
        return 1

    # subprocess: run a command, capture output, fail loudly on non-zero exit
    out = subprocess.run(["git", "rev-parse", "--short", "HEAD"],
                         capture_output=True, text=True, check=True)
    print(f"OK at commit {out.stdout.strip()}")
    return 0

if __name__ == "__main__":
    sys.exit(main())
```

- `requests.post(url, json=payload, headers={"Authorization": f"Bearer {token}"}, timeout=5)`; `r.raise_for_status()` raises on 4xx/5xx.
- `subprocess.run([...])` with a **list** of args (no shell injection). Avoid `shell=True` with user input.
- Exit codes matter for CI: `sys.exit(0)` success, non-zero failure.
- `os.environ.get("API_TOKEN")` for secrets, never hard-code.
- Logging: `import logging; logging.basicConfig(level=logging.INFO); log = logging.getLogger(__name__)`.

---

## 11. DSA in Python — idioms

```python
# Two Sum (LeetCode 1)
def two_sum(nums: list[int], target: int) -> list[int]:
    seen: dict[int, int] = {}
    for i, x in enumerate(nums):
        if target - x in seen:
            return [seen[target - x], i]
        seen[x] = i
    return []

# Sliding window: longest substring without repeating characters (LeetCode 3)
def length_of_longest_substring(s: str) -> int:
    last: dict[str, int] = {}
    best = left = 0
    for right, ch in enumerate(s):
        if last.get(ch, -1) >= left:
            left = last[ch] + 1
        last[ch] = right
        best = max(best, right - left + 1)
    return best

# BFS on a grid
from collections import deque
def shortest_path(grid: list[list[int]]) -> int:
    rows, cols = len(grid), len(grid[0])
    q, seen = deque([(0, 0, 0)]), {(0, 0)}
    while q:
        r, c, d = q.popleft()
        if (r, c) == (rows - 1, cols - 1):
            return d
        for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nr, nc = r + dr, c + dc
            if 0 <= nr < rows and 0 <= nc < cols and grid[nr][nc] == 0 and (nr, nc) not in seen:
                seen.add((nr, nc))
                q.append((nr, nc, d + 1))
    return -1

# Memoized recursion (1-D DP)
from functools import cache
@cache
def climb(n: int) -> int:
    return n if n <= 2 else climb(n - 1) + climb(n - 2)

# Binary search with the standard library
import bisect
i = bisect.bisect_left(sorted_nums, target)   # first index with value >= target

# Sorting with keys (Comparator.comparing(...).thenComparing(...))
people.sort(key=lambda p: (p.dept, -p.salary, p.name))
```

| Java habit | Python idiom |
|---|---|
| `int[] count = new int[26]; count[c - 'a']++` | `Counter(s)` or `ord(c) - ord('a')` |
| `Integer.MAX_VALUE` sentinel | `math.inf` |
| `Arrays.fill(dp, -1)` | `dp = [-1] * n` |
| Stack via `ArrayDeque` | plain `list`: `append` / `pop` / `stack[-1]` |
| Queue via `ArrayDeque` | `deque` (never `list.pop(0)` — O(n)) |
| Max-heap | push negatives |
| Deep recursion | `sys.setrecursionlimit(10**6)` or iterate — default limit is 1000 |

Performance note: Python loops are slow; an O(n) solution in Python is fine for LeetCode limits,
but O(n²) with n = 10⁵ will time out sooner than in Java.

---

## 12. Interview questions

<details><summary>1. What are the main differences between Python and Java?</summary>

Dynamic vs static typing; interpreted (CPython) vs JIT-compiled JVM; indentation-based syntax;
duck typing vs nominal interfaces; arbitrary-precision ints; the GIL limits CPU parallelism in
threads; venv/pip vs Maven. Python is faster to write, Java faster to run and safer to refactor at scale.
</details>

<details><summary>2. List vs tuple?</summary>

List is mutable, tuple immutable. Tuples are hashable (if their elements are), so they can be dict
keys/set elements — e.g. `(row, col)` coordinates.
</details>

<details><summary>3. What is the GIL?</summary>

CPython's Global Interpreter Lock lets only one thread execute Python bytecode at a time. Threads
still help for I/O-bound work (the GIL is released during I/O); for CPU-bound parallelism use
`multiprocessing` or native extensions. (Free-threaded builds are experimental in 3.13.)
</details>

<details><summary>4. What is the mutable default argument problem?</summary>

Default values are evaluated once when the function is defined, so `def f(x=[])` shares one list
across calls. Use `None` as the default and create the list inside.
</details>

<details><summary>5. What's a virtual environment and why use one?</summary>

An isolated Python installation per project with its own packages, avoiding version conflicts
between projects — conceptually like each Maven project having its own resolved dependency set.
</details>

<details><summary>6. How do you handle money in Python?</summary>

`decimal.Decimal` constructed from strings (not floats), explicit quantize/rounding — same reasoning as Java's `BigDecimal`. Or integer cents.
</details>

---

## ✅ Checklist

- [ ] Can translate every row of the §2 table both directions
- [ ] Know the `//` and `%` sign differences from Java
- [ ] Used `Counter`, `defaultdict`, `deque`, `heapq`, `bisect`, `@cache`
- [ ] Wrote a frozen dataclass with `Decimal` and validation
- [ ] Wrote a CLI with `argparse` + `pathlib`, tested with pytest
- [ ] Called an API with `requests` + timeout + status handling
