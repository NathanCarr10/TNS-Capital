package com.neueda.leap.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Security configuration for the API with JWT validation and security headers.
 *
 * Security Best Practices Implemented:
 * - JWT authentication with HMAC-SHA256; any valid token can call the trading
 *   API endpoints. The dead-letter queue admin endpoints also need the ADMIN
 *   role (method security on DeadLetterQueueController)
 * - Missing or invalid tokens get an AUTH-401 error body
 * - Security headers to prevent common attacks
 * - CSRF disabled for stateless API (appropriate for REST)
 * - X-Frame-Options set to prevent clickjacking
 * - The token's "roles" claim is mapped to Spring roles (ROLE_ADMIN etc.) so
 *   method-level @PreAuthorize checks can be added once roles are agreed
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        @Value("${jwt.shared-secret}")
        private String sharedSecret;

        // Must match the auth service's JWT_ISSUER
        @Value("${jwt.issuer:urn:tns-capital:auth-service}")
        private String issuer;

        /**
         * Configures JWT decoder using the shared secret.
         * This validates incoming JWT tokens signed with HMAC-SHA256, and checks
         * that they were issued by the auth service (iss) as access tokens
         * (typ = "access"), on top of the default expiry checks.
         */
        @Bean
        public JwtDecoder jwtDecoder() {
                checkSecret(sharedSecret);
                SecretKeySpec secretKey = new SecretKeySpec(sharedSecret.getBytes(), "HmacSHA256");
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                                JwtValidators.createDefaultWithIssuer(issuer),
                                new JwtClaimValidator<String>("typ", "access"::equals)));
                return decoder;
        }

        /**
         * Refuses to start with a key anyone could know: too short for HS256, or the
         * placeholder from .env.example, with which anyone could forge an ADMIN token
         * (OWASP A02-5). The auth service applies the same rule.
         */
        static void checkSecret(String secret) {
                if (secret == null || secret.length() < 32) {
                        throw new IllegalStateException("JWT_SECRET must be at least 32 bytes (256 bits) for HS256");
                }
                if (secret.toLowerCase(java.util.Locale.ROOT).matches("^change-?me.*")) {
                        throw new IllegalStateException(
                                        "JWT_SECRET is the placeholder from .env.example. Generate one with: openssl rand -hex 32");
                }
        }

        /**
         * Reads the auth service's "roles" claim instead of Spring's default "scope"
         * claim, so a token with roles ["ADMIN"] gets the authority ROLE_ADMIN.
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

        /**
         * Configures the security filter chain with:
         * - JWT authentication for API endpoints
         * - Security headers to prevent common attacks
         * - Public access to Swagger/OpenAPI documentation
         */
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                AuthenticationEntryPoint unauthorised = (request, response, e) -> writeError(response,
                                HttpServletResponse.SC_UNAUTHORIZED, "AUTH-401", "Unauthorised or invalid token");

                http
                                // Clients authenticate with a bearer token in the Authorization header,
                                // never a cookie, and no session is created. A browser cannot attach
                                // the token to a forged cross-site request, so CSRF protection adds
                                // nothing here
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // Add security headers to all responses
                                .headers(headers -> headers
                                                // Prevent clickjacking attacks
                                                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
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
                                                // Require authentication for all other requests
                                                .anyRequest().authenticated())

                                // Return AUTH-401 JSON for missing or invalid tokens
                                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorised))

                                // Configure OAuth2 resource server with JWT
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .authenticationEntryPoint(unauthorised)
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
                                errorCode, message, LocalDateTime.now(ZoneOffset.UTC)));
        }
}
