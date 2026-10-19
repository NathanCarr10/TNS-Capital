package com.neueda.leap.marketdata;

import com.neueda.leap.exceptions.InstrumentNotFoundException;
import com.neueda.leap.exceptions.MarketDataUnavailableException;
import com.neueda.leap.exceptions.ReplayInProgressException;
import com.neueda.leap.exceptions.ReplayNotFoundException;
import com.neueda.leap.kafka.MarketDataPublisher;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.time.Clock;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replays a past trading day on the market-data topic at high speed, so the
 * strategy service can be shown trading without waiting for the real market
 * (Strategy Lab demo, docs/DECISIONS.md #16).
 *
 * The regular session's 1-minute bars (09:30 to 16:00 New York time) are
 * fetched from Alpaca and published one by one as REPLAY ticks, each with the
 * bar's close. Only one replay runs at a time, since every strategy on the
 * symbol sees it. Replayed prices only drive strategy signals: orders are still
 * priced at the live market price, as the platform cannot fill at a past price.
 */
@Service
@Slf4j
public class MarketReplayService {
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime SESSION_OPEN = LocalTime.of(9, 30);
    private static final LocalTime SESSION_CLOSE = LocalTime.of(16, 0);
    private static final long MICROS_PER_SECOND = 1_000_000L;

    private final AlpacaRestClient restClient;
    private final AlpacaProperties properties;
    private final InstrumentRepository instrumentRepository;
    private final MarketDataPublisher publisher;
    private final Clock clock;
    private final String feed;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "market-replay");
        thread.setDaemon(true);
        return thread;
    });

    // The running replay, or the last one; guarded by this
    private Replay current;

    public MarketReplayService(
            AlpacaRestClient restClient,
            AlpacaProperties properties,
            InstrumentRepository instrumentRepository,
            MarketDataPublisher publisher,
            Clock clock,
            @Value("${market-data.replay.feed:sip}") String feed) {
        this.restClient = Objects.requireNonNull(restClient);
        this.properties = Objects.requireNonNull(properties);
        this.instrumentRepository = Objects.requireNonNull(instrumentRepository);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
        this.feed = Objects.requireNonNull(feed);
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    /**
     * Starts replaying a day's regular session.
     *
     * @throws InstrumentNotFoundException    if the symbol is not a tradable instrument
     * @throws MarketDataUnavailableException if no market data keys are configured
     * @throws ReplayInProgressException      if another replay is running
     * @throws ReplayNotFoundException        if the day has no bars (weekend or market holiday)
     */
    public synchronized ReplayStatus start(String symbol, LocalDate date, int barsPerSecond, String startedBy) {
        instrumentRepository.findBySymbol(symbol)
                .filter(Instrument::isTradable)
                .orElseThrow(() -> new InstrumentNotFoundException("Instrument not found or not tradable: " + symbol));
        if (!properties.isConfigured()) {
            throw new MarketDataUnavailableException("Replay needs ALPACA_API_KEY_ID and ALPACA_API_SECRET_KEY");
        }
        if (current != null && current.isRunning()) {
            throw new ReplayInProgressException("A replay of " + current.symbol + " is already running");
        }
        List<PriceBar> bars = restClient.minuteBars(symbol, sessionTime(date, SESSION_OPEN),
                sessionTime(date, SESSION_CLOSE), feed);
        if (bars.isEmpty()) {
            throw new ReplayNotFoundException("No 1-minute bars for " + symbol + " on " + date
                    + "; was the market open that day?");
        }

        Replay replay = new Replay(UUID.randomUUID().toString(), symbol, date, bars, barsPerSecond, clock.now(),
                startedBy);
        replay.task = scheduler.scheduleAtFixedRate(() -> publishNext(replay), 0,
                MICROS_PER_SECOND / barsPerSecond, TimeUnit.MICROSECONDS);
        current = replay;
        log.info("Replay {} started by {}: {} on {}, {} bars at {} a second", replay.id, startedBy, symbol, date,
                bars.size(), barsPerSecond);
        return replay.status();
    }

    /**
     * @return the running replay, or the last one; empty if none was started
     */
    public synchronized Optional<ReplayStatus> current() {
        return Optional.ofNullable(current).map(Replay::status);
    }

    /**
     * Stops the running replay.
     *
     * @throws ReplayNotFoundException if no replay is running
     */
    public synchronized ReplayStatus stop() {
        if (current == null || !current.isRunning()) {
            throw new ReplayNotFoundException("No replay is running");
        }
        current.task.cancel(false);
        log.info("Replay {} stopped after {} of {} bars", current.id, current.published.get(), current.bars.size());
        return current.status();
    }

    private void publishNext(Replay replay) {
        int index = replay.published.get();
        if (index >= replay.bars.size()) {
            replay.task.cancel(false);
            log.info("Replay {} finished: {} bars published", replay.id, index);
            return;
        }
        publisher.publish(MarketTick.replay(replay.symbol, replay.bars.get(index), replay.id));
        replay.published.incrementAndGet();
    }

    private static Instant sessionTime(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(NEW_YORK).toInstant();
    }

    private static final class Replay {
        private final String id;
        private final String symbol;
        private final LocalDate date;
        private final List<PriceBar> bars;
        private final int barsPerSecond;
        private final Instant startedAt;
        private final String startedBy;
        private final AtomicInteger published = new AtomicInteger();
        private volatile ScheduledFuture<?> task;

        private Replay(String id, String symbol, LocalDate date, List<PriceBar> bars, int barsPerSecond,
                Instant startedAt, String startedBy) {
            this.id = id;
            this.symbol = symbol;
            this.date = date;
            this.bars = bars;
            this.barsPerSecond = barsPerSecond;
            this.startedAt = startedAt;
            this.startedBy = startedBy;
        }

        boolean isRunning() {
            return task != null && !task.isDone();
        }

        ReplayStatus status() {
            return new ReplayStatus(id, symbol, date, published.get(), bars.size(), barsPerSecond, isRunning(),
                    startedAt, startedBy);
        }
    }
}
