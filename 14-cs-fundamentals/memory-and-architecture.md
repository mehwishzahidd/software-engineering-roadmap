# Memory & Computer Architecture (Week 5)

> **Practical use:** understand why `ArrayList` beats `LinkedList` in practice, why money in LedgerX is
> `BigDecimal`, why `Integer.MAX_VALUE + 1` is negative, why a DB query that touches disk is 1000×
> slower than one served from memory, and what a Java object actually costs.
> **Interview use:** stack vs heap, integer overflow, two's complement, cache locality, latency numbers.

Pairs with [`01-java/06-memory-jvm.md`](../01-java/06-memory-jvm.md) (GC, JVM memory areas) — this file is the hardware/number side.

---

## 1. The machine in one picture

```
┌─────────────────────────── CPU package ───────────────────────────┐
│  Core 0                         Core 1                            │
│  ┌──────────────┐               ┌──────────────┐                  │
│  │ registers    │ ~0.3 ns       │ registers    │                  │
│  │ L1 32–64 KB  │ ~1 ns         │ L1           │                  │
│  │ L2 ~1–2 MB   │ ~4 ns         │ L2           │                  │
│  └──────┬───────┘               └──────┬───────┘                  │
│         └──────────── L3 (shared, tens of MB) ~10–40 ns ─────────┘│
└──────────────────────────────┬────────────────────────────────────┘
                               │ memory bus
                        RAM (GBs) ~100 ns
                               │
                  SSD (NVMe) ~20–100 µs · HDD ~5–10 ms
                               │
                    Network: same DC ~0.5 ms RTT · cross-continent ~100–150 ms RTT
```

| Component | Role |
|---|---|
| **Registers** | Tiny, fastest storage inside the core; operands of instructions |
| **ALU / control unit** | Execute instructions: fetch → decode → execute → write back |
| **Caches (L1/L2/L3)** | Hardware-managed copies of recently used memory, moved in **64-byte cache lines** |
| **RAM** | Main memory, volatile, byte-addressable |
| **Disk (SSD/HDD)** | Persistent, block-addressed (4 KB pages typical) |
| **Clock** | ~3 GHz → one cycle ≈ 0.33 ns |

Modern cores also pipeline instructions, execute out of order, and **predict branches**. A
mispredicted branch costs ~5 ns. That's why sorted data can make a branchy loop faster — a fun
fact, not something to optimize in interviews.

---

## 2. Latency numbers every engineer should know (orders of magnitude)

| Operation | Approx. time | Scaled: if L1 = 1 second |
|---|---:|---:|
| L1 cache reference | 1 ns | 1 s |
| Branch mispredict | 5 ns | 5 s |
| L2 cache reference | 4 ns | 4 s |
| Mutex lock/unlock (uncontended) | ~20 ns | 20 s |
| Main memory reference | 100 ns | ~1.5 min |
| Compress 1 KB (fast codec) | ~2 µs | ~30 min |
| Read 1 MB sequentially from RAM | ~10–50 µs | hours |
| SSD random read (4 KB) | ~20–100 µs | ~1 day |
| Round trip within same datacenter | ~500 µs | ~6 days |
| Read 1 MB sequentially from SSD | ~0.2–1 ms | ~1 week |
| HDD seek | ~5–10 ms | ~3 months |
| Round trip US ↔ Europe | ~100–150 ms | ~4 years |

**How to use this in practice/interviews:**
- One network round trip to Postgres (~0.5 ms) ≈ 5,000 RAM reads. **N+1 queries** hurt because of
  round trips, not SQL complexity → see [`05-spring-boot/03-data-jpa.md`](../05-spring-boot/03-data-jpa.md).
- A Redis cache hit (~0.2–0.5 ms incl. network) beats a Postgres query that hits disk (ms).
- Caching in-process (a `HashMap`) beats Redis by 1000× but isn't shared across instances.
- In system design estimation, these numbers turn "is this fast enough?" into arithmetic → [`15-system-design/fundamentals.md`](../15-system-design/fundamentals.md).

---

## 3. Caches and locality

Caches work because programs exhibit:
- **Temporal locality** — recently used data is used again soon (loop variables, hot rows).
- **Spatial locality** — data near recently used data is used soon (next array element).

Memory moves in **cache lines** of 64 bytes. Touch `arr[0]` of an `int[]` and `arr[1..15]` come along for free.

```java
int[][] grid = new int[4096][4096];

// Row-major: walks memory sequentially → cache friendly
for (int r = 0; r < n; r++)
    for (int c = 0; c < n; c++) sum += grid[r][c];

// Column-major: jumps 4096 ints (16 KB) each step → a cache miss nearly every access,
// typically several times slower for the same Big-O
for (int c = 0; c < n; c++)
    for (int r = 0; r < n; r++) sum += grid[r][c];
```

Consequences you'll actually meet:
- **`ArrayList` vs `LinkedList`:** both O(n) iteration, but `ArrayList` stores references contiguously; `LinkedList` nodes are scattered on the heap → pointer chasing, cache misses, plus 24+ bytes overhead per node. `ArrayList`/`ArrayDeque` win almost always.
- **`int[]` vs `List<Integer>`:** primitives are contiguous; boxed `Integer`s are separate heap objects.
- **Database pages** (8 KB in Postgres) and the OS page cache are the same idea one level down: read a block, reuse it.
- **False sharing** (awareness): two threads writing different variables on the same cache line slow each other down.

---

## 4. Numbers in binary

### 4.1 Bases

| Decimal | Binary | Hex |
|---:|---|---|
| 0 | 0000 | 0x0 |
| 5 | 0101 | 0x5 |
| 10 | 1010 | 0xA |
| 15 | 1111 | 0xF |
| 255 | 1111 1111 | 0xFF |
| 256 | 1 0000 0000 | 0x100 |

One hex digit = 4 bits; one byte = 2 hex digits. You see hex in: memory addresses, colors
(`#1E90FF`), UUIDs, SHA hashes (git commit IDs), `hashCode()` in default `toString()` (`Object@1b6d3586`).

```java
Integer.toBinaryString(10);      // "1010"
Integer.toHexString(255);        // "ff"
Integer.parseInt("ff", 16);      // 255
int mask = 0b1010_1010;          // binary literal with underscores
int color = 0x1E90FF;
```

### 4.2 Two's complement

Java's `byte/short/int/long` are **signed two's complement**. For an `n`-bit number, the top bit has
weight −2ⁿ⁻¹.

```
 8-bit examples
 0000 0101 =   5
 1111 1011 =  -5     (invert 0000 0101 → 1111 1010, add 1 → 1111 1011)
 0111 1111 =  127    (Byte.MAX_VALUE)
 1000 0000 = -128    (Byte.MIN_VALUE)
```

Why two's complement: addition works the same for signed and unsigned; one zero; hardware is simple.
Range for `int`: −2³¹ … 2³¹−1 = −2,147,483,648 … 2,147,483,647 (≈ ±2.1 billion).

### 4.3 Integer overflow in Java — silent wraparound

```java
int max = Integer.MAX_VALUE;
System.out.println(max + 1);               // -2147483648  (no exception!)
System.out.println(Math.abs(Integer.MIN_VALUE)); // -2147483648 (still negative)

// Classic bug #1: binary search midpoint
int mid = (lo + hi) / 2;                   // overflows when lo + hi > MAX_VALUE
int safeMid = lo + (hi - lo) / 2;          // OK
int alsoSafe = (lo + hi) >>> 1;            // unsigned shift, OK for non-negative lo/hi

// Classic bug #2: arithmetic happens in int, THEN widens
int days = 30;
long wrong = days * 24 * 60 * 60 * 1000;   // int overflow → -1702967296
long right = days * 24L * 60 * 60 * 1000;  // 2592000000

// Classic bug #3: negative hash bucket
int bucket = key.hashCode() % n;           // can be negative!
int ok = Math.floorMod(key.hashCode(), n); // always 0..n-1

// Fail loudly instead of wrapping
Math.addExact(max, 1);                     // throws ArithmeticException
Math.multiplyExact(a, b);
Math.toIntExact(someLong);
```

DSA relevance: sums of large arrays (use `long`), products in DP, `Integer.compare(a, b)` instead
of `a - b` in comparators (subtraction overflows for large magnitudes). Your NeetCode solutions are
in Python, whose `int` is arbitrary-precision and never overflows — so this is a classic trap when
you re-implement a solution in Java (the weekly Java rep) or discuss Java in an interview.

### 4.4 Bit operations (enough for [`03-dsa/23-bit-manipulation.md`](../03-dsa/23-bit-manipulation.md))

| Op | Java | Use |
|---|---|---|
| AND | `a & b` | Test/clear bits: `(x & 1) == 0` → even |
| OR | `a \| b` | Set bits (flags) |
| XOR | `a ^ b` | Toggle; `x ^ x = 0` (Single Number problem) |
| NOT | `~a` | `~a == -a - 1` |
| Left shift | `a << k` | Multiply by 2ᵏ |
| Arithmetic right shift | `a >> k` | Divide by 2ᵏ, keeps sign |
| Logical right shift | `a >>> k` | Fills with 0 — treats as unsigned |

`x & (x - 1)` clears the lowest set bit (count bits; power-of-two check `x > 0 && (x & (x-1)) == 0`).

### 4.5 Floating point — why LedgerX uses `BigDecimal`

`double` is IEEE-754 binary: 0.1 has no exact binary representation.

```java
System.out.println(0.1 + 0.2);                       // 0.30000000000000004
System.out.println(0.1 + 0.2 == 0.3);                // false
new BigDecimal("0.10").add(new BigDecimal("0.20"));  // 0.30 exactly
new BigDecimal(0.1);                                 // 0.1000000000000000055511151231257827… (don't!)
```

Money → `BigDecimal` (construct from `String`, explicit `RoundingMode`) or `long` cents. Compare
`BigDecimal` with `compareTo`, not `equals` (`2.0` vs `2.00` differ in scale). In SQL: `NUMERIC(12,2)`.

### 4.6 Characters (brief)

Java `char` is a 16-bit UTF-16 code unit; some characters (emoji) need two `char`s (a surrogate
pair) → `String.length()` counts code units, not user-visible characters. Files/HTTP use **UTF-8**
(1–4 bytes per code point). Always specify `StandardCharsets.UTF_8` when converting bytes ↔ strings.

---

## 5. Stack vs heap (how it really looks)

```java
public BigDecimal total(List<Transaction> txs) {   // frame for total()
    BigDecimal sum = BigDecimal.ZERO;              // 'sum' reference on stack → object on heap
    for (Transaction t : txs) {                    // 't' reference on stack
        sum = sum.add(t.amount());                 // new BigDecimal on heap each time
    }
    return sum;
}
```

```
 Thread's stack                        Heap (shared by all threads)
┌───────────────────────┐             ┌───────────────────────────────┐
│ total()               │             │ ArrayList ──► [ref, ref, ref]  │
│   this ───────────────┼────────────►│ ReportService object           │
│   txs  ───────────────┼────────────►│ ArrayList                       │
│   sum  ───────────────┼────────────►│ BigDecimal(…)                   │
│   t    ───────────────┼────────────►│ Transaction ─► BigDecimal amount│
├───────────────────────┤             │ (old BigDecimals → garbage)     │
│ main()                │             └───────────────────────────────┘
└───────────────────────┘
```

| | Stack | Heap |
|---|---|---|
| Holds | Frames: local primitives, **references**, return address | All objects and arrays |
| Owner | One per thread | Shared by all threads |
| Lifetime | Frame popped when method returns | Until unreachable, then GC |
| Size | Small (default ~512 KB–1 MB per platform thread, `-Xss`) | Large (`-Xmx`) |
| Allocation | Bump a pointer — trivial | Fast (TLAB bump) but GC costs later |
| Error | `StackOverflowError` (deep recursion) | `OutOfMemoryError: Java heap space` |
| Thread-safety | Locals are thread-confined | Shared objects need synchronization |

Interview traps:
- "Java is pass-by-value" — the **reference** is copied. Reassigning a parameter doesn't affect the caller; mutating the object does.
- Escape analysis may let the JIT avoid heap allocation for objects that never escape a method — an optimization, not something the language guarantees.
- Virtual threads' stacks live on the heap as chunks when unmounted — why millions are affordable.

---

## 6. How Java objects are laid out (roughly)

On 64-bit HotSpot with compressed class pointers/oops (default for heaps < 32 GB):

```
 Object header: mark word (8 B: hash, lock state, GC age) + class pointer (4 B) = 12 B
 then fields (reordered by size), then padding to a multiple of 8 B
```

| Thing | Approx. size | Why |
|---|---:|---|
| `new Object()` | 16 B | 12 B header + 4 padding |
| `Integer` | 16 B | 12 header + 4 int |
| `Long` | 24 B | 12 + 8 = 20 → pad to 24 |
| `int[0]` | 16 B | header 12 + length 4 |
| `int[1000]` | ~4 KB | 16 + 4000 |
| `Integer[1000]` all distinct | ~20 KB | 16 + 4×1000 refs + 1000×16 objects |
| `String "hello"` (Latin-1, compact strings) | ~48 B | String object (~24) + `byte[5]` (~24) |
| `HashMap` entry | ~32–48 B each | Node object (hash, key, value, next) + table slot |

Takeaways:
- **Boxing costs 4× memory** and cache locality; prefer `int[]`, `IntStream`, or primitive-specialized code in hot paths.
- A `HashMap<Long, Long>` with 10 M entries is on the order of **hundreds of MB**, not 160 MB.
- Measure with **JOL** (OpenJDK "Java Object Layout" tool) or a heap dump in VisualVM / IntelliJ profiler, don't guess.
- Awareness: newer JDKs add *compact object headers* (8-byte headers) — the idea above stays the same.

---

## 7. Break → Debug → Explain

- [ ] Write the row- vs column-major loop over a 4096×4096 `int[][]`; time both with `System.nanoTime()` (JIT warm-up: run each 5×). Explain the difference.
- [ ] Sum 1–100,000 squared into an `int` and a `long`; find where the `int` goes wrong.
- [ ] `List<Integer>` of 10 M vs `int[]` of 10 M: watch heap in VisualVM / `jcmd <pid> GC.heap_info`.
- [ ] Recurse without a base case; read the `StackOverflowError`; rerun with `-Xss4m` and see depth change.
- [ ] Explain to a rubber duck why LedgerX's money amounts are `BigDecimal` / `NUMERIC(19,4)` and never `double`.

---

## 8. Interview questions (quick)

<details><summary>Why is <code>ArrayList</code> usually faster than <code>LinkedList</code> even for inserts in the middle?</summary>

Finding the middle in a `LinkedList` is O(n) pointer chasing with cache misses; `ArrayList` does an
O(n) `System.arraycopy` over contiguous memory, which is extremely fast. Plus per-node overhead.
`LinkedList` only wins with an iterator already positioned and many inserts there.
</details>

<details><summary>What is two's complement and why is <code>Integer.MAX_VALUE + 1</code> negative?</summary>

Signed representation where the top bit weighs −2³¹. Adding 1 to 0111…1 gives 1000…0, which is
−2³¹. Java int arithmetic wraps silently; use `Math.addExact` or `long` to detect/avoid it.
</details>

<details><summary>Why not use double for money?</summary>

Binary floating point can't represent most decimal fractions exactly, so sums drift (0.1+0.2 ≠ 0.3).
Use `BigDecimal` with explicit scale/rounding or integer cents.
</details>

<details><summary>What's a cache line and why should a Java developer care?</summary>

The 64-byte unit moved between RAM and CPU caches. Contiguous data (arrays of primitives) uses
every byte fetched; scattered objects waste most of it. It explains array vs linked structure
performance and false sharing in concurrent code.
</details>

<details><summary>Roughly how much slower is RAM than L1? SSD than RAM? A cross-continent request?</summary>

~100×; ~1,000×; ~1,000,000× (100+ ms vs ~100 ns). Orders of magnitude are what matter.
</details>
