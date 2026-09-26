# Frontend Testing: Vitest + React Testing Library + MSW

> **Week 17** · P3 TeamBoard M4 (≥ 10 meaningful tests), reused for the P4 PulseWatch dashboard.
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

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));  // unmocked request = failing test
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
import type { Issue } from "../features/issues/types";

export const issueFixture = (over: Partial<Issue> = {}): Issue => ({
  id: 1, title: "Login fails on Safari", description: null, status: "TODO",
  createdAt: "2026-05-01T10:00:00Z", assignee: null, ...over,
});

export const handlers = [
  http.get("*/api/projects/:projectId/issues/board", () =>
    HttpResponse.json([issueFixture(), issueFixture({ id: 2, title: "Add labels", status: "DONE" })]),
  ),
  http.patch("*/api/issues/:id/status", async ({ params, request }) => {
    const { status } = (await request.json()) as { status: Issue["status"] };
    return HttpResponse.json(issueFixture({ id: Number(params.id), status }));
  }),
];
```

Override per test with `server.use(...)` (reset after each test by `resetHandlers`):

```ts
server.use(
  http.post("*/api/projects/:projectId/issues", () =>
    HttpResponse.json(
      { title: "Validation failed", status: 400, errors: { title: "must not be blank" } },
      { status: 400, headers: { "Content-Type": "application/problem+json" } },
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
  { route = "/", path = "*", user = memberUser }: { route?: string; path?: string; user?: CurrentUser | null } = {},
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

## 5. Component tests

### Board: loading → data, empty, error

```tsx
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "../../test/server";
import { renderWithProviders } from "../../test/render";
import { BoardPage } from "./BoardPage";

const route = { route: "/orgs/1/projects/5", path: "/orgs/:orgId/projects/:projectId" };

test("shows issues in their status lanes", async () => {
  renderWithProviders(<BoardPage />, route);

  expect(screen.getByText(/loading/i)).toBeInTheDocument();
  const todo = await screen.findByRole("region", { name: /todo/i });   // findBy* waits (default 1s)
  expect(within(todo).getByText("Login fails on Safari")).toBeInTheDocument();
  expect(within(screen.getByRole("region", { name: /done/i })).getByText("Add labels")).toBeInTheDocument();
});

test("shows empty state when project has no issues", async () => {
  server.use(http.get("*/api/projects/:projectId/issues/board", () => HttpResponse.json([])));
  renderWithProviders(<BoardPage />, route);
  expect(await screen.findByText(/no issues yet/i)).toBeInTheDocument();
});

test("shows error and retries", async () => {
  let calls = 0;
  server.use(http.get("*/api/projects/:projectId/issues/board", () => {
    calls++;
    return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json([]);
  }));
  const user = userEvent.setup();
  renderWithProviders(<BoardPage />, route);

  expect(await screen.findByRole("alert")).toHaveTextContent(/500|went wrong/i);
  await user.click(screen.getByRole("button", { name: /retry/i }));
  expect(await screen.findByText(/no issues yet/i)).toBeInTheDocument();
});
```

(`<section aria-labelledby>` gives a lane the implicit `region` role with an accessible name — accessible markup makes tests easy.)

### Form: client validation and server errors

```tsx
test("shows required error and does not submit", async () => {
  const onSubmit = vi.fn();
  const user = userEvent.setup();
  render(<IssueForm onSubmit={onSubmit} members={[]} />);

  await user.click(screen.getByRole("button", { name: /save/i }));

  expect(screen.getByText(/title is required/i)).toBeInTheDocument();
  expect(onSubmit).not.toHaveBeenCalled();
});

test("maps server field errors onto the field", async () => {
  server.use(http.post("*/api/projects/:projectId/issues", () =>
    HttpResponse.json({ status: 400, title: "Validation failed", errors: { title: "must not be blank" } },
                      { status: 400, headers: { "Content-Type": "application/problem+json" } })));
  const user = userEvent.setup();
  renderWithProviders(<NewIssuePage />, { route: "/orgs/1/projects/5/issues/new", path: "/orgs/:orgId/projects/:projectId/issues/new" });

  await user.type(screen.getByLabelText(/title/i), "   x");
  await user.click(screen.getByRole("button", { name: /save/i }));

  expect(await screen.findByText("must not be blank")).toBeInTheDocument();
});
```

### Role-aware UI

```tsx
test.each([
  ["VIEWER", false],
  ["MEMBER", true],
  ["ADMIN", true],
] as const)("%s sees edit button: %s", async (role, visible) => {
  renderWithProviders(<IssueDetailPage />, { route: "/orgs/1/projects/5/issues/1",
    path: "/orgs/:orgId/projects/:projectId/issues/:issueId", user: userWithRole(1, role) });
  await screen.findByRole("heading", { name: /login fails/i });
  expect(screen.queryByRole("button", { name: /edit/i }) !== null).toBe(visible);
});
```

(Remember: this proves UX. The backend 403 tests in [spring-testing.md §4](./spring-testing.md#4-security-tests) prove security.)

---

## 6. Queries: which one?

Priority (most → least preferred):

1. `getByRole(role, { name })` — buttons, headings, textboxes, links, regions, alerts
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

## 7. Testing hooks

Most hooks are best tested **through a component**. For reusable hooks, use `renderHook`:

```ts
import { act, renderHook } from "@testing-library/react";
import { useDebouncedValue } from "./useDebouncedValue";

test("debounces value changes", () => {
  vi.useFakeTimers();
  const { result, rerender } = renderHook(({ v }) => useDebouncedValue(v, 300), { initialProps: { v: "a" } });

  rerender({ v: "ab" });
  expect(result.current).toBe("a");               // not yet

  act(() => { vi.advanceTimersByTime(300); });
  expect(result.current).toBe("ab");

  vi.useRealTimers();
});
```

With fake timers **and** user-event, pass `userEvent.setup({ advanceTimers: vi.advanceTimersByTime })`.

Pure logic (`atLeast`, reducers, `validate`) needs no React at all:

```ts
test.each([
  ["OWNER", "ADMIN", true], ["ADMIN", "ADMIN", true], ["MEMBER", "ADMIN", false], ["VIEWER", "MEMBER", false],
] as const)("atLeast(%s, %s) = %s", (role, min, expected) => {
  expect(atLeast(role, min)).toBe(expected);
});
```

---

## 8. What NOT to test

| Don't | Why | Instead |
|---|---|---|
| Internal state values (`useState` contents) | implementation detail | visible output |
| That a child component received certain props | couples to structure | what the user sees |
| CSS classes / styles | brittle; not behaviour | accessible state (`toBeDisabled`, `aria-invalid`) |
| Snapshot tests of whole pages | rubber-stamped on every change | targeted assertions |
| React / React Router / TanStack Query themselves | already tested | your usage of them |
| `vi.mock` of your own API module for every test | skips the client code | MSW |
| Every trivial presentational component | low value | cover via page tests |

---

## Break it

1. Change `onUnhandledRequest: "error"` to `"bypass"` and remove a handler — the test hangs or passes vacuously. Put it back.
2. Replace `findByText` with `getByText` right after render — fails because data isn't loaded yet. Explain.
3. Use `fireEvent.change` instead of `user.type` on a field with `onKeyDown` logic — handler never runs.
4. Enable retries in the test `QueryClient` → the error test becomes slow/flaky. Why default `retry: false` in tests?
5. Break the `<label htmlFor>` in `IssueForm` — `getByLabelText` fails. The test just caught an accessibility bug.

---

## Interview Q&A

<details><summary>How do you test React components?</summary>

Vitest + React Testing Library: render the component with its providers, query by role/label as a user would,
interact with user-event, and assert on visible output. HTTP is mocked at the network level with MSW so my real API
client runs. Pure logic is unit-tested without React.
</details>

<details><summary><code>getBy</code> vs <code>queryBy</code> vs <code>findBy</code>?</summary>

`getBy` throws if not found — for things that should be there now. `queryBy` returns null — for asserting absence.
`findBy` returns a promise that waits — for things that appear asynchronously after a fetch.
</details>

<details><summary>Why MSW instead of mocking <code>fetch</code> or your API module?</summary>

It intercepts at the network boundary so everything above it — URL building, headers, JSON parsing, error mapping — is
exercised. The same handlers can back the dev server and tests. It's the frontend equivalent of WireMock.
</details>

<details><summary>What do you avoid testing on the frontend?</summary>

Implementation details: internal state, props passed to children, CSS classes, big snapshots, and library behaviour.
Those tests break on refactors without catching real bugs.
</details>

---

## Mastery checklist

- [ ] Vitest + jsdom + RTL + MSW configured; `npm run test:ci` passes in CI.
- [ ] Board tests: loading → data, empty, error + retry.
- [ ] Form tests: client validation, server field errors, disabled while submitting.
- [ ] Role-aware UI test table for all four roles; `atLeast` unit-tested.
- [ ] One hook test with `renderHook` + fake timers.
- [ ] I can explain query priority and `get/query/find` without notes.
