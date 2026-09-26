# 03 — Two Pointers

> **Week 3** · NeetCode section: **Two Pointers** · Target: **5 new problems** + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#03--two-pointers) · Java APIs: [`java-dsa-toolkit.md`](./java-dsa-toolkit.md)

Two indices walk the input in a coordinated way so that every step **discards** candidates you can prove are useless. Result: O(n²) → O(n), often with O(1) extra space.

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| Opposite-direction pointers | `l = 0`, `r = n - 1`, move inward. Works when the input is **sorted** or **symmetric** (palindromes). |
| Same-direction (fast/slow, read/write) | `read` scans every element; `write` marks where the next kept element goes. In-place compaction. |
| Two inputs, one pointer each | Merge two sorted arrays/lists; compare heads, advance the smaller. |
| The elimination argument | In sorted Two Sum: if `a[l] + a[r] < target`, then `a[l]` paired with **any** element ≤ `a[r]` is too small → discard `l`. This proof is what interviewers want to hear. |
| Reduction to 2-pointer | 3Sum = fix one element + Two Sum II on the rest (after sorting). k-Sum = recursion down to 2-Sum. |
| Skipping duplicates | After sorting, `while (l < r && a[l] == a[l - 1]) l++` to avoid duplicate triplets. |
| Fill from the back | Merging into an array with spare capacity at the end (88) avoids shifting. |

---

## 2. Prerequisite knowledge

- [`01-arrays-strings.md`](./01-arrays-strings.md) (in-place reverse, write pointer)
- [`02-hashing.md`](./02-hashing.md) (Two Sum with a map — compare with the sorted two-pointer version)
- Sorting cost: `Arrays.sort` is O(n log n) — [`00-big-o.md`](./00-big-o.md)

---

## 3. Java implementation

```java
import java.util.*;

class TwoPointerTemplates {
    // Opposite ends: palindrome check ignoring non-alphanumerics (LC 125 style)
    static boolean isPalindrome(String s) {
        int l = 0, r = s.length() - 1;
        while (l < r) {
            while (l < r && !Character.isLetterOrDigit(s.charAt(l))) l++;
            while (l < r && !Character.isLetterOrDigit(s.charAt(r))) r--;
            if (Character.toLowerCase(s.charAt(l)) != Character.toLowerCase(s.charAt(r))) return false;
            l++; r--;
        }
        return true;
    }

    // Opposite ends on sorted input: pair with target sum (LC 167 style, 1-indexed output)
    static int[] pairSumSorted(int[] a, int target) {
        int l = 0, r = a.length - 1;
        while (l < r) {
            int sum = a[l] + a[r];
            if (sum == target) return new int[]{l + 1, r + 1};
            if (sum < target) l++;            // a[l] too small for every remaining partner
            else r--;                         // a[r] too big for every remaining partner
        }
        return new int[0];
    }

    // Same direction read/write: remove duplicates from sorted array in place, return new length
    static int dedupeSorted(int[] a) {
        if (a.length == 0) return 0;
        int w = 1;
        for (int read = 1; read < a.length; read++)
            if (a[read] != a[w - 1]) a[w++] = a[read];
        return w;
    }

    // Two inputs, fill from the back (LC 88 style): nums1 has m elements + n empty slots
    static void mergeInto(int[] nums1, int m, int[] nums2, int n) {
        int i = m - 1, j = n - 1, w = m + n - 1;
        while (j >= 0) {                      // once nums2 is exhausted, nums1's prefix is already in place
            if (i >= 0 && nums1[i] > nums2[j]) nums1[w--] = nums1[i--];
            else nums1[w--] = nums2[j--];
        }
    }

    // Sort + fix one + two pointers with duplicate skipping (3Sum)
    static List<List<Integer>> threeSum(int[] nums) {
        int[] a = nums.clone();
        Arrays.sort(a);
        List<List<Integer>> res = new ArrayList<>();
        for (int i = 0; i < a.length - 2; i++) {
            if (a[i] > 0) break;                          // smallest is positive → no zero sum
            if (i > 0 && a[i] == a[i - 1]) continue;      // skip duplicate anchors
            int l = i + 1, r = a.length - 1;
            while (l < r) {
                int sum = a[i] + a[l] + a[r];
                if (sum < 0) l++;
                else if (sum > 0) r--;
                else {
                    res.add(List.of(a[i], a[l], a[r]));
                    l++; r--;
                    while (l < r && a[l] == a[l - 1]) l++; // skip duplicate seconds
                }
            }
        }
        return res;
    }

    public static void main(String[] args) {
        System.out.println(isPalindrome("A man, a plan, a canal: Panama"));             // true
        System.out.println(Arrays.toString(pairSumSorted(new int[]{2, 7, 11, 15}, 9)));  // [1, 2]
        int[] d = {1, 1, 2, 3, 3};
        System.out.println(dedupeSorted(d));                                             // 3
        int[] n1 = {1, 2, 3, 0, 0, 0};
        mergeInto(n1, 3, new int[]{2, 5, 6}, 3);
        System.out.println(Arrays.toString(n1));                                         // [1, 2, 2, 3, 5, 6]
        System.out.println(threeSum(new int[]{-1, 0, 1, 2, -1, -4}));                    // [[-1, -1, 2], [-1, 0, 1]]
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Move rule | Problems |
|---|---|---|
| Converging on symmetric data | Compare ends, move both inward | 344, 125 |
| Converging on sorted sum | Sum too small → `l++`; too big → `r--` | 167, 15, 18 |
| Converging on area/width | Move the **shorter** side (it limits the area) | 11 |
| Converging with running max from each side | Water at i = min(maxLeft, maxRight) − h[i]; move the side with the smaller max | 42 |
| Merge two sorted sequences | Advance the smaller head; fill from the back when merging in place | 88, 21 (lists) |
| Read/write compaction | `write` only advances on kept elements | 26, 27, 283 |
| Fast/slow on linked lists | See [08](./08-linked-lists.md) | 141, 876 |

---

## 5. How to recognise the pattern

- Input is **sorted** (or you're allowed to sort) and you're looking for a pair/triplet → converging pointers.
- "In place", "O(1) extra space", "without allocating another array".
- Palindrome, reverse, symmetric.
- "Container", "area between lines", "trap water".
- Two sorted inputs to merge.
- n ≤ 10⁵ and brute force is a double loop over pairs → two pointers gives O(n) after an O(n log n) sort.
- **Counter-signal:** unsorted input and you need original indices (LC 1) → hashing is simpler; sorting destroys indices.

---

## 6. Beginner problems (Week 3)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 344 | [Reverse String](https://leetcode.com/problems/reverse-string/) | Easy | Swap `s[l]`, `s[r]`, move inward; `char[]` in place. |
| 125 | [Valid Palindrome](https://leetcode.com/problems/valid-palindrome/) | Easy | Skip non-alphanumerics with inner `while (l < r && ...)`; compare lowercase. |

---

## 7. Interview problems (Week 3)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 167 | [Two Sum II - Input Array Is Sorted](https://leetcode.com/problems/two-sum-ii-input-array-is-sorted/) | Medium | 3 | <details><summary>show</summary>Converging pointers; sum too small → `l++`, too big → `r--`. 1-indexed output. O(1) space beats the hash map.</details> |
| 15 | [3Sum](https://leetcode.com/problems/3sum/) | Medium | 3 | <details><summary>show</summary>Sort; fix `i`, run Two Sum II on `(i+1 .. n-1)` for `-a[i]`; skip duplicate `i` and duplicate `l` after a hit.</details> |
| 11 | [Container With Most Water](https://leetcode.com/problems/container-with-most-water/) | Medium | 3 | <details><summary>show</summary>Area = width × min height. Moving the taller side can never help (width shrinks, min can't grow), so always move the shorter side.</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 88 | [Merge Sorted Array](https://leetcode.com/problems/merge-sorted-array/) | Easy | <details><summary>show</summary>Fill `nums1` from the back with a write pointer at `m + n - 1`; loop while `nums2` still has elements.</details> |
| 42 | [Trapping Rain Water](https://leetcode.com/problems/trapping-rain-water/) | Hard | <details><summary>show</summary>Track `leftMax` and `rightMax`; process the side with the smaller max — its water level is fully determined.</details> |
| 18 | [4Sum](https://leetcode.com/problems/4sum/) | Medium | <details><summary>show</summary>Two nested anchors + two pointers → O(n³); sum in `long` (values up to 10⁹ overflow int).</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Two Pointers**: 125 → 167 → 15 → 11 → 42.
Here (Week 3, before the prefix-sum problems): 344 → 125 → 167 → 15 → 11 → (W20–26) 88, 18, 42. 42 is a Hard; by Week 20 you'll also have monotonic-stack intuition from [06](./06-stack-queue.md).

---

## 9. Target number of problems

**5 new** in Week 3 (+ 3 prefix sums from [04](./04-prefix-sums.md) = the week's 8) + 3 stretch.

---

## 10. Mistakes beginners commonly make

- `while (l <= r)` when comparing pairs → compares an element with itself. Pairs need `l < r`.
- 3Sum: forgetting to skip duplicates → duplicate triplets; or skipping with `a[i] == a[i + 1]` (skips valid triplets like `[-1,-1,2]`). Compare with the **previous** element.
- Inner skip loops without the `l < r` guard → index out of bounds.
- Sorting when you need original indices (LC 1).
- 88: filling from the front → overwrites unread `nums1` values.
- 18/large sums: `a[i] + a[j] + a[k] + a[l]` overflows `int` → cast to `long`.
- Java: `List.of(a, b, c)` in results is fine (immutable), but if you later need to mutate, use `new ArrayList<>(...)`.
- Not being able to **prove** why moving a pointer is safe — interviewers ask.

---

## 11. Mastery criteria

- [ ] Solve 15 3Sum from blank in ≤ 20 min, no duplicates, and explain the duplicate-skip logic.
- [ ] Give the elimination proof for 11 in ≤ 3 sentences.
- [ ] Solve 2 unseen two-pointer Mediums in ≤ 25 min each.
- [ ] Explain when you'd pick hashing (O(n) time, O(n) space) vs sorting + two pointers (O(n log n), O(1)).

---

## 12. Revision schedule

| Review | Early set (344, 125) | Late set (167, 15, 11) |
|---|---|---|
| Day 0 | W3 Mon–Tue | W3 Tue–Thu |
| Day 3 | W3 Thu–Fri | W3 Sun / W4 Mon |
| Day 7 | W4 | W4 |
| Day 14 | W5 | W5 |
| Day 30 | W7 | W7 |

**Revisit:** Week 4 (Checkpoint 4: 15 timed), Week 8 review week (11 timed), Week 16 when sorted-scan thinking returns in intervals, Weeks 20–26 (88, 18, 42).

---

## Worked example — 15. 3Sum

**Clarify.**
- `int[] nums`, 3 ≤ n ≤ 3000, values in [−10⁵, 10⁵]. Return all **unique** triplets (as values) summing to 0. Order of triplets and within triplets doesn't matter.
- `[0,0,0,0]` → `[[0,0,0]]` once.

**Brute force.** Triple loop + `Set<List<Integer>>` of sorted triplets → O(n³) = 2.7·10¹⁰ for n = 3000. Too slow. n ≤ 3000 suggests **O(n²)**.

**Optimise.** Sort (O(n log n)). For each anchor `i`, we need `a[l] + a[r] = -a[i]` in the sorted suffix → Two Sum II in O(n). Total O(n²). Duplicates: skip `i` if `a[i] == a[i-1]`; after recording a triplet, advance `l` past equal values. Early exit: if `a[i] > 0`, no triplet can sum to 0.

**Code.**

```java
import java.util.*;

class ThreeSum {
    public List<List<Integer>> threeSum(int[] nums) {
        Arrays.sort(nums);
        List<List<Integer>> res = new ArrayList<>();
        int n = nums.length;
        for (int i = 0; i < n - 2 && nums[i] <= 0; i++) {
            if (i > 0 && nums[i] == nums[i - 1]) continue;
            int l = i + 1, r = n - 1;
            while (l < r) {
                int sum = nums[i] + nums[l] + nums[r];      // max |sum| = 3·10^5, fits in int
                if (sum < 0) {
                    l++;
                } else if (sum > 0) {
                    r--;
                } else {
                    res.add(Arrays.asList(nums[i], nums[l], nums[r]));
                    l++;
                    r--;
                    while (l < r && nums[l] == nums[l - 1]) l++;
                }
            }
        }
        return res;
    }

    public static void main(String[] args) {
        ThreeSum s = new ThreeSum();
        System.out.println(s.threeSum(new int[]{-1, 0, 1, 2, -1, -4})); // [[-1, -1, 2], [-1, 0, 1]]
        System.out.println(s.threeSum(new int[]{0, 1, 1}));             // []
        System.out.println(s.threeSum(new int[]{0, 0, 0, 0}));          // [[0, 0, 0]]
        System.out.println(s.threeSum(new int[]{-2, 0, 0, 2, 2}));      // [[-2, 0, 2]]
    }
}
```

**Test cases.** Example `[-1,0,1,2,-1,-4]` · no answer `[0,1,1]` · all zeros `[0,0,0,0]` · duplicates on the right `[-2,0,0,2,2]` · all negatives `[-3,-2,-1]` → `[]`.

**Complexity.** Time O(n²) (sort O(n log n) is dominated). Space O(1) extra beyond the output, plus O(log n) for the sort's recursion (Java's dual-pivot quicksort). Mention that we sorted the input in place — clone it if the caller needs the original.
