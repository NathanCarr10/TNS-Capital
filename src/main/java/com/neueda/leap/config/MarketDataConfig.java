package com.neueda.leap.config;

import com.neueda.leap.marketdata.AlpacaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds the market-data.alpaca.* settings used by the live price pool.
 */
@Configuration
@EnableConfigurationProperties(AlpacaProperties.class)
public class MarketDataConfig {
}
