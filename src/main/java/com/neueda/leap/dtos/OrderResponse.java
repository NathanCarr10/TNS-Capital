package com.neueda.leap.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        Long accountId,
        String symbol,
        OrderSide side,
        Integer quantity,
        BigDecimal price,
        OrderStatus status,
        Instant createdOn,
        // Only populated for REJECTED/CANCELLED orders; omitted from JSON otherwise
        @JsonInclude(JsonInclude.Include.NON_NULL) String statusReason) {

    public OrderResponse(UUID id, Long accountId, String symbol, OrderSide side, Integer quantity,
            BigDecimal price, OrderStatus status, Instant createdOn) {
        this(id, accountId, symbol, side, quantity, price, status, createdOn, null);
    }

    /**
     * Returns the reason to expose for an order; only REJECTED and CANCELLED
     * orders carry a status reason in API responses.
     */
    public static String statusReasonFor(OrderStatus status, String statusReason) {
        return status == OrderStatus.REJECTED || status == OrderStatus.CANCELLED ? statusReason : null;
    }
}
