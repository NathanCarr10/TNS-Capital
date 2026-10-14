import { CommonModule } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, input, signal } from '@angular/core';

import { BarTimeframe, PriceBar } from '../../../core/trading/trading.models';
import { MIN_SLOT, PRICE_AXIS_WIDTH, layoutCandlesticks, movingAverage } from './candlestick-layout';

@Component({
  selector: 'app-candlestick-chart',
  imports: [CommonModule],
  templateUrl: './candlestick-chart.component.html',
  styleUrl: './candlestick-chart.component.scss',
})
export class CandlestickChartComponent {
  static readonly HEIGHT = 340;
  /** Most bars shown at once; any before them only feed the moving averages. */
  static readonly MAX_VISIBLE = 60;

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Bars to draw, oldest first. */
  readonly bars = input.required<PriceBar[]>();
  /** Picks how bar times are labelled. */
  readonly timeframe = input.required<BarTimeframe>();
  /** Periods of the moving averages to draw over the candles, e.g. [20, 50]. */
  readonly movingAverages = input<number[]>([]);

  readonly height = CandlestickChartComponent.HEIGHT;
  readonly priceAxisWidth = PRICE_AXIS_WIDTH;
  readonly width = signal(0);
  /** The bar under the pointer or picked with the arrow keys; null shows the latest. */
  readonly activeIndex = signal<number | null>(null);

  /** How many of the most recent bars are shown: as many as fit the width, up to MAX_VISIBLE. */
  private readonly visibleCount = computed(() => {
    const fit = Math.floor((this.width() - PRICE_AXIS_WIDTH) / MIN_SLOT);
    return Math.min(fit > 0 ? fit : Infinity, CandlestickChartComponent.MAX_VISIBLE);
  });

  readonly visibleBars = computed(() => this.bars().slice(-this.visibleCount()));

  // Averaged over every bar, so the lines start at the chart's left edge when there is enough history
  readonly overlays = computed(() =>
    this.movingAverages().map((period) => ({
      period,
      values: movingAverage(this.bars(), period).slice(-this.visibleCount()),
    }))
  );

  readonly layout = computed(() =>
    layoutCandlesticks(
      this.visibleBars(),
      this.width(),
      this.height,
      this.overlays().map((overlay) => overlay.values)
    )
  );

  /** The candle the readout and price marker describe. */
  readonly marked = computed(() => {
    const candles = this.layout()?.candles ?? [];
    return candles[this.activeIndex() ?? candles.length - 1] ?? null;
  });

  readonly readout = computed(() => {
    const bars = this.visibleBars();
    const index = this.activeIndex() ?? bars.length - 1;
    const bar = bars[index];
    if (!bar) {
      return null;
    }
    const previousClose = bars[index - 1]?.close;
    const change = previousClose != null ? bar.close - previousClose : null;
    return {
      bar,
      change,
      changePercent: change != null && previousClose ? (change * 100) / previousClose : null,
    };
  });

  readonly intraday = computed(() => this.timeframe() !== '1Day');

  readonly summary = computed(() => {
    const bars = this.visibleBars();
    if (bars.length === 0) {
      return 'No price history';
    }
    const first = bars[0];
    const last = bars[bars.length - 1];
    const low = Math.min(...bars.map((bar) => bar.low));
    const high = Math.max(...bars.map((bar) => bar.high));
    return (
      `Candlestick chart of ${bars.length} bars from ${new Date(first.time).toLocaleString()} ` +
      `to ${new Date(last.time).toLocaleString()}. Opened at ${first.open}, last closed at ${last.close}, ` +
      `ranging from ${low} to ${high}. Use the arrow keys to read each bar.`
    );
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    // Drawn in pixels rather than a scaled viewBox so text and 1px wicks stay crisp at any width
    afterNextRender(() => {
      const observer = new ResizeObserver(([entry]) => this.width.set(Math.floor(entry.contentRect.width)));
      observer.observe(this.host.nativeElement);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  point(event: PointerEvent): void {
    const layout = this.layout();
    if (!layout) {
      return;
    }
    const svg = event.currentTarget as SVGSVGElement;
    const x = event.clientX - svg.getBoundingClientRect().left;
    if (x > layout.plotRight) {
      this.activeIndex.set(null);
      return;
    }
    this.activeIndex.set(Math.max(0, Math.min(layout.candles.length - 1, Math.floor(x / layout.slot))));
  }

  leave(): void {
    this.activeIndex.set(null);
  }

  step(event: KeyboardEvent): void {
    const last = this.visibleBars().length - 1;
    const current = this.activeIndex() ?? last;
    const next: Record<string, number> = {
      ArrowLeft: current - 1,
      ArrowRight: current + 1,
      Home: 0,
      End: last,
    };
    if (!(event.key in next) || last < 0) {
      return;
    }
    event.preventDefault();
    this.activeIndex.set(Math.max(0, Math.min(last, next[event.key])));
  }
}
