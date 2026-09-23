package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.OrderRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public void save(Order order) {
        Objects.requireNonNull(order, "Order cannot be null");
        storage.put(order.getIdempotencyKey(), order);
    }

    @Override
    public List<Order> findByAccountId(Long accountId) {
        // Filters orders by accountId since they store account context
        return storage.values().stream()
                .filter(order -> order.getAccountId().equals(accountId))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Order> findById(UUID id) {
        // Searches storage by UUID since orders are keyed by idempotency key; UUID lookup requires full scan
        return storage.values().stream()
                .filter(order -> order.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<Order> findAll() {
        // Returns all orders as a new list; decouples internal storage from external API consumers
        return new ArrayList<>(storage.values());
    }
}
