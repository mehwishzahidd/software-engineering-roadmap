# Python for Java developers

> A translation table for someone whose backend language is Java 21 and whose interview language is Python 3.12.
> Read once in Week 1 (1–2 h), then come back whenever you catch yourself writing Java in Python.
> Companion files: [`01-python-core.md`](./01-python-core.md), [`03-pitfalls-and-complexity.md`](./03-pitfalls-and-complexity.md), [`01-java/03-collections-generics.md`](../01-java/03-collections-generics.md), [`03-dsa/java-dsa-toolkit.md`](../03-dsa/java-dsa-toolkit.md).

---

## 1. Side-by-side mapping

### 1.1 Types, variables, operators

| Java | Python | Notes |
|---|---|---|
| `int x = 5;` `long`, `short`, `byte` | `x = 5` | one `int`, arbitrary precision; no overflow; no `Integer.MAX_VALUE` concerns |
| `double`, `float` | `float` | always 64-bit; `math.inf`, `float("nan")` |
| `boolean` `true`/`false` | `bool` `True`/`False` | `bool` subclasses `int` |
| `char c = 'a';` | `c = "a"` (1-char `str`) | no char type; `ord`/`chr` |
| `String s = "hi";` immutable | `s = "hi"` immutable | `+=` creates a new string in both; `StringBuilder` ↔ `list` + `"".join` |
| `null` | `None` | `x is None`, never `x == None` |
| `final int N = 3;` | `N = 3` (convention: UPPER_CASE) | no true constants; `typing.Final` for type checkers |
| `var x = ...` | `x = ...` | Python is dynamically typed; hints are optional |
| `x / y` (int division if both int) | `x // y` | `/` is always float in Python; `//` floors toward −∞ (Java truncates) |
| `x % y` sign of dividend | `x % y` sign of divisor | `-7 % 2` → `-1` (Java) vs `1` (Python) |
| `Math.floorMod(a, b)` | `a % b` | Python's default |
| `a == b` (primitives) / `a.equals(b)` (objects) | `a == b` | `==` calls `__eq__`; `is` is Java's `==` on references |
| `(int) 3.9`, `Math.floor` | `int(3.9)`, `math.floor` | `int()` truncates toward zero |
| `x++`, `++x` | `x += 1` | no `++` |
| `cond ? a : b` | `a if cond else b` | |
| `&&`, `\|\|`, `!` | `and`, `or`, `not` | return operands, not just booleans |
| `Integer.parseInt(s)`, `String.valueOf(n)` | `int(s)`, `str(n)` | |
| `Integer.toBinaryString(n)` | `bin(n)[2:]`, `f"{n:b}"` | |
| `Long.MAX_VALUE`, `Integer.MIN_VALUE` | `float("inf")`, `-float("inf")`, or `sys.maxsize` | use `inf` as sentinel |
| `switch` | `match` (3.10+) or dict dispatch | rarely needed |

### 1.2 Control flow and functions

| Java | Python |
|---|---|
| `for (int i = 0; i < n; i++)` | `for i in range(n):` |
| `for (int i = n - 1; i >= 0; i--)` | `for i in range(n - 1, -1, -1):` |
| `for (T x : xs)` | `for x in xs:` |
| index + element | `for i, x in enumerate(xs):` |
| two lists in lockstep | `for a, b in zip(xs, ys):` |
| `while (cond) { }` | `while cond:` |
| `do { } while` | `while True: ... if not cond: break` |
| `break`/`continue` | same; plus `for ... else` |
| `static int f(int a, int b)` | `def f(a: int, b: int) -> int:` |
| method overloading | default args / `*args` / `isinstance` checks — no overloading |
| varargs `int... xs` | `*xs` |
| named args (none) | `f(b=2, a=1)` keyword arguments |
| return multiple values (record/array) | `return a, b` (tuple) and `a, b = f()` |
| lambda `(a, b) -> a + b` | `lambda a, b: a + b` (single expression) |
| `Function<T,R>`, `Comparator<T>` | any callable; `key=` functions |
| anonymous inner class / local class | nested `def` (closure) |
| `static` nested helper | inner function or module-level function |
| block scope `{ }` | function scope only; loop variables leak |
| `Optional<T>` | `T \| None` and an `is None` check |

### 1.3 Collections

| Java | Python | Complexity note |
|---|---|---|
| `int[] a = new int[n];` | `a = [0] * n` | list of int objects |
| `int[][] g = new int[m][n];` | `g = [[0] * n for _ in range(m)]` | never `[[0]*n]*m` |
| `ArrayList<T>` | `list` | `add` ↔ `append`; `remove(int idx)` ↔ `pop(i)`; `remove(Object)` ↔ `remove(x)`; `size()` ↔ `len()` |
| `LinkedList`/`ArrayDeque` as queue | `collections.deque` | `offer`/`poll` ↔ `append`/`popleft`; `push`/`pop` ↔ `append`/`pop` |
| `Stack<T>` / `ArrayDeque` as stack | `list` | `push`/`pop`/`peek` ↔ `append`/`pop`/`[-1]` |
| `HashMap<K,V>` | `dict` | `getOrDefault(k, d)` ↔ `get(k, d)`; `computeIfAbsent(k, k -> new ArrayList<>())` ↔ `setdefault(k, [])` or `defaultdict(list)`; `merge(k, 1, Integer::sum)` ↔ `d[k] = d.get(k, 0) + 1` / `Counter` |
| `LinkedHashMap` | `dict` (insertion-ordered) | `OrderedDict` for `move_to_end` (LRU) |
| `TreeMap<K,V>` | **none built in** | sort + `bisect`, or a heap; `sortedcontainers` (3rd party, not on judges) |
| `HashSet<T>` | `set` | `contains` ↔ `in`; `add`/`remove` ↔ `add`/`discard` |
| `TreeSet<T>` | none | sorted list + `bisect` |
| `PriorityQueue<T>` (min) | `heapq` on a list | `offer`/`poll`/`peek` ↔ `heappush`/`heappop`/`h[0]`; max-heap: negate (Java: `Collections.reverseOrder()`) |
| `PriorityQueue<>(comparator)` | tuple keys or `__lt__` | no comparator parameter |
| `Map.Entry`, `Pair` (none) | `tuple` | `(k, v)` |
| `record Point(int x, int y)` | `@dataclass(frozen=True)` / `namedtuple` | `equals`/`hashCode`/`toString` generated in both |
| `Collections.sort(list)` / `list.sort(cmp)` | `list.sort(key=...)` | stable in both (Java: TimSort for objects, dual-pivot quicksort for primitives) |
| `Arrays.sort(arr)` | `arr.sort()` | |
| `Arrays.fill(a, v)` | `a = [v] * n` / `a[:] = [v] * len(a)` | |
| `Arrays.asList(...)`, `List.of(...)` | `[...]` / `(...)` | |
| `Collections.reverse(list)` | `list.reverse()` / `list[::-1]` | |
| `String.join(",", parts)` | `",".join(parts)` | |
| `s.charAt(i)`, `s.substring(i, j)`, `s.length()` | `s[i]`, `s[i:j]`, `len(s)` | |
| `s.toCharArray()` | `list(s)` | |
| `new String(chars)` | `"".join(chars)` | |
| `Character.isLetter(c)`, `isDigit` | `c.isalpha()`, `c.isdigit()` | |
| `Integer.compare(a, b)` | `(a > b) - (a < b)` | only inside `cmp_to_key` |
| `Collections.max(xs, cmp)` | `max(xs, key=...)` | |
| `list.stream().filter(...).map(...).collect(toList())` | `[f(x) for x in xs if p(x)]` | comprehensions |
| `IntStream.range(0, n)` | `range(n)` | |
| `stream().mapToInt(...).sum()` | `sum(x for x in xs)` | |
| `Collectors.groupingBy(f)` | `defaultdict(list)` loop or `itertools.groupby` on sorted input | |
| `Collectors.counting()` | `Counter` | |
| `anyMatch`/`allMatch` | `any(...)`/`all(...)` | short-circuit |
| `Iterator<T>`, `hasNext`/`next` | `iter()`, `next(it, default)`; generators with `yield` | |

### 1.4 Classes and OOP

| Java | Python |
|---|---|
| `class Node { int val; Node next; Node(int v) { val = v; } }` | `class Node:` + `def __init__(self, val, next=None): self.val = val; self.next = next` |
| `this` | `self` (explicit first parameter) |
| constructor overloading | default arguments / `@classmethod` factories |
| `private`/`protected`/`public` | convention: `_name` (internal), `__name` (name-mangled); nothing enforced |
| getters/setters | attributes; `@property` if logic is needed later |
| `interface Shape { double area(); }` | duck typing; `typing.Protocol` for static checks; `abc.ABC` + `@abstractmethod` for runtime enforcement |
| `extends`, `super(...)` | `class Dog(Animal):`, `super().__init__(...)` |
| `@Override` | nothing (just redefine); type checkers warn on signature mismatch |
| `abstract class` | `abc.ABC` with `@abstractmethod` |
| `static` field | class attribute (shared) — careful with mutable ones |
| `static` method | `@staticmethod` / `@classmethod` / module-level function |
| `toString()` | `__repr__` (debug) / `__str__` (display) |
| `equals()` + `hashCode()` | `__eq__` + `__hash__` (defining `__eq__` alone → unhashable) |
| `compareTo()` / `Comparable` | `__lt__` (enough for sort/heap); `functools.total_ordering` fills the rest |
| `Comparator.comparing(...)` | `key=` function; `cmp_to_key` for pairwise comparators |
| `instanceof` | `isinstance(x, T)` |
| `enum` | `enum.Enum` |
| generics `List<Integer>` | hints `list[int]` (not enforced) |
| `record` | `@dataclass(frozen=True)` |
| `Object` | `object` |
| `Iterable<T>`/`Iterator<T>` | `__iter__`/`__next__`; generators |
| `AutoCloseable` + try-with-resources | `__enter__`/`__exit__` + `with` |

### 1.5 Exceptions

| Java | Python |
|---|---|
| `try { } catch (IOException e) { } finally { }` | `try: ... except OSError as e: ... finally: ...` |
| checked vs unchecked | all unchecked; no `throws` clause |
| `throw new IllegalArgumentException("...")` | `raise ValueError("...")` |
| `throw e;` re-throw | `raise` (bare, inside `except`) |
| `new RuntimeException("x", cause)` | `raise RuntimeError("x") from cause` |
| `class MyEx extends RuntimeException` | `class MyEx(Exception): pass` |
| `catch (A \| B e)` | `except (A, B) as e:` |
| `NullPointerException` | `AttributeError: 'NoneType' object has no attribute ...` |
| `ArrayIndexOutOfBoundsException` | `IndexError` (note: slices never raise) |
| `NumberFormatException` | `ValueError` |
| `ClassCastException` | `TypeError` |
| `StackOverflowError` | `RecursionError` |
| `NoSuchElementException` | `KeyError` / `StopIteration` |
| `try`/`else` (none) | `try: ... except: ... else:` runs when no exception |

### 1.6 Packaging, tooling, testing

| Java | Python |
|---|---|
| Maven `pom.xml`, `mvn verify` | `pyproject.toml`, `pip install -e ".[dev]" && pytest` |
| `~/.m2` shared repo | per-project `.venv` (isolation) |
| `package com.x.y;` + directory | folder with `__init__.py`; `import x.y` |
| `import java.util.*;` | `from collections import deque` (explicit names) |
| `public static void main` | `if __name__ == "__main__": main()` |
| JAR | wheel / sdist (`python -m build`) — rarely needed for tools |
| JUnit 5 `@Test`, `@ParameterizedTest`, `@BeforeEach` | pytest `def test_*`, `@pytest.mark.parametrize`, fixtures |
| `assertEquals(exp, act)` | `assert act == exp` (rewritten assertions show values) |
| `assertThrows(Ex.class, () -> ...)` | `with pytest.raises(Ex): ...` |
| Mockito | `unittest.mock` / `monkeypatch` / injected fakes |
| Testcontainers | Testcontainers for Python, or Compose + `integration` marker |
| `javac` type errors | `mypy` / `pyright` (opt-in, static) |
| Checkstyle/Spotless | `ruff` / `black` (optional) |
| SLF4J/Logback | `logging` |
| Jackson | `json` + dataclasses |
| JDBC / Spring Data | `psycopg` 3 |
| `BigDecimal` | `decimal.Decimal` |
| `ExecutorService`, `CompletableFuture` | `concurrent.futures.ThreadPoolExecutor`, `asyncio` |
| `synchronized`, `ReentrantLock` | `threading.Lock` (the GIL does not make compound operations atomic) |

---

## 2. Things Java developers get wrong in Python

1. **Writing getters/setters and `private` fields.** Use plain attributes; add `@property` only when logic appears. `_name` signals "internal".
2. **Building class hierarchies for everything.** A function, a tuple or a dataclass is usually enough. Interfaces are duck typing; `Protocol` only when a type checker needs it.
3. **`for i in range(len(xs)): x = xs[i]`.** Iterate directly, or `enumerate` when the index is needed.
4. **Using `list` as a queue** (`pop(0)`) because `ArrayList` was fine. Use `deque`.
5. **Expecting `%` and `/` to behave like Java.** `-7 // 2 == -4`, `-7 % 2 == 1`, `7 / 2 == 3.5`.
6. **Comparing with `is`** as if it were reference equality you want. `==` is what you want; `is` only for `None`.
7. **Believing type hints are enforced.** `def f(x: int)` accepts a string at runtime. Run `mypy`.
8. **Expecting `null` checks to throw early.** Passing `None` fails later with `AttributeError`; check at the boundary.
9. **`Integer.MAX_VALUE` sentinels and overflow guards.** Use `float("inf")`; ints do not overflow (except when a problem simulates 32-bit).
10. **Looking for `TreeMap`/`TreeSet`.** There is none; use sort + `bisect`, a heap, or restructure.
11. **Looking for a `PriorityQueue` comparator.** Push tuples `(key, item)` or define `__lt__`.
12. **Mutable default arguments** (`def f(acc=[])`) — Java has no defaults so the trap is new.
13. **Mutable class attributes** as "instance fields" (`items = []` in the class body is shared, unlike a Java instance field initializer).
14. **Overloading methods** — the last `def` wins silently. Use defaults or `*args`.
15. **Catching `Exception` to "be safe."** Catch the specific exception; let the rest propagate; never bare `except:`.
16. **Semicolons, braces-like indentation mistakes, `this.`** — Python cares about indentation; `self` is explicit.
17. **StringBuilder mindset without the join.** `s += c` in a loop is O(n²) worst case; collect and `"".join`.
18. **Concurrency assumptions.** Threads do not run Python bytecode in parallel (GIL); they do help with I/O. CPU parallelism → processes.
19. **Forgetting that variables leak from loops and `if` blocks.** No block scope: `for i in ...:` leaves `i` defined.
20. **`==` on floats and `BigDecimal` habits.** `Decimal("0.1")`, not `Decimal(0.1)`; never construct money from floats.
21. **Static typing reflexes in interviews:** declaring types for every local costs time. Annotate signatures, keep bodies lean.
22. **Not using tuples.** `return a, b`, `for k, v in d.items()`, `(r, c)` as keys, `(dist, node)` in heaps — tuples are everywhere.
23. **Writing `if len(xs) == 0`/`if x == None`/`if flag == True`.** Idioms: `if not xs`, `if x is None`, `if flag`.
24. **`sorted` vs `sort`:** `xs = xs.sort()` sets `xs` to `None`.
25. **Slicing everywhere because it's pretty.** Every slice is a copy; in recursion pass indices.

---

## 3. A worked translation

Java (Two Sum with a HashMap):

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int need = target - nums[i];
        if (seen.containsKey(need)) return new int[]{seen.get(need), i};
        seen.put(nums[i], i);
    }
    throw new IllegalArgumentException("no solution");
}
```

Python:

```python
def two_sum(nums: list[int], target: int) -> list[int]:
    seen: dict[int, int] = {}
    for i, x in enumerate(nums):
        need = target - x
        if need in seen:
            return [seen[need], i]
        seen[x] = i
    raise ValueError("no solution")
```

What changed: `enumerate` instead of index loop; `in` instead of `containsKey`; no declared array type; `ValueError` instead of a checked/unchecked hierarchy; type hints on the signature only. Same O(n) time, O(n) space — say it either way.

Weekly Java rep: pick one solved Python problem and re-do it in Java to keep `HashMap`/`ArrayDeque`/`PriorityQueue`/`Comparator` fluent — see the "Java rep" block in [`exercises.md`](./exercises.md) and [`03-dsa/java-dsa-toolkit.md`](../03-dsa/java-dsa-toolkit.md).
