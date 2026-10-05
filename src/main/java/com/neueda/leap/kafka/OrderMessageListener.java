package com.neueda.leap.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.services.DeadLetterService;
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
 * Failures are automatically captured and routed to the Dead-Letter Queue.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderMessageListener {

    private final OrderService orderService;
    private final DeadLetterService deadLetterService;
    private final ObjectMapper objectMapper;

    /**
     * Processes order events from the Kafka "orders" topic.
     * 
     * Deserializes the message envelope, extracts the order event, and processes
     * it.
     * Failures are automatically handled by the error handler which routes to DLQ.
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
            orderService.processOrderEvent(event);

            log.info("Order event processed successfully: orderId={}", event.orderId());

        } catch (Exception ex) {
            log.error("Error processing order event: error={}",
                    ex.getMessage(), ex);
            // Re-throw the exception to trigger the error handler
            // which will implement retry logic and DLQ routing
            throw ex;
        }
    }
}
