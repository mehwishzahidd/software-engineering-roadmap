# 03 — TypeScript 5

> Week 7 · ≈ 2 hours + exercises, then daily use in FlowGrid M4. TypeScript = JavaScript + a **compile-time** type system.
> Types are erased at runtime: TS protects you from your own code, **not from the network**. Keep that sentence in mind for §9.

---

## 1. Setup and strict mode

```jsonc
// tsconfig.json (what Vite's react-ts template gives you, trimmed)
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "strict": true,                      // non-negotiable
    "noUncheckedIndexedAccess": true,    // arr[i] is T | undefined — catches real bugs
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "skipLibCheck": true,
    "noEmit": true                       // Vite/esbuild transpiles; tsc only type-checks
  },
  "include": ["src"]
}
```

`strict: true` enables, among others: `strictNullChecks` (no silent `null`), `noImplicitAny`,
`strictFunctionTypes`, `useUnknownInCatchVariables` (catch variable is `unknown`).

Run `npx tsc --noEmit` in CI — Vite's dev server does **not** type-check.

---

## 2. Basic types and inference

```ts
let sku = "BOLT-M8-50";             // inferred string
const status = "RESERVED";          // inferred literal type "RESERVED" (const!)
let quantity: number = 3;
const tags: string[] = ["fragile", "hazmat"];
const pair: [number, string] = [1, "one"];   // tuple

function totalUnits(lines: { quantity: number }[]): number {
  return lines.reduce((a, l) => a + l.quantity, 0);
}

// Annotate function parameters and public return types; let inference do locals.
```

Special types:

| Type | Meaning |
|---|---|
| `any` | Turn off checking. Contagious. Avoid. |
| `unknown` | "Some value; prove what it is before using it." Safe `any`. |
| `never` | No value possible (exhaustive checks, functions that always throw) |
| `void` | Function returns nothing useful |
| `null` / `undefined` | Distinct types under strict mode |

---

## 3. Object types: `interface` vs `type`

```ts
// FlowGrid — what the dashboard receives from GET /api/orders/{id}
interface Order {
  id: number;
  number: string;                 // "SO-1042"
  warehouseId: number | null;     // null until allocated
  note?: string;                  // optional → string | undefined
  readonly createdAt: string;     // ISO-8601 from Spring (Instant)
  status: OrderStatus;
  lines: OrderLine[];
}

type OrderLine = { skuId: number; sku: string; quantity: number };
type OrderStatus = "PENDING" | "RESERVED" | "ALLOCATED" | "PICKING" | "PACKED" | "SHIPPED" | "CANCELLED";
```

(Use *your* FlowGrid state machine's names; these are illustrative.)

| | `interface` | `type` alias |
|---|---|---|
| Object shapes | ✅ | ✅ |
| Unions, tuples, primitives, mapped types | ❌ | ✅ |
| `extends` | `interface A extends B` | `type A = B & { ... }` |
| Declaration merging (re-open) | ✅ | ❌ |

**Practical rule:** `type` for unions/utility compositions, `interface` for object shapes you might extend.
Consistency within a codebase matters more than the choice. TS is **structural**: any object with the right
shape is assignable — unlike Java's nominal typing.

---

## 4. Unions, literal types, narrowing

```ts
function badgeColor(status: OrderStatus): string {
  switch (status) {
    case "PENDING": return "gray";
    case "RESERVED":
    case "ALLOCATED": return "blue";
    case "PICKING":
    case "PACKED": return "purple";
    case "SHIPPED": return "green";
    case "CANCELLED": return "red";
  }
}
```

Narrowing techniques:

```ts
function format(v: string | number | null | Date): string {
  if (v === null) return "—";                    // equality
  if (typeof v === "string") return v.trim();    // typeof
  if (v instanceof Date) return v.toISOString(); // instanceof
  return v.toFixed(0);                           // only number is left
}

type Manager = { role: "OPS_MANAGER"; warehouseIds: number[] };
type Viewer = { role: "VIEWER" };
function canAdjust(u: Manager | Viewer, warehouseId: number) {
  return "warehouseIds" in u && u.warehouseIds.includes(warehouseId); // `in` narrowing
}

// Custom type guard
function isOrder(x: unknown): x is Order {
  return typeof x === "object" && x !== null && "id" in x && "number" in x && "lines" in x;
}
```

### `as const` and deriving types from values

```ts
export const ROLES = ["ADMIN", "OPS_MANAGER", "WAREHOUSE_ASSOCIATE", "VIEWER"] as const;
export type Role = (typeof ROLES)[number];   // "ADMIN" | "OPS_MANAGER" | "WAREHOUSE_ASSOCIATE" | "VIEWER"
```

One source of truth: the role dropdown iterates `ROLES`, the type follows.

---

## 5. Discriminated unions (the most useful TS pattern)

A union of object types sharing a literal **discriminant** property.

```ts
// ForgeCI (Week 16): the result of one job, as the build page shows it
type JobResult =
  | { kind: "SUCCEEDED"; durationMs: number }
  | { kind: "FAILED"; exitCode: number; failedStep: string }      // app failure: no retry
  | { kind: "TIMED_OUT"; timeoutMs: number }
  | { kind: "CANCELLED"; cancelledBy: string };

function describe(r: JobResult): string {
  switch (r.kind) {
    case "SUCCEEDED": return `passed in ${(r.durationMs / 1000).toFixed(1)} s`;
    case "FAILED": return `step "${r.failedStep}" exited with ${r.exitCode}`;
    case "TIMED_OUT": return `killed after ${r.timeoutMs / 1000} s`;
    case "CANCELLED": return `cancelled by ${r.cancelledBy}`;
    default: {
      const unreachable: never = r;   // compile error if a new kind is added and not handled
      return unreachable;
    }
  }
}

// Request state for any UI view
type AsyncState<T> =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "error"; error: string }
  | { status: "success"; data: T };
```

`AsyncState` makes "loading and error at the same time" **unrepresentable** — better than three booleans.
FlagForge (Week 23) models evaluation reasons the same way: `RULE_MATCH` (with `ruleId`) / `ROLLOUT` (with `bucket`) / `DEFAULT` / `FLAG_OFF`.

> **Break it:** add `| { kind: "INFRA_ERROR"; attempt: number }` to `JobResult`. The `never` line now fails to compile — that's the point.

---

## 6. Generics

```ts
function first<T>(items: T[]): T | undefined {
  return items[0];
}
first([1, 2]);        // number | undefined
first(["a"]);         // string | undefined

// Constraint
function byId<T extends { id: number }>(items: T[], id: number): T | undefined {
  return items.find((i) => i.id === id);
}

// Generic type — a paged response from your API
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;   // current page (0-based)
  size: number;
}

// Generic + keyof
function pluck<T, K extends keyof T>(items: T[], key: K): T[K][] {
  return items.map((i) => i[key]);
}
declare const orders: Order[];
pluck(orders, "number");   // string[]
// pluck(orders, "nope");  // compile error
```

Heads-up: Spring Boot 3.3+ warns about serializing `PageImpl` directly and recommends `PagedModel` or your own DTO
(whose JSON shape differs). Type **what your API actually returns** — copy a real response from `curl`.
LedgerX's history API uses **cursor** pagination instead: `{ items: T[]; nextCursor: string | null }`.

---

## 7. Utility types

```ts
type CreateOrderRequest = Pick<Order, "note"> & { lines: Array<Pick<OrderLine, "skuId" | "quantity">> };
type OrderSummary = Omit<Order, "lines">;
type UpdateSkuRequest = Partial<Pick<Sku, "name" | "reorderPoint">>;
type CountByStatus = Record<OrderStatus, number>;
type ReadonlyOrder = Readonly<Order>;
type AssignedWarehouse = NonNullable<Order["warehouseId"]>;        // number
declare function loadOrders(): Promise<Order[]>;
type LoadResult = Awaited<ReturnType<typeof loadOrders>>;          // Order[]

type Sku = { id: number; code: string; name: string; reorderPoint: number };

const counts: CountByStatus = {
  PENDING: 2, RESERVED: 5, ALLOCATED: 1, PICKING: 3, PACKED: 0, SHIPPED: 40, CANCELLED: 1,
};
// Missing a key → compile error. Great for exhaustive dashboard columns.
```

| Utility | Does |
|---|---|
| `Partial<T>` / `Required<T>` | all props optional / required |
| `Pick<T, K>` / `Omit<T, K>` | keep / drop keys |
| `Record<K, V>` | object with keys K, values V |
| `Readonly<T>` | shallow readonly |
| `ReturnType<F>`, `Parameters<F>`, `Awaited<P>` | derive from functions/promises |
| `NonNullable<T>` | strip `null`/`undefined` |

---

## 8. `unknown` vs `any`

```ts
const a: any = JSON.parse(text);
a.foo.bar.baz();        // compiles, explodes at runtime

const u: unknown = JSON.parse(text);
// u.foo;               // compile error — must narrow first
if (typeof u === "object" && u !== null && "foo" in u) { /* ... */ }

try { /* ... */ } catch (e) {        // e: unknown under strict
  const msg = e instanceof Error ? e.message : String(e);
}
```

Use `unknown` at trust boundaries (JSON, `catch`, `localStorage`, SSE `event.data`). `any` only as a deliberate,
commented escape hatch. `as` casts are lies you tell the compiler — each one should be justifiable in code review.

---

## 9. Typing API responses — and validating them

`res.json()` returns `Promise<any>`. Writing `as Order[]` compiles, but if the backend renames `number` to `orderNumber`,
you crash far from the cause. Two options:

**Option A — typed at the boundary, trusted (fine for your own backend, covered by tests):**

```ts
async function getJson<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init);
  if (!res.ok) throw new ApiError(res.status, await res.text());
  return (await res.json()) as T;   // the ONE cast in the app, in one place
}
```

**Option B — validated with zod (recommended for data you don't fully control, and a good interview talking point):**

```ts
import { z } from "zod";

export const InventoryLevelSchema = z.object({
  skuId: z.number().int(),
  sku: z.string().min(1),
  warehouseId: z.number().int(),
  onHand: z.number().int().nonnegative(),
  reserved: z.number().int().nonnegative(),
  available: z.number().int(),                      // may go negative only if you have a bug — let the UI show it
  updatedAt: z.string().datetime({ offset: true }), // ISO-8601 from Spring (Instant ends with "Z")
});
export type InventoryLevel = z.infer<typeof InventoryLevelSchema>;   // type derived from schema — no drift

export async function fetchInventory(warehouseId: number, signal?: AbortSignal): Promise<InventoryLevel[]> {
  const data = await getJson<unknown>(`/api/warehouses/${warehouseId}/inventory`, { signal });
  return z.array(InventoryLevelSchema).parse(data);   // throws ZodError with a precise path if shape is wrong
}
```

(`z.string().datetime()` is zod 3 syntax; zod 4 also offers `z.iso.datetime()`. Check the version you install.)

```ts
export class ApiError extends Error {
  constructor(public readonly status: number, public readonly body: string) {
    super(`API error ${status}`);
    this.name = "ApiError";
  }
}
```

---

## 10. Typing functions and callbacks

```ts
type Comparator<T> = (a: T, b: T) => number;
const byAvailableAsc: Comparator<InventoryLevel> = (a, b) => a.available - b.available;

type StatusChangeHandler = (event: { orderId: number; to: OrderStatus }) => void;

// Optional param + default
function paginate<T>(items: T[], page = 0, size = 20): T[] {
  return items.slice(page * size, page * size + size);
}
```

---

## 11. Enums? Prefer union literals

```ts
enum Status { Reserved = "RESERVED", Shipped = "SHIPPED" }   // emits a runtime object
type Status2 = "RESERVED" | "SHIPPED";                        // zero runtime cost, matches JSON directly
```

Most modern TS codebases prefer string-literal unions + `as const` arrays. Know that `enum` exists for reading legacy code.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `as SomeType` on `res.json()` scattered everywhere | one typed boundary, or zod |
| `any` to silence errors | `unknown` + narrowing; fix the real type |
| Three booleans `isLoading/isError/data` | discriminated union `AsyncState<T>` |
| Non-null assertion `order!.warehouseId!` everywhere | handle `null`; narrow once |
| Assuming TS validates runtime data | it doesn't — types are erased |
| Duplicating the same type by hand in 5 files | derive with `Pick/Omit/z.infer` |
| Turning off `strict` to "make it compile" | never |

---

## Interview Q&A

<details><summary><code>interface</code> vs <code>type</code>?</summary>

Both describe object shapes. `type` can also name unions, tuples, primitives and mapped/conditional types;
`interface` supports declaration merging and `extends`. I use `type` for unions and compositions, `interface`
for extendable object shapes, and follow the codebase's convention.
</details>

<details><summary><code>any</code> vs <code>unknown</code>?</summary>

`any` disables type checking and spreads silently. `unknown` accepts any value but forces you to narrow
before use. I use `unknown` for parsed JSON, SSE payloads, `catch` variables and other untrusted inputs.
</details>

<details><summary>What is a discriminated union and why use it?</summary>

A union of object types that share a literal field (e.g. `kind`). Switching on it narrows to the exact member,
and an exhaustive `never` check makes the compiler flag unhandled cases. I used it for ForgeCI job results and for
UI request state so impossible combinations can't be represented.
</details>

<details><summary>Does TypeScript make my app type-safe at runtime?</summary>

No — types are erased at compile time. Data from the network is unverified. I validate it at the boundary
with a schema library like zod, or at minimum centralise the cast in one API client.
</details>

<details><summary>Explain generics with an example.</summary>

Type parameters let one function/type work for many types while preserving the relationship between input
and output. `Page<T>` models my paged responses; `getJson<T>` returns `Promise<T>`; `K extends keyof T`
constrains a key to exist on T.
</details>

<details><summary>Structural vs nominal typing?</summary>

Java is nominal: types are compatible only through declared relationships. TS is structural: if a value has
the required properties, it's compatible. Upside: easy with JSON. Downside: two different IDs (`SkuId`, `OrderId`)
both typed `number` are interchangeable unless you use branded types.
</details>

---

## Mastery checklist

- [ ] Configure `tsconfig` with `strict` + `noUncheckedIndexedAccess` and explain each flag.
- [ ] Model FlowGrid's `Order`, `OrderStatus`, `Role`, `InventoryLevel` with unions derived from `as const`.
- [ ] Write an exhaustive `switch` with a `never` check.
- [ ] Write `Page<T>` and a generic `getJson<T>`.
- [ ] Derive request types with `Omit`/`Partial`/`Pick`.
- [ ] Validate one endpoint's response with zod and see the error when the backend changes a field.
- [ ] Explain `unknown` vs `any` and structural typing out loud in < 60 s each.
