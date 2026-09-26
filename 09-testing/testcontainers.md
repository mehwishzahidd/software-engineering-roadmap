# Testcontainers

> **Week 5** (FlowGrid M2: the reservation concurrency test), **Week 10** (LedgerX: concurrent transfers),
> **Week 18** (ForgeCI M5: Postgres + Redis, chaos-style tests). Testcontainers starts real, throwaway Docker containers
> from your tests, so integration tests hit **the same PostgreSQL 16 and Redis 7** you run in production.
> Prereq: Docker running locally (`docker info` works). Docker basics: [11-docker/](../11-docker/README.md).

---

## 1. Why

| Approach | Problem |
|---|---|
| H2 in Postgres mode | different SQL, types (`jsonb`, `timestamptz`), locking (`FOR UPDATE` semantics), triggers — false greens |
| A shared dev database | tests pollute each other and your data; not reproducible in CI |
| Mocking repositories | the query and the lock never execute |
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
> `org.testcontainers.postgresql.PostgreSQLContainer`, no generic parameter). Concepts are identical — check the docs
> for the major version your Boot version manages.

---

## 3. The simplest correct test: `@Testcontainers` + `@Container` + `@ServiceConnection`

```java
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class SkuRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired SkuRepository skus;

    @Test
    void findsByCodeCaseInsensitively() {
        skus.save(TestData.sku("BOLT-M8-50"));
        assertThat(skus.findByCodeIgnoreCase("bolt-m8-50")).isPresent();
    }
}
```

| Piece | Effect |
|---|---|
| `@Testcontainers` | JUnit extension that starts/stops fields annotated `@Container` |
| `@Container` on a **static** field | one container per test **class** (instance field → one per test method: slow) |
| `@ServiceConnection` (Boot 3.1+) | Boot creates connection details from the container → `spring.datasource.*` wired automatically |
| `"postgres:16-alpine"` | **pin the tag** to match production (RDS PostgreSQL 16) |

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

You'll see this in many codebases and tutorials. Still needed for properties without a service-connection integration.

---

## 4. Share containers across the suite

Per-class containers restart Postgres for every test class (≈ 2–5 s each). Two common patterns:

### A. `@TestConfiguration` beans (Boot 3.1+) — recommended

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection(name = "redis")          // Boot wires spring.data.redis.* from this
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }
}
```

```java
@SpringBootTest
@Import(TestcontainersConfig.class)
class AllocationServiceIT { /* ... */ }

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
class InventoryLevelRepositoryIT { /* ... */ }
```

Container beans start with the context and — because Spring caches contexts with identical configuration — are shared by
every test class using the same setup. FlowGrid needs Redis from M3 (catalog cache), ForgeCI from M2 (queue).

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

| Strategy | When |
|---|---|
| `@Transactional` rollback (default in `@DataJpaTest`) | single-thread repository tests |
| Truncate tables before each test (`@Sql` or `JdbcTemplate`) | `@SpringBootTest` with `RANDOM_PORT`, multi-threaded tests |
| Unique data per test (random SKU codes, emails) | tests that just need "some" data |
| Never: rely on test order | — |

```java
@Sql(statements = "TRUNCATE reservation, inventory_level, sku, warehouse RESTART IDENTITY CASCADE",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
```

---

## 6. Concurrency tests: the core evidence

FlowGrid M2's acceptance criterion: *N threads try to reserve the last unit → exactly one succeeds.* LedgerX M2's: *balance
$500, two concurrent $400 transfers → exactly one succeeds, balance never negative.* Only a real database with real
transactions can prove these. The reusable part is the **harness** — release all threads at once and collect outcomes:

```java
public final class Concurrently {
    /** Runs the task on n threads released simultaneously; returns each thread's outcome (null = success). */
    public static List<Throwable> run(int n, IntConsumerWithException task) throws InterruptedException {
        var ready = new CountDownLatch(n);
        var go = new CountDownLatch(1);
        var outcomes = Collections.synchronizedList(new ArrayList<Throwable>());
        try (ExecutorService pool = Executors.newFixedThreadPool(n)) {   // AutoCloseable since Java 19: close() waits
            for (int i = 0; i < n; i++) {
                int id = i;
                pool.submit(() -> {
                    ready.countDown();
                    try { go.await(); task.accept(id); outcomes.add(null); }
                    catch (Throwable t) { outcomes.add(t); }
                });
            }
            ready.await();
            go.countDown();
        }
        return outcomes;
    }
    @FunctionalInterface public interface IntConsumerWithException { void accept(int i) throws Exception; }
}
```

Then **you** write the test for your design. Describe it first:

- Given: one SKU, one warehouse, `onHand = 1`, no reservations (inserted via repositories, committed — no `@Transactional` on the test).
- When: 20 threads call the real `ReservationService.reserve(...)` for 1 unit each via `Concurrently.run(20, …)`.
- Then: exactly 1 `null` outcome; 19 outcomes are your *expected* rejection type (e.g. `InsufficientStockException`,
  or `ObjectOptimisticLockingFailureException` if you compare with `@Version`); `reserved == 1` in the DB; no unexpected exception types.

LedgerX's version: two threads transfer $400 from a $500 wallet; assert one success, one `InsufficientFundsException`,
final balance $100, and the ledger invariant (sum of entries per journal transaction = 0) still holds.

Notes:
- No `@Transactional` on these tests: each thread must run and commit its own transaction.
- **Break it:** remove the protection (`FOR UPDATE` / `@Version` / lock ordering) and run the test with `@RepeatedTest(20)`.
  You'll see more than one success at least sometimes. That red run, and why it happened, is your interview story.
- For LedgerX, also try two transfers in **opposite directions** (A→B and B→A) without lock ordering and observe the
  deadlock Postgres detects (`40P01`). Then order locks by account id and watch it disappear.

---

## 7. Chaos-style tests (Week 18, ForgeCI)

Testcontainers gives you handles to the infrastructure, so you can break it on purpose:

```java
redis.stop();                                              // Redis disappears mid-test
// assert: API returns a clear error / worker stops dequeuing / FlowGrid serves catalog from DB (degrade, don't crash)
redis.start();                                             // note: a restarted container may get a NEW mapped port
```

Useful scenarios (describe expected behaviour first, then assert it): worker killed between dequeue and ack → job
recovered after lease expiry; Postgres unavailable during log append → chunks retried or buffered, no duplicates;
duplicate webhook delivery → one build. Use Awaitility (`await().atMost(10, SECONDS).until(...)`) instead of `sleep`.

Toxiproxy (a Testcontainers module) can inject latency and dropped connections between your app and Redis/Postgres —
optional, but good for timeout behaviour.

---

## 8. Reuse for fast local runs

```java
new PostgreSQLContainer<>("postgres:16-alpine").withReuse(true);
```

```properties
# ~/.testcontainers.properties  (on your machine only)
testcontainers.reuse.enable=true
```

- Reuse only activates when the property is set **and** `withReuse(true)` is used → CI (no property) still gets fresh containers.
- Reused containers keep data between runs → your tests must clean up (§5).
- Reused containers aren't removed automatically: `docker ps` / `docker rm -f` them when done.
- A local convenience — correctness must never depend on it.

### Dev-time services (Boot 3.1+)

```java
// src/test/java/.../TestFlowGridApplication.java — run the app locally against containers
public class TestFlowGridApplication {
    public static void main(String[] args) {
        SpringApplication.from(FlowGridApplication::main).with(TestcontainersConfig.class).run(args);
    }
}
```

Alternatively, Boot's Docker Compose support (`spring-boot-docker-compose`) starts services from `compose.yaml`.

---

## 9. CI considerations

- **GitHub Actions `ubuntu-latest`** runners have Docker, so Testcontainers works without extra setup ([13-cicd/github-actions.md](../13-cicd/github-actions.md)).
- Name container tests `*IT` and run them via Failsafe in `mvn verify`; keep `mvn test` fast.
- Pin image tags (`postgres:16-alpine`, `redis:7-alpine`) → reproducible builds.
- Hosted runners start fresh, so images are pulled every run (tens of seconds). Keep images small (`-alpine`).
- Ryuk (the resource-reaper container) cleans up after crashes; don't disable it unless your CI forbids it.
- Concurrency tests are the likeliest to flake in CI (fewer cores, slower disks). If one flakes, the test is telling you
  something — investigate before adding retries.
- Docker Desktop alternatives (Colima, Podman, Rancher Desktop) may need `DOCKER_HOST` configuration — see the
  Testcontainers docs, "Supported Docker environments".

---

## Break it

1. Change the image to `postgres:12-alpine` with a migration that uses `gen_random_uuid()` (built in only from PG 13). Watch Flyway fail. Tags matter.
2. Make the `@Container` field non-static. Count container starts in the logs.
3. Stop Docker and run `mvn verify`. Read the error ("Could not find a valid Docker environment") so you recognise it in CI.
4. Add `@Transactional` to the concurrency test. What happens to the other threads' visibility and why?
5. Replace Testcontainers with H2 for a repository test using `FOR UPDATE SKIP LOCKED` or `jsonb`. Observe.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `latest` image tag | pin version |
| Non-static `@Container` | `static` (or a shared config bean) |
| `@DataJpaTest` without `Replace.NONE` | add `@AutoConfigureTestDatabase(replace = NONE)` |
| Tests depending on leftover data from reuse | clean state per test |
| Running container tests in `mvn test` | `*IT` + Failsafe |
| `@Transactional` on a concurrency test | let threads commit |
| Asserting "no exception" in a concurrency test | assert exact success count **and** the expected failure types **and** DB state |

---

## Interview Q&A

<details><summary>Why Testcontainers instead of H2?</summary>

H2 isn't Postgres: different SQL features, types, locking and trigger behaviour, so tests can pass on H2 and fail in
production. Testcontainers runs the real Postgres 16 image for each test run, in CI too, at the cost of a few seconds'
startup — which I amortise by sharing one container across the suite.
</details>

<details><summary>What does <code>@ServiceConnection</code> do?</summary>

Since Boot 3.1, it tells Spring Boot to derive connection details (JDBC URL, credentials; Redis host/port) from the
container, replacing manual `@DynamicPropertySource` wiring.
</details>

<details><summary>How did you prove there's no overselling?</summary>

A `@SpringBootTest` against Testcontainers Postgres releases 20 threads simultaneously with a latch to reserve the last
unit and asserts exactly one success, 19 expected rejections, and `reserved = 1` in the database. I watched it fail with
the row lock removed, then pass with it. (Say this only once you've done it.)
</details>

<details><summary>How do you keep container tests fast?</summary>

One container per JVM/context shared across classes, small pinned images, data cleanup instead of restarts, reuse locally,
and running them only in `mvn verify` via Failsafe.
</details>

---

## Mastery checklist

- [ ] `TestcontainersConfig` with Postgres (and Redis from FlowGrid M3 / ForgeCI M2) using `@ServiceConnection`.
- [ ] All repository tests run on Postgres 16 via Testcontainers; no H2 in any project.
- [ ] FlowGrid N-threads-one-unit test passes, and I've seen it fail with protection removed.
- [ ] LedgerX $500/$400/$400 test passes; the deadlock experiment done and explained.
- [ ] (W18) At least two chaos-style ForgeCI tests (Redis stop, worker loss).
- [ ] `mvn verify` runs `*IT` in GitHub Actions and is green.
