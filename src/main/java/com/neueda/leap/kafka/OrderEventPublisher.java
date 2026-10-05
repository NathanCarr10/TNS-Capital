package com.neueda.leap.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.exceptions.OrderSubmissionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes accepted orders to the {@code orders} topic.
 * 
 * Sending is synchronous: nothing is stored before the order executes, so if
 * the broker does not acknowledge the event the API must report the failure
 * instead of accepting an order that would be lost.
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
    private final long sendTimeoutSeconds;

    public OrderEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${trading.kafka.topics.orders:orders}") String ordersTopic,
            @Value("${trading.kafka.send-timeout-seconds:10}") long sendTimeoutSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ordersTopic = ordersTopic;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    /**
     * Publishes an OrderEvent to the orders topic and waits for the broker to
     * acknowledge it, so the API only reports an order as accepted once Kafka
     * has it.
     *
     * @param event     the order event to publish
     * @param accountId the account ID (used as partition key)
     * @throws OrderSubmissionException if the broker does not acknowledge in time
     */
    public void publishEvent(OrderEvent event, Long accountId) {
        String json;
        try {
            json = objectMapper.writeValueAsString(buildEnvelope(event));
        } catch (JsonProcessingException ex) {
            log.error("Could not serialize order event {}: {}", event.orderId(), ex.getMessage(), ex);
            throw new IllegalStateException("Failed to serialize order event: " + event.orderId(), ex);
        }

        try {
            SendResult<String, String> result = kafkaTemplate.send(ordersTopic, accountId.toString(), json)
                    .get(sendTimeoutSeconds, TimeUnit.SECONDS);
            log.info("Published order event {} to {}-{}@{}",
                    event.orderId(), ordersTopic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new OrderSubmissionException("Interrupted while publishing order " + event.orderId(), ex);
        } catch (ExecutionException | TimeoutException ex) {
            log.error("Failed to publish order event {} to {}: {}", event.orderId(), ordersTopic, ex.getMessage());
            throw new OrderSubmissionException("Order " + event.orderId() + " could not be queued", ex);
        }
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
