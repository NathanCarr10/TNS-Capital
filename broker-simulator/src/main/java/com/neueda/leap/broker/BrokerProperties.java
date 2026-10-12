package com.neueda.leap.broker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * broker.* settings, checked on startup.
 *
 * @param minDelay                shortest time before an order is filled
 * @param maxDelay                longest time before an order is filled
 * @param maxPriceImprovementBps  most a fill can beat the limit price by, in basis points
 * @param executionsTopic         Kafka topic the fills are published to
 * @param venue                   name recorded on each fill
 */
@Validated
@ConfigurationProperties(prefix = "broker")
public record BrokerProperties(
        @NotNull Duration minDelay,
        @NotNull Duration maxDelay,
        @Min(0) @Max(1000) int maxPriceImprovementBps,
        @NotBlank String executionsTopic,
        @NotBlank String venue) {

    public BrokerProperties {
        if (minDelay != null && maxDelay != null && maxDelay.compareTo(minDelay) < 0) {
            throw new IllegalArgumentException("broker.max-delay must not be shorter than broker.min-delay");
        }
    }
}
