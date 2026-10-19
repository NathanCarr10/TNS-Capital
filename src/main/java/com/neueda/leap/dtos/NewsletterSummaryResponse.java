package com.neueda.leap.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * One newsletter issue in GET /api/v1/newsletters, without its contents.
 */
public record NewsletterSummaryResponse(
        @Schema(requiredMode = REQUIRED) long id,
        @Schema(requiredMode = REQUIRED, description = "Monday of the week covered") LocalDate weekStart,
        @Schema(requiredMode = REQUIRED) Instant generatedAt) {
}
