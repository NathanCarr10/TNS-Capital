package com.neueda.leap.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.DLQStatus;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.model.Account;
import com.neueda.leap.model.DeadLetterMessage;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.DeadLetterMessageRepository;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.DeadLetterService;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-End Integration Tests for Dead-Letter Queue Workflow
 * Tests complete scenarios from message failure to admin recovery
 */
@DisplayName("Dead Letter Queue End-to-End Integration Tests")
public class DeadLetterQueueEndToEndIT extends AbstractIntegrationTest {

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
    private DeadLetterService deadLetterService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private Account account;
    private Instrument instrument;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        dlqRepository.deleteAll();
        accountRepository.deleteAll();
        instrumentRepository.deleteAll();

        // Setup test data
        account = new Account("E2E_TEST", "E2E Test Account", new BigDecimal("100000.00"), clock);
        account = accountRepository.save(account);

        instrument = new Instrument("E2E", "End-to-End Test Stock", "EQUITY", "USD", true);
        instrumentRepository.save(instrument);
    }

    @Test
    @DisplayName("E2E: Invalid account → DLQ capture → Admin review")
    void testInvalidAccountWorkflow() throws Exception {
        // Step 1: Publish order with invalid account
        UUID orderId = UUID.randomUUID();
        Long invalidAccountId = 99999L;

        OrderEvent event = new OrderEvent(
                orderId,
                invalidAccountId,
                instrument.getSymbol(),
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

        String message = objectMapper.writeValueAsString(envelope);
        kafkaTemplate.send("orders", invalidAccountId.toString(), message);

        // Step 2: Wait for message to be captured in DLQ
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<DeadLetterMessage> dlqMessages = dlqRepository
                            .findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING);
                    assertThat(dlqMessages).isNotEmpty();
                    assertThat(dlqMessages.get(0).getFailureType()).contains("AccountNotFoundException");
                });

        // Step 3: Admin reviews the message
        List<DeadLetterMessage> dlqMessages = dlqRepository.findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING);
        DeadLetterMessage dlqMsg = dlqMessages.get(0);

        assertThat(dlqMsg.getFailureReason()).contains("AccountNotFoundException");
        assertThat(dlqMsg.getRetryCount()).isGreaterThanOrEqualTo(0);

        // Step 4: Admin dismisses as unfixable
        deadLetterService.dismissMessage(dlqMsg.getId(), "Invalid account - customer provided wrong account ID");

        // Step 5: Verify message is dismissed
        Optional<DeadLetterMessage> dismissed = dlqRepository.findById(dlqMsg.getId());
        assertThat(dismissed.get().getStatus()).isEqualTo(DLQStatus.IGNORED);
    }

    @Test
    @DisplayName("E2E: Insufficient funds → DLQ → Admin deposits → Replay succeeds")
    void testInsufficientFundsRecoveryWorkflow() throws Exception {
        // Step 1: Create account with low balance
        Account poorAccount = new Account("POOR", "Low Balance Account", new BigDecimal("10.00"), clock);
        poorAccount = accountRepository.save(poorAccount);

        // Step 2: Attempt large order (will fail)
        UUID orderId = UUID.randomUUID();
        OrderEvent event = new OrderEvent(
                orderId,
                poorAccount.getId(),
                instrument.getSymbol(),
                OrderSide.BUY,
                1000,
                new BigDecimal("100.00"),
                clock.now());

        MessageEnvelope<OrderEvent> envelope = new MessageEnvelope<>(
                clock.now(),
                UUID.randomUUID().toString(),
                "1.0",
                "ORDER_ACCEPTED",
                event);

        String message = objectMapper.writeValueAsString(envelope);
        kafkaTemplate.send("orders", poorAccount.getId().toString(), message);

        // Step 3: Wait for DLQ capture
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<DeadLetterMessage> dlqMessages = dlqRepository
                            .findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING);
                    assertThat(dlqMessages).isNotEmpty();
                    assertThat(dlqMessages.get(0).getFailureType()).contains("InsufficientFundsException");
                });

        List<DeadLetterMessage> dlqMessages = dlqRepository.findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING);
        DeadLetterMessage dlqMsg = dlqMessages.get(0);

        // Step 4: Admin deposits funds to account
        BigDecimal currentBalance = poorAccount.getCashBalance();
        BigDecimal amountToDeposit = new BigDecimal("200000.00").subtract(currentBalance);
        poorAccount.credit(amountToDeposit);
        accountRepository.save(poorAccount);

        // Step 5: Admin replays the message
        boolean replaySuccess = deadLetterService.replayMessage(dlqMsg.getId(), orderService);

        // Step 6: Verify replay was successful
        assertThat(replaySuccess).isTrue();

        Optional<DeadLetterMessage> replayed = dlqRepository.findById(dlqMsg.getId());
        assertThat(replayed.get().getStatus()).isEqualTo(DLQStatus.RESOLVED);
        assertThat(replayed.get().getResolvedOn()).isNotNull();
    }

    @Test
    @DisplayName("E2E: Multiple DLQ messages with different statuses")
    void testMultipleDLQMessagesStatuses() throws Exception {
        // Create multiple messages with different scenarios
        for (int i = 0; i < 3; i++) {
            UUID orderId = UUID.randomUUID();
            OrderEvent event = new OrderEvent(
                    orderId,
                    99999L + i, // Invalid accounts
                    instrument.getSymbol(),
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

            String message = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send("orders", (99999L + i) + "", message);
        }

        // Wait for all messages to be captured
        await()
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    List<DeadLetterMessage> dlqMessages = dlqRepository
                            .findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING);
                    assertThat(dlqMessages).hasSize(3);
                });

        List<DeadLetterMessage> dlqMessages = dlqRepository.findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING);

        // Replay the first: the account still does not exist, so it stays PENDING
        boolean replayed = deadLetterService.replayMessage(dlqMessages.get(0).getId(), orderService);
        assertThat(replayed).isFalse();
        assertThat(dlqRepository.findById(dlqMessages.get(0).getId()).get().getRetryCount()).isEqualTo(1);

        // Dismiss second message
        deadLetterService.dismissMessage(dlqMessages.get(1).getId(), "Duplicate order");

        // Leave third as PENDING

        // Verify statistics
        long pending = dlqRepository.countByStatus(DLQStatus.PENDING);
        long resolved = dlqRepository.countByStatus(DLQStatus.RESOLVED);
        long ignored = dlqRepository.countByStatus(DLQStatus.IGNORED);

        assertThat(pending).isEqualTo(2);
        assertThat(resolved).isZero();
        assertThat(ignored).isEqualTo(1);
    }

    @Test
    @DisplayName("E2E: Successfully processed order does NOT go to DLQ")
    void testSuccessfulOrderBypassesDLQ() throws Exception {
        // Use existing valid account with sufficient funds
        UUID orderId = UUID.randomUUID();
        OrderEvent event = new OrderEvent(
                orderId,
                account.getId(),
                instrument.getSymbol(),
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

        String message = objectMapper.writeValueAsString(envelope);
        kafkaTemplate.send("orders", account.getId().toString(), message);

        // Wait for successful processing
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(orderRepository.findById(orderId)).isPresent();
                });

        // Verify no DLQ entries
        List<DeadLetterMessage> dlqMessages = dlqRepository.findAll();
        assertThat(dlqMessages).isEmpty();
    }
}
