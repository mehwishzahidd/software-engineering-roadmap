# 05 — Sliding Window

> **Week 5** · NeetCode section: **Sliding Window** · Target: **7 new problems** + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#05--sliding-window) · Java APIs: [`java-dsa-toolkit.md`](./java-dsa-toolkit.md)

A window `[l, r]` over a **contiguous** range grows on the right and shrinks on the left while maintaining some summary (sum, counts, distinct count). Each index enters once and leaves once → O(n) instead of O(n²) or O(n·k).

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| Fixed-size window | Size k: add `a[r]`, remove `a[r - k]`, record when `r >= k - 1` |
| Variable-size, "longest valid" | Expand `r`; **while** invalid, shrink `l`; record `r - l + 1` after the while |
| Variable-size, "shortest valid" | Expand `r`; **while** valid, record then shrink `l` |
| Window state | `int[26]`/`int[128]` counts, `HashMap` counts, running sum, `distinct` counter, max frequency |
| Monotonicity requirement | Shrinking must never make an invalid window valid-by-growing again — e.g. non-negative numbers, "at most k distinct". Negatives break it → prefix sums ([04](./04-prefix-sums.md)) |
| Amortised O(n) | Inner `while` looks nested but `l` moves ≤ n times total |
| "Exactly k" trick | `exactly(k) = atMost(k) - atMost(k - 1)` |
| Monotonic deque | Window max/min in O(1) amortised (239) — deque of indices with decreasing values |
| Best Time to Buy/Sell | A degenerate window: track min so far, profit = price − min |

---

## 2. Prerequisite knowledge

- [`03-two-pointers.md`](./03-two-pointers.md) — same-direction pointers.
- [`02-hashing.md`](./02-hashing.md) — counts in maps/arrays.
- [`04-prefix-sums.md`](./04-prefix-sums.md) — to know when a window **won't** work.
- [Toolkit §4 `merge`, §6 `ArrayDeque`](./java-dsa-toolkit.md#6-arraydeque-as-stack-and-queue).

---

## 3. Java implementation

```java
import java.util.*;

class WindowTemplates {
    // Fixed window: max average of any length-k subarray (LC 643 core)
    static double maxAverage(int[] a, int k) {
        long sum = 0;
        for (int i = 0; i < k; i++) sum += a[i];
        long best = sum;
        for (int r = k; r < a.length; r++) {
            sum += a[r] - a[r - k];           // slide: add right, drop left
            best = Math.max(best, sum);
        }
        return (double) best / k;
    }

    // Variable window, LONGEST valid: longest substring with at most k distinct chars
    static int longestAtMostK(String s, int k) {
        int[] cnt = new int[128];
        int distinct = 0, best = 0;
        for (int l = 0, r = 0; r < s.length(); r++) {
            if (cnt[s.charAt(r)]++ == 0) distinct++;
            while (distinct > k) {                       // shrink until valid
                if (--cnt[s.charAt(l++)] == 0) distinct--;
            }
            best = Math.max(best, r - l + 1);            // record AFTER shrinking
        }
        return best;
    }

    // Variable window, SHORTEST valid: min length subarray with sum >= target (non-negative values)
    static int minLenAtLeast(int[] a, int target) {
        int best = Integer.MAX_VALUE;
        long sum = 0;
        for (int l = 0, r = 0; r < a.length; r++) {
            sum += a[r];
            while (sum >= target) {                      // record while valid, then shrink
                best = Math.min(best, r - l + 1);
                sum -= a[l++];
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    // Fixed window with frequency match: does s contain a permutation of p? (LC 567 core)
    static boolean containsPermutation(String p, String s) {
        if (p.length() > s.length()) return false;
        int[] need = new int[26], have = new int[26];
        for (int i = 0; i < p.length(); i++) { need[p.charAt(i) - 'a']++; have[s.charAt(i) - 'a']++; }
        if (Arrays.equals(need, have)) return true;
        for (int r = p.length(); r < s.length(); r++) {
            have[s.charAt(r) - 'a']++;
            have[s.charAt(r - p.length()) - 'a']--;
            if (Arrays.equals(need, have)) return true;  // 26 compares per step → O(26·n)
        }
        return false;
    }

    // Monotonic deque: max of every window of size k (LC 239 core)
    static int[] windowMax(int[] a, int k) {
        int[] out = new int[a.length - k + 1];
        Deque<Integer> dq = new ArrayDeque<>();          // indices; values decreasing front→back
        for (int r = 0; r < a.length; r++) {
            while (!dq.isEmpty() && dq.peekFirst() <= r - k) dq.pollFirst();       // out of window
            while (!dq.isEmpty() && a[dq.peekLast()] <= a[r]) dq.pollLast();       // dominated
            dq.offerLast(r);
            if (r >= k - 1) out[r - k + 1] = a[dq.peekFirst()];
        }
        return out;
    }

    public static void main(String[] args) {
        System.out.println(maxAverage(new int[]{1, 12, -5, -6, 50, 3}, 4));       // 12.75
        System.out.println(longestAtMostK("eceba", 2));                           // 3
        System.out.println(minLenAtLeast(new int[]{2, 3, 1, 2, 4, 3}, 7));         // 2
        System.out.println(containsPermutation("ab", "eidbaooo"));                // true
        System.out.println(Arrays.toString(windowMax(new int[]{1, 3, -1, -3, 5, 3, 6, 7}, 3))); // [3, 3, 5, 5, 6, 7]
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | State | Problems |
|---|---|---|
| Fixed size k | running sum | 643 |
| Fixed size k with frequency match | `int[26]` × 2 (or a `matches` counter) | 567 |
| Set/Map of last seen within distance k | `HashSet` of size ≤ k | 219 |
| Longest with no repeats | `int[128]` last index or counts | 3 |
| Longest with ≤ k replacements | counts + `maxFreq`; valid iff `len - maxFreq ≤ k` | 424, 1004 |
| Shortest containing all required | `need` counts + `formed` counter | 76 |
| Min-so-far (degenerate window) | running min | 121 |
| Window max/min | monotonic deque | 239 |

---

## 5. How to recognise the pattern

- **"Contiguous"** subarray / substring **and** "longest", "shortest", "maximum sum of size k", "at most k", "contains all of".
- Values are **non-negative** (for sum windows) or constraint is about counts/distinct chars.
- Brute force enumerates all O(n²) subarrays and re-computes something each time.
- n up to 10⁵ with strings of lowercase letters → O(26·n) is fine.
- **Counter-signals:** "subsequence" (not contiguous) → DP; negatives with exact-sum → prefix sums.

---

## 6. Beginner problems (Week 5)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 643 | [Maximum Average Subarray I](https://leetcode.com/problems/maximum-average-subarray-i/) | Easy | Fixed window: `sum += a[r] - a[r - k]`. Divide once at the end. |
| 121 | [Best Time to Buy and Sell Stock](https://leetcode.com/problems/best-time-to-buy-and-sell-stock/) | Easy | Track min price so far; best = max(best, price − min). |
| 219 | [Contains Duplicate II](https://leetcode.com/problems/contains-duplicate-ii/) | Easy | Keep a `HashSet` of the last k values; remove `nums[i - k - 1]` as you go (or map value → last index). |

---

## 7. Interview problems (Week 5)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 3 | [Longest Substring Without Repeating Characters](https://leetcode.com/problems/longest-substring-without-repeating-characters/) | Medium | 5 | <details><summary>show</summary>Counts in `int[128]`; while `cnt[s[r]] > 1` shrink. Or store last index and jump `l = max(l, last[c] + 1)`.</details> |
| 424 | [Longest Repeating Character Replacement](https://leetcode.com/problems/longest-repeating-character-replacement/) | Medium | 5 | <details><summary>show</summary>Window valid iff `len - maxFreq ≤ k`. `maxFreq` never needs decreasing — a stale max only prevents growth, never gives a wrong answer.</details> |
| 567 | [Permutation in String](https://leetcode.com/problems/permutation-in-string/) | Medium | 5 | <details><summary>show</summary>Fixed window of size `|s1|` over `s2`; compare two `int[26]` (or track a `matches` count for O(n)).</details> |
| 76 | [Minimum Window Substring](https://leetcode.com/problems/minimum-window-substring/) | Hard | 5 | <details><summary>show</summary>`need` counts + `missing` total; expand until `missing == 0`, then shrink while still valid, recording the min window.</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 239 | [Sliding Window Maximum](https://leetcode.com/problems/sliding-window-maximum/) | Hard | <details><summary>show</summary>Monotonic decreasing deque of indices; front is the max; pop front when out of window.</details> |
| 1004 | [Max Consecutive Ones III](https://leetcode.com/problems/max-consecutive-ones-iii/) | Medium | <details><summary>show</summary>Longest window with ≤ k zeros; shrink while zeros > k.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Sliding Window**: 121 → 3 → 424 → 567 → 76 → 239.
Here: implement §3 templates → 643 → 121 → 219 → 3 → 424 → 567 → 76 (Sat, fresh brain) → 239 + 1004 in W21–26.

---

## 9. Target number of problems

**7 new** in Week 5 + 2 stretch.

---

## 10. Mistakes beginners commonly make

- Using `if` instead of `while` to shrink → window stays invalid.
- Recording the answer at the wrong place (before shrinking for "longest").
- Window length `r - l` instead of `r - l + 1`.
- Using a `HashMap<Character,Integer>` and forgetting to remove keys at count 0 → `map.size()` as "distinct" is wrong.
- Comparing `Integer` counts from two maps with `==` (fails > 127) — 567/76 bug in Java.
- 76: returning `s.substring` inside the loop (O(n) each) instead of storing `(start, len)`.
- 424: recomputing max frequency over 26 letters every step is fine (O(26n)), but thinking you must decrease `maxFreq` when shrinking wastes time.
- Applying a window to sums with negative numbers.
- `int` sum overflow for large values → `long`.

---

## 11. Mastery criteria

- [ ] Write the "longest valid" and "shortest valid" templates from memory and explain where the answer is recorded in each.
- [ ] Solve 3 in ≤ 15 min and 424 in ≤ 20 min from blank.
- [ ] Solve 76 in ≤ 35 min from blank (Hard).
- [ ] Explain the amortised O(n) argument in ≤ 3 sentences.
- [ ] Solve 2 unseen window Mediums in ≤ 25 min each.

---

## 12. Revision schedule

| Review | Week 5 set |
|---|---|
| Day 0 | W5 |
| Day 3 | W5/W6 |
| Day 7 | W6 |
| Day 14 | W7 |
| Day 30 | W9 |

**Revisit:** Week 8 review week (3, 424 timed), Week 12 (heap vs deque for 239), Weeks 21–26 (239, 1004), OA simulations (windows are a top OA pattern).

---

## Worked example — 3. Longest Substring Without Repeating Characters

**Clarify.** `String s`, 0 ≤ length ≤ 5·10⁴, any ASCII (letters, digits, symbols, spaces). Return length of the longest substring (contiguous) with all distinct characters. `""` → 0.

**Brute force.** Every (i, j), check distinct with a set → O(n³); with incremental set per i → O(n²) = 2.5·10⁹. Too slow.

**Optimise.** Window `[l, r]` with all-distinct invariant. Add `s[r]`; while it's duplicated, remove `s[l]` and `l++`. Record `r - l + 1`. Each char enters and leaves once → O(n). Refinement: store each char's **last index**; on a repeat jump `l` directly to `last + 1` (never backwards).

**Code.**

```java
import java.util.*;

class LongestUniqueSubstring {
    public int lengthOfLongestSubstring(String s) {
        int[] last = new int[128];          // last index + 1 of each ASCII char (0 = unseen)
        int best = 0;
        for (int l = 0, r = 0; r < s.length(); r++) {
            char c = s.charAt(r);
            l = Math.max(l, last[c]);       // jump past previous occurrence, never move left
            last[c] = r + 1;
            best = Math.max(best, r - l + 1);
        }
        return best;
    }

    public static void main(String[] args) {
        LongestUniqueSubstring s = new LongestUniqueSubstring();
        System.out.println(s.lengthOfLongestSubstring("abcabcbb")); // 3
        System.out.println(s.lengthOfLongestSubstring("bbbbb"));    // 1
        System.out.println(s.lengthOfLongestSubstring("pwwkew"));   // 3
        System.out.println(s.lengthOfLongestSubstring(""));         // 0
        System.out.println(s.lengthOfLongestSubstring("abba"));     // 2  (tests the Math.max guard)
    }
}
```

**Test cases.** `"abcabcbb"` → 3 · `"bbbbb"` → 1 · `"pwwkew"` → 3 (`"wke"`; `"pwke"` is a subsequence, not a substring) · `""` → 0 · `"abba"` → 2 — without `Math.max`, `l` would jump *back* to 1 when seeing the final `a`. · `" "` → 1.

**Complexity.** Time O(n). Space O(1) — fixed 128-int array (O(Σ) for alphabet Σ).
