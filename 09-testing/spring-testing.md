# Spring Boot Testing

> **Week 12** · P2 TicketHold M4 (unit + `@WebMvcTest` + `@DataJpaTest` + Testcontainers), reused for
> P3 TeamBoard authorization tests (W17) and P4 PulseWatch. Server-side concepts: [05-spring-boot/](../05-spring-boot/README.md).

---

## 1. The menu: full context vs slices

| Annotation | Loads | Speed | Use for |
|---|---|---|---|
| none (plain JUnit + Mockito) | nothing | ms | services, domain logic |
| `@WebMvcTest(Controller.class)` | MVC infrastructure: controllers, `@ControllerAdvice`, filters, converters, Jackson, Spring Security auto-config, `MockMvc` — **no** `@Service`/`@Repository` beans | fast | HTTP contract: routing, validation, status codes, JSON, error format, security rules |
| `@DataJpaTest` | JPA: entities, repositories, `EntityManager`, Flyway/Liquibase, `DataSource`; **transactional + rollback** per test | medium | custom queries, mappings, constraints, migrations |
| `@JsonTest` | Jackson only | fast | tricky (de)serialization |
| `@RestClientTest` | `RestClient`/`RestTemplate` + mock server | fast | outbound HTTP clients (PulseWatch webhook sender) |
| `@SpringBootTest` | the **whole** application context | slow | end-to-end through all layers, transactions, security, scheduling |

Slices load **only** the relevant beans; anything else the slice needs must be provided as a mock bean or imported.

---

## 2. `@WebMvcTest` + MockMvc

```java
@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)                    // your SecurityFilterChain (so the real rules apply)
class EventControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean EventService eventService;      // replaces the bean in the slice context
    @MockitoBean JwtService jwtService;          // needed by your JWT filter if the filter is a bean

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void create_withValidBody_returns201AndLocation() throws Exception {
        var request = new CreateEventRequest("Jazz Night", 3L, Instant.parse("2026-06-01T19:00:00Z"));
        when(eventService.create(any())).thenReturn(new EventResponse(10L, "Jazz Night", 3L, request.startsAt()));

        mvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", endsWith("/api/events/10")))
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.name").value("Jazz Night"));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void create_withBlankName_returns400ProblemDetail() throws Exception {
        mvc.perform(post("/api/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "", "venueId": 3, "startsAt": "2026-06-01T19:00:00Z"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.errors.name").exists());

        verifyNoInteractions(eventService);      // validation stopped the request before the service
    }

    @Test
    void getEvent_notFound_returns404() throws Exception {
        when(eventService.get(99L)).thenThrow(new NotFoundException("event", 99L));
        mvc.perform(get("/api/events/99").with(user("alice").roles("CUSTOMER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }
}
```

Static imports: `MockMvcRequestBuilders.*`, `MockMvcResultMatchers.*`, `SecurityMockMvcRequestPostProcessors.*`, `Matchers.endsWith`.

Notes:
- `"$.title"` and `"$.errors.name"` match **your** `@RestControllerAdvice` output ([05-spring-boot/04-validation-errors.md](../05-spring-boot/04-validation-errors.md)); adjust.
- Debug a failing MockMvc test with `.andDo(print())`.
- Spring Framework 6.2 adds `MockMvcTester` (AssertJ-style MockMvc). Either API is fine; be consistent.

### What a `@WebMvcTest` proves
Routing, request binding, `@Valid` validation, exception → status mapping, JSON serialization, and security rules.
It does **not** prove the service logic or the database — those are other tests.

---

## 3. `@MockitoBean` vs `@MockBean`

| | `@MockitoBean` | `@MockBean` |
|---|---|---|
| From | Spring Framework 6.2 (`org.springframework.test.context.bean.override.mockito`) | Spring Boot (`org.springframework.boot.test.mock.mockito`) |
| Available | Spring Boot **3.4+** | Boot 1.4 → deprecated in 3.4, removed in Boot 4 |
| Companion | `@MockitoSpyBean` | `@SpyBean` |

Both replace a bean in the test's `ApplicationContext` with a Mockito mock that's reset after each test.
In older codebases (and older tutorials) you'll see `@MockBean` — same idea.

> **Context caching:** Spring caches application contexts across test classes with the **same configuration**.
> Every distinct combination of mock beans creates a **new** context → slow suites. Keep mock-bean sets consistent
> (e.g. a shared abstract base for controller tests), and avoid `@DirtiesContext` unless necessary.

---

## 4. Security tests

Dependency: `spring-security-test` (test scope).

```java
@WebMvcTest(IssueController.class)
@Import(SecurityConfig.class)
class IssueControllerSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean IssueService issueService;
    @MockitoBean JwtService jwtService;
    @MockitoBean(name = "authz") ProjectAuthz authz;   // bean used in @PreAuthorize("@authz.canWrite(#projectId, authentication)")

    @Test
    void anonymous_gets401() throws Exception {
        mvc.perform(get("/api/projects/1/issues")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "viewer@teamboard.dev")
    void viewer_cannotCreateIssue_gets403() throws Exception {
        when(authz.canWrite(eq(1L), any())).thenReturn(false);
        mvc.perform(post("/api/projects/1/issues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"status\":\"TODO\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(issueService);
    }

    @Test
    @WithMockUser(username = "member@teamboard.dev")
    void member_canCreateIssue() throws Exception {
        when(authz.canWrite(eq(1L), any())).thenReturn(true);
        when(issueService.create(eq(1L), any(), any())).thenReturn(sampleIssue());
        mvc.perform(post("/api/projects/1/issues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"status\":\"TODO\"}"))
            .andExpect(status().isCreated());
    }
}
```

Tools:
- `@WithMockUser(roles = "ORGANIZER")` → authority `ROLE_ORGANIZER`. `authorities = "issue:write"` for raw authorities.
- `.with(user("alice").roles("ADMIN"))` per request.
- `.with(jwt().authorities(...))` if you use Spring's OAuth2 resource server.
- `.with(csrf())` only if CSRF protection is enabled (stateless JWT APIs usually disable it).
- `@WithUserDetails` loads a real user from your `UserDetailsService` (needs that bean).
- **Method security** (`@PreAuthorize` on services) needs `@EnableMethodSecurity` in the loaded config; test it in a
  `@SpringBootTest` or with the service bean real, not mocked — mocking the service mocks away its annotations.

For TeamBoard's per-org RBAC, the strongest evidence is a `@SpringBootTest` + Testcontainers test that seeds a real
VIEWER membership and asserts 403 through the real filter chain, JWT and database. Write at least one per role.

> The 401 vs 403 distinction depends on your `AuthenticationEntryPoint`. Know which one your config returns for anonymous requests.

---

## 5. `@DataJpaTest`

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)   // don't swap in H2
@Import(TestcontainersConfig.class)                                            // real Postgres, see testcontainers.md
class HoldRepositoryTest {

    @Autowired HoldRepository holds;
    @Autowired TestEntityManager em;

    @Test
    void existsActiveHold_ignoresExpiredHolds() {
        var seat = em.persist(TestData.seat());
        var now = Instant.parse("2026-05-01T10:00:00Z");
        em.persist(TestData.hold(seat, now.minus(Duration.ofMinutes(10)), Duration.ofMinutes(5))); // expired
        em.flush();

        assertThat(holds.existsActiveHold(seat.getId(), now)).isFalse();
    }

    @Test
    void uniqueConfirmedBookingPerSeat_isEnforcedByDatabase() {
        var seat = em.persist(TestData.seat());
        em.persist(TestData.booking(seat));
        em.flush();
        assertThatThrownBy(() -> { em.persist(TestData.booking(seat)); em.flush(); })
            .isInstanceOf(PersistenceException.class);   // jakarta.persistence; Hibernate's ConstraintViolationException is a subtype
    }
}
```

Facts:
- Each test runs in a transaction that is **rolled back** → isolated. Consequence: code that relies on a **commit**
  (e.g. `@TransactionalEventListener(AFTER_COMMIT)`, other threads seeing data) won't behave as in production. Use
  `@SpringBootTest` for those.
- **Call `em.flush()`** before asserting on constraints — otherwise the INSERT hasn't happened yet. (Via a Spring Data
  repository you'd see Spring's `DataIntegrityViolationException`; via `TestEntityManager` directly, Hibernate's
  `ConstraintViolationException`, a `jakarta.persistence.PersistenceException`.)
- Flyway migrations run, so this also tests your schema.
- **Why not H2?** Different SQL dialect, constraint behaviour, locking and types (`jsonb`, `timestamptz`). Tests
  that pass on H2 and fail on Postgres are worse than no tests. Use Testcontainers.

---

## 6. `@SpringBootTest`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class BookingFlowIT {

    @Autowired TestRestTemplate http;       // real HTTP to the random port
    @Autowired BookingRepository bookings;

    @Test
    void customerCanHoldThenConfirm() {
        String token = TestAuth.loginAsCustomer(http);
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        var hold = http.exchange("/api/events/1/seats/7/hold", HttpMethod.POST,
                new HttpEntity<>(headers), HoldResponse.class);
        assertThat(hold.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var booking = http.exchange("/api/holds/" + hold.getBody().id() + "/confirm", HttpMethod.POST,
                new HttpEntity<>(headers), BookingResponse.class);
        assertThat(booking.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bookings.count()).isEqualTo(1);
    }
}
```

- `webEnvironment = MOCK` (default) + `@AutoConfigureMockMvc` → full context, MockMvc, no real server; tests are
  transactional-rollback-capable. `RANDOM_PORT` → real server on another thread; `@Transactional` on the test does
  **not** roll back server-side work → clean up with `@Sql`/truncation or fresh data per test.
- The concurrency test for P2 M5 (N threads holding the same seat, exactly one wins) must be a `@SpringBootTest` with
  real Postgres — see [testcontainers.md §6](./testcontainers.md#6-the-tickethold-concurrency-test).

---

## 7. Test profiles and properties

```yaml
# src/test/resources/application-test.yml
spring:
  jpa:
    open-in-view: false
  flyway:
    clean-disabled: false        # allow clean in tests only
app:
  jwt:
    secret: test-secret-at-least-32-bytes-long-000000
    access-ttl: PT15M
logging:
  level:
    org.hibernate.SQL: debug     # see generated SQL while debugging tests
```

- `@ActiveProfiles("test")` activates it; `@TestPropertySource(properties = "app.hold-ttl=PT1S")` for one class.
- `@DynamicPropertySource` sets properties computed at runtime (container URLs in pre-3.1 style).
- Never let tests read production secrets; never point tests at a shared DB.

---

## 8. Break it

1. Remove `@Import(SecurityConfig.class)` from a `@WebMvcTest`. Boot's default security applies → your role rules vanish (or everything is 401). Explain.
2. Replace `@MockitoBean EventService` with nothing → `NoSuchBeanDefinitionException` for the controller's dependency. That's the slice boundary.
3. Remove `em.flush()` from the constraint test → no exception → test fails. Why?
4. Put `@Transactional` on a `RANDOM_PORT` test and expect rollback → data leaks into the next test.
5. Give two controller test classes different `@MockitoBean` sets; watch "Starting application" appear twice in logs (two contexts).

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `@SpringBootTest` for everything | slices for layers; full context for flows |
| H2 for Postgres features | Testcontainers |
| Mocking the service then testing `@PreAuthorize` on it | test method security with real beans |
| Asserting on `toString()` of JSON | `jsonPath` / DTO deserialization |
| Relying on test order to share data | each test arranges its own data |
| Forgetting `flush()` in JPA tests | flush before asserting DB behaviour |

---

## Interview Q&A

<details><summary><code>@SpringBootTest</code> vs <code>@WebMvcTest</code> vs <code>@DataJpaTest</code>?</summary>

`@SpringBootTest` loads the whole context — for flows across layers. `@WebMvcTest` loads only the web layer with MockMvc;
services are mock beans — for HTTP contracts, validation and security. `@DataJpaTest` loads JPA with rollback per test —
for repositories, queries and constraints, and I point it at Testcontainers Postgres instead of H2.
</details>

<details><summary>What is MockMvc?</summary>

A way to perform requests against the DispatcherServlet without starting a server, then assert on status, headers and
JSON. It goes through filters (including Spring Security), argument resolution, validation and exception handlers.
</details>

<details><summary><code>@MockBean</code> vs <code>@MockitoBean</code>?</summary>

Same purpose — replace a bean in the Spring test context with a Mockito mock. `@MockitoBean` is the Spring Framework 6.2
version used from Boot 3.4; Boot's `@MockBean` is deprecated there.
</details>

<details><summary>How do you test that a VIEWER can't create an issue?</summary>

A MockMvc test with a mock user and the real security config asserting 403 and that the service wasn't called; plus
one `@SpringBootTest` with a real VIEWER membership in Testcontainers Postgres asserting 403 through the full chain.
</details>

<details><summary>Why does <code>@DataJpaTest</code> roll back, and when is that a problem?</summary>

It wraps each test in a transaction for isolation. It's a problem when behaviour depends on commit — after-commit
listeners, other threads/connections seeing data, or constraint checks deferred until flush/commit.
</details>

---

## Mastery checklist

- [ ] TicketHold: ≥ 1 `@WebMvcTest` per controller covering happy path, validation 400, 404, and 401/403.
- [ ] TicketHold: `@DataJpaTest` for every custom query and each unique constraint, on Postgres.
- [ ] TeamBoard: authorization tests per role (OWNER/ADMIN/MEMBER/VIEWER) for issue create/update/delete.
- [ ] One `@SpringBootTest` flow test (hold → confirm) with real HTTP.
- [ ] Explain context caching and why mock-bean sets matter.
- [ ] Explain `@MockitoBean` vs `@MockBean` and 401 vs 403.
