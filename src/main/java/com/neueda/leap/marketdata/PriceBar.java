package com.neueda.leap.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One OHLC bar (candlestick) of an instrument, as reported by the market data API.
 *
 * @param time   start of the bar's period
 * @param open   first trade price in the period
 * @param high   highest trade price in the period
 * @param low    lowest trade price in the period
 * @param close  last trade price in the period
 * @param volume number of shares traded in the period
 */
public record PriceBar(Instant time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
        long volume) {
}
