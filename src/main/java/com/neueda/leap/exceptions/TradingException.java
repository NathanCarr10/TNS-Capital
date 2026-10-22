package com.neueda.leap.exceptions;

/**
 * Root of the platform's exception hierarchy. Every failure the trading
 * platform raises on purpose extends it and carries the error code from the
 * spec's error catalogue (§21), e.g. ACC-404, which GlobalExceptionHandler
 * returns to the client.
 *
 * The prefix of the code says which part of the domain failed (ACC account,
 * INS instrument, ORD order, POS position, PRC/MKT market data, BRK broker,
 * SYS platform). The two Kafka wrappers, NonRetryableOrderException and
 * SettlementException, keep the code of the failure they wrap.
 *
 * Exceptions extend this class directly: Throwable, Exception and
 * RuntimeException already make the tree three deep, so a further layer of
 * category classes would go past Sonar's inheritance limit (java:S110).
 */
public abstract class TradingException extends RuntimeException {
    private static final String UNEXPECTED_ERROR = "SYS-500";

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

    /**
     * The error code of a wrapped failure: its own code if it is a
     * TradingException, otherwise SYS-500.
     */
    protected static String errorCodeOf(Throwable cause) {
        return cause instanceof TradingException trading ? trading.getErrorCode() : UNEXPECTED_ERROR;
    }
}
