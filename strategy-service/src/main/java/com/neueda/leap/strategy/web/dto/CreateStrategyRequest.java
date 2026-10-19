package com.neueda.leap.strategy.web.dto;

import com.neueda.leap.strategy.rules.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * Body of POST /api/v1/strategies. The strategy starts PAUSED.
 *
 * @param name     what the trader calls it; letters, numbers, spaces and . , ' / _ - only,
 *                 so it is safe to show anywhere (OWASP A03)
 * @param symbol   the instrument it trades
 * @param ruleType MA_CROSSOVER or BOLLINGER
 * @param params   the rule's settings; missing ones take the defaults
 * @param quantity shares per order
 */
public record CreateStrategyRequest(
        @NotNull(message = "Name is required")
        @Pattern(regexp = "^[\\p{L}\\p{M}\\p{N} .,'/_-]{1,80}$",
                message = "Name must be 1-80 letters, numbers, spaces or . , ' / _ -")
        String name,

        @NotNull(message = "Symbol is required")
        @Pattern(regexp = "^[A-Z]{1,10}$", message = "Symbol must contain 1-10 uppercase letters")
        String symbol,

        @NotNull(message = "Rule type is required")
        RuleType ruleType,

        @Schema(description = "MA_CROSSOVER: shortWindow (default 20), longWindow (default 50). "
                + "BOLLINGER: window (default 20), width in standard deviations (default 2)",
                example = "{\"shortWindow\": 20, \"longWindow\": 50}")
        Map<String, Double> params,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 10000, message = "Quantity must be at most 10,000")
        Integer quantity) {
}
