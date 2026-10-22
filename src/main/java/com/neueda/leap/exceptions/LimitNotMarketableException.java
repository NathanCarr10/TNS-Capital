package com.neueda.leap.exceptions;

/**
 * A limit order that would not fill at the current market price: a BUY
 * limit below it or a SELL limit above it. Limit orders are
 * immediate-or-cancel, so it is refused when placed (ORD-422) rather than
 * filled at a price the market would not give.
 */
public class LimitNotMarketableException extends TradingException {
    public LimitNotMarketableException(String message) {
        super("ORD-422", message);
    }
}
