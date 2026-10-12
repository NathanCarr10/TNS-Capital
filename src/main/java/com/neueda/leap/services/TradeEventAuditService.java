package com.neueda.leap.services;

import com.neueda.leap.kafka.events.TradeEvent;
import com.neueda.leap.model.TradeEventRecord;
import com.neueda.leap.repositories.TradeEventRecordRepository;
import com.neueda.leap.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Keeps the audit trail of order lifecycle events (NFR-04): every event on
 * the trade-events topic is stored once in trade_event_log.
 */
@Service
@Slf4j
public class TradeEventAuditService {
    private final TradeEventRecordRepository repository;
    private final Clock clock;

    public TradeEventAuditService(TradeEventRecordRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Stores the event, unless it was stored before (Kafka can redeliver).
     *
     * @return true if it was stored now, false if it was a duplicate
     */
    @Transactional
    public boolean store(TradeEvent event) {
        String eventId = Objects.requireNonNull(event.getCorrelationId(), "Trade event has no correlationId");
        if (repository.existsById(eventId)) {
            log.debug("Trade event already recorded: eventId={}", eventId);
            return false;
        }
        TradeEvent.TradeEventPayload payload = Objects.requireNonNull(event.getPayload(), "Trade event has no payload");
        repository.save(new TradeEventRecord(
                eventId,
                UUID.fromString(payload.getOrderId()),
                payload.getAccountId(),
                payload.getSymbol(),
                payload.getSide(),
                payload.getQuantity(),
                new BigDecimal(payload.getPrice()),
                payload.getPreviousStatus(),
                payload.getStatus(),
                payload.getReason(),
                clock.now()));
        log.info("Recorded trade event: orderId={}, {} -> {}", payload.getOrderId(), payload.getPreviousStatus(),
                payload.getStatus());
        return true;
    }

    @Transactional(readOnly = true)
    public List<TradeEventRecord> eventsForOrder(UUID orderId) {
        return repository.findByOrderIdOrderByReceivedOnAsc(orderId);
    }
}
