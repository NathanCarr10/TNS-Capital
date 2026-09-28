package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Position;
import com.neueda.leap.model.PositionId;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.utils.PositionKeyFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
    public Optional<Position> findByAccountIdAndSymbol(Long accountId, String symbol) {
        String key = PositionKeyFactory.createKey(accountId, symbol);
        return Optional.ofNullable(storage.get(key));
    }

    @Override
    public <S extends Position> S save(S position) {
        Objects.requireNonNull(position, "Position cannot be null");
        String key = PositionKeyFactory.createKey(position.getAccountId(), position.getSymbol());
        storage.put(key, position);
        return position;
    }

    @Override
    public void deleteByAccountIdAndSymbol(Long accountId, String symbol) {
        String key = PositionKeyFactory.createKey(accountId, symbol);
        storage.remove(key);
    }

    @Override
    public List<Position> findByAccountId(Long accountId) {
        // Filters positions by accountId since keys are "accountId::symbol"
        return storage.values().stream()
                .filter(position -> position.getAccountId().equals(accountId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Position> findById(PositionId id) {
        if (id == null)
            return Optional.empty();
        return findByAccountIdAndSymbol(id.getAccountId(), id.getSymbol());
    }

    // Stub implementations for other JpaRepository methods
    @Override
    public <S extends Position> List<S> saveAll(Iterable<S> entities) {
        return new ArrayList<>();
    }

    @Override
    public void flush() {
    }

    @Override
    public <S extends Position> S saveAndFlush(S entity) {
        return entity;
    }

    @Override
    public <S extends Position> List<S> saveAllAndFlush(Iterable<S> entities) {
        return new ArrayList<>();
    }

    @Override
    public void deleteAllInBatch(Iterable<Position> entities) {
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<PositionId> ids) {
    }

    @Override
    public void deleteAllInBatch() {
    }

    @Override
    public Position getReferenceById(PositionId id) {
        return null;
    }

    @Override
    public Position getById(PositionId id) {
        return null;
    }

    @Override
    public Position getOne(PositionId id) {
        return null;
    }

    @Override
    public <S extends Position> List<S> findAll(org.springframework.data.domain.Example<S> example) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Position> List<S> findAll(org.springframework.data.domain.Example<S> example,
            org.springframework.data.domain.Sort sort) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Position> Optional<S> findOne(org.springframework.data.domain.Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends Position> long count(org.springframework.data.domain.Example<S> example) {
        return 0;
    }

    @Override
    public <S extends Position> boolean exists(org.springframework.data.domain.Example<S> example) {
        return false;
    }

    @Override
    public <S extends Position, R> R findBy(org.springframework.data.domain.Example<S> example,
            java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public List<Position> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Position> findAll(org.springframework.data.domain.Sort sort) {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Position> findAllById(Iterable<PositionId> ids) {
        return new ArrayList<>();
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public void deleteById(PositionId id) {
        if (id != null)
            deleteByAccountIdAndSymbol(id.getAccountId(), id.getSymbol());
    }

    @Override
    public void delete(Position entity) {
    }

    @Override
    public void deleteAllById(Iterable<? extends PositionId> ids) {
    }

    @Override
    public void deleteAll(Iterable<? extends Position> entities) {
    }

    @Override
    public void deleteAll() {
    }

    @Override
    public boolean existsById(PositionId id) {
        return id != null && findById(id).isPresent();
    }

    @Override
    public <S extends Position> org.springframework.data.domain.Page<S> findAll(
            org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) {
        return new org.springframework.data.domain.PageImpl<>(new ArrayList<>());
    }

    @Override
    public org.springframework.data.domain.Page<Position> findAll(org.springframework.data.domain.Pageable pageable) {
        return new org.springframework.data.domain.PageImpl<>(new ArrayList<>(storage.values()));
    }
}
