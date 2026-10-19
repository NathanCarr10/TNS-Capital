package com.neueda.leap.exceptions;

public class ReplayInProgressException extends TradingException {

    public ReplayInProgressException(String message) {
        super("MKT-409", message);
    }
}
