package com.neueda.leap.strategy.web;

import com.neueda.leap.strategy.errors.StrategyException;
import com.neueda.leap.strategy.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Returns every failure in the platform's error envelope, with the error codes
 * of the spec's catalogue, as the trading API does. Unexpected errors are
 * logged and hidden behind SYS-500.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String INVALID_INPUT = "VAL-422";
    private static final Map<String, HttpStatus> STATUS_BY_PREFIX = Map.of(
            "403", HttpStatus.FORBIDDEN,
            "404", HttpStatus.NOT_FOUND,
            "409", HttpStatus.CONFLICT,
            "422", HttpStatus.UNPROCESSABLE_ENTITY);

    @ExceptionHandler(StrategyException.class)
    public ResponseEntity<ErrorResponse> handleStrategy(StrategyException e) {
        String code = e.getErrorCode();
        HttpStatus status = STATUS_BY_PREFIX.getOrDefault(code.substring(code.indexOf('-') + 1),
                HttpStatus.BAD_REQUEST);
        return error(status, code, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_INPUT, message);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class })
    public ResponseEntity<ErrorResponse> handleUnreadable(Exception e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_INPUT, "The request is missing a value or has one of the wrong type");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConflict(OptimisticLockingFailureException e) {
        return error(HttpStatus.CONFLICT, "SYS-409", "The strategy was changed at the same time; try again");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "REQ-405", "HTTP method " + e.getMethod() + " is not supported here");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return error(HttpStatus.NOT_FOUND, "NOT-404", "Resource not found: " + e.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "SYS-500", "An unexpected error occurred");
    }

    private static ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, Instant.now()));
    }
}
