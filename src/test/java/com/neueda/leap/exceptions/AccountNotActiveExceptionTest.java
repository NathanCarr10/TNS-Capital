package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AccountNotActiveExceptionTest {

    @Test
    void testAccountNotActiveExceptionWithMessage() {
        // ARRANGE
        String message = "Account is not active";
        
        // ACT
        AccountNotActiveException exception = new AccountNotActiveException(message);
        
        // ASSERT
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testAccountNotActiveExceptionCanBeThrown() {
        // ARRANGE & ACT & ASSERT: verify exception is thrown
        assertThrows(AccountNotActiveException.class, () -> {
            throw new AccountNotActiveException("Account is not active");
        });
    }
}
