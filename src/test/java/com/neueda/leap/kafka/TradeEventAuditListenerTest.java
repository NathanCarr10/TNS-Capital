package com.neueda.leap.kafka;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.kafka.events.TradeEvent;
import com.neueda.leap.model.Order;
import com.neueda.leap.services.TradeEventAuditService;
import com.neueda.leap.time.FixedClock;

@DisplayName("TradeEventAuditListener")
class TradeEventAuditListenerTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    @DisplayName("reads exactly what TradeEventPublisher writes")
    @SuppressWarnings("unchecked")
    void roundTripsThePublishersMessage() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        Order order = new Order(11L, "ACME", OrderSide.BUY, 10, new BigDecimal("30.00"), "key-1", new FixedClock());
        order.setStatus(OrderStatus.REJECTED);
        new TradeEventPublisher(kafkaTemplate, objectMapper, "trade-events").publish(order, OrderStatus.NEW,
                "Insufficient funds");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("trade-events"), eq("11"), json.capture());

        TradeEventAuditService auditService = mock(TradeEventAuditService.class);
        new TradeEventAuditListener(auditService, objectMapper).onTradeEvent(json.getValue());

        ArgumentCaptor<TradeEvent> event = ArgumentCaptor.forClass(TradeEvent.class);
        verify(auditService).store(event.capture());
        TradeEvent.TradeEventPayload payload = event.getValue().getPayload();
        assertNotNull(event.getValue().getCorrelationId());
        assertEquals(order.getId().toString(), payload.getOrderId());
        assertEquals("NEW", payload.getPreviousStatus());
        assertEquals("REJECTED", payload.getStatus());
        assertEquals("Insufficient funds", payload.getReason());
    }

    @Test
    @DisplayName("throws on an unreadable message, so it is retried and sent to trade-events.dlq")
    void rejectsGarbage() {
        TradeEventAuditService auditService = mock(TradeEventAuditService.class);
        TradeEventAuditListener listener = new TradeEventAuditListener(auditService, objectMapper);

        assertThrows(Exception.class, () -> listener.onTradeEvent("not json"));
        verifyNoInteractions(auditService);
    }
}
