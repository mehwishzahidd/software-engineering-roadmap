# Mockito

> **Week 12** · P2 TicketHold M4. Mockito lets you replace collaborators so a unit test exercises **one class**.
> Used well, it makes service tests fast and focused. Used badly, it produces tests that pass while production burns.

---

## 1. Setup

Included in `spring-boot-starter-test` (`mockito-core` + `mockito-junit-jupiter`). Mockito 5 uses the **inline mock maker**
by default, so it can mock `final` classes and methods.

> On JDK 21+, Mockito's self-attaching agent prints a warning about dynamic agent loading (future JDKs will
> disallow it by default). The fix recommended in Mockito's docs is to add Mockito as a `-javaagent` in the Surefire
> `argLine`. Know why the warning appears; fix it when it bothers you.

---

## 2. Mock vs stub vs spy (in Mockito terms)

| Term | Mockito | Use for |
|---|---|---|
| **Stub** | `when(repo.findById(1L)).thenReturn(Optional.of(seat))` | feeding data to the class under test (queries) |
| **Mock** (verified) | `verify(notifier).bookingConfirmed(booking)` | asserting an outbound side effect happened (commands) |
| **Spy** | `spy(realObject)` — real methods run unless stubbed | partial mocking of legacy code; rarely needed |

Rule of thumb: **stub queries, verify commands.** Don't verify a stubbed query was called — the assertion on the
result already proves it.

---

## 3. The service under test (TicketHold)

```java
@Service
@RequiredArgsConstructor // or an explicit constructor
public class HoldService {
    private final SeatRepository seats;
    private final HoldRepository holds;
    private final Clock clock;
    private final DomainEvents events;

    @Transactional
    public Hold hold(long seatId, long userId) {
        Seat seat = seats.findById(seatId).orElseThrow(() -> new NotFoundException("seat", seatId));
        if (holds.existsActiveHold(seatId, Instant.now(clock))) {
            throw new SeatUnavailableException(seatId);
        }
        Hold hold = holds.save(Hold.create(seat.getId(), userId, Instant.now(clock), Duration.ofMinutes(5)));
        events.publish(new SeatHeld(hold.getId(), seatId, userId));
        return hold;
    }
}
```

Constructor injection makes this trivially testable without Spring.

---

## 4. A complete Mockito unit test

```java
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldServiceTest {

    @Mock SeatRepository seats;
    @Mock HoldRepository holds;
    @Mock DomainEvents events;
    @Captor ArgumentCaptor<SeatHeld> eventCaptor;

    final Instant now = Instant.parse("2026-05-01T10:00:00Z");
    HoldService service;

    @BeforeEach
    void setUp() {
        service = new HoldService(seats, holds, Clock.fixed(now, ZoneOffset.UTC), events);
    }

    @Test
    void hold_whenSeatFree_savesHoldAndPublishesEvent() {
        // Arrange (stubs)
        when(seats.findById(7L)).thenReturn(Optional.of(seat(7L)));
        when(holds.existsActiveHold(7L, now)).thenReturn(false);
        when(holds.save(any(Hold.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));

        // Act
        Hold result = service.hold(7L, 42L);

        // Assert (state)
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(5)));

        // Assert (behaviour: command was issued)
        verify(events).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new SeatHeld(100L, 7L, 42L));
    }

    @Test
    void hold_whenSeatAlreadyHeld_throwsAndSavesNothing() {
        when(seats.findById(7L)).thenReturn(Optional.of(seat(7L)));
        when(holds.existsActiveHold(7L, now)).thenReturn(true);

        assertThatThrownBy(() -> service.hold(7L, 42L)).isInstanceOf(SeatUnavailableException.class);

        verify(holds, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void hold_whenSeatMissing_throwsNotFound() {
        when(seats.findById(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.hold(7L, 42L)).isInstanceOf(NotFoundException.class);
    }
}
```

`seat(...)` and `withId(...)` are small test helpers (test data builders) in the test class or a `TestData` util.

---

## 5. Stubbing reference

```java
when(repo.findById(1L)).thenReturn(Optional.of(e));            // return value
when(repo.findById(anyLong())).thenReturn(Optional.empty());   // matcher
when(gateway.charge(any())).thenThrow(new PaymentDeclinedException());
when(idGen.next()).thenReturn(1L, 2L, 3L);                     // consecutive calls
when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));  // echo argument

// void methods: doX().when(mock).method()
doThrow(new MailException("SMTP down")).when(mailer).send(any());
doNothing().when(mailer).send(any());                           // default anyway

// BDD style (reads as Given/When/Then)
given(seats.findById(7L)).willReturn(Optional.of(seat));
then(events).should().publish(any(SeatHeld.class));
```

### Argument matchers

`any()`, `any(Class)`, `anyLong()`, `eq(x)`, `argThat(p -> ...)`, `isNull()`, `startsWith("...")`.
**If one argument uses a matcher, all must**:

```java
when(holds.existsActiveHold(7L, any()))          // ❌ InvalidUseOfMatchersException
when(holds.existsActiveHold(eq(7L), any()))      // ✅
```

---

## 6. Verification reference

```java
verify(events).publish(any());                   // exactly once (times(1))
verify(events, times(2)).publish(any());
verify(events, never()).publish(any());
verify(events, atLeastOnce()).publish(any());
verifyNoInteractions(mailer);                     // mock never touched
verifyNoMoreInteractions(events);                 // use sparingly: makes tests brittle

InOrder inOrder = inOrder(holds, events);         // order matters (save before publish)
inOrder.verify(holds).save(any());
inOrder.verify(events).publish(any());
```

---

## 7. `ArgumentCaptor`

Use a captor when the argument is **built inside** the method under test and you need to inspect it.

```java
@Captor ArgumentCaptor<AlertJob> jobCaptor;

@Test
void incidentOpened_enqueuesWebhookAlertWithIdempotencyKey() {
    alertService.onIncidentOpened(incident(55L, monitor(9L)));

    verify(jobQueue).enqueue(jobCaptor.capture());
    AlertJob job = jobCaptor.getValue();
    assertThat(job.channel()).isEqualTo(Channel.WEBHOOK);
    assertThat(job.idempotencyKey()).isEqualTo("incident-55-opened");   // PulseWatch M2
}
```

If you only need a simple check, `verify(queue).enqueue(argThat(j -> j.channel() == Channel.WEBHOOK))` is shorter.
`captor.getAllValues()` for multiple calls.

---

## 8. Strict stubs

`MockitoExtension` uses **strict stubs** by default:

- An unused stubbing fails the test with `UnnecessaryStubbingException` → keeps tests honest (copy-pasted setup that doesn't matter).
- A stubbing called with **different arguments** than the code uses → `PotentialStubbingProblem`, pointing at the real bug.

```java
when(holds.existsActiveHold(7L, now)).thenReturn(false);
service.hold(8L, 42L);   // code calls existsActiveHold(8L, now) → PotentialStubbingProblem
```

Escape hatch for genuinely shared setup: `lenient().when(...)` or `@MockitoSettings(strictness = Strictness.LENIENT)` —
use rarely, and ask whether the setup belongs in the test that needs it.

---

## 9. Spies (and why you rarely need them)

```java
List<String> list = spy(new ArrayList<>());
list.add("a");
verify(list).add("a");
assertThat(list).hasSize(1);            // real method ran

doReturn(100).when(list).size();        // stub a spy with doReturn — when(list.size()) would call the real method first
```

If you need to spy on the class under test, it's usually doing too much — split it.

---

## 10. The over-mocking smell

Signs your tests mock too much:

| Smell | Why it hurts | Instead |
|---|---|---|
| Mocking value objects (`Money`, `Hold`, DTOs) | tests nothing real | use real instances |
| Mocking the repository to test a JPQL query | query never runs | `@DataJpaTest` + Testcontainers |
| `verify` on every call, in order | test mirrors implementation; any refactor breaks it | assert outcomes; verify only side effects |
| 8 `@Mock`s for one class | class has too many responsibilities | split the class |
| Mocking types you don't own (`RestTemplate`, `JdbcTemplate`, `HttpClient`) | you encode your assumptions about their behaviour | wrap them in your own port, mock the port; integration-test the adapter (WireMock/Testcontainers) |
| Mocks returning mocks | "train wreck"; Law of Demeter violation | redesign |

**The key question:** *"If I introduced a real bug here, would this test fail?"* A test that mocks the thing that
contains the bug can't catch it. TicketHold's double-booking protection is a DB + transaction property — only an
integration test with real Postgres ([testcontainers.md](./testcontainers.md)) proves it.

---

## Break it

1. Stub `holds.existsActiveHold(7L, now)` but call `service.hold(8L, ...)`. Read the `PotentialStubbingProblem` message.
2. Add a stub that's never used. Read the `UnnecessaryStubbingException`.
3. Mix a raw value and a matcher. Read the `InvalidUseOfMatchersException`.
4. Introduce a bug: publish the event **before** saving (hold id is null). Which test catches it? Add an `InOrder` check or an assertion on the captured event id.
5. Remove `@ExtendWith(MockitoExtension.class)`. `@Mock` fields stay `null` → `NullPointerException` in `setUp`.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `when(mock.voidMethod()).thenThrow(...)` | `doThrow(...).when(mock).voidMethod()` |
| `@InjectMocks` with ambiguous constructors, silently null fields | construct the SUT yourself in `@BeforeEach` |
| Verifying stubbed queries | assert on the result |
| `any()` everywhere | specific values where they matter — otherwise wrong-argument bugs pass |
| Mocking `Clock.instant()` | use `Clock.fixed(...)` |

---

## Interview Q&A

<details><summary>Mock vs stub vs spy in Mockito?</summary>

A stub is a mock configured to return values (`when…thenReturn`) — I assert on the SUT's output. A mock is verified for
interactions (`verify`). A spy wraps a real object so real methods run unless stubbed. I stub queries, verify commands,
and almost never use spies.
</details>

<details><summary>When would you use an <code>ArgumentCaptor</code>?</summary>

When the SUT creates an object internally and passes it to a collaborator, and I need to assert its fields — e.g.
the `AlertJob` with its idempotency key in PulseWatch.
</details>

<details><summary>What are strict stubs?</summary>

Mockito's default with `MockitoExtension`: unused stubbings fail the test and stubbings called with mismatched args
are reported. It keeps tests minimal and catches argument bugs.
</details>

<details><summary>When should you NOT mock?</summary>

Value objects, the code under test, types I don't own, and anything whose correctness depends on real infrastructure —
SQL queries, constraints, transactions, locking. Those get integration tests with Testcontainers.
</details>

<details><summary>What's the difference between <code>@Mock</code> and <code>@MockitoBean</code>?</summary>

`@Mock` creates a plain Mockito mock for a unit test with no Spring context. `@MockitoBean` (Spring Framework 6.2 / Boot 3.4+,
replacing Boot's `@MockBean`) replaces a bean inside a Spring test context, e.g. the service in a `@WebMvcTest`.
</details>

---

## Mastery checklist

- [ ] Write `HoldServiceTest` from a blank file: stubs, `verify`, `never`, captor.
- [ ] Explain strict stubs and trigger both failure types on purpose.
- [ ] Use `doThrow` for a void method and `thenAnswer` to echo an argument.
- [ ] Identify one over-mocked test in my code and rewrite it with real objects or an integration test.
- [ ] Explain "stub queries, verify commands" with an example.
