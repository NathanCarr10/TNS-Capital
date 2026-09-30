package com.neueda.leap.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka configuration for TNS Capital order processing system.
 * 
 * Defines Kafka topics and Spring Kafka beans for order messaging.
 */
@Configuration
public class KafkaConfig {

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
}
