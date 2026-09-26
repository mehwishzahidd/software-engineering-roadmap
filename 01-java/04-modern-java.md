# 04 — Modern Java: Lambdas, Streams, Optional, Records, Enums (Week 3)

> **Outcome:** write reporting logic (kata: monthly spend per category; FlowGrid: stock by warehouse) as stream pipelines; know when a stream is
> the wrong tool; use `Optional`, records, enums, sealed types and pattern matching the way
> current Java code (and Spring Boot 3) expects.

Related: [README](./README.md) · prev [03-collections-generics.md](./03-collections-generics.md) · next [05-exceptions-io.md](./05-exceptions-io.md)

---

## 1. Lambdas and functional interfaces

A **functional interface** has exactly one abstract method. A lambda is a concise
implementation of it.

```java
@FunctionalInterface
interface Rule { boolean test(Transaction tx); }

Rule bigSpend = tx -> tx.amount().compareTo(new BigDecimal("500")) > 0;
```

The `java.util.function` toolkit:

| Interface | Signature | Example |
|---|---|---|
| `Function<T,R>` | `R apply(T)` | `Transaction::merchant` |
| `BiFunction<T,U,R>` | `R apply(T,U)` | `(a, b) -> a.add(b)` |
| `Predicate<T>` | `boolean test(T)` | `tx -> tx.amount().signum() < 0` |
| `Consumer<T>` | `void accept(T)` | `System.out::println` |
| `Supplier<T>` | `T get()` | `ArrayList::new` |
| `UnaryOperator<T>` / `BinaryOperator<T>` | `T apply(T)` / `T apply(T,T)` | `BigDecimal::add` |
| Primitive specializations | `IntPredicate`, `ToIntFunction<T>`, `IntBinaryOperator`… | avoid boxing |

Composition: `p1.and(p2).negate()`, `f.andThen(g)`, `Comparator.comparing(...).thenComparing(...)`.

### Method references

| Kind | Syntax | Lambda equivalent |
|---|---|---|
| Static | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| Bound instance | `System.out::println` | `x -> System.out.println(x)` |
| Unbound instance | `String::toLowerCase` | `s -> s.toLowerCase()` |
| Constructor | `ArrayList::new` | `() -> new ArrayList<>()` |

### Capture rules

Lambdas can read local variables only if they're **effectively final**:

```java
int total = 0;
txs.forEach(tx -> total += 1);   // ❌ compile error
```
Why: the lambda may run later or on another thread; Java captures the *value*, so mutation
would be ambiguous. Use a stream reduction (`count()`, `sum()`) instead of mutating outer state.

**Under the hood:** lambdas compile to an `invokedynamic` call site bootstrapped by
`LambdaMetafactory`, which spins a hidden class at first use. Non-capturing lambdas are
typically a single cached instance. They are *not* anonymous inner classes (`this` inside a
lambda means the enclosing instance).

---

## 2. Streams

A stream is a **lazy pipeline** over a source: `source → intermediate ops → one terminal op`.

```java
List<Transaction> txs = repo.findAll();

// Total spend per category for a month, sorted desc (Week 2–3 ledger kata)
YearMonth month = YearMonth.of(2025, 3);
Map<String, BigDecimal> byCategory = txs.stream()
    .filter(tx -> YearMonth.from(tx.date()).equals(month))
    .filter(tx -> tx.amount().signum() < 0)                               // expenses
    .collect(Collectors.groupingBy(
        Transaction::category,
        TreeMap::new,                                                     // sorted keys
        Collectors.reducing(BigDecimal.ZERO, tx -> tx.amount().negate(), BigDecimal::add)));

List<Map.Entry<String, BigDecimal>> top3 = byCategory.entrySet().stream()
    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
    .limit(3)
    .toList();                                                            // Java 16+, unmodifiable
```

### The ops you'll use 95% of the time

| Intermediate (lazy) | Terminal (triggers execution) |
|---|---|
| `filter`, `map`, `flatMap`, `mapToInt/Long/Obj`, `distinct`, `sorted`, `limit`, `skip`, `peek`, `takeWhile`, `dropWhile` | `collect`, `toList`, `forEach`, `reduce`, `count`, `sum`/`average` (primitive streams), `min`/`max`, `anyMatch`/`allMatch`/`noneMatch`, `findFirst`/`findAny` |

### Collectors cookbook

```java
Collectors.toMap(Account::id, a -> a)                            // throws on duplicate keys!
Collectors.toMap(Transaction::merchant, t -> 1, Integer::sum)    // merge fn handles duplicates
Collectors.groupingBy(Transaction::category)                     // Map<String, List<Transaction>>
Collectors.groupingBy(Transaction::category, Collectors.counting())
Collectors.groupingBy(tx -> YearMonth.from(tx.date()), TreeMap::new, Collectors.toList())
Collectors.partitioningBy(tx -> tx.amount().signum() >= 0)      // Map<Boolean, List<...>>
Collectors.joining(", ", "[", "]")
Collectors.summingLong(Transaction::cents)
Collectors.mapping(Transaction::merchant, Collectors.toSet())
Collectors.teeing(Collectors.counting(), Collectors.summingLong(Transaction::cents),
                  (n, sum) -> n == 0 ? 0 : sum / n)              // Java 12+: average in one pass
```

### Primitive streams

```java
int total = IntStream.rangeClosed(1, 100).sum();                 // 5050
double avg = nums.stream().mapToInt(Integer::intValue).average().orElse(0);
int[] arr = list.stream().mapToInt(Integer::intValue).toArray();
List<Integer> boxed = Arrays.stream(arr).boxed().toList();
```

### Under the hood

- **Laziness & fusion:** nothing runs until the terminal op; then each element flows through the whole pipeline one at a time (`filter→map→...`), not stage by stage. `limit`/`findFirst`/`anyMatch` **short-circuit**.
- A stream can be consumed **once** (`IllegalStateException: stream has already been operated upon or closed`).
- `sorted()` and `distinct()` are *stateful* — they buffer.
- `parallelStream()` uses the common `ForkJoinPool`. Only helps for large, CPU-bound, splittable sources with no shared mutable state. Never in a web request handler "to go faster" without measuring.

### When not to use streams

- DSA inner loops with indices, early `break`s, or multiple mutable pointers → plain loops are clearer and faster.
- Checked exceptions inside lambdas → awkward (see [05-exceptions-io.md](./05-exceptions-io.md)).
- Side effects: `stream().forEach(x -> list.add(x))` is a smell; collect instead.

---

## 3. `Optional`

`Optional<T>` = "a value that may be absent", designed as a **return type**.

```java
public interface TransactionRepository {
    Optional<Transaction> findById(long id);
}

String merchant = repo.findById(id)
    .map(Transaction::merchant)
    .filter(m -> !m.isBlank())
    .orElse("unknown");

Transaction tx = repo.findById(id)
    .orElseThrow(() -> new TransactionNotFoundException(id));    // Spring: mapped to 404 ProblemDetail

repo.findById(id).ifPresentOrElse(this::print, () -> System.out.println("none"));
```

Rules (*Effective Java* Item 55):
- ✅ Return type for "might not exist" lookups.
- ❌ Fields, method parameters, collection elements, `Optional<List<X>>` (return an empty list).
- ❌ `opt.get()` without checking — it's just a fancier NPE. Prefer `orElseThrow()`.
- `orElse(expensive())` **always evaluates** `expensive()`; use `orElseGet(() -> expensive())`.
- Never return `null` from a method declared to return `Optional`.

---

## 4. Records (Java 16)

```java
public record MonthlySummary(YearMonth month, BigDecimal income, BigDecimal expenses) {
    public MonthlySummary {                               // compact canonical constructor
        Objects.requireNonNull(month);
        if (income.signum() < 0) throw new IllegalArgumentException("income < 0");
    }
    public BigDecimal net() { return income.subtract(expenses); }   // derived method
    public static MonthlySummary empty(YearMonth m) { return new MonthlySummary(m, BigDecimal.ZERO, BigDecimal.ZERO); }
}
```
- Final, fields `private final`, accessors `month()`, value-based `equals/hashCode/toString`.
- Shallowly immutable: `record R(List<String> xs)` — defensively copy in the compact constructor: `xs = List.copyOf(xs);`.
- Great for: DTOs in Spring (Jackson supports records), value objects, composite map keys, local "tuples" inside a method.

---

## 5. Enums

```java
public enum Frequency {
    WEEKLY(7), BIWEEKLY(14), MONTHLY(30);

    private final int approxDays;
    Frequency(int approxDays) { this.approxDays = approxDays; }
    public int approxDays() { return approxDays; }

    public static Frequency detect(long avgGapDays) {
        for (Frequency f : values()) if (Math.abs(avgGapDays - f.approxDays) <= 2) return f;
        throw new IllegalArgumentException("not recurring: " + avgGapDays);
    }
}

Frequency.valueOf("MONTHLY");   // IllegalArgumentException if not found
Frequency.MONTHLY.ordinal();    // 2 — never persist ordinals (reordering corrupts data)
EnumMap<Frequency, Integer> counts = new EnumMap<>(Frequency.class);  // array-backed, fast
EnumSet<Frequency> frequent = EnumSet.of(Frequency.WEEKLY, Frequency.BIWEEKLY);
```
Enums are full classes: fields, constructors, methods, even per-constant bodies. Each constant
is a singleton created at class initialization. Compare with `==`. In JPA use
`@Enumerated(EnumType.STRING)`, never the default `ORDINAL`.

---

## 6. Pattern matching (Java 16–21)

```java
// instanceof pattern (16)
if (obj instanceof Transaction tx && tx.amount().signum() < 0) { ... }

// Record patterns + switch patterns (21)
sealed interface Shape permits Circle, Square {}
record Circle(double r) implements Shape {}
record Square(double side) implements Shape {}

static double area(Shape s) {
    return switch (s) {
        case Circle(double r) -> Math.PI * r * r;
        case Square(double side) when side < 0 -> throw new IllegalArgumentException();
        case Square(double side) -> side * side;
    };
}
```
Sealed + records + pattern `switch` = algebraic data types. The compiler checks exhaustiveness,
so adding a new `Shape` breaks compilation at every unhandled `switch` — a feature.

---

## 7. Immutability in practice

```java
public final class Rulebook {
    private final List<CategorizationRule> rules;
    public Rulebook(List<CategorizationRule> rules) { this.rules = List.copyOf(rules); } // defensive copy in
    public List<CategorizationRule> rules() { return rules; }                              // already unmodifiable
}
```
- `List.copyOf` → truly immutable snapshot. `Collections.unmodifiableList(x)` → read-only *view*; changes to `x` still show through.
- `LocalDate`, `Instant`, `BigDecimal`, `String`, `Integer` are immutable — methods return new instances (`date.plusDays(1)` does nothing if you ignore the result).
- Immutable objects are automatically thread-safe and safe as map keys.

### `java.time` essentials (you'll use them in every project)

FlowGrid idempotency-key TTLs, LedgerX scheduled payments, ForgeCI leases and FlagForge version timestamps all use these. Store `Instant` (UTC) in the database; convert to a zone only for display.

```java
LocalDate d = LocalDate.parse("2025-03-14");                 // ISO
LocalDate d2 = LocalDate.parse("14/03/2025", DateTimeFormatter.ofPattern("dd/MM/yyyy"));
YearMonth ym = YearMonth.from(d);
long days = ChronoUnit.DAYS.between(d, d2);
Instant now = Instant.now();                                  // UTC timestamp; store this in DBs
Duration timeout = Duration.ofSeconds(5);                     // ForgeCI job timeouts, FlagForge SDK timeouts
```

### `BigDecimal` essentials (money)

```java
new BigDecimal("19.99");                 // ✅ from String
new BigDecimal(19.99);                   // ❌ 19.989999999999998436805981327779591083526611328125
BigDecimal.valueOf(19.99);               // OK: uses Double.toString → "19.99"
a.add(b); a.subtract(b); a.multiply(b);  // immutable, return new
a.divide(b, 2, RoundingMode.HALF_EVEN);  // must give scale+rounding or risk ArithmeticException
a.compareTo(b) == 0                      // numeric equality (equals also compares scale)
```

---

## 🔨 Break it

1. Call a terminal op twice on the same `Stream` variable.
2. `Collectors.toMap(Transaction::merchant, t -> t)` with duplicate merchants → `IllegalStateException: Duplicate key`.
3. Add `.peek(System.out::println)` before `.filter(...).findFirst()` — observe that not every element is visited (laziness + short-circuit).
4. `Optional.of(null)` vs `Optional.ofNullable(null)`.
5. `repo.findById(1).orElse(loadFromNetwork())` with a print inside `loadFromNetwork` — it prints even when present.
6. Store an enum by `ordinal()`, reorder constants, reload → wrong values. Explain why JPA `EnumType.STRING` exists.
7. `new BigDecimal("1").divide(new BigDecimal("3"))` → `ArithmeticException: Non-terminating decimal expansion`.
8. Parallel-sum with a shared `ArrayList` in `forEach` → lost elements / `ArrayIndexOutOfBoundsException`.

## ⚠️ Common mistakes

- Using `forEach` to build collections instead of `collect`/`toList`.
- `Stream.toList()` returns an **unmodifiable** list; `collect(Collectors.toList())` currently returns an `ArrayList` but that's not guaranteed.
- `Optional` as a field or parameter.
- Long, clever stream chains nobody can debug — extract named methods or use a loop.
- Forgetting that `LocalDate.plusDays` returns a new object.
- `new BigDecimal(double)`.

## 🎤 Interview questions

<details><summary>1. What is a functional interface? Name five from java.util.function.</summary>

An interface with exactly one abstract method (default/static methods allowed), target type for
lambdas. `Function`, `Predicate`, `Consumer`, `Supplier`, `BiFunction`, `UnaryOperator`, `BinaryOperator`.
</details>

<details><summary>2. Intermediate vs terminal operations? What does "lazy" mean?</summary>

Intermediate ops (`filter`, `map`) return a new stream and do nothing until a terminal op
(`collect`, `count`, `findFirst`) runs. Then elements are pulled through the fused pipeline
one by one, and short-circuiting ops can stop early.
</details>

<details><summary>3. map vs flatMap?</summary>

`map` transforms each element 1→1. `flatMap` transforms each element into a stream and
flattens: `List<List<T>>` → `Stream<T>`. Same idea for `Optional.flatMap` to avoid `Optional<Optional<T>>`.
</details>

<details><summary>4. When is a parallel stream a bad idea?</summary>

Small data, I/O-bound work, shared mutable state, ordering-dependent operations, `LinkedList`
or iterator-based sources that split poorly, and inside servers where the common ForkJoinPool
is shared by everything. Measure first.
</details>

<details><summary>5. How should Optional be used?</summary>

As a return type for "may be absent". Chain `map`/`filter`/`orElse`/`orElseThrow`. Not for
fields, parameters or collections. Avoid `get()`. Use `orElseGet` for expensive defaults.
</details>

<details><summary>6. Records vs Lombok @Data / regular classes?</summary>

Records are a language feature: immutable, final, value-based equals/hashCode, no setters, no
inheritance. Lombok `@Data` generates mutable JavaBeans with setters. Use records for DTOs/value
objects; regular classes for entities with identity and lifecycle (JPA).
</details>

<details><summary>7. Why can lambdas only capture effectively final variables?</summary>

The lambda captures a copy of the value and may execute later or on another thread. Allowing
mutation would create confusing semantics and data races; Java forbids it at compile time.
</details>

<details><summary>8. What are sealed classes good for?</summary>

Closed hierarchies where you know all subtypes (result types, commands, events, AST nodes).
With pattern-matching `switch` the compiler enforces exhaustive handling and you don't need a `default`.
</details>

## ✅ Mastery checklist

- [ ] Write lambdas for `Function`, `Predicate`, `Supplier`, `Consumer`, `BinaryOperator` without looking
- [ ] Build the kata's monthly report with `groupingBy` + downstream collector
- [ ] Explain laziness and short-circuiting; demonstrate with `peek`
- [ ] Handle duplicate keys in `toMap`
- [ ] Use `Optional` idiomatically; explain `orElse` vs `orElseGet`
- [ ] Write a record with a compact constructor and defensive copy
- [ ] Write an enum with fields and use `EnumMap`
- [ ] Use a sealed interface with a record-pattern `switch`
- [ ] Use `BigDecimal` correctly for money
- [ ] Complete Week 3 exercises in [exercises.md](./exercises.md#week-3--modern-java-io-debugging)
