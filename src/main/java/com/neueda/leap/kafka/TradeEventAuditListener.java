package com.neueda.leap.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.TradeEvent;
import com.neueda.leap.services.TradeEventAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes the trade-events topic and records each order lifecycle event in
 * the audit trail. It has its own consumer group, so it reads every event
 * independently of any other consumer.
 * 
 * A message that cannot be read or stored is retried, then sent to
 * trade-events.dlq (see AuditKafkaConfig).
 */
@Component
@RequiredArgsConstructor
public class TradeEventAuditListener {
    private final TradeEventAuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${trading.kafka.topics.trade-events:trade-events}", groupId = "tns-capital-audit",
            containerFactory = "auditListenerContainerFactory")
    public void onTradeEvent(@Payload String message) throws Exception {
        // The publisher wraps a TradeEvent (itself enveloped) in a MessageEnvelope
        MessageEnvelope<TradeEvent> envelope = objectMapper.readValue(message,
                new TypeReference<MessageEnvelope<TradeEvent>>() {
                });
        auditService.record(envelope.getPayload());
    }
}
