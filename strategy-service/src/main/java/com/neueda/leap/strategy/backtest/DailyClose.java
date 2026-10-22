package com.neueda.leap.strategy.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trading day's closing price, from analytics.fact_daily_prices.
 */
public record DailyClose(LocalDate date, BigDecimal close) {
}
