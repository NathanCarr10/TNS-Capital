package com.neueda.leap.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.enums.OrderStatus;
import com.neueda.leap.model.Order;
import com.neueda.leap.exceptions.NonRetryableOrderException;
import com.neueda.leap.kafka.TradeEventPublisher;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.services.DeadLetterService;
import com.neueda.leap.services.ReservationService;
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
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

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

    private final OrderRepository orderRepository;
    private final DeadLetterService deadLetterService;
    private final TradeEventPublisher tradeEventPublisher;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ReservationService reservations;

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
     * - auto-commit off: offsets are committed per record after processing
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
        // The listener container commits each offset once the record is processed
        // (or routed to the DLQ); auto-commit could commit a message that was
        // never processed if the app stopped in between
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

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
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        log.info("Kafka listener container factory configured with exponential backoff error handler");
        return factory;
    }

    /**
     * Error handler with NO RETRY logic - immediate DLQ routing.
     * 
     * All exceptions (retryable and non-retryable) are routed directly to DLQ
     * without retry attempts.
     * 
     * When an exception occurs, the recovery callback is invoked which:
     * 1. Deserializes the message envelope from the ConsumerRecord
     * 2. Extracts the OrderEvent and failure details
     * 3. Calls DeadLetterService.captureFailedMessage() to persist in dlq_messages
     * table
     * 4. Message is marked with status=PENDING for administrative review and replay
     * 
     * @return CommonErrorHandler with NO retry strategy and DLQ recovery
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        // Recovery callback: invoked immediately on exception (no retries).
        // The BackOff must be explicit: DefaultErrorHandler defaults to
        // FixedBackOff(0, 9), i.e. 10 delivery attempts per failed record.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                this::handleRecovery,
                new FixedBackOff(0L, 0L));

        log.info(
                "Kafka error handler configured: NO RETRIES - all exceptions routed immediately to DLQ");
        return errorHandler;
    }

    /**
     * Handles recovery when a message fails after max retries or when a
     * non-retryable exception occurs.
     * Captures the failed message to the Dead-Letter Queue for administrative
     * review and explicitly updates order status to REJECTED.
     * 
     * Also publishes a trade lifecycle event to notify downstream systems
     * (Settlement, Risk Dashboard) that the order was rejected.
     * 
     * DEFENSIVE GUARANTEE: This method ensures that every NEW order reaching the
     * DLQ ends REJECTED with an associated lifecycle event, even if
     * saveRejectedOrder() failed in the async consumer. An order that already
     * has a final status (FILLED, CANCELLED) keeps it: it was settled or
     * cancelled while this message failed, e.g. a cancellation that won the
     * race against a broker error.
     * 
     * A message that cannot be read is forwarded unchanged to orders.dlq, so a
     * copy is kept for investigation instead of being lost when its offset is
     * committed.
     * 
     * @param consumerRecord the Kafka consumer record that failed
     * @param exception      the exception that caused the failure
     */
    void handleRecovery(ConsumerRecord<?, ?> consumerRecord, Exception exception) {
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

            OrderEvent event = envelope.getPayload();

            // DEFENSIVE GUARANTEE: a NEW order ends REJECTED even if saveRejectedOrder()
            // failed earlier, with the same failure reason that is stored in dlq_messages
            String failureReason = DeadLetterService.buildFailureReason(exception);
            orderRepository.findById(event.orderId())
                    .ifPresent(order -> rejectIfStillNew(order, failureReason));

            // Capture the failed message to DLQ database table
            // Pass the retryability flag so admin UI knows which messages can be safely replayed
            deadLetterService.captureFailedMessage(envelope, exception, 0, isNonRetryable);
            log.info("Captured failed message to DLQ: orderId={}, isNonRetryable={}",
                    event.orderId(), isNonRetryable);

            // Also publish to orders.dlq Kafka topic for audit trail
            kafkaTemplate.send(ORDERS_DLQ_TOPIC, event.accountId().toString(),
                    (String) consumerRecord.value());
            log.debug("Published failed message to orders.dlq topic: orderId={}",
                    event.orderId());

        } catch (JsonProcessingException jsonException) {
            // The message cannot be read, so it has no order to reject or capture in the
            // DLQ table. Its offset is committed after this returns, so keep the raw
            // message on orders.dlq for manual investigation
            log.error(
                    "Unreadable order message, forwarding it to {}: topic={}, partition={}, offset={}, deserializationError={}",
                    ORDERS_DLQ_TOPIC, consumerRecord.topic(), consumerRecord.partition(), consumerRecord.offset(),
                    jsonException.getMessage(), jsonException);
            kafkaTemplate.send(ORDERS_DLQ_TOPIC, String.valueOf(consumerRecord.value()));
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
     * Stores a NEW order as REJECTED, releasing the cash or shares it reserved,
     * and publishes its REJECTED event. An order
     * already REJECTED (its event was published during processing) only gets
     * the failure reason if it has none; FILLED and CANCELLED orders keep
     * their status.
     */
    private void rejectIfStillNew(Order order, String failureReason) {
        OrderStatus status = order.getStatus();
        switch (status) {
            case NEW -> reservations.rejectIfNew(order.getId(), failureReason).ifPresent(rejected -> {
                log.info("Updated order status to REJECTED in recovery handler (was NEW): orderId={}", order.getId());
                tradeEventPublisher.publish(rejected, status, "EXHAUSTED_RETRIES");
            });
            case REJECTED -> {
                if (order.getStatusReason() == null) {
                    order.setStatusReason(failureReason);
                    orderRepository.save(order);
                }
                log.debug("Order already REJECTED (trade event was published during processing): orderId={}",
                        order.getId());
            }
            default -> log.info("Order already {}, keeping its status: orderId={}", status, order.getId());
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
