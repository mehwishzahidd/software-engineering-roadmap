# Java Exercises — graded katas (Weeks 1–3, 5, 15)

> These are **throwaway katas**, not portfolio projects — the portfolio is FlowGrid, LedgerX,
> ForgeCI and FlagForge. Each kata isolates one Java concept so that when a project milestone needs
> it you already know how it behaves. Do them **from a blank file**, with tests, in a Maven project
> (`java-katas/`, one package per week). Difficulty: 🟢 warm-up · 🟡 core · 🔴 stretch.
> Log them in [../trackers/technology-tracker.md](../trackers/technology-tracker.md).
>
> DSA problems are **not** here — they are solved in Python ([../03-dsa/README.md](../03-dsa/README.md)).
> The weekly Java DSA rep uses [03-collections-generics.md](./03-collections-generics.md#java-for-occasional-dsa-reps).

**General acceptance criteria (every exercise from Week 2 on):**
- [ ] `./mvnw -q verify` passes; no compiler warnings you can't explain
- [ ] JUnit 5 tests cover the happy path, at least one edge case, and at least one invalid input
- [ ] No `System.out.println` in library code (only in `main`/CLI classes)
- [ ] You can explain the design choice and its complexity out loud in 60 seconds
- [ ] Committed with a Conventional Commit message (`feat(w02): stock level invariants`)

---

## Week 1 — Syntax basics

Module: [01-syntax-basics.md](./01-syntax-basics.md)

**1.1 🟢 Temperature table.** Print Celsius −20..40 step 5 with Fahrenheit using `printf("%5d %7.1f%n", ...)`.
- [ ] Output aligned; uses `double` arithmetic (`9.0 / 5`), not integer division
- [ ] Compiled and run with `javac` + `java` from the terminal, not the IDE

**1.2 🟢 FizzBuzz with a `switch` expression** over `(i % 3 == 0 ? 1 : 0) + (i % 5 == 0 ? 2 : 0)`.
- [ ] Arrow-form `switch` expression; output identical to an if/else version (diff them)

**1.3 🟢 Array stats.** `long[] minMaxSum(int[] a)` returning `{min, max, sum}`.
- [ ] Throws `IllegalArgumentException` for null/empty input
- [ ] Correct for `{Integer.MAX_VALUE, Integer.MAX_VALUE}` (no overflow)

**1.4 🟡 Reverse words.** `"  the sky  is blue "` → `"blue is sky the"`.
- [ ] `strip()` + `split("\\s+")` + `StringBuilder` (no `+=` in a loop); blank input → `""`

**1.5 🟡 SKU normalizer.** `normalizeSku(" mug-12oz-blu ")` → `"MUG-12OZ-BLU"`; reject SKUs that aren't 3–32 chars of `[A-Z0-9-]` after normalization.
- [ ] Uses `strip`, `toUpperCase`, `chars().allMatch(...)` or a loop — no regex for the first version, then a regex version; both pass the same tests

**1.6 🟡 Character frequency.** Letters of a lowercase string sorted by frequency desc, then alphabetically, using an `int[26]`.
- [ ] No `HashMap`; explain why `'b' - 'a'` is an `int`

**1.7 🔴 Fast stdin sum.** Read `n` then `n` numbers (up to 10⁶) with `BufferedReader` + `StringTokenizer`, print the `long` sum.
- [ ] Generate the input file with a one-liner; < 1 s runtime; record `Scanner` timing for comparison

---

## Week 2 — OOP, exceptions, collections, Maven & JUnit

Modules: [02-oop.md](./02-oop.md) Part A, [05-exceptions-io.md](./05-exceptions-io.md) Part A, [03-collections-generics.md](./03-collections-generics.md), [08-maven-build.md](./08-maven-build.md), [../09-testing/junit5.md](../09-testing/junit5.md)

**2.1 🟢 Maven from zero.** Hand-write `pom.xml` for `java-katas` (no archetype): Java 21 via `maven.compiler.release`, JUnit 5 in `test` scope, surefire pinned, Maven Wrapper committed.
- [ ] `./mvnw -q verify` passes on a fresh clone; `target/` ignored
- [ ] You can explain every POM element without notes

**2.2 🟢 Stock level with invariants.** `StockLevel` (sku, warehouseId, onHand, reserved) with `receive(n)`, `reserve(n)`, `release(n)`, `available()`.
- [ ] No public setters; `reserved` never exceeds `onHand`, nothing goes negative; violations throw a custom `InsufficientStockException` carrying requested/available
- [ ] Tests use `assertThrows` and check the message

**2.3 🟢 `Money` value class.** `final` class with `BigDecimal` + `Currency`; `plus`, `minus`, `isNegative`; hand-written `equals`/`hashCode`/`toString`.
- [ ] `Money.of("1.5", EUR).equals(Money.of("1.50", EUR))` is true (scale normalized)
- [ ] Adding different currencies throws; two equal `Money` in a `HashSet` → size 1

**2.4 🟡 Break the contract.** Copy `Money`, remove `hashCode`; tests demonstrating `HashSet` duplicates and `HashMap.get` returning `null` for an equal key.
- [ ] Test class `BrokenHashCodeDemoTest` asserts the *broken* behavior, with a 3-sentence comment explaining why

**2.5 🟡 Sealed results.** `sealed interface ReserveResult permits Reserved, InsufficientStock, UnknownSku` (records) + `String describe(ReserveResult)` with a pattern-matching `switch`.
- [ ] No `instanceof` chains; exhaustive `switch` without `default`; adding a fourth record breaks compilation (try it)

**2.6 🟡 Interface vs abstract class.** `Notifier` interface (`send(to, msg)`) with two implementations, plus `abstract class RetryingNotifier` holding `maxAttempts` and a template method.
- [ ] Written justification of which is an interface and which is abstract
- [ ] Test with a fake that fails twice then succeeds

**2.7 🟡 Parse or report.** `Optional<Integer> tryParse(String)` and `int parseOrThrow(String s, int line)` throwing an unchecked `ParseException` with line number and cause.
- [ ] `getCause()` is the `NumberFormatException`; no `catch (Exception e)`

**2.8 🟡 Immutable record with a collection.** `record Order(String id, List<OrderLine> lines)`.
- [ ] Mutating the list passed in does not change the record; `order.lines().add(...)` throws `UnsupportedOperationException`

**2.9 🟡 Inventory index.** Given `List<StockLevel>`, build `Map<String, Map<Long, Integer>>` (sku → warehouse → available) and `Map<Long, List<String>>` (warehouse → SKUs below a threshold, sorted).
- [ ] Uses `computeIfAbsent`, `merge`, `TreeMap` where order matters; no `containsKey` + `put` pairs

**2.10 🟡 LRU cache twice.** (a) `LinkedHashMap` with `accessOrder=true` + `removeEldestEntry`; (b) by hand: `HashMap<K, Node>` + doubly linked list with sentinels.
- [ ] Both pass one shared test suite (abstract test class or `@ParameterizedTest`); O(1) `get`/`put`

**2.11 🟡 Comparator chains.** Sort pick tasks (record: zone, priority, createdAt, id) by priority desc, zone asc, createdAt asc, id asc.
- [ ] One `Comparator` built with `comparing`/`thenComparing`/`reverseOrder`; no `a - b`; test with `Integer.MIN_VALUE` priorities

**2.12 🟡 Generic utilities.** `static <T extends Comparable<? super T>> T max(Collection<? extends T>)`, `static <K, V> Map<V, List<K>> invert(Map<K, V>)`, `record Pair<A, B>(A first, B second)`.
- [ ] No raw types or unchecked warnings; `max` works for `List<LocalDate>` (explain why `? super T` is required)

**2.13 🔴 HashMap from scratch.** `MyHashMap<K, V>`: separate chaining, power-of-two capacity, hash spreading, resize at 0.75, `null` key.
- [ ] 100 000 random ops compared against `java.util.HashMap` in a test
- [ ] A key class whose `hashCode()` returns a constant still passes (slowly) — explain why Java 8 treeifies

---

## Week 3 — Modern Java, I/O, debugging

Modules: [04-modern-java.md](./04-modern-java.md), [05-exceptions-io.md](./05-exceptions-io.md) Part B, [09-debugging-java.md](./09-debugging-java.md)

**3.1 🟢 Stream drills.** Given `List<Movement>` (record: sku, warehouseId, type RECEIVE/SHIP/ADJUST, qty, at): net quantity per SKU, movements per warehouse per day, largest single shipment per SKU, SKUs moved in every warehouse, average adjustment per warehouse.
- [ ] Each is one pipeline; at least one `groupingBy` with a downstream collector and a `TreeMap::new` supplier; one uses `teeing` or `partitioningBy`

**3.2 🟢 Optional refactor.** Rewrite `order.getCustomer().getAddress().getRegion().getCode()` with three nested null checks as an `Optional.map` chain.
- [ ] No `Optional.get()`; no `Optional` fields/parameters

**3.3 🟡 Enum with behavior.** `enum OrderStatus { CREATED, RESERVED, ALLOCATED, PICKED, PACKED, SHIPPED, CANCELLED }` with `boolean canTransitionTo(OrderStatus next)`.
- [ ] Transition table lives in the enum (`EnumMap`/`EnumSet`); illegal transitions throw `IllegalStateException` naming both states; a test enumerates all 49 pairs

**3.4 🟡 File stats CLI.** `java -jar stats.jar <dir>`: file count, total bytes, 5 largest files under a directory (`Files.walk`).
- [ ] Streams from `Files.walk` closed via try-with-resources; unreadable files reported, not fatal; tests use `@TempDir`

**3.5 🟡 CSV import with per-row errors.** Implement the kata `CsvImporter` from [05-exceptions-io.md §8](./05-exceptions-io.md#8-kata-csv-import-with-per-row-error-reporting) for a `date,sku,warehouse,qty` stock file.
- [ ] Fixture with 10 rows, 3 invalid (bad date, non-numeric qty, missing column) → 7 imported + 3 errors with correct line numbers
- [ ] Empty and header-only files handled

**3.6 🟡 Suppressed exceptions.** An `AutoCloseable` that throws on close; prove the body's exception is primary and close's is in `getSuppressed()`.
- [ ] Also show the manual try/finally version losing the primary exception

**3.7 🔴 Debugger kata.** Plant 3 bugs in 3.1 (off-by-one date filter, wrong comparator direction, `null` warehouse) — or swap with a friend.
- [ ] Each found with a conditional breakpoint, exception breakpoint, or Evaluate Expression (note which)
- [ ] Each fix comes with a failing-then-passing regression test

---

## Design patterns (read Week 2, applied in FlowGrid Weeks 4–6)

Module: [02-oop.md](./02-oop.md) Part B. Kata versions only — the project versions are yours to design in the milestones.

**D.1 🟡 Strategy.** `AllocationScorer` implementations (availability, region match, workload) combined with weights, deterministic tie-break by warehouse id.
- [ ] Adding a fourth scorer requires no change to the combining class (show it in a separate commit)
- [ ] Same inputs → same winner, every run (test with shuffled input order)

**D.2 🟡 Factory.** Detect a supplier CSV format from its header and return the matching parser; unknown header → descriptive exception.
- [ ] Two formats + unknown case tested

**D.3 🟡 Builder.** `ClientConfig.builder(apiKey).pollInterval(Duration).timeout(Duration).build()` — validation in `build()` (positive durations, timeout < poll interval).
- [ ] Built object immutable; invalid combinations fail at `build()` with a clear message (this is the shape FlagForge's SDK will need)

**D.4 🟡 Observer.** `LowStockMonitor` notifies listeners when `available` crosses a threshold downward — once per crossing, not on every change.
- [ ] Recording-listener test asserts exact notifications for a sequence of reserve/receive calls

**D.5 🔴 Composition over inheritance.** Reproduce the `CountingSet extends HashSet` double count; fix with a forwarding wrapper implementing `Set<E>`.
- [ ] Test shows 6 (broken) vs 3 (fixed) for `addAll(List.of(1, 2, 3))`

---

## Week 5 — Memory, JVM & thread basics

Modules: [06-memory-jvm.md](./06-memory-jvm.md), [07-concurrency.md](./07-concurrency.md) §1–3, §10

**5.1 🟢 Pass-by-value proof.** Tests for `reassign` vs `mutate` with an array, a `StringBuilder`, and an `Integer`.
- [ ] Each test has a comment drawing the stack/heap state

**5.2 🟢 Identity quiz.** 10 `==`/`equals` assertions on pooled/new strings and cached/uncached `Integer`s.
- [ ] Predictions written before running; mismatches explained

**5.3 🟡 Stack depth.** Measure max recursion depth with default stack and `-Xss4m`; convert a recursive DFS over a 1 000 000-node chain to an iterative `ArrayDeque` version.
- [ ] Iterative version completes without `StackOverflowError`

**5.4 🟡 GC and container memory.** Run an allocation-heavy program with `-Xlog:gc`, then inside `docker run --memory=256m` with and without `-XX:MaxRAMPercentage=75`.
- [ ] 5 lines interpreting the log; one sentence on what happens when heap max ≈ container limit

**5.5 🔴 Leak hunt.** A static cache growing per "request"; run with `-Xmx64m -XX:+HeapDumpOnOutOfMemoryError`.
- [ ] Heap dump opened in VisualVM or Eclipse MAT; GC-root path noted
- [ ] Fixed with the bounded LRU from 2.10; heap stays flat

**5.6 🟡 RaceDemo from memory.** Type [07-concurrency.md §2](./07-concurrency.md#2-race-condition--runnable-demo-and-three-fixes) without looking; run 5 times.
- [ ] Unsafe count < expected on a multi-core machine; the three fixes are exact
- [ ] Add a check-then-act version (`if (available >= 1) available--`) and show oversell; fix it — this is FlowGrid M2's bug in miniature

**5.7 🟡 Deadlock and cure.** Reproduce `DeadlockDemo`; capture `jcmd <pid> Thread.print`; fix with lock ordering by id.
- [ ] Dump excerpt with the cycle saved in notes
- [ ] Stress test (1 000 random transfers, 10 accounts, 8 threads) finishes and total balance is conserved — LedgerX M2's rule in miniature

---

## Week 15 — Concurrency deep dive

Module: [07-concurrency.md](./07-concurrency.md) §4–9. These katas rehearse the mechanics ForgeCI's worker needs; the worker itself is built in the milestone.

**15.1 🟡 Volatile stop flag.** Worker loop stopped from another thread; demonstrate (or explain) the hang without `volatile`.
- [ ] Final version stops within 100 ms of `stop()`; test uses `assertTimeoutPreemptively`

**15.2 🟡 Per-key counters.** `hit(String key)` from 16 threads × 10 000 calls with `ConcurrentHashMap<String, LongAdder>`.
- [ ] Exact totals; a `HashMap` + `containsKey`/`put` version demonstrates lost updates

**15.3 🟡 Bounded producer/consumer.** `ArrayBlockingQueue` (capacity 10), 2 producers, 3 consumers, poison-pill shutdown.
- [ ] Every item consumed exactly once (assert via a concurrent set); program exits cleanly; explain what happens to producers when the queue is full (backpressure)

**15.4 🟡 Parallel HTTP calls with timeouts.** 20 URLs fetched with `java.net.http.HttpClient` on a virtual-thread executor, 2 s timeout each; print status + latency.
- [ ] Wall time ≈ slowest call, not the sum; timeouts/connection errors reported per URL, not thrown
- [ ] Concurrency capped at 5 with a `Semaphore` in a second version

**15.5 🟡 CompletableFuture composition.** Combine two async lookups with `thenCombine`, `orTimeout`, fallback via `exceptionally`.
- [ ] Tests for success, one side failing, and timeout (fake that sleeps)

**15.6 🟡 Cancellable job runner.** Submit a long "job" (loop that checks `Thread.currentThread().isInterrupted()` and sleeps); cancel it via `Future.cancel(true)`; then implement a per-job timeout.
- [ ] Job observes interruption within 200 ms, cleans up in `finally`, and records CANCELLED vs TIMED_OUT distinctly

**15.7 🔴 Graceful shutdown + heartbeat.** A worker with a `ScheduledExecutorService` heartbeat every second and a job pool; on shutdown: stop taking jobs, wait up to N seconds for running ones, then interrupt, then stop the heartbeat.
- [ ] Test proves: in-flight job completes if it finishes within the grace period; otherwise it's interrupted; no thread leaks (`Thread.getAllStackTraces()` check)
- [ ] Explain how a lease + heartbeat lets *another* worker recover a job if this JVM is `kill -9`'d

---

## Progress summary

| Section | Exercises | Done |
|---|---:|---:|
| Week 1 | 7 | |
| Week 2 | 13 | |
| Week 3 | 7 | |
| Design patterns | 5 | |
| Week 5 | 7 | |
| Week 15 | 7 | |
| **Total** | **46** | |
