package com.neueda.leap.strategy.performance;

/**
 * An account's trading split in two: the orders its strategies placed, and
 * the ones the trader placed themselves.
 */
public record PerformanceResponse(Long accountId, BookPerformance strategies, BookPerformance manual) {
}
