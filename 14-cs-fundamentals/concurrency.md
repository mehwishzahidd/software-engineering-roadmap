# Concurrency Theory (Week 13)

> **Practical use:** reason about the P2 "N threads hold the same seat → exactly one wins" test,
> size P4's checker pool, and recognise a deadlock from a thread dump.
> **Interview use:** race condition, mutex vs semaphore, deadlock conditions, producer-consumer, thread pools.

The Java API side (`synchronized`, `ReentrantLock`, atomics, `ConcurrentHashMap`, `CompletableFuture`,
virtual threads) is in [`01-java/07-concurrency.md`](../01-java/07-concurrency.md). This file is the
theory that explains *why* those tools exist. Read them together.

---

## 1. Concurrency vs parallelism

| | Concurrency | Parallelism |
|---|---|---|
| Meaning | Multiple tasks **in progress** over the same period (interleaved) | Multiple tasks **executing at the same instant** |
| Needs multiple cores? | No | Yes |
| Example | One Tomcat handling 200 requests, most waiting on DB | `parallelStream()` summing on 8 cores |

Concurrency is about *structure* (dealing with many things); parallelism is about *execution*
(doing many things at once).

---

## 2. Race conditions

A **race condition** is when correctness depends on the timing/interleaving of threads. Two shapes:

### Read-modify-write

```java
class Counter {
    private int count;
    void increment() { count++; }   // read count → add 1 → write count: 3 steps
}
```

```
Thread A: read 5            add → 6      write 6
Thread B:        read 5            add → 6      write 6     → one increment lost
```

### Check-then-act

```java
if (!seatHeld(seatId)) {        // check
    holdSeat(seatId, userId);   // act — another thread may have acted in between
}
```

This is exactly P2's double-booking bug. In-memory fixes: make check+act **atomic** (lock, `ConcurrentHashMap.putIfAbsent`,
`AtomicInteger.incrementAndGet`, `compareAndSet`). With multiple app instances, in-JVM locks don't
help → the database must enforce it: unique constraint, `SELECT … FOR UPDATE`, or **optimistic
locking** with `@Version` (`UPDATE … WHERE id = ? AND version = ?` → 0 rows = someone else won).
See [database-internals.md §4](./database-internals.md#4-mvcc-and-locking).

A **data race** (Java Memory Model term) is narrower: two threads access the same variable, at least
one writes, without a happens-before relation. Data races cause visibility bugs even for single
reads/writes.

---

## 3. Visibility and the memory model (the part people forget)

CPUs cache values and compilers reorder instructions. Without synchronization, a thread may **never
see** another thread's write.

```java
class Worker implements Runnable {
    private boolean running = true;     // BUG: add volatile
    public void run() { while (running) { /* work */ } }
    public void stop() { running = false; }   // may never be observed by run()
}
```

**Happens-before** guarantees visibility: unlocking a monitor happens-before the next lock of it;
a `volatile` write happens-before subsequent reads; `Thread.start()` and `join()` create edges;
`ExecutorService.submit` / `Future.get` too.

| Tool | Atomicity | Visibility | Use for |
|---|---|---|---|
| `volatile` | Single read/write only | ✅ | Flags, publishing immutable objects |
| `synchronized` / `Lock` | ✅ for the block | ✅ | Compound actions |
| `AtomicInteger` etc. (CAS) | ✅ single variable ops | ✅ | Counters, sequences |
| Immutable objects (`final` fields, records) | N/A — no writes | ✅ after safe publication | Share freely |

---

## 4. Critical sections and synchronization primitives

A **critical section** is code that accesses shared state and must not run in more than one thread
at a time. Requirements: mutual exclusion, progress, bounded waiting.

| Primitive | Idea | Java |
|---|---|---|
| **Mutex (lock)** | One owner at a time; only the owner unlocks | `synchronized`, `ReentrantLock` |
| **Semaphore** | Counter of permits; `acquire` blocks at 0; anyone may `release` | `java.util.concurrent.Semaphore` |
| **Binary semaphore** | Semaphore with 1 permit — like a mutex without ownership | `new Semaphore(1)` |
| **Monitor** | Mutex + condition variables bundled with an object | Every Java object: `synchronized` + `wait`/`notify` |
| **Condition variable** | Wait until a predicate holds, releasing the lock meanwhile | `Object.wait/notifyAll`, `Lock.newCondition()` |
| **Read-write lock** | Many readers or one writer | `ReentrantReadWriteLock` |
| **CAS (compare-and-swap)** | Hardware atomic "set if still equals expected" → lock-free algorithms | `AtomicReference.compareAndSet` |
| **Latch / barrier** | Wait for N events / N threads | `CountDownLatch`, `CyclicBarrier` |

**Mutex vs semaphore** (classic question): a mutex protects a *resource* — exclusive, owned. A
semaphore *counts* — limits concurrency to N (e.g. max 10 concurrent outbound HTTP checks per
host in P4) or signals between threads.

```java
// Limit concurrent checks against the same host to 5
private final Semaphore perHost = new Semaphore(5);

void check(Monitor m) throws InterruptedException {
    perHost.acquire();
    try {
        http.send(request(m), BodyHandlers.discarding());
    } finally {
        perHost.release();   // always in finally
    }
}
```

**Reentrancy:** Java locks are reentrant — a thread holding a lock can acquire it again (a
`synchronized` method calling another `synchronized` method on the same object doesn't deadlock).

---

## 5. Deadlock

Threads wait on each other forever. **All four Coffman conditions** must hold:

| Condition | Meaning | Break it by |
|---|---|---|
| **Mutual exclusion** | Resources can't be shared | Lock-free / immutable data (often impossible) |
| **Hold and wait** | Holding one resource while waiting for another | Acquire everything at once, or release before requesting |
| **No preemption** | Resources can't be forcibly taken | `tryLock(timeout)`; back off and retry |
| **Circular wait** | A waits for B, B waits for A | **Global lock ordering** (most practical) |

```java
// Deadlock-prone transfer
void transfer(Account from, Account to, BigDecimal amt) {
    synchronized (from) {
        synchronized (to) { from.debit(amt); to.credit(amt); }
    }
}
// Thread 1: transfer(a, b)   Thread 2: transfer(b, a)  → each holds one, waits for the other

// Fix: order locks by a stable key
void transferSafe(Account x, Account y, BigDecimal amt) {
    Account first  = x.id() < y.id() ? x : y;
    Account second = x.id() < y.id() ? y : x;
    synchronized (first) {
        synchronized (second) { x.debit(amt); y.credit(amt); }
    }
}
```

Databases have the same problem: two transactions updating rows in opposite order. Postgres detects
it and aborts one (`ERROR: deadlock detected`) → your code must **retry** the transaction. Consistent
update order (e.g. by primary key) prevents it.

Detection in Java: `jcmd <pid> Thread.print` prints "Found one Java-level deadlock"; also
`ThreadMXBean.findDeadlockedThreads()`.

### Livelock and starvation

- **Livelock:** threads keep reacting to each other and make no progress (two people in a corridor
  stepping aside the same way). Common with naive retry loops → add **randomized backoff (jitter)**.
- **Starvation:** a thread never gets the resource (unfair locks, low priority, a greedy reader
  starving writers). Fix: fair locks (`new ReentrantLock(true)`), bounded queues, time limits.

---

## 6. Producer-consumer

Producers put work into a bounded buffer; consumers take it. The buffer decouples rates and applies
**backpressure** when full. It's the in-process version of a message queue — P4's scheduler producing
"check due" tasks for checker workers.

With `BlockingQueue` (what you'd write in practice):

```java
BlockingQueue<Monitor> queue = new ArrayBlockingQueue<>(1_000);

// Producer (scheduler): every second, enqueue monitors whose next check is due
void schedule() throws InterruptedException {
    for (Monitor m : repo.findDue(Instant.now())) {
        queue.put(m);                     // blocks if full → natural backpressure
    }
}

// Consumers (checkers)
Runnable consumer = () -> {
    try {
        while (!Thread.currentThread().isInterrupted()) {
            Monitor m = queue.take();     // blocks if empty
            checker.check(m);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // restore flag, exit
    }
};
```

From scratch with a monitor (the interview whiteboard version):

```java
final class BoundedBuffer<T> {
    private final Deque<T> items = new ArrayDeque<>();
    private final int capacity;
    BoundedBuffer(int capacity) { this.capacity = capacity; }

    public synchronized void put(T item) throws InterruptedException {
        while (items.size() == capacity) wait();   // while, not if: spurious wakeups
        items.addLast(item);
        notifyAll();
    }

    public synchronized T take() throws InterruptedException {
        while (items.isEmpty()) wait();
        T item = items.removeFirst();
        notifyAll();
        return item;
    }
}
```

Key points to say: `wait()` releases the monitor; always re-check the condition in a `while` loop;
`notifyAll` over `notify` unless you're sure all waiters are equivalent.

---

## 7. Thread pools

Creating a platform thread per task is expensive and unbounded. A **thread pool** reuses a fixed set
of workers fed from a queue.

```java
ExecutorService pool = new ThreadPoolExecutor(
        8, 8,                                   // core, max
        0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(500),          // bounded queue!
        Thread.ofPlatform().name("checker-", 0).factory(),
        new ThreadPoolExecutor.CallerRunsPolicy()); // backpressure when full
```

| Decision | Guidance |
|---|---|
| Size | CPU-bound ≈ cores; I/O-bound larger (or virtual threads) — see [operating-systems.md §3](./operating-systems.md#3-context-switches-and-scheduling) |
| Queue | **Bounded.** `Executors.newFixedThreadPool` uses an *unbounded* queue → memory grows until OOM under overload |
| Rejection | Abort (throw), CallerRuns (slow the producer), Discard (drop) — choose deliberately |
| Shutdown | `shutdown()` + `awaitTermination()`; Spring-managed executors do this for you |
| Exceptions | Exceptions in `submit()` tasks are captured in the `Future` — if you never call `get()` they vanish; log inside the task |
| ThreadLocal | Pool threads are reused → clear `ThreadLocal`/MDC after each task, or you leak request IDs between tasks |
| Virtual threads | `Executors.newVirtualThreadPerTaskExecutor()` — no pooling needed; limit concurrency with a `Semaphore` instead |

**Little's law** for sizing: concurrent requests in the system = arrival rate × time in system.
100 checks/s × 0.5 s each ⇒ ~50 concurrent checks needed.

---

## 8. Other concurrency hazards worth naming

- **Non-thread-safe classes shared across threads:** `SimpleDateFormat`, `HashMap` (can corrupt under concurrent writes), `ArrayList`. Use `DateTimeFormatter` (immutable), `ConcurrentHashMap`.
- **Spring singletons are shared:** a `@Service` with a mutable field is shared by every request thread. Keep beans stateless.
- **Compound operations on concurrent collections:** `if (!map.containsKey(k)) map.put(k, v)` is still a race → `computeIfAbsent`/`putIfAbsent`.
- **Double-checked locking** without `volatile` is broken. Prefer the holder-class idiom or an `enum` singleton.
- **Timeouts everywhere:** a thread blocked forever on a socket without a timeout is a leaked thread. P4 sets connect + request timeouts on every check.

---

## 9. Testing concurrent code (P2 M5)

```java
@Test
void onlyOneHoldWinsUnderContention() throws Exception {
    int threads = 20;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Boolean>> results = new ArrayList<>();

    for (int i = 0; i < threads; i++) {
        long userId = i;
        results.add(pool.submit(() -> {
            start.await();                              // release all threads together
            try { holdService.hold(SEAT_ID, userId); return true; }
            catch (SeatUnavailableException | OptimisticLockingFailureException e) { return false; }
        }));
    }
    start.countDown();

    long winners = 0;
    for (Future<Boolean> f : results) if (f.get(10, TimeUnit.SECONDS)) winners++;
    pool.shutdown();
    assertThat(winners).isEqualTo(1);
}
```

A passing concurrency test is evidence, not proof — run it many times (`@RepeatedTest(50)`), and
rely on design (DB constraints/versions) for correctness.

---

## 10. Interview questions (quick)

<details><summary>What's a race condition? Give an example from your code.</summary>

Correctness depending on interleaving. In TicketHold, two customers checking "is seat free?" then
inserting a hold could both succeed. I fixed it at the database level with optimistic locking on the
seat's `@Version` column plus a unique constraint on active holds, and proved it with a 20-thread test.
</details>

<details><summary>Mutex vs semaphore?</summary>

Mutex: exclusive access, owned by the locking thread, which must unlock it. Semaphore: N permits,
no ownership; limits concurrency or signals between threads. A binary semaphore resembles a mutex
but anyone can release it.
</details>

<details><summary>Four conditions for deadlock, and how do you prevent it?</summary>

Mutual exclusion, hold-and-wait, no preemption, circular wait. Break one — usually circular wait via
a global lock order, or no-preemption via `tryLock` with timeout and retry.
</details>

<details><summary>Why use a bounded queue in a thread pool?</summary>

An unbounded queue hides overload: tasks accumulate until memory runs out and latency explodes.
Bounded queues force a decision (reject, caller-runs, drop) — backpressure.
</details>

<details><summary>What does <code>volatile</code> guarantee?</summary>

Visibility and ordering (happens-before) for reads/writes of that variable; not atomicity of
compound operations like `count++`.
</details>
