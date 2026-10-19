package com.neueda.leap.strategy.marketdata;

import com.neueda.leap.strategy.domain.SignalSource;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload of a market-data message, as the trading API publishes it.
 *
 * @param symbol   the instrument symbol
 * @param price    LIVE: a trade's price; REPLAY: a 1-minute bar's close
 * @param time     LIVE: when the trade happened; REPLAY: the bar's start
 * @param source   LIVE or REPLAY
 * @param replayId the replay a REPLAY bar belongs to
 */
public record MarketTick(String symbol, BigDecimal price, Instant time, SignalSource source, String replayId) {
}
