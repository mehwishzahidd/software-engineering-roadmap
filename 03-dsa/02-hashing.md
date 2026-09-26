# 02 — HashMap / HashSet

> **Week 2** · NeetCode section: **Arrays & Hashing** · Target: **7 new problems** + stretch pool
> Back to [DSA overview](./README.md) · Tracker: [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md#02--hashmap--hashset) · Java APIs: [toolkit §4–§5](./java-dsa-toolkit.md#4-hashmap)

Hashing turns "search" from O(n) into O(1) average. It is the single most common optimisation in interviews: *"the brute force is O(n²) because of the inner search — replace the inner loop with a hash lookup."* Week 2 also covers `HashMap` internals in [`01-java/03-collections-generics.md`](../01-java/03-collections-generics.md) — study them together.

---

## 1. Concepts to learn

| Concept | Key facts |
|---|---|
| Hash function | Maps a key to an int; `index = hash & (capacity - 1)` when capacity is a power of two |
| `hashCode` / `equals` contract | Equal objects **must** have equal hash codes. Override both or neither. Records and `String` do it for you |
| Collisions | Separate chaining (Java: linked list → red-black tree at 8 entries per bucket) vs open addressing |
| Load factor & resizing | Java resizes ×2 at 0.75 full; rehash is O(n) but amortised O(1) per insert |
| Complexity | Average O(1) get/put/remove; worst case O(log n) per op in Java 8+ |
| `HashSet` | A `HashMap` with dummy values — membership only |
| Ordered variants | `LinkedHashMap` (insertion/access order), `TreeMap` (sorted, O(log n)) |
| Canonical keys | Group equivalent items under one key: sorted string, count signature, `(r, c)` record |
| Complement lookup | For each `x`, ask "have I seen `target - x`?" |
| Frequency buckets | Bucket sort by count: `List<Integer>[] buckets = new List[n + 1]` for top-K in O(n) |

**Practical use:** In [FlowGrid](../18-projects/flowgrid/README.md) you group inventory levels by warehouse (`computeIfAbsent`), and its `Idempotency-Key` store is a hash lookup ("have I seen this key before?") — the same patterns as 49 and 217, moved into PostgreSQL/Redis.

---

## 2. Prerequisite knowledge

- [`00-big-o.md`](./00-big-o.md), [`01-arrays-strings.md`](./01-arrays-strings.md)
- `equals`/`hashCode` — [`01-java/02-oop.md`](../01-java/02-oop.md)
- `Map`/`Set` API — [toolkit §4](./java-dsa-toolkit.md#4-hashmap), [§5](./java-dsa-toolkit.md#5-hashset), [§10 Integer `==` trap](./java-dsa-toolkit.md#10-the-integer-caching--trap)

---

## 3. Java implementation

### 3.1 A hash map from scratch (separate chaining)

```java
import java.util.*;

class MyHashMap<K, V> {
    private static final class Node<K, V> {
        final K key; V value; Node<K, V> next;
        Node(K key, V value, Node<K, V> next) { this.key = key; this.value = value; this.next = next; }
    }

    private Node<K, V>[] table;
    private int size;

    @SuppressWarnings("unchecked")
    MyHashMap() { table = (Node<K, V>[]) new Node[16]; }

    private int index(Object key, int cap) {
        int h = (key == null) ? 0 : key.hashCode();
        h ^= (h >>> 16);                       // spread high bits (same trick as java.util.HashMap)
        return h & (cap - 1);                  // cap is a power of two
    }

    V get(K key) {
        for (Node<K, V> n = table[index(key, table.length)]; n != null; n = n.next)
            if (Objects.equals(n.key, key)) return n.value;
        return null;
    }

    V put(K key, V value) {
        int i = index(key, table.length);
        for (Node<K, V> n = table[i]; n != null; n = n.next) {
            if (Objects.equals(n.key, key)) { V old = n.value; n.value = value; return old; }
        }
        table[i] = new Node<>(key, value, table[i]);   // prepend to chain
        if (++size > table.length * 3 / 4) resize();
        return null;
    }

    V remove(K key) {
        int i = index(key, table.length);
        Node<K, V> prev = null;
        for (Node<K, V> n = table[i]; n != null; prev = n, n = n.next) {
            if (Objects.equals(n.key, key)) {
                if (prev == null) table[i] = n.next; else prev.next = n.next;
                size--;
                return n.value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Node<K, V>[] old = table;
        table = (Node<K, V>[]) new Node[old.length * 2];
        for (Node<K, V> head : old)
            for (Node<K, V> n = head; n != null; n = n.next)
                table[index(n.key, table.length)] = new Node<>(n.key, n.value, table[index(n.key, table.length)]);
    }

    int size() { return size; }

    public static void main(String[] args) {
        MyHashMap<String, Integer> m = new MyHashMap<>();
        for (int i = 0; i < 100; i++) m.put("k" + i, i);
        m.put("k5", 500);
        m.remove("k7");
        System.out.println(m.get("k5") + " " + m.get("k7") + " " + m.size());   // 500 null 99
    }
}
```

### 3.2 Hashing templates

```java
import java.util.*;

class HashingTemplates {
    // Complement lookup (Two Sum): O(n)
    static int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> indexOf = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            Integer j = indexOf.get(target - nums[i]);
            if (j != null) return new int[]{j, i};
            indexOf.put(nums[i], i);                 // put AFTER checking → never pairs i with itself
        }
        return new int[0];
    }

    // Frequency count with merge
    static Map<Integer, Integer> counts(int[] nums) {
        Map<Integer, Integer> c = new HashMap<>();
        for (int x : nums) c.merge(x, 1, Integer::sum);
        return c;
    }

    // Grouping by canonical key (Group Anagrams): O(n * k log k)
    static List<List<String>> groupAnagrams(String[] strs) {
        Map<String, List<String>> groups = new HashMap<>();
        for (String s : strs) {
            char[] k = s.toCharArray();
            Arrays.sort(k);
            groups.computeIfAbsent(new String(k), x -> new ArrayList<>()).add(s);
        }
        return new ArrayList<>(groups.values());
    }

    // Count-signature key: O(n * k) — avoids sorting each word
    static String signature(String s) {
        int[] f = new int[26];
        for (int i = 0; i < s.length(); i++) f[s.charAt(i) - 'a']++;
        return Arrays.toString(f);                   // "[1, 0, 0, ...]" is a valid map key
    }

    // Bucket sort by frequency (Top K Frequent): O(n)
    static int[] topKFrequent(int[] nums, int k) {
        Map<Integer, Integer> c = counts(nums);
        @SuppressWarnings("unchecked")
        List<Integer>[] buckets = new List[nums.length + 1];
        for (var e : c.entrySet()) {
            int f = e.getValue();
            if (buckets[f] == null) buckets[f] = new ArrayList<>();
            buckets[f].add(e.getKey());
        }
        int[] res = new int[k];
        int w = 0;
        for (int f = nums.length; f > 0 && w < k; f--)
            if (buckets[f] != null) for (int x : buckets[f]) if (w < k) res[w++] = x;
        return res;
    }

    // Set membership for "seen before"
    static boolean hasDuplicate(int[] nums) {
        Set<Integer> seen = new HashSet<>();
        for (int x : nums) if (!seen.add(x)) return true;
        return false;
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(twoSum(new int[]{2, 7, 11, 15}, 9)));           // [0, 1]
        System.out.println(groupAnagrams(new String[]{"eat", "tea", "tan", "ate", "nat", "bat"}).size()); // 3
        System.out.println(signature("aab").substring(0, 7));                              // [2, 1,
        System.out.println(Arrays.toString(topKFrequent(new int[]{1, 1, 1, 2, 2, 3}, 2))); // [1, 2]
        System.out.println(hasDuplicate(new int[]{1, 2, 3, 1}));                            // true
    }
}
```

---

## 4. Common patterns (sub-variants)

| Sub-pattern | Structure | Problems |
|---|---|---|
| Membership / dedupe | `HashSet.add` returns false on duplicate | 217 |
| Frequency comparison | `int[26]` or `Map` counts, compare | 242, 383 |
| Complement lookup | `Map<value, index>`; check before insert | 1 |
| Grouping by canonical key | `computeIfAbsent(key, ...).add(item)` | 49 |
| Frequency → bucket sort / heap | counts then top-K | 347 |
| Multiple constraint sets | one `Set` per row/col/box | 36 |
| Sequence starts | only expand from `x` where `x - 1` is absent | 128 |
| Map + list for O(1) random access | index map + `ArrayList` swap-with-last | 380 |

---

## 5. How to recognise the pattern

- Words: *duplicate*, *unique*, *count*, *frequency*, *anagram*, *seen before*, *pair with sum*, *group*.
- The brute force has an **inner loop that searches** for something → replace with O(1) lookup.
- Order of input doesn't matter (or you need O(n) and sorting would be O(n log n)).
- "Must run in O(n) time" on unsorted input (e.g. 128) is a strong hint: sorting is forbidden → hash set.
- If you need order (next bigger key), a `HashMap` is wrong → `TreeMap` ([toolkit §8](./java-dsa-toolkit.md#8-treemap--treeset)).

---

## 6. Beginner problems (Week 2)

| # | Problem | Difficulty | Hint |
|---:|---|---|---|
| 217 | [Contains Duplicate](https://leetcode.com/problems/contains-duplicate/) | Easy | `if (!set.add(x)) return true;` |
| 242 | [Valid Anagram](https://leetcode.com/problems/valid-anagram/) | Easy | `int[26]`: +1 for `s`, −1 for `t`, all zeros at the end. Length check first. |
| 1 | [Two Sum](https://leetcode.com/problems/two-sum/) | Easy | Map value → index; look up `target - x` before inserting `x`. |

---

## 7. Interview problems (Week 2)

| # | Problem | Difficulty | Week | Key insight |
|---:|---|---|---:|---|
| 49 | [Group Anagrams](https://leetcode.com/problems/group-anagrams/) | Medium | 2 | <details><summary>show</summary>Canonical key = sorted chars (or 26-count signature); `computeIfAbsent(key, k -> new ArrayList<>()).add(s)`.</details> |
| 347 | [Top K Frequent Elements](https://leetcode.com/problems/top-k-frequent-elements/) | Medium | 2 | <details><summary>show</summary>Count, then bucket by frequency (index = count, max n) and read from the highest bucket → O(n). Heap of size k is O(n log k).</details> |
| 36 | [Valid Sudoku](https://leetcode.com/problems/valid-sudoku/) | Medium | 2 | <details><summary>show</summary>9 row sets, 9 col sets, 9 box sets; box index = `(r / 3) * 3 + c / 3`.</details> |
| 128 | [Longest Consecutive Sequence](https://leetcode.com/problems/longest-consecutive-sequence/) | Medium | 2 | <details><summary>show</summary>Put all in a set; only start counting at `x` when `x - 1` is not in the set → each element visited ≤ 2 times → O(n).</details> |

### Stretch pool (Weeks 21–26 mixed review)

| # | Problem | Difficulty | Key insight |
|---:|---|---|---|
| 383 | [Ransom Note](https://leetcode.com/problems/ransom-note/) | Easy | <details><summary>show</summary>Count magazine letters in `int[26]`, then consume them for the note; any negative → false. Timed warm-up: ≤ 5 min.</details> |
| 229 | [Majority Element II](https://leetcode.com/problems/majority-element-ii/) | Medium | <details><summary>show</summary>At most 2 elements appear > n/3 times; Boyer–Moore with two candidates + a verification pass. (Hash-count solution is fine first.)</details> |
| 380 | [Insert Delete GetRandom O(1)](https://leetcode.com/problems/insert-delete-getrandom-o1/) | Medium | <details><summary>show</summary>`ArrayList` of values + `HashMap` value→index; delete by swapping with the last element and removing the tail.</details> |

---

## 8. Recommended NeetCode / LeetCode practice order

NeetCode 150 **Arrays & Hashing**: 217 → 242 → 1 → 49 → 347 → (238 in [01](./01-arrays-strings.md)) → 36 → 128. (NeetCode's "Encode and Decode Strings" is LeetCode Premium 271 — optional, do it on neetcode.io if you have time; not counted.)

Order here (all Week 2, after 238 from [01](./01-arrays-strings.md)): implement `MyHashMap` → 217 → 242 → 1 → 49 → 347 → 36 → 128 → (W20–26) 383, 229, 380.

---

## 9. Target number of problems

**7 new** in Week 2 (+ 1 from [01](./01-arrays-strings.md) = the week's 8) + 3 stretch problems for Weeks 20–26.

---

## 10. Mistakes beginners commonly make

- `map.get(k) + 1` when absent → `NullPointerException`. Use `getOrDefault` or `merge`.
- `map.get(a) == map.get(b)` with `Integer` values → false above 127 ([toolkit §10](./java-dsa-toolkit.md#10-the-integer-caching--trap)).
- Using `char[]`/`int[]` as a key — arrays use identity hash. Convert to `String`.
- In Two Sum, inserting before checking → pairs an element with itself (`[3]`, target 6).
- Iterating a `HashMap` and expecting sorted/insertion order.
- Using a `HashMap<Character,Integer>` where `int[26]` would do (slower, more boxing, more code).
- Forgetting to override `hashCode` when overriding `equals` on a custom key class (use a `record`).
- Claiming "O(1)" without "average".
- 128: starting a count from **every** element → O(n²) worst case.

---

## 11. Mastery criteria

- [ ] Implement `MyHashMap` (`get`/`put`/`remove`/`resize`) from memory in ≤ 20 min; explain load factor and collision handling.
- [ ] Solve 49, 347 and 128 from blank in ≤ 20 min each, with the O(n) variants for 347 and 128.
- [ ] Solve 2 unseen hashing Mediums in ≤ 25 min each.
- [ ] Explain the `equals`/`hashCode` contract with an example of what breaks when it's violated.

---

## 12. Revision schedule

| Review | Week 2 early set (217, 242, 1) | Week 2 late set (49, 347, 36, 128) |
|---|---|---|
| Day 0 | W2 Mon–Tue | W2 Wed–Thu |
| Day 3 | W2 Thu–Fri | W2 Sun / W3 Mon |
| Day 7 | W3 | W3 |
| Day 14 | W4 | W4 |
| Day 30 | W6 | W6 |

**Revisit:** Week 4 (Checkpoint 4: 1 and 49 timed), Week 8 review week (128, 347), Week 11 (347 again with a heap in [13](./13-heap-priority-queue.md)), Weeks 20–26 stretch pool.

---

## Worked example — 128. Longest Consecutive Sequence

**Clarify.** `int[] nums`, 0 ≤ n ≤ 10⁵, values in [−10⁹, 10⁹], may contain duplicates, unsorted. Return the length of the longest run of consecutive integers (values, not positions). **Must be O(n).** Empty → 0.

**Brute force.** For each x, count x+1, x+2, … by scanning the array → O(n³); with a set, O(n²) worst case (e.g. `1..n`, every element walks the whole run).

**Sort-based.** Sort, then scan counting runs (skip duplicates) → O(n log n). Correct but violates the O(n) requirement — mention it as a stepping stone.

**Optimise.** Put everything in a `HashSet`. A number `x` is the **start** of a sequence iff `x - 1` is not in the set. Only from starts do we walk `x+1, x+2, …`. Every element is visited once by the outer loop and at most once by an inner walk → O(n).

**Code.**

```java
import java.util.*;

class LongestConsecutive {
    public int longestConsecutive(int[] nums) {
        Set<Integer> set = new HashSet<>();
        for (int x : nums) set.add(x);
        int best = 0;
        for (int x : set) {                    // iterate the SET, so duplicates don't repeat work
            if (set.contains(x - 1)) continue; // not a start
            int len = 1;
            while (set.contains(x + len)) len++;
            best = Math.max(best, len);
        }
        return best;
    }

    public static void main(String[] args) {
        LongestConsecutive s = new LongestConsecutive();
        System.out.println(s.longestConsecutive(new int[]{100, 4, 200, 1, 3, 2}));        // 4
        System.out.println(s.longestConsecutive(new int[]{0, 3, 7, 2, 5, 8, 4, 6, 0, 1})); // 9
        System.out.println(s.longestConsecutive(new int[]{}));                             // 0
        System.out.println(s.longestConsecutive(new int[]{1, 2, 0, 1}));                   // 3
    }
}
```

**Test cases.** `[100,4,200,1,3,2]` → 4 · `[0,3,7,2,5,8,4,6,0,1]` → 9 · `[]` → 0 · duplicates `[1,2,0,1]` → 3 · negatives `[-1,-2,0]` → 3 · extremes `[-1000000000, 1000000000]` → 1 (values are bounded by 10⁹, so `x + len` stays below `Integer.MAX_VALUE`; if values could reach `Integer.MAX_VALUE`, `x + 1` would wrap to `MIN_VALUE` — say this out loud).

**Complexity.** Time O(n) average (each element touched ≤ 2 times + O(1) set ops). Space O(n) for the set.
