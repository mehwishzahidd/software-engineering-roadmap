package com.example.library.policy;

import java.time.LocalDate;

/**
 * Lending rules. Values are in days and cents so we never use floating point for money.
 */
public final class LoanPolicy {

    /** 14-day loans, max 3 active loans, 25 cents/day, fine capped at 10.00. */
    public static final LoanPolicy DEFAULT = new LoanPolicy(14, 3, 25, 1_000);

    private final int loanPeriodDays;
    private final int maxActiveLoans;
    private final long dailyFineCents;
    private final long maxFineCents;

    public LoanPolicy(int loanPeriodDays, int maxActiveLoans, long dailyFineCents, long maxFineCents) {
        if (loanPeriodDays <= 0 || maxActiveLoans <= 0 || dailyFineCents < 0 || maxFineCents < 0) {
            throw new IllegalArgumentException("invalid policy values");
        }
        this.loanPeriodDays = loanPeriodDays;
        this.maxActiveLoans = maxActiveLoans;
        this.dailyFineCents = dailyFineCents;
        this.maxFineCents = maxFineCents;
    }

    /** The date a book borrowed on {@code loanDate} must be back. */
    public LocalDate dueDateFor(LocalDate loanDate) {
        // the loan day itself counts as the first day of the loan period
        return loanDate.plusDays(loanPeriodDays - 1);
    }

    /** Whether a member who currently has {@code activeLoans} books out may borrow another. */
    public boolean canBorrow(int activeLoans) {
        return activeLoans < maxActiveLoans;
    }

    public int loanPeriodDays() {
        return loanPeriodDays;
    }

    public int maxActiveLoans() {
        return maxActiveLoans;
    }

    public long dailyFineCents() {
        return dailyFineCents;
    }

    public long maxFineCents() {
        return maxFineCents;
    }
}
