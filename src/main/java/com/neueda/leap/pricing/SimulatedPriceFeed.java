package com.neueda.leap.pricing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.model.Instrument;
import com.neueda.leap.repositories.InstrumentRepository;
import com.neueda.leap.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.security.SecureRandom;
import java.util.Random;

/**
 * Stand-in for the live pricing API: moves each tradable instrument's price
 * by a small random step on a schedule, stores it as the instrument's last
 * price and publishes it to the market-data topic (keyed by symbol).
 *
 * Positions are valued at these prices (see PositionValuation). Replacing
 * this class with a client for a real pricing API leaves the rest unchanged.
 * Turn it off with pricing.feed.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "pricing.feed.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class SimulatedPriceFeed {
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");

    private final InstrumentRepository instrumentRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Random random;
    private final String marketDataTopic;
    private final double volatility;

    @Autowired
    public SimulatedPriceFeed(
            InstrumentRepository instrumentRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${trading.kafka.topics.market-data:market-data}") String marketDataTopic,
            @Value("${pricing.feed.volatility:0.01}") double volatility) {
        // SecureRandom, so the generator needs no security review even though prices are only simulated
        this(instrumentRepository, kafkaTemplate, objectMapper, clock, new SecureRandom(), marketDataTopic, volatility);
    }

    SimulatedPriceFeed(InstrumentRepository instrumentRepository, KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper, Clock clock, Random random, String marketDataTopic, double volatility) {
        this.instrumentRepository = Objects.requireNonNull(instrumentRepository);
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
        this.random = Objects.requireNonNull(random);
        this.marketDataTopic = Objects.requireNonNull(marketDataTopic);
        this.volatility = volatility;
    }

    /** One price update on the market-data topic */
    public record PriceUpdate(String symbol, BigDecimal price, String currency, Instant timestamp) {
    }

    @Bean
    public static NewTopic marketDataTopic(
            @Value("${trading.kafka.topics.market-data:market-data}") String marketDataTopic) {
        return TopicBuilder.name(marketDataTopic).partitions(1).replicas(1).build();
    }

    /**
     * Moves every priced, tradable instrument one step and publishes the new prices.
     */
    @Scheduled(fixedDelayString = "${pricing.feed.interval-ms:15000}")
    @Transactional
    public void tick() {
        Instant now = clock.now();
        List<Instrument> instruments = instrumentRepository.findAll().stream()
                .filter(instrument -> instrument.isTradable() && instrument.getLastPrice() != null)
                .toList();

        for (Instrument instrument : instruments) {
            instrument.updatePrice(nextPrice(instrument.getLastPrice()), now);
            publish(new PriceUpdate(instrument.getSymbol(), instrument.getLastPrice(), instrument.getCurrency(), now));
        }
        instrumentRepository.saveAll(instruments);
        log.debug("Published {} prices to {}", instruments.size(), marketDataTopic);
    }

    /** A random step of about +/- volatility (normally distributed), never below one cent */
    BigDecimal nextPrice(BigDecimal price) {
        BigDecimal factor = BigDecimal.valueOf(1 + random.nextGaussian() * volatility);
        return price.multiply(factor).setScale(2, RoundingMode.HALF_UP).max(MIN_PRICE);
    }

    private void publish(PriceUpdate update) {
        try {
            kafkaTemplate.send(marketDataTopic, update.symbol(), objectMapper.writeValueAsString(update))
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Failed to publish price for {}: {}", update.symbol(), error.getMessage());
                        }
                    });
        } catch (JsonProcessingException ex) {
            // A price that cannot be serialised is skipped; the next tick sends a new one
            log.error("Could not serialise price for {}: {}", update.symbol(), ex.getMessage());
        }
    }
}
