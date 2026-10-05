package com.neueda.leap.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.exceptions.OrderSubmissionException;
import com.neueda.leap.kafka.events.OrderEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OrderEventPublisher only reports success once Kafka acknowledges")
class OrderEventPublisherTest {
    private KafkaTemplate<String, String> kafkaTemplate;
    private OrderEvent event;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        event = new OrderEvent(UUID.randomUUID(), 7L, "ACME", OrderSide.BUY, 10, new BigDecimal("25.50"),
                Instant.parse("2026-10-05T10:00:00Z"), "KEY-1");
    }

    private OrderEventPublisher publisher(long timeoutSeconds) {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new OrderEventPublisher(kafkaTemplate, objectMapper, "orders", timeoutSeconds);
    }

    @Test
    @DisplayName("Sends the event, keyed by account, and returns once acknowledged")
    void acknowledgedSendSucceeds() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition("orders", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> result = new SendResult<>(new ProducerRecord<>("orders", "7", "{}"), metadata);
        when(kafkaTemplate.send(eq("orders"), eq("7"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(result));

        OrderEventPublisher publisher = publisher(10);
        assertDoesNotThrow(() -> publisher.publishEvent(event, 7L));

        verify(kafkaTemplate).send(eq("orders"), eq("7"), contains("\"idempotencyKey\":\"KEY-1\""));
    }

    @Test
    @DisplayName("A failed send is reported instead of accepting an order that would be lost")
    void failedSendThrows() {
        when(kafkaTemplate.send(eq("orders"), eq("7"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        OrderEventPublisher publisher = publisher(10);
        assertThrows(OrderSubmissionException.class, () -> publisher.publishEvent(event, 7L));
    }

    @Test
    @DisplayName("No acknowledgement within the timeout is reported as a failure")
    void unacknowledgedSendTimesOut() {
        when(kafkaTemplate.send(eq("orders"), eq("7"), anyString())).thenReturn(new CompletableFuture<>());

        OrderEventPublisher publisher = publisher(0);
        assertThrows(OrderSubmissionException.class, () -> publisher.publishEvent(event, 7L));
    }
}
