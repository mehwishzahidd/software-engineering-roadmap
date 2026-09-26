package com.example.library.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookTest {

    @Test
    void rejectsBlankIsbn() {
        assertThrows(IllegalArgumentException.class, () -> new Book("  ", "Title", "Author", 2020));
    }

    @Test
    void rejectsNullTitle() {
        assertThrows(NullPointerException.class, () -> new Book("123", null, "Author", 2020));
    }

    @Test
    void trimsFields() {
        Book book = new Book(" 978-1 ", " Effective Java ", "Joshua Bloch", 2018);
        assertEquals("978-1", book.getIsbn());
        assertEquals("Effective Java", book.getTitle());
    }

    @Test
    void booksWithSameIsbnAreEqual() {
        Book a = new Book("978-0134685991", "Effective Java", "Joshua Bloch", 2018);
        Book b = new Book("978-0134685991", "Effective Java (3rd ed.)", "Joshua Bloch", 2018);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void booksWithDifferentIsbnAreNotEqual() {
        Book a = new Book("111", "Same Title", "Same Author", 2000);
        Book b = new Book("222", "Same Title", "Same Author", 2000);
        assertNotEquals(a, b);
    }

    @Test
    void hashSetDeduplicatesEqualBooks() {
        List<Book> imported = List.of(
                new Book("978-0132350884", "Clean Code", "Robert Martin", 2008),
                new Book("978-0132350884", "Clean Code", "Robert Martin", 2008));
        Set<Book> unique = new HashSet<>(imported);
        assertEquals(1, unique.size());
        assertTrue(unique.contains(new Book("978-0132350884", "Clean Code", "Robert Martin", 2008)));
    }
}
