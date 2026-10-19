package com.neueda.leap.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.neueda.leap.model.NewsletterIssue;
import com.neueda.leap.model.NewsletterMover;
import com.neueda.leap.model.NewsletterNewsItem;
import com.neueda.leap.repositories.NewsletterIssueRepository;

/**
 * Newsletter issues against the real newsletter_issues table: the movers and
 * news survive the round trip through JSONB, and are stored as readable JSON.
 */
@DisplayName("Newsletter issue storage")
class NewsletterIssueRepositoryIT extends AbstractIntegrationTest {
    private static final LocalDate WEEK_START = LocalDate.of(2026, 10, 12);

    @Autowired
    private NewsletterIssueRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static NewsletterIssue issue(LocalDate weekStart) {
        return new NewsletterIssue(weekStart, Instant.parse("2026-10-16T21:00:00Z"),
                List.of(new NewsletterMover("AAPL", "Apple Inc.", new BigDecimal("200.00"), new BigDecimal("210.00"),
                        new BigDecimal("5.00"))),
                List.of(new NewsletterMover("TSLA", "Tesla, Inc.", new BigDecimal("250.00"), new BigDecimal("225.00"),
                        new BigDecimal("-10.00"))),
                List.of(new NewsletterNewsItem("AAPL", "Apple unveils new chip", "benzinga",
                        "https://www.benzinga.com/news/1", Instant.parse("2026-10-14T09:30:00Z"))));
    }

    @Test
    @DisplayName("Reads back the movers and news as they were saved")
    void roundTrip() {
        Long id = repository.save(issue(WEEK_START)).getId();

        NewsletterIssue read = repository.findById(id).orElseThrow();

        NewsletterIssue expected = issue(WEEK_START);
        assertEquals(WEEK_START, read.getWeekStart());
        assertEquals(expected.getGeneratedAt(), read.getGeneratedAt());
        assertEquals(expected.getGainers(), read.getGainers());
        assertEquals(expected.getLosers(), read.getLosers());
        assertEquals(expected.getNews(), read.getNews());
    }

    @Test
    @DisplayName("Stores dates as ISO-8601 text in the JSON")
    void storesReadableJson() {
        repository.save(issue(WEEK_START));

        String news = jdbcTemplate.queryForObject("SELECT news ->> 0 FROM newsletter_issues", String.class);

        assertTrue(news.contains("\"publishedAt\": \"2026-10-14T09:30:00Z\""), news);
    }

    @Test
    @DisplayName("Lists issues newest first")
    void listsNewestFirst() {
        repository.save(issue(WEEK_START.minusWeeks(1)));
        repository.save(issue(WEEK_START));

        assertEquals(List.of(WEEK_START, WEEK_START.minusWeeks(1)),
                repository.findAllByOrderByWeekStartDesc().stream().map(NewsletterIssue::getWeekStart).toList());
    }

    @Test
    @DisplayName("Refuses a second issue for the same week")
    void oneIssuePerWeek() {
        repository.save(issue(WEEK_START));

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(issue(WEEK_START)));
    }
}
