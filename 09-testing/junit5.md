# JUnit 5 (Jupiter) + AssertJ

> Basics in **Week 2** (first tests), deepened in **Week 12**. Examples from P1 Ledger and P2 TicketHold.
> Version note: this file uses the JUnit Jupiter API (JUnit 5.x, as managed by Spring Boot 3.x). JUnit 6 keeps the same
> Jupiter annotations and assertions, so everything here carries over.

---

## 1. Setup

With Spring Boot, `spring-boot-starter-test` brings JUnit Jupiter, AssertJ, Mockito, Hamcrest, JSONassert and Spring Test.
Plain Maven (P1 Ledger):

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
package dev.ledger.domain;

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
| `@TempDir Path dir` | fresh temp directory (great for P1 CSV import tests) |
| `@ExtendWith(...)` | plug in extensions (Mockito, Spring) |

---

## 3. Assertions

### JUnit built-ins

```java
assertEquals(expected, actual);            // expected FIRST — reversed order gives confusing messages
assertEquals(0.3, 0.1 + 0.2, 1e-9);        // doubles need a delta
assertNotNull(x); assertTrue(cond); assertSame(a, b);
assertIterableEquals(List.of(1, 2), list);
assertThrows(SeatUnavailableException.class, () -> service.hold(seatId, userId));
assertDoesNotThrow(() -> parser.parse(line));
assertTimeout(Duration.ofMillis(200), () -> report.generate());
assertAll("parsed transaction",            // reports ALL failures, not just the first
    () -> assertEquals(LocalDate.of(2026, 1, 3), tx.date()),
    () -> assertEquals("COFFEE BAR", tx.merchant()),
    () -> assertEquals(Money.of("-4.50", "EUR"), tx.amount()));
```

### AssertJ (preferred: fluent, better failure messages)

```java
import static org.assertj.core.api.Assertions.*;

assertThat(report.total()).isEqualByComparingTo("123.45");        // BigDecimal: ignores scale
assertThat(tx.merchant()).isEqualTo("COFFEE BAR").startsWith("COFFEE");
assertThat(transactions)
    .hasSize(3)
    .extracting(Transaction::category)
    .containsExactly(Category.FOOD, Category.RENT, Category.FOOD);
assertThat(transactions)
    .filteredOn(t -> t.amount().isNegative())
    .extracting(Transaction::merchant, t -> t.amount().value())
    .contains(tuple("COFFEE BAR", new BigDecimal("-4.50")));
assertThat(optionalEvent).isPresent().get().extracting(Event::name).isEqualTo("Jazz Night");
assertThat(map).containsEntry("FOOD", new BigDecimal("12.00")).doesNotContainKey("UNKNOWN");

assertThatThrownBy(() -> bookingService.confirm(holdId))
    .isInstanceOf(HoldExpiredException.class)
    .hasMessageContaining("expired");

assertThatExceptionOfType(ImportException.class)
    .isThrownBy(() -> importer.importFile(badCsv))
    .satisfies(e -> assertThat(e.errors()).hasSize(2));

// Soft assertions: collect all failures
SoftAssertions.assertSoftly(s -> {
    s.assertThat(event.name()).isEqualTo("Jazz Night");
    s.assertThat(event.capacity()).isEqualTo(120);
});

// Recursive comparison for DTOs without equals()
assertThat(actualDto).usingRecursiveComparison().ignoringFields("id", "createdAt").isEqualTo(expectedDto);
```

> **Break it:** `assertThat(new BigDecimal("1.0")).isEqualTo(new BigDecimal("1.00"))` fails (`equals` compares scale).
> `isEqualByComparingTo` passes. Same trap as P1 Ledger's `Money.equals` — decide and document which semantics you want.

---

## 4. Parameterized tests

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class MerchantRuleTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" matches COFFEE rule → {1}")
    @CsvSource({
        "COFFEE BAR 123,     true",
        "coffee bar,         true",
        "'STARBUCKS, NYC',   false",   // quote values containing commas
        "'',                 false"
    })
    void merchantContains(String merchant, boolean expected) {
        var rule = new MerchantContainsRule("coffee", Category.FOOD);
        assertThat(rule.matches(txWithMerchant(merchant))).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-01", "not-a-date", "01/02/2026"})
    void rejectsBadDates(String raw) {
        assertThatThrownBy(() -> CsvRowParser.parseDate(raw)).isInstanceOf(DateTimeParseException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void blankMerchantIsUncategorized(String merchant) {
        assertThat(engine.categorize(txWithMerchant(merchant))).isEqualTo(Category.UNCATEGORIZED);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER"})
    void customersCannotCreateEvents(Role role) { /* ... */ }

    @ParameterizedTest
    @MethodSource("amountRanges")
    void amountRange(BigDecimal amount, boolean expected) {
        assertThat(new AmountRangeRule(new BigDecimal("10"), new BigDecimal("50")).matches(txWithAmount(amount)))
            .isEqualTo(expected);
    }
    static Stream<Arguments> amountRanges() {
        return Stream.of(
            Arguments.of(new BigDecimal("9.99"), false),
            Arguments.of(new BigDecimal("10.00"), true),   // boundary: inclusive lower
            Arguments.of(new BigDecimal("50.00"), true),   // boundary: inclusive upper
            Arguments.of(new BigDecimal("50.01"), false));
    }
}
```

Requires `junit-jupiter-params` (included in the `junit-jupiter` aggregate and in `spring-boot-starter-test`).
Parameterized tests are how you test **boundaries** cheaply — the classic source of off-by-one bugs.

---

## 5. Nested tests

```java
@DisplayName("Hold")
class HoldTest {
    private final Instant start = Instant.parse("2026-05-01T10:00:00Z");

    @Nested @DisplayName("when active")
    class WhenActive {
        Hold hold = Hold.create(1L, 42L, start, Duration.ofMinutes(5));
        Clock at4min = Clock.fixed(start.plusSeconds(240), ZoneOffset.UTC);

        @Test void canBeConfirmed() { assertThat(hold.confirm(at4min).status()).isEqualTo(HoldStatus.CONFIRMED); }
        @Test void isNotExpired()   { assertThat(hold.isExpired(at4min)).isFalse(); }
    }

    @Nested @DisplayName("when expired")
    class WhenExpired {
        Hold hold = Hold.create(1L, 42L, start, Duration.ofMinutes(5));
        Clock at5min = Clock.fixed(start.plusSeconds(300), ZoneOffset.UTC);   // boundary!

        @Test void cannotBeConfirmed() {
            assertThatThrownBy(() -> hold.confirm(at5min)).isInstanceOf(HoldExpiredException.class);
        }
    }
}
```

`@Nested` classes must be non-static inner classes; outer `@BeforeEach` methods run before inner ones.

---

## 6. Testing with files and exceptions (P1 Ledger)

```java
@Test
void importReportsBadRowsWithoutAbortingGoodOnes(@TempDir Path dir) throws IOException {
    Path csv = dir.resolve("bank.csv");
    Files.writeString(csv, """
        date,merchant,amount
        2026-01-03,COFFEE BAR,-4.50
        2026-01-04,,-10.00
        not-a-date,RENT,-900.00
        """);

    ImportResult result = new CsvImporter().importFile(csv);

    assertThat(result.imported()).hasSize(1);
    assertThat(result.errors())
        .extracting(RowError::lineNumber, RowError::message)
        .containsExactly(
            tuple(3, "merchant is blank"),
            tuple(4, "invalid date: not-a-date"));
}
```

---

## 7. Assumptions, tags, ordering

```java
@Test void onlyOnCi() { assumeTrue("true".equals(System.getenv("CI"))); /* skipped otherwise */ }
@Tag("slow") @Test void bigImport() { }
```

Test order is deterministic but **intentionally not obvious**. Never depend on it. `@TestMethodOrder(OrderAnnotation.class)` exists; needing it is a smell.

---

## 8. Break it

1. Make a test share a `static List` that each test adds to. Run tests individually (pass) and together (fail). Fix with an instance field.
2. Reverse `assertEquals(actual, expected)` and read the misleading failure message.
3. Write a test with no assertion. It passes. Coverage goes up. What did it prove?
4. Replace `Clock.fixed` with `Instant.now()` in `HoldTest` and add a `Thread.sleep`. Run it 20 times (`-Dsurefire.rerunFailingTestsCount` hides flakiness — don't rely on it).
5. Delete `maven-surefire-plugin` version pinning on an old parent → tests silently not discovered. Check `Tests run: 0`.

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

To cover many inputs, especially boundaries, with one test body — e.g. amount-range rules at 9.99/10.00/50.00/50.01 in my Ledger project.
</details>

<details><summary>Why AssertJ over plain assertions?</summary>

Fluent, type-specific assertions (collections, optionals, exceptions, BigDecimal comparison, recursive comparison)
with much clearer failure messages.
</details>

---

## Mastery checklist

- [ ] Explain the JUnit 5 lifecycle and per-method instances.
- [ ] Write a parameterized boundary test with `@CsvSource` and `@MethodSource`.
- [ ] Use `@Nested` + `@DisplayName` to structure a state-based test class.
- [ ] Use `@TempDir` for file-import tests in P1.
- [ ] Use AssertJ `extracting`, `containsExactly`, `assertThatThrownBy`, `usingRecursiveComparison`.
- [ ] Run a single test method from Maven and from the IDE.
