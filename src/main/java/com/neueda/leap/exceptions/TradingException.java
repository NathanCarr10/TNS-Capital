package com.neueda.leap.exceptions;

/**
 * Root of the platform's exception hierarchy. Every failure the trading
 * platform raises on purpose carries the error code from the spec's error
 * catalogue (§21), e.g. ACC-404, which GlobalExceptionHandler returns to the
 * client.
 *
 * The four categories below it say what kind of failure it is:
 * - {@link ResourceNotFoundException}: something the request names does not exist (404)
 * - {@link BusinessRuleException}: a trading rule refuses the request (§20)
 * - {@link ConflictException}: the request clashes with the current state (409)
 * - {@link ServiceUnavailableException}: a service the platform depends on is down (503)
 *
 * {@link OrderProcessingException} wraps failures in the Kafka consumers,
 * where they decide whether an order goes to the dead-letter queue.
 */
public abstract class TradingException extends RuntimeException {
    private final String errorCode;

    protected TradingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected TradingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * @return the spec error code, e.g. "ORD-400"
     */
    public String getErrorCode() {
        return errorCode;
    }
}
