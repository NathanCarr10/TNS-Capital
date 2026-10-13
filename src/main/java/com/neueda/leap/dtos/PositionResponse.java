package com.neueda.leap.dtos;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @param marketValue quantity at the latest live price, or null when no live price is available
 */
public record PositionResponse(
                @Schema(requiredMode = REQUIRED) Long accountId,
                @Schema(requiredMode = REQUIRED) String symbol,
                @Schema(requiredMode = REQUIRED) Integer quantity,
                @Schema(requiredMode = REQUIRED) BigDecimal averageCost,
                @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal marketValue) {
}
