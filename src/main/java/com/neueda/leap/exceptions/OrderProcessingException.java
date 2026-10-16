package com.neueda.leap.exceptions;

/**
 * Wraps a failure while the Kafka consumers work an order, so the error
 * handler can decide whether the order goes to the dead-letter queue and
 * whether it may be replayed. Keeps the error code of the failure it wraps.
 */
public abstract class OrderProcessingException extends TradingException {
    private static final String UNEXPECTED_ERROR = "SYS-500";

    protected OrderProcessingException(String message, Throwable cause) {
        super(cause instanceof TradingException trading ? trading.getErrorCode() : UNEXPECTED_ERROR,
                message, cause);
    }
}
