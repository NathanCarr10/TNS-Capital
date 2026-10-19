package com.neueda.leap.exceptions;

/**
 * Exception thrown when support publishes the week's newsletter early while
 * early publishing is switched off (newsletter.early-publish).
 */
public class NewsletterPublishingDisabledException extends TradingException {
    public NewsletterPublishingDisabledException(String message) {
        super("NWS-403", message);
    }
}
