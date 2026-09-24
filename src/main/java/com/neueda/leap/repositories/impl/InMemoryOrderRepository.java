package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory implementation of OrderRepository.
 * 
 * Wraps a Map<String, Order> for storage (keyed by idempotency key).
 * Suitable for testing and non-persistent use; replace with Spring Data JPA
 * implementation for production.
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
    public <S extends Order> S save(S order) {
        Objects.requireNonNull(order, "Order cannot be null");
        storage.put(order.getIdempotencyKey(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        Objects.requireNonNull(id, "Order ID cannot be null");
        return storage.values().stream()
                .filter(order -> order.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<Order> findAll() {
        return new ArrayList<>(storage.values());
    }

    // Stub implementations for other JpaRepository methods
    @Override
    public <S extends Order> List<S> saveAll(Iterable<S> entities) {
        return new ArrayList<>();
    }

    @Override
    public void flush() {
    }

    @Override
    public <S extends Order> S saveAndFlush(S entity) {
        return entity;
    }

    @Override
    public <S extends Order> List<S> saveAllAndFlush(Iterable<S> entities) {
        return new ArrayList<>();
    }

    @Override
    public void deleteAllInBatch(Iterable<Order> entities) {
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<UUID> ids) {
    }

    @Override
    public void deleteAllInBatch() {
    }

    @Override
    public Order getReferenceById(UUID id) {
        return null;
    }

    @Override
    public Order getById(UUID id) {
        return null;
    }

    @Override
    public Order getOne(UUID id) {
        return null;
    }

    @Override
    public <S extends Order> List<S> findAll(org.springframework.data.domain.Example<S> example) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Order> List<S> findAll(org.springframework.data.domain.Example<S> example,
            org.springframework.data.domain.Sort sort) {
        return new ArrayList<>();
    }

    @Override
    public <S extends Order> Optional<S> findOne(org.springframework.data.domain.Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends Order> long count(org.springframework.data.domain.Example<S> example) {
        return 0;
    }

    @Override
    public <S extends Order> boolean exists(org.springframework.data.domain.Example<S> example) {
        return false;
    }

    @Override
    public <S extends Order, R> R findBy(org.springframework.data.domain.Example<S> example,
            java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
        return null;
    }

    @Override
    public List<Order> findAll(org.springframework.data.domain.Sort sort) {
        return new ArrayList<>(storage.values());
    }

    @Override
    public List<Order> findAllById(Iterable<UUID> ids) {
        return new ArrayList<>();
    }

    @Override
    public List<Order> findByAccountId(Long accountId) {
        return storage.values().stream()
                .filter(order -> order.getAccountId().equals(accountId))
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public void deleteById(UUID id) {
        storage.values().removeIf(order -> order.getId().equals(id));
    }

    @Override
    public void delete(Order entity) {
    }

    @Override
    public void deleteAllById(Iterable<? extends UUID> ids) {
    }

    @Override
    public void deleteAll(Iterable<? extends Order> entities) {
    }

    @Override
    public void deleteAll() {
    }

    @Override
    public boolean existsById(UUID id) {
        return findById(id).isPresent();
    }

    @Override
    public <S extends Order> org.springframework.data.domain.Page<S> findAll(
            org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) {
        return new org.springframework.data.domain.PageImpl<>(new ArrayList<>());
    }

    @Override
    public org.springframework.data.domain.Page<Order> findAll(org.springframework.data.domain.Pageable pageable) {
        return new org.springframework.data.domain.PageImpl<>(new ArrayList<>(storage.values()));
    }
}
