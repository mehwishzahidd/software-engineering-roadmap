package com.example.library.service;

import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.domain.Member;
import com.example.library.policy.FineCalculator;
import com.example.library.policy.LoanPolicy;
import com.example.library.repo.InMemoryRepository;
import com.example.library.search.SearchIndex;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Application service: the only class callers (CLI, a future REST layer) should talk to.
 * Not thread-safe.
 */
public class LibraryService {

    private final InMemoryRepository<String, Book> books = new InMemoryRepository<>(Book::getIsbn);
    private final InMemoryRepository<String, Member> members = new InMemoryRepository<>(Member::getId);
    private final InMemoryRepository<String, Loan> loans = new InMemoryRepository<>(Loan::getId);

    /** memberId -> that member's loans that are not yet returned. */
    private final Map<String, List<Loan>> activeLoansByMember = new HashMap<>();
    /** ISBNs currently checked out (one physical copy per ISBN in this model). */
    private final Set<String> onLoan = new HashSet<>();

    private final SearchIndex searchIndex = new SearchIndex();
    private final LoanPolicy policy;
    private final FineCalculator fineCalculator;
    private long nextLoanId = 1;

    public LibraryService(LoanPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
        this.fineCalculator = new FineCalculator(policy);
    }

    // ---------------------------------------------------------------- catalogue

    public void addBook(Book book) {
        books.findById(book.getIsbn()).ifPresent(searchIndex::remove);
        books.save(book);
        searchIndex.index(book);
    }

    public Optional<Book> findBook(String isbn) {
        return books.findById(isbn);
    }

    public Set<Book> search(String keyword) {
        return searchIndex.search(keyword);
    }

    // ---------------------------------------------------------------- members

    public void registerMember(Member member) {
        members.save(member);
    }

    public Optional<Member> findMemberByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return members.findAll().stream()
                .filter(m -> m.getEmail().equalsIgnoreCase(email))
                .findFirst();
    }

    // ---------------------------------------------------------------- lending

    public Loan checkout(String memberId, String isbn, LocalDate today) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new LoanNotAllowedException("Unknown member: " + memberId));
        Book book = books.findById(isbn)
                .orElseThrow(() -> new LoanNotAllowedException("Unknown book: " + isbn));
        if (onLoan.contains(isbn)) {
            throw new LoanNotAllowedException("Book already on loan: " + isbn);
        }
        List<Loan> active = activeLoansByMember.computeIfAbsent(member.getId(), k -> new ArrayList<>());
        if (!policy.canBorrow(active.size())) {
            throw new LoanNotAllowedException("Loan limit reached for member " + memberId);
        }
        Loan loan = new Loan("L" + nextLoanId++, book, member.getId(), today, policy.dueDateFor(today));
        loans.save(loan);
        active.add(loan);
        onLoan.add(isbn);
        return loan;
    }

    /**
     * Returns one loan.
     *
     * @return the fine owed, in cents
     */
    public long returnBook(String loanId, LocalDate returnDate) {
        Loan loan = loans.findById(loanId)
                .orElseThrow(() -> new LoanNotAllowedException("Unknown loan: " + loanId));
        if (loan.isReturned()) {
            throw new LoanNotAllowedException("Loan already returned: " + loanId);
        }
        loan.markReturned(returnDate);
        activeLoansByMember.getOrDefault(loan.getMemberId(), new ArrayList<>()).remove(loan);
        onLoan.remove(loan.getBook().getIsbn());
        Member member = members.findById(loan.getMemberId()).orElseThrow();
        return fineCalculator.fineCents(loan, member.getType(), returnDate);
    }

    /**
     * Returns every book the member currently has (e.g. when a membership is closed).
     *
     * @return total fine owed, in cents
     */
    public long returnAll(String memberId, LocalDate returnDate) {
        long total = 0;
        for (Loan loan : activeLoansByMember.getOrDefault(memberId, new ArrayList<>())) {
            total += returnBook(loan.getId(), returnDate);
        }
        return total;
    }

    /** Snapshot of a member's active loans. */
    public List<Loan> activeLoans(String memberId) {
        return List.copyOf(activeLoansByMember.getOrDefault(memberId, List.of()));
    }

    /** Unreturned loans past their due date, most overdue first. */
    public List<Loan> overdueLoans(LocalDate today) {
        return loans.findAll().stream()
                .filter(l -> !l.isReturned() && l.daysOverdue(today) > 0)
                .sorted(Comparator.comparing(Loan::getDueDate).reversed())
                .toList();
    }

    public long currentFine(String loanId, LocalDate asOf) {
        Loan loan = loans.findById(loanId)
                .orElseThrow(() -> new LoanNotAllowedException("Unknown loan: " + loanId));
        Member member = members.findById(loan.getMemberId()).orElseThrow();
        return fineCalculator.fineCents(loan, member.getType(), asOf);
    }
}
