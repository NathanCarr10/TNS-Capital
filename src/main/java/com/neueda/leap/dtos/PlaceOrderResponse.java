package com.neueda.leap.dtos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response to POST /api/v1/orders (202 Accepted): the order is stored as NEW
 * and queued; poll GET /api/v1/orders/{orderId} for its outcome. The price is
 * the order's limit price: the one requested, or the live price if the
 * request had none.
 */
public record PlaceOrderResponse(UUID orderId, String status, BigDecimal price, String message) {

    public static PlaceOrderResponse accepted(UUID orderId, BigDecimal price) {
        return new PlaceOrderResponse(orderId, "ACCEPTED", price,
                "Order accepted for processing. Poll GET /api/v1/orders/{orderId} to track status.");
    }
}
