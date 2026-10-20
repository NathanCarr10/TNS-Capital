import { Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, from } from 'rxjs';
import { catchError, map, mergeMap } from 'rxjs/operators';

import { MarketQuote } from '../../api/trading';
import { AccountStore } from '../../core/trading/account.store';
import { MarketDataService } from '../../core/trading/market-data.service';
import { MarketQuotesStore } from '../../core/trading/market-quotes.store';

export type TickerDirection = 'up' | 'down' | 'flat';

export interface TickerItem {
  symbol: string;
  price: number | null;
  direction: TickerDirection | null;
  /** e.g. "▲ 1.23%", or null when the day's change is unknown */
  change: string | null;
  /** SVG polyline points, or null until the bars load */
  sparkline: string | null;
  label: string;
}

const SPARKLINE_WIDTH = 48;
const SPARKLINE_HEIGHT = 16;

/** Scales closing prices into polyline points for a SPARKLINE_WIDTH x SPARKLINE_HEIGHT box. */
export function sparklinePoints(closes: number[]): string | null {
  if (closes.length < 2) {
    return null;
  }
  const min = Math.min(...closes);
  const range = Math.max(...closes) - min;
  const step = SPARKLINE_WIDTH / (closes.length - 1);
  return closes
    .map((close, index) => {
      // A flat line sits in the middle rather than on the bottom edge
      const y = range === 0 ? SPARKLINE_HEIGHT / 2 : SPARKLINE_HEIGHT - ((close - min) / range) * SPARKLINE_HEIGHT;
      return `${(index * step).toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
}

function directionOf(quote: MarketQuote | undefined): TickerDirection | null {
  if (quote?.change == null) {
    return null;
  }
  if (quote.change === 0) {
    return 'flat';
  }
  return quote.change > 0 ? 'up' : 'down';
}

const ARROWS: Record<TickerDirection, string> = { up: '▲', down: '▼', flat: '■' };
const SPOKEN: Record<TickerDirection, string> = { up: 'up', down: 'down', flat: 'unchanged' };

/**
 * A strip of prices that scrolls across the top of every page: each symbol's price, day change
 * and a sparkline of its recent 15-minute closes, green when the day is up and red when it is down.
 * Hovering or focusing it pauses the scroll; users who prefer reduced motion (in Settings or, until
 * that is set, in their system) get a still, scrollable row.
 */
@Component({
  selector: 'app-ticker-tape',
  imports: [RouterLink],
  providers: [MarketQuotesStore],
  templateUrl: './ticker-tape.component.html',
  styleUrl: './ticker-tape.component.scss',
})
export class TickerTapeComponent {
  // Each sparkline is its own bars request, so keep the tape short
  static readonly MAX_SYMBOLS = 12;
  // About one regular session of 15-minute bars
  static readonly SPARKLINE_BARS = 26;
  static readonly SECONDS_PER_ITEM = 4;

  private readonly accountStore = inject(AccountStore);
  private readonly quotesStore = inject(MarketQuotesStore);
  private readonly marketDataService = inject(MarketDataService);

  readonly sparklineViewBox = `-1 -1 ${SPARKLINE_WIDTH + 2} ${SPARKLINE_HEIGHT + 2}`;
  // The items are drawn twice so the scroll loops without a gap; the copy is hidden from screen readers
  readonly copies = [0, 1];

  private readonly sparklines = signal<ReadonlyMap<string, string>>(new Map());
  private readonly requestedSparklines = new Set<string>();

  /** Held symbols first, then the other tradable instruments. */
  readonly symbols = computed(() => {
    if (this.accountStore.instruments() === null) {
      return [];
    }
    const tradable = this.accountStore.tradableInstruments().map((instrument) => instrument.symbol);
    return [...new Set([...this.accountStore.heldSymbols(), ...tradable])].slice(0, TickerTapeComponent.MAX_SYMBOLS);
  });

  /** No instruments to quote, so the tape is not shown at all. */
  readonly empty = computed(() => this.accountStore.instruments() !== null && this.symbols().length === 0);
  readonly loaded = computed(() => this.quotesStore.lastUpdated() !== null);
  readonly unavailable = computed(() => !this.loaded() && this.quotesStore.errorMessage() !== null);

  readonly items = computed<TickerItem[]>(() => {
    const quotes = this.quotesStore.quotes();
    const sparklines = this.sparklines();
    return this.symbols().map((symbol) => {
      const quote = quotes.get(symbol);
      const direction = directionOf(quote);
      const change =
        direction && quote?.changePercent != null
          ? `${ARROWS[direction]} ${Math.abs(quote.changePercent).toFixed(2)}%`
          : null;
      const price = quote?.price ?? null;
      return {
        symbol,
        price,
        direction,
        change,
        sparkline: sparklines.get(symbol) ?? null,
        label: this.spokenLabel(symbol, price, direction, quote?.changePercent),
      };
    });
  });

  /** Longer tapes scroll for longer so every tape moves at the same speed. */
  readonly duration = computed(() => `${this.items().length * TickerTapeComponent.SECONDS_PER_ITEM}s`);

  constructor() {
    effect(() => this.quotesStore.watch(this.symbols()));

    // Sparklines load once per symbol; the quotes keep the tape current between page loads
    toObservable(this.symbols)
      .pipe(
        mergeMap((symbols) => {
          const fresh = symbols.filter((symbol) => !this.requestedSparklines.has(symbol));
          fresh.forEach((symbol) => this.requestedSparklines.add(symbol));
          return from(fresh);
        }),
        mergeMap(
          (symbol) =>
            this.marketDataService.getBars(symbol, '15Min', TickerTapeComponent.SPARKLINE_BARS).pipe(
              map((bars) => [symbol, sparklinePoints(bars.map((bar) => bar.close))] as const),
              // A missing sparkline is not worth an error; the price still shows
              catchError(() => EMPTY)
            ),
          4
        ),
        takeUntilDestroyed()
      )
      .subscribe(([symbol, points]) => {
        if (points) {
          this.sparklines.update((current) => new Map(current).set(symbol, points));
        }
      });
  }

  private spokenLabel(
    symbol: string,
    price: number | null,
    direction: TickerDirection | null,
    changePercent: number | null | undefined
  ): string {
    if (price === null) {
      return `${symbol}, no live price`;
    }
    const move =
      direction && changePercent != null ? `, ${SPOKEN[direction]} ${Math.abs(changePercent).toFixed(2)}% today` : '';
    return `${symbol} ${price.toFixed(2)}${move}. Open the trade page.`;
  }
}
