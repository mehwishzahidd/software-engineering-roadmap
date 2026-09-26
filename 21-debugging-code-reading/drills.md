# Debugging and Code-Reading Drills

All drills use the project in [`exercises/buggy-library/`](./exercises/buggy-library/).
Work through them with the method in [`method.md`](./method.md). The drills run from
**Week 13** onward and feed the OA simulations in [`../OA_PREP.md`](../OA_PREP.md).

---

## Before every drill: get a clean copy

The repository copy must stay buggy so the drills can be reused. **Never commit fixes to it.**

```bash
# from the repo root
rm -rf ~/drills/buggy-library
mkdir -p ~/drills && cp -r 21-debugging-code-reading/exercises/buggy-library ~/drills/
cd ~/drills/buggy-library
git init -q && git add -A && git commit -qm "baseline"   # a local repo just for this drill
mvn -q test                                             # baseline: expect failures
```

With a local baseline commit you can use `git diff` to review your own fix and `git stash` / `git reset --hard` to start over.

**Baseline (unmodified project):** `Tests run: 40, Failures: 11, Errors: 1`. Twelve tests are red; 28 are green. Any fix that turns a green test red is a regression.

---

## How to use each drill

1. Start a timer for the **timebox**.
2. Read the task. Do **not** open the hints yet.
3. If you are stuck for 10 minutes, open **Hint 1**. Stuck again after 5 more minutes → **Hint 2**.
4. When done (or when the timebox ends), open **Solution** and compare: root cause, fix, and the test that proves it.
5. Log the drill in [`../trackers/interview-tracker.md`](../trackers/interview-tracker.md) (OA/drill section): time taken, hints used, what slowed you down.
6. Explain the bug out loud in 60 seconds: *symptom → root cause → fix → why the test now passes → how you'd prevent it.*

| Type | What it trains | Drills |
|---|---|---|
| Orientation / reading | Building, running, navigating unknown code | D01, D02 |
| Find-the-bug | Reproduce → isolate → hypothesize → fix | D03–D10 |
| Make failing tests pass | Triage + fix many bugs under time | D11 |
| Add a feature | Extending code you didn't write, test-first | D12, D13, D14 |
| Refactor | Changing structure without changing behavior | D15, D16 |
| Test writing | Finding what's untested and pinning it down | D17 |

---

## D01 — Orientation and triage

**Type:** orientation · **Timebox:** 20 min

Without editing any code:

1. Build the project and run all tests.
2. Produce a triage table: failing test → class under test → one-line symptom (expected vs actual / exception).
3. Group failures that you suspect share a root cause.
4. Rank the groups by what you'd fix first and say why.

<details>
<summary>Hint 1</summary>

`mvn test` prints a summary block under `[ERROR] Failures:` and `[ERROR] Errors:`. A *Failure* is a failed assertion; an *Error* is an unexpected exception. `target/surefire-reports/*.txt` has the full stack traces.
</details>

<details>
<summary>Hint 2</summary>

Two failures in different test classes can share a cause. Compare `LoanPolicyTest.dueDateIsLoanPeriodDaysAfterLoanDate` with `LibraryServiceTest.lateReturnIsFinedPerDayAfterDueDate`, and the three `Book`-related failures across `BookTest` and `SearchIndexTest`.
</details>

<details>
<summary>Solution</summary>

| # | Failing test | Symptom | Suspected group |
|---|---|---|---|
| 1 | `LoanPolicyTest.dueDateIsLoanPeriodDaysAfterLoanDate` | expected 2024-03-15, was 2024-03-14 | A: due date |
| 2 | `LibraryServiceTest.lateReturnIsFinedPerDayAfterDueDate` | expected 75, was 100 (one extra day) | A: due date |
| 3 | `BookTest.booksWithSameIsbnAreEqual` | two "equal" books not equal | B: identity |
| 4 | `BookTest.hashSetDeduplicatesEqualBooks` | set size 2, expected 1 | B: identity |
| 5 | `SearchIndexTest.reindexingAnEqualBookDoesNotDuplicateResults` | 2 results, expected 1 | B: identity |
| 6 | `FineCalculatorTest.studentDiscountIsApplied` | expected 50, was 0 | C: fine math |
| 7 | `SearchIndexTest.searchIsCaseInsensitive` | 0 results for `JaVa` | D: search |
| 8 | `SearchIndexTest.callersCannotCorruptTheIndexThroughSearchResults` | index emptied by caller | E: encapsulation |
| 9 | `LibraryServiceTest.returnAllReturnsEveryLoan_threeLoans` | `ConcurrentModificationException` | F: iteration |
| 10 | `LibraryServiceTest.returnAllReturnsEveryLoan_twoLoans` | one loan still active | F: iteration |
| 11 | `LibraryServiceTest.findMemberByEmailSkipsMembersWithoutEmail` | `NullPointerException` (Error) | G: null handling |
| 12 | `LibraryServiceTest.overdueLoansAreSortedMostOverdueFirst` | `[L2, L1, L3]` instead of `[L3, L1, L2]` | H: ordering |

Reasonable fix order: **A** and **B** first — they are domain-level and other behaviour builds on them (due dates feed fines; identity feeds sets and the index). Then the cheap, isolated ones (C, D, G, H), then F and E, which need more thought. The key skill here is noticing that 12 red tests are really **8 root causes**.
</details>

---

## D02 — Trace a checkout end-to-end

**Type:** code reading · **Timebox:** 20 min

Answer in writing, citing class and method names:

1. What is the entry point of the application? What would be the entry point in a web version?
2. List every class touched by `LibraryService.checkout(...)` in call order.
3. Where is "one copy per ISBN" enforced? What data structure?
4. Which state is **duplicated** (the same fact stored in two places)? What could go wrong?
5. Set a breakpoint in `LoanPolicy.dueDateFor` and run `LibraryServiceTest.checkoutRecordsActiveLoan` in the debugger. What is `loanPeriodDays`? What is returned?

<details>
<summary>Hint 1</summary>

Look for `public static void main`. The README's "Request flow" section is a claim — verify it against the code rather than trusting it.
</details>

<details>
<summary>Hint 2</summary>

`LibraryService` has four fields that hold loan-related state: `loans`, `activeLoansByMember`, `onLoan`, and each `Loan`'s own `returnDate`.
</details>

<details>
<summary>Solution</summary>

1. `LibraryDemo.main`. In a web version the entry points would be controller methods that call `LibraryService`.
2. `LibraryService.checkout` → `InMemoryRepository.findById` (members) → `InMemoryRepository.findById` (books) → `onLoan.contains` (`HashSet<String>`) → `activeLoansByMember.computeIfAbsent` → `LoanPolicy.canBorrow` → `LoanPolicy.dueDateFor` → `new Loan(...)` (validates dueDate ≥ loanDate) → `loans.save` → `active.add` → `onLoan.add`.
3. `onLoan`, a `Set<String>` of ISBNs, checked in `checkout`, cleared in `returnBook`.
4. "Is this loan active?" is stored three ways: `Loan.returnDate == null`, membership in `activeLoansByMember`, and the ISBN in `onLoan`. If any update path forgets one of them (see D09), they disagree: a returned loan can remain in the member's active list, or a book can be stuck "on loan". This is a classic source of bugs and the motivation for D16.
5. `loanPeriodDays = 14`, returns `loanDate + 13` days. This observation alone finds the D03 bug.
</details>

---

## D03 — Books come back "late" a day early

**Type:** find-the-bug · **Timebox:** 20 min
**Failing tests:** `LoanPolicyTest.dueDateIsLoanPeriodDaysAfterLoanDate`, `LibraryServiceTest.lateReturnIsFinedPerDayAfterDueDate`

A member complains that a 14-day loan taken on 1 March was fined when returned on 15 March. Find and fix the root cause. Both tests must pass; no green test may go red.

<details>
<summary>Hint 1</summary>

The service test's fine is 100 instead of 75, which is exactly one extra day at 25¢. Is the fine calculation wrong or is the input to it wrong?
</details>

<details>
<summary>Hint 2</summary>

Read the comment in `LoanPolicy.dueDateFor`. Is the comment consistent with the README spec ("due `loanPeriodDays` days **after** the loan date")?
</details>

<details>
<summary>Solution</summary>

**Root cause:** off-by-one in `LoanPolicy.dueDateFor`. The code (and its comment) treat the loan day as day 1 of the period, which contradicts the spec.

```java
// before
return loanDate.plusDays(loanPeriodDays - 1);
// after
return loanDate.plusDays(loanPeriodDays);
```

**Why both tests pass:** the service test fails *only* because the due date is one day early, so `daysOverdue` is one too high. Fixing the input fixes the output; `FineCalculator` was correct all along. Resist the temptation to "fix" the fine by subtracting one in `FineCalculator` — that would make the service test green while leaving every due date on every receipt wrong.

**Prevention:** boundary tests around the due date (return on due date = 0 fine, due date + 1 = one day's fine) and a comment that states the spec, not the implementation.

**Interview talking point:** "I noticed the fine was off by exactly one day's rate, so I checked the input to the calculation before touching the calculation."
</details>

---

## D04 — Students never pay fines

**Type:** find-the-bug · **Timebox:** 15 min
**Failing test:** `FineCalculatorTest.studentDiscountIsApplied`

Students should pay 50 % of fines. They pay nothing. Standard members pay correctly, staff correctly pay nothing.

<details>
<summary>Hint 1</summary>

Two of three member types behave correctly. What do 0 % and 100 % discounts have in common that 50 % doesn't?
</details>

<details>
<summary>Hint 2</summary>

Evaluate `payablePercent / 100` by hand when `payablePercent` is `50` and both operands are `int`.
</details>

<details>
<summary>Solution</summary>

**Root cause:** integer division. `(100 - 50) / 100` is `50 / 100` → `0` in `int` arithmetic, so `gross * 0 = 0`. With 0 % discount it's `100 / 100 = 1` and with 100 % discount it's `0 / 100 = 0`, which is why only students were affected — the bug hides behind "correct" edge values.

```java
// before
return gross * (payablePercent / 100);
// after — multiply first, then divide (still integer cents, rounds down)
return gross * payablePercent / 100;
```

**Discussion:** multiply-before-divide is correct here because `gross ≤ maxFineCents` so there's no overflow risk. If rounding matters (e.g., 25¢ × 50 % = 12.5¢), decide the policy explicitly — round half up with `Math.floorDiv`/`BigDecimal` — and write a test for it. Never switch to `double` for money.

**Prevention:** test *every* enum value, not just the extremes.
</details>

---

## D05 — The same book, twice

**Type:** find-the-bug · **Timebox:** 25 min
**Failing tests:** `BookTest.booksWithSameIsbnAreEqual`, `BookTest.hashSetDeduplicatesEqualBooks`, `SearchIndexTest.reindexingAnEqualBookDoesNotDuplicateResults`

A nightly import re-sends books that already exist. Searches then show duplicates.

<details>
<summary>Hint 1</summary>

What does `HashSet.add` use to decide whether an element is already present? Which class is the element?
</details>

<details>
<summary>Hint 2</summary>

Compare `Book` with `Member`. One of them overrides two methods from `Object`.
</details>

<details>
<summary>Solution</summary>

**Root cause:** `Book` does not override `equals`/`hashCode`, so it inherits identity equality from `Object`. Two `Book` instances with the same ISBN are different keys in any `HashSet`/`HashMap`, so the index's `Set<Book>` keeps both.

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Book other)) return false;
    return isbn.equals(other.isbn);
}

@Override
public int hashCode() {
    return isbn.hashCode();
}
```

**Why ISBN only?** The spec says identity is the ISBN; titles get corrected. Including `title` would make a corrected record a "different" book. Keep `equals` and `hashCode` consistent: same fields in both.

**Why did `LibraryService.addBook` not suffer?** It calls `searchIndex.remove(existing)` first — but `remove` uses `Set.remove`, which also relies on `equals`/`hashCode`. It only worked because the *same instance* happened to be removed. After the fix, removal works for equal copies too.

**Interview talking point:** the `equals`/`hashCode` contract (equal objects must have equal hash codes) and what happens to `HashMap` lookups when it's broken. See [`../01-java/02-oop.md`](../01-java/02-oop.md) and [`../01-java/03-collections-generics.md`](../01-java/03-collections-generics.md).
</details>

---

## D06 — "Java" finds nothing

**Type:** find-the-bug · **Timebox:** 15 min
**Failing test:** `SearchIndexTest.searchIsCaseInsensitive`

<details>
<summary>Hint 1</summary>

Look at how text is normalized on the way **in** (`index`) and on the way **out** (`search`).
</details>

<details>
<summary>Hint 2</summary>

`tokens(...)` lower-cases. Does `search(...)`?
</details>

<details>
<summary>Solution</summary>

**Root cause:** asymmetric normalization. Indexed tokens are lower-cased with `Locale.ROOT`; the query is only trimmed.

```java
String key = keyword.trim().toLowerCase(Locale.ROOT);
return index.getOrDefault(key, Set.of());   // see D07 for the return value
```

Use `Locale.ROOT` for machine-facing normalization (the "Turkish i" problem: `"TITLE".toLowerCase()` under a Turkish default locale yields a dotless ı). Better still: route both paths through the same `normalize()` helper so they cannot drift again.
</details>

---

## D07 — A caller emptied the index

**Type:** find-the-bug · **Timebox:** 20 min
**Failing test:** `SearchIndexTest.callersCannotCorruptTheIndexThroughSearchResults`

<details>
<summary>Hint 1</summary>

After `result.clear()`, the next `search("java")` returns nothing. So `result` and the index's internal state must be... the same object?
</details>

<details>
<summary>Hint 2</summary>

`Map.getOrDefault` returns the stored value itself, not a copy.
</details>

<details>
<summary>Solution</summary>

**Root cause:** mutable internal state leaks. `search` returns the `HashSet` stored in the index map. Any caller that modifies the result (sorting in place, `retainAll` to intersect two searches, `clear`) silently corrupts the index for everyone.

```java
return Set.copyOf(index.getOrDefault(key, Set.of()));   // immutable snapshot
// or: Collections.unmodifiableSet(...)  — a read-only *view* (cheaper, but reflects later changes)
```

**Trade-off to mention:** `Set.copyOf` costs O(k) per search and gives a stable snapshot; `unmodifiableSet` is O(1) but a caller iterating it while the index is updated can get a `ConcurrentModificationException`. For a small result set, copy. The same principle applies to getters returning `List` fields (Effective Java, "Make defensive copies when needed").

Note `Set.copyOf` rejects `null` elements — fine here because the index never stores null.
</details>

---

## D08 — NullPointerException on email lookup

**Type:** find-the-bug · **Timebox:** 15 min
**Failing test (Error):** `LibraryServiceTest.findMemberByEmailSkipsMembersWithoutEmail`

<details>
<summary>Hint 1</summary>

Read the full exception message. Java 14+ "helpful NullPointerExceptions" name the exact expression that was null.
</details>

<details>
<summary>Hint 2</summary>

Which side of `a.equalsIgnoreCase(b)` is allowed to be null?
</details>

<details>
<summary>Solution</summary>

**Root cause:** `m.getEmail().equalsIgnoreCase(email)` dereferences a nullable field. The existing passing test `findMemberByEmailIgnoresCase` never registered a member without email, so it didn't catch it. The null-email member must come **before** the match in iteration order to trigger it (`InMemoryRepository` preserves insertion order).

```java
.filter(m -> email.equalsIgnoreCase(m.getEmail()))   // email already null-checked above
// or: .filter(m -> m.getEmail() != null && m.getEmail().equalsIgnoreCase(email))
```

**Better design:** have `Member.getEmail()` return `Optional<String>` so the compiler forces callers to handle absence. Mention this as a follow-up refactor in an interview, but make the minimal fix first.
</details>

---

## D09 — Closing a membership

**Type:** find-the-bug · **Timebox:** 25 min
**Failing tests:** `LibraryServiceTest.returnAllReturnsEveryLoan_threeLoans` (throws `ConcurrentModificationException`), `LibraryServiceTest.returnAllReturnsEveryLoan_twoLoans` (no exception, but one loan is left active)

<details>
<summary>Hint 1</summary>

`returnAll` loops over a list. What does `returnBook` do to that same list?
</details>

<details>
<summary>Hint 2</summary>

`ArrayList`'s iterator checks `modCount` in `next()`, and `hasNext()` is just `cursor != size`. Simulate 2 elements: remove element 0 while the cursor is 1. What does `hasNext()` return?
</details>

<details>
<summary>Solution</summary>

**Root cause:** structural modification of a collection while iterating it with a for-each loop. `returnAll` iterates `activeLoansByMember.get(memberId)`, and each `returnBook` call removes the loan from **that same list**.

- 3 loans: after removing the first, `size = 2`, `cursor = 1` → `hasNext()` true → `next()` sees modCount changed → **CME**.
- 2 loans: after removing the first, `size = 1`, `cursor = 1` → `hasNext()` false → the loop **silently ends**, leaving the second loan active. This is the more dangerous variant: no exception, wrong data.

```java
// iterate over a snapshot, mutate the original
for (Loan loan : List.copyOf(activeLoansByMember.getOrDefault(memberId, List.of()))) {
    total += returnBook(loan.getId(), returnDate);
}
```

Alternatives: iterate with an explicit `Iterator` and call `iterator.remove()` (but here the removal happens deep inside `returnBook`, so that doesn't fit), or collect ids first then return them.

**Interview talking point:** "`ConcurrentModificationException` doesn't mean threads — it's a single-threaded fail-fast check. And it's best-effort: my two-element test shows it can silently skip elements instead of throwing."
</details>

---

## D10 — Overdue report upside down

**Type:** find-the-bug · **Timebox:** 15 min
**Failing test:** `LibraryServiceTest.overdueLoansAreSortedMostOverdueFirst`

<details>
<summary>Hint 1</summary>

"Most overdue" = the loan whose due date is **earliest**. Which order does `Comparator.comparing(Loan::getDueDate)` produce on its own?
</details>

<details>
<summary>Hint 2</summary>

What does `.reversed()` do to that?
</details>

<details>
<summary>Solution</summary>

**Root cause:** wrong comparator direction. `comparing(Loan::getDueDate)` is already ascending by due date = most overdue first. The `.reversed()` flips it to least overdue first.

```java
.sorted(Comparator.comparing(Loan::getDueDate))
// equivalent, and states intent better:
.sorted(Comparator.comparingLong((Loan l) -> l.daysOverdue(today)).reversed()
        .thenComparing(Loan::getId))   // deterministic tie-break
```

**Prevention:** a test with **three** elements in a non-trivial input order (with two elements, a reversed sort passes half the time by luck). Add a tie-breaker so equal keys sort deterministically.
</details>

---

## D11 — All green under time pressure

**Type:** make-failing-tests-pass · **Timebox:** 60 min (Weeks 13–19), 45 min (Week 22+)

From a fresh copy, make **all 40 tests pass** without editing any test file. Commit once per root cause with a message `fix(<area>): <what>`. Then run `git log --oneline` and explain each commit in one sentence.

Rules: no `@Disabled`, no changing assertions, no catching exceptions to hide them.

<details>
<summary>Hint 1</summary>

Do D01's triage first (5 minutes). Fix the cheapest isolated bugs first to shrink the red list quickly, and re-run the **whole** suite after each fix.
</details>

<details>
<summary>Hint 2</summary>

There are 8 root causes across 6 files: `LoanPolicy`, `FineCalculator`, `Book`, `SearchIndex` (2), `LibraryService` (3).
</details>

<details>
<summary>Solution</summary>

The complete fix set is D03 + D04 + D05 + D06 + D07 + D08 + D09 + D10. Expected result:

```
Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
```

Suggested commit sequence:

```
fix(policy): due date is loanPeriodDays after loan date
fix(policy): apply member discount without integer division
fix(domain): Book equality by ISBN (equals/hashCode)
fix(search): normalize query case like indexed tokens
fix(search): return immutable copy from search
fix(service): null-safe email lookup
fix(service): iterate snapshot in returnAll
fix(service): sort overdue loans most overdue first
```

Self-assessment: < 45 min with ≤ 1 hint = OA-ready on debugging tasks (target by Week 20).
</details>

---

## D12 — Feature: renew a loan

**Type:** add-a-feature · **Timebox:** 40 min · **Prerequisite:** D03 fixed (renewal depends on correct due dates)

**Spec:**
- `Loan LibraryService.renew(String loanId, LocalDate today)`
- New due date = **current due date** + `loanPeriodDays` (not today + period).
- Max **2** renewals per loan (`LoanPolicy.maxRenewals`, default 2).
- Cannot renew a returned loan, or an overdue loan (`today` after due date).
- Violations throw `LoanNotAllowedException`.

Write the tests **first** (at least 5: happy path, due date math, limit reached, returned, overdue).

<details>
<summary>Hint 1</summary>

`Loan.dueDate` is `final`. Either make it mutable with a guarded method (`extendDueDate`) or replace the `Loan` in the repository with a new instance. Consider what else holds a reference to the old `Loan` (`activeLoansByMember`).
</details>

<details>
<summary>Hint 2</summary>

Mutating in place (`loan.extendDueDate(newDue)` + `renewalCount++`) is simplest here because three structures reference the same `Loan` object. Replacing it would require updating all of them.
</details>

<details>
<summary>Solution</summary>

```java
// Loan
private LocalDate dueDate;          // no longer final
private int renewals;

public void renew(int loanPeriodDays) {
    if (isReturned()) throw new IllegalStateException("returned");
    this.dueDate = dueDate.plusDays(loanPeriodDays);
    this.renewals++;
}
public int getRenewals() { return renewals; }

// LibraryService
public Loan renew(String loanId, LocalDate today) {
    Loan loan = loans.findById(loanId)
            .orElseThrow(() -> new LoanNotAllowedException("Unknown loan: " + loanId));
    if (loan.isReturned()) throw new LoanNotAllowedException("Loan already returned");
    if (today.isAfter(loan.getDueDate())) throw new LoanNotAllowedException("Overdue loans cannot be renewed");
    if (loan.getRenewals() >= policy.maxRenewals()) throw new LoanNotAllowedException("Renewal limit reached");
    loan.renew(policy.loanPeriodDays());
    return loan;
}
```

Test sketch:

```java
@Test
void renewExtendsFromCurrentDueDate() {
    Loan loan = library.checkout("M1", "B1", DAY_0);             // due 15 Mar
    library.renew(loan.getId(), LocalDate.of(2024, 3, 10));
    assertEquals(LocalDate.of(2024, 3, 29), loan.getDueDate());
}

@Test
void thirdRenewalIsRejected() {
    Loan loan = library.checkout("M1", "B1", DAY_0);
    library.renew(loan.getId(), DAY_0);
    library.renew(loan.getId(), DAY_0);
    assertThrows(LoanNotAllowedException.class, () -> library.renew(loan.getId(), DAY_0));
}
```

**Design discussion:** adding `maxRenewals` to `LoanPolicy` changes its constructor — every `new LoanPolicy(...)` call in tests must change. Adding a second constructor or a builder avoids breaking callers. Mention this trade-off.
</details>

---

## D13 — Feature: overdue fines report

**Type:** add-a-feature · **Timebox:** 35 min · **Prerequisite:** D03, D04, D10 fixed

**Spec:** `List<OverdueLine> overdueReport(LocalDate today)` where `record OverdueLine(String loanId, String memberName, String title, long daysOverdue, long fineCents)`.
- Only unreturned, overdue loans.
- Sorted by **fine descending**, then **days overdue descending**, then loan id ascending.
- Fine uses the member's discount (so a student can sort below a standard member with fewer days).

<details>
<summary>Hint 1</summary>

You need `Member` for the name and discount. `members.findById(loan.getMemberId())` inside a stream `map`.
</details>

<details>
<summary>Hint 2</summary>

`Comparator.comparingLong(OverdueLine::fineCents).reversed().thenComparing(...)` — careful: `.reversed()` applies to **everything chained before it**, so build the full chain in the right order or reverse each key individually with `Comparator.reverseOrder()`.
</details>

<details>
<summary>Solution</summary>

```java
public record OverdueLine(String loanId, String memberName, String title, long daysOverdue, long fineCents) {}

public List<OverdueLine> overdueReport(LocalDate today) {
    return loans.findAll().stream()
            .filter(l -> !l.isReturned() && l.daysOverdue(today) > 0)
            .map(l -> {
                Member m = members.findById(l.getMemberId()).orElseThrow();
                return new OverdueLine(l.getId(), m.getName(), l.getBook().getTitle(),
                        l.daysOverdue(today), fineCalculator.fineCents(l, m.getType(), today));
            })
            .sorted(Comparator.comparingLong(OverdueLine::fineCents).reversed()
                    .thenComparing(Comparator.comparingLong(OverdueLine::daysOverdue).reversed())
                    .thenComparing(OverdueLine::loanId))
            .toList();
}
```

Test with: a standard member 4 days late (100¢), a student 6 days late (75¢), a standard member 4 days late with a lower loan id → expected order by fine then id. Also a staff member (0¢ fine, still overdue) goes last.
</details>

---

## D14 — Feature: multi-word search

**Type:** add-a-feature · **Timebox:** 30 min · **Prerequisite:** D05, D06, D07 fixed

**Spec:** `SearchIndex.searchAll(String query)` returns books matching **every** word (AND). `"java bloch"` → only *Effective Java*. Empty/blank query → empty set. Order of words doesn't matter; case doesn't matter.

<details>
<summary>Hint 1</summary>

Reuse `tokens(query)`. Start from the smallest posting set and intersect.
</details>

<details>
<summary>Hint 2</summary>

`retainAll` mutates the receiver. Which set are you calling it on? (Revisit D07 — do not intersect the index's internal sets.)
</details>

<details>
<summary>Solution</summary>

```java
public Set<Book> searchAll(String query) {
    if (query == null || query.isBlank()) return Set.of();
    List<Set<Book>> postings = tokens(query).stream()
            .map(t -> index.getOrDefault(t, Set.of()))
            .sorted(Comparator.comparingInt(Set::size))
            .toList();
    if (postings.isEmpty()) return Set.of();
    Set<Book> result = new HashSet<>(postings.get(0));   // copy — never mutate the index
    for (int i = 1; i < postings.size() && !result.isEmpty(); i++) {
        result.retainAll(postings.get(i));
    }
    return Set.copyOf(result);
}
```

Complexity: O(k · m) where k = number of words and m = size of the smallest posting list. Starting from the smallest set is the same trick real search engines use.
</details>

---

## D15 — Refactor trap: "just make Book a record"

**Type:** refactor · **Timebox:** 25 min · **Prerequisite:** D05 fixed

A reviewer suggests converting `Book` to a `record` "to remove boilerplate". Do it, keep all tests green, and keep behaviour identical.

<details>
<summary>Hint 1</summary>

What does a record's generated `equals` compare? What does the spec say book identity is?
</details>

<details>
<summary>Hint 2</summary>

Records can have a compact constructor (validation/trimming) and can override `equals`/`hashCode`.
</details>

<details>
<summary>Solution</summary>

A naive record `record Book(String isbn, String title, String author, int year)` compares **all four components**, which breaks `booksWithSameIsbnAreEqual` (the titles differ). It also loses the trimming/blank validation and renames accessors (`getIsbn()` → `isbn()`), breaking every caller.

A behaviour-preserving version:

```java
public record Book(String isbn, String title, String author, int year) {
    public Book {
        isbn = requireText(isbn, "isbn");
        title = requireText(title, "title");
        author = requireText(author, "author");
    }
    @Override public boolean equals(Object o) { return o instanceof Book b && isbn.equals(b.isbn); }
    @Override public int hashCode() { return isbn.hashCode(); }
    public String getIsbn() { return isbn; }     // keep old accessors during migration, or update callers
    // ... requireText as before
}
```

**The real lesson:** a record whose `equals` is overridden to ignore components surprises readers ("records are value-based"). Many teams would reject this refactor and keep a class. Being able to say *why* you'd push back on a suggested refactor is a strong junior signal.
</details>

---

## D16 — Refactor: one source of truth for "active"

**Type:** refactor · **Timebox:** 40 min · **Prerequisite:** D11 (all green)

Remove the duplicated state identified in D02: delete `activeLoansByMember` and `onLoan`, and derive both from `loans` (`!loan.isReturned()`). All 40 tests must stay green. Then inject a `java.time.Clock` into `LibraryService` for a `checkout(memberId, isbn)` overload that uses `LocalDate.now(clock)`.

<details>
<summary>Hint 1</summary>

Write two private helpers: `List<Loan> activeLoansOf(String memberId)` and `boolean isOnLoan(String isbn)`. Replace every read and delete every write of the old fields.
</details>

<details>
<summary>Hint 2</summary>

Tests use `Clock.fixed(Instant.parse("2024-03-01T10:00:00Z"), ZoneOffset.UTC)`.
</details>

<details>
<summary>Solution</summary>

```java
private List<Loan> activeLoansOf(String memberId) {
    return loans.findAll().stream()
            .filter(l -> l.getMemberId().equals(memberId) && !l.isReturned())
            .toList();
}

private boolean isOnLoan(String isbn) {
    return loans.findAll().stream()
            .anyMatch(l -> !l.isReturned() && l.getBook().getIsbn().equals(isbn));
}
```

`returnAll` becomes `for (Loan l : activeLoansOf(memberId))` — the CME from D09 becomes **impossible by construction** because the loop iterates a fresh list.

**Trade-off:** lookups go from O(1) to O(total loans). For an in-memory demo that's fine; with a database it becomes a `WHERE member_id = ? AND returned_at IS NULL` query backed by an index — which is exactly how you'd model it in PostgreSQL (see [`../04-sql-databases/05-indexes-performance.md`](../04-sql-databases/05-indexes-performance.md)). Removing derived state removes a whole category of consistency bugs; caching it back is an optimization you'd make only with a measured reason.
</details>

---

## D17 — Write the missing tests

**Type:** test writing · **Timebox:** 25 min

Without looking at other drills' solutions, write tests that **would have caught** at least three more potential bugs that are *not* currently tested. Targets:

1. Loan limit boundary: exactly `maxActiveLoans` allowed, `maxActiveLoans + 1` rejected, and returning one allows another.
2. Fine cap **with** a discount (is the cap applied before or after the discount? The README says before — pin it).
3. `addBook` with a corrected title for an existing ISBN: search by the **old** title word must no longer find it; the new word must.
4. `returnBook` for an unknown loan id throws `LoanNotAllowedException`, not `NoSuchElementException`.

<details>
<summary>Hint 1</summary>

Use `@ParameterizedTest` + `@CsvSource` for boundaries (see [`../09-testing/junit5.md`](../09-testing/junit5.md)).
</details>

<details>
<summary>Hint 2</summary>

For (3), the old and new `Book` objects are equal (same ISBN) after D05 — `SearchIndex.remove` tokenizes the *argument's* title. Which `Book` does `addBook` pass to `remove`?
</details>

<details>
<summary>Solution</summary>

```java
@Test
void studentFineIsCappedBeforeDiscount() {
    // 365 days late: gross capped at 1000, student pays 500
    assertEquals(500, calculator.fineCents(loan(), MemberType.STUDENT, DUE.plusDays(365)));
}

@Test
void correctedTitleReplacesOldIndexEntries() {
    library.addBook(new Book("B9", "Effectve Jva", "J Bloch", 2018));   // typo import
    library.addBook(new Book("B9", "Effective Java", "J Bloch", 2018)); // correction
    assertTrue(library.search("effectve").isEmpty());
    assertEquals(1, library.search("effective").size());
}
```

(3) passes because `addBook` removes the **stored** (old) instance, whose tokens are the old words — a good example of a test that *confirms* correct behaviour and protects it from a future refactor that passes the new book to `remove`.

(2) fails until D04 is fixed (it returns 0) — a second test pinning the same bug from a different angle. (1) and (4) should pass as-is. A test that passes on first run isn't wasted: it's a regression guard. But **always** check it can fail: temporarily break the code (e.g., `<=` in `canBorrow`) and watch it go red.
</details>

---

## Feature-request backlog (no code, no solutions)

Use these for OA "small feature" slots, repo-modification drills in Weeks 23–26, and mock interviews ("here's a codebase, add X"). Each needs: tests first, a short design note in the PR description, and a demo line in `LibraryDemo`.

| ID | Feature | Acceptance criteria | Size |
|---|---|---|---|
| F1 | **Reservation queue** | `reserve(memberId, isbn)` on a book that's on loan puts the member in a FIFO queue; on return, the book is held for the head of the queue for 3 days; nobody else can check it out; a member can't reserve a book they currently hold or reserve twice. | M |
| F2 | **Overdue report sorted by fine** with CSV export | Extends D13: `String overdueReportCsv(LocalDate)` with header row, cents formatted as `12.50`, proper CSV quoting of titles containing commas/quotes. | S |
| F3 | **Member suspension** | Members with unpaid fines ≥ 500¢ cannot borrow; `payFine(memberId, cents)` reduces the balance; partial payments allowed; overpayment rejected. | M |
| F4 | **Multiple copies per ISBN** | `addCopies(isbn, n)`; checkout succeeds while any copy is free; `availableCopies(isbn)`. Replaces the `onLoan` set semantics. | M |
| F5 | **Catalogue CSV import** | `ImportResult importCsv(Path)` with per-row error reporting (line number + reason), skipping bad rows, deduping by ISBN (depends on D05). | M |
| F6 | **Thread-safe checkout** | Two threads checking out the same ISBN concurrently → exactly one succeeds. Write the concurrency test first (`ExecutorService` + `CountDownLatch`), then fix with the smallest correct locking strategy. Compare with FlowGrid's reservation test and LedgerX's ordered locking. | L |
| F7 | **Loan history per member** | `List<Loan> history(memberId, int page, int size)` newest first; validate page/size. | S |
| F8 | **Due-soon reminders** | `List<Reminder> dueWithin(int days, LocalDate today)` for members with an email only (depends on D08). | S |
