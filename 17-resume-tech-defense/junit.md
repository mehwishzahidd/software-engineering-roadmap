# JUnit 5 — Résumé Defense

**Target level:** L3 · **Learned:** Week 2 (basics), Week 5 (Mockito, Spring slices, Testcontainers for FlowGrid's concurrency test), Week 12 (failure injection + invariant suites in LedgerX), Week 18 (chaos-style integration tests with Postgres + Redis in ForgeCI) · **Version:** JUnit 5 (Jupiter, 5.10+), AssertJ, Mockito 5
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md)

---

## 1. Beginner questions

<details><summary><b>Q1. What are the parts of JUnit 5?</b></summary>

JUnit Platform (launcher that build tools/IDEs use), JUnit Jupiter (the JUnit 5 programming model
and engine: `@Test`, extensions), JUnit Vintage (runs JUnit 3/4 tests on the platform).
</details>

<details><summary><b>Q2. Lifecycle annotations?</b></summary>

`@BeforeEach`/`@AfterEach` around every test; `@BeforeAll`/`@AfterAll` once per class (must be
`static` unless `@TestInstance(PER_CLASS)`). A new test-class instance is created per test method by default.
</details>

<details><summary><b>Q3. How do you test that an exception is thrown?</b></summary>

```java
var ex = assertThrows(InsufficientStockException.class, () -> reservations.reserve(skuId, warehouseId, 5));
assertEquals(3, ex.available());
```
Or AssertJ `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)`.
</details>

<details><summary><b>Q4. What makes a good unit test?</b></summary>

Fast, isolated, deterministic, one behaviour per test, clear name (`rejectsReservationBeyondAvailable`),
Arrange-Act-Assert structure, asserts on behaviour not implementation details.
</details>

<details><summary><b>Q5. JUnit 4 vs JUnit 5 differences you notice day to day?</b></summary>

Package `org.junit.jupiter.api`; `@BeforeEach` instead of `@Before`; `@Disabled` instead of
`@Ignore`; `assertThrows` instead of `@Test(expected=...)`; extensions (`@ExtendWith`) instead of
runners/rules; test classes and methods can be package-private.
</details>

## 2. Intermediate questions

<details><summary><b>Q6. How do parameterized tests work?</b></summary>

```java
@ParameterizedTest
@CsvSource({"RESERVED, PICKING, true", "SHIPPED, RESERVED, false", "PACKED, SHIPPED, true"})
void transitionAllowed(OrderState from, OrderState to, boolean allowed) { ... }
```
Sources: `@ValueSource`, `@CsvSource`, `@CsvFileSource`, `@MethodSource`, `@EnumSource`. Requires `junit-jupiter-params` (included in `junit-jupiter`).
</details>

<details><summary><b>Q7. Mock vs stub vs fake vs spy?</b></summary>

Stub returns canned answers. Mock also verifies interactions. Fake is a working lightweight
implementation (in-memory repository). Spy wraps a real object, partially stubbed. Prefer fakes
for your own interfaces (FlowGrid's `InMemoryInventoryRepository` for allocation-scoring tests); mocks for boundaries (GitHub API client, Docker client, email).
</details>

<details><summary><b>Q8. How do you use Mockito with JUnit 5?</b></summary>

```java
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
  @Mock InventoryLevelRepository levels;
  @InjectMocks ReservationService service;
  @Test void reservesWhenAvailable() {
    when(levels.findBySkuAndWarehouse(1L, 7L)).thenReturn(Optional.of(levelWithAvailable(3)));
    service.reserve(1L, 7L, 2);
    verify(levels).save(argThat(l -> l.reserved() == 2 && l.available() == 1));
  }
}
```
Strict stubs: unused stubbings fail the test (`UnnecessaryStubbingException`).
</details>

<details><summary><b>Q9. How do you test time-dependent code?</b></summary>

Inject `java.time.Clock`; in tests use `Clock.fixed(...)` or a mutable test clock. Never
`Thread.sleep` to wait for expiry (slow + flaky).
</details>

<details><summary><b>Q10. What are <code>@Nested</code>, <code>@DisplayName</code>, <code>@Tag</code>, <code>@TempDir</code>?</b></summary>

`@Nested` groups tests by scenario with shared setup; `@DisplayName` readable names; `@Tag("slow")`
to include/exclude in builds; `@TempDir Path dir` gives a temp directory cleaned up after the test (great for ForgeCI's workspace-cleanup tests).
</details>

<details><summary><b>Q11. Explain the test pyramid for a Spring Boot service.</b></summary>

Many unit tests (plain JUnit + Mockito, ms each); fewer slice tests (`@WebMvcTest`, `@DataJpaTest`);
few full `@SpringBootTest` + Testcontainers; very few E2E. Cost and flakiness rise going up.
</details>

<details><summary><b>Q12. <code>@Mock</code> vs <code>@MockBean</code>/<code>@MockitoBean</code>?</b></summary>

`@Mock` (Mockito) creates a mock with no Spring context. `@MockBean` (Spring Boot, deprecated in
3.4 in favour of Spring Framework's `@MockitoBean`) replaces a bean in the Spring context — used in slice tests, and each
different combination forces a new context (slower).
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "How did you approach testing in your previous roles?"</b></summary>

- Truthful: what existed (e.g. "the team had some JUnit 4 tests; I added tests when fixing bugs") — don't claim TDD culture if there wasn't one.
- Now: "In my recent projects tests are part of every milestone — FlowGrid has unit, slice, Testcontainers
  and a concurrency test running in CI since Week 4; LedgerX has an invariant suite (every journal transaction
  sums to zero, balance == sum of entries) plus crash-between-steps tests; ForgeCI has chaos-style integration
  tests against Postgres + Redis in Testcontainers."
</details>

<details><summary><b>R2. "How would you test this method?" (they paste a service method)</b></summary>

- List behaviours: happy path, each validation branch, boundary values, exception paths, interactions.
- Choose doubles: fake repository or Mockito mock for collaborators; fixed `Clock`.
- Name 3–5 tests aloud with names, then write one.
</details>

<details><summary><b>R3. "How do you test code that talks to a database?"</b></summary>

- Real database via Testcontainers (PostgreSQL 16) — H2 hides dialect differences (e.g. `ON CONFLICT`, JSONB).
- `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@ServiceConnection` (Boot 3.1+).
- Flyway migrations run in the test so schema matches production.
</details>

<details><summary><b>R4. "How did you prove there's no double-reservation / no negative balance?"</b></summary>

- FlowGrid M2: `ExecutorService` with N threads, `CountDownLatch` to release them simultaneously, all
  reserve the last unit of one SKU; assert exactly one success and N-1 `409 Conflict`; repeat in a loop.
- LedgerX M2: account with $500, two concurrent $400 transfers → exactly one succeeds, balance never
  negative, and the invariant suite (sum of entries == balance) still passes afterwards.
- Mention its limits: probabilistic, so also rely on `SELECT … FOR UPDATE` / `CHECK (balance >= 0)` for correctness.
</details>

<details><summary><b>R5. "What's code coverage good for? What's a good number?"</b></summary>

- Finds untested code; not proof of quality (you can hit 100% with no assertions). JaCoCo for reports.
- "I aim for meaningful coverage of domain logic and error paths rather than a fixed number; I review uncovered branches."
</details>

## 4. Practical tasks (live)

- [ ] Write 5 tests for a `Money` value object including `equals` with different scales.
- [ ] Parameterize a state-machine transition test (order / reservation / job states) with `@CsvSource`.
- [ ] Use `@TempDir` to test ForgeCI's workspace creation + cleanup end to end.
- [ ] Mock a repository with Mockito; verify `save` called once with `ArgumentCaptor`.
- [ ] Write a `@WebMvcTest` asserting a 400 ProblemDetail for invalid input.
- [ ] Run one test class and one method from the command line with Maven.

## 5. Debugging questions

<details><summary><b>D1. A test passes alone but fails when the whole suite runs.</b></summary>

Shared mutable state (static fields, singletons, DB rows not cleaned up, shared Spring context
mutated). Test order dependence. Fix by isolating state: fresh fixtures per test, `@Transactional`
rollback in Spring tests, or truncating tables; don't add `@Order` to hide it.
</details>

<details><summary><b>D2. A test is flaky in CI (~1 in 10 fails).</b></summary>

Usual causes: time (`now()`), thread timing/`sleep`, random data, unordered collections asserted as ordered,
port collisions, container not ready. Reproduce with `@RepeatedTest(100)`; inject Clock; use Awaitility for async; sort before asserting.
</details>

<details><summary><b>D3. Mockito: <code>NullPointerException</code> inside the class under test.</b></summary>

Mocks weren't initialised (missing `@ExtendWith(MockitoExtension.class)`), `@InjectMocks` couldn't
inject (constructor mismatch), or an unstubbed method returned `null`/default. Check stubbing args match (`eq` vs raw).
</details>

<details><summary><b>D4. Spring tests take 4 minutes.</b></summary>

Too many distinct contexts (different `@MockBean` sets/properties prevent context caching), too many
`@SpringBootTest` where slices or unit tests suffice, containers started per class instead of reused (singleton container pattern).
</details>

## 6. Architecture questions

- How does designing for testability (constructor injection, interfaces at boundaries, injected `Clock`) change your class design?
- Where do you draw the line between unit and integration test for FlowGrid's role rules (ADMIN / OPS_MANAGER / WAREHOUSE_ASSOCIATE / VIEWER)?
- How do you test a crash *between* two steps (LedgerX M4 fault-injection hook) deterministically, and what does the retry-after-crash test assert?
- How would you structure tests so CI stays under 5 minutes as the suite grows (tags, Failsafe split, parallel execution)?
- Contract tests between the React client and the API — worth it for a 1-person project?

## 7. Common mistakes

- Testing implementation (verifying every internal call) instead of behaviour.
- Multiple unrelated behaviours in one test; vague names like `test1`.
- `Thread.sleep` for async code.
- Mocking value objects or the class under test.
- Using H2 and believing Postgres behaviour is tested.
- No assertions (test "passes" because nothing threw).

## 8. Terminology I must know

| Term | One-line meaning |
|---|---|
| Jupiter | JUnit 5 programming model + engine |
| Platform | Test launcher API used by Maven/IDEs |
| Extension | JUnit 5 hook mechanism (`@ExtendWith`) |
| Assertion | Check that fails the test if false |
| Test double | Any stand-in: dummy, stub, fake, mock, spy |
| Fixture | Known state set up before a test |
| AAA | Arrange-Act-Assert structure |
| Slice test | Spring test loading only one layer |
| Testcontainers | Library starting real dependencies in Docker for tests |
| Flaky test | Non-deterministic pass/fail |
| Regression test | Test added to lock in a bug fix |
| Coverage | Share of code executed by tests |

## 9. When to use it

All Java code with logic worth protecting: domain rules, parsers, services, repositories (with
real DB), controllers (slice tests). Write the failing test first when fixing a bug.

## 10. When NOT to use it

Pure glue/getters with no logic; UI behaviour of the React app (use Vitest + RTL); load testing
(k6); exploratory checks better done with curl/Postman then turned into tests if valuable.

## 11. Trade-offs

| Choice | Gain | Cost |
|---|---|---|
| Mocks | Fast, isolated | Brittle to refactors; can test fiction |
| Fakes | Realistic, refactor-friendly | You maintain them |
| Testcontainers | Real DB behaviour | Needs Docker, slower startup |
| High coverage target | Fewer blind spots | Can incentivise low-value tests |

## 12. How it interacts with the rest of my stack

- **Maven**: Surefire (unit) / Failsafe (`*IT`) run tests; `mvn verify` in CI.
- **Spring Boot**: `spring-boot-starter-test` bundles JUnit 5, AssertJ, Mockito, MockMvc.
- **PostgreSQL/Docker**: Testcontainers runs `postgres:16` in Docker during tests.
- **CI**: GitHub Actions runs the suite on every push/PR; red build blocks merge.
- **React**: frontend has its own Vitest/RTL suite — same principles, different runner.

## 13. Hands-on exercise

**Test LedgerX's reconciliation job (against an in-memory fake repository).**

Acceptance criteria:
- [ ] ≥ 8 tests covering: all transactions balanced → no findings; one unbalanced transaction flagged with its id; materialized balance drift flagged; a reversal nets the original to zero; empty ledger; scale differences (`2.0` vs `2.00`) not reported as drift.
- [ ] At least one `@ParameterizedTest` and one `@Nested` group.
- [ ] Time via injected `Clock`; no `LocalDate.now()` in the class under test.
- [ ] All tests run in < 1 s with `mvn test`; zero flakiness over `@RepeatedTest(50)` on one case.
- [ ] One bug found → regression test added first, then fix.

## 14. Mastery checklist

- [ ] Write JUnit 5 tests from memory (lifecycle, assertions, exceptions, parameterized).
- [ ] Use Mockito correctly (`when/thenReturn`, `verify`, captors, strict stubs).
- [ ] Explain and apply the test pyramid to a Spring app.
- [ ] Write `@WebMvcTest` and `@DataJpaTest` + Testcontainers tests.
- [ ] Write a deterministic concurrency test.
- [ ] Diagnose and fix a flaky test.
- [ ] Explain mocks vs fakes and when I choose each.

## Evidence in my projects

| Project | What it demonstrates | Fill in: file / commit |
|---|---|---|
| FlowGrid | Mockito unit tests (allocation scoring, state transitions), `@WebMvcTest` (ProblemDetail, 401/403 by role), `@DataJpaTest` + Testcontainers, N-threads-one-unit concurrency test, Idempotency-Key replay test, CI from W4 | |
| LedgerX | **Invariant suite** (every journal txn sums to zero; balance == sum(entries); `ledger_entry` immutable — UPDATE/DELETE rejected by trigger), $500/$400/$400 concurrency test, idempotency conflict test (same key, different body → 422), crash-between-steps + retry-after-crash tests via a fault-injection hook | |
| ForgeCI | Webhook signature + duplicate-delivery tests, queue lease / orphan-recovery tests, retry-policy tests (app vs infra failure), Testcontainers Postgres + Redis integration tests, timeout/cancel tests with an injected `Clock` | |
| FlagForge | Evaluation-engine tests (priority, percentage bucketing determinism), SDK unit tests (stale-if-error, offline mode, defaults) + contract tests against the server | |

## Where to learn it in this repo

[`../09-testing/README.md`](../09-testing/README.md) · [`../09-testing/junit5.md`](../09-testing/junit5.md) ·
[`../09-testing/mockito.md`](../09-testing/mockito.md) · [`../09-testing/spring-testing.md`](../09-testing/spring-testing.md) ·
[`../09-testing/testcontainers.md`](../09-testing/testcontainers.md) · [`../01-java/08-maven-build.md`](../01-java/08-maven-build.md)
