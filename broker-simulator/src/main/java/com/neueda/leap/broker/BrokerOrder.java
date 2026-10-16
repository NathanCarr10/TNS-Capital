package com.neueda.leap.broker;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An order sent to the broker: POST /api/v1/orders. The limit price is the
 * worst price the trader accepts. The market price, when the trading API has
 * a live one, is what the order fills at if the limit allows it; without it
 * the order fills at its limit (see SimulatedMarket).
 */
public record BrokerOrder(
        @NotNull UUID orderId,
        @NotNull Long accountId,
        @NotBlank String symbol,
        @NotNull Side side,
        @NotNull @Positive Integer quantity,
        @NotNull @DecimalMin(value = "0.01") BigDecimal limitPrice,
        @DecimalMin(value = "0.01") BigDecimal marketPrice) {

    /** An order with no market price, filled relative to its limit */
    public BrokerOrder(UUID orderId, Long accountId, String symbol, Side side, Integer quantity,
            BigDecimal limitPrice) {
        this(orderId, accountId, symbol, side, quantity, limitPrice, null);
    }
}
