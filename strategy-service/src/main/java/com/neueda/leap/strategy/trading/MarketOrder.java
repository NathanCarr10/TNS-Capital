package com.neueda.leap.strategy.trading;

import java.math.BigDecimal;

/**
 * An order a strategy places.
 *
 * @param fallbackPrice the signal's price, sent only if the API has no live
 *                      price to price a market order with (no market data keys)
 */
public record MarketOrder(Long accountId, String symbol, String side, int quantity, String idempotencyKey,
        BigDecimal fallbackPrice) {
}
