# 06 · Logging (SLF4J, Logback, MDC, JSON) and Actuator

> **Week 4** (FlowGrid M1: request-ID logging from the first endpoint) → **Week 8** (FlowGrid M5: structured JSON logs to
> CloudWatch, health checks for deployment) → reused in every project. ForgeCI (W15–18) adds a second app (the worker),
> which makes correlation IDs essential. CloudWatch side: [`../12-aws/cloudwatch.md`](../12-aws/cloudwatch.md).

## 1. SLF4J + Logback

- **SLF4J** is the logging **API** your code calls. **Logback** is the implementation Spring Boot ships with. Swap the
  implementation without touching code.
- Boot routes other frameworks' logging (JUL, commons-logging, Log4j API) into the same pipeline.

```java
private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

log.info("Reserved {} units of sku={} in warehouse={}", qty, skuId, warehouseId);   // placeholders, not concatenation
log.warn("Idempotency key reused with different body key={}", key);
log.error("Allocation failed for order={}", orderId, ex);                           // exception LAST -> stack trace printed
log.debug("Candidate scores {}", scores);                                           // cheap when DEBUG is off
```

| Level | Use for | Prod default |
|---|---|---|
| `ERROR` | something failed and needs attention (5xx, job gave up) | on |
| `WARN` | unexpected but handled (retry, degraded mode, 4xx worth noticing) | on |
| `INFO` | business-significant events (order created, deploy started), lifecycle | on |
| `DEBUG` | diagnostic detail for developers | off |
| `TRACE` | very fine detail (SQL bind values) | off |

**Rules**

- Use `{}` placeholders. The string is only built if the level is enabled, and argument values are kept structured for JSON output.
- **Never log secrets or sensitive data:** passwords, tokens, `Authorization` headers, full card numbers, and personal data beyond what you need. It's a security incident waiting in your log storage.
- Log **once**, where you handle an exception, not at every layer that rethrows it.
- Include identifiers (orderId, jobId, requestId) so a line can be found and joined across services.

```yaml
logging:
  level:
    root: info
    com.example.flowgrid: debug          # your code, in dev
    org.hibernate.SQL: debug             # dev profile only
    org.springframework.security: info
```

Levels can be changed **at runtime** via Actuator's `/actuator/loggers/{name}` (POST `{"configuredLevel":"DEBUG"}`). Secure that endpoint.

## 2. Correlation: request IDs with MDC ⭐

The **MDC** (Mapped Diagnostic Context) is a per-thread map whose entries are added to every log line. Put a request ID
there at the start of each request, and every log line from controller → service → repository carries it.

```java
public class RequestIdFilter extends OncePerRequestFilter {
    static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String incoming = req.getHeader(HEADER);
        String id = (incoming != null && incoming.matches("[A-Za-z0-9-]{8,64}")) ? incoming : UUID.randomUUID().toString();
        MDC.put("requestId", id);
        res.setHeader(HEADER, id);                         // client can quote it in bug reports
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("requestId");                       // threads are pooled: ALWAYS clean up
        }
    }
}
```

Register it **first**, before security, so even rejected requests get an ID
(`FilterRegistrationBean` with `setOrder(Ordered.HIGHEST_PRECEDENCE)`). Put the same ID into your ProblemDetail responses
([`04-validation-errors.md`](./04-validation-errors.md)).

Plain-text pattern including it:

```yaml
logging:
  pattern:
    console: "%d{ISO8601} %-5level [%thread] [%X{requestId:-}] %logger{36} - %msg%n"
```

**MDC is thread-local.** Work handed to another thread (`@Async`, executors, `CompletableFuture`, scheduled jobs) loses it
unless you copy it:

```java
@Bean
TaskDecorator mdcTaskDecorator() {                       // Boot applies a TaskDecorator bean to its auto-configured executors
    return runnable -> {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return () -> {
            if (ctx != null) MDC.setContextMap(ctx);
            try { runnable.run(); } finally { MDC.clear(); }
        };
    };
}
```

Across services (ForgeCI API → Redis → worker), put the ID **in the message** and restore it into the MDC in the consumer.
Micrometer Tracing (with an OpenTelemetry bridge) automates trace/span IDs across HTTP calls. Know it exists; the manual
request ID is enough for these projects.

## 3. Structured (JSON) logs

Text logs are for humans at a terminal. **JSON logs** are for machines (CloudWatch Logs Insights, ELK), which can filter
by field: `requestId = "…" and level = "ERROR"`.

Spring Boot **3.4+** has built-in structured logging:

```yaml
logging:
  structured:
    format:
      console: ecs          # or "logstash" / "gelf"; MDC entries become JSON fields automatically
```

```json
{"@timestamp":"2026-09-26T10:00:00.123Z","log.level":"INFO","process.thread.name":"http-nio-8080-exec-3",
 "log.logger":"com.example.flowgrid.order.OrderService","message":"Order created orderId=881","requestId":"6f1c…",
 "service.name":"flowgrid"}
```

On older Boot 3.x versions, use `logstash-logback-encoder` in a `logback-spring.xml`. Use text in the dev profile and JSON in prod.

## 4. Actuator

`spring-boot-starter-actuator` adds operational endpoints under `/actuator`.

| Endpoint | Shows | Exposure advice |
|---|---|---|
| `health` | UP/DOWN + component checks (db, redis, disk) | public, details hidden |
| `health/liveness`, `health/readiness` | probe groups | for load balancers / orchestrators |
| `info` | build version, git commit, custom info | public or internal |
| `metrics`, `metrics/{name}` | Micrometer metrics (JVM, HTTP, Hikari, custom) | internal |
| `prometheus` | metrics in Prometheus format (needs `micrometer-registry-prometheus`) | internal |
| `loggers` | view/change log levels at runtime | **admin only** |
| `env`, `configprops` | resolved config (values masked by default) | **admin only / off** |
| `beans`, `conditions`, `mappings` | context internals | dev only |
| `threaddump`, `heapdump` | JVM state (heap dump contains **data**) | **off in prod** |

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized          # details only for authenticated users with the right role
      roles: ADMIN
      probes:
        enabled: true                        # /actuator/health/liveness and /readiness
  info:
    env:
      enabled: true
    git:
      mode: simple
info:
  app:
    name: flowgrid
```

- `info` build data: add the `build-info` goal to `spring-boot-maven-plugin` and the `git-commit-id-maven-plugin`. Then `/actuator/info` tells you **exactly which commit is running**, which is invaluable after a deploy.
- **Security:** actuator paths go through your `SecurityFilterChain`. Permit `health` (and `info` if you want), and require ADMIN for the rest. Alternatively, serve actuator on a separate port (`management.server.port=8081`) that isn't exposed publicly (security group on AWS).

### Health indicators

Boot auto-adds indicators for the DataSource, Redis and disk space. When Redis is down, `health` turns **DOWN**, and a load balancer would pull the instance. That's wrong if your app is designed to **degrade** without Redis (FlowGrid M3). Remove non-critical dependencies from the readiness group:

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState,db        # Redis deliberately excluded: the app works without it
```

Custom indicator:

```java
@Component
class QueueBacklogHealth implements HealthIndicator {
    private final QueueStats stats;
    QueueBacklogHealth(QueueStats stats) { this.stats = stats; }

    @Override public Health health() {
        long backlog = stats.pending();
        return backlog < 10_000 ? Health.up().withDetail("backlog", backlog).build()
                                : Health.status("DEGRADED").withDetail("backlog", backlog).build();
    }
}
```

### Custom metrics (Micrometer)

```java
@Service
class OrderMetrics {
    private final Counter created;
    private final Timer allocationTimer;

    OrderMetrics(MeterRegistry registry) {
        this.created = Counter.builder("orders.created").description("Orders created").register(registry);
        this.allocationTimer = Timer.builder("allocation.duration").publishPercentiles(0.5, 0.95, 0.99).register(registry);
    }
    void orderCreated() { created.increment(); }
    <T> T timeAllocation(Supplier<T> work) { return allocationTimer.record(work); }
}
```

HTTP request metrics (`http.server.requests`, tagged by URI template, method, status), JVM, and HikariCP
(`hikaricp.connections.active`, `…pending`) come for free. **Pending connections > 0 under load** is the classic
pool-exhaustion signal. Measured latencies from here (with the environment and load recorded) are what your résumé numbers come from.

## 5. Apply it (you implement)

- **FlowGrid M1:** request-ID filter + MDC + `X-Request-Id` response header + `requestId` in ProblemDetails.
- **FlowGrid M5:** JSON logs in prod shipped to CloudWatch; `/actuator/health` wired to the deploy check; `info` shows the git commit; metrics for order creation and allocation.
- **ForgeCI:** the job ID travels through the queue into the worker's MDC; every log chunk and worker log line can be tied to a build.

## 6. 🔨 Break it

1. Remove `MDC.remove` from the filter, send requests with and without the header, and look for IDs leaking into unrelated requests (thread reuse).
2. Log inside an `@Async` method without the TaskDecorator. Where did the request ID go?
3. `log.info("user " + user)` where `toString()` includes the password hash. Find it in the logs. Fix it.
4. Expose `include: "*"` and open `/actuator/env` and `/actuator/heapdump` without auth. What could an attacker learn?
5. Stop Redis and watch `/actuator/health` with and without the readiness group change.

## 7. 🐞 Debugging tips

- "Which config value won?" → `/actuator/env/{property}` (in dev).
- "Is the pool exhausted?" → `/actuator/metrics/hikaricp.connections.pending`.
- "What version is deployed?" → `/actuator/info`.
- Turn up one logger for one incident: `POST /actuator/loggers/com.example.flowgrid.order {"configuredLevel":"DEBUG"}`, then set it back.
- In CloudWatch Logs Insights: `fields @timestamp, message | filter requestId = "…" | sort @timestamp asc`.

## 8. 🎤 Interview Q&A

<details><summary>SLF4J vs Logback?</summary>

SLF4J is a logging facade (API). Logback is an implementation. Code depends on the facade so the backend can change. Spring Boot uses Logback by default and bridges other logging APIs into it.
</details>

<details><summary>How do you trace one request through your logs?</summary>

A filter assigns or accepts a request ID, puts it in the MDC so every log line includes it, returns it in a response header and in error bodies, and propagates it to async threads (TaskDecorator) and other services (headers/message fields). JSON logs make it a filterable field.
</details>

<details><summary>What is MDC and what's the pitfall?</summary>

A thread-local map of contextual values added to log lines. Pitfalls: it doesn't cross thread boundaries automatically, and it must be cleared in finally, or pooled threads leak values into other requests.
</details>

<details><summary>What does Spring Boot Actuator provide? How do you secure it?</summary>

Operational endpoints: health (with liveness/readiness), info, metrics/prometheus, loggers, env, etc. Expose only what you need, keep health public without details, require an admin role for the rest (or use a separate internal management port), and never expose heapdump/env publicly.
</details>

<details><summary>Liveness vs readiness?</summary>

Liveness: is the process healthy, or should it be restarted? Readiness: can it serve traffic right now (dependencies OK, warmed up)? A failing readiness removes the instance from the load balancer without restarting it.
</details>

<details><summary>Why structured logging?</summary>

Machine-parsable fields (level, logger, requestId, orderId) allow precise queries, aggregation and alerting in log platforms, instead of regex over free text.
</details>

## ✅ Mastery checklist

- [ ] Request ID on every log line, response header and ProblemDetail (FlowGrid M1)
- [ ] JSON logs in prod; queried a request end-to-end in CloudWatch (FlowGrid M5)
- [ ] Actuator locked down; health groups reflect what the app really depends on
- [ ] One custom metric and one custom health indicator, used in a real decision
