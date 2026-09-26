# 09 — Testing

> **JUnit 5 in Week 2** (foundation katas) → **Mockito, Spring slices and Testcontainers in Weeks 4–5** (FlowGrid M1–M2:
> the N-threads-one-unit reservation test needs real Postgres) → **Vitest + React Testing Library in Week 7** (FlowGrid
> dashboard) → **failure injection and invariant tests in Week 12** (LedgerX M4) → **chaos-style integration tests with
> Testcontainers Postgres + Redis in Week 18** (ForgeCI M5). See [ROADMAP §6](../ROADMAP.md#6-just-in-time-learning-map).

Testing is where "I did a tutorial" and "I've built software" separate in interviews. Every project ships with tests,
and you should be able to open any test and explain **why it exists and what bug it would catch**.

---

## Files

| File | Topic | First needed |
|---|---|---:|
| [junit5.md](./junit5.md) | Annotations, lifecycle, assertions, `assertThrows`, parameterized & nested tests, AssertJ | W2 |
| [mockito.md](./mockito.md) | Mocks vs stubs vs spies, stubbing, verification, captors, strict stubs, over-mocking | W5 |
| [spring-testing.md](./spring-testing.md) | `@SpringBootTest` vs slices, MockMvc, `@MockitoBean`, security tests, profiles | W4–5 |
| [testcontainers.md](./testcontainers.md) | Real PostgreSQL (and Redis) in tests, `@ServiceConnection`, concurrency tests, reuse, CI | W5, W10, W18 |
| [frontend-testing.md](./frontend-testing.md) | Vitest, React Testing Library, user-event, MSW, hooks | W7 |

---

## 1. The test pyramid

```
            ▲  fewer, slower, more realistic, more brittle
           /E2E\          1–5   Playwright: login → create order → see it on the pick board
          /─────\
         / Integ \        tens  @SpringBootTest + Testcontainers; @DataJpaTest; RTL + MSW page tests
        /─────────\
       /   Unit    \      many  domain rules, state machines, allocation scoring, bucketing, reducers
      /─────────────\
            ▼  more, faster (ms), cheaper, pinpoint failures
```

| Level | Scope | Speed | In this roadmap |
|---|---|---|---|
| Unit | one class/function; collaborators faked or real-but-pure | ms | FlowGrid allocation scoring, order state machine; LedgerX money + journal balancing; ForgeCI `.forgeci.yml` parser, retry policy; FlagForge bucketing + rule evaluation |
| Slice / component | one layer with the framework | 100s ms – s | `@WebMvcTest` controllers, `@DataJpaTest` repositories, RTL component tests |
| Integration | several layers + real infrastructure | seconds | Testcontainers Postgres/Redis; concurrency tests; webhook dedupe; Redis-down degradation |
| End-to-end | whole deployed system | seconds–minutes | optional Playwright smoke test on the Compose stack; k6 is load, not correctness |

The **"testing trophy"** variant (popular on the frontend) puts most weight on integration/component tests. Both agree:
don't build an ice-cream cone (mostly manual/E2E, few unit tests).

---

## 2. What to test at each level

| Test this… | …at this level | Example |
|---|---|---|
| Business rules, calculations, state machines | Unit | FlowGrid: `PICKING → SHIPPED` is illegal; allocation tie-break by id; FlagForge: same user always lands in the same bucket |
| Input validation, status codes, JSON shape, error format | `@WebMvcTest` | `POST /api/skus` with blank code → 400 ProblemDetail |
| Authorization rules | `@WebMvcTest` + security, or `@SpringBootTest` | VIEWER → 403 on stock adjustment |
| Custom queries, constraints, mappings, migrations | `@DataJpaTest` + Testcontainers | unique `(idempotency_key)`; LedgerX `UPDATE ledger_entry` rejected by trigger; ForgeCI duplicate `X-GitHub-Delivery` rejected |
| Transactions, locking, concurrency | `@SpringBootTest` + Testcontainers | N threads reserve the last unit → exactly one wins; LedgerX $500 / $400 / $400 → exactly one transfer succeeds |
| Failure behaviour | integration + fault injection | crash between debit and credit → nothing half-written; Redis down → FlowGrid serves catalog from DB |
| Invariants | property-style / invariant suite | every LedgerX journal transaction sums to zero; balance = sum(entries) |
| UI behaviour | RTL + MSW | form shows server field error; empty state; role-hidden buttons |

**Don't test:** framework code (that `JpaRepository.save` saves), trivial getters, private methods directly,
third-party libraries, exact CSS.

---

## 3. Naming and structure

Pick one convention per project and stick to it:

```java
// methodUnderTest_condition_expectedResult
@Test void reserve_whenNoUnitsAvailable_throwsInsufficientStock() { }

// or behaviour sentences with @DisplayName
@Test @DisplayName("reserving more than available is rejected")
void rejectsOverReservation() { }
```

### Arrange–Act–Assert / Given–When–Then

```java
@Test
void transfer_whenInsufficientFunds_isRejectedAndNothingIsPosted() {
    // Arrange / Given
    var from = wallet("500.00");
    var to = wallet("0.00");
    // Act / When
    var thrown = catchThrowable(() -> ledger.transfer(from.id(), to.id(), new BigDecimal("600.00"), key()));
    // Assert / Then
    assertThat(thrown).isInstanceOf(InsufficientFundsException.class);
    assertThat(entriesFor(from.id())).isEmpty();
}
```

One behaviour per test. Multiple asserts are fine if they describe **one** outcome.
Inject `Clock` (Java) / use fake timers (JS) — never `Thread.sleep` to test time (reservation TTLs, idempotency-key
expiry, ForgeCI job timeouts and lease expiry).

---

## 4. Test doubles taxonomy (Meszaros / Fowler)

| Double | What it does | Example |
|---|---|---|
| **Dummy** | passed but never used | a placeholder `User` for a constructor |
| **Stub** | returns canned answers | `when(skus.findById(1L)).thenReturn(Optional.of(sku))` |
| **Spy** | real object that records calls | a hand-written `RecordingNotifier` capturing low-stock alerts |
| **Mock** | pre-programmed with expectations, verified | `verify(jobQueue).enqueue(any())` |
| **Fake** | working lightweight implementation | in-memory `DockerRunner` returning scripted exit codes for ForgeCI worker tests |

Mockito blurs the words: a Mockito "mock" is used both as stub and mock. Interviewers like the distinction:
*stubs are for queries (state verification), mocks are for commands (behaviour verification).*

Prefer **fakes** for your own ports (ForgeCI's container runner, FlagForge SDK's HTTP transport) and **real infrastructure**
(Testcontainers) for anything SQL or Redis. Mock at **boundaries you own**, not deep internals.

---

## 5. Failure injection and invariants (Week 12, LedgerX; Week 18, ForgeCI)

Two techniques beyond "happy path + a few errors":

- **Fault injection hooks.** A small interface in production code (e.g. `FaultInjector.maybeFail("after-debit")`) that is a
  no-op in production and throws in tests. It lets you prove "a crash between step A and step B leaves no partial state"
  and "retrying after the crash with the same idempotency key produces exactly one effect".
- **Invariant tests.** Instead of asserting one expected value, run many random operations (transfers, reversals, retries,
  concurrent threads) and then assert properties that must **always** hold: every journal transaction sums to zero; no
  balance is negative; stored balance == sum of entries; each GitHub delivery produced at most one build.

Chaos-style tests (Week 18) apply the same idea to infrastructure: stop the Redis container mid-run, kill a worker
between "dequeued" and "acknowledged", and assert the job is recovered via lease expiry — not lost, not run twice
(or run twice *safely*, if you designed for at-least-once).

---

## 6. Flaky tests

A flaky test passes and fails without code changes. It destroys trust in CI.

| Cause | Fix |
|---|---|
| Time (`now()`, time zones, midnight) | inject `Clock`; fake timers |
| Order dependence / shared mutable state | fresh fixtures per test; rollback or truncate; no static state |
| Async / concurrency without proper waiting | latches, Awaitility; RTL `findBy*`; never `sleep` |
| Randomness | seed it and log the seed; assert properties, not exact values |
| External services (real HTTP, GitHub, email) | stub at the boundary; WireMock/MSW |
| Port or resource collisions | random ports (`RANDOM_PORT`), Testcontainers mapped ports |
| Leftover containers/data from previous runs | containers per run; unique names; cleanup in `finally` |

Policy: a flaky test is a bug. Quarantine it (`@Disabled("flaky: #42")` or a tag) **with a ticket**, fix within the week.

---

## 7. Coverage: a guide, not a goal

```xml
<!-- pom.xml: JaCoCo report on mvn verify -->
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution><goals><goal>prepare-agent</goal></goals></execution>
    <execution><id>report</id><phase>verify</phase><goals><goal>report</goal></goals></execution>
  </executions>
</plugin>
```

- Use the report to find **untested important code** (the rollback branch of a transfer, the expiry path of a reservation).
- 100% line coverage doesn't mean correct: a test with no assertions covers lines too.
- Branch coverage is more informative than line coverage.
- Mutation testing (PIT) answers "would my tests notice if this line were wrong?" — awareness level.
- **Never** put a coverage percentage on your résumé as an achievement; talk about what the tests prove.

---

## 8. TDD-lite workflow (what you actually do)

Full TDD is optional; this loop is not:

1. **Write the test-name list** for the behaviour first (from the milestone's acceptance criteria).
2. For a bug: **write a failing test that reproduces it first.** Watch it fail for the right reason.
3. Implement the smallest change to pass.
4. Refactor with the test green.
5. Run the whole suite (`mvn verify` / `npx vitest run`) before pushing; CI runs it again.

Strict red-green-refactor pays off most for pure logic: allocation scoring, state machines, bucketing, rule evaluation,
the pipeline-config parser.

---

## 9. Maven test phases (Java)

| Plugin | Phase | Default includes |
|---|---|---|
| Surefire | `test` | `**/*Test.java`, `**/Test*.java`, `**/*Tests.java`, `**/*TestCase.java` |
| Failsafe | `integration-test`, `verify` | `**/*IT.java`, `**/IT*.java`, `**/*ITCase.java` |

Suffix Testcontainers tests with `IT` and add the Failsafe plugin so `mvn test` stays fast and `mvn verify`
(what CI runs) includes everything. See [01-java/08-maven-build.md](../01-java/08-maven-build.md).

---

## Interview Q&A

<details><summary>Explain the test pyramid.</summary>

Many fast, isolated unit tests at the base; fewer integration tests that exercise real infrastructure; very few
end-to-end tests at the top. Lower levels are cheaper and pinpoint failures; higher levels give realism. In FlowGrid I
unit-test the state machine and allocation, slice-test controllers and repositories, and prove reservation concurrency
with Testcontainers Postgres.
</details>

<details><summary>Mock vs stub vs fake?</summary>

A stub returns canned data so the code under test can proceed — I assert on the result. A mock is verified for
interactions — I assert that a call happened. A fake is a working lightweight implementation, like an in-memory container
runner for ForgeCI's worker. I prefer fakes and real databases for persistence logic and mocks for outbound side effects.
</details>

<details><summary>How did you test that your system survives failures?</summary>

With fault-injection hooks that throw between steps, then asserting no partial state and that an idempotent retry
produces exactly one effect; and with invariant suites that run many random operations and check properties such as
"every journal transaction sums to zero". Describe only tests you actually wrote.
</details>

<details><summary>How do you deal with flaky tests?</summary>

Treat as bugs: find the nondeterminism (time, ordering, shared state, async waits, external calls), fix it with injected
clocks, isolated fixtures, proper waiting or stubs, and quarantine with a ticket meanwhile.
</details>

<details><summary>Is 100% coverage a good goal?</summary>

No. Coverage shows what's *not* tested; it doesn't prove behaviour is checked. I use it to find gaps in important
branches and focus on meaningful assertions.
</details>

---

## Mastery checklist

- [ ] Draw the pyramid and place 3 real tests from my projects on it.
- [ ] Name the five test doubles with an example of each from my code.
- [ ] Each project follows one naming convention and AAA.
- [ ] `mvn verify` runs unit (Surefire) + `*IT` (Failsafe) tests; JaCoCo report generated.
- [ ] (W12) I've written one fault-injection test and one invariant test, and can explain what they prove.
- [ ] I've fixed at least one flaky test and can explain the root cause.
- [ ] I've written a failing test before fixing a bug at least 3 times.
