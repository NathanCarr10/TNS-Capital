package com.neueda.leap.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.TradeEvent;
import com.neueda.leap.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Publishes trade lifecycle events to the {@code trade-events} topic.
 * 
 * Trade events represent order status changes across the complete lifecycle:
 * NEW, FILLED, REJECTED, CANCELLED.
 * 
 * Each event includes:
 * - The originating order ID for tracking and auditing
 * - Current and previous status for lifecycle tracking
 * - Optional reason for status changes (e.g., rejection or cancellation reason)
 * 
 * Sending is fire-and-forget from the caller's point of view: a failed send 
 * is only logged, because the order status is already safely stored in the database 
 * and could be republished by an async retry mechanism if needed.
 * 
 * Messages are keyed by accountId so all events for one account land on the 
 * same partition and are processed in order.
 * 
 * Consumers can subscribe independently using different consumer group IDs
 * without impacting the producer or other consumers.
 */
@Component
@Slf4j
public class TradeEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String tradeEventsTopic;

    public TradeEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${trading.kafka.topics.trade-events:trade-events}") String tradeEventsTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.tradeEventsTopic = tradeEventsTopic;
    }

    /**
     * Publishes a trade lifecycle event to the trade-events topic.
     * 
     * Called whenever an order status changes (NEW, FILLED, REJECTED, CANCELLED).
     * Each event includes the originating order ID for audit and tracking purposes.
     * 
     * @param order the order with the current status
     * @param previousStatus the order's previous status (null if transitioning from initial state)
     * @param reason optional reason for status change (e.g., rejection reason, cancellation reason)
     */
    public void publish(Order order, OrderStatus previousStatus, String reason) {
        try {
            TradeEvent tradeEvent = buildTradeEvent(order, previousStatus, reason);
            MessageEnvelope<TradeEvent> envelope = buildEnvelope(tradeEvent);
            String json = objectMapper.writeValueAsString(envelope);
            
            String accountId = order.getAccountId().toString();
            kafkaTemplate.send(tradeEventsTopic, accountId, json)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.warn("Failed to publish trade event for order {} (status: {}) to {}; it will be retried: {}", 
                            order.getId(), order.getStatus(), tradeEventsTopic, error.getMessage());
                    } else {
                        log.info("Published {} event for order {} to {}-{}@{}", 
                            order.getStatus(), order.getId(), tradeEventsTopic,
                            result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                    }
                });
        } catch (JsonProcessingException ex) {
            log.error("Could not serialize trade event for order {}: {}", order.getId(), ex.getMessage(), ex);
            throw new IllegalStateException("Failed to serialize trade event for order: " + order.getId(), ex);
        }
    }

    private TradeEvent buildTradeEvent(Order order, OrderStatus previousStatus, String reason) {
        return new TradeEvent(
            order.getCreatedOn(),
            UUID.randomUUID().toString(),
            "1.0",
            "order_status_changed",
            new TradeEvent.TradeEventPayload(
                order.getId().toString(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide().toString(),
                order.getQuantity(),
                order.getPrice().toString(),
                order.getStatus().toString(),
                previousStatus != null ? previousStatus.toString() : null,
                reason
            )
        );
    }

    private MessageEnvelope<TradeEvent> buildEnvelope(TradeEvent tradeEvent) {
        MessageEnvelope<TradeEvent> envelope = new MessageEnvelope<>();
        envelope.setTimestamp(tradeEvent.getTimestamp());
        envelope.setCorrelationId(tradeEvent.getCorrelationId());
        envelope.setSchemaVersion(tradeEvent.getSchemaVersion());
        envelope.setMessageType(tradeEvent.getMessageType());
        envelope.setPayload(tradeEvent);
        return envelope;
    }
}
