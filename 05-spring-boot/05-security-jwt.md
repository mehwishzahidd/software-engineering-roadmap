# 05 · Spring Security 6 and Stateless JWT Authentication

> **Week 4** (FlowGrid M1: "JWT auth + roles": ADMIN, OPS_MANAGER, WAREHOUSE_ASSOCIATE, VIEWER). Reused in LedgerX (W9),
> ForgeCI (W14, plus GitHub OAuth and webhook HMAC), FlagForge (W20, org roles plus SDK keys). Client side (token storage,
> protected routes): [`../08-react/04-api-integration-auth.md`](../08-react/04-api-integration-auth.md).
> Security review checklist (OWASP API Top 10): [`../06-rest-apis/api-design-guide.md`](../06-rest-apis/api-design-guide.md#security).

## 1. Concepts

| Term | Meaning | HTTP failure |
|---|---|---|
| **Authentication** | *who are you?* (verify credentials / token) | **401 Unauthorized** (really "unauthenticated") |
| **Authorization** | *are you allowed to do this?* (roles, ownership) | **403 Forbidden** |
| Principal | the authenticated identity (username/user id) | |
| `GrantedAuthority` | a permission string, e.g. `inventory:write` or `ROLE_ADMIN` | |
| Role | an authority with the `ROLE_` prefix; `hasRole('ADMIN')` checks `ROLE_ADMIN` | |
| `SecurityContext` | holds the current `Authentication`, per thread (`SecurityContextHolder`) | |

**Roles vs authorities:** a role is a coarse bundle ("OPS_MANAGER"); an authority can be a fine-grained permission
("stock:adjust"). Start with roles. Map roles to permissions when the rules get more specific.

## 2. How Spring Security plugs in

Spring Security is a **servlet filter** (`DelegatingFilterProxy` → `FilterChainProxy`) that runs **before**
DispatcherServlet ([`02-web-layer.md`](./02-web-layer.md)). It holds one or more `SecurityFilterChain`s, each an ordered list of
filters: CORS → CSRF → authentication filters (form login, basic, **your JWT filter**, bearer token) → `ExceptionTranslationFilter`
→ `AuthorizationFilter`.

Adding `spring-boot-starter-security` alone secures **everything** with HTTP Basic/form login and a generated password
printed at startup. You replace that by defining your own `SecurityFilterChain` bean.

## 3. Passwords

```java
@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();   // stores "{bcrypt}$2a$10$…"; bcrypt by default
}
```

- **BCrypt** is a slow, salted, adaptive hash: the per-password salt defeats rainbow tables, and the cost factor (default 10) makes brute force expensive. `encode()` gives a different string each time; verify with `matches(raw, hash)`.
- Never store plain or reversibly-encrypted passwords. Never log them. Never return the hash in an API.
- The delegating encoder's `{id}` prefix lets you migrate algorithms later (bcrypt → argon2) without breaking old hashes.

## 4. Stateless JWT authentication ⭐

### What a JWT is

`base64url(header).base64url(payload).signature`

```json
{ "alg": "HS256", "typ": "JWT" }
{ "sub": "42", "roles": ["OPS_MANAGER"], "iss": "flowgrid", "iat": 1790000000, "exp": 1790000900 }
```

- **Signed, not encrypted.** Anyone can base64-decode the payload, so put no secrets in it. The signature proves it wasn't modified and was issued by someone holding the key.
- **HS256:** one shared secret signs and verifies (fine for a single service; the secret must be ≥ 256 bits and come from config/env).
  **RS256/ES256:** a private key signs and a public key verifies (better when other services verify tokens).
- **Stateless:** the server stores no session. Each request carries `Authorization: Bearer <token>`. The trade-off is that you
  can't easily revoke a token before `exp`. Mitigate with short access tokens (5–15 min) plus refresh tokens (stored
  server-side and revocable) or a deny-list for logout/compromise.

### Issuing and parsing (jjwt 0.12.x)

```java
@Service
public class JwtService {
    private final SecretKey key;
    private final String issuer;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
                      @Value("${app.jwt.issuer}") String issuer,
                      @Value("${app.jwt.ttl:15m}") Duration ttl) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));   // throws if < 256 bits
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public String issue(String subject, Collection<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject).issuer(issuer)
                .claim("roles", roles)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {                     // verifies signature, exp, issuer; throws JwtException
        return Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                .parseSignedClaims(token).getPayload();
    }
}
```

### The filter (`OncePerRequestFilter`)

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    public JwtAuthenticationFilter(JwtService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims c = jwt.parse(header.substring(7));
                List<?> roles = c.get("roles", List.class);
                var authorities = roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
                var auth = UsernamePasswordAuthenticationToken.authenticated(c.getSubject(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();       // bad/expired token: stay anonymous -> 401 later
            }
        }
        chain.doFilter(req, res);                           // ALWAYS continue; authorization decides
    }
}
```

Don't annotate this filter with `@Component`. Spring Boot auto-registers every `Filter` bean with the servlet container,
so it would **also** run outside the security chain. Construct it inside the security config (below), or disable its
auto-registration with a `FilterRegistrationBean`.

### The security configuration

```java
@Configuration
@EnableMethodSecurity                                        // enables @PreAuthorize
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, JwtService jwtService) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)           // stateless bearer tokens: see §6
            .cors(Customizer.withDefaults())                 // uses the CorsConfigurationSource bean
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                .anyRequest().authenticated())               // first match wins: specific rules first
            .exceptionHandling(e -> e
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))   // 401 instead of a login redirect
            .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();           // DaoAuthenticationProvider wired from your UserDetailsService + PasswordEncoder
    }
}
```

### Login: verify the password, then issue the token

```java
@Service
class DbUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    DbUserDetailsService(UserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        AppUser u = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("unknown user"));
        return User.withUsername(u.getId().toString())       // subject = stable id, not the e-mail
                .password(u.getPasswordHash())
                .roles(u.getRole().name())                   // adds the ROLE_ prefix
                .disabled(!u.isActive())
                .build();
    }
}

// in AuthController
@PostMapping("/api/auth/login")
TokenResponse login(@Valid @RequestBody LoginRequest req) {
    Authentication auth = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(req.email(), req.password()));   // BadCredentialsException if wrong
    List<String> roles = auth.getAuthorities().stream()
            .map(a -> a.getAuthority().substring("ROLE_".length())).toList();
    return new TokenResponse(jwtService.issue(auth.getName(), roles), "Bearer");
}
```

Map `AuthenticationException` to a **generic** 401 ProblemDetail in your advice. Don't reveal whether the e-mail exists
("invalid credentials", not "wrong password"). Rate-limit the login endpoint.

**Built-in alternative:** `spring-boot-starter-oauth2-resource-server` + `http.oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))`
validates bearer JWTs for you (given a `JwtDecoder`, e.g. `NimbusJwtDecoder.withSecretKey(key).build()`, or an issuer URI).
Writing the filter yourself once is the best way to understand it. Using the resource server is what most production code does.
Knowing both is a strong interview answer.

## 5. Method security and ownership checks

```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteWarehouse(long id) { … }

@PreAuthorize("hasAnyRole('ADMIN','OPS_MANAGER')")
public AdjustmentResponse adjustStock(AdjustStockRequest req) { … }

@PreAuthorize("@projectAccess.canEdit(#projectId, authentication)")   // custom bean for resource-level rules
public FlagResponse updateFlag(long projectId, UpdateFlagRequest req) { … }
```

- URL rules are coarse. **Object-level authorization** ("is this *your* wallet?", "is the caller a member of this org?") must
  be checked for every access. Missing object-level checks is **OWASP API #1: Broken Object Level Authorization**. LedgerX
  wallets and FlagForge orgs live or die on this.
- `@PreAuthorize` works through a **proxy**, so the same self-invocation caveat as `@Transactional` applies ([`07-transactions.md`](./07-transactions.md)).
- Denials raise `AccessDeniedException` → 403.

## 6. CORS and CSRF

**CORS** is a **browser** rule: a page from `http://localhost:5173` (Vite) calling `http://localhost:8080` is cross-origin, so
the browser sends a **preflight** `OPTIONS` and checks the `Access-Control-Allow-*` response headers. curl and Postman ignore CORS entirely.

```java
@Bean
CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(origins);                                   // explicit list, never "*" with credentials
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
    c.setExposedHeaders(List.of("Location", "Retry-After"));
    c.setMaxAge(Duration.ofHours(1));
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/api/**", c);
    return src;
}
```

**CSRF** attacks abuse the fact that browsers **automatically attach cookies**. If authentication is a bearer token that
your JS adds explicitly to the `Authorization` header, a forged cross-site request can't carry it, so disabling CSRF is
acceptable. **If you put the token (or session) in a cookie, CSRF protection is required again** (SameSite cookies plus a
CSRF token). That trade-off matters when you pick token storage for the React dashboard.

## 7. Testing security

```java
@WebMvcTest(StockController.class)
@Import(SecurityConfig.class)
class StockControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean StockService service;       // Boot 3.4+ (older: @MockBean)
    @MockitoBean JwtService jwtService;

    @Test void anonymousGets401() throws Exception {
        mvc.perform(post("/api/stock/adjustments")).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(roles = "VIEWER")
    void viewerCannotAdjust() throws Exception {
        mvc.perform(post("/api/stock/adjustments").contentType(APPLICATION_JSON).content("{}"))
           .andExpect(status().isForbidden());
    }
}
```

`spring-security-test` also provides `jwt()` and `user()` request post-processors. Every role × endpoint rule deserves a test,
because security regressions are silent. More in [`../09-testing/spring-testing.md`](../09-testing/spring-testing.md).

## 8. Apply it to FlowGrid M1 (you implement)

- Register/login, BCrypt hashes, access token ≤ 15 min, secret from an env var (≥ 256-bit, never committed).
- Role matrix in the README: which of ADMIN / OPS_MANAGER / WAREHOUSE_ASSOCIATE / VIEWER may call which endpoint. One test per cell that matters.
- 401 vs 403 both as ProblemDetail. Login errors are generic.
- Swagger UI and health are public; everything else is authenticated.
- Record in `SECURITY.md` why you chose stateless JWT, where the client stores it, and how logout/revocation works (or honestly: doesn't yet).

## 9. 🔨 Break it

1. Change one character of a token's payload (e.g. the role) and re-encode it. What happens and why?
2. Issue a token with `ttl: 5s`, wait, call again. Which exception gets swallowed, and what status does the client see?
3. Annotate the JWT filter with `@Component` and add a log line. How many times does it log per request?
4. Put `.anyRequest().authenticated()` **before** the `permitAll()` line. What breaks?
5. Call the API from the React dev server without the CORS bean. Read the browser console, then the Network tab's preflight.
6. Call a `@PreAuthorize` method from another method in the same class. Is the rule enforced?
7. Use `hasAuthority("ADMIN")` against a user built with `.roles("ADMIN")`. Why 403?

## 10. 🐞 Debugging tips

- `logging.level.org.springframework.security=DEBUG` prints the filter chain for each request and why access was denied.
- The request goes to `/login` or returns HTML → the default form login is still active; your `SecurityFilterChain` bean isn't being picked up.
- Everything returns 401 → the filter isn't setting the context (wrong header parsing, secret mismatch between issuer and verifier, clock skew on `exp`).
- 403 on POST only → CSRF still enabled.
- Paste tokens into a local decoder to inspect claims. Never paste production tokens into websites.

## 11. 🎤 Interview Q&A

<details><summary>Authentication vs authorization?</summary>

Authentication establishes identity (credentials, token verification) and failure is 401. Authorization decides whether that identity may perform the action (roles, permissions, ownership) and failure is 403.
</details>

<details><summary>How does JWT authentication work in your Spring Boot app?</summary>

Login verifies the password via AuthenticationManager (DaoAuthenticationProvider + BCrypt) and issues a signed JWT with subject, roles and expiry. Each request sends it as a Bearer token. A OncePerRequestFilter in the security chain verifies signature, expiry and issuer, builds an Authentication with authorities, and puts it in the SecurityContext. Authorization rules (URL and @PreAuthorize) then decide. The server is stateless.
</details>

<details><summary>What's inside a JWT? Is it encrypted?</summary>

Header (algorithm), payload (claims: sub, exp, iat, iss, custom roles) and signature, each base64url-encoded. It's signed, not encrypted: anyone can read the payload, so no secrets go in it.
</details>

<details><summary>How do you log out / revoke a JWT?</summary>

You can't un-issue it. Keep access tokens short-lived, use refresh tokens that are stored server-side and can be revoked, and optionally keep a deny-list of token ids (jti) until they expire. Rotating the signing key revokes everything.
</details>

<details><summary>Why BCrypt?</summary>

It's deliberately slow and salted with a tunable cost factor, which makes offline brute force and rainbow tables impractical. Fast hashes like SHA-256 are the wrong tool for passwords.
</details>

<details><summary>hasRole vs hasAuthority?</summary>

hasRole('X') checks for the authority 'ROLE_X'; hasAuthority('X') checks the exact string. `.roles("X")` on a UserDetails builder adds the ROLE_ prefix.
</details>

<details><summary>Why can you disable CSRF for a JWT API?</summary>

CSRF relies on the browser automatically sending credentials (cookies). A bearer token added explicitly by JS isn't sent automatically on cross-site requests. If tokens live in cookies, CSRF protection is needed again.
</details>

<details><summary>What is CORS and who enforces it?</summary>

The browser enforces it. The server declares which origins, methods and headers are allowed, and the browser blocks cross-origin responses otherwise (with a preflight OPTIONS for non-simple requests). It is not an authentication mechanism, and non-browser clients ignore it.
</details>

<details><summary>What is a SecurityFilterChain?</summary>

The Spring Security 6 way to configure security: a bean built from HttpSecurity defining matchers, authentication mechanisms, authorization rules, session policy, CSRF/CORS, and custom filters. It replaces the removed WebSecurityConfigurerAdapter.
</details>

## ✅ Mastery checklist

- [ ] FlowGrid M1: login + JWT + role rules + 401/403 ProblemDetails, each rule tested
- [ ] Wrote the JWT filter by hand once; can explain the resource-server alternative
- [ ] Can explain CSRF vs CORS and when CSRF protection comes back
- [ ] Can name OWASP API #1 (BOLA) and show where the object-level check lives in my code
