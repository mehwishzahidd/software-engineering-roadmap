# 04 · Schema Design — Keys, Relationships, Constraints, Normalization

> **Week 4** (§1–4, before FlowGrid M1's first Flyway migration) and **Week 6** (normalization, denormalization, conventions).
> **Week 9** revisits constraints and triggers for LedgerX's append-only ledger. Examples use the neutral TixHub ticketing
> domain; §7 turns the ideas into design prompts for your projects.

A good schema makes wrong data **impossible to store**. Application code has bugs; constraints don't take days off.

## 1. Design process (use it for every project)

1. List the **nouns** in the requirements → candidate tables (TixHub: Venue, Event, Seat, Booking, User; FlowGrid: Warehouse, Product, SKU, InventoryLevel, …).
2. For each pair, ask the **cardinality** questions: "Can one X have many Y? Can one Y have many X?"
3. Choose **primary keys**; add **foreign keys** for every relationship.
4. Write down every **business rule** and turn it into a constraint (`NOT NULL`, `UNIQUE`, `CHECK`, FK action).
5. Normalize to 3NF; denormalize only with a measured reason.
6. Write the DDL as a migration (Flyway `V1__init.sql`, from FlowGrid M1 on).
7. Seed realistic data and write the queries the app will run. Add indexes for them ([`05-indexes-performance.md`](./05-indexes-performance.md)).

## 2. Keys

| | Surrogate (`bigint identity`, `uuid`) | Natural (email, ISBN, country code) |
|---|---|---|
| Stability | never changes | can change (people change email) |
| Size / join cost | small, fast | often wide text |
| Meaning | none | meaningful, human-readable |
| Recommendation | **use as PK** | keep, but as a `UNIQUE` constraint |

- `bigint GENERATED ALWAYS AS IDENTITY`: compact, ordered (good B-tree locality), but guessable → don't expose in URLs if enumeration matters.
- `uuid` (`gen_random_uuid()`): safe to expose and generate client-side; larger and random (index page splits). UUIDv7 (time-ordered) fixes locality; native generation arrives in PG 18, libraries generate it today.
- **Composite keys** are natural for junction tables: `PRIMARY KEY (order_id, event_id)`.

## 3. Relationships

### One-to-many (1:N) — FK on the "many" side

```sql
CREATE SCHEMA IF NOT EXISTS design_demo;
SET search_path = design_demo;

CREATE TABLE venue (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name      text    NOT NULL,
    city      text    NOT NULL,
    capacity  integer NOT NULL CHECK (capacity > 0)
);

CREATE TABLE event (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id   bigint      NOT NULL REFERENCES venue (id) ON DELETE RESTRICT,
    title      text        NOT NULL,
    starts_at  timestamptz NOT NULL,
    ends_at    timestamptz NOT NULL,
    CONSTRAINT event_time_ck CHECK (ends_at > starts_at)
);
CREATE INDEX event_venue_idx ON event (venue_id);   -- Postgres does NOT index FKs for you
```

### One-to-one (1:1) — FK that is also UNIQUE (or shared PK)

```sql
CREATE TABLE app_user (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         text NOT NULL,
    password_hash text NOT NULL
);
CREATE UNIQUE INDEX app_user_email_uq ON app_user (lower(email));

CREATE TABLE user_profile (
    user_id      bigint PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,  -- shared PK = 1:1
    display_name text NOT NULL,
    avatar_url   text
);
```

Why split 1:1? Optional data, different access patterns (hot `app_user` rows stay narrow), different security (password hash separate from public profile).

### Many-to-many (M:N) — junction table

```sql
CREATE TABLE seat (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id  bigint NOT NULL REFERENCES venue (id) ON DELETE CASCADE,
    section   text   NOT NULL,
    row_label text   NOT NULL,
    number    integer NOT NULL CHECK (number > 0),
    CONSTRAINT seat_position_uq UNIQUE (venue_id, section, row_label, number)
);

-- a booking reserves a specific seat for a specific event: event <-> seat is M:N
CREATE TABLE booking (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id    bigint      NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    seat_id     bigint      NOT NULL REFERENCES seat (id)  ON DELETE RESTRICT,
    user_id     bigint      NOT NULL REFERENCES app_user (id),
    price       numeric(10,2) NOT NULL CHECK (price >= 0),
    status      text        NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at  timestamptz NOT NULL DEFAULT now()
);

-- The core business rule of a ticketing system: a seat can be sold at most once per event.
-- Partial unique index: cancelled bookings don't block re-selling the seat.
CREATE UNIQUE INDEX booking_one_active_per_seat
    ON booking (event_id, seat_id)
    WHERE status = 'CONFIRMED';
```

That last index is the **database-level guarantee against double-booking**. Even if two app instances race past every Java check, the second `INSERT` fails with a unique violation (SQLSTATE `23505`). FlowGrid M2 uses the same idea: a constraint as the last line of defence behind the application's locking (a `CHECK` on inventory quantities, a unique idempotency key), proven by a concurrency test.

Junction tables often carry their own data (price, quantity, role). FlagForge's organization membership (user, organization, role) is exactly this, and so is a FlowGrid order line (order, SKU, quantity).

## 4. Constraints cheat-sheet

| Constraint | Protects against | Example |
|---|---|---|
| `NOT NULL` | missing required data | `email text NOT NULL` |
| `UNIQUE` | duplicates | `UNIQUE (venue_id, section, row_label, number)` |
| `PRIMARY KEY` | = `NOT NULL` + `UNIQUE`, one per table | |
| `CHECK` | invalid values / cross-column rules | `CHECK (ends_at > starts_at)` |
| `FOREIGN KEY` | orphans / dangling references | `REFERENCES venue (id)` |
| `DEFAULT` | forgotten values | `created_at timestamptz NOT NULL DEFAULT now()` |
| `EXCLUDE` (awareness) | overlapping ranges | no two holds on the same seat with overlapping time windows (`btree_gist`) |

### `ON DELETE` actions

| Action | When the parent is deleted… | Use for |
|---|---|---|
| `NO ACTION` / `RESTRICT` (default-ish) | error if children exist | money, history: orders, bookings, payments |
| `CASCADE` | children are deleted too | true ownership: order → order_items, user → profile |
| `SET NULL` | child FK becomes NULL | optional links: employee → manager |
| `SET DEFAULT` | child FK becomes its default | rare |

`NO ACTION` checks at end of statement (deferrable); `RESTRICT` checks immediately. **Cascades on financial data are a bug waiting to happen** — prefer soft-delete (`deleted_at timestamptz`) or status columns.

### Name your constraints

`CONSTRAINT event_time_ck CHECK (…)` → errors say `violates check constraint "event_time_ck"`, and Spring's exception translator / your `@ControllerAdvice` can map a known constraint name to a friendly 409 message.

### Enum options

| Option | Pros | Cons |
|---|---|---|
| `text` + `CHECK (status IN (…))` | simple, easy to change in a migration | values repeated per table |
| Postgres `CREATE TYPE … AS ENUM` | compact, typed | adding is easy, removing/renaming painful |
| Lookup table + FK | values are data, can carry metadata | extra join |

This roadmap uses `text + CHECK` and maps to Java `enum` with `@Enumerated(EnumType.STRING)` (never `ORDINAL` — reordering the Java enum corrupts data).

## 5. Normalization

Goal: each fact stored **once**, so it can't disagree with itself (update/insert/delete anomalies).

Start with one wide "spreadsheet" table:

| order_id | customer_email | customer_city | event_titles | venue_name | venue_city | qty |
|---|---|---|---|---|---|---|
| 3 | ava@example.com | Toronto | "Arena Rock Live, Late Laughs" | "Riverside Arena, Blue Note Club" | … | "2,1" |

**1NF — atomic values, no repeating groups.** `event_titles` holds a list → split into one row per (order, event). Also: each row identifiable by a key.

| order_id | event_title | customer_email | customer_city | venue_name | venue_city | qty |
|---|---|---|---|---|---|---|

Key is now `(order_id, event_title)`.

**2NF — 1NF + no partial dependency** (every non-key column depends on the *whole* composite key). `customer_email` depends only on `order_id`; `venue_name` only on `event_title` → move them out: `orders(order_id, customer_email, …)`, `events(event_title, venue_name, …)`, `order_items(order_id, event_id, qty)`.

**3NF — 2NF + no transitive dependency** (non-key → non-key). In `orders`, `customer_city` depends on `customer_email`, not on `order_id` → move to `customers`. In `events`, `venue_city` depends on `venue_name` → move to `venues`.

Result: exactly the `customers / orders / order_items / events / venues` tables of [`practice-schema.sql`](./practice-schema.sql).

Memory aid: *"The key, the whole key, and nothing but the key."* BCNF tightens 3NF for tables with overlapping candidate keys — know the name, rarely needed for interviews.

### Anomalies normalization prevents

- **Update anomaly:** venue renamed → must update 5,000 rows; miss one and the data disagrees.
- **Insert anomaly:** can't add a venue until someone buys a ticket there.
- **Delete anomaly:** deleting the last order for an event deletes the only record of the event.

### When to denormalize (deliberately)

| Reason | Example here |
|---|---|
| **Historical truth** | `order_items.unit_price` copies the price at purchase time — the event price may change later; the receipt must not. This isn't redundancy, it's a different fact. |
| Read performance on hot paths, measured | `events.tickets_sold` counter maintained in the same transaction as bookings, instead of `COUNT(*)` on every page view |
| Reporting | materialized views / summary tables (`monthly_revenue`) refreshed by a job |
| Caching outside the DB | Redis catalog cache in FlowGrid M3, config snapshots in FlagForge M2 ([`redis.md`](./redis.md)) |

Every denormalization needs an answer to: *who keeps the copy in sync, and what happens when it drifts?*

## 6. Conventions used in this roadmap

- Table names: `snake_case`, plural (`orders`) *or* singular — pick one per project and never mix. Columns `snake_case`. FK columns `<table_singular>_id`.
- Every table: `id` PK, `created_at timestamptz NOT NULL DEFAULT now()`; mutable tables also `updated_at`, and `version bigint` when JPA optimistic locking is used (FlowGrid M2 compares it with `FOR UPDATE`).
- Money `numeric(12,2)`, time `timestamptz`, text `text`.
- Index every FK column you join or filter on.
- Schema changes only via migrations (Flyway from FlowGrid M1 on; see [`../05-spring-boot/03-data-jpa.md`](../05-spring-boot/03-data-jpa.md)).

## 7. Append-only tables enforced by the database (LedgerX M1 concept)

Some tables must never change after insert: audit events (FlowGrid M1), ledger entries (LedgerX M1), config versions (FlagForge M1).
"The app never updates them" is a convention. **The database refusing** is a guarantee. Two layers:

1. **Privileges:** the application's DB role gets `INSERT, SELECT` on the table and no `UPDATE`/`DELETE`
   (`REVOKE UPDATE, DELETE ON audit_event FROM app_user_role;`). Migrations run as a different role.
2. **A trigger** that rejects modification even for roles that do have the privilege:

```sql
CREATE TABLE audit_event (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type text        NOT NULL,
    entity_id   bigint      NOT NULL,
    action      text        NOT NULL,
    payload     jsonb       NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION reject_modification() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION '% on % is not allowed: table is append-only', TG_OP, TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER audit_event_append_only
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_modification();

INSERT INTO audit_event (entity_type, entity_id, action, payload)
VALUES ('sku', 1, 'CREATED', '{"code": "SKU-1"}');
-- UPDATE audit_event SET action = 'X';   -> ERROR: UPDATE on audit_event is not allowed: table is append-only
```

`TRUNCATE` doesn't fire row triggers. Add a `BEFORE TRUNCATE … FOR EACH STATEMENT` trigger, or rely on privileges.
Corrections to append-only data are **new rows** that reference the original: a compensating ledger entry, a new config
version that copies an old one. They are never edits. That's the core idea behind LedgerX reversals and FlagForge rollbacks.

### Apply it: design prompts (do these on paper before writing the migration)

These are questions to answer in your design doc ([`../18-projects/templates/design-doc.md`](../18-projects/templates/design-doc.md)). They aren't answers.

- **FlowGrid M1:** Is `inventory_level` one row per `(warehouse, SKU)`? Which UNIQUE constraint says so? Which quantity
  columns need `CHECK (… >= 0)`? Can a CHECK express "reserved + allocated ≤ on_hand"? Which FKs are `RESTRICT` (a SKU with stock)?
  Which could be `CASCADE`? Is `available` a stored column or derived, and who keeps it in sync?
- **FlowGrid M2:** What makes an idempotency key unique: the key alone, or `(key, user)`? What do you store so a
  replay returns the same response?
- **LedgerX M1:** How does the schema make "every journal transaction sums to zero" checkable? (A CHECK is per-row, so it
  can't express a cross-row rule. Think about deferred constraint triggers, or verifying in the same transaction plus a
  reconciliation query.) Should money be `numeric(19,4)` or integer minor units?
- **FlagForge M1:** How do you model "the current version" of an environment's config without ever updating old versions?

```sql
-- clean up the demo schema when you're done with §8
RESET search_path;
DROP SCHEMA design_demo CASCADE;
```

## 8. 🔨 Break it

(Run the §3 and §7 DDL first, in the `design_demo` schema. Run the cleanup block at the end of §7 only when you're done.)

1. Insert two `CONFIRMED` bookings for the same `(event_id, seat_id)` — read the error and its SQLSTATE (`\set VERBOSITY verbose`). Then cancel the first and insert again — why does it succeed now?
2. `DELETE FROM venue WHERE id = 1;` with events attached → which constraint fires? Change it to `ON DELETE CASCADE` in your head: what else would silently disappear?
3. Insert an event with `ends_at < starts_at`.
4. Insert `Ava@Example.com` and `ava@example.com` into `app_user`. Now drop the `lower()` index and use plain `UNIQUE (email)` — try again.
5. Try `UPDATE audit_event …`, `DELETE FROM audit_event`, then `TRUNCATE audit_event`. Which one gets through, and why?
6. Write the one wide spreadsheet table for FlowGrid orders (order + customer + each line's SKU code, product name, warehouse name and region as comma lists). Normalize it to 3NF on paper.

## 9. 🐞 Debugging tips

- `\d tablename` shows every constraint and index — read it before writing a migration.
- SQLSTATE codes worth knowing: `23505` unique_violation, `23503` foreign_key_violation, `23502` not_null_violation, `23514` check_violation, `40001` serialization_failure, `40P01` deadlock_detected. Map them in your Spring error handler.
- Adding `NOT NULL`/`UNIQUE` to an existing table fails if existing data violates it → find violators with a `GROUP BY … HAVING` or `WHERE col IS NULL` query first.

## 10. 🎤 Interview Q&A

<details><summary>How do you model a many-to-many relationship?</summary>

A junction table with FKs to both sides, usually with a composite PK or unique constraint on the pair (e.g., `order_items(order_id, event_id)`), plus any attributes of the relationship itself (quantity, price, role).
</details>

<details><summary>Explain 1NF, 2NF, 3NF.</summary>

1NF: atomic values, no repeating groups, rows identified by a key. 2NF: no non-key column depends on only part of a composite key. 3NF: no non-key column depends on another non-key column (no transitive dependencies). "Every non-key attribute depends on the key, the whole key, and nothing but the key."
</details>

<details><summary>When would you denormalize?</summary>

When a measured read path is too slow and the cost of keeping a copy in sync is acceptable — counters, summary tables, materialized views, caches — or when the "copy" is actually a separate fact such as the price at time of purchase. Always define how the copy is kept consistent.
</details>

<details><summary>Surrogate vs natural key?</summary>

Surrogate keys are stable, compact, meaningless ids; natural keys carry meaning but can change and are often wide. Use a surrogate PK and enforce the natural key with UNIQUE.
</details>

<details><summary>What does ON DELETE CASCADE do and when is it dangerous?</summary>

Deleting the parent deletes dependent children automatically. Great for owned data (order → order_items); dangerous for independent or financial records, where one delete can wipe history. Prefer RESTRICT + soft delete there.
</details>

<details><summary>How do you prevent double-booking at the database level?</summary>

A unique constraint/index on `(event_id, seat_id)` (partial, for active bookings only). The DB rejects the second insert atomically regardless of application races. Optionally add optimistic/pessimistic locking for friendlier behaviour.
</details>

<details><summary>Does Postgres automatically index foreign keys?</summary>

No. It indexes PRIMARY KEY and UNIQUE constraints only. Index FK columns used in joins/filters, and to avoid slow parent deletes (which must check children). MySQL/InnoDB does create an index for FKs automatically.
</details>

## ✅ Mastery checklist

- [ ] FlowGrid's V1 migration has named constraints, justified `ON DELETE` actions and indexed FKs
- [ ] Can normalize a wide table to 3NF on a whiteboard and name each anomaly
- [ ] Can justify each `ON DELETE` choice in my schemas
- [ ] Wrote the partial unique index that prevents double-booking (TixHub) and triggered its violation
- [ ] Built the append-only trigger and answered the §7 design prompts in my design docs (FlowGrid W4, LedgerX W9)
