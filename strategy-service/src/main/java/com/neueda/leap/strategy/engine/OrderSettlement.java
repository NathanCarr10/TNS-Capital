package com.neueda.leap.strategy.engine;

import com.neueda.leap.strategy.accounts.PlatformLookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Waits for an order the engine placed to be filled, rejected or cancelled.
 *
 * Orders are executed asynchronously (Kafka, then the broker, which takes
 * up to a few seconds). Acting on the next bar before then could sell shares
 * whose purchase has not filled yet, which the API rejects, so the engine
 * waits for each order to settle first. Bars that arrive meanwhile wait on
 * the topic and are handled in order.
 */
@Component
public class OrderSettlement {
    static final Set<String> FINAL = Set.of("FILLED", "REJECTED", "CANCELLED");

    private final PlatformLookup platform;
    private final Duration timeout;
    private final Duration pollInterval;

    public OrderSettlement(PlatformLookup platform,
            @Value("${strategy.order-settlement.timeout:15s}") Duration timeout,
            @Value("${strategy.order-settlement.poll-interval:200ms}") Duration pollInterval) {
        this.platform = Objects.requireNonNull(platform);
        this.timeout = Objects.requireNonNull(timeout);
        this.pollInterval = Objects.requireNonNull(pollInterval);
    }

    /**
     * @return FILLED, REJECTED or CANCELLED; empty if the order is still open after the timeout
     */
    public Optional<String> await(UUID orderId) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            String status = platform.orderStatuses(List.of(orderId)).get(orderId);
            if (status != null && FINAL.contains(status)) {
                return Optional.of(status);
            }
            if (System.nanoTime() >= deadline) {
                return Optional.empty();
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
    }
}
