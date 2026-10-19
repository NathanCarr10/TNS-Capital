package com.neueda.leap.strategy.trading;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.strategy.config.StrategyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Places strategies' orders through the trading API, POST /api/v1/orders,
 * like a trader would, so every check (account status, funds when it is
 * executed, the instrument) still applies.
 *
 * Orders are market orders: no price, so the API prices them at the live
 * market. If the API has no live price (no market data keys) it answers
 * PRC-503, and the order is sent again at the signal's price.
 */
@Component
public class TradingApiClient {
    private static final Logger log = LoggerFactory.getLogger(TradingApiClient.class);
    private static final String NO_LIVE_PRICE = "PRC-503";

    private final RestClient restClient;
    private final ServiceTokenProvider tokens;
    private final ObjectMapper objectMapper;

    public TradingApiClient(RestClient.Builder builder, StrategyProperties properties, ServiceTokenProvider tokens,
            ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(properties.tradingApiUrl()).build();
        this.tokens = Objects.requireNonNull(tokens);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    /**
     * @return the id of the order the API accepted
     * @throws OrderRefusedException if the API refused it or could not be reached
     */
    public UUID place(MarketOrder order) {
        try {
            return send(order, null, true);
        } catch (OrderRefusedException e) {
            if (NO_LIVE_PRICE.equals(e.getErrorCode()) && order.fallbackPrice() != null) {
                log.info("No live price for {}; placing the order at the signal's price {}", order.symbol(),
                        order.fallbackPrice());
                return send(order, order.fallbackPrice(), true);
            }
            throw e;
        }
    }

    private UUID send(MarketOrder order, BigDecimal price, boolean retryWithNewToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", order.accountId());
        body.put("symbol", order.symbol());
        body.put("side", order.side());
        body.put("quantity", order.quantity());
        if (price != null) {
            body.put("price", price);
        }
        body.put("idempotencyKey", order.idempotencyKey());
        try {
            JsonNode accepted = restClient.post()
                    .uri("/api/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (accepted == null || !accepted.hasNonNull("orderId")) {
                throw new OrderRefusedException("SYS-502", "The trading API accepted the order without an order id");
            }
            return UUID.fromString(accepted.get("orderId").asText());
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED && retryWithNewToken) {
                tokens.invalidate();
                return send(order, price, false);
            }
            throw refusal(e);
        } catch (RestClientException e) {
            throw new OrderRefusedException("SYS-503", "The trading API could not be reached: " + e.getMessage());
        }
    }

    private OrderRefusedException refusal(HttpStatusCodeException e) {
        try {
            JsonNode error = objectMapper.readTree(e.getResponseBodyAsString());
            return new OrderRefusedException(error.path("errorCode").asText("HTTP-" + e.getStatusCode().value()),
                    error.path("message").asText(e.getStatusText()));
        } catch (JsonProcessingException unreadable) {
            // Not the platform's error envelope, e.g. a proxy's error page
            return new OrderRefusedException("HTTP-" + e.getStatusCode().value(), e.getStatusText());
        }
    }
}
