import { CommonModule } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, input, signal } from '@angular/core';

import { EquityPoint } from '../../../api/strategy';
import { layoutEquityCurve } from './equity-curve-layout';

const compactCurrency = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 1,
});

/** A backtest's value over time: the strategy against buying and holding. */
@Component({
  selector: 'app-equity-curve-chart',
  imports: [CommonModule],
  templateUrl: './equity-curve-chart.component.html',
  styleUrl: './equity-curve-chart.component.scss',
})
export class EquityCurveChartComponent {
  static readonly HEIGHT = 260;

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly curve = input.required<EquityPoint[]>();
  readonly height = EquityCurveChartComponent.HEIGHT;
  readonly width = signal(0);
  readonly activeIndex = signal<number | null>(null);

  readonly layout = computed(() =>
    layoutEquityCurve(this.curve(), {
      width: this.width(),
      height: this.height,
      padding: { top: 12, right: 12, bottom: 26, left: 56 },
    })
  );

  readonly active = computed(() => {
    const index = this.activeIndex();
    return index == null ? null : (this.layout()?.points[index] ?? null);
  });

  readonly summary = computed(() => {
    const curve = this.curve();
    if (curve.length < 2) {
      return 'No backtest values';
    }
    const last = curve.at(-1)!;
    return (
      `Backtest from ${curve[0].date} to ${last.date}: the strategy ends at ${compactCurrency.format(last.strategy ?? 0)}, ` +
      `buying and holding at ${compactCurrency.format(last.buyAndHold ?? 0)}.`
    );
  });

  readonly formatValue = (value: number) => compactCurrency.format(value);

  constructor() {
    const destroyRef = inject(DestroyRef);
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
    const fraction = (event.clientX - svg.getBoundingClientRect().left - layout.plotLeft) / (layout.plotRight - layout.plotLeft);
    const last = layout.points.length - 1;
    this.activeIndex.set(Math.max(0, Math.min(last, Math.round(fraction * last))));
  }

  leave(): void {
    this.activeIndex.set(null);
  }
}
