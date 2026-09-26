# 🌱 05 · Spring Boot 3 (Spring Framework 6, Spring Security 6, `jakarta.*`)

> **Goal:** build, secure, test and operate production-shaped Spring Boot services, and explain *how* they work
> underneath (proxies, auto-configuration, the filter chain, the persistence context, thread-bound transactions).
> Every project in this roadmap is a Spring Boot application, so this module is where the four projects get their backbone.

## When (just-in-time, per [ROADMAP §6](../ROADMAP.md#6-just-in-time-learning-map))

| Week | Topic files | Why then |
|---|---|---|
| 3 | [01-core-di](./01-core-di.md), [02-web-layer](./02-web-layer.md) §1–3 | Foundation: a tiny Spring Boot API with two endpoints plus tests (throwaway), so Week 4 starts on the domain |
| 4 | [03-data-jpa](./03-data-jpa.md), [04-validation-errors](./04-validation-errors.md), [05-security-jwt](./05-security-jwt.md), [06-logging-actuator](./06-logging-actuator.md) §1–2, [02-web-layer](./02-web-layer.md) (all) | **FlowGrid M1**: entities + Flyway, CRUD + validation + ProblemDetail, pagination, JWT + roles, OpenAPI, CI |
| 5 | [07-transactions](./07-transactions.md) | **FlowGrid M2**: idempotent orders, reservations under concurrency, `FOR UPDATE` vs `@Version`, Testcontainers |
| 6 | [08-caching-scheduling](./08-caching-scheduling.md) §1–2 | **FlowGrid M3**: Redis catalog cache, invalidation, degrade-to-DB |
| 7 | [08-caching-scheduling](./08-caching-scheduling.md) §3–4 | **FlowGrid M4**: scheduled low-stock alerts |
| 8 | [06-logging-actuator](./06-logging-actuator.md) (all) | **FlowGrid M5**: JSON logs → CloudWatch, health checks, metrics for the k6 baseline |
| 9–10 | [07-transactions](./07-transactions.md) (again: propagation, ordered locking, retries), [03-data-jpa](./03-data-jpa.md) §9 | **LedgerX M1–M2** |
| 13, 17 | [08-caching-scheduling](./08-caching-scheduling.md) §3 (again) | LedgerX reconciliation/scheduled payments; ForgeCI heartbeats, reapers, graceful shutdown |
| 21 | [08-caching-scheduling](./08-caching-scheduling.md) §1–2 (again) | FlagForge snapshot cache |
| 4–26 | [interview-questions](./interview-questions.md) | drill 5–10 per week, re-drill weak ones |

Prerequisites: HTTP semantics ([`../06-rest-apis/http-for-apis.md`](../06-rest-apis/http-for-apis.md)), SQL basics and joins
([`../04-sql-databases/`](../04-sql-databases/)), Maven ([`../01-java/08-maven-build.md`](../01-java/08-maven-build.md)), JUnit 5.
Testing Spring apps: [`../09-testing/spring-testing.md`](../09-testing/spring-testing.md) and [`../09-testing/testcontainers.md`](../09-testing/testcontainers.md).

## Files

| File | Covers |
|---|---|
| [`01-core-di.md`](./01-core-di.md) | IoC/DI, constructor injection, `@Component` vs `@Bean`, `@Configuration`, scopes, lifecycle, profiles, `@ConfigurationProperties`, auto-configuration via conditions |
| [`02-web-layer.md`](./02-web-layer.md) | request lifecycle, DispatcherServlet, filters vs interceptors, controllers, DTOs/records, mapping, Jackson, `RestClient` |
| [`03-data-jpa.md`](./03-data-jpa.md) | entities, relationships & owning side, fetch types, persistence context, repositories, projections, N+1, LazyInitializationException, Flyway |
| [`04-validation-errors.md`](./04-validation-errors.md) | Bean Validation, custom constraints, `@RestControllerAdvice`, ProblemDetail (RFC 9457), status mapping |
| [`05-security-jwt.md`](./05-security-jwt.md) | `SecurityFilterChain`, authn vs authz, `UserDetailsService`, BCrypt, JWT filter, roles vs authorities, `@PreAuthorize`, CORS, CSRF |
| [`06-logging-actuator.md`](./06-logging-actuator.md) | SLF4J/Logback, levels, MDC request IDs, JSON logs, Actuator endpoints & security, health groups, Micrometer |
| [`07-transactions.md`](./07-transactions.md) | `@Transactional` proxies, self-invocation, propagation, rollback rules, readOnly, isolation, optimistic vs pessimistic locking, retries, concurrency tests |
| [`08-caching-scheduling.md`](./08-caching-scheduling.md) | Spring Cache + Redis, invalidation, Redis-down resilience, `@Scheduled` in multi-instance deployments, `@Async` |
| [`interview-questions.md`](./interview-questions.md) | 80 Q&A across all of the above |

## How to learn Spring without drowning

1. **Read the topic section, then build the smallest thing that uses it**, in the Week 3 foundation API or directly in the current FlowGrid milestone.
2. **Turn on the lights:** `org.hibernate.SQL=debug`, `org.springframework.security=debug` (when working on security), `--debug` for auto-config. Spring is only "magic" until you read the logs.
3. **Break every annotation once.** Every file has a *Break it* list: remove `@Transactional`, call a proxied method from inside its class, stop Redis. Predict, run, explain.
4. **Debug with a breakpoint in the framework** at least once per topic: `TransactionInterceptor.invoke`, `DispatcherServlet.doDispatch`, your JWT filter.
5. **Explain it**: 5 sentences per topic in your notes, then the matching questions in [`interview-questions.md`](./interview-questions.md).

These files teach concepts with small, isolated examples (mostly on the TixHub practice domain). **They don't contain your
project code.** Each ends with an *Apply it* section listing what you'll build and how to verify it. The project specs are in
[`../18-projects/`](../18-projects/) (start with [`flowgrid/milestones.md`](../18-projects/flowgrid/milestones.md)).

## Official resources

- Spring Boot reference: docs.spring.io/spring-boot/ (Web → Servlet; Data → SQL; IO → Caching; Actuator)
- Spring Framework reference: docs.spring.io/spring-framework/reference/ (Core → IoC; Data Access → Transactions; Web MVC)
- Spring Security reference: docs.spring.io/spring-security/reference/ (Servlet → Architecture, Authentication, Authorization)
- Spring Data JPA reference: docs.spring.io/spring-data/jpa/reference/
- Hibernate ORM 6 user guide (hibernate.org/orm/documentation); Flyway docs (documentation.red-gate.com/flyway)
- Spring Guides (spring.io/guides): short, runnable walkthroughs
- Book (optional): *Spring in Action* (Walls), latest edition. Read the chapters matching the current week only.

## Checkpoint bars

- **[CP-4](../checkpoints/checkpoint-04.md):** from a blank project, without a tutorial: a secured CRUD API with validation, ProblemDetail errors, Flyway, and one `@WebMvcTest` + one `@DataJpaTest`.
- **[CP-8](../checkpoints/checkpoint-08.md):** explain FlowGrid's request path, transaction boundaries, locking choice and cache invalidation in a 10-minute walkthrough.
- **[CP-12](../checkpoints/checkpoint-12.md):** prove, with tests, that LedgerX transfers are atomic, idempotent and deadlock-free.

## ✅ Module mastery checklist

- [ ] Can explain the request lifecycle, auto-configuration and `@Transactional` proxies from memory
- [ ] FlowGrid M1–M5 use every file in this module; each file's *Apply it* checks pass
- [ ] Reproduced and fixed: self-invocation, N+1, LazyInitializationException, UnexpectedRollbackException, the Redis-down 500
- [ ] Wrote a JWT filter by hand and can compare it with the OAuth2 resource server
- [ ] All 80 interview questions answered aloud; weakest 15 re-drilled
- [ ] [`../17-resume-tech-defense/spring-boot.md`](../17-resume-tech-defense/spring-boot.md) drill done
