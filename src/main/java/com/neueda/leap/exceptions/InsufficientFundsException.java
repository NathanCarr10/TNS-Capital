package com.neueda.leap.exceptions;

public class InsufficientFundsException extends BusinessRuleException {

    public InsufficientFundsException(String message) {
        super("ORD-400", message);
    }
}