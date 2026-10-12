package com.neueda.leap.broker;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A fill, published to the executions topic keyed by account. The order is
 * always filled in full, at {@code price}.
 */
public record ExecutionEvent(
        UUID executionId,
        UUID orderId,
        Long accountId,
        String symbol,
        Side side,
        Integer quantity,
        BigDecimal price,
        String venue,
        Instant executedOn) {
}
