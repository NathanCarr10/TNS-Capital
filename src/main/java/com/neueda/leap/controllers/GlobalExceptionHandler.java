package com.neueda.leap.controllers;
//this file defines how to communicate errors to HTTP clients with security best practices

import com.neueda.leap.dtos.ErrorResponse;
import com.neueda.leap.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

/**
 * Global exception handler that provides secure, consistent error responses.
 * - Uses the error codes and HTTP statuses from section 21 of the specification;
 *   ACC-409, ORD-404, ORD-503, POS-404, AUTH-403, NOT-404 and SYS-500 extend the catalog
 *   for cases it does not list
 * - Never exposes stack traces to clients
 * - Logs detailed information server-side for debugging
 * - Follows security best practices to prevent information leakage
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        logger.warn("Account not found: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, "ACC-404", "The requested account could not be found");
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException e) {
        logger.warn("Account not active: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, "ACC-403", "The account is not in an active state for this operation");
    }

    @ExceptionHandler(AccountDeletionConflictException.class)
    public ResponseEntity<ErrorResponse> handleAccountDeletionConflict(AccountDeletionConflictException e) {
        return error(HttpStatus.CONFLICT, "ACC-409", e.getMessage());
    }

    @ExceptionHandler(DuplicateInstrumentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateInstrument(DuplicateInstrumentException e) {
        logger.warn("Duplicate instrument: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "INS-409", "An instrument with this symbol already exists");
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(InstrumentNotFoundException e) {
        logger.warn("Instrument not found: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, "INS-404", "The requested instrument could not be found or is not tradable");
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e) {
        logger.warn("Insufficient funds: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "ORD-400", "The account does not have sufficient funds for this operation");
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientHoldings(InsufficientHoldingsException e) {
        logger.warn("Insufficient holdings: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "ORD-409", "The account does not have sufficient holdings for this operation");
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrder(DuplicateOrderException e) {
        logger.warn("Duplicate order detected: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "ORD-409", "An order with this idempotency key has already been submitted");
    }

    // Two requests with the same idempotency key can both pass the duplicate check;
    // the unique constraint then rejects the second one when it commits
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String detail = String.valueOf(cause.getMessage()).toLowerCase();
        if (detail.contains("idempotency_key")) {
            logger.warn("Duplicate order detected at commit: {}", cause.getMessage());
            return error(HttpStatus.CONFLICT, "ORD-409", "An order with this idempotency key has already been submitted");
        }
        return handleGenericException(e);
    }

    @ExceptionHandler(OrderCancellationConflictException.class)
    public ResponseEntity<ErrorResponse> handleOrderCancellationConflict(OrderCancellationConflictException e) {
        logger.warn("Order cancellation conflict: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "ORD-409", "Only NEW orders can be cancelled");
    }

    // The order was valid but Kafka did not acknowledge it, so it was not accepted
    @ExceptionHandler(OrderSubmissionException.class)
    public ResponseEntity<ErrorResponse> handleOrderSubmission(OrderSubmissionException e) {
        logger.error("Order could not be queued: {}", e.getMessage(), e);
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ORD-503",
                "The order could not be accepted right now. Please try again.");
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException e) {
        logger.warn("Order not found: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, "ORD-404", "The requested order could not be found");
    }

    @ExceptionHandler(PositionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePositionNotFound(PositionNotFoundException e) {
        logger.warn("Position not found: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, "POS-404", "The requested position could not be found");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("Validation failed");

        logger.warn("Validation error: {}", message);
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", message);
    }

    // Malformed JSON or an unknown enum value such as "side": "HOLD"
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        logger.warn("Unreadable request body: {}", e.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Request body is missing or malformed");
    }

    // A path variable of the wrong type, such as an order ID that is not a UUID
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        logger.warn("Invalid value for {}: {}", e.getName(), e.getValue());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid value for " + e.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("Invalid argument: {}", e.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "The request contains invalid arguments");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT-404", "Resource not found: " + e.getResourcePath());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        logger.warn("Access denied: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, "AUTH-403", "You do not have permission to access this resource");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        // Log full details server-side for debugging (but never expose to client)
        logger.error("Unexpected exception occurred: ", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "SYS-500",
                "An unexpected error occurred. Please contact support with error timestamp if problem persists.");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String errorCode, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(errorCode, message, LocalDateTime.now()));
    }
}
