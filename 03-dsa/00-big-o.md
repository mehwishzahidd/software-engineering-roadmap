# 00 — Big-O & Complexity Analysis

> **Week 1** · NeetCode section: — (prerequisite for all) · Target: **0 LeetCode problems + 10 analysis drills**
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md) · Python: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md), [`19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md) · Java reps: [`java-dsa-toolkit.md`](./java-dsa-toolkit.md)

Every interview answer ends with "time is O(…), space is O(…)". Every OA constraint (`1 ≤ n ≤ 10^5`) is a hint about the complexity you need. This guide teaches you to *derive* complexity from code and to *choose* a target complexity from constraints — before you write anything. Because you solve problems in **Python**, you must also know the **hidden costs of Python built-ins** (`list.pop(0)`, slicing, `in` on a list).

---

## 1. Concepts to learn

| Concept | What you must be able to say |
|---|---|
| **Asymptotic notation** | Big-O = upper bound on growth; Θ = tight bound; Ω = lower bound. In interviews "O" usually means the tight worst case. |
| **Drop constants and lower terms** | O(3n² + 5n + 7) = O(n²). O(n/2) = O(n). But O(n + m) stays — two independent inputs. |
| **Common classes** (fast → slow) | O(1) < O(log n) < O(√n) < O(n) < O(n log n) < O(n²) < O(n³) < O(2ⁿ) < O(n!) |
| **Loops** | Sequential loops **add**; nested loops **multiply**; a loop that halves/doubles is O(log n). |
| **Recursion** | Time ≈ (number of calls) × (work per call). Draw the recursion tree: branching factor `b`, depth `d` → O(bᵈ) calls. Space = max depth of the call stack. |
| **Master theorem (awareness)** | T(n) = a·T(n/b) + O(nᵈ). Merge sort: 2T(n/2) + O(n) → O(n log n). Binary search: T(n/2) + O(1) → O(log n). |
| **Amortised analysis** | `list.append` is O(1) amortised: occasional O(n) resize (CPython over-allocates ~12.5%), averages to O(1). Same for `dict`/`set` inserts. |
| **Space complexity** | Extra memory *you* allocate + recursion stack. Input usually doesn't count; output sometimes does (say which convention you're using). Slices and `sorted()` **allocate** new lists. |
| **Hidden costs in Python** | `x in list` O(n) · `list.pop(0)` / `list.insert(0, x)` O(n) · `a[i:j]` O(j−i) copy · `s + t` O(len) · `sorted` O(n log n) · `min`/`max`/`sum`/`count`/`index` O(n) · `list(dict)` O(n). See the table in §3. |
| **Best / average / worst case** | `dict` lookup O(1) average, O(n) worst (pathological collisions). Quicksort O(n log n) average, O(n²) worst; Python's `sorted` (Timsort) is O(n log n) worst, O(n) on already-sorted data. |
| **Log bases don't matter** | log₂ n and log₁₀ n differ by a constant factor. |
| **Input size vs input value** | `for i in range(n)` where `n` is a *value* up to 10⁹ is too slow even though it's "O(n)". |
| **Python speed** | ~10⁷ simple operations/second (Java ~10⁸). Choosing the right complexity class matters more in Python, not less. |

Practical use: in [FlowGrid](../18-projects/flowgrid/README.md) (Weeks 4–8) you will choose between a `HashSet` (O(1) lookup) and `List.contains` (O(n)) in Java when de-duplicating order lines, and between an in-memory scan and an indexed SQL query when listing inventory — the same analysis, with `EXPLAIN` instead of pen and paper.

---

## 2. Prerequisite knowledge

- Python core: loops, lists, dicts, functions — [`19-python/01-python-core.md`](../19-python/01-python-core.md) (Week 1).
- High-school algebra: exponents, logarithms (log₂ 1024 = 10, log₂ 10⁶ ≈ 20, log₂ 10⁹ ≈ 30).
- Nothing else — this is file 00.

---

## 3. Python implementation — code you should be able to analyse on sight

```python
from collections import deque


def first(a: list[int]) -> int:                 # O(1)
    return a[0]


def binary_search(a: list[int], t: int) -> int:  # O(log n): search space halves
    lo, hi = 0, len(a) - 1
    while lo <= hi:
        mid = (lo + hi) // 2
        if a[mid] == t:
            return mid
        if a[mid] < t:
            lo = mid + 1
        else:
            hi = mid - 1
    return -1


def total(a: list[int]) -> int:                  # O(n) time, O(1) extra space
    s = 0
    for x in a:
        s += x
    return s


def has_duplicate_sort(a: list[int]) -> bool:    # O(n log n) time, O(n) space (sorted() copies)
    b = sorted(a)
    return any(b[i] == b[i - 1] for i in range(1, len(b)))


def has_duplicate_set(a: list[int]) -> bool:     # O(n) average time, O(n) space
    seen = set()
    for x in a:
        if x in seen:                            # O(1) average on a set
            return True
        seen.add(x)
    return False


def count_pairs(a: list[int], target: int) -> int:   # O(n^2): n(n-1)/2 pairs
    c = 0
    for i in range(len(a)):
        for j in range(i + 1, len(a)):
            if a[i] + a[j] == target:
                c += 1
    return c


def common(a: list[int], b: list[int]) -> int:   # O(n * m) — two inputs, NOT O(n^2)
    return sum(1 for x in a for y in b if x == y)


def fib(n: int) -> int:                          # O(2^n) time, O(n) stack
    return n if n < 2 else fib(n - 1) + fib(n - 2)


def fib_memo(n: int, memo: dict[int, int] | None = None) -> int:   # O(n) time and space
    if memo is None:                             # never use a mutable default argument
        memo = {}
    if n < 2:
        return n
    if n not in memo:
        memo[n] = fib_memo(n - 1, memo) + fib_memo(n - 2, memo)
    return memo[n]


def dedupe_slow(xs: list[int]) -> list[int]:     # O(n^2) HIDDEN: `in` on a list is O(n)
    out = []
    for x in xs:
        if x not in out:
            out.append(x)
    return out


def dedupe_fast(xs: list[int]) -> list[int]:     # O(n): dict preserves insertion order (3.7+)
    return list(dict.fromkeys(xs))


def drain_slow(xs: list[int]) -> int:            # O(n^2) HIDDEN: pop(0) shifts every element
    xs = xs[:]                                   # O(n) copy so we don't mutate the caller's list
    s = 0
    while xs:
        s += xs.pop(0)
    return s


def drain_fast(xs: list[int]) -> int:            # O(n): deque.popleft is O(1)
    q = deque(xs)
    s = 0
    while q:
        s += q.popleft()
    return s


def harmonic(n: int) -> int:                     # O(n log n): n/1 + n/2 + ... + n/n
    ops = 0
    for i in range(1, n + 1):
        for _ in range(i, n + 1, i):
            ops += 1
    return ops


def is_prime(n: int) -> bool:                    # O(sqrt n)
    if n < 2:
        return False
    d = 2
    while d * d <= n:
        if n % d == 0:
            return False
        d += 1
    return True


assert first([4, 1]) == 4 and total([4, 1, 3, 1]) == 9
assert binary_search([1, 3, 5, 7], 5) == 2 and binary_search([1, 3], 2) == -1
assert has_duplicate_sort([4, 1, 3, 1]) and has_duplicate_set([4, 1, 3, 1])
assert count_pairs([4, 1, 3, 1], 4) == 2 and common([4, 1, 3, 1], [1, 9]) == 2
assert fib(20) == 6765 and fib_memo(80) == 23416728348467685
assert dedupe_slow([3, 1, 3, 2]) == dedupe_fast([3, 1, 3, 2]) == [3, 1, 2]
assert drain_slow([1, 2, 3]) == drain_fast([1, 2, 3]) == 6
assert harmonic(10) == 27 and is_prime(97) and not is_prime(91)
print("all complexity-zoo asserts passed")
```

### Python built-in cost table (memorise)

| Operation | Cost | Operation | Cost |
|---|---|---|---|
| `lst[i]`, `lst[i] = x`, `len(lst)` | O(1) | `x in lst`, `lst.index(x)`, `lst.count(x)` | O(n) |
| `lst.append(x)`, `lst.pop()` | O(1) amortised | `lst.pop(0)`, `lst.insert(0, x)`, `del lst[0]` | **O(n)** |
| `lst[a:b]` (slice) | O(b − a) copy | `lst + other`, `lst.copy()`, `list(x)` | O(n) |
| `sorted(x)`, `lst.sort()` | O(n log n) | `min`, `max`, `sum`, `any`, `all` | O(n) |
| `x in set`, `set.add`, `d[k]`, `k in d`, `d[k] = v` | O(1) average | `set_a & set_b` | O(min(len)) |
| `deque.append/appendleft/pop/popleft` | O(1) | `deque[i]` (middle) | O(n) |
| `heapq.heappush/heappop` | O(log n) | `heapq.heapify(lst)` | O(n) |
| `bisect.bisect_left` | O(log n) | `bisect.insort` | O(n) (the insert shifts) |
| `s + t` (strings) | O(len(s) + len(t)) | `"".join(parts)` | O(total length) |
| `s[i]` | O(1) | `sub in s`, `s.find(sub)` | O(n·m) worst |
| `Counter(xs)` | O(n) | `Counter.most_common(k)` | O(n log k) |

### Measure it — "Break" step

Predict, then run: double `n` and see how the time scales (O(n) → ×2, O(n²) → ×4, O(2ⁿ) → explodes).

```python
import time


def fib(n: int) -> int:
    return n if n < 2 else fib(n - 1) + fib(n - 2)


for n in range(20, 27, 2):
    t0 = time.perf_counter()
    fib(n)
    print(f"fib({n}) {1000 * (time.perf_counter() - t0):.1f} ms")   # ~x2.6 per +2

for n in (20_000, 40_000):
    xs = list(range(n))
    t0 = time.perf_counter()
    while xs:
        xs.pop(0)                                                   # O(n) each → O(n^2) total
    print(f"pop(0) drain n={n}: {1000 * (time.perf_counter() - t0):.1f} ms")  # ~x4 when n doubles
```

---

## 4. The same in Java (occasional reps)

No Java rep for Big-O itself. What changes when you re-implement a solution in Java:

| Concern | Python | Java |
|---|---|---|
| Speed | ~10⁷ ops/s | ~10⁸ ops/s after JIT warm-up |
| Integer size | unbounded | `int` 32-bit / `long` 64-bit — sums of 10⁵ values up to 10⁹ need `long` |
| Hidden O(n) ops | `list.pop(0)`, `x in list`, slicing | `ArrayList.remove(0)`, `list.contains`, `substring` (copies) |
| String building | `"".join(parts)` | `StringBuilder` |
| Sorting | Timsort (stable) | `Arrays.sort(int[])` dual-pivot quicksort (not stable); objects: Timsort (stable) |

```java
import java.util.*;

class ComplexityInJava {
    static boolean hasDuplicate(int[] a) {             // O(n) average time, O(n) space
        Set<Integer> seen = new HashSet<>();
        for (int x : a) if (!seen.add(x)) return true;
        return false;
    }

    public static void main(String[] args) {
        System.out.println(hasDuplicate(new int[]{1, 2, 3, 1}));   // true
        long sum = 0;                                              // int would overflow for big inputs
        for (int i = 0; i < 100_000; i++) sum += 1_000_000_000;
        System.out.println(sum);                                   // 100000000000000
    }
}
```

---

## 5. Common patterns (sub-variants of analysis)

| Code shape | Complexity | Why |
|---|---|---|
| `for i in range(n)` then separate `for j in range(n)` | O(n) | Add: n + n |
| `for i` { `for j` } over same n | O(n²) | Multiply |
| `i = 1; while i < n: i *= 2` | O(log n) | Number of doublings to reach n |
| Two pointers moving toward each other | O(n) | Each pointer moves ≤ n times total |
| Sliding window with inner `while` | O(n) | Left pointer moves ≤ n times **in total** (amortised) |
| Sort then scan | O(n log n) | Sort dominates |
| BFS/DFS on graph | O(V + E) | Each vertex and edge processed once |
| Grid DFS | O(R · C) | Each cell visited once |
| Heap of size k over n items | O(n log k) | n pushes/pops at log k each |
| Recursion, b branches, depth d | O(bᵈ) | Subsets: O(2ⁿ · n) including copying |
| Memoised DP with S states and T transitions per state | O(S · T) | Each state computed once |
| Binary search on answer range [lo, hi] with O(n) check | O(n log(hi − lo)) | log of the *value range* |
| Loop containing `x in some_list` | O(n²) | hidden linear scan |

---

## 6. How to recognise what complexity you need

Use the constraints **before** designing. Budget ≈ 10⁷ simple Python operations per second (judges usually allow 1–10 s; OA platforms often 2–10 s for Python).

| Constraint | Target | Signals the pattern… |
|---|---|---|
| n ≤ 10 | O(n!) | permutations |
| n ≤ 20 | O(2ⁿ) | subsets / bitmask |
| n ≤ 500 | O(n³) | triple loop / interval DP (borderline in Python) |
| n ≤ 3·10³ | O(n²) | double loop / 2-D DP |
| n ≤ 10⁵–2·10⁵ | **O(n log n) or better** | sort, heap, binary search |
| n ≤ 10⁶+ | O(n) with small constants | hash, two pointers, window, prefix sums |
| value ≤ 10⁹ with no array of that size | O(log V) | binary search on answer, math |

"Follow-up: can you do it in O(1) extra space?" → two pointers / in-place marking / bit tricks.
"Follow-up: can you do better than O(n log n)?" → hashing, bucket sort, or counting sort.

---

## 7. Beginner exercises (analysis drills, no LeetCode)

Do these on paper; write complexity, then check by running with n doubled.

| # | Drill |
|---:|---|
| D1 | Every function in the §3 complexity zoo: state time and space |
| D2 | `i = n; while i > 0: for j in range(i): ...; i //= 2` |
| D3 | `for i in range(n): for j in range(i * i): ...` |
| D4 | A function that does `if x not in result: result.append(x)` inside a loop over the input |
| D5 | Recursive `total(a, i)` that returns `a[i] + total(a, i + 1)` — time, space, and what happens at n = 5000? |

<details>
<summary>Answers D2–D5</summary>

- **D2:** n + n/2 + n/4 + … ≤ 2n → **O(n)** (geometric series), not O(n log n).
- **D3:** Σ i² ≈ n³/3 → **O(n³)**.
- **D4:** O(n) iterations × O(n) `in` on a list → **O(n²)**; use a `set` (or `dict.fromkeys` to keep order) for O(n).
- **D5:** O(n) time, **O(n) space** for the call stack — and `RecursionError` because Python's default recursion limit is 1000.

</details>

---

## 8. Interview-style analysis drills

<details>
<summary>D6–D10 (try first, then open)</summary>

| # | Question | Answer |
|---:|---|---|
| D6 | Complexity of generating all subsets of n elements and appending each (as a copy) to a result list? | O(2ⁿ · n) time; O(n) auxiliary stack; O(2ⁿ · n) output |
| D7 | Why is the sliding-window loop with an inner `while` still O(n)? | Each index enters the window once and leaves at most once → ≤ 2n pointer moves total (amortised) |
| D8 | Complexity of Dijkstra with `heapq`? | O((V + E) log V) (lazy deletion: O(E log E), same class) |
| D9 | `dict` lookup is O(1) — when is it not? | Average O(1); worst O(n) with pathological collisions; occasional resize is O(n) but amortised O(1) |
| D10 | Top-K frequent with `heapq.nlargest(k, ...)` vs sorting all counts vs bucket sort | O(n log k) vs O(n log n) vs O(n) |

</details>

---

## 9. Recommended practice order

1. Read §1 and the cost table in §3. 2. Analyse every function in §3 on paper. 3. Run the timing script. 4. D2–D5. 5. D6–D10.
6. **From now on:** every problem you solve gets a "Time / Space" line in the tracker's Notes. Never submit without stating complexity.

---

## 10. Target number of problems

- **0 LeetCode problems** in this guide (Week 1's 6 new problems belong to [`01-arrays-strings.md`](./01-arrays-strings.md)).
- **10 analysis drills** (D1–D10) completed in Week 1.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Treating `x in lst`, `lst.pop(0)`, `lst.insert(0, x)`, `lst.index(x)` as O(1) — they are O(n).
- Forgetting that slicing (`a[1:]`), `sorted()`, `list(...)`, `s[::-1]` **copy** → O(k) time **and** space. Recursing on `a[1:]` makes an O(n) solution O(n²).
- Building strings with `+=` in a loop instead of `"".join(...)`.
- Assuming recursion depth is free: default limit 1000 → `RecursionError` on deep inputs (`sys.setrecursionlimit` or iterate).
- Forgetting that `Counter`, `set(xs)`, `dict.fromkeys` are O(n) to **build** (fine — but count them).
- Assuming Python is "fast enough" for 10⁸ operations. It isn't.

**General**
- Calling O(n + m) "O(n)" or two independent inputs "O(n²)" when it's O(n·m).
- Forgetting the **recursion stack** in space complexity.
- Forgetting the **sort**: "my loop is O(n)" — but you called `sorted` first → O(n log n).
- Saying a sliding window with a nested `while` is O(n²).
- Confusing "n is a value" with "n is a length".
- Ignoring output size: returning all permutations is at least O(n · n!).
- Declaring `dict` "always O(1)" without the word *average*.

**Java-rep traps:** `ArrayList.remove(0)` and `String +=` have the same hidden costs; sums overflow `int`.

---

## 12. Mastery criteria

- [ ] Given any solution you've written, state time **and** space within 10 seconds, including recursion stack.
- [ ] Given a constraint line, state the target complexity and two candidate patterns.
- [ ] Recite the Python cost table in §3 for list, dict/set, deque, heapq, strings.
- [ ] Explain amortised O(1) of `list.append` in ≤ 4 sentences.
- [ ] Score 9/10 on D1–D10 without opening the answers.

---

## 13. Revision schedule

| When | What |
|---|---|
| Day 0 (Week 1) | All drills |
| Day 3 (Week 1) | Re-do D2, D3, D7 without notes |
| Day 7 (Week 2) | Recite the Python cost table; explain amortised analysis out loud |
| Day 14 (Week 3) | Analyse your Week 1–3 solutions in the tracker |
| Day 30 (Week 5) | Explain monotonic-stack amortisation while doing [06](./06-stack-queue.md) |
| Revisit | **Week 4** (sliding-window amortisation, [05](./05-sliding-window.md)), **Week 8** (recursion trees + merge sort, [09](./09-recursion.md)), **Week 15** (DP state × transition analysis), **Checkpoints 4 and 8** |

---

## Worked example — analyse and improve "Contains Duplicate"

**Clarify.** Input `nums: list[int]`, up to 10⁵ elements, values up to ±10⁹. Return `True` if any value appears twice. Empty list → `False`.

**Brute force.** Compare every pair: O(n²) time, O(1) space. With n = 10⁵ that's 5·10⁹ comparisons → hopeless in Python (~10 minutes).

**Optimise.**
- Option A: sort, then check neighbours → O(n log n) time; `nums.sort()` in place is O(1) extra besides Timsort's O(n) worst-case buffer, but it mutates the input; `sorted(nums)` is O(n) extra.
- Option B: `set`, return on first repeat → O(n) average time, O(n) space.
- One-liner: `len(set(nums)) != len(nums)` — also O(n), but always processes the whole list (no early exit). Mention the trade-off.

**Code (Python).**

```python
def contains_duplicate(nums: list[int]) -> bool:
    seen: set[int] = set()
    for x in nums:
        if x in seen:
            return True          # early exit on the first repeat
        seen.add(x)
    return False


assert contains_duplicate([1, 2, 3, 1]) is True
assert contains_duplicate([1, 2, 3]) is False
assert contains_duplicate([]) is False
assert contains_duplicate([7]) is False
assert contains_duplicate([-10**9, -10**9]) is True
print("217 worked example passed")
```

**Test cases.** `[1,2,3,1]` → True · `[1,2,3]` → False · `[]` → False · `[7]` → False · `[-10⁹, -10⁹]` → True.

**Complexity.** Time O(n) average; space O(n). Trade-off to say out loud: "If memory is tight I'd sort in place for O(n log n) time and less extra memory, at the cost of mutating the input."

(This is LeetCode 217, which you solve for real in Week 2 — see [`02-hashing.md`](./02-hashing.md).)
