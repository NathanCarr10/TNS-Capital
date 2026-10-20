import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { of, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

import { NewsHeadlineResponse } from '../../../api/trading';
import { MarketDataService } from '../../../core/trading/market-data.service';

/**
 * The latest headlines about the symbol on the Trade page, one card each in a row that
 * scrolls past on a loop. Hovering or focusing it pauses the scroll; users who prefer
 * reduced motion get a still row they can scroll sideways. Without news it is left out.
 */
@Component({
  selector: 'app-news-ticker',
  imports: [DatePipe],
  templateUrl: './news-ticker.component.html',
  styleUrl: './news-ticker.component.scss',
})
export class NewsTickerComponent {
  /** The API caches news for 5 minutes, so polling more often finds nothing new. */
  static readonly REFRESH_INTERVAL_MS = 5 * 60 * 1000;
  // Slow enough to read a headline as it passes
  static readonly SECONDS_PER_CARD = 8;

  private readonly marketData = inject(MarketDataService);

  readonly symbol = input.required<string>();

  readonly headlines = signal<NewsHeadlineResponse[]>([]);

  // The cards are drawn twice so the scroll loops without a gap; the copy is hidden from screen readers
  readonly copies = [0, 1];

  /** More headlines scroll for longer so the row moves at the same speed after a refresh. */
  readonly duration = computed(() => `${this.headlines().length * NewsTickerComponent.SECONDS_PER_CARD}s`);

  constructor() {
    toObservable(this.symbol)
      .pipe(
        switchMap((symbol) => {
          this.headlines.set([]);
          return timer(0, NewsTickerComponent.REFRESH_INTERVAL_MS).pipe(
            switchMap(() => this.marketData.getNews(symbol).pipe(catchError(() => of(null)))),
          );
        }),
        takeUntilDestroyed(),
      )
      // On a failed refresh, keep the headlines already shown
      .subscribe((headlines) => {
        if (headlines) {
          this.headlines.set(headlines);
        }
      });
  }
}
