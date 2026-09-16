package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InsufficientFundsExceptionTest {

    @Test
    void testInsufficientFundsExceptionWithMessage() {
        String message = "Insufficient funds";
        InsufficientFundsException exception = new InsufficientFundsException(message);
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testInsufficientFundsExceptionCanBeThrown() {
        assertThrows(InsufficientFundsException.class, () -> {
            throw new InsufficientFundsException("Insufficient funds");
        });
    }
}
