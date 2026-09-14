package com.neueda.leap.exception;

public class InstrumentNotFoundException extends RuntimeException {

    public InstrumentNotFoundException(String message) {
        super(message);
    }
}