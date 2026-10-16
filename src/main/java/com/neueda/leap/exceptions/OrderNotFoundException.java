package com.neueda.leap.exceptions;

/**
 * Exception thrown when an order cannot be found by ID.
 */
public class OrderNotFoundException extends TradingException {
    public OrderNotFoundException(String message) {
        super("ORD-404", message);
    }
}
