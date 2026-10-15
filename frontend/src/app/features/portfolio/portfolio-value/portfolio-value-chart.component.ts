import { CommonModule } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, combineLatest, forkJoin, of } from 'rxjs';
import { catchError, distinctUntilChanged, map, switchMap } from 'rxjs/operators';

import { PositionResponse } from '../../../api/trading';
import { MarketDataService } from '../../../core/trading/market-data.service';
import { BarTimeframe, PriceBar } from '../../../core/trading/trading.models';
import { niceStep } from '../../trade/candlestick-chart/candlestick-layout';
import { ValuePoint, buildValueSeries } from './portfolio-value';

export interface ValueRange {
  label: string;
  timeframe: BarTimeframe;
  bars: number;
}

const PADDING = { top: 12, right: 12, bottom: 26, left: 52 };
const MIN_TIME_TICK_SPACING = 120;

const compactCurrency = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 1,
});

/** The current holdings valued at past closes, over a chosen range. */
@Component({
  selector: 'app-portfolio-value-chart',
  imports: [CommonModule],
  templateUrl: './portfolio-value-chart.component.html',
  styleUrl: './portfolio-value-chart.component.scss',
})
export class PortfolioValueChartComponent {
  static readonly HEIGHT = 240;

  private readonly marketDataService = inject(MarketDataService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /** Open positions, or null while they are still loading. */
  readonly positions = input<PositionResponse[] | null>(null);
  /** The account's cash, or null while the account is still loading. */
  readonly cashBalance = input<number | null>(null);

  readonly ranges: ValueRange[] = [
    { label: '1D', timeframe: '15Min', bars: 26 },
    { label: '1W', timeframe: '1Hour', bars: 40 },
    { label: '1M', timeframe: '1Day', bars: 22 },
    { label: '3M', timeframe: '1Day', bars: 63 },
    { label: '1Y', timeframe: '1Day', bars: 252 },
  ];
  readonly range = signal<ValueRange>(this.ranges[2]);

  readonly height = PortfolioValueChartComponent.HEIGHT;
  readonly width = signal(0);
  readonly loading = signal(true);
  readonly failed = signal(false);
  private readonly barsBySymbol = signal<ReadonlyMap<string, PriceBar[]>>(new Map());
  readonly activeIndex = signal<number | null>(null);

  readonly series = computed<ValuePoint[]>(() =>
    buildValueSeries(this.positions() ?? [], this.barsBySymbol(), this.cashBalance() ?? 0)
  );

  readonly change = computed(() => {
    const series = this.series();
    if (series.length < 2) {
      return null;
    }
    const first = series[0].value;
    const amount = series.at(-1)!.value - first;
    return { amount, percent: first > 0 ? (amount * 100) / first : null };
  });

  readonly latest = computed(() => this.series().at(-1) ?? null);
  readonly intraday = computed(() => this.range().timeframe !== '1Day');

  readonly layout = computed(() => {
    const series = this.series();
    const width = this.width();
    const plotLeft = PADDING.left;
    const plotRight = width - PADDING.right;
    const plotTop = PADDING.top;
    const plotBottom = this.height - PADDING.bottom;
    if (series.length < 2 || plotRight <= plotLeft) {
      return null;
    }

    const values = series.map((point) => point.value);
    const low = Math.min(...values);
    const high = Math.max(...values);
    const padding = high > low ? (high - low) * 0.08 : Math.max(high * 0.01, 1);
    const [min, max] = [low - padding, high + padding];
    const x = (index: number) => plotLeft + (index / (series.length - 1)) * (plotRight - plotLeft);
    const y = (value: number) => plotBottom - ((value - min) / (max - min)) * (plotBottom - plotTop);

    const points = series.map((point, index) => ({ x: x(index), y: y(point.value), point }));
    const line = points.map((p, index) => `${index === 0 ? 'M' : 'L'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join('');
    const area = `${line}L${plotRight},${plotBottom}L${plotLeft},${plotBottom}Z`;

    const step = niceStep(max - min, 4);
    const valueTicks = [];
    for (let value = Math.ceil(min / step) * step; value <= max; value += step) {
      valueTicks.push({ value, y: y(value), label: compactCurrency.format(value) });
    }

    const every = Math.max(1, Math.ceil(series.length / Math.max(1, Math.floor((plotRight - plotLeft) / MIN_TIME_TICK_SPACING))));
    const timeTicks = points.filter((_, index) => index % every === 0 && index > 0 && index < points.length - 1);

    return { points, line, area, valueTicks, timeTicks, plotLeft, plotRight, plotTop, plotBottom };
  });

  readonly active = computed(() => {
    const points = this.layout()?.points ?? [];
    const index = this.activeIndex();
    return index == null ? null : (points[index] ?? null);
  });

  readonly summary = computed(() => {
    const series = this.series();
    if (series.length < 2) {
      return 'No portfolio value history';
    }
    const values = series.map((point) => point.value);
    return (
      `Portfolio value over ${this.range().label}: from ${compactCurrency.format(values[0])} ` +
      `to ${compactCurrency.format(values.at(-1)!)}, ranging from ${compactCurrency.format(Math.min(...values))} ` +
      `to ${compactCurrency.format(Math.max(...values))}. Use the arrow keys to read each point.`
    );
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const observer = new ResizeObserver(([entry]) => this.width.set(Math.floor(entry.contentRect.width)));
      observer.observe(this.host.nativeElement);
      destroyRef.onDestroy(() => observer.disconnect());
    });
    this.loadBars();
  }

  selectRange(range: ValueRange): void {
    this.range.set(range);
  }

  point(event: PointerEvent): void {
    const layout = this.layout();
    if (!layout) {
      return;
    }
    const svg = event.currentTarget as SVGSVGElement;
    const x = event.clientX - svg.getBoundingClientRect().left;
    const fraction = (x - layout.plotLeft) / (layout.plotRight - layout.plotLeft);
    const last = layout.points.length - 1;
    this.activeIndex.set(Math.max(0, Math.min(last, Math.round(fraction * last))));
  }

  leave(): void {
    this.activeIndex.set(null);
  }

  step(event: KeyboardEvent): void {
    const last = this.series().length - 1;
    const current = this.activeIndex() ?? last;
    const next: Record<string, number> = { ArrowLeft: current - 1, ArrowRight: current + 1, Home: 0, End: last };
    if (!(event.key in next) || last < 0) {
      return;
    }
    event.preventDefault();
    this.activeIndex.set(Math.max(0, Math.min(last, next[event.key])));
  }

  // Refetches when the held symbols or the range change; a bar series that fails is left out
  private loadBars(): void {
    const symbols$ = toObservable(this.positions).pipe(
      map((positions) => (positions === null ? null : positions.map((position) => position.symbol).sort((a, b) => a.localeCompare(b)).join())),
      distinctUntilChanged()
    );
    combineLatest([symbols$, toObservable(this.range)])
      .pipe(
        switchMap(([symbols, range]) => {
          if (symbols === null) {
            return EMPTY;
          }
          this.loading.set(true);
          this.activeIndex.set(null);
          const list = symbols ? symbols.split(',') : [];
          if (list.length === 0) {
            return of(new Map<string, PriceBar[]>());
          }
          let failures = 0;
          return forkJoin(
            list.map((symbol) =>
              this.marketDataService.getBars(symbol, range.timeframe, range.bars).pipe(
                catchError(() => {
                  failures++;
                  return of([] as PriceBar[]);
                }),
                map((bars) => [symbol, bars] as const)
              )
            )
          ).pipe(
            map((entries) => {
              this.failed.set(failures === list.length);
              return new Map(entries);
            })
          );
        }),
        takeUntilDestroyed()
      )
      .subscribe((barsBySymbol) => {
        this.barsBySymbol.set(barsBySymbol);
        this.loading.set(false);
      });
  }
}
