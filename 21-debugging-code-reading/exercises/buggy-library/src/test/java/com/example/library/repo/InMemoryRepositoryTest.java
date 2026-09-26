package com.example.library.repo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRepositoryTest {

    record Thing(String id, String label) {
    }

    @Test
    void saveThenFindById() {
        InMemoryRepository<String, Thing> repo = new InMemoryRepository<>(Thing::id);
        repo.save(new Thing("a", "first"));
        assertEquals("first", repo.findById("a").orElseThrow().label());
        assertTrue(repo.findById("missing").isEmpty());
    }

    @Test
    void saveWithSameIdReplaces() {
        InMemoryRepository<String, Thing> repo = new InMemoryRepository<>(Thing::id);
        repo.save(new Thing("a", "v1"));
        repo.save(new Thing("a", "v2"));
        assertEquals(1, repo.count());
        assertEquals("v2", repo.findById("a").orElseThrow().label());
    }

    @Test
    void findAllKeepsInsertionOrderAndIsASnapshot() {
        InMemoryRepository<String, Thing> repo = new InMemoryRepository<>(Thing::id);
        repo.save(new Thing("b", "B"));
        repo.save(new Thing("a", "A"));
        List<Thing> all = repo.findAll();
        assertEquals(List.of("b", "a"), all.stream().map(Thing::id).toList());
        all.clear();
        assertEquals(2, repo.count());
    }

    @Test
    void deleteById() {
        InMemoryRepository<String, Thing> repo = new InMemoryRepository<>(Thing::id);
        repo.save(new Thing("a", "A"));
        assertTrue(repo.deleteById("a"));
        assertFalse(repo.deleteById("a"));
    }
}
