package com.neueda.leap.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The closing prices of an instrument's two latest daily bars, from an Alpaca snapshot.
 *
 * @param lastDay    trading day of the latest daily bar (exchange time)
 * @param lastClose  close of the latest daily bar
 * @param priorClose close of the daily bar before it
 */
public record DailyCloses(LocalDate lastDay, BigDecimal lastClose, BigDecimal priorClose) {
    private static final ZoneId EXCHANGE_ZONE = ZoneId.of("America/New_York");

    /**
     * The close of the trading day before the one a trade happened on.
     *
     * Once a new day's trading starts the latest bar is still yesterday's until
     * the first trade of the day, so a trade after lastDay is compared with
     * lastClose and a trade on lastDay with priorClose.
     *
     * @return the previous close, or null if the snapshot did not have it
     */
    public BigDecimal previousCloseFor(Instant tradedAt) {
        LocalDate tradeDay = tradedAt.atZone(EXCHANGE_ZONE).toLocalDate();
        if (lastDay != null && tradeDay.isAfter(lastDay)) {
            return lastClose;
        }
        return priorClose;
    }

    static LocalDate tradingDay(Instant barTime) {
        return barTime.atZone(EXCHANGE_ZONE).toLocalDate();
    }
}
