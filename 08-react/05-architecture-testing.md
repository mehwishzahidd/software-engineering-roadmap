# 05 — Frontend Architecture and Testing

> Week 7 (FlowGrid M4), revisited Week 16 (ForgeCI's data-heavy live views) and Week 23 (FlagForge admin).
> ≈ 1 hour of reading. How to organise a dashboard so it stays readable at 50+ components, where state belongs,
> performance basics for long lists and streams, and how testing fits in. Full testing guide: [09-testing/frontend-testing.md](../09-testing/frontend-testing.md).

---

## 1. Folder architecture: by feature, not by type

**By type** (`components/`, `hooks/`, `services/`, `pages/`) scatters one feature across five folders.
**By feature** keeps what changes together, together — the same idea as package-by-feature in Spring.

```
flowgrid-web/
├── index.html
├── vite.config.ts
├── .env.development
└── src/
    ├── main.tsx                     # providers + RouterProvider
    ├── app/
    │   ├── router.tsx               # route tree
    │   ├── providers.tsx            # QueryClient, AuthProvider
    │   └── Layout.tsx               # top nav, warehouse switcher, <Outlet/>
    ├── features/
    │   ├── auth/                    # AuthContext, LoginPage, RequireAuth, permissions.ts (+ tests)
    │   ├── inventory/               # api.ts, types.ts, hooks.ts, InventoryPage.tsx, AdjustmentForm.tsx (+ tests)
    │   ├── orders/                  # OrderListPage, OrderDetailPage, components/OrderCard.tsx
    │   ├── fulfillment/             # PickPackBoardPage, pick reducer
    │   └── transfers/
    ├── shared/
    │   ├── api/client.ts            # request<T>, ApiError, tokenStore
    │   ├── ui/                      # Button, ErrorPanel, Spinner, EmptyState — no business logic
    │   ├── hooks/                   # useDebouncedValue, usePolling
    │   └── types.ts                 # Page<T>, AsyncState<T>
    └── test/                        # setup.ts, MSW server + handlers, renderWithProviders
```

Rules:
1. `features/X` may import from `shared/`; `shared/` never imports from `features/`.
2. Features import each other only through a small public surface (e.g. `features/auth/index.ts` exporting `useAuth`, `can`).
3. Tests live next to the code they test.
4. Page components (routes) compose and fetch; leaf components render. Keep fetching out of `shared/ui`.

ForgeCI's UI (Week 16) uses the same layout with `features/builds`, `features/jobs`, `features/logs`, `features/repos`;
FlagForge's (Week 23) with `features/flags`, `features/rules`, `features/versions`, `features/audit`.

---

## 2. Where does state live?

| Kind of state | FlowGrid example | Where |
|---|---|---|
| **Server state** (cached copy of backend data) | inventory levels, orders, pick lists | TanStack Query or `useAsync` in the page/feature hook |
| **URL state** | warehouse id, search, filters, sort, page | route params / `useSearchParams` |
| **Global client state** | current user, permissions, theme | Context (low-frequency) |
| **Local UI state** | modal open, form fields, pick-screen scans | `useState` / `useReducer` in the component |
| **Derived** | lane counts, low-stock count | compute during render (maybe `useMemo`) |
| **Streamed state** (Week 16) | live log lines, job status | a subscription hook (`useJobLog`) owned by the page |

The most common architectural mistake is **copying server data into global client state** and then fighting to keep it
in sync. Let the server-state layer own it and invalidate after mutations.

---

## 3. Component design guidelines

- **Small and single-purpose.** If a component name needs "And", split it.
- **Props down, events up.** Callback props named `onX` (`onAdvance`, `onSubmit`).
- **Container vs presentational (loosely):** `InventoryPage` fetches and handles states; `InventoryTable` receives rows and renders — easy to test and reuse.
- **Type the props, not the internals.** Export prop types when others compose the component.
- **Don't abstract early.** Three similar components → maybe extract. Two → leave it.
- **Accessibility is design:** labelled inputs, buttons for actions, headings in order, `role="alert"` for errors.

---

## 4. Performance basics (just enough)

1. **Measure first**: React DevTools Profiler → "Highlight updates when components render".
2. Common real fixes, in order of payoff:
   - Keep state as **low** in the tree as possible (typing in the SKU search shouldn't re-render the whole dashboard).
   - Stable keys in lists.
   - Paginate on the server; **virtualise** long lists (render only visible rows — e.g. `@tanstack/react-virtual`).
   - `React.memo` on a heavy child + `useCallback` for its callback props.
   - Code-split routes: `const TransfersPage = lazy(() => import("./TransfersPage"))` inside `<Suspense>`.
3. Network beats rendering: fewer, smaller requests; server-side filtering and pagination.

**Week 16 — ForgeCI live logs** are the stress test: a build can emit tens of thousands of lines. Batch incoming chunks
(append once per animation frame or per SSE message, not per line), virtualise the list, cap lines kept in memory with a
"load earlier lines" action backed by the API, and key rows by sequence number. Measure before and after with the Profiler
and record it — that's an honest, specific interview story.

---

## 5. Error boundaries

A render-time exception unmounts the whole tree (white screen) unless caught by an **error boundary**.
Error boundaries must be class components (or use a small library such as `react-error-boundary`).
React Router's `errorElement` on a route is a built-in boundary for that subtree — use it at the layout level
(see the route tree in [03 §2.1](./03-forms-routing.md#21-designing-the-route-tree)).

Error boundaries don't catch errors in event handlers or async code — those go through your `ApiError` handling.

---

## 6. Testing strategy for the frontend

| Level | Tool | What | Share |
|---|---|---|---|
| Unit | Vitest | pure functions: `can()`, reducers (pick screen), `validate`, formatters | many, tiny |
| Component/integration | Vitest + React Testing Library + user-event + MSW | a page with real hooks and a mocked **network**: loading → data; form submit; 403/409/empty/error | the bulk |
| End-to-end | Playwright (optional, 1–3 tests) | login → create order → see it on the board, against the Compose stack | few |

What to test in FlowGrid M4 (minimum ≈ 6 meaningful tests; describe them first, then write them):
- [ ] `can()` for every role × a few permissions (table-driven).
- [ ] Pick-screen reducer: scanning a SKU not on the list sets an error; over-picking is rejected.
- [ ] `InventoryPage`: loading → rows; empty state; error state with retry.
- [ ] `AdjustmentForm`: required fields; server 400 field error under the field; 409 as a form alert; submit disabled while saving.
- [ ] `RequireAuth` redirects anonymous users to `/login`.
- [ ] VIEWER sees no mutation buttons; OPS_MANAGER does.

Week 16 adds: the log view renders replayed lines once (no duplicates after reconnect). Week 23 adds: the rules editor
round-trips a rule set, and rollback asks for confirmation.

Setup and code patterns: [09-testing/frontend-testing.md](../09-testing/frontend-testing.md).

---

## 7. Build and ship

```bash
npm run build        # tsc -b && vite build → dist/ (static files)
npm run preview      # serve dist/ locally to sanity-check
npx tsc --noEmit     # type-check in CI (Vite doesn't)
npx vitest run       # single test run for CI
```

`dist/` is static: nginx serves it, with `try_files … /index.html` for client routes and `location /api/` proxying to
Spring ([11-docker/dockerfiles.md](../11-docker/dockerfiles.md)). CI for the frontend (lint, type-check, test, build) is
covered in [13-cicd/pipeline-examples.md](../13-cicd/pipeline-examples.md).

---

## 8. Break it

1. Move the SKU search `useState` from `SkuSearch` up to the dashboard root. Profile typing — everything re-renders per keystroke. Move it back down (or debounce).
2. Throw inside `OrderCard` render when `number` is empty. See the white screen. Add `errorElement` — now only that route shows an error.
3. Import something from `features/orders` inside `shared/ui/Button.tsx`. Explain why the dependency rule forbids it (cycles, coupling).
4. Remove `npx tsc --noEmit` from CI and introduce a type error. `vite build` still succeeds. Put it back.
5. (Week 16) Append log lines one `setState` per line for a 20,000-line log. Profile. Then batch per SSE message and virtualise. Record both numbers.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| Folders by type in a growing app | folders by feature |
| Global store holding server data | server-state layer + invalidation |
| One 600-line page component | extract presentational children |
| Premature `React.memo` everywhere | profile, then memo the hot path |
| Tests asserting implementation (state values, CSS classes) | assert what the user sees |
| No type-check in CI | `tsc --noEmit` step |

---

## Interview Q&A

<details><summary>How is your React app structured?</summary>

By feature: `features/auth`, `features/inventory`, `features/orders`, each with its API module, types, hooks, pages and
tests; `shared/` for the HTTP client, generic UI and hooks, with a one-way dependency rule. The router and providers
live in `app/`.
</details>

<details><summary>How do you decide where state lives?</summary>

Server data in a server-state layer keyed by the request; filters and pagination in the URL; low-frequency globals like
the current user in context; everything else local; derived data computed rather than stored.
</details>

<details><summary>How do you test React components?</summary>

With React Testing Library and user-event, querying by role/label like a user, and MSW to mock HTTP at the network
level so the real API client runs. I test visible behaviour — loading, data, errors, form validation, role-based
visibility — not internal state.
</details>

<details><summary>How would you render a very long, live log without freezing the page?</summary>

Batch incoming lines per message instead of per line, virtualise the list so only visible rows are in the DOM, cap
what's kept in memory with on-demand loading of older lines, key rows by sequence number, and measure with the
Profiler before and after.
</details>

---

## Mastery checklist

- [ ] FlowGrid `src/` follows the feature layout; `shared/` has no feature imports.
- [ ] I can classify every piece of state in the dashboard into the table in §2.
- [ ] Route-level `errorElement` in place.
- [ ] Frontend tests and `tsc --noEmit` run in CI.
- [ ] I used the Profiler once and fixed one real unnecessary re-render.
- [ ] I can draw the frontend architecture on a whiteboard in 2 minutes.
