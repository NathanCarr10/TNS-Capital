package com.neueda.leap.broker;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SimulatedMarketTest {
    private static BrokerProperties properties(long minMs, long maxMs, int bps) {
        return new BrokerProperties(Duration.ofMillis(minMs), Duration.ofMillis(maxMs), bps, "executions", "SIM");
    }

    @ParameterizedTest
    @EnumSource(Side.class)
    @DisplayName("never fills worse than the limit, nor better than the maximum improvement")
    void priceStaysBetweenLimitAndMaxImprovement(Side side) {
        SimulatedMarket market = new SimulatedMarket(properties(0, 0, 50), new Random(7));
        BigDecimal limit = new BigDecimal("100.00");

        for (int i = 0; i < 1000; i++) {
            BigDecimal price = market.fillPrice(side, limit);
            assertEquals(2, price.scale());
            if (side == Side.BUY) {
                assertTrue(price.compareTo(limit) <= 0 && price.compareTo(new BigDecimal("99.50")) >= 0, price.toString());
            } else {
                assertTrue(price.compareTo(limit) >= 0 && price.compareTo(new BigDecimal("100.50")) <= 0, price.toString());
            }
        }
    }

    @Test
    @DisplayName("fills exactly at the limit when improvement is off")
    void noImprovement() {
        SimulatedMarket market = new SimulatedMarket(properties(0, 0, 0), new Random(7));

        assertEquals(new BigDecimal("25.50"), market.fillPrice(Side.BUY, new BigDecimal("25.50")));
        assertEquals(new BigDecimal("25.50"), market.fillPrice(Side.SELL, new BigDecimal("25.50")));
    }

    @Test
    @DisplayName("fills at the market price when the limit allows it")
    void fillsAtMarketPrice() {
        SimulatedMarket market = new SimulatedMarket(properties(0, 0, 50), new Random(7));

        // A BUY limit above the market and a SELL limit below it get the market price
        assertEquals(new BigDecimal("227.47"),
                market.fillPrice(Side.BUY, new BigDecimal("230.00"), new BigDecimal("227.47")));
        assertEquals(new BigDecimal("227.47"),
                market.fillPrice(Side.SELL, new BigDecimal("225.00"), new BigDecimal("227.47")));
    }

    @Test
    @DisplayName("never fills worse than the limit when the market has moved past it")
    void marketPriceCappedByLimit() {
        SimulatedMarket market = new SimulatedMarket(properties(0, 0, 50), new Random(7));

        assertEquals(new BigDecimal("1.00"),
                market.fillPrice(Side.BUY, new BigDecimal("1.00"), new BigDecimal("227.47")));
        assertEquals(new BigDecimal("10000.00"),
                market.fillPrice(Side.SELL, new BigDecimal("10000.00"), new BigDecimal("227.47")));
    }

    @Test
    @DisplayName("rounds a market price to cents in the trader's favour, never past the limit")
    void marketPriceRounding() {
        SimulatedMarket market = new SimulatedMarket(properties(0, 0, 0), new Random(7));

        assertEquals(new BigDecimal("227.46"),
                market.fillPrice(Side.BUY, new BigDecimal("230.00"), new BigDecimal("227.4651")));
        assertEquals(new BigDecimal("227.47"),
                market.fillPrice(Side.SELL, new BigDecimal("225.00"), new BigDecimal("227.4651")));
    }

    @Test
    @DisplayName("without a market price, fills relative to the limit as before")
    void noMarketPrice() {
        SimulatedMarket market = new SimulatedMarket(properties(0, 0, 0), new Random(7));

        assertEquals(new BigDecimal("25.50"), market.fillPrice(Side.BUY, new BigDecimal("25.50"), null));
    }

    @Test
    @DisplayName("picks a delay inside the configured range")
    void delayInRange() {
        SimulatedMarket market = new SimulatedMarket(properties(500, 2000, 0), new Random(7));

        for (int i = 0; i < 1000; i++) {
            long delay = market.fillDelay().toMillis();
            assertTrue(delay >= 500 && delay <= 2000, "delay " + delay);
        }
        assertEquals(Duration.ZERO, new SimulatedMarket(properties(0, 0, 0)).fillDelay());
    }

    @Test
    @DisplayName("refuses a max delay shorter than the min delay")
    void rejectsBackwardsDelays() {
        assertThrows(IllegalArgumentException.class, () -> properties(2000, 500, 0));
    }
}
