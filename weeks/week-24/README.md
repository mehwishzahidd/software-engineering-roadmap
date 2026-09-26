# Week 24 — Polish I: bugs, refactors, tests, security (all four projects)

[← Week 23](../week-23/) · [Roadmap](../../ROADMAP.md) · [Week 25 →](../week-25/)

**Phase 5 · Polish & interviews** (weeks 24–26) · Ends with [**Checkpoint 24**](../../checkpoints/checkpoint-24.md) — *"Would I pass an internship/junior loop today?"*

| Block | Hours | Focus |
|---|---:|---|
| Project | 22 | All four projects: bug-fix sprint, targeted refactors, test gaps (Java **and** Python components), security pass, README/ARCHITECTURE/API/DATABASE docs |
| Learning | 4 | Security review checklist (OWASP API Top 10), refactoring techniques |
| DSA (Python) | 6 | Timed mixed — **6 new** + reviews + 1 Java rep, plus OA sims |
| Interview / review | 13 | **2 mocks (Track A + Track B), OA sims #7–8**, recruiter-screen rehearsal, applications, **CP-24** |

---

## 1. Main objective

Switch from building to **hardening and proving**. Every project gets the same pass: known
bugs fixed, the two ugliest modules refactored in place, test gaps closed (including the
Python tools and SDK), a security review against a written checklist, and the four core
documents (README, ARCHITECTURE, API, DATABASE) brought to a standard a hiring manager can
read in ten minutes. Interview hours triple: from now on you run full loops every week.

## 2. Prerequisites

- All four projects tagged `v1.0` ([FlowGrid W8](../week-08/), [LedgerX W13](../week-13/), [ForgeCI W18](../week-18/), [FlagForge W23](../week-23/)). If one is not, its remaining M-work is the first item on Monday — Polish weeks are the buffer (ROADMAP §13).
- Résumé-defense matrix complete ([Week 23](../week-23/)).
- OWASP first pass done on FlagForge ([Week 23](../week-23/) §4.4) — this week applies it to the other three.

## 3. Learning topics

| Topic | Subtopics | Read |
|---|---|---|
| Security review checklist | OWASP API Top 10 (2023): BOLA, broken auth, property-level authz, unrestricted resource consumption, function-level authz, sensitive business flows, SSRF, misconfiguration, inventory, unsafe API consumption; secrets hygiene; dependency scanning; JWT pitfalls (alg, expiry, storage) | [`05-spring-boot/05-security-jwt.md`](../../05-spring-boot/05-security-jwt.md), [`06-rest-apis/api-design-guide.md`](../../06-rest-apis/api-design-guide.md), [`14-cs-fundamentals/software-engineering-practices.md`](../../14-cs-fundamentals/software-engineering-practices.md) |
| Refactoring techniques | Extract method/class, replace conditional with polymorphism (strategy), introduce parameter object, remove duplication across projects' idempotency filters, characterization tests before refactoring, small PRs | [`14-cs-fundamentals/software-engineering-practices.md`](../../14-cs-fundamentals/software-engineering-practices.md), [`01-java/02-oop.md`](../../01-java/02-oop.md), [`21-debugging-code-reading/method.md`](../../21-debugging-code-reading/method.md) |
| Test-gap analysis | JaCoCo report reading (branch coverage on the hot paths, not a global number), mutation-style thinking ("what test would fail if I broke this?"), `pytest --cov` for Python tools | [`09-testing/README.md`](../../09-testing/README.md), [`09-testing/junit5.md`](../../09-testing/junit5.md), [`19-python/04-testing-and-scripting.md`](../../19-python/04-testing-and-scripting.md) |
| Documentation for readers with 10 minutes | README structure (what/why/architecture picture/run/tests/measurements/limitations), ARCHITECTURE (components + one request's path), API (from OpenAPI + error catalogue), DATABASE (ERD + invariants + migrations policy) | [`18-projects/flowgrid/docs-and-resume.md`](../../18-projects/flowgrid/docs-and-resume.md), [`18-projects/ledgerx/docs-and-resume.md`](../../18-projects/ledgerx/docs-and-resume.md), [`18-projects/forgeci/docs-and-resume.md`](../../18-projects/forgeci/docs-and-resume.md), [`18-projects/flagforge/docs-and-resume.md`](../../18-projects/flagforge/docs-and-resume.md) |

## 4. Concepts to learn

### 4.1 A security review is a table, not a feeling

For each project, `docs/SECURITY.md` gets a table with one row per OWASP API item and one per project-specific risk:

| Risk | Where | Control | Evidence (test / config) | Status |
|---|---|---|---|---|
| BOLA on `/orders/{id}` | FlowGrid | ownership check in service | `OrderAccessIT` | done |
| Unrestricted consumption | LedgerX `/transfers` | per-user rate limit (Redis) | `RateLimitIT` | TODO #142 |
| Docker socket in worker | ForgeCI | not mounted in step containers; limits | `docs/SECURITY.md` §2 | done (documented limitation) |
| SDK key leakage in logs | FlagForge | key hash only; log filter | `LogRedactionTest` | done |

Typical findings this week: JWT with no expiry check on refresh; `@PreAuthorize` missing on one admin endpoint; stack traces in ProblemDetail `detail` in prod profile; CORS `*`; `.env.example` containing a real value; Compose exposing Postgres on `0.0.0.0:5432`.

```java
// ProblemDetail: never leak internals in production
@ExceptionHandler(Exception.class)
ProblemDetail unexpected(Exception e) {
    log.error("unhandled", e);                              // full trace to logs only
    return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
}
```

- **Interview angle (Track B):** "How do you secure your APIs?" — answer with the table's rows and the tests that prove them, not with "I used Spring Security".

### 4.2 Refactor with a safety net, in place

1. Pick the module you are most embarrassed by (usually a 400-line service).
2. Write **characterization tests** that pin current behaviour (including the weird bits).
3. Refactor in ≤ 3 small PRs: extract, rename, remove duplication. No behaviour change.
4. Only then fix the behaviour bugs you found, each with its own test.

Candidates: FlowGrid's allocation scorer (strategy per criterion); LedgerX's transfer service (transaction boundary vs orchestration); ForgeCI's worker loop (state machine object); FlagForge's rule matching (predicate compilation). Never restart a project "cleaner" (ROADMAP §13 rule 6).

### 4.3 Test gaps: ask "what would I break undetected?"

- Java: run JaCoCo, look only at `service`/`domain` packages; list uncovered **branches** on money/inventory/queue paths; write the tests for those, ignore getters.
- Python components: `pytest --cov=tools --cov=sdk-python`, same rule: cover the decision branches (signature mismatch, empty result set, malformed input, rule overlap). Add `mypy --strict` or at least `pyright` basic mode to CI for the tools and fix the findings — types are the second test suite.
- Cross-cutting: every failure-engineering scenario has a named regression test (audit the mapping issue → test).

### 4.4 Docs a hiring manager can read in ten minutes

README order: one-paragraph what/why → architecture diagram (can be ASCII this week; real diagrams in W25) → "run it in 3 commands" → "what is tested and how" → **measured results with links** → known limitations → docs index. If a README needs scrolling to find how to run it, it fails.

## 5. Resources

- OWASP API Security Top 10 (2023) project page.
- "Refactoring" (Fowler) — catalogue entries for the moves above.
- JaCoCo docs; `pytest-cov`; `mypy` docs.
- Spring Boot reference: "Error handling" (`ProblemDetail`), Actuator exposure in production.
- [`RESOURCES.md`](../../RESOURCES.md).

## 6. Exercises and assignments

### Exercise A — Bug triage (Mon, 1 h)

Open every repo's issues; label `bug`, `refactor`, `test-gap`, `security`, `docs`; assign each to milestone `Polish I` or `Polish II`. Acceptance: no unlabeled issue; each project has ≤ 10 items for this week.

### Exercise B — Security table (Wed, 2 h across projects)

Fill the §4.1 table for all four. Acceptance: every row has evidence or an issue number.

### Break it (across projects)

- Run each API with the `prod` profile and trigger an unexpected exception — does the response leak a class name?
- Send a JWT with `alg: none` / expired / wrong signature to each service — 401 every time?
- Feed `tools/loadsim` (ForgeCI) an unsigned payload; feed `config-validator` (FlagForge) an empty file and a 5 MB file — graceful?

### Debug it

- A refactor PR turns one IT flaky: characterization test depended on ordering of an unordered `Set` — fix the test's assertion, not the code.
- `pytest` passes locally, fails in CI: missing `pyproject.toml` dependency pin or a test that reads a file relative to the CWD (use `pathlib.Path(__file__).parent`).

## 7. DSA — Timed mixed (6 new, Python) + OA sims

Week 20 selection rule with the OA clock (Easy 12 / Medium 25 / Hard 40 min), all in Python, cheatsheet closed ([`PYTHON_INTERVIEW_CHEATSHEET.md`](../../PYTHON_INTERVIEW_CHEATSHEET.md) is for *after* the timer). Because OA sims #7–8 add 4 more timed problems, keep the 6 new ones to two 2-problem sets + two singles. Pool additions (real numbers): 15 (3Sum), 39 (Combination Sum), 102 (Binary Tree Level Order Traversal), 152 (Maximum Product Subarray), 200 (Number of Islands), 215 (Kth Largest Element in an Array), 239 (Sliding Window Maximum — Hard), 416 (Partition Equal Subset Sum), 981 (Time Based Key-Value Store), 1448 (Count Good Nodes in Binary Tree).

**Java rep:** 200 (Number of Islands) in Java — BFS with `ArrayDeque<int[]>`; mention it in Track B if asked "how comfortable are you with Java outside Spring?".

Reviews due: Day-3 of W23, Day-7 of W22, Day-14 of W21, Day-30 of W19 graphs/bits. Sunday: recompute `weak` per pattern — W25 takes its 5 problems from the top of that list.

## 8. Project work — Polish I (all four)

Specs and doc standards: [`18-projects/flowgrid/docs-and-resume.md`](../../18-projects/flowgrid/docs-and-resume.md) · [`18-projects/ledgerx/docs-and-resume.md`](../../18-projects/ledgerx/docs-and-resume.md) · [`18-projects/forgeci/docs-and-resume.md`](../../18-projects/forgeci/docs-and-resume.md) · [`18-projects/flagforge/docs-and-resume.md`](../../18-projects/flagforge/docs-and-resume.md) · failure suites in each project's `failure-engineering.md`.

Budget ≈ 5.5 h per project. Same checklist for each:

### Weekly task checklist (× 4 projects)

- [ ] Milestone `Polish I` with labeled issues (Exercise A)
- [ ] Bugs: fix every `bug` issue with a regression test (Java or `pytest` as appropriate)
- [ ] Refactor: one module, characterization tests first, ≤ 3 PRs, no behaviour change
- [ ] Test gaps: JaCoCo branch review on core packages; `pytest --cov` + `mypy`/`pyright` on Python components; failure-scenario → regression-test mapping complete
- [ ] Security: §4.1 table in `docs/SECURITY.md`; fix `done`-able rows; prod-profile check (no stack traces, no `*` CORS, no exposed DB port, Actuator restricted); Dependabot/`dependency-check` enabled; secrets scan of history (`git log -p | grep -i secret` at minimum)
- [ ] Docs: README (§4.4 order), ARCHITECTURE, API, DATABASE reviewed and updated; Python components have READMEs linked from TESTING.md
- [ ] CI: Java + Python test jobs both required checks on `main`
- [ ] Deployed instance (if running) redeployed from the polished `main`; otherwise "deploy on demand" documented

### Acceptance summary

- Zero open `bug` issues in `Polish I`; every fix has a test.
- One refactor per project merged with green characterization tests.
- Security table complete for all four; no leaked internals in prod profile; no secret in git history.
- README/ARCHITECTURE/API/DATABASE readable in 10 minutes each; Python components documented and in CI.
- `v1.0.1` (or `v1.1`) tag per project with a changelog entry.

### Verification tests (examples to write where missing)

| Project | Test | Proves |
|---|---|---|
| FlowGrid | `OrderAccessIT` (other user → 404), `ProdProfileErrorResponseTest` | BOLA control; no leak |
| LedgerX | `RateLimitIT`, `test_verifier_detects_drift` (pytest, inject a bad entry) | consumption limit; Python verifier actually catches errors |
| ForgeCI | `WebhookUnsignedRejectedIT`, `test_loadsim_rejects_unsigned` (pytest) | HMAC on both sides |
| FlagForge | `SdkKeyNotLoggedTest`, `test_validator_large_input` (pytest) | secret hygiene; tool robustness |

### Failure scenarios to (re)run

One per project, chosen from its `failure-engineering.md` as the scenario you are least able to explain from memory. Re-run it, re-read the logs, rewrite the explanation in the doc in ≤ 8 lines.

### GitHub expectations

- Milestone `Polish I` closed in all four repos; `docs/SECURITY.md` tables; CI shows Java + Python checks; changelog.

Do not write new features this week. Cutting scope is the skill being trained (ROADMAP §8).

## 9. Git activity

- Small PRs (< 300 lines) labeled by type; characterization tests in their own PR before the refactor PR.
- `git log --oneline v1.0..HEAD` per project becomes the changelog.
- Tag `v1.0.1`/`v1.1` and update the release that the résumé links to.

## 10. Interview preparation (13 h)

Two tracks, never mixed (ROADMAP §10). From this week: **2 mocks/week (one per track), 2 OA sims/week, full loops.**

**Track A (Python coding)**
- **OA simulation #7** (Tue, 90 min) and **#8** (Fri, 120 min): [`OA_PREP.md`](../../OA_PREP.md). Each = 2 timed Python problems + one Java task from [`21-debugging-code-reading/exercises/buggy-library/`](../../21-debugging-code-reading/exercises/buggy-library/) (#7: fix failing tests; #8: repo-modification drill from [`21-debugging-code-reading/drills.md`](../../21-debugging-code-reading/drills.md)). Score parts separately in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).
- **Coding mock** (Thu, 45 min + 15 min debrief, Python): [`16-interview-prep/mock-interviews.md`](../../16-interview-prep/mock-interviews.md), scored with [`INTERVIEW_CHECKLIST.md`](../../INTERVIEW_CHECKLIST.md). Target ≥ 3/4 consistently.

**Track B (Java / projects / résumé / behavioral)**
- **Engineering mock** (Sat, 60 min): 15-minute project deep-dive **without notes** (interviewer picks the project) + 30 min technical probing across [`RESUME_INTERVIEW_QUESTIONS.md`](../../RESUME_INTERVIEW_QUESTIONS.md) + 15 min behavioral ([`16-interview-prep/behavioral.md`](../../16-interview-prep/behavioral.md), incl. the career-gap question).
- **Recruiter-screen rehearsal** (Wed, 30 min): [`16-interview-prep/recruiter-screen.md`](../../16-interview-prep/recruiter-screen.md) — 60-second "about me" now names all four projects and the Python tooling in one breath.
- **Résumé grill** (Wed, 1 h): all technologies, random order, 2 minutes each, from [`RESUME_TECH_DEFENSE.md`](../../RESUME_TECH_DEFENSE.md)'s matrix; mark anything below "confident" for W25.

**Applications (Wed/Sun, 2 h):** you are at the **interview-ready** stage of [`JOB_READINESS.md`](../../JOB_READINESS.md) after CP-24 — apply at that tier's weekly volume; keep a pipeline table (company, role, stage, next action) in [`trackers/interview-tracker.md`](../../trackers/interview-tracker.md).

## 11. Revision

- 45 min: re-read your four `docs/DESIGN_DECISIONS`/ADRs back-to-back and write one paragraph: "the three decisions I would defend hardest and the one I would change".
- 30 min: [`14-cs-fundamentals/interview-questions.md`](../../14-cs-fundamentals/interview-questions.md) — 10 random questions out loud.
- DSA reviews (never skipped).

## 12. Daily plan

| Day | Plan |
|---|---|
| **Mon (8 h)** | Learning 1: OWASP walk-through · Project 5: Exercise A triage; FlowGrid bugs + refactor start · DSA 1.5 (timed set) · Interview 0.5: schedule mocks |
| **Tue (8 h)** | Project 4: FlowGrid finish; LedgerX bugs + Python verifier tests · DSA 1 (reviews) · **OA sim #7** 1.5 h + review 0.5 · Interview 1: applications |
| **Wed (8 h)** | Learning 1: refactoring moves · Project 3.5: LedgerX refactor + security table · DSA 1 (single) · Interview 2.5: recruiter rehearsal 0.5, résumé grill 1, applications 1 |
| **Thu (8 h)** | Learning 1: test-gap method · Project 4: ForgeCI bugs, refactor, `tools/` coverage · DSA 1 (timed set) · **Track A mock** 1 h · Docs 1 |
| **Fri (6 h)** | Project 3: FlagForge bugs, refactor, validator/SDK-python coverage · **OA sim #8** 2 h · DSA reviews 0.5 · Retro 0.5 |
| **Sat (6 h)** | Project 2.5: docs pass (all four), tags · **Track B mock** 1.5 h + debrief · Interview 1: story bank refresh · DSA 1 (Java rep) |
| **Sun (3 h)** | **Checkpoint 24** (timed coding in Python, knowledge, 15-min deep-dive) · trackers · plan W25 · rest |

Total ≈ 47 h; drop the Sunday plan block before dropping rest.

## 13. End-of-week test — Checkpoint 24

Run [`checkpoints/checkpoint-24.md`](../../checkpoints/checkpoint-24.md) in full: timed Python coding, knowledge questions, a practical task (likely a repo-modification drill in Java), explain-out-loud prompts, a project review and a 15-minute deep-dive without notes. Record the score in [`trackers/weekly-progress.md`](../../trackers/weekly-progress.md). A fail → remediation in W25–26 review blocks; two consecutive fails in one area → freeze that area's polish and remediate (ROADMAP §9).

## 14. Mastery checklist

- [ ] I can run a security review against OWASP API Top 10 with evidence for every row
- [ ] I can refactor a module safely with characterization tests and small PRs
- [ ] I can find real test gaps (branches on critical paths) in Java and Python and close them
- [ ] Each project's README/ARCHITECTURE/API/DATABASE can be understood in 10 minutes
- [ ] I run OA sims at pace in Python and debugging tasks in Java without mixing the two
- [ ] I can deliver a 15-minute deep-dive of any of the four projects without notes

## 15. Expected deliverables

- Four repos: milestone `Polish I` closed, security tables, tags, Java + Python CI checks.
- CP-24 score; remediation list if any.
- Trackers: project (Polish I rows per project), DSA (6 Python + reviews + Java rep + `weak` recomputed), interview (OA #7/#8 split, Track A/B mock scores, recruiter rehearsal notes, application pipeline), technology (Python tooling "tested + typed in CI"), weekly progress.

## 16. If behind / stretch

**Behind:** prioritise per ROADMAP §8 — security fixes and test gaps before refactors; docs before refactors; refactor only the one module you would be asked about in a deep-dive. Never let interview hours drop below 10 this week.

**Stretch:** ArchUnit rules (no controller → repository), a shared `idempotency` note comparing the three implementations, `ruff` + `mypy --strict` on all Python components.
