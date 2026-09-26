# 02 — Hooks

> Week 16 · ≈ 5 hours. Hooks are functions that let components hold state and run effects.
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
function IssueList({ projectId }: { projectId: number }) {
  const [state, setState] = useState<AsyncState<Issue[]>>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    setState({ status: "loading" });

    issuesApi
      .list(projectId, controller.signal)
      .then((data) => setState({ status: "success", data }))
      .catch((err: unknown) => {
        if (err instanceof DOMException && err.name === "AbortError") return; // cancelled — ignore
        setState({ status: "error", error: err instanceof Error ? err.message : "Unknown error" });
      });

    return () => controller.abort();   // cancels on projectId change and on unmount → no race
  }, [projectId]);

  if (state.status === "loading") return <p>Loading…</p>;
  if (state.status === "error") return <p role="alert">{state.error}</p>;
  if (state.status === "success" && state.data.length === 0) return <p>No issues.</p>;
  return state.status === "success" ? <IssueTable issues={state.data} /> : null;
}
```

Without the cleanup, switching quickly from project 1 to project 2 can show project 1's issues if its response arrives last.

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
const [visible, setVisible] = useState<Issue[]>([]);
useEffect(() => setVisible(issues.filter((i) => i.status !== "DONE")), [issues]);
// ✅ derive
const visible = issues.filter((i) => i.status !== "DONE");
```

---

## 2. `useRef`

A mutable box (`ref.current`) that persists across renders **without causing re-renders**.

```tsx
// 1. DOM access
function IssueTitleInput() {
  const inputRef = useRef<HTMLInputElement>(null);
  useEffect(() => { inputRef.current?.focus(); }, []);
  return <input ref={inputRef} aria-label="Title" />;
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

`usePolling` is what the PulseWatch dashboard uses to refresh monitor status every 10 s without overlapping requests.

Rule: don't read or write `ref.current` during rendering (except lazy init) — refs are for effects and handlers.

---

## 3. `useMemo` and `useCallback`

```tsx
const sorted = useMemo(
  () => issues.toSorted((a, b) => a.title.localeCompare(b.title)),
  [issues],
);                                                       // caches a VALUE

const handleMove = useCallback((id: number, to: IssueStatus) => {
  setIssues((prev) => prev.map((i) => (i.id === id ? { ...i, status: to } : i)));
}, []);                                                  // caches a FUNCTION identity
```

**When they're worth it:**
1. The computation is measurably expensive (profile first; sorting 50 issues is not).
2. The value/function is passed to a `React.memo` child, so a stable identity avoids re-rendering it.
3. The value/function is a dependency of another hook (`useEffect`), and a new identity each render would re-run it.

Otherwise they add noise and cost. (The React Compiler, where adopted, automates much of this — know it exists.)

---

## 4. `useReducer` — when state transitions get complex

```tsx
type FormState = { title: string; status: IssueStatus; submitting: boolean; error: string | null };
type Action =
  | { type: "field"; name: "title"; value: string }
  | { type: "status"; value: IssueStatus }
  | { type: "submit" }
  | { type: "failure"; error: string }
  | { type: "success" };

function reducer(state: FormState, action: Action): FormState {
  switch (action.type) {
    case "field": return { ...state, [action.name]: action.value };
    case "status": return { ...state, status: action.value };
    case "submit": return { ...state, submitting: true, error: null };
    case "failure": return { ...state, submitting: false, error: action.error };
    case "success": return { ...state, submitting: false, title: "" };
  }
}
const [state, dispatch] = useReducer(reducer, { title: "", status: "TODO", submitting: false, error: null });
```

Reducers are pure functions → trivially unit-testable, and discriminated-union actions are type-checked.

---

## 5. Context

Context passes a value to a whole subtree without prop drilling. Good for: current user/auth, theme, current organization.
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
const issues = useAsync((signal) => issuesApi.list(projectId, signal), [projectId]);
```

```tsx
// src/shared/hooks/useDebouncedValue.ts — for the issue search box
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
`usePolling` for PulseWatch's dashboard and `useDebouncedValue` for TeamBoard's search.
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
- [ ] Explain when I'd use `useMemo`/`useCallback` — with a concrete counter-example where I wouldn't.
