package com.neueda.leap.strategy.performance;

import java.math.BigDecimal;

/**
 * How one set of filled orders did, valued at average cost.
 *
 * @param trades      filled orders
 * @param bought      what the BUYs cost
 * @param sold        what the SELLs raised
 * @param realizedPnl profit or loss on the shares sold, against their average cost
 * @param openCost    average cost of the shares still held
 */
public record BookPerformance(int trades, BigDecimal bought, BigDecimal sold, BigDecimal realizedPnl,
        BigDecimal openCost) {
}
