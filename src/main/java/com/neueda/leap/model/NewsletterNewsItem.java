package com.neueda.leap.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A headline about one of the movers in a newsletter issue (an element of the
 * news JSONB column).
 *
 * @param symbol      the mover the headline is shown under
 * @param headline    the article's title, as plain text
 * @param source      who published it, e.g. benzinga
 * @param url         link to the full article; always https
 * @param publishedAt when it was published
 */
public record NewsletterNewsItem(String symbol, String headline, String source, String url, Instant publishedAt) {

    public NewsletterNewsItem {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(headline, "headline");
        Objects.requireNonNull(url, "url");
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException("News links must be https: " + url);
        }
    }
}
