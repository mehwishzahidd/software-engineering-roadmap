# 08 · JDBC, Connection Pooling, ORM vs Raw SQL

> **Week 3** (JDBC, `PreparedStatement`, SQL injection: the foundation exercise) and **Week 4** (pooling, ORM vs raw SQL, as
> FlowGrid M1 starts on Spring Data JPA: [`../05-spring-boot/03-data-jpa.md`](../05-spring-boot/03-data-jpa.md)).
> Raw JDBC comes back whenever JPA is the wrong tool: reports, bulk work, `SKIP LOCKED` queues (LedgerX outbox).

## 1. The JDBC stack

```
Your code ──> java.sql interfaces (Connection, PreparedStatement, ResultSet)
                 │
                 └─> Driver (org.postgresql:postgresql) ──TCP 5432──> PostgreSQL
DataSource (HikariCP pool) hands out Connections; close() returns them to the pool.
```

Maven:

```xml
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <version>42.7.4</version>
</dependency>
<dependency>
  <groupId>com.zaxxer</groupId>
  <artifactId>HikariCP</artifactId>
  <version>5.1.0</version>
</dependency>
```

(Check Maven Central for current patch versions. From Week 3 on, Spring Boot manages both for you.)

## 2. First query — the right way

```java
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public record EventRow(long id, String title, BigDecimal basePrice) {}

public final class EventQueries {
    private static final String URL = "jdbc:postgresql://localhost:5432/tixhub";

    public static List<EventRow> eventsCheaperThan(BigDecimal maxPrice) throws SQLException {
        String sql = """
            SELECT id, title, base_price
            FROM events
            WHERE base_price < ?
            ORDER BY base_price
            """;
        // try-with-resources closes ResultSet, Statement, Connection in reverse order, even on exceptions
        try (Connection conn = DriverManager.getConnection(URL, "postgres", System.getenv("PGPASSWORD"));
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, maxPrice);                       // parameters are 1-based
            try (ResultSet rs = ps.executeQuery()) {
                List<EventRow> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new EventRow(rs.getLong("id"), rs.getString("title"), rs.getBigDecimal("base_price")));
                }
                return out;
            }
        }
    }
}
```

Checklist: `PreparedStatement` with `?` placeholders · try-with-resources · `BigDecimal` for money · `getObject(col, OffsetDateTime.class)` for `timestamptz` · never hardcode passwords · `rs.getInt()` returns **0 for NULL** — use `rs.getObject("col", Integer.class)` or `rs.wasNull()`.

## 3. SQL injection ⭐

```java
// ❌ NEVER: string concatenation
String sql = "SELECT * FROM customers WHERE email = '" + email + "'";
// email = "x' OR '1'='1"  -> returns every customer
// email = "x'; DROP TABLE customers; --"  -> game over (if multiple statements allowed)

// ✅ ALWAYS: bind parameters — the value is sent separately and can never become SQL
PreparedStatement ps = conn.prepareStatement("SELECT * FROM customers WHERE email = ?");
ps.setString(1, email);
```

Parameters can bind **values only** — not table names, column names, or `ASC/DESC`. For dynamic sorting, map user input through a **whitelist**:

```java
private static final Map<String, String> SORTABLE = Map.of(
        "price", "base_price",
        "date", "starts_at");

String column = SORTABLE.getOrDefault(sortParam, "starts_at");      // never the raw input
String direction = "desc".equalsIgnoreCase(dirParam) ? "DESC" : "ASC";
String sql = "SELECT id, title FROM events ORDER BY " + column + " " + direction + " LIMIT ?";
```

`LIKE` with user input: bind `"%" + escapeLike(term) + "%"` — the parameter is safe from injection, but `%` and `_` in user input still act as wildcards.

## 4. Writes, generated keys, batching

```java
public long insertVenue(Connection conn, String name, String city, int capacity) throws SQLException {
    String sql = "INSERT INTO venues (name, city, capacity) VALUES (?, ?, ?)";
    try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        ps.setString(1, name);
        ps.setString(2, city);
        ps.setInt(3, capacity);
        int affected = ps.executeUpdate();              // number of rows changed
        if (affected != 1) throw new IllegalStateException("expected 1 row, got " + affected);
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }
}

public void insertViews(Connection conn, List<PageView> views) throws SQLException {
    String sql = "INSERT INTO page_views (event_id, customer_id, device, viewed_at) VALUES (?, ?, ?, ?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        for (PageView v : views) {
            ps.setInt(1, v.eventId());
            ps.setObject(2, v.customerId(), Types.INTEGER);   // handles null
            ps.setString(3, v.device());
            ps.setObject(4, v.viewedAt());                    // OffsetDateTime -> timestamptz
            ps.addBatch();
        }
        ps.executeBatch();                                    // one round-trip per batch, not per row
    }
}
```

For big imports add `?reWriteBatchedInserts=true` to the Postgres JDBC URL (rewrites into multi-row `INSERT`s), and flush every ~1,000 rows.

## 5. Transactions in JDBC ⭐ (all-or-nothing writes)

Example on the TixHub practice DB: create an order and its items as **one unit**. It's the same shape as a FlowGrid order
(M2) or a LedgerX journal transaction with its entries (M1), but those you design and write yourself.

```java
public long placeOrder(long customerId, List<Item> items) throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
        conn.setAutoCommit(false);                                   // BEGIN
        try (PreparedStatement order = conn.prepareStatement(
                 "INSERT INTO orders (customer_id, status) VALUES (?, 'PENDING')",
                 Statement.RETURN_GENERATED_KEYS);
             PreparedStatement line = conn.prepareStatement(
                 "INSERT INTO order_items (order_id, event_id, quantity, unit_price) VALUES (?, ?, ?, ?)")) {
            order.setLong(1, customerId);
            order.executeUpdate();
            long orderId;
            try (ResultSet keys = order.getGeneratedKeys()) { keys.next(); orderId = keys.getLong(1); }
            for (Item it : items) {
                line.setLong(1, orderId);
                line.setLong(2, it.eventId());
                line.setInt(3, it.quantity());
                line.setBigDecimal(4, it.unitPrice());
                line.addBatch();
            }
            line.executeBatch();
            conn.commit();                                           // COMMIT: order + all items, or nothing
            return orderId;
        } catch (SQLException | RuntimeException e) {
            conn.rollback();                                         // ROLLBACK on any failure
            throw e;
        }
    }   // close() returns the connection to the pool; HikariCP resets autocommit on return
}
```

- A duplicate `(order_id, event_id)` or a CHECK violation on item 3 rolls back the order **and** items 1–2. Without the
  transaction you'd have an order with half its lines.
- **Idempotent inserts:** a unique constraint plus `INSERT … ON CONFLICT DO NOTHING` makes "insert this exactly once" safe
  to retry (ForgeCI M1 dedupes webhook deliveries this way). `executeUpdate()` returns 0 when the row already existed.
- Isolation per connection: `conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ)`.
- Savepoints: `Savepoint sp = conn.setSavepoint(); … conn.rollback(sp);`
- Error handling uses `e.getSQLState()`: `"23505"` unique violation, `"23503"` FK violation, `"40001"` serialization failure
  (retry), `"40P01"` deadlock (retry). The Postgres driver's `PSQLException#getServerErrorMessage()` adds the constraint name.
- In Spring you won't write this by hand. `@Transactional` does it ([`../05-spring-boot/07-transactions.md`](../05-spring-boot/07-transactions.md)).
  Knowing what it does underneath is what lets you debug it.

## 6. Repository behind an interface (Week 3 foundation exercise)

```java
public interface EventRepository {
    List<EventRow> findUpcomingAtVenue(long venueId, OffsetDateTime from, int limit);
}

public final class JdbcEventRepository implements EventRepository {
    private final DataSource dataSource;
    public JdbcEventRepository(DataSource dataSource) { this.dataSource = dataSource; }

    @Override
    public List<EventRow> findUpcomingAtVenue(long venueId, OffsetDateTime from, int limit) {
        String sql = """
            SELECT id, title, base_price
            FROM events
            WHERE venue_id = ? AND starts_at >= ? AND status = 'SCHEDULED'
            ORDER BY starts_at, id
            LIMIT ?
            """;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, venueId);
            ps.setObject(2, from);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<EventRow> out = new ArrayList<>();
                while (rs.next()) out.add(new EventRow(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3)));
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("findUpcomingAtVenue failed for venue " + venueId, e);  // keep the cause
        }
    }
}
```

Services depend on the interface. Unit tests use an in-memory fake, and the JDBC class gets an **integration test**
against a real Postgres (Testcontainers from Week 5: [`../09-testing/testcontainers.md`](../09-testing/testcontainers.md)).
In FlowGrid, Spring Data JPA generates the implementations, but the boundary stays the same.

## 7. Connection pooling with HikariCP ⭐

```java
HikariConfig cfg = new HikariConfig();
cfg.setJdbcUrl("jdbc:postgresql://localhost:5432/tixhub");
cfg.setUsername("tixhub_app");
cfg.setPassword(System.getenv("TIXHUB_DB_PASSWORD"));
cfg.setMaximumPoolSize(10);                 // hard cap of open connections
cfg.setMinimumIdle(2);
cfg.setConnectionTimeout(3_000);            // ms to wait for a free connection before failing
cfg.setMaxLifetime(30 * 60_000);            // recycle before DB/network drops them
cfg.setLeakDetectionThreshold(10_000);      // log a stack trace if a connection is held > 10 s
try (HikariDataSource ds = new HikariDataSource(cfg)) {
    // use ds.getConnection() everywhere; close() returns it to the pool
}
```

Spring Boot equivalents (`application.yml`):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/flowgrid
    username: flowgrid
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      connection-timeout: 3000
      leak-detection-threshold: 10000
```

Why pool: creating a connection = TCP + TLS + auth + a new Postgres backend process (milliseconds, MBs of RAM). Why *small*: the DB can only execute ~(cores) queries truly in parallel; more connections add contention, not throughput. Symptoms of a wrong pool:

| Symptom | Likely cause |
|---|---|
| `Connection is not available, request timed out after 3000ms` | pool exhausted: slow queries, leaks (unclosed connections), long transactions, or pool too small for real concurrency |
| `FATAL: sorry, too many clients already` | sum of all app instances' pools > `max_connections` |
| Many `idle in transaction` sessions | code opens transactions and does slow non-DB work inside them |

With many app instances, put **PgBouncer** (transaction pooling) in front of Postgres; managed alternative on AWS: RDS Proxy.

## 8. Levels of abstraction: raw JDBC → JdbcClient → ORM

| Approach | Example | Pros | Cons |
|---|---|---|---|
| Raw JDBC | this file | full control, no magic, great for learning | verbose, manual mapping, easy to leak resources |
| Spring `JdbcClient` / `JdbcTemplate` | `jdbcClient.sql("select … where id = :id").param("id", id).query(Event.class).single()` | SQL stays explicit; resource handling + exception translation done | still hand-written SQL + mapping |
| Query builders / mappers (jOOQ, MyBatis) | type-safe SQL DSL / XML-mapped SQL | SQL-first with safety | extra tooling |
| **ORM** (JPA/Hibernate, Spring Data JPA) | `eventRepository.findById(id)` | object graphs, dirty checking, less boilerplate, DB-portable | hidden queries (N+1), lazy loading pitfalls, harder performance tuning, leaky abstraction |

### ORM vs raw SQL — the balanced answer

- **Use the ORM** for CRUD on aggregates (create a warehouse, update a SKU, load an order with its lines). That's most of FlowGrid M1.
- **Use SQL** (native queries, `JdbcClient`, views) for reporting, bulk operations, window functions, upserts, `SKIP LOCKED` queues, conditional updates like `UPDATE … SET available = available - ? WHERE … AND available >= ?`, reconciliation queries (LedgerX M4), and anything where you care exactly what hits the database.
- **Always** turn on SQL logging in development and read what the ORM generates. An ORM doesn't remove the need to know SQL; it raises the stakes. That's why this roadmap teaches SQL in Week 3, before JPA in Week 4.

```java
// Spring 6.1+ JdbcClient — the sweet spot for queries JPA is bad at
record MonthlyRevenue(LocalDate month, BigDecimal revenue) {}

List<MonthlyRevenue> rows = jdbcClient.sql("""
        SELECT date_trunc('month', o.created_at)::date AS month,
               SUM(oi.quantity * oi.unit_price)         AS revenue
        FROM orders o JOIN order_items oi ON oi.order_id = o.id
        WHERE o.status = 'PAID' AND o.created_at >= :from
        GROUP BY 1 ORDER BY 1
        """)
    .param("from", OffsetDateTime.parse("2026-01-01T00:00:00Z"))
    .query(MonthlyRevenue.class)
    .list();
```

## 9. Caching — where it fits

Layers from closest to farthest: in-process cache (Caffeine, per instance) → distributed cache (**Redis**, shared by instances — [`redis.md`](./redis.md)) → database buffer cache (`shared_buffers`) → OS page cache → disk. Spring's `@Cacheable` abstraction is in [`../05-spring-boot/08-caching-scheduling.md`](../05-spring-boot/08-caching-scheduling.md). Rule: **fix the query and index first**; cache data that is read far more often than it changes and can tolerate brief staleness.

## 10. 🔨 Break it

1. Write the injectable `WHERE email = '" + email + "'"` version and log in as any user with `' OR '1'='1`. Then fix it. Keep both in a test.
2. Remove try-with-resources, call your query in a loop with a pool of 5 and `leakDetectionThreshold` set — read the leak warning and the timeout.
3. Call `placeOrder` with a third item whose quantity is 0 (violates the CHECK), with autocommit on. What's left in the tables? Now try it with the transactional version.
4. Insert the same row twice with and without `ON CONFLICT DO NOTHING` on a unique column, and compare `executeUpdate()` return values.
5. Read a NULL `salary` with `rs.getBigDecimal` vs `rs.getInt` — which one lies?

## 11. 🐞 Debugging tips

- `Connection refused` → DB not running / wrong port / Docker port not published. `password authentication failed` → wrong credentials or `pg_hba.conf`. `database "x" does not exist` → create it.
- Log SQL with parameters in development: Postgres `log_statement = 'all'` (local only), or a JDBC proxy such as datasource-proxy / p6spy.
- If a statement "does nothing", check `executeUpdate()`'s return value and whether you're in a transaction that never committed.

## 12. 🎤 Interview Q&A

<details><summary>Statement vs PreparedStatement?</summary>

PreparedStatement sends SQL with placeholders and binds values separately — prevents SQL injection, handles types/escaping, and lets the server reuse the parsed/planned statement. Statement concatenates values into SQL text. Always use PreparedStatement for any user-influenced value.
</details>

<details><summary>How does SQL injection work and how do you prevent it?</summary>

User input is concatenated into SQL and changes its structure (`' OR '1'='1`). Prevent with bind parameters; whitelist identifiers like sort columns; least-privilege DB users; ORMs/`JdbcClient` parameterise by default but native/concatenated queries can still be vulnerable.
</details>

<details><summary>How do you run multiple statements in one transaction in JDBC?</summary>

`setAutoCommit(false)`, execute statements, `commit()`; `rollback()` in a catch; restore autocommit and close the connection in finally/try-with-resources. In Spring, `@Transactional` does this for you via the DataSourceTransactionManager / JpaTransactionManager.
</details>

<details><summary>What is connection pooling and why is it needed?</summary>

A pool keeps open database connections and lends them to threads, avoiding the expensive setup per request and capping concurrent DB load. HikariCP is Spring Boot's default. Size it small (around core count based), and watch for leaks and long transactions.
</details>

<details><summary>ORM vs raw SQL — when would you use each?</summary>

ORM for straightforward CRUD on entities and relationships where productivity matters; SQL for reporting, bulk/batch work, complex joins/window functions, locking patterns and performance-critical paths. Either way, inspect the generated SQL.
</details>

<details><summary>What is the N+1 query problem?</summary>

Loading N parent rows with one query, then issuing one extra query per parent to load children (N more queries). Common with lazy-loaded ORM associations. Fix with a join (JOIN FETCH / entity graph), batch fetching, or a DTO projection query. Detected by counting SQL statements in logs/tests.
</details>

## ✅ Mastery checklist

- [ ] Wrote a JDBC repository with PreparedStatements and try-with-resources (Week 3 exercise)
- [ ] Integration test against a real Postgres passes
- [ ] Wrote an all-or-nothing multi-insert with commit/rollback, then broke it on purpose to see the rollback
- [ ] Demonstrated and fixed SQL injection in a test
- [ ] Configured HikariCP and triggered (then fixed) a connection leak
- [ ] Can give the balanced ORM-vs-SQL answer in 60 seconds
