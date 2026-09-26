# 🧠 14 — CS Fundamentals (practical, interview-driven)

> **Not a university course.** Every topic here earns its place by (a) explaining something you see
> when building and debugging the four projects, and (b) appearing in internship / junior interviews.
> If a concept doesn't pass both tests, it's not here.

Time budget: part of the ≈22 h "CS fundamentals + system design" share in [ROADMAP §3](../ROADMAP.md#3-priority-weighting).
You read each file in the week it becomes relevant, then drill [`interview-questions.md`](./interview-questions.md) weekly.

---

## Files and when to read them

| File | Week | Why then | Project tie-in |
|---|---|---|---|
| [`memory-and-architecture.md`](./memory-and-architecture.md) | **6** | Same week as Java memory (stack/heap/GC) | P1: `BigDecimal` vs `double`, int overflow in amounts |
| [`software-engineering-practices.md`](./software-engineering-practices.md) | **5** (patterns), revisit 12, 18 | SOLID/patterns week; code review from W5 PR workflow | P1 Strategy rules, Factory for CSV formats, Builder |
| [`database-internals.md`](./database-internals.md) | **7–8** | Indexes, transactions, `EXPLAIN` | P1 M5 index, P2 optimistic locking, P4 composite index |
| [`networking.md`](./networking.md) | **9** | Before the first Spring controller | P2 API, CORS in P3, TLS/DNS in P4 AWS |
| [`operating-systems.md`](./operating-systems.md) | **13** (+ 19 with Linux) | With Java concurrency; again with Linux/Docker | Threads for P4 checker, signals for `docker stop` |
| [`concurrency.md`](./concurrency.md) | **13** | Theory beside [`01-java/07-concurrency.md`](../01-java/07-concurrency.md) | P2 seat-hold race test, P4 worker pool |
| [`interview-questions.md`](./interview-questions.md) | From **6**, weekly | Spaced review, 90 Q&A | All |

---

## The "explain it" standard

For each topic you should be able to give a **5-sentence answer**: *what* it is, *why* it exists,
*how* it works (one level deeper than the definition), the *trade-off*, and an *example from your
own code*. Example for "database index":

> An index is a separate sorted data structure (a B-tree in Postgres) mapping column values to row
> locations. It exists so lookups don't scan the whole table. The tree is shallow — 3–4 levels for
> millions of rows — so a lookup is a handful of page reads. The cost is slower writes and extra
> storage, since every insert updates every index. In PulseWatch, the status page query went from a
> sequential scan to an index scan after I added a composite index on `(monitor_id, checked_at)`.

---

## Topic map

```
                 ┌───────────────────────────────┐
                 │  Hardware: CPU, caches, RAM,   │  memory-and-architecture.md
                 │  disk, numbers in binary       │
                 └──────────────┬────────────────┘
                                │
                 ┌──────────────▼────────────────┐
                 │  OS: processes, threads,       │  operating-systems.md
                 │  virtual memory, syscalls, fds │
                 └───────┬───────────────┬───────┘
                         │               │
        ┌────────────────▼───┐   ┌───────▼──────────────────┐
        │ Concurrency:       │   │ Networking: TCP/IP, DNS, │  networking.md
        │ races, locks,      │   │ HTTP, TLS, LBs           │
        │ deadlock, pools    │   └───────┬──────────────────┘
        └────────┬───────────┘           │
                 │    ┌──────────────────▼──────────┐
                 └───►│ Databases: B-trees, WAL,     │  database-internals.md
                      │ MVCC, planner, replication   │
                      └──────────────┬──────────────┘
                                     │
                      ┌──────────────▼──────────────┐
                      │ Engineering practice: SOLID, │  software-engineering-practices.md
                      │ patterns, reviews, errors    │
                      └──────────────┬──────────────┘
                                     ▼
                         15-system-design/  (puts it all together)
```

---

## Checklist

- [ ] Can recite approximate latency numbers (L1, RAM, SSD, same-DC round trip, cross-continent) and use them to reason
- [ ] Can convert between decimal, binary, hex; explain two's complement and why `Integer.MAX_VALUE + 1` is negative
- [ ] Can explain stack vs heap for a specific Java method call with objects
- [ ] Can explain process vs thread, context switch, and what happens on `java -jar app.jar`
- [ ] Can name the four Coffman deadlock conditions and how lock ordering breaks one
- [ ] Can walk through "what happens when you type a URL" for 3+ minutes without notes
- [ ] Can explain why a B-tree index helps `WHERE monitor_id = ? ORDER BY checked_at DESC LIMIT 50`
- [ ] Can explain MVCC and why readers don't block writers in Postgres
- [ ] Can name SOLID and give a real example of each from P1–P4
- [ ] Can implement Strategy, Builder, Factory, Observer from memory in Java
- [ ] Score ≥ 80% on a random 20 from [`interview-questions.md`](./interview-questions.md)

---

## Resources

- *Computer Systems: A Programmer's Perspective* (Bryant & O'Hallaron) — chapters on data representation, memory hierarchy, virtual memory (reference, not cover-to-cover)
- *Operating Systems: Three Easy Pieces* (Arpaci-Dusseau) — free online; concurrency and virtual memory chapters
- *Java Concurrency in Practice* (Goetz) — chapters 1–5
- *Computer Networking: A Top-Down Approach* (Kurose & Ross) — reference; or MDN "How the web works" and HTTP guides (developer.mozilla.org)
- *Designing Data-Intensive Applications* (Kleppmann) — chapters 2, 3, 5, 7 (storage, replication, transactions)
- PostgreSQL docs: "Concurrency Control" (MVCC), "Indexes", "Using EXPLAIN", "Write-Ahead Logging"
- *Effective Java* (Bloch), *Head First Design Patterns*, *Clean Code* (read critically), *Refactoring* (Fowler)
