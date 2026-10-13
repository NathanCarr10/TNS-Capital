import { CommonModule } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MarketQuotesStore } from '../market-quotes.store';
import { PositionResponse } from '../../../api/trading';
import { HoldingRow, toHoldingRow } from './holding-row';

@Component({
  selector: 'app-holdings',
  imports: [CommonModule, RouterLink],
  templateUrl: './holdings.component.html',
  styleUrl: './holdings.component.scss',
})
export class HoldingsComponent {
  private readonly store = inject(MarketQuotesStore);

  /** Open positions, or null while they are still loading. */
  readonly positions = input<PositionResponse[] | null>(null);
  /** Why the positions could not be loaded. */
  readonly error = input<string | null>(null);

  readonly skeletonRows = [1, 2, 3];

  // Wait for the first quotes too, or every holding would briefly show as unpriced
  readonly loading = computed(
    () =>
      this.positions() === null ||
      ((this.positions()?.length ?? 0) > 0 && !this.store.lastUpdated() && !this.store.errorMessage())
  );

  /** Largest holdings first; unpriced ones last, by symbol. */
  readonly rows = computed<HoldingRow[]>(() => {
    const quotes = this.store.quotes();
    return (this.positions() ?? [])
      .map((position) => toHoldingRow(position, quotes.get(position.symbol)?.price))
      .sort((a, b) => (b.value ?? -1) - (a.value ?? -1) || a.symbol.localeCompare(b.symbol));
  });

  trend(value: number): string {
    if (value === 0) {
      return 'pnl-flat';
    }
    return value > 0 ? 'pnl-up' : 'pnl-down';
  }
}
