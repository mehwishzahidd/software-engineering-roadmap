# 08 — React (with TypeScript)

> **Week 7** of the [ROADMAP](../ROADMAP.md) — built directly into **FlowGrid M4** (operations dashboard: inventory by
> warehouse, orders, pick/pack screens, low-stock view). Deepened in **Week 16** (ForgeCI: build list + live log view
> over SSE — streaming, data-heavy views) and **Week 23** (FlagForge: forms-heavy admin dashboard — flags, rules editor,
> versions, rollback, audit).
> Prereq: [07-javascript-typescript](../07-javascript-typescript/README.md). Don't start React until you can explain closures and the event loop.

**Scope:** enough React to build, test, secure and *explain* a real client for your own Spring Boot APIs.
Not in scope: animation libraries, CSS-in-JS debates, Next.js/SSR, Redux deep dives, micro-frontends.

**Project rule:** the snippets here teach one concept each and use FlowGrid-flavoured names. They are not your
dashboard — you design and build that yourself from the milestone spec in [18-projects/flowgrid/milestones.md](../18-projects/flowgrid/milestones.md).

---

## Files

| File | Topics | Time | Week |
|---|---|---:|---:|
| [01-fundamentals.md](./01-fundamentals.md) | Vite setup, components, JSX, props, state, events, immutability, lists & keys, conditional rendering, lifting state | 1.5 h | 7 |
| [02-hooks.md](./02-hooks.md) | `useEffect` + dependency arrays + cleanup, StrictMode double-invoke, `useRef`, `useMemo`/`useCallback`, `useReducer`, context, custom hooks | 2 h | 7 (SSE hook: 16) |
| [03-forms-routing.md](./03-forms-routing.md) | Controlled forms, validation, server errors, React Router v6 (nested routes, params, search params, navigation) | 1.5 h | 7 (forms-heavy: 23) |
| [04-api-integration-auth.md](./04-api-integration-auth.md) | Typed API client, loading/error/empty states, TanStack Query (optional), env vars with Vite, CORS with Spring Boot, token storage trade-offs, CSRF, protected routes, role-aware UI | 2 h | 7 |
| [05-architecture-testing.md](./05-architecture-testing.md) | Feature-based folder structure, state placement, component design, performance basics, testing overview (links to [09-testing/frontend-testing.md](../09-testing/frontend-testing.md)) | 1 h | 7, 16, 23 |

Week 7's learning block is ≈ 7 h and shared with TypeScript and AWS IAM/EC2, so React reading is compressed: read a
section, then build the matching piece of the FlowGrid dashboard the same day (Mon/Wed learning blocks, Tue/Thu/Sat project blocks).

---

## Mental model in one paragraph

A React component is a **function from (props, state) to UI**. When state changes, React calls your function
again ("re-render"), diffs the new element tree against the previous one, and applies the minimal DOM changes.
Each render is a snapshot: its props, state and handlers are fixed for that render (closures — see
[07/01 §5](../07-javascript-typescript/01-javascript-core.md#5-closures)). Side effects (fetching, timers, subscriptions,
SSE streams) don't belong in rendering; they go in event handlers or `useEffect`.

---

## Where React shows up

| Week | Study | Build |
|---|---|---|
| 7 | 01 → 05 | **FlowGrid M4**: dashboard (inventory by warehouse, orders, pick/pack, low stock), search/filter/sort/pagination, login + protected routes + role-aware UI, a handful of RTL tests |
| 8 | (reuse) | FlowGrid M5: production build served by nginx in the deployed Compose stack ([11-docker/compose.md](../11-docker/compose.md)) |
| 16 | 02 §8 (streaming), 05 §4 (performance) | **ForgeCI M3**: build list, build detail, live log view (SSE, thousands of lines), status badges |
| 23 | 03 (forms-heavy), 04 §6 | **FlagForge M4**: admin dashboard — flag list, rules editor, version history + rollback, audit log |

---

## Project setup (Week 7)

```bash
npm create vite@latest flowgrid-web -- --template react-ts
cd flowgrid-web
npm install
npm install react-router-dom          # v6 API (also works under v7's react-router package)
npm install zod                       # optional: runtime validation of API responses
npm install -D vitest @testing-library/react @testing-library/dom @testing-library/user-event @testing-library/jest-dom jsdom msw
npm run dev                           # http://localhost:5173
```

The frontend can live in the same GitHub repo as the FlowGrid API (`/web` folder) or its own repo — decide, and record
the decision in an ADR ([18-projects/templates/adr.md](../18-projects/templates/adr.md)).

> Version note: React 19 and React Router 7 are current at the time of writing; everything in this folder
> uses APIs that are the same in React 18 and 19 and in Router v6 and v7 (import from `react-router-dom`
> in v6; v7 re-exports from `react-router`). If a snippet fails, check the installed major version first.

---

## Interview relevance

| Likely question | Where |
|---|---|
| What triggers a re-render? What's reconciliation? | 01 |
| Why do lists need keys? Why not the index? | 01 |
| Why must state be immutable? | 01 |
| Explain `useEffect` dependency arrays and cleanup | 02 |
| Why does my effect run twice in development? | 02 |
| `useMemo` vs `useCallback` — when are they worth it? | 02 |
| Controlled vs uncontrolled inputs | 03 |
| Where do you store a JWT and why? | 04 |
| How does your frontend talk to your Spring API across origins? | 04 |
| How is your React app structured? How did you test it? | 05 |
| How do you render a live log of 50,000 lines without freezing the page? | 02, 05 |

Résumé defense drills: [17-resume-tech-defense/react.md](../17-resume-tech-defense/react.md).

---

## Resources

- **react.dev** — the official docs (the "Learn" section is excellent; read "You Might Not Need an Effect").
- **reactrouter.com** — routing docs (select your major version).
- **TanStack Query docs** — tanstack.com/query (optional).
- **Testing Library docs** — testing-library.com.
- See also [RESOURCES.md](../RESOURCES.md).

---

## Exit criteria (end of Week 7, re-checked at CP-8)

- [ ] FlowGrid dashboard works against the real API: inventory by warehouse, orders, pick/pack flow, low-stock view.
- [ ] Search/filter/sort/pagination state lives in the URL.
- [ ] No `any` in the API layer; all responses typed (optionally zod-validated).
- [ ] Every data view handles loading, error and empty states.
- [ ] Protected routes + role-aware UI (VIEWER sees no mutation controls; the backend still enforces with 403).
- [ ] At least 6 meaningful component tests with RTL + MSW; `npm test` runs in CI.
- [ ] I can explain every hop from a button click to a Postgres row and back (see [16-interview-prep/project-deep-dive.md](../16-interview-prep/project-deep-dive.md)).
