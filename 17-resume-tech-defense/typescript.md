# TypeScript — Résumé Tech Defense

> **Goal:** defend "TypeScript" on your résumé with truthful past context plus current TS 5
> fluency: the type system, narrowing, generics, `tsconfig` strictness, and typing a real API
> client and React components (TeamBoard, PulseWatch dashboard).
>
> **Honesty rule:** if your past TS usage was light (e.g. adding types to an existing codebase),
> say so. Then show depth through what you built here.

---

## Evidence in my projects

| Project | Where TypeScript shows up | What I can point at |
|---|---|---|
| **P3 TeamBoard** (W16–18) | Entire Vite frontend in `strict` TS; DTO types mirroring Spring DTOs; discriminated unions for request state; typed role checks | `src/api/types.ts` (`Issue`, `IssueStatus` union, `Page<T>`), `src/api/client.ts` generic `request<T>()`, `can(role, action)` helper |
| **P3 tests** (W17) | Vitest + RTL tests are TS | typed mock responses using `satisfies` |
| **P4 PulseWatch dashboard** (W22) | Status dashboard in TS | `MonitorStatus = "UP" \| "DOWN" \| "DEGRADED"`, exhaustive `switch` with `never` |

## Where to learn it in this repo

- [`../07-javascript-typescript/03-typescript.md`](../07-javascript-typescript/03-typescript.md) — types, interfaces, unions, narrowing, generics, `tsconfig`
- [`../07-javascript-typescript/01-javascript-core.md`](../07-javascript-typescript/01-javascript-core.md) — what TS compiles to
- [`../08-react/04-api-integration-auth.md`](../08-react/04-api-integration-auth.md) — typed API client
- [`../07-javascript-typescript/exercises.md`](../07-javascript-typescript/exercises.md)
- Related: [`javascript.md`](./javascript.md), [`react.md`](./react.md)

---

## 1. Beginner questions

<details><summary><b>B1. What is TypeScript and what happens to types at runtime?</b></summary>

A statically typed superset of JavaScript. `tsc` (or esbuild/SWC inside Vite) type-checks and emits JS; **types are erased** — no runtime checks. So data from the network is `unknown` in reality, whatever your type says. Vite transpiles without type-checking; run `tsc --noEmit` in CI.
</details>

<details><summary><b>B2. <code>interface</code> vs <code>type</code>?</b></summary>

Both describe object shapes. `interface` supports declaration merging and `extends`; `type` can express unions, intersections, mapped and conditional types, tuples. Rule of thumb: `type` for unions/utility compositions, `interface` for public object contracts; consistency matters more than the choice.
</details>

<details><summary><b>B3. <code>any</code> vs <code>unknown</code> vs <code>never</code>?</b></summary>

`any` disables checking (contagious). `unknown` is the type-safe top type — must narrow before use. `never` is the empty type: functions that throw, impossible branches, exhaustiveness checks.
</details>

<details><summary><b>B4. What is a union type and how do you narrow it?</b></summary>

`string | number`. Narrow with `typeof`, `instanceof`, `in`, equality checks, truthiness, discriminant properties, or user-defined type guards (`x is Foo`).
</details>

<details><summary><b>B5. What are optional properties and <code>readonly</code>?</b></summary>

`title?: string` → `string | undefined` (with `exactOptionalPropertyTypes` it means "may be absent"). `readonly id: number` blocks reassignment at compile time; `ReadonlyArray<T>` / `readonly T[]` removes mutating methods.
</details>

<details><summary><b>B6. Name five utility types.</b></summary>

`Partial<T>`, `Required<T>`, `Pick<T,K>`, `Omit<T,K>`, `Record<K,V>`, `Readonly<T>`, `ReturnType<F>`, `Awaited<T>`, `NonNullable<T>`. Example: `type IssueUpdate = Partial<Pick<Issue, "title" | "status" | "assigneeId">>`.
</details>

<details><summary><b>B7. What does <code>strict: true</code> enable?</b></summary>

`strictNullChecks`, `noImplicitAny`, `strictFunctionTypes`, `strictBindCallApply`, `strictPropertyInitialization`, `alwaysStrict`, `useUnknownInCatchVariables`, `noImplicitThis`. Also worth enabling: `noUncheckedIndexedAccess`.
</details>

## 2. Intermediate questions

<details><summary><b>I1. What are discriminated unions? Show one from your code.</b></summary>

```ts
type Load<T> =
  | { state: "idle" }
  | { state: "loading" }
  | { state: "error"; error: ApiError }
  | { state: "success"; data: T };
```
`switch (s.state)` narrows each branch; impossible states (data + error) can't be represented.
</details>

<details><summary><b>I2. How do generics work? Write a typed <code>request</code> function.</b></summary>

```ts
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, init);
  if (!res.ok) throw await ApiError.from(res);
  return (await res.json()) as T; // assertion: runtime not checked
}
const page = await request<Page<Issue>>("/orgs/1/issues?page=0");
```
Constraints: `<T extends { id: number }>`. Mention the `as T` is a trust boundary — validate with zod if the data is untrusted.
</details>

<details><summary><b>I3. Exhaustiveness checking with <code>never</code>.</b></summary>

```ts
function color(s: MonitorStatus) {
  switch (s) {
    case "UP": return "green"; case "DOWN": return "red"; case "DEGRADED": return "amber";
    default: { const x: never = s; return x; }
  }
}
```
Adding a new status becomes a compile error everywhere it isn't handled.
</details>

<details><summary><b>I4. Structural typing — what does it mean?</b></summary>

Compatibility is by shape, not by declared name (unlike Java's nominal typing). Any object with the right properties satisfies an interface. Consequence: two ID types `number` are interchangeable — use branded types (`number & { __brand: "OrgId" }`) if mixing them is a real risk.
</details>

<details><summary><b>I5. <code>as</code> vs <code>satisfies</code>?</b></summary>

`as` asserts (overrides the checker; can hide errors). `satisfies` (TS 4.9+) checks a value against a type while keeping the value's narrower inferred type:
```ts
const routes = { board: "/board", issue: "/issues/:id" } satisfies Record<string, string>;
```
</details>

<details><summary><b>I6. Mapped and conditional types — one practical example each.</b></summary>

Mapped: `type Flags<T> = { [K in keyof T]: boolean }` (form "touched" state). Conditional: `type Unwrap<T> = T extends Promise<infer U> ? U : T`. Template literal types: `` type Perm = `${"issue"|"comment"}:${"read"|"write"}` ``.
</details>

<details><summary><b>I7. How do you keep frontend types in sync with Spring DTOs?</b></summary>

Options: hand-written types reviewed with DTO changes (TeamBoard, small API); generate from OpenAPI (springdoc → `openapi-typescript`) for larger APIs; runtime validation with zod at the boundary. Trade-off: generation adds a build step but removes drift.
</details>

<details><summary><b>I8. What's <code>keyof</code> and indexed access?</b></summary>

`keyof Issue` → union of property names. `Issue["status"]` → the property type. Combined: `function sortBy<K extends keyof Issue>(k: K)`.
</details>

## 3. Realistic interview questions

<details><summary><b>R1. "Why did you use TypeScript instead of plain JavaScript?"</b></summary>

Catch shape mismatches with the API at compile time, safe refactors, self-documenting props, editor tooling. Concrete: renaming `assignee` → `assigneeId` in the DTO produced compile errors in 6 components instead of runtime `undefined`s.
</details>

<details><summary><b>R2. "How do you type an API response you don't fully trust?"</b></summary>

Treat it as `unknown`, validate (zod schema / type guard), then narrow. For a first-party API I control, a typed assertion at one boundary is acceptable if there's a contract test; say where the boundary is.
</details>

<details><summary><b>R3. "How do you model user roles in the frontend?"</b></summary>

`type Role = "OWNER" | "ADMIN" | "MEMBER" | "VIEWER"`; a `can(role, action)` function with a typed permission map; UI hides actions, **backend enforces** — the frontend check is UX, not security.
</details>

<details><summary><b>R4. "Explain a TypeScript error you had to fight."</b></summary>

Truthful example from TeamBoard: `Object is possibly 'undefined'` after `noUncheckedIndexedAccess`; or a union not narrowing because the check happened in a helper — fixed with a type guard `isApiError(e): e is ApiError`.
</details>

<details><summary><b>R5. "How is TypeScript's type system different from Java's?"</b></summary>

Structural vs nominal; erased vs reified-at-runtime classes (Java generics are also erased, but classes exist at runtime); unions/literal types; no overloading by runtime type; `null` handled via `strictNullChecks` rather than `Optional`.
</details>

<details><summary><b>R6. "Your résumé says TypeScript — when did you last use it professionally?"</b></summary>

State the real date and context. "Since then I've built TeamBoard's frontend in strict TS 5 — here's the typed client and the discriminated-union load state." Don't round dates up.
</details>

## 4. Practical tasks (doable live)

1. Type `groupBy<T, K extends PropertyKey>(items: T[], key: (t: T) => K): Record<K, T[]>`.
2. Convert a JS API module to TS with `strict` and zero `any`.
3. Write a type guard `isProblemDetail(x: unknown): x is ProblemDetail`.
4. Model a form state where `submitting` and `error` can't both be set.
5. Write `DeepReadonly<T>`.
6. Fix a set of compile errors in a provided snippet and explain each.

## 5. Debugging questions

<details><summary><b>D1. The build passes locally in Vite but CI's type-check fails.</b></summary>

Vite/esbuild strips types without checking. CI runs `tsc --noEmit` (or `vue-tsc`/`tsc -b`). Run the same command locally; add it to a pre-push script.
</details>

<details><summary><b>D2. Types say <code>issue.assignee.name</code> exists, runtime crashes.</b></summary>

The API returns `assignee: null` for unassigned issues; the type lied. Fix the type (`assignee: User | null`), handle null in UI, add a test fixture with null.
</details>

<details><summary><b>D3. <code>as Issue[]</code> everywhere and bugs slip through.</b></summary>

Assertions bypass checking. Replace with typed `request<T>` at one boundary + validation, remove casts in components.
</details>

<details><summary><b>D4. "Type 'string' is not assignable to type 'IssueStatus'."</b></summary>

A literal widened to `string` (e.g. from `useState("TODO")` or an object literal). Fix: annotate (`useState<IssueStatus>("TODO")`) or `as const`.
</details>

## 6. Architecture questions

<details><summary><b>A1. Where do shared types live in a frontend?</b></summary>

`src/api/types.ts` (wire types matching DTOs) separate from view-model types; components import from the API layer. Don't let backend entity shapes leak through the entire UI.
</details>

<details><summary><b>A2. Hand-written types vs OpenAPI codegen for TeamBoard?</b></summary>

Hand-written for ~15 endpoints and one developer; codegen once multiple teams/clients consume the API. Either way, the Spring side remains the source of truth.
</details>

## 7. Common mistakes

- Sprinkling `any` / `as` to silence errors.
- Believing types validate runtime data.
- Not enabling `strict` (or turning it off to "fix" errors).
- Over-engineering types (5-level conditional types for a CRUD form).
- Duplicating the same union in many files instead of exporting one.
- Using `enum` where a string-literal union is simpler (enums emit runtime code; `const enum` has tooling pitfalls).

## 8. Terminology I must know

| Term | Meaning |
|---|---|
| Type erasure | Types removed in emitted JS |
| Structural typing | Compatibility by shape |
| Narrowing | Refining a union via control flow |
| Discriminated union | Union with a shared literal tag property |
| Type guard | Function returning `x is T` |
| Generic constraint | `T extends X` |
| Utility types | Built-in mapped types (`Partial`, `Pick`, ...) |
| `satisfies` | Check without widening |
| Declaration file | `.d.ts` types for JS libs |
| `tsconfig` | Compiler options, `strict`, `target`, `moduleResolution` |
| Literal type | `"UP"` as a type |
| `as const` | Deep readonly literal inference |

## 9. When to use it

- Any frontend or Node code that more than one person maintains or that lives > a few weeks.
- API clients, shared contracts, component props.

## 10. When NOT to use it

- Throwaway scripts where setup cost > value (plain JS or Python).
- When you'd just be writing `any` everywhere — fix the design first.
- Heavy type-level programming nobody on the team can read.

## 11. Trade-offs

| Gain | Cost |
|---|---|
| Compile-time safety, refactoring | Build/type-check step, learning curve |
| Self-documenting APIs | Types can drift from backend without codegen/validation |
| Great IDE support | False confidence at runtime boundaries |

## 12. How it interacts with the rest of my stack

- **Spring Boot DTOs (Java records)** ↔ TS types; Jackson serialises `Instant` as ISO strings → type as `string`, parse at the edge.
- **ProblemDetail** errors ↔ `ApiError` type.
- **React:** typed props, `useState<T>`, typed context.
- **CI:** `tsc --noEmit` + `eslint` + `vitest run` in GitHub Actions before the Docker build.
- **Docker:** multi-stage Node build stage compiles TS → static assets copied into nginx.

## 13. One small hands-on exercise

**Typed TeamBoard API module.**

- [ ] `types.ts` with `Issue`, `IssueStatus`, `Role`, `Page<T>`, `ProblemDetail` matching the Spring DTOs.
- [ ] `request<T>` with `ApiError` on non-2xx; `isProblemDetail` type guard.
- [ ] `Load<T>` discriminated union + a component rendering all four states with an exhaustive `switch`.
- [ ] `strict` + `noUncheckedIndexedAccess` on; zero `any`, zero `as` outside `request`.
- [ ] `tsc --noEmit` passes in CI.

## 14. Mastery checklist

- [ ] Explain erasure and why runtime validation still matters
- [ ] Write discriminated unions + exhaustive switch from memory
- [ ] Write generic functions with constraints
- [ ] Use and explain 6 utility types
- [ ] Explain `satisfies` vs `as`
- [ ] Explain structural vs nominal typing with a Java comparison
- [ ] Truthful 60-second answer on past TS use + TeamBoard bridge
