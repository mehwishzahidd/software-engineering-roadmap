# 07 — JavaScript & TypeScript Exercises

> Weeks 6–7 · ≈ 3 hours of learning-block time + spillover into FlowGrid work. Do them in a scratch folder (`js-lab/`, see [README](./README.md)).
> **Predict first, then run.** Write the prediction down — the gap between prediction and result is what you learn from.

Legend: 🔮 predict-the-output · 🛠️ build · 💥 break/debug · 🗣️ explain out loud

---

## Part A — Core JavaScript (Week 6, Mon)

### A1 🔮 Coercion
Predict each, then run:
```js
console.log(1 + "2", "3" * "4", true + 1, [] == false, null == 0, null >= 0, "b" + "a" + +"a" + "a");
```
<details><summary>Answer</summary>

`"12" 12 2 true false true "baNaNa"`. `null >= 0` is true because relational comparison converts `null` to 0,
but `==` has a special rule: `null` only equals `undefined`. `+"a"` is `NaN`.
</details>

### A2 🔮 Hoisting
```js
console.log(typeof a, typeof b, typeof c);
var a = 1;
function b() {}
let c = 3;
```
<details><summary>Answer</summary>

`ReferenceError` — `typeof c` is inside the TDZ. `typeof` is only safe for **undeclared** identifiers. Remove `c` and you get `"undefined" "function"`.
</details>

### A3 🛠️ `once(fn)`
Write `once(fn)` returning a function that calls `fn` only the first time and returns the cached result afterwards. Explain which variables live in the closure.

### A4 🛠️ `memoize(fn)`
Memoize a single-argument function using a `Map`. Test with a slow Fibonacci. Then answer: why does keying by an **object** argument surprise people?

### A5 💥 Loop closure bug
```js
const handlers = [];
for (var i = 0; i < 3; i++) handlers.push(() => i);
console.log(handlers.map((h) => h()));
```
Fix it two different ways (`let`; an IIFE or a factory function). 🗣️ Explain why each works.

### A6 🔮 `this`
```js
const svc = {
  name: "api",
  regular() { return this?.name; },
  arrow: () => globalThis.name,
  nested() { return [1].map(function () { return this?.name; }); },
  nestedArrow() { return [1].map(() => this.name); },
};
const { regular } = svc;
console.log(svc.regular(), regular(), svc.nested(), svc.nestedArrow());
```
Run in an ES module (`.mjs`). <details><summary>Answer</summary>`"api" undefined [undefined] ["api"]`</details>

### A7 🛠️ Array transforms (FlowGrid data)
Given:
```js
const orders = [
  { id: 1, status: "RESERVED", units: 3, tags: ["fragile"] },
  { id: 2, status: "SHIPPED", units: 5, tags: ["express", "hazmat"] },
  { id: 3, status: "RESERVED", units: 2, tags: ["fragile", "hazmat"] },
];
```
Without mutating `orders`, compute:
1. Total units of orders not yet `SHIPPED`.
2. `{ RESERVED: [1, 3], SHIPPED: [2] }` (ids grouped by status) using `reduce`.
3. Unique tags sorted: `["express", "fragile", "hazmat"]` (hint: `new Set`, `flatMap`).
4. A new array where order 3 has status `"PICKING"` (use `map` + spread).
5. Orders sorted by units descending with `toSorted`. Confirm the original order is unchanged.

### A8 💥 Shallow copy
```js
const original = { id: 1, tags: ["fragile"] };
const copy = { ...original };
copy.tags.push("express");
console.log(original.tags);
```
Explain the output. Fix with `structuredClone` and with `{ ...original, tags: [...original.tags, "express"] }`.
🗣️ Which fix matches React state updates, and why?

### A9 🛠️ Modules
Split A7 into `orders.js` (data + named exports) and `stats.js` (functions) with a default export. Run with `node`. Then remove `"type": "module"` from `package.json` and read the error.

---

## Part B — Async (Week 6, Wed + project time)

### B1 🔮 Ordering puzzle #2
```js
setTimeout(() => console.log("A"), 0);
Promise.resolve().then(() => {
  console.log("B");
  setTimeout(() => console.log("C"), 0);
}).then(() => console.log("D"));
(async () => {
  console.log("E");
  await Promise.resolve();
  console.log("F");
})();
console.log("G");
```
<details><summary>Answer</summary>

`E G B F D A C`. Sync: E, G. Microtasks in order: B (queues timer C), F; then D (queued after B finished). Timers: A (queued first), C.
</details>

### B2 🛠️ `delay` and `timeout`
Implement `delay(ms)` and `withTimeout(promise, ms)` (using `Promise.race`) that rejects with `new Error("timeout")`.
Then rewrite `withTimeout` for `fetch` using `AbortSignal.timeout(ms)` instead. 🗣️ Why is the second one better? (The race version doesn't cancel the underlying request.)

### B3 🛠️ Sequential vs parallel
Write `fakeFetch(id)` that resolves after `200 + id * 100` ms. Load ids `[1,2,3]` sequentially and in parallel; print `console.time` for each.

### B4 🛠️ `allSettled` dashboard
Simulate 5 FlowGrid dashboard widgets (inventory, low-stock, open orders, pick lists, returns) where 2 reject. Use `Promise.allSettled` to render the ones that loaded and an error card for the others. Then use `Promise.all` and observe you lose all results.

### B5 💥 `forEach` + `async`
```js
async function saveAll(items) {
  items.forEach(async (i) => { await delay(100); console.log("saved", i); });
  console.log("all saved");
}
```
Predict. Fix two ways (sequential and parallel).

### B6 🛠️ `fetch` against your API
Start your FlowGrid Spring Boot app. From Node 20+ (global `fetch`):
1. `GET` a list endpoint; print status and body.
2. `GET` a missing id; confirm `fetch` **resolves** with `404` and `res.ok === false`.
3. `POST` invalid JSON body; print the ProblemDetail `detail`.
4. Stop the server and `GET` again; confirm it **rejects** with `TypeError: fetch failed`.

### B7 🛠️ Race-free search
Implement `onSearchInput(q)` from [02-async-javascript §7](./02-async-javascript.md#7-cancellation-with-abortcontroller) with a fake API where
shorter queries respond **slower**. Prove that without `abort()` the wrong results win, and with it they don't.

### B8 🛠️ Retry with backoff
`retry(fn, { attempts: 3, baseMs: 200 })` — exponential backoff (200, 400, 800) + jitter; do **not** retry on 4xx `ApiError`s.
(The same idea appears server-side in ForgeCI's infra-failure retries, Week 17.)

### B9 🛠️ SSE (do in Week 16, before ForgeCI M3)
Write a 20-line Node HTTP server that streams `id: <n>\nevent: log\ndata: {"line":"step 1..."}\n\n` every 500 ms. Consume it in a browser page with `EventSource`.
Kill and restart the server mid-stream: confirm the browser reconnects and sends `Last-Event-ID`; make the server resume from `n + 1`.

---

## Part C — TypeScript (Week 7)

Enable `strict` and `noUncheckedIndexedAccess` for all of these. **No `any`, no `as` except where stated.**

### C1 🛠️ Domain types
Model FlowGrid: `Role` (from an `as const` array), `OrderStatus`, `Order`, `OrderLine`, `InventoryLevel`, `Warehouse`,
and `Page<T>`. Write `CreateOrderRequest` and `UpdateSkuRequest` using utility types only.

### C2 🛠️ Exhaustive status transitions
Write `nextStatuses(s: OrderStatus): OrderStatus[]` for your FlowGrid order state machine (e.g. RESERVED→ALLOCATED→PICKING→PACKED→SHIPPED, CANCELLED allowed only before PICKING).
Use a `Record<OrderStatus, OrderStatus[]>`. Add a new status (`PARTIALLY_SHIPPED`) and watch the compiler point at every place to update.
The UI uses this only to decide which buttons to show — the backend state machine is the authority.

### C3 🛠️ `AsyncState<T>` + reducer
Write the `AsyncState<T>` union and a function `render(state)` returning a string for each case, with a `never` check.

### C4 💥 `unknown` from JSON
```ts
const raw = JSON.parse('{"id": "7", "number": "SO-7", "lines": []}');
const order: Order = raw; // compiles!
order.id.toFixed();       // runtime crash
```
Explain why it compiled (`JSON.parse` returns `any`). Change to `const raw: unknown = ...` and write an `isOrder` type guard. Then do it with zod.

### C5 🛠️ Generic API client
Write `getJson<T>(path, init?)` and `ApiError`. Use it for `Page<OrderSummary>`. Then write `postJson<Req, Res>(path, body: Req)`.

### C6 🛠️ Generic `groupBy`
```ts
function groupBy<T, K extends PropertyKey>(items: readonly T[], key: (t: T) => K): Record<K, T[]>
```
Implement it. Hint: the result type is really `Partial<Record<K, T[]>>` — why? Pick one and justify.

### C7 🔮 Narrowing
For each, say what type `x` has on the marked line:
```ts
function f(x: string | number[] | null) {
  if (!x) { /* 1 */ return; }
  if (Array.isArray(x)) { /* 2 */ return; }
  /* 3 */
}
```
<details><summary>Answer</summary>1: `string | null` (empty string is falsy; an empty array is truthy). 2: `number[]`. 3: `string`.</details>

---

## Part D — HTML/CSS (Weeks 6–7, as needed)

### D1 🛠️ Login page
Build [04-html-css-minimum](./04-html-css-minimum.md)'s login form with vanilla JS: show inline errors on submit, disable the button while "submitting" (fake `delay(800)`).

### D2 🛠️ Board lanes
Four lanes with flexbox, horizontally scrollable, stacking vertically under 640 px.

---

## Part E — Explain (Sunday review) 🗣️

Record yourself answering each in ≤ 60 s. Re-record any you stumble on.

1. What happens between `fetch()` being called and `.then` running?
2. Closure: definition + one example from your own FlowGrid code.
3. `this` in arrow vs regular functions.
4. Why `===`?
5. `unknown` vs `any`, with the JSON example.
6. Discriminated unions: why they beat boolean flags.

---

## Completion tracker

| Part | Done | Notes / what surprised me |
|---|---|---|
| A (1–9) | ☐ | |
| B (1–9) | ☐ | |
| C (1–7) | ☐ | |
| D (1–2) | ☐ | |
| E (1–6) | ☐ | |

Update [trackers/technology-tracker.md](../trackers/technology-tracker.md) (JavaScript, TypeScript rows) when done.
