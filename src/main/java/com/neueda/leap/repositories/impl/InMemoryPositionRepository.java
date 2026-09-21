package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Position;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.utils.PositionKeyFactory;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory implementation of PositionRepository.
 * 
 * Wraps a Map<String, Position> for storage (keyed by accountId::symbol).
 * Suitable for testing and non-persistent use; replace with Spring Data JPA
 * implementation for production.
 */
public class InMemoryPositionRepository implements PositionRepository {
    private final Map<String, Position> storage;

    public InMemoryPositionRepository(Map<String, Position> storage) {
        this.storage = Objects.requireNonNull(storage, "Storage map cannot be null");
    }

    @Override
    public Optional<Position> findPosition(Long accountId, String symbol) {
        String key = PositionKeyFactory.createKey(accountId, symbol);
        return Optional.ofNullable(storage.get(key));
    }

    @Override
    public void save(Position position) {
        Objects.requireNonNull(position, "Position cannot be null");
        String key = PositionKeyFactory.createKey(position.getAccountId(), position.getSymbol());
        storage.put(key, position);
    }

    @Override
    public void delete(Long accountId, String symbol) {
        String key = PositionKeyFactory.createKey(accountId, symbol);
        storage.remove(key);
    }
}
