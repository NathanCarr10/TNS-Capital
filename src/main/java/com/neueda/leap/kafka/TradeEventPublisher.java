package com.neueda.leap.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.TradeEvent;
import com.neueda.leap.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;

/**
 * Publishes executed trades to the {@code trade-events} topic.
 * 
 * Trade events represent completed order executions (FILLED status).
 * Unlike order events (which are published when orders are placed), trade events
 * are published when orders are actually executed.
 * 
 * Sending is fire-and-forget from the caller's point of view: a failed send 
 * is only logged, because the trade execution is already safely stored in the database 
 * and could be republished by an async retry mechanism if needed.
 * 
 * Messages are keyed by accountId so all trades for one account land on the 
 * same partition and are processed in order.
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
     * Publishes a trade event to the trade-events topic.
     * 
     * Called when an order is executed (FILLED status).
     * 
     * @param order the executed order to publish as a trade event
     */
    public void publish(Order order) {
        try {
            TradeEvent tradeEvent = buildTradeEvent(order);
            MessageEnvelope<TradeEvent> envelope = buildEnvelope(tradeEvent);
            String json = objectMapper.writeValueAsString(envelope);
            
            String accountId = order.getAccountId().toString();
            kafkaTemplate.send(tradeEventsTopic, accountId, json)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.warn("Failed to publish trade event for order {} to {}; it will be retried: {}", 
                            order.getId(), tradeEventsTopic, error.getMessage());
                    } else {
                        log.info("Published trade event for order {} to {}-{}@{}", 
                            order.getId(), tradeEventsTopic,
                            result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                    }
                });
        } catch (JsonProcessingException ex) {
            log.error("Could not serialize trade event for order {}: {}", order.getId(), ex.getMessage(), ex);
            throw new IllegalStateException("Failed to serialize trade event for order: " + order.getId(), ex);
        }
    }

    private TradeEvent buildTradeEvent(Order order) {
        return new TradeEvent(
            order.getCreatedOn(),
            UUID.randomUUID().toString(),
            "1.0",
            "trade_executed",
            new TradeEvent.TradeEventPayload(
                order.getId().toString(),
                order.getAccountId(),
                order.getSymbol(),
                order.getSide().toString(),
                order.getQuantity(),
                order.getPrice().toString(),
                order.getStatus().toString()
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
