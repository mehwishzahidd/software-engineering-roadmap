# 02 — Asynchronous JavaScript

> Week 14 · ≈ 3.5 hours. Java gave you threads. JavaScript gives you **one thread + an event loop**.
> Every React data-fetching bug you will hit in TeamBoard and PulseWatch traces back to this file.

---

## 1. The event loop model

```
┌──────────────────────────┐
│        Call stack        │  ← runs synchronous code, one frame at a time
└────────────┬─────────────┘
             │ stack empty?
             ▼
┌──────────────────────────┐
│    Microtask queue       │  ← promise reactions (.then/.catch/.finally), await continuations,
│  (drained COMPLETELY)    │     queueMicrotask, MutationObserver
└────────────┬─────────────┘
             │ microtasks empty?
             ▼
   (browser may render here)
             │
             ▼
┌──────────────────────────┐
│    Macrotask (task) queue│  ← setTimeout, setInterval, I/O callbacks, UI events, MessageChannel
│   (ONE task per turn)    │
└──────────────────────────┘
```

Algorithm, simplified:

1. Run the current script (a task) until the stack is empty.
2. Drain **all** microtasks (including microtasks queued by microtasks).
3. Render if needed (browser).
4. Take **one** macrotask, run it, go to 2.

Web APIs (timers, network, DOM events) run outside the JS thread and enqueue callbacks when ready.
`fetch` itself doesn't block — its promise resolves later via a task + microtask.

**Consequences:**
- `setTimeout(fn, 0)` means "at least 0 ms, after current task and all microtasks".
- A long synchronous loop freezes the UI: no rendering, no clicks, no timers.
- An infinite chain of microtasks also starves rendering.

---

## 2. The ordering puzzle (do this before reading the answer)

```js
console.log("1: script start");

setTimeout(() => console.log("2: timeout"), 0);

Promise.resolve()
  .then(() => console.log("3: then A"))
  .then(() => console.log("4: then B"));

queueMicrotask(() => console.log("5: microtask"));

(async () => {
  console.log("6: async fn body");
  await null;
  console.log("7: after await");
})();

setTimeout(() => {
  console.log("8: timeout 2");
  Promise.resolve().then(() => console.log("9: then inside timeout"));
}, 0);

console.log("10: script end");
```

<details><summary>Answer + explanation</summary>

```
1: script start
6: async fn body
10: script end
3: then A
5: microtask
7: after await
4: then B
2: timeout
8: timeout 2
9: then inside timeout
```

- Synchronous first: 1, 6 (an async function runs synchronously until its first `await`), 10.
- Microtask queue at end of script, in enqueue order: `then A`, `microtask`, `after await`.
- `then B` is only enqueued when `then A` finishes, so it runs after those three.
- Then macrotasks one at a time: `timeout`, then `timeout 2`. After `timeout 2`, its microtask (9) runs before any further task.
</details>

> **Break it:** replace `await null` with `await new Promise(r => setTimeout(r, 0))`. Where does "7" move? (After "2", because
> it's now waiting on a macrotask, queued before "8".)

---

## 3. Promises

A `Promise` is an object representing a future value, in one of three states: **pending → fulfilled | rejected** (settled once).

```js
function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

delay(100)
  .then(() => fetch("/api/projects"))
  .then((res) => res.json())               // returning a promise flattens the chain
  .then((projects) => console.log(projects))
  .catch((err) => console.error("failed", err)) // catches any rejection above
  .finally(() => console.log("done"));
```

### Combinators

| Combinator | Resolves when | Rejects when | Use in projects |
|---|---|---|---|
| `Promise.all([...])` | all fulfill (array of values) | **first** rejection | Load project + members + labels for TeamBoard in parallel |
| `Promise.allSettled([...])` | all settle (array of `{status, value/reason}`) | never | PulseWatch: check 10 monitors, show partial results |
| `Promise.race([...])` | first to settle | first to settle (if rejection) | Manual timeout (prefer `AbortSignal.timeout`) |
| `Promise.any([...])` | first fulfillment | all reject (`AggregateError`) | Fastest mirror |

---

## 4. `async` / `await`

`async` functions always return a promise. `await` pauses **the function** (not the thread) and resumes it as a microtask.

```js
async function loadBoard(projectId) {
  const res = await fetch(`/api/projects/${projectId}/issues`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
```

### Sequential vs parallel

```js
// Sequential: ~ t1 + t2
const project = await getProject(id);
const members = await getMembers(id);

// Parallel: ~ max(t1, t2)
const [project2, members2] = await Promise.all([getProject(id), getMembers(id)]);
```

> **Break it:** add `await delay(500)` inside both `getProject` and `getMembers` and time each version with `console.time`.

### `await` in loops

```js
for (const id of ids) await check(id);            // sequential — intentional throttling
await Promise.all(ids.map((id) => check(id)));    // parallel
ids.forEach(async (id) => await check(id));       // BUG: forEach ignores the promises; nothing waits
```

---

## 5. Error handling

```js
async function safeLoad() {
  try {
    const data = await loadBoard(1);
    return { ok: true, data };
  } catch (err) {
    // err is `unknown` in TS — could be TypeError (network), your Error, AbortError...
    return { ok: false, error: err instanceof Error ? err.message : String(err) };
  }
}
```

Rules:
- A rejected promise nobody handles → **unhandled rejection** (console error in browsers; in Node 15+ it crashes the process by default).
- `try/catch` only catches an async error if you `await` inside the `try`. `try { loadBoard() } catch {}` catches nothing.
- Throw `Error` objects (stack traces), not strings.

---

## 6. `fetch` done properly

**`fetch` only rejects on network failure** (DNS, CORS block, offline, abort). A `404` or `500` **resolves** with `res.ok === false`.

```js
async function http(path, { method = "GET", body, signal } = {}) {
  const res = await fetch(`${import.meta.env?.VITE_API_URL ?? ""}${path}`, {
    method,
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: body !== undefined ? JSON.stringify(body) : undefined,
    signal,
  });

  if (res.status === 204) return null;

  const isJson = res.headers.get("content-type")?.includes("json");
  const payload = isJson ? await res.json() : await res.text();

  if (!res.ok) {
    // Spring Boot ProblemDetail (RFC 7807): { type, title, status, detail, instance }
    const message = payload?.detail ?? payload?.title ?? `HTTP ${res.status}`;
    const err = new Error(message);
    err.status = res.status;
    err.problem = payload;
    throw err;
  }
  return payload;
}
```

This is the JS seed of the typed client in [08-react/04-api-integration-auth.md](../08-react/04-api-integration-auth.md).

---

## 7. Cancellation with `AbortController`

```js
const controller = new AbortController();
const p = fetch("/api/issues?q=login", { signal: controller.signal });
controller.abort();                     // p rejects with DOMException name "AbortError"

// Built-in timeout (Node 18+/modern browsers)
await fetch("/api/monitors", { signal: AbortSignal.timeout(5000) }); // rejects with "TimeoutError"

// Combine user-cancel + timeout (AbortSignal.any: Node 20+ / modern browsers)
const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(5000)]);
```

### Search-as-you-type without races (TeamBoard)

```js
let current;
async function onSearchInput(q) {
  current?.abort();                     // cancel the previous in-flight request
  current = new AbortController();
  try {
    const results = await http(`/api/issues?q=${encodeURIComponent(q)}`, { signal: current.signal });
    render(results);
  } catch (err) {
    if (err.name === "AbortError") return; // expected, ignore
    showError(err);
  }
}
```

Without the abort, a slow response for `"lo"` can arrive **after** the fast one for `"login"` and overwrite the correct results.

---

## 8. Timers and polling (PulseWatch dashboard)

```js
function poll(fn, intervalMs) {
  let stopped = false;
  let timer;
  async function tick() {
    try { await fn(); } catch (e) { console.error(e); }
    if (!stopped) timer = setTimeout(tick, intervalMs); // schedule AFTER completion
  }
  tick();
  return () => { stopped = true; clearTimeout(timer); };
}
const stop = poll(() => http("/api/status"), 10_000);
```

`setTimeout`-recursion instead of `setInterval` prevents overlapping requests when the API is slow.

---

## Common mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| Forgetting `await` | `[object Promise]` rendered, or error not caught | `await`, lint rule `no-floating-promises` (typescript-eslint) |
| `forEach(async ...)` | Code after loop runs before work finishes | `for...of` or `Promise.all(map)` |
| Treating 4xx/5xx as rejection | Error UI never shows | check `res.ok` |
| Sequential awaits for independent calls | Slow page | `Promise.all` |
| No cancellation | Stale results flash; "state update on unmounted component" | `AbortController` |
| `setInterval` polling a slow endpoint | Pile-up of concurrent requests | recursive `setTimeout` |

---

## Interview Q&A

<details><summary>JavaScript is single-threaded — how does it handle concurrency?</summary>

The JS engine runs one call stack. I/O and timers are handled by the host (browser/libuv in Node) off the
main thread; when they complete, callbacks are queued. The event loop runs one macrotask, then drains the
microtask queue, then (in browsers) may render, and repeats. So it's concurrency via non-blocking I/O, not parallelism.
</details>

<details><summary>Microtask vs macrotask?</summary>

Microtasks: promise reactions, `await` continuations, `queueMicrotask`. Macrotasks: `setTimeout`, I/O, UI events.
After each macrotask the engine drains **all** microtasks before taking the next macrotask, so promise callbacks
always beat a `setTimeout(0)` queued at the same time.
</details>

<details><summary><code>Promise.all</code> vs <code>Promise.allSettled</code>?</summary>

`all` fails fast on the first rejection — right when every result is required. `allSettled` waits for all and
reports each outcome — right when partial success is useful, e.g. a dashboard showing each monitor's status.
</details>

<details><summary>Does <code>fetch</code> reject on HTTP 500?</summary>

No. It rejects only on network-level failures or abort. You must check `response.ok` / `response.status` yourself.
</details>

<details><summary>How do you cancel a request?</summary>

Pass `signal` from an `AbortController` to `fetch` and call `abort()`. The promise rejects with an `AbortError`,
which I ignore in the handler. I use it for search-as-you-type and in React effect cleanups.
</details>

<details><summary>Compare to Java's <code>CompletableFuture</code>.</summary>

Both represent a future result with chaining (`thenApply` ≈ `then`, `exceptionally` ≈ `catch`, `allOf` ≈ `Promise.all`).
Difference: `CompletableFuture` callbacks may run on pool threads in parallel; JS callbacks always run on the one
event-loop thread, so there are no data races on JS variables — but also no CPU parallelism.
</details>

---

## Mastery checklist

- [ ] Draw the event loop from memory (stack, microtasks, macrotasks, render).
- [ ] Solve the ordering puzzle and a variant I invent myself.
- [ ] Convert a `.then` chain to `async/await` and back.
- [ ] Show sequential vs parallel timing with `console.time`.
- [ ] Write the `http` helper that handles non-2xx, 204 and ProblemDetail.
- [ ] Implement search-as-you-type with `AbortController` and prove no stale results.
- [ ] Implement non-overlapping polling.
