# LedgerX — Digital Wallet & Double-Entry Ledger Engine

> **Project 2 of 4 · Weeks 9–13 · 125–150 hours · Technical identity: correctness.**
> FlowGrid taught you to ship a Spring Boot system. LedgerX teaches you to ship one that is
> *provably right about money*: immutable double-entry ledger, transactional boundaries, lock
> ordering, isolation levels, idempotency that survives crashes, reconciliation, fault injection
> and an invariant suite. LedgerX must carry the **strongest test suite** of the four projects.

| File | Purpose |
|---|---|
| `README.md` (this file) | Full project plan and specification |
| [`milestones.md`](./milestones.md) | M1–M5: know first → requirements → guidance → acceptance → implement → tests → debugging → interview |
| [`failure-engineering.md`](./failure-engineering.md) | Break it on purpose: 12 scenarios with reproduce → observe → diagnose → fix → regression test |
| [`interview-questions.md`](./interview-questions.md) | Question bank, deep-dive drill-downs, talk outlines |
| [`docs-and-resume.md`](./docs-and-resume.md) | Required docs, benchmark protocol, résumé bullets from real numbers, GitHub hygiene |

Related roadmap material: [`../../ROADMAP.md`](../../ROADMAP.md) §5 (weeks 9–13) · [`../../PROJECTS.md`](../../PROJECTS.md) · [`../../PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md) · [`../README.md`](../README.md) · templates: [`../templates/design-doc.md`](../templates/design-doc.md), [`../templates/adr.md`](../templates/adr.md), [`../templates/benchmark-report.md`](../templates/benchmark-report.md).

> **The project rule applies.** This folder tells you *what* to build, *why* it must be built that
> way, *how* to verify it and *what breaks*. It does not contain the implementation. Code snippets
> here are ≤ 25 lines and cover single isolated concepts (a lock-ordering query, a trigger, a CHECK).

---

## Contents

1. [Product overview](#1-product-overview)
2. [Problem statement — why `balance = balance - amount` is wrong](#2-problem-statement)
3. [Double-entry accounting for engineers](#3-double-entry-accounting-for-engineers)
4. [Functional requirements](#4-functional-requirements)
5. [Non-functional requirements](#5-non-functional-requirements)
6. [Scope tiers](#6-scope-tiers)
7. [Milestone summary (M1–M5)](#7-milestone-summary)
8. [Architecture](#8-architecture)
9. [Database design](#9-database-design)
10. [API design](#10-api-design)
11. [Package structure](#11-package-structure)
12. [Authentication and authorization](#12-authentication-and-authorization)
13. [Testing strategy](#13-testing-strategy)
14. [Docker strategy](#14-docker-strategy)
15. [CI/CD strategy](#15-cicd-strategy)
16. [AWS deployment plan](#16-aws-deployment-plan)
17. [Logging strategy](#17-logging-strategy)
18. [Error-handling strategy](#18-error-handling-strategy)
19. [Security considerations](#19-security-considerations)
20. [Performance considerations](#20-performance-considerations)
21. [Scalability considerations](#21-scalability-considerations)
22. [Git strategy, milestones, PR workflow](#22-git-strategy)
23. [README and demo requirements](#23-readme-and-demo-requirements)
24. [Prerequisite topic map](#24-prerequisite-topic-map)

---

## 1. Product overview

LedgerX is a backend service for a **digital wallet**: users hold money in wallet accounts, top
them up from a (simulated) external source, withdraw to it, transfer to other users, request
payments from each other, and reverse or refund transactions. Every movement of money is
recorded as an **immutable double-entry journal transaction**, balances are **derived** from the
ledger, and a **reconciliation job** continuously proves that the system agrees with itself.

Concretely, the product exposes a REST API (JWT-secured) that supports:

- User registration/login and one or more wallet accounts per user (single currency per account).
- Simulated **deposits** (money comes in from an `external_clearing` system account) and
  **withdrawals** (money goes out to it), with optional **fees** credited to a `fees` account.
- **Internal transfers** between wallets, safe under concurrency and safe under retries
  (`Idempotency-Key` on every money-moving request).
- **Payment requests** (A asks B for X; B accepts → transfer B→A, or declines).
- **Refunds and reversals** implemented as *compensating* journal transactions that link back to
  the original, never as edits.
- **Transaction history** per account with cursor pagination; a per-entity **audit log**.
- **Reconciliation**: every journal sums to zero, every materialized balance equals the sum of
  its entries, drift is flagged and never silently "fixed".
- A **transactional outbox** so that "transfer completed" events reach downstream consumers
  exactly when the transfer actually committed.
- Advanced: **scheduled payments** and **basic configurable risk rules** (max amount, velocity).
- A required **Python component** (`tools/`, M4): an **independent reconciliation verifier**
  that reads PostgreSQL directly (psycopg 3 + `decimal.Decimal`) and proves the ledger's
  invariants *without trusting the Java code*, plus a transaction-data generator and a
  consistency checker used during the crash/retry failure exercises. pytest-tested and typed.

What LedgerX is *not*: it is not a real payment processor, it does not talk to banks or card
networks, it holds no PCI data, and it does not do multi-currency FX. Those exclusions are
deliberate — the engineering problem is **correctness under concurrency and failure**, and
everything else is scope noise.

Who uses it: a wallet user (owns accounts), an admin/operator (runs reconciliation, freezes
accounts, inspects audit trails), and downstream systems (consume outbox events).

---

## 2. Problem statement

The naive wallet does this:

```java
// DO NOT DO THIS — shown so you can explain precisely why it is wrong
Account a = accountRepo.findById(id).orElseThrow();
if (a.getBalance().compareTo(amount) >= 0) {
    a.setBalance(a.getBalance().subtract(amount));
    accountRepo.save(a);
}
```

Three independent failures live inside those five lines.

### 2.1 Lost updates (concurrency)

Two threads read balance = 500. Both check `500 >= 400`. Both write `100`. The account paid out
800 from a 500 balance and the database happily shows 100. Under PostgreSQL's default
`READ COMMITTED`, nothing stops this: both reads see the committed 500, and the second `UPDATE`
just overwrites the first. Even `UPDATE ... SET balance = balance - 400` (the "atomic decrement")
only fixes the arithmetic, not the *check* — you can still drive the balance negative unless the
check is inside the same statement or the row is locked first. (FlowGrid M2 met this with
reservations; LedgerX makes it the whole project.)

### 2.2 No audit trail

`balance` is a single mutable number. After a month, the balance is 137.20 and nobody can say
*why*. There is no record of what happened, in which order, requested by whom, and whether the
system's totals still add up. Regulators, support staff and your own debugging all need the
history, not the snapshot. A `transactions` table bolted on later is *derived* from code paths
and will drift from `balance` the first time an exception is thrown between the two writes.

### 2.3 No reversibility

A refund on a mutable balance is `balance = balance + amount` — another mutation that erases
the fact that anything was refunded. "Undo" becomes a data-fixing script. In a ledger, nothing
is ever undone; a **compensating transaction** is appended that references the original, so the
history stays complete and the effect is reversed.

### 2.4 The ledger answer

| Naive wallet | Ledger |
|---|---|
| Balance is *stored* and mutated | Balance is *derived* (`SUM(entries)`) and optionally cached in a versioned, verifiable materialized row |
| One row per account | Append-only `ledger_entry` rows; a journal transaction groups them |
| Race → lost update | Ordered row locks (or optimistic version) + DB `CHECK` constraints make an overdraft impossible to commit |
| Undo = overwrite | Undo = compensating journal transaction linked via `reversal_of` |
| Retries double-charge | Idempotency key + request fingerprint + stored response |
| Nobody can verify anything | Reconciliation job proves invariants continuously |

---

## 3. Double-entry accounting for engineers

You do not need to become an accountant. You need one mental model and five worked examples.

### 3.1 The model

- An **account** is a bucket with a running total. Users have **wallet accounts**. The system
  itself has **system accounts** that represent "the outside world" and "our own money".
- A **journal transaction** (a.k.a. journal entry, "txn") is one business event: a deposit, a
  transfer, a refund. It contains **two or more ledger entries**.
- A **ledger entry** is a single signed movement on a single account, belonging to exactly one
  journal transaction.
- **The invariant:** for every journal transaction, the entries **sum to zero**. Money is never
  created or destroyed inside the ledger; it only moves between accounts. If 100 leaves account
  A, 100 must arrive somewhere — a user wallet, a fee account, or the clearing account that
  represents the outside world.

That last point is the mental leap: *money entering the system from outside* is not "created".
It is moved **from** a system account (`external_clearing`) **to** the user's wallet. The
clearing account's balance goes negative (or, in accounting terms, it is a liability/contra
account whose sign convention is inverted). Its negative balance is the exact amount the system
"owes to" or "has received from" the outside world — which is itself useful information.

### 3.2 Debits and credits — choose a representation

Accountants say "debit" and "credit" and the sign depends on the account's type (asset vs
liability). Engineers get this wrong constantly. **Pick one convention, write it in
`DESIGN_DECISIONS.md`, enforce it in code and constraints:**

| Option | Representation | Pros | Cons |
|---|---|---|---|
| A. Signed amount | `amount NUMERIC(19,4)` — positive = money **into** this account, negative = money **out** | Sum-to-zero check is `SUM(amount) = 0`; balance is `SUM(amount)`; one column | "Debit/credit" vocabulary must be translated for accountants; must forbid `amount = 0` |
| B. Debit/credit columns | `debit NUMERIC(19,4)`, `credit NUMERIC(19,4)`, exactly one non-zero | Matches accounting textbooks and general-ledger exports | Balance = `SUM(credit) - SUM(debit)` (for a liability-style wallet); two columns; needs a `CHECK` that exactly one is > 0; sign conventions per account type |

**This spec uses Option A (signed amount) plus a `direction` label (`DEBIT`/`CREDIT`) derived
for display/export.** Justification: the invariants become single-column SQL, every test asserts
`SUM(amount) = 0`, and you cannot accidentally write a row with both columns filled. If an
interviewer asks "what about real accounting systems?", the answer is that many general ledgers
use two columns and a per-account normal-balance rule; the mapping is mechanical and you can
document it. You may choose B if you can defend it — the requirement is that the *system*
enforces the invariants, not the choice itself.

Convention for user wallets under Option A: **positive entry = the wallet gained money**.
Convention for `external_clearing`: mirror image — a deposit of 100 writes `+100` on the wallet
and `-100` on clearing.

### 3.3 System accounts

| Account | Type | Role |
|---|---|---|
| `external_clearing` | system | Counterparty for every deposit and withdrawal. Represents money outside LedgerX. |
| `fees` | system | Revenue: fees charged on withdrawals/transfers land here. |
| `suspense` | system | Parking account for money whose destination is unknown or in dispute (e.g. reconciliation finds an unmatched movement; a scheduled payment whose target was frozen). Nothing should live in suspense for long — a non-empty suspense account is an alert. |
| `pending_outbound` (optional, M3+) | system | Holds funds for a withdrawal that is initiated but not yet confirmed by the "external" side; released or returned when it completes/fails. |

System accounts are ordinary rows in `accounts` with `type = 'SYSTEM'` and no owner. They are
seeded by a Flyway migration and must never be deleted or frozen by API calls.

### 3.4 Why balances are derived

`balance(account) = SUM(amount) FROM ledger_entry WHERE account_id = ?`. That is the **truth**.
Everything else is a cache. LedgerX keeps a materialized `account_balance` row per account
(with a `version` column) because summing a million rows on every transfer is too slow, but the
materialized number is *verified* against the sum by the reconciliation job (M4) and by the
invariant test suite. If they ever disagree, the ledger wins and the materialized row is
flagged, not overwritten. This is the design's safety net: the mutable thing is checkable
against the immutable thing.

### 3.5 How reversals work

A reversal is a **new journal transaction** whose entries are the exact negation of the
original's entries, with `reversal_of = original_txn_id`. The original's `state` becomes
`REVERSED` (or `REFUNDED` for partial refunds, which write a *partial* negation with
`refund_of`). Nothing is updated in `ledger_entry` — ever. Consequences you must design for:

- A reversal of a reversal is rejected (`ALREADY_REVERSED`); "un-reversing" is a fresh transfer.
- A reversal must itself be idempotent (the client may retry it).
- Reversing a transfer whose receiver has since spent the money means the receiver goes
  negative under a strict rule — decide: refuse (`INSUFFICIENT_FUNDS` on the reversal), or allow
  system-initiated reversals to overdraw and flag for collections. Write the decision down.

### 3.6 Worked examples

All amounts in USD; columns are `(account, amount)`; every row set sums to zero.

**Deposit 100 into Alice's wallet** — txn type `DEPOSIT`

| Account | Amount |
|---|---:|
| alice_wallet | +100.0000 |
| external_clearing | −100.0000 |

Alice: 100. Clearing: −100 (the world has given us 100).

**Withdraw 50 with a 1.00 fee** — txn type `WITHDRAWAL`

| Account | Amount |
|---|---:|
| alice_wallet | −51.0000 |
| external_clearing | +50.0000 |
| fees | +1.0000 |

Alice: 49. Clearing: −50. Fees: 1. Sum of the three entries: 0.

**Transfer 30 from Alice to Bob** — txn type `TRANSFER`

| Account | Amount |
|---|---:|
| alice_wallet | −30.0000 |
| bob_wallet | +30.0000 |

Alice: 19. Bob: 30. Note that the system's total across all accounts (alice + bob + clearing +
fees) is still 0: 19 + 30 − 50 + 1 = 0. **The sum over all accounts is always zero** — that is
the global invariant the reconciliation job checks.

**Refund the transfer (full)** — txn type `REVERSAL`, `reversal_of = transfer txn`

| Account | Amount |
|---|---:|
| bob_wallet | −30.0000 |
| alice_wallet | +30.0000 |

Alice: 49. Bob: 0. Both original and reversal rows remain forever.

**Failed transfer** (Alice tries to send 500 with a balance of 49)

No ledger entries are written. A `journal_transaction` row *may* exist in state `FAILED` with
`failure_reason = INSUFFICIENT_FUNDS` if you choose to record attempts (recommended: it is
audit-useful and cheap). Design consequence: a `FAILED` transaction has zero entries, and the
invariant suite must assert "every `COMPLETED` transaction has ≥ 2 entries; every `FAILED`
transaction has 0".

**Pending → completed (two-phase withdrawal, M3 optional)**

Phase 1 (`PENDING`): `alice_wallet −50`, `pending_outbound +50`. Phase 2 on external
confirmation: `pending_outbound −50`, `external_clearing +50` (same or new txn — decide and
document). Phase 2 on external failure: `pending_outbound −50`, `alice_wallet +50` as a
compensating txn. Funds are never "in limbo" without an account holding them.

### 3.7 Interview one-liners you should own after this section

- "Why double-entry?" → every movement has a source and destination, so the ledger can never
  create money; the sum-to-zero invariant is machine-checkable.
- "Why derive balances?" → the truth is the append-only log; caches are verified against it.
- "How do you undo?" → you don't; you append a compensating transaction linked to the original.

---

## 4. Functional requirements

Requirement IDs are used in `milestones.md` acceptance criteria and in GitHub issues.

### 4.1 Users and accounts

- **FR-1** Register (email + password), login → JWT (reuse FlowGrid's approach, see
  [`../../05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md)).
- **FR-2** A user can open one or more wallet accounts (`type = USER_WALLET`), each with a
  fixed `currency` (ISO-4217 code; MVP: USD only, but store it).
- **FR-3** System accounts (`external_clearing`, `fees`, `suspense`) exist from migration; not
  creatable/deletable via API.
- **FR-4** Accounts have `status ∈ {ACTIVE, FROZEN, CLOSED}`. Frozen accounts reject all
  outgoing money movement (and, by policy decision, incoming — document it). Only ADMIN can freeze.

### 4.2 Ledger core

- **FR-5** Every money movement creates exactly one `journal_transaction` with ≥ 2
  `ledger_entry` rows summing to zero, written in one database transaction.
- **FR-6** `ledger_entry` is append-only: `UPDATE` and `DELETE` are rejected by the database
  (trigger), and the application role has no privilege to do them.
- **FR-7** Balance for an account is derivable as `SUM(amount)`; a materialized
  `account_balance` row is maintained and verifiable.
- **FR-8** A user wallet can never be overdrawn by a user-initiated operation (system-initiated
  reversals: see 3.5). Enforced both by application check and by a DB `CHECK` on
  `account_balance.balance >= 0` for wallet accounts.

### 4.3 Money operations

- **FR-9** Simulated deposit: `POST /accounts/{id}/deposits` (amount > 0).
- **FR-10** Simulated withdrawal with optional fee: `POST /accounts/{id}/withdrawals`.
- **FR-11** Internal transfer: `POST /transfers` (from, to, amount, currency, optional memo).
  Both accounts same currency; from ≠ to.
- **FR-12** All money-moving `POST`s require an `Idempotency-Key` header (UUID). Same key + same
  body → same response (no double charge). Same key + different body → `409 IDEMPOTENCY_CONFLICT`.
  Keys expire after 24 h (configurable).
- **FR-13** Transaction states: `PENDING → COMPLETED | FAILED`; `COMPLETED → REVERSED |
  REFUNDED`. Illegal transitions are rejected with `INVALID_STATE_TRANSITION`.
- **FR-14** Reversal (full) and refund (partial) of a `COMPLETED` transaction produce a linked
  compensating transaction. Reversal of a non-`COMPLETED` transaction is rejected.
- **FR-15** Payment requests: create (requester, payer, amount), accept (executes a transfer
  payer → requester with the request's own idempotency semantics), decline, expire.

### 4.4 Reads, audit, events

- **FR-16** Transaction history per account: cursor-paginated, newest first, stable under
  concurrent inserts, filterable by type/state/date range.
- **FR-17** `GET /transactions/{id}` shows the journal, its entries and links
  (`reversal_of`, `reversed_by`, `payment_request_id`).
- **FR-18** Audit events for: account create/freeze/close, every transaction state change,
  reversal/refund, admin actions, reconciliation runs and findings. Audit rows are append-only.
- **FR-19** Transactional outbox: `TransactionCompleted`, `TransactionReversed`,
  `AccountFrozen` events are written in the same DB transaction and published by a poller
  at-least-once with a monotonically increasing sequence.

### 4.5 Reconciliation and operations

- **FR-20** Reconciliation job (scheduled + on-demand ADMIN endpoint) checks: every journal sums
  to zero; every `account_balance.balance == SUM(entries)`; global sum over all accounts is 0;
  no `COMPLETED` txn without entries. Findings are persisted, alertable, and **never auto-fixed**.
- **FR-21** ADMIN endpoints: freeze/unfreeze, run reconciliation, view findings, view audit.
- **FR-21b** Independent verification (Python, `tools/ledgerx_verify`): a CLI that connects to
  the same PostgreSQL with a **read-only role**, recomputes every invariant in FR-20 with
  `decimal.Decimal`, and exits non-zero with a machine-readable report (JSON) on any violation.
  It shares no code with the Java service. A companion generator (`tools/ledgerx_gen`) drives
  realistic workloads through the REST API (with `Idempotency-Key`s, including deliberate
  duplicates), and a checker (`tools/ledgerx_check`) is run during the M4 crash/retry exercises
  to compare before/after snapshots.

### 4.6 Advanced (M5)

- **FR-22** Scheduled payments: a user schedules a transfer at a future time (one-off or simple
  recurrence). A scheduler executes it with idempotency (`schedule_id + occurrence` as key),
  handles insufficient funds (skip + notify vs retry policy — document).
- **FR-23** Risk rules: per-account configurable max single amount and velocity limit (e.g. ≤ N
  transfers or ≤ X total per rolling hour). Violations → `RISK_RULE_VIOLATION`; counters in
  Redis (optional) or Postgres.

---

## 5. Non-functional requirements

| ID | Requirement | How it is verified |
|---|---|---|
| NFR-1 **Correctness first** | No sequence of concurrent or retried API calls can produce an overdrawn wallet, a journal that does not sum to zero, or a double charge. | Concurrency tests, idempotency tests, invariant suite, DB constraints, reconciliation. |
| NFR-2 **Invariants are DB-enforced where possible** | `CHECK`s, trigger forbidding mutation of ledger rows, unique constraints on idempotency keys. Application checks are a *second* line. | Tests that attempt to violate each constraint directly with SQL and assert failure. |
| NFR-3 **Auditability** | Every state change is attributable (who, when, why, from what). | Audit table + tests; `GET` endpoints for audit. |
| NFR-4 **Idempotency** | Every money-moving POST is safe to retry, including after a crash between DB commit and HTTP response. | Fault-injection tests (M4). |
| NFR-5 **Measured throughput** | Report transfers/s and p95 latency on 1 hot account vs many accounts, with methodology. | Benchmark protocol in [`docs-and-resume.md`](./docs-and-resume.md). |
| NFR-6 **Money representation** | `BigDecimal` in Java, `NUMERIC(19,4)` in PostgreSQL, ISO-4217 currency code. Never `double`/`float`. | Architecture test (ArchUnit or a grep in CI) fails the build if `double`/`float` appears in ledger/transfers packages. |
| NFR-7 **Observability** | Structured JSON logs with txn id, idempotency key, account ids, state; Actuator health/metrics. | Log assertions in tests; manual inspection in failure exercises. |
| NFR-8 **Security** | Per-wallet authorization, ADMIN role gate, replay protection, input validation. | Security tests (403 for non-owner, 401 without token). |
| NFR-9 **Reproducibility** | `docker compose up` gives a working system; `mvn verify` runs the full suite with Testcontainers. | CI green on every PR. |

---

## 6. Scope tiers

| Tier | Contents | Tag |
|---|---|---|
| **MVP** (end of W10) | M1 + M2: accounts, immutable ledger, deposits/withdrawals, idempotent concurrent transfers, DB constraints, `$500/$400/$400` test green, Testcontainers CI | `v0.5` |
| **STRONG RESUME VERSION** (end of W13, primary target) | MVP + M3 (states, reversals/refunds, payment requests, history, audit, outbox) + M4 (reconciliation, fault injection, retry-after-crash, invariant suite, throughput measured) + deploy + docs set | **`v1.0`** |
| **ADVANCED** (W13 remainder / polish weeks) | Scheduled payments; basic risk rules; two-phase pending withdrawals; small React/TS admin/history view | `v1.1` |
| **OPTIONAL STRETCH** | Multi-currency accounts with an FX rate table (each leg still sums to zero *per currency*); statement PDF/CSV export to S3; optimistic-locking mode behind a flag with benchmark comparison; account sharding experiment | — |

If you slip: **keep M1–M4 depth, cut M5 advanced features.** A LedgerX with a bulletproof
transfer path and a real invariant suite beats one with scheduled payments and a flaky ledger.

---

## 7. Milestone summary

Full detail per milestone in [`milestones.md`](./milestones.md).

| Milestone | Week | Theme | Key deliverables | Hours |
|---|---|---|---|---|
| **M1** | 9 | Ledger core | Accounts (user/system), `journal_transaction`, append-only `ledger_entry` with trigger + revoked privileges, `CHECK`s, derived + materialized balances with invariant check, deposits/withdrawals, JWT auth, OpenAPI, CI | 26–28 |
| **M2** | 10 | Idempotent concurrent transfers | Idempotency store (key, fingerprint, status, response, TTL), transfer with ordered `SELECT … FOR UPDATE`, optimistic comparison branch, isolation experiments, `$500/$400/$400` concurrency test → **MVP** | 28 |
| **M3** | 11 | States, reversals, history, audit, outbox | State machine, reversals/refunds as compensating txns, payment requests, cursor-paginated history, audit log, transactional outbox + publisher | 28 |
| **M4** | 12 | Reconciliation, failure injection, invariants | Reconciliation job + findings, fault-injection hook, crash-between-steps tests, retry-after-crash idempotency tests, invariant suite, throughput measurement, **Python independent verifier + data generator + consistency checker (`tools/`, pytest-tested)** | 26 |
| **M5** | 13 | Deploy, docs, advanced | AWS deploy, CI/CD, docs set, scheduled payments, risk rules → **v1.0** (after M4 + deploy/docs), advanced tag after | 26 |

---

## 8. Architecture

### 8.1 Style

A **modular monolith**: one Spring Boot application, one PostgreSQL database, packages with
strict dependency direction (`api → application → domain ← infrastructure`). No microservices —
the whole point is that the transfer is *one* database transaction. Redis is optional and
non-authoritative (rate-limit counters, idempotency fast-path cache); the system must be fully
correct with Redis absent.

### 8.2 System diagram (ASCII spec)

```
                 ┌──────────────────────────────────────────────────────────────┐
                 │                         Clients                              │
                 │  curl / Postman / k6 / (optional React admin & history UI)   │
                 └───────────────┬──────────────────────────────┬───────────────┘
                                 │ HTTPS + JWT                   │
                                 ▼                              ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                          LedgerX (Spring Boot 3.x, Java 21)                       │
│                                                                                  │
│  api/            IdempotencyFilter ──► Controllers ──► ProblemDetail handler      │
│                     │ (key + fingerprint lookup/reserve)                          │
│  application/    TransferService · DepositService · ReversalService ·             │
│                  PaymentRequestService · ReconciliationService · SchedulerService │
│                     │ @Transactional boundaries live HERE                         │
│  domain/         Account · JournalTransaction · LedgerEntry · Money · States ·    │
│                  PostingRules (build balanced entry sets) · RiskRules             │
│  infrastructure/ JPA repos + native SQL (FOR UPDATE, cursor queries) ·            │
│                  IdempotencyStore · OutboxPublisher (poller) · AuditWriter ·      │
│                  FaultInjector (test hook) · Redis adapters (optional)            │
└───────┬──────────────────────────────┬───────────────────────────┬───────────────┘
        │ JDBC (HikariCP)              │ optional                  │ logs/metrics
        ▼                              ▼                           ▼
┌─────────────────────┐      ┌──────────────────┐       ┌────────────────────────┐
│  PostgreSQL 16      │      │  Redis 7         │       │ CloudWatch / Actuator  │
│  accounts           │      │  rate-limit ctrs │       └────────────────────────┘
│  journal_transaction│      │  idem fast cache │
│  ledger_entry (RO)  │      └──────────────────┘
│  account_balance    │
│  idempotency_key    │               outbox poller ──► stdout/log sink (MVP)
│  payment_request    │                                 ──► SQS/SNS (optional, M5)
│  audit_event        │
│  outbox_event       │
│  recon_run/finding  │
└─────────┬───────────┘
          │ read-only role (ledgerx_verify), psycopg 3, Decimal
          ▼
┌────────────────────────────────────────────────────────────────┐
│  tools/ (Python 3.12, independent of the Java code)             │
│  ledgerx_verify  — recomputes every invariant from raw rows    │
│  ledgerx_gen     — drives workloads via REST (+ duplicate keys) │
│  ledgerx_check   — before/after snapshot diff in crash drills   │
└────────────────────────────────────────────────────────────────┘
```

The Python tools are a **second, independent opinion**. The Java reconciliation job (M4) is
part of the system under test; if the Java code has a bug in how it sums entries, its own
reconciliation might share that bug. A verifier in a different language, reading the tables
directly with exact decimals, cannot inherit a Java bug. This is the same reason auditors do
not rely on the auditee's spreadsheet.

### 8.3 The transfer path (the heart of the system)

```
POST /transfers  (Idempotency-Key: K, body B)
  │
  ├─ 1. Validate body (amount > 0, scale ≤ 4, from ≠ to, currency)         → 400 VALIDATION_ERROR
  ├─ 2. AuthZ: caller owns `from`                                            → 403 NOT_ACCOUNT_OWNER
  ├─ 3. Idempotency: INSERT idempotency_key(K, fp=hash(B), status=IN_PROGRESS)
  │       ├─ conflict, same fp, COMPLETED   → replay stored response (same status/body)
  │       ├─ conflict, same fp, IN_PROGRESS → 409 IDEMPOTENCY_IN_PROGRESS (Retry-After)
  │       └─ conflict, different fp         → 409 IDEMPOTENCY_CONFLICT
  ├─ 4. BEGIN (READ COMMITTED)
  │     4a. SELECT ... FROM account_balance WHERE account_id IN (lo, hi)
  │           ORDER BY account_id FOR UPDATE            ← lock in id order, both rows
  │     4b. Check status ACTIVE on both, currency match, funds ≥ amount     → FAILED txn + error
  │     4c. INSERT journal_transaction (COMPLETED, TRANSFER, idem key K)
  │     4d. INSERT ledger_entry ×2 (−amount from, +amount to)   ← immutable
  │     4e. UPDATE account_balance ×2 (balance, version+1, last_entry_id)
  │     4f. INSERT audit_event, INSERT outbox_event
  │     4g. UPDATE idempotency_key SET status=COMPLETED, response=..., http_status=201
  │   COMMIT
  └─ 5. Return 201 with the stored response
```

Design rules encoded in that sequence (justified in `milestones.md` M2):

- **One DB transaction** covers steps 4a–4g. Either everything is durable or nothing is.
- **Lock ordering by account id** prevents A→B / B→A deadlocks by construction.
- **Idempotency finalization is inside the same transaction** as the ledger writes (4g). That is
  what makes "crash after commit, before response" safe: the retry finds `COMPLETED` and replays.
  If the key row lived elsewhere (e.g. Redis), you would need a separate recovery path.
- **The `IN_PROGRESS` reservation (step 3) is its own short transaction** committed before
  step 4, so that a concurrent duplicate sees it. If the process dies between 3 and 4, the key
  is stuck `IN_PROGRESS`: the retry must detect staleness (age > timeout) and either take over or
  return 409 with `Retry-After` — a scenario in `failure-engineering.md`.
- `READ COMMITTED` + explicit row locks is the chosen isolation. `SERIALIZABLE` is evaluated
  in M2's experiments (it works, at the cost of retry loops on `40001` serialization failures).

### 8.4 Architecture diagram specification (what you draw for `ARCHITECTURE.md`)

Produce two diagrams (draw.io, Excalidraw, or Mermaid rendered to PNG — commit the source):

1. **Component diagram** — the boxes in §8.2 with arrows labelled by protocol (HTTPS/JWT, JDBC,
   RESP for Redis, "poll" for the outbox publisher). Mark which arrows carry money-changing
   writes. Show Redis with a dashed border and a note "non-authoritative; system correct without it".
2. **Transfer sequence diagram** — actors: Client, IdempotencyFilter, TransferService,
   PostgreSQL. Show both the happy path and the two branches: duplicate key (replay) and
   insufficient funds (`FAILED` journal, no entries). Annotate the transaction boundary as a box
   around 4a–4g and mark where `FOR UPDATE` locks are taken and released (at COMMIT).

Optional third diagram for M4: the reconciliation job's data flow (read entries → compute →
compare → write findings → emit audit/outbox → alert).

---

## 9. Database design

Full DDL is yours to write in Flyway migrations (`V1__accounts.sql`, `V2__ledger.sql`, …).
Below is the design specification with the constraints that *must* exist.

### 9.1 ERD specification

```
users 1───* accounts 1───1 account_balance
                │
                │ 1
                │
                * ledger_entry *───1 journal_transaction 1───0..1 idempotency_key
                                          │  ▲
                                          │  │ reversal_of / refund_of (self-reference)
                                          │
                                          0..1 payment_request

journal_transaction 1───* audit_event (entity_type='TXN')
journal_transaction 1───* outbox_event
reconciliation_run 1───* reconciliation_finding
scheduled_payment 1───* journal_transaction (via schedule_id, M5)
risk_rule *───1 accounts (M5)
```

### 9.2 Tables

**`users`** — `id UUID PK`, `email CITEXT UNIQUE`, `password_hash`, `role` (`USER`/`ADMIN`),
`created_at TIMESTAMPTZ`.

**`accounts`**
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` identity PK (or UUID — see note) | **Use `BIGINT` for accounts**: lock ordering by id must be deterministic and cheap; UUID v4 ordering is fine too but `BIGINT` reads better in explain plans and tests |
| `owner_id` | `UUID NULL FK users` | `NULL` for system accounts |
| `type` | `TEXT CHECK (type IN ('USER_WALLET','SYSTEM'))` | |
| `system_code` | `TEXT UNIQUE NULL` | `external_clearing`, `fees`, `suspense`, `pending_outbound` |
| `currency` | `CHAR(3)` | ISO-4217 |
| `status` | `TEXT CHECK (status IN ('ACTIVE','FROZEN','CLOSED'))` | |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | |

Constraint: `CHECK ((type='SYSTEM' AND owner_id IS NULL AND system_code IS NOT NULL) OR
(type='USER_WALLET' AND owner_id IS NOT NULL AND system_code IS NULL))`.

**`journal_transaction`**
| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | client-visible id; generated server-side |
| `type` | `TEXT` | `DEPOSIT`, `WITHDRAWAL`, `TRANSFER`, `REVERSAL`, `REFUND`, `PAYMENT_REQUEST_SETTLEMENT`, `SCHEDULED_TRANSFER`, `RECON_ADJUSTMENT` (admin only) |
| `state` | `TEXT` | `PENDING`, `COMPLETED`, `FAILED`, `REVERSED`, `REFUNDED` |
| `amount` | `NUMERIC(19,4) CHECK (amount > 0)` | the business amount (fees are separate entries) |
| `currency` | `CHAR(3)` | |
| `idempotency_key_id` | `UUID NULL FK idempotency_key UNIQUE` | one key → at most one txn |
| `reversal_of` | `UUID NULL FK journal_transaction` | set on REVERSAL/REFUND txns |
| `payment_request_id` | `UUID NULL FK payment_request` | |
| `schedule_id`, `occurrence_at` | nullable (M5) | `UNIQUE (schedule_id, occurrence_at)` |
| `initiated_by` | `UUID NULL FK users` | null for system/scheduler |
| `failure_code` | `TEXT NULL` | e.g. `INSUFFICIENT_FUNDS` |
| `memo` | `TEXT NULL` | ≤ 140 chars |
| `created_at`, `completed_at` | `TIMESTAMPTZ` | |

Constraints: `CHECK ((state IN ('REVERSED','REFUNDED')) OR TRUE)` is not enough — enforce state
transitions in the application, but add `CHECK ((type IN ('REVERSAL','REFUND')) = (reversal_of IS
NOT NULL))`. Partial unique index: `UNIQUE (reversal_of) WHERE type = 'REVERSAL'` — a txn can be
fully reversed at most once (refunds can be many, bounded by amount in application code).

**`ledger_entry`** — the immutable core
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` identity PK | monotonic; used as a stable cursor |
| `txn_id` | `UUID FK journal_transaction NOT NULL` | |
| `account_id` | `BIGINT FK accounts NOT NULL` | |
| `amount` | `NUMERIC(19,4) NOT NULL CHECK (amount <> 0)` | signed; + = into account |
| `currency` | `CHAR(3) NOT NULL` | denormalized on purpose: reconciliation groups by currency |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | |

Constraints and rules:
- `UNIQUE (txn_id, account_id)` — one entry per account per transaction. (If you later need
  fee legs on the same account, drop this and add `leg_no`; document the change.) This makes
  "a transfer is exactly two rows" checkable.
- **Trigger forbidding mutation** (application-level roles are revoked too):

```sql
CREATE OR REPLACE FUNCTION ledger_entry_immutable() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'ledger_entry is append-only (% attempted)', TG_OP
    USING ERRCODE = 'restrict_violation';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entry_immutable
  BEFORE UPDATE OR DELETE ON ledger_entry
  FOR EACH ROW EXECUTE FUNCTION ledger_entry_immutable();

-- belt and braces: the app role cannot even try
REVOKE UPDATE, DELETE, TRUNCATE ON ledger_entry FROM ledgerx_app;
```

- The trigger also covers `TRUNCATE` only if you add a statement-level `BEFORE TRUNCATE`
  trigger — do so.
- **Balanced-journal check.** A deferred constraint trigger `AFTER INSERT ON ledger_entry
  DEFERRABLE INITIALLY DEFERRED` that raises if `SUM(amount) FROM ledger_entry WHERE txn_id =
  NEW.txn_id` ≠ 0 at commit time makes the sum-to-zero invariant DB-enforced. It costs one
  aggregate per transaction; measure it in M4 and keep it unless the benchmark proves it hurts.

**`account_balance`** — materialized cache
| Column | Type | Notes |
|---|---|---|
| `account_id` | `BIGINT PK FK accounts` | 1:1 |
| `balance` | `NUMERIC(19,4) NOT NULL` | |
| `version` | `BIGINT NOT NULL DEFAULT 0` | for the optimistic-locking comparison branch and for detecting concurrent modification in tests |
| `last_entry_id` | `BIGINT NULL FK ledger_entry` | lets reconciliation do incremental checks: `SUM(entries WHERE id <= last_entry_id)` |
| `updated_at` | `TIMESTAMPTZ` | |

Constraint: `CHECK (balance >= 0)` is **wrong** as a global rule because system accounts go
negative. Either (a) put a `min_balance NUMERIC(19,4)` column on `accounts` (0 for wallets,
unbounded negative for system) and check in a trigger, or (b) enforce in application and add a
trigger that joins `accounts.type`. Choose (a): explicit, queryable, and lets you model an
overdraft limit later. Snippet-sized trigger:

```sql
CREATE OR REPLACE FUNCTION account_balance_floor() RETURNS trigger AS $$
DECLARE floor_amt NUMERIC(19,4);
BEGIN
  SELECT min_balance INTO floor_amt FROM accounts WHERE id = NEW.account_id;
  IF NEW.balance < floor_amt THEN
    RAISE EXCEPTION 'INSUFFICIENT_FUNDS: account % would fall to % (< %)',
      NEW.account_id, NEW.balance, floor_amt USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_account_balance_floor BEFORE INSERT OR UPDATE ON account_balance
  FOR EACH ROW EXECUTE FUNCTION account_balance_floor();
```

**`idempotency_key`**
| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `key` | `TEXT NOT NULL` | the header value |
| `owner_id` | `UUID NOT NULL FK users` | keys are scoped per user: `UNIQUE (owner_id, key)` — two users can reuse a key without colliding |
| `endpoint` | `TEXT NOT NULL` | method + path template; part of the fingerprint |
| `fingerprint` | `CHAR(64) NOT NULL` | SHA-256 of canonical JSON body + endpoint |
| `status` | `TEXT CHECK (status IN ('IN_PROGRESS','COMPLETED','FAILED'))` | |
| `http_status` | `SMALLINT NULL` | |
| `response_body` | `JSONB NULL` | stored verbatim for replay |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | staleness detection for stuck `IN_PROGRESS` |
| `expires_at` | `TIMESTAMPTZ NOT NULL` | `created_at + 24h`; a cleanup job deletes expired rows |

**`payment_request`** — `id`, `requester_account_id`, `payer_account_id`, `amount`, `currency`,
`status` (`OPEN`, `ACCEPTED`, `DECLINED`, `EXPIRED`, `CANCELLED`), `settlement_txn_id NULL`,
`expires_at`, `memo`, timestamps. `CHECK (requester_account_id <> payer_account_id)`.

**`audit_event`** — `id BIGINT`, `occurred_at`, `actor_id NULL`, `actor_type`
(`USER`/`ADMIN`/`SYSTEM`), `entity_type`, `entity_id`, `action`, `before JSONB NULL`,
`after JSONB NULL`, `request_id`, `idempotency_key NULL`. Append-only (same trigger pattern).

**`outbox_event`** — `id BIGINT identity` (the publishing sequence), `aggregate_type`,
`aggregate_id`, `event_type`, `payload JSONB`, `created_at`, `published_at NULL`,
`attempts INT DEFAULT 0`. Index on `(published_at) WHERE published_at IS NULL`.

**`reconciliation_run`** / **`reconciliation_finding`** — run: `id`, `started_at`,
`finished_at`, `status`, `entries_scanned`, `accounts_checked`. Finding: `run_id`, `kind`
(`UNBALANCED_TXN`, `BALANCE_DRIFT`, `GLOBAL_SUM_NONZERO`, `COMPLETED_WITHOUT_ENTRIES`,
`SUSPENSE_NONEMPTY`), `entity_id`, `expected`, `actual`, `resolved_at NULL`, `resolution_note`.

**`scheduled_payment`**, **`risk_rule`** — M5; specified in `milestones.md`.

### 9.3 Indexes

| Index | Why |
|---|---|
| `ledger_entry (account_id, id DESC)` | history pagination by cursor; per-account sums |
| `ledger_entry (txn_id)` | fetch a journal's entries; sum-to-zero check |
| `journal_transaction (created_at DESC, id)` | admin listing |
| `journal_transaction (reversal_of)` | find reversals of X |
| `idempotency_key (owner_id, key) UNIQUE` | the idempotency lookup |
| `idempotency_key (expires_at)` | cleanup job |
| `outbox_event (id) WHERE published_at IS NULL` | poller reads unpublished in order |
| `audit_event (entity_type, entity_id, id DESC)` | per-entity audit view |
| `payment_request (payer_account_id, status)` | inbox view |

Do **not** index `ledger_entry.amount`. Run `EXPLAIN (ANALYZE, BUFFERS)` on the history query
and the reconciliation sum in M4 and record the plans in `PERFORMANCE.md`.

### 9.4 Money: `BigDecimal` + `NUMERIC`, never floating point

- `0.1 + 0.2 == 0.30000000000000004` in `double`. Multiply that by a million transactions and
  the ledger no longer sums to zero. Floating point represents binary fractions; money is decimal.
- Java: `BigDecimal` with an explicit `MathContext`/scale. Define a `Money` value type (record
  of `BigDecimal amount` + `Currency`), always `setScale(4, RoundingMode.HALF_EVEN)` on entry,
  compare with `compareTo` (never `equals`, which is scale-sensitive: `1.0 ≠ 1.00`).
- PostgreSQL: `NUMERIC(19,4)` — exact decimal, 15 integer digits, 4 fractional (allows
  sub-cent fee calculations; display rounds to 2). JPA maps `BigDecimal` ↔ `NUMERIC` cleanly;
  declare `@Column(precision = 19, scale = 4)`.
- Reject request amounts with more than 4 decimal places (`400 VALIDATION_ERROR`) rather than
  silently rounding.
- Alternative many fintechs use: `BIGINT` minor units (cents). Fine, but then a `currency` with
  different minor-unit counts (JPY has 0, BHD has 3) becomes a per-currency rule. `NUMERIC` keeps
  this simple. Write an ADR either way.
- Add a CI guard: an ArchUnit rule (or a `grep -rn "double\|float" src/main/java/.../ledger`)
  that fails the build if primitives sneak into money code.

### 9.5 Transactions and isolation (summary; details in M2)

- PostgreSQL default is `READ COMMITTED`. Each statement sees a fresh snapshot; a `SELECT … FOR
  UPDATE` blocks until conflicting row locks are released and then *re-reads the current row
  version*, so the funds check after the lock is safe.
- `REPEATABLE READ` / `SERIALIZABLE` use a transaction-wide snapshot and abort with SQLSTATE
  `40001` on conflict; correct, but the application must retry. Measure both in M2.
- Deadlocks: PostgreSQL detects them (`deadlock_timeout`, default 1 s) and aborts one victim
  with `40P01`. Lock ordering eliminates the A→B/B→A case; a retry loop handles the rest.
- See [`../../04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md)
  and [`../../05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md).

---

## 10. API design

Conventions: JSON; `application/problem+json` errors (RFC 9457 `ProblemDetail`); all ids are
UUIDs except account ids (numeric); amounts are **strings** in JSON (`"amount": "100.0000"`)
to avoid client-side float parsing; times are ISO-8601 UTC. Versioned under `/api/v1`. See
[`../../06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md).

### 10.1 Endpoint table

| Method | Path | Auth | Idempotency-Key | Purpose |
|---|---|---|---|---|
| `POST` | `/auth/register` | — | — | Create user |
| `POST` | `/auth/login` | — | — | JWT |
| `POST` | `/accounts` | USER | — | Open a wallet (currency) |
| `GET` | `/accounts` | USER | — | My accounts with balances |
| `GET` | `/accounts/{id}` | owner/ADMIN | — | Account + balance (+ `derivedBalance` when `?verify=true`, ADMIN) |
| `POST` | `/accounts/{id}/deposits` | owner | **required** | Simulated deposit |
| `POST` | `/accounts/{id}/withdrawals` | owner | **required** | Simulated withdrawal (+fee) |
| `POST` | `/transfers` | owner of `from` | **required** | Internal transfer |
| `GET` | `/transactions/{id}` | participant/ADMIN | — | Journal + entries + links |
| `POST` | `/transactions/{id}/reversals` | ADMIN (or original sender, policy) | **required** | Full reversal |
| `POST` | `/transactions/{id}/refunds` | receiver of original | **required** | Partial refund `{amount}` |
| `GET` | `/accounts/{id}/transactions?cursor=&limit=&type=&state=&from=&to=` | owner/ADMIN | — | History, cursor paginated |
| `POST` | `/payment-requests` | owner of requester acct | **required** | Create request |
| `POST` | `/payment-requests/{id}/accept` | owner of payer acct | **required** | Settle (creates transfer) |
| `POST` | `/payment-requests/{id}/decline` | owner of payer acct | — | Decline |
| `GET` | `/payment-requests?role=payer|requester&status=` | USER | — | Inbox/outbox |
| `GET` | `/audit?entityType=&entityId=&cursor=` | ADMIN (owner for own entities) | — | Audit trail |
| `POST` | `/admin/accounts/{id}/freeze` · `/unfreeze` | ADMIN | — | Freeze/unfreeze |
| `POST` | `/admin/reconciliation/runs` | ADMIN | — | Trigger a run (202 + run id) |
| `GET` | `/admin/reconciliation/runs/{id}` | ADMIN | — | Status + findings |
| `POST` | `/scheduled-payments` · `DELETE …/{id}` | owner | required on POST | M5 |
| `PUT` | `/admin/accounts/{id}/risk-rules` | ADMIN | — | M5 |
| `GET` | `/actuator/health` | — | — | Liveness/readiness |

### 10.2 Idempotency contract

- Header `Idempotency-Key` is **required** on every row marked *required*; missing → `400
  IDEMPOTENCY_KEY_REQUIRED`. Format: UUID (or ≤ 64 chars `[A-Za-z0-9_-]`).
- Scope: per authenticated user per endpoint.
- Same key + same fingerprint → replay the original response (same status code and body),
  plus header `Idempotent-Replayed: true`.
- Same key + different fingerprint → `409 IDEMPOTENCY_CONFLICT`.
- Same key, original still `IN_PROGRESS` → `409 IDEMPOTENCY_IN_PROGRESS` with `Retry-After: 1`.
- Keys expire after 24 h; a replay after expiry is treated as a new request (document that
  clients must not retry beyond the TTL).
- Failed outcomes are stored too: a `422 INSUFFICIENT_FUNDS` is replayed as `422` for the same
  key (you do *not* re-attempt a failed transfer under the same key — the client must send a
  new key to try again after topping up). Write this down; interviewers ask.

### 10.3 Cursor pagination for history

`GET /accounts/{id}/transactions?limit=50&cursor=<opaque>`. The cursor encodes
`(created_at, ledger_entry.id)` of the last item (base64 of `"<epochMillis>:<id>"`). Query:
`WHERE account_id = ? AND (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT ?+1`.
Response: `{ "items": [...], "nextCursor": "..." | null }`. Offset pagination is rejected
because concurrent inserts shift pages and `OFFSET 100000` scans 100k rows.

### 10.4 Error catalogue

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Bad body/params (amount ≤ 0, > 4 decimals, from = to, bad currency) |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | Missing/invalid header on a money-moving POST |
| 401 | `UNAUTHENTICATED` | No/invalid JWT |
| 403 | `NOT_ACCOUNT_OWNER` | Caller does not own the source account |
| 403 | `ADMIN_REQUIRED` | Non-admin calling admin endpoint |
| 404 | `ACCOUNT_NOT_FOUND` / `TRANSACTION_NOT_FOUND` / `PAYMENT_REQUEST_NOT_FOUND` | |
| 409 | `IDEMPOTENCY_CONFLICT` | Same key, different fingerprint |
| 409 | `IDEMPOTENCY_IN_PROGRESS` | Same key, first request still running |
| 409 | `INVALID_STATE_TRANSITION` | e.g. accept a DECLINED request |
| 409 | `ALREADY_REVERSED` | Reverse a reversed txn |
| 409 | `CONCURRENT_MODIFICATION` | Optimistic-mode retry exhausted |
| 422 | `INSUFFICIENT_FUNDS` | Funds check failed (a `FAILED` journal row is recorded) |
| 422 | `ACCOUNT_FROZEN` / `ACCOUNT_CLOSED` | Source (or destination, by policy) not ACTIVE |
| 422 | `CURRENCY_MISMATCH` | from/to currencies differ |
| 422 | `REFUND_EXCEEDS_ORIGINAL` | Cumulative refunds > original amount |
| 422 | `RISK_RULE_VIOLATION` | M5 |
| 429 | `RATE_LIMITED` | Optional Redis limiter |
| 500 | `INTERNAL_ERROR` | Never leaks stack traces; includes `requestId` |
| 503 | `DEPENDENCY_UNAVAILABLE` | DB down; idempotency store unavailable (see failure scenario 6) |

Every error body: `{ "type": "https://ledgerx.dev/errors/INSUFFICIENT_FUNDS", "title": …,
"status": 422, "code": "INSUFFICIENT_FUNDS", "detail": …, "instance": "/api/v1/transfers",
"requestId": "…", "transactionId": "…" (when a FAILED journal was recorded) }`.

### 10.5 OpenAPI expectations

springdoc-openapi generates the spec; you **curate** it: every endpoint has a summary, every
error code above appears in the responses of the endpoints that can raise it, the
`Idempotency-Key` header is declared as a reusable parameter, and the money schema documents
`amount` as a decimal string with pattern `^\d{1,15}(\.\d{1,4})?$`. Export `openapi.json` in CI
and commit it under `docs/api/` so PRs show API diffs.

---

## 11. Package structure

```
com.ledgerx
├── LedgerXApplication
├── accounts/        # Account, AccountBalance, AccountService, AccountController, freeze
├── ledger/          # JournalTransaction, LedgerEntry, Money, PostingRules, LedgerService
│                    #   (the ONLY package allowed to insert ledger rows)
├── transfers/       # TransferService (locking, funds check), DepositService, WithdrawalService
├── idempotency/     # IdempotencyKey, IdempotencyStore, IdempotencyFilter/Interceptor, fingerprint
├── reversals/       # ReversalService, RefundService (compensating txns)
├── paymentrequests/ # PaymentRequest, state machine, settlement
├── history/         # cursor queries, HistoryController
├── audit/           # AuditEvent, AuditWriter (called inside the same transaction)
├── outbox/          # OutboxEvent, OutboxWriter, OutboxPublisher (@Scheduled poller)
├── reconciliation/  # ReconciliationJob, checks, findings, admin endpoints
├── risk/            # RiskRule, RiskEvaluator (M5), Redis velocity counters (optional)
├── scheduling/      # ScheduledPayment, PaymentScheduler (M5)
├── auth/            # JWT, users, security config, ownership checks
├── faults/          # FaultInjector interface + NoOp impl (test profile swaps a real one)
└── common/          # ProblemDetail handler, error codes, request id filter, JSON config
```

Repository layout around the Java package (the Python component lives beside it, not inside):

```
ledgerx/
├── pom.xml, src/main/java/com/ledgerx/..., src/test/java/...
├── docs/                      # ARCHITECTURE.md, DATABASE.md, ... (see docs-and-resume.md)
├── db/init/                   # role creation SQL used by Compose (ledgerx_app, ledgerx_migrate, ledgerx_verify)
├── compose.yaml, Dockerfile
└── tools/                     # Python 3.12 — independent of the Java code
    ├── pyproject.toml         # psycopg[binary]>=3, httpx, pytest, mypy, ruff
    ├── README.md              # how to run each tool, exit codes, report format
    ├── ledgerx_verify/        # verifier: invariants recomputed with decimal.Decimal
    │   ├── __init__.py, cli.py, checks.py, db.py, report.py
    ├── ledgerx_gen/           # workload generator: users, accounts, deposits, transfers, duplicates
    ├── ledgerx_check/         # consistency checker: snapshot → compare → diff (crash/retry drills)
    └── tests/                 # pytest: unit (checks on fixtures) + integration (against Compose Postgres)
```

Rules (enforce with ArchUnit tests): only `ledger` writes `ledger_entry`; `api` classes never
call repositories directly; `domain` types (`Money`, `PostingRules`, state enums) have no
Spring imports; no `double`/`float` anywhere in `ledger`, `transfers`, `reversals`.

---

## 12. Authentication and authorization

- **AuthN:** JWT (HS256 for MVP, secret from env; RS256 optional later), 15-min access token,
  refresh optional. Reuse your FlowGrid implementation — do not spend LedgerX hours here.
- **AuthZ model:**
  - `USER` owns zero or more wallet accounts. A user may move money **only out of** accounts
    they own; may deposit only into their own accounts; may view only their own accounts,
    transactions in which they are a participant, and audit events on their own entities.
  - `ADMIN` may: freeze/unfreeze/close accounts, run reconciliation, view all audit, create
    `RECON_ADJUSTMENT` journals (each requires a reason, is audited, and moves money only to or
    from `suspense`), reverse any transaction.
  - System accounts have no owner; only the application (never a user request) posts to them.
- Enforce ownership in the service layer with an explicit `requireOwner(accountId, principal)`
  check *inside* the transaction (after the lock), so that a freeze/ownership change committed a
  millisecond earlier is seen. Method security (`@PreAuthorize("hasRole('ADMIN')")`) gates admin
  endpoints.
- Tests: every endpoint has a `401` test and a `403` test (non-owner, non-admin).

---

## 13. Testing strategy

LedgerX must have the strongest suite of the four projects. Target: `mvn verify` runs
everything below against a real PostgreSQL via Testcontainers in < 5 minutes. See
[`../../09-testing/testcontainers.md`](../../09-testing/testcontainers.md),
[`../../09-testing/spring-testing.md`](../../09-testing/spring-testing.md),
[`../../09-testing/junit5.md`](../../09-testing/junit5.md).

| Layer | Tool | What |
|---|---|---|
| **Unit** | JUnit 5 + Mockito | `Money` arithmetic/scale/rounding; `PostingRules` builds balanced entry sets for every txn type; state-machine transitions; fingerprint canonicalization (key order, whitespace); cursor encode/decode |
| **Integration (DB)** | `@SpringBootTest` + Testcontainers Postgres 16 | Every service against real SQL: constraints, triggers, migrations, native queries |
| **Transaction tests** | Testcontainers | Prove boundaries: an exception after entries are written rolls back the journal, balance and outbox together; `@Transactional` self-invocation pitfall reproduced and fixed |
| **Concurrency tests** | JUnit + `ExecutorService`/`CountDownLatch` + Testcontainers | The named mandatory tests below; N-thread hammer tests asserting invariants after the storm |
| **Idempotency tests** | Testcontainers | Same key same body → same response, one journal; same key different body → 409; parallel same-key → exactly one executes |
| **Invariant suite** | Testcontainers, property-style loops | After random workloads (deposits/withdrawals/transfers/reversals with random amounts, N threads): every journal sums to 0; global sum is 0; every balance = SUM(entries); no wallet negative; every COMPLETED txn has ≥ 2 entries |
| **Fault injection** | `FaultInjector` hook + Testcontainers | Crash before commit; after entries before balance update; after commit before response; outbox publisher mid-batch; idempotency store timeout |
| **API/contract** | `MockMvc` / `WebTestClient` | Status codes, ProblemDetail shape, header contract (`Idempotent-Replayed`), OpenAPI snapshot |
| **Security** | `MockMvc` + `@WithMockUser` | 401/403 matrix per endpoint |
| **Architecture** | ArchUnit | Package rules; no floating point in money packages |
| **Migration** | Testcontainers | Flyway from empty → head; a test that attempts `UPDATE ledger_entry` and expects failure; a test attempting to insert an unbalanced journal expects failure at commit |
| **Load** | k6 (external) | Benchmarks per `docs-and-resume.md` — not part of `mvn verify` |
| **Independent verification (Python)** | `tools/ledgerx_verify` + pytest | A second implementation of every reconciliation invariant, in a different language, reading raw tables with `decimal.Decimal`. Run after every failure exercise, after every benchmark, and as a post-deploy check. Its own pytest suite proves it *detects* injected violations (drift, unbalanced journal, negative wallet, orphan entries) on fixture data — a verifier that has never been seen to fail is untested |

### 13.0 Independent verification — why a second implementation

The Java reconciliation job and the Java invariant tests are written by the same person, with
the same mental model, against the same `Money` class. A systematic mistake (wrong sign
convention on a system account, a scale/rounding slip, an `equals` vs `compareTo` bug on
`BigDecimal`) can be present in both the code and the test that "verifies" it. The Python
verifier breaks that circularity: it knows nothing about `PostingRules`, it only knows the
table contract from `docs/DATABASE.md`. Rules for it:

- Connects as `ledgerx_verify`, a role with `SELECT` only. It cannot fix anything, by construction.
- Uses `decimal.Decimal` everywhere; `float` is banned (a `ruff`/grep rule fails CI if `float(`
  appears). psycopg 3 returns `NUMERIC` as `Decimal` by default — do not override that adapter.
- Recomputes: per-journal sum = 0; global sum = 0 per currency; `account_balance.balance ==
  SUM(entries)` per account; no `USER_WALLET` below `min_balance`; every `COMPLETED` journal has
  ≥ 2 entries and every `FAILED` has 0; every `REVERSAL` negates its original exactly; no
  `ledger_entry` without a `journal_transaction` (orphan); idempotency keys `COMPLETED` with a
  `201` response reference exactly one journal.
- Emits a JSON report (`{"ok": false, "findings": [{"kind": "BALANCE_DRIFT", "account_id": 42,
  "expected": "100.0000", "actual": "99.9900"}]}`) and exit code 0/1/2 (ok / findings / error).
- pytest suite: unit tests run the check functions against small in-memory fixtures (lists of
  rows) so no DB is needed; integration tests run against the Compose Postgres, insert a
  violation as a superuser, and assert the verifier reports it. See
  [`../../19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md).

### 13.1 Named mandatory tests (must exist with these names)

| Test | Assertion |
|---|---|
| `transfer_whenTwoConcurrent400sOn500Balance_exactlyOneSucceeds` | Two threads, same source (500), each transfers 400: exactly one `201`, one `422 INSUFFICIENT_FUNDS`; source balance = 100; destination = 400; exactly one COMPLETED journal with 2 entries; one FAILED journal with 0 entries |
| `transfer_whenNThreadsHammerOneAccount_neverNegativeAndSumZero` | 32 threads × 50 random transfers out of one account: final balance ≥ 0; `SUM(all entries) = 0`; balance = derived |
| `transfer_sameKeySameBody_returnsSameResponseNoDoubleCharge` | Second call returns identical body/status, `Idempotent-Replayed: true`, exactly one journal, balance moved once |
| `transfer_sameKeyDifferentBody_returns409` | `IDEMPOTENCY_CONFLICT`; no journal created by the second call |
| `transfer_sameKeyConcurrent_exactlyOneExecutes` | 10 threads same key/body: one executes, others replay or get `IN_PROGRESS`; one journal |
| `transfer_aToBAndBToAConcurrently_noDeadlockBothSucceed` | 100 iterations of opposite-direction transfers in parallel: zero `40P01`, all succeed, invariants hold |
| `ledger_everyJournalSumsToZero_property` | Random workload then `SELECT txn_id FROM ledger_entry GROUP BY txn_id HAVING SUM(amount) <> 0` returns 0 rows |
| `ledger_updateOrDeleteEntry_rejectedByDatabase` | Native `UPDATE`/`DELETE` raise `restrict_violation` |
| `ledger_unbalancedJournal_rejectedAtCommit` | Inserting entries summing to 5 fails on commit (deferred trigger) |
| `balance_materializedEqualsDerived_afterRandomWorkload` | For every account, `account_balance.balance == SUM(entries)` |
| `reversal_ofReversedTxn_returns409` | `ALREADY_REVERSED` |
| `reversal_fullReversal_restoresBothBalancesAndLinks` | Balances restored; `reversal_of` and state `REVERSED` set; 4 entries total |
| `crashAfterEntriesBeforeBalance_retryIsConsistent` | Fault after step 4d aborts the txn: no partial rows; retry with same key succeeds exactly once |
| `crashAfterCommitBeforeResponse_retryReplaysStoredResponse` | Fault after COMMIT: retry returns 201 with the *same* transaction id; balances moved once |
| `idempotency_staleInProgressKey_isRecoverable` | Key stuck `IN_PROGRESS` older than timeout: retry takes over or returns 409 + `Retry-After` per your policy, never double-executes |
| `reconciliation_detectsInjectedDrift_andDoesNotAutoFix` | Admin-only direct SQL alters `account_balance`; run flags `BALANCE_DRIFT`; balance not overwritten |
| `outbox_eventPublishedOnlyIfTransferCommitted` | Rolled-back transfer → no outbox row; committed → exactly one, published once by poller |
| `history_cursorPagination_stableUnderConcurrentInserts` | Page 1, insert 20 new rows, page 2 has no duplicates/gaps |
| `test_verifier_detects_injected_drift` (pytest) | Superuser alters one `account_balance`; verifier reports exactly one `BALANCE_DRIFT` with expected/actual as exact decimals; exit code 1 |
| `test_verifier_detects_unbalanced_journal` (pytest) | With the deferred trigger disabled in a fixture DB, insert a journal summing to 0.0001; verifier reports `UNBALANCED_TXN` |
| `test_verifier_clean_ledger_reports_ok` (pytest) | After `ledgerx_gen` workload against a healthy service: zero findings, exit 0 |
| `test_generator_duplicate_keys_never_double_charge` (pytest) | Generator sends 10% duplicate `Idempotency-Key`s; verifier + API history agree that each key produced ≤ 1 journal |

---

## 14. Docker strategy

- `Dockerfile`: multi-stage — `maven:3.9-eclipse-temurin-21` build stage → `eclipse-temurin:21-jre`
  runtime, non-root user, `java -XX:MaxRAMPercentage=75 -jar app.jar`. See
  [`../../11-docker/dockerfiles.md`](../../11-docker/dockerfiles.md).
- `compose.yaml` (dev): `postgres:16` (with an init SQL creating the `ledgerx_app` role with
  restricted privileges and a separate `ledgerx_migrate` role for Flyway), `redis:7` (optional
  profile), `app` (depends on healthy postgres). Named volume for Postgres data. Environment via
  `.env` (never committed).
- **Three DB roles are a feature, not overhead**: migrations run as owner (`ledgerx_migrate`);
  the app connects as a role with `SELECT/INSERT` on `ledger_entry` only (`ledgerx_app`); the
  Python verifier connects as `ledgerx_verify` with `SELECT` only. A test proves the app role
  cannot `UPDATE`, and a pytest proves the verifier role cannot `INSERT`.
- `tools/` gets its own tiny image (`python:3.12-slim`, `pip install .`) and a Compose profile
  `verify` so `docker compose --profile verify run verifier` works in CI and on the EC2 box.
- `compose.test.yaml` is unnecessary — Testcontainers owns test databases.
- See [`../../11-docker/compose.md`](../../11-docker/compose.md).

---

## 15. CI/CD strategy

GitHub Actions ([`../../13-cicd/github-actions.md`](../../13-cicd/github-actions.md)):

1. **`ci.yml`** on every PR/push: checkout → JDK 21 (Temurin) → Maven cache → `mvn -B verify`
   (Testcontainers needs Docker — the `ubuntu-latest` runner has it) → upload Surefire/Failsafe
   reports → export `openapi.json` and fail if it differs from the committed one (or auto-commit
   on main — pick one) → ArchUnit + no-float guard inside `verify`.
   A second job `python-tools` (runs in parallel): `pip install -e tools/[dev]` → `ruff check`
   → `mypy --strict tools/` → `pytest tools/tests -m "not integration"`; the integration
   marker runs in a third job with a `postgres:16` service container and Flyway migrations applied.
2. **`release.yml`** on tag `v*`: build image → push to GHCR (`ghcr.io/<you>/ledgerx:<tag>`)
   → SSH deploy step (see §16) → smoke test `GET /actuator/health` + one idempotent transfer
   against a seed account → post-deploy reconciliation run → **run the Python verifier against
   RDS** (read-only role) and fail the release if it reports findings.
3. Branch protection on `main`: PR required, CI green required, no force-push.
4. Concurrency tests are non-deterministic by nature; run them 3× in CI (`-Dsurefire.rerunFailingTestsCount=0`
   — do **not** use reruns to hide flakiness; instead loop the test body 3× and treat any failure
   as real).

---

## 16. AWS deployment plan

Keep it identical in shape to FlowGrid so the skill compounds ([`../../12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md), [`../../12-aws/cost-safety.md`](../../12-aws/cost-safety.md)).

| Component | Choice | Notes |
|---|---|---|
| Compute | 1× EC2 `t3.small`/`t4g.small`, Docker Compose, app container from GHCR | Security group: 443/80 from anywhere, 22 from your IP only |
| Database | RDS PostgreSQL 16, `db.t4g.micro`, private subnet, 7-day automated backups, deletion protection ON | The two-role scheme applies: create `ledgerx_app` during first deploy |
| Redis | Optional; if used, Redis container on the same EC2 (ElastiCache only if it is genuinely cheaper for you) | System must work with it absent |
| Secrets | SSM Parameter Store (SecureString) for DB password, JWT secret | EC2 instance role with least-privilege `ssm:GetParameter` on `/ledgerx/*` |
| Logs | CloudWatch agent shipping container JSON logs; log group per env | Alarm: `ERROR` count > 0 in 5 min; alarm on reconciliation findings metric > 0 |
| Metrics | Actuator → CloudWatch (Micrometer) or just log-metric filters | Publish `ledgerx.transfers.count`, `ledgerx.recon.findings` |
| TLS | Caddy or nginx sidecar with Let's Encrypt, or ALB + ACM if you prefer | |
| Backups | RDS snapshots; test a restore once and document it | A ledger without a tested restore is not a ledger |
| Cost | Target < $30/month; stop EC2 when not demoing; budget alarm at $20 | |

Deployment checklist lives in `docs/DEPLOYMENT.md` (see `docs-and-resume.md`).

---

## 17. Logging strategy

- Structured JSON (Logback + `logstash-logback-encoder` or Spring Boot 3.4+'s structured
  logging), one line per event. MDC carries `requestId`, `userId`, `idempotencyKey`, `txnId`.
- **Log transaction ids and states, never secrets and never PAN-like data.** LedgerX holds no
  card numbers, but treat account ids as sensitive-ish (fine to log) and never log full request
  bodies for money endpoints at INFO (they may contain memos with personal text). Debug-level
  body logging only in the `local` profile.
- Key events at INFO: `transfer.requested`, `transfer.completed {txnId, from, to, amount,
  durationMs}`, `transfer.failed {code}`, `idempotency.replayed`, `idempotency.conflict`,
  `reversal.completed`, `recon.run.finished {findings}`, `outbox.published {count}`.
- WARN: deadlock retry, serialization retry, stale `IN_PROGRESS` takeover, Redis unavailable
  (degraded mode). ERROR: reconciliation finding, unhandled exception (with `requestId`).
- Never log at ERROR inside a hot loop; sample if necessary.
- See [`../../05-spring-boot/06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md).

---

## 18. Error-handling strategy

- Domain exceptions (`InsufficientFundsException`, `AccountFrozenException`,
  `IdempotencyConflictException`, `InvalidStateTransitionException`, …) carry an `ErrorCode`
  enum; one `@RestControllerAdvice` maps them to `ProblemDetail`
  ([`../../05-spring-boot/04-validation-errors.md`](../../05-spring-boot/04-validation-errors.md)).
- **Database-level failures are translated, not leaked**: `check_violation` from the balance
  floor trigger → `INSUFFICIENT_FUNDS`; `unique_violation` on `(owner_id, key)` → idempotency
  path; `40P01` (deadlock) and `40001` (serialization) → bounded retry (3 attempts, jittered
  backoff) *outside* the transaction boundary, then `409 CONCURRENT_MODIFICATION`.
- **Business failures are recorded**: an insufficient-funds transfer writes a `FAILED` journal
  row (no entries) in its own short transaction *after* the main one rolled back — or design the
  main transaction to commit the `FAILED` row without entries. Decide, document, test.
- The idempotency record is finalized in a `finally`-like path: on business failure it stores
  the error response (`COMPLETED` with 422), on infrastructure failure it is set to `FAILED` so
  a retry may re-execute.
- Never catch `Exception` broadly inside a `@Transactional` method and continue — the
  transaction is already marked rollback-only; you will get `UnexpectedRollbackException`.
  (Reproduced in `failure-engineering.md`.)

---

## 19. Security considerations

- **Per-wallet authorization** inside the transaction (see §12). Test that user A cannot move
  money out of B's wallet even with a valid token.
- **Replay protection**: idempotency keys are scoped per user; a stolen key cannot replay
  another user's transfer. JWTs are short-lived; consider a `jti` deny-list only if you add
  refresh tokens.
- **Amount validation**: positive, ≤ 4 decimals, ≤ configurable max (`10_000_000.0000`),
  currency matches accounts. Reject scientific notation and leading `+`.
- **Rate limiting (optional, Redis)**: token bucket per user on money-moving endpoints; when
  Redis is unavailable fail *open* with a WARN (availability) or fail *closed* (safety) — decide
  per endpoint and document. See [`../../04-sql-databases/redis.md`](../../04-sql-databases/redis.md).
- **Least-privilege DB roles** (§14). **Secrets** only from env/SSM. Dependency scanning
  (`mvn dependency-check` or GitHub Dependabot). OWASP API Top 10 review in polish weeks.
- **Admin actions** always audited with actor and reason; `RECON_ADJUSTMENT` requires a
  free-text reason ≥ 20 chars and only touches `suspense`.
- Mass-assignment: request DTOs are records with exactly the allowed fields; never bind entities.

---

## 20. Performance considerations

- **Hot accounts**: every transfer takes a row lock on both `account_balance` rows. A popular
  merchant wallet or the `fees` system account becomes a serialization point; throughput on it
  is bounded by `1 / (lock hold time)`. Keep the locked section tiny: no HTTP calls, no logging
  of large objects, no lazy-loaded collections inside it.
- **Lock contention measurement**: `pg_stat_activity` (`wait_event_type = 'Lock'`),
  `pg_locks`, and Micrometer timers around the locked section. Record p50/p95/p99 and
  `pg_stat_database.deadlocks`.
- **Fees account**: consider not locking system accounts at all — they have no floor
  (`min_balance = -∞`), so an `UPDATE account_balance SET balance = balance + ? WHERE account_id
  = ?` is safe without `FOR UPDATE` (row-level atomic update). Document why that is sound
  (no check depends on the pre-image).
- **Batch reconciliation**: `SUM` over `ledger_entry` grouped by `account_id` — one pass;
  incremental mode using `account_balance.last_entry_id` to scan only new entries. Run it with
  `statement_timeout` and a lower `work_mem`-aware plan; never in the request path.
- **Connection pool**: HikariCP `maximumPoolSize` ≈ `2×cores + spindles` on the DB side; a
  larger pool makes lock queues longer, not throughput higher.
- **What to measure** (protocol in `docs-and-resume.md`): transfers/s and p95 latency at
  concurrency 1/8/32/64 for (a) all transfers hitting one hot account and (b) transfers across
  1,000 accounts; deadlock/serialization retry counts; reconciliation duration for 1M entries.

---

## 21. Scalability considerations

Write these up in `docs/DESIGN_DECISIONS.md` even though v1.0 runs on one box:

- **Sharding by account**: the ledger shards naturally by `account_id` *until* a transfer spans
  two shards — then you need either two-phase commit or a saga with `pending_outbound`-style
  holding accounts per shard and an asynchronous settlement step. The sum-to-zero invariant
  becomes "sums to zero across shards eventually", which is why holding accounts exist.
- **Hot system accounts** (`fees`, `external_clearing`): shard them into N sub-accounts
  (`fees_00…fees_15`), pick one by hash of txn id, and report the aggregate. The invariant
  holds per sub-account. This is the standard fix for the "one row everyone updates" problem.
- **Serialized queues per account**: instead of row locks, route all operations for an account
  to a single-writer queue (Kafka partition/Redis stream keyed by account). Trades latency and
  operational complexity for lock-free hot accounts. Justify why v1.0 does *not* do this.
- **At 100× load, where does locking break?** Lock queue depth on hot rows → p99 explodes;
  deadlock detection cost rises with waiters; the deferred balanced-journal trigger's aggregate
  scan per commit becomes visible; `idempotency_key` table growth needs partitioning by
  `expires_at`; the outbox poller needs multiple workers with `FOR UPDATE SKIP LOCKED`.
- **Read scaling**: history and balances can be served from a read replica with a
  `replica_lag` caveat — a just-completed transfer may be missing; show `asOfEntryId` in the
  response so clients can detect staleness.
- **Multi-region**: an active-active ledger requires either per-account home regions (route
  writes to the home region; cross-region transfers via holding accounts) or a consensus store.
  v1.0 is single-region; the design note explains what would change.

See [`../../15-system-design/scalability.md`](../../15-system-design/scalability.md) and
[`../../14-cs-fundamentals/database-internals.md`](../../14-cs-fundamentals/database-internals.md).

---

## 22. Git strategy

- Repository `ledgerx` (public), trunk-based: `main` protected; short-lived branches
  `m2/idempotency-store`, `m2/transfer-locking`, `m4/recon-job`; PRs squash-merged.
- **GitHub milestones** `M1 Ledger core` … `M5 Deploy & advanced`, each with issues created
  from the "Now implement it" task lists in `milestones.md` (one issue per task, labelled
  `type:feature|test|docs|infra`, `area:ledger|transfers|…`). Close issues via PR keywords.
- Conventional commits (`feat(transfers): ordered row locking`, `test(ledger): sum-to-zero
  property`). Tags: `v0.5` (MVP), `v1.0` (Strong Résumé Version), `v1.1` (Advanced).
- **PR workflow**: branch → commits → open PR with the template (what/why/how tested/risk/
  rollback) → CI green → self-review with the checklist (transaction boundary? lock order?
  idempotency? test added? docs updated?) → squash-merge → delete branch. Review your own PR
  the *next day* before merging; write at least one comment you would give a colleague.
- ADRs in `docs/adr/` for: signed-amount vs debit/credit columns; pessimistic vs optimistic;
  isolation level; idempotency storage location; outbox vs direct publish; `NUMERIC` vs minor
  units. Template: [`../templates/adr.md`](../templates/adr.md).
- See [`../../02-git/workflows.md`](../../02-git/workflows.md).

---

## 23. README and demo requirements

**Repository README must contain:** one-paragraph pitch; the transfer sequence diagram; the
"why double-entry" explanation in ≤ 10 lines; quick start (`docker compose up`, register,
deposit, transfer with `curl` including the `Idempotency-Key` header, replay it, see
`Idempotent-Replayed: true`); the invariants list; the named test list with a link to the CI
run; measured numbers with a link to `PERFORMANCE.md` and the methodology; architecture
diagram; links to `docs/`; honest "limitations" section (single currency, simulated external
side, single region).

**Demo (≤ 5 minutes, recorded as GIF/MP4 or a scripted `demo.sh`):**
1. Deposit 500 into Alice; show the two ledger entries and clearing account going −500.
2. Fire two concurrent 400 transfers (a tiny script with `&`); show one 201, one 422; balance 100.
3. Replay the successful one with the same key → identical response, `Idempotent-Replayed`.
4. Reverse it; show the compensating journal linked by `reversal_of`.
5. Run reconciliation → zero findings; then (admin, deliberately) corrupt a materialized
   balance with SQL; run again → `BALANCE_DRIFT` finding; show it was not auto-fixed.
6. Run `python -m ledgerx_verify --dsn ...` — the independent verifier reports the same drift
   from raw rows, in a different language, with exact decimals; exit code 1.

---

## 24. Prerequisite topic map

| Need | Read | When |
|---|---|---|
| Transactions, isolation, `FOR UPDATE`, deadlocks, MVCC | [`../../04-sql-databases/06-transactions.md`](../../04-sql-databases/06-transactions.md) | Before M1/M2 |
| Constraints, triggers, schema design | [`../../04-sql-databases/04-schema-design.md`](../../04-sql-databases/04-schema-design.md), [`../../04-sql-databases/03-advanced-queries.md`](../../04-sql-databases/03-advanced-queries.md) | M1 |
| Indexes and `EXPLAIN` | [`../../04-sql-databases/05-indexes-performance.md`](../../04-sql-databases/05-indexes-performance.md) | M3 (history), M4 (recon) |
| DB internals (WAL, MVCC, locks) | [`../../14-cs-fundamentals/database-internals.md`](../../14-cs-fundamentals/database-internals.md) | M1–M2 |
| `@Transactional` propagation, self-invocation, rollback rules | [`../../05-spring-boot/07-transactions.md`](../../05-spring-boot/07-transactions.md) | M2 |
| JPA vs native SQL | [`../../05-spring-boot/03-data-jpa.md`](../../05-spring-boot/03-data-jpa.md), [`../../04-sql-databases/08-jdbc-orm.md`](../../04-sql-databases/08-jdbc-orm.md) | M1 |
| Validation and ProblemDetail | [`../../05-spring-boot/04-validation-errors.md`](../../05-spring-boot/04-validation-errors.md) | M1 |
| Security/JWT | [`../../05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md) | M1 |
| Scheduling and caching | [`../../05-spring-boot/08-caching-scheduling.md`](../../05-spring-boot/08-caching-scheduling.md) | M3 outbox poller, M4 recon, M5 scheduler |
| API design, idempotency headers, pagination | [`../../06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md), [`../../06-rest-apis/http-for-apis.md`](../../06-rest-apis/http-for-apis.md) | M2/M3 |
| Java concurrency for tests | [`../../01-java/07-concurrency.md`](../../01-java/07-concurrency.md), [`../../14-cs-fundamentals/concurrency.md`](../../14-cs-fundamentals/concurrency.md) | M2 |
| Testing stack | [`../../09-testing/testcontainers.md`](../../09-testing/testcontainers.md), [`../../09-testing/spring-testing.md`](../../09-testing/spring-testing.md), [`../../09-testing/mockito.md`](../../09-testing/mockito.md) | M1+ |
| Redis (optional) | [`../../04-sql-databases/redis.md`](../../04-sql-databases/redis.md) | M5 risk rules |
| Python for the tools: pytest, type hints, scripting, `decimal` | [`../../19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md), [`../../19-python/03-pitfalls-and-complexity.md`](../../19-python/03-pitfalls-and-complexity.md), [`../../19-python/python-for-java-devs.md`](../../19-python/python-for-java-devs.md) | M4 |
| Docker, CI, AWS | [`../../11-docker/README.md`](../../11-docker/README.md), [`../../13-cicd/README.md`](../../13-cicd/README.md), [`../../12-aws/README.md`](../../12-aws/README.md) | M1 (compose/CI), M5 (deploy) |
| Interview framing | [`../../16-interview-prep/project-deep-dive.md`](../../16-interview-prep/project-deep-dive.md), [`../../17-resume-tech-defense/postgresql.md`](../../17-resume-tech-defense/postgresql.md), [`../../17-resume-tech-defense/spring-boot.md`](../../17-resume-tech-defense/spring-boot.md) | W13 |
| Checkpoint | [`../../checkpoints/checkpoint-12.md`](../../checkpoints/checkpoint-12.md) | End of W12 |
| Weekly plans | [`../../weeks/week-09/README.md`](../../weeks/week-09/README.md) … [`../../weeks/week-13/README.md`](../../weeks/week-13/README.md) | |

Next: open [`milestones.md`](./milestones.md) and start M1.
