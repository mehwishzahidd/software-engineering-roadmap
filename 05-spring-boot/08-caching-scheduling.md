# 08 · Spring Cache (+ Redis), `@Scheduled`, `@Async`

> **Week 6** (FlowGrid M3: catalog cache-aside + invalidation, degrade to DB when Redis is down) → **Week 7** (FlowGrid M4:
> scheduled low-stock alerts) → **Week 13** (LedgerX: reconciliation job, scheduled payments) → **Week 17** (ForgeCI:
> heartbeats, orphan recovery, timeouts) → **Week 21** (FlagForge: config snapshot cache).
> Redis mechanics: [`../04-sql-databases/redis.md`](../04-sql-databases/redis.md). Caching strategy: [`../15-system-design/caching.md`](../15-system-design/caching.md).

## 1. The cache abstraction

Spring's cache annotations implement **cache-aside** declaratively through a proxy, with a pluggable store (Caffeine
in-process, Redis distributed, …).

```java
@SpringBootApplication
@EnableCaching
public class App { … }

@Service
public class VenueQueries {

    @Cacheable(cacheNames = "venue", key = "#id")                      // miss -> run method, store; hit -> skip method
    public VenueView get(long id) { return repo.findView(id).orElseThrow(() -> new NotFoundException("venue", id)); }

    @Cacheable(cacheNames = "venueSearch", key = "#city + ':' + #page", unless = "#result.isEmpty()")
    public List<VenueView> byCity(String city, int page) { … }

    @CachePut(cacheNames = "venue", key = "#result.id()")               // always run, then overwrite the entry
    public VenueView rename(long id, String name) { … }

    @CacheEvict(cacheNames = "venue", key = "#id")                     // remove the entry
    public void delete(long id) { … }

    @CacheEvict(cacheNames = "venueSearch", allEntries = true)         // coarse invalidation of a derived cache
    public void onVenueChanged() { }
}
```

| Annotation / attribute | Purpose |
|---|---|
| `@Cacheable` | read-through (cache-aside) |
| `@CachePut` | update the cache with the method result |
| `@CacheEvict` (`allEntries`, `beforeInvocation`) | invalidate |
| `key` (SpEL) | build the key from parameters; default = all params |
| `condition` / `unless` | cache only if (before call) / skip storing if (after call) |
| `sync = true` | only one thread computes a missing key **per JVM** (stampede protection within one instance) |

### Pitfalls ⭐

1. **Proxy-based:** calling a `@Cacheable` method from the same class bypasses the cache (same as `@Transactional`).
2. **Cache DTOs, not entities.** Entities drag lazy proxies and persistence state into the cache and serialize badly.
3. **Mutable cached objects with an in-process cache:** the caller gets the *same instance* and can mutate the cached value. Cache immutable records.
4. **Evict after commit.** If you evict and the transaction then rolls back, or a concurrent reader re-caches the old row before your commit, the cache serves stale data until the TTL. Evict via `@TransactionalEventListener(phase = AFTER_COMMIT)`, and keep a TTL as the safety net.
5. **Key design:** include every parameter that changes the result (tenant/org id! locale, page, filters). A missing tenant in the key is a data leak between customers.
6. **Null results:** caching "not found" briefly protects the DB from repeated misses (penetration), but forces a decision on how long an absence may be served.

## 2. Redis as the cache store

```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 10m          # default TTL for all caches: always set one
      key-prefix: "app:"
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 200ms             # fail fast so a dead Redis doesn't stall requests
```

```java
@Bean
RedisCacheManagerBuilderCustomizer cacheConfig() {
    var json = RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer());
    return builder -> builder
        .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10)).serializeValuesWith(json))
        .withCacheConfiguration("venueSearch",
            RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(60)).serializeValuesWith(json));
}
```

The default JDK serialization writes binary blobs (`\xac\xed…`) that are unreadable in `redis-cli` and break when classes
change. Use JSON. `GenericJackson2JsonRedisSerializer` stores type info in the JSON. Be deliberate about that when classes
are renamed.

### Redis down → degrade, don't fail ⭐ (FlowGrid M3 requirement)

By default a Redis connection error in `@Cacheable` **propagates** and your endpoint returns 500, even though Postgres is
fine. Install an error handler that logs and treats failures as misses:

```java
@Configuration
class CacheResilienceConfig implements CachingConfigurer {
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(true);   // log (with stack trace) and continue: get-error -> treated as miss
    }
}
```

Then **test it**: stop the Redis container during an integration test (or point to a dead port) and assert that the
endpoint still returns 200. Add a metric/log so "running degraded" is visible. Also check your health groups
([`06-logging-actuator.md`](./06-logging-actuator.md#health-indicators)).

**Multi-instance stampede:** `sync = true` only coordinates threads in one JVM. Across N instances, use TTL jitter,
refresh-ahead, or a short Redis lock around recomputation (FlagForge M4's "stampede protection").

## 3. `@Scheduled`

```java
@SpringBootApplication
@EnableScheduling
public class App { … }

@Component
class Housekeeping {
    @Scheduled(fixedDelayString = "${jobs.expire-reservations.delay:30s}")    // 30 s AFTER the previous run finishes
    void expireStaleReservations() { … }

    @Scheduled(fixedRate = 60_000, initialDelay = 10_000)                     // every 60 s from START to start
    void publishGauges() { … }

    @Scheduled(cron = "0 15 2 * * *", zone = "UTC")                           // sec min hour day month weekday: 02:15 UTC daily
    void nightlyReport() { … }
}
```

| Option | Semantics | Use |
|---|---|---|
| `fixedDelay` | wait N after the previous run **ends** | work whose duration varies; never overlaps |
| `fixedRate` | start every N regardless | metrics sampling; can pile up if runs are slow |
| `cron` | calendar schedule (**6 fields, seconds first**, unlike Unix cron) | nightly/weekly jobs; always set `zone` |

**Production realities**

- **One thread by default.** All `@Scheduled` methods share a single-thread scheduler, so a slow job delays the others. Set `spring.task.scheduling.pool.size`.
- **Every instance runs every job.** Two app instances means the nightly reconciliation runs twice. Options: a **DB-backed lock** (ShedLock; a Postgres advisory lock `pg_try_advisory_lock`), claim work rows with `SKIP LOCKED` so instances split the work, or a single dedicated worker.
- **Jobs must be idempotent and restartable.** A crash halfway through must be safe to re-run: process in batches, record progress, use unique constraints.
- Exceptions in a scheduled method are logged and the schedule continues. Add your own error metrics and alerts.

## 4. `@Async`

```java
@Configuration
@EnableAsync
class AsyncConfig {
    @Bean(name = "notificationExecutor")
    ThreadPoolTaskExecutor notificationExecutor(TaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(500);                          // bounded! unbounded queues hide overload until OOM
        ex.setThreadNamePrefix("notify-");
        ex.setTaskDecorator(mdcTaskDecorator);             // carry the request ID (06-logging-actuator.md)
        ex.initialize();
        return ex;
    }
}

@Service
class Notifier {
    @Async("notificationExecutor")
    public CompletableFuture<Void> sendLowStockEmail(LowStockAlert alert) {   // returns immediately to the caller
        mailer.send(alert);
        return CompletableFuture.completedFuture(null);
    }
}
```

**What you lose on another thread:** the transaction (not propagated), the `SecurityContext` (unless you use a delegating
executor or `DelegatingSecurityContextAsyncTaskExecutor`), the MDC (unless decorated), and exceptions (`void` methods
report them to an `AsyncUncaughtExceptionHandler`; `CompletableFuture` carries them to whoever joins).

**`@Async` is not durable.** If the process dies, queued tasks vanish. For anything that must happen (alerts, webhooks,
payments), persist a job/outbox row first and process it from there (LedgerX outbox, ForgeCI queue). `@Async` fits best-effort
fire-and-forget work.

Java 21: `spring.threads.virtual.enabled=true` (Boot 3.2+) runs request handling, `@Async` and `@Scheduled` on virtual
threads. That's great for I/O-bound work, but it doesn't remove the need to bound **downstream** concurrency (DB pool size, external rate limits).

## 5. Apply it (you implement)

- **FlowGrid M3:** cache product/SKU catalog reads (DTOs) with TTL, invalidate on write **after commit**, keep a low-stock view cache, and prove the app serves from Postgres with Redis stopped.
- **FlowGrid M4:** a scheduled low-stock alert job that is idempotent (doesn't re-alert the same SKU every minute) and safe with two instances.
- **LedgerX M4/M5:** the reconciliation job and scheduled payments: batch, restartable, one-instance-at-a-time, with metrics.
- **ForgeCI M4:** a heartbeat/lease reaper on a schedule, and graceful shutdown that stops scheduling and drains work.
- **FlagForge M2:** an environment snapshot cached in Redis, invalidated on publish.

## 6. 🔨 Break it

1. Call a `@Cacheable` method from the same class 100 times and count DB queries.
2. Cache an entity with a lazy association in Redis. What gets stored? What happens on read?
3. Stop Redis with and without the `CacheErrorHandler`, then call a cached endpoint.
4. Evict before commit, then roll back. Is the cache now empty while the DB still has the old value? Reverse the order in two concurrent requests and produce a stale entry that survives until the TTL.
5. Make one `@Scheduled` method sleep 30 s. What happens to the others with the default pool?
6. Run two app instances with the nightly job at `*/10 * * * * *`. Count the executions.
7. Throw inside an `@Async void` method. Where does the exception go?

## 7. 🐞 Debugging tips

- `logging.level.org.springframework.cache=TRACE` shows cache hits/misses and computed keys.
- `redis-cli --scan --pattern 'app:*'` and `TTL <key>` confirm what was stored and for how long.
- Scheduled job not running → missing `@EnableScheduling`, bean not scanned, or the single scheduler thread is stuck (take a thread dump: `/actuator/threaddump` in dev, `jstack`).
- `@Async` running synchronously → missing `@EnableAsync`, self-invocation, or the method isn't public on a proxied bean.

## 8. 🎤 Interview Q&A

<details><summary>How does @Cacheable work?</summary>

A proxy intercepts the call, computes a key from the parameters (SpEL), and checks the configured cache. On a hit it returns the cached value without invoking the method. On a miss it invokes the method and stores the result, subject to condition/unless. It's cache-aside implemented as an aspect.
</details>

<details><summary>How do you keep the cache consistent with the database?</summary>

Evict (or update) on writes, after the transaction commits, and use TTLs to bound staleness from missed invalidations. For multi-instance local caches, broadcast invalidations (e.g., Redis pub/sub), or use a shared Redis cache. Accept and document the staleness window.
</details>

<details><summary>What happens if Redis goes down?</summary>

By default cache errors propagate and requests fail. With a CacheErrorHandler (e.g., LoggingCacheErrorHandler) failures are logged and treated as misses, so the app serves from the database at higher latency. Short client timeouts keep the degradation cheap. I tested this in FlowGrid M3 (say so only if you did).
</details>

<details><summary>fixedRate vs fixedDelay vs cron?</summary>

fixedRate starts runs at a fixed interval (runs can pile up if slow). fixedDelay waits a fixed time after the previous run completes. cron uses a calendar expression (Spring's has 6 fields including seconds) with an explicit zone.
</details>

<details><summary>How do you avoid a scheduled job running on every instance?</summary>

A distributed lock (ShedLock with a DB table, or a Postgres advisory lock), claiming work items with SELECT … FOR UPDATE SKIP LOCKED so instances share the work safely, or a single dedicated worker. The job should be idempotent regardless.
</details>

<details><summary>What are @Async's pitfalls?</summary>

Proxy self-invocation, the default executor config (bound it explicitly), lost context (transaction, security, MDC), exceptions in void methods being swallowed into a handler, and no durability. Use a persistent queue/outbox for work that must happen.
</details>

## ✅ Mastery checklist

- [ ] FlowGrid M3: cache with TTL + after-commit invalidation + Redis-down test passing
- [ ] Explained key design including tenant/org id, and why DTOs not entities
- [ ] A scheduled job that is idempotent and single-runner across instances
- [ ] `@Async` executor bounded and context-propagating; know when not to use `@Async`
