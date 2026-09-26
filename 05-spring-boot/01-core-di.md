# 01 · Spring Core — IoC, Dependency Injection, Beans, Configuration, Auto-configuration

> **Week 9** (TicketHold M1). Stack assumed throughout this module: **Java 21, Spring Boot 3.x (Spring Framework 6.x),
> Spring Security 6.x, `jakarta.*` packages**. (Spring Boot 4 / Framework 7 exist; everything here carries over, with
> minor API differences you'd look up.)

## 1. The problem Spring solves

Without a container, every class builds its own dependencies:

```java
public class BookingService {
    private final BookingRepository repo = new JdbcBookingRepository(new HikariDataSource(/* config */));
    private final Clock clock = Clock.systemUTC();
}
```

Hard to test (can't swap the repository), hard to configure (config is buried), hard to share (every service creates its own pool).

**Inversion of Control (IoC):** objects don't create their collaborators; a container creates them and **injects** them.
**Dependency Injection (DI)** is the mechanism. Spring's container is the `ApplicationContext`; the objects it manages are **beans**.

```java
@Service
public class BookingService {
    private final BookingRepository repo;
    private final Clock clock;

    public BookingService(BookingRepository repo, Clock clock) {   // constructor injection
        this.repo = repo;
        this.clock = clock;
    }
}
```

In a unit test: `new BookingService(fakeRepo, Clock.fixed(...))` — no Spring needed. That testability is the real point.

## 2. Injection styles

| Style | Example | Verdict |
|---|---|---|
| **Constructor** | `public BookingService(BookingRepository repo)` | ✅ default. Fields can be `final`, dependencies explicit, object never half-built, easy to test. With a single constructor, `@Autowired` is optional. |
| Setter | `@Autowired void setClock(Clock c)` | optional dependencies only |
| Field | `@Autowired private BookingRepository repo;` | ❌ hides dependencies, can't be `final`, needs reflection/Spring to test |

A constructor with 8 parameters is a design smell (class does too much), not a reason to switch to field injection.

## 3. Declaring beans: `@Component` vs `@Bean`

```java
// 1) Component scanning — for YOUR classes
@Component   // generic
@Service     // business logic (semantic alias of @Component)
@Repository  // persistence; also enables exception translation to DataAccessException
@Controller / @RestController   // web layer

// 2) @Bean methods — for classes you don't own, or need custom construction
@Configuration
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ObjectMapper objectMapper() {           // (Boot already provides one — example only)
        return JsonMapper.builder().findAndAddModules().build();
    }
}
```

| | `@Component` (+ stereotypes) | `@Bean` |
|---|---|---|
| Where | on the class | on a method in a `@Configuration` class |
| Who constructs | Spring calls the constructor | your method body |
| Use for | your own application classes | third-party classes (`Clock`, `RestClient`, `PasswordEncoder`), conditional/complex creation |

`@SpringBootApplication` scans its own package **and sub-packages**. Put the main class in the root package (`com.example.tickethold`), or beans in sibling packages won't be found.

### `@Configuration` proxying

In a `@Configuration` class, calling one `@Bean` method from another returns the **same singleton**, because Spring subclasses (CGLIB-proxies) the configuration class. With `@Configuration(proxyBeanMethods = false)` (what Boot's own auto-configs use), inter-method calls create new instances — inject beans as method parameters instead:

```java
@Configuration(proxyBeanMethods = false)
class ClientConfig {
    @Bean RestClient pulseClient(RestClient.Builder builder) {    // parameter injection, no inter-method call
        return builder.baseUrl("https://example.com").build();
    }
}
```

## 4. Choosing between multiple candidates

```java
public interface NotificationSender { void send(Alert a); }

@Component @Primary class EmailSender implements NotificationSender { … }
@Component("webhook") class WebhookSender implements NotificationSender { … }

@Service
class AlertService {
    AlertService(NotificationSender defaultSender,                                  // EmailSender (@Primary)
                 @Qualifier("webhook") NotificationSender webhook,                  // by name
                 List<NotificationSender> all,                                      // every implementation
                 Map<String, NotificationSender> byName) { … }                      // bean name -> bean
}
```

No `@Primary`/`@Qualifier` with two candidates → `NoUniqueBeanDefinitionException` at startup. Injecting a `List<Strategy>` is a clean way to do the Strategy pattern (Ledger's rules engine, in Spring form).

## 5. Bean scopes

| Scope | Instances | Use |
|---|---|---|
| **singleton** (default) | one per ApplicationContext | stateless services, repositories, controllers — ~all beans |
| prototype | new one per injection/lookup | stateful helpers (rare) |
| request | one per HTTP request | request-scoped data (web only) |
| session | one per HTTP session | rarely in stateless APIs |
| application | one per ServletContext | rare |

**Consequences**

- Singletons are shared across **all request threads** → they must be **stateless** or thread-safe. A mutable field in a `@Service` (`private int counter;`) is a race condition.
- A prototype injected into a singleton is created **once** (at injection) — it doesn't become "new per call". Use `ObjectProvider<T>`:

```java
@Service
class ReportService {
    private final ObjectProvider<ReportBuilder> builders;       // prototype-scoped bean
    ReportService(ObjectProvider<ReportBuilder> builders) { this.builders = builders; }
    Report build() { return builders.getObject().withTitle("Monthly").build(); }   // fresh each call
}
```

## 6. Bean lifecycle (the parts you'll use)

1. Instantiate (constructor) → 2. inject dependencies → 3. `BeanPostProcessor`s run (this is where **proxies** for `@Transactional`, `@Async`, `@Cacheable` are created) → 4. `@PostConstruct` (`jakarta.annotation.PostConstruct`) → bean ready → … → `@PreDestroy` on shutdown.

Knowing step 3 explains the self-invocation pitfall in [`07-transactions.md`](./07-transactions.md): callers get the **proxy**, but `this.method()` inside the bean bypasses it.

Run code at startup: implement `ApplicationRunner`/`CommandLineRunner`, or `@EventListener(ApplicationReadyEvent.class)`.

## 7. Configuration: properties, profiles, `@ConfigurationProperties`

```yaml
# src/main/resources/application.yml
spring:
  application:
    name: tickethold
tickethold:
  hold:
    ttl: 10m
    max-seats-per-hold: 6
  jwt:
    issuer: tickethold
    expiry: 15m
---
spring:
  config:
    activate:
      on-profile: dev
logging:
  level:
    org.hibernate.SQL: debug
```

```java
@ConfigurationProperties(prefix = "tickethold.hold")
@Validated
public record HoldProperties(
        @NotNull Duration ttl,                    // "10m" -> Duration.ofMinutes(10)
        @Min(1) @Max(20) int maxSeatsPerHold) {}  // kebab-case in YAML -> camelCase

@SpringBootApplication
@ConfigurationPropertiesScan                      // registers all @ConfigurationProperties records
public class TicketHoldApplication {
    public static void main(String[] args) { SpringApplication.run(TicketHoldApplication.class, args); }
}

@Service
class HoldService {
    private final HoldProperties props;
    HoldService(HoldProperties props) { this.props = props; }
}
```

- Prefer typed `@ConfigurationProperties` (validated, grouped, IDE-completable with `spring-boot-configuration-processor`) over scattered `@Value("${tickethold.hold.ttl}")`.
- **Property source precedence** (high → low, simplified): command-line args → environment variables (`TICKETHOLD_HOLD_TTL=5m`, relaxed binding) → `application-{profile}.yml` → `application.yml`. That's how Docker/AWS override config without rebuilding.
- **Profiles**: `spring.profiles.active=dev` (env `SPRING_PROFILES_ACTIVE=prod`). Beans can be profile-specific: `@Profile("dev")`.
- **Secrets never in `application.yml` in Git.** Use env vars / a secrets manager (Week 21).

## 8. Auto-configuration — how Boot "just works" ⭐

`@SpringBootApplication` = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`.

`@EnableAutoConfiguration` loads candidate configuration classes listed in every jar's
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Each is guarded by **conditions**:

```java
// simplified shape of Boot's DataSourceAutoConfiguration
@AutoConfiguration
@ConditionalOnClass({ DataSource.class, EmbeddedDatabaseType.class })   // only if JDBC is on the classpath
@EnableConfigurationProperties(DataSourceProperties.class)              // binds spring.datasource.*
public class DataSourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataSource.class)                           // back off if YOU defined one
    DataSource dataSource(DataSourceProperties props) { … }              // creates a HikariDataSource
}
```

| Condition | Matches when |
|---|---|
| `@ConditionalOnClass` | a class is on the classpath (you added a starter) |
| `@ConditionalOnMissingBean` | you haven't defined that bean yourself → **your beans win** |
| `@ConditionalOnProperty` | a property has a value (`spring.cache.type=redis`) |
| `@ConditionalOnBean` | another bean exists |
| `@ConditionalOnWebApplication` | it's a servlet/reactive web app |

So: adding `spring-boot-starter-data-jpa` puts Hibernate + HikariCP on the classpath → conditions match → Boot creates `DataSource`, `EntityManagerFactory`, `JpaTransactionManager`, repositories — configured from `spring.datasource.*`/`spring.jpa.*`. Define your own `DataSource` bean and Boot's backs off.

**See what happened:** run with `--debug` (or `debug=true`) for the **Conditions Evaluation Report** (positive/negative matches), or Actuator's `/actuator/conditions` endpoint. Exclude one: `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)`.

**Starters** are dependency bundles (`spring-boot-starter-web` = Spring MVC + Jackson + embedded Tomcat + validation deps…), versions managed by the Boot parent/BOM — which is why you don't write versions for them in `pom.xml`.

## 9. Project layout (TicketHold M1)

```
com.example.tickethold
├── TicketHoldApplication.java
├── config/            (SecurityConfig, properties records, Clock bean)
├── event/             (EventController, EventService, EventRepository, Event, dto/)
├── venue/
├── booking/
└── common/            (error handling, logging filter)
```

Package **by feature** (event/, booking/) scales better than by layer (controllers/, services/). Layers still exist inside each feature: controller → service → repository.

## 10. 🔨 Break it

1. Move a `@Service` to a package outside the main class's package. Start the app — read the `NoSuchBeanDefinitionException` / "Parameter 0 of constructor … required a bean of type …" message.
2. Create two beans implementing the same interface, no `@Primary`. Read `NoUniqueBeanDefinitionException`.
3. Make two services depend on each other via constructors. Read the circular-reference error (Boot forbids circular references by default since 2.6). Fix by redesigning (extract a third class), not by `@Lazy`.
4. Add `private int requestCount;` to a singleton controller, increment it per request, hit it with `ab`/`hey`/k6 using 50 concurrent requests. Compare count vs requests sent.
5. Inject a prototype bean into a singleton and print its `hashCode()` per request. Fix with `ObjectProvider`.
6. Set `tickethold.hold.max-seats-per-hold: 0` with `@Validated` present — startup fails. Why is failing at startup a *good* thing?
7. Run with `--debug`, find `DataSourceAutoConfiguration` in the report. Then define your own `DataSource` bean and find it under negative matches.

## 11. 🐞 Debugging tips

- Startup failures: scroll to the **first** `Caused by:` and the "APPLICATION FAILED TO START / Description / Action" block — Boot's failure analyzers usually tell you the fix.
- "Which bean got injected?" → set a breakpoint in the constructor, or Actuator `/actuator/beans`.
- "Why is Boot configuring X?" → `--debug` conditions report or `/actuator/conditions`.
- "Which value won for a property?" → `/actuator/env/<property.name>` (secure it! — [`06-logging-actuator.md`](./06-logging-actuator.md)).

## 12. 🎤 Interview Q&A

<details><summary>What is IoC / dependency injection, and why use it?</summary>

Objects receive their dependencies from outside (the container) instead of constructing them. Benefits: loose coupling to interfaces, easy substitution in tests, centralized configuration and lifecycle, and cross-cutting features via proxies (transactions, caching, security).
</details>

<details><summary>Why is constructor injection preferred?</summary>

Dependencies are explicit and required, fields can be final (immutable, thread-safe publication), the object can't exist half-initialized, circular dependencies surface immediately, and unit tests can call the constructor without Spring.
</details>

<details><summary>@Component vs @Bean?</summary>

@Component marks your class for classpath scanning; Spring calls its constructor. @Bean is a factory method in a @Configuration class, used for third-party classes or custom construction logic.
</details>

<details><summary>What bean scopes exist and what's the default?</summary>

Singleton (default, one per context), prototype (new per injection/lookup), and web scopes request, session, application. Singletons are shared across threads so must be stateless or thread-safe.
</details>

<details><summary>How does Spring Boot auto-configuration work?</summary>

@EnableAutoConfiguration imports auto-configuration classes listed in `AutoConfiguration.imports` files on the classpath. Each is guarded by conditions (@ConditionalOnClass, @ConditionalOnMissingBean, @ConditionalOnProperty…), so beans are created only when relevant libraries are present and you haven't defined your own. Inspect with --debug or /actuator/conditions.
</details>

<details><summary>What does @SpringBootApplication do?</summary>

Combines @SpringBootConfiguration (a @Configuration), @EnableAutoConfiguration, and @ComponentScan of the annotated class's package and sub-packages.
</details>

<details><summary>@Value vs @ConfigurationProperties?</summary>

@Value injects single properties via placeholders/SpEL. @ConfigurationProperties binds a whole prefix to a typed (record) object with relaxed binding, validation and metadata. Prefer the latter for anything beyond one or two values.
</details>

<details><summary>How do profiles work?</summary>

Profiles activate profile-specific config files/documents (`application-prod.yml`, `spring.config.activate.on-profile`) and @Profile-annotated beans. Activate via `spring.profiles.active` (property, env var SPRING_PROFILES_ACTIVE, CLI arg).
</details>

<details><summary>What happens if two beans match an injection point?</summary>

NoUniqueBeanDefinitionException at startup, unless one is @Primary, the injection point uses @Qualifier, or the parameter name matches a bean name. Or inject List/Map to get all of them.
</details>

## ✅ Mastery checklist

- [ ] TicketHold M1 uses constructor injection everywhere; no field injection
- [ ] Wrote a `@ConfigurationProperties` record with validation and used it
- [ ] Ran `--debug` and can explain one positive and one negative auto-config match
- [ ] Reproduced the singleton-state race and the prototype-in-singleton pitfall
- [ ] Can explain IoC, scopes, and auto-configuration out loud in under 2 minutes each
