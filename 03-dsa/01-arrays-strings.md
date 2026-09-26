# 01 — Arrays & Strings

> **Weeks 1–2** · NeetCode section: **Arrays & Hashing** · Target: **7 new problems** (W1: 6, W2: 1)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#01--arrays--strings) · Java APIs: [`java-dsa-toolkit.md`](./java-dsa-toolkit.md)

Arrays and strings are the input format of most interview problems. This guide is about **indexing fluently**, building results efficiently, and knowing what Java does under the hood (`String` immutability, `StringBuilder`, `char` arithmetic, dynamic arrays).

---

## 1. Concepts to learn

| Concept | Key facts | Interview angle |
|---|---|---|
| Static array | Contiguous memory, O(1) index access via `base + i * size`, fixed length | "Why is array access O(1) but linked-list access O(n)?" |
| Dynamic array (`ArrayList`) | Backed by an array; grows ×1.5 when full; `add` amortised O(1); middle insert/delete O(n) | "Explain amortised O(1)." |
| 2-D arrays | Array of arrays in Java (rows can differ in length); `grid[r][c]`; row-major traversal is cache-friendly | Matrix traversal, rotation, spiral |
| In-place algorithms | Modify input with O(1) extra space: reverse, rotate, overwrite with a write pointer | "Can you do it in O(1) extra space?" |
| `String` | Immutable, `char[]`/`byte[]` inside, `charAt` O(1), `substring` O(k) copy | Why `+=` in a loop is O(n²) |
| `StringBuilder` | Mutable, amortised O(1) `append`, `reverse`, `setLength` | Building answers, backtracking paths |
| Character encoding | `char` is a 16-bit UTF-16 unit; `'a'..'z'` are contiguous → `c - 'a'` ∈ [0, 25] | Frequency arrays `int[26]` |
| Prefix/suffix thinking | Precompute info from the left and from the right, combine | 238 Product of Array Except Self |
| Boyer–Moore voting | Majority element in O(n) time, O(1) space | 169 Majority Element |

---

## 2. Prerequisite knowledge

- [`00-big-o.md`](./00-big-o.md) — you must state complexity for everything here.
- Java syntax, loops, arrays, `String` API — [`01-java/01-syntax-basics.md`](../01-java/01-syntax-basics.md).
- Toolkit §1–§3: [Arrays](./java-dsa-toolkit.md#1-arrays), [Strings](./java-dsa-toolkit.md#2-strings-stringbuilder-and-char-arithmetic), [Character](./java-dsa-toolkit.md#3-character-methods).

---

## 3. Java implementation

### 3.1 A dynamic array from scratch (step 2 of the loop)

```java
import java.util.*;

class IntArrayList {
    private int[] data = new int[4];
    private int size = 0;

    void add(int x) {                          // amortised O(1)
        if (size == data.length) data = Arrays.copyOf(data, data.length * 2);   // O(n) occasionally
        data[size++] = x;
    }

    int get(int i) {                           // O(1)
        Objects.checkIndex(i, size);
        return data[i];
    }

    void insert(int i, int x) {                // O(n): shift right
        Objects.checkIndex(i, size + 1);
        if (size == data.length) data = Arrays.copyOf(data, data.length * 2);
        System.arraycopy(data, i, data, i + 1, size - i);
        data[i] = x;
        size++;
    }

    int removeAt(int i) {                      // O(n): shift left
        Objects.checkIndex(i, size);
        int old = data[i];
        System.arraycopy(data, i + 1, data, i, size - i - 1);
        size--;
        return old;
    }

    int size() { return size; }

    @Override public String toString() { return Arrays.toString(Arrays.copyOf(data, size)); }

    public static void main(String[] args) {
        IntArrayList a = new IntArrayList();
        for (int i = 0; i < 10; i++) a.add(i);
        a.insert(0, -1);
        a.removeAt(5);
        System.out.println(a + " size=" + a.size());   // [-1, 0, 1, 2, 3, 5, 6, 7, 8, 9] size=10
    }
}
```

### 3.2 Core array/string templates

```java
import java.util.*;

class ArrayTemplates {
    // Reverse a range in place — building block for rotate (189)
    static void reverse(int[] a, int l, int r) {
        while (l < r) { int t = a[l]; a[l++] = a[r]; a[r--] = t; }
    }

    // Rotate right by k in O(n) time, O(1) space: reverse all, reverse first k, reverse rest
    static void rotate(int[] a, int k) {
        int n = a.length;
        k %= n;                                 // k can exceed n
        reverse(a, 0, n - 1);
        reverse(a, 0, k - 1);
        reverse(a, k, n - 1);
    }

    // Frequency count of lowercase letters
    static int[] freq(String s) {
        int[] f = new int[26];
        for (int i = 0; i < s.length(); i++) f[s.charAt(i) - 'a']++;
        return f;
    }

    // Write-pointer compaction: keep elements != val, return new length (LC 27 style)
    static int removeValue(int[] a, int val) {
        int w = 0;
        for (int x : a) if (x != val) a[w++] = x;
        return w;
    }

    // Prefix/suffix products without division (LC 238 core idea)
    static int[] productExceptSelf(int[] a) {
        int n = a.length;
        int[] out = new int[n];
        out[0] = 1;
        for (int i = 1; i < n; i++) out[i] = out[i - 1] * a[i - 1];     // product of everything left of i
        int right = 1;
        for (int i = n - 1; i >= 0; i--) { out[i] *= right; right *= a[i]; }
        return out;
    }

    // Boyer–Moore majority vote (LC 169)
    static int majority(int[] a) {
        int cand = 0, count = 0;
        for (int x : a) {
            if (count == 0) cand = x;
            count += (x == cand) ? 1 : -1;
        }
        return cand;                            // guaranteed to exist per problem statement
    }

    // Matrix traversal: transpose then reverse rows = rotate 90° clockwise
    static void rotateMatrix(int[][] m) {
        int n = m.length;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) { int t = m[i][j]; m[i][j] = m[j][i]; m[j][i] = t; }
        for (int[] row : m) reverse(row, 0, n - 1);
    }

    // Build a string efficiently
    static String interleave(String a, String b) {
        StringBuilder sb = new StringBuilder(a.length() + b.length());
        int i = 0;
        while (i < a.length() || i < b.length()) {
            if (i < a.length()) sb.append(a.charAt(i));
            if (i < b.length()) sb.append(b.charAt(i));
            i++;
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        int[] a = {1, 2, 3, 4, 5, 6, 7};
        rotate(a, 3);
        System.out.println(Arrays.toString(a));                                // [5, 6, 7, 1, 2, 3, 4]
        System.out.println(freq("banana")[0]);                                 // 3
        System.out.println(Arrays.toString(productExceptSelf(new int[]{1, 2, 3, 4}))); // [24, 12, 8, 6]
        System.out.println(majority(new int[]{2, 2, 1, 1, 1, 2, 2}));          // 2
        int[][] m = {{1, 2}, {3, 4}};
        rotateMatrix(m);
        System.out.println(Arrays.deepToString(m));                            // [[3, 1], [4, 2]]
        System.out.println(interleave("abc", "pqrs"));                          // apbqcrs
        System.out.println(removeValue(new int[]{3, 2, 2, 3}, 3));              // 2
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Idea | Example |
|---|---|---|
| Single pass with running state | Track max/min/count as you go | 121 (sliding-window file), 169 |
| Write pointer / compaction | One index reads, one writes | 27, 283 |
| Build output with `StringBuilder` | Never `+=` in loops | 1768, 14 |
| Scan from the end | Avoid shifting; skip trailing noise | 58 |
| Prefix / suffix precomputation | Info from left × info from right | 238 |
| Reverse tricks | Rotate = three reversals | 189 |
| Vertical scanning | Compare column by column across strings | 14 |
| Counting array `int[26]` / `int[128]` | Replace hash maps for small alphabets | anagram-style problems |
| Matrix manipulation | Transpose, reverse, layer-by-layer | rotate image, spiral order |

---

## 5. How to recognise the pattern

- Input is an array/string and the answer depends on **positions**, not just membership → array technique (otherwise think hashing, [02](./02-hashing.md)).
- "In-place", "O(1) extra space" → reversal tricks, write pointer, or reuse the output array.
- "Without using division" → prefix/suffix products.
- "Appears more than ⌊n/2⌋ times" → Boyer–Moore voting.
- n ≤ 10⁵ → O(n) or O(n log n); a double loop is too slow.
- Strings with only lowercase letters → `int[26]`.

---

## 6. Beginner problems (Week 1)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 1929 | [Concatenation of Array](https://leetcode.com/problems/concatenation-of-array/) | Easy | `ans[i] = ans[i + n] = nums[i]`; allocate `new int[2 * n]`. |
| 1768 | [Merge Strings Alternately](https://leetcode.com/problems/merge-strings-alternately/) | Easy | One index, `StringBuilder`, append whichever strings still have chars. |
| 58 | [Length of Last Word](https://leetcode.com/problems/length-of-last-word/) | Easy | Scan from the end: skip spaces, then count letters. |
| 14 | [Longest Common Prefix](https://leetcode.com/problems/longest-common-prefix/) | Easy | Vertical scan: for each column `i`, check every string's `charAt(i)`. |

---

## 7. Interview problems (Weeks 1–2)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 169 | [Majority Element](https://leetcode.com/problems/majority-element/) | Easy | 1 | <details><summary>show</summary>Boyer–Moore: a candidate with a counter; majority survives all cancellations. O(n)/O(1).</details> |
| 238 | [Product of Array Except Self](https://leetcode.com/problems/product-of-array-except-self/) | Medium | 2 | <details><summary>show</summary>`answer[i]` = (product left of i) × (product right of i); do the right pass with a running variable to get O(1) extra space.</details> |
| 189 | [Rotate Array](https://leetcode.com/problems/rotate-array/) | Medium | 1 | <details><summary>show</summary>Reverse whole array, then reverse first k, then the rest. Remember `k %= n`.</details> |

No stretch pool for this pattern — Arrays reappear inside every later pattern.

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 section **Arrays & Hashing** (this guide covers the array-manipulation half; the hashing half is [02](./02-hashing.md)).

1. Implement `IntArrayList` (§3.1) from memory.
2. 1929 → 1768 → 58 → 14 → 169 → 189 (Week 1).
3. 238 (Week 2, first problem of the week). 238 is the only NeetCode 150 problem here; it then leads straight into the hashing half of the section.

---

## 9. Target number of problems

**7 new** — 6 in Week 1, 1 in Week 2 (matches [ROADMAP §5](../ROADMAP.md#5-week-by-week-master-table): Week 1 = 6; Week 2's 8 = 1 here + 7 in [02](./02-hashing.md)).

---

## 10. Mistakes beginners commonly make

- **Off-by-one**: `i <= a.length` → `ArrayIndexOutOfBoundsException`. Loop bounds: `i < a.length`.
- `a.length` (array field) vs `s.length()` (String method) vs `list.size()` — mixing them is a compile error in interviews.
- `result += c` in a loop → O(n²). Use `StringBuilder`.
- `new String(chars)` vs `chars.toString()` — the latter prints `[C@1b6d3586`.
- `'a' + 1` is the `int` 98; you need `(char) ('a' + 1)`.
- Forgetting `k %= n` in rotation → index out of bounds or wasted work.
- Comparing strings with `==`.
- Modifying the input array when the problem (or interviewer) expects it to stay unchanged — ask first.
- Using `Arrays.asList(intArray)` expecting a `List<Integer>` (you get `List<int[]>`).
- Returning a local array reference when a copy was expected (aliasing).

---

## 11. Mastery criteria

- [ ] Implement `IntArrayList` with `add`, `get`, `insert`, `removeAt` from memory in ≤ 15 min, and explain amortised growth.
- [ ] Solve 238 from a blank editor in ≤ 20 min with O(1) extra space (output array excluded) and explain why division fails with zeros.
- [ ] Solve 2 unseen Easy array/string problems in ≤ 12 min each.
- [ ] State time/space for every solution above without hesitation.

---

## 12. Revision schedule

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
- Input `int[] nums`, 2 ≤ n ≤ 10⁵, values in [−30, 30]; product of any prefix/suffix fits in 32-bit int (guaranteed).
- Output `answer[i]` = product of all elements except `nums[i]`. **No division.** O(n) time.
- Zeros? Yes, allowed — one more reason division is out.

**Brute force.** For each i, multiply all j ≠ i → O(n²) time, O(1) extra. n = 10⁵ → 10¹⁰ ops. Too slow.

**Optimise.** `answer[i] = left[i] * right[i]` where `left[i]` = product of `nums[0..i-1]` and `right[i]` = product of `nums[i+1..n-1]`. Two arrays → O(n) time, O(n) space. Improvement: store `left` directly in the output array, then sweep right-to-left multiplying by a running `right` → O(1) extra space.

**Code.**

```java
import java.util.*;

class ProductExceptSelf {
    public int[] productExceptSelf(int[] nums) {
        int n = nums.length;
        int[] answer = new int[n];
        answer[0] = 1;                                   // nothing to the left of index 0
        for (int i = 1; i < n; i++) {
            answer[i] = answer[i - 1] * nums[i - 1];     // prefix product
        }
        int right = 1;                                   // product of everything to the right of i
        for (int i = n - 1; i >= 0; i--) {
            answer[i] *= right;
            right *= nums[i];
        }
        return answer;
    }

    public static void main(String[] args) {
        ProductExceptSelf s = new ProductExceptSelf();
        System.out.println(Arrays.toString(s.productExceptSelf(new int[]{1, 2, 3, 4})));      // [24, 12, 8, 6]
        System.out.println(Arrays.toString(s.productExceptSelf(new int[]{-1, 1, 0, -3, 3}))); // [0, 0, 9, 0, 0]
        System.out.println(Arrays.toString(s.productExceptSelf(new int[]{2, 3})));            // [3, 2]
    }
}
```

**Test cases.**

| Input | Expected | Why |
|---|---|---|
| `[1,2,3,4]` | `[24,12,8,6]` | Normal case |
| `[-1,1,0,-3,3]` | `[0,0,9,0,0]` | Single zero: only the zero's slot is non-zero |
| `[0,0,2]` | `[0,0,0]` | Two zeros: everything is 0 |
| `[2,3]` | `[3,2]` | Minimum length |

**Complexity.** Time O(n) (two passes). Space O(1) extra — the output array doesn't count by the problem's convention; say so explicitly.
