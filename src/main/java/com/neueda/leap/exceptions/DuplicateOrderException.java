package com.neueda.leap.exceptions;

public class DuplicateOrderException extends TradingException {

    public DuplicateOrderException(String message) {
        super("ORD-409", message);
    }
}