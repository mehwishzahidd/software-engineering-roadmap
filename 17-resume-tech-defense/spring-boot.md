# Spring Boot — Résumé Defense

**Target level:** L3 (L4 on DI and transactions) · **Learned:** Week 3 intro; Weeks 4–5 (FlowGrid: JPA, validation, errors, security, transactions, testing); Week 10 (propagation pitfalls, LedgerX); Weeks 13/17 (scheduling, caching); Weeks 14–15 (two apps in one multi-module repo, ForgeCI) · **Version:** Spring Boot 3.x (Spring Framework 6, Jakarta EE namespaces, Java 17+ baseline; we use 21)
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md)

---

## 1. Beginner questions

<details><summary><b>Q1. What is the difference between Spring Framework and Spring Boot?</b></summary>

Spring Framework provides the IoC container, DI, MVC, transactions, data access. Spring Boot is
an opinionated layer on top: auto-configuration, starter dependencies, embedded server (Tomcat),
externalised config (`application.yml`), Actuator, and an executable JAR — so you don't hand-wire infrastructure.
</details>

<details><summary><b>Q2. What is dependency injection / IoC?</b></summary>

Objects don't construct their dependencies; the container creates beans and injects them
(preferably via constructor). This decouples classes from concrete implementations and makes them
easy to test with fakes/mocks.
</details>

<details><summary><b>Q3. What does <code>@SpringBootApplication</code> do?</b></summary>

Combines `@SpringBootConfiguration` (a `@Configuration`), `@EnableAutoConfiguration`, and
`@ComponentScan` of the class's package and sub-packages.
</details>

<details><summary><b>Q4. <code>@Component</code> vs <code>@Service</code> vs <code>@Repository</code> vs <code>@Controller</code>?</b></summary>

All are stereotype annotations picked up by component scanning. `@Repository` adds persistence
exception translation; `@Controller`/`@RestController` are handled by Spring MVC
(`@RestController` = `@Controller` + `@ResponseBody`). `@Service` is semantic only.
</details>

<details><summary><b>Q5. What is a starter?</b></summary>

A dependency bundle, e.g. `spring-boot-starter-web` pulls Spring MVC, Jackson and embedded Tomcat.
Bean Validation is *not* included — add `spring-boot-starter-validation`. Versions are managed by the Boot parent/BOM.
</details>

<details><summary><b>Q6. Default bean scope?</b></summary>

Singleton — one instance per application context. So beans must be stateless or thread-safe;
they serve concurrent requests. Other scopes: prototype, request, session.
</details>

## 2. Intermediate questions

<details><summary><b>Q7. How does auto-configuration work?</b></summary>

Boot reads candidate classes from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
and applies them conditionally: `@ConditionalOnClass` (e.g. `DataSource` on classpath),
`@ConditionalOnMissingBean` (back off if you defined your own), `@ConditionalOnProperty`. Debug with
`--debug` or Actuator `/actuator/conditions`.
</details>

<details><summary><b>Q8. Why constructor injection over field injection?</b></summary>

Dependencies are explicit and can be `final`; object is never half-constructed; easy to unit-test
with `new Service(fakeRepo)`; circular dependencies fail fast. Single constructor needs no `@Autowired`.
</details>

<details><summary><b>Q9. Two beans implement the same interface — what happens?</b></summary>

Injection fails with `NoUniqueBeanDefinitionException`. Resolve with `@Primary`, `@Qualifier("name")`,
inject `List<Interface>` (all of them — how FlagForge's rule evaluators and FlowGrid's allocation scorers are collected), or `Map<String, Interface>`.
</details>

<details><summary><b>Q10. How does <code>@Transactional</code> work, and when doesn't it?</b></summary>

Spring wraps the bean in a proxy; calls through the proxy start/commit/roll back a transaction.
Rolls back by default on unchecked exceptions and `Error`, not checked exceptions (use `rollbackFor`).
Doesn't apply on self-invocation (`this.method()` bypasses the proxy), on private methods, or if the
exception is caught inside. Default propagation `REQUIRED`; `REQUIRES_NEW` suspends and starts a new one.
</details>

<details><summary><b>Q11. What is the N+1 problem in Spring Data JPA and how do you fix it?</b></summary>

Loading N parents then lazily touching a collection triggers N extra queries. Fix: `JOIN FETCH` in
JPQL, `@EntityGraph`, DTO projections, or batch fetching (`hibernate.default_batch_fetch_size`).
Detect by enabling SQL logging or counting statements in tests.
</details>

<details><summary><b>Q12. How do you handle errors globally?</b></summary>

`@RestControllerAdvice` with `@ExceptionHandler` methods returning `ProblemDetail` (RFC 7807/9457,
built into Spring 6). Map domain exceptions to 404/409, `MethodArgumentNotValidException` to 400 with field errors.
Enable `spring.mvc.problemdetails.enabled=true` for framework exceptions.
</details>

<details><summary><b>Q13. Walk through the Spring Security filter chain for a JWT request.</b></summary>

Request → `SecurityFilterChain` (defined as a `@Bean` with `HttpSecurity`) → custom JWT filter
(or the OAuth2 resource server `BearerTokenAuthenticationFilter`) extracts the `Authorization: Bearer`
token, validates signature + expiry, builds an `Authentication` and puts it in `SecurityContextHolder`
→ authorization rules (`authorizeHttpRequests`, `@PreAuthorize`) → controller. Failures give 401
(unauthenticated) or 403 (forbidden). Stateless: `SessionCreationPolicy.STATELESS`, CSRF typically disabled for pure token APIs.
</details>

<details><summary><b>Q14. How do profiles and externalised configuration work?</b></summary>

`application.yml` + `application-{profile}.yml`; activate with `SPRING_PROFILES_ACTIVE`. Precedence:
command-line args and env vars override files. Bind typed config with `@ConfigurationProperties`
records. Secrets come from env vars / a secrets manager, never committed.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "You list Spring Boot. What did you build with it, and what was your part?"</b></summary>

- Truthful past scope (e.g. "maintained endpoints in an existing service; I didn't design the architecture").
- Current: "All four of my projects are Spring Boot 3 on Java 21. FlowGrid is the most complete: layered
  controller/service/repository, JPA + Flyway, JWT security with ADMIN / OPS_MANAGER / WAREHOUSE_ASSOCIATE /
  VIEWER roles, ProblemDetail errors, a Redis cache and scheduled low-stock alerts. ForgeCI runs as two Boot
  apps — api and worker — from one Maven reactor."
- One hard part: reservations under concurrency (`SELECT … FOR UPDATE` vs `@Version`, 409 on conflict,
  N-threads-one-unit test) — and in LedgerX, `@Transactional` propagation around the idempotency store and lock ordering.
</details>

<details><summary><b>R2. "What happens when a request hits your controller?" (from the socket to the DB and back)</b></summary>

- Tomcat thread accepts → filter chain (security, request-ID MDC filter) → `DispatcherServlet` →
  handler mapping → argument resolution + `@Valid` → controller → service (`@Transactional` proxy) →
  repository → Hikari connection → SQL → entity → DTO → Jackson → `ResponseEntity` → filters → client.
</details>

<details><summary><b>R3. "Your @Transactional method isn't rolling back. Why?"</b></summary>

- Self-invocation, checked exception, caught exception, non-public method, bean not Spring-managed,
  or wrong `@Transactional` import (Spring's vs `jakarta.transaction` — both work but differ in attributes).
- How you'd prove it: enable `logging.level.org.springframework.transaction=DEBUG`, write a failing integration test.
</details>

<details><summary><b>R4. "How did you secure the API? Why JWT and not sessions?"</b></summary>

- BCrypt passwords, login returns short-lived signed JWT, stateless filter chain, method security for OPS_MANAGER-only endpoints (stock adjustments, transfers).
- Trade-off: JWT scales without shared session store but revocation is hard (short expiry, refresh tokens,
  denylist). Sessions are simpler and revocable for a single server-rendered app.
</details>

<details><summary><b>R5. "How do you test a Spring Boot app?"</b></summary>

- Unit tests with Mockito for services (no Spring context). `@WebMvcTest` for controllers + `MockMvc`.
  `@DataJpaTest` + Testcontainers Postgres for repositories. A few `@SpringBootTest` end-to-end.
- Evidence: FlowGrid's suite running in GitHub Actions via `mvn verify` since W4; LedgerX's invariant suite; ForgeCI's Testcontainers Postgres + Redis integration tests.
</details>

<details><summary><b>R6. "When did you last use Spring, and what has changed since?"</b></summary>

- Honest year/version. Then Boot 3 changes: `javax.*` → `jakarta.*`, Java 17 baseline, `SecurityFilterChain`
  bean instead of `WebSecurityConfigurerAdapter` (removed), ProblemDetail support, Micrometer Observation, virtual threads
  via `spring.threads.virtual.enabled=true` (3.2+).
</details>

## 4. Practical tasks (live)

- [ ] From start.spring.io, build a CRUD `/api/warehouses` resource with DTO records, validation, and a 404 via ProblemDetail — in 30 minutes.
- [ ] Add a `@DataJpaTest` with Testcontainers that proves a unique constraint.
- [ ] Add a `SecurityFilterChain` that permits `/api/auth/**` and requires `ROLE_OPS_MANAGER` for `POST /api/stock-adjustments`.
- [ ] Add a `@Scheduled(fixedDelay = ...)` job (FlowGrid's low-stock alert) and `@EnableScheduling`; explain the default single-thread scheduler and what happens with 3 instances.
- [ ] Expose `/actuator/health` and `/actuator/metrics` only.

## 5. Debugging questions

<details><summary><b>D1. App fails to start: "Parameter 0 of constructor in X required a bean of type Y that could not be found."</b></summary>

Y isn't a bean: missing stereotype annotation, outside the component-scan package, missing starter,
or an auto-config conditional didn't match. Check package structure and `--debug` conditions report.
</details>

<details><summary><b>D2. <code>LazyInitializationException: could not initialize proxy - no Session</code>.</b></summary>

A lazy association accessed after the transaction/persistence context closed (often during JSON
serialization in the controller). Fix: fetch what you need in the service (`JOIN FETCH`/`@EntityGraph`)
and map to DTOs inside the transaction. Don't "fix" by enabling open-in-view or making everything EAGER.
</details>

<details><summary><b>D3. Every request returns 403, even login.</b></summary>

Security chain not permitting the login path, CSRF enabled for a stateless POST, or role naming mismatch
(`hasRole("ADMIN")` expects authority `ROLE_ADMIN`). Turn on `logging.level.org.springframework.security=DEBUG` to see which filter rejects.
</details>

<details><summary><b>D4. Requests slow down under load; logs show "Connection is not available, request timed out after 30000ms".</b></summary>

HikariCP pool exhausted — long transactions, connection leaks, or slow queries. Check Actuator
`hikaricp.connections.active/pending` metrics, find long transactions, fix query/indices, keep
transactions short (no HTTP calls inside them). Then size pool deliberately.
</details>

<details><summary><b>D5. Jackson infinite recursion (<code>StackOverflowError</code>) serialising entities.</b></summary>

Bidirectional relationships serialised both ways. Root fix: return DTOs, not entities.
</details>

## 6. Architecture questions

- Why layered (controller/service/repository), and what belongs in each layer? Where do transactions live?
- Where should authorization checks for FlagForge's per-org roles live — filter, `@PreAuthorize`, or service? Trade-offs.
- ForgeCI runs api and worker as separate Spring Boot apps sharing Postgres + Redis in one Maven reactor. What lives in the shared `core` module, what must never (web layer, docker-java), and how do profiles/config differ?
- Optimistic vs pessimistic locking for FlowGrid reservations and LedgerX transfers — when would you switch between `@Version` and `SELECT ... FOR UPDATE`?
- Where does the `@Transactional` boundary sit relative to LedgerX's idempotency store, and why must the stored response be written in the same transaction as the journal entries?
- How do you make a POST idempotent (Idempotency-Key table with unique constraint + stored response)?

## 7. Common mistakes

- Returning JPA entities from controllers.
- Field injection; business logic in controllers.
- `@Transactional` on private methods or self-invoked methods.
- `FetchType.EAGER` everywhere to avoid lazy errors.
- Committing secrets in `application.yml`.
- Catching exceptions in controllers instead of a global handler.
- Relying on `ddl-auto=update` in production instead of Flyway.

## 8. Terminology I must know

| Term | One-line meaning |
|---|---|
| Bean | Object whose lifecycle the Spring container manages |
| ApplicationContext | The IoC container holding beans |
| Auto-configuration | Conditional config applied based on classpath/properties |
| Starter | Curated dependency set for a feature |
| Proxy | Wrapper Spring puts around beans for transactions/security/caching |
| `DispatcherServlet` | Front controller routing requests to handlers |
| Persistence context | Hibernate's first-level cache of managed entities per transaction |
| Propagation | How a transactional method joins/starts transactions |
| `SecurityFilterChain` | Ordered servlet filters applying security |
| ProblemDetail | RFC 7807/9457 JSON error body type |
| Actuator | Production endpoints: health, metrics, info |
| Flyway | Versioned SQL migrations run at startup |
| Profile | Named config set activated per environment |

## 9. When to use it

Java REST APIs and services needing DB access, security, observability and a large ecosystem;
teams that value convention and hiring pool.

## 10. When NOT to use it

Tiny single-purpose functions (cold-start/memory cost), client libraries (the FlagForge Java SDK
deliberately has no Spring dependency so any app can embed it), extremely latency-critical paths where framework overhead matters, or when the team
has no Java skills.

## 11. Trade-offs

| Gain | Cost |
|---|---|
| Fast start via auto-config and starters | "Magic" — must know how to inspect conditions |
| Rich integrations (JPA, Security, Actuator) | Heavier memory/startup than Micronaut/Quarkus/Go |
| Proxies give declarative transactions/security | Proxy pitfalls (self-invocation) |
| JPA productivity | Hides SQL; N+1 and lazy-loading traps |

## 12. How it interacts with the rest of my stack

- **Java 21**: records as DTOs, virtual threads for request handling (optional).
- **Maven**: `spring-boot-starter-parent` manages versions; `spring-boot-maven-plugin` builds the fat JAR.
- **PostgreSQL**: HikariCP + Hibernate + Flyway migrations; Testcontainers in tests.
- **React**: JSON API + CORS config for the Vite dev origin; JWT in `Authorization` header.
- **Docker**: JAR in a JRE image; config via env vars (`SPRING_DATASOURCE_URL`).
- **AWS**: runs on EC2, connects to RDS, logs to CloudWatch; health check hits `/actuator/health`.
- **CI**: `mvn verify` runs unit + slice + Testcontainers tests on every push.

## 13. Hands-on exercise

**Build `ReservationService.reserve(skuId, warehouseId, qty)` in a fresh app.**

Acceptance criteria:
- [ ] `InventoryLevel` entity; `reserve` locks the row (`@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE`) and moves `qty` from available → reserved in one `@Transactional` method; a second branch does it with `@Version` so you can compare.
- [ ] Concurrent test: 10 threads reserve the last unit → exactly 1 success, 9 get `409 Conflict` (ProblemDetail).
- [ ] `@DataJpaTest` runs against Testcontainers PostgreSQL 16.
- [ ] Unauthenticated call → 401; VIEWER → 403; OPS_MANAGER → allowed.
- [ ] You can explain the SQL Hibernate emits in both variants (`SELECT … FOR UPDATE` vs `UPDATE ... WHERE id=? AND version=?`).

## 14. Mastery checklist

- [ ] Explain IoC/DI, bean scopes, and auto-configuration with conditions.
- [ ] Explain the request lifecycle end-to-end.
- [ ] Explain `@Transactional` proxy mechanics, rollback rules, propagation, and pitfalls.
- [ ] Fix N+1 and `LazyInitializationException` properly.
- [ ] Configure Spring Security 6 JWT stateless auth from scratch.
- [ ] Write unit, `@WebMvcTest`, `@DataJpaTest`, and Testcontainers tests.
- [ ] Use profiles, `@ConfigurationProperties`, and env-var overrides.
- [ ] Read a startup failure and find the missing bean/conditional.

## Evidence in my projects

| Project | What it demonstrates | Fill in: file / commit |
|---|---|---|
| FlowGrid | Layered API, JPA + Flyway, Bean Validation, ProblemDetail, JWT + 4 roles, MDC request IDs, Actuator, OpenAPI, `@Transactional` + `@Lock(PESSIMISTIC_WRITE)` reservations, Idempotency-Key, `StringRedisTemplate`/Spring Cache for the catalog cache, `@Scheduled` low-stock alerts, Micrometer metrics, structured JSON logs, full test pyramid | |
| LedgerX | `@Transactional` propagation done deliberately (idempotency row + journal entries in one transaction), `REQUIRES_NEW` for the audit log, isolation-level experiments via `@Transactional(isolation = …)`, transactional outbox + `@TransactionalEventListener`, `@Scheduled` reconciliation and scheduled payments | |
| ForgeCI | **Two Boot apps (api + worker) in one Maven reactor** sharing `core`; worker: `ApplicationRunner` loop, `ExecutorService` beans, graceful shutdown (`server.shutdown=graceful`, `SmartLifecycle`), docker-java client bean; api: **SSE** via `SseEmitter`, webhook filter verifying HMAC, GitHub OAuth (Spring Security OAuth2 client), Redis pub/sub listener | |
| FlagForge | Multi-tenant authz (org roles), immutable config versions, evaluation service + Redis snapshot cache (`@Cacheable` vs explicit template — decision recorded), SSE propagation, SDK-key auth filter, rate limiting; Java SDK built *without* Spring | |

## Where to learn it in this repo

[`../05-spring-boot/README.md`](../05-spring-boot/README.md) · [`../05-spring-boot/01-core-di.md`](../05-spring-boot/01-core-di.md) ·
[`../05-spring-boot/02-web-layer.md`](../05-spring-boot/02-web-layer.md) · [`../05-spring-boot/03-data-jpa.md`](../05-spring-boot/03-data-jpa.md) ·
[`../05-spring-boot/04-validation-errors.md`](../05-spring-boot/04-validation-errors.md) · [`../05-spring-boot/05-security-jwt.md`](../05-spring-boot/05-security-jwt.md) ·
[`../05-spring-boot/06-logging-actuator.md`](../05-spring-boot/06-logging-actuator.md) · [`../05-spring-boot/07-transactions.md`](../05-spring-boot/07-transactions.md) ·
[`../05-spring-boot/08-caching-scheduling.md`](../05-spring-boot/08-caching-scheduling.md) · [`../05-spring-boot/interview-questions.md`](../05-spring-boot/interview-questions.md) ·
[`../09-testing/spring-testing.md`](../09-testing/spring-testing.md)
