# 04 — API Integration and Frontend Auth

> Week 7 · ≈ 2 hours. This file owns the **client side** of auth. Server-side auth (Spring Security, JWT signing,
> BCrypt, roles) lives in [05-spring-boot/05-security-jwt.md](../05-spring-boot/05-security-jwt.md) and was built in FlowGrid M1.
> Build target: **FlowGrid M4**; reused for ForgeCI (Week 16, GitHub OAuth login) and FlagForge (Week 23, org roles).

---

## 1. Environment variables with Vite

```bash
# .env.development      (committed; non-secret defaults)
VITE_API_URL=http://localhost:8080
# .env.production       (committed; or injected at build time in CI)
VITE_API_URL=
# .env.local            (gitignored; your overrides)
```

```ts
// src/vite-env.d.ts
/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_API_URL: string;
}
interface ImportMeta {
  readonly env: ImportMetaEnv;
}
```

Facts to know cold:
- Only variables prefixed `VITE_` are exposed to client code, via `import.meta.env.VITE_*`.
- They are **inlined at build time** into the JS bundle. Changing them requires a rebuild.
- Anything in the bundle is **public**. Never put API secrets, DB passwords, signing keys or SDK *server* keys in `VITE_*`.
- An empty `VITE_API_URL` in production means "same origin" — nginx serves the SPA and proxies `/api` to Spring (no CORS needed; see [11-docker/compose.md](../11-docker/compose.md)).

---

## 2. CORS with Spring Boot

In development the SPA runs on `http://localhost:5173` and the API on `http://localhost:8080` — **different origins**
(scheme + host + port). The browser blocks JS from reading cross-origin responses unless the server opts in with CORS
headers. For non-simple requests (an `Authorization` header, `Content-Type: application/json`) the browser first sends an
`OPTIONS` **preflight**.

**Option A — avoid CORS in dev with Vite's proxy** (same-origin from the browser's view):

```ts
// vite.config.ts
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  server: { proxy: { "/api": "http://localhost:8080" } },
});
// then VITE_API_URL="" and call fetch("/api/...")
```

**Option B — configure CORS in Spring Security** (needed whenever SPA and API are really on different origins):

```java
@Bean
CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins);                 // e.g. http://localhost:5173 — never "*" with credentials
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-XSRF-TOKEN"));
    config.setExposedHeaders(List.of("Location"));
    config.setAllowCredentials(true);                         // only needed if you use cookies
    config.setMaxAge(3600L);                                  // cache preflight for 1h
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
// In the SecurityFilterChain: http.cors(Customizer.withDefaults())  — uses the bean above, answers preflights before auth
```

Note `Idempotency-Key` in `allowedHeaders`: without it, FlowGrid's order POST fails its preflight from the dev SPA.

**CORS is not security for your API** — it only controls what *browsers* let JS read. `curl` ignores it.
Authorization is still enforced by Spring Security.

> **Break it:** remove `http.cors(...)`. The console shows "blocked by CORS policy: No 'Access-Control-Allow-Origin' header";
> the Network tab shows the `OPTIONS` preflight rejected with 401/403. `curl` to the same URL works. Explain why.

---

## 3. A typed API client layer

One module owns `fetch`. Components never call `fetch` directly. This is a concept-sized core (≈ 40 lines) — you'll
extend it in your own repo.

```ts
// src/shared/api/client.ts
export type ProblemDetail = {
  type?: string; title?: string; status?: number; detail?: string; instance?: string;
  errors?: Record<string, string>;   // extension your @RestControllerAdvice adds for field errors
};

export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly problem?: ProblemDetail) {
    super(message);
    this.name = "ApiError";
  }
  get fieldErrors(): Record<string, string> | undefined { return this.problem?.errors; }
}

const BASE_URL = import.meta.env.VITE_API_URL ?? "";
let accessToken: string | null = null;                      // memory only — see §7
export const tokenStore = { get: () => accessToken, set: (t: string | null) => { accessToken = t; } };
let unauthorized: () => void = () => {};
export const onUnauthorized = (fn: () => void) => { unauthorized = fn; };

type Options = { method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"; body?: unknown; signal?: AbortSignal; headers?: HeadersInit };

export async function request<T>(path: string, opts: Options = {}): Promise<T> {
  const headers = new Headers(opts.headers);
  headers.set("Accept", "application/json");
  if (opts.body !== undefined) headers.set("Content-Type", "application/json");
  const token = tokenStore.get();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const res = await fetch(`${BASE_URL}${path}`, {
    method: opts.method ?? "GET", headers, signal: opts.signal, credentials: "include",
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  if (res.status === 401) unauthorized();
  if (!res.ok) {
    const problem = res.headers.get("content-type")?.includes("json") ? ((await res.json()) as ProblemDetail) : undefined;
    throw new ApiError(res.status, problem?.detail ?? problem?.title ?? `HTTP ${res.status}`, problem);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;   // the single trusted cast; swap for zod parsing if you want runtime checks
}
```

Feature modules then expose small typed functions, e.g. `inventoryApi.list(warehouseId, signal)`,
`ordersApi.create(body, idempotencyKey)`, `ordersApi.search(query, signal)` returning `Page<OrderSummary>`.
Build a `toQuery(params)` helper with `URLSearchParams` that skips `undefined`/empty values.

Why this shape: one place for base URL, headers, auth, error mapping and (optionally) validation; features get
small typed functions; tests mock at the network level with MSW ([09-testing/frontend-testing.md](../09-testing/frontend-testing.md)).

---

## 4. Loading, error and empty states — every time

Every data view has **four** states. Design all four before you ship.

| State | Show | Don't |
|---|---|---|
| loading | skeleton/spinner, keep layout stable | flash the empty state first |
| error | human message + retry; 403 → "no access"; 404 → "not found" | raw stack trace; silent failure |
| empty | helpful message + primary action ("Receive stock") | blank screen |
| success | the data | — |

### Option: TanStack Query (recommended once you have more than a few screens)

It handles caching, dedupe, retries, refetch on focus, cancellation and invalidation — what a hand-written `useAsync` lacks.

```tsx
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

export function useInventory(warehouseId: number, q: string) {
  return useQuery({
    queryKey: ["warehouses", warehouseId, "inventory", { q }],
    queryFn: ({ signal }) => inventoryApi.list(warehouseId, signal, { q }),   // signal → auto-cancel
  });
}

export function useAdjustStock(warehouseId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: AdjustmentRequest) => inventoryApi.adjust(warehouseId, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["warehouses", warehouseId, "inventory"] }),
  });
}
// In the page: if (inv.isPending) … else if (inv.isError) … else if (inv.data.length === 0) … else render
```

(TanStack Query v5 API: `isPending`, object-form arguments, `invalidateQueries({ queryKey })`.) The low-stock widget can use
`refetchInterval: 30_000` instead of a hand-written polling hook.

---

## 5. The login flow

```
[LoginPage] --POST /api/auth/login {email,password}--> [Spring]
     <-- 200 { accessToken, expiresIn, user }  + Set-Cookie: refresh_token=…; HttpOnly; Secure; SameSite=Strict; Path=/api/auth
tokenStore.set(accessToken); setUser(user); navigate(from ?? "/warehouses")

On page reload (memory wiped):
[AuthProvider mount] --POST /api/auth/refresh (cookie sent automatically)--> 200 { accessToken, user } | 401 → anonymous
```

`AuthProvider` (from [02-hooks §5](./02-hooks.md#5-context)) tracks `status: "checking" | "authenticated" | "anonymous"`,
calls refresh once on mount, and registers `onUnauthorized(() => { tokenStore.set(null); setUser(null); })`.
If FlowGrid M1 only issued access tokens (no refresh endpoint), decide consciously: add refresh now, or use the simpler
baseline in §7 and write down the trade-off.

ForgeCI (Week 14/16) logs users in with **GitHub OAuth**: the browser is redirected to GitHub, GitHub redirects back to
your API's callback, and the API issues its own session/token — the SPA never sees the GitHub client secret.

---

## 6. Protected routes and role-aware UI

```tsx
// src/features/auth/RequireAuth.tsx
import { Navigate, Outlet, useLocation } from "react-router-dom";

export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === "checking") return <p>Loading…</p>;
  if (status === "anonymous") return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}
```

FlowGrid roles are ADMIN, OPS_MANAGER, WAREHOUSE_ASSOCIATE, VIEWER. Map roles to **permissions** rather than comparing
role names all over the UI:

```ts
// src/features/auth/permissions.ts
export type Role = "ADMIN" | "OPS_MANAGER" | "WAREHOUSE_ASSOCIATE" | "VIEWER";
export type Permission = "inventory:adjust" | "order:cancel" | "pick:perform" | "transfer:create" | "user:manage";

const GRANTS: Record<Role, readonly Permission[]> = {
  ADMIN: ["inventory:adjust", "order:cancel", "pick:perform", "transfer:create", "user:manage"],
  OPS_MANAGER: ["inventory:adjust", "order:cancel", "pick:perform", "transfer:create"],
  WAREHOUSE_ASSOCIATE: ["pick:perform"],
  VIEWER: [],
};

export const can = (roles: readonly Role[], p: Permission) => roles.some((r) => GRANTS[r].includes(p));
```

```tsx
export function RequirePermission({ perm, children }: { perm: Permission; children: React.ReactNode }) {
  const { user } = useAuth();
  return user && can(user.roles, perm) ? <>{children}</> : <p role="alert">You don't have access to this page.</p>;
}

// In OrderDetailPage
{user && can(user.roles, "order:cancel") && <button type="button" onClick={cancel}>Cancel order</button>}
```

(The grants above are an example; the source of truth is your backend's authorization rules — mirror them, and
consider exposing `GET /api/me` with the user's effective permissions so the UI can't drift.)
FlagForge (Week 23) adds a twist: roles are **per organization**, so permission checks take the org id from the route.

**Role-aware UI is UX, not security.** Anyone can edit JS in DevTools or call the API with `curl`. The backend must
enforce every permission, and FlowGrid needs authorization tests proving a VIEWER gets **403** on
`POST /api/inventory/adjustments` ([09-testing/spring-testing.md](../09-testing/spring-testing.md)).

---

## 7. Token storage trade-offs

| Where | Can XSS steal it? | CSRF risk? | Survives reload? | Notes |
|---|---|---|---|---|
| **Memory** (JS variable) | Not readable after the fact, but XSS can still make requests while the page is open | No (sent manually in a header) | ❌ | Pair with a refresh cookie |
| **`localStorage` / `sessionStorage`** | ✅ yes — any injected script reads it | No | ✅ (session: per tab) | Simplest; a long-lived token in `localStorage` is the riskiest common choice |
| **httpOnly + Secure cookie** | ❌ JS can't read it | ✅ yes — browser sends it automatically | ✅ | Needs `SameSite` and CSRF protection |

**A defensible design:** short-lived access token (≈ 15 min) **in memory**, sent as `Authorization: Bearer`; long-lived
refresh token in an **httpOnly, Secure, `SameSite=Strict` cookie scoped to `Path=/api/auth`**. Only the refresh endpoint
relies on the cookie.

A simpler acceptable baseline, *if you state the trade-off honestly*: access token in `sessionStorage`/`localStorage`,
short expiry, no `dangerouslySetInnerHTML`, a Content-Security-Policy. Know why it's weaker.

### CSRF in one paragraph

CSRF = a malicious site makes the victim's browser send a request to your API, and the browser **attaches cookies
automatically**. It only matters when auth rides on cookies. Defences: `SameSite=Lax/Strict` cookies, a CSRF token
(Spring Security's `CookieCsrfTokenRepository` sets an `XSRF-TOKEN` cookie; the SPA echoes it in an `X-XSRF-TOKEN`
header — see the Spring Security reference on CSRF for single-page applications), and checking `Origin`.
Bearer tokens in headers aren't sent automatically, which is why pure header-token APIs commonly disable CSRF
protection — and why that's **only** safe if no cookie authenticates anything.

### XSS reminders
React escapes text by default. Risks: `dangerouslySetInnerHTML` (ForgeCI build logs contain arbitrary program output —
render them as **text**, and if you add ANSI colours, parse them into spans yourself rather than injecting HTML),
`href={userInput}` allowing `javascript:` URLs, and third-party scripts.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `fetch` calls scattered in components | one client module + feature API modules |
| Secrets in `VITE_*` vars | backend-only config |
| `allowedOrigins("*")` with credentials | explicit origins per environment |
| Hiding buttons as "authorization" | enforce on the server; test 403s |
| Long-lived JWT in `localStorage` with no expiry | short expiry + refresh flow |
| "Fixing" CORS errors with `*` | understand origin and preflight |
| Ignoring 401 mid-session | global `onUnauthorized` → logout/redirect |

---

## Interview Q&A

<details><summary>Where do you store the JWT and why?</summary>

Access token in memory, sent as a Bearer header; refresh token in an httpOnly, Secure, SameSite=Strict cookie scoped to
the refresh path. That keeps the long-lived secret out of reach of JS (XSS) and limits CSRF exposure to one endpoint
protected by SameSite. `localStorage` is simpler but any XSS can exfiltrate the token.
</details>

<details><summary>What is CORS and how did you configure it?</summary>

A browser mechanism that blocks JS from reading cross-origin responses unless the server allows it via
`Access-Control-Allow-*` headers, with a preflight OPTIONS for non-simple requests. In dev I used Vite's proxy; where
origins differ I registered a `CorsConfigurationSource` with explicit origins and allowed headers (including
`Idempotency-Key`) and enabled `http.cors()`. In production nginx serves both SPA and `/api` from one origin.
</details>

<details><summary>What's CSRF and does your app need protection?</summary>

A cross-site request that rides on automatically-sent cookies. Endpoints authenticated only by a Bearer header aren't
vulnerable; a cookie-based refresh endpoint is protected by SameSite=Strict (and can add a CSRF token).
</details>

<details><summary>If the UI hides the Cancel button for viewers, is that enough?</summary>

No. It's UX. The API enforces authorization and I have tests asserting a VIEWER gets 403.
</details>

<details><summary>How are Vite env vars different from Spring properties?</summary>

Vite inlines `VITE_*` values into the JS bundle at build time — they're public and require a rebuild to change.
Spring reads properties at runtime from files/env vars and can hold secrets because they never leave the server.
</details>

---

## Mastery checklist

- [ ] Configure Vite proxy **and** Spring CORS; reproduce and explain a CORS failure.
- [ ] Build `request<T>` + `ApiError` + feature API modules with no `any`.
- [ ] Every FlowGrid dashboard screen has loading/error/empty states.
- [ ] (Optional) Move server data to TanStack Query with invalidation after mutations.
- [ ] Login, reload behaviour, logout and global 401 handling work.
- [ ] `RequireAuth`, `RequirePermission` and `can()` mirror the backend's rules.
- [ ] Explain the token-storage table and CSRF out loud in ≤ 2 minutes.
