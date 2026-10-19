package com.neueda.leap.strategy.rules;

/**
 * The rules a strategy can use, with their settings (strategies.rule_params).
 */
public enum RuleType {
    /** Settings: shortWindow, longWindow (bars) */
    MA_CROSSOVER,
    /** Settings: window (bars), width (standard deviations) */
    BOLLINGER
}
