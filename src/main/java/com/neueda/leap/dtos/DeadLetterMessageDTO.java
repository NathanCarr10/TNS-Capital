package com.neueda.leap.dtos;

import com.neueda.leap.enums.DLQStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for Dead-Letter Message details.
 * 
 * Used by the admin DLQ API to provide detailed information about failed
 * messages.
 */
public record DeadLetterMessageDTO(
        UUID id,
        UUID originalOrderId,
        String failureType,
        String status,
        Integer retryCount,
        Instant createdOn,
        Instant lastRetryOn,
        Instant resolvedOn,
        String adminNotes,
        String failureReason) {
}
