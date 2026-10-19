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
 * a scheduled job publishes one each Friday. Admins can preview the current
 * week's, e.g. for a demo; a preview is not saved. Where early publishing is
 * switched on (a demo), admins can also publish it before Friday.
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

    /**
     * Previews are for admins (they cost an Alpaca call per instrument). Nothing
     * is saved, so a preview never replaces the issue the weekly job publishes.
     */
    @Operation(summary = "Preview this week's newsletter",
            description = "Admins only. Builds the week so far from the latest prices and news without publishing it; "
                    + "the preview has no id.")
    @ApiResponse(responseCode = "200", description = "This week's issue so far, not saved")
    @ApiResponse(responseCode = "403", description = "AUTH-403 Admins only")
    @ApiResponse(responseCode = "503", description = "MKT-503 No prices to rank the week's movers")
    @GetMapping("/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NewsletterResponse> previewIssue() {
        return ResponseEntity.ok(toResponse(newsletterService.previewWeeklyIssue()));
    }

    /** Publishing before Friday is for demos, so it is switched off unless configured. */
    @Operation(summary = "Publish this week's newsletter early",
            description = "Admins only, where newsletter.early-publish is on (e.g. a demo). Publishes the week so far; "
                    + "the weekly run then skips this week. If this week's issue was published already, returns it.")
    @ApiResponse(responseCode = "200", description = "This week's published issue")
    @ApiResponse(responseCode = "403", description = "AUTH-403 Admins only; NWS-403 Early publishing is switched off")
    @ApiResponse(responseCode = "503", description = "MKT-503 No prices to rank the week's movers")
    @PostMapping("/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NewsletterResponse> publishIssue() {
        return ResponseEntity.ok(toResponse(newsletterService.publishEarly()));
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
