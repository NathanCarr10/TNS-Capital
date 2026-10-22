package com.neueda.leap.strategy.trading;

import com.neueda.leap.strategy.config.StrategyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("ServiceTokenProvider")
class ServiceTokenProviderTest {
    private static final String TOKEN_URL = "http://auth.test/auth/token";

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-19T14:00:00Z"));
    private MockRestServiceServer server;
    private ServiceTokenProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        provider = new ServiceTokenProvider(builder, new StrategyProperties(1, Duration.ofMinutes(2), 250,
                "http://api.test", "http://auth.test", "strategy-service", "client-secret"), clock);
    }

    private void expectToken(String token) {
        server.expect(once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"clientId\": \"strategy-service\", \"clientSecret\": \"client-secret\"}"))
                .andRespond(withSuccess("{\"accessToken\": \"" + token + "\", \"tokenType\": \"Bearer\", \"expiresIn\": 900}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Logs in with the client credentials and keeps the token until a minute before it expires")
    void cachesToken() {
        expectToken("first");
        expectToken("second");

        assertEquals("first", provider.token());
        now.set(now.get().plusSeconds(839));
        assertEquals("first", provider.token());
        now.set(now.get().plusSeconds(1));
        assertEquals("second", provider.token());
        server.verify();
    }

    @Test
    @DisplayName("Fetches a new token after it is invalidated")
    void renewsInvalidatedToken() {
        expectToken("first");
        expectToken("second");

        provider.token();
        provider.invalidate();

        assertEquals("second", provider.token());
    }

    @Test
    @DisplayName("Fails when the auth service answers without a token")
    void failsWithoutToken() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, provider::token);
    }
}
