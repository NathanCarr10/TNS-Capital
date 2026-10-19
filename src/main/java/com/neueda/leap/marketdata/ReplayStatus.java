package com.neueda.leap.marketdata;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Progress of a market replay.
 *
 * @param replayId      identifies the replay's bars on the market-data topic
 * @param symbol        the instrument replayed
 * @param date          the US trading day replayed
 * @param barsPublished 1-minute bars published so far
 * @param totalBars     1-minute bars in the day's regular session
 * @param barsPerSecond replay speed
 * @param running       false once every bar is published or the replay is stopped
 * @param startedAt     when the replay started
 * @param startedBy     the user who started it
 */
public record ReplayStatus(String replayId, String symbol, LocalDate date, int barsPublished, int totalBars,
        int barsPerSecond, boolean running, Instant startedAt, String startedBy) {
}
