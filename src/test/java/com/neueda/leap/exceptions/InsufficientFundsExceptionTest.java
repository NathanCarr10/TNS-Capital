package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InsufficientFundsExceptionTest {

    @Test
    void testInsufficientFundsExceptionWithMessage() {
        // ARRANGE
        String message = "Insufficient funds";
        
        // ACT
        InsufficientFundsException exception = new InsufficientFundsException(message);
        
        // ASSERT
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testInsufficientFundsExceptionCanBeThrown() {
        // ARRANGE & ACT & ASSERT: verify exception is thrown
        assertThrows(InsufficientFundsException.class, () -> {
            throw new InsufficientFundsException("Insufficient funds");
        });
    }
}
