# Operating Systems (Week 14, with the Linux and Docker deep dive)

> **Practical use:** read `top`/`ps` output meaningfully, understand why your container was
> `OOMKilled` (exit 137), why `docker stop` takes 10 seconds, why you get "Too many open files",
> how to get a Java thread dump from a hung process, how many threads a ForgeCI worker should use, and
> how ForgeCI cancels a running build (signals to a container's PID 1).
> **Interview use:** process vs thread, context switch, virtual memory, system calls, "what happens when you run a program?"

Hands-on Linux commands live in [`10-linux/commands.md`](../10-linux/commands.md); this file is the *why*.

---

## 1. What an OS does

The kernel **multiplexes hardware** (CPU time, memory, devices) among programs and **isolates** them
from each other. User programs run in **user mode** (restricted); the kernel runs in **kernel mode**
(full hardware access). Programs ask the kernel for anything privileged through **system calls**.

---

## 2. Processes vs threads

| | Process | Thread |
|---|---|---|
| Definition | A running program: its own address space + resources | A unit of execution **inside** a process |
| Memory | Isolated virtual address space | **Shares** heap, code, open files with sibling threads; own stack + registers |
| Creation cost | Higher (new address space, page tables) | Lower |
| Communication | IPC: pipes, sockets, shared memory, files, signals | Shared memory directly (needs synchronization) |
| Crash impact | One process crashing doesn't kill others | An unhandled native crash kills the whole process |
| Java view | Each `java` command = one JVM process | `Thread`, `ExecutorService`, virtual threads |
| Linux view | `task_struct` with its own `mm` | Also a `task_struct` (created by `clone()` sharing `mm`) — `ps -eLf` lists them |

Process lifecycle on Linux: `fork()` (clone the parent) → `execve()` (replace the image with a new
program) → run → `exit()` → parent `wait()`s to collect the exit status.
- **Zombie:** exited but the parent hasn't `wait()`ed → entry lingers (`Z` in `ps`).
- **Orphan:** parent died first → adopted by PID 1 (init/systemd, or your container's PID 1!).

Process states: running, runnable (waiting for CPU), sleeping/blocked (waiting for I/O, lock, timer),
stopped, zombie. `top`'s **load average** ≈ average number of runnable (+ uninterruptible) tasks.

---

## 3. Context switches and scheduling

A **context switch** saves one thread's registers/program counter/stack pointer and restores
another's. Switching between processes also switches the address space (and may flush the TLB).
Cost: roughly a few microseconds direct, more indirectly through cold caches.

Triggers: time slice expires (**preemption**), thread blocks (I/O, lock, `sleep`), higher-priority
task becomes runnable, thread yields.

Linux uses a fair scheduler (CFS, succeeded by EEVDF in 6.6+): each runnable task gets a fair share
of CPU weighted by its **nice** value. You don't need the algorithm — you need the consequences:

| Workload | Characteristic | Thread-count heuristic |
|---|---|---|
| **CPU-bound** (hashing, JSON of big payloads, image resize) | Always runnable | ≈ number of cores (`Runtime.getRuntime().availableProcessors()`) |
| **I/O-bound** (HTTP calls, DB calls, waiting on containers) | Mostly blocked waiting | Many more threads: cores × (1 + wait time / compute time) — or **virtual threads** |

A ForgeCI worker's JVM is mostly I/O-bound (waits on the Docker daemon, `git clone`, container exit,
Redis) → virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) or a bounded pool — but the
real limit is the **host**: each build container consumes CPU/RAM, so cap concurrent jobs per worker
(a semaphore) regardless of how cheap threads are. More platform threads
than that just adds context switching and memory (≈1 MB stack reserve each).

**Virtual threads (Java 21):** the JVM schedules many virtual threads onto a few **carrier** platform
threads (M:N). When a virtual thread blocks on I/O, it's *unmounted* and the carrier runs another.
Cheap to create (KBs), perfect for blocking I/O code; no benefit for CPU-bound work. Pitfall:
blocking inside `synchronized` could **pin** the carrier in JDK 21 (fixed for most cases in JDK 24).

---

## 4. Virtual memory

Every process sees its own contiguous **virtual address space**. The MMU translates virtual → physical
addresses using per-process **page tables**; memory is managed in **pages** (typically 4 KB).

```
Process A virtual            Page table A          Physical RAM
0x0000 [code  ] ───────────► frame 17 ───────────► ┌─────────┐
0x1000 [heap  ] ───────────► frame 3  ───────────► │ frame 3 │
0x2000 [heap  ] ───────────► (on disk / not yet)   │   …     │
0x7fff [stack ] ───────────► frame 42              │ frame 17│  ◄── also mapped by B (shared lib)
                                                    └─────────┘
```

What it buys:
- **Isolation** — A can't address B's memory.
- **Overcommit / lazy allocation** — `-Xmx4g` reserves address space; physical pages are assigned on first touch.
- **Sharing** — shared libraries and the OS page cache map the same physical frames into many processes.
- **Swapping** — cold pages written to disk; accessing them is a **major page fault** (ms!). Heavy swapping = **thrashing**.
- **Memory-mapped files** (`mmap`, Java `MappedByteBuffer`) — file contents appear as memory.

The **TLB** caches recent translations; misses cost a page-table walk.

Why this matters in production:
- **RSS** (resident set size) = physical memory actually used; **VSZ** = virtual — often huge and meaningless.
- JVM memory = heap + metaspace + thread stacks + code cache + direct buffers + GC structures. A
  container with `-Xmx` equal to its limit **will** be killed. Use `-XX:MaxRAMPercentage=60–75`.
- When RAM runs out, Linux's **OOM killer** kills a process; in Docker with a memory limit, the
  cgroup kills the container → `docker inspect` shows `OOMKilled: true`, exit code **137** (128 + SIGKILL 9).
- `java.lang.OutOfMemoryError` is different: the **JVM's** heap limit was hit, the process is still alive.

---

## 5. System calls

The boundary between your program and the kernel. Java's standard library ends up in syscalls:

| Java | Syscall(s) (Linux) |
|---|---|
| `Files.readString(path)` | `openat`, `read`, `close` |
| `System.out.println` | `write(1, …)` |
| `new ServerSocket(8080)` / Tomcat start | `socket`, `bind`, `listen`, `accept` |
| JDBC query | `sendto`/`write`, `recvfrom`/`read` on a socket |
| NIO selector / Netty | `epoll_create`, `epoll_wait` |
| `new Thread().start()` | `clone` |
| `ProcessBuilder.start()` | `fork`/`vfork`/`posix_spawn` + `execve` |
| Heap growth | `mmap`, `munmap`, `madvise` |
| `Thread.sleep` | `futex` / `nanosleep` |

Syscalls cost a mode switch (~100 ns+). That's why buffered I/O (`BufferedReader`, `BufferedOutputStream`)
matters: 1 syscall per 8 KB instead of per byte.

See them: `strace -f -e trace=network java -jar app.jar` (Linux). Great for "what is this process
actually doing?" debugging.

---

## 6. File descriptors

A **file descriptor (fd)** is a small integer index into the process's table of open files. "Files"
include regular files, directories, **sockets**, pipes, and devices.

| fd | Standard stream |
|---|---|
| 0 | stdin |
| 1 | stdout |
| 2 | stderr |

- Shell redirection manipulates fds: `java -jar app.jar > out.log 2>&1` → fd 1 to file, fd 2 to where fd 1 points.
- Each process has a limit (`ulimit -n`, often 1024 soft). Every open DB connection, HTTP client
  connection, and file consumes one.
- **"Too many open files"** (`EMFILE`) almost always means a **leak**: streams/connections not closed.
  Java fix: **try-with-resources**. Diagnose: `ls /proc/<pid>/fd | wc -l`, `lsof -p <pid>`.
- Docker logging: the app writes to fd 1/2; the Docker daemon captures them → that's why "log to stdout" is the container convention.

---

## 7. Signals

Asynchronous notifications to a process.

| Signal | Number | Default | You'll see it when |
|---|---:|---|---|
| `SIGINT` | 2 | Terminate | Ctrl+C |
| `SIGTERM` | 15 | Terminate | `kill <pid>`, `docker stop`, systemd stop, Kubernetes pod deletion |
| `SIGKILL` | 9 | Kill (cannot be caught) | `kill -9`, OOM killer, `docker stop` after timeout |
| `SIGHUP` | 1 | Terminate | Terminal closed; many daemons reload config on it |
| `SIGQUIT` | 3 | Core dump — **JVM prints a thread dump instead** | `kill -3 <pid>` |
| `SIGSEGV` | 11 | Core dump | Native crash (`hs_err_pid*.log` for the JVM) |

Java and signals:
- On `SIGTERM`/`SIGINT` the JVM runs **shutdown hooks**. Spring Boot registers one and, with
  `server.shutdown=graceful`, stops accepting requests and finishes in-flight ones.
- `SIGKILL` skips everything — no hooks, no flush. Exit code 137.
- **`docker stop`** sends SIGTERM to **PID 1** in the container, waits 10 s, then SIGKILL. If your
  Dockerfile uses shell form `ENTRYPOINT java -jar app.jar`, PID 1 is `/bin/sh`, which doesn't forward
  SIGTERM → your app is SIGKILLed after 10 s every time. Use **exec form**:
  `ENTRYPOINT ["java", "-jar", "/app/app.jar"]`. See [`11-docker/dockerfiles.md`](../11-docker/dockerfiles.md).
- Exit codes: 0 success; 1 generic error; 130 = 128 + SIGINT; 137 = 128 + SIGKILL; 143 = 128 + SIGTERM.

ForgeCI tie-in (M4 cancellation/timeouts): `docker stop -t 10 <container>` = SIGTERM to the container's
PID 1, then SIGKILL after 10 s; `docker kill` = SIGKILL immediately. A build step started via
`sh -c "…"` may not forward SIGTERM to its children — know which process is PID 1 in your build container.

Thread dump of a hung app (find deadlocks, stuck pool threads): `jcmd <pid> Thread.print` or `kill -3 <pid>`.

---

## 8. What happens when you run `java -jar app.jar`

1. **Shell** parses the line, finds `java` via `$PATH`, and calls `fork()` then `execve("/usr/bin/java", ["java","-jar","app.jar"], env)` in the child. The shell `wait()`s.
2. **Kernel** loads the `java` launcher ELF binary, maps it into a fresh address space, and the dynamic linker loads shared libs (`libc`, then the launcher loads `libjvm.so`).
3. **JVM initializes**: parses flags (`-Xmx`, `JAVA_TOOL_OPTIONS`), reserves the heap with `mmap`, sets up the GC, starts internal threads (GC workers, JIT compiler threads, signal dispatcher, finalizer/reference handler).
4. **Class loading**: bootstrap loader loads `java.base`; the launcher opens the JAR (a ZIP), reads `META-INF/MANIFEST.MF` → `Main-Class` (for Spring Boot fat jars, that's Spring's `JarLauncher`, which sets up a class loader over nested jars and then calls your `@SpringBootApplication` class).
5. **Linking + initialization**: bytecode verification, static initializers run.
6. **`main` runs** on the main thread — interpreted at first; hot methods get **JIT-compiled** to native code (why Java "warms up").
7. **Spring Boot** builds the application context (component scan, bean creation, DI), runs Flyway, creates the Hikari pool (opens sockets → fds), starts embedded Tomcat: `socket()`, `bind(8080)`, `listen()`, then worker threads `accept()` connections.
8. **Runs** until a signal arrives: SIGTERM → shutdown hooks → context closes → pool closes → exit code → the parent (shell / Docker / systemd) collects it.

Great interview answer structure: *shell → kernel exec → JVM bootstrap → class loading → main → your framework → steady state → shutdown via signals.*

---

## 9. Inter-process communication (awareness)

Pipes (`ps aux | grep java`), sockets (TCP between your API and Postgres, Unix domain sockets like
`/var/run/docker.sock`), shared memory, files, signals, message brokers at the system level.
Microservices are just processes doing IPC over TCP.

---

## 10. Break → Debug drills

- [ ] Run a Spring Boot app in Docker with `mem_limit: 256m` and `-Xmx512m`; load it; read `OOMKilled` + exit 137.
- [ ] Build an image with shell-form `ENTRYPOINT`; time `docker stop` (≈10 s). Switch to exec form (≈1 s).
- [ ] Create a deliberate deadlock (two threads, two locks, opposite order); `jcmd <pid> Thread.print` → find "Found one Java-level deadlock".
- [ ] Open files in a loop without closing; hit "Too many open files"; count `/proc/<pid>/fd`.
- [ ] `strace -f -e trace=network curl -s https://example.com > /dev/null` — spot `connect()` and the DNS lookup.

---

## 11. Interview questions (quick)

<details><summary>Process vs thread?</summary>

A process is an isolated address space with its own resources; threads live inside a process and
share its heap and open files, each with its own stack and registers. Threads are cheaper to create
and communicate through shared memory, which is why they need synchronization.
</details>

<details><summary>What is a context switch and why is it expensive?</summary>

Saving one thread's CPU state and loading another's (plus switching page tables between processes).
Direct cost is microseconds, but it also evicts cache/TLB contents. Too many runnable threads →
the CPU spends time switching instead of working.
</details>

<details><summary>What is virtual memory?</summary>

Each process gets its own address space mapped to physical frames via page tables, in pages. It
provides isolation, lazy allocation, sharing and swapping. A page fault occurs when a mapped page
isn't in RAM.
</details>

<details><summary>What happens when a Docker container exceeds its memory limit?</summary>

The kernel's cgroup OOM killer SIGKILLs the process: exit 137, `OOMKilled: true`. That's different
from a Java `OutOfMemoryError`, which is the JVM's heap limit. Fix by sizing heap as a percentage of
the container limit and leaving room for non-heap memory.
</details>

<details><summary>How would you investigate a Java process using 100% CPU?</summary>

`top -H -p <pid>` to find the hot thread ID, convert it to hex, take a thread dump with `jcmd <pid>
Thread.print`, find the thread with that `nid`, read its stack. Repeat a few times to confirm. Could
be a busy loop, GC thrashing (check GC logs), or regex backtracking.
</details>
