# 01 — JavaScript Core

> Week 14 · ≈ 4 hours · Prereq: you know Java. This file focuses on **where JavaScript differs from Java**,
> because that is where both bugs and interview questions live.

---

## 1. Types and values

JavaScript has **7 primitive types** + objects:

| Type | Example | `typeof` |
|---|---|---|
| `string` | `"TB-42"` | `"string"` |
| `number` (IEEE-754 double, no int type) | `42`, `0.1`, `NaN`, `Infinity` | `"number"` |
| `bigint` | `9007199254740993n` | `"bigint"` |
| `boolean` | `true` | `"boolean"` |
| `undefined` | declared, not assigned | `"undefined"` |
| `null` | intentional "no value" | `"object"` ← historical bug |
| `symbol` | `Symbol("id")` | `"symbol"` |
| object (incl. arrays, functions) | `{}`, `[]`, `() => {}` | `"object"` / `"function"` |

```js
0.1 + 0.2 === 0.3;           // false (same as Java double)
Number.MAX_SAFE_INTEGER;     // 9007199254740991 → IDs from Postgres BIGINT can exceed this!
typeof null;                 // "object"
Array.isArray([]);           // true — use this, not typeof
Number.isNaN(NaN);           // true;  NaN === NaN is false
```

**Practical:** if a TeamBoard/PulseWatch backend ever returns a `BIGINT` id above 2^53, serialize it as a string.
For money, never use floats in JS either — send amounts as strings or integer cents (same lesson as P1 Ledger's `BigDecimal`).

### Primitives are copied, objects are shared by reference

```js
const a = { status: "TODO" };
const b = a;           // same reference (like Java)
b.status = "DONE";
console.log(a.status); // "DONE"
```

`const` means the **binding** can't be reassigned — the object is still mutable. Same as `final` in Java.

---

## 2. Coercion, `==` vs `===`, truthiness

`==` performs type coercion; `===` does not. **Always use `===`** (and `!==`). The one common exception
some codebases allow: `x == null` which is true for both `null` and `undefined`.

```js
0 == "";          // true   (both coerce to 0)
"0" == false;     // true
null == undefined;// true
null === undefined; // false
[] + [];          // ""     (arrays → strings)
[] + {};          // "[object Object]"
"5" - 2;          // 3      (- forces numeric)
"5" + 2;          // "52"   (+ with a string concatenates)
```

**Falsy values** (exactly 8): `false`, `0`, `-0`, `0n`, `""`, `null`, `undefined`, `NaN`. Everything else is truthy — including `"0"`, `"false"`, `[]`, `{}`.

```js
const count = 0;
const label = count || "none";   // "none"  ← bug if 0 is valid
const label2 = count ?? "none";  // 0       ← ?? only falls back on null/undefined
const title = issue?.assignee?.name ?? "Unassigned"; // optional chaining
```

> **Break it:** In a PulseWatch dashboard, `responseTimeMs || "—"` displays "—" when the check took 0 ms.
> Replace with `??` and observe the difference.

---

## 3. `var`, `let`, `const`, scope and hoisting

| | Scope | Hoisted? | Re-declare | Reassign |
|---|---|---|---|---|
| `var` | function | yes, initialised to `undefined` | yes | yes |
| `let` | block | yes, but in **TDZ** until declaration | no | yes |
| `const` | block | yes, TDZ | no | no |

```js
console.log(x); // undefined  (var hoisted + initialised)
var x = 1;

console.log(y); // ReferenceError: Cannot access 'y' before initialization (TDZ)
let y = 2;

sayHi();        // works — function declarations are hoisted with their body
function sayHi() { console.log("hi"); }

greet();        // TypeError: greet is not a function (var hoisted as undefined)
var greet = function () {};
```

**Rule:** `const` by default, `let` when you must reassign, never `var`.

---

## 4. Functions

```js
function add(a, b) { return a + b; }        // declaration (hoisted)
const mul = function (a, b) { return a * b; }; // expression
const sub = (a, b) => a - b;                // arrow: no own this/arguments, can't be `new`ed

// Default + rest params
function log(level = "INFO", ...parts) { console.log(`[${level}]`, ...parts); }

// Functions are values — pass them like Java lambdas
[3, 1, 2].sort((a, b) => a - b);            // [1, 2, 3]
```

> **Break it:** `[10, 9, 1].sort()` → `[1, 10, 9]`. Default `sort` compares **strings**. Always pass a comparator for numbers.

---

## 5. Closures

A closure is a function **plus the variables from the scope where it was created**, which it keeps alive.

```js
function createCounter() {
  let count = 0;                // private state
  return {
    increment: () => ++count,
    get: () => count,
  };
}
const c = createCounter();
c.increment(); c.increment();
c.get(); // 2 — `count` is not accessible any other way
```

**Real uses:** data privacy (module pattern), memoization, debounce, event handlers, React hooks (every render's
handlers close over that render's state).

```js
// Debounce: used for TeamBoard's issue search box
function debounce(fn, ms) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), ms);
  };
}
const search = debounce((q) => console.log("search", q), 300);
```

### The classic loop bug

```js
for (var i = 0; i < 3; i++) setTimeout(() => console.log(i), 0); // 3 3 3
for (let i = 0; i < 3; i++) setTimeout(() => console.log(i), 0); // 0 1 2
```

`var` has one binding shared by all callbacks; `let` creates a new binding per iteration.

---

## 6. `this`

`this` is decided by **how a function is called**, not where it's defined (except arrows).

| Call form | `this` |
|---|---|
| `obj.method()` | `obj` |
| `const f = obj.method; f()` | `undefined` in strict mode / modules |
| `f.call(x)`, `f.apply(x)`, `f.bind(x)()` | `x` |
| `new Foo()` | the new object |
| arrow function | `this` of the enclosing scope (lexical) |

```js
const board = {
  name: "Sprint 12",
  print() { console.log(this.name); },
  printLater() { setTimeout(() => console.log(this.name), 0); },     // arrow: works
  printLaterBroken() { setTimeout(function () { console.log(this?.name); }, 0); }, // NOT "Sprint 12": this = window (browser) / Timeout (Node)
};
board.print();              // "Sprint 12"
const p = board.print;
p();                        // TypeError in strict mode (this is undefined)
const bound = board.print.bind(board);
bound();                    // "Sprint 12"
```

**Why it matters for you:** React function components avoid `this` entirely — but you'll still meet it in
older code, class components, and interview questions.

---

## 7. Prototypes (awareness level)

Every object has an internal `[[Prototype]]` link. Property lookup walks the chain. `class` is syntax sugar over this.

```js
class Monitor {
  #failures = 0;                        // truly private field
  constructor(url) { this.url = url; }
  recordFailure() { this.#failures++; }
  get failures() { return this.#failures; }
}
const m = new Monitor("https://example.com");
Object.getPrototypeOf(m) === Monitor.prototype; // true
m.hasOwnProperty("url");        // true
m.hasOwnProperty("recordFailure"); // false — lives on the prototype
```

Interview one-liner: *"JS uses prototypal inheritance: objects delegate missing property lookups to their prototype.
`class` in ES2015 is syntax over constructor functions and prototypes."*

---

## 8. Arrays and objects — the methods you'll use daily

```js
const issues = [
  { id: 1, title: "Login fails", status: "TODO", points: 3 },
  { id: 2, title: "Add labels", status: "DONE", points: 5 },
  { id: 3, title: "CSV export", status: "IN_PROGRESS", points: 2 },
];

issues.map(i => i.title);                        // ["Login fails", ...]
issues.filter(i => i.status !== "DONE");         // open issues
issues.find(i => i.id === 2);                    // object or undefined
issues.some(i => i.points > 4);                  // true
issues.every(i => i.points > 0);                 // true
issues.reduce((sum, i) => sum + i.points, 0);    // 10
issues.findIndex(i => i.id === 3);               // 2
issues.toSorted((a, b) => b.points - a.points);  // ES2023: non-mutating sort

// Group by status (Object.groupBy is ES2024 / Node 21+; reduce works everywhere)
const byStatus = issues.reduce((acc, i) => {
  (acc[i.status] ??= []).push(i);
  return acc;
}, {});

Object.keys(byStatus);    // ["TODO", "DONE", "IN_PROGRESS"]
Object.entries(byStatus); // [["TODO", [...]], ...]
```

**Mutating vs non-mutating** (critical for React):

| Mutates | Returns new |
|---|---|
| `push`, `pop`, `splice`, `sort`, `reverse`, `shift` | `map`, `filter`, `slice`, `concat`, `toSorted`, `toReversed`, `with`, spread `[...a]` |

---

## 9. Destructuring, spread, rest

```js
const { id, title, assignee = null } = issues[0];   // default if undefined
const { status: s } = issues[0];                    // rename
const [first, ...rest] = issues;                    // array rest

const updated = { ...issues[0], status: "IN_PROGRESS" }; // shallow copy + override
const all = [...issues, { id: 4, title: "New", status: "TODO", points: 1 }];

function renderIssue({ title, status }) { return `${title} (${status})`; }
```

> **Break it:** spread is **shallow**. `const copy = { ...issue }; copy.labels.push("bug");` mutates
> the original's `labels` array too. Use `structuredClone(issue)` for a deep copy.

---

## 10. Modules (ESM)

```js
// api/issues.js
export async function listIssues(projectId) { /* ... */ }
export const PAGE_SIZE = 20;
export default function IssueCard() {}

// app.js
import IssueCard, { listIssues, PAGE_SIZE } from "./api/issues.js";
import * as issuesApi from "./api/issues.js";
```

- ESM is **static** (imports resolved before execution), **strict mode by default**, and each module evaluates once (singletons).
- CommonJS (`require` / `module.exports`) is the older Node system; you'll see it in configs.
- In Node, ESM needs `"type": "module"` in `package.json` or `.mjs`. Vite handles this for React apps.

---

## 11. Equality of objects, JSON, dates

```js
({ a: 1 }) === ({ a: 1 });          // false — reference comparison
JSON.stringify({ at: new Date(0) }); // '{"at":"1970-01-01T00:00:00.000Z"}'
JSON.parse('{"at":"1970-01-01T00:00:00.000Z"}').at instanceof Date; // false — it's a string!
```

Spring Boot sends `Instant` as ISO-8601 strings. Your frontend must **parse them explicitly** (`new Date(s)`)
— a typed API client should do this in one place.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `==` comparisons | `===` everywhere |
| `||` for defaults when `0`/`""` are valid | `??` |
| `arr.sort()` on numbers | `arr.sort((a, b) => a - b)` or `toSorted` |
| Mutating state arrays with `push` | spread / `map` / `filter` |
| Detaching a method and losing `this` | arrow function or `.bind` |
| Assuming `JSON.parse` revives dates | parse dates explicitly |
| `for...in` over arrays | `for...of`, or array methods |

---

## Interview Q&A

<details><summary>What's the difference between <code>==</code> and <code>===</code>?</summary>

`===` compares type and value with no conversion. `==` applies the abstract-equality coercion rules
(e.g., `"0" == 0` is true, `null == undefined` is true). I use `===` everywhere; `== null` is the only
idiom some teams allow, to check both `null` and `undefined`.
</details>

<details><summary>What is a closure? Give a real use.</summary>

A function that retains access to variables from the scope it was created in, even after that scope
has returned. Real use: my debounced search in TeamBoard keeps its `timer` variable private inside a
closure; React hooks also rely on closures — each render's handlers see that render's state.
</details>

<details><summary>Explain hoisting and the temporal dead zone.</summary>

Declarations are processed before execution. `var` is hoisted and initialised to `undefined`; function
declarations are hoisted with their body; `let`/`const` are hoisted but uninitialised — accessing them
before the declaration line throws `ReferenceError`. That window is the TDZ.
</details>

<details><summary>How is <code>this</code> determined?</summary>

By the call site: method call → the object; plain call → `undefined` in strict mode; `call/apply/bind`
→ explicit; `new` → the new instance. Arrow functions don't have their own `this`; they capture it lexically.
</details>

<details><summary><code>null</code> vs <code>undefined</code>?</summary>

`undefined` means "not assigned" (missing property, missing argument, no return). `null` is an explicit
"no value" set by a programmer. JSON has `null` but no `undefined` — properties with `undefined` are dropped by `JSON.stringify`.
</details>

<details><summary>How does JS inheritance differ from Java's?</summary>

Java is class-based: classes are blueprints fixed at compile time. JS is prototype-based: objects
link to other objects and delegate property lookups at runtime. `class`/`extends` are syntax over that chain.
</details>

---

## Mastery checklist

- [ ] List the 8 falsy values from memory.
- [ ] Explain `??` vs `||` with a PulseWatch example.
- [ ] Write `debounce` from a blank file and explain its closure.
- [ ] Explain the `var` loop bug and two fixes.
- [ ] Predict `this` in 5 call forms.
- [ ] Group, sum and sort an issue array with `reduce`/`toSorted` without mutation.
- [ ] Explain shallow vs deep copy; use `structuredClone`.
- [ ] Split code into ESM modules with named + default exports.
