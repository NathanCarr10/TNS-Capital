package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DuplicateOrderExceptionTest {

    @Test
    void testDuplicateOrderExceptionWithMessage() {
        String message = "Duplicate order";
        DuplicateOrderException exception = new DuplicateOrderException(message);
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testDuplicateOrderExceptionCanBeThrown() {
        assertThrows(DuplicateOrderException.class, () -> {
            throw new DuplicateOrderException("Duplicate order");
        });
    }
}
