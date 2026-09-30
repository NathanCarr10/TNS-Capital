package com.neueda.leap.controllers;
//this file defines how to communicate errors to HTTP clients

import com.neueda.leap.dtos.ErrorResponse;
import com.neueda.leap.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

/**
 * Maps exceptions to the error catalog in section 21 of the specification.
 * ORD-404, POS-404, NOT-404 and SYS-500 extend the catalog for cases it does not list.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "ACC-404", e.getMessage());
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException e) {
        return error(HttpStatus.FORBIDDEN, "ACC-403", e.getMessage());
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(InstrumentNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "INS-404", e.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e) {
        return error(HttpStatus.BAD_REQUEST, "ORD-400", e.getMessage());
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientHoldings(InsufficientHoldingsException e) {
        return error(HttpStatus.CONFLICT, "ORD-409", e.getMessage());
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrder(DuplicateOrderException e) {
        return error(HttpStatus.CONFLICT, "ORD-409", e.getMessage());
    }

    // Two requests with the same idempotency key can both pass the duplicate check;
    // the unique constraint then rejects the second one when it commits
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String detail = String.valueOf(cause.getMessage()).toLowerCase();
        if (detail.contains("idempotency_key")) {
            return error(HttpStatus.CONFLICT, "ORD-409", "Order already submitted");
        }
        return handleGenericException(e);
    }

    @ExceptionHandler(OrderCancellationConflictException.class)
    public ResponseEntity<ErrorResponse> handleOrderCancellationConflict(OrderCancellationConflictException e) {
        return error(HttpStatus.CONFLICT, "ORD-409", e.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "ORD-404", e.getMessage());
    }

    @ExceptionHandler(PositionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePositionNotFound(PositionNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "POS-404", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((m1, m2) -> m1 + ", " + m2)
                .orElse("Validation failed");

        return error(HttpStatus.UNPROCESSABLE_CONTENT, "VAL-422", message);
    }

    // Malformed JSON or an unknown enum value such as "side": "HOLD"
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "VAL-422", "Request body is missing or malformed");
    }

    // A path variable of the wrong type, such as an order ID that is not a UUID
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "VAL-422", "Invalid value for " + e.getName());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT-404", "No endpoint at " + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        logger.error("Unexpected exception occurred: ", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "SYS-500", "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(errorCode, message, LocalDateTime.now()));
    }
}
