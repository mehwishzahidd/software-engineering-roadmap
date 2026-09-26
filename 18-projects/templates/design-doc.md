# Design Doc Template

> Copy to `docs/design.md` in your project repo. Time-box: P1 ≈ 1 h, P2 ≈ 2 h, P3 ≈ 3 h, P4 ≈ 4 h.
> Delete guidance lines (in *italics*) once filled. Keep it updated; add to "Changes since v0" instead of silently rewriting.
> How this fits the project loop: [`../README.md`](../README.md#2-design-doc-first).

---

# <Project name> — Design Doc

| Field | Value |
|---|---|
| Author | <you> |
| Status | Draft / In review / Accepted / Superseded |
| Created | YYYY-MM-DD |
| Last updated | YYYY-MM-DD |
| Spec | link to the spec in the roadmap repo |

## 1. Problem

*2–4 sentences. What problem, for whom, why it's non-trivial. No solution yet.*

## 2. Goals and non-goals

**Goals** (measurable)

- *e.g. Two concurrent holds on the same seat never both succeed.*
- *e.g. p95 latency of `GET /status/{slug}` < 50 ms with warm cache on a t3.small.*

**Non-goals** (explicitly out of scope)

- *e.g. Payments. Multi-region. Mobile app.*

## 3. Users and use cases

| Actor | Use case | Priority |
|---|---|---|
| *CUSTOMER* | *Hold a seat, then confirm booking* | *Must* |

## 4. Architecture overview

*ASCII diagram of components and arrows (who calls whom, sync vs async).*

```
[Client] --HTTP--> [API] --JDBC--> [PostgreSQL]
```

*3–6 sentences: responsibilities of each component, where state lives.*

## 5. Data model

*ERD in text or DDL. Keys, constraints, indexes, and why each exists.*

```sql
CREATE TABLE example (
  id          BIGSERIAL PRIMARY KEY,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

| Table | Invariant enforced by DB | Index | Why |
|---|---|---|---|

## 6. Interface (API / CLI)

| Method | Path / command | Auth | Request | Success | Errors |
|---|---|---|---|---|---|
| *POST* | */api/v1/holds* | *CUSTOMER* | *`{seatId}`* | *201* | *400, 401, 404, 409* |

*Error format (e.g. RFC 7807 ProblemDetail). Pagination format. Versioning.*

## 7. Key flows

*Numbered steps or text sequence diagrams for the 2–4 most important flows, including failure paths.*

```
Client -> API: POST /holds {seatId}
API -> DB: BEGIN; SELECT seat ... ; INSERT hold ...; COMMIT
DB --> API: OptimisticLockException (another tx won)
API --> Client: 409 Conflict (ProblemDetail)
```

## 8. Concurrency, consistency and failure

*What can run at the same time? What happens if the DB/Redis/network fails mid-operation? Retries? Idempotency?*

## 9. Security

*AuthN, AuthZ model, secret handling, input validation, threats you considered (OWASP top items relevant here).*

## 10. Observability

*Logs (format, correlation ID), metrics, health checks, alarms.*

## 11. Testing strategy

| Level | Tooling | What it covers |
|---|---|---|
| Unit | JUnit 5, Mockito | |
| Slice/Integration | @WebMvcTest, @DataJpaTest, Testcontainers | |
| End-to-end / manual | curl script, Postman, UI | |

*List the must-exist test cases from the spec by name.*

## 12. Deployment

*How it runs locally, how it is packaged, where it is deployed, config/secrets per environment.*

## 13. Milestones

| Milestone | Week | Scope | Exit criteria |
|---|---|---|---|

## 14. Alternatives considered

| Option | Pros | Cons | Decision (ADR link) |
|---|---|---|---|

## 15. Risks and open questions

- [ ] *Open question → resolved by ADR-000N*

## 16. Changes since v0

| Date | Change | Reason |
|---|---|---|
