package com.neueda.leap.strategy.web.dto;

import com.neueda.leap.strategy.domain.SignalOutcome;
import com.neueda.leap.strategy.domain.SignalSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A time a strategy's rule fired.
 *
 * @param price       close of the 1-minute bar it fired on
 * @param orderId     the order placed; null if it failed
 * @param outcome     PLACED or FAILED
 * @param orderStatus the order's status now (NEW, FILLED, REJECTED, CANCELLED); null if none
 * @param detail      why it failed
 */
public record SignalResponse(
        Long id,
        Long strategyId,
        String strategyName,
        String symbol,
        String side,
        BigDecimal price,
        Instant barTime,
        SignalSource source,
        UUID orderId,
        SignalOutcome outcome,
        String orderStatus,
        String detail,
        Instant createdOn) {
}
