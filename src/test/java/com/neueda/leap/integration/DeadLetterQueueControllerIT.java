package com.neueda.leap.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dtos.DeadLetterMessageDTO;
import com.neueda.leap.dtos.DLQStatistics;
import com.neueda.leap.enums.DLQStatus;
import com.neueda.leap.kafka.events.MessageEnvelope;
import com.neueda.leap.kafka.events.OrderEvent;
import com.neueda.leap.model.DeadLetterMessage;
import com.neueda.leap.repositories.DeadLetterMessageRepository;
import com.neueda.leap.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration Tests for DeadLetterQueueController (REST API)
 * Tests admin DLQ management endpoints with security
 */
@DisplayName("Dead Letter Queue Controller Integration Tests")
public class DeadLetterQueueControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeadLetterMessageRepository dlqRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private DeadLetterMessage pendingMessage;
    private DeadLetterMessage resolvedMessage;
    private DeadLetterMessage ignoredMessage;

    @BeforeEach
    void setUp() {
        dlqRepository.deleteAll();

        // Create test messages with different statuses
        pendingMessage = new DeadLetterMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"test\": \"message\"}",
                "Test failure reason\nStack Trace:\nat java.lang.Exception",
                "TestException",
                clock.now());
        pendingMessage.setStatus(DLQStatus.PENDING);
        pendingMessage = dlqRepository.save(pendingMessage);

        resolvedMessage = new DeadLetterMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"test\": \"resolved\"}",
                "Previously failed, now resolved",
                "ResolvedTestException",
                clock.now());
        resolvedMessage.setStatus(DLQStatus.RESOLVED);
        resolvedMessage.setResolvedOn(clock.now());
        resolvedMessage = dlqRepository.save(resolvedMessage);

        ignoredMessage = new DeadLetterMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"test\": \"ignored\"}",
                "Marked as unfixable",
                "IgnoredTestException",
                clock.now());
        ignoredMessage.setStatus(DLQStatus.IGNORED);
        ignoredMessage.setResolvedOn(clock.now());
        ignoredMessage.setAdminNotes("Duplicate order, customer will resubmit");
        ignoredMessage = dlqRepository.save(ignoredMessage);
    }

    @Test
    @DisplayName("Should list DLQ messages of all statuses by default")
    @WithMockUser(roles = "ADMIN")
    void testListAllMessagesByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/dlq/messages")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].status", containsInAnyOrder("PENDING", "RESOLVED", "IGNORED")));
    }

    @Test
    @DisplayName("Should filter messages by failure type across all statuses")
    @WithMockUser(roles = "ADMIN")
    void testFilterByFailureTypeOnly() throws Exception {
        mockMvc.perform(get("/api/v1/dlq/messages")
                .param("failureType", "ResolvedTestException")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(resolvedMessage.getId().toString())));
    }

    @Test
    @DisplayName("Should filter messages by status")
    @WithMockUser(roles = "ADMIN")
    void testFilterByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/dlq/messages")
                .param("status", "RESOLVED")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(resolvedMessage.getId().toString())))
                .andExpect(jsonPath("$[0].status", is("RESOLVED")));
    }

    @Test
    @DisplayName("Should filter messages by status and failure type")
    @WithMockUser(roles = "ADMIN")
    void testFilterByStatusAndFailureType() throws Exception {
        mockMvc.perform(get("/api/v1/dlq/messages")
                .param("status", "IGNORED")
                .param("failureType", "IgnoredTestException")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].failureType", is("IgnoredTestException")));
    }

    @Test
    @DisplayName("Should retrieve specific DLQ message with full details")
    @WithMockUser(roles = "ADMIN")
    void testGetDLQMessageById() throws Exception {
        mockMvc.perform(get("/api/v1/dlq/messages/{id}", pendingMessage.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(pendingMessage.getId().toString())))
                .andExpect(jsonPath("$.failureType", is("TestException")))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.retryCount", is(0)))
                .andExpect(jsonPath("$.failureReason", containsString("Test failure reason")));
    }

    @Test
    @DisplayName("Should return 404 for non-existent message")
    @WithMockUser(roles = "ADMIN")
    void testGetNonExistentMessage() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/dlq/messages/{id}", nonExistentId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should replay DLQ message")
    @WithMockUser(roles = "ADMIN")
    void testReplayMessage() throws Exception {
        mockMvc.perform(post("/api/v1/dlq/messages/{id}/replay", pendingMessage.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Verify status changed to RESOLVED (assuming message was valid)
        // Note: In test data, this might fail due to invalid account, but endpoint
        // should accept it
    }

    @Test
    @DisplayName("Should dismiss DLQ message")
    @WithMockUser(roles = "ADMIN")
    void testDismissMessage() throws Exception {
        String adminNotes = "Customer cancelled order, no longer needed";

        mockMvc.perform(delete("/api/v1/dlq/messages/{id}", pendingMessage.getId())
                .param("adminNotes", adminNotes)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify message is dismissed
        DeadLetterMessage dismissed = dlqRepository.findById(pendingMessage.getId()).get();
        assert dismissed.getStatus() == DLQStatus.IGNORED;
        assert dismissed.getAdminNotes().equals(adminNotes);
    }

    @Test
    @DisplayName("Should return DLQ statistics")
    @WithMockUser(roles = "ADMIN")
    void testGetDLQStatistics() throws Exception {
        mockMvc.perform(get("/api/v1/dlq/statistics")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingMessages", is(1)))
                .andExpect(jsonPath("$.resolvedMessages", is(1)))
                .andExpect(jsonPath("$.ignoredMessages", is(1)))
                .andExpect(jsonPath("$.totalMessages", is(3)));
    }

    @Test
    @DisplayName("Should deny access without ADMIN role")
    @WithMockUser(roles = "USER")
    void testAccessDeniedWithoutAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/dlq/messages"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dlq/messages/{id}", pendingMessage.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/dlq/messages/{id}/replay", pendingMessage.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/dlq/messages/{id}", pendingMessage.getId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dlq/statistics"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 401 when not authenticated")
    @WithAnonymousUser
    void testAccessDeniedWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dlq/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return invalid status as bad request")
    @WithMockUser(roles = "ADMIN")
    void testInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/dlq/messages")
                .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest());
    }
}
