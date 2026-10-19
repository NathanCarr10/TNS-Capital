package com.neueda.leap.dtos;

import com.neueda.leap.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @param cashBalance   all the cash in the account, including what open orders reserved
 * @param reservedCash  cash held for accepted BUY orders until they are filled, cancelled or rejected
 * @param availableCash cash that can be traded or withdrawn: cashBalance - reservedCash
 */
public record AccountResponse(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String accountNumber,
        @Schema(requiredMode = REQUIRED) String holderName,
        @Schema(requiredMode = REQUIRED) BigDecimal cashBalance,
        @Schema(requiredMode = REQUIRED) BigDecimal reservedCash,
        @Schema(requiredMode = REQUIRED) BigDecimal availableCash,
        @Schema(requiredMode = REQUIRED) AccountStatus status,
        @Schema(requiredMode = REQUIRED) Instant openedOn,
        @Schema(requiredMode = REQUIRED) Instant lastUpdated) {
}