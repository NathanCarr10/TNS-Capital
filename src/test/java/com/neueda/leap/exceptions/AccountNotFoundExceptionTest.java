package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AccountNotFoundExceptionTest {

    @Test
    void testAccountNotFoundExceptionWithMessage() {
        // ARRANGE
        String message = "Account not found";
        
        // ACT
        AccountNotFoundException exception = new AccountNotFoundException(message);
        
        // ASSERT
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testAccountNotFoundExceptionCanBeThrown() {
        // ARRANGE & ACT & ASSERT: verify exception is thrown
        assertThrows(AccountNotFoundException.class, () -> {
            throw new AccountNotFoundException("Account not found");
        });
    }
}
