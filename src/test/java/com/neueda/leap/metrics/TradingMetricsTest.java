package com.neueda.leap.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.model.Order;
import com.neueda.leap.time.FixedClock;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("Trading metrics")
class TradingMetricsTest {
    private static final Instant ACCEPTED = Instant.parse("2026-10-19T10:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TradingMetrics metrics = new TradingMetrics(registry, () -> ACCEPTED.plusMillis(1500));

    private Order order(OrderSide side, OrderStatus status) {
        Order order = new Order(11L, "ACME", side, 10, new BigDecimal("30.00"), "key-1", new FixedClock(ACCEPTED));
        if (status != OrderStatus.NEW) {
            order.setStatus(status);
        }
        return order;
    }

    @Test
    @DisplayName("Counts accepted orders by side")
    void countsAcceptedOrders() {
        metrics.orderAccepted(order(OrderSide.BUY, OrderStatus.NEW));
        metrics.orderAccepted(order(OrderSide.BUY, OrderStatus.NEW));
        metrics.orderAccepted(order(OrderSide.SELL, OrderStatus.NEW));

        assertEquals(2, registry.get("trading.orders.accepted").tag("side", "BUY").counter().count());
        assertEquals(1, registry.get("trading.orders.accepted").tag("side", "SELL").counter().count());
    }

    @Test
    @DisplayName("Counts finished orders by status and times only the fills")
    void timesFills() {
        metrics.orderFinished(order(OrderSide.BUY, OrderStatus.FILLED));
        metrics.orderFinished(order(OrderSide.BUY, OrderStatus.REJECTED));

        assertEquals(1, registry.get("trading.orders.finished").tag("status", "FILLED").counter().count());
        assertEquals(1, registry.get("trading.orders.finished").tag("status", "REJECTED").counter().count());
        Timer fillTime = registry.get("trading.order.fill.time").timer();
        assertEquals(1, fillTime.count());
        assertEquals(1500, fillTime.totalTime(TimeUnit.MILLISECONDS), 0.001);
    }
}
