package com.neueda.leap.marketdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.leap.time.ClockTest;

@DisplayName("LivePriceService")
class LivePriceServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-12T14:30:00Z");

    private AlpacaRestClient restClient;
    private ClockTest clock;
    private LivePriceService service;

    @BeforeEach
    void setUp() {
        restClient = mock(AlpacaRestClient.class);
        when(restClient.latestTrades(any())).thenReturn(Map.of());
        clock = new ClockTest(NOW);
        AlpacaProperties properties = new AlpacaProperties("key", "secret", "iex",
                "wss://example.test/v2/", "https://example.test", 30, Duration.ofSeconds(60));
        service = new LivePriceService(restClient, properties, clock);
    }

    private static PriceQuote quote(String price, Instant tradedAt) {
        return new PriceQuote("AAPL", new BigDecimal(price), tradedAt);
    }

    @Test
    @DisplayName("Returns a streamed price without calling the REST API")
    void returnsStreamedPrice() {
        service.update(quote("227.10", NOW));

        assertEquals(new BigDecimal("227.10"), service.latestPrice("AAPL").orElseThrow().price());
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("Ignores a trade older than the one it already holds")
    void ignoresOutOfOrderTrade() {
        service.update(quote("227.10", NOW));
        service.update(quote("226.00", NOW.minusSeconds(5)));

        assertEquals(new BigDecimal("227.10"), service.latestPrice("AAPL").orElseThrow().price());
    }

    @Test
    @DisplayName("Fetches a price it does not have from the REST API")
    void fetchesMissingPriceOverRest() {
        when(restClient.latestTrades(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote("228.00", NOW)));

        assertEquals(new BigDecimal("228.00"), service.latestPrice("AAPL").orElseThrow().price());
    }

    @Test
    @DisplayName("Refreshes a price that has not been updated within max-price-age")
    void refreshesStalePrice() {
        service.update(quote("227.10", NOW));
        clock.incrementSeconds(61);
        when(restClient.latestTrades(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", quote("229.50", NOW.plusSeconds(30))));

        assertEquals(new BigDecimal("229.50"), service.latestPrice("AAPL").orElseThrow().price());
    }

    @Test
    @DisplayName("Keeps the last known price when the refresh fails")
    void keepsLastPriceWhenRefreshFails() {
        service.update(quote("227.10", NOW));
        clock.incrementSeconds(61);

        assertEquals(new BigDecimal("227.10"), service.latestPrice("AAPL").orElseThrow().price());
    }

    @Test
    @DisplayName("Returns empty when no price is available at all")
    void returnsEmptyWithoutAnyPrice() {
        assertTrue(service.latestPrice("AAPL").isEmpty());
    }

    @Test
    @DisplayName("Refreshes all missing and stale prices in one REST call")
    void refreshesManyPricesInOneCall() {
        service.update(quote("227.10", NOW));
        service.update(new PriceQuote("MSFT", new BigDecimal("431.00"), NOW));
        clock.incrementSeconds(61);
        service.update(new PriceQuote("NVDA", new BigDecimal("120.00"), NOW.plusSeconds(61)));
        when(restClient.latestTrades(List.of("AAPL", "MSFT", "TSLA"))).thenReturn(Map.of(
                "AAPL", quote("229.50", NOW.plusSeconds(30)),
                "TSLA", new PriceQuote("TSLA", new BigDecimal("250.00"), NOW.plusSeconds(30))));

        Map<String, PriceQuote> prices = service.latestPrices(List.of("AAPL", "MSFT", "NVDA", "TSLA"));

        verify(restClient, times(1)).latestTrades(any());
        assertEquals(new BigDecimal("229.50"), prices.get("AAPL").price());
        // Refresh found nothing newer, so the last known price is kept
        assertEquals(new BigDecimal("431.00"), prices.get("MSFT").price());
        assertEquals(new BigDecimal("120.00"), prices.get("NVDA").price());
        assertEquals(new BigDecimal("250.00"), prices.get("TSLA").price());
    }

    @Test
    @DisplayName("Makes no REST call when every price is fresh")
    void makesNoCallWhenPricesFresh() {
        service.update(quote("227.10", NOW));

        assertEquals(1, service.latestPrices(List.of("AAPL")).size());
        verifyNoInteractions(restClient);
    }
}
