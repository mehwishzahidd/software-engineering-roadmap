package com.example.library.service;

import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.domain.Member;
import com.example.library.domain.MemberType;
import com.example.library.policy.LoanPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryServiceTest {

    private static final LocalDate DAY_0 = LocalDate.of(2024, 3, 1);

    private LibraryService library;

    @BeforeEach
    void setUp() {
        library = new LibraryService(LoanPolicy.DEFAULT);
        library.addBook(new Book("B1", "Effective Java", "Joshua Bloch", 2018));
        library.addBook(new Book("B2", "Head First Java", "Kathy Sierra", 2005));
        library.addBook(new Book("B3", "Clean Code", "Robert Martin", 2008));
        library.addBook(new Book("B4", "Refactoring", "Martin Fowler", 2018));
        library.registerMember(new Member("M1", "Ada", "ada@example.com", MemberType.STANDARD));
        library.registerMember(new Member("M2", "Grace", "grace@example.com", MemberType.STUDENT));
    }

    // ------------------------------------------------------------------ passing behaviour

    @Test
    void checkoutRecordsActiveLoan() {
        Loan loan = library.checkout("M1", "B1", DAY_0);
        assertEquals("B1", loan.getBook().getIsbn());
        assertEquals(List.of(loan), library.activeLoans("M1"));
    }

    @Test
    void unknownMemberCannotBorrow() {
        assertThrows(LoanNotAllowedException.class, () -> library.checkout("nobody", "B1", DAY_0));
    }

    @Test
    void bookAlreadyOnLoanCannotBeBorrowedAgain() {
        library.checkout("M1", "B1", DAY_0);
        assertThrows(LoanNotAllowedException.class, () -> library.checkout("M2", "B1", DAY_0));
    }

    @Test
    void loanLimitIsEnforced() {
        library.checkout("M1", "B1", DAY_0);
        library.checkout("M1", "B2", DAY_0);
        library.checkout("M1", "B3", DAY_0);
        assertThrows(LoanNotAllowedException.class, () -> library.checkout("M1", "B4", DAY_0));
    }

    @Test
    void returnOnTimeHasNoFineAndFreesTheBook() {
        Loan loan = library.checkout("M1", "B1", DAY_0);
        assertEquals(0, library.returnBook(loan.getId(), DAY_0.plusDays(5)));
        assertTrue(library.activeLoans("M1").isEmpty());
        assertDoesNotThrow(() -> library.checkout("M2", "B1", DAY_0.plusDays(6)));
    }

    @Test
    void returningTwiceIsRejected() {
        Loan loan = library.checkout("M1", "B1", DAY_0);
        library.returnBook(loan.getId(), DAY_0.plusDays(1));
        assertThrows(LoanNotAllowedException.class, () -> library.returnBook(loan.getId(), DAY_0.plusDays(2)));
    }

    @Test
    void findMemberByEmailIgnoresCase() {
        Optional<Member> found = library.findMemberByEmail("ADA@example.com");
        assertEquals("M1", found.orElseThrow().getId());
    }

    // ------------------------------------------------------------------ specified behaviour

    @Test
    void lateReturnIsFinedPerDayAfterDueDate() {
        // 14-day loan from 1 March -> due 15 March. Returned 18 March = 3 days late = 75 cents.
        Loan loan = library.checkout("M1", "B1", DAY_0);
        assertEquals(75, library.returnBook(loan.getId(), LocalDate.of(2024, 3, 18)));
    }

    @Test
    void returnAllReturnsEveryLoan_threeLoans() {
        library.checkout("M1", "B1", DAY_0);
        library.checkout("M1", "B2", DAY_0);
        library.checkout("M1", "B3", DAY_0);
        assertDoesNotThrow(() -> library.returnAll("M1", DAY_0.plusDays(2)));
        assertTrue(library.activeLoans("M1").isEmpty());
    }

    @Test
    void returnAllReturnsEveryLoan_twoLoans() {
        library.checkout("M1", "B1", DAY_0);
        library.checkout("M1", "B2", DAY_0);
        library.returnAll("M1", DAY_0.plusDays(2));
        assertTrue(library.activeLoans("M1").isEmpty(), "still active: " + library.activeLoans("M1"));
    }

    @Test
    void findMemberByEmailSkipsMembersWithoutEmail() {
        LibraryService lib = new LibraryService(LoanPolicy.DEFAULT);
        lib.registerMember(new Member("W1", "Walk-in", null, MemberType.STANDARD));
        lib.registerMember(new Member("M9", "Barbara", "barbara@example.com", MemberType.STAFF));
        assertEquals("M9", lib.findMemberByEmail("barbara@example.com").orElseThrow().getId());
    }

    @Test
    void overdueLoansAreSortedMostOverdueFirst() {
        Loan older = library.checkout("M1", "B1", DAY_0);             // due mid-March
        Loan newer = library.checkout("M2", "B2", DAY_0.plusDays(7)); // due a week later
        Loan oldest = library.checkout("M1", "B3", DAY_0.minusDays(3));
        List<Loan> overdue = library.overdueLoans(LocalDate.of(2024, 5, 1));
        assertEquals(List.of(oldest.getId(), older.getId(), newer.getId()),
                overdue.stream().map(Loan::getId).toList());
    }
}
