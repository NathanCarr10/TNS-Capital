/// <reference types="jasmine" />

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PriceBar } from '../../../core/trading/trading.models';
import { CandlestickChartComponent } from './candlestick-chart.component';

describe('CandlestickChartComponent', () => {
  let fixture: ComponentFixture<CandlestickChartComponent>;
  let component: CandlestickChartComponent;

  const bars: PriceBar[] = [
    { time: '2026-10-08T04:00:00Z', open: 100, high: 104, low: 99, close: 103, volume: 1000 },
    { time: '2026-10-09T04:00:00Z', open: 103, high: 105, low: 100, close: 101, volume: 1500 },
    { time: '2026-10-10T04:00:00Z', open: 101, high: 108, low: 101, close: 107, volume: 2000 },
  ];

  const readout = (): string =>
    (fixture.nativeElement.querySelector('.readout') as HTMLElement).textContent!.replace(/\s+/g, ' ').trim();

  /** The readout value next to a label such as O or Vol. */
  const field = (label: string): string => {
    const terms = Array.from(fixture.nativeElement.querySelectorAll('.readout dt')) as HTMLElement[];
    const term = terms.find((dt) => dt.textContent?.trim() === label);
    return term?.nextElementSibling?.textContent?.trim() ?? '';
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CandlestickChartComponent] }).compileComponents();

    fixture = TestBed.createComponent(CandlestickChartComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('bars', bars);
    fixture.componentRef.setInput('timeframe', '1Day');
    // Stands in for the resize observer, which reports the host's width after the first render
    component.width.set(600);
    fixture.detectChanges();
  });

  it('draws one candle per bar, coloured by direction', () => {
    const candles = Array.from(fixture.nativeElement.querySelectorAll('g.candle')) as SVGGElement[];

    expect(candles.length).toBe(3);
    expect(candles.map((candle) => candle.classList.contains('candle-up'))).toEqual([true, false, true]);
  });

  it('shows the latest bar and its change from the previous close until another is picked', () => {
    expect(field('O')).toBe('101.00');
    expect(field('C')).toBe('107.00');
    expect(readout()).toContain('+6.00 (+5.94%)');
    expect(field('Vol')).toBe('2,000');
  });

  it('steps through bars with the arrow keys', () => {
    const svg = fixture.nativeElement.querySelector('svg') as SVGSVGElement;

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    fixture.detectChanges();
    expect(field('C')).toBe('101.00');
    expect(readout()).toContain('-2.00 (-1.94%)');

    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    fixture.detectChanges();
    // The first bar has no previous close to change from
    expect(field('C')).toBe('103.00');
    expect(readout()).not.toContain('%');
    expect(component.activeIndex()).toBe(0);
  });

  it('shows only the most recent bars that fit a narrow chart', () => {
    const many = Array.from({ length: 120 }, (_, index) => ({ ...bars[0], time: new Date(Date.UTC(2026, 0, index + 1)).toISOString() }));
    fixture.componentRef.setInput('bars', many);
    component.width.set(264);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('g.candle').length).toBe(50);
    expect(component.visibleBars()[49]).toBe(many[119]);
  });

  it('shows at most MAX_VISIBLE bars on a wide chart, the earlier ones only feeding the averages', () => {
    const many = Array.from({ length: 120 }, (_, index) => ({ ...bars[0], time: new Date(Date.UTC(2026, 0, index + 1)).toISOString() }));
    fixture.componentRef.setInput('bars', many);
    fixture.componentRef.setInput('movingAverages', [50]);
    component.width.set(2000);
    fixture.detectChanges();

    expect(component.visibleBars().length).toBe(CandlestickChartComponent.MAX_VISIBLE);
    // With 60 earlier bars to average over, the line starts at the first visible candle
    expect(component.overlays()[0].values.every((value) => value !== null)).toBeTrue();
  });

  it('picks the bar under the pointer and dims the others', () => {
    const svg = fixture.nativeElement.querySelector('svg') as SVGSVGElement;
    const left = svg.getBoundingClientRect().left;
    const slot = component.layout()!.slot;

    svg.dispatchEvent(new PointerEvent('pointermove', { clientX: left + slot * 1.5 }));
    fixture.detectChanges();

    expect(component.activeIndex()).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('g.candle.dimmed').length).toBe(2);
    expect(fixture.nativeElement.querySelector('line.crosshair')).not.toBeNull();

    svg.dispatchEvent(new PointerEvent('pointerleave'));
    fixture.detectChanges();
    expect(component.activeIndex()).toBeNull();
    expect(fixture.nativeElement.querySelector('line.crosshair')).toBeNull();
  });
});
