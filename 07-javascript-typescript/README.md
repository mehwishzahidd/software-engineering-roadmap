# 07 — JavaScript & TypeScript

> **Weeks 14–15** of the [ROADMAP](../ROADMAP.md). Budget: ≈ 14 hours of the 45-hour JS/TS/React allocation.
> Goal: write JavaScript and TypeScript you can **defend line by line** — not framework-shaped guesswork.

This is **not** a frontend-specialist track. You are a Java backend engineer who must be able to
build, debug and explain the React client of **P3 TeamBoard** and the dashboard of **P4 PulseWatch**,
and survive the JavaScript/TypeScript questions an interviewer will ask because both are on your résumé.

---

## Why JS before TS before React

| If you skip… | You get… |
|---|---|
| JS core (scope, closures, `this`) | Stale-closure bugs in React effects you can't explain |
| The event loop | "Why did my `console.log` run before the fetch finished?" |
| Promises / `async` | Unhandled rejections, race conditions between requests |
| TypeScript narrowing | `as any` sprinkled everywhere, runtime crashes on API data |

---

## Files in this folder

| File | Topic | Time | Week |
|---|---|---:|---:|
| [01-javascript-core.md](./01-javascript-core.md) | Types, coercion, `===`, scope, hoisting, closures, `this`, prototypes, array/object methods, destructuring, ESM | 4 h | 14 |
| [02-async-javascript.md](./02-async-javascript.md) | Event loop, micro/macrotasks, promises, `async`/`await`, errors, `fetch`, `AbortController` | 3.5 h | 14 |
| [03-typescript.md](./03-typescript.md) | TS 5: types, interfaces vs aliases, unions, narrowing, generics, utility types, `unknown`, strict mode, typing API responses, zod | 4 h | 15 |
| [04-html-css-minimum.md](./04-html-css-minimum.md) | Just enough HTML/CSS: semantic markup, forms, tables, flexbox | 3 h (total) | 14 |
| [exercises.md](./exercises.md) | Predict-the-output drills, build tasks, break/debug tasks | 3 h | 14–15 |

---

## Environment setup (30 min)

```bash
# Node 20+ (LTS). Use a version manager so projects can pin versions.
node --version     # v20.x or v22.x
npm --version

# Scratch playground for this folder
mkdir js-lab && cd js-lab
npm init -y
npm pkg set type=module          # enable ESM (import/export) in .js files
npm i -D typescript tsx @types/node
npx tsc --init --strict --target es2022 --module nodenext
npx tsx hello.ts                 # run TS directly without a build step
```

Use the browser DevTools console for quick checks and **Node + `tsx`** for anything longer than three lines.

---

## Week plan

### Week 14 (JavaScript)

| Day | Block |
|---|---|
| Mon | [01-javascript-core](./01-javascript-core.md) §1–§4 (types, coercion, scope, hoisting) |
| Tue | §5–§7 (closures, `this`, prototypes) + exercises 1–6 |
| Wed | P3 TeamBoard backend work (design doc, schema) |
| Thu | [02-async-javascript](./02-async-javascript.md) event loop + promises; ordering puzzle |
| Fri | Light: [04-html-css-minimum](./04-html-css-minimum.md) forms + flexbox |
| Sat | P3 + exercises 7–14 (async, `fetch` against your Spring API with `curl`-equivalent JS) |
| Sun | Review: predict-the-output quiz, explain the event loop out loud |

### Week 15 (TypeScript + start React)

| Day | Block |
|---|---|
| Mon | [03-typescript](./03-typescript.md) §1–§5 |
| Tue | §6–§10 (generics, utility types, API typing) + exercises 15–20 |
| Thu–Sat | Move into [08-react](../08-react/README.md) |

---

## Mapping to projects

| Concept | Where you use it |
|---|---|
| Closures, `this` | React event handlers and effects in TeamBoard |
| Event loop, promises | Debugging request ordering on the PulseWatch dashboard (polling) |
| `AbortController` | Cancelling a stale issue search when the user keeps typing |
| Discriminated unions | Modelling `IssueStatus` and `CheckResult` (`UP` / `DOWN` / `DEGRADED`) |
| `unknown` + zod | Validating API JSON before trusting it |
| Utility types | `CreateIssueRequest = Omit<Issue, "id" \| "createdAt">` |

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

## Exit criteria (before Week 16)

- [ ] I can predict the output of a mixed `setTimeout` / `Promise` / `await` snippet and explain why.
- [ ] I can write a closure-based counter and explain the `var`-in-loop bug.
- [ ] I can explain `this` for method call, detached call, arrow function, and `bind`.
- [ ] I can write a typed `fetch` wrapper with timeouts, error handling and `AbortController`.
- [ ] I can model an API response with a discriminated union and narrow it without casts.
- [ ] I know when to reach for `unknown`, and why `any` is a smell.
- [ ] I can build a form + table page with flexbox without Googling every property.
- [ ] Exercises in [exercises.md](./exercises.md) done; tracker updated in [trackers/technology-tracker.md](../trackers/technology-tracker.md).
