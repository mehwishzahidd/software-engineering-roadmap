# JavaScript — Résumé Tech Defense

> **Goal:** when an interviewer sees "JavaScript" on your résumé, you can (1) describe truthfully
> where you used it in past roles, (2) answer language-level questions from *current* knowledge
> (ES2022+), and (3) point at code you wrote in this roadmap — the TeamBoard and PulseWatch frontends.
>
> **Honesty rule:** describe past work at the level you actually did it ("I maintained form
> validation and API calls in an existing jQuery/React front end"), never inflate it. Your depth
> claims should come from what you can demonstrate *today*.

---

## Evidence in my projects

| Project | Where JavaScript shows up | What I can point at |
|---|---|---|
| **P3 TeamBoard** (W14–18) | React + TS frontend compiles to JS; `fetch`-based typed API client; async loading/error states; token handling | `src/api/client.ts` (wraps `fetch`, attaches `Authorization`, maps non-2xx to typed errors), board view state updates with immutable array ops |
| **P4 PulseWatch** (W22) | React/TS status dashboard polling the public status API | polling with `setInterval` + cleanup, `AbortController` to cancel in-flight requests, `Promise.allSettled` to load monitors + incidents together |
| **P4 load test** (W22) | k6 scripts are plain JavaScript | `loadtest/status-page.js` — stages, thresholds, checks |
| **Week 14 exercises** | Closures, event-loop ordering, promise chains | [`../07-javascript-typescript/exercises.md`](../07-javascript-typescript/exercises.md) |

## Where to learn it in this repo

- [`../07-javascript-typescript/01-javascript-core.md`](../07-javascript-typescript/01-javascript-core.md) — types, scope, closures, `this`, objects, modules
- [`../07-javascript-typescript/02-async-javascript.md`](../07-javascript-typescript/02-async-javascript.md) — event loop, promises, `async`/`await`, `fetch`
- [`../07-javascript-typescript/04-html-css-minimum.md`](../07-javascript-typescript/04-html-css-minimum.md) — DOM and events minimum
- [`../07-javascript-typescript/exercises.md`](../07-javascript-typescript/exercises.md)
- Next: [`typescript.md`](./typescript.md), [`react.md`](./react.md)

---

## 1. Beginner questions

<details><summary><b>B1. What's the difference between <code>var</code>, <code>let</code> and <code>const</code>?</b></summary>

- `var`: function-scoped, hoisted and initialised to `undefined`, can be redeclared. Creates a property on the global object at top level in scripts.
- `let`/`const`: block-scoped, hoisted but in the **temporal dead zone** until the declaration line (access → `ReferenceError`).
- `const` prevents **rebinding**, not mutation: `const a = []; a.push(1)` is fine.
- Default: `const`; `let` when you reassign; never `var` in new code.
</details>

<details><summary><b>B2. What are JavaScript's primitive types?</b></summary>

`string`, `number` (IEEE-754 double), `bigint`, `boolean`, `undefined`, `symbol`, `null`. Everything else is an object (arrays, functions, dates). Primitives are immutable and compared by value; objects are compared by reference. `typeof null === "object"` is a historical bug.
</details>

<details><summary><b>B3. <code>==</code> vs <code>===</code>?</b></summary>

`===` compares without type coercion; `==` coerces (`"0" == 0` is `true`, `null == undefined` is `true`). Use `===` always; the one idiom some teams allow is `x == null` to check null-or-undefined. Prefer `x ?? fallback` for defaults.
</details>

<details><summary><b>B4. What is a closure?</b></summary>

A function plus the lexical environment it was created in. The inner function keeps access to outer variables after the outer function returns.
```js
function counter() { let n = 0; return () => ++n; }
const next = counter(); next(); next(); // 2
```
Practical uses: private state, factories, memoisation, event handlers that remember an id. In React every hook callback is a closure — the source of "stale closure" bugs.
</details>

<details><summary><b>B5. What are <code>null</code> and <code>undefined</code>, and how do <code>??</code> and <code>?.</code> help?</b></summary>

`undefined` = not assigned / missing property / no return value. `null` = intentional "no value". `a ?? b` returns `b` only if `a` is `null`/`undefined` (unlike `||`, which also replaces `0`, `""`, `false`). `a?.b?.c` short-circuits to `undefined` instead of throwing.
</details>

<details><summary><b>B6. Arrow functions vs regular functions?</b></summary>

Arrows have no own `this`, `arguments`, `super` or `prototype`; they capture `this` lexically and can't be used with `new`. Use them for callbacks; use method syntax for object methods that need `this`.
</details>

<details><summary><b>B7. How do ES modules work?</b></summary>

`export`/`import` are static (analysed before execution, enabling tree-shaking). Modules are strict mode, have their own scope, execute once and are cached. Named vs default exports. `import()` is dynamic and returns a promise (code-splitting). Node uses ESM with `"type": "module"` or `.mjs`; CommonJS (`require`) is the legacy system.
</details>

<details><summary><b>B8. What do <code>map</code>, <code>filter</code>, <code>reduce</code> return, and which mutate?</b></summary>

All three return new values and don't mutate the source. `map` → same length array, `filter` → subset, `reduce` → any accumulator. Mutating: `push`, `pop`, `splice`, `sort`, `reverse`. ES2023 added non-mutating `toSorted`, `toReversed`, `toSpliced`, `with` — useful in React state updates.
</details>

## 2. Intermediate questions

<details><summary><b>I1. Explain the event loop.</b></summary>

- One call stack per agent. Synchronous code runs to completion.
- When the stack empties, the loop drains **all microtasks** (promise reactions, `queueMicrotask`, `await` continuations), then takes **one macrotask** (timers, I/O, UI events, `MessageChannel`), then microtasks again, then (in browsers) possibly renders.
- Consequence: `setTimeout(fn, 0)` runs after pending promise callbacks; a long synchronous loop blocks rendering and input.
```js
console.log(1); setTimeout(() => console.log(2)); Promise.resolve().then(() => console.log(3)); console.log(4);
// 1 4 3 2
```
</details>

<details><summary><b>I2. How is <code>this</code> determined?</b></summary>

By call site (for non-arrow functions): `obj.m()` → `obj`; plain call `f()` → `undefined` in strict mode (modules are strict); `new F()` → the new object; `f.call/apply(x)` / `f.bind(x)` → `x`. Arrow functions inherit `this` from the enclosing scope. Classic bug: passing `obj.method` as a callback loses `this`.
</details>

<details><summary><b>I3. Promises: states and combinators.</b></summary>

States: pending → fulfilled | rejected (settled once). `.then` returns a new promise (chaining). Combinators: `Promise.all` (fail-fast, all must fulfil), `allSettled` (never rejects, gives status per item), `race` (first settled), `any` (first fulfilled; `AggregateError` if all reject).
</details>

<details><summary><b>I4. What does <code>async</code>/<code>await</code> actually do?</b></summary>

`async` functions always return a promise. `await x` suspends the function, and resumes in a microtask when `x` settles; rejections become thrown exceptions (use `try/catch`). Sequential `await` in a loop serialises work — use `Promise.all` for independent calls. Top-level `await` is allowed in ES modules.
</details>

<details><summary><b>I5. Prototypal inheritance vs <code>class</code>.</b></summary>

Objects link to a prototype; property lookup walks the chain. `class` is syntax over constructor functions + prototypes, adding `extends`, `super`, static members, and (ES2022) `#private` fields, static blocks and public class fields. Methods live on the prototype, not per instance.
</details>

<details><summary><b>I6. Shallow vs deep copy?</b></summary>

Spread `{...o}` / `[...a]` / `Object.assign` copy one level; nested objects are shared. `structuredClone(o)` does a deep copy (handles Dates, Maps, cycles; not functions or class prototypes). `JSON.parse(JSON.stringify(o))` loses Dates, `undefined`, Maps.
</details>

<details><summary><b>I7. How does <code>fetch</code> handle errors?</b></summary>

`fetch` only rejects on network failure/abort/CORS failure. A 404 or 500 **resolves** — you must check `res.ok` / `res.status`. `res.json()` is itself async and throws on invalid JSON. Cancellation with `AbortController` (or `AbortSignal.timeout(ms)`).
</details>

<details><summary><b>I8. <code>Map</code>/<code>Set</code> vs plain objects?</b></summary>

`Map` keys can be any type, preserves insertion order, has `size`, no prototype key collisions, better for frequent add/remove. Objects are fine for fixed-shape records and JSON. `WeakMap` holds keys weakly (metadata caches without leaks).
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "Your résumé says JavaScript — what did you actually build with it?"</b></summary>

- Past role, truthful scope: which app, which part (forms, API calls, UI logic), team size, whether you maintained or created it.
- Bridge: "I've been away from day-to-day work since <year>, so I rebuilt my fundamentals in modern ES2022+ and TypeScript."
- Current evidence: TeamBoard frontend — typed API client over `fetch`, auth token handling, optimistic UI; PulseWatch dashboard polling with abort/cleanup.
- Follow-up they'll ask: "Show me how your API client handles a 401."
</details>

<details><summary><b>R2. "What's the output, and why?" (event-loop ordering snippet)</b></summary>

Narrate: sync first → microtasks (all) → next macrotask. Mention `await` splits a function: code after `await` is a microtask continuation.
</details>

<details><summary><b>R3. "How do you handle errors in async code in your frontend?"</b></summary>

- One `request()` helper: checks `res.ok`, parses RFC 7807 `ProblemDetail` from Spring, throws a typed `ApiError { status, title, detail }`.
- Callers `try/catch` or let a query library surface `error`; UI shows an error state, not a blank screen.
- Unhandled rejection = bug; watch the console / `unhandledrejection` event.
</details>

<details><summary><b>R4. "Explain closures with an example from your code."</b></summary>

Debounced search input in TeamBoard issue filter; or `useEffect` cleanup capturing the `AbortController`. Also mention the stale-closure bug in `setInterval` and fix via functional state updates or a ref.
</details>

<details><summary><b>R5. "How is JavaScript different from Java?"</b></summary>

Dynamic vs static typing; prototypes vs classes; single-threaded event loop vs threads; functions are first-class values; `number` is a double (no `int`/`long`, `BigInt` for large ints); no checked exceptions; modules vs packages. Then: "that's why I use TypeScript on the frontend."
</details>

<details><summary><b>R6. "Why is <code>0.1 + 0.2 !== 0.3</code>, and how do you handle money?"</b></summary>

IEEE-754 binary floats. For money: keep amounts as integer minor units (cents) or strings from the API, format with `Intl.NumberFormat`. Tie to Ledger: the backend uses `BigDecimal`; the frontend never does arithmetic on money it doesn't need to.
</details>

## 4. Practical tasks (doable live)

1. Implement `debounce(fn, ms)` and demonstrate with a fake input.
2. Implement `promiseAll(promises)` without using `Promise.all` (preserve order, reject fast).
3. Write `fetchJson(url, { timeoutMs })` using `AbortSignal.timeout`, throwing on non-2xx.
4. Group an array of issues by `status` using `reduce` (then with `Object.groupBy`, ES2024, noting support).
5. Write `retry(fn, { attempts, baseMs })` with exponential backoff + jitter.
6. Deep-freeze an object recursively.

## 5. Debugging questions

<details><summary><b>D1. A button handler logs <code>undefined</code> for <code>this.state</code>.</b></summary>

Method passed as a callback lost its receiver. Fix: arrow function, `.bind`, or (in React) function components where `this` doesn't exist.
</details>

<details><summary><b>D2. The UI shows "Saved!" even when the server returned 500.</b></summary>

`fetch` resolved on HTTP error; code never checked `res.ok`. Fix in the shared client, add a test with a mocked 500.
</details>

<details><summary><b>D3. A loop of <code>await fetch(...)</code> for 50 items takes 10 seconds.</b></summary>

Sequential awaits. Use `Promise.all` (with a concurrency limit if the server needs it). Verify in DevTools Network waterfall.
</details>

<details><summary><b>D4. A <code>setInterval</code> counter always shows 1.</b></summary>

Stale closure capturing initial `count`. Use `setCount(c => c + 1)` or a ref; clear the interval in cleanup.
</details>

<details><summary><b>D5. "Uncaught (in promise) TypeError: Failed to fetch" only in the browser, curl works.</b></summary>

Likely CORS (preflight rejected) or mixed content (https page → http API). Check the Network tab for the `OPTIONS` request and response headers; fix the Spring CORS config or use the Vite dev proxy / same-origin nginx in production.
</details>

## 6. Architecture questions

<details><summary><b>A1. Where should API-calling code live in a frontend?</b></summary>

In one API layer (`api/client.ts` + per-resource modules), not scattered in components. Centralises base URL, auth header, error mapping, and makes mocking in tests trivial.
</details>

<details><summary><b>A2. How do you keep the UI responsive with heavy computation?</b></summary>

Don't block the main thread: chunk work, move to a Web Worker, or do it server-side. For large lists, paginate/virtualise.
</details>

<details><summary><b>A3. Polling vs WebSockets vs Server-Sent Events for a status dashboard?</b></summary>

Polling (PulseWatch's choice): simplest, cacheable (Redis-cached status endpoint), fine at 15–30 s intervals. SSE: one-way server push over HTTP. WebSockets: bidirectional, more infra. Choose the simplest that meets freshness needs.
</details>

## 7. Common mistakes

- Using `||` for defaults when `0`/`""` are valid (use `??`).
- Forgetting `res.ok` with `fetch`.
- `forEach` with `async` callbacks — it doesn't await them.
- Mutating state arrays with `sort()`/`push()` in React.
- Floating-point arithmetic for money.
- `==` comparisons and truthiness surprises (`[] ` is truthy, `"0"` is truthy).
- Unhandled promise rejections swallowed silently.
- Leaking timers/listeners (no cleanup).

## 8. Terminology I must know

| Term | Meaning in one line |
|---|---|
| Event loop | Scheduler running stack → microtasks → one macrotask |
| Microtask | Promise reaction / `queueMicrotask`; drained before next macrotask |
| Closure | Function + captured lexical scope |
| Hoisting | Declarations processed before execution; `let/const` in TDZ |
| TDZ | Temporal dead zone: binding exists but can't be accessed yet |
| Prototype chain | Lookup path for inherited properties |
| Promise | Object representing a future settled value |
| ESM | ECMAScript modules: static `import`/`export` |
| Tree-shaking | Bundler removes unused exports |
| Transpile | Convert syntax (TS/JSX → JS) |
| `AbortController` | Cancels `fetch` and other async ops |
| Truthy/falsy | Coercion to boolean; falsy: `false 0 -0 0n "" null undefined NaN` |
| Optional chaining | `a?.b` stops on null/undefined |
| Nullish coalescing | `a ?? b` default only for null/undefined |

## 9. When to use it

- Anything that runs in the browser (via TypeScript in this roadmap).
- Small tooling scripts in a Node project, k6 load tests, build configs.
- When the team's stack is Node-based.

## 10. When NOT to use it

- Plain JS for a large codebase with multiple contributors → use TypeScript.
- CPU-bound backend work or where the team's services are Java/Spring (keep one backend language).
- Precise decimal arithmetic without a decimal library.

## 11. Trade-offs

| Choice | Gain | Cost |
|---|---|---|
| Dynamic typing | Fast to write | Runtime type errors; weaker refactoring |
| Single-threaded event loop | No data races on shared memory | CPU work blocks UI |
| Huge npm ecosystem | Libraries for everything | Supply-chain risk, dependency churn |
| Fetch vs axios | No dependency | Manual `res.ok`, timeouts, interceptors |

## 12. How it interacts with the rest of my stack

- **Spring Boot:** JS calls REST endpoints with JSON; must handle CORS (dev) and `ProblemDetail` errors; sends `Authorization: Bearer <jwt>`.
- **Postgres:** never directly — all data goes through the API; IDs are strings/numbers, timestamps ISO-8601 (`Instant` → `"2026-01-01T10:00:00Z"`).
- **React/TS:** JS is the runtime; TS types erase to it.
- **Docker/nginx:** Vite builds static JS bundles served by nginx in the TeamBoard Compose stack.
- **AWS:** bundles could be served from S3 (+CloudFront); in PulseWatch nginx on EC2 serves them.
- **CI:** `npm ci && npm run lint && npm test && npm run build` in GitHub Actions.

## 13. One small hands-on exercise

**Build `fetchJson` with retry and timeout (Node 20+, no dependencies).**

Acceptance criteria:
- [ ] `fetchJson(url, { timeoutMs = 3000, retries = 2 })` returns parsed JSON on 2xx.
- [ ] Non-2xx throws `HttpError` with `status` and parsed body (if JSON).
- [ ] Retries only on network errors and 5xx/429, with exponential backoff + jitter; never retries 4xx.
- [ ] Times out using `AbortSignal.timeout`.
- [ ] 5 tests with `node --test` using a local `http.createServer` returning 200, 404, 503-then-200, slow, invalid JSON.
- [ ] You can explain the event-loop ordering of your retry delay.

## 14. Mastery checklist

- [ ] Predict output of 5 event-loop snippets without running them
- [ ] Explain closures, `this`, prototypes out loud in < 2 min each
- [ ] Write debounce, throttle, promiseAll, retry from a blank file
- [ ] Explain why `fetch` doesn't reject on 500 and show my client's fix
- [ ] Know the ES2020–ES2024 features I use (`?.`, `??`, `#private`, `at()`, `toSorted`, `structuredClone`)
- [ ] Can describe past JS work truthfully in 60 seconds and bridge to TeamBoard
- [ ] Diagnose a CORS failure from the Network tab
