# 03 — Forms and Routing

> Week 16 · ≈ 4 hours. Build TeamBoard's **create/edit issue form** and its **route tree**.

---

## Part 1 — Forms

### 1.1 Controlled vs uncontrolled

| | Controlled | Uncontrolled |
|---|---|---|
| Source of truth | React state (`value` + `onChange`) | the DOM (`defaultValue`, read via `FormData` or ref) |
| Instant validation / dependent fields | easy | harder |
| Re-render per keystroke | yes (fine for normal forms) | no |

Use controlled inputs by default for app forms. Uncontrolled + `FormData` is fine for simple submit-only forms.
Libraries (React Hook Form + zod resolver) exist; learn the manual version first so you can explain what they do.

### 1.2 A complete, typed issue form

```tsx
// src/features/issues/IssueForm.tsx
import { useState } from "react";

export type IssueFormValues = { title: string; description: string; status: IssueStatus; assigneeId: string };
type Errors = Partial<Record<keyof IssueFormValues, string>>;

function validate(v: IssueFormValues): Errors {
  const e: Errors = {};
  if (!v.title.trim()) e.title = "Title is required";
  else if (v.title.length > 200) e.title = "Title must be at most 200 characters";
  if (v.description.length > 5000) e.description = "Description is too long";
  return e;
}

type Props = {
  initial?: Partial<IssueFormValues>;
  onSubmit: (values: IssueFormValues) => Promise<void>;
  members: { id: number; displayName: string }[];
};

export function IssueForm({ initial, onSubmit, members }: Props) {
  const [values, setValues] = useState<IssueFormValues>({
    title: "", description: "", status: "TODO", assigneeId: "", ...initial,
  });
  const [errors, setErrors] = useState<Errors>({});
  const [touched, setTouched] = useState<Partial<Record<keyof IssueFormValues, boolean>>>({});
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  function handleChange(e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) {
    const { name, value } = e.target;
    const next = { ...values, [name]: value } as IssueFormValues;
    setValues(next);
    if (touched[name as keyof IssueFormValues]) setErrors(validate(next)); // re-validate once touched
  }

  function handleBlur(e: React.FocusEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) {
    setTouched((t) => ({ ...t, [e.target.name]: true }));
    setErrors(validate(values));
  }

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const found = validate(values);
    setErrors(found);
    setTouched({ title: true, description: true, status: true, assigneeId: true });
    if (Object.keys(found).length > 0) return;

    setSubmitting(true);
    setFormError(null);
    try {
      await onSubmit(values);
    } catch (err) {
      // Map server-side Bean Validation errors (ProblemDetail with field errors) back onto fields
      if (err instanceof ApiError && err.status === 400 && err.fieldErrors) {
        setErrors(err.fieldErrors as Errors);
      } else {
        setFormError(err instanceof Error ? err.message : "Something went wrong");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      {formError && <p role="alert" className="error">{formError}</p>}

      <div className="field">
        <label htmlFor="title">Title</label>
        <input id="title" name="title" value={values.title} onChange={handleChange} onBlur={handleBlur}
               aria-invalid={!!errors.title} aria-describedby={errors.title ? "title-error" : undefined} />
        {touched.title && errors.title && <p id="title-error" className="error">{errors.title}</p>}
      </div>

      <div className="field">
        <label htmlFor="description">Description</label>
        <textarea id="description" name="description" rows={5} value={values.description}
                  onChange={handleChange} onBlur={handleBlur} />
        {touched.description && errors.description && <p className="error">{errors.description}</p>}
      </div>

      <div className="field">
        <label htmlFor="status">Status</label>
        <select id="status" name="status" value={values.status} onChange={handleChange}>
          <option value="TODO">To do</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="IN_REVIEW">In review</option>
          <option value="DONE">Done</option>
        </select>
      </div>

      <div className="field">
        <label htmlFor="assigneeId">Assignee</label>
        <select id="assigneeId" name="assigneeId" value={values.assigneeId} onChange={handleChange}>
          <option value="">Unassigned</option>
          {members.map((m) => <option key={m.id} value={String(m.id)}>{m.displayName}</option>)}
        </select>
      </div>

      <button type="submit" disabled={submitting}>{submitting ? "Saving…" : "Save"}</button>
    </form>
  );
}
```

Notes:
- `<select>` values are **strings**; convert `assigneeId` to `number | null` when building the API request, not in the form.
- Disabling submit while `submitting` prevents double-POSTs (the backend should still be idempotent where it matters — see TicketHold's Idempotency-Key).
- Client validation mirrors server rules for UX. **The server is the authority** (Bean Validation in [05-spring-boot/04-validation-errors.md](../05-spring-boot/04-validation-errors.md)).
- Accessibility: `aria-invalid` + `aria-describedby` + visible `<label>` → also makes tests easy.

### 1.3 Break it

1. Replace `value={values.title}` with `value={values.title || undefined}`, then clear the field. React warns about switching controlled → uncontrolled.
2. Remove `e.preventDefault()` — full page reload, state lost.
3. Double-click Save quickly with `disabled` removed and watch two POSTs in the Network tab.
4. Return a 400 ProblemDetail with `errors: { title: "must not be blank" }` from Spring and confirm it lands under the field.

---

## Part 2 — Routing with React Router v6

### 2.1 Route tree for TeamBoard

```
/login                                  LoginPage (public)
/                                       → redirect to /orgs
/orgs                                   OrgListPage                   (protected)
/orgs/:orgId/projects                   ProjectListPage               (protected)
/orgs/:orgId/projects/:projectId        ProjectLayout (tabs + <Outlet/>)
    index                               BoardPage
    issues                              IssueListPage (?q=&status=&page=)
    issues/new                          NewIssuePage                  (MEMBER+)
    issues/:issueId                     IssueDetailPage
    settings                            ProjectSettingsPage           (ADMIN+)
*                                       NotFoundPage
```

```tsx
// src/app/router.tsx
import { createBrowserRouter, Navigate } from "react-router-dom";

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    element: <RequireAuth />,                 // layout route guarding everything below (see 04)
    children: [
      { path: "/", element: <Navigate to="/orgs" replace /> },
      { path: "/orgs", element: <OrgListPage /> },
      { path: "/orgs/:orgId/projects", element: <ProjectListPage /> },
      {
        path: "/orgs/:orgId/projects/:projectId",
        element: <ProjectLayout />,
        children: [
          { index: true, element: <BoardPage /> },
          { path: "issues", element: <IssueListPage /> },
          { path: "issues/new", element: <RequireRole min="MEMBER"><NewIssuePage /></RequireRole> },
          { path: "issues/:issueId", element: <IssueDetailPage /> },
          { path: "settings", element: <RequireRole min="ADMIN"><ProjectSettingsPage /></RequireRole> },
        ],
      },
    ],
  },
  { path: "*", element: <NotFoundPage /> },
]);

// src/main.tsx
// <StrictMode><AuthProvider><RouterProvider router={router} /></AuthProvider></StrictMode>
```

### 2.2 Layouts and `<Outlet />`

```tsx
import { NavLink, Outlet, useParams } from "react-router-dom";

export function ProjectLayout() {
  const { orgId, projectId } = useParams();   // strings | undefined
  return (
    <div>
      <nav className="row">
        <NavLink to="." end>Board</NavLink>
        <NavLink to="issues">Issues</NavLink>
        <NavLink to="settings">Settings</NavLink>
      </nav>
      <Outlet />                               {/* child route renders here */}
    </div>
  );
}
```

### 2.3 Params, search params, navigation

```tsx
import { useNavigate, useParams, useSearchParams, Link } from "react-router-dom";

export function IssueListPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const pid = Number(projectId);
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const page = Number(params.get("page") ?? "0");

  // URL is the state → shareable, back-button works, survives refresh
  function setPage(p: number) {
    setParams((prev) => { prev.set("page", String(p)); return prev; });
  }
  // ...fetch with (pid, q, page) as effect deps / query key
  return <Link to="new">New issue</Link>;   // relative to this route's path "issues" → issues/new
}

export function NewIssuePage() {
  const navigate = useNavigate();
  const { orgId, projectId } = useParams();
  async function create(values: IssueFormValues) {
    const created = await issuesApi.create(Number(projectId), toCreateRequest(values));
    navigate(`/orgs/${orgId}/projects/${projectId}/issues/${created.id}`, { replace: true });
  }
  // replace: true → Back doesn't return to a submitted form
  return <IssueForm onSubmit={create} members={[]} />;
}
```

**Put filter/search/page state in the URL** (search params), not in component state: it's bookmarkable, shareable,
and survives refresh. `useParams` values are always strings (or `undefined`) — validate/convert them.

### 2.4 Deploying an SPA: the refresh-404 problem

Client-side routes don't exist on the server. Refreshing `/orgs/1/projects/2` asks the server for that path.
In dev Vite handles it; in production nginx must fall back to `index.html`:

```nginx
location / {
  try_files $uri $uri/ /index.html;
}
```

This is part of P3 M5's Compose stack ([11-docker/compose.md](../11-docker/compose.md)).

> **Break it:** remove `try_files` in your nginx config, deploy, open a deep link → 404.

---

## Common mistakes

| Mistake | Fix |
|---|---|
| `<a href>` for internal links (full reload) | `<Link to>` / `<NavLink>` |
| Filters in `useState` → lost on refresh | `useSearchParams` |
| Using `useParams()` values as numbers | `Number(...)` + validate (`Number.isNaN`) |
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

My Spring API returns RFC 7807 ProblemDetail with a field → message map on 400. The API client turns that into
a typed `ApiError` with `fieldErrors`, and the form sets those as field errors next to the inputs; other failures
show a form-level alert.
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

- [ ] Build `IssueForm` with typed values, per-field errors, touched state and disabled submit.
- [ ] Map a Spring 400 ProblemDetail onto form fields.
- [ ] Build TeamBoard's route tree with a layout route, index route, params and catch-all.
- [ ] Drive list filters and pagination from `useSearchParams`.
- [ ] Explain and fix the SPA refresh-404 in nginx.
