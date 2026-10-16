package com.neueda.leap.exceptions;

public class MarketDataUnavailableException extends TradingException {

    public MarketDataUnavailableException(String message) {
        super("MKT-503", message);
    }
}
