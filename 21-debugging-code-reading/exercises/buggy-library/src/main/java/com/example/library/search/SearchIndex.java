package com.example.library.search;

import com.example.library.domain.Book;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Inverted index: word -> books whose title or author contains that word.
 * Search is intended to be case-insensitive.
 */
public class SearchIndex {

    private final Map<String, Set<Book>> index = new HashMap<>();

    public void index(Book book) {
        for (String token : tokens(book.getTitle() + " " + book.getAuthor())) {
            index.computeIfAbsent(token, k -> new HashSet<>()).add(book);
        }
    }

    public void remove(Book book) {
        for (String token : tokens(book.getTitle() + " " + book.getAuthor())) {
            Set<Book> books = index.get(token);
            if (books != null) {
                books.remove(book);
                if (books.isEmpty()) {
                    index.remove(token);
                }
            }
        }
    }

    /**
     * Books matching a single keyword.
     *
     * @param keyword one word, any case
     * @return matching books (empty if none or keyword is blank)
     */
    public Set<Book> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Set.of();
        }
        return index.getOrDefault(keyword.trim(), Set.of());
    }

    /** Number of distinct words in the index. */
    public int size() {
        return index.size();
    }

    static List<String> tokens(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(t -> !t.isBlank())
                .toList();
    }
}
