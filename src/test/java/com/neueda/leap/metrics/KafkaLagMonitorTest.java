package com.neueda.leap.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Kafka lag monitor")
class KafkaLagMonitorTest {

    @Test
    @DisplayName("Sums each topic's partitions and never goes below zero")
    void sumsLagPerTopic() {
        Map<TopicPartition, OffsetAndMetadata> committed = Map.of(
                new TopicPartition("orders", 0), new OffsetAndMetadata(10),
                new TopicPartition("orders", 1), new OffsetAndMetadata(5),
                new TopicPartition("executions", 0), new OffsetAndMetadata(7));
        Map<TopicPartition, ListOffsetsResultInfo> ends = Map.of(
                new TopicPartition("orders", 0), new ListOffsetsResultInfo(12, 0, Optional.empty()),
                new TopicPartition("orders", 1), new ListOffsetsResultInfo(9, 0, Optional.empty()),
                new TopicPartition("executions", 0), new ListOffsetsResultInfo(6, 0, Optional.empty()));

        assertEquals(Map.of("orders", 6L, "executions", 0L), KafkaLagMonitor.lagByTopic(committed, ends));
    }
}
