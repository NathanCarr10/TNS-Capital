package com.neueda.leap.strategy.web.dto;

import com.neueda.leap.strategy.rules.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Body of POST /api/v1/strategies/backtests.
 *
 * @param from        first day; default five years before to
 * @param to          last day; default today
 * @param initialCash starting cash; default 10,000
 */
public record BacktestRequest(
        @NotNull(message = "Symbol is required")
        @Pattern(regexp = "^[A-Z]{1,10}$", message = "Symbol must contain 1-10 uppercase letters")
        String symbol,

        @NotNull(message = "Rule type is required")
        RuleType ruleType,

        @Schema(description = "As for creating a strategy", example = "{\"shortWindow\": 20, \"longWindow\": 50}")
        Map<String, Double> params,

        @PastOrPresent(message = "from must not be in the future")
        LocalDate from,

        @PastOrPresent(message = "to must not be in the future")
        LocalDate to,

        @DecimalMin(value = "100", message = "initialCash must be at least 100")
        @DecimalMax(value = "100000000", message = "initialCash must be at most 100,000,000")
        BigDecimal initialCash) {
}
