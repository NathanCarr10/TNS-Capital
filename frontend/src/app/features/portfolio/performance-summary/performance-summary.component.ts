import { CommonModule } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';

import { MarketQuotesStore } from '../../../core/trading/market-quotes.store';
import { IconComponent } from '../../../layout/icon/icon.component';
import { PositionResponse } from '../../../api/trading';
import { calculatePortfolioMetrics } from './portfolio-metrics';

@Component({
  selector: 'app-performance-summary',
  imports: [CommonModule, IconComponent],
  templateUrl: './performance-summary.component.html',
  styleUrl: './performance-summary.component.scss',
})
export class PerformanceSummaryComponent {
  private readonly store = inject(MarketQuotesStore);

  /** Open positions, or null while they are still loading. */
  readonly positions = input<PositionResponse[] | null>(null);
  /** The account's cash, or null while the account is still loading. */
  readonly cashBalance = input<number | null>(null);
  /** Why the positions could not be loaded; every figure depends on them. */
  readonly error = input<string | null>(null);

  // Wait for the first quotes too, or every position would briefly show as unpriced
  readonly loading = computed(
    () =>
      this.positions() === null ||
      this.cashBalance() === null ||
      ((this.positions()?.length ?? 0) > 0 && !this.store.lastUpdated() && !this.store.errorMessage())
  );

  readonly metrics = computed(() => {
    const positions = this.positions();
    const cashBalance = this.cashBalance();
    if (positions === null || cashBalance === null) {
      return null;
    }
    return calculatePortfolioMetrics(positions, this.store.quotes(), cashBalance);
  });

  trend(value: number | null): string {
    if (value == null || value === 0) {
      return 'pnl-flat';
    }
    return value > 0 ? 'pnl-up' : 'pnl-down';
  }
}
