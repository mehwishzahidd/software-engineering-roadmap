# Python — Résumé Tech Defense

**Target level:** L3 · **Learned:** Week 1 (core for interviews), Week 2 (interview toolkit + pitfalls), Week 3 (`pytest`, type hints, scripting) — then **daily**: every DSA/OA problem is solved in Python, and each project ships a Python component (FlowGrid W7–8, LedgerX W12, ForgeCI W18, FlagForge W22–23) · **Version:** Python 3.12
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md) · Templates: [`../PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md)

> **Goal:** Python is on the résumé twice over. It is the language you **code in** during coding
> interviews and OAs (Track A), and it is a **tooling language** you used for real in all four
> projects (Track B). Defend both: language-level questions (complexity, mutability, generators,
> the GIL) *and* "walk me through the Python tool in your project".
>
> **Honesty rule:** Java is the backend language of every project; Python never replaced it. Say
> exactly that: "Java for the services, Python for algorithms, verification and simulation
> tooling." Don't claim Django/FastAPI production depth you don't have. Do claim — because it's
> true — that every Python tool has `pytest` tests, type hints and a README.

---

## Evidence in my projects

| Where | Python evidence | What I can point at |
|---|---|---|
| **DSA track** (W1–26) | ≈185 problems solved in Python, spaced reviews, timed OA simulations from W17 | [`../trackers/dsa-tracker.md`](../trackers/dsa-tracker.md), pattern templates in [`../03-dsa/`](../03-dsa/README.md) |
| **FlowGrid** `tools/` (M4–M5, W7–8) | Synthetic inventory & order generator (realistic SKUs, warehouses, order mix; writes via API or SQL); **load/simulation harness** driving concurrent order creation with `concurrent.futures`, reporting latency percentiles and reservation contention — feeds the k6/benchmark protocol and failure exercises | `tools/gen_inventory.py`, `tools/load_orders.py`, `tests/`, `README.md`, numbers quoted in `PERFORMANCE.md` |
| **LedgerX** `tools/` (M4, W12) | **Independent reconciliation verifier**: reads Postgres directly with `psycopg` + `decimal`, proves every journal transaction sums to zero and `balance == sum(entries)` *without trusting the Java code*; transaction-data generator; consistency checker used in crash/retry failure exercises | `tools/verify_ledger.py`, `tools/gen_transactions.py`, `tests/test_verify.py` (fixture DB with a planted unbalanced txn) |
| **ForgeCI** `tools/` (M5, W18) | Test-repository generator (creates Git repos with `.forgeci.yml` variants: passing, failing, slow, timeout, bad config); **worker/load simulator** (fires HMAC-signed webhooks at a rate, measures queue wait and completion); build-result/log analysis (failure-taxonomy stats) | `tools/gen_repos.py`, `tools/fire_webhooks.py`, `tools/analyze_builds.py`, `README.md` |
| **FlagForge** `tools/` + `sdk-python/` (M3–M4, W22–23) | Rollout-distribution simulator (proves 10 % ± tolerance and stickiness over N users, compares against server evaluation); **minimal Python SDK / test client** implementing the same evaluation contract (polling + `ETag`, defaults, offline) used for cross-SDK contract tests; configuration linter (overlapping / unreachable / invalid rules) | `sdk-python/flagforge/client.py`, `tools/simulate_rollout.py`, `tools/lint_config.py`, contract tests shared with the Java SDK |

Rules every tool follows (ROADMAP §8): README, `pytest` tests, type hints, `pyproject.toml`, listed in the project's TESTING.md / PERFORMANCE.md where it is used. If a tool isn't used by a test, benchmark or failure exercise, it doesn't exist.

## Where to learn it in this repo

- [`../19-python/README.md`](../19-python/README.md) · [`../19-python/01-python-core.md`](../19-python/01-python-core.md) · [`../19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) · [`../19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md) · [`../19-python/04-testing-and-scripting.md`](../19-python/04-testing-and-scripting.md)
- [`../19-python/python-for-java-devs.md`](../19-python/python-for-java-devs.md) · [`../19-python/exercises.md`](../19-python/exercises.md) · [`../19-python/interview-questions.md`](../19-python/interview-questions.md)
- [`../PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md) · [`../03-dsa/README.md`](../03-dsa/README.md) · [`../OA_PREP.md`](../OA_PREP.md)

---

## 1. Beginner questions

<details><summary><b>B1. Mutable vs immutable built-in types?</b></summary>

Immutable: `int`, `float`, `str`, `tuple`, `frozenset`, `bytes`. Mutable: `list`, `dict`, `set`, `bytearray`. Only hashable (in practice immutable) objects can be dict keys / set members — a `tuple` of ints works as a memo key, a `list` doesn't.
</details>

<details><summary><b>B2. List vs tuple vs set vs dict — and the Java equivalents?</b></summary>

List: ordered, mutable (`ArrayList`). Tuple: ordered, immutable, hashable (a record / composite key). Set: unique, hash-based, O(1) average membership (`HashSet`). Dict: key → value, insertion-ordered since 3.7 (`LinkedHashMap`-like), O(1) average get/set.
</details>

<details><summary><b>B3. What is a comprehension, and when do you not use one?</b></summary>

`[x * 2 for x in nums if x > 0]` — map + filter in one expression; dict/set variants; generator expression `(… for …)` is lazy. Don't use one for side effects or when it needs a nested `if/else` that nobody can read — a plain loop is fine.
</details>

<details><summary><b>B4. How does Python pass arguments?</b></summary>

By object reference ("pass by assignment") — same as Java references: mutating a passed list is visible to the caller; rebinding the parameter isn't. This is also why `visited` sets can be shared across recursive DFS calls without returning them.
</details>

<details><summary><b>B5. The mutable-default-argument trap?</b></summary>

`def f(x, acc=[])` — the default is evaluated once at definition time and shared across calls. Use `acc=None` then `acc = [] if acc is None else acc`. Same trap in `@dataclass` fields → `field(default_factory=list)`.
</details>

<details><summary><b>B6. <code>==</code> vs <code>is</code>?</b></summary>

`==` value equality (`__eq__`); `is` identity. Use `is` only for `None` (and sentinels). Small-int/str interning makes `is` *appear* to work sometimes — never rely on it.
</details>

<details><summary><b>B7. Slicing: what does it cost and what does it return?</b></summary>

`a[i:j]` copies → O(k) time and memory. So `while s[1:]` in a loop is O(n²). Slices never raise on out-of-range bounds; `a[::-1]` reverses (copy); strings are immutable so every "edit" builds a new string — accumulate in a list and `''.join`.
</details>

<details><summary><b>B8. How do <code>sorted</code>, <code>key</code> and <code>lambda</code> fit together?</b></summary>

`sorted(items, key=lambda t: (-t.count, t.name))` — Timsort, O(n log n), stable, so multi-key sorts can be layered. `key` runs once per element (cheaper than `cmp_to_key`). `list.sort()` is in place and returns `None` — a classic bug.
</details>

## 2. Intermediate questions

<details><summary><b>I1. Complexity of the common operations you rely on in interviews?</b></summary>

List: index/append/pop-end O(1), insert/pop-front/`in` O(n). Dict/set: get/set/`in`/delete O(1) average, O(n) worst (hash collisions). `deque`: append/pop both ends O(1). `heapq`: push/pop O(log n), `heapify` O(n), peek O(1). `sorted` O(n log n). `str` concatenation in a loop O(n²) → `join`. `bisect` O(log n) search but O(n) insert into a list.
</details>

<details><summary><b>I2. Shallow vs deep copy, and aliasing bugs?</b></summary>

`b = a` aliases. `a[:]`, `list(a)`, `a.copy()`, `dict(d)` copy one level — nested lists are shared, so `grid = [[0] * n] * m` is the classic bug (`m` references to one row); use `[[0] * n for _ in range(m)]`. `copy.deepcopy` for nested structures.
</details>

<details><summary><b>I3. What are generators, and where did you use one?</b></summary>

Functions with `yield` produce lazy iterators — constant memory for large inputs. LedgerX verifier: `for txn in iter_journal(conn, batch=5000)` streams transactions with a server-side cursor instead of loading the whole ledger; ForgeCI log analysis streams multi-GB log files line by line. Java analog: a lazy `Stream`/`Iterator`.
</details>

<details><summary><b>I4. Dataclasses vs Java records?</b></summary>

```python
from dataclasses import dataclass
from decimal import Decimal

@dataclass(frozen=True, slots=True)
class Entry:
    txn_id: int
    account_id: int
    amount: Decimal
```
Generates `__init__`, `__repr__`, `__eq__` (and `__hash__` when frozen). `frozen=True` ≈ a record; `slots=True` saves memory. Validation goes in `__post_init__` (vs a record's compact constructor).
</details>

<details><summary><b>I5. The GIL — what does it mean for your load harness?</b></summary>

In CPython one thread executes Python bytecode at a time. Threads still help **I/O-bound** work — FlowGrid's harness fires HTTP requests from a `ThreadPoolExecutor` and the GIL is released while waiting on sockets, so 50 threads saturate the API fine. For **CPU-bound** work (rollout simulation over 10⁷ users) use `ProcessPoolExecutor` or vectorise. 3.13 has an experimental free-threaded build; 3.12 (used here) has the GIL.
</details>

<details><summary><b>I6. <code>heapq</code> is a min-heap — how do you get a max-heap and tie-breaking?</b></summary>

Push negated keys (`heappush(h, (-score, item))`) for a max-heap. Tuples compare element-wise, so push `(priority, counter, obj)` with an increasing counter to avoid comparing non-comparable objects and to keep FIFO order among equal priorities. Same trick as `PriorityQueue` + `Comparator` in Java.
</details>

<details><summary><b>I7. Recursion limit and how you handle deep recursion?</b></summary>

Default limit ≈ 1000 frames (`sys.getrecursionlimit()`). A DFS over a 10⁵-node path will hit it: raise the limit (`sys.setrecursionlimit(10**6)` — still bounded by the C stack) or convert to an explicit stack / iterative BFS. Say which you'd do in an OA: iterate.
</details>

<details><summary><b>I8. Type hints — are they enforced, and how do you use them?</b></summary>

Not at runtime (like TS erasure). `mypy --strict` / `pyright` check them. Every project tool is typed (`list[str]`, `dict[str, Decimal]`, `Iterator[Entry]`, `Optional` via `X | None`); 3.12 adds `type Alias = …` and `def first[T](xs: list[T]) -> T`. Hints make the tool's contract reviewable and let the IDE catch the `None` case before `pytest` does.
</details>

<details><summary><b>I9. Exceptions and context managers vs Java?</b></summary>

No checked exceptions; `try/except/else/finally`; `raise … from e` chains causes; catch specific types, never bare `except:`. `with` = try-with-resources — the verifier opens its `psycopg` connection in a `with` block so it closes on any exit; `contextlib.contextmanager` builds your own.
</details>

<details><summary><b>I10. How do you handle money in Python?</b></summary>

`decimal.Decimal` constructed from **strings** or DB `numeric` values (never from floats): `Decimal("19.99")`. `getcontext().prec` and `quantize(Decimal("0.0001"))` for the ledger's 4-decimal scale. The verifier compares `sum(entries) == Decimal(0)` exactly — the same reasoning as `BigDecimal` in LedgerX's Java code, and the point of writing the verifier in a second language.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "You list Python — how much have you used it?"</b></summary>

- Truthful past scope (scripts? none?). Then: "For the last six months Python has been my daily algorithm language — every LeetCode/NeetCode problem and OA — and the tooling language in all four projects: a load harness, an independent ledger verifier, a webhook simulator, a Python SDK, all with pytest tests."
- Draw the line yourself: "Backend services are Java/Spring; I haven't built a Django/FastAPI production service."
- Follow-up: "Which language would you use for the coding round?" → "Python, unless you need Java — I'm fluent in both collections libraries."
</details>

<details><summary><b>R2. "Walk me through a Python tool you built."</b></summary>

- Pick the **LedgerX verifier**: why it exists (an invariant checker that shares no code with the Java service can't share its bugs), what it checks (every journal txn sums to zero; `balance == sum(entries)`; no gaps in sequence), how (`psycopg` server-side cursor streaming, `Decimal`, exits non-zero with a report), how it's tested (pytest against a Testcontainers/Compose Postgres with a planted unbalanced transaction), where it's used (M4 crash/retry failure exercises, CI job).
- Follow-up: "What would it miss?" → anything both implementations agree on wrongly, e.g. a shared misunderstanding of the accounting model; and it's point-in-time, not continuous.
</details>

<details><summary><b>R3. "Why Python for interviews but Java for the backend?"</b></summary>

- Interviews reward speed and clarity: less boilerplate, `Counter`/`defaultdict`/`heapq`/slicing built in, fewer lines to get wrong in 30 minutes.
- Backends reward types, tooling, concurrency and ecosystem: Spring, JPA, HikariCP, Testcontainers, a JIT and real threads.
- "I keep one Java DSA rep a week so the collections stay fluent — I can do the round in Java if the team needs it."
</details>

<details><summary><b>R4. "Can you solve this in Python?" (live)</b></summary>

- Yes. Narrate: restate → examples/edge cases → brute force + complexity → better approach → code → trace one example → complexity. Use `collections`, `heapq`, `bisect`, `itertools` by name; say "O(1) average" for dict ops, not "O(1)".
- Method: [`../16-interview-prep/coding-interview-method.md`](../16-interview-prep/coding-interview-method.md).
</details>

<details><summary><b>R5. "How did your load harness measure contention, and can you trust the numbers?"</b></summary>

- `ThreadPoolExecutor(max_workers=N)` submits order creations against the *same* SKU; each future records latency and status; report p50/p95/p99, 201 vs 409 counts, and (from the API's metrics) lock-wait time. Warm-up excluded; N, machine, DB size, run count recorded in `PERFORMANCE.md`.
- Trust: it's a single-machine client with the GIL — fine for I/O concurrency at this scale, cross-checked against k6. Say what it can't tell you (server-side queueing vs client-side scheduling).
</details>

<details><summary><b>R6. "Why write a second SDK in Python for FlagForge?"</b></summary>

- A second implementation of the evaluation contract (bucketing `hash(flagKey:userKey) mod 10000`, rule priority, defaults, stale-if-error) catches contract ambiguities: if Java and Python disagree on any (flag, user) pair, the spec is wrong. Contract tests run both against the same server snapshot.
- Kept minimal on purpose (polling + `ETag`, no SSE) — say so.
</details>

<details><summary><b>R7. "What's the output?" (aliasing / default-arg / late-binding closure snippet)</b></summary>

Narrate the object graph: which names point to which objects, when defaults are evaluated, that lambdas in a loop capture the *variable* not the value (`[lambda: i for i in range(3)]` all return 2 — fix with `i=i`). These are the three snippets interviewers love; drill them in [`../19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md).
</details>

<details><summary><b>R8. "Python vs Java — when would you pick each?"</b></summary>

Python: scripts, data/verification tooling, simulators, glue, quick prototypes, ML ecosystems, coding interviews. Java: long-lived services, strong typing at scale, JVM performance and real threads, the Spring ecosystem, teams that already run it. In my stack: Java services, Python tooling — and the tooling is tested like production code.
</details>

## 4. Practical tasks (doable live)

1. Two Sum, Group Anagrams, Top-K Frequent in Python using `dict` / `defaultdict` / `Counter` / `heapq` — explain average vs worst complexity.
2. Parse a log file, count status codes with `Counter`, print the top 5 (the ForgeCI log-analysis shape).
3. Call a REST endpoint (`urllib.request` or `requests`), handle non-2xx and timeouts, exit non-zero (the harness client shape).
4. Write a `@dataclass(frozen=True)` with validation in `__post_init__`; show it works as a dict key.
5. Use `ThreadPoolExecutor` to fire 50 requests concurrently and collect latencies; explain why the GIL doesn't limit this.
6. Write a generator that streams rows from a cursor in batches; explain memory behaviour.
7. Fix the `[[0] * n] * m` bug and the mutable-default bug in a provided snippet.

## 5. Debugging questions

<details><summary><b>D1. <code>ModuleNotFoundError</code> although you installed the package.</b></summary>

Installed into a different interpreter/venv. `which python`, `python -m pip install …`, activate the venv, check `pyproject.toml`/lockfile.
</details>

<details><summary><b>D2. A function's list result keeps growing across calls.</b></summary>

Mutable default argument (or module-level list). Use `None` default / `default_factory`.
</details>

<details><summary><b>D3. The rollout simulator is slow and using one CPU.</b></summary>

CPU-bound under the GIL. `ProcessPoolExecutor` (chunk users per process), or vectorise the hashing; measure with `time.perf_counter` before/after.
</details>

<details><summary><b>D4. The verifier reports drift of <code>0.0001</code> on a few accounts.</b></summary>

Float sneaked in: a value built from `float`, or `Decimal(0.1)` instead of `Decimal("0.1")`, or the DB column read as `float`. Force `Decimal` at the boundary (`psycopg` returns `Decimal` for `numeric`), `quantize` consistently, add a test with `0.1 + 0.2`.
</details>

<details><summary><b>D5. <code>RecursionError</code> in a DFS during an OA.</b></summary>

Deep path (linked-list-shaped graph). Switch to an explicit stack; if time is short, `sys.setrecursionlimit` and say why it's a band-aid.
</details>

<details><summary><b>D6. Webhook simulator gets 401 from ForgeCI for every request.</b></summary>

Signature computed over a re-serialised body (`json.dumps` key order / spacing differs from the bytes sent). Sign the exact `bytes` you send; use `hmac.compare_digest`; test with a known-good fixture from GitHub's docs.
</details>

## 6. Architecture questions

<details><summary><b>A1. Where does Python fit around a Java/Spring system?</b></summary>

Verification and simulation tooling that must be *independent* of the service (LedgerX verifier), load and failure-injection harnesses (FlowGrid, ForgeCI), data generators, analysis/reporting, a second SDK for contract testing (FlagForge). Business logic stays in the Java service so it has one home.
</details>

<details><summary><b>A2. Why is an independent verifier worth more than another JUnit test?</b></summary>

A JUnit invariant test shares the service's entity mappings, `BigDecimal` handling and transaction boundaries — a bug there can hide in both the code and the test. A verifier reading raw rows with `psycopg` + `Decimal` is a second implementation of the invariants; agreement between two implementations is stronger evidence. Cost: two things to maintain; the contract (schema) must be stable.
</details>

<details><summary><b>A3. Script vs small service?</b></summary>

Script if run manually / in CI / on a schedule with no API; service if other systems call it, it needs auth, scaling, monitoring — at which point Java/Spring is the better fit for consistency with the rest of the stack.
</details>

<details><summary><b>A4. How would you design the Python SDK so it behaves like the Java SDK under failure?</b></summary>

Same contract, written down: poll with `If-None-Match`/`ETag`; keep the last snapshot in memory; on error serve stale (`stale-if-error`) and expose `last_updated`; offline mode from a file; defaults per flag; timeouts on every call; evaluation is pure (`evaluate(snapshot, flag, user) -> value`) so it can be contract-tested against recorded server results.
</details>

## 7. Common mistakes (especially coming from Java)

- Mutable default args; `[[0] * n] * m`.
- Using `is` for value comparison; relying on interning.
- Float for money; `Decimal(0.1)`.
- Building strings with `+=` in a loop; slicing inside a loop (O(n²)).
- `list.sort()` return value (`None`) assigned; `sorted` result ignored.
- Bare `except:`; swallowing `KeyboardInterrupt`.
- Forgetting `newline=""` with the `csv` module; not closing files (use `with`).
- Threads for CPU-bound work; global installs instead of a venv.
- Writing Java-style getters/setters and `ArrayList`-style loops instead of comprehensions and unpacking.

## 8. Terminology I must know

| Term | Meaning |
|---|---|
| Comprehension | Inline build of list/dict/set; generator expression is lazy |
| Generator | Lazy iterator via `yield` |
| Iterator protocol | `__iter__` / `__next__`; `for` calls them |
| Context manager | `with` resource handling (`__enter__`/`__exit__`) |
| Decorator | Function wrapping a function (`@lru_cache`, `@dataclass`) |
| Dunder method | `__eq__`, `__hash__`, `__lt__`, `__repr__`, … |
| GIL | Global Interpreter Lock — one thread runs bytecode at a time |
| Hashable | Has stable `__hash__` + `__eq__`; usable as key |
| Aliasing | Two names, one object |
| Late binding | Closures look up variables when called, not when defined |
| venv / `pyproject.toml` | Isolated environment / project metadata + deps |
| `pytest` fixture | Setup injected by parameter name |
| Duck typing | Behaviour over declared type |
| `Decimal` | Exact decimal arithmetic (money) |

## 9. When to use it

- Coding interviews and OAs (Track A) — by default.
- Verification, simulation, load, generation and analysis tooling around the Java services.
- Anything that talks to CSV/JSON/HTTP/Postgres and is < a few hundred lines with tests.

## 10. When NOT to use it

- As a second backend language in a Java shop without a reason.
- CPU-bound multithreaded work (without multiprocessing / native libs).
- Code that needs the JVM's type system and tooling to stay maintainable across a team.

## 11. Trade-offs

| Gain | Cost |
|---|---|
| Fast to write, huge stdlib, ideal under interview time pressure | Dynamic typing → runtime errors; hints + `mypy` mitigate |
| Great for glue, data, verification | Slower CPU-bound; the GIL |
| Readable, testable with `pytest` in minutes | Packaging/env friction; two toolchains in one repo |

## 12. How it interacts with the rest of my stack

- **Spring Boot APIs:** Python tools are clients (JSON over HTTP, bearer token / SDK key, `Idempotency-Key` per request in the harness).
- **Postgres:** `psycopg` with a read-only role for the verifier; `numeric` ↔ `Decimal`.
- **Redis:** not touched directly — tools observe effects through the API (e.g. cache hit ratio from Actuator).
- **Docker:** `python:3.12-slim` for tooling containers; harness run inside the Compose network in failure exercises.
- **AWS:** `boto3` for pulling S3 report exports in analysis scripts (same IAM least-privilege rules).
- **CI:** `pytest` + `mypy --strict` jobs for `tools/` next to `mvn verify`; the verifier runs as a CI step after the Java integration tests.
- **Git/GitHub:** ForgeCI's repo generator creates real Git repos and pushes them; the webhook simulator signs payloads like GitHub does.
- **Java:** the Java DSA rep re-implements one solved Python problem a week — `Counter` → `HashMap.merge`, `deque` → `ArrayDeque`, `heapq` → `PriorityQueue`.

## 13. One small hands-on exercise

**`verify_ledger.py` for LedgerX (≤ 3 h).**

- [ ] Connects with `psycopg` using a read-only role from an env var; streams `journal_txn` + `ledger_entry` in batches via a server-side cursor (generator).
- [ ] Reports every transaction whose entries don't sum to `Decimal(0)` and every account whose materialized balance ≠ `sum(entries)`; prints a table and exits non-zero on any finding.
- [ ] Typed; passes `mypy --strict`; `pytest` tests against a Compose/Testcontainers Postgres with a planted unbalanced transaction and a planted drift.
- [ ] Runs in CI after the Java suite; documented in LedgerX's `TESTING.md`.
- [ ] You can explain in 60 s why it's independent evidence and what it can't catch.

## 14. Mastery checklist

- [ ] Solve a NeetCode Medium in Python in ≤ 30 min, narrating complexity (average vs worst)
- [ ] Explain mutability, aliasing, copies, mutable defaults, late-binding closures with snippets
- [ ] Use `Counter`, `defaultdict`, `deque`, `heapq`, `bisect`, `itertools`, dataclasses fluently
- [ ] Explain the GIL and choose threads vs processes vs `asyncio` for a given tool
- [ ] Set up a typed, tested `pyproject.toml` project from scratch
- [ ] Walk through each project's Python component in 90 s: purpose, design, tests, where it's used
- [ ] Truthful 30-second answer: Python for interviews and tooling, Java for services
