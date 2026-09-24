package com.neueda.leap.controllers;
//this file defines how to communicate errors to HTTP clients

import com.neueda.leap.dtos.ErrorResponse;
import com.neueda.leap.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;


@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "ACCOUNT_NOT_FOUND",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "ACCOUNT_NOT_ACTIVE",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(InstrumentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(
                        "INSTRUMENT_NOT_FOUND",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrder(DuplicateOrderException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "DUPLICATE_ORDER",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INSUFFICIENT_FUNDS",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(InsufficientHoldingsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientHoldings(InsufficientHoldingsException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "INSUFFICIENT_HOLDINGS",
                        e.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((m1, m2) -> m1 + ", " + m2)
                .orElse("Validation failed");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "VALIDATION_ERROR",
                        message,
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        logger.error("Unexpected exception occurred: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred",
                        LocalDateTime.now()
                ));
    }

}
