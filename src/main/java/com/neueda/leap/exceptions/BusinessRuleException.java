package com.neueda.leap.exceptions;

/**
 * A trading rule from the spec (§20) refuses the request, e.g. a BUY the
 * account cannot afford.
 */
public abstract class BusinessRuleException extends TradingException {
    protected BusinessRuleException(String errorCode, String message) {
        super(errorCode, message);
    }
}
