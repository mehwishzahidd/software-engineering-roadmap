# Week 5 — FlowGrid M2: idempotent orders, concurrent reservations

[← Week 4](../week-04/) · [Roadmap](../../ROADMAP.md) · [Week 6 →](../week-06/)

**Phase 1 · FlowGrid** · Milestone **M2 — Idempotent orders + concurrent reservations** (= MVP)

| Block | Hours | What it means this week |
|---|---:|---|
| Project | 28 | Orders + order lines, `Idempotency-Key`, reservation state machine, cancellation, `SELECT … FOR UPDATE` vs `@Version`, the **N-threads-one-unit** test on Testcontainers, multi-stage Dockerfile |
| Learning | 7 | Transactions & ACID, isolation levels, locking, deadlocks; Java memory model + threads (basics); Mockito, `@WebMvcTest`/`@DataJpaTest`, Testcontainers; Dockerfile; Git rebase/PR workflow |
| DSA | 7 | Stack & Queue — **8 new problems in Python** + Day-3/7/14/30 reviews + 1 Java rep |
| Interview / review | 3 | **Think-aloud practice begins** (weekly, recorded, scored) |

---

## 1. Main objective

Make FlowGrid *correct under concurrency* — the engineering problem that gives the project its
identity. Two guarantees, both proven by tests:

1. **Exactly-once order creation** under client retries: `POST /api/orders` with an
   `Idempotency-Key` stores key + request hash + response; a retry with the same key replays the
   stored response; the same key with a different body is `422`/`409`.
2. **Never oversell**: N threads reserve the last unit of a SKU concurrently → exactly one
   reservation succeeds, the rest fail cleanly, `inventory_level` never violates its `CHECK`s.
   You implement it with `SELECT … FOR UPDATE`, then *compare* with `@Version` optimistic locking
   on the same entity and write down the trade-off.

This is MVP: after this week FlowGrid takes orders and reserves stock safely. It is also the
template for LedgerX's transfers (Week 10), which reuse everything here with money.

Spec: [`18-projects/flowgrid/README.md`](../../18-projects/flowgrid/README.md) · M2 in
[`18-projects/flowgrid/milestones.md`](../../18-projects/flowgrid/milestones.md) ·
[`18-projects/flowgrid/failure-engineering.md`](../../18-projects/flowgrid/failure-engineering.md).

## 2. Prerequisites

- [CP-4](../../checkpoints/checkpoint-04.md) taken; M1 tagged `m1`, CI green.
- `InventoryLevel` has `@Version` and `CHECK` constraints (Week 4 §4.1–4.2). If not, add them Monday first.
- Docker running with enough memory for Testcontainers (≥ 4 GB for Docker).

## 3. Learning topics

| Topic | Subtopics | Folder file |
|---|---|---|
| Transactions & isolation | ACID, `@Transactional` boundaries, propagation (`REQUIRED`/`REQUIRES_NEW`), rollback rules, read committed vs repeatable read vs serializable, lost update, phantom, Postgres MVCC in one paragraph | [`04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md), [`05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md) |
| Locking | `SELECT … FOR UPDATE` (+ `NOWAIT`/`SKIP LOCKED`), `@Lock(PESSIMISTIC_WRITE)`, `@Version` + `OptimisticLockException`, lock ordering, deadlock detection (`deadlock_timeout`), retry strategy | [`04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md) |
| Java memory model + threads (basics) | threads, `synchronized`, `volatile`, visibility vs atomicity, `AtomicInteger`, `ExecutorService`, `CountDownLatch`, why DB locks and not JVM locks for multi-instance safety | [`01-java/07-concurrency.md`](../../01-java/07-concurrency.md), [`01-java/06-memory-jvm.md`](../../01-java/06-memory-jvm.md), [`14-cs-fundamentals/concurrency.md`](../../14-cs-fundamentals/concurrency.md) |
| Idempotency | key + request fingerprint + stored response, TTL, key scope (per user), concurrent first requests | [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md) |
| Testing | Mockito (`@Mock`, `@InjectMocks`, `when/verify`, argument captors), `@WebMvcTest` + `@MockBean`, `@DataJpaTest` with real Postgres, Testcontainers (`@ServiceConnection`), Failsafe for `*IT` | [`09-testing/mockito.md`](../../09-testing/mockito.md), [`09-testing/spring-testing.md`](../../09-testing/spring-testing.md), [`09-testing/testcontainers.md`](../../09-testing/testcontainers.md) |
| Dockerfile | multi-stage (Maven build → JRE runtime), layered jars, non-root user, `.dockerignore`, image size | [`11-docker/dockerfiles.md`](../../11-docker/dockerfiles.md), [`11-docker/exercises.md`](../../11-docker/exercises.md) |
| Git rebase / PR workflow | `rebase` vs `merge`, `rebase -i` to squash, `--force-with-lease`, resolving conflicts during rebase, reviewing a PR properly | [`02-git/workflows.md`](../../02-git/workflows.md) |

## 4. Concepts to learn

### 4.1 Where the race is, and why a JVM lock does not fix it

Two requests read `available = 1`, both decide "OK", both write `reserved = 1`: two reservations
for one unit — a **lost update**. `synchronized` in the service only helps within one JVM; with
two app instances (M5 deploys behind Compose, later multiple replicas) it does nothing. The
lock must live where the data lives: the database row.

- **Interview angle:** "How would you prevent overselling?" — say *row-level lock in the DB or optimistic version check*, then explain why an in-memory lock is wrong for horizontally scaled services.
- **FlowGrid uses this:** `InventoryLevel` is locked per (warehouse, sku) during reservation.

### 4.2 Pessimistic: `SELECT … FOR UPDATE`

```sql
BEGIN;
SELECT id, on_hand, reserved, allocated, picked
FROM inventory_level
WHERE warehouse_id = $1 AND sku_id = $2
FOR UPDATE;                                  -- second transaction blocks here until COMMIT/ROLLBACK
-- check available >= qty in Java, then:
UPDATE inventory_level SET reserved = reserved + $3 WHERE id = $4;
COMMIT;
```

```java
public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select il from InventoryLevel il where il.warehouse.id = :wid and il.sku.id = :sid")
    Optional<InventoryLevel> findForUpdate(long wid, long sid);
}
```

Must run inside `@Transactional` (the lock lives until commit). Lock rows in a **stable order**
(e.g. by `sku_id`) when an order has several lines — otherwise two orders with lines {A,B} and
{B,A} deadlock.

- **Interview angle:** "What does `FOR UPDATE` do? What happens to the second transaction?" "How do deadlocks arise and how do you avoid them?" (consistent lock ordering; keep transactions short; Postgres detects and aborts one).
- **FlowGrid uses this:** the reservation service; the same idea, with money, in LedgerX M2.

### 4.3 Optimistic: `@Version`

```java
@Transactional
public void reserve(long levelId, int qty) {
    var level = repo.findById(levelId).orElseThrow();      // reads version = 7
    level.reserve(qty);                                     // domain check: available >= qty
    repo.saveAndFlush(level);                               // UPDATE ... SET reserved=?, version=8 WHERE id=? AND version=7
}                                                           // 0 rows updated → OptimisticLockException → caller retries or fails
```

No blocking; the loser fails at commit. Good when conflicts are rare; bad for hot rows (many
retries). Compare both on the same test (§8) and record throughput + failure counts.

- **Interview angle:** "Optimistic vs pessimistic — when would you pick each?" (contention level, retry cost, transaction length, user-facing latency).
- **FlowGrid uses this:** `@Version` stays on `InventoryLevel` as a second safety net; you choose `FOR UPDATE` for reservations and justify it in `DESIGN_DECISIONS.md`.

### 4.4 Isolation levels in one experiment

Two `psql` sessions:

```sql
-- session A                                  -- session B
BEGIN; SELECT reserved FROM inventory_level WHERE id=1;   -- 0
                                              BEGIN; UPDATE inventory_level SET reserved=1 WHERE id=1; COMMIT;
SELECT reserved FROM inventory_level WHERE id=1;          -- READ COMMITTED: 1 (non-repeatable read)
COMMIT;
BEGIN ISOLATION LEVEL REPEATABLE READ; ...                 -- same steps: still 0 inside A
```

Postgres default is **read committed**. Neither level prevents the lost update in §4.1 without a
lock or version check (repeatable read *would* abort the second writer with a serialization error — try it).

- **Interview angle:** "What is the default isolation level in Postgres and what anomalies does it allow?" "What does `SERIALIZABLE` cost?"
- **FlowGrid uses this:** documented experiment in `docs/DESIGN_DECISIONS.md`; LedgerX repeats it with `SERIALIZABLE`.

### 4.5 Idempotency keys done properly

```
POST /api/orders
Idempotency-Key: 7c1f0c1e-...   (client-generated UUID)
```

Table `idempotency_key(key text, user_id bigint, request_hash text, status smallint, response_status int, response_body jsonb, created_at, expires_at, PRIMARY KEY (key, user_id))`.

Flow: hash canonical body → `INSERT … ON CONFLICT DO NOTHING` (or insert in its own transaction)
→ if inserted, process and store the response; if exists: same hash → replay stored response
(`200/201` + header `Idempotent-Replayed: true`), different hash → `422`; status `IN_PROGRESS`
→ `409` "retry later". TTL 24 h cleaned by a scheduled job (Week 6's scheduling).

- **Interview angle:** "How do you make a POST idempotent?" "What if two identical requests arrive at the same moment?" (the unique constraint decides; the loser replays or gets 409).
- **FlowGrid uses this:** `POST /api/orders`; the k6 script in M5 sends retries on purpose to prove it.

### 4.6 The reservation state machine

`PENDING → RESERVED → (ALLOCATED → PICKED → SHIPPED)` in M3; `RESERVED → RELEASED` on cancellation;
`PENDING → FAILED` when stock is insufficient. Encode transitions in one place:

```java
enum ReservationStatus {
    PENDING, RESERVED, RELEASED, FAILED;
    boolean canTransitionTo(ReservationStatus next) {
        return switch (this) {
            case PENDING  -> next == RESERVED || next == FAILED;
            case RESERVED -> next == RELEASED;
            case RELEASED, FAILED -> false;
        };
    }
}
```

Cancellation: `RESERVED → RELEASED` **and** `inventory_level.reserved -= qty` in one transaction,
under the same row lock.

- **Interview angle:** "How do you prevent invalid state transitions?" (single transition table; tests per illegal edge; DB `CHECK` on status values).
- **FlowGrid uses this:** M3 extends the enum through allocation → pick → pack → ship.

### 4.7 The concurrency test (shape only — you write it)

```java
@SpringBootTest @Testcontainers
class ReservationConcurrencyIT {
    @Container @ServiceConnection static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");
    @Autowired ReservationService service;

    @Test
    void nThreadsOneUnit_exactlyOneWins() throws Exception {
        long levelId = seedLevelWith(onHand = 1);
        int n = 20; var pool = Executors.newFixedThreadPool(n); var start = new CountDownLatch(1);
        var results = new ArrayList<Future<Boolean>>();
        for (int i = 0; i < n; i++) results.add(pool.submit(() -> { start.await(); return tryReserve(levelId, 1); }));
        start.countDown();                                   // release all threads at once
        long wins = results.stream().filter(f -> get(f)).count();
        assertEquals(1, wins);
        assertEquals(1, reload(levelId).getReserved());
    }
}
```

Run it three ways: no locking (expect failures — the test *must* fail), `FOR UPDATE`, `@Version`.
Record wins, exceptions and wall time in `docs/PERFORMANCE.md` (methodology first).

- **Interview angle:** "How did you *prove* your reservation logic is safe?" This test is your answer; say the numbers.
- **FlowGrid uses this:** CI runs it on every PR (Failsafe, `*IT`).

### 4.8 Mockito for the service layer, slices for the web layer

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock IdempotencyStore store; @Mock ReservationService reservations; @InjectMocks OrderService service;
    @Test void sameKeySameBodyReplaysStoredResponse() {
        when(store.find("k1", 42L)).thenReturn(Optional.of(stored(hashOf(body), 201, json)));
        var out = service.create("k1", 42L, body);
        assertTrue(out.replayed()); verify(reservations, never()).reserve(any());
    }
}
```

`@WebMvcTest(OrderController.class)` + `@MockBean OrderService` for HTTP contract tests;
`@DataJpaTest` + Testcontainers for repository queries; `@SpringBootTest` only for the concurrency
IT and one end-to-end path.

- **Interview angle:** "What do you mock and what do you not?" (mock collaborators at the boundary; never mock the database in a locking test).
- **FlowGrid uses this:** the test pyramid in `TESTING.md`.

## 5. Resources

- [PostgreSQL 16 — Concurrency Control (ch. 13)](https://www.postgresql.org/docs/16/mvcc.html): transaction isolation, explicit locking; [`SELECT … FOR UPDATE`](https://www.postgresql.org/docs/16/sql-select.html#SQL-FOR-UPDATE-SHARE).
- [Spring Framework — Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html); [Spring Data JPA — locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
- *Java Concurrency in Practice* ch. 2–3 (thread safety, sharing objects); [`java.util.concurrent` package docs](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/package-summary.html).
- [Testcontainers for Java](https://java.testcontainers.org/); [Spring Boot — Testcontainers support](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html); [Mockito docs](https://site.mockito.org/).
- [Idempotency-Key header draft (IETF)](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/).
- [Docker — multi-stage builds](https://docs.docker.com/build/building/multi-stage/); [Spring Boot — container images](https://docs.spring.io/spring-boot/reference/packaging/container-images/index.html).
- [Pro Git — Rebasing](https://git-scm.com/book/en/v2/Git-Branching-Rebasing).
- DSA: [`03-dsa/06-stack-queue.md`](../../03-dsa/06-stack-queue.md), NeetCode "Stack".

## 6. Exercises + coding assignments (inside FlowGrid)

| # | Exercise | Acceptance |
|---|---|---|
| 1 | Isolation experiment (§4.4) in two `psql` sessions at read committed and repeatable read; write it up | `docs/DESIGN_DECISIONS.md` § "Isolation level" with the observed outputs |
| 2 | Deadlock on purpose: two orders with lines {A,B} and {B,A}, no lock ordering; capture the Postgres deadlock error; fix with ordered locking | Test that reliably deadlocks before the fix (use latches to interleave) and passes after |
| 3 | Idempotency matrix test: same key+body → replay; same key different body → 422; two concurrent first requests → one processes, one replays/409 | Parameterized IT |
| 4 | Multi-stage Dockerfile; image runs with Compose Postgres; image size recorded | `docker compose up` brings app + db; `docs/DEPLOYMENT.md` starts |
| 5 | Timed comparison `FOR UPDATE` vs `@Version` with N = 20 and N = 100 threads | Table in `docs/PERFORMANCE.md` with methodology (machine, JVM, Postgres, N, repetitions) |

### Break it

- Remove `@Transactional` from `reserve`. Run the concurrency test. Explain why `FOR UPDATE` without a transaction is meaningless (lock released immediately).
- Use `@Transactional` on a `private` method or call it from within the same class. Nothing happens (proxy bypass). Show it with a test, then explain Spring AOP proxies.
- Mark the reservation method `synchronized` instead of using DB locks and run two app instances (two ports) against the same Postgres with the harness from §8. Oversell.
- Set the idempotency key TTL to 1 second and retry after 2. Duplicate order. Decide what TTL means for your clients and document it.
- Kill the app mid-transaction (`kill -9` during a `Thread.sleep` you insert after the `UPDATE`): Postgres rolls back; the `CHECK`s and the row lock behaved. Remove the sleep.

### Debug it

- The concurrency test passes locally but hangs in CI. Suspect connection-pool exhaustion: 20 threads, HikariCP default 10 connections, each holding a transaction waiting for a lock held by … a thread waiting for a connection. Read the Hikari log line, reason it through, set pool size / thread count sensibly, document.
- `OptimisticLockException` is wrapped as `ObjectOptimisticLockingFailureException` and lands in your `500` handler. Trace it with a breakpoint in the advice and map it to `409`.
- A test uses `@MockBean` and silently mocks the *wrong* bean (an interface with two implementations). Read the Mockito "unnecessary stubbing" hint and the Spring context log.

## 7. DSA — Stack & Queue (8 new, in Python)

Guide: [`03-dsa/06-stack-queue.md`](../../03-dsa/06-stack-queue.md); templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md).
Limits: Easy 20 min, Medium 35 min, Hard 45 min.

| Day | Problem | Difficulty | Limit | Focus |
|---|---|---|---|---|
| Mon | [20. Valid Parentheses](https://leetcode.com/problems/valid-parentheses/) | Easy | 15 min | `list` as stack; matching `dict` |
| Mon | [155. Min Stack](https://leetcode.com/problems/min-stack/) | Medium | 25 min | parallel min stack |
| Tue | [232. Implement Queue using Stacks](https://leetcode.com/problems/implement-queue-using-stacks/) | Easy | 20 min | amortised O(1) with two stacks |
| Wed | [150. Evaluate Reverse Polish Notation](https://leetcode.com/problems/evaluate-reverse-polish-notation/) | Medium | 25 min | `int(a / b)` truncation toward zero |
| Wed | [496. Next Greater Element I](https://leetcode.com/problems/next-greater-element-i/) | Easy | 25 min | first monotonic stack |
| Thu | [739. Daily Temperatures](https://leetcode.com/problems/daily-temperatures/) | Medium | 30 min | monotonic decreasing stack of indices |
| Thu | [853. Car Fleet](https://leetcode.com/problems/car-fleet/) | Medium | 35 min | sort by position, stack of arrival times |
| Sat | [239. Sliding Window Maximum](https://leetcode.com/problems/sliding-window-maximum/) | Hard | 45 min | monotonic `deque` — window + stack combined |

**Java rep (Sat, 30 min):** re-do **739. Daily Temperatures** in Java with `ArrayDeque<Integer>` (never `Stack`).

**Spaced reviews due:** Day-30 of Week 1 — Reverse String, Remove Element (Mon), Remove Duplicates
(Tue), Move Zeroes (Wed), Max Consecutive Ones, Best Time to Buy and Sell Stock (Thu) — a clean,
timed, independent solve here → `Mastered`. Day-14 of Week 3 — Valid Palindrome, Two Sum II (Mon),
3Sum (Tue), Container With Most Water, Running Sum (Wed), Find Pivot Index, Range Sum Query (Thu),
Subarray Sum Equals K (Sat). Day-7 of Week 4 — Maximum Average Subarray, Longest Substring Without
Repeating (Mon), Minimum Size Subarray Sum (Tue), Max Consecutive Ones III, Fruit Into Baskets (Wed),
Longest Repeating Character Replacement, Permutation in String (Thu), Minimum Window Substring (Sat).
Day-3 of this week's — Valid Parentheses, Min Stack (Thu), Queue using Stacks (Fri), Evaluate RPN,
Next Greater Element I (Sat), Daily Temperatures, Car Fleet (Sun).

## 8. Project work — FlowGrid M2

Read M2 in [`milestones.md`](../../18-projects/flowgrid/milestones.md) first. GitHub: milestone
**M2 — Idempotent orders + concurrent reservations**, issues labelled `m2`.

### Task checklist

- [ ] Mon: `V3__orders_reservations_idempotency.sql` (order, order_line, reservation with status `CHECK`, idempotency_key); entities; Testcontainers wired with `@ServiceConnection`; Failsafe for `*IT`; CI switched from service container to Testcontainers.
- [ ] Tue: `POST /api/orders` (customer ref, lines: sku + qty + priority), `GET /api/orders/{id}`, list with filters; idempotency store + filter/interceptor; idempotency matrix IT.
- [ ] Wed: `ReservationService.reserve(order)` with `FOR UPDATE`, ordered by `sku_id`; reservation state machine; partial failure policy (all-or-nothing per order — document); concurrency IT (N = 20) green with `FOR UPDATE`, red without locking (keep the "red" run in `PERFORMANCE.md`).
- [ ] Thu: `@Version` variant behind a feature flag/property; timed comparison N = 20/100; deadlock exercise + ordered-lock fix; cancellation (`POST /api/orders/{id}/cancel`) releasing reservations atomically; audit events for reserve/release.
- [ ] Fri: multi-stage Dockerfile, `compose.yaml` gains the `app` service; `docs/DEPLOYMENT.md` local section; Break-it/Debug-it.
- [ ] Sat: think-aloud session; failure scenarios; `DESIGN_DECISIONS.md` (locking choice, isolation experiment, idempotency semantics); PR review; tag `m2`.

### Acceptance criteria (summary)

- Orders created with lines; `Idempotency-Key` required (`400` without); replay/422/409 semantics as §4.5; keys expire.
- Reservation succeeds only if `available ≥ qty` for every line under row locks; state transitions validated; cancellation releases and audits.
- Concurrency IT (N threads, 1 unit → exactly one success) green in CI; `CHECK`s never violated (assert via query after the test).
- Both locking strategies implemented; the comparison and the decision are documented.
- `docker compose up` runs app + Postgres from the built image.

### Verification tests

- Unit (Mockito): `OrderService` idempotency branches; `ReservationStatus` transition table (every illegal edge).
- `@WebMvcTest`: `POST /api/orders` validation, missing key → `400`, `ProblemDetail` shapes.
- `@DataJpaTest` + Testcontainers: `findForUpdate` actually locks (second transaction with `NOWAIT` gets `55P03` lock_not_available).
- IT: concurrency (N = 20, repeat 5×), idempotency matrix, cancel-releases-stock, deadlock-before/ordered-after.

### Failure-engineering scenarios this week

From [`failure-engineering.md`](../../18-projects/flowgrid/failure-engineering.md): **double
submit** (client retries the same order), **oversell under concurrency** (no lock), **deadlock
between two multi-line orders**, **connection-pool exhaustion under lock waits**, **crash
mid-transaction**. Each: reproduce → observe → inspect logs → diagnose → fix/design around →
regression test → entry in `docs/FAILURE_LOG.md`.

### PR expectations

≈ 4 PRs (schema+tests infra; orders+idempotency; reservations+locking; cancel+docker+docs).
From this week PRs are **rebased on `main`** before merge (see §9) and squash-merged.

## 9. Git activity

```bash
git switch -c feat/m2-reservations
# ... commits ...
git fetch origin && git rebase origin/main          # replay your commits on top of main
git rebase -i origin/main                           # squash WIP commits into meaningful ones
git push --force-with-lease                         # never plain --force
git tag -a m2 -m "FlowGrid M2: idempotent orders + concurrent reservations" && git push origin m2
```

- Review your PR as if it were a colleague's: leave ≥ 3 review comments on your own diff (naming, a missing test, a boundary case), resolve them, then merge.
- Conventional Commits: `feat(orders): idempotency-key store with request hash`, `feat(reservations): FOR UPDATE with ordered locking`, `test(reservations): N-threads-one-unit IT`, `build(docker): multi-stage image`, `docs: locking decision and isolation experiment`.

## 10. Interview preparation

**Think-aloud practice begins** (Sat, 60 min, weekly from now): pick one Medium you have *not*
solved (suggested: [853. Car Fleet](https://leetcode.com/problems/car-fleet/) if not done, else a
NeetCode Stack problem), set 35 min, record yourself, follow
[`16-interview-prep/coding-interview-method.md`](../../16-interview-prep/coding-interview-method.md)
(clarify → examples → brute force → optimise → code in Python → test → complexity), score against
[`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md), log in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).

Questions to answer out loud (from your own code):

Track A: 1. Why is a monotonic stack O(n) even with the inner `while`? 2. Amortised analysis of the two-stack queue. 3. When would you use `deque` over a list as a stack?
Track B: 4. "How do you prevent overselling?" (full answer with both strategies and the trade-off). 5. What does `FOR UPDATE` do to a second transaction; what is `NOWAIT`? 6. How does your idempotency key handle two concurrent identical requests? 7. Why does `@Transactional` on a private method do nothing? 8. Why Testcontainers rather than H2 for these tests? 9. `volatile` vs `synchronized` — and why neither solves a multi-instance race.

Plus M2 questions from [`18-projects/flowgrid/interview-questions.md`](../../18-projects/flowgrid/interview-questions.md), one per day.

## 11. Revision work

- Redo Week 4's N+1 fix explanation; then explain how `JOIN FETCH` interacts with `FOR UPDATE` (lock the parent row only).
- Re-draw the reservation state machine from memory; list every illegal edge and the test that covers it.
- CP-4 remediation: whatever CP-4 scored lowest gets 2 × 30 min this week (Tue/Thu interview slots).

## 12. Daily plan

| Day | Blocks |
|---|---|
| **Mon** (8 h) | Learning 2: transactions, isolation, locking (read + isolation experiment) · Project 4.5: `V3` migration, entities, Testcontainers + Failsafe, CI update · DSA 1.5: 20, 155 + Day-30 (Reverse String, Remove Element) + Day-14 (Valid Palindrome, Two Sum II) + Day-7 (Max Avg Subarray, Longest Substring) |
| **Tue** (8 h) | Project 5: orders API + idempotency store + matrix IT · DSA 2: 232 + Day-30 (Remove Duplicates) + Day-14 (3Sum) + Day-7 (Min Size Subarray) · Interview 1: CP-4 remediation; M2 question out loud |
| **Wed** (8 h) | Learning 2: JMM + threads basics; Mockito/slices · Project 4.5: reservation service with `FOR UPDATE`, state machine, concurrency IT · DSA 1.5: 150, 496 + Day-30 (Move Zeroes) + Day-14 (Container, Running Sum) + Day-7 (Ones III, Fruit) |
| **Thu** (8 h) | Project 5: `@Version` variant, timed comparison, deadlock exercise, cancellation · DSA 2: 739, 853 + Day-30 (Max Consecutive Ones, Best Time) + Day-14 (Pivot, Range Sum) + Day-7 (Char Replacement, Permutation) + Day-3 (20, 155) · Docs 1: `PERFORMANCE.md` methodology + numbers |
| **Fri** (5 h) | Learning 1.5: Dockerfile multi-stage; Git rebase · Project 2: Dockerfile, Compose `app`, `DEPLOYMENT.md` · DSA 1: Day-3 (232) · Retro 0.5 |
| **Sat** (6 h) | Project 3: Break-it/Debug-it, failure scenarios, `DESIGN_DECISIONS.md`, rebase + PR + tag `m2` · DSA 1: 239 + Day-14 (Subarray Sum K) + Day-7 (Min Window) + Day-3 (150, 496) + **Java rep** (739) · Interview 2: **think-aloud #1** (recorded, scored) |
| **Sun** (2–3 h) | End-of-week test (§13) · Day-3 (739, 853) · trackers · plan Week 6 · rest |

## 13. End-of-week test (100 min, timed)

**Part A — DSA in Python (40 min).** [739. Daily Temperatures](https://leetcode.com/problems/daily-temperatures/)
and [155. Min Stack](https://leetcode.com/problems/min-stack/) from blank; complexities out loud.

**Part B — Concepts (25 min).**

<details>
<summary>1. Two transactions at READ COMMITTED both read <code>available = 1</code>, then both <code>UPDATE … SET reserved = reserved + 1</code>. Final <code>reserved</code>? Does the CHECK save you?</summary>

`reserved = 2` — each `UPDATE` is atomic and re-reads the current row, so `0 → 1 → 2`. The `CHECK
(reserved + … <= on_hand)` with `on_hand = 1` *does* reject the second update — which is why the
constraint is the last line of defence — but you must not rely on it for the business flow; lock or version.
</details>

<details>
<summary>2. Why does <code>SELECT … FOR UPDATE</code> outside a transaction not protect anything?</summary>

Row locks are held until the transaction ends. In autocommit, the statement is its own transaction:
the lock is released immediately after the `SELECT` returns, before your check and `UPDATE`.
</details>

<details>
<summary>3. What is stored for an idempotency key and why the request hash?</summary>

Key (scoped to the user), request fingerprint (hash of canonical body), status, stored response
(status + body), timestamps/TTL. The hash detects key reuse with a *different* payload, which must
be rejected rather than replayed.
</details>

<details>
<summary>4. Order A locks SKU rows [1, 2]; order B locks [2, 1]. What happens, and the fix?</summary>

Possible deadlock: A holds 1 waits for 2; B holds 2 waits for 1. Postgres detects it after
`deadlock_timeout` and aborts one. Fix: always lock in a canonical order (sort by `sku_id`), and
retry the aborted transaction.
</details>

<details>
<summary>5. Optimistic locking: what SQL does Hibernate emit on update, and how is the conflict detected?</summary>

`UPDATE … SET …, version = :old + 1 WHERE id = :id AND version = :old`. Zero rows affected means
someone else committed first → `OptimisticLockException` at flush/commit.
</details>

<details>
<summary>6. Python: why does <code>int(-7 / 2)</code> give −3 but <code>-7 // 2</code> give −4, and which does Evaluate RPN need?</summary>

`/` yields a float and `int()` truncates toward zero (−3); `//` floors (−4). RPN per LeetCode
truncates toward zero, so use `int(a / b)`.
</details>

**Part C — Practical (30 min).** Write (from blank) a `@DataJpaTest` with Testcontainers proving
`findForUpdate` blocks a second transaction: open transaction 1 via `TransactionTemplate`, lock;
from another thread attempt `SELECT … FOR UPDATE NOWAIT` and assert the lock-not-available error.
Pass = test compiles, runs against a container, and fails when you remove `@Lock`.

**Part D — Explain out loud (5 min).** "Prove to me your system cannot oversell." Numbers from `PERFORMANCE.md`, the test's design, and the trade-off you chose.

## 14. Mastery checklist

- [ ] I can explain ACID, Postgres isolation levels and the anomalies each allows, with the experiment I ran.
- [ ] I can implement and *test* both pessimistic and optimistic locking and argue the choice for a hot row.
- [ ] I can design an idempotency store and state its semantics for every retry case.
- [ ] I can write a deterministic concurrency test with `ExecutorService` + `CountDownLatch` on Testcontainers.
- [ ] I know what Mockito is for and what a `@WebMvcTest`/`@DataJpaTest` slice loads.
- [ ] I can build a multi-stage image and run app + db with Compose; I rebase and force-with-lease correctly.
- [ ] Stack/queue problems: 8 re-solvable in Python within limits; Java rep done; think-aloud #1 recorded and scored.

## 15. Expected deliverables

- `flowgrid`: milestone M2 closed, ≥ 4 rebased + squash-merged PRs, tag `m2`, image builds in CI (build only; publish is Week 8), `docs/PERFORMANCE.md` (locking comparison with methodology), `docs/DESIGN_DECISIONS.md`, `docs/FAILURE_LOG.md` (+5 scenarios), `TESTING.md` (pyramid + how to run ITs).
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 38 problems total, 5 Java reps, first `Mastered` entries (Week 1 Day-30).
- [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md): think-aloud #1 score.
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md), [`trackers/project-tracker.md`](../../trackers/project-tracker.md) (M2 done, MVP reached), [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md) (transactions, concurrency basics, Testcontainers, Mockito, Docker).

## 16. If you're behind / stretch

**Behind:** preserve, in order: reservations with `FOR UPDATE` + concurrency IT, idempotency
store with replay/422, cancellation. Cut: the `@Version` timed comparison (do it as a documented
manual experiment, not a full implementation), the Dockerfile (move to Week 6 Monday). DSA: drop 239 and 853; keep every review.

**Stretch:** `SKIP LOCKED` experiment for a future pick-list worker; retry-on-`OptimisticLockException`
with bounded backoff behind the flag; JFR/`jcmd` snapshot during the N = 100 run; solve
[84. Largest Rectangle in Histogram](https://leetcode.com/problems/largest-rectangle-in-histogram/) in Python.
