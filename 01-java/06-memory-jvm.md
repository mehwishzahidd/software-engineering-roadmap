# 06 — Memory & the JVM (Week 5)

> **Outcome:** explain what lives on the stack vs the heap for any snippet; prove Java is
> pass-by-value; explain the String pool and Integer cache; name the JVM memory areas, GC
> generations and what the JIT does; recognize a memory leak and the tools to find it.

Related: [README](./README.md) · prev [05-exceptions-io.md](./05-exceptions-io.md) · next [07-concurrency.md](./07-concurrency.md) · CS depth [../14-cs-fundamentals/memory-and-architecture.md](../14-cs-fundamentals/memory-and-architecture.md) · C++ contrast [../20-cpp-basics/README.md](../20-cpp-basics/README.md)

---

## 1. Stack vs heap

```java
public class Demo {
    public static void main(String[] args) {
        int count = 3;                              // stack (main's frame): the value 3
        String label = "total";                     // stack: reference → pooled "total" on heap
        List<Integer> xs = new ArrayList<>();       // stack: reference → ArrayList object on heap
        xs.add(count);                              // heap: Integer(3) (cached), referenced from the array
        int r = square(count);                      // new frame pushed for square
    }
    static int square(int n) { int result = n * n; return result; }  // n, result in square's frame
}
```

| | Stack (per thread) | Heap (shared) |
|---|---|---|
| Holds | Frames: local primitives, references, operand stack, return address | All objects and arrays (incl. their fields) |
| Lifetime | Frame popped when the method returns | Until unreachable, then GC reclaims |
| Size | Small (≈ 512 KB–1 MB default, `-Xss`) | Large (`-Xms` initial, `-Xmx` max) |
| Error when full | `StackOverflowError` (deep/infinite recursion) | `OutOfMemoryError: Java heap space` |
| Thread-safety | Private to the thread → locals are thread-safe | Shared → needs synchronization |

**Nuance for interviews:** "objects live on the heap" is the *language model*. The JIT's
**escape analysis** can avoid heap allocation for objects that never escape a method (scalar
replacement). You still reason with the heap model.

---

## 2. Pass-by-value, always

```java
record Box(int v) {}
static class Counter { int n; }

static void reassign(Counter c) { c = new Counter(); c.n = 100; }  // changes local copy of the reference
static void mutate(Counter c)   { c.n = 100; }                     // follows reference, changes shared object
static void swap(Integer a, Integer b) { Integer t = a; a = b; b = t; } // no effect on caller

Counter k = new Counter();
reassign(k); System.out.println(k.n);   // 0
mutate(k);   System.out.println(k.n);   // 100
```

Draw it:

```
main frame           heap
k ──────────────►  Counter{n=0}
reassign frame
c (copy of k) ──►  (same Counter) ... then c ──► new Counter{n=100} (garbage after return)
```

This is why you can't write `swap(a, b)` for two variables in Java, and why DSA helpers pass
arrays or return values (e.g., return `int[]{min, max}` or use a small record).

---

## 3. JVM runtime memory areas

```
┌──────────────────────────── JVM process ─────────────────────────────┐
│  Heap (shared, GC-managed)                                           │
│   ├─ Young gen: Eden │ Survivor S0 │ Survivor S1                     │
│   └─ Old gen (tenured)                                               │
│  Metaspace (native memory): class metadata, method bytecode          │
│  Code cache: JIT-compiled native code                                │
│  Per thread: JVM stack (frames) · PC register · native method stack  │
│  Direct/off-heap buffers, GC data structures, thread stacks (native) │
└──────────────────────────────────────────────────────────────────────┘
```

- **Metaspace** (Java 8+) replaced PermGen; lives in native memory, grows by default, capped with `-XX:MaxMetaspaceSize`. Leaks here come from classloader leaks (redeploying apps in app servers).
- **String pool** lives in the heap (moved from PermGen in Java 7).
- Container note (every project runs in Docker from Week 5; ForgeCI workers and FlagForge's API on EC2): the JVM's total footprint = heap + metaspace + code cache + thread stacks + direct buffers. Setting `-Xmx` equal to the container limit gets you OOM-killed. Modern JVMs are container-aware; `-XX:MaxRAMPercentage=75` is a common setting.

---

## 4. The String pool

```java
String a = "ledger";                   // literal → interned in the pool at class load/first use
String b = "ledger";                   // same pooled object
String c = new String("ledger");       // new heap object (copy)
String d = c.intern();                 // returns the pooled instance
String e = "led" + "ger";              // compile-time constant → folded → pooled
String part = "led";
String f = part + "ger";               // runtime concat → new object

System.out.println(a == b);  // true
System.out.println(a == c);  // false
System.out.println(a == d);  // true
System.out.println(a == e);  // true
System.out.println(a == f);  // false
```
Takeaway: `==` "sometimes works" for strings, which is worse than never working. Use `equals`.

---

## 5. Autoboxing and the Integer cache

Autoboxing = compiler inserts `Integer.valueOf(x)`; unboxing = `x.intValue()`.

```java
Integer a = 127, b = 127;
Integer c = 128, d = 128;
System.out.println(a == b);        // true  — Integer.valueOf caches −128..127
System.out.println(c == d);        // false — two distinct objects
System.out.println(c.equals(d));   // true
System.out.println(c == 128);      // true  — mixed: c is unboxed, primitive comparison

Integer missing = null;
int boom = missing;                 // NullPointerException (unboxing null)

Long sum = 0L;
for (long i = 0; i < 1_000_000; i++) sum += i;   // creates ~1M Long objects; use `long`
```
- Cache range −128..127 is guaranteed; upper bound tunable (`-XX:AutoBoxCacheMax`). `Long`, `Short`, `Byte`, `Character` (0..127) also cache; `Double`/`Float` don't.
- In DSA, `Map<Integer, Integer>` and `List<Integer>` box constantly; that's fine for correctness, but use `int[]` when the key space is small and dense.

---

## 6. Garbage collection

**Reachability:** an object is live if reachable from a **GC root** — local variables in
active frames, static fields, active threads, JNI references. Everything else is garbage, even
cycles (Java doesn't use reference counting).

**Generational hypothesis:** most objects die young. So the heap is split:

1. New objects are allocated in **Eden** (fast bump-pointer allocation in thread-local buffers, TLABs).
2. **Minor GC** (young collection): live objects in Eden + one survivor are *copied* to the other survivor; age++. Cost ∝ live objects, not garbage — cheap when most die.
3. Objects surviving enough collections (tenuring threshold) are **promoted** to the **Old gen**.
4. **Major/mixed/full GC** collects the old gen: more expensive.
5. **Stop-the-world (STW)** pauses: application threads halt for (parts of) collection. Modern collectors do most work concurrently.

| Collector | Default when | Trait |
|---|---|---|
| **G1** | Default since Java 9 (server-class machines) | Region-based heap, mixed collections, pause-time goal (`-XX:MaxGCPauseMillis=200`) |
| **ZGC** (generational in Java 21 with `-XX:+ZGenerational`) | Opt-in: `-XX:+UseZGC` | Sub-millisecond pauses, large heaps |
| **Parallel** | Opt-in | Max throughput, longer pauses (batch jobs) |
| **Serial** | Small heaps / 1 CPU containers | Single-threaded |

`System.gc()` is only a hint. `finalize()` is deprecated for removal; use try-with-resources or `Cleaner`.

### Memory leaks in a GC'd language

A Java leak = objects that are **reachable but no longer needed**:
- Ever-growing `static` collections / caches without eviction (FlagForge SDK's local snapshot must be replaced, not appended to; use Redis TTLs or bounded caches).
- Listeners registered and never removed (Observer!).
- `ThreadLocal` values in thread pools not removed (request-ID MDC in FlowGrid's logging filter — clear it in `finally`).
- Non-static inner classes/lambdas capturing a large outer object.
- Mutable keys in `HashMap` (entry unreachable by lookup but still referenced).

Symptoms: heap usage after each full GC trends upward → eventually `OutOfMemoryError`.
Diagnosis: heap dump + dominator tree (see [09-debugging-java.md](./09-debugging-java.md#6-jvm-diagnostic-tools)).

---

## 7. Execution: classloading, interpreter, JIT

### Classloading (basics)

1. **Loading** — find bytes for `com.example.flowgrid.FlowGridApplication` (from classpath/module path/jar).
2. **Linking** — *verify* bytecode safety, *prepare* static fields with defaults, *resolve* symbolic references (often lazily).
3. **Initialization** — run static initializers and static field assignments, **once**, thread-safely, on first active use (that's why the holder-idiom singleton is lazy and thread-safe).

Loaders form a hierarchy with **parent delegation**: Bootstrap (core `java.*`) → Platform →
Application (your classpath). A loader asks its parent first, so you can't replace
`java.lang.String` with your own. The same class name loaded by two loaders = two different
classes (`ClassCastException: X cannot be cast to X` in app servers).

### Interpreter + JIT

- Bytecode starts **interpreted**. The JVM profiles which methods/loops are hot.
- **Tiered compilation:** C1 compiles quickly with light optimization; very hot code is recompiled by **C2** with aggressive optimization using runtime profile data: inlining, escape analysis, loop unrolling, dead-code elimination, devirtualization of monomorphic calls.
- If an assumption breaks (a new subclass is loaded), code is **deoptimized** back to the interpreter.
- Consequence: **benchmarks need warm-up**. A naïve `System.nanoTime()` loop measures interpreter + compilation. Use JMH for real benchmarks.

---

## 8. Useful JVM flags

| Flag | Meaning |
|---|---|
| `-Xms512m -Xmx512m` | Initial / max heap |
| `-Xss1m` | Thread stack size |
| `-XX:MaxRAMPercentage=75` | Heap as % of container memory |
| `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp` | Dump heap on OOM (always set this in prod) |
| `-Xlog:gc` / `-Xlog:gc*:file=gc.log` | Unified GC logging (Java 9+) |
| `-XX:+UseZGC` / `-XX:+UseG1GC` | Pick a collector |
| `-XX:+PrintFlagsFinal` | See all effective flag values |

---

## 🔨 Break it

1. **Stack overflow:** `static int f(int n) { return f(n + 1) + 1; }` → note the trace length. Run again with `-Xss4m` → deeper.
2. **Heap OOM:** `List<byte[]> hog = new ArrayList<>(); while (true) hog.add(new byte[1_000_000]);` with `java -Xmx64m -XX:+HeapDumpOnOutOfMemoryError Hog.java`. Open the `.hprof` in VisualVM or Eclipse MAT.
3. **GC log:** run an allocation-heavy loop with `-Xlog:gc`; identify young vs old pauses.
4. **Integer cache:** compare `==` for 127 vs 128; then run with `-XX:AutoBoxCacheMax=1000`.
5. **String pool:** run the table in §4; predict each line first.
6. **JIT warm-up:** time the same method 10 times in a loop; watch the first iterations be slower. Add `-XX:+PrintCompilation` to see methods being compiled.
7. **Leak:** static `List<String>` that every "request" appends to; watch heap with `jcmd <pid> GC.heap_info` over time.

## ⚠️ Common mistakes

- Saying "Java passes objects by reference".
- Thinking GC prevents all leaks.
- Calling `System.gc()` to "fix" memory.
- Micro-benchmarks without warm-up.
- Setting `-Xmx` equal to the container limit.
- Unbounded caches (`static Map`) "for performance".

## 🎤 Interview questions

<details><summary>1. What's stored on the stack vs the heap?</summary>

Stack: one frame per method call per thread, holding local primitives and references, parameters,
return address. Heap: every object and array (and their fields, including primitive fields),
shared by all threads, managed by GC. A local `int` is on the stack; an `int` field of an object is on the heap.
</details>

<details><summary>2. Is Java pass-by-value or pass-by-reference? Prove it.</summary>

Pass-by-value. Reassigning a parameter inside a method (`c = new Counter()`) doesn't change the
caller's variable; you can't write a working `swap(a, b)`. Mutating the object *through* the
copied reference is visible because both references point to the same object.
</details>

<details><summary>3. How does garbage collection work in Java?</summary>

Tracing from GC roots marks reachable objects; the rest is reclaimed. The heap is generational:
Eden → survivors → old gen. Minor GCs copy survivors (cheap when most objects die young); old
gen is collected less often. G1 is the default; ZGC offers sub-millisecond pauses. Some phases stop the world.
</details>

<details><summary>4. Can Java have memory leaks?</summary>

Yes — objects that stay reachable but are no longer needed: static collections, unremoved
listeners, `ThreadLocal`s in pools, caches without eviction. Find them with heap dumps, dominator trees and GC logs.
</details>

<details><summary>5. What's the difference between StackOverflowError and OutOfMemoryError?</summary>

`StackOverflowError`: a thread's stack exhausted, usually unbounded recursion. `OutOfMemoryError`:
heap (or metaspace, or native threads) can't satisfy an allocation even after GC. Both are `Error`s.
</details>

<details><summary>6. What is the JIT compiler?</summary>

Just-In-Time compiler that turns hot bytecode into optimized native code at runtime, using
profiling data (inlining, escape analysis, devirtualization). HotSpot uses tiered C1/C2
compilation and can deoptimize when assumptions change.
</details>

<details><summary>7. What happens when you run <code>new Foo()</code>?</summary>

If `Foo` isn't initialized: load, link, initialize (static init). Then allocate memory on the heap
(usually in the thread's TLAB in Eden), zero the fields, set the object header, run instance
initializers and the constructor chain (super first), and return the reference.
</details>

<details><summary>8. What is Metaspace?</summary>

Native memory area (Java 8+) holding class metadata, replacing PermGen. Grows dynamically;
exhausted typically by classloader leaks or generating many classes → `OutOfMemoryError: Metaspace`.
</details>

## ✅ Mastery checklist

- [ ] Annotate any 10-line snippet with what's on the stack and heap
- [ ] Prove pass-by-value with `reassign` vs `mutate`
- [ ] Explain the String pool table in §4 line by line
- [ ] Explain Integer cache and the unboxing NPE
- [ ] Draw the JVM memory areas and heap generations from memory
- [ ] Explain minor vs major GC, promotion, STW, and name G1 and ZGC
- [ ] Explain classloading's three phases and parent delegation
- [ ] Produce a `StackOverflowError`, an OOM + heap dump, and a GC log
- [ ] Complete the Week 5 memory exercises in [exercises.md](./exercises.md#week-5--memory-jvm--thread-basics)
