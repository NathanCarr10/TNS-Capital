package com.neueda.leap.strategy.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Moving-average crossover rule")
class MovingAverageCrossoverRuleTest {
    private final MovingAverageCrossoverRule rule = new MovingAverageCrossoverRule(2, 4);

    @Test
    @DisplayName("Buys on the bar where the short average crosses above the long one")
    void buysOnUpwardCross() {
        // SMA2 / SMA4 at the last two bars: 10 / 10.5 (below), then 11 / 10.5 (above)
        assertEquals(Signal.BUY, rule.evaluate(List.of(11.0, 11.0, 10.0, 10.0, 12.0)));
    }

    @Test
    @DisplayName("Sells on the bar where the short average crosses back below")
    void sellsOnDownwardCross() {
        assertEquals(Signal.SELL, rule.evaluate(List.of(9.0, 9.0, 10.0, 10.0, 8.0)));
    }

    @Test
    @DisplayName("Holds while the averages stay on the same side, so it trades once per trend")
    void holdsWithoutCross() {
        assertEquals(Signal.HOLD, rule.evaluate(List.of(10.0, 11.0, 12.0, 13.0, 14.0, 15.0)));
        assertEquals(Signal.HOLD, rule.evaluate(List.of(15.0, 14.0, 13.0, 12.0, 11.0)));
    }

    @Test
    @DisplayName("Holds until it has the long window plus one bar")
    void holdsDuringWarmUp() {
        assertEquals(5, rule.barsNeeded());
        assertEquals(Signal.HOLD, rule.evaluate(List.of(10.0, 10.0, 10.0, 12.0)));
    }

    @Test
    @DisplayName("Looks only at the last bars of a long series")
    void usesLatestBars() {
        List<Double> closes = new ArrayList<>(java.util.Collections.nCopies(100, 50.0));
        closes.addAll(List.of(11.0, 11.0, 10.0, 10.0, 12.0));

        assertEquals(Signal.BUY, rule.evaluate(closes));
    }

    @ParameterizedTest(name = "short {0}, long {1}")
    @CsvSource({ "1, 5", "5, 5", "10, 5", "20, 201" })
    @DisplayName("Refuses windows out of range")
    void refusesBadWindows(int shortWindow, int longWindow) {
        assertThrows(InvalidRuleException.class, () -> new MovingAverageCrossoverRule(shortWindow, longWindow));
    }

    @Test
    @DisplayName("Describes itself for people")
    void describesItself() {
        assertEquals("SMA 20 / SMA 50 crossover", new MovingAverageCrossoverRule(20, 50).describe());
    }
}
