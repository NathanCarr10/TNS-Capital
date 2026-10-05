package com.neueda.leap.dtos;

/**
 * Response DTO for DLQ statistics.
 * 
 * Provides high-level metrics about Dead-Letter Queue messages.
 */
public record DLQStatistics(
        long pendingMessages,
        long resolvedMessages,
        long ignoredMessages,
        long totalMessages) {
}
