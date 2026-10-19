package com.neueda.leap.strategy.rules;

import java.util.List;

/**
 * Technical indicators over a window of closing prices.
 */
public final class Indicators {

    private Indicators() {
    }

    /**
     * Simple moving average of the window closes ending at index end (inclusive).
     */
    public static double sma(List<Double> closes, int end, int window) {
        double sum = 0;
        for (int i = end - window + 1; i <= end; i++) {
            sum += closes.get(i);
        }
        return sum / window;
    }

    /**
     * Population standard deviation of the window closes ending at index end
     * (inclusive), as Bollinger bands use it.
     */
    public static double standardDeviation(List<Double> closes, int end, int window) {
        double mean = sma(closes, end, window);
        double squares = 0;
        for (int i = end - window + 1; i <= end; i++) {
            double difference = closes.get(i) - mean;
            squares += difference * difference;
        }
        return Math.sqrt(squares / window);
    }
}
