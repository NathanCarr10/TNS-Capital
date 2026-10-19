package com.neueda.leap.marketdata;

import java.time.Instant;
import java.util.List;

/**
 * A news headline from the market data provider.
 *
 * @param headline    the article's title, as plain text
 * @param source      who published it, e.g. benzinga
 * @param url         link to the full article; always https
 * @param publishedAt when it was published
 * @param symbols     the instruments it is about, e.g. [AAPL]
 */
public record NewsArticle(String headline, String source, String url, Instant publishedAt, List<String> symbols) {
}
