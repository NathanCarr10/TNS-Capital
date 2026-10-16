package com.neueda.leap.exceptions;

public class OrderCancellationConflictException extends TradingException {

    public OrderCancellationConflictException(String message) {
        super("ORD-409", message);
    }
}
