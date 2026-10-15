package com.neueda.leap.marketdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("AlpacaRestClient")
class AlpacaRestClientTest {

    private static AlpacaProperties properties(String keyId) {
        return new AlpacaProperties(keyId, "my-secret", "iex",
                "wss://example.test/v2/", "https://data.example.test", 30, Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("Reads the latest trade of each symbol")
    void readsLatestTrades() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data.example.test/v2/stocks/trades/latest?symbols=AAPL,MSFT&feed=iex"))
                .andExpect(header("APCA-API-KEY-ID", "my-key"))
                .andExpect(header("APCA-API-SECRET-KEY", "my-secret"))
                .andRespond(withSuccess("""
                        {"trades": {
                          "AAPL": {"t": "2026-10-12T14:30:01.123Z", "x": "V", "p": 227.31, "s": 100, "i": 1, "z": "C"},
                          "MSFT": {"t": "2026-10-12T14:29:58Z", "x": "V", "p": 431.05, "s": 5, "i": 2, "z": "C"}
                        }}""", MediaType.APPLICATION_JSON));

        Map<String, PriceQuote> prices = new AlpacaRestClient(properties("my-key"), builder)
                .latestTrades(List.of("AAPL", "MSFT"));

        server.verify();
        assertEquals(new PriceQuote("AAPL", new BigDecimal("227.31"), Instant.parse("2026-10-12T14:30:01.123Z")),
                prices.get("AAPL"));
        assertEquals(new BigDecimal("431.05"), prices.get("MSFT").price());
    }

    @Test
    @DisplayName("Returns no prices when the API call fails")
    void returnsEmptyOnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data.example.test/v2/stocks/trades/latest?symbols=AAPL&feed=iex"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));

        assertTrue(new AlpacaRestClient(properties("my-key"), builder).latestTrades(List.of("AAPL")).isEmpty());
    }

    @Test
    @DisplayName("Makes no call when no API key is configured")
    void makesNoCallWithoutKeys() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        assertTrue(new AlpacaRestClient(properties(""), builder).latestTrades(List.of("AAPL")).isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("Reads the closes of the latest two daily bars from snapshots")
    void readsDailyCloses() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data.example.test/v2/stocks/snapshots?symbols=AAPL,MSFT&feed=iex"))
                .andExpect(header("APCA-API-KEY-ID", "my-key"))
                .andRespond(withSuccess("""
                        {
                          "AAPL": {
                            "latestTrade": {"t": "2026-10-12T14:30:01Z", "p": 227.31},
                            "dailyBar": {"t": "2026-10-12T04:00:00Z", "o": 225.0, "h": 228.0, "l": 224.5, "c": 227.31, "v": 1000},
                            "prevDailyBar": {"t": "2026-10-09T04:00:00Z", "o": 223.0, "h": 226.0, "l": 222.0, "c": 225.50, "v": 900}
                          },
                          "MSFT": {
                            "dailyBar": {"t": "2026-10-12T04:00:00Z", "o": 430.0, "h": 432.0, "l": 429.0, "c": 431.05, "v": 500}
                          }
                        }""", MediaType.APPLICATION_JSON));

        Map<String, DailyCloses> closes = new AlpacaRestClient(properties("my-key"), builder)
                .dailyCloses(List.of("AAPL", "MSFT"));

        server.verify();
        assertEquals(new DailyCloses(LocalDate.of(2026, 10, 12), new BigDecimal("227.31"), new BigDecimal("225.50")),
                closes.get("AAPL"));
        assertNull(closes.get("MSFT").priorClose());
    }

    @Test
    @DisplayName("Returns no closes when the snapshot call fails")
    void returnsNoClosesOnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data.example.test/v2/stocks/snapshots?symbols=AAPL&feed=iex"))
                .andRespond(withServerError());

        assertTrue(new AlpacaRestClient(properties("my-key"), builder).dailyCloses(List.of("AAPL")).isEmpty());
    }

    @Test
    @DisplayName("Reads the most recent bars and returns them oldest first")
    void readsBarsOldestFirst() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data.example.test/v2/stocks/bars?symbols=AAPL&timeframe=1Day"
                        + "&start=2026-09-12T00:00:00Z&limit=2&adjustment=raw&feed=iex&sort=desc"))
                .andRespond(withSuccess("""
                        {"bars": {"AAPL": [
                          {"t": "2026-10-12T04:00:00Z", "o": 225.0, "h": 228.0, "l": 224.5, "c": 227.31, "v": 1000, "n": 10, "vw": 226.1},
                          {"t": "2026-10-09T04:00:00Z", "o": 223.0, "h": 226.0, "l": 222.0, "c": 225.50, "v": 900, "n": 9, "vw": 224.0}
                        ]}, "next_page_token": "abc"}""", MediaType.APPLICATION_JSON));

        List<PriceBar> bars = new AlpacaRestClient(properties("my-key"), builder)
                .bars("AAPL", BarTimeframe.DAY_1, Instant.parse("2026-09-12T00:00:00Z"), 2);

        server.verify();
        assertEquals(List.of(
                new PriceBar(Instant.parse("2026-10-09T04:00:00Z"), new BigDecimal("223.0"), new BigDecimal("226.0"),
                        new BigDecimal("222.0"), new BigDecimal("225.50"), 900),
                new PriceBar(Instant.parse("2026-10-12T04:00:00Z"), new BigDecimal("225.0"), new BigDecimal("228.0"),
                        new BigDecimal("224.5"), new BigDecimal("227.31"), 1000)),
                bars);
    }

    @Test
    @DisplayName("Returns no bars when the bars call fails")
    void returnsNoBarsOnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://data.example.test/v2/stocks/bars")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));

        assertTrue(new AlpacaRestClient(properties("my-key"), builder)
                .bars("AAPL", BarTimeframe.DAY_1, Instant.parse("2026-09-12T00:00:00Z"), 10).isEmpty());
    }

    @Test
    @DisplayName("Makes no snapshot or bars call when no API key is configured")
    void makesNoMarketDataCallWithoutKeys() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AlpacaRestClient client = new AlpacaRestClient(properties(""), builder);

        assertTrue(client.dailyCloses(List.of("AAPL")).isEmpty());
        assertTrue(client.bars("AAPL", BarTimeframe.DAY_1, Instant.parse("2026-09-12T00:00:00Z"), 10).isEmpty());
        server.verify();
    }
}
