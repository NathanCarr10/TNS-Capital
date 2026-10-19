package com.neueda.leap.strategy.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Value of the strategy's and of the buy-and-hold portfolio at a day's close.
 */
public record EquityPoint(LocalDate date, BigDecimal strategy, BigDecimal buyAndHold) {
}
