package com.neueda.leap.strategy.marketdata;

import com.neueda.leap.strategy.config.StrategyProperties;
import com.neueda.leap.strategy.domain.SignalSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BarSeriesStore")
class BarSeriesStoreTest {
    private static final Instant T0 = Instant.parse("2026-10-19T14:30:00Z");
    private final BarSeriesStore store = new BarSeriesStore(
            new StrategyProperties(1, Duration.ofMinutes(2), 3, "http://api", "http://auth", "strategy-service", "x"));

    private static MarketTick live(String price, Instant time) {
        return new MarketTick("SPY", new BigDecimal(price), time, SignalSource.LIVE, null);
    }

    private static MarketTick replay(String price, int minute, String replayId) {
        return new MarketTick("SPY", new BigDecimal(price), T0.plusSeconds(60L * minute), SignalSource.REPLAY, replayId);
    }

    @Test
    @DisplayName("Closes a live bar at its last trade when the next minute's first trade arrives")
    void closesLiveBars() {
        assertTrue(store.onTick(live("580.00", T0.plusSeconds(5))).isEmpty());
        assertTrue(store.onTick(live("580.50", T0.plusSeconds(50))).isEmpty());

        Optional<ClosedBar> closed = store.onTick(live("581.00", T0.plusSeconds(61)));

        assertEquals(T0, closed.orElseThrow().time());
        assertEquals(new BigDecimal("580.50"), closed.get().close());
        assertEquals(List.of(580.5), closed.get().closes());
        assertEquals(SignalSource.LIVE, closed.get().source());
    }

    @Test
    @DisplayName("Ignores a late trade from a minute that has closed")
    void ignoresLateTrades() {
        store.onTick(live("580.00", T0));
        store.onTick(live("581.00", T0.plusSeconds(60)));

        assertTrue(store.onTick(live("579.00", T0.plusSeconds(30))).isEmpty());
        assertEquals(new BigDecimal("580.00"), store.view("SPY").get(0).lastClose());
    }

    @Test
    @DisplayName("Takes each replayed price as a closed bar, and starts afresh for a new replay")
    void replaysBars() {
        store.onTick(replay("500", 0, "replay-1"));
        assertEquals(List.of(500.0, 501.0), store.onTick(replay("501", 1, "replay-1")).orElseThrow().closes());

        ClosedBar first = store.onTick(replay("600", 0, "replay-2")).orElseThrow();

        assertEquals(List.of(600.0), first.closes());
        assertEquals("replay-2", first.replayId());
    }

    @Test
    @DisplayName("Keeps live and replayed prices apart, and only the latest max-bars closes")
    void keepsSeriesApart() {
        for (int minute = 0; minute < 5; minute++) {
            store.onTick(replay(String.valueOf(500 + minute), minute, "replay-1"));
        }
        store.onTick(live("580.00", T0));
        store.onTick(live("581.00", T0.plusSeconds(60)));

        List<BarSeriesStore.SeriesView> views = store.view("SPY");

        assertEquals(2, views.size());
        assertEquals(new BarSeriesStore.SeriesView(SignalSource.LIVE, null, 1, new BigDecimal("580.00"), T0), views.get(0));
        assertEquals(3, views.get(1).barsCollected());
        assertEquals(new BigDecimal("504"), views.get(1).lastClose());
        assertTrue(store.view("AAPL").isEmpty());
    }
}
