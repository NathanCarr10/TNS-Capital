import { CommonModule, DOCUMENT } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, Subject, combineLatest, fromEvent, timer } from 'rxjs';
import { catchError, distinctUntilChanged, finalize, map, startWith, switchMap, tap } from 'rxjs/operators';

import { MarketDataService } from '../market-data.service';
import { InstrumentResponse, MarketQuote, PlatformErrorResponse } from '../trading.models';

export type PriceDirection = 'up' | 'down' | 'flat';

export interface MarketWatchRow {
  instrument: InstrumentResponse;
  quote: MarketQuote | null;
}

@Component({
  selector: 'app-market-watch',
  imports: [CommonModule],
  templateUrl: './market-watch.component.html',
  styleUrl: './market-watch.component.scss',
})
export class MarketWatchComponent {
  static readonly REFRESH_INTERVAL_MS = 15000;
  // The prices endpoint accepts at most 50 symbols per request
  static readonly MAX_SYMBOLS = 50;

  private readonly marketDataService = inject(MarketDataService);
  private readonly document = inject(DOCUMENT);
  private readonly refresh$ = new Subject<void>();

  /** The instruments to quote, or null while they are still loading. */
  readonly instruments = input<InstrumentResponse[] | null>(null);

  readonly quotes = signal<ReadonlyMap<string, MarketQuote>>(new Map());
  readonly refreshing = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastUpdated = signal<Date | null>(null);
  readonly skeletonRows = [1, 2, 3, 4, 5];

  readonly watchlist = computed(() => this.instruments()?.slice(0, MarketWatchComponent.MAX_SYMBOLS) ?? null);
  readonly rows = computed<MarketWatchRow[]>(() =>
    (this.watchlist() ?? []).map((instrument) => ({
      instrument,
      quote: this.quotes().get(instrument.symbol) ?? null,
    }))
  );

  constructor() {
    const symbols$ = toObservable(this.watchlist).pipe(
      map((instruments) => instruments?.map((instrument) => instrument.symbol) ?? [])
    );
    const visible$ = fromEvent(this.document, 'visibilitychange').pipe(
      startWith(null),
      map(() => !this.document.hidden),
      distinctUntilChanged()
    );

    // Poll while the tab is visible; a manual refresh fetches now and restarts the interval
    combineLatest([symbols$, visible$])
      .pipe(
        switchMap(([symbols, visible]) => {
          if (symbols.length === 0 || !visible) {
            return EMPTY;
          }
          return this.refresh$.pipe(
            startWith(undefined),
            switchMap(() => timer(0, MarketWatchComponent.REFRESH_INTERVAL_MS)),
            switchMap(() => this.fetchQuotes(symbols))
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe();
  }

  refresh(): void {
    this.refresh$.next();
  }

  direction(quote: MarketQuote): PriceDirection | null {
    if (quote.change == null) {
      return null;
    }
    if (quote.change > 0) {
      return 'up';
    }
    return quote.change < 0 ? 'down' : 'flat';
  }

  private fetchQuotes(symbols: string[]): Observable<MarketQuote[]> {
    this.refreshing.set(true);

    return this.marketDataService.getQuotes(symbols).pipe(
      tap((quotes) => {
        this.quotes.set(new Map(quotes.map((quote) => [quote.symbol, quote])));
        this.errorMessage.set(null);
        this.lastUpdated.set(new Date());
      }),
      catchError((error: unknown) => {
        this.errorMessage.set(this.readErrorMessage(error));
        return EMPTY;
      }),
      finalize(() => this.refreshing.set(false))
    );
  }

  private readErrorMessage(error: unknown): string {
    const apiError = (error as { error?: Partial<PlatformErrorResponse> } | null)?.error;
    return apiError?.errorCode && apiError.message ? apiError.message : 'Failed to load market data.';
  }
}
