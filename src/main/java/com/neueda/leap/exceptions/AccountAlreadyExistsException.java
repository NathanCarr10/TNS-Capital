package com.neueda.leap.exceptions;

public class AccountAlreadyExistsException extends TradingException {

    public AccountAlreadyExistsException(String message) {
        super("ACC-409", message);
    }
}
