# Week 2 — Model problems with classes; test and build them

[← Week 1](../week-01/) · [Roadmap](../../ROADMAP.md) · [Week 3 →](../week-03/)

**Phase 0 · Foundation** (exercises only — throwaway, not portfolio)

| Block | Hours | What it means this week |
|---|---:|---|
| Project (foundation coding) | 18 | An **inventory/ledger kata** built as a real Maven project with JUnit 5 tests |
| Learning | 15 | OOP, `equals`/`hashCode`, exceptions, Collections & generics, JUnit 5, Maven, Git branches/merge |
| DSA | 7 | Arrays & Hashing — **8 new problems** + Day-3/7 reviews |
| Interview / review | 3 | Explain solutions out loud; Sunday test |

---

## 1. Main objective

Week 1 was "can I write Java". Week 2 is "can I *design* a small piece of Java that someone else
could trust": classes with invariants, correct `equals`/`hashCode`, the right collection for the
job, custom exceptions, and — for the first time — **tests that prove it**, inside a **Maven**
project that builds from the command line. The inventory/ledger kata is deliberately in FlowGrid's
and LedgerX's domain so that vocabulary (SKU, on-hand, reserved, adjustment, balance) is familiar
before the real projects start. It is still throwaway: you will not reuse the code, only the
thinking.

Unlocks: Week 3's Spring Boot intro (DI needs interfaces; controllers need DTO classes; tests need
JUnit) and Week 4's JPA entities (`equals`/`hashCode` on entities is a famous trap).

## 2. Prerequisites

- Week 1 katas done and pushed; Week 1 Sunday test passed (or gaps noted in
  [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md)).
- JDK 21 + IntelliJ working. Install **Maven 3.9+** on Monday morning (`mvn -v`).

## 3. Learning topics

| Topic | Subtopics | Folder file |
|---|---|---|
| OOP in Java | classes, constructors, encapsulation, `static` vs instance, interfaces vs abstract classes, polymorphism, composition over inheritance, `final`, immutability | [`01-java/02-oop.md`](../../01-java/02-oop.md) |
| `equals`/`hashCode`/`toString`/`compareTo` | contract, symmetric/transitive, consistency with `hashCode`, `Comparable` vs `Comparator` | [`01-java/02-oop.md`](../../01-java/02-oop.md) |
| Exceptions | checked vs unchecked, custom exceptions, try-with-resources, wrapping vs swallowing, fail-fast validation | [`01-java/05-exceptions-io.md`](../../01-java/05-exceptions-io.md) |
| Collections & generics | `List`/`ArrayList`/`LinkedList`, `Set`/`HashSet`/`TreeSet`, `Map`/`HashMap`/`TreeMap`/`LinkedHashMap`, `Deque`, iteration & `ConcurrentModificationException`, generics, bounded types, wildcards (reading only) | [`01-java/03-collections-generics.md`](../../01-java/03-collections-generics.md) |
| JUnit 5 | `@Test`, assertions, `@BeforeEach`, `@ParameterizedTest`, `assertThrows`, `@DisplayName`, test naming, AAA structure | [`09-testing/junit5.md`](../../09-testing/junit5.md), [`09-testing/README.md`](../../09-testing/README.md) |
| Maven | `pom.xml`, coordinates, lifecycle (`compile`/`test`/`package`/`verify`), dependency scopes, Surefire, `mvn -q`, wrapper | [`01-java/08-maven-build.md`](../../01-java/08-maven-build.md) |
| Git branches | `branch`/`switch`/`merge`, fast-forward vs merge commit, conflict resolution, `git stash`, `.gitignore` for Maven | [`02-git/workflows.md`](../../02-git/workflows.md), [`02-git/exercises.md`](../../02-git/exercises.md) |

## 4. Concepts to learn

### 4.1 A class with an invariant

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

The constructor is the *only* door in; once built, a `Quantity` cannot be invalid. `minus` returns
a new object instead of mutating.

- **Interview angle:** "What is encapsulation *for*?" (protecting invariants, not hiding fields for its own sake). "Why make value objects immutable?" (thread-safe, safe as map keys, no defensive copies).
- **FlowGrid uses this:** inventory quantities by state (`on_hand`, `available`, `reserved`) must never go negative; the check lives in the domain, then again as a DB `CHECK` constraint in M1.

### 4.2 The `equals`/`hashCode` contract

If `a.equals(b)` then `a.hashCode() == b.hashCode()` — always. Break it and `HashSet` "loses" objects:

```java
record SkuCode(String value) {}                 // records get equals/hashCode/toString for free
Set<SkuCode> seen = new HashSet<>();
seen.add(new SkuCode("WH1-BOLT-M6"));
System.out.println(seen.contains(new SkuCode("WH1-BOLT-M6")));  // true — value equality
```

- **Interview angle:** "What happens if you override `equals` but not `hashCode`?" — objects equal by `equals` land in different buckets, `HashMap.get` misses.
- **FlowGrid uses this:** JPA entities in M1 — using generated IDs in `equals` breaks before the entity is persisted (ID is null). You will read the *Effective Java* item and decide a policy (business key or ID-only-when-non-null) in Week 4.

### 4.3 Choosing a collection (cost table you should know cold)

| Need | Use | Key costs |
|---|---|---|
| Ordered list, index access | `ArrayList` | get O(1), add-end amortised O(1), insert-middle O(n) |
| Uniqueness, fast membership | `HashSet` | add/contains O(1) average |
| Key → value | `HashMap` | get/put O(1) average; iteration order unspecified |
| Insertion-ordered map | `LinkedHashMap` | same + predictable order (LRU with `accessOrder`) |
| Sorted keys / range queries | `TreeMap` | O(log n); `floorKey`, `headMap` |
| Stack / queue / both | `ArrayDeque` | O(1) at both ends; prefer over `Stack`/`LinkedList` |

```java
Map<String, Integer> onHand = new HashMap<>();
onHand.merge("BOLT-M6", 5, Integer::sum);       // insert-or-add
onHand.computeIfAbsent("NUT-M6", k -> 0);
for (var e : onHand.entrySet()) System.out.println(e.getKey() + "=" + e.getValue());
```

- **Interview angle:** "How does `HashMap` work internally?" (hash → bucket index, chaining, treeify at 8, resize at load factor 0.75). Be able to draw it.
- **FlowGrid uses this:** the allocation scorer (M3) groups candidate warehouses per order line in a `Map<SkuId, List<Candidate>>`; the low-stock cache (M3) is a `Set` of SKU IDs.

### 4.4 Exceptions: checked vs unchecked, and where to throw

```java
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku, int requested, int available) {
        super("sku %s: requested %d, available %d".formatted(sku, requested, available));
    }
}
```

- Throw unchecked for programming errors and business-rule violations the caller cannot reasonably recover from in-line.
- Never catch-and-ignore. Either handle, or wrap with context and rethrow.
- try-with-resources for anything `AutoCloseable`.

- **Interview angle:** "Checked vs unchecked — when would you use each?" "What does try-with-resources guarantee?" (close in reverse order, suppressed exceptions).
- **FlowGrid uses this:** `InsufficientStockException`, `ReservationConflictException` → mapped to RFC 9457 ProblemDetail responses in M1's `@ControllerAdvice`.

### 4.5 A JUnit 5 test that says what it checks

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

- **Interview angle:** "How do you structure a unit test?" (Arrange-Act-Assert; one behaviour per test; name states the rule).
- **FlowGrid uses this:** every state-machine transition in M2 gets a test like the first one — including the "state unchanged after failure" assertion, which catches half of all concurrency bugs.

### 4.6 Maven lifecycle and scopes

```xml
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.11.4</version>
  <scope>test</scope>
</dependency>
```

`mvn compile` → `test` → `package` → `verify` → `install`; each phase runs the ones before it.
`test` scope = available only on the test classpath. Surefire runs `*Test` classes.

- **Interview angle:** "Difference between `mvn package` and `mvn install`?" "What is the difference between `compile` and `provided` scope?"
- **FlowGrid uses this:** CI runs `mvn -B verify` from the first PR (Week 4); integration tests bind to `verify` via Failsafe in Week 5.

### 4.7 Git branches and a real conflict

```bash
git switch -c feature/ledger-balance
# edit, commit
git switch main && git merge feature/ledger-balance      # fast-forward if main did not move
# force a conflict: edit the same line on main and on a branch, then merge
git status      # both modified
# resolve markers <<<<<<< ======= >>>>>>>, then:
git add . && git commit
```

- **Interview angle:** "How do you resolve a merge conflict?" "Fast-forward vs merge commit?"
- **FlowGrid uses this:** feature branch per issue from Week 4; PR per milestone.

## 5. Resources

- *Effective Java* (3rd ed.): Items 10–12 (`equals`, `hashCode`, `toString`), 14 (`Comparable`), 15–17 (encapsulation, immutability), 69–72 (exceptions), 75 (failure-capture in messages).
- [Java Collections Framework overview](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/package-summary.html); [`HashMap` Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html).
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/).
- [Maven — Introduction to the Build Lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html); [Dependency scopes](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html).
- [Pro Git ch. 3 — Branching](https://git-scm.com/book/en/v2/Git-Branching-Branches-in-a-Nutshell).
- DSA: [`03-dsa/01-arrays-strings.md`](../../03-dsa/01-arrays-strings.md), [`03-dsa/02-hashing.md`](../../03-dsa/02-hashing.md), NeetCode "Arrays & Hashing".

## 6. Exercises + coding assignments

### 6.1 The inventory/ledger kata (main assignment, ~14 h, Maven + JUnit, throwaway)

Repo `inventory-kata` (single Maven module, package `kata.inventory`). No database, no framework —
in-memory only. Model:

- `Sku` (value object: code upper-cased and trimmed at construction; invalid → exception).
- `StockLevel` per SKU with `onHand`, `reserved`; `available = onHand − reserved` (derived, never stored).
- `Inventory` service: `receive(sku, qty)`, `reserve(sku, qty)`, `release(sku, qty)`, `ship(sku, qty)` (ship consumes reserved and on-hand), `adjust(sku, delta, reason)`.
- `Ledger`: append-only `List<Movement>` (`record Movement(Instant at, Sku sku, MovementType type, int delta, String reason)`); every mutating operation appends exactly one movement; `Inventory` state must be reproducible by replaying the ledger.
- `LowStockReport`: SKUs whose available < threshold, sorted by available then SKU.

**Acceptance criteria**

- [ ] `mvn -q verify` passes with ≥ 20 tests; every public rule has at least one negative test.
- [ ] `reserve` beyond available throws `InsufficientStockException` and leaves state unchanged.
- [ ] `ship` without prior reservation throws; `release` more than reserved throws.
- [ ] `replay(ledger)` rebuilds identical `StockLevel`s (test: random sequence of 200 valid ops → replay equals live state).
- [ ] `Sku` uses a `record` with a compact constructor for validation; `equals`/`hashCode` tested (two `Sku("bolt-m6")` and `Sku(" BOLT-M6 ")` are equal).
- [ ] `LowStockReport` uses a `Comparator` chain (`comparingInt(...).thenComparing(...)`).
- [ ] README documents the invariants in plain English.

### 6.2 Smaller drills (from the topic folders)

- [`01-java/exercises.md`](../../01-java/exercises.md): OOP + collections sections.
- [`02-git/exercises.md`](../../02-git/exercises.md): branch, conflict, stash.
- Write a `Comparable<Movement>` ordering by time then SKU; verify with `Collections.sort` and a `TreeSet`.

### Break it

- Remove `hashCode` from a hand-written `Sku` class (before converting it to a record). Put two equal SKUs in a `HashSet`. Size? Fix, then convert to a record and delete the boilerplate.
- Iterate `inventory.levels()` and call `adjust` inside the loop. Get the `ConcurrentModificationException`, explain the fail-fast iterator, fix with a copy or an explicit index.
- Change `available` to a stored field updated on every operation. Introduce one operation that forgets to update it. Which test catches it? If none does, you are missing a test — write it.

### Debug it

- Plant a bug: `release` subtracts from `onHand` instead of `reserved`. Run the suite, read the failing assertion, use the IntelliJ debugger with a breakpoint in `release` and watch `this` in the variables pane. Fix. Write down the three-step method (failing test → breakpoint → inspect state).
- Run `mvn verify` with a test that has a typo in the class name (`InventoryTests` → not matched by Surefire's default pattern). Tests silently don't run. Find out why by reading the Surefire summary line. Lesson: read the "Tests run: N" line every time.

## 7. DSA — Arrays & Hashing (8 new)

Guides: [`03-dsa/02-hashing.md`](../../03-dsa/02-hashing.md), [`03-dsa/01-arrays-strings.md`](../../03-dsa/01-arrays-strings.md).
Limits: Easy 20 min, Medium 35 min. Same stuck-rule as Week 1.

| Day | Problem | Difficulty | Limit | Focus |
|---|---|---|---|---|
| Mon | [217. Contains Duplicate](https://leetcode.com/problems/contains-duplicate/) | Easy | 15 min | `HashSet` membership |
| Mon | [242. Valid Anagram](https://leetcode.com/problems/valid-anagram/) | Easy | 20 min | count array vs `HashMap` |
| Tue | [1. Two Sum](https://leetcode.com/problems/two-sum/) | Easy | 20 min | complement lookup — compare with Week 1's brute force |
| Tue | [49. Group Anagrams](https://leetcode.com/problems/group-anagrams/) | Medium | 35 min | canonical key design |
| Wed | [347. Top K Frequent Elements](https://leetcode.com/problems/top-k-frequent-elements/) | Medium | 35 min | frequency map + bucket sort (heap version in Week 11) |
| Thu | [238. Product of Array Except Self](https://leetcode.com/problems/product-of-array-except-self/) | Medium | 35 min | prefix/suffix products (previews Week 3) |
| Thu | [36. Valid Sudoku](https://leetcode.com/problems/valid-sudoku/) | Medium | 35 min | composite keys in a set |
| Sat | [128. Longest Consecutive Sequence](https://leetcode.com/problems/longest-consecutive-sequence/) | Medium | 35 min | "only start from sequence starts" — O(n) |

**Spaced reviews due:** Day-7 of Week 1's problems — Reverse String, Remove Element (Mon);
Remove Duplicates from Sorted Array (Tue); Move Zeroes (Wed); Max Consecutive Ones, Best Time to
Buy and Sell Stock (Thu). Day-3 of this week's Mon problems on Thu (Contains Duplicate, Valid
Anagram), Tue's on Fri (Two Sum, Group Anagrams), Wed's on Sat (Top K Frequent Elements).
A review = re-solve from blank in ≤ half the original limit; if you fail, status → `Needs Review`
and the clock restarts at Day 3.

## 8. Project work — foundation exercises

The inventory/ledger kata *is* the project this week. Task checklist:

- [ ] Mon: `mvn archetype`-free setup (write `pom.xml` by hand: Java 21, JUnit 5, Surefire 3.x); `Sku` + tests.
- [ ] Tue: `StockLevel`, `Inventory.receive/reserve/release` + tests; custom exceptions.
- [ ] Wed: `Ledger` + `Movement` record; every op appends; replay test.
- [ ] Thu: `ship`, `adjust`, `LowStockReport` with comparator chain; parameterized tests.
- [ ] Fri: Break-it + Debug-it tasks; README with invariants.
- [ ] Sat: refactor pass (naming, small methods), branch + merge exercise, final `mvn verify`.

Not a portfolio piece: do not spend time on packaging, logging or pretty output.

## 9. Git activity

- `main` is protected by habit: all kata work on `feature/<thing>` branches, merged with
  `git merge --no-ff` so the log shows the branch (you will compare with squash merges in Week 5).
- Force and resolve **one real conflict** (Thu): change the same comparator line on two branches.
- Add a Maven `.gitignore` (`target/`, `.idea/`, `*.iml`).
- Tag the finished kata: `git tag -a kata-v1 -m "inventory kata complete"` and `git push --tags`.
- Commit messages remain Conventional: `feat(ledger): append movement on every mutation`, `test(inventory): reserve beyond available leaves state unchanged`.

## 10. Interview preparation

Still the Weeks 1–4 ramp: explain each DSA solution out loud after solving (record two). Add one
new habit this week: **before coding**, say the approach and complexity in one sentence — this is
the seed of the think-aloud method in [`16-interview-prep/coding-interview-method.md`](../../16-interview-prep/coding-interview-method.md) (read its first section only; full practice starts Week 5).

Questions to answer out loud this week:

1. Explain the `equals`/`hashCode` contract and what breaks when you violate it.
2. How does `HashMap` handle collisions? What is the load factor?
3. `ArrayList` vs `LinkedList` — when would you ever pick `LinkedList`? (Almost never; `ArrayDeque` for queues.)
4. Checked vs unchecked exceptions — your rule of thumb?
5. What does `mvn verify` do that `mvn test` does not?
6. What is a fast-forward merge and when can't Git do one?

## 11. Revision work

- Sunday: draw the `HashMap` internals from memory (array of buckets → nodes → treeified bin); write the cost table from §4.3 from memory.
- Redo Week 1 Kata 4 (SKU count table) using a `HashMap` + sorted keys, compare complexity with the arrays-only version.
- Re-read your Week 1 notes on Big-O; write the complexity of every method in `Inventory`.

## 12. Daily plan

| Day | Blocks |
|---|---|
| **Mon** (8 h) | Learning 3.5: install Maven, `pom.xml` anatomy, lifecycle; OOP + immutability · Project 2.5: repo, `Sku` record, first tests · DSA 2: 217, 242 + Day-7 reviews (344, 27) |
| **Tue** (8 h) | Learning 2: `equals`/`hashCode`, `Comparable`/`Comparator`, exceptions · Project 3.5: `StockLevel`, `Inventory` reserve/release, exceptions · DSA 2: 1, 49 + Day-7 review (26) · Interview 0.5: explain out loud |
| **Wed** (8 h) | Learning 3: Collections & generics, `HashMap` internals · Project 3: `Ledger`, replay test · DSA 2: 347 + Day-7 (283) |
| **Thu** (8 h) | Learning 2: JUnit 5 parameterized tests, Surefire; Git branches + conflicts · Project 3: `ship`, `adjust`, `LowStockReport`; forced merge conflict · DSA 2: 238, 36 + Day-7 (485, 121) + Day-3 (217, 242) · Docs 1: README invariants |
| **Fri** (5 h) | Project 2.5: Break-it + Debug-it · DSA 1.5: Day-3 (1, 49) · Retro 1: weekly-progress entry |
| **Sat** (6 h) | Project 3: refactor pass, tag `kata-v1` · DSA 1.5: 128 + Day-3 (347) · Interview 1.5: record 2 explanations; re-say "about me" |
| **Sun** (2–3 h) | End-of-week test (§13) · Day-3 (238, 36) · trackers · plan Week 3 · rest |

## 13. End-of-week test (90 min, timed)

**Part A — DSA (40 min).** [1. Two Sum](https://leetcode.com/problems/two-sum/) in O(n) and
[49. Group Anagrams](https://leetcode.com/problems/group-anagrams/), from blank, no notes, complexities stated.

**Part B — Concepts (25 min).**

<details>
<summary>1. Two objects are equal by <code>equals</code> but have different <code>hashCode</code>s. What happens with <code>HashSet.add</code> twice?</summary>

Both are added (size 2): the set first selects a bucket by hash; different hashes → different
buckets → `equals` is never consulted. The contract is violated, so the set is "wrong".
</details>

<details>
<summary>2. Why is a Java <code>record</code> a good fit for a value object like <code>Sku</code>?</summary>

Immutable by default, `equals`/`hashCode`/`toString` derived from components, compact constructor
for validation/normalisation, no accidental setters. Not a fit for JPA entities (mutable identity).
</details>

<details>
<summary>3. <code>for (String s : list) if (s.isBlank()) list.remove(s);</code> — what happens?</summary>

`ConcurrentModificationException` (usually) — the fail-fast iterator detects `modCount` changed.
Use `list.removeIf(String::isBlank)` or an explicit `Iterator.remove()`.
</details>

<details>
<summary>4. What is the amortised cost of <code>ArrayList.add(e)</code> and why?</summary>

O(1) amortised: the backing array grows by ~1.5× when full; the O(n) copies happen rarely enough
that total work over n adds is O(n).
</details>

<details>
<summary>5. What is the Maven <code>test</code> scope and why does it matter?</summary>

The dependency is on the compile and runtime classpath of tests only; it is not packaged into the
artifact and not transitive to consumers — keeps JUnit out of production JARs.
</details>

<details>
<summary>6. A merge shows <code>&lt;&lt;&lt;&lt;&lt;&lt;&lt; HEAD</code>. What are the three steps to finish?</summary>

Edit the file to the intended content (remove markers), `git add` the file to mark it resolved,
`git commit` (or `git merge --continue`).
</details>

**Part C — Practical (20 min).** Add `transfer(fromSku, toSku, qty)` to the kata on a branch, with
two tests (happy path + insufficient stock leaves both unchanged), `mvn verify` green, merge to
`main`, push. Pass = tests written *before* the implementation and green at the end.

**Part D — Explain out loud (5 min).** "Walk me through how `HashMap.get` finds a value."

## 14. Mastery checklist

- [ ] I can design a small class with an invariant, immutable where sensible, with correct `equals`/`hashCode`.
- [ ] I can choose between `ArrayList`, `HashMap`, `TreeMap`, `HashSet`, `ArrayDeque` and say the costs.
- [ ] I write JUnit 5 tests before or alongside code, including negative tests and parameterized tests.
- [ ] I can create a Maven project from a hand-written `pom.xml` and explain lifecycle phases and scopes.
- [ ] I can branch, merge, resolve a conflict and tag.
- [ ] The 8 hashing problems: all re-solvable independently within limits.

## 15. Expected deliverables

- `inventory-kata` repo: ≥ 20 tests green, tag `kata-v1`, ≥ 3 merged feature branches, one resolved conflict visible in history.
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 14 problems total, reviews logged.
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md): Week 2 entry with test scores.
- [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): Java OOP/Collections, JUnit, Maven, Git branches rows updated.
- [`trackers/project-tracker.md`](../../trackers/project-tracker.md): Foundation row — kata done.

## 16. If you're behind / stretch

**Behind:** finish `Sku`, `Inventory.receive/reserve/release` and the ledger replay test — drop
`LowStockReport` and `adjust`. Keep all Day-7 reviews; drop 128 and 36 from new problems if needed.

**Stretch:** make `Inventory` generic over a `Clock` so movement timestamps are testable
(`Clock.fixed`); implement `LowStockReport` a second time with streams (preview of Week 3);
read *Effective Java* Item 18 (composition over inheritance) and refactor one inheritance you wrote
into composition; solve [271. Encode and Decode Strings](https://leetcode.com/problems/encode-and-decode-strings/) if you have Premium/NeetCode access.
