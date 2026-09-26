# 🌐 06 · REST APIs and HTTP

> **Goal:** design HTTP APIs that are predictable, safe to retry, secure, and documented, and defend every design
> decision (status code, method, pagination style, idempotency) in an interview. All four projects expose REST APIs;
> FlagForge also ships an SDK that *consumes* one.

## Scope and ownership

- **This folder owns HTTP semantics for API design:** methods, status codes, headers, conventions, error format, idempotency, pagination, versioning, API security, and tooling (curl/Postman).
- **TCP/IP, DNS, TLS handshakes** live in [`../14-cs-fundamentals/networking.md`](../14-cs-fundamentals/networking.md).
- **Spring implementation** (controllers, validation, ProblemDetail, security) lives in [`../05-spring-boot/`](../05-spring-boot/).

## When (just-in-time, per [ROADMAP §6](../ROADMAP.md#6-just-in-time-learning-map))

| Week | Read | For |
|---|---|---|
| 3 | [`http-for-apis.md`](./http-for-apis.md) (all), [`curl-postman.md`](./curl-postman.md) §1–3, [`api-design-guide.md`](./api-design-guide.md) naming → versioning | the foundation Spring Boot API; before designing FlowGrid's API |
| 4 | [`api-design-guide.md`](./api-design-guide.md): pagination, filtering, errors, OpenAPI; [`curl-postman.md`](./curl-postman.md) §4 | **FlowGrid M1**: CRUD, pagination/sort/filter, ProblemDetail, springdoc, Postman collection |
| 5 | [`api-design-guide.md`](./api-design-guide.md): idempotency keys | **FlowGrid M2**: `Idempotency-Key` on order creation |
| 6–7 | actions & state transitions | FlowGrid M3–M4: pick/pack/ship transitions, returns, transfers |
| 10–11 | idempotency keys (again), cursor pagination | **LedgerX M2–M3**: transfer idempotency, history with cursors |
| 14 | long-running work & webhooks, auth schemes | **ForgeCI M1**: GitHub OAuth, HMAC-verified webhooks, 202 for builds |
| 16 | SSE in [`http-for-apis.md`](./http-for-apis.md) §6 | **ForgeCI M3**: live log streaming |
| 20–22 | versioning, conditional requests (ETag/304), API keys | **FlagForge**: admin API, SDK polling with ETags, SDK-facing API design |
| 24 | security (OWASP API Top 10), design review checklist | security pass over all four projects |

## Files

| File | Covers |
|---|---|
| [`http-for-apis.md`](./http-for-apis.md) | anatomy of an exchange, methods with the safety/idempotency table, PUT vs PATCH, status codes with when-to-use, headers (Content-Type, Accept, Authorization, Cache-Control, ETag/If-None-Match/If-Match, Location, Retry-After), caching, SSE, HTTP/1.1 vs 2 vs 3 |
| [`api-design-guide.md`](./api-design-guide.md) | resource naming, actions/state transitions, versioning, offset vs cursor pagination, filtering/sorting, RFC 9457 errors, idempotency keys, rate-limit headers, async work & webhooks, HATEOAS, OpenAPI/springdoc, API security & OWASP API Top 10, REST vs GraphQL vs gRPC, review checklist |
| [`curl-postman.md`](./curl-postman.md) | curl flags (`-v -i -X -H -d --json -w -N`…), scripted sessions with jq, HTTPie, Postman collections/environments/tests, Newman in CI |

## Practice drills

1. **Status-code flashcards** (Week 3, then weekly for a month): 20 scenarios, answer in 3 seconds each ("SKU code already exists", "token expired", "report queued", "same idempotency key, different body", …).
2. **Design on paper first** (Week 3): before writing FlowGrid code, write the endpoint table (method, path, request DTO, success code, error types) for warehouses, SKUs and inventory. Review it against the [design review checklist](./api-design-guide.md#design-review-checklist).
3. **Explain a retry** (Week 5): draw the timeline of a POST whose response is lost, with and without an Idempotency-Key, including two concurrent first attempts.
4. **Critique a public API** (any week): read GitHub's REST docs on pagination and rate limits and compare them to your choices.

## Resources

- MDN HTTP docs (developer.mozilla.org/docs/Web/HTTP): methods, status codes, headers, caching, CORS. The best everyday reference.
- RFC 9110 (HTTP Semantics), RFC 9457 (Problem Details), RFC 7396 (JSON Merge Patch). Skim; know they exist and what they define.
- OpenAPI Specification (spec.openapis.org) and springdoc-openapi docs (springdoc.org).
- OWASP API Security Top 10 (owasp.org/API-Security).
- GitHub REST API docs (docs.github.com/rest): a well-designed real API, and the one ForgeCI integrates with.
- Book (optional): *API Design Patterns* (JJ Geewax).

## ✅ Module mastery checklist

- [ ] Method table and status-code choices recalled without notes
- [ ] FlowGrid API documented in OpenAPI; Postman collection with tests runs in CI
- [ ] Idempotency keys implemented and tested for concurrent duplicates (FlowGrid M2, LedgerX M2)
- [ ] Cursor pagination implemented (LedgerX M3); can explain when offset is still fine
- [ ] Webhook receiver verifies signatures and dedupes (ForgeCI M1)
- [ ] Can answer "REST vs GraphQL vs gRPC" with reasons tied to my projects
- [ ] [`../17-resume-tech-defense/rest-apis.md`](../17-resume-tech-defense/rest-apis.md) drill done
