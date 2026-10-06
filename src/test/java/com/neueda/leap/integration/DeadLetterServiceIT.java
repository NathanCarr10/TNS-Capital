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
import com.neueda.leap.services.DeadLetterService;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Tests for DeadLetterService
 * Tests message capture, replay, and dismissal logic
 */
@DisplayName("Dead Letter Service Integration Tests")
public class DeadLetterServiceIT extends AbstractIntegrationTest {

    @Autowired
    private DeadLetterService deadLetterService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private DeadLetterMessageRepository dlqRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private Account testAccount;
    private Instrument testInstrument;
    private MessageEnvelope<OrderEvent> testEnvelope;

    @BeforeEach
    void setUp() {
        dlqRepository.deleteAll();
        accountRepository.deleteAll();
        instrumentRepository.deleteAll();

        // Create test account
        testAccount = new Account("ACC_SERVICE_TEST", "Service Test Account", new BigDecimal("50000.00"), clock);
        testAccount = accountRepository.save(testAccount);

        // Create test instrument
        testInstrument = new Instrument("TEST", "Test Instrument", "EQUITY", "USD", true);
        instrumentRepository.save(testInstrument);

        // Create test envelope
        OrderEvent event = new OrderEvent(
                UUID.randomUUID(),
                testAccount.getId(),
                testInstrument.getSymbol(),
                OrderSide.BUY,
                100,
                new BigDecimal("50.00"),
                clock.now());

        testEnvelope = new MessageEnvelope<>(
                clock.now(),
                UUID.randomUUID().toString(),
                "1.0",
                "ORDER_ACCEPTED",
                event);
    }

    @Test
    @DisplayName("Should capture failed message with full details")
    void testCaptureFailedMessage() throws Exception {
        // Arrange
        Exception testException = new IllegalArgumentException("Test exception for DLQ capture");
        String expectedFailureType = "IllegalArgumentException";

        // Act
        DeadLetterMessage dlqMessage = deadLetterService.captureFailedMessage(
                testEnvelope,
                testException,
                0);

        // Assert
        assertThat(dlqMessage).isNotNull();
        assertThat(dlqMessage.getId()).isNotNull();
        assertThat(dlqMessage.getOriginalOrderId()).isEqualTo(testEnvelope.getPayload().orderId());
        assertThat(dlqMessage.getFailureType()).isEqualTo(expectedFailureType);
        assertThat(dlqMessage.getStatus()).isEqualTo(DLQStatus.PENDING);
        assertThat(dlqMessage.getRetryCount()).isEqualTo(0);
        assertThat(dlqMessage.getCreatedOn()).isNotNull();
        assertThat(dlqMessage.getLastRetryOn()).isNull();
        assertThat(dlqMessage.getResolvedOn()).isNull();
        assertThat(dlqMessage.getFailureReason()).isEqualTo("Test exception for DLQ capture");

        // Verify persisted in database
        Optional<DeadLetterMessage> saved = dlqRepository.findById(dlqMessage.getId());
        assertThat(saved)
                .isPresent()
                .get()
                .isEqualTo(dlqMessage);
    }

    @Test
    @DisplayName("Should replay message successfully and mark as RESOLVED")
    void testReplayMessageSuccess() throws Exception {
        // Arrange - Capture a message first
        Exception testException = new IllegalArgumentException("Initial capture");
        DeadLetterMessage dlqMessage = deadLetterService.captureFailedMessage(
                testEnvelope,
                testException,
                0);

        assertThat(dlqMessage.getStatus()).isEqualTo(DLQStatus.PENDING);

        // Act - Replay
        boolean success = deadLetterService.replayMessage(dlqMessage.getId(), orderService);

        // Assert
        assertThat(success).isTrue();

        // Verify status updated
        Optional<DeadLetterMessage> replayed = dlqRepository.findById(dlqMessage.getId());
        assertThat(replayed)
                .isPresent()
                .get()
                .satisfies(msg -> {
                    assertThat(msg.getStatus()).isEqualTo(DLQStatus.RESOLVED);
                    assertThat(msg.getResolvedOn()).isNotNull();
                    assertThat(msg.getLastRetryOn()).isNotNull();
                });
    }

    @Test
    @DisplayName("Should increment retry count on replay failure")
    void testReplayMessageFailure() throws Exception {
        // Arrange - Create envelope with invalid account (will fail on replay)
        OrderEvent invalidEvent = new OrderEvent(
                UUID.randomUUID(),
                99999L, // Invalid account
                testInstrument.getSymbol(),
                OrderSide.BUY,
                100,
                new BigDecimal("50.00"),
                clock.now());

        MessageEnvelope<OrderEvent> invalidEnvelope = new MessageEnvelope<>(
                clock.now(),
                UUID.randomUUID().toString(),
                "1.0",
                "ORDER_ACCEPTED",
                invalidEvent);

        Exception testException = new IllegalArgumentException("Setup failure");
        DeadLetterMessage dlqMessage = deadLetterService.captureFailedMessage(
                invalidEnvelope,
                testException,
                0);

        assertThat(dlqMessage.getRetryCount()).isEqualTo(0);

        // Act - Attempt replay (should fail due to invalid account)
        boolean success = deadLetterService.replayMessage(dlqMessage.getId(), orderService);

        // Assert
        assertThat(success).isFalse();

        // Verify retry count incremented
        Optional<DeadLetterMessage> retried = dlqRepository.findById(dlqMessage.getId());
        assertThat(retried)
                .isPresent()
                .get()
                .satisfies(msg -> {
                    assertThat(msg.getStatus()).isEqualTo(DLQStatus.PENDING);
                    assertThat(msg.getRetryCount()).isEqualTo(1);
                    assertThat(msg.getLastRetryOn()).isNotNull();
                });
    }

    @Test
    @DisplayName("Should dismiss message and mark as IGNORED")
    void testDismissMessage() throws Exception {
        // Arrange
        Exception testException = new IllegalArgumentException("Dismissible error");
        DeadLetterMessage dlqMessage = deadLetterService.captureFailedMessage(
                testEnvelope,
                testException,
                0);

        String adminNotes = "This order is no longer valid - customer cancelled";

        // Act
        deadLetterService.dismissMessage(dlqMessage.getId(), adminNotes);

        // Assert
        Optional<DeadLetterMessage> dismissed = dlqRepository.findById(dlqMessage.getId());
        assertThat(dismissed)
                .isPresent()
                .get()
                .satisfies(msg -> {
                    assertThat(msg.getStatus()).isEqualTo(DLQStatus.IGNORED);
                    assertThat(msg.getResolvedOn()).isNotNull();
                    assertThat(msg.getAdminNotes()).isEqualTo(adminNotes);
                });
    }

    @Test
    @DisplayName("Should not replay already resolved message")
    void testReplayResolvedMessageBlocked() throws Exception {
        // Arrange - Capture and successfully replay
        Exception testException = new IllegalArgumentException("Setup");
        DeadLetterMessage dlqMessage = deadLetterService.captureFailedMessage(
                testEnvelope,
                testException,
                0);

        deadLetterService.replayMessage(dlqMessage.getId(), orderService);

        // Verify it's resolved
        Optional<DeadLetterMessage> resolved = dlqRepository.findById(dlqMessage.getId());
        assertThat(resolved.get().getStatus()).isEqualTo(DLQStatus.RESOLVED);

        // Act - Try to replay again
        boolean success = deadLetterService.replayMessage(dlqMessage.getId(), orderService);

        // Assert
        assertThat(success).isFalse();
    }

    @Test
    @DisplayName("Should track multiple retries correctly")
    void testTrackMultipleRetries() throws Exception {
        // Arrange
        OrderEvent invalidEvent = new OrderEvent(
                UUID.randomUUID(),
                99999L,
                testInstrument.getSymbol(),
                OrderSide.BUY,
                100,
                new BigDecimal("50.00"),
                clock.now());
        MessageEnvelope<OrderEvent> invalidEnvelope = new MessageEnvelope<>(
                clock.now(), UUID.randomUUID().toString(), "1.0", "ORDER_ACCEPTED", invalidEvent);
        Exception testException = new IllegalArgumentException("Multi-retry test");
        DeadLetterMessage dlqMessage = deadLetterService.captureFailedMessage(
                invalidEnvelope,
                testException,
                0);

        // Act & Assert - Multiple failed retries
        for (int i = 0; i < 3; i++) {
            boolean success = deadLetterService.replayMessage(dlqMessage.getId(), orderService);
            assertThat(success).isFalse();

            Optional<DeadLetterMessage> updated = dlqRepository.findById(dlqMessage.getId());
            assertThat(updated.get().getRetryCount()).isEqualTo(i + 1);
        }
    }
}
