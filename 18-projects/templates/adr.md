# ADR Template (Architecture Decision Record)

> Copy to `docs/adr/NNNN-kebab-case-title.md` in your project repo (e.g. `0002-optimistic-locking-for-seats.md`).
> One decision per file. Never edit an accepted ADR's decision — write a new ADR that **supersedes** it.
> Minimum 3 ADRs per project. See [`../README.md`](../README.md#adrs) for example topics.

---

# ADR-NNNN: <Short decision title in imperative form>

| Field | Value |
|---|---|
| Status | Proposed / Accepted / Deprecated / Superseded by ADR-NNNN |
| Date | YYYY-MM-DD |
| Milestone | M? |
| Deciders | <you> |

## Context

*What forces are at play? What problem are we solving right now? Include constraints (time, skill, cost, scale).
Facts only — no decision yet. 3–8 sentences.*

## Options considered

### Option A — <name>
- Pros:
- Cons:

### Option B — <name>
- Pros:
- Cons:

### Option C — <name> (optional)
- Pros:
- Cons:

## Decision

*"We will …" — one or two sentences, unambiguous.*

## Consequences

**Positive**
-

**Negative / accepted trade-offs**
-

**Follow-ups**
- [ ] *e.g. add test `concurrentHold_exactlyOneSucceeds`*

## How we'll know it was wrong

*The observable signal that should make us revisit this (e.g. "409 rate > 5% under k6 load test").*

---

## Worked example (delete in your copy)

# ADR-0002: Use optimistic locking (`@Version`) on `Seat` to prevent double holds

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-04-02 |
| Milestone | M4 |

**Context.** Two customers can send `POST /holds` for the same seat within milliseconds. Without
coordination both transactions read `status = AVAILABLE` and both write `HELD` (lost update). Contention per
seat is low (most seats are requested by one user at a time), and holds are short.

**Options.** (A) Optimistic locking with a `version` column — no DB locks held, loser gets
`ObjectOptimisticLockingFailureException`. (B) Pessimistic `SELECT … FOR UPDATE` via
`@Lock(PESSIMISTIC_WRITE)` — serializes access, loser waits, risk of lock waits under load.
(C) Unique partial index on active holds — strong DB guarantee but expiry makes "active" time-dependent.

**Decision.** We will add `@Version` to `Seat` and map the optimistic-lock exception to `409 Conflict`.

**Consequences.** + No blocking, simple. − Losers must retry or pick another seat; hot seats produce many 409s.
Follow-up: concurrency test with 10 threads → exactly one 201.

**Revisit if** the 409 rate for a single popular event exceeds ~20% in load tests; then consider pessimistic locking or a queue.
