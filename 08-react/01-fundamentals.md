# 01 — React Fundamentals

> Week 7 · ≈ 1.5 hours. While reading, build a **static pick/pack board** for FlowGrid from hard-coded orders; wire it to
> the API in [04](./04-api-integration-auth.md).

---

## 1. Components and JSX

A component is a function that returns JSX. Names start with a capital letter.

```tsx
// src/features/orders/OrderCard.tsx
type OrderCardProps = {
  number: string;
  status: OrderStatus;
  units: number;
  assignee?: string | null;
};

export function OrderCard({ number, status, units, assignee }: OrderCardProps) {
  return (
    <article className="card">
      <h3>{number}</h3>
      <span className={`badge ${status.toLowerCase()}`}>{status}</span>
      <p>{units} units · {assignee ?? "Unassigned"}</p>
    </article>
  );
}
```

JSX rules:
- It compiles to function calls (`jsx("article", {...})`) — so it's an **expression**. Use `{}` to embed JS expressions (not statements).
- `className` not `class`, `htmlFor` not `for`, camelCase events (`onClick`).
- One root element per return; use a fragment `<>...</>` to avoid wrapper divs.
- Text in `{}` is **escaped** automatically → XSS-safe by default.

---

## 2. Props

Props are the component's **read-only inputs**. Never mutate them.

```tsx
type LaneProps = {
  status: OrderStatus;
  orders: OrderSummary[];
  onAdvance: (orderId: number, to: OrderStatus) => void;   // callback prop: child → parent communication
  children?: React.ReactNode;                              // nested content
};
```

Data flows **down** via props; events flow **up** via callback props.

---

## 3. State with `useState`

```tsx
import { useState } from "react";

export function LowStockToggle() {
  const [onlyLow, setOnlyLow] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOnlyLow((v) => !v)}>
        {onlyLow ? "Show all SKUs" : "Show low stock only"}
      </button>
      {onlyLow && <p>Filtering to SKUs below reorder point</p>}
    </>
  );
}
```

Key facts:
- Calling the setter **schedules** a re-render; the variable in the current render doesn't change.
- React 18 **batches** multiple state updates in the same event (and in promises/timeouts) into one render.
- Use the **updater form** `setX(prev => ...)` when the new value depends on the old one.

```tsx
// BUG: clicking once adds 1, not 3 — each call sees the same snapshot `count`
setCount(count + 1); setCount(count + 1); setCount(count + 1);
// FIX
setCount((c) => c + 1); setCount((c) => c + 1); setCount((c) => c + 1);
```

---

## 4. Immutability

React decides whether to re-render by comparing state with `Object.is`. If you mutate the object and
set the same reference, **React sees no change**.

```tsx
const [orders, setOrders] = useState<OrderSummary[]>(initial);

// ❌ Mutation — same array reference; UI may not update
orders.push(newOrder); setOrders(orders);

// ✅ New arrays/objects
setOrders((prev) => [...prev, newOrder]);                                        // add
setOrders((prev) => prev.filter((o) => o.id !== id));                            // remove
setOrders((prev) => prev.map((o) => (o.id === id ? { ...o, status: to } : o)));  // update one
setOrders((prev) => prev.toSorted((a, b) => a.number.localeCompare(b.number)));  // sort
```

Nested updates copy **every level** you change:

```tsx
setOrder((prev) => ({
  ...prev,
  lines: prev.lines.map((l) => (l.skuId === skuId ? { ...l, picked: l.picked + 1 } : l)),
}));
```

> **Break it:** implement "advance order" with `order.status = to; setOrders(orders)`. Click. Nothing moves
> (or it moves only on the next unrelated re-render). Explain why.

---

## 5. Rendering lists and keys

```tsx
<ul>
  {orders.map((o) => (
    <li key={o.id}>
      <OrderCard number={o.number} status={o.status} units={o.units} assignee={o.assignee?.name} />
    </li>
  ))}
</ul>
```

Keys tell React **which item is which** across renders so it can preserve component state and DOM nodes.

| Key choice | Verdict |
|---|---|
| Database id (`o.id`) | ✅ stable and unique |
| Array index | ❌ when list can reorder/insert/delete — state attaches to the wrong item |
| `Math.random()` / `crypto.randomUUID()` in render | ❌ new key every render → remount, lost state, slow |

> **Break it:** give each `OrderCard` a local "expanded" state, use `key={index}`, expand the first card, then
> prepend a new order. The *new* first card is now expanded. Switch to `key={o.id}` — fixed.
> (ForgeCI's log view, Week 16: key each log line by its **sequence number**, never by index.)

---

## 6. Conditional rendering and empty states

```tsx
if (orders.length === 0) return <p className="empty">No orders waiting to be picked.</p>;

return (
  <>
    {canCancel && <button type="button">Cancel order</button>}
    {error ? <p role="alert">{error}</p> : null}
    {lowStockCount > 0 && <span className="badge low">{lowStockCount}</span>}
    {/* NOT {lowStockCount && ...} — renders "0" when the count is 0 */}
  </>
);
```

---

## 7. Events

```tsx
function SkuSearch({ onSearch }: { onSearch: (q: string) => void }) {
  const [q, setQ] = useState("");
  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    onSearch(q.trim());
  }
  return (
    <form onSubmit={handleSubmit} role="search">
      <label htmlFor="q">Search SKUs</label>
      <input id="q" value={q} onChange={(e) => setQ(e.target.value)} />
      <button type="submit">Search</button>
    </form>
  );
}
```

Pass a function, don't call it: `onClick={handleClick}` ✅, `onClick={handleClick()}` ❌ (runs during render → often an infinite loop if it sets state).

---

## 8. Lifting state up

When two siblings need the same data, move the state to their **closest common parent** and pass it down.
Here the lanes of the pick/pack board share one `orders` array owned by `PickPackBoard`:

```tsx
const LANES = ["ALLOCATED", "PICKING", "PACKED", "SHIPPED"] as const;
type Lane = (typeof LANES)[number];

export function PickPackBoard({ initial }: { initial: OrderSummary[] }) {
  const [orders, setOrders] = useState(initial);

  function advance(id: number, to: Lane) {
    setOrders((prev) => prev.map((o) => (o.id === id ? { ...o, status: to } : o)));
  }

  return (
    <div className="board">
      {LANES.map((lane) => (
        <LaneColumn key={lane} status={lane} orders={orders.filter((o) => o.status === lane)} onAdvance={advance} />
      ))}
    </div>
  );
}

function LaneColumn({ status, orders, onAdvance }: { status: Lane; orders: OrderSummary[]; onAdvance: (id: number, to: Lane) => void }) {
  const next = LANES[LANES.indexOf(status) + 1];      // Lane | undefined (noUncheckedIndexedAccess)
  return (
    <section className="lane" aria-labelledby={`lane-${status}`}>
      <h2 id={`lane-${status}`}>{status} ({orders.length})</h2>
      {orders.map((o) => (
        <div key={o.id} className="card">
          {o.number}
          {next && <button type="button" onClick={() => onAdvance(o.id, next)}>→ {next}</button>}
        </div>
      ))}
    </section>
  );
}
```

Note `orders.filter(...)` is **derived** during render — don't store filtered lists in separate state (they drift).
In the real dashboard, "advance" calls the API (the server's state machine decides whether `PICKING → PACKED` is legal)
and then refreshes; this local version is only for learning state flow.

**Rule of thumb for state:** minimal, non-redundant, single source of truth. If it can be computed from props/state, compute it.

---

## 9. What triggers a render, and reconciliation

A component re-renders when: its state changes, its parent re-renders (by default, even with equal props),
or a context it reads changes. Rendering ≠ DOM update: React diffs the new tree against the previous one
(reconciliation) and commits only differences. Two heuristics make diffing fast: different element **types**
tear down the subtree; **keys** match children in lists.

---

## 10. Composition over inheritance

```tsx
function Panel({ title, actions, children }: { title: string; actions?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className="panel">
      <header className="row"><h2>{title}</h2><div className="spacer">{actions}</div></header>
      {children}
    </section>
  );
}
// <Panel title="Low stock" actions={<button type="button">Export CSV</button>}><LowStockTable /></Panel>
```

React never uses component inheritance. You compose with props and `children`.

---

## Common mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| Mutating state | UI doesn't update | new objects/arrays |
| `key={index}` on dynamic lists | state jumps between rows | stable id |
| `{count && <X/>}` | stray `0` rendered | `count > 0 &&` |
| `onClick={fn()}` | runs on render; "Too many re-renders" | `onClick={fn}` or `() => fn(x)` |
| Duplicated derived state | filtered list out of sync | compute during render |
| `setX(x + 1)` multiple times | only +1 | updater form |
| Business rules only in the UI (e.g. allowed transitions) | API accepts illegal transitions | server enforces; UI mirrors for UX |

---

## Interview Q&A

<details><summary>What is the virtual DOM / how does React update the page?</summary>

On state change React re-runs the component to produce a new element tree, compares it to the previous tree
(reconciliation), and commits the minimal set of real DOM changes. Element type changes remount a subtree;
keys identify list items across renders.
</details>

<details><summary>Why keys, and why not the index?</summary>

Keys let React match list items between renders to preserve their state and DOM. The index changes when you
insert, delete or reorder, so state gets attached to the wrong item. Use a stable unique id like the database id.
</details>

<details><summary>Why must state be treated as immutable?</summary>

React bails out of re-rendering when the new state is `Object.is`-equal to the old one. Mutating keeps the same
reference so updates can be skipped, and it also breaks things that rely on comparing previous vs next values
(memoization, effect dependencies).
</details>

<details><summary>Props vs state?</summary>

Props are inputs passed by the parent and read-only for the child. State is private data owned by the component
that changes over time and triggers re-renders. A value shared by siblings is lifted to their common parent and passed down as props.
</details>

<details><summary>Is <code>setState</code> synchronous?</summary>

No — it schedules an update. The current render's variable doesn't change; the next render sees the new value.
React 18 batches updates automatically, including inside promises and timeouts.
</details>

---

## Mastery checklist

- [ ] Scaffold a Vite `react-ts` app and explain every file in `src/`.
- [ ] Build a static pick/pack board with typed props from hard-coded data.
- [ ] Add, remove, update and move orders immutably.
- [ ] Demonstrate the index-key bug and fix it.
- [ ] Lift state from lanes to board; keep filtered lists derived.
- [ ] Explain reconciliation and batching in ≤ 60 s each.
