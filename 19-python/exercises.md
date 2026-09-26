# Python exercises

> Graded exercises for Weeks 1–3, plus the weekly "Java rep" set. Each has acceptance criteria — an exercise is done when every criterion holds and you have explained the solution out loud.
> Grades: **E** (Easy, ≤ 15 min) · **M** (Medium, ≤ 30 min) · **H** (Hard, ≤ 60 min). Everything is throwaway kata code in `python-katas/`, tested with `pytest` from Week 3 (before that, `assert` blocks under `if __name__ == "__main__":`).
> Track them in [`trackers/technology-tracker.md`](../trackers/technology-tracker.md). LeetCode numbers are given where a matching problem exists; the DSA schedule itself is in [`03-dsa/`](../03-dsa/).

---

## Week 1 — core (with [`01-python-core.md`](./01-python-core.md))

### Types, strings, slicing

- [ ] **W1-01 (E) Number facts.** Write `describe(n: int) -> str` returning parity, sign, digit count, binary and hex. Acceptance: handles negatives and 0; uses f-string format specs (`:b`, `:x`), no manual loops for base conversion; `describe(-10)` mentions `"-1010"`.
- [ ] **W1-02 (E) Negative division table.** Print `a // b`, `a % b`, `int(a / b)`, `math.fmod(a, b)` for all `a, b in {-7, 7} × {-2, 2}`. Acceptance: you can explain every row and state the Java result beside it.
- [ ] **W1-03 (E) Slicing drill.** From `a = list(range(20))` produce: evens, odds reversed, last 5, middle third, `a` rotated left by k (`a[k:] + a[:k]`), every 3rd from index 1. Acceptance: one slice expression each; explain the cost of each slice.
- [ ] **W1-04 (E) Clean palindrome (LC 125).** `is_palindrome(s)` ignoring non-alphanumerics and case. Acceptance: two-pointer version without building a new string, and a one-liner with a comprehension + `[::-1]`; both O(n).
- [ ] **W1-05 (E) Char frequency two ways.** `top3(s)` returns `[(char, count)]` sorted by count desc then char asc, once with `dict.get` and once with a `[0]*26` array. Acceptance: multi-key sort with a negated count; no `Counter` yet.
- [ ] **W1-06 (M) Run-length encode/decode.** `"aaabccdddd"` → `"a3b1c2d4"` and back. Acceptance: encode uses list + `"".join` (no `+=`); decode handles multi-digit counts; round-trip property holds for 100 random strings (`random.Random(0)`).
- [ ] **W1-07 (M) String compression in place-ish (LC 443).** Work on `list[str]`, return the new length. Acceptance: O(1) extra space; write pointer pattern.

### Lists, tuples, sets, dicts

- [ ] **W1-08 (E) Dedupe preserving order.** `dedupe(xs)` keeps first occurrences. Acceptance: O(n) using a `set` for seen; explain why `list(set(xs))` is wrong.
- [ ] **W1-09 (E) Two Sum (LC 1)** with a dict of value → index. Acceptance: one pass; `enumerate`; explain why a set is insufficient.
- [ ] **W1-10 (E) Contains Duplicate (LC 217)** three ways: set length, sort + adjacent, early-exit set. Acceptance: state time/space of each.
- [ ] **W1-11 (M) Group anagrams (LC 49)** keyed by `tuple(sorted(w))` then by a 26-count tuple. Acceptance: explain why the key must be a tuple; complexities O(n·k log k) vs O(n·k).
- [ ] **W1-12 (M) Matrix drills.** Transpose (`zip(*m)` and index loops), rotate 90° clockwise in place (LC 48), spiral order (LC 54). Acceptance: no `[[0]*n]*m`; rotate is in place; spiral handles 1×n and n×1.
- [ ] **W1-13 (M) Inventory dict.** `Inventory` mapping `(warehouse, sku) → qty` with `add`, `remove` (raises `InsufficientStock`), `total_by_sku()`, `low_stock(threshold)`. Acceptance: tuple keys; custom exception with fields; `total_by_sku` uses `dict.get` or `setdefault`; 8 asserts including the exception path.
- [ ] **W1-14 (E) Set algebra.** Given warehouse SKU sets, compute SKUs in all, in any, only in one, missing from a target set. Acceptance: `&`, `|`, `^`, `-` used correctly; explain the complexity of `&`.

### Control flow, functions, closures, comprehensions

- [ ] **W1-15 (E) FizzBuzz variants.** Classic; then as a list comprehension; then `for ... else` search for the first multiple of 7 not divisible by 3. Acceptance: you can say when `else` runs.
- [ ] **W1-16 (E) `make_adder`, `counter`.** Closure factory and a `nonlocal` counter. Acceptance: explain what happens without `nonlocal`.
- [ ] **W1-17 (E) Flexible `stats(*nums, **opts)`.** Returns mean/min/max; `opts` may include `precision`. Acceptance: `stats()` with no args raises `ValueError`, not `ZeroDivisionError`.
- [ ] **W1-18 (M) Comprehension set.** Flatten a nested list one level; build `{word: len}` for words > 3 chars; set of first letters; transpose via nested comprehension; sum of squares via generator. Acceptance: no intermediate lists where a generator suffices; each ≤ 1 line.
- [ ] **W1-19 (M) Sorting keys.** Sort orders (`(id, priority, created_at)`) by priority desc then created_at asc; sort strings by length then alphabetically; sort dict items by value desc then key. Acceptance: single `sorted` call each; explain stability.
- [ ] **W1-20 (E) `zip`/`enumerate` drills.** Pair SKUs with quantities into a dict; unzip back; find the index of the max with `enumerate` and `max(key=...)`. Acceptance: `dict(zip(...))`, `zip(*pairs)`, `max(range(n), key=nums.__getitem__)`.

### Recursion, exceptions, classes

- [ ] **W1-21 (E) Recursion set.** `power(b, e)` (fast, O(log e)), `digit_sum(n)`, `reverse_in_place(a, i, j)`, `flatten(nested)` (arbitrary depth), `to_binary(n)`. Acceptance: base cases first; explain depth; `flatten` uses `isinstance(x, list)`.
- [ ] **W1-22 (M) Recursive vs iterative depth.** Sum a 5 000-element list recursively (index-based) and observe `RecursionError`; fix with `sys.setrecursionlimit` and separately with an iterative version. Acceptance: you can say why the iterative one is preferred.
- [ ] **W1-23 (E) Safe parsing.** `parse_qty(text) -> int | None` using EAFP; `parse_all(texts)` collects failures with their reasons. Acceptance: specific `except ValueError`; no bare `except`.
- [ ] **W1-24 (M) Stack and Queue classes.** `Stack` over `list`, `Queue` over `deque`, both with `push/pop/peek`, `__len__`, `__bool__`, `__repr__`, and `pop` on empty raising `IndexError`. Acceptance: `if not stack:` works; `repr` shows contents.
- [ ] **W1-25 (M) `Point` as dict key.** Class with `__eq__`, `__hash__`, `__lt__`, `__repr__`; use in a set, as dict keys, in `sorted`, in `heapq`. Acceptance: explain why `__eq__` without `__hash__` breaks set membership.

---

## Week 2 — toolkit + pitfalls (with [`02-interview-toolkit.md`](./02-interview-toolkit.md), [`03-pitfalls-and-complexity.md`](./03-pitfalls-and-complexity.md))

### collections

- [ ] **W2-01 (E) Valid Anagram (LC 242)** with `Counter` equality and with a 26-array. Acceptance: both O(n); say which uses less memory.
- [ ] **W2-02 (M) Top K Frequent (LC 347)** three ways: `most_common`, size-k heap, bucket sort. Acceptance: complexities O(n log k) / O(n log k) / O(n) stated; ties handled consistently.
- [ ] **W2-03 (M) Sliding Window Maximum (LC 239)** with a monotonic `deque` of indices. Acceptance: O(n); explain why indices, not values, are stored.
- [ ] **W2-04 (M) LRU Cache (LC 146)** with `OrderedDict`, then with dict + doubly linked list. Acceptance: O(1) `get`/`put`; the second version has a `_remove`/`_add_front` pair and dummy head/tail.
- [ ] **W2-05 (E) Graph builder.** From an edge list build undirected and directed adjacency lists with `defaultdict(list)`; return a plain dict. Acceptance: no key insertion on reads (`in` checks); isolated nodes are present if given.

### heapq and bisect

- [ ] **W2-06 (E) Kth Largest (LC 215)** with a size-k min-heap and with `nlargest`. Acceptance: O(n log k); explain why not a max-heap of all n.
- [ ] **W2-07 (M) K Closest Points (LC 973)** with a max-heap by negated distance. Acceptance: no `sqrt`; tuple `(-d, x, y)`; state why the tuple is fully orderable.
- [ ] **W2-08 (M) Merge k sorted lists (LC 23)** with `(val, i, node)` tiebreak. Acceptance: explain the `TypeError` that appears without `i`.
- [ ] **W2-09 (H) Median of a stream (LC 295)** with two heaps. Acceptance: invariant (`len(low) - len(high) ∈ {0, 1}`) stated and maintained; O(log n) add, O(1) median.
- [ ] **W2-10 (E) bisect drills.** For a sorted list with duplicates: first index of x, count of x, insertion index, "smallest ≥ x", "largest ≤ x". Acceptance: `bisect_left`/`bisect_right` chosen correctly; edge cases at both ends.
- [ ] **W2-11 (M) LIS (LC 300)** O(n log n) with `bisect_left` on tails, then the O(n²) DP. Acceptance: explain why `tails` is not the subsequence itself.
- [ ] **W2-12 (M) Time-based key-value store (LC 981).** Acceptance: `bisect_right` on timestamps; O(log n) get.

### itertools, functools, math, dataclasses

- [ ] **W2-13 (E) Combinatorics via itertools.** All subsets of `[1,2,3]`, all permutations of `"abc"`, all 3-bit masks via `product`, prefix sums via `accumulate(initial=0)`, RLE via `groupby`. Acceptance: outputs verified against the hand-written backtracking of the next exercise.
- [ ] **W2-14 (M) Backtracking by hand.** Subsets (LC 78), Permutations (LC 46), Combination Sum (LC 39). Acceptance: `path[:]` copies; undo step present; pruning explained.
- [ ] **W2-15 (E) Memoization.** Fibonacci and Climbing Stairs (LC 70) with `@cache`, then bottom-up with O(1) space. Acceptance: `cache_info()` inspected; explain hashability of arguments.
- [ ] **W2-16 (M) Largest Number (LC 179)** with `cmp_to_key`. Acceptance: handles all-zeros; explain why a plain key doesn't work.
- [ ] **W2-17 (E) math drills.** Perfect-square test with `isqrt`; gcd of a list with `math.gcd(*xs)`; count set bits three ways; ceil division without floats. Acceptance: no float sqrt.
- [ ] **W2-18 (M) Dataclass nodes.** `ListNode`, `TreeNode` as `@dataclass`, plus `build_list`/`to_list`/`build_tree` helpers; `Job` with `order=True` + `field(compare=False)` payload used in a heap; `Cell(frozen=True)` used in a `set`. Acceptance: `Job` heap pops by priority; `Cell` works as dict key; `default_factory` used for any list field.
- [ ] **W2-19 (M) Custom sorting drill.** Sort people by age desc then name asc in one call; by name desc then age asc using two stable sorts; argsort indices; sort dict by value desc key asc. Acceptance: each result verified against a brute-force expectation.
- [ ] **W2-20 (M) Generators.** Inorder traversal as a generator → BST iterator (LC 173); a `chunks(iterable, n)` generator; a `read_lines(path)` generator. Acceptance: O(h) memory for the iterator; the generator is exhausted after one pass and you can explain it.

### Pitfalls (make each bug happen, then fix it)

- [ ] **W2-21 (E) Mutable default.** Reproduce the shared-list bug; fix with `None`. Acceptance: test that two calls are independent.
- [ ] **W2-22 (E) Grid aliasing.** Reproduce `[[0]*n]*m`; fix; prove rows are distinct with `is`. 
- [ ] **W2-23 (E) Shallow vs deep.** Nested list copy with `[:]`, `copy.copy`, `copy.deepcopy`; show which share inner lists.
- [ ] **W2-24 (E) BFS with a list.** Time `pop(0)` vs `popleft` for 10^5 elements; write the numbers in your notes.
- [ ] **W2-25 (E) Membership cost.** Time `in` on list vs set for 2·10^4 lookups.
- [ ] **W2-26 (E) Heap ties.** Push two `(1, obj)` entries and get the `TypeError`; fix three ways.
- [ ] **W2-27 (E) Closures in loops.** `[lambda: i ...]` bug; fix with a default argument.
- [ ] **W2-28 (E) Recursion limit.** Hit `RecursionError` on a 2 000-node linked list traversal; fix iteratively.
- [ ] **W2-29 (M) Mutation during iteration.** Remove evens from a list inside a `for` and observe skipped elements; rewrite with a comprehension; same for a set (`RuntimeError`) and a dict (snapshot keys).
- [ ] **W2-30 (M) Complexity audit.** Take three of your Week 1 solutions and annotate every line with its cost; find one avoidable O(n) inside a loop. Acceptance: written table, fixed version, before/after timing.

---

## Week 3 — testing and scripting (with [`04-testing-and-scripting.md`](./04-testing-and-scripting.md))

- [ ] **W3-01 (E) Project skeleton.** `katas-tools/` with `pyproject.toml` (dev extras: pytest, mypy), `src/katas_tools/`, `tests/`, `.venv` in `.gitignore`; `pip install -e ".[dev]"` works; `pytest` finds zero tests and exits cleanly.
- [ ] **W3-02 (E) First tests.** Move `two_sum`, `is_palindrome`, `rle` into the package and write parametrized tests (≥ 5 cases each incl. empty input). Acceptance: `pytest -q` green; one test uses `pytest.raises`.
- [ ] **W3-03 (E) Fixtures.** `conftest.py` with a `sample_orders` fixture (list of dataclasses) and a `tmp_report(tmp_path)` fixture; tests for a `write_report(orders, path)` function. Acceptance: `tmp_path` used; no files left in the repo.
- [ ] **W3-04 (M) Type-checked.** Annotate every public function; run `mypy --strict src/` (or pyright) to zero errors; deliberately introduce an `Optional` misuse and watch it get caught.
- [ ] **W3-05 (M) CLI: order generator (throwaway).** `argparse` with `--orders --seed --out --format {json,csv}`; deterministic via `random.Random(seed)`; `main(argv)` testable. Acceptance: `main(["--orders","3","--seed","1"]) == 0`; same seed → identical output (tested); CSV written with `csv.DictWriter`; JSON with `json.dump`.
- [ ] **W3-06 (M) CSV → stats.** Read a 100k-row CSV lazily, aggregate qty by SKU with `Counter`, print top 10 as a Markdown table. Acceptance: memory stays flat (no `list(reader)`); `pathlib` for paths; `logging` for progress to stderr.
- [ ] **W3-07 (M) JSON config validator.** Load a JSON of "rules" (`{name, priority, percentage}`), validate: unique names, priorities unique, percentage in [0, 100]; report all violations; exit code 1 if any. Acceptance: `pytest` covers each violation type; validator is a pure function returning `list[Violation]` dataclasses.
- [ ] **W3-08 (M) subprocess: git repo generator.** Create N temp git repos with one commit each; assert `git log --oneline` shows it. Acceptance: `subprocess.run([...], check=True, capture_output=True, text=True, timeout=30)`; runs inside `tmp_path`.
- [ ] **W3-09 (M) HTTP client with a fake.** `ApiClient` wrapping `httpx.Client` with `post_order(payload)` returning `(status, elapsed_ms)`; tests use `httpx.MockTransport` returning 201, 409, 500. Acceptance: no network in tests; `raise_for_status` behaviour decided and tested.
- [ ] **W3-10 (H) Mini load harness.** `ThreadPoolExecutor` firing 200 requests at the fake transport with `--concurrency 20`; report p50/p95/p99 and status counts as JSON on stdout. Acceptance: percentile math unit-tested on a known list; harness handles exceptions from workers; explain GIL + I/O.
- [ ] **W3-11 (M) Decimal ledger check.** Given a CSV of entries (`txn_id, account, amount`), verify every txn sums to `Decimal("0")` and compute balances; report violations. Acceptance: amounts parsed as `Decimal(str)`; a float-based version is shown to produce a false drift.
- [ ] **W3-12 (M) psycopg read-only query (needs Postgres from [`04-sql-databases/`](../04-sql-databases/)).** Connect with a URL from an env var, run a parametrized `SELECT`, return dataclasses; `integration` marker skipped unless the env var is set. Acceptance: no string-formatted SQL; connection closed by `with`.
- [ ] **W3-13 (E) Logging discipline.** Add `-v` flag → DEBUG; results on stdout, logs on stderr; verify with `2>/dev/null` and `>/dev/null`.
- [ ] **W3-14 (E) README + CI.** README with usage; GitHub Actions workflow running `pip install -e ".[dev]" && mypy src && pytest` on push (see [`13-cicd/github-actions.md`](../13-cicd/github-actions.md)). Acceptance: green check on the katas repo.

---

## Java reps (≈1 per week from Week 2; log in [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md) "Java rep" column)

Re-implement in Java 21 a problem you already solved in Python. The goal is collections fluency, not new problem-solving. Reference: [`03-dsa/java-dsa-toolkit.md`](../03-dsa/java-dsa-toolkit.md), [`01-java/03-collections-generics.md`](../01-java/03-collections-generics.md).

- [ ] **J-01 HashMap counting.** Valid Anagram / Top K Frequent with `HashMap<Character,Integer>` + `merge(k, 1, Integer::sum)`; then `int[26]`. Acceptance: JUnit test; explain `getOrDefault` vs `computeIfAbsent` vs Python `Counter`/`defaultdict`.
- [ ] **J-02 HashSet + two pointers.** Contains Duplicate and Two Sum II. Acceptance: `HashSet<Integer>` and `contains`; compare with Python `set`.
- [ ] **J-03 ArrayDeque BFS.** Grid BFS (Rotting Oranges 994) with `ArrayDeque<int[]>` and a `boolean[][]` visited. Acceptance: `offer`/`poll`; level-by-level with `size()` snapshot; compare with `deque.popleft()`.
- [ ] **J-04 PriorityQueue top-k with Comparator.** K Closest Points with `PriorityQueue<int[]>((a, b) -> Integer.compare(dist(b), dist(a)))` capped at k; then `Comparator.comparingInt(...)`. Acceptance: explain max-heap via comparator vs Python negation.
- [ ] **J-05 Recursion on trees.** Max Depth, Same Tree, Invert Tree with a `TreeNode` class. Acceptance: `null` checks; compare with `Optional[TreeNode]`/`None`.
- [ ] **J-06 Stack.** Valid Parentheses and Daily Temperatures with `ArrayDeque<Integer>` as a stack. Acceptance: `push`/`pop`/`peek`; never `java.util.Stack`.
- [ ] **J-07 Sorting with Comparator + records.** Merge Intervals with `int[][]` sorted by `Comparator.comparingInt(a -> a[0])`; K Closest with a `record Point(int x, int y)`. Acceptance: `Arrays.sort` on objects is stable (TimSort); explain multi-key `thenComparing`.
- [ ] **J-08 Memoized DP.** Climbing Stairs / House Robber with a `HashMap<Integer,Integer>` memo, then an `int[]` table. Acceptance: compare with `@cache`; explain why Java needs an explicit map.

---

## Done criteria for the module

- [ ] W1: 25/25 with acceptance criteria met; the 12 "Break it" experiments in `01-python-core.md` predicted correctly
- [ ] W2: 30/30; the 25-question self-test in `03-pitfalls-and-complexity.md` scored ≥ 22
- [ ] W3: 14/14; katas repo has green CI with pytest + mypy
- [ ] Java reps: J-01 and J-02 done by end of Week 3; one per week afterwards
