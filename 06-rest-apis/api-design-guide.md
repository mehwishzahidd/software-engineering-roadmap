# REST API Design Guide

> **Week 3** (naming, methods, status codes), **Week 4** (FlowGrid M1: pagination/sort/filter, errors, OpenAPI),
> **Week 5** (idempotency keys), **Week 11** (cursor pagination for LedgerX history), **Week 14** (receiving webhooks in ForgeCI),
> **Week 22** (SDK-facing API design in FlagForge), **Week 24** (security review against the OWASP API Top 10).
> These are **conventions to apply to your own APIs**. The projects' specs in [`../18-projects/`](../18-projects/) say *what*
> to expose; this guide says *how* to shape it.

Contents: [Resources & naming](#resources-and-naming) · [Actions & state transitions](#actions-and-state-transitions) ·
[Versioning](#versioning) · [Pagination](#pagination) · [Filtering & sorting](#filtering-and-sorting) · [Errors](#errors) ·
[Idempotency keys](#idempotency-keys) · [Rate limiting](#rate-limiting) · [Long-running work & webhooks](#long-running-work-and-webhooks) ·
[HATEOAS](#hateoas-awareness) · [OpenAPI / springdoc](#openapi-and-springdoc) · [Security](#security) ·
[REST vs GraphQL vs gRPC](#rest-vs-graphql-vs-grpc) · [Checklist](#design-review-checklist)

## Resources and naming

- URIs name **things** (nouns), and methods say what to do: `POST /orders`, not `POST /createOrder`.
- **Plural collections, ids for members:** `/warehouses`, `/warehouses/7`.
- Lowercase, hyphens for multi-word segments: `/stock-transfers`. JSON fields in camelCase (or snake_case, but pick one API-wide).
- **Nest only for real containment**, and at most one level: `/orders/881/lines` (lines don't exist without the order).
  For things with their own identity, use top-level resources with filters: `/shipments?orderId=881`, not `/customers/5/orders/881/shipments/3`.
- Base path + version: `/api/v1/...`.
- Don't leak implementation: no `/api/v1/getInventoryLevelEntities`, no table names, no `.json` suffixes.

| Operation | Request | Success |
|---|---|---|
| list | `GET /api/v1/skus?productId=12&page=0&size=20&sort=code,asc` | 200 + page |
| get | `GET /api/v1/skus/1042` | 200 / 404 |
| create | `POST /api/v1/skus` | 201 + `Location` |
| replace | `PUT /api/v1/skus/1042` | 200 / 204 |
| partial update | `PATCH /api/v1/skus/1042` (merge patch) | 200 |
| delete | `DELETE /api/v1/skus/1042` | 204 |

## Actions and state transitions

Real domains have verbs that aren't CRUD: cancel an order, confirm a pick list, reverse a transaction, publish a flag
config. Options:

| Style | Example | Use when |
|---|---|---|
| **Action sub-resource (POST)** | `POST /orders/881/cancel`, `POST /journal-transactions/55/reversal` | a transition with rules and side effects. Honest and explicit. **Recommended for state machines** |
| Create a resource that represents the action | `POST /stock-transfers`, `POST /refunds` | the action has its own lifecycle/identity |
| PATCH the status field | `PATCH /orders/881 {"status":"CANCELLED"}` | trivial transitions only. Hides business rules behind a field write |

Invalid transitions (shipping a cancelled order) → **409** with a ProblemDetail naming the current state and the attempted
transition. Reversals and refunds in LedgerX are **new resources** (compensating entries), never edits.

## Versioning

| Strategy | Example | Pros | Cons |
|---|---|---|---|
| **URI** | `/api/v1/orders` | obvious, cache-friendly, easy routing | "not pure REST"; version in every URL |
| Header | `API-Version: 2` | clean URLs | invisible in logs/links; easy to forget |
| Media type | `Accept: application/vnd.flowgrid.v2+json` | precise per representation | hardest for clients and tooling |

Use URI versioning for these projects. More important than the mechanism: **avoid breaking changes**.

- **Non-breaking (no new version):** adding optional request fields, adding response fields, adding endpoints, adding enum values *if clients were told to tolerate unknown ones*.
- **Breaking (new version):** removing or renaming fields, changing types or meaning, making optional fields required, changing status codes or error formats, tightening validation.
- Deprecate with notice: document it, add `Deprecation`/`Sunset` headers, and measure who still calls the old version.
- For FlagForge's SDK (Week 22), the API and SDK versions evolve independently. Semantic versioning for the SDK, a compatibility window for the API.

## Pagination

Never return unbounded lists. Cap `size` (e.g. max 100).

### Offset pagination

`GET /orders?page=3&size=20&sort=createdAt,desc` → SQL `ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 60`.

```json
{ "items": [ … ], "page": 3, "size": 20, "totalElements": 1234, "totalPages": 62 }
```

- ✅ Simple, jump to any page, total counts for UIs (FlowGrid's dashboard tables).
- ❌ Deep pages are slow (`OFFSET` reads and discards rows; see [`../04-sql-databases/sql-practice.md`](../04-sql-databases/sql-practice.md) S5). `COUNT(*)` on big tables is expensive. Rows **shift** when new ones are inserted, so a client paging through sees duplicates or skips.

### Cursor (keyset) pagination

`GET /accounts/42/entries?limit=50&cursor=eyJ0IjoiMjAyNi0wOS0yNlQxMDowMDowMFoiLCJpZCI6OTgxfQ`

```json
{ "items": [ … ], "nextCursor": "eyJ0IjoiMjAyNi0wOS0yNVQwODoxMjozMFoiLCJpZCI6OTMxfQ", "hasMore": true }
```

The cursor is an **opaque** encoding (base64 JSON) of the last row's sort key, including a unique tiebreaker:

```sql
SELECT id, created_at, amount
FROM ledger_entry
WHERE account_id = $1
  AND (created_at, id) < ($2, $3)          -- values decoded from the cursor
ORDER BY created_at DESC, id DESC
LIMIT 51;                                  -- one extra row tells you hasMore
```

- ✅ Constant cost at any depth (an index on `(account_id, created_at, id)` makes it a seek). Stable under inserts. Ideal for feeds, histories, logs: **LedgerX M3 history**, ForgeCI log chunks.
- ❌ No "jump to page 40", no cheap total. The sort must be on a unique (composite) key.
- Make cursors opaque and validate them. Clients must not construct them, so you can change the encoding later.

## Filtering and sorting

- Filters as query params, named after the **API fields**, not DB columns: `?status=RESERVED&warehouseId=3&createdFrom=2026-09-01T00:00:00Z&createdTo=2026-10-01T00:00:00Z`.
- Ranges with `From`/`To` (half-open: `[from, to)`), timestamps in ISO-8601 UTC.
- Multi-value: `?status=RESERVED&status=ALLOCATED` (Spring binds `List<Status>`).
- Search: `?q=widget` with clearly documented semantics (prefix? contains? case?).
- Sorting: `?sort=createdAt,desc&sort=id,desc`. **Whitelist** sortable fields and map them to columns. Unknown field → 400. Always add a unique tiebreaker so paging is deterministic.
- Every filter combination you expose is a query you must be able to index ([`../04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md)). Expose the filters users need, not every column.

## Errors

One format for every error: **RFC 9457 Problem Details** (`application/problem+json`). Implementation:
[`../05-spring-boot/04-validation-errors.md`](../05-spring-boot/04-validation-errors.md).

```json
{
  "type": "https://api.example.com/problems/insufficient-stock",
  "title": "Insufficient stock",
  "status": 409,
  "detail": "Requested 5 units of SKU-1042; 2 available across eligible warehouses.",
  "instance": "/api/v1/orders",
  "requestId": "6f1c2a9e-…",
  "sku": "SKU-1042",
  "requested": 5,
  "available": 2
}
```

- `type` is a stable identifier per error kind. Clients branch on `type` (or a `code` field), **never on `detail` text**.
- Validation errors carry a field list (`errors: [{field, message}]`).
- Include `requestId` for support. Never include stack traces, SQL or internal hostnames.
- Document every error `type` per endpoint in OpenAPI.

## Idempotency keys

**Problem:** a client POSTs an order, the network drops the response, the client retries → two orders. Or a transfer
executes twice → money is moved twice. **FlowGrid M2** (orders) and **LedgerX M2** (transfers) require the fix. You'll
implement it; here's the design contract.

**Protocol**

1. The client generates a unique key per *logical operation* (a UUID) and sends `Idempotency-Key: <key>` with the POST. It reuses **the same key** for retries of that operation.
2. The server stores `(key, scope, request fingerprint, status, response code, response body, created_at, expires_at)`.
   - `scope`: the authenticated user or client. Keys are unique **per caller**, not globally.
   - `request fingerprint`: a hash of the canonicalized body (+ method + path).
3. On each request:

| Stored state for (scope, key) | Same fingerprint? | Server does |
|---|---|---|
| none | — | insert the key row as `IN_PROGRESS` (unique constraint!), do the work, store the response, mark `COMPLETED` |
| `COMPLETED` | yes | **replay the stored response** (same status and body). Don't execute again |
| `COMPLETED` | no | **422** (or 409): "key reused with a different request" |
| `IN_PROGRESS` | — | **409** (+ `Retry-After`): the original is still running. Don't run it concurrently |

4. Keys expire after a TTL (e.g., 24 h), cleaned up by a scheduled job.

**Correctness details you'll be asked about**

- The **unique constraint** on `(scope, key)` is what makes two simultaneous first attempts safe: exactly one insert wins, and the other sees a conflict.
- Store the idempotency record and the business change in the **same database transaction**. Otherwise a crash between them leaves "order created, key not recorded" (duplicate on retry) or the reverse.
- Only replay **deterministic outcomes**. Decide whether to store 4xx results (usually yes: the same bad request gets the same answer) and 5xx results (usually no: let the client retry).
- Where does it live? Postgres (transactional with the work, the default here) or Redis (fast, TTL built in, but not in the same transaction; acceptable as a front cache in LedgerX if you can explain the failure modes).
- This is exactly what [`../04-sql-databases/sql-practice.md`](../04-sql-databases/sql-practice.md) Q31 finds in data that lacked it: a double charge.

## Rate limiting

Protects availability and fairness (OWASP API4: Unrestricted Resource Consumption).

- Respond **429 Too Many Requests** with `Retry-After: <seconds>` and a ProblemDetail.
- Tell well-behaved clients their budget. Common conventions: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (GitHub-style). The IETF *RateLimit header fields* draft standardizes `RateLimit-Policy` / `RateLimit`. Pick one and document it.
- Key the limit by API key, user or IP (IP alone is weak behind NAT/proxies).
- Algorithms: fixed window, sliding window, **token bucket**. Redis implementations are in [`../04-sql-databases/redis.md`](../04-sql-databases/redis.md#9-rate-limiting-).
- Different limits for expensive endpoints (login: strict, to slow credential stuffing; flag evaluation: generous but bounded).

## Long-running work and webhooks

**Async operations:** `POST /reports/exports` → **202 Accepted** + `Location: /reports/exports/19`. The client polls
`GET /reports/exports/19` (`status: PENDING | RUNNING | DONE | FAILED`, plus a download URL when done), or receives a
callback/stream. ForgeCI builds are the extreme version: queued, running, streaming logs (SSE), finished.

**Receiving webhooks (ForgeCI M1: GitHub):**

1. **Verify the signature** before trusting anything. Compute HMAC-SHA256 over the **raw request body** with the shared secret and compare it to `X-Hub-Signature-256` using a constant-time comparison. Reject with 401.
2. **Dedupe** on the delivery id (`X-GitHub-Delivery`) with a unique constraint. Senders retry, so duplicates are normal.
3. **Respond fast** (2xx within a few seconds). Enqueue the work and process it asynchronously.
4. Be tolerant: unknown event types → 2xx and ignore. Log the delivery id.

**Sending webhooks** (awareness): sign payloads, include an event id for consumer dedupe, retry with exponential backoff, and let receivers replay.

## HATEOAS awareness

*Hypermedia as the Engine of Application State*: responses include links to related actions (`"_links": {"cancel": {"href": "/orders/881/cancel"}}`),
so clients navigate by links instead of hard-coded URLs. It's the strictest level of the Richardson Maturity Model
(0: one endpoint/RPC, 1: resources, 2: HTTP verbs + status codes, **3: hypermedia**). Spring HATEOAS supports it.
Most real-world "REST" APIs stop at level 2. Know the term, the model, and an honest trade-off: discoverability and
server-driven workflows vs extra payload and client complexity.

## OpenAPI and springdoc

OpenAPI 3 is the machine-readable contract of your API: paths, parameters, schemas, responses, security schemes.

```xml
<dependency>
  <groupId>org.springdoc</groupId>
  <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
  <version>2.8.9</version>   <!-- use the latest 2.x compatible with your Boot 3.x; check the springdoc compatibility table -->
</dependency>
```

- The spec is served at `/v3/api-docs` and the UI at `/swagger-ui.html`. Both need `permitAll()` in security (or dev-only).
- Enrich with annotations where the code can't speak for itself:

```java
@Tag(name = "Orders")
@Operation(summary = "Create an order", description = "Reserves stock. Requires Idempotency-Key.")
@ApiResponse(responseCode = "201", description = "Created")
@ApiResponse(responseCode = "409", description = "Insufficient stock",
             content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@PostMapping
public ResponseEntity<OrderResponse> create(@RequestHeader("Idempotency-Key") String key,
                                            @Valid @RequestBody CreateOrderRequest body) { … }
```

- Declare the bearer auth scheme once (`@SecurityScheme(type = HTTP, scheme = "bearer", bearerFormat = "JWT")`) so "Authorize" works in Swagger UI.
- **Code-first** (generate the spec from annotations; used in FlowGrid) vs **design-first** (write the YAML, generate stubs/clients). Know both.
- Commit an exported `openapi.json` to the repo, or check it in CI, so contract changes show up in PR diffs. You can generate the React client's types from it (Week 7).

## Security

Baseline for every API in this roadmap (checked again in the Week 24 security review):

- **HTTPS only** in any deployed environment (TLS at the load balancer or nginx). Never send tokens over plain HTTP. HSTS in prod.
- **Auth schemes:**

| Scheme | How | Use |
|---|---|---|
| Bearer JWT | `Authorization: Bearer <jwt>` | user sessions for your SPA (FlowGrid, LedgerX, FlagForge dashboard) |
| API key | `Authorization: Bearer <key>` or a custom header; store only a **hash** server-side | machine clients: FlagForge **SDK keys** per environment |
| HMAC signature | hash of the body with a shared secret | webhooks (GitHub → ForgeCI) |
| OAuth 2.0 / OIDC | delegated authorization / login via a provider | "Log in with GitHub" (ForgeCI M1), acting on a user's repos |
| Basic | `user:password` base64 | internal tools only, and only over TLS |

- **Validate everything** (types, ranges, sizes). Limit body size and page size. Timeouts on outbound calls.
- **Least data:** response DTOs expose only what the caller may see.
- **OWASP API Security Top 10 (2023)**: know each one and where your code handles it:

| # | Risk | Where you handle it |
|---|---|---|
| API1 | Broken Object Level Authorization | ownership/membership check on every `/{id}` access (LedgerX wallets, FlagForge orgs) |
| API2 | Broken Authentication | BCrypt, token expiry, login rate limit, generic error messages |
| API3 | Broken Object Property Level Authorization | request/response DTOs; no mass assignment; no over-exposed fields |
| API4 | Unrestricted Resource Consumption | page-size caps, rate limits, timeouts, body-size limits |
| API5 | Broken Function Level Authorization | role rules on admin endpoints, tested per role |
| API6 | Unrestricted Access to Sensitive Business Flows | limits on flows that can be abused (mass order creation, payment requests) |
| API7 | Server-Side Request Forgery | validate/allow-list URLs your server fetches (webhook targets, repo URLs) |
| API8 | Security Misconfiguration | CORS allow-list, actuator locked down, no stack traces, security headers |
| API9 | Improper Inventory Management | versioned, documented APIs; old versions retired; no forgotten debug endpoints |
| API10 | Unsafe Consumption of APIs | treat GitHub/third-party responses as untrusted input; timeouts; validation |

## REST vs GraphQL vs gRPC

| | REST (JSON/HTTP) | GraphQL | gRPC |
|---|---|---|---|
| Model | resources + HTTP verbs | one endpoint, client-specified query over a typed schema | RPC methods on services, Protobuf messages |
| Transport/format | HTTP/1.1+, JSON | HTTP (usually POST), JSON | HTTP/2, binary Protobuf |
| Strengths | simple, universal, HTTP caching, great tooling, easy to debug with curl | clients fetch exactly what they need in one round-trip; strong schema; good for many different UIs | fast, compact, strongly typed contracts, streaming, code generation |
| Weaknesses | over/under-fetching, many round-trips for nested data | caching is harder, N+1 on the server (resolvers), query-cost limiting, more server complexity | not browser-native (needs gRPC-Web), harder to inspect, tighter coupling |
| Best for | public and CRUD-ish APIs, what all four projects expose | aggregating many backends for varied front ends | internal service-to-service calls with high throughput/latency needs |

Honest answer: "I built REST because the clients are a browser dashboard and an SDK, HTTP semantics (status codes,
caching, idempotency keys) fit, and the tooling is universal. I'd consider gRPC for high-volume internal calls, and GraphQL
if many different front ends needed differently-shaped data."

## Design review checklist

Run this on every new endpoint (in the PR description):

- [ ] Noun-based URI, correct method, correct success code (`201` + `Location` for creates)
- [ ] Request and response DTOs; validation on every field; no entity exposed
- [ ] Every error path returns a documented ProblemDetail `type`
- [ ] Lists are paginated with a capped size, a deterministic sort and whitelisted sort fields
- [ ] Non-idempotent POSTs that move money or stock require `Idempotency-Key`
- [ ] Authentication required unless explicitly public; role and **object-level** checks tested
- [ ] Queries behind each filter/sort combination are indexed (EXPLAIN checked)
- [ ] OpenAPI annotations updated; exported spec diff reviewed
- [ ] curl/Postman example added ([`curl-postman.md`](./curl-postman.md))

## 🎤 Interview Q&A

<details><summary>How do you design a REST API for X?</summary>

Identify resources and relationships, map operations to methods and status codes, define request/response DTOs, pagination/filtering/sorting, the error format (RFC 9457), auth and authorization rules, idempotency for unsafe retries, versioning, and document it with OpenAPI. Then consider rate limits and observability.
</details>

<details><summary>Offset vs cursor pagination?</summary>

Offset (page/size) is simple and supports jumping and totals, but it's slow for deep pages and unstable under inserts. Cursor/keyset encodes the last seen sort key and filters with a WHERE, so it has constant cost and stable ordering, but no random access or cheap totals. Cursor fits feeds, logs and histories.
</details>

<details><summary>How do you make POST requests safe to retry?</summary>

Idempotency keys: the client sends a unique key per operation, and the server records key + request fingerprint + response under a unique constraint, in the same transaction as the work. Retries get the stored response; a reused key with a different body is rejected; concurrent duplicates see "in progress".
</details>

<details><summary>How do you version an API?</summary>

Usually the URI (/v1). Evolve additively without new versions. Breaking changes (removals, renames, type changes) go in a new version with a deprecation period, Deprecation/Sunset headers and usage monitoring.
</details>

<details><summary>How do you model a state transition like "cancel order"?</summary>

As an explicit action (`POST /orders/{id}/cancel`) or a new resource (`POST /refunds`), which enforces the state machine in the service and returns 409 for invalid transitions. It's clearer than PATCHing a status field.
</details>

<details><summary>What is HATEOAS / the Richardson Maturity Model?</summary>

Levels 0–3: RPC over one endpoint, resources, HTTP verbs + status codes, hypermedia links driving client state. Most production APIs are level 2. HATEOAS adds discoverability at the cost of payload size and client complexity.
</details>

<details><summary>How do you secure a webhook receiver?</summary>

Verify an HMAC signature over the raw body with a shared secret (constant-time compare), dedupe on the delivery id, respond quickly and process asynchronously, and treat the payload as untrusted input.
</details>

<details><summary>REST vs GraphQL vs gRPC?</summary>

REST: simple, cacheable, universal. GraphQL: flexible client-driven queries, harder caching and cost control. gRPC: fast, typed, streaming, best internally, not browser-native. Choose by client types, performance needs and team tooling.
</details>

<details><summary>Name some OWASP API Top 10 risks and your mitigations.</summary>

BOLA (object-level checks on every id), broken authentication (BCrypt, expiry, login rate limits), property-level authorization (DTOs, no mass assignment), unrestricted resource consumption (page caps, rate limits, timeouts), SSRF (URL allow-lists). Tie each to code you wrote.
</details>

## ✅ Mastery checklist

- [ ] FlowGrid M1 endpoints pass the design review checklist; OpenAPI UI works with JWT "Authorize"
- [ ] FlowGrid M2 / LedgerX M2: idempotency keys implemented per the table above, including the concurrent-duplicate case
- [ ] LedgerX M3: cursor pagination with an opaque cursor and a supporting index
- [ ] ForgeCI M1: webhook signature verification + delivery dedupe
- [ ] Can map each OWASP API Top 10 item to a line of my own code (Week 24)
