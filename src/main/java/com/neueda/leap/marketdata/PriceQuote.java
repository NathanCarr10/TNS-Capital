package com.neueda.leap.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The latest trade price of an instrument, as reported by the market data feed.
 *
 * @param symbol    the instrument symbol, e.g. AAPL
 * @param price     the price of the last trade
 * @param tradedAt  when that trade happened on the exchange
 */
public record PriceQuote(String symbol, BigDecimal price, Instant tradedAt) {
}
