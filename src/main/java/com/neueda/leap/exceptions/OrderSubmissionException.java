package com.neueda.leap.exceptions;

/**
 * The order passed validation but could not be handed to Kafka, so it was not accepted.
 */
public class OrderSubmissionException extends RuntimeException {

    public OrderSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
