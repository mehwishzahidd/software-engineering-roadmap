# 🧠 14 — CS Fundamentals (practical, interview-driven)

> **Not a university course.** Every topic here earns its place by (a) explaining something you see
> when building and debugging the four projects, and (b) appearing in internship / junior interviews.
> If a concept doesn't pass both tests, it's not here.

Time budget: comes out of the 6–8 h/week "technology / CS learning" block in [ROADMAP §3](../ROADMAP.md#3-time-allocation),
scheduled just-in-time by [ROADMAP §6](../ROADMAP.md#6-just-in-time-learning-map). Read each file the week a project needs it,
then drill [`interview-questions.md`](./interview-questions.md) weekly; Weeks 20+ are the interview sweep.

---

## Files and when to read them

| File | Week | Why then | Project tie-in |
|---|---|---|---|
| [`software-engineering-practices.md`](./software-engineering-practices.md) | **4–5** (SOLID, patterns, errors, logging), code review from W5 | FlowGrid's design + PR workflow start | Allocation strategies, ProblemDetail errors, structured logs; reused in every project |
| [`memory-and-architecture.md`](./memory-and-architecture.md) | **5** | Same week as Java memory model + thread basics | Stack/heap for the first concurrency test; `BigDecimal` for LedgerX money |
| [`database-internals.md`](./database-internals.md) | **9** (locking touched in W5, indexes in W6) | LedgerX is about correctness inside the DB | FlowGrid reservations, LedgerX append-only ledger, isolation experiments |
| [`operating-systems.md`](./operating-systems.md) | **14** | With the Linux + Docker internals deep dive | ForgeCI runs processes in containers; signals for cancel/timeout |
| [`concurrency.md`](./concurrency.md) | **15** (basics in 5) | With Java concurrency deep dive — theory beside [`01-java/07-concurrency.md`](../01-java/07-concurrency.md) | FlowGrid last-unit test, LedgerX transfers, ForgeCI worker pool |
| [`networking.md`](./networking.md) | **15** (HTTP basics in 3) | ForgeCI talks to GitHub, Docker, Redis; SSE live logs | Every deploy's SGs/DNS/TLS; SSE in ForgeCI + FlagForge |
| [`interview-questions.md`](./interview-questions.md) | From **5**, weekly; sweep **20+** | Spaced review | All four projects |

---

## The "explain it" standard

For each topic you should be able to give a **5-sentence answer**: *what* it is, *why* it exists,
*how* it works (one level deeper than the definition), the *trade-off*, and an *example from your
own code*. Example for "database index":

> An index is a separate sorted data structure (a B-tree in Postgres) mapping column values to row
> locations. It exists so lookups don't scan the whole table. The tree is shallow — 3–4 levels for
> millions of rows — so a lookup is a handful of page reads. The cost is slower writes and extra
> storage, since every insert updates every index. In LedgerX, the account-history query can walk a
> composite index on `(account_id, created_at)` backwards and stop after 50 rows — I'd show the
> before/after `EXPLAIN ANALYZE` from my `DATABASE.md`.

(The last sentence must describe what *you* measured — never borrow numbers.)

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
- [ ] Can name the four Coffman deadlock conditions and how lock ordering breaks one (LedgerX transfers)
- [ ] Can walk through "what happens when you type a URL" for 3+ minutes without notes
- [ ] Can explain why a B-tree index helps `WHERE account_id = ? ORDER BY created_at DESC LIMIT 50`
- [ ] Can explain MVCC and why readers don't block writers in Postgres
- [ ] Can name SOLID and give a real example of each from FlowGrid, LedgerX, ForgeCI or FlagForge
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
