package com.neueda.leap.controllers;
//this file defines how to communicate errors to HTTP clients with security best practices

import com.neueda.leap.dtos.ErrorResponse;
import com.neueda.leap.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Global exception handler that provides secure, consistent error responses.
 * - Each TradingException carries its error code (see the exceptions package);
 *   this class picks the HTTP status and a fixed, safe message for it
 * - Uses the error codes and HTTP statuses from section 21 of the specification;
 *   ACC-409, INS-409, ORD-404, ORD-422, POS-404, AUTH-403, NOT-404, REQ-405,
 *   REQ-415, PRC-503, MKT-503, SYS-409, SYS-500 and SYS-503 extend the catalog for cases it does not list
 * - Never exposes stack traces to clients
 * - Logs detailed information server-side for debugging
 * - Follows security best practices to prevent information leakage
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String ACCOUNT_CONFLICT = "ACC-409";
    private static final String ORDER_CONFLICT = "ORD-409";
    private static final String INVALID_INPUT = "VAL-422";

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        logger.warn("Account not found: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, e.getErrorCode(), "The requested account could not be found");
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException e) {
        logger.warn("Account not active: {}", e.getMessage());
        return error(HttpStatus.FORBIDDEN, e.getErrorCode(), "The account is not in an active state for this operation");
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAccountAlreadyExists(AccountAlreadyExistsException e) {
        logger.warn("Duplicate account: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, e.getErrorCode(), "An account with this account number already exists");
    }

    @ExceptionHandler(AccountDeletionConflictException.class)
    public ResponseEntity<ErrorResponse> handleAccountDeletionConflict(AccountDeletionConflictException e) {
        return error(HttpStatus.CONFLICT, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(DuplicateInstrumentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateInstrument(DuplicateInstrumentException e) {
        logger.warn("Duplicate instrument: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, e.getErrorCode(), "An instrument with this symbol already exists");
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(InstrumentNotFoundException e) {
        logger.warn("Instrument not found: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, e.getErrorCode(), "The requested instrument could not be found or is not tradable");
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e) {
        logger.warn("Insufficient funds: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, e.getErrorCode(), "The account does not have sufficient funds for this operation");
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientHoldings(InsufficientHoldingsException e) {
        logger.warn("Insufficient holdings: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, e.getErrorCode(), "The account does not have sufficient holdings for this operation");
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrder(DuplicateOrderException e) {
        logger.warn("Duplicate order detected: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, e.getErrorCode(), "An order with this idempotency key has already been submitted");
    }

    // Two requests with the same idempotency key can both pass the duplicate check;
    // the unique constraint then rejects the second one when it commits
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String detail = String.valueOf(cause.getMessage()).toLowerCase();
        if (detail.contains("idempotency_key")) {
            logger.warn("Duplicate order detected at commit: {}", cause.getMessage());
            return error(HttpStatus.CONFLICT, ORDER_CONFLICT, "An order with this idempotency key has already been submitted");
        }
        if (detail.contains("account_number")) {
            logger.warn("Duplicate account detected at commit: {}", cause.getMessage());
            return error(HttpStatus.CONFLICT, ACCOUNT_CONFLICT, "An account with this account number already exists");
        }
        return handleGenericException(e);
    }

    // Two requests changed the same account at once (e.g. a deposit while a fill
    // settles); the account's version column (NFR-02) rejected the later one, and
    // nothing it did was saved
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(OptimisticLockingFailureException e) {
        logger.warn("Concurrent update rejected: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "SYS-409",
                "The record was changed by another request at the same time; nothing was saved. Please try again");
    }

    // A limit order that would not fill at the live price; the message names both prices
    @ExceptionHandler(LimitNotMarketableException.class)
    public ResponseEntity<ErrorResponse> handleLimitNotMarketable(LimitNotMarketableException e) {
        logger.warn("Limit not marketable: {}", e.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(OrderCancellationConflictException.class)
    public ResponseEntity<ErrorResponse> handleOrderCancellationConflict(OrderCancellationConflictException e) {
        logger.warn("Order cancellation conflict: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, e.getErrorCode(), "Only NEW orders can be cancelled");
    }

    // Kafka did not take the order; it is stored as REJECTED and will not execute
    @ExceptionHandler(OrderPublishException.class)
    public ResponseEntity<ErrorResponse> handleOrderPublish(OrderPublishException e) {
        logger.error("Order could not be queued: {}", e.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getErrorCode(),
                "The order could not be queued and was not executed. Try again later with a new idempotency key");
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException e) {
        logger.warn("Order not found: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, e.getErrorCode(), "The requested order could not be found");
    }

    @ExceptionHandler(PositionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePositionNotFound(PositionNotFoundException e) {
        logger.warn("Position not found: {}", e.getMessage());
        return error(HttpStatus.NOT_FOUND, e.getErrorCode(), "The requested position could not be found");
    }

    @ExceptionHandler(PriceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handlePriceUnavailable(PriceUnavailableException e) {
        logger.warn("Price unavailable: {}", e.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getErrorCode(),
                "No live price is available for this instrument; include a price in the order");
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleMarketDataUnavailable(MarketDataUnavailableException e) {
        logger.warn("Market data unavailable: {}", e.getMessage());
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getErrorCode(),
                "Market data is unavailable right now; try again later");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("Validation failed");

        logger.warn("Validation error: {}", message);
        return error(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_INPUT, message);
    }

    // Thrown instead of MethodArgumentNotValidException when the handler also has
    // constraints on other parameters, e.g. @PathVariable @NotNull plus @Valid @RequestBody
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidationError(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .map(error -> error instanceof FieldError fieldError
                        ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                        : error.getDefaultMessage())
                .reduce((m1, m2) -> m1 + "; " + m2)
                .orElse("Validation failed");

        logger.warn("Validation error: {}", message);
        return error(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_INPUT, message);
    }

    // Malformed JSON or an unknown enum value such as "side": "HOLD"
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        logger.warn("Unreadable request body: {}", e.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_INPUT, "Request body is missing or malformed");
    }

    // A path variable of the wrong type, such as an order ID that is not a UUID
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        logger.warn("Invalid value for {}: {}", e.getName(), e.getValue());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_INPUT, "Invalid value for " + e.getName());
    }

    // A required query parameter is missing
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        logger.warn("Missing request parameter: {}", e.getParameterName());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_INPUT, "Missing required parameter " + e.getParameterName());
    }

    // For example PUT on an endpoint that only supports GET
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        logger.warn("Method not supported: {}", e.getMessage());
        return error(HttpStatus.METHOD_NOT_ALLOWED, "REQ-405", "HTTP method " + e.getMethod() + " is not supported here");
    }

    // For example a form or XML body sent to a JSON endpoint
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        logger.warn("Media type not supported: {}", e.getContentType());
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "REQ-415", "Request body must be application/json");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        logger.warn("Invalid argument: {}", e.getMessage());
        return error(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_INPUT, "The request contains invalid arguments");
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
                .body(new ErrorResponse(errorCode, message, LocalDateTime.now(ZoneOffset.UTC)));
    }
}
