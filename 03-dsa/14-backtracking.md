# 14 — Backtracking

> **Week 13** · NeetCode section: **Backtracking** · Target: **8 new problems** + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#14--backtracking) · Java APIs: [toolkit §11 list copies](./java-dsa-toolkit.md#11-collections-listof-and-immutability)

Backtracking = DFS over a **decision tree**. At each node you make a choice, recurse, then **undo** the choice. It enumerates all subsets, permutations, combinations and partitions — exponential by nature, so the craft is in structuring choices to avoid duplicates and **pruning** branches that can't succeed.

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| Decision tree | Each level = one decision; each leaf (or node) = a candidate |
| Choose → explore → unchoose | `path.add(x); recurse(); path.remove(path.size() - 1);` |
| State | `path` (current partial answer), `start` index (for combinations), `used[]` (for permutations), remaining target |
| Record a result | Copy the path: `res.add(new ArrayList<>(path))` |
| Subsets vs combinations vs permutations | Subsets: record at **every** node. Combinations: `start = i + 1` (or `i` if reuse allowed). Permutations: loop all indices, skip `used` |
| Duplicates in input | **Sort** first; at the same tree level skip `i > start && a[i] == a[i-1]` |
| Pruning | Stop when remaining target < 0; if sorted, `break` when `a[i] > remaining` |
| Grid backtracking | Mark cell visited (e.g. set to `'#'`), recurse 4 directions, restore |
| Complexity | Subsets O(2ⁿ · n), permutations O(n! · n), combination sum ~ O(branching^(target/min)) — exponential; n is small (≤ 10–20) |
| vs DP | Backtracking enumerates **all** solutions; DP counts/optimises when subproblems overlap |

Practical use: generating test data combinations, constraint solvers, and — more commonly — recognising when a brute-force enumeration is acceptable because n ≤ 20.

---

## 2. Prerequisite knowledge

- [`09-recursion.md`](./09-recursion.md) — contract, base case, `StringBuilder` undo.
- [`10-trees.md`](./10-trees.md) — preorder DFS on a tree (the decision tree).
- [Toolkit §11](./java-dsa-toolkit.md#11-collections-listof-and-immutability) — the "same mutable list added to results" trap.

---

## 3. Java implementation

```java
import java.util.*;

class BacktrackingTemplates {
    // SUBSETS: record at every node; next choices start after i
    static List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        subsetDfs(nums, 0, new ArrayList<>(), res);
        return res;
    }
    private static void subsetDfs(int[] a, int start, List<Integer> path, List<List<Integer>> res) {
        res.add(new ArrayList<>(path));                  // copy!
        for (int i = start; i < a.length; i++) {
            path.add(a[i]);                              // choose
            subsetDfs(a, i + 1, path, res);              // explore
            path.remove(path.size() - 1);                // unchoose
        }
    }

    // SUBSETS WITH DUPLICATES: sort + skip equal siblings
    static List<List<Integer>> subsetsWithDup(int[] nums) {
        int[] a = nums.clone();
        Arrays.sort(a);
        List<List<Integer>> res = new ArrayList<>();
        dupDfs(a, 0, new ArrayList<>(), res);
        return res;
    }
    private static void dupDfs(int[] a, int start, List<Integer> path, List<List<Integer>> res) {
        res.add(new ArrayList<>(path));
        for (int i = start; i < a.length; i++) {
            if (i > start && a[i] == a[i - 1]) continue; // same value already tried at THIS level
            path.add(a[i]);
            dupDfs(a, i + 1, path, res);
            path.remove(path.size() - 1);
        }
    }

    // PERMUTATIONS: used[] marks elements already in the path
    static List<List<Integer>> permute(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        permDfs(nums, new boolean[nums.length], new ArrayList<>(), res);
        return res;
    }
    private static void permDfs(int[] a, boolean[] used, List<Integer> path, List<List<Integer>> res) {
        if (path.size() == a.length) { res.add(new ArrayList<>(path)); return; }
        for (int i = 0; i < a.length; i++) {
            if (used[i]) continue;
            used[i] = true; path.add(a[i]);
            permDfs(a, used, path, res);
            used[i] = false; path.remove(path.size() - 1);
        }
    }

    // COMBINATIONS OF SIZE k from 1..n with pruning (not enough numbers left → stop)
    static List<List<Integer>> combine(int n, int k) {
        List<List<Integer>> res = new ArrayList<>();
        combDfs(n, k, 1, new ArrayList<>(), res);
        return res;
    }
    private static void combDfs(int n, int k, int start, List<Integer> path, List<List<Integer>> res) {
        if (path.size() == k) { res.add(new ArrayList<>(path)); return; }
        for (int i = start; i <= n - (k - path.size()) + 1; i++) {   // prune
            path.add(i);
            combDfs(n, k, i + 1, path, res);
            path.remove(path.size() - 1);
        }
    }

    // STRING BUILDING with constraints (LC 22 shape): open/close counts
    static List<String> parens(int n) {
        List<String> res = new ArrayList<>();
        parenDfs(n, 0, 0, new StringBuilder(), res);
        return res;
    }
    private static void parenDfs(int n, int open, int close, StringBuilder sb, List<String> res) {
        if (sb.length() == 2 * n) { res.add(sb.toString()); return; }
        if (open < n) { sb.append('('); parenDfs(n, open + 1, close, sb, res); sb.setLength(sb.length() - 1); }
        if (close < open) { sb.append(')'); parenDfs(n, open, close + 1, sb, res); sb.setLength(sb.length() - 1); }
    }

    // GRID backtracking (LC 79 shape): mark, recurse, restore
    static boolean exist(char[][] b, String word) {
        for (int r = 0; r < b.length; r++)
            for (int c = 0; c < b[0].length; c++)
                if (gridDfs(b, word, 0, r, c)) return true;
        return false;
    }
    private static boolean gridDfs(char[][] b, String w, int i, int r, int c) {
        if (i == w.length()) return true;
        if (r < 0 || r >= b.length || c < 0 || c >= b[0].length || b[r][c] != w.charAt(i)) return false;
        char saved = b[r][c];
        b[r][c] = '#';                                   // mark visited in place
        boolean found = gridDfs(b, w, i + 1, r + 1, c) || gridDfs(b, w, i + 1, r - 1, c)
                     || gridDfs(b, w, i + 1, r, c + 1) || gridDfs(b, w, i + 1, r, c - 1);
        b[r][c] = saved;                                 // restore
        return found;
    }

    public static void main(String[] args) {
        System.out.println(subsets(new int[]{1, 2, 3}));          // [[], [1], [1, 2], [1, 2, 3], [1, 3], [2], [2, 3], [3]]
        System.out.println(subsetsWithDup(new int[]{2, 1, 2}));   // [[], [1], [1, 2], [1, 2, 2], [2], [2, 2]]
        System.out.println(permute(new int[]{1, 2, 3}).size());   // 6
        System.out.println(combine(4, 2));                        // [[1, 2], [1, 3], [1, 4], [2, 3], [2, 4], [3, 4]]
        System.out.println(parens(3));                            // [((())), (()()), (())(), ()(()), ()()()]
        char[][] board = {"ABCE".toCharArray(), "SFCS".toCharArray(), "ADEE".toCharArray()};
        System.out.println(exist(board, "ABCCED") + " " + exist(board, "ABCB")); // true false
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Distinguishing rule | Problems |
|---|---|---|
| Subsets | record every node; `start = i + 1` | 78 |
| Subsets with duplicates | sort + `i > start && a[i] == a[i-1]` skip | 90 |
| Combination sum, reuse allowed | recurse with `start = i` | 39 |
| Combination sum, each once, duplicates | sort + skip + `start = i + 1` + `break` when too big | 40 |
| Permutations | `used[]`, loop from 0 | 46 |
| Constrained string generation | counts decide which chars are legal | 22 |
| Mapping digits → letters | one level per digit | 17 |
| Partitioning a string | choose the end of the next piece; check validity | 131 |
| Grid path search | mark/restore cells | 79, 212 |
| Constraint placement | row by row, sets for columns/diagonals | 51 |

---

## 5. How to recognise the pattern

- "Return **all** possible …" (subsets, combinations, permutations, partitions, valid strings, placements).
- n is **small**: ≤ 10 for permutations, ≤ 20 for subsets, board ≤ 6×6 for word search, n ≤ 9 for N-Queens.
- The output itself is exponential in size → no polynomial algorithm exists; enumeration is expected.
- "Count the number of ways" with large n → probably DP, not backtracking ([18](./18-dp-1d.md)).

---

## 6. Beginner problems (Week 13)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 78 | [Subsets](https://leetcode.com/problems/subsets/) | Medium | Record at every call; loop `i` from `start`; copy the path. |
| 22 | [Generate Parentheses](https://leetcode.com/problems/generate-parentheses/) | Medium | Add `(` if `open < n`; add `)` if `close < open`. |

---

## 7. Interview problems (Week 13)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 39 | [Combination Sum](https://leetcode.com/problems/combination-sum/) | Medium | 13 | <details><summary>show</summary>Recurse with `start = i` (reuse allowed) and `remaining - c[i]`; sort and `break` when `c[i] > remaining`.</details> |
| 46 | [Permutations](https://leetcode.com/problems/permutations/) | Medium | 13 | <details><summary>show</summary>`used[]` array; record when `path.size() == n`.</details> |
| 90 | [Subsets II](https://leetcode.com/problems/subsets-ii/) | Medium | 13 | <details><summary>show</summary>Sort; skip `a[i] == a[i-1]` only when `i > start` (same level), not across levels.</details> |
| 40 | [Combination Sum II](https://leetcode.com/problems/combination-sum-ii/) | Medium | 13 | <details><summary>show</summary>Sort; `start = i + 1`; skip same-level duplicates; `break` when the candidate exceeds the remaining target.</details> |
| 79 | [Word Search](https://leetcode.com/problems/word-search/) | Medium | 13 | <details><summary>show</summary>DFS from every cell; mark with `'#'` and restore after; fail fast on mismatch.</details> |
| 131 | [Palindrome Partitioning](https://leetcode.com/problems/palindrome-partitioning/) | Medium | 13 | <details><summary>show</summary>At index `start`, try every end `e`; if `s[start..e]` is a palindrome, add it and recurse from `e + 1`.</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 17 | [Letter Combinations of a Phone Number](https://leetcode.com/problems/letter-combinations-of-a-phone-number/) | Medium | <details><summary>show</summary>`String[] map` indexed by digit; one recursion level per digit; empty input → empty list.</details> |
| 51 | [N-Queens](https://leetcode.com/problems/n-queens/) | Hard | <details><summary>show</summary>Place one queen per row; `boolean[] cols, diag(r+c), anti(r−c+n−1)` for O(1) conflict checks.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Backtracking**: 78 → 39 → 46 → 90 → 40 → 79 → 131 → 17 → 51. (22 is listed under NeetCode's Stack section; it's a backtracking problem at heart.)
Here: §3 templates from memory → 78 → 22 → 39 → 46 → 90 → 40 → 79 → 131 (W13) → (W21–26) 17, 51.

---

## 9. Target number of problems

**8 new** in Week 13 + 2 stretch.

---

## 10. Mistakes beginners commonly make

- `res.add(path)` instead of `res.add(new ArrayList<>(path))` → every result ends up empty.
- Forgetting to undo (`path.remove(...)`, `used[i] = false`, restore the grid cell).
- `path.remove(x)` on a `List<Integer>` where `x` is an `int` → removes by **index**. Use `path.remove(path.size() - 1)`.
- Duplicate skip across levels (`i > 0` instead of `i > start`) → loses valid answers like `[2,2]`.
- 39 vs 40: mixing up `start = i` (reuse) and `start = i + 1` (no reuse).
- Not sorting before duplicate-skipping or before `break`-pruning.
- 79: using a separate `visited` HashSet of strings like `"r,c"` → slow; mark in place.
- Computing substrings repeatedly in 131 without need — fine for n ≤ 16, but mention palindrome DP precomputation as an optimisation.
- Stating complexity as "O(n²)" — it's exponential; say O(2ⁿ · n) / O(n! · n).

---

## 11. Mastery criteria

- [ ] Write the subsets, permutations and combination templates from memory in ≤ 10 min total.
- [ ] Explain the difference between `i > start` and `i > 0` duplicate skipping with an example.
- [ ] Solve 39, 40, 79 in ≤ 20 min each from blank.
- [ ] Solve 2 unseen backtracking Mediums in ≤ 25 min each and state their exponential complexity.

---

## 12. Revision schedule

| Review | Week 13 set |
|---|---|
| Day 0 | W13 |
| Day 3 | W13/W14 |
| Day 7 | W14 |
| Day 14 | W15 |
| Day 30 | W17 (first weekly mock — backtracking is a mock favourite) |

**Revisit:** Week 14 (grid DFS without undo = graphs), Week 16 (backtracking + memo = DP: 139, 322), Weeks 21–26 (17, 51, and 212 from [12](./12-tries.md)).

---

## Worked example — 39. Combination Sum

**Clarify.** `candidates`: distinct positive ints (2 ≤ c ≤ 40), 1 ≤ length ≤ 30; `target` ≤ 40. Each candidate may be used **unlimited** times. Return all unique combinations (as multisets — `[2,2,3]` and `[3,2,2]` are the same). Fewer than 150 combinations guaranteed.

**Brute force.** Generate all sequences summing to target (order matters) and dedupe with a set of sorted lists → wasteful: exponentially many orderings per combination.

**Optimise.** Enforce **non-decreasing index order** so each multiset is generated once: from index `start`, choose `candidates[i]` for `i ≥ start` and recurse with `start = i` (reuse allowed). Sort so we can `break` as soon as a candidate exceeds the remaining target (all later ones are bigger).

**Code.**

```java
import java.util.*;

class CombinationSum {
    public List<List<Integer>> combinationSum(int[] candidates, int target) {
        int[] c = candidates.clone();
        Arrays.sort(c);
        List<List<Integer>> res = new ArrayList<>();
        dfs(c, target, 0, new ArrayList<>(), res);
        return res;
    }

    // Contract: adds to res every combination (non-decreasing indices >= start) summing to remaining, prefixed by path
    private void dfs(int[] c, int remaining, int start, List<Integer> path, List<List<Integer>> res) {
        if (remaining == 0) {
            res.add(new ArrayList<>(path));
            return;
        }
        for (int i = start; i < c.length; i++) {
            if (c[i] > remaining) break;         // sorted → no later candidate fits either
            path.add(c[i]);
            dfs(c, remaining - c[i], i, path, res);   // i, not i + 1: reuse allowed
            path.remove(path.size() - 1);
        }
    }

    public static void main(String[] args) {
        CombinationSum s = new CombinationSum();
        System.out.println(s.combinationSum(new int[]{2, 3, 6, 7}, 7));   // [[2, 2, 3], [7]]
        System.out.println(s.combinationSum(new int[]{2, 3, 5}, 8));      // [[2, 2, 2, 2], [2, 3, 3], [3, 5]]
        System.out.println(s.combinationSum(new int[]{2}, 1));            // []
        System.out.println(s.combinationSum(new int[]{7, 3, 2}, 7));      // [[2, 2, 3], [7]] (unsorted input)
    }
}
```

**Test cases.** `[2,3,6,7], 7` → `[[2,2,3],[7]]` · `[2,3,5], 8` → three combos · impossible `[2], 1` → `[]` · unsorted input works because we sort a clone · target equal to a candidate → includes the single-element list.

**Complexity.** Exponential: roughly O(N^(T/M + 1)) where N = #candidates, T = target, M = smallest candidate (depth ≤ T/M, branching ≤ N), plus O(T/M) per copied result. Space O(T/M) recursion depth + output.
