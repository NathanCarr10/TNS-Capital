package com.neueda.leap.strategy.rules;

import java.util.List;

/**
 * Moving-average crossover: BUY when the short moving average crosses above
 * the long one (an uptrend starting), SELL when it crosses back below.
 *
 * A signal fires only on the bar where the cross happens, not on every bar
 * the short average stays above, so the strategy trades once per trend.
 */
public final class MovingAverageCrossoverRule implements TradingRule {
    private final int shortWindow;
    private final int longWindow;

    /**
     * @throws InvalidRuleException unless 2 <= shortWindow < longWindow <= 200
     */
    public MovingAverageCrossoverRule(int shortWindow, int longWindow) {
        if (shortWindow < 2 || longWindow > 200 || shortWindow >= longWindow) {
            throw new InvalidRuleException(
                    "shortWindow and longWindow must satisfy 2 <= shortWindow < longWindow <= 200");
        }
        this.shortWindow = shortWindow;
        this.longWindow = longWindow;
    }

    @Override
    public int barsNeeded() {
        // The long average at this bar and at the one before, to see the cross
        return longWindow + 1;
    }

    @Override
    public Signal evaluate(List<Double> closes) {
        if (closes.size() < barsNeeded()) {
            return Signal.HOLD;
        }
        int last = closes.size() - 1;
        double gapNow = Indicators.sma(closes, last, shortWindow) - Indicators.sma(closes, last, longWindow);
        double gapBefore = Indicators.sma(closes, last - 1, shortWindow)
                - Indicators.sma(closes, last - 1, longWindow);
        if (gapBefore <= 0 && gapNow > 0) {
            return Signal.BUY;
        }
        if (gapBefore >= 0 && gapNow < 0) {
            return Signal.SELL;
        }
        return Signal.HOLD;
    }

    @Override
    public String describe() {
        return "SMA " + shortWindow + " / SMA " + longWindow + " crossover";
    }
}
