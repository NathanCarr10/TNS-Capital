package com.neueda.leap.dtos;

import com.neueda.leap.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record AccountResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String accountNumber,
        @Schema(requiredMode = REQUIRED) String holderName,
        @Schema(requiredMode = REQUIRED) BigDecimal cashBalance,
        @Schema(requiredMode = REQUIRED) AccountStatus status,
        @Schema(requiredMode = REQUIRED) Instant lastUpdated) {
}