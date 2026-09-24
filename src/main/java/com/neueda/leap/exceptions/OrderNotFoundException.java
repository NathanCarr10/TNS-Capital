package com.neueda.leap.exceptions;

/**
 * Exception thrown when an order cannot be found by ID.
 */
public class OrderNotFoundException extends RuntimeException{
    public OrderNotFoundException(String message) {
        super(message);
    }
}
