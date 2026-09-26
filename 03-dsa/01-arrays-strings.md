# 01 — Arrays & Strings

> **Weeks 1–2** · NeetCode section: **Arrays & Hashing** · Target: **7 new problems** (W1: 6, W2: 1) · Java rep: **189** (Week 1)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#01--arrays--strings) · Python: [cheatsheet](../PYTHON_INTERVIEW_CHEATSHEET.md), [`19-python/01-python-core.md`](../19-python/01-python-core.md) · Java: [quick reference](./java-dsa-toolkit.md)

Arrays (Python `list`) and strings are the input format of most interview problems. This guide is about **indexing fluently**, building results efficiently, and knowing what Python does under the hood (dynamic arrays, immutable `str`, slicing copies, `ord`/`chr`).

---

## 1. Concepts to learn

| Concept | Key facts | Interview angle |
|---|---|---|
| Static vs dynamic array | A Python `list` is a dynamic array of object references: O(1) index, amortised O(1) `append`/`pop()`, O(n) insert/delete elsewhere | "Why is `lst[i]` O(1) but `lst.pop(0)` O(n)?" |
| Amortised growth | CPython over-allocates when full, so appends are O(1) on average | "Explain amortised O(1)." |
| 2-D lists | `[[0] * C for _ in range(R)]` — **never** `[[0] * C] * R` (R references to one row) | Grid problems, matrix rotation |
| In-place algorithms | Modify the list with O(1) extra space: reverse, rotate, overwrite with a write pointer | "Can you do it in O(1) extra space?" |
| `str` | Immutable sequence of Unicode code points; `s[i]` O(1); slicing and `+` create new strings | Why `+=` in a loop is slow — use `"".join` |
| Slicing | `a[i:j]` copies j − i elements; `a[::-1]` copies all; slice assignment `a[:] = ...` mutates in place | Hidden O(n) inside loops |
| Character math | `ord(c) - ord('a')` ∈ [0, 25]; `chr(ord('a') + k)` | Frequency arrays `[0] * 26` |
| Prefix/suffix thinking | Precompute info from the left and from the right, combine | 238 Product of Array Except Self |
| Boyer–Moore voting | Majority element in O(n) time, O(1) space | 169 Majority Element |
| Useful built-ins | `enumerate`, `zip`, `reversed`, `min`/`max` with `key=`, `sum`, `any`/`all`, `str.split`/`strip`/`join`, `os.path.commonprefix` (know it exists; implement it yourself in interviews) | Clean, idiomatic interview code |

---

## 2. Prerequisite knowledge

- [`00-big-o.md`](./00-big-o.md) — including the Python cost table.
- Python core (Week 1): lists, strings, slicing, comprehensions — [`19-python/01-python-core.md`](../19-python/01-python-core.md).
- Cheatsheet sections on lists and strings — [`PYTHON_INTERVIEW_CHEATSHEET.md`](../PYTHON_INTERVIEW_CHEATSHEET.md).

---

## 3. Python implementation

### 3.1 A dynamic array from scratch (step 2 of the loop)

Python lists already are dynamic arrays — building one teaches you *why* `append` is amortised O(1) and `insert(0, x)` is O(n).

```python
class DynamicArray:
    def __init__(self) -> None:
        self._cap = 4
        self._data: list[int | None] = [None] * self._cap   # fixed-size backing store
        self._size = 0

    def _grow(self) -> None:                                # O(n), rare → amortised O(1) appends
        self._cap *= 2
        new = [None] * self._cap
        for i in range(self._size):
            new[i] = self._data[i]
        self._data = new

    def append(self, x: int) -> None:
        if self._size == self._cap:
            self._grow()
        self._data[self._size] = x
        self._size += 1

    def get(self, i: int) -> int:                           # O(1)
        if not 0 <= i < self._size:
            raise IndexError(i)
        return self._data[i]

    def insert(self, i: int, x: int) -> None:               # O(n): shift right
        if not 0 <= i <= self._size:
            raise IndexError(i)
        if self._size == self._cap:
            self._grow()
        for j in range(self._size, i, -1):
            self._data[j] = self._data[j - 1]
        self._data[i] = x
        self._size += 1

    def remove_at(self, i: int) -> int:                     # O(n): shift left
        if not 0 <= i < self._size:
            raise IndexError(i)
        old = self._data[i]
        for j in range(i, self._size - 1):
            self._data[j] = self._data[j + 1]
        self._size -= 1
        return old

    def __len__(self) -> int:
        return self._size

    def to_list(self) -> list[int]:
        return self._data[: self._size]


a = DynamicArray()
for i in range(10):
    a.append(i)
a.insert(0, -1)
a.remove_at(5)
assert a.to_list() == [-1, 0, 1, 2, 3, 5, 6, 7, 8, 9] and len(a) == 10
print("DynamicArray ok")
```

### 3.2 Core array/string templates

```python
def reverse_range(a: list[int], l: int, r: int) -> None:
    while l < r:
        a[l], a[r] = a[r], a[l]          # tuple swap
        l, r = l + 1, r - 1


def rotate(a: list[int], k: int) -> None:
    """Rotate right by k in place: O(n) time, O(1) extra space (three reversals)."""
    n = len(a)
    k %= n                                # k can exceed n
    reverse_range(a, 0, n - 1)
    reverse_range(a, 0, k - 1)
    reverse_range(a, k, n - 1)


def freq(s: str) -> list[int]:
    f = [0] * 26
    for ch in s:
        f[ord(ch) - ord("a")] += 1
    return f


def remove_value(a: list[int], val: int) -> int:
    """Write-pointer compaction; returns new logical length (LC 27 style)."""
    w = 0
    for x in a:
        if x != val:
            a[w] = x
            w += 1
    return w


def product_except_self(a: list[int]) -> list[int]:
    n = len(a)
    out = [1] * n
    for i in range(1, n):
        out[i] = out[i - 1] * a[i - 1]    # product of everything left of i
    right = 1
    for i in range(n - 1, -1, -1):
        out[i] *= right
        right *= a[i]
    return out


def majority(a: list[int]) -> int:
    """Boyer–Moore vote; assumes a majority exists."""
    cand, count = 0, 0
    for x in a:
        if count == 0:
            cand = x
        count += 1 if x == cand else -1
    return cand


def rotate_matrix(m: list[list[int]]) -> None:
    """90° clockwise in place: transpose, then reverse each row."""
    n = len(m)
    for i in range(n):
        for j in range(i + 1, n):
            m[i][j], m[j][i] = m[j][i], m[i][j]
    for row in m:
        row.reverse()


def interleave(a: str, b: str) -> str:
    parts = []
    for i in range(max(len(a), len(b))):
        if i < len(a):
            parts.append(a[i])
        if i < len(b):
            parts.append(b[i])
    return "".join(parts)                 # O(total), not O(n^2)


arr = [1, 2, 3, 4, 5, 6, 7]
rotate(arr, 3)
assert arr == [5, 6, 7, 1, 2, 3, 4]
assert freq("banana")[0] == 3
nums = [3, 2, 2, 3]
assert remove_value(nums, 3) == 2 and nums[:2] == [2, 2]
assert product_except_self([1, 2, 3, 4]) == [24, 12, 8, 6]
assert majority([2, 2, 1, 1, 1, 2, 2]) == 2
m = [[1, 2], [3, 4]]
rotate_matrix(m)
assert m == [[3, 1], [4, 2]]
assert interleave("abc", "pqrs") == "apbqcrs"
grid_bad = [[0] * 2] * 2
grid_bad[0][0] = 9
assert grid_bad == [[9, 0], [9, 0]]       # aliasing trap: both rows are the SAME list
grid_ok = [[0] * 2 for _ in range(2)]
grid_ok[0][0] = 9
assert grid_ok == [[9, 0], [0, 0]]
print("array templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 1): 189 Rotate Array** — re-solve it in Java from a blank file after your Python solution passes.

| Python | Java |
|---|---|
| `len(a)` / `len(s)` | `a.length` / `s.length()` |
| `a[l], a[r] = a[r], a[l]` | `int t = a[l]; a[l] = a[r]; a[r] = t;` |
| `ord(c) - ord('a')` | `c - 'a'` |
| `"".join(parts)` | `StringBuilder sb; sb.append(...); sb.toString()` |
| `[[0] * C for _ in range(R)]` | `new int[R][C]` (zero-filled, independent rows) |

```java
import java.util.*;

class RotateArrayRep {
    static void reverse(int[] a, int l, int r) {
        while (l < r) { int t = a[l]; a[l++] = a[r]; a[r--] = t; }
    }

    static void rotate(int[] a, int k) {
        int n = a.length;
        k %= n;
        reverse(a, 0, n - 1);
        reverse(a, 0, k - 1);
        reverse(a, k, n - 1);
    }

    public static void main(String[] args) {
        int[] a = {1, 2, 3, 4, 5, 6, 7};
        rotate(a, 3);
        System.out.println(Arrays.toString(a));   // [5, 6, 7, 1, 2, 3, 4]
    }
}
```

Java traps here: `Arrays.toString(a)` to print (not `a.toString()`), and `String` comparison with `.equals`.

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Idea | Example |
|---|---|---|
| Single pass with running state | Track max/min/count as you go | 169 |
| Write pointer / compaction | One index reads, one writes | LC 27, 283 style |
| Build output with a list, then `"".join` | Never `+=` strings in loops | 1768, 14 |
| Scan from the end | Skip trailing noise without splitting | 58 |
| Prefix / suffix precomputation | Info from left × info from right | 238 |
| Reverse tricks | Rotate = three reversals | 189 |
| Vertical scanning | Compare column by column across strings (`zip(*strs)`) | 14 |
| Counting list `[0] * 26` | Replace dicts for small alphabets | anagram-style problems |
| Matrix manipulation | Transpose, reverse, layer-by-layer | rotate image, spiral order |

---

## 6. How to recognise the pattern

- Input is a list/string and the answer depends on **positions**, not just membership → array technique (otherwise think hashing, [02](./02-hashing.md)).
- "In-place", "O(1) extra space" → reversal tricks, write pointer, or reuse the output list.
- "Without using division" → prefix/suffix products.
- "Appears more than ⌊n/2⌋ times" → Boyer–Moore voting.
- n ≤ 10⁵ → O(n) or O(n log n); a double loop is too slow (especially in Python).
- Strings with only lowercase letters → `[0] * 26` or `Counter`.

---

## 7. Beginner problems (Week 1)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 1929 | [Concatenation of Array](https://leetcode.com/problems/concatenation-of-array/) | Easy | `return nums + nums` works — then also write the index version (`ans[i] = ans[i + n] = nums[i]`). |
| 1768 | [Merge Strings Alternately](https://leetcode.com/problems/merge-strings-alternately/) | Easy | One index, a `parts` list, `"".join(parts)`; append whichever strings still have chars. |
| 58 | [Length of Last Word](https://leetcode.com/problems/length-of-last-word/) | Easy | `len(s.split()[-1])` is O(n) — then do the O(1)-extra-space version: scan from the end. |
| 14 | [Longest Common Prefix](https://leetcode.com/problems/longest-common-prefix/) | Easy | Vertical scan: `for i, chars in enumerate(zip(*strs))`, stop when `len(set(chars)) > 1`. |

---

## 8. Interview problems (Weeks 1–2)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 169 | [Majority Element](https://leetcode.com/problems/majority-element/) | Easy | 1 | <details><summary>show</summary>`Counter(nums).most_common(1)` is O(n)/O(n); Boyer–Moore voting is O(n)/O(1): the majority survives all cancellations.</details> |
| 189 | [Rotate Array](https://leetcode.com/problems/rotate-array/) | Medium | 1 | <details><summary>show</summary>Reverse all, reverse first k, reverse the rest; `k %= n`. (`nums[:] = nums[-k:] + nums[:-k]` works but uses O(n) extra — say so.)</details> |
| 238 | [Product of Array Except Self](https://leetcode.com/problems/product-of-array-except-self/) | Medium | 2 | <details><summary>show</summary>`answer[i]` = (product left of i) × (product right of i); do the right pass with a running variable to get O(1) extra space.</details> |

No stretch pool for this pattern — arrays reappear inside every later pattern.

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 section **Arrays & Hashing** (this guide covers the array-manipulation half; the hashing half is [02](./02-hashing.md)).

1. Implement `DynamicArray` (§3.1) from memory.
2. 1929 → 1768 → 58 → 14 → 169 → 189 (Week 1). Java rep: 189.
3. 238 (Week 2, first problem of the week). 238 is the only NeetCode 150 problem here; it leads straight into the hashing half of the section.

---

## 10. Target number of problems

**7 new** — 6 in Week 1, 1 in Week 2 (matches [ROADMAP §5](../ROADMAP.md#5-week-by-week-master-table): Week 1 = 6; Week 2's 8 = 1 here + 7 in [02](./02-hashing.md)).

---

## 11. Mistakes beginners commonly make

**Python-specific**
- `[[0] * C] * R` → every row is the **same list** (aliasing). Use a comprehension.
- `b = a` then mutating `b` changes `a` — assignment copies the reference. Use `a[:]`, `list(a)` or `copy.deepcopy` for nested lists.
- `s += ch` in a loop → quadratic-ish copying; collect in a list and `"".join`.
- Strings are immutable: `s[0] = "x"` raises `TypeError`. Convert with `list(s)`, modify, `"".join`.
- `nums = nums[-k:] + nums[:-k]` inside a function **rebinds** the local name; LeetCode checks the original list → use `nums[:] = ...`.
- `nums.sort()` returns `None` (in place); `sorted(nums)` returns a new list. `x = nums.sort()` is a bug.
- `/` is float division; use `//` for indices (`mid = (lo + hi) // 2`).
- Negative indices silently work (`a[-1]`), so an off-by-one to −1 doesn't crash — it reads the last element.
- `list.pop(0)` / `insert(0, x)` in a loop → O(n²).
- `for i in range(len(a))` when `enumerate(a)` is clearer; mutating a list while iterating over it.

**General**
- Off-by-one on loop bounds and on "last index = n − 1".
- Forgetting `k %= n` in rotation.
- Modifying the input when the interviewer expects it unchanged — ask first.

**Java-rep traps (189):** `a.length` vs `s.length()` vs `list.size()`; printing an array needs `Arrays.toString`.

---

## 12. Mastery criteria

- [ ] Implement `DynamicArray` with `append`, `get`, `insert`, `remove_at` from memory in ≤ 15 min, and explain amortised growth.
- [ ] Solve 238 from a blank editor in ≤ 20 min with O(1) extra space (output excluded) and explain why division fails with zeros.
- [ ] Solve 2 unseen Easy array/string problems in ≤ 12 min each in Python.
- [ ] Explain the `[[0] * C] * R` aliasing bug and the difference between `sort()` and `sorted()`.
- [ ] Java rep: 189 in Java in ≤ 15 min.

---

## 13. Revision schedule

| Review | Week 1 problems (1929, 1768, 58, 14, 169, 189) | Week 2 problem (238) |
|---|---|---|
| Day 0 | W1 | W2 |
| Day 3 | W1 Thu–Sun | W2 Thu–Sun |
| Day 7 | W2 | W3 |
| Day 14 | W3 | W4 (Checkpoint 4) |
| Day 30 | W5 | W6 |

**Revisit:** Week 4 ([Checkpoint 4](../checkpoints/checkpoint-04.md)) — re-solve 238 timed; Week 8 review week — 189; Week 20 mixed review if arrays are among your weakest patterns.

---

## Worked example — 238. Product of Array Except Self

**Clarify.**
- Input `nums: list[int]`, 2 ≤ n ≤ 10⁵, values in [−30, 30]; any prefix/suffix product fits in a 32-bit int (guaranteed — irrelevant in Python, relevant in your Java rep).
- Output `answer[i]` = product of all elements except `nums[i]`. **No division.** O(n) time.
- Zeros? Yes, allowed — one more reason division is out.

**Brute force.** For each i, multiply all j ≠ i → O(n²) time. n = 10⁵ → 10¹⁰ multiplications; hours in Python.

**Optimise.** `answer[i] = left[i] * right[i]` where `left[i]` = product of `nums[:i]` and `right[i]` = product of `nums[i+1:]`. Two arrays → O(n) time, O(n) space. Improvement: store `left` directly in the output list, then sweep right-to-left multiplying by a running `right` → O(1) extra space.

**Code (Python).**

```python
def product_except_self(nums: list[int]) -> list[int]:
    n = len(nums)
    answer = [1] * n                  # answer[0] = 1: nothing to the left of index 0
    for i in range(1, n):
        answer[i] = answer[i - 1] * nums[i - 1]
    right = 1                         # product of everything to the right of i
    for i in range(n - 1, -1, -1):
        answer[i] *= right
        right *= nums[i]
    return answer


assert product_except_self([1, 2, 3, 4]) == [24, 12, 8, 6]
assert product_except_self([-1, 1, 0, -3, 3]) == [0, 0, 9, 0, 0]
assert product_except_self([0, 0, 2]) == [0, 0, 0]
assert product_except_self([2, 3]) == [3, 2]
print("238 worked example passed")
```

**Test cases.**

| Input | Expected | Why |
|---|---|---|
| `[1,2,3,4]` | `[24,12,8,6]` | Normal case |
| `[-1,1,0,-3,3]` | `[0,0,9,0,0]` | Single zero: only the zero's slot is non-zero |
| `[0,0,2]` | `[0,0,0]` | Two zeros: everything is 0 |
| `[2,3]` | `[3,2]` | Minimum length |

**Complexity.** Time O(n) (two passes). Space O(1) extra — the output list doesn't count by the problem's convention; say so explicitly.
