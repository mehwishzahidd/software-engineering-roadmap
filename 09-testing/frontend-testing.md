# Frontend Testing: Vitest + React Testing Library + MSW

> **Week 7** (FlowGrid M4 dashboard), reused in Week 16 (ForgeCI build/log views) and Week 23 (FlagForge admin).
> Architecture context: [08-react/05-architecture-testing.md](../08-react/05-architecture-testing.md).

**Guiding principle (Testing Library):** *"The more your tests resemble the way your software is used, the more
confidence they can give you."* Find things like a user does (role, label, text), interact like a user (typing,
clicking), mock the **network**, not your own modules.

---

## 1. Tooling

| Tool | Role | Java analogy |
|---|---|---|
| **Vitest** | test runner + assertions + mocks, shares Vite config | JUnit + Mockito |
| **jsdom** | simulated browser DOM in Node | — |
| **React Testing Library (RTL)** | render components, query the DOM accessibly | MockMvc for UI |
| **@testing-library/user-event** | realistic user interactions | — |
| **@testing-library/jest-dom** | DOM matchers: `toBeInTheDocument`, `toBeDisabled` | AssertJ |
| **MSW (Mock Service Worker)** | intercepts `fetch` at the network level | WireMock |

```bash
npm i -D vitest jsdom @testing-library/react @testing-library/dom @testing-library/user-event @testing-library/jest-dom msw
```

---

## 2. Configuration

```ts
// vitest.config.ts
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    env: { VITE_API_URL: "http://localhost:8080" },   // absolute URL: Node's fetch can't resolve relative paths
    css: false,
  },
});
```

```ts
// src/test/server.ts
import { setupServer } from "msw/node";
import { handlers } from "./handlers";
export const server = setupServer(...handlers);
```

```ts
// src/test/setup.ts
import "@testing-library/jest-dom/vitest";
import { afterAll, afterEach, beforeAll } from "vitest";
import { cleanup } from "@testing-library/react";
import { server } from "./server";

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));  // an unmocked request fails the test
afterEach(() => { server.resetHandlers(); cleanup(); });
afterAll(() => server.close());
```

```jsonc
// package.json
"scripts": { "test": "vitest", "test:ci": "vitest run --coverage" }
// tsconfig: add "types": ["vitest/globals", "@testing-library/jest-dom"] if you use globals
```

(`--coverage` needs `@vitest/coverage-v8`.)

---

## 3. MSW handlers (v2 API)

```ts
// src/test/handlers.ts
import { http, HttpResponse } from "msw";
import type { InventoryLevel } from "../features/inventory/types";

export const level = (over: Partial<InventoryLevel> = {}): InventoryLevel => ({
  skuId: 7, sku: "BOLT-M8-50", warehouseId: 1, onHand: 120, reserved: 0, available: 120,
  updatedAt: "2026-05-01T10:00:00Z", ...over,
});

export const handlers = [
  http.get("*/api/warehouses/:warehouseId/inventory", () =>
    HttpResponse.json([level(), level({ skuId: 8, sku: "NUT-M8", available: 4, onHand: 4 })]),
  ),
  http.post("*/api/inventory/adjustments", async ({ request }) => {
    const body = (await request.json()) as { delta: number };
    return HttpResponse.json({ id: 1, ...body }, { status: 201 });
  }),
];
```

Override per test with `server.use(...)` (reset after each test by `resetHandlers`):

```ts
server.use(
  http.post("*/api/inventory/adjustments", () =>
    HttpResponse.json(
      { title: "Conflict", status: 409, detail: "Adjustment would make on-hand negative" },
      { status: 409, headers: { "Content-Type": "application/problem+json" } },
    ),
  ),
);
```

Why MSW instead of `vi.mock("./api")`: your real `request<T>`, headers, JSON parsing and `ApiError` mapping all run.
Mocking your own module tests less and couples tests to file structure.

---

## 4. A render helper with providers

```tsx
// src/test/render.tsx
import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

export function renderWithProviders(
  ui: React.ReactElement,
  { route = "/", path = "*", user = opsManager }: { route?: string; path?: string; user?: CurrentUser | null } = {},
) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } }); // fresh per test, no retries
  return render(
    <QueryClientProvider client={queryClient}>
      <TestAuthProvider user={user}>
        <MemoryRouter initialEntries={[route]}>
          <Routes><Route path={path} element={ui} /></Routes>
        </MemoryRouter>
      </TestAuthProvider>
    </QueryClientProvider>,
  );
}
```

`TestAuthProvider` supplies a fixed `useAuth()` value. Drop `QueryClientProvider` if you don't use TanStack Query.

---

## 5. Component test patterns

### Loading → data, empty, error

```tsx
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "../../test/server";
import { renderWithProviders } from "../../test/render";
import { InventoryPage } from "./InventoryPage";

const at = { route: "/warehouses/1", path: "/warehouses/:warehouseId" };

test("shows inventory rows and flags low stock", async () => {
  renderWithProviders(<InventoryPage />, at);

  expect(screen.getByText(/loading/i)).toBeInTheDocument();
  const row = (await screen.findByText("NUT-M8")).closest("tr")!;        // findBy* waits (default 1 s)
  expect(row).toHaveTextContent("4");
  expect(screen.getByText("BOLT-M8-50")).toBeInTheDocument();
});

test("shows empty state when the warehouse has no stock", async () => {
  server.use(http.get("*/api/warehouses/:warehouseId/inventory", () => HttpResponse.json([])));
  renderWithProviders(<InventoryPage />, at);
  expect(await screen.findByText(/no stock recorded/i)).toBeInTheDocument();
});

test("shows error and retries", async () => {
  let calls = 0;
  server.use(http.get("*/api/warehouses/:warehouseId/inventory", () =>
    ++calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([])));
  const user = userEvent.setup();
  renderWithProviders(<InventoryPage />, at);

  expect(await screen.findByRole("alert")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: /retry/i }));
  expect(await screen.findByText(/no stock recorded/i)).toBeInTheDocument();
});
```

### Form: client validation and server errors

```tsx
test("requires a non-zero quantity and does not submit", async () => {
  const onSubmit = vi.fn();
  const user = userEvent.setup();
  render(<AdjustmentForm onSubmit={onSubmit} />);

  await user.click(screen.getByRole("button", { name: /save adjustment/i }));

  expect(screen.getByText(/non-zero whole number/i)).toBeInTheDocument();
  expect(onSubmit).not.toHaveBeenCalled();
});

test("shows a 409 conflict from the server as a form alert", async () => {
  server.use(http.post("*/api/inventory/adjustments", () =>
    HttpResponse.json({ status: 409, title: "Conflict", detail: "Adjustment would make on-hand negative" },
                      { status: 409, headers: { "Content-Type": "application/problem+json" } })));
  const user = userEvent.setup();
  renderWithProviders(<NewAdjustmentPage />, { route: "/warehouses/1/adjustments/new", path: "/warehouses/:warehouseId/adjustments/new" });

  // fill SKU, quantity -500, reason … then:
  await user.click(screen.getByRole("button", { name: /save adjustment/i }));
  expect(await screen.findByRole("alert")).toHaveTextContent(/on-hand negative/i);
});
```

### Role-aware UI (table-driven)

```tsx
test.each([
  ["VIEWER", false],
  ["WAREHOUSE_ASSOCIATE", false],
  ["OPS_MANAGER", true],
  ["ADMIN", true],
] as const)("%s sees Cancel order: %s", async (role, visible) => {
  renderWithProviders(<OrderDetailPage />, { route: "/warehouses/1/orders/5",
    path: "/warehouses/:warehouseId/orders/:orderId", user: userWithRoles([role]) });
  await screen.findByRole("heading", { name: /SO-1005/ });
  expect(screen.queryByRole("button", { name: /cancel order/i }) !== null).toBe(visible);
});
```

(This proves UX. The backend 403 tests in [spring-testing.md §4](./spring-testing.md#4-security-tests) prove security.)

---

## 6. Queries: which one?

Priority (most → least preferred):

1. `getByRole(role, { name })` — buttons, headings, textboxes, links, regions, alerts, rows
2. `getByLabelText` — form fields
3. `getByPlaceholderText`, `getByText`, `getByDisplayValue`
4. `getByAltText`, `getByTitle`
5. `getByTestId` — last resort

| Variant | No match | Many matches | Async |
|---|---|---|---|
| `getBy…` | throws | throws | no |
| `queryBy…` | returns `null` (use for "not present") | throws | no |
| `findBy…` | rejects after timeout | rejects | **yes** — waits |
| `getAllBy…` / `queryAllBy…` / `findAllBy…` | arrays | | |

Use `findBy*` (or `waitFor`) for anything that appears after a fetch. Never `setTimeout` in tests.

---

## 7. Testing hooks, reducers and streams

Most hooks are best tested **through a component**. For reusable hooks, use `renderHook`:

```ts
import { act, renderHook } from "@testing-library/react";
import { useDebouncedValue } from "./useDebouncedValue";

test("debounces value changes", () => {
  vi.useFakeTimers();
  const { result, rerender } = renderHook(({ v }) => useDebouncedValue(v, 300), { initialProps: { v: "bo" } });

  rerender({ v: "bolt" });
  expect(result.current).toBe("bo");              // not yet

  act(() => { vi.advanceTimersByTime(300); });
  expect(result.current).toBe("bolt");

  vi.useRealTimers();
});
```

With fake timers **and** user-event, pass `userEvent.setup({ advanceTimers: vi.advanceTimersByTime })`.

Pure logic needs no React at all — the pick-screen reducer and `can()`:

```ts
test("scanning a SKU that is not on the pick list sets an error", () => {
  const s = reducer({ remaining: { "BOLT-M8-50": 1 }, lastScan: null, error: null }, { type: "scanned", sku: "NUT-M8" });
  expect(s.error).toMatch(/not on this pick list/);
  expect(s.remaining["BOLT-M8-50"]).toBe(1);
});
```

**SSE (Week 16):** jsdom has no `EventSource`. Inject the stream behind a tiny interface (or stub `globalThis.EventSource`
with a fake class that lets the test `emit("log", data)`), then assert the log view renders lines once, in order, and
de-duplicates a replayed chunk after a simulated reconnect.

---

## 8. What NOT to test

| Don't | Why | Instead |
|---|---|---|
| Internal state values (`useState` contents) | implementation detail | visible output |
| That a child received certain props | couples to structure | what the user sees |
| CSS classes / styles | brittle; not behaviour | accessible state (`toBeDisabled`, `aria-invalid`) |
| Snapshot tests of whole pages | rubber-stamped on every change | targeted assertions |
| React / React Router / TanStack Query themselves | already tested | your usage of them |
| `vi.mock` of your own API module for every test | skips the client code | MSW |
| Every trivial presentational component | low value | cover via page tests |

---

## Break it

1. Change `onUnhandledRequest: "error"` to `"bypass"` and remove a handler — the test hangs or passes vacuously. Put it back.
2. Replace `findByText` with `getByText` right after render — fails because data isn't loaded yet. Explain.
3. Use `fireEvent.change` instead of `user.type` on a field with `onKeyDown` logic (barcode scanner input) — the handler never runs.
4. Enable retries in the test `QueryClient` → the error test becomes slow or flaky. Why default `retry: false` in tests?
5. Break the `<label htmlFor>` in `AdjustmentForm` — `getByLabelText` fails. The test just caught an accessibility bug.

---

## Interview Q&A

<details><summary>How do you test React components?</summary>

Vitest + React Testing Library: render the component with its providers, query by role/label as a user would,
interact with user-event, and assert on visible output. HTTP is mocked at the network level with MSW so my real API
client runs. Pure logic such as reducers and permission checks is unit-tested without React.
</details>

<details><summary><code>getBy</code> vs <code>queryBy</code> vs <code>findBy</code>?</summary>

`getBy` throws if not found — for things that should be there now. `queryBy` returns null — for asserting absence.
`findBy` returns a promise that waits — for things that appear asynchronously after a fetch.
</details>

<details><summary>Why MSW instead of mocking <code>fetch</code> or your API module?</summary>

It intercepts at the network boundary so everything above it — URL building, headers, JSON parsing, error mapping — is
exercised. It's the frontend equivalent of WireMock.
</details>

<details><summary>What do you avoid testing on the frontend?</summary>

Implementation details: internal state, props passed to children, CSS classes, big snapshots, and library behaviour.
Those tests break on refactors without catching real bugs.
</details>

---

## Mastery checklist

- [ ] Vitest + jsdom + RTL + MSW configured; `npm run test:ci` passes in CI.
- [ ] Inventory page: loading → data, empty, error + retry.
- [ ] Adjustment form: client validation, server 400 field error, 409 conflict, disabled while submitting.
- [ ] Role-aware UI table for all four FlowGrid roles; `can()` unit-tested.
- [ ] One hook test with `renderHook` + fake timers; one reducer test.
- [ ] (Week 16) Log-view test with a fake `EventSource`, including a replayed chunk.
- [ ] I can explain query priority and `get/query/find` without notes.
