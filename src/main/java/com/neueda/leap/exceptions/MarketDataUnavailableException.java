package com.neueda.leap.exceptions;

public class MarketDataUnavailableException extends ServiceUnavailableException {

    public MarketDataUnavailableException(String message) {
        super("MKT-503", message);
    }
}
