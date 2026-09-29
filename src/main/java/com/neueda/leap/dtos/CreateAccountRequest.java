package com.neueda.leap.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank(message = "Account ID cannot be blank")
        String accountId,
        
        @NotBlank(message = "Holder name cannot be blank")
        String holderName,
        
        @NotNull(message = "Cash balance cannot be null")
        @DecimalMin(value = "0.0", inclusive = true, message = "Cash balance must be non-negative")
        BigDecimal cashBalance) {
}
