package com.neueda.leap.marketdata;

import com.neueda.leap.time.Clock;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Market data for the dashboard: quotes with their daily change, price bars,
 * and the latest news about an instrument.
 *
 * Prices come from the live price pool. Previous closes come from Alpaca
 * snapshots and only change once a day, so they are cached for
 * CLOSES_MAX_AGE; a dashboard polling every few seconds then costs no extra
 * Alpaca calls beyond what the price pool already makes. News is cached for
 * NEWS_MAX_AGE per symbol, so every trader on a symbol's page shares one call.
 */
@Service
public class MarketDataService {
    static final Duration CLOSES_MAX_AGE = Duration.ofMinutes(15);
    static final Duration NEWS_MAX_AGE = Duration.ofMinutes(5);
    // How far back to look for headlines, and how many to keep
    static final Duration NEWS_LOOKBACK = Duration.ofDays(7);
    static final int NEWS_LIMIT = 10;

    private final PriceProvider priceProvider;
    private final AlpacaRestClient restClient;
    private final Clock clock;
    private final Map<String, CachedCloses> closes = new ConcurrentHashMap<>();
    private final Map<String, CachedNews> news = new ConcurrentHashMap<>();

    public MarketDataService(PriceProvider priceProvider, AlpacaRestClient restClient, Clock clock) {
        this.priceProvider = Objects.requireNonNull(priceProvider);
        this.restClient = Objects.requireNonNull(restClient);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * @param symbols normalized symbols
     * @return a quote for every symbol, in the same order; see MarketQuote for when fields are null
     */
    public List<MarketQuote> quotes(List<String> symbols) {
        Map<String, PriceQuote> prices = priceProvider.latestPrices(symbols);
        Map<String, DailyCloses> dailyCloses = dailyCloses(symbols);
        return symbols.stream()
                .map(symbol -> MarketQuote.of(symbol, prices.get(symbol), dailyCloses.get(symbol)))
                .toList();
    }

    /**
     * @param symbol normalized symbol
     * @return up to limit of the most recent bars, oldest first; empty if none are available
     */
    public List<PriceBar> bars(String symbol, BarTimeframe timeframe, int limit) {
        Instant start = clock.now().minus(timeframe.lookback());
        return restClient.bars(symbol, timeframe, start, limit);
    }

    /**
     * @param symbol normalized symbol of a known instrument (the cache holds one entry per symbol)
     * @return up to NEWS_LIMIT headlines from the last NEWS_LOOKBACK, newest first; empty if
     *         there are none or Alpaca cannot be reached
     */
    public List<NewsArticle> news(String symbol) {
        Instant now = clock.now();
        CachedNews cached = news.get(symbol);
        if (cached != null && !cached.fetchedAt().plus(NEWS_MAX_AGE).isBefore(now)) {
            return cached.articles();
        }
        List<NewsArticle> articles = restClient.news(List.of(symbol), now.minus(NEWS_LOOKBACK), NEWS_LIMIT).stream()
                .sorted(Comparator.comparing(NewsArticle::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        // A failed call is cached too, so a page polling the symbol does not retry it every time
        news.put(symbol, new CachedNews(articles, now));
        return articles;
    }

    private Map<String, DailyCloses> dailyCloses(Collection<String> symbols) {
        Instant now = clock.now();
        List<String> toFetch = symbols.stream()
                .filter(symbol -> {
                    CachedCloses cached = closes.get(symbol);
                    return cached == null || cached.fetchedAt().plus(CLOSES_MAX_AGE).isBefore(now);
                })
                .toList();
        if (!toFetch.isEmpty()) {
            restClient.dailyCloses(toFetch)
                    .forEach((symbol, fetched) -> closes.put(symbol, new CachedCloses(fetched, now)));
        }

        Map<String, DailyCloses> result = new HashMap<>();
        symbols.forEach(symbol -> {
            CachedCloses cached = closes.get(symbol);
            if (cached != null) {
                result.put(symbol, cached.closes());
            }
        });
        return result;
    }

    private record CachedCloses(DailyCloses closes, Instant fetchedAt) {
    }

    private record CachedNews(List<NewsArticle> articles, Instant fetchedAt) {
    }
}
