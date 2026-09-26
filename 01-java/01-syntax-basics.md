# 01 — Java Syntax & Basics (Week 1)

> **Outcome:** write, compile and run Java from a blank file in a terminal; know exactly how
> primitives, references, arrays and strings behave; never be surprised by integer overflow,
> `==` on strings, or integer division.

Related: [README](./README.md) · next: [02-oop.md](./02-oop.md) · exercises W1 in [exercises.md](./exercises.md#week-1--syntax-basics)

---

## 1. From source to running program

```bash
mkdir hello && cd hello
cat > Hello.java <<'EOF'
public class Hello {
    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "world";
        System.out.println("Hello, " + name + "!");
    }
}
EOF
javac Hello.java        # produces Hello.class (bytecode)
java Hello Ada          # JVM loads Hello.class, runs main -> Hello, Ada!
java Hello.java Ada     # Java 11+: single-file source launch (compiles in memory)
```

| Tool | What it does |
|---|---|
| `javac` | Compiles `.java` source → `.class` **bytecode** (platform-independent). |
| `java` | Starts a **JVM**, loads classes, interprets bytecode, JIT-compiles hot code to native. |
| JDK | JRE + compiler + tools (`jshell`, `jcmd`, `jar`, `javadoc`). You install the JDK. |
| `jshell` | REPL. Perfect for "Break it" experiments: `jshell` → `int x = Integer.MAX_VALUE + 1;` |

**Under the hood:** "write once, run anywhere" means the *bytecode* is portable; the JVM is
platform-specific. The file name must match the `public` class name because the compiler
maps public top-level classes to files 1:1.

**`main` signature:** `public static void main(String[] args)`. `static` because no object
exists yet when the JVM starts. (Java 21 has a *preview* of instance `main` methods — don't
rely on it in interviews.)

---

## 2. Types: primitives vs references

| Primitive | Size | Range / notes | Default (fields) |
|---|---|---|---|
| `byte` | 8 bit | −128 … 127 | 0 |
| `short` | 16 bit | −32 768 … 32 767 | 0 |
| `int` | 32 bit | ≈ ±2.1 billion (`Integer.MAX_VALUE` = 2 147 483 647) | 0 |
| `long` | 64 bit | ≈ ±9.2 × 10¹⁸ — suffix `L` | 0L |
| `float` | 32 bit | ~7 decimal digits — suffix `f` | 0.0f |
| `double` | 64 bit | ~15–16 decimal digits | 0.0 |
| `char` | 16 bit | unsigned UTF-16 code unit, 0 … 65 535 | `'\u0000'` |
| `boolean` | JVM-dependent | `true`/`false` only (no 0/1 conversion) | false |

Everything else (`String`, arrays, `Integer`, your classes) is a **reference type**: the
variable holds a reference (pointer-like handle) to an object on the heap, or `null`.

```java
int a = 5;
int b = a;      // copies the value 5
b++;            // a is still 5

int[] x = {1, 2, 3};
int[] y = x;    // copies the REFERENCE; x and y point to the same array
y[0] = 99;      // x[0] is now 99 too
```

**Local variables have no default** — the compiler rejects reading an unassigned local
("definite assignment"). Fields and array elements *do* get defaults.

### `var` (local type inference, Java 10+)

```java
var list = new ArrayList<String>();   // type is ArrayList<String>, fixed at compile time
var n = 10;                           // int
// var z;            // compile error: cannot infer
// var q = null;     // compile error
```
`var` is **not dynamic typing**; it's the compiler filling in the type. Use it when the
right-hand side makes the type obvious.

---

## 3. Operators and the traps inside them

```java
System.out.println(7 / 2);        // 3      integer division truncates toward zero
System.out.println(-7 / 2);       // -3
System.out.println(-7 % 3);       // -1     sign follows the dividend
System.out.println(Math.floorMod(-7, 3)); // 2 — use for "wrap-around" indices
System.out.println(7 / 2.0);      // 3.5    one double operand → double arithmetic
System.out.println(0.1 + 0.2);    // 0.30000000000000004  (binary floating point)
System.out.println(Integer.MAX_VALUE + 1);  // -2147483648 (silent overflow, wraps)
System.out.println(Math.addExact(Integer.MAX_VALUE, 1)); // throws ArithmeticException
```

- **Overflow is silent** for `int`/`long`. In DSA, `mid = lo + (hi - lo) / 2` instead of
  `(lo + hi) / 2`; sum into a `long` when values can exceed ~2·10⁹.
- **Money is never `double`.** P1 Ledger uses `BigDecimal` (see [04-modern-java.md](./04-modern-java.md) and exercises).
- **Short-circuit**: `&&` and `||` skip the right side; `&` and `|` on booleans evaluate both.
  `if (s != null && s.isEmpty())` is safe; with `&` it NPEs.
- **Compound assignment casts silently**: `byte b = 10; b += 300;` compiles (implicit cast), `b = b + 300;` does not.
- **`++i` vs `i++`**: value of the expression differs; as a standalone statement they're identical.
- **Bitwise:** `&`, `|`, `^`, `~`, `<<`, `>>` (arithmetic, keeps sign), `>>>` (logical, fills 0). Used in [../03-dsa/23-bit-manipulation.md](../03-dsa/23-bit-manipulation.md).

### Casting and widening

```java
long big = 3_000_000_000L;
int truncated = (int) big;        // -1294967296: keeps low 32 bits
double d = 3.99;
int i = (int) d;                  // 3 — truncates, does not round
char c = 'a';
int code = c;                     // 97 — widening, implicit
char next = (char) (c + 1);       // 'b' — c + 1 is int, must cast back
```

In DSA: `s.charAt(i) - 'a'` gives an index 0–25 into an `int[26]` frequency array.

---

## 4. Control flow

```java
// Classic for, enhanced for, while, do-while
for (int i = 0; i < n; i++) { ... }
for (String s : names) { ... }        // works on arrays and any Iterable
while (lo <= hi) { ... }
do { line = reader.readLine(); } while (line != null && line.isBlank());

// Labeled break — exit nested loops (useful in grid search)
outer:
for (int r = 0; r < grid.length; r++) {
    for (int c = 0; c < grid[r].length; c++) {
        if (grid[r][c] == target) { found = true; break outer; }
    }
}
```

### `switch` expressions (Java 14+) — prefer these

```java
enum Day { MON, TUE, WED, THU, FRI, SAT, SUN }

static int studyHours(Day d) {
    return switch (d) {
        case MON, TUE, WED, THU -> 3;
        case FRI, SUN -> 2;
        case SAT -> 5;
    };  // exhaustive over the enum: no default needed; compiler errors if you add a constant and forget it
}

String label = switch (code) {
    case 200 -> "OK";
    case 404 -> "Not Found";
    default -> {
        String s = "HTTP " + code;
        yield s;                     // yield returns a value from a block
    }
};
```
Arrow-form cases **don't fall through**. Old colon-form `case X:` falls through without `break` — a classic bug.

---

## 5. Methods

```java
static int clamp(int value, int min, int max) {
    if (min > max) throw new IllegalArgumentException("min > max");
    return Math.max(min, Math.min(max, value));
}

// Overloading: same name, different parameter lists (resolved at COMPILE time)
static double area(double r)            { return Math.PI * r * r; }
static double area(double w, double h)  { return w * h; }

// Varargs: sugar for an array parameter; must be last
static int sum(int... xs) { int t = 0; for (int x : xs) t += x; return t; }
sum(); sum(1, 2, 3); sum(new int[]{4, 5});
```

**Java is always pass-by-value.** For references, the *reference value* is copied:

```java
static void reassign(int[] arr) { arr = new int[]{0}; }   // caller unaffected
static void mutate(int[] arr)   { arr[0] = 42; }           // caller sees 42
```
Deep dive in [06-memory-jvm.md](./06-memory-jvm.md#2-pass-by-value-always).

---

## 6. Arrays

```java
int[] a = new int[5];                 // {0,0,0,0,0}
int[] b = {3, 1, 2};
String[] names = new String[3];       // {null, null, null}
int[][] grid = new int[3][4];         // 3 rows × 4 cols, all 0
int[][] jagged = new int[3][];        // rows are null until assigned

Arrays.sort(b);                             // dual-pivot quicksort for primitives
System.out.println(Arrays.toString(b));     // [1, 2, 3]   (b.toString() prints [I@1b6d3586)
int[] copy = Arrays.copyOf(b, 5);           // [1, 2, 3, 0, 0]
int[] part = Arrays.copyOfRange(b, 1, 3);   // [2, 3]   (end exclusive)
Arrays.fill(a, -1);
boolean same = Arrays.equals(b, copy);      // content comparison; b.equals(copy) is identity
System.out.println(Arrays.deepToString(grid));
```

**Under the hood:** an array is an object with a fixed `length` field (not a method) and
contiguous element storage. Index access is bounds-checked → `ArrayIndexOutOfBoundsException`.
`int[]` stores values inline; `Integer[]` stores references to separate `Integer` objects
(more memory, worse cache locality, `null` possible).

---

## 7. Strings

```java
String s = "Ledger";
s.length();               // 6 (method; arrays use .length field)
s.charAt(0);              // 'L'
s.substring(1, 4);        // "edg"  [begin, end)
s.indexOf("dg");          // 2, or -1 if absent
s.toLowerCase();          // returns NEW string; s unchanged
s.contains("edge");       // false (case-sensitive)
" a b ".strip();          // "a b"  (Unicode-aware; trim() is ASCII-only)
"a,b,,c".split(",");      // ["a", "b", "", "c"]  — trailing empties dropped!
"a,b,,".split(",", -1);   // ["a", "b", "", ""]   — limit -1 keeps them (CSV parsing!)
String.join("-", List.of("x", "y"));  // "x-y"
"ab".repeat(3);           // "ababab"
s.isBlank();              // true for "", "  ", "\n"
s.chars();                // IntStream of chars
char[] cs = s.toCharArray(); Arrays.sort(cs); String sorted = new String(cs); // anagram key
```

### Comparing strings

```java
String a = "hello";
String b = "hello";
String c = new String("hello");
System.out.println(a == b);        // true  — both literals → same pooled object
System.out.println(a == c);        // false — c is a new heap object
System.out.println(a.equals(c));   // true  — content comparison: ALWAYS use this
System.out.println(a.compareTo("help")); // negative: lexicographic
```

### Immutability and `StringBuilder`

`String` is **immutable**: every "modification" returns a new object. So this is O(n²):

```java
String out = "";
for (int i = 0; i < n; i++) out += i;   // copies the whole string each iteration
```
Use `StringBuilder` (mutable, amortized O(1) append):

```java
StringBuilder sb = new StringBuilder();
for (int i = 0; i < n; i++) sb.append(i).append(',');
if (!sb.isEmpty()) sb.setLength(sb.length() - 1);   // drop trailing comma
sb.reverse(); sb.insert(0, '['); sb.deleteCharAt(0);
String result = sb.toString();
```
(`StringBuffer` is the old synchronized version — you'll almost never need it.)

**Under the hood:** since Java 9, `String` stores a `byte[]` plus a coder flag (Latin-1 or
UTF-16 — "compact strings"). A single `a + b + c` expression is compiled to one
`invokedynamic` concatenation, so it's fine; the problem is concatenation **inside loops**.
Why immutable: safe to share across threads, safe as `HashMap` keys (hash cached), enables
the string pool, and security (a validated path/URL can't be altered afterwards).

### Text blocks (Java 15+)

```java
String sql = """
    SELECT id, amount
    FROM transactions
    WHERE account_id = ?
    """;
```

---

## 8. Input for DSA / OA platforms

LeetCode gives you a method. HackerRank/CodeSignal OAs often need stdin:

```java
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int n = Integer.parseInt(br.readLine().trim());
        StringTokenizer st = new StringTokenizer(br.readLine());
        long sum = 0;
        for (int i = 0; i < n; i++) sum += Long.parseLong(st.nextToken());
        System.out.println(sum);
    }
}
```
`Scanner` is simpler but much slower on large input. See [../OA_PREP.md](../OA_PREP.md).

---

## 🔨 Break it

Predict first, then run in `jshell`.

| # | Experiment | What you should learn |
|---|---|---|
| 1 | `int x = Integer.MAX_VALUE; x++; System.out.println(x);` | Silent wraparound to `MIN_VALUE`. |
| 2 | `Math.abs(Integer.MIN_VALUE)` | Returns a **negative** number (no positive counterpart). |
| 3 | `System.out.println(1 + 2 + "3" + 4 + 5);` | `"3345"` — left-to-right; once a String appears, `+` concatenates. |
| 4 | `new String("a") == "a"` vs `new String("a").intern() == "a"` | `false` / `true` — the pool. |
| 5 | `"a,b,,".split(",").length` | `2` — trailing empty strings removed. |
| 6 | `char c = 'a'; c + 1` in jshell, then `(char)(c + 1)` | `98` vs `'b'` — char arithmetic promotes to int. |
| 7 | Time `+=` in a loop of 100 000 vs `StringBuilder` with `System.nanoTime()` | Quadratic vs linear. |
| 8 | `int[] a = new int[3]; a[3] = 1;` | `ArrayIndexOutOfBoundsException: Index 3 out of bounds for length 3`. |
| 9 | `double d = 0.1 * 3; d == 0.3` | `false`. Compare with tolerance or use `BigDecimal`. |
| 10 | Old-style switch: `case 1: x = "a"; case 2: x = "b";` with input 1 | Falls through to `"b"`. |

---

## ⚠️ Common mistakes

- Comparing strings with `==`.
- Integer division where you meant a ratio: `count / total * 100` is 0 when `count < total`. Use `100.0 * count / total`.
- Off-by-one in `substring(begin, end)` (end is exclusive).
- `for (int i = 0; i <= arr.length; i++)` → out of bounds.
- `String.split` with regex metacharacters: `"a.b".split(".")` returns an empty array; use `split("\\.")`.
- Printing arrays with `System.out.println(arr)` instead of `Arrays.toString(arr)`.
- Forgetting `L` on large literals: `long ms = 24 * 60 * 60 * 1000 * 365;` overflows in `int` **before** assignment.

---

## 🎤 Interview questions

<details><summary>1. What's the difference between JDK, JRE and JVM?</summary>

JVM executes bytecode (interpreter + JIT + GC). JRE = JVM + standard class library (a runtime).
JDK = JRE + development tools (`javac`, `jshell`, `jcmd`, `jar`). Since Java 11 Oracle no longer
ships a separate JRE; you install a JDK and can build a trimmed runtime with `jlink`.
</details>

<details><summary>2. Why is <code>String</code> immutable?</summary>

Security (validated values can't change), thread safety without locking, hash code can be
cached (fast `HashMap` keys), and it enables the string constant pool (sharing literals is only
safe if nobody can mutate them). Cost: modifications create new objects → use `StringBuilder` in loops.
</details>

<details><summary>3. <code>==</code> vs <code>equals()</code> for strings?</summary>

`==` compares references (same object?). `equals` compares character content. Two literals
with the same content are the same pooled object, so `==` *happens* to work — which is exactly
why the bug hides until a string comes from user input, `substring`, or `new String`.
</details>

<details><summary>4. What happens on <code>int</code> overflow in Java?</summary>

It wraps around silently (two's complement). No exception. Use `long`, `Math.addExact` /
`multiplyExact` (throw on overflow) or `BigInteger`. Classic DSA fix: `lo + (hi - lo) / 2`.
</details>

<details><summary>5. <code>StringBuilder</code> vs <code>StringBuffer</code>?</summary>

Same API; `StringBuffer` methods are `synchronized` (thread-safe, slower), `StringBuilder`
isn't. Builders are almost always local to one method/thread → `StringBuilder`.
</details>

<details><summary>6. Is Java pass-by-reference?</summary>

No. Always pass-by-value. For object parameters the value copied is the reference, so the
method can mutate the object but cannot make the caller's variable point elsewhere.
</details>

<details><summary>7. Why shouldn't you use <code>double</code> for money?</summary>

Binary floating point can't represent most decimal fractions exactly (`0.1 + 0.2 != 0.3`);
errors accumulate across sums. Use `BigDecimal` (created from a `String`, not a `double`) with
explicit scale and `RoundingMode`, or store integer minor units (cents) in a `long`.
</details>

---

## ✅ Mastery checklist

- [ ] Compile and run a class from the terminal with `javac`/`java`, passing args
- [ ] State size/range of `int` and `long`; explain and demonstrate overflow
- [ ] Explain primitive vs reference assignment with an array example
- [ ] Write a `switch` expression over an enum with no `default`
- [ ] Use `StringBuilder` and explain why `+=` in a loop is O(n²)
- [ ] Explain `==` vs `equals` and the string pool
- [ ] Read stdin fast with `BufferedReader` + `StringTokenizer`
- [ ] Complete Week 1 exercises in [exercises.md](./exercises.md#week-1--syntax-basics)
