package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InstrumentNotFoundExceptionTest {

    @Test
    void testInstrumentNotFoundExceptionWithMessage() {
        // ARRANGE
        String message = "Instrument not found";
        
        // ACT
        InstrumentNotFoundException exception = new InstrumentNotFoundException(message);
        
        // ASSERT
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testInstrumentNotFoundExceptionCanBeThrown() {
        // ARRANGE & ACT & ASSERT: verify exception is thrown
        assertThrows(InstrumentNotFoundException.class, () -> {
            throw new InstrumentNotFoundException("Instrument not found");
        });
    }
}
