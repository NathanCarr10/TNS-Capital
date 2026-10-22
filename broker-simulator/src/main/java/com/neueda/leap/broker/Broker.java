package com.neueda.leap.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Works accepted orders: after the market's delay, fills each one and
 * publishes the fill to the executions topic, keyed by account so one
 * account's fills arrive in order.
 */
@Component
public class Broker {
    private static final Logger log = LoggerFactory.getLogger(Broker.class);

    private final SimulatedMarket market;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final BrokerProperties properties;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    @Autowired
    public Broker(SimulatedMarket market, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
            BrokerProperties properties) {
        this(market, kafkaTemplate, objectMapper, properties, Executors.newScheduledThreadPool(2), Clock.systemUTC());
    }

    Broker(SimulatedMarket market, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
            BrokerProperties properties, ScheduledExecutorService scheduler, Clock clock) {
        this.market = market;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @Bean
    public static NewTopic executionsTopic(BrokerProperties properties) {
        return TopicBuilder.name(properties.executionsTopic()).partitions(3).replicas(1).build();
    }

    /** Queues the order; it is filled after the market's delay. */
    public void accept(BrokerOrder order) {
        long delay = market.fillDelay().toMillis();
        log.info("Accepted order {} ({} {} {} limit {}, market {}), filling in {} ms", order.orderId(), order.side(),
                order.quantity(), order.symbol(), order.limitPrice(), order.marketPrice(), delay);
        scheduler.schedule(() -> fill(order), delay, TimeUnit.MILLISECONDS);
    }

    void fill(BrokerOrder order) {
        ExecutionEvent fill = new ExecutionEvent(
                UUID.randomUUID(),
                order.orderId(),
                order.accountId(),
                order.symbol(),
                order.side(),
                order.quantity(),
                market.fillPrice(order.side(), order.limitPrice(), order.marketPrice()),
                properties.venue(),
                clock.instant());
        try {
            kafkaTemplate.send(properties.executionsTopic(), order.accountId().toString(),
                    objectMapper.writeValueAsString(fill))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Could not publish fill for order {}: {}", order.orderId(), error.getMessage());
                        } else {
                            log.info("Filled order {} at {}", order.orderId(), fill.price());
                        }
                    });
        } catch (JsonProcessingException ex) {
            log.error("Could not serialise fill for order {}: {}", order.orderId(), ex.getMessage());
        }
    }
}
