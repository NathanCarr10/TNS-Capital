package com.neueda.leap.config;

import com.neueda.leap.time.Clock;
import com.neueda.leap.time.SystemClock;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.strategies.BuyOrderStrategy;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import com.neueda.leap.strategies.SellOrderStrategy;
import com.neueda.leap.repositories.PositionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Application configuration for Spring beans.
 * Provides system-wide singleton beans for Clock, strategies, etc.
 */
@Configuration
public class ApplicationConfig {

    /**
     * Provides the system clock singleton for production use.
     * For testing, use ClockTest implementation instead.
     */
    @Bean
    public Clock clock() {
        return SystemClock.INSTANCE;
    }

    /**
     * Provides the order execution strategies map.
     * Maps OrderSide enum to corresponding execution strategy implementations.
     */
    @Bean
    public Map<OrderSide, OrderExecutionStrategy> orderExecutionStrategies(
            PositionRepository positionRepository) {
        Map<OrderSide, OrderExecutionStrategy> strategies = new HashMap<>();
        strategies.put(OrderSide.BUY, new BuyOrderStrategy(positionRepository));
        strategies.put(OrderSide.SELL, new SellOrderStrategy(positionRepository));
        return strategies;
    }
}
