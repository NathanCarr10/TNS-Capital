package com.neueda.leap.exceptions;

public class AccountAlreadyExistsException extends ConflictException {

    public AccountAlreadyExistsException(String message) {
        super("ACC-409", message);
    }
}
