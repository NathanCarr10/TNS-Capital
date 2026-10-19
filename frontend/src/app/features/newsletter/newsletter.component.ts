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
import { readApiError, readErrorMessage } from '../../core/trading/api-error';

/** A mover with the headlines shown under it. */
export interface MoverNews {
  mover: NewsletterMoverResponse;
  headlines: NewsletterNewsResponse[];
}

/**
 * The weekly market newsletter: the week's biggest gainers and losers and the
 * news about them. Opens the latest issue, or the one in the URL
 * (/newsletter/:id). Support staff can preview the current week's issue; a
 * preview is not saved, so traders never see it. Where the API allows it (a
 * demo), support can publish the preview before Friday.
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

  readonly isSupport = inject(AuthService).isSupport();

  readonly issues = signal<NewsletterSummaryResponse[]>([]);
  readonly issue = signal<NewsletterResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  /** Support only: this week's issue so far, shown instead of the published one until closed. */
  readonly preview = signal<NewsletterResponse | null>(null);
  readonly previewing = signal(false);
  readonly previewError = signal<string | null>(null);
  readonly publishing = signal(false);

  /** The preview while there is one, otherwise the published issue. */
  readonly shown = computed(() => this.preview() ?? this.issue());

  /** Movers that have headlines, gainers first, each with its own headlines. */
  readonly news = computed<MoverNews[]>(() => {
    const issue = this.shown();
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

  /** Support only: builds this week's issue so far from the latest prices. Nothing is published. */
  showPreview(): void {
    if (!this.isSupport || this.previewing()) {
      return;
    }
    this.previewing.set(true);
    this.previewError.set(null);
    this.newsletterApi.previewIssue().subscribe({
      next: (preview) => {
        this.previewing.set(false);
        this.preview.set(preview);
      },
      error: (error: unknown) => {
        this.previewing.set(false);
        this.previewError.set(readErrorMessage(error, "This week's newsletter could not be previewed."));
      },
    });
  }

  closePreview(): void {
    this.preview.set(null);
  }

  /**
   * Support only: publishes this week's issue now, for traders to read, and opens it.
   * The API builds it again from the latest prices, and refuses (NWS-403) unless
   * early publishing is switched on.
   */
  publish(): void {
    if (!this.isSupport || this.publishing()) {
      return;
    }
    this.publishing.set(true);
    this.previewError.set(null);
    this.newsletterApi.publishIssue().subscribe({
      next: (issue) => {
        this.publishing.set(false);
        this.preview.set(null);
        void this.router.navigate(['/newsletter', issue.id]);
      },
      error: (error: unknown) => {
        this.publishing.set(false);
        this.previewError.set(
          readApiError(error)?.errorCode === 'NWS-403'
            ? 'Publishing before Friday is switched off. Set NEWSLETTER_EARLY_PUBLISH=true for a demo.'
            : readErrorMessage(error, "This week's newsletter could not be published.")
        );
      },
    });
  }
}
