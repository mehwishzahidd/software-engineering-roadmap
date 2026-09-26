# 02 — Hooks

> Week 7 · ≈ 2 hours (§8 streaming hook: Week 16, ForgeCI). Hooks are functions that let components hold state and run effects.
> **Rules of hooks:** call them only at the top level of a component or custom hook — never in loops,
> conditions or nested functions — because React identifies hooks by **call order**. Enable `eslint-plugin-react-hooks` (Vite template does).

---

## 1. `useEffect` — synchronising with things outside React

An effect runs **after** React commits the render to the screen. Use it to sync with external systems:
network, timers, subscriptions, the DOM, `localStorage`.

```tsx
useEffect(() => {
  // setup
  return () => {
    // cleanup: runs before the next effect run and on unmount
  };
}, [dep1, dep2]); // dependency array
```

| Dependency array | Runs |
|---|---|
| omitted | after **every** render |
| `[]` | after first mount (and cleanup on unmount) |
| `[a, b]` | after mount and whenever `a` or `b` changed (`Object.is`) |

**Every reactive value (props, state, values derived from them) used inside the effect must be in the array.**
The lint rule enforces this. Lying to it causes stale closures.

### Fetching in an effect — the correct shape

```tsx
function InventoryTable({ warehouseId }: { warehouseId: number }) {
  const [state, setState] = useState<AsyncState<InventoryLevel[]>>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    setState({ status: "loading" });

    inventoryApi
      .list(warehouseId, controller.signal)
      .then((data) => setState({ status: "success", data }))
      .catch((err: unknown) => {
        if (err instanceof DOMException && err.name === "AbortError") return; // cancelled — ignore
        setState({ status: "error", error: err instanceof Error ? err.message : "Unknown error" });
      });

    return () => controller.abort();   // cancels on warehouseId change and on unmount → no race
  }, [warehouseId]);

  if (state.status === "loading") return <p>Loading…</p>;
  if (state.status === "error") return <p role="alert">{state.error}</p>;
  if (state.status === "success" && state.data.length === 0) return <p>No stock recorded for this warehouse.</p>;
  return state.status === "success" ? <InventoryGrid rows={state.data} /> : null;
}
```

Without the cleanup, switching quickly from warehouse BER-1 to HAM-1 can show Berlin's stock under the Hamburg heading if its response arrives last — an operator would pick from the wrong numbers.

### StrictMode double-invoke (development only)

`<React.StrictMode>` (in Vite's `main.tsx`) **mounts → unmounts → mounts** every component once in development,
so every effect runs setup → cleanup → setup. It also double-calls component bodies and state initialisers.
Purpose: surface missing cleanups and impure renders. It does **not** happen in production builds.

> **Break it:** remove the `return () => controller.abort()` and open DevTools → Network. You'll see two requests
> on mount. With the cleanup, the first is shown as *(canceled)*. Now remove `StrictMode` — one request. Put it back:
> the right fix is a correct cleanup, not deleting StrictMode.

### You might not need an effect

| Situation | Instead of an effect |
|---|---|
| Value computable from props/state | compute during render |
| Response to a user action (submit, click) | do it in the event handler |
| Reset state when a prop changes | give the component a `key={id}` |
| Expensive computation | `useMemo` |
| Fetching lots of server data | a data library (TanStack Query) or router loaders |

```tsx
// ❌ effect + extra state
const [visible, setVisible] = useState<OrderSummary[]>([]);
useEffect(() => setVisible(orders.filter((o) => o.status !== "SHIPPED")), [orders]);
// ✅ derive
const visible = orders.filter((o) => o.status !== "SHIPPED");
```

---

## 2. `useRef`

A mutable box (`ref.current`) that persists across renders **without causing re-renders**.

```tsx
// 1. DOM access
function ScanInput() {                              // pick screen: focus the barcode field on mount
  const inputRef = useRef<HTMLInputElement>(null);
  useEffect(() => { inputRef.current?.focus(); }, []);
  return <input ref={inputRef} aria-label="Scan SKU barcode" />;
}

// 2. Mutable instance values (timer ids, previous values, "is latest request")
function usePolling(fn: () => Promise<void>, ms: number) {
  const fnRef = useRef(fn);
  useEffect(() => { fnRef.current = fn; });          // always call the latest fn

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    let stopped = false;
    const tick = async () => {
      try { await fnRef.current(); } finally { if (!stopped) timer = setTimeout(tick, ms); }
    };
    tick();
    return () => { stopped = true; clearTimeout(timer); };
  }, [ms]);
}
```

`usePolling` is what FlowGrid's low-stock widget uses to refresh every 30 s without overlapping requests.

Rule: don't read or write `ref.current` during rendering (except lazy init) — refs are for effects and handlers.

---

## 3. `useMemo` and `useCallback`

```tsx
const sorted = useMemo(
  () => levels.toSorted((a, b) => a.available - b.available),
  [levels],
);                                                       // caches a VALUE

const handleAdvance = useCallback((id: number, to: OrderStatus) => {
  setOrders((prev) => prev.map((o) => (o.id === id ? { ...o, status: to } : o)));
}, []);                                                  // caches a FUNCTION identity
```

**When they're worth it:**
1. The computation is measurably expensive (profile first; sorting 50 SKUs is not).
2. The value/function is passed to a `React.memo` child, so a stable identity avoids re-rendering it.
3. The value/function is a dependency of another hook (`useEffect`), and a new identity each render would re-run it.

Otherwise they add noise and cost. (The React Compiler, where adopted, automates much of this — know it exists.)

---

## 4. `useReducer` — when state transitions get complex

```tsx
// Pick screen: scanning items for one pick list
type PickState = { remaining: Record<string, number>; lastScan: string | null; error: string | null };
type Action =
  | { type: "scanned"; sku: string }
  | { type: "undo"; sku: string }
  | { type: "reset"; remaining: Record<string, number> };

function reducer(state: PickState, action: Action): PickState {
  switch (action.type) {
    case "scanned": {
      const left = state.remaining[action.sku];
      if (left === undefined) return { ...state, error: `${action.sku} is not on this pick list` };
      if (left === 0) return { ...state, error: `${action.sku} already fully picked` };
      return { remaining: { ...state.remaining, [action.sku]: left - 1 }, lastScan: action.sku, error: null };
    }
    case "undo":
      return { ...state, remaining: { ...state.remaining, [action.sku]: (state.remaining[action.sku] ?? 0) + 1 }, error: null };
    case "reset":
      return { remaining: action.remaining, lastScan: null, error: null };
  }
}
const [state, dispatch] = useReducer(reducer, { remaining: { "BOLT-M8-50": 3 }, lastScan: null, error: null });
```

Reducers are pure functions → trivially unit-testable (no React needed), and discriminated-union actions are type-checked.
The server still records each pick — the reducer only drives the screen.

---

## 5. Context

Context passes a value to a whole subtree without prop drilling. Good for: current user/auth, theme, the currently selected warehouse.
Not a general state manager — **every consumer re-renders when the value changes**.

```tsx
// src/features/auth/AuthContext.tsx
type AuthState = { user: CurrentUser | null; login: (e: string, p: string) => Promise<void>; logout: () => void };
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const login = useCallback(async (email: string, password: string) => {
    setUser(await authApi.login(email, password));
  }, []);
  const logout = useCallback(() => { authApi.logout(); setUser(null); }, []);
  const value = useMemo(() => ({ user, login, logout }), [user, login, logout]); // stable unless user changes
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside <AuthProvider>");
  return ctx;
}
```

Full auth flow: [04-api-integration-auth.md](./04-api-integration-auth.md).

---

## 6. Custom hooks

A function starting with `use` that calls other hooks. It shares **logic**, not state — each caller gets its own state.

```tsx
// src/shared/hooks/useAsync.ts
export function useAsync<T>(fn: (signal: AbortSignal) => Promise<T>, deps: React.DependencyList): AsyncState<T> {
  const [state, setState] = useState<AsyncState<T>>({ status: "loading" });
  useEffect(() => {
    const c = new AbortController();
    setState({ status: "loading" });
    fn(c.signal)
      .then((data) => setState({ status: "success", data }))
      .catch((e: unknown) => {
        if (c.signal.aborted) return;
        setState({ status: "error", error: e instanceof Error ? e.message : "Unknown error" });
      });
    return () => c.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- caller supplies deps explicitly
  }, deps);
  return state;
}

// usage
const inventory = useAsync((signal) => inventoryApi.list(warehouseId, signal), [warehouseId]);
```

```tsx
// src/shared/hooks/useDebouncedValue.ts — for the SKU search box
export function useDebouncedValue<T>(value: T, ms = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
}
```

Once `useAsync` grows caching, refetching and deduping, you've reinvented TanStack Query — see [04 §4](./04-api-integration-auth.md).

---

## 7. Stale closures

```tsx
function Ticker() {
  const [count, setCount] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setCount(count + 1), 1000); // captures count = 0 forever
    return () => clearInterval(id);
  }, []);                                                      // lint warns: missing `count`
  return <p>{count}</p>;                                       // shows 1 forever
}
```

Fix: `setCount((c) => c + 1)` (no dependency on `count` needed), or add `count` to deps (restarts the interval each tick).

---

## 8. Subscriptions: a streaming hook (Week 16, ForgeCI M3)

The same effect/cleanup rules apply to long-lived subscriptions. A minimal shape for consuming a job's live log over SSE
(see [07/02 §8](../07-javascript-typescript/02-async-javascript.md#8-server-sent-events-eventsource)):

```tsx
type LogLine = { seq: number; text: string };

export function useJobLog(jobId: number) {
  const [lines, setLines] = useState<LogLine[]>([]);
  const [done, setDone] = useState(false);

  useEffect(() => {
    setLines([]);
    setDone(false);
    const es = new EventSource(`/api/jobs/${jobId}/logs/stream`);
    es.addEventListener("log", (e) => {
      const chunk = JSON.parse(e.data) as LogLine[];
      setLines((prev) => prev.concat(chunk));          // updater form: never closes over a stale `lines`
    });
    es.addEventListener("end", () => { setDone(true); es.close(); });
    return () => es.close();                           // unmount / jobId change → no leaked connection
  }, [jobId]);

  return { lines, done };
}
```

What you must still design yourself for ForgeCI: dedupe by `seq` after a reconnect replay, a cap on lines kept in
memory, and rendering thousands of lines without jank ([05 §4](./05-architecture-testing.md#4-performance-basics-just-enough)).

> **Break it:** remove `return () => es.close()` and navigate between three builds. DevTools → Network shows three open
> streams, and lines from the old job appear in the new one.

---

## Common mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| Missing deps | stale data, stale closures | follow the lint rule |
| Object/array literal in deps | effect runs every render | move inside effect, or `useMemo` |
| Setting state in an effect that depends on that state | infinite loop | derive, or guard |
| No cleanup for fetch/timer/subscription | races, leaks, double subscriptions in StrictMode | return a cleanup |
| `useEffect(async () => …)` | returns a promise instead of cleanup | define async fn inside, call it |
| Memoizing everything | noise, no gain | measure first |
| Context for rapidly changing values | whole tree re-renders | local state or split contexts |

---

## Interview Q&A

<details><summary>Explain the <code>useEffect</code> dependency array.</summary>

It lists the reactive values the effect reads. React re-runs the effect after a render where any dependency
changed (by `Object.is`); `[]` means only after mount; no array means after every render. Before re-running,
it calls the previous cleanup. Omitting a dependency causes stale closures.
</details>

<details><summary>Why does my effect run twice in development?</summary>

React 18 StrictMode deliberately mounts, unmounts and remounts components in development to check that effects
clean up properly. If running twice causes a bug, the cleanup is missing. It doesn't happen in production.
</details>

<details><summary><code>useMemo</code> vs <code>useCallback</code>?</summary>

`useMemo(fn, deps)` caches the **result** of `fn`; `useCallback(fn, deps)` caches `fn` itself — it's
`useMemo(() => fn, deps)`. Worth it for expensive computations or for stable identities passed to memoized
children or used as effect dependencies. Otherwise not.
</details>

<details><summary><code>useRef</code> vs <code>useState</code>?</summary>

Both persist across renders. Changing state triggers a re-render; changing `ref.current` doesn't. Refs are for
DOM nodes and mutable values the UI doesn't display (timer ids, latest callback).
</details>

<details><summary>What's a custom hook? Give one you wrote.</summary>

A `use`-prefixed function that composes hooks to share stateful logic. Each call has independent state. I wrote
`usePolling` for FlowGrid's low-stock widget, `useDebouncedValue` for SKU search, and (Week 16) `useJobLog` for ForgeCI's live logs.
</details>

<details><summary>Context vs a state library?</summary>

Context is dependency injection for a subtree — good for low-frequency global values like auth and theme.
It re-renders all consumers on change and has no caching. Server data belongs in a data-fetching library;
complex client state may justify a reducer or a small store.
</details>

---

## Mastery checklist

- [ ] Write a fetching effect with `AbortController` cleanup and explain the race it prevents.
- [ ] Demonstrate StrictMode's double-invoke and fix a missing cleanup.
- [ ] Replace two unnecessary effects in my code with derived values.
- [ ] Implement `usePolling`, `useDebouncedValue`, `useAsync` from scratch.
- [ ] Build `AuthProvider` + `useAuth` with a guarded context.
- [ ] Reproduce and fix a stale-closure interval bug.
- [ ] (Week 16) Build `useJobLog` with cleanup and explain what happens on reconnect.
- [ ] Explain when I'd use `useMemo`/`useCallback` — with a concrete counter-example where I wouldn't.
