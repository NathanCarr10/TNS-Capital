package com.neueda.leap.security;

import com.neueda.leap.controllers.AccountController;
import com.neueda.leap.controllers.DeadLetterQueueController;
import com.neueda.leap.enums.DLQStatus;
import com.neueda.leap.model.Account;
import com.neueda.leap.repositories.AccountRepository;
import com.neueda.leap.repositories.DeadLetterMessageRepository;
import com.neueda.leap.repositories.OrderRepository;
import com.neueda.leap.repositories.PositionRepository;
import com.neueda.leap.services.DeadLetterService;
import com.neueda.leap.services.OrderService;
import com.neueda.leap.time.Clock;
import com.neueda.leap.time.ClockTest;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sends real JWTs through the application's security filter chain
 * (SecurityConfig) to check that tokens are verified and roles are applied.
 */
@WebMvcTest(controllers = { AccountController.class, DeadLetterQueueController.class })
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.shared-secret=" + JwtSecurityTest.SECRET)
@DisplayName("JWT security")
class JwtSecurityTest {

    static final String SECRET = "unit-test-shared-secret-at-least-32-bytes-long";
    private static final String ACCOUNT_URL = "/api/v1/accounts/1";
    private static final String DLQ_URL = "/api/v1/dlq/messages";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountRepository accountRepository;
    @MockitoBean
    private PositionRepository positionRepository;
    @MockitoBean
    private OrderRepository orderRepository;
    @MockitoBean
    private Clock clock;
    @MockitoBean
    private DeadLetterMessageRepository dlqRepository;
    @MockitoBean
    private DeadLetterService deadLetterService;
    @MockitoBean
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        Account account = new Account("ACC-1001", "John Doe", new BigDecimal("5000.00"),
                new ClockTest(Instant.parse("2026-10-01T09:00:00Z")));
        account.setId(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(dlqRepository.findByStatusOrderByCreatedOnDesc(DLQStatus.PENDING)).thenReturn(List.of());
    }

    private static String token(String secret, Instant expiresAt, List<String> roles) throws JOSEException {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("alice")
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt));
        if (roles != null) {
            claims.claim("roles", roles);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }

    private static String validToken(List<String> roles) throws JOSEException {
        return token(SECRET, Instant.now().plusSeconds(3600), roles);
    }

    @Test
    @DisplayName("No token is rejected with an AUTH-401 body")
    void noTokenIsRejected() throws Exception {
        mockMvc.perform(get(ACCOUNT_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
    }

    @Test
    @DisplayName("A token that is not a JWT is rejected")
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get(ACCOUNT_URL).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
    }

    @Test
    @DisplayName("A token signed with a different secret is rejected")
    void wrongSignatureIsRejected() throws Exception {
        String forged = token("some-other-secret-that-is-also-32-bytes-long", Instant.now().plusSeconds(3600),
                List.of("ADMIN"));

        mockMvc.perform(get(ACCOUNT_URL).header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An expired token is rejected")
    void expiredTokenIsRejected() throws Exception {
        // Beyond Spring's default 60 second clock skew allowance
        String expired = token(SECRET, Instant.now().minusSeconds(300), List.of("ADMIN"));

        mockMvc.perform(get(ACCOUNT_URL).header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An unsigned (alg=none) token is rejected")
    void unsignedTokenIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("alice")
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("roles", List.of("ADMIN"))
                .build();
        String unsigned = new PlainJWT(claims).serialize();

        mockMvc.perform(get(ACCOUNT_URL).header("Authorization", "Bearer " + unsigned))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A valid token reaches the endpoint, with security headers on the response")
    void validTokenIsAccepted() throws Exception {
        mockMvc.perform(get(ACCOUNT_URL).header("Authorization", "Bearer " + validToken(List.of("GUEST"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC-1001"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("The DLQ endpoints refuse a valid token without the ADMIN role")
    void dlqRequiresAdminRole() throws Exception {
        mockMvc.perform(get(DLQ_URL).header("Authorization", "Bearer " + validToken(List.of("GUEST"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH-403"));
    }

    @Test
    @DisplayName("The DLQ endpoints refuse a valid token with no roles claim")
    void dlqRefusesTokenWithoutRoles() throws Exception {
        mockMvc.perform(get(DLQ_URL).header("Authorization", "Bearer " + validToken(null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The token's roles claim grants ADMIN access to the DLQ")
    void dlqAllowsAdminRoleFromToken() throws Exception {
        mockMvc.perform(get(DLQ_URL).header("Authorization", "Bearer " + validToken(List.of("MISSION_OPERATOR", "ADMIN"))))
                .andExpect(status().isOk());
    }
}
