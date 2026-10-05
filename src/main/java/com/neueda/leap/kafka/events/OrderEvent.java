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
 *
 * idempotencyKey is the client's key from PlaceOrderRequest, already normalized.
 * Events captured before the field existed have no key; the processor then
 * falls back to the order ID.
 */
public record OrderEvent(
        UUID orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        Integer quantity,
        BigDecimal price,
        Instant createdOn,
        String idempotencyKey
) {
}
