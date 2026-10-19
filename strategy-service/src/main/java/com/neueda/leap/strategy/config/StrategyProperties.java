package com.neueda.leap.strategy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings of the strategy service (strategy.* in application.yml).
 *
 * @param maxActivePerAccount how many strategies one account may have switched on at once
 * @param staleAfter          market-data messages published longer ago than this are
 *                            ignored, e.g. a backlog read after a restart
 * @param maxBars             1-minute closes kept per symbol, enough for the slowest rule
 * @param tradingApiUrl       the trading API the strategies place orders through
 * @param authUrl             the auth service that issues the service's token
 * @param clientId            the service's client id at the auth service
 * @param clientSecret        its client secret (STRATEGY_CLIENT_SECRET)
 */
@ConfigurationProperties(prefix = "strategy")
public record StrategyProperties(
        int maxActivePerAccount,
        Duration staleAfter,
        int maxBars,
        String tradingApiUrl,
        String authUrl,
        String clientId,
        String clientSecret) {
}
