package com.neueda.leap.strategy.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neueda.leap.strategy.config.StrategyProperties;
import com.neueda.leap.strategy.domain.SignalSource;
import com.neueda.leap.strategy.engine.StrategyEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("MarketDataListener")
class MarketDataListenerTest {
    private static final Instant NOW = Instant.parse("2026-10-19T14:32:00Z");

    private BarSeriesStore store;
    private StrategyEngine engine;
    private MarketDataListener listener;

    @BeforeEach
    void setUp() {
        store = mock(BarSeriesStore.class);
        engine = mock(StrategyEngine.class);
        StrategyProperties properties = new StrategyProperties(1, Duration.ofMinutes(2), 250, "http://api",
                "http://auth", "strategy-service", "x");
        listener = new MarketDataListener(new ObjectMapper().registerModule(new JavaTimeModule()), store, engine,
                properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String message(Instant published, String payload) {
        return """
                {"timestamp": "%s", "correlationId": "c-1", "schemaVersion": "1.0", "messageType": "MarketTick",
                 "payload": %s}""".formatted(published, payload);
    }

    @Test
    @DisplayName("Feeds each price to the bar series and runs the strategies on a bar it closes")
    void runsStrategiesOnClosedBars() {
        MarketTick tick = new MarketTick("SPY", new BigDecimal("580.12"), Instant.parse("2026-10-19T14:31:05Z"),
                SignalSource.LIVE, null);
        ClosedBar bar = new ClosedBar("SPY", SignalSource.LIVE, null, Instant.parse("2026-10-19T14:30:00Z"),
                new BigDecimal("580.00"), java.util.List.of(580.0));
        when(store.onTick(tick)).thenReturn(Optional.of(bar));

        listener.onMessage(message(NOW.minusSeconds(1), """
                {"symbol": "SPY", "price": 580.12, "time": "2026-10-19T14:31:05Z", "source": "LIVE", "replayId": null}"""));

        verify(engine).onBar(bar);
    }

    @Test
    @DisplayName("Skips prices published too long ago, e.g. a backlog after a restart")
    void skipsStaleMessages() {
        listener.onMessage(message(NOW.minusSeconds(121), """
                {"symbol": "SPY", "price": 580.12, "time": "2026-10-19T14:29:00Z", "source": "LIVE"}"""));

        verifyNoInteractions(store, engine);
    }

    @ParameterizedTest
    @ValueSource(strings = { "not json", "{\"timestamp\": \"yesterday\"}" })
    @DisplayName("Skips unreadable messages")
    void skipsUnreadable(String message) {
        listener.onMessage(message);

        verifyNoInteractions(store, engine);
    }

    @Test
    @DisplayName("Skips a message without a price")
    void skipsIncomplete() {
        listener.onMessage(message(NOW, "{\"symbol\": \"SPY\", \"source\": \"LIVE\"}"));

        verify(store, never()).onTick(any());
    }
}
