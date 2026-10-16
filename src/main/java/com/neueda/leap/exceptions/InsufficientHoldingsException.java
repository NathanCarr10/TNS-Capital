package com.neueda.leap.exceptions;

public class InsufficientHoldingsException extends BusinessRuleException {

    public InsufficientHoldingsException(String message) {
        super("ORD-409", message);
    }
}