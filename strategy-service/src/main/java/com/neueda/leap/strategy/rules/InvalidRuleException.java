package com.neueda.leap.strategy.rules;

import com.neueda.leap.strategy.errors.StrategyException;

/**
 * The rule's settings are out of range, or the rule type is unknown.
 */
public class InvalidRuleException extends StrategyException {

    public InvalidRuleException(String message) {
        super("STR-422", message);
    }
}
