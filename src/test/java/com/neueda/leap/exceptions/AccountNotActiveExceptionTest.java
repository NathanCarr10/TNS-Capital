package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AccountNotActiveExceptionTest {

    @Test
    void testAccountNotActiveExceptionWithMessage() {
        String message = "Account is not active";
        AccountNotActiveException exception = new AccountNotActiveException(message);
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testAccountNotActiveExceptionCanBeThrown() {
        assertThrows(AccountNotActiveException.class, () -> {
            throw new AccountNotActiveException("Account is not active");
        });
    }
}
