package com.example.library.policy;

import com.example.library.domain.Book;
import com.example.library.domain.Loan;
import com.example.library.domain.MemberType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FineCalculatorTest {

    private static final LocalDate LOANED = LocalDate.of(2024, 5, 1);
    private static final LocalDate DUE = LocalDate.of(2024, 5, 15);

    private final FineCalculator calculator = new FineCalculator(new LoanPolicy(14, 3, 25, 1000));
    private final Book book = new Book("1", "The Pragmatic Programmer", "Hunt Thomas", 2019);

    private Loan loan() {
        return new Loan("L1", book, "M1", LOANED, DUE);
    }

    @Test
    void noFineWhenReturnedOnDueDate() {
        assertEquals(0, calculator.fineCents(loan(), MemberType.STANDARD, DUE));
    }

    @Test
    void standardMemberPaysDailyRate() {
        assertEquals(75, calculator.fineCents(loan(), MemberType.STANDARD, DUE.plusDays(3)));
    }

    @Test
    void fineIsCappedAtMaximum() {
        assertEquals(1000, calculator.fineCents(loan(), MemberType.STANDARD, DUE.plusDays(365)));
    }

    @Test
    void staffPayNoFines() {
        assertEquals(0, calculator.fineCents(loan(), MemberType.STAFF, DUE.plusDays(10)));
    }

    @Test
    void studentDiscountIsApplied() {
        // 4 days late * 25 = 100 cents, students get 50% off
        assertEquals(50, calculator.fineCents(loan(), MemberType.STUDENT, DUE.plusDays(4)));
    }
}
