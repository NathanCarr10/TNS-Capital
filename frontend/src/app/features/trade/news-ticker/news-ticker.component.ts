import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { of, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

import { NewsHeadlineResponse } from '../../../api/trading';
import { MarketDataService } from '../../../core/trading/market-data.service';
import { IconComponent } from '../../../layout/icon/icon.component';

/**
 * A scrolling strip of the latest headlines about the symbol on the Trade page.
 * It pauses while the pointer or keyboard focus is on it and has a pause button;
 * with reduced motion (Settings, which defaults to the system setting) it is a
 * list to scroll instead. Without news it is left out.
 */
@Component({
  selector: 'app-news-ticker',
  imports: [DatePipe, IconComponent, NgTemplateOutlet],
  templateUrl: './news-ticker.component.html',
  styleUrl: './news-ticker.component.scss',
})
export class NewsTickerComponent {
  /** The API caches news for 5 minutes, so polling more often finds nothing new. */
  static readonly REFRESH_INTERVAL_MS = 5 * 60 * 1000;
  /** Seconds each headline takes to scroll past, so a longer list scrolls at the same speed. */
  static readonly SECONDS_PER_HEADLINE = 8;

  private readonly marketData = inject(MarketDataService);

  readonly symbol = input.required<string>();

  readonly headlines = signal<NewsHeadlineResponse[]>([]);
  readonly paused = signal(false);
  readonly duration = computed(() => Math.max(20, this.headlines().length * NewsTickerComponent.SECONDS_PER_HEADLINE));

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

  togglePause(): void {
    this.paused.update((paused) => !paused);
  }
}
