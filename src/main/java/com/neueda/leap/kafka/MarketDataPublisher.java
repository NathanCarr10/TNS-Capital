package com.neueda.leap.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.marketdata.MarketTick;
import com.neueda.leap.marketdata.PriceUpdatedEvent;
import com.neueda.leap.time.Clock;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publishes prices on the market-data topic (spec §10.3), keyed by symbol so
 * each symbol's prices stay in order on one partition.
 *
 * Live prices come from the live price pool. A busy symbol trades many times
 * a second, so at most one live price per symbol is published per
 * trading.market-data.publish-interval (a sample, enough for the strategy
 * service's 1-minute bars). Replayed bars (MarketReplayService) are published
 * as they come.
 *
 * Sending happens on its own thread, so a Kafka outage (a send can block for
 * max.block.ms) never holds up the price stream; if Kafka falls behind, the
 * oldest waiting prices are dropped, since a newer price replaces them.
 */
@Component
@Slf4j
public class MarketDataPublisher {
    static final String MESSAGE_TYPE = "MarketTick";
    static final String SCHEMA_VERSION = "1.0";
    private static final int QUEUE_CAPACITY = 1000;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String topic;
    private final Duration publishInterval;
    private final Map<String, Instant> lastPublished = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY), task -> {
                Thread thread = new Thread(task, "market-data-publisher");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());

    public MarketDataPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${trading.kafka.topics.market-data:market-data}") String topic,
            @Value("${trading.market-data.publish-interval:1s}") Duration publishInterval) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.topic = Objects.requireNonNull(topic);
        this.publishInterval = Objects.requireNonNull(publishInterval);
    }

    @PreDestroy
    void stop() {
        sender.shutdownNow();
    }

    /**
     * Publishes a live price, unless one was published for its symbol within
     * the publish interval.
     */
    @EventListener
    public void onPriceUpdated(PriceUpdatedEvent event) {
        if (isDue(event.quote().symbol())) {
            publish(MarketTick.live(event.quote()));
        }
    }

    /**
     * Queues a price for sending. Never blocks and never throws: a failed send is logged.
     */
    public void publish(MarketTick tick) {
        sender.execute(() -> send(tick));
    }

    private boolean isDue(String symbol) {
        Instant now = clock.now();
        AtomicBoolean due = new AtomicBoolean();
        lastPublished.compute(symbol, (key, last) -> {
            if (last == null || !now.isBefore(last.plus(publishInterval))) {
                due.set(true);
                return now;
            }
            return last;
        });
        return due.get();
    }

    private void send(MarketTick tick) {
        try {
            String json = objectMapper.writeValueAsString(new MessageEnvelope<>(
                    clock.now(), UUID.randomUUID().toString(), SCHEMA_VERSION, MESSAGE_TYPE, tick));
            kafkaTemplate.send(topic, tick.symbol(), json).whenComplete((result, error) -> {
                if (error != null) {
                    log.warn("Could not publish {} price of {} to {}: {}", tick.source(), tick.symbol(), topic,
                            error.getMessage());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Could not write the market-data message for {}", tick.symbol(), e);
        } catch (RuntimeException e) {
            // e.g. Kafka unreachable for max.block.ms: drop this price, the next one replaces it
            log.warn("Could not publish {} price of {} to {}: {}", tick.source(), tick.symbol(), topic, e.getMessage());
        }
    }
}
