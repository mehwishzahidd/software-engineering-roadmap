# 03 — Collections & Generics (Week 2)

> **Outcome:** pick the right collection in 5 seconds (production code and your weekly Java DSA rep), state its
> complexity, explain `HashMap` and `ArrayList` internals from memory, sort with
> `Comparator` chains, and write/read generic signatures including wildcards.

Related: [README](./README.md) · prev [02-oop.md](./02-oop.md) · next [04-modern-java.md](./04-modern-java.md) · Java DSA-rep cheat sheet [../03-dsa/java-dsa-toolkit.md](../03-dsa/java-dsa-toolkit.md) (DSA itself is solved in Python — [../19-python/README.md](../19-python/README.md)) · hashing pattern [../03-dsa/02-hashing.md](../03-dsa/02-hashing.md)

---

## 1. The map of the framework

```
Iterable
 └─ Collection
     ├─ List        ArrayList, LinkedList            (ordered, index, duplicates)
     ├─ Set         HashSet, LinkedHashSet, TreeSet  (no duplicates)
     │   └─ SortedSet/NavigableSet → TreeSet
     └─ Queue       PriorityQueue, ArrayDeque, LinkedList
         └─ Deque   ArrayDeque, LinkedList           (both ends: stack + queue)
Map (not a Collection)  HashMap, LinkedHashMap, TreeMap, ConcurrentHashMap
```

### Choosing (memorize this table)

| Need | Use | Key ops cost |
|---|---|---|
| Indexed list, append, iterate | `ArrayList` | get O(1), add-at-end amortized O(1), insert/remove middle O(n) |
| Key → value lookup | `HashMap` | get/put/remove O(1) average |
| Membership / dedupe | `HashSet` | add/contains O(1) average |
| Insertion-order map/set (or LRU) | `LinkedHashMap` / `LinkedHashSet` | O(1) + ordered iteration |
| Sorted keys, floor/ceiling, range | `TreeMap` / `TreeSet` (red-black tree) | O(log n) |
| Stack (LIFO) | `ArrayDeque` (`push`/`pop`/`peek`) | O(1) — **not** `java.util.Stack` |
| Queue (FIFO), BFS | `ArrayDeque` (`offer`/`poll`/`peek`) | O(1) |
| Min/max repeatedly, top-K | `PriorityQueue` (binary heap) | offer/poll O(log n), peek O(1) |
| Thread-safe map | `ConcurrentHashMap` | see [07-concurrency.md](./07-concurrency.md) |

`LinkedList` is almost never the right answer: O(n) `get(i)`, poor cache locality, 24+ bytes
overhead per node. `ArrayDeque` beats it as a queue. `Stack` and `Vector` are legacy synchronized classes.

---

## 2. Lists

```java
List<String> names = new ArrayList<>();
names.add("ana"); names.add("bo"); names.add(0, "cy");   // [cy, ana, bo]
names.get(1);                  // "ana"
names.set(1, "ANA");
names.remove("bo");            // by object → true
names.remove(0);               // by index  → "cy"
names.contains("ANA");         // O(n) — uses equals
names.indexOf("ANA");
Collections.sort(names);       // or names.sort(null) / names.sort(Comparator.naturalOrder())

List<Integer> nums = new ArrayList<>(List.of(10, 20, 30));
nums.remove(Integer.valueOf(10));  // removes value 10
nums.remove(1);                    // removes INDEX 1 (value 30 now) — overload trap!
```

**Immutable factories (Java 9+):** `List.of`, `Set.of`, `Map.of`, `List.copyOf`. They throw
`UnsupportedOperationException` on mutation and **reject `null`** elements. `Arrays.asList`
is different: fixed-size (set allowed, add/remove throw) and backed by the array.

### Under the hood: `ArrayList`

- Backed by `Object[] elementData` + `int size`.
- `new ArrayList<>()` starts with a shared empty array; the first `add` allocates capacity **10**.
- When full, grows to **old + old/2 (×1.5)** via `Arrays.copyOf` → O(n) copy, but amortized O(1) per add (geometric growth).
- `remove(i)` shifts everything after `i` left with `System.arraycopy` → O(n).
- If you know the size, `new ArrayList<>(n)` avoids resizes.
- Stores references; `ArrayList<Integer>` boxes every int.

---

## 3. Maps

```java
Map<String, Integer> counts = new HashMap<>();
for (String w : words) counts.merge(w, 1, Integer::sum);      // idiomatic counting
counts.getOrDefault("x", 0);
counts.putIfAbsent("y", 0);
counts.computeIfAbsent("groceries", k -> 0);

Map<String, List<Transaction>> byCategory = new HashMap<>();
byCategory.computeIfAbsent(tx.category(), k -> new ArrayList<>()).add(tx);  // multimap idiom

for (Map.Entry<String, Integer> e : counts.entrySet()) {       // iterate entries, not keySet+get
    System.out.println(e.getKey() + "=" + e.getValue());
}
counts.entrySet().removeIf(e -> e.getValue() < 2);            // safe removal while iterating
```

### Under the hood: HashMap

```
table (Node<K,V>[]; length is a power of two, default 16)
 index = (n - 1) & hash          where hash = h ^ (h >>> 16),  h = key.hashCode()
 ┌───┬───┬───┬───┬ ... ┐
 │ 0 │ 1 │ 2 │ 3 │     │
 └─┬─┴───┴─┬─┴───┴ ... ┘
   │       └─► Node(k,v) ─► Node(k,v)          (linked list on collision)
   └─► TreeNode ... (red-black tree once a bin has ≥ 8 nodes AND table ≥ 64)
```

1. **Hash spreading:** `h ^ (h >>> 16)` mixes high bits into low bits because the index uses only the low bits (`& (n-1)`).
2. **put:** find bucket; walk the bin comparing `hash ==` then `equals`; replace value if found, else append.
3. **Resize:** when `size > capacity × loadFactor` (default 0.75 → first resize at 13th entry for capacity 16), capacity **doubles** and entries are split: each node stays at index `i` or moves to `i + oldCap` (one extra bit of hash decides). O(n) occasionally, amortized O(1).
4. **Treeification (Java 8+):** a bin with **≥ 8** entries becomes a red-black tree (if table capacity ≥ 64; otherwise the table resizes instead). Shrinks back to a list at ≤ 6. Worst case per lookup goes from O(n) to O(log n) — defence against bad hash codes / hash-flooding.
5. **null:** one `null` key allowed (hash 0 → bucket 0), `null` values allowed. (`ConcurrentHashMap` allows neither.)
6. **Not thread-safe**; concurrent modification can lose updates or corrupt structure.
7. **Iteration order is unspecified** and can change after a resize. Need order? `LinkedHashMap` (insertion or access order) or `TreeMap` (sorted).

`HashSet` is literally a `HashMap<E, Object>` with a dummy value.

### `LinkedHashMap` as an LRU cache (classic interview + DSA: LRU Cache, LeetCode 146)

```java
class LruCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    LruCache(int capacity) { super(16, 0.75f, true); this.capacity = capacity; } // accessOrder=true
    @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) { return size() > capacity; }
}
```
In an interview, expect to also implement it by hand with `HashMap<K, Node>` + doubly linked list.

### `TreeMap` navigation

```java
TreeMap<LocalDate, BigDecimal> balances = new TreeMap<>();
balances.floorKey(date);          // greatest key ≤ date (or null)
balances.ceilingEntry(date);      // least entry ≥ date
balances.headMap(date, false);    // keys < date (view)
balances.firstKey(); balances.lastEntry(); balances.pollFirstEntry();
```

---

## 4. Deque, Queue, PriorityQueue

```java
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1); stack.push(2); stack.peek(); /* 2 */ stack.pop(); /* 2 */

Queue<int[]> q = new ArrayDeque<>();      // BFS
q.offer(new int[]{0, 0});
while (!q.isEmpty()) { int[] cell = q.poll(); /* ... */ }

PriorityQueue<Integer> minHeap = new PriorityQueue<>();
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
PriorityQueue<int[]> byDist = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
```

- `ArrayDeque` rejects `null` (it uses null as the empty-slot marker).
- `offer/poll/peek` return `false`/`null` on failure; `add/remove/element` throw.
- **`PriorityQueue` iteration is NOT sorted** — only `poll()` gives elements in order. `System.out.println(pq)` shows heap array order.
- `PriorityQueue` under the hood: array-based binary heap; children of `i` at `2i+1`, `2i+2`; `offer` sifts up, `poll` moves last to root and sifts down. Building from a collection is O(n) (heapify).

---

## 5. Iteration and fail-fast iterators

```java
List<Integer> xs = new ArrayList<>(List.of(1, 2, 3, 4));
for (Integer x : xs) if (x % 2 == 0) xs.remove(x);   // ❌ ConcurrentModificationException

Iterator<Integer> it = xs.iterator();
while (it.hasNext()) if (it.next() % 2 == 0) it.remove();   // ✅
xs.removeIf(x -> x % 2 == 0);                               // ✅ simplest
```
**Under the hood:** collections keep a `modCount`; the iterator snapshots it and throws
`ConcurrentModificationException` when it changes by any path other than the iterator itself.
It's a *bug detector* for single-threaded misuse, not a concurrency guarantee.

---

## 6. `Comparable` and `Comparator`

```java
public record Transaction(LocalDate date, String merchant, BigDecimal amount) implements Comparable<Transaction> {
    @Override public int compareTo(Transaction o) { return date.compareTo(o.date); } // natural order
}

List<Transaction> txs = new ArrayList<>(loaded);
txs.sort(Comparator.comparing(Transaction::date)
        .thenComparing(Transaction::amount, Comparator.reverseOrder())
        .thenComparing(Transaction::merchant, String.CASE_INSENSITIVE_ORDER));

Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));  // int[][] in DSA
```

- `compareTo` returns negative/zero/positive. **Never** `return a - b;` for arbitrary ints — overflows (e.g. `Integer.MIN_VALUE - 1`). Use `Integer.compare(a, b)`.
- Keep `compareTo` **consistent with equals**, or `TreeSet`/`TreeMap` will treat compare-equal objects as duplicates (a `TreeSet` sorted only by date drops different transactions on the same day!).
- `Collections.sort`/`List.sort` on objects is a **stable** merge sort (TimSort). `Arrays.sort(int[])` is dual-pivot quicksort (not stable, but stability is meaningless for primitives).

---

## 7. Generics

```java
public final class Pair<A, B> {
    private final A first; private final B second;
    public Pair(A first, B second) { this.first = first; this.second = second; }
    public A first() { return first; }
    public B second() { return second; }
}

static <T extends Comparable<? super T>> T max(List<? extends T> list) {
    if (list.isEmpty()) throw new NoSuchElementException();
    T best = list.get(0);
    for (T t : list) if (t.compareTo(best) > 0) best = t;
    return best;
}
```

### Wildcards and PECS ("Producer Extends, Consumer Super")

```java
// Reads Numbers out of the list → producer → extends
static double sum(List<? extends Number> xs) { double s = 0; for (Number n : xs) s += n.doubleValue(); return s; }
// Writes Integers into the list → consumer → super
static void fillOnes(List<? super Integer> out, int n) { for (int i = 0; i < n; i++) out.add(1); }

sum(List.of(1, 2.5, 3L));            // List<Number> fine; List<Integer> also fine
fillOnes(new ArrayList<Number>(), 3); // fine; List<Object> fine
```

**Invariance:** `List<Integer>` is **not** a `List<Number>`, otherwise you could add a
`Double` to a list of integers. Arrays *are* covariant (`Integer[]` is a `Number[]`), which is
why `Number[] a = new Integer[1]; a[0] = 1.5;` compiles and throws `ArrayStoreException` at runtime.

### Type erasure (under the hood)

Generics exist at compile time only. `List<String>` and `List<Integer>` are both `List` at
runtime; the compiler inserts casts. Consequences:
- No `new T()`, no `new T[n]`, no `instanceof List<String>`, no primitive type arguments (`List<int>`).
- Can't overload `m(List<String>)` and `m(List<Integer>)` — same erasure.
- **Raw types** (`List list = new ArrayList();`) turn off checks: heap pollution, `ClassCastException` far from the cause. Never use them.

---

## 8. Autoboxing in collections

```java
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) freq.put(c, freq.getOrDefault(c, 0) + 1);  // boxes each time
int[] freq26 = new int[26];                                                 // DSA: faster, no boxing
for (char c : s.toCharArray()) freq26[c - 'a']++;

Integer a = 127, b = 127;  a == b;   // true  (Integer cache −128..127)
Integer c = 128, d = 128;  c == d;   // false (distinct objects) → use equals
Map<String, Integer> m = new HashMap<>();
int x = m.get("missing");            // NullPointerException: auto-unboxing null
```
Details in [06-memory-jvm.md](./06-memory-jvm.md#5-autoboxing-and-the-integer-cache).

---

## 🔨 Break it

1. Insert 1 000 000 entries into `new ArrayList<>()` vs `new ArrayList<>(1_000_000)`; time both.
2. Create a key class whose `hashCode()` returns `42`. Insert 10 000 keys into a `HashMap`, time `get`. Then make the class `Comparable` and re-time (treeified bins use `compareTo` to order).
3. Remove from a list inside a for-each → read the `ConcurrentModificationException` stack trace.
4. `TreeSet<Transaction>` with a comparator on date only. Add two different transactions on the same date → size 1.
5. `new PriorityQueue<>(List.of(5, 1, 4, 2))` and print it; then poll until empty.
6. `List<Integer> l = new ArrayList<>(List.of(1,2,3)); l.remove(1);` — which one was removed?
7. `List.of(1, null)` → NPE. `Map.of("a", 1, "a", 2)` → `IllegalArgumentException` (duplicate key).
8. Comparator `(a, b) -> a - b` sorting `[Integer.MIN_VALUE, 1]` → wrong order.

## ⚠️ Common mistakes

- `java.util.Stack` for stacks (legacy, synchronized, extends `Vector`) — use `ArrayDeque`.
- Iterating `map.keySet()` then `map.get(k)` — use `entrySet()`.
- Relying on `HashMap` iteration order in tests.
- Mutable keys in hash maps.
- `==` on `Integer`/`Long` values pulled from collections.
- Returning internal lists directly — callers mutate your state.
- `a - b` comparators.

## 🎤 Interview questions

<details><summary>1. How does HashMap work internally?</summary>

Array of buckets (power-of-two size). Index = `(n-1) & (h ^ h>>>16)`. Collisions chain in a
linked list; bins with ≥ 8 entries become red-black trees (when capacity ≥ 64). Lookup: hash →
bucket → compare hash then `equals`. Resizes (doubles) when size exceeds capacity × 0.75,
redistributing entries to `i` or `i + oldCap`. Average O(1), worst O(log n) since Java 8.
</details>

<details><summary>2. ArrayList vs LinkedList?</summary>

`ArrayList`: contiguous array, O(1) random access, amortized O(1) append, O(n) middle insert,
cache-friendly. `LinkedList`: doubly linked nodes, O(n) index access, O(1) insert/remove *given
a node/iterator position*, big per-node overhead. In practice `ArrayList` (or `ArrayDeque` for
queues) wins almost always.
</details>

<details><summary>3. HashMap vs TreeMap vs LinkedHashMap?</summary>

HashMap: O(1) avg, no order. TreeMap: red-black tree, O(log n), sorted keys + navigation
(`floorKey`, `subMap`). LinkedHashMap: HashMap + doubly linked list for insertion or access
order; enables LRU via `removeEldestEntry`.
</details>

<details><summary>4. What happens if two keys have the same hashCode?</summary>

They land in the same bucket; `equals` distinguishes them. Both are stored. Many collisions
degrade that bucket to a list (O(n)) or tree (O(log n)).
</details>

<details><summary>5. What is a ConcurrentModificationException and how do you avoid it?</summary>

Thrown by fail-fast iterators when the collection's `modCount` changes outside the iterator.
Use `Iterator.remove`, `removeIf`, collect-then-remove, or a concurrent collection.
</details>

<details><summary>6. Comparable vs Comparator?</summary>

`Comparable` defines the class's single natural order (`compareTo`, implemented by the class).
`Comparator` is an external, composable ordering (`comparing().thenComparing().reversed()`),
any number of them, usable for classes you don't own.
</details>

<details><summary>7. What is type erasure? What can't you do because of it?</summary>

Generic type arguments are removed after compilation; runtime sees raw types plus casts.
You can't create `new T()`/`new T[]`, check `instanceof List<String>`, use primitives as type
args, or overload on generic parameter types alone.
</details>

<details><summary>8. Explain PECS.</summary>

Producer Extends, Consumer Super. If a parameter only supplies values to you, use
`? extends T`; if it only receives values, use `? super T`. Example: `Collections.copy(List<? super T> dest, List<? extends T> src)`.
</details>

<details><summary>9. Why is iterating a PriorityQueue not sorted?</summary>

It's a binary heap: only the root is guaranteed minimal. The backing array is partially
ordered. Repeated `poll()` yields sorted order (that's heapsort).
</details>

<details><summary>10. Why can't ConcurrentHashMap hold null?</summary>

Ambiguity: `get(k) == null` could mean "absent" or "mapped to null", and in a concurrent map
you can't follow up with `containsKey` atomically. So nulls are banned.
</details>

## ✅ Mastery checklist

- [ ] Recite the collection-choice table with complexities
- [ ] Draw `HashMap` buckets, explain hash spreading, resize at 0.75, treeify at 8/64
- [ ] Explain `ArrayList` growth (×1.5) and amortized O(1) append
- [ ] Use `merge`, `computeIfAbsent`, `getOrDefault` fluently
- [ ] Use `ArrayDeque` as stack and queue; `PriorityQueue` as min/max heap with a comparator
- [ ] Write a multi-key `Comparator` chain without `a - b`
- [ ] Write a generic method with a bounded type parameter and a PECS wildcard
- [ ] Explain type erasure and array covariance vs generic invariance
- [ ] Complete the Week 2 collections exercises in [exercises.md](./exercises.md#week-2--oop-exceptions-collections-maven--junit)
