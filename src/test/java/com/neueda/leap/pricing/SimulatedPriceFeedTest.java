package com.neueda.leap.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.time.Clock;
import com.neueda.leap.time.ClockTest;

@DisplayName("SimulatedPriceFeed")
class SimulatedPriceFeedTest {
    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    private SimulatedPriceFeed feed;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
        feed = new SimulatedPriceFeed(instrumentRepository, kafkaTemplate, objectMapper, new ClockTest(NOW),
                new Random(42), "market-data", 0.01);
    }

    private static Instrument priced(String symbol, boolean tradable, String price) {
        Instrument instrument = new Instrument(symbol, symbol + " Inc", "Equity", "USD", tradable);
        if (price != null) {
            instrument.updatePrice(new BigDecimal(price), Instant.parse("2026-10-01T00:00:00Z"));
        }
        return instrument;
    }

    @Test
    @DisplayName("moves tradable prices, stamps them and publishes each one keyed by symbol")
    void tickUpdatesAndPublishesPrices() {
        Instrument acme = priced("ACME", true, "100.00");
        when(instrumentRepository.findAll()).thenReturn(List.of(acme));

        feed.tick();

        assertNotEquals(new BigDecimal("100.00"), acme.getLastPrice());
        assertEquals(NOW, acme.getLastPriceOn());
        verify(kafkaTemplate).send(eq("market-data"), eq("ACME"),
                contains("\"price\":" + acme.getLastPrice().toPlainString()));
        verify(instrumentRepository).saveAll(List.of(acme));
    }

    @Test
    @DisplayName("leaves untradable and unpriced instruments alone")
    void tickSkipsUntradableAndUnpriced() {
        Instrument halted = priced("GLOB", false, "40.00");
        Instrument unpriced = priced("NEWCO", true, null);
        when(instrumentRepository.findAll()).thenReturn(List.of(halted, unpriced));

        feed.tick();

        assertEquals(new BigDecimal("40.00"), halted.getLastPrice());
        assertNull(unpriced.getLastPrice());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("keeps each step small and never goes below one cent")
    void nextPriceStaysCloseAndPositive() {
        for (int i = 0; i < 1000; i++) {
            BigDecimal next = feed.nextPrice(new BigDecimal("100.00"));
            assertTrue(next.compareTo(new BigDecimal("90")) > 0 && next.compareTo(new BigDecimal("110")) < 0,
                    "a 1% volatility step should stay within 10%: " + next);
            assertEquals(2, next.scale());
        }
        assertTrue(feed.nextPrice(new BigDecimal("0.01")).compareTo(new BigDecimal("0.01")) >= 0);
    }

    @Test
    @DisplayName("is created by Spring when enabled, and not when disabled")
    void springCreatesFeedOnlyWhenEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(InstrumentRepository.class, () -> instrumentRepository)
                .withBean(KafkaTemplate.class, () -> kafkaTemplate)
                .withBean(ObjectMapper.class, () -> objectMapper)
                .withBean(Clock.class, () -> new ClockTest(NOW))
                .withUserConfiguration(SimulatedPriceFeed.class);

        runner.run(context -> assertThat(context).hasSingleBean(SimulatedPriceFeed.class)
                .hasBean("marketDataTopic"));
        runner.withPropertyValues("pricing.feed.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(SimulatedPriceFeed.class));
    }
}
