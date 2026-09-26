# 🎤 Spring Boot Interview Questions (80)

> How to use: answer **out loud** before opening each answer. Then add one sentence tying it to *your* code: "In FlowGrid I…".
> If you can't point at your own code, say what you'd do, and don't claim you did it. Mark weak ones in
> [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md) and re-drill on Day 3/7/14.
> Deeper explanations live in the topic files [01](./01-core-di.md)–[08](./08-caching-scheduling.md). Résumé drill: [`../17-resume-tech-defense/spring-boot.md`](../17-resume-tech-defense/spring-boot.md).

## A. Core, DI, configuration (1–14)

<details><summary>1. What is Inversion of Control?</summary>

The framework, not your code, creates objects and wires their dependencies. Your classes declare what they need; the container supplies it.
</details>

<details><summary>2. What is dependency injection and what are its forms?</summary>

Supplying an object's collaborators from outside: constructor injection (preferred), setter injection (optional dependencies), field injection (discouraged).
</details>

<details><summary>3. Why is constructor injection preferred?</summary>

Explicit, required dependencies; final fields; no half-built objects; easy unit tests without Spring; circular dependencies surface at startup.
</details>

<details><summary>4. What is a Spring bean?</summary>

An object whose lifecycle is managed by the ApplicationContext: created, injected, proxied if needed, initialized, and destroyed by the container.
</details>

<details><summary>5. @Component vs @Service vs @Repository vs @Controller?</summary>

All are stereotypes of @Component for scanning. @Repository adds persistence exception translation. @Controller marks web handlers. @Service is semantic.
</details>

<details><summary>6. @Component vs @Bean?</summary>

@Component: annotate your own class and Spring constructs it. @Bean: a factory method in a @Configuration class, for third-party types or custom construction.
</details>

<details><summary>7. What are bean scopes?</summary>

singleton (default), prototype, request, session, application. Singletons are shared across threads, so they must be stateless or thread-safe.
</details>

<details><summary>8. What happens when you inject a prototype bean into a singleton?</summary>

It's injected once, so the singleton keeps one instance. Use ObjectProvider/Provider or lookup methods to get a fresh one per use.
</details>

<details><summary>9. Two beans implement the same interface. How does Spring choose?</summary>

It fails with NoUniqueBeanDefinitionException unless one is @Primary, the injection point has @Qualifier, the parameter name matches a bean name, or you inject a List/Map of all of them.
</details>

<details><summary>10. What is the bean lifecycle?</summary>

Instantiate → inject dependencies → Aware callbacks → BeanPostProcessors before-init → @PostConstruct / init → BeanPostProcessors after-init (proxies created here) → in use → @PreDestroy on shutdown.
</details>

<details><summary>11. How does Spring Boot auto-configuration work?</summary>

@EnableAutoConfiguration loads auto-configuration classes listed in META-INF/spring/…AutoConfiguration.imports. Conditions (@ConditionalOnClass, @ConditionalOnMissingBean, @ConditionalOnProperty) decide which apply. Your own beans take precedence. Inspect with --debug or /actuator/conditions.
</details>

<details><summary>12. What is a starter?</summary>

A curated dependency bundle (e.g., spring-boot-starter-web) with versions managed by Boot's BOM, which triggers the related auto-configuration.
</details>

<details><summary>13. How do you externalize configuration and what overrides what?</summary>

application.yml, profile-specific files, env vars, CLI args, and more. Command-line args and env vars override files. Bind groups with @ConfigurationProperties (validated records). Secrets come from env/secret stores, never Git.
</details>

<details><summary>14. What are profiles used for?</summary>

Environment-specific configuration and beans (dev/test/prod): `spring.profiles.active`, `application-prod.yml`, `@Profile("dev")`.
</details>

## B. Web layer (15–24)

<details><summary>15. Describe the request lifecycle in Spring MVC.</summary>

Tomcat thread → servlet filters (incl. Spring Security) → DispatcherServlet → HandlerMapping → interceptors' preHandle → HandlerAdapter resolves arguments (message converters, validation) → controller → return value converted to JSON → postHandle/afterCompletion → exception resolvers if thrown → filters unwind.
</details>

<details><summary>16. Filter vs HandlerInterceptor?</summary>

Filters are servlet-level, run for every request before DispatcherServlet, and can wrap the request/response. Interceptors are MVC-level, run around mapped handlers, and know the handler method.
</details>

<details><summary>17. @RestController vs @Controller?</summary>

@RestController adds @ResponseBody to every method, so return values are written to the body instead of being resolved as views.
</details>

<details><summary>18. @PathVariable vs @RequestParam vs @RequestBody?</summary>

URI template segment; query/form parameter; deserialized request body via an HttpMessageConverter.
</details>

<details><summary>19. How is JSON converted to objects?</summary>

The MappingJackson2HttpMessageConverter uses Boot's auto-configured ObjectMapper, chosen by Content-Type/Accept (content negotiation).
</details>

<details><summary>20. Why DTOs instead of entities in the API?</summary>

Contract decoupling, no over-posting, no lazy-loading/recursion issues, no leaked fields, per-endpoint shapes. Records make concise, immutable DTOs.
</details>

<details><summary>21. How do you return 201 Created properly?</summary>

ResponseEntity.created(location).body(dto), with Location built via ServletUriComponentsBuilder.
</details>

<details><summary>22. How do you implement pagination and sorting?</summary>

A Pageable parameter (page, size, sort) passed to Spring Data, returning a stable page DTO. Whitelist sortable fields, cap the size, and consider cursor pagination for large or append-only data.
</details>

<details><summary>23. What changes with virtual threads in Spring Boot 3.2+?</summary>

With spring.threads.virtual.enabled=true, requests, @Async and @Scheduled run on virtual threads: cheap blocking for I/O-bound work. You still must bound DB pool usage and downstream concurrency. Pinning inside synchronized blocks can reduce the benefit on older JDKs.
</details>

<details><summary>24. How do you call another HTTP service from Spring?</summary>

RestClient (Spring 6.1+, synchronous), WebClient (reactive), or HTTP interface clients, with timeouts configured, errors mapped, and retries only for idempotent calls.
</details>

## C. Validation and errors (25–30)

<details><summary>25. How does Bean Validation integrate with controllers?</summary>

@Valid on @RequestBody triggers validation of constraint annotations. Failures raise MethodArgumentNotValidException → 400. Nested objects need @Valid to cascade.
</details>

<details><summary>26. @Valid vs @Validated?</summary>

@Valid (Jakarta) triggers validation and cascades. @Validated (Spring) enables method-level validation on a bean and supports validation groups.
</details>

<details><summary>27. How do you implement global error handling?</summary>

A @RestControllerAdvice with @ExceptionHandler methods (often extending ResponseEntityExceptionHandler) returning ProblemDetail, with a catch-all 500 that hides internals.
</details>

<details><summary>28. What is ProblemDetail?</summary>

Spring 6's representation of RFC 9457 (formerly 7807) `application/problem+json`: type, title, status, detail, instance, plus extension properties.
</details>

<details><summary>29. Why can't @ControllerAdvice handle authentication failures?</summary>

They happen in the security filter chain before DispatcherServlet. Use an AuthenticationEntryPoint (401) and an AccessDeniedHandler (403).
</details>

<details><summary>30. How do you map a unique-constraint violation to a good response?</summary>

Catch DataIntegrityViolationException, find the underlying constraint name, map known names to a 409 ProblemDetail with a friendly message, and never echo the raw SQL error.
</details>

## D. JPA, Hibernate, Spring Data (31–44)

<details><summary>31. JPA vs Hibernate vs Spring Data JPA?</summary>

A specification, its implementation, and a repository abstraction on top that generates DAO code and queries.
</details>

<details><summary>32. What is the persistence context?</summary>

The set of managed entities in a unit of work: an identity map (first-level cache) with change tracking, flushed to the DB as SQL.
</details>

<details><summary>33. What is dirty checking?</summary>

At flush, Hibernate compares managed entities to their snapshots and issues UPDATEs for changes. No explicit save needed inside the transaction.
</details>

<details><summary>34. Entity states?</summary>

Transient, managed, detached, removed.
</details>

<details><summary>35. Default fetch types?</summary>

@ManyToOne/@OneToOne EAGER; @OneToMany/@ManyToMany LAZY. Recommended: make all LAZY and fetch per use case.
</details>

<details><summary>36. What is the N+1 problem? How do you detect and fix it?</summary>

1 query for parents + N queries for lazy children. Detect with SQL logs and statement-count tests. Fix with JOIN FETCH / @EntityGraph, DTO projections, or batch fetching.
</details>

<details><summary>37. Why is JOIN FETCH with pagination on a collection a problem?</summary>

Row multiplication makes SQL-level LIMIT wrong, so Hibernate paginates in memory (HHH90003004), loading everything. Page parent ids first, then fetch.
</details>

<details><summary>38. What causes LazyInitializationException?</summary>

Accessing an uninitialized lazy association after the session closed, typically outside the transactional service method.
</details>

<details><summary>39. What is Open Session in View?</summary>

Keeps the session open through view rendering. It's enabled by default in Boot and hides lazy loading in controllers/serialization, at the cost of hidden queries and longer connection holding. Often disabled.
</details>

<details><summary>40. What is the owning side of a relationship?</summary>

The side with the FK mapping (@JoinColumn). Only it is used to write the relationship. mappedBy marks the inverse side.
</details>

<details><summary>41. CascadeType.ALL and orphanRemoval — when?</summary>

For true composition (order → order lines): children are persisted/removed with the parent, and removing from the collection deletes them. Never across aggregates.
</details>

<details><summary>42. IDENTITY vs SEQUENCE id generation?</summary>

IDENTITY uses DB auto-increment. The id is only known after the insert, so JDBC batch inserts are disabled. SEQUENCE preallocates ids (allocationSize) and allows batching.
</details>

<details><summary>43. Why Flyway (or Liquibase)?</summary>

Versioned, reviewed, repeatable schema migrations with history and checksums. ddl-auto=update is unsafe and unreviewable.
</details>

<details><summary>44. When would you not use JPA?</summary>

Reporting, bulk operations, upserts, window functions, SKIP LOCKED queues, and append-only financial entries where explicit SQL and DB-enforced rules matter more than object mapping.
</details>

## E. Transactions and concurrency (45–56)

<details><summary>45. How does @Transactional work?</summary>

A proxy + TransactionInterceptor use the PlatformTransactionManager to begin a transaction, bind the connection to the thread, and commit or roll back around the method.
</details>

<details><summary>46. Why doesn't @Transactional work on self-invocation?</summary>

Internal `this.` calls bypass the proxy. Move the method to another bean or use TransactionTemplate.
</details>

<details><summary>47. Which exceptions trigger rollback?</summary>

RuntimeException and Error by default; checked exceptions commit unless rollbackFor is set.
</details>

<details><summary>48. What is UnexpectedRollbackException?</summary>

The inner participating method threw, marking the shared transaction rollback-only. The outer method caught the exception and tried to commit, so Spring rolls back and throws.
</details>

<details><summary>49. Explain propagation REQUIRED, REQUIRES_NEW, NESTED.</summary>

Join-or-create; suspend and start an independent transaction (separate connection); savepoint within the current transaction.
</details>

<details><summary>50. What's dangerous about REQUIRES_NEW under load?</summary>

Each call needs a second connection while holding the first, which can exhaust the pool and deadlock. Its commit also survives the outer rollback.
</details>

<details><summary>51. What does readOnly = true do?</summary>

Skips Hibernate dirty checking/flushing, may mark the connection read-only, and signals intent. It's an optimisation hint.
</details>

<details><summary>52. How do you set isolation in Spring and what's Postgres's default?</summary>

@Transactional(isolation = …). Postgres defaults to Read Committed; MySQL InnoDB to Repeatable Read.
</details>

<details><summary>53. Optimistic locking in JPA?</summary>

A @Version column added to UPDATE's WHERE clause. 0 rows updated → OptimisticLockException (ObjectOptimisticLockingFailureException) → retry or 409.
</details>

<details><summary>54. Pessimistic locking in Spring Data?</summary>

@Lock(PESSIMISTIC_WRITE) on a repository query → SELECT … FOR UPDATE, with a lock-timeout hint. Take locks in a consistent order to avoid deadlocks.
</details>

<details><summary>55. How do you retry a failed optimistic transaction correctly?</summary>

From outside the transactional method (a separate bean or @Retryable ordered outside the transaction), re-reading state each attempt, with bounded attempts and backoff.
</details>

<details><summary>56. How do you make sure an event is published only if the transaction commits?</summary>

@TransactionalEventListener(AFTER_COMMIT) for in-process reactions. For reliability, the transactional outbox: persist the event in the same transaction and relay it afterwards.
</details>

## F. Security (57–66)

<details><summary>57. Authentication vs authorization?</summary>

Who you are (401 on failure) vs what you may do (403 on failure).
</details>

<details><summary>58. What is the SecurityFilterChain?</summary>

The Spring Security 6 bean defining the ordered filters, request matchers, authorization rules, session policy, CSRF/CORS and custom filters for a set of requests.
</details>

<details><summary>59. How is a password verified?</summary>

AuthenticationManager → DaoAuthenticationProvider → UserDetailsService loads the user → PasswordEncoder.matches(raw, storedHash) (BCrypt).
</details>

<details><summary>60. Walk through your JWT filter.</summary>

A OncePerRequestFilter reads the Bearer header, verifies signature/expiry/issuer, maps claims to authorities, and sets an authenticated token in the SecurityContext. On failure it clears the context and continues, so authorization returns 401.
</details>

<details><summary>61. Is a JWT encrypted? How do you revoke one?</summary>

Signed, not encrypted: the payload is readable. Revoke via short expiry + refresh tokens stored server-side, a jti deny-list, or key rotation.
</details>

<details><summary>62. hasRole vs hasAuthority?</summary>

hasRole('X') checks 'ROLE_X'; hasAuthority checks the exact string.
</details>

<details><summary>63. What does @PreAuthorize enable that URL rules can't?</summary>

Method-level and object-level decisions using SpEL and parameters, e.g. `@PreAuthorize("@access.canEdit(#orgId, authentication)")`. Requires @EnableMethodSecurity, and it's proxy-based.
</details>

<details><summary>64. What is BOLA and how do you prevent it?</summary>

Broken Object Level Authorization (OWASP API #1): accessing another user's object by changing an id. Check ownership/membership on every object access, in the service layer, and test it.
</details>

<details><summary>65. CSRF vs CORS?</summary>

CSRF: an attack that exploits automatically-sent cookies; protect cookie-based auth with tokens/SameSite. CORS: a browser policy declaring which origins may read responses. It's not a security boundary for non-browser clients.
</details>

<details><summary>66. How do you test security rules?</summary>

@WebMvcTest with the security config imported, @WithMockUser or jwt() post-processors, asserting 401/403/200 per role and endpoint, plus ownership tests in service/integration tests.
</details>

## G. Observability, caching, scheduling (67–74)

<details><summary>67. How do you correlate logs for one request?</summary>

Request-ID filter → MDC → every log line; echo it in the response header and ProblemDetail; propagate to async threads and other services. JSON logs make it queryable.
</details>

<details><summary>68. What does Actuator give you and how do you secure it?</summary>

health (liveness/readiness), info, metrics, prometheus, loggers, env… Expose the minimum, keep health public without details, protect the rest by role or on a separate port.
</details>

<details><summary>69. How do you add a custom metric?</summary>

Inject MeterRegistry and register Counters/Timers/Gauges (Micrometer). Tag carefully (low cardinality: no user ids).
</details>

<details><summary>70. How does @Cacheable work, and what are the pitfalls?</summary>

A proxy-based cache-aside keyed by SpEL. Pitfalls: self-invocation, caching entities, missing tenant in keys, eviction before commit, JDK serialization in Redis, and failures propagating when Redis is down.
</details>

<details><summary>71. How do you make the app survive a Redis outage?</summary>

A CacheErrorHandler that logs and treats errors as misses, short Redis timeouts, health groups that don't mark the app down for a cache, and a test that stops Redis.
</details>

<details><summary>72. fixedRate vs fixedDelay?</summary>

fixedRate schedules start-to-start; fixedDelay waits after completion. Cron has 6 fields in Spring (seconds first) and should set a zone.
</details>

<details><summary>73. How do you avoid duplicate scheduled jobs across instances?</summary>

ShedLock/advisory locks, or SKIP LOCKED work claiming, plus idempotent, restartable jobs.
</details>

<details><summary>74. What are @Async's limitations?</summary>

Proxy self-invocation; lost transaction/security/MDC context; exception handling; not durable; needs a bounded executor.
</details>

## H. Testing and production (75–80)

<details><summary>75. @SpringBootTest vs @WebMvcTest vs @DataJpaTest?</summary>

Full context vs web slice (controllers, advice, converters, security if imported; services mocked) vs JPA slice (repositories, Flyway, test transaction rolled back). Use slices for speed and focus.
</details>

<details><summary>76. Why Testcontainers instead of H2?</summary>

Real Postgres semantics (SQL dialect, constraints, locking, isolation, jsonb, SKIP LOCKED). H2 passes tests that fail in production, especially concurrency tests.
</details>

<details><summary>77. Why can't a @Transactional test verify concurrency?</summary>

The test transaction is never committed, so other threads can't see its data, and the rollback hides commit-time behaviour. Run concurrency tests without test-managed transactions.
</details>

<details><summary>78. How do you make a POST idempotent in Spring?</summary>

Require an Idempotency-Key header, store (key, request hash, status, response) under a unique constraint in the same transaction as the work, replay the stored response for repeats, and reject the same key with a different body (422/409). Expire keys after a TTL.
</details>

<details><summary>79. What would you check first if a Spring Boot API becomes slow under load?</summary>

Metrics: request latency by endpoint, Hikari pending connections, DB query times (pg_stat_statements), GC/CPU, thread pools. Then logs with request IDs, EXPLAIN on the slow queries, and N+1 in SQL logs. Change one thing, re-measure.
</details>

<details><summary>80. How do you run database migrations safely during a deploy?</summary>

Flyway at startup (or as a separate step) with backward-compatible migrations (expand → migrate → contract), so the old and new app versions both work during a rolling deploy. Never edit applied migrations.
</details>

## ✅ Drill checklist

- [ ] Answered all 80 aloud once (Weeks 4–6)
- [ ] Sections E and F re-drilled during LedgerX (Weeks 9–10)
- [ ] Every answer I use in interviews has a "in my project…" sentence I can back with code
