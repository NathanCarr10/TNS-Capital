package com.neueda.leap.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;

/**
 * Settings for the Alpaca market data API (market-data.alpaca.* in application.yml).
 *
 * @param keyId       API key ID; when blank the live price pool is switched off
 * @param secretKey   API secret key
 * @param feed        "iex" (free plan) or "sip" (paid plan, all US exchanges)
 * @param streamUrl   WebSocket base URL; the feed name is appended to it
 * @param dataUrl     REST API base URL
 * @param maxSymbols  how many symbols the stream may subscribe to (30 on the free plan)
 * @param maxPriceAge a cached price older than this is refreshed over REST before use
 */
@ConfigurationProperties(prefix = "market-data.alpaca")
public record AlpacaProperties(
        String keyId,
        String secretKey,
        String feed,
        String streamUrl,
        String dataUrl,
        int maxSymbols,
        Duration maxPriceAge) {

    public boolean isConfigured() {
        return StringUtils.hasText(keyId) && StringUtils.hasText(secretKey);
    }

    public URI streamUri() {
        return URI.create(streamUrl + feed);
    }
}
