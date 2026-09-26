# Week 9 — LedgerX M1: double-entry ledger core

[← Week 8](../week-08/) · [Roadmap](../../ROADMAP.md) · [Week 10 →](../week-10/)

**Phase 2 · LedgerX (Weeks 9–13)** — the correctness project. FlowGrid proved you can ship a full stack; LedgerX proves you can keep money *right* under concurrency, crashes and retries.

| Block | Hours | Notes |
|---|---:|---|
| Project (LedgerX M1) | 26 | New repo, schema, immutable ledger, deposits/withdrawals, auth |
| Learning | 7 | Double-entry accounting, `BigDecimal`, constraints/triggers, MVCC, DB internals |
| DSA (Trees) | 7 | 8 new problems + Day 3/7/14/30 reviews from Weeks 5–8 |
| Interview / review | 4 | Weekly think-aloud, 3 résumé-defense questions, retro |

---

## 1. Main objective

By Sunday you have a **new GitHub repository `ledgerx`** with a Spring Boot 3 / PostgreSQL 16 service where:

- every money movement is a **journal transaction** made of **ledger entries** that sum to zero,
- `ledger_entry` rows **cannot be updated or deleted** — enforced by the database, not by discipline,
- an account's balance is **derived from entries** and cross-checked against a materialized balance,
- simulated deposits and withdrawals work end-to-end behind JWT auth, with tests and CI green.

The point of this week is not the endpoints. It is that you can say, in an interview, *"the database makes the ledger immutable and the invariants are tested, here is how"*.

## 2. Prerequisites

- [Checkpoint 8](../../checkpoints/checkpoint-08.md) passed (or its remediation plan written). FlowGrid v1.0 tagged and deployed.
- You can create a Spring Boot + Flyway + Postgres + JWT skeleton from memory in ≤ 2 hours ([Week 4](../week-04/)). If not, that is Monday's first project block — time-box it.
- Recursion is solid in Python ([Week 8](../week-08/)) — every tree problem this week is recursion. Remember `sys.setrecursionlimit` and the `TreeNode` template in [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md).
- Read the LedgerX spec before writing code: [`18-projects/ledgerx/README.md`](../../18-projects/ledgerx/README.md).

## 3. Learning topics

| Topic | Subtopics | File |
|---|---|---|
| Double-entry accounting for engineers | debit/credit, account types (asset/liability/equity), system accounts (`external_clearing`, `fees`), journal vs ledger, why sums are zero | [`18-projects/ledgerx/README.md`](../../18-projects/ledgerx/README.md) |
| Money in Java | `BigDecimal` scale/rounding, `NUMERIC(19,4)`, minor units vs decimals, never `double` | [`01-java/04-modern-java.md`](../../01-java/04-modern-java.md) |
| Schema design for append-only data | `CHECK` constraints, `GENERATED ALWAYS AS IDENTITY`, revoked privileges, triggers that raise, partial/unique indexes | [`04-sql-databases/04-schema-design.md`](../../04-sql-databases/04-schema-design.md) |
| Transactions & MVCC | snapshot visibility, xmin/xmax, why `SUM()` over entries is consistent inside one snapshot, vacuum | [`04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md), [`14-cs-fundamentals/database-internals.md`](../../14-cs-fundamentals/database-internals.md) |
| JPA for immutable rows | `@Immutable`, no setters, `@Version` decisions, insert-only repositories | [`05-spring-boot/03-data-jpa.md`](../../05-spring-boot/03-data-jpa.md), [`04-sql-databases/08-jdbc-orm.md`](../../04-sql-databases/08-jdbc-orm.md) |
| Spring transactions | `@Transactional` boundaries, read-only, rollback rules | [`05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md) |
| Security | JWT re-use from FlowGrid, per-user wallet ownership checks | [`05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md) |
| Testing | Testcontainers Postgres for constraint/trigger tests | [`09-testing/testcontainers.md`](../../09-testing/testcontainers.md) |

## 4. Concepts to learn

### 4.1 Double-entry in one paragraph

Every transaction touches **at least two accounts**; debits equal credits; the ledger is the list of entries, the *journal transaction* groups them. For a wallet system, model *user wallets* as liability accounts (the platform owes the user), and *system accounts* (`external_clearing`, `fees`, `revenue`) as the other side. A $100 deposit is: debit `external_clearing` 100, credit `wallet:alice` 100. A $2 fee: debit `wallet:alice` 2, credit `fees` 2. **Balance of a wallet = sum(credits) − sum(debits)** (sign convention must be fixed once and documented in an ADR).

A simpler representation that many fintechs use: store `amount` as **signed** per entry and require `SUM(amount) = 0` per `journal_txn_id`. Pick one convention, write it in `docs/adr/0001-sign-convention.md`, and never mix.

- **Interview angle:** "Why not just have a `balance` column and update it?" → Lost history, no audit trail, no way to reconcile, race conditions on read-modify-write. Entries are facts; balances are views.
- **Where LedgerX uses this:** every endpoint in M1–M5 writes entries; nothing ever writes a balance directly except the materialized cache in §4.5.

### 4.2 `BigDecimal` and money

```java
// Java 21 — money value object (record), scale fixed at 4, HALF_EVEN for banker's rounding
public record Money(BigDecimal amount, String currency) {
    public static final int SCALE = 4;
    public Money {
        Objects.requireNonNull(amount); Objects.requireNonNull(currency);
        amount = amount.setScale(SCALE, RoundingMode.HALF_EVEN);
    }
    public Money plus(Money o)  { requireSame(o); return new Money(amount.add(o.amount), currency); }
    public Money negate()       { return new Money(amount.negate(), currency); }
    public boolean isNegative() { return amount.signum() < 0; }
    private void requireSame(Money o) {
        if (!currency.equals(o.currency)) throw new IllegalArgumentException("currency mismatch");
    }
}
```

Rules: never `new BigDecimal(0.1)` (use `new BigDecimal("0.1")` or `BigDecimal.valueOf`); compare with `compareTo`, not `equals` (`2.0` ≠ `2.00` under `equals`); column type `NUMERIC(19,4)`.

- **Interview angle:** "Why is `double` wrong for money?" → binary floating point cannot represent 0.1 exactly; accumulated rounding breaks the zero-sum invariant.
- **Where LedgerX uses this:** `Money` in every DTO/entity; `SUM(amount)` in reconciliation (M4) must be exactly `0.0000`.

### 4.3 DB-enforced immutability

Two layers, both required (belt and braces — and both are interview answers):

```sql
-- V3__ledger_entry_immutability.sql (PostgreSQL 16)
CREATE OR REPLACE FUNCTION forbid_ledger_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'ledger_entry is append-only (attempted %)', TG_OP
    USING ERRCODE = 'restrict_violation';
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entry_immutable
  BEFORE UPDATE OR DELETE ON ledger_entry
  FOR EACH ROW EXECUTE FUNCTION forbid_ledger_mutation();

-- Layer 2: the application role cannot even try.
REVOKE UPDATE, DELETE, TRUNCATE ON ledger_entry FROM ledgerx_app;
```

Constraints to add on `ledger_entry`: `amount <> 0`, `currency` matches the account's currency (enforce in the service and with a composite FK `(account_id, currency)` if you want it in the DB), `journal_txn_id NOT NULL`. On `account`: `CHECK (type IN (...))`, `UNIQUE (owner_id, currency)` for user wallets.

- **Interview angle:** "What if a developer runs `UPDATE ledger_entry` in prod?" → trigger raises; if they use the app role, they lack the privilege; corrections are new compensating entries (M3).
- **Where LedgerX uses this:** M1 migration; M4's invariant suite includes a test that *tries* to update and expects failure.

### 4.4 MVCC and consistent sums

PostgreSQL gives each statement (READ COMMITTED) or each transaction (REPEATABLE READ) a snapshot. `SELECT SUM(amount) FROM ledger_entry WHERE account_id = ?` is consistent *within* that snapshot even while other transactions insert. It does **not** protect a read-then-write (check balance → insert withdrawal) across two statements — that is Week 10's locking problem. This week: understand `xmin`/`xmax`, why dead tuples exist, and what `VACUUM` does.

- **Interview angle:** "Does a `SUM` over a hot table block writers?" → No, readers never block writers under MVCC.
- **Where LedgerX uses this:** balance endpoint (derived), reconciliation (M4) runs under `REPEATABLE READ`.

### 4.5 Derived balance + materialized balance with invariant

Keep `account_balance(account_id PK, balance NUMERIC(19,4), version BIGINT, updated_at)` updated **in the same transaction** as the entries. Derived balance is the truth; materialized is the cache. The invariant `account_balance.balance == SUM(entries)` is checked by a test and (M4) a reconciliation job. Provide `GET /accounts/{id}/balance?mode=derived|materialized` for debugging.

- **Interview angle:** "Why keep both?" → `SUM` over millions of entries is O(n); a materialized row is O(1). Keeping both lets you *prove* the cache is right.
- **Where LedgerX uses this:** M2 locks `account_balance` rows (not `SUM`) to enforce non-negative balance under concurrency.

### 4.6 Spring `@Transactional` boundaries for a ledger write

The journal write is one unit: insert `journal_txn` (state `pending`), insert N entries, update N balances, set state `completed`. All inside one `@Transactional` service method. Controllers never open transactions; repositories never own the boundary.

```java
@Service
public class DepositService {
    @Transactional
    public JournalTxnView deposit(UUID walletId, Money amount, String reference) {
        Account wallet = accounts.findByIdAndOwner(walletId, currentUser()).orElseThrow(NotFound::new);
        Account clearing = accounts.systemAccount("external_clearing", amount.currency());
        JournalTxn txn = journal.create(JournalType.DEPOSIT, reference);        // state=pending
        entries.append(txn, clearing, amount.negate());                          // -100 clearing
        entries.append(txn, wallet,   amount);                                   // +100 wallet
        balances.apply(clearing, amount.negate()); balances.apply(wallet, amount);
        return journal.complete(txn);                                            // state=completed
    }
}
```

- **Interview angle:** "What happens if the second `append` throws?" → whole transaction rolls back; no half-written journal; state machine (M3) makes `pending` observable when a step is *deliberately* split.

### 4.7 Postgres internals you should be able to sketch

Heap pages, tuple headers, B-tree index on `(account_id, created_at)`, WAL as the durability mechanism, checkpoints. Read [`14-cs-fundamentals/database-internals.md`](../../14-cs-fundamentals/database-internals.md) and answer: *why is an append-only table cheap for Postgres?* (no dead tuples from updates, no bloat, sequential inserts).

## 5. Resources

- PostgreSQL 16 docs: *CREATE TRIGGER*, *Constraints*, *Concurrency Control (MVCC)*, *GRANT/REVOKE* — https://www.postgresql.org/docs/16/
- Spring Framework docs: *Transaction Management* — https://docs.spring.io/spring-framework/reference/data-access/transaction.html
- Java 21 `BigDecimal` Javadoc — https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html
- Martin Fowler, *Accounting Patterns* (Account, Entry, Transaction, Posting Rules) — https://martinfowler.com/eaaDev/AccountingNarrative.html
- Testcontainers for Java, PostgreSQL module — https://java.testcontainers.org/modules/databases/postgres/
- NeetCode 150 — Trees section; LeetCode problems listed in §7.

## 6. Exercises and assignments

### 6.1 Warm-up exercises (Mon–Wed, ~2 h total)

1. **Sign-convention drill (paper, 20 min).** Write the entries for: deposit 100; withdraw 30; fee 2 on the withdrawal; transfer 25 alice→bob; reversal of the transfer. Every row: account, signed amount, txn id. Check each txn sums to zero. Keep this sheet — it becomes the ADR's examples.
2. **`BigDecimal` kata (30 min).** In a scratch test: show `0.1 + 0.2` in `double` vs `BigDecimal("0.1")`; show `equals` vs `compareTo` on `2.0`/`2.00`; sum 1,000,000 entries of `0.0001` and prove exactly `100.0000`.
3. **psql constraint lab (45 min).** In a throwaway DB: create the trigger from §4.3, insert, then `UPDATE`/`DELETE` as superuser (trigger fires) and as the app role (privilege denied). Record both error messages in your notes.
4. **Snapshot lab (30 min).** Two `psql` sessions: A starts `BEGIN; SELECT SUM(...)`; B inserts and commits; A re-runs the `SUM` under READ COMMITTED then repeats the whole thing under `REPEATABLE READ`. Explain the difference in 3 sentences.

### 6.2 Assignment — LedgerX M1 (see §8 for the milestone breakdown)

**Acceptance criteria (all must hold):**

- [ ] Flyway migrations create `app_user`, `account`, `journal_txn`, `ledger_entry`, `account_balance`; all `CHECK`s from §4.3; trigger + `REVOKE` present.
- [ ] `POST /wallets` creates a wallet (one per user per currency); system accounts are seeded by migration.
- [ ] `POST /wallets/{id}/deposits` and `POST /wallets/{id}/withdrawals` produce a `journal_txn` + 2 entries + balance updates atomically; withdrawal beyond balance → `422` ProblemDetail (single-threaded correctness only; concurrency is M2).
- [ ] `GET /wallets/{id}/balance` returns derived and materialized values, and they are equal in every test.
- [ ] Any attempt to update/delete an entry fails at the DB, proven by a Testcontainers test.
- [ ] JWT auth; a user cannot read or move another user's wallet (`403`/`404` — decide and document).
- [ ] OpenAPI available; `mvn verify` green in GitHub Actions on every PR.

### 6.3 Break it

- Remove `@Transactional` from `deposit()`, make the second `append` throw. Observe the orphaned `journal_txn` in `pending` with one entry. Restore and write the regression test.
- Change `Money.SCALE` to 2 and run the 1,000,000 × 0.0001 test. Watch it fail; understand why scale is a schema-level decision.
- Set the withdrawal check to use the *materialized* balance, then manually corrupt `account_balance` in psql. Withdrawal succeeds against a wrong number. Write down why the M4 reconciliation job must exist.

### 6.4 Debug it

- A `DataIntegrityViolationException` appears on deposit only in CI, not locally. Hypothesis list: seed migration order, test isolation (`@Transactional` tests rolling back seed data?), Testcontainers reuse. Find it with `docker logs` and Flyway's `info` output.
- `GET /balance` returns `100.00` derived but `100.0000` materialized and your JSON test fails on string compare. Fix the serialization (Jackson `BigDecimal` scale) — not the test.

## 7. DSA — Trees (8 new problems, in Python)

**Language: Python (Track A).** Pattern guide: [`03-dsa/10-trees.md`](../../03-dsa/10-trees.md) · Templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) (tree/BFS templates, `deque`) · Method: [`16-interview-prep/coding-interview-method.md`](../../16-interview-prep/coding-interview-method.md)

Read §1–§5 of the guide on Monday (45 min), then solve. Write the recursive **and** the iterative (explicit stack) version for the first two. BFS uses `collections.deque` — never `list.pop(0)` (O(n)).

| # | Problem | Difficulty | Time limit | Day |
|---|---|---|---|---|
| 94 | Binary Tree Inorder Traversal | Easy | 15 min (both versions) | Mon |
| 104 | Maximum Depth of Binary Tree | Easy | 10 min | Mon |
| 226 | Invert Binary Tree | Easy | 10 min | Tue |
| 100 | Same Tree | Easy | 12 min | Tue |
| 572 | Subtree of Another Tree | Easy | 20 min | Wed |
| 543 | Diameter of Binary Tree | Easy | 20 min | Wed |
| 110 | Balanced Binary Tree | Easy | 20 min | Thu |
| 102 | Binary Tree Level Order Traversal | Medium | 25 min | Thu |

**Reviews due this week** (from [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md)): Day-3 of Thu/Fri Week 8 recursion problems; Day-7 of Week 8's first half; Day-14 of Week 7 linked lists; Day-30 of Week 5 stack/queue. Friday's DSA hour is reviews only.

**Java rep (1 problem, ≤ 30 min, Fri):** re-solve #102 (Level Order) in Java with `ArrayDeque` + `List<List<Integer>>` — you already know the algorithm; the point is keeping Java collections fluent for Track B. Toolkit: [`03-dsa/java-dsa-toolkit.md`](../../03-dsa/java-dsa-toolkit.md). Mark the "Java rep" column in the tracker.

Rules: 5 minutes of thinking out loud before code; if stuck at the time limit, read the hint, then the solution, mark `Solved With Solution`, and re-solve from blank on Day 3. Python pitfall of the week: a mutable default argument (`def dfs(node, path=[])`) is shared across calls — pass the list explicitly.

## 8. Project work — LedgerX M1 (Ledger core)

Spec: [`18-projects/ledgerx/README.md`](../../18-projects/ledgerx/README.md) · Milestone detail: [`18-projects/ledgerx/milestones.md`](../../18-projects/ledgerx/milestones.md) (M1) · Failure scenarios: [`18-projects/ledgerx/failure-engineering.md`](../../18-projects/ledgerx/failure-engineering.md)

You implement it. This section is the weekly cut of the milestone, not the code.

### 8.1 Task checklist

- [ ] **Mon:** create repo `ledgerx` (README with one-paragraph purpose, Compose for Postgres 16 + Redis 7, Spring Boot 3 skeleton, Flyway, Actuator, springdoc, GitHub Actions `mvn verify`). Create GitHub milestone **M1 — Ledger core** with issues for each bullet below.
- [ ] **Mon–Tue:** write `docs/adr/0001-sign-convention.md` and `docs/adr/0002-money-representation.md`; migrations `V1__users_accounts.sql`, `V2__journal_ledger.sql`, `V3__ledger_entry_immutability.sql`, `V4__seed_system_accounts.sql`.
- [ ] **Tue:** entities (`Account`, `JournalTxn`, `LedgerEntry` `@Immutable`, `AccountBalance`), insert-only `LedgerEntryRepository` (no `save` overrides that could update — consider a custom interface exposing only `append`).
- [ ] **Wed:** `DepositService`, `WithdrawalService` (single `@Transactional` each), `BalanceService` (derived + materialized), ProblemDetail errors (`InsufficientFunds`, `CurrencyMismatch`, `WalletNotFound`).
- [ ] **Thu:** controllers + DTOs + validation; JWT (port from FlowGrid, do not copy blindly — re-type the filter and understand every line); ownership checks.
- [ ] **Fri:** Testcontainers suite (§8.3); OpenAPI polish; `docs/DATABASE.md` first draft with the ERD.
- [ ] **Sat:** failure-engineering scenarios (§8.4); PR review of your own PRs against the checklist; tag `m1`.

### 8.2 Acceptance summary

M1 is done when §6.2's criteria hold **and** the milestone's issues are all closed via merged PRs, CI is green on `main`, and `docs/DATABASE.md` explains the immutability enforcement in your own words.

### 8.3 Verification tests you write

| Test | Type | What it proves |
|---|---|---|
| `LedgerEntryImmutabilityIT` | Testcontainers | `UPDATE`/`DELETE` via `JdbcTemplate` throws; entity `save` of a modified entry throws |
| `JournalZeroSumIT` | Testcontainers | after deposit/withdrawal, `SELECT journal_txn_id, SUM(amount) ... HAVING SUM(amount) <> 0` returns no rows |
| `BalanceInvariantIT` | Testcontainers | after 50 random deposits/withdrawals, derived == materialized for every account |
| `DepositServiceTest` | Mockito unit | correct entries and signs; currency mismatch rejected |
| `WithdrawalRejectsOverdraftIT` | Testcontainers | balance 50, withdraw 60 → `422`, no rows written |
| `WalletOwnershipIT` | `@SpringBootTest` + MockMvc | other user's wallet → `403`/`404` per ADR |
| `DepositAtomicityIT` | Testcontainers + fault hook | second `append` fails → no `journal_txn` row remains |

JUnit skeleton for the zero-sum check:

```java
@Test
void everyJournalTxnSumsToZero() {
    List<UUID> broken = jdbc.queryForList(
        "SELECT journal_txn_id FROM ledger_entry GROUP BY journal_txn_id HAVING SUM(amount) <> 0",
        UUID.class);
    assertThat(broken).isEmpty();
}
```

### 8.4 Failure-engineering scenarios this week

From [`failure-engineering.md`](../../18-projects/ledgerx/failure-engineering.md), run the M1 set: (1) partial write inside the journal, (2) manual mutation attempt as superuser and as app role, (3) Postgres restarted mid-request (`docker compose restart postgres`) — what does the client see, what is in the DB after? For each: reproduce → observe → logs → diagnose → fix/design → regression test → 5-line write-up in `docs/FAILURES.md`.

### 8.5 GitHub expectations

- Milestone `M1 — Ledger core`, 6–9 issues, labels `area:db`, `area:api`, `type:test`.
- 3–5 PRs, each with description: what/why, how tested, screenshot of CI. Self-review before merge (use the review checklist in [`02-git/workflows.md`](../../02-git/workflows.md)).
- Tag `m1` on Saturday.

## 9. Git activity

- Feature branches per issue: `feat/m1-schema`, `feat/m1-deposit-withdraw`, `feat/m1-auth`, `test/m1-invariants`.
- Conventional commits; one logical change per commit; `git rebase -i` to squash noise before opening a PR.
- Practice: `git bisect` once this week on a deliberately broken commit (e.g., the removed `@Transactional`) — 15 min.

## 10. Interview preparation

Two tracks, never mixed: **Track A = coding interview, in Python**; **Track B = software-engineering / résumé interview, in Java/Spring/SQL/etc.** (ROADMAP §10).

- **Think-aloud (Tue, 45 min) — Track A, Python:** LeetCode 102 (Level Order) recorded; score with [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md). Log in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Résumé-defense drill (Sat, 45 min) — Track B — this week's techs: PostgreSQL, JUnit, Maven.** Three questions from [`17-resume-tech-defense/postgresql.md`](../../17-resume-tech-defense/postgresql.md), [`17-resume-tech-defense/junit.md`](../../17-resume-tech-defense/junit.md), [`17-resume-tech-defense/maven.md`](../../17-resume-tech-defense/maven.md). Answer out loud, 2 minutes each, with a LedgerX or FlowGrid example.
- **Story bank (Sat, 30 min):** add one story from this week (e.g., "I found the orphaned journal row and wrote the atomicity test") using [`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md).
- **Applications:** you are in the early stage since Week 8 ([`JOB_READINESS.md`](../../JOB_READINESS.md)). Sunday: 2–3 long-timeline internship/new-grad applications where FlowGrid alone is a fair story. Track in the interview tracker.

## 11. Revision work

- Re-derive FlowGrid's `SELECT ... FOR UPDATE` reservation from memory (20 min) — you need it again next week with lock ordering.
- Re-read your Week 5 notes on isolation levels; write the four anomalies (dirty read, non-repeatable read, phantom, lost update) with one LedgerX example each.
- Flashcards: trigger vs constraint, `xmin/xmax`, `BigDecimal.compareTo`, `@Transactional` self-invocation pitfall.

## 12. Daily plan

| Day | Learning | Project | DSA | Interview / other |
|---|---|---|---|---|
| **Mon** (8h) | Double-entry + sign convention; `BigDecimal` (2h) | Repo, Compose, skeleton, CI, milestone + issues, ADRs started (4.5h) | Trees §1–5 + #94, #104 (1.5h) | — |
| **Tue** (8h) | — | Migrations, entities, immutability trigger/REVOKE (5h) | #226, #100 + Day-3 reviews (2h) | Think-aloud #102 (1h) |
| **Wed** (8h) | Constraints/triggers, MVCC lab (2h) | Deposit/withdraw services, balance service, errors (4.5h) | #572, #543 (1.5h) | — |
| **Thu** (8h) | — | Controllers, JWT, ownership, OpenAPI (5h) | #110, #102 + Day-7 reviews (2h) | `docs/DATABASE.md` + ERD (1h) |
| **Fri** (5h) | — | Testcontainers invariant suite (3h) | Reviews + Java rep #102 (1h) | Retro prep: what broke, what I learned (1h) |
| **Sat** (6h) | — | Failure scenarios, PR self-review, tag `m1` (4h) | — | Résumé-defense drill + story bank (2h) |
| **Sun** (2–3h) | — | — | Day-14/30 reviews | End-of-week test (§13), trackers, plan Week 10, 2–3 applications |

## 13. End-of-week test (Sunday, 75 min, closed notes)

**Part A — DSA (30 min, Python).** Solve LeetCode **199. Binary Tree Right Side View** (Medium) in ≤ 25 min, then explain BFS vs DFS-right-first approaches out loud.

**Part B — Concepts (20 min).**

1. A journal transaction has entries +100 wallet, −98 clearing, −2 fees. Is it valid? What is the fee account's role?
   <details><summary>Answer</summary>Yes — sums to zero. Fees is a revenue/system account; the 2 moved from the external side to fees. (With the signed convention: the *source* of funds is negative.) Equivalent to two txns (deposit 100, fee 2) but as one atomic unit.</details>
2. Why do you need both the trigger and the `REVOKE`?
   <details><summary>Answer</summary>Defense in depth: `REVOKE` stops the app role even if a trigger is dropped or disabled (`ALTER TABLE ... DISABLE TRIGGER`); the trigger stops privileged sessions (superuser/DBA scripts). Different threat models.</details>
3. Under READ COMMITTED, can `SELECT SUM(amount)` return a value that never existed at any instant?
   <details><summary>Answer</summary>No — a single statement sees one snapshot. Two consecutive statements can disagree (non-repeatable read), which is why balance-then-withdraw needs a lock or `REPEATABLE READ` + retry.</details>
4. `new BigDecimal("2.0").equals(new BigDecimal("2.00"))` → ?
   <details><summary>Answer</summary>`false` (scale differs). Use `compareTo(...) == 0`. Fix scale on construction (`setScale`) so `equals` is safe inside your `Money` record.</details>
5. Where does the materialized balance get updated, and why there?
   <details><summary>Answer</summary>In the same `@Transactional` unit as the entries — so either both commit or neither; the invariant `balance == SUM(entries)` holds at every commit point.</details>
6. What does `@Transactional` on a `private` method do in Spring?
   <details><summary>Answer</summary>Nothing — proxies intercept only public calls from outside the bean; self-invocation bypasses the proxy.</details>

**Part C — Practical (20 min).** From a blank psql session, write the trigger + revoke for a new table `audit_event`, then prove it with an `UPDATE` attempt. No notes.

**Part D — Explain out loud (5 min).** "Walk me through what happens in the database when a user deposits $100." Cover: transaction boundary, rows written, constraints checked, WAL, commit, what a concurrent reader sees.

Pass: A solved in time · B ≥ 5/6 · C works · D fluent without restarting.

## 14. Mastery checklist

- [ ] I can explain double-entry to a non-accountant in 60 seconds and to a DBA in 3 minutes.
- [ ] I chose and documented a sign convention and every test enforces it.
- [ ] I can write an append-only trigger and a `REVOKE` from memory.
- [ ] I know why `SUM` in one statement is consistent and why balance-then-write is not.
- [ ] I can write the derived vs materialized balance trade-off as an ADR.
- [ ] All 8 tree problems attempted in Python; ≥ 6 `Solved Independently` or `Solved With Hint`; all reviews done; one Java rep done.
- [ ] Think-aloud recorded and scored; 3 résumé-defense answers logged.

## 15. Expected deliverables

- `ledgerx` repo: M1 milestone closed, tag `m1`, CI green, `docs/adr/0001`, `0002`, `docs/DATABASE.md`, `docs/FAILURES.md` (3 scenarios).
- [`trackers/project-tracker.md`](../../trackers/project-tracker.md): LedgerX M1 row filled (hours, PRs, what slipped).
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 8 tree rows (Python) with statuses + next review dates; "Java rep" column ticked for #102.
- [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md): PostgreSQL triggers/MVCC, `BigDecimal` marked with evidence links.
- [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md): think-aloud score, drill answers, applications sent.
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md): Week 9 retro (3 wins, 3 problems, 1 change for next week).

## 16. If behind / stretch

**Behind?** Cut in this order: OpenAPI polish → `docs/DATABASE.md` ERD (keep the text) → problem #102 (do it Week 10) → withdrawal endpoint (keep deposit + invariants + immutability; withdrawal moves to Monday of Week 10). Never cut the immutability test or the zero-sum test. Never skip reviews.

**Ahead?** (only after §6.2 is fully green) Add `GET /wallets/{id}/entries` with offset pagination now so Week 11's cursor pagination has something to compare against; add a `journal_txn.metadata JSONB` column with a GIN index and one query using it; solve #199 and #1448 early.
