# 04 — API Integration and Frontend Auth

> Weeks 16–17 · ≈ 5 hours. This file owns the **client side** of auth. Server-side auth (Spring Security,
> JWT signing, BCrypt, roles) lives in [05-spring-boot/05-security-jwt.md](../05-spring-boot/05-security-jwt.md).
> Build target: **P3 TeamBoard M3 + M4**.

---

## 1. Environment variables with Vite

```bash
# .env.development      (committed; non-secret defaults)
VITE_API_URL=http://localhost:8080
# .env.production       (committed; or injected in CI)
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
- Anything in the bundle is **public**. Never put API secrets, DB passwords or signing keys in `VITE_*`.
- An empty `VITE_API_URL` in production means "same origin" — nginx serves the SPA and proxies `/api` to Spring (no CORS needed; see [11-docker/compose.md](../11-docker/compose.md)).

---

## 2. CORS with Spring Boot

In development the SPA runs on `http://localhost:5173` and the API on `http://localhost:8080` — **different origins**
(scheme + host + port). The browser blocks cross-origin responses unless the server opts in with CORS headers.
For non-simple requests (e.g. `Authorization` header, `Content-Type: application/json`) the browser first sends an
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

**Option B — configure CORS in Spring Security** (needed whenever the SPA and API are really on different origins):

```java
@Configuration
public class CorsConfig {
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
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
}

// In your SecurityFilterChain:
// http.cors(Customizer.withDefaults())   // picks up the CorsConfigurationSource bean, handles preflight before auth
```

```yaml
# application-dev.yml
app:
  cors:
    allowed-origins: http://localhost:5173
```

**CORS is not security for your API** — it only controls what *browsers* let JS read. `curl` ignores it.
Authorization is still enforced by Spring Security.

> **Break it:** remove `http.cors(...)`. The browser console shows "blocked by CORS policy: No 'Access-Control-Allow-Origin' header";
> the Network tab shows the preflight `OPTIONS` failing with 401/403. `curl` to the same URL works. Explain why.

---

## 3. A typed API client layer

One module owns `fetch`. Components never call `fetch` directly.

```ts
// src/shared/api/client.ts
export type ProblemDetail = {
  type?: string; title?: string; status?: number; detail?: string; instance?: string;
  errors?: Record<string, string>;   // custom extension your Spring handler adds for field errors
};

export class ApiError extends Error {
  constructor(public readonly status: number, message: string, public readonly problem?: ProblemDetail) {
    super(message);
    this.name = "ApiError";
  }
  get fieldErrors(): Record<string, string> | undefined {
    return this.problem?.errors;
  }
}

const BASE_URL = import.meta.env.VITE_API_URL ?? "";

// Access token lives in memory (module variable), not localStorage — see §7.
let accessToken: string | null = null;
export const tokenStore = {
  get: () => accessToken,
  set: (t: string | null) => { accessToken = t; },
};

let unauthorizedHandler: () => void = () => {};
export function onUnauthorized(fn: () => void) { unauthorizedHandler = fn; }

type RequestOptions = { method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE"; body?: unknown; signal?: AbortSignal; headers?: HeadersInit };

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const headers = new Headers(opts.headers);
  headers.set("Accept", "application/json");
  if (opts.body !== undefined) headers.set("Content-Type", "application/json");
  const token = tokenStore.get();
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const res = await fetch(`${BASE_URL}${path}`, {
    method: opts.method ?? "GET",
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    signal: opts.signal,
    credentials: "include",           // send the httpOnly refresh cookie (only matters for /api/auth/*)
  });

  if (res.status === 401) unauthorizedHandler();

  if (!res.ok) {
    let problem: ProblemDetail | undefined;
    if (res.headers.get("content-type")?.includes("json")) {   // application/problem+json
      problem = (await res.json()) as ProblemDetail;
    }
    throw new ApiError(res.status, problem?.detail ?? problem?.title ?? `HTTP ${res.status}`, problem);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;     // the single trusted cast; swap for zod parsing if you want runtime checks
}
```

```ts
// src/features/issues/api.ts
import { request } from "../../shared/api/client";
import type { Issue, IssueStatus, Page } from "./types";

export type IssueQuery = { q?: string; status?: IssueStatus; page?: number; size?: number };
export type CreateIssueRequest = { title: string; description?: string; status: IssueStatus; assigneeId: number | null };

function toQuery(params: Record<string, string | number | undefined>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== "") sp.set(k, String(v));
  return sp.toString();
}

export const issuesApi = {
  list: (projectId: number, signal?: AbortSignal) =>
    request<Issue[]>(`/api/projects/${projectId}/issues/board`, { signal }),
  search: (projectId: number, query: IssueQuery, signal?: AbortSignal) =>
    request<Page<Issue>>(`/api/projects/${projectId}/issues?${toQuery(query)}`, { signal }),
  get: (id: number, signal?: AbortSignal) => request<Issue>(`/api/issues/${id}`, { signal }),
  create: (projectId: number, body: CreateIssueRequest) =>
    request<Issue>(`/api/projects/${projectId}/issues`, { method: "POST", body }),
  updateStatus: (id: number, status: IssueStatus) =>
    request<Issue>(`/api/issues/${id}/status`, { method: "PATCH", body: { status } }),
  remove: (id: number) => request<void>(`/api/issues/${id}`, { method: "DELETE" }),
};
```

Why this shape: one place for base URL, headers, auth, error mapping and (optionally) validation; features get
small typed functions; tests mock at the network level with MSW ([09-testing/frontend-testing.md](../09-testing/frontend-testing.md)).

---

## 4. Loading, error and empty states — every time

Every data view has **four** states. Design all four before you ship.

| State | Show | Don't |
|---|---|---|
| loading | skeleton/spinner, keep layout stable | flash empty state first |
| error | human message + retry; 403 → "no access"; 404 → "not found" | raw stack trace; silent failure |
| empty | helpful message + primary action ("Create issue") | blank screen |
| success | the data | — |

### Option: TanStack Query (recommended once you have >3 screens)

It handles caching, dedupe, retries, refetch on focus, cancellation and invalidation — the stuff your `useAsync` lacks.

```tsx
// main.tsx: const queryClient = new QueryClient();  <QueryClientProvider client={queryClient}>…
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

export function useBoard(projectId: number) {
  return useQuery({
    queryKey: ["projects", projectId, "board"],
    queryFn: ({ signal }) => issuesApi.list(projectId, signal),     // signal → auto-cancel
  });
}

export function useMoveIssue(projectId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: IssueStatus }) => issuesApi.updateStatus(id, status),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["projects", projectId, "board"] }),
  });
}

function BoardPage() {
  const projectId = Number(useParams().projectId);
  const board = useBoard(projectId);
  const move = useMoveIssue(projectId);
  if (board.isPending) return <BoardSkeleton />;
  if (board.isError) return <ErrorPanel error={board.error} onRetry={() => board.refetch()} />;
  if (board.data.length === 0) return <EmptyBoard />;
  return <Board issues={board.data} onMove={(id, status) => move.mutate({ id, status })} />;
}
```

(TanStack Query v5 API: `isPending`, object-form arguments, `invalidateQueries({ queryKey })`.)
PulseWatch's dashboard can use `refetchInterval: 10_000` instead of a hand-written polling hook.

---

## 5. The login flow (TeamBoard)

```
[LoginPage] --POST /api/auth/login {email,password}--> [Spring]
     <-- 200 { accessToken, expiresIn, user }  + Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth
tokenStore.set(accessToken); setUser(user); navigate(from ?? "/orgs")

On page reload (memory wiped):
[AuthProvider mount] --POST /api/auth/refresh (cookie sent automatically)--> 200 { accessToken, user } | 401 → anonymous
```

```tsx
// src/features/auth/authApi.ts
export const authApi = {
  login: (email: string, password: string) =>
    request<{ accessToken: string; user: CurrentUser }>("/api/auth/login", { method: "POST", body: { email, password } }),
  refresh: () => request<{ accessToken: string; user: CurrentUser }>("/api/auth/refresh", { method: "POST" }),
  logout: () => request<void>("/api/auth/logout", { method: "POST" }),   // server clears cookie
};
```

`AuthProvider` (from [02-hooks §5](./02-hooks.md#5-context)) grows a `status: "checking" | "authenticated" | "anonymous"`,
calls `authApi.refresh()` once on mount, and registers `onUnauthorized(() => { tokenStore.set(null); setUser(null); })`.

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

TeamBoard roles are **per organization** (`Membership(role)`): OWNER > ADMIN > MEMBER > VIEWER.

```ts
// src/features/auth/roles.ts
export const ROLE_RANK = { VIEWER: 0, MEMBER: 1, ADMIN: 2, OWNER: 3 } as const;
export type Role = keyof typeof ROLE_RANK;
export type CurrentUser = { id: number; email: string; displayName: string; memberships: { orgId: number; role: Role }[] };

export function roleIn(user: CurrentUser | null, orgId: number): Role | null {
  return user?.memberships.find((m) => m.orgId === orgId)?.role ?? null;
}
export function atLeast(role: Role | null, min: Role): boolean {
  return role !== null && ROLE_RANK[role] >= ROLE_RANK[min];
}
```

```tsx
export function RequireRole({ min, children }: { min: Role; children: React.ReactNode }) {
  const { user } = useAuth();
  const orgId = Number(useParams().orgId);
  return atLeast(roleIn(user, orgId), min) ? <>{children}</> : <p role="alert">You don't have access to this page.</p>;
}

export function useCan(min: Role): boolean {
  const { user } = useAuth();
  return atLeast(roleIn(user, Number(useParams().orgId)), min);
}

// In IssueDetailPage
const canEdit = useCan("MEMBER");
{canEdit && <button type="button" onClick={startEdit}>Edit</button>}
```

**Role-aware UI is UX, not security.** Anyone can edit JS in DevTools or call the API with `curl`. The backend must
enforce every permission (`@PreAuthorize`, per-org membership checks) and you must have backend authorization tests
proving a VIEWER gets **403** on `POST /api/projects/{id}/issues` ([09-testing/spring-testing.md](../09-testing/spring-testing.md)).

---

## 7. Token storage trade-offs

| Where | XSS can steal it? | CSRF risk? | Survives reload? | Notes |
|---|---|---|---|---|
| **Memory** (JS variable) | Not directly readable after the fact, but XSS can still make requests while the page is open | No (sent manually in header) | ❌ | Pair with a refresh cookie |
| **`localStorage` / `sessionStorage`** | ✅ yes — any injected script reads it | No | ✅ (session: per tab) | Simplest; a long-lived token in `localStorage` is the riskiest common choice |
| **httpOnly + Secure cookie** | ❌ JS can't read it | ✅ yes — browser sends it automatically | ✅ | Needs `SameSite` and CSRF protection |

**TeamBoard's choice (defensible in an interview):** short-lived access token (≈15 min) **in memory**, sent as
`Authorization: Bearer`; long-lived refresh token in an **httpOnly, Secure, `SameSite=Strict` cookie scoped to
`Path=/api/auth`**. Only the refresh endpoint relies on the cookie.

A simpler acceptable baseline for M4, *if you state the trade-off honestly*: access token in `localStorage`,
short expiry, strict output escaping, a Content-Security-Policy. Know why it's weaker.

### CSRF in one paragraph

CSRF = a malicious site makes the victim's browser send a request to your API, and the browser **attaches cookies
automatically**. It only matters when auth rides on cookies. Defences: `SameSite=Lax/Strict` cookies, a CSRF token
(Spring Security's `CookieCsrfTokenRepository` sets an `XSRF-TOKEN` cookie; the SPA echoes it in an `X-XSRF-TOKEN`
header — see the Spring Security reference section on CSRF for single-page apps), and checking `Origin`.
Bearer tokens in headers aren't sent automatically, which is why pure header-token APIs commonly disable CSRF
protection — and why that's **only** safe if no cookie authenticates anything.

### XSS reminders
React escapes text by default. Risks: `dangerouslySetInnerHTML` (e.g. rendering Markdown issue descriptions — sanitize
with a library like DOMPurify), `href={userInput}` allowing `javascript:` URLs, and third-party scripts.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `fetch` calls scattered in components | one client module + feature API modules |
| Secrets in `VITE_*` vars | backend-only config |
| `allowedOrigins("*")` with credentials | explicit origins per environment |
| Hiding buttons as "authorization" | enforce on the server; test 403s |
| Long-lived JWT in `localStorage` with no expiry | short expiry + refresh flow |
| Treating CORS errors as backend bugs to "fix" with `*` | understand origin/preflight |
| Ignoring 401 mid-session | global `onUnauthorized` → logout/redirect |

---

## Interview Q&A

<details><summary>Where do you store the JWT and why?</summary>

Access token in memory, sent as a Bearer header; refresh token in an httpOnly, Secure, SameSite=Strict cookie
scoped to the refresh path. That keeps the long-lived secret out of reach of JS (XSS) and limits CSRF exposure to one
endpoint protected by SameSite. `localStorage` is simpler but any XSS can exfiltrate the token.
</details>

<details><summary>What is CORS and how did you configure it?</summary>

A browser mechanism that blocks JS from reading cross-origin responses unless the server allows it via
`Access-Control-Allow-*` headers, with a preflight OPTIONS for non-simple requests. In dev I used Vite's proxy;
where origins differ I registered a `CorsConfigurationSource` with explicit origins and enabled `http.cors()` in
the security chain. In production nginx serves both SPA and `/api` from one origin.
</details>

<details><summary>What's CSRF and does your app need protection?</summary>

A cross-site request that rides on automatically-sent cookies. Endpoints authenticated only by a Bearer header aren't
vulnerable; my refresh endpoint uses a cookie, so it's protected by SameSite=Strict (and could add a CSRF token).
</details>

<details><summary>If the UI hides the Delete button for viewers, is that enough?</summary>

No. It's UX. The API enforces authorization with method security and per-org membership checks, and I have tests
asserting a VIEWER gets 403.
</details>

<details><summary>How are Vite env vars different from Spring properties?</summary>

Vite inlines `VITE_*` values into the JS bundle at build time — they're public and require a rebuild to change.
Spring reads properties at runtime from files/env vars and can hold secrets because they never leave the server.
</details>

---

## Mastery checklist

- [ ] Configure Vite proxy **and** Spring CORS; reproduce and explain a CORS failure.
- [ ] Build `request<T>` + `ApiError` + feature API modules with no `any`.
- [ ] Every TeamBoard screen has loading/error/empty states.
- [ ] (Optional) Migrate board data to TanStack Query with invalidation after mutations.
- [ ] Implement login, silent refresh on reload, logout, and global 401 handling.
- [ ] `RequireAuth`, `RequireRole`, `useCan` with per-org roles.
- [ ] Explain the token-storage table and CSRF out loud in ≤ 2 minutes.
