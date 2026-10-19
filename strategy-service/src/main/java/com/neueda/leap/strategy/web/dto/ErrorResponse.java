package com.neueda.leap.strategy.web.dto;

import java.time.Instant;

/**
 * The platform's error envelope, as the trading API and auth service return it.
 */
public record ErrorResponse(String errorCode, String message, Instant timestamp) {
}
