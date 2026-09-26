# 03 — Forms and Routing

> Week 7 · ≈ 1.5 hours. FlowGrid M4 needs forms (stock adjustments, returns, transfers) and a route tree
> (warehouses → inventory, orders, pick/pack). Week 23 revisits forms at scale for FlagForge's rules editor.

---

## Part 1 — Forms

### 1.1 Controlled vs uncontrolled

| | Controlled | Uncontrolled |
|---|---|---|
| Source of truth | React state (`value` + `onChange`) | the DOM (`defaultValue`, read via `FormData` or ref) |
| Instant validation / dependent fields | easy | harder |
| Re-render per keystroke | yes (fine for normal forms) | no |

Use controlled inputs by default for app forms. Uncontrolled + `FormData` is fine for simple submit-only forms.
Libraries (React Hook Form + zod resolver) exist; learn the manual version first so you can explain what they do —
then decide per project (FlagForge's rules editor in Week 23 is where a form library starts paying for itself).

### 1.2 The pattern, on a stock-adjustment form

The concept to learn: **typed values + per-field errors + touched state + submitting state + mapping server errors**.
This is a teaching skeleton; FlowGrid's real adjustment form has the fields your API defines.

```tsx
type AdjustmentValues = { skuId: string; delta: string; reason: "" | "DAMAGED" | "CYCLE_COUNT" | "FOUND" };
type Errors = Partial<Record<keyof AdjustmentValues, string>>;

function validate(v: AdjustmentValues): Errors {
  const e: Errors = {};
  if (!v.skuId) e.skuId = "Choose a SKU";
  const n = Number(v.delta);
  if (!Number.isInteger(n) || n === 0) e.delta = "Enter a non-zero whole number";
  if (!v.reason) e.reason = "Choose a reason";
  return e;
}

export function AdjustmentForm({ onSubmit }: { onSubmit: (v: AdjustmentValues) => Promise<void> }) {
  const [values, setValues] = useState<AdjustmentValues>({ skuId: "", delta: "", reason: "" });
  const [errors, setErrors] = useState<Errors>({});
  const [touched, setTouched] = useState<Partial<Record<keyof AdjustmentValues, boolean>>>({});
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const set = (name: keyof AdjustmentValues) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setValues((v) => ({ ...v, [name]: e.target.value }));
  const blur = (name: keyof AdjustmentValues) => () => {
    setTouched((t) => ({ ...t, [name]: true }));
    setErrors(validate(values));
  };

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const found = validate(values);
    setErrors(found);
    setTouched({ skuId: true, delta: true, reason: true });
    if (Object.keys(found).length > 0) return;
    setSubmitting(true);
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (err) {
      if (err instanceof ApiError && err.status === 400 && err.fieldErrors) setErrors(err.fieldErrors); // server rules win
      else if (err instanceof ApiError && err.status === 409) setFormError(err.message);  // e.g. "would make on-hand negative"
      else setFormError("Something went wrong");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      {formError && <p role="alert" className="error">{formError}</p>}
      <label htmlFor="delta">Quantity change</label>
      <input id="delta" inputMode="numeric" value={values.delta} onChange={set("delta")} onBlur={blur("delta")}
             aria-invalid={!!errors.delta} aria-describedby={errors.delta ? "delta-error" : undefined} />
      {touched.delta && errors.delta && <p id="delta-error" className="error">{errors.delta}</p>}
      {/* skuId <select> and reason <select> follow the same pattern */}
      <button type="submit" disabled={submitting}>{submitting ? "Saving…" : "Save adjustment"}</button>
    </form>
  );
}
```

Notes:
- Inputs hold **strings**; convert (`Number(values.delta)`) when building the API request, not inside the form.
- Disabling submit while `submitting` prevents accidental double-POSTs — but the backend must still be safe: FlowGrid's
  order creation uses an `Idempotency-Key` (generated once per submit intent, reused on retry).
- Client validation mirrors server rules for UX. **The server is the authority** (Bean Validation, domain rules →
  400/409 ProblemDetail; see [05-spring-boot/04-validation-errors.md](../05-spring-boot/04-validation-errors.md)).
- Accessibility: visible `<label>`, `aria-invalid`, `aria-describedby` — also what makes tests easy ([09-testing/frontend-testing.md](../09-testing/frontend-testing.md)).

### 1.3 Break it

1. Replace `value={values.delta}` with `value={values.delta || undefined}`, then clear the field. React warns about switching controlled → uncontrolled.
2. Remove `e.preventDefault()` — full page reload, state lost.
3. Remove `disabled`, double-click Save, and watch two POSTs in the Network tab. Which FlowGrid endpoints would that corrupt without idempotency?
4. Make the API return `409` for an adjustment that would drive on-hand below zero; confirm the message appears as a form-level alert.

---

## Part 2 — Routing with React Router v6

### 2.1 Designing the route tree

Design URLs before components. A FlowGrid-shaped example (yours will differ):

```
/login                                   LoginPage (public)
/                                        → redirect to /warehouses
/warehouses                              WarehouseListPage                    (any signed-in role)
/warehouses/:warehouseId                 WarehouseLayout (tabs + <Outlet/>)
    index                                InventoryPage (?q=&lowStock=&sort=&page=)
    orders                               OrderListPage (?status=&page=)
    orders/:orderId                      OrderDetailPage
    pick                                 PickPackBoardPage                    (WAREHOUSE_ASSOCIATE+)
    adjustments/new                      NewAdjustmentPage                    (OPS_MANAGER+)
*                                        NotFoundPage
```

```tsx
// src/app/router.tsx
import { createBrowserRouter, Navigate } from "react-router-dom";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    element: <RequireAuth />,                  // layout route guarding everything below (see 04)
    children: [
      { path: "/", element: <Navigate to="/warehouses" replace /> },
      { path: "/warehouses", element: <WarehouseListPage /> },
      {
        path: "/warehouses/:warehouseId",
        element: <WarehouseLayout />,
        errorElement: <RouteError />,
        children: [
          { index: true, element: <InventoryPage /> },
          { path: "orders", element: <OrderListPage /> },
          { path: "orders/:orderId", element: <OrderDetailPage /> },
          { path: "pick", element: <RequirePermission perm="pick:perform"><PickPackBoardPage /></RequirePermission> },
          { path: "adjustments/new", element: <RequirePermission perm="inventory:adjust"><NewAdjustmentPage /></RequirePermission> },
        ],
      },
    ],
  },
  { path: "*", element: <NotFoundPage /> },
]);

// src/main.tsx:  <StrictMode><AuthProvider><RouterProvider router={router} /></AuthProvider></StrictMode>
```

### 2.2 Layouts and `<Outlet />`

```tsx
import { NavLink, Outlet } from "react-router-dom";

export function WarehouseLayout() {
  return (
    <div>
      <nav className="row">
        <NavLink to="." end>Inventory</NavLink>
        <NavLink to="orders">Orders</NavLink>
        <NavLink to="pick">Pick / pack</NavLink>
      </nav>
      <Outlet />                                {/* the matched child route renders here */}
    </div>
  );
}
```

### 2.3 Params, search params, navigation

```tsx
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

export function InventoryPage() {
  const { warehouseId } = useParams<{ warehouseId: string }>();  // string | undefined — always a string
  const id = Number(warehouseId);
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const lowStock = params.get("lowStock") === "true";
  const page = Number(params.get("page") ?? "0");

  // The URL is the state → shareable, Back works, survives refresh
  function setPage(p: number) {
    setParams((prev) => { prev.set("page", String(p)); return prev; });
  }
  // ...fetch with (id, q, lowStock, page) as effect deps / query key
}

export function NewAdjustmentPage() {
  const navigate = useNavigate();
  const { warehouseId } = useParams();
  async function save(v: AdjustmentValues) {
    await inventoryApi.adjust(Number(warehouseId), toAdjustmentRequest(v));
    navigate(`/warehouses/${warehouseId}`, { replace: true });   // Back won't return to the submitted form
  }
  return <AdjustmentForm onSubmit={save} />;
}
```

**Put filter/search/sort/page state in the URL** (search params), not in component state: bookmarkable, shareable,
survives refresh. `useParams` values are strings (or `undefined`) — convert and validate (`Number.isNaN`).

### 2.4 Deploying an SPA: the refresh-404 problem

Client-side routes don't exist on the server. Refreshing `/warehouses/2/orders` asks the server for that path.
In dev Vite handles it; in production nginx must fall back to `index.html`:

```nginx
location / {
  try_files $uri $uri/ /index.html;
}
```

This is part of FlowGrid M5's deployed stack ([11-docker/dockerfiles.md §7](../11-docker/dockerfiles.md#7-frontend-image-nginx--static-build)).

> **Break it:** remove `try_files`, deploy, open a deep link → 404.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `<a href>` for internal links (full reload) | `<Link to>` / `<NavLink>` |
| Filters in `useState` → lost on refresh | `useSearchParams` |
| Using `useParams()` values as numbers | `Number(...)` + validate |
| Forgetting `<Outlet />` in a layout | child routes render nothing |
| No catch-all route | blank page on unknown URLs |
| Only client-side validation | server validation is mandatory |

---

## Interview Q&A

<details><summary>Controlled vs uncontrolled components?</summary>

Controlled: React state holds the value and `onChange` updates it, enabling instant validation and dependent fields.
Uncontrolled: the DOM holds the value and you read it on submit via `FormData` or a ref. I use controlled for forms
with validation, uncontrolled for trivial forms or file inputs.
</details>

<details><summary>How do you handle server-side validation errors in a form?</summary>

My Spring API returns ProblemDetail: 400 with a field → message map for validation, 409 for domain conflicts such as an
adjustment that would make stock negative. The API client turns that into a typed `ApiError`; the form shows field errors
next to inputs and conflicts as a form-level alert.
</details>

<details><summary>How does client-side routing work?</summary>

The router intercepts link clicks, updates the URL with the History API (`pushState`) without a page load, and
renders the matching component tree. On refresh the server must serve `index.html` for any app route, which I
configure with `try_files` in nginx.
</details>

<details><summary>Where do you keep search/filter state?</summary>

In the URL's query string via `useSearchParams`, so views are shareable and survive refresh and Back.
</details>

---

## Mastery checklist

- [ ] Build one FlowGrid form with typed values, per-field errors, touched state and disabled submit.
- [ ] Map a Spring 400 ProblemDetail onto fields and a 409 onto a form-level alert.
- [ ] Design FlowGrid's route tree on paper first; implement it with a layout route, index route, params and catch-all.
- [ ] Drive inventory filters, sort and pagination from `useSearchParams`.
- [ ] Explain and fix the SPA refresh-404 in nginx.
