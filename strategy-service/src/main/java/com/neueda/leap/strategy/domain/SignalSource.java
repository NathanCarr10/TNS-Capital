package com.neueda.leap.strategy.domain;

/**
 * Where the bar a signal fired on came from: live prices, or a replay of a past day.
 */
public enum SignalSource {
    LIVE,
    REPLAY
}
