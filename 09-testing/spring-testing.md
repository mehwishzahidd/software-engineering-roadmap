# Spring Boot Testing

> **Weeks 4–5** (FlowGrid M1–M2: controllers, validation, security, repositories, idempotent orders), reused in
> LedgerX (W9–12), ForgeCI (W14–18) and FlagForge (W20–23). Server-side concepts: [05-spring-boot/](../05-spring-boot/README.md).
> Code below shows **test patterns** on FlowGrid-shaped endpoints; your own tests follow your own API.

---

## 1. The menu: full context vs slices

| Annotation | Loads | Speed | Use for |
|---|---|---|---|
| none (plain JUnit + Mockito) | nothing | ms | services, domain logic |
| `@WebMvcTest(Controller.class)` | MVC infrastructure: controllers, `@ControllerAdvice`, filters, converters, Jackson, Spring Security auto-config, `MockMvc` — **no** `@Service`/`@Repository` beans | fast | HTTP contract: routing, validation, status codes, JSON, error format, security rules |
| `@DataJpaTest` | JPA: entities, repositories, `EntityManager`, Flyway/Liquibase, `DataSource`; **transactional + rollback** per test | medium | custom queries, mappings, constraints, triggers, migrations |
| `@DataRedisTest` | Spring Data Redis repositories/templates | medium | Redis-backed repositories (with a Redis container) |
| `@JsonTest` | Jackson only | fast | tricky (de)serialization (money as strings, `Instant`) |
| `@RestClientTest` | `RestClient`/`RestTemplate` + mock server | fast | outbound HTTP clients (ForgeCI's GitHub API client) |
| `@SpringBootTest` | the **whole** application context | slow | flows across layers, transactions, security, scheduling, concurrency |

Slices load **only** the relevant beans; anything else the slice needs must be provided as a mock bean or imported.

---

## 2. `@WebMvcTest` + MockMvc

```java
@WebMvcTest(SkuController.class)
@Import(SecurityConfig.class)                    // your SecurityFilterChain, so the real rules apply
class SkuControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean SkuService skuService;          // replaces the bean in the slice context
    @MockitoBean JwtService jwtService;          // needed by your JWT filter if the filter is a bean

    @Test
    @WithMockUser(roles = "OPS_MANAGER")
    void create_withValidBody_returns201AndLocation() throws Exception {
        var request = new CreateSkuRequest("BOLT-M8-50", "Bolt M8×50", 20);
        when(skuService.create(any())).thenReturn(new SkuResponse(10L, "BOLT-M8-50", "Bolt M8×50", 20));

        mvc.perform(post("/api/skus")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", endsWith("/api/skus/10")))
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.code").value("BOLT-M8-50"));
    }

    @Test
    @WithMockUser(roles = "OPS_MANAGER")
    void create_withBlankCode_returns400ProblemDetail() throws Exception {
        mvc.perform(post("/api/skus")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code": "", "name": "Bolt", "reorderPoint": 20}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errors.code").exists());

        verifyNoInteractions(skuService);        // validation stopped the request before the service
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(skuService.get(99L)).thenThrow(new NotFoundException("sku", 99L));
        mvc.perform(get("/api/skus/99").with(user("ann").roles("VIEWER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }
}
```

Static imports: `MockMvcRequestBuilders.*`, `MockMvcResultMatchers.*`, `SecurityMockMvcRequestPostProcessors.*`, `Matchers.endsWith`.

Notes:
- `$.errors.code` matches **your** `@RestControllerAdvice` output ([05-spring-boot/04-validation-errors.md](../05-spring-boot/04-validation-errors.md)); adjust.
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
Older codebases and tutorials use `@MockBean` — same idea.

> **Context caching:** Spring caches application contexts across test classes with the **same configuration**.
> Every distinct combination of mock beans creates a **new** context → slow suites. Keep mock-bean sets consistent
> (e.g. a shared abstract base for controller tests), and avoid `@DirtiesContext` unless necessary.

---

## 4. Security tests

Dependency: `spring-security-test` (test scope). FlowGrid roles: ADMIN, OPS_MANAGER, WAREHOUSE_ASSOCIATE, VIEWER.

```java
@WebMvcTest(AdjustmentController.class)
@Import(SecurityConfig.class)
class AdjustmentControllerSecurityTest {

    @Autowired MockMvc mvc;
    @MockitoBean AdjustmentService adjustments;
    @MockitoBean JwtService jwtService;

    static final String BODY = """
        {"skuId": 7, "warehouseId": 1, "delta": -2, "reason": "DAMAGED"}
        """;

    @Test
    void anonymous_gets401() throws Exception {
        mvc.perform(post("/api/inventory/adjustments").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWER", "WAREHOUSE_ASSOCIATE"})
    void rolesWithoutAdjustPermission_get403(String role) throws Exception {
        mvc.perform(post("/api/inventory/adjustments").with(user("u").roles(role))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verifyNoInteractions(adjustments);
    }

    @Test
    @WithMockUser(roles = "OPS_MANAGER")
    void opsManager_canAdjust() throws Exception {
        when(adjustments.adjust(any())).thenReturn(TestData.adjustmentResponse());
        mvc.perform(post("/api/inventory/adjustments").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated());
    }
}
```

(Which roles may adjust stock is *your* decision in FlowGrid's spec — the test table encodes it.)

Tools:
- `@WithMockUser(roles = "OPS_MANAGER")` → authority `ROLE_OPS_MANAGER`. `authorities = "inventory:adjust"` for raw authorities.
- `.with(user("ann").roles("ADMIN"))` per request (works well with `@ParameterizedTest`).
- `.with(jwt().authorities(...))` if you use Spring's OAuth2 resource server.
- `.with(csrf())` only if CSRF protection is enabled (stateless JWT APIs usually disable it).
- **Method security** (`@PreAuthorize` on services) needs `@EnableMethodSecurity` in the loaded config; test it with the
  real service bean (a `@SpringBootTest`) — mocking the service mocks away its annotations.
- The 401 vs 403 distinction depends on your `AuthenticationEntryPoint`. Know which one your config returns for anonymous requests.

FlagForge (Week 20) has **per-organization** roles: the rule is "member of *this* org with role ≥ X". A mock user with a
global role can't express that — write those tests as `@SpringBootTest` + Testcontainers with real memberships, and include
the cross-tenant case (an ADMIN of org A gets 403/404 on org B's flags).

ForgeCI (Week 14) adds a different kind of auth test: the webhook endpoint authenticates **the sender** with an
HMAC-SHA256 signature — test valid signature → 2xx, wrong/missing signature → 401, and the same delivery id twice → one build.

---

## 5. `@DataJpaTest`

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)   // don't swap in H2
@Import(TestcontainersConfig.class)                                            // real Postgres, see testcontainers.md
class IdempotencyRecordRepositoryTest {

    @Autowired IdempotencyRecordRepository records;
    @Autowired TestEntityManager em;

    @Test
    void sameKeyTwice_isRejectedByUniqueConstraint() {
        em.persist(TestData.idempotencyRecord("key-1", "sha256:aaa"));
        em.flush();
        assertThatThrownBy(() -> { em.persist(TestData.idempotencyRecord("key-1", "sha256:bbb")); em.flush(); })
            .isInstanceOf(PersistenceException.class);   // jakarta.persistence; Hibernate's ConstraintViolationException is a subtype
    }

    @Test
    void findLowStock_returnsOnlyLevelsBelowReorderPoint() {
        // arrange 3 inventory levels around the reorder point, flush, call the custom query, assert exact SKUs
    }
}
```

Facts:
- Each test runs in a transaction that is **rolled back** → isolated. Consequence: code that relies on a **commit**
  (`@TransactionalEventListener(AFTER_COMMIT)`, the outbox relay, other threads/connections seeing data) won't behave as
  in production. Use `@SpringBootTest` for those.
- **Call `em.flush()`** before asserting on constraints or triggers — otherwise the SQL hasn't run yet. (Through a Spring
  Data repository you'd see Spring's `DataIntegrityViolationException`; through `TestEntityManager` directly, a JPA
  `PersistenceException`.)
- Flyway migrations run, so this also tests your schema — including LedgerX's "no UPDATE/DELETE on `ledger_entry`"
  trigger: attempt a native `UPDATE`, flush, expect an exception.
- **Why not H2?** Different SQL dialect, constraint and trigger behaviour, locking, types (`jsonb`, `timestamptz`).
  Tests that pass on H2 and fail on Postgres are worse than no tests. Use Testcontainers.

---

## 6. `@SpringBootTest`

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class OrderIdempotencyIT {

    @Autowired TestRestTemplate http;       // real HTTP to the random port
    @Autowired OrderRepository orders;

    @Test
    void samePostWithSameIdempotencyKey_createsOneOrderAndReturnsSameResponse() {
        HttpHeaders headers = TestAuth.bearer(http, "ops@flowgrid.test");
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var body = new HttpEntity<>(TestData.createOrderRequest(), headers);

        var first = http.postForEntity("/api/orders", body, OrderResponse.class);
        var second = http.postForEntity("/api/orders", body, OrderResponse.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).usingRecursiveComparison().isEqualTo(first.getBody());
        assertThat(orders.count()).isEqualTo(1);
    }
    // Also: same key + different body → 409 (or 422 — your spec decides); key expired → treated as new.
}
```

- `webEnvironment = MOCK` (default) + `@AutoConfigureMockMvc` → full context, MockMvc, no real server; tests can be
  transactional-rollback. `RANDOM_PORT` → real server on another thread; `@Transactional` on the test does **not** roll back
  server-side work → clean up with `@Sql`/truncation or unique data per test.
- Concurrency tests (FlowGrid N-threads-one-unit, LedgerX $500/$400/$400) must be `@SpringBootTest` with real Postgres —
  see [testcontainers.md §6](./testcontainers.md#6-concurrency-tests-the-core-evidence).

---

## 7. Test profiles and properties

```yaml
# src/test/resources/application-test.yml
spring:
  jpa:
    open-in-view: false
app:
  jwt:
    secret: test-secret-at-least-32-bytes-long-000000
    access-ttl: PT15M
  reservations:
    ttl: PT15M
logging:
  level:
    org.hibernate.SQL: debug     # see generated SQL while debugging tests
```

- `@ActiveProfiles("test")` activates it; `@TestPropertySource(properties = "app.reservations.ttl=PT1S")` for one class.
- `@DynamicPropertySource` sets properties computed at runtime (container URLs in pre-3.1 style).
- Never let tests read production secrets; never point tests at a shared DB.

---

## 8. Break it

1. Remove `@Import(SecurityConfig.class)` from a `@WebMvcTest`. Boot's default security applies → your role rules vanish (or everything is 401). Explain.
2. Remove `@MockitoBean SkuService` → `NoSuchBeanDefinitionException` for the controller's dependency. That's the slice boundary.
3. Remove `em.flush()` from the constraint test → no exception → test fails. Why?
4. Put `@Transactional` on a `RANDOM_PORT` test and expect rollback → data leaks into the next test.
5. Give two controller test classes different `@MockitoBean` sets; watch "Starting application" appear twice in logs (two contexts).
6. Forget to add `Idempotency-Key` handling for a retry that arrives while the first request is still in flight. What should the second request get? Write the test before deciding.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `@SpringBootTest` for everything | slices for layers; full context for flows |
| H2 for Postgres features | Testcontainers |
| Mocking the service then testing `@PreAuthorize` on it | test method security with real beans |
| Asserting on JSON strings | `jsonPath` / DTO deserialization |
| Relying on test order to share data | each test arranges its own data |
| Forgetting `flush()` in JPA tests | flush before asserting DB behaviour |

---

## Interview Q&A

<details><summary><code>@SpringBootTest</code> vs <code>@WebMvcTest</code> vs <code>@DataJpaTest</code>?</summary>

`@SpringBootTest` loads the whole context — for flows across layers. `@WebMvcTest` loads only the web layer with MockMvc;
services are mock beans — for HTTP contracts, validation and security. `@DataJpaTest` loads JPA with rollback per test —
for repositories, queries and constraints, pointed at Testcontainers Postgres instead of H2.
</details>

<details><summary>What is MockMvc?</summary>

A way to perform requests against the DispatcherServlet without starting a server, then assert on status, headers and
JSON. It goes through filters (including Spring Security), argument resolution, validation and exception handlers.
</details>

<details><summary><code>@MockBean</code> vs <code>@MockitoBean</code>?</summary>

Same purpose — replace a bean in the Spring test context with a Mockito mock. `@MockitoBean` is the Spring Framework 6.2
version used from Boot 3.4; Boot's `@MockBean` is deprecated there.
</details>

<details><summary>How did you test that a VIEWER can't adjust stock?</summary>

A parameterized MockMvc test over the roles that lack the permission, with the real security config, asserting 403 and
that the service wasn't called; plus a positive test for the allowed role.
</details>

<details><summary>How did you test idempotency?</summary>

A `@SpringBootTest` against Testcontainers Postgres that posts the same request twice with the same `Idempotency-Key` and
asserts one row and identical responses; a second test with the same key and a different body asserts a conflict.
</details>

---

## Mastery checklist

- [ ] FlowGrid: ≥ 1 `@WebMvcTest` per controller covering happy path, validation 400, 404, and 401/403.
- [ ] FlowGrid: `@DataJpaTest` for every custom query and each unique constraint, on Postgres.
- [ ] FlowGrid: idempotent order creation proven end-to-end.
- [ ] LedgerX: immutability trigger tested; ForgeCI: webhook signature + delivery dedupe tested; FlagForge: cross-tenant access tested.
- [ ] Explain context caching and why mock-bean sets matter.
- [ ] Explain `@MockitoBean` vs `@MockBean` and 401 vs 403.
