package com.neueda.leap.repositories;

import com.neueda.leap.model.TradeEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TradeEventRecordRepository extends JpaRepository<TradeEventRecord, String> {
    /**
     * @return the order's lifecycle events, oldest first
     */
    List<TradeEventRecord> findByOrderIdOrderByReceivedOnAsc(UUID orderId);
}
