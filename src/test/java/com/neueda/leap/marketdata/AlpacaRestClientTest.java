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
    @DisplayName("Makes no snapshot, bars or news call when no API key is configured")
    void makesNoMarketDataCallWithoutKeys() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AlpacaRestClient client = new AlpacaRestClient(properties(""), builder);

        assertTrue(client.dailyCloses(List.of("AAPL")).isEmpty());
        assertTrue(client.bars("AAPL", BarTimeframe.DAY_1, Instant.parse("2026-09-12T00:00:00Z"), 10).isEmpty());
        assertTrue(client.news(List.of("AAPL"), Instant.parse("2026-10-12T00:00:00Z"), 50).isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("Reads news about the symbols, newest first")
    void readsNews() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://data.example.test/v1beta1/news")))
                .andExpect(header("APCA-API-KEY-ID", "my-key"))
                .andExpect(queryParam("symbols", "AAPL,TSLA"))
                .andExpect(queryParam("start", "2026-10-12T00:00:00Z"))
                .andExpect(queryParam("limit", "50"))
                .andExpect(queryParam("sort", "desc"))
                .andRespond(withSuccess("""
                        {"news": [
                          {"id": 2, "headline": " Tesla beats delivery estimates ", "author": "A", "source": "benzinga",
                           "url": "https://www.benzinga.com/news/2", "created_at": "2026-10-15T13:00:00Z",
                           "updated_at": "2026-10-15T13:05:00Z", "summary": "", "images": [], "symbols": ["TSLA"]},
                          {"id": 1, "headline": "Apple unveils new chip", "source": "benzinga",
                           "url": "https://www.benzinga.com/news/1", "created_at": "2026-10-14T09:30:00Z",
                           "symbols": ["AAPL", "QCOM"]}
                        ], "next_page_token": null}""", MediaType.APPLICATION_JSON));

        List<NewsArticle> news = new AlpacaRestClient(properties("my-key"), builder)
                .news(List.of("AAPL", "TSLA"), Instant.parse("2026-10-12T00:00:00Z"), 50);

        server.verify();
        assertEquals(List.of(
                new NewsArticle("Tesla beats delivery estimates", "benzinga", "https://www.benzinga.com/news/2",
                        Instant.parse("2026-10-15T13:00:00Z"), List.of("TSLA")),
                new NewsArticle("Apple unveils new chip", "benzinga", "https://www.benzinga.com/news/1",
                        Instant.parse("2026-10-14T09:30:00Z"), List.of("AAPL", "QCOM"))), news);
    }

    @Test
    @DisplayName("Leaves out news without a headline or an https link")
    void leavesOutUnsafeNews() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://data.example.test/v1beta1/news")))
                .andRespond(withSuccess("""
                        {"news": [
                          {"headline": "Script link", "source": "x", "url": "javascript:alert(1)", "created_at": "2026-10-15T13:00:00Z", "symbols": ["AAPL"]},
                          {"headline": "Plain http", "source": "x", "url": "http://example.com", "created_at": "2026-10-15T13:00:00Z", "symbols": ["AAPL"]},
                          {"headline": "No link", "source": "x", "created_at": "2026-10-15T13:00:00Z", "symbols": ["AAPL"]},
                          {"headline": "  ", "source": "x", "url": "https://example.com/blank", "created_at": "2026-10-15T13:00:00Z", "symbols": ["AAPL"]},
                          {"headline": "Kept", "source": "x", "url": "https://example.com/kept", "created_at": "2026-10-15T13:00:00Z"}
                        ]}""", MediaType.APPLICATION_JSON));

        List<NewsArticle> news = new AlpacaRestClient(properties("my-key"), builder)
                .news(List.of("AAPL"), Instant.parse("2026-10-12T00:00:00Z"), 50);

        assertEquals(List.of(new NewsArticle("Kept", "x", "https://example.com/kept",
                Instant.parse("2026-10-15T13:00:00Z"), List.of())), news);
    }

    @Test
    @DisplayName("Returns no news when the news call fails")
    void returnsNoNewsOnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://data.example.test/v1beta1/news")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

        assertTrue(new AlpacaRestClient(properties("my-key"), builder)
                .news(List.of("AAPL"), Instant.parse("2026-10-12T00:00:00Z"), 50).isEmpty());
    }

    @Test
    @DisplayName("Makes no news call for no symbols")
    void makesNoNewsCallForNoSymbols() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        assertTrue(new AlpacaRestClient(properties("my-key"), builder)
                .news(List.of(), Instant.parse("2026-10-12T00:00:00Z"), 50).isEmpty());
        server.verify();
    }

    @Test
    @DisplayName("Reads the 1-minute bars of a past day in time order, from the feed it is given")
    void readsMinuteBarsOfADay() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data.example.test/v2/stocks/bars?symbols=SPY&timeframe=1Min"
                        + "&start=2026-10-09T13:30:00Z&end=2026-10-09T20:00:00Z&limit=1000&adjustment=raw&feed=sip&sort=asc"))
                .andRespond(withSuccess("""
                        {"bars": {"SPY": [
                          {"t": "2026-10-09T13:30:00Z", "o": 580.1, "h": 580.9, "l": 579.8, "c": 580.5, "v": 120000},
                          {"t": "2026-10-09T13:31:00Z", "o": 580.5, "h": 581.0, "l": 580.2, "c": 580.8, "v": 90000}
                        ]}}""", MediaType.APPLICATION_JSON));

        List<PriceBar> bars = new AlpacaRestClient(properties("my-key"), builder).minuteBars("SPY",
                Instant.parse("2026-10-09T13:30:00Z"), Instant.parse("2026-10-09T20:00:00Z"), "sip");

        server.verify();
        assertEquals(2, bars.size());
        assertEquals(Instant.parse("2026-10-09T13:30:00Z"), bars.get(0).time());
        assertEquals(new BigDecimal("580.8"), bars.get(1).close());
    }

    @Test
    @DisplayName("Returns no minute bars for a day without trading, a failed call or no API key")
    void returnsNoMinuteBars() {
        Instant start = Instant.parse("2026-10-10T13:30:00Z");
        Instant end = Instant.parse("2026-10-10T20:00:00Z");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://data.example.test/v2/stocks/bars")))
                .andRespond(withSuccess("{\"bars\": {}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://data.example.test/v2/stocks/bars")))
                .andRespond(withServerError());
        AlpacaRestClient client = new AlpacaRestClient(properties("my-key"), builder);

        assertTrue(client.minuteBars("SPY", start, end, "sip").isEmpty());
        assertTrue(client.minuteBars("SPY", start, end, "sip").isEmpty());
        server.verify();
        assertTrue(new AlpacaRestClient(properties(""), RestClient.builder()).minuteBars("SPY", start, end, "sip").isEmpty());
    }
}
