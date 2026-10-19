package com.neueda.leap.strategy.backtest;

import com.neueda.leap.strategy.rules.Signal;
import com.neueda.leap.strategy.rules.TradingRule;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a rule over daily closing prices as if it had traded them.
 *
 * Long only, like the live strategy: it starts in cash; on BUY it spends all
 * its cash on whole shares at that day's close; on SELL it sells them all at
 * that day's close. No fees or slippage. The benchmark buys as many whole
 * shares as it can on the first day and holds them.
 */
@Component
public class Backtester {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param closes      at least two days of closes, oldest first
     * @param initialCash positive starting cash
     */
    public BacktestResult run(String symbol, TradingRule rule, List<DailyClose> closes, BigDecimal initialCash) {
        if (closes.size() < 2) {
            throw new IllegalArgumentException("A backtest needs at least two days of prices");
        }
        List<Double> prices = closes.stream().map(day -> day.close().doubleValue()).toList();
        Portfolio strategy = new Portfolio(initialCash.doubleValue());
        Portfolio benchmark = new Portfolio(initialCash.doubleValue());
        benchmark.buy(prices.get(0));

        List<BacktestTrade> trades = new ArrayList<>();
        List<EquityPoint> curve = new ArrayList<>(closes.size());
        double peak = initialCash.doubleValue();
        double maxDrawdown = 0;
        for (int day = 0; day < closes.size(); day++) {
            double price = prices.get(day);
            Signal signal = rule.evaluate(prices.subList(0, day + 1));
            BacktestTrade trade = strategy.act(signal, closes.get(day), price);
            if (trade != null) {
                trades.add(trade);
            }
            double value = strategy.value(price);
            peak = Math.max(peak, value);
            maxDrawdown = Math.max(maxDrawdown, (peak - value) / peak);
            curve.add(new EquityPoint(closes.get(day).date(), money(value), money(benchmark.value(price))));
        }

        double lastPrice = prices.get(prices.size() - 1);
        return new BacktestResult(
                symbol,
                rule.describe(),
                closes.get(0).date(),
                closes.get(closes.size() - 1).date(),
                money(initialCash.doubleValue()),
                money(strategy.value(lastPrice)),
                percent(strategy.value(lastPrice) / initialCash.doubleValue() - 1),
                percent(benchmark.value(lastPrice) / initialCash.doubleValue() - 1),
                percent(maxDrawdown),
                strategy.roundTrips,
                strategy.winningRoundTrips,
                trades,
                curve);
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(double fraction) {
        return BigDecimal.valueOf(fraction).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    /** Cash and whole shares of the one instrument */
    private static final class Portfolio {
        private double cash;
        private int shares;
        private double costOfShares;
        private int roundTrips;
        private int winningRoundTrips;

        private Portfolio(double cash) {
            this.cash = cash;
        }

        double value(double price) {
            return cash + shares * price;
        }

        int buy(double price) {
            int quantity = (int) Math.floor(cash / price);
            cash -= quantity * price;
            shares += quantity;
            costOfShares = quantity * price;
            return quantity;
        }

        /** Buys only when out of the market, sells only when in it */
        BacktestTrade act(Signal signal, DailyClose day, double price) {
            if (signal == Signal.BUY && shares == 0) {
                int quantity = buy(price);
                return quantity == 0 ? null : new BacktestTrade(day.date(), "BUY", day.close(), quantity, null);
            }
            if (signal == Signal.SELL && shares > 0) {
                int quantity = shares;
                double profit = quantity * price - costOfShares;
                cash += quantity * price;
                shares = 0;
                roundTrips++;
                if (profit > 0) {
                    winningRoundTrips++;
                }
                return new BacktestTrade(day.date(), "SELL", day.close(), quantity, money(profit));
            }
            return null;
        }
    }
}
