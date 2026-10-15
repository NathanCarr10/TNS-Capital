package com.neueda.leap.marketdata;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Source of current market prices.
 */
public interface PriceProvider {

    /**
     * Returns the latest known price for a symbol.
     *
     * @param symbol the normalized instrument symbol
     * @return the latest price, or empty if no price is available
     */
    Optional<PriceQuote> latestPrice(String symbol);

    /**
     * Returns the latest known prices for several symbols.
     *
     * @param symbols normalized instrument symbols
     * @return prices keyed by symbol; symbols with no price available are left out
     */
    default Map<String, PriceQuote> latestPrices(Collection<String> symbols) {
        Map<String, PriceQuote> prices = new HashMap<>();
        symbols.forEach(symbol -> latestPrice(symbol).ifPresent(quote -> prices.put(symbol, quote)));
        return prices;
    }
}
