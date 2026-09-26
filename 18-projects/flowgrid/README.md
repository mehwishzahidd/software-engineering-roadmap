# FlowGrid — Multi-Warehouse Fulfillment & Inventory Platform

> **Project 1 of 4 · Weeks 4–8 · 125–150 hours · Strong Résumé Version = `v1.0`**
> Stack: Java 21 · Spring Boot 3.x · PostgreSQL 16 · Redis 7 · REST · React 18 + TypeScript · Docker Compose v2 · AWS (EC2, RDS, S3, CloudWatch, IAM) · JUnit 5 · Testcontainers · GitHub Actions · **Python 3.12 tooling** (`tools/`: data generator + load/simulation harness, pytest)
>
> **Language note.** The backend is Java/Spring — that is the résumé story and this project's interview track is **Track B** (Java, Spring, SQL, React, Docker, AWS). Python appears where operations engineers actually use it: a typed, tested `tools/` package that generates realistic data and hammers the API to measure latency and reservation contention. Coding interviews (Track A) are practised in Python separately — see [`../../19-python/README.md`](../../19-python/README.md).
>
> This folder is the **spec**. The code lives in *your* repository (`flowgrid`), written by you.
> Read the [project rule](../../ROADMAP.md#1-learning-philosophy) again before starting: this document tells you *what* to build and *how to think about it*, not the code.

| File | What it is for |
|---|---|
| [`README.md`](./README.md) (this file) | Full project plan: requirements, architecture, data model, API, security, testing, Docker, CI/CD, AWS, Git workflow, demo |
| [`milestones.md`](./milestones.md) | M1–M5 week by week: know first → requirements → architecture → acceptance → implement → verify → debug → interview |
| [`failure-engineering.md`](./failure-engineering.md) | 12 scenarios to reproduce, observe, diagnose, fix and regression-test |
| [`interview-questions.md`](./interview-questions.md) | Question bank (basic → advanced), deep-dive talk outline, 2-minute pitch |
| [`docs-and-resume.md`](./docs-and-resume.md) | Required docs, benchmarking protocol, résumé-bullet rules, GitHub hygiene |

---

## Contents

1. [Product overview](#1-product-overview)
2. [Problem statement](#2-problem-statement)
3. [Functional requirements](#3-functional-requirements)
4. [Non-functional requirements](#4-non-functional-requirements)
5. [Scope tiers](#5-scope-tiers)
6. [Milestone progression](#6-milestone-progression)
7. [Architecture](#7-architecture)
8. [Database design and ERD](#8-database-design-and-erd)
9. [API design](#9-api-design)
10. [OpenAPI expectations](#10-openapi-expectations)
11. [Package and folder structure](#11-package-and-folder-structure)
12. [Authentication model](#12-authentication-model)
13. [Authorization model](#13-authorization-model)
14. [Testing strategy](#14-testing-strategy)
15. [Docker strategy](#15-docker-strategy)
16. [CI/CD strategy](#16-cicd-strategy)
17. [AWS deployment plan](#17-aws-deployment-plan)
18. [Logging strategy](#18-logging-strategy)
19. [Error-handling strategy](#19-error-handling-strategy)
20. [Security considerations](#20-security-considerations)
21. [Performance considerations](#21-performance-considerations)
22. [Scalability considerations](#22-scalability-considerations)
23. [Git strategy](#23-git-strategy)
24. [Milestone branches and issues](#24-milestone-branches-and-issues)
25. [PR workflow](#25-pr-workflow)
26. [README requirements for your repo](#26-readme-requirements-for-your-repo)
27. [Demo requirements](#27-demo-requirements)
28. [Links](#28-links)

---

## 1. Product overview

FlowGrid is a back-office platform for a company that stores goods in **several warehouses** and ships **customer orders** from them. It answers, correctly and concurrently, the questions every fulfillment operation lives on:

- *How many units of SKU X do we really have, where, and in what state?*
- *Can I promise this order to the customer right now, without promising the same unit to two people?*
- *Which warehouse should ship this order, and can part of it ship today if the rest is short?*
- *What happened to this unit — who adjusted it, when, and why?*

Users are internal staff, not shoppers. There is no checkout page and no payment; the "order" arrives from an upstream channel (a web store, a marketplace, a CSV import — you will simulate it with the API and a seed script). FlowGrid owns everything from *order received* to *shipment created*, plus returns, transfers between warehouses, stock adjustments and low-stock alerting.

**Technical identity** (what an interviewer should remember about it): domain modelling of inventory state, **idempotent order creation**, **race-free reservations under concurrency** (`SELECT … FOR UPDATE` vs optimistic locking, with a test that proves it), **deterministic multi-warehouse allocation**, pick → pack → ship state machine with partial fulfillment, **Redis cache-aside with graceful degradation**, a React/TS operations dashboard, Docker + CI/CD, and a real AWS deployment with measured (not assumed) latency numbers.

## 2. Problem statement

You have worked in fulfillment operations. You have seen the real versions of these problems:

| Ops reality | What goes wrong without a system that handles it | FlowGrid feature |
|---|---|---|
| Stock counts on the shelf, in the WMS, and in the sales channel disagree | Oversells, cancelled orders, angry customers, manual reconciliation | Inventory **by state** per warehouse; every movement is an auditable event; `available = on_hand − reserved` is an invariant, not a guess |
| Two orders for the last unit arrive within the same second | Both get "confirmed"; one is discovered short at picking | **Reservations** taken under a row lock; exactly one succeeds; the loser gets a clear `409` |
| Channel retries an order because a timeout hid a success | Duplicate orders, duplicate picks, double shipments | **Idempotency-Key** on order creation: same key → same response, never a second order |
| Order lines exist in three warehouses; nobody agrees which one ships | Late shipments, split shipping cost, manual decisions | **Deterministic allocation** scoring availability, region match, workload, capacity, priority; explainable and reproducible |
| One item is back-ordered, the rest sits waiting | Whole order delayed | **Partial fulfillment** — ship what is ready, keep the remainder open |
| Returns arrive damaged or resellable | Damaged stock restocked; resellable stock thrown in quarantine | Returns with **restock vs quarantine** disposition |
| Stock moved between sites is "lost" in the truck | Two warehouses both count it, or neither | **Two-phase transfers** with an explicit `IN_TRANSIT` state |
| Nobody notices a SKU is nearly out until it is out | Stockouts | **Scheduled low-stock alerts** + a low-stock view served from cache |
| "Who changed this count and why?" | No answer | `audit_event` for every mutating action, with actor and reason |

You are **not** recreating any employer's proprietary system, importing its data, or copying its screens. You are building a clean, general model of a domain you understand. When you describe FlowGrid in interviews, say exactly that: *"I've worked in fulfillment operations, so I chose a domain where I know which failures matter — oversells, duplicate orders, lost transfers — and built a system that prevents them and proves it with tests."*

## 3. Functional requirements

Numbered so issues, tests and acceptance criteria can reference them (`FR-xx`). "Must" = Strong Résumé Version. Milestone in brackets.

### Catalog and warehouses
- **FR-01** [M1] CRUD for warehouses: code (unique), name, region/zone, address, `active`, daily pick capacity.
- **FR-02** [M1] CRUD for products and SKUs: a product has ≥1 SKU; a SKU has a unique code, attributes (size/colour as JSON or columns), weight, `active`. Deactivate rather than delete once referenced.
- **FR-03** [M1] All list endpoints support pagination, sorting and filtering (§9.3).

### Inventory
- **FR-04** [M1] An inventory level exists per (warehouse, SKU) with quantities by state: `on_hand`, `reserved`, `allocated`, `picked`, `shipped` (cumulative), `quarantined`; `available` is **derived** (`on_hand − reserved`), never stored.
- **FR-05** [M1] Stock adjustments (receive, count correction, damage write-off) change `on_hand` and write an `audit_event` with reason and actor. `on_hand` can never go negative.
- **FR-06** [M4] Stock transfers between warehouses: `REQUESTED → IN_TRANSIT → RECEIVED` (or `CANCELLED`); source decrements on dispatch, destination increments on receipt.
- **FR-07** [M4] Low-stock threshold per (warehouse, SKU) or per SKU; a scheduled job evaluates thresholds and records alerts; the dashboard shows them.

### Orders and reservations
- **FR-08** [M2] Create an order with ≥1 line (SKU, quantity), customer reference, shipping region, priority (`STANDARD`, `EXPRESS`). Validation errors return `400` ProblemDetail.
- **FR-09** [M2] Order creation requires an `Idempotency-Key` header. Same key + same body → identical response (`201` replayed, same order id). Same key + different body → `422` (`IDEMPOTENCY_KEY_REUSED`). Keys expire after 24 h.
- **FR-10** [M2] Creating an order reserves stock for every line (first at a single chosen warehouse in M2; multi-warehouse allocation arrives in M3). If any line cannot be reserved the whole order is `REJECTED` (or `BACKORDERED` if you choose to support it) and nothing is reserved — atomic.
- **FR-11** [M2] Under concurrent creation, a unit is never reserved twice. This is proven by a test (§14.4).
- **FR-12** [M2] Cancelling an order (allowed before `PICKED`) releases all its reservations atomically.
- **FR-13** [M2] Order and reservation state machines are explicit and enforced; illegal transitions return `409` (`ILLEGAL_STATE_TRANSITION`).

### Allocation and fulfillment
- **FR-14** [M3] Allocation chooses a warehouse per order (M3) — or per line (ADVANCED) — with a **deterministic** score; the score breakdown is stored so a human can read *why*.
- **FR-15** [M3] Pick lists are generated per warehouse from allocated orders; associates pick lines, quantities picked are recorded; short picks are supported.
- **FR-16** [M3] Packing creates packages (weight, dimensions optional); shipping creates a shipment with a carrier stub and tracking placeholder; inventory moves `allocated → picked → shipped`.
- **FR-17** [M3] Partial fulfillment: lines that are ready ship; the order stays `PARTIALLY_SHIPPED` until remaining lines ship or are cancelled.
- **FR-18** [M4] Returns: register a return against a shipped order line; disposition `RESTOCK` (increments `on_hand`) or `QUARANTINE` (increments `quarantined`); both audited.

### Caching
- **FR-19** [M3] Product/SKU reads are served cache-aside from Redis with TTL; writes invalidate. Low-stock view is cached with a short TTL.
- **FR-20** [M3] If Redis is unavailable, every endpoint still works from the DB (slower); the outage is logged once per interval, not per request; a health indicator reflects it.

### Users, security, audit
- **FR-21** [M1] Users with roles `ADMIN`, `OPS_MANAGER`, `WAREHOUSE_ASSOCIATE`, `VIEWER`; login returns a JWT; every non-auth endpoint requires one.
- **FR-22** [M1] Role matrix (§13) is enforced at the method level; violations return `403`.
- **FR-23** [M1] Every mutating action writes an `audit_event` (actor, action, entity, before/after summary, timestamp, request id).

### Dashboard
- **FR-24** [M4] React/TS SPA: login; inventory by warehouse (search/filter/sort/paginate); orders list + detail; pick and pack screens for associates; low-stock view; transfers and returns forms; role-aware navigation.

### Operations
- **FR-25** [M5] Deployed on AWS; CI/CD builds, tests, publishes an image and deploys; CloudWatch has logs and one alarm; report export (e.g. inventory snapshot CSV) is written to S3.

### Python tooling (`tools/`) — required component
- **FR-26** [M4] `flowgrid-tools` Python package (3.12, typed, `pytest`-tested, own README) with a **synthetic data generator**: realistic warehouses (regions, capacities), products/SKUs (categories, attribute JSON, weights), inventory levels with a controllable low-stock ratio, and an order mix (region distribution, line-count distribution, priority ratio, deliberate duplicates for idempotency testing). Output: JSON/CSV fixtures **or** direct writes via the REST API (login as admin, honour `Idempotency-Key`) **or** SQL inserts for bulk seeding (≥ 50k orders for index experiments). Deterministic given a `--seed`.
- **FR-27** [M5] **Load/simulation harness**: drives concurrent order creation against a running API (`asyncio` + `httpx`, configurable concurrency, duration, SKU hot-spot ratio), and reports p50/p95/p99 latency, throughput, HTTP status distribution, and **reservation contention** (for a hot SKU with `N` units and `M` competing orders: successes must equal `min(N, M)`; the harness verifies this against `GET /inventory` afterwards and prints a pass/fail line). Output: a Markdown/JSON report consumed by the benchmark protocol and by failure-engineering scenario 1. It complements k6 (k6 measures; the harness *verifies invariants* and generates realistic mixes).

## 4. Non-functional requirements

| Area | Requirement | How it is verified |
|---|---|---|
| **Correctness under concurrency** | No unit reserved twice; no negative `on_hand`; transfers never double-count; idempotent create never duplicates | Named concurrency tests (§14.4) run in CI against a real Postgres (Testcontainers) |
| **Atomicity** | Multi-step operations (create order + reserve lines; pick → pack → ship; transfer dispatch/receive) are all-or-nothing | Tests that fail a step mid-way and assert rollback; DB constraints |
| **Latency** | You will **measure**, not assume. Targets to *compare against* after measurement: order creation p95 < 200 ms on the dev laptop at 20 VUs; catalog read p95 < 50 ms with warm cache. If your numbers differ, record them — do not edit the target to match | k6 scripts and the protocol in [`docs-and-resume.md`](./docs-and-resume.md#benchmarking-protocol) |
| **Auditability** | Every mutation traceable to a user, a request id and a reason | `audit_event` table; log correlation via MDC |
| **Security** | JWT auth, role-based authz on every endpoint, BCrypt, no secrets in the repo, input validation everywhere, least-privilege IAM | Security tests (`403`/`401` cases), secret scanning, IAM policy review |
| **Observability** | Structured JSON logs with request id; Actuator health incl. DB and Redis; one CloudWatch alarm | Manual check + `DEPLOYMENT.md` evidence |
| **Resilience** | Redis outage degrades, not fails; DB outage returns `503` fast, not hangs | Failure scenario 2 in [`failure-engineering.md`](./failure-engineering.md) |
| **Maintainability** | Package-by-feature; ≥ 70 % line coverage on service layer; Conventional Commits; ADRs for major decisions | JaCoCo report in CI; `DESIGN_DECISIONS.md` |
| **Cost** | AWS bill under ~$25/month while deployed; teardown script exists | [`../../12-aws/cost-safety.md`](../../12-aws/cost-safety.md) |

## 5. Scope tiers

### MVP (end of M2, week 5)
- Warehouses, products, SKUs, inventory levels, adjustments, audit events, users/roles, JWT
- Pagination/sort/filter, ProblemDetail errors, OpenAPI UI, Flyway migrations, Compose dev stack
- Orders with `Idempotency-Key`, single-warehouse reservation with `SELECT … FOR UPDATE`, cancellation
- Concurrency test (N threads, 1 unit → exactly one reservation) on Testcontainers
- CI running `mvn verify`

### STRONG RESUME VERSION — `v1.0` (end of M5, week 8) — *the primary target*
Everything in MVP plus:
- Deterministic multi-warehouse allocation with stored score breakdown
- Pick lists, picking → packing → shipment state machine, partial fulfillment
- Redis cache-aside for catalog + low-stock cache, invalidation, degrade-to-DB when Redis is down
- React/TS dashboard (inventory, orders, pick/pack, low-stock, transfers, returns)
- Returns (restock/quarantine), two-phase transfers, scheduled low-stock alerts
- `tools/` Python package: data generator (FR-26) + load/simulation harness with contention verification (FR-27), pytest suite green in CI, README
- Multi-stage Dockerfile, prod Compose, GitHub Actions (test → image → GHCR → deploy)
- AWS: EC2 + Compose, RDS Postgres, Redis (EC2 or ElastiCache), S3 exports, IAM least privilege, CloudWatch logs + alarm
- k6 baseline recorded with methodology; 9 docs (§26); failure-engineering exercises done; tag `v1.0`

### ADVANCED (polish weeks 24–26, only after `v1.0`)
- Split fulfillment: allocate **per line** across warehouses; multiple shipments per order
- Capacity-aware allocation: daily pick capacity as a hard constraint with overflow to next-best warehouse
- Distributed lock (Redis `SET NX PX` + token) **only if you can justify it** — write the ADR first; the default answer is "the DB row lock is sufficient"
- Optimistic-locking comparison benchmark (`@Version` vs `FOR UPDATE`) recorded in `PERFORMANCE.md`

### OPTIONAL STRETCH (never blocks completion)
- Carrier rate stub (fake rate table by region/weight) chosen at shipment time
- EDI-style flat-file order import (batch endpoint, idempotent per file + line)
- Webhook to notify an upstream channel on shipment
- Cursor pagination on the orders list

**Slip rule:** if behind at week 8, cut ADVANCED entirely and keep every line of the Strong Résumé Version; if still behind, cut dashboard *breadth* (keep inventory + orders + pick screen), never tests, deploy or docs.

## 6. Milestone progression

Full detail in [`milestones.md`](./milestones.md). One milestone = one week = one GitHub milestone = one or more PRs.

| Milestone | Week | Theme | Exit artefact | Tier reached |
|---|---|---|---|---|
| [M1](./milestones.md#m1--domain-persistence-auth-week-4) | 4 | Domain, persistence, auth | Secured CRUD API with migrations, OpenAPI, CI green | MVP core |
| [M2](./milestones.md#m2--idempotent-orders--concurrent-reservations-week-5) | 5 | Idempotent orders, concurrent reservations | Concurrency test passing on Testcontainers; `v0.2` | **MVP** |
| [M3](./milestones.md#m3--allocation-fulfillment-workflow-redis-week-6) | 6 | Allocation, pick/pack/ship, Redis | Order shipped end-to-end via API; Redis-down test; `v0.3` | — |
| [M4](./milestones.md#m4--operations-dashboard-returns-transfers-week-7) | 7 | Dashboard, returns, transfers, alerts, **Python generator** | Dashboard demo GIF; `tools/` generator seeding the dev stack; `v0.4` | — |
| [M5](./milestones.md#m5--deploy-document-measure-week-8) | 8 | AWS, CI/CD, k6 + **Python harness**, docs, failure exercises | Public URL, docs set, benchmark report (k6 + harness); **`v1.0`** | **Strong Résumé Version** |

Checkpoints: [CP-4](../../checkpoints/checkpoint-04.md) after M1, [CP-8](../../checkpoints/checkpoint-08.md) after M5.

## 7. Architecture

### 7.1 Overview diagram (draw this yourself — spec in 7.3)

```
                     ┌──────────────────────────────┐
   Browser           │  React 18 + TS SPA (Vite)    │  static files served by nginx
   (ops staff)  ───▶ │  login · inventory · orders  │  container (or S3+CloudFront later)
                     │  pick/pack · low-stock · ... │
                     └──────────────┬───────────────┘
                                    │ HTTPS  JSON  Authorization: Bearer <JWT>
                                    ▼
                     ┌──────────────────────────────┐
                     │  FlowGrid API (Spring Boot)  │
                     │  ─ SecurityFilterChain (JWT) │
                     │  ─ IdempotencyFilter         │
                     │  ─ Controllers → Services →  │
                     │    Repositories (JPA)        │
                     │  ─ @Scheduled jobs:          │
                     │    low-stock scan, idem-key  │
                     │    purge, inventory export   │
                     │  ─ Actuator /health          │
                     └───────┬──────────────┬───────┘
                             │ JDBC         │ Lettuce
                             ▼              ▼
                ┌──────────────────┐  ┌───────────────┐      ┌──────────────┐
                │ PostgreSQL 16    │  │ Redis 7       │      │ S3 bucket    │
                │ source of truth  │  │ cache-aside:  │      │ CSV exports  │
                │ Flyway-migrated  │  │ catalog,      │      │ (M5)         │
                │ row locks        │  │ low-stock,    │      └──────────────┘
                │                  │  │ idem keys(opt)│
                └──────────────────┘  └───────────────┘
                                                             CloudWatch ◀── JSON logs (awslogs driver)

   ┌──────────────────────────────────────────────┐
   │ tools/ (Python 3.12)  — dev/ops side, not    │   HTTPS/JSON as an API client (or SQL for bulk seed)
   │ deployed:  flowgrid_tools.generate  ─────────┼──▶ API  /auth/login, /warehouses, /skus, /inventory/adjustments, /orders
   │            flowgrid_tools.harness   ─────────┼──▶ API  concurrent POST /orders → latency + contention report
   └──────────────────────────────────────────────┘
```

### 7.2 Component responsibilities

| Component | Owns | Does **not** own |
|---|---|---|
| **SPA** | Presentation, client-side routing, token storage (memory + refresh), optimistic UI *only* for non-critical views | Business rules; never computes `available` itself; never decides allocation |
| **Controllers** | HTTP mapping, DTO validation (`@Valid`), pagination params, status codes | Transactions, business rules |
| **Services** | Transaction boundaries (`@Transactional`), state machines, invariants, allocation scoring, audit writing, cache orchestration | HTTP concerns, SQL details |
| **Repositories** | Queries, locking queries (`@Lock(PESSIMISTIC_WRITE)` / native `FOR UPDATE`), projections | Business decisions |
| **PostgreSQL** | Source of truth; constraints (`CHECK`, `UNIQUE`, FK); row-level locks that make reservations safe | Caching, session state |
| **Redis** | Cache-aside for catalog and low-stock; optionally idempotency key store (decide and document — default is Postgres for keys, because the stored response must survive a Redis restart) | Source of truth for anything |
| **Scheduled jobs** | Low-stock scan (FR-07), idempotency-key purge, nightly inventory snapshot export to S3 | Anything a user waits for synchronously |
| **`tools/` (Python)** | Generating realistic fixtures/seeds; driving concurrent load; verifying the reservation invariant from the outside; producing benchmark reports | Any business rule — it is a *client* of the API and a *reader* of the DB, never a writer of inventory quantities except through the API or an explicit bulk-seed mode |
| **GitHub Actions** | Build, test, image publish, deploy; runs `pytest` for `tools/` | — |
| **AWS** | Hosting (EC2), managed DB (RDS), object storage (S3), logs/alarm (CloudWatch), identities (IAM) | — |

Key rule: **the database is the only source of truth and the only arbiter of concurrency.** Redis can vanish at any moment and nothing may become incorrect — only slower.

### 7.3 Architecture diagram specification (what you must draw in `ARCHITECTURE.md`)

Draw with draw.io, Excalidraw, Mermaid or PlantUML — commit the source file plus a PNG/SVG.

Boxes (at least): Browser/SPA · nginx (static) · API container · Postgres (RDS in prod / container in dev) · Redis · S3 · CloudWatch · GitHub Actions · GHCR · EC2 host boundary (dashed) · VPC/security-group boundary (dashed).
Arrows, each labelled with protocol + purpose: SPA→API (HTTPS/JSON, Bearer JWT) · API→Postgres (JDBC, TLS in prod) · API→Redis (RESP via Lettuce) · API→S3 (SDK, IAM instance role) · container logs→CloudWatch (awslogs driver) · Actions→GHCR (push image) · Actions→EC2 (SSH or SSM: pull + `docker compose up -d`).
Second diagram: **request flow for order creation** as a sequence: Client → IdempotencyFilter → OrderController → OrderService (`@Transactional`) → InventoryRepository (`FOR UPDATE`) → AuditService → response stored → client. Show where the transaction begins and ends.
Third diagram: **state machines** for Order, Reservation, Transfer as tables (see [`milestones.md`](./milestones.md)); a picture is optional.

## 8. Database design and ERD

### 8.1 Principles
- PostgreSQL 16. Flyway migrations `V1__…` onward; never edit an applied migration. Snake_case names. `BIGINT` identity PKs (simple, orderable, fine at this scale; document that UUIDs are an option and why you didn't choose them — or choose them and document that).
- Money is not modelled (no prices) — deliberately, to stay focused; note this in `DESIGN_DECISIONS.md`.
- `created_at`/`updated_at` `TIMESTAMPTZ NOT NULL DEFAULT now()` on every table.
- Quantities are `INTEGER NOT NULL DEFAULT 0` with `CHECK (x >= 0)`.
- Enum-like states are `VARCHAR(32)` with a `CHECK (state IN (...))`, mapped by `@Enumerated(EnumType.STRING)`. (Postgres native enums are harder to migrate; document the trade-off.)

### 8.2 Why `on_hand` and `reserved` are separate columns

You could store a single `quantity` and subtract reservations on the fly, or store `available` directly. Both are wrong for this domain:

- **Physical vs promised.** `on_hand` is *what is on the shelf*; it changes only on receipt, adjustment, pick (out) and return. `reserved` is *what has been promised to orders*; it changes on reservation and release. They change for different reasons, by different actors, and they are audited differently.
- **The invariant is checkable.** `available = on_hand − reserved` and `reserved ≤ on_hand` is a `CHECK` constraint the database enforces on every write, no matter which code path made it. A stored `available` column can drift; a derived one cannot.
- **Locking semantics.** A reservation needs `on_hand − reserved ≥ qty` *evaluated under a lock on that row*. Two columns on one row make that a single locked read-then-write. Splitting reservations into a separate count query invites the race you are trying to prevent.
- **Reporting.** Ops wants "on hand", "committed" and "available" as three numbers. They are all one row.

Invariants (enforce in DB and assert in tests):
```
available   = on_hand - reserved          (derived, never stored)
0 <= reserved <= on_hand
0 <= allocated <= reserved                (allocated is a subset of reserved once a warehouse is chosen)
0 <= picked   <= allocated               (per open pick list; shipped decrements picked and on_hand)
```
Design decision to document: whether `reserved` is decremented when stock is picked (then `on_hand` and `reserved` both drop at pick) or at ship. Recommended: decrement `on_hand`, `reserved` and `allocated` together **at ship** (physical departure); `picked` is a transient staging count. Whatever you pick, write it down and test it.

### 8.3 Tables

Legend: PK, FK, U = unique, NN = not null, IX = index, CK = check.

**warehouse**
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK identity |
| code | VARCHAR(16) | U, NN |
| name | VARCHAR(128) | NN |
| region | VARCHAR(32) | NN, IX (used by allocation) |
| address_line | VARCHAR(256) | |
| daily_pick_capacity | INTEGER | NN, CK ≥ 0 |
| active | BOOLEAN | NN default true |
| created_at / updated_at | TIMESTAMPTZ | NN |

**product**
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| name | VARCHAR(256) | NN, IX (trigram optional later) |
| category | VARCHAR(64) | IX |
| active | BOOLEAN | NN |
| created_at / updated_at | | |

**sku**
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| product_id | BIGINT | FK → product, NN, IX |
| code | VARCHAR(32) | U, NN |
| attributes | JSONB | e.g. `{"size":"M","colour":"navy"}` |
| weight_grams | INTEGER | CK ≥ 0 |
| active | BOOLEAN | NN |
| created_at / updated_at | | |

**inventory_level** — *the* concurrency hotspot
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| warehouse_id | BIGINT | FK, NN |
| sku_id | BIGINT | FK, NN |
| on_hand | INTEGER | NN, CK ≥ 0 |
| reserved | INTEGER | NN, CK ≥ 0 |
| allocated | INTEGER | NN, CK ≥ 0 |
| picked | INTEGER | NN, CK ≥ 0 |
| shipped_total | INTEGER | NN, CK ≥ 0 (cumulative, reporting only) |
| quarantined | INTEGER | NN, CK ≥ 0 |
| low_stock_threshold | INTEGER | nullable (falls back to SKU default) |
| version | BIGINT | NN default 0 (for the `@Version` comparison experiment) |
| created_at / updated_at | | |
| — | | **U (warehouse_id, sku_id)**; CK `reserved <= on_hand`; CK `allocated <= reserved`; IX (sku_id) for "where is SKU X" |

**stock_adjustment**
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| inventory_level_id | BIGINT | FK, NN, IX |
| delta | INTEGER | NN, CK ≠ 0 |
| reason | VARCHAR(32) | NN, CK IN (RECEIPT, COUNT_CORRECTION, DAMAGE, RETURN_RESTOCK, OTHER) |
| note | VARCHAR(512) | |
| created_by | BIGINT | FK → app_user, NN |
| created_at | | |

**customer_order**  (`order` is a reserved word)
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| order_number | VARCHAR(32) | U, NN (human-readable, e.g. `FG-000123`) |
| external_ref | VARCHAR(64) | IX (channel reference) |
| customer_ref | VARCHAR(64) | NN |
| ship_region | VARCHAR(32) | NN |
| priority | VARCHAR(16) | NN, CK IN (STANDARD, EXPRESS) |
| status | VARCHAR(32) | NN, CK IN (RECEIVED, RESERVED, REJECTED, ALLOCATED, PICKING, PACKED, PARTIALLY_SHIPPED, SHIPPED, CANCELLED), IX (status, created_at) |
| allocated_warehouse_id | BIGINT | FK nullable (M3; per-line in ADVANCED) |
| allocation_explanation | JSONB | score breakdown (M3) |
| created_by | BIGINT | FK |
| created_at / updated_at | | IX (created_at DESC) for the list |

**order_line**
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| order_id | BIGINT | FK, NN, IX |
| sku_id | BIGINT | FK, NN, IX |
| quantity | INTEGER | NN, CK > 0 |
| quantity_shipped | INTEGER | NN default 0, CK ≤ quantity |
| status | VARCHAR(32) | NN (OPEN, ALLOCATED, PICKED, PACKED, SHIPPED, CANCELLED, SHORT) |
| — | | U (order_id, sku_id) |

**reservation**
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| order_line_id | BIGINT | FK, NN, IX |
| inventory_level_id | BIGINT | FK, NN, IX |
| quantity | INTEGER | NN, CK > 0 |
| status | VARCHAR(32) | NN, CK IN (ACTIVE, ALLOCATED, CONSUMED, RELEASED), IX (status) |
| expires_at | TIMESTAMPTZ | nullable (design decision: do reservations expire?) |
| created_at / updated_at | | |

**allocation** (M3)
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| order_id | BIGINT | FK, NN, IX |
| order_line_id | BIGINT | FK nullable (per-line in ADVANCED) |
| warehouse_id | BIGINT | FK, NN, IX |
| reservation_id | BIGINT | FK, NN |
| quantity | INTEGER | NN, CK > 0 |
| score | NUMERIC(8,3) | NN |
| score_breakdown | JSONB | NN — `{availability, regionMatch, workload, capacity, priority}` |
| created_at | | |

**pick_list** (M3): id, warehouse_id FK IX, status (OPEN, IN_PROGRESS, COMPLETED, CANCELLED), assigned_to FK app_user nullable, created_at/updated_at.
**pick_list_item** (M3): id, pick_list_id FK IX, order_line_id FK, allocation_id FK, quantity_requested, quantity_picked (CK ≤ requested), status (PENDING, PICKED, SHORT).
**package** (M3): id, order_id FK IX, weight_grams, dimensions JSONB, status (PACKED, SHIPPED), created_at.
**package_item**: id, package_id FK, order_line_id FK, quantity.
**shipment** (M3): id, order_id FK IX, warehouse_id FK, carrier VARCHAR(32) (stub), tracking_number VARCHAR(64) U nullable, status (CREATED, SHIPPED), shipped_at TIMESTAMPTZ.
**shipment_package**: shipment_id FK, package_id FK, PK (both).
**return_request** (M4): id, order_line_id FK IX, quantity CK > 0, reason VARCHAR(64), disposition (RESTOCK, QUARANTINE), warehouse_id FK, status (RECEIVED, PROCESSED), processed_by FK, created_at/processed_at.
**stock_transfer** (M4): id, transfer_number U, source_warehouse_id FK, destination_warehouse_id FK (CK source ≠ destination), status (REQUESTED, IN_TRANSIT, RECEIVED, CANCELLED), requested_by FK, dispatched_at, received_at.
**stock_transfer_line**: id, transfer_id FK IX, sku_id FK, quantity CK > 0, quantity_received CK ≤ quantity.
**low_stock_alert** (M4): id, inventory_level_id FK IX, available_at_alert INTEGER, threshold INTEGER, status (OPEN, ACKNOWLEDGED, RESOLVED), created_at, acknowledged_by FK; partial U index on (inventory_level_id) WHERE status = 'OPEN' (one open alert per level).

**app_user**
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| username | VARCHAR(64) | U, NN |
| email | VARCHAR(256) | U, NN |
| password_hash | VARCHAR(72) | NN (BCrypt) |
| role | VARCHAR(32) | NN, CK IN (ADMIN, OPS_MANAGER, WAREHOUSE_ASSOCIATE, VIEWER) |
| warehouse_id | BIGINT | FK nullable (associates belong to one warehouse) |
| active | BOOLEAN | NN |
| created_at / updated_at | | |

**refresh_token** (if you choose refresh tokens — §12): id, user_id FK IX, token_hash VARCHAR(64) U, expires_at, revoked BOOLEAN.

**audit_event**
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| occurred_at | TIMESTAMPTZ | NN, IX |
| actor_user_id | BIGINT | FK nullable (system jobs) |
| request_id | VARCHAR(64) | IX |
| action | VARCHAR(64) | NN, e.g. `ORDER_CREATED`, `STOCK_ADJUSTED`, `RESERVATION_RELEASED` |
| entity_type | VARCHAR(64) | NN |
| entity_id | BIGINT | NN |
| — | | IX (entity_type, entity_id, occurred_at) |
| summary | JSONB | before/after or key fields — **no PII** beyond ids |
| No UPDATE/DELETE | | append-only by convention now; LedgerX will enforce it with triggers |

**idempotency_key**
| column | type | constraints |
|---|---|---|
| id | BIGINT | PK |
| key | VARCHAR(128) | NN |
| user_id | BIGINT | FK, NN |
| endpoint | VARCHAR(128) | NN (e.g. `POST /api/v1/orders`) |
| request_hash | CHAR(64) | NN (SHA-256 of canonical body) |
| response_status | SMALLINT | nullable until completed |
| response_body | JSONB | nullable until completed |
| status | VARCHAR(16) | NN CK IN (IN_PROGRESS, COMPLETED) |
| created_at | TIMESTAMPTZ | NN |
| expires_at | TIMESTAMPTZ | NN, IX (purge job) |
| — | | **U (user_id, endpoint, key)** — this unique constraint is what makes two concurrent identical requests safe |

### 8.4 ERD specification (what you must draw in `DATABASE.md`)
- Crow's-foot notation, every table above, PK/FK marked, cardinalities: warehouse 1—* inventory_level *—1 sku *—1 product; customer_order 1—* order_line 1—* reservation *—1 inventory_level; order 1—* allocation; warehouse 1—* pick_list 1—* pick_list_item; order 1—* package 1—* package_item; order 1—* shipment *—* package; order_line 1—* return_request; stock_transfer 1—* stock_transfer_line; app_user 1—* audit_event.
- Colour or group by feature package (catalog / inventory / orders / fulfillment / auth / audit).
- A separate small figure: the **inventory quantity state flow** (`on_hand` ⇄ `reserved` → `allocated` → `picked` → `shipped`; `quarantined` side path).
- Mermaid `erDiagram` is acceptable and renders on GitHub.

## 9. API design

Base path `/api/v1`. JSON only. Timestamps ISO-8601 UTC. Ids are numbers. Enums are upper-snake strings.

### 9.1 Conventions
- **Auth:** `Authorization: Bearer <access JWT>` on everything except `POST /auth/login`, `POST /auth/refresh`, `/actuator/health`, `/v3/api-docs`, `/swagger-ui/**`.
- **Idempotency:** `Idempotency-Key: <client-generated UUID>` required on `POST /orders` (and `POST /returns`, `POST /transfers` — decide; at least orders). Missing → `400 IDEMPOTENCY_KEY_MISSING`.
- **Request id:** accept `X-Request-Id` or generate one; echo it in the response header and every log line.
- **Errors:** RFC 9457 `application/problem+json` via Spring's `ProblemDetail` (§19).
- **Versioning:** path (`/v1`). Breaking changes bump the path; you will not need v2 in this project.

### 9.2 Endpoint table

Roles: A = ADMIN, M = OPS_MANAGER, W = WAREHOUSE_ASSOCIATE, V = VIEWER. "Read" roles include everyone above them.

| Method | Path | Roles | Request | Response | Status / errors |
|---|---|---|---|---|---|
| POST | `/auth/login` | public | `{username, password}` | `{accessToken, expiresIn, refreshToken?}` | 200; 401 `BAD_CREDENTIALS` |
| POST | `/auth/refresh` | public | `{refreshToken}` | same as login | 200; 401 `REFRESH_INVALID` |
| GET | `/me` | any | — | `{id, username, role, warehouseId}` | 200 |
| GET | `/warehouses` | V+ | page params | `Page<WarehouseDto>` | 200 |
| POST | `/warehouses` | A | `WarehouseCreate` | `WarehouseDto` | 201 + Location; 400; 409 `DUPLICATE_CODE` |
| GET/PUT/PATCH | `/warehouses/{id}` | V+ / A | … | `WarehouseDto` | 200; 404 |
| GET/POST | `/products`, `/products/{id}` | V+ / M+ | `ProductCreate` | `ProductDto` (with skus) | 201/200; 404 |
| GET/POST | `/skus`, `/skus/{id}` | V+ / M+ | `SkuCreate` | `SkuDto` | 201/200; 409 `DUPLICATE_CODE` |
| GET | `/inventory` | V+ | filters: `warehouseId, skuId, skuCode, belowThreshold, page, size, sort` | `Page<InventoryLevelDto>` incl. computed `available` | 200 |
| GET | `/inventory/{warehouseId}/{skuId}` | V+ | — | `InventoryLevelDto` | 200; 404 |
| POST | `/inventory/adjustments` | M+ (W for RECEIPT/COUNT at own warehouse) | `{warehouseId, skuId, delta, reason, note}` | `InventoryLevelDto` | 201; 400; 409 `NEGATIVE_STOCK` |
| GET | `/inventory/low-stock` | V+ | `warehouseId?` | `[LowStockDto]` (cached) | 200 |
| POST | `/orders` | M+ (or API client role) | header `Idempotency-Key`; `{externalRef, customerRef, shipRegion, priority, lines:[{skuCode, quantity}]}` | `OrderDto` | 201; 200 on replay (decide: 201 replay is also fine — be consistent); 400; 409 `INSUFFICIENT_STOCK`; 409 `IDEMPOTENCY_IN_PROGRESS`; 422 `IDEMPOTENCY_KEY_REUSED` |
| GET | `/orders` | V+ | `status?, region?, from?, to?, q?, page, size, sort` | `Page<OrderSummaryDto>` | 200 |
| GET | `/orders/{id}` | V+ | — | `OrderDto` (lines, reservations, allocation explanation, shipments) | 200; 404 |
| POST | `/orders/{id}/cancel` | M+ | `{reason}` | `OrderDto` | 200; 409 `ILLEGAL_STATE_TRANSITION` |
| POST | `/orders/{id}/allocate` | M+ (also triggered automatically after reservation in M3) | — | `OrderDto` with allocation | 200; 409 `NO_WAREHOUSE_CAN_FULFIL` |
| GET | `/allocations/{orderId}/explanation` | V+ | — | score table | 200 |
| POST | `/pick-lists` | M+ | `{warehouseId, orderIds?}` | `PickListDto` | 201 |
| GET | `/pick-lists` | W+ | `warehouseId, status` | `Page<PickListDto>` | 200 |
| POST | `/pick-lists/{id}/start` | W+ (own warehouse) | — | `PickListDto` | 200; 409 |
| POST | `/pick-lists/{id}/items/{itemId}/pick` | W+ | `{quantityPicked}` | `PickListItemDto` | 200; 409 `OVER_PICK` |
| POST | `/pick-lists/{id}/complete` | W+ | — | `PickListDto` | 200; 409 |
| POST | `/orders/{id}/packages` | W+ | `{items:[{orderLineId, quantity}], weightGrams?}` | `PackageDto` | 201; 409 `NOT_PICKED` |
| POST | `/orders/{id}/ship` | W+ | `{packageIds, carrier}` | `ShipmentDto` | 201; 409 |
| POST | `/returns` | W+ | `Idempotency-Key`; `{orderLineId, quantity, reason, disposition, warehouseId}` | `ReturnDto` | 201; 409 `EXCEEDS_SHIPPED_QTY` |
| POST | `/transfers` | M+ | `{sourceWarehouseId, destinationWarehouseId, lines:[{skuId, quantity}]}` | `TransferDto` | 201; 400 same warehouse |
| POST | `/transfers/{id}/dispatch` | W+ (source) | — | `TransferDto` | 200; 409 `INSUFFICIENT_STOCK` |
| POST | `/transfers/{id}/receive` | W+ (destination) | `{lines:[{lineId, quantityReceived}]}` | `TransferDto` | 200; 409 |
| POST | `/transfers/{id}/cancel` | M+ | — | `TransferDto` | 200; 409 (only REQUESTED) |
| GET | `/alerts/low-stock` | V+ | `status?` | `Page<AlertDto>` | 200 |
| POST | `/alerts/low-stock/{id}/ack` | M+ | — | `AlertDto` | 200 |
| GET | `/audit` | A, M | `entityType, entityId, actor, from, to, page` | `Page<AuditEventDto>` | 200 |
| POST | `/users` | A | `{username, email, password, role, warehouseId?}` | `UserDto` | 201; 409 `DUPLICATE_USERNAME` |
| GET | `/users` | A | page | `Page<UserDto>` (never returns hash) | 200 |
| POST | `/reports/inventory-export` | M+ | `{warehouseId?}` | `{jobId, s3Key?}` | 202 (M5) |
| GET | `/actuator/health` | public (details for A) | — | health incl. `db`, `redis` | 200/503 |

### 9.3 Pagination, filtering, sorting
- Query params `page` (0-based), `size` (default 20, max 100 — reject above with `400`), `sort=field,asc|desc` (repeatable). Use Spring Data `Pageable` with a whitelist of sortable fields per endpoint (unknown field → `400 INVALID_SORT`).
- Response shape (do not leak Spring's `PageImpl` serialisation — map to your own):
  ```json
  {"content":[...],"page":0,"size":20,"totalElements":143,"totalPages":8,"sort":"createdAt,desc"}
  ```
- Filters are explicit query params, combined with `AND`. Implement with JPA `Specification` or a hand-written query; document which and why.
- Free-text `q` on orders searches `order_number`, `external_ref`, `customer_ref` with `ILIKE` (index consideration in §21).

## 10. OpenAPI expectations

- `springdoc-openapi-starter-webmvc-ui` (2.x for Boot 3). UI at `/swagger-ui.html`, spec at `/v3/api-docs`.
- Global `@OpenAPIDefinition` with title, version, contact, and a `bearerAuth` `@SecurityScheme` (HTTP, bearer, JWT) applied via `@SecurityRequirement` so the "Authorize" button works.
- Every controller method: `@Operation(summary, description)`, `@ApiResponse` for each status in the table including ProblemDetail examples (`@ExampleObject`), `@Parameter` descriptions on filters, `@Schema(description, example)` on DTO fields.
- Group tags by feature: Auth, Catalog, Inventory, Orders, Fulfillment, Returns, Transfers, Alerts, Audit, Admin.
- Export the spec in CI (`curl /v3/api-docs > docs/openapi.json`) and commit it so `API.md` can link to a static copy.
- Test: a `@SpringBootTest` that fetches `/v3/api-docs` and asserts it parses and lists every path you expect (cheap regression against forgotten annotations).

## 11. Package and folder structure

Maven single module, **package-by-feature** (not by layer). Feature packages own their controller, service, repository, entities and DTOs; `common` holds cross-cutting code.

```
flowgrid/
├── .github/workflows/ci.yml, deploy.yml
├── api/                              # Spring Boot app (or root if you prefer single-project)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/<you>/flowgrid/
│       ├── FlowGridApplication.java
│       ├── common/
│       │   ├── config/        (Jackson, OpenAPI, Redis, Async/Scheduling, Clock bean)
│       │   ├── error/         (GlobalExceptionHandler, ErrorCode enum, domain exceptions)
│       │   ├── idempotency/   (IdempotencyFilter/Interceptor, IdempotencyKey entity+repo+service)
│       │   ├── logging/       (RequestIdFilter, MDC, JSON encoder config)
│       │   ├── paging/        (PageResponse, SortWhitelist)
│       │   └── state/         (StateMachine helper: allowed transitions + IllegalStateTransitionException)
│       ├── auth/              (SecurityConfig, JwtService, JwtAuthFilter, AuthController, AppUser, UserRepository, PasswordConfig)
│       ├── catalog/           (Product, Sku, repos, CatalogService (+ cache), CatalogController, dtos/)
│       ├── inventory/         (Warehouse, InventoryLevel, StockAdjustment, InventoryService, reservation/ (Reservation, ReservationService), transfer/, lowstock/ (LowStockJob, alerts), InventoryController)
│       ├── orders/            (CustomerOrder, OrderLine, OrderService, OrderStateMachine, OrderController, dtos/)
│       ├── fulfillment/       (allocation/ (AllocationService, AllocationScorer, ScoreBreakdown), picking/ (PickList…), packing/, shipping/, returns/)
│       ├── audit/             (AuditEvent, AuditService, AuditController)
│       └── reports/           (InventoryExportJob, S3Client wrapper)  [M5]
│   └── src/main/resources/
│       ├── application.yml, application-dev.yml, application-prod.yml, logback-spring.xml
│       └── db/migration/V1__baseline.sql … 
│   └── src/test/java/...      (mirrors main; + support/ for Testcontainers base class, TestData builders)
├── web/                              # React + TS (Vite)
│   ├── src/{api,auth,components,pages/{inventory,orders,picking,lowstock,transfers,returns},hooks,types}
│   ├── Dockerfile (nginx)
│   └── vite.config.ts
├── docker-compose.yml                # dev: postgres, redis, (api optional)
├── docker-compose.prod.yml           # prod: api, web, redis (postgres = RDS)
├── load/                             # k6 scripts + results/
├── tools/                            # Python 3.12 package (required component, M4–M5)
│   ├── pyproject.toml                # deps: httpx, pydantic (or dataclasses), typer/argparse, psycopg[binary]; dev: pytest, pytest-asyncio, mypy, ruff
│   ├── README.md                     # install, commands, report format, how the harness verifies contention
│   ├── src/flowgrid_tools/
│   │   ├── __init__.py
│   │   ├── models.py                 # typed dataclasses/pydantic: Warehouse, Sku, InventorySeed, OrderSpec
│   │   ├── generate.py               # deterministic generator (seeded random), distributions, duplicate injection
│   │   ├── sinks.py                  # JsonSink / CsvSink / ApiSink (httpx, login, Idempotency-Key) / SqlSink (psycopg COPY)
│   │   ├── harness.py                # asyncio + httpx concurrent order creation; latency percentiles; status histogram
│   │   ├── contention.py             # hot-SKU scenario: expected = min(units, competitors); verify via GET /inventory
│   │   ├── report.py                 # Markdown + JSON report writer (environment block, methodology, results)
│   │   └── cli.py                    # `flowgrid-tools generate|seed|load|contention|report`
│   └── tests/                        # pytest: test_generate.py, test_sinks.py, test_harness.py (respx/mock server), test_contention.py, test_report.py
├── scripts/                          # seed-demo-data.sh (wraps tools/), teardown-aws.sh
├── docs/                             # ARCHITECTURE.md, API.md, DATABASE.md, TESTING.md, DEPLOYMENT.md, SECURITY.md, DESIGN_DECISIONS.md, PERFORMANCE.md, adr/, diagrams/, screenshots/
└── README.md
```

Rules: no `util` dumping ground; no cross-feature repository access (orders calls `InventoryService`/`ReservationService`, not `InventoryLevelRepository`); entities never leave the service layer (DTOs out); `@Transactional` only on services.

## 12. Authentication model

- **Password storage:** `BCryptPasswordEncoder` (strength 10–12; measure login time and note it). Never log or return hashes.
- **Access token:** JWT (HS256 with a ≥ 256-bit secret from an env var; RS256 is an ADR option — HS256 is acceptable for a single service). Claims: `sub` (user id), `username`, `role`, `wid` (warehouse id), `iat`, `exp` (15 min), `jti`. Signed and verified with the `jjwt` or `nimbus-jose-jwt` library — pick one, document it. Spring Security 6 `SecurityFilterChain` bean with a `OncePerRequestFilter` that parses the header and sets an `Authentication` in the `SecurityContext`; stateless session policy; CSRF disabled (token-based, no cookies) — say *why* in `SECURITY.md`.
- **Refresh strategy — decision required (write ADR-002):**
  - *Option A — no refresh, 8-hour access token.* Simplest. Risk: stolen token lives 8 h; no revocation.
  - *Option B — short access (15 min) + opaque refresh token stored hashed in `refresh_token`, rotated on every use, revocable.* Recommended for the Strong Résumé Version; it gives you a revocation story in interviews.
  - *Option C — refresh in an `HttpOnly` cookie.* Better XSS posture, but adds CSRF considerations and CORS cookie config; fine as ADVANCED.
- **Client side:** access token in memory (not `localStorage` — explain XSS), refresh on `401`, redirect to login on refresh failure ([`../../08-react/04-api-integration-auth.md`](../../08-react/04-api-integration-auth.md)).
- **Seed:** an `admin` user created by a Flyway repeatable migration or a `CommandLineRunner` that reads the initial password from an env var — never a hard-coded password in the repo.

## 13. Authorization model

| Action | ADMIN | OPS_MANAGER | WAREHOUSE_ASSOCIATE | VIEWER |
|---|:-:|:-:|:-:|:-:|
| Manage users | ✔ | – | – | – |
| Create/edit warehouses | ✔ | – | – | – |
| Create/edit products & SKUs | ✔ | ✔ | – | – |
| View catalog, inventory, orders | ✔ | ✔ | ✔ (own warehouse for inventory) | ✔ |
| Stock adjustment (any reason) | ✔ | ✔ | RECEIPT / COUNT_CORRECTION at own warehouse only | – |
| Create order / cancel order / trigger allocation | ✔ | ✔ | – | – |
| Create pick list | ✔ | ✔ | – | – |
| Start/pick/complete pick list, pack, ship | ✔ | ✔ | ✔ own warehouse | – |
| Register return | ✔ | ✔ | ✔ own warehouse | – |
| Create/cancel transfer | ✔ | ✔ | – | – |
| Dispatch / receive transfer | ✔ | ✔ | ✔ own warehouse (source / destination respectively) | – |
| Ack low-stock alert | ✔ | ✔ | – | – |
| Read audit log | ✔ | ✔ | – | – |
| Trigger report export | ✔ | ✔ | – | – |

Implementation: `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` / `hasAnyRole(...)` on service (or controller) methods; the "own warehouse" rule is a custom check (`@PreAuthorize("@warehouseAccess.canAct(#warehouseId)")`) reading `wid` from the principal. Also configure URL-level defaults in the `SecurityFilterChain` (`anyRequest().authenticated()`) so a forgotten annotation still needs a valid token. Test every row of this matrix at least once (§14.5).

## 14. Testing strategy

Full details per milestone in [`milestones.md`](./milestones.md) and topic files in [`../../09-testing/`](../../09-testing/README.md).

### 14.1 Pyramid
| Layer | Tool | Runs in | Target |
|---|---|---|---|
| Unit | JUnit 5 + Mockito + AssertJ | `mvn test` | State machines, allocation scorer, validation, JWT service, idempotency hashing — fast, no Spring |
| Slice | `@WebMvcTest` (controllers + security), `@DataJpaTest` (queries, constraints) with Testcontainers Postgres (not H2 — the `FOR UPDATE`, `JSONB` and `CHECK` semantics matter) | `mvn test` | Every controller; every custom query |
| Integration | `@SpringBootTest` + Testcontainers (Postgres + Redis), `TestRestTemplate`/`MockMvc` | `mvn verify` (failsafe `*IT`) | End-to-end flows: create → allocate → pick → pack → ship; Redis down |
| Concurrency | `@SpringBootTest` + `ExecutorService` + `CountDownLatch` | `mvn verify` | The named tests below |
| API contract | OpenAPI presence test; optional REST-assured | `mvn verify` | Spec completeness |
| Load | k6 **+ `tools/` harness** | manual, recorded | k6 = baseline numbers; harness = realistic mixes + invariant verification (contention pass/fail) |
| Python tooling | `pytest` + `pytest-asyncio` + `respx` (mock HTTP) + `mypy --strict` + `ruff` | `cd tools && pytest` in CI | Generator validity, sink behaviour, harness statistics, contention arithmetic, report format |
| Frontend | Vitest + React Testing Library | `npm test` | Key components, API client, auth flow |

### 14.2 Rules
- One shared Testcontainers base class with static reusable containers (`@ServiceConnection` in Boot 3.1+). Containers start once per JVM.
- Test data via builders, not shared SQL fixtures that couple tests. `@Transactional` rollback on slice tests; **not** on concurrency tests (they need real commits across threads).
- A `Clock` bean injected everywhere time matters, so expiry tests don't sleep.
- JaCoCo report in CI; check ≥ 70 % on `service` packages (not a vanity number for the whole project).

### 14.3 Mandatory named tests (M1–M2)
- `warehouseController_createWithDuplicateCode_returns409ProblemDetail`
- `inventoryLevel_reservedGreaterThanOnHand_violatesCheckConstraint`
- `stockAdjustment_negativeResult_rejectedWith409AndNoAuditEvent`
- `jwtAuthFilter_expiredToken_returns401WithProblemDetail`
- `orderCreate_missingIdempotencyKey_returns400`
- `orderCreate_sameKeySameBody_returnsSameOrderIdAndCreatesOneOrder`
- `orderCreate_sameKeyDifferentBody_returns422`
- `orderCreate_concurrentSameKey_createsExactlyOneOrder`
- `reserve_whenOneUnitAndTenConcurrentOrders_exactlyOneSucceeds`
- `reserve_whenFiveUnitsAndTenConcurrentSingleUnitOrders_exactlyFiveSucceedAndReservedEqualsFive`
- `cancelOrder_releasesAllReservationsAtomically`
- `cancelOrder_afterPicked_returns409IllegalTransition`

### 14.4 Mandatory named tests (M3–M5)
- `allocation_sameInputsTwice_producesIdenticalWarehouseAndScore` (determinism)
- `allocation_tie_brokenByLowestWarehouseId`
- `allocation_everyWarehouseShort_orderBackorderedAndNothingReserved`
- `pickPackShip_fullFlow_movesQuantitiesAndOrderReachesShipped`
- `ship_failureAfterPackageCreated_rollsBackInventoryAndShipment`
- `partialShip_oneLineShort_orderPartiallyShippedAndRemainderOpen`
- `catalogCache_secondReadHitsRedisNotDb` (count queries with a Hibernate statistics or a `DataSource` proxy)
- `catalogCache_updateInvalidatesKey`
- `catalog_whenRedisDown_readsFromDbAndReturns200`
- `transfer_dispatchThenReceive_sourceDecrementsDestinationIncrementsExactlyOnce`
- `transfer_twoOppositeTransfersConcurrently_noDeadlockOrDeterministicRetry`
- `return_restockIncrementsOnHand_quarantineIncrementsQuarantined`
- `lowStockJob_levelBelowThreshold_createsOneOpenAlertNotTwo`
- `roleMatrix_associateCannotCreateWarehouse_403` (+ one test per matrix row)

Mandatory `tools/` pytest cases (M4–M5; details in [`milestones.md`](./milestones.md)):
- `test_generator_produces_valid_skus` (unique codes, matching regex, product linkage, attributes JSON-serialisable)
- `test_generator_is_deterministic_for_same_seed`
- `test_generator_order_mix_matches_configured_distributions` (within tolerance)
- `test_api_sink_sends_idempotency_key_and_retries_on_5xx` (mocked HTTP)
- `test_harness_computes_percentiles_correctly` (known latency sample → known p50/p95/p99)
- `test_harness_reports_contention` (mocked API returning N×201 then 409s → report says `successes == min(units, competitors)`)
- `test_contention_flags_oversell` (successes > units → report status `FAIL`)
- `test_report_contains_environment_and_methodology_blocks`

### 14.5 What "tested" means at `v1.0`
`mvn verify` green in CI on every PR; concurrency tests run there too (Testcontainers on GitHub-hosted runners works with Docker preinstalled); `TESTING.md` explains how to run each layer and lists the mandatory tests with a one-line description each.

## 15. Docker strategy

- **API Dockerfile (multi-stage):** stage 1 `maven:3.9-eclipse-temurin-21` builds with cached dependencies (`dependency:go-offline` layer before copying `src`); stage 2 `eclipse-temurin:21-jre` (or `-jre-alpine` if you accept musl trade-offs), non-root user, `COPY --from=build target/*.jar app.jar`, `EXPOSE 8080`, `ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]`. Optionally Spring Boot layered jars (`java -Djarmode=layertools -jar app.jar extract`) for better layer caching — document if used. `HEALTHCHECK` hitting `/actuator/health`.
- **Web Dockerfile:** `node:20` build stage → `nginx:alpine` serving `dist/` with a `nginx.conf` that proxies `/api` to the API container and falls back to `index.html` for SPA routes.
- **`docker-compose.yml` (dev):** `postgres:16` (volume, `POSTGRES_*` from `.env`, healthcheck `pg_isready`), `redis:7` (healthcheck `redis-cli ping`), optional `api` profile. You run the API from the IDE against these.
- **`docker-compose.prod.yml`:** `api` (image from GHCR, env from `.env` on the host, `depends_on` with `condition: service_healthy`, `restart: unless-stopped`, `logging: awslogs` driver), `web`, `redis` (with `maxmemory` + `allkeys-lru`), **no** Postgres (RDS). Resource limits (`mem_limit`) sized for a t3.small/t3.micro.
- **Never** bake secrets into images; `.env` files are `.gitignore`d; `.env.example` is committed.
- See [`../../11-docker/dockerfiles.md`](../../11-docker/dockerfiles.md) and [`../../11-docker/compose.md`](../../11-docker/compose.md).

## 16. CI/CD strategy

GitHub Actions ([`../../13-cicd/github-actions.md`](../../13-cicd/github-actions.md), [`../../13-cicd/pipeline-examples.md`](../../13-cicd/pipeline-examples.md)).

**`ci.yml`** — on every PR and push to `main`:
1. `checkout` → `setup-java` 21 (Temurin) with Maven cache
2. `mvn -B verify` (unit + slice + integration + concurrency via Testcontainers; Docker is available on `ubuntu-latest`)
3. Upload JaCoCo + Surefire/Failsafe reports as artefacts
4. `web`: `npm ci`, `npm run lint`, `npm test`, `npm run build`
5. `tools`: `setup-python` 3.12, `pip install -e .[dev]`, `ruff check`, `mypy`, `pytest` (from M4 on)
6. Optional: `mvn dependency-check` or `trivy fs` for vulnerabilities (nice-to-have)

**`deploy.yml`** — on tag `v*` (and manual `workflow_dispatch`):
1. Re-run tests (or require `ci.yml` success on the same SHA)
2. `docker/build-push-action` → `ghcr.io/<you>/flowgrid-api:<tag>` and `:latest`, and `flowgrid-web`
3. SSH (or SSM Run Command) to EC2: `docker compose -f docker-compose.prod.yml pull && up -d`, then `curl /actuator/health` until `UP` (with timeout) — fail the job otherwise
4. Post-deploy smoke: login + `GET /inventory` returns 200

Secrets in GitHub: `EC2_HOST`, `EC2_SSH_KEY` (or OIDC role for SSM), `GHCR` uses `GITHUB_TOKEN`. Protect `main`: PRs required, CI required, no force-push.

## 17. AWS deployment plan

Read [`../../12-aws/README.md`](../../12-aws/README.md), [`iam.md`](../../12-aws/iam.md), [`ec2.md`](../../12-aws/ec2.md), [`rds.md`](../../12-aws/rds.md), [`s3.md`](../../12-aws/s3.md), [`cloudwatch.md`](../../12-aws/cloudwatch.md), [`deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md), [`cost-safety.md`](../../12-aws/cost-safety.md).

| Piece | Choice | Notes |
|---|---|---|
| Compute | 1× EC2 `t3.small` (Amazon Linux 2023 or Ubuntu 24.04), Docker + Compose v2 installed via user-data script | Security group: 22 from your IP only, 80/443 from anywhere. Elastic IP so the URL survives restarts |
| Database | RDS PostgreSQL 16 `db.t3.micro`/`t4g.micro`, single-AZ, 20 GB gp3, automated backups 1–7 days | Private subnet or SG that only allows the EC2 SG on 5432. `sslmode=require` in the JDBC URL. Free-tier eligible for 12 months on new accounts |
| Redis | **Decision (ADR-005):** Redis container on the same EC2 (default — $0 extra, and correctness never depends on it) **vs** ElastiCache `cache.t3.micro` (managed, but ~$12+/month and needs VPC wiring) | Choose EC2 unless you want to demonstrate ElastiCache specifically; write down why |
| Object storage | S3 bucket `flowgrid-exports-<random>` private, lifecycle rule expiring objects after 30 days | Inventory CSV export job writes here |
| Identity | IAM **instance role** on the EC2 with a policy allowing only `s3:PutObject`/`GetObject` on that bucket prefix and `logs:*` on the log group. No access keys on the box | Separate IAM user for your CLI with MFA; deploy uses SSH key or SSM |
| Logs | Docker `awslogs` driver → CloudWatch log group `/flowgrid/api` (retention 7 days) | Metric filter on `"level":"ERROR"` → alarm when > 5 in 5 min → SNS email |
| TLS | Caddy or nginx with Let's Encrypt on a free subdomain (e.g. DuckDNS) — optional; document if you stay on HTTP for cost/time | The SPA must call the API over the same scheme |
| Cost | ~$15–25/month with EC2 + RDS; **budget alarm at $20**; `scripts/teardown-aws.sh` (stop EC2, snapshot + delete RDS, empty bucket) | Stop the instance when not demoing; RDS can be stopped for 7 days at a time |

Deployment steps live in `docs/DEPLOYMENT.md` with the exact commands you ran, screenshots of the health endpoint from the public URL, and the rollback procedure (`docker compose pull <previous tag>`).

## 18. Logging strategy

- **Format:** JSON to stdout via Logback + `logstash-logback-encoder` (or Boot 3.4+ structured logging `logging.structured.format.console=ecs`). Fields: `timestamp, level, logger, message, requestId, userId, orderId?, durationMs?, exception`.
- **Correlation:** `RequestIdFilter` puts `requestId` (from `X-Request-Id` or generated) and `userId` (after auth) into MDC; cleared in `finally`. Every log line carries them. `@Async`/`@Scheduled` threads set their own `jobId`.
- **Levels:**
  - `ERROR` — unexpected exceptions, DB unavailable, deploy-time failures. Must be actionable; the CloudWatch alarm keys on it.
  - `WARN` — expected-but-notable: Redis unavailable (rate-limited to once per 30 s), reservation conflicts above a threshold, idempotency conflicts, deadlock retries.
  - `INFO` — business events: order created/reserved/allocated/shipped, transfer dispatched, low-stock alert raised, job started/finished with counts. One line per event, with ids.
  - `DEBUG` — SQL (`org.hibernate.SQL` in dev only), cache hit/miss, allocation score breakdown.
- **PII rules:** log ids, never customer names/addresses/emails; never tokens or `Authorization` headers; never request bodies at `INFO`. Mask if you must log a body at `DEBUG`.
- **What not to log:** every request at `INFO` (use Actuator metrics/`http.server.requests` instead). Log slow requests (> 500 ms) at `WARN` with the path.
- See [`../../05-spring-boot/06-logging-actuator.md`](../../05-spring-boot/06-logging-actuator.md).

## 19. Error-handling strategy

- `@RestControllerAdvice` extending `ResponseEntityExceptionHandler` returning `ProblemDetail` (Spring 6 / Boot 3). `spring.mvc.problemdetails.enabled=true` for framework-raised errors.
- Every ProblemDetail has: `type` (URL to `docs/API.md#error-codes`), `title`, `status`, `detail` (safe for humans), `instance` (path), plus extensions `code` (from the catalogue), `requestId`, `timestamp`, and `errors[]` for validation (`field`, `message`).
- Domain exceptions carry an `ErrorCode` enum member; the handler maps code → HTTP status. Never leak stack traces or SQL text to clients.

### Error catalogue

| Code | HTTP | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Bean Validation failure; `errors[]` populated |
| `INVALID_SORT` / `INVALID_PAGE_SIZE` | 400 | Sort field not whitelisted / size > 100 |
| `IDEMPOTENCY_KEY_MISSING` | 400 | Header absent on a POST that requires it |
| `BAD_CREDENTIALS` | 401 | Login failed |
| `TOKEN_EXPIRED` / `TOKEN_INVALID` | 401 | JWT problems (distinguish so the client knows to refresh) |
| `REFRESH_INVALID` | 401 | Refresh token unknown/expired/revoked |
| `FORBIDDEN` | 403 | Role or warehouse check failed |
| `NOT_FOUND` | 404 | Entity missing (include entity type in `detail`) |
| `DUPLICATE_CODE` / `DUPLICATE_USERNAME` | 409 | Unique violation on a natural key |
| `INSUFFICIENT_STOCK` | 409 | Reservation or dispatch cannot be satisfied; `detail` names sku + warehouse + shortfall |
| `NEGATIVE_STOCK` | 409 | Adjustment would take `on_hand` below zero |
| `ILLEGAL_STATE_TRANSITION` | 409 | State machine refused (`from`, `to`, `entity` in extensions) |
| `OVER_PICK` / `NOT_PICKED` / `EXCEEDS_SHIPPED_QTY` | 409 | Fulfillment quantity rules |
| `NO_WAREHOUSE_CAN_FULFIL` | 409 | Allocation found no feasible warehouse |
| `IDEMPOTENCY_IN_PROGRESS` | 409 | Same key, first request still running (`Retry-After: 1`) |
| `CONCURRENT_MODIFICATION` | 409 | Optimistic-lock failure after retries (only in the `@Version` variant) |
| `IDEMPOTENCY_KEY_REUSED` | 422 | Same key, different request hash |
| `RATE_LIMITED` | 429 | Reserved for later |
| `INTERNAL_ERROR` | 500 | Anything unmapped; logged at ERROR with requestId; client gets requestId only |
| `DEPENDENCY_UNAVAILABLE` | 503 | DB unreachable / connection pool exhausted (`Retry-After`) |

## 20. Security considerations

- **Input validation** on every DTO (`@NotNull`, `@Positive`, `@Size`, custom `@ValidSkuCode`); reject unknown JSON fields (`FAIL_ON_UNKNOWN_PROPERTIES=true`) — decide and document.
- **AuthZ on every endpoint** — the matrix in §13; default deny in the filter chain; tests for every row.
- **Object-level authorization (OWASP API1):** an associate at warehouse A must not pick a list at warehouse B even with a valid token. Check ownership in the service, not only the URL.
- **Excessive data exposure (API3):** DTOs, not entities; `UserDto` never carries the hash; audit `summary` has no PII.
- **Mass assignment (API6):** separate create/update DTOs; never bind `role` from a self-service endpoint.
- **Secrets:** env vars only; `.env` ignored; GitHub secret scanning enabled; rotate the JWT secret if it ever leaks; RDS password in the EC2 `.env` with `chmod 600`, or SSM Parameter Store as ADVANCED.
- **Rate limiting (later):** design a place for it (a filter before auth on `/auth/login`, bucket per IP in Redis) but implement only in polish weeks; LedgerX and FlagForge revisit it.
- **Headers:** Spring Security defaults (no cache, X-Content-Type-Options, frame options); CORS allow-list for the SPA origin only.
- **SQL injection:** parameterised queries everywhere; the `q` search builds `ILIKE` with a bound parameter, never string concatenation.
- **Dependencies:** Dependabot on Maven + npm; fix highs before `v1.0`.
- **Transport:** TLS in prod if you did the Caddy step; JDBC `sslmode=require` to RDS regardless.
- Security review checklist and OWASP API Top 10 walkthrough in polish week 24 → `docs/SECURITY.md`.

## 21. Performance considerations

- **Indexes** (all in §8): `inventory_level (warehouse_id, sku_id)` unique; `customer_order (status, created_at)` and `(created_at DESC)`; `order_line (order_id)`; `reservation (order_line_id)`, `(inventory_level_id, status)`; `audit_event (entity_type, entity_id, occurred_at)`; `idempotency_key (user_id, endpoint, key)`; `(expires_at)` for the purge. Verify each with `EXPLAIN (ANALYZE, BUFFERS)` on seeded data (≥ 50k orders) and paste the plans into `PERFORMANCE.md` ([`../../04-sql-databases/05-indexes-performance.md`](../../04-sql-databases/05-indexes-performance.md)).
- **N+1:** orders list must not load lines per row. Use a summary projection for the list, `@EntityGraph`/`JOIN FETCH` for the detail, and `spring.jpa.open-in-view=false` so lazy loads fail loudly in tests rather than silently in controllers. Count queries in a test with Hibernate `Statistics` ([`../../05-spring-boot/03-data-jpa.md`](../../05-spring-boot/03-data-jpa.md)).
- **Caching:** only catalog reads and the low-stock view. Never cache inventory quantities that reservations depend on. Cache key includes a version prefix so a deploy can invalidate wholesale.
- **Pagination:** offset pagination is fine at this scale; note the `OFFSET 100000` cost in `PERFORMANCE.md` and describe cursor pagination as the fix (LedgerX implements it).
- **Connection pool:** HikariCP max 10 on the `t3.small`; RDS micro allows ~80 connections; leave headroom. Lock waits show up as pool exhaustion first — you will see this in scenario 10 of failure engineering.
- **Lock hold time:** keep the reservation transaction tiny — no HTTP calls, no cache writes inside it; write audit rows in the same transaction (consistency) but nothing slower.
- **What to benchmark with k6** (protocol in [`docs-and-resume.md`](./docs-and-resume.md#benchmarking-protocol)): order creation p50/p95/p99 and throughput at 10/20/50 VUs; reservation contention (50 VUs hitting one SKU with 100 units — success ratio + p95); catalog reads with cache cold vs warm (hit ratio from Redis `INFO stats`); orders list page 1 vs page 500. Record hardware, JVM flags, DB location (local container vs RDS), dataset size, warm-up, duration, and the k6 summary. **Never publish a number you didn't measure.**
- **What the Python harness adds:** k6 is excellent at fixed-shape load; the `tools/` harness (FR-27) drives a *realistic order mix* produced by the generator, injects a configurable hot-SKU ratio, and — unlike k6 — **verifies the reservation invariant afterwards** (`successes == min(units, competitors)`, `reserved <= on_hand` for every touched level) by reading the API. Every benchmark run in `PERFORMANCE.md` records both: the k6 summary for numbers and the harness report for the invariant pass/fail. Dataset for index experiments (≥ 50k orders) is produced by `flowgrid-tools seed --sql` so `EXPLAIN` plans are taken on realistic cardinalities.

## 22. Scalability considerations

- **Stateless API:** no HTTP session; JWT carries identity; any number of API containers can run behind a load balancer. Scheduled jobs need a leader guard if you scale to 2+ instances (ShedLock or a DB advisory lock — describe, don't build).
- **DB as source of truth:** every correctness decision (reservation, transfer, idempotency) goes through Postgres row locks and unique constraints. This is the *right* bottleneck: it is the one component that must be consistent.
- **Where it breaks at 100×:**
  1. *Hot rows.* Reservations on a popular SKU serialise on one `inventory_level` row → lock queueing → latency spikes. Mitigations, in order: keep the locked section tiny; shard "virtual stock buckets" per SKU (pre-split quantity across N rows, reserve from any); move promise-checking to an in-memory/Redis counter with async reconciliation (accepting rare oversell + compensation — a business decision).
  2. *Orders list.* Offset pagination and `ILIKE` search on 10M rows → cursor pagination, trigram index or a search index.
  3. *Audit table growth.* Partition by month; archive to S3.
  4. *Single EC2.* Move API to ECS/ASG behind an ALB; RDS read replica for reporting; ElastiCache for the catalog cache so replicas share it.
  5. *Allocation as a synchronous step.* Move to an async job with an outbox event (LedgerX teaches the outbox) so order creation stays fast.
  6. *Idempotency table.* Fine in Postgres at 100×; at 1000× move to Redis with TTL and accept loss of replay after restart, or keep Postgres and partition by day.
- **Multi-region:** the domain is naturally partitioned by warehouse region — a warehouse's inventory rows could live in the region that operates it, with orders routed to the region of the allocated warehouse. Cross-region transfers become the hard case (two-phase already models it). Say this in interviews; do not build it.
- See [`../../15-system-design/scalability.md`](../../15-system-design/scalability.md) and [`../../15-system-design/caching.md`](../../15-system-design/caching.md).

## 23. Git strategy

- **Default branch:** `main`, protected. **Branch naming:** `feat/m2-idempotency-key`, `fix/m3-allocation-tie-break`, `chore/ci-jacoco`, `docs/m5-deployment`. One branch per issue.
- **Conventional Commits:** `feat(orders): reserve stock under row lock on order creation`, `fix(inventory): reject adjustment that would go negative`, `test(reservation): add 10-thread one-unit contention test`, `docs:`, `chore:`, `refactor:`, `perf:`. Body explains *why*; reference issues `Closes #12`.
- **PR per milestone (minimum):** in practice 3–6 PRs per milestone, one per issue; the milestone closes when all its issues close.
- **Tags:** `v0.1` after M1, `v0.2` M2, `v0.3` M3, `v0.4` M4, **`v1.0`** M5 (annotated tags with release notes; GitHub Release with the benchmark summary and screenshots). ADVANCED work → `v1.1`, `v1.2`.
- Rebase feature branches on `main` before merge; squash-merge PRs so `main` history is one commit per issue.
- See [`../../02-git/workflows.md`](../../02-git/workflows.md).

## 24. Milestone branches and issues

Create five GitHub milestones (`M1 – Domain & Auth`, …, `M5 – Deploy, Docs, Measure`) with due dates = end of weeks 4–8. Labels: `type:feat`, `type:test`, `type:docs`, `type:infra`, `area:inventory`, `area:orders`, `area:fulfillment`, `area:web`, `area:ops`. Suggested issues (create them all in week 4 so the board tells the story):

**M1:** #1 Bootstrap Boot 3 + Flyway + Compose Postgres · #2 Catalog entities + CRUD · #3 Warehouse + InventoryLevel + adjustments + constraints · #4 ProblemDetail + error catalogue · #5 Pagination/sort/filter · #6 JWT auth + roles + method security · #7 Audit events · #8 springdoc + annotations · #9 CI `mvn verify` + JaCoCo · #10 Testcontainers base + first slice tests
**M2:** #11 Order + OrderLine + state machine · #12 Idempotency-Key filter + table + purge job · #13 Reservation service with `FOR UPDATE` · #14 Concurrency tests · #15 `@Version` comparison experiment + ADR · #16 Cancellation releases · #17 Multi-stage Dockerfile
**M3:** #18 Allocation scorer + explanation · #19 Pick lists + picking · #20 Packing + shipment · #21 Partial fulfillment · #22 Redis cache-aside catalog + invalidation · #23 Low-stock cache + Redis-down degradation · #24 Health indicators
**M4:** #25 Vite app scaffold + auth + API client · #26 Inventory page · #27 Orders list/detail · #28 Pick/pack screens · #29 Low-stock view · #30 Returns · #31 Transfers (two-phase) · #32 Scheduled low-stock alerts · #33 Web Dockerfile + nginx proxy · #34 `tools/` package scaffold (pyproject, ruff, mypy, pytest in CI) · #35 Generator + JSON/CSV/API/SQL sinks + tests
**M5:** #36 Prod compose + env · #37 AWS: IAM, EC2, RDS, SG · #38 S3 export job · #39 CloudWatch logs + alarm · #40 deploy.yml (GHCR → EC2) · #41 k6 scripts + baseline report · #42 `tools/` load harness + contention verifier + report writer + tests · #43 Docs set (9 files + `tools/README.md`) · #44 Failure-engineering exercises · #45 Demo data (via `tools/`) + GIF · #46 Tag `v1.0`

## 25. PR workflow

`.github/pull_request_template.md`:
```
## What
## Why (link issue)
## How (design notes, trade-offs)
## Tests added / run
## Screenshots / curl (if API or UI)
## Checklist
- [ ] Conventional Commit title
- [ ] Tests pass locally (`mvn verify`, `npm test`)
- [ ] New endpoints documented in OpenAPI and API.md
- [ ] Migrations are additive and numbered
- [ ] No secrets, no PII in logs
- [ ] Error codes added to catalogue
- [ ] ADR written if a significant decision was made
```
**Self-review checklist** (you are your own reviewer — do it on GitHub's "Files changed" view, not in the IDE, and leave real comments):
- Would a reader know *why* from the commit messages alone?
- Every `@Transactional` boundary intentional? Any HTTP/cache call inside one?
- Every new query indexed? Any N+1?
- Every new state transition tested for the illegal case?
- Every endpoint has a `403` test?
- Anything copied that you cannot explain? Delete it and rewrite.

## 26. README requirements for your repo

Your `flowgrid/README.md` must contain (checklist; details in [`docs-and-resume.md`](./docs-and-resume.md)):
- [ ] One-paragraph description + the technical identity sentence
- [ ] Badges: CI status, coverage, latest tag
- [ ] Architecture diagram (image) + link to `docs/ARCHITECTURE.md`
- [ ] Feature list mapped to scope tier (MVP / v1.0 / Advanced)
- [ ] Quick start: `docker compose up -d`, `mvn spring-boot:run`, `npm run dev`, seed script, default credentials via env
- [ ] Live demo URL (or "stopped to save cost — run locally" with a date) + screenshots/GIF
- [ ] API summary + Swagger link + link to `docs/API.md`
- [ ] Testing summary (how to run each layer; the concurrency test named explicitly)
- [ ] Measured results table (from `PERFORMANCE.md`) with environment line — **only measured numbers**
- [ ] Design decisions highlights (3–5 bullets linking ADRs)
- [ ] Failure engineering summary linking the scenarios
- [ ] Roadmap / known limitations (honest)
- [ ] Licence

## 27. Demo requirements

- `scripts/seed-demo-data.sh` wrapping `flowgrid-tools seed --profile demo --seed 42` (the Python generator, FR-26): 3 warehouses in different regions, 50 products / 120 SKUs, inventory levels with a few deliberately low, 200 orders across all states, 2 open pick lists, 1 in-transit transfer, 2 open alerts; users `admin`, `manager`, `associate-east`, `viewer` with passwords from env. Re-runnable (idempotent — truncate or upsert).
- Screenshots in `docs/screenshots/`: Swagger UI with Authorize; inventory page filtered; order detail with allocation explanation; pick screen; low-stock view; CloudWatch alarm; k6 summary.
- One GIF (≤ 15 s, ≤ 5 MB): create order via Swagger → see it on the dashboard → pick → ship → inventory changes.
- A 2-minute demo script (words you will say) in `docs/DEMO.md` — the same script as the 2-minute pitch in [`interview-questions.md`](./interview-questions.md#2-minute-version).

## 28. Links

**This project:** [`milestones.md`](./milestones.md) · [`failure-engineering.md`](./failure-engineering.md) · [`interview-questions.md`](./interview-questions.md) · [`docs-and-resume.md`](./docs-and-resume.md) · templates: [`design-doc.md`](../templates/design-doc.md), [`adr.md`](../templates/adr.md), [`benchmark-report.md`](../templates/benchmark-report.md) · [`../README.md`](../README.md) · [`../../PROJECTS.md`](../../PROJECTS.md) · [`../../PROJECT_SELECTION.md`](../../PROJECT_SELECTION.md)

**Topics:**
- Spring Boot: [`01-core-di`](../../05-spring-boot/01-core-di.md) · [`02-web-layer`](../../05-spring-boot/02-web-layer.md) · [`03-data-jpa`](../../05-spring-boot/03-data-jpa.md) · [`04-validation-errors`](../../05-spring-boot/04-validation-errors.md) · [`05-security-jwt`](../../05-spring-boot/05-security-jwt.md) · [`06-logging-actuator`](../../05-spring-boot/06-logging-actuator.md) · [`07-transactions`](../../05-spring-boot/07-transactions.md) · [`08-caching-scheduling`](../../05-spring-boot/08-caching-scheduling.md)
- SQL/DB: [`02-joins-aggregation`](../../04-sql-databases/02-joins-aggregation.md) · [`04-schema-design`](../../04-sql-databases/04-schema-design.md) · [`05-indexes-performance`](../../04-sql-databases/05-indexes-performance.md) · [`06-transactions`](../../04-sql-databases/06-transactions.md) · [`08-jdbc-orm`](../../04-sql-databases/08-jdbc-orm.md) · [`redis.md`](../../04-sql-databases/redis.md)
- REST: [`api-design-guide`](../../06-rest-apis/api-design-guide.md) · [`http-for-apis`](../../06-rest-apis/http-for-apis.md) · [`curl-postman`](../../06-rest-apis/curl-postman.md)
- Java: [`07-concurrency`](../../01-java/07-concurrency.md) · [`08-maven-build`](../../01-java/08-maven-build.md) · [`09-debugging-java`](../../01-java/09-debugging-java.md)
- Testing: [`junit5`](../../09-testing/junit5.md) · [`mockito`](../../09-testing/mockito.md) · [`spring-testing`](../../09-testing/spring-testing.md) · [`testcontainers`](../../09-testing/testcontainers.md) · [`frontend-testing`](../../09-testing/frontend-testing.md)
- Frontend: [`03-typescript`](../../07-javascript-typescript/03-typescript.md) · React [`01-fundamentals`](../../08-react/01-fundamentals.md) · [`02-hooks`](../../08-react/02-hooks.md) · [`03-forms-routing`](../../08-react/03-forms-routing.md) · [`04-api-integration-auth`](../../08-react/04-api-integration-auth.md) · [`05-architecture-testing`](../../08-react/05-architecture-testing.md)
- Docker: [`dockerfiles`](../../11-docker/dockerfiles.md) · [`compose`](../../11-docker/compose.md)
- AWS: [`iam`](../../12-aws/iam.md) · [`ec2`](../../12-aws/ec2.md) · [`rds`](../../12-aws/rds.md) · [`s3`](../../12-aws/s3.md) · [`cloudwatch`](../../12-aws/cloudwatch.md) · [`deploy-walkthrough`](../../12-aws/deploy-walkthrough.md) · [`cost-safety`](../../12-aws/cost-safety.md)
- CI/CD: [`github-actions`](../../13-cicd/github-actions.md) · [`pipeline-examples`](../../13-cicd/pipeline-examples.md)
- Git: [`workflows`](../../02-git/workflows.md)
- Python (for `tools/`): [`01-python-core`](../../19-python/01-python-core.md) · [`04-testing-and-scripting`](../../19-python/04-testing-and-scripting.md) · [`python-for-java-devs`](../../19-python/python-for-java-devs.md) · defence: [`../../17-resume-tech-defense/python.md`](../../17-resume-tech-defense/python.md)
- CS / design: [`concurrency`](../../14-cs-fundamentals/concurrency.md) · [`database-internals`](../../14-cs-fundamentals/database-internals.md) · [`caching`](../../15-system-design/caching.md) · [`scalability`](../../15-system-design/scalability.md)
- Interview: [`project-deep-dive`](../../16-interview-prep/project-deep-dive.md) · [`../../RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md)
- Weeks: [`week-04`](../../weeks/week-04/README.md) · [`week-05`](../../weeks/week-05/README.md) · [`week-06`](../../weeks/week-06/README.md) · [`week-07`](../../weeks/week-07/README.md) · [`week-08`](../../weeks/week-08/README.md)
