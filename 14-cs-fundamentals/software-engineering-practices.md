# Software Engineering Practices (Week 5, revisited Weeks 12 and 18)

> **Practical use:** design P1's rules engine and CSV importers so they're easy to extend, review
> your own PRs like a teammate would, log and handle errors consistently across P2–P4.
> **Interview use:** "Explain SOLID with an example", "Which design patterns have you used?", "How do you handle errors?",
> "What do you look for in a code review?", "Tell me about technical debt you took on."

Java OOP mechanics are in [`01-java/02-oop.md`](../01-java/02-oop.md); Spring-specific logging and
error handling in [`05-spring-boot/06-logging-actuator.md`](../05-spring-boot/06-logging-actuator.md) and
[`05-spring-boot/04-validation-errors.md`](../05-spring-boot/04-validation-errors.md).

---

## 1. SOLID — with examples from your projects

### S — Single Responsibility Principle

*A class should have one reason to change* (one actor/stakeholder whose requirements drive it).

```java
// Before: parsing, categorizing and persisting all in one — three reasons to change
class CsvImporter {
    void importFile(Path file) { /* parse lines, apply rules, INSERT rows */ }
}

// After (P1): each class changes for one reason
class CsvTransactionParser { List<ParsedRow> parse(Reader in) { … } }        // bank format changes
class Categorizer          { Category categorize(Transaction t) { … } }       // rule changes
class ImportService {                                                         // orchestration changes
    ImportResult importFile(Path file) { /* parse → categorize → repository.saveAll */ }
}
```

Smell: class names with "And"/"Manager"/"Util", 1,000-line services, tests needing ten mocks.

### O — Open/Closed Principle

*Open for extension, closed for modification.* Add behaviour by adding code, not editing working code.

```java
public interface CategorizationRule {
    Optional<Category> apply(Transaction t);
    int priority();
}

record MerchantContainsRule(String fragment, Category category, int priority) implements CategorizationRule {
    public Optional<Category> apply(Transaction t) {
        return t.merchant().toLowerCase().contains(fragment.toLowerCase())
                ? Optional.of(category) : Optional.empty();
    }
}

record AmountRangeRule(BigDecimal min, BigDecimal max, Category category, int priority) implements CategorizationRule {
    public Optional<Category> apply(Transaction t) {
        BigDecimal a = t.amount().abs();
        return a.compareTo(min) >= 0 && a.compareTo(max) <= 0 ? Optional.of(category) : Optional.empty();
    }
}

final class Categorizer {
    private final List<CategorizationRule> rules;
    Categorizer(List<CategorizationRule> rules) {
        this.rules = rules.stream().sorted(Comparator.comparingInt(CategorizationRule::priority)).toList();
    }
    Category categorize(Transaction t) {
        return rules.stream().map(r -> r.apply(t)).flatMap(Optional::stream)
                .findFirst().orElse(Category.UNCATEGORIZED);
    }
}
```

A new `RegexRule` requires **zero** changes to `Categorizer`. (This is also the Strategy pattern.)

### L — Liskov Substitution Principle

*Subtypes must be usable wherever the base type is expected without breaking expectations.*

```java
// Violation: a "read-only" repository that throws on save
class ArchivedTransactionRepository implements TransactionRepository {
    public void save(Transaction t) { throw new UnsupportedOperationException(); } // surprises callers
}
```

Fix: split the interface (`TransactionReader` vs `TransactionRepository extends TransactionReader`).
Classic example: `Square extends Rectangle` breaks `setWidth` expectations. Java's own
`List.of(...)` throwing on `add` is a pragmatic LSP compromise worth knowing.

### I — Interface Segregation Principle

*Clients shouldn't depend on methods they don't use.* Prefer several small interfaces.

```java
interface ReportExporter { byte[] export(Report r); }
interface ReportStorage  { String store(byte[] data); URL link(String key); }
// A CSV exporter doesn't need to know about S3; a test fake of ReportStorage is two methods.
```

### D — Dependency Inversion Principle

*High-level modules depend on abstractions, not on low-level details.*

```java
// P1 M4: the service depends on an interface; JDBC is a detail plugged in from outside
final class ReportService {
    private final TransactionRepository repository;          // abstraction
    ReportService(TransactionRepository repository) { this.repository = repository; } // constructor injection
}
// Implementations: InMemoryTransactionRepository (tests, M1–M3), JdbcTransactionRepository (M4)
```

DIP (a principle) enables DI (a technique — Spring's container does it). Interview line: *"Swapping
in-memory storage for Postgres in P1 touched zero lines of ReportService because it depended on the
`TransactionRepository` interface."*

---

## 2. Design patterns — the eight worth knowing cold

| Pattern | Category | One-line intent | Your project |
|---|---|---|---|
| Strategy | Behavioral | Swap an algorithm at runtime behind an interface | P1 categorization rules; P4 check types (HTTP, TCP) |
| Factory | Creational | Centralize "which implementation do I create?" | P1 bank CSV parsers |
| Builder | Creational | Construct complex/immutable objects readably | P1 `Transaction`, test data builders |
| Observer | Behavioral | Notify subscribers of events without coupling | P1 over-budget alerts; P4 incident → notifiers |
| Singleton | Creational | Exactly one instance | Spring beans (container-managed) |
| Adapter | Structural | Make an incompatible interface fit the one you need | P4 wrapping email/webhook providers |
| Decorator | Structural | Add behaviour by wrapping, same interface | Caching/retrying/logging wrappers |
| Template Method | Behavioral | Fixed algorithm skeleton, subclasses fill steps | Abstract CSV parser base class |

### 2.1 Strategy

**When:** several interchangeable algorithms, chosen by config/data; replacing `switch` on a type.

```java
public interface CheckStrategy { CheckResult check(Monitor m); }

@Component("HTTP") class HttpCheck implements CheckStrategy { public CheckResult check(Monitor m) { … } }
@Component("TCP")  class TcpCheck  implements CheckStrategy { public CheckResult check(Monitor m) { … } }

@Service
class Checker {
    private final Map<String, CheckStrategy> strategies;   // Spring injects bean-name → bean
    Checker(Map<String, CheckStrategy> strategies) { this.strategies = strategies; }
    CheckResult run(Monitor m) { return strategies.get(m.type().name()).check(m); }
}
```

With lambdas, a strategy can simply be a `Function<Transaction, Optional<Category>>`. **Don't** use
it for one algorithm that will never vary.

### 2.2 Factory (simple factory / factory method)

**When:** creation logic depends on input and you don't want callers to know concrete classes.

```java
public final class ParserFactory {
    public static TransactionParser forBank(String bankCode) {
        return switch (bankCode.toUpperCase()) {
            case "CHASE" -> new ChaseCsvParser();
            case "REVOLUT" -> new RevolutCsvParser();
            case "GENERIC" -> new GenericCsvParser();
            default -> throw new IllegalArgumentException("Unsupported bank: " + bankCode);
        };
    }
}
```

The *Factory Method* pattern proper uses an overridable creation method in a base class; *Abstract
Factory* creates families of related objects. For interviews: know the difference exists, use the
simple version. JDK examples: `List.of`, `Executors.newFixedThreadPool`, `Calendar.getInstance`.

### 2.3 Builder

**When:** many parameters (especially optional), immutability, readability over telescoping constructors.

```java
public final class Transaction {
    private final LocalDate date; private final String merchant;
    private final Money amount; private final Category category; private final String note;

    private Transaction(Builder b) {
        this.date = Objects.requireNonNull(b.date, "date");
        this.merchant = Objects.requireNonNull(b.merchant, "merchant");
        this.amount = Objects.requireNonNull(b.amount, "amount");
        this.category = b.category == null ? Category.UNCATEGORIZED : b.category;
        this.note = b.note;
    }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private LocalDate date; private String merchant; private Money amount;
        private Category category; private String note;
        public Builder date(LocalDate d) { this.date = d; return this; }
        public Builder merchant(String m) { this.merchant = m; return this; }
        public Builder amount(Money a) { this.amount = a; return this; }
        public Builder category(Category c) { this.category = c; return this; }
        public Builder note(String n) { this.note = n; return this; }
        public Transaction build() { return new Transaction(this); }   // validate here
    }
}
```

For simple immutable data, a **record** is often enough. Builders shine in **test data**:
`aTransaction().withAmount("12.50").build()`. JDK: `StringBuilder`, `HttpRequest.newBuilder()`, `Thread.ofVirtual()`.

### 2.4 Observer

**When:** one event, many independent reactions; publishers shouldn't know subscribers.

```java
public interface BudgetListener { void onOverBudget(Budget budget, Money spent); }

final class BudgetTracker {
    private final List<BudgetListener> listeners = new CopyOnWriteArrayList<>();
    void subscribe(BudgetListener l) { listeners.add(l); }
    void record(Transaction t) {
        // … update totals …
        if (spent.isGreaterThan(budget.limit())) listeners.forEach(l -> l.onOverBudget(budget, spent));
    }
}
```

Spring version: `ApplicationEventPublisher.publishEvent(new IncidentOpened(...))` +
`@EventListener` / `@TransactionalEventListener(phase = AFTER_COMMIT)` (don't email before the DB commit!).
At system scale, Observer becomes **pub/sub** with a message broker. Pitfalls: listener exceptions,
ordering, memory leaks from never-unsubscribed listeners.

### 2.5 Singleton

**When:** exactly one instance of a stateless service or shared resource. In Spring apps, **let the
container do it** — beans are singletons by default; hand-rolled singletons hurt testability (global state).

```java
public enum IdGenerator {            // simplest thread-safe singleton; serialization-safe
    INSTANCE;
    private final AtomicLong next = new AtomicLong();
    public long nextId() { return next.incrementAndGet(); }
}

public final class Config {          // lazy holder idiom — thread-safe via class-init guarantees
    private Config() {}
    private static class Holder { static final Config INSTANCE = new Config(); }
    public static Config get() { return Holder.INSTANCE; }
}
```

Interview traps: double-checked locking needs `volatile`; singletons with mutable state are shared
across all request threads.

### 2.6 Adapter

**When:** you must use a class/library whose interface doesn't match what your code expects.

```java
public interface Notifier { void send(Alert alert); }            // what PulseWatch wants

final class SmtpNotifierAdapter implements Notifier {             // wraps a third-party mail API
    private final ThirdPartyMailClient client;
    SmtpNotifierAdapter(ThirdPartyMailClient client) { this.client = client; }
    public void send(Alert alert) {
        client.sendMail(new MailMessage(alert.recipient(), alert.subject(), alert.body()));
    }
}
```

Also your "anti-corruption layer" around external APIs: keep vendor types out of your domain.
JDK: `Arrays.asList`, `InputStreamReader` (bytes → chars).

### 2.7 Decorator

**When:** add cross-cutting behaviour (caching, retries, logging, metrics) without changing the class
and while keeping the same interface. Composable.

```java
final class RetryingNotifier implements Notifier {
    private final Notifier delegate; private final int maxAttempts;
    RetryingNotifier(Notifier delegate, int maxAttempts) { this.delegate = delegate; this.maxAttempts = maxAttempts; }

    public void send(Alert alert) {
        for (int attempt = 1; ; attempt++) {
            try { delegate.send(alert); return; }
            catch (RuntimeException e) {
                if (attempt >= maxAttempts) throw e;
                sleep(Duration.ofMillis(200L * (1L << attempt)));   // exponential backoff
            }
        }
    }
    private static void sleep(Duration d) {
        try { Thread.sleep(d); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IllegalStateException(ie); }
    }
}

Notifier notifier = new RetryingNotifier(new LoggingNotifier(new WebhookNotifier(http)), 3);
```

JDK: `BufferedInputStream(new FileInputStream(...))`, `Collections.unmodifiableList`. Spring AOP
(`@Transactional`, `@Cacheable`, `@Retryable`) is decorator via proxies. Decorator vs Adapter: decorator
keeps the **same** interface and adds behaviour; adapter **converts** interfaces.

### 2.8 Template Method

**When:** an algorithm's skeleton is fixed but some steps vary by subtype.

```java
public abstract class AbstractCsvParser implements TransactionParser {
    @Override
    public final ImportResult parse(Reader in) {                 // final: skeleton can't change
        List<Transaction> ok = new ArrayList<>(); List<RowError> errors = new ArrayList<>();
        List<String[]> rows = readRows(in);
        for (int i = headerRows(); i < rows.size(); i++) {
            try { ok.add(mapRow(rows.get(i))); }
            catch (IllegalArgumentException e) { errors.add(new RowError(i + 1, e.getMessage())); }
        }
        return new ImportResult(ok, errors);
    }
    protected int headerRows() { return 1; }                        // hook with default
    protected abstract Transaction mapRow(String[] columns);        // step subclasses supply
    private List<String[]> readRows(Reader in) { … }
}
```

Modern alternative: composition — pass the varying step as a lambda/Strategy. Prefer composition when
subclasses start overriding many hooks. Spring's `JdbcTemplate`/`RestTemplate` are callback-based
cousins of this idea.

### Pattern anti-patterns

Patterns are vocabulary, not goals. Don't add a Factory for one class or a Strategy for one
algorithm. "I introduced the Factory when the second bank format arrived" is a better answer than
"I used five patterns".

---

## 3. Clean code — the practical subset

| Practice | Example |
|---|---|
| Intention-revealing names | `overBudgetCategories` not `list2`; `isExpired(now)` not `check()` |
| Small functions doing one thing | Extract `parseAmount(String)` from a 60-line loop |
| Few parameters | > 3 → parameter object or builder |
| No magic numbers | `MAX_CONSECUTIVE_FAILURES = 3` |
| Guard clauses over nesting | `if (rows.isEmpty()) return ImportResult.empty();` |
| Immutability by default | `final` fields, records, `List.copyOf` |
| Don't return null for collections | Return `List.of()`; use `Optional` for "maybe a value" return types (not fields/params) |
| Comments explain **why**, code explains what | `// Bank X exports debits as positive numbers` |
| Consistent formatting | Formatter/linter in the build (Spotless, ESLint + Prettier) |
| Delete dead code | Git remembers it |
| Tests as documentation | `@DisplayName("hold expires after 10 minutes")` |

Read *Clean Code* critically — some advice (very tiny functions, no comments) is taken too far by
some; *Refactoring* (Fowler) and *Effective Java* have aged better.

---

## 4. Code review

Your P1+ PR workflow (from Week 5) makes you both author and reviewer.

**As author:**
- [ ] Small PR (< ~400 lines changed), one purpose, descriptive title
- [ ] Description: *what*, *why*, *how to test*, screenshots for UI, linked issue
- [ ] Self-review the diff first; CI green; no debug prints, commented-out code, secrets

**As reviewer — in priority order:**
1. **Correctness:** does it do what it claims? Edge cases (empty, null, huge, concurrent, timezone)?
2. **Security:** input validation, authorization checks, SQL injection, secrets, logging PII.
3. **Design:** right place/layer? Coupling? Will it be easy to change?
4. **Tests:** meaningful assertions; would they fail if the code were wrong?
5. **Readability:** names, structure, comments where non-obvious.
6. **Performance:** N+1 queries, unbounded loads, missing indexes — when it matters.
7. **Style nits:** label them `nit:` — ideally automated away.

Tone: comment on code, not people; ask questions ("What happens if `rows` is empty?"); explain why;
approve with minor comments rather than blocking. Interview question "how do you handle disagreement
in code review?" → evidence, data, discuss synchronously if it drags, defer to team conventions,
disagree-and-commit.

---

## 5. Logging practices

| Level | Use for | P4 example |
|---|---|---|
| ERROR | Action failed and needs attention | Alert delivery failed after all retries |
| WARN | Unexpected but handled; may need attention if frequent | Check timed out; retrying |
| INFO | Significant business/lifecycle events | Monitor created; incident opened/resolved; app started |
| DEBUG | Diagnostic detail, off in prod | Request/response details of a check |
| TRACE | Very fine detail | Rarely used |

Rules:
- **Structured logs** (JSON) with consistent fields: `timestamp, level, logger, message, requestId, userId, monitorId`.
- **Correlation IDs**: put a request ID in MDC at the edge (filter), include it in every line and in error responses. Clear MDC after the request (thread reuse!).
- Use **parameterized logging**: `log.info("Monitor {} failed {} times", id, count)` — no string concatenation, cheap when disabled.
- Log exceptions **once**, with the stack trace, at the boundary where you handle them: `log.error("Export failed for monitor {}", id, e)`. Don't log-and-rethrow at every layer.
- **Never log** secrets, passwords, tokens, full card numbers, presigned URLs; be careful with emails/PII.
- Logs are not metrics: count things with metrics (Micrometer), use logs for detail.
- Log to stdout in containers; let the platform ship logs ([`12-aws/cloudwatch.md`](../12-aws/cloudwatch.md)).

---

## 6. Error-handling strategy

1. **Classify errors:**

| Kind | Example | Handling | HTTP |
|---|---|---|---|
| Client/validation | Bad email, negative amount | Validate early (Bean Validation), clear message | 400 / 422 |
| Not found | Unknown monitor ID | Domain exception → mapped | 404 |
| Auth | Missing/invalid token; wrong role | Security layer | 401 / 403 |
| Conflict / business rule | Seat already held; version mismatch | Domain exception | 409 |
| Transient infrastructure | DB failover, timeout calling webhook | **Retry with backoff + jitter** (idempotent ops only), circuit breaker awareness | 503 |
| Bug | NPE, unexpected state | Don't catch-and-hide; log with stack trace; generic message | 500 |

2. **Fail fast** on invalid input and invalid config (startup validation with `@ConfigurationProperties` + `@Validated`).
3. **Use exceptions for exceptional paths**, return types (`Optional`, result objects like P1's `ImportResult` with per-row errors) for expected outcomes.
4. **Domain exceptions** (`SeatUnavailableException`) thrown in services; **one global handler** (`@RestControllerAdvice`) maps them to **RFC 7807 `ProblemDetail`** responses. Controllers stay clean.
5. **Don't swallow exceptions** (`catch (Exception e) {}`); don't catch `Throwable`; preserve the cause when wrapping (`new ExportException("…", e)`).
6. **Checked vs unchecked:** Java libraries use checked for recoverable I/O; Spring apps mostly use unchecked domain exceptions. Be consistent.
7. **Clean up** with try-with-resources; restore interrupt status on `InterruptedException`.
8. **Don't leak internals** to clients: no stack traces or SQL in responses; include the request ID so logs can be found.

```java
@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(SeatUnavailableException.class)
    ProblemDetail seatTaken(SeatUnavailableException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        pd.setTitle("Seat unavailable");
        pd.setProperty("requestId", MDC.get("requestId"));
        return pd;
    }
}
```

---

## 7. Technical debt

**Definition:** the implied cost of future rework caused by choosing an expedient solution now.
Not all debt is bad — *deliberate, prudent* debt ("ship with in-memory storage, add Postgres in M4")
is a valid trade-off; *reckless, inadvertent* debt (no tests, copy-paste) is not.

| Quadrant (Fowler) | Deliberate | Inadvertent |
|---|---|---|
| **Prudent** | "We must ship now and will refactor X" | "Now we know how we should have done it" |
| **Reckless** | "We don't have time for design" | "What's layering?" |

Managing it:
- Make it **visible**: TODO with issue link, ADR noting the trade-off, backlog item.
- **Boy Scout rule**: leave code slightly better than you found it.
- Refactor **with tests** in place; small safe steps.
- Prioritize by **interest paid**: debt in code you touch weekly costs more than in code nobody touches.
- Explain to non-engineers in terms of delivery speed and risk.

Good interview story from this roadmap (true, because you'll do it): *"In P1 I started with CSV
parsing inside the command class. When the second bank format arrived in M3, that became painful, so
I extracted a parser interface with a factory and a template-method base class; adding the third
format was then a 40-line class plus tests."*

---

## 8. Interview questions (quick)

<details><summary>Explain SOLID with examples.</summary>

SRP: parser, categorizer and import service separated. OCP: new categorization rules are new
classes, `Categorizer` untouched. LSP: don't implement an interface by throwing on its methods —
split it. ISP: small `ReportExporter` / `ReportStorage` interfaces. DIP: services depend on
`TransactionRepository`, so JDBC replaced in-memory storage without touching them.
</details>

<details><summary>Strategy vs Template Method?</summary>

Both vary part of an algorithm. Template Method uses inheritance: a base class fixes the skeleton and
subclasses override steps. Strategy uses composition: the varying algorithm is an injected object,
swappable at runtime. Prefer Strategy/composition by default.
</details>

<details><summary>Decorator vs Adapter vs Proxy?</summary>

Decorator: same interface, adds behaviour, stackable. Adapter: converts one interface to another.
Proxy: same interface, controls access (lazy loading, security, remote, transactions). Spring AOP
proxies are proxies that often act as decorators.
</details>

<details><summary>What do you look for in a code review?</summary>

Correctness and edge cases first, then security, design/fit, tests, readability, performance, and
finally style — which should be automated.
</details>

<details><summary>How do you handle errors in a REST API?</summary>

Validate at the edge, throw domain exceptions from services, map them centrally with
`@RestControllerAdvice` to `ProblemDetail` responses with correct status codes and a request ID; log
unexpected errors once with stack traces; retry only transient failures on idempotent operations.
</details>
