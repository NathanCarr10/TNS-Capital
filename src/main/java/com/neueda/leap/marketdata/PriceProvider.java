package com.neueda.leap.marketdata;

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
}
