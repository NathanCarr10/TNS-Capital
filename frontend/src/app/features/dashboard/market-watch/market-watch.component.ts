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
  held: boolean;
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
  /** Symbols the user holds, listed before the rest. */
  readonly heldSymbols = input<string[]>([]);

  readonly quotes = signal<ReadonlyMap<string, MarketQuote>>(new Map());
  readonly refreshing = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly lastUpdated = signal<Date | null>(null);
  readonly skeletonRows = [1, 2, 3, 4, 5];

  private readonly held = computed(() => new Set(this.heldSymbols()));

  // Held instruments go first so they are never cut off by the symbol limit
  readonly watchlist = computed(() => {
    const instruments = this.instruments();
    if (!instruments) {
      return null;
    }
    const held = this.held();
    return [...instruments]
      .sort((a, b) => Number(held.has(b.symbol)) - Number(held.has(a.symbol)))
      .slice(0, MarketWatchComponent.MAX_SYMBOLS);
  });

  /** Held symbols first, then the rest; each group by the biggest move in either direction. */
  readonly rows = computed<MarketWatchRow[]>(() => {
    const held = this.held();
    const quotes = this.quotes();
    return (this.watchlist() ?? [])
      .map((instrument) => ({
        instrument,
        quote: quotes.get(instrument.symbol) ?? null,
        held: held.has(instrument.symbol),
      }))
      .sort(
        (a, b) =>
          Number(b.held) - Number(a.held) ||
          this.moveSize(b.quote) - this.moveSize(a.quote) ||
          a.instrument.symbol.localeCompare(b.instrument.symbol)
      );
  });

  constructor() {
    const symbols$ = toObservable(this.watchlist).pipe(
      map((instruments) => instruments?.map((instrument) => instrument.symbol).sort() ?? []),
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

  // Unknown changes sort last
  private moveSize(quote: MarketQuote | null): number {
    return quote?.changePercent == null ? -1 : Math.abs(quote.changePercent);
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
