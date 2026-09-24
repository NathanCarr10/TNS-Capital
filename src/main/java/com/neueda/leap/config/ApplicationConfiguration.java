package com.neueda.leap.config;

import com.neueda.leap.time.Clock;
import com.neueda.leap.time.SystemClock;
import com.neueda.leap.enums.OrderSide;
import com.neueda.leap.strategies.OrderExecutionStrategy;
import com.neueda.leap.strategies.BuyOrderStrategy;
import com.neueda.leap.strategies.SellOrderStrategy;
import com.neueda.leap.repositories.PositionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configuration for core application services.
 */
@Configuration
public class ApplicationConfiguration {
    
    /**
     * Provides the Clock bean for time-related operations.
     *
     * @return SystemClock singleton instance
     */
    @Bean
    public Clock clock() {
        return SystemClock.INSTANCE;
    }

    /**
     * Provides the BuyOrderStrategy bean.
     *
     * @param positionRepository repository for position data
     * @return configured BuyOrderStrategy instance
     */
    @Bean
    public BuyOrderStrategy buyOrderStrategy(PositionRepository positionRepository) {
        return new BuyOrderStrategy(positionRepository);
    }

    /**
     * Provides the SellOrderStrategy bean.
     *
     * @param positionRepository repository for position data
     * @return configured SellOrderStrategy instance
     */
    @Bean
    public SellOrderStrategy sellOrderStrategy(PositionRepository positionRepository) {
        return new SellOrderStrategy(positionRepository);
    }

    /**
     * Provides a map of OrderExecutionStrategies keyed by OrderSide.
     *
     * @param buyOrderStrategy strategy for buy orders
     * @param sellOrderStrategy strategy for sell orders
     * @return map of strategies by order side
     */
    @Bean
    public Map<OrderSide, OrderExecutionStrategy> orderExecutionStrategies(
            BuyOrderStrategy buyOrderStrategy,
            SellOrderStrategy sellOrderStrategy) {
        return Map.of(
                OrderSide.BUY, buyOrderStrategy,
                OrderSide.SELL, sellOrderStrategy
        );
    }
}

