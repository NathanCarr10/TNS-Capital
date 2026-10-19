package com.neueda.leap.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * Body of POST /api/v1/market-data/replays.
 *
 * @param symbol        the instrument to replay, e.g. SPY
 * @param date          a past US trading day
 * @param barsPerSecond how many 1-minute bars to publish a second (default 10:
 *                      a 390-bar session in under a minute)
 */
public record StartReplayRequest(
        @NotNull(message = "Symbol is required")
        @Pattern(regexp = "^[A-Z]{1,10}$", message = "Symbol must contain 1-10 uppercase letters")
        String symbol,

        @NotNull(message = "Date is required")
        @Past(message = "Date must be a past trading day")
        LocalDate date,

        @Min(value = 1, message = "barsPerSecond must be at least 1")
        @Max(value = 50, message = "barsPerSecond must be at most 50")
        Integer barsPerSecond) {

    public static final int DEFAULT_BARS_PER_SECOND = 10;

    public int barsPerSecondOrDefault() {
        return barsPerSecond == null ? DEFAULT_BARS_PER_SECOND : barsPerSecond;
    }
}
