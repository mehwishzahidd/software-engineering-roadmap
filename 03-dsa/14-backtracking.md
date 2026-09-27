# 14 — Backtracking

> **Week 12** · NeetCode section: **Backtracking** · Target: **8 new problems** + stretch pool · Java rep: **78** (Week 12)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#14--backtracking) · Python: [`19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md) (aliasing, copies) · Java: [quick reference](./java-dsa-toolkit.md)

Backtracking = DFS over a **decision tree**. At each node you make a choice, recurse, then **undo** the choice. It enumerates all subsets, permutations, combinations and partitions — exponential by nature, so the craft is in structuring choices to avoid duplicates and **pruning** branches that can't succeed.

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| Decision tree | Each level = one decision; each leaf (or node) = a candidate |
| Choose → explore → unchoose | `path.append(x); dfs(...); path.pop()` |
| State | `path` (current partial answer), `start` index (combinations), `used` list/set (permutations), remaining target |
| Record a result | **Copy** the path: `res.append(path[:])` (or `list(path)`); appending `path` itself stores a reference that you later empty |
| Subsets vs combinations vs permutations | Subsets: record at **every** node. Combinations: `start = i + 1` (or `i` if reuse allowed). Permutations: loop all indices, skip used |
| Duplicates in input | **Sort** first; at the same tree level skip `i > start and a[i] == a[i - 1]` |
| Pruning | Stop when the remaining target < 0; if sorted, `break` when `a[i] > remaining` |
| Grid backtracking | Mark the cell (e.g. `board[r][c] = "#"`), recurse 4 directions, restore |
| `itertools` | `combinations`, `permutations`, `product` exist — fine to *mention* (and for quick checks), but interviews expect the hand-written DFS |
| Complexity | Subsets O(2ⁿ · n), permutations O(n! · n) — exponential; n is small (≤ 10–20) |
| vs DP | Backtracking enumerates **all** solutions; DP counts/optimises when subproblems overlap |

Practical use: generating test-case matrices — e.g. the [ForgeCI](../18-projects/forgeci/README.md) Python test-repository generator enumerates pipeline-config variants (passing/failing/slow/timeout) — and recognising when brute-force enumeration is acceptable because n ≤ 20.

---

## 2. Prerequisite knowledge

- [`09-recursion.md`](./09-recursion.md) — contracts, base case, undo, closures and `nonlocal`.
- [`10-trees.md`](./10-trees.md) — preorder DFS on a tree (the decision tree).
- Aliasing and copies — [`19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md).

---

## 3. Python implementation

```python
from itertools import combinations, permutations


def subsets(nums: list[int]) -> list[list[int]]:
    res: list[list[int]] = []
    path: list[int] = []

    def dfs(start: int) -> None:
        res.append(path[:])                       # COPY — path keeps changing
        for i in range(start, len(nums)):
            path.append(nums[i])                  # choose
            dfs(i + 1)                            # explore
            path.pop()                            # unchoose

    dfs(0)
    return res


def subsets_with_dup(nums: list[int]) -> list[list[int]]:
    a = sorted(nums)
    res: list[list[int]] = []
    path: list[int] = []

    def dfs(start: int) -> None:
        res.append(path[:])
        for i in range(start, len(a)):
            if i > start and a[i] == a[i - 1]:    # same value already tried at THIS level
                continue
            path.append(a[i])
            dfs(i + 1)
            path.pop()

    dfs(0)
    return res


def permute(nums: list[int]) -> list[list[int]]:
    res: list[list[int]] = []
    path: list[int] = []
    used = [False] * len(nums)

    def dfs() -> None:
        if len(path) == len(nums):
            res.append(path[:])
            return
        for i, x in enumerate(nums):
            if used[i]:
                continue
            used[i] = True; path.append(x)
            dfs()
            used[i] = False; path.pop()

    dfs()
    return res


def combine(n: int, k: int) -> list[list[int]]:
    res: list[list[int]] = []
    path: list[int] = []

    def dfs(start: int) -> None:
        if len(path) == k:
            res.append(path[:])
            return
        for i in range(start, n - (k - len(path)) + 2):   # prune: not enough numbers left
            path.append(i)
            dfs(i + 1)
            path.pop()

    dfs(1)
    return res


def parens(n: int) -> list[str]:
    res: list[str] = []
    path: list[str] = []

    def dfs(open_: int, close: int) -> None:
        if len(path) == 2 * n:
            res.append("".join(path))
            return
        if open_ < n:
            path.append("("); dfs(open_ + 1, close); path.pop()
        if close < open_:
            path.append(")"); dfs(open_, close + 1); path.pop()

    dfs(0, 0)
    return res


def exist(board: list[list[str]], word: str) -> bool:
    R, C = len(board), len(board[0])

    def dfs(r: int, c: int, i: int) -> bool:
        if i == len(word):
            return True
        if not (0 <= r < R and 0 <= c < C) or board[r][c] != word[i]:
            return False
        saved, board[r][c] = board[r][c], "#"     # mark visited in place
        found = (dfs(r + 1, c, i + 1) or dfs(r - 1, c, i + 1)
                 or dfs(r, c + 1, i + 1) or dfs(r, c - 1, i + 1))
        board[r][c] = saved                       # restore
        return found

    return any(dfs(r, c, 0) for r in range(R) for c in range(C))


assert subsets([1, 2, 3]) == [[], [1], [1, 2], [1, 2, 3], [1, 3], [2], [2, 3], [3]]
assert subsets_with_dup([2, 1, 2]) == [[], [1], [1, 2], [1, 2, 2], [2], [2, 2]]
assert sorted(permute([1, 2, 3])) == sorted(map(list, permutations([1, 2, 3])))
assert combine(4, 2) == [list(c) for c in combinations(range(1, 5), 2)]
assert parens(3) == ["((()))", "(()())", "(())()", "()(())", "()()()"]
board = [list("ABCE"), list("SFCS"), list("ADEE")]
assert exist(board, "ABCCED") and not exist(board, "ABCB")
assert board[0] == list("ABCE")                   # board restored
print("backtracking templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 12): 78 Subsets.**

| Python | Java |
|---|---|
| `res.append(path[:])` | `res.add(new ArrayList<>(path))` |
| `path.pop()` | `path.remove(path.size() - 1)` — **not** `path.remove(x)` (that removes by index for `int`) |
| nested `def dfs` closure | a private helper with `res`/`path` passed as parameters (or fields) |
| `used = [False] * n` | `boolean[] used = new boolean[n]` |

```java
import java.util.*;

class SubsetsRep {
    static List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        dfs(nums, 0, new ArrayList<>(), res);
        return res;
    }

    private static void dfs(int[] nums, int start, List<Integer> path, List<List<Integer>> res) {
        res.add(new ArrayList<>(path));
        for (int i = start; i < nums.length; i++) {
            path.add(nums[i]);
            dfs(nums, i + 1, path, res);
            path.remove(path.size() - 1);
        }
    }

    public static void main(String[] args) {
        System.out.println(subsets(new int[]{1, 2, 3}));   // [[], [1], [1, 2], [1, 2, 3], [1, 3], [2], [2, 3], [3]]
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Distinguishing rule | Problems |
|---|---|---|
| Subsets | record every node; `start = i + 1` | 78 |
| Subsets with duplicates | sort + `i > start and a[i] == a[i-1]` skip | 90 |
| Combination sum, reuse allowed | recurse with `start = i` | 39 |
| Combination sum, each once, duplicates | sort + skip + `start = i + 1` + `break` when too big | 40 |
| Permutations | `used` flags, loop from 0 | 46 |
| Constrained string generation | counts decide which chars are legal | 22 |
| Mapping digits → letters | one level per digit | 17 |
| Partitioning a string | choose the end of the next piece; check validity | 131 |
| Grid path search | mark/restore cells | 79, 212 |
| Constraint placement | row by row, sets for columns/diagonals | 51 |

---

## 6. How to recognise the pattern

- "Return **all** possible …" (subsets, combinations, permutations, partitions, valid strings, placements).
- n is **small**: ≤ 10 for permutations, ≤ 20 for subsets, board ≤ 6×6 for word search, n ≤ 9 for N-Queens.
- The output itself is exponential in size → no polynomial algorithm exists; enumeration is expected.
- "Count the number of ways" with large n → probably DP, not backtracking ([18](./18-dp-1d.md)).

---

## 7. Beginner problems (Week 12)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 78 | [Subsets](https://leetcode.com/problems/subsets/) | Medium | Record at every call; loop `i` from `start`; `path[:]`. |
| 22 | [Generate Parentheses](https://leetcode.com/problems/generate-parentheses/) | Medium | Add `(` if `open < n`; add `)` if `close < open`. |

---

## 8. Interview problems (Week 12)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 39 | [Combination Sum](https://leetcode.com/problems/combination-sum/) | Medium | 12 | <details><summary>show</summary>Recurse with `start = i` (reuse allowed) and `remaining - c[i]`; sort and `break` when `c[i] > remaining`.</details> |
| 46 | [Permutations](https://leetcode.com/problems/permutations/) | Medium | 12 | <details><summary>show</summary>`used` flags; record when `len(path) == n`.</details> |
| 90 | [Subsets II](https://leetcode.com/problems/subsets-ii/) | Medium | 12 | <details><summary>show</summary>Sort; skip `a[i] == a[i-1]` only when `i > start` (same level), not across levels.</details> |
| 40 | [Combination Sum II](https://leetcode.com/problems/combination-sum-ii/) | Medium | 12 | <details><summary>show</summary>Sort; `start = i + 1`; skip same-level duplicates; `break` when the candidate exceeds the remaining target.</details> |
| 79 | [Word Search](https://leetcode.com/problems/word-search/) | Medium | 12 | <details><summary>show</summary>DFS from every cell; mark with `"#"` and restore after; fail fast on mismatch. (Pruning idea: if the word's last letter is rarer than its first, search the reversed word.)</details> |
| 131 | [Palindrome Partitioning](https://leetcode.com/problems/palindrome-partitioning/) | Medium | 12 | <details><summary>show</summary>At index `start`, try every end `e`; if `s[start:e+1]` is a palindrome, add it and recurse from `e + 1`.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 17 | [Letter Combinations of a Phone Number](https://leetcode.com/problems/letter-combinations-of-a-phone-number/) | Medium | <details><summary>show</summary>Dict digit → letters; one recursion level per digit; empty input → `[]` (not `[""]`).</details> |
| 51 | [N-Queens](https://leetcode.com/problems/n-queens/) | Hard | <details><summary>show</summary>One queen per row; sets `cols`, `diag (r + c)`, `anti (r - c)` for O(1) conflict checks.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Backtracking**: 78 → 39 → 46 → 90 → 40 → 79 → 131 → 17 → 51. (22 is listed under NeetCode's Stack section; it's backtracking at heart.)
Here: §3 templates from memory → 78 → 22 → 39 → 46 → 90 → 40 → 79 → 131 → Java rep 78 → (W20–26) 17, 51.

---

## 10. Target number of problems

**8 new** in Week 12 + 2 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- `res.append(path)` instead of `res.append(path[:])` → every result ends up as the same (eventually empty) list. **Aliasing** is the #1 backtracking bug in Python.
- `path + [x]` in the recursive call avoids undo but allocates a new list per call (O(n) each) — acceptable, but know the cost.
- Mutable default arguments: `def dfs(i, path=[])` shares one list across calls and test cases.
- Strings are immutable: building with `s + ch` per call is fine for small n; for undo-style use a list + `"".join`.
- Forgetting to restore a grid cell after marking it.
- Using `itertools.permutations` in an interview without being able to write the DFS.
- Deep recursion is rarely the problem here (depth ≤ n ≤ 20); **time** is — prune.

**General**
- Duplicate skip across levels (`i > 0` instead of `i > start`) → loses valid answers like `[2, 2]`.
- 39 vs 40: mixing up `start = i` (reuse) and `start = i + 1` (no reuse).
- Not sorting before duplicate-skipping or before `break`-pruning.
- Stating complexity as "O(n²)" — it's exponential; say O(2ⁿ · n) / O(n! · n).

**Java-rep traps (78):** `res.add(path)` without `new ArrayList<>(path)`; `path.remove(int)` removes by index.

---

## 12. Mastery criteria

- [ ] Write the subsets, permutations and combination templates from memory in ≤ 10 min total.
- [ ] Explain the difference between `i > start` and `i > 0` duplicate skipping with an example.
- [ ] Explain the `res.append(path)` aliasing bug and fix it two ways.
- [ ] Solve 39, 40, 79 in ≤ 20 min each from blank.
- [ ] Solve 2 unseen backtracking Mediums in ≤ 25 min each and state their exponential complexity.
- [ ] Java rep: 78 in Java in ≤ 15 min.

---

## 13. Revision schedule

| Review | Week 12 set |
|---|---|
| Day 0 | W12 |
| Day 3 | W12/W13 |
| Day 7 | W13 |
| Day 14 | W14 (weekly mocks begin — backtracking is a mock favourite) |
| Day 30 | W16 |

**Revisit:** Week 13 (grid DFS without undo = graphs), Week 15 (backtracking + memo = DP: 139, 322), Weeks 20–26 (17, 51, and 212 from [12](./12-tries.md)).

---

## Worked example — 39. Combination Sum

**Clarify.** `candidates`: distinct positive ints (2 ≤ c ≤ 40), 1 ≤ length ≤ 30; `target` ≤ 40. Each candidate may be used **unlimited** times. Return all unique combinations (as multisets — `[2,2,3]` and `[3,2,2]` are the same). Fewer than 150 combinations are guaranteed.

**Brute force.** Generate all sequences summing to the target (order matters) and dedupe with a set of sorted tuples → exponentially many orderings per combination. Wasteful.

**Optimise.** Enforce **non-decreasing index order** so each multiset is generated once: from index `start`, choose `candidates[i]` for `i ≥ start` and recurse with `start = i` (reuse allowed). Sort so we can `break` as soon as a candidate exceeds the remaining target (all later ones are bigger).

**Code (Python).**

```python
def combination_sum(candidates: list[int], target: int) -> list[list[int]]:
    c = sorted(candidates)
    res: list[list[int]] = []
    path: list[int] = []

    def dfs(start: int, remaining: int) -> None:
        """Adds every combination (indices >= start, non-decreasing) summing to `remaining`, prefixed by path."""
        if remaining == 0:
            res.append(path[:])
            return
        for i in range(start, len(c)):
            if c[i] > remaining:
                break                        # sorted → no later candidate fits either
            path.append(c[i])
            dfs(i, remaining - c[i])         # i, not i + 1: reuse allowed
            path.pop()

    dfs(0, target)
    return res


assert combination_sum([2, 3, 6, 7], 7) == [[2, 2, 3], [7]]
assert combination_sum([2, 3, 5], 8) == [[2, 2, 2, 2], [2, 3, 3], [3, 5]]
assert combination_sum([2], 1) == []
assert combination_sum([7, 3, 2], 7) == [[2, 2, 3], [7]]      # unsorted input
print("39 worked example passed")
```

**Test cases.** `[2,3,6,7], 7` → `[[2,2,3],[7]]` · `[2,3,5], 8` → three combos · impossible `[2], 1` → `[]` · unsorted input works because we sort a copy · target equal to a candidate → includes the single-element list.

**Complexity.** Exponential: roughly O(N^(T/M + 1)) where N = #candidates, T = target, M = smallest candidate (depth ≤ T/M, branching ≤ N), plus O(T/M) per copied result. Space O(T/M) recursion depth + output.
