package com.neueda.leap.exceptions;

public class OrderCancellationConflictException extends ConflictException {

    public OrderCancellationConflictException(String message) {
        super("ORD-409", message);
    }
}
