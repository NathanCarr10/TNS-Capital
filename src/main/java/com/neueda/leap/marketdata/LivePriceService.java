package com.neueda.leap.marketdata;

import com.neueda.leap.time.Clock;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live price pool: the latest trade price of each instrument, kept in memory.
 *
 * AlpacaPriceStream pushes every trade it receives into the pool. A price
 * that has not been updated for market-data.alpaca.max-price-age (no trades
 * on the stream, or the symbol is not streamed) is refreshed from the REST
 * API when it is next asked for.
 */
@Service
public class LivePriceService implements PriceProvider {
    private final AlpacaRestClient restClient;
    private final AlpacaProperties properties;
    private final Clock clock;
    private final Map<String, CachedPrice> prices = new ConcurrentHashMap<>();

    public LivePriceService(AlpacaRestClient restClient, AlpacaProperties properties, Clock clock) {
        this.restClient = Objects.requireNonNull(restClient);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Records a trade. A trade older than the one already held is ignored, so
     * out-of-order messages cannot move the price backwards.
     */
    public void update(PriceQuote quote) {
        CachedPrice received = new CachedPrice(quote, clock.now());
        prices.merge(quote.symbol(), received,
                (held, fresh) -> fresh.quote().tradedAt().isBefore(held.quote().tradedAt()) ? held : fresh);
    }

    /**
     * Fetches the latest prices for these symbols from the REST API.
     */
    public void refresh(Collection<String> symbols) {
        restClient.latestTrades(symbols).values().forEach(this::update);
    }

    @Override
    public Optional<PriceQuote> latestPrice(String symbol) {
        CachedPrice cached = prices.get(symbol);
        if (cached == null || isStale(cached)) {
            refresh(List.of(symbol));
            cached = prices.get(symbol);
        }
        return Optional.ofNullable(cached).map(CachedPrice::quote);
    }

    private boolean isStale(CachedPrice cached) {
        return cached.receivedAt().plus(properties.maxPriceAge()).isBefore(clock.now());
    }

    private record CachedPrice(PriceQuote quote, Instant receivedAt) {
    }
}
