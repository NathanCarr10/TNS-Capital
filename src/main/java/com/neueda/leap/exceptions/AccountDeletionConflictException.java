package com.neueda.leap.exceptions;

public class AccountDeletionConflictException extends TradingException {

    public AccountDeletionConflictException(String message) {
        super("ACC-409", message);
    }
}
