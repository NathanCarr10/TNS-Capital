package com.neueda.leap.exceptions;

public class InstrumentNotFoundException extends ResourceNotFoundException {

    public InstrumentNotFoundException(String message) {
        super("INS-404", message);
    }
}