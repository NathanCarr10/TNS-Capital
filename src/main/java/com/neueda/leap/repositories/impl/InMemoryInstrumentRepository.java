package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;
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
 * In-memory implementation of InstrumentRepository for testing.
 * 
 * Wraps a Map<String, Instrument> for storage (keyed by symbol).
 */
public class InMemoryInstrumentRepository implements InstrumentRepository {
    private final Map<String, Instrument> storage;

    public InMemoryInstrumentRepository(Map<String, Instrument> storage) {
        this.storage = Objects.requireNonNull(storage, "Storage map cannot be null");
    }

    @Override
    public Optional<Instrument> findBySymbol(String symbol) {
        return Optional.ofNullable(storage.get(symbol));
    }

    @Override
    public Optional<Instrument> findById(Long id) {
        // Symbol is now the primary key; this method is not applicable
        return Optional.empty();
    }

    @Override
    public <S extends Instrument> S save(S instrument) {
        Objects.requireNonNull(instrument, "Instrument cannot be null");
        storage.put(instrument.getSymbol(), instrument);
        return instrument;
    }

    @Override
    public void deleteById(Long id) {
        findById(id).ifPresent(this::delete);
    }

    @Override
    public void delete(Instrument entity) {
        if (entity != null && entity.getSymbol() != null) {
            storage.remove(entity.getSymbol());
        }
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public boolean existsById(Long id) {
        return findById(id).isPresent();
    }

    // Stub implementations for JpaRepository methods
    @Override
    public List<Instrument> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Instrument> findAllById(Iterable<Long> ids) {
        List<Instrument> result = new ArrayList<>();
        for (Long id : ids) {
            findById(id).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public <S extends Instrument> List<S> saveAll(Iterable<S> entities) {
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
    public <S extends Instrument> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends Instrument> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<Instrument> entities) {
        for (Instrument entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<Long> ids) {
        for (Long id : ids) {
            deleteById(id);
        }
    }

    @Override
    public void deleteAllInBatch() {
        storage.clear();
    }

    @Override
    public Instrument getById(Long id) {
        return findById(id).orElse(null);
    }

    @Override
    public Instrument getReferenceById(Long id) {
        return findById(id).orElse(null);
    }

    @Override
    @Deprecated(since = "3.0")
    public Instrument getOne(Long id) {
        return findById(id).orElse(null);
    }

    @Override
    public <S extends Instrument> List<S> findAll(Example<S> example) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Instrument> List<S> findAll(Example<S> example, Sort sort) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Instrument> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends Instrument> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends Instrument> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends Instrument, R> R findBy(Example<S> example, java.util.function.Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public void deleteAll(Iterable<? extends Instrument> entities) {
        for (Instrument entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        for (Long id : ids) {
            deleteById(id);
        }
    }

    @Override
    public List<Instrument> findAll(Sort sort) {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Page<Instrument> findAll(Pageable pageable) {
        return null;
    }

    @Override
    public <S extends Instrument> Page<S> findAll(Example<S> example, Pageable pageable) {
        return null;
    }
}
