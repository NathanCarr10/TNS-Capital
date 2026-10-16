package com.neueda.leap.exceptions;

public class AccountNotActiveException extends BusinessRuleException {

    public AccountNotActiveException(String message) {
        super("ACC-403", message);
    }
}