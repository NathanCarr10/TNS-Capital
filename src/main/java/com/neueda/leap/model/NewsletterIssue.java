package com.neueda.leap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One weekly market newsletter (table newsletter_issues): the week's biggest
 * movers and the news about them, as published. Issues are never changed, so
 * there are no setters; the movers and news are stored as JSONB
 * (docs/DECISIONS.md #16).
 */
@Entity
@Table(name = "newsletter_issues")
public class NewsletterIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private LocalDate weekStart;

    @Column(nullable = false, updatable = false)
    private Instant generatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private List<NewsletterMover> gainers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private List<NewsletterMover> losers;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private List<NewsletterNewsItem> news;

    protected NewsletterIssue() {
        // JPA no-arg constructor
    }

    /**
     * @param weekStart Monday of the week covered
     * @throws IllegalArgumentException if weekStart is not a Monday
     */
    public NewsletterIssue(LocalDate weekStart, Instant generatedAt, List<NewsletterMover> gainers,
            List<NewsletterMover> losers, List<NewsletterNewsItem> news) {
        this.weekStart = Objects.requireNonNull(weekStart);
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("A newsletter week starts on a Monday, not " + weekStart);
        }
        this.generatedAt = Objects.requireNonNull(generatedAt);
        this.gainers = List.copyOf(gainers);
        this.losers = List.copyOf(losers);
        this.news = List.copyOf(news);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public List<NewsletterMover> getGainers() {
        return gainers;
    }

    public List<NewsletterMover> getLosers() {
        return losers;
    }

    public List<NewsletterNewsItem> getNews() {
        return news;
    }
}
