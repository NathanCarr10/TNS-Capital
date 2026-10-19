package com.neueda.leap.strategy.errors;

public class ActiveStrategyLimitException extends StrategyException {

    public ActiveStrategyLimitException(int limit) {
        super("STR-409", limit == 1
                ? "Only one strategy can be switched on at a time; pause the active one first"
                : "Only " + limit + " strategies can be switched on at a time; pause one first");
    }
}
