package com.neueda.leap.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.services.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for order events.
 * 
 * Listens to the "orders" topic and processes order events asynchronously.
 * Failures are automatically captured and routed to the Dead-Letter Queue by
 * the error handler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMessageListener {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    /**
     * Processes order events from the Kafka "orders" topic.
     * 
     * EXCEPTION HANDLING:
     * - All exceptions are rethrown to trigger the error handler
     * - Error handler will route the message to DLQ (no retries)
     * - The order is already persisted with REJECTED status before the exception is
     * rethrown
     * - The error handler's recovery callback will publish trade events and route
     * to DLQ
     * 
     * @param message the serialized message as a string
     * @throws Exception if message processing fails (will be handled by error
     *                   handler)
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
            // Note: If order processing fails, an exception is rethrown and caught by the
            // error handler
            orderService.processOrderEvent(event);

            log.info("Order event processed successfully: orderId={}", event.orderId());

        } catch (Exception ex) {
            // Log the error and rethrow to trigger error handler for DLQ routing
            log.error("Error processing order event: error={}",
                    ex.getMessage(), ex);
            throw ex;
        }
    }
}
