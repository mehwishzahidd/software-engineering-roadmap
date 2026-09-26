package com.example.library.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoanTest {

    private final Book book = new Book("1", "Refactoring", "Martin Fowler", 2018);

    @Test
    void daysOverdueIsZeroBeforeDueDate() {
        Loan loan = new Loan("L1", book, "M1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 15));
        assertEquals(0, loan.daysOverdue(LocalDate.of(2024, 1, 10)));
        assertEquals(0, loan.daysOverdue(LocalDate.of(2024, 1, 15)));
    }

    @Test
    void daysOverdueUsesReturnDateOnceReturned() {
        Loan loan = new Loan("L1", book, "M1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 15));
        loan.markReturned(LocalDate.of(2024, 1, 18));
        assertEquals(3, loan.daysOverdue(LocalDate.of(2024, 6, 1)));
    }

    @Test
    void cannotReturnTwice() {
        Loan loan = new Loan("L1", book, "M1", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 15));
        loan.markReturned(LocalDate.of(2024, 1, 2));
        assertThrows(IllegalStateException.class, () -> loan.markReturned(LocalDate.of(2024, 1, 3)));
    }
}
