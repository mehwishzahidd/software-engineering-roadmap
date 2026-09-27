# 23 — Bit Manipulation (basics)

> **Week 19** · NeetCode section: **Bit Manipulation** · Target: **3 new problems** + stretch pool · Java rep: Week 19's rep is **743** in [22](./22-advanced-graphs.md) (optional extra: 191 — Java's fixed-width ints make it a different exercise)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#23--bit-manipulation) · Python: [cheatsheet](../PYTHON_INTERVIEW_CHEATSHEET.md) · Java: [quick reference §5](./java-dsa-toolkit.md#5-overflow-integer-caching-and-other-traps)

Integers are bit strings; bitwise operators work on all bits at once in O(1). A handful of identities — XOR cancels pairs, `n & (n - 1)` clears the lowest set bit, masks select bits — solve a family of "O(1) extra space" problems and appear in hashing (FlagForge's percentage-rollout bucketing hashes a key and takes it modulo 10 000), permissions bitmasks, and network masks. **Python-specific twist:** Python `int`s are unbounded (two's complement with infinitely many sign bits), so 32-bit tricks need explicit masking.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Operators | `&` AND, `\|` OR, `^` XOR, `~` NOT, `<<` left shift, `>>` right shift (arithmetic in Python: sign-extends) |
| XOR identities | `x ^ x = 0`, `x ^ 0 = x`, commutative and associative → pairs cancel (136, 268) |
| Lowest set bit | `n & (n - 1)` clears it; `n & -n` isolates it |
| Power of two | `n > 0 and n & (n - 1) == 0` (231 from [09](./09-recursion.md)) |
| Get / set / clear / toggle bit i | `(n >> i) & 1`, `n \| (1 << i)`, `n & ~(1 << i)`, `n ^ (1 << i)` |
| Counting bits | Kernighan loop (`while n: n &= n - 1`), `bin(n).count("1")`, `int.bit_count()` (3.10+) |
| DP over bits | `bits[i] = bits[i >> 1] + (i & 1)` (338) |
| Two's complement | −x = ~x + 1. In 32-bit: range [−2³¹, 2³¹ − 1] |
| Python unbounded ints | `-1` has infinitely many 1-bits → `bin(-1)` is `'-0b1'`; a naive `while n:` loop on a negative number never ends. Use `n & 0xFFFFFFFF` to view 32 bits |
| Converting back to signed 32-bit | `x if x < 2**31 else x - 2**32` (371) |
| Bitmask as a set | Subset of ≤ 20 items as an int; iterate all subsets `for mask in range(1 << n)` |
| Shifts as ×2 / ÷2 | `x << 1 == 2 * x`, `x >> 1 == x // 2` (floor, also for negatives in Python) |

---

## 2. Prerequisite knowledge

- [`00-big-o.md`](./00-big-o.md) — O(1) per operation on fixed-width ints; O(log n) bits in n.
- [`09-recursion.md`](./09-recursion.md) — 231 Power of Two.
- [`14-backtracking.md`](./14-backtracking.md) — bitmask enumeration is the iterative twin of subsets.

---

## 3. Python implementation

```python
MASK32 = 0xFFFFFFFF


def single_number(nums: list[int]) -> int:
    x = 0
    for n in nums:
        x ^= n                                  # pairs cancel; the lone value survives
    return x


def count_ones(n: int) -> int:                  # Kernighan: iterations = number of set bits
    n &= MASK32                                 # treat as unsigned 32-bit (safe for negatives)
    c = 0
    while n:
        n &= n - 1
        c += 1
    return c


def count_bits(n: int) -> list[int]:            # LC 338: DP over bits, O(n)
    bits = [0] * (n + 1)
    for i in range(1, n + 1):
        bits[i] = bits[i >> 1] + (i & 1)
    return bits


def missing_number(nums: list[int]) -> int:     # XOR indices 0..n with values
    x = len(nums)
    for i, v in enumerate(nums):
        x ^= i ^ v
    return x


def get_sum(a: int, b: int) -> int:
    """LC 371 in Python: simulate 32-bit add with masking, then convert back to signed."""
    a, b = a & MASK32, b & MASK32
    while b:
        carry = ((a & b) << 1) & MASK32
        a = (a ^ b) & MASK32
        b = carry
    return a if a < 2**31 else a - 2**32


def reverse_bits(n: int) -> int:                # LC 190 shape (32-bit unsigned)
    out = 0
    for _ in range(32):
        out = (out << 1) | (n & 1)
        n >>= 1
    return out


def subsets_by_mask(items: list[str]) -> list[list[str]]:
    n = len(items)
    return [[items[i] for i in range(n) if mask >> i & 1] for mask in range(1 << n)]


assert single_number([4, 1, 2, 1, 2]) == 4
assert count_ones(11) == 3 and count_ones(-1) == 32 and (11).bit_count() == 3
assert count_bits(5) == [0, 1, 1, 2, 1, 2]
assert missing_number([3, 0, 1]) == 2 and missing_number([0]) == 1
assert get_sum(1, 2) == 3 and get_sum(-2, 3) == 1 and get_sum(-5, -7) == -12
assert reverse_bits(0b00000010100101000001111010011100) == 964176192
assert subsets_by_mask(["a", "b"]) == [[], ["a"], ["b"], ["a", "b"]]
assert (8 & 7) == 0 and (12 & -12) == 4          # power-of-two test; isolate lowest set bit
assert -7 >> 1 == -4                             # arithmetic shift floors
print("bit templates ok")
```

---

## 4. The same in Java (occasional reps)

Week 19's mandatory Java rep is **743** ([22](./22-advanced-graphs.md)). Optional extra: **191 Number of 1 Bits** — in Java the input is a signed 32-bit `int`, so you need `>>>` (unsigned shift) or Kernighan.

| Python | Java |
|---|---|
| unbounded `int`, mask with `& 0xFFFFFFFF` | fixed 32-bit `int` / 64-bit `long`; overflow wraps silently |
| `>>` arithmetic shift | `>>` arithmetic, `>>>` **logical** (zero-fill) — Python has no `>>>` |
| `n.bit_count()` | `Integer.bitCount(n)` |
| `bin(n)` | `Integer.toBinaryString(n)` (shows 32 bits for negatives) |

```java
class HammingWeightRep {
    static int hammingWeight(int n) {
        int count = 0;
        while (n != 0) {
            n &= n - 1;            // Kernighan works for negatives too: at most 32 iterations
            count++;
        }
        return count;
    }

    public static void main(String[] args) {
        System.out.println(hammingWeight(11) + " " + hammingWeight(-1) + " " + Integer.bitCount(-1) + " " + (-8 >>> 28)); // 3 32 32 15
    }
}
```

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Trick | Problems |
|---|---|---|
| Find the unpaired element | XOR everything | 136 |
| Count set bits | Kernighan / mask 32 bits | 191 |
| Count bits for 0..n | `bits[i >> 1] + (i & 1)` | 338 |
| Missing number in 0..n | XOR indices and values (or Gauss sum) | 268 |
| Add without `+` | XOR = sum without carry, AND << 1 = carry; loop until no carry (mask in Python) | 371 |
| Power of two | `n & (n - 1) == 0` | 231 ([09](./09-recursion.md)) |
| Enumerate subsets | `for mask in range(1 << n)` | alternative to [14](./14-backtracking.md) |

---

## 6. How to recognise the pattern

- "Every element appears twice except one", "find the missing/duplicate number" with **O(1) extra space**.
- "Without using `+` / `*`", "reverse bits", "number of 1 bits", "power of two/four".
- n ≤ 20 items and you need all subsets or a DP over subsets → bitmask.
- Input described as a "32-bit unsigned integer" or "binary representation".

---

## 7. Beginner problems (Week 19)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 136 | [Single Number](https://leetcode.com/problems/single-number/) | Easy | XOR all numbers. (`functools.reduce(operator.xor, nums)` one-liner — write the loop first.) |
| 191 | [Number of 1 Bits](https://leetcode.com/problems/number-of-1-bits/) | Easy | Kernighan's `n &= n - 1`, or `n.bit_count()`; explain why a naive shift loop would never end for a negative Python int. |

---

## 8. Interview problems (Week 19)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 338 | [Counting Bits](https://leetcode.com/problems/counting-bits/) | Easy | 19 | <details><summary>show</summary>`bits[i] = bits[i >> 1] + (i & 1)` — i without its last bit was already computed → O(n) total.</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 268 | [Missing Number](https://leetcode.com/problems/missing-number/) | Easy | <details><summary>show</summary>XOR all indices 0..n with all values; or `n*(n+1)//2 - sum(nums)`.</details> |
| 371 | [Sum of Two Integers](https://leetcode.com/problems/sum-of-two-integers/) | Medium | <details><summary>show</summary>XOR + carry loop; in Python mask every step with `0xFFFFFFFF` and convert back to signed at the end — otherwise negative inputs loop forever.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Bit Manipulation**: 136 → 191 → 338 → 190 → 268 → 371 → 7. (190 Reverse Bits and 7 Reverse Integer are optional, not tracked — `reverse_bits` in §3 covers 190's idea.)
Here: §3 → 136 → 191 → 338 (Week 19, after [22](./22-advanced-graphs.md)) → (W20–26) 268, 371.

---

## 10. Target number of problems

**3 new** in Week 19 (+ 4 advanced graphs = 7) + 2 stretch.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Forgetting ints are unbounded: `while n: n >>= 1` never terminates for negative `n` (`-1 >> 1 == -1`). Mask with `& 0xFFFFFFFF` first.
- 371 without masking → infinite loop on negative inputs; forgetting to convert the 32-bit result back to a signed int.
- `bin(n)` of a negative number is `'-0b…'`, not a two's-complement string — don't count characters in it for negatives.
- Operator precedence: arithmetic binds tighter than shifts, which bind tighter than `&`: `1 << n - 1` is `1 << (n - 1)`, and `ans[i >> 1] + i & 1` would be `(ans[i >> 1] + i) & 1`. Parenthesise. (`n & 1 == 0` happens to work in Python because `&` binds tighter than `==` — in Java it's a compile error, so write `(n & 1) == 0` in both.)
- `~x` is `-x - 1` in Python (not a 32-bit flip) — mask if you need unsigned semantics.
- `/` instead of `//` when halving; `>> 1` floors for negatives (`-7 >> 1 == -4`).

**General**
- Using extra space (a set/Counter) when the problem demands O(1) — correct but fails the follow-up.
- Off-by-one in bit positions (bit 0 is the least significant).

**Java-rep traps (191):** `>>` vs `>>>` on negative ints; `Integer.MIN_VALUE` edge cases; `1 << 31` is negative.

---

## 12. Mastery criteria

- [ ] Recite and justify: XOR cancellation, `n & (n - 1)`, `n & -n`, get/set/clear/toggle bit i.
- [ ] Solve 136, 191, 338 in ≤ 8 min each from blank.
- [ ] Explain why Python needs `& 0xFFFFFFFF` for 32-bit problems and how to convert back to signed.
- [ ] Enumerate all subsets of a 4-element list with a bitmask loop without looking.

---

## 13. Revision schedule

| Review | Week 19 set (136, 191, 338) |
|---|---|
| Day 0 | W19 |
| Day 3 | W19/W20 |
| Day 7 | W20 |
| Day 14 | W21 (FlagForge M2 bucketing: explain hash → bucket with `%`) |
| Day 30 | W23 |

**Revisit:** Week 21 (FlagForge deterministic bucketing — hashing and modulo), Weeks 20–26 (268, 371), OA sims (bit tricks appear as "Easy warm-ups").

---

## Worked example — 338. Counting Bits

**Clarify.** Given `n` (0 ≤ n ≤ 10⁵), return a list `ans` of length n + 1 where `ans[i]` is the number of 1-bits in `i`. Follow-up: O(n) time, without a built-in popcount per number.

**Brute force.** For each i, count bits with Kernighan or `bin(i).count("1")` → O(n log n) (≤ 17 bits each for 10⁵). Fine, but misses the follow-up.

**Optimise.** `i >> 1` is i without its lowest bit, and it's smaller than i, so its answer is already known. The dropped bit is `i & 1`. Recurrence: `ans[i] = ans[i >> 1] + (i & 1)`. Alternative: `ans[i] = ans[i & (i - 1)] + 1` (drop the lowest **set** bit). Both O(n).

**Code (Python).**

```python
def count_bits(n: int) -> list[int]:
    ans = [0] * (n + 1)
    for i in range(1, n + 1):
        ans[i] = ans[i >> 1] + (i & 1)          # parentheses matter: + binds tighter than &
    return ans


def count_bits_lowest_set(n: int) -> list[int]:
    ans = [0] * (n + 1)
    for i in range(1, n + 1):
        ans[i] = ans[i & (i - 1)] + 1
    return ans


for f in (count_bits, count_bits_lowest_set):
    assert f(0) == [0]
    assert f(2) == [0, 1, 1]
    assert f(5) == [0, 1, 1, 2, 1, 2]
    assert f(1000) == [bin(i).count("1") for i in range(1001)]   # cross-check with a brute force
print("338 worked example passed")
```

**Test cases.** `n = 0` → `[0]` · `n = 2` → `[0,1,1]` · `n = 5` → `[0,1,1,2,1,2]` · large n cross-checked against a brute force (good habit: property-style testing — the same idea as LedgerX's invariant tests).

**Complexity.** Time O(n). Space O(n) for the output (O(1) extra).
