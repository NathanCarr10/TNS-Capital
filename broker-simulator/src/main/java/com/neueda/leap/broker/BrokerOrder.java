package com.neueda.leap.broker;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An order sent to the broker: POST /api/v1/orders. The limit price is the
 * worst price the trader accepts.
 */
public record BrokerOrder(
        @NotNull UUID orderId,
        @NotNull Long accountId,
        @NotBlank String symbol,
        @NotNull Side side,
        @NotNull @Positive Integer quantity,
        @NotNull @DecimalMin(value = "0.01") BigDecimal limitPrice) {
}
