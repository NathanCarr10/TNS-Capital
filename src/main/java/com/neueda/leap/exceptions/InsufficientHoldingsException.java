package com.neueda.leap.exceptions;

public class InsufficientHoldingsException extends TradingException {

    public InsufficientHoldingsException(String message) {
        super("ORD-409", message);
    }
}