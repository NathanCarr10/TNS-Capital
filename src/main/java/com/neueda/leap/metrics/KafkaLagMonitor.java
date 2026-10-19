package com.neueda.leap.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Kafka consumer lag per consumer group and topic, read from the broker:
 * the topic's latest offsets minus the group's committed offsets.
 *
 * Measured from the broker rather than from the consumers' own client
 * metrics, so the lag keeps growing when a consumer has died, which is when
 * it matters. One admin client polls every 10 s, the scrape interval.
 *
 * Gauge: kafka.consumer.group.lag{group, topic}. Only runs with the metrics
 * port set (METRICS_PORT), as in Docker Compose.
 */
@Component
@Slf4j
@ConditionalOnExpression("${metrics.port:0} > 0")
public class KafkaLagMonitor {
    static final List<String> GROUPS = List.of("tns-capital-orders", "tns-capital-settlement", "tns-capital-audit");
    private static final long TIMEOUT_SECONDS = 5;

    private final MeterRegistry registry;
    private final Admin admin;
    private final Map<String, AtomicLong> lags = new ConcurrentHashMap<>();

    public KafkaLagMonitor(MeterRegistry registry, @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.registry = registry;
        this.admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.CLIENT_ID_CONFIG, "tns-capital-lag-monitor",
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10000));
    }

    @Scheduled(fixedDelay = 10, initialDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void poll() {
        for (String group : GROUPS) {
            try {
                Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(group)
                        .partitionsToOffsetAndMetadata().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                Map<TopicPartition, OffsetSpec> latest = committed.keySet().stream()
                        .collect(Collectors.toMap(partition -> partition, partition -> OffsetSpec.latest()));
                Map<TopicPartition, ListOffsetsResultInfo> ends = admin.listOffsets(latest).all()
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                lagByTopic(committed, ends).forEach((topic, lag) -> gauge(group, topic).set(lag));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Kafka down or the group not created yet: keep the last values
                log.debug("Could not read the lag of consumer group {}: {}", group, e.getMessage());
            }
        }
    }

    static Map<String, Long> lagByTopic(Map<TopicPartition, OffsetAndMetadata> committed,
            Map<TopicPartition, ListOffsetsResultInfo> ends) {
        Map<String, Long> byTopic = new HashMap<>();
        committed.forEach((partition, offset) -> {
            ListOffsetsResultInfo end = ends.get(partition);
            if (offset != null && end != null) {
                byTopic.merge(partition.topic(), Math.max(0, end.offset() - offset.offset()), Long::sum);
            }
        });
        return byTopic;
    }

    private AtomicLong gauge(String group, String topic) {
        return lags.computeIfAbsent(group + "/" + topic, key -> {
            AtomicLong lag = new AtomicLong();
            Gauge.builder("kafka.consumer.group.lag", lag, AtomicLong::get)
                    .description("Messages on the topic the consumer group has not yet committed")
                    .tag("group", group)
                    .tag("topic", topic)
                    .register(registry);
            return lag;
        });
    }

    @PreDestroy
    public void close() {
        admin.close();
    }
}
