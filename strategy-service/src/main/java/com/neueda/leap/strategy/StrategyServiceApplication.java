package com.neueda.leap.strategy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Strategy service (spec Component 8, automated strategy execution): backtests
 * trading rules on the daily price history and runs the rules traders switch
 * on against the market-data price stream, placing their orders through the
 * trading API.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class StrategyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrategyServiceApplication.class, args);
    }
}
