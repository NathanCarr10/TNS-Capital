package com.neueda.leap.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.neueda.leap.model.NewsletterIssue;
import com.neueda.leap.model.NewsletterMover;
import com.neueda.leap.model.NewsletterNewsItem;
import com.neueda.leap.repositories.NewsletterIssueRepository;

/**
 * The newsletter endpoints: every signed-in trader reads the issues, only
 * admins preview this week's. Alpaca is switched off in tests, so a preview
 * here can only show the no-prices failure; NewsletterServiceTest covers
 * previews with prices.
 */
@DisplayName("Newsletter API")
@WithMockUser(username = "trader", roles = "USER")
class NewsletterControllerIT extends AbstractIntegrationTest {
    private static final LocalDate WEEK_START = LocalDate.of(2026, 10, 12);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NewsletterIssueRepository repository;

    private NewsletterIssue saveIssue(LocalDate weekStart) {
        return repository.save(new NewsletterIssue(weekStart, Instant.parse("2026-10-16T21:00:00Z"),
                List.of(new NewsletterMover("AAPL", "Apple Inc.", new BigDecimal("200.00"), new BigDecimal("210.00"),
                        new BigDecimal("5.00"))),
                List.of(new NewsletterMover("TSLA", "Tesla, Inc.", new BigDecimal("250.00"), new BigDecimal("225.00"),
                        new BigDecimal("-10.00"))),
                List.of(new NewsletterNewsItem("AAPL", "Apple unveils new chip", "benzinga",
                        "https://www.benzinga.com/news/1", Instant.parse("2026-10-14T09:30:00Z")))));
    }

    @Test
    @DisplayName("Lists the issues newest first, without their contents")
    void listsIssues() throws Exception {
        saveIssue(WEEK_START.minusWeeks(1));
        long newest = saveIssue(WEEK_START).getId();

        mockMvc.perform(get("/api/v1/newsletters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", equalTo(2)))
                .andExpect(jsonPath("$[0].id", equalTo((int) newest)))
                .andExpect(jsonPath("$[0].weekStart", equalTo("2026-10-12")))
                .andExpect(jsonPath("$[0].generatedAt", equalTo("2026-10-16T21:00:00Z")))
                .andExpect(jsonPath("$[1].weekStart", equalTo("2026-10-05")))
                .andExpect(jsonPath("$[0].gainers").doesNotExist());
    }

    @Test
    @DisplayName("Returns an issue with its movers and news")
    void getsIssue() throws Exception {
        long id = saveIssue(WEEK_START).getId();

        mockMvc.perform(get("/api/v1/newsletters/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart", equalTo("2026-10-12")))
                .andExpect(jsonPath("$.gainers[0].symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$.gainers[0].name", equalTo("Apple Inc.")))
                .andExpect(jsonPath("$.gainers[0].startClose", equalTo(200.00)))
                .andExpect(jsonPath("$.gainers[0].changePct", equalTo(5.00)))
                .andExpect(jsonPath("$.losers[0].changePct", equalTo(-10.00)))
                .andExpect(jsonPath("$.news[0].symbol", equalTo("AAPL")))
                .andExpect(jsonPath("$.news[0].headline", equalTo("Apple unveils new chip")))
                .andExpect(jsonPath("$.news[0].url", equalTo("https://www.benzinga.com/news/1")))
                .andExpect(jsonPath("$.news[0].publishedAt", equalTo("2026-10-14T09:30:00Z")));
    }

    @Test
    @DisplayName("Answers 404 NWS-404 for an unknown issue")
    void unknownIssue() throws Exception {
        mockMvc.perform(get("/api/v1/newsletters/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", equalTo("NWS-404")));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("Refuses readers without a token")
    void requiresSignIn() throws Exception {
        mockMvc.perform(get("/api/v1/newsletters")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/newsletters/{id}", 1)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refuses traders a preview")
    void tradersCannotPreview() throws Exception {
        mockMvc.perform(get("/api/v1/newsletters/preview"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", equalTo("AUTH-403")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("Answers 503 MKT-503 for an admin's preview without prices, and saves nothing")
    void previewWithoutPrices() throws Exception {
        mockMvc.perform(get("/api/v1/newsletters/preview"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode", equalTo("MKT-503")));
        assertEquals(0, repository.count());
    }
}
