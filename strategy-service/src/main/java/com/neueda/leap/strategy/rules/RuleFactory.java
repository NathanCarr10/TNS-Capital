package com.neueda.leap.strategy.rules;

import java.util.Map;

/**
 * Builds a rule from its type and settings, as stored in strategies.rule_type
 * and rule_params. Missing settings take the defaults below.
 */
public final class RuleFactory {
    public static final int DEFAULT_SHORT_WINDOW = 20;
    public static final int DEFAULT_LONG_WINDOW = 50;
    public static final int DEFAULT_BOLLINGER_WINDOW = 20;
    public static final double DEFAULT_BOLLINGER_WIDTH = 2.0;

    // The settings' names in strategies.rule_params and the API
    static final String SHORT_WINDOW = "shortWindow";
    static final String LONG_WINDOW = "longWindow";
    static final String WINDOW = "window";
    static final String WIDTH = "width";

    private RuleFactory() {
    }

    /**
     * @throws InvalidRuleException if a setting is not a number or out of range
     */
    public static TradingRule create(RuleType type, Map<String, ? extends Number> params) {
        Map<String, ? extends Number> settings = params == null ? Map.of() : params;
        return switch (type) {
            case MA_CROSSOVER -> new MovingAverageCrossoverRule(
                    whole(settings, SHORT_WINDOW, DEFAULT_SHORT_WINDOW),
                    whole(settings, LONG_WINDOW, DEFAULT_LONG_WINDOW));
            case BOLLINGER -> new BollingerBandRule(
                    whole(settings, WINDOW, DEFAULT_BOLLINGER_WINDOW),
                    number(settings, WIDTH, DEFAULT_BOLLINGER_WIDTH));
        };
    }

    /**
     * The settings with every default filled in, as they are stored.
     */
    public static Map<String, Number> withDefaults(RuleType type, Map<String, ? extends Number> params) {
        Map<String, ? extends Number> settings = params == null ? Map.of() : params;
        create(type, settings);
        return switch (type) {
            case MA_CROSSOVER -> Map.of(
                    SHORT_WINDOW, whole(settings, SHORT_WINDOW, DEFAULT_SHORT_WINDOW),
                    LONG_WINDOW, whole(settings, LONG_WINDOW, DEFAULT_LONG_WINDOW));
            case BOLLINGER -> Map.of(
                    WINDOW, whole(settings, WINDOW, DEFAULT_BOLLINGER_WINDOW),
                    WIDTH, number(settings, WIDTH, DEFAULT_BOLLINGER_WIDTH));
        };
    }

    private static int whole(Map<String, ? extends Number> settings, String name, int fallback) {
        Number value = settings.get(name);
        if (value == null) {
            return fallback;
        }
        if (value.doubleValue() != Math.rint(value.doubleValue())) {
            throw new InvalidRuleException(name + " must be a whole number of bars");
        }
        return value.intValue();
    }

    private static double number(Map<String, ? extends Number> settings, String name, double fallback) {
        Number value = settings.get(name);
        return value == null ? fallback : value.doubleValue();
    }
}
