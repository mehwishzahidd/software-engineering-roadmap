package com.example.library.domain;

import java.util.Objects;

/**
 * A catalogue entry. Two books are the same book when they have the same ISBN
 * (title/author typos get corrected over time, the ISBN does not change).
 */
public final class Book {

    private final String isbn;
    private final String title;
    private final String author;
    private final int year;

    public Book(String isbn, String title, String author, int year) {
        this.isbn = requireText(isbn, "isbn");
        this.title = requireText(title, "title");
        this.author = requireText(author, "author");
        this.year = year;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getYear() {
        return year;
    }

    @Override
    public String toString() {
        return "Book{isbn='" + isbn + "', title='" + title + "', author='" + author + "', year=" + year + "}";
    }
}
