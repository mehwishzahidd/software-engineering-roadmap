# 19 · Python — the coding-interview language

> **Python 3.12 is the primary language for DSA, LeetCode/NeetCode, online assessments and coding interviews (Track A).**
> **Java 21 is the primary language for backends, Spring Boot, the four flagship projects and the résumé/software-engineering interview (Track B).**
> The two tracks never mix: nothing is solved in both languages by default. Source of truth: [`ROADMAP.md`](../ROADMAP.md) §4, §7, §10.

The quick reference you keep open while practising is the root [`PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md). This folder is where the cheatsheet is *learned*.

---

## Module map

| File | Week | Hours | Outcome — you can... |
|---|---|---:|---|
| [`01-python-core.md`](./01-python-core.md) | W1 | 8–10 | write correct Python from a blank file: types, strings, slicing, lists/tuples/sets/dicts with their Big-O, control flow, functions/closures/lambdas, comprehensions, `enumerate`/`zip`/`sorted`, recursion, exceptions, classes |
| [`02-interview-toolkit.md`](./02-interview-toolkit.md) | W2 | 6–8 | reach for the right stdlib tool without thinking: `Counter`, `defaultdict`, `deque`, `heapq`, `bisect`, `itertools`, `functools`, `math`, `dataclasses`, `typing`; build node classes for lists/trees/graphs; write custom sort keys and `__lt__` |
| [`03-pitfalls-and-complexity.md`](./03-pitfalls-and-complexity.md) | W2 | 4–5 | explain every classic Python trap (mutable defaults, aliasing, copies, `[[0]*n]*m`, recursion limit, heap ties...) with a demo and a fix; state the complexity of any built-in operation and *why* |
| [`04-testing-and-scripting.md`](./04-testing-and-scripting.md) | W3 | 6–8 | write `pytest` tests with fixtures/parametrize, type-check with `mypy`/`pyright`, set up a venv + `pyproject.toml`, write CLI tools (`argparse`, `pathlib`, `csv`/`json`, `subprocess`, `httpx`, `psycopg`, `Decimal`, `logging`, `concurrent.futures`) — the skills behind each project's Python component |
| [`python-for-java-devs.md`](./python-for-java-devs.md) | W1–2 | 1–2 | translate every Java concept you know into its Python equivalent and avoid the habits Java developers bring across |
| [`exercises.md`](./exercises.md) | W1–3 | ongoing | 40+ graded exercises with acceptance criteria + 8 "Java rep" exercises |
| [`interview-questions.md`](./interview-questions.md) | W2+ | ongoing | answer 50+ Python interview questions (semantics, complexity, stdlib, pitfalls, language strategy) |

Total planned learning time: ~30 h across Weeks 1–3, then Python is **used daily** for DSA (6–8 h/week) and in each project's Python component (W7–8, W12, W18, W22–23).

---

## Language strategy (why two languages)

| | Python | Java |
|---|---|---|
| Used for | LeetCode/NeetCode, OAs (HackerRank, CodeSignal, Amazon-style), timed problems, mock coding interviews, project tooling (`tools/` generators, simulators, verifiers, analysis, Python SDK) | Spring Boot backends of FlowGrid, LedgerX, ForgeCI, FlagForge; JUnit; concurrency; architecture; résumé defense; project deep-dives |
| Why | Fastest to write under time pressure: no boilerplate, built-in tuples/dicts/sets/heaps, slicing, comprehensions, arbitrary-precision ints; the NeetCode reference solutions are in Python | The résumé says Java/Spring; employers hire juniors to write backend Java; depth in OOP/concurrency/testing is what the SE interview probes |
| Weekly time | 6–8 h DSA + project tooling when the milestone calls for it | 25–30 h project + learning |
| Java DSA reps | — | ≈1 already-solved problem/week re-done in Java to keep `HashMap`/`ArrayDeque`/`PriorityQueue`/`Comparator` fluent (logged in [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md), never counted as new) |

**Rule:** if an interviewer lets you choose, choose Python for algorithms. If the role's OA is Java-only (rare), the Java rep exercises in [`exercises.md`](./exercises.md) and [`03-dsa/java-dsa-toolkit.md`](../03-dsa/java-dsa-toolkit.md) are the fallback.

---

## Learning order

```
W1  Day 1–2   01-python-core.md §1–§5     (types, strings, collections)      → 03-dsa/00-big-o.md, 01-arrays-strings.md in Python
    Day 3–4   01-python-core.md §6–§10    (functions, comprehensions, recursion, exceptions, classes)
    Day 5     python-for-java-devs.md; exercises.md W1 block; explain 5 concepts out loud
W2  Day 1–2   02-interview-toolkit.md     (Counter/defaultdict/deque/heapq/bisect)  → 03-dsa/02-hashing.md
    Day 3     02-interview-toolkit.md     (itertools/functools/dataclasses/typing/custom sorting)
    Day 4     03-pitfalls-and-complexity.md + its 25-question self-test
    Day 5     exercises.md W2 block; interview-questions.md first 25
W3  Day 1–2   04-testing-and-scripting.md (pytest, type hints, venv/pyproject)
    Day 3–4   04-testing-and-scripting.md (argparse/pathlib/csv/json/httpx/psycopg/Decimal/logging/concurrent.futures)
    Day 5     exercises.md W3 block: build one small tested CLI tool end-to-end (throwaway)
W4+           Cheatsheet open during every DSA session; re-read §6 pitfalls before each checkpoint and OA sim
W7–8, W12, W18, W22–23   Project Python components (see 04-testing-and-scripting.md §12)
```

Follow the philosophy: **Learn → Build → Break → Debug → Explain → Review → Build again.** Every file has "Break it" experiments — run them, predict the output first, then check.

---

## How Python is tested at checkpoints

| Checkpoint | Python expectation |
|---|---|
| [CP-4](../checkpoints/checkpoint-04.md) | 3 Easies in ≤ 15 min each, clean Python (no O(n) list membership, `deque` for queues, `Counter` where it fits); explain the complexity of every built-in you used; 5 pitfall questions from [`03-pitfalls-and-complexity.md`](./03-pitfalls-and-complexity.md) |
| [CP-8](../checkpoints/checkpoint-08.md) | Easies reliably + first Mediums; FlowGrid `tools/` generator and load harness exist with `pytest` tests and type hints; 10 questions from [`interview-questions.md`](./interview-questions.md) |
| [CP-12](../checkpoints/checkpoint-12.md) | LedgerX independent reconciliation verifier (psycopg + Decimal) passes against the real DB and catches an injected drift; recursion/backtracking in Python without hints |
| [CP-16](../checkpoints/checkpoint-16.md) | Standard Mediums ≤ 30 min in Python including graph BFS/DFS, heaps, DP memo→tabulation; state every template's invariant |
| [CP-20](../checkpoints/checkpoint-20.md) | ForgeCI Python tooling shipped and used by a benchmark; OA simulations passing in Python |
| [CP-24](../checkpoints/checkpoint-24.md) | Full OA under time limits in Python; FlagForge rollout simulator + Python SDK contract tests; answer any of the 50+ interview questions |

Related tracker: [`trackers/technology-tracker.md`](../trackers/technology-tracker.md) (Python row) and [`17-resume-tech-defense/python.md`](../17-resume-tech-defense/python.md).

---

## Resources (stable sources only)

| Resource | Use it for |
|---|---|
| [The Python Tutorial](https://docs.python.org/3/tutorial/) (docs.python.org) | W1 core: chapters 3–9 (informal intro, control flow, data structures, modules, I/O, errors, classes) |
| [The Python Standard Library reference](https://docs.python.org/3/library/) | `collections`, `heapq`, `bisect`, `itertools`, `functools`, `dataclasses`, `typing`, `pathlib`, `argparse`, `csv`, `json`, `subprocess`, `logging`, `concurrent.futures`, `decimal` — read the module page once, then keep it bookmarked |
| [Time complexity wiki (python.org)](https://wiki.python.org/moin/TimeComplexity) | The official Big-O table for list/dict/set/deque |
| NeetCode (Pro) — Python solutions | Every roadmap problem has a Python reference; watch *after* attempting |
| [pytest documentation](https://docs.pytest.org/) | W3: getting started, fixtures, parametrize, `tmp_path`, `monkeypatch` |
| [mypy docs](https://mypy.readthedocs.io/) · [pyright docs](https://microsoft.github.io/pyright/) | Type checking basics |
| *Python Cookbook*, 3rd ed. (Beazley & Jones) | Ch. 1 (data structures & algorithms), Ch. 4 (iterators/generators), Ch. 7 (functions) |
| *Fluent Python*, 2nd ed. (Ramalho) | Ch. 2 (sequences), Ch. 3 (dicts/sets), Ch. 6 (object references, mutability, copies), Ch. 7 (functions as objects), Ch. 17 (iterators/generators) |
| [psycopg 3 docs](https://www.psycopg.org/psycopg3/docs/) · [httpx docs](https://www.python-httpx.org/) | Project tooling |

Do not binge-read. Read a section, close it, write the code from a blank file, break it, explain it.
