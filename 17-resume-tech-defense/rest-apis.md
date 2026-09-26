# REST APIs — Résumé Defense

**Target level:** L4 · **Learned:** Week 9 (HTTP + REST), Week 10 (validation, errors, pagination), Week 12 (idempotency), Week 20 (rate limiting)
Method: [`../RESUME_TECH_DEFENSE.md`](../RESUME_TECH_DEFENSE.md) · Index: [`README.md`](./README.md)

---

## 1. Beginner questions

<details><summary><b>Q1. What is REST?</b></summary>

An architectural style for networked APIs: resources identified by URIs, manipulated through a
uniform interface (HTTP methods), stateless requests, representations (usually JSON), cacheable
responses, layered system. HATEOAS is part of the original style but rarely fully implemented.
</details>

<details><summary><b>Q2. HTTP methods and their properties?</b></summary>

| Method | Use | Safe | Idempotent |
|---|---|---|---|
| GET | Read | ✅ | ✅ |
| POST | Create / action | ❌ | ❌ |
| PUT | Replace whole resource | ❌ | ✅ |
| PATCH | Partial update | ❌ | ❌ (not guaranteed) |
| DELETE | Remove | ❌ | ✅ |
</details>

<details><summary><b>Q3. Key status codes?</b></summary>

200 OK, 201 Created (+ `Location`), 204 No Content, 400 Bad Request, 401 Unauthorized (not
authenticated), 403 Forbidden (authenticated, not allowed), 404 Not Found, 409 Conflict, 422
Unprocessable Content, 429 Too Many Requests, 500 Internal Server Error, 503 Service Unavailable.
</details>

<details><summary><b>Q4. What does stateless mean?</b></summary>

Each request carries everything needed to process it (e.g. a JWT); the server keeps no
per-client session between requests. Enables horizontal scaling behind a load balancer.
</details>

<details><summary><b>Q5. Path vs query parameters?</b></summary>

Path identifies a resource (`/events/42`); query filters/sorts/paginates a collection
(`/events?venueId=3&page=0&size=20&sort=startsAt,asc`).
</details>

## 2. Intermediate questions

<details><summary><b>Q6. Design good resource URIs.</b></summary>

Plural nouns, hierarchy for ownership, no verbs: `GET /orgs/{orgId}/projects/{projectId}/issues`.
Actions that don't fit CRUD become sub-resources: `POST /holds/{id}/confirmation` → creates a booking.
Keep nesting ≤ 2 levels.
</details>

<details><summary><b>Q7. How do you return errors consistently?</b></summary>

RFC 7807/9457 Problem Details, `Content-Type: application/problem+json`:
```json
{"type":"https://tickethold.example/problems/seat-unavailable","title":"Seat unavailable",
 "status":409,"detail":"Seat A12 is held by another customer","instance":"/api/holds"}
```
Add an `errors` array for field validation. Never leak stack traces.
</details>

<details><summary><b>Q8. Offset vs cursor (keyset) pagination?</b></summary>

Offset (`page`, `size` → `LIMIT/OFFSET`): simple, random page access, but deep pages are slow
(DB scans skipped rows) and results shift when rows are inserted. Cursor (`?after=<lastId>` →
`WHERE (created_at, id) < (?, ?) ORDER BY ... LIMIT n`): stable and fast with an index, no page jumping.
</details>

<details><summary><b>Q9. What is idempotency and how do you make POST idempotent?</b></summary>

Repeating the request has the same effect as doing it once. For POST: client sends
`Idempotency-Key: <uuid>`; server stores key + request hash + response in a table with a unique
constraint; a retry with the same key returns the stored response; same key with a different body → 422/409.
</details>

<details><summary><b>Q10. How do you version an API?</b></summary>

URI (`/api/v1/...`, most common, explicit), header (`Accept: application/vnd.x.v2+json`), or query
param. Prefer additive, backward-compatible changes (new optional fields) to avoid versions at all.
</details>

<details><summary><b>Q11. What is CORS?</b></summary>

A browser mechanism: a page from origin A calling origin B needs B to allow it via
`Access-Control-Allow-Origin` etc. Non-simple requests trigger an `OPTIONS` preflight. It's
enforced by browsers, not servers — curl ignores it. Not a security boundary for your API; auth is.
</details>

<details><summary><b>Q12. How do caching headers work for APIs?</b></summary>

`Cache-Control: max-age=30, public` lets clients/CDNs reuse responses; `ETag` + `If-None-Match` →
`304 Not Modified`; `ETag` + `If-Match` enables optimistic concurrency on updates (412 on mismatch).
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "Design an API for booking seats at an event."</b></summary>

- Resources: `events`, `seats`, `holds`, `bookings`. `GET /events/{id}/seats?status=AVAILABLE`,
  `POST /holds {seatIds}` → 201 with `expiresAt`, `POST /bookings {holdId}` + `Idempotency-Key` → 201,
  `DELETE /holds/{id}` → 204.
- Conflicts → 409 ProblemDetail; expired hold → 410 or 409; validation → 400.
- Concurrency: optimistic locking; hold expiry job. This *is* TicketHold — show it.
</details>

<details><summary><b>R2. "You mentioned REST APIs on your résumé — what did you build or consume?"</b></summary>

- Truthful past: which APIs, consumed vs built, your part.
- Current: TicketHold/TeamBoard APIs designed from scratch; Postman collection; PulseWatch public status API with rate limiting.
</details>

<details><summary><b>R3. "401 vs 403?"</b></summary>

- 401: no/invalid credentials — "who are you?" (should include `WWW-Authenticate`). 403: known user lacks permission.
- TeamBoard example: VIEWER trying to create an issue → 403; expired JWT → 401.
- Some APIs return 404 instead of 403 to avoid revealing a resource exists.
</details>

<details><summary><b>R4. "PUT vs PATCH? When would you use each?"</b></summary>

- PUT replaces the full representation (idempotent); missing fields reset. PATCH partial update (JSON Merge Patch or JSON Patch).
- TeamBoard: `PATCH /issues/{id}` for status changes; validates allowed transition TODO→IN_PROGRESS etc.
</details>

<details><summary><b>R5. "How would you protect a public API from abuse?"</b></summary>

- Rate limiting (token bucket per API key/IP in Redis, return 429 + `Retry-After`), auth, input validation,
  payload size limits, pagination caps, timeouts. PulseWatch M2 is the evidence.
</details>

<details><summary><b>R6. "REST vs GraphQL vs gRPC?"</b></summary>

- REST: simple, cacheable, universal. GraphQL: client picks fields, one endpoint, good for varied UIs, harder caching/rate-limiting.
  gRPC: binary, HTTP/2, streaming, strong contracts — great service-to-service, poor for browsers directly.
</details>

## 4. Practical tasks (live)

- [ ] Sketch endpoints + status codes for a TeamBoard feature (comments) in 10 minutes.
- [ ] With curl: `POST` JSON with a bearer token, show headers with `-i`/`-v`, follow `Location`.
- [ ] Implement a paginated `GET` returning `{content, page, size, totalElements}` with max `size` of 100.
- [ ] Return a 400 ProblemDetail listing field errors for an invalid body.
- [ ] Implement an Idempotency-Key filter/service with a unique DB constraint.

## 5. Debugging questions

<details><summary><b>D1. The React app gets "blocked by CORS policy", but curl works.</b></summary>

Browser preflight failing: API doesn't allow the origin/method/headers (e.g. `Authorization`).
Check the `OPTIONS` response in DevTools Network tab. Configure CORS in Spring Security (`http.cors(...)`
+ `CorsConfigurationSource`) — Spring MVC CORS alone is bypassed if security rejects the preflight first.
</details>

<details><summary><b>D2. Client says "the API returns 415".</b></summary>

Unsupported Media Type: missing/wrong `Content-Type: application/json`. 406 would be an `Accept` mismatch.
</details>

<details><summary><b>D3. Duplicate bookings appear when mobile network is flaky.</b></summary>

Client retries a non-idempotent POST after a timeout even though the first succeeded. Add
Idempotency-Key handling + unique constraint on the business key (seat per event).
</details>

<details><summary><b>D4. Endpoint returns 200 with an error message in the body.</b></summary>

Design bug: clients/monitoring can't detect failure. Map exceptions to correct 4xx/5xx via global handler.
</details>

<details><summary><b>D5. Listing endpoint times out on page 5,000.</b></summary>

Deep `OFFSET` scans. Switch to keyset pagination with an index on the sort columns; cap page size.
</details>

## 6. Architecture questions

- How do you evolve an API used by a React app you control vs third-party clients?
- Where does validation belong (DTO Bean Validation vs domain invariants vs DB constraints)? All three — why?
- Synchronous request vs `202 Accepted` + job resource for PulseWatch CSV exports to S3?
- How would you expose PulseWatch's public status page for 10k req/s (caching headers, Redis cache, CDN)?
- Webhooks for alerts: signing, retries with backoff, idempotency on the receiver.

## 7. Common mistakes

- Verbs in URIs (`/createEvent`), inconsistent plurals.
- 200 for everything; 500 for validation errors.
- Exposing database IDs/entities and internal fields directly.
- Unbounded list endpoints.
- Treating CORS as security.
- Non-idempotent retries on POST without keys.

## 8. Terminology I must know

| Term | One-line meaning |
|---|---|
| Resource | Named thing addressable by URI |
| Representation | Format of a resource sent over the wire (JSON) |
| Safe method | No server-side state change (GET, HEAD) |
| Idempotent | Repeating has the same effect as once |
| Content negotiation | Choosing format via `Accept`/`Content-Type` |
| ProblemDetail | Standard JSON error format (RFC 7807/9457) |
| Preflight | Browser `OPTIONS` check for CORS |
| ETag | Version identifier for caching/concurrency |
| Keyset pagination | Page by last-seen key, not offset |
| Rate limiting | Capping requests per client per time |
| HATEOAS | Responses include links to next actions |
| OpenAPI | Machine-readable API specification |

## 9. When to use it

Public or cross-team APIs, CRUD-heavy resources, browser/mobile clients, when HTTP caching and tooling (curl, Postman, OpenAPI) matter.

## 10. When NOT to use it

High-throughput internal RPC with strict contracts (gRPC), highly varied client data needs (GraphQL),
real-time push (WebSockets/SSE), event-driven workflows (message queues).

## 11. Trade-offs

| Gain | Cost |
|---|---|
| Simple, universal, cacheable | Over/under-fetching |
| Stateless → easy scaling | Auth on every request; token revocation harder |
| Human-readable JSON | Larger payloads than binary |
| Loose coupling | No enforced schema unless you add OpenAPI |

## 12. How it interacts with the rest of my stack

- **Spring Boot**: `@RestController`, `ResponseEntity`, `@Valid`, `@RestControllerAdvice` + ProblemDetail, Spring Security for auth.
- **Java**: records as request/response DTOs, serialised by Jackson.
- **PostgreSQL**: pagination → `LIMIT/OFFSET` or keyset; conflicts from unique constraints → 409.
- **React**: typed API client (`fetch` wrapper) maps status codes to UI states.
- **Redis**: caching GET responses, rate-limit counters.
- **Docker/AWS**: API behind nginx/load balancer; health endpoint for checks.
- **CI**: `@WebMvcTest` tests pin status codes and error shapes.

## 13. Hands-on exercise

**Design and implement TeamBoard's comments API.**

Acceptance criteria:
- [ ] Written endpoint table (method, path, request, success code, error codes) reviewed before coding.
- [ ] `GET /issues/{id}/comments` paginated with max size; `POST` → 201 + `Location`; `DELETE` → 204.
- [ ] VIEWER cannot post (403); non-member gets 404; invalid body → 400 ProblemDetail with field errors.
- [ ] `@WebMvcTest` asserts every status code in the table.
- [ ] Postman/curl script demonstrates each case.

## 14. Mastery checklist

- [ ] Explain methods, safety, idempotency, and status codes without hesitation.
- [ ] Design resource URIs for a new domain in 10 minutes.
- [ ] Implement ProblemDetail errors and validation.
- [ ] Explain and implement offset and keyset pagination.
- [ ] Implement Idempotency-Key handling.
- [ ] Explain CORS and preflight; fix a CORS error.
- [ ] Explain rate limiting (token bucket) and 429.
- [ ] Compare REST, GraphQL, gRPC.

## Evidence in my projects

| Project | What it demonstrates | Fill in: file / commit |
|---|---|---|
| P2 TicketHold | Resource design (holds/bookings), ProblemDetail, validation, pagination, Idempotency-Key, 409 on conflicts, Postman collection | |
| P3 TeamBoard | Nested resources, PATCH status workflow, filters/search, 401 vs 403 by role, CORS with React | |
| P4 PulseWatch | Public status API with Redis cache + token-bucket rate limiting (429), webhook alerts with retry | |

## Where to learn it in this repo

[`../06-rest-apis/README.md`](../06-rest-apis/README.md) · [`../06-rest-apis/http-for-apis.md`](../06-rest-apis/http-for-apis.md) ·
[`../06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md) · [`../06-rest-apis/curl-postman.md`](../06-rest-apis/curl-postman.md) ·
[`../05-spring-boot/02-web-layer.md`](../05-spring-boot/02-web-layer.md) · [`../05-spring-boot/04-validation-errors.md`](../05-spring-boot/04-validation-errors.md) ·
[`../14-cs-fundamentals/networking.md`](../14-cs-fundamentals/networking.md)
