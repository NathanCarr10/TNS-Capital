package com.neueda.leap.dtos;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * @param quantity          all the shares held, including those open SELL orders reserved
 * @param reservedQuantity  shares held for accepted SELL orders until they are filled, cancelled or rejected
 * @param availableQuantity shares that can be sold: quantity - reservedQuantity
 * @param marketValue quantity at the latest live price, or null when no live price is available
 */
public record PositionResponse(
                @Schema(requiredMode = REQUIRED) Long accountId,
                @Schema(requiredMode = REQUIRED) String symbol,
                @Schema(requiredMode = REQUIRED) Integer quantity,
                @Schema(requiredMode = REQUIRED) Integer reservedQuantity,
                @Schema(requiredMode = REQUIRED) Integer availableQuantity,
                @Schema(requiredMode = REQUIRED) BigDecimal averageCost,
                @Schema(requiredMode = REQUIRED, nullable = true) BigDecimal marketValue) {
}
