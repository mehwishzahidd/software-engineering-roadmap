# 09 — Debugging Java (Week 3 · codebase-reading drills from Week 13)

> **Outcome:** find the root cause of a Java failure methodically: read the stack trace down to
> the last `Caused by`, reproduce, form a hypothesis, test it with the IntelliJ debugger or
> logs, fix, and add a regression test. Diagnose hung and memory-hungry JVMs with `jcmd`,
> `jstack` and heap dumps.

Related: [README](./README.md) · drills and the buggy practice project: [../21-debugging-code-reading/README.md](../21-debugging-code-reading/README.md), [../21-debugging-code-reading/method.md](../21-debugging-code-reading/method.md), [../21-debugging-code-reading/drills.md](../21-debugging-code-reading/drills.md) · logging in Spring: [../05-spring-boot/06-logging-actuator.md](../05-spring-boot/06-logging-actuator.md)

---

## 1. The systematic method

```
1. Reproduce      — a failing test or a command that fails every time
2. Read           — the whole error: exception type, message, top frame in YOUR code, last Caused by
3. Localize       — narrow the search space (binary search: comment out, git bisect, smaller input)
4. Hypothesize    — "I think X is null because the CSV has a trailing empty column"
5. Test it        — breakpoint / log line / assertion that confirms or kills the hypothesis
6. Fix            — the root cause, not the symptom (don't just add a null check)
7. Prove          — the failing test now passes; add a regression test; run the full suite
8. Explain        — one sentence in the commit message: cause + fix
```
Rules: change **one thing at a time**; write down what you've ruled out; if you've spent 30 min
without a new hypothesis, step back and re-read the error.

---

## 2. Reading stack traces

```
Exception in thread "main" com.example.katas.ImportFailedException: import of data/stock.csv failed
	at com.example.katas.StockImportService.importFile(StockImportService.java:41)
	at com.example.katas.KataCli.run(KataCli.java:58)
	at com.example.katas.KataCli.main(KataCli.java:22)
Caused by: com.example.katas.RepositoryException: save failed for sku=MUG-12OZ-BLU
	at com.example.katas.JdbcStockRepository.save(JdbcStockRepository.java:73)
	at com.example.katas.StockImportService.importFile(StockImportService.java:36)
	... 2 more
Caused by: org.postgresql.util.PSQLException: ERROR: null value in column "warehouse_id" of relation "stock_adjustments" violates not-null constraint
  Detail: Failing row contains (17, MUG-12OZ-BLU, null, 40, 2025-03-02 10:15:00+00).
	at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2725)
	at org.postgresql.jdbc.PgPreparedStatement.executeUpdate(PgPreparedStatement.java:152)
	at com.example.katas.JdbcStockRepository.save(JdbcStockRepository.java:70)
	... 3 more
```

How to read it:
1. **Top line:** the exception that finally escaped, and its message.
2. **Frames** are the call stack, **most recent call first**. `at Class.method(File.java:line)`.
3. **`Caused by:`** chains come from wrapping (`new X(msg, cause)`). **The last `Caused by` is usually the root cause.** Here: a `NOT NULL` violation — the CSV row's warehouse code didn't resolve to an id, and the code passed `null` on instead of rejecting the row.
4. **First frame in *your* package** under the root cause (`JdbcStockRepository.java:70`) is where to start looking; library frames above it tell you *what* failed.
5. `... 3 more` = frames identical to the enclosing trace; not hidden information.
6. `Suppressed:` blocks come from try-with-resources close failures.

Common exceptions → first suspicion:

| Exception | First thing to check |
|---|---|
| `NullPointerException` | Java 14+ *helpful NPE* message names the null: `Cannot invoke "String.length()" because "<local2>" is null` (compile with `-g` to see variable names) |
| `ArrayIndexOutOfBounds` / `StringIndexOutOfBounds` | Loop bounds, `split` result length, off-by-one |
| `NumberFormatException: For input string: " 12"` | Whitespace, commas, currency symbols, empty strings |
| `DateTimeParseException ... at index 2` | Format pattern vs input |
| `ClassCastException` | Raw types, wrong deserialization target |
| `ConcurrentModificationException` | Modifying a collection while iterating |
| `IllegalStateException` in Spring startup | Scroll to the **last Caused by** — often a missing bean or bad config |
| `NoSuchMethodError` / `NoClassDefFoundError` | Dependency version conflict → `mvn dependency:tree` ([08-maven-build.md](./08-maven-build.md)) |
| `StackOverflowError` | Repeating frames → infinite recursion (or `toString`/`equals` cycles between two entities) |

Spring Boot traces are long. Search for `Caused by`, jump to the last one, then find the first
`com.example` frame.

---

## 3. The IntelliJ IDEA debugger

Start with **Debug** (bug icon / `Shift+F9`) instead of Run. For tests: gutter icon → *Debug*.
For Maven: `mvn -Dmaven.surefire.debug test` waits for a debugger on port 5005 (Run → *Attach to Process* / Remote JVM Debug config).

### Breakpoint types

| Type | How | Use |
|---|---|---|
| Line breakpoint | Click gutter | Stop at a line |
| **Conditional** | Right-click breakpoint → Condition: `merchant.contains("Coffee") && amount.signum() < 0` | Stop only on the interesting iteration (row 4 017 of a CSV) |
| Log / non-suspending | Uncheck *Suspend*, tick *Evaluate and log* | "printf debugging" without editing code |
| Exception breakpoint | Run → View Breakpoints (`Ctrl+Shift+F8`) → + Java Exception Breakpoint → `NullPointerException` | Stop exactly where the exception is **thrown**, not where it's caught |
| Method breakpoint | Click gutter at method signature | Stop on entry/exit (slow — use sparingly) |
| Field watchpoint | Gutter on a field | Stop whenever a field is read/written — "who changed this?" |
| Pass count | Breakpoint → *More* → Pass count | Stop on the Nth hit |

### Stepping

| Action | Key (Win/Linux) | Meaning |
|---|---|---|
| Step Over | `F8` | Run the current line; don't enter calls |
| Step Into | `F7` | Enter the method called on this line |
| Smart Step Into | `Shift+F7` | Choose which call to enter on a chained line |
| Step Out | `Shift+F8` | Finish current method, return to caller |
| Run to Cursor | `Alt+F9` | Continue until the caret line |
| Resume | `F9` | Continue to the next breakpoint |
| **Evaluate Expression** | `Alt+F8` | Run any Java expression in the current frame: `rules.stream().map(r -> r.priority()).toList()` |
| **Drop Frame / Reset Frame** | Frames panel → ↶ | Pop the current frame and re-enter the method — replay without restarting (side effects already done are **not** undone) |

(macOS: F-keys the same with `fn`; Evaluate is `⌥F8`.)

Panels: **Frames** (the call stack — click a lower frame to inspect the caller's locals),
**Variables** (expand objects; right-click → *Set Value* to test a hypothesis), **Watches**
(expressions re-evaluated at every stop), **Threads** (switch threads; essential for concurrency bugs).

### A debugging session, concretely (kata report total is wrong)

1. Failing test: `monthlyReportSumsOnlyMarch` expects `-123.45`, gets `-246.90`.
2. Hypothesis: transactions are counted twice. Breakpoint in `ReportService.summarize`, condition `tx.merchant().equals("Rent")`.
3. Hits twice for the same transaction id → step out (`Shift+F8`) → caller loops over `repo.findAll()` **and** the in-memory import list.
4. Evaluate `repo.findAll().size()` vs `imported.size()` → confirms duplicates.
5. Fix caller to use one source; test passes; add a test with two imports of the same file.

---

## 4. Logging-first debugging

Production has no debugger. Logs are the debugger you had to plan ahead for.

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ImportService {
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    public ImportReport importFile(Path path) throws IOException {
        log.info("import started file={}", path);
        ImportReport report = importer.importFile(path);
        log.info("import finished file={} imported={} errors={}", path, report.imported().size(), report.errors().size());
        report.errors().forEach(e -> log.warn("import row rejected line={} reason={}", e.line(), e.reason()));
        return report;
    }
}
```

- Use **parameterized** messages `{}` (no string concat cost when the level is off).
- Levels: `ERROR` (someone must act), `WARN` (unexpected but handled), `INFO` (business events), `DEBUG` (diagnostics, off in prod), `TRACE`.
- Log **identifiers and counts**, not whole objects or secrets (never passwords, tokens, full card numbers).
- Log an exception **with** the throwable as last arg: `log.error("save failed id={}", id, e);` — prints the full stack trace.
- Log **once** where the exception is handled, not at every layer.
- Correlate requests with an ID (MDC) — FlowGrid adds a request-ID filter; ForgeCI adds build/job IDs to every worker log line.
- Temporarily raise one logger to DEBUG (Spring: `logging.level.com.example.flowgrid=DEBUG`) instead of adding `System.out.println`s.

---

## 5. When there's no stack trace: hangs, slowness, memory

| Symptom | Tool | Look for |
|---|---|---|
| App hangs / request never returns | Thread dump | Threads `BLOCKED` on the same monitor, "Found one Java-level deadlock", threads stuck in socket reads (missing timeouts) |
| CPU at 100% | Several thread dumps a few seconds apart + `top -H -p <pid>` | Same `RUNNABLE` frame in every dump (hot loop) |
| Memory grows until OOM | Heap histogram, heap dump | Class with ever-increasing instance count; dominator tree |
| Long pauses | GC logs (`-Xlog:gc*`) | Frequent full GCs, old gen near max |

---

## 6. JVM diagnostic tools

```bash
jps -l                                   # list Java processes: pid + main class
jcmd                                     # same, and lists available commands per pid
jcmd <pid> help                          # commands supported by that JVM

jcmd <pid> Thread.print > threads.txt    # thread dump (== jstack <pid>)
jstack <pid>                             # classic thread dump; detects deadlocks
jstack -l <pid>                          # + ownable synchronizers (ReentrantLock owners)

jcmd <pid> GC.heap_info                  # heap usage per generation
jcmd <pid> GC.class_histogram | head -20 # instance counts & bytes per class (live objects)
jcmd <pid> GC.heap_dump /tmp/heap.hprof  # full heap dump (pauses the app; file ≈ heap size)
jmap -dump:live,format=b,file=/tmp/heap.hprof <pid>   # older equivalent

jcmd <pid> VM.flags                      # effective JVM flags
jcmd <pid> VM.system_properties
jcmd <pid> JFR.start duration=60s filename=/tmp/rec.jfr   # Java Flight Recorder profile
```

**Reading a thread dump entry:**

```
"transfer-A-to-B" #21 prio=5 os_prio=0 tid=0x... nid=0x5e03 waiting for monitor entry
   java.lang.Thread.State: BLOCKED (on object monitor)
	at DeadlockDemo.transferUnsafe(DeadlockDemo.java:14)
	- waiting to lock <0x000000071a8b2f10> (a DeadlockDemo$Account)
	- locked <0x000000071a8b2ef8> (a DeadlockDemo$Account)
	at DeadlockDemo.lambda$main$0(DeadlockDemo.java:39)
```
Thread name (name your threads/pools!), state, what it holds (`locked`), what it wants
(`waiting to lock`). See [07-concurrency.md](./07-concurrency.md#10-deadlock--create-it-see-it-fix-it).

**Heap dumps:** open the `.hprof` in **VisualVM** or **Eclipse MAT** → *Dominator tree* (which
objects retain the most memory) → *Path to GC roots* (why they're still reachable — e.g. a static
`HashMap` in `CacheHolder`). Always run production JVMs with
`-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=...`.

In Docker (every project from Week 5): `docker exec -it <container> jcmd 1 Thread.print` (the JVM is often PID 1).
Needs a JDK image or `jcmd` available — one reason to know what your base image contains.

---

## 7. Other techniques

- **`git bisect`** — the bug appeared "sometime last week": `git bisect start; git bisect bad; git bisect good v0.3`, then mark each checkout good/bad (or `git bisect run ./mvnw -q test`). See [../02-git/README.md](../02-git/README.md).
- **Minimize the input** — halve the CSV until the failing row is isolated.
- **Assertions in tests** at intermediate steps to localize.
- **Rubber duck** — explain the code line by line out loud; this is also interview practice.
- **Read the source** — `Ctrl+B` into JDK/Spring code; download sources in IntelliJ. Library code isn't magic.

---

## 🔨 Break it (debugging drills)

1. Plant `cols[3]` in the CSV parser; read the `ArrayIndexOutOfBoundsException` trace; find the line without reading code first.
2. Make the categorizer return `null` for one merchant; set an **exception breakpoint** on `NullPointerException`; see where it's thrown vs where it's caught.
3. In a loop over 10 000 transactions, find the one with a negative id using a **conditional breakpoint**, not by stepping.
4. Use **Evaluate Expression** to call a method with different arguments while paused.
5. Use **Drop Frame** to re-run a method after changing a variable's value in the Variables panel.
6. Run `DeadlockDemo` from [07-concurrency.md](./07-concurrency.md) and diagnose it with `jcmd Thread.print` only.
7. Run the heap-hog from [06-memory-jvm.md](./06-memory-jvm.md) with `-XX:+HeapDumpOnOutOfMemoryError`; find the culprit in MAT/VisualVM.
8. From Week 13: pick a failing test in [../21-debugging-code-reading/](../21-debugging-code-reading/README.md) and apply §1 end-to-end, timing yourself.

## ⚠️ Common mistakes

- Reading only the first line of a trace (the wrapper) and not the last `Caused by`.
- Fixing the symptom (`if (x != null)`) without asking why `x` was null.
- Changing several things at once.
- `System.out.println` debugging left in commits.
- Catch-and-ignore blocks hiding the real exception.
- Debugging without a reproducible failing test.

## 🎤 Interview questions

<details><summary>1. How do you approach a bug you've never seen before?</summary>

Reproduce it reliably (ideally a failing test), read the full error to the root cause, narrow the
area (bisect, smaller input), form a hypothesis and test it with a debugger or logs, fix the root
cause, add a regression test, run the suite. Give a real example from FlowGrid or LedgerX — only one you actually debugged.
</details>

<details><summary>2. How do you read a stack trace with multiple "Caused by" sections?</summary>

Top is the outermost wrapper; each `Caused by` is the exception it wrapped. The last `Caused by` is
the root cause. Within it, the first frame in my own package shows where my code triggered it.
</details>

<details><summary>3. Your service is hanging in production. What do you do?</summary>

Take 2–3 thread dumps a few seconds apart (`jcmd <pid> Thread.print`), look for deadlocks,
threads BLOCKED on the same lock, or threads waiting on I/O without timeouts (DB pool exhausted,
HTTP call without timeout). Check metrics (pool usage), logs around the time, then fix and add timeouts/alerts.
</details>

<details><summary>4. Memory keeps growing. How do you investigate?</summary>

Confirm with GC logs / heap metrics that post-GC usage trends up. Compare `GC.class_histogram`
snapshots, take a heap dump, open the dominator tree, follow the path to GC roots to find what's
retaining the objects (static map, listener list, ThreadLocal). Fix and verify with the same measurement.
</details>

<details><summary>5. What is a conditional breakpoint and when do you use it?</summary>

A breakpoint that suspends only when a boolean expression is true — to stop on the one bad
iteration among thousands without stepping manually.
</details>

<details><summary>6. When do you prefer logging over a debugger?</summary>

In production or staging, for intermittent or timing-dependent bugs (concurrency, where pausing
changes behavior), distributed systems (requests across services), and anything you need
after the fact. Debugger for local, reproducible logic bugs.
</details>

## ✅ Mastery checklist

- [ ] Recite the 8-step method and apply it to a real bug with notes
- [ ] Find the root cause in a 3-level `Caused by` trace in under a minute
- [ ] Use conditional, exception and logging breakpoints
- [ ] Use Step Over/Into/Out, Evaluate Expression, and Drop Frame fluently
- [ ] Add SLF4J logging with parameterized messages and exception logging
- [ ] Take and read a thread dump; detect a deadlock
- [ ] Take a heap histogram and heap dump; find a leak's GC-root path
- [ ] Use `git bisect` to find a regression commit
