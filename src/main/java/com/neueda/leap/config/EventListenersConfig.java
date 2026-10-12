package com.neueda.leap.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener containers for the consumers that are not the order consumer:
 * the trade-events audit consumer and the executions (fill) consumer.
 * 
 * They are kept apart from the order listener's container because their
 * error handling differs: the order error handler rejects the order and
 * captures it in the DLQ table. Here a message that keeps failing (e.g. it
 * cannot be read, or the database is down) is retried twice, one second
 * apart, then published unchanged to the topic's own .dlq topic.
 */
@Configuration
public class EventListenersConfig {
    public static final String EXECUTIONS_TOPIC = "executions";
    public static final String EXECUTIONS_DLQ_TOPIC = "executions.dlq";

    private final String bootstrapServers;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public EventListenersConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.bootstrapServers = bootstrapServers;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Fills from the Broker Simulator, keyed by account. The broker declares
     * it too, so whichever service starts first creates it.
     */
    @Bean
    public NewTopic executionsTopic() {
        return TopicBuilder.name(EXECUTIONS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic executionsDlqTopic() {
        return TopicBuilder.name(EXECUTIONS_DLQ_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> auditListenerContainerFactory() {
        return containerFactory(KafkaConfig.TRADE_EVENTS_DLQ_TOPIC);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> executionListenerContainerFactory() {
        return containerFactory(EXECUTIONS_DLQ_TOPIC);
    }

    private ConcurrentKafkaListenerContainerFactory<String, String> containerFactory(String dlqTopic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        DeadLetterPublishingRecoverer toDlq = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (consumerRecord, exception) -> new TopicPartition(dlqTopic, -1));

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
        factory.setCommonErrorHandler(new DefaultErrorHandler(toDlq, new FixedBackOff(1000L, 2L)));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
