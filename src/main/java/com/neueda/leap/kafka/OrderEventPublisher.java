package com.neueda.leap.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import com.neueda.leap.exceptions.OrderPublishException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private final long sendTimeoutMs;

    public OrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${trading.kafka.topics.orders:orders}") String ordersTopic,
            @Value("${trading.kafka.send-timeout-ms:5000}") long sendTimeoutMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ordersTopic = ordersTopic;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    /**
     * Publishes a stored NEW order to the orders topic and waits for Kafka to
     * acknowledge it (at most trading.kafka.send-timeout-ms), so the caller
     * knows whether the order is really queued.
     * 
     * @param order the order to publish
     * @throws OrderPublishException if Kafka did not acknowledge the order in time
     */
    public void publish(Order order) {
        try {
            OrderEvent orderEvent = buildOrderEvent(order);
            MessageEnvelope<OrderEvent> envelope = buildEnvelope(orderEvent);
            String json = objectMapper.writeValueAsString(envelope);

            String accountId = order.getAccountId().toString();
            SendResult<String, String> result = kafkaTemplate.send(ordersTopic, accountId, json)
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            log.info("Published order {} to {}-{}@{}",
                    order.getId(), ordersTopic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OrderPublishException("Interrupted while queuing order " + order.getId(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            log.error("Failed to publish order {} to {}: {}", order.getId(), ordersTopic, ex.getMessage());
            throw new OrderPublishException("Could not queue order " + order.getId(), ex);
        } catch (JsonProcessingException ex) {
            log.error("Could not serialize order {}: {}", order.getId(), ex.getMessage(), ex);
            throw new IllegalStateException("Failed to serialize order event for order: " + order.getId(), ex);
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
                order.getCreatedOn(),
                order.getIdempotencyKey());
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
