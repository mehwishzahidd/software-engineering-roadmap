package com.example.library.policy;

import com.example.library.domain.Loan;
import com.example.library.domain.MemberType;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Computes overdue fines in cents.
 * Rule: dailyFine x daysOverdue, capped at maxFine, then the member-type discount is applied.
 */
public final class FineCalculator {

    private final LoanPolicy policy;

    public FineCalculator(LoanPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public long fineCents(Loan loan, MemberType memberType, LocalDate asOf) {
        long daysLate = loan.daysOverdue(asOf);
        if (daysLate == 0) {
            return 0;
        }
        long gross = Math.min(daysLate * policy.dailyFineCents(), policy.maxFineCents());
        int payablePercent = 100 - memberType.fineDiscountPercent();
        return gross * (payablePercent / 100);
    }
}
