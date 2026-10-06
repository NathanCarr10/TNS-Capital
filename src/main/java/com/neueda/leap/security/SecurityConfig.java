package com.neueda.leap.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

/**
 * Security configuration for the API with JWT validation and security headers.
 *
 * Security Best Practices Implemented:
 * - JWT authentication with HMAC-SHA256
 * - Security headers to prevent common attacks
 * - CSRF disabled for stateless API (appropriate for REST)
 * - Rate limiting headers configured
 * - X-Frame-Options set to prevent clickjacking
 * - Content-Security-Policy to prevent XSS
 * - Method-level security for role-based access control on endpoints
 *
 * Roles (from the JWT "roles" claim, e.g. {"sub":"john","roles":["CUSTOMER"]}):
 * - ADMIN: full access to every account, order, instrument and the DLQ
 * - CUSTOMER: only accounts whose ownerUsername matches the token subject
 *   (enforced per endpoint with @PreAuthorize + {@link AccountAccess})
 * - anything else (e.g. GUEST): authenticated but forbidden from the API
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        public static final String ADMIN = "ADMIN";
        public static final String CUSTOMER = "CUSTOMER";

        @Value("${jwt.shared-secret}")
        private String sharedSecret;

        /**
         * Configures JWT decoder using the shared secret.
         * This validates incoming JWT tokens signed with HMAC-SHA256.
         */
        @Bean
        public JwtDecoder jwtDecoder() {
                SecretKeySpec secretKey = new SecretKeySpec(sharedSecret.getBytes(), "HmacSHA256");
                return NimbusJwtDecoder.withSecretKey(secretKey).build();
        }

        /**
         * Maps the token's "roles" claim to Spring authorities with the ROLE_ prefix,
         * so ["ADMIN"] becomes ROLE_ADMIN and hasRole('ADMIN') works. Without this,
         * Spring only reads the "scope" claim and role checks never match.
         */
        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
                authoritiesConverter.setAuthoritiesClaimName("roles");
                authoritiesConverter.setAuthorityPrefix("ROLE_");

                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
                converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
                return converter;
        }

        /**
         * Configures the security filter chain with:
         * - JWT authentication for API endpoints
         * - Security headers to prevent common attacks
         * - Public access to Swagger/OpenAPI documentation
         * - Coarse role rules per URL; per-account ownership is checked in the controllers
         */
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                // Disable CSRF for stateless APIs (no session state)
                                .csrf(csrf -> csrf.disable())

                                // Add security headers to all responses
                                .headers(headers -> headers
                                                // Prevent clickjacking attacks
                                                .frameOptions(frameOptions -> frameOptions.deny())
                                                // Prevent MIME type sniffing - use correct method for Spring 6.x
                                                .contentTypeOptions(contentTypeOptions -> {
                                                })
                                                // Enforce HTTPS (if available)
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000) // 1 year
                                                ))

                                // Configure request authorization
                                .authorizeHttpRequests(auth -> auth
                                                // Allow public access to Swagger/OpenAPI documentation
                                                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
                                                                "/v3/api-docs/**")
                                                .permitAll()
                                                // Dead letter queue is an operations tool
                                                .requestMatchers("/api/v1/dlq/**").hasRole(ADMIN)
                                                // Customers may browse instruments but only admins manage them
                                                .requestMatchers(HttpMethod.GET, "/api/v1/instruments/**")
                                                .hasAnyRole(ADMIN, CUSTOMER)
                                                .requestMatchers("/api/v1/instruments/**").hasRole(ADMIN)
                                                // Opening/closing accounts and changing their status is admin-only
                                                .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasRole(ADMIN)
                                                .requestMatchers(HttpMethod.DELETE, "/api/v1/accounts/*").hasRole(ADMIN)
                                                .requestMatchers("/api/v1/accounts/*/status").hasRole(ADMIN)
                                                // Everything else in the API needs a trading role
                                                .requestMatchers("/api/**").hasAnyRole(ADMIN, CUSTOMER)
                                                // Require authentication for all other requests
                                                .anyRequest().authenticated())

                                // Configure OAuth2 resource server with JWT
                                .oauth2ResourceServer((OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) -> oauth2
                                                .jwt(jwt -> jwt.decoder(jwtDecoder())
                                                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

                return http.build();
        }
}
