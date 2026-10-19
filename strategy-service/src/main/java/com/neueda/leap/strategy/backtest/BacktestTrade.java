package com.neueda.leap.strategy.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A trade the strategy would have made, at the close of the day its rule fired.
 *
 * @param profit for a SELL, what the round trip made (sell value minus buy
 *               value); null for a BUY
 */
public record BacktestTrade(LocalDate date, String side, BigDecimal price, int quantity, BigDecimal profit) {
}
