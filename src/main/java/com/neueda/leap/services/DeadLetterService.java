package com.neueda.leap.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.DLQStatus;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.model.DeadLetterMessage;
import com.neueda.leap.repositories.DeadLetterMessageRepository;
import com.neueda.leap.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for managing Dead-Letter Queue messages.
 * 
 * Captures failed order messages from Kafka, stores them for administrative
 * review,
 * and handles replay of messages.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DeadLetterService {

    private final DeadLetterMessageRepository dlqRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Captures a failed message envelope and stores it in the DLQ table.
     * 
     * @param envelope   the failed MessageEnvelope
     * @param exception  the exception that caused the failure
     * @param retryCount the number of retries already attempted
     * @return the stored DeadLetterMessage
     */
    public DeadLetterMessage captureFailedMessage(
            MessageEnvelope<OrderEvent> envelope,
            Exception exception,
            int retryCount) {

        try {
            OrderEvent event = envelope.getPayload();
            String originalMessage = objectMapper.writeValueAsString(envelope);

            // Extract root cause exception to get the actual business error
            Throwable rootCause = getRootCause(exception);
            String failureReason = buildFailureReason(exception);
            String failureType = rootCause.getClass().getSimpleName();

            DeadLetterMessage dlqMessage = new DeadLetterMessage(
                    UUID.randomUUID(),
                    event.orderId(),
                    originalMessage,
                    failureReason,
                    failureType,
                    clock.now());

            dlqMessage.setRetryCount(retryCount);
            dlqMessage.setStatus(DLQStatus.PENDING);

            DeadLetterMessage saved = dlqRepository.save(dlqMessage);

            log.info("Captured failed message in DLQ: dlqId={}, orderId={}, failureType={}, retryCount={}",
                    saved.getId(), event.orderId(), failureType, retryCount);

            return saved;
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize message envelope while capturing to DLQ", ex);
            throw new RuntimeException("Failed to capture message in DLQ", ex);
        }
    }

    /**
     * Replays a DLQ message by processing it again.
     * 
     * @param dlqMessageId the ID of the DLQ message to replay
     * @param orderService the service to process the order event
     * @return true if replay was successful, false otherwise
     */
    public boolean replayMessage(UUID dlqMessageId, OrderService orderService) {
        try {
            DeadLetterMessage dlqMessage = dlqRepository.findById(dlqMessageId)
                    .orElseThrow(() -> new IllegalArgumentException("DLQ message not found: " + dlqMessageId));

            if (dlqMessage.getStatus() == DLQStatus.RESOLVED) {
                log.warn("Cannot replay already resolved message: dlqId={}", dlqMessageId);
                return false;
            }

            try {
                // Deserialize the original message envelope using TypeReference to preserve
                // generic type
                MessageEnvelope<OrderEvent> envelope = objectMapper.readValue(
                        dlqMessage.getOriginalMessage(),
                        new com.fasterxml.jackson.core.type.TypeReference<MessageEnvelope<OrderEvent>>() {
                        });

                // Re-process the order event
                OrderEvent event = envelope.getPayload();
                orderService.processOrderEvent(event);

                // Mark DLQ message as resolved
                dlqMessage.setStatus(DLQStatus.RESOLVED);
                dlqMessage.setResolvedOn(clock.now());
                dlqMessage.setLastRetryOn(clock.now());
                dlqRepository.save(dlqMessage);

                log.info("DLQ message replayed successfully: dlqId={}, orderId={}", dlqMessageId, event.orderId());
                return true;

            } catch (Exception ex) {
                // Increment retry count and keep message as PENDING
                dlqMessage.setRetryCount(dlqMessage.getRetryCount() + 1);
                dlqMessage.setLastRetryOn(clock.now());
                dlqRepository.save(dlqMessage);

                log.warn("DLQ message replay failed (attempt {}): dlqId={}, orderId={}, error={}",
                        dlqMessage.getRetryCount(), dlqMessageId,
                        dlqMessage.getOriginalOrderId(), ex.getMessage());

                return false;
            }
        } catch (Exception ex) {
            log.error("Error replaying DLQ message: dlqId={}", dlqMessageId, ex);
            return false;
        }
    }

    /**
     * Dismisses a DLQ message as non-recoverable.
     * 
     * @param dlqMessageId the ID of the DLQ message to dismiss
     * @param adminNotes   optional notes explaining the dismissal
     */
    public void dismissMessage(UUID dlqMessageId, String adminNotes) {
        try {
            DeadLetterMessage dlqMessage = dlqRepository.findById(dlqMessageId)
                    .orElseThrow(() -> new IllegalArgumentException("DLQ message not found: " + dlqMessageId));

            dlqMessage.setStatus(DLQStatus.IGNORED);
            dlqMessage.setResolvedOn(clock.now());
            dlqMessage.setAdminNotes(adminNotes);
            dlqRepository.save(dlqMessage);

            log.info("DLQ message dismissed: dlqId={}, orderId={}, adminNotes={}",
                    dlqMessageId, dlqMessage.getOriginalOrderId(), adminNotes);
        } catch (Exception ex) {
            log.error("Error dismissing DLQ message: dlqId={}", dlqMessageId, ex);
        }
    }

    /**
     * Extracts the root cause from an exception chain.
     * Traverses the exception chain by following getCause() until reaching the
     * original cause.
     * 
     * @param exception the exception to unwrap
     * @return the root cause Throwable
     */
    private static Throwable getRootCause(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    /**
     * Builds a failure reason string from an exception.
     * Extracts the root cause first, then formats it as "ExceptionName: message".
     * Handles null messages gracefully by using empty string.
     * 
     * @param exception the exception to format
     * @return failure reason string with root cause class name and message
     */
    private String buildFailureReason(Exception exception) {
        Throwable rootCause = getRootCause(exception);
        String message = rootCause.getMessage();
        if (message == null) {
            message = "";
        }
        return rootCause.getClass().getSimpleName() + ": " + message;
    }
}
