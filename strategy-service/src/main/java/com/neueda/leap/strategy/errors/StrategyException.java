package com.neueda.leap.strategy.errors;

/**
 * Root of the strategy service's failures. Each carries an error code from the
 * platform's catalogue (STR strategy, PRC prices, INS instrument, AUTH access),
 * which ApiExceptionHandler returns in the platform's error envelope.
 */
public abstract class StrategyException extends RuntimeException {
    private final String errorCode;

    protected StrategyException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
