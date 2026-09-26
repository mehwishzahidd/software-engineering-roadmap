# 🧰 Java DSA Toolkit (Java 21)

> The Java APIs, idioms and traps you need for LeetCode, OAs and whiteboard interviews.
> Read once in **Week 1**, then keep it open while solving. Every code block here compiles on Java 21.
> Deeper Collections theory (internals, `equals`/`hashCode` contracts, generics) lives in
> [`01-java/03-collections-generics.md`](../01-java/03-collections-generics.md); memory/stack details in
> [`01-java/06-memory-jvm.md`](../01-java/06-memory-jvm.md).

## Contents

1. [Arrays](#1-arrays)
2. [Strings, `StringBuilder` and `char` arithmetic](#2-strings-stringbuilder-and-char-arithmetic)
3. [`Character` methods](#3-character-methods)
4. [`HashMap`](#4-hashmap)
5. [`HashSet`](#5-hashset)
6. [`ArrayDeque` as stack and queue](#6-arraydeque-as-stack-and-queue)
7. [`PriorityQueue` and comparators](#7-priorityqueue-and-comparators)
8. [`TreeMap` / `TreeSet`](#8-treemap--treeset)
9. [Integer overflow and `long`](#9-integer-overflow-and-long)
10. [The `Integer` caching `==` trap](#10-the-integer-caching--trap)
11. [`Collections`, `List.of` and immutability](#11-collections-listof-and-immutability)
12. [Recursion depth](#12-recursion-depth)
13. [Fast I/O for OAs](#13-fast-io-for-oas)
14. [Common templates](#14-common-templates)
15. [Operation cost cheat sheet](#15-operation-cost-cheat-sheet)
16. [Interview questions about this toolkit](#16-interview-questions-about-this-toolkit)

---

## 1. Arrays

```java
import java.util.*;

class ArraysDemo {
    public static void main(String[] args) {
        int[] a = new int[5];                 // all 0; boolean[] → false; Object[] → null
        int[] b = {5, 3, 1, 4, 2};
        int[][] grid = new int[3][4];         // 3 rows, 4 cols; grid.length = 3, grid[0].length = 4
        int[][] jagged = new int[3][];        // rows allocated later: jagged[0] = new int[7];

        Arrays.sort(b);                       // dual-pivot quicksort for primitives, O(n log n)
        Arrays.fill(a, -1);                   // every cell = -1 (handy for memo arrays)
        for (int[] row : grid) Arrays.fill(row, Integer.MAX_VALUE); // fill a 2-D array row by row

        int[] copy = Arrays.copyOf(b, b.length);          // independent copy
        int[] part = Arrays.copyOfRange(b, 1, 3);         // [from, to) → indices 1, 2
        System.out.println(Arrays.toString(b));           // [1, 2, 3, 4, 5]  (arrays have no useful toString)
        System.out.println(Arrays.deepToString(grid));    // for 2-D
        System.out.println(Arrays.equals(b, copy));       // content equality; b.equals(copy) is reference equality!

        int idx = Arrays.binarySearch(b, 4);  // index if found; otherwise -(insertionPoint) - 1

        // Sorting objects / 2-D arrays needs a Comparator (boxed or int[] rows)
        int[][] intervals = {{5, 6}, {1, 3}, {2, 4}};
        Arrays.sort(intervals, (x, y) -> Integer.compare(x[0], y[0]));  // sort by start

        Integer[] boxed = {3, 1, 2};
        Arrays.sort(boxed, Collections.reverseOrder());   // descending needs boxed type
        // Arrays.sort(int[], Comparator) does NOT exist → box, or sort ascending and read backwards.

        // Arrays.asList: fixed-size List VIEW backed by the array
        List<Integer> view = Arrays.asList(boxed);
        view.set(0, 99);                      // OK, writes through to boxed[0]
        // view.add(4);                       // UnsupportedOperationException (fixed size)
        List<Integer> growable = new ArrayList<>(Arrays.asList(3, 1, 2)); // copy → fully mutable

        // Trap: Arrays.asList(int[]) gives List<int[]> with ONE element, not a List<Integer>
        List<int[]> oops = Arrays.asList(new int[]{1, 2, 3});
        System.out.println(oops.size());      // 1

        // int[] ↔ List<Integer>
        List<Integer> list = Arrays.stream(b).boxed().toList();   // immutable
        int[] back = list.stream().mapToInt(Integer::intValue).toArray();
        int sum = Arrays.stream(b).sum();
        int max = Arrays.stream(b).max().getAsInt();
        System.out.println(idx + " " + part.length + " " + growable + " " + back.length + " " + sum + " " + max + " " + jagged.length);
    }
}
```

**Grid direction array** (used in every grid BFS/DFS):

```java
class Dirs {
    static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static boolean inBounds(int[][] g, int r, int c) {
        return r >= 0 && r < g.length && c >= 0 && c < g[0].length;
    }
}
```

---

## 2. Strings, `StringBuilder` and `char` arithmetic

`String` is **immutable**. `s + t` in a loop creates a new string every iteration → O(n²). Use `StringBuilder`.

```java
import java.util.*;

class StringsDemo {
    public static void main(String[] args) {
        String s = "Hello, World";
        char c = s.charAt(0);                 // 'H' — O(1)
        int n = s.length();                   // method on String; field .length on arrays; .size() on collections
        char[] chars = s.toCharArray();       // O(n) copy; mutate freely
        Arrays.sort(chars);
        String sorted = new String(chars);    // back to String (NOT chars.toString())
        String sub = s.substring(0, 5);       // [0, 5) → "Hello", O(k) copy
        String[] parts = "a,b,,c".split(","); // regex! split(".") needs "\\."; trailing empties dropped
        String joined = String.join("-", List.of("a", "b", "c"));   // "a-b-c"
        boolean same = s.equals("Hello, World");  // ALWAYS equals, never == for content
        int cmp = "apple".compareTo("banana");    // < 0 → lexicographic order
        String lower = s.toLowerCase();
        String trimmed = "  x ".strip();          // Java 11+, Unicode-aware trim
        int pos = s.indexOf("World");             // -1 if absent
        String rep = "ab".repeat(3);              // "ababab"
        String fromInt = String.valueOf(42);      // or Integer.toString(42)
        int parsed = Integer.parseInt("-123");

        // StringBuilder — mutable, amortised O(1) append
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) sb.append(i).append(',');
        sb.setLength(sb.length() - 1);        // drop last char (cheap "pop" for backtracking)
        sb.deleteCharAt(0);
        sb.insert(0, 'X');
        sb.reverse();
        sb.setCharAt(0, 'Z');
        String out = sb.toString();

        // char arithmetic — chars are 16-bit unsigned integers
        int idx = 'c' - 'a';                  // 2  → index into int[26]
        char next = (char) ('a' + 3);         // 'd' — the cast is required
        int digit = '7' - '0';                // 7
        int[] freq = new int[26];
        for (char ch : "banana".toCharArray()) freq[ch - 'a']++;

        System.out.println(c + n + sorted + sub + parts.length + joined + same + cmp + lower + trimmed
                + pos + rep + fromInt + parsed + out + idx + next + digit + freq[0]);
    }
}
```

| Trap | Why | Fix |
|---|---|---|
| `s1 == s2` | Compares references; works "sometimes" due to the string pool | `s1.equals(s2)` |
| `result += s` in a loop | O(n²) copies | `StringBuilder` |
| `char + char` | Produces an `int`, not a string: `'a' + 'b'` = 195 | `"" + a + b` or `sb.append(a).append(b)` |
| `sb1.equals(sb2)` | `StringBuilder` doesn't override `equals` | `sb1.toString().equals(sb2.toString())` or `sb1.compareTo(sb2) == 0` (Java 11+) |
| `s.split(".")` | `.` is a regex meta-char → empty array | `s.split("\\.")` |
| `substring` in a hot loop | Each call copies (since Java 7u6) | Work with indices instead |

---

## 3. `Character` methods

```java
class CharDemo {
    public static void main(String[] args) {
        char c = 'a';
        System.out.println(Character.isLetter(c));          // true
        System.out.println(Character.isDigit('5'));         // true
        System.out.println(Character.isLetterOrDigit('_')); // false — use for "alphanumeric" (LC 125)
        System.out.println(Character.isWhitespace(' '));    // true
        System.out.println(Character.isUpperCase('A'));     // true
        System.out.println(Character.toLowerCase('A'));     // 'a'
        System.out.println(Character.toUpperCase(c));       // 'A'
        System.out.println(Character.getNumericValue('9')); // 9 (also handles 'a'→10! prefer c - '0' for digits)
        char[] cs = {'x', 'y'};
        System.out.println(String.valueOf(cs));             // "xy"
    }
}
```

---

## 4. `HashMap`

Average O(1) `get`/`put`/`containsKey`; worst case O(log n) per bucket since Java 8 (treeified buckets). Iteration order is **not** defined — use `LinkedHashMap` (insertion order) or `TreeMap` (sorted) if order matters.

```java
import java.util.*;

class HashMapDemo {
    public static void main(String[] args) {
        Map<String, Integer> count = new HashMap<>();
        String[] words = {"a", "b", "a", "c", "a"};

        // 1) Counting — three equivalent idioms
        for (String w : words) count.put(w, count.getOrDefault(w, 0) + 1);
        count.clear();
        for (String w : words) count.merge(w, 1, Integer::sum);   // cleanest
        // merge(key, value, fn): absent → put value; present → put fn(old, value); fn returns null → remove

        // 2) Grouping — computeIfAbsent returns the (existing or new) value
        Map<Integer, List<String>> byLen = new HashMap<>();
        for (String w : List.of("hi", "yo", "hey")) {
            byLen.computeIfAbsent(w.length(), k -> new ArrayList<>()).add(w);
        }

        // 3) Adjacency list for graphs
        Map<Integer, List<Integer>> adj = new HashMap<>();
        int[][] edges = {{0, 1}, {1, 2}};
        for (int[] e : edges) {
            adj.computeIfAbsent(e[0], k -> new ArrayList<>()).add(e[1]);
            adj.computeIfAbsent(e[1], k -> new ArrayList<>()).add(e[0]);
        }

        // 4) Iterating
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            System.out.println(e.getKey() + "=" + e.getValue());
        }
        count.forEach((k, v) -> System.out.println(k + ":" + v));

        // 5) Decrement and remove at zero (sliding-window bookkeeping)
        count.merge("a", -1, Integer::sum);
        if (count.get("a") == 0) count.remove("a");   // get() returns Integer; == 0 unboxes, OK

        // 6) Array keys don't work (identity hash) → convert to String or List
        Map<String, List<String>> anagrams = new HashMap<>();
        char[] key = "eat".toCharArray();
        Arrays.sort(key);
        anagrams.computeIfAbsent(new String(key), k -> new ArrayList<>()).add("eat");

        System.out.println(byLen + " " + adj + " " + anagrams);
    }
}
```

| Trap | Fix |
|---|---|
| `map.get(k) + 1` when `k` is absent → `NullPointerException` (unboxing `null`) | `getOrDefault(k, 0)` or `merge` |
| Using `int[]` / `char[]` as a key | `new String(chars)`, `Arrays.toString(arr)`, or `List<Integer>` |
| Mutating a key object after inserting it | Keys must be effectively immutable (records/Strings/Integers) |
| `if (map.get(k) == map.get(j))` with `Integer` values | `==` on boxed values — see [§10](#10-the-integer-caching--trap). Use `.equals` or `.intValue()` |
| Modifying a map while iterating over it → `ConcurrentModificationException` | Collect keys to remove, or use `entrySet().removeIf(...)` |
| Fixed small alphabet | `int[26]` / `int[128]` is faster and simpler than a map |

---

## 5. `HashSet`

```java
import java.util.*;

class HashSetDemo {
    public static void main(String[] args) {
        Set<Integer> seen = new HashSet<>();
        boolean added = seen.add(3);      // true if newly added — "if (!seen.add(x)) duplicate!"
        boolean dup = !seen.add(3);       // true
        seen.remove(3);
        Set<Integer> a = new HashSet<>(List.of(1, 2, 3));
        Set<Integer> b = new HashSet<>(List.of(2, 3, 4));
        Set<Integer> inter = new HashSet<>(a); inter.retainAll(b);   // {2, 3}
        Set<Integer> union = new HashSet<>(a); union.addAll(b);      // {1, 2, 3, 4}
        Set<Integer> diff = new HashSet<>(a);  diff.removeAll(b);    // {1}
        // Visited set of grid cells: encode (r, c) as r * cols + c, or use a boolean[][]
        int cols = 10;
        Set<Integer> visited = new HashSet<>();
        visited.add(2 * cols + 5);
        // Or with records (Java 16+) — equals/hashCode generated for you
        record Cell(int r, int c) {}
        Set<Cell> cells = new HashSet<>();
        cells.add(new Cell(2, 5));
        System.out.println(added + " " + dup + inter + union + diff + visited + cells.contains(new Cell(2, 5)));
    }
}
```

---

## 6. `ArrayDeque` as stack and queue

**Never use `java.util.Stack`** (synchronized, extends `Vector`, legacy) or `LinkedList` as a queue in DSA (more allocation). `ArrayDeque` is the default for both. It **rejects `null`**.

```java
import java.util.*;

class DequeDemo {
    public static void main(String[] args) {
        // Stack (LIFO) — push/pop/peek operate on the HEAD
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(1); stack.push(2);
        int top = stack.peek();     // 2   (peek on empty → null → NPE on unboxing!)
        int popped = stack.pop();   // 2   (pop on empty → NoSuchElementException)

        // Queue (FIFO) — offer at TAIL, poll from HEAD
        Queue<int[]> q = new ArrayDeque<>();
        q.offer(new int[]{0, 0});
        int[] cur = q.poll();       // null if empty (no exception)

        // Deque (both ends) — monotonic deque for sliding-window max
        Deque<Integer> dq = new ArrayDeque<>();
        dq.offerLast(5); dq.offerFirst(4);
        int first = dq.peekFirst(), last = dq.peekLast();
        dq.pollFirst(); dq.pollLast();

        System.out.println(top + popped + cur[0] + first + last + " empty=" + stack.isEmpty());
    }
}
```

| Operation | Stack view | Queue view | Deque explicit |
|---|---|---|---|
| insert | `push(x)` | `offer(x)` | `offerFirst` / `offerLast` |
| remove | `pop()` | `poll()` | `pollFirst` / `pollLast` |
| look | `peek()` | `peek()` | `peekFirst` / `peekLast` |

> ⚠️ Mixing views on one object: `push` adds at the head, `offer` at the tail. For a stack use only push/pop/peek; for a queue only offer/poll/peek.

---

## 7. `PriorityQueue` and comparators

Binary heap. `offer`/`poll` O(log n), `peek` O(1), `remove(Object)` O(n), `contains` O(n). **Min-heap by default.** Iteration order is *not* sorted.

```java
import java.util.*;

class PQDemo {
    public static void main(String[] args) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        // or: new PriorityQueue<>((a, b) -> Integer.compare(b, a));

        // int[] entries: {node, dist} ordered by dist
        PriorityQueue<int[]> byDist = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
        byDist.offer(new int[]{3, 10});
        byDist.offer(new int[]{1, 2});
        System.out.println(byDist.poll()[0]);   // 1

        // Comparator combinators (readable, overflow-safe)
        record Task(String name, int priority, long createdAt) {}
        PriorityQueue<Task> tasks = new PriorityQueue<>(
                Comparator.comparingInt(Task::priority).reversed()
                          .thenComparingLong(Task::createdAt));
        tasks.offer(new Task("a", 1, 5));
        tasks.offer(new Task("b", 3, 9));
        System.out.println(tasks.peek().name());  // b

        // Map entries: most frequent first, ties by key ascending
        Map<String, Integer> freq = Map.of("x", 2, "y", 5, "z", 2);
        PriorityQueue<Map.Entry<String, Integer>> pq = new PriorityQueue<>(
                (e1, e2) -> e1.getValue().equals(e2.getValue())
                        ? e1.getKey().compareTo(e2.getKey())
                        : Integer.compare(e2.getValue(), e1.getValue()));
        pq.addAll(freq.entrySet());
        System.out.println(pq.poll().getKey());  // y

        minHeap.offer(3); maxHeap.offer(3);
    }
}
```

### The `a - b` overflow trap

```java
import java.util.*;

class CompareTrap {
    public static void main(String[] args) {
        // WRONG: subtraction overflows for large magnitudes
        Comparator<Integer> bad = (a, b) -> a - b;
        System.out.println(bad.compare(Integer.MIN_VALUE, 1));  // 2147483647 → claims MIN_VALUE > 1 !

        // RIGHT
        Comparator<Integer> good = (a, b) -> Integer.compare(a, b);
        System.out.println(good.compare(Integer.MIN_VALUE, 1)); // -1
    }
}
```

`(a, b) -> a - b` is only safe when both values are small and non-negative (e.g., array indices). **Always write `Integer.compare` / `Long.compare`** — interviewers notice.

---

## 8. `TreeMap` / `TreeSet`

Red-black tree: `get`/`put`/`remove`/`floor`/`ceiling` all **O(log n)**; keys iterate in sorted order.

```java
import java.util.*;

class TreeDemo {
    public static void main(String[] args) {
        TreeMap<Integer, String> tm = new TreeMap<>();
        tm.put(10, "ten"); tm.put(20, "twenty"); tm.put(30, "thirty");
        System.out.println(tm.floorKey(25));    // 20  — greatest key ≤ 25   (null if none!)
        System.out.println(tm.ceilingKey(25));  // 30  — smallest key ≥ 25
        System.out.println(tm.lowerKey(20));    // 10  — strictly less
        System.out.println(tm.higherKey(20));   // 30  — strictly greater
        System.out.println(tm.firstKey() + " " + tm.lastKey());
        Map.Entry<Integer, String> e = tm.floorEntry(25);  // key + value at once
        System.out.println(e.getValue());
        System.out.println(tm.headMap(20));        // {10=ten}          keys < 20
        System.out.println(tm.tailMap(20, true));  // {20=.., 30=..}    keys ≥ 20
        tm.pollFirstEntry();                       // remove + return smallest

        TreeSet<Integer> ts = new TreeSet<>(List.of(5, 1, 9));
        System.out.println(ts.floor(6) + " " + ts.ceiling(6) + " " + ts.first() + " " + ts.pollLast());
        // Multiset (duplicates) = TreeMap<value, count>
        TreeMap<Integer, Integer> multiset = new TreeMap<>();
        multiset.merge(5, 1, Integer::sum);
        multiset.merge(5, 1, Integer::sum);
        if (multiset.merge(5, -1, Integer::sum) == 0) multiset.remove(5);
        System.out.println(multiset);
    }
}
```

Used in: 981 Time Based Key-Value Store (`floorKey` on timestamps), 846 Hand of Straights, calendar/booking problems.
Trap: `floorKey` returns `Integer` **`null`** when nothing qualifies — assigning to `int` throws `NullPointerException`.

---

## 9. Integer overflow and `long`

| Type | Range | When |
|---|---|---|
| `int` | −2,147,483,648 … 2,147,483,647 (≈ ±2.1·10^9) | Indices, counts, values ≤ 10^9 |
| `long` | ≈ ±9.2·10^18 | Sums/products of many ints, `n * n` for n ≥ 46,341, timestamps |

```java
class OverflowDemo {
    public static void main(String[] args) {
        int big = 2_000_000_000;
        int wrong = big + big;                 // -294967296 — silent wraparound, no exception
        long right = (long) big + big;         // cast BEFORE the operation
        long alsoWrong = big * 2;              // still overflows: int*int computed first, then widened

        // Binary search midpoint
        int lo = 1_500_000_000, hi = 2_000_000_000;
        int badMid = (lo + hi) / 2;            // overflow → negative
        int mid = lo + (hi - lo) / 2;          // safe
        int mid2 = (lo + hi) >>> 1;            // safe: unsigned shift

        // Detect overflow explicitly
        try {
            Math.addExact(big, big);
        } catch (ArithmeticException ex) {
            System.out.println("overflow detected");
        }

        // Modulo arithmetic ("return the answer modulo 1e9+7")
        final int MOD = 1_000_000_007;
        long ways = 0;
        ways = (ways + 999_999_999L) % MOD;
        long product = (long) 1_000_000 * 1_000_000 % MOD;   // cast first
        int negMod = Math.floorMod(-7, 3);                    // 2 ; (-7 % 3) is -1 in Java

        // Math.abs(Integer.MIN_VALUE) is still negative!
        System.out.println(Math.abs(Integer.MIN_VALUE));      // -2147483648
        System.out.println(wrong + " " + right + " " + alsoWrong + " " + badMid + " " + mid + " " + mid2
                + " " + ways + " " + product + " " + negMod);
    }
}
```

Sentinels: use `Integer.MAX_VALUE` as "infinity" only if you never add to it (`INF + w` overflows). For Dijkstra/DP prefer `int INF = 1_000_000_000` or use `long`.

---

## 10. The `Integer` caching `==` trap

```java
import java.util.*;

class IntegerCacheTrap {
    public static void main(String[] args) {
        Integer a = 127, b = 127;
        System.out.println(a == b);        // true  — Integer.valueOf caches −128..127
        Integer c = 128, d = 128;
        System.out.println(c == d);        // false — different objects!
        System.out.println(c.equals(d));   // true

        Map<Character, Integer> m1 = new HashMap<>(), m2 = new HashMap<>();
        for (int i = 0; i < 200; i++) { m1.merge('x', 1, Integer::sum); m2.merge('x', 1, Integer::sum); }
        System.out.println(m1.get('x') == m2.get('x'));          // false — the classic bug (passes small tests!)
        System.out.println(m1.get('x').equals(m2.get('x')));     // true
        System.out.println(m1.get('x').intValue() == m2.get('x'));  // true — one side unboxed → numeric compare

        // Same trap with stacks/lists of Integer
        Deque<Integer> s1 = new ArrayDeque<>(List.of(1000)), s2 = new ArrayDeque<>(List.of(1000));
        System.out.println(s1.peek().equals(s2.peek()));         // compare with equals
    }
}
```

Rule: **boxed vs boxed → `.equals()`**. Boxed vs primitive → `==` is fine (unboxing). Also: `List<Integer>.remove(1)` removes **index** 1; `remove(Integer.valueOf(1))` removes the **value** 1.

---

## 11. `Collections`, `List.of` and immutability

```java
import java.util.*;

class CollectionsDemo {
    public static void main(String[] args) {
        List<Integer> list = new ArrayList<>(List.of(3, 1, 2));
        Collections.sort(list);                          // or list.sort(null)
        list.sort(Comparator.reverseOrder());
        Collections.reverse(list);
        Collections.swap(list, 0, 2);
        int max = Collections.max(list), min = Collections.min(list);
        int freq = Collections.frequency(list, 2);
        List<Integer> ro = Collections.unmodifiableList(list);   // read-only VIEW (changes to list show through)

        // List.of / Set.of / Map.of (Java 9+): IMMUTABLE, reject nulls
        List<Integer> fixed = List.of(1, 2, 3);
        try {
            fixed.add(4);                                 // UnsupportedOperationException
        } catch (UnsupportedOperationException ex) {
            System.out.println("List.of is immutable");
        }
        // Stream.toList() (Java 16+) is also unmodifiable; Collectors.toList() is (currently) mutable.

        // Backtracking trap: adding the SAME mutable list to results
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        path.add(1);
        res.add(new ArrayList<>(path));   // ✅ snapshot copy
        res.add(path);                    // ❌ later changes to path mutate this "result"
        path.clear();
        System.out.println(res);          // [[1], []]
        System.out.println(max + min + freq + ro.size());

        // Removing the last element of an ArrayList — O(1)
        path.add(5);
        path.remove(path.size() - 1);     // Java 21 also has path.removeLast()
    }
}
```

---

## 12. Recursion depth

- Default thread stack ≈ 512 KB–1 MB → roughly **5,000–20,000 frames** depending on frame size. A recursive DFS on a 10^5-node linked-list-shaped tree or a 1000×1000 grid **will** throw `StackOverflowError`.
- LeetCode's judge usually tolerates depth ~10^4. OAs (HackerRank/CodeSignal) are less forgiving.
- Mitigations: (1) convert to iteration with an explicit `ArrayDeque` stack; (2) use BFS for grids; (3) in your own programs/OAs where you control `main`, run the solver in a thread with a bigger stack:

```java
class BigStack {
    static int depth(int n) { return n == 0 ? 0 : 1 + depth(n - 1); }

    public static void main(String[] args) throws InterruptedException {
        Thread t = new Thread(null, () -> System.out.println(depth(1_000_000)), "solver", 1L << 28); // 256 MB
        t.start();
        t.join();
    }
}
```

Every recursive solution's **space complexity includes the call stack**: O(h) for trees (h = height, O(n) worst case), O(n) for memoised DP.

---

## 13. Fast I/O for OAs

LeetCode passes arguments to a method. HackerRank / CodeSignal-style OAs often make you read `stdin`. `Scanner` is slow for 10^5+ tokens; use `BufferedReader` + `StringTokenizer` and a single `PrintWriter`.

```java
import java.io.*;
import java.util.*;

class FastIO {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));

        String firstLine = br.readLine();
        if (firstLine == null) { out.flush(); return; }
        int n = Integer.parseInt(firstLine.trim());
        int[] a = new int[n];
        StringTokenizer st = new StringTokenizer("");
        int i = 0;
        while (i < n) {                                  // numbers may span several lines
            while (!st.hasMoreTokens()) {
                String line = br.readLine();
                if (line == null) break;
                st = new StringTokenizer(line);
            }
            if (!st.hasMoreTokens()) break;
            a[i++] = Integer.parseInt(st.nextToken());
        }
        long sum = 0;
        for (int x : a) sum += x;
        out.println(sum);
        out.flush();                                     // forgetting flush = empty output = 0 points
    }
}
```

(Input format assumed: `n` on line 1, then `n` integers on following lines.) Printing in a loop with `System.out.println` 10^5 times is slow — build a `StringBuilder` or use one `PrintWriter`.

---

## 14. Common templates

All templates are also explained in their pattern guide; this is the one-page copy.

```java
import java.util.*;

class Templates {
    // Binary search on a sorted array (see 07-binary-search.md)
    static int lowerBound(int[] a, int target) {      // first index with a[i] >= target
        int lo = 0, hi = a.length;                      // half-open [lo, hi)
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] < target) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    // Sliding window, variable size (see 05-sliding-window.md)
    static int longestAtMostKDistinct(String s, int k) {
        Map<Character, Integer> cnt = new HashMap<>();
        int best = 0;
        for (int l = 0, r = 0; r < s.length(); r++) {
            cnt.merge(s.charAt(r), 1, Integer::sum);
            while (cnt.size() > k) {
                char c = s.charAt(l++);
                if (cnt.merge(c, -1, Integer::sum) == 0) cnt.remove(c);
            }
            best = Math.max(best, r - l + 1);
        }
        return best;
    }

    // BFS shortest path on a grid (see 15-graphs-bfs-dfs.md)
    static int bfs(char[][] g, int sr, int sc, int tr, int tc) {
        int R = g.length, C = g[0].length;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean[][] seen = new boolean[R][C];
        Deque<int[]> q = new ArrayDeque<>();
        q.offer(new int[]{sr, sc});
        seen[sr][sc] = true;                            // mark when ENQUEUED, not when polled
        for (int steps = 0; !q.isEmpty(); steps++) {
            for (int size = q.size(); size > 0; size--) {
                int[] cur = q.poll();
                if (cur[0] == tr && cur[1] == tc) return steps;
                for (int[] d : dirs) {
                    int r = cur[0] + d[0], c = cur[1] + d[1];
                    if (r < 0 || r >= R || c < 0 || c >= C || seen[r][c] || g[r][c] == '#') continue;
                    seen[r][c] = true;
                    q.offer(new int[]{r, c});
                }
            }
        }
        return -1;
    }

    // Backtracking (see 14-backtracking.md)
    static List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        backtrack(nums, 0, new ArrayList<>(), res);
        return res;
    }
    private static void backtrack(int[] nums, int start, List<Integer> path, List<List<Integer>> res) {
        res.add(new ArrayList<>(path));
        for (int i = start; i < nums.length; i++) {
            path.add(nums[i]);
            backtrack(nums, i + 1, path, res);
            path.remove(path.size() - 1);
        }
    }

    // Union-Find (see 17-union-find.md)
    static final class DSU {
        private final int[] parent, rank;
        DSU(int n) { parent = new int[n]; rank = new int[n]; for (int i = 0; i < n; i++) parent[i] = i; }
        int find(int x) { return parent[x] == x ? x : (parent[x] = find(parent[x])); }
        boolean union(int a, int b) {
            int ra = find(a), rb = find(b);
            if (ra == rb) return false;
            if (rank[ra] < rank[rb]) { int t = ra; ra = rb; rb = t; }
            parent[rb] = ra;
            if (rank[ra] == rank[rb]) rank[ra]++;
            return true;
        }
    }

    public static void main(String[] args) {
        System.out.println(lowerBound(new int[]{1, 3, 5}, 4));              // 2
        System.out.println(longestAtMostKDistinct("eceba", 2));             // 3
        System.out.println(bfs(new char[][]{"..".toCharArray(), "..".toCharArray()}, 0, 0, 1, 1)); // 2
        System.out.println(subsets(new int[]{1, 2}));                      // [[], [1], [1, 2], [2]]
        DSU d = new DSU(3); d.union(0, 1);
        System.out.println(d.find(0) == d.find(1));                         // true
    }
}
```

---

## 15. Operation cost cheat sheet

| Structure | Access / get | Search | Insert | Delete | Notes |
|---|---|---|---|---|---|
| `int[]` | O(1) | O(n) (O(log n) sorted) | — | — | Fixed size |
| `ArrayList` | O(1) | O(n) | O(1) amortised at end, O(n) middle | O(1) end, O(n) middle | `remove(0)` is O(n) — use `ArrayDeque` |
| `LinkedList` | O(n) | O(n) | O(1) at ends | O(1) at ends | Rarely the right choice |
| `ArrayDeque` | O(1) ends | O(n) | O(1) amortised ends | O(1) ends | Stack + queue default; no nulls |
| `HashMap` / `HashSet` | O(1) avg | O(1) avg | O(1) avg | O(1) avg | Unordered |
| `LinkedHashMap` | O(1) avg | O(1) avg | O(1) | O(1) | Insertion/access order; LRU with `removeEldestEntry` |
| `TreeMap` / `TreeSet` | O(log n) | O(log n) | O(log n) | O(log n) | Sorted; floor/ceiling |
| `PriorityQueue` | O(1) peek | O(n) | O(log n) | O(log n) poll, O(n) arbitrary | Heap; not sorted iteration |
| `String` | O(1) `charAt` | O(n·m) `indexOf` | new string O(n) | new string O(n) | Immutable |
| `StringBuilder` | O(1) | O(n) | O(1) amortised append | O(n) middle | `setLength` to truncate |
| `Arrays.sort(int[])` | | | | | O(n log n), not stable (primitives) |
| `Arrays.sort(Object[])` / `Collections.sort` | | | | | O(n log n), **stable** (TimSort) |

---

## 16. Interview questions about this toolkit

These come up when you say "Java" on your résumé and then solve a problem in it.

1. **Why `ArrayDeque` instead of `Stack`?** `Stack` extends `Vector` (synchronized, legacy, exposes index access that breaks LIFO). `ArrayDeque` is unsynchronized, faster, and implements `Deque`.
2. **How does `HashMap` work, and what's the complexity?** Array of buckets indexed by `hash(key) & (n-1)`; collisions chain in a list that becomes a red-black tree at 8 entries; resizes ×2 at load factor 0.75. Average O(1), worst O(log n). (Deep dive: [`01-java/03-collections-generics.md`](../01-java/03-collections-generics.md).)
3. **Why is `(a, b) -> a - b` a bad comparator?** Integer overflow flips the sign for large magnitudes; use `Integer.compare`.
4. **Why did `map.get(x) == map.get(y)` fail only on large inputs?** `Integer` cache covers −128..127; above that, `==` compares references.
5. **`Arrays.sort` on `int[]` vs `Integer[]`?** Primitives: dual-pivot quicksort, not stable, O(n²) theoretical worst case but practically O(n log n). Objects: TimSort, stable, O(n log n) guaranteed.
6. **What's the space complexity of your recursive DFS?** O(h) call stack — O(n) for a skewed tree/long path. That's why deep grids can throw `StackOverflowError`.
7. **String concatenation in a loop — what's wrong?** Immutable strings → each `+=` copies → O(n²). Use `StringBuilder`.
