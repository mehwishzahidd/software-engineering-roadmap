# Resources — a curated library, not a reading list

> Only stable, well-known sources: official documentation, long-lived books, NeetCode/LeetCode,
> and a handful of reference sites. No URLs that rot. Each entry says **what type it is**,
> **free or paid**, **what to use it for**, and **which weeks** it belongs to. If a resource is
> not tied to a week and a task in [`ROADMAP.md`](./ROADMAP.md), it is not in this file.

## How to use resources without tutorial hell

1. **Problem first, resource second.** Open a resource only when a milestone task, a checkpoint
   remediation row or a failed interview question sends you there. Reading "to be ready" is how
   six months disappear.
2. **One primary source per topic.** The official docs are the primary source for every
   technology here. Books and videos are secondary — for the *why*, not the *how*.
3. **Time-box reading to 25 minutes, then build for 60.** The weekly rhythm (ROADMAP §12)
   gives learning 2 h on Mon/Wed; the rest is building. Notes in your own words, 3 lines per
   concept, into the daily template.
4. **Never watch a course end to end.** Use a course's index like a table of contents: jump to
   the chapter the current milestone needs, then close it.
5. **Reproduce, don't copy.** After reading a snippet, close the page and write it from memory
   into the project. If you cannot, you did not learn it — read again, once.
6. **Every resource ends in an artefact**: a test, a commit, a `psql` session log, a benchmark,
   a flashcard set, or an explanation recorded out loud. No artefact → the reading did not
   happen.
7. **Two-track rule.** Track A (Python coding) resources are for DSA/OA speed; Track B (Java
   engineering) resources are for depth. Do not "study Python" when the gap is a failed Spring
   question, and vice versa — the checkpoint scoring table tells you which track failed.
8. **Paid resources**: only NeetCode Pro (already owned) and at most two books. Everything else
   in this file is free.

Legend — **Type:** docs · book · video · practice · reference · tool. **Cost:** free / paid.

---

## Python for interviews (Track A)

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| Python official documentation — Tutorial (docs.python.org "The Python Tutorial") | docs | free | Core syntax, data structures chapter (lists, dicts, sets, comprehensions), classes, errors. Read chapters 3–9 once. | 1–2 |
| Python Standard Library reference: `collections` (`Counter`, `defaultdict`, `deque`), `heapq`, `bisect`, `itertools`, `functools` (`lru_cache`/`cache`), `math`, `dataclasses`, `typing` | docs | free | The interview toolkit. Read each module page once, keep the page open while doing that week's problems. | 2, then on demand |
| Python Language Reference — "Data model" section | docs | free | `__eq__`/`__hash__`/`__lt__`, immutability, why tuples are hashable; read only when a pitfall bites. | 2, 9 |
| `pytest` official documentation (getting started, fixtures, parametrize, `tmp_path`) | docs | free | Testing the project `tools/` and your DSA examples. | 3, 7–8, 12, 18, 22–23 |
| `psycopg` 3 documentation | docs | free | LedgerX verifier: connections, server-side cursors, `Decimal` adaptation. | 12 |
| `decimal` module documentation | docs | free | Money in the verifier; quantize/rounding. | 12 |
| NeetCode (neetcode.io) — Python solutions and pattern videos | practice / video | free (Pro owned) | The primary DSA source; read the Python solution *after* solving, note one idiom per problem. Pro: roadmap ordering, practice sets. | 1–26 |
| LeetCode (leetcode.com) — problems by topic, discuss, contest | practice | free (Premium optional) | Unseen problems for checkpoints and OAs; company tags if Premium. | 1–26 |
| [`PYTHON_INTERVIEW_CHEATSHEET.md`](./PYTHON_INTERVIEW_CHEATSHEET.md) | reference (this repo) | free | Templates per pattern; rewrite from memory at each checkpoint remediation. | 2–26 |
| [`19-python/`](./19-python/README.md) | this repo | free | Core, toolkit, pitfalls & complexity, testing & scripting, exercises. | 1–3, then daily |
| *Fluent Python* (Ramalho, 2nd ed.) — chapters on sequences, dicts/sets, functions, data classes | book | paid (optional) | Deeper *why* behind idioms; read only the chapters named, only if pitfalls keep recurring. | 12+ (light week) |

## Java (Track B)

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| Java SE 21 API documentation (Javadoc) | docs | free | Collections, `java.util.concurrent`, `java.time`, `BigDecimal`, streams. The allowed reference during checkpoints. | 1–26 |
| The Java Tutorials (Oracle "Learning the Java Language", Collections, Concurrency trails) | docs | free | Foundation weeks; the Concurrency trail before W5 and again in W15. | 1–3, 5, 15 |
| JEP index (openjdk.org) — records, sealed classes, pattern matching for switch, virtual threads | docs | free | Modern Java features used in projects (records for DTOs, sealed rule types in FlagForge, virtual threads in ForgeCI). Read the JEP summary, not the whole spec. | 3, 15, 20–21 |
| *Effective Java* (Bloch, 3rd ed.) | book | paid | Items on `equals`/`hashCode`, immutability, generics, enums, exceptions, lambdas. Read 2–3 items per week tied to code you are writing. | 2–13 |
| *Java Concurrency in Practice* (Goetz et al.) | book | paid (optional) | Chapters 2–5 (thread safety, sharing objects, composing objects) before FlowGrid reservations; chapters 6–8 (task execution, cancellation, thread pools) for ForgeCI workers. | 5, 15–17 |
| Maven documentation — POM reference, lifecycle, Surefire/Failsafe plugin docs | docs | free | Multi-module ForgeCI repo; `verify` vs `test`; dependency scopes. | 2, 14 |
| JMH (OpenJDK Code Tools) — README and samples | tool / docs | free | Microbenchmarks for FlagForge local evaluation (p99), LedgerX hot paths. Follow the samples; beware dead-code elimination and warm-up. | 21, 25 |
| [`01-java/`](./01-java/README.md), [`17-resume-tech-defense/java.md`](./17-resume-tech-defense/java.md), [`03-dsa/java-dsa-toolkit.md`](./03-dsa/java-dsa-toolkit.md) | this repo | free | Learning path, defense drills, the Java rep toolkit. | 1–26 |

## Data structures & algorithms

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| NeetCode 150 / NeetCode 250 roadmaps | practice | free / Pro | The problem sequence behind ROADMAP §7; solve in Python, one Java rep per week. | 1–26 |
| NeetCode pattern videos (one per pattern) | video | free | One video per new pattern *before* the first problem; never binge. | pattern week |
| LeetCode Explore cards and topic tags | practice | free | Extra unseen problems for checkpoints. | 4, 8, 12, 16, 20, 24 |
| *Grokking Algorithms* (Bhargava) | book | paid (optional) | Only if Big-O or recursion intuition is missing in W1–8. | 1–8 |
| *Introduction to Algorithms* (CLRS) — selected chapters | book | paid (optional) | Reference for graphs (BFS/DFS/topological sort/Dijkstra), DP; look up, do not read cover to cover. | 13–19 |
| [`03-dsa/`](./03-dsa/README.md), [`OA_PREP.md`](./OA_PREP.md), [`trackers/dsa-tracker.md`](./trackers/dsa-tracker.md) | this repo | free | Pattern guides (Python first, Java section), OA protocol, spaced-review tracking. | 1–26 |

## SQL, PostgreSQL, MySQL, Redis

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| PostgreSQL 16 official manual — Tutorial, SQL Language, "Concurrency Control" (MVCC, isolation), "Performance Tips" (`EXPLAIN`), Indexes, Triggers, `psql` | docs | free | The primary SQL source. Concurrency Control chapter before FlowGrid M2 and LedgerX M2; `EXPLAIN` chapter in W6. | 3–6, 9–12 |
| MySQL 8 reference manual — selected pages (InnoDB locking, isolation, differences from Postgres) | docs | free | ≈ 2 h diff against Postgres for résumé defense. | 6 |
| *Designing Data-Intensive Applications* (Kleppmann) — chapters 3 (storage), 7 (transactions), 8 (distributed trouble), 11 (stream processing: outbox/at-least-once) | book | paid | The *why* behind isolation, locking, idempotency, outbox, queues. One chapter per relevant week. | 5, 9–11, 15, 17 |
| *Use The Index, Luke* (use-the-index-luke.com) | reference | free | Index anatomy, composite index column order, `EXPLAIN` reading. | 6, 8 |
| pgexercises.com | practice | free | SQL drills when [`04-sql-databases/sql-practice.md`](./04-sql-databases/sql-practice.md) is exhausted. | 3–6 |
| Redis official documentation — data types, `SET` options/TTL, `BLMOVE`, Streams + consumer groups, pub/sub, Lua scripting (`EVAL`), persistence (RDB/AOF) | docs | free | FlowGrid cache-aside (W6), ForgeCI queue/leases/pub-sub (W15–17), FlagForge snapshots (W21, 23). | 6, 15–17, 21, 23 |
| Redis University (free courses) — "Redis for Java developers" style modules | video / practice | free | Only the queue and pub/sub modules if the docs are not enough. | 15 |
| Flyway documentation | docs | free | Migrations, versioning, repeatable migrations, backfills. | 4, 20 |
| [`04-sql-databases/`](./04-sql-databases/README.md), [`practice-schema.sql`](./04-sql-databases/practice-schema.sql), [`04-sql-databases/redis.md`](./04-sql-databases/redis.md) | this repo | free | Learning path, the practice database, Redis mechanics. | 3–26 |

## Spring Boot & backend

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| Spring Boot reference documentation (3.x) — Core features, Web, Data, Testing, Actuator | docs | free | Primary source; `@SpringBootTest` vs slices in W5, Actuator in W6+. | 3–26 |
| Spring Framework reference — Core (IoC, AOP proxies), Data Access (transactions: propagation, isolation, rollback rules), Web MVC (async, SSE, `ProblemDetail`) | docs | free | Transactions chapter before FlowGrid M2 and LedgerX M2; MVC async/SSE before ForgeCI M3. | 4–5, 10, 16 |
| Spring Security 6 reference — Servlet: Authentication, Authorization (method security), OAuth2 resource server (JWT) | docs | free | JWT setup W4; method security; GitHub OAuth client in W14. | 4, 14, 20 |
| Spring Data JPA reference + Hibernate ORM user guide (locking, fetching, N+1, `@Version`) | docs | free | Entity mapping W4; locking modes and `@Version` W5, W10; fetch strategies when N+1 shows up. | 4–5, 10 |
| springdoc-openapi documentation | docs | free | OpenAPI UI for every project. | 4 |
| Testcontainers for Java documentation (Postgres, Redis modules; JUnit 5 integration; Boot `@ServiceConnection`) | docs | free | FlowGrid concurrency test W5; ForgeCI Postgres+Redis integration tests W18. | 5, 12, 18 |
| JUnit 5 user guide; Mockito documentation (javadoc + "Mockito in a nutshell") | docs | free | Foundation testing W2; slices/mocks W5. | 2, 5 |
| Baeldung (baeldung.com) — as a *secondary* lookup only | reference | free | Quick examples for a specific Spring feature; always confirm against the official reference. Never as a course. | on demand |
| [`05-spring-boot/`](./05-spring-boot/README.md), [`09-testing/`](./09-testing/README.md) | this repo | free | Learning path and testing guides. | 3–26 |

## REST & HTTP

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| MDN Web Docs — HTTP (methods, status codes, headers, caching, CORS, cookies, `text/event-stream`) | docs | free | HTTP semantics W3; CORS when the dashboard first calls the API W7; SSE W16. | 3, 7, 16 |
| RFC 9110 (HTTP Semantics), RFC 9457 (Problem Details), RFC 7519 (JWT), RFC 6455 (WebSockets) — read selectively | reference | free | Precise definitions for idempotent methods, ProblemDetail fields, JWT claims; SSE vs WS trade-off. | 3–5, 16 |
| curl manual (`man curl`, "Everything curl" online book) | docs | free | `curl -v`, `-N` for SSE, `--data-binary` for signed webhooks. | 3, 14, 16 |
| Postman / Bruno docs | tool | free | Collections per project for manual testing; export into the repo. | 4+ |
| [`06-rest-apis/`](./06-rest-apis/README.md) | this repo | free | API design guide used for every project's API. | 3–5, 11, 22 |

## JavaScript, TypeScript, React

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| MDN — JavaScript Guide (functions, closures, promises, async/await, modules, event loop) | docs | free | JS core W6. Read the "Using promises" and "Async functions" pages before touching React. | 6 |
| javascript.info (The Modern JavaScript Tutorial) — parts 1 and selected async chapters | reference | free | Secondary explanation of closures, `this`, event loop if MDN is too terse. | 6 |
| TypeScript Handbook (typescriptlang.org) — Basics, Everyday Types, Narrowing, Object Types, Generics, Utility Types | docs | free | TS in W7; typing the API client; discriminated unions for status badges. | 7, 16, 23 |
| React official documentation (react.dev) — "Learn" section: Describing UI, Adding Interactivity, Managing State, Escape Hatches (`useEffect`, refs) | docs | free | React W7; "Synchronizing with Effects" and "You Might Not Need an Effect" before the dashboard's data fetching. | 7, 16, 23 |
| Vite documentation | docs | free | Project setup, env variables, dev proxy to the API, production build in Docker. | 7 |
| React Router documentation | docs | free | Routing for dashboards. | 7 |
| TanStack Query documentation (optional) | docs | free | Server-state fetching/caching if you adopt it for the ForgeCI/FlagForge UIs; otherwise hand-written fetch hooks are fine. | 16, 23 |
| Testing Library (React) + Vitest documentation | docs | free | Component tests in [`09-testing/frontend-testing.md`](./09-testing/frontend-testing.md). | 7, 23 |
| MDN — HTML and CSS basics (flexbox, grid, forms) | docs | free | The ≈ 3 h minimum. Priority VERY LOW; stop when the dashboard is usable. | 7 |
| [`07-javascript-typescript/`](./07-javascript-typescript/README.md), [`08-react/`](./08-react/README.md) | this repo | free | Learning path. | 6–7, 16, 23 |

## Testing

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| JUnit 5 User Guide — writing tests, parameterized tests, extensions, parallel execution | docs | free | Foundation; parameterized tests for state machines. | 2, 5, 11 |
| Mockito documentation | docs | free | Service tests; argument captors; `verifyNoMoreInteractions` with care. | 5 |
| Testcontainers documentation | docs | free | Real Postgres/Redis in tests; reuse and speed tips. | 5, 12, 18 |
| AssertJ documentation | docs | free | Fluent assertions; `usingRecursiveComparison` for DTOs. | 4+ |
| k6 documentation (grafana.com/docs/k6) — scenarios, thresholds, checks, results output | tool / docs | free | Load baselines: FlowGrid order creation W8, ForgeCI webhook/queue W18, FlagForge evaluation W23; methodology in [`18-projects/templates/benchmark-report.md`](./18-projects/templates/benchmark-report.md). | 8, 18, 23, 25 |
| jqwik documentation (optional) | docs | free | Property-based tests for LedgerX invariants if you go beyond seeded random tests. | 12 |
| *Growing Object-Oriented Software, Guided by Tests* (Freeman & Pryce) — chapters on test smells and integration tests | book | paid (optional) | If your tests become brittle mocks-of-mocks in W9–13. | 9–13 |
| [`09-testing/`](./09-testing/README.md) | this repo | free | JUnit, Mockito, Spring slices, Testcontainers, frontend testing. | 2–26 |

## Linux & shell

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| *The Linux Command Line* (Shotts) — free PDF from the author's site | book | free | W1 basics (part 1), W14 deep dive (processes, permissions, scripting parts). | 1, 14 |
| GNU Bash Reference Manual; `man` pages (`man 7 signal`, `man ps`, `man chmod`, `man ulimit`) | docs | free | Signals and permissions before ForgeCI; Bash scripts for deploy/cleanup. | 14 |
| explainshell.com | reference | free | Decode any command you copied before you run it. | 1+ |
| ShellCheck (shellcheck.net / CLI) | tool | free | Lint every Bash script in the repos. | 8, 14, 18 |
| [`10-linux/`](./10-linux/README.md) | this repo | free | Commands, scripting, exercises. | 1, 14 |

## Docker

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| Docker documentation — Get started, Dockerfile reference, Build (multi-stage, cache), Compose file reference (v2), Networking, Volumes, Engine API reference | docs | free | Compose for Postgres W4; Dockerfile W5; internals + Engine API W14; multi-worker Compose W18. | 4–5, 14, 18 |
| Docker security documentation (rootless mode, capabilities, seccomp, the daemon socket) | docs | free | ForgeCI SECURITY.md: what mounting the socket means; container hardening flags. | 14, 19 |
| docker-java (github.com/docker-java/docker-java) — README and wiki | docs | free | ForgeCI worker: create/start/attach/wait/remove containers, log streaming, resource limits. Read the "Getting started" and the `DockerClient` javadoc. | 15–17 |
| Docker Engine API (versioned OpenAPI reference on docs.docker.com) | docs | free | When docker-java's abstraction is unclear, read the underlying endpoint (`/containers/create`, `/containers/{id}/logs`, `/containers/{id}/kill`). | 15, 17 |
| [`11-docker/`](./11-docker/README.md) | this repo | free | Dockerfiles, Compose, exercises. | 4–5, 14, 18 |

## AWS

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| AWS documentation — IAM user guide (policies, roles, instance profiles, least privilege), EC2 user guide, RDS for PostgreSQL, S3 user guide (buckets, presigned URLs, lifecycle), CloudWatch (logs agent, metrics, alarms), ElastiCache for Redis (only if chosen) | docs | free | Each deploy: FlowGrid W7–8, LedgerX W13, ForgeCI W19, FlagForge W23. Read the specific "getting started" for each service the week you use it. | 7–8, 13, 19, 23 |
| AWS Free Tier page and Billing/Cost Explorer docs; AWS Budgets | docs | free | Set a budget alarm before creating anything ([`12-aws/cost-safety.md`](./12-aws/cost-safety.md)). | 7 |
| AWS CLI reference | docs | free | Scripting deploys; `aws s3 cp`, `aws logs tail`. | 8+ |
| Amazon SQS developer guide — visibility timeout, DLQ, at-least-once | docs | free | The "SQS considered" ADR for ForgeCI. | 19 |
| AWS Well-Architected Framework — Security and Reliability pillars (skim) | reference | free | Vocabulary for SECURITY.md and design interviews. | 19, 24 |
| [`12-aws/`](./12-aws/README.md), [`17-resume-tech-defense/aws.md`](./17-resume-tech-defense/aws.md) | this repo | free | Deploy walkthrough, cost safety, defense drills. | 7–8, 13, 19, 23 |

## CI/CD & Git

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| Pro Git book (git-scm.com/book) — chapters 2, 3, 7 (tools: stash, rewriting history, bisect, worktree) | book | free | Foundation W1–2; rebase/PR workflow W5; bisect/worktree when needed. | 1–2, 5, 12, 16 |
| Git reference manual (`git help <cmd>`) | docs | free | Exact flags during exercises. | 1+ |
| GitHub Docs — Pull requests, branch protection, Actions (workflow syntax, caching, services, secrets, environments), Packages/GHCR, Webhooks (payloads, securing webhooks with HMAC), OAuth apps / GitHub Apps, REST API | docs | free | Actions from W4; GHCR + deploy W8; webhooks/OAuth/API for ForgeCI W14. | 4, 8, 14, 18 |
| Conventional Commits specification | reference | free | Commit message convention across repos. | 2+ |
| Semantic Versioning specification (semver.org) | reference | free | FlagForge SDK versioning; release tags. | 22 |
| [`02-git/`](./02-git/README.md), [`13-cicd/`](./13-cicd/README.md) | this repo | free | Workflows, exercises, pipeline examples. | 1–2, 4–5, 8, 14, 18 |

## CS fundamentals

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| *Operating Systems: Three Easy Pieces* (OSTEP, free online) — Virtualization (processes, scheduling intro), Concurrency (threads, locks, condition variables), Persistence (files, crash consistency, journaling) | book | free | Processes/signals W14; locks W5/W15; crash consistency W12 (LedgerX) — read the named chapters only. | 5, 12, 14–15 |
| *Computer Networking: A Top-Down Approach* (Kurose & Ross) — chapters 2 (application layer/HTTP/DNS), 3 (transport/TCP) | book | paid (optional) | If [`14-cs-fundamentals/networking.md`](./14-cs-fundamentals/networking.md) is not enough. | 3, 15 |
| High Performance Browser Networking (hpbn.co) — TCP, TLS, HTTP/2 chapters | book | free | TLS handshake, HTTP/2, SSE/WebSocket transport details. | 15–16 |
| *Designing Data-Intensive Applications* — chapters 5–6 (replication, partitioning) | book | paid | Database internals and system-design vocabulary. | 9, 20 |
| PostgreSQL internals docs — "Database Physical Storage", WAL configuration/internals | docs | free | MVCC, WAL, checkpoints for LedgerX durability explanations. | 9, 12 |
| [`14-cs-fundamentals/`](./14-cs-fundamentals/README.md) | this repo | free | Memory/architecture, OS, concurrency, networking, DB internals, practices, interview questions. | 5, 9, 14–15, 20+ |

## System design

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| *Designing Data-Intensive Applications* (Kleppmann) | book | paid | The one system-design book worth owning at this level; chapters as listed above. | 5–20 |
| *System Design Interview – An Insider's Guide* (Xu, vol. 1) — chapters on framework, rate limiter, key-value store, unique ID, notification system | book | paid (optional) | Junior-level framing; the rate limiter and notification chapters map to FlagForge/ForgeCI. | 20–22 |
| The System Design Primer (github.com/donnemartin/system-design-primer) | reference | free | Vocabulary and trade-off tables; skim, do not memorise. | 20 |
| martinfowler.com — "Patterns of Enterprise Application Architecture" catalog (Optimistic/Pessimistic Offline Lock, Unit of Work); microservices.io pattern catalog (Transactional outbox, Idempotent consumer, Saga) | reference | free | Named patterns you already implemented — for interview vocabulary. | 10–11, 20 |
| AWS Architecture Center / Builders' Library (selected articles: retries with backoff and jitter, timeouts, avoiding fallback) | reference | free | Failure taxonomy and retry design for ForgeCI M4. | 17 |
| [`15-system-design/`](./15-system-design/README.md), [`16-interview-prep/system-design-interview.md`](./16-interview-prep/system-design-interview.md) | this repo | free | Fundamentals, caching, scalability, junior problems, the interview method. | 20–26 |

## Interviews & career

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| NeetCode Pro — mock/practice mode | practice | paid (owned) | Timed sets for OA sims. | 17–26 |
| Pramp (pramp.com) / interviewing.io (free tier) | practice | free / paid | Peer mocks weekly from W14; CP-24 loop with a real interviewer. | 14–26 |
| LeetCode contests (weekly/biweekly) | practice | free | OA-like time pressure with unseen problems; one per fortnight from W17. | 17–26 |
| *Cracking the Coding Interview* (McDowell) — behavioral chapter, "Big O", and the interview process chapters only | book | paid (optional) | Process and behavioral framing; skip the problem sets (NeetCode covers them in Python). | 8, 24 |
| HackerRank / CodeSignal practice environments | practice | free | Get used to the actual OA editors, stdin/stdout, hidden tests. | 17 |
| Company engineering blogs (read one per target company before interviewing) | reference | free | Specific talking points for "why us" and design questions. | 12+ |
| [`16-interview-prep/`](./16-interview-prep/README.md), [`INTERVIEW_CHECKLIST.md`](./INTERVIEW_CHECKLIST.md), [`OA_PREP.md`](./OA_PREP.md), [`RESUME_TECH_DEFENSE.md`](./RESUME_TECH_DEFENSE.md), [`RESUME_INTERVIEW_QUESTIONS.md`](./RESUME_INTERVIEW_QUESTIONS.md), [`JOB_READINESS.md`](./JOB_READINESS.md) | this repo | free | Method, checklists, drills, readiness. | 5–26 |

## Project-specific references

| Project | Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|---|
| FlowGrid | *Domain-Driven Design Distilled* (Vernon) — chapters on bounded contexts and aggregates (optional) | book | paid | Modelling inventory states, reservations and allocations as aggregates with invariants. | 4–6 |
| FlowGrid | PostgreSQL manual: "Explicit Locking" (`FOR UPDATE`, `SKIP LOCKED`), "Transaction Isolation" | docs | free | Reservation concurrency; the `@Version` comparison. | 5 |
| LedgerX | Martin Fowler — "Accounting Patterns" (Account, Entry, Accounting Transaction, Posting Rule) — free on martinfowler.com | reference | free | The double-entry model before M1. | 9 |
| LedgerX | Modern Treasury — "Ledgers" documentation / journal entries concepts; Square Developer blog "Books, an immutable double-entry accounting database service" | reference | free | Industry framing of immutable ledgers, balances, idempotency for money movement (concept reference; do not copy APIs). | 9–10 |
| LedgerX | Stripe API docs — "Idempotent requests" | docs | free | The canonical description of key + fingerprint + stored response behaviour. | 5, 10 |
| LedgerX | microservices.io — Transactional outbox, Idempotent consumer | reference | free | Outbox in M3. | 11 |
| ForgeCI | GitHub Docs — Webhooks (events & payloads, validating deliveries with HMAC, redeliveries), OAuth apps (authorization code flow), REST API (repos, statuses/checks) | docs | free | M1 integration; commit status updates. | 14 |
| ForgeCI | Redis docs — `BLMOVE`, reliable queue pattern, Streams consumer groups (`XREADGROUP`, `XPENDING`, `XCLAIM`), pub/sub, `EVAL` | docs | free | M2 queue decision and M4 limits. | 15, 17 |
| ForgeCI | docker-java README/wiki; Docker Engine API reference | docs | free | M2 execution, M4 kill/timeouts. | 15, 17 |
| ForgeCI | MDN — Server-sent events (`EventSource`, `Last-Event-ID`); Spring MVC `SseEmitter` reference | docs | free | M3 live logs. | 16 |
| ForgeCI | GitHub Actions docs (workflow syntax, `needs:`) as a *design reference* for `.forgeci.yml` and DAG semantics | docs | free | M1 config, M6 DAG. | 14, 19 |
| ForgeCI | AWS Builders' Library — "Timeouts, retries, and backoff with jitter" | reference | free | M4 retry policy. | 17 |
| FlagForge | LaunchDarkly documentation — concepts (flags, environments, targeting rules, percentage rollouts, segments), SDK concepts (client-side vs server-side, polling vs streaming, bootstrapping, offline mode, default values) | docs | free | **Concept reference only** — understand what a mature product exposes; design your own API and never copy their SDK code. | 20–23 |
| FlagForge | OpenFeature specification (openfeature.dev) | reference | free | Vendor-neutral evaluation API vocabulary (evaluation context, hooks, providers) — useful for SDK design decisions. | 22 |
| FlagForge | MurmurHash3 reference (Guava `Hashing.murmur3_32_fixed` javadoc; Python `mmh3` package docs) | docs | free | A stable cross-language hash for bucketing, contract-tested between Java and Python SDKs. | 21–22 |
| FlagForge | Maven Central / Sonatype publishing guide (or GitHub Packages for Maven) | docs | free | Publishing the SDK artifact. | 22 |
| FlagForge | JMH samples | tool | free | p99 local-evaluation benchmark. | 21 |
| All | k6 docs; [`18-projects/templates/benchmark-report.md`](./18-projects/templates/benchmark-report.md) | tool / this repo | free | Every measured number that could become a résumé bullet. | 8, 13, 18, 23, 25 |
| All | OWASP API Security Top 10 (owasp.org) | reference | free | Week 24 security pass on every project. | 24 |
| All | C4 model (c4model.com); Mermaid docs; dbdiagram.io or `pg_dump`-based ERD tools | reference / tool | free | Architecture diagrams and ERDs in W25. | 25 |
| All | [`18-projects/`](./18-projects/README.md) | this repo | free | The specs, milestones, failure engineering, interview questions, docs & résumé guidance. | 4–26 |

## Python & C++ (secondary languages)

| Resource | Type | Cost | Use it for | Weeks |
|---|---|---|---|---|
| Python docs — `argparse`, `pathlib`, `csv`, `json`, `subprocess`, `logging`; `requests` documentation | docs | free | Scripting for project tools (generators, simulators, analysis). | 3, 7–8, 12, 18, 22–23 |
| `pyproject.toml` / packaging guide (packaging.python.org) | docs | free | Each `tools/` directory and `sdk-python/` as an installable package with `pytest`. | 7, 12, 18, 22 |
| [`19-python/python-for-java-devs.md`](./19-python/python-for-java-devs.md) | this repo | free | Mapping Java idioms to Python and back (helps the weekly Java rep too). | 1–3 |
| cppreference.com; *A Tour of C++* (Stroustrup, 3rd ed.) chapters 1–4 | docs / book | free / paid (optional) | The ≈ 4 h C++ basics sprint. Priority LOW. | 21 |
| [`20-cpp-basics/README.md`](./20-cpp-basics/README.md) | this repo | free | The sprint itself. | 21 |

---

## Not on this list, on purpose

- Twelve-hour "complete bootcamp" videos for any technology.
- Random Medium/blog tutorials for Spring Security or Docker — they are frequently outdated
  (Spring Security 5 vs 6, Compose v1 vs v2).
- Multiple DSA platforms at once. NeetCode + LeetCode is enough.
- Kubernetes, Kafka, microservices frameworks — out of scope by design (ROADMAP §2.4).
- Any resource that would replace building the projects.

When a resource you love is missing here, ask: which week, which milestone, which artefact?
If there is an answer, add it to the relevant row. If not, close the tab.
