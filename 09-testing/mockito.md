# Mockito

> **Week 5** (FlowGrid M2), then every project. Mockito lets you replace collaborators so a unit test exercises
> **one class**. Used well, it makes service tests fast and focused. Used badly, it produces tests that pass while
> production burns — especially for the concurrency and transaction behaviour that FlowGrid, LedgerX and ForgeCI are about.

---

## 1. Setup

Included in `spring-boot-starter-test` (`mockito-core` + `mockito-junit-jupiter`). Mockito 5 uses the **inline mock maker**
by default, so it can mock `final` classes and methods.

> On JDK 21+, Mockito's self-attaching agent prints a warning about dynamic agent loading (future JDKs will disallow it
> by default). The fix recommended in Mockito's docs is to add Mockito as a `-javaagent` in the Surefire `argLine`.
> Know why the warning appears; fix it when it bothers you.

---

## 2. Mock vs stub vs spy (in Mockito terms)

| Term | Mockito | Use for |
|---|---|---|
| **Stub** | `when(skus.findById(1L)).thenReturn(Optional.of(sku))` | feeding data to the class under test (queries) |
| **Mock** (verified) | `verify(events).publish(any(StockReserved.class))` | asserting an outbound side effect happened (commands) |
| **Spy** | `spy(realObject)` — real methods run unless stubbed | partial mocking of legacy code; rarely needed |

Rule of thumb: **stub queries, verify commands.** Don't verify a stubbed query was called — the assertion on the result
already proves it.

---

## 3. The class under test (a simplified FlowGrid service)

A deliberately small version — your real reservation logic has more rules. What matters is the **shape**: constructor
injection, collaborators behind interfaces, time from a `Clock`.

```java
public class ReservationService {
    private final InventoryRepository inventory;
    private final ReservationRepository reservations;
    private final DomainEvents events;
    private final Clock clock;

    public ReservationService(InventoryRepository inventory, ReservationRepository reservations,
                              DomainEvents events, Clock clock) { /* assign fields */ }

    @Transactional
    public Reservation reserve(long orderLineId, long skuId, long warehouseId, int qty) {
        InventoryLevel level = inventory.findForUpdate(skuId, warehouseId)          // SELECT … FOR UPDATE
            .orElseThrow(() -> new NotFoundException("inventory", skuId));
        level.reserve(qty);                                                          // throws InsufficientStockException
        Reservation r = reservations.save(Reservation.create(orderLineId, level.id(), qty, clock.instant(), Duration.ofMinutes(15)));
        events.publish(new StockReserved(r.getId(), skuId, warehouseId, qty));
        return r;
    }
}
```

---

## 4. A unit test with Mockito

```java
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock InventoryRepository inventory;
    @Mock ReservationRepository reservations;
    @Mock DomainEvents events;
    @Captor ArgumentCaptor<StockReserved> eventCaptor;

    final Instant now = Instant.parse("2026-05-01T10:00:00Z");
    ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(inventory, reservations, events, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void reserve_whenStockAvailable_savesReservationAndPublishesEvent() {
        when(inventory.findForUpdate(7L, 1L)).thenReturn(Optional.of(TestData.level(7L, 1L, /*onHand*/ 10, /*reserved*/ 0)));
        when(reservations.save(any(Reservation.class))).thenAnswer(inv -> TestData.withId(inv.getArgument(0), 100L));

        Reservation r = service.reserve(55L, 7L, 1L, 3);

        assertThat(r.getId()).isEqualTo(100L);
        assertThat(r.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        verify(events).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new StockReserved(100L, 7L, 1L, 3));
    }

    @Test
    void reserve_whenInsufficientStock_throwsAndSavesNothing() {
        when(inventory.findForUpdate(7L, 1L)).thenReturn(Optional.of(TestData.level(7L, 1L, 2, 0)));

        assertThatThrownBy(() -> service.reserve(55L, 7L, 1L, 3)).isInstanceOf(InsufficientStockException.class);

        verify(reservations, never()).save(any());
        verifyNoInteractions(events);
    }
}
```

What this test **does not** prove: that two concurrent requests can't both reserve the last unit. The lock lives in
Postgres; the mock repository has no lock. That's the Testcontainers test in [testcontainers.md §6](./testcontainers.md#6-concurrency-tests-the-core-evidence).

---

## 5. Stubbing reference

```java
when(skus.findById(1L)).thenReturn(Optional.of(sku));               // return value
when(skus.findById(anyLong())).thenReturn(Optional.empty());        // matcher
when(github.fetchFile(any(), any())).thenThrow(new GitHubUnavailableException());
when(ids.next()).thenReturn(1L, 2L, 3L);                            // consecutive calls
when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));       // echo argument

// void methods: doX().when(mock).method()
doThrow(new DockerException("image pull failed")).when(runner).pull(any());
doNothing().when(notifier).lowStock(any());                          // default anyway

// BDD style (reads as Given/When/Then)
given(skus.findById(7L)).willReturn(Optional.of(sku));
then(events).should().publish(any(StockReserved.class));
```

### Argument matchers

`any()`, `any(Class)`, `anyLong()`, `eq(x)`, `argThat(p -> ...)`, `isNull()`, `startsWith("...")`.
**If one argument uses a matcher, all must**:

```java
when(inventory.findForUpdate(7L, anyLong()))          // ❌ InvalidUseOfMatchersException
when(inventory.findForUpdate(eq(7L), anyLong()))      // ✅
```

---

## 6. Verification reference

```java
verify(events).publish(any());                   // exactly once (times(1))
verify(events, times(2)).publish(any());
verify(events, never()).publish(any());
verify(events, atLeastOnce()).publish(any());
verifyNoInteractions(notifier);                  // mock never touched
verifyNoMoreInteractions(events);                // use sparingly: makes tests brittle

InOrder inOrder = inOrder(reservations, events); // order matters (save before publish)
inOrder.verify(reservations).save(any());
inOrder.verify(events).publish(any());
```

---

## 7. `ArgumentCaptor`

Use a captor when the argument is **built inside** the method under test and you need to inspect it.

```java
@Captor ArgumentCaptor<QueuedJob> jobCaptor;

@Test
void pushEvent_createsBuildAndEnqueuesOneJobPerPipelineJob() {        // ForgeCI M1/M2
    webhookService.handlePush(TestData.pushEvent("delivery-abc", "main", "3f2c1e0"));

    verify(jobQueue, times(2)).enqueue(jobCaptor.capture());
    assertThat(jobCaptor.getAllValues())
        .extracting(QueuedJob::jobName, QueuedJob::commitSha)
        .containsExactly(tuple("build", "3f2c1e0"), tuple("test", "3f2c1e0"));
}
```

If you only need a simple check, `verify(queue).enqueue(argThat(j -> j.commitSha().equals("3f2c1e0")))` is shorter.

---

## 8. Strict stubs

`MockitoExtension` uses **strict stubs** by default:

- An unused stubbing fails the test with `UnnecessaryStubbingException` → keeps tests honest (copy-pasted setup that doesn't matter).
- A stubbing called with **different arguments** than the code uses → `PotentialStubbingProblem`, pointing at the real bug.

```java
when(inventory.findForUpdate(7L, 1L)).thenReturn(Optional.of(level));
service.reserve(55L, 7L, 2L, 3);   // code calls findForUpdate(7L, 2L) → PotentialStubbingProblem
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

| Smell | Why it hurts | Instead |
|---|---|---|
| Mocking value objects (`Money`, `Reservation`, DTOs) | tests nothing real | use real instances |
| Mocking the repository to test a query or a lock | the SQL never runs | `@DataJpaTest` / `@SpringBootTest` + Testcontainers |
| `verify` on every call, in order | test mirrors implementation; any refactor breaks it | assert outcomes; verify only side effects |
| 8 `@Mock`s for one class | class has too many responsibilities | split the class |
| Mocking types you don't own (`RestClient`, `JdbcTemplate`, `StringRedisTemplate`, the Docker client) | you encode your assumptions about their behaviour | wrap them in your own port (`ContainerRunner`, `JobQueue`); mock or fake the port; integration-test the adapter |
| Mocks returning mocks | "train wreck"; Law of Demeter violation | redesign |

**The key question:** *"If I introduced a real bug here, would this test fail?"* A test that mocks the thing that
contains the bug can't catch it. FlowGrid's no-oversell guarantee and LedgerX's no-negative-balance guarantee are
database + transaction properties — only integration tests with real Postgres prove them.

---

## Break it

1. Stub `findForUpdate(7L, 1L)` but call the service with warehouse `2L`. Read the `PotentialStubbingProblem` message.
2. Add a stub that's never used. Read the `UnnecessaryStubbingException`.
3. Mix a raw value and a matcher. Read the `InvalidUseOfMatchersException`.
4. Introduce a bug: publish the event **before** saving (reservation id is null). Which test catches it? Add an `InOrder` check or assert on the captured id.
5. Remove `@ExtendWith(MockitoExtension.class)`. `@Mock` fields stay `null` → `NullPointerException` in `setUp`.
6. Remove `FOR UPDATE` from the real repository query. Does `ReservationServiceTest` notice? (No — which is the point of §10.)

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

When the SUT creates an object internally and passes it to a collaborator, and I need to assert its fields — e.g. the
jobs ForgeCI enqueues for a push event.
</details>

<details><summary>What are strict stubs?</summary>

Mockito's default with `MockitoExtension`: unused stubbings fail the test and stubbings called with mismatched args are
reported. It keeps tests minimal and catches argument bugs.
</details>

<details><summary>When should you NOT mock?</summary>

Value objects, the code under test, types I don't own, and anything whose correctness depends on real infrastructure —
SQL, constraints, transactions, row locks, Redis atomicity. Those get integration tests with Testcontainers.
</details>

<details><summary>What's the difference between <code>@Mock</code> and <code>@MockitoBean</code>?</summary>

`@Mock` creates a plain Mockito mock for a unit test with no Spring context. `@MockitoBean` (Spring Framework 6.2 / Boot 3.4+,
replacing Boot's `@MockBean`) replaces a bean inside a Spring test context, e.g. the service in a `@WebMvcTest`.
</details>

---

## Mastery checklist

- [ ] Write a service unit test from a blank file: stubs, `verify`, `never`, captor.
- [ ] Explain strict stubs and trigger both failure types on purpose.
- [ ] Use `doThrow` for a void method and `thenAnswer` to echo an argument.
- [ ] Identify one over-mocked test in my code and replace it with real objects or an integration test.
- [ ] Explain "stub queries, verify commands" with an example from my own project.
