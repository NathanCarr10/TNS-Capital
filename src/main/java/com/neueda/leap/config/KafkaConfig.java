package com.neueda.leap.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.exceptions.NonRetryableOrderException;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.services.DeadLetterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${trading.kafka.topics.orders:orders}")
    private String ordersTopic;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

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
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
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
     * SMART RETRY LOGIC:
     * - Retryable exceptions (business logic failures): Use exponential backoff
     * (1s, 2s, 4s)
     * - Max failures: 3 (after 3 retries, message is routed to DLQ via recovery
     * callback)
     * 
     * NOTE: NonRetryableOrderException is caught and handled in
     * OrderMessageListener
     * directly (see onOrderEvent), so this error handler only processes retryable
     * exceptions.
     * 
     * When max failures are exhausted, the recovery callback is invoked which:
     * 1. Deserializes the message envelope from the ConsumerRecord
     * 2. Extracts the OrderEvent and failure details
     * 3. Calls DeadLetterService.captureFailedMessage() to persist in dlq_messages
     * table
     * 4. Message is marked with status=PENDING for administrative review and replay
     * 
     * @return CommonErrorHandler with exponential backoff strategy and DLQ recovery
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1000, 2.0);
        backOff.setMaxInterval(10000); // Max 10 seconds between retries
        backOff.setMaxElapsedTime(30000); // Give up after ~30 seconds of retrying

        // Recovery callback: invoked when backoff strategy exhausted
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (consumerRecord, exception) -> handleRecovery(consumerRecord, exception),
                backOff);

        log.info(
                "Kafka error handler configured: exponential backoff (1s initial, 2x multiplier, 10s max interval, 30s max elapsed), DLQ recovery enabled");
        return errorHandler;
    }

    /**
     * Handles recovery when a message fails after max retries or when a
     * non-retryable exception occurs.
     * Captures the failed message to the Dead-Letter Queue for administrative
     * review.
     * 
     * @param consumerRecord the Kafka consumer record that failed
     * @param exception      the exception that caused the failure
     */
    private void handleRecovery(ConsumerRecord<?, ?> consumerRecord, Exception exception) {
        try {
            // Determine if this is a non-retryable error
            boolean isNonRetryable = isNonRetryableException(exception);

            log.warn(
                    "Max retries exhausted, capturing message to DLQ: topic={}, partition={}, offset={}, error={}, isNonRetryable={}",
                    consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(),
                    exception.getMessage(), isNonRetryable);

            // Deserialize the message envelope from the Kafka record using TypeReference
            MessageEnvelope<OrderEvent> envelope = objectMapper.readValue(
                    (String) consumerRecord.value(),
                    new TypeReference<MessageEnvelope<OrderEvent>>() {
                    });

            // Capture the failed message to DLQ database table
            // Pass the retryability flag so admin UI knows which messages can be safely
            // replayed
            deadLetterService.captureFailedMessage(envelope, exception, 3, isNonRetryable);

            // Also publish to orders.dlq Kafka topic for audit trail
            kafkaTemplate.send("orders.dlq", envelope.getPayload().accountId().toString(),
                    (String) consumerRecord.value());
            log.info("Published failed message to orders.dlq topic: orderId={}, isNonRetryable={}",
                    envelope.getPayload().orderId(), isNonRetryable);

        } catch (JsonProcessingException jsonException) {
            // Message deserialization failed - log the raw message for manual investigation
            log.error(
                    "Failed to deserialize order message for DLQ capture (message will remain in orders topic): topic={}, partition={}, offset={}, deserializationError={}",
                    consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(),
                    jsonException.getMessage(), jsonException);
        } catch (Exception recoveryException) {
            // Unexpected error during recovery - log but don't throw to prevent cascading
            // failures
            log.error(
                    "Unexpected error while capturing message to DLQ: topic={}, partition={}, offset={}, error={}",
                    consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(),
                    recoveryException.getMessage(), recoveryException);
        }
    }

    /**
     * Checks if an exception or its cause chain contains
     * NonRetryableOrderException.
     *
     * @param exception the exception to check
     * @return true if exception is non-retryable, false otherwise
     */
    private boolean isNonRetryableException(Exception exception) {
        if (exception instanceof NonRetryableOrderException) {
            return true;
        }

        // Check cause chain
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof NonRetryableOrderException) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }
}
