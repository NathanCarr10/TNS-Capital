package com.neueda.leap.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CustomExceptionsTest {

    @Test
    void accountNotFoundExceptionStoresMessage() {
        AccountNotFoundException exception = new AccountNotFoundException("Account ACC-001 not found");

        assertAll(
                () -> assertInstanceOf(RuntimeException.class, exception),
                () -> assertEquals("Account ACC-001 not found", exception.getMessage())
        );
    }

    @Test
    void accountNotActiveExceptionStoresMessage() {
        AccountNotActiveException exception = new AccountNotActiveException("Account ACC-001 is not active");

        assertAll(
                () -> assertInstanceOf(RuntimeException.class, exception),
                () -> assertEquals("Account ACC-001 is not active", exception.getMessage())
        );
    }

    @Test
    void instrumentNotFoundExceptionStoresMessage() {
        InstrumentNotFoundException exception = new InstrumentNotFoundException("Instrument AAPL not found");

        assertAll(
                () -> assertInstanceOf(RuntimeException.class, exception),
                () -> assertEquals("Instrument AAPL not found", exception.getMessage())
        );
    }

    @Test
    void insufficientFundsExceptionStoresMessage() {
        InsufficientFundsException exception = new InsufficientFundsException("Insufficient funds for order ORD-001");

        assertAll(
                () -> assertInstanceOf(RuntimeException.class, exception),
                () -> assertEquals("Insufficient funds for order ORD-001", exception.getMessage())
        );
    }

    @Test
    void insufficientHoldingsExceptionStoresMessage() {
        InsufficientHoldingsException exception = new InsufficientHoldingsException("Insufficient holdings for instrument AAPL");

        assertAll(
                () -> assertInstanceOf(RuntimeException.class, exception),
                () -> assertEquals("Insufficient holdings for instrument AAPL", exception.getMessage())
        );
    }

    @Test
    void duplicateOrderExceptionStoresMessage() {
        DuplicateOrderException exception = new DuplicateOrderException("Duplicate order ORD-001 detected");

        assertAll(
                () -> assertInstanceOf(RuntimeException.class, exception),
                () -> assertEquals("Duplicate order ORD-001 detected", exception.getMessage())
        );
    }
}