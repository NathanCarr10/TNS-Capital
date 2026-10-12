package com.neueda.leap.dtos;

import java.time.Instant;

/**
 * One step in an order's lifecycle, from GET /api/v1/orders/{orderId}/events.
 */
public record TradeEventResponse(
        String eventId,
        String previousStatus,
        String newStatus,
        String reason,
        Instant receivedOn) {
}
