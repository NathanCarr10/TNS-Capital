package com.neueda.leap.dtos;

import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record OrderHistoryResponse(
        @Schema(requiredMode = REQUIRED) UUID orderId,
        @Schema(requiredMode = REQUIRED) Long accountId,
        @Schema(requiredMode = REQUIRED) String symbol,
        @Schema(requiredMode = REQUIRED) OrderSide side,
        @Schema(requiredMode = REQUIRED) Integer quantity,
        @Schema(requiredMode = REQUIRED) BigDecimal price,
        @Schema(requiredMode = REQUIRED) OrderStatus status,
        @Schema(requiredMode = REQUIRED) Instant orderCreatedOn,
        @Schema(requiredMode = REQUIRED) Instant deletedOn) {
}
