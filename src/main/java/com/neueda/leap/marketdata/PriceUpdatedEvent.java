package com.neueda.leap.marketdata;

/**
 * Raised by the live price pool when it takes a newer price for a symbol,
 * from the stream or a REST refresh. MarketDataPublisher republishes it on the
 * market-data topic.
 *
 * @param quote the accepted price
 */
public record PriceUpdatedEvent(PriceQuote quote) {
}
