package com.neueda.leap.strategy.trading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.strategy.config.StrategyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("TradingApiClient")
class TradingApiClientTest {
    private static final String ORDERS = "http://api.test/api/v1/orders";
    private static final MarketOrder ORDER = new MarketOrder(7L, "SPY", "BUY", 5, "stg-42-L-1-BUY",
            new BigDecimal("580.12"));
    private static final UUID ORDER_ID = UUID.fromString("6f1c1c2e-8a4e-4b4f-9a57-0d5c3f1f2a10");

    private MockRestServiceServer server;
    private ServiceTokenProvider tokens;
    private TradingApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        tokens = mock(ServiceTokenProvider.class);
        when(tokens.token()).thenReturn("service-token");
        StrategyProperties properties = new StrategyProperties(1, Duration.ofMinutes(2), 250, "http://api.test",
                "http://auth.test", "strategy-service", "secret");
        client = new TradingApiClient(builder, properties, tokens, new ObjectMapper());
    }

    private static String accepted() {
        return "{\"orderId\": \"" + ORDER_ID + "\", \"status\": \"NEW\"}";
    }

    private static String error(String code, String message) {
        return "{\"errorCode\": \"" + code + "\", \"message\": \"" + message + "\", \"timestamp\": \"2026-10-19T14:30:00Z\"}";
    }

    @Test
    @DisplayName("Places a market order (no price) with the service's token")
    void placesMarketOrder() {
        server.expect(once(), requestTo(ORDERS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(content().json("""
                        {"accountId": 7, "symbol": "SPY", "side": "BUY", "quantity": 5,
                         "idempotencyKey": "stg-42-L-1-BUY"}""", true))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON).body(accepted()));

        assertEquals(ORDER_ID, client.place(ORDER));
        server.verify();
    }

    @Test
    @DisplayName("Sends the signal's price when the API has no live price (PRC-503)")
    void fallsBackToSignalPrice() {
        server.expect(once(), requestTo(ORDERS))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body(error("PRC-503", "No live price")));
        server.expect(once(), requestTo(ORDERS))
                .andExpect(content().json("{\"price\": 580.12}"))
                .andRespond(withSuccess(accepted(), MediaType.APPLICATION_JSON));

        assertEquals(ORDER_ID, client.place(ORDER));
        server.verify();
    }

    @Test
    @DisplayName("Fetches a new token once when the API refuses the old one")
    void renewsRefusedToken() {
        server.expect(once(), requestTo(ORDERS))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body(error("AUTH-401", "Unauthorised or invalid token")));
        server.expect(once(), requestTo(ORDERS))
                .andRespond(withSuccess(accepted(), MediaType.APPLICATION_JSON));

        assertEquals(ORDER_ID, client.place(ORDER));
        verify(tokens).invalidate();
    }

    @Test
    @DisplayName("Reports the API's error code when it refuses the order")
    void reportsRefusal() {
        server.expect(once(), requestTo(ORDERS))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body(error("AUTH-403", "Account 7 has no active strategy")));

        OrderRefusedException refused = assertThrows(OrderRefusedException.class, () -> client.place(ORDER));

        assertEquals("AUTH-403", refused.getErrorCode());
        assertEquals("Account 7 has no active strategy", refused.getMessage());
    }

    @Test
    @DisplayName("Reports an error without the platform's envelope, or a reply without an order id")
    void reportsOddReplies() {
        server.expect(once(), requestTo(ORDERS)).andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>"));
        server.expect(once(), requestTo(ORDERS)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertEquals("HTTP-502", assertThrows(OrderRefusedException.class, () -> client.place(ORDER)).getErrorCode());
        assertEquals("SYS-502", assertThrows(OrderRefusedException.class, () -> client.place(ORDER)).getErrorCode());
    }

    @Test
    @DisplayName("Reports SYS-503 when the API cannot be reached")
    void reportsUnreachableApi() {
        when(tokens.token()).thenThrow(new org.springframework.web.client.ResourceAccessException("refused"));

        assertEquals("SYS-503", assertThrows(OrderRefusedException.class, () -> client.place(ORDER)).getErrorCode());
    }
}
