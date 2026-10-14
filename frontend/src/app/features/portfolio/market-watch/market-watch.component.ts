import { CommonModule } from '@angular/common';
import { Component, computed, effect, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MarketQuotesStore } from '../../../core/trading/market-quotes.store';
import { InstrumentResponse, MarketQuote } from '../../../api/trading';

export type PriceDirection = 'up' | 'down' | 'flat';

export interface MarketWatchRow {
  instrument: InstrumentResponse;
  quote: MarketQuote | null;
  held: boolean;
}

@Component({
  selector: 'app-market-watch',
  imports: [CommonModule, RouterLink],
  templateUrl: './market-watch.component.html',
  styleUrl: './market-watch.component.scss',
})
export class MarketWatchComponent {
  // The prices endpoint accepts at most 50 symbols per request
  static readonly MAX_SYMBOLS = 50;

  private readonly store = inject(MarketQuotesStore);

  /** The instruments to quote, or null while they are still loading. */
  readonly instruments = input<InstrumentResponse[] | null>(null);
  /** Symbols the user holds, listed before the rest. */
  readonly heldSymbols = input<string[]>([]);

  readonly refreshing = this.store.refreshing;
  readonly errorMessage = this.store.errorMessage;
  readonly lastUpdated = this.store.lastUpdated;
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
    const quotes = this.store.quotes();
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
    effect(() => this.store.watch(this.watchlist()?.map((instrument) => instrument.symbol) ?? []));
  }

  refresh(): void {
    this.store.refresh();
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
}
