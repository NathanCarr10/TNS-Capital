package com.neueda.leap.marketdata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;

/**
 * Feeds Alpaca stream messages to the handler; no network connection is made.
 */
@DisplayName("AlpacaPriceStream message handling")
class AlpacaPriceStreamTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> sent = new ArrayList<>();

    private LivePriceService prices;
    private InstrumentRepository instrumentRepository;
    private AlpacaPriceStream stream;

    @BeforeEach
    void setUp() {
        prices = mock(LivePriceService.class);
        instrumentRepository = mock(InstrumentRepository.class);
        when(instrumentRepository.findAll()).thenReturn(List.of(
                new Instrument("MSFT", "Microsoft Corp", "Equity", "USD", true),
                new Instrument("AAPL", "Apple Inc", "Equity", "USD", true),
                new Instrument("ARKK", "ARK Innovation ETF", "Fund", "USD", false)));
        stream = newStream(30);
    }

    private AlpacaPriceStream newStream(int maxSymbols) {
        AlpacaProperties properties = new AlpacaProperties("my-key", "my-secret", "iex",
                "wss://example.test/v2/", "https://example.test", maxSymbols, Duration.ofSeconds(60));
        return new AlpacaPriceStream(properties, prices, instrumentRepository, objectMapper);
    }

    private JsonNode lastSent() throws Exception {
        return objectMapper.readTree(sent.get(sent.size() - 1));
    }

    @Test
    @DisplayName("Sends the API keys once connected")
    void authenticatesOnConnect() throws Exception {
        stream.handleMessage("[{\"T\":\"success\",\"msg\":\"connected\"}]", sent::add);

        JsonNode auth = lastSent();
        assertEquals("auth", auth.get("action").asText());
        assertEquals("my-key", auth.get("key").asText());
        assertEquals("my-secret", auth.get("secret").asText());
    }

    @Test
    @DisplayName("Subscribes to trades for tradable instruments once authenticated")
    void subscribesToTradableInstruments() throws Exception {
        stream.handleMessage("[{\"T\":\"success\",\"msg\":\"authenticated\"}]", sent::add);

        JsonNode subscribe = lastSent();
        assertEquals("subscribe", subscribe.get("action").asText());
        assertEquals("[\"AAPL\",\"MSFT\"]", subscribe.get("trades").toString());
    }

    @Test
    @DisplayName("Subscribes to no more symbols than the plan allows")
    void respectsSymbolLimit() {
        assertEquals(List.of("AAPL"), newStream(1).streamedSymbols());
    }

    @Test
    @DisplayName("Puts each trade into the price pool")
    void recordsTrades() {
        stream.handleMessage("[{\"T\":\"t\",\"S\":\"AAPL\",\"p\":227.31,\"s\":100,\"t\":\"2026-10-12T14:30:01.123456789Z\"},"
                + "{\"T\":\"t\",\"S\":\"MSFT\",\"p\":431.05,\"s\":5,\"t\":\"2026-10-12T14:30:02Z\"}]", sent::add);

        ArgumentCaptor<PriceQuote> quotes = ArgumentCaptor.forClass(PriceQuote.class);
        verify(prices, times(2)).update(quotes.capture());
        assertEquals(new PriceQuote("AAPL", new BigDecimal("227.31"),
                Instant.parse("2026-10-12T14:30:01.123456789Z")), quotes.getAllValues().get(0));
        assertEquals("MSFT", quotes.getAllValues().get(1).symbol());
    }

    @Test
    @DisplayName("Ignores malformed messages and incomplete trades")
    void ignoresBadMessages() {
        stream.handleMessage("not json", sent::add);
        stream.handleMessage("[{\"T\":\"t\",\"S\":\"AAPL\"}]", sent::add);
        stream.handleMessage("[{\"T\":\"t\",\"S\":\"AAPL\",\"p\":227.31,\"t\":\"yesterday\"}]", sent::add);
        stream.handleMessage("[{\"T\":\"q\",\"S\":\"AAPL\",\"bp\":227.30}]", sent::add);

        verify(prices, never()).update(any());
        assertTrue(sent.isEmpty());
        assertEquals(0, stream.tradesReceived());
    }

    @Test
    @DisplayName("Reports its state and the trades received, for the metrics")
    void reportsStateForMetrics() {
        assertEquals(AlpacaPriceStream.State.OFF, stream.state());

        stream.handleMessage("[{\"T\":\"subscription\",\"trades\":[\"AAPL\"]}]", sent::add);
        stream.handleMessage("[{\"T\":\"t\",\"S\":\"AAPL\",\"p\":227.31,\"t\":\"2026-10-12T14:30:01Z\"}]", sent::add);
        assertEquals(AlpacaPriceStream.State.STREAMING, stream.state());
        assertEquals(1, stream.tradesReceived());

        stream.handleMessage("[{\"T\":\"error\",\"code\":402,\"msg\":\"auth failed\"}]", sent::add);
        assertEquals(AlpacaPriceStream.State.FAILED, stream.state());
    }
}
