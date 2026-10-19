package com.neueda.leap.exceptions;

/**
 * No replay is running, or the requested day has no 1-minute bars to replay
 * (a weekend or market holiday).
 */
public class ReplayNotFoundException extends TradingException {

    public ReplayNotFoundException(String message) {
        super("MKT-404", message);
    }
}
