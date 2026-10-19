package com.neueda.leap.strategy.domain;

/**
 * What a strategy holds from its own orders. It buys only when FLAT and sells
 * only when LONG, so it never sells shares the trader bought themselves.
 */
public enum Position {
    FLAT,
    LONG
}
