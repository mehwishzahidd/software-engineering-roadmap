# 07 · `@Transactional`, Propagation, Locking, and Concurrency Tests

> **Week 5** (FlowGrid M2: idempotent orders, concurrent reservations, `SELECT … FOR UPDATE` vs `@Version`) →
> **Week 10** (LedgerX M2: propagation pitfalls, ordered pessimistic locking, retries). Database side first:
> [`../04-sql-databases/06-transactions.md`](../04-sql-databases/06-transactions.md). Read that before this.

## 1. What `@Transactional` actually does ⭐

```java
@Service
public class ReservationService {
    @Transactional
    public ReservationResult reserve(ReserveCommand cmd) { … }
}
```

At startup a `BeanPostProcessor` wraps the bean in a **proxy**. Callers get the proxy:

```
caller ──> [proxy: TransactionInterceptor]
              1. get a Connection from HikariCP, setAutoCommit(false)     (via JpaTransactionManager)
              2. bind it (and the EntityManager) to the current THREAD
              3. call the real reserve(...)
              4. returned normally        -> flush + COMMIT
                 threw RuntimeException/Error -> ROLLBACK
              5. unbind, return connection to pool
```

Consequences you must know:

1. **Only calls through the proxy are transactional.** See §2.
2. The transaction is **bound to the thread**. Work you hand to another thread (`@Async`, `CompletableFuture`, a parallel stream) runs **outside** it.
3. The connection is held for the **whole method**. A slow HTTP call inside a transaction holds a pooled connection and row locks for its entire duration.
4. Put `@Transactional` on **service** methods (the use-case boundary), not on controllers or repositories. Spring Data repository methods are already transactional individually (reads `readOnly`).
5. Put it on `public` methods. (Spring 6 also proxies protected/package-private methods for class-based proxies, but public is the convention and works everywhere.)

## 2. The self-invocation pitfall ⭐

```java
@Service
public class OrderService {
    public void importOrders(List<NewOrder> batch) {
        for (NewOrder o : batch) {
            createOrder(o);                  // this.createOrder(...) -> NOT through the proxy -> NO new transaction
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOrder(NewOrder o) { … }   // annotation silently ignored for the call above
}
```

The same applies to `@Async`, `@Cacheable` and `@PreAuthorize`: all proxy-based.

Fixes, best first:

1. **Move the method to another bean** (`OrderWriter.createOrder`) and inject it. Clean and explicit.
2. Use `TransactionTemplate` for programmatic boundaries:
   ```java
   transactionTemplate.executeWithoutResult(status -> writer.persist(o));
   ```
3. Self-injection (`@Lazy OrderService self`), which works but is a smell.

## 3. Propagation

| Propagation | Behaviour | When |
|---|---|---|
| **`REQUIRED`** (default) | join the current transaction, or start one | almost always |
| `REQUIRES_NEW` | suspend the current one, run in a **new, independent** transaction (a separate connection!) | audit/attempt records that must survive the caller's rollback; per-item commits in a batch |
| `NESTED` | savepoint inside the current transaction (JDBC savepoints; `DataSourceTransactionManager`; limited with JPA) | partial rollback of a step |
| `MANDATORY` | must run inside an existing one, else exception | guard methods that must never run alone |
| `SUPPORTS` | join if there is one, else run without | rare |
| `NOT_SUPPORTED` | suspend, run without a transaction | long read-only work that shouldn't hold a tx |
| `NEVER` | fail if a transaction exists | rare |

`REQUIRES_NEW` pitfalls: it needs a **second** connection while the first is still held. Under load, with a small pool,
every thread holds one connection and waits for a second, and you get **pool deadlock**. Also, the inner commit is
**not** undone if the outer transaction later rolls back.

## 4. Rollback rules ⭐

| Thrown | Default |
|---|---|
| `RuntimeException` (unchecked) | rollback |
| `Error` | rollback |
| **checked** `Exception` (e.g. `IOException`) | **commit!** |

```java
@Transactional(rollbackFor = Exception.class)     // also roll back on checked exceptions
public void importFile(Path p) throws IOException { … }
```

**The swallowed-exception trap:**

```java
@Transactional
public void outer() {
    try {
        inner.doWork();              // inner is @Transactional(REQUIRED) and throws RuntimeException
    } catch (RuntimeException e) {
        log.warn("ignored", e);      // you "handled" it…
    }
}                                    // …but the shared tx was marked rollback-only -> at commit:
                                     // UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only
```

When the inner transactional method threw, it marked the shared transaction rollback-only. Either let the exception
propagate, make the inner call `REQUIRES_NEW` (if its failure really is independent), or don't make the inner method
transactional.

## 5. Other attributes

```java
@Transactional(readOnly = true)                          // hint: Hibernate skips dirty checking/flush; driver/DB may optimise
public Page<OrderView> search(OrderFilter f, Pageable p) { … }

@Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 5)   // per-transaction isolation; timeout in seconds
public TransferResult transfer(TransferCommand cmd) { … }
```

A common pattern is `@Transactional(readOnly = true)` on the class, with `@Transactional` on the writing methods.
Isolation semantics per level are covered in [`../04-sql-databases/06-transactions.md`](../04-sql-databases/06-transactions.md#5-isolation-levels--standard-vs-postgresql).
The PostgreSQL default is Read Committed.

## 6. Optimistic vs pessimistic locking in JPA ⭐

### Optimistic: `@Version`

```java
@Entity
class InventoryLevel {
    @Id Long id;
    int onHand;
    int reserved;
    @Version long version;          // Hibernate adds "WHERE id = ? AND version = ?" to every UPDATE and increments it
}
```

If another transaction updated the row since you read it, the `UPDATE` matches 0 rows, and Hibernate throws
`OptimisticLockException`, which Spring wraps as `ObjectOptimisticLockingFailureException`. **No locks are held while you think.**
The loser must **retry the whole transaction** (re-read, re-check, re-write) or return 409.

### Pessimistic: `SELECT … FOR UPDATE`

```java
public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)                                     // -> SELECT … FOR UPDATE
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000"))
    @Query("select i from InventoryLevel i where i.warehouseId = :w and i.skuId = :s")
    Optional<InventoryLevel> lockByWarehouseAndSku(@Param("w") long warehouseId, @Param("s") long skuId);
}
```

The second transaction **waits** at the SELECT until the first commits, then reads the updated row. The lock must be taken
**inside** the transaction that does the write.

### Conditional update (often the simplest correct answer)

`update … set reserved = reserved + :q where id = :id and on_hand - reserved - allocated >= :q` → 1 row = success,
0 rows = insufficient stock. One statement, and the DB's row lock handles the race ([`03-data-jpa.md`](./03-data-jpa.md#7-spring-data-repositories)).

| | `@Version` | `FOR UPDATE` | Conditional `UPDATE` |
|---|---|---|---|
| Contention handling | fail + retry | wait | DB serializes the single statement |
| Good when | conflicts rare, long think time | conflicts frequent, short critical section, multi-row invariants | the rule fits in one `WHERE` |
| Risks | retry storms on hot rows | deadlocks if lock order varies; blocked threads | logic split between Java and SQL |

**FlowGrid M2** asks you to implement one and **measure/compare** it with another on the same hot-SKU test. **LedgerX M2**
uses pessimistic locking of **both accounts in ascending id order**, so two opposite transfers (A→B and B→A) can't deadlock.

### Retrying correctly

The retry must wrap **outside** the transactional method. Retrying inside a failed transaction reuses a rollback-only
transaction and a stale persistence context.

```java
public ReservationResult reserveWithRetry(ReserveCommand cmd) {             // NOT @Transactional
    for (int attempt = 1; ; attempt++) {
        try {
            return reservationTx.reserve(cmd);                              // separate bean, @Transactional
        } catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException e) {
            if (attempt == 3) throw e;
            sleepWithJitter(attempt);                                       // small backoff
        }
    }
}
```

Spring Retry (`@Retryable`) does the same declaratively. Mind the proxy ordering (retry must be outside the transaction).

## 7. Side effects and transactions

- Don't call external systems (HTTP, e-mail, Redis publish) **inside** a transaction that may still roll back. You'll announce things that never happened.
- `@TransactionalEventListener(phase = AFTER_COMMIT)` runs a listener only if the transaction commits. But if the process dies right after the commit, the event is lost.
- The robust pattern is the **transactional outbox**: write the event row in the **same** transaction as the business change, and a relay publishes it afterwards (`SKIP LOCKED` polling). That's LedgerX M3.
- Cache eviction: evict **after** commit, or a concurrent reader can re-cache the old value ([`08-caching-scheduling.md`](./08-caching-scheduling.md)).

## 8. Testing concurrency ⭐ (FlowGrid M2, LedgerX M2)

`@DataJpaTest` and `@Transactional` tests wrap each test in a transaction that **rolls back**, and the data they insert is
**invisible to other threads**. Concurrency tests must therefore run **without** a test-managed transaction, against a real
Postgres (Testcontainers), and clean up explicitly.

A skeleton. You fill in the domain:

```java
@SpringBootTest
@Testcontainers
class ReserveLastUnitConcurrencyTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired ReservationService reservations;

    @Test
    void exactlyOneOfNThreadsGetsTheLastUnit() throws Exception {
        // given: one SKU with exactly 1 available unit (insert via repository/SQL; commit it)
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> { start.await(); return tryReserveOne(); }));  // all threads wait at the gate
        }
        start.countDown();                                                              // release them together
        long successes = 0;
        for (Future<Boolean> f : results) if (f.get(30, TimeUnit.SECONDS)) successes++;
        assertThat(successes).isEqualTo(1);
        // and: available == 0, reserved == 1, never negative (query the DB directly)
    }
}
```

Run it many times (`@RepeatedTest(50)`). Then **remove** your locking and watch it fail. A concurrency test that has
never failed hasn't proven anything.

## 9. 🔨 Break it

1. Self-invocation: call a `REQUIRES_NEW` method from the same class, throw after it, and check whether its row survived. Then move it to another bean.
2. Throw a checked exception from a `@Transactional` method. Was the data committed?
3. Reproduce `UnexpectedRollbackException` with the swallowed-exception pattern.
4. Set `maximum-pool-size: 2`, call a `REQUIRES_NEW` method from inside a transaction with 10 concurrent requests, and watch the pool time out.
5. Remove `@Version` / the `FOR UPDATE` from the reservation path and run the concurrency test 50×.
6. LedgerX preview: transfer A→B and B→A concurrently, locking "from" then "to". Watch for `40P01 deadlock detected`, then fix it with ordered locking.
7. Put `@Transactional` on the concurrency test class. Why do all threads see "not found"?

## 10. 🐞 Debugging tips

- `logging.level.org.springframework.transaction=TRACE` (or `org.springframework.orm.jpa.JpaTransactionManager=DEBUG`) logs "Creating new transaction", "Participating in existing transaction", "Committing", "Rolling back".
- `TransactionSynchronizationManager.isActualTransactionActive()` in a debugger answers "am I in a transaction here?".
- Lock waits: `pg_stat_activity` + `pg_blocking_pids` ([`../04-sql-databases/06-transactions.md`](../04-sql-databases/06-transactions.md#8-timeouts-and-hygiene)).
- `LazyInitializationException` after moving code → you moved it outside the transaction boundary.

## 11. 🎤 Interview Q&A

<details><summary>How does @Transactional work under the hood?</summary>

Spring creates a proxy around the bean. The TransactionInterceptor asks the PlatformTransactionManager (JpaTransactionManager) to begin a transaction: it gets a connection, disables autocommit, and binds the connection and EntityManager to the thread. Then it invokes the method and commits, or rolls back on RuntimeException/Error, before releasing the resources.
</details>

<details><summary>Why doesn't @Transactional work when I call the method from the same class?</summary>

Self-invocation calls `this.method()` on the target object, bypassing the proxy that applies transactional behaviour. Move the method to another bean, use TransactionTemplate, or (less ideally) inject the proxy of self.
</details>

<details><summary>Which exceptions cause rollback by default?</summary>

Unchecked exceptions (RuntimeException and subclasses) and Errors. Checked exceptions commit unless configured with rollbackFor.
</details>

<details><summary>REQUIRED vs REQUIRES_NEW?</summary>

REQUIRED joins an existing transaction or creates one. REQUIRES_NEW suspends the current transaction and runs in a new independent one on a separate connection. Its commit survives an outer rollback. Beware pool exhaustion.
</details>

<details><summary>What does readOnly = true do?</summary>

It's a hint: Hibernate sets flush mode to manual and skips dirty checking; the JDBC connection may be set read-only, allowing DB/driver optimisations or routing to replicas. It doesn't by itself prevent writes in every setup.
</details>

<details><summary>Optimistic vs pessimistic locking — how did you choose in FlowGrid/LedgerX?</summary>

Answer from what you built and measured: the contention level of the hot path, whether a retry is acceptable to the caller, multi-row invariants (transfers lock two accounts in a consistent order), and the results of your concurrency test and load test.
</details>

<details><summary>How do you test that concurrent requests can't oversell?</summary>

A Testcontainers Postgres integration test without test-managed transactions: seed one unit, release N threads simultaneously through a latch, assert exactly one success and consistent quantities in the DB, repeat many times, and prove the test fails without the locking.
</details>

<details><summary>What's the problem with calling an external API inside a transaction?</summary>

It holds a DB connection and locks during network latency, and the external side effect can't be rolled back if the transaction later fails. Use the outbox pattern or after-commit hooks with idempotent consumers.
</details>

## ✅ Mastery checklist

- [ ] Reproduced self-invocation, checked-exception commit, and UnexpectedRollbackException
- [ ] FlowGrid M2: concurrency test passes 50/50 runs, fails without locking; locking choice documented with measurements
- [ ] LedgerX M2: ordered locking; deadlock reproduced and fixed; retry wraps outside the transaction
- [ ] Can draw the proxy + thread-bound transaction diagram from memory
