package com.neueda.leap.strategy.domain;

/**
 * ACTIVE strategies trade; PAUSED ones do not; ARCHIVED ones were deleted by
 * their owner and are kept for the audit trail of their signals.
 */
public enum StrategyStatus {
    ACTIVE,
    PAUSED,
    ARCHIVED
}
