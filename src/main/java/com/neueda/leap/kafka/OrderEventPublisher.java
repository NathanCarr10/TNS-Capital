package com.neueda.leap.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

/**
 * Publishes accepted orders to the {@code orders} topic.
 * 
 * Sending is fire-and-forget from the caller's point of view: a failed send
 * is only logged, because the order is already safely stored as NEW and
 * could be republished by an async retry mechanism if needed.
 * 
 * Messages are keyed by accountId so all orders for one account land on the
 * same partition and are processed in order.
 */
@Component
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String ordersTopic;

    public OrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${trading.kafka.topics.orders:orders}") String ordersTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ordersTopic = ordersTopic;
    }

    /**
     * Publishes an order event to the orders topic from a persisted Order entity.
     * 
     * Used when an order has already been created (e.g., in synchronous flows).
     * 
     * @param order the order to publish
     */
    public void publish(Order order) {
        try {
            OrderEvent orderEvent = buildOrderEvent(order);
            MessageEnvelope<OrderEvent> envelope = buildEnvelope(orderEvent);
            String json = objectMapper.writeValueAsString(envelope);

            String accountId = order.getAccountId().toString();
            kafkaTemplate.send(ordersTopic, accountId, json)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Failed to publish order {} to {}; it will be retried: {}",
                                    order.getId(), ordersTopic, error.getMessage());
                        } else {
                            log.info("Published order {} to {}-{}@{}",
                                    order.getId(), ordersTopic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException ex) {
            log.error("Could not serialize order {}: {}", order.getId(), ex.getMessage(), ex);
            throw new IllegalStateException("Failed to serialize order event for order: " + order.getId(), ex);
        }
    }

    /**
     * Publishes an OrderEvent directly to the orders topic.
     * 
     * Used in async flows where the REST endpoint publishes an event for later
     * processing
     * without creating an Order entity first.
     * 
     * @param event     the order event to publish
     * @param accountId the account ID (used as partition key)
     */
    public void publishEvent(OrderEvent event, Long accountId) {
        try {
            MessageEnvelope<OrderEvent> envelope = buildEnvelope(event);
            String json = objectMapper.writeValueAsString(envelope);

            String partitionKey = accountId.toString();
            kafkaTemplate.send(ordersTopic, partitionKey, json)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Failed to publish order event {} to {}; error: {}",
                                    event.orderId(), ordersTopic, error.getMessage());
                        } else {
                            log.info("Published order event {} to {}-{}@{}",
                                    event.orderId(), ordersTopic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException ex) {
            log.error("Could not serialize order event {}: {}", event.orderId(), ex.getMessage(), ex);
            throw new IllegalStateException("Failed to serialize order event: " + event.orderId(), ex);
        }
    }

    private OrderEvent buildOrderEvent(Order order) {
        return new OrderEvent(
                order.getId(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide(),
                order.getQuantity(),
                order.getPrice(),
                order.getCreatedOn());
    }

    private MessageEnvelope<OrderEvent> buildEnvelope(OrderEvent orderEvent) {
        return new MessageEnvelope<>(
                Instant.now(),
                UUID.randomUUID().toString(), // correlationId
                "1.0", // schemaVersion
                "ORDER_ACCEPTED", // messageType
                orderEvent);
    }
}
