package com.neueda.leap.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.DLQStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.DeadLetterMessage;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.model.Order;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.DeadLetterMessageRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration Tests for OrderMessageListener (Kafka Consumer)
 * Tests async order processing via Kafka topic
 */
@DisplayName("Order Message Listener Integration Tests")
public class OrderMessageListenerIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private DeadLetterMessageRepository dlqRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private Account testAccount;
    private Instrument testInstrument;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        dlqRepository.deleteAll();
        accountRepository.deleteAll();
        instrumentRepository.deleteAll();

        // Create test account with sufficient funds
        testAccount = new Account("ACC_LISTENER_TEST", "Kafka Listener Tester", new BigDecimal("100000.00"), clock);
        testAccount = accountRepository.save(testAccount);

        // Create test instrument
        testInstrument = new Instrument("KAFKA", "Kafka Test Stock", "EQUITY", "USD", true);
        instrumentRepository.save(testInstrument);
    }

    @Test
    @DisplayName("Should process valid OrderEvent from Kafka successfully")
    void testProcessValidOrderEvent() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        OrderEvent event = new OrderEvent(
                orderId,
                testAccount.getId(),
                testInstrument.getSymbol(),
                OrderSide.BUY,
                100,
                new BigDecimal("50.00"),
                clock.now());

        MessageEnvelope<OrderEvent> envelope = new MessageEnvelope<>(
                clock.now(),
                UUID.randomUUID().toString(),
                "1.0",
                "ORDER_ACCEPTED",
                event);

        // Act - Publish to Kafka
        try {
            String message = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("orders", testAccount.getId().toString(), message);

            // Assert - Wait for consumer to process
            await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        Optional<Order> processedOrder = orderRepository.findById(orderId);
                        assertThat(processedOrder)
                                .isPresent()
                                .get()
                                .satisfies(order -> {
                                    assertThat(order.getAccountId()).isEqualTo(testAccount.getId());
                                    assertThat(order.getSymbol()).isEqualTo(testInstrument.getSymbol());
                                });
                    });

            // Verify no DLQ entry for successful order
            List<DeadLetterMessage> dlqMessages = dlqRepository.findAll();
            assertThat(dlqMessages).isEmpty();

        } catch (Exception e) {
            throw new RuntimeException("Failed to publish order event", e);
        }
    }

    @Test
    @DisplayName("Should route invalid account OrderEvent to DLQ")
    void testProcessInvalidAccountOrderEvent() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Long invalidAccountId = 99999L; // Non-existent account

        OrderEvent event = new OrderEvent(
                orderId,
                invalidAccountId,
                testInstrument.getSymbol(),
                OrderSide.BUY,
                100,
                new BigDecimal("50.00"),
                clock.now());

        MessageEnvelope<OrderEvent> envelope = new MessageEnvelope<>(
                clock.now(),
                UUID.randomUUID().toString(),
                "1.0",
                "ORDER_ACCEPTED",
                event);

        // Act - Publish to Kafka
        try {
            String message = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("orders", invalidAccountId.toString(), message);

            // Assert - Wait for DLQ capture
            await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        List<DeadLetterMessage> dlqMessages = dlqRepository
                                .findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING);
                        assertThat(dlqMessages)
                                .isNotEmpty()
                                .anyMatch(msg -> msg.getOriginalOrderId().equals(orderId))
                                .anyMatch(msg -> msg.getFailureType().contains("AccountNotFoundException"));
                    });

        } catch (Exception e) {
            throw new RuntimeException("Failed to publish invalid order event", e);
        }
    }

    @Test
    @DisplayName("Should route insufficient funds OrderEvent to DLQ")
    void testProcessInsufficientFundsOrderEvent() {
        // Arrange - Create account with low balance
        Account lowBalanceAccount = new Account("LOW_BALANCE", "Poor Trader", new BigDecimal("10.00"), clock);
        lowBalanceAccount = accountRepository.save(lowBalanceAccount);

        UUID orderId = UUID.randomUUID();
        OrderEvent event = new OrderEvent(
                orderId,
                lowBalanceAccount.getId(),
                testInstrument.getSymbol(),
                OrderSide.BUY,
                1000, // Large quantity requiring significant funds
                new BigDecimal("100.00"),
                clock.now());

        MessageEnvelope<OrderEvent> envelope = new MessageEnvelope<>(
                clock.now(),
                UUID.randomUUID().toString(),
                "1.0",
                "ORDER_ACCEPTED",
                event);

        // Act - Publish to Kafka
        try {
            String message = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("orders", lowBalanceAccount.getId().toString(), message);

            // Assert - Wait for DLQ capture
            await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        List<DeadLetterMessage> dlqMessages = dlqRepository
                                .findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING);
                        assertThat(dlqMessages)
                                .isNotEmpty()
                                .anyMatch(msg -> msg.getOriginalOrderId().equals(orderId))
                                .anyMatch(msg -> msg.getFailureType().contains("InsufficientFundsException"));
                    });

            // The REJECTED order carries the same reason as the DLQ record, without the
            // exception class name
            DeadLetterMessage dlqMsg = dlqRepository.findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING).stream()
                    .filter(msg -> orderId.equals(msg.getOriginalOrderId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(dlqMsg.getFailureReason()).doesNotContain("Exception");
            assertThat(orderRepository.findById(orderId))
                    .get()
                    .satisfies(order -> {
                        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
                        assertThat(order.getStatusReason()).isEqualTo(dlqMsg.getFailureReason());
                    });

        } catch (Exception e) {
            throw new RuntimeException("Failed to publish insufficient funds order event", e);
        }
    }

    @Test
    @DisplayName("Should capture exception details in DLQ message")
    void testDLQMessageContainsFullStackTrace() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Long invalidAccountId = 99999L;

        OrderEvent event = new OrderEvent(
                orderId,
                invalidAccountId,
                testInstrument.getSymbol(),
                OrderSide.BUY,
                100,
                new BigDecimal("50.00"),
                clock.now());

        MessageEnvelope<OrderEvent> envelope = new MessageEnvelope<>(
                clock.now(),
                UUID.randomUUID().toString(),
                "1.0",
                "ORDER_ACCEPTED",
                event);

        // Act - Publish to Kafka
        try {
            String message = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("orders", invalidAccountId.toString(), message);

            // Assert - Verify DLQ message contains full details
            await()
                    .atMost(10, TimeUnit.SECONDS)
                    .pollInterval(100, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> {
                        List<DeadLetterMessage> dlqMessages = dlqRepository
                                .findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING);
                        assertThat(dlqMessages)
                                .isNotEmpty()
                                .first()
                                .satisfies(dlqMsg -> {
                                    assertThat(dlqMsg.getFailureReason())
                                            .startsWith("Account not found")
                                            .doesNotContain("AccountNotFoundException");
                                    // Unknown accounts are marked non-retryable
                                    assertThat(dlqMsg.getFailureType()).isEqualTo("NON_RETRYABLE_AccountNotFoundException");
                                    assertThat(dlqMsg.getIsRetryable()).isFalse();
                                    assertThat(dlqMsg.getRetryCount()).isGreaterThanOrEqualTo(0);
                                    assertThat(dlqMsg.getCreatedOn()).isNotNull();
                                });
                    });

        } catch (Exception e) {
            throw new RuntimeException("Failed to publish order event for stack trace verification", e);
        }
    }
}
