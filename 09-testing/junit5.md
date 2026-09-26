# JUnit 5 (Jupiter) + AssertJ

> Basics in **Week 2** (foundation katas), then daily in every project. Examples use the Week 2 kata and small,
> isolated pieces of FlowGrid, LedgerX, ForgeCI and FlagForge logic.
> Version note: this file uses the JUnit Jupiter API (JUnit 5.x, as managed by Spring Boot 3.x). JUnit 6 keeps the same
> Jupiter annotations and assertions, so everything here carries over.

---

## 1. Setup

With Spring Boot, `spring-boot-starter-test` brings JUnit Jupiter, AssertJ, Mockito, Hamcrest, JSONassert and Spring Test.
Plain Maven (Week 2 kata, or a non-Spring module such as FlagForge's SDK):

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.junit</groupId>
      <artifactId>junit-bom</artifactId>
      <version>5.11.4</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
<dependencies>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.26.3</version>
    <scope>test</scope>
  </dependency>
</dependencies>
<!-- maven-surefire-plugin 3.x is required to discover JUnit 5 tests -->
```

Run: `mvn test` · single class: `mvn test -Dtest=MoneyTest` · single method: `mvn test -Dtest=MoneyTest#addsSameCurrency`.

---

## 2. Anatomy and lifecycle

```java
package dev.kata.money;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {                           // package-private is fine; no `public` needed

    @BeforeAll static void beforeAll() { }  // once per class (static unless PER_CLASS lifecycle)
    @BeforeEach void setUp() { }            // before every test
    @AfterEach void tearDown() { }          // after every test
    @AfterAll static void afterAll() { }

    @Test
    void addsSameCurrency() {
        Money a = Money.of("10.50", "EUR");
        Money b = Money.of("0.25", "EUR");
        assertEquals(Money.of("10.75", "EUR"), a.plus(b));
    }

    @Test
    @DisplayName("adding different currencies is rejected")
    void rejectsCurrencyMismatch() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> Money.of("1", "EUR").plus(Money.of("1", "USD")));
        assertTrue(ex.getMessage().contains("currency"));
    }

    @Test @Disabled("pending rounding decision, see issue #7")
    void roundsHalfEven() { }
}
```

The Week 2 `Money` kata is throwaway, but the lessons (BigDecimal, scale, currency checks) return in LedgerX (Week 9).

**Lifecycle fact:** JUnit creates a **new instance of the test class for every test method** (default
`@TestInstance(Lifecycle.PER_METHOD)`). Instance fields are fresh per test → tests don't share state. With
`@TestInstance(Lifecycle.PER_CLASS)`, one instance is shared and `@BeforeAll` can be non-static.

| Annotation | Purpose |
|---|---|
| `@Test` | test method |
| `@BeforeEach` / `@AfterEach` | per-test setup/teardown |
| `@BeforeAll` / `@AfterAll` | per-class (static) |
| `@DisplayName` | readable name in reports |
| `@Nested` | group related tests in an inner class |
| `@ParameterizedTest` | run with multiple inputs |
| `@Disabled` | skip (always give a reason) |
| `@Tag("slow")` | filter: `mvn test -Dgroups=slow` / `-DexcludedGroups=slow` |
| `@Timeout(2)` | fail if slower than 2 s |
| `@TempDir Path dir` | fresh temp directory (config/workspace/file tests) |
| `@RepeatedTest(50)` | run a test many times (useful while hunting a flaky concurrency test) |
| `@ExtendWith(...)` | plug in extensions (Mockito, Spring) |

---

## 3. Assertions

### JUnit built-ins

```java
assertEquals(expected, actual);            // expected FIRST — reversed order gives confusing messages
assertEquals(0.3, 0.1 + 0.2, 1e-9);        // doubles need a delta (and money must not be double at all)
assertNotNull(x); assertTrue(cond); assertSame(a, b);
assertIterableEquals(List.of(1, 2), list);
assertThrows(InsufficientStockException.class, () -> reservations.reserve(skuId, warehouseId, 5));
assertDoesNotThrow(() -> parser.parse(yaml));
assertTimeout(Duration.ofMillis(200), () -> evaluator.evaluate(flag, context));
assertAll("parsed pipeline",               // reports ALL failures, not just the first
    () -> assertEquals("eclipse-temurin:21-jdk", config.image()),
    () -> assertEquals(3, config.steps().size()),
    () -> assertEquals(Duration.ofMinutes(10), config.timeout()));
```

### AssertJ (preferred: fluent, better failure messages)

```java
import static org.assertj.core.api.Assertions.*;

assertThat(balance).isEqualByComparingTo("123.45");                 // BigDecimal: ignores scale
assertThat(order.number()).startsWith("SO-").hasSize(7);
assertThat(allocations)
    .hasSize(3)
    .extracting(Allocation::warehouseCode)
    .containsExactly("BER-1", "BER-1", "HAM-1");
assertThat(levels)
    .filteredOn(l -> l.available() < l.reorderPoint())
    .extracting(InventoryLevel::sku, InventoryLevel::available)
    .contains(tuple("BOLT-M8-50", 4));
assertThat(repo.findByCode("BOLT-M8-50")).isPresent().get().extracting(Sku::name).isEqualTo("Bolt M8×50");
assertThat(countsByStatus).containsEntry(OrderStatus.PICKING, 3L).doesNotContainKey(OrderStatus.CANCELLED);

assertThatThrownBy(() -> order.transitionTo(OrderStatus.SHIPPED))
    .isInstanceOf(IllegalStateTransitionException.class)
    .hasMessageContaining("PICKING -> SHIPPED");

assertThatExceptionOfType(PipelineConfigException.class)
    .isThrownBy(() -> parser.parse("steps: 42"))
    .satisfies(e -> assertThat(e.line()).isEqualTo(1));

// Soft assertions: collect all failures
SoftAssertions.assertSoftly(s -> {
    s.assertThat(result.variation()).isEqualTo("on");
    s.assertThat(result.reason()).isEqualTo(Reason.RULE_MATCH);
});

// Recursive comparison for DTOs without equals()
assertThat(actualDto).usingRecursiveComparison().ignoringFields("id", "createdAt").isEqualTo(expectedDto);
```

> **Break it:** `assertThat(new BigDecimal("1.0")).isEqualTo(new BigDecimal("1.00"))` fails (`equals` compares scale).
> `isEqualByComparingTo` passes. LedgerX must decide which semantics its `Money` type has — and document it.

---

## 4. Parameterized tests

Parameterized tests are how you test **boundaries** cheaply — the classic source of off-by-one bugs.

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class PercentageRolloutTest {                         // FlagForge M2: bucket in [0, 10000)

    @ParameterizedTest(name = "[{index}] bucket {0} with rollout {1}% → included={2}")
    @CsvSource({
        "0,     1,   true",
        "99,    1,   true",      // boundary: last bucket inside 1%
        "100,   1,   false",     // boundary: first bucket outside
        "9999, 100,  true",
        "0,     0,   false"
    })
    void includesBucketsBelowThreshold(int bucket, int percent, boolean expected) {
        assertThat(Rollout.includes(bucket, percent)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user-1", "user-2", "ü-ñ-日本", ""})
    void bucketIsStableAndInRange(String userKey) {
        int b1 = Bucketing.bucket("new-checkout", userKey);
        int b2 = Bucketing.bucket("new-checkout", userKey);
        assertThat(b1).isEqualTo(b2).isBetween(0, 9_999);
    }
}

class SkuCodeTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "bolt m8", "TOO-LONG-CODE-THAT-EXCEEDS-THIRTY-TWO-CHARS"})
    void rejectsInvalidCodes(String code) {
        assertThatThrownBy(() -> SkuCode.of(code)).isInstanceOf(IllegalArgumentException.class);
    }
}

class OrderStateMachineTest {
    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"SHIPPED", "CANCELLED"})
    void terminalStatesCannotBeCancelled(OrderStatus terminal) {
        var order = TestOrders.inStatus(terminal);
        assertThatThrownBy(order::cancel).isInstanceOf(IllegalStateTransitionException.class);
    }

    @ParameterizedTest
    @MethodSource("legalTransitions")
    void allowsLegalTransitions(OrderStatus from, OrderStatus to) {
        assertThat(TestOrders.inStatus(from).transitionTo(to).status()).isEqualTo(to);
    }
    static Stream<Arguments> legalTransitions() {
        return Stream.of(
            Arguments.of(OrderStatus.RESERVED, OrderStatus.ALLOCATED),
            Arguments.of(OrderStatus.ALLOCATED, OrderStatus.PICKING),
            Arguments.of(OrderStatus.PICKING, OrderStatus.PACKED));
    }
}
```

Requires `junit-jupiter-params` (included in the `junit-jupiter` aggregate and in `spring-boot-starter-test`).
The names above (`Rollout`, `Bucketing`, `OrderStatus`…) are illustrative — use your own classes; the point is the
**table of boundaries**, which you derive from the milestone's acceptance criteria.

---

## 5. Nested tests

```java
@DisplayName("Reservation")                            // FlowGrid M2: reservations expire
class ReservationTest {
    private final Instant start = Instant.parse("2026-05-01T10:00:00Z");

    @Nested @DisplayName("when active")
    class WhenActive {
        Reservation r = Reservation.create(7L, 3, start, Duration.ofMinutes(15));
        Clock at14min = Clock.fixed(start.plus(Duration.ofMinutes(14)), ZoneOffset.UTC);

        @Test void canBeAllocated() { assertThat(r.allocate(at14min).status()).isEqualTo(ReservationStatus.ALLOCATED); }
        @Test void isNotExpired()   { assertThat(r.isExpired(at14min)).isFalse(); }
    }

    @Nested @DisplayName("when expired")
    class WhenExpired {
        Reservation r = Reservation.create(7L, 3, start, Duration.ofMinutes(15));
        Clock at15min = Clock.fixed(start.plus(Duration.ofMinutes(15)), ZoneOffset.UTC);   // boundary!

        @Test void cannotBeAllocated() {
            assertThatThrownBy(() -> r.allocate(at15min)).isInstanceOf(ReservationExpiredException.class);
        }
    }
}
```

`@Nested` classes must be non-static inner classes; outer `@BeforeEach` methods run before inner ones.
Whether "exactly at expiry" counts as expired is a **decision** — the boundary test forces you to make it.

---

## 6. Testing with files (`@TempDir`)

```java
@Test
void parsesStepsAndReportsBadLineNumbers(@TempDir Path workspace) throws IOException {   // ForgeCI M1
    Files.writeString(workspace.resolve(".forgeci.yml"), """
        image: eclipse-temurin:21-jdk
        steps:
          - mvn -B verify
          -
        """);

    assertThatThrownBy(() -> PipelineConfigParser.parse(workspace.resolve(".forgeci.yml")))
        .isInstanceOf(PipelineConfigException.class)
        .hasMessageContaining("line 4");
}
```

`@TempDir` gives each test a fresh directory that JUnit deletes afterwards — the same "always clean up the workspace"
idea ForgeCI's worker must implement for real.

---

## 7. Assumptions, tags, ordering

```java
@Test void onlyOnCi() { assumeTrue("true".equals(System.getenv("CI"))); /* skipped otherwise */ }
@Tag("slow") @Test void bigImport() { }
```

Test order is deterministic but **intentionally not obvious**. Never depend on it. `@TestMethodOrder(OrderAnnotation.class)` exists; needing it is a smell.

---

## 8. Break it

1. Make tests share a `static List` that each test adds to. Run tests individually (pass) and together (fail). Fix with an instance field.
2. Reverse `assertEquals(actual, expected)` and read the misleading failure message.
3. Write a test with no assertion. It passes. Coverage goes up. What did it prove?
4. Replace `Clock.fixed` with `Instant.now()` in `ReservationTest` and add a `Thread.sleep`. Run it with `@RepeatedTest(20)`. Why is it slow *and* unreliable?
5. Make `Bucketing.bucket` use `String.hashCode()` with `Math.abs(h) % 10000`. Find the input class that breaks it (hint: `Integer.MIN_VALUE`). Why does FlagForge need a stable, well-distributed hash instead?

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `assertEquals(actual, expected)` | expected first; or AssertJ `assertThat(actual)` |
| `assertTrue(list.size() == 3)` | `assertThat(list).hasSize(3)` — useful message |
| `try { …; fail(); } catch (E e) {}` | `assertThrows` / `assertThatThrownBy` |
| Testing private methods via reflection | test through the public API |
| Static shared state | instance fields, `@BeforeEach` |
| One giant test for everything | one behaviour per test |
| `Thread.sleep` for timing | `Clock`, Awaitility |

---

## Interview Q&A

<details><summary>JUnit 4 vs JUnit 5?</summary>

JUnit 5 = Platform (launcher) + Jupiter (new API) + Vintage (runs JUnit 4). Jupiter uses `@BeforeEach`/`@AfterEach`
instead of `@Before`/`@After`, `@ExtendWith` instead of runners/rules, adds `@Nested`, `@ParameterizedTest`,
`@DisplayName`, `assertThrows`, `assertAll`, and doesn't require public classes/methods.
</details>

<details><summary>Is a new test-class instance created per test?</summary>

Yes, by default (PER_METHOD), which isolates instance state between tests. `@TestInstance(PER_CLASS)` shares one instance.
</details>

<details><summary>How do you test that an exception is thrown?</summary>

`assertThrows(Type.class, executable)` returns the exception so I can assert on its message or fields; or AssertJ's
`assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)`.
</details>

<details><summary>Why parameterized tests?</summary>

To cover many inputs, especially boundaries, with one test body — e.g. the rollout-percentage boundaries in FlagForge
or every legal/illegal transition of FlowGrid's order state machine.
</details>

<details><summary>How do you test code that depends on time?</summary>

Inject `java.time.Clock` and use `Clock.fixed`/an adjustable test clock, so expiry boundaries are exact and tests are
instant and deterministic.
</details>

---

## Mastery checklist

- [ ] Explain the JUnit 5 lifecycle and per-method instances.
- [ ] Write a parameterized boundary test with `@CsvSource` and `@MethodSource`.
- [ ] Use `@Nested` + `@DisplayName` for a state-based class (reservation, job, flag version).
- [ ] Use `@TempDir` for a file/workspace test.
- [ ] Use AssertJ `extracting`, `containsExactly`, `assertThatThrownBy`, `usingRecursiveComparison`, `isEqualByComparingTo`.
- [ ] Run a single test method from Maven and from the IDE.
