# 03 — Two Pointers

> **Week 3** · NeetCode section: **Two Pointers** · Target: **5 new problems** + stretch pool · Java rep: Week 3's rep is **560** in [04](./04-prefix-sums.md) (optional extra: 15)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#03--two-pointers) · Python: [cheatsheet](../PYTHON_INTERVIEW_CHEATSHEET.md) · Java: [quick reference](./java-dsa-toolkit.md)

Two indices walk the input in a coordinated way so that every step **discards** candidates you can prove are useless. Result: O(n²) → O(n), often with O(1) extra space.

---

## 1. Concepts to learn

| Concept | Explanation |
|---|---|
| Opposite-direction pointers | `l, r = 0, len(a) - 1`, move inward. Works when the input is **sorted** or **symmetric** (palindromes). |
| Same-direction (read/write, fast/slow) | `read` scans every element; `write` marks where the next kept element goes. In-place compaction. |
| Two inputs, one pointer each | Merge two sorted lists; compare heads, advance the smaller. |
| The elimination argument | In sorted Two Sum: if `a[l] + a[r] < target`, then `a[l]` paired with **any** element ≤ `a[r]` is too small → discard `l`. This proof is what interviewers want to hear. |
| Reduction to 2-pointer | 3Sum = fix one element + Two Sum II on the rest (after sorting). k-Sum = recursion down to 2-Sum. |
| Skipping duplicates | After sorting, `while l < r and a[l] == a[l - 1]: l += 1` to avoid duplicate triplets. |
| Fill from the back | Merging into a list with spare capacity at the end (88) avoids shifting. |
| Pythonic shortcuts (know their cost) | `s == s[::-1]` is O(n) time **and** O(n) space; the two-pointer check is O(1) space. `s.reverse()` on a list is in place. |

---

## 2. Prerequisite knowledge

- [`01-arrays-strings.md`](./01-arrays-strings.md) (in-place reverse, write pointer)
- [`02-hashing.md`](./02-hashing.md) (Two Sum with a dict — compare with the sorted two-pointer version)
- `sorted` / `list.sort` cost O(n log n) — [`00-big-o.md`](./00-big-o.md)

---

## 3. Python implementation

```python
def is_palindrome(s: str) -> bool:
    """Opposite ends, skipping non-alphanumerics (LC 125 style): O(n) time, O(1) space."""
    l, r = 0, len(s) - 1
    while l < r:
        while l < r and not s[l].isalnum():
            l += 1
        while l < r and not s[r].isalnum():
            r -= 1
        if s[l].lower() != s[r].lower():
            return False
        l, r = l + 1, r - 1
    return True


def pair_sum_sorted(a: list[int], target: int) -> list[int]:
    """Sorted input, 1-indexed answer (LC 167 style)."""
    l, r = 0, len(a) - 1
    while l < r:
        s = a[l] + a[r]
        if s == target:
            return [l + 1, r + 1]
        if s < target:
            l += 1          # a[l] is too small for every remaining partner
        else:
            r -= 1          # a[r] is too big for every remaining partner
    return []


def dedupe_sorted(a: list[int]) -> int:
    """Read/write compaction; returns the new logical length."""
    if not a:
        return 0
    w = 1
    for read in range(1, len(a)):
        if a[read] != a[w - 1]:
            a[w] = a[read]
            w += 1
    return w


def merge_into(nums1: list[int], m: int, nums2: list[int], n: int) -> None:
    """LC 88 style: nums1 has m real values followed by n zeros; fill from the back."""
    i, j, w = m - 1, n - 1, m + n - 1
    while j >= 0:           # once nums2 is exhausted, nums1's prefix is already in place
        if i >= 0 and nums1[i] > nums2[j]:
            nums1[w] = nums1[i]
            i -= 1
        else:
            nums1[w] = nums2[j]
            j -= 1
        w -= 1


def three_sum(nums: list[int]) -> list[list[int]]:
    a = sorted(nums)        # sorted() leaves the caller's list untouched
    res: list[list[int]] = []
    for i in range(len(a) - 2):
        if a[i] > 0:
            break                               # smallest is positive → no zero sum
        if i > 0 and a[i] == a[i - 1]:
            continue                            # skip duplicate anchors
        l, r = i + 1, len(a) - 1
        while l < r:
            s = a[i] + a[l] + a[r]
            if s < 0:
                l += 1
            elif s > 0:
                r -= 1
            else:
                res.append([a[i], a[l], a[r]])
                l, r = l + 1, r - 1
                while l < r and a[l] == a[l - 1]:
                    l += 1                      # skip duplicate second elements
    return res


assert is_palindrome("A man, a plan, a canal: Panama") and not is_palindrome("race a car")
assert pair_sum_sorted([2, 7, 11, 15], 9) == [1, 2]
d = [1, 1, 2, 3, 3]
assert dedupe_sorted(d) == 3 and d[:3] == [1, 2, 3]
n1 = [1, 2, 3, 0, 0, 0]
merge_into(n1, 3, [2, 5, 6], 3)
assert n1 == [1, 2, 2, 3, 5, 6]
assert three_sum([-1, 0, 1, 2, -1, -4]) == [[-1, -1, 2], [-1, 0, 1]]
print("two-pointer templates ok")
```

---

## 4. The same in Java (occasional reps)

Week 3's mandatory Java rep is **560** ([04](./04-prefix-sums.md)). Optional extra rep: **15 3Sum** — good practice for `Arrays.sort`, `List.of` and index loops.

| Python | Java |
|---|---|
| `l, r = l + 1, r - 1` | `l++; r--;` |
| `s[l].isalnum()` / `.lower()` | `Character.isLetterOrDigit(c)` / `Character.toLowerCase(c)` |
| `sorted(nums)` (new list) | `int[] a = nums.clone(); Arrays.sort(a);` |
| `res.append([a, b, c])` | `res.add(List.of(a, b, c))` (immutable) or `Arrays.asList(...)` |

```java
import java.util.*;

class ThreeSumRep {
    static List<List<Integer>> threeSum(int[] nums) {
        int[] a = nums.clone();
        Arrays.sort(a);
        List<List<Integer>> res = new ArrayList<>();
        for (int i = 0; i < a.length - 2 && a[i] <= 0; i++) {
            if (i > 0 && a[i] == a[i - 1]) continue;
            int l = i + 1, r = a.length - 1;
            while (l < r) {
                int s = a[i] + a[l] + a[r];
                if (s < 0) l++;
                else if (s > 0) r--;
                else {
                    res.add(List.of(a[i], a[l], a[r]));
                    l++; r--;
                    while (l < r && a[l] == a[l - 1]) l++;
                }
            }
        }
        return res;
    }

    public static void main(String[] args) {
        System.out.println(threeSum(new int[]{-1, 0, 1, 2, -1, -4}));   // [[-1, -1, 2], [-1, 0, 1]]
    }
}
```

Java trap: 4Sum (stretch 18) sums four values up to 10⁹ → overflow; cast to `long`.

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Move rule | Problems |
|---|---|---|
| Converging on symmetric data | Compare ends, move both inward | 344, 125 |
| Converging on sorted sum | Sum too small → `l += 1`; too big → `r -= 1` | 167, 15, 18 |
| Converging on area/width | Move the **shorter** side (it limits the area) | 11 |
| Converging with running max from each side | Water at i = min(maxLeft, maxRight) − h[i]; move the side with the smaller max | 42 |
| Merge two sorted sequences | Advance the smaller head; fill from the back when merging in place | 88, 21 (lists, [08](./08-linked-lists.md)) |
| Read/write compaction | `write` only advances on kept elements | LC 26, 27, 283 style |
| Fast/slow on linked lists | See [08](./08-linked-lists.md) | 141 |

---

## 6. How to recognise the pattern

- Input is **sorted** (or you're allowed to sort) and you're looking for a pair/triplet → converging pointers.
- "In place", "O(1) extra space", "without allocating another array".
- Palindrome, reverse, symmetric.
- "Container", "area between lines", "trap water".
- Two sorted inputs to merge.
- n ≤ 10⁵ and brute force is a double loop over pairs → two pointers gives O(n) after an O(n log n) sort.
- **Counter-signal:** unsorted input and you need original indices (LC 1) → hashing is simpler; sorting destroys indices (unless you sort `(value, index)` pairs).

---

## 7. Beginner problems (Week 3)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 344 | [Reverse String](https://leetcode.com/problems/reverse-string/) | Easy | Swap `s[l], s[r] = s[r], s[l]` moving inward. `s.reverse()` works — but write the loop. |
| 125 | [Valid Palindrome](https://leetcode.com/problems/valid-palindrome/) | Easy | Skip non-alphanumerics with inner `while l < r and ...`; compare `.lower()`. |

---

## 8. Interview problems (Week 3)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 167 | [Two Sum II - Input Array Is Sorted](https://leetcode.com/problems/two-sum-ii-input-array-is-sorted/) | Medium | 3 | <details><summary>show</summary>Converging pointers; sum too small → `l += 1`, too big → `r -= 1`. 1-indexed output. O(1) space beats the dict.</details> |
| 15 | [3Sum](https://leetcode.com/problems/3sum/) | Medium | 3 | <details><summary>show</summary>Sort; fix `i`, run Two Sum II on `i+1..n-1` for `-a[i]`; skip duplicate `i` and duplicate `l` after a hit.</details> |
| 11 | [Container With Most Water](https://leetcode.com/problems/container-with-most-water/) | Medium | 3 | <details><summary>show</summary>Area = width × min height. Moving the taller side can never help (width shrinks, min can't grow), so always move the shorter side.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 88 | [Merge Sorted Array](https://leetcode.com/problems/merge-sorted-array/) | Easy | <details><summary>show</summary>Fill `nums1` from the back with a write pointer at `m + n - 1`; loop while `nums2` still has elements.</details> |
| 18 | [4Sum](https://leetcode.com/problems/4sum/) | Medium | <details><summary>show</summary>Two nested anchors + two pointers → O(n³), with duplicate skipping at each level.</details> |
| 42 | [Trapping Rain Water](https://leetcode.com/problems/trapping-rain-water/) | Hard | <details><summary>show</summary>Track `left_max` and `right_max`; process the side with the smaller max — its water level is fully determined.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Two Pointers**: 125 → 167 → 15 → 11 → 42.
Here (Week 3, before the prefix-sum problems): 344 → 125 → 167 → 15 → 11 → (W20–26) 88, 18, 42. 42 is a Hard; by Week 20 you'll also have monotonic-stack intuition from [06](./06-stack-queue.md).

---

## 10. Target number of problems

**5 new** in Week 3 (+ 3 prefix sums from [04](./04-prefix-sums.md) = the week's 8) + 3 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- `nums.sort()` inside the function mutates the caller's list; `sorted(nums)` doesn't — know which one the problem allows.
- `s[::-1] == s` is fine as a first answer, but it's O(n) extra space; interviewers ask for O(1).
- 344: `s = s[::-1]` rebinds a local name — LeetCode checks the original list. Use in-place swaps or `s[:] = s[::-1]`.
- `str.isalnum()` also accepts Unicode letters/digits (fine for LeetCode); `isalpha()` would wrongly drop digits.
- `while l < r:` forgetting to update both pointers in the "match" branch → infinite loop.

**General**
- `while l <= r` when comparing pairs → compares an element with itself. Pairs need `l < r`.
- 3Sum: forgetting to skip duplicates → duplicate triplets; or skipping with `a[i] == a[i + 1]` (skips valid triplets like `[-1,-1,2]`). Compare with the **previous** element.
- Inner skip loops without the `l < r` guard → `IndexError`.
- Sorting when you need original indices (LC 1).
- 88: filling from the front → overwrites unread values.
- Not being able to **prove** why moving a pointer is safe — interviewers ask.

**Java-rep traps (15/18):** `List.of` results are immutable; 4Sum needs `long`.

---

## 12. Mastery criteria

- [ ] Solve 15 3Sum from blank in ≤ 20 min, no duplicates, and explain the duplicate-skip logic.
- [ ] Give the elimination proof for 11 in ≤ 3 sentences.
- [ ] Solve 2 unseen two-pointer Mediums in ≤ 25 min each.
- [ ] Explain when you'd pick hashing (O(n) time, O(n) space) vs sorting + two pointers (O(n log n), O(1) extra).

---

## 13. Revision schedule

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
- `nums: list[int]`, 3 ≤ n ≤ 3000, values in [−10⁵, 10⁵]. Return all **unique** triplets (as values) summing to 0. Order of triplets and within triplets doesn't matter.
- `[0,0,0,0]` → `[[0,0,0]]` once.

**Brute force.** Triple loop + a `set` of sorted tuples → O(n³) = 2.7·10¹⁰ for n = 3000. Impossible in Python. n ≤ 3000 suggests **O(n²)** (9·10⁶ — fine).

**Optimise.** Sort (O(n log n)). For each anchor `i`, we need `a[l] + a[r] == -a[i]` in the sorted suffix → Two Sum II in O(n). Total O(n²). Duplicates: skip `i` if `a[i] == a[i-1]`; after recording a triplet, advance `l` past equal values. Early exit: if `a[i] > 0`, no triplet can sum to 0.

**Code (Python).**

```python
def three_sum(nums: list[int]) -> list[list[int]]:
    nums = sorted(nums)
    n = len(nums)
    res: list[list[int]] = []
    for i in range(n - 2):
        if nums[i] > 0:
            break
        if i > 0 and nums[i] == nums[i - 1]:
            continue
        l, r = i + 1, n - 1
        while l < r:
            total = nums[i] + nums[l] + nums[r]
            if total < 0:
                l += 1
            elif total > 0:
                r -= 1
            else:
                res.append([nums[i], nums[l], nums[r]])
                l += 1
                r -= 1
                while l < r and nums[l] == nums[l - 1]:
                    l += 1
    return res


assert three_sum([-1, 0, 1, 2, -1, -4]) == [[-1, -1, 2], [-1, 0, 1]]
assert three_sum([0, 1, 1]) == []
assert three_sum([0, 0, 0, 0]) == [[0, 0, 0]]
assert three_sum([-2, 0, 0, 2, 2]) == [[-2, 0, 2]]
assert three_sum([-3, -2, -1]) == []
print("15 worked example passed")
```

**Test cases.** Example `[-1,0,1,2,-1,-4]` · no answer `[0,1,1]` · all zeros `[0,0,0,0]` · duplicates on the right `[-2,0,0,2,2]` · all negatives `[-3,-2,-1]` → `[]`.

**Complexity.** Time O(n²) (the O(n log n) sort is dominated). Space O(n) for the sorted copy (O(1) extra if you're allowed to sort in place), excluding output.
