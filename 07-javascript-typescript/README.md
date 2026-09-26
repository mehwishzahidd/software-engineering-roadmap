# 07 — JavaScript & TypeScript

> **JavaScript in Week 6, TypeScript in Week 7** ([ROADMAP §6](../ROADMAP.md#6-just-in-time-learning-map)) — just before
> **FlowGrid M4**, the React/TS operations dashboard. Revisited in **Week 16** (ForgeCI's live build-log UI over SSE)
> and **Week 23** (FlagForge's admin dashboard).
> Budget: ≈ 3–4 h of Week 6's learning block for JS, ≈ 2–3 h of Week 7's for TS; everything else is practice inside FlowGrid.

This is **not** a frontend-specialist track. You are a Java backend engineer who must build, debug and explain the
dashboards of your own projects, and survive the JavaScript/TypeScript questions an interviewer will ask because both
are on your résumé.

---

## Why JS before TS before React

| If you skip… | You get… |
|---|---|
| JS core (scope, closures, `this`) | Stale-closure bugs in React effects you can't explain |
| The event loop | "Why did my `console.log` run before the fetch finished?" |
| Promises / `async` | Unhandled rejections; out-of-order responses overwriting newer ones |
| TypeScript narrowing | `as any` everywhere, runtime crashes on API data |

---

## Files in this folder

| File | Topic | Time | Week |
|---|---|---:|---:|
| [01-javascript-core.md](./01-javascript-core.md) | Types, coercion, `===`, scope, hoisting, closures, `this`, prototypes, array/object methods, destructuring, ESM | 1.5 h | 6 |
| [02-async-javascript.md](./02-async-javascript.md) | Event loop, micro/macrotasks, promises, `async`/`await`, errors, `fetch`, `AbortController`, `EventSource` (SSE) | 1.5 h | 6 (SSE revisited W16) |
| [03-typescript.md](./03-typescript.md) | TS 5: types, interfaces vs aliases, unions, narrowing, discriminated unions, generics, utility types, `unknown`, strict mode, typing API responses, zod | 2 h | 7 |
| [04-html-css-minimum.md](./04-html-css-minimum.md) | Just enough HTML/CSS: semantic markup, forms, tables, flexbox | ≈ 3 h total | 6–7 |
| [exercises.md](./exercises.md) | Predict-the-output drills, build tasks, break/debug tasks | 3 h | 6–7 |

---

## Environment setup (30 min)

```bash
# Node 20+ (LTS). Use a version manager (nvm, fnm, volta) so projects can pin versions.
node --version     # v20.x or v22.x
npm --version

# Scratch playground for this folder (throwaway — not a portfolio repo)
mkdir js-lab && cd js-lab
npm init -y
npm pkg set type=module          # enable ESM (import/export) in .js files
npm i -D typescript tsx @types/node
npx tsc --init --strict --target es2022 --module nodenext
npx tsx hello.ts                 # run TS directly without a build step
```

Use the browser DevTools console for quick checks and **Node + `tsx`** for anything longer than three lines.

---

## Where this fits in the week

Weeks 6–7 are FlowGrid weeks (≈ 28 h project, ≈ 7 h learning). JS/TS learning uses the Mon/Wed learning blocks:

| Week | Mon learning (2 h) | Wed learning (2 h) | Project tie-in |
|---|---|---|---|
| 6 | [01-javascript-core](./01-javascript-core.md) + exercises Part A | [02-async-javascript](./02-async-javascript.md) + exercises Part B | Call your FlowGrid API from a Node script with `fetch` (list SKUs, create an order with an `Idempotency-Key`, read a ProblemDetail) |
| 7 | [03-typescript](./03-typescript.md) + exercises Part C | [08-react](../08-react/README.md) begins | FlowGrid M4: typed API client + dashboard; HTML/CSS as needed ([04](./04-html-css-minimum.md)) |
| 16 | revisit [02 §8 SSE](./02-async-javascript.md#8-server-sent-events-eventsource) | — | ForgeCI M3: live log view |
| 23 | revisit [03 §5 discriminated unions](./03-typescript.md#5-discriminated-unions-the-most-useful-ts-pattern) | — | FlagForge M4: rules editor types |

---

## Mapping to projects

| Concept | Where you use it |
|---|---|
| Closures, `this` | Event handlers and effects in the FlowGrid dashboard |
| Event loop, promises | Debugging request ordering on the orders list; polling low-stock alerts |
| `AbortController` | Cancelling a stale SKU search when the operator keeps typing |
| `EventSource` (SSE) | ForgeCI live build logs with reconnect + replay; FlagForge config propagation |
| Discriminated unions | FlowGrid `OrderStatus`, ForgeCI `JobResult` (`SUCCEEDED` / `FAILED` / `TIMED_OUT` / `CANCELLED`), FlagForge evaluation reasons |
| `unknown` + zod | Validating API JSON before trusting it |
| Utility types | `CreateOrderRequest = Omit<Order, "id" \| "status" \| "createdAt">` |

---

## Interview relevance

JS/TS questions for a junior full-stack/backend role cluster around:

1. `==` vs `===`, truthy/falsy, `null` vs `undefined`.
2. `var` vs `let` vs `const`, hoisting, TDZ.
3. Closures — define, give a real use, show the loop bug.
4. `this` in regular vs arrow functions.
5. The event loop — order of `setTimeout`, promise, `console.log`.
6. Promises vs `async`/`await`; `Promise.all` vs `allSettled`.
7. TS: `interface` vs `type`, `any` vs `unknown`, generics, narrowing.

Deeper résumé-defense drills: [17-resume-tech-defense/javascript.md](../17-resume-tech-defense/javascript.md)
and [17-resume-tech-defense/typescript.md](../17-resume-tech-defense/typescript.md).

---

## Resources

- **MDN** — developer.mozilla.org (JavaScript Guide + Reference). The single best JS reference.
- **TypeScript Handbook** — typescriptlang.org/docs
- *You Don't Know JS Yet* (Kyle Simpson) — scope & closures, `this`.
- *Eloquent JavaScript* (Marijn Haverbeke) — free, good for fundamentals.
- More in [RESOURCES.md](../RESOURCES.md).

---

## Exit criteria (before FlowGrid M4 work starts in earnest)

- [ ] I can predict the output of a mixed `setTimeout` / `Promise` / `await` snippet and explain why.
- [ ] I can write a closure-based counter and explain the `var`-in-loop bug.
- [ ] I can explain `this` for method call, detached call, arrow function, and `bind`.
- [ ] I can write a typed `fetch` wrapper that handles non-2xx, ProblemDetail and `AbortController`.
- [ ] I can model an API response with a discriminated union and narrow it without casts.
- [ ] I know when to reach for `unknown`, and why `any` is a smell.
- [ ] I can build a form + table page with flexbox without Googling every property.
- [ ] Exercises in [exercises.md](./exercises.md) done; tracker updated in [trackers/technology-tracker.md](../trackers/technology-tracker.md).
