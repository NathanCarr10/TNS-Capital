package com.neueda.leap.strategy.web.dto;

import com.neueda.leap.strategy.domain.StrategyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Body of PUT /api/v1/strategies/{id}/status: ACTIVE switches it on, PAUSED off.
 */
public record StatusChangeRequest(
        @Schema(allowableValues = { "ACTIVE", "PAUSED" })
        @NotNull(message = "Status is required")
        StrategyStatus status) {
}
