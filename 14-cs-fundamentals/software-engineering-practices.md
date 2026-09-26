# Software Engineering Practices (Weeks 4–5, revisited in every project)

> **Practical use:** keep FlowGrid's allocation logic, FlagForge's rule types and ForgeCI's executors
> easy to extend and test; review your own PRs like a teammate would (PR workflow from Week 5); log and
> handle errors consistently across all four projects.
> **Interview use:** "Explain SOLID with an example", "Which design patterns have you used?", "How do you handle errors?",
> "What do you look for in a code review?", "Tell me about technical debt you took on."

Java OOP mechanics are in [`01-java/02-oop.md`](../01-java/02-oop.md); Spring-specific logging and
error handling in [`05-spring-boot/06-logging-actuator.md`](../05-spring-boot/06-logging-actuator.md) and
[`05-spring-boot/04-validation-errors.md`](../05-spring-boot/04-validation-errors.md).

> **How to use the code on this page.** Snippets show the *shape* of each idea — an interface and a
> line or two — using names from your projects so you recognise where it applies. They are not the
> projects' implementations: you design and write those yourself in each milestone.

---

## 1. SOLID — mapped to your projects

### S — Single Responsibility Principle

*A class should have one reason to change* (one actor/stakeholder whose requirements drive it).

```java
// Smell: one class changes when the API contract, the allocation rules or the persistence change
class OrderService {
    OrderResponse create(CreateOrderRequest req) { /* validate, score warehouses, lock rows, save, map DTO */ }
}

// Shape after splitting (FlowGrid M2–M3): each collaborator changes for one reason
class OrderService       { /* orchestration + transaction boundary */ }
class AllocationPlanner  { /* which warehouse(s) — pure logic, unit-testable without a DB */ }
class ReservationStore   { /* row locking + persistence */ }
class OrderMapper        { /* entity ↔ DTO */ }
```

Smells: class names with "And"/"Manager"/"Util", 1,000-line services, tests needing ten mocks.
A pure `AllocationPlanner` (inputs → decision, no I/O) is also what makes "deterministic allocation"
testable with plain JUnit tables of cases.

### O — Open/Closed Principle

*Open for extension, closed for modification.* Add behaviour by adding code, not editing working code.

```java
// FlagForge-shaped: each targeting rule type is a new class; the evaluator loop never changes
public interface TargetingRule {
    boolean matches(EvaluationContext ctx);
}
record AttributeEqualsRule(String attribute, String value) implements TargetingRule {
    public boolean matches(EvaluationContext ctx) { return value.equals(ctx.attribute(attribute)); }
}
// UserListRule, PercentageRule, ... → added without editing the evaluator
```

Counter-point worth saying in interviews: a `switch` over a **sealed interface** of records is also
fine in modern Java — the compiler forces you to handle every case. OCP is about where change is
cheap, not about banning `switch`.

### L — Liskov Substitution Principle

*Subtypes must be usable wherever the base type is expected without breaking expectations.*

```java
// Violation: a "read-only" repository that throws on append
class ArchivedLedgerRepository implements LedgerEntryRepository {
    public void append(LedgerEntry e) { throw new UnsupportedOperationException(); } // surprises callers
}
```

Fix: split the interface (`LedgerEntryReader` vs `LedgerEntryWriter`). Classic example: `Square extends
Rectangle` breaks `setWidth` expectations. Java's own `List.of(...)` throwing on `add` is a pragmatic
LSP compromise worth knowing.

### I — Interface Segregation Principle

*Clients shouldn't depend on methods they don't use.* Prefer several small interfaces.

```java
interface ReportExporter { byte[] export(ReportQuery q); }
interface ReportStorage  { String store(byte[] data); URL link(String key, Duration ttl); }
// The CSV exporter knows nothing about S3; a test fake of ReportStorage is two methods.
```

### D — Dependency Inversion Principle

*High-level modules depend on abstractions, not on low-level details.*

```java
// ForgeCI-shaped: the job runner depends on an abstraction of "run a step in isolation"
public interface StepExecutor {
    StepResult run(StepSpec step, Workspace ws, Duration timeout);
}
// DockerStepExecutor (Engine API) in production; FakeStepExecutor in unit tests
// → retry/timeout/cancellation logic is testable without a Docker daemon.
```

DIP (a principle) enables DI (a technique — Spring's container does it). Interview line: *"Because the
job-lifecycle logic depended on a `StepExecutor` interface, I could unit-test timeouts and retries with a
fake executor and keep the Docker integration tests separate."* — say it only once it's true of your code.

---

## 2. Design patterns — the eight worth knowing cold

| Pattern | Category | One-line intent | Where it naturally appears in your projects |
|---|---|---|---|
| Strategy | Behavioral | Swap an algorithm behind an interface | FlowGrid allocation scoring factors; FlagForge rule types |
| Factory | Creational | Centralize "which implementation do I create?" | ForgeCI: executor per step type; LedgerX: journal-transaction builders per operation type |
| Builder | Creational | Construct complex/immutable objects readably | FlagForge `FlagClient.builder()` (M3); test-data builders everywhere |
| Observer | Behavioral | Notify subscribers of events without coupling | FlowGrid low-stock alerts; LedgerX outbox events; FlagForge publish → SSE |
| Singleton | Creational | Exactly one instance | Spring beans (container-managed); an SDK client per app |
| Adapter | Structural | Make an incompatible interface fit the one you need | ForgeCI wrapping the GitHub API / docker-java client behind your own interfaces |
| Decorator | Structural | Add behaviour by wrapping, same interface | Caching, retry, metrics wrappers (FlagForge SDK fetcher) |
| Template Method | Behavioral | Fixed algorithm skeleton, subclasses fill steps | ForgeCI job lifecycle: prepare → run steps → collect → **always** clean up |

### 2.1 Strategy

**When:** several interchangeable algorithms chosen by config/data; replacing a growing `switch`.

```java
public interface WarehouseScorer { double score(Warehouse w, OrderContext ctx); }
// AvailabilityScorer, RegionMatchScorer, WorkloadScorer ... combined with weights;
// ties broken by warehouse id so the result is deterministic.
```

With lambdas, a strategy can simply be a `Function<Order, Double>` or `ToDoubleBiFunction`.
Spring can inject all implementations as `List<WarehouseScorer>` or `Map<String, WarehouseScorer>`.
**Don't** use it for one algorithm that will never vary.

### 2.2 Factory (simple factory / factory method)

**When:** creation logic depends on input and callers shouldn't know concrete classes.

```java
static StepExecutor forStep(StepSpec spec) {
    return switch (spec.kind()) {
        case SHELL  -> new DockerShellExecutor(docker);
        case SCRIPT -> new DockerScriptExecutor(docker);
    };
}
```

*Factory Method* proper uses an overridable creation method in a base class; *Abstract Factory*
creates families of related objects. Know the difference exists; use the simple version. JDK
examples: `List.of`, `Executors.newFixedThreadPool`, `HttpClient.newHttpClient()`.

### 2.3 Builder

**When:** many (especially optional) parameters, validation at construction, immutability, readability.

```java
// Shape of an SDK entry point (you'll design FlagForge's in Week 22)
FlagClient client = FlagClient.builder()
        .sdkKey(System.getenv("FLAG_SDK_KEY"))     // required → validated in build()
        .pollInterval(Duration.ofSeconds(30))      // optional with sensible default
        .requestTimeout(Duration.ofSeconds(2))
        .build();
```

Rules: required parameters validated in `build()` (fail fast with a clear message), sensible defaults,
the built object immutable and thread-safe. For simple data, a **record** is enough. Builders also shine
for **test data**: `anOrder().withLine(sku, 2).build()`. JDK: `HttpRequest.newBuilder()`, `Thread.ofVirtual()`.

### 2.4 Observer

**When:** one event, many independent reactions; publishers shouldn't know subscribers.

```java
// Spring's built-in observer: publish a domain event, react after the DB commit
publisher.publishEvent(new StockBelowThreshold(skuId, warehouseId, available));

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
void onLowStock(StockBelowThreshold e) { /* notify ops */ }
```

`AFTER_COMMIT` matters: don't notify about something that might still roll back. In-process events
are lost if the process dies after commit — that's the problem the **transactional outbox** (LedgerX M3)
solves. At system scale, Observer becomes **pub/sub** (Redis pub/sub in ForgeCI logs and FlagForge
propagation). Pitfalls: listener exceptions, ordering, never-unsubscribed listeners (memory leaks).

### 2.5 Singleton

**When:** exactly one instance of a stateless service or shared resource. In Spring apps, **let the
container do it** — beans are singletons by default; hand-rolled singletons hurt testability (global state).

```java
public enum Clocks { INSTANCE; /* simplest thread-safe, serialization-safe singleton */ }

public final class Config {          // lazy holder idiom — thread-safe via class-init guarantees
    private Config() {}
    private static class Holder { static final Config INSTANCE = new Config(); }
    public static Config get() { return Holder.INSTANCE; }
}
```

Interview traps: double-checked locking needs `volatile`; a singleton with mutable state is shared by
every request thread. For an SDK: document that the client should be created **once per application**
(it owns threads and caches) — a "singleton by convention", not by enforcement.

### 2.6 Adapter

**When:** a library or external API's interface doesn't match what your code wants.

```java
public interface CommitStatusPublisher { void publish(RepoRef repo, String sha, BuildStatus status); }
// GitHubStatusAdapter implements it by calling GitHub's REST API (DTOs, auth, rate limits hidden inside)
```

This is your *anti-corruption layer*: vendor types never leak into the domain, and tests use a fake.
JDK: `Arrays.asList`, `InputStreamReader` (bytes → chars).

### 2.7 Decorator

**When:** add cross-cutting behaviour (caching, retries, logging, metrics) without changing the class,
keeping the same interface. Composable.

```java
ConfigFetcher fetcher = new MetricsFetcher(
                            new RetryingFetcher(
                                new HttpConfigFetcher(http), 3), registry);
```

Each wrapper implements `ConfigFetcher` and delegates to the next. JDK: `BufferedInputStream(new
FileInputStream(...))`, `Collections.unmodifiableList`. Spring AOP (`@Transactional`, `@Cacheable`) is
decorator-via-proxy. Decorator vs Adapter: decorator keeps the **same** interface and adds behaviour;
adapter **converts** interfaces.

### 2.8 Template Method

**When:** an algorithm's skeleton is fixed but some steps vary by subtype.

```java
public abstract class JobLifecycle {
    public final JobResult execute(Job job) {           // final: skeleton can't change
        Workspace ws = prepare(job);
        try {
            return runSteps(job, ws);                    // varies by executor type
        } finally {
            cleanup(ws);                                 // ALWAYS runs — containers never leak
        }
    }
    protected abstract Workspace prepare(Job job);
    protected abstract JobResult runSteps(Job job, Workspace ws);
    protected abstract void cleanup(Workspace ws);
}
```

Modern alternative: composition — pass the varying steps as strategies/lambdas. Prefer composition
when subclasses start overriding many hooks. `JdbcTemplate` and `TransactionTemplate` are callback-based
cousins of this idea.

### Pattern anti-patterns

Patterns are vocabulary, not goals. Don't add a Factory for one class or a Strategy for one algorithm.
*"I introduced the interface when the second implementation arrived"* is a better answer than
*"I used five patterns"*.

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

Your PR workflow (from Week 5, FlowGrid M2 onward) makes you both author and reviewer.

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

| Level | Use for | Project example |
|---|---|---|
| ERROR | Action failed and needs attention | Outbox event failed to publish after all retries |
| WARN | Unexpected but handled; may need attention if frequent | Redis unavailable, serving from DB; job retried after infra failure |
| INFO | Significant business/lifecycle events | Order created; reservation released; build finished; flag version published |
| DEBUG | Diagnostic detail, off in prod | Allocation scores per warehouse for one order |
| TRACE | Very fine detail | Rarely used |

Rules:
- **Structured logs** (JSON) with consistent fields: `timestamp, level, logger, message, requestId, userId, orderId / jobId`.
- **Correlation IDs**: put a request ID in MDC at the edge (filter), include it in every line and in error responses. Clear MDC after the request (thread reuse!).
- Use **parameterized logging**: `log.info("Job {} failed on attempt {}", jobId, attempt)` — no string concatenation, cheap when disabled.
- Log exceptions **once**, with the stack trace, at the boundary where you handle them: `log.error("Report export failed for warehouse {}", warehouseId, e)`. Don't log-and-rethrow at every layer.
- **Never log** secrets, passwords, tokens, full card numbers, presigned URLs; be careful with emails/PII.
- Logs are not metrics: count things with metrics (Micrometer), use logs for detail.
- Log to stdout in containers; let the platform ship logs ([`12-aws/cloudwatch.md`](../12-aws/cloudwatch.md)).

---

## 6. Error-handling strategy

1. **Classify errors:**

| Kind | Example | Handling | HTTP |
|---|---|---|---|
| Client/validation | Bad email, negative amount | Validate early (Bean Validation), clear message | 400 / 422 |
| Not found | Unknown SKU or order ID | Domain exception → mapped | 404 |
| Auth | Missing/invalid token; wrong role | Security layer | 401 / 403 |
| Conflict / business rule | Insufficient stock; version mismatch; idempotency key reused with a different body | Domain exception | 409 / 422 |
| Transient infrastructure | DB failover, Docker daemon hiccup, timeout calling GitHub | **Retry with backoff + jitter** (idempotent ops only), circuit breaker awareness | 503 |
| Bug | NPE, unexpected state | Don't catch-and-hide; log with stack trace; generic message | 500 |

2. **Fail fast** on invalid input and invalid config (startup validation with `@ConfigurationProperties` + `@Validated`).
3. **Use exceptions for exceptional paths**, return types (`Optional`, result objects such as an allocation result with unfilled lines) for expected outcomes.
4. **Domain exceptions** (`InsufficientStockException`) thrown in services; **one global handler** (`@RestControllerAdvice`) maps them to **RFC 7807 `ProblemDetail`** responses. Controllers stay clean.
5. **Don't swallow exceptions** (`catch (Exception e) {}`); don't catch `Throwable`; preserve the cause when wrapping (`new ExportException("…", e)`).
6. **Checked vs unchecked:** Java libraries use checked for recoverable I/O; Spring apps mostly use unchecked domain exceptions. Be consistent.
7. **Clean up** with try-with-resources; restore interrupt status on `InterruptedException`.
8. **Don't leak internals** to clients: no stack traces or SQL in responses; include the request ID so logs can be found.

```java
@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail insufficientStock(InsufficientStockException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        pd.setTitle("Insufficient stock");
        pd.setProperty("requestId", MDC.get("requestId"));
        return pd;
    }
}
```

---

## 7. Technical debt

**Definition:** the implied cost of future rework caused by choosing an expedient solution now.
Not all debt is bad — *deliberate, prudent* debt ("single-warehouse allocation first; split fulfillment is an Advanced-tier feature")
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

Good interview stories come from decisions you actually made and wrote down. The scope tiers in
[`18-projects/`](../18-projects/README.md) generate them naturally: every feature you consciously pushed
from Strong Résumé Version to Advanced is deliberate, prudent debt — with an ADR explaining why. Example
shape (only tell it if it's true): *"In FlowGrid I kept Redis on the EC2 instance instead of ElastiCache
to control cost, wrote an ADR noting that a restart empties the cache, and made sure the app degrades
to Postgres reads — so the debt was visible and its failure mode was tested."*

---

## 8. Interview questions (quick)

<details><summary>Explain SOLID with examples.</summary>

SRP: order orchestration, allocation decision and persistence in separate classes. OCP: new flag
targeting rule types are new classes; the evaluator loop is untouched. LSP: don't implement an
interface by throwing on its methods — split it. ISP: small `ReportExporter` / `ReportStorage`
interfaces. DIP: the job runner depends on a `StepExecutor` abstraction, so Docker can be faked in tests.
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
