package com.neueda.leap.repositories;

import com.neueda.leap.model.NewsletterIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface NewsletterIssueRepository extends JpaRepository<NewsletterIssue, Long> {
    /**
     * @param weekStart Monday of the week
     * @return that week's issue, if it has been published
     */
    Optional<NewsletterIssue> findByWeekStart(LocalDate weekStart);

    /**
     * @return every issue, newest first
     */
    List<NewsletterIssue> findAllByOrderByWeekStartDesc();
}
