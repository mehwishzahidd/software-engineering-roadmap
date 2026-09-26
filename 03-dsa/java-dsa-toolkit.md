# ☕ Java DSA Quick Reference (for occasional reps)

> **Python is the DSA / OA / coding-interview language** in this roadmap ([README](./README.md#language-rule-python-first-one-java-rep-per-week)).
> This page exists for the **weekly Java rep**: one problem you already solved in Python, re-implemented in Java 21 from a blank file.
> It keeps Java collections fluent for the backend/résumé interview track (FlowGrid, LedgerX, ForgeCI, FlagForge are all Java).
> Deep Collections theory lives in [`01-java/03-collections-generics.md`](../01-java/03-collections-generics.md). Every code block here compiles on Java 21.

## Contents

1. [How to do a Java rep](#1-how-to-do-a-java-rep)
2. [Python → Java translation table](#2-python--java-translation-table)
3. [Collections in one file](#3-collections-in-one-file)
4. [Comparators and the `a - b` trap](#4-comparators-and-the-a---b-trap)
5. [Overflow, `Integer` caching and other traps](#5-overflow-integer-caching-and-other-traps)
6. [Node classes and traversal templates](#6-node-classes-and-traversal-templates)
7. [The Java-rep schedule](#7-the-java-rep-schedule)

---

## 1. How to do a Java rep

1. Pick the week's rep from the table in [§7](#7-the-java-rep-schedule) (it's always a problem you already solved in Python).
2. Blank Java file, **no** copying from your Python solution. Timebox: Easy 15 min, Medium 25 min.
3. Submit on LeetCode (Java). Then compare with the NeetCode/LeetCode Java solution for idioms.
4. Tick **Java rep ✓** in [`trackers/dsa-tracker.md`](../trackers/dsa-tracker.md) and write one line: *which Java trap did I hit?* (overflow, `==` on `Integer`, forgot `new ArrayList<>(path)`, …).
5. Never counts as a new problem. Never replaces a Python review.

---

## 2. Python → Java translation table

| Python | Java 21 | Watch out |
|---|---|---|
| `list` / `append` / `pop()` | `ArrayList<Integer>` / `add` / `remove(list.size() - 1)` (Java 21: `removeLast()`) | `list.remove(1)` removes **index** 1; `remove(Integer.valueOf(1))` removes the value |
| `len(x)` | `arr.length` · `s.length()` · `list.size()` | Three different spellings |
| `dict` / `d.get(k, 0)` | `HashMap<K,V>` / `getOrDefault(k, 0)` | `map.get(absent) + 1` → `NullPointerException` |
| `Counter(xs)` | `for (int x : xs) cnt.merge(x, 1, Integer::sum);` | |
| `defaultdict(list)` | `map.computeIfAbsent(k, key -> new ArrayList<>()).add(v)` | |
| `set` / `in` | `HashSet<>` / `contains` · `add` returns `false` on duplicate | |
| `collections.deque` | `ArrayDeque<>`: `offer`/`poll` (queue), `push`/`pop` (stack) | Never `java.util.Stack`; no `null` elements |
| `heapq` (min-heap) | `PriorityQueue<>` (min-heap); max-heap: `new PriorityQueue<>(Collections.reverseOrder())` | No need to negate values like in Python |
| `heapq` with tuples `(dist, node)` | `PriorityQueue<int[]>((a, b) -> Integer.compare(a[0], b[0]))` | Tuples compare lexicographically in Python; Java needs an explicit comparator |
| `bisect_left(a, x)` | hand-written lower bound, or `Collections.binarySearch` / `Arrays.binarySearch` (returns `-(insertion) - 1`) | Library search doesn't promise the *first* duplicate |
| `sortedcontainers` (not stdlib) | `TreeMap` / `TreeSet`: `floorKey`, `ceilingKey` | Java has this built in; Python's stdlib doesn't |
| `sorted(xs, key=...)` | `list.sort(Comparator.comparingInt(...))` / `Arrays.sort(Integer[], cmp)` | `Arrays.sort(int[], cmp)` doesn't exist — box first |
| `s[i]` / `s[a:b]` / `"".join(parts)` | `s.charAt(i)` / `s.substring(a, b)` / `StringBuilder` or `String.join` | Strings compare with `.equals`, never `==` |
| `ord(c) - ord('a')` | `c - 'a'` | `(char) ('a' + 1)` needs the cast |
| `int` (unbounded) | `int` (32-bit) / `long` (64-bit) | **Overflow is silent** — see [§5](#5-overflow-integer-caching-and-other-traps) |
| `//` floor division | `/` truncates toward zero; `Math.floorDiv` floors | `-7 / 2 == -3` in Java, `-7 // 2 == -4` in Python |
| `%` (result has divisor's sign) | `%` (result has dividend's sign); `Math.floorMod` | `-7 % 3 == -1` in Java, `2` in Python |
| `float('inf')` | `Integer.MAX_VALUE` (don't add to it!) or `Long.MAX_VALUE` | `MAX_VALUE + 1` wraps negative |
| `@functools.cache` | `int[] memo` / `HashMap<Long, Integer>` + `Arrays.fill(memo, -1)` | |
| `sys.setrecursionlimit` | Thread with a bigger stack, or iterate with `ArrayDeque` | Default stack handles ~5–20k frames |
| `path[:]` / `list(path)` | `new ArrayList<>(path)` | Same aliasing bug in both languages |

---

## 3. Collections in one file

```java
import java.util.*;

class CollectionsInOneFile {
    public static void main(String[] args) {
        // HashMap counting and grouping
        Map<String, Integer> count = new HashMap<>();
        for (String w : List.of("a", "b", "a")) count.merge(w, 1, Integer::sum);
        Map<Integer, List<String>> byLen = new HashMap<>();
        for (String w : List.of("hi", "yo", "hey")) byLen.computeIfAbsent(w.length(), k -> new ArrayList<>()).add(w);
        for (Map.Entry<String, Integer> e : count.entrySet()) System.out.print(e.getKey() + "=" + e.getValue() + " ");
        System.out.println(byLen);

        // HashSet
        Set<Integer> seen = new HashSet<>();
        System.out.println(seen.add(3) + " " + seen.add(3));          // true false

        // ArrayDeque as stack and as queue
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(1); stack.push(2);
        System.out.println(stack.pop() + " " + stack.peek());          // 2 1
        Queue<int[]> q = new ArrayDeque<>();
        q.offer(new int[]{0, 0});
        System.out.println(q.poll()[0] + " " + q.isEmpty());           // 0 true

        // PriorityQueue: min-heap, max-heap, heap of int[] by second field
        PriorityQueue<Integer> min = new PriorityQueue<>(List.of(5, 1, 3));
        PriorityQueue<Integer> max = new PriorityQueue<>(Collections.reverseOrder());
        max.addAll(List.of(5, 1, 3));
        PriorityQueue<int[]> byDist = new PriorityQueue<>((a, b) -> Integer.compare(a[1], b[1]));
        byDist.offer(new int[]{7, 10}); byDist.offer(new int[]{8, 2});
        System.out.println(min.poll() + " " + max.poll() + " " + byDist.poll()[0]);   // 1 5 8

        // TreeMap floor / ceiling
        TreeMap<Integer, String> tm = new TreeMap<>(Map.of(10, "a", 20, "b"));
        System.out.println(tm.floorKey(15) + " " + tm.ceilingKey(15) + " " + tm.floorKey(5)); // 10 20 null

        // Arrays and strings
        int[] arr = {3, 1, 2};
        Arrays.sort(arr);
        int[] memo = new int[5];
        Arrays.fill(memo, -1);
        int[][] intervals = {{5, 6}, {1, 3}};
        Arrays.sort(intervals, (x, y) -> Integer.compare(x[0], y[0]));
        StringBuilder sb = new StringBuilder();
        for (char c : "abc".toCharArray()) sb.append((char) (c + 1));
        System.out.println(Arrays.toString(arr) + " " + memo[0] + " " + intervals[0][0] + " " + sb);  // [1, 2, 3] -1 1 bcd

        // Snapshot copies (backtracking)
        List<List<Integer>> res = new ArrayList<>();
        List<Integer> path = new ArrayList<>(List.of(1, 2));
        res.add(new ArrayList<>(path));
        path.remove(path.size() - 1);
        System.out.println(res + " " + path);                          // [[1, 2]] [1]
    }
}
```

---

## 4. Comparators and the `a - b` trap

```java
import java.util.*;

class ComparatorDemo {
    record Task(String name, int priority, long createdAt) {}

    public static void main(String[] args) {
        Comparator<Integer> bad = (a, b) -> a - b;                     // overflows for large magnitudes
        System.out.println(bad.compare(Integer.MIN_VALUE, 1));         // 2147483647 → claims MIN_VALUE > 1 !
        Comparator<Integer> good = Integer::compare;
        System.out.println(good.compare(Integer.MIN_VALUE, 1));        // -1

        // Combinators: priority descending, then oldest first
        PriorityQueue<Task> pq = new PriorityQueue<>(
                Comparator.comparingInt(Task::priority).reversed().thenComparingLong(Task::createdAt));
        pq.offer(new Task("a", 1, 5));
        pq.offer(new Task("b", 3, 9));
        pq.offer(new Task("c", 3, 2));
        System.out.println(pq.poll().name());                          // c
    }
}
```

Rule: always `Integer.compare` / `Long.compare` / `Comparator.comparingInt`. Python never has this bug (unbounded ints), which is exactly why Java reps matter.

---

## 5. Overflow, `Integer` caching and other traps

```java
import java.util.*;

class JavaTraps {
    public static void main(String[] args) {
        int big = 2_000_000_000;
        System.out.println(big + big);                      // -294967296 — silent wraparound
        System.out.println((long) big + big);               // 4000000000 — cast BEFORE the operation
        int lo = 1_500_000_000, hi = 2_000_000_000;
        System.out.println(lo + (hi - lo) / 2);             // safe midpoint (lo + hi overflows)
        System.out.println(Math.abs(Integer.MIN_VALUE));    // -2147483648 (!)
        System.out.println(-7 / 2 + " " + Math.floorDiv(-7, 2) + " " + (-7 % 3) + " " + Math.floorMod(-7, 3)); // -3 -4 -1 2

        Integer a = 127, b = 127, c = 128, d = 128;
        System.out.println((a == b) + " " + (c == d) + " " + c.equals(d));   // true false true
        Map<Character, Integer> m1 = new HashMap<>(), m2 = new HashMap<>();
        for (int i = 0; i < 200; i++) { m1.merge('x', 1, Integer::sum); m2.merge('x', 1, Integer::sum); }
        System.out.println(m1.get('x') == m2.get('x'));                     // false — use equals()

        List<Integer> fixed = List.of(1, 2, 3);                              // immutable
        try { fixed.add(4); } catch (UnsupportedOperationException e) { System.out.println("List.of is immutable"); }
        String s1 = new String("hi");
        System.out.println((s1 == "hi") + " " + s1.equals("hi"));           // false true
    }
}
```

| Trap | Python behaviour | Java behaviour | Fix in Java |
|---|---|---|---|
| Big sums/products | exact | silent overflow | `long`, cast first, `Math.addExact` |
| `==` on boxed numbers / strings | `==` compares values | compares references (cache −128..127) | `.equals()` |
| Negative `/` and `%` | floor | truncate toward zero | `Math.floorDiv`, `Math.floorMod` |
| Deep recursion | `RecursionError` at ~1000 | `StackOverflowError` at ~10⁴ | iterate with `ArrayDeque` |
| Missing map key | `KeyError` / `.get` → `None` | `get` → `null` → NPE on unboxing | `getOrDefault`, `merge` |
| String building in a loop | `+=` is O(n²)-ish too | `+=` is O(n²) | `StringBuilder` |

---

## 6. Node classes and traversal templates

```java
import java.util.*;

class ListNode {
    int val; ListNode next;
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

class TreeNode {
    int val; TreeNode left, right;
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
}

class Traversals {
    static ListNode reverse(ListNode head) {
        ListNode prev = null;
        while (head != null) { ListNode nxt = head.next; head.next = prev; prev = head; head = nxt; }
        return prev;
    }

    static List<List<Integer>> levelOrder(TreeNode root) {
        List<List<Integer>> out = new ArrayList<>();
        if (root == null) return out;
        Deque<TreeNode> q = new ArrayDeque<>();
        q.offer(root);
        while (!q.isEmpty()) {
            List<Integer> level = new ArrayList<>();
            for (int size = q.size(); size > 0; size--) {
                TreeNode n = q.poll();
                level.add(n.val);
                if (n.left != null) q.offer(n.left);
                if (n.right != null) q.offer(n.right);
            }
            out.add(level);
        }
        return out;
    }

    static int bfsGrid(char[][] g, int sr, int sc, int tr, int tc) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        boolean[][] seen = new boolean[g.length][g[0].length];
        Deque<int[]> q = new ArrayDeque<>();
        q.offer(new int[]{sr, sc});
        seen[sr][sc] = true;                                   // mark when enqueued
        for (int steps = 0; !q.isEmpty(); steps++) {
            for (int size = q.size(); size > 0; size--) {
                int[] cur = q.poll();
                if (cur[0] == tr && cur[1] == tc) return steps;
                for (int[] d : dirs) {
                    int r = cur[0] + d[0], c = cur[1] + d[1];
                    if (r < 0 || r >= g.length || c < 0 || c >= g[0].length || seen[r][c] || g[r][c] == '#') continue;
                    seen[r][c] = true;
                    q.offer(new int[]{r, c});
                }
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        ListNode h = reverse(new ListNode(1, new ListNode(2, new ListNode(3))));
        System.out.println(h.val + " " + h.next.val);                                   // 3 2
        TreeNode t = new TreeNode(1, new TreeNode(2), new TreeNode(3, new TreeNode(4), null));
        System.out.println(levelOrder(t));                                              // [[1], [2, 3], [4]]
        System.out.println(bfsGrid(new char[][]{"..".toCharArray(), "#.".toCharArray()}, 0, 0, 1, 1)); // 2
    }
}
```

---

## 7. The Java-rep schedule

One rep per week, always a problem already solved in Python that week (or earlier). The pattern guide's §4 shows the Java idioms involved.

| Week | Rep | Java skill practised | Guide |
|---:|---|---|---|
| 1 | 189 Rotate Array | `int[]`, in-place swaps, `k %= n` | [01](./01-arrays-strings.md) |
| 2 | 49 Group Anagrams | `HashMap` + `computeIfAbsent`, `char[]` → `String` key | [02](./02-hashing.md) |
| 3 | 560 Subarray Sum Equals K | `merge`, `getOrDefault` | [04](./04-prefix-sums.md) |
| 4 | 3 Longest Substring Without Repeating Characters | `int[128]`, `charAt` | [05](./05-sliding-window.md) |
| 5 | 739 Daily Temperatures | `ArrayDeque` as a stack of indices | [06](./06-stack-queue.md) |
| 6 | 875 Koko Eating Bananas | `long` accumulation, overflow-safe midpoint | [07](./07-binary-search.md) |
| 7 | 146 LRU Cache | `HashMap` + hand-written doubly linked list | [08](./08-linked-lists.md) |
| 8 | 50 Pow(x, n) | `long` for `-Integer.MIN_VALUE` | [09](./09-recursion.md) |
| 9 | 102 Binary Tree Level Order Traversal | `TreeNode`, `ArrayDeque` BFS | [10](./10-trees.md) |
| 10 | 98 Validate Binary Search Tree | `long` bounds | [11](./11-bst.md) |
| 11 | 973 K Closest Points to Origin | `PriorityQueue<int[]>` + comparator | [13](./13-heap-priority-queue.md) |
| 12 | 78 Subsets | `new ArrayList<>(path)`, `remove(size - 1)` | [14](./14-backtracking.md) |
| 13 | 994 Rotting Oranges | grid BFS with `int[]` cells | [15](./15-graphs-bfs-dfs.md) |
| 14 | 207 Course Schedule | `List<List<Integer>>` adjacency, in-degree array | [16](./16-topological-sort.md) |
| 15 | 322 Coin Change | `int[] dp`, `Arrays.fill`, sentinel choice | [18](./18-dp-1d.md) |
| 16 | 56 Merge Intervals | `Arrays.sort(int[][], cmp)`, `List<int[]>` → `toArray` | [19](./19-intervals.md) |
| 17 | 763 Partition Labels | `int[26]` last index, `List<Integer>` | [20](./20-greedy.md) |
| 18 | 1143 Longest Common Subsequence | `int[][]` table | [21](./21-dp-2d.md) |
| 19 | 743 Network Delay Time | Dijkstra with `PriorityQueue<int[]>` | [22](./22-advanced-graphs.md) |
| 20–26 | One rep/week from your **weakest** patterns (tracker confidence ≤ 2) | — | — |
