# 09 — Testing

> **JUnit 5 starts in Week 2** (P1 Ledger is tested from day one), **Mockito, Spring slices and Testcontainers in Week 12**
> (P2 TicketHold M4), **Vitest + React Testing Library in Week 17** (P3 TeamBoard M4). ≈ 17 dedicated hours
> plus continuous use in every project ([ROADMAP §3](../ROADMAP.md#3-priority-weighting)).

Testing is where "I did a tutorial" and "I've built software" separate in interviews. Every project in this
roadmap ships with tests, and you should be able to open any test and explain **why it exists and what bug it would catch**.

---

## Files

| File | Topic | Week |
|---|---|---:|
| [junit5.md](./junit5.md) | Annotations, lifecycle, assertions, `assertThrows`, parameterized & nested tests, AssertJ | 2, 12 |
| [mockito.md](./mockito.md) | Mocks vs stubs vs spies, stubbing, verification, captors, strict stubs, over-mocking | 12 |
| [spring-testing.md](./spring-testing.md) | `@SpringBootTest` vs slices, MockMvc, `@MockitoBean`, security tests, profiles | 12 |
| [testcontainers.md](./testcontainers.md) | Real PostgreSQL in tests, `@ServiceConnection`, reuse, CI | 12 |
| [frontend-testing.md](./frontend-testing.md) | Vitest, React Testing Library, user-event, MSW, hooks | 17 |

---

## 1. The test pyramid

```
            ▲  fewer, slower, more realistic, more brittle
           /E2E\          1–5   Playwright: login → create issue → see it on the board
          /─────\
         / Integ \        tens  @SpringBootTest + Testcontainers; @DataJpaTest; RTL + MSW page tests
        /─────────\
       /   Unit    \      many  pure domain logic, services with mocked ports, reducers, validators
      /─────────────\
            ▼  more, faster (ms), cheaper, pinpoint failures
```

| Level | Scope | Speed | In this roadmap |
|---|---|---|---|
| Unit | one class/function, collaborators faked or real-but-pure | ms | Ledger `Money`, rules engine; TicketHold `HoldService` with mocked repo; TeamBoard `atLeast()` |
| Slice / component | one layer with the framework | 100s ms – s | `@WebMvcTest` controllers, `@DataJpaTest` repositories, RTL component tests |
| Integration | several layers + real infrastructure | seconds | `@SpringBootTest` + Testcontainers Postgres; concurrent seat-hold test |
| End-to-end | whole deployed system | seconds–minutes | optional Playwright smoke test on the Compose stack |

The **"testing trophy"** variant (popular on the frontend) puts most weight on integration/component tests. Both
agree: don't build an ice-cream cone (mostly manual/E2E, few unit tests).

---

## 2. What to test at each level

| Test this… | …at this level | Example |
|---|---|---|
| Business rules, calculations, state machines | Unit | `IssueStatus` transitions; hold expiry; budget alerts |
| Input validation, status codes, JSON shape, error format | `@WebMvcTest` | `POST /api/events` with blank name → 400 ProblemDetail |
| Authorization rules | `@WebMvcTest` + security, or `@SpringBootTest` | VIEWER → 403 on create issue |
| Custom queries, constraints, mappings, migrations | `@DataJpaTest` + Testcontainers | unique constraint on `(event_id, seat_id)`; Flyway runs clean |
| Transactions, locking, concurrency | `@SpringBootTest` + Testcontainers | N threads hold same seat → exactly one wins |
| UI behaviour | RTL + MSW | form shows server field error; empty state |
| Critical user journey | E2E | login → create → move issue |

**Don't test:** framework code (that `JpaRepository.save` saves), trivial getters, private methods directly,
third-party libraries, exact CSS.

---

## 3. Naming and structure

Pick one convention per project and stick to it:

```java
// methodUnderTest_condition_expectedResult
@Test void hold_whenSeatAlreadyHeld_throwsSeatUnavailable() { }

// or behaviour sentences with @DisplayName
@Test @DisplayName("holding an already-held seat is rejected")
void rejectsDoubleHold() { }
```

### Arrange–Act–Assert / Given–When–Then

```java
@Test
void confirm_whenHoldExpired_throws() {
    // Arrange / Given
    var clock = Clock.fixed(Instant.parse("2026-05-01T10:10:00Z"), ZoneOffset.UTC);
    var hold = Hold.create(seatId, userId, Instant.parse("2026-05-01T10:00:00Z"), Duration.ofMinutes(5));
    // Act / When + Assert / Then
    assertThatThrownBy(() -> hold.confirm(clock))
        .isInstanceOf(HoldExpiredException.class);
}
```

One behaviour per test. Multiple asserts are fine if they describe **one** outcome.
Inject `Clock` (Java) / use fake timers (JS) — never `Thread.sleep` to test time.

---

## 4. Test doubles taxonomy (Meszaros / Fowler)

| Double | What it does | Example |
|---|---|---|
| **Dummy** | passed but never used | a `null`-safe placeholder `User` for a constructor |
| **Stub** | returns canned answers | `when(repo.findById(1L)).thenReturn(Optional.of(event))` |
| **Spy** | real object that records calls | `spy(new ArrayList<>())`; a hand-written `RecordingNotifier` |
| **Mock** | pre-programmed with expectations, verified | `verify(emailSender).send(any())` |
| **Fake** | working lightweight implementation | `InMemoryTransactionRepository` in P1 Ledger |

Mockito blurs the words: a Mockito "mock" is used both as stub and mock. Interviewers like the distinction:
*stubs are for queries (state verification), mocks are for commands (behaviour verification).*

Prefer **fakes** for your own repository interfaces (P1 Ledger's in-memory repo) and **real infrastructure**
(Testcontainers) for anything SQL. Mock at **boundaries you own** (ports), not deep internals.

---

## 5. Flaky tests

A flaky test passes and fails without code changes. It destroys trust in CI.

| Cause | Fix |
|---|---|
| Time (`now()`, time zones, midnight) | inject `Clock`; fake timers |
| Order dependence / shared mutable state | fresh fixtures per test; `@Transactional` rollback or truncate; no static state |
| Async / concurrency without proper waiting | `CountDownLatch`, `Awaitility`; RTL `findBy*`, never `sleep` |
| Randomness | seed it; assert properties, not exact values |
| External services (real HTTP, real email) | stub at the boundary; WireMock/MSW |
| Port or resource collisions | random ports (`RANDOM_PORT`), Testcontainers mapped ports |
| DB state from previous runs | containers per run; Flyway `clean` in tests only |

Policy: a flaky test is a bug. Quarantine it (`@Disabled("flaky: issue #12")`/`@Tag`) **with a ticket**, fix within the week.

---

## 6. Coverage: a guide, not a goal

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

- Use the report to find **untested important code** (the `catch` branch of booking confirmation, the expiry path).
- 100% line coverage doesn't mean correct: a test with no assertions covers lines too.
- Branch coverage is more informative than line coverage.
- A reasonable bar for your projects: domain + service packages well covered; don't chase DTOs/config.
- Mutation testing (PIT) answers "would my tests notice if this line were wrong?" — awareness level.

---

## 7. TDD-lite workflow (what you actually do)

Full TDD is optional; this loop is not:

1. **Write the test name list** for the behaviour (5 lines of `// TODO` test names).
2. For a bug: **write a failing test that reproduces it first.** Watch it fail for the right reason.
3. Implement the smallest change to pass.
4. Refactor with the test green.
5. Run the whole suite (`mvn verify` / `npm test -- --run`) before pushing; CI runs it again.

Use strict red-green-refactor for pure logic (Ledger rules engine, TicketHold hold expiry, TeamBoard status transitions),
where it's fastest. For UI and glue code, test right after writing.

---

## 8. Maven test phases (Java)

| Plugin | Phase | Default includes |
|---|---|---|
| Surefire | `test` | `**/*Test.java`, `**/Test*.java`, `**/*Tests.java`, `**/*TestCase.java` |
| Failsafe | `integration-test`, `verify` | `**/*IT.java`, `**/IT*.java`, `**/*ITCase.java` |

Suffix Testcontainers tests with `IT` and add the failsafe plugin so `mvn test` stays fast and `mvn verify`
(what CI runs) includes everything. See [01-java/08-maven-build.md](../01-java/08-maven-build.md).

---

## Interview Q&A

<details><summary>Explain the test pyramid.</summary>

Many fast, isolated unit tests at the base; fewer integration tests that exercise real infrastructure; very few
end-to-end tests at the top. Lower levels are cheaper and pinpoint failures; higher levels give realism. In TicketHold
I have unit tests for hold rules, `@WebMvcTest` for API contracts, and Testcontainers tests for locking and the DB.
</details>

<details><summary>Mock vs stub vs fake?</summary>

A stub returns canned data so the code under test can proceed — I assert on the result. A mock is verified for
interactions — I assert that a call happened. A fake is a working lightweight implementation, like an in-memory
repository. I prefer fakes and real databases for persistence logic and mocks for outbound side effects like email.
</details>

<details><summary>How do you deal with flaky tests?</summary>

Treat as bugs: find the nondeterminism (time, ordering, shared state, async waits, external calls), fix it with
injected clocks, isolated fixtures, proper waiting, or stubs, and quarantine with a ticket meanwhile.
</details>

<details><summary>Is 100% coverage a good goal?</summary>

No. Coverage shows what's *not* tested; it doesn't prove behaviour is checked. I use it to find gaps in important
branches and focus on meaningful assertions, not a number.
</details>

<details><summary>How do you test code that depends on the current time?</summary>

Inject `java.time.Clock` and use `Clock.fixed` in tests; on the frontend, Vitest fake timers.
</details>

---

## Mastery checklist

- [ ] Draw the pyramid and place 3 real tests from my projects on it.
- [ ] Name the five test doubles with an example of each from my code.
- [ ] Every test in TicketHold follows one naming convention and AAA.
- [ ] `mvn verify` runs unit (Surefire) + `*IT` (Failsafe) tests; JaCoCo report generated.
- [ ] I've fixed at least one flaky test and can explain the root cause.
- [ ] I've written a failing test before fixing a bug at least 3 times.
