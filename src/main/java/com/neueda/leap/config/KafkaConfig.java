package com.neueda.leap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.services.DeadLetterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for TNS Capital order processing system.
 * 
 * Defines Kafka topics and Spring Kafka beans for order messaging.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConfig {

    private final DeadLetterService deadLetterService;
    private final ObjectMapper objectMapper;

    @Value("${trading.kafka.topics.orders:orders}")
    private String ordersTopic;

    /**
     * Main topic for order events.
     * Orders are published to this topic for async processing.
     */
    public static final String ORDERS_TOPIC = "orders";

    /**
     * Dead-Letter Queue topic for failed orders.
     * Orders that fail processing are routed here for investigation and replay.
     */
    public static final String ORDERS_DLQ_TOPIC = "orders.dlq";

    /**
     * Trade events topic for executed trades.
     * Published whenever an order is successfully executed.
     * Partitioned by Account ID to ensure ordering per account.
     */
    public static final String TRADE_EVENTS_TOPIC = "trade-events";

    /**
     * Dead-Letter Queue topic for failed trade events.
     * Trade events that fail publishing are routed here.
     */
    public static final String TRADE_EVENTS_DLQ_TOPIC = "trade-events.dlq";

    /**
     * Creates the main orders topic.
     * 
     * Partitioned by Account ID (3 partitions) to ensure all orders for one
     * account land on the same partition and are processed in order.
     * 
     * @return NewTopic bean for the orders topic
     */
    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name(ORDERS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Creates the dead-letter queue topic for failed orders.
     * 
     * @return NewTopic bean for the orders DLQ topic
     */
    @Bean
    public NewTopic ordersDlqTopic() {
        return TopicBuilder.name(ORDERS_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Creates the trade-events topic.
     * 
     * Partitioned by Account ID (3 partitions) to ensure:
     * - Settlement system sees trades ordered per account
     * - Risk Dashboard sees trades ordered per account
     * - Compliance Audit has scalable consumption
     * 
     * @return NewTopic bean for the trade-events topic
     */
    @Bean
    public NewTopic tradeEventsTopic() {
        return TopicBuilder.name(TRADE_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Creates the dead-letter queue topic for failed trade events.
     * 
     * @return NewTopic bean for the trade-events DLQ topic
     */
    @Bean
    public NewTopic tradeEventsDlqTopic() {
        return TopicBuilder.name(TRADE_EVENTS_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Consumer factory for order event consumption.
     * 
     * Configures the Kafka consumer with:
     * - StringDeserializer for keys and values (messages are JSON strings)
     * - group-id: tns-capital-orders
     * - auto-offset-reset: earliest (process from beginning if no offset exists)
     * - max.poll.records: 50 (batch size)
     * 
     * @return ConsumerFactory configured for OrderEvent consumption
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "tns-capital-orders");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        configProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 1000);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Listener container factory with custom error handler.
     * 
     * Implements exponential backoff retry logic:
     * - Initial interval: 1 second
     * - Max interval: 10 seconds
     * - Multiplier: 2.0 (doubles on each retry)
     * - Max failures: 3 (after 3 retries, message is routed to DLQ)
     * 
     * The error handler captures failures to DeadLetterService.
     * 
     * @return ConcurrentKafkaListenerContainerFactory configured with error handler
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler());
        factory.setConcurrency(3); // 3 concurrent threads for parallel processing

        log.info("Kafka listener container factory configured with exponential backoff error handler");
        return factory;
    }

    /**
     * Error handler with exponential backoff retry and DLQ routing.
     * 
     * Retry strategy:
     * - Initial backoff: 1000ms (1 second)
     * - Max backoff: 10000ms (10 seconds)
     * - Multiplier: 2.0 (exponential increase)
     * - Max backoff: 10000ms (10 seconds)
     * - Multiplier: 2.0 (exponential increase)
     * 
     * The DefaultErrorHandler with ExponentialBackOff will automatically retry
     * failed messages with exponential backoff intervals until max retries are
     * exhausted,
     * at which point the message is considered a failure.
     * 
     * @return CommonErrorHandler with exponential backoff strategy
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1000, 2.0);
        backOff.setMaxInterval(10000); // Max 10 seconds between retries

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(backOff);

        log.info(
                "Kafka error handler configured with exponential backoff: initialInterval=1s, multiplier=2x, maxInterval=10s");
        return errorHandler;
    }
}
