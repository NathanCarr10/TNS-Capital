package com.neueda.leap.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the health check endpoint.
 * HealthController was removed, so health is served by Spring Actuator at /actuator/health.
 */
@DisplayName("Health Controller Integration Tests")
@WithMockUser(username = "testuser", roles = "USER")
public class HealthControllerIT extends AbstractIntegrationTest {

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
    @DisplayName("Should return service information in health response")
    @SuppressWarnings("null")
    void testHealthCheckDetails() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }
}
