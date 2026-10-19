package com.neueda.leap.strategy.trading;

/**
 * The trading API refused an order, or could not be reached.
 *
 * @see TradingApiClient
 */
public class OrderRefusedException extends RuntimeException {
    private final String errorCode;

    public OrderRefusedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * @return the API's error code, e.g. ACC-403, or SYS-503 if it could not be reached
     */
    public String getErrorCode() {
        return errorCode;
    }
}
