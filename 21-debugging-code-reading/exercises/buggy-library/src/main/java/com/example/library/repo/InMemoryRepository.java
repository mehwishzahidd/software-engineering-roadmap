package com.example.library.repo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Minimal insertion-ordered repository. Stands in for a database table in this exercise.
 *
 * @param <ID> identifier type
 * @param <T>  entity type
 */
public class InMemoryRepository<ID, T> {

    private final Map<ID, T> store = new LinkedHashMap<>();
    private final Function<T, ID> idOf;

    public InMemoryRepository(Function<T, ID> idOf) {
        this.idOf = Objects.requireNonNull(idOf);
    }

    /** Inserts or replaces the entity with the same id. */
    public T save(T entity) {
        store.put(idOf.apply(entity), entity);
        return entity;
    }

    public Optional<T> findById(ID id) {
        return Optional.ofNullable(store.get(id));
    }

    /** Snapshot of all entities in insertion order. */
    public List<T> findAll() {
        return new ArrayList<>(store.values());
    }

    public boolean deleteById(ID id) {
        return store.remove(id) != null;
    }

    public int count() {
        return store.size();
    }
}
