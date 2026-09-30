package com.neueda.leap.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Security configuration for the API with JWT validation and security headers.
 *
 * Security Best Practices Implemented:
 * - JWT authentication with HMAC-SHA256; any valid token can call every API
 *   endpoint, roles in the token are not checked
 * - Missing or invalid tokens get an AUTH-401 error body
 * - Security headers to prevent common attacks
 * - CSRF disabled for stateless API (appropriate for REST)
 * - X-Frame-Options set to prevent clickjacking
 */
@Configuration
public class SecurityConfig {

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
                                                // Require authentication for all other requests
                                                .anyRequest().authenticated())

                                // Return AUTH-401 JSON for missing or invalid tokens
                                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorised))

                                // Configure OAuth2 resource server with JWT
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .authenticationEntryPoint(unauthorised)
                                                .jwt(jwt -> jwt.decoder(jwtDecoder())));

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
