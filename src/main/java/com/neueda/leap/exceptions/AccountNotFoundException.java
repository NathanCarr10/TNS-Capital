package com.neueda.leap.exceptions;

public class AccountNotFoundException extends ResourceNotFoundException {

    public AccountNotFoundException(String message) {
        super("ACC-404", message);
    }
}