package com.neueda.leap.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.DLQStatus;
import com.neueda.leap.model.DeadLetterMessage;
import com.neueda.leap.repositories.DeadLetterMessageRepository;
import com.neueda.leap.time.ClockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DeadLetterService keeps messages that are not readable orders")
class DeadLetterServiceTest {
    private DeadLetterService service;

    @BeforeEach
    void setUp() {
        DeadLetterMessageRepository repository = mock(DeadLetterMessageRepository.class);
        when(repository.save(any(DeadLetterMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new DeadLetterService(repository, new ObjectMapper(),
                new ClockTest(Instant.parse("2026-10-05T10:00:00Z")));
    }

    @Test
    @DisplayName("Stores the raw text with the root cause and no order ID")
    void capturesUnreadableMessage() {
        Exception failure = new IllegalStateException("listener failed",
                new IllegalArgumentException("Unexpected character 'n'"));

        DeadLetterMessage captured = service.captureUnreadableMessage("not json", failure);

        assertEquals("not json", captured.getOriginalMessage());
        assertNull(captured.getOriginalOrderId());
        assertEquals("IllegalArgumentException", captured.getFailureType());
        assertTrue(captured.getFailureReason().contains("Unexpected character"));
        assertEquals(DLQStatus.PENDING, captured.getStatus());
    }

    @Test
    @DisplayName("Stores a placeholder for an empty message, which the table requires")
    void capturesEmptyMessage() {
        DeadLetterMessage captured = service.captureUnreadableMessage("  ", new IllegalStateException());

        assertEquals("<empty message>", captured.getOriginalMessage());
    }
}
