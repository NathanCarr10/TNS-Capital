package com.neueda.leap.strategy.domain;

/**
 * PLACED: the trading API accepted the order. FAILED: it refused it, or could not be reached.
 */
public enum SignalOutcome {
    PLACED,
    FAILED
}
