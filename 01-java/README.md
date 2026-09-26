# ☕ 01 — Java 21 (Primary Language)

> Java is the language you build every backend project in, solve every DSA problem in, and
> get probed on in every technical interview. It gets the largest single-language budget in
> this roadmap (≈75 h, see [ROADMAP §3](../ROADMAP.md#3-priority-weighting)) and it keeps
> coming back: in Spring Boot, in concurrency, in debugging drills, in résumé defense.

**Target version:** Java 21 (LTS). Every snippet in this folder compiles on JDK 21.
**Goal:** not "I've used Java" but "I can explain what the JVM does with this line, and I can
prove it with a small experiment."

---

## Module map

| # | File | Weeks ([ROADMAP §5](../ROADMAP.md#5-week-by-week-master-table)) | Key outcomes |
|---:|---|---|---|
| 1 | [01-syntax-basics.md](./01-syntax-basics.md) | **W1** | JDK/`javac`/`java`, primitives vs references, operators and overflow, control flow, methods, arrays, `String` vs `StringBuilder`, `switch` expressions, `var` |
| 2 | [02-oop.md](./02-oop.md) | **W2** (mechanics) · **W5** (design) | Classes, encapsulation, `static`, inheritance, interfaces, abstract classes, polymorphism & dynamic dispatch, `equals`/`hashCode`/`toString` contract, composition over inheritance, SOLID, Strategy/Factory/Builder/Observer/Singleton |
| 3 | [03-collections-generics.md](./03-collections-generics.md) | **W3** | `List`/`Set`/`Map`/`Deque`/`PriorityQueue`, `HashMap` internals (buckets, resize, treeification), `ArrayList` growth, iteration & fail-fast, `Comparable`/`Comparator`, generics, wildcards (PECS), type erasure |
| 4 | [04-modern-java.md](./04-modern-java.md) | **W4** | Lambdas, functional interfaces, method references, streams & collectors, `Optional`, records, enums, sealed types, pattern matching, immutability |
| 5 | [05-exceptions-io.md](./05-exceptions-io.md) | **W2** (exceptions) · **W4** (I/O) | Checked vs unchecked, exception hierarchy, custom exceptions, try-with-resources & suppressed exceptions, `java.nio.file`, CSV parsing with per-row errors (P1 Ledger) |
| 6 | [06-memory-jvm.md](./06-memory-jvm.md) | **W6** | Stack vs heap, pass-by-value, String pool, Integer cache, JVM memory areas, GC generations, JIT, classloading, memory leaks in Java |
| 7 | [07-concurrency.md](./07-concurrency.md) | **W13** | Threads, race conditions (runnable demo + fix), `synchronized`, `volatile`, happens-before, `ReentrantLock`, atomics, `ExecutorService`, `CompletableFuture`, virtual threads, `ConcurrentHashMap`, deadlock + fix |
| 8 | [08-maven-build.md](./08-maven-build.md) | **W3** (revisited W9, W12) | POM anatomy, coordinates, scopes, lifecycle, surefire/failsafe/jar/spring-boot plugins, dependency tree & conflicts, `mvnw`, multi-module, Gradle comparison |
| 9 | [09-debugging-java.md](./09-debugging-java.md) | **W4** (revisited W18) | Reading stack traces incl. `Caused by`, IntelliJ debugger, conditional breakpoints, evaluate expression, drop frame, `jstack`/`jcmd`/heap dumps, logging-first debugging, systematic method |
| — | [exercises.md](./exercises.md) | W1–W13 | 46 graded exercises with acceptance criteria |
| — | [interview-questions.md](./interview-questions.md) | W4 onward, weekly | 70 Java interview Q&A, grouped by topic |

---

## Learning order (and why)

```
W1  syntax-basics ─┐
W2  oop ───────────┼── exceptions (05, first half) + JUnit 5 basics (../09-testing/junit5.md)
W3  collections-generics + maven-build ──► P1 Ledger starts (Maven project)
W4  modern-java + exceptions-io (I/O half) + debugging-java ──► 🏁 Checkpoint 4
W5  oop (design half: SOLID + patterns) ──► P1 refactor
W6  memory-jvm
W13 concurrency ──► P2 TicketHold concurrent seat-hold test
```

- **Syntax before OOP** — you can't design classes while fighting semicolons.
- **Collections right after OOP** — `HashMap` only makes sense once you understand `equals`/`hashCode`.
- **Maven in W3** — Project 1 is a Maven project from its first commit.
- **Modern Java in W4** — streams need collections; lambdas need interfaces.
- **Memory in W6** — after you've written enough code to have *questions* about references.
- **Concurrency in W13** — needs memory model (W6), and has a real use case waiting: TicketHold double-booking.

Each module file follows the same shape:

1. **Concepts** with explanations and runnable Java 21 code
2. **Under the hood** — what the JVM/library actually does
3. **Break it** — experiments where you predict, run, and explain
4. **Common mistakes**
5. **Interview questions** with answers in collapsible `<details>`
6. **Mastery checklist**

Work through the file, type every example yourself (no copy-paste), then do the matching
section of [exercises.md](./exercises.md).

---

## How Java is tested at each checkpoint

| Checkpoint | What you must demonstrate in Java |
|---|---|
| [CP-4](../checkpoints/checkpoint-04.md) | From a blank file, no IDE autocomplete for the first 10 min: a class with correct `equals`/`hashCode`, a `Comparator` chain, a stream pipeline with `groupingBy`, a file read with try-with-resources; explain `HashMap` put/get out loud; set a conditional breakpoint and find a planted bug. JUnit tests for all of it. |
| [CP-8](../checkpoints/checkpoint-08.md) | P1 Ledger v1.0 code walk-through: explain the `Money`/`BigDecimal` choice, the Strategy-based rules engine, the `TransactionRepository` abstraction, JDBC transactions. Answer: stack vs heap, pass-by-value, String pool, GC generations. All DSA so far solved in idiomatic Java (`ArrayDeque`, `HashMap.merge`, `PriorityQueue` with comparator). |
| [CP-12](../checkpoints/checkpoint-12.md) | Java inside Spring: records as DTOs, `Optional` from repositories, custom exceptions mapped to ProblemDetail, reading a Spring stack trace to its `Caused by`. Maven: explain `mvn verify` phases and surefire vs failsafe. |
| [CP-16](../checkpoints/checkpoint-16.md) | Concurrency (W13): write the race-condition demo and three fixes from memory; explain happens-before, `volatile` vs `synchronized`, why `ConcurrentHashMap` doesn't allow `null`; walk through the TicketHold concurrent seat-hold test. |
| [CP-20](../checkpoints/checkpoint-20.md) | P4 PulseWatch checker: `ExecutorService` / virtual threads with timeouts, graceful shutdown; take a thread dump with `jcmd` and interpret it. |
| [CP-24](../checkpoints/checkpoint-24.md) | Full mock: random 10 questions from [interview-questions.md](./interview-questions.md) answered out loud in < 2 min each; one OA problem in Java under time. |

---

## Resources

**Official (primary)**
- [dev.java](https://dev.java/learn/) — Oracle's modern learning path (records, sealed classes, pattern matching, streams, virtual threads).
- [Java SE 21 API docs](https://docs.oracle.com/en/java/javase/21/docs/api/) — read the class-level Javadoc of `HashMap`, `ArrayList`, `ConcurrentHashMap`, `Thread`, `CompletableFuture`. They contain the answers to half of all interview questions.
- [Java Language Specification (JLS 21)](https://docs.oracle.com/javase/specs/jls/se21/html/index.html) — Chapter 17 (threads and locks / happens-before) when you reach W13.
- [JDK 21 tool reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/index.html) — `java`, `javac`, `jcmd`, `jstack`, `jmap`.
- [maven.apache.org](https://maven.apache.org/guides/) — Getting Started + Introduction to the Build Lifecycle.

**Books**

| Book | How to use it |
|---|---|
| *Effective Java*, 3rd ed. — Joshua Bloch | Read these items at the week listed: **1** (static factories), **2** (builders), **3** (singleton via enum) — W5 · **10, 11, 12** (`equals`, `hashCode`, `toString`) — W2 · **14** (`Comparable`) — W3 · **15, 16, 17** (minimize accessibility & mutability) — W2/W4 · **18** (composition over inheritance) — W5 · **26, 28, 31** (raw types, lists vs arrays, bounded wildcards) — W3 · **42–47** (lambdas and streams) — W4 · **49, 50** (validate params, defensive copies) — W4 · **55** (return `Optional` judiciously) — W4 · **57, 61** (minimize local var scope, prefer primitives) — W6 · **69–77** (exceptions chapter) — W2/W4 · **78–84** (concurrency chapter) — W13 |
| *Core Java, Vol. I — Fundamentals* (12th ed. or later) — Cay Horstmann | Reference textbook; read the chapter matching each module when an explanation here isn't enough. |
| *Modern Java in Action*, 2nd ed. — Urma, Fusco, Mycroft | W4: chapters on lambdas, streams, collectors, `Optional`; W13: `CompletableFuture`. |
| *Java Concurrency in Practice* — Goetz et al. | W13 optional: chapters 1–5 still the best explanation of visibility and safe publication (pre-virtual-threads, but the memory model hasn't changed). |

**Practice**
- DSA in Java: [../03-dsa/java-dsa-toolkit.md](../03-dsa/java-dsa-toolkit.md) — the collections API you'll use every day on LeetCode.
- Testing: [../09-testing/junit5.md](../09-testing/junit5.md) (starts W2).
- Résumé defense: [../17-resume-tech-defense/java.md](../17-resume-tech-defense/java.md) and [../17-resume-tech-defense/maven.md](../17-resume-tech-defense/maven.md).
- CS depth behind Java: [../14-cs-fundamentals/memory-and-architecture.md](../14-cs-fundamentals/memory-and-architecture.md), [../14-cs-fundamentals/concurrency.md](../14-cs-fundamentals/concurrency.md).

---

## Weekly Java routine (fits the ROADMAP §11 rhythm)

| Block | Java activity |
|---|---|
| Mon core learning (1.5 h) | Read the week's module sections; type examples. |
| Mon/Thu hands-on (1 h each) | "Break it" experiments + exercises for that week. |
| Fri light day | Pick 5 questions from [interview-questions.md](./interview-questions.md), answer out loud, open `<details>` only after. |
| Sun review | Re-do one exercise from 2+ weeks ago from a blank file. Tick the module's mastery checklist. |

## Folder mastery checklist

- [ ] Every module's mastery checklist ticked
- [ ] ≥ 40 exercises from [exercises.md](./exercises.md) done with tests
- [ ] ≥ 50 of the questions in [interview-questions.md](./interview-questions.md) answered out loud without peeking
- [ ] Can draw the JVM memory areas and `HashMap` bucket structure from memory
- [ ] Can write, run and fix the race-condition demo from memory
- [ ] Every Java claim on your résumé maps to code you wrote in P1–P4
