package com.neueda.leap.marketdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.neueda.leap.time.FixedClock;

@DisplayName("MarketDataService")
class MarketDataServiceTest {
    // Monday 10:30 in New York
    private static final Instant NOW = Instant.parse("2026-10-12T14:30:00Z");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 9);

    private PriceProvider priceProvider;
    private AlpacaRestClient restClient;
    private FixedClock clock;
    private MarketDataService service;

    @BeforeEach
    void setUp() {
        priceProvider = mock(PriceProvider.class);
        restClient = mock(AlpacaRestClient.class);
        when(priceProvider.latestPrices(any())).thenReturn(Map.of());
        when(restClient.dailyCloses(any())).thenReturn(Map.of());
        clock = new FixedClock(NOW);
        service = new MarketDataService(priceProvider, restClient, clock);
    }

    private static PriceQuote quote(String symbol, String price) {
        return new PriceQuote(symbol, new BigDecimal(price), NOW);
    }

    @Test
    @DisplayName("Computes the daily change from the previous close")
    void computesDailyChange() {
        when(priceProvider.latestPrices(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote("231.00", "231.00")));
        when(restClient.dailyCloses(List.of("AAPL"))).thenReturn(Map.of("AAPL",
                new DailyCloses(MONDAY, new BigDecimal("230.10"), new BigDecimal("220.00"))));

        MarketQuote result = service.quotes(List.of("AAPL")).get(0);

        assertEquals(new MarketQuote("AAPL", new BigDecimal("231.00"), new BigDecimal("220.00"),
                new BigDecimal("11.00"), new BigDecimal("5.00"), NOW), result);
    }

    @Test
    @DisplayName("Uses the latest daily close when the trade is on a newer day than the latest bar")
    void usesLatestCloseForNewDay() {
        when(priceProvider.latestPrices(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote("AAPL", "198.00")));
        when(restClient.dailyCloses(List.of("AAPL"))).thenReturn(Map.of("AAPL",
                new DailyCloses(FRIDAY, new BigDecimal("200.00"), new BigDecimal("190.00"))));

        MarketQuote result = service.quotes(List.of("AAPL")).get(0);

        assertEquals(new BigDecimal("200.00"), result.previousClose());
        assertEquals(new BigDecimal("-2.00"), result.change());
        assertEquals(new BigDecimal("-1.00"), result.changePercent());
    }

    @Test
    @DisplayName("Returns null price fields for a symbol with no live price")
    void returnsNullsWithoutPrice() {
        assertEquals(List.of(new MarketQuote("AAPL", null, null, null, null, null)),
                service.quotes(List.of("AAPL")));
    }

    @Test
    @DisplayName("Returns the price without a change when the previous close is unknown")
    void returnsPriceWithoutCloses() {
        when(priceProvider.latestPrices(List.of("AAPL"))).thenReturn(Map.of("AAPL", quote("AAPL", "231.00")));

        MarketQuote result = service.quotes(List.of("AAPL")).get(0);

        assertEquals(new BigDecimal("231.00"), result.price());
        assertNull(result.previousClose());
        assertNull(result.change());
        assertNull(result.changePercent());
    }

    @Test
    @DisplayName("Caches previous closes and fetches them again once they are too old")
    void cachesCloses() {
        when(restClient.dailyCloses(List.of("AAPL"))).thenReturn(Map.of("AAPL",
                new DailyCloses(MONDAY, new BigDecimal("230.10"), new BigDecimal("220.00"))));

        service.quotes(List.of("AAPL"));
        clock.setNow(NOW.plus(MarketDataService.CLOSES_MAX_AGE));
        service.quotes(List.of("AAPL"));
        verify(restClient, times(1)).dailyCloses(any());

        clock.incrementSeconds(1);
        service.quotes(List.of("AAPL"));
        verify(restClient, times(2)).dailyCloses(any());
    }

    @Test
    @DisplayName("Asks for bars starting the timeframe's lookback before now")
    void requestsBarsFromLookback() {
        List<PriceBar> bars = List.of(new PriceBar(NOW, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, 1));
        when(restClient.bars("AAPL", BarTimeframe.HOUR_1, NOW.minus(BarTimeframe.HOUR_1.lookback()), 50))
                .thenReturn(bars);

        assertEquals(bars, service.bars("AAPL", BarTimeframe.HOUR_1, 50));
    }

    private static NewsArticle article(String headline, String publishedAt) {
        return new NewsArticle(headline, "benzinga", "https://example.com/" + headline.replace(' ', '-'),
                Instant.parse(publishedAt), List.of("AAPL"));
    }

    @Test
    @DisplayName("Asks for a week of news about the symbol and returns it newest first")
    void returnsNewsNewestFirst() {
        when(restClient.news(List.of("AAPL"), NOW.minus(MarketDataService.NEWS_LOOKBACK), MarketDataService.NEWS_LIMIT))
                .thenReturn(List.of(article("Older", "2026-10-11T09:00:00Z"), article("Newer", "2026-10-12T09:00:00Z")));

        assertEquals(List.of("Newer", "Older"), service.news("AAPL").stream().map(NewsArticle::headline).toList());
    }

    @Test
    @DisplayName("Caches news per symbol and fetches it again once it is too old")
    void cachesNews() {
        when(restClient.news(any(), any(), anyInt())).thenReturn(List.of(article("Apple news", "2026-10-12T09:00:00Z")));

        service.news("AAPL");
        clock.setNow(NOW.plus(MarketDataService.NEWS_MAX_AGE));
        service.news("AAPL");
        verify(restClient, times(1)).news(any(), any(), anyInt());

        service.news("MSFT");
        verify(restClient, times(2)).news(any(), any(), anyInt());

        clock.incrementSeconds(1);
        service.news("AAPL");
        verify(restClient, times(3)).news(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Remembers a failed news call for a while instead of retrying it on every request")
    void cachesNoNews() {
        when(restClient.news(any(), any(), anyInt())).thenReturn(List.of());

        assertTrue(service.news("AAPL").isEmpty());
        assertTrue(service.news("AAPL").isEmpty());
        verify(restClient, times(1)).news(any(), any(), anyInt());
    }
}
