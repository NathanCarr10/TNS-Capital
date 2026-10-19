package com.neueda.leap.strategy.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.strategy.config.StrategyProperties;
import com.neueda.leap.strategy.engine.StrategyEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Consumes the market-data topic (spec §10.3): turns the prices into 1-minute
 * bars and runs the strategies on each bar that closes.
 *
 * Messages published longer than strategy.stale-after ago are skipped: after
 * a restart, trading on an old backlog of prices would be wrong. A message
 * that cannot be read is logged and skipped; prices are only useful while
 * fresh, so there is nothing to replay later.
 */
@Component
public class MarketDataListener {
    private static final Logger log = LoggerFactory.getLogger(MarketDataListener.class);

    private final ObjectMapper objectMapper;
    private final BarSeriesStore store;
    private final StrategyEngine engine;
    private final StrategyProperties properties;
    private final Clock clock;

    public MarketDataListener(ObjectMapper objectMapper, BarSeriesStore store, StrategyEngine engine,
            StrategyProperties properties, Clock clock) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.store = Objects.requireNonNull(store);
        this.engine = Objects.requireNonNull(engine);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    @KafkaListener(topics = "${strategy.market-data-topic:market-data}", groupId = "strategy-service")
    public void onMessage(String message) {
        MarketTick tick;
        try {
            JsonNode envelope = objectMapper.readTree(message);
            Instant published = Instant.parse(envelope.path("timestamp").asText());
            if (published.plus(properties.staleAfter()).isBefore(clock.instant())) {
                return;
            }
            tick = objectMapper.treeToValue(envelope.get("payload"), MarketTick.class);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Skipping an unreadable market-data message: {}", e.getMessage());
            return;
        }
        if (tick == null || tick.symbol() == null || tick.price() == null || tick.time() == null
                || tick.source() == null) {
            log.warn("Skipping an incomplete market-data message: {}", message);
            return;
        }
        store.onTick(tick).ifPresent(engine::onBar);
    }
}
