package com.neueda.leap.exceptions;

public class OrderCancellationConflictException extends RuntimeException {

    public OrderCancellationConflictException(String message) {
        super(message);
    }
}
