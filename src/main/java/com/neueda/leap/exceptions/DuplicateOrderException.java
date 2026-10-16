package com.neueda.leap.exceptions;

public class DuplicateOrderException extends ConflictException {

    public DuplicateOrderException(String message) {
        super("ORD-409", message);
    }
}