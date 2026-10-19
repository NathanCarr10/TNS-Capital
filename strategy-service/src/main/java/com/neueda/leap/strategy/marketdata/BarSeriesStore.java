package com.neueda.leap.strategy.marketdata;

import com.neueda.leap.strategy.config.StrategyProperties;
import com.neueda.leap.strategy.domain.SignalSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the market-data stream into 1-minute closing prices, one series per
 * symbol and source, so a replay never mixes with live prices.
 *
 * Live prices are trades: a bar closes, at its last trade price, when the
 * first trade of a later minute arrives. A trade from a minute that has
 * already closed is ignored. Replayed prices are already 1-minute bar
 * closes; a new replay starts its series afresh.
 *
 * Only the last strategy.max-bars closes of each series are kept.
 */
@Component
public class BarSeriesStore {
    private final int maxBars;
    private final Map<SeriesKey, Series> series = new HashMap<>();

    public BarSeriesStore(StrategyProperties properties) {
        this.maxBars = properties.maxBars();
    }

    /**
     * @return the bar the tick closed, if it closed one
     */
    public synchronized Optional<ClosedBar> onTick(MarketTick tick) {
        Series current = series.computeIfAbsent(new SeriesKey(tick.symbol(), tick.source()), key -> new Series());
        return tick.source() == SignalSource.REPLAY ? current.onReplayBar(tick) : current.onTrade(tick);
    }

    /**
     * What the store holds for a symbol, live and replayed, for the Strategy Lab page.
     */
    public synchronized List<SeriesView> view(String symbol) {
        List<SeriesView> views = new ArrayList<>();
        for (SignalSource source : SignalSource.values()) {
            Series held = series.get(new SeriesKey(symbol, source));
            if (held != null && held.lastBarTime != null) {
                views.add(new SeriesView(source, held.replayId, held.closes.size(), held.lastClose, held.lastBarTime));
            }
        }
        return views;
    }

    /**
     * @param barsCollected closed 1-minute bars held
     * @param lastClose     close of the latest of them
     * @param lastBarTime   start of the latest of them
     */
    public record SeriesView(SignalSource source, String replayId, int barsCollected, BigDecimal lastClose,
            Instant lastBarTime) {
    }

    private record SeriesKey(String symbol, SignalSource source) {
    }

    private final class Series {
        private final Deque<Double> closes = new ArrayDeque<>();
        private String replayId;
        // The live bar still open: its start and latest trade
        private Instant openBarTime;
        private BigDecimal openBarLast;
        private Instant lastBarTime;
        private BigDecimal lastClose;

        Optional<ClosedBar> onTrade(MarketTick trade) {
            Instant minute = trade.time().truncatedTo(ChronoUnit.MINUTES);
            if (openBarTime == null || minute.equals(openBarTime)) {
                openBarTime = minute;
                openBarLast = trade.price();
                return Optional.empty();
            }
            if (minute.isBefore(openBarTime)) {
                return Optional.empty();
            }
            ClosedBar closed = close(trade.symbol(), SignalSource.LIVE, null, openBarTime, openBarLast);
            openBarTime = minute;
            openBarLast = trade.price();
            return Optional.of(closed);
        }

        Optional<ClosedBar> onReplayBar(MarketTick bar) {
            if (!bar.replayId().equals(replayId)) {
                closes.clear();
                replayId = bar.replayId();
            }
            return Optional.of(close(bar.symbol(), SignalSource.REPLAY, replayId, bar.time(), bar.price()));
        }

        private ClosedBar close(String symbol, SignalSource source, String replay, Instant time, BigDecimal close) {
            closes.addLast(close.doubleValue());
            if (closes.size() > maxBars) {
                closes.removeFirst();
            }
            lastBarTime = time;
            lastClose = close;
            return new ClosedBar(symbol, source, replay, time, close, List.copyOf(closes));
        }
    }
}
