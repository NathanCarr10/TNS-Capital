package com.neueda.leap.exceptions;

/**
 * Exception thrown when a position cannot be found for an account/symbol pair.
 */
public class PositionNotFoundException extends RuntimeException {
    public PositionNotFoundException(String message) {
        super(message);
    }
}
