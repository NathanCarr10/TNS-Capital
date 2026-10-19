package com.neueda.leap.strategy.rules;

import java.util.List;

/**
 * A rules-based trading strategy (spec 13.2: moving-average crossovers,
 * Bollinger-band triggers). Each rule is its own class behind this interface,
 * like the API's BUY and SELL order strategies, so the backtester and the live
 * engine run any rule the same way.
 *
 * Rules are stateless: they look only at the closing prices they are given.
 */
public interface TradingRule {

    /**
     * @return how many closes the rule needs before it can signal
     */
    int barsNeeded();

    /**
     * Decides what to do at the last of the closes.
     *
     * @param closes closing prices, oldest first; only the last barsNeeded() are used
     * @return BUY or SELL when the rule triggers at the last close, otherwise HOLD
     *         (also HOLD while there are fewer than barsNeeded() closes)
     */
    Signal evaluate(List<Double> closes);

    /**
     * @return a short description for people, e.g. "SMA 20 / SMA 50 crossover"
     */
    String describe();
}
