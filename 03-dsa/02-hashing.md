# 02 — HashMap / HashSet (Python `dict` / `set`)

> **Week 2** · NeetCode section: **Arrays & Hashing** · Target: **7 new problems** + stretch pool · Java rep: **49** (Week 2)
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#02--hashmap--hashset) · Python: [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md) (`Counter`, `defaultdict`) · Java: [quick reference](./java-dsa-toolkit.md)

Hashing turns "search" from O(n) into O(1) average. It is the single most common optimisation in interviews: *"the brute force is O(n²) because of the inner search — replace the inner loop with a hash lookup."* Week 2 is also when you learn the Python interview toolkit (`Counter`, `defaultdict`) and Java's `HashMap` internals ([`01-java/03-collections-generics.md`](../01-java/03-collections-generics.md)) — study them together.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Hash function | Maps a key to an int; the table index is derived from it. Python: `hash(x)`; Java: `hashCode()` |
| Hashable keys | Python keys must be **immutable & hashable**: `int`, `str`, `tuple` of hashables, `frozenset`. `list`, `dict`, `set` are unhashable → `TypeError` |
| `__eq__` / `__hash__` contract | Equal objects must hash equal. `@dataclass(frozen=True)` generates both. (Java: `equals`/`hashCode`, records) |
| Collisions | CPython `dict`/`set` use **open addressing**; Java `HashMap` uses **separate chaining** (list → red-black tree at 8 per bucket) |
| Resizing | Tables grow when ~2/3 full (CPython) / 0.75 (Java); rehash O(n) but amortised O(1) per insert |
| Complexity | Average O(1) get/set/delete/`in`; worst case O(n) |
| Ordering | Python `dict` preserves **insertion order** (3.7+); `set` has no order. Java `HashMap` has none (use `LinkedHashMap`) |
| `Counter` | `Counter(iterable)` counts in O(n); missing keys read as 0; `most_common(k)`; supports `+`, `-`, `==` |
| `defaultdict` | `defaultdict(list)` / `defaultdict(int)` creates missing values on access — great for grouping |
| Canonical keys | Group equivalent items under one key: sorted string, `tuple` of 26 counts, `(r, c)` tuple |
| Complement lookup | For each `x`, ask "have I seen `target - x`?" |
| Frequency buckets | Bucket sort by count: `buckets = [[] for _ in range(n + 1)]` for top-K in O(n) |

**Practical use:** in [FlowGrid](../18-projects/flowgrid/README.md) you group inventory levels by warehouse and its `Idempotency-Key` store is a hash lookup ("have I seen this key before?") — the same patterns as 49 and 217, implemented in Java and moved into PostgreSQL/Redis.

---

## 2. Prerequisite knowledge

- [`00-big-o.md`](./00-big-o.md), [`01-arrays-strings.md`](./01-arrays-strings.md)
- Python dict/set basics, `Counter`, `defaultdict` — [`19-python/02-interview-toolkit.md`](../19-python/02-interview-toolkit.md)
- Mutability and hashability — [`19-python/03-pitfalls-and-complexity.md`](../19-python/03-pitfalls-and-complexity.md)

---

## 3. Python implementation

### 3.1 A hash map from scratch (separate chaining)

```python
class MyHashMap:
    """Separate chaining with resize at load factor 0.75 (the Java HashMap design)."""

    def __init__(self, capacity: int = 8) -> None:
        self._buckets: list[list[list]] = [[] for _ in range(capacity)]   # each entry: [key, value]
        self._size = 0

    def _bucket(self, key) -> list[list]:
        return self._buckets[hash(key) % len(self._buckets)]

    def get(self, key, default=None):
        for k, v in self._bucket(key):
            if k == key:
                return v
        return default

    def put(self, key, value) -> None:
        bucket = self._bucket(key)
        for entry in bucket:
            if entry[0] == key:
                entry[1] = value              # update in place
                return
        bucket.append([key, value])
        self._size += 1
        if self._size > 0.75 * len(self._buckets):
            self._resize()

    def remove(self, key) -> bool:
        bucket = self._bucket(key)
        for i, (k, _) in enumerate(bucket):
            if k == key:
                bucket[i] = bucket[-1]        # swap with last, pop: O(1)
                bucket.pop()
                self._size -= 1
                return True
        return False

    def _resize(self) -> None:
        old = self._buckets
        self._buckets = [[] for _ in range(2 * len(old))]
        for bucket in old:
            for k, v in bucket:
                self._bucket(k).append([k, v])

    def __len__(self) -> int:
        return self._size


m = MyHashMap()
for i in range(100):
    m.put(f"k{i}", i)
m.put("k5", 500)
assert m.remove("k7") and not m.remove("missing")
assert m.get("k5") == 500 and m.get("k7") is None and len(m) == 99
print("MyHashMap ok")
```

### 3.2 Hashing templates

```python
from collections import Counter, defaultdict


def two_sum(nums: list[int], target: int) -> list[int]:
    index_of: dict[int, int] = {}
    for i, x in enumerate(nums):
        j = index_of.get(target - x)
        if j is not None:                     # `if j:` would be a bug when j == 0
            return [j, i]
        index_of[x] = i                       # insert AFTER checking → never pairs i with itself
    return []


def group_anagrams(strs: list[str]) -> list[list[str]]:
    groups: defaultdict[str, list[str]] = defaultdict(list)
    for s in strs:
        groups["".join(sorted(s))].append(s)  # O(k log k) key
    return list(groups.values())


def signature(s: str) -> tuple[int, ...]:     # O(k) key: tuple of 26 counts (hashable; a list is not)
    counts = [0] * 26
    for ch in s:
        counts[ord(ch) - ord("a")] += 1
    return tuple(counts)


def top_k_frequent(nums: list[int], k: int) -> list[int]:
    count = Counter(nums)
    buckets: list[list[int]] = [[] for _ in range(len(nums) + 1)]
    for x, f in count.items():
        buckets[f].append(x)
    out: list[int] = []
    for f in range(len(buckets) - 1, 0, -1):
        for x in buckets[f]:
            out.append(x)
            if len(out) == k:
                return out
    return out


def is_anagram(s: str, t: str) -> bool:
    return Counter(s) == Counter(t)           # O(n); sorted(s) == sorted(t) is O(n log n)


assert two_sum([2, 7, 11, 15], 9) == [0, 1] and two_sum([3, 3], 6) == [0, 1]
assert sorted(map(sorted, group_anagrams(["eat", "tea", "tan", "ate", "nat", "bat"]))) == \
    [["ate", "eat", "tea"], ["bat"], ["nat", "tan"]]
assert signature("aab")[:3] == (2, 1, 0)
assert sorted(top_k_frequent([1, 1, 1, 2, 2, 3], 2)) == [1, 2]
assert is_anagram("anagram", "nagaram") and not is_anagram("rat", "car")
c = Counter("banana")
assert c["a"] == 3 and c["z"] == 0 and "z" not in c          # reading a missing key doesn't insert it
d = defaultdict(int)
_ = d["z"]
assert "z" in d                                               # ...but defaultdict DOES insert on read
print("hashing templates ok")
```

---

## 4. The same in Java (occasional reps)

**This week's Java rep (Week 2): 49 Group Anagrams.**

| Python | Java |
|---|---|
| `d.get(k, 0) + 1` / `Counter` | `map.merge(k, 1, Integer::sum)` |
| `defaultdict(list)[k].append(v)` | `map.computeIfAbsent(k, key -> new ArrayList<>()).add(v)` |
| `"".join(sorted(s))` as a key | `char[] c = s.toCharArray(); Arrays.sort(c); new String(c)` |
| `tuple(counts)` as a key | `Arrays.toString(counts)` (arrays hash by identity — never use `int[]` as a key) |
| `x in seen` / `seen.add(x)` | `seen.contains(x)` / `if (!seen.add(x))` detects duplicates |

```java
import java.util.*;

class GroupAnagramsRep {
    static List<List<String>> groupAnagrams(String[] strs) {
        Map<String, List<String>> groups = new HashMap<>();
        for (String s : strs) {
            char[] key = s.toCharArray();
            Arrays.sort(key);
            groups.computeIfAbsent(new String(key), k -> new ArrayList<>()).add(s);
        }
        return new ArrayList<>(groups.values());
    }

    public static void main(String[] args) {
        System.out.println(groupAnagrams(new String[]{"eat", "tea", "tan", "ate", "nat", "bat"}).size()); // 3
    }
}
```

Java traps: `map.get(k) + 1` on a missing key → `NullPointerException`; comparing two `Integer` counts with `==` fails above 127 ([quick reference §5](./java-dsa-toolkit.md#5-overflow-integer-caching-and-other-traps)).

---

## 5. Common patterns (sub-variants)

| Sub-pattern | Structure | Problems |
|---|---|---|
| Membership / dedupe | `set`; `len(set(xs)) != len(xs)` or early exit | 217 |
| Frequency comparison | `Counter(s) == Counter(t)` or `[0] * 26` | 242, 383 |
| Complement lookup | `dict` value → index; check before insert | 1 |
| Grouping by canonical key | `defaultdict(list)` | 49 |
| Frequency → bucket sort / heap | `Counter` then buckets or `heapq.nlargest` | 347 |
| Multiple constraint sets | one `set` per row/col/box (or a set of tuples) | 36 |
| Sequence starts | only expand from `x` where `x - 1` is absent | 128 |
| Dict + list for O(1) random access | index map + list swap-with-last | 380 |

---

## 6. How to recognise the pattern

- Words: *duplicate*, *unique*, *count*, *frequency*, *anagram*, *seen before*, *pair with sum*, *group*.
- The brute force has an **inner loop that searches** for something → replace with O(1) lookup.
- Order of input doesn't matter (or you need O(n) and sorting would be O(n log n)).
- "Must run in O(n) time" on unsorted input (e.g. 128) → sorting is forbidden → set.
- If you need order (next bigger key), a dict is wrong → `bisect` on a sorted list, or `TreeMap` in Java.

---

## 7. Beginner problems (Week 2)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 217 | [Contains Duplicate](https://leetcode.com/problems/contains-duplicate/) | Easy | `set` with early exit (see the worked example in [00](./00-big-o.md)). |
| 242 | [Valid Anagram](https://leetcode.com/problems/valid-anagram/) | Easy | `Counter(s) == Counter(t)`; then write the `[0] * 26` version. |
| 1 | [Two Sum](https://leetcode.com/problems/two-sum/) | Easy | Dict value → index; look up `target - x` before inserting `x`. |

---

## 8. Interview problems (Week 2)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 49 | [Group Anagrams](https://leetcode.com/problems/group-anagrams/) | Medium | 2 | <details><summary>show</summary>Canonical key = `"".join(sorted(s))` or a 26-count `tuple`; `defaultdict(list)`.</details> |
| 347 | [Top K Frequent Elements](https://leetcode.com/problems/top-k-frequent-elements/) | Medium | 2 | <details><summary>show</summary>`Counter`, then bucket by frequency (index = count ≤ n) and read from the highest bucket → O(n). `heapq.nlargest(k, count, key=count.get)` is O(n log k).</details> |
| 36 | [Valid Sudoku](https://leetcode.com/problems/valid-sudoku/) | Medium | 2 | <details><summary>show</summary>Sets per row, column and box; box index `(r // 3, c // 3)`. Skip `"."`.</details> |
| 128 | [Longest Consecutive Sequence](https://leetcode.com/problems/longest-consecutive-sequence/) | Medium | 2 | <details><summary>show</summary>Put all in a set; only start counting at `x` when `x - 1` is not in the set → each element visited ≤ 2 times → O(n).</details> |

### Stretch pool (Weeks 20–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 383 | [Ransom Note](https://leetcode.com/problems/ransom-note/) | Easy | <details><summary>show</summary>`not (Counter(ransomNote) - Counter(magazine))` — Counter subtraction drops non-positive counts. Timed warm-up: ≤ 5 min.</details> |
| 229 | [Majority Element II](https://leetcode.com/problems/majority-element-ii/) | Medium | <details><summary>show</summary>At most 2 elements appear > n/3 times; Boyer–Moore with two candidates + a verification pass. (`Counter` solution first.)</details> |
| 380 | [Insert Delete GetRandom O(1)](https://leetcode.com/problems/insert-delete-getrandom-o1/) | Medium | <details><summary>show</summary>List of values + dict value→index; delete by moving the last element into the hole and popping; `random.choice(list)`.</details> |

---

## 9. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Arrays & Hashing**: 217 → 242 → 1 → 49 → 347 → (238 in [01](./01-arrays-strings.md)) → 36 → 128. (NeetCode's "Encode and Decode Strings" is LeetCode Premium 271 — optional on neetcode.io; not counted.)

Order here (all Week 2, after 238): implement `MyHashMap` → 217 → 242 → 1 → 49 → 347 → 36 → 128 → Java rep 49 → (W20–26) 383, 229, 380.

---

## 10. Target number of problems

**7 new** in Week 2 (+ 1 from [01](./01-arrays-strings.md) = the week's 8) + 3 stretch problems for Weeks 20–26.

---

## 11. Mistakes beginners commonly make

**Python-specific**
- Using a `list` (or `dict`/`set`) as a key → `TypeError: unhashable type`. Convert to `tuple` / `frozenset` / `str`.
- `if index_of.get(x):` when the stored index can be `0` → falsy → bug. Compare with `is not None`.
- `defaultdict` **inserts** on read (`d[k]` creates the key); `Counter` doesn't. Checking `if d[k]` on a `defaultdict` silently grows it.
- Mutating a dict while iterating (`for k in d: del d[k]`) → `RuntimeError`. Iterate over `list(d)`.
- `x in lst` inside a loop instead of `x in set_of_x` → O(n²).
- Relying on `set` ordering (there is none); relying on dict order is fine (insertion order) but say so.
- Mutable default argument `def f(seen=set())` — the same set is shared across calls (and across LeetCode test cases).

**General**
- Two Sum: inserting before checking → pairs an element with itself (`[3]`, target 6).
- 128: starting a count from **every** element → O(n²) worst case.
- Claiming "O(1)" without "average".

**Java-rep traps (49):** `char[]` keys, `map.get(k) + 1` NPE, `Integer ==`.

---

## 12. Mastery criteria

- [ ] Implement `MyHashMap` (`get`/`put`/`remove`/`_resize`) from memory in ≤ 20 min; explain load factor and collision handling (and how CPython differs: open addressing).
- [ ] Solve 49, 347 and 128 from blank in ≤ 20 min each, with the O(n) variants for 347 and 128.
- [ ] Solve 2 unseen hashing Mediums in ≤ 25 min each.
- [ ] Explain why a `list` can't be a dict key and what makes an object hashable.
- [ ] Java rep: 49 in Java in ≤ 20 min.

---

## 13. Revision schedule

| Review | Week 2 early set (217, 242, 1) | Week 2 late set (49, 347, 36, 128) |
|---|---|---|
| Day 0 | W2 Mon–Tue | W2 Wed–Thu |
| Day 3 | W2 Thu–Fri | W2 Sun / W3 Mon |
| Day 7 | W3 | W3 |
| Day 14 | W4 | W4 |
| Day 30 | W6 | W6 |

**Revisit:** Week 4 (Checkpoint 4: 1 and 49 timed), Week 8 review week (128, 347), Week 11 (347 again with `heapq` in [13](./13-heap-priority-queue.md)), Weeks 20–26 stretch pool.

---

## Worked example — 128. Longest Consecutive Sequence

**Clarify.** `nums: list[int]`, 0 ≤ n ≤ 10⁵, values in [−10⁹, 10⁹], may contain duplicates, unsorted. Return the length of the longest run of consecutive integers (values, not positions). **Must be O(n).** Empty → 0.

**Brute force.** For each x, count x+1, x+2, … by scanning the list → O(n³); with a set, O(n²) worst case (e.g. `1..n`: every element walks the whole run).

**Sort-based.** `sorted(set(nums))`, then scan counting runs → O(n log n). Correct but violates the O(n) requirement — mention it as a stepping stone.

**Optimise.** Put everything in a `set`. A number `x` is the **start** of a sequence iff `x - 1` is not in the set. Only from starts do we walk `x+1, x+2, …`. Every element is visited once by the outer loop and at most once by an inner walk → O(n).

**Code (Python).**

```python
def longest_consecutive(nums: list[int]) -> int:
    num_set = set(nums)
    best = 0
    for x in num_set:                 # iterate the SET so duplicates don't repeat work
        if x - 1 in num_set:
            continue                  # not the start of a run
        length = 1
        while x + length in num_set:
            length += 1
        best = max(best, length)
    return best


assert longest_consecutive([100, 4, 200, 1, 3, 2]) == 4
assert longest_consecutive([0, 3, 7, 2, 5, 8, 4, 6, 0, 1]) == 9
assert longest_consecutive([]) == 0
assert longest_consecutive([1, 2, 0, 1]) == 3
assert longest_consecutive([-1, -2, 0]) == 3
assert longest_consecutive([-10**9, 10**9]) == 1
print("128 worked example passed")
```

**Test cases.** `[100,4,200,1,3,2]` → 4 · `[0,3,7,2,5,8,4,6,0,1]` → 9 · `[]` → 0 · duplicates `[1,2,0,1]` → 3 · negatives `[-1,-2,0]` → 3 · extremes → 1. (In your Java version, `x + len` near `Integer.MAX_VALUE` could overflow — the constraint ±10⁹ keeps it safe.)

**Complexity.** Time O(n) average (each element touched ≤ 2 times + O(1) set ops). Space O(n) for the set.
