# The Method: Reading Unfamiliar Code and Debugging It

Two procedures. Practise them until they're automatic, then use them in every drill in
[`drills.md`](./drills.md), in OA debugging tasks, and on your own projects (FlowGrid, LedgerX, ForgeCI, FlagForge).

---

## Part 1 — Reading an unfamiliar codebase

Goal: in **30 minutes** go from "never seen it" to "I can make a safe change and explain where it lives".

### Step 1 — Build it and run the tests (5 min)

Before reading a single class, prove the toolchain works and get a baseline.

```bash
git status && git log --oneline -10    # what state am I in? what changed recently?
java -version && mvn -v                # right JDK?
mvn -q test                            # does it compile? how many tests? which fail?
```

Write down the baseline: `Tests run: N, Failures: F, Errors: E`. Every later change is measured against it. A test that was green and turns red after your change is **your** regression.

Useful Maven commands:

| Command | Use |
|---|---|
| `mvn test -Dtest=ClassName` | one test class |
| `mvn test -Dtest='ClassName#method*'` | matching methods |
| `mvn -q compile` | fast compile check |
| `mvn dependency:tree` | what's actually on the classpath |
| `mvn test -X` | debug output when the build itself is the problem |
| `ls target/surefire-reports/` | full per-class results and stack traces |

### Step 2 — Read the README and the build file (5 min)

- **README:** what the system is *supposed* to do, domain vocabulary, how to run it. Treat stated behaviour as a hypothesis to verify, not truth.
- **`pom.xml` / `build.gradle` / `package.json`:** Java version, frameworks (Spring? none?), test libraries, plugins, the main class. Dependencies tell you the architecture before you read code: `spring-boot-starter-web` → controllers; `flyway` → `db/migration`; `testcontainers` → integration tests need Docker.

### Step 3 — Find the entry points (3 min)

| App type | Where to look |
|---|---|
| CLI / plain Java | `grep -rn "static void main" src/main` |
| Spring Boot | `@SpringBootApplication`, then `@RestController` / `@Controller`, `@Scheduled`, `@KafkaListener`, `CommandLineRunner` |
| Library | The public "service" classes the README's examples call |
| React | `main.tsx` → `App.tsx` → router config |
| Tests | Tests are also entry points — often the fastest way to see how a class is meant to be used |

### Step 4 — Map the packages (5 min)

Sketch a tree like the one in the buggy-library README: one line per package, what it owns, which way dependencies point. Look for the layering (`domain` ← `policy` ← `service`). Note anything that breaks the layering.

```bash
find src/main/java -name '*.java' | sed 's|src/main/java/||' | sort
grep -rn "^import com.example" src/main/java | awk -F: '{print $1" -> "$3}' | sort | uniq
```

### Step 5 — Follow one request end-to-end (7 min)

Pick the most important use case (checkout, "create booking", "POST /issues") and trace every call from the entry point to where data is stored, and back. Write it down as a call chain. This is the single highest-value reading activity: after one trace you know the layering, the naming conventions, where validation lives, and where state is kept.

While tracing, note:
- **State:** every field that changes. Is the same fact stored twice? (Common bug source.)
- **Boundaries:** where input is validated, where nulls can enter, where exceptions are thrown/caught.
- **Surprises:** comments that disagree with the code, TODOs, methods named for one thing that do another.

### Step 6 — Use the debugger to confirm (5 min)

Reading tells you what the code *should* do; the debugger shows what it *does*.

- Set a breakpoint at the entry point, run a test **in debug mode**, step into (F7 in IntelliJ) each call.
- Watch the values you wrote down in step 5. The first value that surprises you is your lead.
- **Evaluate expression** (Alt+F8) to try a hypothesis without editing code: `loanDate.plusDays(14)`.
- **Conditional breakpoints** in loops: `loan.getId().equals("L3")`.
- **Exception breakpoints** (Run → View Breakpoints → + → Java Exception Breakpoints) stop exactly where an NPE/CME is thrown, before the stack unwinds.

More in [`../01-java/09-debugging-java.md`](../01-java/09-debugging-java.md).

### Step 7 — Use history: `git log` and `git blame`

The code tells you *what*; history tells you *why*.

```bash
git log --oneline -- path/to/File.java          # who touched this file, when
git log -p -S "plusDays" -- src/                 # commits that added/removed a string ("pickaxe")
git blame -L 40,60 path/to/File.java             # who last changed these lines, which commit
git show <sha>                                   # the full change + message
git bisect start; git bisect bad; git bisect good v1.0   # binary-search the commit that broke it
git bisect run mvn -q test -Dtest=LoanPolicyTest         # automate it
git bisect reset
```

A commit message like "treat loan day as day 1 per ticket LIB-42" tells you whether an odd line is a bug or a deliberate business rule. Ask before "fixing" a deliberate rule.

### Reading checklist

- [ ] Baseline recorded (tests run / failing).
- [ ] I can state in two sentences what the system does.
- [ ] I know the entry point(s) and the main service class.
- [ ] I've drawn the package map.
- [ ] I've traced one use case end-to-end and listed all state it touches.
- [ ] I've confirmed one assumption in the debugger.
- [ ] I know where tests for the area I'll change live, and how to run only them.

---

## Part 2 — Debugging

```
Reproduce → Isolate → Hypothesize → Test the hypothesis → Fix → Regression test
```

Each step has an exit condition. Don't move on until it's met.

### 1. Reproduce

**Exit condition:** you can make the failure happen on demand, ideally with one command.

- A failing test is the best reproduction. If the bug report is prose ("students never pay fines"), **write the failing test first**.
- Record exact inputs, expected vs actual, and the full stack trace.
- Can't reproduce? Diff the environments: JDK version, locale, time zone, data, ordering (a `HashMap` iteration order bug appears only with some inputs).

### 2. Isolate

**Exit condition:** you know the smallest piece of code where correct input produces wrong output.

- **Read the stack trace bottom-up to the first frame in *your* code.** Java 14+ helpful NPE messages name the null expression.
- **Binary search the data flow:** check the value halfway along the call chain. Right there? The bug is later. Wrong? Earlier.
- **Shrink the input:** remove items until it stops failing; the last removal is the clue. (Why does 2 loans fail silently but 3 throw? See D09.)
- **Check inputs before calculations.** A wrong fine may come from a wrong due date, not wrong fine math (D03).
- Compare with a **working sibling**: `Member` has `equals`, `Book` doesn't (D05); `STANDARD` works, `STUDENT` doesn't (D04).

### 3. Hypothesize

**Exit condition:** one specific, falsifiable sentence.

Bad: "Something's wrong with search." Good: "`search` doesn't lower-case the keyword, so `JaVa` never matches the lower-cased token `java`."

Common Java root causes to check first:

| Symptom | Usual suspects |
|---|---|
| Off by exactly one unit | `<` vs `<=`, `- 1`/`+ 1`, inclusive vs exclusive ranges, `ChronoUnit.between` semantics |
| Result is 0 or truncated | Integer division, `int` overflow, `long` → `int` cast |
| Duplicates in `Set` / missing `Map` lookups | `equals`/`hashCode` missing or inconsistent, mutable fields used in `hashCode` |
| `NullPointerException` | Nullable field/return dereferenced, `Map.get` miss, unboxing a null `Integer` |
| `ConcurrentModificationException` | Collection modified while iterating (usually single-threaded) |
| Wrong order | Comparator direction, `.reversed()` applied to a whole chain, missing tie-breaker |
| "Someone changed my data" | Getter returns internal mutable collection; shared static state |
| Works on my machine | Locale (`toLowerCase()` without `Locale.ROOT`), time zone (`LocalDate.now()`), file encoding |
| Case/whitespace mismatch | Normalization applied on write but not on read (or vice versa) |
| Intermittent | Race condition, iteration order of `HashMap`/`HashSet`, time-dependent test |

### 4. Test the hypothesis

**Exit condition:** evidence confirms or kills it — without changing production code yet.

- Debugger: evaluate the expression you suspect.
- A tiny focused test or `jshell`: `jshell> 50 / 100` → `0`.
- If killed, go back to step 2 with the new information. Don't stack speculative edits.

### 5. Fix

**Exit condition:** the reproducing test passes and **the full suite** is no worse than baseline.

- Make the **smallest** change that fixes the **root cause**, not the symptom. (Subtracting one day inside `FineCalculator` would make a test green and leave every due date wrong.)
- Fix one root cause per commit, with a message that says what and why.
- Re-run the full suite, not just the one test.

### 6. Regression test

**Exit condition:** a test exists that fails without the fix and passes with it.

- If you wrote the reproducing test in step 1, you already have it. Otherwise write it now.
- **Prove it can fail:** revert the fix (`git stash`), watch it go red, restore.
- Add the neighbouring cases the bug suggests: boundary ±1, every enum value, null, empty, 3 elements for ordering.
- Ask: *where else could this same mistake exist?* (`grep -rn "/ 100" src/main`, other getters returning fields.)

### Explaining a bug (interview format, 60 seconds)

> **Symptom:** students were never charged fines.
> **Isolation:** standard and staff were correct, so I compared the three discount values.
> **Root cause:** `(100 - 50) / 100` is integer division → 0, so every student fine was multiplied by zero.
> **Fix:** multiply before dividing; money stays in integer cents.
> **Regression:** parameterized test over every `MemberType`, plus a cap-with-discount test.
> **Prevention:** test every enum value, not just the extremes.

---

## Anti-patterns

| Don't | Instead |
|---|---|
| Change code until the test passes | Know *why* it failed before you edit |
| Edit a test's assertion to match the output | Check the spec; the test is usually the spec |
| Wrap it in `try/catch` and move on | Find why the exception happens |
| Five speculative changes at once | One hypothesis, one change, re-run |
| Paste the stack trace into a chatbot and apply its patch | Use it to *explain* a concept if needed, then verify with the debugger yourself |
| Only run the one test you fixed | Run the full suite every time |

## Interview questions this prepares you for

- "Walk me through how you'd debug a `NullPointerException` in production you can't reproduce locally."
- "How do you get productive in a large codebase you've never seen?"
- "What's the `equals`/`hashCode` contract and what breaks if you violate it?"
- "Why would you get a `ConcurrentModificationException` in single-threaded code?"
- "Tell me about the hardest bug you've fixed." — use a real one from your four projects or these drills, and say which.
- "How do you find which commit introduced a bug?" (`git bisect`)
