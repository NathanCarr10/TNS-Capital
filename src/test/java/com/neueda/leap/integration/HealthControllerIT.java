package com.neueda.leap.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the health check endpoint and the other actuator
 * endpoints (Spring Actuator, /actuator/*).
 *
 * Health is public so Docker and load balancers can probe it without a token;
 * only admins see its details. Metrics and info are admin-only.
 */
@DisplayName("Health Controller Integration Tests")
@WithMockUser(username = "testuser", roles = "ADMIN")
class HealthControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return OK for health check")
    @SuppressWarnings("null")
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")));
    }

    @Test
    @DisplayName("Should show admins the health of each component")
    @SuppressWarnings("null")
    void testHealthCheckDetails() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status", equalTo("UP")));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("Should answer the health check without a token, but without details")
    @SuppressWarnings("null")
    void testHealthCheckIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("UP")))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @WithMockUser(username = "trader", roles = "USER")
    @DisplayName("Should not show traders the health details")
    @SuppressWarnings("null")
    void testHealthDetailsHiddenFromTraders() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @WithMockUser(username = "trader", roles = "USER")
    @DisplayName("Should refuse metrics and info to traders")
    void testMetricsAreAdminOnly() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("Should refuse metrics without a token")
    void testMetricsNeedAToken() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should give admins the metrics")
    void testMetricsForAdmins() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
    }
}
