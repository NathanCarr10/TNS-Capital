package com.neueda.leap.exceptions;

/**
 * Exception wrapper for non-retryable order processing failures.
 * 
 * Used to signal to the Kafka error handler that an exception should NOT be retried.
 * Wraps exceptions that are known to be non-recoverable (e.g., account not found,
 * instrument doesn't exist) so that the error handler can skip retry attempts and
 * route directly to the Dead-Letter Queue.
 * 
 * This prevents wasted retry cycles on impossible scenarios where the root cause
 * is permanent (e.g., account was deleted after order placement).
 */
public class NonRetryableOrderException extends RuntimeException {

    /**
     * The original exception that caused this non-retryable error.
     * Used for logging and DLQ audit trail.
     */
    private final Exception originalException;

    /**
     * Wraps an exception to mark it as non-retryable.
     *
     * @param message the detail message
     * @param originalException the original exception that caused this error
     */
    public NonRetryableOrderException(String message, Exception originalException) {
        super(message, originalException);
        this.originalException = originalException;
    }

    /**
     * Gets the original exception that was wrapped.
     *
     * @return the original exception
     */
    public Exception getOriginalException() {
        return originalException;
    }
}
