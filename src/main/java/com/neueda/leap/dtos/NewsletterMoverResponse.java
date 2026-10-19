package com.neueda.leap.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * One of the week's biggest movers in a newsletter issue.
 */
public record NewsletterMoverResponse(
        @Schema(requiredMode = REQUIRED) String symbol,
        @Schema(requiredMode = REQUIRED) String name,
        @Schema(requiredMode = REQUIRED, description = "Close of the last trading day before the week") BigDecimal startClose,
        @Schema(requiredMode = REQUIRED, description = "Latest close of the week") BigDecimal endClose,
        @Schema(requiredMode = REQUIRED, description = "Change over the week in percent, e.g. -4.25") BigDecimal changePct) {
}
