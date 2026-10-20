package com.neueda.leap.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A weekly newsletter issue with its movers, or a preview of one.
 */
public record NewsletterResponse(
        @Schema(requiredMode = REQUIRED, nullable = true, description = "Null for a preview, which is not saved") Long id,
        @Schema(requiredMode = REQUIRED, description = "Monday of the week covered") LocalDate weekStart,
        @Schema(requiredMode = REQUIRED) Instant generatedAt,
        @Schema(requiredMode = REQUIRED, description = "Biggest rises, best first") List<NewsletterMoverResponse> gainers,
        @Schema(requiredMode = REQUIRED, description = "Biggest falls, worst first") List<NewsletterMoverResponse> losers) {
}
