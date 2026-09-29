package com.neueda.leap.repositories;

import com.neueda.leap.model.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for OrderHistory persistence.
 * 
 * Provides CRUD operations and custom query methods for order history records.
 */
@Repository
public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {
    /**
     * Finds a history record by original order ID.
     *
     * @param orderId the original order ID
     * @return Optional containing the history record if found
     */
    Optional<OrderHistory> findByOrderId(UUID orderId);

    /**
     * Finds all history records for a given account.
     *
     * @param accountId the account ID
     * @return list of order history records for the account
     */
    List<OrderHistory> findByAccountId(Long accountId);
}
