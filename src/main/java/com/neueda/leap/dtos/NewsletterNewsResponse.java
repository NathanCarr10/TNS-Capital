package com.neueda.leap.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A news headline about one of the movers in a newsletter issue. The headline
 * comes from a third party: show it as text, never as HTML.
 */
public record NewsletterNewsResponse(
        @Schema(requiredMode = REQUIRED, description = "The mover the headline is about") String symbol,
        @Schema(requiredMode = REQUIRED, description = "Plain text") String headline,
        String source,
        @Schema(requiredMode = REQUIRED, description = "Link to the full article; always https") String url,
        Instant publishedAt) {
}
