package com.neueda.leap.strategy.marketdata;

import com.neueda.leap.strategy.domain.SignalSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A 1-minute bar that has just closed, with the closes of the bars before it.
 *
 * @param symbol   the instrument
 * @param source   LIVE or REPLAY
 * @param replayId the replay it came from; null for LIVE
 * @param time     the bar's start
 * @param close    its close
 * @param closes   the series' closes up to and including this one, oldest first
 */
public record ClosedBar(String symbol, SignalSource source, String replayId, Instant time, BigDecimal close,
        List<Double> closes) {
}
