package com.neueda.leap.metrics;

import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.model.Order;
import com.neueda.leap.time.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The order flow as metrics, for the monitoring stack (docker-compose.yml,
 * profile "observability") and the admin dashboard's Monitoring view.
 *
 * - trading.orders.accepted{side}: orders accepted and queued on Kafka
 * - trading.orders.finished{status}: orders that reached FILLED, REJECTED or CANCELLED
 * - trading.order.fill.time: from acceptance to the FILLED event
 *
 * The fill time keeps only a few fixed buckets (from 0.25 s to 5 min) rather
 * than a full percentile histogram: a dozen series instead of about seventy,
 * which is still enough for the dashboard's median and 95th percentile.
 */
@Component
public class TradingMetrics {
    static final Duration[] FILL_TIME_BUCKETS = {
            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2),
            Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30),
            Duration.ofMinutes(1), Duration.ofMinutes(5)};

    private final MeterRegistry registry;
    private final Clock clock;
    private final Timer fillTime;

    public TradingMetrics(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        this.fillTime = Timer.builder("trading.order.fill.time")
                .description("Time from accepting an order to filling it")
                .serviceLevelObjectives(FILL_TIME_BUCKETS)
                .register(registry);
    }

    /** An order was stored as NEW and queued on Kafka. */
    public void orderAccepted(Order order) {
        Counter.builder("trading.orders.accepted")
                .description("Orders accepted and queued for execution")
                .tag("side", String.valueOf(order.getSide()))
                .register(registry)
                .increment();
    }

    /** An order reached its final status; a FILLED one also records its fill time. */
    public void orderFinished(Order order) {
        Counter.builder("trading.orders.finished")
                .description("Orders that reached a final status")
                .tag("status", String.valueOf(order.getStatus()))
                .register(registry)
                .increment();
        if (order.getStatus() == OrderStatus.FILLED && order.getCreatedOn() != null) {
            Duration taken = Duration.between(order.getCreatedOn(), clock.now());
            if (!taken.isNegative()) {
                fillTime.record(taken);
            }
        }
    }
}
