# 02 — Object-Oriented Programming (Week 2 · design applied in FlowGrid, Weeks 4–6)

> **Outcome (Week 2):** model a domain with classes, interfaces and records; implement
> `equals`/`hashCode`/`toString` correctly; explain polymorphism as *dynamic dispatch*.
> **Outcome (Weeks 4–6):** apply SOLID and five patterns (Strategy, Factory, Builder, Observer,
> Singleton) where FlowGrid genuinely needs them — and explain *why* each one earns its place.
> Examples below use the throwaway Week 2 **inventory/ledger kata** (transactions, money,
> categorization rules) so the patterns are visible without project noise.

Related: [README](./README.md) · prev [01-syntax-basics.md](./01-syntax-basics.md) · next [03-collections-generics.md](./03-collections-generics.md) · first project [../18-projects/flowgrid/README.md](../18-projects/flowgrid/README.md)

---

## Part A — Mechanics (Week 2)

### 1. Classes, objects, constructors

```java
public class Account {
    private final String id;          // final: assigned exactly once
    private final String owner;
    private long balanceCents;        // mutable state, hidden

    public Account(String id, String owner) {
        this(id, owner, 0);           // constructor chaining; must be first statement
    }

    public Account(String id, String owner, long openingCents) {
        if (openingCents < 0) throw new IllegalArgumentException("negative opening balance");
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.balanceCents = openingCents;
    }

    public void deposit(long cents) {
        if (cents <= 0) throw new IllegalArgumentException("deposit must be positive");
        balanceCents += cents;
    }

    public long balanceCents() { return balanceCents; }
    public String id() { return id; }
}
```

- If you write **no** constructor, the compiler adds a no-arg one. Write any constructor and it disappears.
- **Encapsulation** = invariants live inside the class. Nobody outside can set a negative balance because there's no setter; `deposit` enforces the rule. Getters/setters for every field is *not* encapsulation.
- `final` field ≠ immutable object: `final List<X> items` can still be mutated; only the reference is fixed.

### 2. `static`

```java
public class IdGenerator {
    private static int counter = 0;               // one per class, shared by all instances
    public static final String PREFIX = "TX-";    // constant

    public static String next() { return PREFIX + (++counter); } // no `this`
}
```
- `static` members belong to the **class**, loaded once per classloader.
- A static method can't access instance fields (there's no `this`).
- Static methods are **not** overridden — they're *hidden*; call resolution is compile-time.
- Static nested class vs inner class: an inner (non-static) class holds a hidden reference
  to its outer instance — a memory-leak source. Prefer `static` nested classes unless you need the outer instance.

### 3. Inheritance and polymorphism

```java
public abstract class Shape {
    public abstract double area();                  // subclasses MUST implement
    public String describe() { return getClass().getSimpleName() + " area=" + area(); } // template
}

public final class Circle extends Shape {
    private final double r;
    public Circle(double r) { this.r = r; }
    @Override public double area() { return Math.PI * r * r; }
}

public final class Rect extends Shape {
    private final double w, h;
    public Rect(double w, double h) { this.w = w; this.h = h; }
    @Override public double area() { return w * h; }
}

List<Shape> shapes = List.of(new Circle(1), new Rect(2, 3));
for (Shape s : shapes) System.out.println(s.describe());  // each calls ITS area()
```

**Under the hood — dynamic dispatch:** the compiler checks that `Shape` has `area()`; at
runtime the JVM uses the object's **actual class** to pick the implementation (via a per-class
method table, the *vtable*; `invokevirtual`/`invokeinterface` bytecodes). The JIT often
inlines monomorphic call sites, so polymorphism is usually free.

**Overriding rules:** same signature; return type may be a subtype (covariant); can't reduce
visibility; can't throw broader checked exceptions. Always use `@Override` — it turns a typo
(`equals(Account o)`) into a compile error instead of a silent overload.

**Overloading vs overriding:** overloading = compile-time choice by *static* argument types;
overriding = runtime choice by the *dynamic* receiver type.

**`super`:** `super.method()` calls the parent's version; `super(args)` calls the parent
constructor (implicitly `super()` if omitted — if the parent has no no-arg constructor, compile error).

**Constructors and overridable methods:** never call an overridable method from a constructor —
the subclass override runs before the subclass's fields are initialized.

### 4. Interfaces vs abstract classes

```java
public interface CategorizationRule {
    Optional<String> categorize(Transaction tx);          // abstract
    default int priority() { return 100; }                // default method (Java 8)
    static CategorizationRule merchantContains(String needle, String category) { // static factory
        return tx -> tx.merchant().toLowerCase().contains(needle.toLowerCase())
                ? Optional.of(category) : Optional.empty();
    }
}
```

| | Interface | Abstract class |
|---|---|---|
| State | Only `static final` constants | Instance fields |
| Constructors | No | Yes |
| Multiple | A class implements many | A class extends one |
| Methods | abstract, `default`, `static`, `private` | anything |
| Use for | A **capability/contract** (`Comparable`, `Repository`) | Sharing **code + state** among closely related classes |

Default: **interface first**. Add an abstract base class only when implementations genuinely share state.

**Diamond with defaults:** if two interfaces provide the same default method, the class must override it and may pick one with `A.super.method()`.

### 5. `Object` methods: `equals`, `hashCode`, `toString`

The contract (from the `Object` Javadoc):

- `equals` is **reflexive, symmetric, transitive, consistent**, and `x.equals(null)` is `false`.
- **If `a.equals(b)` then `a.hashCode() == b.hashCode()`.** (The reverse need not hold — collisions are legal.)
- `hashCode` must be consistent while the fields it uses don't change.

```java
public final class Money {
    private final BigDecimal amount;
    private final Currency currency;

    public Money(BigDecimal amount, Currency currency) {
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN); // normalize: 1.5 and 1.50 equal
        this.currency = Objects.requireNonNull(currency);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;   // pattern matching; also handles null
        return amount.equals(other.amount) && currency.equals(other.currency);
    }

    @Override public int hashCode() { return Objects.hash(amount, currency); }

    @Override public String toString() { return currency.getCurrencyCode() + " " + amount.toPlainString(); }
}
```

**Why both:** `HashMap`/`HashSet` first use `hashCode` to find the bucket, then `equals`
within the bucket. Override `equals` without `hashCode` → two equal keys land in different
buckets → `set.contains(equalObject)` returns `false`. See
[03-collections-generics.md](./03-collections-generics.md#under-the-hood-hashmap).

**`BigDecimal` trap:** `new BigDecimal("1.5").equals(new BigDecimal("1.50"))` is **false**
(scale differs) but `compareTo` returns 0. That's why `Money` normalizes scale in its constructor.

**Records do this for you** (Java 16+):

```java
public record Transaction(LocalDate date, String merchant, Money amount, String category) {
    public Transaction {                          // compact constructor: validation
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(merchant, "merchant");
        Objects.requireNonNull(amount, "amount");
    }
}
```
A record is a final class with private final fields, a canonical constructor, accessors
(`date()` not `getDate()`), and `equals`/`hashCode`/`toString` derived from all components.
Use records for data carriers: DTOs, value objects, map keys.

### 6. Access modifiers

| Modifier | Class | Package | Subclass (other pkg) | World |
|---|:-:|:-:|:-:|:-:|
| `private` | ✔ | | | |
| *(package-private, no keyword)* | ✔ | ✔ | | |
| `protected` | ✔ | ✔ | ✔ | |
| `public` | ✔ | ✔ | ✔ | ✔ |

Rule: start everything `private`, widen only when a test or caller needs it. Package-private is great for classes that are implementation details of a package.

### 7. Sealed types (Java 17) — closed hierarchies

```java
public sealed interface ImportResult permits ImportResult.Ok, ImportResult.Failed {
    record Ok(Transaction tx) implements ImportResult {}
    record Failed(int line, String reason) implements ImportResult {}
}

static String render(ImportResult r) {
    return switch (r) {                       // exhaustive: compiler knows all subtypes
        case ImportResult.Ok ok -> "OK " + ok.tx().merchant();
        case ImportResult.Failed f -> "Line " + f.line() + ": " + f.reason();
    };
}
```
This is how the Week 3 CSV-import kata reports per-row errors without exceptions for control flow — and the same shape models FlowGrid's reservation outcomes (`Reserved` / `InsufficientStock` / `AlreadyProcessed`).

---

## Part B — Design (read in Week 2, applied in FlowGrid Weeks 4–6)

### 8. Composition over inheritance

```java
// ❌ Inheritance for reuse: InstrumentedSet breaks if HashSet.addAll calls add() internally
class CountingSet<E> extends HashSet<E> {
    int added = 0;
    @Override public boolean add(E e) { added++; return super.add(e); }
    @Override public boolean addAll(Collection<? extends E> c) { added += c.size(); return super.addAll(c); }
}
// new CountingSet<>().addAll(List.of(1,2,3)) → added == 6, because HashSet.addAll calls add()

// ✅ Composition: wrap and forward
class CountingSet2<E> {
    private final Set<E> delegate = new HashSet<>();
    private int added = 0;
    public boolean add(E e) { added++; return delegate.add(e); }
    public boolean addAll(Collection<? extends E> c) { boolean ch = false; for (E e : c) ch |= add(e); return ch; }
    public int added() { return added; }
}
```
(*Effective Java* Item 18.) Inheritance couples you to the parent's **implementation**; composition only to its **interface**. Use inheritance for genuine *is-a* relationships you control.

### 9. SOLID, with kata and project examples

| Principle | Meaning | Kata example | Where it shows up in the projects |
|---|---|---|---|
| **S**ingle responsibility | One reason to change | `CsvParser` parses; `Categorizer` categorizes; `ReportService` aggregates | FlowGrid: reservation service vs allocation service vs shipment service |
| **O**pen/closed | Extend by adding code, not editing | New rule type = new `CategorizationRule` class | FlowGrid M3 allocation scoring factors; LedgerX M5 risk rules; FlagForge rule types |
| **L**iskov substitution | Subtypes usable wherever the parent is | Any `TransactionRepository` passes the same test suite | FlagForge SDK: polling vs streaming update sources behind one interface |
| **I**nterface segregation | Small, focused interfaces | `TransactionReader` separate from `TransactionWriter` | ForgeCI: `JobQueue` (enqueue) vs `JobLease` (claim/ack) |
| **D**ependency inversion | Depend on abstractions; inject them | `ImportService(TransactionRepository repo)` | Spring constructor injection everywhere; fakes in unit tests |

### 10. Five patterns you must be able to write from memory

**Strategy** — swap an algorithm at runtime (kata rules engine; same shape as FlowGrid's allocation scorers):

```java
public final class Categorizer {
    private final List<CategorizationRule> rules;
    public Categorizer(List<CategorizationRule> rules) {
        this.rules = rules.stream().sorted(Comparator.comparingInt(CategorizationRule::priority)).toList();
    }
    public String categorize(Transaction tx) {
        for (CategorizationRule r : rules) {
            Optional<String> c = r.categorize(tx);
            if (c.isPresent()) return c.get();
        }
        return "Uncategorized";
    }
}
```

**Factory** — pick an implementation by input (kata: multiple bank CSV formats; ForgeCI: step executor per config type):

```java
public interface BankCsvFormat { Transaction parse(String[] cols); }

public final class BankCsvFormats {
    private BankCsvFormats() {}
    public static BankCsvFormat forHeader(String headerLine) {
        if (headerLine.startsWith("Date,Description,Amount")) return new SimpleBankFormat();
        if (headerLine.startsWith("Posting Date,Payee,Debit,Credit")) return new DebitCreditFormat();
        throw new IllegalArgumentException("Unknown CSV format: " + headerLine);
    }
}
```

**Builder** — many optional parameters, readable construction, immutable result:

```java
public final class Budget {
    private final String category; private final Money limit; private final YearMonth month; private final int alertPercent;
    private Budget(Builder b) { category = b.category; limit = b.limit; month = b.month; alertPercent = b.alertPercent; }
    public static Builder builder(String category, Money limit) { return new Builder(category, limit); }

    public static final class Builder {
        private final String category; private final Money limit;
        private YearMonth month = YearMonth.now(); private int alertPercent = 80;
        private Builder(String category, Money limit) { this.category = category; this.limit = limit; }
        public Builder month(YearMonth m) { this.month = m; return this; }
        public Builder alertPercent(int p) {
            if (p < 1 || p > 100) throw new IllegalArgumentException("alertPercent 1..100");
            this.alertPercent = p; return this;
        }
        public Budget build() { return new Budget(this); }
    }
}
Budget b = Budget.builder("Groceries", groceriesLimit).alertPercent(90).build();
```

**Observer** — notify listeners without coupling (kata over-budget alerts; FlowGrid's low-stock alerts, Spring's `ApplicationEvent`s):

```java
public interface BudgetListener { void onOverBudget(String category, Money spent, Money limit); }

public final class BudgetTracker {
    private final List<BudgetListener> listeners = new ArrayList<>();
    public void subscribe(BudgetListener l) { listeners.add(l); }
    void check(String category, Money spent, Money limit) {
        if (spent.compareTo(limit) > 0) listeners.forEach(l -> l.onOverBudget(category, spent, limit));
    }
}
tracker.subscribe((c, s, l) -> System.out.println("⚠ " + c + " over budget: " + s + " / " + l));
```
(assumes `Money implements Comparable<Money>`.)

**Singleton** — one instance. Simplest correct form is an enum (*Effective Java* Item 3):

```java
public enum Clock2 { INSTANCE; public Instant now() { return Instant.now(); } }
```
Interview note: singletons are global state and hurt testability; in Spring you get "singleton
scope" beans via DI instead, which you can replace in tests. Know the lazy holder idiom too:

```java
public final class Config {
    private Config() {}
    private static final class Holder { static final Config INSTANCE = new Config(); }
    public static Config get() { return Holder.INSTANCE; }  // lazy + thread-safe via class init
}
```

---

## 🔬 Under the hood summary

- **Object header:** every object carries a header (mark word: hash, lock bits, GC age; class pointer) ≈ 12–16 bytes on 64-bit HotSpot. That's why `Integer` costs ~16 bytes vs `int`'s 4.
- **Dynamic dispatch** via method tables; `final`/`private`/`static` methods are statically bound.
- **Default `hashCode`** is an identity hash stored in the header on first call — not the memory address (objects move during GC).
- **Default `equals`** is `==`.
- **Records**' `equals`/`hashCode`/`toString` are generated with `invokedynamic` + `ObjectMethods` bootstrap.

## 🔨 Break it

1. Remove `hashCode` from `Money`, put two equal `Money` objects in a `HashSet`. Print `size()` → 2 (usually). Put it back → 1.
2. Write `public boolean equals(Money o)` (no `@Override`, wrong parameter type). Call `list.contains(new Money(...))` → `false`, because `List.contains` calls `equals(Object)`. Add `@Override` → compile error reveals it.
3. Use a mutable object as a `HashMap` key, mutate the field used in `hashCode`, call `map.get(key)` → `null`, the entry is "lost".
4. Call an overridden method from a parent constructor; print a subclass field inside it → `null`/`0`.
5. Run the `CountingSet` example → `added == 6`. Explain why.
6. Make `Money.equals` use `amount.compareTo(...) == 0` but `hashCode` use `amount.hashCode()` → equal objects with different hashes (`1.5` vs `1.50`). Fix by normalizing scale.

## ⚠️ Common mistakes

- Getters and setters for everything ("anemic" classes) — invariants leak out.
- `equals` with `getClass() != o.getClass()` vs `instanceof`: both are acceptable; be consistent. `instanceof` + `final` class is simplest.
- Deep inheritance hierarchies for code reuse.
- Singletons everywhere → untestable code. Inject dependencies.
- Exposing internal mutable collections: return `List.copyOf(items)` or `Collections.unmodifiableList(items)`.

## 🎤 Interview questions

<details><summary>1. What are the four pillars of OOP? Give a concrete example of each.</summary>

Encapsulation (Account hides balance, `deposit` enforces positive amounts), abstraction
(`TransactionRepository` interface hides JDBC vs in-memory), inheritance (`Circle extends
Shape`), polymorphism (`shape.area()` dispatches to the runtime class). Always follow with an example from your own code.
</details>

<details><summary>2. Abstract class vs interface — when do you use each?</summary>

Interface for a contract/capability, multiple implementations, no shared state (a class can
implement many). Abstract class when subclasses share state and code and form a true is-a
family (single inheritance). Since Java 8 interfaces can have default methods, so the
deciding factor is usually **state** and constructors.
</details>

<details><summary>3. What's the equals/hashCode contract, and what breaks if you violate it?</summary>

Equal objects must have equal hash codes; `equals` must be reflexive, symmetric, transitive,
consistent, and false for null. Violate it and hash-based collections misbehave: duplicates in
`HashSet`, `get` returns `null` for a key that's "there".
</details>

<details><summary>4. Overloading vs overriding?</summary>

Overloading: same name, different parameters, chosen at compile time from static types.
Overriding: subclass redefines an inherited instance method with the same signature, chosen at
runtime from the object's actual class.
</details>

<details><summary>5. Can you override a static or private method?</summary>

No. Static methods are hidden (resolved by the reference's compile-time type); private methods
aren't inherited, so a same-named method in the subclass is a new method.
</details>

<details><summary>6. Why prefer composition over inheritance?</summary>

Inheritance exposes you to the parent's implementation details (fragile base class — see the
`addAll` double-count), fixes the relationship at compile time, and allows only one parent.
Composition depends only on an interface, can be swapped at runtime (Strategy) and tested with fakes.
</details>

<details><summary>7. What is a record and when would you not use one?</summary>

Immutable data carrier with auto-generated constructor, accessors, `equals`, `hashCode`,
`toString`. Don't use it for JPA entities (need no-arg constructor, mutable, proxies), for
classes that need to extend another class, or where identity (not value) matters.
</details>

<details><summary>8. Explain the Strategy pattern using something you built.</summary>

Kata version: `CategorizationRule` is the strategy interface; merchant-contains, regex and
amount-range rules are implementations; `Categorizer` applies them in priority order. Adding a rule
type needs no engine change (open/closed), and each rule is unit-tested alone. Project version
(only once you've built it): FlowGrid's allocation combines scoring strategies (availability,
region match, workload) with a deterministic tie-break.
</details>

<details><summary>9. How do you make a class immutable?</summary>

`final` class (or private constructor), all fields `private final`, no setters, validate in the
constructor, defensively copy mutable inputs and outputs (`List.copyOf`), don't leak `this`
during construction. Or use a record with immutable component types.
</details>

<details><summary>10. How would you write a thread-safe singleton?</summary>

Enum singleton, or the initialization-on-demand holder idiom (class initialization is
guaranteed thread-safe by the JVM). Double-checked locking also works but requires the field to
be `volatile`. In Spring, let the container manage a singleton-scoped bean instead.
</details>

## ✅ Mastery checklist

- [ ] Write a class with invariants enforced in the constructor and methods (no public setters)
- [ ] Explain dynamic dispatch and why `@Override` matters
- [ ] Implement `equals`/`hashCode`/`toString` by hand and demonstrate the `HashSet` bug when broken
- [ ] Convert a value class to a record with a compact constructor
- [ ] Choose interface vs abstract class and justify it
- [ ] Use a sealed interface + exhaustive `switch`
- [ ] Map each SOLID letter to real code you wrote (kata now, FlowGrid by Week 6)
- [ ] Write Strategy, Factory, Builder, Observer, Singleton from memory
- [ ] Complete the Week 2 and design-pattern exercises in [exercises.md](./exercises.md)
