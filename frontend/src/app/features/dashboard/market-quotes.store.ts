import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BehaviorSubject, EMPTY, Observable, Subject, combineLatest, fromEvent, timer } from 'rxjs';
import { catchError, distinctUntilChanged, finalize, map, startWith, switchMap, tap } from 'rxjs/operators';

import { MarketDataService } from './market-data.service';
import { MarketQuote, PlatformErrorResponse } from './trading.models';

/**
 * Live quotes shared by the dashboard's market data table and performance panel,
 * so both show the same prices from one request.
 *
 * Polls while the tab is visible; provide it per component so polling stops with it.
 */
@Injectable()
export class MarketQuotesStore {
  static readonly REFRESH_INTERVAL_MS = 15000;

  private readonly marketDataService = inject(MarketDataService);
  private readonly document = inject(DOCUMENT);
  private readonly symbols$ = new BehaviorSubject<string[]>([]);
  private readonly refresh$ = new Subject<void>();

  readonly quotes = signal<ReadonlyMap<string, MarketQuote>>(new Map());
  readonly refreshing = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastUpdated = signal<Date | null>(null);

  constructor() {
    const symbols$ = this.symbols$.pipe(
      map((symbols) => [...symbols].sort()),
      // Reordering the same symbols, e.g. when holdings load, must not trigger an extra fetch
      distinctUntilChanged((previous, current) => previous.join() === current.join())
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
            switchMap(() => timer(0, MarketQuotesStore.REFRESH_INTERVAL_MS)),
            switchMap(() => this.fetchQuotes(symbols))
          );
        }),
        takeUntilDestroyed(inject(DestroyRef))
      )
      .subscribe();
  }

  /** Replaces the symbols being quoted. */
  watch(symbols: string[]): void {
    this.symbols$.next(symbols);
  }

  refresh(): void {
    this.refresh$.next();
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
