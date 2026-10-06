package com.neueda.leap.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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
 * Security configuration for the API with JWT validation and security headers.
 *
 * Security Best Practices Implemented:
 * - JWT authentication with HMAC-SHA256
 * - Missing or invalid tokens get an AUTH-401 error body
 * - Security headers to prevent common attacks
 * - CSRF disabled for stateless API (appropriate for REST)
 * - X-Frame-Options set to prevent clickjacking
 * - The token's "roles" claim is mapped to Spring roles (ROLE_ADMIN etc.)
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
                // Same body as GlobalExceptionHandler's AUTH-403, for requests the URL rules below refuse
                AccessDeniedHandler forbidden = (request, response, e) -> writeError(response,
                                HttpServletResponse.SC_FORBIDDEN, "AUTH-403",
                                "You do not have permission to access this resource");

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

                                // Return AUTH-401 / AUTH-403 JSON for missing tokens and refused roles
                                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorised)
                                                .accessDeniedHandler(forbidden))

                                // Configure OAuth2 resource server with JWT
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
