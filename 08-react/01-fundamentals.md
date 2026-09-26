# 01 — React Fundamentals

> Week 15 · ≈ 4 hours. Build the **static TeamBoard board** while reading this file.

---

## 1. Components and JSX

A component is a function that returns JSX. Names start with a capital letter.

```tsx
// src/features/issues/IssueCard.tsx
type IssueCardProps = {
  title: string;
  status: IssueStatus;
  assignee?: string | null;
};

export function IssueCard({ title, status, assignee }: IssueCardProps) {
  return (
    <article className="card">
      <h3>{title}</h3>
      <span className={`badge ${status.toLowerCase()}`}>{status}</span>
      <p>{assignee ?? "Unassigned"}</p>
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
  status: IssueStatus;
  issues: Issue[];
  onMove: (issueId: number, to: IssueStatus) => void;   // callback prop: child → parent communication
  children?: React.ReactNode;                           // nested content
};
```

Data flows **down** via props; events flow **up** via callback props.

---

## 3. State with `useState`

```tsx
import { useState } from "react";

export function NewIssueToggle() {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen((o) => !o)}>
        {open ? "Cancel" : "New issue"}
      </button>
      {open && <p>Form goes here</p>}
    </>
  );
}
```

Key facts:
- Calling the setter **schedules** a re-render; the `open` variable in the current render doesn't change.
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
const [issues, setIssues] = useState<Issue[]>(initial);

// ❌ Mutation — same array reference; UI may not update
issues.push(newIssue); setIssues(issues);

// ✅ New arrays/objects
setIssues((prev) => [...prev, newIssue]);                                      // add
setIssues((prev) => prev.filter((i) => i.id !== id));                          // remove
setIssues((prev) => prev.map((i) => (i.id === id ? { ...i, status: to } : i))); // update one
setIssues((prev) => prev.toSorted((a, b) => a.title.localeCompare(b.title)));  // sort
```

Nested updates copy **every level** you change:

```tsx
setIssue((prev) => ({ ...prev, assignee: prev.assignee && { ...prev.assignee, displayName: "Sam" } }));
```

> **Break it:** implement "move issue" with `issue.status = to; setIssues(issues)`. Click. Nothing moves
> (or it moves only on the next unrelated re-render). Explain why.

---

## 5. Rendering lists and keys

```tsx
<ul>
  {issues.map((issue) => (
    <li key={issue.id}>
      <IssueCard title={issue.title} status={issue.status} assignee={issue.assignee?.displayName} />
    </li>
  ))}
</ul>
```

Keys tell React **which item is which** across renders so it can preserve component state and DOM nodes.

| Key choice | Verdict |
|---|---|
| Database id (`issue.id`) | ✅ stable and unique |
| Array index | ❌ when list can reorder/insert/delete — state attaches to the wrong item |
| `Math.random()` / `crypto.randomUUID()` in render | ❌ new key every render → remount, lost state, slow |

> **Break it:** give each `IssueCard` a local "expanded" state, use `key={index}`, expand the first card, then
> prepend a new issue. The *new* first card is now expanded. Switch to `key={issue.id}` — fixed.

---

## 6. Conditional rendering and empty states

```tsx
if (issues.length === 0) return <p className="empty">No issues yet. Create the first one.</p>;

return (
  <>
    {isAdmin && <button type="button">Delete</button>}
    {error ? <p role="alert">{error}</p> : null}
    {count > 0 && <span>{count}</span>}   {/* NOT {count && ...} — renders "0" when count is 0 */}
  </>
);
```

---

## 7. Events

```tsx
function SearchBox({ onSearch }: { onSearch: (q: string) => void }) {
  const [q, setQ] = useState("");
  function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    onSearch(q.trim());
  }
  return (
    <form onSubmit={handleSubmit} role="search">
      <label htmlFor="q">Search issues</label>
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

```tsx
const STATUSES = ["TODO", "IN_PROGRESS", "IN_REVIEW", "DONE"] as const;
type IssueStatus = (typeof STATUSES)[number];

export function Board({ initial }: { initial: Issue[] }) {
  const [issues, setIssues] = useState(initial);

  function move(id: number, to: IssueStatus) {
    setIssues((prev) => prev.map((i) => (i.id === id ? { ...i, status: to } : i)));
  }

  return (
    <div className="board">
      {STATUSES.map((s) => (
        <Lane key={s} status={s} issues={issues.filter((i) => i.status === s)} onMove={move} />
      ))}
    </div>
  );
}

function Lane({ status, issues, onMove }: { status: IssueStatus; issues: Issue[]; onMove: (id: number, to: IssueStatus) => void }) {
  const next = nextStatus(status);
  return (
    <section className="lane" aria-labelledby={`lane-${status}`}>
      <h2 id={`lane-${status}`}>{status} ({issues.length})</h2>
      {issues.map((i) => (
        <div key={i.id} className="card">
          {i.title}
          {next && <button type="button" onClick={() => onMove(i.id, next)}>→ {next}</button>}
        </div>
      ))}
    </section>
  );
}

function nextStatus(s: IssueStatus): IssueStatus | null {
  const i = STATUSES.indexOf(s);
  return STATUSES[i + 1] ?? null;
}
```

Note `issues.filter(...)` is **derived** during render — don't store filtered lists in separate state (they drift).

**Rule of thumb for state:** minimal, non-redundant, single source of truth. If it can be computed from props/state, compute it.

---

## 9. What triggers a render, and reconciliation

A component re-renders when: its state changes, its parent re-renders (by default, even with equal props),
or a context it reads changes. Rendering ≠ DOM update: React diffs the new tree against the previous one
(reconciliation) and commits only differences. Two heuristics make diffing O(n): different element **types**
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
// <Panel title="Monitors" actions={<button type="button">Add</button>}><MonitorGrid /></Panel>
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
- [ ] Build the four-lane TeamBoard board from hard-coded data with typed props.
- [ ] Add, remove, update and move issues immutably.
- [ ] Demonstrate the index-key bug and fix it.
- [ ] Lift state from lanes to board; keep filtered lists derived.
- [ ] Explain reconciliation and batching in ≤ 60 s each.
