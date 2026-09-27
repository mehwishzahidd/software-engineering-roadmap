# Python interview questions

> 55 questions with model answers. Answer out loud first, then open the answer. Score yourself 0/1/2 (blank / partial / complete with an example).
> Sources of truth: [`01-python-core.md`](./01-python-core.md), [`02-interview-toolkit.md`](./02-interview-toolkit.md), [`03-pitfalls-and-complexity.md`](./03-pitfalls-and-complexity.md), [`PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md). Résumé-defense angle: [`17-resume-tech-defense/python.md`](../17-resume-tech-defense/python.md).

**Sections:** [Language semantics](#a-language-semantics) · [Complexity](#b-complexity) · [Standard library](#c-standard-library) · [Pitfalls](#d-pitfalls) · [Testing & tooling](#e-testing-and-tooling) · [Strategy](#f-language-strategy)

---

## A. Language semantics

<details><summary>1. Is Python pass-by-value or pass-by-reference?</summary>

Neither in the C++ sense: it is "pass by assignment" (call by sharing). The parameter name is bound to the same object the caller passed. Mutating a mutable argument is visible to the caller; rebinding the parameter is not. Example: `def f(xs): xs.append(1)` changes the caller's list; `def g(xs): xs = []` does not.
</details>

<details><summary>2. Which built-in types are mutable and which are immutable?</summary>

Immutable: `int`, `float`, `bool`, `str`, `tuple`, `frozenset`, `bytes`, `None`. Mutable: `list`, `dict`, `set`, `bytearray`, user classes by default. Only immutable (hashable) values can be dict keys or set members; a tuple is hashable only if all its elements are.
</details>

<details><summary>3. What is the difference between `is` and `==`?</summary>

`==` compares values by calling `__eq__`; `is` compares object identity (`id`). Use `is` for singletons (`None`, `True`, `False`, sentinel objects). `a is b` for equal ints may be True or False depending on caching (−5..256 are cached), so it is never a correctness tool for values.
</details>

<details><summary>4. What does `x = y = []` do vs `x, y = [], []`?</summary>

The first binds both names to one list (aliasing); the second creates two independent lists. Same trap with `a = b = 0` (harmless, ints immutable) vs mutable objects.
</details>

<details><summary>5. Explain Python's scoping rule (LEGB) and `nonlocal`/`global`.</summary>

Names resolve Local → Enclosing function → Global (module) → Built-ins. Assignment inside a function makes a name local for the whole function, so `count += 1` in an inner function fails with `UnboundLocalError` unless declared `nonlocal count` (enclosing) or `global count` (module). Mutating an object (`lst.append`) needs no declaration because it is not an assignment to the name.
</details>

<details><summary>6. What is a closure and why do lambdas in a loop all return the last value?</summary>

A closure is a function that captures variables from its enclosing scope by reference (cell), not by value. `[lambda: i for i in range(3)]` captures the single variable `i`, which is 2 after the loop, so every lambda returns 2. Bind early with a default argument (`lambda i=i: i`) or `functools.partial`.
</details>

<details><summary>7. How does integer division and modulo behave with negatives?</summary>

`//` floors toward negative infinity; `%` has the sign of the divisor; together they satisfy `a == b * (a // b) + a % b`. `-7 // 2 == -4`, `-7 % 2 == 1`. Java truncates toward zero (`-3`, `-1`). `int(a / b)` gives truncation in Python (via float).
</details>

<details><summary>8. Why are Python integers "unbounded" and what does that cost?</summary>

`int` is arbitrary precision: it grows as needed, so there is no overflow. Arithmetic on huge ints costs O(digits) for add and more for multiply, but for interview ranges it is effectively constant. When a problem specifies 32-bit behaviour (e.g. Reverse Integer 7), you must check the range yourself.
</details>

<details><summary>9. What is the difference between a list and a tuple beyond mutability?</summary>

Tuples are hashable (if contents are), slightly smaller, and signal a fixed-shape record. They support the same indexing/slicing. Lists have `append`/`sort` etc. Use tuples for coordinates, heap entries, dict keys, multiple returns.
</details>

<details><summary>10. How do `__eq__` and `__hash__` interact?</summary>

Objects that compare equal must have equal hashes, because dict/set locate a bucket by hash and then confirm by `__eq__`. Defining `__eq__` without `__hash__` sets `__hash__ = None` (unhashable). Implement `__hash__` as `hash((field1, field2))` over the same fields used in `__eq__`, or use `@dataclass(frozen=True)`.
</details>

<details><summary>11. What is duck typing? How does Python do interfaces?</summary>

An object is usable if it has the needed methods, regardless of declared type ("if it quacks..."). No interface keyword is needed. For static checking use `typing.Protocol`; for runtime enforcement use `abc.ABC` with `@abstractmethod`. Compared with Java, `Comparable`/`Iterable` become `__lt__`/`__iter__` dunder methods.
</details>

<details><summary>12. What are `*args` and `**kwargs`?</summary>

`*args` collects extra positional arguments into a tuple; `**kwargs` collects extra keyword arguments into a dict. On the call side `*` and `**` unpack sequences and mappings into arguments (`print(*nums)`, `f(**opts)`).
</details>

<details><summary>13. What is a generator, and how does it differ from a list?</summary>

A function containing `yield` returns a generator object that produces values lazily on `next()`, keeping its local state between yields. It uses O(1) memory for the sequence, can be infinite, and can be consumed once. A list materializes everything up front. Generator expressions `(x for x in xs)` are the inline form.
</details>

<details><summary>14. How does `with` work?</summary>

It calls the context manager's `__enter__`, binds the result, runs the block, and always calls `__exit__` (even on exceptions) — a structured try/finally. Used for files, locks, DB connections, `pytest.raises`. `contextlib.contextmanager` turns a generator into one.
</details>

<details><summary>15. What is the difference between `__repr__` and `__str__`?</summary>

`__repr__` is the unambiguous developer representation (REPL, debugger, containers' printing); `__str__` is the user-facing string (`print`, `str()`), falling back to `__repr__`. For node classes define `__repr__` so lists of nodes print usefully.
</details>

<details><summary>16. What are dataclasses and what do they generate?</summary>

`@dataclass` generates `__init__`, `__repr__`, `__eq__` from annotated fields; `order=True` adds comparisons; `frozen=True` makes instances immutable and hashable; `slots=True` reduces memory. Mutable defaults must use `field(default_factory=list)`. They replace boilerplate node/record classes.
</details>

<details><summary>17. Are type hints enforced?</summary>

No. They are annotations stored on the function; the interpreter ignores them. `mypy`/`pyright` check them statically; some libraries read them at runtime (dataclasses, pydantic). In interviews they document intent; in tooling they catch bugs in CI.
</details>

<details><summary>18. How do exceptions differ from Java's?</summary>

All Python exceptions are unchecked; no `throws` clause. `try/except/else/finally`; `except (A, B) as e`; `raise ... from cause` for chaining. `KeyboardInterrupt` and `SystemExit` derive from `BaseException`, so `except Exception` does not swallow them. EAFP style (`try: d[k] except KeyError`) is idiomatic where Java would check first.
</details>

<details><summary>19. What does `if __name__ == "__main__":` do?</summary>

Runs the block only when the file is executed as a script, not when imported as a module. It keeps CLI entry points importable and testable (`main(argv)`).
</details>

<details><summary>20. What is the GIL?</summary>

The Global Interpreter Lock: a mutex in CPython letting only one thread execute Python bytecode at a time. Threads still help I/O-bound work because the GIL is released during blocking I/O; CPU-bound parallelism needs `multiprocessing`/`ProcessPoolExecutor`. It also means compound operations like `count += 1` are still not atomic — use a `Lock`. Python 3.13 has an experimental free-threaded build; 3.12 has the GIL.
</details>

---

## B. Complexity

<details><summary>21. Complexity of list operations: index, append, insert(0), pop(), pop(0), `in`, slice, sort.</summary>

O(1), O(1) amortized, O(n), O(1), O(n), O(n), O(k), O(n log n). Lists are dynamic arrays of references; anything that shifts elements is linear.
</details>

<details><summary>22. Why is dict/set lookup "O(1) on average" and what is the worst case?</summary>

Hash tables: compute the hash, probe a bucket; with a good hash and load factor ≤ 2/3 the expected probes are constant. Worst case O(n) with many collisions (adversarial keys); CPython randomizes string hashes per process to make that hard.
</details>

<details><summary>23. What is the complexity of `"".join(parts)` vs repeated `+=`?</summary>

`join` is O(total length): one allocation, one copy. Repeated `+=` on strings is O(n²) in the worst case because each concatenation copies. CPython has an in-place optimization when the string has a single reference, but it is not guaranteed.
</details>

<details><summary>24. Complexity of `heapq` operations?</summary>

`heappush`/`heappop` O(log n); `heapify` O(n); `h[0]` O(1); `nlargest(k)`/`nsmallest(k)` O(n log k); `heappushpop` O(log n). No decrease-key: push a new entry and skip stale ones.
</details>

<details><summary>25. `bisect_left` vs `bisect_right` and `insort` cost?</summary>

Both binary-search in O(log n): `bisect_left` returns the first index with `a[i] >= x`, `bisect_right` the first with `a[i] > x`; their difference is the count of `x`. `insort` is O(n) because inserting into a list shifts elements.
</details>

<details><summary>26. What is Timsort and why does it matter?</summary>

Python's sort: a hybrid of merge sort and insertion sort exploiting existing runs. O(n log n) worst, O(n) best on nearly sorted data, stable, O(n) auxiliary space. Stability lets you compose multi-key sorts with successive sorts.
</details>

<details><summary>27. How much slower is Python than Java for CPU-bound loops, and what does that mean in OAs?</summary>

Roughly 10–100× for tight loops (interpretation, dynamic typing, boxed ints). Rule of thumb: ~10^7 simple operations per second. So O(n²) with n = 10^5 will time out; the fix is always algorithmic, then built-ins over Python-level loops (`sum`, comprehension, `Counter`).
</details>

<details><summary>28. Complexity of `Counter(xs).most_common(k)` and of `sorted(Counter(xs).items())`?</summary>

O(n) to build; `most_common(k)` uses a heap: O(n log k); sorting all items: O(u log u) for u distinct keys.
</details>

<details><summary>29. Space complexity of a recursive DFS on a tree vs an iterative one?</summary>

Both O(h) for tree height h: the recursive version in call frames, the iterative in an explicit stack. Recursion is limited by `sys.getrecursionlimit()` (1000 default), so a degenerate tree/list of 10^5 nodes needs iteration.
</details>

<details><summary>30. Why is `x in range(10**9)` fast but `x in list(range(10**9))` catastrophic?</summary>

`range` implements `__contains__` arithmetically in O(1) and stores three ints; the list materializes 10^9 objects and scans them linearly.
</details>

---

## C. Standard library

<details><summary>31. When do you choose `Counter`, `defaultdict(int)`, or a plain dict?</summary>

`Counter` for frequencies with `most_common`, arithmetic, and zero-default reads without insertion. `defaultdict(int/list/set)` for grouping/accumulation with automatic empty values (beware insertion on read). Plain dict when explicit `get`/`setdefault` reads better or when the interviewer wants the mechanics visible.
</details>

<details><summary>32. How do you implement a max-heap in Python?</summary>

`heapq` is min-only: push negated keys `(-key, item)`, or wrap items in a class whose `__lt__` is inverted, or use `dataclass(order=True)` with negated priority. For top-k largest with `nlargest`, no negation needed.
</details>

<details><summary>33. Why can `heappush(h, (priority, obj))` raise `TypeError`?</summary>

When priorities tie, tuple comparison compares the second elements; arbitrary objects don't support `<`. Add a monotonically increasing counter as the second element `(priority, seq, obj)` or define `__lt__` on the object.
</details>

<details><summary>34. What does `deque` give you over `list`?</summary>

O(1) `appendleft`/`popleft` (list is O(n) at the front), `maxlen` for bounded windows, `rotate`. Middle indexing is O(n) though. It is the queue for BFS and the structure for monotonic-deque problems.
</details>

<details><summary>35. How does `@lru_cache`/`@cache` work and what are its constraints?</summary>

Wraps the function with a dict keyed by the arguments (must be hashable — tuples not lists), returning the cached result on repeat calls. `@cache` is unbounded; `lru_cache(maxsize)` evicts least-recently-used. `cache_clear()` resets; on methods the cache key includes `self`.
</details>

<details><summary>36. How do you sort by multiple keys with mixed directions?</summary>

Return a tuple key and negate numeric fields for descending: `key=lambda x: (-x.score, x.name)`. For a non-negatable (string) descending key, sort twice from least significant to most (stable), or use `cmp_to_key` with a comparator.
</details>

<details><summary>37. What does `itertools.groupby` require?</summary>

Consecutive equal keys — it groups runs, not all equal items. Sort by the same key first for a SQL-style GROUP BY. Each group iterator is consumed before moving on.
</details>

<details><summary>38. Explain `zip` behaviour on unequal lengths and how to unzip.</summary>

`zip` stops at the shortest; `zip(..., strict=True)` (3.10+) raises on mismatch; `itertools.zip_longest` pads. Unzip with `xs, ys = zip(*pairs)` (tuples).
</details>

<details><summary>39. `sorted(d)` vs `sorted(d.items())` vs `max(d, key=d.get)`?</summary>

`sorted(d)` sorts keys; `sorted(d.items(), key=lambda kv: kv[1])` sorts pairs by value; `max(d, key=d.get)` returns the key with the largest value (ties → first encountered in insertion order).
</details>

<details><summary>40. What is `bisect` good for beyond searching?</summary>

Counting elements in a range in sorted data, insertion points, "smallest ≥ x", LIS in O(n log n) (patience tails), snapshotted time-series lookup (`bisect_right` on timestamps then index − 1), and replacing a `TreeMap` when data can be sorted once.
</details>

<details><summary>41. How do you represent a graph in Python?</summary>

Adjacency list as `defaultdict(list)` for arbitrary labels or `[[] for _ in range(n)]` for 0..n−1 nodes; weighted as lists of `(neighbor, weight)`; grids are implicit graphs with a `DIRS` tuple. Visited as a `set` of nodes/`(r, c)` tuples or a `bool` matrix; distances as a dict or list.
</details>

<details><summary>42. When would you use `Decimal` and why not `float`?</summary>

Money and anything requiring exact decimal arithmetic: `Decimal("0.1") + Decimal("0.2") == Decimal("0.3")`, floats give `0.30000000000000004`. Construct from strings or ints, quantize to the currency scale, use `ROUND_HALF_UP` where the business requires. Maps to Java's `BigDecimal` in LedgerX.
</details>

---

## D. Pitfalls

<details><summary>43. Explain the mutable default argument bug and its fix.</summary>

Defaults are evaluated once at function definition and stored on the function object; a mutable default (`[]`, `{}`, `set()`) is shared across calls, so state leaks between calls. Use `None` as the default and create the object inside.
</details>

<details><summary>44. What is wrong with `grid = [[0] * n] * m`?</summary>

The outer `* m` repeats references to one inner list; every row is the same object. Use `[[0] * n for _ in range(m)]`.
</details>

<details><summary>45. Shallow vs deep copy — when is a shallow copy enough?</summary>

Shallow copies the outer container only; inner mutable objects are shared. It is enough when the elements are immutable (ints, strings, tuples of immutables) — e.g. `res.append(path[:])` in backtracking. Nested lists need `copy.deepcopy` or a per-row copy.
</details>

<details><summary>46. Why does modifying a list while iterating skip elements?</summary>

The iterator tracks an index; removing the current element shifts the next one into its slot, which the iterator then skips. Sets/dicts raise `RuntimeError` instead. Build a new container or iterate over a snapshot.
</details>

<details><summary>47. What happens at the recursion limit and how do you handle deep recursion?</summary>

`RecursionError` after ~1000 frames. Options: `sys.setrecursionlimit(n)` (the C stack can still overflow at extreme depth on some platforms), or convert to iteration with an explicit stack. State the trade-off when you raise the limit in an interview.
</details>

<details><summary>48. Why is `0.1 + 0.2 != 0.3` and how do you compare floats?</summary>

Binary floating point cannot represent 0.1 exactly; rounding errors accumulate. Compare with `math.isclose(a, b, rel_tol=..., abs_tol=...)`, keep computations in integers when possible (cross-multiply instead of dividing), or use `Decimal`/`fractions.Fraction` for exactness.
</details>

<details><summary>49. What does `if adj[v]:` do to a `defaultdict` and why does it matter?</summary>

It inserts `v` with an empty list. That changes `len(adj)`, adds nodes to iteration, and can raise "dictionary changed size during iteration" if it happens inside a loop over `adj`. Use `if v in adj`.
</details>

<details><summary>50. Why is slicing inside recursion a problem and what is the fix?</summary>

Each `a[1:]` copies O(n), giving O(n²) time and memory over n levels. Pass indices (`lo`, `hi`) and slice only when you need a copy (e.g. merge sort's merge step, which is O(n log n) total anyway).
</details>

---

## E. Testing and tooling

<details><summary>51. How do you structure and test a Python tool in a Java project repo?</summary>

A `tools/` package with `pyproject.toml`, `src/<pkg>/` (pure core + I/O edges: `cli.py`, `api_client.py`, `db.py`), `tests/` with `conftest.py` fixtures; `pytest` with `parametrize`, `tmp_path`, `monkeypatch`, fakes for HTTP (`httpx.MockTransport`) and an `integration` marker for tests needing Postgres/API; `mypy --strict` and `pytest` in GitHub Actions; a README that says where the tool is used (TESTING.md/PERFORMANCE.md).
</details>

<details><summary>52. How would you write a load harness in Python and what are its limits?</summary>

`ThreadPoolExecutor` submitting `httpx` requests, `as_completed` collecting `(status, latency)`, percentiles from the sorted latencies, JSON on stdout, logging on stderr, parameters for concurrency/duration/scenario. Threads are fine because the work is I/O-bound (GIL released). Limits: client-side overhead and the GIL cap throughput at a few thousand req/s per process; for higher load use multiple processes or k6, and always record methodology (environment, warm-up, dataset).
</details>

<details><summary>53. How does the LedgerX Python verifier prove correctness without trusting the Java code?</summary>

It reads Postgres directly with psycopg using a read-only role, loads amounts as `Decimal`, and checks invariants from raw rows: every journal transaction's entries sum to zero, each account's materialized balance equals the sum of its entries, no orphan entries, state/entry consistency. It reports violations and exits non-zero. Because it shares no code with the service, a bug in the Java layer cannot hide itself.
</details>

---

## F. Language strategy

<details><summary>54. Why do you interview in Python if your backend work is in Java?</summary>

Under time pressure Python lets me express the algorithm with the least ceremony: built-in tuples, dicts, sets, heaps, slicing, comprehensions, arbitrary-precision ints, and a stdlib (`Counter`, `deque`, `bisect`, `itertools`) that maps one-to-one to interview patterns. I keep Java collections fluent with weekly Java reps, so if a role's OA requires Java I can switch. Coding-interview language choice is about communicating the algorithm; my engineering depth is demonstrated by the Java/Spring projects.
</details>

<details><summary>55. Why is Java your backend language and not Python?</summary>

The roles I target are Java/Spring backend roles and my résumé history is Java. Java gives static typing, a mature concurrency model (executors, virtual threads), JPA/transactions, and the Spring ecosystem I built FlowGrid, LedgerX, ForgeCI and FlagForge with. Python is the right tool for the projects' tooling — generators, load harnesses, verifiers, a small SDK — where iteration speed matters more than throughput, and each of those components is typed and tested like the Java code.
</details>

---

**Scoring:** 55 questions × 2 = 110. ≥ 95 → Python interview-ready for this checkpoint. 75–94 → redo the weak section's file. < 75 → repeat Week 2 material before the next checkpoint. Log the score in [`trackers/interview-tracker.md`](../trackers/interview-tracker.md).
