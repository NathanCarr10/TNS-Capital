package com.neueda.leap.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Validates JWTs from the auth service and enforces role-based access.
 *
 * GUEST can read single resources. Everything else, including listing every
 * account or order, trading and editing instruments, needs MISSION_OPERATOR.
 * Any endpoint not listed here defaults to MISSION_OPERATOR.
 */
@Configuration
public class SecurityConfig {
    static final String OPERATOR = "MISSION_OPERATOR";
    static final String GUEST = "GUEST";

    @Value("${jwt.shared-secret}")
    private String sharedSecret;

    @Bean
    public JwtDecoder jwtDecoder() {
        // HMAC-SHA256 with the same secret the auth stub signs tokens with
        SecretKeySpec secretKey = new SecretKeySpec(sharedSecret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    /**
     * Turns the token's "roles" claim, e.g. ["GUEST"], into ROLE_GUEST authorities.
     */
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
        AuthenticationEntryPoint unauthorised = (request, response, e) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH-401", "Unauthorised or invalid token");
        AccessDeniedHandler forbidden = (request, response, e) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "AUTH-403", "Your role does not allow this action");

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/health",
                                "/api/v1/accounts/*",
                                "/api/v1/accounts/*/balance",
                                "/api/v1/accounts/*/positions",
                                "/api/v1/accounts/*/orders",
                                "/api/v1/orders/*",
                                "/api/v1/instruments",
                                "/api/v1/instruments/*",
                                "/api/v1/positions/**")
                        .hasAnyRole(OPERATOR, GUEST)
                        .anyRequest().hasRole(OPERATOR))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorised)
                        .accessDeniedHandler(forbidden))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(unauthorised)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    private static void writeError(HttpServletResponse response, int status, String errorCode, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format(
                "{\"errorCode\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                errorCode, message, LocalDateTime.now()));
    }
}
