# React — Résumé Tech Defense

> **Goal:** defend "React" with truthful past context and current React 18+ competence:
> rendering model, hooks, state, effects, forms, routing, API integration, auth on the client,
> testing with Vitest + React Testing Library — all demonstrable in TeamBoard and PulseWatch.
>
> **Honesty rule:** if past work was class components or maintenance of someone else's app,
> say that plainly. "I've since rebuilt with modern function components and hooks" is a strong,
> honest bridge.

---

## Evidence in my projects

| Project | React evidence |
|---|---|
| **P3 TeamBoard** M3 (W16) | Vite + React + TS, React Router routes (`/orgs/:orgId/board`, `/issues/:id`), board view grouped by status, issue create/edit forms with validation, loading/error states |
| **P3 TeamBoard** M4 (W17) | `AuthContext` (token in memory + refresh strategy documented), `<ProtectedRoute>`, role-aware UI (`VIEWER` sees no edit buttons), Vitest + RTL tests with mocked API |
| **P3 TeamBoard** M5 (W18) | Comments, audit-log view, search + pagination UI; production build served by nginx in Compose |
| **P4 PulseWatch** M4 (W22) | Status dashboard: polling with cleanup, incident timeline, uptime % per monitor |

## Where to learn it in this repo

- [`../08-react/01-fundamentals.md`](../08-react/01-fundamentals.md) — components, JSX, props, state, lists/keys
- [`../08-react/02-hooks.md`](../08-react/02-hooks.md) — `useEffect`, `useRef`, `useMemo`, `useContext`, custom hooks
- [`../08-react/03-forms-routing.md`](../08-react/03-forms-routing.md)
- [`../08-react/04-api-integration-auth.md`](../08-react/04-api-integration-auth.md)
- [`../08-react/05-architecture-testing.md`](../08-react/05-architecture-testing.md)
- [`../09-testing/frontend-testing.md`](../09-testing/frontend-testing.md)

---

## 1. Beginner questions

<details><summary><b>B1. What is a React component?</b></summary>

A function that takes props and returns JSX describing UI. React calls it to render; when state/props change it calls it again and reconciles the difference with the DOM. Components must be pure with respect to render (no side effects in the body).
</details>

<details><summary><b>B2. Props vs state?</b></summary>

Props: inputs from the parent, read-only. State: data owned by a component that changes over time (`useState`/`useReducer`); setting it schedules a re-render. If two siblings need the same state, lift it to the nearest common parent.
</details>

<details><summary><b>B3. What is JSX?</b></summary>

Syntax that compiles to `jsx()` calls (automatic runtime) producing React elements (plain objects). Expressions in `{}`; `className` not `class`; must return a single root (or a Fragment).
</details>

<details><summary><b>B4. Why do lists need <code>key</code>?</b></summary>

Keys let reconciliation match items across renders. Use stable ids (`issue.id`), not array indices when items can reorder/insert — otherwise state (e.g. input text) sticks to the wrong row.
</details>

<details><summary><b>B5. Controlled vs uncontrolled inputs?</b></summary>

Controlled: value in React state, `onChange` updates it — easy validation. Uncontrolled: DOM keeps the value, read via ref or `FormData` — less re-rendering. TeamBoard forms are controlled for inline validation.
</details>

<details><summary><b>B6. What does <code>useEffect</code> do?</b></summary>

Synchronises a component with something external (network, timers, subscriptions, DOM APIs) after render. Dependency array controls when it re-runs; returning a function registers cleanup (runs before next effect and on unmount). In dev StrictMode, effects mount→unmount→mount once to surface missing cleanup.
</details>

## 2. Intermediate questions

<details><summary><b>I1. What triggers a re-render and how does reconciliation work?</b></summary>

A state update in the component, a parent re-render, or a context value change. React renders a new element tree, diffs by type + key, and commits minimal DOM changes. Different element type at the same position → subtree unmounted and remounted (state lost).
</details>

<details><summary><b>I2. Why are state updates "async" and what is batching?</b></summary>

`setX` schedules an update; the variable in the current render doesn't change. React 18 batches updates from events, promises and timeouts into one render (automatic batching). Use functional updates `setCount(c => c + 1)` when new state depends on previous.
</details>

<details><summary><b>I3. <code>useMemo</code>, <code>useCallback</code>, <code>React.memo</code> — when are they worth it?</b></summary>

Only when a measured render cost or referential identity matters (memoised child, effect dependency). They cost memory and complexity. Profile first with React DevTools Profiler.
</details>

<details><summary><b>I4. How do you fetch data correctly in an effect?</b></summary>

```tsx
useEffect(() => {
  const ac = new AbortController();
  setLoad({ state: "loading" });
  api.listIssues(orgId, { signal: ac.signal })
     .then(data => setLoad({ state: "success", data }))
     .catch(e => { if (!ac.signal.aborted) setLoad({ state: "error", error: e }); });
  return () => ac.abort();
}, [orgId]);
```
Handles race conditions (old response after `orgId` changes) and unmount. Or use TanStack Query for caching, dedupe, retries.
</details>

<details><summary><b>I5. <code>useRef</code> use cases?</b></summary>

Mutable box that persists across renders without causing re-render: DOM node access (focus), storing interval ids, latest value for callbacks. Not a substitute for state that should appear in the UI.
</details>

<details><summary><b>I6. Context — what is it for and what's the catch?</b></summary>

Pass values (auth user, theme) deep without prop drilling. Every consumer re-renders when the value identity changes — memoise the value object, split contexts, don't put rapidly changing data in one global context.
</details>

<details><summary><b>I7. What are custom hooks?</b></summary>

Functions starting with `use` that compose hooks to share stateful logic, e.g. `useIssues(orgId, filters)`, `useAuth()`, `usePolling(fn, ms)`. They share logic, not state (each call has its own state).
</details>

<details><summary><b>I8. Rules of hooks — why do they exist?</b></summary>

Call hooks at the top level, in the same order every render, only from components/custom hooks. React tracks hook state by call order; conditional hooks would misalign it.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "Tell me about the React work on your résumé."</b></summary>

Truthful past scope (which app, class vs hooks, what you owned). Then TeamBoard: routes, board, forms, auth, role-aware UI, tests. Show one component and its test.
Follow-up: "What would you change about its architecture now?"
</details>

<details><summary><b>R2. "How does your React frontend talk to the Spring Boot backend?"</b></summary>

Typed API client over `fetch` → JSON over HTTPS → `Authorization: Bearer` → Spring Security filter → controller → service → JPA → Postgres. Dev: Vite proxy `/api` → `localhost:8080` (avoids CORS). Prod: nginx serves the bundle and reverse-proxies `/api` → same origin. Errors come back as ProblemDetail and render inline.
</details>

<details><summary><b>R3. "Where do you store the JWT and why?"</b></summary>

Trade-offs: `localStorage` (simple, XSS-readable), memory (lost on refresh, safest vs XSS), httpOnly `Secure` `SameSite` cookie (not readable by JS, needs CSRF consideration). State what TeamBoard does and why, and what you'd do in production (httpOnly cookie + CSRF protection or short-lived access token in memory + refresh cookie).
</details>

<details><summary><b>R4. "How do you protect routes?"</b></summary>

`<ProtectedRoute>` checks auth context, redirects to `/login` with `state.from`; role-aware rendering via `can(role, action)`. Always add: the backend re-checks every request (`@PreAuthorize`, org membership) — the UI is not a security boundary.
</details>

<details><summary><b>R5. "How do you test React components?"</b></summary>

Vitest + RTL: render, query by role/label like a user, `userEvent` interactions, assert visible output; mock the API module (or MSW). Example test: VIEWER doesn't see the "Edit" button; form shows server validation error from ProblemDetail.
</details>

<details><summary><b>R6. "What's new in React 18/19 that matters to you?"</b></summary>

18: concurrent rendering, automatic batching, `createRoot`, `useTransition`/`useDeferredValue`, `useId`, StrictMode double effects in dev. 19: Actions, `useActionState`, `useOptimistic`, `use`, ref as a prop. Be honest about which you've actually used.
</details>

<details><summary><b>R7. "How would you handle a list of 10,000 issues?"</b></summary>

Server-side pagination/filtering (TeamBoard API already paginates), virtualisation if a long list is genuinely needed, debounced search, stable keys.
</details>

## 4. Practical tasks (doable live)

1. Build a controlled issue form with required title and max length, showing errors on blur.
2. Write `useDebounce(value, ms)` and use it for a search box.
3. Render issues grouped by status columns; move an issue with a button (immutable update).
4. Write `usePolling(fn, ms)` with cleanup and pause-when-tab-hidden.
5. Write an RTL test: clicking "Create" calls the API mock and shows the new issue.

## 5. Debugging questions

<details><summary><b>D1. "Too many re-renders" / infinite loop.</b></summary>

Setting state during render, or an effect whose dependency is an object/array recreated every render and which sets state. Fix: move to handler, memoise dependency, depend on primitives.
</details>

<details><summary><b>D2. Data from the previous org flashes after switching orgs.</b></summary>

Race condition: old request resolved last. Abort in cleanup or ignore stale responses; or use a query library keyed by `orgId`.
</details>

<details><summary><b>D3. In dev, every API call fires twice.</b></summary>

StrictMode double-invokes effects on mount in development. Not a production bug; ensure effects are idempotent with cleanup.
</details>

<details><summary><b>D4. Typing in one row's input changes another row after a delete.</b></summary>

Index used as `key`. Use `issue.id`.
</details>

<details><summary><b>D5. After login, the protected page redirects back to login.</b></summary>

Auth state set asynchronously and route checked before it's populated; or token not attached by the client. Add a `loading` auth state, verify `Authorization` header in the Network tab, check backend returns 401 vs 403.
</details>

## 6. Architecture questions

<details><summary><b>A1. How did you structure TeamBoard's frontend?</b></summary>

Feature folders (`features/issues`, `features/auth`), `api/` layer, shared `components/`, `hooks/`; pages compose features; server state vs UI state separated. Explain why not Redux: server state is mostly cache → fetch hooks/TanStack Query; little global client state.
</details>

<details><summary><b>A2. Server state vs client state?</b></summary>

Server state (issues, users) is owned remotely: cache, invalidate, refetch. Client state (modal open, form draft) lives in components. Mixing them in one global store causes stale data bugs.
</details>

<details><summary><b>A3. SPA vs server rendering for TeamBoard?</b></summary>

SPA (Vite) is fine: authenticated app, no SEO. SSR/Next.js helps public, SEO-sensitive, or first-paint-critical pages — e.g. PulseWatch's public status page could benefit, but a cached JSON endpoint + static SPA is enough at this scale.
</details>

## 7. Common mistakes

- Mutating state (`issues.push(x); setIssues(issues)`) → no re-render.
- Effects without cleanup (intervals, listeners, in-flight fetches).
- Missing/incorrect effect dependencies; silencing the lint rule.
- Derived data stored in state (store `issues`, compute `filtered` during render).
- Treating hidden buttons as authorization.
- Premature memoisation everywhere.

## 8. Terminology I must know

| Term | Meaning |
|---|---|
| Reconciliation | Diffing element trees to update DOM |
| Render / commit | Compute UI / apply DOM changes |
| Hook | Function using React state/lifecycle (`use*`) |
| Effect cleanup | Function returned from `useEffect` |
| Lifting state | Moving state to common ancestor |
| Prop drilling | Passing props through many layers |
| Context | Dependency-injection-like value passing |
| StrictMode | Dev checks, double effect invocation |
| Controlled component | Input value driven by state |
| Key | Identity hint for list reconciliation |
| Suspense | Declarative loading boundary |
| Hydration | Attaching React to server-rendered HTML |

## 9. When to use it

- Interactive, stateful UIs (boards, forms, dashboards) against an API.
- Teams wanting a large ecosystem and component reuse.

## 10. When NOT to use it

- Mostly static content sites (plain HTML or a static generator).
- A single small form on a server-rendered page (Thymeleaf or plain JS is enough).
- When SEO + first paint dominate and you'd need SSR anyway (choose a framework that does it).

## 11. Trade-offs

| Choice | Gain | Cost |
|---|---|---|
| SPA | Rich interactivity, simple deploy (static files) | Bundle size, initial load, SEO |
| Fetch-in-effect | No deps | Manual caching/races |
| TanStack Query | Caching, retries, invalidation | Another abstraction |
| Context for auth | Simple | Re-renders if misused |

## 12. How it interacts with the rest of my stack

- **Spring Boot:** JSON REST, JWT bearer, ProblemDetail errors, pagination `?page=&size=&sort=`.
- **CORS:** Vite proxy in dev; same-origin nginx in prod; Spring `CorsConfigurationSource` if origins differ.
- **Docker:** multi-stage build (`node:20` → `npm ci && npm run build` → `nginx:alpine` serving `dist/`).
- **AWS:** nginx on EC2 (PulseWatch) or S3 static hosting option.
- **CI:** lint, type-check, `vitest run`, build before image push.

## 13. One small hands-on exercise

**Issue board slice (TeamBoard-style), 2–3 hours.**

- [ ] `useIssues(orgId)` hook with loading/error/success, abort on change.
- [ ] Board renders three columns by status with stable keys.
- [ ] "Move right" button updates status optimistically, rolls back on API error, shows toast.
- [ ] VIEWER role renders no mutating buttons.
- [ ] 3 RTL tests: renders columns, rollback on error, viewer sees no buttons.

## 14. Mastery checklist

- [ ] Explain render → reconcile → commit
- [ ] Write effects with correct deps and cleanup; explain StrictMode double-run
- [ ] Build a controlled form with validation from scratch
- [ ] Explain token storage trade-offs honestly and what TeamBoard does
- [ ] Write RTL tests querying by role
- [ ] Trace a click to a DB row and back in TeamBoard
- [ ] Truthful 60-second answer on past React work + bridge
