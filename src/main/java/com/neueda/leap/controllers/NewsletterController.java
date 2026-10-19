package com.neueda.leap.controllers;

import com.neueda.leap.dtos.NewsletterMoverResponse;
import com.neueda.leap.dtos.NewsletterNewsResponse;
import com.neueda.leap.dtos.NewsletterResponse;
import com.neueda.leap.dtos.NewsletterSummaryResponse;
import com.neueda.leap.model.NewsletterIssue;
import com.neueda.leap.model.NewsletterMover;
import com.neueda.leap.model.NewsletterNewsItem;
import com.neueda.leap.services.NewsletterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * The weekly market newsletter. Every signed-in trader reads the same issues;
 * a scheduled job publishes one each Friday, and admins can publish the
 * current week's early, e.g. for a demo.
 */
@RestController
@RequestMapping("/api/v1/newsletters")
public class NewsletterController {
    private final NewsletterService newsletterService;

    public NewsletterController(NewsletterService newsletterService) {
        this.newsletterService = Objects.requireNonNull(newsletterService);
    }

    @Operation(summary = "List newsletter issues", description = "Newest first, without their contents.")
    @GetMapping
    public ResponseEntity<List<NewsletterSummaryResponse>> listIssues() {
        return ResponseEntity.ok(newsletterService.issues().stream()
                .map(issue -> new NewsletterSummaryResponse(issue.getId(), issue.getWeekStart(),
                        issue.getGeneratedAt()))
                .toList());
    }

    @Operation(summary = "Get a newsletter issue")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "NWS-404 Newsletter issue not found")
    @GetMapping("/{id}")
    public ResponseEntity<NewsletterResponse> getIssue(@PathVariable long id) {
        return ResponseEntity.ok(toResponse(newsletterService.issue(id)));
    }

    /** Publishing early is an admin task; traders wait for the weekly job. */
    @Operation(summary = "Publish this week's newsletter",
            description = "Admins only. Covers the week so far; if this week's issue was published already, returns it.")
    @ApiResponse(responseCode = "200", description = "This week's issue")
    @ApiResponse(responseCode = "403", description = "AUTH-403 Admins only")
    @ApiResponse(responseCode = "503", description = "MKT-503 No prices to rank the week's movers")
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NewsletterResponse> generateIssue() {
        return ResponseEntity.ok(toResponse(newsletterService.publishWeeklyIssue()));
    }

    private static NewsletterResponse toResponse(NewsletterIssue issue) {
        return new NewsletterResponse(issue.getId(), issue.getWeekStart(), issue.getGeneratedAt(),
                toResponses(issue.getGainers()), toResponses(issue.getLosers()),
                issue.getNews().stream().map(NewsletterController::toResponse).toList());
    }

    private static List<NewsletterMoverResponse> toResponses(List<NewsletterMover> movers) {
        return movers.stream()
                .map(mover -> new NewsletterMoverResponse(mover.symbol(), mover.name(), mover.startClose(),
                        mover.endClose(), mover.changePct()))
                .toList();
    }

    private static NewsletterNewsResponse toResponse(NewsletterNewsItem item) {
        return new NewsletterNewsResponse(item.symbol(), item.headline(), item.source(), item.url(),
                item.publishedAt());
    }
}
