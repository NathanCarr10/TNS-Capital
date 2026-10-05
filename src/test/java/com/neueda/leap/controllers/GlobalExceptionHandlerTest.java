package com.neueda.leap.controllers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import com.neueda.leap.dtos.ErrorResponse;
import com.neueda.leap.exceptions.*;

@DisplayName("GlobalExceptionHandler maps exceptions to the spec error catalog")
class GlobalExceptionHandlerTest {
    private static final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    static Stream<Arguments> catalog() {
        return Stream.of(
                Arguments.of("ACC-404", 404, (Function<String, ResponseEntity<ErrorResponse>>)
                        m -> handler.handleAccountNotFound(new AccountNotFoundException(m))),
                Arguments.of("ACC-403", 403, (Function<String, ResponseEntity<ErrorResponse>>)
                        m -> handler.handleAccountNotActive(new AccountNotActiveException(m))),
                Arguments.of("INS-404", 404, (Function<String, ResponseEntity<ErrorResponse>>)
                        m -> handler.handleInstrumentNotFound(new InstrumentNotFoundException(m))),
                Arguments.of("ORD-400", 400, (Function<String, ResponseEntity<ErrorResponse>>)
                        m -> handler.handleInsufficientFunds(new InsufficientFundsException(m))),
                Arguments.of("ORD-409", 409, (Function<String, ResponseEntity<ErrorResponse>>)
                        m -> handler.handleInsufficientHoldings(new InsufficientHoldingsException(m))),
                Arguments.of("ORD-409", 409, (Function<String, ResponseEntity<ErrorResponse>>)
                        m -> handler.handleDuplicateOrder(new DuplicateOrderException(m))),
                Arguments.of("ORD-409", 409, (Function<String, ResponseEntity<ErrorResponse>>)
                        m -> handler.handleOrderCancellationConflict(new OrderCancellationConflictException(m))),
                Arguments.of("ORD-404", 404, (Function<String, ResponseEntity<ErrorResponse>>)
                        m -> handler.handleOrderNotFound(new OrderNotFoundException(m))),
                Arguments.of("ACC-409", 409, (Function<String, ResponseEntity<ErrorResponse>>)
                        m -> handler.handleAccountAlreadyExists(new AccountAlreadyExistsException(m))));
    }

    @ParameterizedTest(name = "{0} -> HTTP {1}")
    @MethodSource("catalog")
    void testCatalogMapping(String code, int status, Function<String, ResponseEntity<ErrorResponse>> handle) {
        ResponseEntity<ErrorResponse> response = handle.apply("internal detail");

        assertEquals(status, response.getStatusCode().value());
        assertEquals(code, response.getBody().errorCode());
        assertFalse(response.getBody().message().contains("internal detail"),
                "Should return a fixed message, not the exception's internal message");
    }

    @Test
    @DisplayName("Idempotency key constraint violations return ORD-409")
    void testIdempotencyKeyConstraintViolation() {
        var e = new DataIntegrityViolationException("could not execute batch",
                new java.sql.SQLException("duplicate key value violates unique constraint \"orders_idempotency_key_key\""));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(e);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("ORD-409", response.getBody().errorCode());
    }

    @Test
    @DisplayName("Other constraint violations return SYS-500")
    void testOtherConstraintViolation() {
        var e = new DataIntegrityViolationException("could not execute batch",
                new java.sql.SQLException("insert or update on table \"orders\" violates foreign key constraint"));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(e);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("SYS-500", response.getBody().errorCode());
    }

    @Test
    @DisplayName("Unexpected exceptions return SYS-500 without leaking the message")
    void testGenericException() {
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(new RuntimeException("secret"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("SYS-500", response.getBody().errorCode());
        assertFalse(response.getBody().message().contains("secret"));
    }

    @Test
    @DisplayName("Account number constraint violations return ACC-409")
    void testAccountNumberConstraintViolation() {
        var e = new DataIntegrityViolationException("could not execute statement",
                new java.sql.SQLException("duplicate key value violates unique constraint \"accounts_account_number_key\""));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(e);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("ACC-409", response.getBody().errorCode());
    }

    @Test
    @DisplayName("Unsupported HTTP methods return 405 instead of 500")
    void testMethodNotSupported() {
        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(
                new org.springframework.web.HttpRequestMethodNotSupportedException("PUT"));

        assertEquals(405, response.getStatusCode().value());
        assertEquals("REQ-405", response.getBody().errorCode());
    }

    @Test
    @DisplayName("Non-JSON request bodies return 415 instead of 500")
    void testMediaTypeNotSupported() {
        ResponseEntity<ErrorResponse> response = handler.handleMediaTypeNotSupported(
                new org.springframework.web.HttpMediaTypeNotSupportedException("text/plain not supported"));

        assertEquals(415, response.getStatusCode().value());
        assertEquals("REQ-415", response.getBody().errorCode());
    }

    @Test
    @DisplayName("A missing query parameter returns VAL-422")
    void testMissingParameter() {
        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(
                new org.springframework.web.bind.MissingServletRequestParameterException("status", "String"));

        assertEquals(422, response.getStatusCode().value());
        assertEquals("VAL-422", response.getBody().errorCode());
    }
}
