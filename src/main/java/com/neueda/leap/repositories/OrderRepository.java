package com.neueda.leap.repositories;

import com.neueda.leap.model.Order;
import java.util.Optional;

/**
 * Repository abstraction for Order persistence.
 * 
 * Orders are immutable after creation; status changes are persisted atomically.
 * Decouples business logic from storage implementation.
 */
public interface OrderRepository {
    /**
     * Finds an order by idempotency key.
     *
     * @param idempotencyKey the idempotency key for the order
     * @return Optional containing the order if found, empty otherwise
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /**
     * Saves or updates an order.
     *
     * @param order the order to persist
     * @throws IllegalArgumentException if order is null
     */
    void save(Order order);
}
