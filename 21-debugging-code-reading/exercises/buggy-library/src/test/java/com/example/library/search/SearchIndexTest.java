package com.example.library.search;

import com.example.library.domain.Book;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIndexTest {

    private final Book effectiveJava = new Book("978-0134685991", "Effective Java", "Joshua Bloch", 2018);
    private final Book headFirst = new Book("978-0596009205", "Head First Java", "Kathy Sierra", 2005);
    private final Book cleanCode = new Book("978-0132350884", "Clean Code", "Robert Martin", 2008);

    private SearchIndex indexWithThreeBooks() {
        SearchIndex index = new SearchIndex();
        index.index(effectiveJava);
        index.index(headFirst);
        index.index(cleanCode);
        return index;
    }

    @Test
    void findsBooksByLowercaseTitleWord() {
        assertEquals(2, indexWithThreeBooks().search("java").size());
    }

    @Test
    void findsBooksByAuthorWord() {
        Set<Book> result = indexWithThreeBooks().search("martin");
        assertEquals(1, result.size());
        assertTrue(result.contains(cleanCode));
    }

    @Test
    void blankKeywordReturnsNothing() {
        assertTrue(indexWithThreeBooks().search("   ").isEmpty());
        assertTrue(indexWithThreeBooks().search(null).isEmpty());
    }

    @Test
    void removeDropsBookFromResults() {
        SearchIndex index = indexWithThreeBooks();
        index.remove(headFirst);
        assertEquals(1, index.search("java").size());
    }

    @Test
    void searchIsCaseInsensitive() {
        assertEquals(2, indexWithThreeBooks().search("JaVa").size());
    }

    @Test
    void reindexingAnEqualBookDoesNotDuplicateResults() {
        SearchIndex index = new SearchIndex();
        index.index(effectiveJava);
        // same ISBN arriving again, e.g. from a nightly catalogue import
        index.index(new Book("978-0134685991", "Effective Java", "Joshua Bloch", 2018));
        assertEquals(1, index.search("effective").size());
    }

    @Test
    void callersCannotCorruptTheIndexThroughSearchResults() {
        SearchIndex index = indexWithThreeBooks();
        Set<Book> result = index.search("java");
        try {
            result.clear();
        } catch (UnsupportedOperationException expected) {
            // an unmodifiable result is an acceptable design
        }
        assertEquals(2, index.search("java").size());
    }
}
