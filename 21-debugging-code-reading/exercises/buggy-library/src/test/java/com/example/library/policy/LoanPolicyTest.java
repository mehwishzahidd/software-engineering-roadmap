package com.example.library.policy;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanPolicyTest {

    @Test
    void dueDateIsLoanPeriodDaysAfterLoanDate() {
        // Spec (README): a 14-day loan taken on 1 March is due back on 15 March.
        LocalDate due = LoanPolicy.DEFAULT.dueDateFor(LocalDate.of(2024, 3, 1));
        assertEquals(LocalDate.of(2024, 3, 15), due);
    }

    @Test
    void canBorrowBelowLimitButNotAtLimit() {
        LoanPolicy policy = new LoanPolicy(14, 3, 25, 1000);
        assertTrue(policy.canBorrow(2));
        assertFalse(policy.canBorrow(3));
    }

    @Test
    void rejectsNonPositiveLoanPeriod() {
        assertThrows(IllegalArgumentException.class, () -> new LoanPolicy(0, 3, 25, 1000));
    }
}
