package com.neueda.leap.broker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Stand-in for a real broker: accepts orders over a small REST API and, after
 * a simulated market delay, reports each fill on the executions Kafka topic.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BrokerSimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(BrokerSimulatorApplication.class, args);
    }
}
