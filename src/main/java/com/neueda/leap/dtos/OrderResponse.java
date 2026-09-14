package com.neueda.leap.dtos;

import com.neueda.leap.domain.enums.OrderSide;
import com.neueda.leap.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderResponse(
                Long id,
                Long accountId,
                String symbol,
                OrderSide side,
                Integer quantity,
                BigDecimal price,
                OrderStatus status,
                LocalDateTime createdOn) {
}
