package com.neueda.leap.exceptions;

public class InsufficientFundsException extends TradingException {

    public InsufficientFundsException(String message) {
        super("ORD-400", message);
    }
}