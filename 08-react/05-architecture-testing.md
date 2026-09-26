# 05 — Frontend Architecture and Testing

> Weeks 17–18 · ≈ 4 hours. How to organise TeamBoard's frontend so it stays readable at 50+ components, where state
> belongs, basic performance hygiene, and how testing fits in. Full testing guide: [09-testing/frontend-testing.md](../09-testing/frontend-testing.md).

---

## 1. Folder architecture: by feature, not by type

**By type** (`components/`, `hooks/`, `services/`, `pages/`) scatters one feature across five folders.
**By feature** keeps what changes together, together — the same idea as package-by-feature in Spring.

```
teamboard-web/
├── index.html
├── vite.config.ts
├── .env.development
└── src/
    ├── main.tsx                     # providers + RouterProvider
    ├── app/
    │   ├── router.tsx               # route tree
    │   ├── providers.tsx            # QueryClient, AuthProvider
    │   └── Layout.tsx               # top nav, <Outlet/>
    ├── features/
    │   ├── auth/
    │   │   ├── AuthContext.tsx
    │   │   ├── authApi.ts
    │   │   ├── LoginPage.tsx
    │   │   ├── RequireAuth.tsx
    │   │   ├── roles.ts
    │   │   └── roles.test.ts
    │   ├── issues/
    │   │   ├── api.ts               # issuesApi
    │   │   ├── types.ts             # Issue, IssueStatus, CreateIssueRequest
    │   │   ├── hooks.ts             # useBoard, useMoveIssue
    │   │   ├── BoardPage.tsx
    │   │   ├── IssueForm.tsx
    │   │   ├── IssueForm.test.tsx
    │   │   └── components/          # IssueCard, Lane (private to the feature)
    │   ├── projects/
    │   ├── comments/
    │   └── audit/
    ├── shared/
    │   ├── api/client.ts            # request<T>, ApiError, tokenStore
    │   ├── ui/                      # Button, ErrorPanel, Spinner, EmptyState — no business logic
    │   ├── hooks/                   # useDebouncedValue, usePolling
    │   └── types.ts                 # Page<T>, AsyncState<T>
    └── test/
        ├── setup.ts                 # jest-dom, MSW server lifecycle
        ├── server.ts                # MSW handlers
        └── render.tsx               # renderWithProviders helper
```

Rules:
1. `features/X` may import from `shared/`; `shared/` never imports from `features/`.
2. Features import each other only through a small public surface (e.g. `features/auth/index.ts` exporting `useAuth`, `RequireRole`).
3. Tests live next to the code they test.
4. Page components (routes) compose; leaf components render. Keep fetching in pages/hooks, not in `shared/ui`.

The PulseWatch dashboard follows the same layout with `features/monitors`, `features/incidents`, `features/status`.

---

## 2. Where does state live?

| Kind of state | Example | Where |
|---|---|---|
| **Server state** (cached copy of backend data) | issues, monitors, members | TanStack Query or `useAsync` in the page/feature hook |
| **URL state** | filters, search, page, selected tab | `useSearchParams` / route params |
| **Global client state** | current user, theme | Context (low-frequency) |
| **Local UI state** | modal open, form fields, hover | `useState` in the component |
| **Derived** | filtered issues, counts per lane | compute during render (maybe `useMemo`) |

The most common architectural mistake is **copying server data into global client state** and then fighting to
keep it in sync. Let the server-state layer own it and invalidate after mutations.

---

## 3. Component design guidelines

- **Small and single-purpose.** If a component name needs "And", split it.
- **Props down, events up.** Callback props named `onX` (`onMove`, `onSubmit`).
- **Container vs presentational (loosely):** `BoardPage` fetches and handles states; `Board`/`Lane`/`IssueCard` receive data and render — easy to test and reuse.
- **Type the props, not the internals.** Export prop types when others compose the component.
- **Don't abstract early.** Three similar components → maybe extract. Two → leave it.
- **Accessibility is design:** labelled inputs, buttons for actions, headings in order, `role="alert"` for errors.

---

## 4. Performance basics (just enough)

1. **Measure first**: React DevTools Profiler → "Highlight updates when components render".
2. Common real fixes, in order of payoff:
   - Keep state as **low** in the tree as possible (typing in a search box shouldn't re-render the whole board).
   - Stable keys in lists.
   - Paginate / virtualise long lists (issue history, check results) — don't render 5,000 rows.
   - `React.memo` on a heavy child + `useCallback` for its callback props.
   - Code-split routes: `const SettingsPage = lazy(() => import("./SettingsPage"))` inside `<Suspense>`.
3. Network beats rendering: fewer, smaller requests; server-side pagination and filtering (your Spring API already supports them).

---

## 5. Error boundaries

A render-time exception unmounts the whole tree (white screen) unless caught by an **error boundary**.
Error boundaries must be class components (or use a small library such as `react-error-boundary`).
React Router's `errorElement` on a route is a built-in boundary for that route subtree — use it at the layout level.

```tsx
{ path: "/orgs/:orgId/projects/:projectId", element: <ProjectLayout />, errorElement: <RouteError />, children: [...] }
```

Error boundaries don't catch errors in event handlers or async code — those go through your `ApiError` handling.

---

## 6. Testing strategy for the frontend

| Level | Tool | What | Share |
|---|---|---|---|
| Unit | Vitest | pure functions: `roles.ts` (`atLeast`), reducers, `validate`, formatters | many, tiny |
| Component/integration | Vitest + React Testing Library + user-event + MSW | a page/component with real hooks and a mocked **network**: renders loading → data; submits form; shows 403/empty/error | the bulk |
| End-to-end | Playwright (optional, 1–3 tests) | login → create issue → see it on board, against the real Compose stack | few |

What to test in TeamBoard (M4 minimum, ≥ 10 tests):
- [ ] `atLeast` / `roleIn` for every role pair.
- [ ] `IssueForm`: required title error; server 400 field error lands under the field; submit disabled while saving.
- [ ] `BoardPage`: loading → lanes with counts; empty state; error state with retry.
- [ ] Moving an issue sends `PATCH` and the card appears in the new lane.
- [ ] `RequireAuth` redirects anonymous users to `/login`.
- [ ] VIEWER does not see Edit/Delete; MEMBER does.
- [ ] Login form: wrong password shows the server's message.

Code and setup for all of these: [09-testing/frontend-testing.md](../09-testing/frontend-testing.md).

---

## 7. Build and ship

```bash
npm run build        # tsc -b && vite build → dist/ (static files)
npm run preview      # serve dist/ locally to sanity-check
npx tsc --noEmit     # type-check in CI (Vite doesn't)
npm test -- --run    # vitest single run for CI
```

`dist/` is static: nginx serves it, with `try_files ... /index.html` for client routes and `location /api/` proxying to
Spring — packaged in P3 M5's Compose stack ([11-docker/compose.md](../11-docker/compose.md)).
CI for the frontend (lint, type-check, test, build) is covered in [13-cicd/pipeline-examples.md](../13-cicd/pipeline-examples.md).

---

## 8. Break it

1. Move `const [q, setQ] = useState("")` from `SearchBox` up to `BoardPage`. Profile typing — every lane re-renders per keystroke. Move it back down (or debounce).
2. Throw inside `IssueCard` render when `title` is empty. See the white screen. Add `errorElement` — now only the route shows an error.
3. Import something from `features/issues` inside `shared/ui/Button.tsx`. Explain why the dependency rule forbids it (cycles, coupling).
4. Remove `npx tsc --noEmit` from CI and introduce a type error. `vite build` still succeeds. Put it back.

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

By feature: `features/auth`, `features/issues`, `features/projects`, each with its API module, types, hooks,
pages and tests; `shared/` for the HTTP client, generic UI and hooks, with a one-way dependency rule. The router and
providers live in `app/`.
</details>

<details><summary>How do you decide where state lives?</summary>

Server data in a server-state layer (TanStack Query / a fetch hook) keyed by the request; filters and pagination in
the URL; low-frequency globals like the current user in context; everything else local; derived data computed
rather than stored.
</details>

<details><summary>How do you test React components?</summary>

With React Testing Library and user-event, querying by role/label like a user, and MSW to mock HTTP at the network
level so the real API client runs. I test visible behaviour — loading, data, errors, form validation, role-based
visibility — not internal state.
</details>

<details><summary>How would you find and fix a slow component?</summary>

Profile with React DevTools to see what renders and why; usually state is too high in the tree or a list is too long.
Push state down, paginate/virtualise, then memoize the specific hot child with stable callbacks if still needed.
</details>

---

## Mastery checklist

- [ ] TeamBoard `src/` follows the feature layout; `shared/` has no feature imports.
- [ ] I can classify every piece of state in TeamBoard into the table in §2.
- [ ] Route-level `errorElement` in place.
- [ ] ≥ 10 frontend tests passing in CI; `tsc --noEmit` in CI.
- [ ] I used the Profiler once and fixed one real unnecessary re-render.
- [ ] I can draw the frontend architecture on a whiteboard in 2 minutes.
