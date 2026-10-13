package com.neueda.leap.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record InstrumentResponse(
        @Schema(requiredMode = REQUIRED) String symbol,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED) String assetClass,
        @Schema(requiredMode = REQUIRED) String currency,
        @Schema(requiredMode = REQUIRED) boolean tradable) {
}
