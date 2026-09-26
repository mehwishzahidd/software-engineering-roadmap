# 03 · Spring Data JPA, Hibernate, Flyway: Entities, Relationships, N+1

> **Week 4** (FlowGrid M1: entities, relationships, Flyway, pagination). Revisited in **Week 5** (locking, `@DataJpaTest`)
> and **Week 9** (LedgerX: when *not* to use JPA for append-only, money-critical paths).
> Prerequisite: [`../04-sql-databases/02-joins-aggregation.md`](../04-sql-databases/02-joins-aggregation.md). You must be able
> to read the SQL that Hibernate generates. Examples use the TixHub practice domain (Venue, Event).

## 1. The layers

| Layer | What it is |
|---|---|
| **JPA** (`jakarta.persistence`) | a *specification*: annotations (`@Entity`) + `EntityManager` API + JPQL |
| **Hibernate 6** | the JPA *implementation* Spring Boot uses; generates SQL, manages the persistence context |
| **Spring Data JPA** | generates repository implementations from interfaces (`JpaRepository`), derived queries, paging |
| **JDBC + HikariCP** | underneath everything ([`../04-sql-databases/08-jdbc-orm.md`](../04-sql-databases/08-jdbc-orm.md)) |

## 2. Setup

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
<dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
<dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency> <!-- required since Flyway 10 -->
```

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/app
    username: app
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false            # see §7 — turn OSIV off deliberately
    hibernate:
      ddl-auto: validate           # Flyway owns the schema; Hibernate only checks it matches
    properties:
      hibernate:
        default_batch_fetch_size: 50   # softens N+1 on lazy collections (§8)
logging:
  level:
    org.hibernate.SQL: debug                 # every SQL statement
    org.hibernate.orm.jdbc.bind: trace       # bound parameter values (Hibernate 6) — dev only
```

**Never use `ddl-auto: update/create` beyond throwaway experiments.** Schema changes are migrations, reviewed like code.

## 3. Flyway migrations

```
src/main/resources/db/migration/
  V1__create_venue_and_event.sql
  V2__add_event_status.sql
  V3__index_event_venue_starts_at.sql
  R__refresh_reporting_views.sql      # repeatable: re-run when its checksum changes
```

- Flyway runs pending `V<n>__*.sql` in order at startup and records them in `flyway_schema_history` with a **checksum**.
- **Never edit an applied migration.** The checksum changes and startup fails (`Validate failed: Migration checksum mismatch`). Write a new migration instead.
- Postgres DDL is transactional, so a failing migration rolls back cleanly (MySQL can leave it half-applied: [`../04-sql-databases/07-postgres-vs-mysql.md`](../04-sql-databases/07-postgres-vs-mysql.md)).
- Seed **reference** data in migrations. Seed demo data with a dev-profile runner or a separate `db/dev-data` location.
- Adding `NOT NULL` to a big existing table: add nullable → backfill → add constraint, as separate steps.

## 4. Entities

```java
@Entity
@Table(name = "venue")
public class Venue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)       // maps to GENERATED … AS IDENTITY
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @OneToMany(mappedBy = "venue")                           // inverse side (no FK column here)
    private List<Event> events = new ArrayList<>();

    protected Venue() {}                                     // JPA needs a no-arg constructor (protected is fine)
    public Venue(String name, int capacity) { this.name = name; this.capacity = capacity; }
    // getters; behaviour methods instead of blind setters
}

@Entity
@Table(name = "event")
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)     // OWNING side: has the FK column
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)                             // never ORDINAL
    @Column(nullable = false)
    private EventStatus status = EventStatus.SCHEDULED;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;                        // timestamptz

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Version                                                 // optimistic locking (07-transactions.md)
    private long version;

    protected Event() {}
    Event(Venue venue, String title, OffsetDateTime startsAt, BigDecimal basePrice) {   // used by Venue.scheduleEvent
        this.venue = venue; this.title = title; this.startsAt = startsAt; this.basePrice = basePrice;
    }
}
```

**Rules**

- Entities are mutable classes with a no-arg constructor; Hibernate creates proxies (subclasses) for lazy loading, so don't make them `final`. **Records can't be entities.** Use records for DTOs and projections.
- `IDENTITY` is simple but disables JDBC insert batching (the id is only known after each insert). `SEQUENCE` with `allocationSize` enables batching, which matters for bulk inserts.
- `equals`/`hashCode`: don't use Lombok `@Data` on entities. Either don't override (identity semantics), or base them on a natural business key, or use the id with care (`null` before persist; constant `hashCode`, e.g. `getClass().hashCode()`).
- Keep invariants in the entity (`event.cancel()` checks the state) rather than letting services set arbitrary fields.

## 5. Relationships and the owning side ⭐

| Annotation | Default fetch | Notes |
|---|---|---|
| `@ManyToOne` | **EAGER** (!) | always set `fetch = LAZY` |
| `@OneToOne` | **EAGER** (!) | set `LAZY`; the inverse side of a 1:1 may still load eagerly |
| `@OneToMany` | LAZY | usually `mappedBy` (inverse side) |
| `@ManyToMany` | LAZY | prefer an explicit junction **entity** once the link has attributes (quantity, role) |

- The **owning side** is the one whose table holds the FK (`@JoinColumn`). **Only the owning side is written to the DB.** Changing `venue.getEvents().add(e)` alone does nothing; `e.setVenue(v)` does.
- Keep both sides in sync with a helper on the aggregate:

```java
// in Venue
public Event scheduleEvent(String title, OffsetDateTime at, BigDecimal price) {
    Event e = new Event(this, title, at, price);
    events.add(e);                   // in-memory consistency
    return e;                        // the constructor set e.venue = this: that's the part that gets persisted
}
```

- `cascade = CascadeType.ALL` + `orphanRemoval = true` on `@OneToMany` = "children live and die with the parent" (an order and its lines). Don't cascade across aggregates (event → venue), and never cascade `REMOVE` on `@ManyToOne`.

## 6. The persistence context (why Hibernate "just saves" changes)

Inside a transaction, loaded entities are **managed**. Hibernate keeps a snapshot; at **flush** (before commit, or before a query that needs fresh data) it diffs and issues `UPDATE`s. That's **dirty checking**.

```java
@Transactional
public void reschedule(long eventId, OffsetDateTime newTime) {
    Event e = events.findById(eventId).orElseThrow(() -> new NotFoundException("event", eventId));
    e.reschedule(newTime);          // no save() call needed: UPDATE issued at commit
}
```

| State | Meaning |
|---|---|
| Transient | `new Event(...)`, not known to Hibernate |
| Managed | loaded or persisted in the current persistence context; changes tracked |
| Detached | was managed, context closed (after the transaction); changes **not** tracked |
| Removed | scheduled for delete at flush |

The persistence context is also a **first-level cache**: `findById(5)` twice in one transaction = one SELECT.

## 7. Spring Data repositories

```java
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByVenueIdAndStatusOrderByStartsAt(Long venueId, EventStatus status);   // derived query

    Page<Event> findByStatus(EventStatus status, Pageable pageable);                        // paging + sorting

    @Query("select e from Event e join fetch e.venue where e.startsAt >= :from")           // JPQL + JOIN FETCH
    List<Event> findUpcomingWithVenue(@Param("from") OffsetDateTime from);

    @EntityGraph(attributePaths = "venue")                                                  // same effect, declarative
    List<Event> findByStatus(EventStatus status);

    @Query("""
           select new com.example.tix.event.EventSummary(e.id, e.title, v.name, e.startsAt)
           from Event e join e.venue v
           where e.status = :status
           """)
    List<EventSummary> summaries(@Param("status") EventStatus status);                       // DTO projection (record)

    @Modifying(clearAutomatically = true)
    @Query("update Event e set e.status = :status where e.venue.id = :venueId and e.startsAt > :now")
    int setStatusForFutureAtVenue(@Param("status") EventStatus status,
                                  @Param("venueId") Long venueId,
                                  @Param("now") OffsetDateTime now);                        // bulk update
}

public record EventSummary(Long id, String title, String venueName, OffsetDateTime startsAt) {}
```

- Derived queries are fine for simple filters. When the method name gets long, switch to `@Query`.
- Bulk `@Modifying` queries **bypass** the persistence context: managed entities go stale unless cleared.
- **Conditional updates** are how you make "decrement only if enough is left" atomic in one statement:
  `update … set qty = qty - :n where id = :id and qty >= :n` returns the affected-row count (0 = not enough). Compare this
  with `SELECT … FOR UPDATE` and `@Version` in FlowGrid M2 ([`07-transactions.md`](./07-transactions.md)).
- Native SQL (`nativeQuery = true`) or `JdbcClient` for window functions, `ON CONFLICT`, `SKIP LOCKED`.

## 8. LazyInitializationException and N+1 ⭐

### LazyInitializationException

```
org.hibernate.LazyInitializationException: could not initialize proxy [Venue#3] - no Session
```

You touched a lazy association **after** the persistence context closed, typically while mapping to a DTO or serializing
in the controller, outside the service's `@Transactional`.

**Open Session in View (OSIV)** is `spring.jpa.open-in-view`, **true by default** (Boot logs a warning). It keeps the session
open until the response is written, which hides the exception by running lazy queries during JSON rendering. The costs:
queries you can't see in the service, and a DB connection held for the whole request. Turn it **off**, and fetch what
each use case needs explicitly.

Fixes, in order of preference:

1. Map to the DTO **inside** the transactional service method, after fetching what you need.
2. Fetch the association in the query (`JOIN FETCH` / `@EntityGraph`).
3. Query a DTO projection directly (no entities, no laziness).

### N+1

```java
List<Event> events = eventRepository.findAll();                 // 1 query
events.forEach(e -> System.out.println(e.getVenue().getName())); // +1 query PER event for its venue
```

The SQL log shows:

```
select e1_0.id, … from event e1_0
select v1_0.id, … from venue v1_0 where v1_0.id=?
select v1_0.id, … from venue v1_0 where v1_0.id=?
… (one per distinct venue)
```

| Fix | How | Watch out |
|---|---|---|
| `JOIN FETCH` | `select e from Event e join fetch e.venue` | fetching a **collection** + `Pageable` → Hibernate paginates **in memory** (warning `HHH90003004`) and duplicates parent rows |
| `@EntityGraph(attributePaths = …)` | declarative fetch plan on a repository method | same collection + paging caveat |
| DTO projection | `select new …Summary(e.id, v.name) from Event e join e.venue v` | read-only; the best choice for list endpoints |
| Batch fetching | `hibernate.default_batch_fetch_size=50` or `@BatchSize` | turns N queries into ⌈N/50⌉ `IN (…)` queries; a mitigation, not a design |

**Paginating parents with their children:** page the parent **ids** first (one query with `LIMIT`), then fetch the parents
and children for those ids with `JOIN FETCH … where id in :ids`.

**Detect N+1 automatically:** assert the statement count in a `@DataJpaTest` using Hibernate statistics
(`hibernate.generate_statistics=true`, then `SessionFactory#getStatistics().getPrepareStatementCount()`) or a datasource-proxy
query counter. Every list endpoint in FlowGrid should have one such test.

## 9. When JPA is the wrong tool

- **Append-only, money-critical tables (LedgerX):** you want explicit `INSERT`s, DB-enforced immutability, and invariant
  queries you can read. Many teams use JPA for accounts and a `JdbcClient`/native path for entries and reconciliation. Decide deliberately, and write the decision down in an ADR ([`../18-projects/templates/adr.md`](../18-projects/templates/adr.md)).
- Reporting, bulk loads, upserts, queue polling: SQL.
- Everything else (aggregates with invariants, CRUD with validation): JPA is productive. Keep the SQL log on while you learn.

## 10. Apply it to FlowGrid M1 (you implement)

- Map warehouse, product, SKU, inventory level, stock adjustment, audit event and user entities over **your** Flyway `V1` schema. Keep `ddl-auto: validate` and fix mismatches in the migration, not in the annotations.
- Every `@ManyToOne` is `LAZY`. Every list endpoint returns DTOs and has a statement-count test.
- The audit event is written in the **same transaction** as the change it records.
- Write down (in `DATABASE.md`) which queries are derived, which are JPQL and which are native, and why.

## 11. 🔨 Break it

1. Remove `fetch = LAZY` from `@ManyToOne` and load 20 events with `findAll()`. Count the queries.
2. Set `open-in-view: false` and return an entity from a controller. Read the exception and the line that triggered it.
3. `JOIN FETCH` a collection and use `Pageable`. Find the `HHH90003004` warning and inspect the SQL (no `LIMIT`!).
4. Edit `V1__…sql` after it has run. Restart and read the checksum error. Undo it properly with a `V2`.
5. Set only `venue.getEvents().add(event)` (inverse side) and save. Is the FK set?
6. Run the bulk `@Modifying` update without `clearAutomatically`, then read the same entity in the same transaction. Stale?

## 12. 🐞 Debugging tips

- Always have `org.hibernate.SQL=debug` in the dev profile. Count queries per request while developing.
- `Schema-validation: missing column [x] in table [y]` → your migration and your entity disagree. The migration is the truth.
- `detached entity passed to persist` → you called `persist` on something with an id. Use the managed instance, or `merge`.
- `could not initialize proxy - no Session` → see §8; find where the entity escapes the transaction.
- Unexpected `UPDATE`s → dirty checking on an entity you modified "just for display". Map to DTOs instead of mutating entities.

## 13. 🎤 Interview Q&A

<details><summary>What is the N+1 problem and how do you fix it?</summary>

One query loads N parents, then accessing a lazy association triggers one query per parent. Fix with JOIN FETCH or @EntityGraph for that use case, DTO projections for read endpoints, or batch fetching as a mitigation. Detect it with SQL logs and statement-count assertions in tests.
</details>

<details><summary>LAZY vs EAGER — defaults and recommendation?</summary>

@ManyToOne and @OneToOne default to EAGER; @OneToMany and @ManyToMany default to LAZY. Make everything LAZY and fetch explicitly per use case. EAGER can't be turned off per query and causes hidden joins or N+1.
</details>

<details><summary>What causes LazyInitializationException?</summary>

Accessing an uninitialized lazy proxy/collection after the persistence context (session) has closed, usually outside the @Transactional service method. Fix by fetching what you need within the transaction or projecting to DTOs, not by enabling EAGER or relying on OSIV.
</details>

<details><summary>What is the owning side of a relationship?</summary>

The side that maps the foreign key column (@JoinColumn, typically the @ManyToOne). Hibernate writes the FK only from the owning side; `mappedBy` marks the inverse side, which is ignored for persistence.
</details>

<details><summary>What is dirty checking?</summary>

Hibernate snapshots managed entities and, at flush, compares the current state with the snapshot, issuing UPDATEs for changed fields. No explicit save() is needed for managed entities within a transaction.
</details>

<details><summary>What is Open Session in View and should you use it?</summary>

It keeps the Hibernate session open for the whole web request so lazy loading works during view/JSON rendering. It's on by default in Boot. Many teams disable it because it hides N+1 queries and holds DB connections longer.
</details>

<details><summary>Why use Flyway instead of ddl-auto=update?</summary>

Versioned, reviewed, repeatable, ordered migrations with checksums and history. ddl-auto=update can't do renames or data migrations safely, can't be reviewed, and behaves differently per environment.
</details>

<details><summary>save() vs saveAndFlush() vs dirty checking?</summary>

save() persists new entities or merges detached ones (and returns the managed instance). Changes to managed entities are flushed automatically at commit. saveAndFlush() forces the SQL immediately, which is useful to surface constraint violations inside the method.
</details>

## ✅ Mastery checklist

- [ ] FlowGrid M1 schema is 100% Flyway; `ddl-auto: validate` passes
- [ ] All associations LAZY; OSIV off; list endpoints use projections or explicit fetch plans
- [ ] Reproduced N+1 and fixed it three ways, with a statement-count test
- [ ] Can explain owning side, dirty checking and entity states without notes
- [ ] Wrote an ADR on where JPA stops and SQL starts (by LedgerX M1)
