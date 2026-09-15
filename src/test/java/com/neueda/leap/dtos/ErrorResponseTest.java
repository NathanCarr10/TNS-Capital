package com.neueda.leap.dtos;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class ErrorResponseTest {

    @Test
    void testErrorResponseCreation() {
        // ARRANGE
        LocalDateTime timestamp = LocalDateTime.now();
        String errorCode = "ERR_INVALID_REQUEST";
        String message = "Invalid request";
        
        // ACT
        ErrorResponse response = new ErrorResponse(errorCode, message, timestamp);
        
        // ASSERT
        assertEquals(errorCode, response.errorCode());
        assertEquals(message, response.message());
        assertEquals(timestamp, response.timestamp());
    }

    @Test
    void testErrorResponseWithDifferentErrorCodes() {
        // ARRANGE
        LocalDateTime timestamp = LocalDateTime.now();
        
        // ACT
        ErrorResponse badRequest = new ErrorResponse("ERR_BAD_REQUEST", "Bad Request", timestamp);
        ErrorResponse notFound = new ErrorResponse("ERR_NOT_FOUND", "Not Found", timestamp);
        ErrorResponse internalError = new ErrorResponse("ERR_INTERNAL", "Internal Server Error", timestamp);
        
        // ASSERT
        assertEquals("ERR_BAD_REQUEST", badRequest.errorCode());
        assertEquals("ERR_NOT_FOUND", notFound.errorCode());
        assertEquals("ERR_INTERNAL", internalError.errorCode());
    }
}
