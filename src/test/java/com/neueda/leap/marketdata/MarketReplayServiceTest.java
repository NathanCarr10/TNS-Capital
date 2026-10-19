package com.neueda.leap.marketdata;

import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.MarketDataUnavailableException;
import com.neueda.leap.exceptions.ReplayInProgressException;
import com.neueda.leap.exceptions.ReplayNotFoundException;
import com.neueda.leap.kafka.MarketDataPublisher;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.time.FixedClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MarketReplayService")
class MarketReplayServiceTest {
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 9);
    // 09:30 and 16:00 New York time (EDT, UTC-4)
    private static final Instant OPEN = Instant.parse("2026-10-09T13:30:00Z");
    private static final Instant CLOSE = Instant.parse("2026-10-09T20:00:00Z");

    private AlpacaRestClient restClient;
    private InstrumentRepository instrumentRepository;
    private MarketDataPublisher publisher;
    private MarketReplayService service;

    @BeforeEach
    void setUp() {
        restClient = mock(AlpacaRestClient.class);
        instrumentRepository = mock(InstrumentRepository.class);
        publisher = mock(MarketDataPublisher.class);
        when(instrumentRepository.findBySymbol("SPY"))
                .thenReturn(Optional.of(new Instrument("SPY", "SPDR S&P 500 ETF", "ETF", "USD", true)));
        service = newService("key");
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    private MarketReplayService newService(String keyId) {
        AlpacaProperties properties = new AlpacaProperties(keyId, "secret", "iex", "wss://example.test/v2/",
                "https://example.test", 30, Duration.ofSeconds(60));
        return new MarketReplayService(restClient, properties, instrumentRepository, publisher,
                new FixedClock(Instant.parse("2026-10-19T12:00:00Z")), "sip");
    }

    private static List<PriceBar> bars(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new PriceBar(OPEN.plusSeconds(60L * i), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE,
                        BigDecimal.valueOf(500 + i), 100))
                .toList();
    }

    @Test
    @DisplayName("Publishes every bar of the day's regular session in order, then stops")
    void replaysTheSession() {
        when(restClient.minuteBars("SPY", OPEN, CLOSE, "sip")).thenReturn(bars(3));

        ReplayStatus started = service.start("SPY", FRIDAY, 50, "trader");

        assertEquals("SPY", started.symbol());
        assertEquals(FRIDAY, started.date());
        assertEquals(3, started.totalBars());
        assertEquals("trader", started.startedBy());
        ArgumentCaptor<MarketTick> ticks = ArgumentCaptor.forClass(MarketTick.class);
        verify(publisher, timeout(2000).times(3)).publish(ticks.capture());
        assertEquals(List.of(500, 501, 502), ticks.getAllValues().stream().map(t -> t.price().intValue()).toList());
        assertTrue(ticks.getAllValues().stream()
                .allMatch(t -> t.source() == MarketTick.Source.REPLAY && started.replayId().equals(t.replayId())));
        awaitFinished();
        ReplayStatus finished = service.current().orElseThrow();
        assertEquals(3, finished.barsPublished());
    }

    @Test
    @DisplayName("Runs one replay at a time, and can be stopped")
    void oneReplayAtATime() {
        when(restClient.minuteBars("SPY", OPEN, CLOSE, "sip")).thenReturn(bars(390));
        service.start("SPY", FRIDAY, 1, "trader");

        assertThrows(ReplayInProgressException.class, () -> service.start("SPY", FRIDAY, 1, "someone"));

        ReplayStatus stopped = service.stop();
        assertTrue(stopped.barsPublished() < 390);
        assertThrows(ReplayNotFoundException.class, () -> service.stop());
        awaitFinished();
    }

    @Test
    @DisplayName("Refuses a day without bars, such as a weekend")
    void refusesDayWithoutBars() {
        LocalDate saturday = FRIDAY.plusDays(1);
        when(restClient.minuteBars(eq("SPY"), any(), any(), anyString())).thenReturn(List.of());

        assertThrows(ReplayNotFoundException.class, () -> service.start("SPY", saturday, 10, "trader"));
        assertTrue(service.current().isEmpty());
    }

    @Test
    @DisplayName("Refuses an unknown or untradable instrument, and works only with market data keys")
    void refusesWithoutInstrumentOrKeys() {
        when(instrumentRepository.findBySymbol("OLD"))
                .thenReturn(Optional.of(new Instrument("OLD", "Delisted", "EQUITY", "USD", false)));

        assertThrows(InstrumentNotFoundException.class, () -> service.start("NOPE", FRIDAY, 10, "trader"));
        assertThrows(InstrumentNotFoundException.class, () -> service.start("OLD", FRIDAY, 10, "trader"));
        MarketReplayService withoutKeys = newService("");
        try {
            assertThrows(MarketDataUnavailableException.class, () -> withoutKeys.start("SPY", FRIDAY, 10, "trader"));
        } finally {
            withoutKeys.shutdown();
        }
        verify(restClient, never()).minuteBars(anyString(), any(), any(), anyString());
    }

    private void awaitFinished() {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (service.current().map(ReplayStatus::running).orElse(false) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertFalse(service.current().orElseThrow().running());
    }
}
