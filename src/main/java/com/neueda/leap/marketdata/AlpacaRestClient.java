package com.neueda.leap.marketdata;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Calls Alpaca's REST market data API for the latest trade of each symbol.
 *
 * Used to fill the live price pool at startup and to price symbols the
 * stream has no recent trade for (market closed, or not subscribed).
 * Failures are logged and return no prices, so callers fall back to whatever
 * they already have. Timeouts come from spring.http.client.* in application.yml.
 */
@Component
@Slf4j
public class AlpacaRestClient {
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
                            .queryParam("symbols", String.join(",", symbols))
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

    // Response body of GET /v2/stocks/trades/latest; other fields are ignored
    record LatestTradesResponse(Map<String, Trade> trades) {
    }

    record Trade(@JsonProperty("p") BigDecimal price, @JsonProperty("t") Instant timestamp) {
    }
}
