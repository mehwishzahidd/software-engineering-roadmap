# Java — Résumé Defense

**Target level:** L4 · **Learned:** Weeks 1–3 (core, OOP, collections, modern Java), 5 (memory model + threads for FlowGrid reservations), 15 (concurrency deep-dive for ForgeCI workers) · **Version:** Java 21 LTS
**Track:** B (résumé / backend). Coding interviews are Track A in Python; one solved problem per week is re-done in Java to keep collections fluent.
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md)

---

## 1. Beginner questions

<details><summary><b>Q1. What is the difference between the JDK, JRE and JVM?</b></summary>

JVM executes bytecode (class loading, JIT, GC). JRE = JVM + standard library needed to run
apps. JDK = JRE + dev tools (`javac`, `jshell`, `jar`, `jdb`). Since Java 11 there's no separate
JRE download from Oracle; you ship a JDK or a `jlink`-built runtime.
</details>

<details><summary><b>Q2. Is Java pass-by-value or pass-by-reference?</b></summary>

Always pass-by-value. For objects, the *value* passed is a copy of the reference. So a method
can mutate the object the reference points to, but reassigning the parameter doesn't affect the caller.
</details>

<details><summary><b>Q3. <code>==</code> vs <code>equals()</code>?</b></summary>

`==` compares primitive values or reference identity. `equals()` is logical equality as defined
by the class. `"a" == new String("a")` is `false`; `.equals` is `true`. Always use `equals` for
Strings, boxed numbers (`Integer` cache only covers −128..127), and domain objects.
</details>

<details><summary><b>Q4. Why must you override <code>hashCode</code> when you override <code>equals</code>?</b></summary>

Contract: equal objects must have equal hash codes. `HashMap`/`HashSet` first locate the bucket by
`hashCode`, then compare with `equals`. Break it and `set.contains(equalObject)` returns `false`.
Records generate both correctly.
</details>

<details><summary><b>Q5. Checked vs unchecked exceptions?</b></summary>

Checked (subclass `Exception` but not `RuntimeException`, e.g. `IOException`) must be declared or
caught — used for recoverable conditions outside the program's control. Unchecked
(`RuntimeException` subclasses, e.g. `IllegalArgumentException`) signal programming errors or
unrecoverable states. `Error` (e.g. `OutOfMemoryError`) shouldn't normally be caught.
</details>

<details><summary><b>Q6. Why is <code>String</code> immutable, and when do you use <code>StringBuilder</code>?</b></summary>

Immutability enables the string pool, safe sharing across threads, cached `hashCode`, and safe use
as map keys. Concatenating in a loop creates many intermediate strings (O(n²) copying), so use
`StringBuilder` (not thread-safe, fast) for loops.
</details>

<details><summary><b>Q7. Interface vs abstract class?</b></summary>

Interface: a contract; a class can implement many; can have `default`, `static`, and `private`
methods but no instance state. Abstract class: can hold state and constructors; single
inheritance. Prefer interfaces for capabilities (`InventoryRepository`), abstract classes for
sharing implementation among closely related types.
</details>

## 2. Intermediate questions

<details><summary><b>Q8. How does <code>HashMap</code> work internally?</b></summary>

Array of buckets (default 16, load factor 0.75). Index = `(n-1) & (h ^ (h >>> 16))`. Collisions
form a linked list; when a bucket reaches 8 entries and the table has ≥ 64 buckets it becomes a
red-black tree (O(log n) worst case). On exceeding capacity × load factor it resizes (doubles)
and redistributes. Average O(1) get/put. Not thread-safe; allows one `null` key.
</details>

<details><summary><b>Q9. <code>ArrayList</code> vs <code>LinkedList</code>?</b></summary>

`ArrayList`: backing array, O(1) random access, amortised O(1) append, O(n) middle insert,
cache-friendly. `LinkedList`: O(1) insert/remove at a known node, O(n) access, high per-node
overhead. In practice `ArrayList` (or `ArrayDeque` for queues/stacks) wins almost always.
</details>

<details><summary><b>Q10. What are records and when would you use them?</b></summary>

`record Money(BigDecimal amount, Currency currency) {}` — a final, shallowly-immutable data
carrier with generated constructor, accessors, `equals`/`hashCode`/`toString`. Use for DTOs and
value objects. Compact constructors validate invariants. Not suitable for JPA entities (need
no-arg constructor, mutability, proxies).
</details>

<details><summary><b>Q11. Explain streams: intermediate vs terminal operations, laziness.</b></summary>

Intermediate ops (`filter`, `map`, `sorted`) return a new stream and are lazy; nothing runs until
a terminal op (`collect`, `forEach`, `reduce`, `count`). Streams are single-use. Short-circuiting
ops (`findFirst`, `anyMatch`, `limit`) can stop early. Avoid side effects in lambdas.
</details>

<details><summary><b>Q12. What is <code>Optional</code> for, and how should it not be used?</b></summary>

A return type signalling "may be absent" (`findById`). Use `map`, `orElse`, `orElseThrow`. Don't
use it for fields, parameters, or collections (return an empty list instead), and avoid bare
`get()`.
</details>

<details><summary><b>Q13. Stack vs heap; what does the GC do?</b></summary>

Each thread has a stack of frames holding locals and references; objects live on the shared heap.
The GC reclaims objects unreachable from GC roots (stack references, statics, JNI). Default
collector in Java 21 is G1 (region-based, generational); ZGC is available for low-pause needs
(generational ZGC since 21). Memory "leaks" in Java = objects still reachable, e.g. a static map that grows forever.
</details>

<details><summary><b>Q14. What are virtual threads (Java 21)?</b></summary>

Lightweight threads managed by the JVM and mounted on a small pool of carrier (platform) threads.
Blocking I/O unmounts them, so you can have hundreds of thousands. Great for thread-per-request
I/O-bound work (`Executors.newVirtualThreadPerTaskExecutor()`). No benefit for CPU-bound work;
don't pool them; in Java 21 `synchronized` blocks around blocking I/O can pin the carrier (fixed in JDK 24), so prefer `ReentrantLock` there.
</details>

<details><summary><b>Q15. <code>synchronized</code> vs <code>ReentrantLock</code> vs atomics vs <code>ConcurrentHashMap</code>?</b></summary>

`synchronized`: intrinsic lock, mutual exclusion + visibility, simple. `ReentrantLock`: same plus
`tryLock`, timeouts, fairness, interruptible. `AtomicInteger`/`AtomicLong`: lock-free CAS for single
variables. `ConcurrentHashMap`: thread-safe map with fine-grained locking; use `compute`/`merge`
for atomic read-modify-write instead of `get`-then-`put`.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "Your résumé says Java. Walk me through the most recent Java code you've written."</b></summary>

- One sentence truthful past context: which roles used Java, what kind of code (e.g. service layer, batch jobs).
- Then: "Most recently I've been writing Java 21 daily." Pick one: FlowGrid's allocation engine
  (deterministic scoring — availability, capacity, zone match, workload, priority — as a `Comparator`
  chain with an id tie-break), LedgerX's `BigDecimal` journal (every transaction sums to zero), or
  ForgeCI's worker loop (`ExecutorService`, per-job timeout via `Future.get(timeout)`, cancellation
  flag, `finally` container cleanup, graceful shutdown).
- One design decision + why (e.g. `BigDecimal` for money, never `double`).
- Offer to show the code / tests.
</details>

<details><summary><b>R2. "When did you last use Java professionally?"</b></summary>

- State the year and role honestly (template A in the method file).
- "Since then I've rebuilt on Java 21 — records, sealed interfaces, pattern matching for `switch`, virtual threads."
- Point to FlowGrid / LedgerX evidence. Don't apologise; don't pad.
</details>

<details><summary><b>R3. "What's new in Java since 8 that you actually use?"</b></summary>

- `var` (10), records (16), text blocks (15), switch expressions (14), pattern matching for
  `instanceof` (16), sealed classes (17), pattern matching for `switch` + record patterns (21),
  virtual threads (21), sequenced collections (21), `HttpClient` (11).
- Name where you used 2–3 of them (records for DTOs in FlowGrid; sealed interface + pattern-matching `switch` for ForgeCI's `JobOutcome` — `Succeeded | Failed(exitCode) | InfraError(cause) | Cancelled | TimedOut`; virtual threads considered for ForgeCI's log streaming).
</details>

<details><summary><b>R4. "How would you make this class thread-safe?"</b> (they show a counter/cache)</summary>

- Identify shared mutable state and the compound action (check-then-act, read-modify-write).
- Options in order of preference: make it immutable → confine to one thread → use a concurrent
  structure (`ConcurrentHashMap.merge`, `AtomicLong`) → lock (`synchronized`/`ReentrantLock`).
- Mention visibility (`volatile`/happens-before), and how you'd test it (N threads + `CountDownLatch`, as in FlowGrid M2's one-unit reservation test and LedgerX M2's $500 / $400 / $400 transfer test).
</details>

<details><summary><b>R5. "Why <code>BigDecimal</code> for money? Any gotchas?"</b></summary>

- Binary floating point can't represent 0.1 exactly → rounding errors accumulate.
- Gotchas: construct with `new BigDecimal("0.10")` or `BigDecimal.valueOf(0.1)`, not `new BigDecimal(0.1)`;
  `equals` compares scale (`2.0` ≠ `2.00`) — use `compareTo`; division needs a `RoundingMode`.
- Evidence: LedgerX — amounts as `BigDecimal` mapped to `NUMERIC(19,4)`, compared with `compareTo`, and an invariant test that every journal transaction's entries sum to exactly `BigDecimal.ZERO`.
</details>

<details><summary><b>R6. "Tell me about a hard bug you debugged in Java."</b></summary>

- Use a real one. Truthful past bug if you remember it clearly; otherwise one from the roadmap
  (e.g. FlowGrid's double-reservation race, found by the N-threads-one-unit test, fixed with `SELECT … FOR UPDATE` after measuring against `@Version`; or ForgeCI's orphaned job after a worker was killed mid-run, fixed with lease expiry + heartbeats).
- STAR shape: symptom → hypothesis → tool (debugger, thread dump, logs) → root cause → fix → regression test.
</details>

## 4. Practical tasks (do these live, no notes)

- [ ] Implement an `LRUCache<K,V>` two ways: `LinkedHashMap` with `removeEldestEntry`, and HashMap + doubly linked list.
- [ ] Group a `List<LedgerEntry>` by account and sum amounts with `Collectors.groupingBy(..., reducing(BigDecimal.ZERO, ..., BigDecimal::add))`; assert each journal transaction nets to zero.
- [ ] Write a correct `equals`/`hashCode` for a class, then replace it with a record.
- [ ] Parse a CSV file with `Files.lines` in try-with-resources, reporting per-row errors without stopping.
- [ ] Run 1,000 increments from 10 threads on a counter; show the race; fix it with `AtomicInteger`.
- [ ] Use `ExecutorService` + `invokeAll` (or virtual threads) to fetch 5 URLs with a timeout.

## 5. Debugging questions

<details><summary><b>D1. <code>NullPointerException</code> in production logs — how do you diagnose?</b></summary>

Read the helpful NPE message (Java 14+ says which variable was null) and the top frame in your
code. Reproduce with a test. Find where the null originates, not where it blew up. Fix at the
boundary (validation, `Objects.requireNonNull`, `Optional` return) and add the regression test.
</details>

<details><summary><b>D2. <code>ConcurrentModificationException</code> in a loop.</b></summary>

You structurally modified a collection while iterating with a fail-fast iterator. Fix: `Iterator.remove()`,
`list.removeIf(...)`, collect then modify, or a concurrent collection if multiple threads are involved.
</details>

<details><summary><b>D3. The app's memory grows until <code>OutOfMemoryError: Java heap space</code>.</b></summary>

Capture a heap dump (`-XX:+HeapDumpOnOutOfMemoryError` or `jcmd <pid> GC.heap_dump`), open in
Eclipse MAT / VisualVM, look at dominator tree for what's retaining memory — often an unbounded
static cache/map or listeners never removed. Fix with bounded caches/eviction.
</details>

<details><summary><b>D4. A service hangs; CPU is near zero.</b></summary>

Likely deadlock or threads blocked on I/O/locks. Take a thread dump (`jcmd <pid> Thread.print` or
`jstack`). Look for "Found one Java-level deadlock" or many threads `BLOCKED`/`WAITING` on the same
monitor or waiting on a pool/connection. Fix lock ordering, add timeouts.
</details>

<details><summary><b>D5. A <code>HashSet</code> contains an object but <code>contains()</code> returns false.</b></summary>

Either `equals`/`hashCode` not overridden consistently, or a field used in `hashCode` was mutated
after insertion (object now in the "wrong" bucket). Use immutable keys (records).
</details>

## 6. Architecture questions

- How would you structure FlowGrid's allocation logic so the scoring strategy can change without touching the fulfillment workflow? (Strategy interface, dependency inversion, tests against a fake inventory repository.)
- When would you pick composition over inheritance? Give an example from your code.
- Sealed interface + records for a result type (`sealed interface JobOutcome permits Succeeded, Failed, InfraError, Cancelled, TimedOut` in ForgeCI — the retry policy switches on it) vs exceptions — trade-offs?
- Platform threads with a bounded pool vs virtual threads for ForgeCI's worker (each job blocks on a Docker container) and for FlagForge's SDK polling client — what limits concurrency in each case (Docker daemon, DB connections, downstream)?
- How do you design an immutable value object, and why is immutability valuable under concurrency?

## 7. Common mistakes

- Using `double` for money; `new BigDecimal(0.1)`.
- Comparing strings/boxed integers with `==`.
- Catching `Exception` and swallowing it (`catch (Exception e) {}`) or logging and rethrowing twice.
- Mutating a collection while iterating; returning internal mutable lists from getters.
- `Optional.get()` without checking; `Optional` fields.
- Assuming `HashMap` is thread-safe; check-then-act on a `ConcurrentHashMap`.
- Forgetting try-with-resources for streams/connections.

## 8. Terminology I must know

| Term | One-line meaning |
|---|---|
| Bytecode | Platform-independent instructions `javac` produces for the JVM |
| JIT | Compiles hot bytecode to native code at runtime |
| GC roots | Starting references (stack, statics) from which reachability is traced |
| Autoboxing | Automatic conversion between primitives and wrappers |
| Generics / type erasure | Compile-time type parameters removed at runtime |
| Functional interface | Interface with one abstract method; target for lambdas |
| Record | Immutable data class with generated members |
| Sealed class | Restricts which classes may extend/implement it |
| Happens-before | JMM rule that guarantees visibility/order between threads |
| `volatile` | Guarantees visibility of writes across threads, not atomicity |
| Race condition | Outcome depends on thread interleaving |
| Deadlock | Threads each wait on a lock the other holds |
| Virtual thread | JVM-scheduled lightweight thread (Java 21) |
| Checked exception | Must be declared or handled at compile time |

## 9. When to use it

Backend services, APIs, batch processing, anything needing a mature ecosystem (Spring,
JDBC drivers, observability), strong typing, and long-term maintainability in teams. The résumé /
software-engineering interview track (Track B). Coding interviews are Python (Track A) — say so if asked which language you'd code in.

## 10. When NOT to use it

Tiny scripts/glue (Python/Bash is faster to write), browser UIs (TypeScript), extremely
low-latency/embedded with tight memory (C/C++/Rust), serverless functions where cold start
dominates (unless using CRaC/GraalVM native image).

## 11. Trade-offs

| Gain | Cost |
|---|---|
| Static typing, great tooling, refactor safety | Verbosity vs Kotlin/Python (reduced by records, `var`) |
| Mature, fast JIT, excellent GC | Higher memory footprint and startup time |
| Huge ecosystem (Spring, Hibernate) | Framework "magic" can hide behaviour |
| Virtual threads make blocking code scale | Pinning pitfalls; still need back-pressure |

## 12. How it interacts with the rest of my stack

- **Spring Boot**: Java is the language; Spring manages object lifecycles (beans) via reflection/proxies.
- **Maven**: compiles, tests, packages Java into a JAR; sets `maven.compiler.release=21`.
- **PostgreSQL**: via JDBC driver → HikariCP pool → JPA/Hibernate.
- **React**: consumes JSON the Java API produces (Jackson serialises records/DTOs).
- **Docker**: multi-stage build (JDK to build, JRE base like `eclipse-temurin:21-jre` to run).
- **AWS**: JAR runs in a container on EC2; heap sized via `-XX:MaxRAMPercentage`.
- **CI**: GitHub Actions `setup-java` with Temurin 21 → `mvn verify`.

## 13. Hands-on exercise

**Build `RateCounter`**: a thread-safe class that records events per key and returns counts for the last 60 seconds (the shape of ForgeCI's per-project concurrency accounting and FlagForge's evaluation counters).

Acceptance criteria:
- [ ] `record(String key)` and `count(String key)` are safe under 50 concurrent threads (JUnit test with `ExecutorService` + `CountDownLatch` passes 20 runs in a row).
- [ ] Uses `ConcurrentHashMap` + `ConcurrentLinkedDeque<Instant>` or equivalent; no global `synchronized`.
- [ ] Time is injected via `java.time.Clock` so the test is deterministic.
- [ ] You can explain in 60 s why a naive `HashMap<String,Integer>` version fails.

## 14. Mastery checklist

- [ ] Explain JDK/JRE/JVM, stack/heap, GC roots, and G1 in 2 minutes.
- [ ] Explain `HashMap` internals including treeification and resize.
- [ ] Write `equals`/`hashCode`/`compareTo` correctly from memory.
- [ ] Use streams + collectors fluently (`groupingBy`, `toMap` with merge fn, `partitioningBy`).
- [ ] Explain and demonstrate a race condition and three ways to fix it.
- [ ] Explain virtual threads and when they don't help.
- [ ] Read a stack trace and thread dump and find the root cause.
- [ ] Name 6 post-Java-8 features and where I used them.
- [ ] Re-implement one already-solved problem per week in Java with idiomatic collections (`HashMap`, `ArrayDeque`, `PriorityQueue`, `Comparator`) — the Java DSA rep from ROADMAP §7.

## Evidence in my projects

| Project | What it demonstrates | Fill in: file / commit |
|---|---|---|
| FlowGrid | Domain model (warehouses, SKUs, inventory levels by state, orders, reservations, shipments) with enum state machines and validated transitions; records as DTOs; exception hierarchy → ProblemDetail; deterministic allocation `Comparator`; N-threads-one-unit concurrency test | |
| LedgerX | `BigDecimal` money everywhere (scale, `compareTo`, `RoundingMode`), immutable ledger entries, invariant suite (every journal txn sums to zero), ordered lock acquisition, idempotency fingerprint hashing | |
| ForgeCI | Worker concurrency: `ExecutorService`, `CompletableFuture`, per-job timeout, cancellation flag, `finally` cleanup, graceful shutdown, heartbeats; sealed `JobOutcome`; docker-java client usage | |
| FlagForge | Deterministic bucketing (`hash(flagKey:userKey) mod 10000`), rule engine (priority → first match), Java SDK: builder pattern, in-memory snapshot, polling thread, stale-if-error, timeouts, no Spring dependency | |

## Where to learn it in this repo

[`../01-java/README.md`](../01-java/README.md) · [`../01-java/01-syntax-basics.md`](../01-java/01-syntax-basics.md) ·
[`../01-java/02-oop.md`](../01-java/02-oop.md) · [`../01-java/03-collections-generics.md`](../01-java/03-collections-generics.md) ·
[`../01-java/04-modern-java.md`](../01-java/04-modern-java.md) · [`../01-java/05-exceptions-io.md`](../01-java/05-exceptions-io.md) ·
[`../01-java/06-memory-jvm.md`](../01-java/06-memory-jvm.md) · [`../01-java/07-concurrency.md`](../01-java/07-concurrency.md) ·
[`../01-java/09-debugging-java.md`](../01-java/09-debugging-java.md) · [`../01-java/interview-questions.md`](../01-java/interview-questions.md) ·
[`../14-cs-fundamentals/concurrency.md`](../14-cs-fundamentals/concurrency.md)
