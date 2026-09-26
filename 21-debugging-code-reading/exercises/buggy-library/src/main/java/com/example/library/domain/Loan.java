package com.example.library.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One checkout of one book by one member.
 */
public final class Loan {

    private final String id;
    private final Book book;
    private final String memberId;
    private final LocalDate loanDate;
    private final LocalDate dueDate;
    private LocalDate returnDate; // null while the book is still out

    public Loan(String id, Book book, String memberId, LocalDate loanDate, LocalDate dueDate) {
        this.id = Objects.requireNonNull(id);
        this.book = Objects.requireNonNull(book);
        this.memberId = Objects.requireNonNull(memberId);
        this.loanDate = Objects.requireNonNull(loanDate);
        this.dueDate = Objects.requireNonNull(dueDate);
        if (dueDate.isBefore(loanDate)) {
            throw new IllegalArgumentException("dueDate before loanDate");
        }
    }

    public String getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public String getMemberId() {
        return memberId;
    }

    public LocalDate getLoanDate() {
        return loanDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public boolean isReturned() {
        return returnDate != null;
    }

    public void markReturned(LocalDate date) {
        if (isReturned()) {
            throw new IllegalStateException("Loan " + id + " already returned");
        }
        this.returnDate = Objects.requireNonNull(date);
    }

    /**
     * Whole days past the due date, measured at the return date if returned,
     * otherwise at {@code asOf}. Never negative.
     */
    public long daysOverdue(LocalDate asOf) {
        LocalDate end = isReturned() ? returnDate : asOf;
        long days = ChronoUnit.DAYS.between(dueDate, end);
        return Math.max(0, days);
    }

    @Override
    public String toString() {
        return "Loan{id='" + id + "', isbn='" + book.getIsbn() + "', member='" + memberId
                + "', due=" + dueDate + ", returned=" + returnDate + "}";
    }
}
