# HTTP for API Design: Methods, Status Codes, Headers

> **Week 3** (before the first controller), used every week after. Transport details (TCP, DNS, TLS handshakes) live in
> [`../14-cs-fundamentals/networking.md`](../14-cs-fundamentals/networking.md); this file covers the **semantics** you design APIs with.
> Practise every example with [`curl-postman.md`](./curl-postman.md).

## 1. Anatomy of an exchange

```
POST /api/v1/orders HTTP/1.1                      <- method, target, version
Host: api.example.com
Content-Type: application/json                    <- what I'm sending
Accept: application/json                          <- what I want back
Authorization: Bearer eyJhbGciOi…                 <- who I am
Idempotency-Key: 5f7c1c1e-8a4b-4b8e-9d51-2f0a6c1b9e11

{"lines":[{"sku":"SKU-1042","qty":2}]}

HTTP/1.1 201 Created                               <- status
Location: /api/v1/orders/881                        <- where the new resource lives
Content-Type: application/json
ETag: "3"
X-Request-Id: 6f1c2a9e-…

{"id":881,"status":"RESERVED", …}
```

HTTP is **stateless**: every request carries everything the server needs (auth, content type). Server-side state lives in
the database, not in "the connection".

## 2. Methods: safety and idempotency ⭐

| Method | Meaning | Safe | Idempotent | Request body | Typical success |
|---|---|:---:|:---:|---|---|
| `GET` | read a representation | ✅ | ✅ | no (ignored) | 200 (304 if unchanged) |
| `HEAD` | GET without body (headers only) | ✅ | ✅ | no | 200 |
| `OPTIONS` | what's allowed (CORS preflight) | ✅ | ✅ | no | 204/200 |
| `POST` | create a subordinate resource / run a processing action | ❌ | ❌ | yes | 201 + `Location`, or 200/202 |
| `PUT` | **replace** the resource at this URI with the body (or create it at a client-chosen URI) | ❌ | ✅ | yes (full representation) | 200 / 204 (201 if created) |
| `PATCH` | **partially** modify | ❌ | ❌ in general | yes (a patch document) | 200 / 204 |
| `DELETE` | remove | ❌ | ✅ | usually no | 204 (404 or 204 on repeat) |

- **Safe** = no intended state change. Crawlers, prefetchers and caches may call it freely. **Never change state on GET** (`GET /orders/881/cancel` gets triggered by link previews).
- **Idempotent** = doing it N times has the **same effect on the server** as doing it once. The *responses* may differ (DELETE: 204, then 404), but the state doesn't.
- Why it matters: networks fail **after** the server acted but **before** the client got the response. Clients, proxies and SDKs can **safely retry idempotent requests**. Retrying a `POST /orders` creates a second order unless you add an **Idempotency-Key** ([`api-design-guide.md`](./api-design-guide.md#idempotency-keys)). That's FlowGrid M2 and LedgerX M2.
- `PATCH` can be idempotent (`{"name":"x"}` as JSON Merge Patch) or not (`{"op":"increment"}`). Don't promise idempotency unless you've designed for it.

### PUT vs PATCH

| | `PUT /warehouses/7` | `PATCH /warehouses/7` |
|---|---|---|
| Body | the **complete** new state | only the changes |
| Missing field | set to default/null (it's a replacement) | left unchanged |
| Formats | the normal JSON representation | JSON Merge Patch (`application/merge-patch+json`, RFC 7396): `null` means "remove"; or JSON Patch (`application/json-patch+json`, RFC 6902): a list of ops |

In practice many APIs use PATCH with merge-patch semantics for updates, and PUT only for true replacement. Pick one convention per API and document it.

## 3. Status codes that matter (and when to use them) ⭐

### 2xx success

| Code | Use when | Notes |
|---|---|---|
| **200 OK** | successful GET/PUT/PATCH with a body | |
| **201 Created** | POST (or PUT) created a resource | include `Location`; usually return the representation |
| **202 Accepted** | request accepted, **processed later** | return a status resource URL (`Location: /builds/77`); ForgeCI queues builds like this |
| **204 No Content** | success, nothing to return (DELETE, some PUTs) | no body at all |

### 3xx redirection

| Code | Use when |
|---|---|
| 301 / 308 | resource moved permanently (308 keeps the method and body) |
| 302 / 307 | temporary redirect (307 keeps the method and body) |
| **304 Not Modified** | conditional GET: your cached copy (`If-None-Match` ETag) is still valid, so there's no body |

### 4xx client errors

| Code | Use when | Not to be confused with |
|---|---|---|
| **400 Bad Request** | malformed syntax, invalid JSON, failed field validation | |
| **401 Unauthorized** | **no or invalid credentials** (unauthenticated) | 403 |
| **403 Forbidden** | authenticated but **not allowed** | 404 (you may return 404 to hide existence) |
| **404 Not Found** | no such resource (or you won't reveal it) | |
| 405 Method Not Allowed | method not supported on this URI (send an `Allow` header) | |
| 406 Not Acceptable | can't produce any type in `Accept` | 415 |
| **409 Conflict** | conflicts with current state: duplicate unique value, invalid state transition, version conflict | 422 |
| 410 Gone | existed, permanently removed | |
| **412 Precondition Failed** | `If-Match` ETag didn't match (someone else changed it) | 409 |
| **415 Unsupported Media Type** | body `Content-Type` not supported | 406 |
| **422 Unprocessable Content** | well-formed but semantically invalid (some APIs use it for validation; idempotency key reused with a different body) | 400 |
| 428 Precondition Required | you require `If-Match` for updates and it was missing | |
| **429 Too Many Requests** | rate limited; send `Retry-After` | 503 |

### 5xx server errors

| Code | Use when |
|---|---|
| **500 Internal Server Error** | unexpected failure (a bug). Generic body, details in logs |
| 502 Bad Gateway | a proxy/gateway got an invalid response upstream (nginx → dead app) |
| **503 Service Unavailable** | temporarily overloaded / maintenance / dependency down; `Retry-After` |
| 504 Gateway Timeout | the upstream didn't answer in time |

Rules of thumb: **4xx = the client can fix the request, 5xx = the server must be fixed or recover.** Clients retry 5xx
(with backoff) for idempotent requests and 429 after `Retry-After`. Never retry 4xx unchanged. Never return 200 with
`{"error": …}` in the body; monitoring, clients and caches all trust the status code.

## 4. Headers you'll actually use

| Header | Direction | Purpose |
|---|---|---|
| `Content-Type` | both | media type of the body: `application/json`, `application/problem+json`, `text/event-stream` |
| `Accept` | request | what the client can handle (content negotiation) |
| `Authorization` | request | `Bearer <jwt>`, `Basic <base64>`, or a custom scheme |
| `Location` | response | URI of a created resource (201) or of a status resource (202), redirect target |
| `Cache-Control` | both | `no-store` (never cache: auth responses, personal data), `private, max-age=60`, `public, max-age=300`, `no-cache` (revalidate each time) |
| `ETag` | response | version tag of this representation: `"3"` (e.g., the entity `@Version`) or a content hash |
| `If-None-Match` | request | conditional GET: "send it only if the ETag changed", else 304 |
| `If-Match` | request | conditional update: "apply only if the ETag is still X", else **412**. That's optimistic locking over HTTP |
| `Last-Modified` / `If-Modified-Since` | both | date-based alternative to ETags (coarser) |
| `Vary` | response | which request headers change the response (`Vary: Accept, Authorization`) so caches don't mix them up |
| `Retry-After` | response | seconds (or a date) to wait, with 429/503 |
| `Idempotency-Key` | request | client-generated unique key making a POST safely retryable |
| `X-Request-Id` | both | correlation ID for logs ([`../05-spring-boot/06-logging-actuator.md`](../05-spring-boot/06-logging-actuator.md)) |
| `Access-Control-Allow-*`, `Origin` | both | CORS ([`../05-spring-boot/05-security-jwt.md`](../05-spring-boot/05-security-jwt.md)) |
| `X-Hub-Signature-256`, `X-GitHub-Delivery` | request (webhooks) | GitHub's HMAC signature and unique delivery id, which ForgeCI M1 verifies and dedupes |

### Conditional requests ⭐

```
GET /api/v1/flags/checkout-v2            -> 200, ETag: "12"
GET /api/v1/flags/checkout-v2
If-None-Match: "12"                      -> 304 Not Modified (no body: cheap polling; FlagForge's SDK polls like this)

PATCH /api/v1/skus/1042
If-Match: "7"                            -> 200, ETag: "8"      (nobody changed it)
If-Match: "7"                            -> 412 Precondition Failed (someone updated it: re-read, re-apply)
```

`If-Match` exposes JPA's `@Version` to clients, which prevents lost updates between two people editing the same form.
Spring has `ShallowEtagHeaderFilter` (hash-based, saves bandwidth, not DB work) or you can set ETags yourself from the version.

## 5. Caching in one table

| Response | Header |
|---|---|
| Personal/authenticated data | `Cache-Control: private, no-store` (or `no-cache` + ETag) |
| Public catalog data that may be seconds stale | `Cache-Control: public, max-age=60` + ETag |
| Static, fingerprinted assets (`app.3f9c1.js`) | `Cache-Control: public, max-age=31536000, immutable` |
| Token/login responses | `Cache-Control: no-store` |

HTTP caching (browsers, CDNs) is a separate layer from Redis. Both can apply.

## 6. Server-Sent Events (awareness for ForgeCI M3 and FlagForge M3/M4)

```
GET /api/v1/builds/77/logs/stream
Accept: text/event-stream

HTTP/1.1 200 OK
Content-Type: text/event-stream

id: 41
data: {"line":"mvn -B verify"}

id: 42
data: {"line":"[INFO] BUILD SUCCESS"}
```

One long-lived HTTP response streaming `data:` events. The browser `EventSource` auto-reconnects and sends
`Last-Event-ID: 42`, so the server can **replay from sequence 43**. It's server→client only and plain HTTP (works through
most proxies). WebSockets are bidirectional and need their own protocol upgrade. ForgeCI explains why SSE was chosen.

## 7. HTTP versions (enough for interviews)

| | HTTP/1.1 | HTTP/2 | HTTP/3 |
|---|---|---|---|
| Transport | TCP | TCP | QUIC over UDP |
| Concurrency | one request at a time per connection (browsers open ~6) | **multiplexed** streams on one connection, binary framing, header compression | multiplexed without TCP head-of-line blocking |
| API design impact | none: same methods, codes and headers | none | none |

## 8. 🔨 Break it

1. `curl -X POST` a JSON body **without** `Content-Type: application/json` to a Spring endpoint. Status?
2. Send `Accept: application/xml` to a JSON-only API. Status?
3. Call an endpoint with an expired token and then with a valid token but the wrong role. Compare the codes and bodies.
4. POST the same order twice (simulate a timeout-and-retry). How many orders exist? Now add an Idempotency-Key (after FlowGrid M2).
5. GET with `If-None-Match` using the ETag from a previous response. Then change the resource and repeat.

## 9. 🎤 Interview Q&A

<details><summary>What does idempotent mean? Which methods are idempotent?</summary>

Repeating the request has the same effect on server state as sending it once. GET, HEAD, OPTIONS, PUT and DELETE are idempotent by definition. POST isn't. PATCH isn't necessarily. That determines what can be retried safely.
</details>

<details><summary>Safe vs idempotent?</summary>

Safe means no intended state change (GET, HEAD, OPTIONS). Idempotent means the same effect however many times (PUT and DELETE are idempotent but not safe).
</details>

<details><summary>PUT vs PATCH vs POST?</summary>

PUT replaces the resource at a known URI (idempotent). PATCH applies a partial modification. POST creates a subordinate resource or triggers processing, and the server chooses the URI (not idempotent).
</details>

<details><summary>401 vs 403?</summary>

401: not authenticated (missing/invalid credentials). 403: authenticated but not permitted.
</details>

<details><summary>400 vs 409 vs 422?</summary>

400 for malformed or invalid input; 409 for a conflict with the current resource state (duplicates, version conflicts, illegal transitions); 422 for well-formed but semantically unprocessable content (convention varies).
</details>

<details><summary>How do ETags work?</summary>

The server tags a representation's version. Clients send If-None-Match for conditional GETs (304 if unchanged) and If-Match for conditional updates (412 if someone else changed it), enabling cache validation and optimistic concurrency over HTTP.
</details>

<details><summary>When would you return 202?</summary>

When work is accepted but completed asynchronously (a queued CI build, a report export). Return a Location to a status resource the client can poll, or stream updates.
</details>

<details><summary>What's the difference between 502, 503 and 504?</summary>

502: a gateway got an invalid response from upstream. 503: the service is temporarily unavailable (overload/maintenance), often with Retry-After. 504: a gateway timed out waiting for upstream.
</details>

## ✅ Mastery checklist

- [ ] Can fill in the method table (safe, idempotent, body, success code) from memory
- [ ] Can choose the status code for 15 scenarios without hesitation (drill with a friend or flashcards)
- [ ] Used `Location`, `ETag`/`If-None-Match`, `If-Match` and `Retry-After` in a real endpoint
- [ ] Explained why POST needs an Idempotency-Key using a FlowGrid or LedgerX example
