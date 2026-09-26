# 07 — Concurrency (Week 5 basics · Week 15 deep dive)

> **Outcome:** reproduce a race condition on demand and fix it three ways; explain
> visibility, atomicity and ordering via *happens-before*; choose between `synchronized`,
> `ReentrantLock`, atomics and concurrent collections; run work on `ExecutorService`,
> `CompletableFuture` and **virtual threads**; create, diagnose and fix a deadlock.
> Then apply it in the projects: FlowGrid M2's "N threads, 1 unit in stock → exactly one reservation"
> test (Week 5), LedgerX M2's "$500 balance, two concurrent $400 transfers → exactly one succeeds"
> (Week 10), and ForgeCI's worker pool (Week 15).
>
> **Two passes:**
> - **Week 5 (FlowGrid M2):** §1–3 (threads, race conditions, happens-before, `volatile`, `synchronized`), §10 (deadlock), §11. Enough to write and reason about a concurrency test.
> - **Week 15 (ForgeCI M2):** §4–9 (locks, atomics, concurrent collections, executors, `CompletableFuture`, virtual threads) for the worker pool, leases and graceful shutdown.

Related: [README](./README.md) · prev [06-memory-jvm.md](./06-memory-jvm.md) · OS side (processes, scheduling) [../14-cs-fundamentals/concurrency.md](../14-cs-fundamentals/concurrency.md), [../14-cs-fundamentals/operating-systems.md](../14-cs-fundamentals/operating-systems.md) · DB-level concurrency [../04-sql-databases/06-transactions.md](../04-sql-databases/06-transactions.md) · projects [../18-projects/flowgrid/milestones.md](../18-projects/flowgrid/milestones.md), [../18-projects/ledgerx/milestones.md](../18-projects/ledgerx/milestones.md), [../18-projects/forgeci/milestones.md](../18-projects/forgeci/milestones.md)

---

## 1. Threads: the basics

```java
Runnable task = () -> System.out.println("hi from " + Thread.currentThread().getName());
Thread t = new Thread(task, "worker-1");
t.start();       // new thread runs task.run()   (calling t.run() would run it on THIS thread!)
t.join();        // wait for it to finish

Thread vt = Thread.ofVirtual().name("v-1").start(task);   // Java 21 virtual thread
vt.join();
```

Thread states: `NEW → RUNNABLE ⇄ (BLOCKED | WAITING | TIMED_WAITING) → TERMINATED`.
- `BLOCKED` = waiting to enter a `synchronized` monitor.
- `WAITING` / `TIMED_WAITING` = `join`, `wait`, `LockSupport.park`, `sleep`, lock `await`.
You'll see these words in every thread dump.

**Why concurrency is hard — three separate problems:**

| Problem | Meaning | Example |
|---|---|---|
| **Atomicity** | A compound action can be interleaved | `count++` = read, add, write |
| **Visibility** | One thread's write may never be seen by another (caches, registers, compiler reordering) | a `boolean running` flag loop that never stops |
| **Ordering** | Compiler/CPU can reorder independent instructions | seeing a non-null reference to a half-constructed object |

---

## 2. Race condition — runnable demo and three fixes

Save as `RaceDemo.java` and run `java RaceDemo.java` (JDK 21).

```java
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.IntSupplier;

public class RaceDemo {
    static final int THREADS = 8;
    static final int INCREMENTS = 100_000;

    // ❌ Broken: count++ is read-modify-write, not atomic
    static class UnsafeCounter {
        private int count;
        void increment() { count++; }
        int get() { return count; }
    }

    // ✅ Fix A: intrinsic lock (monitor)
    static class SyncCounter {
        private int count;
        synchronized void increment() { count++; }
        synchronized int get() { return count; }
    }

    // ✅ Fix B: explicit lock
    static class LockCounter {
        private final ReentrantLock lock = new ReentrantLock();
        private int count;
        void increment() { lock.lock(); try { count++; } finally { lock.unlock(); } }
        int get() { lock.lock(); try { return count; } finally { lock.unlock(); } }
    }

    // ✅ Fix C: lock-free compare-and-swap
    static class AtomicCounter {
        private final AtomicInteger count = new AtomicInteger();
        void increment() { count.incrementAndGet(); }
        int get() { return count.get(); }
    }

    static int run(Runnable increment, IntSupplier get) {
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            CountDownLatch start = new CountDownLatch(1);
            for (int t = 0; t < THREADS; t++) {
                pool.submit(() -> {
                    start.await();                       // line everyone up to maximize contention
                    for (int i = 0; i < INCREMENTS; i++) increment.run();
                    return null;                         // Callable, so await() may throw
                });
            }
            start.countDown();
        }   // ExecutorService.close() (Java 19+) waits for all submitted tasks
        return get.getAsInt();
    }

    public static void main(String[] args) {
        UnsafeCounter u = new UnsafeCounter();
        SyncCounter s = new SyncCounter();
        LockCounter l = new LockCounter();
        AtomicCounter a = new AtomicCounter();
        System.out.printf("expected      %,d%n", THREADS * INCREMENTS);
        System.out.printf("unsafe        %,d%n", run(u::increment, u::get));
        System.out.printf("synchronized  %,d%n", run(s::increment, s::get));
        System.out.printf("ReentrantLock %,d%n", run(l::increment, l::get));
        System.out.printf("AtomicInteger %,d%n", run(a::increment, a::get));
    }
}
```

Typical output (the unsafe number changes every run):

```
expected      800,000
unsafe        560,058
synchronized  800,000
ReentrantLock 800,000
AtomicInteger 800,000
```

**Why the unsafe counter loses updates** — two threads interleave:

```
T1: read count (41)
T2: read count (41)
T1: write 42
T2: write 42          ← T1's increment is lost
```

**Why `volatile int count` would NOT fix it:** `volatile` gives visibility and ordering, not
atomicity of `count++`. Try it (Break it #2).

**Check-then-act** is the same bug in disguise — and it's exactly FlowGrid's overselling bug:

```java
// ❌ Two threads both see available == 1, both reserve → available == -1
if (level.available() >= qty) level.reserve(qty);
// ✅ In-JVM version: check + act as ONE atomic step (ConcurrentHashMap<String, Integer> stock)
boolean[] reserved = {false};
stock.computeIfPresent(skuKey, (k, avail) -> {    // runs atomically for this key
    if (avail < qty) return avail;
    reserved[0] = true;
    return avail - qty;
});
```
In FlowGrid the real fix lives in the **database** (`SELECT … FOR UPDATE` on the inventory row, or
optimistic locking with `@Version` and retry), because several app instances don't share a JVM lock.
The JVM-level version is the same idea at a smaller scale — see [../04-sql-databases/06-transactions.md](../04-sql-databases/06-transactions.md).

---

## 3. The Java Memory Model: happens-before

The JMM (JLS §17.4) guarantees that if action A **happens-before** B, then A's effects are
visible to B and ordered before it. Key rules:

| Rule | Consequence |
|---|---|
| Program order | Within one thread, earlier statements happen-before later ones |
| Monitor lock | `unlock` of monitor M happens-before every later `lock` of M |
| `volatile` | A write to volatile v happens-before every later read of v |
| Thread start | `t.start()` happens-before anything in `t` |
| Thread join | Everything in `t` happens-before `t.join()` returns |
| Executor / concurrent collections | Submitting a task happens-before it runs; `put` into a `ConcurrentHashMap` / `BlockingQueue` happens-before the corresponding `get`/`take`; `Future` completion happens-before `get()` returns |
| Transitivity | A hb B and B hb C ⇒ A hb C |
| `final` fields | Correctly constructed objects' final fields are visible to all threads without sync (don't leak `this` from the constructor) |

No happens-before edge between a write and a read = **data race** = the read may see a stale
value, forever.

### `volatile`

```java
public class Worker implements Runnable {
    private volatile boolean running = true;     // remove volatile → loop may never exit (JIT hoists the read)
    public void stop() { running = false; }
    @Override public void run() {
        while (running) { /* poll monitors, do work */ }
    }
}
```
Use `volatile` for: stop flags, publishing an immutable object reference, double-checked
locking's field. Don't use it for compound actions (`++`, check-then-act).

### `synchronized`

```java
public class Inventory {
    private final Map<String, Integer> stock = new HashMap<>();
    public synchronized boolean reserve(String sku, int qty) {  // lock = this
        int have = stock.getOrDefault(sku, 0);
        if (have < qty) return false;
        stock.put(sku, have - qty);
        return true;
    }
    public synchronized void restock(String sku, int qty) { stock.merge(sku, qty, Integer::sum); }
}
```
- Provides **mutual exclusion + visibility** (via the monitor happens-before rule).
- **Reentrant:** a thread holding the lock can re-enter other `synchronized` methods on the same object.
- `static synchronized` locks the `Class` object. Synchronized block: `synchronized (lockObj) { ... }` — prefer a `private final Object lock = new Object();` over `this` so outsiders can't grab your lock.
- Every read **and** write of shared state must use the same lock. Synchronizing only the writer is a bug.

**Under the hood:** each object has a monitor tied to its header's mark word. HotSpot uses
lightweight (CAS-based) locking when uncontended and inflates to a heavyweight OS-backed monitor
under contention. (Biased locking was removed in JDK 18.)

---

## 4. `java.util.concurrent` locks

```java
private final ReentrantLock lock = new ReentrantLock();       // new ReentrantLock(true) = fair

public boolean tryTransfer(Duration timeout) throws InterruptedException {
    if (!lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) return false;  // no infinite wait
    try {
        // critical section
        return true;
    } finally {
        lock.unlock();                                        // ALWAYS in finally
    }
}
```

| | `synchronized` | `ReentrantLock` |
|---|---|---|
| Release | Automatic at block exit | Manual `unlock()` in `finally` |
| Try / timeout | No | `tryLock()`, `tryLock(timeout)` |
| Interruptible wait | No | `lockInterruptibly()` |
| Fairness option | No | Yes |
| Multiple conditions | One wait-set (`wait`/`notify`) | Many `Condition`s (`await`/`signal`) |
| Default choice | ✅ simpler | When you need the extras |

Also: `ReadWriteLock` (many readers or one writer), `StampedLock` (optimistic reads). Know they exist.

---

## 5. Atomics and CAS

```java
AtomicInteger hits = new AtomicInteger();
hits.incrementAndGet();
hits.updateAndGet(x -> Math.min(x + 1, 100));                  // retry loop inside
AtomicReference<Config> current = new AtomicReference<>(initial);
current.compareAndSet(expected, updated);                     // true if swapped
LongAdder requests = new LongAdder();                          // better under heavy contention
requests.increment(); requests.sum();
```
**Under the hood:** CAS = one CPU instruction (e.g. `LOCK CMPXCHG` on x86): "set to new if
currently equal to expected". Atomics loop on CAS until success — lock-free, no blocking, but can
spin under contention. `LongAdder` spreads counts over cells and sums on read.

---

## 6. Concurrent collections

| Use | Class |
|---|---|
| Shared map | `ConcurrentHashMap` |
| Producer/consumer queue | `ArrayBlockingQueue` (bounded), `LinkedBlockingQueue` |
| Read-mostly list (listeners) | `CopyOnWriteArrayList` |
| Sorted concurrent map | `ConcurrentSkipListMap` |

### `ConcurrentHashMap` under the hood (Java 8+)

- Same table-of-bins layout as `HashMap` (incl. treeification).
- Empty bin → insert with **CAS**, no lock. Non-empty bin → `synchronized` on the **bin's first node** only. So writers to different bins never block each other. (Java 7 used 16 fixed `Segment` locks — an old interview answer; know it's outdated.)
- Reads are lock-free (volatile reads of nodes).
- Resizing is done cooperatively by multiple threads, transferring bins in chunks.
- Iterators are **weakly consistent**: never throw `ConcurrentModificationException`, may or may not reflect concurrent updates.
- `size()` is an estimate under concurrent modification (uses `LongAdder`-style counters).
- **No `null` keys or values.**
- Atomic compound ops: `putIfAbsent`, `computeIfAbsent`, `compute`, `merge`. `if (!m.containsKey(k)) m.put(k, v)` is still a race.

```java
ConcurrentHashMap<String, LongAdder> perMonitorFailures = new ConcurrentHashMap<>();
perMonitorFailures.computeIfAbsent(monitorId, k -> new LongAdder()).increment();  // per-key counters, e.g. ForgeCI jobs per project
```

`Collections.synchronizedMap(map)` = one lock for everything, and you must manually lock while iterating. Prefer `ConcurrentHashMap`.

---

## 7. Executors: stop creating threads by hand

```java
ExecutorService pool = Executors.newFixedThreadPool(4);
Future<Integer> f = pool.submit(() -> expensiveComputation());     // Callable<Integer>
try {
    Integer result = f.get(2, TimeUnit.SECONDS);                     // blocks with timeout
} catch (TimeoutException e) {
    f.cancel(true);                                                  // interrupts the task
} catch (ExecutionException e) {
    Throwable real = e.getCause();                                   // the task's exception
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}

// Graceful shutdown (pre-Java-19 style; still what you'll see in code bases)
pool.shutdown();                                                     // no new tasks
if (!pool.awaitTermination(10, TimeUnit.SECONDS)) pool.shutdownNow(); // interrupt stragglers
```

| Factory | Behavior | Watch out |
|---|---|---|
| `newFixedThreadPool(n)` | n threads, **unbounded** queue | Queue can grow until OOM |
| `newCachedThreadPool()` | Creates threads on demand, reuses idle | Unbounded threads under load |
| `newSingleThreadExecutor()` | Sequential execution | — |
| `newScheduledThreadPool(n)` | Delayed / periodic tasks | Exception in a periodic task silently cancels future runs |
| `newVirtualThreadPerTaskExecutor()` | One new virtual thread per task (Java 21) | Don't pool virtual threads |
| `new ThreadPoolExecutor(core, max, keepAlive, unit, queue, factory, rejectionHandler)` | Full control, bounded queue | Production choice for platform threads |

Sizing rule of thumb: CPU-bound → ≈ number of cores; I/O-bound → more (or virtual threads).

**Exceptions in `submit`ted tasks are captured in the `Future`** — if nobody calls `get()`,
they vanish. `execute(Runnable)` sends them to the thread's uncaught-exception handler instead.

**Synchronizers you should recognize:** `CountDownLatch` (wait for N events; used in the demo),
`CyclicBarrier`, `Semaphore` (limit concurrency, e.g. max 50 outbound HTTP checks), `Phaser`.

---

## 8. `CompletableFuture`: composing async work

```java
import java.util.*;
import java.util.concurrent.*;

public class PriceCheck {
    record Price(String vendor, int cents) {}

    static Price fetch(String vendor) {
        try { Thread.sleep(200); }                                     // simulate I/O
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CompletionException(e); }
        if (vendor.equals("broken")) throw new IllegalStateException("vendor down");
        return new Price(vendor, vendor.length() * 100);
    }

    static Throwable unwrap(Throwable ex) {
        return (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
    }

    public static void main(String[] args) {
        try (ExecutorService io = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Price>> futures = List.of("acme", "globex", "broken").stream()
                .map(v -> CompletableFuture.supplyAsync(() -> fetch(v), io)
                        .orTimeout(1, TimeUnit.SECONDS)
                        .exceptionally(ex -> new Price(v + " (failed: " + unwrap(ex).getMessage() + ")", Integer.MAX_VALUE)))
                .toList();

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();   // wait for all
            futures.forEach(f -> System.out.println(f.join()));
            Price best = futures.stream().map(CompletableFuture::join)
                    .min(Comparator.comparingInt(Price::cents)).orElseThrow();
            System.out.println("best = " + best);
        }
    }
}
```

| Method | Like | Purpose |
|---|---|---|
| `supplyAsync(supplier, executor)` | `submit` | Start async work. **Always pass an executor** for I/O; default is the common ForkJoinPool. |
| `thenApply(fn)` | `map` | Transform result |
| `thenCompose(fn)` | `flatMap` | Chain another async call |
| `thenCombine(other, fn)` | zip | Combine two independent results |
| `allOf` / `anyOf` | — | Wait for all / first |
| `exceptionally`, `handle`, `whenComplete` | catch / finally | Error handling |
| `orTimeout`, `completeOnTimeout` (Java 9) | — | Timeouts |
| `join()` vs `get()` | — | `join` throws unchecked `CompletionException`; `get` throws checked `ExecutionException` |

Exceptions thrown inside a stage arrive in dependent stages wrapped in `CompletionException`
— hence `unwrap`.

---

## 9. Virtual threads (Java 21)

A **platform thread** is a thin wrapper over an OS thread (~1 MB stack reserved, expensive to
create, a few thousand per JVM). A **virtual thread** is a JVM-managed thread whose stack lives
on the heap; the JVM mounts it on a small pool of **carrier** platform threads (a ForkJoinPool
sized to CPU cores) and **unmounts it when it blocks** on I/O, `sleep`, locks from
`java.util.concurrent`, etc. You can have millions.

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 10_000; i++) {
        executor.submit(() -> { Thread.sleep(Duration.ofSeconds(1)); return null; });
    }
}   // ≈ 1 second total, not 10 000 seconds and not 10 000 OS threads
```

Rules:
- They make **blocking I/O code scale** (thread-per-request; ForgeCI workers waiting on Docker/Redis; FlagForge SDK polling). They do **not** make CPU-bound code faster.
- **Don't pool** them — create one per task.
- **Pinning (Java 21):** blocking inside a `synchronized` block (or native frame) pins the virtual thread to its carrier, wasting it. Prefer `ReentrantLock` around blocking calls in virtual-thread code. (JDK 24 removed most `synchronized` pinning; on 21 it still applies.) Diagnose with `-Djdk.tracePinnedThreads=full`.
- Limit concurrency to a downstream resource with a `Semaphore`, not by pool size.
- `ThreadLocal`s work but with millions of threads can be costly.
- Spring Boot 3.2+: `spring.threads.virtual.enabled=true`.

---

## 10. Deadlock — create it, see it, fix it

Four conditions (Coffman): mutual exclusion, hold-and-wait, no preemption, **circular wait**.
Break any one → no deadlock. In practice you break *circular wait* with a global lock order.

```java
public class DeadlockDemo {
    static final class Account {
        final int id; long balance;
        Account(int id, long balance) { this.id = id; this.balance = balance; }
    }

    // ❌ Locks in argument order: thread 1 locks A→B, thread 2 locks B→A
    static void transferUnsafe(Account from, Account to, long amount) {
        synchronized (from) {
            sleep(50);                                   // widen the window: deadlock is now reliable
            synchronized (to) { from.balance -= amount; to.balance += amount; }
        }
    }

    // ✅ Global ordering by id: every thread locks the lower id first
    static void transferSafe(Account from, Account to, long amount) {
        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;
        synchronized (first) {
            sleep(50);
            synchronized (second) { from.balance -= amount; to.balance += amount; }
        }
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) throws InterruptedException {
        boolean safe = args.length > 0 && args[0].equals("safe");
        Account a = new Account(1, 100), b = new Account(2, 100);
        Thread x = new Thread(() -> { if (safe) transferSafe(a, b, 10); else transferUnsafe(a, b, 10); }, "transfer-A-to-B");
        Thread y = new Thread(() -> { if (safe) transferSafe(b, a, 20); else transferUnsafe(b, a, 20); }, "transfer-B-to-A");
        x.start(); y.start();
        x.join(2000); y.join(2000);
        if (x.isAlive() || y.isAlive()) {
            System.out.println("DEADLOCK: run `jstack " + ProcessHandle.current().pid() + "` in another terminal");
            Thread.sleep(30_000);                        // keep the process alive so you can inspect it
            System.exit(1);
        }
        System.out.println("done: a=" + a.balance + " b=" + b.balance);
    }
}
```

```bash
java DeadlockDemo.java          # prints DEADLOCK + pid
jstack <pid>                     # or: jcmd <pid> Thread.print
java DeadlockDemo.java safe      # done: a=110 b=90
```

`jstack` ends with something like:

```
Found one Java-level deadlock:
=============================
"transfer-A-to-B":
  waiting to lock monitor 0x... (object 0x..., a DeadlockDemo$Account),
  which is held by "transfer-B-to-A"
"transfer-B-to-A":
  waiting to lock monitor 0x... (object 0x..., a DeadlockDemo$Account),
  which is held by "transfer-A-to-B"
```

Other fixes: `tryLock(timeout)` and back off (breaks hold-and-wait); hold one coarse lock;
avoid calling unknown code (listeners) while holding a lock. **Database deadlocks** are the same
idea across rows — PostgreSQL detects them and aborts one transaction (see
[../04-sql-databases/06-transactions.md](../04-sql-databases/06-transactions.md)).

Related liveness failures: **livelock** (threads keep reacting to each other, no progress),
**starvation** (a thread never gets the lock — fair locks help).

---

## 11. Applying it: the project concurrency tests (FlowGrid M2, LedgerX M2)

You write these tests yourself; the milestone specs define the requirements. The reusable part
is the **harness**: start N tasks behind one latch so they collide, collect outcomes, assert an
invariant. Skeleton (fill in the arrange/act/assert for your own services):

```java
@Test
void exactlyOneOfNConcurrentReservationsWins() throws Exception {
    int n = 20;
    // arrange: one SKU with available = 1 in one warehouse (Testcontainers Postgres, not H2)
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(n)) {
        for (int i = 0; i < n; i++) {
            results.add(pool.submit(() -> {
                start.await();                 // all threads released at once
                return tryToReserveOneUnit();   // your call: true if reserved, false if rejected
            }));
        }
        start.countDown();
    }
    long winners = 0;
    for (Future<Boolean> f : results) if (f.get()) winners++;
    assertEquals(1, winners);
    // also assert the DB invariant: available == 0 and never negative
}
```

What to be able to explain after writing it:
- Why the test must hit a **real Postgres** (row locks, isolation) and why a `synchronized` service method would pass the test but fail with two app instances.
- Why the latch matters (without it threads start staggered and the race rarely happens).
- The LedgerX variant: two concurrent $400 transfers from a $500 account → exactly one succeeds; the balance is never negative; accounts locked in **id order** to avoid deadlocks (§10).

---

## 🔨 Break it

1. Run `RaceDemo` 5 times; record the unsafe numbers. Set `THREADS = 1` → always correct. Why?
2. Make `UnsafeCounter.count` `volatile` → still wrong. Explain atomicity vs visibility.
3. Synchronize only `increment()` but not `get()` in `SyncCounter` — why is that still a (visibility) bug even if the test passes?
4. Remove `volatile` from `Worker.running`, run the loop with an empty body, call `stop()` from main after 1 s. On many JVMs it never stops (JIT hoists the read).
5. Run `DeadlockDemo`, take a thread dump with `jcmd <pid> Thread.print`, find the cycle.
6. Submit a task that throws to an `ExecutorService` via `submit` and never call `get()` → exception disappears. Switch to `execute` → stack trace printed.
7. Iterate a `HashMap` in one thread while another inserts → `ConcurrentModificationException` (or worse). Swap to `ConcurrentHashMap`.
8. Run 10 000 `Thread.sleep(1s)` tasks on `newFixedThreadPool(100)` vs virtual threads; time both.

## ⚠️ Common mistakes

- Calling `run()` instead of `start()`.
- `unlock()` not in `finally`.
- Check-then-act on concurrent collections (`containsKey` + `put`).
- Swallowing `InterruptedException`.
- Unbounded queues/pools; never shutting executors down (JVM won't exit — non-daemon threads).
- Doing blocking I/O inside `synchronized` with virtual threads on Java 21 (pinning).
- Believing `ConcurrentHashMap` makes *multi-step* logic atomic.
- Using `parallelStream()` for I/O.

## 🎤 Interview questions

<details><summary>1. What is a race condition? Give an example and three fixes.</summary>

Correctness depends on the interleaving of threads. `count++` from multiple threads loses updates
(read-modify-write). Fixes: `synchronized`, `ReentrantLock`, `AtomicInteger` (CAS). Or avoid
shared mutable state (confinement, immutability).
</details>

<details><summary>2. What does volatile guarantee? What doesn't it?</summary>

Visibility (a write is seen by subsequent reads in other threads) and ordering (no reordering
across the volatile access; it creates a happens-before edge). Not atomicity of compound actions like `++`.
</details>

<details><summary>3. Explain happens-before.</summary>

The JMM's partial order: if A happens-before B, A's writes are visible to B. Established by
program order, monitor unlock→lock, volatile write→read, thread start/join, executor submission,
concurrent-collection handoffs, and transitivity. Without it you have a data race.
</details>

<details><summary>4. synchronized vs ReentrantLock?</summary>

Both mutual exclusion + visibility + reentrancy. `synchronized` is simpler and auto-released.
`ReentrantLock` adds `tryLock` with timeout, interruptible acquisition, fairness, multiple
`Condition`s, and (on Java 21) avoids virtual-thread pinning. Must `unlock()` in `finally`.
</details>

<details><summary>5. How does ConcurrentHashMap achieve thread safety?</summary>

CAS for inserting into empty bins, `synchronized` on the first node of a bin for other writes,
volatile lock-free reads, cooperative resizing. Fine-grained, so different bins don't contend.
Iterators weakly consistent; nulls not allowed; use `compute`/`merge`/`putIfAbsent` for atomic compound updates.
</details>

<details><summary>6. What is a deadlock and how do you prevent it?</summary>

Threads each hold a lock and wait for another's, forming a cycle. Needs mutual exclusion,
hold-and-wait, no preemption, circular wait. Prevent with a global lock order, `tryLock` with
timeout and retry, coarser locking, or not holding locks while calling out. Detect with `jstack`/`jcmd Thread.print`.
</details>

<details><summary>7. What are virtual threads and when would you use them?</summary>

Lightweight JVM-scheduled threads (Java 21) mounted on a few carrier threads and unmounted when
blocking. Use for high-concurrency blocking I/O (HTTP calls, DB calls) with simple thread-per-task
code. Don't pool them; they don't speed up CPU-bound work; watch for pinning in `synchronized` on 21.
</details>

<details><summary>8. Future vs CompletableFuture?</summary>

`Future` only lets you block (`get`), poll (`isDone`) or cancel. `CompletableFuture` can be
completed explicitly and composed without blocking: `thenApply`, `thenCompose`, `thenCombine`,
`allOf`, `exceptionally`, timeouts.
</details>

<details><summary>9. What happens to an exception thrown inside an ExecutorService task?</summary>

With `submit`, it's stored in the `Future` and rethrown wrapped in `ExecutionException` on
`get()`; if nobody calls `get`, it's silently lost. With `execute`, it propagates to the worker
thread's uncaught exception handler (printed by default) and the worker is replaced.
</details>

<details><summary>10. How does FlowGrid prevent overselling under concurrency?</summary>

(Answer from what you actually built.) At the database level, because the API can run as multiple
instances: the reservation transaction locks the inventory row (`SELECT … FOR UPDATE`) or uses
optimistic locking (`@Version` + retry), checks `available >= qty`, and updates in the same
transaction; losers get a 409 ProblemDetail. Proven by a Testcontainers test that releases N
threads through a `CountDownLatch` against one unit of stock and asserts exactly one reservation
succeeds and `available` never goes negative. Be ready to compare the pessimistic and optimistic
versions you measured.
</details>

## ✅ Mastery checklist

- [ ] Write `RaceDemo` from memory, run it, fix it three ways
- [ ] Explain atomicity vs visibility vs ordering; list 5 happens-before rules
- [ ] Use `volatile` correctly for a stop flag and explain why it fails for counters
- [ ] Use `ReentrantLock` with `tryLock` and `finally`
- [ ] Explain `ConcurrentHashMap` internals and use `merge`/`computeIfAbsent`
- [ ] Use `ExecutorService` with `Future.get(timeout)` and shut it down gracefully
- [ ] Compose `CompletableFuture`s with a timeout and error fallback
- [ ] Explain virtual threads, carriers, pinning; run 10 000 blocking tasks
- [ ] Create a deadlock, read it in `jstack`, fix with lock ordering
- [ ] Write FlowGrid's N-threads-one-unit test (W5) and LedgerX's $500/$400/$400 test (W10)
- [ ] Complete the Week 5 and Week 15 concurrency exercises in [exercises.md](./exercises.md#week-15--concurrency-deep-dive)
