package com.neueda.leap.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload of a market-data message (spec §10.3: price updates from the pricing feed).
 *
 * @param symbol   the instrument symbol, e.g. SPY
 * @param price    LIVE: the price of a trade; REPLAY: the close of a 1-minute bar
 * @param time     LIVE: when the trade happened; REPLAY: the start of the bar
 * @param source   LIVE (the Alpaca stream) or REPLAY (a past trading day, replayed fast)
 * @param replayId the replay this bar belongs to; null for LIVE
 */
public record MarketTick(String symbol, BigDecimal price, Instant time, Source source, String replayId) {

    public enum Source {
        LIVE,
        REPLAY
    }

    public static MarketTick live(PriceQuote quote) {
        return new MarketTick(quote.symbol(), quote.price(), quote.tradedAt(), Source.LIVE, null);
    }

    public static MarketTick replay(String symbol, PriceBar bar, String replayId) {
        return new MarketTick(symbol, bar.close(), bar.time(), Source.REPLAY, replayId);
    }
}
