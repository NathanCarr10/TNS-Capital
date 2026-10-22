package com.neueda.leap.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Calls Alpaca's REST market data API.
 *
 * Latest trades fill the live price pool at startup and price symbols the
 * stream has no recent trade for (market closed, or not subscribed).
 * Snapshots give the previous close for the dashboard's daily change, and
 * bars feed its candlestick chart. News gives the newsletter its headlines.
 * Failures are logged and return no prices, so callers fall back to whatever
 * they already have. Timeouts come from spring.http.client.* in application.yml.
 */
@Component
@Slf4j
public class AlpacaRestClient {
    private static final String SYMBOLS_PARAM = "symbols";
    private static final String START_PARAM = "start";
    private static final String LIMIT_PARAM = "limit";
    // A trading day is at most 16 hours of 1-minute bars (pre-market to after-hours)
    private static final int MAX_MINUTE_BARS = 1000;

    private final AlpacaProperties properties;
    private final RestClient restClient;

    public AlpacaRestClient(AlpacaProperties properties, RestClient.Builder builder) {
        this.properties = Objects.requireNonNull(properties);
        this.restClient = builder
                .baseUrl(properties.dataUrl())
                .defaultHeader("APCA-API-KEY-ID", Objects.toString(properties.keyId(), ""))
                .defaultHeader("APCA-API-SECRET-KEY", Objects.toString(properties.secretKey(), ""))
                .build();
    }

    /**
     * Fetches the latest trade for each symbol in one request.
     *
     * @param symbols normalized symbols, e.g. AAPL
     * @return prices keyed by symbol; symbols Alpaca has no trade for are left out
     */
    public Map<String, PriceQuote> latestTrades(Collection<String> symbols) {
        if (!properties.isConfigured() || symbols.isEmpty()) {
            return Map.of();
        }
        try {
            LatestTradesResponse response = restClient.get()
                    .uri(uri -> uri.path("/v2/stocks/trades/latest")
                            .queryParam(SYMBOLS_PARAM, String.join(",", symbols))
                            .queryParam("feed", properties.feed())
                            .build())
                    .retrieve()
                    .body(LatestTradesResponse.class);

            if (response == null || response.trades() == null) {
                return Map.of();
            }
            return response.trades().entrySet().stream()
                    .filter(entry -> entry.getValue().price() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> new PriceQuote(
                            entry.getKey(), entry.getValue().price(), entry.getValue().timestamp())));
        } catch (RestClientException e) {
            log.warn("Could not fetch latest prices from Alpaca for {}: {}", symbols, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Fetches the closes of the latest two daily bars for each symbol in one request.
     *
     * @param symbols normalized symbols, e.g. AAPL
     * @return closes keyed by symbol; symbols Alpaca has no snapshot for are left out
     */
    public Map<String, DailyCloses> dailyCloses(Collection<String> symbols) {
        if (!properties.isConfigured() || symbols.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, Snapshot> response = restClient.get()
                    .uri(uri -> uri.path("/v2/stocks/snapshots")
                            .queryParam(SYMBOLS_PARAM, String.join(",", symbols))
                            .queryParam("feed", properties.feed())
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Snapshot>>() {
                    });

            if (response == null) {
                return Map.of();
            }
            return response.entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue().dailyBar() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toDailyCloses()));
        } catch (RestClientException e) {
            log.warn("Could not fetch snapshots from Alpaca for {}: {}", symbols, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Fetches the most recent bars of one symbol.
     *
     * @param symbol    normalized symbol, e.g. AAPL
     * @param timeframe bar size
     * @param start     earliest bar time to look at
     * @param limit     how many of the most recent bars to return
     * @return bars oldest first; empty if Alpaca has none or the call fails
     */
    public List<PriceBar> bars(String symbol, BarTimeframe timeframe, Instant start, int limit) {
        if (!properties.isConfigured()) {
            return List.of();
        }
        try {
            BarsResponse response = restClient.get()
                    .uri(uri -> uri.path("/v2/stocks/bars")
                            .queryParam(SYMBOLS_PARAM, symbol)
                            .queryParam("timeframe", timeframe.value())
                            .queryParam(START_PARAM, start.toString())
                            .queryParam(LIMIT_PARAM, limit)
                            .queryParam("adjustment", "raw")
                            .queryParam("feed", properties.feed())
                            // Newest first, so the limit keeps the most recent bars
                            .queryParam("sort", "desc")
                            .build())
                    .retrieve()
                    .body(BarsResponse.class);

            if (response == null || response.bars() == null || response.bars().get(symbol) == null) {
                return List.of();
            }
            List<PriceBar> bars = new ArrayList<>(response.bars().get(symbol).stream()
                    .map(Bar::toPriceBar)
                    .toList());
            Collections.reverse(bars);
            return bars;
        } catch (RestClientException e) {
            log.warn("Could not fetch {} bars from Alpaca for {}: {}", timeframe.value(), symbol, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches the most recent news about any of the symbols in one request.
     * Articles without a headline or an https link are left out, so a link
     * from the feed can never be a javascript: or plain http URL.
     *
     * @param symbols normalized symbols, e.g. AAPL
     * @param start   earliest publication time to look at
     * @param limit   how many articles to return at most (Alpaca allows 50)
     * @return articles newest first; empty if there are none or the call fails
     */
    public List<NewsArticle> news(Collection<String> symbols, Instant start, int limit) {
        if (!properties.isConfigured() || symbols.isEmpty()) {
            return List.of();
        }
        try {
            NewsResponse response = restClient.get()
                    .uri(uri -> uri.path("/v1beta1/news")
                            .queryParam(SYMBOLS_PARAM, String.join(",", symbols))
                            .queryParam(START_PARAM, start.toString())
                            .queryParam(LIMIT_PARAM, limit)
                            .queryParam("sort", "desc")
                            .build())
                    .retrieve()
                    .body(NewsResponse.class);

            if (response == null || response.news() == null) {
                return List.of();
            }
            return response.news().stream()
                    .filter(Article::isDisplayable)
                    .map(Article::toNewsArticle)
                    .toList();
        } catch (RestClientException e) {
            log.warn("Could not fetch news from Alpaca for {}: {}", symbols, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches the 1-minute bars of one symbol between two times, for a replay
     * of a past trading day. A regular session has 390 of them, so one page holds them.
     *
     * @param symbol normalized symbol, e.g. SPY
     * @param start  first bar time to include
     * @param end    bar times before this are included
     * @param feed   the data feed; the free plan has every exchange (sip) for times
     *               more than 15 minutes ago
     * @return bars oldest first; empty if Alpaca has none (e.g. a market holiday) or the call fails
     */
    public List<PriceBar> minuteBars(String symbol, Instant start, Instant end, String feed) {
        if (!properties.isConfigured()) {
            return List.of();
        }
        try {
            BarsResponse response = restClient.get()
                    .uri(uri -> uri.path("/v2/stocks/bars")
                            .queryParam(SYMBOLS_PARAM, symbol)
                            .queryParam("timeframe", "1Min")
                            .queryParam(START_PARAM, start.toString())
                            .queryParam("end", end.toString())
                            .queryParam(LIMIT_PARAM, MAX_MINUTE_BARS)
                            .queryParam("adjustment", "raw")
                            .queryParam("feed", feed)
                            .queryParam("sort", "asc")
                            .build())
                    .retrieve()
                    .body(BarsResponse.class);
            if (response == null || response.bars() == null || response.bars().get(symbol) == null) {
                return List.of();
            }
            return response.bars().get(symbol).stream().map(Bar::toPriceBar).toList();
        } catch (RestClientException e) {
            log.warn("Could not fetch 1Min bars from Alpaca for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    // Response body of GET /v2/stocks/trades/latest; other fields are ignored
    record LatestTradesResponse(Map<String, Trade> trades) {
    }

    record Trade(@JsonProperty("p") BigDecimal price, @JsonProperty("t") Instant timestamp) {
    }

    // One symbol of GET /v2/stocks/snapshots; trades, quotes and minute bars are ignored
    record Snapshot(Bar dailyBar, Bar prevDailyBar) {
        DailyCloses toDailyCloses() {
            return new DailyCloses(DailyCloses.tradingDay(dailyBar.time()), dailyBar.close(),
                    prevDailyBar == null ? null : prevDailyBar.close());
        }
    }

    // Response body of GET /v2/stocks/bars; next_page_token is ignored as one page holds the limit
    record BarsResponse(Map<String, List<Bar>> bars) {
    }

    record Bar(@JsonProperty("t") Instant time, @JsonProperty("o") BigDecimal open,
            @JsonProperty("h") BigDecimal high, @JsonProperty("l") BigDecimal low,
            @JsonProperty("c") BigDecimal close, @JsonProperty("v") long volume) {
        PriceBar toPriceBar() {
            return new PriceBar(time, open, high, low, close, volume);
        }
    }

    // Response body of GET /v1beta1/news; next_page_token is ignored as one page holds the limit
    record NewsResponse(List<Article> news) {
    }

    // One article; author, summary, content and images are ignored
    record Article(String headline, String source, String url,
            @JsonProperty("created_at") Instant createdAt, List<String> symbols) {
        boolean isDisplayable() {
            return StringUtils.hasText(headline) && url != null && url.startsWith("https://");
        }

        NewsArticle toNewsArticle() {
            return new NewsArticle(headline.strip(), source, url, createdAt,
                    symbols == null ? List.of() : List.copyOf(symbols));
        }
    }
}
