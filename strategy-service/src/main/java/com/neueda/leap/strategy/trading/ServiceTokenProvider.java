package com.neueda.leap.strategy.trading;

import com.neueda.leap.strategy.config.StrategyProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * The strategy service's own access token (role STRATEGY), from the auth
 * service's client-credentials endpoint, POST /auth/token. It is kept until
 * a minute before it expires, then a new one is fetched.
 */
@Component
public class ServiceTokenProvider {
    private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofMinutes(1);

    private final RestClient restClient;
    private final StrategyProperties properties;
    private final Clock clock;
    private String token;
    private Instant renewAt = Instant.MIN;

    public ServiceTokenProvider(RestClient.Builder builder, StrategyProperties properties, Clock clock) {
        this.restClient = builder.baseUrl(properties.authUrl()).build();
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * @throws org.springframework.web.client.RestClientException if the auth service refuses or cannot be reached
     */
    public synchronized String token() {
        if (token == null || !clock.instant().isBefore(renewAt)) {
            TokenResponse response = restClient.post()
                    .uri("/auth/token")
                    .body(Map.of("clientId", properties.clientId(), "clientSecret", properties.clientSecret()))
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException("The auth service returned no token");
            }
            token = response.accessToken();
            renewAt = clock.instant().plusSeconds(response.expiresIn()).minus(RENEW_BEFORE_EXPIRY);
        }
        return token;
    }

    /**
     * Drops the token, e.g. after the trading API refused it, so the next call fetches a new one.
     */
    public synchronized void invalidate() {
        token = null;
    }

    record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }
}
