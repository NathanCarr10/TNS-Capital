import { CommonModule } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, Subject, combineLatest, of } from 'rxjs';
import { catchError, finalize, map, startWith, switchMap, tap } from 'rxjs/operators';

import { AccountStore } from '../../core/trading/account.store';
import { readErrorMessage } from '../../core/trading/api-error';
import { MarketDataService } from '../../core/trading/market-data.service';
import { MarketQuotesStore } from '../../core/trading/market-quotes.store';
import { BarTimeframe, PriceBar } from '../../core/trading/trading.models';
import { toHoldingRow } from '../portfolio/holdings/holding-row';
import { CandlestickChartComponent } from './candlestick-chart/candlestick-chart.component';
import { NewsTickerComponent } from './news-ticker/news-ticker.component';
import { OrderTicketComponent } from './order-ticket/order-ticket.component';

export type Trend = 'up' | 'down' | 'flat';

interface MovingAverageOption {
  period: number;
  label: string;
}

/** One instrument: its quote and day's range, a candlestick chart, the user's position and an order ticket. */
@Component({
  selector: 'app-trade',
  imports: [CommonModule, RouterLink, CandlestickChartComponent, NewsTickerComponent, OrderTicketComponent],
  providers: [MarketQuotesStore],
  templateUrl: './trade.component.html',
  styleUrl: './trade.component.scss',
})
export class TradeComponent {
  /** Bars shown, plus enough before them to average over the longest moving average. */
  static readonly BAR_LIMIT = CandlestickChartComponent.MAX_VISIBLE + 50;

  private readonly store = inject(MarketQuotesStore);
  private readonly account = inject(AccountStore);
  private readonly marketDataService = inject(MarketDataService);
  private readonly router = inject(Router);
  private readonly reloadBars$ = new Subject<void>();

  /** From the route, e.g. /trade/AAPL; empty on /trade. */
  readonly symbol = input('', { transform: (value: string | undefined) => (value ?? '').trim().toUpperCase() });

  readonly timeframes: { value: BarTimeframe; label: string }[] = [
    { value: '15Min', label: '15m' },
    { value: '1Hour', label: '1H' },
    { value: '1Day', label: '1D' },
  ];
  readonly timeframe = signal<BarTimeframe>('1Day');

  readonly movingAverageOptions: MovingAverageOption[] = [
    { period: 20, label: 'MA 20' },
    { period: 50, label: 'MA 50' },
  ];
  readonly shownAverages = signal<number[]>([20, 50]);

  readonly positionsError = this.account.positionsError;
  readonly bars = signal<PriceBar[]>([]);
  readonly barsLoading = signal(true);
  readonly barsError = signal<string | null>(null);
  /** The latest daily bar, for the day's open, high, low and volume. */
  readonly session = signal<PriceBar | null>(null);

  readonly quoteError = this.store.errorMessage;
  readonly lastUpdated = this.store.lastUpdated;

  readonly instrument = computed(
    () => this.account.instruments()?.find((instrument) => instrument.symbol === this.symbol()) ?? null
  );
  // Only once the list has loaded; if it fails to load, the page still shows what it can
  readonly notFound = computed(() => {
    const instruments = this.account.instruments();
    return !!this.symbol() && instruments !== null && instruments.length > 0 && !this.instrument();
  });
  readonly currency = computed(() => this.instrument()?.currency ?? 'USD');
  readonly quote = computed(() => this.store.quotes().get(this.symbol()) ?? null);
  readonly quoteLoading = computed(() => !this.lastUpdated() && !this.quoteError());

  /** The user's position in this symbol; null when they hold none, so the section is left out. */
  readonly holding = computed(() => {
    const position = this.account.positions()?.find((candidate) => candidate.symbol === this.symbol());
    return position ? toHoldingRow(position, this.quote()?.price) : null;
  });

  constructor() {
    effect(() => this.store.watch(this.symbol() ? [this.symbol()] : []));
    effect(() => {
      if (this.symbol()) {
        this.account.recentSymbol.set(this.symbol());
      }
    });
    this.openDefaultSymbol();
    this.loadBars();
    this.loadSession();
  }

  selectTimeframe(timeframe: BarTimeframe): void {
    this.timeframe.set(timeframe);
  }

  toggleAverage(period: number): void {
    this.shownAverages.update((shown) =>
      shown.includes(period) ? shown.filter((value) => value !== period) : [...shown, period].sort((a, b) => a - b)
    );
  }

  retryBars(): void {
    this.reloadBars$.next();
  }

  refreshQuote(): void {
    this.store.refresh();
  }

  trend(value: number | null | undefined): Trend {
    if (value == null || value === 0) {
      return 'flat';
    }
    return value > 0 ? 'up' : 'down';
  }

  // On /trade, opens the last symbol viewed, else the first holding, else the first tradable instrument
  private openDefaultSymbol(): void {
    effect(() => {
      // Waits for the positions, unless they will never come because the account failed to load
      const positionsSettled = this.account.positions() !== null || this.account.accountError() !== null;
      if (this.symbol() || !positionsSettled) {
        return;
      }
      const fallback =
        this.account.recentSymbol() ??
        this.account.positions()?.[0]?.symbol ??
        this.account.tradableInstruments()[0]?.symbol;
      if (fallback) {
        void this.router.navigate(['/trade', fallback], { replaceUrl: true });
      }
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
          return this.marketDataService.getBars(symbol, timeframe, TradeComponent.BAR_LIMIT).pipe(
            tap((bars) => this.bars.set(bars)),
            catchError((error: unknown) => {
              this.bars.set([]);
              this.barsError.set(readErrorMessage(error, 'Failed to load price history.'));
              return EMPTY;
            }),
            finalize(() => this.barsLoading.set(false))
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe();
  }

  private loadSession(): void {
    toObservable(this.symbol)
      .pipe(
        switchMap((symbol) => {
          this.session.set(null);
          return symbol
            ? this.marketDataService.getBars(symbol, '1Day', 1).pipe(
                map((bars) => bars.at(-1) ?? null),
                catchError(() => of(null))
              )
            : EMPTY;
        }),
        takeUntilDestroyed()
      )
      .subscribe((bar) => this.session.set(bar));
  }
}
