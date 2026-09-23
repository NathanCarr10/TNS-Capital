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
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;

/**
 * In-memory implementation of PositionRepository for testing.
 * 
 * Wraps a Map<String, Position> for storage (keyed by accountId::symbol).
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
    public Optional<Position> findById(PositionId id) {
        if (id == null) {
            return Optional.empty();
        }
        return findByAccountIdAndSymbol(id.getAccountId(), id.getSymbol());
    }

    @Override
    public <S extends Position> S save(S entity) {
        Objects.requireNonNull(entity, "Position cannot be null");
        String key = PositionKeyFactory.createKey(entity.getAccountId(), entity.getSymbol());
        storage.put(key, entity);
        return entity;
    }

    @Override
    public void deleteById(PositionId id) {
        if (id != null) {
            deleteByAccountIdAndSymbol(id.getAccountId(), id.getSymbol());
        }
    }

    @Override
    public void delete(Position entity) {
        if (entity != null) {
            deleteByAccountIdAndSymbol(entity.getAccountId(), entity.getSymbol());
        }
    }

    @Override
    public void deleteByAccountIdAndSymbol(Long accountId, String symbol) {
        String key = PositionKeyFactory.createKey(accountId, symbol);
        storage.remove(key);
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public boolean existsById(PositionId id) {
        if (id == null) {
            return false;
        }
        return findById(id).isPresent();
    }

    // Stub implementations for JpaRepository methods
    @Override
    public List<Position> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Position> findAllById(Iterable<PositionId> ids) {
        List<Position> result = new ArrayList<>();
        for (PositionId id : ids) {
            findById(id).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public <S extends Position> List<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for (S entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public void flush() {
    }

    @Override
    public <S extends Position> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends Position> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<Position> entities) {
        for (Position entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<PositionId> ids) {
        for (PositionId id : ids) {
            deleteById(id);
        }
    }

    @Override
    public void deleteAllInBatch() {
        storage.clear();
    }

    @Override
    public Position getById(PositionId id) {
        return findById(id).orElse(null);
    }

    @Override
    public Position getReferenceById(PositionId id) {
        return findById(id).orElse(null);
    }

    @Override
    @Deprecated(since = "3.0")
    public Position getOne(PositionId id) {
        return findById(id).orElse(null);
    }

    @Override
    public <S extends Position> List<S> findAll(Example<S> example) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Position> List<S> findAll(Example<S> example, Sort sort) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Position> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends Position> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends Position> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends Position, R> R findBy(Example<S> example, java.util.function.Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public void deleteAll(Iterable<? extends Position> entities) {
        for (Position entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }

    @Override
    public void deleteAllById(Iterable<? extends PositionId> ids) {
        for (PositionId id : ids) {
            deleteById(id);
        }
    }

    @Override
    public List<Position> findAll(Sort sort) {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Page<Position> findAll(Pageable pageable) {
        return null;
    }

    @Override
    public <S extends Position> Page<S> findAll(Example<S> example, Pageable pageable) {
        return null;
    }
}
