package com.neueda.leap.repositories;

import com.neueda.leap.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for Order persistence.
 * 
 * Provides CRUD operations and custom query methods.
 * JpaRepository automatically provides: findById, save, findAll, delete, etc.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    /**
     * Finds an order by idempotency key for duplicate detection.
     *
     * @param idempotencyKey the idempotency key for the order
     * @return Optional containing the order if found, empty otherwise
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByAccountId(Long accountId);
}
