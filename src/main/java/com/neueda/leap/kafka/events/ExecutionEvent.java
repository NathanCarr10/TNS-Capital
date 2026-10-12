package com.neueda.leap.kafka.events;

import com.neueda.leap.enums.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A fill from the Broker Simulator, read from the executions topic. The
 * broker has its own copy of this record; the two share the JSON contract,
 * not code (see broker-simulator/README.md).
 */
public record ExecutionEvent(
        UUID executionId,
        UUID orderId,
        Long accountId,
        String symbol,
        OrderSide side,
        Integer quantity,
        BigDecimal price,
        String venue,
        Instant executedOn) {
}
