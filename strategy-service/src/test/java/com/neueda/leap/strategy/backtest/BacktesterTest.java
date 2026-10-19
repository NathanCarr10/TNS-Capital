package com.neueda.leap.strategy.backtest;

import com.neueda.leap.strategy.rules.Signal;
import com.neueda.leap.strategy.rules.TradingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Backtester")
class BacktesterTest {
    private static final LocalDate START = LocalDate.of(2024, 1, 2);
    private final Backtester backtester = new Backtester();

    private static List<DailyClose> closes(double... prices) {
        return IntStream.range(0, prices.length)
                .mapToObj(i -> new DailyClose(START.plusDays(i), BigDecimal.valueOf(prices[i])))
                .toList();
    }

    /** Signals on fixed days: day index -> signal */
    private static TradingRule scripted(Map<Integer, Signal> signals) {
        return new TradingRule() {
            @Override
            public int barsNeeded() {
                return 1;
            }

            @Override
            public Signal evaluate(List<Double> closes) {
                return signals.getOrDefault(closes.size() - 1, Signal.HOLD);
            }

            @Override
            public String describe() {
                return "scripted";
            }
        };
    }

    @Test
    @DisplayName("Buys whole shares with all its cash on BUY and sells them all on SELL, at the close")
    void tradesAtTheClose() {
        // Day 1: 1000 / 30 = 33 shares, 10 left; day 3: sold at 40 -> 1330
        BacktestResult result = backtester.run("SPY", scripted(Map.of(1, Signal.BUY, 3, Signal.SELL)),
                closes(25, 30, 35, 40, 20), BigDecimal.valueOf(1000));

        assertEquals(List.of(
                new BacktestTrade(START.plusDays(1), "BUY", BigDecimal.valueOf(30.0), 33, null),
                new BacktestTrade(START.plusDays(3), "SELL", BigDecimal.valueOf(40.0), 33, new BigDecimal("330.00"))),
                result.trades());
        assertEquals(new BigDecimal("1330.00"), result.finalValue());
        assertEquals(new BigDecimal("33.00"), result.totalReturnPct());
        assertEquals(1, result.roundTrips());
        assertEquals(1, result.winningRoundTrips());
    }

    @Test
    @DisplayName("Compares with buying on the first day and holding")
    void comparesWithBuyAndHold() {
        // 40 shares at 25, worth 20 each at the end: 800, -20%
        BacktestResult result = backtester.run("SPY", scripted(Map.of()), closes(25, 30, 35, 40, 20),
                BigDecimal.valueOf(1000));

        assertEquals(new BigDecimal("-20.00"), result.buyAndHoldReturnPct());
        assertEquals(new BigDecimal("0.00"), result.totalReturnPct());
        assertEquals(new BigDecimal("800.00"), result.equityCurve().get(4).buyAndHold());
        assertEquals(new BigDecimal("1000.00"), result.equityCurve().get(4).strategy());
        assertEquals(5, result.equityCurve().size());
    }

    @Test
    @DisplayName("Measures the largest fall from a peak")
    void measuresDrawdown() {
        // In the market from day 0: 100 shares, peak 2000 on day 1, low 1000 on day 2: -50%
        BacktestResult result = backtester.run("SPY", scripted(Map.of(0, Signal.BUY)), closes(10, 20, 10, 15),
                BigDecimal.valueOf(1000));

        assertEquals(new BigDecimal("50.00"), result.maxDrawdownPct());
        assertEquals(START, result.from());
        assertEquals(START.plusDays(3), result.to());
    }

    @Test
    @DisplayName("Ignores a BUY while in the market, a SELL while out of it, and a BUY it cannot afford")
    void ignoresSignalsThatDoNotApply() {
        BacktestResult result = backtester.run("SPY",
                scripted(Map.of(0, Signal.SELL, 1, Signal.BUY, 2, Signal.BUY, 3, Signal.SELL)),
                closes(5000, 10, 12, 8), BigDecimal.valueOf(100));

        assertEquals(2, result.trades().size());
        assertNull(result.trades().get(0).profit());
        assertEquals(new BigDecimal("-20.00"), result.trades().get(1).profit());
        assertEquals(0, result.winningRoundTrips());
        // Cannot buy a single share at 5000 with 100: the benchmark holds cash only
        assertEquals(new BigDecimal("0.00"), result.buyAndHoldReturnPct());
    }

    @Test
    @DisplayName("Needs at least two days of prices")
    void needsTwoDays() {
        TradingRule rule = scripted(Map.of());
        List<DailyClose> oneDay = closes(10);
        BigDecimal cash = BigDecimal.TEN;

        assertThrows(IllegalArgumentException.class, () -> backtester.run("SPY", rule, oneDay, cash));
    }
}
