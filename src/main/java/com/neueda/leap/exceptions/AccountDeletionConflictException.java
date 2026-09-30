package com.neueda.leap.exceptions;

public class AccountDeletionConflictException extends RuntimeException {

    public AccountDeletionConflictException(String message) {
        super(message);
    }
}
