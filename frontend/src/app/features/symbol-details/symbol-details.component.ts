import { CommonModule } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, Subject, combineLatest, of } from 'rxjs';
import { catchError, finalize, startWith, switchMap, tap } from 'rxjs/operators';

import { toHoldingRow } from '../dashboard/holdings/holding-row';
import { MarketDataService } from '../dashboard/market-data.service';
import { MarketQuotesStore } from '../dashboard/market-quotes.store';
import {
  BarTimeframe,
  InstrumentResponse,
  PlatformErrorResponse,
  PositionResponse,
  PriceBar,
} from '../dashboard/trading.models';
import { TradingService } from '../dashboard/trading.service';
import { CandlestickChartComponent } from './candlestick-chart/candlestick-chart.component';

export type Trend = 'up' | 'down' | 'flat';

/** Quote, the user's position if they hold any, and a candlestick chart of one instrument. */
@Component({
  selector: 'app-symbol-details',
  imports: [CommonModule, RouterLink, CandlestickChartComponent],
  providers: [MarketQuotesStore],
  templateUrl: './symbol-details.component.html',
  styleUrl: './symbol-details.component.scss',
})
export class SymbolDetailsComponent {
  static readonly BAR_LIMIT = 120;

  private readonly store = inject(MarketQuotesStore);
  private readonly marketDataService = inject(MarketDataService);
  private readonly tradingService = inject(TradingService);
  private readonly reloadBars$ = new Subject<void>();

  /** From the route, e.g. /symbols/AAPL. */
  readonly symbol = input('', { transform: (value: string | undefined) => (value ?? '').trim().toUpperCase() });

  readonly timeframes: { value: BarTimeframe; label: string }[] = [
    { value: '15Min', label: '15m' },
    { value: '1Hour', label: '1H' },
    { value: '1Day', label: '1D' },
  ];
  readonly timeframe = signal<BarTimeframe>('1Day');

  /** All instruments, or null while they are still loading. */
  private readonly instruments = signal<InstrumentResponse[] | null>(null);
  /** Open positions, or null while they are still loading. */
  private readonly positions = signal<PositionResponse[] | null>(null);
  readonly positionsError = signal<string | null>(null);

  readonly bars = signal<PriceBar[]>([]);
  readonly barsLoading = signal(true);
  readonly barsError = signal<string | null>(null);

  readonly quoteError = this.store.errorMessage;
  readonly lastUpdated = this.store.lastUpdated;

  readonly instrument = computed(
    () => this.instruments()?.find((instrument) => instrument.symbol === this.symbol()) ?? null
  );
  // Only once the list has loaded; if it fails to load, the page still shows what it can
  readonly notFound = computed(() => this.instruments() !== null && this.instruments()!.length > 0 && !this.instrument());
  readonly currency = computed(() => this.instrument()?.currency ?? 'USD');

  readonly quote = computed(() => this.store.quotes().get(this.symbol()) ?? null);
  readonly quoteLoading = computed(() => !this.lastUpdated() && !this.quoteError());

  /** The user's position in this symbol; null when they hold none, so the section is left out. */
  readonly holding = computed(() => {
    const position = this.positions()?.find(
      (candidate) => candidate.symbol === this.symbol() && candidate.quantity > 0
    );
    return position ? toHoldingRow(position, this.quote()?.price) : null;
  });

  constructor() {
    effect(() => this.store.watch(this.symbol() ? [this.symbol()] : []));
    this.loadInstruments();
    this.loadPositions();
    this.loadBars();
  }

  selectTimeframe(timeframe: BarTimeframe): void {
    this.timeframe.set(timeframe);
  }

  retryBars(): void {
    this.reloadBars$.next();
  }

  refreshQuote(): void {
    this.store.refresh();
  }

  trend(value: number | null): Trend {
    if (value == null || value === 0) {
      return 'flat';
    }
    return value > 0 ? 'up' : 'down';
  }

  private loadInstruments(): void {
    this.tradingService
      .getInstruments()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (instruments) => this.instruments.set(instruments),
        error: () => this.instruments.set([]),
      });
  }

  private loadPositions(): void {
    this.tradingService
      .getCurrentUserProfile()
      .pipe(
        switchMap((profile) =>
          profile.accountId == null ? of([]) : this.tradingService.getAccountPositions(profile.accountId)
        ),
        takeUntilDestroyed()
      )
      .subscribe({
        next: (positions) => this.positions.set(positions),
        error: (error: unknown) => {
          this.positions.set([]);
          this.positionsError.set(this.readErrorMessage(error, 'Failed to load your position.'));
        },
      });
  }

  // Refetches when the symbol or timeframe changes; a newer request cancels an older one
  private loadBars(): void {
    combineLatest([toObservable(this.symbol), toObservable(this.timeframe), this.reloadBars$.pipe(startWith(undefined))])
      .pipe(
        switchMap(([symbol, timeframe]) => {
          if (!symbol) {
            return EMPTY;
          }
          this.barsLoading.set(true);
          this.barsError.set(null);
          return this.marketDataService.getBars(symbol, timeframe, SymbolDetailsComponent.BAR_LIMIT).pipe(
            tap((bars) => this.bars.set(bars)),
            catchError((error: unknown) => {
              this.bars.set([]);
              this.barsError.set(this.readErrorMessage(error, 'Failed to load price history.'));
              return EMPTY;
            }),
            finalize(() => this.barsLoading.set(false))
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe();
  }

  private readErrorMessage(error: unknown, fallback: string): string {
    const apiError = (error as { error?: Partial<PlatformErrorResponse> } | null)?.error;
    return apiError?.errorCode && apiError.message ? apiError.message : fallback;
  }
}
