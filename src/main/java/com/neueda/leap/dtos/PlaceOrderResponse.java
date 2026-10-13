package com.neueda.leap.dtos;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Response to POST /api/v1/orders (202 Accepted): the order is stored as NEW
 * and queued; poll GET /api/v1/orders/{orderId} for its outcome. The price is
 * the order's limit price: the one requested, or the live price if the
 * request had none.
 */
public record PlaceOrderResponse(
        @Schema(requiredMode = REQUIRED) UUID orderId,
        @Schema(requiredMode = REQUIRED) String status,
        @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal price,
        @Schema(requiredMode = REQUIRED) String message) {

    public static PlaceOrderResponse accepted(UUID orderId, BigDecimal price) {
        return new PlaceOrderResponse(orderId, "ACCEPTED", price,
                "Order accepted for processing. Poll GET /api/v1/orders/{orderId} to track status.");
    }
}
