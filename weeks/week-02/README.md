# Week 2 — Model problems with classes; test and build them

[← Week 1](../week-01/) · [Roadmap](../../ROADMAP.md) · [Week 3 →](../week-03/)

**Phase 0 · Foundation** (exercises only — throwaway, not portfolio)

| Block | Hours | What it means this week |
|---|---:|---|
| Project (foundation coding) | 18 | An **inventory/ledger kata** built as a real Maven project with JUnit 5 tests (+ a small Python twin) |
| Learning | 15 | **Python interview toolkit + pitfalls**; Java OOP, `equals`/`hashCode`, exceptions, Collections & generics, JUnit 5, Maven, Git branches/merge |
| DSA | 7 | Arrays & Hashing — **8 new problems in Python** + Day-3/7 reviews + 1 Java rep |
| Interview / review | 3 | Explain solutions out loud; Sunday test |

---

## 1. Main objective

Week 1 was "can I write it". Week 2 is "can I *design* a small piece of software someone else
could trust" — in Java, with tests and a build — and "do I own the Python standard-library toolkit
that every interview solution is made of".

- **Java (Track B):** classes with invariants, correct `equals`/`hashCode`, the right collection
  for the job, custom exceptions, **JUnit 5** tests that prove behaviour, inside a **Maven**
  project that builds from the command line. The inventory/ledger kata is deliberately in
  FlowGrid's and LedgerX's vocabulary (SKU, on-hand, reserved, adjustment, balance). Still throwaway.
- **Python (Track A):** `Counter`, `defaultdict`, `deque`, `heapq`, `bisect`, `itertools`,
  classes/dataclasses — and the pitfalls that lose interviews: mutable default args, aliasing,
  shallow vs deep copy, recursion limit, dict/set average costs.

Unlocks: Week 3's Spring Boot intro (DI needs interfaces; controllers need DTOs; tests need
JUnit) and Week 4's JPA entities (`equals`/`hashCode` on entities is a famous trap). On the Python
side, every hashing/sliding-window/stack problem from here on uses this week's toolkit.

## 2. Prerequisites

- Week 1 katas pushed; Week 1 Sunday test passed (or gaps noted in [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md)).
- JDK 21 + IntelliJ + Python 3.12 working. Install **Maven 3.9+** on Monday (`mvn -v`).

## 3. Learning topics

| Topic | Subtopics | Folder file |
|---|---|---|
| **Python interview toolkit** | `collections.Counter`/`defaultdict`/`deque`, `heapq` (min-heap, tuples), `bisect`, `itertools` (`accumulate`, `groupby`, `combinations`), `functools` (`lru_cache`), `math`, classes vs `@dataclass`, `__eq__`/`__hash__`, `sorted` vs `heapq.nsmallest` | [`19-python/02-interview-toolkit.md`](../../19-python/02-interview-toolkit.md), [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) |
| **Python pitfalls & complexity** | mutable default args, aliasing, `copy` vs `deepcopy`, `str` immutability, slicing cost, dict/set O(1) average, recursion limit, sorting O(n log n), integer size, `//` and `%` with negatives | [`19-python/03-pitfalls-and-complexity.md`](../../19-python/03-pitfalls-and-complexity.md) |
| Java OOP | classes, constructors, encapsulation, `static` vs instance, interfaces vs abstract classes, polymorphism, composition over inheritance, `final`, immutability, records | [`01-java/02-oop.md`](../../01-java/02-oop.md) |
| `equals`/`hashCode`/`toString`/`compareTo` | contract, consistency with `hashCode`, `Comparable` vs `Comparator` | [`01-java/02-oop.md`](../../01-java/02-oop.md) |
| Exceptions | checked vs unchecked, custom exceptions, try-with-resources, wrapping vs swallowing | [`01-java/05-exceptions-io.md`](../../01-java/05-exceptions-io.md) |
| Collections & generics | `ArrayList`/`HashSet`/`HashMap`/`TreeMap`/`LinkedHashMap`/`ArrayDeque`, `ConcurrentModificationException`, generics, bounded types | [`01-java/03-collections-generics.md`](../../01-java/03-collections-generics.md) |
| JUnit 5 | `@Test`, assertions, `@BeforeEach`, `@ParameterizedTest`, `assertThrows`, AAA, naming | [`09-testing/junit5.md`](../../09-testing/junit5.md), [`09-testing/README.md`](../../09-testing/README.md) |
| Maven | `pom.xml`, coordinates, lifecycle, scopes, Surefire, wrapper | [`01-java/08-maven-build.md`](../../01-java/08-maven-build.md) |
| Git branches | `branch`/`switch`/`merge`, fast-forward vs merge commit, conflicts, `stash` | [`02-git/workflows.md`](../../02-git/workflows.md), [`02-git/exercises.md`](../../02-git/exercises.md) |

## 4. Concepts to learn

### 4.1 Python toolkit: `Counter`, `defaultdict`, `deque`

```python
from collections import Counter, defaultdict, deque
c = Counter("inventory")            # {'n': 2, 'i': 1, ...}; c.most_common(2)
groups = defaultdict(list)          # missing key → [] automatically
groups["".join(sorted("eat"))].append("eat")
q = deque([1, 2, 3]); q.appendleft(0); q.pop(); q.popleft()   # O(1) both ends
```

- **Interview angle:** "Group anagrams" is one `defaultdict(list)` line once you see the canonical key; BFS is `deque` — never `list.pop(0)`.
- **FlowGrid uses this:** the Week 7 order generator counts SKU demand with `Counter` and buckets orders per warehouse with `defaultdict(list)`.

### 4.2 Python toolkit: `heapq`, `bisect`, `dataclass`

```python
import heapq, bisect
from dataclasses import dataclass, field
h = []; heapq.heappush(h, (3, "c")); heapq.heappush(h, (1, "a")); heapq.heappop(h)  # (1,'a') — min-heap, tuples compare
heapq.heappush(h, (-5, "x"))        # max-heap trick: negate
i = bisect.bisect_left([1, 3, 5], 4)   # 2 — first index with value >= 4

@dataclass(frozen=True, order=True)
class Movement:
    at: int
    sku: str
    delta: int
```

- **Interview angle:** "How do you get a max-heap in Python?" (negate keys or wrap). "What does `frozen=True` give you?" (immutability and a usable `__hash__`).
- **FlowGrid uses this:** the simulation harness (Week 8) computes p50/p95/p99 latencies with `sorted` + index arithmetic and keeps the k slowest requests in a heap.

### 4.3 Python pitfalls you must be able to explain

```python
def add(item, bucket=[]):      # BUG: default evaluated once, shared across calls
    bucket.append(item); return bucket
def add_ok(item, bucket=None):
    bucket = [] if bucket is None else bucket; bucket.append(item); return bucket

import copy
a = [[1], [2]]; b = a[:]; b[0].append(9); print(a)   # [[1, 9], [2]] — shallow copy shares inner lists
c = copy.deepcopy(a)
import sys; sys.setrecursionlimit(10_000)             # default ~1000 frames: deep DFS needs this or iteration
print(-7 // 2, -7 % 2)                                 # -4 1 — floor division, sign of divisor
```

- **Interview angle:** mutable-default and shallow-copy questions appear in Python screens; recursion-limit awareness matters for tree/graph problems (Weeks 9–13).
- **FlowGrid uses this:** the generator's `@dataclass` config objects use `field(default_factory=list)` for exactly this reason.

### 4.4 Java: a class with an invariant

```java
public final class Quantity {
    private final int value;
    public Quantity(int value) {
        if (value < 0) throw new IllegalArgumentException("quantity must be >= 0, was " + value);
        this.value = value;
    }
    public int value() { return value; }
    public Quantity minus(Quantity other) { return new Quantity(this.value - other.value); }
    @Override public boolean equals(Object o) { return o instanceof Quantity q && q.value == value; }
    @Override public int hashCode() { return Integer.hashCode(value); }
    @Override public String toString() { return "Quantity[" + value + "]"; }
}
```

- **Interview angle:** "What is encapsulation *for*?" (protecting invariants). "Why immutable value objects?" (thread-safe, safe map keys, no defensive copies).
- **FlowGrid uses this:** inventory quantities by state must never go negative; the check lives in the domain, then again as a DB `CHECK` constraint in M1.

### 4.5 Java: the `equals`/`hashCode` contract; records

If `a.equals(b)` then `a.hashCode() == b.hashCode()` — always. Break it and `HashSet` "loses" objects.

```java
record SkuCode(String value) {
    SkuCode { value = value.strip().toUpperCase(); if (value.isBlank()) throw new IllegalArgumentException("blank sku"); }
}
Set<SkuCode> seen = new HashSet<>();
seen.add(new SkuCode(" bolt-m6 "));
System.out.println(seen.contains(new SkuCode("BOLT-M6")));  // true — value equality
```

- **Interview angle:** "Override `equals` but not `hashCode` — what happens?" — equal objects land in different buckets; `HashMap.get` misses. Python parallel: define `__eq__` without `__hash__` and the class becomes unhashable.
- **FlowGrid uses this:** JPA entities in M1 — generated IDs in `equals` break before persistence (ID is null). You will pick a policy in Week 4.

### 4.6 Java: choosing a collection (know the costs cold)

| Need | Java | Python twin | Key costs |
|---|---|---|---|
| Ordered list, index access | `ArrayList` | `list` | get O(1), add-end amortised O(1), insert-middle O(n) |
| Uniqueness, membership | `HashSet` | `set` | add/contains O(1) average |
| Key → value | `HashMap` | `dict` | get/put O(1) average |
| Insertion-ordered map | `LinkedHashMap` | `dict` (ordered since 3.7) | same + order; LRU with `accessOrder` |
| Sorted keys / range queries | `TreeMap` | `sorted list` + `bisect` | O(log n); `floorKey`, `headMap` |
| Stack / queue / both | `ArrayDeque` | `deque` / `list` as stack | O(1) both ends |

```java
Map<String, Integer> onHand = new HashMap<>();
onHand.merge("BOLT-M6", 5, Integer::sum);       // insert-or-add
onHand.computeIfAbsent("NUT-M6", k -> 0);
```

- **Interview angle:** "How does `HashMap` work internally?" (hash → bucket, chaining, treeify at 8, resize at 0.75). Draw it. Python's `dict` is open addressing — know the difference in one sentence.
- **FlowGrid uses this:** the allocation scorer (M3) groups candidates in `Map<SkuId, List<Candidate>>`; the low-stock cache (M3) is a `Set` of SKU IDs.

### 4.7 Java: exceptions

```java
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku, int requested, int available) {
        super("sku %s: requested %d, available %d".formatted(sku, requested, available));
    }
}
```

Unchecked for business-rule violations and programming errors; never catch-and-ignore; try-with-resources for `AutoCloseable`.

- **Interview angle:** "Checked vs unchecked — your rule?" "What does try-with-resources guarantee?" (close in reverse order; suppressed exceptions).
- **FlowGrid uses this:** `InsufficientStockException`, `ReservationConflictException` → RFC 9457 ProblemDetail in M1's `@ControllerAdvice`.

### 4.8 A JUnit 5 test that says what it checks

```java
class InventoryTest {
    @Test
    void reservingMoreThanAvailableFails() {
        var inv = new Inventory();
        inv.receive("BOLT-M6", 5);
        var ex = assertThrows(InsufficientStockException.class, () -> inv.reserve("BOLT-M6", 6));
        assertTrue(ex.getMessage().contains("available 5"));
        assertEquals(5, inv.available("BOLT-M6"));   // state unchanged after failure
    }

    @ParameterizedTest
    @CsvSource({"5,5,0", "5,2,3", "5,0,5"})
    void reserveReducesAvailable(int onHand, int reserve, int expectedAvailable) {
        var inv = new Inventory();
        inv.receive("BOLT-M6", onHand);
        inv.reserve("BOLT-M6", reserve);
        assertEquals(expectedAvailable, inv.available("BOLT-M6"));
    }
}
```

- **Interview angle:** "How do you structure a unit test?" (Arrange-Act-Assert; one behaviour per test; the name states the rule).
- **FlowGrid uses this:** every state-machine transition in M2 gets a test like the first one, including "state unchanged after failure" — which catches half of all concurrency bugs.

### 4.9 Maven lifecycle and scopes

```xml
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.11.4</version>
  <scope>test</scope>
</dependency>
```

`compile` → `test` → `package` → `verify` → `install`; each phase runs the ones before it. `test`
scope = test classpath only. Surefire runs `*Test` classes.

- **Interview angle:** "`mvn package` vs `install`?" "`compile` vs `provided` scope?"
- **FlowGrid uses this:** CI runs `mvn -B verify` from the first PR (Week 4); Failsafe binds integration tests to `verify` in Week 5.

### 4.10 Git branches and a real conflict

```bash
git switch -c feature/ledger-balance
git switch main && git merge feature/ledger-balance      # fast-forward if main did not move
# force a conflict: edit the same line on main and on a branch, then merge
git status      # both modified → edit, remove markers, then:
git add . && git commit
```

- **Interview angle:** "How do you resolve a merge conflict?" "Fast-forward vs merge commit?"
- **FlowGrid uses this:** feature branch per issue from Week 4; PR per milestone.

## 5. Resources

- Python: [`collections`](https://docs.python.org/3/library/collections.html), [`heapq`](https://docs.python.org/3/library/heapq.html), [`bisect`](https://docs.python.org/3/library/bisect.html), [`itertools`](https://docs.python.org/3/library/itertools.html), [`dataclasses`](https://docs.python.org/3/library/dataclasses.html) docs; [Python FAQ — "Why are default values shared between objects?"](https://docs.python.org/3/faq/programming.html#why-are-default-values-shared-between-objects).
- *Effective Java* (3rd ed.): Items 10–12, 14, 15–17, 69–72, 75.
- [Java Collections Framework](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/package-summary.html); [`HashMap` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html).
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/); [Maven lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html); [Maven scopes](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html).
- [Pro Git ch. 3 — Branching](https://git-scm.com/book/en/v2/Git-Branching-Branches-in-a-Nutshell).
- DSA: [`03-dsa/02-hashing.md`](../../03-dsa/02-hashing.md), [`03-dsa/01-arrays-strings.md`](../../03-dsa/01-arrays-strings.md), NeetCode "Arrays & Hashing".

## 6. Exercises + coding assignments

### 6.1 The inventory/ledger kata (main assignment, ~13 h, Java + Maven + JUnit, throwaway)

Repo `inventory-kata` (single Maven module, package `kata.inventory`). In-memory only. Model:

- `Sku` record (code upper-cased/trimmed in a compact constructor; blank → exception).
- `StockLevel` per SKU with `onHand`, `reserved`; `available = onHand − reserved` (derived, never stored).
- `Inventory` service: `receive`, `reserve`, `release`, `ship` (consumes reserved and on-hand), `adjust(sku, delta, reason)`.
- `Ledger`: append-only `List<Movement>` (`record Movement(Instant at, Sku sku, MovementType type, int delta, String reason)`); every mutating op appends exactly one movement; state must be reproducible by replaying the ledger.
- `LowStockReport`: SKUs with available < threshold, sorted by available then SKU (`Comparator` chain).

**Acceptance criteria**

- [ ] `mvn -q verify` passes with ≥ 20 tests; every public rule has ≥ 1 negative test.
- [ ] `reserve` beyond available throws `InsufficientStockException` and leaves state unchanged.
- [ ] `ship` without prior reservation throws; `release` more than reserved throws.
- [ ] `replay(ledger)` rebuilds identical `StockLevel`s (random sequence of 200 valid ops → replay equals live state).
- [ ] `Sku("bolt-m6")` equals `Sku(" BOLT-M6 ")`; tested.
- [ ] README documents the invariants in plain English.

### 6.2 Python twin (~2 h): `inventory.py` + pitfalls drills

Re-model `Sku`/`StockLevel`/`Ledger` as `@dataclass(frozen=True)` + a small `Inventory` class with
`Counter`-based on-hand/reserved and a `deque` of movements; verify with `assert`s (pytest comes in
Week 3). Then do the pitfalls drills in [`19-python/exercises.md`](../../19-python/exercises.md)
(toolkit + pitfalls sections). Goal: the same rules, ~⅓ the lines, and you can say *why*.

### 6.3 Smaller drills

- [`01-java/exercises.md`](../../01-java/exercises.md): OOP + collections sections.
- [`02-git/exercises.md`](../../02-git/exercises.md): branch, conflict, stash.
- `Comparable<Movement>` by time then SKU; verify with `Collections.sort` and a `TreeSet`.

### Break it

- Remove `hashCode` from a hand-written `Sku` class (before converting to a record); put two equal SKUs in a `HashSet`. Size? Fix, then convert to a record. In Python, define `__eq__` on a plain class without `__hash__` and try to put it in a `set` — read the `TypeError`.
- Iterate `inventory.levels()` and call `adjust` inside the loop → `ConcurrentModificationException`. In Python, delete dict keys while iterating → `RuntimeError: dictionary changed size during iteration`. Fix both properly.
- Make `available` a stored field updated on every operation; forget it in one operation. Which test catches it? None → you are missing a test; write it.
- Python: give `Inventory.__init__` a `movements=[]` default and create two inventories. Observe the shared list. Fix with `None`/`field(default_factory=...)`.

### Debug it

- Plant: `release` subtracts from `onHand` instead of `reserved`. Run the suite, read the failing assertion, set a breakpoint in `release`, watch `this` in the debugger. Fix. Write the three-step method (failing test → breakpoint → inspect state).
- `mvn verify` with a test class named `InventoryTests` (not matched by Surefire's default pattern) — tests silently don't run. Find out why from the Surefire summary. Lesson: read "Tests run: N" every time.
- Python: run the twin with `python3 -X dev inventory.py` and a deliberately infinite recursion in `replay`; read `RecursionError` and rewrite iteratively.

## 7. DSA — Arrays & Hashing (8 new, in Python)

Guides: [`03-dsa/02-hashing.md`](../../03-dsa/02-hashing.md), [`03-dsa/01-arrays-strings.md`](../../03-dsa/01-arrays-strings.md); templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md).
Limits: Easy 20 min, Medium 35 min. Same stuck-rule as Week 1.

| Day | Problem | Difficulty | Limit | Toolkit focus |
|---|---|---|---|---|
| Mon | [217. Contains Duplicate](https://leetcode.com/problems/contains-duplicate/) | Easy | 15 min | `set` membership / `len(set(nums)) < len(nums)` |
| Mon | [242. Valid Anagram](https://leetcode.com/problems/valid-anagram/) | Easy | 20 min | `Counter(s) == Counter(t)` vs 26-array |
| Tue | [1. Two Sum](https://leetcode.com/problems/two-sum/) | Easy | 20 min | complement in `dict` — compare with Week 1's brute force |
| Tue | [49. Group Anagrams](https://leetcode.com/problems/group-anagrams/) | Medium | 35 min | `defaultdict(list)` with a canonical tuple key |
| Wed | [347. Top K Frequent Elements](https://leetcode.com/problems/top-k-frequent-elements/) | Medium | 35 min | `Counter` + bucket sort (heap version returns in Week 11) |
| Thu | [238. Product of Array Except Self](https://leetcode.com/problems/product-of-array-except-self/) | Medium | 35 min | prefix/suffix products (previews Week 3) |
| Thu | [36. Valid Sudoku](https://leetcode.com/problems/valid-sudoku/) | Medium | 35 min | tuple keys in a `set`; `r // 3, c // 3` |
| Sat | [128. Longest Consecutive Sequence](https://leetcode.com/problems/longest-consecutive-sequence/) | Medium | 35 min | "only start from sequence starts" — O(n) with a `set` |

**Java rep (Sat, 30 min):** re-do **49. Group Anagrams** in Java with `HashMap<String, List<String>>`
+ `computeIfAbsent` and a sorted-char-array key. Log in the tracker's "Java rep" column.

**Spaced reviews due:** Day-7 of Week 1's problems — Reverse String, Remove Element (Mon); Remove
Duplicates from Sorted Array (Tue); Move Zeroes (Wed); Max Consecutive Ones, Best Time to Buy and
Sell Stock (Thu). Day-3 of this week's Mon problems on Thu (Contains Duplicate, Valid Anagram),
Tue's on Fri (Two Sum, Group Anagrams), Wed's on Sat (Top K Frequent Elements), Thu's on Sun
(Product of Array Except Self, Valid Sudoku). A review = re-solve from blank in Python in ≤ half
the original limit; fail → `Needs Review`, clock restarts at Day 3.

## 8. Project work — foundation exercises

The inventory/ledger kata *is* the project this week. Task checklist:

- [ ] Mon: hand-written `pom.xml` (Java 21, JUnit 5, Surefire 3.x); `Sku` + tests.
- [ ] Tue: `StockLevel`, `Inventory.receive/reserve/release` + tests; custom exceptions.
- [ ] Wed: `Ledger` + `Movement` record; every op appends; replay test.
- [ ] Thu: `ship`, `adjust`, `LowStockReport` with comparator chain; parameterized tests.
- [ ] Fri: Break-it + Debug-it tasks; README with invariants; Python twin.
- [ ] Sat: refactor pass (naming, small methods), branch + merge exercise, final `mvn verify`, tag.

Not a portfolio piece: no packaging, logging or pretty output.

## 9. Git activity

- All kata work on `feature/<thing>` branches, merged with `git merge --no-ff` so the log shows the branch (compare with squash merges in Week 5).
- Force and resolve **one real conflict** (Thu): change the same comparator line on two branches.
- `.gitignore` for Maven + Python (`target/`, `.idea/`, `*.iml`, `__pycache__/`, `.venv/`).
- Tag: `git tag -a kata-v1 -m "inventory kata complete"` and `git push --tags`.
- Conventional Commits: `feat(ledger): append movement on every mutation`, `test(inventory): reserve beyond available leaves state unchanged`, `feat(py): dataclass twin of the kata`.

## 10. Interview preparation

Weeks 1–4 ramp: explain each Python DSA solution out loud after solving (record two). New habit
this week: **before coding**, say the approach and complexity in one sentence — the seed of the
think-aloud method in [`16-interview-prep/coding-interview-method.md`](../../16-interview-prep/coding-interview-method.md) (read its first section only; full practice starts Week 5).

Questions to answer out loud:

Track A (Python): 1. Why is `def f(x, acc=[])` a bug and how do you fix it? 2. `copy.copy` vs `copy.deepcopy` — when does it matter? 3. How do you make a max-heap with `heapq`? 4. Average vs worst-case cost of `dict` lookup?
Track B (Java): 5. The `equals`/`hashCode` contract and what breaks when violated. 6. How does `HashMap` handle collisions; what is the load factor? 7. `ArrayList` vs `LinkedList` — when would you pick `LinkedList`? (Almost never; `ArrayDeque` for queues.) 8. What does `mvn verify` do that `mvn test` does not?

## 11. Revision work

- Sunday: draw `HashMap` internals from memory; write the collection cost table (§4.6) from memory for both languages.
- Redo Week 1 Kata 4 in Java with a `HashMap` + sorted keys; compare with the arrays-only version.
- Redo [242. Valid Anagram](https://leetcode.com/problems/valid-anagram/) three ways in Python (`Counter`, 26-array, `sorted`) and rank them by cost.

## 12. Daily plan

| Day | Blocks |
|---|---|
| **Mon** (8 h) | Learning 3.5: install Maven, `pom.xml` anatomy, lifecycle; Python toolkit part 1 (`Counter`, `defaultdict`, `deque`) · Project 2.5: repo, `Sku` record, first tests · DSA 2: 217, 242 + Day-7 (Reverse String, Remove Element) |
| **Tue** (8 h) | Learning 2: `equals`/`hashCode`, `Comparable`/`Comparator`, exceptions · Project 3.5: `StockLevel`, `Inventory` reserve/release · DSA 2: 1, 49 + Day-7 (Remove Duplicates) · Interview 0.5: explain out loud |
| **Wed** (8 h) | Learning 3: Java Collections & generics, `HashMap` internals; Python toolkit part 2 (`heapq`, `bisect`, `itertools`, dataclasses) · Project 3: `Ledger`, replay test · DSA 2: 347 + Day-7 (Move Zeroes) |
| **Thu** (8 h) | Learning 2: JUnit 5 parameterized tests, Surefire; Git branches + conflicts; Python pitfalls file · Project 3: `ship`, `adjust`, `LowStockReport`; forced merge conflict · DSA 2: 238, 36 + Day-7 (Max Consecutive Ones, Best Time) + Day-3 (217, 242) · Docs 1: README invariants |
| **Fri** (5 h) | Project 2.5: Break-it + Debug-it; Python twin · DSA 1.5: Day-3 (1, 49) · Retro 1: weekly-progress entry |
| **Sat** (6 h) | Project 3: refactor pass, tag `kata-v1` · DSA 1.5: 128 + Day-3 (347) + **Java rep** (49) · Interview 1.5: record 2 explanations; re-say "about me" |
| **Sun** (2–3 h) | End-of-week test (§13) · Day-3 (238, 36) · trackers · plan Week 3 · rest |

## 13. End-of-week test (90 min, timed)

**Part A — DSA in Python (40 min).** [1. Two Sum](https://leetcode.com/problems/two-sum/) in O(n)
and [49. Group Anagrams](https://leetcode.com/problems/group-anagrams/), from blank, no notes,
complexities stated out loud.

**Part B — Concepts (25 min).**

<details>
<summary>1. Python: <code>def push(x, stack=[]): stack.append(x); return stack</code> — what do <code>push(1)</code> then <code>push(2)</code> return?</summary>

`[1]` then `[1, 2]`. The default list is created once at function definition and shared across
calls. Use `stack=None` and create the list inside.
</details>

<details>
<summary>2. Java: two objects are equal by <code>equals</code> but have different <code>hashCode</code>s. <code>HashSet.add</code> both — size?</summary>

2. The set picks a bucket by hash first; different hashes → different buckets → `equals` never
consulted. The contract is violated, so the set is "wrong".
</details>

<details>
<summary>3. Python: <code>heapq.heappush(h, (priority, task))</code> — what breaks if two tasks share a priority and <code>task</code> objects are not comparable?</summary>

`TypeError` when the heap compares the second tuple element. Fix: add a unique counter as the
second element `(priority, counter, task)` or make the task comparable.
</details>

<details>
<summary>4. Java: <code>for (String s : list) if (s.isBlank()) list.remove(s);</code> — what happens?</summary>

`ConcurrentModificationException` (fail-fast iterator via `modCount`). Use `list.removeIf(String::isBlank)` or `Iterator.remove()`.
</details>

<details>
<summary>5. Why is a Java <code>record</code> (or a frozen Python dataclass) a good fit for a value object like <code>Sku</code>, and not for a JPA entity?</summary>

Immutable, `equals`/`hashCode` from components, validation in the compact constructor. JPA
entities need mutable state and identity-based equality, so records are a poor fit there.
</details>

<details>
<summary>6. What is the Maven <code>test</code> scope and why does it matter?</summary>

Available on the test classpath only; not packaged, not transitive — keeps JUnit out of production artifacts.
</details>

<details>
<summary>7. A merge shows <code>&lt;&lt;&lt;&lt;&lt;&lt;&lt; HEAD</code>. Three steps to finish?</summary>

Edit to the intended content (remove markers), `git add` the file, `git commit` (or `git merge --continue`).
</details>

**Part C — Practical (20 min).** On a branch, add `transfer(fromSku, toSku, qty)` to the Java kata
with two tests (happy path; insufficient stock leaves both unchanged), `mvn verify` green, merge to
`main`, push. Pass = tests written *before* the implementation and green at the end.

**Part D — Explain out loud (5 min).** "Walk me through how `HashMap.get` finds a value" and
"what is the difference between a Python `dict` and a Java `HashMap` in one sentence?"

## 14. Mastery checklist

- [ ] I reach for `Counter`/`defaultdict`/`deque`/`heapq`/`bisect` without looking them up, and I can explain mutable defaults, aliasing and shallow vs deep copy.
- [ ] I can design a Java class with an invariant, immutable where sensible, with correct `equals`/`hashCode`.
- [ ] I can choose between `ArrayList`, `HashMap`, `TreeMap`, `HashSet`, `ArrayDeque` and say the costs.
- [ ] I write JUnit 5 tests before or alongside code, including negative and parameterized tests.
- [ ] I can create a Maven project from a hand-written `pom.xml` and explain lifecycle and scopes.
- [ ] I can branch, merge, resolve a conflict and tag.
- [ ] The 8 hashing problems are re-solvable independently in Python within limits; 1 Java rep done.

## 15. Expected deliverables

- `inventory-kata` repo: ≥ 20 tests green, tag `kata-v1`, ≥ 3 merged feature branches, one resolved conflict in history, `python/inventory.py` twin.
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 14 problems total (Python), 2 Java reps, reviews logged.
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md): Week 2 entry with test scores.
- [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): Python toolkit, Java OOP/Collections, JUnit, Maven, Git branches rows updated.
- [`trackers/project-tracker.md`](../../trackers/project-tracker.md): Foundation row — kata done.

## 16. If you're behind / stretch

**Behind:** finish `Sku`, `Inventory.receive/reserve/release` and the ledger replay test — drop
`LowStockReport`, `adjust` and the Python twin. Keep all Day-7 reviews; drop 128 and 36 from new
problems if needed.

**Stretch:** make `Inventory` take a `Clock` so timestamps are testable (`Clock.fixed`); implement
`LowStockReport` a second time with streams (preview of Week 3); *Effective Java* Item 18
(composition over inheritance) — refactor one inheritance into composition; Python:
`functools.lru_cache` on a recursive Fibonacci and measure with `timeit`; solve
[271. Encode and Decode Strings](https://leetcode.com/problems/encode-and-decode-strings/) if you have Premium/NeetCode access.
