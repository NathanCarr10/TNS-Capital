package com.neueda.leap.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A news headline about an instrument, from GET /api/v1/prices/{symbol}/news.
 * The headline comes from a third party: show it as text, never as HTML.
 */
public record NewsHeadlineResponse(
        @Schema(requiredMode = REQUIRED, description = "Plain text") String headline,
        String source,
        @Schema(requiredMode = REQUIRED, description = "Link to the full article; always https") String url,
        Instant publishedAt) {
}
