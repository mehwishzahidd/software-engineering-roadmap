# Java Exercises — 45 graded exercises (Weeks 1–13)

> Do these **from a blank file**, in a Maven project (from Week 3; before that, single-file
> `java X.java` is fine). Every exercise has **acceptance criteria**: it's done only when all
> are met, including tests. Difficulty: 🟢 warm-up · 🟡 core · 🔴 stretch.
> Log each one in [../trackers/technology-tracker.md](../trackers/technology-tracker.md).

Suggested project layout for all of them: `java-drills/` Maven project, one package per week
(`com.example.drills.w01`, ...), tests in `src/test/java`. Commit after each exercise with a
Conventional Commit message (`feat(w03): LRU cache via LinkedHashMap`).

**General acceptance criteria (apply to every exercise from Week 2 on):**
- [ ] Compiles with `./mvnw -q verify` and zero warnings you can't explain
- [ ] JUnit 5 tests cover the happy path, at least one edge case, and at least one invalid input
- [ ] No `System.out.println` in library code (only in `main`/CLI classes)
- [ ] You can explain the time/space complexity out loud

---

## Week 1 — Syntax basics

Module: [01-syntax-basics.md](./01-syntax-basics.md)

**1.1 🟢 Temperature table.** Print Celsius −20..40 step 5 with Fahrenheit, formatted with `printf("%5d %7.1f%n", ...)`.
- [ ] Output aligned in columns; uses `double` arithmetic (`9.0 / 5`), not integer division
- [ ] Run via `javac` + `java` from the terminal, not the IDE

**1.2 🟢 FizzBuzz with a `switch` expression.** For 1..100, compute the label with a `switch` over `(i % 3 == 0 ? 1 : 0) + (i % 5 == 0 ? 2 : 0)`.
- [ ] Uses arrow-form `switch` expression with `yield` or single expressions
- [ ] Output identical to a classic if/else version (write both, diff the output)

**1.3 🟢 Array stats.** Method `int[] minMaxSum(int[] a)` returning `{min, max, sum}`; sum in a `long`-safe way (return type `long[]`) when values can be up to `Integer.MAX_VALUE`.
- [ ] Throws `IllegalArgumentException` for empty or null input
- [ ] Correct for `{Integer.MAX_VALUE, Integer.MAX_VALUE}` (no overflow)

**1.4 🟡 Reverse words.** `String reverseWords(String s)`: `"  the sky  is blue "` → `"blue is sky the"`.
- [ ] Uses `strip()` + `split("\\s+")` and `StringBuilder`, not `+=` in a loop
- [ ] Handles empty/blank input → `""`

**1.5 🟡 Palindrome check.** Ignore non-alphanumerics and case (LeetCode 125 Valid Palindrome) with two indices, O(1) extra space.
- [ ] Uses `Character.isLetterOrDigit` and `Character.toLowerCase`
- [ ] `"A man, a plan, a canal: Panama"` → true; `"race a car"` → false

**1.6 🟡 Character frequency.** Given a lowercase string, print letters sorted by frequency desc, then alphabetically, using an `int[26]`.
- [ ] No `HashMap` — array indexed by `c - 'a'`
- [ ] Explain why `'b' - 'a'` is an `int`

**1.7 🔴 Fast stdin sum.** Read `n` then `n` numbers (up to 10⁶) from stdin with `BufferedReader` + `StringTokenizer`; print the sum as `long`.
- [ ] Generates a 10⁶-number test file with a one-line shell/Java command
- [ ] Completes in < 1 s; compare timing with `Scanner` and record both

---

## Week 2 — OOP & exceptions & JUnit

Modules: [02-oop.md](./02-oop.md), [05-exceptions-io.md](./05-exceptions-io.md) Part A, [../09-testing/junit5.md](../09-testing/junit5.md)

**2.1 🟢 BankAccount with invariants.** `deposit`, `withdraw`, `transferTo(BankAccount other, long cents)`; balance never negative.
- [ ] No public setters; invalid amounts throw `IllegalArgumentException`; overdraft throws a custom `InsufficientFundsException` with the shortfall in the message
- [ ] Tests use `assertThrows` and check the message

**2.2 🟢 `Money` value class.** `final` class with `BigDecimal amount` and `Currency`; `plus`, `minus`, `isNegative`; `equals`/`hashCode`/`toString` by hand.
- [ ] `new Money("1.5", EUR).equals(new Money("1.50", EUR))` is true (scale normalized)
- [ ] Adding different currencies throws `IllegalArgumentException`
- [ ] Test: two equal `Money` in a `HashSet` → size 1

**2.3 🟡 Break the contract.** Copy `Money`, remove `hashCode`, write a test demonstrating `HashSet` duplicates, then a test showing `HashMap.get` returns `null` for an equal key.
- [ ] Tests assert the *broken* behavior (they document the bug), in a class named `BrokenHashCodeDemoTest`
- [ ] A 3-sentence comment explains why

**2.4 🟡 Shapes & polymorphism.** `sealed interface Shape permits Circle, Rect, Triangle`; records; `double totalArea(List<Shape>)` using a pattern-matching `switch`.
- [ ] No `instanceof` chains; exhaustive `switch` without `default`
- [ ] Invalid dimensions (≤ 0) rejected in compact constructors

**2.5 🟡 Interface vs abstract class.** Model `Notifier` (interface: `send(String to, String msg)`) with `EmailNotifier` and `SmsNotifier`, plus an `abstract class RetryingNotifier` holding a `maxAttempts` field and a template method.
- [ ] Written justification (in README or Javadoc) of why one is an interface and one is abstract
- [ ] Test with a fake notifier that fails twice then succeeds

**2.6 🟡 Parse or report.** `static Optional<Integer> tryParse(String s)` and `static int parseOrThrow(String s, int line)` that throws a custom unchecked `ParseException` with the line number and original cause.
- [ ] `getCause()` is the `NumberFormatException`
- [ ] No `catch (Exception e)`

**2.7 🔴 Immutable `Transaction` + defensive copies.** A record with `List<String> tags`.
- [ ] Mutating the list passed into the constructor does not change the record
- [ ] `tx.tags().add("x")` throws `UnsupportedOperationException`

---

## Week 3 — Collections, generics & Maven

Modules: [03-collections-generics.md](./03-collections-generics.md), [08-maven-build.md](./08-maven-build.md)

**3.1 🟢 Maven from zero.** Create `java-drills` by hand-writing `pom.xml` (no archetype): Java 21, JUnit 5 (`test` scope), surefire pinned, Maven Wrapper committed.
- [ ] `./mvnw -q verify` passes on a fresh clone
- [ ] `target/` is in `.gitignore`
- [ ] You can explain each POM element without notes

**3.2 🟢 Word counter.** `Map<String, Long> countWords(String text)` case-insensitive; top N via `PriorityQueue`.
- [ ] Uses `merge` (not `containsKey` + `put`)
- [ ] `topN` is O(n log k) with a min-heap of size k; test with ties (alphabetical tiebreak)

**3.3 🟡 Group anagrams (LeetCode 49).** Key = sorted chars.
- [ ] Uses `computeIfAbsent(key, k -> new ArrayList<>())`
- [ ] Explain why `String` is a safe `HashMap` key and `char[]` is not

**3.4 🟡 LRU cache twice.** (a) `LinkedHashMap` with `accessOrder=true` + `removeEldestEntry`; (b) by hand: `HashMap<K, Node>` + doubly linked list with sentinels (LeetCode 146).
- [ ] Both pass the same parameterized test suite (`@ParameterizedTest` or abstract test class)
- [ ] `get`/`put` O(1)

**3.5 🟡 Comparator chains.** Sort `List<Employee>` (record: name, dept, salary, hired) by dept asc, salary desc, hired asc.
- [ ] Single `Comparator` built with `comparing`/`thenComparing`/`reversed` or `Comparator.reverseOrder()`
- [ ] No `a - b` anywhere; test includes `Integer.MIN_VALUE` salaries

**3.6 🟡 Generic utilities.** `static <T extends Comparable<? super T>> T max(Collection<? extends T> xs)`, `static <K, V> Map<V, List<K>> invert(Map<K, V> m)`, and a generic `Pair<A, B>` record.
- [ ] Compiles without raw types or unchecked warnings
- [ ] `max` works for `List<LocalDate>` (LocalDate implements `Comparable<ChronoLocalDate>` — that's why `? super T` matters)

**3.7 🔴 HashMap from scratch.** `MyHashMap<K, V>` with separate chaining, power-of-two capacity, hash spreading, resize at 0.75.
- [ ] Supports `put`, `get`, `remove`, `size`, `null` key
- [ ] Randomized test: 100 000 random ops compared against `java.util.HashMap`
- [ ] Test with a key class whose `hashCode()` returns a constant still passes (slowly)

---

## Week 4 — Modern Java, I/O, debugging

Modules: [04-modern-java.md](./04-modern-java.md), [05-exceptions-io.md](./05-exceptions-io.md) Part B, [09-debugging-java.md](./09-debugging-java.md)

**4.1 🟢 Stream drills.** Given `List<Transaction>`: total spend, count per category, largest expense per month, merchants seen more than 3 times, average amount per category.
- [ ] Each is a single stream pipeline using an appropriate collector
- [ ] At least one uses `groupingBy` with a downstream collector and a `TreeMap::new` supplier

**4.2 🟢 Optional refactor.** Rewrite a method with three nested `null` checks (`user.getAddress().getCity().getName()`) using `Optional.map` chains.
- [ ] No `Optional.get()`; no `Optional` parameters or fields

**4.3 🟡 Enum with behavior.** `enum Operation { ADD("+"), SUB("-"), MUL("*"), DIV("/") }` each implementing `int apply(int a, int b)`; `fromSymbol(String)`.
- [ ] `DIV` by zero throws `ArithmeticException` with a clear message
- [ ] `fromSymbol("%")` throws `IllegalArgumentException` listing valid symbols

**4.4 🟡 File stats CLI.** `java -jar stats.jar <dir>` prints file count, total bytes, and top 5 largest files under a directory (`Files.walk`).
- [ ] Streams from `Files.walk` closed via try-with-resources
- [ ] Unreadable files reported, not fatal; test uses `@TempDir`

**4.5 🟡 CSV import with per-row errors.** Implement the `CsvImporter` from [05-exceptions-io.md](./05-exceptions-io.md#8-p1-ledger-csv-import-with-per-row-error-reporting).
- [ ] Test fixture CSV with 10 rows: 3 invalid (bad date, bad amount, missing column); report has 7 imported + 3 errors with correct line numbers
- [ ] Empty file and header-only file handled

**4.6 🟡 Suppressed exceptions.** Write an `AutoCloseable` that throws on close; prove with a test that the body's exception is primary and close's is in `getSuppressed()`.
- [ ] Also show the manual try/finally version losing the primary exception

**4.7 🔴 Debugger kata.** Take a teammate's (or [../21-debugging-code-reading/](../21-debugging-code-reading/README.md) later) buggy method, or plant 3 bugs yourself in 4.1 and swap with a friend.
- [ ] Each bug found using a conditional breakpoint, exception breakpoint, or Evaluate Expression (note which)
- [ ] Each fix accompanied by a failing-then-passing regression test

---

## Week 5 — Design (SOLID & patterns)

Module: [02-oop.md](./02-oop.md) Part B

**5.1 🟡 Strategy rules engine.** `CategorizationRule` implementations: merchant-contains, regex, amount-range; `Categorizer` applies by priority.
- [ ] Adding a new rule type requires **no** change to `Categorizer` (show with a `WeekdayRule` added in a separate commit)
- [ ] Each rule unit-tested in isolation

**5.2 🟡 Factory for bank formats.** Detect format from the header line, return the right parser; unknown header → descriptive exception.
- [ ] Two formats supported; tests for both plus unknown

**5.3 🟡 Builder.** `Budget.builder(...)` with validation in `build()` (limit > 0, alertPercent 1–100).
- [ ] Built object immutable; invalid combos throw at `build()` with a clear message

**5.4 🟡 Observer.** `BudgetTracker` notifies listeners when spend crosses the alert threshold and again when it crosses 100%, but only once per month per category.
- [ ] Test with a recording listener asserting exact notifications

**5.5 🔴 Composition over inheritance.** Reproduce the `CountingSet extends HashSet` double-count, then fix with a forwarding wrapper implementing `Set<E>`.
- [ ] Test showing the broken count (6) and fixed count (3) for `addAll(List.of(1,2,3))`

---

## Week 6 — Memory & JVM

Module: [06-memory-jvm.md](./06-memory-jvm.md)

**6.1 🟢 Pass-by-value proof.** Tests for `reassign` vs `mutate` for an array, a `StringBuilder`, and an `Integer`.
- [ ] Each test has a comment drawing the stack/heap state

**6.2 🟢 Identity quiz.** A test class asserting the results of 10 `==`/`equals` comparisons on pooled/new strings and cached/uncached Integers.
- [ ] All predictions written *before* running; mismatches noted and explained

**6.3 🟡 Stack depth.** Measure max recursion depth of a trivial recursive method with default stack and with `-Xss4m`; convert a recursive DFS to an iterative one with `ArrayDeque`.
- [ ] Iterative version handles a 1 000 000-node linked chain without `StackOverflowError`

**6.4 🟡 GC observation.** Allocation-heavy program run with `-Xlog:gc`; then with `-Xmx32m`.
- [ ] Write 5 lines interpreting the log (young vs old collections, pause times)

**6.5 🔴 Leak hunt.** Program with a static cache that grows per "request"; run with `-Xmx64m -XX:+HeapDumpOnOutOfMemoryError`.
- [ ] Heap dump opened in VisualVM or Eclipse MAT; screenshot of dominator tree / GC-root path in your notes
- [ ] Fix with a bounded LRU (reuse 3.4) and show heap stays flat

---

## Week 13 — Concurrency

Module: [07-concurrency.md](./07-concurrency.md)

**13.1 🟢 RaceDemo from memory.** Type it without looking; run 5 times; record results.
- [ ] Unsafe count < expected on a multi-core machine; three fixes all exact

**13.2 🟡 Volatile stop flag.** A worker loop stopped by another thread; demonstrate (or explain, if your JVM doesn't reproduce) the hang without `volatile`.
- [ ] Final version stops within 100 ms of `stop()`; test uses `assertTimeoutPreemptively`

**13.3 🟡 Thread-safe counter map.** `hit(String key)` from 16 threads × 10 000 hits with `ConcurrentHashMap<String, LongAdder>`.
- [ ] Exact totals; a second version using `HashMap` + `containsKey`/`put` demonstrates lost updates

**13.4 🟡 Bounded producer/consumer.** `ArrayBlockingQueue` of capacity 10, 2 producers, 3 consumers, poison-pill shutdown.
- [ ] All N items consumed exactly once (assert via a concurrent set); program exits cleanly

**13.5 🟡 Parallel HTTP checks.** Given 20 URLs, fetch each with `java.net.http.HttpClient` using a virtual-thread executor; each call has a 2 s timeout; print status/latency (prototype for P4's checker).
- [ ] Total wall time ≈ slowest call, not the sum
- [ ] Timeouts and connection errors reported per URL, not thrown

**13.6 🟡 CompletableFuture composition.** Combine two async lookups (user + orders) with `thenCombine`, add `orTimeout` and a fallback via `exceptionally`.
- [ ] Tests for success, one side failing, and timeout (use a fake that sleeps)

**13.7 🔴 Deadlock and cure.** Reproduce `DeadlockDemo`; capture `jcmd Thread.print`; fix with lock ordering; then a second fix with `tryLock(timeout)` + retry.
- [ ] Thread dump excerpt saved in notes with the cycle highlighted
- [ ] Stress test (1 000 random transfers across 10 accounts, 8 threads) finishes and total balance is conserved

**13.8 🔴 P2 seat-hold race.** Write the TicketHold concurrent hold test (see [07-concurrency.md §11](./07-concurrency.md#11-applying-it-tickethold-concurrent-seat-hold-test-p2-m5)) *before* adding the fix; watch it fail; add the DB constraint / `@Version`; watch it pass.
- [ ] Commit history shows red → green
- [ ] You can explain why a `synchronized` service method would be insufficient with two app instances

---

## Progress summary

| Week | Exercises | Done |
|---|---:|---:|
| 1 | 7 | |
| 2 | 7 | |
| 3 | 7 | |
| 4 | 7 | |
| 5 | 5 | |
| 6 | 5 | |
| 13 | 8 | |
| **Total** | **46** | |
