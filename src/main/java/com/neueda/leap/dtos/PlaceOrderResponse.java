package com.neueda.leap.dtos;

import java.util.UUID;

/**
 * Response to POST /api/v1/orders (202 Accepted): the order is stored as NEW
 * and queued; poll GET /api/v1/orders/{orderId} for its outcome.
 */
public record PlaceOrderResponse(UUID orderId, String status, String message) {

    public static PlaceOrderResponse accepted(UUID orderId) {
        return new PlaceOrderResponse(orderId, "ACCEPTED",
                "Order accepted for processing. Poll GET /api/v1/orders/{orderId} to track status.");
    }
}
