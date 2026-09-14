// PlaceOrderRequest.java
package com.neueda.leap.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.neueda.leap.domain.enums.OrderSide;

import java.math.BigDecimal;

public record PlaceOrderRequest(
                @NotNull Long accountId,
                @NotNull String symbol,
                @NotNull OrderSide side,
                @NotNull @Positive Integer quantity,
                @NotNull @Positive BigDecimal price,
                @NotNull String idempotencyKey) {
}