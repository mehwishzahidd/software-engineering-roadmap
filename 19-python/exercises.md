# Python Exercises — 15 (Week 13 sprint + later scripting)

> Work in the `py-drills/` venv from [README.md](./README.md#setup). Every exercise has
> acceptance criteria; from #7 on, each needs **pytest** tests. Exercises 10, 11 and 14 are
> tools you will actually use on your projects — keep them in a `tools/` folder of the relevant
> project repo.

| # | Exercise | Block | Skills |
|---:|---|---|---|
| 1 | FizzBuzz, then translate 3 Java W1 exercises | 1 | syntax, `range`, f-strings |
| 2 | String toolkit | 1 | slicing, `split`/`join`, `//` and `%` |
| 3 | Function arguments | 1 | defaults, `*args`, keyword-only, mutable default trap |
| 4 | Word frequency | 2 | `Counter`, `most_common`, `sorted(key=)` |
| 5 | Group anagrams + Top-K | 2 | `defaultdict`, `heapq` |
| 6 | BFS shortest path in a grid | 2 | `deque`, tuples in sets |
| 7 | Money & Transaction dataclasses | 3 | `@dataclass(frozen=True)`, `Decimal`, validation |
| 8 | Bank account with custom exceptions | 3 | classes, `@property`, `raise ... from` |
| 9 | JSON config merger | 4 | `json`, `pathlib`, dict merging |
| 10 | Log parser | 4 | regex, `Counter`, `argparse` |
| 11 | CSV cleaner for Ledger imports | 4 | `csv`, `Decimal`, per-row errors |
| 12 | Port 3 LeetCode Easys from Java | 5 | DSA idioms |
| 13 | pytest suite for #11 | 5 | fixtures, `tmp_path`, parametrize |
| 14 | TicketHold API smoke tester | later (W14) | `requests`, exit codes |
| 15 | Git repo reporter | later | `subprocess`, `pathlib` |

---

## 1. FizzBuzz, then translate

Write FizzBuzz for 1..100 using `match` or `if/elif`. Then translate Java exercises 1.3, 1.4 and
1.5 from [../01-java/exercises.md](../01-java/exercises.md#week-1--syntax-basics) to Python.

- [ ] Uses `range`, f-strings; no semicolons, no parentheses around `if` conditions
- [ ] Python versions give the same outputs as your Java versions on 5 inputs each

## 2. String toolkit

Functions: `reverse_words(s)`, `is_palindrome(s)` (alphanumerics only, case-insensitive),
`caesar(s, k)` (letters only, wraps around, negative `k` allowed).

- [ ] `reverse_words("  the sky  is blue ")` → `"blue is sky the"` using `split()` + `join`
- [ ] `caesar("xyz", 3)` → `"abc"`, `caesar("abc", -3)` → `"xyz"` — uses `%` and explain why Python's `%` makes this simpler than Java's
- [ ] `is_palindrome` uses slicing or two indices; explain the O(n) space of `s[::-1]`

## 3. Function arguments

Write `def add_tag(tag, tags=[])` and demonstrate the shared-default bug by calling it three times.
Fix it. Then write `def log(msg, *args, level="INFO", **fields)` that prints
`"[INFO] msg arg1 arg2 k=v"`.

- [ ] A test (or asserts in `__main__`) shows the buggy version accumulates; fixed version doesn't
- [ ] `level` is keyword-only (calling `log("x", "WARN")` treats `"WARN"` as an arg, not the level)

## 4. Word frequency

`python wordfreq.py FILE --top 10` prints the top-N words (case-insensitive, letters and apostrophes only).

- [ ] Uses `Counter` and `re.findall(r"[a-z']+", text.lower())`
- [ ] Ties broken alphabetically: `sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))`
- [ ] Handles a missing file with a clear message and exit code 1

## 5. Group anagrams + Top-K

`group_anagrams(words) -> list[list[str]]` and `top_k_frequent(nums, k) -> list[int]` (LeetCode 49 and 347).

- [ ] Group anagrams uses `defaultdict(list)` with a `"".join(sorted(w))` key (or a 26-count tuple key)
- [ ] Top-K uses `heapq.nlargest` or a size-k heap; state the complexity

## 6. BFS shortest path in a grid

Given a grid of `0` (open) and `1` (wall), return the shortest path length from top-left to
bottom-right moving 4-directionally, or `-1`.

- [ ] Uses `collections.deque` and a `set` of `(r, c)` tuples
- [ ] Tests: open grid, blocked start, no path, 1×1 grid

## 7. Money & Transaction dataclasses

Port P1 Ledger's `Money` and `Transaction` to Python.

- [ ] `@dataclass(frozen=True)`; amounts are `Decimal` created from strings, quantized to 2 places with `ROUND_HALF_EVEN`
- [ ] `Money("1.5") == Money("1.50")` is `True` (normalize in `__post_init__` using `object.__setattr__` because it's frozen)
- [ ] Adding different currencies raises `ValueError`
- [ ] Instances usable as dict keys / in sets

## 8. Bank account with custom exceptions

`Account` with `deposit`, `withdraw`, `transfer_to`; `InsufficientFunds(Exception)` carrying the shortfall.

- [ ] `balance` is a read-only `@property`
- [ ] Wrapping a lower-level error uses `raise ... from e`; a test checks `__cause__`
- [ ] pytest: `with pytest.raises(InsufficientFunds, match="short by 500")`

## 9. JSON config merger

`python merge_config.py base.json override.json -o merged.json` deep-merges dicts (override wins, nested dicts merged recursively, lists replaced).

- [ ] Uses `pathlib`, `json.load`/`json.dump(indent=2, sort_keys=True)`
- [ ] Recursive `deep_merge(a, b)` does not mutate its inputs (test it)
- [ ] Invalid JSON → message with file name and line (`json.JSONDecodeError.lineno`), exit code 2

## 10. Log parser

Parse application logs (Spring Boot default format or your P2/P4 logs from `docker compose logs`):

```
2025-03-14T10:15:02.123Z  INFO 1 --- [nio-8080-exec-3] c.e.t.web.RequestLogFilter : method=POST path=/api/holds status=201 durationMs=34 requestId=ab12
2025-03-14T10:15:03.456Z ERROR 1 --- [nio-8080-exec-7] c.e.t.web.GlobalErrorHandler : method=POST path=/api/bookings status=500 durationMs=812 requestId=cd34
```

`python logstats.py app.log [--since 2025-03-14T10:00] [--path-prefix /api]` prints: count per level,
requests per status class (2xx/4xx/5xx), p50/p95/max `durationMs` per path, and the 5 slowest request IDs.

- [ ] Uses a compiled regex with named groups (`(?P<level>\w+)`) and `Counter`/`defaultdict(list)`
- [ ] Streams the file line by line (works on a 1 GB file without loading it)
- [ ] Lines that don't match are counted as "unparsed", not crashes
- [ ] Percentiles computed with `statistics.quantiles(data, n=100)` or sorted-index math; tested on known data
- [ ] `--help` output is meaningful (`argparse`)

## 11. CSV cleaner for Ledger imports

Real bank exports are messy. Write `clean_csv.py IN.csv OUT.csv --errors errors.csv` that
normalizes a bank export into P1 Ledger's import format `date,merchant,amount`:

- Dates in `dd/mm/yyyy`, `yyyy-mm-dd`, or `mm/dd/yyyy` (configurable via `--date-format`) → ISO `yyyy-mm-dd`
- Amounts like `"1,234.56"`, `(45.00)` (negative), `-3.5`, `€12,00` (with `--decimal-comma`) → `Decimal` with 2 places
- Merchants trimmed, internal whitespace collapsed, quoted commas preserved
- Duplicate rows (same date+merchant+amount) removed, count reported

Acceptance:
- [ ] Uses `csv.DictReader`/`DictWriter` (never `line.split(",")`)
- [ ] Bad rows go to `errors.csv` with `line,raw,reason`; good rows continue — same philosophy as the Java `CsvImporter` in [../01-java/05-exceptions-io.md](../01-java/05-exceptions-io.md)
- [ ] Prints a summary: `read=120 written=113 duplicates=4 errors=3`
- [ ] Exit code 0 if no errors, 1 if some rows rejected (useful in scripts)
- [ ] Output imports cleanly into your Ledger CLI

## 12. Port 3 LeetCode Easys from Java

Pick three problems you solved in Java during Weeks 1–4 (e.g. Two Sum, Valid Anagram, Valid
Parentheses) and solve them in Python.

- [ ] Idiomatic: `enumerate`, `Counter`, list-as-stack, no index-juggling where a builtin fits
- [ ] Each accepted on LeetCode; note in [../trackers/dsa-tracker.md](../trackers/dsa-tracker.md) that it was a language-port, not a new solve
- [ ] Write 3 bullet points: what was easier, what was harder than in Java

## 13. pytest suite for #11

- [ ] `tmp_path` fixture creates input files; tests read the produced output files
- [ ] `@pytest.mark.parametrize` covers ≥ 8 amount formats and ≥ 4 date formats (valid + invalid)
- [ ] One test runs the script end-to-end via `subprocess.run([sys.executable, "clean_csv.py", ...])` and checks the exit code
- [ ] `pytest -q` passes; coverage optional (`pip install pytest-cov; pytest --cov`)

## 14. TicketHold API smoke tester

After every change (and after deploy), run a script that exercises P2 TicketHold's critical path.
Adjust paths/payloads to match **your** API.

```
python smoke_tickethold.py --base-url http://localhost:8080
[PASS] GET  /actuator/health                 200  12ms
[PASS] POST /api/auth/register (organizer)   201  95ms
[PASS] POST /api/auth/login                  200  80ms
[PASS] POST /api/venues                      201  30ms
[PASS] POST /api/events                      201  28ms
[PASS] POST /api/holds (seat 1)              201  25ms
[PASS] POST /api/holds (same seat again)     409  18ms   ← conflict expected
[PASS] POST /api/bookings (Idempotency-Key)  201  40ms
[PASS] POST /api/bookings (same key again)   201/200 same booking id   ← idempotent
[PASS] GET  /api/events?page=0&size=5        200  15ms
10/10 passed
```

Acceptance:
- [ ] Uses a `requests.Session`, a timeout on **every** call, and a unique email per run (`uuid4`)
- [ ] Each step is a small function returning pass/fail + latency; one failure doesn't stop later independent checks
- [ ] Checks **negative** cases too: 401 without token, 400/422 on invalid payload (ProblemDetail body has `status` and `title`), 409 on double hold
- [ ] Non-zero exit code if any step fails → usable as a CI step or post-deploy check
- [ ] Base URL and credentials from args/env vars; no secrets in the file

## 15. Git repo reporter

`python repo_report.py ~/code` walks a folder, finds Git repositories (`.git` dirs), and prints for
each: current branch, uncommitted changes (yes/no), commits ahead/behind upstream, last commit date.

- [ ] Uses `pathlib` to find repos and `subprocess.run([...], capture_output=True, text=True, cwd=repo)` — list args, no `shell=True`
- [ ] Handles repos with no upstream gracefully
- [ ] Output as an aligned table; `--json` flag prints JSON instead
- [ ] Run it every Sunday review to catch unpushed work across P1–P4

---

## Progress

| # | Done | Notes |
|---:|:-:|---|
| 1–6 | | |
| 7–9 | | |
| 10–11 | | |
| 12–13 | | |
| 14–15 | | |
