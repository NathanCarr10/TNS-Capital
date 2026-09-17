package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InsufficientHoldingsExceptionTest {

    @Test
    void testInsufficientHoldingsExceptionWithMessage() {
        String message = "Insufficient holdings";
        InsufficientHoldingsException exception = new InsufficientHoldingsException(message);
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testInsufficientHoldingsExceptionCanBeThrown() {
        assertThrows(InsufficientHoldingsException.class, () -> {
            throw new InsufficientHoldingsException("Insufficient holdings");
        });
    }
}
