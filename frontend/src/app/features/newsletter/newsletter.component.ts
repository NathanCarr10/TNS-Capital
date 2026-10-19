import { CommonModule } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';

import {
  NewsletterControllerService,
  NewsletterMoverResponse,
  NewsletterNewsResponse,
  NewsletterResponse,
  NewsletterSummaryResponse,
} from '../../api/trading';
import { AuthService } from '../../core/auth/auth.service';
import { readErrorMessage } from '../../core/trading/api-error';

/** A mover with the headlines shown under it. */
export interface MoverNews {
  mover: NewsletterMoverResponse;
  headlines: NewsletterNewsResponse[];
}

/**
 * The weekly market newsletter: the week's biggest gainers and losers and the
 * news about them. Opens the latest issue, or the one in the URL
 * (/newsletter/:id). Admins can publish the current week's issue early.
 */
@Component({
  selector: 'app-newsletter',
  imports: [CommonModule, RouterLink],
  templateUrl: './newsletter.component.html',
  styleUrl: './newsletter.component.scss',
})
export class NewsletterComponent {
  private readonly newsletterApi = inject(NewsletterControllerService);
  private readonly router = inject(Router);

  readonly isAdmin = inject(AuthService).isAdmin();

  readonly issues = signal<NewsletterSummaryResponse[]>([]);
  readonly issue = signal<NewsletterResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly publishing = signal(false);
  readonly publishError = signal<string | null>(null);

  /** Movers that have headlines, gainers first, each with its own headlines. */
  readonly news = computed<MoverNews[]>(() => {
    const issue = this.issue();
    if (!issue) {
      return [];
    }
    return [...issue.gainers, ...issue.losers]
      .map((mover) => ({ mover, headlines: issue.news.filter((item) => item.symbol === mover.symbol) }))
      .filter((entry) => entry.headlines.length > 0);
  });

  constructor() {
    inject(ActivatedRoute)
      .paramMap.pipe(
        map((params) => params.get('id')),
        switchMap((id) => {
          this.loading.set(true);
          this.error.set(null);
          return this.newsletterApi.listIssues().pipe(
            tap((issues) => this.issues.set(issues)),
            // Without an id in the URL, open the newest issue
            switchMap((issues) => {
              const issueId = id ? Number(id) : issues[0]?.id;
              return issueId == null ? of(null) : this.newsletterApi.getIssue(issueId);
            }),
            catchError((error: unknown) => {
              this.issue.set(null);
              this.loading.set(false);
              this.error.set(readErrorMessage(error, 'Failed to load the newsletter.'));
              return EMPTY;
            })
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe((issue) => {
        this.issue.set(issue);
        this.loading.set(false);
      });
  }

  open(issueId: string): void {
    void this.router.navigate(['/newsletter', issueId]);
  }

  /** Admin only: publishes this week's issue now (or opens it if it exists already). */
  publish(): void {
    if (!this.isAdmin || this.publishing()) {
      return;
    }
    this.publishing.set(true);
    this.publishError.set(null);
    this.newsletterApi.generateIssue().subscribe({
      next: (issue) => {
        this.publishing.set(false);
        void this.router.navigate(['/newsletter', issue.id]);
      },
      error: (error: unknown) => {
        this.publishing.set(false);
        this.publishError.set(readErrorMessage(error, "This week's newsletter could not be published."));
      },
    });
  }
}
