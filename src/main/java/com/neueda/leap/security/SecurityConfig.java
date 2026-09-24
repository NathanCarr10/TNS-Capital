package com.neueda.leap.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

// KATA A - wire JWT validation into this service.
@Configuration
public class SecurityConfig {

    @Value("${jwt.shared-secret}")
    private String sharedSecret;

    @Bean
    public JwtDecoder jwtDecoder() {
        // TODO: build a NimbusJwtDecoder using sharedSecret as an HMAC-SHA256
        // key (a SecretKeySpec, algorithm "HmacSHA256"). This is the same
        // secret the Node auth stub signs tokens with.
        SecretKeySpec secretKey = new SecretKeySpec(sharedSecret.getBytes(), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // TODO:
        // - disable CSRF (this is a stateless API, not a browser form)
        // - permit /public with no authentication required
        // - require authentication for every other request
        // - enable oauth2ResourceServer().jwt() (the default JwtAuthenticationConverter
        // is fine for this kata - you don't need to customise the roles claim)

        // JwtAuthenticationConverter authenticationConverter = new
        // JwtAuthenticationConverter();

        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
                                "/v3/api-docs/**", "/api/v1/**")
                        .permitAll()
                        .requestMatchers("/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer((OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder())));

        return http.build();
    }
}
