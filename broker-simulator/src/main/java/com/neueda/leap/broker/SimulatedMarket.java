package com.neueda.leap.broker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Random;

/**
 * How the simulated market treats an order: how long it takes to fill and at
 * what price. Every order is a limit order filled in full, never worse than
 * its limit: a BUY at or below it, a SELL at or above it.
 *
 * With a market price (the trading API's live price when it sent the order)
 * the order fills at that price, capped by the limit if the market has moved
 * past it since. Without one (no live prices, e.g. local development) it
 * fills at its limit, improved by up to broker.max-price-improvement-bps.
 */
@Component
public class SimulatedMarket {
    private static final BigDecimal BPS = new BigDecimal("10000");

    private final BrokerProperties properties;
    private final Random random;

    @Autowired
    public SimulatedMarket(BrokerProperties properties) {
        this(properties, new Random());
    }

    SimulatedMarket(BrokerProperties properties, Random random) {
        this.properties = properties;
        this.random = random;
    }

    /** A random delay between broker.min-delay and broker.max-delay */
    public Duration fillDelay() {
        long min = properties.minDelay().toMillis();
        long max = properties.maxDelay().toMillis();
        return Duration.ofMillis(min == max ? min : min + random.nextLong(max - min + 1));
    }

    /**
     * The market price if the limit allows it, otherwise the limit; with no
     * market price, {@link #fillPrice(Side, BigDecimal)}.
     */
    public BigDecimal fillPrice(Side side, BigDecimal limitPrice, BigDecimal marketPrice) {
        if (marketPrice == null) {
            return fillPrice(side, limitPrice);
        }
        BigDecimal price = side == Side.BUY ? marketPrice.min(limitPrice) : marketPrice.max(limitPrice);
        RoundingMode towardsLimit = side == Side.BUY ? RoundingMode.FLOOR : RoundingMode.CEILING;
        return price.setScale(2, towardsLimit);
    }

    /** The limit price improved by 0 to broker.max-price-improvement-bps, rounded to cents */
    public BigDecimal fillPrice(Side side, BigDecimal limitPrice) {
        BigDecimal improvement = limitPrice
                .multiply(BigDecimal.valueOf(random.nextInt(properties.maxPriceImprovementBps() + 1)))
                .divide(BPS);
        BigDecimal price = side == Side.BUY ? limitPrice.subtract(improvement) : limitPrice.add(improvement);
        // Rounding towards the limit keeps a fill from ever being worse than the limit
        RoundingMode towardsLimit = side == Side.BUY ? RoundingMode.CEILING : RoundingMode.FLOOR;
        return price.setScale(2, towardsLimit).max(new BigDecimal("0.01"));
    }
}
