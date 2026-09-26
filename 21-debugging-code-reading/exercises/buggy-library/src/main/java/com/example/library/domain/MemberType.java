package com.example.library.domain;

/**
 * Membership tiers. The discount is applied to overdue fines.
 */
public enum MemberType {
    STANDARD(0),
    STUDENT(50),
    STAFF(100);

    private final int fineDiscountPercent;

    MemberType(int fineDiscountPercent) {
        this.fineDiscountPercent = fineDiscountPercent;
    }

    /** Percentage (0..100) knocked off any overdue fine. */
    public int fineDiscountPercent() {
        return fineDiscountPercent;
    }
}
