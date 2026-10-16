/// <reference types="jasmine" />

import { PriceBar } from '../../../core/trading/trading.models';
import { PRICE_AXIS_WIDTH, layoutCandlesticks, movingAverage, niceStep } from './candlestick-layout';

describe('layoutCandlesticks', () => {
  const bar = (open: number, high: number, low: number, close: number, volume = 100, day = 1): PriceBar => ({
    time: `2026-10-${String(day).padStart(2, '0')}T04:00:00Z`,
    open,
    high,
    low,
    close,
    volume,
  });

  it('draws an overlay through the bars that have a value, widening the price range to fit it', () => {
    const bars = [bar(10, 11, 9, 10, 100, 1), bar(10, 11, 9, 10, 100, 2), bar(10, 11, 9, 10, 100, 3)];
    const plain = layoutCandlesticks(bars, 364, 300)!;
    const layout = layoutCandlesticks(bars, 364, 300, [[null, 20, 20]])!;

    expect(layout.overlayPaths).toHaveSize(1);
    expect(layout.overlayPaths[0]).toMatch(/^M150\.0,[\d.]+L250\.0,[\d.]+$/);
    // 20 is far above every bar, so the candles are squeezed lower to make room
    expect(layout.candles[0].wickTop).toBeGreaterThan(plain.candles[0].wickTop);
  });

  it('returns null when there is nothing to draw or no room for it', () => {
    expect(layoutCandlesticks([], 600, 300)).toBeNull();
    expect(layoutCandlesticks([bar(1, 2, 0.5, 1.5)], PRICE_AXIS_WIDTH, 300)).toBeNull();
  });

  it('gives each bar an equal slot left of the price axis, centred within it', () => {
    const layout = layoutCandlesticks([bar(10, 12, 9, 11, 100, 1), bar(11, 13, 10, 12, 100, 2)], 464, 300)!;

    expect(layout.plotRight).toBe(400);
    expect(layout.slot).toBe(200);
    expect(layout.candles.map((candle) => candle.x)).toEqual([100, 300]);
    expect(layout.bodyWidth).toBe(18);
  });

  it('marks rising bars up and falling bars down, with the body between open and close', () => {
    const [rising, falling] = layoutCandlesticks([bar(10, 14, 8, 12, 100, 1), bar(12, 13, 9, 10, 100, 2)], 600, 300)!
      .candles;

    expect(rising.direction).toBe('up');
    expect(falling.direction).toBe('down');
    // Higher prices sit higher up, at smaller y
    expect(rising.wickTop).toBeLessThan(rising.bodyTop);
    expect(rising.bodyTop + rising.bodyHeight).toBeLessThan(rising.wickBottom);
    expect(rising.closeY).toBeCloseTo(rising.bodyTop);
    expect(falling.closeY).toBeCloseTo(falling.bodyTop + falling.bodyHeight);
  });

  it('keeps every wick inside the price area and gives a doji a visible body', () => {
    const layout = layoutCandlesticks([bar(10, 15, 5, 10), bar(10, 10, 10, 10, 100, 2)], 600, 300)!;

    for (const candle of layout.candles) {
      expect(candle.wickTop).toBeGreaterThan(layout.priceTop);
      expect(candle.wickBottom).toBeLessThan(layout.priceBottom);
      expect(candle.bodyHeight).toBeGreaterThanOrEqual(1);
    }
  });

  it('scales volume bars to the largest volume', () => {
    const layout = layoutCandlesticks([bar(1, 2, 0.5, 1.5, 50, 1), bar(1, 2, 0.5, 1.5, 100, 2)], 600, 300)!;
    const [half, full] = layout.candles.map((candle) => layout.volumeBottom - candle.volumeTop);

    expect(full).toBeGreaterThan(0);
    expect(half).toBeCloseTo(full / 2);
    expect(layout.volumeBottom - full).toBeGreaterThan(layout.priceBottom);
  });

  it('labels the price axis at round values within the range', () => {
    const layout = layoutCandlesticks([bar(100, 120, 95, 110)], 600, 300)!;
    const values = layout.priceTicks.map((tick) => tick.value);

    expect(values).toEqual([95, 100, 105, 110, 115, 120]);
  });

  it('spaces time labels so they do not crowd', () => {
    const bars = Array.from({ length: 100 }, (_, index) => bar(10, 11, 9, 10, 100, (index % 28) + 1));
    const layout = layoutCandlesticks(bars, 664, 300)!;
    const positions = layout.timeTicks.map((tick) => tick.position);

    expect(positions.length).toBeGreaterThan(1);
    positions.slice(1).forEach((position, index) => expect(position - positions[index]).toBeGreaterThanOrEqual(110));
    expect(positions[positions.length - 1]).toBeLessThanOrEqual(layout.plotRight);
  });
});

describe('niceStep', () => {
  it('rounds to 1, 2 or 5 times a power of ten', () => {
    expect(niceStep(10, 5)).toBe(2);
    expect(niceStep(27, 5)).toBe(5);
    expect(niceStep(0.9, 5)).toBeCloseTo(0.2);
    expect(niceStep(1000, 5)).toBe(200);
    expect(niceStep(60, 5)).toBe(10);
  });
});

describe('movingAverage', () => {
  const closes = (...values: number[]): PriceBar[] =>
    values.map((close, index) => ({ time: `2026-10-0${index + 1}T04:00:00Z`, open: close, high: close, low: close, close, volume: 1 }));

  it('averages the closes over the period, with no value until the period has filled', () => {
    expect(movingAverage(closes(1, 2, 3, 4, 5), 3)).toEqual([null, null, 2, 3, 4]);
  });

  it('has no values when there are fewer bars than the period', () => {
    expect(movingAverage(closes(1, 2), 3)).toEqual([null, null]);
  });
});
