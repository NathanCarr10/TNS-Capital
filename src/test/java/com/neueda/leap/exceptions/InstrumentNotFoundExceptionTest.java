package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InstrumentNotFoundExceptionTest {

    @Test
    void testInstrumentNotFoundExceptionWithMessage() {
        String message = "Instrument not found";
        InstrumentNotFoundException exception = new InstrumentNotFoundException(message);
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testInstrumentNotFoundExceptionCanBeThrown() {
        assertThrows(InstrumentNotFoundException.class, () -> {
            throw new InstrumentNotFoundException("Instrument not found");
        });
    }
}
