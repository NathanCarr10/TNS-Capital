package com.neueda.leap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.exceptions.BrokerUnavailableException;
import com.neueda.leap.kafka.TradeEventPublisher;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.DeadLetterService;
import com.neueda.leap.time.FixedClock;

/**
 * KafkaConfig's recovery handler: what happens to an order message the
 * consumer could not process.
 */
@DisplayName("Kafka recovery handler")
class KafkaRecoveryTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final DeadLetterService deadLetterService = mock(DeadLetterService.class);
    private final TradeEventPublisher tradeEventPublisher = mock(TradeEventPublisher.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private KafkaConfig config;
    private Order order;
    private ConsumerRecord<String, String> failedRecord;
    private final Exception failure = new BrokerUnavailableException("broker down", new RuntimeException("timeout"));

    @BeforeEach
    void setUp() throws Exception {
        config = new KafkaConfig(orderRepository, deadLetterService, tradeEventPublisher, objectMapper, kafkaTemplate);

        order = new Order(7L, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.00"), "key-1",
                new FixedClock(Instant.parse("2026-10-16T10:00:00Z")));
        order.setId(UUID.randomUUID());
        OrderEvent event = new OrderEvent(order.getId(), 7L, "AAPL", OrderSide.BUY, 10,
                new BigDecimal("150.00"), Instant.parse("2026-10-16T10:00:00Z"), "key-1");
        String message = objectMapper.writeValueAsString(
                new MessageEnvelope<>(Instant.parse("2026-10-16T10:00:00Z"), "msg-1", "1.0", "ORDER_ACCEPTED", event));
        failedRecord = new ConsumerRecord<>("orders", 0, 42L, "7", message);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
    }

    @Test
    @DisplayName("A NEW order is stored REJECTED, its event published and the message captured in the DLQ")
    void rejectsNewOrder() {
        config.handleRecovery(failedRecord, failure);

        assertEquals(OrderStatus.REJECTED, order.getStatus());
        verify(orderRepository).save(order);
        verify(tradeEventPublisher).publish(order, OrderStatus.NEW, "EXHAUSTED_RETRIES");
        verify(deadLetterService).captureFailedMessage(any(), eq(failure), anyInt(), anyBoolean());
        verify(kafkaTemplate).send(eq(KafkaConfig.ORDERS_DLQ_TOPIC), eq("7"), anyString());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = OrderStatus.class, names = { "CANCELLED", "FILLED" })
    @DisplayName("An order that was cancelled or filled meanwhile keeps its status")
    void keepsFinalStatus(OrderStatus finalStatus) {
        order.setStatus(finalStatus);

        config.handleRecovery(failedRecord, failure);

        assertEquals(finalStatus, order.getStatus());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(tradeEventPublisher);
    }

    @Test
    @DisplayName("An unreadable message is forwarded unchanged to orders.dlq instead of being lost")
    void forwardsUnreadableMessage() {
        ConsumerRecord<String, String> poison = new ConsumerRecord<>("orders", 1, 7L, null, "{not json");

        config.handleRecovery(poison, failure);

        verify(kafkaTemplate).send(KafkaConfig.ORDERS_DLQ_TOPIC, "{not json");
        verifyNoInteractions(orderRepository, deadLetterService, tradeEventPublisher);
    }
}
