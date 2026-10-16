package com.neueda.leap.exceptions;

/**
 * Exception thrown when a position cannot be found for an account/symbol pair.
 */
public class PositionNotFoundException extends ResourceNotFoundException {
    public PositionNotFoundException(String message) {
        super("POS-404", message);
    }
}
