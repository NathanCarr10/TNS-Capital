import { CommonModule } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';

import { MarketQuotesStore } from '../market-quotes.store';
import { PositionResponse } from '../trading.models';
import { SplitSegment, calculatePortfolioSplit } from './portfolio-split';

interface ActiveSegment {
  segment: SplitSegment;
  /** Centre of the segment, in pixels from the bar's left edge. */
  x: number;
}

@Component({
  selector: 'app-portfolio-split',
  imports: [CommonModule],
  templateUrl: './portfolio-split.component.html',
  styleUrl: './portfolio-split.component.scss',
})
export class PortfolioSplitComponent {
  private readonly store = inject(MarketQuotesStore);

  /** Open positions, or null while they are still loading. */
  readonly positions = input<PositionResponse[] | null>(null);
  /** The account's cash, or null while the account is still loading. */
  readonly cashBalance = input<number | null>(null);
  /** Why the positions could not be loaded. */
  readonly error = input<string | null>(null);

  readonly active = signal<ActiveSegment | null>(null);

  // Wait for the first quotes too, or every holding would briefly drop out of the split
  readonly loading = computed(
    () =>
      this.positions() === null ||
      this.cashBalance() === null ||
      ((this.positions()?.length ?? 0) > 0 && !this.store.lastUpdated() && !this.store.errorMessage())
  );

  readonly split = computed(() => {
    const positions = this.positions();
    const cashBalance = this.cashBalance();
    if (positions === null || cashBalance === null) {
      return null;
    }
    return calculatePortfolioSplit(positions, this.store.quotes(), cashBalance);
  });

  show(segment: SplitSegment, event: Event): void {
    const element = event.currentTarget as HTMLElement;
    this.active.set({ segment, x: element.offsetLeft + element.offsetWidth / 2 });
  }

  hide(): void {
    this.active.set(null);
  }
}
