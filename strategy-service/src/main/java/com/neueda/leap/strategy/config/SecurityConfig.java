package com.neueda.leap.strategy.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

/**
 * Same token rules as the trading API: HS256 access tokens from the auth
 * service (iss, typ = access, expiry), the "roles" claim mapped to Spring roles.
 *
 * Every endpoint except the health check and the API docs needs a user's
 * token; the service's own STRATEGY token is refused here, as it is only for
 * placing orders at the trading API. Which strategies a user may see or change
 * is checked by StrategyService.
 */
@Configuration
public class SecurityConfig {

    @Value("${jwt.shared-secret}")
    private String sharedSecret;

    @Value("${jwt.issuer:urn:tns-capital:auth-service}")
    private String issuer;

    @Bean
    public JwtDecoder jwtDecoder() {
        checkSecret(sharedSecret);
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<String>("typ", "access"::equals)));
        return decoder;
    }

    /**
     * Refuses a key anyone could know (OWASP A02-5), as the API and auth service do.
     */
    static void checkSecret(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes (256 bits) for HS256");
        }
        if (secret.toLowerCase(Locale.ROOT).matches("^change-?me.*")) {
            throw new IllegalStateException(
                    "JWT_SECRET is the placeholder from .env.example. Generate one with: openssl rand -hex 32");
        }
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> userOnly = AuthorizationManagers.allOf(
                AuthenticatedAuthorizationManager.authenticated(),
                AuthorizationManagers.not(AuthorityAuthorizationManager.hasRole("STRATEGY")));
        http
                // Clients authenticate with a bearer token in the Authorization header, never a
                // cookie, and no session is created. A browser cannot attach the token to a forged
                // cross-site request, so CSRF protection adds nothing here (as the trading API)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs",
                                "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .anyRequest().access(userOnly))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> writeError(response,
                                HttpServletResponse.SC_UNAUTHORIZED, "AUTH-401", "Unauthorised or invalid token"))
                        .accessDeniedHandler((request, response, e) -> writeError(response,
                                HttpServletResponse.SC_FORBIDDEN, "AUTH-403",
                                "You do not have permission to access this resource")))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, e) -> writeError(response,
                                HttpServletResponse.SC_UNAUTHORIZED, "AUTH-401", "Unauthorised or invalid token"))
                        .jwt(jwt -> jwt.decoder(jwtDecoder()).jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String errorCode, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"errorCode\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}"
                .formatted(errorCode, message, Instant.now()));
    }
}
