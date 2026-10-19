package com.neueda.leap.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.marketdata.MarketTick;
import com.neueda.leap.marketdata.PriceBar;
import com.neueda.leap.marketdata.PriceQuote;
import com.neueda.leap.marketdata.PriceUpdatedEvent;
import com.neueda.leap.time.FixedClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataPublisher")
class MarketDataPublisherTest {
    private static final Instant NOW = Instant.parse("2026-10-19T14:30:00Z");

    private KafkaTemplate<String, String> kafkaTemplate;
    private FixedClock clock;
    private MarketDataPublisher publisher;
    // As Spring Boot configures it: times as ISO-8601 text
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        clock = new FixedClock(NOW);
        publisher = new MarketDataPublisher(kafkaTemplate, objectMapper, clock, "market-data", Duration.ofSeconds(1));
    }

    @AfterEach
    void tearDown() {
        publisher.stop();
    }

    private static PriceUpdatedEvent price(String symbol, String price) {
        return new PriceUpdatedEvent(new PriceQuote(symbol, new BigDecimal(price), NOW));
    }

    private List<String> sentMessages(int count) {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, timeout(2000).times(count)).send(eq("market-data"), anyString(), json.capture());
        return json.getAllValues();
    }

    @Test
    @DisplayName("Publishes a live price keyed by symbol, in the platform's message envelope")
    void publishesLivePrice() throws Exception {
        publisher.onPriceUpdated(price("SPY", "580.12"));

        verify(kafkaTemplate, timeout(2000)).send(eq("market-data"), eq("SPY"), anyString());
        JsonNode message = objectMapper.readTree(sentMessages(1).get(0));
        assertEquals("MarketTick", message.get("messageType").asText());
        assertEquals("1.0", message.get("schemaVersion").asText());
        assertTrue(message.hasNonNull("correlationId"));
        JsonNode payload = message.get("payload");
        assertEquals("SPY", payload.get("symbol").asText());
        assertEquals(new BigDecimal("580.12"), payload.get("price").decimalValue());
        assertEquals("LIVE", payload.get("source").asText());
        assertTrue(payload.get("replayId").isNull());
    }

    @Test
    @DisplayName("Publishes at most one live price per symbol per interval")
    void samplesBusySymbols() {
        publisher.onPriceUpdated(price("SPY", "580.12"));
        publisher.onPriceUpdated(price("SPY", "580.13"));
        publisher.onPriceUpdated(price("AAPL", "227.10"));
        clock.incrementSeconds(1);
        publisher.onPriceUpdated(price("SPY", "580.20"));

        verify(kafkaTemplate, timeout(2000).times(2)).send(eq("market-data"), eq("SPY"), anyString());
        verify(kafkaTemplate, timeout(2000).times(1)).send(eq("market-data"), eq("AAPL"), anyString());
    }

    @Test
    @DisplayName("Publishes every replayed bar, with its replay id")
    void publishesReplayedBars() throws Exception {
        PriceBar bar = new PriceBar(Instant.parse("2026-10-09T13:30:00Z"), new BigDecimal("580.1"),
                new BigDecimal("580.9"), new BigDecimal("579.8"), new BigDecimal("580.5"), 1000);

        publisher.publish(MarketTick.replay("SPY", bar, "replay-1"));
        publisher.publish(MarketTick.replay("SPY", bar, "replay-1"));

        JsonNode payload = objectMapper.readTree(sentMessages(2).get(1)).get("payload");
        assertEquals("REPLAY", payload.get("source").asText());
        assertEquals("replay-1", payload.get("replayId").asText());
        assertEquals(new BigDecimal("580.5"), payload.get("price").decimalValue());
        assertEquals("2026-10-09T13:30:00Z", payload.get("time").asText());
    }

    @Test
    @DisplayName("Keeps going when Kafka cannot be reached")
    void survivesKafkaFailure() {
        when(kafkaTemplate.send(anyString(), eq("SPY"), anyString())).thenThrow(new IllegalStateException("timeout"));

        publisher.onPriceUpdated(price("SPY", "580.12"));
        publisher.onPriceUpdated(price("AAPL", "227.10"));

        verify(kafkaTemplate, timeout(2000)).send(eq("market-data"), eq("AAPL"), any());
        verify(kafkaTemplate, times(1)).send(eq("market-data"), eq("SPY"), any());
    }
}
