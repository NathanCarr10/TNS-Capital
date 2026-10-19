/// <reference types="jasmine" />

import { EquityPoint } from '../../../api/strategy';
import { layoutEquityCurve } from './equity-curve-layout';

const BOX = { width: 400, height: 200, padding: { top: 10, right: 10, bottom: 20, left: 50 } };

const curve = (values: [number, number][]): EquityPoint[] =>
  values.map(([strategy, buyAndHold], index) => ({ date: `2024-01-${String(index + 1).padStart(2, '0')}`, strategy, buyAndHold }));

describe('layoutEquityCurve', () => {
  it('draws both lines across the plot on one value scale', () => {
    const layout = layoutEquityCurve(curve([[100, 100], [120, 90], [150, 110]]), BOX)!;

    expect(layout.points.map((p) => p.x)).toEqual([50, 220, 390]);
    expect(layout.strategyLine.startsWith('M50.0,')).toBeTrue();
    expect(layout.buyAndHoldLine.split('L')).toHaveSize(3);
    // Higher values sit higher on the chart
    expect(layout.points[2].strategyY).toBeLessThan(layout.points[0].strategyY);
    expect(layout.points[1].buyAndHoldY).toBeGreaterThan(layout.points[1].strategyY);
    expect(layout.valueTicks.length).toBeGreaterThan(1);
  });

  it('labels some dates between the ends, spaced to fit', () => {
    const many = curve(Array.from({ length: 300 }, (_, i) => [100 + i, 100] as [number, number]));

    const layout = layoutEquityCurve(many, BOX)!;

    expect(layout.timeTicks.length).toBeGreaterThan(0);
    expect(layout.timeTicks.length).toBeLessThanOrEqual(4);
  });

  it('draws nothing for fewer than two points or no width', () => {
    expect(layoutEquityCurve(curve([[100, 100]]), BOX)).toBeNull();
    expect(layoutEquityCurve(curve([[100, 100], [100, 100]]), { ...BOX, width: 40 })).toBeNull();
  });

  it('still has a scale when nothing moves', () => {
    const layout = layoutEquityCurve(curve([[100, 100], [100, 100]]), BOX)!;

    expect(layout.points[0].strategyY).toBe(layout.points[1].strategyY);
    expect(Number.isFinite(layout.points[0].strategyY)).toBeTrue();
  });
});
