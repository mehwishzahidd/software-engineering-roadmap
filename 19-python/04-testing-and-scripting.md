# 04 · Testing, type checking and scripting (Week 3)

> Goal: write the kind of Python each flagship project ships in `tools/` — small, typed, tested command-line tools that talk to HTTP APIs and Postgres.
> Time: 6–8 h in Week 3. Applied in FlowGrid W7–8, LedgerX W12, ForgeCI W18, FlagForge W22–23.
> Rules from [`ROADMAP.md`](../ROADMAP.md) §8: every tool has a README, `pytest` tests, type hints, a `pyproject.toml`/`requirements.txt`, and is actually used by a test, benchmark or failure exercise.

**Contents:** [Environment](#1-environment-venv-pip-pyprojecttoml) · [pytest](#2-pytest) · [Type hints + mypy/pyright](#3-type-hints-mypy-pyright) · [argparse](#4-argparse) · [pathlib](#5-pathlib) · [csv/json](#6-csv-and-json) · [subprocess](#7-subprocess) · [HTTP](#8-http-requests--httpx) · [Postgres](#9-postgres-with-psycopg-3) · [Decimal](#10-decimaldecimal-for-money) · [logging](#11-logging) · [concurrent.futures](#12-concurrentfutures-a-simple-load-harness) · [tools/ package layout](#13-structuring-a-tools-package) · [Project components](#14-the-four-projects-python-components) · [Checklist](#15-mastery-checklist)

---

## 1. Environment: venv, pip, `pyproject.toml`

```bash
cd flowgrid/tools
python3 -m venv .venv && source .venv/bin/activate
python -m pip install --upgrade pip
pip install -e ".[dev]"          # editable install of this package + dev extras (pytest, mypy)
pip freeze > requirements.txt    # pin for reproducibility (or rely on pyproject alone for tools)
deactivate
```

Minimal `pyproject.toml` (PEP 621, works with pip ≥ 21.3):

```toml
[build-system]
requires = ["setuptools>=68"]
build-backend = "setuptools.build_meta"

[project]
name = "flowgrid-tools"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = ["httpx>=0.27", "psycopg[binary]>=3.1"]

[project.optional-dependencies]
dev = ["pytest>=8", "mypy>=1.10", "pyright>=1.1"]

[project.scripts]
flowgrid-gen = "flowgrid_tools.cli:main"      # `pip install -e .` creates the `flowgrid-gen` command

[tool.pytest.ini_options]
testpaths = ["tests"]
addopts = "-q"

[tool.mypy]
python_version = "3.12"
strict = true
```

Add `.venv/` to `.gitignore`. Use `python -m pytest` (not bare `pytest`) if imports fail — it puts the cwd on `sys.path`.

---

## 2. pytest

```python
# tests/test_orders.py
import pytest
from flowgrid_tools.generator import make_order, OrderSpec

def test_make_order_has_positive_quantities():
    order = make_order(OrderSpec(max_lines=3), seed=1)
    assert order.lines and all(line.qty > 0 for line in order.lines)

@pytest.mark.parametrize("seed", [0, 1, 42])
def test_generator_is_deterministic_for_seed(seed):
    assert make_order(OrderSpec(), seed=seed) == make_order(OrderSpec(), seed=seed)

@pytest.mark.parametrize("qty, expected", [(0, False), (1, True), (-3, False)])
def test_validate_qty(qty, expected):
    assert (qty > 0) is expected

def test_raises_on_empty_catalog():
    with pytest.raises(ValueError, match="catalog is empty"):
        make_order(OrderSpec(catalog=[]))

# Fixtures: setup shared across tests, torn down automatically
@pytest.fixture
def catalog():
    return ["SKU-1", "SKU-2", "SKU-3"]

def test_uses_fixture(catalog):
    assert len(catalog) == 3

@pytest.fixture
def db_url(monkeypatch):                                   # monkeypatch: env vars, attributes, dict entries — auto-undone
    monkeypatch.setenv("LEDGERX_DB_URL", "postgresql://test@localhost/test")
    return "postgresql://test@localhost/test"

def test_writes_report(tmp_path):                          # tmp_path: a fresh pathlib.Path per test
    out = tmp_path / "report.json"
    out.write_text('{"ok": true}')
    assert out.read_text() == '{"ok": true}'

@pytest.fixture(scope="session")
def api_base_url():                                        # created once per test session
    return "http://localhost:8080"

@pytest.mark.skipif(True, reason="needs a running server")   # or a custom marker + `-m integration`
def test_integration_placeholder(): ...
```

Essentials: `assert` with plain expressions (pytest rewrites them to show values); `pytest -x` stop on first failure; `-k name` select; `-vv` verbose; `--lf` last failed; `conftest.py` for shared fixtures; `pytest.approx(0.3)` for floats; `capsys` to capture stdout; `caplog` for logs. Test names describe behaviour: `test_reservation_never_goes_negative`.

Structure: `tests/` mirrors the package; unit tests need no network or DB; integration tests are marked and skipped unless an env var says the stack is up.

---

## 3. Type hints, `mypy`, `pyright`

```python
from dataclasses import dataclass
from typing import Iterator, Protocol

@dataclass(frozen=True)
class OrderLine:
    sku: str
    qty: int

@dataclass
class Order:
    id: str
    warehouse: str
    lines: list[OrderLine]

def total_units(order: Order) -> int:
    return sum(line.qty for line in order.lines)

def iter_orders(path: str) -> Iterator[Order]: ...       # generator return type

class Client(Protocol):                                   # structural interface: anything with .post works
    def post(self, path: str, json: dict) -> int: ...

def submit(client: Client, orders: list[Order]) -> dict[str, int]:
    return {o.id: client.post("/orders", {"id": o.id}) for o in orders}

Money = int          # cents — a simple alias; or `type Money = int` (3.12 syntax)
def find(xs: list[int], t: int) -> int | None: ...
```

```bash
mypy src/           # or `pyright src/` — both read pyproject; strict mode catches missing returns, Optional misuse
```

Rules: annotate every public function signature; use `X | None` and check it before use (`if x is None: raise`); avoid `Any`; `TypedDict` for JSON shapes, `dataclass` for records; run the checker in CI along with pytest (`.github/workflows/tools.yml`: `pip install -e ".[dev]" && mypy . && pytest`). See [`13-cicd/github-actions.md`](../13-cicd/github-actions.md).

---

## 4. `argparse`

```python
import argparse, sys

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="flowgrid-gen", description="Generate synthetic inventory and orders")
    p.add_argument("--warehouses", type=int, default=3)
    p.add_argument("--skus", type=int, default=200)
    p.add_argument("--orders", type=int, default=1000)
    p.add_argument("--seed", type=int, default=0)
    p.add_argument("--out", type=str, default="-", help="file path or - for stdout")
    p.add_argument("--mode", choices=["api", "sql"], default="api")
    p.add_argument("-v", "--verbose", action="store_true")
    sub = p.add_subparsers(dest="command", required=True)
    gen = sub.add_parser("generate"); gen.add_argument("--kind", choices=["inventory", "orders"], required=True)
    sub.add_parser("load")
    return p

def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)     # argv=None → sys.argv[1:]; pass a list in tests
    ...
    return 0                                    # exit code

if __name__ == "__main__":
    sys.exit(main())
```

Testable because `main(argv)` takes a list: `assert main(["generate", "--kind", "orders", "--orders", "5"]) == 0`.

---

## 5. `pathlib`

```python
from pathlib import Path
root = Path(__file__).resolve().parent.parent      # tools/
data = root / "data" / "orders.json"               # `/` joins
data.parent.mkdir(parents=True, exist_ok=True)
data.write_text('[]', encoding="utf-8"); text = data.read_text(encoding="utf-8")
data.exists(), data.is_file(), data.suffix, data.stem, data.name          # '.json' 'orders' 'orders.json'
for p in root.glob("**/*.log"): ...                # recursive glob → the ForgeCI log analyzer walks build logs
with data.open("w", encoding="utf-8") as f: f.write("...")
Path.cwd(), Path.home(), Path("/tmp") / "x"
```

Prefer `Path` over `os.path` string juggling. Always pass `encoding="utf-8"`.

---

## 6. `csv` and `json`

```python
import csv, json
from dataclasses import asdict

with open("orders.csv", newline="", encoding="utf-8") as f:
    for row in csv.DictReader(f):                  # dict per row, keys from the header; values are STRINGS
        qty = int(row["qty"])

with open("out.csv", "w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=["id", "sku", "qty"])
    w.writeheader(); w.writerows(asdict(line) for line in lines)

json.dumps({"a": 1}, indent=2, sort_keys=True); json.loads('{"a": 1}')
with open("cfg.json", encoding="utf-8") as f: cfg = json.load(f)
json.dump(payload, f, default=str)                 # default=str serializes Decimal/datetime crudely; better: convert explicitly
```

JSON has no tuples/sets/Decimal/datetime: convert to str/list first. Read big CSVs row by row (the reader is lazy) — never `list(reader)` a multi-GB file.

---

## 7. `subprocess`

```python
import subprocess
r = subprocess.run(["git", "init", str(repo_dir)], capture_output=True, text=True, check=True)
r.stdout, r.stderr, r.returncode
subprocess.run(["git", "-C", str(repo_dir), "commit", "-m", "init"], check=True, capture_output=True, text=True)
try:
    subprocess.run(["docker", "ps"], check=True, timeout=10)
except subprocess.CalledProcessError as e: print(e.returncode, e.stderr)
except subprocess.TimeoutExpired: ...
```

Always a list of args (no `shell=True`), always `check=True` unless you handle the code, always `timeout`. ForgeCI's test-repo generator drives `git` this way.

---

## 8. HTTP: `requests` / `httpx`

`httpx` is the modern choice (same API as `requests`, plus timeouts by default, HTTP/2, async). Either is fine; pick one per tool.

```python
import httpx
client = httpx.Client(base_url="http://localhost:8080", timeout=5.0,
                      headers={"Authorization": f"Bearer {token}"})
r = client.post("/api/orders", json=payload, headers={"Idempotency-Key": key})
r.status_code, r.json(), r.headers.get("ETag"), r.elapsed.total_seconds()
r.raise_for_status()                          # raises httpx.HTTPStatusError on 4xx/5xx
try:
    client.get("/health")
except httpx.TimeoutException: ...
except httpx.ConnectError: ...
r = client.get("/sdk/config", headers={"If-None-Match": etag}); r.status_code == 304    # FlagForge polling
client.close()                                # or `with httpx.Client(...) as client:`
```

Testing without a server: inject the client (or a `Protocol`) and pass a fake in tests; or use `httpx.MockTransport`. Never hit the real API from unit tests.

---

## 9. Postgres with `psycopg` 3

```python
import psycopg
from decimal import Decimal

with psycopg.connect("postgresql://ledgerx:secret@localhost:5432/ledgerx") as conn:     # or conninfo from env
    with conn.cursor() as cur:
        cur.execute("SELECT journal_txn_id, SUM(amount) FROM ledger_entry GROUP BY journal_txn_id HAVING SUM(amount) <> 0")
        broken = cur.fetchall()                     # list of tuples; NUMERIC columns arrive as Decimal
        cur.execute("SELECT balance FROM account WHERE id = %s", (account_id,))   # parameters, NEVER f-strings
        row = cur.fetchone()
        for rec in cur.execute("SELECT id, amount FROM ledger_entry"):            # iterate → server-side streaming with cur.itersize
            ...
    conn.commit()                                   # read-only checks don't need it; the `with` closes the connection
```

`psycopg.rows.dict_row` (`conn.cursor(row_factory=dict_row)`) returns dicts. Use `sql.SQL`/`sql.Identifier` for dynamic identifiers. The verifier opens a **read-only** role (`GRANT SELECT`) — it must not be able to fix what it finds. Connection string from an env var (`LEDGERX_DB_URL`); tests use a Testcontainers-started or Compose Postgres, marked `integration`.

See [`04-sql-databases/08-jdbc-orm.md`](../04-sql-databases/08-jdbc-orm.md) for the Java side and [`18-projects/ledgerx/milestones.md`](../18-projects/ledgerx/milestones.md) M4.

---

## 10. `decimal.Decimal` for money

```python
from decimal import Decimal, ROUND_HALF_UP, getcontext
Decimal("0.1") + Decimal("0.2") == Decimal("0.3")       # True — construct from STRINGS, never from floats
Decimal(0.1)                                             # Decimal('0.1000000000000000055511151231257827...') — the float's real value
amount = Decimal("19.999").quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)   # Decimal('20.00')
sum((Decimal(r[1]) for r in rows), Decimal("0"))         # Decimal start value keeps the type
getcontext().prec                                         # 28 significant digits by default
```

Ledger invariant checks compare `Decimal`s exactly (`== Decimal("0")`); never `float(x) == 0`. Java side: `BigDecimal` with the same scale ([`01-java/`](../01-java/) and LedgerX M1).

---

## 11. `logging`

```python
import logging, sys
logging.basicConfig(level=logging.INFO, stream=sys.stderr,
                    format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("flowgrid.loadgen")
log.info("starting run: %d orders, concurrency=%d", n, workers)     # lazy %-formatting; not f-strings
log.warning("retrying %s after %s", url, exc)
log.exception("request failed")                                     # inside except: includes the traceback
log.debug("payload=%r", payload)                                    # only shown with --verbose → level DEBUG
```

Results go to **stdout** (JSON/CSV a script can parse); diagnostics go to **stderr** via logging. `-v` sets `DEBUG`. Never `print` for diagnostics inside library code.

---

## 12. `concurrent.futures`: a simple load harness

Threads are the right tool for an HTTP load generator: the work is I/O-bound, so the GIL is released while waiting on sockets.

```python
import statistics, time
from concurrent.futures import ThreadPoolExecutor, as_completed
import httpx

def one_order(client: httpx.Client, payload: dict) -> tuple[int, float]:
    t = time.perf_counter()
    r = client.post("/api/orders", json=payload, headers={"Idempotency-Key": payload["id"]})
    return r.status_code, (time.perf_counter() - t) * 1000

def run(payloads: list[dict], workers: int, base_url: str) -> dict:
    latencies, statuses = [], {}
    with httpx.Client(base_url=base_url, timeout=10) as client, ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(one_order, client, p) for p in payloads]
        for fut in as_completed(futures):
            code, ms = fut.result()               # re-raises exceptions from the worker
            latencies.append(ms); statuses[code] = statuses.get(code, 0) + 1
    latencies.sort()
    return {"n": len(latencies), "p50_ms": statistics.median(latencies),
            "p95_ms": latencies[int(0.95 * len(latencies)) - 1], "p99_ms": latencies[int(0.99 * len(latencies)) - 1],
            "statuses": statuses}
```

Record: environment, server config, dataset, concurrency, duration, warm-up, percentiles — see [`18-projects/templates/benchmark-report.md`](../18-projects/templates/benchmark-report.md). k6 stays the primary benchmarking tool ([`18-projects/flowgrid/docs-and-resume.md`](../18-projects/flowgrid/docs-and-resume.md)); the Python harness is for contention experiments (many clients hitting the *same* SKU) and failure exercises. `ProcessPoolExecutor` for CPU-bound analysis (ForgeCI log parsing over thousands of files). `asyncio` + `httpx.AsyncClient` is an alternative; threads are simpler to reason about at this scale.

---

## 13. Structuring a `tools/` package

```
<project-repo>/
└── tools/                          # separate Python package inside the Java project repo
    ├── pyproject.toml
    ├── README.md                   # what each tool does, how to run, how it is used by TESTING.md/PERFORMANCE.md
    ├── src/<project>_tools/
    │   ├── __init__.py
    │   ├── cli.py                  # argparse entry point(s); thin — parses args, calls library code
    │   ├── config.py               # env vars → dataclass (DB URL, API base URL, token)
    │   ├── models.py               # dataclasses for domain records
    │   ├── generator.py            # pure functions: (spec, seed) → records — no I/O
    │   ├── api_client.py           # httpx wrapper with retries/timeouts
    │   ├── db.py                   # psycopg queries
    │   └── report.py               # percentiles, JSON/CSV/Markdown output
    └── tests/
        ├── conftest.py             # fixtures: sample records, fake client, tmp dirs
        ├── test_generator.py       # unit: determinism, distributions, validation
        ├── test_report.py
        └── test_integration_api.py # marked `integration`; skipped unless FLOWGRID_URL is set
```

Principles: pure core (generators, validators, parsers) with no I/O → trivially testable; I/O at the edges (`cli.py`, `api_client.py`, `db.py`); determinism via `random.Random(seed)` instances, never the global `random`; every tool documented in the project's `TESTING.md` or `PERFORMANCE.md` with the exact command used.

---

## 14. The four projects' Python components

Spec-level guidance only — you implement them (project rule in [`ROADMAP.md`](../ROADMAP.md) §1). Full requirements and acceptance criteria live in each project's `milestones.md`.

### 14.1 FlowGrid — order generator + load harness (M4–M5, W7–8) → [`18-projects/flowgrid/milestones.md`](../18-projects/flowgrid/milestones.md)

| Piece | Pattern | Skeleton |
|---|---|---|
| Synthetic inventory generator | pure function `(spec, seed) → list[Warehouse], list[Sku], list[InventoryLevel]`; realistic SKU codes, zones, quantity distributions (`random.Random(seed)`, `choices` with weights) | `generator.py`, `models.py` |
| Order generator | order mix (single-line 60 %, multi-line 35 %, bulk 5 %), hot SKUs (Zipf-like: 20 % of SKUs get 80 % of demand) to provoke contention, optional duplicate `Idempotency-Key`s to test dedupe | `generator.py` |
| Writer | `--mode api` (httpx, JWT from env) or `--mode sql` (psycopg `COPY`/batched `INSERT`) | `api_client.py`, `db.py` |
| Load harness | `ThreadPoolExecutor` §12; scenario flags `--concurrency`, `--duration`, `--same-sku` (N clients reserve 1 unit of one SKU); reports p50/p95/p99, status counts, **reservation contention** (409/conflict rate, exactly-one-success check) | `load.py`, `report.py` |
| Tests | determinism per seed; distribution sanity (hot-SKU share within tolerance); schema of generated JSON matches the API's DTOs (compare against `openapi.json` fields); harness math on a fake client | `tests/` |

Used by: FlowGrid M2 concurrency failure exercise, M5 benchmark protocol.

### 14.2 LedgerX — independent reconciliation verifier (M4, W12) → [`18-projects/ledgerx/milestones.md`](../18-projects/ledgerx/milestones.md)

| Piece | Pattern | Skeleton |
|---|---|---|
| Verifier | psycopg read-only; checks: every `journal_txn` sums to `Decimal("0")`; `account.balance == SUM(ledger_entry.amount)` per account; no `ledger_entry` without a journal; state-machine consistency (completed txns have entries, failed have none); prints a report and exits non-zero on drift | `verifier.py`, `checks.py` (each check = function returning `list[Violation]`) |
| Transaction-data generator | deposits/withdrawals/transfers mix, deliberate edge cases (zero amounts, insufficient funds, duplicate keys), driven through the API with idempotency keys | `generator.py`, `api_client.py` |
| Consistency checker for failure exercises | run verifier before/after a crash-injection run; diff results; assert idempotent retry did not double-post | `crash_check.py` |
| Tests | each check against a tiny fixture DB (Compose Postgres, `integration` marker) with an injected drift row; `Decimal` handling; report formatting | `tests/` |

The point: it does not import or trust the Java code — it proves the invariants from the database alone.

### 14.3 ForgeCI — test-repo generator, webhook load simulator, log analysis (M5, W18) → [`18-projects/forgeci/milestones.md`](../18-projects/forgeci/milestones.md)

| Piece | Pattern | Skeleton |
|---|---|---|
| Test-repository generator | `subprocess` + `git`; templates of `.forgeci.yml` (passing, failing exit code, slow `sleep`, timeout, malformed YAML, DAG with `needs:`); writes N repos under `tmp_path`/a target dir; optionally pushes to GitHub test org | `repos.py`, `templates/` |
| Webhook load simulator | builds GitHub-shaped `push` payloads, signs with HMAC-SHA256 (`hmac`, `hashlib`) using the shared secret, fires at `--rate` with a thread pool; polls build status; measures **queue wait** (created → started) and completion time; replays the same `X-GitHub-Delivery` to test dedupe | `webhooks.py`, `load.py` |
| Log/result analysis | walks persisted logs/results (DB via psycopg or exported JSON), classifies failures (app failure vs infra failure vs timeout vs cancelled), jobs/min, per-step duration stats, Markdown table output for PERFORMANCE.md | `analyze.py`, `report.py` |
| Tests | payload signature matches a known vector; YAML templates parse; analyzer on fixture logs; generator creates valid repos (`git log` works) | `tests/` |

### 14.4 FlagForge — rollout simulator, config validator, Python SDK (M3–M4, W22–23) → [`18-projects/flagforge/milestones.md`](../18-projects/flagforge/milestones.md)

| Piece | Pattern | Skeleton |
|---|---|---|
| Rollout-distribution simulator | evaluates N synthetic users against a percentage rule locally **and** via the server eval endpoint; asserts observed share within tolerance of the target (e.g. 10 % ± 0.5 % for N = 100 000), **stickiness** (same user → same result across runs and across SDK vs server), bucket = `hash(flagKey:userKey) mod 10000` using the same hash the Java side uses | `simulate.py` |
| Configuration validator | loads an environment's rules (JSON export or API); lints: overlapping attribute rules, unreachable rules after a catch-all, invalid percentages (< 0, > 100, don't sum), duplicate priorities, missing defaults; exit code + report | `validate.py` |
| Python SDK / test client | `sdk-python/`: `FlagClient(sdk_key, base_url, poll_interval)` — polling with `ETag`/`If-None-Match`, in-memory snapshot, local evaluation with the same contract as the Java SDK, defaults when unknown, offline mode, stale-if-error; used for **cross-SDK contract tests** (same inputs → same outputs as the Java SDK, from a shared JSON test-vector file) | `sdk-python/src/flagforge/client.py`, `evaluator.py` |
| Tests | evaluator against the shared contract vectors; simulator statistics on a fixed seed; validator on crafted bad configs; client polling with `httpx.MockTransport` (200 → 304 → error → stale) | `tests/` |

---

## 15. Mastery checklist

- [ ] I can create a venv, a `pyproject.toml` with dev extras and a console script, and install it editable
- [ ] I write pytest tests with `parametrize`, fixtures, `tmp_path`, `monkeypatch`, `pytest.raises`, and an `integration` marker
- [ ] My tools pass `mypy --strict` (or pyright) and CI runs both checkers and pytest
- [ ] I write an `argparse` CLI whose `main(argv)` is testable, with subcommands and exit codes
- [ ] I use `pathlib`, `csv.DictReader/Writer`, `json` with explicit encoding and no giant in-memory loads
- [ ] I call external commands with `subprocess.run([...], check=True, timeout=...)`
- [ ] I talk to an HTTP API with `httpx` (timeouts, headers, `raise_for_status`, ETag) and can fake it in tests
- [ ] I read Postgres with psycopg 3 using parameters, `Decimal` for NUMERIC, and a read-only role
- [ ] I compare money with `Decimal` from strings, never floats
- [ ] I log to stderr with levels and write results to stdout
- [ ] I can write a thread-pool load harness that reports p50/p95/p99 and status counts, and explain why threads are fine here (GIL + I/O)
- [ ] I can describe each project's Python component: purpose, inputs, outputs, what its tests prove, and where it is referenced in the project docs
