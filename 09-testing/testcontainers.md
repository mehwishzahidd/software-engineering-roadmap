# Testcontainers

> **Week 12** · P2 TicketHold M4–M5, then P3 TeamBoard and P4 PulseWatch (Postgres + Redis).
> Testcontainers starts real, throwaway Docker containers from your tests. Your integration tests hit **the same
> PostgreSQL 16** you run in production instead of an in-memory imitation.
> Prereq: Docker running locally (`docker info` works). Docker basics: [11-docker/](../11-docker/README.md).

---

## 1. Why

| Approach | Problem |
|---|---|
| H2 in Postgres mode | different SQL, types (`jsonb`, `timestamptz`), locking, constraint timing — false greens |
| A shared dev database | tests pollute each other and your data; not reproducible in CI |
| Mocking repositories | the query is never executed |
| **Testcontainers** | real engine, fresh per run, same in CI; costs a few seconds of startup |

---

## 2. Dependencies (Spring Boot 3.1+)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
```

Versions are managed by the Spring Boot parent (Boot 3.x manages Testcontainers 1.x).

> **Version note:** Testcontainers 2.x (used by Spring Boot 4) renamed modules (e.g. `testcontainers-postgresql`,
> `testcontainers-junit-jupiter`) and moved container classes into per-module packages (e.g.
> `org.testcontainers.postgresql.PostgreSQLContainer`, no generic parameter). Concepts are identical — check the
> docs for the major version your Boot version manages.

---

## 3. The simplest correct test: `@Testcontainers` + `@Container` + `@ServiceConnection`

```java
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class EventRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired EventRepository events;

    @Test
    void savesAndFindsUpcomingEvents() {
        events.save(TestData.event("Jazz Night", Instant.parse("2026-06-01T19:00:00Z")));
        assertThat(events.findUpcoming(Instant.parse("2026-05-01T00:00:00Z"))).hasSize(1);
    }
}
```

What each piece does:

| Piece | Effect |
|---|---|
| `@Testcontainers` | JUnit extension that starts/stops fields annotated `@Container` |
| `@Container` on a **static** field | one container per test **class** (instance field → one per test method: slow) |
| `@ServiceConnection` (Boot 3.1+) | Boot creates `JdbcConnectionDetails` from the container → `spring.datasource.url/username/password` are wired automatically |
| `"postgres:16-alpine"` | **pin the tag** to match production (P4 uses RDS PostgreSQL 16) |

Flyway runs against the container on context startup, so migrations are tested too.

### Before Boot 3.1: `@DynamicPropertySource`

```java
@DynamicPropertySource
static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
}
```

You'll see this in many existing codebases and tutorials. Still needed for things without a service-connection
integration (e.g. custom properties).

---

## 4. Share one container across the suite

Per-class containers restart Postgres for every test class (≈2–5 s each). Two common patterns:

### A. `@TestConfiguration` bean (Boot 3.1+) — recommended

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection(name = "redis")          // PulseWatch: Boot wires spring.data.redis.* from this
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }
}
```

```java
@SpringBootTest
@Import(TestcontainersConfig.class)
class MonitorServiceIT { /* ... */ }

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
class CheckResultRepositoryIT { /* ... */ }
```

Container beans are started with the context and — because Spring caches contexts with identical configuration —
shared by every test class that uses the same setup.

### B. Singleton container in an abstract base class

```java
public abstract class AbstractPostgresIT {
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static { POSTGRES.start(); }   // started once per JVM; Ryuk removes it when the JVM exits
}
```

No `@Container`/`@Testcontainers` here on purpose: the JUnit extension would stop it after the first class.

---

## 5. Test isolation with a shared database

A shared container means shared data. Options:

| Strategy | When |
|---|---|
| `@Transactional` rollback (default in `@DataJpaTest`) | single-thread repository tests |
| Truncate tables in `@BeforeEach` (`@Sql` script or `JdbcTemplate`) | `@SpringBootTest` with `RANDOM_PORT`, multi-threaded tests |
| Unique data per test (random emails/names) | tests that just need "some" data |
| Never: rely on test order | — |

```java
@Sql(statements = "TRUNCATE booking, hold, seat, event, venue RESTART IDENTITY CASCADE",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
```

---

## 6. The TicketHold concurrency test

The whole point of P2: prove that N simultaneous holds on one seat produce **exactly one** winner.
This can only be proven against a real database with real transactions.

```java
@SpringBootTest
@Import(TestcontainersConfig.class)
@Sql(statements = "TRUNCATE booking, hold, seat, event, venue RESTART IDENTITY CASCADE")
class SeatHoldConcurrencyIT {

    @Autowired HoldService holdService;
    @Autowired TestDataFactory data;           // your helper that inserts venue/event/seat and returns ids

    @Test
    void manyConcurrentHolds_exactlyOneWins() throws Exception {
        long seatId = data.createEventWithOneSeat();
        int threads = 20;
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var failures = new ConcurrentLinkedQueue<Throwable>();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {   // ExecutorService is AutoCloseable since Java 19
            for (int i = 0; i < threads; i++) {
                long userId = 1000 + i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();                       // release all threads at once
                        holdService.hold(seatId, userId);
                        successes.incrementAndGet();
                    } catch (Throwable t) {
                        failures.add(t);
                    }
                    return null;
                });
            }
            ready.await();
            start.countDown();
        }                                                    // close() waits for all tasks

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures).hasSize(threads - 1)
            .allSatisfy(t -> assertThat(t).isInstanceOfAny(
                SeatUnavailableException.class,
                ObjectOptimisticLockingFailureException.class,
                DataIntegrityViolationException.class));
    }
}
```

Notes:
- No `@Transactional` on this test: each thread must run and commit its own transaction.
- **Break it:** remove the protection (unique partial index on active holds / `@Version` / `SELECT … FOR UPDATE`) and
  run the test 10×. You'll see `successes > 1` at least sometimes. That red run is your interview story.
- The accepted exception list must match *your* design (which mechanism rejects the losers). Be able to explain it.

---

## 7. Reuse for fast local runs

By default containers are removed after the run. For a faster local loop, reuse a container between runs:

```java
new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);
```

```properties
# ~/.testcontainers.properties  (on your machine only)
testcontainers.reuse.enable=true
```

- Reuse only activates when the property is set **and** `withReuse(true)` is used → CI (no property) still gets fresh containers.
- Reused containers keep data between runs → your tests must clean up (§5).
- Reused containers are not removed automatically: `docker ps` / `docker rm -f` them when done.
- Treat reuse as a local convenience, not something correctness depends on.

### Dev-time services (Boot 3.1+)

You can run the *application* locally against Testcontainers:

```java
// src/test/java/.../TestTicketHoldApplication.java
public class TestTicketHoldApplication {
    public static void main(String[] args) {
        SpringApplication.from(TicketHoldApplication::main).with(TestcontainersConfig.class).run(args);
    }
}
```

Alternatively, Boot's Docker Compose support starts services from `compose.yaml`. Either way, no manual DB setup.

---

## 8. CI considerations

- **GitHub Actions `ubuntu-latest`** runners have Docker, so Testcontainers works without extra setup ([13-cicd/github-actions.md](../13-cicd/github-actions.md)).
- Name container tests `*IT` and run them via Failsafe in `mvn verify`; keep `mvn test` fast.
- Pin image tags (`postgres:16-alpine`, not `latest`) → reproducible builds.
- First run pulls images (≈ tens of seconds); later runs on the same runner are cached only if the runner persists — hosted runners start fresh.
- Ryuk (the resource reaper container) cleans up after crashes; don't disable it unless your CI forbids privileged containers.
- If CI is slow: share containers (§4), avoid per-method containers, and parallelise at the **class** level only after isolating data.
- macOS/Windows with Docker Desktop, Colima or Podman may need `DOCKER_HOST` or socket configuration — see the Testcontainers docs "Supported Docker environments".

---

## Break it

1. Change the image to `postgres:12-alpine` and use a PG 13+ feature in a migration (e.g. `gen_random_uuid()` without `pgcrypto`). Watch Flyway fail. Tags matter.
2. Make the `@Container` field non-static. Count container starts in the logs.
3. Stop Docker and run `mvn verify`. Read the error ("Could not find a valid Docker environment"). Now you know what it looks like in CI.
4. Add `@Transactional` to `SeatHoldConcurrencyIT`. What happens to the other threads' visibility and why?
5. Replace Testcontainers with H2 for `HoldRepositoryTest` using a Postgres-specific query (`FOR UPDATE SKIP LOCKED`, `jsonb`). Observe.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `latest` image tag | pin version |
| Non-static `@Container` | `static` (or a shared config bean) |
| `@DataJpaTest` without `Replace.NONE` (silently H2 if on classpath, or failure) | add `@AutoConfigureTestDatabase(replace = NONE)` |
| Tests depending on leftover data from reuse | clean state per test |
| Running container tests in `mvn test` | `*IT` + Failsafe |
| `@Transactional` concurrency test | let threads commit |

---

## Interview Q&A

<details><summary>Why Testcontainers instead of H2?</summary>

H2 isn't Postgres: different SQL features, types, locking and constraint behaviour, so tests can pass on H2 and fail in
production. Testcontainers runs the real Postgres 16 image in Docker for each test run, in CI too, at the cost of a few
seconds' startup — which I amortise by sharing one container across the suite.
</details>

<details><summary>What does <code>@ServiceConnection</code> do?</summary>

Since Boot 3.1, it tells Spring Boot to derive connection details (JDBC URL, credentials; Redis host/port) from the
container, replacing the manual `@DynamicPropertySource` wiring.
</details>

<details><summary>How did you prove there's no double-booking?</summary>

A `@SpringBootTest` against Testcontainers Postgres releases 20 threads simultaneously with a latch to hold the same
seat and asserts exactly one success and 19 expected failures. I watched it fail without the locking mechanism, then pass with it.
</details>

<details><summary>How do you keep container tests fast?</summary>

One container per JVM/context shared across classes, pinned small images (`-alpine`), data cleanup instead of restarts,
reuse locally, and running them only in `mvn verify` via Failsafe.
</details>

---

## Mastery checklist

- [ ] `TestcontainersConfig` with Postgres (and Redis for P4) using `@ServiceConnection`.
- [ ] All repository tests run on Postgres 16 via Testcontainers; H2 removed from the project.
- [ ] `SeatHoldConcurrencyIT` passes, and I've seen it fail with protection removed.
- [ ] `mvn verify` runs `*IT` in GitHub Actions and is green.
- [ ] I can explain `@Container` static vs instance, reuse, and Ryuk.
