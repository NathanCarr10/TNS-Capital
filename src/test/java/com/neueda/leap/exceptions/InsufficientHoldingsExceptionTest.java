package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InsufficientHoldingsExceptionTest {

    @Test
    void testInsufficientHoldingsExceptionWithMessage() {
        // ARRANGE
        String message = "Insufficient holdings";
        
        // ACT
        InsufficientHoldingsException exception = new InsufficientHoldingsException(message);
        
        // ASSERT
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testInsufficientHoldingsExceptionCanBeThrown() {
        // ARRANGE & ACT & ASSERT: verify exception is thrown
        assertThrows(InsufficientHoldingsException.class, () -> {
            throw new InsufficientHoldingsException("Insufficient holdings");
        });
    }
}
