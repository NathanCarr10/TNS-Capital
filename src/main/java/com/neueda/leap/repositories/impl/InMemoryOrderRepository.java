package com.neueda.leap.repositories.impl;

import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.OrderRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
}
