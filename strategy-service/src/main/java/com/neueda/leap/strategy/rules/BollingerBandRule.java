package com.neueda.leap.strategy.rules;

import java.util.List;

/**
 * Bollinger-band mean reversion: the bands sit width standard deviations
 * above and below the moving average. BUY when the close drops below the
 * lower band (unusually cheap), SELL when it rises above the upper band
 * (unusually dear).
 *
 * As with the crossover, a signal fires only on the bar where the close
 * crosses the band.
 */
public final class BollingerBandRule implements TradingRule {
    private final int window;
    private final double width;

    /**
     * @throws InvalidRuleException unless 5 <= window <= 200 and 0.5 <= width <= 4
     */
    public BollingerBandRule(int window, double width) {
        if (window < 5 || window > 200 || width < 0.5 || width > 4) {
            throw new InvalidRuleException("window must be 5-200 and width 0.5-4 standard deviations");
        }
        this.window = window;
        this.width = width;
    }

    @Override
    public int barsNeeded() {
        return window + 1;
    }

    @Override
    public Signal evaluate(List<Double> closes) {
        if (closes.size() < barsNeeded()) {
            return Signal.HOLD;
        }
        int last = closes.size() - 1;
        if (crossesBelowLowerBand(closes, last)) {
            return Signal.BUY;
        }
        if (crossesAboveUpperBand(closes, last)) {
            return Signal.SELL;
        }
        return Signal.HOLD;
    }

    private boolean crossesBelowLowerBand(List<Double> closes, int last) {
        return closes.get(last - 1) >= lowerBand(closes, last - 1) && closes.get(last) < lowerBand(closes, last);
    }

    private boolean crossesAboveUpperBand(List<Double> closes, int last) {
        return closes.get(last - 1) <= upperBand(closes, last - 1) && closes.get(last) > upperBand(closes, last);
    }

    private double lowerBand(List<Double> closes, int end) {
        return Indicators.sma(closes, end, window) - width * Indicators.standardDeviation(closes, end, window);
    }

    private double upperBand(List<Double> closes, int end) {
        return Indicators.sma(closes, end, window) + width * Indicators.standardDeviation(closes, end, window);
    }

    @Override
    public String describe() {
        return "Bollinger bands (" + window + ", " + width + " sd) mean reversion";
    }
}
