package com.neueda.leap.strategy.backtest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outcome of a backtest.
 *
 * @param symbol              the instrument
 * @param rule                the rule, described for people
 * @param from                first day of price history used
 * @param to                  last day
 * @param initialCash         cash at the start
 * @param finalValue          the strategy's cash plus holdings at the last close
 * @param totalReturnPct      the strategy's return, in percent
 * @param buyAndHoldReturnPct the return of buying on the first day and holding, in percent
 * @param maxDrawdownPct      the largest fall from a peak of the strategy's value, in percent
 * @param roundTrips          completed buy-then-sell trades
 * @param winningRoundTrips   round trips that made money
 * @param trades              every trade, oldest first
 * @param equityCurve         both portfolios' value at every close, oldest first
 */
public record BacktestResult(
        String symbol,
        String rule,
        LocalDate from,
        LocalDate to,
        BigDecimal initialCash,
        BigDecimal finalValue,
        BigDecimal totalReturnPct,
        BigDecimal buyAndHoldReturnPct,
        BigDecimal maxDrawdownPct,
        int roundTrips,
        int winningRoundTrips,
        List<BacktestTrade> trades,
        List<EquityPoint> equityCurve) {
}
