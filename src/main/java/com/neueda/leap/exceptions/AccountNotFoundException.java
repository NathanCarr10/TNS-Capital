package com.neueda.leap.exceptions;

public class AccountNotFoundException extends TradingException {

    public AccountNotFoundException(String message) {
        super("ACC-404", message);
    }
}