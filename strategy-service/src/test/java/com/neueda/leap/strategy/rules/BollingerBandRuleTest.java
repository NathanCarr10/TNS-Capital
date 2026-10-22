package com.neueda.leap.strategy.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Bollinger-band rule")
class BollingerBandRuleTest {
    // With n bars a close can be at most (n-1)/sqrt(n) deviations from their mean, so 10 bars can reach 2
    private final BollingerBandRule rule = new BollingerBandRule(10, 2);

    /** A gently oscillating price, so the bands have some width, then the given closes */
    private static List<Double> calmThen(double... closes) {
        List<Double> series = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            series.add(i % 2 == 0 ? 100.0 : 101.0);
        }
        for (double close : closes) {
            series.add(close);
        }
        return series;
    }

    @Test
    @DisplayName("Buys when the close drops below the lower band")
    void buysBelowLowerBand() {
        assertEquals(Signal.BUY, rule.evaluate(calmThen(90.0)));
    }

    @Test
    @DisplayName("Sells when the close rises above the upper band")
    void sellsAboveUpperBand() {
        assertEquals(Signal.SELL, rule.evaluate(calmThen(111.0)));
    }

    @Test
    @DisplayName("Holds inside the bands, and does not buy again while the price stays below")
    void holdsInsideBands() {
        assertEquals(Signal.HOLD, rule.evaluate(calmThen(100.5)));
        assertEquals(Signal.HOLD, rule.evaluate(calmThen(90.0, 85.0)));
    }

    @Test
    @DisplayName("Holds until it has the window plus one bar")
    void holdsDuringWarmUp() {
        assertEquals(11, rule.barsNeeded());
        assertEquals(Signal.HOLD, rule.evaluate(List.of(100.0, 101.0, 100.0, 101.0, 100.0, 101.0, 100.0, 101.0,
                100.0, 50.0)));
    }

    @ParameterizedTest(name = "window {0}, width {1}")
    @CsvSource({ "4, 2", "201, 2", "20, 0.4", "20, 4.5" })
    @DisplayName("Refuses settings out of range")
    void refusesBadSettings(int window, double width) {
        assertThrows(InvalidRuleException.class, () -> new BollingerBandRule(window, width));
    }

    @Test
    @DisplayName("Describes itself for people")
    void describesItself() {
        assertEquals("Bollinger bands (20, 2.0 sd) mean reversion", new BollingerBandRule(20, 2).describe());
    }
}
