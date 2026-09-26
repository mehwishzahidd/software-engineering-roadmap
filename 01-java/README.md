# ☕ 01 — Java 21 (Backend, Projects & Résumé-Interview Language)

> **Java's role in this roadmap:** Java is the language of **every flagship project backend**
> (FlowGrid, LedgerX, ForgeCI, FlagForge), of Spring Boot, and of the **software-engineering /
> résumé interview** (Track B in [ROADMAP §10](../ROADMAP.md#10-interview-practice-ramp)): project
> deep-dives, concurrency, testing, debugging, architecture. It gets deep treatment.
>
> **Java is *not* your DSA / LeetCode / OA language.** Coding rounds and OAs are solved in
> **Python** (Track A — [../19-python/README.md](../19-python/README.md),
> [../PYTHON_INTERVIEW_CHEATSHEET.md](../PYTHON_INTERVIEW_CHEATSHEET.md), [../03-dsa/README.md](../03-dsa/README.md)).
> Java collections fluency is kept alive with **one "Java rep" per week**: one problem you already
> solved in Python, re-done in Java (HashMap, ArrayDeque, PriorityQueue, Comparator, BFS/DFS,
> trees, recursion) — see [03-collections-generics.md](./03-collections-generics.md#java-for-occasional-dsa-reps)
> and [../03-dsa/java-dsa-toolkit.md](../03-dsa/java-dsa-toolkit.md). Never solve everything twice.

**Target version:** Java 21 (LTS). Every snippet in this folder compiles on JDK 21.
**Goal:** not "I've used Java" but "I can explain what the JVM does with this line, prove it with
a small experiment, and show where I relied on it in a project I built."

---

## Module map

| # | File | Weeks ([ROADMAP §5–6](../ROADMAP.md#6-just-in-time-learning-map)) | Key outcomes | Where a project leans on it |
|---:|---|---|---|---|
| 1 | [01-syntax-basics.md](./01-syntax-basics.md) | **W1** | JDK/`javac`/`java`, primitives vs references, overflow, control flow, methods, arrays, `String` vs `StringBuilder`, `switch` expressions, `var` | Everything |
| 2 | [02-oop.md](./02-oop.md) | **W2** (mechanics) · applied **W4–6** | Encapsulation, interfaces vs abstract classes, polymorphism & dynamic dispatch, `equals`/`hashCode`, records, sealed types, composition, SOLID, Strategy/Factory/Builder/Observer/Singleton | FlowGrid domain model & allocation scorers; FlagForge `FlagClient` builder |
| 3 | [03-collections-generics.md](./03-collections-generics.md) | **W2** | `List`/`Set`/`Map`/`Deque`/`PriorityQueue`, `HashMap` internals (buckets, resize, treeify), `ArrayList` growth, fail-fast iterators, `Comparator`, generics & PECS, erasure, **Java DSA-rep idioms** | Everywhere; weekly Java rep |
| 4 | [04-modern-java.md](./04-modern-java.md) | **W3** | Lambdas, functional interfaces, streams & collectors, `Optional`, records, enums, pattern matching, immutability, `java.time`, `BigDecimal` | DTOs as records; LedgerX money; FlowGrid reports |
| 5 | [05-exceptions-io.md](./05-exceptions-io.md) | **W2** (exceptions) · **W3** (I/O) | Checked vs unchecked, custom exceptions, translation at boundaries, try-with-resources & suppressed exceptions, `java.nio.file`, per-row error reporting | ProblemDetail mapping in every API; ForgeCI log files/workspaces |
| 6 | [06-memory-jvm.md](./06-memory-jvm.md) | **W5** | Stack vs heap, pass-by-value, String pool, Integer cache, JVM memory areas, GC generations, JIT, classloading, leaks, container memory | FlowGrid in Docker; ForgeCI worker sizing |
| 7 | [07-concurrency.md](./07-concurrency.md) | **W5** (basics) · **W15** (deep) | Race condition demo + 3 fixes, happens-before, `volatile`, `synchronized`, `ReentrantLock`, atomics, `ConcurrentHashMap`, `ExecutorService`, `CompletableFuture`, virtual threads, deadlock + fix | FlowGrid M2 reservation test; LedgerX M2 transfers; ForgeCI M2/M4 worker pool, leases, shutdown |
| 8 | [08-maven-build.md](./08-maven-build.md) | **W2** · multi-module **W14** · publishing **W22** | POM anatomy, coordinates, scopes, lifecycle, surefire/failsafe/jar/spring-boot plugins, dependency conflicts, `mvnw`, multi-module, Gradle comparison | CI `./mvnw -B verify` everywhere; ForgeCI api+worker modules; FlagForge SDK artifact |
| 9 | [09-debugging-java.md](./09-debugging-java.md) | **W3** · drills from **W13** | Stack traces & `Caused by`, IntelliJ debugger (conditional breakpoints, evaluate, drop frame), logging-first debugging, `jcmd`/`jstack`/heap dumps, systematic method | Every project's `failure-engineering.md`; [../21-debugging-code-reading/](../21-debugging-code-reading/README.md) |
| — | [exercises.md](./exercises.md) | W1–W3, W5, W15 | 46 graded exercises with acceptance criteria (throwaway katas, not portfolio) | — |
| — | [interview-questions.md](./interview-questions.md) | weekly from W4 | 70 Track-B Java Q&A, grouped by topic | Résumé defense: [../17-resume-tech-defense/java.md](../17-resume-tech-defense/java.md) |

---

## Learning order (and why)

```
W1   01 syntax-basics                          (Python core runs in parallel — DSA is Python from Day 1)
W2   02 oop · 03 collections · 05 exceptions · 08 maven   + JUnit 5 (../09-testing/junit5.md)
W3   04 modern-java · 05 I/O half · 09 debugging          → tiny Spring Boot API (throwaway)
W4   FlowGrid M1 — uses records, Optional, exceptions→ProblemDetail, Maven, CI
W5   06 memory-jvm · 07 concurrency §1–3, §10–11          → FlowGrid M2 N-threads-one-unit test
W9–10  LedgerX: BigDecimal, lock ordering (07 §10), concurrent transfer test
W14  08 multi-module                                     → ForgeCI api + worker
W15  07 concurrency §4–9 (executors, CompletableFuture, virtual threads) → ForgeCI worker pool
W22  08 publishing an artifact                          → FlagForge Java SDK
```

- **Syntax → OOP → collections in two weeks**: the foundation phase is short because FlowGrid starts in Week 4; depth comes from using Java daily in the projects.
- **Collections right after OOP**: `HashMap` only makes sense once you understand `equals`/`hashCode`.
- **Maven in W2**: the Week 3 Spring Boot intro and every project are Maven builds.
- **Memory + thread basics in W5**, exactly when FlowGrid's reservation concurrency test needs them.
- **Deep concurrency in W15**, when ForgeCI's workers need executors, leases, timeouts and graceful shutdown.

Each module file has the same shape: **concepts** with runnable Java 21 code → **under the hood**
→ **Break it** experiments → **common mistakes** → **interview questions** (answers in `<details>`)
→ **mastery checklist**. Type every example yourself; then do the matching section of [exercises.md](./exercises.md).

---

## How Java is tested at each checkpoint

| Checkpoint | Java evidence required (coding-round problems at every checkpoint are Python) |
|---|---|
| [CP-4](../checkpoints/checkpoint-04.md) | From a blank file: a class with correct `equals`/`hashCode`, a `Comparator` chain, a stream `groupingBy`, try-with-resources file read — all with JUnit tests. Explain `HashMap` put/get aloud. Find a planted bug with a conditional breakpoint. FlowGrid M1 compiles, `./mvnw verify` green in CI. |
| [CP-8](../checkpoints/checkpoint-08.md) | FlowGrid v1.0 walk-through: records as DTOs, exception → ProblemDetail mapping, the reservation concurrency test and why it needs a real Postgres. Answer: stack vs heap, pass-by-value, String pool, GC generations, `volatile` vs `synchronized`. |
| [CP-12](../checkpoints/checkpoint-12.md) | LedgerX: `BigDecimal` rules, lock ordering to avoid deadlock, the $500/$400/$400 test, reading a Spring stack trace to its last `Caused by`. |
| [CP-16](../checkpoints/checkpoint-16.md) | ForgeCI worker: `ExecutorService`/virtual threads, timeouts, cancellation via interrupts, graceful shutdown; race-condition demo + 3 fixes from memory; `ConcurrentHashMap` internals; multi-module Maven build. |
| [CP-20](../checkpoints/checkpoint-20.md) | Take and interpret a thread dump (`jcmd <pid> Thread.print`) of a running worker; explain a deadlock from a dump; ForgeCI failure recovery walkthrough. |
| [CP-24](../checkpoints/checkpoint-24.md) | Mock Track-B round: 10 random questions from [interview-questions.md](./interview-questions.md) in < 2 min each, plus a 15-minute project deep-dive where every Java claim points to code you wrote. |

---

## Resources

**Official (primary)**
- [dev.java/learn](https://dev.java/learn/) — Oracle's modern learning path (records, sealed classes, pattern matching, streams, virtual threads).
- [Java SE 21 API docs](https://docs.oracle.com/en/java/javase/21/docs/api/) — read the class-level Javadoc of `HashMap`, `ArrayList`, `ConcurrentHashMap`, `Thread`, `CompletableFuture`, `ExecutorService`.
- [Java Language Specification, SE 21](https://docs.oracle.com/javase/specs/jls/se21/html/index.html) — Chapter 17 (threads and locks / happens-before) in Week 15.
- [JDK 21 tool specifications](https://docs.oracle.com/en/java/javase/21/docs/specs/man/index.html) — `java`, `javac`, `jcmd`, `jstack`, `jmap`.
- [maven.apache.org/guides](https://maven.apache.org/guides/) — Getting Started; Introduction to the Build Lifecycle.

**Books**

| Book | How to use it |
|---|---|
| *Effective Java*, 3rd ed. — Joshua Bloch | **W2:** Items 10–12 (`equals`, `hashCode`, `toString`), 14 (`Comparable`), 15–17 (accessibility & mutability), 26, 28, 31 (raw types, lists vs arrays, bounded wildcards), 69–77 (exceptions) · **W3:** 42–48 (lambdas & streams), 49–50 (validity checks, defensive copies), 55 (`Optional`) · **W4–6:** 1–3 (static factories, builders, enum singleton), 18 (composition over inheritance), 64 (refer to objects by interfaces) · **W5:** 57, 61 (local scope, primitives vs boxed) · **W15:** 78–84 (concurrency) |
| *Core Java, Vol. I — Fundamentals* (12th ed. or later) — Cay Horstmann | Reference textbook; read the matching chapter whenever a module's explanation isn't enough. |
| *Modern Java in Action*, 2nd ed. — Urma, Fusco, Mycroft | W3: lambdas, streams, collectors, `Optional`; W15: `CompletableFuture`. |
| *Java Concurrency in Practice* — Goetz et al. | W15 optional: chapters 1–5 and 6–8 (task execution, cancellation, shutdown) map directly onto ForgeCI's worker. |

**Related folders:** [../09-testing/junit5.md](../09-testing/junit5.md) · [../05-spring-boot/README.md](../05-spring-boot/README.md) · [../14-cs-fundamentals/memory-and-architecture.md](../14-cs-fundamentals/memory-and-architecture.md) · [../14-cs-fundamentals/concurrency.md](../14-cs-fundamentals/concurrency.md) · [../17-resume-tech-defense/java.md](../17-resume-tech-defense/java.md) · [../17-resume-tech-defense/maven.md](../17-resume-tech-defense/maven.md)

---

## Weekly Java routine (inside the ROADMAP §12 rhythm)

| Block | Java activity |
|---|---|
| Learning blocks (Mon/Wed) | The module sections the current milestone needs — type every example, run every "Break it". |
| Project blocks | Apply it in the current project; when something surprises you, write a 5-line note on *why* (that note becomes an interview answer). |
| DSA blocks | Python. **Once a week:** one Java rep of an already-solved problem (tracker's *Java rep* column). |
| Interview block | 5 questions from [interview-questions.md](./interview-questions.md) out loud; open `<details>` only afterwards. |
| Sunday review | Re-do one exercise from 2+ weeks ago from a blank file; tick the module's mastery checklist. |

## Folder mastery checklist

- [ ] Every module's mastery checklist ticked
- [ ] ≥ 35 exercises from [exercises.md](./exercises.md) done with tests
- [ ] ≥ 50 questions in [interview-questions.md](./interview-questions.md) answered out loud without peeking
- [ ] Can draw the JVM memory areas and `HashMap` bucket structure from memory
- [ ] Can write, run and fix the race-condition demo from memory
- [ ] One Java DSA rep logged every week since Week 2
- [ ] Every Java claim on your résumé maps to code you wrote in FlowGrid, LedgerX, ForgeCI or FlagForge
