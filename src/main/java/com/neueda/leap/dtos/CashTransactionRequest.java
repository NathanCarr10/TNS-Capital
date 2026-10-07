package com.neueda.leap.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Body for depositing cash to, or withdrawing cash from, an account.
 */
public record CashTransactionRequest(
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @DecimalMax(value = "1000000000.00", message = "Amount exceeds maximum allowed")
        @Digits(integer = 16, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount) {
}
