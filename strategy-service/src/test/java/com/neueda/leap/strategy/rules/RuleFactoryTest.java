package com.neueda.leap.strategy.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RuleFactory")
class RuleFactoryTest {

    @Test
    @DisplayName("Builds each rule type from its settings")
    void buildsRules() {
        TradingRule crossover = RuleFactory.create(RuleType.MA_CROSSOVER, Map.of("shortWindow", 5, "longWindow", 15));
        TradingRule bands = RuleFactory.create(RuleType.BOLLINGER, Map.of("window", 10, "width", 1.5));

        assertInstanceOf(MovingAverageCrossoverRule.class, crossover);
        assertEquals(16, crossover.barsNeeded());
        assertInstanceOf(BollingerBandRule.class, bands);
        assertEquals("Bollinger bands (10, 1.5 sd) mean reversion", bands.describe());
    }

    @Test
    @DisplayName("Fills in missing settings with the defaults, as they are stored")
    void fillsDefaults() {
        assertEquals(Map.of("shortWindow", 20, "longWindow", 50), RuleFactory.withDefaults(RuleType.MA_CROSSOVER, null));
        assertEquals(Map.of("shortWindow", 10, "longWindow", 50),
                RuleFactory.withDefaults(RuleType.MA_CROSSOVER, Map.of("shortWindow", 10.0)));
        assertEquals(Map.of("window", 20, "width", 2.0), RuleFactory.withDefaults(RuleType.BOLLINGER, Map.of()));
    }

    @Test
    @DisplayName("Refuses fractional bar counts and settings out of range")
    void refusesBadSettings() {
        Map<String, Double> fractional = Map.of("shortWindow", 2.5);
        Map<String, Integer> crossed = Map.of("shortWindow", 60, "longWindow", 50);

        assertThrows(InvalidRuleException.class, () -> RuleFactory.create(RuleType.MA_CROSSOVER, fractional));
        assertThrows(InvalidRuleException.class, () -> RuleFactory.withDefaults(RuleType.MA_CROSSOVER, crossed));
    }
}
