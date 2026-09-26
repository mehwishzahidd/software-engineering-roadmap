# 🐍 19 — Python for a Java Developer

> **Scope:** a **≈5-hour sprint in Week 13** ([ROADMAP §5](../ROADMAP.md#5-week-by-week-master-table)),
> then **ongoing scripting reps** in later weeks (log parsing, CSV cleanup, API smoke tests,
> automation around P2–P4). Python is *not* a second DSA language in this roadmap — Java stays
> primary for interviews. The goal is to read and write idiomatic Python confidently, so the
> Python line on your résumé is defensible ([../17-resume-tech-defense/python.md](../17-resume-tech-defense/python.md))
> and you have a fast tool for glue work.

Files: this README (plan) · [python-for-java-devs.md](./python-for-java-devs.md) (the guide) · [exercises.md](./exercises.md) (15 exercises, several useful for your projects)

---

## Why Python at all?

| Use | Example in this roadmap |
|---|---|
| Scripting & automation | Clean a messy bank CSV before P1 import; rename/organize files |
| Tooling around services | Smoke-test TicketHold's API after deploy; seed test data |
| Data wrangling | Summarize P4 `check_results` exports from S3 |
| Interviews | Some OAs/companies allow or prefer Python; résumé questions about Python |
| Reading code | Many ops scripts, CI helpers, and ML/data code you'll meet are Python |

---

## Week 13 sprint plan (≈5 h)

| Block | Time | Content (sections of [python-for-java-devs.md](./python-for-java-devs.md)) | Output |
|---|---|---|---|
| 1 | 60 min | Setup (venv, pip), syntax mapping, types, control flow, functions | Exercises 1–3 |
| 2 | 60 min | Collections: list/dict/set/tuple, comprehensions, `collections`, `heapq` | Exercises 4–6 |
| 3 | 60 min | Classes, dataclasses, exceptions, typing | Exercises 7–8 |
| 4 | 60 min | Files, JSON, CSV, `pathlib`, `argparse`, `subprocess`, `requests` | Exercises 9–11 |
| 5 | 60 min | pytest; DSA-in-Python idioms; re-solve 3 Easy problems you already solved in Java | Exercises 12–13 |

Stretch (later weeks, 20–30 min each, whenever a project needs glue): exercises 14–15, and
replacing ad-hoc bash with Python scripts where logic gets complex.

---

## Setup

```bash
python3 --version                 # 3.11+ recommended (3.12 fine)
mkdir py-drills && cd py-drills
python3 -m venv .venv             # per-project virtual environment (like a local ~/.m2 per project)
source .venv/bin/activate         # Windows: .venv\Scripts\activate
python -m pip install --upgrade pip
pip install pytest requests
pip freeze > requirements.txt     # pin exact versions; commit this, .gitignore .venv/
```
Editor: VS Code with the Python extension, or IntelliJ with the Python plugin / PyCharm.

---

## Ongoing scripting reps (after Week 13)

| Week | Script | Exercise |
|---|---|---|
| 14 | TicketHold API smoke tester run after each change | [exercises.md #14](./exercises.md#14-tickethold-api-smoke-tester) |
| 19 | Log parser for the Docker Compose logs of P4 | [exercises.md #10](./exercises.md#10-log-parser) |
| 21 | CSV summary of `check_results` export | [exercises.md #11](./exercises.md#11-csv-cleaner-for-ledger-imports) adapted |
| 23+ | Solve 1 easy/week in Python to keep syntax warm (optional) | — |

---

## Resources

- **docs.python.org/3/tutorial/** — the official tutorial; chapters 3–9 cover everything here.
- **docs.python.org/3/library/** — `collections`, `heapq`, `itertools`, `pathlib`, `csv`, `json`, `argparse`, `subprocess`, `dataclasses`, `typing`.
- **docs.pytest.org** — Getting started.
- **requests.readthedocs.io** — Quickstart.
- Book (optional): *Fluent Python* (Ramalho) — deep idioms, read selectively.

---

## Mastery checklist

- [ ] Create a venv, install packages, pin them in `requirements.txt`
- [ ] Translate any Java snippet from [../01-java/](../01-java/README.md) Week 1–4 into idiomatic Python
- [ ] Use comprehensions, `dict.get`, `Counter`, `defaultdict`, `deque`, `heapq` without looking up
- [ ] Write a `@dataclass` with type hints and a custom exception
- [ ] Read/write CSV and JSON with the standard library
- [ ] Build a CLI with `argparse` + `pathlib`, and test it with pytest
- [ ] Call an HTTP API with `requests`, with timeouts and status checks
- [ ] Solve 3 LeetCode Easys in Python that you previously solved in Java
- [ ] Answer: "How does Python differ from Java?" in 60 seconds (typing, GIL, interpreted, indentation, duck typing)
- [ ] Complete ≥ 12 of the 15 exercises in [exercises.md](./exercises.md)
