# 04 · Bean Validation and Error Handling with ProblemDetail (RFC 9457)

> **Week 4** (FlowGrid M1: "CRUD + validation + ProblemDetail errors"). Reused unchanged in every later project.
> API-level error format rules: [`../06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md#errors).

## 1. Bean Validation (`jakarta.validation`)

Add `spring-boot-starter-validation` (Hibernate Validator is the implementation).

```java
public record CreateEventRequest(
        @NotNull @Positive Long venueId,
        @NotBlank @Size(max = 200) String title,
        @NotNull @Future OffsetDateTime startsAt,
        @NotNull @Future OffsetDateTime endsAt,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal basePrice,
        @NotNull @Size(min = 1, max = 20) List<@Valid TicketTierRequest> tiers) {}   // nested + element validation

public record TicketTierRequest(@NotBlank String name, @Min(1) int quantity) {}
```

| Constraint | Checks | Note |
|---|---|---|
| `@NotNull` | not null | |
| `@NotEmpty` | not null and size/length > 0 | strings, collections |
| `@NotBlank` | not null and has a non-whitespace char | strings only |
| `@Size(min,max)` | length / collection size | |
| `@Min/@Max`, `@Positive/@PositiveOrZero` | numeric bounds | |
| `@DecimalMin/@DecimalMax`, `@Digits` | `BigDecimal` bounds and scale | money |
| `@Email`, `@Pattern(regexp=…)` | format | `@Email` is permissive, so still verify ownership |
| `@Past/@Future(OrPresent)` | temporal | |
| `@Valid` | cascade into a nested object / list elements | without it, nested constraints are **ignored** |

Most constraints treat `null` as **valid**. Combine them with `@NotNull` when a value is required.

### Triggering validation

```java
@PostMapping
public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest body) { … }
// invalid body -> MethodArgumentNotValidException -> 400
```

For `@RequestParam`/`@PathVariable` constraints (`@RequestParam @Max(100) int size`), Spring Framework 6.1+ validates them
automatically when the controller method has constraint annotations, and raises `HandlerMethodValidationException`.
In services, put `@Validated` on the class to validate method parameters, which raises `ConstraintViolationException`.

### Cross-field rules: a custom constraint

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EndsAfterStartsValidator.class)
public @interface EndsAfterStarts {
    String message() default "endsAt must be after startsAt";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class EndsAfterStartsValidator implements ConstraintValidator<EndsAfterStarts, CreateEventRequest> {
    @Override
    public boolean isValid(CreateEventRequest r, ConstraintValidatorContext ctx) {
        if (r.startsAt() == null || r.endsAt() == null) return true;   // @NotNull reports those
        return r.endsAt().isAfter(r.startsAt());
    }
}
// usage: @EndsAfterStarts public record CreateEventRequest(...) {}
```

### What validation is not

Bean Validation checks **the shape of the input**. Business rules that need state ("SKU code already exists", "can't cancel
a shipped order", "not enough available stock") belong in the **service**, often backed by a DB constraint. They produce
**409/422**, not 400. The database constraint stays as the final guarantee
([`../04-sql-databases/04-schema-design.md`](../04-sql-databases/04-schema-design.md)).

## 2. Error responses: ProblemDetail ⭐

RFC 9457 (which obsoletes RFC 7807) defines `application/problem+json`:

```json
{
  "type": "https://api.example.com/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "2 fields are invalid",
  "instance": "/api/events",
  "errors": [
    { "field": "title", "message": "must not be blank" },
    { "field": "startsAt", "message": "must be a future date" }
  ],
  "requestId": "6f1c2a9e-…"
}
```

Spring 6 has a `ProblemDetail` class. Setting `spring.mvc.problemdetails.enabled=true` makes Spring's built-in exceptions
(404 no handler, 405, 415, malformed JSON…) render as ProblemDetail. Your own exceptions go through a
`@RestControllerAdvice`:

```java
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {   // handles Spring MVC's own exceptions

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource not found");
        pd.setType(URI.create("https://api.example.com/problems/not-found"));
        return pd;
    }

    @ExceptionHandler(ConflictException.class)                             // business-rule / state conflicts
    ProblemDetail conflict(ConflictException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Conflict");
        return pd;
    }

    @Override                                                              // @Valid body failures -> field list
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, "Request body is invalid");
        pd.setTitle("Validation failed");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList());
        return ResponseEntity.status(status).body(pd);
    }

    @ExceptionHandler(Exception.class)                                     // last resort: never leak internals
    ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled exception", ex);                             // full stack trace in logs only
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
        pd.setTitle("Internal error");
        return pd;
    }

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
}
```

Your domain exceptions (`NotFoundException`, `ConflictException`, …) are plain `RuntimeException` subclasses you define.
Alternatively, extend Spring's `ErrorResponseException`, which carries its own `ProblemDetail`.

### Mapping table (decide once, apply everywhere)

| Situation | Exception (typical) | Status |
|---|---|---|
| Malformed JSON / wrong type | `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` | 400 |
| Field validation | `MethodArgumentNotValidException`, `HandlerMethodValidationException` | 400 (some APIs use 422) |
| Not authenticated | `AuthenticationException` (Security's entry point) | 401 |
| Authenticated but not allowed | `AccessDeniedException` | 403 |
| Unknown id | your `NotFoundException` | 404 |
| Unique constraint / duplicate | `DataIntegrityViolationException` (inspect constraint name) | 409 |
| Stale version (optimistic lock) | `ObjectOptimisticLockingFailureException` | 409 (client should re-read) or retry internally |
| Illegal state transition (ship a cancelled order) | your `ConflictException` / `InvalidStateException` | 409 |
| Same Idempotency-Key, different body | your exception | 422 (or 409; document it) |
| Rate limited | — | 429 + `Retry-After` |
| Bug / unexpected | anything else | 500, generic message |

`DataIntegrityViolationException` wraps the JDBC error. Walk the cause chain to the `SQLException`/`PSQLException` and map
**known constraint names** (`sku_code_uq` → "SKU code already exists"). That's why you name your constraints.

### Security and hygiene

- **No stack traces, SQL or class names in responses.** Log them server-side with the request ID
  ([`06-logging-actuator.md`](./06-logging-actuator.md)) and put the same `requestId` in the ProblemDetail, so a user report can be matched to the logs.
- Log 4xx at `WARN`/`INFO` (the client's fault; don't page anyone) and 5xx at `ERROR`.
- Security exceptions (401/403) are raised in the filter chain, **before** `@ControllerAdvice` can see them. Customize them
  via `AuthenticationEntryPoint`/`AccessDeniedHandler` ([`05-security-jwt.md`](./05-security-jwt.md)).
- `server.error.include-stacktrace=never` (the default) must stay that way in prod.

## 3. Apply it to FlowGrid M1 (you implement)

- One `@RestControllerAdvice` for the whole app; every error is `application/problem+json` with a stable `type` URI per error kind and a `requestId`.
- Request DTOs carry shape validation. Services enforce business rules (unique SKU code, non-negative adjustments that
  don't drive stock below zero, allowed state transitions) and throw typed exceptions.
- **Acceptance checks** (write them as `@WebMvcTest`s): malformed JSON → 400 problem; missing field → 400 with an `errors[]`
  entry for that field; unknown id → 404; duplicate SKU code → 409; unexpected exception → 500 with no stack trace in the body.

## 4. 🔨 Break it

1. Remove `@Valid` from the controller parameter and post an empty body `{}`. What gets saved (or which DB error do you get)?
2. Remove `@Valid` from `List<@Valid TicketTierRequest>` and send a tier with `quantity: 0`.
3. Throw `new RuntimeException("SELECT * FROM users failed: password=…")` from a service without the catch-all handler. What does the client see with default Boot error handling? With your handler?
4. Violate a unique constraint and return `ex.getMessage()` to the client. Read what you just leaked.
5. Throw `AccessDeniedException` from a controller vs from a `@PreAuthorize`d method vs in a filter. Which handler catches each?

## 5. 🐞 Debugging tips

- Validation not firing → missing `spring-boot-starter-validation`, a missing `@Valid`, or constraints on a field Jackson never set (a naming mismatch, so the value is `null` and only `@NotNull` would catch it).
- `@ExceptionHandler` not called → the exception happened in a filter (security, your MDC filter), or another advice with higher precedence handled it (`@Order`).
- Two advices both matching → the most specific exception type wins within one advice; between advices, order decides.

## 6. 🎤 Interview Q&A

<details><summary>How do you handle validation in Spring Boot?</summary>

Annotate DTO fields with Jakarta Bean Validation constraints, trigger with @Valid on @RequestBody (cascading with @Valid on nested objects), and @Validated for method-level validation. Failures raise MethodArgumentNotValidException (or HandlerMethodValidationException / ConstraintViolationException), which a @RestControllerAdvice maps to a 400 ProblemDetail with field errors.
</details>

<details><summary>What is ProblemDetail / RFC 9457?</summary>

A standard JSON error format (`application/problem+json`) with type, title, status, detail, instance and extension members. Spring 6 provides the ProblemDetail class and can render built-in exceptions this way.
</details>

<details><summary>@ControllerAdvice vs @RestControllerAdvice vs @ExceptionHandler?</summary>

@ExceptionHandler methods handle exceptions from the same controller or, when placed in a @ControllerAdvice, globally. @RestControllerAdvice = @ControllerAdvice + @ResponseBody.
</details>

<details><summary>400 vs 409 vs 422?</summary>

400: the request is malformed or fails schema/field validation. 409: the request conflicts with the current state of the resource (duplicate, version mismatch, invalid state transition). 422: syntactically valid but semantically unprocessable; some APIs use it for validation errors. Pick a convention and document it.
</details>

<details><summary>Where should business validation live?</summary>

In the service/domain layer (it needs state and transactions), backed by database constraints as the final guarantee. Bean Validation handles input shape.
</details>

<details><summary>How do you avoid leaking internal details in errors?</summary>

A catch-all handler returning a generic 500 ProblemDetail, full details only in server logs correlated by a request ID, never exception messages from SQL/drivers in responses, and stack traces disabled in error responses.
</details>

## ✅ Mastery checklist

- [ ] FlowGrid M1 returns ProblemDetail for 400/401/403/404/409/500, verified by tests
- [ ] Wrote one custom cross-field constraint
- [ ] Constraint-name → message mapping for unique violations
- [ ] Can explain shape validation vs business rules vs DB constraints with a FlowGrid example
