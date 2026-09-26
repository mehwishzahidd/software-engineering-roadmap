# buggy-library — Library Lending Service

A small, dependency-light Java 21 codebase that models a public library's lending desk.
It is used for the code-reading and debugging drills in
[`../../drills.md`](../../drills.md) and for the OA simulations in [`../../../OA_PREP.md`](../../../OA_PREP.md).

> Treat this as a codebase you inherited on your first day. The tests describe how the
> product is **supposed** to behave. Some of them fail. Your job is to find out why.
> Do not read `drills.md` solutions before you have tried.

## Requirements

- JDK 21 (`java -version`)
- Maven 3.9+ (`mvn -v`)
- Only test dependency: JUnit Jupiter 5.10.x (downloaded from Maven Central on first build)

## Build and run

```bash
mvn -q test                      # compile + run all unit tests
mvn test -Dtest=SearchIndexTest  # run one test class
mvn test -Dtest='LibraryServiceTest#returnOnTime*'   # run matching test methods
mvn -q package -DskipTests && java -jar target/buggy-library-0.3.0-SNAPSHOT.jar   # scripted demo
```

Surefire writes per-class reports to `target/surefire-reports/` (`*.txt` has the stack traces).

## Domain rules (the specification)

| Rule | Value / behaviour |
|---|---|
| Loan period | A book is due **`loanPeriodDays` days after** the loan date. Default 14: borrowed 1 March → due 15 March. |
| Active-loan limit | A member may hold at most `maxActiveLoans` (default 3) unreturned books. |
| Copies | One physical copy per ISBN. A book on loan cannot be checked out again until returned. |
| Book identity | Two `Book` objects with the same ISBN are the **same book** (titles get corrected; ISBNs don't change). |
| Fines | `dailyFineCents × daysOverdue`, capped at `maxFineCents` (default 25¢/day, cap 1000¢), **then** the member-type discount. Returning on the due date is not late. |
| Discounts | `STANDARD` 0 %, `STUDENT` 50 %, `STAFF` 100 %. |
| Money | Always integer cents (`long`). Never `double`. |
| Email | Optional. Walk-in members may have no email. |
| Search | Single keyword, matches any word of the title or author, **case-insensitive**. Results are a read-only view for the caller; they must not let the caller change the index. |
| Overdue report | Unreturned loans past due, **most overdue first**. |
| Return all | Closing a membership returns **every** active loan of that member. |

## Package map

```
com.example.library
├── LibraryDemo                 entry point (scripted walkthrough, prints to stdout)
├── domain                      plain data + invariants
│   ├── Book                    catalogue entry, identity = ISBN
│   ├── Member, MemberType      member + tier (fine discount)
│   └── Loan                    one checkout; knows its own days overdue
├── policy                      business rules, no state
│   ├── LoanPolicy              due date, loan limit, fine rates
│   └── FineCalculator          overdue fine in cents
├── repo
│   └── InMemoryRepository      Map-backed stand-in for a database table
├── search
│   └── SearchIndex             inverted index word -> books
└── service                     application layer — the only API callers should use
    ├── LibraryService          checkout / return / search / reports
    └── LoanNotAllowedException business-rule violation
```

## Request flow for a checkout

```
caller -> LibraryService.checkout(memberId, isbn, today)
            -> InMemoryRepository<Member>.findById
            -> InMemoryRepository<Book>.findById
            -> onLoan set check (one copy per ISBN)
            -> LoanPolicy.canBorrow(activeCount)
            -> LoanPolicy.dueDateFor(today)
            -> new Loan(...) -> loans.save, activeLoansByMember, onLoan
```

## Conventions

- Dates are passed in explicitly (`LocalDate today`) instead of calling `LocalDate.now()`, so tests are deterministic.
- The service is **not** thread-safe; that is a known limitation, not a drill.
- Tests live in the same package as the class they test (`src/test/java/...`).
