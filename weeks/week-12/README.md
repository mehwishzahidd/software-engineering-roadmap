# Week 12 — LedgerX M4: reconciliation, failure injection, invariants · Checkpoint 12

[← Week 11](../week-11/) · [Roadmap](../../ROADMAP.md) · [Week 13 →](../week-13/)

**Phase 2 · LedgerX (Weeks 9–13)** — the week LedgerX becomes *provably* correct rather than *probably* correct. Two independent proofs: the Java reconciliation job inside the app, and a **Python verifier in `tools/` that reads Postgres directly and trusts nothing the Java code says** (LedgerX's required Python component, ROADMAP §8). Ends with **[Checkpoint 12](../../checkpoints/checkpoint-12.md)**.

| Block | Hours | Notes |
|---|---:|---|
| Project (LedgerX M4) | 26 | Reconciliation job, fault-injection hook, crash-between-steps tests, retry-after-crash tests, invariant suite, throughput measurement, **Python independent verifier + data generator** (~6 h of the 26) |
| Learning | 8 | Failure injection, invariant/property-style tests, crash-consistency reasoning (5 h) + **Python tooling patterns: `psycopg`, `decimal.Decimal`, `pytest` fixtures (3 h)** |
| DSA (Backtracking) | 7 | 8 new + reviews |
| Interview / review | 4 | Think-aloud, drill, **Checkpoint 12** (Sun) |

---

## 1. Main objective

By Sunday:

- a **reconciliation job** proves, on a schedule and on demand, that every journal txn sums to zero, every materialized balance equals `SUM(entries)`, every reversal nets its original, and no user wallet is negative — and it **flags drift** into a `reconciliation_run` / `reconciliation_finding` table rather than silently fixing it;
- a **fault-injection hook** lets tests crash the process (or throw) at named points inside the transfer/reversal/outbox flows;
- **crash-between-steps** and **retry-after-crash** tests show that after any injected failure, a client retry with the same idempotency key converges to exactly one effect;
- an **invariant suite** runs the whole property set after randomised workloads;
- a **Python independent verifier** (`tools/ledgerx_verify`) proves the same invariants by reading Postgres directly with `psycopg` and `Decimal` — no Java, no JPA, no trust in the app — and a **Python data generator** produces realistic transaction workloads; both are `pytest`-tested and are the consistency checkers used in the crash/retry failure exercises;
- a **throughput measurement** (transfers/s, p50/p95/p99) is recorded with methodology in `docs/PERFORMANCE.md`.

## 2. Prerequisites

- Tag `m3`. Outbox at-least-once test passing (crash tests extend it).
- You can state all LedgerX invariants from memory (write them Monday morning before reading §4.1 — then compare).
- Recursion/trees comfortable in Python: backtracking is recursion with undo (and `path.copy()` / `path[:]` when recording).
- Python scripting basics from Weeks 1–3 ([`19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md)): `argparse`, `pytest`, type hints, virtualenv. This week you write a real tool with a database.

## 3. Learning topics

| Topic | Subtopics | File |
|---|---|---|
| Failure injection | named injection points, `FaultInjector` bean active only in test profile, throw vs `System.exit`/`kill -9`, Toxiproxy-style network faults (concept) | [`09-testing/spring-testing.md`](../../09-testing/spring-testing.md), [`09-testing/testcontainers.md`](../../09-testing/testcontainers.md) |
| Invariant / property-style tests | invariants as executable SQL, randomised workloads with seeds, shrinking (concept), jqwik awareness | [`09-testing/junit5.md`](../../09-testing/junit5.md), [`14-cs-fundamentals/software-engineering-practices.md`](../../14-cs-fundamentals/software-engineering-practices.md) |
| Crash consistency | what survives a crash (committed WAL), what does not (in-memory state, in-flight HTTP), idempotent recovery, poison rows | [`14-cs-fundamentals/database-internals.md`](../../14-cs-fundamentals/database-internals.md), [`04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md) |
| Reconciliation design | run/finding tables, `REPEATABLE READ` snapshot, drift severity, alerting hooks, why never auto-fix | [`18-projects/ledgerx/README.md`](../../18-projects/ledgerx/README.md), [`04-sql-databases/03-advanced-queries.md`](../../04-sql-databases/03-advanced-queries.md) |
| Measurement methodology | warm-up, fixed duration, percentiles not averages, environment recorded, k6 or JMH choice | [`18-projects/templates/benchmark-report.md`](../../18-projects/templates/benchmark-report.md), [`18-projects/ledgerx/docs-and-resume.md`](../../18-projects/ledgerx/docs-and-resume.md) |
| **Python tooling patterns** | `psycopg` 3 (connection, server-side cursors, `Decimal` mapping, `REPEATABLE READ` via `conn.transaction()`/`isolation_level`), `decimal.Decimal` vs `float`, `dataclasses`, `argparse` CLIs, `pytest` fixtures with a real database (Docker Postgres or the app's Compose), `pyproject.toml`, type hints + `mypy`-clean | [`19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md), [`19-python/03-pitfalls-and-complexity.md`](../../19-python/03-pitfalls-and-complexity.md), [`19-python/python-for-java-devs.md`](../../19-python/python-for-java-devs.md), [`17-resume-tech-defense/python.md`](../../17-resume-tech-defense/python.md) |

## 4. Concepts to learn

### 4.1 The LedgerX invariant set (write these as SQL first)

| # | Invariant | SQL sketch |
|---|---|---|
| I1 | Every journal txn sums to zero | `SELECT journal_txn_id FROM ledger_entry GROUP BY 1 HAVING SUM(amount) <> 0` → empty |
| I2 | Materialized balance == derived | `SELECT b.account_id FROM account_balance b LEFT JOIN (SELECT account_id, SUM(amount) s FROM ledger_entry GROUP BY 1) e USING (account_id) WHERE b.balance <> COALESCE(e.s, 0)` → empty |
| I3 | User wallets never negative | `SELECT id FROM account_balance b JOIN account a USING(account_id) WHERE a.kind = 'USER' AND b.balance < 0` → empty |
| I4 | A completed txn has ≥ 2 entries; a failed txn has 0 | join + count |
| I5 | Every reversal nets its original to zero | entries of T ∪ R sum to zero per account |
| I6 | No `pending` txn older than N minutes (stuck) | `WHERE state='PENDING' AND created_at < now() - interval '5 min'` → empty (finding, not failure) |
| I7 | Every `COMPLETED` idempotency record references an existing txn | anti-join |
| I8 | Outbox has no unpublished rows older than N minutes | finding |

Each invariant becomes (a) a JUnit assertion helper, (b) a reconciliation check with a `severity` (`CRITICAL` for I1–I5, `WARN` for I6/I8).

- **Interview angle:** "How do you know your ledger is correct?" → I can enumerate the invariants, they run after every test workload and every night in prod, and drift is recorded, never patched.
- **Where LedgerX uses this:** the invariant suite (this week), reconciliation job (this week), Checkpoint 12, the deep-dive rehearsal in Week 13.

### 4.2 Reconciliation job

Runs under `REPEATABLE READ` (one snapshot for all checks — otherwise I2 can false-positive during concurrent writes), records a `reconciliation_run(id, started_at, finished_at, snapshot_note, status)` and `reconciliation_finding(run_id, invariant, entity_id, expected, actual, severity)`. Exposed as `POST /admin/reconcile` (ADMIN role) and `@Scheduled(cron = "0 */15 * * * *")`. On `CRITICAL` findings: log at ERROR with correlation id, emit an outbox event `ReconciliationDriftDetected`. **Never auto-correct.**

```java
@Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
public ReconciliationReport check() { ... }   // runs all invariant queries in one snapshot
```

Note: the run/finding *writes* happen in a second, short transaction after the read-only snapshot transaction.

- **Interview angle:** "Why REPEATABLE READ here?" → all checks must see the same committed state; a `SUM` and a balance read from different snapshots disagree legitimately mid-write.

### 4.3 Fault-injection hook

```java
public interface FaultInjector { void maybeFail(String point); }

@Component @Profile("!chaos")
class NoopFaultInjector implements FaultInjector { public void maybeFail(String p) {} }

@Component @Profile("chaos")
class ScriptedFaultInjector implements FaultInjector {
    private final Set<String> armed = ConcurrentHashMap.newKeySet();
    public void arm(String point) { armed.add(point); }
    public void maybeFail(String point) {
        if (armed.remove(point)) throw new InjectedFailure(point);   // fires once
    }
}
```

Injection points (name them as constants): `transfer.after-lock`, `transfer.after-entries`, `transfer.before-complete`, `idempotency.after-claim`, `outbox.after-publish`, `reversal.after-link`. A thrown `InjectedFailure` rolls back the transaction — that simulates "crash before commit". For "crash *after* commit but before the HTTP response" (the interesting one for idempotency), inject *after* the transactional method returns, in the controller/filter layer. For a **real** process kill, run the app in Testcontainers/Compose and `docker kill` it between steps in a scripted test (do this for at least two points).

- **Where LedgerX uses this:** every M4 crash test; ForgeCI reuses the interface for worker crashes.

### 4.4 Retry-after-crash tests — the property

For every injection point P: arm P → send request with key K → observe failure (5xx or dropped connection) → disarm → **retry with K** → assert: exactly one effect (one completed txn or zero), balances consistent, invariants hold, response is final. Cases: crash before claim (retry executes normally), crash after claim before txn (retry finds `IN_PROGRESS` — what now? Decide: TTL on `IN_PROGRESS` or *ownership token* so the same client can resume; document), crash after commit before response (retry replays stored response — requires the response to be stored *in the same transaction* as the txn; check yours!).

- **Interview angle:** the last case is the classic: "if you store the idempotency response after the commit in a separate transaction, a crash in between makes the retry re-execute." Say it with your test name.

### 4.5 Randomised invariant workload

Seeded `Random`, N wallets, 2,000 operations chosen from {deposit, withdraw, transfer, reverse, refund, request+accept}, run on 8 threads, then assert I1–I8. Print the seed on failure so it can be replayed. This is property-based testing done by hand; mention jqwik as the library you would reach for next.

### 4.6 Throughput measurement (methodology first)

Record in `docs/PERFORMANCE.md` using [`templates/benchmark-report.md`](../../18-projects/templates/benchmark-report.md): machine, JVM flags, Postgres config (Docker, default `shared_buffers`?), dataset (wallets, hot vs uniform distribution), tool (k6 script with `Idempotency-Key` per iteration), warm-up 30 s, run 2 min, results p50/p95/p99, error rate, and the *same* run under `optimistic` for comparison. Two distributions: uniform random pairs vs 20 % of traffic on one hot wallet. Expected shape: hot wallet serialises on the lock — that is the *correct* behaviour and a great interview story.

### 4.7 The independent Python verifier — why a second implementation

The Java reconciliation job shares code, entities and assumptions with the thing it checks. If `Money` rounds wrong, both the writer and the checker round wrong. An **independent verifier in a different language, using raw SQL and `Decimal`,** breaks that shared-assumption loop. It lives in `ledgerx/tools/` as a small package:

```
tools/
  pyproject.toml            # deps: psycopg[binary]>=3, pytest; python>=3.11
  README.md                 # what it proves, how to run, exit codes
  ledgerx_verify/
    __init__.py
    db.py                   # connect(dsn) -> psycopg.Connection; one REPEATABLE READ snapshot
    invariants.py           # I1..I8 as functions returning list[Finding]
    cli.py                  # python -m ledgerx_verify --dsn ... [--json] ; exit 1 on CRITICAL
  ledgerx_gen/
    generator.py            # deterministic workload: seed -> list[Op]; runs via HTTP or SQL
    cli.py
  tests/
    conftest.py             # fixture: Postgres via docker compose / testcontainers-python, seeded schema
    test_invariants.py      # seeded drift -> finding; clean db -> no findings
    test_generator.py       # same seed -> same ops; ops sum to zero per txn
```

Core rules: **never `float`** — `psycopg` returns `NUMERIC` as `Decimal` by default; keep it that way and compare with `Decimal("0")`; all invariant queries run inside **one** `REPEATABLE READ` transaction; the tool has **no dependency on the Java code** (schema is read from the live DB; column names are the contract — document them in `docs/DATABASE.md`); exit code `0` clean / `1` critical / `2` warnings so failure exercises and CI can gate on it.

```python
# ledgerx_verify/invariants.py (sketch of I1; the other seven follow the same shape)
from dataclasses import dataclass
from decimal import Decimal
import psycopg

@dataclass(frozen=True)
class Finding:
    invariant: str
    entity_id: str
    expected: Decimal
    actual: Decimal
    severity: str  # "CRITICAL" | "WARN"

def journal_sums_to_zero(conn: psycopg.Connection) -> list[Finding]:
    rows = conn.execute(
        "SELECT journal_txn_id, SUM(amount) FROM ledger_entry "
        "GROUP BY journal_txn_id HAVING SUM(amount) <> 0").fetchall()
    return [Finding("I1", str(txn), Decimal("0"), total, "CRITICAL") for txn, total in rows]
```

The generator: `seed → deterministic list of ops` (deposit/withdraw/transfer/reverse/refund with realistic amount and wallet distributions, including a configurable hot-wallet share), executed either through the HTTP API (with idempotency keys derived from `seed:index` so re-running is safe) or straight into SQL for bulk seeding. Same seed → same ops (tested).

- **Interview angle (Track B):** "How do you know your reconciliation is right?" → "A second, independent checker in Python with raw SQL and `Decimal` agrees with it — and I injected drift as a superuser to prove both detect it." Also an honest answer to "Do you use Python professionally?" → "Yes, for tooling around my Java services: here is a tested package with a real database fixture." See [`17-resume-tech-defense/python.md`](../../17-resume-tech-defense/python.md).
- **Where LedgerX uses this:** every crash/retry failure exercise ends with `python -m ledgerx_verify`; the generator seeds the k6 throughput dataset (§4.6) and the randomised invariant workload; CI runs `pytest tools/` and the verifier against the Testcontainers DB after the workload.

## 5. Resources

- PostgreSQL 16 docs: *Transaction Isolation*, *SET TRANSACTION* — https://www.postgresql.org/docs/16/sql-set-transaction.html
- Spring docs: `@Transactional` attributes (isolation, readOnly) — https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- Testcontainers: *Toxiproxy module* (network fault concept) — https://java.testcontainers.org/modules/toxiproxy/
- k6 docs: *Results output / summary* — https://grafana.com/docs/k6/latest/
- jqwik (property-based testing for JUnit 5) — https://jqwik.net/
- psycopg 3 documentation (transactions, `Decimal` adaptation) — https://www.psycopg.org/psycopg3/docs/
- Python `decimal` module — https://docs.python.org/3/library/decimal.html
- pytest documentation (fixtures, `conftest.py`) — https://docs.pytest.org/
- NeetCode 150 — Backtracking; problems in §7

## 6. Exercises and assignments

### 6.1 Warm-up exercises (~2.5 h)

1. **Invariants from memory (15 min).** Write I1–I8 before reading §4.1; diff against the table; add any you had that the table lacks.
2. **Snapshot false-positive demo (30 min, psql).** Under READ COMMITTED run I2 as two statements while a transfer commits between them → drift reported. Repeat under REPEATABLE READ → clean.
3. **Kill lab (30 min).** Run LedgerX in Compose; start a transfer with a `Thread.sleep` at `transfer.before-complete` (temporary); `docker kill ledgerx-app` during the sleep; restart; inspect `journal_txn`, `ledger_entry`, `idempotency_record`. Write what you found.
4. **Backtracking template (20 min).** Write the choose/explore/un-choose skeleton in Python on paper; derive Subsets and Permutations from it; mark where `path.copy()` is required.
5. **`Decimal` vs `float` lab (20 min, Python REPL).** `sum([0.1]*10) == 1.0` → `False`; `sum([Decimal("0.1")]*10) == Decimal("1")` → `True`; `psycopg` `conn.execute("SELECT 0.1::numeric").fetchone()` returns `Decimal`. Then show that `Decimal(0.1)` (from a float) is *not* `Decimal("0.1")` — the same lesson as `new BigDecimal(0.1)` in Week 9.
6. **psycopg snapshot lab (30 min).** With `conn.isolation_level = psycopg.IsolationLevel.REPEATABLE_READ`, read `SUM(amount)` twice with a Java-side transfer committed in between → same value; under READ COMMITTED → different. This is the verifier's core design decision, proven before you write it.

### 6.2 Assignment — LedgerX M4

**Acceptance criteria:**

- [ ] `reconciliation_run`/`reconciliation_finding` tables; job runs I1–I8 in one `REPEATABLE READ` snapshot; findings persisted; `POST /admin/reconcile` (ADMIN) and `@Scheduled` every 15 min; `CRITICAL` → ERROR log + outbox event; never auto-fix.
- [ ] `FaultInjector` with ≥ 6 named points; `chaos` profile only; production has the no-op bean (verified by a context test).
- [ ] Crash-between-steps tests for every point; two of them use a **real** process kill (`docker kill`) in a scripted integration test or documented manual runbook with captured output.
- [ ] Retry-after-crash tests: for every point, retry with same key converges to exactly one effect; the "crash after commit before response" case passes because the response is stored transactionally.
- [ ] Randomised invariant workload: 2,000 ops, 8 threads, seed printed, invariants hold; runs in CI (`mvn verify`, ≤ 2 min).
- [ ] Throughput report in `docs/PERFORMANCE.md` with full methodology, pessimistic vs optimistic, uniform vs hot-wallet; dataset seeded by the Python generator (seed recorded).
- [ ] **Python verifier** `tools/ledgerx_verify`: I1–I8 in one `REPEATABLE READ` snapshot, `Decimal` throughout, CLI with `--json` and exit codes, zero dependency on Java code; `pytest` suite with a real Postgres fixture (seeded drift → finding, clean → none); type-hinted; `tools/README.md` + `pyproject.toml`; listed in `docs/TESTING.md`.
- [ ] **Python generator** `tools/ledgerx_gen`: seeded deterministic workload (op mix, amounts, hot-wallet share), HTTP mode with derived idempotency keys and SQL bulk mode; `pytest` (same seed → same ops; per-txn zero-sum in SQL mode).
- [ ] Java reconciliation and Python verifier **agree** on the same seeded-drift database (both find the same findings) — a documented cross-check in `docs/TESTING.md`.
- [ ] CI job `python-tools`: `pytest tools/`, then run the verifier against the DB left by the randomised workload.

### 6.3 Break it

- Store the idempotency response in a separate transaction after commit (a common "cleaner" refactor). Arm `transfer.after-commit`; retry double-spends. Restore; keep the test as the guard.
- Change reconciliation to READ COMMITTED; run it during the randomised workload; watch false `CRITICAL` findings. Restore.
- Let reconciliation "fix" I2 by overwriting `account_balance`. Now inject a bug that mis-updates balances — reconciliation hides it, **but the Python verifier still reports I2 drift** (it never trusted the Java path). Delete the fix; make the point in `docs/DESIGN_DECISIONS.md`: independence is the value.
- Change the verifier to use `float(row[1])`; seed a wallet with 1,000,000 × `0.0001` entries; watch I2 report false drift of a few units in the last place. Restore `Decimal`.

### 6.4 Debug it

- The randomised workload fails 1 in 20 runs on I5 (reversal nets). Replay with the printed seed; bisect operations by truncating the op list (manual shrinking); you should find a refund-after-partial-refund over-refund path or a currency-mismatch on a system account. Fix; add the minimal reproducer as a named test.
- Reconciliation never finishes: `pg_stat_activity` shows it waiting on a lock — you accidentally used `FOR UPDATE` in a check query. Remove; add a `statement_timeout` for the job's session.
- The Python verifier reports I2 drift on every wallet by exactly the last transfer's amount, while the Java job is clean: the verifier opened its connection in autocommit and each query got its own snapshot. Wrap all checks in one `with conn.transaction():` at `REPEATABLE READ`; add the pytest that proves it (a concurrent writer thread during the check → no findings).

## 7. DSA — Backtracking (8 new problems, in Python)

**Language: Python (Track A).** Guide: [`03-dsa/14-backtracking.md`](../../03-dsa/14-backtracking.md) · Templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md). Template: choose → recurse → un-choose; `res.append(path[:])` when recording (aliasing pitfall); prune early; for Word Search mutate the grid cell to `'#'` and restore, or use a `visited` set of tuples.

| # | Problem | Difficulty | Time limit | Day |
|---|---|---|---|---|
| 78 | Subsets | Medium | 20 min | Mon |
| 46 | Permutations | Medium | 20 min | Mon |
| 39 | Combination Sum | Medium | 25 min | Tue |
| 90 | Subsets II | Medium | 25 min | Tue |
| 40 | Combination Sum II | Medium | 25 min | Wed |
| 79 | Word Search | Medium | 30 min | Thu |
| 131 | Palindrome Partitioning | Medium | 30 min | Thu |
| 17 | Letter Combinations of a Phone Number | Medium | 20 min | Fri |

Reviews due: Day-3 Week 11 Thu/Fri; Day-7 Week 11 heaps; Day-14 Week 10 BST/Tries; Day-30 Week 8 recursion (fitting — this is recursion's final form). Checkpoint 12's timed coding is in Python (Track A), a Medium from Weeks 9–12 patterns.

**Java rep (Fri, ≤ 30 min):** #78 Subsets in Java (`List<List<Integer>>`, `new ArrayList<>(path)` on record, `path.remove(path.size() - 1)` on un-choose) — the aliasing lesson is identical in both languages.

## 8. Project work — LedgerX M4 (Reconciliation + failure injection + invariants)

Spec: [`18-projects/ledgerx/README.md`](../../18-projects/ledgerx/README.md) · [`milestones.md`](../../18-projects/ledgerx/milestones.md) (M4) · [`failure-engineering.md`](../../18-projects/ledgerx/failure-engineering.md) — this week you run most of the file.

### 8.1 Task checklist

- [ ] **Mon:** milestone `M4 — Reconciliation, failure injection, invariants` + issues; invariant SQL I1–I8 as a `Invariants` test helper + reconciliation tables migration `V7__reconciliation.sql`.
- [ ] **Tue:** `ReconciliationService` (RR snapshot, findings, severity), admin endpoint, schedule, outbox event; tests with seeded drift (insert drift via superuser in test — the app role cannot).
- [ ] **Wed:** `FaultInjector` + points wired into transfer/reversal/outbox/idempotency; context test that prod profile has the no-op.
- [ ] **Thu:** crash-between-steps + retry-after-crash tests for all points; `docker kill` runbook/scripted test for two points.
- [ ] **Wed (PM) / Thu:** `tools/ledgerx_verify` (db, invariants I1–I8, CLI, exit codes) + pytest with real Postgres fixture; cross-check vs Java job on seeded drift.
- [ ] **Fri:** `tools/ledgerx_gen` (seeded workload, HTTP + SQL modes) + tests; randomised invariant workload in CI followed by the verifier; `python-tools` CI job.
- [ ] **Sat:** k6 throughput runs on generator-seeded data (uniform + hot, pessimistic + optimistic), `docs/PERFORMANCE.md`; failure write-ups (each ending with a verifier run); PR review; tag `m4`.

### 8.2 Acceptance summary

M4 done = §6.2 green; `docs/PERFORMANCE.md` and `docs/TESTING.md` (test taxonomy: unit / slice / IT / chaos / property / perf) written; tag `m4`. This is the **engineering-depth** milestone that makes v1.0 defensible.

### 8.3 Verification tests you write

| Test | Type | Proves |
|---|---|---|
| `InvariantsAfterWorkloadIT` | Testcontainers, 8 threads, seeded | I1–I8 hold after 2,000 random ops |
| `ReconciliationDetectsSeededDriftIT` | Testcontainers, superuser drift | each invariant produces a finding with the right severity |
| `ReconciliationSnapshotIsolationIT` | Testcontainers + concurrent writers | no false findings under load |
| `CrashBetweenStepsIT` (parameterised over points) | chaos profile | after injected failure, DB has no partial journal; invariants hold |
| `RetryAfterCrashIT` (parameterised) | chaos profile | retry with same key → exactly one effect |
| `ResponseStoredTransactionallyIT` | chaos | crash after commit before response → retry replays, no double spend |
| `ProdProfileHasNoopInjectorTest` | context | no `ScriptedFaultInjector` bean outside `chaos` |
| `OutboxCrashAfterPublishIT` | chaos | duplicate published, consumer dedupes |
| `tools/tests/test_invariants.py` | pytest + real Postgres | clean DB → no findings; superuser-seeded drift for each of I1–I8 → exactly the expected finding with severity; concurrent writer during check → no false findings |
| `tools/tests/test_decimal.py` | pytest | all amounts are `Decimal`; a `float` anywhere fails the type guard |
| `tools/tests/test_generator.py` | pytest | same seed → identical op list; SQL-mode output sums to zero per txn; hot-wallet share within tolerance |
| `tools/tests/test_cli.py` | pytest, subprocess | exit codes 0/1/2 and `--json` schema |
| Cross-check (documented) | manual + CI | Java job findings == Python verifier findings on the same seeded-drift DB |

### 8.4 Failure-engineering scenarios this week

Run the M4 section of [`failure-engineering.md`](../../18-projects/ledgerx/failure-engineering.md) end-to-end: crash at each point (process kill for two), Postgres restart mid-workload, Redis (if used for idempotency fast-path) down → DB path still correct, clock skew on `expires_at` (set container TZ differently), reconciliation during migration. **Every scenario ends with `python -m ledgerx_verify --json`** and the output is pasted into the write-up — the verifier is the referee, not the app under test. Each gets a write-up with captured logs.

### 8.5 GitHub expectations

Milestone `M4`, 9–11 issues; PRs `feat/m4-invariants`, `feat/m4-reconciliation`, `feat/m4-fault-injection`, `test/m4-crash-retry`, `test/m4-random-workload`, `tools/m4-python-verifier`, `tools/m4-python-generator`, `perf/m4-throughput`. The perf PR includes the k6 script, the generator seed, and raw JSON summaries under `perf/results/<date>/`. Tag `m4`.

## 9. Git activity

- Store raw benchmark outputs in-repo (small JSON) with a date-stamped folder; never edit them after commit — that is your methodology's honesty guarantee.
- `git tag -a m4 -m "..."`; push tags; write the tag message as a 5-line changelog.
- 10-minute drill: recover a deleted branch with `git reflog`.

## 10. Interview preparation

Two tracks, never mixed: **Track A = coding interview, Python**; **Track B = software-engineering / résumé interview, Java/Spring/SQL/etc.**

- **Think-aloud (Tue, 45 min) — Track A, Python:** LeetCode 39 (Combination Sum), recorded and scored.
- **Résumé-defense drill (Sat) — Track B — this week: Docker, Linux, CI/CD** (+ one Python question from [`17-resume-tech-defense/python.md`](../../17-resume-tech-defense/python.md) now that you have a real Python component to point at). [`17-resume-tech-defense/docker.md`](../../17-resume-tech-defense/docker.md), [`linux.md`](../../17-resume-tech-defense/linux.md), [`cicd.md`](../../17-resume-tech-defense/cicd.md). Use the `docker kill` runbook and the CI chaos job as evidence.
- **Checkpoint 12 (Sun, ~3 h):** [`checkpoints/checkpoint-12.md`](../../checkpoints/checkpoint-12.md) — "Can I reason about and prove transactional correctness, locking and idempotency?" Timed coding (Python, Track A) + knowledge + practical (Java/SQL, Track B) + explain + project review. Score it honestly; write the remediation plan if any section fails.
- **Applications (Sun):** with two projects (one deployed, one MVP+), re-run [`JOB_READINESS.md`](../../JOB_READINESS.md) — you should now meet "internship-ready" on most criteria; raise volume to 5/week from next week.

## 11. Revision work

- Checkpoint 12 covers Weeks 9–12: re-read your ADRs 0001–0004 and `docs/DESIGN_DECISIONS.md`; be able to reproduce the isolation table from memory.
- Flashcards: RR vs RC for reconciliation, injection point names and what each proves, at-least-once, the 4 retry-after-crash cases.
- Redo one Week 10 concurrency question and one Week 11 outbox question from the end-of-week tests, closed notes.

## 12. Daily plan

| Day | Learning | Project | DSA | Interview / other |
|---|---|---|---|---|
| **Mon** (8h) | Failure injection + invariants (1h); `psycopg`/`Decimal` patterns + labs (1h) | Milestone, invariant helpers, reconciliation tables (4.5h) | Backtracking §1–5, #78, #46 (1.5h) | — |
| **Tue** (8h) | — | ReconciliationService + endpoint + drift tests (5h) | #39, #90 + Day-3 reviews (2h) | Think-aloud #39 (1h) |
| **Wed** (8h) | Crash consistency (1h); pytest fixtures with a real DB (1h) | FaultInjector + wiring + profile test (3h); start `ledgerx_verify` (1.5h) | #40 (1.5h) | — |
| **Thu** (8h) | — | Crash-between-steps + retry-after-crash tests; docker-kill runbook (3.5h); finish `ledgerx_verify` + cross-check (1.5h) | #79, #131 + Day-7 reviews (2h) | `docs/TESTING.md` incl. Python tools (1h) |
| **Fri** (5h) | Property-style testing (1h) | `ledgerx_gen` + tests; workload → verifier in CI (2h) | Reviews + Java rep #78 (+#17) (1h) | Retro prep (1h) |
| **Sat** (6h) | Measurement methodology (1h, part of perf work) | k6 runs, `docs/PERFORMANCE.md`, failure write-ups, tag `m4` (4h) | — | Drill (1h) |
| **Sun** (3h) | — | — | Day-14/30 reviews | **Checkpoint 12** (§13), trackers, plan Week 13 |

## 13. End-of-week test = Checkpoint 12

This week the Sunday test **is** [`checkpoints/checkpoint-12.md`](../../checkpoints/checkpoint-12.md). Do it in full. A short self-check to run *before* it (Sat evening, 20 min):

1. Name the four retry-after-crash cases and which one most people get wrong.
   <details><summary>Answer</summary>Crash before claim; after claim before txn; during txn (rolled back); after commit before response. The last: if the response is stored outside the txn, a retry re-executes. Fix: store response in the same transaction.</details>
2. Why does reconciliation run under REPEATABLE READ?
   <details><summary>Answer</summary>All invariant queries must see one consistent snapshot; otherwise a concurrent commit between two checks yields spurious drift.</details>
3. Why must reconciliation never auto-fix?
   <details><summary>Answer</summary>Fixing hides the bug that caused drift, destroys evidence, and violates ledger immutability; drift is a finding for a human plus a compensating entry if warranted.</details>
4. What does `docker kill` test that a thrown exception cannot?
   <details><summary>Answer</summary>Real loss of in-memory state and in-flight connections: no `finally` blocks, no rollback issued by the app (Postgres detects the dropped connection and rolls back), no HTTP response. It validates recovery *on restart*, not just rollback.</details>
5. Backtracking: why do you copy the path when recording a result?
   <details><summary>Answer</summary>The same `List` object is mutated by later un-choose steps; without a copy every recorded result aliases the final (empty) state.</details>
6. What is the one number from your throughput report you would put on a résumé and how would you phrase it honestly?
   <details><summary>Answer</summary>Example shape: "Measured N transfers/s at p95 X ms on [machine], Docker Postgres, uniform load, k6 2-min run" — always with the environment and load qualifier; never a bare number.</details>
7. Why does the Python verifier not import anything from the Java project, and why `Decimal` instead of `float`?
   <details><summary>Answer</summary>Independence: sharing code would share bugs and assumptions; the DB schema is the only contract. `float` cannot represent decimal cents exactly, so sums drift in the last place and the verifier would report false (or hide real) drift; `psycopg` maps `NUMERIC` to `Decimal` and the tool keeps it that way.</details>

Pass gate is defined in the checkpoint file. Record the result in [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md).

## 14. Mastery checklist

- [ ] I can list LedgerX's invariants and show each as SQL.
- [ ] I can explain fault-injection design (profile-gated, named points, once-only) and the difference between throw and kill.
- [ ] I can prove retry-after-crash convergence with named tests and explain the transactional-response subtlety.
- [ ] I ran a seeded randomised workload and know how to replay/shrink a failure.
- [ ] I have a throughput report with methodology I could defend line by line.
- [ ] I built a tested, type-hinted Python verifier and generator with a real-DB pytest fixture, and can explain why it is independent and why `Decimal`.
- [ ] Java reconciliation and Python verifier agree on seeded drift; the verifier ends every failure write-up.
- [ ] 8 backtracking problems done in Python; Java rep done; Checkpoint 12 completed and scored.

## 15. Expected deliverables

- `ledgerx`: tag `m4`; `docs/TESTING.md` (incl. Python tools + cross-check), `docs/PERFORMANCE.md` (+ `perf/results/…`, generator seed), `docs/FAILURES.md` (M4 section, each with verifier output), `tools/` package (`ledgerx_verify`, `ledgerx_gen`, `tests/`, `pyproject.toml`, `README.md`), CI job `python-tools`.
- [`checkpoints/checkpoint-12.md`](../../checkpoints/checkpoint-12.md) score + remediation notes in [`weekly-progress.md`](../../trackers/weekly-progress.md).
- Trackers: [`project-tracker.md`](../../trackers/project-tracker.md) (M4 + Python component row), [`dsa-tracker.md`](../../trackers/dsa-tracker.md) (Python; Java rep column), [`technology-tracker.md`](../../trackers/technology-tracker.md) (Python: "tooling with DB + pytest, evidence: `ledgerx/tools/`"), [`interview-tracker.md`](../../trackers/interview-tracker.md).

## 16. If behind / stretch

**Behind?** Cut order: optimistic throughput run (keep pessimistic) → hot-wallet distribution → generator HTTP mode (keep SQL bulk mode) → `docker kill` scripted test (keep the manual runbook with captured output) → #17/#131. **Never cut** the invariant suite, reconciliation, the retry-after-crash tests, or the Python verifier with its pytest suite — Checkpoint 12 is built on the first three, and the verifier is LedgerX's required Python component. Per ROADMAP §13: if M4 is more than a week behind, drop M5's *advanced* features next week, not its deploy/docs.

**Ahead?** Convert the randomised workload to jqwik with shrinking; add a Toxiproxy Testcontainer to inject latency between app and Postgres and observe lock-wait behaviour under the hot-wallet load; give the verifier a `--watch` mode that re-checks every N seconds during a k6 run and records the first moment drift appears; solve LeetCode 51 (N-Queens) and 212 (Word Search II) in Python.
