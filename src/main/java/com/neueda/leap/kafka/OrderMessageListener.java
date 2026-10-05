package com.neueda.leap.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.exceptions.NonRetryableOrderException;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.services.DeadLetterService;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.services.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for order events.
 * 
 * Listens to the "orders" topic and processes order events asynchronously.
 * Failures are automatically captured and routed to the Dead-Letter Queue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMessageListener {

    private final OrderService orderService;
    private final DeadLetterService deadLetterService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Processes order events from the Kafka "orders" topic.
     * 
     * EXCEPTION HANDLING:
     * - NonRetryableOrderException (not-found errors): Caught here, routed directly to DLQ
     *   without retries. This prevents wasted retry attempts on impossible scenarios
     *   (account deleted, instrument doesn't exist).
     * - Other exceptions (business logic errors): Re-thrown to trigger error handler,
     *   which retries 3 times with exponential backoff before DLQ.
     * 
     * @param message the serialized message as a string
     * @throws Exception if message processing fails (business logic errors will be retried)
     */
    @KafkaListener(topics = "${trading.kafka.topics.orders:orders}", groupId = "tns-capital-orders", containerFactory = "kafkaListenerContainerFactory")
    public void onOrderEvent(
            @Payload String message) throws Exception {

        try {
            log.debug("Received order event from Kafka topic");

            // Deserialize the message envelope using TypeReference to preserve generic type
            // info
            MessageEnvelope<OrderEvent> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<MessageEnvelope<OrderEvent>>() {
                    });

            OrderEvent event = envelope.getPayload();

            log.info("Processing order event: orderId={}, accountId={}",
                    event.orderId(), event.accountId());

            // Process the order event
            orderService.processOrderEvent(event);

            log.info("Order event processed successfully: orderId={}", event.orderId());

        } catch (NonRetryableOrderException ex) {
            // NON-RETRYABLE EXCEPTION: Route directly to DLQ without retries
            log.warn("Non-retryable exception caught (skipping retries, routing to DLQ): orderId={}, error={}",
                    extractOrderId(message), ex.getMessage());

            // Deserialize the message envelope for DLQ capture
            try {
                MessageEnvelope<OrderEvent> envelope = objectMapper.readValue(
                        message,
                        new TypeReference<MessageEnvelope<OrderEvent>>() {
                        });

                // Capture to DLQ directly without retries
                deadLetterService.captureFailedMessage(envelope, ex, 0, true); // 0 = no retries, true = non-retryable

                // Publish to orders.dlq topic for audit trail
                kafkaTemplate.send("orders.dlq", envelope.getPayload().accountId().toString(), message);
                log.info("Non-retryable message routed to DLQ: orderId={}", envelope.getPayload().orderId());

                // Do NOT re-throw - message has been processed (sent to DLQ)
                return;
            } catch (Exception dlqException) {
                log.error("Failed to route non-retryable message to DLQ: error={}", dlqException.getMessage(), dlqException);
                // Re-throw the original exception if DLQ capture fails
                throw ex;
            }

        } catch (Exception ex) {
            log.error("Error processing order event: error={}",
                    ex.getMessage(), ex);
            // Re-throw the exception to trigger the error handler
            // which will implement retry logic and DLQ routing for retryable exceptions
            throw ex;
        }
    }

    /**
     * Extracts the order ID from a serialized message for logging purposes.
     * Used in error messages when deserialization fails.
     *
     * @param message the serialized message
     * @return the order ID as a string, or "UNKNOWN" if extraction fails
     */
    private String extractOrderId(String message) {
        try {
            MessageEnvelope<OrderEvent> envelope = objectMapper.readValue(
                    message,
                    new TypeReference<MessageEnvelope<OrderEvent>>() {
                    });
            return envelope.getPayload().orderId().toString();
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }
}
