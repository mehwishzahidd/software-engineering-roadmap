# Python — Résumé Tech Defense

> **Goal:** if Python is on your résumé (or comes up), defend it with truthful past context and
> current Python 3.12 working knowledge from a Java developer's angle: data types, comprehensions,
> functions, classes/dataclasses, exceptions, modules/venv, typing, and scripting.
>
> **Honesty rule:** in this roadmap Python is a **secondary** language (~5 h in Week 13 plus
> scripting reps). Say so: "Java is my primary language; I use Python for scripting and tooling."
> Don't claim Python backend (Django/FastAPI) depth you don't have. Java remains the interview
> language for DSA.

---

## Evidence in my projects

| Where | Python evidence |
|---|---|
| **Week 13 sprint** | [`../19-python/exercises.md`](../19-python/exercises.md): ports of Java exercises, CSV parsing, dict/Counter work |
| **P1 Ledger** (optional tooling) | `tools/gen_csv.py` — generates messy bank CSVs (bad dates, duplicates, different column orders) to test the importer |
| **P4 PulseWatch** | `scripts/report_uptime.py` — calls the API, computes per-monitor uptime %, writes CSV; `scripts/seed_monitors.py` using `urllib`/`requests` |
| **P2 TicketHold** | `scripts/concurrency_probe.py` — `concurrent.futures` fires 50 hold requests on one seat, checks exactly one 201 |

## Where to learn it in this repo

- [`../19-python/README.md`](../19-python/README.md)
- [`../19-python/python-for-java-devs.md`](../19-python/python-for-java-devs.md)
- [`../19-python/exercises.md`](../19-python/exercises.md)

---

## 1. Beginner questions

<details><summary><b>B1. Mutable vs immutable built-in types?</b></summary>

Immutable: `int`, `float`, `str`, `tuple`, `frozenset`, `bytes`. Mutable: `list`, `dict`, `set`, `bytearray`. Only hashable (typically immutable) objects can be dict keys / set members.
</details>

<details><summary><b>B2. List vs tuple vs set vs dict?</b></summary>

List: ordered, mutable (`ArrayList`). Tuple: ordered, immutable (records, dict keys). Set: unique, hash-based, O(1) avg membership (`HashSet`). Dict: key→value, insertion-ordered since 3.7 (`LinkedHashMap`-like).
</details>

<details><summary><b>B3. What is a list comprehension?</b></summary>

`[x * 2 for x in nums if x > 0]` — concise map+filter. Dict/set comprehensions and generator expressions `(x for x in ...)` (lazy) too. Java analog: streams.
</details>

<details><summary><b>B4. How does Python pass arguments?</b></summary>

By object reference ("pass by assignment") — like Java references: mutating a passed list is visible to the caller; rebinding the parameter isn't.
</details>

<details><summary><b>B5. The mutable default argument trap?</b></summary>

`def f(x, acc=[])` — default evaluated once at definition, shared across calls. Use `acc=None` then `acc = [] if acc is None else acc`.
</details>

<details><summary><b>B6. <code>==</code> vs <code>is</code>?</b></summary>

`==` value equality (`__eq__`), `is` identity. Use `is` only for `None` (and sentinels). Java analog: `.equals()` vs `==`.
</details>

## 2. Intermediate questions

<details><summary><b>I1. Dataclasses vs Java records?</b></summary>

```python
from dataclasses import dataclass
@dataclass(frozen=True, slots=True)
class Transaction:
    date: str
    amount: Decimal
    merchant: str
```
Generates `__init__`, `__repr__`, `__eq__` (and `__hash__` when frozen). Close to Java records.
</details>

<details><summary><b>I2. Exceptions — how do they compare to Java?</b></summary>

No checked exceptions. `try/except/else/finally`; `raise ... from e` chains causes. Catch specific exceptions; `with` statements (context managers) replace try-with-resources.
</details>

<details><summary><b>I3. What are generators?</b></summary>

Functions with `yield` produce lazy iterators — constant memory for large files: `for line in open(path)` streams lines. Java analog: a lazy `Stream`/`Iterator`.
</details>

<details><summary><b>I4. The GIL — what does it mean for concurrency?</b></summary>

In standard CPython, one thread executes Python bytecode at a time, so threads help I/O-bound work (HTTP calls) but not CPU-bound work → use `multiprocessing`/`ProcessPoolExecutor`. (3.13 introduced an experimental free-threaded build; 3.12, the version used here, has the GIL.) `asyncio` for many concurrent I/O tasks.
</details>

<details><summary><b>I5. Virtual environments and dependencies?</b></summary>

`python -m venv .venv && source .venv/bin/activate && pip install -r requirements.txt`. Isolates per-project packages (Maven analog: per-project dependency resolution, but Python installs into an environment). Modern: `pyproject.toml`, `uv`/`pip-tools` for lockfiles.
</details>

<details><summary><b>I6. Type hints — are they enforced?</b></summary>

No, not at runtime (like TS erasure). Checked by `mypy`/`pyright`. 3.12 adds `type` alias statements and new generic syntax `def first[T](xs: list[T]) -> T`.
</details>

<details><summary><b>I7. How do you handle money in Python?</b></summary>

`decimal.Decimal` from strings (`Decimal("19.99")`), never floats — same reasoning as `BigDecimal` in Ledger.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "You list Python — how much have you used it?"</b></summary>

Truthful past scope; "Java is primary; Python for scripting — e.g. the concurrency probe for TicketHold and the uptime report script for PulseWatch." Don't overclaim frameworks.
</details>

<details><summary><b>R2. "Can you solve this coding question in Python?"</b></summary>

Honest: "I'm faster and more precise in Java; can I use Java?" Most interviews allow it. If Python is required, know `collections.Counter/defaultdict/deque`, `heapq`, `bisect`, slicing, `sorted(key=...)`.
</details>

<details><summary><b>R3. "Write a script that reads a CSV and summarises spend by category."</b></summary>

```python
import csv
from collections import defaultdict
from decimal import Decimal
totals: dict[str, Decimal] = defaultdict(Decimal)
with open("tx.csv", newline="") as f:
    for row in csv.DictReader(f):
        totals[row["category"]] += Decimal(row["amount"])
for cat, amt in sorted(totals.items(), key=lambda kv: kv[1], reverse=True):
    print(f"{cat:<15}{amt:>10}")
```
</details>

<details><summary><b>R4. "Python vs Java — when would you pick each?"</b></summary>

Python: scripts, data analysis, glue, quick prototypes, ML ecosystems. Java: large long-lived services, strong typing, performance/concurrency on the JVM, Spring ecosystem. In my stack: Java services, Python tooling.
</details>

## 4. Practical tasks (doable live)

1. Two Sum and Group Anagrams in Python using `dict`/`defaultdict`.
2. Parse a log file, count status codes with `Counter`, print top 5.
3. Call a REST endpoint (`urllib.request` or `requests`), handle non-200 and timeouts.
4. Write a `@dataclass` with validation in `__post_init__`.
5. Use `ThreadPoolExecutor` to fetch 20 URLs concurrently with a timeout.

## 5. Debugging questions

<details><summary><b>D1. <code>ModuleNotFoundError</code> though you installed the package.</b></summary>

Installed in a different interpreter/venv. Check `which python`, `python -m pip install`, activate the venv.
</details>

<details><summary><b>D2. A function's list result keeps growing across calls.</b></summary>

Mutable default argument. Use `None` default.
</details>

<details><summary><b>D3. Threaded CPU-heavy script isn't faster.</b></summary>

GIL. Use processes or vectorised libraries.
</details>

<details><summary><b>D4. <code>0.1 + 0.2 != 0.3</code> in a finance script.</b></summary>

Binary floats; use `Decimal` from strings.
</details>

## 6. Architecture questions

<details><summary><b>A1. Where does Python fit around a Java/Spring system?</b></summary>

Ops scripts, data exports/reports, load/probe tools, one-off migrations of data (with tests), notebooks for analysis. Keep business logic in the Java service so it has one home.
</details>

<details><summary><b>A2. Script vs small service?</b></summary>

Script if run manually/cron with no API; service if other systems call it, it needs auth, scaling, monitoring — at which point Java/Spring may be the better fit for consistency.
</details>

## 7. Common mistakes (especially coming from Java)

- Mutable default args.
- Using `is` for value comparison.
- Float for money.
- Forgetting `newline=""` with the `csv` module.
- Bare `except:` swallowing everything.
- Installing packages globally instead of in a venv.
- Writing Java-style getters/setters instead of plain attributes/properties.

## 8. Terminology I must know

| Term | Meaning |
|---|---|
| Comprehension | Inline build of list/dict/set |
| Generator | Lazy iterator via `yield` |
| Context manager | `with` resource handling |
| Decorator | Function wrapping a function |
| Dunder method | `__eq__`, `__repr__`, ... |
| GIL | Global Interpreter Lock |
| venv | Isolated environment |
| PEP 8 | Style guide |
| Duck typing | Behaviour over declared type |
| `dataclass` | Generated boilerplate for data classes |

## 9. When to use it

- Scripts, automation, CSV/JSON wrangling, quick probes/load tools, data analysis.

## 10. When NOT to use it

- As a second backend language in a Java shop without a reason.
- Coding interviews if Java is your stronger language (and allowed).
- CPU-bound multithreaded work (without multiprocessing/native libs).

## 11. Trade-offs

| Gain | Cost |
|---|---|
| Fast to write, huge stdlib | Dynamic typing, runtime errors |
| Great for glue/data | Slower CPU-bound; GIL |
| Readable | Packaging/env management friction |

## 12. How it interacts with the rest of my stack

- **Spring Boot APIs:** Python scripts are clients (JSON over HTTP, bearer token).
- **Postgres:** `psycopg` for ad-hoc reports (read-only user).
- **Docker:** `python:3.12-slim` for tooling containers.
- **AWS:** `boto3` for S3 listing/uploads in scripts (same IAM least-privilege rules).
- **CI:** a job step can run Python checks (e.g. validating a generated OpenAPI file).
- **Linux:** replaces Bash once logic grows beyond ~30 lines.

## 13. One small hands-on exercise

**`uptime_report.py` for PulseWatch.**

- [ ] Fetches monitors and check results for a date range from the API (token from env var).
- [ ] Computes uptime % per monitor using `Decimal`, rounds to 2 dp.
- [ ] Writes a CSV and prints a sorted table.
- [ ] Handles HTTP errors/timeouts with clear messages and non-zero exit.
- [ ] Type-hinted; passes `mypy --strict`; 3 `pytest` tests.

## 14. Mastery checklist

- [ ] Translate 5 NeetCode Easy Java solutions to Python idiomatically
- [ ] Explain mutability, references, mutable defaults
- [ ] Use `Counter`, `defaultdict`, `deque`, `heapq`, dataclasses
- [ ] Explain the GIL and choose threads vs processes vs asyncio
- [ ] Set up a venv project with `pyproject.toml`
- [ ] Truthful 30-second answer: Java primary, Python for tooling
