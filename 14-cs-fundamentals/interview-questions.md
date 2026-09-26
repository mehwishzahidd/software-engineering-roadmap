# CS Fundamentals — Interview Questions (90)

> Drill weekly from Week 6. Answer **out loud** before opening the answer. Mark each question in your
> notes: ✅ confident · 🟡 shaky · ❌ couldn't. Re-drill 🟡/❌ on the Day 3/7/14/30 cycle used for DSA.
> Answers are deliberately short — the target is a clear 30–60 second spoken answer, plus an example
> from your projects where marked 🛠.

| Section | Questions | Source file |
|---|---|---|
| [A. Architecture & memory](#a-architecture--memory) | 1–12 | [memory-and-architecture.md](./memory-and-architecture.md) |
| [B. Operating systems](#b-operating-systems) | 13–25 | [operating-systems.md](./operating-systems.md) |
| [C. Concurrency](#c-concurrency) | 26–40 | [concurrency.md](./concurrency.md) |
| [D. Networking](#d-networking) | 41–56 | [networking.md](./networking.md) |
| [E. Databases](#e-databases) | 57–71 | [database-internals.md](./database-internals.md) |
| [F. Engineering practices](#f-engineering-practices) | 72–84 | [software-engineering-practices.md](./software-engineering-practices.md) |
| [G. Security & misc](#g-security--misc) | 85–90 | mixed |

---

## A. Architecture & memory

<details><summary>1. What is the memory hierarchy and why does it exist?</summary>

Registers → L1 → L2 → L3 → RAM → SSD → HDD/network: each level is larger, cheaper per byte and
slower. Fast memory is expensive and small, so caches keep the working set close to the CPU,
exploiting locality.
</details>

<details><summary>2. Temporal vs spatial locality?</summary>

Temporal: recently used data will be used again soon. Spatial: data near recently used data will be
used soon. Caches load 64-byte lines (spatial) and keep recently used lines (temporal).
</details>

<details><summary>3. Give approximate latencies for L1, RAM, SSD, and a cross-continent round trip.</summary>

~1 ns, ~100 ns, ~20–100 µs, ~100–150 ms. Each step is orders of magnitude.
</details>

<details><summary>4. Why is iterating a 2-D array row-by-row faster than column-by-column in Java?</summary>

Each `int[]` row is contiguous; row-wise iteration uses every byte of each cache line. Column-wise
jumps between different row arrays, causing a cache miss almost every access.
</details>

<details><summary>5. Convert 202 to binary and hex.</summary>

202 = 128+64+8+2 = `1100 1010` = `0xCA`.
</details>

<details><summary>6. What is two's complement? What is −1 in 8 bits?</summary>

Signed representation where the MSB weighs −2ⁿ⁻¹; negate by inverting bits and adding 1. −1 is
`1111 1111`. Makes addition identical for signed/unsigned and has a single zero.
</details>

<details><summary>7. What does <code>Integer.MAX_VALUE + 1</code> evaluate to and how do you guard against it?</summary>

`Integer.MIN_VALUE` (−2,147,483,648) — silent wraparound. Use `long`, `Math.addExact` (throws), or
restructure (e.g. `lo + (hi - lo) / 2`).
</details>

<details><summary>8. Why can <code>Math.abs(x)</code> return a negative number?</summary>

For `Integer.MIN_VALUE`, the positive counterpart isn't representable, so it returns MIN_VALUE.
Relevant when computing `Math.abs(hashCode()) % n` — use `Math.floorMod`.
</details>

<details><summary>9. Why shouldn't you store money in a double? 🛠</summary>

Binary floating point can't represent most decimal fractions exactly (0.1 + 0.2 ≠ 0.3), so sums
drift. P1 wraps `BigDecimal` (created from strings, explicit rounding) in a `Money` type; DB column is `NUMERIC(12,2)`.
</details>

<details><summary>10. Stack vs heap in Java?</summary>

Each thread has a stack of frames holding primitives and references for local variables; objects
and arrays live in the shared heap, freed by GC. Stack overflow → `StackOverflowError`; heap
exhaustion → `OutOfMemoryError`.
</details>

<details><summary>11. Roughly how much memory does an <code>Integer</code> take versus an <code>int</code>?</summary>

`int` 4 bytes; `Integer` ~16 bytes (12-byte header + 4, aligned) plus a 4–8 byte reference to it. A
`List<Integer>` costs ~5× an `int[]`, and loses locality.
</details>

<details><summary>12. What is <code>x & (x - 1)</code> used for?</summary>

Clears the lowest set bit. `x > 0 && (x & (x-1)) == 0` tests for a power of two; looping it counts
set bits (Kernighan).
</details>

---

## B. Operating systems

<details><summary>13. Process vs thread?</summary>

Process: isolated address space and resources. Thread: execution unit within a process sharing heap
and files, with its own stack/registers. Threads are cheaper and communicate via shared memory
(needs synchronization); processes are isolated (crash containment).
</details>

<details><summary>14. What is a context switch?</summary>

Saving a running thread's CPU state and restoring another's; between processes also switching
address spaces. Costs microseconds plus cache/TLB pollution.
</details>

<details><summary>15. What's preemptive scheduling?</summary>

The OS can interrupt a running thread (timer interrupt at time-slice end) to run another, so no
thread can monopolize the CPU. Contrast cooperative scheduling where tasks must yield.
</details>

<details><summary>16. CPU-bound vs I/O-bound — how does it change thread-pool size?</summary>

CPU-bound: ≈ number of cores; more only adds switching. I/O-bound: threads mostly wait, so use more
— cores × (1 + wait/compute) — or virtual threads.
</details>

<details><summary>17. What are virtual threads? 🛠</summary>

Java 21 lightweight threads scheduled by the JVM onto a few carrier threads; blocking I/O unmounts
them. Cheap enough for one per task. P4's checker uses them for thousands of concurrent HTTP checks.
No gain for CPU-bound work.
</details>

<details><summary>18. What is virtual memory and a page fault?</summary>

Each process has its own virtual address space mapped to physical frames via page tables in 4 KB
pages. A page fault occurs when an accessed page isn't mapped in RAM — minor (just needs mapping) or
major (read from disk/swap).
</details>

<details><summary>19. What is thrashing?</summary>

The working set exceeds RAM, so the system spends most time swapping pages in and out; throughput
collapses. Fix: more memory, smaller working set, fewer processes.
</details>

<details><summary>20. What is a system call? Give examples.</summary>

A controlled entry from user mode into the kernel for privileged work: `read`, `write`, `openat`,
`socket`, `connect`, `accept`, `mmap`, `clone`, `execve`. Costly relative to function calls → buffer I/O.
</details>

<details><summary>21. What is a file descriptor? What causes "Too many open files"?</summary>

A per-process integer handle to an open file/socket/pipe (0,1,2 = stdin/out/err). The error means the
fd limit was hit — usually leaked streams/connections; fix with try-with-resources, check `lsof`.
</details>

<details><summary>22. SIGTERM vs SIGKILL? 🛠</summary>

SIGTERM asks to terminate — catchable, JVM runs shutdown hooks (Spring graceful shutdown). SIGKILL
can't be caught — immediate death, exit 137. `docker stop` sends SIGTERM, then SIGKILL after 10 s.
Exec-form ENTRYPOINT ensures the JVM receives SIGTERM.
</details>

<details><summary>23. What does exit code 137 mean in Docker?</summary>

128 + 9: killed by SIGKILL — typically the OOM killer (container exceeded memory limit) or a stop
timeout. Check `docker inspect` → `OOMKilled`.
</details>

<details><summary>24. What happens when you run <code>java -jar app.jar</code>?</summary>

Shell forks and execs the java launcher; kernel loads it; JVM initializes heap (mmap), GC, JIT and
internal threads; reads the manifest's Main-Class; loads/links/initializes classes; runs `main`
(interpreted, then JIT-compiled hot code); Spring builds its context and opens the server socket;
on SIGTERM, shutdown hooks run and the exit code returns to the parent.
</details>

<details><summary>25. How do you get a thread dump and what's it for?</summary>

`jcmd <pid> Thread.print` or `kill -3 <pid>` (or `jstack`). Shows every thread's state and stack —
find deadlocks, stuck pool threads, hot loops (combine with `top -H`).
</details>

---

## C. Concurrency

<details><summary>26. Concurrency vs parallelism?</summary>

Concurrency: multiple tasks making progress in overlapping periods (can be one core, interleaved).
Parallelism: literally simultaneous execution on multiple cores.
</details>

<details><summary>27. What is a race condition? 🛠</summary>

Correctness depends on thread timing. E.g. check-then-act "seat free? then hold" in TicketHold — two
requests both see free. Fixed with DB-level optimistic locking and a uniqueness constraint.
</details>

<details><summary>28. Why is <code>count++</code> not thread-safe?</summary>

It's read, add, write — three steps. Interleaving loses updates. Use `AtomicInteger.incrementAndGet`,
`LongAdder`, or a lock.
</details>

<details><summary>29. What is a critical section?</summary>

Code accessing shared mutable state that must be executed by at most one thread at a time.
</details>

<details><summary>30. Mutex vs semaphore?</summary>

Mutex: exclusive ownership; the locker unlocks. Semaphore: N permits, no ownership; limits
concurrency (e.g. max 5 concurrent calls per host) or signals.
</details>

<details><summary>31. What is a monitor in Java?</summary>

Every object has an intrinsic lock plus a wait set: `synchronized` gives mutual exclusion,
`wait/notify/notifyAll` give condition waiting. Together that's a monitor.
</details>

<details><summary>32. Why must <code>wait()</code> be called in a while loop?</summary>

Spurious wakeups exist, and another thread may have changed the condition between notify and
reacquiring the lock. Re-check the predicate every time.
</details>

<details><summary>33. What does <code>volatile</code> do?</summary>

Guarantees visibility and ordering (happens-before) for that variable's reads/writes; not atomicity
of compound operations.
</details>

<details><summary>34. What are the four conditions for deadlock?</summary>

Mutual exclusion, hold and wait, no preemption, circular wait. All four must hold.
</details>

<details><summary>35. How do you prevent deadlocks in practice?</summary>

Global lock ordering (break circular wait), `tryLock` with timeout (break no-preemption), hold fewer
locks for less time, avoid calling unknown code while holding locks. In the DB: update rows in a
consistent order and retry on `deadlock detected`.
</details>

<details><summary>36. Livelock vs starvation?</summary>

Livelock: threads actively respond to each other but make no progress (fix: randomized backoff).
Starvation: a thread never gets resources because others always win (fix: fairness, bounded waits).
</details>

<details><summary>37. Explain producer-consumer. 🛠</summary>

Producers enqueue work in a bounded buffer; consumers dequeue. Decouples rates and provides
backpressure. In Java: `BlockingQueue.put/take`. In P4 the scheduler produces due checks and the
checker workers consume them.
</details>

<details><summary>38. Why prefer thread pools over <code>new Thread()</code> per task?</summary>

Reuse avoids creation cost, caps concurrency (protects CPU/memory/downstreams), and gives a queue
and lifecycle management. Use a bounded queue and a deliberate rejection policy.
</details>

<details><summary>39. What's the danger of <code>Executors.newFixedThreadPool</code>?</summary>

Its queue is unbounded: under sustained overload tasks pile up until memory runs out, and latency
grows without any error signal.
</details>

<details><summary>40. What is compare-and-swap (CAS)?</summary>

An atomic CPU instruction: set a value only if it still equals the expected value. Basis of
`Atomic*` classes and lock-free structures; retried in a loop on contention. Optimistic locking in
databases is the same idea (`WHERE version = ?`).
</details>

---

## D. Networking

<details><summary>41. Name the TCP/IP layers and a protocol at each.</summary>

Link (Ethernet, Wi-Fi), Internet (IP, ICMP), Transport (TCP, UDP), Application (HTTP, DNS, TLS, SSH).
</details>

<details><summary>42. Describe the TCP three-way handshake.</summary>

SYN (client seq x) → SYN-ACK (server seq y, ack x+1) → ACK (ack y+1). Both sides agree on initial
sequence numbers; costs one round trip before data.
</details>

<details><summary>43. How does TCP provide reliability?</summary>

Sequence numbers, acknowledgments, retransmission on timeout/duplicate ACKs, checksums, flow control
(receiver window) and congestion control (slow start, backoff on loss).
</details>

<details><summary>44. TCP vs UDP — when would you use UDP?</summary>

UDP for low-latency or loss-tolerant traffic (voice/video, games), tiny request/response (DNS), or
when building your own reliability (QUIC). TCP for anything needing reliable ordered delivery.
</details>

<details><summary>45. Walk through a DNS lookup.</summary>

Browser/OS cache → recursive resolver → root (points to TLD) → TLD (points to authoritative) →
authoritative returns the record with a TTL → cached along the way.
</details>

<details><summary>46. What is a TTL in DNS and why does it matter during a migration?</summary>

How long resolvers may cache the answer. Lower it in advance so a changed IP propagates quickly.
</details>

<details><summary>47. HTTP/1.1 vs HTTP/2 vs HTTP/3?</summary>

1.1: text, one outstanding request per connection. 2: binary, multiplexed streams and header
compression over one TCP connection (still TCP head-of-line blocking). 3: over QUIC/UDP, independent
streams, faster handshakes.
</details>

<details><summary>48. What does TLS provide and how does the handshake work?</summary>

Confidentiality, integrity, authentication. Client/server exchange key shares (ECDHE) and the server
presents a CA-signed certificate; the client verifies chain and hostname; both derive symmetric keys;
data is encrypted with fast symmetric ciphers. TLS 1.3: 1 RTT.
</details>

<details><summary>49. Symmetric vs asymmetric encryption?</summary>

Symmetric: same key both ways, fast (AES). Asymmetric: public/private key pair, slow, solves key
distribution and signatures (RSA, ECDSA, ECDHE). TLS uses asymmetric to agree on a symmetric key.
</details>

<details><summary>50. What happens when you type a URL and press Enter?</summary>

Parse URL/HSTS → cache → DNS → TCP (or QUIC) handshake → TLS handshake → HTTP request → LB/reverse
proxy → app server (filters, controller, service, cache, DB) → response → browser parses HTML,
fetches subresources, runs JS, renders; connection kept alive.
</details>

<details><summary>51. L4 vs L7 load balancer?</summary>

L4 routes TCP/UDP connections by IP/port, unaware of content. L7 parses HTTP and routes by host/path/
headers, terminates TLS, supports sticky sessions and retries.
</details>

<details><summary>52. What is NAT?</summary>

Translating private addresses to a public one at a boundary so many hosts share an IP; allows
outbound connections, blocks unsolicited inbound. Home routers, AWS NAT gateways, Docker bridge networking.
</details>

<details><summary>53. "Connection refused" vs "connection timed out"?</summary>

Refused: host answered with RST — nothing listening on the port. Timed out: no answer — firewall/SG
dropping packets or wrong address/route.
</details>

<details><summary>54. Why does <code>localhost</code> inside a Docker container not reach the host's Postgres?</summary>

Each container has its own network namespace; localhost is the container itself. Use the Compose
service name (`postgres:5432`) on the same network, or `host.docker.internal` for the host.
</details>

<details><summary>55. What is CORS and who enforces it? 🛠</summary>

A browser mechanism relaxing the same-origin policy when the server sends `Access-Control-Allow-*`
headers (with preflight OPTIONS for non-simple requests). Enforced by browsers only. In TeamBoard I
configured allowed origins in Spring Security for dev and served UI + API from one origin via nginx in prod.
</details>

<details><summary>56. What is a reverse proxy and why put nginx in front of Spring Boot?</summary>

A server that receives client requests and forwards them to backends. nginx serves static React
files, terminates TLS, routes `/api` to Spring, adds headers, buffers slow clients, and can load-balance.
</details>

---

## E. Databases

<details><summary>57. What is an index and what does it cost?</summary>

A separate structure (usually B+tree) mapping key values to row locations for fast lookup, range and
ordering. Costs storage and slower writes (every insert/update maintains it).
</details>

<details><summary>58. Why are B-trees used instead of binary search trees on disk?</summary>

High fan-out (hundreds of keys per page) keeps the tree 3–4 levels deep for huge tables, so lookups
touch few pages. Binary trees would be ~30 levels = ~30 random I/Os.
</details>

<details><summary>59. Explain the leftmost-prefix rule. 🛠</summary>

A composite index on (a, b) is sorted by a then b; it helps queries filtering on a, or a and b, but
not b alone. P4's `(monitor_id, checked_at)` index serves "latest 50 results for monitor X" without a sort.
</details>

<details><summary>60. Why might the database ignore your index?</summary>

Low selectivity (seq scan cheaper), functions/casts on the column, leading-wildcard LIKE, not a
leftmost prefix, tiny table, stale statistics.
</details>

<details><summary>61. Clustered vs non-clustered index?</summary>

Clustered: table rows physically stored in index order (InnoDB primary key). Non-clustered: separate
structure pointing to rows (all Postgres indexes; the table is a heap).
</details>

<details><summary>62. What is the WAL?</summary>

Write-ahead log: changes are appended and fsynced to a sequential log before data pages are written.
Provides durability, crash recovery, replication and point-in-time recovery.
</details>

<details><summary>63. What is MVCC?</summary>

Multi-version concurrency control: writes create new row versions; transactions read a consistent
snapshot; readers and writers don't block each other; VACUUM removes dead versions.
</details>

<details><summary>64. Name the isolation levels and anomalies.</summary>

Read uncommitted (dirty reads), read committed (non-repeatable reads), repeatable read (phantoms, per
standard), serializable (none; also prevents write skew). Postgres default: read committed.
</details>

<details><summary>65. What's a lost update and how do you prevent it? 🛠</summary>

Two transactions read the same value and both write, one overwriting the other. Prevent with
optimistic locking (`@Version`), `SELECT … FOR UPDATE`, atomic `UPDATE … SET x = x + 1`, or
stricter isolation. TicketHold uses `@Version` on seats.
</details>

<details><summary>66. Optimistic vs pessimistic locking?</summary>

Optimistic: detect conflicts at write time with a version check; best for low contention. Pessimistic:
lock rows up front; best for high contention, risks waits/deadlocks.
</details>

<details><summary>67. How do you investigate a slow query? 🛠</summary>

Reproduce with real parameters, `EXPLAIN (ANALYZE, BUFFERS)`, look for seq scans on big tables,
sorts, estimate vs actual row mismatch; add/adjust index or rewrite; verify with a new plan and
timing. I did this for P4's status query.
</details>

<details><summary>68. What does the query planner use to choose a plan?</summary>

Table statistics (row counts, value distributions), available indexes, and a cost model of I/O and
CPU. It picks the cheapest estimated plan — so stale stats cause bad plans (`ANALYZE`).
</details>

<details><summary>69. Replication: synchronous vs asynchronous?</summary>

Synchronous: commit waits for replica ack — no data loss on failover, higher latency. Asynchronous:
commit returns immediately — replicas lag; failover may lose recent commits.
</details>

<details><summary>70. What is replication lag and how does it bite users?</summary>

Delay before a replica reflects primary writes. A user creates something and immediately reads it
from a replica that doesn't have it yet. Mitigate with read-your-writes routing to the primary.
</details>

<details><summary>71. Why is having 500 connections to Postgres a problem?</summary>

Each is a backend process with its own memory; contention grows. Keep pools small, use a pooler
(PgBouncer/RDS Proxy); throughput usually peaks at a modest multiple of cores.
</details>

---

## F. Engineering practices

<details><summary>72. What does SRP mean? 🛠</summary>

A class has one reason to change. P1 separates parsing, categorization and import orchestration.
</details>

<details><summary>73. Open/closed principle with an example? 🛠</summary>

Extend behaviour without modifying existing code: new `CategorizationRule` implementations are
added without touching the `Categorizer`.
</details>

<details><summary>74. What is Liskov substitution? Give a violation.</summary>

Subtypes must honour the base type's contract. Violation: a repository subclass that throws on
`save`, or `Square extends Rectangle` breaking independent width/height.
</details>

<details><summary>75. Dependency inversion vs dependency injection?</summary>

DIP is the principle: depend on abstractions. DI is a technique for supplying dependencies from
outside (constructor injection, Spring container) — it makes DIP convenient.
</details>

<details><summary>76. When would you use the Strategy pattern? 🛠</summary>

Multiple interchangeable algorithms selected by data/config — P1 categorization rules, P4 check
types. Replaces growing `switch` statements.
</details>

<details><summary>77. Builder vs constructor vs record?</summary>

Record/constructor for few required fields. Builder for many or optional parameters, validation at
build time, readable test data.
</details>

<details><summary>78. Why are hand-written singletons discouraged?</summary>

Global mutable state, hidden dependencies, hard to test/mock. Let the DI container manage single
instances; if needed, an enum singleton is simplest and thread-safe.
</details>

<details><summary>79. Observer — where have you used it? 🛠</summary>

P1 over-budget alerts notify listeners; P4 publishes an `IncidentOpened` event handled after commit
by notification listeners. At scale, pub/sub via a broker.
</details>

<details><summary>80. Decorator vs Adapter?</summary>

Decorator keeps the same interface and adds behaviour (retry, caching); Adapter converts an
incompatible interface into the one you need.
</details>

<details><summary>81. What goes into a good pull request?</summary>

Small, single purpose, clear title and description (what/why/how to test), tests, green CI, self-reviewed diff.
</details>

<details><summary>82. What should never be logged?</summary>

Passwords, tokens, API keys, secrets, full payment data, presigned URLs; minimize PII. Log IDs instead.
</details>

<details><summary>83. How do you decide what to retry?</summary>

Only transient failures (timeouts, 503, connection resets) on idempotent operations (or with
idempotency keys), with exponential backoff, jitter and a max attempts cap. Never retry 4xx validation errors.
</details>

<details><summary>84. What is technical debt? Give an example you managed.</summary>

The future cost of an expedient choice. Example: in-memory storage in P1 until M4, made safe by
coding against a repository interface, then replaced with JDBC without touching services.
</details>

---

## G. Security & misc

<details><summary>85. How do you prevent SQL injection?</summary>

Parameterized queries (`PreparedStatement`, JPA parameters) — never concatenate user input into SQL.
Plus least-privilege DB users and input validation.
</details>

<details><summary>86. Hashing vs encryption? How should passwords be stored?</summary>

Hashing is one-way; encryption is reversible with a key. Store passwords with a slow, salted
password hash — BCrypt/Argon2 — never encrypted or with fast hashes like SHA-256 alone.
</details>

<details><summary>87. Authentication vs authorization?</summary>

Authentication: who are you (login, JWT validation) → 401 if missing/invalid. Authorization: what
may you do (roles, ownership checks) → 403 if denied.
</details>

<details><summary>88. What is idempotency and why does it matter in networks? 🛠</summary>

Repeating an operation has the same effect as doing it once. Networks cause retries, so non-
idempotent operations (POST booking) need an Idempotency-Key — TicketHold stores the key with the
result and replays it on retry.
</details>

<details><summary>89. What is a hash function's role in a HashMap, and what happens on collisions?</summary>

`hashCode` (spread) picks a bucket index; collisions chain entries in the bucket (a linked list, a
tree once a bucket exceeds 8 entries in a large enough table). Equal objects must have equal hash codes.
</details>

<details><summary>90. Big-O of common operations: HashMap get, TreeMap get, ArrayList add, binary search, sorting?</summary>

HashMap get O(1) average; TreeMap O(log n); ArrayList add amortized O(1); binary search O(log n);
comparison sorting O(n log n).
</details>
