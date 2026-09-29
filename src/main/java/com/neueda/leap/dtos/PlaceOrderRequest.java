// PlaceOrderRequest.java
package com.neueda.leap.dtos;

import jakarta.validation.constraints.*;
import com.neueda.leap.enums.OrderSide;

import java.math.BigDecimal;

/**
 * DTO for placing an order with comprehensive input validation.
 * Enforces business rules at the API boundary to prevent invalid data entry.
 */
public record PlaceOrderRequest(
        @NotNull(message = "Account ID is required")
        Long accountId,
        
        @NotNull(message = "Symbol is required")
        @NotBlank(message = "Symbol cannot be blank")
        @Pattern(regexp = "^[A-Z]{1,10}$", message = "Symbol must contain 1-10 uppercase letters")
        String symbol,
        
        @NotNull(message = "Order side is required")
        OrderSide side,
        
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        @Max(value = 1000000, message = "Quantity exceeds maximum allowed (1,000,000)")
        Integer quantity,
        
        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        @DecimalMax(value = "999999.99", message = "Price exceeds maximum allowed")
        BigDecimal price,
        
        @NotNull(message = "Idempotency key is required")
        @NotBlank(message = "Idempotency key cannot be blank")
        @Pattern(regexp = "^[a-zA-Z0-9_-]{1,100}$", message = "Invalid idempotency key format")
        String idempotencyKey) {
}