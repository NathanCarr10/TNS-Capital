package com.neueda.leap.kafka.events;

import com.neueda.leap.enums.OrderSide;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Order event payload.
 * 
 * Represents an accepted order that should be processed.
 * Keyed by accountId so all orders for one account land on the same partition
 * and are processed in order.
 */
public record OrderEvent(
        UUID orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        Integer quantity,
        BigDecimal price,
        Instant createdOn,
        // The client's key; null in messages published before it was added
        String idempotencyKey
) {
    public OrderEvent(UUID orderId, Long accountId, String symbol, OrderSide side, Integer quantity,
            BigDecimal price, Instant createdOn) {
        this(orderId, accountId, symbol, side, quantity, price, createdOn, null);
    }
}
