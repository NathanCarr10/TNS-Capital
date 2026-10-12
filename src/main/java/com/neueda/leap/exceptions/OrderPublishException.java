package com.neueda.leap.exceptions;

/**
 * An order could not be queued on Kafka (broker down or too slow to answer),
 * so it will not be executed. Returned as 503 SYS-503.
 */
public class OrderPublishException extends RuntimeException {
    public OrderPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
