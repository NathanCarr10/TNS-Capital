package com.neueda.leap.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DuplicateOrderExceptionTest {

    @Test
    void testDuplicateOrderExceptionWithMessage() {
        // ARRANGE
        String message = "Duplicate order";
        
        // ACT
        DuplicateOrderException exception = new DuplicateOrderException(message);
        
        // ASSERT
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testDuplicateOrderExceptionCanBeThrown() {
        // ARRANGE & ACT & ASSERT: verify exception is thrown
        assertThrows(DuplicateOrderException.class, () -> {
            throw new DuplicateOrderException("Duplicate order");
        });
    }
}
