import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { of, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

import { NewsHeadlineResponse } from '../../../api/trading';
import { MarketDataService } from '../../../core/trading/market-data.service';

/**
 * The latest headlines about the symbol on the Trade page, one card each in a row
 * that scrolls sideways when they do not all fit. Without news it is left out.
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

  private readonly marketData = inject(MarketDataService);

  readonly symbol = input.required<string>();

  readonly headlines = signal<NewsHeadlineResponse[]>([]);

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
