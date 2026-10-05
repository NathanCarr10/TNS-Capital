package com.neueda.leap.dtos;

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
        Instant createdOn) {
}
