# 18 — Projects: How to Run a Project

> This folder holds the **specs**. Each project is built in **its own GitHub repository** that you create
> (see [`../PROJECTS.md`](../PROJECTS.md#7-where-each-project-lives)). Overview, résumé matrix and timeline:
> [`../PROJECTS.md`](../PROJECTS.md). Progress: [`../trackers/project-tracker.md`](../trackers/project-tracker.md).

| Spec | Weeks | One-line goal |
|---|---|---|
| [P1 Ledger](./p1-ledger/README.md) | 3–8 | Java CLI finance engine: messy CSV → rules → reports → PostgreSQL via JDBC |
| [P2 TicketHold](./p2-tickethold/README.md) | 9–13 | Spring Boot seat-reservation API that cannot double-book |
| [P3 TeamBoard](./p3-teamboard/README.md) | 14–18 | Full-stack issue tracker with per-organization RBAC |
| [P4 PulseWatch](./p4-pulsewatch/README.md) | 18–22 (+23–25) | Uptime monitoring: workers, Redis, rate limiting, AWS, CI/CD |

Templates: [`templates/design-doc.md`](./templates/design-doc.md) · [`templates/adr.md`](./templates/adr.md)

---

## 1. The project loop

```
Spec (this repo) ──▶ Design doc ──▶ Milestone ──▶ Issues ──▶ Branch per issue ──▶ PR ──▶ Review ──▶ Merge ──▶ Tag
                         ▲                                                                   │
                         └──────────── ADR for every non-obvious decision ◀──────────────────┘
```

Every milestone follows the roadmap's philosophy: **Learn → Build → Break → Debug → Explain → Review → Build again.**

---

## 2. Design doc first

Before writing code for a project (and before each major milestone for P3/P4), write or update
`docs/design.md` in the project repo using [`templates/design-doc.md`](./templates/design-doc.md).

Rules:

- **Time-box it:** P1 ≈ 1 h, P2 ≈ 2 h, P3 ≈ 3 h, P4 ≈ 4 h. A design doc is thinking on paper, not a thesis.
- It must contain: problem, goals/non-goals, data model, API or CLI surface, key flows (sequence in text), risks, test plan, milestones.
- Write "**Open questions**" honestly. Resolve each with an ADR as you go.
- Update it when reality diverges. An out-of-date design doc is worse than none; in interviews you will be asked "what changed from your original design and why?" — keep a "Changes since v0" section.

### ADRs

One ADR per decision that a reasonable engineer could have made differently. File: `docs/adr/NNNN-kebab-title.md`
using [`templates/adr.md`](./templates/adr.md). Minimum 3 per project. Good ADR topics:

| Project | Example ADRs |
|---|---|
| P1 | `BigDecimal` for money · Strategy for rules · JDBC instead of JPA |
| P2 | Optimistic vs pessimistic locking · JWT vs server sessions · Idempotency-key storage |
| P3 | Per-org membership roles vs global roles · Token storage in the browser · Monorepo |
| P4 | Virtual threads vs fixed pool · Token bucket in Redis Lua · EC2 + Compose vs ECS |

---

## 3. Milestone → issues → branches → PRs

### 3.1 Milestone to issues

1. Create a GitHub **Milestone** named after the spec (`M2 — Persistence`), with the due date = end of that week.
2. Split the spec's task checklist into **issues**, each ≤ 1 session (≈2–3 h). Label: `feat`, `test`, `bug`, `docs`, `infra`, `chore`.
3. Each issue body: context (1–2 lines), task checklist, **acceptance criteria copied from the spec**.

```markdown
### Context
Imports must not create duplicates when the same CSV is imported twice.

### Tasks
- [ ] Add UNIQUE (account_id, fingerprint) to schema.sql
- [ ] Compute fingerprint = sha256(date|amount|normalizedDescription)
- [ ] Map SQLState 23505 to DuplicateTransactionException

### Acceptance criteria
- [ ] Importing the same file twice leaves row count unchanged
- [ ] Test `importSameFileTwice_insertsOnce` passes against Postgres
```

### 3.2 Branch naming

`<type>/<issue-number>-<short-kebab>` — e.g. `feat/14-csv-import`, `fix/31-hold-expiry-off-by-one`, `test/40-concurrent-hold`, `docs/52-readme-architecture`.

### 3.3 Commits — Conventional Commits

```
feat(import): report per-row errors with line numbers
fix(hold): compare expiry with Clock, not LocalDateTime.now()
test(booking): 10 threads holding same seat -> exactly one succeeds
refactor(repo): extract TransactionRepository interface
docs(adr): 0002 optimistic locking for seats
build: add Testcontainers BOM
ci: run mvn verify on pull_request
```

Small commits that each compile. Never commit secrets (`.env` in `.gitignore`, provide `.env.example`).

### 3.4 Pull requests

- One PR per issue, or one per milestone for small milestones. Keep PRs **< 400 changed lines** when possible.
- PR template (put in `.github/pull_request_template.md`):

```markdown
## What
## Why (link issue: Closes #NN)
## How (key design points)
## Testing
- [ ] Unit  - [ ] Integration  - [ ] Manual (curl / UI) — paste output
## Checklist
- [ ] Tests added/updated  - [ ] README/ADR updated  - [ ] No secrets  - [ ] CI green
```

- **Self-review before merge:** read the diff on GitHub as if someone else wrote it; leave at least one comment on your own PR explaining a non-obvious line.
- Merge strategy: **squash merge** for feature PRs (clean `main` history); PR title follows Conventional Commits.
- After the last PR of a milestone: close the GitHub Milestone, update the [tracker](../trackers/project-tracker.md) with the PR link.

### 3.5 Tags and releases

- `v0.<milestone>.0` at the end of each milestone (e.g. `v0.3.0`), `v1.0.0` at completion.
- Annotated: `git tag -a v1.0.0 -m "Ledger v1.0.0"` then `git push origin v1.0.0`.
- Create a GitHub Release with notes (features, how to run, known limitations).

---

## 4. Definition of done

### Per milestone

- [ ] Every task in the spec milestone is done or explicitly moved (with an issue) to a later milestone.
- [ ] Every acceptance criterion demonstrated (test name or pasted output in the PR).
- [ ] Tests pass locally and in CI (P2+).
- [ ] New decisions captured in ADRs; design doc updated.
- [ ] README "How to run" still works from a clean clone.
- [ ] Tracker row updated with PR link.
- [ ] One "Break it" exercise from the spec done and logged in `docs/break-it.md`.

### Per project

See the full list in [`../PROJECTS.md` §5](../PROJECTS.md#5-definition-of-done-applies-to-every-project). In short: all milestones merged,
clean-clone build, CI green, must-exist tests present, README checklist complete, diagram, ≥3 ADRs,
`v1.0.0` tag, deployment target met, break-it log, recorded walkthroughs.

---

## README requirements

Every project README must contain, in this order:

- [ ] **Title + one-sentence pitch** ("TicketHold is a REST API for reserving event seats that guarantees no double-booking under concurrent requests.")
- [ ] **Badges:** CI status (P2+), license. Nothing decorative.
- [ ] **Demo:** GIF or screenshot (P3/P4), or a terminal session transcript (P1/P2).
- [ ] **Features** — bullet list, each one real and tested.
- [ ] **Architecture** — diagram (ASCII or PNG in `docs/`), 3–6 sentences explaining it.
- [ ] **Tech stack** with versions (Java 21, Spring Boot 3.x, PostgreSQL 16, …).
- [ ] **Getting started** — prerequisites, exact commands, expected output. Must work from a clean clone.
- [ ] **Configuration** — env vars table (name, default, purpose). Reference `.env.example`.
- [ ] **API / CLI reference** — endpoint table or command list; link to Postman collection / OpenAPI if present.
- [ ] **Testing** — how to run tests, what kinds exist, coverage number (as information, not a trophy).
- [ ] **Design decisions** — links to ADRs with one-line summaries.
- [ ] **What I'd do next / known limitations** — honest.
- [ ] **What I learned** — 3–5 bullets (useful for interviews, shows reflection).
- [ ] **Project context:** "Built as a learning project during a structured 26-week roadmap." (Honest framing.)

Skeleton:

```markdown
# TicketHold
REST API for reserving event seats that guarantees no double-booking under concurrent requests.

![CI](https://github.com/<you>/tickethold/actions/workflows/ci.yml/badge.svg)

## Demo
## Features
## Architecture
## Tech stack
## Getting started
## Configuration
## API
## Testing
## Design decisions
## Limitations & next steps
## What I learned
```

---

## 5. Demo expectations

| Project | Demo artifact | Length | Must show |
|---|---|---|---|
| P1 | Terminal transcript in README (or asciinema) | ≈1 min | Import with a bad row → error report; categorize; monthly report; re-import → no duplicates |
| P2 | `demo.sh` script with curl + recorded terminal | ≈2 min | Register/login → create event → hold seat → confirm with Idempotency-Key → second user gets 409; concurrency test run |
| P3 | GIF in README (≤ 10 MB) + optional 3-min video | ≈3 min | Login as OWNER vs VIEWER, drag/change status, comment, audit log, VIEWER cannot edit |
| P4 | Live URL (while running) + GIF + CloudWatch screenshot | ≈3 min | Create monitor → failure → incident opens → webhook alert once → status page (cached) → CI/CD deploy |

Rules for demos:

- Use **seed data** (`data.sql`, Flyway `R__seed.sql` in a `dev` profile, or a script), never your real finances or personal data.
- Rehearse the [2-minute and 10-minute walkthroughs](../16-interview-prep/project-deep-dive.md) from each spec out loud; record at least once.
- Have a fallback: if the live demo breaks in an interview, show the test that proves the behavior.
- P4 on AWS: follow [`../12-aws/cost-safety.md`](../12-aws/cost-safety.md). Stop/terminate when not demoing; keep screenshots and the teardown script.

---

## 6. Weekly project rhythm

| Day | Block | Project activity |
|---|---|---|
| Wed | 2 h | Pick 1 issue; branch; test first; implement; push; open draft PR |
| Sat | 3 h | Finish issue(s); self-review; merge; one Break-it exercise; update tracker |
| Sun | (review) | Update design doc/ADRs; re-read acceptance criteria for next week |
