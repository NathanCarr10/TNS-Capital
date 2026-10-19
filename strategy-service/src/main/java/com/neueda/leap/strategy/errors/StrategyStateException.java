package com.neueda.leap.strategy.errors;

/**
 * The change is not allowed in the strategy's current state, e.g. switching on
 * an archived strategy.
 */
public class StrategyStateException extends StrategyException {

    public StrategyStateException(String message) {
        super("STR-409", message);
    }
}
