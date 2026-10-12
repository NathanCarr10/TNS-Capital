package com.neueda.leap.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.neueda.leap.kafka.events.TradeEvent;
import com.neueda.leap.model.TradeEventRecord;
import com.neueda.leap.repositories.TradeEventRecordRepository;
import com.neueda.leap.time.ClockTest;

@DisplayName("TradeEventAuditService")
class TradeEventAuditServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private static final UUID ORDER_ID = UUID.randomUUID();

    private final TradeEventRecordRepository repository = mock(TradeEventRecordRepository.class);
    private final TradeEventAuditService service = new TradeEventAuditService(repository, new ClockTest(NOW));

    private static TradeEvent filledEvent() {
        return new TradeEvent(NOW, "event-1", "1.0", "order_status_changed", new TradeEvent.TradeEventPayload(
                ORDER_ID.toString(), 11L, "ACME", "BUY", 10, "30.00", "FILLED", "NEW", null));
    }

    @Test
    @DisplayName("stores a new event with its order details")
    void recordsNewEvent() {
        assertTrue(service.record(filledEvent()));

        ArgumentCaptor<TradeEventRecord> saved = ArgumentCaptor.forClass(TradeEventRecord.class);
        verify(repository).save(saved.capture());
        TradeEventRecord record = saved.getValue();
        assertEquals("event-1", record.getEventId());
        assertEquals(ORDER_ID, record.getOrderId());
        assertEquals(11L, record.getAccountId());
        assertEquals(new BigDecimal("30.00"), record.getPrice());
        assertEquals("NEW", record.getPreviousStatus());
        assertEquals("FILLED", record.getNewStatus());
        assertEquals(NOW, record.getReceivedOn());
    }

    @Test
    @DisplayName("stores a redelivered event only once")
    void skipsDuplicate() {
        when(repository.existsById("event-1")).thenReturn(true);

        assertFalse(service.record(filledEvent()));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("refuses an event without an ID")
    void rejectsEventWithoutId() {
        TradeEvent event = filledEvent();
        event.setCorrelationId(null);

        assertThrows(NullPointerException.class, () -> service.record(event));
    }
}
