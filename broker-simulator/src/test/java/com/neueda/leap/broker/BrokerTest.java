package com.neueda.leap.broker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

class BrokerTest {
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private final BrokerProperties properties = new BrokerProperties(Duration.ofMillis(800), Duration.ofMillis(800), 0,
            "executions", "SIM");
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final Broker broker = new Broker(new SimulatedMarket(properties, new Random(1)), kafkaTemplate,
            objectMapper, properties, scheduler, Clock.fixed(NOW, ZoneOffset.UTC));

    private final BrokerOrder order = new BrokerOrder(UUID.randomUUID(), 11L, "ACME", Side.BUY, 10,
            new BigDecimal("30.00"));

    @Test
    @DisplayName("schedules the fill after the market's delay")
    void acceptSchedulesFill() {
        broker.accept(order);

        verify(scheduler).schedule(any(Runnable.class), eq(800L), eq(TimeUnit.MILLISECONDS));
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("publishes the fill to executions, keyed by account")
    void fillPublishesExecution() throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        broker.fill(order);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("executions"), eq("11"), json.capture());
        ExecutionEvent fill = objectMapper.readValue(json.getValue(), ExecutionEvent.class);
        assertEquals(order.orderId(), fill.orderId());
        assertEquals(11L, fill.accountId());
        assertEquals(Side.BUY, fill.side());
        assertEquals(10, fill.quantity());
        assertEquals(new BigDecimal("30.00"), fill.price());
        assertEquals("SIM", fill.venue());
        assertEquals(NOW, fill.executedOn());
        assertNotNull(fill.executionId());
    }
}
