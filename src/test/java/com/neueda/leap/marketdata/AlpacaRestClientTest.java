package com.neueda.leap.marketdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
}
