# Java Interview Questions — 70 Q&A (Track B)

> **Which interview is this for?** Java is your **backend / Spring Boot / project / résumé-interview**
> language (Track B in [ROADMAP §10](../ROADMAP.md#10-interview-practice-ramp)). Coding rounds and OAs
> are solved in **Python** (Track A — [../19-python/README.md](../19-python/README.md),
> [../PYTHON_INTERVIEW_CHEATSHEET.md](../PYTHON_INTERVIEW_CHEATSHEET.md)). These questions are what an
> interviewer asks after reading "Java" on your résumé or while you walk through FlowGrid, LedgerX,
> ForgeCI or FlagForge. Java collections fluency for the occasional Java DSA rep is covered in
> [03-collections-generics.md](./03-collections-generics.md#java-for-occasional-dsa-reps).

> How to use: read the question, **answer out loud in under 2 minutes**, then open the answer.
> Mark weak ones in [../trackers/interview-tracker.md](../trackers/interview-tracker.md) and
> revisit them on the spaced schedule (Day 0/3/7/14/30). Wherever possible, end your spoken
> answer with *"for example, in FlowGrid / LedgerX / ForgeCI / FlagForge I…"* — only if you actually built it.
> Deeper per-module questions live at the bottom of each module file.

| Group | Questions | Module |
|---|---|---|
| [A. Language fundamentals](#a-language-fundamentals) | 1–12 | [01](./01-syntax-basics.md) |
| [B. OOP & design](#b-oop--design) | 13–24 | [02](./02-oop.md) |
| [C. Collections & generics](#c-collections--generics) | 25–36 | [03](./03-collections-generics.md) |
| [D. Modern Java](#d-modern-java) | 37–44 | [04](./04-modern-java.md) |
| [E. Exceptions & I/O](#e-exceptions--io) | 45–50 | [05](./05-exceptions-io.md) |
| [F. Memory & JVM](#f-memory--jvm) | 51–58 | [06](./06-memory-jvm.md) |
| [G. Concurrency](#g-concurrency) | 59–66 | [07](./07-concurrency.md) |
| [H. Build, debugging & practice](#h-build-debugging--practice) | 67–70 | [08](./08-maven-build.md), [09](./09-debugging-java.md) |

---

## A. Language fundamentals

<details><summary>1. What are the primitive types in Java and their sizes?</summary>

`byte` 8, `short` 16, `int` 32, `long` 64, `float` 32, `double` 64, `char` 16 (unsigned UTF-16
unit), `boolean` (size JVM-dependent). Everything else is a reference type.
</details>

<details><summary>2. Why is Java "platform independent"?</summary>

`javac` produces bytecode for the JVM, not native code. Each platform has its own JVM that
interprets/JIT-compiles the same bytecode. The JVM is platform-specific; the `.class` files aren't.
</details>

<details><summary>3. What does <code>final</code> mean on a variable, a method, a class?</summary>

Variable: assigned once (the reference, not the object, for reference types). Method: can't be
overridden. Class: can't be extended (`String`, records).
</details>

<details><summary>4. <code>static</code> — what does it mean and what can't a static method do?</summary>

Belongs to the class, not an instance; one copy per class (per classloader). Static methods have no
`this`, so they can't access instance members directly and can't be overridden (only hidden).
</details>

<details><summary>5. <code>==</code> vs <code>equals</code>?</summary>

`==` on primitives compares values; on references compares identity. `equals` compares logical
equality as defined by the class (default: identity). Use `equals` for strings, wrappers, value objects.
</details>

<details><summary>6. Why is String immutable and what is the String pool?</summary>

Immutability gives thread safety, cacheable hash codes (fast map keys), security, and allows
literal sharing. The pool (in the heap) holds one instance per distinct literal/interned string;
literals and compile-time constants are pooled, `new String(...)` and runtime concatenations aren't.
</details>

<details><summary>7. String vs StringBuilder vs StringBuffer?</summary>

`String` immutable. `StringBuilder` mutable, not synchronized — use for building strings in loops.
`StringBuffer` mutable and synchronized — legacy, rarely needed.
</details>

<details><summary>8. What happens when an int overflows?</summary>

Silent wraparound in two's complement (`MAX_VALUE + 1 == MIN_VALUE`). Use `long`,
`Math.addExact`, or `BigInteger`; in binary search use `lo + (hi - lo) / 2`.
</details>

<details><summary>9. Is Java pass-by-value or pass-by-reference?</summary>

Always pass-by-value. For objects, the reference is copied: the callee can mutate the object but
can't make the caller's variable point to a different object.
</details>

<details><summary>10. What is autoboxing, and what can go wrong?</summary>

Automatic conversion between primitives and wrappers (`Integer.valueOf`, `intValue`). Pitfalls:
NPE when unboxing `null`, `==` on wrappers outside the −128..127 cache returns false, hidden
allocations in hot loops (`Long sum += i`).
</details>

<details><summary>11. What's new in switch since Java 14/21?</summary>

Switch **expressions** (arrow cases, no fall-through, `yield`, exhaustiveness for enums/sealed
types) and, in Java 21, **pattern matching** in switch (type patterns, record patterns, `when`
guards, `case null`).
</details>

<details><summary>12. What does <code>var</code> do?</summary>

Local-variable type inference: the compiler infers the static type from the initializer. It's
still statically typed; only for locals (and lambda params, for-loop variables); not for fields,
parameters or return types.
</details>

## B. OOP & design

<details><summary>13. Explain the four OOP principles with examples.</summary>

Encapsulation (private state + invariant-enforcing methods), abstraction (interfaces like
`TransactionRepository`), inheritance (is-a reuse), polymorphism (one call, runtime-selected
implementation). Tie each to your code.
</details>

<details><summary>14. Interface vs abstract class?</summary>

Interface: contract, multiple implementation, no instance state, default/static/private methods.
Abstract class: shared state + constructors + partial implementation, single inheritance. Default to
interfaces; use abstract classes for closely related types sharing state.
</details>

<details><summary>15. Overloading vs overriding?</summary>

Overloading: same name, different parameter lists, resolved at compile time by static types.
Overriding: same signature in a subclass, resolved at runtime by the object's class (dynamic dispatch).
</details>

<details><summary>16. What's the equals/hashCode contract?</summary>

Equal objects ⇒ equal hash codes. `equals` reflexive, symmetric, transitive, consistent,
`equals(null) == false`. Override both together using the same fields; otherwise hash collections break.
</details>

<details><summary>17. What happens if you override equals but not hashCode?</summary>

Two equal objects likely get different identity hash codes, land in different buckets, and a
`HashSet` stores both; `map.get(equalKey)` returns `null`.
</details>

<details><summary>18. How do you create an immutable class?</summary>

Final class, private final fields, no setters, validate in constructor, defensive copies of
mutable inputs/outputs, don't leak `this`. Or a record with immutable components (copy collections).
</details>

<details><summary>19. Composition vs inheritance — which and why?</summary>

Prefer composition: depends only on an interface, swappable at runtime, testable, avoids the
fragile-base-class problem (e.g. `HashSet.addAll` calling `add`). Use inheritance for true is-a
hierarchies designed for extension.
</details>

<details><summary>20. Explain SOLID briefly.</summary>

SRP one reason to change; OCP extend without modifying; LSP subtypes substitutable; ISP small
interfaces; DIP depend on abstractions, inject dependencies. Example: FlowGrid's allocation scorers (OCP),
repositories and clocks injected into services (DIP).
</details>

<details><summary>21. What is dependency injection and why does it help testing?</summary>

Objects receive their collaborators (usually via constructor) instead of creating them. Tests can
pass fakes/mocks; production wiring is done elsewhere (manually in the Week 2 katas, by Spring's container in every project).
</details>

<details><summary>22. Name design patterns you've used and why.</summary>

Kata level: Strategy (categorization rules), Factory (bank CSV formats), Builder (Budget), Observer
(alerts). Project level, once built: Strategy for FlowGrid's allocation scoring, state machines for
reservations/shipments, Repository everywhere, Builder for FlagForge's `FlagClient`. Say the problem each solved, not just the name.
</details>

<details><summary>23. What are records? Limitations?</summary>

Transparent immutable data carriers with generated constructor, accessors, `equals`/`hashCode`/`toString`.
Final, can't extend classes (can implement interfaces), fields final; not suitable for JPA entities.
</details>

<details><summary>24. What are sealed classes?</summary>

Classes/interfaces that restrict which types may extend them (`permits`). Enable exhaustive pattern
matching and model closed sets of variants (results, events).
</details>

## C. Collections & generics

<details><summary>25. How does HashMap work internally?</summary>

Power-of-two array of bins; index `(n-1) & (h ^ h>>>16)`; collisions chained; bins with ≥ 8
nodes treeify to red-black trees if capacity ≥ 64; resize ×2 when size > 0.75 × capacity,
splitting bins into `i` / `i+oldCap`. Average O(1).
</details>

<details><summary>26. What changed in HashMap in Java 8?</summary>

Treeification of long bins (O(log n) worst case instead of O(n)), simplified hash spreading, and
order-preserving split during resize (fixing the infinite-loop-on-concurrent-resize issue of Java 7, though it's still not thread-safe).
</details>

<details><summary>27. HashMap vs Hashtable vs ConcurrentHashMap?</summary>

HashMap: unsynchronized, allows one null key and null values. Hashtable: legacy, every method
synchronized, no nulls. ConcurrentHashMap: fine-grained CAS + per-bin locking, lock-free reads, no nulls — the right choice for concurrency.
</details>

<details><summary>28. ArrayList vs LinkedList?</summary>

ArrayList: array-backed, O(1) get, amortized O(1) append, O(n) middle insert, cache-friendly.
LinkedList: O(n) get, O(1) insert/remove at a known node, heavy per-node overhead. ArrayList almost always.
</details>

<details><summary>29. How does ArrayList grow?</summary>

Default capacity 10 on first add; when full, new capacity = old + old/2, elements copied with
`Arrays.copyOf`. Geometric growth makes append amortized O(1).
</details>

<details><summary>30. HashSet vs TreeSet vs LinkedHashSet?</summary>

HashSet: backed by HashMap, O(1), unordered. TreeSet: red-black tree, O(log n), sorted, navigation
methods, uses `compareTo`/comparator for equality. LinkedHashSet: insertion order, O(1).
</details>

<details><summary>31. What is a fail-fast iterator?</summary>

Iterator that throws `ConcurrentModificationException` if the collection is structurally modified
other than via the iterator (detected via `modCount`). Best-effort bug detection, not thread-safety.
Concurrent collections have weakly consistent iterators instead.
</details>

<details><summary>32. Comparable vs Comparator?</summary>

Comparable: natural order inside the class (`compareTo`). Comparator: external strategy objects,
composable (`comparing().thenComparing().reversed()`). Avoid `a - b` (overflow); keep consistent with equals for sorted sets/maps.
</details>

<details><summary>33. Which collection for a stack, a queue, and a priority queue in Java?</summary>

`ArrayDeque` for both stack (`push/pop/peek`) and queue (`offer/poll/peek`); `PriorityQueue` (min-heap
by default; `Comparator.reverseOrder()` for max). Not `Stack`/`Vector`.
</details>

<details><summary>34. What is type erasure?</summary>

Generic type info is removed at compile time; runtime uses raw types with inserted casts. So no
`new T()`, no generic arrays, no `instanceof List<String>`, no primitive type args, no overloads differing only by type args.
</details>

<details><summary>35. What does <code>? extends T</code> vs <code>? super T</code> mean?</summary>

Upper-bounded (read T's out, can't add) vs lower-bounded (can add T's, read as Object). PECS:
producer extends, consumer super.
</details>

<details><summary>36. List.of vs Arrays.asList vs Collections.unmodifiableList?</summary>

`List.of`: truly immutable, no nulls. `Arrays.asList`: fixed-size view backed by the array (set
ok, add/remove throw). `unmodifiableList`: read-only view of a list that may still change underneath.
</details>

## D. Modern Java

<details><summary>37. What is a lambda and a functional interface?</summary>

A lambda is an anonymous function implementing a functional interface (one abstract method), e.g.
`Predicate<T>`, `Function<T,R>`. Compiled via `invokedynamic`; captures effectively final locals.
</details>

<details><summary>38. Explain stream laziness.</summary>

Intermediate operations build a pipeline; nothing executes until a terminal op. Elements then flow
one at a time through fused stages, and short-circuiting ops (`findFirst`, `limit`, `anyMatch`) stop early.
</details>

<details><summary>39. map vs flatMap?</summary>

`map` is 1:1 transformation. `flatMap` maps each element to a stream and flattens the results (or
flattens nested `Optional`s).
</details>

<details><summary>40. How would you group transactions by category and sum amounts?</summary>

`txs.stream().collect(groupingBy(Transaction::category, reducing(BigDecimal.ZERO, Transaction::amount, BigDecimal::add)))`
— or `summingLong` if amounts are cents.
</details>

<details><summary>41. How should Optional be used?</summary>

Return type for possibly-absent results. Chain `map`/`filter`/`orElse`/`orElseThrow`; avoid `get`;
not for fields/params/collections; `orElseGet` for expensive defaults.
</details>

<details><summary>42. Are streams always faster than loops?</summary>

No. Usually similar; sometimes slower (boxing, overhead on small data). Parallel streams help only for
big CPU-bound splittable work. Choose for readability; measure for performance.
</details>

<details><summary>43. Why can't a lambda modify a local variable?</summary>

Captured locals must be effectively final because the lambda captures a copy and may run later or
on another thread. Use reductions/collectors instead of mutating outer state.
</details>

<details><summary>44. What are enums in Java capable of?</summary>

Full classes with fields, constructors, methods, per-constant bodies, implementing interfaces;
singleton constants; `values()`/`valueOf`; `EnumMap`/`EnumSet`; exhaustive switches. Persist by name, not ordinal.
</details>

## E. Exceptions & I/O

<details><summary>45. Checked vs unchecked exceptions?</summary>

Checked (extend `Exception`) must be caught or declared — external recoverable conditions.
Unchecked (`RuntimeException`) — programming errors/domain errors, no compiler requirement. Modern
code uses unchecked broadly and translates checked ones at boundaries.
</details>

<details><summary>46. What is try-with-resources and why is it better than finally?</summary>

Auto-closes `AutoCloseable` resources in reverse order; if both body and close throw, the body's
exception propagates with close's as suppressed. Manual `finally` can mask the original exception and is verbose.
</details>

<details><summary>47. final vs finally vs finalize?</summary>

`final`: modifier (no reassignment/override/extension). `finally`: block that always runs after
try/catch. `finalize`: deprecated GC callback — don't use; use try-with-resources or `Cleaner`.
</details>

<details><summary>48. How do you design custom exceptions?</summary>

Unchecked by default, domain-meaningful names (`InsufficientStockException`), include context (ids,
values), always accept/pass a cause when wrapping, prefer standard exceptions when they fit.
</details>

<details><summary>49. How do you read a large file efficiently?</summary>

Stream it: `Files.newBufferedReader` line by line or `Files.lines` in try-with-resources — O(1)
memory instead of `readAllLines`. Always specify UTF-8.
</details>

<details><summary>50. What should you never do with exceptions?</summary>

Swallow them silently, catch `Throwable`/`Exception` broadly, lose the cause when wrapping, use them
for normal control flow, log-and-rethrow at every layer.
</details>

## F. Memory & JVM

<details><summary>51. Stack vs heap?</summary>

Stack: per-thread frames with locals/references, LIFO, auto-freed, `StackOverflowError`. Heap: all
objects, shared across threads, GC-managed, `OutOfMemoryError`.
</details>

<details><summary>52. What are the JVM memory areas?</summary>

Heap (young: Eden + 2 survivors; old), Metaspace (class metadata, native), code cache (JIT output),
per-thread stacks, PC registers, native method stacks, plus direct buffers.
</details>

<details><summary>53. How does generational GC work?</summary>

Allocate in Eden; minor GC copies live objects to a survivor space, aging them; long-lived objects
promote to old gen; old gen collected less often (mixed/full GC). Efficient because most objects die young.
</details>

<details><summary>54. Which GC does Java 21 use by default, and what else is there?</summary>

G1 (region-based, pause-time goals) on server-class machines. Alternatives: ZGC (sub-ms pauses,
generational mode in 21), Parallel (throughput), Serial (small heaps).
</details>

<details><summary>55. Can Java leak memory? Example?</summary>

Yes: reachable-but-unused objects — static maps without eviction, listeners never removed,
ThreadLocals in pools, inner classes capturing outer instances.
</details>

<details><summary>56. What is the JIT and why do benchmarks need warm-up?</summary>

Just-in-time compiler turns hot bytecode into optimized native code (tiered C1/C2) using runtime
profiles. Early iterations are interpreted/compiling, so naive timings mislead; use JMH.
</details>

<details><summary>57. What is classloading and parent delegation?</summary>

Loading → linking (verify, prepare, resolve) → initialization (static init, once, thread-safe).
Loaders (bootstrap → platform → application) delegate to the parent first, preventing core class spoofing.
</details>

<details><summary>58. What's the Integer cache?</summary>

`Integer.valueOf` returns cached instances for −128..127, so `==` is true there and false outside.
Always compare wrappers with `equals`.
</details>

## G. Concurrency

<details><summary>59. Process vs thread?</summary>

A process has its own address space; threads within a process share heap and resources but have
their own stacks and registers. Threads are cheaper to create and switch, but shared memory requires synchronization.
</details>

<details><summary>60. What's a race condition and how do you fix it?</summary>

Result depends on interleaving (e.g. `count++`, check-then-act). Fix with locks (`synchronized`,
`ReentrantLock`), atomics, atomic collection ops (`putIfAbsent`, `merge`), or eliminate shared mutable state.
</details>

<details><summary>61. volatile vs synchronized?</summary>

`volatile`: visibility + ordering for a single variable, no mutual exclusion. `synchronized`:
mutual exclusion + visibility for a block. `count++` needs the latter (or an atomic).
</details>

<details><summary>62. What is happens-before?</summary>

JMM ordering guaranteeing visibility: program order, unlock→lock, volatile write→read,
`start`/`join`, executor handoff, transitivity. Without it, reads can see stale data.
</details>

<details><summary>63. What is a deadlock, and how do you prevent and detect it?</summary>

Circular wait between threads each holding a lock the other needs. Prevent via global lock ordering,
`tryLock` with timeout, fewer/coarser locks. Detect with `jstack`/`jcmd Thread.print` ("Found one Java-level deadlock").
</details>

<details><summary>64. Why use an ExecutorService instead of new Thread()?</summary>

Thread reuse, bounded concurrency, queuing, futures for results/exceptions, graceful shutdown,
central configuration and naming. (With virtual threads, `newVirtualThreadPerTaskExecutor` gives per-task threads with the same API.)
</details>

<details><summary>65. What are virtual threads?</summary>

Java 21 lightweight threads scheduled by the JVM onto carrier threads; they unmount while blocked,
so millions of blocking tasks are feasible. Great for I/O-bound thread-per-request code; not faster
for CPU work; don't pool; watch `synchronized` pinning on 21.
</details>

<details><summary>66. How is ConcurrentHashMap thread-safe without locking the whole map?</summary>

CAS for empty-bin inserts, `synchronized` on individual bin heads for updates, volatile reads,
cooperative resizing. Compound ops like `compute`/`merge` are atomic per key.
</details>

## H. Build, debugging & practice

<details><summary>67. Explain the Maven lifecycle and scopes in one minute.</summary>

validate → compile → test → package → integration-test → verify → install → deploy; a phase runs all
before it. Scopes: compile, provided, runtime, test, import. CI runs `./mvnw -B verify`.
</details>

<details><summary>68. How do you resolve a NoSuchMethodError at runtime?</summary>

Almost always a dependency version conflict: `mvn dependency:tree -Dverbose`, find which version won
(nearest-wins), align with `dependencyManagement`/BOM or exclusion, rebuild and retest.
</details>

<details><summary>69. How do you debug a failing test in a codebase you don't know?</summary>

Run just that test, read the assertion and trace to the last `Caused by`, find the first frame in
project code, set a breakpoint there (conditional if in a loop), inspect state against expectations,
narrow to the wrong line, fix the root cause, run the full suite. See [09-debugging-java.md](./09-debugging-java.md).
</details>

<details><summary>70. What Java version do you use and what features do you rely on?</summary>

Java 21 LTS: records for DTOs/value objects, sealed interfaces + pattern-matching switch for result
types, `var` sparingly, text blocks for SQL, streams/Optional, `java.time`, virtual threads for the
ForgeCI worker pool. Be ready to show each in your projects.
</details>

---

## Rapid-fire (answer in one sentence each)

- Default value of an `int` field? of a local `int`? · Can a constructor be `private`? Why would it be? · Can an interface have a constructor? · What does `transient` do? · What is `instanceof` pattern matching? · Is `char` signed? · What does `Objects.requireNonNull` return? · What's the output of `"a" + 1 + 2` vs `1 + 2 + "a"`? · What's `hashCode` of an empty `String`? (0) · Which is bigger: `Integer.MIN_VALUE` or `-Integer.MIN_VALUE`? (equal — overflow)
