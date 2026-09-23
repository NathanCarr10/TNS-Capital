package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery;

/**
 * In-memory implementation of OrderRepository for testing.
 * 
 * Wraps a Map<String, Order> for storage (keyed by idempotency key).
 */
public class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> storage;

    public InMemoryOrderRepository(Map<String, Order> storage) {
        this.storage = Objects.requireNonNull(storage, "Storage map cannot be null");
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(storage.get(idempotencyKey));
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return storage.values().stream()
                .filter(order -> order.getId().equals(id))
                .findFirst();
    }

    @Override
    public <S extends Order> S save(S order) {
        Objects.requireNonNull(order, "Order cannot be null");
        storage.put(order.getIdempotencyKey(), order);
        return order;
    }

    @Override
    public void deleteById(UUID id) {
        findById(id).ifPresent(this::delete);
    }

    @Override
    public void delete(Order entity) {
        if (entity != null && entity.getIdempotencyKey() != null) {
            storage.remove(entity.getIdempotencyKey());
        }
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public boolean existsById(UUID id) {
        return findById(id).isPresent();
    }

    // Stub implementations for JpaRepository methods
    @Override
    public List<Order> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Order> findAllById(Iterable<UUID> ids) {
        List<Order> result = new ArrayList<>();
        for (UUID id : ids) {
            findById(id).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public <S extends Order> List<S> saveAll(Iterable<S> entities) {
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
    public <S extends Order> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends Order> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<Order> entities) {
        for (Order entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<UUID> ids) {
        for (UUID id : ids) {
            deleteById(id);
        }
    }

    @Override
    public void deleteAllInBatch() {
        storage.clear();
    }

    @Override
    public Order getById(UUID id) {
        return findById(id).orElse(null);
    }

    @Override
    public Order getReferenceById(UUID id) {
        return findById(id).orElse(null);
    }

    @Override
    @Deprecated(since = "3.0")
    public Order getOne(UUID id) {
        return findById(id).orElse(null);
    }

    @Override
    public <S extends Order> List<S> findAll(Example<S> example) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Order> List<S> findAll(Example<S> example, Sort sort) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Order> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends Order> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends Order> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public <S extends Order, R> R findBy(Example<S> example, java.util.function.Function<FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public void deleteAll(Iterable<? extends Order> entities) {
        for (Order entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        storage.clear();
    }

    @Override
    public void deleteAllById(Iterable<? extends UUID> ids) {
        for (UUID id : ids) {
            deleteById(id);
        }
    }

    @Override
    public List<Order> findAll(Sort sort) {
        return new ArrayList<>(storage.values());
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return null;
    }

    @Override
    public <S extends Order> Page<S> findAll(Example<S> example, Pageable pageable) {
        return null;
    }
}
