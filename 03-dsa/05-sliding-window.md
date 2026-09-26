# 05 — Sliding Window

> **Week 4** · NeetCode section: **Sliding Window** · Target: **8 new problems** + stretch pool · Java rep: **3** (Week 4)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#05--sliding-window) · Python: [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) (`Counter`, `deque`) · Java: [quick reference](./java-dsa-toolkit.md)

A window `[l, r]` over a **contiguous** range grows on the right and shrinks on the left while maintaining some summary (sum, counts, distinct count). Each index enters once and leaves once → O(n) instead of O(n²) or O(n·k).

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| Fixed-size window | Size k: add `a[r]`, remove `a[r - k]`, record when `r >= k - 1` |
| Variable-size, "longest valid" | Expand `r`; **while** invalid, shrink `l`; record `r - l + 1` after the while |
| Variable-size, "shortest valid" | Expand `r`; **while** valid, record then shrink `l` |
| Window state | `[0] * 26` / `[0] * 128` counts, `Counter`/`defaultdict(int)`, running sum, `distinct` counter, max frequency |
| Monotonicity requirement | Shrinking must never make an invalid window valid-by-growing again — e.g. non-negative numbers, "at most k distinct". Negatives break it → prefix sums ([04](./04-prefix-sums.md)) |
| Amortised O(n) | Inner `while` looks nested but `l` moves ≤ n times total |
| "Exactly k" trick | `exactly(k) = at_most(k) - at_most(k - 1)` |
| Monotonic deque | Window max/min in O(1) amortised (239) — `collections.deque` of indices with decreasing values |
| Best Time to Buy/Sell | A degenerate window: track min so far, profit = price − min |
| Comparing counts | `Counter` equality is O(alphabet); comparing two `[0] * 26` lists with `==` is O(26) |

---

## 2. Prerequisite knowledge

- [`03-two-pointers.md`](./03-two-pointers.md) — same-direction pointers.
- [`02-hashing.md`](./02-hashing.md) — counts in dicts/lists.
- [`04-prefix-sums.md`](./04-prefix-sums.md) — to know when a window **won't** work.
- `collections.deque` — [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md).

---

## 3. Python implementation

```python
from collections import defaultdict, deque


def max_average(a: list[int], k: int) -> float:
    """Fixed window (LC 643 core)."""
    window = sum(a[:k])
    best = window
    for r in range(k, len(a)):
        window += a[r] - a[r - k]            # slide: add right, drop left
        best = max(best, window)
    return best / k


def longest_at_most_k_distinct(s: str, k: int) -> int:
    """Variable window, LONGEST valid."""
    count: defaultdict[str, int] = defaultdict(int)
    best = l = 0
    for r, ch in enumerate(s):
        count[ch] += 1
        while len(count) > k:                # shrink until valid
            count[s[l]] -= 1
            if count[s[l]] == 0:
                del count[s[l]]              # keep len(count) == number of distinct chars
            l += 1
        best = max(best, r - l + 1)          # record AFTER shrinking
    return best


def min_len_at_least(a: list[int], target: int) -> int:
    """Variable window, SHORTEST valid (non-negative values)."""
    best = float("inf")
    total = l = 0
    for r, x in enumerate(a):
        total += x
        while total >= target:               # record while valid, then shrink
            best = min(best, r - l + 1)
            total -= a[l]
            l += 1
    return 0 if best == float("inf") else best


def contains_permutation(p: str, s: str) -> bool:
    """Fixed window with frequency match (LC 567 core)."""
    if len(p) > len(s):
        return False
    need, have = [0] * 26, [0] * 26
    for i in range(len(p)):
        need[ord(p[i]) - 97] += 1
        have[ord(s[i]) - 97] += 1
    if need == have:
        return True
    for r in range(len(p), len(s)):
        have[ord(s[r]) - 97] += 1
        have[ord(s[r - len(p)]) - 97] -= 1
        if need == have:                     # 26 compares per step → O(26 · n)
            return True
    return False


def window_max(a: list[int], k: int) -> list[int]:
    """Monotonic deque (LC 239 core): indices whose values decrease front → back."""
    dq: deque[int] = deque()
    out = []
    for r, x in enumerate(a):
        if dq and dq[0] <= r - k:
            dq.popleft()                     # front fell out of the window
        while dq and a[dq[-1]] <= x:
            dq.pop()                         # dominated: can never be a future max
        dq.append(r)
        if r >= k - 1:
            out.append(a[dq[0]])
    return out


assert max_average([1, 12, -5, -6, 50, 3], 4) == 12.75
assert longest_at_most_k_distinct("eceba", 2) == 3
assert min_len_at_least([2, 3, 1, 2, 4, 3], 7) == 2 and min_len_at_least([1, 1], 5) == 0
assert contains_permutation("ab", "eidbaooo") and not contains_permutation("ab", "eidboaoo")
assert window_max([1, 3, -1, -3, 5, 3, 6, 7], 3) == [3, 3, 5, 5, 6, 7]
print("sliding-window templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 4): 3 Longest Substring Without Repeating Characters.**

| Python | Java |
|---|---|
| `for r, ch in enumerate(s)` | `for (int r = 0; r < s.length(); r++) { char c = s.charAt(r); ... }` |
| `[0] * 128` | `int[] last = new int[128];` |
| `defaultdict(int)` + `del` at zero | `map.merge(c, -1, Integer::sum); if (map.get(c) == 0) map.remove(c);` |
| `deque` of indices | `ArrayDeque<Integer>`: `peekFirst`/`pollFirst`/`peekLast`/`pollLast`/`offerLast` |

```java
class LongestUniqueRep {
    static int lengthOfLongestSubstring(String s) {
        int[] last = new int[128];              // last index + 1 of each char (0 = unseen)
        int best = 0;
        for (int l = 0, r = 0; r < s.length(); r++) {
            char c = s.charAt(r);
            l = Math.max(l, last[c]);
            last[c] = r + 1;
            best = Math.max(best, r - l + 1);
        }
        return best;
    }

    public static void main(String[] args) {
        System.out.println(lengthOfLongestSubstring("abcabcbb") + " " + lengthOfLongestSubstring("abba")); // 3 2
    }
}
```

Java trap: comparing two `Integer` counts from two maps with `==` (567/76) fails above 127.

---

## 5. Common patterns (sub-variants)

| Sub-pattern | State | Problems |
|---|---|---|
| Fixed size k | running sum | 643 |
| Fixed size k with frequency match | two `[0] * 26` (or a `matches` counter) | 567 |
| Set/dict of values within distance k | `set` of size ≤ k, or value → last index | 219 |
| Longest with no repeats | last index per char | 3 |
| Longest with ≤ k "bad" items | counts + `max_freq` (valid iff `len - max_freq ≤ k`); zeros count | 424, 1004 |
| Shortest containing all required | `need` counts + `missing` counter | 76 |
| Shortest with sum ≥ target | running sum, shrink while valid | 209 |
| Min-so-far (degenerate window) | running min | 121 |
| Window max/min | monotonic deque | 239 |

---

## 6. How to recognise the pattern

- **"Contiguous"** subarray / substring **and** "longest", "shortest", "maximum sum of size k", "at most k", "contains all of".
- Values are **non-negative** (for sum windows) or the constraint is about counts/distinct chars.
- Brute force enumerates all O(n²) subarrays and re-computes something each time.
- n up to 10⁵ with lowercase strings → O(26·n) is fine.
- **Counter-signals:** "subsequence" (not contiguous) → DP; negatives with exact-sum → prefix sums.

---

## 7. Beginner problems (Week 4)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 643 | [Maximum Average Subarray I](https://leetcode.com/problems/maximum-average-subarray-i/) | Easy | Fixed window: `window += a[r] - a[r - k]`. Divide once at the end. |
| 121 | [Best Time to Buy and Sell Stock](https://leetcode.com/problems/best-time-to-buy-and-sell-stock/) | Easy | Track the min price so far; `best = max(best, price - lowest)`. |
| 219 | [Contains Duplicate II](https://leetcode.com/problems/contains-duplicate-ii/) | Easy | Dict value → last index; duplicate within k if `i - last[x] <= k`. |

---

## 8. Interview problems (Week 4)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 3 | [Longest Substring Without Repeating Characters](https://leetcode.com/problems/longest-substring-without-repeating-characters/) | Medium | 4 | <details><summary>show</summary>Store each char's last index; on a repeat jump `l = max(l, last[c] + 1)` — never move `l` backwards.</details> |
| 424 | [Longest Repeating Character Replacement](https://leetcode.com/problems/longest-repeating-character-replacement/) | Medium | 4 | <details><summary>show</summary>Window valid iff `len - max_freq ≤ k`. `max_freq` never needs decreasing — a stale max only prevents growth, never gives a wrong answer.</details> |
| 567 | [Permutation in String](https://leetcode.com/problems/permutation-in-string/) | Medium | 4 | <details><summary>show</summary>Fixed window of size `len(s1)` over `s2`; compare two 26-count lists (or track a `matches` count for O(n)).</details> |
| 1004 | [Max Consecutive Ones III](https://leetcode.com/problems/max-consecutive-ones-iii/) | Medium | 4 | <details><summary>show</summary>Longest window containing ≤ k zeros; shrink while zeros > k.</details> |
| 76 | [Minimum Window Substring](https://leetcode.com/problems/minimum-window-substring/) | Hard | 4 | <details><summary>show</summary>`need = Counter(t)` + `missing = len(t)`; expand until `missing == 0`, then shrink while still valid, recording `(start, length)` — slice only once at the end.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 209 | [Minimum Size Subarray Sum](https://leetcode.com/problems/minimum-size-subarray-sum/) | Medium | <details><summary>show</summary>Shortest-valid template: record while `total >= target`, then shrink. Positive values make it monotonic.</details> |
| 239 | [Sliding Window Maximum](https://leetcode.com/problems/sliding-window-maximum/) | Hard | <details><summary>show</summary>Monotonic decreasing deque of indices; front is the max; pop front when out of window.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Sliding Window**: 121 → 3 → 424 → 567 → 76 → 239.
Here: §3 templates → 643 → 121 → 219 → 3 → 424 → 567 → 1004 → 76 (fresh brain) → Java rep 3 → (W20–26) 209, 239.

---

## 10. Target number of problems

**8 new** in Week 4 + 2 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- `s[l:r + 1]` inside the loop to check validity → O(n) per step → O(n²). Keep incremental state instead.
- 76: slicing the answer every time a better window is found; store indices and slice once.
- `len(count)` as "distinct" while leaving zero-count keys in the dict — `del` them (or use a separate `distinct` counter).
- `Counter` comparison each step is O(alphabet) — fine for 26, careful with large alphabets.
- `deque[i]` in the middle is O(n); only use the ends.
- `float("inf")` as a sentinel then returning it (must map to 0 / -1 as the problem says).

**General**
- Using `if` instead of `while` to shrink → window stays invalid.
- Recording the answer at the wrong place (before shrinking for "longest").
- Window length `r - l` instead of `r - l + 1`.
- Applying a window to sums with negative numbers.
- 424: recomputing max frequency over 26 letters each step is fine (O(26n)); believing you must decrease `max_freq` when shrinking wastes time.

**Java-rep traps (3):** `s.charAt(r)` not `s[r]`; `Integer ==` on counts; `int` sums may overflow.

---

## 12. Mastery criteria

- [ ] Write the "longest valid" and "shortest valid" templates from memory and explain where the answer is recorded in each.
- [ ] Solve 3 in ≤ 15 min and 424 in ≤ 20 min from blank.
- [ ] Solve 76 in ≤ 35 min from blank (Hard).
- [ ] Explain the amortised O(n) argument in ≤ 3 sentences.
- [ ] Solve 2 unseen window Mediums in ≤ 25 min each.
- [ ] Java rep: 3 in Java in ≤ 15 min.

---

## 13. Revision schedule

| Review | Week 4 set |
|---|---|
| Day 0 | W4 |
| Day 3 | W4/W5 |
| Day 7 | W5 |
| Day 14 | W6 |
| Day 30 | W8 (review week) |

**Revisit:** Week 8 review week (3, 424 timed), Week 11 (heap vs deque for 239), Week 17+ OA simulations (windows are a top OA pattern), Weeks 20–26 (209, 239).

---

## Worked example — 3. Longest Substring Without Repeating Characters

**Clarify.** `s: str`, 0 ≤ len ≤ 5·10⁴, any ASCII (letters, digits, symbols, spaces). Return the length of the longest substring (contiguous) with all distinct characters. `""` → 0.

**Brute force.** Every (i, j), check distinct with a set → O(n³); with an incremental set per i → O(n²) = 2.5·10⁹. Far too slow in Python.

**Optimise.** Window `[l, r]` with an all-distinct invariant. Add `s[r]`; if it was seen at index `j ≥ l`, move `l` to `j + 1`. Record `r - l + 1`. Each char is processed once → O(n). Storing each char's **last index** lets `l` jump directly (never backwards — the `max` guard).

**Code (Python).**

```python
def length_of_longest_substring(s: str) -> int:
    last: dict[str, int] = {}          # char -> last index where it appeared
    best = l = 0
    for r, ch in enumerate(s):
        if ch in last and last[ch] >= l:
            l = last[ch] + 1           # jump past the previous occurrence
        last[ch] = r
        best = max(best, r - l + 1)
    return best


assert length_of_longest_substring("abcabcbb") == 3
assert length_of_longest_substring("bbbbb") == 1
assert length_of_longest_substring("pwwkew") == 3
assert length_of_longest_substring("") == 0
assert length_of_longest_substring("abba") == 2      # tests the `last[ch] >= l` guard
assert length_of_longest_substring(" ") == 1
print("3 worked example passed")
```

**Test cases.** `"abcabcbb"` → 3 · `"bbbbb"` → 1 · `"pwwkew"` → 3 (`"wke"`; `"pwke"` is a subsequence, not a substring) · `""` → 0 · `"abba"` → 2 — without the `>= l` guard, `l` would jump *back* when seeing the final `a` · `" "` → 1.

**Complexity.** Time O(n). Space O(min(n, Σ)) for the dict, Σ = alphabet size (≤ 128 for ASCII).
