package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AccountNotFoundExceptionTest {

    @Test
    void testAccountNotFoundExceptionWithMessage() {
        String message = "Account not found";
        AccountNotFoundException exception = new AccountNotFoundException(message);
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testAccountNotFoundExceptionCanBeThrown() {
        assertThrows(AccountNotFoundException.class, () -> {
            throw new AccountNotFoundException("Account not found");
        });
    }
}
