# 02 · The Web Layer: Request Lifecycle, Controllers, DTOs, Jackson

> **Week 3** (foundation API: two endpoints plus tests) → **Week 4** (FlowGrid M1: CRUD, DTOs, pagination/sort/filter, OpenAPI).
> Read [`../06-rest-apis/http-for-apis.md`](../06-rest-apis/http-for-apis.md) first. Examples use the neutral **TixHub**
> domain (venues/events) from [`../04-sql-databases/practice-schema.sql`](../04-sql-databases/practice-schema.sql). Your FlowGrid code is yours to write.

## 1. What happens to one HTTP request ⭐

```
Client ──HTTP──> Tomcat (embedded servlet container, thread from its pool)
   │
   ▼
[Servlet Filter chain]  ← request-ID filter, Spring Security's FilterChainProxy, CORS, …   (jakarta.servlet.Filter)
   │
   ▼
DispatcherServlet  (the single front controller)
   │ 1. HandlerMapping: which @Controller method matches  GET /api/venues/{id} ?
   │ 2. HandlerInterceptor.preHandle()   ← Spring MVC-level hooks
   │ 3. HandlerAdapter invokes the method:
   │      argument resolvers: @PathVariable, @RequestParam, @RequestBody (HttpMessageConverter → Jackson JSON→DTO),
   │      @Valid → Bean Validation, Pageable, Principal …
   │ 4. controller → service → repository → DB
   │ 5. return value: DTO / ResponseEntity → HttpMessageConverter (Jackson DTO→JSON), content negotiation via Accept
   │ 6. HandlerInterceptor.postHandle() / afterCompletion()
   │ on exception: HandlerExceptionResolver → @ExceptionHandler in @RestControllerAdvice → ProblemDetail
   ▼
[Filter chain unwinds] ──HTTP response──> Client
```

One request is handled on one Tomcat worker thread from start to finish (the blocking servlet model). With
`spring.threads.virtual.enabled=true` (Boot 3.2+, Java 21) each request runs on a **virtual thread** instead, which is useful
when requests spend most of their time waiting on I/O.

### Filters vs interceptors

| | Servlet `Filter` (`OncePerRequestFilter`) | Spring `HandlerInterceptor` |
|---|---|---|
| Layer | servlet container, **before** DispatcherServlet | inside Spring MVC, around the handler |
| Sees | raw request/response, every request (static files, errors, security) | only requests mapped to a handler; knows **which** handler method |
| Can | wrap/replace request & response, short-circuit, set MDC, authenticate | pre/post logic per handler, read handler annotations |
| Typical | request IDs, logging, **Spring Security**, CORS, compression | per-endpoint timing, locale, checking a custom annotation |
| Register | `@Component` bean or `FilterRegistrationBean` (order!) | `WebMvcConfigurer#addInterceptors` |

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TimingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(TimingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);                                // continue down the chain
        } finally {
            log.info("{} {} -> {} in {} ms", req.getMethod(), req.getRequestURI(),
                     res.getStatus(), (System.nanoTime() - start) / 1_000_000);
        }
    }
}
```

`OncePerRequestFilter` guarantees one execution per request, even across internal forwards and error dispatches.

## 2. Controllers

```java
@RestController                                  // = @Controller + @ResponseBody on every method
@RequestMapping("/api/venues")
public class VenueController {
    private final VenueService service;
    public VenueController(VenueService service) { this.service = service; }

    @GetMapping("/{id}")
    public VenueResponse get(@PathVariable long id) {
        return service.get(id);                  // 200 + JSON; service throws NotFoundException -> 404 (see 04-validation-errors)
    }

    @GetMapping
    public Page<VenueResponse> list(@RequestParam(required = false) String city,
                                    @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.search(city, pageable);   // ?city=Toronto&page=0&size=20&sort=capacity,desc
    }

    @PostMapping
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody CreateVenueRequest body) {
        VenueResponse created = service.create(body);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);          // 201 + Location header
    }

    @PutMapping("/{id}")
    public VenueResponse replace(@PathVariable long id, @Valid @RequestBody UpdateVenueRequest body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)                               // 204
    public void delete(@PathVariable long id) {
        service.delete(id);
    }
}
```

**Controllers stay thin:** parse, validate, delegate, map the status code. No business rules, no repositories, no
`@Transactional` here. Business logic belongs in services, where it's unit-testable without HTTP.

| Annotation | Binds | Missing → |
|---|---|---|
| `@PathVariable` | `/venues/{id}` | 404 if the pattern doesn't match; 400 if type conversion fails |
| `@RequestParam` | `?city=Toronto` | 400 unless `required = false` or `defaultValue` |
| `@RequestHeader("Idempotency-Key")` | a header | 400 unless optional |
| `@RequestBody` | JSON body via Jackson | 400 (`HttpMessageNotReadableException`) on malformed JSON |
| `Pageable` | `page`, `size`, `sort` params | defaults |

**Pagination JSON:** returning a Spring Data `Page` directly serializes internals that can change between versions.
Boot 3.3+ warns about this. Either map to your own record (`PageResponse<T>(List<T> items, int page, int size, long totalElements)`)
or enable `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`. API-level pagination design (offset vs cursor)
is in [`../06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md#pagination).

## 3. DTOs and mapping

**Never expose JPA entities as your API.** Reasons:

1. **Coupling:** renaming a column changes your public contract.
2. **Over-posting / mass assignment:** a client sends `"role": "ADMIN"` or `"id": 5` and Jackson happily sets it.
3. **Lazy-loading explosions:** serializing an entity touches lazy associations, which means N+1 queries or a `LazyInitializationException` ([`03-data-jpa.md`](./03-data-jpa.md)).
4. **Infinite recursion:** bidirectional relationships serialize forever (`Venue → events → venue → …`).
5. **Leaking data:** password hashes, internal flags, `version` columns.

**Records are ideal DTOs:** immutable, concise, with `equals`/`hashCode`/`toString`, and Jackson supports them natively.

```java
public record CreateVenueRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank String city,
        @Positive int capacity) {}

public record VenueResponse(long id, String name, String city, int capacity) {
    public static VenueResponse from(Venue v) {                 // manual mapping: explicit, debuggable
        return new VenueResponse(v.getId(), v.getName(), v.getCity(), v.getCapacity());
    }
}
```

| Mapping approach | Pros | Cons |
|---|---|---|
| Manual (static factory / mapper class) | explicit, no magic, compile-time safe | boilerplate for big objects |
| MapStruct | generated at compile time, fast, type-checked | annotation processor setup; learn its rules |
| ModelMapper (reflection) | little code | runtime surprises, silent mismatches: avoid |

Use **separate request and response DTOs**. Creation input, update input and output have different fields and rules.

## 4. Jackson essentials (Boot auto-configures an `ObjectMapper`)

| Concern | Default in Boot | Change with |
|---|---|---|
| Unknown JSON properties | **ignored** (`FAIL_ON_UNKNOWN_PROPERTIES=false`) | `spring.jackson.deserialization.fail-on-unknown-properties=true` (stricter APIs) |
| `java.time` types | supported, ISO-8601 strings (`2026-09-26T10:00:00Z`) | keep ISO; send `Instant`/`OffsetDateTime` |
| Nulls in output | included | `spring.jackson.default-property-inclusion=non_null` |
| Naming | camelCase | `spring.jackson.property-naming-strategy=SNAKE_CASE` (pick one per API, never mix) |
| `BigDecimal` | JSON number | many money APIs send amounts as **strings** to avoid float parsing in JS clients; decide and document |
| Enums | by name | keep names stable; they're part of the contract |

Customize with properties first. For more, add a `Jackson2ObjectMapperBuilderCustomizer` bean. Don't replace the whole
`ObjectMapper` unless you know what you're giving up.

## 5. Calling other services: `RestClient` (Spring 6.1+)

```java
@Bean
RestClient githubClient(RestClient.Builder builder) {                  // Boot provides a pre-configured builder
    return builder.baseUrl("https://api.github.com")
                  .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                  .build();
}

record Repo(long id, String fullName) {}
Repo repo = githubClient.get().uri("/repos/{owner}/{name}", owner, name)
        .retrieve()
        .body(Repo.class);                                            // 4xx/5xx -> RestClientResponseException
```

Always configure **timeouts** on outbound HTTP (connect and read). A slow dependency without timeouts exhausts your Tomcat
threads. You'll need this for ForgeCI (GitHub API) and FlagForge's SDK.

## 6. Apply it to FlowGrid M1 (you implement)

- Resources you'll expose: warehouses, products, SKUs, inventory levels, stock adjustments. Decide which are nested (`/warehouses/{id}/inventory`) and which are top-level with filters (`/inventory?warehouseId=…&skuId=…`); see the naming rules in [`../06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md).
- Every write takes a request DTO record with validation; every read returns a response DTO. No entity leaves the service layer.
- List endpoints: pagination + sort + at least two filters, and a **whitelist** of sortable fields (sorting by an arbitrary property is an injection and performance risk).
- `POST` returns 201 + `Location`; `DELETE` returns 204. You'll document the status codes in OpenAPI (springdoc).
- **Acceptance check:** a `@WebMvcTest` per controller proves the status codes, validation errors and JSON shape ([`../09-testing/spring-testing.md`](../09-testing/spring-testing.md)).

## 7. 🔨 Break it

1. Return the JPA entity instead of the DTO from a GET with a bidirectional relationship. Read the `StackOverflowError` or JSON-recursion error. Then read the SQL log.
2. Send `{"name": "x", "city": "y", "capacity": 10, "id": 999}` to a POST that binds an entity. What id got saved?
3. Send malformed JSON (`{"name": }`) and a string for `capacity`. What status, what body? (Fix the body in [`04-validation-errors.md`](./04-validation-errors.md).)
4. Hit `GET /api/venues/abc` where `id` is `long`. Status?
5. Make a filter that forgets `chain.doFilter(...)`. What does the client get?
6. Register an interceptor and a filter that both log. Which order do they print in for a 404 on an unmapped URL?

## 8. 🐞 Debugging tips

- `logging.level.org.springframework.web=DEBUG` shows mapping resolution ("Mapped to VenueController#get") and which message converter was used.
- 404 on an endpoint you're sure exists: wrong package (not scanned), wrong prefix, a trailing slash (Spring 6 no longer matches trailing slashes by default), or `@Controller` without `@ResponseBody`.
- 415 Unsupported Media Type → missing `Content-Type: application/json`. 406 Not Acceptable → an `Accept` header the server can't produce.
- `curl -v` shows exactly what was sent and received ([`../06-rest-apis/curl-postman.md`](../06-rest-apis/curl-postman.md)).

## 9. 🎤 Interview Q&A

<details><summary>Walk me through what happens when a request hits a Spring Boot app.</summary>

Tomcat accepts the connection and assigns a thread. The request passes through the servlet filter chain (including Spring Security's). DispatcherServlet asks the HandlerMappings for the matching controller method, runs interceptors' preHandle, and the HandlerAdapter resolves arguments (path variables, params, and the body via HttpMessageConverters/Jackson, with validation). It invokes the controller, which calls the service and the repository. The return value is written back via a message converter based on content negotiation. postHandle/afterCompletion run. Exceptions go through HandlerExceptionResolvers (@ControllerAdvice). Then the filters unwind.
</details>

<details><summary>Filter vs interceptor?</summary>

A filter is a servlet-level component that runs before DispatcherServlet for every request and can wrap the request/response (security, logging, request IDs). An interceptor is Spring MVC-level, runs only around mapped handlers, and knows which handler method is being called.
</details>

<details><summary>What's the role of DispatcherServlet?</summary>

It's the front controller. It receives all requests and delegates to handler mappings, handler adapters, message converters, view resolution and exception resolvers.
</details>

<details><summary>Why use DTOs instead of returning entities?</summary>

To decouple the API contract from the persistence model, prevent over-posting, avoid lazy-loading and recursion problems, hide sensitive fields, and shape each endpoint's input and output independently.
</details>

<details><summary>@Controller vs @RestController?</summary>

@RestController = @Controller + @ResponseBody. Return values are serialized to the response body instead of being resolved as view names.
</details>

<details><summary>What does an HttpMessageConverter do?</summary>

It converts between HTTP bodies and Java objects: JSON via Jackson's MappingJackson2HttpMessageConverter, strings, byte arrays, etc. The converter is picked from Content-Type (reading) and Accept (writing).
</details>

<details><summary>How do you return 201 with a Location header?</summary>

`ResponseEntity.created(uri).body(dto)`, building the URI with ServletUriComponentsBuilder from the current request and the new id.
</details>

## ✅ Mastery checklist

- [ ] Can draw the request lifecycle from memory, including filters, interceptors and exception resolution
- [ ] Week 3 API: two endpoints with DTO records and `@WebMvcTest` tests
- [ ] FlowGrid M1 controllers are thin; no entity crosses the controller boundary
- [ ] Wrote one filter (timing or request ID) and can explain why it's a filter, not an interceptor
- [ ] Did Break-it 1–6 and wrote down each status code and cause
