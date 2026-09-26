# Week 10 — Persist data properly with JPA

[← Week 9](../week-09/) · [Roadmap](../../ROADMAP.md) · [Week 11 →](../week-11/)

**Phase 3 — Backend engineering** (Weeks 9–13) · **Estimated time: ≈21 h**

| Category | Hours | What goes here |
|---|---:|---|
| Core learning | 4.0 | Spring Data JPA, Hibernate, relationships, fetch types, Flyway, validation, error handling |
| Hands-on coding | 3.0 | Scratch JPA app, N+1 reproduction, Compose file, `@RestControllerAdvice` |
| DSA | 5.5 | Trees — 8 new + spaced reviews |
| Project | 5.0 | P2 TicketHold M2 |
| Revision | 1.5 | SQL joins/indexes (W6–W8), Spring DI (W9) |
| Interview | 2.0 | Think-aloud Medium, résumé-defense drill (3 questions) |
| **Total** | **21.0** | |

---

## 1. Main objective

Last week TicketHold stored events in a `HashMap`. That is not an application — restart it and the data
is gone. This week you replace the in-memory store with **PostgreSQL behind Spring Data JPA**, manage the
schema with **Flyway migrations**, stop leaking entities over HTTP with **DTOs**, reject bad input with
**Bean Validation**, and return consistent errors as **RFC 7807 `ProblemDetail`** responses.

Why it matters: "How did you persist data?", "What is the N+1 problem?", "Why not `ddl-auto=update`?" and
"How do you return errors from your API?" are the first four questions an interviewer asks after you say
"Spring Boot" on a résumé. After this week you answer all four from code you wrote.

What it unlocks: Week 11 (security needs a `users` table), Week 12 (`@DataJpaTest`, Testcontainers,
`@Version` all assume JPA), and every later project.

## 2. Prerequisites

- [Checkpoint 8](../../checkpoints/checkpoint-08.md) passed (or remediation in progress): joins, indexes, transactions, `EXPLAIN`.
- Week 9 done: TicketHold skeleton with controller → service → repository layers and a Postman collection.
- JDBC from Week 7 ([`04-sql-databases/08-jdbc-orm.md`](../../04-sql-databases/08-jdbc-orm.md)) — JPA is built on top of it; you need to know what it hides.
- Docker Desktop (or Docker Engine) installed. No prior Docker knowledge required.

## 3. Topics & subtopics

| Topic | Subtopics | Read |
|---|---|---|
| Spring Data JPA | `JpaRepository`, derived queries, `@Query` (JPQL + native), projections, `Pageable`/`Sort`/`Page` | [05-spring-boot/03-data-jpa.md](../../05-spring-boot/03-data-jpa.md) |
| Hibernate / JPA mapping | `@Entity`, `@Id`/`@GeneratedValue`, `@ManyToOne`/`@OneToMany`, owning side, `mappedBy`, cascade, orphan removal, persistence context, dirty checking | [05-spring-boot/03-data-jpa.md](../../05-spring-boot/03-data-jpa.md) |
| Fetching | `LAZY` vs `EAGER`, `LazyInitializationException`, **N+1**, `JOIN FETCH`, `@EntityGraph`, open-in-view | [05-spring-boot/03-data-jpa.md](../../05-spring-boot/03-data-jpa.md) · [04-sql-databases/08-jdbc-orm.md](../../04-sql-databases/08-jdbc-orm.md) |
| Schema migrations | Flyway `V1__init.sql` naming, checksum, `flyway_schema_history`, never edit an applied migration | [05-spring-boot/03-data-jpa.md](../../05-spring-boot/03-data-jpa.md) · [04-sql-databases/04-schema-design.md](../../04-sql-databases/04-schema-design.md) |
| DTOs & mapping | Request/response records, why not expose entities, manual mappers | [05-spring-boot/02-web-layer.md](../../05-spring-boot/02-web-layer.md) |
| Validation & errors | `@Valid`, `@NotBlank`, `@Positive`, `@Future`, custom constraint, `@RestControllerAdvice`, `ProblemDetail` | [05-spring-boot/04-validation-errors.md](../../05-spring-boot/04-validation-errors.md) |
| API design | Pagination (offset vs cursor), sorting, filtering params, 400 vs 404 vs 409 vs 422 | [06-rest-apis/api-design-guide.md](../../06-rest-apis/api-design-guide.md) |
| Docker minimal | `compose.yaml` for Postgres 16, volumes, ports, env vars, `docker compose up -d / logs / down -v` | [11-docker/compose.md](../../11-docker/compose.md) · [11-docker/README.md](../../11-docker/README.md) |

## 4. Concepts to learn

### 4.1 Postgres in one file (Docker Compose)

```yaml
# compose.yaml (repo root of TicketHold)
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: tickethold
      POSTGRES_USER: tickethold
      POSTGRES_PASSWORD: tickethold
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U tickethold"]
      interval: 5s
volumes:
  pgdata:
```

`docker compose up -d` starts it, `docker compose logs -f db` tails it, `docker compose down` stops it and
**keeps** the named volume; `down -v` deletes the data. That is all the Docker you need until Week 19.

> **Interview angle:** "Why use Docker for your local DB?" — reproducible version, zero install drift, identical setup for CI and teammates.

### 4.2 Entities and the owning side

```java
@Entity
@Table(name = "events")
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)   // owning side: holds the FK
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats = new ArrayList<>();

    protected Event() {}                                   // JPA needs a no-arg constructor
    // getters, domain methods…
}
```

- The side with `@JoinColumn` owns the relationship; `mappedBy` is the inverse, read-only mirror.
- `@ManyToOne` defaults to **EAGER** — always set `LAZY` explicitly.
- Don't use Lombok `@Data` on entities: generated `equals/hashCode/toString` walk lazy collections.

> **Interview angle:** "What does `mappedBy` mean?" — it says which field on the other entity owns the foreign key; only the owner's changes are written.

### 4.3 Persistence context and dirty checking

```java
@Transactional
public void rename(Long id, String newName) {
    Event e = events.findById(id).orElseThrow(() -> new NotFoundException("event", id));
    e.rename(newName);          // no save() call needed
}                               // commit → Hibernate compares snapshot → issues UPDATE
```

Inside a transaction, every loaded entity is *managed*. At flush/commit Hibernate diffs it against a snapshot.

> **Interview angle:** "Why didn't you call `save()`?" — managed entities are dirty-checked at commit; `save()` matters for new (transient) or detached objects.

### 4.4 The N+1 problem

```java
List<Event> all = eventRepository.findAll();              // 1 query
all.forEach(e -> System.out.println(e.getVenue().getName())); // +N queries, one per venue
```

Turn on `spring.jpa.show-sql=true` (or better, `logging.level.org.hibernate.SQL=debug`) and count. Fixes:

```java
@Query("select e from Event e join fetch e.venue where e.startsAt > :from")
List<Event> findUpcomingWithVenue(@Param("from") Instant from);

@EntityGraph(attributePaths = "venue")
Page<Event> findByStartsAtAfter(Instant from, Pageable pageable);
```

Warning: `JOIN FETCH` of a *collection* + pagination makes Hibernate paginate **in memory** (log warning `HHH90003004`). Paginate IDs first or use a DTO projection.

> **Interview angle:** "How do you detect and fix N+1?" — SQL logging / query-count assertion; fix with fetch join, entity graph, batch size, or a DTO projection.

### 4.5 Flyway, not `ddl-auto`

```
src/main/resources/db/migration/
  V1__create_venues_events.sql
  V2__create_seats_holds_bookings.sql
  V3__create_users.sql
```

```sql
-- V2__create_seats_holds_bookings.sql
CREATE TABLE seats (
  id        BIGSERIAL PRIMARY KEY,
  event_id  BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  row_label VARCHAR(5) NOT NULL,
  number    INT NOT NULL CHECK (number > 0),
  status    VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  UNIQUE (event_id, row_label, number)
);
CREATE INDEX idx_seats_event_status ON seats(event_id, status);
```

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
```

`validate` makes Hibernate fail fast if entities and schema disagree. Applied migrations are **immutable** — Flyway stores a checksum; edit V1 after it ran and startup fails.

> **Interview angle:** "How do you change a production schema?" — new versioned migration, reviewed in a PR, applied automatically on deploy; backward-compatible steps (add nullable column → backfill → add constraint).

### 4.6 DTOs + Bean Validation

```java
public record CreateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull @Future Instant startsAt,
        @NotNull @Positive Long venueId) {}

public record EventResponse(Long id, String name, Instant startsAt, String venueName) {
    static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getStartsAt(), e.getVenue().getName());
    }
}

@PostMapping
ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest req, UriComponentsBuilder uri) {
    EventResponse created = service.create(req);
    return ResponseEntity.created(uri.path("/api/events/{id}").build(created.id())).body(created);
}
```

> **Interview angle:** "Why DTOs?" — decouple API contract from schema, avoid lazy-loading/serialization loops, prevent mass assignment, version independently.

### 4.7 One error format: `ProblemDetail`

```java
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource not found");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", f.getDefaultMessage()))
                .toList());
        return pd;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Duplicate or conflicting resource");
    }
}
```

> **Interview angle:** "What's RFC 7807?" — a standard JSON error shape (`type`, `title`, `status`, `detail`, `instance`) so clients parse every error the same way.

### 4.8 Pagination and sorting

```java
@GetMapping
Page<EventResponse> list(@RequestParam(required = false) Long venueId,
                         @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
    return service.list(venueId, pageable);
}
// GET /api/events?page=0&size=20&sort=startsAt,desc
```

Cap `size` (`spring.data.web.pageable.max-page-size=100`). Offset pagination gets slow on deep pages (`OFFSET 100000` still scans); cursor/keyset (`WHERE starts_at > :last ORDER BY starts_at LIMIT 20`) doesn't.

> **Interview angle:** "Offset vs cursor pagination?" — offset is simple, supports jumping to page N, but degrades and skips/duplicates on concurrent inserts; keyset is stable and index-friendly.

## 5. Resources

| Type | Resource |
|---|---|
| Official docs | docs.spring.io — Spring Data JPA reference (repositories, query methods, projections); Spring Boot reference "Data" + "SQL Databases" sections |
| Official docs | Hibernate ORM User Guide (hibernate.org/orm/documentation) — "Fetching", "Associations" |
| Official docs | Flyway docs (documentation.red-gate.com/flyway) — "Migrations" concept page |
| Official docs | docs.docker.com — "Docker Compose: Getting started", "Compose file reference" |
| Official docs | postgresql.org/docs/16 — `CREATE TABLE`, constraints, indexes |
| Spec | RFC 9457 / RFC 7807 "Problem Details for HTTP APIs" |
| Book | *Spring in Action* (Walls), chapters on Spring Data; *High-Performance Java Persistence* (Mihalcea) — N+1 and fetching chapters (optional) |
| DSA | neetcode.io — Trees section videos; `03-dsa/10-trees.md` |

## 6. Exercises

1. Start Postgres with Compose; connect with `psql -h localhost -U tickethold`; `\dt` after the app boots and read `flyway_schema_history`.
2. Map `Venue 1—* Event 1—* Seat` in a scratch project. Save a venue with two events via cascade; print the SQL log and explain every statement.
3. Write three derived queries (`findByNameContainingIgnoreCase`, `countByVenueId`, `existsByVenueIdAndStartsAt`) and predict the SQL before running.
4. Write one JPQL `@Query` and one `nativeQuery = true` query returning the same result. Compare generated SQL.
5. Create an interface projection `EventSummary { Long getId(); String getName(); }` and check that only two columns are selected.
6. Write a custom constraint `@ValidSeatRow` (1–2 uppercase letters) with a `ConstraintValidator`.

### Break it

- **B1 — Trigger N+1.** Seed 50 events across 10 venues; call `GET /api/events` mapping `venueName`. Count the queries in the log (expect 1 + up to 10). Fix with `@EntityGraph`; count again.
- **B2 — Lazy outside a transaction.** Set `spring.jpa.open-in-view=false`, return an entity (not a DTO) and access `getSeats()` in the controller. Read the `LazyInitializationException` fully; explain which session was closed and why.
- **B3 — Edit an applied migration.** Change a column length in `V1__…sql` after it ran. Read Flyway's checksum-mismatch error. Revert and add `V4__…` instead.
- **B4 — `ddl-auto=validate` mismatch.** Rename a field in the entity without a migration; read the startup error.
- **B5 — Remove `@Valid`.** POST `{"name":""}`. Where does the failure surface now (DB constraint? 500?). Restore it.
- **B6 — `down -v`.** Run `docker compose down -v`, restart, and confirm Flyway rebuilds the schema from scratch (this is why migrations matter).

### Debug it

- **D1.** A `POST /api/events` returns 500 with `DataIntegrityViolationException`. Using only the log, find which constraint failed and map it to a 409 in the advice.
- **D2.** A `@OneToMany` list shows the child rows twice after a fetch join. Explain the Cartesian product and fix it (`select distinct` / `Set` / separate query).
- **D3.** `findAll(PageRequest.of(0, 20))` prints the `HHH90003004` warning. Find the `JOIN FETCH` on a collection causing it and restructure.

## 7. Coding assignments (from a blank file)

| # | Assignment | Acceptance criteria |
|---|---|---|
| A1 | **Library catalogue, JPA from scratch.** New Spring Boot 3 project with `Author 1—* Book`, Flyway `V1`, Compose Postgres. | `ddl-auto=validate` boots; `GET /books?page=0&size=5&sort=title` returns a `Page`; SQL log shows exactly 1 query for listing with author names (use entity graph); no entity class appears in any controller signature. |
| A2 | **Error contract.** Add `@RestControllerAdvice` for not-found, validation, conflict, and unexpected exceptions. | Every error response is `application/problem+json`; validation errors list each field; unexpected errors return 500 **without** a stack trace in the body but with one in the log. |
| A3 | **N+1 test.** Write a test (or a counter using Hibernate `Statistics`) that asserts listing 20 books executes ≤ 2 statements. | Test fails before the fix and passes after. |

## 8. DSA — Trees (8 new)

Pattern guide: [`03-dsa/10-trees.md`](../../03-dsa/10-trees.md) · Toolkit: [`03-dsa/java-dsa-toolkit.md`](../../03-dsa/java-dsa-toolkit.md) · Track in [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md)

Core ideas: DFS returns a value up the tree (height, balanced?, diameter); pass state down (max-so-far); BFS with a queue for level order. Always state the base case (`null`) first.

| # | Problem | Level | Pattern note | Time limit |
|---|---|---|---|---|
| 1 | [100. Same Tree](https://leetcode.com/problems/same-tree/) | Beginner | Parallel DFS | 15 min |
| 2 | [572. Subtree of Another Tree](https://leetcode.com/problems/subtree-of-another-tree/) | Beginner | Reuse Same Tree at every node | 20 min |
| 3 | [543. Diameter of Binary Tree](https://leetcode.com/problems/diameter-of-binary-tree/) | Beginner | Return height, update global | 20 min |
| 4 | [110. Balanced Binary Tree](https://leetcode.com/problems/balanced-binary-tree/) | Beginner | Return -1 sentinel for "unbalanced" | 20 min |
| 5 | [102. Binary Tree Level Order Traversal](https://leetcode.com/problems/binary-tree-level-order-traversal/) | Interview | BFS, `size` snapshot per level | 25 min |
| 6 | [199. Binary Tree Right Side View](https://leetcode.com/problems/binary-tree-right-side-view/) | Interview | BFS last-in-level or DFS right-first | 25 min |
| 7 | [1448. Count Good Nodes in Binary Tree](https://leetcode.com/problems/count-good-nodes-in-binary-tree/) | Interview | Pass max down | 25 min |
| 8 | [105. Construct Binary Tree from Preorder and Inorder Traversal](https://leetcode.com/problems/construct-binary-tree-from-preorder-and-inorder-traversal/) | Interview | Index map + recursion bounds | 35 min |

Stretch: [124. Binary Tree Maximum Path Sum](https://leetcode.com/problems/binary-tree-maximum-path-sum/) (Hard, 45 min).

**Spaced reviews due this week** (re-solve from blank, timed; update status):
- Day 7: Week 9 recursion + tree-intro problems (e.g., Maximum Depth of Binary Tree, Invert Binary Tree, Reverse Linked List recursive).
- Day 14: Week 8 linked-list problems (e.g., Merge Two Sorted Lists, Linked List Cycle, Reorder List).
- Day 30: Week 6 stack/binary search problems (e.g., Valid Parentheses, Min Stack, Binary Search, Search a 2D Matrix).

**Rule:** if a new problem exceeds its time limit, read the pattern guide, look at the solution, close it, and re-solve from blank → status `Solved With Solution`, review in 3 days.

## 9. Project — P2 TicketHold, Milestone 2

Spec: [`18-projects/p2-tickethold/README.md`](../../18-projects/p2-tickethold/README.md)

**M2 (W10): PostgreSQL + Spring Data JPA + Flyway; entities Venue, Event, Seat, Hold, Booking, User; DTOs, Bean Validation, global error handler (ProblemDetail / RFC 7807), pagination; Postgres via Docker Compose.**

- [ ] `compose.yaml` with Postgres 16 + named volume + healthcheck; README "Run locally" section updated
- [ ] Dependencies: `spring-boot-starter-data-jpa`, `postgresql`, `flyway-core` (+ `flyway-database-postgresql`), `spring-boot-starter-validation`
- [ ] Migrations `V1`–`V3` create `venues`, `events`, `seats`, `holds`, `bookings`, `users` with PKs, FKs, `NOT NULL`, `CHECK`, `UNIQUE(event_id,row_label,number)`, indexes on FK columns
- [ ] `ddl-auto=validate`, `open-in-view=false`
- [ ] Entities for all six tables with `LAZY` `@ManyToOne`; no Lombok `@Data`
- [ ] Repositories replace the Week 9 in-memory maps (service layer signatures unchanged where possible)
- [ ] Request/response records for venues, events, seats; no entity leaves the service layer
- [ ] Bean Validation on every request DTO; one custom constraint
- [ ] `@RestControllerAdvice` producing `ProblemDetail` for 400/404/409/500
- [ ] `GET /api/events` paginated + sortable + filter by `venueId`; max page size capped
- [ ] `POST /api/events/{id}/seats/bulk` generates a seat map (rows × numbers)
- [ ] N+1 checked on the event list endpoint (log shows fixed query count)
- [ ] Postman collection updated with happy path + 3 error cases

## 10. Git activity

- Branch per slice: `feat/postgres-compose`, `feat/jpa-entities`, `feat/flyway-migrations`, `feat/validation-problemdetail`, `feat/pagination`.
- Each PR description: *What / Why / How to test (curl commands) / Screenshots of log or psql*. Link the milestone.
- **Squash merge** feature branches into `main` (one clean commit per feature); keep migrations in the same PR as the entity that needs them.
- Turn on branch protection for `main` now: require PR, disallow force-push. (CI status checks get added in Week 12.)
- Commit message convention: `feat(events): paginate and sort event listing`.
- Never commit `.env` or real passwords; Compose credentials here are local-dev-only and documented as such.

## 11. Interview preparation

- **Think-aloud (1×):** one tree Medium from §8 (e.g., 199) using [`16-interview-prep/coding-interview-method.md`](../../16-interview-prep/coding-interview-method.md). Record, then score against [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md).
- **Résumé-defense drill (3 questions, out loud, recorded)** from [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md):
  1. "Explain how JPA maps an object to a table and what happens on `save()`." → [`17-resume-tech-defense/spring-boot.md`](../../17-resume-tech-defense/spring-boot.md)
  2. "What is the N+1 problem and how did you find it?" → [`17-resume-tech-defense/postgresql.md`](../../17-resume-tech-defense/postgresql.md)
  3. "Why run your database in Docker?" → [`17-resume-tech-defense/docker.md`](../../17-resume-tech-defense/docker.md)
- Questions to answer out loud: LAZY vs EAGER defaults; `mappedBy`; why DTOs; `ddl-auto` values; `PUT` vs `PATCH` validation; 400 vs 422 vs 409.
- Log it in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).

## 12. Revision work

- SQL (Weeks 6–8): write by hand the SQL you expect JPA to generate for "events with venue name, next 30 days, page 2". Run it with `EXPLAIN ANALYZE` against the TicketHold DB. Revisit [`04-sql-databases/05-indexes-performance.md`](../../04-sql-databases/05-indexes-performance.md).
- Java (Week 4): records, `Optional` — used heavily in DTOs and `findById`.
- Spring DI (Week 9): explain constructor injection for your repository → service → controller chain.

## 13. Daily plan

| Day | Block | Tasks |
|---|---|---|
| **Mon (3h)** | Core learning 1.5h | Spring Data JPA reference: repositories, entities, relationships; notes in own words |
| | Hands-on 1h | Compose Postgres up; scratch project with `Author`/`Book`; watch SQL log (Ex 1–2) |
| | DSA 0.5h | Day-7 reviews: Week 9 tree-intro problems |
| **Tue (3h)** | DSA 1.5h | #1 Same Tree, #2 Subtree of Another Tree, #3 Diameter |
| | Core/coding 1.5h | Fetch types + N+1 (Break B1, B2); derived queries (Ex 3–5) |
| **Wed (3h)** | Project 2h | Compose + Flyway V1–V3 + entities; `ddl-auto=validate` boots |
| | DSA review 1h | Day-14 reviews: Week 8 linked lists |
| **Thu (3h)** | Core learning 1h | Bean Validation, `ProblemDetail`, pagination docs |
| | Hands-on 1h | A2 error contract in the scratch project; custom constraint (Ex 6) |
| | DSA 1h | #4 Balanced Binary Tree, #5 Level Order |
| **Fri (2h, light)** | Revision | SQL-vs-JPA exercise (§12); update trackers; explain N+1 out loud in 2 minutes |
| **Sat (5h)** | Project 3h | Repositories replace in-memory store; DTOs; validation; advice; pagination; PRs merged |
| | DSA 1h | #6 Right Side View, #7 Count Good Nodes |
| | Interview 1h | Think-aloud on #6 or #8 (recorded) + résumé-defense drill |
| **Sun (2h)** | Review | End-of-week test (§14); Day-30 reviews; #8 Construct Tree if not done; plan Week 11 |

## 14. End-of-week test (75 min)

**Part A — DSA (30 min).** Solve [543. Diameter of Binary Tree](https://leetcode.com/problems/diameter-of-binary-tree/) from blank, then state time/space complexity. Stretch in remaining time: re-solve [102](https://leetcode.com/problems/binary-tree-level-order-traversal/).

**Part B — Concepts (15 min).** Answer in writing, then check.

<details><summary>1. What are the default fetch types of @ManyToOne and @OneToMany?</summary>

`@ManyToOne` and `@OneToOne` default to EAGER; `@OneToMany` and `@ManyToMany` default to LAZY. Best practice: make everything LAZY and fetch explicitly per use case.
</details>

<details><summary>2. Why does `ddl-auto=update` not belong in production?</summary>

It never drops or renames safely, has no history, no review, no rollback plan, and can differ between environments. Flyway migrations are versioned, reviewed, repeatable and recorded in `flyway_schema_history`.
</details>

<details><summary>3. You modify a managed entity inside a @Transactional method but never call save(). Is it persisted?</summary>

Yes. Dirty checking at flush/commit detects the change and issues an UPDATE.
</details>

<details><summary>4. What status code for a duplicate seat (unique constraint) vs a missing event vs a blank name?</summary>

409 Conflict; 404 Not Found; 400 Bad Request (some APIs use 422 for semantic validation — pick one and be consistent).
</details>

<details><summary>5. Why is JOIN FETCH on a collection combined with pagination dangerous?</summary>

The SQL row count no longer equals the entity count, so Hibernate fetches everything and paginates in memory (HHH90003004) — slow and memory-hungry.
</details>

**Part C — Debug task (20 min).** In your scratch project, return `Book` entities directly from a controller with `open-in-view=false`. Reproduce the error, write a one-paragraph root-cause note, and fix it with a DTO + entity graph.

**Part D — Explain out loud (10 min, recorded).** "Walk me from `POST /api/events` with a JSON body to a row in Postgres — every layer, every annotation, and what happens on invalid input."

## 15. Mastery checklist

- [ ] `docker compose up -d` → app boots → Flyway applies all migrations with `ddl-auto=validate`
- [ ] I can reproduce N+1 on demand and show the query count before/after the fix
- [ ] I can explain owning side vs inverse side without notes
- [ ] No entity class appears in any controller method signature in TicketHold
- [ ] Every error response from TicketHold is `ProblemDetail` JSON
- [ ] `GET /api/events?page=1&size=5&sort=startsAt,desc` works and the page size is capped
- [ ] 8 tree problems logged; ≥ 5 `Solved Independently` or `Solved With Hint`
- [ ] All spaced reviews due this week completed

## 16. Expected deliverables

- 5+ merged PRs on TicketHold (squash), each with a test/curl section
- Tag `p2-m2` on `main` after the milestone is complete
- Updated: [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md), [`trackers/project-tracker.md`](../../trackers/project-tracker.md), [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md) (JPA, Flyway, Docker), [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md), [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md)
- Scratch repo (Library catalogue) pushed as practice evidence

## 17. If you're behind / stretch goals

**Behind (minimum viable week):** Compose + Flyway + entities for Venue/Event/Seat only; DTOs + `ProblemDetail`; pagination on events. Push `Hold`, `Booking`, `User` entities to Monday of Week 11 (they're needed there). DSA: problems 1–5 + all reviews.

**Stretch:**
- Keyset pagination endpoint `GET /api/events/after?cursor=…` and compare `EXPLAIN ANALYZE` to deep offset.
- Enable `hibernate.default_batch_fetch_size=50` and explain how it changes N+1 into N/50+1.
- Add `pgAdmin` or `adminer` as a second Compose service.
- [124. Binary Tree Maximum Path Sum](https://leetcode.com/problems/binary-tree-maximum-path-sum/).
