package com.neueda.leap.controllers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
                        m -> handler.handleOrderNotFound(new OrderNotFoundException(m))));
    }

    @ParameterizedTest(name = "{0} -> HTTP {1}")
    @MethodSource("catalog")
    void testCatalogMapping(String code, int status, Function<String, ResponseEntity<ErrorResponse>> handle) {
        ResponseEntity<ErrorResponse> response = handle.apply("detail");

        assertEquals(status, response.getStatusCode().value());
        assertEquals(code, response.getBody().errorCode());
        assertEquals("detail", response.getBody().message());
    }

    @Test
    @DisplayName("Unexpected exceptions return SYS-500 without leaking the message")
    void testGenericException() {
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(new RuntimeException("secret"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("SYS-500", response.getBody().errorCode());
        assertFalse(response.getBody().message().contains("secret"));
    }
}
