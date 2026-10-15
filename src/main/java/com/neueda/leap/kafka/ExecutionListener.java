package com.neueda.leap.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.exceptions.SettlementException;
import com.neueda.leap.kafka.events.ExecutionEvent;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.services.DeadLetterService;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes fills from the Broker Simulator (executions topic) and settles
 * them through OrderService.
 * 
 * An order rejected at settlement is captured in the DLQ table like any
 * other failed order, so an admin can review and replay it. A message that
 * cannot be read, or fails for another reason, is retried and then sent to
 * executions.dlq (see EventListenersConfig).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionListener {
    private final OrderService orderService;
    private final DeadLetterService deadLetterService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @KafkaListener(topics = "executions", groupId = "tns-capital-settlement",
            containerFactory = "executionListenerContainerFactory")
    public void onExecution(@Payload String message) throws Exception {
        ExecutionEvent fill = objectMapper.readValue(message, ExecutionEvent.class);
        try {
            orderService.settleExecution(fill);
        } catch (SettlementException ex) {
            OrderEvent order = ex.getOrderEvent();
            MessageEnvelope<OrderEvent> envelope = new MessageEnvelope<>(clock.now(), UUID.randomUUID().toString(),
                    "1.0", "ORDER_ACCEPTED", order);
            deadLetterService.captureFailedMessage(envelope, ex, 0, !ex.isRetryable());
            log.info("Order rejected at settlement captured in the DLQ: orderId={}", order.orderId());
        }
    }
}
