package com.neueda.leap.marketdata;

import java.time.Duration;
import java.util.Arrays;

/**
 * The bar sizes the dashboard can chart.
 *
 * Alpaca returns only today's bars unless a start is given, so each timeframe
 * has a lookback long enough to hold the largest bar limit the API allows.
 */
public enum BarTimeframe {
    MINUTES_15("15Min", Duration.ofDays(60)),
    HOUR_1("1Hour", Duration.ofDays(250)),
    DAY_1("1Day", Duration.ofDays(6L * 365));

    private final String value;
    private final Duration lookback;

    BarTimeframe(String value, Duration lookback) {
        this.value = value;
        this.lookback = lookback;
    }

    /** The timeframe as Alpaca and the API's query parameter spell it, e.g. 1Day. */
    public String value() {
        return value;
    }

    public Duration lookback() {
        return lookback;
    }

    /**
     * @throws IllegalArgumentException if the value is not a supported timeframe
     */
    public static BarTimeframe fromValue(String value) {
        return Arrays.stream(values())
                .filter(timeframe -> timeframe.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported timeframe: " + value));
    }
}
